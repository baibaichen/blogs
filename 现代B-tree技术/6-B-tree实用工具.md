## 6. B-tree 实用工具

在数据库中，除了利用B-tree索引的各种高效事务技术和查询处理方法之外，也许正是现有的大量实用工具将B-tree与其他索引技术区别开。例如，验证数据库或某些备份介质上的B-tree索引结构，可通过以任何顺序扫描数据页一次，并在内存或磁盘上保留有限的临时信息来完成。即使是为大型数据集高效创建索引，也往往需要数年才能开发出新的索引结构。例如，很长一段时间内已经出现越来越多的高效策略，用于构建和批量加载R-tree索引[23,62]，而B-tree索引则只需要排序这一简单策略，也适用于多维度的B-tree索引[6,109]。替代索引结构的可比技术通常不是新索引技术最初建议的那部分。

索引实用工具通常是集合操作，无论是创建索引时，准备一组将要索引的数据；还是验证结构时，处理从诸如B-tree之类的索引中提取的一组索引事实。因此，索引实用工具可以使用许多传统的查询处理技术，包括查询优化（例如，是连接同一个表的两个二级索引，还是扫描表的一级索引中）、分区和并行、工作负载管理（用于准入控制和调度）、和资源管理（用于内存和临时磁盘空间，或磁盘带宽的管理）。类似地，前面已经讨论过的许多事务性技术，可用在索引实用工具中。因此，以下讨论不涉及与空间管理、分区、非日志操作，在线操作的并发更新等**相关的实现问题**；相反，下面的讨论主要聚焦于尚未涵盖的方面，但与一般索引，特别是-tree索引相关的数据库实用工具。

- 实用工具对于数据库及其应用程序的高效运行至关重要。 和任何其他索引格式相比，B-tree存在更多的技术和实现。
- 实用工具通常会影响整个数据库、表或索引，经常运行很长时间。一些系统采用查询优化和查询执行中的技术和代码。

### 6.1 索引创建

虽然有些产品最初依赖重复插入来创建索引，但通过对将要索引的数据进行排序，索引创建的性能得到了很大的提高。因此，高效创建索引的技术可以分为**快速排序**、**从排序流中的构建B-tree**、**并行创建索引**、以及**创建索引时的事务**。

从排序流构建B-tree期间，B-tree的整个“右边缘”可以始终保留甚至固定在缓冲池中，以避免在缓冲池中进行冗余搜索。类似地，在线创建索引期间，可以尽可能多地保留“大”锁[48]，并仅在响应冲突请求时释放锁。或者，如果索引创建不会修改数据库的逻辑内容，则可以完全避免事务锁，因为只修改了数据库的结构。

创建新索引时通常会保留一些可用空间，用于将来的插入和更新。叶节点内的可用空间允许插入新索引时不会分裂叶节点；分支节点内的可用空间允许下层节点分裂时，不会向上级联分裂；分配单元内的空闲页面（例如一组连续页面的节或磁盘柱面）使得在分裂时没有昂贵的搜索操作，更重要的是，这使得后续所有的**范围查询**和**索引扫描**没有昂贵的搜索操作。并非所有系统都提供控制这类空闲空间的接口，因为很难预测在未来的索引使用过程中，哪些参数值最佳，而且，也可能不会精确地遵循控制参数。例如，配置每个叶中所需的空闲空间为10%，则前缀B-trees [10]可能选择相邻叶的键值范围，这样分隔键较短，左节点包含0%到20%的空闲空间。

> ==TODO：== Fig.6.1

图6.1显示了刚刚创建完索引后，具有可变大小记录的固定大小页面。所有页面都差不多，但不一定是满的。在一定数量的页面（此处为3）之后，预留的空页面可用于将来的页面拆分。

如果索引的键值不唯一，**排序顺序**应包括足够的引用信息，以使索引项具有唯一性，如前所述，这将有助于并发控制、日志记录和恢复、以及索引项的最终删除。例如，在表中删除逻辑行时，必须删除所有索引中与该行相关的所有记录。在不唯一的二级索引中，此**排序顺序**可以有效地查找要删除的正确索引项。

