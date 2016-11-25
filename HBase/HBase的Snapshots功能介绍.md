# HBase的Snapshots功能介绍

2013-06-07 | [开源观察](http://www.dengchuanhua.com/category/opensource_observe)

hbase的snapshot功能还是挺有用的，本文翻译自cloudera的一篇博客，希望对想了解snapshot 的朋友有点作用，如果翻译得不好的地方，请查看原文 [Introduction to Apache HBase Snapshots](http://blog.cloudera.com/blog/2013/03/introduction-to-apache-hbase-snapshots/)  对照。

在之前，备份或者拷贝一个表只能用copy/export表，或者disable表后，从hdfs中拷贝出所有hfile。copy/export表用的是MapReduce来scan和copy表，这会对Region Server产生直接的性能影响，而用disable后拷贝文件则是直接不能访问了。

以此相反，HBase的snapshots功能可以让管理员不用拷贝数据的情况下轻松拷贝table，并且只会对RS造成很小影响。导出snapshots到另一个集群不会直接作用于RS，只是添加一些额外的逻辑。

下面是一些实用snapshots的场景：

- 从用户/app错误中恢复
  - 从某个已知的安全状态恢复/还原。
  - 查看之前的snapshots并选择性地从merge到产线中。
  - 在重大升级或者修改之前保存snapshots。
- 审查和/或报告指定时间的数据视图
  - 有目的性地按月采集数据。
  - 运行每天/每月/一刻时间报表。
- 应用测试
  - 用snapshots在产线测试schema或者程序改变对数据相似度的影响，然后丢弃它。例如，获取一个snapshot，然后用该snapshot的内容创建一个表，然后对该表进行操作。
- 离线作业
  - 获取一个snapshot，导到另外一个集群并用MapReduce作业来分析它。由于导出snapshot的操作发生在HDFS级别，你不会像拷贝表那样拖慢HBase。

## 什么是Snapshot?

一个snapshot其实就是一组metadata信息的集合，它可以让管理员将表恢复到以前的一个状态。snapshot并不是一份拷贝，它只是一个文件名的列表，并不拷贝数据。一个全的snapshot恢复以为着你可以回滚到原来的表schema和创建snapshot之前的数据。

**操作**

- 获取：该操作尝试从指定的表中获取一个snapshot。该操作在regions作balancing，split或者merge等迁移工作的时候可能会失败。
- 拷贝：该操作用指定snapshot的schema和数据来创建一个新表。该操作会不会对 原表或者该snapshot造成任何影响。
- 恢复： 该操作将一个表的schema和data回滚到创建该snapshot时的状态。 
- 删除：该操作将一个snapshot从系统中移除，释放磁盘空间，不会对其他拷贝或者snapshot造成任何影响。
- 导出：该操作拷贝这个snapshot的data和metadata到另一个集群。该操作仅影响HDFS，并不会和hbase的Master或者Region Server通信（这些操作可能会导致集群挂掉）。

## 零拷贝Snapshot，恢复，克隆

snapshot和CopyTable/ExportTable最大的区别是snapshot仅涉及metadata，不涉及数据拷贝。

Hbase一个重要的设计就是一旦写到一个文件就不会修改了。有不可修改的文件意味着一个snapshot仅需保持当前文件的使用相关信息就可以了, 并且，当compaction发生的时候，snapshot通知hbase系统仅把这些文件归档而不要删除它。

同样，当克隆或者恢复操作发生的时候，由于这些不变的文件，当用snapshot创建新表的时候仅需链接向这些不变的文件就行了。

导出snapshot是唯一需要拷贝数据的操作，这是因为其它的集群并没有这些数据文件。

## 导出Snapshot Vs Copy/Export Table

除去更加好的一致性保证外，和Copy/Export作业相比，最大的不同是导出snapshot操作是在HDFS层级进行的。这就意味着hbase的master和Region Server是不参与该操作的，因此snapshot导出不会创建一些不必要的数据缓存，并且也不会因为由于很多scan操作导致的GC。snapshot导出操作产生的网络和磁盘开销都被HDFS的datanode分摊吸收了。

## HBase Shell: Snapshot 操作

要想使用snapshot功能，请确认你的hbase-site.xml中的`hbase.snapshot.enabled` 配置项为true，如下：

```xml
<property>
<name>hbase.snapshot.enabled</name>
  <value>true</value>
</property>
```

 创建一个snapshot用如下命令，该操作没有文件拷贝操作：

> `hbase> snapshot ‘tableName’, ‘snapshotName’`


要想知道系统中创建了哪些**snapshot**，可以用**list_snapshot**命令，它会显示**snapshot**名，源表和创建时间日期。 

> hbase> list_snapshots
> SNAPSHOT               TABLE + CREATION TIME
>  TestSnapshot         TestTable (Mon Feb 25 21:13:49+0000 2013)

要想移除snapshot，用**delete_snapshot**命令，移除**snapshot**不会对已经克隆好的表胡总和随后发生的**snapshot**造成任何影响。

> hbase> delete_snapshot ‘snapshotName’

要想使用snapshot来创建一个新表，用**clone_snapshot**命令。该操作也无任何数据拷贝操作发生。

> hbase> clone_snapshot ‘snapshotName’, ‘newTableName’ 

要是想恢复或者替换当前表的schema和数据，用**restore_snapshot**命令。

> hbase> restore_snapshot ‘snapshotName’ 

要想导出一个snapshot到另外的集群，用**ExportSnapshot**工具。导出操作不会对Region server造成额外的负担。因为它工作在HDFS层级，你仅需指定HDFS的位置（其它集群的*hbase.rootdir*）即可，如下。

> hbase org.apache.hadoop.hbase.snapshot.ExportSnapshot -snapshot
> SnapshotName -copy-to hdfs:///srv2:8082/hbase 

## 当前存在的限制

Snapshots依赖于一些想当然的地方，当前还有很多新特性并没有完全集成到工具里：

- 做snapshot或者克隆表时如果发生Merging region操作时数据可能丢失。
- 恢复表的时候，由于是对一个replication进行的，这可能导致两个集群数据不同步。

## 总结

当前的snapshot特性以及包括了所有基本功能，但是依然还有很多工作要做，例如质量（metrics），Web UI集成，磁盘使用优化等。

要想了解更多snapshot相关信息，请看官方文档的snapshot一节。