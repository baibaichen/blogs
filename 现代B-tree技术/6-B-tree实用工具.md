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

重建现有索引有多种原因，有些系统需要在高效整理碎片时重建索引，特别是可以在线或增量重建索引。

如果主索引中的搜索键不唯一，为了确保唯一引用，需要在**人工字段**中增加新值，则可能需要重建主索引。 类似地，移动有物理记录标识符的表需要修改所有引用。 请注意，这两个操作都需要重建所有二级索引才能反映修改后的引用值。

当主索引更改时，即主索引中**主键的列集**发生更改时，需要重建所有二级索引。如果仅仅改变它们的顺序，则不需严格地重建二级索引和分配新引用。更新所有现有的二级索引可能比重建索引慢，==部分原因是更新索引需要在恢复日志中保存完整信息，而重建索引不必记录创建索引的信息（仅需要记录分配信息）==。

> ==TODO：== Fig.6.4

图6.4的查询执行计划，说明如何重建表的主索引以及随后的三个二级索引。扫描从当前主索引或堆文件中读取数据，排序准备填充新的主索引。在临时存储中，缓存（spool）操作只保留二级索引所需的那些列。如果反复扫描新的主索引比写入和重新读取spool数据的成本更低，则可以省略spool操作。==或者，如前面与图6.4相关的讨论所述，spool之后的每个单独的排序可以起到spool操作的作用==。

- 可以在（由于软件或硬件故障）损坏索引后重建索引，或在需要整理碎片但删除和重建更快时重建索引。
- 重建主索引时，通常也必须重建二级索引。 各种优化适用于此操作，包括一些通常不用于标准查询处理的优化。

> ==TODO:==
>
> 1. **artificial field**: 人工字段。这个还要结合全文来看
> 2. 这章反复说到恢复，说到日志，有必要回顾下数据库的日志操作
> 3. *前面与6.4相关的讨论*，这里的==**前面**==是指5.10节吗？

### 6.4 批量插入

**批量插入**，也称为增量加载、滚入或信息捕获，是许多数据库中非常频繁的操作，尤其是数据仓库、数据集市、或其他数据库，它们主要包含事件或活动（如销售事务）信息，而不是状态（如账户余额）信息。当夜间数据库维护或初始概念验证实现的时间窗口很短时，批量插入的性能和可扩展性有时会决定竞争供应商。

任何对批量插入的性能或带宽的分析，都必须区分**即时带宽**和**持续带宽**，以及**在线加载**和**离线加载**。**第一个区别**是由于物化视图，索引，直方图等统计数据的延迟维护。例如，分区B-trees[43]可以实现**很高的即时负载带宽**（基本上，以磁盘写入速度追加到B-tree）。但是最终，查询性能会随着每个B-tree中的额外分区而恶化，需要通过合并分区进行重组；在确定要一直维持这么高的负载带宽时，必须考虑这种重组。

第二个区别在于在加载操作期间，是否能为应用程序提供查询和更新服务。例如，某些数据库供应商建议，在其某些版本大量插入之前删除所有索引，例如插入大于现有表大小的1％。 这是由于索引插入的性能不佳；为先前表大小的101%重建所有索引，可能比插入等于表大小1%的数据更快。

用于优化批量插入B-tree的技术可分为两组。两组都依赖某种形式的缓冲来延迟B-tree维护，并获得一定的规模经济性。第一组主要研究B-tree的结构并缓冲分支节点中的插入[74]。因此，B-tree节点非常大，==仅限于小扇出==，或者==<u>”每边“</u>==需要额外存储。第二组利用B-tree而不修改它们的结构，通过使用多个B-tree[100]，或通过人工前导键字段[43]在单个B-tree中创建分区。在所有情况下，具有活动插入的页面或分区都保留在缓冲池中。各种方法的相对性能，特别是在持续带宽方面，尚未通过实验研究。下面的一些简单计算**强调**了对负载带宽的主要影响。

> ==TODO：== Fig.6.5

图6.5显示了缓冲插入的B-tree节点，例如根节点或分支节点。有两个分隔符键（11和47），有三个指针指向同一个B-tree内子节点，以及每个子指针带有一组缓冲的插入。在二级索引中，索引项包含一个键值，一个指向表主索引的记录引用，此处以“ref”表示。换句话说，每个缓冲插入数据项未来都应该存储在叶节点。中间子节点的缓冲插入远小于左边子节点的缓冲插入，这可能是由于数据倾斜，或插入到中间子节点刚刚发生了==<u>分裂传播</u>==。右边子节点的缓冲的修改操作，不仅包括插入还包括删除（键值72）。主索引之前的更新，要确保删除的值确实存在于二级索引中，仅在二级索引中缓冲删除才可行。

