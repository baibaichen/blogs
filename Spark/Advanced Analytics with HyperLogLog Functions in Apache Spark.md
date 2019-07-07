# [Advanced Analytics with HyperLogLog Functions in Apache Spark](https://databricks.com/blog/2019/05/08/advanced-analytics-with-apache-spark.html)

Pre-aggregation is a common technique in the high-performance analytics toolbox. For example, 10 billion rows of website visitation data per hour may be reducible to 10 million rows of visit counts, aggregated by the superset of dimensions used in common queries, a 1000x reduction in data processing volume with a corresponding decrease in processing costs and waiting time to see the result of any query. Further improvements could come from computing higher-level aggregates, e.g., by day in the time dimension or by the site as opposed to by URL.

In this blog, we introduce the advanced HyperLogLog functionality of the open-source library [spark-alchemy](https://github.com/swoop-inc/spark-alchemy) and explore how it addresses data aggregation challenges at scale. But first, let’s explore some of the challenges.

## The Challenges of Reaggregation

Pre-aggregation is a powerful analytics technique… as long as the measures being computed are *reaggregable*. In the dictionary, aggregate has aggregable, so it’s a small stretch to invent reaggregable as having the property that aggregates may be further reaggregated. Counts reaggregate with SUM, minimums with MIN, maximums with MAX, etc. ==The odd one out is distinct counts, which are not reaggregable. For example, the sum of the distinct count of visitors by site will typically not be equal to the distinct count of visitors across all sites because of double counting: the same visitor may visit multiple sites.==

The non-reaggregability of distinct counts has far-reaching implications. The system computing distinct counts must have access to the most granular level of data. Further, queries that return distinct counts have to touch every row of granular data.

![img](https://databricks.com/wp-content/uploads/2019/05/standard-workflow.png)

When it comes to big data, distinct counts pose an additional challenge: during computation, they require memory proportional to the size of all distinct values being counted. In recent years, big data systems such as Apache Spark and analytics-oriented databases such as Amazon Redshift have introduced functionality for approximate distinct counting, a.k.a., cardinality estimation, using the HyperLogLog (HLL) probabilistic data structure. To use approximate distinct counts in Spark, replace `COUNT(DISTINCT x)` with `approx_count_distinct(x [, rsd])`. The optional rsd argument is the maximum estimation error allowed. The default is 5%. HLL performance analysis by Databricks indicates that Spark’s approximate distinct counting may enable aggregations to run 2-8x faster compared to when precise counts are used, as long as the maximum estimation error is 1% or higher. However, if we require a lower estimation error, approximate distinct counts may actually take longer to compute than precise distinct counts.

A 2-8x reduction in query execution time is a solid improvement on its own, but it comes at the cost of an estimation error of 1% or more, which may not be acceptable in some situations. Further, 2-8x reduction gains for distinct counts pale in comparison to the 1000x gains available through pre-aggregation. What can we do about this?

> 减少2-8倍的查询执行时间本身是一个很好的改进，但它的代价是估计误差为1%或更高，在某些情况下这可能是不可接受的。此外，与通过**预聚合**获得的1000倍收益相比，==不重复计数==2-8倍的收益相形见绌。我们该怎么办？

## 重温 HyperLogLog

The answer lies in the guts of the HyperLogLog algorithm. In the partitioned MapReduce pseudocode, the way Spark processes, HLL looks like this:

1. Map (for each partition)
   - Initialize an HLL data structure, called an HLL sketch
   - Add each input to the sketch
   - Emit the sketch
2. Reduce
   - Merge all sketches into an “aggregate” sketch
3. Finalize
   - Compute approximate distinct count from the aggregate sketch

**Note that HLL sketches are reaggregable**: when they are merged in the reduce operation, the result is an HLL sketch. If we serialize sketches as data, we can persist them in pre-aggregations and compute the approximate distinct counts at a later time, unlocking 1000x gains. This is huge!

There is another, subtler, but no less important, benefit: we are no longer bound by the practical requirement to have estimation errors of 1% or more. When pre-aggregation allows 1000x gains, we can easily build HLL sketches with very, very small estimation errors. It’s rarely a problem for a pre-aggregation job to run 2-5x slower if there are 1000x gains at query time. This is the closest to a free lunch we can get in the big data business: significant cost/performance improvements without a negative trade-off from a business standpoint for most use cases.

## Introducing Spark-Alchemy: HLL Native Functions

Since Spark does not provide this functionality, [Swoop](https://www.swoop.com/) open-sourced a rich suite of native (high-performance) HLL functions as part of the [spark-alchemy](https://github.com/swoop-inc/spark-alchemy) library. Take a look at the [HLL docs](https://github.com/swoop-inc/spark-alchemy/wiki/Spark-HyperLogLog-Functions), which have lots of examples. To the best of our knowledge, this is the richest set of big data HyperLogLog processing capabilities, exceeding even [BigQuery’s HLL support](https://cloud.google.com/bigquery/docs/reference/standard-sql/functions-and-operators#hyperloglog-functions).

The following diagram demonstrates how spark-alchemy handles initial aggregation (via `hll_init_agg`), reaggregation (via `hll_merge`) and presentation (via `hll_cardinality`).

![img](https://databricks.com/wp-content/uploads/2019/05/workflow-with-hll-functions.png)

If you are wondering about the storage cost of HLL sketches, the simple rule of thumb is that a 2x increase in HLL cardinality estimation precision requires a 4x increase in the size of HLL sketches. In most applications, the reduction in the number of rows far outweighs the increase in storage due to the HLL sketches.

| error | sketch_size_in_bytes |
| :---- | :------------------- |
| 0.005 | 43702 = 46.8 (KB)    |
| 0.01  | 10933                |
| 0.02  | 2741                 |
| 0.03  | 1377                 |
| 0.04  | 693                  |
| 0.05  | 353                  |
| 0.06  | 353                  |
| 0.07  | 181                  |
| 0.08  | 181                  |
| 0.09  | 181                  |
| 0.1   | 96                   |

## HyperLogLog 互操作性

The switch from precise to approximate distinct counts and the ability to save HLL sketches as a column of data has eliminated the need to process every row of granular data at final query time, but we are still left with the implicit requirement that the system working with HLL data has to have access to all granular data. The reason is that there is no industry-standard representation for HLL data structure serialization. Most implementations, such as BigQuery’s, use undocumented opaque binary data, which cannot be shared across systems. This interoperability challenge significantly increases the cost and complexity of interactive analytics systems.

> 从精确到近似的==不重复计数==，以及将==HLL草图==保存为数据列的能力，消除了在最终查询时处理每一行粒度数据的需要，但我们仍然有一个隐含的要求，即使用HLL数据的系统必须具有访问所有粒度数据。原因是，HLL数据结构序列化没有==**标准化的行业实现**==。大多数实现（如bigquery）使用未记录的不透明二进制数据，这些数据不能跨系统共享。这种互操作性挑战极大地增加了交互式分析系统的成本和复杂性。

A key requirement for interactive analytics systems is very fast query response times. This is not a core design goal for big data systems such as Spark or BigQuery, which is why interactive analytics queries are typically executed by some relational or, in some cases, NoSQL database. Without HLL sketch interoperability at the data level, we’d be back to square one.

> 交互式分析系统的一个关键要求是非常快的查询响应时间。这不是Spark或BigQuery等大数据系统的核心设计目标，这就是为什么交互式分析查询通常由某些关系数据库或NoSQL数据库执行的原因。如果没有数据级的==HLL草图==互操作性，我们将回到原点。

To address this issue, when implementing the HLL capabilities in spark-alchemy, we purposefully chose an HLL implementation with a published [storage specification](https://github.com/aggregateknowledge/hll-storage-spec) and [built-in support for Postgres-compatible databases](https://github.com/citusdata/postgresql-hll) and even [JavaScript](https://github.com/aggregateknowledge/js-hll). This allows Spark to serve as a universal data pre-processing platform for systems that require fast query ==turnaround== times, such as portals & dashboards. The benefits of this architecture are significant:

- 99+% of the data is managed via Spark only, with no duplication
- 99+% of processing happens through Spark, during pre-aggregation
- Interactive queries run much, much faster and require far fewer resources


>为了解决这个问题，在spark-alchemy中实现HLL功能时，我们有意选择了一个HLL实现，该实现具有已发布的[存储规范](https://github.com/aggregateknowledge/hll-storage-spec)，并且内置支持[Postgres兼容的数据库](https://github.com/citusdata/postgresql-hll)，甚至支持[JavaScript](https://github.com/aggregateknowledge/js-hll)。 这使得Spark可以作为一个通用的数据预处理平台，用于需要快速响应的查询系统，如网站入口和仪表盘。这种体系结构的好处是显著的：
>
>1. 99％以上的数据仅通过Spark进行管理，没有重复。
>2. 99%以上的处理是通过Spark在预聚合期间进行的。
>3. 交互式查询运行速度更快，所需资源更少。

## 小结

In summary, we have shown how the commonly-used technique of pre-aggregation can be efficiently extended to distinct counts using HyperLogLog data structures, which not only unlocks potential 1000x gains in processing speed but also gives us interoperability between Apache Spark, RDBMSs and even JavaScript. It’s hard to believe, but we may have gotten very close to two free lunches in one big data blog post, all because of the power of HLL sketches and Spark’s powerful extensibility.

Advanced HLL processing is just one of the goodies in [spark-alchemy](https://github.com/swoop-inc/spark-alchemy). Check out [what’s coming](https://github.com/swoop-inc/spark-alchemy#whats-coming) and [let us know](mailto:spark-interest@swoop.com) which items on the list are important to you and what else you’d like to see there.

Last but most definitely not least, the data engineering and data science teams at Swoop would like to thank the engineering and support teams at Databricks for partnering with us to redefine what is possible with Apache Spark. You rock!

# [Apache Spark native functions](https://blog.simeonov.com/2018/11/14/apache-spark-native-functions/)

There are many ways to extend [Apache Spark](https://spark.apache.org/) and one of the easiest is with functions that manipulate one of more columns in a [DataFrame](https://spark.apache.org/docs/latest/sql-programming-guide.html#datasets-and-dataframes). When considering [different Spark function types](https://medium.com/@mrpowers/the-different-type-of-spark-functions-custom-transformations-column-functions-udfs-bf556c9d0ce7), it is important to not ignore the full set of options available to developers.

>有许多方法可以扩展[Apache Spark](https://spark.apache.org/)，其中一个最简单的方法是使SQL函数，它操作于[DataFrame](https://spark.apache.org/docs/latest/sql-programming-guide.html#datasets-and-dataframes)中一个或多个列之上。 当考虑到[Spark不同的函数类型](https://medium.com/@mrpowers/the-different-type-of-spark-functions-custom-transformations-column-functions-udfs-bf556c9d0ce7)时，重要的是不要忽略开发人员可用的全套选项。

Beyond the two types of functions–simple Spark [user-defined functions](https://docs.databricks.com/spark/latest/spark-sql/udaf-scala.html) (UDFs) and functions that operate on [Column](https://github.com/apache/spark/blob/f26cd18816fe85e22393887d2d201f8554ccb37d/sql/core/src/main/scala/org/apache/spark/sql/Column.scala#L103)–described in the previous link, there two more types of UDFs: **user-defined aggregate functions** (UDAFs) and **user-defined table-generating functions** (UDTFs). [sum()](https://spark.apache.org/docs/latest/api/sql/index.html#sum) is an example of an aggregate function and [explode()](https://spark.apache.org/docs/latest/api/sql/index.html#explode) is an example of a table-generating function. The former processes many rows to create a single value. The latter uses value(s) from a single row to “generate” many rows. Spark supports UDAFs [directly](https://docs.databricks.com/spark/latest/spark-sql/udaf-scala.html) and UDTFs [indirectly](https://github.com/apache/spark/blob/f38594fc561208e17af80d17acf8da362b91fca4/sql/hive/src/main/scala/org/apache/spark/sql/hive/hiveUDFs.scala#L187), by converting them to Generator expressions.

>除了前面链接中描述的两种类型的函数：简单的Spark[用户定义函数](https://docs.databricks.com/spark/latest/spark-sql/udaf-scala.html)（UDFs）和对[列](https://github.com/apache/spark/blob/f26cd18816fe85e22393887d2d201f8554ccb37d/sql/core/src/main/scala/org/apache/spark/sql/Column.scala#L103)进行操作的函数。还有两种类型的UDF：**用户定义的聚合函数**（UDAFs）和**用户定义的表生成函数**（UDTFs）。[`sum()`](https://spark.apache.org/docs/latest/api/sql/index.html#sum)是聚合函数的示例，[`explode()`](https://spark.apache.org/docs/latest/api/sql/index.html#explode)是表生成函数的示例。前者处理多行以创建单个值，后者使用一行中的值“生成”多行。Spark通过将它们转换为**生成器表达式**，[直接](https://docs.databricks.com/spark/latest/spark-sql/udaf-scala.html)支持UDAFs，[间接](https://github.com/apache/spark/blob/f38594fc561208e17af80d17acf8da362b91fca4/sql/hive/src/main/scala/org/apache/spark/sql/hive/hiveUDFs.scala#L187)支持UDTFs。

Beyond all types of UDFs, Spark’s most exciting functions are Spark’s native functions, which is how the logic of most of Spark’s Column and SparkSQL functions is implemented. Internally, Spark native functions are nodes in the Expression trees that determine column values. Very loosely-speaking, an Expression is the internal Spark representation for a Column, just like a LogicalPlan is the internal representation of a data transformation (Dataset/DataFrame).

> 除了各种UDF之外，Spark最令人兴奋的功能是==**Spark-native函数**==，**这就是Spark的大多数`Column`和SparkSQL函数的逻辑实现方式**。 在内部，**Spark-native函数**是表达式树中用于确定列值的节点。 非常松散地说，`Expression`是Spark内部`Column`的表示，就像`LogicalPlan`是数据转换（`Dataset` / `DataFrame`）的内部表示一样。

Native functions, while a bit more involved to create, have three fundamental advantages: better *user experience*, *flexibility* and *performance*.

> **native函数**虽然在创建方面有点复杂，但有三个基本优势：更好的用户体验、灵活性和性能。

Better user experience & flexibility comes from native functions’ lifecycle having two distinct phases:

1. Analysis, which happens on the driver, while the transformation DAG is created (before an action is run).
2. Execution, which happens on executors/workers, while an action is running.

>更好的用户体验和灵活性来自于**native函数**的生命周期，有两个不同的阶段：
>
>1. 分析阶段：在创建DAG时（执行**action**之前），在**driver**上进行分析。
>2. 执行阶段：在执行**action**时，在**executors**/**workers**上运行**native函数**。

The analysis phase allows Spark native functions to dynamically validate the type of their inputs to produce better error messages and, if necessary, change the type of their result. For example, the return type of sort_array() depends on the input type. If you pass in an array of strings, you’ll get an array of strings. If you pass in an array of ints, you’ll get an array of ints.

> 分析阶段允许**Spark-native函数**动态验证其输入的类型，以生成更好的错误消息，并在必要时更改其结果的类型。 例如，`sort_array()`的返回类型取决于输入类型。 如果传入一个字符串数组，则返回一个字符串数组； 如果传入一个int数组，则返回一个int数组。

A user-defined function, which internally maps to a strongly-typed Scala/JVM function, cannot do this. We can parameterize an implementation by the type of its input, e.g.,

> UDF（内部映射到强类型的Scala / JVM函数）无法执行此操作。 我们可以通过参数化输入类型来实现，例如：

```scala
def mySortArray[A: Ordered](arr: Array[A]): Array[A]
```

but we cannot create type-parameterized UDFs in Spark, requiring hacks such as

>但我们不能在Spark中创建**类型参数化**的UDF，需要诸如此类的黑技巧：

```scala
spark.udf.register("my_sort_array_int", mySortArray[Int] _)
spark.udf.register("my_sort_array_long", mySortArray[Long] _)
//…
```

Think of native functions like macros in a traditional programming language. The power of macros also comes from having a lifecycle with two execution phases: compile-time and runtime.

> 可以将**native函数**想象成传统编程语言中的宏。宏的强大还来自于在执行阶段有两个生命周期：编译时和运行时。

Performance comes from the fact that Spark native functions operate on the internal Spark representation of rows, which, in many cases, avoids serialization/deserialization to “normal” Scala/Java/Python/R datatypes. For example, internally Spark strings are [UTF8String](https://github.com/apache/spark/blob/17781d75308c328b11cab3658ca4f358539414f2/common/unsafe/src/main/java/org/apache/spark/unsafe/types/UTF8String.java). Further, you can choose to implement the runtime behavior of a native function by code-generating Java and participating in whole-stage code generation (reinforcing the macro analogy) or as a simple method.

> 性能来自于**Spark-native函数**直接在Spark Row（`InternalRow`）的==内部表示==上操作的事实，这在许多情况下，避免了序列化/反序列化为“普通的”Scala / Java / Python / R数据类型。 例如，Spark内部的字符串是[`UTF8String`](https://github.com/apache/spark/blob/17781d75308c328b11cab3658ca4f358539414f2/common/unsafe/src/main/java/org/apache/spark/unsafe/types/UTF8String.java)。此外，可以选择用简单的方法来实现**native函数**，或者参与到==整个代码生成阶段==（类比于宏增强，感觉就是模板啊），通过生成Java代码来实现**native函数**的运行时行为。

Working with Spark’s internal (a.k.a., unsafe) datatypes does require careful coding but Spark’s codebase includes many dozens of examples of native functions: essentially, the entire [SparkSQL function library](https://spark.apache.org/docs/latest/api/sql/index.html). I encourage you to experiment with native Spark function development. As an example, take a look at [array_contains()](https://github.com/apache/spark/blob/d03e0af80d7659f12821cc2442efaeaee94d3985/sql/catalyst/src/main/scala/org/apache/spark/sql/catalyst/expressions/collectionOperations.scala#L1321).

> 使用Spark内部的（即unsafe）数据类型确实需要仔细编码，但Spark的代码库包含许多**native函数**（本质上是[整个SparkSQL函数库](https://spark.apache.org/docs/latest/api/sql/index.html)）使用unsafe数据类型的示例。建议大家尝试下**Spark-native函数**的开发，可以用[`array_contains()`](https://github.com/apache/spark/blob/d03e0af80d7659f12821cc2442efaeaee94d3985/sql/catalyst/src/main/scala/org/apache/spark/sql/catalyst/expressions/collectionOperations.scala#L1321)作为例子，看看实现。

For user experience, flexibility and performance reasons, at [Swoop](https://www.swoop.com/) we have created a number of native Spark functions. We plan on open-sourcing many of them, as well as other tools we have created for improving Spark productivity and performance, via the [spark-alchemy](https://github.com/swoop-inc/spark-alchemy) library.

## 中文
有许多方法可以扩展[Apache Spark](https://spark.apache.org/)，其中一个最简单的方法是使SQL函数，它操作于[DataFrame](https://spark.apache.org/docs/latest/sql-programming-guide.html#datasets-and-dataframes)中一个或多个列之上。 当考虑到[Spark不同的函数类型](https://medium.com/@mrpowers/the-different-type-of-spark-functions-custom-transformations-column-functions-udfs-bf556c9d0ce7)时，重要的是不要忽略开发人员可用的全套选项。

除了前面链接中描述的两种类型的函数：简单的Spark[用户定义函数](https://docs.databricks.com/spark/latest/spark-sql/udaf-scala.html)（UDFs）和对[列](https://github.com/apache/spark/blob/f26cd18816fe85e22393887d2d201f8554ccb37d/sql/core/src/main/scala/org/apache/spark/sql/Column.scala#L103)进行操作的函数。还有两种类型的UDF：**用户定义的聚合函数**（UDAFs）和**用户定义的表生成函数**（UDTFs）。[`sum()`](https://spark.apache.org/docs/latest/api/sql/index.html#sum)是聚合函数的示例，[`explode()`](https://spark.apache.org/docs/latest/api/sql/index.html#explode)是表生成函数的示例。前者处理多行以创建单个值，后者使用一行中的值“生成”多行。Spark通过将它们转换为**生成器表达式**，[直接](https://docs.databricks.com/spark/latest/spark-sql/udaf-scala.html)支持UDAFs，[间接](https://github.com/apache/spark/blob/f38594fc561208e17af80d17acf8da362b91fca4/sql/hive/src/main/scala/org/apache/spark/sql/hive/hiveUDFs.scala#L187)支持UDTFs。

除了各种UDF之外，Spark最令人兴奋的功能是==**Spark-native函数**==，==**这就是Spark的大多数`Column`和SparkSQL函数的逻辑实现方式**==。 在内部，**Spark-native函数**是表达式树中用于确定列值的节点。 非常松散地说，`Expression`是Spark内部`Column`的表示，就像`LogicalPlan`是数据转换（`Dataset` / `DataFrame`）的内部表示一样。

**native函数**虽然在创建方面有点复杂，但有三个基本优势：更好的用户体验、灵活性和性能。

更好的用户体验和灵活性来自于**native函数**的生命周期，有两个不同的阶段：

1. 分析阶段：在创建DAG时（执行**action**之前），在**driver**上进行分析。
2. 执行阶段：在执行**action**时，在**executors**/**workers**上运行**native函数**。

分析阶段允许**Spark-native函数**动态验证其输入的类型，以生成更好的错误消息，并在必要时更改其结果的类型。 例如，`sort_array()`的返回类型取决于输入类型。 如果传入一个字符串数组，则返回一个字符串数组； 如果传入一个int数组，则返回一个int数组。

UDF（内部映射到强类型的Scala / JVM函数）无法执行此操作。 我们可以通过参数化输入类型来实现，例如：

```scala
def mySortArray[A: Ordered](arr: Array[A]): Array[A]
```
但我们不能在Spark中创建**类型参数化**的UDF，需要诸如此类的黑技巧：

```scala
spark.udf.register("my_sort_array_int", mySortArray[Int] _)
spark.udf.register("my_sort_array_long", mySortArray[Long] _)
//…
```

可以将**native函数**想象成传统编程语言中的宏。宏的强大还来自于在执行阶段有两个生命周期：编译时和运行时。

**性能**来自于**Spark-native函数**直接在Spark Row（`InternalRow`）的==内部表示==上操作，在许多情况下，这避免了序列化/反序列化为“普通的”Scala / Java / Python / R数据类型。 例如，Spark内部的字符串是[`UTF8String`](https://github.com/apache/spark/blob/17781d75308c328b11cab3658ca4f358539414f2/common/unsafe/src/main/java/org/apache/spark/unsafe/types/UTF8String.java)。此外，可以选择用简单的方法来实现**native函数**，或者参与到==整个代码生成阶段==（类比于宏增强，感觉就是模板啊），通过生成Java代码来实现**native函数**的运行时行为。

使用Spark内部的（即unsafe）数据类型确实需要仔细编码，但Spark的代码库包含许多**native函数**（本质上是[整个SparkSQL函数库](https://spark.apache.org/docs/latest/api/sql/index.html)）使用unsafe数据类型的示例。建议大家尝试下**Spark-native函数**的开发，可以用[`array_contains()`](https://github.com/apache/spark/blob/d03e0af80d7659f12821cc2442efaeaee94d3985/sql/catalyst/src/main/scala/org/apache/spark/sql/catalyst/expressions/collectionOperations.scala#L1321)作为例子，看看实现。

>For user experience, flexibility and performance reasons, at [Swoop](https://www.swoop.com/) we have created a number of native Spark functions. We plan on open-sourcing many of them, as well as other tools we have created for improving Spark productivity and performance, via the [spark-alchemy](https://github.com/swoop-inc/spark-alchemy) library.

# Spark HyperLogLog Functions

## HyperLogLog background

Precise distinct counts are expensive to compute in Spark because:

1. When large cardinalities are involved, precise distinct counts require a lot of memory/IO, and
2. Every row of data has to be processed for every query because distinct counts cannot be reaggregated

[HyperLogLog](https://en.wikipedia.org/wiki/HyperLogLog) (HLL) can address both problems. Memory use is reduced via the tight **binary sketch** representation and the data can be pre-aggregated because HLL binary sketches--unlike distinct counts--are **mergeable**. It is unfortunate that Spark's HLL implementation does not expose the binary HLL sketches, which makes its usefulness rather limited: it addresses (1) but not (2) above.

### Additional reading

- [HLL in BigQuery](https://cloud.google.com/blog/products/gcp/counting-uniques-faster-in-bigquery-with-hyperloglog)
- [HLL in Spark](https://databricks.com/blog/2016/05/19/approximate-algorithms-in-apache-spark-hyperloglog-and-quantiles.html)

## spark-alchemy's approach to approximate counting

The HLL Spark native functions in spark-alchemy provide two key benefits:

1. They expose HLL sketches as binary columns, enabling 1,000+x speedups in approximate distinct count computation via pre-aggregation.
2. They enable interoperability at the HLL sketch level with other data processing systems. We use an open-source [HLL library](https://github.com/aggregateknowledge/java-hll) with an independent [storage specification](https://github.com/aggregateknowledge/hll-storage-spec) and [built-in support for Postgres-compatible databases](https://github.com/citusdata/postgresql-hll) and even [JavaScript](https://github.com/aggregateknowledge/js-hll). This allows Spark to serve as a universal data (pre-)processing platform for systems that require fast query turnaround times, e.g., portals & dashboards.

spark-alchemy provides a richer set of HLL functions than either Spark or BigQuery. The full list of HLL functions is:

- **hll_cardinality(hll_sketch)**: returns the cardinality (distinct count) of items in the set represented by the HLL sketch.
- **hll_intersect_cardinality(hll_sketch, hll_sketch)**: computes a merged (unioned) sketch and uses the fact that |A intersect B| = (|A| + |B|) - |A union B| to estimate the intersection cardinality of the two sketches.
- **hll_init(column[, precision])**: creates an HLL sketch (a binary column) for each value in `column`. `hll_init()` is designed for use outside of aggregation (GROUP BY) as a privacy protection tool (because it hashes potentially sensitive IDs) and/or to prepare granular data for re-aggregation. Instead of `hll_init()`, you will typically use `hll_init_agg()`.
  - **precision**: here and everywhere else, determines the maximum error for cardinality computation. The smaller the precision, the greater the size of the binary sketch. The default value is 0.05, just as with Spark's `approx_distinct_count()`. See [HLL in Spark](https://databricks.com/blog/2016/05/19/approximate-algorithms-in-apache-spark-hyperloglog-and-quantiles.html) for background on the relationship between precision and computation cost. Sometimes, it is worth using low precision and suffering the slow computation as long as the data can be pre-aggregated as follow-on re-aggregation will perform much faster on the existing aggregates.
- **hll_init_agg(column[, precision])**: like `hll_init()`, it creates an HLL sketch, but designed for use with aggregation (GROUP BY), i.e., it creates a sketch for all values in a group. Logically equivalent to `hll_merge(hll_init(...))`.
- **hll_init_collection(array_or_map_column[, precision])**: creates an HLL sketch for each value in the column, which has to be an array or a map. Having collection versions of HLL functions is a performance optimization, eliminating the need to explode & re-group data.
- **hll_init_collection_agg(array_or_map_column[, precision]):** like `hll_init_collection()`, but designed for use with aggregation (GROUP BY), i.e., it creates a sketch for all values in the arrays/maps of a group. Logically equivalent to `hll_merge(hll_init_collection(...))`.
- **hll_merge(hll_sketch)**: merges HLL sketches during an aggregation operation.
- **hll_row_merge(hll_sketches\*)**: merges multiple sketches in one row into a single field.

## Using HLL functions

### From SparkSQL

For maximum performance and flexibility, `spark-alchemy` implements HLL functionality as [native Spark functions](https://blog.simeonov.com/2018/11/14/apache-spark-native-functions/). Similar to user-defined functions, native functions require registration using the following Scala command.

```scala
// Register spark-alchemy HLL functions for use from SparkSQL
com.swoop.alchemy.spark.expressions.hll.HLLFunctionRegistration.registerFunctions(spark)
```

### From Scala

```scala
import com.swoop.alchemy.spark.expressions.hll.functions._
```

## Example set up

The following examples assume we are working with 100,000 distinct IDs from 0..99,999 in a dataframe with a single column `id`. SparkSQL examples assume this data is available as a table/view called `ids`. You can create it using

```scala
spark.range(100000).createOrReplaceTempView("ids")
```

## Basic approximate counting

Let's compute the distinct count of IDs using exact counting, Spark's built-in approximate counting function and spark-alchemy's functions. We'll look at the output at different precisions.

You will note that for basic approximate counts spark-alchemy requires two functions (`hll_cardinality(hll_init_agg(...))`) vs. Spark's single (`approx_count_distinct(...)`). The reason will become apparent in the next section.

### SparkSQL

```sql
select
    -- exact distinct count
    count(distinct id) as cntd,
    -- Spark's HLL implementation with default 5% precision
    approx_count_distinct(id) as anctd_spark_default,
    -- approximate distinct count with default 5% precision
    hll_cardinality(hll_init_agg(id)) as acntd_default,
    -- approximate distinct counts with custom precision
    map(
        0.005, hll_cardinality(hll_init_agg(id, 0.005)),
        0.020, hll_cardinality(hll_init_agg(id, 0.020)),
        0.050, hll_cardinality(hll_init_agg(id, 0.050)),
        0.100, hll_cardinality(hll_init_agg(id, 0.100))
    ) as acntd
from ids
```

### Scala

```scala
import org.apache.spark.sql.functions._
import com.swoop.alchemy.spark.expressions.hll.functions._

spark.range(100000).select(
  // exact distinct count
  countDistinct('id).as("cntd"),
  // Spark's HLL implementation with default 5% precision
  approx_count_distinct('id).as("anctd_spark_default"),
  // approximate distinct count with default 5% precision
  hll_cardinality(hll_init_agg('id)).as("acntd_default"),
  // approximate distinct counts with custom precision
  map(
    Seq(0.005, 0.02, 0.05, 0.1).flatMap { error =>
      lit(error) :: hll_cardinality(hll_init_agg('id, error)) :: Nil
    }: _*
  ).as("acntd")
).show(false)
```

### Output

```bash
+------+-------------------+-------------+-------------------------------------------------------------+
|cntd  |anctd_spark_default|acntd_default|acntd                                                        |
+------+-------------------+-------------+-------------------------------------------------------------+
|100000|95546              |98566        |[0.005 -> 99593, 0.02 -> 98859, 0.05 -> 98566, 0.1 -> 106476]|
+------+-------------------+-------------+-------------------------------------------------------------+
```

## High-performance distinct counts for analytics

The following example shows how to use the pre-aggregate -> re-aggregate -> finalize pattern for high-performance distinct counting. We will calculate approximate distinct counts for odd vs. even (modulo 2) IDs in three steps:

1. Pre-aggregate the data modulo 10 using `hll_init_agg()`.
2. Re-aggregate modulo 2 using `hll_merge()`.
3. Produce a final result using `hll_cardinality()`.

In a product environment, the pre-aggregates from step (1) and, in the case of very large data, re-aggregations at various granularities will be computed and persisted so that final reports can be created without having to look at the rows of data from step (1).

### SparkSQL

```sql
with
-- pre-aggregates contain a binary HLL sketch column
pre_aggregate as (
  select
    id % 10 as id_mod10,
    hll_init_agg(id) as hll_id
  from ids
  group by id % 10
),
-- HLL sketch columns can be re-aggregated using hll_merge() just like counts can be re-aggregated with sum().
-- Note that you cannot combine distinct counts with sum(); this is where HLL sketches shine.
aggregate as (
  select
    id_mod10 % 2 as id_mod2,
    hll_merge(hll_id) as hll_id
  from pre_aggregate
  group by id_mod10 % 2
)
-- When a final result has to be produced, use hll_cardinality() on the HLL sketches
select
  id_mod2,
  hll_cardinality(hll_id) as acntd
from aggregate
order by id_mod2
```

### Scala

```scala
spark.range(100000)
  // pre-aggregate
  .groupBy(('id % 10).as("id_mod10"))
  .agg(hll_init_agg('id).as("hll_id"))
  // reaggregate
  .groupBy(('id_mod10 % 2).as("id_mod2"))
  .agg(hll_merge('hll_id).as("hll_id"))
  // final report
  .select('id_mod2, hll_cardinality('hll_id).as("acntd"))
  .orderBy('id_mod2)
  .show(false)
```

### Output

```
+-------+-----+
|id_mod2|acntd|
+-------+-----+
|0      |47305|
|1      |53156|
+-------+-----+
```

## Approximate counting of collection elements

Let's change the previous example such that, instead of grouping the ID modulo 10 into HLL sketches, we collect them in arrays.

### SparkSQL

```sql
with
-- Rather than grouping the modulo 10 IDs into binary sketches, collect them in arrays
grouped as (
  select
    id % 10 as id_mod10,
    collect_list(id) as ids
  from ids
  group by id % 10
),
-- For aggregation, use hll_init_collection_agg() to create HLL sketches
aggregate as (
  select
    id_mod10 % 2 as id_mod2,
    hll_init_collection_agg(ids) as hll_id
  from grouped
  group by id_mod10 % 2
)
-- When a final result has to be produced, use hll_cardinality() as before
select
  id_mod2,
  hll_cardinality(hll_id) as acntd
from aggregate
order by id_mod2
```

### Scala

```scala
import org.apache.spark.sql.functions._
import com.swoop.alchemy.spark.expressions.hll.functions._

spark.range(100000)
  // group into arrays
  .groupBy(('id % 10).as("id_mod10"))
  .agg(collect_list('id).as("ids"))
  // aggregate
  .groupBy(('id_mod10 % 2).as("id_mod2"))
  .agg(hll_init_collection_agg('ids).as("hll_id"))
  // final report
  .select('id_mod2, hll_cardinality('hll_id).as("acntd"))
  .orderBy('id_mod2)
  .show(false)
```

### Output

```
+-------+-----+
|id_mod2|acntd|
+-------+-----+
|0      |47305|
|1      |53156|
+-------+-----+
```

## Binding HLL sketch precision

Imagine yourself having to build HLL sketches for various columns using the same precision. Now imagine yourself having to do this over and over in many different cells in a notebook. Wouldn't it be nice to not have to keep typing the precision when you use `hll_init_*` in your transformations? Some of us at [Swoop](https://www.swoop.com/) thought so and we added `BoundHLL` just for this purpose. In the following slightly longer example we will compute approximate distinct counts for odd vs. even IDs using different precisions.

```scala
import org.apache.spark.sql.Dataset
import org.apache.spark.sql.functions._
import com.swoop.alchemy.spark.expressions.hll.functions._
import com.swoop.alchemy.spark.expressions.hll.BoundHLL

def preAggregateIds(error: Double)(ds: Dataset[_]) = {
  val hll = BoundHLL(error)
  import hll._ // imports hll_init_* versions bound to error
  ds.toDF("id")
    .groupBy(('id % 2).as("id_mod"))
    .agg(hll_init_agg('id).as("hll_id"))
    .withColumn("error", lit(error))
}

val ids = spark.range(100000)

Seq(0.005, 0.01, 0.02, 0.05, 0.1)
  .map(error => ids.transform(preAggregateIds(error)))
  .reduce(_ union _)
  .groupBy('error).pivot('id_mod)
  .agg(hll_cardinality(hll_merge('hll_id)).as("acntd"))
  .orderBy('error)
  .show(false)
+-----+-----+-----+
|error|0    |1    |
+-----+-----+-----+
|0.005|49739|49908|
|0.01 |49740|49662|
|0.02 |51024|49712|
|0.05 |47305|53156|
|0.1  |52324|56113|
+-----+-----+-----+
```

You can use the `import hll._` pattern in a notebook cell to bind all `hll_init_*()` functions in all notebook cells to the precision provided to `BoundHLL` but you have to keep two things in mind that another import, e.g., `com.swoop.alchemy.spark.expressions.hll.functions._` can override the bound import. Also, if you attach the notebook to a new Spark/REPL session, you have to re-run the import. For that reason, we typically recommend that notebook-level precision binding happens in a single cell, e.g.,

```scala
import com.swoop.alchemy.spark.expressions.hll.functions._
import com.swoop.alchemy.spark.expressions.hll.BoundHLL

val hll = BoundHLL(0.02) // Spark default is 0.05
import hll._
```

When you bind the precision of `hll_init_*()`, attempting to use a version with an explicit precision will generate a compiler error. This is by design. To keep things consistent, we also bind the precision of Spark's own `approx_count_distinct()`.

## The space cost of precision

HLL sketch size depends on the desired precision and is independent of data size. A simple rule of thumb is that a 2x increase in HLL cardinality estimation precision requires a 4x increase in the size of HLL sketches.

```scala
spark.range(100000)
  .select(map(
    Seq(0.005, 0.01, 0.02, 0.03, 0.04, 0.05, 0.06, 0.07, 0.08, 0.09, 0.1).flatMap { error =>
      lit(error) :: length(hll_init_agg('id, error)) :: Nil
    }: _*
  ).as("lengths"))
  .select(explode('lengths).as("error" :: "sketch_size_in_bytes" :: Nil))
  .show(false)
+-----+--------------------+
|error|sketch_size_in_bytes|
+-----+--------------------+
|0.005|43702               |
|0.01 |10933               |
|0.02 |2741                |
|0.03 |1377                |
|0.04 |693                 |
|0.05 |353                 |
|0.06 |353                 |
|0.07 |181                 |
|0.08 |181                 |
|0.09 |181                 |
|0.1  |96                  |
+-----+--------------------+
```

# [UDFs vs Map vs Custom Spark-Native Functions](https://medium.com/@Farox2q/udfs-vs-map-vs-custom-spark-native-functions-91ab2c154b44)

## Introduction

Apache Spark provides a lot of functions out-of-the-box. However, as with any other language, there are still times when you’ll find a particular functionality is missing. It’s at this point that you would often look to implement your own function. In this article, we’ll be demonstrating and comparing 3 methods for implementing your own functions in Spark, namely:

1. User Defined Functions
2. Map functions
3. Custom Spark-native functions

By the end of this article, we hope to give the reader clear programming recommendations specifically as they relate to implementing custom functions in Spark.

Readers short on time may skip straight to the conclusion. For those interested in the journey, read on.

## Setup

We’ll start by setting up our environment for conducting experiments.

We’ll be using Spark version 2.4.0 and Scala version 2.11.8 although any recent (2.0+) Spark distribution will do. We’ll also load [Vegas](https://github.com/vegas-viz/Vegas), a visualization library for Scala/Spark, which will be useful closer to the end of the article.

```bash
spark-shell --packages="org.vegas-viz:vegas_2.11:0.3.11,org.vegas-viz:vegas-spark_2.11:0.3.11"
```

Next, we’ll generate a Dataset for testing our code against. The Dataset will be called `testDf`and will consist of exactly 1 column (named `id`by default). We’ll make this a fairly large Dataset of 10 million rows to simulate real-life conditions and we’ll also cache it in memory so we don’t have to constantly regenerate it from scratch.

```scala
scala> val testDf = spark.range(10000000).toDF.cache
testDf: org.apache.spark.sql.Dataset[org.apache.spark.sql.Row] = [id: bigint]
scala> testDf.count
res0: Long = 10000000
```

Finally, we need to write a function that will return us an accurate measurement of execution time for a given block of code. There’s typically a lot of activity happening under the hood of your OS which is likely to affect execution times. To overcome this, we will execute a given block of code multiple times and calculate the average execution time. This should give us relatively accurate results for our purposes.

```scala
scala> def averageTime[R](block: => R, numIter: Int = 10): Unit = {
     | val t0 = System.nanoTime()
     | (1 to numIter).foreach( _ => block)  // 可参考
     | val t1 = System.nanoTime()
     | val averageTimeTaken = (t1 - t0) / numIter
     | val timeTakenMs = averageTimeTaken / 1000000
     | println("Elapsed time: " + timeTakenMs + "ms")
     | }
averageTime: [R](block: => R, numIter: Int)Unit
```

That’s it for the basic setup, we’re ready to dive into some experiments.

## Spark-Native Functions (Baseline)

To keep our code examples simple, we’ll be implementing a very basic function that increments the value of whatever argument is passed to it by 1. So for example, if we give our function the argument 1, it should return 2.

Before writing any custom functions, we first want to demonstrate how you would normally do this in Spark in order to establish a baseline for comparisons later:

```scala
scala> val resultSparkNative = testDf.withColumn(“addOne”, ‘id + 1)
resultSparkNative: org.apache.spark.sql.DataFrame = [id: bigint, addOne: bigint]
scala> resultSparkNative.show(5)
+---+------+
| id|addOne|
+---+------+
|  0|     1|
|  1|     2|
|  2|     3|
|  3|     4|
|  4|     5|
+---+------+
only showing top 5 rows
```

Nothing complicated there, just standard Spark native column functions. Note this is the same as:

```scala
scala> val resultSparkNative = testDf.withColumn(“addOne”, ‘id.plus(1))
resultSparkNative: org.apache.spark.sql.DataFrame = [id: bigint, addOne: bigint]
scala> resultSparkNative.show(5)
+---+------+
| id|addOne|
+---+------+
|  0|     1|
|  1|     2|
|  2|     3|
|  3|     4|
|  4|     5|
+---+------+
only showing top 5 rows
```

Under the hood, both `+` and `plus` call the `Add` class (which is defined [here](https://github.com/apache/spark/blob/114d0de14c441f06d98ab1bcf6c8375c58ecd9ab/sql/catalyst/src/main/scala/org/apache/spark/sql/catalyst/expressions/arithmetic.scala#L157)).

Now let’s take a quick look at the physical plan Spark generates for this operation:

```bash
scala> resultSparkNative.explain 
== Physical Plan ==
*(1) Project [id#0L, (id#0L + 1) AS addOne#34L]
+- *(1) InMemoryTableScan [id#0L]
      +- InMemoryRelation [id#0L], StorageLevel(disk, memory, deserialized, 1 replicas)
            +- *(1) Range (0, 10000000, step=1, splits=4)
```

Don’t be alarmed if you’ve never seen this kind of output before. A physical plan just describes the way in which Spark intends to execute your computation. In the example above, `InMemoryRelation` and `InMemoryTableScan` indicate that Spark will look in our memory to read the data that was cached. After this, Spark will perform a `project`which selects the columns we want. It’s during the `project` that Spark will actually calculate our new (addOne) column. That’s about it; this is after all a very simple query. Later on, we’ll be comparing the Physical Plans generated by our custom functions with this one to see if there are any differences. These differences may in turn help to explain performance variations, if any.

Let’s see how fast Spark’s native `plus` function is using the `averageTime` function we defined earlier:

```scala
scala> averageTime { resultSparkNative.count }
Elapsed time: 36ms
```

Nice, Spark was able to process 10 million records in less than 36 milliseconds (on average). This query will be our baseline. Ideally, we’ll want to get similar performance with our custom functions, if not better.

Now let’s pretend that Spark doesn’t actually have any native functions for incrementing values in a column by 1. What are our options then?

## User Defined Functions

The first thing people will usually try at this point is a UDF (User Defined Function). UDFs allow developers to use regular Scala functions in their Spark queries. Here’s a quick implementation of our desired UDF:

```scala
scala> import org.apache.spark.sql.functions.udf
import org.apache.spark.sql.functions.udf

scala> val addOneUdf = udf { x: Long => x + 1 }
addOneUdf: org.apache.spark.sql.expressions.UserDefinedFunction = UserDefinedFunction(<function1>,LongType,Some(List(LongType)))
```

With just two lines of code, we were able to implement our custom function. This is one of the advantages of UDFs; they’re straight-forward to implement.

Let’s go ahead and check that our UDF does what we expect:

```scala
scala> val resultUdf = testDf.withColumn("addOne", addOneUdf('id))
resultUdf: org.apache.spark.sql.DataFrame = [id: bigint, addOne: bigint]
scala> resultUdf.show(5)
+---+------+
| id|addOne|
+---+------+
|  0|     1|
|  1|     2|
|  2|     3|
|  3|     4|
|  4|     5|
+---+------+
only showing top 5 rows
```

Looks like we were successful; the results are equivalent to what we got with Spark’s native `plus` function.

Let’s have a look at the physical plan Spark generates when we use a UDF in our query:

```bash
scala> resultUdf.explain
== Physical Plan ==
*(1) Project [id#0L, UDF(id#0L) AS addOne#184L]
+- *(1) InMemoryTableScan [id#0L]
      +- InMemoryRelation [id#0L], StorageLevel(disk, memory, deserialized, 1 replicas)
            +- *(1) Range (0, 10000000, step=1, splits=4)
```

For the most part it looks pretty similar to what we saw previously. We have the `InMemoryRelation` and `InMemoryTableScan` same as before. The only real difference is during the `project` where we’re now calling our UDF instead of Spark’s native `plus `function.

Based on this and assuming our implementation of the UDF is fairly efficient, we should expect the performance of our query to be similar to our baseline query:

```bash
scala> averageTime { resultUdf.count }
Elapsed time: 40ms
```

Just as we guessed, this query and our baseline query are equivalent as far as execution time goes (a difference of a few milliseconds is inconsequential and could be the result of any number of factors e.g. music playing in the background). That’s great right?

Unfortunately, UDFs are a black-box from Spark’s perspective. All Spark knows about our UDF is that it takes `id` as its argument and it will return some value which is assigned to `addOne`. As a result, Spark can’t apply many of the optimizations it normally would if we were to use just native column functions (at least not today).

In our simple `count` query above, there weren’t any optimizations that Spark could have made so we saw no performance degradation compared to our baseline query. Performance degradations will be more apparent in cases where the black-box nature of a UDF really gets in the way of Spark’s optimization rules e.g. predicate push down. **To demonstrate this, we’ll first create a parquet file containing our test data:**

```scala
scala> val path = "temp.parquet/"
path: String = temp.parquet/

scala> testDf.write.mode("overwrite").parquet(path)
                                                                               
scala> val testDfParquet = spark.read.parquet(path)
testDfParquet: org.apache.spark.sql.DataFrame = [id: bigint]
```

Now we’ll show the difference in the plan generated by running a filter operation against this Parquet file using Spark’s native `plus ` functions and our UDF:

```scala
scala> val resultSparkNativeFilterParquet = testDfParquet.filter('id.plus(1) === 2)
resultSparkNativeFilterParquet: org.apache.spark.sql.Dataset[org.apache.spark.sql.Row] = [id: bigint]

scala> val resultUdfFilterParquet = testDfParquet.filter(addOneUdf('id) === 2)
resultUdfFilterParquet: org.apache.spark.sql.Dataset[org.apache.spark.sql.Row] = [id: bigint]

scala> resultSparkNativeFilterParquet.explain
== Physical Plan ==
*(1) Project [id#324L]
+- *(1) Filter (isnotnull(id#324L) && ((id#324L + 1) = 2))
   +- *(1) FileScan parquet [id#324L] Batched: true, Format: Parquet, Location: InMemoryFileIndex[file:/home/temp.parquet], PartitionFilters: [], PushedFilters: [IsNotNull(id)], ReadSchema: struct<id:bigint>

scala> resultUdfFilterParquet.explain
== Physical Plan ==
*(1) Filter (if (isnull(id#324L)) null else UDF(id#324L) = 2)
+- *(1) FileScan parquet [id#324L] Batched: true, Format: Parquet, Location: InMemoryFileIndex[file:/home/temp.parquet], PartitionFilters: [], PushedFilters: [], ReadSchema: struct<id:bigint>
```

Notice how there is nothing in `PushedFilters` for the second query where we use our UDF. Spark was unable to push the `IsNotNull` filter into our parquet source. Instead, the second query will have to perform the `IsNotNull` filter in memory after loading all of the data.

Lets see how much of a difference this makes in practice:

```bash
scala> averageTime { resultSparkNativeFilterParquet.count }
Elapsed time: 114ms

scala> averageTime { resultUdfFilterParquet.count }
Elapsed time: 204ms
```

Both queries take longer than our previous queries because they first have to load the parquet data into memory. However, we see that the query using our UDF takes almost twice as long as our query using the native `plus `function because Spark was unable to push filters down into the data source.

We’ve established here that although UDFs are easy to implement, they aren’t that great performance wise as they get in the way of Spark’s optimization rules. What are our other options for implementing custom functions then?

## Map Functions

**The map function returns a new Dataset that contains the result of applying a given function to each row of a given Dataset**. For more information, see the official documentation [here](https://spark.apache.org/docs/latest/api/scala/index.html#org.apache.spark.sql.Dataset@map[U](func:T=>U)(implicitevidence$6:org.apache.spark.sql.Encoder[U]):org.apache.spark.sql.Dataset[U]). Surprisingly, this is still considered an experimental feature although it has been available since Spark 1.6.0.

Here’s how you would use the map function to perform a similar operation to our previous queries:

```scala
scala> case class foo(id: Long, addOne: Long)
defined class foo

scala> val resultMap = testDf.map { r => foo(r.getAs[Long](0), r.getAs[Long](0) + 1) }
resultMap: org.apache.spark.sql.Dataset[foo] = [id: bigint, addOne: bigint]
scala> resultMap.show(5)
+---+------+
| id|addOne|
+---+------+
|  0|     1|
|  1|     2|
|  2|     3|
|  3|     4|
|  4|     5|
+---+------+
only showing top 5 rows
```

Not quite as simple as a UDF perhaps but not terribly complicated either and still only 2 lines of code. Now let’s take a peek at the physical plan Spark generates to execute this query:

```bash
scala> resultMap.explain
== Physical Plan ==
*(1) SerializeFromObject [assertnotnull(input[0, $line60.$read$$iw$$iw$foo, true]).id AS id#894L, assertnotnull(input[0, $line60.$read$$iw$$iw$foo, true]).addOne AS addOne#895L]
+- *(1) MapElements <function1>, obj#893: $line60.$read$$iw$$iw$foo
   +- *(1) DeserializeToObject createexternalrow(id#0L, StructField(id,LongType,false)), obj#892: org.apache.spark.sql.Row
      +- *(1) InMemoryTableScan [id#0L]
            +- InMemoryRelation [id#0L], StorageLevel(disk, memory, deserialized, 1 replicas)
                  +- *(1) Range (0, 10000000, step=1, splits=4)
```

Yikes! That’s quite different. Just looking at the number of steps involved in this physical plan, we can already guess that this is going to be slower than our baseline query. Another thing that could slow us down are the `deserialization` and `serialization` steps which are required for each row just to create our `addOne` column using the map function. Let’s see how much these factors impact performance in practice:

```bash
scala> averageTime { resultMap.count }
Elapsed time: 198ms
```

Wow, the map based query is almost 5x slower than its counterparts against the same cached Dataset.

While we’re here, we may as well check to see if Spark is able to add the predicate push down optimization against the parquet data we generated earlier:

```scala
scala> val resultMapFilterParquet = testDfParquet.map { r => foo(r.getAs[Long](0), r.getAs[Long](0) + 1) }.filter('addOne === 2)
resultMapFilterParquet: org.apache.spark.sql.Dataset[foo] = [id: bigint, addOne: bigint]

scala> resultMapFilterParquet.explain
== Physical Plan ==
*(1) Filter (addOne#54160L = 2)
+- *(1) SerializeFromObject [assertnotnull(input[0, $line36.$read$$iw$$iw$foo, true]).id AS id#54159L, assertnotnull(input[0, $line36.$read$$iw$$iw$foo, true]).addOne AS addOne#54160L]
   +- *(1) MapElements <function1>, obj#54158: $line36.$read$$iw$$iw$foo
      +- *(1) DeserializeToObject createexternalrow(id#324L, StructField(id,LongType,true)), obj#54157: org.apache.spark.sql.Row
         +- *(1) FileScan parquet [id#324L] Batched: true, Format: Parquet, Location: InMemoryFileIndex[file:/home/temp.parquet], PartitionFilters: [], 
PushedFilters: [], ReadSchema: struct<id:bigint>
```

Apparently not.

At this point, it looks like UDFs have the upper hand over Map functions in terms of performance when writing custom functions and are also a little simpler to write. However, Spark’s native column functions trump both these options easily in terms of engineering ease and raw performance. What we need is a way to write our own Spark native functions.

> 显然不是。
>
> 在这一点上，在编写自定义函数时，从性能上看，UDF似乎具有优于映射函数的优势，而且编写起来也稍微简单一些。但是，Spark的==原生列功能==在工程简便性和原始性能方面轻松胜过这两个选项。 我们需要的是一种编写我们自己的Spark原生函数的方法。

## Custom Spark-Native Functions

It is possible to extend the Apache Spark project to include our own custom column functions. To do so however there is a bit of setup required. First, we download a local copy of the Apache Spark project from GitHub and extract it to our home directory.

> 可以扩展Apache Spark项目以包含我们自定义的==**列函数**==。 但要做到这一点，需要进行一些设置。 首先，从GitHub下载Apache Spark的源码到本地，并将其解压缩到的主目录。

```bash
cd ~
wget https://github.com/apache/spark/archive/v2.4.0-rc5.tar.gztar -zxvf v2.4.0-rc5.tar.gz
```

> At this point, readers are advised to explore how the Apache Spark project is organized, specifically the SQL module. We won’t be covering this topic in any detail here and instead refer you to this excellent [article](https://blog.insightdatascience.com/a-journey-through-spark-5d67b4af4b24) for more information.

**Now we can start creating the files required to implement our own Spark-native function.** We can take inspiration for our desired implementation from any of the existing native functions in the `org.apache.spark.sql.catalyst.expressions` package e.g. the `UnaryMinus` class from the `Arithmetic.scala` file. Since we only need to pass in one argument, we can make use of the `UnaryExpression` abstract class that is already defined for us in the `org.apache.spark.sql.catalyst.expressions` package. We will also mix in the `NullIntolerant` trait as this is one of the things Catalyst looks out for when trying to push predicates (see the `QueryPlanConstraints.scala` file inside of the `org.apache.spark.sql.catalyst.plans.logical `package for more details). To keep things simple, we’ll be supporting only `LongType`arguments but this could easily be extended with appropriate code in the future. We can optionally give it a `prettyName`. Lastly, we define the `doGenCode` method. This is the part that actually performs our calculation and is essentially used to generate Java code at run-time. Below is what a basic working implementation looks like for our purposes:

> 现在开始创建**Spark-native函数**，可以从`org.apache.spark.sql.catalyst.expressions`包现有的代码中，获取实现**native函数**的灵感，如类`UnaryMinus`，位于**Arithmetic.scala**中。
>
> 1. 因为只需要一个参数，所以使用在`org.apache.spark.sql.catalyst.expressions`包中定义的抽象类`UnaryExpression`。
> 2. 我们还将混入`NullIntolerant`特质，==因为这是**Catalyst**在尝试下推谓词时会注意的事情之一==（有关详细信息，请参阅`org.apache.spark.sql.catalyst.plans.logical` 包中的源文件`QueryPlanConstraints.scala`）。 
> 3. 简单起见，仅支持`LongType`参数，但将来可以使用适当的代码轻松扩展。
> 4. 给它定义了一个漂亮的名字（`prettyName`）。
> 5. 最后，定义`doGenCode`方法。这是实际执行计算的部分，主要用于在运行时生成Java代码。
>
> 以下是达到我们目标的最基本的实现：

```scala
package org.apache.spark.sql.catalyst.expressions
import org.apache.spark.sql.catalyst.expressions.codegen.{CodegenContext, ExprCode}
import org.apache.spark.sql.types.{DataType, LongType}

case class Add_One_Custom_Native(child: Expression) 
extends UnaryExpression with NullIntolerant {
  override def dataType: DataType = LongType
  override def prettyName: String = "addOneSparkNative"
  override def doGenCode(ctx: CodegenContext, ev: ExprCode): ExprCode = defineCodeGen(ctx, ev, c => s"$c + 1")
}
```

Next we create another object, this time inside the `org.apache.spark.sql` package mimicking the contents of the `functions.scala` file. We define our new column function in here, calling the class we produced in the previous step. The only feature we’ve added here is the ability to operate on and return `Column` objects (as opposed to `Expression` objects).

```scala
package org.apache.spark.sqlimport org.apache.spark.sql.catalyst.expressions._

object CustomFunctions {
  private def withExpr(expr: Expression): Column = Column(expr)
  def addOneCustomNative(x: Column): Column = withExpr {
    Add_One_Custom_Native(x.expr)
  }
}
```

After this, we can build our own Spark distribution using the helpful instructions provided on the official Spark website [here](https://spark.apache.org/docs/latest/building-spark.html#building-a-runnable-distribution).

```bash
cd spark-2.4.0-rc5
./dev/make-distribution.sh --name custom-spark --tgz
tar -zxvf spark-2.4.0-bin-custom-spark.tgz
```

Once this is complete, we can launch a new `spark-shell` session using our custom distribution.

```bash
export SPARK_HOME=~/spark-2.4.0-rc5/spark-2.4.0-bin-custom-spark
export PATH=$SPARK_HOME/bin:$PATH
spark-shell --packages="org.vegas-viz:vegas_2.11:0.3.11,org.vegas-viz:vegas-spark_2.11:0.3.11"
```

To make use of our new function, all we need to do now is import it. After that, its back to regular old Spark:

```scala
scala> import org.apache.spark.sql.CustomFunctions.addOneCustomNative
import org.apache.spark.sql.CustomFunctions.addOneCustomNative

scala> val resultCustomNative = testDf.withColumn("addOne", addOneCustomNative('id))
resultCustomNative: org.apache.spark.sql.DataFrame = [id: bigint, addOne: bigint]

scala> resultCustomNative.show(5)
+---+------+
| id|addOne|
+---+------+
|  0|     1|
|  1|     2|
|  2|     3|
|  3|     4|
|  4|     5|
+---+------+
only showing top 5 rows
```

Cool, so our custom Spark-native function is working as expected, now let’s see the physical plan that was generated.

```bash
scala> resultCustomNative.explain
== Physical Plan ==
*(1) Project [id#0L, addOneSparkNative(id#0L) AS addOne#926L]
+- *(1) InMemoryTableScan [id#0L]
      +- InMemoryRelation [id#0L], StorageLevel(disk, memory, deserialized, 1 replicas)
            +- *(1) Range (0, 10000000, step=1, splits=4)
```

Its a simple plan much like the one we saw with our baseline query. We should therefore expect to see similar performance:

```
scala> averageTime { resultCustomNative.count }
Elapsed time: 40ms
```

Indeed, this is comparable to the execution time we saw using Spark’s native `plus` function.

Okay but can Spark apply its usual optimizations when we read from a parquet data source?

```scala
scala> val resultCustomNativeFilterParquet = testDfParquet.filter(addOneCustomNative('id) === 2)
resultCustomNativeFilterParquet: org.apache.spark.sql.Dataset[org.apache.spark.sql.Row] = [id: bigint]

scala> resultCustomNativeFilterParquet.explain
== Physical Plan ==
*(1) Project [id#324L]
+- *(1) Filter (isnotnull(id#324L) && (addOneSparkNative(id#324L) = 2))
   +- *(1) FileScan parquet [id#324L] Batched: true, Format: Parquet, Location: InMemoryFileIndex[file:/home/temp.parquet], PartitionFilters: [], PushedFilters: [IsNotNull(id)], ReadSchema: struct<id:bigint>
```

Success! We were able to push the same `IsNotNull` filter down into our parquet source just like in our baseline query. Let’s do a quick check of execution time:

```
scala> averageTime { resultCustomNativeFilterParquet.count }
Elapsed time: 116ms
```

So we finally have a method for writing custom functions that can achieve the same level of performance as Spark-native functions. However, engineering effort-wise this method is by far the most intense and definitely has an initial learning curve to it. The fact that we have to go back to Java for the `doGenCode` portion of the implementation can also be daunting for those unfamiliar with the language.

>最终，我们有了一种编写定制函数的方法，它和spark本机函数具有相同的性能级别。然而，从工程的角度来看，这种方法是迄今为止最为复杂的，而且绝对有一个初步的学习曲线。对于那些不熟悉语言的人来说，我们必须返回Java来实现`doGenCode`部分，这也是令人畏惧的。

## A closer look at performance

Thus far, we’ve assessed performance by looking at the average execution time across 10 runs of a given block of code. While this is useful, we lose valuable information by looking at just the average. In this section, we’ll take a closer look at the distribution of execution times for each method.

We’ll start by defining a few utility functions:

```scala
scala> import vegas._
import vegas._

scala> import vegas.render.WindowRenderer._
import vegas.render.WindowRenderer._

scala> import vegas.sparkExt._
import vegas.sparkExt._

scala> import org.apache.spark.sql.Dataset
import org.apache.spark.sql.Dataset

scala> def time[R](block: => R) = {
     | val t0 = System.nanoTime()
     | block
     | val t1 = System.nanoTime()
     | val timeTakenMs = (t1 - t0) / 1000000
     | timeTakenMs
     | }
time: [R](block: => R)Long

scala> case class ResultsSchema(method: String, iteration: Int, timeTaken: Long)
defined class ResultsSchema

scala> def genResults(methods: Map[String, org.apache.spark.sql.Dataset[_ >: foo with org.apache.spark.sql.Row <: Serializable]]) = {
     |   methods.keys.map { method => { 
     |   (1 to 1000).map { iter => 
     |           val timeTaken = time { methods.get(method).get.count } 
     |           ResultsSchema(method, iter, timeTaken)
     |   }}}.flatten.toSeq.toDS
     | }
genResults: (methods: Map[String,org.apache.spark.sql.Dataset[_ >: foo with org.apache.spark.sql.Row <: Serializable]])org.apache.spark.sql.Dataset[ResultsSchema]

scala> def genSummary(ds: Dataset[ResultsSchema]) = {
     |   ds
     |   .groupBy('method)
     |   .agg(
     |           min('timeTaken) as "min",
     |           round(mean('timeTaken), 1) as "mean",
     |           max('timeTaken) as "max", 
     |           round(stddev('timeTaken), 1) as "std", 
     |           count("*") as "sampleSize")
     |   .orderBy('method)
     | }
genSummary: (ds: org.apache.spark.sql.Dataset[ResultsSchema])org.apache.spark.sql.Dataset[org.apache.spark.sql.Row]

scala> def genGraph(ds: Dataset[ResultsSchema]) = {
     |   Vegas("bar chart", width=1000, height=200).
     |   withDataFrame(ds.withColumn("count", lit(1))).
     |   filter("datum.timeTaken < 500").
     |   mark(Bar).
     |   encodeX("timeTaken", Quantitative, scale=Scale(bandSize=50), axis=Axis(title="Time Taken (ms)")).
     |   encodeY("count", Quantitative, aggregate=AggOps.Count, axis=Axis(title="Count")).
     |   encodeColumn("method", title="Method").
     |   configCell(width = 200, height = 200)
     | }
genGraph: (ds: org.apache.spark.sql.Dataset[ResultsSchema])vegas.DSL.ExtendedUnitSpecBuilder
```

Now we can simulate running 1000 queries using each of the 4 methods we’ve seen thus far for incrementing values by 1 and look at the distribution of execution times for any additional insights on performance. We’ll use the cached Dataset for this experiment.

```scala
scala> val methodsCacheCount = Map(
     | "Native" -> resultSparkNative, 
     | "UDF" -> resultUdf, 
     | "Map" -> resultMap, 
     | "Custom Native" -> resultCustomNative
     | )
methodsCacheCount: scala.collection.immutable.Map[String,org.apache.spark.sql.Dataset[_ >: foo with org.apache.spark.sql.Row <: Serializable]] = Map(Native -> [id: bigint, addOne: bigint], UDF -> [id: bigint, addOne: bigint], Map -> [id: bigint, addOne: bigint], Custom Native -> [id: bigint, addOne: bigint])

scala> val resultsCacheCount = genResults(methodsCacheCount)
resultsCacheCount: org.apache.spark.sql.Dataset[ResultsSchema] = [method: string, iteration: int ... 1 more field]

scala> genSummary(resultsCacheCount).show
+-------------+---+-----+---+----+----------+
|       method|min| mean|max| std|sampleSize|
+-------------+---+-----+---+----+----------+
|Custom Native| 30| 33.4| 64| 3.3|      1000|
|          Map|153|198.8|632|69.5|      1000|
|       Native| 31| 36.8| 59| 4.4|      1000|
|          UDF| 31| 34.5| 53| 3.3|      1000|
+-------------+---+-----+---+----+----------+

scala> genGraph(resultsCacheCount).show
```

![resultsCacheCount](Advanced Analytics with HyperLogLog Functions in Apache Spark/resultsCacheCount.png)

It’s interesting to note how distributed the counts are in our queries using the Map function, taking anywhere between 153 milliseconds and 632 milliseconds. The remaining methods show very consistent times around the 35 milliseconds mark.

Now let’s try the same thing except using filter operations against the parquet data source:

```scala
scala> val methodsParquetFilter = Map(
     | "Native" -> resultSparkNativeFilterParquet, 
     | "UDF" -> resultUdfFilterParquet, 
     | "Map" -> resultMapFilterParquet, 
     | "Custom Native" -> resultCustomNativeFilterParquet
     | )
methodsParquetFilter: scala.collection.immutable.Map[String,org.apache.spark.sql.Dataset[_ >: foo with org.apache.spark.sql.Row <: Serializable]] = Map(Native -> [id: bigint], UDF -> [id: bigint], Map -> [id: bigint, addOne: bigint], Custom Native -> [id: bigint])

scala> val resultsParquetFilter = genResults(methodsParquetFilter)
resultsParquetFilter: org.apache.spark.sql.Dataset[ResultsSchema] = [method: string, iteration: int ... 1 more field]

scala> genSummary(resultsParquetFilter).show
+-------------+---+-----+---+----+----------+
|       method|min| mean|max| std|sampleSize|
+-------------+---+-----+---+----+----------+
|Custom Native| 84| 99.0|321|12.5|      1000|
|          Map|196|240.7|517|73.0|      1000|
|       Native| 84|106.2|236|12.5|      1000|
|          UDF|159|199.5|715|71.9|      1000|
+-------------+---+-----+---+----+----------+

scala> genGraph(resultsParquetFilter).show
```

![resultsParquetFilter](Advanced Analytics with HyperLogLog Functions in Apache Spark/resultsParquetFilter.png)

These distributions are more interesting. Surprisingly, we see our Custom Native function actually does better than Spark’s Native function sometimes. This may be because of our simple implementation. On the other hand, both the UDF and Map based queries take almost twice as long as they’re unable to take advantage of Spark’s optimizations. Finally, its interesting to note the two humps in the UDF and Map based queries although sadly, we can offer no reasonable explanation for this phenomenon at this stage.

## Conclusion

Alright, we’ve covered a lot of ground in this article. To bring together everything we’ve learned so far, we decided to rank each method on the basis of engineering ease and performance using a 3 point scale (where 1 is good and 3 is bad). Below are our results:

```bash
+----------------+------------------+-------------+---------+
|     Method     | Engineering Ease | Performance | Overall |
+----------------+------------------+-------------+---------+
| Native         |                1 |           1 |       1 |
| UDF            |                2 |           3 |     2.5 |
| Map            |                2 |           3 |     2.5 |
| Custom Native  |                3 |           1 |       2 |
+----------------+------------------+-------------+---------+
* Overall = (Engineering Ease + Performance) / 2
```

Based on this, we can make several recommendations. Firstly, developers should always try to use Spark-native functions over all else. If that’s not possible, developers are advised to try extending Spark’s native functions library. If there is a need to put together something quickly, UDFs are appropriate although these are not recommended for jobs running in production pipelines. Map based functions should be generally avoided.

Now for the caveats. Although switching from UDFs to native functions will generally improve performance, it may not always be the most effective way of improving overall job performance. It depends on where the bottlenecks are in your job. Spark’s Web UI is usually a good place to start investigating these kinds of questions.

Finally, its important to recognize that the Apache Spark project is constantly improving . Spark may not be able to optimize around UDFs today but that’s not to say that it won’t be able to in the future. At that point, it would be wise to reconsider the recommendations presented in this article.

All of the code used in this article can be found [here](https://github.com/fqaiser94/UdfVsMapVsNative/blob/master/UDFvsMapvsNative.scala).

> 基于此，我们可以提出几点建议。首先，开发人员应该总是尝试使用**Spark-native函数**。如果这不可能，建议开发人员尝试扩展**Spark-native函数库**。如果有需要，UDF适合快速组合某些内容，但不建议将这些内容用于生产环境中运行的作业。通常应避免使用基于`Map`的功能。
>
> 现在请注意。尽管从UDF切换到**native函数**通常可以提高性能，但它可能并不总是==提高整体工作性能==的最有效方法。这取决于**Spark Job**的瓶颈具体在哪里。 Spark的Web UI通常是开始调查这些问题的好地方。
>
> 最后，重要的是要认识到Apache Spark项目在不断改进。 Spark今天可能无法围绕UDF进行优化，但这并不是说它将来无法实现。那时，明智的是重新考虑本文中提出的建议。
>
>  本文中使用的所有代码都可以在[这里](https://github.com/fqaiser94/UdfVsMapVsNative/blob/master/UDFvsMapvsNative.scala)找到。

