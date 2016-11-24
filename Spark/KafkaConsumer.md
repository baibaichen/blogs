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