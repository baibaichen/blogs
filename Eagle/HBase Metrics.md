- [ ] Gague, Histogram, Counter的<u>含义是什么</u>？

# Master

## `MetricsAssignmentManagerSourceImpl`

JMX Context = `Master,sub=AssignmentManger`

| 指标                    | 类型        |
| --------------------- | --------- |
| ritCount              | Gague     |
| ritCountOverThreshold | Gague     |
| ritOldestAge          | Gague     |
| assign                | Histogram |
| bulkAssign            | Histogram |
## `MetricsBalancerSourceImpl`

JMX Context = `Master,sub=Balancer`

| 指标                  | 类型        |
| ------------------- | --------- |
| balancerCluster     | Histogram |
| miscInvocationCount | Counter   |

##`MetricsSnapshotSourceImpl`

JMX Context = `Master,sub=Snapshots`

| 指标                  | 类型        |
| ------------------- | --------- |
| snapshotTime        | Histogram |
| snapshotRestoreTime | Histogram |
| snapshotCloneTime   | Histogram |


##`MetricsMasterFilesystemSourceImpl`

JMX Context = `Master,sub=FileSystem`

| 指标                | 类型        |
| ----------------- | --------- |
| hlogSplitSize     | Histogram |
| hlogSplitTime     | Histogram |
| metaHlogSplitSize | Histogram |
| metaHlogSplitTime | Histogram |

##`MetricsMaterSourceImpl`

JMX Context = `Master,sub=Server`

| 指标                   | 类型      |
| -------------------- | ------- |
| clusterRequests      | Counter |
| masterActiveTime     | Gauge   |
| masterStartTime      | Gauge   |
| averageLoad          | Gauge   |
| liveRegionServers    | tag     |
| numRegionServers     | Gauge   |
| deadRegionServers    | tag     |
| numDeadRegionServers | Gauge   |
| zookeeperQuorum      | tag     |
| serverName           | tag     |
| clusterId            | tag     |
| isActiveMaster       | tag     |

# Region Server

##`MetricsEditsReplaySourceImpl`

JMX Context =   `RegionServer,sub=replay`

| 指标              | 类型        |
| --------------- | --------- |
| replayTime      | Histogram |
| replayBatchSize | Histogram |
| replayDataSize  | Histogram |

##`MetricsRegionServerSourceImpl`

JMX Context =  `RegionServer,sub=Server`  

| 指标                            | 类型        |
| ----------------------------- | --------- |
| mutate                        | Histogram |
| slowPutCount                  | Counter   |
| delete                        | Histogram |
| slowDeleteCount               | Counter   |
| get                           | Histogram |
| slowGetCount                  | Counter   |
| increment                     | Histogram |
| slowIncrementCount            | Counter   |
| append                        | Histogram |
| slowAppendCount               | Counter   |
| replay                        | Histogram |
| splitTime                     | Histogram |
| flushTime                     | Histogram |
| splitRequestCount             | Counter   |
| splitSuccessCounnt            | Counter   |
| regionCount                   | Gauge     |
| storeCount                    | Gauge     |
| hlogFileCount                 | Gauge     |
| hlogFileSize                  | Gauge     |
| storeFileCount                | Gauge     |
| memStoreSize                  | Gauge     |
| storeFileSize                 | Gauge     |
| regionServerStartTime         | Gauge     |
| totalRequestCount             | Counter   |
| readRequestCount              | Counter   |
| writeRequestCount             | Counter   |
| checkMutateFailedCount        | Counter   |
| checkMutatePassedCount        | Counter   |
| storeFileIndexSize            | Gauge     |
| staticIndexSize               | Gauge     |
| staticBloomSize               | Gauge     |
| mutationsWithoutWALCount      | Gauge     |
| mutationsWithoutWALSize       | Gauge     |
| percentFilesLocal             | Gauge     |
| splitQueueLength              | Gauge     |
| compactionQueueLength         | Gauge     |
| flushQueueLength              | Gauge     |
| blockCacheFreeSize            | Gauge     |
| blockCacheCount               | Gauge     |
| blockCacheSize                | Gauge     |
| blockCacheHitCount            | Counter   |
| blockCacheMissCount           | Counter   |
| blockCacheEvictionCount       | Counter   |
| blockCacheCountHitPercent     | Gauge     |
| blockCacheExpressHitPercent   | Gauge     |
| updatesBlockedTime            | Counter   |
| flushedCellsCount             | Counter   |
| compactedCellsCount           | Counter   |
| majorCompactedCellsCount      | Counter   |
| flushedCellsSize              | Counter   |
| compactedCellsSize            | Counter   |
| majorCompactedCellsSize       | Counter   |
| blockedRequestCount           | Counter   |
| hedgedReads                   | Counter   |
| hedgedReadWins                | Counter   |
| mobCompactedFromMobCellsCount | Counter   |
| mobCompactedIntoMobCellsCount | Counter   |
| mobCompactedFromMobCellsSize  | Counter   |
| mobCompactedIntoMobCellsSize  | Counter   |
| mobFlushCount                 | Counter   |
| mobFlushedCellsCount          | Counter   |
| mobFlushedCellsSize           | Counter   |
| mobScanCellsCount             | Counter   |
| mobScanCellsSize              | Counter   |
| mobFileCacheCount             | Gauge     |
| mobFileCacheAccessCount       | Counter   |
| mobFileCacheMissCount         | Counter   |
| mobFileCacheEvictedCount      | Counter   |
| mobFileCacheHitPercent        | Gauge     |
| zookeeperQuorum               | tag       |
| serverName                    | tag       |
| clusterId                     | tag       |

