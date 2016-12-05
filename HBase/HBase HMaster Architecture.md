## [HBase HMaster Architecture](http://blog.zahoor.in/2012/08/hbase-hmaster-architecture/)

August 10, 2012, 7:22 am

HBase architecture follows the traditional master slave model where you have a master which takes decisions and one or more slaves which does the real task. In HBase, the master is called HMaster  and slaves are called HRegionServers (yes..servers). In this post i will zoom in to HMaster and will detail some of the modules and functionality of HMaster.

 Lets zoom in to HMaster and discuss different modules present in that.[![HMaster and its Components](http://blog.zahoor.in/wp-content/uploads/2012/08/Screen-Shot-2012-08-06-at-3.32.49-PM.png)](http://blog.zahoor.in/wp-content/uploads/2012/08/Screen-Shot-2012-08-06-at-3.32.49-PM.png)


**Note: Most of the descriptions are taken from javadocs of HBase project. So all credits goes to the developers who wrote it in the first place.**

The above diagram shows the internals of HMaster. I have grouped the modules in to different categories for us to make it a little easy.

## 1 ) External Interfaces
External Interfaces are responsible for interacting with the external world (Hmaster web site, client, Region Servers and other management utilities like JConsole).

### Info Server
Info server is an embedded jetty server instance started by HMaster to answer http requests (default port is 60010). The primary goal is to serve up status information for the server. There are three contexts:

- “/stacks/” -> points to stack trace
- “/static/” -> points to common static files (src/hbase-webapps/static)
- “/” -> the jsp server code from (src/hbase-webapps/<name>)

### RPC Server
HBase rpc server module instantiates the configured RPC Engine which is responsible for all the rpc communication that the master does. There are atleast two different RPC Engines in hbase now. One is the good old WritableRPCEngine (Which is the default) and the other one is the ProtocolBufferRPCEngine. Whichever is selected, they maintain 3 different interfaces/protocols to which they respond. In master, the supported protocols are **MasterMonitorProtocol**, **MasterAdminProtocol** and **RegionServerStatusProtocol**. More about protocols in my earlier post [here](http://blog.zahoor.in/2012/08/protocol-buffers-in-hbase/).

[![Hbase RPC Server](http://blog.zahoor.in/wp-content/uploads/2012/08/Screen-Shot-2012-08-08-at-1.28.34-PM-300x123.png)](http://blog.zahoor.in/wp-content/uploads/2012/08/Screen-Shot-2012-08-08-at-1.28.34-PM.png)

### Master MXBean
 Apart from standard HBase metrics, hbase supports Java Management Extension based metric export. You can use any standard JMX compliant browser like JConsole and can view the metrics. To enable JMX based remote metrics monitoring in hbase follow the instructions [here](http://hbase.apache.org/metrics.html)

## 2 ) Executor Services
A generic executor service abstracts a Event Queue where Events of different types can be posted. The events are handled by their respective Runnable handlers which pick threads from a dedicated thread pool. In order to create a new service, create an instance of this class and then do: instance.startExecutorService(“myService”).  When done call shutdown(). In order to use the service created above, call submit(EventHandler). Register pre- and post- processing listeners by registering your implementation of EventHandler.EventHandlerListener with registerListener(EventHandler.EventType, EventHandler.EventHandlerListener).  Be sure to deregister your listener when done via unregisterListener(EventHandler.EventType). The services mentioned below are instantiated using this generic  executor service. The events and Event handlers are described below for each of the service.

[![Hbase Executor Service](http://blog.zahoor.in/wp-content/uploads/2012/08/Screen-Shot-2012-08-08-at-1.29.07-PM-300x269.png)](http://blog.zahoor.in/wp-content/uploads/2012/08/Screen-Shot-2012-08-08-at-1.29.07-PM.png)

### Open Region Service (MASTER_OPEN_REGION)
When the master (i.e. Assignment Manager) detects that a region was successfully opened (<u>through zookeeper watch)</u>, it posts a event of type `RS_ZK_REGION_OPENED` to this service. This event is handled by the event handler `OpenedRegionHandler()`.

### Close Region Service (MASTER_CLOSE_REGION)

When the master (i,e, Assignment Manager) detects that a region was successfully closed (through a watcher), it posts a event of type `RS_ZK_REGION_CLOSED` to this service. Also when a region open attempt fails an event of type `RS_ZK_REGION_FAILED_OPEN` is posted. These events are handled by the event handler `ClosedRegionHandler()`.

### Server Operations Service (MASTER_SERVER_OPERATIONS)
Master detects a region split through zookeeper watch and posts RS_ZK_REGION_SPLIT which is handled by SplitRegionHandler. Also when a master needs to expire a region server (which does not host ROOT or META) it posts an event M_SERVER_SHUTDOWN which is handled by an event handler ServerShutdownHandler.

### Meta Server Operations Service (MASTER_META_SERVER_OPERATIONS)
when a master needs to expire a region server which hosts ROOT or META, it posts an event M_META_SERVER_SHUTDOWN which is handled by an event handler MetaServerShutdownHandler.

### Table Operations Service (MASTER_TABLE_OPERATIONS)
 All the table operations originating from the client is handled in this service. Message like C_M_DELETE_TABLE, CM_DISABLE_TABLE, C_M_ENABLE_TABLE, C_M_MODIFY_TABLE and C_M_CREATE_TABLE are posted from the client and handled by the handler DeleteTableHandler, DisableTableHandler, EnableTableHandler, ModifyTableHandler and CreateTableHandler respectivily.

| **Executor Service**              | **Event**                                | **Event Handler**                        | **Threads****(Default)** |
| --------------------------------- | ---------------------------------------- | ---------------------------------------- | ------------------------ |
| **Master Open Region**            | RS_ZK_REGION_OPENED                      | OpenRegionHandler                        | 5                        |
| **Master Close Region**           | RS_ZK_REGION_CLOSED                      | ClosedRegionHandler                      | 5                        |
| **Master Server Operations**** ** | RS_ZK_REGION_SPLITM_SERVER_SHUTDOWN      | SplitRegionHandlerServerShutdownHandler  | 3                        |
| **Master Meta Server Operations** | M_META_SERVER_SHUTDOWN                   | MetaServerShutdownHandler                | 5                        |
| **Master Table Operations**** **  | C_M_DELETE_TABLE C_M_DISABLE_TABLE C_M_ENABLE_TABLE C_M_MODIFY_TABLE C_M_CREATE_TABLE | DeleteTableHandler DisableTableHandler EnableTableHandler ModifyTableHandler CreateTableHandler | 1                        |

## 3 ) Zookeeper System Trackers
Master and RS uses zookeeper to keep track of certain events and happenings in the cluster. In Master, a centralized class called **ZookeeperWatcher** acts as a proxy for any event tracker which uses zookeeper. All the common things like connection handling, node management and exceptions are handled here. Any tracker which needs the service of this call must register with this class to get notified of any specific event.

[![Zookeeper Based Trackers](http://blog.zahoor.in/wp-content/uploads/2012/08/Screen-Shot-2012-08-10-at-10.32.58-AM-300x211.png)](http://blog.zahoor.in/wp-content/uploads/2012/08/Screen-Shot-2012-08-10-at-10.32.58-AM.png)Zookeeper Based Trackers

### Active Master Manager
Handles everything on master side related to master election. This is the place where the backup masters block, until the active master fails or the cluster shuts down. Listens and responds to ZooKeeper notifications on the master znode, both nodeCreated and nodeDeleted. Uses a zNode called “master” under the base zNode.

### Region Server Tracker
Watches the zNode called “rs” under the base zNode. If any children is added (i.e. if any Region server comes up) or deleted (if any Region server goes down) this class gets notified. The main function of this class is to maintain a active list of online Region Servers. If any region server fails it triggers the serverExpiry procedure.

### Draining Server Tracker
Tracks the list of draining region servers via ZK. This class is responsible for watching for changes to the draining servers list.  It handles adds/deletes in the draining RS list and watches each node. If an RS gets deleted from draining list, we call ServerManager#removeServerFromDrainList(ServerName) If an RS gets added to the draining list, we add a watcher to it and call ServerManager#addServerToDrainList(ServerName). Uses the zNode called “draining” under the base zNode.

### Catalog Tracker
Tracks the availability of the catalog tables -ROOT and .META. This class is “read-only” in that the locations of the catalog tables cannot be explicitly set.  Instead, ZooKeeper is used to learn of the availability and location of -ROOT. -ROOT is used to learn of the location of .META. If not available in -ROOT, ZooKeeper is used to monitor for a new location of .META..Call  #start() to start up operation.  Call #stop() to interrupt waits and close up shop.

### Cluster Status Tracker
Used to monitor the cluster status using the zNode “shutdown”. It just says wether the cluster is up or down for now.

### Assignment Manager
Manages and performs region assignment. Monitors ZooKeeper for events related to regions in transition. Handles existing regions in transition during master failover.

### Root Region Tracker
Tracks the root region server location node in zookeeper (zNode is “root-region-server”). Root region location is set by RootLocationEditor usually called out of RegionServerServices. This class has a watcher on the root location and notices changes. Mainly used to know if the root region is available and where is it hosted.

### Load Balancer
Makes decisions about the placement and movement of Regions across RegionServers. Cluster-wide load balancing will occur only when there are no regions in transition and according to a fixed period of a time using  #balanceCluster(Map). Inline region placement with {@link #immediateAssignment} can be used when the Master needs to handle closed regions that it currently does not have a destination set for.  This can happen during master failover. On cluster startup, bulk assignment can be used to determine locations for all Regions in a cluster. This classes produces plans for the {@link AssignmentManager} to execute. The load balancer implementation are pluggable. By default it uses the “DefaultLoadBalancer”. There is a new StochasticsLoadBalancer also which can be used.

### Meta Node Tracker
Watches the zNode called “unassigned” used by the META table. Used by CatalogTracker to track the location of the meta table.

### Master Address Tracker
Used by Active Master Manager to manage the current location of the master for the Region Servers.

## 4 ) File System Interfaces
All the services which interacts with the underlying FileSystem to store or manage data pertaining to the control of a Hmaster is lumped under this section

### MasterFileSystem
This class abstracts the file system operation for HBase including identifying base directory, log splitting, Delete Region, Delete Table etc.

### Log Cleaner
This is a chore (see next section) which runs at some specified interval and attempt to delete the Hlogs in the oldlogs directory. This is a chain of cleaner delegate which can be used to clean any kind of log files. By default, two cleaners: **TimeToLiveLogCleaner** and **ReplicationLogCleaner** are called in order. So if other effects are needed, implement your own LogCleanerDelegate and add it to the configuration “hbase.master.logcleaner.plugins”, which is a comma-separated list of fully qualified class names. LogsCleaner will add it to the chain. HBase ships with LogsCleaner as the default implementation.

### HFile Cleaner
This is also a chore (see next section) which runs at some specified intervals. This handles the HFile cleaning functions inside the master. By default, only the **TimeToLiveHFileCleaner** is called. If other effects are needed, implement your own LogCleanerDelegate and add it to the configuration “hbase.master.hfilecleaner.plugins”, which is a comma-separated list of fully qualified class names. The <code>HFileCleaner<code> will build the cleaner chain in  order the order specified by the configuration.

## 5 ) Chores
Chore is a task performed on a period in hbase.  The chore is run in its own thread. This base abstract class provides while loop and sleeping facility. If an unhandled exception, the threads exit is logged. Implementers just need to add checking if there is work to be done and if so, do it.  Its the base of most of the chore threads in hbase. Don’t subclass Chore if the task relies on being woken up for something to do, such as an entry being added to a queue, etc.

### Balancer Chore
The balancer is a tool that balances disk space usage on an HDFS cluster when some datanodes become full or when new empty nodes join the cluster. The tool is deployed as an application program that can be run by the cluster administrator on a live HDFS cluster while applications adding and deleting files.

The threshold parameter is a fraction in the range of (1%, 100%) with a default value of 10%. The threshold sets a target for whether the cluster is balanced. A cluster is balanced if for each datanode, the utilization of the node (ratio of used space at the node to total capacity of the node) differs from the utilization of the (ratio of used space in the cluster to total capacity of the cluster) by no more than the threshold value. The smaller the threshold, the more balanced a cluster will become. It takes more time to run the balancer for small threshold values. Also for a very small threshold the cluster may not be able to reach the balanced state when applications write and delete files concurrently.

The tool moves blocks from highly utilized datanodes to poorly utilized datanodes iteratively. In each iteration a datanode moves or receives no more than the lesser of 10G bytes or the threshold fraction of its capacity. Each iteration runs no more than 20 minutes. At the end of each iteration, the balancer obtains updated datanodes information from the namenode.

A system property that limits the balancer’s use of bandwidth is defined in the default configuration file:

```
   dfs.balance.bandwidthPerSec
```
This property determines the maximum speed at which a block will be moved from one datanode to another. The default value is 1MB/s. The higher the bandwidth, the faster a cluster can reach the balanced state, but with greater competition with application processes. If an administrator changes the value of this property in the configuration file, the change is observed when HDFS is next restarted.

### Catalog Janitor Chore
A janitor for catalog tables. It scans the META tables for looking for unused regions to garbage collect.

### Log Cleaner Chore
Explained in the previous section

### HFile Cleaner Chore
Explained in the previous section

## 6 ) Others
### Server Manager
The ServerManager class manages info about region servers.Maintains lists of online and dead servers. Processes the startups, shutdowns, and deaths of region servers. Servers are distinguished in two different ways. A given server has a location, specified by hostname and port, and of which there can only be one online at any given time. A server instance is specified by the location (hostname and port) as well as the startcode (timestamp from when the server was started). This is used to differentiate a restarted instance of a given server from the original instance.

### Co-Processor Host
Provides the common setup framework and runtime services for coprocessor invocation from HBase services.

### 参考

1. [Co-locate meta and master](https://issues.apache.org/jira/browse/HBASE-10569) : 这个commit之后，`HMaster`也是`HRegionServer` 
2. [Generic framework for Master-coordinated tasks](https://issues.apache.org/jira/browse/HBASE-5487) : 按容错的方式执行需要`HMaster`协调的任务