# StateStore in Apache Spark Structured Streaming

During my last Spark exploration of the RPC implementation one class caught my attention. It was `StateStoreCoordinator` used by the state store that is an important place in Structured Streaming pipelines.

This post presents the Apache Spark Structured Streaming state store. The first section gives a general idea about it. The second part focuses on its API while the last one shows a sample code involving state store.

## State store defined

The queries in Structured Streaming are different than the queries in batch-oriented Spark SQL. In the batch processing the query is executed against bounded amount of data, thus the computed results can be final. It's not the case for the streaming processing where the results can grow infinitely. ==So growing results are stored in a **fault-tolerant state store**==.

The purpose of the state store is to provide a reliable place from where the engine can read the intermediary result of Structured Streaming aggregations. Thanks to this place Spark can, even in the case of driver failure, recover the processing state to the point before the failure. In the analyzed version (2.2.1), the state store is backed by a HDFS-like distributed filesystem. **And in order to guarantee recoverability, at least 2 most recent versions must be stored.** For instance, if the batch#10 fails in the middle of processing, then the state store will probably have the states for batch#9 and a half of batch#10. Spark will restart the processing from the batch#9 because it's the last one completed successfully. As we'll discover in the next part, a garbage collection mechanism for too old states exists as well.

Technically the state store is an object presented in each executor, storing the data as key-value pairs and, as already told, used for streaming aggregations. The object is the implementation of `org.apache.spark.sql.execution.streaming.state.StateStore` trait. As told in the previous paragraph, the single supported store is actually `HDFSBackedStateStore`.

## State store implementation details

To have a better idea about what happens under-the-hood, without entering a lot into details now (as promised, stateful aggregations will be covered in further post), the image below should help:

