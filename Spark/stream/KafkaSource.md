# `KafkaSource`
A [`Source`]() that reads data from Kafka using the following design.

- The [`KafkaSourceOffset`]() is the custom [`Offset`]() defined for this source that contains  a map of `TopicPartition -> offset`. Note that this offset is 1 + (available offset). For  example if the last record in a Kafka topic "t", partition 2 is offset 5, then KafkaSourceOffset will contain TopicPartition("t", 2) -> 6. This is done keep it consistent with the semantics of `KafkaConsumer.position()`.

- The [`KafkaSource`]() written to do the following.

  - As soon as the source is created, the pre-configured [`KafkaOffsetReader`]() is used to query the initial offsets that this source should start reading from. This is used to create the first batch.

  - `getOffset()` uses the [`KafkaOffsetReader`]() to query the latest available offsets, which are returned as a [`KafkaSourceOffset`]().

  - `getBatch()` returns a DF that reads from the '**start offset**' until the '**end offset**' in for each partition. *The end offset is excluded* to be consistent with the semantics of [`KafkaSourceOffset`]() and `KafkaConsumer.position()`.

  - The DF returned is based on [`KafkaSourceRDD`]() which is constructed such that the data from Kafka topic + partition is consistently read by the same executors across batches, and cached KafkaConsumers in the executors can be reused efficiently. See the docs on [`KafkaSourceRDD`]() for more details.

Zero data lost is not guaranteed when topics are deleted. If zero data lost is critical, the user must make sure all messages in a topic have been processed when deleting a topic.

There is a known issue caused by KAFKA-1894: the query using `KafkaSource` maybe cannot be stopped. To avoid this issue, you should make sure stopping the query before stopping the Kafka brokers and not use wrong broker addresses.

# `KafkaOffsetReader`
This class uses Kafka's own [`KafkaConsumer`]() API to read data offsets from Kafka. The [`ConsumerStrategy`]() class defines which Kafka topics and partitions should be read by this source. These strategies directly correspond to the different consumption options in. This class is designed to return a configured [`KafkaConsumer`]() that is used by the [`KafkaSource`]() to query for the offsets. See the docs on
[`org.apache.spark.sql.kafka010.ConsumerStrategy`]() for more details.

Note: This class is not Thread Safe