# Spark Shuffle的技术演进

在Spark或Hadoop MapReduce的分布式计算框架中，数据被按照key分成一块一块的分区，打散分布在集群中各个节点的物理存储或内存空间中，每个计算任务一次处理一个分区，但map端和reduce端的计算任务并非按照一种方式对相同的分区进行计算，例如，当需要对数据进行排序时，就需要将key相同的数据分布到同一个分区中，原分区的数据需要被打乱重组，这个**按照一定的规则对数据重新分区的过程就是Shuffle（洗牌）**。

#### Spark Shuffle的两阶段

对于Spark来讲，一些Transformation或Action算子会让RDD产生宽依赖，即parent RDD中的每个Partition被child RDD中的多个Partition使用，这时便需要进行Shuffle，根据Record的key对parent RDD进行重新分区。如果对这些概念还有一些疑问，可以参考我的另一篇文章[《Spark基本概念快速入门》](http://www.jianshu.com/p/e41b18a7e202)。

以Shuffle为边界，Spark将一个Job划分为不同的Stage，这些Stage构成了一个大粒度的DAG。Spark的Shuffle分为Write和Read两个阶段，分属于两个不同的Stage，前者是Parent Stage的最后一步，后者是Child Stage的第一步。如下图所示:

![img](http://upload-images.jianshu.io/upload_images/35301-05f2e70588800a10.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)

**执行Shuffle的主体是Stage中的并发任务，这些任务分ShuffleMapTask和ResultTask两种，ShuffleMapTask要进行Shuffle，ResultTask负责返回计算结果，一个Job中只有最后的Stage采用ResultTask，其他的均为ShuffleMapTask。如果要按照map端和reduce端来分析的话，ShuffleMapTask可以即是map端任务，又是reduce端任务，==因为Spark中的Shuffle是可以串行的；ResultTask则只能充当reduce端任务的角色。==**

我把Spark Shuffle的流程简单抽象为以下几步以便于理解：

- Shuffle Write
  1. Map side combine (if needed)
  2. Write to local output file
- Shuffle Read
  1. Block fetch
  2. Reduce side combine
  3. Sort (if needed)

Write阶段发生于`ShuffleMapTask`对该Stage的最后一个RDD完成了map端的计算之后，首先会判断是否需要对计算结果进行聚合，然后将最终结果按照不同的reduce端进行区分，写入当前节点的本地磁盘。
Read阶段开始于reduce端的任务读取ShuffledRDD之时，首先通过远程或本地数据拉取获得Write阶段各个节点中属于当前任务的数据，根据数据的Key进行聚合，然后判断是否需要排序，最后生成新的RDD。

#### Spark Shuffle具体实现的演进

在具体的实现上，Shuffle经历了Hash、Sort、Tungsten-Sort三阶段：

- `Spark 0.8及以前` **Hash Based Shuffle**
  在Shuffle Write过程按照Hash的方式重组Partition的数据，不进行排序。每个map端的任务为每个reduce端的Task生成一个文件，通常会产生大量的文件（即对应为`M*R`个中间文件，其中M表示map端的Task个数，R表示reduce端的Task个数），伴随大量的随机磁盘IO操作与大量的内存开销。
  Shuffle Read过程如果有combiner操作，那么它会把拉到的数据保存在一个Spark封装的哈希表（AppendOnlyMap）中进行合并。
  在代码结构上：
  - org.apache.spark.storage.ShuffleBlockManager负责Shuffle Write
  - org.apache.spark.BlockStoreShuffleFetcher负责Shuffle Read
  - org.apache.spark.Aggregator负责combine，依赖于AppendOnlyMap
- `Spark 0.8.1` **为Hash Based Shuffle引入File Consolidation机制**
  通过文件合并，中间文件的生成方式修改为每个执行单位（一个Executor中的执行单位等于Core的个数除以每个Task所需的Core数）为每个reduce端的任务生成一个文件。最终可以将文件个数从`M*R`修改为`E*C/T*R`，其中，E表示Executor的个数，C表示每个Executor中可用Core的个数，T表示Task所分配的Core的个数。
  是否采用Consolidate机制，需要配置`spark.shuffle.consolidateFiles`参数
- `Spark 0.9` **引入ExternalAppendOnlyMap**
  在combine的时候，可以将数据spill到磁盘，然后通过堆排序merge（可以参考这篇[文章](https://github.com/JerryLead/SparkInternals/blob/master/markdown/4-shuffleDetails.md)，了解其具体实现）
- `Spark 1.1` **引入Sort Based Shuffle，但默认仍为Hash Based Shuffle**
  在Sort Based Shuffle的Shuffle Write阶段，map端的任务会按照Partition id以及key对记录进行排序。同时将全部结果写到一个数据文件中，同时生成一个索引文件，reduce端的Task可以通过该索引文件获取相关的数据。
  在代码结构上：
  - 从以前的`ShuffleBlockManager`中分离出`ShuffleManager`来专门管理Shuffle Writer和Shuffle Reader。两种Shuffle方式分别对应`HashShuffleManager`和`SortShuffleManager`，可通过`spark.shuffle.manager`参数配置。两种Shuffle方式有各自的**ShuffleWriter**：`HashShuffle`和`SortShuffleWriter`；但共用一个**ShuffleReader**，即`HashShuffleReader`。
  - `ExternalSorter`实现排序功能。可通过对`spark.shuffle.spill`参数配置，决定是否可以在排序时将临时数据Spill到磁盘。
- `Spark 1.2` **默认的Shuffle方式改为Sort Based Shuffle**
- `Spark 1.4` **引入Tungsten-Sort Based Shuffle**
  将数据记录用序列化的二进制方式存储，把排序转化成指针数组的排序，引入堆外内存空间和新的内存管理模型，这些技术决定了使用Tungsten-Sort要符合一些严格的限制，==比如Shuffle dependency不能带有aggregation、输出不能排序等。由于堆外内存的管理基于JDK Sun Unsafe API，故Tungsten-Sort Based Shuffle也被称为Unsafe Shuffle。==
  在代码层面：
  - 新增org.apache.spark.shuffle.unsafe.UnsafeShuffleManager
  - 新增org.apache.spark.shuffle.unsafe.UnsafeShuffleWriter(用java实现)
  - ShuffleReader复用HashShuffleReader
- `Spark 1.6` **Tungsten-sort并入Sort Based Shuffle**
  由SortShuffleManager自动判断选择最佳Shuffle方式，如果检测到满足Tungsten-sort条件会自动采用Tungsten-sort Based Shuffle，否则采用Sort Based Shuffle。在代码方面：
  - UnsafeShuffleManager[合并](https://github.com/apache/spark/commit/f6d06adf05afa9c5386dc2396c94e7a98730289f)到SortShuffleManager
  - HashShuffleReader 重命名为BlockStoreShuffleReader，Sort Based Shuffle和Hash Based Shuffle仍共用ShuffleReader。
- `Spark 2.0` **Hash Based Shuffle退出历史舞台**
  从此Spark只有Sort Based Shuffle。

#### Spark Shuffle源码结构

这里以最新的Spark 2.1为例简单介绍一下Spark Shuffle相关部分的代码结构

- Shuffle Write

  - ShuffleWriter的入口链路

    ```
    org.apache.spark.scheduler.ShuffleMapTask#runTask
      ---> org.apache.spark.shuffle.sort.SortShuffleManager#getWriter
          ---> org.apache.spark.shuffle.sort.SortShuffleWriter#write(如果是普通sort)
          ---> org.apache.spark.shuffle.sort.UnsafeShuffleWriter#write (如果是Tungsten-sort)
    ```

  - SortShuffleWriter的主要依赖

    ```
    org.apache.spark.util.collection.ExternalSorter 负责按照(partition id, key)排序，如果需要Map side combine，需要提供aggregator
      ---> org.apache.spark.util.collection.PartitionedAppendOnlyMap
    ```

  - UnsafeShuffleWriter的主要依赖

    ```
    org.apache.spark.shuffle.sort.ShuffleExternalSorter (Java实现)
    ```

- Shuffle Read

  - ShuffleReader的入口链路

    ```
    org.apache.spark.rdd.ShuffledRDD#compute
      ---> org.apache.spark.shuffle.sort.SortShuffleManager#getReader
          ---> org.apache.spark.shuffle.BlockStoreShuffleReader#read
    ```

  - ShuffleReader主要依赖

    ```
    org.apache.spark.Aggregator 负责combine
      ---> org.apache.spark.util.collection.ExternalAppendOnlyMap
    org.apache.spark.util.collection.ExternalSorter 取决于是否需要对最终结果进行排序
    ```

#### 参考资料及推荐阅读

1. Spark 1.0之前Hash Based Shuffle的原理
   - [Apache Spark的设计与实现——Shuffle 过程](https://github.com/JerryLead/SparkInternals/blob/master/markdown/4-shuffleDetails.md)
   - [《Spark大数据处理：技术、应用与性能优化》第4.6节Shuffle机制](https://book.douban.com/subject/26261153/)
2. Spark 1.1时Sort Based Shuffle的资料
   - [Spark Shuffle Comparison](https://github.com/hustnn/SparkShuffleComparison)
3. Spark 1.2之前两种Shuffle方式的分析和对比
   - [《Spark技术内幕：深入解析Spark内核架构于实现原理》第7章Shuffle模块详解](https://book.douban.com/subject/26649141/)
4. Spark 1.6之前三种Shuffle方式的分析和对比
   - [Spark Architecture: Shuffle](https://0x0fff.com/spark-architecture-shuffle/)
   - [深入理解Spark(三)：Spark Shuffle原理及相关调优](http://sharkdtu.com/posts/spark-shuffle.html)
5. Spark 1.6之前Sort Based Shuffle的源码和原理
   - [Spark Core源码解读（十）－shuffle write](http://www.jianshu.com/p/ac41682c5d16)
   - [Spark Core源码解读（十二）－shuffle read](http://www.jianshu.com/p/913db058962e)
   - [Spark Sort Based Shuffle内存分析](http://www.jianshu.com/p/c83bb237caa8)
   - [Shuffle的框架之框架演进与框架内核](http://www.webzmt.com/IT/1296308.shtml)
6. Spark 1.6之前Tungsten-sort Based Shuffle的原理
   - [Spark Tungsten-sort Based Shuffle 分析](http://www.jianshu.com/p/d328c96aebfd)
   - [探索Spark Tungsten的秘密](https://github.com/hustnn/TungstenSecret/tree/master)