如果二级索引“有太多的重复键”，即每个唯一键值都有大量引用，则各种压缩方法可用于减小索引的大小，从而降低将初始索引写入磁盘的时间，也提高了扫描索引（查询处理时）或是复制索引（执行复制或备份操作时）的速度。最传统的数据结构是把计数器和每个唯一键值的引用列表关联起来[66]。在许多情况下，与完整参考值相比，可以更紧凑地保存相邻参考之间的数值差异。适当的排序可以有效地构造此类差异列表；事实上，此类列表的构造可以建模为聚合函数，从而在外部合并排序时，可以减少写入临时存储的数据量。==也可以使用位图==。尽早根据相等的键值对即将插入的索引项进行分组，不仅能够实现压缩，从而减少排序期间的I/O，而且在合并步骤中也可以减少比较，因为在索引项分组之后，每组只需要一个代表性的键值参与合并逻辑。

> ==TODO：== Fig.6.2

图6.2（来自[46]）用三路归并说明了这一点。**带下划线的键**代表归并输入和归并输出中的组。因为值1、2和3已经通过了合并逻辑，在归并输入中删除了它们。**值2**的两个副本都标记为其输入组中的代表；在输出中（下一层归并会用到），只标记了第一个副本而没标记第二个副本。**值3**在输入中有一个副本未被标记，因此不参与到当前归并步骤的合并逻辑。在下一层归并中，值3的两个副本将不参与合并逻辑。**值4**节省的成本更多：六个副本中只有两个参与当前归并步骤的合并逻辑，而下一层归并中，六个副本中只有一个参与合并逻辑。

创建大型索引期间，另一个可能问题是需要临时空间来保存**临时有序的一组文件**。请注意，一旦合并进程**消费**了某个临时有序的文件，或是其中的一页数据，可能会立即“回收”它们。因此无论是默认还是作为一个选项，一些商业数据库系统将这些**临时有序的一组文件**，存储在**为最终索引指定**的磁盘空间中。最后的一步归并时，为正在创建的索引回收页面。如果目标空间是唯一可用的磁盘空间，那么除了将其用于保存**临时有序的文件**之外，没有其他选择，尽管这种选择的==一个明显问题是目标空间通常位于镜像磁盘或冗余的RAID磁盘上==。此外，在目标空间中进行排序可能会导致最终索引相当碎片化，因为以随机顺序从归并的输入中回收页面，。因此，顺序扫描最终索引（例如大范围查询）将导致许多磁盘查找。

> ==TODO：== Fig.6.3

图6.3说明了排序的数据源大于临时空间的情况。因此，临时有序的文件不能放在保存临时数据的标准位置， 而是将它们放在目标空间中。当归并步骤消耗临时有序文件时，必须立即释放磁盘空间，以便为归并步骤的输出创建可用空间。

有两种可能的解决方案。**首先**，归并完成时可以将临时文件页面释放到全局可用页面池，最终创建索引时，尝试从那里分配大的连续磁盘空间。但是，除非分配算法搜索连续可用空间非常有效，否则大多数分配将具有相同的小尺寸，并处于归并过程中回收的空间。**其次**，从初始的临时文件到中间步骤的临时文件之间、中间临时文件之间、以及从临时文件到最终的的索引之间，可以按较大的单元（通常是I/O单元的倍数）回收空间。例如，如果此倍数为8，那么保留不超过磁盘页面大小8倍的内存空间，以便一起延迟回收，这在创建大型索引时通常是可接受的开销。其好处是，在大型有序扫描中，顺序扫描索引或大范围扫描完整索引所需的查找次数**要少8倍**。

如果创建索引的排序操作生成的临时有序文件，保存在最终存放B-tree的空间，则从系统或介质故障中恢复时，必须非常精确地重复原始排序操作。否则，恢复可能会以不同于原始执行的**方式**放置B-tree记录，并且描述B-tree更新的后续日志记录不能用于恢复的B-tree索引。具体来说，初始临时有序文件大小、合并步骤的顺序、合并扇入、合并输入的选择等，所有这些信息要么在日志中记录，要么由创建索引的日志信息所指定，例如，授予排序操作的内存分配。因此，在恢复期间，必须为排序提供与原始执行期间相同的内存。**也必须精确地重复排序数据的扫描（比如存在异步预读），而不必排列输入页或记录，如果在不同硬件上恢复原始执行，例如在灾难性硬件故障（如洪水或火灾）之后，这可能是一个问题**。精确重复执行，可能还需要在索引创建期间禁止自适应存储器分配，即，存储器分配，初始临时有序文件大小，合并扇入都会响应索引创建期间的内存争用的波动。

不仅可以使用分组和聚合，还可以使用查询处理中的各种技术来创建索引，包括查询优化和查询执行技术。例如，创建二级索引的标准技术是扫描表；但是，如果存在两个或多个二级索引包含所有必需的列，并且一起扫描的速度比扫描表快，则查询优化器可能会选择扫描这些现有索引，并关联结果以构造新索引的记录。查询优化在物化视图时发挥的作用更大，在某些系统中，物化视图被建模为创建索引不是创建表。

