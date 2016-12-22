# 记 HBase的一次事故调研

11月24日（2016年）早上，发现 HBase-Common 集群的 **`某个`** region 长时间处于 RIT（Region-In-Transition）状态，于是决定重启：

1. 首先尝试重启 Region 所在的 RS（Region Sever）之后，发现没有效果。
2. 接着尝试重启Master，Master重启后报错。由于是早上高峰期，马上Google，然后按搜索到的内容快速修复问题，重启成功。

11月28日早上发现HBase Common快不能用了，该集群所有RS的Compaction 队列都到3000+以上，很快我们就推断出Compaction 队列持续增长的原因**大概率**是 compaction 出现死循环了。怀疑和『修复24日Master不能重启的事』有关。

> Tips：
>  Region 处于RIT时，HBase 的负载平衡不会工作。因此，一旦有 region 长时间处于 RIT状态，都需要及时处理。



## 背景

Compaction 队列无限增长调研


今天（11月28日）早上发现HBase Common不能用了，该集群所有RS的Compaction 队列都到3000+以上的长度，下图是10.17.28.179的压缩队列：

![179的压缩队列](179-compactionqueue.PNG)

下载10.17.28.179的日志，**仔细观察**发现：

> <u>**2016-11-26 04:57:48,679**</u> INFO org.apache.hadoop.hbase.regionserver.HStore: **Starting compaction** of 9 file(s) in F of PMS_SF_DAILY_FC_SALES,\x01\x80\x00\x00\x00\x03D\xFA\x17\x80\x00\x01U\xD6,1476150303969.3c15c3c1bb8835a5a1082c54eb0a49e2. into tmpdir=hdfs://nameservice2/hbasedata/hbase-common/data/default/PMS_SF_DAILY_FC_SALES/3c15c3c1bb8835a5a1082c54eb0a49e2/.tmp, totalSize=203.7 M
>
> <u>**2016-11-26 14:44:08**</u>,223 INFO org.apache.hadoop.hbase.regionserver.HStore: **Completed major compaction** of 9 (all) file(s) in F of PMS_SF_DAILY_FC_SALES,\x01\x80\x00\x00\x00\x03D\xFA\x17\x80\x00\x01U\xD6,1476150303969.3c15c3c1bb8835a5a1082c54eb0a49e2. into c08e05d9c5c14f9ebeda4cdd697d6ba8(size=153.7 M), total size for store is 153.7 M. This selection was in queue for 0sec, and took **9hrs, 46mins, 19sec** to execute.

日志上的时间和监控数据能对上。看HBase代码可知，`CompactSplitThread`启动时会创建两个线程池分别用于major compaction和minor compaction，参数是 `hbase.regionserver.thread.compaction.large` 和 `hbase.regionserver.thread.compaction.small`，线上设置都是1，因此只要有**一个Compaction 任务不能完成，对应的compaction 队列就会不断增长**。显然这里是major compaction 的队列堵了。

这么长的时间肯定有问题，就在上面<u>2016-11-26 14:44:08,223 INFO org.apache.hadoop.hbase.regionserver.HStore: Completed major compaction</u> 的日志上面发现如下的异常：

