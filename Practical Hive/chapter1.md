# 简介
When I first learned about Hive I was working as a consultant on two data warehousing projects. One of them was in its sixth month of development. We had a team of 12 consultants and we were showing little progress. The <u>source</u> database was relational but, for some unknown reason, all the constraints such as primary and foreign key references had been turned off. **For all intents and purposes**, the source was nonrelational and the team was struggling with moving the data into our highly structured data warehouse. We struggled with NULL values and building constraints as well as master data management issues and data quality. The goal at the end of the project was to have a data warehouse that would reproduce reports they already had.

当我第一次了解Hive时，我正在两个数据仓库项目中担任顾问。其中一个是12人的顾问团队，已经开发了六个月，但几乎没什么进展。数据库是关系型的，但由于一些未知原因，关闭了所有的约束，如主键和外键引用。真实原因是，**数据**源是非关系型的，团队正在努力将数据迁移到高度结构化的数据仓库中，努力处理NULL值，构建约束，解决主数据管理的问题以及保证数据质量。在项目结束时，我们的目标是新的数据仓库可以产生以前的报表。

The second project was smaller but involved hierarchical relationships. For example, a TV has a brand name, a SKU, a product code, and any number of **other descriptive features**. Some of these features are dynamic while others apply to one or more different products or brands. The hierarchy of features would be different from one brand to another. Again we were struggling with representing this business requirement in a relational data warehouse. 

第二个项目较小，但涉及层次关系。例如，电视机有品牌名称，SKU，产品代码以及任意数量的其他分类特征。有些特征是动态的，而有些特征字段则适用于一个或多个不同的产品或品牌。在不同的品牌之间，特征层次各不相同。同样，我们努力在关系型数据仓库中表示这种业务需求。

The first project represented the difficulty in moving from one schema to another. **This problem had to be solved before anyone could ask any questions and, even then the questions had to be known ahead of time.** The second project showed the difficulty in expressing business rules that did not fit into a rigid data structure. **We found ourselves** telling the customer to change their business rules to fit the structure. 

第一个项目代表了从一个模式迁移到另一个模式的困难。必须在有人提出任何疑问之前解决，即便如此，也应该事先知晓这个困难。第二个项目表现出的困难是，业务规则不能用严格的数据结构表达。只好告诉客户更改业务规则以适应数据结构。

When I first copied a file into HDFS and created a Hive table on top of the file, I was **blown away** by the simplicity of the solution yet by the far-reaching impact it would have on data analytics. Since that first simple beginning, I have seen data projects using Hive go from design to real analytic value built in weeks, which would take months with traditional approaches. Hive and the greater Hadoop ecosystem is truly a game-changer for data driven companies and for companies who need answers to critical business questions.

当我第一次拷贝文件进HDFS，基于文件创建Hive表后，便被彻底震惊了，解决方案如此简洁，却对数据分析带来深远的影响。这次试水之后，我看到使用Hive的数据项目在几周之内便能完成设计，并产生出真正有价值的分析，而使用传统的方法则需要耗时几月。对于数据驱动和需要解决关键业务问题的公司，Hive和更大的Hadoop生态系统确实是游戏规则的改变者。

The purpose of this book is the hope that it will provide to you the same “ah-ha” moment I experienced. The purpose is to give you the foundation to explore and experience what Hive and Hadoop have to offer and to help you begin your journey into the technology that will drive innovation for the next decade or more. To survive in the technology field, you must constantly reinvent yourself. Technology is constantly travelling forward. Right now there is a train departing; welcome aboard.

希望通过阅读你也能同样体会到我经历过的“顿悟”时刻，这是本书的目的之一。另外，也为你提供探索和体验Hive和Hadoop所需的基础知识，帮助你进入Hive学习之旅，它会在未来十年或更长的时间内推动技术创新。技术不断前进，要在技术领域生存下去，你必须不断重塑自己，现在我们要出发了，欢迎加入。

# 第一章  为Hive做准备: Hadoop

By now, any technical specialist with even a sliver of curiosity has heard the term Hadoop tossed around at the water cooler. The discussion likely ranges from, “Hadoop is a waste-of-time,” to “This is big. This will solve all our current problems.” You may also have heard your company director, manager, or even CIO ask the team to begin implementing this new Big Data thing and to somehow identify a problem it is meant to solve. One of the first responses I usually get from non-technical folks when mentioning Big Data is, “Oh, you mean like the NSA”? It is true that with **Big Data comes big responsibility**, but clearly, a lack of knowledge about the uses and benefits of Big Data can breed unnecessary FUD (fear, uncertainty, and doubt).

现在，任何一个有一丝好奇心的技术专家，都已经在饮水机边的闲聊中听到了Hadoop这个术语。闲谈的内容可能从“Hadoop是浪费时间”到“Hadoop很**牛**，将解决我们目前的所有问题”。你可能听说过公司的主管，经理，甚至CIO要求团队开始**部署大数据平台**这个新东西，并以某种方式确定它应该要解决的问题。向非技术人员提到大数据时，第一个答复通常是“哦，你是说**大数据**像NSA（国家安全局）”？事实上，大数据带来大责任，但显然，缺乏关于如何使用大数据，及其带来好处的知识会滋生不必要的FUD（恐惧、不确定性和怀疑）