对于并行创建索引，可以使用标准的并行查询执行技术，以所需的排序顺序生成将要索引的数据项。剩下的问题是并行插入到新的B-tree中。一种方法是创建多个独立的、键范围不相交的B-tree，并用单个叶到根路径把它们“缝合”在一起，并在相邻节点之间实现负载平衡。

- 高效的B-tree创建依赖于有效的排序和提供事务保证，而无需在日志中记录新的索引内容。
- 用于创建索引的命令通常有许多选项，例如关于压缩的选项、为后续更新留下多少空闲空间的选项、以及临时空间的选项（用于排序索引数据）。

### 6.2 索引删除
删除索引可能看起来相当简单，但由于各种原因，可能不是这样。例如，如果索引删除是较大事务的一部分，那么这个事务是否会阻止其他所有事务访问表，即使索引删除事务可能已中止？索引删除是否可以在线，即是否可以并发查询和更新表？再举一例，如果一个表同时具有主索引（非冗余）和一些二级索引（通过搜索键指向主索引中的记录），那么当主索引被删除时需要多少工作量？ 也许主索引仅释放分支节点，保留的叶节点变成成立堆文件，重建二级索引需要多长时间？同样，索引是否可以在线删除？

最后，索引可能非常大，更新分配信息（例如，空闲空间映射）可能需要相当长的时间。这种情况下，“即时”删除索引可能只是在适当的目录（**catalog**）记录中声明索引已**过时**。这有点类似于幻影记录，不过幻影指示符仅与其出现的记录有关，而**过时指示符**与目录记录所代表的整个索引有关。此外，幻影记录可在其创建之后很久就才被删除，但应该尽快释放索引空间，因为这可能涉及大量的存储空间。即使在释放此空间之前或过程中，系统发生崩溃，在成功重启之后，也应该快速继续该过程。需要适当的恢复日志记录，精心设计可最大限度地减少日志量，即使在尝试恢复时重复崩溃，也可确保成功。

作为目录记录中的**过时指示符**的替代，可用内存数据结构表示延迟的删除工作。请注意，此数据结构是服务器状态（内存中）的一部分，而不是数据库状态（磁盘上）的一部分。因此，只要服务器在延迟工作完成之前不崩溃，此数据结构就有效。 对于这种可能性，应该在恢复日志中记录数据结构的创建和最终删除。 因此，这种替代设计不会节省日志工作量。此外，在正常处理期间、==可能发生崩溃后的恢复期间==以及==随后的恢复期间==，这两种设计都要求支持中间状态。        

- 索引删除可能很复杂，特别是如果必须创建一些结构作为响应。 
- 通过延迟更新**管理可用空间的数据结构**，可以即时删除索引。其他许多实用工具可以使用这个执行模型，但是索引删除似乎是最明显的候选者。

### 6.3 索引重建
There are a variety of reasons for rebuilding an existing index, and some systems require rebuilding an index when an efficient defragmentation would be more appropriate, in particular if index rebuilding can be online or incremental.

Rebuilding a primary index might be required if the search keys in the primary index are not unique and new values are desired in the artificial field that ensure unique references. Moving a table with physical record identifiers similarly modifies all references. Note that both operations require that all secondary indexes be rebuilt in order to reflect modified reference values.

Rebuilding all secondary indexes is also required when the primary index changes, i.e., when the set of key columns in the primary index changes. If merely their sequence changes, it is not strictly required that new references be assigned and the secondary indexes rebuild. Updating all existing secondary indexes might be slower than rebuilding the indexes, partially because updates require full information in the recovery log whereas rebuilding indexes can employ non-logged index creation (allocation-only logging).

> ==TODO：== Fig.6.4

Figure 6.4 illustrates a query execution plan for rebuilding a table’s primary index and subsequently its three secondary indexes. The scan captures the data from the current primary index or heap file. The sort prepares filling the new primary index. The spool operation retains in temporary storage only those columns required for the secondary indexes. The spool operation can be omitted if it is less expensive to scan the new primary index repeatedly than to write and re-read the spool data. Alternatively, the individual sort operations following the spool operation can serve the purpose of the spool operation, as discussed earlier in connection with Figure 6.4.

- An index may be rebuild after a corruption (due to faulty software or hardware) or when defragmentation is desired but removing and rebuilding the index is faster.
- When a primary index is rebuilt, the secondary indexes usually must be rebuilt, too. Various optimizations apply to this operation, including some that are usually not exploited for standard query processing. 