![img](https://www.waitingforcode.com/public/images/articles/spark_statestore.png)

The diagram above shows the method generating `org.apache.spark.sql.execution.streaming.state.StateStoreRDD`. As its name indicates, this RDD is responsible for the execution of computations against available state stores. The implementation of `StateStoreRDD` is pretty simple since it retrieves the state store for given method and partition, and executes the `storeUpdateFunction: (StateStore, Iterator[T]) => Iterator[U]` function passed by one of callers on it:

```scala
override def compute(partition: Partition, ctxt: TaskContext): Iterator[U] = {
  var store: StateStore = null
  val storeId = StateStoreId(checkpointLocation, operatorId, partition.index)
  store = StateStore.get(
    storeId, keySchema, valueSchema, storeVersion, storeConf, confBroadcast.value.value)
  val inputIter = dataRDD.iterator(partition, ctxt)
  storeUpdateFunction(store, inputIter)
}
```

The `StateStoreRDD` is the only place accessing to state store object. Before resolving the mystery of `storeUpdateFunction`, let's inspect the `StateStore` trait contract:

- *def id: StateStoreId* - returns the id of given state store. The id is represented by *StateStoreId* instance and is described by: checkpoint location, operator id and partition id. The first attribute comes either from *checkpointLocation* option or *spark.sql.streaming.checkpointLocation* property. The second one represents the id for the current stateful operator in the query plan. It's defined in *IncrementalExecution* and updated at every *SparkPlan* execution. The last property is related to the partition id of underlying RDD.
- *def version: Long* - defines the version of the data. It's incremented with every update. Firstly it's updated locally and keep in pending state. Only after the commit to the state store the locally changed version is considered as the new current version for the aggregation. 
  At this moment it's important to mention that Spark won't keep all versions. *org.apache.spark.sql.execution.streaming.state.HDFSBackedStateStoreProvider#cleanup* method keeps only last X versions where X corresponds to the number defined in the *spark.sql.streaming.minBatchesToRetain* property. It defines the minimum number of batches that must be retained to made the processing recoverable.
- `def commit(): Long` - it commits the local changes and returns the new version of state store. In the case of *HDFSBackedStateStore* changes are progressively saved to a temporary file by each executor. The file is located in a HDFS-compatible file system directory: checkpointLocation/operatorId/partitionId. From that is created this file representing all the updates ($version.delta).
- *CRUD methods* - Spark doesn't commit all state as it. Each key can be either added/updated (*def put(key: UnsafeRow, value: UnsafeRow): Unit*) or removed (*def remove(key: UnsafeRow): Unit* or *def remove(condition: UnsafeRow => Boolean): Unit*). In the case of any errors the current state will be cleaned with *def abort(): Unit*. 
  The amount of written data depends on the used output mode. If it's *complete*, then Spark writes all rows to the state store. If it's *append* or *update* it writes only the rows that before the watermark.
- *def filter(condition: (UnsafeRow, UnsafeRow) => Boolean): Iterator[(UnsafeRow, UnsafeRow)]* - StateStore also exposes the filtering method that returns the rows matchig defined condition. Currently it's used by *FlatMapGroupsWithStateExec* to retrieve expired keys. The filter method should be fail-safe, i.e. do not fail during the filtering when some of states are updated. The iterated entries are backed by *java.util.concurrent.ConcurrentHashMap* instance that provides this fail-safe guarantee since it iterates over snapshot map representation.

The StateStore has a companion object defining helper methods to create and retrieve stores by their ids. Another useful class is *StateStoreProvider* implementation (HDFSBackedStateStoreProvider). It's used in the companion object methods to get given store and execute maintenance task (cleaning old states). The maintenance task is also responsible for generating snapshot files. These file consolidate multiple state store files (delta files) into a single snapshot file. The snapshot file reduces the lineage of delta files by taking the delta files of the last version and saving all changes in a single file.

This whole logic can be resumed as follows: the executors write local changes (added/updated/removed rows) to a file stream representing a temporary delta file. At the end they call commit method that: closes the stream, creates the delta file for given version, logs this fact with the *Committed version...* message and changes the state of the store from UPDATING to *COMMITTED*. The commit method is called from the storeUpdateFunction presented above. Apart that, there is also a background task making some maintenance work, i.e. consolidating finalized delta files into 1 single file called snapshot file and removing old snapshot and delta files.

> **State store files**
> The state store deals with 2 kinds of files: delta and snapshot. The delta file contains the state representation of the results for each query execution. It's constructed from temporary delta file supplied by the row changes registered in given executor (state store is related to a partition and each executor stores the versioned data in a hash map). The name of that temporary file is resolved from the *s"temp-${Random.nextLong}"* pattern. At the end, i.e. when commit method is called, the final delta file (*s"$version.delta"*) is created for the new version. At the end multiple delta files can be consolidated to a snapshot file those the name is *s"$version.snapshot"*. The following schema resumes all of that: 
> ![img](https://www.waitingforcode.com/public/images/articles/spark_statestore_files.png)

Regarding to the storeUpdateFunction quoted previously, it defines what to do with the data generated in given micro-batch. Its implementation depends a lot of the object defining it. For instance in the case of *StateStoreSaveExec*, this function handles the data according to the output mode used by the writer and either it outputs: all rows every time (complete mode), only the rows evicted from the data store (append mode) or only the updated rows (update mode). In the case of *StreamingDeduplicateExec* operator, Spark saves the firstly encountered rows in the state store. This detection is done by the following snippet:

```scala
val result = baseIterator.filter { r =>
  val row = r.asInstanceOf[UnsafeRow]
  val key = getKey(row)
  val value = store.get(key)
  if (value.isEmpty) {
    store.put(key.copy(), StreamingDeduplicateExec.EMPTY_ROW)
    numUpdatedStateRows += 1
    numOutputRows += 1
    true
  } else {
    // Drop duplicated rows
    false
  }
}
```

The *FlatMapGroupsWithStateExec* uses the state store to obviously save the generated state but also to handle rows expiration.

## State store example

The 2 tests below prove that the state store is used only in the streaming processing and that different version are managed by the engne:

```scala
"stateful count aggregation" should "use state store" in {
  val logAppender = InMemoryLogAppender.createLogAppender(Seq("Retrieved version",
    "Reported that the loaded instance StateStoreId", "Committed version"))
  val testKey = "stateful-aggregation-count-state-store-use"
  val inputStream = new MemoryStream[(Long, String)](1, sparkSession.sqlContext)
  val aggregatedStream = inputStream.toDS().toDF("id", "name")
    .groupBy("id")
    .agg(count("*"))
  inputStream.addData((1, "a1"), (1, "a2"), (2, "b1"),
    (2, "b2"), (2, "b3"), (2, "b4"), (1, "a3"))
 
  val query = aggregatedStream.writeStream.trigger(Trigger.ProcessingTime(1000)).outputMode("update")
    .foreach(
      new InMemoryStoreWriter[Row](testKey, (row) => s"${row.getAs[Long]("id")} -> ${row.getAs[Long]("count(1)")}"))
    .start()
 
  query.awaitTermination(15000)
 
  val readValues = InMemoryKeyedStore.getValues(testKey)
  readValues should have size 2
  readValues should contain allOf("1 -> 3", "2 -> 4")
  // The assertions below show that the state is involved in the execution of the aggregation
  // The commit messages are the messages like:
  //  Committed version 1 for HDFSStateStore[id=(op=0,part=128),
  // dir=/tmp/temporary-6cbcad4e-70aa-4691-916c-cfccc842716b/state/0/128] to file
  // /tmp/temporary-6cbcad4e-70aa-4691-916c-cfccc842716b/state/0/128/1.delta
  val commitMessages = logAppender.getMessagesText().filter(_.startsWith("Committed version"))
  commitMessages.filter(_.startsWith("Committed version 1 for HDFSStateStore")).nonEmpty shouldEqual(true)
  // Retrieval messages look like:
  // version 0 of HDFSStateStoreProvider[id = (op=0, part=2), dir =
  // /tmp/temporary-cb59691c-21dc-4b87-9d76-de108ab32778/state/0/2] for update
  // (org.apache.spark.sql.execution.streaming.state.HDFSBackedStateStoreProvider:54)
  // It proves that the state is updated (new state is stored when new data is processed)
  val retrievalMessages = logAppender.getMessagesText().filter(_.startsWith("Retrieved version"))
  retrievalMessages.filter(_.startsWith("Retrieved version 0 of HDFSStateStoreProvider")).nonEmpty shouldEqual(true)
  // The report messages show that the state is physically loaded. An example of the message looks like:
  // Reported that the loaded instance StateStoreId(/tmp/temporary-6cbcad4e-70aa-4691-916c-cfccc842716b/state,0,3)
  // is active (org.apache.spark.sql.execution.streaming.state.StateStore:58)
  val reportMessages = logAppender.getMessagesText().filter(_.startsWith("Reported that the loaded instance"))
  reportMessages.filter(_.endsWith("state,0,1) is active")).nonEmpty shouldEqual(true)
  reportMessages.filter(_.endsWith("state,0,2) is active")).nonEmpty shouldEqual(true)
  reportMessages.filter(_.endsWith("state,0,3) is active")).nonEmpty shouldEqual(true)
}
 
"stateless count aggregation" should "not use state store" in {
  val logAppender = InMemoryLogAppender.createLogAppender(Seq("Retrieved version",
    "Reported that the loaded instance StateStoreId", "Committed version"))
  val data = Seq((1, "a1"), (1, "a2"), (2, "b1"), (2, "b2"), (2, "b3"), (2, "b4"), (1, "a3")).toDF("id", "name")
 
  val statelessAggregation = data.groupBy("id").agg(count("*").as("count")).collect()
 
  val mappedResult = statelessAggregation.map(row => s"${row.getAs[Int]("id")} -> ${row.getAs[Long]("count")}").toSeq
  mappedResult should have size 2
  mappedResult should contain allOf("1 -> 3", "2 -> 4")
  logAppender.getMessagesText() shouldBe empty
}
```

The state store is a required element to handle the aggregation results changing in each micro-batch execution. As shown, it's strongly related to the output mode used by the writer. The mode defines which rows (all or not expired) will be saved in the new version of the state. At the time of writing the only supported store is a HDFS-like distributed file system but the StateStore trait seems to be easily adaptable to storage engines. However, the state store exists only in structured streaming pipelines. It was proven in the tests defined in the 3rd section.

Read also about *StateStore in Apache Spark Structured Streaming* here: [State Store: A new framework for state management for computing Streaming Aggregates ](https://issues.apache.org/jira/browse/SPARK-13809), [StateStore — Streaming Aggregation State Management ](https://github.com/jaceklaskowski/spark-structured-streaming-book/blob/master/spark-sql-streaming-StateStore.adoc), [State Store for Streaming Aggregation ](https://docs.google.com/document/d/1-ncawFx8JS5Zyfq1HAEGBx56RDet9wfVp_hDM8ZL254/edit).

# Stateful aggregations in Apache Spark Structured Streaming

Recently we discovered the concept of state stores used to deal with stateful aggregations in Structured Streaming. But at that moment we didn't spend the time on these aggregations. As promised, they'll be described now.

This post begins with a short comparison between aggregations and stateful aggregations. Directly after the article ends with some tests illustrating the stateful aggregations.

## Aggregations vs stateful aggregations

An aggregation is the operation computing a single value from multiple values. In Apache Spark an example of this kind of operation can be count or sum method. In the context of the streaming applications we talk about *stateful aggregations*, i.e. aggregations having a state those value evolves incrementally during the time.

After the explanations given for [output modes](https://www.waitingforcode.com/apache-spark-structured-streaming/output-modes-apache-spark-structured-streaming/read), [state store](https://www.waitingforcode.com/apache-spark-structured-streaming/statestore-apache-spark-structured-streaming/read) and [triggers](https://www.waitingforcode.com/apache-spark-structured-streaming/triggers-apache-spark-structured-streaming/read) in Structured Streaming, it'd be easier to understand the specificity of the stateful aggregations. As told, the output modes determines not only the amount of the data but also, if used with watermark, when the intermediary state can be dropped. All these intermediary state is then persisted in a fault-tolerant state store. The state computation is executed at regular interval by the triggers for the data accumulated from the last trigger execution The following schema shows how all these part work together:

![img](https://www.waitingforcode.com/public/images/articles/spark_stateful_aggregation.png)

Thus to make it simple we could define the stateful aggregation as an aggregation those result evolves in time. The results computation is launched by the trigger and saved in the state store from where it can be dropped after the processed data passes before the watermark (if defined).

## Stateful aggregation examples

Two tests show the stateful aggregation in Apache Spark Structured Streaming:

``` scala
"stateful count aggregation" should "succeed after grouping by id" in {
  val testKey = "stateful-aggregation-count"
  val inputStream = new MemoryStream[(Long, String)](1, sparkSession.sqlContext)
  val aggregatedStream = inputStream.toDS().toDF("id", "name")
    .groupBy("id")
    .agg(count("*"))
 
  val query = aggregatedStream.writeStream.trigger(Trigger.ProcessingTime(1000)).outputMode("update")
    .foreach(
      new InMemoryStoreWriter[Row](testKey, (row) => s"${row.getAs[Long]("id")} -> ${row.getAs[Long]("count(1)")}"))
  .start()
 
  new Thread(new Runnable() {
    override def run(): Unit = {
      inputStream.addData((1, "a1"), (1, "a2"), (2, "b1"))
      while (!query.isActive) {}
      Thread.sleep(2000)
      inputStream.addData((2, "b2"), (2, "b3"), (2, "b4"), (1, "a3"))
    }
  }).start()
 
  query.awaitTermination(25000)
 
  val readValues = InMemoryKeyedStore.getValues(testKey)
  readValues should have size 4
  readValues should contain allOf("1 -> 2", "2 -> 1", "1 -> 3", "2 -> 4")
}
 
"sum stateful aggregation" should "be did with the help of state store" in {
  val logAppender = InMemoryLogAppender.createLogAppender(Seq("Retrieved version",
  "Reported that the loaded instance StateStoreId", "Committed version"))
  val testKey = "stateful-aggregation-sum"
  val inputStream = new MemoryStream[(Long, Long)](1, sparkSession.sqlContext)
  val aggregatedStream = inputStream.toDS().toDF("id", "revenue")
    .groupBy("id")
    .agg(sum("revenue"))
 
  val query = aggregatedStream.writeStream.trigger(Trigger.ProcessingTime(1000)).outputMode("update")
    .foreach(
      new InMemoryStoreWriter[Row](testKey, (row) => s"${row.getAs[Long]("id")} -> ${row.getAs[Double]("sum(revenue)")}"))
    .start()
 
  new Thread(new Runnable() {
    override def run(): Unit = {
      inputStream.addData((1, 10), (1, 11), (2, 20))
      while (!query.isActive) {}
      Thread.sleep(2000)
      inputStream.addData((2, 21), (2, 22), (2, 23), (1, 12))
    }
  }).start()
 
  query.awaitTermination(35000)
 
  // The assertions below show that the state is involved in the execution of the aggregation
  // The commit messages are the messages like:
  //  Committed version 1 for HDFSStateStore[id=(op=0,part=128),
  // dir=/tmp/temporary-6cbcad4e-70aa-4691-916c-cfccc842716b/state/0/128] to file
  // /tmp/temporary-6cbcad4e-70aa-4691-916c-cfccc842716b/state/0/128/1.delta
  val commitMessages = logAppender.getMessagesText().filter(_.startsWith("Committed version"))
  commitMessages.filter(_.startsWith("Committed version 1 for HDFSStateStore")).nonEmpty shouldEqual(true)
  commitMessages.filter(_.startsWith("Committed version 2 for HDFSStateStore")).nonEmpty shouldEqual(true)
  // Retrieval messages look like:
  // Retrieved version 1 of HDFSStateStoreProvider[id = (op=0, part=0),
  // dir = /tmp/temporary-6cbcad4e-70aa-4691-916c-cfccc842716b/state/0/0] for update
  // (org.apache.spark.sql.execution.streaming.state.HDFSBackedStateStoreProvider:54)
  // It proves that the state is updated (new state is stored when new data is processed)
  val retrievalMessages = logAppender.getMessagesText().filter(_.startsWith("Retrieved version"))
  retrievalMessages.filter(_.startsWith("Retrieved version 0 of HDFSStateStoreProvider")).nonEmpty shouldEqual(true)
  retrievalMessages.filter(_.startsWith("Retrieved version 1 of HDFSStateStoreProvider")).nonEmpty shouldEqual(true)
  // The report messages show that the state is physically loaded. An example of the message looks like:
  // Reported that the loaded instance StateStoreId(/tmp/temporary-6cbcad4e-70aa-4691-916c-cfccc842716b/state,0,3)
  // is active (org.apache.spark.sql.execution.streaming.state.StateStore:58)
  val reportMessages = logAppender.getMessagesText().filter(_.startsWith("Reported that the loaded instance"))
  reportMessages.filter(_.endsWith("state,0,1) is active")).nonEmpty shouldEqual(true)
  reportMessages.filter(_.endsWith("state,0,2) is active")).nonEmpty shouldEqual(true)
  reportMessages.filter(_.endsWith("state,0,3) is active")).nonEmpty shouldEqual(true)
  // The stateful character of the processing is also shown through the
  // stateful operators registered in the last progresses of the query
  // Usual tests on the values
  val readValues = InMemoryKeyedStore.getValues(testKey)
  readValues should have size 4
  readValues should contain allOf("2 -> 20", "1 -> 21", "1 -> 33", "2 -> 86")
}
 
"stateful count aggregation" should "succeed without grouping it by id" in {
  val logAppender = InMemoryLogAppender.createLogAppender(Seq("Retrieved version",
    "Reported that the loaded instance StateStoreId", "Committed version"))
  val testKey = "stateful-aggregation-count-without-grouping"
  val inputStream = new MemoryStream[(Long, String)](1, sparkSession.sqlContext)
  val aggregatedStream = inputStream.toDS().toDF("id", "name")
    .agg(count("*").as("all_rows"))
 
  val query = aggregatedStream.writeStream.trigger(Trigger.ProcessingTime(1000)).outputMode("update")
    .foreach(
      new InMemoryStoreWriter[Row](testKey, (row) => s"${row.getAs[Long]("all_rows")}"))
    .start()
 
  new Thread(new Runnable() {
    override def run(): Unit = {
      inputStream.addData((1, "a1"), (1, "a2"), (2, "b1"))
      while (!query.isActive) {}
      Thread.sleep(2000)
      inputStream.addData((2, "b2"), (2, "b3"), (2, "b4"), (1, "a3"))
    }
  }).start()
 
  query.awaitTermination(25000)
 
  // We can see that the same assertions as for the previous test pass. It means that the
  // stateful aggregation doesn't depend on the presence or not of the groupBy(...) transformation
  val commitMessages = logAppender.getMessagesText().filter(_.startsWith("Committed version"))
  commitMessages.filter(_.startsWith("Committed version 1 for HDFSStateStore")).nonEmpty shouldEqual(true)
  commitMessages.filter(_.startsWith("Committed version 2 for HDFSStateStore")).nonEmpty shouldEqual(true)
  val retrievalMessages = logAppender.getMessagesText().filter(_.startsWith("Retrieved version"))
  retrievalMessages.filter(_.startsWith("Retrieved version 0 of HDFSStateStoreProvider")).nonEmpty shouldEqual(true)
  retrievalMessages.filter(_.startsWith("Retrieved version 1 of HDFSStateStoreProvider")).nonEmpty shouldEqual(true)
  val reportMessages = logAppender.getMessagesText().filter(_.startsWith("Reported that the loaded instance"))
  reportMessages.filter(_.endsWith("state,0,0) is active")).nonEmpty shouldEqual(true)
  val readValues = InMemoryKeyedStore.getValues(testKey)
  readValues should have size 2
  readValues should contain allOf("3", "7")
}
```

This short post was the resume of the stateful data processing in Apache Spark Structured Streaming. After discovering the output modes, state store and triggers, we learned something more about the stateful aggregations. And in fact they are no much different than the stateless aggregations. The main difference consists on the fact that the computed values can change in the subsequent query executions, as shown in the first test from the 2nd section. And those intermediary results are stored in already described state store, what was proved in the second test from the same section.

# Stateful transformations with mapGroupsWithState

[TODO]

# Fault tolerance in Apache Spark Structured Streaming

The Structured Streaming guarantees end-to-end exactly-once delivery (in micro-batch mode) through the semantics applied to ==state management==, ==data source== and ==data sink==. The state was more covered in the post about the state store but 2 other parts still remain to discover.

This post is divided in 2 main parts. The first part focuses on data sources and explains how they contribute in the end-2-end exactly once delivery in the case of micro-batch processing. The second part is about the sinks while the last summarizes all theoretical points in an example.

## Data sources

In terms of exactly-once processing the source must be *replayable*. That said it must allow to track the current read position and also to start the reprocessing from the last failure position. Both properties help to recover the processing state after any arbitrary failure (driver or executor included). A good examples of replayable sources are [Apache Kafka](https://www.waitingforcode.com/apache-kafka) or its cloud-based collegue, Amazon Kinesis. Both are able to track the currently read elements - Kafka with offsets and Kinesis with sequence numbers. A good example of not replayable sources is *org.apache.spark.sql.execution.streaming.MemoryStream* that can't be restored after closing the application because the data is stored in volatile memory.

The processed offsets are tracked thanks to the **checkpoint mechanism**. In Structured Streaming the items stored in checkpoint files are mainly the metadata about the offsets processed in the current batch. The checkpoint are stored in the location specified in *checkpointLocation* option or *spark.sql.streaming.checkpointLocation* configuration entry.

In the case of micro-batch execution the checkpoint integrates in the following schema:

- the checkpoint location is passed from `DataStreamWriter#startQuery()` method to `StreamExecution` abstract class through `StreamingQueryManager`'s `startQuery` and `createQuery` methods.

- `StreamExecution` initializes the object `org.apache.spark.sql.execution.streaming.OffsetSeqLog`. This object represents the WAL log recording the offsets present in each of processed batches. This field represents the logic of handling offsets in data sources. The offsets for the current micro-batch (let's call it N) are always written before the processing is done. **==This fact assumes also that all data from the previous micro-batch (N-1) are correctly written to the output sink==**. It's represented in the following code snippet coming from `MicroBatchExecution#constructNextBatch()`

    ``` scala
    updateStatusMessage("Writing offsets to log")
    reportTimeTaken("walCommit") {
      assert(offsetLog.add(
        currentBatchId,
        availableOffsets.toOffsetSeq(sources, offsetSeqMetadata)),
        s"Concurrent update to the log. Multiple streaming jobs detected for $currentBatchId")
        logInfo(s"Committed offsets for batch $currentBatchId. " +
        s"Metadata ${offsetSeqMetadata.toString}")
     
      // NOTE: The following code is correct because runStream() processes exactly one
      // batch at a time. If we add pipeline parallelism (multiple batches in flight at
      // the same time), this cleanup logic will need to change.
     
      // Now that we've updated the scheduler's persistent checkpoint, it is safe for the
      // sources to discard data from the previous batch.
      if (currentBatchId != 0) {
        val prevBatchOff = offsetLog.get(currentBatchId - 1)
        if (prevBatchOff.isDefined) {
          prevBatchOff.get.toStreamProgress(sources).foreach {
            case (src: Source, off) => src.commit(off)
            case (reader: MicroBatchReader, off) =>
              reader.commit(reader.deserializeOffset(off.json))
          }
        } else {
          throw new IllegalStateException(s"batch $currentBatchId doesn't exist")
        }
      }
       
      // It is now safe to discard the metadata beyond the minimum number to retain.
      // Note that purge is exclusive, i.e. it purges everything before the target ID.
      if (minLogEntriesToMaintain < currentBatchId) {
        offsetLog.purge(currentBatchId - minLogEntriesToMaintain)
        commitLog.purge(currentBatchId - minLogEntriesToMaintain)
      }
    }
    ```
- if the offset checkpoint file already exist when the streaming query starts, the engine tries to retrieve previously processed offsets in `populateStartOffsets(sparkSessionToRunBatches: SparkSession)`. The resolution algorithm is done in the following steps:

  1. resolve the id of the current batch, the offsets to process (from Nth checkpoint file) and the offsets committed in N-1 batch. **==At this moment the offsets from Nth batch are considered as available to the processing and the ones from N-1 batch as already committed==**
  2. identify the real current batch. When batch id of the Nth offsets is the same as the last batch id written in the commit logs, the engine sets the offsets offsets as committed ones and increments the batch id by 1. A new micro-batch is then recomputed by querying the sink for new offsets to process. Otherwise the resolved micro-batch is considered as the real current batch and the processing starts from offsets to process. To fully understand this process it's important to also know what the commit log is. Since it's more related to the sink it'll detailed in the next part.

## Data sinks

The **commit log** is a WAL recording the ids of completed batches. It's used to check if given batch was fully processed, i.e. if all offsets were read and if the output was committed to the sink. Internally it's represented as *org.apache.spark.sql.execution.streaming.CommitLog* and it's executed immediately before the processing of the next trigger, as in the following schema:

![img](https://www.waitingforcode.com/public/images/articles/spark_wal_structured_streaming.png)

Indeed, neither the replayable source nor commit log don't guarantee exactly-once processing itself. What if the batch commit fails ? **As told previously, the engine will detect the last committed offsets as offsets to reprocess and output once again the processed data to the sink**. It'll obviously lead to a duplicated output. But it'd be the case only when the writes and the sink aren't *idempotent*.

An idempotent write is the one that generates the same written data for given input. The idempotent sink is the one that writes given generated row only once, even if it's sent multiple times. A good example of such sink are key-value data stores. Now, if the writer is idempotent, obviously it generates the same keys every time and since the row identification is key-based, the whole process is idempotent. Together with replayable source it guarantees exactly-once end-2-end processing.

## Exactly-once processing example

To see how exactly-once end-to-end processing works, we'll take a simple example of files that are transformed and written back as another files in different directory. To illustrate a failure and thus fault tolerance, the first processing of one entry will fail:

[?](https://www.waitingforcode.com/apache-spark-structured-streaming/fault-tolerance-apache-spark-structured-streaming/read#)

```
`override` `def` `beforeAll()``:` `Unit ``=` `{``  ``Path(TestConfiguration.TestDirInput).createDirectory()``  ``Path(TestConfiguration.TestDirOutput).createDirectory()``  ``for` `(i <- ``1` `to ``10``) {``    ``val` `file ``=` `s``"file${i}"``    ``val` `content ``=``      ``s``""``"``        ``|{"``id``": 1, "``name``": "``content``1``=``${i}``"}``        ``|{"``id``": 2, "``name``": "``content``2``=``${i}``"}``      ``"``""``.stripMargin``    ``File(s``"${TestConfiguration.TestDirInput}/${file}"``).writeAll(content)``  ``}``}` `override` `def` `afterAll()``:` `Unit ``=` `{``  ``Path(TestConfiguration.TestDirInput).deleteRecursively()``  ``Path(TestConfiguration.TestDirOutput).deleteRecursively()``}`  `"after one failure"` `should ``"all rows should be processed and output in idempotent manner"` `in {``  ``for` `(i <- ``0` `until ``2``) {``    ``val` `sparkSession``:` `SparkSession ``=` `SparkSession.builder()``      ``.appName(``"Spark Structured Streaming fault tolerance example"``)``      ``.master(``"local[2]"``).getOrCreate()``    ``import` `sparkSession.implicits.``_``    ``try` `{``      ``val` `schema ``=` `StructType(StructField(``"id"``, LongType, nullable ``=` `false``) ``::``        ``StructField(``"name"``, StringType, nullable ``=` `false``) ``::` `Nil)` `      ``val` `idNameInputStream ``=` `sparkSession.readStream.format(``"json"``).schema(schema).option(``"maxFilesPerTrigger"``, ``1``)``        ``.load(TestConfiguration.TestDirInput)``        ``.toDF(``"id"``, ``"name"``)``        ``.map(row ``=``> {``          ``val` `fieldName ``=` `row.getAs[String](``"name"``)``          ``if` `(fieldName ``==` `"content1=7"` `&& !GlobalFailureFlag.alreadyFailed.get() && GlobalFailureFlag.mustFail.get()) {``            ``GlobalFailureFlag.alreadyFailed.set(``true``)``            ``GlobalFailureFlag.mustFail.set(``false``)``            ``throw` `new` `RuntimeException(``"Something went wrong"``)``          ``}``          ``fieldName``        ``})` `      ``val` `query ``=` `idNameInputStream.writeStream.outputMode(``"update"``).foreach(``new` `ForeachWriter[String] {``        ``override` `def` `process(value``:` `String)``:` `Unit ``=` `{``          ``val` `fileName ``=` `value.replace(``"="``, ``"_"``)``          ``File(s``"${TestConfiguration.TestDirOutput}/${fileName}"``).writeAll(value)``        ``}` `        ``override` `def` `close(errorOrNull``:` `Throwable)``:` `Unit ``=` `{}` `        ``override` `def` `open(partitionId``:` `Long, version``:` `Long)``:` `Boolean ``=` `true``      ``}).start()``      ``query.awaitTermination(``15000``)``    ``} ``catch` `{``      ``case` `re``:` `StreamingQueryException ``=``> {``        ``println(``"As expected, RuntimeException was thrown"``)``      ``}``    ``}``  ``}` `  ``val` `outputFiles ``=` `Path(TestConfiguration.TestDirOutput).toDirectory.files.toSeq``  ``outputFiles should have size ``20``  ``val` `filesContent ``=` `outputFiles.map(file ``=``> Source.fromFile(file.path).getLines.mkString(``""``))``  ``val` `expected ``=` `(``1` `to ``2``).flatMap(id ``=``> {``    ``(``1` `to ``10``).map(nr ``=``> s``"content${id}=${nr}"``)``  ``})``  ``filesContent should contain allElementsOf expected``}` `object` `TestConfiguration {``  ``val` `TestDirInput ``=` `"/tmp/spark-fault-tolerance"``  ``val` `TestDirOutput ``=` `s``"${TestDirInput}-output"``}` `object` `GlobalFailureFlag {``  ``var` `mustFail ``=` `new` `AtomicBoolean(``true``)``  ``var` `alreadyFailed ``=` `new` `AtomicBoolean(``false``)``}`
```

Apache Spark Structured Streaming, through replayable sources, fault-tolerant state management, idempotent sinks guarantees exactly-once delivery semantic. As shown in this post, it achieves that thanks to checkpointing. For the sources the engine checkpoints the available offsets that can be later converted to already processed ones. In the case of sinks, the fault-tolerance is provided by commit log when, after the data processing, are submitted the ids of successful batches. And the final third part shown how both parts can work together to provide exactly once processing.