The fact you have this book in your hands shows you are interested in Hadoop. You may also **know already** how Hadoop allows you to store and process large quantities of data. We are guessing that you also realize that Hive is a powerful tool that allows familiar access to the data through SQL. As you may glean from its title, this book is about Apache Hive and how Hive is essential in gaining access to large data stores. With that in mind, it helps to understand **why we are here**. Why do we need Hive when we already have tools like T-SQL, PL/SQL, and any number of other analytical tools capable of retrieving data? Aren’t there additional resource costs to adding more tools that demand new skills to an existing environment? The fact of the matter is, the nature of what we consider usable data is changing, and changing rapidly. This fastpaced change is forcing our hand and making us expand our toolsets beyond those we have relied on for the past 30 years. Ultimately, as we’ll see in later chapters, we do need to change, but we also need to leverage the effort and skills we have already acquired.

事实上，阅读本书表示你对Hadoop感兴趣。你可能已经知道Hadoop如何存储和处理大量数据。我们猜测你还意识到，Hive是一个强大的工具，可以通过你熟悉的SQL访问数据。正如书名所提示，这是一本关于Apache Hive，以及Hive对于访问大型数据存储有多重要的书。认识到这一点，有助于理解<u>为什么我们在这里</u>。既然我们已经有了T-SQL，PL / SQL，以及任何其他能够检索数据的分析工具，为什么还需要Hive？在现有环境中添加更多需要新技能的工具，是否还有额外的资源成本？真实的情况是，我们认为可用数据的性质正在发生变化，并且是快速变化。过去30年我们所依赖的工具不能**应对**这种快速变化，迫使我们扩展工具集。我们将在后面的章节中看到，从根本上说，我们确实需要改变，但也需要利用已经**完成的**工作和获得的技能。

Synonymous with Hadoop is the term Big Data . In our opinion, the term Big Data is slowly moving toward the fate of other terms like Decision Support System (DSS) or e-commerce. When people mention “Big Data” as a solution, they are usually viewing the problem from a marketing perspective, not from a tools or capability perspective. I recalled a meeting with a high-level executive who insisted we not use the term Big Data at all in our discussions. I agreed with him because I felt such a term dilutes the conversation by focusing on generic terminology instead of the truly transformative nature of the technology. But then again, the data really is getting big, and we have to start somewhere. 

Hadoop的同义词是大数据。在我们看来，大数据这个术语的命运正缓慢向决策支持系统（DSS）或电子商务等其他术语看齐。当人们提及用“大数据”作为解决方案时，通常是从营销角度，而非工具或能力角度来看待问题。我回想起和某位高级管理人员的一次会面，会谈中，他坚持不用“大数据”这个词。我同意他这么做，因为“大数据”只是个通用术语，没有**反应出**技术上的真正变革，用这个词会淡化我们之间的谈话。然而，数据真的越来越大，我们必须从某个地方开始。

My point is that Hadoop, as we’ll see, is a technology originally created to solve specific problems. It is evolving, **faster than fruit flies in a jar**, into a core technology that is changing the way companies think about their data—how they make use of and gain important insight into all of it—to solve specific business needs and gain a competitive advantage. Existing models and methodologies of handling data are being challenged. As it evolves and grows in acceptance, Hadoop is changing from a niche solution to something from which every enterprise can extract value. Think of it in the way other, now everyday technologies were created from specialized needs, such as those found in the military. Items we take for granted like duct tape and GPS were each developed first for specific military needs. Why did this happen? Innovation requires at least three ingredients: an immediate need, an identifiable problem, and money. The military is a huge, complex organization that has the talent, the money, the resources, and the need to invent these everyday items. Obviously, products the military invents for its own use are not often the same as those that end up in your retail store. The products get modified, generalized, and refined for everyday use. As we dig deeper into Hadoop, **watch for the same process of these unique and tightly focused inventions evolving to meet the broader needs of the enterprise.** 

我们将看到Hadoop最初是为了解决一个具体问题而创建的技术，这也是我的观点。它的演变速度比罐子里果蝇的繁殖速度还快，逐渐成为核心技术，正在改变企业对数据的看法—如何利用数据，并从中获得重要的洞察力— 从而能解决具体的业务需求并获得竞争优势。现有处理数据的模型和方法正在受到挑战。随着Hadoop的不断发展和成长，它正在从一个小的解决方案，转变为每个企业都可以从中提取价值。换一种方式思考，现在的日常技术也是根据特殊的需求而创造出来，比如军方**发明**的那些技术。像胶带和GPS那些我们认为理所当然的物品，首先是为了具体的军事需求而开发出来。为什么会发生这种情况？ 创新至少需要三个要素：即时需求、可识别的问题以及钱。军队是一个庞大而复杂的组织，有人才，金钱，资源，和发明这些日常用品的需要。显然，军方发明出来为自己使用的产品，通常与零售商店里的最终产品不相同。产品要经过修改，泛化和完善才能在日常中使用。 随着我们深入挖掘Hadoop，为了满足企业更广泛的需求，<u>请注意这个相同的过程，独特且紧密聚焦于发明创造的演进之路</u>。

If Hadoop and Big Data are anything, they are a journey. Few companies come out of the gate requesting a 1,000-node cluster and **decide over happy hour** to run critical processes on the platform.  Enterprises go through a predictable journey that can take anywhere from months to years. As you read through this book, the expectation is that it will help begin your journey and help elucidate particular steps in the overall journey. This first chapter is an introduction into why this Hadoop world is different and **where it all started**. This first chapter gives you a foundation for the later discussions. You will understand the platform before the individual technology and you will also learn about why the open source model is so different and disruptive.

