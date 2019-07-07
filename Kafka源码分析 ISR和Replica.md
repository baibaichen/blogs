# Kafka源码分析 ISR和Replica

接Kafla_LogAppend,消息存储到Leader的Partition之后,ISR保证Partition的复制…

## Partition & Replica

首先来看Partition的Replication副本是个什么概念。每个Partition都可以有多个Replication。其中Leader副本负责读写，Follower副本负责从Leader拉取数据。Replica分布在不同的Broker上。所以Replica只需要两个属性: **Partition**(分区), **brokerid**(所在的Kafka节点).

[![k_partition](https://images.weserv.nl/?url=http://img.blog.csdn.net/20160114142830405)](https://images.weserv.nl/?url=http://img.blog.csdn.net/20160114142830405)

一个Replication有两个重要的元数据: **HighWatermark**和**LogEndOffset**.

- **HighWatermark**：用来确保消费者能获取到的消息的最高水位，超过这个水位的消息是不会被客户端看到的。由于Leader负责读写，所以*HW*只能由Leader更新，但是怎么时候更新，可能由follower在更新*LEO*时通知Leader修改。
- **LogEndOffset**：所有Replica都会有，Leader在消息追加后会更新，follower在从Leader==抓取消息==后也会更新。

[![k_update_leo](https://images.weserv.nl/?url=http://img.blog.csdn.net/20160114145533803)](https://images.weserv.nl/?url=http://img.blog.csdn.net/20160114145533803)

> 问:最后一个可选的Log是怎么用来判断是否是本地的Replica? Local和Remote又是针对什么而言?
> 答:==一个Partition的所有Replicas都是保存在Leader节点的内存中的. 而不是让每个节点自己管理自己的信息,如果这样的话,每个Kafka节点的信息就都是不一样的! 而分布式集群管理是要有一个中心来管理所有节点信息的.==
> 因为现在是在Leader节点上,并且是由Leader管理所有的Replicas,Leader自己就是Local,其他Replics都是Remote!
> 即`Leader Replica = Local Replica`, `Follower Repicas = Remote Replicas`.

> 问:因为Partitions是分布在不同的Kafka节点,本身每个节点记录的Partitions就是不一样的了.
> 答**:没错,分布式节点保存的信息是不一样的,但是同一个Partition的所有Replicas应该是交给Leader管理的.**
> **而follower上的Replicas并不需要管理自己的状态,因为Leader替他们管理好了.**

[![k_local_remote](https://images.weserv.nl/?url=http://img.blog.csdn.net/20160114153213150)](https://images.weserv.nl/?url=http://img.blog.csdn.net/20160114153213150)

```scala
class Replica(val brokerId: Int, val partition: Partition, time: Time = SystemTime, initialHighWatermarkValue: Long = 0L, val log: Option[Log] = None) {
  // the high watermark offset value, in non-leader replicas only its message offsets are kept
  @volatile private[this] var highWatermarkMetadata: LogOffsetMetadata = new LogOffsetMetadata(initialHighWatermarkValue)
  // the log end offset value, kept in all replicas;
  // for local replica it is the log's end offset, for remote replicas its value is only updated by follower fetch
  @volatile private[this] var logEndOffsetMetadata: LogOffsetMetadata = LogOffsetMetadata.UnknownOffsetMetadata

  def isLocal: Boolean = log.isDefined // 创建Replica时指定Log时, 则表示是本地的Replication

  // 设置LEO: 不应该在Local Replication的Partition上设置LEO. 对于Local Replica,它是由Log的EndOffset指定,而不能调用改update方法
  private def logEndOffset_=(newLogEndOffset: LogOffsetMetadata) {
    if (!isLocal) logEndOffsetMetadata = newLogEndOffset
  }
  // 获取LEO
  def logEndOffset = if (isLocal) log.get.logEndOffsetMetadata else logEndOffsetMetadata

  // 设置HW: 不应该在非Local Replication的Partition上设置HW, 即只能在Local Replica上设置, Local Replica也是Leader Replica
  def highWatermark_=(newHighWatermark: LogOffsetMetadata) {
    if (isLocal) highWatermarkMetadata = newHighWatermark
  }
  // 获取HW
  def highWatermark = highWatermarkMetadata
}
```

在Partition中创建Replica，如果不在`assignedReplicaMap`中，根据是否是本地(Leader)来创建Replica实例。Local的Replica比Remote的多了offset和Log. Replica的isLocal()会根据是否有Log判断是不是Local.

```scala
def getOrCreateReplica(replicaId: Int = localBrokerId): Replica = {
  val replicaOpt = getReplica(replicaId)
  replicaOpt match {
    case Some(replica) => replica
    case None =>
      if (isReplicaLocal(replicaId)) {
        val config = LogConfig.fromProps(logManager.defaultConfig.originals, AdminUtils.fetchEntityConfig(zkUtils, ConfigType.Topic, topic))
        // TopicAndPartition的Log
        val log = logManager.createLog(TopicAndPartition(topic, partitionId), config)
        // log.dirs是所有TopicAndPartition的父目录, 而checkpoints也是全局的
        val checkpoint = replicaManager.highWatermarkCheckpoints(log.dir.getParentFile.getAbsolutePath)
        val offsetMap = checkpoint.read
        // checkpoints中记录了所有Partition的offset信息, 所以可以根据TopicAndPartition找到对应的offset
        if (!offsetMap.contains(TopicAndPartition(topic, partitionId))) info("No checkpointed highwatermark is found for partition [%s,%d]".format(topic, partitionId))
        val offset = offsetMap.getOrElse(TopicAndPartition(topic, partitionId), 0L).min(log.logEndOffset)
        // 本地的是Leader, 所以要给出offset和Log.
        val localReplica = new Replica(replicaId, this, time, offset, Some(log))
        addReplicaIfNotExists(localReplica)
      } else {
        // 远程的是follower.
        val remoteReplica = new Replica(replicaId, this, time)
        addReplicaIfNotExists(remoteReplica)
      }
      getReplica(replicaId).get
  }
}
```

注意上面的`log.dir`是TopicAndPartition的目录(每个TopicAndPartition目录是唯一的)。`log.dir.getParentFile`指的是配置文件中的`log.dirs`，而不是Partition的目录，而`log.dirs`是server.properties的log.dirs配置项,它是所有TopicAndPartition的父目录.

> getOrCreateReplica被调用的地方是在ReplicaManager.becomeLeaderOrFollower->makeFollowers

### ReplicaManager->OffsetCheckpoint

Local Replica的offset来源于High watermark的checkpoint(因为HW很重要,所有需要做检查点).
ReplicaManager的highWatermarkCheckpoints是一个Map:日志目录(log.dirs)->OffsetCheckpoint.

```
val highWatermarkCheckpoints = config.logDirs.map(dir => 
  (new File(dir).getAbsolutePath, new OffsetCheckpoint(new File(dir, ReplicaManager.HighWatermarkFilename)))
).toMap
```

下面的/data/kafka目录是server.properties中的log.dirs的配置项,会在这个目录下生成checkpoint文件.

```
➜  kafka  ll                                                                        ⬅️ log.dirs目录
-rw-r--r--  1 zhengqh  staff     0B  1 14 16:13 recovery-point-offset-checkpoint
-rw-r--r--  1 zhengqh  staff    18B  1 14 16:13 replication-offset-checkpoint       ⬅️ offset-checkpoint文件
drwxr-xr-x  4 zhengqh  staff   136B  1 14 16:13 wikipedia-0
➜  kafka  ll wikipedia-0                                                            ⬅️ topic-partition目录         
-rw-r--r--  1 zhengqh  staff    10M  1 14 16:13 00000000000000000000.index          ⬅️ Segment日志文件和索引文件
-rw-r--r--  1 zhengqh  staff     0B  1 14 16:13 00000000000000000000.log
```

ReplicaManager是管理所有的Partition的,而checkpoints里记录的offsetMap是所有Partition共用的.
所以可以根据某个特定的TopicAndPartition找到它在checkpoints中对应的offset,用来创建Replica.

[![k_offset_checkpoint](https://images.weserv.nl/?url=http://img.blog.csdn.net/20160114163702208)](https://images.weserv.nl/?url=http://img.blog.csdn.net/20160114163702208)

因为OffsetCheckpoint记录的是TopicAndPartition到offset的映射关系.所以这个类中只是文件的读写操作.

```scala
class OffsetCheckpoint(val file: File) extends Logging {
  def write(offsets: Map[TopicAndPartition, Long]) {
    // write the current version and the number of entries, then the entries, finally flush to disk
    offsets.foreach { case (topicPart, offset) =>
        writer.write("%s %d %d".format(topicPart.topic, topicPart.partition, offset))
        writer.newLine()
    }
  }
  def read(): Map[TopicAndPartition, Long] = {
    var offsets = Map[TopicAndPartition, Long]()
    line = reader.readLine()
    while(line != null) {
        val pieces = line.split("\\s+")              
        val topic = pieces(0)
        val partition = pieces(1).toInt
        val offset = pieces(2).toLong
        offsets += (TopicAndPartition(topic, partition) -> offset)
        line = reader.readLine()
    }
  }
}
```

ReplicaManager是在`becomeLeaderOrFollower`调度Checkpoint的写入,这个线程是定时运行的,确保HW是最新的.

```
private val highWatermarkCheckPointThreadStarted = new AtomicBoolean(false)   // 原子变量,确保只有一个线程刷写checkpoint文件
private val allPartitions = new Pool[(String, Int), Partition]                // 所有的Partitions

def startHighWaterMarksCheckPointThread() = {
  if(highWatermarkCheckPointThreadStarted.compareAndSet(false, true))
    scheduler.schedule("highwatermark-checkpoint", checkpointHighWatermarks, period = config.replicaHighWatermarkCheckpointIntervalMs, unit = TimeUnit.MILLISECONDS)
}

// Flushes the highwatermark value for all partitions to the highwatermark file 把所有Partitions的HW值刷写到文件中
def checkpointHighWatermarks() {
  val replicas = allPartitions.values.map(_.getReplica(config.brokerId)).collect{case Some(replica) => replica}
  val replicasByDir = replicas.filter(_.log.isDefined).groupBy(_.log.get.dir.getParentFile.getAbsolutePath)
  for((dir, reps) <- replicasByDir) {
    val hwms = reps.map(r => (new TopicAndPartition(r) -> r.highWatermark.messageOffset)).toMap
    highWatermarkCheckpoints(dir).write(hwms)
  }
}
```

如何根据Partition得到offset: Partition->Replica->LogOffsetMetadata->messageOffset.

- 找出所有的Partitions,获取其Replica,确保有Replica的Partition
- 根据log.dirs重新分组(确保有Log), 每个log.dirs对应了replicas列表
- 对每个log.dirs, 循环replicas, 转换成TopicAndPartition到HW的映射
- 最终往每个log.dirs写入了属于这个dir的TopicAndPartition->offset信息

> 问:HW是由Leader更新的,Broker节点存储的并不都是Leader Partitions,在做checkpoint时是只对Leader Partition做吗?
> 答:不是的,ReplicaManager是对所有Partitions做checkpoint的.虽然只有Leader更新HW,但是Leader也会将HW广播给follower的.
> 当follower向leader fetch request的时候,leader会把hw也传给follower,这样follower也有了hw信息. 这样同一个Partition的
> 所有Replica都有了hw信息.目的是即使Leader挂掉了,这个hw仍然会保留在其他Replica上,其他replica成为leader后,hw也不会丢失.

> 问:Replica的highWatermark_=方法不是只有Leader才能调用吗(isLocal),那么leader广播给follower的hw是如何被更新的?

### ReplicaManager.allPartitions

allPartitions被放入也是在`becomeLeaderOrFollower`中.这说明一个Partition在Leader和follower转换时是要做很多工作的.

```scala
def getOrCreatePartition(topic: String, partitionId: Int): Partition = {
  var partition = allPartitions.get((topic, partitionId))
  if (partition == null) {
    allPartitions.putIfNotExists((topic, partitionId), new Partition(topic, partitionId, time, this))
    partition = allPartitions.get((topic, partitionId))
  }
  partition
}
```

allPartitions记录的是当前节点的所有Partitions.这些Partitions并不都是Leader.

```scala
private def getLeaderPartitions() : List[Partition] = {
  allPartitions.values.filter(_.leaderReplicaIfLocal().isDefined).toList
}
private def maybeShrinkIsr() {
  allPartitions.values.foreach(_.maybeShrinkIsr(config.replicaLagTimeMaxMs))  //评估ISR,查看是否有replicas可以从中移除
}
```

> 留个坑: becomeLeaderOrFollower的调用以及ISR收缩时对HW的影响.

## ReplicaManager.appendMessages

在Log.append之后，需要通知follower获取这些新的消息。因为现在Leader的LEO已经更新了。follower需要及时获取落后的消息，如果Partition的ISR只有1个,说明没有其他Replication，则Leader的LEO更新后，其HW也要也要一起更新(当然这是特殊情况)

[![k_hw_leo](https://images.weserv.nl/?url=http://img.blog.csdn.net/20160114094002808)](https://images.weserv.nl/?url=http://img.blog.csdn.net/20160114094002808)

为了更方便地查看在append之后的操作，我把相关的代码都列在一起了，之前分析过的就省略了，

```scala
//*****ReplicationManager.scala*****
private def appendToLocalLog(internalTopicsAllowed: Boolean,messagesPerPartition: Map[TopicAndPartition, MessageSet], requiredAcks: Short): Map[TopicAndPartition, LogAppendResult] = {
  messagesPerPartition.map { case (topicAndPartition, messages) =>
    val info = partition.appendMessagesToLeader(messages.asInstanceOf[ByteBufferMessageSet], requiredAcks)
    (topicAndPartition, LogAppendResult(info))
  }
}
//*****Partition.scala*****
def appendMessagesToLeader(messages: ByteBufferMessageSet, requiredAcks: Int = 0) = {
  val (info, leaderHWIncremented) = inReadLock(leaderIsrUpdateLock) {
        // ① 写到Partition的Leader的那个Log
        val log = leaderReplica.log.get       
        val info = log.append(messages, assignOffsets = true)
        // ② probably unblock some follower fetch requests since log end offset has been updated
        replicaManager.tryCompleteDelayedFetch(new TopicPartitionOperationKey(this.topic, this.partitionId))
        // ③ we may need to increment high watermark since ISR could be down to 1
        (info, maybeIncrementLeaderHW(leaderReplica))
  }     
  // some delayed operations may be unblocked after HW changed 
  // ④ 如果HW改变了,则一些延迟的请求(针对消费者)需要被解锁.当然如果HW没有变化,就不需要通知了
  if (leaderHWIncremented) tryCompleteDelayedRequests()
}
//*****ReplicationManager.scala*****
def appendMessages(timeout: Long, ... ){
    val localProduceResults = appendToLocalLog(internalTopicsAllowed, messagesPerPartition, requiredAcks)
    val produceStatus = localProduceResults.map { case (topicAndPartition, result) =>
      topicAndPartition -> ProducePartitionStatus(result.info.lastOffset + 1, ProducerResponseStatus(result.errorCode, result.info.firstOffset)) // required offset, response status
    }
    if (delayedRequestRequired(requiredAcks, messagesPerPartition, localProduceResults)) {
      // ⑤ create delayed produce operation 创建延迟的Produce操作
      val produceMetadata = ProduceMetadata(requiredAcks, produceStatus)
      val delayedProduce = new DelayedProduce(timeout, produceMetadata, this, responseCallback)
      // create a list of (topic, partition) pairs to use as keys for this delayed produce operation
      val producerRequestKeys = messagesPerPartition.keys.map(new TopicPartitionOperationKey(_)).toSeq
      // ⑥ try to complete the request immediately, otherwise put it into the purgatory 尝试立即完成请求,否则放入十八层地狱中炼狱一番(实际可以看做是缓存)
      // this is because while the delayed produce operation is being created, new requests may arrive and hence make this operation completable.
      delayedProducePurgatory.tryCompleteElseWatch(delayedProduce, producerRequestKeys)
    } else {
      // ⑦ we can respond immediately 如果不需要等待ISR同步数据,在成功写到Leader之后,就可以返回响应给Producer了
      val produceResponseStatus = produceStatus.mapValues(status => status.responseStatus)
      responseCallback(produceResponseStatus)
    }
}
```

①-④ 添加到Leader的本地日志后,要解锁follower的fetch,这样ISR中的follower才能尽快地同步新增的消息.
⑤-⑥ 如果需要ISR中的follower replicas同步数据,则需要创建延迟的ProducerRequest(生产请求),并交给专门的Purgatory处理
⑦ 不需要等待ISR的数据同步,在成功写到Leader之后,就可以返回响应给Producer了

注意这里创建的DelayedProduce和Producer相关的几个对象的关系.

- produceStatus: 生产者的状态, 主要是TopicAndPartition和ProducePartitionStatus的映射. 会有多个Partition对应自己的状态
- produceMetadata: 包含了上面的生产者状态, 以及requiredAcks来自Producer的acks设置
- responseCallback: 回到函数, 来自于appendMessages外面传入的(来源于KafkaApis定义的回调函数<–客户端回调)
- produceResponseStatus: 最终返回给客户端的响应信息, 在请求完成时, 会把响应状态传给回调函数,然后调用回调函数,完成客户端的回调

## ReplicaManager->DelayOperation

> 这里先简单看下生产请求需要被延迟的场景,后面一篇专门讲为什么需要延迟(以及使用缓存来保存和移除操作)

**delayedRequestRequired**

在appendToLocalLog后,满足下面的所有条件时,将会产生一个延迟的Produce请求,并且等待replication完成:

- required acks = -1 : Producer需要等待所有ISR接收数据(这个最重要了,不过由客户端指定的)
- there is data to append : 有数据(废话,没数据怎么叫生产消息)
- at least one partition append was successful 至少一个Partition追加成功(一次请求有多个Partition)

```scala
private def delayedRequestRequired(requiredAcks: Short, messagesPerPartition: Map[TopicAndPartition, MessageSet], localProduceResults: Map[TopicAndPartition, LogAppendResult]): Boolean = {
  requiredAcks == -1 && messagesPerPartition.size > 0 && localProduceResults.values.count(_.error.isDefined) < messagesPerPartition.size
}
```

**DelayedProduce**

```scala
localProduceResults -> (TopicAndPartition,LogAppendResult) -> ProducePartitionStatus -> ProduceMetadata -> DelayedProduce
本地(Leader)的生产结果 ->           日志追加结果               -> Produce的Partition状态  -> Produce的元数据  -> 延迟的Produce操作
```

延迟的Produce操作会被ReplicaManager创建(`new DelayedProduce`),并被ProduceOperationPurgatory监视.

```
// A delayed produce operation that can be created by the replica manager and watched in the produce operation purgatory
class DelayedProduce(delayMs: Long, produceMetadata: ProduceMetadata, replicaManager: ReplicaManager, 
                     responseCallback: Map[TopicAndPartition, ProducerResponseStatus] => Unit) extends DelayedOperation(delayMs) 

abstract class DelayedOperation(delayMs: Long) extends TimerTask with Logging {
  override val expirationMs = delayMs + System.currentTimeMillis()
}
```

DelayedProduce的`delayMs`以及`acks`源头来自于KafkaApis.handleProducerRequest的ProduceRequest:

```
replicaManager.appendMessages(produceRequest.ackTimeoutMs.toLong, produceRequest.requiredAcks, internalTopicsAllowed, authorizedRequestInfo, sendResponseCallback)
```

## DelayedOperationPurgatory

ReplicaManager的append操作出现了三个tryCompleteXXX(Fetch,Request,ElseWath)都是交给DelayedOperationPurgatory炼狱工厂.

```
class ReplicaManager:  
  val delayedProducePurgatory = new DelayedOperationPurgatory[DelayedProduce](purgatoryName = "Produce", config.brokerId, config.producerPurgatoryPurgeIntervalRequests)
  val delayedFetchPurgatory = new DelayedOperationPurgatory[DelayedFetch](purgatoryName = "Fetch", config.brokerId, config.fetchPurgatoryPurgeIntervalRequests)
  def tryCompleteDelayedFetch(key: DelayedOperationKey)   { val completed = delayedFetchPurgatory.checkAndComplete(key) }
  def tryCompleteDelayedProduce(key: DelayedOperationKey) { val completed = delayedProducePurgatory.checkAndComplete(key) }

// A helper purgatory class for bookkeeping delayed operations with a timeout, and expiring timed out operations.
class DelayedOperationPurgatory[T <: DelayedOperation](purgatoryName: String, brokerId: Int = 0, purgeInterval: Int = 1000)
```

A.尝试完成`延迟的fetch请求`的触发条件:

- A.1 `Partition的HW发生变化` (正常的fetch–即consumer的fetch,因为HW是针对消费者而言,消费者最多只能到HW)
- A.2 新的MessageSet追加到本地日志 (follower的fetch, 新的MessageSet有新的LEO, 而follower是跟踪LEO的)

B.尝试完成`延迟的Produce请求`的触发条件:

- B.1 `Partition的HW发生变化` (对于acks=-1的情况)
- B.2 收到了一个follower副本的fetch操作 (对于acks>1的情况? acks不是只有-1,0,1三个值吗?)

## Partition->IncrementLeaderHW

> HW表示的是所有ISR中的节点都已经复制完的消息.也是消费者所能获取到的消息的最大offset,所以叫做high watermark.
> 注意Leader Partition保存了ISR信息.所以可以看到maybeIncrementLeaderHW()是在appendToLocalLog()内一起执行的

C.增加(Leader)Partition的Hight Watermark(HW)的触发条件:

- C.1 `(Leader)Partition的ISR发生变化` (假设某个很慢的节点落后很多从ISR中移除,而其他节点大部分都catch-up,就可以更新HW)
- C.2 `任何Replication的LEO`发生变化 (ISR中的followers有任何一个节点LEO改变,看看所有ISR是否都复制了,然后更新HW)

```
private def maybeIncrementLeaderHW(leaderReplica: Replica): Boolean = {
  // 所有inSync副本中最小的LEO(因为每个follower的LEO都可能不一样), 表示的是最新的hw
  val allLogEndOffsets = inSyncReplicas.map(_.logEndOffset)
  val newHighWatermark = allLogEndOffsets.min(new LogOffsetMetadata.OffsetOrdering)

  // Leader本身的hw, 是旧的
  val oldHighWatermark = leaderReplica.highWatermark  // 是一个LogOffsetMetadata
  if(oldHighWatermark.precedes(newHighWatermark)) {   // 比较Leader的messageOffset是否小于ISR的
    leaderReplica.highWatermark = newHighWatermark    // Leader小于ISR, 更新Leader为ISR中最小的
    true      
  }else false     // Returns true if the HW was incremented, and false otherwise.
}
```

在Log中append的updateLogEndOffset(LogAppendInfo.lastOffset+1)更新的是LogOffsetMetadata的messageOffset
而这里的oldHighWatermark和newHighWatermark也都是LogOffsetMetadata(最重要的就是messageOffset字段了)!
所以实际比较的是两个LogOffsetMetadata的messageOffset, 只有`Leader的HW`小于`ISR最小的LEO`,才更新Leader的HW.

- `leaderReplica`是Partition的Leader副本,一个Partition只有一个Leader,读写都发生在Leader上
- `inSyncReplicas`是Partition的follower中追赶上Leader的副本(并不是所有Follower都是InSync)

> 这两个属性都是Partiton级别的,即Partiton要知道它的leader是哪个,以及这个leader管理的isr列表是什么.

## 其他

**delay operation complete**

触发条件(延迟请求以及增加HW)中关于ISR的部分都是环环相扣的:

- leader有新消息写到本地日志(生产者写新数据) –> A.2 –> DelayedFetch
- leader replication的LEO发生变化(追加了新消息) –> C.2 –> HW
- follower向Leader发起fetch请求(ISR的follower会和Leader保持同步) –> B.2 –> DelayedProduce
- follower所在replication的LEO发生变化(拉取了新消息到本地) –> C.2 –> HW
- 所有replication的LEO发生变化,Leader的HW也会变化(成功提交了消息) –> C.2 –> HW
- consumer读取至多Leader的HW,HW变化了,解锁consumer –> A.1 –> DelayedFetch
- producer等待ISR都同步成功,导致HW变化,就可以返回响应 –> B.1 –> DelayedProduce

[![k_trigger](https://images.weserv.nl/?url=http://img.blog.csdn.net/20160117162956999)](https://images.weserv.nl/?url=http://img.blog.csdn.net/20160117162956999)

**Partition and Replica Creation**

ReplicaManager负责管理Partiton, 所以是在ReplicaManager创建Partition的: getOrCreatePartition
Partition负责管理Replica, 所以是在Partition中创建Replica的: getOrCreateReplica

ReplicaManager要管理Broker上的所有Partition, allPartitions: Pool[(String, Int), Partition]
Partition也要管理分配的所有Replica,还有Leader Replica和ISR.

[![k_replica_comps](https://images.weserv.nl/?url=http://img.blog.csdn.net/20160117163023129)](https://images.weserv.nl/?url=http://img.blog.csdn.net/20160117163023129)

------

## 结语

- Partition副本由Leader和follower组成,只有ISR列表中的副本是仅仅跟着Leader的
- Leader管理了ISR列表,只有ISR列表中的所有副本都复制了消息,才能认为这条消息是提交的
- Leader和follower副本都叫做Replica,同一个Partition的不同副本分布在不同Broker上
- Replica很重要的两个信息是HighWatermark(HW)和LogEndOffset(LEO)
- 只有Leader Partition负责客户端的读写,follower从Leader同步数据
- 所有Replica都会对HW做checkpoint,Leader会在follower的拉取请求时广播HW给follower

## Ref

- <http://www.jasongj.com/2015/04/24/KafkaColumn2/>
- <https://cwiki.apache.org/confluence/display/KAFKA/Kafka+Replication>

**本文标题:**[Kafka源码分析 ISR和Replica](http://zqhxuyuan.github.io/2016/01/14/2016-01-14-Kafka-ISR/)

**文章作者:**[任何忧伤,都抵不过世界的美丽](http://zqhxuyuan.github.io/)

**发布时间:**2016年01月14日 - 00时00分

**最后更新:**2019年02月14日 - 21时42分

**原始链接:**[http://github.com/zqhxuyuan/2016/01/14/2016-01-14-Kafka-ISR/](http://zqhxuyuan.github.io/2016/01/14/2016-01-14-Kafka-ISR/) 

**许可协议:** ["署名-非商用-相同方式共享 3.0"](http://creativecommons.org/licenses/by-nc-sa/3.0/cn/) 转载请保留原文链接及作者。

 [Kafka源码分析 DelayOperation](http://zqhxuyuan.github.io/2016/01/15/2016-01-15-Kafka-Delay/)

[译：Kafka图文详解 ](http://zqhxuyuan.github.io/2016/01/13/2016-01-13-Kafka-Picture/)



文章目录

1. \1. Partition & Replica
   1. [1.1. ReplicaManager->OffsetCheckpoint](http://zqhxuyuan.github.io/2016/01/14/2016-01-14-Kafka-ISR/#ReplicaManager-%3EOffsetCheckpoint)
   2. [1.2. ReplicaManager.allPartitions](http://zqhxuyuan.github.io/2016/01/14/2016-01-14-Kafka-ISR/#ReplicaManager-allPartitions)
2. [2. ReplicaManager.appendMessages](http://zqhxuyuan.github.io/2016/01/14/2016-01-14-Kafka-ISR/#ReplicaManager-appendMessages)
3. [3. ReplicaManager->DelayOperation](http://zqhxuyuan.github.io/2016/01/14/2016-01-14-Kafka-ISR/#ReplicaManager-%3EDelayOperation)
4. [4. DelayedOperationPurgatory](http://zqhxuyuan.github.io/2016/01/14/2016-01-14-Kafka-ISR/#DelayedOperationPurgatory)
5. [5. Partition->IncrementLeaderHW](http://zqhxuyuan.github.io/2016/01/14/2016-01-14-Kafka-ISR/#Partition-%3EIncrementLeaderHW)
6. [6. 其他](http://zqhxuyuan.github.io/2016/01/14/2016-01-14-Kafka-ISR/#%E5%85%B6%E4%BB%96)
7. [7. 结语](http://zqhxuyuan.github.io/2016/01/14/2016-01-14-Kafka-ISR/#%E7%BB%93%E8%AF%AD)
8. [8. Ref](http://zqhxuyuan.github.io/2016/01/14/2016-01-14-Kafka-ISR/#Ref)

[Hexo](http://hexo.io/) Theme [Yelee](https://github.com/MOxFIVE/hexo-theme-yelee) by MOxFIVE