### 6.4 批量插入

Bulk insertion, also known as incremental load, roll-in, or information capture, is a very frequent operation in many databases, in particular data warehouses, data marts, and other databases holding information about events or activities (such as sales transactions) rather than states (such as account balances). Performance and scalability of bulk insertions sometimes decides among competing vendors when the time windows for nightly database maintenance or for the initial proof-of-concept implementation are short.

Any analysis of performance or bandwidth of bulk insertions must distinguish between instant bandwidth and sustained bandwidth, and between online and oﬄine load operations. The first difference is due to deferred maintenance of materialized views, indexes, statistics such as histograms, etc. For example, partitioned B-trees [43] enable high instant load bandwidth (basically, appending to a B-tree at disk write speed). Eventually, however, query performance deteriorates with additional partitions in each B-tree and requires reorganization by merging partitions; such reorganization must be considered when determining the load bandwidth that can be sustained indefinitely.

 The second difference focuses on the ability to serve applications with queries and updates during the load operation. For example, some database vendors for some of their releases recommended dropping all indexes prior to a large bulk insertion, say insertions larger than 1% of the existing table size. This was due to the poor performance of index insertions; rebuilding all indexes for 101% of the prior table size can be faster than insertions equal to 1% of the table size.

Techniques optimized for efficient bulk insertions into B-trees can be divided into two groups. Both groups rely on some form of buffering to delay B-tree maintenance and to gain some economy of scale. The first group focuses on the structure of B-trees and buffers insertions in branch nodes [74]. Thus, B-tree nodes are very large, are limited to a small fan-out, or require additional storage “on the side.” The second group exploits B-trees without modifications to their structure, either by employing multiple B-trees [100] or by creating partitions within a single B-tree by means of an artificial leading key field [43]. In all cases, pages or partitions with active insertions are retained in the buffer pool. The relative performance of the various methods, in particular in sustained bandwidth, has not yet been investigated experimentally. Some simple calculations below highlight the main effects on load bandwidth.

> ==TODO：== Fig.6.5

Figure 6.5 illustrates a B-tree node that buffers insertions, e.g., a root node or a branch node. There are two separator keys (11 and 47), three pointers to child nodes within the same B-tree, and a set of buffered insertions with each child pointer. In a secondary index, index entries contain a key value and reference to a record in the primary index of the table, indicated by “ref” here. In other words, each buffered insertion is a future leaf entry. The set of buffered insertions for the middle child is much smaller than the one for the left child, perhaps due to skew in the workload or a recent propagation of insertions to the middle child. The set of changes buffered for the right child includes not only insertions but also a deletion (key value 72). Buffering deletions is viable only in secondary indexes, after a prior update of a primary index has ensured that the value to be deleted indeed must exist in the secondary index.

The essence of partitioned B-trees is to maintain partitions within a single B-tree, by means of an artificial leading key field, and to reorganize and optimize such a B-tree online using, effectively, the merge step well known from external merge sort. This key field probably should be an integer of 2 or 4 bytes. By default, the same single value appears in all records in a B-tree, and most of the techniques for partitioned B-trees rely on exploiting multiple alternative values, temporarily in most cases and permanently for a few techniques. If a table or view in a relational database has multiple indexes, each index has its own artificial leading key field. The values in these fields are not coordinated or propagated among the indexes. In other words, each artificial leading key field is internal to a single B-tree, such that each B-tree can be reorganized and optimized independently of all others. If a table or index is horizontally partitioned and represented in multiple B-trees, the artificial leading key field should be defined separately for each partition.

> ==TODO：== Fig.6.6

Figure 6.6 illustrates how the artificial leading key field divides the records in a B-tree into partitions. Within each partition, the records are sorted, indexed, and searchable by the user-defined key just as in a standard B-tree. In this example partition 0 might be the main partition whereas partitions 3 and 4 contain recent insertions, appended to the B-tree as new partitions after in-memory sorting. The last partition might remain in the buffer pool, where it can absorb random insertions very efficiently. When its size exceeds the available buffer pool, a new partition is started and the prior one is written from the buffer pool to disk, either by an explicit request or on demand during standard page replacement in the buffer pool. Alternatively, an explicit sort operation may sort a large set of insertions and then append one or multiple partitions. The explicit sort really only performs run generation, appending runs as partitions to the partitioned B-tree.