如果问Hadoop和大数据是什么，那么它们是一段旅程。很少有公司一开始就需要一个有1000个节点的集群，并且能轻松决定在其上运行关键过程。企业将经历一段可预见的旅程，需要耗时几个月到几年的时间。阅读本书，期望它有助于阐明整个旅程中的某些步骤，助你开启自己的Hadoop之旅。第一章介绍为什么Hadoop的世界与众不同，它为什么会出现，本章也是后面讨论的基础。你将在学习单项技术之前先了解Hadoop平台，还将了解开源模型为什么如此不同且那么具有颠覆性。

## 大象出生

In 2003 Google published an inconspicuous paper titled “The Google Filesystem” ( http://static.googleusercontent.com/media/research.google.com/en/us/archive/gfs-sosp2003.pdf ). Not many outside of Silicon Valley paid much attention to its publication or the message it was trying to convey. The message it told was directly applicable to a company like Google, whose primary business focused on indexing the Internet, which was not a common use case for most companies. The paper described a storage framework uniquely designed to handling the current future technological demands Google envisioned for its business. In the spirit of TL&DR, here are its most salient points:

- Failures are the norm
- Files are large
- Files are changed by appending, not by updating
- Closely coupled application and filesystem APIs

2003年，Google发表了一篇不起眼的论文，名为“谷歌文件系统”（http://static.googleusercontent.com/media/research.google.com/en/us/archive/gfs-sosp2003.pdf）。硅谷以外很少有人关注到它的发表，以及它试图传达的信息。它所传达的信息直接适用于像Google这样的公司，其主要业务集中在对互联网进行索引，对大多数公司来说并不常见。论文描述了一个独特设计的存储框架，用于处理Google为其业务设想的当前以及未来的技术需求。长话短说，下面几点最突出：

- 故障是常态
- 文件很大
- 通过追加修改文件，不能就地修改
- 应用和文件API紧密耦合

If you were a planning to become a multi-billion dollar Internet search company, many of these assumptions made sense. You would be primarily concerned with handling large files and executing long sequential reads and writes **<u>at the cost of low latency</u>**. You would also be interested in distributing your gigantic storage requirements across commodity hardware instead of building a vertical tower of expensive resources. Data ingestion was of primary concern and structuring (schematizing) this data on write would only delay the process. You also had at your disposal a team of world-class developers to architect the scalable, distributed, and highly available solution.

如果你打算成为一个数十亿美元的互联网搜索公司，很多假设都有意义。你主要关心处理大文件并以<u>低延迟为代价执行长时间的顺序读写操作</u>。你也会有兴趣用商用硬件来承担巨大的存储需求，而不是建立一个昂贵的资源垂直塔。首要关注于获取数据，在写入的时候结构化（系统化）这些数据只会延缓导入这个过程。你还需要拥有世界级开发人员团队来构建可扩展，分布式和高可用的解决方案。

> **译注 **  原文at the cost of low latency，我认为应该是high latency.

One company who took notice was Yahoo. They were experiencing similar scalability problems along Internet searching and were using an application called Nutch created by Doug Cutting and Mike Caffarella. The whitepaper provided Doug and Mike a framework for solving many problems inherent in the Nutch architecture, most importantly scalability and reliability. What needed to be accomplished next was a reengineering of the solution based on the whitepaper designs.

Yahoo注意到了这点，他们正在使用Nutch，由Doug Cutting和Mike Caffarella创建，在搜索互联网时，Nutch恰好遇到了类似的可扩展性问题。Google的白皮书为Doug和Mike提供了一个框架，用于解决Nutch架构中许多固有的问题，最重要的是可扩展性和可靠性。接下来需要完成的便是基于白皮书的设计再造**Nutch**。

> **Note** Keep in mind the original GFS (Google Filesystem) is not the same as what has become Hadoop. GFS was a framework while Hadoop become the translation of the framework put into action. GFS within Google remained proprietary, i.e., not open source.

> **注** 请记住原始的GFS（Google文件系统）和Hadoop不一样。GFS是一个框架，而Hadoop是这个框架的实现。Google内的GFS仍然是专有的，即，没有开源。

When we think of Hadoop, we usually think of the storage portion that Google encapsulated in the GFS whitepaper. In fact, the other half of the equation and, arguably more important, was a paper Google published in 2004 titled “MapReduce: Simplified Data Processing on Large Clusters” ( http://static.googleusercontent.com/media/research.google.com/en/us/archive/mapreduce-osdi04.pdf ). The MapReduce paper married the storage of data on a large, distributed cluster with the processing of that same data in what is called an “embarrassingly parallel” method.

说起Hadoop，通常指的是其存储部分，描述于Google的GFS白皮书里。事实上，Google于2004年发表的论文，题为“MapReduce：简化的大型集群数据处理”（ http://static.googleusercontent.com/media/research.google.com/en/us/archive/mapreduce-osdi04.pdf ）是等式的另一半，可以说更重要。结合大型分布式存储集群，MapReduce以所谓“简单并行”的方法处理这些数据。

> **Note** We’ll discuss MapReduce (MR) throughout this book. MR plays both a significant role as well as an increasingly diminishing role in interactive SQL query processing.

> **注** 我们将在本书中讨论MapReduce（MR）。在交互式SQL查询处理中，MR扮演着重要的角色，不过作用越来越小。

Doug Cutting, as well as others at Yahoo, saw the value of GFS and MapReduce for their own use cases at Yahoo and so spun off a separate project from Nutch. Doug named the project after the name of his son’s stuffed elephant, Hadoop. Despite the cute name, the project was serious business and Yahoo set to scale it out to handle the demands of its search engine as well as its advertising.

Doug Cutting以及Yahoo的其他人都看到了GFS和MapReduce在Yahoo自己场景中的价值，因此从Nutch分离出一个独立的项目。Doug以他儿子玩具大象的名字命名这个项目。名字尽管可爱，但这确实是个正经的项目，Yahoo计划扩大规模，以满足搜索引擎和广告业务的需求。

> **Note** There is an ongoing joke in the Hadoop community that when you leave product naming to engineering and not marketing you get names like Hadoop, Pig, Hive, Storm, Zookeeper, and Kafka. **<u>I, for one, love the nuisance and silliness of what is at heart applications solving complex and real-world problems</u>**. As far as the fate of Hadoop the elephant, Doug still carries him around to speaking events.

> **注** Hadoop社区中一直存在一个笑话，当你为工程而不是营销命名产品时，你将获得Hadoop，Pig，Hive，Storm，Zookeeper和Kafka这样的名称。~~解决复杂和实际问题的应用。~~至于那个Hadoop**玩具**大象，Doug 还带着它四处参加演讲活动。
>

Yahoo’s internal Hadoop growth is atypical in size but typical of the pattern of many current implementations. In the case of Yahoo, the initial development was able to scale to only a few nodes but after a few years they were able to scale to hundreds. As clusters grow and scale and begin ingesting more and more corporate data, silos within the organization begin to break down and users begin seeing more value in the data. As these silos break down across functional areas, more data moves into the cluster. What begins with hopeful purpose soon becomes the heart and soul or, more appropriately, the storage and analytical engine of an entire organization. As one author mentions: 

Yahoo内部Hadoop集群数量的增长并不典型，但却是如今典型的实现模式。就Yahoo而言，刚开发时仅能扩展到几个节点，但是几年后，就扩展到数百个节点。随着集群的发展和规模的扩大，开始吸收越来越多的企业数据，组织内的**数据孤岛**开始分解，用户开始在数据中看到更多的价值。随着这些孤岛在各个功能领域分解，更多的数据进入集群。从一个有希望的目的开始，很快就变成了核心工具，或者更恰当地说，变成了整个组织的存储和分析引擎。正如一位作者提到：

> *By the time Yahoo spun out Hortonworks into a separate, Hadoop-focused software company in 2011, Yahoo’s Hadoop infrastructure consisted of 42,000 nodes and hundreds of petabytes of storage ( http://gigaom.com/2013/03/04/the-history-of-hadoop-from-4-nodes-to-the-future-of-data/ ).*

> *2011年，当Yahoo将Hortonworks剥离成一个独立且专注于Hadoop的软件公司时，Yahoo的Hadoop基础设施包括42,000个节点和数百P的存储容量 ( http://gigaom.com/2013/03/04/the-history-of-hadoop-from-4-nodes-to-the-future-of-data/ )。*

## Hadoop 技术

Hadoop is a general term for two components: storage and processing. The storage component is the Hadoop Distributed File System (HDFS) and the processing is MapReduce.

Hadoop是存储和处理这两个组件的总称。 存储组件是Hadoop的分布式文件系统（HDFS），处理组件是MapReduce。

> **Note**  The environment is changing as this is written. MapReduce has now become only one means of processing Hive on HDFS. MR is a traditional batch-orientated processing framework. New processing engines such as Tez are geared more toward near real-time query access. With the advent of YARN, HDFS is becoming more and more a multitenant environment allowing for many data access patterns such as batch, real-time, and interactive.

> **注** 写这本书的时候，情况正在变化。MapReduce现在已经成为HDFS上处理Hive的一种手段。MR是传统的批处理框架。新的处理引擎（如Tez）更适合近实时查询访问。随着YARN的到来，HDFS正在逐渐变成多租户环境，允许多种数据访问模式，如批量，实时，或是交互式查询。

When we consider normal filesystems we think of operating systems like Windows or Linux. Those operating systems are installed on a single computer running essential applications. Now what would happen if we took 50 computers and networked them together? We still have 50 different operating systems and this doesn’t do us much good if we want to run a single application that uses the compute power and resources of all of them.

当我们考虑正常的文件系统时，想到的是如Windows或Linux这样的操作系统。这些操作系统安装在单机上，并运行一些关键应用。现在，如果我们把50台电脑联网，会发生什么？我们还有50个不同的操作系统，如果想运行一个应用程序，可以使用所有的计算能力和资源，这样的联网对我们没有多大好处。

For example, I am typing this on Microsoft Word, which can only be installed and run on a single operating system and a single computer. If I want to increase the operational performance of my Word application I have no choice but to add CPU and RAM to my computer. The problem is I am limited to the amount of RAM and CPU I can add. I would quickly hit a physical limitation for a single device. 

例如，我正在微软 Word中打字，Word 只能安装和运行在单机上。如果想提高Word程序的运行性能，只能选择在单机上添加CPU和内存。问题是受限于可以添加的RAM和CPU的数量，很快就会达到单个设备的物理上限。

HDFS, on the other hand, does something unique. You take 50 computers and install an OS on each of them. After networking them together you install HDFS on all them and declare one of the computers a master node and all the other computers worker nodes. This makes up your HDFS cluster. Now when you copy files to a directory, HDFS automatically stores parts of your file on multiple nodes in the cluster. HDFS becomes a virtual filesystem on top of the Linux filesystem. HDFS abstracts away the fact you’re storing data on multiple nodes in a cluster. Figure 1-1 shows a high level view of how HDFS abstracts multiple systems away from the client. 

另一方面，HDFS的做法不一样。你有50台电脑，每台电脑上都安装了操作系统。将它们联网后，在所有的计算机上安装HDFS，将其中一台计算机声明为主节点，并将其他所有计算机声明为工作节点，这就建好了HDFS集群。现在，往某个目录复制文件，HDFS会自动将文件的各个部分存储到集群中的多个节点上。 HDFS成为Linux文件系统之上的虚拟文件系统，将数据存储在群集中的多个节点上的事实抽像出来。图1-1显示了HDFS如何将多个系统从客户端中抽离出来的高级视图。

Figure 1-1 is simplistic to say the least (we will elaborate on this in the section titled “Hadoop High Availability”). The salient point to take away is the ability to grow is now horizontal instead of vertical. Instead of adding CPU or RAM to a single device, you simply need to add a device, i.e., a node. Linear scalability allows you to quickly expand your capabilities based on your expanding resource needs. The perceptive reader will quickly counter that similar advantages are gained through virtualization. Let’s take a look at the same figure through **virtual goggles**. Figure 1-2 shows this virtual architecture.

图1-1是简化的架构概要图（在题为“Hadoop高可用性”这一小节详细阐述HDFS架构）。要抓住的要点是，现在是水平扩展而不是垂直扩展，提升性能只需要向集群添加一个设备，即一个节点，而不是在单个设备上添加CPU或RAM。线性扩展允许你根据不断扩大的资源需求快速扩展容量。敏锐的读者马上会反驳，虚拟化也有类似的优势。让我们通过**虚拟护目镜**来看看同一幅图。图1-2显示了虚拟化的架构。

[图1-1]

Administrators install virtual management software on a server or, in most cases, a cluster of servers. The software pools resources such as CPU and memory so that it looks as if there is a single server with a large amount of resources. On top of the virtual OS layer we had guests and divide the available pool of resources to each guest. The benefits include maximization of IO resources, dynamic provisioning of resources, and high availability at the physical cluster layer. Some problems include a dependency on SAN storage, inability to scale horizontally, as well as limitations to vertical scaling and reliance on multiple OS installations. Most current data centers follow this pattern and virtualization has been the primary IT trend for the past decade.

管理员在服务器上安装虚拟管理软件，或者在大多数情况下安装一组服务器。该软件可以将资源（如CPU和内存）进行汇集，以使其看起来像一个拥有大量资源的单一服务器。客户机在虚拟OS层之上，每台客户机都会从可用的资源池里分配资源。优点包括最大化利用IO资源，动态配置资源以及物理集群层高可用。缺点包括依赖SAN存储，无法水平扩展，以及局限于垂直缩放和依赖于多个OS。当前大多数数据中心遵循这种模式，虚拟化是过去十年IT的主要趋势。

>**Note** Figure 1 -2 uses the term ESX. We certainly don’t intend to pick on VMWare. We show the virtualization architecture only to demonstrate how Hadoop fundamentally changes the data center paradigm for unique modern data needs. Private cloud virtualization is a still a viable technology for many use cases and should be considered in conjunction with other architectures like appliances or public cloud.

> **注** 图1-2使用术语“ESX”。 我们当然不打算选择VMWare。 展示虚拟化架构只是为了演示Hadoop如何从根本上改变数据中心的范式，以满足当前独特的数据需求。 许多场景下，私有云虚拟化仍是一项可行的技术，应与其他架构（如家用电器或公共云）结合考虑。

[图1-2]

Other advantages include reduced power consumption and reduced physical server footprint and dynamic provisioning. Hadoop has the unenviable task of going against a decade-long trend in virtual architecture. Enterprises have for years been moving away from physical architecture and making significant headway in diminishing the amount of physical servers they support in their data center. If Hadoop only provided the ability to add another physical node when needed to expand a filesystem, we would not be writing this book and **Hadoop would go the way of Pets.com**.  There’s much more to the architecture to make it transformative to businesses and worth the investment in a physical architecture.

其他优点包括降低功耗，减少物理服务器占用空间和动态配置。Hadoop有个艰难的任务，即反转长达十年的虚拟化架构的趋势。多年来，企业一直在摆脱物理架构，并在减少数据中心支持的物理服务器数量方面取得了重大进展。如果Hadoop只提供了在需要扩展文件系统容量时添加另一个物理节点的能力，那就没必要写这么一本书，而Hadoop将会进入Pets.com（变成宠物）。这个架构还有更多的内容，对业务来说具有变革性，使其成为值得投资的物理架构。

## 数据冗余

Data at scale must also be highly available. Hadoop stores data efficiently and cheaply. There are mechanisms built into the Hadoop software architecture that allow us to use inexpensive hardware. As stated in the GFS whitepaper, the original design assumed nodes would fail. As clusters expand horizontally into the 100s, 1,000s, or even 10s of thousands, we are left with no option but to assume at least a few servers in the cluster will fail at any given time.

成规模的数据也必须高可用。Hadoop高效廉价地存储数据。 其软件架构中内建的机制，使得我们能使用廉价的硬件。正如GFS白皮书所述，原始设计假设节点将失效。随着集群水平扩展到上百，上千，甚至上万台，我们别无选择，只能假定集群中至少有几个服务器在任意给定的时间都会失效。

To have a few server failures jeopardize the health and integrity of the entire cluster would defeat any other benefits provided by HDFS, not to mention the Hadoop administrator turnover rate due to lack of sleep. Google and Yahoo engineers faced the daunting task of reducing cost while increasing uptime. **The current HA solutions available were not capable of scaling out to their needs without burying the companies in hardware, software, and maintenance costs**. Something had to change in order to meet their demands. Hadoop became the answer but first we need to look at why existing tools were not the solution.

如果几台服务器故障就危及整个集群的健康和完整性，HDFS的其他优势便一无是处，更别说由于缺乏睡眠，导致Hadoop管理员的高流失率。Google和Yahoo的工程师任务艰巨，降低成本的同时，还要延长正常的运行时间。在不增加公司硬件，软件和维护成本的情况下，目前的HA解决方案无法水平扩展满足需求。为了达到目的，必须做出改变。 Hadoop成为答案，但我们首先需要看看现有的工具为何不行。

### 传统的高可用

When we normally think of redundancy, we think in terms of high availability (HA). **HA is an architecture describing how often you have access to your environment**. We normally measure HA in terms of nines. We might say our uptime is 99.999, or five nines. Table 1-1 shows the actual downtime expected based on the HA percentage ( http://en.wikipedia.org/wiki/High_availability ).

我们通常从高可用（HA）的角度考虑冗余。HA是一种架构，描述系统的正常工作时间。通常以几个9的方式测量HA。 我们可能会说正常运行时间是99.999，或者是五个九。 根据HA百分值，表1-1显示了预期的实际停机时间 ( http://en.wikipedia.org/wiki/High_availability )。

Cost is traditionally a ratio of uptime. More uptime means higher cost. The majority of HA solutions center on hardware though a few solutions are also software dependent. Most involve the concept of a set of passive systems sitting in wait to be utilized if the primary system fails. Most cluster infrastructures fit this model. You may have a primary node and any number of secondary nodes containing replicated application binaries as well as the cluster specific software. Once the primary node fails, a secondary node takes over.

成本通常是正常运行时间的比率，更多的运行时间意味着更高的成本。少数几个HA解决方案依赖于软件，但大多数都集中于硬件。几乎都引入了主动和被动系统的概念，一旦主系统出故障，则使用等待中的被动系统。大多数集群基础设施符合这种模式。你可可能拥有主节点和任何数量的辅助节点，辅助节点上备份了应用程序的二进制文件以及集群特定的软件。 一旦主节点发生故障，辅助节点就会接管。

> **Note** You can optionally set up an active/active cluster in which both systems are used. Your cost is still high since you need to account for, from a resource perspective, the chance of the applications from both systems running on one server in the event of a failure.

>**注** 您可以选择设置主/主集群，两个系统可同时使用。但是成本仍然很高，因为从资源的角度来看，你需要考虑故障时两套系统的应用程序运行在一台服务器的可能性。

Quick failover minimizes downtime and, if the application running is cluster-aware and can account for the drop in session, the end user may never realize the system has failed. Virtualization uses this model. The physical hosts are generally a cluster of three or more systems in which one system remains passive in order to take over in the event an active system fails. The virtual guests can move across systems without the client even realizing the OS has moved to a different server. This model can also help with maintenance such as applying updates, patches, or swapping out hardware. Administrators perform maintenance on the secondary system and then make the secondary the primary for maintenance on the original system. Private clouds use a similar framework and, in most cases, have an idle server in the cluster primarily used for replacing a failed cluster node. Figure 1-3 shows a typical cluster configuration.

快速容灾最小化了停机时间，如果正在运行的应用程序具有集群感知能力，并且解决了会话丢失，那么最终用户可能永远不会意识到系统出现过故障。虚拟化采用这种模式。物理主机通常是三个或更多系统的集群，其中一个是被动系统，以便在主系统出故障的情况下接管**其工作**。虚拟客户端可以跨系统移动，客户端甚至不知道操作系统已经移动到不同的服务器。该模型还有助于维护，如更新应用，打补丁，或替换硬件。管理员先在辅助系统上进行维护，然后把辅助系统升级为主系统，再在原来的主系统上进行维护。私有云使用类似的框架，大多数情况下，集群中的空闲服务器主要用于替换故障节点。典型的集群配置如图1-3所示。

[图1-3]

The cost for such a model can be high. Clusters require shared storage architecture, usually served by a SAN infrastructure. SANs can store a tremendous amount of data but they are expensive to build and maintain. SANs exist separate from the servers so data transmits across network interfaces. Furthermore, SANs intermix random IO with sequential IO, which means all IO becomes random. Finally, administrators configure most clusters to be active/passive. The passive standby server remains unused until a failure event. In this scenario hardware costs double without doubling your available resources.

这种模型成本很高。集群需要共享的存储架构，一般由SAN提供底层基础设施。SANs可以存储大量的数据，但是建立和维护成本很高。SANs与服务器分开存在，所以要跨网络传输数据。此外，SANs将混合随机IO与顺序IO，这意味着全是随机IO。最后，管理员将大多数集群配置为主动/被动，被动备用服务器直到发生故障时才启用，在这种情况下，硬件成本翻倍，但可用资源没有翻倍。

Storage vendors use a number of means to maintain storage HA or storage redundancy. The most common is the use of RAID (Redundant Array of Independent Disks) configurations. Table 1-2 shows a quick overview of the most common RAID configurations.

存储厂商采用多种手段来维护存储HA或存储冗余。 最常见的是使用RAID（独立磁盘冗余阵列）配置。 最常见的RAID配置如表1-2所示。

[表1-2]

RAID is popular due to the fact it provides data protection as well as performance enhancements for most workloads. RAID 0 for example supplies no data protection but speeds up write speed due to the increased amount of spindles. RAID, like clusters, come at a cost. In the case of mirrored RAID configuration you are setting aside a dedicated disk solely for the purpose of data recovery. Systems use the secondary disk only to replicate the data on write. This process slows down writes as well as doubling cost without doubling your storage capacity. To implement 5 TB of mirrored disk RAID, you would need to purchase 10 TB of storage. Most enterprises and hardware vendors do not implement RAID 0 or RAID 1 in server architectures.

RAID比较流行，除了提供数据保护的功能，同时在大多数工作负载下能提高性能。例如，RAID 0没有数据保护，但由于主轴数量的增加（其实就是并行写），加快了写入速度。RAID，像集群一样，成本不菲。例如，镜像配置RAID时，需要专门预留磁盘用于数据恢复，仅在写入时，系统向镜像磁盘复制写入的数据。这个过程会减慢写入速度，成本翻倍，但并不加倍存储容量。要实现5 TB的镜像磁盘RAID，需要购买10 TB的存储。大多数企业和硬件厂商并不在服务器架构中使用RAID 0或RAID 1。

Storage vendors such as EMC and NetApp configure their SAN environments with RAID 1+0 (RAID “ten”). This supplies the high-availability storage requirements as well as the performance capabilities. This works well for large SAN environments where arrays may consist of six or more drives and there may be dozens of arrays on the SAN. These arrays are carved up into LUNs (logical unit numbers) and presented to servers for use. These then become your mount points or your standard Windows drive letters.

EMC和NetApp等存储厂商通过RAID 1 + 0（RAID“10”）配置其SAN环境。这既能满足高可用的存储需求，又能提高性能。这适用于大型SAN环境，其中阵列可能包含六个或更多磁盘，SAN上可能有数十个阵列。这些阵列划分为LUN（逻辑单元号），给服务器使用。然后，这些LUN将变成（Linux下的）挂载点或Windows的标准驱动器盘符。

>**Note** Bear with me. The discussion around SANs and RAID storage may seem mundane and unimportant but understanding traditional storage design will help you understand the Hadoop storage structure. The use of SANs and RAID has been the de facto standard for the last 20 years and removing this prejudice is a major obstacle when provisioning Hadoop in data centers.

>**注** 请耐心些。围绕SANS和RAID存储的讨论似乎平淡无奇，但是了解传统的存储设计将有助于了解Hadoop存储结构。使用SAN和RAID一直是过去20年的事实标准，消除这种偏见是在数据中心配置Hadoop时的主要障碍。

So, in essence SANs are large containers holding multiple disk arrays and managed by a central console. A company purchases a server, and then the server is provisioned in the data center with minimal storage usually on a small DAS (direct attached storage) disk for the OS and connected via network links to the SAN infrastructure. Applications, whether point of sale applications or databases, request data from the SAN, which then pulls through the network for processing on the server. SANs become a monolithic storage infrastructure handing out data with little to no regard to the overarching IO processing. The added HA, licensing, and management components on SANs add significantly to the per-TB cost.

因此，本质上，SAN是容纳多个磁盘阵列并由中央控制台管理的大容器。通常，公司购买一台服务器放在数据中心内，然后为其OS配置最小的DAS（直接连接存储）磁盘，并通过网络链接到SAN基础设施。应用程序，无论是POS应用还是数据库，向SAN请求数据，然后通过网络提取数据，以便在服务器上进行处理。SANs成为一个单一的存储基础设施，分发数据时不会从整体上考虑IO处理。再加上HA、软件许可费和管理组件，显著增加了每TB的存储成本。

A lot of enhancements have been made in SAN technologies, such as faster network interconnects and memory cache, but despite all the advances the primary purpose of a SAN was never high performance. The cost per TB has dramatically dropped in the last 15 years and will continue to drop, but going out and buying a TB thumb drive is much different than purchasing a TB of SAN storage. Again, as with the virtualization example, SAN has real-world uses and is the foundation for most large enterprises. The point here is that companies need a faster, less expensive means to store and process data at scale while still maintaining stringent HA requirements.

SAN技术已经进行了许多改进，如更快的网络互连和内存缓存，但尽管有这些进步，SAN的主要目的从来不是高性能。过去15年，每TB的存储成本大幅下降，并将持续下降，但出去买个1TB的U盘远远不同于购买1TB SAN存储。与虚拟化示例一样，SAN具有现实世界的用途，是大多数大型企业的基础。关键是企业需要更快，更便宜的手段来存储和处理成规模的数据，同时仍保持严格的HA要求。

### Hadoop 的高可用

Hadoop provides an alternative framework to the traditional HA clusters or SAN-based architecture. *It does this by first assuming failure and then building the mechanisms to account for failure into the source code*. As a product Hadoop is highly available out of the box. An administrator does not have to install additional software or configure additional hardware components to make Hadoop highly available. An administrator can configure Hadoop to be more or less available, but high availability is the default. More importantly, Hadoop removes the cost to HA ratio. Hadoop is open source and HA is part of the code so, through the transitive property, there is no additional cost for implementing Hadoop as an HA solution.

Hadoop为传统的HA集群或基于SAN的架构提供了一个替代框架。它首先假设故障**是常态**，然后**从一开始**就构建机制来应对故障。作为一个产品，Hadoop本就包含了高可用，要使之高可用，管理员不必安装额外的软件或配置额外的硬件。管理员可以配置Hadoop有多可用，但缺省就是高可用的。更重要的是，Hadoop消除了HA的成本。Hadoop是开源的，HA是代码的一部分，通过传递属性，实现Hadoop的高可用解决方案不需要额外的成本。

So how does Hadoop provide HA at reduced cost? It primarily takes advantage of the fact that storage costs per terabyte have significantly dropped in the past 30 years. Much like a RAID configuration, Hadoop will duplicate data for the purpose of redundancy, by default three times the original size. This means 10 TB of data will equal 30 TB on HDFS. What this means is Hadoop takes a file, let us say a 1 TB web log file, and breaks it up into “blocks”. Hadoop distributes these blocks across the cluster. In the case of the 1 TB log file, Hadoop will distribute the file using 24576 blocks (8192x3) if the block size is 128 MB. Figure 1-4 shows how a single file is broken and stored on a three-node cluster.

那么Hadoop如何以更低的成本提供HA呢？它主要是利用了每TB存储成本在过去30年显著下降的事实。与RAID配置非常相似，Hadoop将以冗余为目的复制数据，默认为原始大小的三倍。这意味着HDFS上的10 TB的数据将等于30 TB。比如，Hadoop保存一个文件，假设是一个1 TB的Web日志文件，会将其分解成“块”，并在集群中分发这些块。在1TB日志文件这个例子中，如果块大小为128 MB，则Hadoop将分发24576块（8192x3）。图1-4显示了如何拆分单个文件，并将其存储在一个三节点集群上。

[图1-4]

Based on the configuration settings, these blocks can range between 128 MB and 256 MB!

根据配置，块大小可介于128 MB和256 MB之间！

> **Note**  These are exceptionally large block sizes for a filesystem. As a reference point, the largest Windows block size, i.e. the largest size that can be read from disk into memory, is 4K. This is also the standard for most Linux-based OSs.

> **注**  对文件系统来说，这样的块太大了。作为参考，Windows下块的最大尺寸，即从磁盘读取到内存的最大的块，是4K。这也是大多数基于Linux操作系统的标准。

Large block sizes influence much of Hadoop’s architecture. Large blocks sizes are core to how Hadoop is deployed, managed, and provisioned. Take into consideration the following factors influenced by large block sizes:

- Large files are more efficiently processed than smaller files
- There are fewer memory requirements on the master server (this will be discussed in the next section)
- Leads to more efficient sequential read and writes
- The seek rate is reduced as a percentage of transfer time

大的块尺寸影响了Hadoop的体系结构，是Hadoop部署，管理和配置的关键，有下面几个影响：

- 可更有效地处理大文件，而非小文件。
- 对Master的内存要求较少（在下一节讨论）
- 产生更高效的顺序读写
- 寻道率在数据传输时间上的占比减少了

For the large file processing, let us go back to the 1 TB log file. Since the block size is set at 128 MB we get 24576 blocks sent over the network and written to the nodes. If the block size was 4K, the number of blocks would jump to 805306368 (268435456 x 3). As we will discuss later, this number of blocks would place undue memory pressure on specific portions of the cluster. The larger block size also optimizes the system for sequential reads and writes, which works best when considering dedicated drive access. A drive is simply a disk with a needle (aperture arm) moving across the surface (platter) to where the data is located. Storage makes no guarantee that data blocks will be stored next to each other on the platter so it takes time for the aperture arm to move randomly around the platter to get to the data. If the data is stored in large chunks or in sequential order, as is the case for most database transaction log files, then reading and writing becomes more efficient. The aperture only needs to move from point A to point B and not skip around searching for the data. Hadoop takes advantage of this sequential access by storing data as large blocks. ......

对于大文件处理，让我们回到1 TB的日志文件。由于块大小设置为128 MB，因此将通过网络发送**24576**个块，并将其写入节点。如果块大小为4K，则块数将攀升到**805306368**（268435456×3）。正如我们稍后会讨论到，这种数量的块数将对集群的<u>特定部分</u>产生不适当的内存压力。较大的块大小也优化了系统的顺序读取和写入，这在访问专用驱动器时效果最好。驱动器仅仅是一个磁盘，~~针~~（~~孔径臂~~）穿过表面（盘片）移到数据所在的位置。存储不保证数据块将彼此相邻地存储在盘片上，所以~~光圈臂~~需要时间在盘片上随机移动到以获取数据。就像大多数数据库事务日志一样，如果数据以大块或按顺序存储，读写就会变得更有效。这时，~~光圈臂~~只需要从A点移动到B点，而不必为了搜索数据而跳来跳去。Hadoop将数据分成大块存储，从而利用了顺序访问的优点。