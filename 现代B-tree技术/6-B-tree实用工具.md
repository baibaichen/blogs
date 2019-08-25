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

---

Index removal might appear to be fairly trivial, but it might not be, for a variety of reasons. For example, if index removal can be part of a larger transaction, does this transaction prevent all other transactions from accessing the table, even if the index removal transaction might yet abort? Can index removal be online, i.e., may concurrent queries and updates be enabled for the table? For another example, if a table has both a primary (non-redundant) index plus some secondary indexes (that point to records in the primary index by means of a search key), how much effort is required when the primary index is dropped? Perhaps the leaves of the primary index may become a heap and merely the branch nodes are freed, but how long does it take to rebuild the secondary indexes? Again, can index removal be online?

Finally, an index may be very large and updates to the allocation information (e.g., free space map) may take considerable time. In that case, an “instant” removal of the index might merely declare the index obsolete in the appropriate catalog records. This is somewhat similar to a ghost record, except that a ghost indicator pertains only to the record in which it occurs whereas this obsolescence indicator pertains to the entire index represented by the catalog record. Moreover, whereas a ghost record might be removed long after its creation, space of a dropped index ought to be freed as soon as possible, since a substantial amount of storage space may be involved. Even if a system crash occurs before or during the process of freeing this space, the process ought to continue quickly after a successful restart. Suitable log records in the recovery log are required, with a careful design that minimizes logging volume but also ensures success even in the event of crashes during repeated attempts of recovery.

An alternative to the obsolescence indicator in the catalog record, an in-memory data structure may represent the deferred work. Note that this data structure is part of the server state (in memory), not of the database state (on disk). Thus, this data structure works well unless the server crashes prior to completion of the deferred work. For this eventuality, both creation and final removal of the data structure should be logged in the recovery log. Thus, this alternative design does not save logging effort. Moreover, both designs require that intermediate state be support both during normal processing and during recovery after a possible crash and the subsequent recovery.                  

- Index removal can be complex, in particular if some structures must be created in response. 
- Index removal can in instantaneous by delaying updates of the data structure used for management of free space. Many other utilities could use this execution model but index removal seems to be the most obvious candidate. 









 

 

 











