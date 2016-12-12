# What are HBase znodes?

[Apache ZooKeeper](http://zookeeper.apache.org/) is a client/server system for distributed coordination that exposes an interface similar to a filesystem, where each node (called a *znode*) may contain data and a set of children. Each znode has a name and can be identified using a filesystem-like path (for example, /root-znode/sub-znode/my-znode).

In Apache HBase, ZooKeeper coordinates, communicates, and shares state between the Masters and RegionServers. HBase has a design policy of using ZooKeeper only for transient data (that is, for coordination and state communication). Thus if the HBase’s ZooKeeper data is removed, only the transient operations are affected — data can continue to be written and read to/from HBase.

In this blog post, you will get a short tour of HBase znodes usage. The version of HBase used for reference here is 0.94 (shipped inside CDH 4.2 and CDH 4.3), but most of the znodes are present in previous versions and also likely to be so in future versions.

The HBase root znode path is configurable using hbase-site.xml, and by default the location is “/hbase”. All the znodes referenced below will be prefixed using the default /hbase location, and the configuration property that lets you rename the particular znode will be listed next to the default znode name and highlighted with bold type.

ZooKeeper provides an interactive shell that allows you to explore the ZooKeeper state — run it by using`hbase zkcli` and walk through the znode via `ls`, as in a typical filesystem. You can also get some information about the znode content by using the `get` command.

>
> $ **hbase zkcli**
> [zk: localhost:2181(CONNECTED) 0] **ls  /**
> [hbase, zookeeper]
> [zk: localhost:2181(CONNECTED) 1] **ls /hbase **
> [splitlog, online-snapshot, unassigned, root-region-server, rs, backup-masters, draining, table, master, shutdown, hbaseid]
> [zk: localhost:2181(CONNECTED) 2] **get /hbase/root-region-server **
> 3008@u1310localhost,60020,1382107614265
> dataLength = 44
> numChildren = 0
> ...
>

## Operations

The znodes that you’ll most often see are the ones that coordinate operations like *Region Assignment*, *Log Splitting*, and *Master Failover*, or keep track of the cluster state such as the *ROOT table location*, *list of online RegionServers*, and *list of unassigned Regions*.

| **/hbase** (zookeeper.znode.parent)      | The root znode that will contain all the znodes created/used by HBase |
| ---------------------------------------- | ---------------------------------------- |
| /hbase/**hbaseid** (zookeeper.znode.clusterId) | Initialized by the Master with the UUID that identifies the cluster. The ID is also stored on HDFS in hdfs:/<namenode>:<port>/hbase/hbase.id. |
| /hbase/root-region-server (zookeeper.znode.rootserver) | Contains the location of the server hosting the ROOT region. It is queried by the client to identify the RegionServer responsible for ROOT and ask for the META locations. (In 0.96, the ROOT table was removed as part of HBASE-3171, and this znode is replaced by /hbase/meta-region-server [zookeeper.znode.metaserver] that contains the location of the server hosting META.) |
| /hbase/**rs** (zookeeper.znode.rs)       | On startup each RegionServer will create a sub-znode (e.g. /hbase/rs/m1.host) that is supposed to describe the “online” state of the RegionServer. The master monitors this znode to get the “online” RegionServer list and use that during Assignment/Balancing. |
| /hbase/**unassigned** (zookeeper.znode.unassigned) | Contains a sub-znode for each unassigned region (e.g. /hbase/unassigned/<region name>). This znode is used by the Assignment Manager to discover the regions to assign. ([Read this](http://blog.cloudera.com/blog/2012/11/apache-hbase-assignmentmanager-improvements/) to learn more about the Assignment Manager.) |
| /hbase/**master** (zookeeper.znode.master) | The “active” master will register its own address in this znode at startup, making this znode the source of truth for identifying which server is the Master. |
| /hbase/**backup-masters** (zookeeper.znode.backup.masters) | Each inactive Master will register itself as backup Master by creating a sub-znode (hbase/backup-master/m1.host). This znode is mainly used to track which machines are available to replace the Master in case of failure. |
| /hbase/**shutdown** (zookeeper.znode.state) | Describes the cluster state, “Is the cluster up?” It is created by the Master on startup and deleted by the Master on shutdown. It is watched by the RegionServers. |
| /hbase/**draining** (zookeeper.znode.draining.rs) | Used to decommission more than one RegionServer at a time by creating sub-znodes with the form *serverName,port,startCode* (for example, /hbase/draining/m1.host,60020,1338936306752). This lets you decommission multiple RegionServers without having the risk of regions temporarily moved to a RegionServer that will be decommissioned later. [Read this](http://hbase.apache.org/book/node.management.html#draining.servers) to learn more about /hbase/draining. |
| /hbase/**table** (zookeeper.znode.masterTableEnableDisable) | Used by the master to track the table state during assignments (disabling/enabling states, for example). |
| /hbase/**splitlog** (zookeeper.znode.splitlog) | Used by the log splitter to track the pending log to replay and its assignment. ([Read this](http://blog.cloudera.com/blog/2012/07/hbase-log-splitting/) to learn more about log splitting). |

##  

## Security

The Access Control List (ACL) and the Token Provider coprocessors add two more znodes: one to synchronize access to table ACLs and the other to synchronize the token encryption keys across the cluster nodes.

| /hbase/**acl** (zookeeper.znode.acl.parent) | The acl znode is used for synchronizing the changes made to the _acl_ table by the grant/revoke commands. Each table will have a sub-znode (/hbase/acl/tableName) containing the ACLs of the table. ([Read this](http://blog.cloudera.com/blog/2012/09/understanding-user-authentication-and-authorization-in-apache-hbase/) for more information about the access controller and the ZooKeeper interaction.) |
| ---------------------------------------- | ---------------------------------------- |
| /hbase/**tokenauth** (zookeeper.znode.tokenauth.parent) | The token provider is usually used to allow a MapReduce job to access the HBase cluster. When a user asks for a new token the information will be stored in a sub-znode created for the key (/hbase/tokenauth/keys/key-id). |

##  

## Replication

As general rule, all znodes are ephemeral, which means they are describing a “temporary” state — so, even if you remove everything from ZooKeeper, HBase should be able to recreate them. Although the Replication znodes do not describe a temporary state, they are meant to be the source of truth for the replication state, describing the replication state of each machine. ([Read this](http://blog.cloudera.com/blog/2012/07/hbase-replication-overview-2/) to learn more about replication).

| /hbase/**replication** (zookeeper.znode.replication) | Root znode that contains all HBase replication state information |
| ---------------------------------------- | ---------------------------------------- |
| /hbase/**replication/peers** (zookeeper.znode.replication.peers) | Each peer will have a sub-znode (e.g. /hbase/replication/peers/<ClusterID>) containing the ZK ensemble’s addresses that allows the peer to be contacted. |
| /hbase/**replication/peers//peer-state**(zookeeper.znode.replication.peers.state) | Mirror of the /hbase/replication/peers znode, but here each sub-znode (/hbase/replication/peer-state/<ClusterID>) will track the peer enabled/disabled state. |
| /hbase/**replication/state** (zookeeper.znode.replication.state) | Indicates whether replication is enabled. Replication can be enabled by setting the hbase.replication configuration to true, or can be enabled/disabled by using the start/stop command in the HBase shell. (In 0.96, this znode was removed and the peer-state znode above is used as a reference.) |
| /hbase/**replication/rs** (zookeeper.znode.replication.rs) | Contains the list of RegionServers in the main cluster (/hbase/replication/rs/<region server>). And for each RegionServer znode there is one sub-znode per peer to which it is replicating. Inside the peer sub-znode the hlogs are waiting to be replicated (/hbase/replication/rs/<region server>/<ClusterId>/<hlogName>). |

##  

## Online Snapshot Procedures

Online snapshots are coordinated by the Master using ZooKeeper to communicate with the RegionServers using a two-phase-commit-like transaction. ([Read this](http://blog.cloudera.com/blog/2013/06/introduction-to-apache-hbase-snapshots-part-2-deeper-dive/) for more details about snapshots.)

| /hbase/**online-snapshot/acquired** | The acquired znode describes the first step of a snapshot transaction. The Master will create a sub-znode for the snapshot (/hbase/online-snapshot/acquired/<snapshot name>). Each RegionServer will be notified about the znode creation and prepare the snapshot; when done they will create a sub-znode with the RegionServer name meaning, “I’m done” (/hbase/online-snapshot/acquired/<snapshot name>/m1.host). |
| ----------------------------------- | ---------------------------------------- |
| /hbase/**online-snapshot/reached**  | Once each RegionServer has joined the acquired znode, the Master will create the reached znode for the snapshot (/hbase/online-snapshot/reached/<snapshot name>) telling each RegionServer that it is time to finalize/commit the snapshot. Again, each RegionServer will create a sub-znode to notify the master that the work is complete. |
| /hbase/**online-snapshot/abort**    | If something fails on the Master side or the RegionServer side, the abort znode will be created for the snapshot telling everyone that something went wrong with the snapshot and to abort the job. |

##  

## Conclusion

As you can see, ZooKeeper is a fundamental part of HBase. All operations that require coordination, such as Regions assignment, Master-Failover, replication, and snapshots, are built on ZooKeeper. (You can learn more about why/how you would use ZooKeeper in your applications [here](http://blog.cloudera.com/blog/2013/02/how-to-use-apache-zookeeper-to-build-distributed-apps-and-why/).)

Although most znodes are only useful to HBase, some — such as the list of RegionServers (/hbase/rs) or list of Unassigned Regions (/hbase/unassigned) — may be used for debugging or monitoring purposes. Or, as in the case with /hbase/draining, you may interact with them to let HBase know what you’re doing with the cluster.



| Path                          | ZooKeeperListener      |                                          |
| ----------------------------- | ---------------------- | ---------------------------------------- |
| /hbase/**running**            | `ClusterStatusTracker` |                                          |
| /hbase/**master**             | `MasterAddressTracker` |                                          |
| /hbase/**backup-masters**     | `MasterAddressTracker` |                                          |
| /hbase/**balancer**           | `LoadBalancerTracker`  |                                          |
| /hbase/**meta-region-server** | `MetaTableLocator`     | [HBASE-3171](https://issues.apache.org/jira/browse/HBASE-3171) |
|                               |                        |                                          |
|                               |                        |                                          |
|                               |                        |                                          |

- [x] meta-region-server, 

- [ ] acl, 
- [x] backup-masters, 
- [ ] table, 
- [ ] draining, 
- [ ] region-in-transition, 
- [x] running,
- [ ] table-lock, 
- [x] master, 
- [x] balancer, 
- [ ] namespace, 
- [ ] hbaseid, 
- [ ] online-snapshot, 
- [ ] replication, 
- [ ] splitWAL, 
- [ ] recovering-regions, 
- [ ] rs, 
- [ ] flush-table-proc


1. [HBase read high-availability using timeline-consistent region replicas](https://issues.apache.org/jira/browse/HBASE-10070)
2. [Abstract out ZooKeeper usage in HBase - phase 1](https://issues.apache.org/jira/browse/HBASE-10909)


SSH => ServerShutdownHandler

````java
//Master startup
//1.Main Thread
HMarster.main()
  HMasterCommandLine.run()
    HMarster.HMaster()
      HRegionServer.HRegionServer()
        createTableLockManager
      HMarster.startActiveMasterManager() // 2 创建抢 master leader的线线程
    HMasterCommandLine.startMaster() //3 创建RegionServer主线程, [for hbase:meta] 

//2
Thread.run()
  HMarster.finishActiveMasterInitialization() // 成为 leader之后
    create MasterFileSystem
      create SplitLogManager //!!!
        create TimeoutMonitor daemon //管理split tasks
    create ServerManager  // 管理 RegionServer
    setupClusterConnection
    ZKTableLockManager.reapWriteLocks() // Invalidate all write locks held previously
    HMarster.initializeZKBasedSystemTrackers() //Initializing ZK system trackers
      create LoadBalancer
      create LoadBalancerTracker
      create AssignmentManager  //!!! zookeeper listener
      create RegionServerTracker
      create DrainingServerTracker
      setup cluster is up       // after setting this flag region server can go ahead
      create SnapshotManager
      create MasterProcedureManagerHost
    create MasterCoprocessorHost  // Initializing master coprocessors
    startServiceThreads // Initializing master service threads
    ServerManager.waitForRegionServers // Wait for region servers to report in
  
    // Check zk for region servers that are up but didn't register
    
    MasterFileSystem#getFailedServersFromLogFolders // get a list for previously 
                                                    //  failed RS which need log
                                                    //  splitting work
    MasterFileSystem#removeStaleRecoveringRegionsFromZK // remove stale recovering
                                                        //  regions from previous run
    //log splitting for hbase:meta server
    get meta server location
    if meta server is dead 
      split it before assignment
    
    if there are tables on active master
      Wait for "regionserver" to finish initialization
  
    initialize load balancer
    
    HMaster#assignMeta  //Assigning Meta Region, i.e. hbase:meta
      
    
    ServerManager#processDeadServer //Submitting log splitting work for previously
                                    // failed region servers
                                    // 开始做 『WAL split』工作
    AssignmentManager#joinCluster // Starting assignment manager ???
    LoadBalancer#setClusterStatus // set cluster status again after user 
                                  //  regions are assigned
    //Starting balancer and catalog janitor Chore
    HMaster.initNamespace  // Starting namespace manager
    HMaster.initQuotaManager // Starting quota manager
    // almost done...
  
// 3
HRegionServer.run
  preRegistrationInitialization
    initializeZooKeeper
      blockAndCheckIfStopped(master)
      blockAndCheckIfStopped(clusterup)
      waitforMasterActive() // wait for becoming active master
  
````