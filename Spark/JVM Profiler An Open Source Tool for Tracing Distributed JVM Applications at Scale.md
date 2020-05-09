# JVM Profiler: An Open Source Tool for Tracing Distributed JVM Applications at Scale

Computing frameworks like [Apache Spark](https://spark.apache.org/) have been widely adopted to build large-scale data applications. For Uber, data is at the heart of strategic decision-making and product development. To help us better leverage this data, we manage massive deployments of Spark across our global engineering offices.  

While Spark makes data technology more accessible, right-sizing the resources allocated to Spark applications and optimizing the operational efficiency of our data infrastructure requires more fine-grained insights about these systems, namely their resource usage patterns.

To mine these patterns without changing user code, we built and open sourced [JVM Profiler](https://github.com/uber-common/jvm-profiler), a distributed profiler to collect performance and resource usage metrics and serve them for further analysis. Though built for Spark, its generic implementation makes it applicable to any Java virtual machine (JVM)-based service or application. Read on to learn how Uber uses this tool to profile our Spark applications, as well as how to use it for your own systems.

>Apache Spark 等计算框架已经被广泛应用于构建大规模数据应用程序。对于Uber来说，数据是战略决策和产品开发的核心，为了更好地利用这些数据，在我们全球工程办公室大规模部署了 Spark 集群。
>
>虽然 Spark 使数据技术更易使用，但调整分配给 Spark 应用程序的资源，优化数据基础架构的操作效率，需要对这些系统（即它们的资源使用模式）有更细粒度的了解。
>
>为了在不更改用户代码的情况下挖掘这些模式，我们构建并开源了JVM Profiler，这是一个分布式 Profiler，用于收集性能和资源使用量度指标，并为进一步的分析提供服务。尽管是为Spark 构建它，但其通用实现使其可适用于任何基于Java 虚拟机（JVM）的服务或应用程序。请继续阅读，了解Uber如何使用此工具来分析我们的 Spark 应用程序，以及如何将其集成到你自己的系统。

### Profiling challenges

On a daily basis, Uber supports tens of thousands of applications running across thousands of machines. As our tech stack grew, we quickly realized that our existing performance profiling and optimization solution would not be able to meet our needs. In particular, we wanted the ability to:

> Uber 每天都支持在数千台机器上运行的数以万计的应用程序。 随着技术堆栈的增长，我们很快意识到我们现有的性能分析和优化解决方案将无法满足我们的需求。 特别是，我们希望能够：

#### Correlate metrics across a large number of processes at the application level

> 在应用程序级别关联大量进程的指标

In a distributed environment, multiple Spark applications run on the same server and each Spark application has a large number of processes (e.g. thousands of executors) running across many servers, as illustrated in Figure 1:

> 分布式环境下，多个 Spark 程序运行在同一台服务器上，而每个 Spark 应用亦有大量 executor 运行在多个服务器上，如图1所示：

[![img](http://1fykyq3mdn5r21tpna3wkdyi-wpengine.netdna-ssl.com/wp-content/uploads/2018/06/image2-3.png)](http://1fykyq3mdn5r21tpna3wkdyi-wpengine.netdna-ssl.com/wp-content/uploads/2018/06/image2-3.png)Figure 1. In a distributed environment, Spark applications run on multiple servers.

Our existing tools could only monitor server-level metrics and did not gauge metrics for individual applications. We needed a solution that could collect metrics for each process and correlate them across processes for each application. **Additionally, we do not know when these processes will launch and how long they will take**. To be able to collect metrics in this environment, the profiler needs to be launched automatically with each process.

> 现有的工具只能监视服务器级别的指标，而不能衡量单个应用程序的指标。 我们需要一个解决方案，可以收集每个进程的指标，并将每个应用多个进程之间的指标关联起来。 **此外，我们不知道这些程序何时启动以及需要多长时间**。 为了能够在此环境中收集指标，需要每个进程自动启动 Profiler。

#### Make metrics metrics collection non-intrusive for arbitrary user code

> 非侵入式的收集用户代码的指标

In their current implementations, Spark and Apache Hadoop libraries do not export performance metrics; however, there are often situations where we need to collect these metrics without changing user or framework code. For example, if we experience high latency on a Hadoop Distributed File System (HDFS) NameNode, we want to check the latency observed from each Spark application to ensure that these issues haven’t been **==replicated==**. Since NameNode client codes are embedded inside our Spark library, it is cumbersome to modify its source code to add this specific metric. To keep up with the perpetual growth of our data infrastructure, we need to be able to take the measurements of any application at any time and without making code changes. Moreover, implementing a more non-intrusive metrics collection process would enable us to dynamically inject code into Java methods during load time.

> 在当前的实现中，Spark 和 Apache Hadoop 库没有输出性能指标；但是通常情况下，我们需要在不更改用户或框架代码的情况下收集这些度量。例如，如果在 Hadoop 分布式文件系统（HDFS）NameNode 上遇到高延迟，我们想检查每个 Spark 应用程序观察到的延迟，以确保这些问题没有被**==复制==**。由于 NameNode 客户端代码嵌入到我们 Spark 库中，因此修改其源代码以添加此特定度量是很麻烦。为了跟上我们数据基础设施的持续增长，需要能够在任何时间对任何应用程序进行测量，而不更改其代码。此外，一个非侵入性的度量收集的方式，将使我们能够在加载期间动态地将代码注入Java方法。

### Introducing JVM Profiler

To address these two challenges, we built and open sourced our [JVM Profiler](https://github.com/uber-common/jvm-profiler). There are some existing open source tools, like Etsy’s [statsd-jvm-profiler](https://github.com/etsy/statsd-jvm-profiler), which could collect metrics at the individual application level, but they do not provide the capability to dynamically inject code into existing Java binary to collect metrics. Inspired by some of these tools, we built our profiler with even more capabilities, such as arbitrary Java method/argument profiling.

>为了解决这两个挑战，我们构建并开源了[JVM Profiler](https://github.com/uber-common/jvm-profiler)。 有一些现有的开源工具，例如Etsy的[statsd-jvm-profiler](https://github.com/etsy/statsd-jvm-profiler)，可以收集应用程序的指标，但不提供动态注入代码以收集指标的功能。受这些工具的启发，我们构建了有更多功能的 profiler，例如检查任意 Java 方法/参数。

#### What does the JVM Profiler do?

The JVM Profiler is composed of three key features that make it easier to collect performance and resource usage metrics, and then serve these metrics (e.g. Apache Kafka) to other systems for further analysis:

- **A java agent**: By incorporating a Java agent into our profiler, users can collect various metrics (e.g. CPU/memory usage) and stack traces for JVM processes in a distributed way.
- **Advanced profiling capabilities**: Our JVM Profiler allows us to trace arbitrary Java methods and arguments in the user code without making any actual code changes. This feature can be used to trace HDFS NameNode RPC call latency for Spark applications and identify slow method calls. It can also trace the HDFS file paths each Spark application reads or writes to identify hot files for further optimization.
- **Data analytics reporting**: At Uber, we use the profiler to report metrics to Kafka topics and Apache Hive tables, making data analytics faster and easier.

> JVM Profiler 有三个关键功能，使得收集性能和资源使用指标变得更容易，然后将这些指标提供（比如通过 Apache Kafka）给其他系统进行进一步分析：
>
> 1. **Java agent**：通过将Java agent合并到我们的 profiler 中，用户可以以分布式方式收集各种指标（例如CPU /内存使用情况）和JVM进程的堆栈跟踪。
> 2. **高级分析功能**：我们的JVM Profiler 允许我们跟踪用户代码中任意的 Java 方法和参数，而无需实际修改任意代码。此功能可用于跟踪 Spar k应用程序 HDFS NameNode RPC 调用延迟，并识别慢速方法的调用。它可以跟踪每个 Spark 应用程序读取或写入HDFS 文件的路径，以标识热文件以进行进一步优化。
> 3. **数据分析报告**：在Uber，profiler 把指标发送至 Kafka topic 和 Apache hive 表，使数据分析更快、更容易。

#### Typical use cases

Our JVM Profiler supports a variety of use cases, most notably making it possible to instrument arbitrary Java code. Using a simple configuration change, the JVM Profiler can attach to each executor in a Spark application and collect Java method runtime metrics. Below, we touch on some of these use cases:

- **Right-size executor:** We use memory metrics from the JVM Profiler to track actual memory usage for each executor so we can set the proper value for the Spark “executor-memory” argument.
- **Monitor HDFS NameNode RPC latency:** We profile methods on the class *org.apache.hadoop.hdfs.protocolPB.ClientNamenodeProtocolTranslatorPB* in a Spark application and identify long latencies on NameNode calls. We monitor more than 50 thousand Spark applications each day with several billions of such RPC calls.
- **Monitor driver dropped events:** We profile methods like *org.apache.spark.scheduler.LiveListenerBus.onDropEvent* to trace situations during which the Spark driver event queue becomes too long and drops events.
- **Trace data lineage:** We profile file path arguments on the method `org.apache.hadoop.hdfs.protocolPB.ClientNamenodeProtocolTranslatorPB.getBlockLocations` and `org.apache.hadoop.hdfs.protocolPB.ClientNamenodeProtocolTranslatorPB.addBlock` to trace what files are read and written by the Spark application.

### Implementation details and extensibility

To make implementation as seamless as possible, JVM Profiler has a very simple and extensible design. People can easily add additional profiler implementations to collect more metrics and also deploy their own custom reporters for sending metrics to different systems for data analysis.

[![img](http://1fykyq3mdn5r21tpna3wkdyi-wpengine.netdna-ssl.com/wp-content/uploads/2018/06/image7-2.png)](http://1fykyq3mdn5r21tpna3wkdyi-wpengine.netdna-ssl.com/wp-content/uploads/2018/06/image7-2.png)Figure 2. Our JVM Profiler is composed of several different profilers that measure specific metrics related to JVM usage and performance.

 

The JVM Profiler code is loaded into a Java process via a [Java agent](https://docs.oracle.com/javase/7/docs/api/java/lang/instrument/package-summary.html) argument once the process starts. It consists of three main parts:

- **Class File Transformer:** instruments Java method bytecode inside the process to profile arbitrary user code and save metrics in an internal metric buffer.
- Metric Profilers
  - **CPU/Memory Profiler:** collects CPU/Memory usage metrics via [JMX](https://docs.oracle.com/javase/tutorial/jmx/index.html) and sends them to the reporters.
  - **Method Duration Profiler:** reads method duration (latency) metrics from the metrics buffer and sends to the reporters.
  - **Method Argument Profiler:** reads method argument values from the metrics buffer and sends to the reporters.
- Reporters
  - **Console Reporter**: writes metrics in the console output.
  - **Kafka Reporter**: sends metrics to Kafka topics.

#### How to extend the JVM Profiler to send metrics via a custom reporter

Users could implement their own reporters and specify them with -javaagent option, like:

java

-javaagent:jvm-profiler-0.0.5.jar=**reporter=com.uber.profiling.reporters.CustomReporter**

### Integration with Uber’s data infrastructure

[![img](http://1fykyq3mdn5r21tpna3wkdyi-wpengine.netdna-ssl.com/wp-content/uploads/2018/06/image5-1.png)](http://1fykyq3mdn5r21tpna3wkdyi-wpengine.netdna-ssl.com/wp-content/uploads/2018/06/image5-1.png)Figure 3. Our JVM Profiler integrates with Uber’s data infrastructure system.

 

We integrated our JVM Profiler metrics with Uber’s internal data infrastructure to enable:

1. **Cluster-wide data analysis**: Metrics are first fed to Kafka and ingested to HDFS, then users query with Hive/Presto/Spark.
2. **Real-time Spark application debugging**: We use Flink to aggregate data for a single application in real time and write to our MySQL database, then users can view the metrics via a web-based interface.

### Using the JVM Profiler

Below, we provide instructions for how to use our JVM Profiler to trace a simple Java application:

First, we git clone the project:

```bash
git clone https://github.com/uber-common/jvm-profiler.git
```

The we build the project by running the following command:

```shell
mvn clean package
```

Next, we call the build result JAR file (e.g.`target/jvm-profiler-0.0.5.jar`) and run the application inside the JVM Profiler using the following command:

```shell
java -javaagent:target/jvm-profiler-0.0.5.jar=reporter=com.uber.profiling.reporters.ConsoleOutputReporter -cp target/jvm-profiler-0.0.5.jar com.uber.profiling.examples.HelloWorldApplication
```

The command runs the sample Java application and reports its performance and resource usage metrics to the output console. For example:

The profiler can also send metrics to a Kafka topic via a command like the following:

#### Use the profiler to profile the Spark application

Now, let’s walkthrough how to run the profiler with the Spark application.

Assuming we already have an HDFS cluster, we upload the JVM Profiler JAR file to our HDFS:

```bash
hdfs dfs -put target/jvm-profiler-0.0.5.jar hdfs://hdfs_url/lib/jvm-profiler-0.0.5.jar
```

Then we use the spark-submit command line to launch the Spark application with the profiler:

```shell
spark-submit --deploy-mode cluster --master yarn --conf spark.jars=hdfs://hdfs_url/lib/jvm-profiler-0.0.5.jar --conf spark.driver.extraJavaOptions=-javaagent:jvm-profiler-0.0.5.jar --conf spark.executor.extraJavaOptions=-javaagent:jvm-profiler-0.0.5.jar --class com.company.SparkJob spark_job.jar
```

#### Metric query examples

At Uber, we send our metrics to Kafka topics and program background data pipelines to automatically ingest them to Hive tables. Users can set up similar pipelines and use SQL to query profiler metrics. They can also write their own reporters to send the metrics to a SQL database like MySQL and query from there. Below is an example of a Hive table schema:



Below, we offer an example result when running a previous SQL query, which shows the memory and CPU metrics for each process for the Spark executors:

| **role** | **processUuid**                      | **maxHeapMemoryMB** | **avgProcessCpuLoad** |
| -------- | ------------------------------------ | ------------------- | --------------------- |
| executor | 6d3aa4ee-4233-4cc7-a628-657e1a87825d | 2805.255325         | 7.61E-11              |
| executor | 21f418d1-6d4f-440d-b28a-4eba1a3bb53d | 3993.969582         | 9.56E-11              |
| executor | a5da6e8c-149b-4722-8fa8-74a16baabcf8 | 3154.484474         | 8.18E-11              |
| executor | a1994e27-59bd-4cda-9de3-745ead954a27 | 2561.847374         | 8.58E-11              |

 

### Results and next steps

We applied the JVM Profiler to one of Uber’s biggest Spark applications (which uses 1,000-plus executors), and in the process, reduced the memory allocation for each executor by 2GB, going from 7GB to 5GB. For this Spark application alone, we saved 2TB of memory.

We also applied the JVM Profiler to all Hive on Spark applications inside Uber, and found some opportunities to improve memory usage efficiency. Figure 3, below, shows one result we found: around 70 percent of our applications used less than 80 percent of their allocated memory. Our findings indicated that we could allocate less memory for most of these applications and increase memory utilization by 20 percent.

[![img](http://1fykyq3mdn5r21tpna3wkdyi-wpengine.netdna-ssl.com/wp-content/uploads/2018/06/image4-1.png)](http://1fykyq3mdn5r21tpna3wkdyi-wpengine.netdna-ssl.com/wp-content/uploads/2018/06/image4-1.png)Figure 3. Our JVM Profiler identified that 70 percent of applications were using less than 80 percent of their allocated memory.

 

As we continue to grow our solution, we look forward to additional memory reduction across our JVMs.

[JVM Profiler](https://github.com/uber-common/jvm-profiler) is a self-contained open source project and we welcome other developers to use this tool and contribute (e.g. submit pull requests) as well!



## 参考

https://www.jianshu.com/p/0bbd79661080

https://www.cnblogs.com/stateis0/p/9062201.html