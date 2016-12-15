# Apache HBase AssignmentManager Improvements

[November 28, 2012](http://blog.cloudera.com/blog/2012/11/apache-hbase-assignmentmanager-improvements/)[By Jimmy Xiang](http://blog.cloudera.com/blog/author/jxiang/)

**`AssignmentManager`** is a module in the Apache HBase [Master](http://hbase.apache.org/book/master.html) that manages [regions](http://hbase.apache.org/book/regions.arch.html) to [RegionServers](http://hbase.apache.org/book/regionserver.arch.html) assignment. (See HBase [architecture](http://hbase.apache.org/book.html#_architecture) for more information.) It ensures that all regions are assigned and each region is assigned to just one RegionServer.

Although the **`AssignmentManager`** generally does a good job, the existing implementation does not handle assignments as well as it could. For example, if a region was assigned to two or more RegionServers, some regions were stuck in transition and never got assigned, or unknown region exceptions were thrown in moving a region from one RegionServer to another.

In the past we tried to fix these bugs without changing the underlying design. Consequently, the AssignmentManager ended up having many band-aids, and the code base became hard to understand/maintain. Furthermore, the underlying issues had not been completely fixed.

After looking at many of the cases, we decided that the best path forward would be to improve the underlying design to be more robust, and simplify the code base to be easier to maintain. The goal here is not to make a better AssignmentManager – it’s to make a correct one.

In this post, I will explain some improvements that the Cloudera engineering team has done recently. They are primarily focused on correctness and reliability instead of performance; with the exception of [HBASE-6977](https://issues.apache.org/jira/browse/HBASE-6977) (Multithreaded processing ZooKeeper assignment events), they improve performance only marginally. As to performance, Nicolas Liochon and Chunhui Shen have done some great work in [HBASE-6109](https://issues.apache.org/jira/browse/HBASE-6109) (Improve region-in-transition performances during assignment on large clusters) and [HBASE-5970](https://issues.apache.org/jira/browse/HBASE-5970) (Improve `AssignmentManager#updateTimer` and speed up handling opened events).

## Region States Overview

**Almost all of the issues discussed previously are caused by inconsistent region states**. For example, a region is assigned to one RegionServer, but the Master thinks it is assigned to a different one, or not assigned at all and assigns it to somewhere else again. So before we move to each patch, let’s talk a little bit about the region states.

A region can be in one of these states:

- **OFFLINE**: Region is in an offline state
- **PENDING_OPEN**: Sent RPC to a RegionServer to open but has not begun
- **OPENING**: RegionServer has begun to open but not yet done
- **OPEN**: RegionServer opened region and updated META
- **PENDING_CLOSE**: Sent RPC to RegionServer to close but has not begun
- **CLOSING**: RegionServer has begun to close but not yet done
- **CLOSED**: RegionServer closed region and updated meta
- **SPLITTING**: RegionServer started split of a region
- **SPLIT**: RegionServer completed split of a region

> The two states SPLITTING and SPLIT relate to region splitting, which is beyond the scope of this post.
>

**Region state is used to track the transition of a region from unassigned (a.k.a offline) to assigned (a.k.a open), and from assigned to unassigned.** The transition path (state machine) looks like this:

- open:  OFFLINE -> PENDING_OPEN -> OPENING -> OPEN
- close:  OPEN -> PENDING_CLOSE -> CLOSING -> CLOSED -> OFFLINE

The state machine is tracked in three different places: **META table**, **Master server memory**, and **Apache ZooKeeper** unassign znodes. As it moves through the state machine, we update the different storage locations at different times. <u>The `AssignmentManager`’s job is to try to keep the three pieces of storage synchronized.</u>

1. **The information in ZooKeeper is transient**. It is some temporary information about a region state transition, which is used to track the states while the region moves through from unassigned to assigned, or the reverse way.  If the corresponding RegionServer or the Master crashes in the middle, that information can be used to recover the transition.

2. **Final region assignment information is persisted in the META table**. However, it doesn’t have the latest information of regions in transition. It only has the most recent RegionServer each region is assigned to. If a region is not online, e.g. is in transition, the META table knows the previous RegionServer the region used to be assigned to, but it doesn’t know what’s going on with the region and where it is opening now.

3. **The Master holds all region states in memory**. This information is used by the `AssignmentManager` to track where each region is, and its current state. Although region assignments are initiated by the Master and it knows a region is opening on a RegionServer, the Master depends on the ZooKeeper event update to find out if the opening is succeeded. When a region is opened on a RegionServer, this information is already in the META table. But it takes a very short time for the Master to find that out based on the region transition update from ZooKeeper.


The region state in the Master memory is not always consistent with the information in the META table, or in ZooKeeper. The AssignmentManager is responsible for keeping track of the current status of each region, and make sure they are eventually consistent.

## HBASE-6272 (In-memory region state should be consistent)

The first improvement we did is [HBASE-6272](https://issues.apache.org/jira/browse/HBASE-6272), to make sure the in-memory region state is consistent. In the Master memory, we have the state of each region. We also have the mapping of regions to the RegionServers they are on when they are open. We need to make sure they are consistent. For example, if a region is in OPEN state, there should be a mapping of this region to an online RegionServer.

In HBASE-6272, we separated this information from AssignmentManager into its own class, and synchronized the access, so that at least the Master has a consistent view to itself internally. Otherwise, how can the Master tell what’s going on with a region for sure?

Another thing is that we keep all the regions known to the Master so far always in memory although it could be offline at some time. We used to not keep such information, and ran into UnknownRegionException in moving a region that might not be in memory temporarily.

This improvement is the foundation for the following improvements.

## HBASE-6381 (AssignmentManager should use the same logic for clean startup and failover)

The second improvement is [HBASE-6381](https://issues.apache.org/jira/browse/HBASE-6381), to fix the region states recovery at startup time. When a Master starts up, it needs to recover its in-memory region states. If it is a clean startup – i.e., all RegionServers, together with the Master, are restarted or started for the first time – things are quite simple:  all regions should be offline and need to be assigned.

**Let’s look at the case where some RegionServers are still up and serving some regions, and the Master and any dead RegionServers restart.** The Master then needs to figure out what regions are not in service, and if any regions are still in transition. If so, are they transitioning to/from dead/live RegionServers? There could be tables in the middle of enabling or disabling at this moment too. Things can get very complex. If just one RegionServer and the Master, or just the Master dies, we don’t want to bring down other RegionServers, as it may take quite some time to re-open all regions. This scenario is called “Master failover.”

**In failover mode**, the Master has its own dead server recovery logic, which is different but similar in function to the dead server handling logic used to recover regions on a RegionServer that dies while the Master is online. **One change** we did is to reuse the dead server handling logic, as we don’t need to maintain similar logic in the AssignmentManager. So the Master now submits all of the dead RegionServers to the dead server handler to process.

**The other change** we did is to suspend the dead server handler until the region states are fully recovered. The goal is to prevent the handler to race with the region state recovering. If the handler tries to reassign a region, the region transition ZooKeeper event comes to the AssignmentManager. If the region states are not fully recovered yet, we used to have some special logic to handle this scenario since we need to find out this region’s state if we don’t know it already. With this change, we removed the special logic and the code is now cleaner.

**One more change** involves the ZooKeeper event. When the Master restarts and while it is recovering the region states, there could be some region that is in the middle of transition – so the ZooKeeper region transition data could be updated. The change we did is to not watch the assignment znodes until the region states are recovered, for the same reason. We don’t want to handle region state transition before we know all regions’ states – i.e. the region states have been recovered.

> 第二个改进是 [HBASE-6381](https://issues.apache.org/jira/browse/HBASE-6381)，用于在 Master 启动时修复 region 的状态。Master 启动时，需要恢复其内存中的 region 状态。如果是干净的重启，即，所有的 RegionServer 和 Master 一起重启，或是集群第一次启动，此时一切都很简单：所有 Region 都是离线的（offline），需要重新分配。
>
> **考虑这样的场景：部分 RegionServer 仍然在工作，因此某些 region 还可用，此时 Master 和 宕机的 RegionServer 重启回来了**。Master 需要知道：那些 region 不可用，是否有 Region 处于状态转换中，如果存在，这些 Region 是不是从宕机的 RegionServer 转换到正常工作的 RegionServer，或者反之？同样，此时可能存在某些表处于 enable 或是 disable 的过程之中。情况可以变得非常复杂。如果只有一台 RegionServer 和 Master宕机，或者仅仅是 Master 宕机，就不应该让其它的 RegionServer 都重启，因为这可能需要相当长的时间来重新打开 Region。这种情况被称为 “Master 容灾”。
>
> 容灾模式下，Master 有自己的宕机服务器恢复逻辑。 正常工作时，Master 也有宕机服务器的处理逻辑，用于恢复 Region，这两套逻辑不同但类似。我们做的第一个改变是重用宕机服务器的处理逻辑，这样，`AssignmentManager`就不再需要维护类似的逻辑。现在，Master 将所有宕机的  RegionServer 交由 `ServerShutdownHandler` 去处理。
>
> 我们做的第二个改变是在完整恢复 region 的状态之前，暂停 `ServerShutdownHandler` 的工作。主要目的是避免其和 region 状态恢复之间的竞争。如果 `ServerShutdownHandler`  尝试重新分配 region，`AssignmentManager` 也会收到 region 转换的 ZK 事件。如果还没有完整地恢复 region 的状态，就需要找到该 region 的状态，过去这块使用特殊的处理逻辑。有了这个改变，我们删除了特殊的处理逻辑，代码现在更加简洁了。
>
> 第三个改变涉及到 ZK 事件。Master 重启之后，当它正在恢复 region 的状态时，由于某些 region 可能正处于状态转换之中，因此  ZK 上 region 的状态转换数据会被更新。基于同样的原因，在恢复 region 状态之前，不去监听 ZK 上的 分配节点（那个Znode？）。在不知道 region 的状态情况下，即， region 状态恢复完之前，不去处理 region 的状态转换。

In this improvement, we also cleaned up and consolidated the bulk assigner. Bulk assignment is a good idea to reduce the number of RPC calls to a RegionServer. However, it is not as reliable as the individual assignment. We would like to fix that eventually, via HBASE-6611. As the first step, however, we did some cleanup: We moved the bulk assigner out of AssignmentManager to a standalone class. Some useful logic in a bulk assignment method used for RegionServer recovery is folded into the standalone bulk assigner. The new bulk assigner is used for all bulk assigning, and redundant bulk assignment logic is removed.

## HBASE-6611 (Forcing region state offline cause double assignment)

[HBASE-6611](https://issues.apache.org/jira/browse/HBASE-6611) **makes sure the Master closes a region before it changes the region’s state to offline/closed from a state like open, opening or pending open**. This is very important to prevent double assignments. <u>When we try to assign a region, if its state is not offline, we used to forcibly change it to offline – but this does not actually close the region</u>. Assuming the region state is right, it means the region could be already open or opening in a RegionServer. If we try to assign the region again to a different RegionServer without closing it at first, it very likely leads to a double assignment. What we did is to send a close-region request to the corresponding RegionServer to close the region if it is open, or abort the region opening if it is still opening. We also retry the close-region if the region is in transition to make sure no timing/racing issue catches us at this moment.

**Another change** in this patch is about the region state transition. Originally, we transition the state upon processing an assignment ZooKeeper event, as long as the current state matches. For example, if we get a region_opened event and the current state is opening or pending open, then we transition the state to open. We added a check on the RegionServer name. We need to make sure the event is indeed from the RegionServer where the transition should be happening. This is to prevent updating the region state based on some old events since the region could be opening on another RegionServer and it is aborted.

The server name in a region transition data used to be the source of the event, which could be the Master server since all assignments start from the Master. We changed it to the name of the RegionServer where the transition is happening. The point is to make sure all region transition data has the RegionServer name where the transition is happening, so that the RegionServer name checking discussed above is possible. Another point for the change is that if we use the source of the event, during the failover mode we could lose track of the RegionServer where the transition is going on, because the source of the event (Master server in this case) does not point to the expected RegionServer. We could keep the source of the event and add the target RegionServer name, but we chose not to do so since we don’t use the source of the event for now.

With the additional RegionServer name checking, we are sure we get the right ZooKeeper event and do the right transition. Since there could be a forced reassignment initiated by a timeout monitor or the client, the same region could still be opening somewhere else when we try to open it on a new RegionServer. If the previous opening is not aborted fast enough, it could trigger some ZooKeeper events which could still be in the queue for processing. Without the RegionServer name checking, some old ZooKeeper event could take the region state in some wrong direction.

For example, the following diagram shows a scenario to demonstrate the problem.

![post](http://blog.cloudera.com/wp-content/uploads/2012/11/post1.png) 

**An old ZooKeeper event leads to the wrong region state, if not checking RegionServer name.**

1. The Master tries to assign a region to RegionServer 1
2. The region is opened and RegionServer 1 transitions the region to opened in ZK
3. The Master doesn’t get the ZooKeeper event fast enough and times out the assignment, so it closes the region
4. The Master tries to open the region again on RegionServer 2
5. The previous ZooKeeper open event finally gets to the Master
6. If not checking RegionServer name, the Master will think the region is opened on RegionServer 1, and deletes the unassign znode
7. Region server 2 opens the region and can’t update the znode since it is already removed, and the region opening fails
8. The Master gets the znode deletion event and moves the region out of transition. Now the Master thinks the region is online and assigned to RegionServer 1.  The region is actually not assigned anywhere.

In this improvement we also changed the bulk assigner to lock the regions to prevent them from assigning again, and use the ZooKeeper offline znode version in requesting the RegionServer to open those regions. That’s what we do in assigning an individual region. Bulk assigner should do the same to be as reliable.

Another locking we added is for the node-deleted ZooKeeper event callback. So, we have proper locking everywhere a region state can change and an existing assignment can be retried forcefully. This will make sure the in-memory region state is trustworthy and reliable.

With this improvement (and previous improvements), we think a bunch of holes have been closed, and now we can trust AssignmentManager much more than before. Based on our testing, we have never seen double assignments, or region stuck in transition forever, with minimal performance impact. On our testing cluster, without this patch, it took around 290 seconds to bulk-assign 10,339 regions to 4 RegionServers. With this patch, it took around 300 seconds. The little overhead is due to the locking in bulk assigning, which is reasonable.

## HBASE-6977 (Multithread processing ZooKeeper assignment events)

The last improvement is [HBASE-6977](https://issues.apache.org/jira/browse/HBASE-6977).  In this small patch, we removed the extra ZooKeeper watcher introduced in HBASE-6611 to prevent deadlocks. We delegated the ZooKeeper events to several workers to process so that the ZooKeeper event thread is freed up to do other things. With this patch, it takes around 250 seconds to bulk assign the same 10,339 regions to 4 RegionServers.

Each ZooKeeper watcher has just one event thread to process all event notifications for this watcher.  If it gets stuck in one event due to locking, it blocks the whole ZooKeeper event notification processing, which could cause some deadlock if the locker holder is also waiting for the ZooKeeper event notification to meet some criteria to move on.  That’s why we introduced an additional ZooKeeper watcher in HBASE-6611. The additional ZooKeeper watcher is for the async ZooKeeper offline assignment znodes callback. In this improvement, we created a pool of a configurable number of single-threaded workers to process ZooKeeper assignment events so the ZooKeeper event thread will not block.

With multiple workers, we need to make sure the events are processed in the right order. In this patch, we choose which worker to use based on the hash code of the region’s unassignment znode path, so that we can guarantee all the events for a given region are always processed by the same worker. The worker is a single threaded executor, and the events for the same region are always processed in order. Events for different regions don’t have to be processed in order.

The hash code of the region’s unassignment znode path should be random enough so that all workers have a fair share of work.

## Summary

With these patches, the AssignmentManager is much more stable and reliable now. There are no more double assignments or regions stuck in transition as far as our experiments show.

Based on our testing, we can assign 10+K regions in around 4 minutes. With these improvements, we are confident to reset the default region assignment timeout to 10 minutes ([HBASE-5119](https://issues.apache.org/jira/browse/HBASE-5119) (Set the TimeoutMonitor’s timeout back down)) in the trunk (0.96) branch, with the AssignmentManager being stable and reliable. Of course, the region assignment time also depends on how long it actually takes to open the region on the RegionServer.

## Acknowledgements

Thanks to the community and everyone who reported the issues, reviewed the patches, and discussed about the fixes.