分区B-tree的本质是通过人工前导键字段来维护单个B-树中的分区，使用从**外部归并排序**中众所周知的归并步骤，高效地在线重组和优化这样的B-tree。此关键字字段可能是2或4字节的整数。==<u>默认情况下，B-tree中所有记录中该字段是相同的单个值，分区B-tree的大多数技术都依赖于利用多个可选值，大多数情况下是临时利用，少量技术是永久利用</u>==。如果关系数据库中的表或视图有多个索引，则每个索引都有自己的人工前导关键字字段。这些字段中的值不在索引之间协调或传播，也就是说，每个人工引导关键字段都是单个B-tree内部的数据结构，这样每个B-tree就可以独立地进行重组和优化。如果一个表或索引是水平分区的，并用多个B-tree表示，那么应该为每个分区分别定义人工前导关键字字段。

> ==TODO：== Fig.6.6

图6.6说明了人工前导关键字字段如何将B-tree中的记录划分为分区。在每个分区中，记录按用户定义的键进行排序，索引和搜索，就像在标准B-tree中一样。 在本例中，分区0可能是主分区，而分区3和4包含最近的插入，在内存中排序后作为新分区追加到B-tree。最后一个分区可能保留在缓冲池中，可以非常有效地吸收随机插入。当其大小超过可用缓冲池时，通过显式请求、或在缓冲池中的标准页面替换期间，按需将其写入磁盘，然后启动一个新分区。或者，显式排序操作可以对大量插入进行排序，然后追加一个或多个分区。显式排序实际上只生成有序的数据文件，然后将它们作为分区追加到分区B-tree。

初始加载操作应将新插入的记录或新填充的页面复制到恢复日志中，以便即使在介质或系统发生故障时也能保证新的数据库内容。重组B-tree可以避免（在恢复日志中）记录内容，因此通过==<u>仔细的写入顺序</u>==，只需要在日志中记录B-tree索引中的结构变化。具体而言，对于有记录或指针被删除的页面，在新位置写入记录新副本后，才能在旧位置覆盖其早期版本。对于其他形式的B-tree重组，已经在[44,89,130]描述了通过==<u>仔细写入顺序</u>==，从而启用最小的日志记录。对于分区B-tree，批量插入后将向其追加新分区，也适用该技术合并分区B-tree的。

考虑批量插入一个表的带宽计算示例，该表包含一个主索引和三个二级索引，都存储在一个磁盘上，磁盘支持每秒200次读写操作（100个读写对）和100 MB/s读写带宽（假设是连续I/O，访问延迟可忽略不计）。 在示例计算中，主索引记录大小为1 KB，二级索引记录大小为0.02 KB，包括页面和记录标头的开销，以及空闲空间等。为简单起见，我们假设一个热缓冲池，只有叶节点需要I/O。**基准计划**依赖于随机插入4个索引，每个索引需要一次读取和一次写入操作。 每插入一行数据需要8次I/O，每秒可在此表中插入25行。每个磁盘驱动器插入的持续带宽为25×1 KB=25KBs = 0.025 MB/s。 这是插入的即时和持续带宽。

对于依赖索引删除和插入后重建索引的计划，假设索引删除几乎是即时的。 如果不存在索引，则即时插入带宽等于磁盘写入带宽。将表大小增加1%后，索引创建必须扫描插入数据量的**101**倍，然后将该数据量的**106％**写入索引的4个临时有序文件（在排序二级索引时共享临时有序文件），最后将它们合并到索引中：对于给定的添加数据量，I/O量为==**1 + 101×(1 + 3×1.06)=423**==，等于该量的423倍；在100 MB/s时，这允许100/423 MB/s = 0.236 MB/s的持续插入带宽。 虽然与100 MB/s相比这可能看起来很差，但它比随机插入快十倍，因此供应商推荐这种方案并不奇怪。

