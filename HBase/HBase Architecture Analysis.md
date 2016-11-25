# HBase Architecture Analysis Part1(Logical Architecture)

## 1. Overview

Apache HBase is an open source column-oriented database. **It is often described as a sparse, consistent, distributed, multi-dimensional sorted map.** HBase is modeled after Google’s “Bigtable: A distributed Storage System for Structured Data”, which can host very large tables with billions of rows, X millions of columns.
HBase is a No-SQL database, and it has very different paradigms from traditional RDBMS, which speaks SQL and enforce relationships upon data. HBase stores structured and semi-structured data with key-value style. Each row in HBase is located by** .**

HBase stores data distributed on Hadoop HDFS shipped with random data access. HDFS is Hadoop Distributed File System, which stores big data sets with streaming style. It has high reliability, scalability and availability but the cons is that HDFS split data into blocks (64MB or 128MB), it can handle large chucks of data very well, but not very well for small piece of data and low-latency data access. In other words, HDFS isn’t fit for online real time read and write, and that’s the meaning of HBase. HBase goal is to make use of cloud distributed data storage for online real time data access.
The blog analyzes the architecture of HBase with the method of “4+1” views by Kruthten.

## 2. Use Case View

[![Image and video hosting by TinyPic](http://i60.tinypic.com/346rmhh.jpg)](http://i60.tinypic.com/346rmhh.jpg)Image and video hosting by TinyPic
Figure 2.1 User Case View of HBase
The figure 2.1 shows the use case view of HBase. Since from the architecture view, the figure gives more details about the main features of HBase:
As shown on figure 2.1, the main features of HBase are as follows:
**1. For User:**

- **Read/Write big data in real time:** HBase provides transparent and simple interface for user. User has no need to care about details about data transforming.
- **Manage Data Model:** User Need to create and manage column-oriented data model about business logic, and HBase provide Interface for them to do management.
- **Manage System:** User can manually configure and monitor the status of HBase
  **2. For the System**
- **Store data distributed on HDFS:** HBase has great scalability and reliability based on HDFS.
- **Data random online, real time access:** HBase provides block cache and index on files for real time queries.
- **Automatic Data Sharding:** HBase partitions big data automatically on distributed node.
- **Automatic failover:** Since hardware failure on clusters is inevitable, HBase can failover automatically for high reliability and availability.

## 3. Logical Architecture

### 3.1 High Level Architecture and Style

[![Image and video hosting by TinyPic](http://i60.tinypic.com/2eezt6x.jpg)](http://i60.tinypic.com/2eezt6x.jpg)Image and video hosting by TinyPic
Figure 3.1 High Level Architecture for HBase
As shown in Figure3.1, the HBase architecture is layered overall, layered architecture style is a typical style for database.
**1. Layered Architecture Style**
➢** Client Layer:** The layer is for user, HBase provides easy-to-use java client API, and ships with non-java API as well, such as shell, Thrift, Avro and REST.
➢ **HBase Layer:** The layer is the core logic of HBase. Overall, HBase has two important components: HMaster and RegionServer. We will discuss them later.
Moreover, another important component is Zookeeper, which is a distributed coordination service modeled after Google’s Chubby. It’s another open source project under Apache.
➢ **Hadoop HDFS Layer:** HBase build on top of HDFS, which provides distributed data storage and data replication.

**2. Master-Slave Architecture Style**
For distributed applications, how to handle data blocks is a key point. There are two typical styles:**centralized and non-centralized**. Centralized mode is very available for scalability and load balancer, but there is Single Pont of Failure(SPOF) problem and performance bottleneck on the master node. Non-centralized mode has no SPOF problem and performance bottleneck, but not very easy to do load balance and there is data consistent problem.
HBase utilizes Centralized Mode: Master-Slave Style. BTW, Hadoop is also master-slave. The architecture style in Hadoop ecosystem is kind of consistent master-slave style.
[![Image and video hosting by TinyPic](http://i62.tinypic.com/ieff35.jpg)](http://i62.tinypic.com/ieff35.jpg)Image and video hosting by TinyPic
Figure 3.2 Master-Slave Architecture Style of HBase
As shown in figure 3.2:

- **HMaster** is the master in such style, which is responsible for RegionServer monitor, region assignment, metadata operations, RegionServer Failover etc. In a distributed cluster, HMaster runs on HDFS NameNode.
- **RegionServer** is the slave, which is responsible for serving and managing regions. In a distributed cluster, it runs on HDFS DataNode.
- **Zookeeper** will track the status of Region Server, where the root table is hosted. Since HBase 0.90.x, it introduces an even more tighter integration with Zookeeper. The heartbeat report from Region Server to HMaster is moved to Zookeeper, that is zookeeper has the responsibility of tracking Region Server status. Moreover, Zookeeper is the entry point of client, which enable query Zookeeper about the location of the region hosting the –ROOT- table.

### 3.2 HBase Data Model

HBase data model is Column-Family-Oriented. Some special characteristics of HBase data model is as follows:
➢ **Key-Value store:** Each cell in a table is specified by the four dimensional key: . The table will not store null values like RDBMS, it works well for large sparse table.
➢ **Sorted by key, index only on key.** Row key design is the only most important thing in HBase Schema Design.
➢ **Each cell can store different versions,** specified by Timestamp by default
➢ The column qualifier is** schema-less,** can be changed during run-time
➢ The column qualifier and value is treated a**s arbitrary bytes**
[![Image and video hosting by TinyPic](http://i58.tinypic.com/8wmlhj.jpg)](http://i58.tinypic.com/8wmlhj.jpg)Image and video hosting by TinyPic
Table 3.1 The logical view of data model
As shown in Table 3.1, is a logical view of a big table, the table is parse, each row has a unique row key, and the table has three column family: cf1, cf2, cf3. cf1 has two qualifiers: q1-1, q1-2. q1-1 on timestamp t1 has value v1.
[![Image and video hosting by TinyPic](http://i61.tinypic.com/14smvbr.jpg)](http://i61.tinypic.com/14smvbr.jpg)Image and video hosting by TinyPic
Table 3.2 The physical view of data model
In RDBMS, a big table as shown in Table 3.1 will store a lot of null values, but for HBase is not, key-value styles make HBase store only non-empty values as shown in Table3.2. It’s the physical view, a table is partitioned by Column Family, each Column Family is stored in a HStore in a table region on a Region Server.
Since the main purpose of the report is architecture analysis, the table design of HBase will not discuss in detail, you can refer to the “HBase in Action”

### 3.3 HBase Distributed Mode

#### 3.3.1 Big Table Splitting

HBase can run on local file system, called “standalone mode” for development and testing. For production, HBase should run on cluster on top of HDFS. Big tables are split and stored across the cluster.
[![Image and video hosting by TinyPic](http://i62.tinypic.com/16h0rdk.jpg)](http://i62.tinypic.com/16h0rdk.jpg)Image and video hosting by TinyPic
Figure 3.3 Region split on big table
A table is split into roughly equal size. Regions are assigned to Region Servers across the cluster. And Region Servers host roughly equal number of regions. As shown in Figure3.3, Table A is split into four regions, each of which is assigned to a Region Server.

#### 3.3.2 Region Server

As shown in figure3.4, the Region Server Architecture. It contains several components as follows:

- **One Block Cache**, which is a LRU priority cache for data reading.
- **One WAL(Write Ahead Log):** HBase use Log-Structured-Merge-Tree(LSM tree) to process data writing. Each data update or delete will be write to WAL first, and then write to MemStore. WAL is persisted on HDFS.
- **Multiple HRegions:** each HRegion is a partition of table as we talk about in 3.3.1.
- **In a HRegion:** Multiple HStore: Each HStore is correspond to a Column Family
- **In a HStore:** **One MemStore:** store updates or deletes before flush to disk. **Multiple StoreFile**, each of which is correspond to a HFile
- **A HFile** is immutable, flushed from MemStore, persisted on HDFS
  [![Image and video hosting by TinyPic](http://i60.tinypic.com/c28l.jpg)](http://i60.tinypic.com/c28l.jpg)Image and video hosting by TinyPic
  Figure3.4 Region Server Architecture

#### 3.3.3 -ROOT- and .META table

Since table are partitioned and store across the cluster. How can the client find which region hosting a specific row key range? There are two special catalog tables, -ROOT- and .META. table for this.
➢**.META. table**: host the region location info for a specific row key range. The table is stored on Region Servers, which can be split into as many region as required.
➢**-ROOT- table:** host the .META. table info. There is only one Region Server store the –ROOT- table. And the Root region never split into more than one region.
The –ROOT- and .META. table structure logically looks as a B+ tree as shown in figure 3.5.
The RegionServer RS1 host the –ROOT- table, the .META. table is split into 3 regions: M1, M2, M3, hosted on RS2, RS3, RS1. Table T1 contains three regions, T2 contains four regions. For example, T1R1 is hosted on RS3, the meta info is hosted on M1.
[![Image and video hosting by TinyPic](http://i60.tinypic.com/soa649.jpg)](http://i60.tinypic.com/soa649.jpg)Image and video hosting by TinyPic
Figure 3.5 –ROOT-, .META., and User table viewed as B+ tree

#### 3.3.4 Region Lookup

How can client find where the –ROOT- table is? Zookeeper does this work, and how to find the region where the target row is. Even though it belongs to process architecture, it is related to the section, hence we discuss it in advance. let’s look at an example as shown in figure3.6.
\1. Client query Zookeeper: where is the –ROOT-? On RS1.
\2. Client request RS1: Which meta region contains row: T10006? META1 on RS2
\3. Client request RS2: Which region can find the row T10006? Region on RS3
\4. Client get the from the region on RS3
\5. Client cache the region info, and is refreshed until the region location info changed.
[![Image and video hosting by TinyPic](http://i60.tinypic.com/30ml56q.jpg)](http://i60.tinypic.com/30ml56q.jpg)Image and video hosting by TinyPic
Figure 3.6 Region Lookup Process

### 3.4 HBase Communication Protocol

[![Image and video hosting by TinyPic](http://i61.tinypic.com/2zggmrk.jpg)](http://i61.tinypic.com/2zggmrk.jpg)Image and video hosting by TinyPic
Figure 3.7 HBase Communication Protocol
In HBase 0.96 release, HBase has moved its communication protocol to** Protocol Buffer.** Protocol buffer is a method of serializing structured data developed by Google. Google uses it for almost all of its internal RPC protocol and file formats.
Protocol buffer involves an Interface Description Language for cross language service like Apache Thrift.
Basically, the communication between sub-systems of HBase is RPC, which is implemented in Protocol Buffer.
The main protocols are as follows:
➢ **MasterMonitor Protocol:** client use it to monitor the status of HMaster
➢ **MasterAdmin Protocol:** client use it to do management for HMaster, such as region manually management, table meta info management.
➢** Admin Protocol:** client use it communicate with HRegionServer, to do admin work, such as region split, store file compact, WAL management.
➢** Client Protocol:** Client use it to read/write to HRegionServer
➢ **RegionServerStatus Protocol**: HRegionServer use it to communicate with HMaster: including the request and response of server startup, server fatal error, server status report.

### 3.5 Log-Structured Merge-Trees(LSM-trees)

No-SQL database usually uses LSM-trees as data storage process architecture. HBase is no exception. As we all known, RDBMS adopts B+ tree to organize its indexes, as shown in Fugure 3.3. These B+ trees are often 3-level n-way balance trees. The nodes of a B+ tree are blocks on disk. So for a update by RDBMS, it likely needs 5 times disk operation. (3 times for B+ tree to find the block of the target row, 1 time for target block read, and 1 time for data update).
On RDBMS, data is written randomly as heap file on disk, but random data block decrease read performance. That’s why we need B+ tree index. B+ tree is fit well for data read, but is not efficient for data updates. Given the large distributed data, B+ tree is not the competitor for LSM-trees so far.
[![Image and video hosting by TinyPic](http://i58.tinypic.com/7286l0.jpg)](http://i58.tinypic.com/7286l0.jpg)Image and video hosting by TinyPic
Figure3.8 B+ tree

LSM-trees can be viewed as n-level merge-trees. It transforms random writes into sequential writes using logfile and in-memory store. Figure3.9 shows data write process of LSM-trees.
[![Image and video hosting by TinyPic](http://i59.tinypic.com/2mw8nky.jpg)](http://i59.tinypic.com/2mw8nky.jpg)Image and video hosting by TinyPic
Figure 3.9 LSM-trees

➢ **Data Write(Insert, update):** Data is written to logfile sequentially first, then to in-memory store, where data is organized as sorted tree, like B+ tree. When the in-memory store is filled up, the tree in the memory will be flushed to a store file on disk. The store files on disk is arranged like B+ tree, as the C1 Tree shown in Figure 3.9 . But store files are optimized for sequential disk access.
➢**Data Read: I**n-memory store is searched first. Then search the store files on disk.
➢**Data Delete:** Give a data record a “delete marker”, system background will do housekeeping work by merging some store files into a larger one to reduce disk seeks. A data record will be deleted permanently during the housekeeping.
LSM-trees’ data updates are operated in memory, no disk access, it’s faster than B+ tree. When the data read is always on the data set that is written recently, LSM-trees will reduce disk seeks, and improve performance. When disk IO is the cost we must consider, LSM-trees is more suitable than B+ tree.

# HBase Architecture Analysis Part2(Process Architecture)

## 4. Process Architecture

### 4.1 HBase Write Path

The client doesn’t write data directly into HFile on HDFS. Firstly it writes data to WAL(Write Ahead Log), and Secondly, writes to MemStore shared by a HStore in memory.
[![Image and video hosting by TinyPic](http://i58.tinypic.com/20i77l0.jpg)](http://i58.tinypic.com/20i77l0.jpg)Image and video hosting by TinyPic
Figure4.1 HBase Write Path

**MemStore** is a write buffer(64MB by default). When the data in MemStore accumulates its threshold, data will be flush to a new HFile on HDFS persistently. Each Column Family can have many HFiles, but each HFile only belongs to one Column Family.
**WAL** is for data reliability, WAL is persistent on HDFS and each Region Server has only on WAL. When the Region Server is down before MemStore flush, HBase can replay WAL to restore data on a new Region Server.
A data write completes successfully only after the data is written to WAL and MemStore.

### 4.2 HBase Read Path

As shown in Figure 4.2, it’s the read path of HBase.
\1. Client will query the MemStore in memory, if it has the target row.
\2. When MemStore query failed, client will hit the BlockCache.
\3. After the MemStore and BlockCache query failed, HBase will load HFiles into memory which may contain the target row info.
The MemStore and BlockCache is the mechanism for real time data access for distributed large data.
**BlockCache is a LRU(Lease Recently Used) priority cache.** Each RegionServer has a single BlockCache. It keeps frequently accessed data from HFile in memory to reduce disk data reads. The “Block”(64KB by default) is the smallest index unit of data or the smallest unit of data that can be read from disk by one pass.
For random data access, small block size is preferred, but block index consumes more memory. And for sequential data access, large block size is better, fewer index save more memory.

[![Image and video hosting by TinyPic](http://i61.tinypic.com/290x9qt.jpg)](http://i61.tinypic.com/290x9qt.jpg)Image and video hosting by TinyPic
Figure 4.2 HBase Read Path

### 4.3.HBase Housekeeping: HFile Compaction

The data of each column family is flush into multiple HFiles. Too many HFiles means many disk data reads and lower the read performance. Therefore, HBase do HFile compaction periodically.
[![Image and video hosting by TinyPic](http://i62.tinypic.com/2nk85xj.jpg)](http://i62.tinypic.com/2nk85xj.jpg)Image and video hosting by TinyPic
Figure4.3 HFile Compaction

**➢Minor Compaction**
It happens on multiple HFiles in one HStore. Minor compaction will pick up a couple of adjacent small HFiles and rewrite them into a larger one.
The process will keep the deleted or expired cells. The HFile selection standard is configurable. Since minor compaction will affect HBase performace, there is an upper limit on the number of HFiles involved (10 by default).
**➢Major Compaction**
Major Compaction compact all HFiles in a HStore(Column Family) into one HFile. It is the only chance to delete records permanently. Major Compaction will usually have to be triggered manually for large clusters.
Major Compaction is not region merge, it happens to HStore which will not result in region merge.

### 4.4 HBase Delete

When HBase client send delete request, the record will be marked “tombstone”, it is a “predicate deletion”, which is supported by LSM-tree. Since HFile is immutable, deletion isn’t available for HFile on HDFS. Therefore, HBase adopts major compaction(Section 4.3) to clean up deleted or expired records.

### 4.5 Region Assignment

It is a main task of HMaster:
\1. HMaster invoke the AssignmentManager to do region assignment.
\2. AssignmentManager checks the existing region assignments in .META.
\3. If region assignment is valid, then keep the region.
\4. If region assignment is invalid, then the LoadBalancerFactory will create a DefaultLoadBalancer
\5. DefaultLoadBalancer will assign the new region randomly to a RegionServer
\6. Update the assignment to .META.
\7. The RegionServer open the new region

### 4.6 Region Split

Region split is the work of RegionServer, not participated by HMaster. When a region in a RegionServer accumulates over size threshold, RegionServer will split the region into half.
\1. RegionServer offline the region to be split.
\2. RegionServer split the region into two half
\3. Update new daughter regions info to .META.
\4. Open the new daughter regions.
\5. Report the split info to HMaster.

### 4.7 Region Merge

A RegionServer can’t have too many regions, because too many regions bring large cost of memory and lower the performance of RegionServer. Meanwhile, HMaster can’t handle load balance with too many regions.
Therefore, when the number of regions is over a threshold, region merge is trigged. Region Merge is joined by RegionServer and HMaster.
\1. Client send RPC region merge request to HMaster
\2. HMaster moves regions together to the same RegionServer where the more heavily loaded region resided.
\3. HMaster send request to the RegionServer to run region merge.
\4. The RegionServer offline the regions to be merged.
\5. The RegionServer Merge the regions on the local file system.
\6. Delete the meta info of the merging regions on .META. table, add new meta info to .META. table.
\7. Open the new merged region
\8. Report the merge to HMaster

### 4.8 Auto Failover

#### 4.8.1 RegionServer Failover

When a region server fails, HMaster will do failover automatically.
\1. RegionServer is down
\2. HMaster will detect the unavailable RegionServer when there is no heartbeat report.
\3. HMaster start region reassignment, detect that the region assignment is invalid, then re-assign the regions like the region assignment sequence.

#### 4.8.2 Master Failover

In centralized architecture style, the SPOF is a problem, you may ask How does HBase will do when HMaster failed? There two safeguards for SPOF:
**1. Multi-Master environment**
HBase can run in multi-master cluster. There is only one active master, when the master is down, the remaining master will take over the master role.
It looks like Hadoop HDFS new feature high availability, a standby NameNode will take over the NameNode role when the active one failed.
**2. Catalog tables are on RegionServers**
When client send read/write request to HBase, it talks directly to RegionServer, not HMaster. Hence, the cluster can be steady for a time without HMaster. But it requires that the HMaster failover as soon as possible, HMaster is the vital of the cluster.

#### 4.9 Data Replication

For data reliability, HBase replicates data blocks across the cluster. This is achieved by Hadoop HDFS.
By default, there 3 replicas:
\1. The First replica is written to local node.
\2. The second replica is written to a random node on a different rack.
\3. The third replica is on a random node on the same rack
HBase stores HFiles and WAL on HDFS. When RegionServer is down, new region assignment can be done by read replicas of HFile and replay the replicas the WAL. In short, HBase has high reliability.

# HBase Architecture Analysis Part 3 Pros and Cons

## 5. HBase Physical Architecture

Figure 5.1 shows the deployment view for HBase cluster:
HBase is the master-slave cluster on top of HDFS. The classic deployment is as follows:
➢** Master node:** one HMaster and one NameNode running on a machine as the master node.
➢ **Slave node:** Each node is running one HRegionServer and one DataNode. And each node report status to the master node and Zookeeper.
➢** Zookeeper:** HBase is shipped with ensemble Zookeeper, but for large clusters, using existing Zookeeper is better. Zookeeper is crucial, the HMaster and HRegionServers will register on Zookeeper.
➢ **Client**: There can be many clients to access HRegionServer, like Java Client, Shell Client, Thrift Client and Avro Client

[![Image and video hosting by TinyPic](http://i58.tinypic.com/6eg29d.jpg)](http://i58.tinypic.com/6eg29d.jpg)Image and video hosting by TinyPic
Figure 5.1 HBase Deployment View

## 6.HBase Pros

**1. Master-Slave Architecture**
HBase build on top HDFS, HDFS is mater-slave architecture, HBase follows this style. It will improve the interoperability between HBase and HDFS.
This style do good for load balance, the HMaster takes over the load balance job, assign regions to region servers and auto failover dead RegionServers.

**2. Real-time , random big data access**
HDFS handles large blocks of data well, but small blocks of data with real-time access is not efficient. HBase uses LSM-trees as data storage architecture. Data is written into WAL first, then to Mem Store. WAL is in case of data lost before Mem Store is flushed to HDFS. Mem Store will be flushed to HDFS when it’s filled up. The mechanism insures that data write is operated in memory, no need of HDFS disk access, which improves write performance.
Meanwhile, data read accesses Mem Store first, then to Block Cache, and last the HFiles, so for random data access, if there is cache in memory, the read performance is very well.
In short, HBase brings big data online, it is efficient for real-time operations on big data(TB, PB);

**3. Column-Oriented data model for big sparse table**
HBase is NO-SQL, column-family-oriented, each column family is stored together in HStore. The key-value data model stores only non-empty values, no null values. So for large spares table, the disk usage is great in HBase, not like RDBMS storing large quantities of null values.

**4. LSM-trees vs. B+ tree**
HBase data storage architecture is LSM-trees. A big feature of LSM-trees is the merge housekeeping, which merge small files into a larger one to reduce disk seek. The housekeeping is a background process, which will not impact real-time data access.
LSM-trees transform random data access into sequential data access, which improves read performance. But B+ tree in RDBMS requires more disk seeks for data modifications, which is very efficient for big data process.

**5. Row-level Atomic**
HBase has no strictly ACID features like RDBMS, how to insure data consistent in HBase and how to avoid dirty read. HBase is row-level automic, in other words, each Put operation on a given row either success or fail. Meanwhile, CheckAnd*, Increment* operations are also atomic.
The scan operation across the table is not on a snapshot of a table, if the data is updated before a Scan operation, the updated version is read by Scan, It ensures data consistent.
HBase is good at loose coupled data and not high requirements for consistent. Row-level atomic is a mechanism to improve data consistent, but data consistent is really a shortage of HBase.

**6. High Scalability**
HBase data is stored across the cluster, the cluster has many HRegionServer, which can be scaled. HBase build on top of HDFS, HDFS is high scalability, that is the DataNode can be scaled. HDFS brings great scalability to HBase. Moreover, the master-slave architecture do well in data scalability.

**7. High Reliability**
The HBase cluster is built on commodity hardware, which will be dead at any time. HBase data is replicated across the cluster. Data replication is done by HDFS. HDFS stores 3 replications for each block: on the same node, on another node on the same rack, on another node on another rack. The feature brings high reliability for HBase.

**8. Auto Failover**
HMaster is in charge of auto failover work. When HMaster detects the dead HRegionServer. It will find that the region assignment is invalid, and it will do region assignment again. Auto failover brings great availability for HBase, which is no need of manually HRegionServer failover.

**9. Data auto sharding**
Since data is stored distributed across the cluster. HBase must makes use of all of the machines in the cluster. HRegionServer stores many HRegions. When a HRegion size reaches its threshold, HRegionServer will split it into half.
In addition, when the number of regions is over the threshold that the HMaster can handle, the HMaster will merge small regions into a larger one. Auto sharding brings HBase great load balance and keeps programmer away from the details of distributed data sharding.

**10. Simple Client Interface**
HBase provides 5 basic data operations : Put, Get, Delete, Scan and Increment. The Interface is easy to use. HBase provide java interface, and non java interface: Thrift, Avro, REST and Shell. Each interface is transparent for developers to do data operations on HBase.

## 7. HBase Cons

**1. Single Point of Failure (SPOF)**
The master-slave architecture always has SPOF, HBase is no exception. When there is only on HMaster in a cluster, if the cluster has more than 4000 nodes, the HMaster is the performance bottleneck. Even though client talks directly to HRegionServer, when the HMaster is down, the cluster can be “steady” for a short time, HMaster long time down will bring down the cluster.
It is possible to start multi HMaster in a cluster, but only one is active, which is also a performance bottleneck.

**2. No transaction**
As we talked before, HBase data consistent is a shortage. Interrow operations are not atomic, which will bring dirty read. HBase is good at louse coupled data but not good at highly rational data records. That is an important point we must be aware of.

**3. No Join, if you want, use MapReduce**
HBase has no join operation, so data model has to be redesign when migrate from RDBMS, which is a big headache. For highly rational data records, HBase will make you sick. But if you really want implement join, you can use MapReduce, please refer to “HBase in Action”.

**4. Index only on key, sorted by key, but RDBMS can be indexed on arbitrary column**
The column-oriented data model is key-value style, HBase only has index on key: <rowkey, column family, qualifier, version>, rowkey design is the most important thing.
The data model paradigm for HBase is quite different for RDBMS. HBase has no index on value. Scan with columnvalue filter for big data is very slow. But RDBMS can be indexed on arbitrary column.
If you want to improve index for HBase, you can use MapReduce to build Inverted Index.

**5. Security Problem**
Finally, HBase has security problem. RDBMS like MySQL has authentication and authorization feature, different user has different data access authority. But HBase has no such feature yet. But we can see that, improve security will lower the performance, If the online application can insure the security, HBase will has no need to care about that.