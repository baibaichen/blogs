## HBase and MapR Database: Designed for Distribution, Scale, and Speed.
[Apache HBase](https://mapr.com/products/apache-hbase/) is a database that runs on a [Hadoop](https://mapr.com/products/apache-hadoop/) cluster. HBase is not a traditional RDBMS, as it relaxes the ACID (Atomicity, Consistency, Isolation, and Durability) properties of traditional RDBMS systems in order to achieve much greater scalability. Data stored in HBase also does not need to fit into a rigid schema like with an RDBMS, making it ideal for storing unstructured or semi-structured data.

The [MapR Data Platform](https://mapr.com/products/) supports HBase, but also supports [MapR Database](https://mapr.com/products/mapr-database/), a high performance, enterprise-grade NoSQL DBMS that includes the HBase API to run HBase applications. For this blog, I’ll specifically refer to HBase, but understand that many of the advantages of using HBase in your data architecture apply to MapR Database. MapR built MapR Database to take HBase applications to the next level, so if the thought of higher powered, more reliable HBase deployments sound appealing to you, take a look at some of the [MapR Database content here](https://mapr.com/products/mapr-database/).

HBase allows you to build big data applications for scaling, but with this comes some different ways of implementing applications compared to developing with traditional relational databases. In this blog post, I will provide an overview of HBase, touch on the limitations of relational databases, and dive into the specifics of the HBase data model.

**Relational Databases vs. HBase – Data Storage Model**

Why do we need NoSQL/HBase? First, let’s look at the pros of relational databases before we discuss its limitations:

- Relational databases have provided a standard persistence model
- [SQL](https://mapr.com/why-hadoop/sql-hadoop/sql-hadoop-details) has become a de-facto standard model of data manipulation (SQL)
- Relational databases manage concurrency for transactions
- Relational database have lots of tools

![Relational databases vs. HBase](https://mapr.com/blog/hbase-and-mapr-db-designed-distribution-scale-and-speed/assets/blogimages/Relational-Databases-vs-HBase.png)

Relational databases were the standard for years, so what changed? With more and more data came the need to scale. One way to scale is vertically with a bigger server, but this can get expensive, and there are limits as your size increases.

![Relational databases vs HBase](https://mapr.com/blog/hbase-and-mapr-db-designed-distribution-scale-and-speed/assets/blogimages/Relational-Databases-vs-HBase-2.png)

*Relational Databases vs. HBase - Scaling*

**What changed to bring on NoSQL?**

An alternative to vertical scaling is to scale horizontally with a cluster of machines, which can use commodity hardware. This can be cheaper and more reliable. To horizontally partition or shard a RDBMS, data is distributed on the basis of rows, with some rows residing on a single machine and the other rows residing on other machines, However, it’s complicated to partition or shard a relational database, and it was not designed to do this automatically. In addition, you lose the querying, transactions, and consistency controls across shards. Relational databases were designed for a single node; they were not designed to be run on clusters.

![NoSQL scale](https://mapr.com/blog/hbase-and-mapr-db-designed-distribution-scale-and-speed/assets/blogimages/NoSQL-scale.png)

**Limitations of a Relational Model**

Database normalization eliminates redundant data, which makes storage efficient. However, a normalized schema causes joins for queries, in order to bring the data back together again. While HBase does not support relationships and joins, data that is accessed together is stored together so it avoids the limitations associated with a relational model. See the difference in data storage models in the chart below:

![RDBMS HBase storage model](https://mapr.com/blog/hbase-and-mapr-db-designed-distribution-scale-and-speed/assets/blogimages/RDBMS-HBase-storage.png)

*Relational databases vs. HBase - data storage model*

**HBase Designed for Distribution, Scale, and Speed**

HBase was designed to scale due to the fact that data that is accessed together is stored together. Grouping the data by key is central to running on a cluster. In horizontal partitioning or sharding, the key range is used for sharding, which distributes different data across multiple servers. Each server is the source for a subset of data. Distributed data is accessed together, which makes it faster for scaling. HBase is actually an implementation of the BigTable storage architecture, which is a distributed storage system developed by Google that’s used to manage structured data that is designed to scale to a very large size.

HBase is referred to as a column family-oriented data store. It’s also row-oriented: each row is indexed by a key that you can use for lookup (for example, lookup a customer with the ID of 1234). Each column family groups like data (customer address, order) within rows. Think of a row as the join of all values in all column families.

![HBase column database](https://mapr.com/blog/hbase-and-mapr-db-designed-distribution-scale-and-speed/assets/blogimages/HBase-column.png)

*HBase is a column family-oriented database*

HBase is also considered a distributed database. Grouping the data by key is central to running on a cluster and sharding. The key acts as the atomic unit for updates. Sharding distributes different data across multiple servers, and each server is the source for a subset of data.

![HBase distributed database](https://mapr.com/blog/hbase-and-mapr-db-designed-distribution-scale-and-speed/assets/blogimages/HBase-distributed-database.png)

*HBase is a distributed database*

**HBase Data Model**

Data stored in HBase is located by its “rowkey.” This is like a primary key from a relational database. Records in HBase are stored in sorted order, according to rowkey. This is a fundamental tenet of HBase and is also a critical semantic used in HBase schema design.

![HBase Data Model](https://mapr.com/blog/hbase-and-mapr-db-designed-distribution-scale-and-speed/assets/blogimages/HBase-data-model.png)

*HBase data model – row keys*

Tables are divided into sequences of rows, by key range, called regions. These regions are then assigned to the data nodes in the cluster called “RegionServers.” This scales read and write capacity by spreading regions across the cluster. This is done automatically and is how HBase was designed for horizontal sharding.

![HBase Tables](https://mapr.com/blog/hbase-and-mapr-db-designed-distribution-scale-and-speed/assets/blogimages/HBase-tables.png)

*Tables are split into regions = contiguous keys*

The image below shows how column families are mapped to storage files. Column families are stored in separate files, which can be accessed separately.

![Hbase column families](https://mapr.com/blog/hbase-and-mapr-db-designed-distribution-scale-and-speed/assets/blogimages/HBase-column-families.png)

The data is stored in HBase table cells. The entire cell, with the added structural information, is called Key Value. The entire cell, the row key, column family name, column name, timestamp, and value are stored for every cell for which you have set a value. The key consists of the row key, column family name, column name, and timestamp.

![Hbase table cells](https://mapr.com/blog/hbase-and-mapr-db-designed-distribution-scale-and-speed/assets/blogimages/Hbase-table-cells.png)

Logically, cells are stored in a table format, but physically, rows are stored as linear sets of cells containing all the key value information inside them.

In the image below, the top left shows the logical layout of the data, while the lower right section shows the physical storage in files. Column families are stored in separate files. The entire cell, the row key, column family name, column name, timestamp, and value are stored for every cell for which you have set a value.

![logical data model vs physical data storage](https://mapr.com/blog/hbase-and-mapr-db-designed-distribution-scale-and-speed/assets/blogimages/Logical-vs-physical-storage.png)

*Logical data model vs. physical data storage*

As mentioned before, the complete coordinates to a cell's value are: Table:Row:Family:Column:Timestamp ➔ Value. HBase tables are sparsely populated. If data doesn’t exist at a column, it’s not stored. Table cells are versioned uninterpreted arrays of bytes. You can use the timestamp or set up your own versioning system. For every coordinate row:family:column, there can be multiple versions of the value.

![Sparse data](https://mapr.com/blog/hbase-and-mapr-db-designed-distribution-scale-and-speed/assets/blogimages/sparse-data.png)

*Sparse data with cell versions*

Versioning is built in. A put is both an insert (create) and an update, and each one gets its own version. Delete gets a tombstone marker. The tombstone marker prevents the data being returned in queries. Get requests return specific version(s) based on parameters. If you do not specify any parameters, the most recent version is returned. You can configure how many versions you want to keep and this is done per column family. The default is to keep up to three versions. When the max number of versions is exceeded, extra records will be eventually removed.

![versioned data](https://mapr.com/blog/hbase-and-mapr-db-designed-distribution-scale-and-speed/assets/blogimages/versioned-data.png)

*Versioned data*

In this blog post, you got an overview of HBase (and implicitly MapR Database) and learned about the HBase/MapR Database data model. Stay tuned for the next blog post, where I’ll take a deep dive into [the details of the HBase architecture](https://mapr.com/blog/in-depth-look-hbase-architecture). In the third and final blog post in this series, we’ll take a look at schema design guidelines.

**Want to learn more?**

- [Installing HBase on MapR](http://doc.mapr.com/display/MapR/HBase)
- [Getting Started with HBase on MapR](http://doc.mapr.com/display/MapR/Working+with+HBase)
- [Release notes for HBase on MapR](http://doc.mapr.com/display/components/HBase+Release+Notes)
- [Apache HBase docs](https://issues.apache.org/jira/browse/HBASE/?report=com.atlassian.jira.jira-projects-plugin:roadmap-panel&selectedTab=com.atlassian.jira.jira-projects-plugin:summary-panel)

In this blog post, I’ll give you an in-depth look at the HBase architecture and its main benefits over NoSQL data store solutions. Be sure and read the first blog post in this series, titled
[“HBase and MapR Database: Designed for Distribution, Scale, and Speed.”](https://mapr.com/blog/hbase-and-mapr-db-designed-distribution-scale-and-speed#.VcKFNflVhBc)

## HBase Architectural Components

Physically, HBase is composed of three types of servers in a master slave type of architecture. Region servers serve data for reads and writes. When accessing data, clients communicate with HBase RegionServers directly. Region assignment, DDL (create, delete tables) operations are handled by the HBase Master process. Zookeeper, which is part of HDFS, maintains a live cluster state.

The Hadoop DataNode stores the data that the Region Server is managing. All HBase data is stored in HDFS files. Region Servers are collocated with the HDFS DataNodes, which enable data locality (putting the data close to where it is needed) for the data served by the RegionServers. HBase data is local when it is written, but when a region is moved, it is not local until compaction.

The NameNode maintains metadata information for all the physical data blocks that comprise the files.

![img](https://mapr.com/blog/in-depth-look-hbase-architecture/assets/blogimages/HBaseArchitecture-Blog-Fig1.png)

## Regions

HBase Tables are divided horizontally by row key range into “Regions.” A region contains all rows in the table between the region’s start key and end key. Regions are assigned to the nodes in the cluster, called “Region Servers,” and these serve data for reads and writes. A region server can serve about 1,000 regions.

![img](https://mapr.com/blog/in-depth-look-hbase-architecture/assets/blogimages/HBaseArchitecture-Blog-Fig2.png)

## HBase HMaster

Region assignment, DDL (create, delete tables) operations are handled by the HBase Master.

A master is responsible for:

- Coordinating the region servers

  \- Assigning regions on startup , re-assigning regions for recovery or load balancing

  \- Monitoring all RegionServer instances in the cluster (listens for notifications from zookeeper)

- Admin functions

  \- Interface for creating, deleting, updating tables

![img](https://mapr.com/blog/in-depth-look-hbase-architecture/assets/blogimages/HBaseArchitecture-Blog-Fig3.png)

## ZooKeeper: The Coordinator

HBase uses ZooKeeper as a distributed coordination service to maintain server state in the cluster. Zookeeper maintains which servers are alive and available, and provides server failure notification. Zookeeper uses consensus to guarantee common shared state. Note that there should be three or five machines for consensus.

![img](https://mapr.com/blog/in-depth-look-hbase-architecture/assets/blogimages/HBaseArchitecture-Blog-Fig4.png)

## How the Components Work Together

Zookeeper is used to coordinate shared state information for members of distributed systems. Region servers and the active HMaster connect with a session to ZooKeeper. The ZooKeeper maintains ephemeral nodes for active sessions via heartbeats.

![img](https://mapr.com/blog/in-depth-look-hbase-architecture/assets/blogimages/HBaseArchitecture-Blog-Fig5.png)

Each Region Server creates an ephemeral node. The HMaster monitors these nodes to discover available region servers, and it also monitors these nodes for server failures. HMasters vie to create an ephemeral node. Zookeeper determines the first one and uses it to make sure that only one master is active. The active HMaster sends heartbeats to Zookeeper, and the inactive HMaster listens for notifications of the active HMaster failure.

If a region server or the active HMaster fails to send a heartbeat, the session is expired and the corresponding ephemeral node is deleted. Listeners for updates will be notified of the deleted nodes. The active HMaster listens for region servers, and will recover region servers on failure. The Inactive HMaster listens for active HMaster failure, and if an active HMaster fails, the inactive HMaster becomes active.

## HBase First Read or Write

There is a special HBase Catalog table called the META table, which holds the location of the regions in the cluster. ZooKeeper stores the location of the META table.

This is what happens the first time a client reads or writes to HBase:

1. The client gets the Region server that hosts the META table from ZooKeeper.
2. The client will query the .META. server to get the region server corresponding to the row key it wants to access. The client caches this information along with the META table location.
3. It will get the Row from the corresponding Region Server.

For future reads, the client uses the cache to retrieve the META location and previously read row keys. Over time, it does not need to query the META table, unless there is a miss because a region has moved; then it will re-query and update the cache.

![img](https://mapr.com/blog/in-depth-look-hbase-architecture/assets/blogimages/HBaseArchitecture-Blog-Fig6.png)

## HBase Meta Table

- This META table is an HBase table that keeps a list of all regions in the system.

- The .META. table is like a b tree.

- The .META. table structure is as follows:

  \- Key: region start key,region id

  \- Values: RegionServer

![img](https://mapr.com/blog/in-depth-look-hbase-architecture/assets/blogimages/HBaseArchitecture-Blog-Fig7.png)

## Region Server Components

A Region Server runs on an HDFS data node and has the following components:

- WAL: Write Ahead Log is a file on the distributed file system. The WAL is used to store new data that hasn't yet been persisted to permanent storage; it is used for recovery in the case of failure.
- BlockCache: is the read cache. It stores frequently read data in memory. Least Recently Used data is evicted when full.
- MemStore: is the write cache. It stores new data which has not yet been written to disk. It is sorted before writing to disk. There is one MemStore per column family per region.
- Hfiles store the rows as sorted KeyValues on disk.

![img](https://mapr.com/blog/in-depth-look-hbase-architecture/assets/blogimages/HBaseArchitecture-Blog-Fig8.png)

## HBase Write Steps (1)

When the client issues a Put request, the first step is to write the data to the write-ahead log, the WAL:

\- Edits are appended to the end of the WAL file that is stored on disk.

\- The WAL is used to recover not-yet-persisted data in case a server crashes.

![img](https://mapr.com/blog/in-depth-look-hbase-architecture/assets/blogimages/HBaseArchitecture-Blog-Fig9.png)

## HBase Write Steps (2)

Once the data is written to the WAL, it is placed in the MemStore. Then, the put request acknowledgement returns to the client.

![img](https://mapr.com/blog/in-depth-look-hbase-architecture/assets/blogimages/HBaseArchitecture-Blog-Fig10.png)

## HBase MemStore

The MemStore stores updates in memory as sorted KeyValues, the same as it would be stored in an HFile. There is one MemStore per column family. The updates are sorted per column family.

![img](https://mapr.com/blog/in-depth-look-hbase-architecture/assets/blogimages/HBaseArchitecture-Blog-Fig11.png)

## HBase Region Flush

When the MemStore accumulates enough data, the entire sorted set is written to a new HFile in HDFS. HBase uses multiple HFiles per column family, which contain the actual cells, or KeyValue instances. These files are created over time as KeyValue edits sorted in the MemStores are flushed as files to disk.

Note that this is one reason why there is a limit to the number of column families in HBase. There is one MemStore per CF; when one is full, they all flush. It also saves the last written sequence number so the system knows what was persisted so far.

The highest sequence number is stored as a meta field in each HFile, to reflect where persisting has ended and where to continue. On region startup, the sequence number is read, and the highest is used as the sequence number for new edits.

![img](https://mapr.com/blog/in-depth-look-hbase-architecture/assets/blogimages/HBaseArchitecture-Blog-Fig12.png)

## HBase HFile

Data is stored in an HFile which contains sorted key/values. When the MemStore accumulates enough data, the entire sorted KeyValue set is written to a new HFile in HDFS. This is a sequential write. It is very fast, as it avoids moving the disk drive head.

![img](https://mapr.com/blog/in-depth-look-hbase-architecture/assets/blogimages/HBaseArchitecture-Blog-Fig13.png)

## HBase HFile Structure

An HFile contains a multi-layered index which allows HBase to seek to the data without having to read the whole file. The multi-level index is like a b+tree:

- Key value pairs are stored in increasing order
- Indexes point by row key to the key value data in 64KB “blocks”
- Each block has its own leaf-index
- The last key of each block is put in the intermediate index
- The root index points to the intermediate index

The trailer points to the meta blocks, and is written at the end of persisting the data to the file. The trailer also has information like bloom filters and time range info. Bloom filters help to skip files that do not contain a certain row key. The time range info is useful for skipping the file if it is not in the time range the read is looking for.

![img](https://mapr.com/blog/in-depth-look-hbase-architecture/assets/blogimages/HBaseArchitecture-Blog-Fig14.png)

## HFile Index

The index, which we just discussed, is loaded when the HFile is opened and kept in memory. This allows lookups to be performed with a single disk seek.

![img](https://mapr.com/blog/in-depth-look-hbase-architecture/assets/blogimages/HBaseArchitecture-Blog-Fig15.png)

## HBase Read Merge

We have seen that the KeyValue cells corresponding to one row can be in multiple places, row cells already persisted are in Hfiles, recently updated cells are in the MemStore, and recently read cells are in the Block cache. So when you read a row, how does the system get the corresponding cells to return? A Read merges Key Values from the block cache, MemStore, and HFiles in the following steps:

1. First, the scanner looks for the Row cells in the Block cache - the read cache. Recently Read Key Values are cached here, and Least Recently Used are evicted when memory is needed.
2. Next, the scanner looks in the MemStore, the write cache in memory containing the most recent writes.
3. If the scanner does not find all of the row cells in the MemStore and Block Cache, then HBase will use the Block Cache indexes and bloom filters to load HFiles into memory, which may contain the target row cells.

![img](https://mapr.com/blog/in-depth-look-hbase-architecture/assets/blogimages/HBaseArchitecture-Blog-Fig16.png)

## HBase Read Merge

As discussed earlier, there may be many HFiles per MemStore, which means for a read, multiple files may have to be examined, which can affect the performance. This is called read amplification.

![img](https://mapr.com/blog/in-depth-look-hbase-architecture/assets/blogimages/HBaseArchitecture-Blog-Fig17.png)

## HBase Minor Compaction

HBase will automatically pick some smaller HFiles and rewrite them into fewer bigger Hfiles. This process is called minor compaction. Minor compaction reduces the number of storage files by rewriting smaller files into fewer but larger ones, performing a merge sort.

![img](https://mapr.com/blog/in-depth-look-hbase-architecture/assets/blogimages/HBaseArchitecture-Blog-Fig18.png)

## HBase Major Compaction

Major compaction merges and rewrites all the HFiles in a region to one HFile per column family, and in the process, drops deleted or expired cells. This improves read performance; however, since major compaction rewrites all of the files, lots of disk I/O and network traffic might occur during the process. This is called write amplification.

Major compactions can be scheduled to run automatically. Due to write amplification, major compactions are usually scheduled for weekends or evenings. Note that MapR Database has made improvements and does not need to do compactions. A major compaction also makes any data files that were remote, due to server failure or load balancing, local to the region server.

![img](https://mapr.com/blog/in-depth-look-hbase-architecture/assets/blogimages/HBaseArchitecture-Blog-Fig19.png)

## Region = Contiguous Keys

Let’s do a quick review of regions:

- A table can be divided horizontally into one or more regions. A region contains a contiguous, sorted range of rows between a start key and an end key
- Each region is 1GB in size (default)
- A region of a table is served to the client by a RegionServer
- A region server can serve about 1,000 regions (which may belong to the same table or different tables)

![img](https://mapr.com/blog/in-depth-look-hbase-architecture/assets/blogimages/HBaseArchitecture-Blog-Fig20.png)

## Region Split

Initially there is one region per table. When a region grows too large, it splits into two child regions. Both child regions, representing one-half of the original region, are opened in parallel on the same Region server, and then the split is reported to the HMaster. For load balancing reasons, the HMaster may schedule for new regions to be moved off to other servers.

![img](https://mapr.com/blog/in-depth-look-hbase-architecture/assets/blogimages/HBaseArchitecture-Blog-Fig21.png)

## Read Load Balancing

Splitting happens initially on the same region server, but for load balancing reasons, the HMaster may schedule for new regions to be moved off to other servers. This results in the new Region server serving data from a remote HDFS node until a major compaction moves the data files to the Regions server’s local node. HBase data is local when it is written, but when a region is moved (for load balancing or recovery), it is not local until major compaction.

![img](https://mapr.com/blog/in-depth-look-hbase-architecture/assets/blogimages/HBaseArchitecture-Blog-Fig22.png)

## HDFS Data Replication

All writes and Reads are to/from the primary node. HDFS replicates the WAL and HFile blocks. HFile block replication happens automatically. HBase relies on HDFS to provide the data safety as it stores its files. When data is written in HDFS, one copy is written locally, and then it is replicated to a secondary node, and a third copy is written to a tertiary node.

![img](https://mapr.com/blog/in-depth-look-hbase-architecture/assets/blogimages/HBaseArchitecture-Blog-Fig23.png)

## HDFS Data Replication (2)

The WAL file and the Hfiles are persisted on disk and replicated, so how does HBase recover the MemStore updates not persisted to HFiles? See the next section for the answer.

![img](https://mapr.com/blog/in-depth-look-hbase-architecture/assets/blogimages/HBaseArchitecture-Blog-Fig24.png)

## HBase Crash Recovery

When a RegionServer fails, Crashed Regions are unavailable until detection and recovery steps have happened. Zookeeper will determine Node failure when it loses region server heart beats. The HMaster will then be notified that the Region Server has failed.

When the HMaster detects that a region server has crashed, the HMaster reassigns the regions from the crashed server to active Region servers. In order to recover the crashed region server’s memstore edits that were not flushed to disk. The HMaster splits the WAL belonging to the crashed region server into separate files and stores these file in the new region servers’ data nodes. Each Region Server then replays the WAL from the respective split WAL, to rebuild the memstore for that region.

![img](https://mapr.com/blog/in-depth-look-hbase-architecture/assets/blogimages/HBaseArchitecture-Blog-Fig25.png)

## Data Recovery

WAL files contain a list of edits, with one edit representing a single put or delete. Edits are written chronologically, so, for persistence, additions are appended to the end of the WAL file that is stored on disk.

What happens if there is a failure when the data is still in memory and not persisted to an HFile? The WAL is replayed. Replaying a WAL is done by reading the WAL, adding and sorting the contained edits to the current MemStore. At the end, the MemStore is flush to write changes to an HFile.

![img](https://mapr.com/blog/in-depth-look-hbase-architecture/assets/blogimages/HBaseArchitecture-Blog-Fig26.png)

## Apache HBase Architecture Benefits

HBase provides the following benefits:

- **Strong consistency model**

  \- When a write returns, all readers will see same value

- **Scales automatically**

  \- Regions split when data grows too large

  \- Uses HDFS to spread and replicate data

- **Built-in recovery**

  \- Using Write Ahead Log (similar to journaling on file system)

- **Integrated with Hadoop**

  \- MapReduce on HBase is straightforward

## Apache HBase Has Problems Too…

- **Business continuity reliability:**

  \- WAL replay slow

  \- Slow complex crash recovery

  \- Major Compaction I/O storms

## MapR Database with MapR XD does not have these problems

The diagram below compares the application stacks for Apache HBase on top of HDFS on the left, Apache HBase on top of MapR's read/write file system MapR XD in the middle, and MapR Database and MapR XD in a Unified Storage Layer on the right.

![img](https://mapr.com/blog/in-depth-look-hbase-architecture/assets/blogimages/HBaseArchitecture-Blog-Fig27.png)

MapR Database exposes the same HBase API and the [Data model](https://mapr.com/blog/in-depth-look-hbase-architecture/#training-module_dev320-mod2) for MapR Database is the same as for Apache HBase. However the MapR Database implementation integrates table storage into the MapR Distributed File and Object Store, eliminating all JVM layers and interacting directly with disks for both file and table storage.

[MapR Academy
Apache HBase Data Model and Architecture](https://mapr.com/training/on-demand/dev-320-apache-hbase-data-model-and-architecture/#2-apache-hbase-data-model)

[Explore the **FREE Course**](https://mapr.com/training/on-demand/dev-320-apache-hbase-data-model-and-architecture/#2-apache-hbase-data-model)

If you need to learn more about the *HBase data model*, we have 4 lessons that will help you. Module 2 of the **FREE course**, [DEV 320 - Apache HBase Data Model and Architecture](https://mapr.com/training/on-demand/dev-320-apache-hbase-data-model-and-architecture/#2-apache-hbase-data-model) will walk you through everything you need to know.

MapR Database offers many benefits over HBase, while maintaining the virtues of the HBase API and the idea of data being sorted according to primary key. MapR Database provides operational benefits such as no compaction delays and automated region splits that do not impact the performance of the database. The tables in MapR Database can also be isolated to certain machines in a cluster by utilizing the topology feature of MapR. The final differentiator is that MapR Database is just plain fast, due primarily to the fact that it is tightly integrated into the MapR Distributed File and Object Store itself, rather than being layered on top of a distributed file system that is layered on top of a conventional file system.

## Key differences between MapR Database and Apache HBase

![img](https://mapr.com/blog/in-depth-look-hbase-architecture/assets/blogimages/HBaseArchitecture-Blog-Fig28.png)

- Tables part of the MapR Read/Write File system
  - Guaranteed data locality
- Smarter load balancing
  - Uses container Replicas
- Smarter fail over
  - Uses container replicas
- Multiple small WALs
  - Faster recovery
- Memstore Flushes Merged into Read/Write File System
  - No compaction !

[You can take this free On Demand training to learn more about MapR XD and MapR Database](https://mapr.com/training/hadoop-demand-training/hde-110)

In this blog post, you learned more about the HBase architecture and its main benefits over NoSQL data store solutions. If you have any questions about HBase, please ask them in the comments section below.

## Want to learn more?

- [MapR provides Complete HBase Certification Curriculum as part of Free Hadoop On-Demand Training](https://mapr.com/training/courses/)
- [Installing HBase on MapR](http://doc.mapr.com/display/MapR/HBase)
- [Getting Started with HBase on MapR](http://doc.mapr.com/display/MapR/Working+with+HBase)
- [Release notes for HBase on MapR](http://doc.mapr.com/display/components/HBase+Release+Notes)
- [Apache HBase docs](https://issues.apache.org/jira/browse/HBASE/?report=com.atlassian.jira.jira-projects-plugin:roadmap-panel&selectedTab=com.atlassian.jira.jira-projects-plugin:summary-panel)
- [HBase: The Definitive Guide, by Lars George](http://shop.oreilly.com/product/0636920014348.do)

######  This blog post was published August 07, 2015.

# [深入理解 Hbase 架构（翻译）](https://segmentfault.com/a/1190000019959411)

[hbase](https://segmentfault.com/t/hbase)[大数据](https://segmentfault.com/t/大数据)[系统架构](https://segmentfault.com/t/系统架构)[分布式系统](https://segmentfault.com/t/分布式系统)

发布于 2019-08-03  约 23 分钟

![img](https://sponsor.segmentfault.com/lg.php?bannerid=0&campaignid=0&zoneid=25&loc=https%3A%2F%2Fsegmentfault.com%2Fa%2F1190000019959411&referer=https%3A%2F%2Fsegmentfault.com%2Fa%2F1190000019959411&cb=cf83532124)

最近在网上看到一篇很好的讲 HBase 架构的文章（原文在[这里](https://mapr.com/blog/in-depth-look-hbase-architecture/)），简洁明了，图文并茂，所以这里将其翻译成中文分享。图片引用的是原文中的，技术性术语会尽量使用英文，在比较重要的段落后面都会加上我个人理解的点评。

### **HBase 架构组件**

物理上，Hbase 是由三种类型的 server 组成的的**主从式**（master-slave）架构：

- **Region Server** 负责处理数据的读写请求，客户端请求数据时直接和 Region Server 交互。
- **HBase Master** 负责 Region 的分配，DDL（创建，删除 table）等操作。
- **Zookeeper**，作为 HDFS 的一部分，负责维护集群状态。

当然底层的存储都是基于 Hadoop HDFS 的：

- Hadoop **DataNode** 负责存储 Region Server 所管理的数据。所有的 HBase 数据都存储在 HDFS 文件中。Region Server 和 HDFS DataNode 往往是分布在一起的，这样 Region Server 就能够实现**数据本地化**（data locality，即将数据放在离需要者尽可能近的地方）。HBase 的数据在写的时候是本地的，但是当 region 被迁移的时候，数据就可能不再满足本地性了，直到完成 **compaction**，才能又恢复到本地。
- Hadoop **NameNode** 维护了所有 HDFS 物理 data block 的元信息。

![图片描述](https://segmentfault.com/img/bVbvOFz?w=632&h=343)

### **Regions**

HBase **表**（**Table**）根据 **rowkey** 的范围被**水平拆分**成若干个 **region**。每个 region 都包含了这个region 的 **start key** 和 **end key** 之间的所有**行**（**row**）。Regions 被分配给集群中的某些节点来管理，即 **Region Server**，由它们来负责处理数据的读写请求。每个 Region Server 大约可以管理 1000 个 regions。

![图片描述](https://segmentfault.com/img/bVbvOFR?w=724&h=337)

### **HBase Master**

也叫 **HMaster**，负责 Region 的分配，DDL（创建，删除表）等操作：

统筹协调所有 region server：

- 启动时分配 regions，在故障恢复和负载均衡时**重分配** regions
- 监控集群中所有 Region Server 实例（从 Zookeeper 获取通知信息）

管理员功能：

- 提供创建，删除和更新 HBase Table 的接口

![图片描述](https://segmentfault.com/img/bVbvOGh?w=722&h=367)

### **Zookeeper**

HBase 使用 Zookeeper 做分布式管理服务，来维护集群中所有服务的状态。Zookeeper 维护了哪些 servers 是健康可用的，并且在 server 故障时做出通知。Zookeeper 使用一致性协议来保证分布式状态的一致性。注意这需要三台或者五台机器来做一致性协议。

![图片描述](https://segmentfault.com/img/bVbvOGw?w=703&h=318)

### **这些组件是如何一起工作的**

Zookeeper 用来协调分布式系统中集群状态信息的共享。Region Servers 和 在线 HMaster（active HMaster）和 Zookeeper 保持会话（session）。Zookeeper 通过心跳检测来维护所有**临时节点**（ephemeral nodes）。

![图片描述](https://segmentfault.com/img/bVbvOH4?w=722&h=329)

每个 Region Server 都会创建一个 ephemeral 节点。HMaster 会监控这些节点来发现可用的 Region Servers，同样它也会监控这些节点是否出现故障。

HMaster 们会竞争创建 ephemeral 节点，而 Zookeeper 决定谁是第一个作为在线 HMaster，保证线上只有一个 HMaster。在线 HMaster（**active HMaster**） 会给 Zookeeper 发送心跳，不在线的待机 HMaster （**inactive HMaster**） 会监听 active HMaster 可能出现的故障并随时准备上位。

如果有一个 Region Server 或者 HMaster 出现故障或各种原因导致发送心跳失败，它们与 Zookeeper 的 session 就会过期，这个 ephemeral 节点就会被删除下线，监听者们就会收到这个消息。Active HMaster 监听的是 region servers 下线的消息，然后会恢复故障的 region server 以及它所负责的 region 数据。而 Inactive HMaster 关心的则是 active HMaster 下线的消息，然后竞争上线变成 active HMaster。

（点评：这一段非常重要，涉及到分布式系统设计中的一些核心概念，包括集群状态、一致性等。可以看到 **Zookeeper** 是沟通一切的桥梁，所有的参与者都和 Zookeeper 保持心跳会话，并从 Zookeeper 获取它们需要的集群状态信息，来管理其它节点，转换角色，这也是分布式系统设计中很重要的思想，由专门的服务来维护分布式集群状态信息。）

### **第一次读和写操作**

有一个特殊的 HBase Catalog 表叫 **Meta table**（它其实是一张特殊的 HBase 表），包含了集群中所有 regions 的位置信息。Zookeeper 保存了这个 Meta table 的位置。

当 HBase 第一次读或者写操作到来时：

- 客户端从 Zookeeper 那里获取是哪一台 Region Server 负责管理 Meta table。
- 客户端会查询那台管理 Meta table 的 Region Server，进而获知是哪一台 Region Server 负责管理本次数据请求所需要的 rowkey。客户端会缓存这个信息，以及 Meta table 的位置信息本身。
- 然后客户端回去访问那台 Region Server，获取数据。

对于以后的的读请求，客户端从可以缓存中直接获取 Meta table 的位置信息（在哪一台 Region Server 上），以及之前访问过的 rowkey 的位置信息（哪一台 Region Server 上），除非因为 Region 被迁移了导致缓存失效。这时客户端会重复上面的步骤，重新获取相关位置信息并更新缓存。

![图片描述](https://segmentfault.com/img/bVbvOIA?w=590&h=356)

（点评：客户端读写数据，实际上分了两步：第一步是定位，从 Meta table 获取 rowkey 属于哪个 Region Server 管理；第二步再去相应的 Region Server 读写数据。这里涉及到了两个 Region Server，要理解它们各自的角色功能。关于 Meta table 下面会详细介绍。）

### **HBase Meta Table**

Meta table 是一个特殊的 HBase table，它保存了系统中所有的 region 列表。这张 table 类似一个 b-tree，结构大致如下：

- Key：table, region start key, region id
- Value：region server

![图片描述](https://segmentfault.com/img/bVbvOIR?w=736&h=362)

### **Region Server 组成**

Region Server 运行在 HDFS DataNode 上，由以下组件组成：

- **WAL**：Write Ahead Log 是分布式文件系统上的一个文件，用于**存储新的还未被持久化存储的数据**，它被用来做故障恢复。
- **BlockCache**：这是读缓存，在**内存**中存储了最常访问的数据，是 LRU（Least Recently Used）缓存。
- **MemStore**：这是写缓存，在**内存**中存储了新的还未被持久化到硬盘的数据。当被写入硬盘时，数据会首先被排序。注意每个 Region 的每个 Column Family 都会有一个 MemStore。
- **HFile** 在硬盘上（HDFS）存储 HBase 数据，以**有序 KeyValue** 的形式。

![图片描述](https://segmentfault.com/img/bVbvOI1?w=675&h=366)

（点评：这一段是重中之重，理解 Region Server 的组成对理解 HBase 的架构至关重要，要充分认识 Region Server 的功能，以及每个组件的作用，这些组件的行为和功能在后续的段落中都会一一展开。）

### **HBase 写数据步骤**

当客户端发起一个写数据请求（Put 操作），第一步首先是将数据写入到 WAL 中：

- 新数据会被追加到 WAL 文件尾部。
- WAL 用来在故障恢复时恢复还未被持久化的数据。

![图片描述](https://segmentfault.com/img/bVbvPRO?w=716&h=363)

数据被写入 WAL 后，会被加入到 MemStore 即写缓存。然后服务端就可以向客户端返回 ack 表示写数据完成。

（点评：注意数据写入时 WAL 和 MemStore 更新的顺序，不能调换，必须先 WAL 再 MemStore。如果反过来，先更新完 MemStore，此时 Region Server 发生 crash，内存中的更新就丢失了，而此时数据还未被持久化到 WAL，就无法恢复了。理论上 WAL 就是 MemStore 中数据的一个镜像，应该保持一致，除非发生系统 crash。另外注意更新 WAL 是在文件尾部追加的方式，这种磁盘操作性能很高，不会太影响请求的整体响应时间。）

![图片描述](https://segmentfault.com/img/bVbvPRS?w=664&h=298)

### **HBase MemStore**

MemStore 在内存中缓存 HBase 的数据更新，以有序 KeyValues 的形式，这和 HFile 中的存储形式一样。每个 Column Family 都有一个 MemStore，所有的更新都以 Column Family 为单位进行排序。

![图片描述](https://segmentfault.com/img/bVbvPRZ?w=719&h=351)

### **HBase Region Flush**

MemStore 中累积了足够多的的数据后，整个有序数据集就会被写入一个新的 HFile 文件到 HDFS 上。HBase 为每个 Column Family 都创建一个 HFile，里面存储了具体的 **Cell**，也即 KeyValue 数据。随着时间推移，HFile 会不断产生，因为 KeyValue 会不断地从 MemStore 中被刷写到硬盘上。

注意这也是为什么 HBase 要限制 Column Family 数量的一个原因。每个 Column Family 都有一个 MemStore；如果一个 MemStore 满了，所有的 MemStore 都会被刷写到硬盘。同时它也会记录最后写入的数据的**最大序列号**（**sequence number**），这样系统就能知道目前为止哪些数据已经被持久化了。

最大序列号是一个 meta 信息，被存储在每个 HFile 中，来表示持久化进行到哪条数据了，应该从哪里继续。当 region 启动时，这些序列号会被读取，取其中最大的一个，作为基础序列号，后面的新的数据更新就会在该值的基础上递增产生新的序列号。

![图片描述](https://segmentfault.com/img/bVbvPTq?w=622&h=248)

（点评：这里有个序列号的概念，每次 HBase 数据更新都会绑定一个新的自增序列号。而每个 HFile 则会存储它所保存的数据的最大序列号，这个元信息非常重要，它相当于一个 commit point，告诉我们在这个序列号之前的数据已经被持久化到硬盘了。它不仅在 region 启动时会被用到，在故障恢复时，也能告诉我们应该从 WAL 的什么位置开始回放数据的历史更新记录。）

### **HBase HFile**

数据存储在 HFile 中，以 Key/Value 形式。当 MemStore 累积了足够多的数据后，整个有序数据集就会被写入一个新的 HFile 文件到 HDFS 上。整个过程是一个顺序写的操作，速度非常快，因为它不需要移动磁盘头。（注意 HDFS 不支持随机修改文件操作，但支持 append 操作。）

![图片描述](https://segmentfault.com/img/bVbvROk?w=698&h=351)

### HBase HFile 文件结构

HFile 使用多层索引来查询数据而不必读取整个文件，这种多层索引类似于一个 B+ tree：

- KeyValues 有序存储。
- rowkey 指向 index，而 index 则指向了具体的 data block，以 64 KB 为单位。
- 每个 block 都有它的叶索引。
- 每个 block 的最后一个 key 都被存储在中间层索引。
- 索引根节点指向中间层索引。

trailer 指向原信息数据块，它是在数据持久化为 HFile 时被写在 HFile 文件尾部。trailer 还包含例如布隆过滤器和时间范围等信息。布隆过滤器用来跳过那些不包含指定 rowkey 的文件，时间范围信息则是根据时间来过滤，跳过那些不在请求的时间范围之内的文件。

![图片描述](https://segmentfault.com/img/bVbvRO0?w=688&h=349)

### **HFile 索引**

刚才讨论的索引，在 HFile 被打开时会被载入内存，这样数据查询只要一次硬盘查询。

![图片描述](https://segmentfault.com/img/bVbvRO4?w=772&h=341)

### **HBase Read 合并**

我们已经发现，每行（**row**）的 KeyValue cells 可能位于不同的地方，这些 cell 可能被写入了 HFile，可能是最近刚更新的，还在 MemStore 中，也可能最近刚读过，缓存在 Block Cache 中。所以，当你读一行 row 时，系统怎么将对应的 cells 返回呢？一次 read 操作会将 Block Cache，MemStore 和 HFile 中的 cell 进行合并：

- 首先 scanner 从 Block Cache 读取 cells。最近读取的 KeyValue 都被缓存在这里，这是 一个 LRU 缓存。
- 然后 scanner 读取 MemStore，即写缓存，包含了最近更新的数据。
- 如果 scanner 没有在 BlockCache 和 MemStore 都没找到对应的 cells，则 HBase 会使用 Block Cache 中的索引和布隆过滤器来加载对应的 HFile 到内存，查找到请求的 row cells。

![图片描述](https://segmentfault.com/img/bVbvRP4?w=769&h=278)

之前讨论过，每个 MemStore 可能会有多个 HFile，所以一次 read 请求可能需要多读个文件，这可能会影响性能，这被称为**读放大**（**read amplification**）。

（点评：从时间轴上看，一个个的 HFile 也是有序的，本质上它们保存了每个 region 的每个 column family 的数据历史更新。所以对于同一个 rowkey 的同一个 cell，它可能也有多个版本的数据分布在不同的 HFile 中，所以可能需要读取多个 HFiles，这样性能开销会比较大，尤其是当不满足 data locality 时这种 read amplification 情况会更加严重。这也是后面会讲到的 **compaction** 必要的原因）

![图片描述](https://segmentfault.com/img/bVbvRQf?w=596&h=307)

### **HBase Minor Compaction**

HBase 会自动合并一些小的 HFile，重写成少量更大的 HFiles。这个过程被称为 **minor compaction**。它使用归并排序算法，将小文件合并成大文件，有效减少 HFile 的数量。

![图片描述](https://segmentfault.com/img/bVbvRQB?w=723&h=329)

### **HBase Major Compaction**

**Major Compaction** 合并重写每个 Column Family 下的所有的 HFiles，成为一个单独的大 HFile，在这个过程中，被删除的和过期的 cell 会被真正从物理上删除，这能提高读的性能。但是因为 major compaction 会重写所有的 HFile，会产生大量的硬盘 I/O 和网络开销。这被称为**写放大**（**Write Amplification**）。

Major compaction 可以被设定为自动调度。因为存在 write amplification 的问题，major compaction 一般都安排在周末和半夜。MapR 数据库对此做出了改进，并不需要做 compaction。Major compaction 还能将因为服务器 crash 或者负载均衡导致的数据迁移重新移回到离 Region Server 的地方，这样就能恢复 **data locality**。

![图片描述](https://segmentfault.com/img/bVbvRQ5?w=653&h=339)

### **Region = Contiguous Keys**

我们再来回顾一下 region 的概念：

- HBase Table 被水平切分成一个或数个 regions。每个 region 包含了连续的，有序的一段 rows，以 start key 和 end key 为边界。
- 每个 region 的默认大小为 1GB。
- region 里的数据由 Region Server 负责读写，和 client 交互。
- 每个 Region Server 可以管理约 1000 个 regions（它们可能来自一张表或者多张表）。

![图片描述](https://segmentfault.com/img/bVbvRRd?w=653&h=334)

### **Region 分裂**

一开始每个 table 默认只有一个 region。当一个 region 逐渐变得很大时，它会分裂（**split**）成两个子 region，每个子 region 都包含了原来 region 一半的数据，这两个子 region 并行地在原来这个 region server 上创建，这个分裂动作会被报告给 HMaster。处于负载均衡的目的，HMaster 可能会将新的 region 迁移给其它 region server。

![图片描述](https://segmentfault.com/img/bVbvRRk?w=675&h=361)

### **Read 负载均衡**

**Splitting** 一开始是发生在同一台 region server 上的，但是出于负载均衡的原因，HMaster 可能会将新的 regions **迁移**给它 region server，这会导致那些 region server 需要访问离它比较远的 HDFS 数据，直到 **major compaction** 的到来，它会将那些远方的数据重新移回到离 region server 节点附近的地方。

（点评：注意这里的迁移的概念，只是逻辑上的迁移，即将某个 region 交给另一个 region server 管理。）

![图片描述](https://segmentfault.com/img/bVbvRRO?w=714&h=358)

### **HDFS 数据备份**

所有的读写都发生在 HDFS 的主 DataNode 节点上。 HDFS 会自动备份 WAL 和 HFile 的文件 blocks。HBase 依赖于 HDFS 来保证数据完整安全。当数据被写入 HDFS 时，一份会写入本地节点，另外两个备份会被写入其它节点。

![图片描述](https://segmentfault.com/img/bVbvUu3?w=641&h=302)

WAL 和 HFiles 都会持久化到硬盘并备份。那么 HBase 是怎么恢复 MemStore 中还未被持久化到 HFile 的数据呢？下面的章节会讨论这个问题。

![图片描述](https://segmentfault.com/img/bVbvUvd?w=679&h=349)

### **HBase 故障恢复**

当某个 Region Server 发生 crash 时，它所管理的 region 就无法被访问了，直到 crash 被检测到，然后故障恢复完成，这些 region 才能恢复访问。Zookeeper 依靠心跳检测发现节点故障，然后 HMaster 会收到 region server 故障的通知。

当 HMaster 发现某个 region server 故障，HMaster 会将这个 region server 所管理的 regions 分配给其它健康的 region servers。为了恢复故障的 region server 的 MemStore 中还未被持久化到 HFile 的数据，HMaster 会将 WAL 分割成几个文件，将它们保存在新的 region server 上。每个 region server 然后回放各自拿到的 WAL 碎片中的数据，来为它所分配到的新 region 建立 MemStore。

![图片描述](https://segmentfault.com/img/bVbvUvO?w=708&h=368)

WAL 包含了一系列的修改操作，每个修改都表示一个 put 或者 delete 操作。这些修改按照时间顺序依次写入，持久化时它们被依次写入 WAL 文件的尾部。

当数据仍然在 MemStore 还未被持久化到 HFile 怎么办呢？WAL 文件会被回放。操作的方法是读取 WAL 文件，排序并添加所有的修改记录到 MemStore，最后 MemStore 会被刷写到 HFile。

![图片描述](https://segmentfault.com/img/bVbvUv7?w=724&h=378)

（点评：故障恢复是 HBase 可靠性保障的一个重要特性。WAL 在这里扮演了关键角色，在分割 WAL 时，数据会根据 region 分配到对应的新的 region server 上，然后 region server 负责回放这一部分数据到 MemStore 中。）

### **Apache HBase 架构的优点**

- 强一致性：

  ```
  - 当 write 返回时，所有的 reader 都会读到同样的值。
  ```

- 自动扩展性

  ```
  - 数据变大时 region 会分裂。
  - 使用 HDFS 存储备份数据。
  ```

- 内置恢复功能

  ```
  - 使用 Write Ahead Log （类似于文件系统中的日志）
  ```

- 与 Hadoop 结合：

  ```
  - 使用 MapReduce 处理 HBase 数据会非常直观。
  ```

### **Apache HBase 也有问题**

- 业务持续可靠性：

  ```
  - WAL 回放很慢。
  - 故障恢复很慢。
  - Major Compaction 时候 I/O 会飙升。
  ```