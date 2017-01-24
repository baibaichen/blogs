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
f (starts with 'A') { outputToPCollectionA } else if (starts with 'B') { outputToPCollectionB }
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