## A Kafka client that consumes records from a Kafka cluster
It will transparently handle the failure of servers in the Kafka cluster, and transparently adapt as partitions of data it fetches migrate within the cluster. This client also interacts with the server to allow groups of consumers to load balance consumption using consumer groups (as described below).

The consumer maintains TCP connections to the necessary brokers to fetch data. Failure to close the consumer after use will leak these connections. The consumer is not thread-safe. See **Multi-threaded Processing** for more details.

### Offsets and Consumer Position

Kafka maintains a numerical offset for each record in a partition. This offset acts as a kind of unique identifier of a record within that partition, and also denotes the position of the consumer in the partition. That is, a consumer which has position 5 has consumed records with offsets 0 through 4 and will next receive the record with offset 5. There are actually two notions of position relevant to the user of the consumer.

The `position` of the consumer gives the offset of the next record that will be given out. It will be one larger than the highest offset the consumer has seen in that partition. It automatically advances every time the consumer receives data calls `poll(long)` and receives messages.

The `committed position` is the last offset that has been saved securely. Should the process fail and restart, this is the offset that it will recover to. The consumer can either automatically commit offsets periodically; or it can choose to control this committed position manually by calling `commitSync`, which will block until the offsets have been successfully committed or fatal error has happened during the commit process, or `commitAsync` which is non-blocking and will trigger `OffsetCommitCallback` upon either successfully committed or fatally failed.

This distinction gives the consumer control over when a record is considered consumed. It is discussed in further detail below.

### Consumer Groups and Topic Subscriptions

Kafka uses the concept of *consumer groups* to allow a pool of processes to divide the work of consuming and processing records. These processes can either be running on the same machine or, as is more likely, they can be distributed over many machines to provide scalability and fault tolerance for processing.

Each Kafka consumer is able to configure a consumer group that it belongs to, and can dynamically set the list of topics it wants to subscribe to through one of the `subscribe` APIs. Kafka will deliver each message in the subscribed topics to one process in each consumer group. This is achieved by balancing the partitions between all members in the consumer group so that each partition is assigned to exactly one consumer in the group. So if there is a topic with four partitions, and a consumer group with two processes, each process would consume from two partitions.

Membership in a consumer group is maintained dynamically: if a process fails, the partitions assigned to it will be reassigned to other consumers in the same group. Similarly, if a new consumer joins the group, partitions will be moved from existing consumers to the new one. This is known as rebalancing the group and is discussed in more detail below. Note that the same process is also used when new partitions are added to one of the subscribed topics: the group automatically detects the new partitions and rebalances the group so that every new partition is assigned to one of the members.

Conceptually you can think of a consumer group as being a single logical subscriber that happens to be made up of multiple processes. As a multi-subscriber system, Kafka naturally supports having any number of consumer groups for a given topic without duplicating data (additional consumers are actually quite cheap).

This is a slight generalization of the functionality that is common in messaging systems. To get semantics similar to a queue in a traditional messaging system all processes would be part of a single consumer group and hence record delivery would be balanced over the group like with a queue. Unlike a traditional messaging system, though, you can have multiple such groups. To get semantics similar to pub-sub in a traditional messaging system each process would have its own consumer group, so each process would subscribe to all the records published to the topic.

In addition, when group reassignment happens automatically, consumers can be notified through `ConsumerRebalanceListener`, which allows them to finish necessary application-level logic such as state cleanup, manual offset commits (note that offsets are always committed for a given consumer group), etc. See **Storing Offsets Outside Kafka** for more details

It is also possible for the consumer to **manually assign** specific partitions (similar to the older "simple" consumer) using `assign(Collection)`. In this case, dynamic partition assignment and consumer group coordination will be disabled.

### Detecting Consumer Failures

After subscribing to a set of topics, the consumer will automatically join the group when `poll(long)` is invoked. The poll API is designed to ensure consumer liveness. As long as you continue to call poll, the consumer will stay in the group and continue to receive messages from the partitions it was assigned. Underneath the covers, the poll API sends periodic heartbeats to the server; when you stop calling poll (perhaps because an exception was thrown), then no heartbeats will be sent. If a period of the configured session timeout elapses before the server has received a heartbeat, then the consumer will be kicked out of the group and its partitions will be reassigned. This is designed to prevent situations where the consumer has failed, yet continues to hold onto the partitions it was assigned (thus preventing active consumers in the group from taking them). To stay in the group, you have to prove you are still alive by calling poll.
The implication of this design is that message processing time in the poll loop must be bounded so that heartbeats can be sent before expiration of the session timeout. What typically happens when processing time exceeds the session timeout is that the consumer won't be able to commit offsets for any of the processed records. For example, this is indicated by a `CommitFailedException` thrown from `commitSync()`. This guarantees that only active members of the group are allowed to commit offsets. If the consumer has been kicked out of the group, then its partitions will have been assigned to another member, which will be committing its own offsets as it handles new records. This gives offset commits an isolation guarantee.

The consumer provides two configuration settings to control this behavior:

1. `session.timeout.ms`: By increasing the session timeout, you can give the consumer more time to handle a batch of records returned from poll(long). The only drawback is that it will take longer for the server to detect hard consumer failures, which can cause a delay before a rebalance can be completed. However, clean shutdown with close() is not impacted since the consumer will send an explicit message to the server to leave the group and cause an immediate rebalance.
2. `max.poll.records`: Processing time in the poll loop is typically proportional to the number of records processed, so it's natural to want to set a limit on the number of records handled at once. This setting provides that. By default, there is essentially no limit.

For use cases where message processing time varies unpredictably, neither of these options may be viable. The recommended way to handle these cases is to move message processing to another thread, which allows the consumer to continue sending heartbeats while the processor is still working. Some care must be taken to ensure that committed offsets do not get ahead of the actual position. Typically, you must disable automatic commits and manually commit processed offsets for records only after the thread has finished handling them (depending on the delivery semantics you need). Note also that you will generally need to `pause(Collection)` the partition so that no new records are received from poll until after thread has finished handling those previously returned.

### Usage Examples

The consumer APIs offer flexibility to cover a variety of consumption use cases. Here are some examples to demonstrate how to use them.
#### Automatic Offset Committing