> 2016-11-26 14:43:35,789 WARN org.apache.phoenix.coprocessor.UngroupedAggregateRegionObserver: Unable to collect stats for PMS_SF_DAILY_FC_SALES
> org.apache.hadoop.hbase.client.RetriesExhaustedWithDetailsException: Failed 1 action: ConnectionClosingException: 1 time, 
> ```java
> at org.apache.hadoop.hbase.client.AsyncProcess$BatchErrors.makeException(AsyncProcess.java:227)
> at org.apache.hadoop.hbase.client.AsyncProcess$BatchErrors.access$1700(AsyncProcess.java:207)
> at org.apache.hadoop.hbase.client.AsyncProcess.waitForAllPreviousOpsAndReset(AsyncProcess.java:1658)
> at org.apache.hadoop.hbase.client.BufferedMutatorImpl.backgroundFlushCommits(BufferedMutatorImpl.java:208)
> at org.apache.hadoop.hbase.client.BufferedMutatorImpl.flush(BufferedMutatorImpl.java:183)
> at org.apache.hadoop.hbase.client.HTable.flushCommits(HTable.java:1496)
> at org.apache.hadoop.hbase.client.HTable.put(HTable.java:1107)
> at org.apache.hadoop.hbase.client.HTableWrapper.put(HTableWrapper.java:149)
> at org.apache.phoenix.schema.stats.StatisticsWriter.commitLastStatsUpdatedTime(StatisticsWriter.java:282)
> at org.apache.phoenix.schema.stats.StatisticsWriter.newWriter(StatisticsWriter.java:79)
> at org.apache.phoenix.schema.stats.StatisticsCollector.<init>(StatisticsCollector.java:96)
> at org.apache.phoenix.schema.stats.StatisticsCollector.<init>(StatisticsCollector.java:84)
> at org.apache.phoenix.coprocessor.UngroupedAggregateRegionObserver.preCompact(UngroupedAggregateRegionObserver.java:628)
> at org.apache.hadoop.hbase.coprocessor.BaseRegionObserver.preCompact(BaseRegionObserver.java:191)
> at org.apache.hadoop.hbase.regionserver.RegionCoprocessorHost$9.call(RegionCoprocessorHost.java:569)
> at org.apache.hadoop.hbase.regionserver.RegionCoprocessorHost$RegionOperation.call(RegionCoprocessorHost.java:1663)
> at org.apache.hadoop.hbase.regionserver.RegionCoprocessorHost.execOperation(RegionCoprocessorHost.java:1738)
> at org.apache.hadoop.hbase.regionserver.RegionCoprocessorHost.execOperationWithResult(RegionCoprocessorHost.java:1712)
> at org.apache.hadoop.hbase.regionserver.RegionCoprocessorHost.preCompact(RegionCoprocessorHost.java:564)
> at org.apache.hadoop.hbase.regionserver.compactions.Compactor.postCreateCoprocScanner(Compactor.java:232)
> at org.apache.hadoop.hbase.regionserver.compactions.DefaultCompactor.compact(DefaultCompactor.java:88)
> at org.apache.hadoop.hbase.regionserver.DefaultStoreEngine$DefaultCompactionContext.compact(DefaultStoreEngine.java:122)
> at org.apache.hadoop.hbase.regionserver.HStore.compact(HStore.java:1230)
> at org.apache.hadoop.hbase.regionserver.HRegion.compact(HRegion.java:1724)
> at org.apache.hadoop.hbase.regionserver.CompactSplitThread$CompactionRunner.run(CompactSplitThread.java:511)
> at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1145)
> at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:615)
> at java.lang.Thread.run(Thread.java:745)
> ```

`PMS_SF_DAILY_FC_SALES`表是Apache phoenix创建的，压缩这张表时需要更新System.stats表（Apache phoenix维护的表，从表名上看是收集统计信息的），从日志里看到更新这张表失败了。

> 2016-11-26 14:41:29,414 INFO org.apache.hadoop.hbase.client.AsyncProcess: #2705, table=SYSTEM.STATS, attempt=**349/350** failed=1ops, last exception: org.apache.hadoop.net.ConnectTimeoutException: 10000 millis timeout while waiting for channel to be ready for connect. ch : java.nio.channels.SocketChannel[connection-pending remote=yhd-jqhadoop204.int.yihaodian.com/10.17.28.204:60020] on yhd-jqhadoop204.int.yihaodian.com,60020,1479956235061, tracking started null, retrying after=20074ms, replay=1ops

我不明白的是

1. 为什么更新失败**需要重试350次**，且重试的间隔2分钟左右？
2. 简单的估算，48小时有3000个major compaction 任务，平均大约一分钟一个major compaction，当然从图上看，是每隔一段时间突然增加几百个major compaction 任务。
3. 为什么更新System.stats表会失败？

问题1估计和phoenix的设置有关，问题二没有头绪。问题3则把**疑点指向了10.17.28.204**，发现10.17.28.204的压缩队列图如下：

![204的压缩队列图](204-compactionqueue.PNG)

下载10.17.28.204的日志，**仔细观察**发现和10.17.28.179不同，并没有长时运行的major compaction，而是

分析过程略，观察的现象如下：

1. 某个时间点之后，日志里再没有starting compaction的信息了
2. 分析重启10.17.28.204之前dump的堆栈，我得出的结论是major compaction的队列是空的
3. minor compaction 线程一直在work，CPU负载比平常高许多

![204 CPU负载](204-cpuload.PNG)

因此我的猜测是minor compaction 死循环了。Google了下发现了这个jira： [Scanner can be stuck in infinite loop if the HFile is corrupted](https://issues.apache.org/jira/browse/HBASE-12949)。



HBase CDH 5.4.3 的 Compaction Queue Size  这个监控指标的计算方式如下（参见 `CompactSplitThread`）

````java
  public int getCompactionQueueSize() {
    return longCompactions.getQueue().size() + shortCompactions.getQueue().size();
  }
````

其它JIRA

1. [HFile intermediate block level indexes might recurse forever creating multi TB files](https://issues.apache.org/jira/browse/HBASE-16288)，[milliseconds metrics may cause the compaction hang and huge region tmp files and region server down](https://github.com/OpenTSDB/opentsdb/issues/490)


调试方法

> Log = debug
> org.apache.hadoop.hbase.regionserver.CompactSplitThread
> org.apache.hadoop.hbase.regionserver.compactions.Compactor



1. `PrefixTreeArraySearcher#positionAtQualifierTimestamp`  二叉搜索
2. `UVIntTool`，可变长度的编码（http://mingxinglai.com/cn/2013/01/leveldb-varint32/）

