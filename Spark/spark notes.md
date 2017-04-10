1. [[SPARK-6942] withScope 用来做DAG可视化](http://m.blog.csdn.net/article/details?id=51289351)
   - [Umbrella: UI Visualizations for Core and Dataframes](https://issues.apache.org/jira/browse/SPARK-6942)

2. [[SPARK-13985] WAL for determistic batches with IDs](https://issues.apache.org/jira/browse/SPARK-13985)
   * [[SPARK-13791] Add MetadataLog and HDFSMetadataLog](https://issues.apache.org/jira/browse/SPARK-13791)

3. [[SPARK-8360] Structured Streaming (aka Streaming DataFrames)](https://issues.apache.org/jira/browse/SPARK-8360) 
   * [ ] [[Blog] Faster Stateful Stream Processing in Apache Spark’s Streaming](https://databricks.com/blog/2016/02/01/faster-stateful-stream-processing-in-apache-spark-streaming.html)
   * [ ] [[Blog] Building Lambda Architecture with Spark Streaming](http://blog.cloudera.com/blog/2014/08/building-lambda-architecture-with-spark-streaming/)
   * [[SPARK-14942] Reduce delay between batch construction and execution](https://issues.apache.org/jira/browse/SPARK-14942)
   * [ ][[SPARK-13985] WAL for determistic batches with IDs](https://issues.apache.org/jira/browse/SPARK-13985)
     * [ ] [[SPARK-13791] Add MetadataLog and HDFSMetadataLog](https://issues.apache.org/jira/browse/SPARK-13791)
   * [[SPARK-10820] Support for the continuous execution of structured queries](https://github.com/apache/spark/pull/11006)，在这个Commit中，引入了 `sql/execution` 这个包
   * [[SPARK-14255] Streaming Aggregation](https://issues.apache.org/jira/browse/SPARK-14255)，在这个Commit中，引入了*IncrementalExecution.scala*

4. [[SPARK-13485] (Dataset-oriented) API evolution in Spark 2.0](https://issues.apache.org/jira/browse/SPARK-13485)
   * [[SPARK-13244] Unify DataFrame and Dataset API](https://issues.apache.org/jira/browse/SPARK-13244) 这个jira提交之后，`DataFrame` 改成 `Dataset`，新的 `DataFrame` 是 `Dataset[Row]` 的**类型别名**。**注意**：文件名仍然是 **DataFrame.scala**

5. [[SPARK-13822] Follow-ups of DataFrame/Dataset API unification](https://issues.apache.org/jira/browse/SPARK-13822)
   * [[SPARK-13880] Rename DataFrame.scala as Dataset.scala](https://issues.apache.org/jira/browse/SPARK-13880) 和 [[SPARK-13881] Remove LegacyFunctions](https://issues.apache.org/jira/browse/SPARK-13881) 这两个jira提交之后DataFrame.scala 改名成 Dataset.scala

6. [[SPARK-12449] Pushing down arbitrary logical plans to data sources](https://issues.apache.org/jira/browse/SPARK-12449)，江烈report的稍微复杂的SQL，SPARK的JDBC 驱动不支持Push down，见下面的SQL：
    ```SQL
    select 
      app_code,app_host,service_name,sum(called_counts) as counts 
    FROM 
      monitor_method_analyse 
    where 
      START_TIME >= cast('2017-01-03 14:00:00' as timestamp) and 
      START_TIME <  cast('2017-01-03 15:00:00' as timestamp) 
    group by 
      app_code,app_host,service_name

    ## 1. group by 不能push down
    ## 2. START_TIME >= cast('2017-01-03 14:00:00' as timestamp) 如果不加cast 也不能push down，这个不知道具体原因
    ```

7. Ongoing work since 2017.2 summit

    1. Standard binary format to pass data to external code
       {0}. Either existing format or Apache Arrow (SPARK-19489, SPARK-13545)
       {0}. Binary format for data sources (SPARK-15689)
    2. Integrations with deep learning libraries
       {0}. Intel BigDL, Databricks TensorFrames (see talks today)
    3. **Accelerators** as a first-class resource
    4. Next generation of SQL and DataFrames
       {0}. **[Cost-based optimizer](https://issues.apache.org/jira/browse/SPARK-16026)** (SPARK-16026 + many others)
       {0}. Improved data sources ([CSV](https://issues.apache.org/jira/browse/SPARK-16099), [JSON](https://issues.apache.org/jira/browse/SPARK-18352))
    5. Continue improving Python/R (SPARK-18924, 17919, 13534, …)
    6. Make Spark easier to run on a single node
       {0}. Publish to PyPI (SPARK-18267) and CRAN (SPARK-15799)
       {0}. Optimize for large servers
       {0}. As convenient as Python multiprocessing
    7. Integrations with more systems
       {0}. JDBC source and sink (SPARK-19478, SPARK-19031)
       {0}. **[Unified access to Kafka](https://issues.apache.org/jira/browse/SPARK-18682)**
    8. New operators
       {0}. [mapGroupsWithState - arbitrary stateful operations with Structured Streaming (similar to DStream.mapWithState)](https://issues.apache.org/jira/browse/SPARK-19067)
       {0}. [EventTime based sessionization，即Session windows](https://issues.apache.org/jira/browse/SPARK-10816)
    9. Performance and latency

# TODO

> ## 小 Issue
> 1. 在 linux 平台下测试一下 ErrorPositionSuite，用于了解下 TreeNode.scala 中 `CurrentOrigin` 的用法，这个对象在这个[commit](https://github.com/apache/spark/commit/104b2c45805ce0a9c86e2823f402de6e9f0aee81)中引入，貌似现在不用。
>
> ## 框架方面的内容
> 1.  文件格式
>
> 2.  **语法分析（调研 ANTLR）**：Spark 曾经有两个分析器，一个是基于 Hive 的，另一个是基于 Scala 的 **parser combinator**，两个都有些问题。1）Hive 的分析器不受 DataBricks 控制（我猜的），引入新语法和修复 BUG 都不方便，2）Scala 的 **parser combinator** 语法出错时的提示信息很烂（怎么个烂法，我也不知道），而且语法有冲突的时候也不警告。所以 DataBricks 自己搞了一个，当然也不是从零开始，貌似是从 presto 搞过来的，主要的jira如下[[SPARK-12362] Create a full-fledged built-in SQL parser](https://issues.apache.org/jira/browse/SPARK-12362)
>
> 3.  **Project Tungsten**
>
>     * [ ] [[SPARK-7075] Project Tungsten (Spark 1.5 Phase 1)](https://issues.apache.org/jira/browse/SPARK-7075)
>     * [ ] [[SPARK-9697] Project Tungsten (Phase 2)](https://issues.apache.org/jira/browse/SPARK-9697)
>     * [ ] [[Blog] Project Tungsten: Bringing Apache Spark Closer to Bare Metal](https://databricks.com/blog/2015/04/28/project-tungsten-bringing-spark-closer-to-bare-metal.html)
>     * [ ] [[中文] Project Tungsten：让Spark将硬件性能压榨到极限](http://www.csdn.net/article/2015-04-30/2824591-project-tungsten-bringing-spark-closer-to-bare-metal)
>     * [ ] [[Blog] Apache Spark as a Compiler: Joining a Billion Rows per Second on a Laptop (Deep dive into the new Tungsten execution engine)](https://databricks.com/blog/2016/05/23/apache-spark-as-a-compiler-joining-a-billion-rows-per-second-on-a-laptop.html)
>     * [ ] [探索Spark Tungsten的秘密](https://github.com/hustnn/TungstenSecret)
> 4.  **代码生成**
>     * [ ] [[SPARK-12795] Whole stage codegen](https://issues.apache.org/jira/browse/SPARK-12795)
>     * [ ] `Janino`
> 5.  存储管理
>     * [ ] [[SPARK-10000] Consolidate storage and execution memory management](https://issues.apache.org/jira/browse/SPARK-10000)
>     * [ ] [[SPARK-10983] Implement unified memory manager](https://issues.apache.org/jira/browse/SPARK-10983)
>     * [ ] [[2016-6 summit] Deep Dive: Apache Spark Memory Management](https://www.youtube.com/watch?v=dPHrykZL8Cg)
> 6.  RPC 框架，用Netty 重新写了一个替换 Akka
>     * [[SPARK-5293] Akka:Enable Spark user applications to use different versions of Akka](https://issues.apache.org/jira/browse/SPARK-5293)
> 7.  RDD
> 8.  Dataset
> 9.  Streaming

---
![ a quick overview of the flow of a spark job](https://trongkhoanguyenblog.files.wordpress.com/2014/11/schedule-process.png)



# Debug
1. Building **[** mvn -Pyarn -Phadoop-2.6 -Phive -Phive-thriftserver -DskipTests clean package **]**
2. SPARK_JAVA_OPTS="-Xdebug -Xrunjdwp:transport=dt_socket,address=5000,server=y,suspend=y"


# 重要的类
## Dataset

A `Dataset` is a strongly typed collection of domain-specific objects that can be transformed in parallel using functional or relational operations. Each Dataset also has an untyped view called a `DataFrame`, which is a Dataset of `Row`.

> `Dataset` 是领域对象的强类型集合

Operations available on Datasets are divided into **transformations** and **actions**. **Transformations are the ones that produce new Datasets, and actions are the ones that trigger computation and return results**. Example transformations include map, filter, select, and aggregate (`groupBy`). Example actions count, show, or writing data out to file systems.

Datasets are "lazy", i.e. computations are only triggered when an action is invoked. Internally, a `Dataset` represents a **logical plan** that describes the computation required to produce the data. When an action is invoked, Spark's query optimizer optimizes the logical plan and generates a **physical plan** for efficient execution in a parallel and distributed manner. To explore the logical plan as well as optimized physical plan, use the `explain` function.

> `Dataset`是“惰性”的，即只有在调用`Action`时才触发计算。在内部，`Dataset`表示一个逻辑计划，描述了生成数据所需的计算。当调用`Action`时，Spark的查询优化器优化逻辑计划，并以并行和分布式的方式生成高效的物理执行计划。使用 `explain` 函数可以查看逻辑计划和优化的物理计划。

To efficiently support domain-specific objects, an [`Encoder`]() is required. **The encoder maps the domain specific type `T` to Spark's internal type system**. For example, given a class `Person` with two fields, **name** (`string`) and **age** (`int`), an encoder is used to tell Spark to generate code at runtime to serialize the `Person` object into a binary structure. This binary structure often has much lower memory footprint as well as are optimized for efficiency in data processing (e.g. in a columnar format). To understand the internal binary representation for data, use the `schema` function.

>为了有效地支持领域对象，`Encoder` 是必需的。`Encoder` 在领域对象的类型`T` 和`Spark`的内部类型系统之间建立映射。
>
>要想理解数据内部的二进制结构，使用 `schema` 函数。

There are typically two ways to create a Dataset. The most common way is by pointing Spark to some files on storage systems, using the `read` function available on a `SparkSession`.

    val people = spark.read.parquet("...").as[Person]  // Scala
    Dataset<Person> people = spark.read().parquet("...").as(Encoders.bean(Person.class)); // Java

Datasets can also be created through transformations available on existing Datasets. For example, the following creates a new Dataset by applying a filter on the existing one:

    val names = people.map(_.name)  // in Scala; names is a Dataset[String]
    Dataset<String> names = people.map((Person p) -> p.name, Encoders.STRING)); // in Java 8

Dataset operations can also be untyped, through various **domain-specific-language** (DSL) functions defined in: `Dataset` (this class), [`Column`](), and [`functions`](). These operations are very similar to the operations available in the data frame abstraction in R or Python. To select a column from the Dataset, use `apply` method in Scala and `col` in Java.

    val ageCol = people("age")  // in Scala
    Column ageCol = people.col("age"); // in Java

Note that the [`Column`]() type can also be manipulated through its various functions.

    // The following creates a new column that increases everybody's age by 10.
    people("age") + 10  // in Scala
    people.col("age").plus(10);  // in Java

A more concrete example in Scala:

```scala
    // To create Dataset[Row] using SparkSession
    val people = spark.read.parquet("...")
    val department = spark.read.parquet("...")
    people.filter("age > 30")
      .join(department, people("deptId") **= department("id"))
      .groupBy(department("name"), "gender")
      .agg(avg(people("salary")), max(people("age")))
```
and in Java:

```java
    // To create Dataset<Row> using SparkSession
    Dataset<Row> people = spark.read().parquet("...");
    Dataset<Row> department = spark.read().parquet("...");
    people.filter("age".gt(30))
      .join(department, people.col("deptId").equalTo(department("id")))
      .groupBy(department.col("name"), "gender")
      .agg(avg(people.col("salary")), max(people.col("age")));
```
---
                            type          val       lazy
    Dataset[T]
      sparkSession    -> SparkSession     [Y]
      queryExecution  -> QueryExecution   [Y]
      encoder         -> Encoder[T]       [?]
      logicalPlan     -> LogicalPlan      [Y]
      exprEnc                             [Y]
      boundEnc                            [Y]
      sqlContext      -> SQLContext       [Y]        [Y]
      rdd             -> RDD[T]           [Y]        [Y]
    
    QueryExecution
      sparkSession    -> SparkSession     [Y]
      logical         -> LogicalPlan      [Y]
      analyzed        -> LogicalPlan      [Y]        [Y]
      withCachedData  -> LogicalPlan      [Y]        [Y]
      optimizedPlan   -> LogicalPlan      [Y]        [Y]
      sparkPlan       -> SparkPlan        [Y]        [Y]
      executedPlan    -> SparkPlan        [Y]        [Y]
      toRdd           -> RDD[InternalRow] [Y]        [Y]

1. **`Dataset[T].rdd`**：用 `RDD[T]` 表示 `Dataset[T]` 的内容
2. **`QueryExecution.toRdd`**: `RDD` 的内部版本，避免拷贝，没有 **schema**

> TODO
> -[ ] `encoder` 声明的时候即没有指定 `val` 也没有指定 `var`，到底是**可变量**还是**常量**？
>      参见*快学 Scala* 的5.7节**主构造器**，取决于是否在类方法中使用
> -[ ] `sqlContext` must be `val` because *a stable identifier is expected when you import implicits*
### 创建
1. **隐式转换**：调用 `rddToDatasetHolder` 把 `RDD` 转换成`DatasetHolder`
2. **隐式参数**：`rddToDatasetHolder`需要一个`Encoder[T]`的隐式参数，注意这里的 `T` 是 `String` 类型
#### Seq => Dataset
    Seq(("a", 10), ("a", 20), ("b", 1), ("b", 2), ("c", 1)).toDS()

1. **隐式转换**：调用 `localSeqToDatasetHolder` 把本地 `Seq` 转换成`DatasetHolder`
2. **隐式参数**：即上下文界定，`localSeqToDatasetHolder` 需要一个`Encoder[T]`的隐式参数，注意这里的 `T` 是 `Pair` 类型（即样例类 `Tuple2`），因此会调用到 `newProductEncoder`！

#### RDD => Dataset
    sparkContext.makeRDD(Seq("a", "b", "c"), 3).toDS()
### Row

Represents one row of output from a relational operator. Allows both generic access by ordinal, which will incur boxing overhead for primitives, as well as **<u>native primitive access</u>**.

It is invalid to use the native primitive interface to retrieve a value that is null, instead a user must check `isNullAt` before attempting to retrieve a value that might be null.

To create a new Row, use `RowFactory.create()` in Java or `Row.apply()` in Scala.

>表示关系运算的一行输出。允许~~通用访问的顺序，这将招致拳击开销原语~~，以及原生的原始访问。

A [Row](http://spark.apache.org/docs/latest/api/scala/org/apache/spark/sql/Row.html) object can be constructed by providing field values. Example:

```scala
import org.apache.spark.sql._

// Create a Row from values.
Row(value1, value2, value3, ...)
// Create a Row from a Seq of values.
Row.fromSeq(Seq(value1, value2, ...))
```

A value of a row can be accessed through both generic access by ordinal, which will incur boxing overhead for primitives, as well as native primitive access. An example of generic access by ordinal:

```scala
import org.apache.spark.sql._

val row = Row(1, true, "a string", null)
// row: Row = [1,true,a string,null]
val firstValue = row(0)
// firstValue: Any = 1
val fourthValue = row(3)
// fourthValue: Any = null
```

For **native primitive access**, it is invalid to use the native primitive interface to retrieve a value that is null, instead a user must check `isNullAt` before attempting to retrieve a value that might be null. An example of native primitive access:

```scala
// using the row from the previous example.
val firstValue = row.getInt(0)
// firstValue: Int = 1
val isNull = row.isNullAt(3)
// isNull: Boolean = true
```

In Scala, fields in a [Row](http://spark.apache.org/docs/latest/api/scala/org/apache/spark/sql/Row.html) object can be extracted in a pattern match. Example:

```scala
import org.apache.spark.sql._

val pairs = sql("SELECT key, value FROM src").rdd.map {
  case Row(key: Int, value: String) =>
    key -> value
}
```

## RDD

| `RDD` 函数          |                                          |
| ----------------- | ---------------------------------------- |
| `getDependencies` | Implemented by subclasses to return how this RDD depends on parent RDDs. |
| `getPartitions`   | Implemented by subclasses to return the set of partitions in this RDD. |
| `iterator`        | Internal method to this RDD; will read from cache if applicable, or otherwise `compute()` it |

`compute()` 本质上就是一个 `Partiontion => Iterator`的函数

    Partition
      ParallelCollectionPartition
    
    Dependency
      ShuffleDependency
      NarrowDependency
        OneToOneDependency
        PruneDependency
        RangeDependency

## DAGScheduler

- [Understand the scheduler component in spark-core](https://trongkhoanguyenblog.wordpress.com/2015/03/28/understand-the-scheduler-component-in-spark-core/)


**The high-level scheduling layer that implements stage-oriented scheduling**. It computes a DAG of stages for each job, keeps track of which RDDs and stage outputs are materialized, and finds a  minimal schedule to run the job. It then submits stages as `TaskSet`s to an underlying TaskScheduler implementation that runs them on the cluster. A **`TaskSet`** contains fully independent tasks that can run right away based on the data that's already on the cluster (e.g. map output files from previous stages), though it may fail if this data becomes unavailable.

**Spark stages are created by breaking the RDD graph at shuffle boundaries**. RDD operations with **narrow** dependencies, like `map()` and `filter()`, are pipelined together into one set of tasks in each stage, but operations with shuffle dependencies require multiple stages (one to write a set of map output files, and another to read those files after a barrier). **In the end, every stage will have only shuffle dependencies on other stages, and may compute multiple operations inside it**. The actual pipelining of these operations happens in the `RDD.compute()` functions of various RDDs (**MapPartitionsRDD**, etc).

> **高级调度层**，**实现了面向 stage 的调度**。 为每个作业计算 stage 的 DAG、跟踪哪些 RDD 和 stage 的输出需要**物化**，并为执行的作业找到一个最小的调度。然后为了在集群上运行这些stages，就把它们做为 `TaskSet`s 提交给`TaskScheduler`的底层实现。**`TaskSet`**包含完全独立的任务，如果执行任务所需的数据（例如，上一阶段 `map` 的输出文件）已经在集群上，则可以马上运行，不过，后续可能因为这些数据不可用而失败。
>
> Spark 以 **shuffle** 为边界分割 **`RDD graph`** 而创建 stages。像 `map()` 和 `filter()` 这类窄依赖的 `RDD` 操作，在每个 stage 内串联成为一组任务。但是有 **shuffle** 依赖的操作需要多个 stages（前一个stage在一端产生一组 `map` 的输出文件，后一个stage在另一端读取这些文件）。最终，每个 stage 之间只有**shuffle** 依赖，但是每个 stage 内部可能有多个操作。在各个 `RDD` （例如**`MapPartitionsRDD`**）的 `compute()` 函数内部真正以流水线的方式执行这些操作。

**In addition to coming up with a DAG of stages, the `DAGScheduler` also determines the preferred locations to run each task on, based on the current cache status, and passes these to the low-level [`TaskScheduler`](#TaskScheduler). Furthermore, it handles failures due to shuffle output files being lost, in which case old stages may need to be resubmitted. Failures **within** a stage that are not caused by shuffle file loss are handled by the [`TaskScheduler`](#TaskScheduler), which will retry each task a small number of times before cancelling the whole stage.

When looking through this code, there are several key concepts:

- **Jobs** (represented by [`ActiveJob`](#ActiveJob)) are the top-level work items submitted to the **scheduler**.For example, when the user calls an **action**, like `count()`, a job will be submitted through `submitJob`. Each Job may require the execution of multiple stages to build intermediate data.
- **Stages** ([`Stage`](#Stage)) are sets of tasks that compute intermediate results in jobs, where *each task computes the same function on partitions of the same RDD*. Stages are separated at shuffle boundaries, which introduce a **barrier** (where we must wait for the previous stage to finish to fetch outputs). There are two types of stages: [`ResultStage`](#ResultStage), for the final stage that executes an action, and [`ShuffleMapStage`](#ShuffleMapStage), which writes map output files for a shuffle. **Stages are often shared across multiple jobs, if these jobs reuse the same RDDs**.
- **Tasks** are individual units of work, each sent to one machine.
- **Cache tracking**: the `DAGScheduler` figures out which RDDs are cached to avoid recomputing them and likewise remembers which shuffle map stages have already produced output files to avoid redoing the map side of a shuffle.
- **Preferred locations**: the `DAGScheduler` also computes where to run each task in a stage based on the preferred locations of its underlying RDDs, or the location of cached or shuffle data. See `getPreferredLocsInternal`
- **Cleanup**: all data structures are cleared when the running jobs that depend on them finish,to prevent memory leaks in a long-running application.

To recover from failures, the same stage might need to run multiple times, which are called "attempts". If the [`TaskScheduler`]() reports that a task failed because a map output file from a previous stage was lost, the `DAGScheduler` resubmits that lost stage. This is detected through a `CompletionEvent` with `FetchFailed`, or an ExecutorLost event. The DAGScheduler will wait a small amount of time to see whether other nodes or tasks fail, then resubmit **TaskSets** for any lost stage(s) that compute the missing tasks. As part of this process, we might also have to create `Stage` objects for old (finished) stages where we previously cleaned up the Stage object. Since tasks from the old attempt of a stage could still be running, care must be taken to map any events received in the correct Stage object.

Here's a checklist to use when making or reviewing changes to this class:

- All data structures should be cleared when the jobs involving them end to avoid indefinite accumulation of state in long-running programs
- When adding a new data structure, update `DAGSchedulerSuite.assertDataStructuresEmpty` to include the new structure. This will help to catch memory leaks.


    EventLoop //基本的 Produce&Consume队列实现
      DAGSchedulerEventProcessLoop
    
    DAGSchedulerEvent
      AllJobsCancelled
      BeginEvent
      CompletionEvent
      ExecutorAdded
      ExecutorLost
      GettingResultEvent
      JobCancelled
      JobGroupCancelled
      JobSubmitted
      MapStageSubmitted
      ResubmitFailedStages
      StageCancelled
      TaskSetFailed

```
graph LR
runJob-->submitJob
DAGSchedulerEventProcessLoop-->EventLoop
```
---

## ActiveJob

A running job in the `DAGScheduler`. Jobs can be of two types: 
- **a result job**, which computes a `ResultStage` to execute an action, or 
- **a map-stage job**, which computes the map outputs for a `ShuffleMapStage` before any downstream stages are submitted. 

The latter is used for adaptive query planning, to look at map output statistics before submitting later stages. We distinguish between these two types of jobs using the finalStage field of this class. **Jobs are only tracked for "leaf" stages** that clients directly submitted, through `DAGScheduler.submitJob` or `submitMapStage` methods. However, either type of job may cause the execution of other earlier stages (for RDDs in the DAG it depends on), and multiple jobs may share some of these previous stages. These dependencies are managed inside `DAGScheduler`.

- @param ++jobId++ A unique ID for this job.
- @param ++**finalStage**++ The stage that this job computes (either a `ResultStage` for an action or a
   `ShuffleMapStage` for `submitMapStage`).
- @param ++callSite++ Where this job was initiated in the user's program (shown on UI).
- @param ++listener++ A listener to notify if tasks in this job finish or the job fails.
- @param ++properties++ Scheduling properties attached to the job, such as fair scheduler pool name.
- ​

---
在背台线程中执行`DAGScheduler.handleJobSubmitted`先创建`ResultStage`，然后再创建`ActiveJob`，建立两者之间的关联之后，`DAGScheduler.submitStage`提交这个`ResultStage`, 如果该Stage有依赖，则先提交依赖的Stage。

提交Stage，其实就是提交`Task`，因此最终就是调用`DAGScheduler.submitMissingTasks`:

- 计算 Stage **Missing**的分区（`Partition`）
- 根据分区信息，计算每个`Task`的执行位置（`TaskLocation`）
- 序列化 Stage 引用的RDD，以及需要在该RDD上执行的函数(TODO: 代码即数据, 即**Scala中所谓的“**统一对象模型****”)
- 根据 Stage 的类型（i.e. `ResultStage`），为每一个分区创建`Task`（i.e. `ResultTask`）
- 最后通过 `TaskScheduler.submitTasks` 提交 `TaskSet`(jobId，attmeptId，stageId，task**s**)。


`TaskRunner`！！！

---
What does Closure.cleaner (func) mean in Spark?

> When Scala constructs a closure, it determines which outer variables the closure will use and stores references to them in the closure object. This allows the closure to work properly even when it's called from a different scope than it was created in.
>
> Scala sometimes errs on the side of capturing too many outer variables (see SI-1419). That's harmless in most cases, because the extra captured variables simply don't get used (though this prevents them from getting GC'd). But it poses a problem for Spark, which has to send closures across the network so they can be run on slaves. When a closure contains unnecessary references, it wastes network bandwidth. More importantly, some of the references may point to non-serializable objects, and Spark will fail to serialize the closure.
>
> To work around this bug in Scala, the [ClosureCleaner]() traverses the object at runtime and prunes the unnecessary references. Since it does this at runtime, it can be more accurate than the Scala compiler can. Spark can then safely serialize the cleaned closure.

---
![image](https://databricks.com/wp-content/uploads/2015/04/Screen-Shot-2015-04-12-at-8.41.26-AM-1024x235.png)

- 语法语义分析`ParseDrive.parse`

### Analysis

Spark SQL begins with a **relation** to be computed, either from an abstract syntax tree (AST) returned by a SQL parser, or from a DataFrame object constructed using the API. **In both cases, the relation may contain unresolved attribute references or relations**: for example, in the SQL query `SELECT col FROM sales`, the type of col, or even whether it is a valid column name, is not known until we look up the table sales. **An attribute is called unresolved if we do not know its type or have not matched it to an input table (or an alias)**. Spark SQL uses **Catalyst rules** and a **Catalog object** that tracks the tables in all data sources to resolve these attributes. It starts by building an “unresolved logical plan” tree with unbound attributes and data types, then applies rules that do the following:

- Looking up relations by name from the catalog.
- Mapping named attributes, such as col, to the input provided given operator’s children.
- Determining which attributes refer to the same value to give them a unique ID (which later allows optimization of expressions such as col = col).
- Propagating and coercing types through expressions: for example, we cannot know the return type of 1 + col until we have resolved col and possibly casted its subexpressions to a compatible types.

In total, the rules for the analyzer are about [1000 lines of code](https://github.com/apache/spark/blob/fedbfc7074dd6d38dc5301d66d1ca097bc2a21e0/sql/catalyst/src/main/scala/org/apache/spark/sql/catalyst/analysis/Analyzer.scala).




# 术语
- CTE [Common Table Expression]


---
<style type="text/css">
.tg  {border-collapse:collapse;border-spacing:0;}
.tg td{font-family:"DejaVu Sans Mono", sans-serif;font-size:14px;padding:10px 5px;border-style:solid;border-width:1px;overflow:hidden;word-break:normal;}
.tg th{font-family:"DejaVu Sans Mono", sans-serif;font-size:14px;font-weight:normal;padding:10px 5px;border-style:solid;border-width:1px;overflow:hidden;word-break:normal;}
.tg .tg-yw4l{vertical-align:top}
</style>
<table class="tg">
  <tr>
    <th class="tg-yw4l">Transformation</th>
    <th class="tg-yw4l">RDD</th>
    <th class="tg-yw4l">Meaning</th>
  </tr>
  <tr>
    <td class="tg-yw4l">map(*func*)</td>
    <td class="tg-yw4l">MapPartitionsRDD</td>
    <td class="tg-yw4l">Return a new distributed dataset formed by passing each element of the source through a function func.</td>
  </tr>
  <tr>
    <td class="tg-yw4l">filter(func)</td>
    <td class="tg-yw4l">MapPartitionsRDD</td>
    <td class="tg-yw4l">Return a new dataset formed by selecting those elements of the source on which func returns true.</td>
  </tr>
  <tr>
    <td class="tg-yw4l">flatMap(func)</td>
    <td class="tg-yw4l">MapPartitionsRDD</td>
    <td class="tg-yw4l">Similar to map, but each input item can be mapped to 0 or more output items (so func should return a Seq rather than a single item).</td>
  </tr>
  <tr>
    <td class="tg-yw4l">mapPartitions(func)</td>
    <td class="tg-yw4l">MapPartitionsRDD</td>
    <td class="tg-yw4l">Similar to map, but runs separately on each partition (block) of the RDD, so func must be of type Iterator&lt;T&gt; =&gt; Iterator&lt;U&gt; when running on an RDD of type T.</td>
  </tr>
  <tr>
    <td class="tg-yw4l">mapPartitionsWithIndex(func)</td>
    <td class="tg-yw4l">MapPartitionsRDD</td>
    <td class="tg-yw4l">Similar to mapPartitions, but also provides func with an integer value representing the index of the partition, so func must be of type (Int, Iterator&lt;T&gt;) =&gt; Iterator&lt;U&gt; when running on an RDD of type T.</td>
  </tr>
  <tr>
    <td class="tg-yw4l">sample(withReplacement, fraction, seed)</td>
    <td class="tg-yw4l">PartitionwiseSampledRDD</td>
    <td class="tg-yw4l">Sample a fraction fraction of the data, with or without replacement, using a given random number generator seed.</td>
  </tr>
  <tr>
    <td class="tg-yw4l">union(otherDataset)</td>
    <td class="tg-yw4l">UnionRDD PartitionerAwareUnionRDD</td>
    <td class="tg-yw4l">Return a new dataset that contains the union of the elements in the source dataset and the argument.</td>
  </tr>
  <tr>
    <td class="tg-yw4l">intersection(otherDataset)</td>
    <td class="tg-yw4l">-</td>
    <td class="tg-yw4l">Return a new RDD that contains the intersection of elements in the source dataset and the argument.</td>
  </tr>
  <tr>
    <td class="tg-yw4l">distinct([numTasks]))</td>
    <td class="tg-yw4l">-</td>
    <td class="tg-yw4l">Return a new dataset that contains the distinct elements of the source dataset.</td>
  </tr>
  <tr>
    <td class="tg-yw4l">groupByKey([numTasks])</td>
    <td class="tg-yw4l">*ShuffledRDD*</td>
    <td class="tg-yw4l">When called on a dataset of (K, V) pairs, returns a dataset of (K, Iterable&lt;V&gt;) pairs. Note: If you are grouping in order to perform an aggregation (such as a sum or average) over each key, using reduceByKey or aggregateByKey will yield much better performance. Note: By default, the level of parallelism in the output depends on the number of partitions of the parent RDD. You can pass an optional numTasks argument to set a different number of tasks.</td>
  </tr>
  <tr>
    <td class="tg-yw4l">reduceByKey(func, [numTasks])</td>
    <td class="tg-yw4l">*ShuffledRDD*</td>
    <td class="tg-yw4l">When called on a dataset of (K, V) pairs, returns a dataset of (K, V) pairs where the values for each key are aggregated using the given reduce function func, which must be of type (V,V) =&gt; V. Like in groupByKey, the number of reduce tasks is configurable through an optional second argument.</td>
  </tr>
  <tr>
    <td class="tg-yw4l">aggregateByKey(zeroValue)(seqOp, combOp, [numTasks])</td>
    <td class="tg-yw4l">*ShuffledRDD*</td>
    <td class="tg-yw4l">When called on a dataset of (K, V) pairs, returns a dataset of (K, U) pairs where the values for each key are aggregated using the given combine functions and a neutral "zero" value. Allows an aggregated value type that is different than the input value type, while avoiding unnecessary allocations. Like in groupByKey, the number of reduce tasks is configurable through an optional second argument.</td>
  </tr>
  <tr>
    <td class="tg-yw4l">sortByKey([ascending], [numTasks])</td>
    <td class="tg-yw4l">*ShuffledRDD*</td>
    <td class="tg-yw4l">When called on a dataset of (K, V) pairs where K implements Ordered, returns a dataset of (K, V) pairs sorted by keys in ascending or descending order, as specified in the boolean ascending argument.</td>
  </tr>
  <tr>
    <td class="tg-yw4l">join(otherDataset, [numTasks])</td>
    <td class="tg-yw4l">-</td>
    <td class="tg-yw4l">When called on datasets of type (K, V) and (K, W), returns a dataset of (K, (V, W)) pairs with all pairs of elements for each key. Outer joins are supported through leftOuterJoin, rightOuterJoin, and fullOuterJoin.</td>
  </tr>
  <tr>
    <td class="tg-yw4l">cogroup(otherDataset, [numTasks])</td>
    <td class="tg-yw4l">-</td>
    <td class="tg-yw4l">When called on datasets of type (K, V) and (K, W), returns a dataset of (K, (Iterable&lt;V&gt;, Iterable&lt;W&gt;)) tuples. This operation is also called groupWith.</td>
  </tr>
  <tr>
    <td class="tg-yw4l">cartesian(otherDataset)</td>
    <td class="tg-yw4l">CartesianRDD</td>
    <td class="tg-yw4l">When called on datasets of types T and U, returns a dataset of (T, U) pairs (all pairs of elements).</td>
  </tr>
  <tr>
    <td class="tg-yw4l">pipe(command, [envVars])</td>
    <td class="tg-yw4l">PipedRDD</td>
    <td class="tg-yw4l">Pipe each partition of the RDD through a shell command, e.g. a Perl or bash script. RDD elements are written to the process's stdin and lines output to its stdout are returned as an RDD of strings.</td>
  </tr>
  <tr>
    <td class="tg-yw4l">coalesce(numPartitions)</td>
    <td class="tg-yw4l">CoalescedRDD</td>
    <td class="tg-yw4l">Decrease the number of partitions in the RDD to numPartitions. Useful for running operations more efficiently after filtering down a large dataset.</td>
  </tr>
  <tr>
    <td class="tg-yw4l">repartition(numPartitions) = coalesce(numPartitions,**true**)</td>
    <td class="tg-yw4l">CoalescedRDD</td>
    <td class="tg-yw4l">Reshuffle the data in the RDD randomly to create either more or fewer partitions and balance it across them. This always shuffles all data over the network.</td>
  </tr>
  <tr>
    <td class="tg-yw4l">repartitionAndSortWithinPartitions(partitioner)</td>
    <td class="tg-yw4l">ShuffledRDD</td>
    <td class="tg-yw4l">Repartition the RDD according to the given partitioner and, within each resulting partition, sort records by their keys. This is more efficient than calling repartition and then sorting within each partition because it can push the sorting down into the shuffle machinery.</td>
  </tr>
</table>



---
## SparkSession – a new entry point
**SparkSession is the “SparkContext” for Dataset/DataFrame**
- Entry point for reading data
- Working with metadata
- Configuration
- Cluster resource management

## Structure Streaming

```
     LogicalPlan                   SparkPlan
          ^                            ^
          |                            |
          |                            |
      LeafNode                    LeafExecNode
          ^                            ^
          |---------|                  |
          |         |                  |
          | StreamingRelation          |
          |                            |
          |                            |
StreamingExecutionRelation     StreamingRelationExec 
```
---
王玉明
1. [[SPARK-16625] Oracle JDBC table creation fails with ORA-00902: invalid datatype](https://issues.apache.org/jira/browse/SPARK-16625)
2. An implementation of Factorization Machine (LibFM)