This example demonstrates a simple usage of Kafka's consumer api that relying on automatic offset committing.

```java
Properties props = new Properties();
props.put("bootstrap.servers", "localhost:9092");
props.put("group.id", "test");
props.put("enable.auto.commit", "true");
props.put("auto.commit.interval.ms", "1000");
props.put("session.timeout.ms", "30000");
props.put("key.deserializer", 
          "org.apache.kafka.common.serialization.StringDeserializer");
props.put("value.deserializer", 
          "org.apache.kafka.common.serialization.StringDeserializer");
KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
consumer.subscribe(Arrays.asList("foo", "bar"));
while (true) {
  ConsumerRecords<String, String> records = consumer.poll(100);
  for (ConsumerRecord<String, String> record : records)
    System.out.printf("offset = %d, key = %s, value = %s", 
                      record.offset(), record.key(), record.value());
}
```
Setting `enable.auto.commit` means that offsets are committed automatically with a frequency controlled by the config `auto.commit.interval.ms`.

The connection to the cluster is bootstrapped by specifying a list of one or more brokers to contact using the configuration bootstrap.servers. This list is just used to discover the rest of the brokers in the cluster and need not be an exhaustive list of servers in the cluster (though you may want to specify more than one in case there are servers down when the client is connecting).

In this example the client is subscribing to the topics foo and bar as part of a group of consumers called test as described above.

