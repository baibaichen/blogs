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

###Overview

The Dataflow SDKs use a specialized class called `PCollection` to represent data in a pipeline. A `PCollection` represents a multi-element data set.

You can think of a `PCollection` as "pipeline" data. Dataflow's [transforms](https://cloud.google.com/dataflow/model/transforms) use `PCollection`s as inputs and outputs; as such, if you want to work with data in your pipeline, it must be in the form of a `PCollection`. **Each `PCollection` is owned by a specific `Pipeline` object, and only that `Pipeline` object can use it.**

> **IMPORTANT:** This document contains information about **unbounded** `PCollection`s and **Windowing**. These concepts refer to the Dataflow Java SDK only, and are not yet available in the Dataflow Python SDK.

#### PCollection Characteristics

**A `PCollection` represents a potentially large, immutable "bag" of elements**. There is **no upper limit** on how many elements a `PCollection` can contain; any given `PCollection` might fit in memory, or it might represent a very large data set backed by a persistent data store.

> The elements of a `PCollection` can be of any type, but must all be of the same type. However, Dataflow needs to be able to encode each individual element as a byte string in order to support distributed processing. The Dataflow SDKs provide a [Data Encoding](https://cloud.google.com/dataflow/model/data-encoding.html) mechanism that includes built in encodings for commonly used types and support for specifying custom encodings as needed. Creating a valid encoding for an aribitrary type can be challenging, but you can [construct](https://cloud.google.com/dataflow/model/pcollection#user-data-types) custom encoding for simple structured types.

##### PCollection Limitations

A `PCollection` has several key aspects in which it differs from a regular collection class:

- A `PCollection` is **immutable**. Once created, you cannot add, remove, or change individual elements.
- A `PCollection` does not support random access to individual elements.
- A `PCollection` belongs to the pipeline in which it is created. You **cannot** share a `PCollection` between `Pipeline` objects.

A `PCollection` may **be physically backed by data in existing storage**, or it may represent data that has not yet been computed. As such, the data in a `PCollection` is immutable. You can use a `PCollection` in computations that generate new pipeline data (as a new `PCollection`); however, you cannot change the elements of an existing `PCollection` once it has been created.

> A `PCollection` does not store data, per se; remember that a `PCollection` may have too many elements to fit in local memory where your Dataflow program is running. When you create or transform a `PCollection`, data isn't copied or moved in memory as with some regular container classes. Instead, a `PCollection` represents a potentially very large data set in the cloud.

#### Bounded and Unbounded PCollections

A `PCollection`'s size can be either **bounded** or **unbounded**, and the boundedness (or unboundedness) is determined when you create the `PCollection`. Some root transforms create bounded `PCollections`, while others create unbounded ones; it depends on the source of your input data.

##### Bounded PCollections

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

##### Unbounded PCollections

Your `PCollection` is unbounded if it represents a **continuously updating data set**, or streaming data. An example of a continuously updating data set might be "server logs as they are generated" or "all new orders as they are processed."`PubSubIO` root transforms create unbounded `PCollection`s.

Some sources, particularly those that create unbounded `PCollection`s (such as `PubsubIO`), automatically append a timestamp to each element of the collection.

**Data sources that create unbounded PCollections include:**

- `PubsubIO`
- Custom unbounded data sources you create using the [Custom Source API](https://cloud.google.com/dataflow/model/custom-io-java)

**Data sinks that accept unbounded PCollections include:**

- `PubsubIO`
- `BigQueryIO`

##### Processing Characteristics

The bounded (or unbounded) nature of your `PCollection` affects how Dataflow processes your data. **Bounded `PCollection`s can be processed using batch jobs**, which might read the entire data set once, and perform processing in a finite job. **Unbounded `PCollection`s must be processed using streaming jobs**, as the entire collection can never be available for processing at any one time.

When grouping unbounded `PCollection`s, Dataflow requires a concept called [Windowing](https://cloud.google.com/dataflow/model/windowing) to **divide a continuously updating data set into logical windows of finite size**. Dataflow processes each window as a bundle, and processing continues as the data set is generated. See the following section on [Timestamps and Windowing](https://cloud.google.com/dataflow/model/pcollection#Timestamps) for more information.

#### PCollection Element Timestamps

Each element in a `PCollection` has an associated **timestamp**. Timestamps are useful for `PCollection`s that contain elements *with an inherent notion of time*. For example, a `PCollection` of orders to process may use the time an order was created as the element timestamp.

**The timestamp for each element is initially assigned by the source that creates the `PCollection`**. **Sources** that create unbounded `PCollection` often *assign each new element a timestamp according to when it was added to the unbounded `PCollection`*.

> Data sources that produce fixed data sets, such as `BigQueryIO` or `TextIO`, also assign timestamps to each element; however, these data sources typically assign the same timestamp (`Long.MIN_VALUE`) to each element.
>
> You can manually assign timestamps to the elements of a `PCollection`. This is commonly done when elements have an inherent timestamp, but that timestamp must be calculated, for example by parsing it out of the structure of the element. To manually assign a timestamp, use a [ParDo](https://cloud.google.com/dataflow/model/par-do) transform; within the `ParDo` transform, your `DoFn` can produce output elements with timestamps. See [Assigning Timestamps](https://cloud.google.com/dataflow/model/windowing#TimeStamping) for more information.

##### Windowing

The timestamps associated with each element in a `PCollection` are used for a concept called **Windowing**. Windowing **divides the elements of a `PCollection` according to their timestamps**. Windowing can be used on all `PCollection`s, but **is required for some computations over unbounded `PCollection`s in order to divide the continuous data stream in finite chunks for processing.**

> **Caution:** Dataflow's default windowing behavior is to assign all elements of a `PCollection` to a single, global window, *even for unbounded PCollections.* Before you use a grouping transform such as `GroupByKey` on an **unbounded** `PCollection`, **you must set a non-global windowing function.** See [Setting Your PCollection's Windowing Function.](https://cloud.google.com/dataflow/model/windowing#Setting) <u>If you don't set a non-global windowing function for your unbounded `PCollection` and subsequently use a grouping transform such as `GroupByKey` or `Combine`, your pipeline will generate an error upon construction and your Dataflow job will fail</u>. You can alternatively set a non-default [Trigger](https://cloud.google.com/dataflow/model/triggers) for a `PCollection` to allow the global window to emit "early" results under some other conditions. Triggers can also be used to allow for conditions such as late-arriving data.

See the section on [Windowing](https://cloud.google.com/dataflow/model/windowing) for more information on how to use Dataflow's Windowing concepts in your pipeline.

#### 创建 `PCollection`

- [ ] Reading External Data
- [ ] Creating a PCollection from Data in Local Memory


#### Using PCollection with Custom Data Types

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

### Triggers

When collecting the data in a `PCollection` and grouping that data in finite windows, **Dataflow needs some way to determine when to emit the aggregated results of each window**. Given [time skew and data lag](https://cloud.google.com/dataflow/model/windowing#Advanced), Dataflow uses a mechanism called **triggers** to determine when "enough" data has been collected in a window, after which it emits the aggregated results of that window, each of which is referred to as a ***pane***.

Dataflow's triggers system provides several different ways to determine when to emit the aggregated results of a given window, depending on your system's data processing needs. **For example**, a system that requires prompt or time-sensitive updates might use *a strict time-based trigger that emits a window every N seconds*, valuing promptness over data completeness; a system that values data completeness more than the exact timing of results might use *a data-based trigger that waits for a certain number of data records* to accumulate before closing a window.

**Triggers** are particularly useful for handling two kinds of conditions in your pipeline:

- Triggers can help you handle instances of late data.
- Triggers can help you emit early results, before all the data in a given window has arrived.

> **Note:** You can set a trigger for an unbounded `PCollection` that uses a single global window for its windowing function. This can be useful when you want your pipeline to provide periodic updates on an unbounded data set—for example, a running average of all data provided to the present time, updated every *N* seconds or every *N* elements.
>
> promptness：及时性

#### Types of Triggers

Dataflow provides a number of pre-built triggers that you can set for your `PCollection`s. There are three major kinds of triggers:

- **Time-based triggers**. These triggers operate on a time reference--either event time (as indicated by the timestamp on each data element) or the processing time (the time when the data element is processed at any given stage in the pipeline).
- **Data-driven triggers**. These triggers operate by examining the data as it arrives in each window and firing when a data condition that you specify is met. For example, you can set a trigger to emit results from a window when that window has received a certain number of data elements.
- **Composite triggers**. These triggers combine multiple time-based or data-driven triggers in some logical way. You can set a composite trigger to fire when all triggers are met (logical AND), when any trigger is met (logical OR), etc.

##### Time-Based Triggers

Dataflow's time-based triggers include `AfterWatermark` and `AfterProcessingTime`. These triggers take a time reference in either event time or processing time, and set a timer based on that time reference.

###### AfterWatermark

**The `AfterWatermark` trigger operates on *event time***. The `AfterWatermark` trigger will emit the contents of a window after the watermark passes the end of the window, based on the timestamps attached to the data elements. **The watermark is a global progress metric, Dataflow's notion of input completeness within your pipeline at any given point.**

The `AfterWatermark` trigger ***only* fires when the watermark passes the end of the window**—it is the primary trigger that Dataflow uses to emit results when the system estimates that it has all the data in a given time-based window.

###### AfterProcessingTime

**The `AfterProcessingTime` trigger operates on *processing time***.  The `AfterProcessingTime` trigger emits a window after a certain amount of processing time has passed since the time reference, such as the start of a window. The processing time is determined by the system clock, rather than the data element's timestamp.

The `AfterProcessingTime` trigger is **useful for triggering early results from a window, particularly a window with a large time frame such as a single global window.**

##### Data-Driven Triggers

Dataflow currently provides only one data-driven trigger, `AfterPane.elementCountAtLeast`. This trigger works on a straight element count; it fires after the current pane has collected at least *N* elements.

The `AfterPane.elementCountAtLeast()` is a good way to cause a window to emit early results, before all the data has accumulated, especially in the case of a single global window.

##### Default Trigger

The default trigger for a `PCollection` is event time-based, and emits the results of the window when the system's watermark (Dataflow's notion of when it "should" have all the data) passes the end of the window. The default triggering configuration **emits exactly once, and late data is discarded**. This is because the default windowing and trigger configuration has an allowed lateness value of 0. See [Handling Late Data](https://cloud.google.com/dataflow/model/triggers#lateData) for information on modifying this behavior.

**The watermark depends on the data source**; in some cases, it is an estimate. In others, such as Pub/Sub with system-assigned timestamps, the watermark can provide an exact bound of what data the pipeline has processed.

##### Handling Late Data

If your pipeline needs to handle late data (data that arrives after the watermark passes the end of the window), you can apply an *allowed lateness* when setting your windowing and trigger configuration. This will give your trigger the opportunity to react to the late data; in the default triggering configuration it will emit new results immediately whenever late data arrives.

You set the allowed lateness by using `.withAllowedLateness()` when setting your window and trigger, as follows:

```java
PCollection<String> pc = ...;
pc.apply(
  Window<String>.into(FixedWindows.of(1,TimeUnit.MINUTES))
                .triggering(AfterProcessingTime.pastFirstElementInPane()
                                               .plusDelayOf(Duration.standardMinutes(1)))
                .withAllowedLateness(Duration.standardMinutes(30));
```

This allowed lateness propagates to all `PCollection`s derived as a result of applying transforms to the original `PCollection`. If you want to change the allowed lateness later in your pipeline, you can apply `Window.withAllowedLateness()` again, explicitly.

#### Setting a Trigger

When you set a windowing function for a `PCollection` by using the `Window` transform, you can also specify a trigger.

You set the trigger(s) for a `PCollection` by invoking the method `.triggering()` on the result of your `Window.into()`transform, as follows:

```java
PCollection<String> pc = ...;
pc.apply(Window<String>.into(FixedWindows.of(1, TimeUnit.MINUTES))
                       .triggering(AfterProcessingTime.pastFirstElementInPane()
                                                      .plusDelayOf(Duration.standardMinutes(1)))
                       .discardingFiredPanes());
```

The preceding code sample sets a trigger for a `PCollection`; the trigger is time-based, and emits each window one minute after the first element in that window has been processed. The last line in the code sample, `.discardingFiredPanes()`, is the window's **accumulation mode**.

##### Window Accumulation Modes

When you specify a trigger, you must also set the the window's **accumulation mode**. *When a trigger fires, it emits the current contents of the window as a pane*. Since a trigger can fire multiple times, the accumulation mode determines whether the system *accumulates* the window panes as the trigger fires, or *discards* them.

To set a window to accumulate the panes that are produced when the trigger fires, invoke `.accumulatingFiredPanes()` when you set the trigger. To set a window to discard fired panes, invoke `.discardingFiredPanes()`.

Let's look an an example that uses a `PCollection` with fixed-time windowing and a data-based trigger. (This is something you'd do if, for example, each window represented a ten-minute running average, but you wanted to display the current value of the average in a UI more frequently than every ten minutes.) We'll assume the following conditions:

- The `PCollection` uses 10-minute fixed-time windows.
- The `PCollection` has a repeating trigger that fires every time 3 elements arrive.

The following diagram shows data events as they arrive in the `PCollection` and are assigned to windows*:

![A diagram of data, per key, arriving in a PCollection with fixed-time windowing.](trigger-accumulation.png)
*Figure 1: Data events in a PCollection with fixed-time windowing*.

> **Note:** To keep the diagram a bit simpler, we'll assume that the events all arrive in the pipeline in order.

For simplicity, let's only consider values associated with key X.

###### Accumulating Mode

If our trigger is set to `.accumulatingFiredPanes`, the trigger emits the following values each time it fires (remember, the trigger fires every time three elements arrive):

```properties
  Key X:
    First trigger firing:  [5, 8, 3]
    Second trigger firing: [5, 8, 3, 15, 19, 23]
    Third trigger firing:  [5, 8, 3, 15, 19, 23, 9, 13, 10]
```

###### Discarding Mode

If our trigger is set to `.discardingFiredPanes`, the trigger emits the following values on each firing:

```properties
  Key X:
    First trigger firing:  [5, 8, 3]
    Second trigger firing: [15, 19, 23]
    Third trigger firing:  [9, 13, 10]
```

###### Effects of Accumulating vs. Discarding

Now, let's add a per-key calculation to our pipeline. Every time the trigger fires, the pipeline applies a `Combine.perKey`that calculates a mean average for all values associated with each key in the window.

Again, let's just consider key X:

With the trigger set to `.accumulatingFiredPanes`:

```properties
  Key X:
    First trigger firing:  [5, 8, 3]
      Average after first trigger firing: 5.3
    Second trigger firing: [5, 8, 3, 15, 19, 23]
      Average after second trigger firing: 12.167
    Third trigger firing:  [5, 8, 3, 15, 19, 23, 9, 13, 10]
      Average after third trigger firing: 11.667
```

With the trigger set to `.discardingFiredPanes`:

```properties
  Key X:
    First trigger firing:  [5, 8, 3]
      Average after first trigger firing: 5.3
    Second trigger firing: [15, 19, 23]
      Average after second trigger firing: 19
    Third trigger firing:  [9, 13, 10]
      Average after third trigger firing: 10.667
```

Note that the `Combine.perKey` that computes the mean average produces different results in each case.

In general, a trigger set to `.accumulatingFiredPanes` always outputs all data in a given window, *including any elements previously triggered*. A trigger set to `.discardingFiredPanes` outputs incremental changes since the last time the trigger fired. **Accumulating mode is most appropriate before a grouping operation that combines or otherwise updates the elements; otherwise, use discarding mode**.

> **Note:** Using `.accumulatingFiredPanes` causes a trigger to output all data in a given window every time the trigger fires. If your pipeline performs successive grouping operations (i.e. more than one `GroupByKey` or `Combine.perKey` in succession), an accumulating trigger will cause elements to be counted twice. This can lead to potentially incorrect results when combining, or out-of-memory errors if your pipeline is continually grouping accumulating data without combining. To avoid these issues, you can set a trigger with `.discardingFiredPanes` on the `PCollection` resulting from your first `GroupByKey` or  `Combine.perKey` operation. Alternatively, you can set `.accumulatingFiredPanes` only on the `PCollection` just before the final `GroupByKey`.

##### Trigger Continuation

When you apply an aggregating transform such as [`GroupByKey`](https://cloud.google.com/dataflow/model/group-by-key) or [`Combine.perKey`](https://cloud.google.com/dataflow/model/combine) to a `PCollection` for which you've specified a trigger, ***keep in mind*** that the `GroupByKey` or `Combine.perKey` produces a new output `PCollection`; the trigger that you set for the input collection **does not** propagate onto the new output collection.

***Instead***, the Dataflow SDK creates <u>a comparable trigger</u> for the output `PCollection` based on the trigger that you specified for the input collection. The new trigger attempts to emit elements as fast as reasonably possible, at roughly the rate specified by the original trigger on the input `PCollection`. Dataflow determines the properties of the new trigger based on the parameters you provided for the input trigger:

- **`AfterWatermark`'s continuation trigger is identical to the original trigger by default**. If the `AfterWatermark`trigger has early or late firings specified, the early or late firings of the continuation will be the continuation of the original trigger's early or late firings.
- `AfterProcessingTime`'s default continuation trigger fires after the synchronized processing time for the amalgamated elements, and does not propagate any additional delays. For example, consider a trigger such as`AfterProcessingTime.pastFirstElementInPane().alignedTo(15 min).plusDelayOf(1 hour)`. After a `GroupByKey`, the trigger Dataflow supplies for the output collection will be synchronized to the same aligned time for each key, but will not retain the 1 hour delay.
- `AfterCount`'s default continuation trigger fires on every element. For example, `AfterCount(n)` on the input collection becomes `AfterCount(1)` on the output collection.

If you feel the trigger that Dataflow generates for a `PCollection` output from `GroupByKey` or `Combine.perKey` is not sufficient, you should **explicitly set a new trigger** for that collection.

> a comparable trigger：这里的意思是创建一个**同类的**触发器

#### Combining Triggers

In Dataflow, you can combine multiple triggers to form **composite triggers**. You can use Dataflow's composite triggers system to logically combine multiple triggers. You can also specify a trigger to emit results repeatedly, at most once, or under other custom conditions.

##### Composite Trigger Types

Dataflow includes the following composite triggers:

- You can add additional early firings or late firings to `AfterWatermark.pastEndOfWindow`.
- `Repeatedly.forever` specifies a trigger that executes forever. Any time the trigger's conditions are met, it causes a window to emit results, then resets and <u>starts over</u>. It can be useful to combine `Repeatedly.forever` with `.orFinally` to specify a condition to cause the repeating trigger to stop.
- `AfterEach.inOrder` combines multiple triggers to fire in a specific sequence. Each time a trigger in the sequence emits a window, the sequence advances to the next trigger.
- `AfterFirst` takes multiple triggers and emits the first time *any* of its argument triggers is satisfied. This is equivalent to a logical OR operation for multiple triggers.
- `AfterAll` takes multiple triggers and emits when *all* of its argument triggers are satisfied. This is equivalent to a logical AND operation for multiple triggers. 
- `orFinally` can serve as a final condition to cause any trigger to fire one final time and never fire again.

> Start over：重新开始

##### Composition with AfterWatermark.pastEndOfWindow

Some of the most useful composite triggers fire a single time when the system estimates that all the data has arrived (i.e. when the watermark passes the end of the window) combined with either, or both, of the following:

- **Speculative firings** that <u>precede</u> the watermark passing the end of the window to allow faster processing of partial results.
- **Late firings** that happen after the watermark passes the end of the window, to allow for handling late-arriving data

You can express this pattern using `AfterWatermark.pastEndOfWindow`. For example, the following example trigger code fires on the following conditions:

- On the system's estimate that all the data has arrived (the watermark passes the end of the window)
- Any time late data arrives, after a ten-minute delay
- After two days, we assume no more data of interest will arrive, and the trigger stops executing

```java
.apply(Window
       .triggering(AfterWatermark
                   .pastEndOfWindow()
                   .withLateFirings(AfterProcessingTime
                                    .pastFirstElementInPane()
                                    .plusDelayOf(Duration.standardMinutes(10))))
       .withAllowedLateness(Duration.standardDays(2)));
```

##### Other Composite Triggers

You can also build other sorts of composite triggers. The following example code shows a simple composite trigger that fires whenever the pane has at least 100 elements, or after a minute.

```java
Repeatedly.forever(AfterFirst.of(
                           AfterPane.elementCountAtLeast(100),
                           AfterProcessingTime.pastFirstElementInPane().
                               plusDelayOf(Duration.standardMinutes(1))))
```

##### Trigger Grammar

The following grammar describes the various ways that you can combine triggers into composite triggers:

```
TRIGGER ::=
   ONCE_TRIGGER
   Repeatedly.forever(TRIGGER)
   TRIGGER.orFinally(ONCE_TRIGGER)
   AfterEach.inOrder(TRIGGER, TRIGGER, ...)

ONCE_TRIGGER ::=
  TIME_TRIGGER
  WATERMARK_TRIGGER
  AfterPane.elementCountAtLeast(Integer)
  AfterFirst.of(ONCE_TRIGGER, ONCE_TRIGGER, ...)
  AfterAll.of(ONCE_TRIGGER, ONCE_TRIGGER, ...)

TIME_TRIGGER ::=
  AfterProcessingTime.pastFirstElementInPane()
  TIME_TRIGGER.alignedTo(Duration)
  TIME_TRIGGER.alignedTo(Duration, Instant)
  TIME_TRIGGER.plusDelayOf(Duration)
  TIME_TRIGGER.mappedBy(Instant -> Instant)

WATERMARK_TRIGGER ::=
  AfterWatermark.pastEndOfWindow()
  WATERMARK_TRIGGER.withEarlyFirings(ONCE_TRIGGER)
  WATERMARK_TRIGGER.withLateFirings(ONCE_TRIGGER)

Default = Repeatedly.forever(AfterWatermark.pastEndOfWindow())
```
###Handling Multiple PCollections

Some Dataflow SDK [transforms](https://cloud.google.com/dataflow/model/transforms) can take multiple `PCollection` objects as input or produce multiple `PCollection`objects as output. The Dataflow SDKs provide several different ways to bundle together multiple `PCollection` objects.

For `PCollection` objects storing the same data type, the Dataflow SDKs also provide the [Flatten](https://cloud.google.com/dataflow/model/multiple-pcollections#flatten) or [Partition](https://cloud.google.com/dataflow/model/multiple-pcollections#partition) transforms. `Flatten` merges multiple `PCollection` objects into a single logical `PCollection`, while `Partition` splits a single `PCollection` into a fixed number of smaller collections.

- [ ] [Handling Multiple PCollections](https://cloud.google.com/dataflow/model/multiple-pcollections)

###Data Encoding

When you create or output pipeline data, you'll need to specify how the elements in your `PCollection`s are encoded and decoded to and from byte strings. **Byte strings** are used for intermediate storage as well reading from sources and writing to sinks. The Dataflow SDKs use objects called **coders** to describe how the elements of a given `PCollection`should be encoded and decoded.

- [ ] [Data Encoding](https://cloud.google.com/dataflow/model/data-encoding)

##Transforms

###Overview

In a Dataflow pipeline, a **transform** represents a step, or a processing operation that transforms data. A transform can perform nearly any kind of processing operation, including *performing mathematical computations on data*, *converting data from one format to another*, *grouping data together*, *reading and writing data*, *filtering data to output only the elements you want*, or *combining data elements into single values*.

Transforms in the Dataflow model can be nested—that is, transforms can contain and invoke other transforms, thus forming **composite transforms**.

> 可以理解为Map Reduce

#### How Transforms Work

Transforms represent your pipeline's processing logic. Each transform accepts one (or multiple) `PCollection`s as input, performs an operation on the elements in the input `PCollection`(s), and produces one (or multiple) new `PCollection`s as output.

To use a transform, you **apply** the transform to the input `PCollection` that you want to process by calling the `apply` method on the input `PCollection`. When you call `PCollection.apply`, you pass the transform you want to use as an argument. The output `PCollection` is the return value from `PCollection.apply`.

**For example**, the following code sample shows how to `apply` a user-defined transform called `ComputeWordLengths` to a `PCollection<String>.ComputeWordLengths` returns a new `PCollection<Integer>` containing the length of each `String` in the input collection:

```java
// The input PCollection of word strings.
PCollection<String> words = ...;

// The ComputeWordLengths transform, which takes a PCollection of Strings as input and
// returns a PCollection of Integers as output.
static class ComputeWordLengths
  extends PTransform<PCollection<String>, PCollection<Integer>> { ... }

// Apply ComputeWordLengths, capturing the results as the PCollection wordLengths.
PCollection<Integer> wordLengths = words.apply(new ComputeWordLengths());
```

>  When you build a pipeline with a Dataflow program, the transforms you include might not be executed precisely in the order you specify them. The Cloud Dataflow managed service, for example, performs [optimized execution](https://cloud.google.com/dataflow/service/dataflow-service-desc#Optimization). In optimized execution, the Dataflow service orders transforms in *dependency order*, inferring the exact sequence from the inputs and outputs defined in your pipeline. Certain transforms may be merged or executed in a different order to provide the most efficient execution.

#### Types of Transforms in the Dataflow SDKs

##### Core Transforms

The Dataflow SDK contains a small group of core transforms that are the foundation of the Cloud Dataflow parallel processing model. Core transforms form the basic building blocks of pipeline processing. **Each core transform provides a generic *processing framework* for applying business logic *that you provide* to the elements of a `PCollection`.**

When you use a core transform, **you provide the processing logic as a function object**. The function you provide gets applied to the elements of the input `PCollection`(s). Instances of the function may be executed in parallel across multiple Google Compute Engine instances, given a large enough data set, and pending optimizations performed by the pipeline runner service. The worker code function produces the output elements, if any, that are added to the output `PCollection`(s).

###### Requirements for User-Provided Function Objects

The **function objects** you provide for a transform might have many copies executing in parallel across multiple Compute Engine instances in your Cloud Platform project. As such, you should consider a few factors when creating such a function:

- Your function object must be serializable.
- Your function object must be thread-compatible, and be aware that the Dataflow SDKs are not thread-safe.
- We recommend making your function object idempotent.

These requirements apply to subclasses of `DoFn` (used with the [ParDo](https://cloud.google.com/dataflow/model/par-do) core transform), `CombineFn` (used with the [Combine](https://cloud.google.com/dataflow/model/combine) core transform), and `WindowFn` (used with the [Window](https://cloud.google.com/dataflow/model/windowing) transform).

**Serializability** **可序列化**

**The function object you provide to a core transform must be fully serializable**. The base classes for user code, such as`DoFn`, `CombineFn`, and `WindowFn`, already implement `Serializable`. *However, your subclass must not add any non-serializable members*.

Some other serializability factors for which you must account:

- **Transient fields** in your function object are **not** carried down to worker instances in your Cloud Platform project, because they are not automatically serialized.
- **Avoid** loading large amounts of data into a field before serialization.
- Individual instances of function objects **cannot share data**.
- Mutating a function object after it gets applied **has no effect**.
- **Take care when you declare your function object inline by using an anonymous inner class instance**. In a non-static context, your inner class instance will implicitly contain a pointer to the enclosing class and its state. That enclosing class will also be serialized, and thus the same considerations that apply to the function object itself also apply to this outer class.

**Thread-Compatibility** **线程兼容**

**Your function object should be thread-compatible**. Each instance of your function object is accessed by a single thread on a worker instance, unless you explicitly create your own threads. Note, however, that **the Dataflow SDKs are not thread-safe.** If you create your own threads in your function object, you must provide your own synchronization. Note that static members are not passed to worker instances and that multiple instances of your function may be accessed from different threads.

**Idempotency** **幂等**

**We recommend making your function object idempotent**— that is, for any given input, your function always provides the same output. Idempotency is *not* required, but making your functions idempotent makes your output deterministic and can make debugging and troubleshooting your transforms easier.

######Types of Core Transforms

You will often use the core transforms directly in your pipeline. In addition, many of the other transforms provided in the Dataflow SDKs are implemented in terms of the core transforms.

The Dataflow SDKs define the following core transforms:

- [ParDo](https://cloud.google.com/dataflow/model/par-do) for generic parallel processing
- [GroupByKey](https://cloud.google.com/dataflow/model/group-by-key) for Key-Grouping Key/Value pairs
- [Combine](https://cloud.google.com/dataflow/model/combine) for combining collections or grouped values
- [Flatten](https://cloud.google.com/dataflow/model/multiple-pcollections#flatten) for merging collections

##### Composite Transforms

The Dataflow SDKs support **composite transforms**, which are transforms built from multiple sub-transforms. *The model of transforms in the Dataflow SDKs is modular, in that you can build a transform that is implemented in terms of other transforms*. You can think of a composite transform as a complex step in your pipeline that contains several nested steps.

Composite transforms are useful when you want to create a repeatable operation that involves multiple steps. Many of the built-in transforms included in the Dataflow SDKs, such as `Count` and `Top`, are this sort of composite transform. They are used in exactly the same manner as any other transform.

See [Creating Composite Transforms](https://cloud.google.com/dataflow/model/composite-transforms) for more information.

##### Pre-Written Transforms in the Dataflow SDKs

The Dataflow SDKs provide a number of pre-written transforms, which are both core and composite transforms where the processing logic is *already written for you*. These are more complex transforms for combining, splitting, manipulating, and performing statistical analysis on data.

> You can find these transforms in the `com.google.cloud.dataflow.sdk.transforms` package and its subpackages.

For a discussion on using the transforms provided in the Dataflow SDKs, see [Transforms Included in the SDKs](https://cloud.google.com/dataflow/model/library-transforms).

##### Root Transforms for Reading and Writing Data

The Dataflow SDKs provide specialized transforms, called **root transforms**, for getting data into and out of your pipeline. These transforms can be used at any time in your pipeline, but most often serve as your pipeline's root and endpoints. They include **read** transforms, **write** transforms, and **create** transforms.

**Read transforms**, which can serve as the root of your pipeline to create an initial `PCollection`, are used to create pipeline data from various sources. These sources can include text files in Google Cloud Storage, data stored in BigQuery or Pub/Sub, and other cloud storage sources. The Dataflow SDKs also provide an extensible API for working with your own custom data sources.

**Write transforms** can serve as pipeline endpoints to write `PCollection`s containing processed output data to external storage. External data storage sinks can include text files in Google Cloud Storage, BigQuery tables, Pub/Sub, or other cloud storage mechanisms.

**Create transforms** are useful for creating a `PCollection` from in-memory data. See [Creating a PCollection](https://cloud.google.com/dataflow/model/pcollection#Creating) for more information.

For more information on read and write transforms, see [Pipeline I/O](https://cloud.google.com/dataflow/model/pipeline-io).

##### Transforms with Multiple Inputs and Outputs

Some transforms accept multiple `PCollection` inputs, or specialized side inputs. A transform can also produce multiple `PCollection` outputs and side outputs. The Dataflow SDKs provide a tagging API to help you track and pass multiple inputs and outputs of different types.

To learn about transforms with multiple inputs and outputs and the details of the tagging system, see [Handling Multiple PCollections](https://cloud.google.com/dataflow/model/multiple-pcollections).

### Parallel Processing with ParDo

`ParDo` is the core parallel processing operation in the Dataflow SDKs. You use `ParDo` for generic parallel processing. The `ParDo` processing style is similar to what happens inside the "Mapper" class of a Map/Shuffle/Reduce-style algorithm: `ParDo` takes each element in an input `PCollection`, performs some processing function on that element, and then emits zero, one, or multiple elements to an output `PCollection`.

You provide the function that `ParDo` performs on each of the elements of the input `PCollection`. The function you provide is invoked independently, and [in parallel](https://cloud.google.com/dataflow/service/dataflow-service-desc#Parallelization), on multiple worker instances in your Dataflow job.

`ParDo` is useful for a variety of data processing operations, including:

- **Filtering a data set.** You can use `ParDo` to consider each element in a `PCollection` and either output that element to a new collection or discard it.
- **Formatting or converting the type of each element in a data set.** You can use `ParDo` to format the elements in your `PCollection`, such as formatting key/value pairs into printable strings.
- **Extracting parts of each element in a data set.** You can use `ParDo` to extract just a part of each element in your `PCollection`. This can be particularly useful for extracting individual fields from [BigQuery](https://cloud.google.com/bigquery/docs) table rows.
- **Performing computations on each element in a data set.** You can use `ParDo` to perform simple or complex computations on every element, or certain elements, of a `PCollection`.

`ParDo` is also a common intermediate step in a pipeline. For example, you can use `ParDo` to assign keys to each element in a `PCollection`, creating key/value pairs. You can group the pairs later using a `GroupByKey` transform.

#### Applying a ParDo Transform

To use `ParDo`, you apply it to the `PCollection` you want to transform, and save the return value as a `PCollection` of the appropriate type.

**The argument that you provide to `ParDo` must be a subclass of a specific type provided by the Dataflow SDK, called `DoFn`**. For more information on `DoFn`, see [Creating and Specifying Processing Logic](#creating-and-specifying-processing-logic) later in this section.

The following code sample shows a basic `ParDo` applied to a `PCollection` of strings, passing a `DoFn`-based function to compute the length of each string, and outputting the string lengths to a `PCollection` of integers.

```java
// The input PCollection of Strings.
PCollection<String> words = ...;

// The DoFn to perform on each element in the input PCollection.
static class ComputeWordLengthFn extends DoFn<String, Integer> { ... }

// Apply a ParDo to the PCollection "words" to compute lengths for each word.
PCollection<Integer> wordLengths = words.apply(
  ParDo
  .of(new ComputeWordLengthFn()));  // The DoFn to perform on each element, which
                                    // we define above.
```

In the example, the code calls `apply` on the input collection (called "words"). `ParDo` is the `PTransform` argument. The `.of`operation is where you specify the `DoFn` to perform on each element, called, in this case, `ComputeWordLengthFn()`.

#### Creating and Specifying Processing Logic

The processing logic you provide for `ParDo` must be of a specific type required by the Dataflow SDK that you're using to create your pipeline.

> You must build a subclass of the SDK class `DoFn`.

The function you provide is invoked independently and across multiple Google Compute Engine instances.

> Any `DoFn` you provide is subject to certain [general requirements for user-provided function objects](#requirements-for-user-provided-function-objects) as well as [specific requirements for any `DoFn`](https://cloud.google.com/dataflow/model/par-do#DoFnReqs). If your `DoFn` doesn't meet these requirements, your pipeline might fail with an error or fail to produce correct results.

In addition, your `DoFn` **should not rely on any persistent state** from invocation to invocation. Any given instance of your processing function in Cloud Platform might not have access to state information in any other instance of that function.

> **Note:** The Dataflow SDK provides a variant of `ParDo` which you can use to pass immutable persistent data to each invocation of your user code as a [side input](https://cloud.google.com/dataflow/model/par-do#side-inputs).

A `DoFn` processes one element at a time from the input `PCollection`. When you create a subclass of `DoFn`, **you specify the type of input element and the type of output element(s) as type parameters**. The following code sample shows how we might define the `ComputeWordLengthFn()` function from the previous example, which accepts an input `String` and produces an output `Integer`:

```java
static class ComputeWordLengthFn extends DoFn<String, Integer> { ... }
```

Your subclass of `DoFn` must override the element-processing method, `processElement`, where you provide the code to actually work with the input element. The following code sample shows the complete `ComputeWordLengthFn()`:

```java
static class ComputeWordLengthFn extends DoFn<String, Integer> {
  @Override
  public void processElement(ProcessContext c) {
    String word = c.element();
    c.output(word.length());
  }
}
```

==**You don't need to manually extract the elements from the input collection**==; ~~the~~ Dataflow Java SDK handles extracting each element and passing it to your `DoFn` subclass. When you override `processElement`, your override method must accept an object of type `ProcessContext`, which allows you to access the element that you want to process. You access the element that's passed to your `DoFn` by using the method `ProcessContext.element()`.

> If the elements in your `PCollection` are key/value pairs, you can access the key by using `ProcessContext.element().getKey()`, and the value by using `ProcessContext.element().getValue()`.

> The Dataflow SDK invokes an individual instance of your `DoFn` one or more times to process an arbitrary bundle of elements. Each element gets passed to your `DoFn` in a separate invocation. You can cache information across multiple calls to the element-processing method, but make sure your implementation of it **does not depend on the number of invocations**. Dataflow does not guarantee the number of times any `DoFn` instance gets invoked.

The Dataflow SDK for Java automatically handles gathering the output elements into a result `PCollection`. You use the `ProcessContext` object to output the resulting element from `processElement` to the output collection. To output an element for the result collection, use the method `ProcessContext.output()`.

> Your implementation of `processElement` must **obey the following immutability requirements**:
>
> - You should not in any way modify an element returned by `ProcessContext.element()` or `ProcessContext.sideInput()`.
> - Once you output a value using `ProcessContext.output()` or `ProcessContext.sideOutput()`, you should not modify that value in any way.
>
> The Dataflow SDK for Java and service also will never modify any of the above values in any way. Together, these restrictions ensure that values may be cached, serialized, or used in other unspecified ways to efficiently execute your pipeline.

##### Lightweight DoFns

The Dataflow SDKs provide language-specific ways to simplify how you provide your `DoFn` implementation.

Often, you can create a simple `DoFn` argument to `ParDo` as an anonymous inner class instance. If your `DoFn` is only a few lines, it might be cleaner to specify it inline. The following code sample shows how to apply a `ParDo` with the `ComputeWordLengthFn` function as an anonymous `DoFn`:

```java
// The input PCollection.
PCollection<String> words = ...;

// Apply a ParDo with an anonymous DoFn to the PCollection words.
// Save the result as the PCollection wordLengths.
PCollection<Integer> wordLengths = words.apply(
  ParDo
  .named("ComputeWordLengths")            // the transform name
  .of(new DoFn<String, Integer>() {       // a DoFn as an anonymous inner class instance
    @Override
    public void processElement(ProcessContext c) {
      c.output(c.element().length());
    }
  }));
```

For transforms like the one above that apply a function to each element in the input to produce exactly one output per element, you can use the **higher-level `MapElements` transform**. This is especially <u>concise</u> in Java 8, as `MapElements` accepts a lambda function.

```java
// The input PCollection.
PCollection<String> words = ...;

// Apply a MapElements with an anonymous lambda function to the PCollection words.
// Save the result as the PCollection wordLengths.
PCollection<Integer> wordLengths = words.apply(
  MapElements.via((String word) -> word.length())
  .withOutputType(new TypeDescriptor<Integer>() {});
```

Similarly, you can use Java 8 lambda functions with the `Filter`, `FlatMapElements`, and `Partition` transforms. See [Pre-Written Transforms in the Dataflow SDKs](https://cloud.google.com/dataflow/model/library-transforms) for details on these transforms.

##### Transform Names

Transform names appear in the execution graph when you view your pipeline in the [Dataflow Monitoring Interface](https://cloud.google.com/dataflow/pipelines/dataflow-monitoring-intf). It is particularly important to specify an explicit name for your transform in order to recognize them in the graph.

The `.named` operation specifies the **transform name** for this step in your pipeline. Transform names appear in the execution graph when you view your pipeline in the [Dataflow Monitoring Interface](https://cloud.google.com/dataflow/pipelines/dataflow-monitoring-intf). It is particularly important to specify an explicit name when you're using an anonymous `DoFn` instance with `ParDo`, so that you can see an easily-readable name for your step in the monitoring interface.

#### Side Inputs

In addition to the main input `PCollection`, you can provide additional inputs to a `ParDo` transform in the form of **side inputs**. A side input is an additional input that your `DoFn` can access each time it processes an element in the input`PCollection`. When you specify a side input, you create a view of some other data that can be read from within the `ParDo` transform's `DoFn` while procesing each element.

Side inputs are useful if your `ParDo` needs to inject additional data when processing each element in the input `PCollection`, but the additional data needs to be determined at runtime (and not hard-coded). Such values might be determined by the input data, or depend on a different branch of your pipeline. **For example, you can obtain a value from a remote service while your pipeline is running and use it as a side input.** Or, **you can use a value computed by a separate branch of your pipeline and add it as a side input to another branch's** `ParDo`.

> There is a fundamental difference between side inputs and main inputs. Main inputs are **sharded** across multiple worker instances in your Dataflow job, so each element is read only once for the entire job. With side inputs, each worker may **read the same elements multiple times**.
>
> By default, Dataflow uses a small cache size for keeping side inputs in memory. Having elements of a side input cached in memory makes fetching the elements quite faster. If you have large side inputs, you should specify a cache size that is appropriate based on the working set size required by your pipeline. To do this, specify the pipeline option:
>
> ```shell
> --workerCacheMb=<XXX>
> ```

#####Representing a Side Input

Side inputs are always of type [`PCollectionView`](https://cloud.google.com/dataflow/java-sdk/JavaDoc/com/google/cloud/dataflow/sdk/values/PCollectionView). `PCollectionView` is a way of representing a `PCollection` as a single entity, which you can then pass as a side input to a `ParDo`. You can make a `PCollectionView` that expresses a `PCollection` as one of the following types:

| View Type          | Usage                                    |
| ------------------ | ---------------------------------------- |
| `View.asSingleton` | Represents a **`PCollection`** as an individual value; generally you'd use this after combining a **`PCollection`** using [`Combine.globally`](https://cloud.google.com/dataflow/model/combine). **Use this when your side input is a single computed value**. You should typically create a singleton view using `Combine.globally(...).asSingletonView()`. |
| `View.asList`      | Represents a **`PCollection`** as a `List`. Use this view when your side input is a collection of individual values. |
| `View.asMap`       | Represents a `PCollection` as a `Map`. Use this view when your side input consists of key/value pairs (`PCollection<K,V>`), and has a single value for each key. |
| `View.asMultimap`  | Represents a `PCollection` as a `MultiMap`. Use this view when your side input consists of key/value pairs (`PCollection<K,V>`), and has multiple values for each key. |

> In streaming pipelines, `View.asList`, `View.asMap`, and `View.asMultimap` all represent an entire `PCollection` **in memory on a single machine**. This is so that when the view is used as a side input, it is cached in memory and not re-read during each iteration of the `ParDo`.To avoid out-of-memory errors, you should only use these views with `PCollection`s that can fit in memory on a single Compute Engine instance.

> **Note:** Like other pipeline data, `PCollectionView` is immutable once created.

#####Passing Side Inputs to ParDo

You pass side inputs to your `ParDo` transform by invoking `.withSideInputs`. Inside your `DoFn`, you access the side input by using the method `DoFn.ProcessContext.sideInput`.

The following example code creates a singleton side input from a `PCollection` and passes it to a subsequent `ParDo`.

In the example, we have a `PCollection` called `words` that represents a collection of individual words, and a `PCollection` that represents word lengths; we can use the latter to compute a maximum word length <u>cutoff</u> as a singleton value, and then pass that computed value as a side input to a `ParDo` that filters `words` based on the cutoff.

```java
// The input PCollection to ParDo.
PCollection<String> words = ...;

// A PCollection of word lengths that we'll combine into a single value.
PCollection<Integer> wordLengths = ...; // Singleton PCollection

// Create a singleton PCollectionView from wordLengths using Combine.globally 
// and View.asSingleton.
final PCollectionView<Integer> maxWordLengthCutOffView =
  wordLengths.apply(Combine.globally(new Max.MaxIntFn()).asSingletonView());

// Apply a ParDo that takes maxWordLengthCutOffView as a side input.
PCollection<String> wordsBelowCutOff =
  words.apply(ParDo.withSideInputs(maxWordLengthCutOffView)
              .of(new DoFn<String, String>() {
                public void processElement(ProcessContext c) {
                  String word = c.element();
                  // In our DoFn, access the side input.
                  int lengthCutOff = c.sideInput(maxWordLengthCutOffView);
                  if (word.length() <= lengthCutOff) {
                    c.output(word);
                  }
                }}));
```

#####Side Inputs and Windowing

- [ ] 没有理解，要看代码

When you create a `PCollectionView` of a **windowed** `PCollection`, which may be infinite and thus cannot be compressed into a single value (or single collection class), the `PCollectionView` represents a single entity per [window](https://cloud.google.com/dataflow/model/windowing). That is, the `PCollectionView` represents one singleton per window, one list per window, etc.

Dataflow uses the window(s) for the main input element to look up the appropriate window for the side input element. Dataflow projects the main input element's window into the side input's window set, and then uses the side input from the resulting window. If the main input and side inputs have identical windows, the projection provides the exact corresponding window; however, if the inputs have different windows, Dataflow uses the projection to choose the most appropriate side input window.

> **Note:** Dataflow projects the main element's window into the side input's window set using the side input's `WindowFn.getSideInputWindow()` method. See the [API reference for `WindowFn`](https://cloud.google.com/dataflow/java-sdk/JavaDoc/com/google/cloud/dataflow/sdk/transforms/windowing/WindowFn) for more information.

For example, if the main input is windowed using fixed-time windows of one minute, and the side input is windowed using fixed-time windows of one hour, Dataflow projects the main input window against the side input window set and selects the side input value from the appropriate hour-long side input window.

> **Note:** If the main input element exists in more than one window, then `processElement` gets called multiple times, once for each window. Each call to `processElement` projects the "current" window for the main input element, and thus might provide a different view of the side input each time.

If the side input has multiple trigger firings, Dataflow uses the value from the latest trigger firing. This is particularly useful if you use a side input with a single global window and specify a trigger.

#### Side Outputs

While `ParDo` always produces a main output `PCollection` (as the return value from `apply`), you can also have your `ParDo` produce any number of additional output `PCollection`s. If you choose to have multiple outputs, your `ParDo` will return all of the output `PCollection`s (**including the main output**) bundled together. For example, in Java, the output `PCollection`s are bundled in a type-safe [PCollectionTuple](https://cloud.google.com/dataflow/model/multiple-pcollections#Heterogenous).

##### Tags for Side Outputs

To emit elements to a side output `PCollection`, you'll need to create a `TupleTag` object to identify each collection your `ParDo`produces. For example, if your `ParDo` produces three output `PCollection`s (the main output and two side outputs), you'll need to create three associated `TupleTag`s.

The following example code shows how to create `TupleTag`s for a `ParDo` with a main output and two side outputs:

```java
// Input PCollection to our ParDo.
PCollection<String> words = ...;

// The ParDo will filter words whose length is below a cutoff and add them to
// the main ouput PCollection<String>.
// If a word is above the cutoff, the ParDo will add the word length to a side output
// PCollection<Integer>.
// If a word starts with the string "MARKER", the ParDo will add that word to a different
// side output PCollection<String>.
final int wordLengthCutOff = 10;

// Create the TupleTags for the main and side outputs.
// Main output.
final TupleTag<String> wordsBelowCutOffTag =
  new TupleTag<String>(){};
// Word lengths side output.
final TupleTag<Integer> wordLengthsAboveCutOffTag =
  new TupleTag<Integer>(){};
// "MARKER" words side output.
final TupleTag<String> markedWordsTag =
  new TupleTag<String>(){};
```

######Passing Output Tags to ParDo

Once you have specified the `TupleTag`s for each of your `ParDo` outputs, you'll need to pass those tags to your `ParDo` by invoking `.withOutputTags`. You pass the tag for the main output first, and then the tags for any side outputs in a `TupleTagList`.

Building on our previous example, here's how we pass the three `TupleTag`s (one for the main output and two for the side outputs) to our `ParDo`:

```java
PCollectionTuple results =
  words.apply(
    ParDo
      // Specify the tag for the main output, wordsBelowCutoffTag.
      .withOutputTags(wordsBelowCutOffTag,
                      // Specify the tags for the two side outputs as a TupleTagList.
                      TupleTagList.of(wordLengthsAboveCutOffTag).and(markedWordsTag))
      .of(new DoFn<String, String>() {
        // DoFn continues here.
        ...
      })))
```

Note that *all* of the outputs (including the main output `PCollection`) are bundled into the returned `PCollectionTuple` called `results.`.

##### Emitting to Side Outputs In Your DoFn

Inside your `ParDo`'s `DoFn`, you can emit an element to a side output by using the method `ProcessContext.sideOutput`. You'll need to pass the appropriate `TupleTag` for the target side output collection when you call `ProcessContext.sideOutput`.

From our previous example, here's the `DoFn` emitting to the main and side outputs:

```java
.of(new DoFn<String, String>() {
  public void processElement(ProcessContext c) {
    String word = c.element();
    if (word.length() <= wordLengthCutOff) {
      // Emit this short word to the main output.
      c.output(word);
    } else {
      // Emit this long word's length to a side output.
      c.sideOutput(wordLengthsAboveCutOffTag, word.length());
    }
    if (word.startsWith("MARKER")) {
      // Emit this word to a different side output.
      c.sideOutput(markedWordsTag, word);
    }
  }}));
```

After your `ParDo`, you'll need to extract the resulting main and side output `PCollection`s from the returned `PCollectionTuple`. See the section on [PCollectionTuple](https://cloud.google.com/dataflow/model/multiple-pcollections#Heterogenous)s for some examples that show how to extract individual `PCollection`s from a tuple.

------
#Beam用到的 JAVA 语法和模式

## java静态类
感觉就是模拟了 `C++` `namespace`的功能，可参考

1. [Java内部类和嵌套静态类](https://github.com/giantray/stackoverflow-java-top-qa/blob/master/contents/java-inner-class-and-static-nested-class.md)
2. [Is it true that every inner class requires an enclosing instance?](http://stackoverflow.com/questions/20468856/is-it-true-that-every-inner-class-requires-an-enclosing-instance)
3. [建议38： 使用静态内部类提高封装性](http://book.51cto.com/art/201202/317517.htm)

## 关于WithXXX

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