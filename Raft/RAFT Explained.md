# RAFT Explained

*This series of three blog posts will give readers an overview on why the Raft consensus algorithm is relevant and summarizes the functionality that makes it work. The 3 posts are divided as follows:*

- *Part 1/3: Introduction to the problem of consensus, why it matters (even to non-PhDs), how the specification of the Raft algorithm is an important contribution to the field and a peek at the actual real-world uses of consensus algorithms.*
- *[Part 2/3](http://container-solutions.com/raft-explained-part-23-overview-core-protocol/): Overview over the core mechanics that constitute the Raft algorithm itself*
- *[Part 3/3](http://container-solutions.com/raft-explained-part-33-safety-liveness-guarantees-conclusion/): Why the algorithm works even on edge cases, with an overview of the algorithm's safety and liveness properties. This is then followed by the conclusion on the insights gained from looking at the Raft protocol.*

## Part 1 Introduction to the Consensus Problem
### **Introduction**

In 1985, researchers Fischer, Lynch, and Patterson (FLP) put to rest a long-standing question. Consensus in an asynchronous system with faulty processes is impossible. This may sound like an absurd result: After all, we rely on systems that rely on consensus for our everyday computing. Even websites like Facebook and Twitter require a meaningful solution to consensus in order to provide distributed solutions to implement their distributed architectures that are required for high availability.

To say that consensus is impossible, under these circumstances, may then sound like an interesting theoretical result and nothing more. However, practical solutions to this problem, that take into account the FLP result, are one of the core areas of research in distributed computing. These solutions generally work under the same framework, wherein consensus is defined strictly as the assurance of the following three properties in a distributed system:

- Validity: any value decided upon must be proposed by one of the processes
- Agreement: all non-faulty processes must agree on the same value,
- Termination: all non-faulty nodes eventually decide.

This post will discuss one solution to the consensus problem called Raft. Raft is a solution to the consensus problem that is focused on understandability and implementability

#### **Motivation**

There are many reasons why modern computing infrastructure is becoming more and more distributed.

First of all, CPU clock speeds are barely increasing today and generally there is a limit to what a single machine can handle. With companies handling higher and higher volumes of data and traffic, this forces them to use parallelism to handle these increasingly huge workloads, giving them **scalability**. Ever-increasing quantities and complexity of data, as well as increased speed at which it is changing, will only further this trend, fueled by the inherent financial value of large data sets and continuous technological advances.

Generally, services today are expected to be highly available, even under varying loads and despite partial system failure. Downtime has become unacceptable, be it due to maintenance or outages. This kind of resilience is only achievable through **replication**, the use of multiple redundant machines: When one fails or is overloaded, another one can take over the given role. To provide resilience against data-loss, data will have to be stored, as well as updated coherently across all replicated machines.

Additionally, with services having global coverage, one might want to reduce **latency**, having requests served by a replicated instance that is closest geographically.

In most applications, three or (many) more replicated servers are used and needed to provide optimal dynamic scalability, resilient data-storage through data replication, and low latency for optimal quality and guarantees of service. In order for a computation to give sensible results in a distributed system, it has to be executed deterministically. In a distributed system this is easier said than done.

How do you make sure that any two machines will have the same state at any given time? With the assumption of arbitrary network delays and possible temporary partitions of the network this is actually fundamentally impossible. Different weaker guarantees are possible at varying trade-offs, but the most simple possible functional guarantee is to have replicas be *eventually consistent* with each other, meaning that without new writes all replicas will eventually return the same value.

In order to understand deterministic execution in a distributed computation, one has to distinguish between operations that don’t interfere with each other like simple read operations and the ones that do, like write operations on the same variable. The servers performing conflicting operations need to have some way to agree on a common reality, that is to find consensus.

Most distributed are built on top of consensus: Fault-tolerant replicated state machines, data stores, distributed databases, and group membership systems are all dependent on a solution to consensus. Actually, even another fundamental problem within distributed systems, that of atomic broadcast, turns out to be fundamentally the same problem – that is isomorphic – to consensus.

To summarize, consensus is a fundamental general-purpose abstraction used in distributed systems: It gives the useful guarantee of getting all the participating servers to agree on something, as long as a certain number of failures is not exceeded. **Fundamentally, the goal of consensus is** not that of the negotiation of an optimal value of some kind, but just **the** **collective agreement on** **some value that was previously proposed by one of the participating servers in that round of the consensus algorithm.** With the help of consensus the distributed system is made to act as though it were a single entity.

####**Main Contribution**

Raft was designed in reaction to issues with understanding and implementing the canonical [Paxos](https://en.wikipedia.org/wiki/Paxos_(computer_science)) algorithm: Paxos has been considered for many years the defining algorithm for solving the consensus problem and found in many real-world systems in some form or another. Yet, Paxos is considered by many to be a subtle, complex algorithm that is famously difficult to fully grasp. Additionally, the implementation of Paxos in real-world systems has brought on many challenges, problems that are not taken into account by the underlying theoretical model of Paxos.

With understandability and implementability as a primary goal, while not compromising on correctness or efficiency relative to (Multi-)Paxos, Ongaro and Ousterhout have developed the Raft protocol. Raft is a new protocol that is based on insights from various previous consensus algorithms and their actual implementations. It recombines and reduces the ideas to the essential, in order to get a fully functional protocol without compromises that is still more optimal relative to previous algorithms for consensus that do not have understandability and implementability as a primary goal.

Regarding understandability, the authors of Raft mainly critique the unnecessary mental overhead created by Paxos and propose that the same guarantees and efficiency can be obtained by an algorithm that also gives better understandability. To this end, they propose the following techniques to improve understandability: Decomposition – the separation of functional components like leader election, log replication, and safety – and state space reduction – the reduction of possible states of the protocol to a minimal functional subset.

Furthermore, the authors point out that the theoretically proven Paxos does not lend itself well to implementation: The needs of real-world systems let the actually implemented algorithm deviate so far from the theoretical description that the theoretical proof may often not apply in practice anymore. These issues are well described within [Paxos Made Live – An Engineering Perspective](http://www.read.seas.harvard.edu/~kohler/class/08w-dsi/chandra07paxos.pdf) by Chandra et. al 2007 where they describe their experiences of implementing Paxos within Chubby (which Google uses within Google File System, BigTable, and MapReduce to synchronize accesses to shared resources):

> While Paxos can be described with a page of pseudo-code, our complete implementation contains several thousand lines of C++ code. The blow-up is not due simply to the fact that we used C++ instead of pseudo notation, nor because our code style may have been verbose. Converting the algorithm into a practical, production-ready system involved implementing many features and optimizations – some published in the literature and some not.

and

> While the core Paxos algorithm is well-described, implementing a fault-tolerant log based on it is a non-trivial endeavor.

The poor implementability of the Paxos protocol is something that they improve upon through formulating not purely the consensus module itself, but by embedding this module within the usual use case of replicated state machines and formally proving safety and liveness guarantees over this whole real-world use case.

#### **Consensus in the Wild**

In order to give an idea how central consensus is to modern distributed systems and in which places it is used (and maybe not commonly known about), I will refer to a formal comparison between the actual consensus implementations in use within various popular frameworks [Consensus in the Cloud: Paxos Systems Demystified](http://www.cse.buffalo.edu/~demirbas/publications/cloudConsensus.pdf) (Ailijiang, Charapko, & Dermibas, 2016) and include their overview diagram (figure 4 in the paper):

 

[![img](https://blog.container-solutions.com/hs-fs/hubfs/Imported_Blog_Media/consensusoverview-1024x863-1.png?width=600&name=consensusoverview-1024x863-1.png)](https://info.container-solutions.com/hubfs/Imported_Blog_Media/consensusoverview-1.png)

The acronyms under usage patterns stand for server replication (SR), log replication (LR), synchronisation service (SS), barrier orchestration (BO), service discovery (SD), leader election (LE), metadata management (MM), and Message Queues (Q).

The consensus system of Chubby is based on Multi-Paxos (a variation on Paxos that decides on more than a single value), Zookeeper is based on [Zab](https://cwiki.apache.org/confluence/display/ZOOKEEPER/Zab+vs.+Paxos) (a protocol similar but not the same as Paxos), and etcd is built on top of Raft – the protocol about which this blog post speaks.

Another two good examples of the Raft protocol in practice are [Consul](https://www.consul.io/) and [Nomad](https://www.nomadproject.io/) from Hashicorp. Consul is a "consensus system" in the language of the above table and is widely used for service discovery in distributed systems. Nomad – Hashicorp's orchestration platform – uses Raft for state machine replication and integrates with Consul for service discovery of scheduled workloads. A comparsion of Consul with some of the other consensus systems in the above table can be found here:[ https://www.consul.io/intro/vs/zookeeper.html.](https://www.consul.io/intro/vs/zookeeper.html)

***[Continue with Part 2/3](http://container-solutions.com/raft-explained-part-23-overview-core-protocol/): Overview over the core mechanics that constitute the Raft algorithm itself***

### **References**

[In search of an understandable consensus algorithm](https://web.stanford.edu/~ouster/cgi-bin/papers/raft-atc14). Ongaro, Diego, & Ousterhout  (2014) *USENIX Annual Technical Conference (USENIX ATC 14)*.

[ARC: analysis of Raft consensus](https://www.cl.cam.ac.uk/techreports/UCAM-CL-TR-857.pdf). Howard (2014). *Technical Report UCAM-CL-TR-857*.

[Consensus in the Cloud: Paxos Systems Demystified](http://www.cse.buffalo.edu/~demirbas/publications/cloudConsensus.pdf) Ailijiang, Charapko, & Demirbas (2016). *The 25th International Conference on Computer Communication and Networks (ICCCN)*.

[Paxos made live: an engineering perspective](https://static.googleusercontent.com/media/research.google.com/en//archive/paxos_made_live.pdf). Chandra, Griesemer, & Redstone (2007). *Proceedings of the twenty-sixth annual ACM symposium on Principles of distributed computing*.

[Official overview website of materials on and implementations of Raft](https://raft.github.io/)

Video lecture on Raft: https://youtu.be/YbZ3zDzDnrw

#### **Figures**

The overview diagram was taken from [Consensus in the Cloud: Paxos Systems Demystified](http://www.cse.buffalo.edu/~demirbas/publications/cloudConsensus.pdf) (Ailijiang, Charapko, & Dermibas, 2016) and is figure 4 in the paper.


## Part 2: Overview of the Core Protocol

### **Assumptions**

The Raft protocol is based on the same set of assumptions as classic [Multi-Paxos](https://en.wikipedia.org/wiki/Paxos_(computer_science)#Multi-Paxos):

**Asynchronicity:
**No upper bound on message delays or computation times and no global clock synchronization.

**Unreliable Links:
**Possibility of indefinite networks delays, packet loss, partitions, reordering, or duplication of messages.

**Fail-Crash Failure Model:
**Processes may crash, may eventually recover, and in that case rejoin the protocol. [Byzantine failures](https://en.wikipedia.org/wiki/Byzantine_fault_tolerance) cannot occur.

### **Replicated State Machine Model**

[![img](https://blog.container-solutions.com/hs-fs/hubfs/Imported_Blog_Media/figure1_raft-1.png?width=497&name=figure1_raft-1.png)](https://info.container-solutions.com/hubfs/Imported_Blog_Media/figure1_raft-1.png)

Ongaro and Ousterhout, the authors of the Raft paper, state that consensus is usually used in the context of replicated state machines. Raft does *not* apply the idea of consensus to agreeing to single values (like classic Paxos), but instead to the **goal of finding agreement on the order with which operations are committed to a single replicated log.** 

With the objective of implementability in mind, drawing out a protocol that tightly integrates the actual consensus within the common use-case of the replicated state machine makes a lot of sense. Within Raft, the authors draw a clear distinction between the parts of such a replicated state machine: The state machine, the replicated log, and the consensus module.

The clients interact with the replicated state machine through the consensus module, which – **once a command has been committed to the log, that is agreed upon by consensus** – **guarantees that eventually the command will applied in the same order (the one specified in the leader’s log) on every live state machine**.

This mimics real-world applications, as they are seen in systems implemented before the publishing of Raft – like ZooKeeper, Chubby, Google File System, or BigTable. Even though aforementioned frameworks do not use Raft, it has been proposed as an alternative for future projects, as the published theoretically grounded specification of the algorithm does not include only the consensus module itself:

**Raft integrates consensus with the very common use case of the replicated state machine, giving the advantage that any guarantees proven theoretically will hold in the implementations, if the specification is closely followed.** As described in [part 1](http://container-solutions.com/raft-explained-part-1-the-consenus-problem/) of this blog series on Raft, it was shown that other consensus algorithm specifications suffered from poor implementability and possible loss of guarantees based on the large gap between theory and implementation practice.

Due to these lines of argumentation, newer implementations – like the one used within Hashicorp's [Consul](https://www.consul.io/) and [Nomad](https://www.nomadproject.io/) – have already switched to Raft as theoretical grounding for their consensus applications.

### **Strong Leadership**

A fundamental difference between Raft and Paxos as well as [Viewstamped Replication](https://blog.acolyer.org/2015/03/02/viewstamped-replication-a-new-primary-copy-method-to-support-highly-available-distributed-systems/), two of the main influences on the protocol, is that Raft implements strong leadership. Contrary to other protocols, who may defer leader election to an oracle (a PhD way of saying *magic black box*), Raft integrates leader election as an essential part of the consensus protocol. Once a leader has been elected, all decision-making within the protocol will then be driven only by the leader. Only one leader can exist at a single time and log entries can only flow from the leader to the followers.

Strong leadership extends classical leader-driven consensus by adding the following constraints:

- All message passing can only be initialized by the leader or a server attempting to become leader. This is enforced in the protocols specification through the modeling of all communications as RPCs, implicitly encoding a server’s role as either active or passive.
- The leader takes care of all communication with clients of the application and needs to be contacted directly by them.
- The system is only available when a leader has been elected and is alive. Otherwise, a new leader will be elected and the system will remain unavailable for the duration of the vote.



### **Terms**

To mitigate problems with clock synchronization in asynchronous systems, where the messages – through which clock synchronization may be negotiated – can have arbitrary delays, Raft uses a logical clock in the form of *terms*. Logical time uses the insight that no exact notion of time is needed to keep track of causality in a system. Each server has its own local view of time that is represented by its currentTerm. This currentTerm number increases monotonically over time, meaning that it can only go up.

Every communication in Raft includes an exchange and comparison of currentTerms. A term is only updated when a server (re-)starts an election or when the currentTerm of the party that a server communicates with is higher than its own, in which case the term get's updated with that higher value. Any communication attempt with a server of a higher term is always rejected and when a candidate or leader learns of a higher term than its own, it immediately returns to being a follower.

> 为了缓解异步系统中时钟同步的问题，在可以协商时钟同步的异步系统中，消息可以有任意延迟，Raft 使用 *term*（任期） 形式的逻辑时钟。**==逻辑时间的本质是不需要精确的时间来跟踪系统中的因果关系==**。每台服务器都有自己的本地时间视图，用 `currentTerm` 表示。`currentTerm` 随着时间单调增加，意味着它只能增加。
>
> Raft 的每次通信都包括 `currentTerm` 的交换和比较。只有当某个服务器（重新）开始一次选举，或者与服务器通信的另一方的 `currentTerm` 高于服务器自身 `currentTerm` 时候，才会更新 *term*，在这种情况下，会用这个更高的值进行更新 `currentTerm`。与较高任期的服务器进行的任何通信尝试都始终被拒绝，并且当**候选人** (candidate) 或**领导者** (leader) 得知其任职期比其本人更高的任期时，它将立即返回，并成为追随者(follower)。
>

### **Consensus Module**

#### **Roles**

[![img](https://blog.container-solutions.com/hs-fs/hubfs/Imported_Blog_Media/figure4_raft-1.png?width=600&name=figure4_raft-1.png)](https://info.container-solutions.com/hubfs/Imported_Blog_Media/figure4_raft-1.png)

The client of the application makes requests only to and gets responses only from the leader server. This means that the replicated state machine service can only be available when a leader has been successfully elected and is alive.

Each server participating in the protocol can only take one of the following roles at a time:

**Follower:
**Followers only respond to RPCs, but do not initiate any communication.

**Candidate:
**Candidates start a new election, incrementing the term, requesting a vote, and voting for themselves. Depending on the outcome of the election, become leader, follower (be outvoted or receive RPC from valid leader), or restart these steps (within a new term). **Only a candidate with a log that contains all** ***committed\*** **commands can become leader**.

**Leader:
**The leader sends heartbeats (empty AppendEntries RPCs) to all followers, thereby preventing timeouts in idle periods. For every command from the client, append to local log and start replicating that log entry, in case of replication on at least a majority of the servers, commit, apply commited entry to its own leader state machine, and then return the result to the client. If logIndex is higher than the nextIndex of a follower, append all log entries at the follower using RPC, starting from the his nextIndex.

All of these roles have a randomized time-out, on the elapse of which all roles assume that the leader has crashed and convert to be candidates, triggering a new election and incrementing the current term.

### **Log and State Machine Replication**

Once a server is established as leader, it becomes available to serve the clients requests. These requests are commands that are to be committed to the replicated state machine. For every received command, the leader assigns a term and index to the command, which gives a unique identifier within the server’s logs, and appends the command to its own log.

#### **Commitment**

If the leader is then able to replicate the command (through the AppendEntries RPC) across the logs of a strict majority of servers, the command is committed, applied to the leaders state machine, and the result of the operation returned to the client. Once a command is **safe to be applied to all replicated state machines**, that is **when a leader replicates a command** **from its current term** **to a majority of servers, it as well as – implicitly – all of the leaders preceding entries are considered \*committed\****.* (for the curious, see figure 8 in the original Raft paper for a detailed description and illustration why the term is so important)

The unidirectional flow of information from the leader to the followers and therefore the guarantee of identical ordering across all replicated logs on any participating server that is alive, lead to eventually consistent state across all replicated state machines: If a message gets delayed, lost, or a server is temporarily down and later comes back up, the follower will catch back up once he receives the next RPC from the leader. Once the leader becomes aware of the current state of that server through the RPC, it then appends all missing commands. It does so starting from the next expected index to the leader’s current log index, the latter being the last appended position on the leader log.

For every AppendEntries RPC performed on the follower, it performs a consistency check and rejects the new entry only if the logs match in their previous entry. This creates an [inductive](https://en.wikipedia.org/wiki/Mathematical_induction) property: If a follower accepts a new entry from the leader, it checks the consistency the leader’s previous entry with its own, which must have been accepted because of the consistency of the previous entry and so on. Because of this inductive property, the replicated log is guaranteed to match the leader’s log up until that last accepted entry.

Raft assumes that the leader’s log is always correct. The cleanup of inconsistent replicated state happens during normal operation and the protocol is designed in such a way that normal operation (at least half of the servers alive) converges all the logs.

*[Continue with Part 3/3](http://container-solutions.com/raft-explained-part-33-safety-liveness-guarantees-conclusion/): Why the algorithm works even on edge cases, with an overview of the algorithm’s safety and liveness properties. This is then followed by the conclusion on the insights gained from looking at the Raft protocol.*

### **References**

[In search of an understandable consensus algorithm](https://web.stanford.edu/~ouster/cgi-bin/papers/raft-atc14). Ongaro, Diego, & Ousterhout  (2014) *USENIX Annual Technical Conference (USENIX ATC 14)*.

[ARC: analysis of Raft consensus](https://www.cl.cam.ac.uk/techreports/UCAM-CL-TR-857.pdf). Howard (2014). *Technical Report UCAM-CL-TR-857*.

[Consensus in the Cloud: Paxos Systems Demystified](http://www.cse.buffalo.edu/~demirbas/publications/cloudConsensus.pdf) Ailijiang, Charapko, & Demirbas (2016). *The 25th International Conference on Computer Communication and Networks (ICCCN)*.

[Paxos made live: an engineering perspective](https://static.googleusercontent.com/media/research.google.com/en//archive/paxos_made_live.pdf). Chandra, Griesemer, & Redstone (2007). *Proceedings of the twenty-sixth annual ACM symposium on Principles of distributed computing*.

[Official overview website of materials on and implementations of Raft](https://raft.github.io/)

Video lecture on Raft: https://youtu.be/YbZ3zDzDnrw

#### **Figures**

Both of the figures were extracted from the original [Raft PhD thesis](http://web.stanford.edu/~ouster/cgi-bin/papers/OngaroPhD.pdf), figure 1 in this document being figure 2.1 in the original and figure 2 being figure 3.3 in the original.


## Part 3 Safety and Liveness Guarantees, Conclusion
### **Safety and Liveness**

The combination of leader election and log replication commitment rules provide Raft’s safety guarantees: Correctness and availability of the system remains guaranteed as long as a majority of the servers remain up.

*Safety* can be informally described as *nothing bad happens* and *liveness* as *something good eventually happens*.

#### **Elections**

**Safety:** For each term, every server gives out one and only one vote and, consequently, two different candidates can never accumulate majorities within the same term. This vote needs to be reliably persisted on disk, in order to account for the possibility of servers failing and recovering within the same term.

> **安全性：**每个任期内，每个服务器只投一票，因此，两名不同的**==候选者==**决不能在同一任期内累积多数票。这种投票需要可靠地保存在磁盘上，以解决服务器在同一任期内发生故障和恢复的可能性。

**Liveness:** Theoretically, competing candidates could cause repeated split votes. Raft mitigates this by having each participating server individually choose a new random timeout within each given interval. This will lead to a situation, where usually there is only one server awake, which can then win the election while every other server is still asleep. This works best if the lower bound of the chosen interval is considerably larger than the broadcast time.

#### **Commitment**

**Safety** in the commitment step is guaranteed by only allowing commitment for log entries that are replicated on a majority of servers **and** that are from the current leader’s term or the items preceding an AppendEntries RPC (may also be a heartbeat/ empty RPC) from the current term. The moment a leader decides an entry is committed, it is from then on guaranteed that this entry will be contained in the logs of all future leaders.

> **安全性：**只允许提交已经在大多数服务器上完成复制的日志记录，这些日志记录来自当前**==领导者==**任期或当前任期之前的 `AppendEntries RPC`（也可能是心跳/空的 RPC）。**==领导者==**确定提交日志记录的那一刻，即确保此条日志记录将包含在未来所有**==领导者==**的日志中。

A leader can never overwrite entries in their own log, they can only append. In order to be committed, the entry must be present in the leader’s log and, only once committed, can be applied to the state machine. This gives us the combined property that once a log entry has been applied to one state machine, no other command may be applied to a different state machine for that specific log entry (specified by term and index).

> **==领导者==**永远不会覆盖自己日志中的记录，他们只能追加。为了提交，记录必须出现在领导者的日志中，并且只有在提交之后，才能应用于状态机。这为我们提供了一个组合性质，即一旦将某条日志记录应用到**==状态机==**，这条日志记录对应的任期和日志索引，就不会向状态机应用别的日志。

#### **Picking the Best Leader**

**Safety:** In leader elections, candidates include the lastTerm and lastIndex of their last log entry and, based on this information, other servers, deny their vote if their own log is more complete. This is the case if the voting server has either participated in a higher term or the same term, but having a higher lastIndex (and therefore a longer log).

#### **Making Logs Consistent**

**Safety:** If an AppendEntries RPC to a follower fails because there are missing entries, for every failed call the nextIndex of that follower will be decremented and the call will be tried again until eventually the last item in the log is reached and the log filled up to mirror the leader’s log.

In case an AppendEntries RPC to a follower returns that there is already an entry at that point in the followers log, this entry will be considered extraneous – as the leader’s log is considered authoritative – and be overwritten. This will also invalidate all consecutive entries on the follower, as all entries following an extraneous entry are also extraneous.

#### **Neutralizing Old Leaders**

**Safety:** A leader might be separated from a majority of other servers by a partition and during this time, these would then elect a new leader. With a new leader in place, the protocol will continue on that side of the partition, but once the deposed leader becomes reconnected to the rest of the servers, the protocol needs to take care of the situation where two servers think they are leader. The stale leader would behave according to its perceived role and try to replicate logs, talk to the client, record logs, or commit log entries. This would be unsafe and needs to be dealt with by the protocol.

Terms are the mechanism by which the protocol takes care of stale leaders. All possible RPCs always include the term of the sender and the comparison of sender’s to receiver’s term leads to the updating of the more out-of-date. Additionally, in case of an older sender’s term, the RPC gets rejected and the sender reverts to follower or, in case of an older receiver’s term, the receiver reverts to follower and then processes the RPC normally.

The key to understanding safety in regard to any situation with stale leaders or candidates is to become aware that any election necessarily updates the terms within a majority of servers. Therefore, once a leader is stale, it is impossible within the mechanics of the protocol for it to commit any new log entries.

#### **Client Relations**

Clients of the Raft protocol interact with it through the leader. Only once a command has been logged, committed, and executed on the state machine of the leader, will he then respond.

**Availability:** If the leader is unknown or the request times out (e.g. leader crash), the client may contact any of the servers, which will then redirect to the (new) leader, where the request will then be retried. This guarantees that the command will eventually be executed if a majority of the servers are still alive.

**Safety:** Yet, in order to make sure that commands are not duplicated, there is an additional property: Every command is paired with a unique command id, which assures that the leader is aware of the situation and that execution of the command as well as the response happen only once for every unique command.

#### **Configuration Changes**

In Raft the process of applying configuration changes in regard to the participating servers – a need in real-world systems – is part of the specification of the protocol. Without this mechanism, changes in configuration could be obtained through taking the cluster off-line, then taking it online again with a new configuration, but this would not retain availability.

Essentially, the changes in a configuration C are encapsulated within a two-phase transition, during which there is overlapping partial knowledge of the configuration changes C_old→C_old+new→C_new across the servers:

**In the first phase,
**the client requests the leader to transition from C_old to C_new. The leader then issues an AppendEntry RPC to add a special entry to the log containing the configuration C_old+new. This intended configuration change then gets proliferated through log replication.

As soon as a server adds a configuration change entry to its log, it starts to immediately act based on that knowledge of C_old+new (not just after commit!). Until the commit, it is still possible that a majority of the servers, without the new knowledge, act purely based on the old configuration and take over the cluster (i.e. if the leader crashes).

Once the configuration change log entry has been replicated to a majority of servers, the next phase begins as now consensus cannot be reached based on C_old anymore.

**The second phase
**starts the moment that the log entry with the configuration change has been committed. Starting from this point in time, the protocol needs a majority of the union of the old as well as new configuration, that is C_old+new, for commitment.

Under these circumstances, it is safe for the leader to issue another log entry that specifies C_new and to replicate it across the other servers. Once appended to a server’s log, this configuration change will again immediately take effect on that server. Once this new configuration is committed through replication on a majority of C_new, the old configuration becomes irrelevant and the now superfluous servers may then be shut down.

**Availability** is guaranteed in this scenario, as the cluster remains up during the entire reconfiguration process and progress is guaranteed by the same mechanisms as the rest of the protocol.

**Safety** is guaranteed, as it is not possible at any point of the transition that the cluster may split into two independent majorities and so it is impossible for two leaders to be elected at the same time.

### **Conclusion**

In theoretical computer science, there comes great benefit from reducing problems to their abstract core. Paxos has taken this approach and revolutionized distributed systems by showing how fault-tolerant consensus can be made workable. Since then, many years have passed and experience dealing with Paxos in both understanding and implementation have shown that it is in some aspects both hard to understand and far from the real-world challenges of implementations.

Understanding is made unnecessarily hard by a state-space that is larger than needed to have the same guarantees as Paxos. Specifically, when used as Multi-Paxos within a replicated state machine, functional segregation of the intertwined parts of the protocol is not given with the same rigor and formal specification as with Raft. Implementation of Paxos is hard, as the protocol is specified in a way that is detached from real-world implementation issues and use cases: The original guarantees and proofs given in theory may not hold anymore in practice. This leads to Paxos implementations needing extensive proofs and verification of their own, detaching them further from the original theoretical results.

In my opinion, the Raft paper is exceptional in that it combines insights into the nature of real-world use-cases and the power of abstract reasoning and combines the two in a way that is not only understandable and tractable while giving hard theoretical guarantees, but also easily reproducible in real-world systems.

### **References**

[In search of an understandable consensus algorithm](https://web.stanford.edu/~ouster/cgi-bin/papers/raft-atc14). Ongaro, Diego, & Ousterhout  (2014) *USENIX Annual Technical Conference (USENIX ATC 14)*.

[ARC: analysis of Raft consensus](https://www.cl.cam.ac.uk/techreports/UCAM-CL-TR-857.pdf). Howard (2014). *Technical Report UCAM-CL-TR-857*.

[Consensus in the Cloud: Paxos Systems Demystified](http://www.cse.buffalo.edu/~demirbas/publications/cloudConsensus.pdf) Ailijiang, Charapko, & Demirbas (2016). *The 25th International Conference on Computer Communication and Networks (ICCCN)*.

[Paxos made live: an engineering perspective](https://static.googleusercontent.com/media/research.google.com/en//archive/paxos_made_live.pdf). Chandra, Griesemer, & Redstone (2007). *Proceedings of the twenty-sixth annual ACM symposium on Principles of distributed computing*.

[Official overview website of materials on and implementations of Raft](https://raft.github.io/)

Video lecture on Raft: https://youtu.be/YbZ3zDzDnrw



# In Search of an Understandable Consensus Algorithm (Extended Version)

## 5 The Raft consensus algorithm
### 5.2 Leader election

Raft uses a heartbeat mechanism to trigger leader election. When servers start up, they begin as followers. A server remains in follower state as long as it receives valid RPCs from a leader or candidate. Leaders send periodic heartbeats (AppendEntriesRPCs that carry no log entries) to all followers in order to maintain their authority. If a follower receives no communication over a period of time called the election timeout, then it assumes there is no viable leader and begins an election to choose a new leader.

> Raft 使用心跳机制触发选举。当服务器启动时，以**==跟随者==**的身份开始。只要服务器从**==领导者==**或**==候选者==**那里收到有效 **RPC**，就**==一直==**保持跟随者状态。**==领导者==**会定期向所有**==跟随者==**发送心跳（不携带日志记录的`AppendEntriesRPC`），以维持其权威。如果跟随者在一段称为**<u>==选举超时的时间==</u>**内没有收到任何通信，那么它将假定当前**==领导者==**不存在，并开始选举以选取新的领导者。

To begin an election, a follower increments its current term and transitions to candidate state. It then votes for itself and issues RequestVote RPCs in parallel to each of the other servers in the cluster. A candidate continues in this state until one of three things happens: (a) it wins the election, (b) another server establishes itself as leader, or (c) a period of time goes by with no winner. These outcomes are discussed separately in the paragraphs below.

> 三种选举结果
> 1. 赢得了多数的选票，成功选举为Leader；
> 2. 收到了Leader的消息，表示有其它服务器已经抢先当选了Leader；
> 3. 没有服务器赢得多数的选票，Leader选举失败，等待选举时间超时后发起下一次选举。

A candidate wins an election if it receives votes from a majority of the servers in the full cluster for the same term. Each server will vote for at most one candidate in a given term, on a first-come-first-served basis (note: Section 5.4 adds an additional restriction on votes). The majority rule ensures that at most one candidate can win the election for a particular term (the Election Safety Property in Figure 3). Once a candidate wins an election, it becomes leader. It then sends heartbeat messages to all of the other servers to establish its authority and prevent new elections.

While waiting for votes, a candidate may receive an AppendEntries RPC from another server claiming to be leader. If the leader’s term (included in its RPC) is at least as large as the candidate’s current term, then the candidate recognizes the leader as legitimate and returns to follower state. If the term in the RPC is smaller than the candidate’s current term, then the candidate rejects the RPC and continues in candidate state.

The third possible outcome is that a candidate neither wins nor loses the election: if many followers become candidates at the same time, votes could be split so that no candidate obtains a majority.When this happens, each candidate will time out and start a new election by incrementing its term and initiating another round of Request-Vote RPCs. However, without extra measures split votes could repeat indefinitely.

> 第三中情况是 **split vote**

Raft uses randomized election timeouts to ensure that split votes are rare and that they are resolved quickly. To prevent split votes in the first place, election timeouts are chosen randomly from a fixed interval (e.g., 150–300ms). This spreads out the servers so that in most cases only a single server will time out; it wins the election and sends heartbeats before any other servers time out. The same mechanism is used to handle split votes. Each candidate restarts its randomized election timeout at the start of an election, and it waits for that timeout to elapse before starting the next election; this reduces the likelihood of another split vote in the new election. Section 9.3 shows that this approach elects a leader rapidly.

> 用随机时间以保证大多数时候只有一个**==候选者==**。

Elections are an example of how understandability guided our choice between design alternatives. Initially we planned to use a ranking system: each candidate was assigned a unique rank, which was used to select between competing candidates. If a candidate discovered another candidate with higher rank, it would return to follower state so that the higher ranking candidate could more easily win the next election. We found that this approach created subtle issues around availability (a lower-ranked server might need to time out and become a candidate again if a higher-ranked server fails, but if it does so too soon, it can reset progress towards electing a leader). We made adjustments to the algorithm several times, but after each adjustment new corner cases appeared. Eventually we concluded that the randomized retry approach is more obvious and understandable.

> 选举就是一个例子，说明可理解性如何指导我们选择设计方案之。最初，我们计划使用排名系统：为每个候选人分配一个唯一的排名，该排名用于在竞争候选人之间进行选择。 如果某个候选人发现了另一个排名较高的候选人，它将返回到**==跟随者==**状态，以便排名较高的候选人可以更轻松地赢得下一次选举。我们发现，这种方法在可用性方面造成了一些细微的问题（如果排名较高的服务器发生故障，排名较低的服务器可能需要超时并再次成为候选服务器，但如果过早这样做，则可能重置**==领导者==**选举的进度）。 对算法进行了数次调整，但每次调整后都会出现新的极端情况。 最终，我们得出结论，随机重试方法更加明显和易于理解。

### 5.3  Log replication

Once a leader has been elected, it begins servicing client requests. Each client request contains a command to be executed by the replicated state machines. The leader appends the command to its log as a new entry, then issues AppendEntries RPCs in parallel to each of the other servers to replicate the entry. When the entry has been safely replicated (as described below), the leader applies the entry to its state machine and returns the result of that execution to the client. If followers crash or run slowly, or if network packets are lost, the leader retries `AppendEntries` RPCs indefinitely (even after it has responded to the client) until all followers eventually store all log entries.

Logs are organized as shown in Figure 6. Each log entry stores a state machine command along with the term number when the entry was received by the leader. The term numbers in log entries are used to detect inconsistencies between logs and to ensure some of the properties in Figure 3. Each log entry also has an integer index identifying its position in the log.

The leader decides when it is safe to apply a log entry to the state machines; such an entry is called committed. Raft guarantees that committed entries are durable and will eventually be executed by all of the available state machines. A log entry is committed once the leader that created the entry has replicated it on a majority of the servers (e.g., entry 7 in Figure 6). This also commits all preceding entries in the leader’s log, including entries created by previous leaders. Section 5.4 discusses some subtleties when applying this rule after leader changes, and it also shows that this definition of commitment is safe. The leader keeps track of the highest index it knows to be committed, and it includes that index in future AppendEntries RPCs (including heartbeats) so that the other servers eventually find out. Once a follower learns that a log entry is committed, it applies the entry to its local state machine (in log order).

We designed the Raft log mechanismto maintain a high level of coherency between the logs on different servers. Not only does this simplify the system’s behavior and make itmore predictable, but it is an important component of ensuring safety. Raft maintains the following properties, which together constitute the Log Matching Property in Figure 3:

- If two entries in different logs have the same index and term, then they store the same command.

- If two entries in different logs have the same index and term, then the logs are identical in all preceding entries.

The first property follows from the fact that a leader creates at most one entry with a given log index in a given term, and log entries never change their position in the log. The second property is guaranteed by a simple consistency check performed by AppendEntries.When sending an AppendEntries RPC, the leader includes the index and term of the entry in its log that immediately precedes the new entries. If the follower does not find an entry in its log with the same index and term, then it refuses the new entries. The consistency check acts as an induction step: the initial empty state of the logs satisfies the Log Matching Property, and the consistency check preserves the Log Matching Property whenever logs are extended. As a result, wheneverAppendEntries returns successfully, the leader knows that the follower’s log is identical to its own log up through the new entries.

During normal operation, the logs of the leader and followers stay consistent, so the AppendEntries consistency check never fails. However, leader crashes can leave the logs inconsistent (the old leader may not have fully replicated all of the entries in its log). These inconsistencies can compound over a series of leader and follower crashes. Figure 7 illustrates the ways in which followers’ logs may differ from that of a new leader. A follower may be missing entries that are present on the leader, it may have extra entries that are not present on the leader, or both. Missing and extraneous entries in a log may span multiple terms.

In Raft, the leader handles inconsistencies by forcing the followers’ logs to duplicate its own. This means that conflicting entries in follower logs will be overwritten with entries from the leader’s log. Section 5.4 will show that this is safe when coupled with one more restriction.

To bring a follower’s log into consistency with its own, the leader must find the latest log entry where the two logs agree, delete any entries in the follower’s log after that point, and send the follower all of the leader’s entries after that point. All of these actions happen in response to the consistency check performed by `AppendEntries` RPCs. The leader maintains a nextIndex for each follower, which is the index of the next log entry the leader will send to that follower.When a leader first comes to power, it initializes all nextIndex values to the index just after the last one in its log (11 in Figure 7). If a follower’s log is inconsistent with the leader’s, the AppendEntries consistency check will fail in the next AppendEntries RPC. After a rejection, the leader decrements nextIndex and retries the AppendEntries RPC. Eventually nextIndex will reach a point where the leader and follower logs match. When this happens,AppendEntrieswill succeed, which removes any conflicting entries in the follower’s log and appends entries fromthe leader’s log (if any). Once AppendEntries succeeds, the follower’s log is consistentwith the leader’s, and it will remain that way for the rest of the term.

If desired, the protocol can be optimized to reduce the number of rejected AppendEntries RPCs. For example, when rejecting an AppendEntries request, the follower can include the term of the conflicting entry and the first index it stores for that term. With this information, the leader can decrement nextIndex to bypass all of the conflicting entries in that term; one AppendEntries RPC will be required for each term with conflicting entries, rather than one RPC per entry. In practice, we doubt this optimization is necessary, since failures happen infrequently and it is unlikely that there will be many inconsistent entries.

With this mechanism, a leader does not need to take any special actions to restore log consistency when it comes to power. It just begins normal operation, and the logs automatically converge in response to failures of the `AppendEntries` consistency check. A leader never overwrites or deletes entries in its own log (the Leader Append-Only Property in Figure 3).

This log replication mechanism exhibits the desirable consensus properties described in Section 2: Raft can accept, replicate, and apply new log entries as long as a majority of the servers are up; in the normal case a new entry can be replicated with a single round of RPCs to a majority of the cluster; and a single slow follower will not impact performance.

### 5.4 Safety

The previous sections described how Raft elects leaders and replicates log entries. However, the mechanisms described so far are not quite sufficient to ensure that each state machine executes exactly the same commands in the same order. For example, a follower might be unavailable while the leader commits several log entries, then it could be elected leader and overwrite these entries with new ones; as a result, different state machines might execute different command sequences.

This section completes the Raft algorithm by adding a restriction on which servers may be elected leader. The restriction ensures that the leader for any given term contains all of the entries committed in previous terms (the Leader Completeness Property from Figure 3). Given the election restriction, we then make the rules for commitment more precise. Finally, we present a proof sketch for the Leader Completeness Property and show how it leads to correct behavior of the replicated state machine.

> 前面的部分描述了 Raft 如何选举领导者并复制日志。**==但是，到目前为止描述的机制还不足以确保每个状态机以相同的顺序执行完全相同的命令==**。 例如，当**==领导者==**提交多个日志条目时，**==跟随者==**可能不可用，随后，它可以当选为新的**==领导者==**并<u>用新的日志覆盖旧的日志</u>。 结果是不同的状态机可能执行不同的命令序列。
>
> 本节通过<u>==限制哪些服务器可被选为领导者==</u>来完善 Raft 算法。该限制可确保任何给定任期的领导者都包含先前任期中提交的所有日志（图3中的**==领导者完备性==**属性）。 给定选举限制，然后我们使**提交**规则更加精确。 最后，我们为领导者完备性提供了一个证明草图，并显示了它如何导致复制状态机的正确行为。

#### 5.4.1 Election restriction

> 拥有最新的已提交的 log entry 的 Follower 才有资格成为 Leader。

In any leader-based consensus algorithm, the leader must eventually store all of the committed log entries. In some consensus algorithms, such as Viewstamped Replication [22], a leader can be elected even if it doesn’t initially contain all of the committed entries. These algorithms contain additional mechanisms to identify the missing entries and transmit them to the new leader, either during the election process or shortly afterwards. Unfortunately, this results in considerable additional mechanism and complexity. Raft uses a simpler approach where it guarantees that all the committed entries from previous terms are present on each new leader from the moment of its election, without the need to transfer those entries to the leader. This means that log entries only flow in one direction, from leaders to followers, and leaders never overwrite existing entries in their logs.

Raft uses the voting process to prevent a candidate from winning an election unless its log contains all committed entries. A candidate must contact a majority of the cluster in order to be elected, which means that every committed entrymust be present in at least one of those servers. If the candidate’s log is at least as up-to-date as any other log in that majority (where “up-to-date” is defined precisely below), then it will hold all the committed entries. The RequestVote RPC implements this restriction: the RPC includes information about the candidate’s log, and the voter denies its vote if its own log is more up-to-date than that of the candidate.

Raft determines which of two logs is more up-to-date by comparing the index and term of the last entries in the logs. If the logs have last entries with different terms, then the log with the later term is more up-to-date. If the logs end with the same term, then whichever log is longer is more up-to-date.

> Raft通过比较日志中最后一条记录的索引和任期来确定两个日志中哪一个是最新。如果日志的最后一条记录具有不同的任期，那么任期更新的日志较新。如果日志以相同的任期结束，则索引较长的日志为最新日志。

#### 5.4.2 Committing entries from previous terms

As described in Section 5.3, a leader knows that an entry from its current term is committed once that entry is stored on a majority of the servers. If a leader crashes before committing an entry, future leaders will attempt to finish replicating the entry. However, a leader cannot immediately conclude that an entry from a previous term is committed once it is stored on a majority of servers. Figure 8 illustrates a situation where an old log entry is stored on a majority of servers, yet can still be overwritten by a future leader.

> 如 5.3 节所述，**==领导者==**知道，一旦当前日志存储在大多数服务器上，就会提交该日志。如果领导者在提交日志之前崩溃，则未来的领导者将尝试完成复制条目。但是，即使已经在大多数服务器上存储了上一个**==任期==**的日志，未来的**==领导者==**也不能立即断定日志是否已被提交。图 8 说明了这样一种情况，未来的**==领导者==**仍然可以覆盖旧的日志，即使他它们已经存储在大多数服务器上。

<p align="center">
<img src=https://pic3.zhimg.com/80/v2-12a5ebab63781f9ec49e14e331775537_720w.jpg> 图8 覆盖已提交的日志<br><p align="left"> A time sequence showing why a leader cannot determine commitment using log entries from older terms. In (a) S1 is leader and partially replicates the log entry at index 2. In (b) S1 crashes; S5 is elected leader for term 3 with votes from S3, S4, and itself, and accepts a different entry at log index 2. In (c) S5 crashes; S1 restarts, is elected leader, and continues replication. At this point, the log entry from term 2 has been replicated on a majority of the servers, but it is not committed. If S1 crashes as in (d), S5 could be elected leader (with votes from S2, S3, and S4) and overwrite the entry with its own entry from term 3. However, if S1 replicates an entry from its current term on a majority of the servers before crashing, as in (e), then this entry is committed (S5 cannot win an election). At this point all preceding entries in the log are committed as well.<br> 显示新的领导者<u>无法使用旧日志来确定是否已提交</u>的时序图。在（a）中，S1 是领导者，索引 2 处的日志被部分复制。在（b）中，S1 崩溃，S5 在 S3 和 S4 及其自身的投票下当选为第 3 届任期的领导者，并在索引 2 处接受了不同的日志。S1 重新启动，重新当选为领导者，然后继续复制。至此，已在大多数服务器上复制了任期 2 的日志，但尚未提交。如果 S1 像（d）中那样崩溃，则 S5 可以被选为领导者（来自S2，S3和S4的投票），并用自己任期 3 的日志覆盖其他服务器的日志。但是，如果 S1 在崩溃前，在大多数服务器上从其当前任期复制了一条日志，如（e）所示，则该日志已提交（S5 无法赢得选举）。此时，也将提交先期为提交的日志。
</p></p>

To eliminate problems like the one in Figure 8, Raft never commits log entries from previous terms by counting replicas. Only log entries from the leader’s current term are committed by counting replicas; once an entry from the current term has been committed in this way, then all prior entries are committed indirectly because of the Log Matching Property. There are some situations where a leader could safely conclude that an older log entry is committed (for example, if that entry is stored on every server), but Raft takes a more conservative approach for simplicity.

> 为了消除类似图8中的问题，Raft 从不通过计算副本数来提交先前任期中的日志。只有**==领导者==**当前任期内的日志，才通过计算副本数提交； 一旦以这种方式提交了当前任期内的日志，则由于日志匹配属性，将会间接提交先前所有的日志。在某些情况下，**==领导者==**可以安全地得出结论，旧的日志已被提交（例如，如果该日志在每台服务器都有存储），但是为了简单起见，Raft 采取了更保守的方法。

Raft incurs this extra complexity in the commitment rules because log entries retain their original term numbers when a leader replicates entries from previous terms. In other consensus algorithms, if a new leader rereplicates entries from prior “terms,” it must do so with its new “term number.” Raft’s approach makes it easier to reason about log entries, since they maintain the same term number over time and across logs. In addition, new leaders in Raft send fewer log entries from previous terms than in other algorithms (other algorithms must send redundant log entries to renumber them before they can be committed).

> 因为当**==领导者==**从先前任期中复制日志时，日志会保留其原始任期编号，导致了 Raft 在提交规则中增加了这种额外的复杂性。在其他共识算法中，如果新**==领导者==**从以前的任期复制日志，则必须使用其新的任期编号进行复制。Raft 的方法使得对日志记录的推理变得更容易，<u>==因为随着时间的推移，日志记录在多个日志文件中都保持相同的任期号==</u>。另外，Raft 中新**==领导者==**发送先前任期的日志记录比其他算法少（其他算法必须发送冗余的日志记录，以便在提交之前对它们重新编号）。