The broker will automatically detect failed processes in the test group by using a heartbeat mechanism. The consumer will automatically ping the cluster periodically, which lets the cluster know that it is alive. Note that the consumer is single-threaded, so periodic heartbeats can only be sent when [`poll(long)`](http://kafka.apache.org/0100/javadoc/org/apache/kafka/clients/consumer/KafkaConsumer.html#poll(long)) is called. As long as the consumer is able to do this it is considered alive and retains the right to consume from the partitions assigned to it. If it stops heartbeating by failing to call [`poll(long)`](http://kafka.apache.org/0100/javadoc/org/apache/kafka/clients/consumer/KafkaConsumer.html#poll(long)) for a period of time longer than `session.timeout.ms` then it will be considered dead and its partitions will be assigned to another process.

The deserializer settings specify how to turn bytes into objects. For example, by specifying string deserializers, we are saying that our record's key and value will just be simple strings.

#### Manual Offset Control

Instead of relying on the consumer to periodically commit consumed offsets, users can also control when messages should be considered as consumed and hence commit their offsets. This is useful when the consumption of the messages are coupled with some processing logic and hence a message should not be considered as consumed until it is completed processing. In this example we will consume a batch of records and batch them up in memory, when we have sufficient records batched we will insert them into a database. If we allowed offsets to auto commit as in the previous example messages would be considered consumed after they were given out by the consumer, and it would be possible that our process could fail after we have read messages into our in-memory buffer but before they had been inserted into the database. To avoid this we will manually commit the offsets only once the corresponding messages have been inserted into the database. This gives us exact control of when a message is considered consumed. This raises the opposite possibility: the process could fail in the interval after the insert into the database but before the commit (even though this would likely just be a few milliseconds, it is a possibility). In this case the process that took over consumption would consume from last committed offset and would repeat the insert of the last batch of data. Used in this way Kafka provides what is often called "**at-least once delivery**" guarantees, as each message will likely be delivered one time but in failure cases could be duplicated.

```java
Properties props = new Properties();
props.put("bootstrap.servers", "localhost:9092");
props.put("group.id", "test");
props.put("enable.auto.commit", "false");
props.put("auto.commit.interval.ms", "1000");
props.put("session.timeout.ms", "30000");
props.put("key.deserializer", 
          "org.apache.kafka.common.serialization.StringDeserializer");
props.put("value.deserializer", 
          "org.apache.kafka.common.serialization.StringDeserializer");
KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
consumer.subscribe(Arrays.asList("foo", "bar"));
final int minBatchSize = 200;
List<ConsumerRecord<String, String>> buffer = new ArrayList<>();
while (true) {
  ConsumerRecords<String, String> records = consumer.poll(100);
  for (ConsumerRecord<String, String> record : records) {
    buffer.add(record);
 }
 if (buffer.size() >= minBatchSize) {
   insertIntoDb(buffer);
   consumer.commitSync();
   buffer.clear();
 }
}
```
The above example uses `commitSync` to mark all received messages as committed. In some cases you may wish to have even finer control over which messages have been committed by specifying an offset explicitly. In the example below we commit offset after we finish handling the messages in each partition.

```java
try {
  while(running) {
    ConsumerRecords<String, String> records = consumer.poll(Long.MAX_VALUE);
    for (TopicPartition partition : records.partitions()) {
      List<ConsumerRecord<String, String>> partitionRecords =records.records(partition);
      for (ConsumerRecord<String, String> record : partitionRecords) {
        System.out.println(record.offset() + ": " + record.value());
      }
      long lastOffset = partitionRecords.get(partitionRecords.size() - 1).offset();
      consumer.commitSync(Collections.singletonMap(partition, 
                                       new OffsetAndMetadata(lastOffset + 1)));
    }
  }
} finally {
  consumer.close();
}
```
**Note: The committed offset should always be the offset of the next message that your application will read**. Thus, when calling [`commitSync(offsets)`](http://kafka.apache.org/0100/javadoc/org/apache/kafka/clients/consumer/KafkaConsumer.html#commitSync(java.util.Map)) you should add one to the offset of the last message processed.

#### Manual Partition Assignment

In the previous examples, we subscribed to the topics we were interested in and let Kafka dynamically assign a fair share of the partitions for those topics based on the active consumers in the group. However, in some cases you may need finer control over the specific partitions that are assigned. For example:
- If the process is maintaining some kind of local state associated with that partition (like a local on-disk key-value store), then it should only get records for the partition it is maintaining on disk.
- If the process itself is highly available and will be restarted if it fails (perhaps using a cluster management framework like YARN, Mesos, or AWS facilities, or as part of a stream processing framework). In this case there is no need for Kafka to detect the failure and reassign the partition since the consuming process will be restarted on another machine.

To use this mode, instead of subscribing to the topic using subscribe, you just call assign(Collection) with the full list of partitions that you want to consume.

```java
String topic = "foo";
TopicPartition partition0 = new TopicPartition(topic, 0);
TopicPartition partition1 = new TopicPartition(topic, 1);
consumer.assign(Arrays.asList(partition0, partition1));
```
Once assigned, you can call poll in a loop, just as in the preceding examples to consume records. The group that the consumer specifies is still used for committing offsets, but now the set of partitions will only change with another call to assign. Manual partition assignment does not use group coordination, so consumer failures will not cause assigned partitions to be rebalanced. Each consumer acts independently even if it shares a groupId with another consumer. To avoid offset commit conflicts, you should usually ensure that the groupId is unique for each consumer instance.

Note that it isn't possible to mix manual partition assignment (i.e. using assign) with dynamic partition assignment through topic subscription (i.e. using subscribe).

#### Storing Offsets Outside Kafka

The consumer application need not use Kafka's built-in offset storage, it can store offsets in a store of its own choosing. The primary use case for this is allowing the application to store both the offset and the results of the consumption in the same system in a way that both the results and offsets are stored atomically. This is not always possible, but when it is it will make the consumption fully atomic and give "exactly once" semantics that are stronger than the default "at-least once" semantics you get with Kafka's offset commit functionality.

Here are a couple of examples of this type of usage:

- If the results of the consumption are being stored in a relational database, storing the offset in the database as well can allow committing both the results and offset in a single transaction. Thus either the transaction will succeed and the offset will be updated based on what was consumed or the result will not be stored and the offset won't be updated.
- If the results are being stored in a local store it may be possible to store the offset there as well. For example a search index could be built by subscribing to a particular partition and storing both the offset and the indexed data together. If this is done in a way that is atomic, it is often possible to have it be the case that even if a crash occurs that causes unsync'd data to be lost, whatever is left has the corresponding offset stored as well. This means that in this case the indexing process that comes back having lost recent updates just resumes indexing from what it has ensuring that no updates are lost.

Each record comes with its own offset, so to manage your own offset you just need to do the following:

- Configure `enable.auto.commit=false`
- Use the offset provided with each [`ConsumerRecord`](http://kafka.apache.org/0100/javadoc/org/apache/kafka/clients/consumer/ConsumerRecord.html) to save your position.
- On restart restore the position of the consumer using `seek(TopicPartition, long)`

This type of usage is simplest when the partition assignment is also done manually (this would be likely in the search index use case described above). If the partition assignment is done automatically special care is needed to handle the case where partition assignments change. This can be done by providing a [`ConsumerRebalanceListener`](http://kafka.apache.org/0100/javadoc/org/apache/kafka/clients/consumer/ConsumerRebalanceListener.html) instance in the call to `subscribe(Collection, ConsumerRebalanceListener)` and `subscribe(Pattern, ConsumerRebalanceListener)`. For example, when partitions are taken from a consumer the consumer will want to commit its offset for those partitions by implementing `ConsumerRebalanceListener.onPartitionsRevoked(Collection)`. When partitions are assigned to a consumer, the consumer will want to look up the offset for those new partitions and correctly initialize the consumer to that position by implementing `ConsumerRebalanceListener.onPartitionsAssigned(Collection)`.

Another common use for `ConsumerRebalanceListener` is to flush any caches the application maintains for partitions that are moved elsewhere.

#### Controlling The Consumer's Position

In most use cases the consumer will simply consume records from beginning to end, periodically committing its position (either automatically or manually). However Kafka allows the consumer to manually control its position, moving forward or backwards in a partition at will. This means a consumer can re-consume older records, or skip to the most recent records without actually consuming the intermediate records.
There are several instances where manually controlling the consumer's position can be useful.

One case is for time-sensitive record processing it may make sense for a consumer that falls far enough behind to not attempt to catch up processing all records, but rather just skip to the most recent records.

Another use case is for a system that maintains local state as described in the previous section. In such a system the consumer will want to initialize its position on start-up to whatever is contained in the local store. Likewise if the local state is destroyed (say because the disk is lost) the state may be recreated on a new machine by re-consuming all the data and recreating the state (assuming that Kafka is retaining sufficient history).

Kafka allows specifying the position using `seek(TopicPartition, long)` to specify the new position. Special methods for seeking to the earliest and latest offset the server maintains are also available (`seekToBeginning(Collection)` and `seekToEnd(Collection)` respectively).

#### Consumption Flow Control

If a consumer is assigned multiple partitions to fetch data from, it will try to consume from all of them at the same time, effectively giving these partitions the same priority for consumption. However in some cases consumers may want to first focus on fetching from some subset of the assigned partitions at full speed, and only start fetching other partitions when these partitions have few or no data to consume.

One of such cases is stream processing, where processor fetches from two topics and performs the join on these two streams. When one of the topics is long lagging behind the other, the processor would like to pause fetching from the ahead topic in order to get the lagging stream to catch up. Another example is bootstraping upon consumer starting up where there are a lot of history data to catch up, the applications usually want to get the latest data on some of the topics before consider fetching other topics.

Kafka supports dynamic controlling of consumption flows by using pause(Collection) and resume(Collection) to pause the consumption on the specified assigned partitions and resume the consumption on the specified paused partitions respectively in the future poll(long) calls.

### Multi-threaded Processing

The Kafka consumer is NOT thread-safe. All network I/O happens in the thread of the application making the call. It is the responsibility of the user to ensure that multi-threaded access is properly synchronized. Un-synchronized access will result in `ConcurrentModificationException`.

The only exception to this rule is `wakeup()`, which can safely be used from an external thread to interrupt an active operation. In this case, a `WakeupException` will be thrown from the thread blocking on the operation. This can be used to shutdown the consumer from another thread. The following snippet shows the typical pattern:
```java
public class KafkaConsumerRunner implements Runnable {
  private final AtomicBoolean closed = new AtomicBoolean(false);
  private final KafkaConsumer consumer;

  public void run() {
    try {
      consumer.subscribe(Arrays.asList("topic"));
      while (!closed.get()) {
        ConsumerRecords records = consumer.poll(10000);
        // Handle new records
      }
    } catch (WakeupException e) {
      // Ignore exception if closing
      if (!closed.get()) throw e;
    } finally {
         consumer.close();
    }
  }

  // Shutdown hook which can be called from a separate thread
  public void shutdown() {
    closed.set(true);
    consumer.wakeup();
  }
}
```
Then in a separate thread, the consumer can be shutdown by setting the closed flag and waking up the consumer.

```java
closed.set(true);
consumer.wakeup();
```
We have intentionally avoided implementing a particular threading model for processing. This leaves several options for implementing multi-threaded processing of records.

#### 1. One Consumer Per Thread

A simple option is to give each thread its own consumer instance. Here are the pros and cons of this approach:
- **PRO**: It is the easiest to implement
- **PRO**: It is often the fastest as no inter-thread co-ordination is needed
- **PRO**: It makes in-order processing on a per-partition basis very easy to implement (each thread just processes messages in the order it receives them).
- **CON**: More consumers means more TCP connections to the cluster (one per thread). In general Kafka handles connections very efficiently so this is generally a small cost.
- **CON**: Multiple consumers means more requests being sent to the server and slightly less batching of data which can cause some drop in I/O throughput.
- **CON**: The number of total threads across all processes will be limited by the total number of partitions.

#### 2. Decouple Consumption and Processing

Another alternative is to have one or more consumer threads that do all data consumption and hands off `ConsumerRecords` instances to a blocking queue consumed by a pool of processor threads that actually handle the record processing. This option likewise has pros and cons:

- **PRO**: This option allows independently scaling the number of consumers and processors. This makes it possible to have a single consumer that feeds many processor threads, avoiding any limitation on partitions.
- **CON**: Guaranteeing order across the processors requires particular care as the threads will execute independently an earlier chunk of data may actually be processed after a later chunk of data just due to the luck of thread execution timing. For processing that has no ordering requirements this is not a problem.
- **CON**: Manually committing the position becomes harder as it requires that all threads co-ordinate to ensure that processing is complete for that partition.

There are many possible variations on this approach. For example each processor thread can have its own queue, and the consumer threads can hash into these queues using the TopicPartition to ensure in-order consumption and simplify commit.

------

# [译：使用新的Kafka消费者客户端](https://www.confluent.io/blog/tutorial-getting-started-with-the-new-apache-kafka-0-9-consumer-client/)

当kafka最初创建的时候,它内置了scala版本的producer和consumer客户端.在使用的过程中我们渐渐发现了这些APIs的限制. 比如,我们有”high-level”的消费者API,可以支持消费组和故障处理,但是不支持更多更复杂的场景需求. 我们也有一个简单的消费者客户端(SimpleConsumer,即low-level),可以支持自定义的控制,但是需要应用程序自己管理故障和错误处理.所以我们决定重新设计这些客户端,它的目标是要能实现之前使用旧的客户端不容易实现甚至无法实现的场景,还要建立一些API的集合,来支持长时间的拉取消息(译注: 即消费者通过poll方式保持长时间的消息拉取).

第一阶段是发布在0.8.1中生产者API(KafkaProducer)的重写,最近发布的0.9完成第二阶段新的消费者API(KafkaConsumer).基于新的消费组协调协议(group coordination protocol),新的消费者API带来了以下的优势:

- 简洁的统一API: 新的消费者结合了旧的API中”simple”和”high-level”消费者客户端两种功能,能够同时提供消费者协调  (高级API)和lower-level的访问,来构建自定义的消费策略.
- 更少的依赖: 新的消费者完全使用java编写,它不再依赖scala运行时环境和zookeeper.在你的项目中可以作为一个轻量级的库
- 更好的安全性: 0.9版本实现了安全性扩展,目前只支持新的消费者API
- 新的消费者还添加了一些协议: 管理一组消费者处理进程的故障容忍.之前这部分功能通过java客户端 频繁地和zookeeper进行交互.部分复杂的逻辑导致很难使用其他语言构建出完整的客户端.  现在新的协议的出现使得这部分非常容易实现,现在已经实现了C的客户端.

尽管新的消费者使用了全新设计的API和新的协调协议,基本概念并没有多大差别.所以熟悉旧的消费者客户端的用户理解新的API并不会有很大的困难. 不过还是有一些微妙的细节需要关注, 特别是消费组管理和线程模型. 这篇文章会覆盖新的消费者的基本用法,并解释这些细节.

## 开始

首先复习下一些基本概念.在kafka中,每个topic会被分成一系列的logs,叫做partitions(逻辑上topic是由partitions组成). Producers写到这些logs的尾部,Consumers以自己的步调读取logs. kafka扩展topic的消费是通过将partitions分布在一个消费组,多个消费者共享了相同的组标识. 下图标识一个topic有三个partitions,一个消费组有两个消费者成员.每个partition都只会分配给组中唯一的一个成员.

[![consumer group](http://img.blog.csdn.net/20160221172547706)](http://img.blog.csdn.net/20160221172547706)

旧的消费者依赖于zookeeper管理消费组(译注:ZookeeperConsumerConnector->ZKRebalancerListener),新的消费者使用了消费组协调协议. 对于每个消费组,会选择一个brokers作为消费组的协调者(group coordinator).协调者负责管理消费者组的状态. 它的主要工作是负责协调partition的分配(assignment): 当有新成员加入,旧成员退出,或者topic的metadata发生变化(topic的partitions改变).重新分配partition叫做消费组的平衡(group rebalance)

当消费组第一次被初始化时,消费者通常会读取每个partition的最早或最近的offset.然后顺序地读取每个partition log的消息.在消费者读取过程中,它会提交已经成功处理的消息的offsets. 下图中消费者的位置在6位置,最近提交的offset则在位置1.

[![consumer position](http://img.blog.csdn.net/20160221172517706)](http://img.blog.csdn.net/20160221172517706)

当一个partition被分配给消费组中的其他消费者,(新的消费者)初始位置会设置为(原始消费者)最近提交的offset.如果示例中的消费者突然崩溃,接管partition的组中其他成员会从offset=1的位置开始消费(lastCommitOffset=1).这种情况下,新的消费者不得不从offset=1的位置开始,重新处理消息直到崩溃的消费者的offset=6的位置.

上图中还有两个log中重要的位置信息. `Log End Offset`是写入log中最后一条消息的offset+1. `High Watermark`是成功拷贝到log的所有副本节点的最近消息的offset(译注: 实际上是partition的所有ISR节点).从消费者的角度来看,最多只能读取到High watermark的位置,为了防止消费者读取还没有完全复制的数据造成数据丢失. (译注:如果消费者读取了未完全复制的数据,但是这部分数据之后丢失了,导致读取不该读的消息,所以应该读取完全复制的数据)

### 配置和初始化

在maven中添加kafka-clients依赖到你的项目中:

```xml
<dependency>
 <groupId>org.apache.kafka</groupId>
 <artifactId>kafka-clients</artifactId>
 <version>0.9.0.0</version>
</dependency>

```

消费者就像其他的kafka客户端一样通过Properties文件构造.下面是使用消费组的最少配置.

```java
Properties props = new Properties();
props.put("bootstrap.servers", "localhost:9092");
props.put("group.id", "consumer-tutorial");
props.put("key.deserializer", StringDeserializer.class.getName());
props.put("value.deserializer", StringDeserializer.class.getName());
KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);

```

就像旧的生产者和消费者,需要配置一个初始brokers列表,能够让消费者发现集群中的其他brokers.但并不需要指定所有的ervers, 客户端会根据初始brokers找出集群中存活的所有brokers(译注:类似gossip协议).在本例中,我们假设broker运行在本地(所以只有一个broker),同时还要告诉消费者怎么序列化消息的keys和values.最后,为了能够加入到一个消费组,需要为消费者指定一个group id. 随着文章的深入,我们会介绍更多的配置.

### topic订阅

为了能够消费消息,应用程序需要指定要订阅的topics. 下面的示例中,我们订阅了”foo”和”bar”两个topics:
```java
consumer.subscribe(Arrays.asList("foo", "bar"));
```

消费者订阅主题之后,这个消费者会和消费组中的其他成员共同协调,来得到分配给它的partition(每个消费者都会分配partition).
这一切都是在你开始消费消息的时候被自动处理. 后面我们会向你展示如何使用assign API手动地分配partitions.但是要注意: 同一个消费者实例是不能混合自动和手动的partition分配.

subscribe方法不是增量的:你必须包括你想要消费的完整的topics列表.你可以在任何时候修改订阅的topics集合.任何之前订阅的topics都会被新的列表替换.

### 基本的poll循环

消费者需要并行地抓取数据,这是因为多个topics的多个partitions是分布在多个brokers上的.
可以使用API的风格,类似于unix中的poll和select调用: 一旦topics注册在消费者实例上,
所有将来的协调,平衡和数据获取都是通过在一个事件循环中调用一个poll方法来驱动的.
这是一种简单而且高效的实现方式,可以只在一个线程中就能完成所有的IO请求.

消费者订阅一个topic之后,你需要启动一个事件循环来得到partition的分配,并且开始抓取数据.
看起来有点复杂,但你要做的仅仅只是在一个循环中调用poll,剩下的工作消费者自己会处理.

每次poll调用都会返回分配给属于这个消费者的partitions的消息集.
下面的示例中展示了一个基本的poll循环,当消息到达的时候, 打印出offset和抓取到的记录的消息内容.

```java
try {
  while (running) {
    ConsumerRecords<String, String> records = consumer.poll(1000);
    for (ConsumerRecord<String, String> record : records)
      System.out.println(record.offset() + ": " + record.value());
  }
} finally {
  consumer.close();
}

```

poll调用会返回基于当前位置的抓取记录(译注:每次抓取都会产生新的offset,下次抓取时,以新的offset为基础).
当第一次创建消费组时,position的值会根据重置策略为每个partition设置为最早或最近的offset.

当消费者开始提交offsets,在这之后的每次rebalance都会重置position为上一次提交的offset.
传递给poll方法的参数控制了消费者在当前位置因为等待消息的到来而阻塞的最长时间.
一旦有可用的记录(新的消息)消费者就会立即返回,如果没有可用的记录,则会一直等待直到超时才返回.

消费者被设计为在自己的线程中运行,在没有外部同步的情况下,使用多线程是不安全的,不建议尝试使用.
在本例中,我们使用了一个标志位,当应用程序关闭时,会从poll循环中跳出(译注:以类似钩子的方式).

当标志位被其他线程设置为false,事件循环会在poll返回时立即退出,不管返回什么记录,应用程序都会结束处理.
当应用程序结束的时候,你应该总是要关闭消费者(译注:类似资源在使用后最终要释放,比如连接对象和文件句柄).
这部分工作不仅仅是清理已经使用的socket连接,也确保了消费者及时通知协调者它已经从消费组中退出(要rebalance).

本例中使用了一个相对较小的timeout,来确保在关闭消费者时,不会有太多的延迟.
相应地,你可以设置较长的timeout,这时应该使用wakeup调用来从事件循环中退出.

```
try {
  while (true) {
    ConsumerRecords<String, String> records = consumer.poll(Long.MAX_VALUE);
    for (ConsumerRecord<String, String> record : records)
      System.out.println(record.offset() + “: ” + record.value());
  }
} catch (WakeupException e) {
  // ignore for shutdown
} finally {
  consumer.close();
}

```

上面的代码中,我们更改了timeout为Long.MAX_VALUE,意味着消费者会无限制地阻塞,直到有下一条记录返回的时候.
这时如果使用标志位也是无法退出循环的,所以只能由触发关闭的线程调用consumer.wakeup来中断进行中的poll,
这个调用会导致抛出WakeupException. wakeup在其他线程中调用是安全的(消费者线程中就这个方法是线程安全的).
注意:如果当前没有活动的poll,这个异常会在下次调用是才会抛出.本例中我们捕获了这个异常防止它传播给上层调用.

所以中断事件循环有两种方式:

- 较小的timeout, 通过使用标志位来控制
- 较长的timeout, 调用wakeup来退出循环

### 完整的示例

下面的示例中,我们构建了一个Runnable任务,初始化消费者,订阅topics,执行poll无限循环,直到外部关闭这个消费者.

```java
public class ConsumerLoop implements Runnable {
  private final KafkaConsumer<String, String> consumer;
  private final List<String> topics;
  private final int id;

  public ConsumerLoop(int id, String groupId,  List<String> topics) {
    this.id = id;
    this.topics = topics;
    Properties props = new Properties();
    props.put("bootstrap.servers", "localhost:9092");
    props.put(“group.id”, groupId);
    props.put(“key.deserializer”, StringDeserializer.class.getName());
    props.put(“value.deserializer”, StringDeserializer.class.getName());
    this.consumer = new KafkaConsumer<>(props);
  }
 
  @Override
  public void run() {
    try {
      consumer.subscribe(topics);

      while (true) {
        ConsumerRecords<String, String> records = consumer.poll(Long.MAX_VALUE);
        for (ConsumerRecord<String, String> record : records) {
          Map<String, Object> data = new HashMap<>();
          data.put("partition", record.partition());
          data.put("offset", record.offset());
          data.put("value", record.value());
          System.out.println(this.id + ": " + data);
        }
      }
    } catch (WakeupException e) {
      // ignore for shutdown 
    } finally {
      consumer.close();
    }
  }

  public void shutdown() {
    consumer.wakeup();
  }
}

```

为了测试这个例子,需要运行的kafka broker版本是0.9.0.0,还要有一些字符串数据构成的topic用来消费.
最简单的方式是使用kafka-verifiable-producer.sh脚本写一批数据到一个topic中. 为了让事情变得有趣一些,
我们还要确保topic有不止一个partition,这样不会有一个消费者成员做所有的工作.
比如在本地同时运行kafka broker和zookeeper, 在kafka的根目录下运行下面命令:

```bash
# bin/kafka-topics.sh --create --topic consumer-tutorial --replication-factor 1 --partitions 3 --zookeeper localhost:2181
# bin/kafka-verifiable-producer.sh --topic consumer-tutorial --max-messages 200000 --broker-list localhost:9092

```

然后创建一个Driver客户端程序,设置一个消费组有三个成员,所有的消费者订阅了刚刚创建的相同的topic

```java
public static void main(String[] args) { 
  int numConsumers = 3;
  String groupId = "consumer-tutorial-group"
  List<String> topics = Arrays.asList("consumer-tutorial");
  ExecutorService executor = Executors.newFixedThreadPool(numConsumers);

  final List<ConsumerLoop> consumers = new ArrayList<>();
  for (int i = 0; i < numConsumers; i++) {
    ConsumerLoop consumer = new ConsumerLoop(i, groupId, topics);
    consumers.add(consumer);
    executor.submit(consumer);
  }

  Runtime.getRuntime().addShutdownHook(new Thread() {
    @Override
    public void run() {
      for (ConsumerLoop consumer : consumers) {
        consumer.shutdown();
      } 
      executor.shutdown();
      try {
        executor.awaitTermination(5000, TimeUnit.MILLISECONDS);
      } catch (InterruptedException e) {
        e.printStackTrace;
      }
    }
  });
}

```

这个示例提交了三个可运行的消费者线程给executor. 每个线程都有单独的编号,这样你就可以看到哪个线程接收了什么数据.
当停止Driver应用程序时,shutdown钩子会被调用(译注:这是在主线程里,而消费者的线程则是其他的线程,这里模拟了多线程),
就会通过wakeup停止三个消费者线程,然后等待它们关闭.

运行上面的程序,你会看到所有线程都会读取到数据, 下面是输出的一部分(第一个数字是消费者编号):

```
2: {partition=0, offset=928, value=2786}
2: {partition=0, offset=929, value=2789}
1: {partition=2, offset=297, value=891}
2: {partition=0, offset=930, value=2792}
1: {partition=2, offset=298, value=894}
2: {partition=0, offset=931, value=2795}
0: {partition=1, offset=278, value=835}
2: {partition=0, offset=932, value=2798}
0: {partition=1, offset=279, value=838}
1: {partition=2, offset=299, value=897}
1: {partition=2, offset=300, value=900}
1: {partition=2, offset=301, value=903}
1: {partition=2, offset=302, value=906}
1: {partition=2, offset=303, value=909}
1: {partition=2, offset=304, value=912}
0: {partition=1, offset=280, value=841}
2: {partition=0, offset=933, value=2801}

```

输出结果显示了所有三个partitions的消费情况.每个partition分配给其中的一个线程(正好三个线程三个partitions).
在每个partition中,你会看到offset是不断增加的(译注:验证了同一个partition的offset是被顺序消费的).

## 消费者的活跃度

作为消费组的一部分,每个消费者会被分配它订阅的topics的一部分partitions.就像在这些partitions上加了一个组锁.
只要锁被持有,组中的其他成员就不会读取他们(译注:每个partition都对应唯一的消费者,partition锁只属于唯一的消费者).
当你的消费者是正常状态时,当然是最好不过了,因为这是防止重复消费的唯一方式.
但如果消费者失败了,你需要释放掉那个锁,这样可以将partitions分配给其他健康的成员.

kafka的消费组协调协议使用心跳机制解决了这个问题.在每次rebalance,所有当前generation的成员都会定时地发送心跳给group协调者.
只要协调者持续接收到心跳,它会假设这个成员是健康的. 每次接收到心跳,协调者就开始或者重置计时器.
如果时间超过了,没有收到消费者的心跳,协调者标记消费者为死亡状态,并触发组中其他的消费者重新加入,来重新分配partitions.
计时器的时间间隔就是session timeout,即客户端应用程序中配置的session.timeout.ms

session timeout确保应用程序崩溃或者partition将消费者和协调者进行了隔离的情况下锁会被释放.
注意应用程序的失败(进程还存在)有点不同,因为消费者仍然会发送心跳给协调者,并不代表应用程序是健康的.

消费者的轮询循环被设计为解决这个问题. 所有的网络IO操作在调用poll或者其他的阻塞API,都是在前台完成的.
消费者并不使用任何的后台线程. 这就意味着消费者的心跳只有在调用poll的时候才会发送给协调者.
如果应用程序停止polling(不管是处理代码抛出异常或者下游系统崩溃了),就不会再发送心跳了,
最终就会导致session超时(没有收到心跳,计时器开始增加), 然后消费组就会开始平衡操作.

唯一存在的问题是如果消费者处理消息花费的时间比session timeout还要长,就会触发一个假的rebalance.
可以通过设置更长的session timeout防止发生这样的情况.默认的超时时间是30秒,设置为几分钟也不是不行的.
更长的session timeout的缺点是,协调者会花费较长时间才能检测到真正崩溃的消费者.

## 消息发送语义

当消费组第一次创建时,初始offset会根据配置项auto.offset.reset策略设置. 一旦消费者开始处理消息,它会根据应用
程序的需要正常滴提交offset(可以是设置自动提交offset,或者手动提交.可以将offset存储在kafka或者外部存储中).

在之后的每一次rebalance,position都会被设置为在当前组中为这个partition最近提交的offset(即offset针对组级别).
如果消费者已经成功处理了一批消息,但是为这批消息提交offsets之前崩溃了,其他消费者会接着最近提交的offset处重复工作.
更加频繁地提交offsets,在发生崩溃的情况下重复消费消息的情况就越少发生(处理完消息后及时地提交offset是明智之举).

目前为止,我们假设开启了自动提交offset的策略.当设置enable.auto.commit=true(这也是默认值),
消费者会根据配置项auto.commit.interval.ms的值定时地触发自动提交offset的行为.
通过减少提交时间间隔,你可以限制在发生崩溃事件时,消费者需要重新处理的消息数量(越经常提交,越不容易重复).

如果要使用消费者的commit API,首先需要关闭自动提交的配置项:

```
props.put("enable.auto.commit", "false");

```

commit API很容易使用,但是怎么和poll循环结合起来才是关键. 下面的示例中包含了完整的循环逻辑,以及提交细节.
手动方式处理commits最简单的方式是使用同步方式的提交API,下面的示例读取消息,处理消息,然后提交offsets.

```
try {
  while (running) {
    ConsumerRecords<String, String> records = consumer.poll(1000);
    for (ConsumerRecord<String, String> record : records)
      System.out.println(record.offset() + ": " + record.value());

    try {
      consumer.commitSync();
    } catch (CommitFailedException e) {
      // application specific failure handling
    }
  }
} finally {
  consumer.close();
}

```

使用不带参数的commitSync方法会在最近一次调用poll的返回值中提交offsets.
这个方法是阻塞的(同步嘛),直到提交成功或者出现不可恢复的错误而失败.

大部分情况下你需要关心的错误是消息处理的时间超过session timeout.
这种情况发生时,协调者会将消费者从消费组中剔除出去,结果会抛出CommitFailedException.
应用程序应该处理这种错误,比如尝试从上次成功提交的offset开始回滚任何因为消息消费引起的改变.

通常情况下,你应该保证只有在消息成功被处理之后,才提交offset(但是offset是否能够成功完成是不一定的).
如果消费者在提交offset之前崩溃了,那么已经成功处理的那部分消息(也是最近的消息)就不得不重新处理.
如果提交策略能够保证最近提交的offset永远不会超过当前的position,你就能得到”至少一次”的消息发送语义.

[![commit ahead pos](http://img.blog.csdn.net/20160221172452583)](http://img.blog.csdn.net/20160221172452583)

通过更改提交策略使得当前position不会超过最近提交的offset(比如上图),你可以得到”最多一次”的语义.
如果消费者在position赶上lastCommittedOffset之前就崩溃了(还没处理消息时就提前提交offset).
那么这中间的那些消息就会丢失了(因此下次只会从lastCommitOffset开始,而不是current position).
虽然有这样的缺点,但你能保证的是不会有消息被处理两次(所以说任何优点都是要牺牲一点代价的).
下面的示例中,只要更改提交offset和消息处理的顺序即可.

```
try {
  while (running) {
  ConsumerRecords<String, String> records = consumer.poll(1000);
  try {
    consumer.commitSync();
    for (ConsumerRecord<String, String> record : records)
      System.out.println(record.offset() + ": " + record.value());
    } catch (CommitFailedException e) {
      // application specific failure handling
    }
  }
} finally {
  consumer.close();
}

```

注意使用`自动提交offset`只提供”至少一次”的处理语义,因为消费者确保要提交的offsets的消息是已经返回给应用程序的.
最坏情况下你需要重新处理的消息的数量是设置的`提交间隔`这段时间内的所有消息(因为没有提及offset需要重新处理消息).

> 译注:应用程序是我们自己编写的客户端代码,而消费者则是KafkaConsumer对象,有时我们认为客户端和应用程序是同一个概念,
> 因为应用程序中会使用消费者对象. 通过消费者的poll返回消息给应用程序,在这之后才会提交offsets,没有消息无从提交

而使用commit API(即手动提交offset),你可以更加自由地控制在你可接受范围内重新处理的消息数量.
极端情况下,你可以在每条消息处理之后都提交一次offset(当然也是有代价的,就是更加频繁的IO操作):

```
try {
  while (running) {
    ConsumerRecords<String, String> records = consumer.poll(1000);
    try {
      for (ConsumerRecord<String, String> record : records) {
        System.out.println(record.offset() + ": " + record.value());
        consumer.commitSync(Collections.singletonMap(record.partition(), new OffsetAndMetadata(record.offset() + 1)));
      }
    } catch (CommitFailedException e) {
      // application specific failure handling
    }
  }
} finally {
  consumer.close();
}

```

在本例中,我们显示地传递要提交的offset给commitSync方法. committed offset总是应该是应用程序读取的下一条消息的offset.
如果使用不带参数的commitSync,消费者会使用返回给应用程序的最近的offset+1作为提交的offset.
这里不能使用它的原因是它会允许committed位置比实际处理的position要超前(而这里的情况刚好相反).

很显然在每条消息处理之后都调用一次提交方法在大多数情况下并不是好的办法, 因为每次的提交请求发送给服务器,到返回结果之前
处理消息的线程都不得不被阻塞住, 这当然是一大性能杀手. 更理想的方式应该是每个N条消息就提交一次,N可以为了更好的性能而调整

这个例子中的commitSync方法的参数是一个map,从topic partition到一个OffsetAndMetadata的实例.
commit API允许你在每次提交时添加额外的元数据信息,比如记录提交的时间,发送请求的主机,或者应用程序需要的其他任何信息.

替代提交每条接收到的消息的另外一种更理想的策略是当你完成处理每个partition的消息时才提交partition级别的offset.
ConsumerRecords集合提供了访问其中的partitions集合的方法,以及访问每个partition的消息.下面代码模拟了这种策略.

> 译注:前面的代码是针对每一条ConsumerRecord,而下面是针对每个Partition. ConsumerRecord一定是有Partition信息的.
> 但是按照ConsumerRecord记录来循环时,无法保证相同的partition是被同时处理的. 而如果按照Partition级别来处理,
> 因为每个Partition可能有多个ConsumerRecrod,所以下面使用了双层循环:首先是Partition,然后是Partition的每条记录.

```
try {
  while (running) {
    ConsumerRecords<String, String> records = consumer.poll(Long.MAX_VALUE);
    for (TopicPartition partition : records.partitions()) {
      List<ConsumerRecord<String, String>> partitionRecords = records.records(partition);
      for (ConsumerRecord<String, String> record : partitionRecords)
        System.out.println(record.offset() + ": " + record.value());

      long lastoffset = partitionRecords.get(partitionRecords.size() - 1).offset();
      consumer.commitSync(Collections.singletonMap(partition, new OffsetAndMetadata(lastoffset + 1)));
    }
  }
} finally {
  consumer.close();
}

```

目前为止我们主要专注于同步的提交API,消费者同时还暴露了一个异步的API: commitAsync.
使用异步方式提交通常来说会获得更高的吞吐量,因为你的应用程序可以在提交返回之前开始处理下一批的消息.
不过它的代价是你只能在之后的某个时刻才能发现有些commit可能是失败的(异步+回调是一种很好的结合).

```
try {
  while (running) {
    ConsumerRecords<String, String> records = consumer.poll(1000);
    for (ConsumerRecord<String, String> record : records)
      System.out.println(record.offset() + ": " + record.value());

    consumer.commitAsync(new OffsetCommitCallback() {
      @Override
      public void onComplete(Map<TopicPartition, OffsetAndMetadata> offsets,  Exception exception) {
        if (exception != null) {
          // application specific failure handling
        }
      }
    });
  }
} finally {
  consumer.close();
}

```

我们提供了一个回调函数给commitAsync,它会在消费者完成提交动作之后被调用(不管是提交成功还是失败都会调用).
当然如果你不关心提交的结果,你可以使用没有参数的commitAsync.

## 消费组检查

当一个消费组是活动的状态时,你可以通过命令行consumer-groups.sh检查partition的分配情况,以及消费进度.

```
# bin/kafka-consumer-groups.sh --new-consumer --describe --group consumer-tutorial-group --bootstrap-server localhost:9092

```

输出结果是这样的:

```
GROUP, TOPIC, PARTITION, CURRENT OFFSET, LOG END OFFSET, LAG, OWNER
consumer-tutorial-group, consumer-tutorial, 0, 6667, 6667, 0, consumer-1_/127.0.0.1
consumer-tutorial-group, consumer-tutorial, 1, 6667, 6667, 0, consumer-2_/127.0.0.1
consumer-tutorial-group, consumer-tutorial, 2, 6666, 6666, 0, consumer-3_/127.0.0.1

```

上面显示了分配给消费组的所有partitions,哪个consumer拥有了partition,partition最近提交的offset(current offset).
partition的lag指的是log end offset和last committed offset的差距. 管理人员可以监视这些来确保消费组能赶上生产者.
(译注:生产者写入消息,LEO会增加,消费者提交offset,会增加LCO,两者差距小说明消费者的消费速度能赶上生产者的生产速度)

## 使用手动分配

在本篇文章开始前,我们提到新的消费者针对不需要消费组的场景实现了低级API,旧的消费者使用SimpleConsumer可以实现,
但是它需要自己做很多的工作来处理错误处理. 现在使用新的消费者,你只需要分配你要读取的partitions,然后开始polling数据.
下面的示例展示了如何分配一个topic的所有partitions(当然也可以静态分配一部分partitions给消费者).

> 译注:在旧的消费者中,高级API使用消费组提供的语义, 而低级API使用SimpleConsumer. 而新的消费者仍然统一使用poll方式.

```
List<TopicPartition> partitions = new ArrayList<>();
for (PartitionInfo partition : consumer.partitionsFor(topic))
  partitions.add(new TopicPartition(topic, partition.partition()));
consumer.assign(partitions);

```

和subscribe类似,调用assign的参数必须传递你要读取的所有partitions(订阅是指定你要读取的所有topics).
一旦partitions被分配了(subscribe是让消费组动态分配partitions),poll循环和之前的方式是一模一样的.

有一点要注意的是,所有offset提交请求都会经过group coordinator,不管是SimpleConsumer还是Consumer Group.
所以如果你要提交offset,你还是必须要指定正确的group.id,防止和其他的消费者实例的group id发生冲突.

如果一个simple consumer尝试提交offset,它的group id和一个活动的consumer group相同,协调者会拒绝这个提交.
但是如果另外一个simple consumer实例和当前同样是simple consumer的实例有相同的group id,则是不会有问题的.

> 译注:消费组有group id,而simple consumer也会指定group id,但是simple consumer的group id不是指消费组.
> 消费组和simple consumer是消费者消费消息的两种不同的实现,一个是high-level,一个是low-level.

## 结论

新的消费者给kafka社区带来了很多好处,包括干净的API,更好的安全性,更少的依赖.这篇文章介绍了它的基本用法,
并主要专注于poll事件循环的语义以及使用commit API来控制消息发送的语义. 还有很多的细节没有覆盖,但是这足以让入门.
尽管新的消费者的开发还在进行中,我们仍然建议你尝试. 如果有问题,欢迎在邮件列表里告诉我们.