The initial load operation should copy the newly inserted records or the newly filled pages into the recovery log such that the new database contents is guaranteed even in the event of a media or system failure. Reorganization of the B-tree can avoid logging the contents, and thus log only the structural changes in the B-tree index, by careful write ordering. Specifically, pages on which records or pointers have been removed may over-write their earlier versions in their old location only after new copies of the records have been written in their new location. Minimal logging enabled by careful write ordering has been described for other forms of B-tree reorganization [44, 89, 130] but it also applies to merging in partitioned B-trees after bulk insertion by appending new partitions.

For some example bandwidth calculations, consider bulk insertion into a table with a primary index and three secondary indexes, all stored on a single disk supporting 200 read–write operations per second (100 read–write pairs) and 100 MB/s read–write bandwidth (assuming large units of I/O and thus negligible access latency). In this example calculation, record sizes are 1 KB in the primary index and 0.02 KB in secondary indexes, including overheads for page and record headers, free space, etc. For simplicity, let us assume a warm buffer pool such that only leaf pages require I/O. The baseline plan relies on random insertions into 4 indexes, each requiring one read and one write operation. 8 I/Os per inserted row enable 25 row insertions per second into this table. The sustained insertion bandwidth is 25 *×*1 KB = 25 KB/s = 0.025 MB/s per disk drive. This is both the instant and the sustained insertion bandwidth.

For the plan relying on index removal and re-creation after insertion, assume index removal is practically instant. With no indexes present, the instant insertion bandwidth is equal to the disk write bandwidth. After growing the table size by 1%, index creation must scan 101 times the insertion volume, then write 106% of that data volume to run files for the 4 indexes (sharing run generation when sorting for the secondary indexes), and finally merge those runs into indexes: for a given amount of data added, the I/O volume is 1 + 101 *×*(1 + 3 *×*1*.*06) = 423 times that amount; at 100 MB/s, this permits 100/423 MB/s = 0.236 MB/s sustained insertion bandwidth. While this might seem poor compared to 100 MB/s, it is about ten times faster than random insertions, so it is not surprising that vendors have recommended this scheme.

For a B-tree implementation that buffers insertions at each branch node, assume buffer space in each node for 10 times more insertions than child pointers in the node. Propagation upon overflow focuses on the child with the most pending insertions; let us assume that 20 records can be propagated on average. Thus, only every 20th record insertion forces reading and writing a B-tree leaf, or 1/20 of a read–write pair per record insertion. On the other hand, B-tree nodes with buffer are much larger and thus each record insertion might require reading and writing both a leaf node and its parent, in which case each record insertion forces 2*/*20 = 1*/*10 of a read–write pair. In the example table with a primary index and three secondary indexes, i.e., 4 B-trees in total, these assumptions lead to 4/10 of a read–write pair per inserted record. The assumed disk hardware with 100 read–write pairs per second thus support 250 record insertion per second or 0.250 MB/s sustained insertion bandwidth. In addition to the slight bandwidth improvement when compared to the prior method, this technique retains and maintains the original B-tree indexes and permits query processing throughout the load process.

 With the assumed disk hardware, partitioned B-trees permit 100 MB/s instant insertion bandwidth, i.e., purely appending new partitions sorted in the in-memory workspace using quicksort or replacement selection. B-tree optimization, i.e., a single merge level in a partitioned B-tree that reads and writes partitions, can process 50 MB/s. If reorganization is invoked when the added partitions reach 33% of the size of the master partition, adding a given amount of data requires an equal amount of initial writing plus 4 times this amount for reorganization (reading and writing). 9 amounts of I/O for each amount of data produce a sustained insertion bandwidth of 11 MB/s for a single B-tree. For the example table with a primary index and three secondary indexes, this bandwidth must be divided among all indexes, giving 11 MB/s *÷*(1 + 3*×*0*.*02) KB = 10 MB/s sustained insertion bandwidth. This is more than an order of magnitude faster than the other techniques for bulk loading. In addition, query processing remains possible throughout initial capture of new information and during B-tree reorganization. A multi-level merge scheme might increase this bandwidth further because the master partition may be reorganized less often yet the number of existing partitions can remain small.

In addition to traditional databases, B-tree indexes can also be exploited for data streams. If only recent data items are to be retained in the index, both bulk insertion techniques and bulk deletion techniques are required. Therefore, indexing streams is discussed in the next section.

- Efficiency of bulk insertions (also known as load, roll-in, or information capture) is crucial for database operations. 
- Efficiency of index maintenance is so poor in some implementations that indexes are removed prior to large load operations. Various techniques have been published and implemented to speed up bulk insertions into B-tree indexes. Their sustained insertion bandwidths differ by orders of magnitude. 