##`MetricsWALSourceImpl`

JMX Context = `RegionServer,sub=WAL`

| 指标                    | 类型        |
| --------------------- | --------- |
| appendTime            | Histogram |
| appendSize            | Histogram |
| appendCount           | Counter   |
| slowAppendCount       | Counter   |
| syncTime              | Histogram |
| rollRequest           | Counter   |
| lowReplicaRollRequest | Counter   |

## `MetricsReplicationSourceImpl`

JMX Context = `RegionServer,sub=Replication`

## `MetricsRegionAggregateSourceImpl`

JMX Context = `RegionServer,sub=Regions`，`MetricsRegionAggregateSourceImpl` 聚合 `MetricsRegionSourceImpl` 里的指标，每个Region 有一个 `MetricsRegionSourceImpl` ，`regionNamePrefix`的定义如下：

```java
regionNamePrefix = "Namespace_" + regionWrapper.getNamespace() +
    "_table_" + regionWrapper.getTableName() +
    "_region_" + regionWrapper.getRegionName()  +
    "_metric_";
```

| 指标                             | 类型          |
| ------------------------------ | ----------- |
| `regionNamePrefix+"mutate"`    | CounterLong |
| `regionNamePrefix+"delete"`    | CounterLong |
| `regionNamePrefix+"increment"` | CounterLong |
| `regionNamePrefix+"append"`    | CounterLong |
| `regionNamePrefix+"get"`       | Histogram   |
| `regionNamePrefix+"scanNext"`  | Histogram   |

# IPC
##`MetricsHBaseServerSourceImpl`

JMX Context = `Master,sub=IPC`  `RegionServer,sub=IPC`

| 指标                         | 类型        |
| -------------------------- | --------- |
| authorizationSuccesses     | Counter   |
| authorizationFailures      | Counter   |
| authenticationSuccesses    | Counter   |
| authenticationFailures     | Counter   |
| sentBytes                  | Counter   |
| receivedBytes              | Counter   |
| queueCallTime              | Histogram |
| processCallTime            | Histogram |
| queueSize                  | Gauge     |
| numCallsInGeneralQueue     | Gauge     |
| numCallsInReplicationQueue | Gauge     |
| numCallsInPriorityQueue    | Gauge     |
| numOpenConnections         | Gauge     |
| numActiveHandler           | Gauge     |

#`MetricsRESTSourceImpl`

JMX Context = `REST`

| 指标                  | 类型          |
| ------------------- | ----------- |
| requests            | CounterLong |
| successfulGet       | CounterLong |
| successfulPut       | CounterLong |
| successfulDelete    | CounterLong |
| successfulScanCount | CounterLong |
| failedGet           | CounterLong |
| failedPut           | CounterLong |
| failedDelete        | CounterLong |
| failedScanCount     | CounterLong |

#`MetricsThriftServerSourceImpl`

JMX Context = `Thrift,sub=ThriftOne`  `Thrift,sub=ThriftTwo` 

| 指标             | 类型          |
| -------------- | ----------- |
| batchGet       | Histogram   |
| batchMutate    | Histogram   |
| timeInQueue    | Histogram   |
| thriftCall     | Histogram   |
| slowThriftCall | Histogram   |
| callQueueLen   | CounterLong |
