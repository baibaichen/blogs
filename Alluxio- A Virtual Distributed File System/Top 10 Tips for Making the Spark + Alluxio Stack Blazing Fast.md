# Top 10 Tips for Making the Spark + Alluxio Stack Blazing Fast

The [Apache Spark](https://spark.apache.org/) + [Alluxio](https://www.alluxio.org/) stack is getting quite popular particularly for the [unification of data access across S3 and HDFS](https://www.alluxio.org/docs/1.8/en/advanced/Namespace-Management.html). In addition, compute and storage are increasingly being separated causing larger latencies for queries. Alluxio is leveraged as compute-side virtual storage to improve performance. But to get the best performance, like any technology stack, you need to follow the best practices. This article provides the top 10 tips for performance tuning for real-world workloads when running Spark on Alluxio with data locality, giving the most bang for the buck.

## A Note on Data Locality

High data locality can greatly improve the performance of Spark jobs. When data locality is achieved, Spark tasks can read in-Alluxio data from local Alluxio workers at memory speed (when ramdisk is configured) instead of transferring the data over the network. The first few tips are related to locality.

### Check Data Locality Achieved

Alluxio provides the best performance when Spark tasks are running at Spark workers co-located with Alluxio workers and performing [short-circuit reads and writes](https://www.alluxio.org/docs/1.8/en/Architecture-DataFlow.html#local-cache-hit). There are multiple ways to check if I/O requests are served by short-circuit reads/writes:

- Monitor the metrics at [Alluxio metrics UI page](http://www.alluxio.org/docs/1.8/en/operation/Metrics-System.html) for “Short-circuit reads” and “From Remote Instance” while a Spark job is running. Alternatively, monitor the metrics  `cluster.BytesReadAlluxioThroughput` and `cluster.BytesReadLocalThroughput`. If the local throughput is zero or significantly lower than the total throughput, this job is likely not interfacing with a local Alluxio worker.
- Leverage tools like  `dstat`  to detect network traffic, which will allow us to see whether short-circuit reads are happening and what network traffic throughput is looking like. YARN users can also check the logs in `/var/log/hadoop-yarn/userlogs`  to find some messages, like below, and see if there are no remote reads happening, or all short circuit reads:  `INFO ShuffleBlockFetcherIterator: Started 0 remote fetches in 0 ms` . You can enable the collection of these logs by running your Spark job with the appropriate YARN properties, like " `--master yarn --num-executors 4 --executor-cores 4` ".

In case Spark jobs have a small fraction of short-circuiting reads or writes served by Alluxio, read the following few tips to improve the data locality.

### Ensure Spark Executor Locality

> There are potentially two different levels of data locality for Spark to achieve. The first level is **executor locality**, which means when Spark is deployed on other resource management frameworks (like [Mesos](http://mesos.apache.org/) and [YARN](https://hadoop.apache.org/docs/current/hadoop-yarn/hadoop-yarn-site/YARN.html)). Spark executors are assigned by these resource managers on nodes that have Alluxio workers running. Without executor locality achieved, it is impossible to enforce Alluxio to serve Spark jobs with local the data. Note that we typically see issues in executor locality when Spark deployment is done by resource management frameworks rather than running Spark Standalone.

Spark可能要实现两种不同级别的**数据本地性**。第一层是**executor本地性**，这意味着当spark部署在其他资源管理框架（如Mesos和YARN）上时。这些资源管理器在运行**alluxio worker**的节点上分配**spark executor**。如果没有实现**executor本地性**，就不可能强制Alluxio使用局部数据来服务*Spark job*。注意，当由资源管理框架完成Spark部署，而不是Spark Standalone部署时，通常会看到**executor本地性**问题。

While recent versions of Spark support YARN and Mesos to schedule executors while considering data locality, there are other ways to force executors to start on the desired nodes. One simple strategy is to start an executor on every node, so there will always be an executor on at least the desired nodes.

In practice, it may not be applicable to deploy Alluxio worker on every node in a production environment due to resources constraints. To deploy Alluxio worker together with computation node, one can leverage features from resource management frameworks like [YARN node label](https://hadoop.apache.org/docs/r2.9.1/hadoop-yarn/hadoop-yarn-site/NodeLabel.html). By marking `NodeManagers` with a label "alluxio" (the name of this label is not important), which means that machine contains an Alluxio worker, the user can submit their job to the same label specified machine and launch Spark jobs with executors collocated with Alluxio workers.

### Ensure Spark Task Locality

The next level of data locality is task locality, which means that once the executors are started, Spark can schedule tasks to the executors with locality respected (i.e., scheduling tasks on the executors that are local to the data served from Alluxio). To achieve this goal, Spark task scheduler first gathers all the data locations from Alluxio as a list of hostnames of Alluxio workers and then tries to match the executor hostnames for the scheduling. You can find the Alluxio worker hostnames from the Alluxio master web UI, and you can find the Spark executor hostnames from the Spark driver web UI.

Note that, sometimes, due to various networking environments and configurations, the hostnames of the Alluxio workers may not match the hostnames of the executors, even though they are running on the same machines. In this case, Spark task scheduler will be confused and not able to ensure data locality. Therefore, it is important for the Alluxio workers hostnames to share the same "hostname namespace" as the executor hostnames. One common issue we see is that one uses IP addresses and the other uses hostnames. Once this happens, users can still manually set Alluxio-client property `alluxio.user.hostname` in Spark (see [how](http://www.alluxio.org/docs/1.8/en/compute/Spark.html#advanced-setup)) and have the same value set for `alluxio.worker.hostname`  in Alluxio worker’s site properties; alternatively, users can read the JIRA ticket [SPARK-10149](https://issues.apache.org/jira/browse/SPARK-10149) for solutions from the Spark community.

### Prioritize Locality for Spark Scheduler

With respect to locality, there are a few Spark client side configs to tune:

- Property `spark.locality.wait`: This checks how long to wait to launch a data-local task before then trying to launch on a less-local node. The same wait will be used to step through multiple locality levels ( `process-local` ,  `node-local` , `rack-local`   and then `any` ).
- We can also set this for a specific locality level using `spark.locality.wait.node,` which will customize the locality wait for each node.

## Load Balance

Load balancing is also very important to ensure the execution of Spark jobs are distributed uniformly across different nodes available. The next few tips are related to preventing imbalanced load or task schedule due to skew input data distribution in Alluxio.

### Use DeterministicHashPolicy to Cold-Read Data From UFS Via Alluxio

It is quite common that multiple tasks of a Spark job read the same input file. When this file is not loaded in Alluxio, by default, the Alluxio worker local to each of these tasks will read the same file from UFS. This can lead to the following consequence:

Multiple Alluxio workers are competing for the Alluxio-UFS connection on the same data. If the connection from Alluxio to UFS is slow, each worker can read slowly due to unnecessary competition.

The same data can be replicated multiple times across Alluxio, evicting other useful data for the subsequent queries.

One way to solve this problem is to set  `alluxio.user.ufs.block.read.location.policy`  to `DeterministicHashPolicy`  to coordinate workers to read data from UFS without unnecessary competition — e.g., edit  `spark/conf/spark-defaults.conf`  to ensure at most four (decided by  `alluxio.user.ufs.block.read.location.policy.deterministic.hash.shards` ) random Alluxio workers to read a given block.

```properties
park.driver.extraJavaOptions=-Dalluxio.user.ufs.block.read.location.policy=alluxio.client.block.policy.DeterministicHashPolicy
spark.executor.extraJavaOptions=-Dalluxio.user.ufs.block.read.location.policy.deterministic.hash.shards=4
```
Note that a different set of four random workers will be selected for different blocks.

### Use Smaller Alluxio Block Size for Higher Parallelism

When Alluxio block size is large (512MB by default) relative to the file size, there can be only a few Alluxio workers serving the input files. During the “file scan” phase of Spark, tasks may be assigned to only a small set of servers in order to be **NODE_LOCAL** to the input data, leading to imbalanced load. In this case, caching the input files in Alluxio with smaller blocks (e.g. setting  `alluxio.user.block.size.bytes.default` to 128MB or smaller) allows for better parallelism across servers. Note that customizing the Alluxio block size is not applicable when UFS is HDFS, where the Alluxio block size is forced to be the HDFS block size.

### Tune the Number of Executors and Tasks Per Executor

Running too many tasks in one executor in parallel to read from Alluxio may create resource contention in connections, network bandwidth, and etc. Based on the number of Alluxio workers, one can tune the number of executors and the number of tasks per executor (configurable in Spark) to better distribute the work to more nodes (thus higher bandwidth to Alluxio) and reduce the overhead in resource contention.

### Preload Data Into Alluxio Uniformly

Though Alluxio provides [transparent and async caching](https://www.alluxio.com/blog/asynchronous-caching-in-alluxio-high-performance-for-partial-read-caching), the first cold-read may still have a performance overhead. To avoid this overhead, users can pre-load the data into Alluxio storage space using CLI:

```bash
$ bin/alluxio fs load /path/to/load \
-Dalluxio.user.ufs.block.read.location.policy=\
alluxio.client.file.policy.MostAvailableFirstPolicy
```

Note that this  `load`  command is simply reading the target file from the under store on this single server to promote data to Alluxio. So, the speed to write to Alluxio is bound by that single server. In Alluxio 2.0, we plan to provide an implementation of a distributed `load` to scale the throughput.

## Capacity Management

At a very high level, Alluxio provides a caching service for hot input data. Allocating and managing the caching capacity correctly is also important to achieve good performance.

### Extend Alluxio Storage Capacity With SSD or HDD

Alluxio workers can also manage local SSD or hard disk resources as the complementary to RAM resource. To reduce evictions, we recommend putting multiple storage directories in the same tier rather than a single tier. See [link](https://www.alluxio.org/docs/1.8/en/advanced/Alluxio-Storage-Management.html#single-tier-storage) for more details. One caveat is that for users running Alluxio workers on AWS EC2 instances, the EBS storage mounted as local disk goes over the network and can be slow.

### Prevent Cache Thrashing by Disabling “Passive Caching”

Alluxio client-side property `alluxio.user.file.passive.cache.enabled` controls whether to cache data with additional copies in Alluxio. This property is enabled by default ( `alluxio.user.file.passive.cache.enabled=true` ), so an Alluxio worker can cache another copy of data already cached on other workers on client requests. When this property is `false`, there will be no more copy made for any data already in Alluxio.

Note that when this property is enabled, it is possible that the same data blocks are available across multiple workers, reducing the amount of available storage capacity for unique data. Depending on the size of the working set and Alluxio capacity, disabling passive caching can help workloads that have no concept of locality and whose dataset is large relative to Alluxio capacity. Alluxio 2.0 will support fine-grained control on data replications, e.g., setting the minimal and maximal number of copies of a given file in Alluxio.

If you have more suggestions on performance tuning Spark on Alluxio, you are welcome to share them on our mailing list ([link](https://groups.google.com/forum/#!forum/alluxio-users)).