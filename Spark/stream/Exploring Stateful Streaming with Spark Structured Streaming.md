# Exploring Stateful Streaming with Spark Structured Streaming

30 Jul 2017

[In a previous post](http://asyncified.io/2016/07/31/exploring-stateful-streaming-with-apache-spark/), we explored how to do stateful streaming using Sparks Streaming API with the `DStream` abstraction. Today, I’d like to ==sail out on a journey== with you to explore Spark 2.2 with its new support for stateful streaming under the Structured Streaming API. In this post, we’ll see how the API has matured and evolved, look at the differences between the two approaches (Streaming vs Structured Streaming), and see what changes were made to the API. We’ll do so by taking the example from my previous post, and adapting it to the new API.

## A recap of state management with Spark Streaming

If you needed to use stateful streaming with Spark you had to choose between two abstractions (up until Spark 2.2). `updateStateByKey` and `mapWithState` where the latter is more or less an improvement (both API and performance wise) version of the former (with a bit of different semantics). In order to utilize state between micro batches, you provided a `StateSpec` function to `mapWithState` which would be invoked per key value pair that arrived in the current micro batch. With `mapWithState`, the main advantage points are:

1. **Initial RDD for state** - One can load up an RDD with previously saved state
2. **Timeout** - Timeout management was handled by Spark. You can set a single timeout *for all* key value pairs.
3. **Partial updates** - Only keys which were “touched” in the current micro batch were iterated for update
4. **Return type** - You can choose any return type of your choice.

But thing aren’t always perfect…

## Pain points of `mapWithState`

`mapWithState` was a big improvement over the previous `updateStateByKey`API. But there are a few caveats I’ve experienced over the last year while using it:

### Checkpointing

To ensure Spark can recover from failed tasks, it has to checkpoint data to a distributed file system from which it can consume upon failure. When using `mapWithState`, each executor process is holding a HashMap, in memory, of all the state you’ve accumulated. At every checkpoint, Spark *serializes the entire state, each time*. If you’re holding a lot of state in memory, this can cause significant processing latencies. For example, under the following set up:

- Batch interval: 4 seconds
- Checkpointing interval: 40 seconds (4 second batch x 10 constant spark factor)
- 80,000 messages/second
- Message size: 500B - 2KB
- 5 m4.2xlarge machines (8 vCPUs, 32GB RAM)
- 2 Executors per machine
- Executor storage size ~ 7GB (each)
- Checkpointing data to HDFS

I’ve experienced accumulated delays **of up to 4 hours**, since each checkpoint under high load was taking between 30 seconds - 1 minute for the entire state and we’re generating batches every 4 seconds. [I’ve also seen people confused by this on StackOverflow](https://stackoverflow.com/questions/36042295/spark-streaming-mapwithstate-seems-to-rebuild-complete-state-periodically/36065778#36065778) as it really isn’t obvious why some batches take drastically longer than others.

If you’re planning on using stateful streaming for high throughput you have to consider this as a serious caveat. This problem was so severe that it sent me looking for an alternative to using in memory state with Spark. But we’ll soon see that things are looking bright ;)

### Saving state between version updates

Software is an evolving process, we always improve, enhance and implement new feature requirements. As such, we need to be able to upgrade from one version to another, preferably without affecting existing data. This becomes quite tricky with in memory data. How do we preserve the current state of things? How do we ensure that we continue from where we left off?

Out of the box, `mapWithState` doesn’t support evolving our data structure. If you’ve modified the data structure you’re storing state with, you have to *delete all previously checkpointed data* since the `serialVersionUID` will differ between object versions. Additionally, any change to the execution graph defined on the `StreamingContext` won’t take effect since we’re restoring the linage from checkpoint.

`mapWithState` does provide a method for viewing the current state snapshot of our data via `MapWithStateDStream.stateSnapshot()`. This enables us to store state at an external repository and be able to recover from it using `StateSpec.initialRDD`. However, storing data externally can increase the already-significant-delays due to checkpoint latencies.

### Separate timeout per state object

`mapWithState` allows us to set a default timeout for all states via `StateSpec.timeout`. However, at times it may be desired to have separate state timeout for each state object. For example, assume we have a requirement that a user session be no longer than 30 minutes. Then comes along a new client which wants to see user sessions end every 10 minutes, what do we do? Well, we can’t handle this out of the box and we have to implement our own mechanism for timeout. The bigger problem is that `mapWithState` only touches key value pairs which we have data for in the *current batch*, it doesn’t touch all the keys. This means that we have to role back to `updateStateByKey` which by default iterates the entire state, which may be bad for performance (depending on the use case, of course).

### Single executor failure causing data loss

Executors are java processes, and as for any process they can fail. I’ve had heap corruptions in production cause a single executor to die. The problem with this is that once a new executor is created by the `Worker` process, it *does not recover the state from checkpoint*. If you look at the [`CheckpointSuite`](https://github.com/apache/spark/blob/master/streaming/src/test/scala/org/apache/spark/streaming/CheckpointSuite.scala#L209) tests, you’ll see that all of them deal with `StreamingContext` recovery, but none for single executor failure.

### Ok, downsides, fine. Where is this new API you’re talking about?

Hold your horses, we’re just getting to it… :)

## Introducing Structured Streaming

Structured Streaming is Sparks new shiny tool for reasoning about streaming.

From the [Structured Streaming Documentation - Overview](https://spark.apache.org/docs/latest/structured-streaming-programming-guide.html#overview):

> Structured Streaming is a scalable and fault-tolerant stream processing engine built on the Spark SQL engine. You can **express your streaming computation the same way you would express a batch computation on static data.** The Spark SQL engine will take care of running it incrementally and continuously and updating the final result as streaming data continues to arrive.

Sparks authors realize that reasoning about a distributed streaming application has many hidden concerns one may or may not realize he/she has to deal with other than maintaining the business domain logic. Instead of taking care of all these concerns, they want us to reason about our stream processing the same way we’d use a static SQL table by generating queries while take care of running them over new data as it comes into our stream. Think about it as a unbounded table of data.

For a deeper explanation on Structured Streaming and the `Dataset[T]`abstraction, see [this great post by DataBricks](https://databricks.com/blog/2016/07/28/structured-streaming-in-apache-spark.html). Don’t worry, I’ll wait..

Welcome back. Let’s continue on to see what the new stateful streaming abstraction looks like in Structured Streaming

## Learning via an example

The example I’ll use here is the same example I’ve used in my previous post regarding stateful streaming. To recap (and for those unfamiliar with the previous post), the example talks about a set of incoming user events which we want to aggregate as they come in from the stream. Our events are modeled in the `UserEvent` class:

```
case class UserEvent(id: Int, data: String, isLast: Boolean)

```

We uniquely identify a user by his id. This id will be used to group the incoming data together so that we get all user events to the same executor process which handles the state. A user event also has a `data` field which generates some `String` content and an additional `Boolean` field indicating if this is the last message for the current session.

The way we aggregate user events as they come in the stream is by using a `UserSession` class:

```
case class UserSession(userEvents: Seq[UserEvent])

```

Which holds together all events by a particular user.

## Introducing `mapGroupsWithState`:

If you think “Hmm, this name sounds familiar”, you’re right, it’s almost identical to the `mapWithState` abstraction we had in Spark Streaming with minor changes to on the user facing API. But first, let’s talk about some key differences between the two.

### Differences between `mapWithState` and `mapGroupsWithState`(and generally Spark Structured VS Streaming)

1. **Keeping state between application updates** - One of the biggest caveats of `mapWithState` is the fact that unless you roll out your own bookkeeping of state you’re forced to drop the in-memory data between upgrades. Not only that, but if anything inside the Spark DAG changes you have to drop that data as well. From my experiments with `mapGroupsWithState`, it seems as using the Kryo encoder combined with versioning your data structures correctly (i.e using default values for newly added state), allows you to **keep the data between application upgrades and also change the Spark DAG defining your transformations and still keep the state**. This is major news for anyone using `mapWithState`. The reason I’m cautious saying this is because I haven’t seen any official documentation or statements from the developers of Structured Streaming to support this claim.
2. **Micro batch execution** - Spark Streaming requires a fix batch interval in order to generate and execute micro batches with data from source. Even if no new data has arrived the micro batch will still execute which will cause the entire graph execute. Structured Streaming is different, it has a dedicated thread which checks the source constantly to see if new data has arrived. If no new data is available, **the query will not be executed**. What does that mean? It means, for example, that if you set a timeout interval of X seconds but new data hasn’t come into the stream, **no state will timeout** because it won’t run the query.
3. **Internal data storage for state** - `mapWithState` is based on an `OpenHashMapBasedStateMap[K, V]` implementation to store the in memory state. `mapGroupsWithState` uses `java.util.ConcurrentHashMap[K, V]`. Additionally, the latter uses an underlying structure called `UnsafeRow` for both key and value instead of a plain JVM object. These unsafe rows are wrappers around the bytes of data generated by the encoder for the keys and values, and applies on demand conversions between the unsafe representation to our JVM object structure when it needs to pass the key value pair to our state function.
4. **State versioning** - there is no longer a single state store per executor in memory. The new implementation uses a `HDFSBackedStateStore` per *version*of the state, meaning it only needs to keep the latest version of the state in memory while letting older versions reside in the backing state store, loading them as needed on demand.
5. **Timeout** - `mapWithState` has a single timeout set for all state objects. `mapGroupsWithState` enables state timeout per group, meaning you can create more complex configurations for timeouts. Additionally, a timeout based on event time and watermarks are available.
6. **Checkpointing** - `mapWithState` checkpoint occurs every fixed interval. If our batch time is 4 seconds, a checkpoint for the entire state in memory would occur every 40 seconds. Not only that, but a checkpoint is a *blocking operation*, meaning that until it finishes we cannot process incoming events of the next batch. `mapGroupsWithState` checkpointing is done *incrementally* for updated keys only and this is abstracted away by the implementation of the underlying `FileSystem` used as the state store. This means that there should be a **significant reduction** in checkpointing overhead.
7. **Offset Handling (For replayable sources, such as Kafka)** - Using the `DStream`API with a replayable source such as Kafka would require us to reason about offset storage ourselves in persistent storage such as ZooKeeper, S3 or HDFS. Replaying a certain source from a particular offset means reading the data from the persistent storage and passing it to `KafkaUtil.createDirectStream`upon initialization of the stream. Structured Streaming stores and retrieves the offsets on our behalf when re-running the application meaning we no longer have to store them externally.

Ok, enough with the comparisons, let’s get to business.

### Analyzing the API

Let’s look at the method signature for `mapGroupsWithState`:

```
def mapGroupsWithState[S: Encoder, U: Encoder](
      timeoutConf: GroupStateTimeout)(
      func: (K, Iterator[V], GroupState[S]) => U)

```

Let’s break down each argument and see what we can do with it. The first argument contains a `timeoutConf` which is responsible for which timeout configuration we want to choose from. We have two options:

1. **Processing Time Based** (`GroupStateTimeout.ProcessingTimeTimeout`) - Timeout based on a constant interval (similar to calling the `timeout` function on `StateSpec` in Spark Streaming)
2. **Event Time Based** (`GroupStateTimeout.EventTimeTimeout`) - Timeout based on a user defined even time *and watermark* (read [this for more about handling late data using watermarks](https://spark.apache.org/docs/latest/structured-streaming-programming-guide.html#handling-late-data-and-watermarking)).

In the second argument list we have our state function. Let’s examine each argument and what it means:

```
func: (K, Iterator[V], GroupState[S] => U)

```

There are three argument types, `K`, `Iterator[V]`, and `GroupState[S]` and a return type of type `U`. Lets map each of these arguments to our example and fill in the types.

As we’ve seen, we have a stream of incoming messages of type `UserEvent`. This class has a field called `id` of type `Int` which we’ll use as our key to group user events together. This means we substitute `K` with `Int`:

```
(Int, Iterator[V], GroupState[S]) => U

```

Next up is `Iterator[V]`. `V` is the type of the values we’ll be aggregating. We’ll be receiving a stream of `UserEvent` and that means we need to substitute that with `V`:

```
(Int, Iterator[UserEvent], GroupState[S]) => U

```

Great! Which class describes our state? If you scroll up a bit, you’ll see we’ve defined a class called `UserSession` which portraits the entire session of the user, and that’s what we’ll use as our state type! Let’s substitute `S` with `UserSession`

```
(Int, Iterator[UserEvent], GroupState[UserSession]) => U

```

Awesome, we’ve managed to fill in the types of the arguments. The return type, `U`is what we have left. We only want to return a `UserSession` once it’s complete, either by the user session timing out or receiving the `isLast` flag set to `true`. We’ll set the return type to be an `Option[UserSession]` which will be filled iff we’ve completed the session. This means substituting `U` with `Option[UserSession]`:

```
(Int, Iterator[UserEvent], GroupState[UserSession]) => Option[UserSession]

```

Hooray!

### The `GroupState` API

For those of you acquainted with the `mapWithState` API, `GroupState` should feel very familiar to the `State` class. Let’s see what the API is like:

1. `def exists: Boolean` - Whether state exists or not.
2. `def get: S` - Get the state value if it exists, or throw NoSuchElementException.
3. `def getOption: Option[S]` - Get the state value as a Scala Option[T].
4. `def update(newState: S): Unit` - Update the value of the state. Note that `null` is not a valid value, and it throws IllegalArgumentException.
5. `def remove(): Unit` - Remove this state.
6. `def hasTimedOut: Boolean` - Whether the function has been called because the key has timed out. This can return true only when timeouts are enabled in `[map/flatmap]GroupsWithStates`.
7. `def setTimeoutDuration(durationMs: Long): Unit` - Set the timeout duration in milliseconds for this key. Note ProcessingTimeTimeout must be enabled in `[map/flatmap]GroupsWithStates`, otherwise it throws an `UnsupportedOperationException`

The newest member to the state API is the `setTimeoutDuration` method. I’ve only included a single overload, but there are 3 others taking various input arguments types such as `String` and `java.util.Timestamp`. Note that since each group state can have it’s own timeout we have to set it explicitly inside `mapGroupsWithState`, for each group state. This means that each time our method is called, we’ll have to set the timeout again using `setTimeoutDuration`, as we’ll see when we implement that method.

In addition, there are several restrictions to calling `setTimeoutDuration`. If the we haven’t set the `timeoutConf` argument in `mapGroupsWithState`, when we call this method it will throw an `UnsupportOperationException`, so make sure you’ve configured the timeout.

I’ve summarized API documentation here but if you want the full details [see the Scala Docs](https://spark.apache.org/docs/latest/api/scala/index.html#org.apache.spark.sql.streaming.GroupState).

### Creating custom encoders

For the perceptive amongst the readers, you’ve probably noticed the following constraint on the type parameters of `mapGroupsWithState`:

```
def mapGroupsWithState[S: Encoder, U: Encoder]

```

What is this `Encoder` class required via [context bound](https://stackoverflow.com/questions/2982276/what-is-a-context-bound-in-scala) on the elements `S` and `U`? The class documentation says:

> Used to convert a JVM object of type `T` to and from the internal Spark SQL representation.

Spark SQL is layered on top an optimizer called the Catalyst Optimizer, which was created as part of the [Project Tungsten](https://databricks.com/blog/2015/04/28/project-tungsten-bringing-spark-closer-to-bare-metal.html). Spark SQL (and Structured Streaming) deals, under the covers, with raw bytes instead of JVM objects, in order to optimize for space and efficient data access. For that, we have to tell Spark how to convert our JVM object structure into binary and that is exactly what these encoders do.

Without going to lengths about encoders, here is the method signature for the trait:

```
trait Encoder[T] extends Serializable {

  /** Returns the schema of encoding this type of object as a Row. */
  def schema: StructType

  /**
   * A ClassTag that can be used to construct an Array to contain a collection of `T`.
   */
  def clsTag: ClassTag[T]
}

```

Any encoder has to provide two things, a schema of the class described via a `StructType`, which is a recursive data structure laying out the schema of each field in the object we’re describing, and the `ClassTag[T]` for converting collections with type `T`.

If we look closely again at the signature for `mapGroupsWithState`, we see that we need to supply two encoders, one for our state class represented by type `S`, and one for our return type represented by type `U`. In our example, that would mean providing implicit evidence for `UserSession` in the form of an `Encoder[UserSession]`. But how do we generate such encoders? Spark comes packed with encoders for primitives via the `SQLImplicits` object, and if we use case classes we’ll have to have an implicit in scope for it. The easiest way to do so is by creating a custom encoder using `Encoders.kryo[T]`. We use it like this:

```
object StatefulStructuredSessionization {
  implicit val userEventEncoder: Encoder[UserEvent] = Encoders.kryo[UserEvent]
  implicit val userSessionEncoder: Encoder[UserSession] = Encoders.kryo[UserSession]
}

```

For more on custom encoders, see this [StackOverflow answer](https://stackoverflow.com/a/39442829/1870803).

## Implementing our state method

After figuring out what the signature of our state method is, let’s go ahead an implement it:

```
def updateSessionEvents(
  id: Int,
  userEvents: Iterator[UserEvent],
  state: GroupState[UserSession]): Option[UserSession] = {
if (state.hasTimedOut) {
  // We've timed out, lets extract the state and send it down the stream
  state.remove()
  state.getOption
} else {
  /*
    New data has come in for the given user id. We'll look up the current state
    to see if we already have something stored. If not, we'll just take the current user events
    and update the state, otherwise will concatenate the user events we already have with the
    new incoming events.
  */
  val currentState = state.getOption
  val updatedUserSession = currentState.fold(UserSession(userEvents.toSeq))(currentUserSession => UserSession(currentUserSession.userEvents ++ userEvents.toSeq))
      
  if (updatedUserSession.userEvents.exists(_.isLast)) {
    /*
    If we've received a flag indicating this should be the last event batch, let's close
    the state and send the user session downstream. 
    */
    state.remove()
    updatedUserSession
  } else {  
    state.update(updatedUserSession)   
    state.setTimeoutDuration("1 minute")
    None
   }
  }
}

```

Our `updateUserEvents` method has to deal with a couple of flows. We check to see if our method was invoked as a cause of the state timing out and if it has, the `state.hasTimedOut` method will be set to `true` and our `userEvents` iterator will be empty. All we have to do is remove the state and send out our `Option[UserSession]` down the stream. If it hasn’t timed out that means that the method has been invoked because new values have arrived.

We extract the current state out to the `currenState` value and deal with two cases by the means of `Option.fold`:

1. **The state is empty** - this means that this is the first batch of values for the given key and all we have to do is take the user events we’ve received and lift them into the `UserSession` class.
2. **The state has a value** - we extract the existing user events from the `UserSession` object (this is the second argument list in the `fold` method) and append them to the new values we just received.

The `UserEvent` class contains a field named `isLast` which we check to see if this is the last incoming event for the session. After aggregating the values, we scan the user events sequence to see if we’ve received the flag. If we did, we remove the session from the state and return the session, otherwise we update the state and set the timeout duration and return `None` indicating the session isn’t complete yet.

## Fitting it all together

We’ve seen how to define our state method, which means we’re ready to create the execution graph. In this example, I’ll be consuming data from a socket in JSON structure which matches our `UserEvent`.

I apologize in advance for not including all the code and imports in this gist. At the bottom of the post you’ll find a link to a full working repo on GitHub containing all the code for you to try out.

To start things off, we create a `SparkSession` instance with the details of the master URI and application name:

```
val spark: SparkSession = SparkSession.builder
      .master("local[*]")
      .appName("Stateful Structured Streaming")
      .getOrCreate()

```

`SparkSession` is our gateway to interaction with the streaming graph, just as `StreamingContext` previously was. After our session is defined, we express what the format of our source is for consuming the data, in this case its a Socket:

```
import spark.implicits._

val userEventsStream: Dataset[String] = spark.readStream
    .format("socket")
    .option("host", host)
    .option("port", port)
    .load()
    .as[String]

```

Importing `spark.implicits._` is for the encoders defined for primitives (we use it here for `String`). The `host` and `port` variables come from the command line arguments. Once we call the `load()` method, we get back a `DataFrame`. Think of it as a generic representation of the data containing rows and columns. In order to convert a `DataFrame` into a `DataSet[String]` we use the `as[T]` method which tells Spark we want to use get back a typed data set.

After we have the data set at hand, we can `map` over it to deserialize our JSON into a `UserEvent` and apply our state method to it:

```
val finishedUserSessionsStream: Dataset[UserSession] =
  userEventsStream
    .map(deserializeUserEvent)
    .groupByKey(_.id)
    .mapGroupsWithState(GroupStateTimeout.ProcessingTimeTimeout())(updateSessionEvents)
    .flatMap(userSession => userSession)

```

After mapping over the data set and deserializing all events, we use `groupByKey`to group user events by their id to get back a `KeyValueGroupedDataset[K, V]`. The grouping is the key (no pun intended) to exposing `mapGroupsWithState`which is defined on the key valued data set type. We then call `mapGroupsWithState` and pass `GroupStateTimeout.ProcessingTimeTimeout`as the first argument to indicate to Spark how we want to timeout our state, and pass in our `updateSessionEvents` method we’ve defined beforehand. After we finish applying the stateful transformation we output the completed sessions to the rest of the graph, but we also output `None` in case the session isn’t complete. This means we have to make sure to keep flowing only `Option[T]` which contains a `Some[UserSession]`, which is the reason for `flatMap`.

We’re left to define the output of our stream. For this example I chose the “console” format which just prints out values to the console, but you may use any of the existing [output sinks](https://spark.apache.org/docs/latest/structured-streaming-programming-guide.html#output-sinks). Additionally, we have to specify which type of [output mode](https://spark.apache.org/docs/latest/structured-streaming-programming-guide.html#output-modes)we choose (we can only use `OutputMode.Update` with `mapGroupsWithState`) and the checkpoint directory location:

```
finishedUserSessionsStream.writeStream
  .outputMode(OutputMode.Update())
  .format("console")
  .option("checkpointLocation", "hdfs:///path/to/checkpoint/location")
  .start()
  .awaitTermination()

```

And voila, watch the data start pouring into the stream!

## Wrapping up

Structured Streaming brings a new mental model of reasoning about streams. The most promising features I see for `mapGroupsWithState` is that fact that we no longer suffer the penalty of checkpointing due to the way stateful aggregations are handled. Additionally, being able to save state between version upgrades and get automatic offset management is also very appealing. Time will definitely tell if this is THE stateful management framework we’ve been waiting for, it sure looks and feels promising.

There are more internal implementation details which are interesting which I encourage you to explore and share in the comments as you start using Structured Streaming.

You can find the full working example in my [GitHub stateful streaming repository](https://github.com/YuvalItzchakov/spark-stateful-example).