对于**在每个分支节点缓冲插入**的B-tree，假设每个节点中的缓冲区空间比节点中的子指针多插入10倍。==<u>溢出时的传播集中在具有最多未完成插入的子节点上；让我们假设平均可以传播20条记录</u>==。因此，每插入20条记录强制读写一个B-tree叶节点，或每个记录插入需要**1/20**的IO读写对。另一方面，具有缓冲区的B-tree节点要大得多，因此每个记录插入可能需要读写一个叶节点及其父节点，这种情况下插入每个记录强制**2/20=1/10** IO读写对。在包含一个主索引和三个二级索引（即总共4个B树）的示例表中，这些假设导致每个插入的记录有4/10的IO读写对。假定磁盘硬件每秒100对读写，因此支持每秒250条记录插入或0.250 MB/s的持续插入带宽。除了与以前的方法相比带宽略有改善外，此技术还保留和维护原始的B-tree索引，并允许在整个加载过程中进行查询处理。

在假定的磁盘硬件中，分区B-tree允许100MB/s的即时插入带宽，即，纯粹追加在内存工作空间排好序的新分区，排序算法是快排或是==**置换选择排序**==。优化B-tree，即合并分区B-tree中的一层，需要读取和写入分区，可以处理50MB/s（的插入数据）。如果**添加的分区达到主分区大小的33％时**调用重组，则添加给定数量的数据，需要相同数量的初始写入，加上此数量4倍的读取和写入以进行重组。对于单个B-tree，一次I/O放大9倍产生11MB/s的持续插入带宽。对于示例表，有主索引和三个二级索引，必须在所有索引之间划分该带宽，从而得到11 MB/s ÷(1 + 3×0.02) KB = 10 MB/s的持续插入带宽。这比其他**批量加载**技术快一个数量级。此外，在初始捕获新信息和B-tree重组期间，仍然可能处理查询。多层合并方案可以进一步增加该带宽，因为可以较少地重新组织主分区，但是现有分区的数量可以保持很小。

除了传统的数据库，B-tree索引也可以用于数据流。 **如果仅在索引中保留最近的数据项**，则需要批量插入技术和批量删除技术。 因此，索引流将在下一节中讨论。

- 批量插入的效率（也称为加载，滚入或信息捕获）对于数据库操作至关重要。
- 在某些实现中，维护索引的效率非常低，以至于在执行大型加载操作之前，索引会被删除。已经发布并实现了各种技术，以加速对B-tree索引的批量插入。它们的持续插入带宽相差几个数量级。

> ==TODO：==有哪些需要深入？

### 6.5 批量删除

Bulk deletion, also known as purging, roll-out, or information de-staging, can employ some of the techniques invented for bulk inser-tion. For example, one technique for deletion simply inserts anti-matter records with the fastest bulk insertion technique, leaving it to queries or a subsequent reorganization to remove records and reclaim storage space.

Partitioned B-trees permit reorganization prior to the actual dele-tion. In the first and preparatory step, records to be deleted are moved from the main “source” partition to a dedicated “victim” partition. In the second and final step, this dedicated partition is deleted very effi-ciently, mostly be simply de-allocation of leaf nodes and appropriate repair of internal B-tree nodes. Note that the first step can be incre-mental, rely entirely only system transactions, and can run prior to the time when the information truly should vanish from the database.

> ==TODO：== Fig.6.7

Figure 6.7 shows the intermediate state of a partitioned B-tree after preparation for bulk deletion. The B-tree entries to be deleted have all been moved from the main partition into a separate parti-tion such that their actual deletion and removal can de-allocate entire leaf pages rather than remove individual records distributed in all leaf pages. If multiple future deletions can be anticipated, e.g., daily purge of out-of-date information, multiple victim partitions can be populated at the same time.

The log volume during bulk deletion in partitioned B-trees can be optimized in multiple ways. First, the initial reorganization (“un-merging”) into one or more victim partitions can employ the same techniques as merging based on careful write ordering. Second, after the victim partitions have been written back to the database, turning multi-ple valid records into ghost records can be described in a single short log record. Third, erasing ghost records does not require contents logging if the log records for removal and commit are merged, as described earlier. Fourth, de-allocation of entire B-tree nodes (pages) can employ simi-lar techniques turning separator keys into ghost records. If the victim partitions are so small that they can remain in the buffer pool until their final deletion, committed deletion of pages in a victim partition permits writing back dirty pages in the source partitions.

Techniques for bulk insertion and bulk deletion together enable indexing of data streams. Data streaming and near-real-time data processing can benefit from many database techniques, perhaps adapted, e.g., from demand-driven execution to data-driven execution [22]. Most stream management systems do not provide persistent indexes for stream contents, however, because the naive or traditional index maintenance techniques would slow down processing rates too much.

