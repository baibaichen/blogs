# 编程模型

使用Beam处理数据，有四个主要的概念：
- Pipelines：封装了一系列计算，用于处理外部数据源数据。**`转换`**数据以提供有用的信息，并产生出一些输出数据。Each pipeline represents **a single, potentially repeatable job, from start to finish**, in the *Dataflow* service. 
- PCollections：代表**pipeline**中的数据，`PCollection`类是经过特殊设计的数据容器，用于表示**固定大小的数据文件**，也可用于表示**持续更新的、无限的数据源**。`PCollection`s是**pipeline**中每一步的输入和输出。
- Transforms：代表**pipeline**每一步的操作（计算）。
- I/O Sources 和 Sinks：输入源和输出目的。

> 看来都差不多，Pipeline 等价于DAG，


##Pipeline
**pipeline**代表了处理数据的job。<u>The data and transforms in a pipeline are unique to, and owned by, that pipeline. While your program can create multiple pipelines, pipelines cannot share data or transforms</u>

###Pipeline的组成部分
**pipeline**由两部分组成，数据和处理数据的**transforms**。

####Pipeline Data
In the Dataflow SDKs, pipelines use a specialized collection class called `PCollection` to represent their input, intermediate, and output data. **`PCollections` can be used to represent data sets of virtually any size**. Note that compared to typical collection classes such as Java's Collection, PCollections are specifically designed to support parallelized processing.

**A pipeline must create a PCollection for any data it needs to work with**. You can read data from an external source into a PCollection, or you can create a PCollection from local data in your Dataflow program. From there, each transform in your pipeline accepts one or more PCollections as input and produces one or more PCollections as output.

> See `PCollection` for a complete discussion of how a `PCollection` works and how to use one.

####Pipeline Transforms

A **transform** is a step in your pipeline. Each transform takes one or more `PCollection`s as input, changes or otherwise manipulates the elements of that `PCollection`, and produces one or more new `PCollection`s as output.

##### Core Transforms

The Dataflow SDKs contain a number of **core transforms**. A core transform is a generic operation that represents a basic or common processing operation that you perform on your pipeline data. Most core transforms provide a processing pattern, and require you to create and supply the actual processing logic that gets applied to the input `PCollection`.