With high-bandwidth insertions (appending partitions sorted in memory), index optimization (merging runs), partition splitting (by prospective deletion date), and deletions (by cutting entire partitions), B-tree indexes on streams can be maintained even on permanent storage. For example, if a disk drive can move data at 100 MB/s, new data can be appended, recent partitions merged, imminently obsolete partitions split from the main partition, and truly obsolete partitions cut at about 20 MB/s sustained. If initial or intermediate partitions are placed on particularly efficient storage, e.g., flash devices or nonvolatile RAM, or if devices are arranged in arrays, the system bandwidth can be much higher.

A stream with multiple independent indexes enables efficient insertion of new data and concurrent removal of obsolete data even if multiple indexes require constant maintenance. In that case, synchronizing all required activities imposes some overhead. Nonetheless, the example disk drive can absorb and purge index entries (for all indexes together) at 20 MB/s.

Similar techniques enable staging data in multiple levels of a storage hierarchy, e.g., in-memory storage, flash devices, performance-optimized “enterprise” disks and capacity-optimized “consumer” disks. Disk storage may differ not only in the drive technology but also in the approach to redundancy and failure resilience. For example, performance is optimized with a RAID-1 “mirroring” configuration whereas cost-per-capacity is optimized with a RAID-5 “striped redundancy” configuration or a RAID-6 “dual redundancy” configuration. Note that RAID-5 and -6 can equal each other in cost-per-capacity because the latter can tolerate dual failures and thus can be employed in larger disk arrays.

- Bulk deletion is less important than bulk insertion; nonetheless, various optimizations can affect bandwidth by orders of magnitude. 
- Indexing data streams requires techniques from both bulk insertion and bulk deletion.

### 6.6 碎片整理
Defragmentation in file systems usually means placing blocks physically together that belong to the same file; in database B-trees, defragmentation encompasses a few more considerations. These considerations apply to individual B-tree nodes or pages, to the B-tree structure, and to separator keys. In many cases, defragmentation logic can be invoked when some or all affected pages are in the buffer pool due to normal workload processing, resulting in incremental and online defragmentation or reorganization [130].

For each node, defragmentation includes free space consolidation within each page for efficient future insertions, removal of ghost records (unless currently locked by user transactions), and optimization of in-page data compression (e.g., de-duplication of field values). The B-tree structure might be optimized by defragmentation for balanced space utilization, free space as discussed above in the context of B-tree creation, shorter separator keys (suffix truncation), and better prefix truncation on each page.

B-tree defragmentation can proceed in key order or in independent key ranges, which also creates an opportunity for parallelism. The key ranges for each task can be determined a priori or dynamically. For example, when system load increases, a defragmentation task can commit its changes instantaneously, pause, and resume later. Note that defragmentation does not change the contents of a B-tree, only its representation. Therefore, the defragmentation task does not need to acquire locks. It must, of course, acquire latches to protect in-memory data structures such as page images in the buffer pool.

Moving a node in a traditional B-tree structure is quite expensive, for several reasons. ***First***, <u>the page contents might be copied from one page frame within the buffer pool to another. While the cost of doing so is moderate, it is probably faster to “rename” a buffer page, i.e., to allocate and latch buffer descriptors for both the old and new locations and then to transfer the page frame from one descriptor to the other</u>. Thus, the page should migrate within the buffer pool “by reference” rather than “by value.” If each page contains its intended disk location to aid database consistency checks, this field must be updated at this point. If it is possible that a de-allocated page **lingers** in the buffer pool, e.g., after a temporary table has been created, written, read, and dropped, this optimized buffer operation must first remove from the buffer’s hash table any prior page with the new page identifier. Alternatively, the two buffer descriptors can simply swap their two page frames.

***Second***, moving a page can be expensive because each B-tree node participates in **a web of** pointers. When moving a leaf page, the parent as well as both the preceding leaf and the succeeding leaf must be updated. Thus, all three surrounding pages must be present in the buffer pool, their changes recorded in the recovery log, and the modified pages written to disk before or during the next checkpoint. It is often advantageous to move multiple leaf pages at the same time, such that each leaf is read and written only once. Nonetheless, each single-page move operation can be a single system transaction, such that locks can be released frequently both for the allocation information (e.g., an allocation bitmap) and for the index being reorganized.

> If B-tree nodes within each level do not form a chain by physical page identifiers, i.e., if each B-tree node is pointed to only by its parent node but not by neighbor nodes, page migration and therefore defrag-mentation are considerably less expensive. Specifically, only the parent of a B-tree node requires updating when a page moves. Neither its siblings nor its children are affected; they are not required in memory during a page migration, they do not require I/O or changes or log records, etc.

如果每层的B-tree节点没有通过物理页标识符形成链，即，如果每个B-tree节点仅由其父节点而不是相邻节点指向，则迁移页和碎片整理的成本要低得多。具体来说，只有B-tree节点的父节点在页面移动时需要更新。它的兄弟和子节点都不受影响；页面迁移期间，不需要它们在内存中，也不需要I/O、修改或记录日志等。

The ***third*** reason why page migration can be quite expensive is logging, i.e., the amount of information written to the recovery log. The standard, “fully logged” method to log a page migration during defragmentation is to log the page contents as part of allocating and formatting a new page. Recovery from a system crash or from media failure unconditionally copies the page contents from the log record to the page on disk, as it does for all other page allocations.

Logging the entire page contents is only one of several means to make the migration durable, however. A second, “forced write” approach is to log the migration itself with a small log record that contains the old and new page locations but not the page contents, and to force the data page to disk at the new location prior committing the page migration. Forcing updated data pages to disk prior to transac-tion commit is well established in the theory and practice of logging and recovery [67]. A recovery from a system crash can safely assume that a committed migration is reflected on disk. Media recovery, on the other hand, must repeat the page migration, and is able to do so because the old page location still contains the correct contents at this point during log-driven redo. The same applies to log shipping and database mirroring, i.e., techniques to keep a second (often remote) database ready for instant failover by continuously shipping the recovery log from the primary site and running continuous redo recovery on the secondary site.

The most ambitious and efficient defragmentation method neither logs the page contents nor forces it to disk at the new location. Instead, this “non-logged” page migration relies on the old page location to preserve a page image upon which recovery can be based. During system recovery, the old page location is inspected. If it contains a log sequence number lower than the migration log record, the migration must be repeated, i.e., after the old page has been recovered to the time of the migration, the page must again be renamed in the buffer pool, and then additional log records can be applied to the new page. To guarantee the ability to recover from a failure, it is necessary to preserve the old page image at the old location until a new image is written to the new location. Even if, after the migration transaction commits, a separate transaction allocates the old location for a new purpose, the old location must not be overwritten on disk until the migrated page has been written successfully to the new location. Thus, if system recovery finds a newer log sequence number in the old page location, it may safely assume that the migrated page contents are available at the new location, and no further recovery action is required.

Some methods for recoverable B-tree maintenance already employ this kind of write dependency between data pages in the buffer pool, in addition to the well-known write dependency of write-ahead logging. To implement this dependency using the standard technique, both the old and new page must be represented in the buffer manager. Differently than in the usual cases of write dependencies, the old location may be marked clean by the migration transaction, i.e., it is not required to write anything back to the old location on disk. Note that redo recovery of a migration transaction must re-create this write dependency, e.g., in media recovery and in log shipping.

The potential weakness of this third method are backup and restore operations, specifically if the backup is “online,” i.e., taken while the system is actively processing user transactions, and the backup contains not the entire database but only pages currently allocated to some table or index. Moreover, the detailed actions of the backup process and page migration must interleave in a particularly unfortunate way. In this case, a backup might not include the page image at the old location, because it is already deallocated. Thus, when backing up the log to complement the online database backup, migration transactions must be complemented by the new page image. In effect, in an online database backup and its corresponding restore operation, the logging and recovery behavior is changed in effect from a non-logged page migration to a fully logged page migration. Applying this log during a restore operation must retrieve the page contents added to the migration log record and write it to its new location. If the page also reflects subsequent changes that happened after the page migration, recovery will process those changes correctly due to the log sequence number on the page. Again, this is quite similar to existing mechanisms, in this case the backup and recovery of “non-logged” index creation supported by some commercial database management systems.

While a migration transaction moves a page from its old to its new locations, it is acceptable for a user transaction to hold a lock on a key within the B-tree node. It is necessary, however, that any such user transaction must search again for the B-tree node, with a new search pass from B-tree root to leaf, in order to obtain the new page identifier and to log further contents changes, if any, correctly. This is very similar to split and merge operations of B-tree nodes, which also invalidate knowledge of page identifiers that user transactions may temporarily retain. Finally, if a user transaction must roll back, it must compensate its actions at the new location, again very similarly to compensating a user transaction after a different transaction has split or merged B-tree nodes.