For example, the [ParDo](https://cloud.google.com/dataflow/model/par-do) core transform provides a generic processing pattern: for every element in the input `PCollection`, perform a user-specified processing function on that element. The Dataflow SDKs supply core transforms such as [ParDo](https://cloud.google.com/dataflow/model/par-do) and [GroupByKey](https://cloud.google.com/dataflow/model/group-by-key), as well as other core transforms for combining, merging, and splitting data sets.

>  See [Transforms](https://cloud.google.com/dataflow/model/transforms.html) for a complete discussion of how to use transforms in your pipeline.

##### Composite Transforms

The Dataflow SDKs support combining multiple transforms into larger **composite transforms.** In a composite transform, multiple transforms are applied to a data set to perform a more complex data processing operation. Composite transforms are a good way to build modular, reusable combinations of transforms that do useful things.

The Dataflow SDKs contain libraries of pre-written composite transforms that handle common data processing use cases, including (but not limited to):

- Combining data, such as **summing** or **averaging** numerical data
- **Map/Shuffle/Reduce-style processing**, such as counting unique elements in a collection
- Statistical analysis, such as **finding the top *N* elements in a collectio**n

You can also create your own reusable composite transforms. See [Creating Composite Transforms](https://cloud.google.com/dataflow/model/composite-transforms.html) for a complete discussion.

##### Root Transforms

The Dataflow SDKs often use **root transforms** at the start of a pipeline to [create an initial PCollection](https://cloud.google.com/dataflow/model/pcollection#Creating). Root transforms frequently involve reading data from an external data source. See [Pipeline I/O](https://cloud.google.com/dataflow/model/pipeline-io#using-reads) for additional information.

###[设计pipeline时需要考虑的因素](https://cloud.google.com/dataflow/pipelines/design-principles)：

1. 数据存储在哪？
2. 数据的存储格式是啥？
3. 怎么处理数据？
4. 数据输出到那？

#### 不同的 Pipeline 形状

##### Branching `PCollection`s

It's important to understand that transforms do not consume `PCollection`s; instead, they consider each individual element of a `PCollection` and create a new `PCollection` as output. This way, you can do different things to different elements in the same `PCollection`.

###### Multiple transforms process the same `PCollection`

这里的意思是用**两个** `PTransform` 处理同一个 `PCollection` 。

第一个`PTransform`
```
if (starts with 'A') { outputToPCollectionA }
```
第一个`PTransform`
```
if (starts with 'B') { outputToPCollectionB }
```
###### A single transform that uses side outputs

[side outputs](https://cloud.google.com/dataflow/model/par-do#side-outputs) 的意思是用**一个**  `PTransform` 处理 `PCollection` ，但是**两（多）个** `PCollection` 输出。

`PTransform`按如下的写：
```
if (starts with 'A') { outputToPCollectionA } else if (starts with 'B') { outputToPCollectionB }
```

##### Merging `PCollection`s

- **Flatten** -  使用 [`Flatten`](https://cloud.google.com/dataflow/model/multiple-pcollections#flatten) ，只能合并同样类型的`PCollection`s。
- **Join** -  使用[`CoGroupByKey`](https://cloud.google.com/dataflow/model/group-by-key#join)。

##### Multiple sources

从多个源读取数据

## PCollection

The Dataflow SDKs use a specialized class called `PCollection` to represent data in a pipeline. A `PCollection` represents a multi-element data set.

You can think of a `PCollection` as "pipeline" data. Dataflow's [transforms](https://cloud.google.com/dataflow/model/transforms) use `PCollection`s as inputs and outputs; as such, if you want to work with data in your pipeline, it must be in the form of a `PCollection`. **Each `PCollection` is owned by a specific `Pipeline` object, and only that `Pipeline` object can use it.**

### PCollection Characteristics

**A `PCollection` represents a potentially large, immutable "bag" of elements**. There is **no upper limit** on how many elements a `PCollection` can contain; any given `PCollection` might fit in memory, or it might represent a very large data set backed by a persistent data store.

> The elements of a `PCollection` can be of any type, but must all be of the same type. However, Dataflow needs to be able to encode each individual element as a byte string in order to support distributed processing. The Dataflow SDKs provide a [Data Encoding](https://cloud.google.com/dataflow/model/data-encoding.html) mechanism that includes built in encodings for commonly used types and support for specifying custom encodings as needed. Creating a valid encoding for an aribitrary type can be challenging, but you can [construct](https://cloud.google.com/dataflow/model/pcollection#user-data-types) custom encoding for simple structured types.

#### PCollection Limitations

A `PCollection` has several key aspects in which it differs from a regular collection class:

- A `PCollection` is **immutable**. Once created, you cannot add, remove, or change individual elements.
- A `PCollection` does not support random access to individual elements.
- A `PCollection` belongs to the pipeline in which it is created. You **cannot** share a `PCollection` between `Pipeline` objects.

A `PCollection` may **be physically backed by data in existing storage**, or it may represent data that has not yet been computed. As such, the data in a `PCollection` is immutable. You can use a `PCollection` in computations that generate new pipeline data (as a new `PCollection`); however, you cannot change the elements of an existing `PCollection` once it has been created.

> A `PCollection` does not store data, per se; remember that a `PCollection` may have too many elements to fit in local memory where your Dataflow program is running. When you create or transform a `PCollection`, data isn't copied or moved in memory as with some regular container classes. Instead, a `PCollection` represents a potentially very large data set in the cloud.

### Bounded and Unbounded PCollections

A `PCollection`'s size can be either **bounded** or **unbounded**, and the boundedness (or unboundedness) is determined when you create the `PCollection`. Some root transforms create bounded `PCollections`, while others create unbounded ones; it depends on the source of your input data.

#### Bounded PCollections

Your `PCollection` is bounded if it represents a *fixed data set*, which has a known size that doesn't change. An example of a fixed data set might be "*server logs from the month of October*", or "*all orders processed last week*." `TextIO` and `BigQueryIO`  root transforms create bounded `PCollection`s.

**Data sources that create bounded PCollections include:**
- TextIO
- `BigQueryIO`
- `DatastoreIO`
- Custom bounded data sources you create using the [Custom Source API](https://cloud.google.com/dataflow/model/custom-io-java)

**Data sinks that accept bounded PCollections include:**
- `TextIO`
- `BigQueryIO`
- `DatastoreIO`
- Custom bounded data sinks you create using the [Custom Sink API](https://cloud.google.com/dataflow/model/custom-io-python)

#### Unbounded PCollections

Your `PCollection` is unbounded if it represents a **continuously updating data set**, or streaming data. An example of a continuously updating data set might be "server logs as they are generated" or "all new orders as they are processed."`PubSubIO` root transforms create unbounded `PCollection`s.

Some sources, particularly those that create unbounded `PCollection`s (such as `PubsubIO`), automatically append a timestamp to each element of the collection.

**Data sources that create unbounded PCollections include:**

- `PubsubIO`
- Custom unbounded data sources you create using the [Custom Source API](https://cloud.google.com/dataflow/model/custom-io-java)

**Data sinks that accept unbounded PCollections include:**

- `PubsubIO`
- `BigQueryIO`

#### Processing Characteristics

The bounded (or unbounded) nature of your `PCollection` affects how Dataflow processes your data. **Bounded `PCollection`s can be processed using batch jobs**, which might read the entire data set once, and perform processing in a finite job. **Unbounded `PCollection`s must be processed using streaming jobs**, as the entire collection can never be available for processing at any one time.

When grouping unbounded `PCollection`s, Dataflow requires a concept called [Windowing](https://cloud.google.com/dataflow/model/windowing) to **divide a continuously updating data set into logical windows of finite size**. Dataflow processes each window as a bundle, and processing continues as the data set is generated. See the following section on [Timestamps and Windowing](https://cloud.google.com/dataflow/model/pcollection#Timestamps) for more information.

### PCollection Element Timestamps

Each element in a `PCollection` has an associated **timestamp**. Timestamps are useful for `PCollection`s that contain elements *with an inherent notion of time*. For example, a `PCollection` of orders to process may use the time an order was created as the element timestamp.

**The timestamp for each element is initially assigned by the source that creates the `PCollection`**. **Sources** that create unbounded `PCollection` often *assign each new element a timestamp according to when it was added to the unbounded `PCollection`*.

> Data sources that produce fixed data sets, such as `BigQueryIO` or `TextIO`, also assign timestamps to each element; however, these data sources typically assign the same timestamp (`Long.MIN_VALUE`) to each element.
>
> You can manually assign timestamps to the elements of a `PCollection`. This is commonly done when elements have an inherent timestamp, but that timestamp must be calculated, for example by parsing it out of the structure of the element. To manually assign a timestamp, use a [ParDo](https://cloud.google.com/dataflow/model/par-do) transform; within the `ParDo` transform, your `DoFn` can produce output elements with timestamps. See [Assigning Timestamps](https://cloud.google.com/dataflow/model/windowing#TimeStamping) for more information.

#### Windowing

The timestamps associated with each element in a `PCollection` are used for a concept called **Windowing**. Windowing **divides the elements of a `PCollection` according to their timestamps**. Windowing can be used on all `PCollection`s, but **is required for some computations over unbounded `PCollection`s in order to divide the continuous data stream in finite chunks for processing.**

> **Caution:** Dataflow's default windowing behavior is to assign all elements of a `PCollection` to a single, global window, *even for unbounded PCollections.* Before you use a grouping transform such as `GroupByKey` on an **unbounded** `PCollection`, **you must set a non-global windowing function.** See [Setting Your PCollection's Windowing Function.](https://cloud.google.com/dataflow/model/windowing#Setting) <u>If you don't set a non-global windowing function for your unbounded `PCollection` and subsequently use a grouping transform such as `GroupByKey` or `Combine`, your pipeline will generate an error upon construction and your Dataflow job will fail</u>. You can alternatively set a non-default [Trigger](https://cloud.google.com/dataflow/model/triggers) for a `PCollection` to allow the global window to emit "early" results under some other conditions. Triggers can also be used to allow for conditions such as late-arriving data.

See the section on [Windowing](https://cloud.google.com/dataflow/model/windowing) for more information on how to use Dataflow's Windowing concepts in your pipeline.

### 创建 `PCollection`

- [ ] Reading External Data
- [ ] Creating a PCollection from Data in Local Memory


### Using PCollection with Custom Data Types

You can create a `PCollection` where the element type is a **custom data type** that you provide. This can be useful if you need to create a collection of your own class or structure with specific fields, like a Java class that holds a customer's name, address, and phone number.

When you create a `PCollection` of a custom type, you'll need to provide a `Coder` for that custom type. The `Coder` tells the [Dataflow service](https://cloud.google.com/dataflow/service/dataflow-service-desc) how to **serialize** and **deserialize** the elements of your `PCollection` as your dataset is parallelized and partitioned out to multiple pipeline worker instances; see [data encoding](https://cloud.google.com/dataflow/model/data-encoding) for more information.

Dataflow will attempt to infer a `Coder` for any `PCollection` for which you do not explictly set a `Coder`. The default `Coder` for a custom type is `SerializableCoder`, which uses Java serialization. However, Dataflow recommends using `AvroCoder` as the `Coder` when possible.

You can register `AvroCoder` as the default coder for your data type by using your `Pipeline` object's [CoderRegistry](https://cloud.google.com/dataflow/model/data-encoding#coder-registry). Annotate your class as follows:

```java
@DefaultCoder(AvroCoder.class)
public class MyClass {
  ...
}
```

To ensure that your custom class is compatible with `AvroCoder`, you might need to add some additional annotations—for example, you must annotate null fields in your data type with `org.apache.avro.reflect.Nullable`. See the API for Java reference documentation for [`AvroCoder`](https://cloud.google.com/dataflow/java-sdk/JavaDoc/com/google/cloud/dataflow/sdk/coders/AvroCoder) and the [package documentation for `org.apache.avro.reflect`](http://avro.apache.org/docs/current/api/java/org/apache/avro/reflect/package-summary.html) for more information.

Dataflow's [TrafficRoutes example pipeline](https://github.com/GoogleCloudPlatform/DataflowJavaSDK/blob/master/examples/src/main/java/com/google/cloud/dataflow/examples/complete/TrafficRoutes.java) creates a `PCollection` whose element type is a custom class called `StationSpeed`.  `StationSpeed` registers `AvroCoder` as its default coder as follows:

```java
/**
 * This class holds information about a station reading's average speed.
 */
@DefaultCoder(AvroCoder.class)
static class StationSpeed {
  @Nullable String stationId;
  @Nullable Double avgSpeed;

  public StationSpeed() {}

  public StationSpeed(String stationId, Double avgSpeed) {
    this.stationId = stationId;
    this.avgSpeed = avgSpeed;
  }

  public String getStationId() {
    return this.stationId;
  }
  public Double getAvgSpeed() {
    return this.avgSpeed;
  }
}
```

###Windowing

The Dataflow SDKs use a concept called **Windowing** to subdivide a [PCollection](https://cloud.google.com/dataflow/model/pcollection) according to the timestamps of its individual elements. Dataflow [transforms](https://cloud.google.com/dataflow/model/transforms) that aggregate multiple elements, such as [GroupByKey](https://cloud.google.com/dataflow/model/group-by-key) and [Combine](https://cloud.google.com/dataflow/model/combine), work implicitly on a per-window basis—that is, they process each `PCollection` as a succession of multiple, finite windows, though the entire collection itself may be of unlimited or infinite size.

>1.  a succession of :  一系列的、一连串、一个接一个

The Dataflow SDKs use a related concept called **Triggers** to determine when to "close" each finite window as unbounded data arrives. Using a trigger can help to refine the windowing strategy for your `PCollection` to deal with late-arriving data or to provide early results. See [Triggers](https://cloud.google.com/dataflow/model/triggers) for more information.

#### Windowing 基础

Windowing is most useful with an **unbounded** `PCollection`, which represents a *continuously updating data set of unknown/unlimited size* (e.g. streaming data). Some Dataflow transforms, such as [GroupByKey](https://cloud.google.com/dataflow/model/group-by-key) and [Combine](https://cloud.google.com/dataflow/model/combine), group multiple elements by a common key. Ordinarily, that grouping operation groups all of the elements that have the same key *in the entire data set*. With an unbounded data set, it is impossible to collect all of the elements, since new elements are constantly being added.

 In the Dataflow model, any `PCollection` can be subdivided into logical **windows**. Each element in a `PCollection` gets assigned to one or more windows according to the `PCollection`'s **windowing function**, and each individual window contains a finite number of elements. Grouping transforms then consider each `PCollection`'s elements on a per-window basis. `GroupByKey`, for example, implicitly groups the elements of a `PCollection` by *key and window*. Dataflow *only* groups data within the same window, and doesn't group data in other windows.

> **Caution:** Dataflow's default windowing behavior is to assign all elements of a `PCollection` to a single, global window, *even for unbounded PCollections.* Before you use a grouping transform such as `GroupByKey` on an unbounded `PCollection`, **you must set a non-global windowing function.** See [Setting Your PCollection's Windowing Function.](https://cloud.google.com/dataflow/model/windowing#Setting)
>
> If you don't set a non-global windowing function for your unbounded `PCollection` and subsequently use a grouping transform such as `GroupByKey` or `Combine`, your pipeline will generate an error upon construction and your Dataflow job will fail.
>
> You can alternatively set a non-default [Trigger](https://cloud.google.com/dataflow/model/triggers) for a `PCollection` to allow the global window to emit "early" results under some other conditions.

##### Windowing Constraints
Once you set the windowing function for a PCollection, the elements' windows are used the next time you apply a grouping transform to that PCollection. Dataflow performs the actual window grouping on an as-needed basis; if you set a windowing function using the Window transform, each element is assigned to a window, but the windows are not considered until you group the PCollection with GroupByKey or Combine. This can have different effects on your pipeline.

Consider the example pipeline in Figure 1 below:

![windowing-pipeline-unbounded](windowing-pipeline-unbounded.png) Figure 1: Pipeline Applying Windowing

In the above pipeline, we create an unbounded `PCollection` by reading a set of key/value pairs using [PubsubIO](https://cloud.google.com/dataflow/model/pubsub-io), and then apply a windowing function to that collection using the `Window` transform. We then apply a `ParDo` to the the collection, and then later group the result of that `ParDo` using `GroupByKey`. **The windowing function *has no effect on the ParDo transform*, because the windows are not actually used until they're needed for the `GroupByKey`.**

Subsequent transforms, however, are applied to the result of the `GroupByKey`--that is, data grouped by *both key and window.*

> 这张图重要

#####Using Windowing With Bounded PCollections

You can use windowing with fixed-size data sets in **bounded** `PCollection`s. Note, however, that **windowing considers only the implicit timestamps attached to each element of a `PCollection`, *and* data sources that create fixed data sets (such as `TextIO` and `BigQueryIO`) assign the same timestamp to every element**. This means that **all the elements are by default part of a single, global window**. Having all elements assigned to the same window will cause a pipeline to execute in classic MapReduce batch style.

To use windowing with fixed data sets, you can [**assign your own timestamps**](https://cloud.google.com/dataflow/model/windowing#TimeStamping) to each element. To assign timestamps to elements, you use a `ParDo` transform with a `DoFn` that outputs each element with a new timestamp.

Using windowing with a bounded `PCollection` can affect how your pipeline processes data. For example, consider the following pipeline:

![unwindowed-pipeline-bounded](unwindowed-pipeline-bounded.png)
*Figure 2: GroupByKey and ParDo without windowing, on a bounded collection*.

In the above pipeline, we create a **bounded** `PCollection` by reading a set of key/value pairs using [TextIO](https://cloud.google.com/dataflow/model/text-io). We then group the collection using `GroupByKey`, and apply a `ParDo` transform to the grouped `PCollection`. In this example, the `GroupByKey` creates a collection of unique keys, and then `ParDo` gets applied *exactly once per key*.

Now, consider the same pipeline, but using a windowing function:

![windowing-pipeline-bounded](windowing-pipeline-bounded.png)
*Figure 3: GroupByKey and ParDo with windowing, on a bounded collection*.

As before, the pipeline creates a bounded `PCollection` of key/value pairs. We then set a [windowing function](https://cloud.google.com/dataflow/model/windowing#Setting) for that `PCollection`. The `GroupByKey` transform now groups the elements of the `PCollection` *by both key and window*. The subsequent `ParDo` transform gets applied *multiple times per key*, once for each window.

####Windowing Functions

The Dataflow SDKs let you define different kinds of windows to divide the elements of your `PCollection`. The SDK provides several windowing functions, including:

- Fixed Time Windows
- Sliding Time Windows
- Per-Session Windows
- Single Global Window

> Note that each element can logically belong to *more than one window*, depending on the windowing function you use. **Sliding time windowing, for example, creates overlapping windows wherein a single element can be assigned to multiple windows**.

##### Fixed Time Windows

The most simple form of windowing is a **fixed time window**: given a timestamped `PCollection`, which might be continously updating, each window might capture (for example) five minutes worth of elements.

A fixed time window represents the time interval in the data stream that defines <u>a bundle of</u> data for processing. Consider a window that operates at five-minute intervals: all of the elements in your unbounded `PCollection` with timestamp values between 0:00:00 and 0:04:59 belong to the first window, elements with timestamp values between 0:05:00 and 0:09:59 belong to the second window, and so on.

![A diagram representing fixed-time windowing.](fixed-time-windows.png)
*Figure 4: Fixed time windows, 30s in size*.

##### Sliding Time Windows

A **sliding time window** also uses time intervals in the data stream to define bundles of data; however, with sliding time windowing, **the windows overlap**. Each window might capture five minutes worth of data, but a new window starts every ten seconds. The frequency with which sliding windows begin is called the ***period***. Therefore, our example would have a window *size* of five minutes and a *period* of ten seconds.

Because multiple windows overlap, most elements in a data set will belong to more than one window. This kind of Windowing is useful for taking running averages of data; using sliding time windows, **you can compute a running average of the past five minutes' worth of data, updated every ten seconds**, in our example.

![A diagram representing sliding-time windowing.](sliding-time-windows.png) 
*Figure 5: Sliding time windows, with 1 minute window size and 30s window period*.

##### Session Windows

A **session window** function defines windows around areas of <u>concentration</u> in the data. **Session windowing is useful for data that is irregularly distributed with respect to time**; for example, a data stream representing user mouse activity may have long periods of idle time interspersed with high concentrations of clicks. Session windowing groups the high concentrations of data into separate windows and filters out the idle sections of the data stream.

Note that **session windowing applies on a per-key basis**; that is, grouping into sessions **only** takes into account data that has the same key. **Each key in your data collection will therefore be grouped into disjoint windows of differing sizes.**

The simplest kind of session windowing specifies a *minimum gap duration*. All data arriving below a minimum threshold of time delay is grouped into the same window. If data arrives after the minimum specified gap duration time, this initiates the start of a new window.

![A diagram representing session windowing.](session-windows.png)
*Figure 5: Session windows, with a minimum gap duration. Note how each data key has different windows, according to its data distribution.*

##### Single Global Window

By default, all data in a `PCollection` is assigned to a single global window. If your data set is of a fixed size, you can leave the global window default for your `PCollection`. If the elements of your `PCollection` all belong to a single global window, your pipeline will execute much like a batch processing job (as in MapReduce-based processing).

>You can use a single global window if you are working with an unbounded data set, e.g. from a streaming data source; however, use caution when applying aggregating transforms such as [GroupByKey](https://cloud.google.com/dataflow/model/group-by-key) and [Combine](https://cloud.google.com/dataflow/model/combine). A single global window with a default trigger generally requires the entire data set to be available before processing, which is not possible with continuously updating data.
>
>To perform aggregations on an unbounded `PCollection` that uses global windowing, you should specify a **non-default** [trigger](https://cloud.google.com/dataflow/model/triggers) for that `PCollection`. If you attempt to perform an aggregation such as `GroupByKey` on an unbounded, globally windowed `PCollection` with default triggering, the Cloud Dataflow service will generate an exception when your pipeline is constructed.

##### Other Windowing Functions

The Dataflow SDKs provide more windowing functions beyond fixed, sliding, session, and global windows, such as **Calendar-based windows**.

#### Setting Your PCollection's Windowing Function

You can set the windowing function for a `PCollection` by applying the `Window` transform. When you apply the `Window` transform, you must provide a `WindowFn`. The `WindowFn` determines the windowing function your `PCollection` will use for subsequent grouping transforms, such as a fixed or sliding time window.

The Dataflow SDKs provide pre-defined `WindownFn`s for the basic [windowing functions](https://cloud.google.com/dataflow/model/windowing#Functions), or you can define your own `WindowFn` in advanced cases.

> Technically, like all transforms, `Window` takes an input `PCollection` and outputs a new `PCollection` with each element assigned to one or more logical, finite windows.
>
> When setting a windowing function, you may also want to set a trigger for your `PCollection`. The trigger determines when each individual window is aggregated and emitted, and helps refine how the windowing function performs with respect to late data and computing early results. See [Triggers](https://cloud.google.com/dataflow/model/triggers) for more information.

##### Setting Fixed-Time Windows

The following example code shows how to apply `Window` to divide a `PCollection` into fixed windows, each one minute in length:

```java
PCollection<String> items = ...;
PCollection<String> fixed_windowed_items = items.apply(
  Window.<String>into(FixedWindows.of(Duration.standardMinutes(1))));
```

##### Setting Sliding Time Windows

The following example code shows how to apply `Window` to divide a `PCollection` into sliding time windows. Each window is 30 minutes in length, and a new window begins every five seconds:

```java
PCollection<String> items = ...;
  PCollection<String> sliding_windowed_items = items.apply(
    Window.<String>into(SlidingWindows.of(Duration.standardMinutes(30)).
                          every(Duration.standardSeconds(5))));
```

##### Setting Session Windows

The following example code shows how to apply `Window` to divide a `PCollection` into session windows, where each session must be separated by a time gap of at least 10 minutes:

```java
PCollection<String> items = ...;
  PCollection<String> session_windowed_items = 
    items.apply(Window.<String>into(Sessions.withGapDuration(Duration.standardMinutes(10))));
```

##### Setting a Single Global Window

If your `PCollection` is **bounded** (the size is fixed), you can assign all the elements to a single global window. The following example code shows how to set a single global window for a `PCollection`:

To set a single global window for your `PCollection`, pass `new GlobalWindows()` as the `WindowFn` when you apply the `Window` transform. The following example code shows how to apply `Window` to assign a `PCollection` into a single global window:

```java
PCollection<String> items = ...;
  PCollection<String> batch_items = items.apply(Window.<String>into(new GlobalWindows()));
```

#### Time Skew, Data Lag, and Late Data

In any data processing system, there is a certain amount of lag between the time a data event occurs (the "event time", determined by the timestamp on the data element itself) and the time the actual data element gets processed at any stage in your pipeline (the "processing time", determined by the clock on the system processing the element).

> In a perfect system, the event time for each data element and the processing time would be equal, or at least have a *g*. However, in any real-world computing system, data generation and delivery are subject to any number of temporal limitations. In large or distributed systems, such as a distributed collection of web front-ends generating customer orders or log files, there are no guarantees that data events will appear in your pipeline in the same order that they were generated in various places on the web.

**For example**, let's say we have a `PCollection` that's using fixed-time windowing, with windows that are five minutes long. For each window, Dataflow must collect all the data with an *event time* timestamp in the given window range (between 0:00 and 4:59 in the first window, for instance). Data with timestamps outside that range (data from 5:00 or later) belong to a different window.

However, data isn't always guaranteed to arrive in a pipeline in correct time order, or to always arrive at predictable intervals. Dataflow tracks a *watermark*, which is the system's notion of when all data in a certain window can be expected to have arrived in the pipeline. <u>Data that arrives with a timestamp after the watermark is considered</u> **late data**.

From our example, suppose we have a simple watermark that assumes approximately 30s of lag time between the data timestamps (the event time) and the time the data appears in the pipeline (the processing time), then Dataflow would close the first window at 5:30. If a data record arrives at 5:34, but with a timestamp that would put it in the 0:00-4:59 window (say, 3:38), then that record is late data.

> **Note:** For simplicity, we've assumed that we're using a very straightforward watermark that estimates the lag time/time skew. In practice, your `PCollection`'s data source determines the watermark, and watermarks can be more precise or complex.

##### Managing Time Skew and Late Data

You can allow late data by invoking the `.withAllowedLateness` operation when you set your `PCollection`'s windowing strategy. The following code example demonstrates a windowing strategy that will allow late data up to two days after the end of a window.

```java
PCollection<String> items = ...;
PCollection<String> fixed_windowed_items = items.apply(
  Window.<String>into(FixedWindows.of(Duration.standardMinutes(1))).
         withAllowedLateness(Duration.standardDays(2)));
```

When you set `.withAllowedLateness` on a `PCollection`, that allowed lateness propagates forward to any subsequent `PCollection` derived from the first `PCollection` you applied allowed lateness to. If you want to change the allowed lateness later in your pipeline, you must do so explictly by applying `Window.withAllowedLateness()` again.

You can also use Dataflow's [Triggers](https://cloud.google.com/dataflow/model/triggers) API to help you refine the windowing strategy for a `PCollection`. You can use triggers to determine exactly when each individual window aggregates and reports its results, including how the window emits late elements.

> **Note:** Dataflow's default windowing and trigger strategies *discard* late data. If you want to ensure that your pipeline handles instances of late data, you'll need to explicitly set `.withAllowedLateness` when you set your `PCollection`'s windowing strategy and set triggers for your `PCollection`s accordingly.

#### Adding Timestamps To a PCollection's Elements

You can assign new timestamps to the elements of a `PCollection` by applying a [ParDo](https://cloud.google.com/dataflow/model/par-do) transform that outputs new elements with timestamps that you set. Assigning timestamps can be useful if you want to use Dataflow's windowing features, but your data set comes from a source without implicit timestamps (such as a file from [TextIO](https://cloud.google.com/dataflow/model/text-io)).

**This is a good pattern to follow when your data set includes timestamp data, but the timestamps are *not* generated by the Dataflow data source**. An example might be if your pipeline reads log records from an input file, and each log record includes a timestamp field; since your pipeline reads the records in from a file, the file source doesn't assign timestamps automatically. You can parse the timestamp field from each record and use a `ParDo` transform to attach the timestamps to each element in your `PCollection`.

To assign timestamps, your `ParDo` transform needs to use a `DoFn` that outputs elements using `ProcessContext.outputWithTimestamp` (rather than the usual `ProcessContext.output` used to emit elements to the main output collection). The following example code shows a `ParDo` with a `DoFn` that outputs elements with new timestamps:

```java
PCollection<LogEntry> unstampedLogs = ...;
PCollection<LogEntry> stampedLogs =
  unstampedLogs.apply(ParDo.of(new DoFn<LogEntry, LogEntry>() {
    public void processElement(ProcessContext c) {
      // Extract the timestamp from log entry we're currently processing.
      Instant logTimeStamp = extractTimeStampFromLogEntry(c.element());
      // Use outputWithTimestamp to emit the log entry with timestamp attached.
      c.outputWithTimestamp(c.element(), logTimeStamp);
    }
  }));
```



------

# 关于WithXXX

Google "java naming convention withXXX"

Here's another example you'll see for builder patterns.

```java
public class Thing {
    String name;

    public Thing () {
    }

    public void setName(String name) {
        this.name = name;
    }

    public Thing withName(String name) {
        this.name = name;
        return this;
    }
}
```

The "withName(String name)" method allows you to do stuff like this:

```java
Thing t = new Thing().withName("bob");
```

This becomes especially useful when there are lots of properties to set:

```java
Thing t = new Thing()
             .withName("bob")
             .withAge(40)
             .withGender("M");
```

**The reason you can do this kind of chaining, is because each "withXXX(...)" method itself returns the "new Thing()" you just created (using the "this" keyword)**. Think of it as a way for the method to say "*Okay you called me. Now I'll return a pointer to the object you used to call me so you can call it right back if you want instead of having to use a new line of code after a colon (;)*."

You'll see this a lot for complex objects which can be instantiated various different ways (e.g. query objects). You'll also see this in mocking frameworks. Generally, the chaining makes creating objects more **versatile** and easier to read.

**Note, in the examples above I use the method naming convention "withXXX(...)" so as to not violate POJO specifications which certain frameworks rely on (e.g. Spring).**

There are some other edge cases where usage of "this" gets weird when using anonymous classes, but that's a whole other topic.