- Most implementations of B-trees (as of other storage structures) require occasional defragmentation (reorganization) to ensure contiguity (for fewer seeks during scans), free space, etc. 
- The cost of page movement can be reduced by using fence keys instead of neighbor pointers (see also Sections 3.5 and 4.4) and by careful write ordering (see also Section 4.10).
- Defragmentation (reorganization, compaction) can proceed in many small system transactions and it can ‘pause and resume’ without wasting work. 

### 6.7 索引验证

There obviously is a large variety of techniques for efficient data structures and algorithms for B-tree indexes. As more techniques are invented or implemented in a specific software system, omissions or mistakes occur and must be found. Many of these mistakes manifest themselves in data structures that do not satisfy the intended invariants. Thus, as part of rigorous regression testing during software development and improvement, verification of B-trees is a crucial necessity.

Many of these omissions and mistakes require large B-trees, high update and query loads, and frequent verification in order to be found in a timely manner during software development. Thus, efficiency is important in B-tree verification.

==<u>许多遗漏和错误需要大型B树，高更新和查询负载以及频繁的验证，以便在软件开发期间及时发现。 因此，效率在B树验证中很重要。</u>==

To be sure, verification of B-trees is also needed after deployment. Hardware defects occur in DRAM, flash devices, and disk devices as is well known [114]. Software defects can be found not only in database management systems but also in device drivers, file system code, etc. [5, 61]. While some self-checking exists in many hardware and software layers, vendors of database management system recommend regular verification of databases. Verification of backup media is also valuable as it enhances the trust and confidence is those media and their con-tents, should they ever be needed.

> For example, Mohan described the danger of partial writes due to performance optimizations in implementations of the SCSI standard [94]. His focus was on problem prevention using appropriate page modification, page verification after each read operation, logging, log analysis, recovery logic, etc. The complexity of these techniques, together with the need for ongoing improvements in these performance-critical modules, reinforces our belief that complete, reliable, and efficient verification of B-tree structures is a required defensive measure.

例如，Mohan描述了由于SCSI标准实现中的性能优化而导致部分写入的危险[94]。 他的重点是使用适当的页面修改，每次读取操作后的页面验证，日志记录，日志分析，恢复逻辑等来预防问题。这些技术的复杂性，加上对这些性能关键模块的持续改进的需要，强化了我们的信念，即B-tree结构的完整、可靠和有效验证是必需的防御措施。

> ==TODO：== Fig.6.8

For example, Figure 6.8 shows the result of an incorrect splitting of a leaf node. When leaf node *b*was split and leaf node *c*was created, the backward pointer in successor node *d*incorrectly remained unchanged. A subsequent (descending) scan of the leaf level will produce a wrong query result, and subsequent split and merge operations will create further havoc. The problem might arise after an incomplete execution, an incomplete recovery, or an incomplete replication of the split operation. The cause might be a defect in the database software, e.g., in the buffer pool management, or in the storage management software, e.g., in snapshot or version management. In other words, there are many thousands of lines of code that may contain a defect that leads to a situation like the one illustrated in Figure 6.8.

As B-trees are complex data structures, efficient verification off all invariants has long been elusive, including in-page invariants, parent-child pointers and neighbor pointers, and key relationships, i.e., the correct ordering of separator keys and keys in the leaf nodes. The latter problem pertains not only to parent-child relationships but to all ancestor-descendent relationships. For example, a separator key in a B-tree’s root node must sort not only the keys in the root’s immediate children but keys at all B-tree levels down to the leaves. Invariants that relate multiple B-trees, e.g., the primary index of a table and its secondary indexes or a materialized view and its underlying tables and views, **can usually be processed with appropriate joins**. If all aspects of database verification are modeled as query processing problems, many query processing techniques can be exploited, from resource management to parallel execution.

In-page invariants are easy to validate once a page is in the buffer pool but exhaustive verification requires checking all instances of all invariants, e.g., key range relationships between all neighboring leaf nodes. The cross-page invariants are easy to verify with an index-order sweep over the entire key range. If, however, an index-order sweep is not desirable due to parallel execution or due to the limitations of backup media such as tapes, the structural invariants can be verified using algorithms based on aggregation. While scanning B-tree pages in any order, required information is extracted from each page and matched with information from other pages. For example, neighbor pointers match if page *x*names page *y*as its successor and page *y*names page *x*as its predecessor. Key ranges must be included in the extracted information in order to ensure that key ranges are disjoint and correctly distinguished by the separator key in the appropriate ancestor node. If two leaf nodes share a parent node, this test is quite straightforward; if the lowest common ancestor is further up in the B-tree, and if transitive operations are to be avoided, some additional information must be retained in B-tree nodes.

> ==TODO：== Fig.6.9

Figure 6.9 shows a B-tree with neighboring leaf nodes with no shared parent but instead a shared grandparent, i.e., cousin nodes *d*and *e*. Shaded areas represent records and their keys; two keys are different (equal) if their shading is different (equal). Efficient verification of keys and pointers among cousin nodes *d*and *e*does not have an immediate or obvious efficient solution in a traditional B-tree implementation. **The potential problem is that there is no easy way to verify that all keys  in leaf page *d*are indeed smaller than the separator key in root page a and that all keys in leaf page *e*are indeed larger than the separator key in root page *a*.** Correct key relationships between neighbors (*b−c*and *d−e*) and between parents and children (*a−b, a−c, b−d, c−e*) do not guarantee correct key relationships across skipped levels (*a−d, a−e*).


> ==TODO：== Fig.6.10

Figure 6.10 shows how fence keys enable a simple solution for the cousin problem in B-tree verification, even if fence keys were originally motivated by write-optimized B-trees [44]. **The essential difference to traditional B-tree designs is that page splits not only post a separator key to the parent page but also retain copies of this separator key as high and low “fence keys” in the two post-split sibling pages.** Note that separators and thus fence keys can be very short due to prefix and suffix truncation [10]. These fence keys take the role of sibling pointers, replacing the traditional page identifiers with search keys. Fence keys speed up defragmentation by eliminating all but one page identifier that must be updated when a page moves, namely the child pointer in the parent node. Fence keys also assist key range locking since they are key values that can be locked. In that sense, they are similar to traditional ghost records, except that fence keys are not subject to ghost cleanup.

The important benefit here is that verification is simplified and the cousin problem can readily be solved, including “second cousins,” “third cousins,” etc. in B-trees with additional levels. In Figure 6.10, the following four pairs of facts can be derived about the key marked by horizontal shading, each pair derived independently from two pages.

  1. From page *a*, the fact that *b* is a level-1 page, and its high fence key
  2. From page *a*, the fact that *c* is a level-1 page, and its low fence key 
  3. From page *b*, the fact that *b* is a level-1 page, and its high fence key; this matches fact 1 above 
  4. From page *b*, the fact that *d* is a leaf page, and its high fence key 
  5. From page *c*, the fact that *c* is a level-1 page, and its low fence key; this matches fact 2 above 
  6.  From page *c*, the fact that *e* is a leaf page, and its low fence key 
  7. From page *d*, the fact that *d* is a leaf page, and its high fence key; this matches fact 4 above 
  8. From page *e*, the fact that *e* is a leaf page, and its low fence key; this matches fact 6 above

No match is required between cousin pages *d*and *e*. Their fence keys are equal due to transitivity among the other comparisons. In fact, matching facts derived from pages *d*and *e*could not include page identifiers, because these pages do not carry the other’s page identifiers. At best, the following facts could be derived, although they are implied by the ones above and thus do not contribute to the quality of B-tree verification:

  9.  From page *b*, the fact that a level-1 page has a specific high fence key 
  10. From page *c*, the fact that a level-1 page has a specific low fence key; to match fact 9 above 
  11. From page *d*, the fact that a leaf page has a specific high fence key 
  12. From page *e*, the fact that a leaf page has a specific low fence key; to match fact 11 above 

The separator key from the root is replicated along the entire seam of neighboring nodes all the way to the leaf level. Equality and consistency are checked along the entire seam and, by transitivity, across the seam. Thus, fence keys also solve the problem of second and third cousins etc. in B-trees with additional levels.

These facts can be derived in any order; thus, B-tree verification can consume database pages from a disk-order scan or even from backup media. These facts can be matched using an in-memory hash table (and possibly hash table overflow to partition files on disk) or they can be used to toggle bits in a bitmap. The former method requires more memory and more CPU effort but can identify any error immediately; the latter method is faster and requires less memory, but requires a second pass of the database in case some facts fail to match equal facts derived from other pages. Moreover, the bitmap method has a miniscule probability of failing to detect two errors that mask each other.

Fence keys also extend local online verification techniques [80]. In traditional systems, neighbor pointers can be verified during a root-to-leaf navigation only for siblings but not for cousins, because the identity of siblings is known from information in the shared parent node but verification of a cousin pointer would require an I/O operation to fetch the cousin’s parent node (also its grandparent node for a second cousin, etc.). Thus, earlier techniques [80] cannot verify all correctness constraints in a B-tree, no matter how many search operations per-form verification. Fence keys, on the other hand, are equal along entire B-tree seams, from leaf level to the ancestor node where the key value serves as separator key. A fence key value can be exploited for online verification at each level in a B-tree, and an ordinary root-to-leaf B-tree descent during query and update processing can verify not only siblings with a shared parent but also cousins, second cousins, etc. Two search operations for keys in neighboring leaves verify all B-tree constraints, even if the leaves are cousin nodes, and search operations touching all leaf nodes verify all correctness constraints in the entire B-tree.

For example, two root-to-leaf searches in the index shown in Figure 6.10 may end in leaf nodes *d*and *e*. Assume that these two root-to-leaf passes occur in separate transactions. Those two searches can verify correct fence keys along the entire seam. In the B-tree that employs neighbor pointers rather than fence keys as shown in Figure 6.9, the same two root-to-leaf searches could verify that entries in leaf nodes*d*and *e*are indeed smaller and larger than the separator key in the root node but they cannot verify that the pointers between cousin nodes *d*and *e*are mutual and consistent.

B-tree verification by extracting and matching facts applies not only to traditional B-trees but also to B^link^-trees and their transitional states. Immediately after a node split, the parent node is not yet updated in B^link^-tree and thus generates the facts as above. The newly allocated page is a normal page and also generates the facts as above. The page recently split is the only one with special information, namely a neighbor pointer. Thus, a page with a neighbor pointer indicating a recent split not yet reflected in the parent must trigger derivation of some special facts. Since this old node provides the appropriate “parent facts” for the new node, the old node could be called a “foster parent” if one wants to continue the metaphor of parent, child, ancestor, etc.

> ==TODO：== Fig.6.11

Figure 6.11 illustrates such a case, with node *b*recently split. The fact about the low fence key of node *d*cannot be derived from the (future) parent node *a*. The fact derived from node *d*must be matched by a fact derived from node *b*. Thus, node *b*acts like a temporary parent for the new node *d*not only in terms of the search logic but also during verification of the B-tree structure. Note that the intermediate state in Figure 6.11 could also be used during node removal from a B-tree, again with the ability to perform complete and correct B-tree verification at any time, in any sequence of nodes, and thus on any media.

In addition to verification of a B-tree structure, each individual page must be verified prior to extraction of facts, and multiple B-tree indexes may need to be matched against one another, e.g., a secondary index against the appropriate identifier in the primary index. In-page verification is fairly straightforward, although it might be surprising how many details are worth validating. Matching multiple indexes against one another is very similar to a join operation and all standard join algorithms can be employed. Alternatively, a bitmap can be used, with a miniscule probability of two errors masking each other and with a second pass required if the bitmap indicates that an error exists.

> Automatic repair of B-tree indexes is not well studied. Techniques may rely on dropping and rebuilding an entire index, replaying the recovery log for just one page, or adjusting a page to match its related pages. A systematic study of repair algorithms, their capabilities, and their performance would be useful for the entire industry.

B-tree索引的自动修复还没有得到很好的研究。技术上可能依赖于删除和重建整个索引、仅为一个页面重做恢复日志或调整页面以匹配其相关页面。系统研究修复算法、它们的能力和性能将对整个行业有用。

- B-tree索引的验证可防止软件和硬件故障。 所有商业数据库系统都提供此类工具
- 可以通过单个索引顺序扫描来验证B-tree，由于碎片化因而代价可能高昂。
- 基于磁盘顺序扫描的验证需要汇总从页面提取的事实。位向量过滤器可以加速该过程，但是如果发现不一致，则不能精确地识别不一致性（由于可能的散列冲突）。
- 如果节点携带**fence键**而不是**邻居指针**，则查询执行（作为副作用）可以验证B-tree所有的不变式。

### 6.8 小结

总之，B-tree实用工具在数据库系统的可用性和**总体拥有成本中**起着重要的作用。B-tree在索引结构中是唯一的，因为有许多众所周知的高效实用技术，且被广泛应用。新提议索引结构的性能和可伸缩性必须能与B-tree竞争，不仅包括查询处理和更新方面的竞争，还包括从索引创建到碎片整理和索引验证的实用工具操作方面的竞争。