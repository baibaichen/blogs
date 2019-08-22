## 2 ROARING Bitmap

我们将32位索引(`[0；n)`)的范围划分为2^16^个整数块，这些整数共享相同的16位**最高有效位**（most significant digits）。我们使用专门的容器来存储它们的16位**最低有效位**（least significant bits）。

> When a chunk contains no more than 4096 integers, we use a sorted array of packed 16-bit integers. When there are more than 4096 integers, we use a 2^16^-bit bitmap. Thus, we have two types of containers: an array container for sparse chunks and a bitmap container for dense chunks. The 4096 threshold insures that at the level of the containers, each integer uses no more than 16 bits: we either use 2^16^ bits for more than 4096 integers, using less than 16 bits/integer, or else we use exactly 16 bits/integer.

当一个块包含的整数不超过4096个时，我们使用一个**压缩的16位整数的排序数组**。当超过4096个整数时，我们使用2^16^位**位图**。因此，我们有两种类型的容器：用于稀疏块的数组容器和用于密集块的位图容器。==<u>4096阈值确保在容器级别，每个整数使用不超过16位：对于4096个以上的整数，我们使用2^16^位，使用少于16位/整数，或者我们正好使用16位/整数</u>==。

> The containers are stored in a dynamic array with the shared 16 most-significant bits: this serves as a first-level index. The array keeps the containers sorted by the 16 most-significant bits.We expect this first-level index to be typically small: when n = 1 000 000, it contains at most 16 entries. Thus it should often remain in the CPU cache. The containers themselves should never use much more than 8 kB.

**容器存储在动态数组中，共享16个最高有效位，是第一级索引**。该数组使容器按16位**最高有效位**排序。我们期望这个第一级索引通常很小：当`n = 1,000,000`时，它最多包含16个==<u>条目</u>==。 因此它应该经常保留在CPU缓存中。 容器本身不应该使用超过8kB（的内存）。

> To illustrate the data structure, consider the list of the first 1000 multiples of 62, all integers [2^16^, 2^16^ + 100) and all even numbers in  [2 × 2^16^, 3 × 2^16^). When encoding this list using the Concise format, we use one 32-bit fill word for each of the 1000 multiples of 62, we use two additional fill words to include the list of numbers between 2^16^ and 2^16^ + 100, and the even numbers in [2 × 2^16^, 3 × 2^16^) are stored as literal words. In the Roaring format, both the multiples of 62 and the integers in [216; 216 + 100) are stored using an array container using 16-bit per integer. The even numbers in  [2 × 2^16^, 3 × 2^16^)are stored in a 2^16^-bit bitmap container. See Fig. 1.

为了说明数据结构，考虑前1000个62倍数的列表、[2^16^, 2^16^ + 100)之间所有的整数和 [2 × 2^16^, 3 × 2^16^)之间所有的偶数。当使用**Concise**的格式编码此列表时，对1000个62的倍数使用一个32位的填充字，使用两个额外的填充字来包括2^16^到2^16^ + 100之间的数字列表，[2 × 2^16^, 3 × 2^16^)中的偶数存储为文字。在**Roaring**的格式中，用**16位整数**的数组（`short []`）来存储1000个62的倍数和[2^16^, 2^16^ + 100)中的所有整数。 [2 × 2^16^, 3 × 2^16^)中的偶数存储在2^16^位的位图容器中。见图1。

> ==TODO：==Figure 1. Roaring bitmap containing the list of the first 1000 multiples of 62, all integers [2^16^,  2^16^ + 100)  and all even numbers in [2 × 2^16^, 3 × 2^16^). 

> Each Roaring container keeps track of its cardinality (number of integers) using a counter. Thus computing the cardinality of a Roaring bitmap can be done quickly: it suffices to sum at most  $\lceil n/2^{16} \rceil$ counters. It also makes it possible to support rank and select queries faster than with a typical bitmap: rank queries count the number of set bits in a range [0, i] whereas select queries seek the location of the ith set bit.

每个Roaring容器使用计数器跟踪其基数（整数的个数）。 因此，可以快速完成计算Roaring位图的基数：它最多可以求和 $\lceil n/2^{16} \rceil$个计数器。与传统的位图相比，它还可以更快地支持排名和选择查询：排名查询计算范围[0, i]中的设置位数，而选择查询查找第i个设置位的位置。

> ==TODO：== 什么是**rank query**，这里感觉是range query

> The overhead due to the containers and the dynamic array means that our memory usage can exceed 16 bits/integer. However, as long as the number of containers is small compared to the total number of integers, we should never use much more than 16 bits/integer. We assume that there are far fewer containers than integers. More precisely, we assume that the density typically exceeds 0.1% or that `n/|S| > 0.001`. When applications encounter integer sets with lower density (less than 0.1%), a bitmap is unlikely to be the proper data structure.

由容器和动态数组引起的开销意味着我们的内存使用量可能超过16位/整数。但是，只要容器的数量比整数的总数少，我们就不应该使用超过16位/整数。我们假设容器远少于整数个数。更确切地说，我们假设密度通常超过**0:1%**，或者`n/|S| > 0.001`。当应用程序遇到密度较低（小于**0:1%**）的整数集时，位图可能不是正确的数据结构。

> The presented Roaring data layout is intentionally simple. Several variations are possible. For very dense bitmaps, when there are more than 2^16^ - 4096 integers per container, we could store the locations of the zero bits instead of a 2^16^-bit bitmap. Moreover, we could better compress sequences of consecutive integers. We leave the investigation of these possibilities as future work.

呈现的Roaring数据布局非常简单，有几种可能的变化。对于非常密集的位图，当每个容器的整数个数超过2^16^ - 4096时，可以存储零位的位置而不是2^16^位的位图。而且，我们可以更好地压缩连续整数的序列。对这些可能性的调查是我们未来的工作。

---
## 4. ROARING BITMAP

> For a detailed presentation of the original Roaring model, we refer the interested reader to Chambi et al. [7]. We summarize the main points and focus on the new algorithms and their implementations.

有关原始Roaring模型的详细介绍，请感兴趣的读者参阅Chambi等人的论文[7]。 这里总结了要点，并重点关注新算法及其实现。

> Roaring bitmaps are used to represent sets of 32-bit unsigned integers. At a high level, a Roaring bitmap implementation is a key-value data structure where each key-value pair represents the set S of all 32-bit integers that share the same most significant 16 bits. The key is made of the shared 16 bits, whereas the value is a container storing the remaining 16 least significant bits for each member of S. No container ever uses much more than 8 kB of memory. Thus, several such small containers fit in the L1 CPU cache of most processors: the last Intel desktop processor to have less than 64 kB of total (data and code) L1 cache was the P6 created in 1995, whereas most mobile processors have 32 kB (e.g., NVidia, Qualcomm) or 64 kB (e.g., Apple) of total L1 cache.

Roaring位图用于表示**32位无符号整数**的集合。在高层，Roaring位图实现了一个键值数据结构，每个**键值对**（key-value）表示**最高16位相同**的**32位整数的集合**。**键**（key）由共享的16位组成，而**值**（value）是一个容器，用于存储S中每个成员剩下的16个最低有效位。任何容器都不会使用超过**8KB**的内存。因此，几个这样的小容器适合大多数CPU的**L1缓存**，**最后一个L1缓存的总和（数据和代码）小于64 KB的英特尔台式机处理器是1995年的P6**，而大多数移动处理器的L1缓存总和有**32KB**（例如NVidia，Qualcomm）或**64KB**（例如Apple）。

> In our actual implementation, the key-value store is implemented as two arrays: an array of packed 16-bit values representing the keys and an array of containers. The arrays expand dynamically in a standard manner when there are insertions. Alternatively, we could use a tree structure for faster insertions, but we expect Roaring bitmaps to be immutable for most of the life of an application. An array minimizes storage.

在我们的实际实现中，键值存储实现为两个数组：一个16位的整数数组表示键（short[] keys）和一个容器数组（Container[] values）。插入时，数组以标准方式动态扩展。尽管可以使用树结构来加快插入速度，但我们预计Roaring位图在应用程序的大部分生命周期中都是不可变的，数组最大限度地减少了内存需求。

> In a system such as Druid, the bitmaps are created, stored on disk and then memory-mapped as needed. When we serialize the bitmaps, we interleave with the 16-bit keys, the cardinalities of the corresponding containers: cardinalities are stored as 16-bit values (indicating the cardinality minus one). If needed, we also use an uncompressed bitmap containing at least one bit per container to indicate whether the corresponding container is a run container.

像Druid这样的系统，创建位图，存储在磁盘上，然后根据需要进行内存映射。序列化位图时，16位的键值和对应容器的基数交错存储，基数存储为16位值（表示基数减去1）。如果需要，我们还使用一个压缩的位图，其中每个容器至少用一位，以指示相应的容器是否为行程容器。

> ==TODO==： keys 和 基数是如何存储的？

> The structure of each container is straightforward (by design):
>
> - A bitmap container is an object made of 1024 64-bit words (using 8 kB) representing an uncompressed bitmap, able to store all sets of 16-bit integers. The container can be serialized as an array of 64-bit words. We also maintain a counter to record how many bits are set to 1. 
>
>    **In some cases**, the range of values might not cover the full range [0, 216) and a smaller bitmap might be sufficient—thus improving compression. However, the bitmap containers would then need to grow and shrink dynamically. For simplicity, we use fixed-size bitmap containers. 
>
>    **Counting the number of 1-bits** in a word can be relatively expensive if done naïvely, but modern processors have bit-count instructions—such as popcnt for x64 processors and cnt for the 64-bit ARM architecture—that can do this count using sometimes as little as a single clock cycle. According to our tests, using dedicated processor instructions can be several times faster than using either tabulation or other conventional alternatives [20]. Henceforth, we refer to such a function as bitCount: it is provided in Java as the Long.bitCount intrinsic. We assume that the platform has a fast bitCount function.
> - An array container is an object containing a counter keeping track of the number of integers,followed by a packed array of sorted 16-bit unsigned integers. It can be serialized as an array of 16-bit values. 
>
>    **We implement array** containers as dynamic arrays that grow their capacity using a standard approach. That is, we keep a count of the used entries in an underlying array that has typically some excess capacity. When the array needs to grow beyond its capacity, we allocate a larger array and copy the data to this new array. Our allocation heuristic is as follow: when the capacity is small (fewer than 64 entries), we double the capacity; when the capacity is moderate (between 64 and 1067 entries), we multiply the capacity by 3/2; when the capacity is large (1067 entries and more), we multiply the capacity by 5/4. Furthermore, we never allocate more than the maximum needed (4096) and if we are within one sixteenth of the maximum (> 3840), then we allocate the maximum right away (4096) to avoid any future reallocation. A simpler heuristic where we double the capacity whenever it is insufficient would be faster, but it would allocate on average (over all possible sizes) a capacity that exceeds the size by 50% whereas the capacity exceeds the size by only 13% in our model. In this sense, we trade speed for reduced memory usage. When the array container is no longer expected to grow, the programmer can use a trim function to copy the data to a new array with no excess capacity.
> - Our new addition, the run container, is made of a packed array of pairs of 16-bit integers. The first value of each pair represents a starting value, whereas the second value is the length of a run. For example, we would store the values 11, 12, 13, 14, 15 as the pair 11, 4 where 4 means that beyond 11 itself, there are 4 contiguous values that follow. In addition to this packed array, we need to maintain the number of runs stored in the packed array. Like the array container, the run container is stored in a dynamic array. During serialization, we write out the number of runs, followed by the corresponding packed array. 
>
>    **Unlike an array or bitmap container**, a run container does not keep track of its cardinality; its cardinality can be computed on the fly by summing the lengths of the runs. In most applications, we expect the number of runs to be often small: the computation of the cardinality should not be a bottleneck. 
>
>    **However**, as part of the serialization process, the cardinality of the run container is computed and stored. Hence, if we access the Roaring+Run bitmaps in their serialized form (as memorymapped bitmaps), the cardinality of run containers is pre-computed.
>

每个容器的结构都很简单（按设计）：

- **位图容器**是由1024个64位字（8 kB）组成的对象，表示未压缩的位图，能够存储全体16位整数。 容器以64位字数组的方式序列化。 还维护了一个计数器来记录多少位被设为1。
  
  某些情况，值的范围不会覆盖整个区间[0,2^16^)，小位图就够了，可以提高压缩率。 但位图容器需要动态增长和缩小。简单起见，我们使用固定大小的位图容器。
  
  简单地计算一个字中1的位数可能相对比较昂贵，但现代处理器有位计数指令，如，用于x64的`popcnt`和用于64位ARM的`cnt`，这些指令有时只需一个时钟周期就可以完成计数。根据测试，使用专用处理器指令比查表法和或其他传统替代方案[20]快几倍。后面我们称这样的函数为`bitCount`，Java以内置函数的方式（**intrinsic**）提供：`Long.bitCount`。我们假设平台有快速的`bitCount`功能。
- **数组容器**包含一个计数器，用于跟踪容器内整数的个数；一个有序的16位无符号整数的数组，按16位整数数组的方式序列化。
  
  我们将数组容器实现为动态数组，使用标准方法增加容量。也就是说，我们在底层数组中保留已使用条目的计数，这个数组通常具有一些冗余的容量。当数组需要超出其容量时，我们会分配一个更大的数组，并将数据复制到这个新数组。我们启发式的分配方法如下：容量很小（少于64）时，加倍容量；容量适中时（64到1067之间），容量乘以3/2；容量很大（大于等于1067）时，容量乘以5/4。此外，容量永远不会超过所需的最大值（4096），在最大值的十六分之一之内（>3840）时，立即按最大值（4096）分配，以避免后续的重新分配。更简单的启发式方法是，每次容量不足时翻倍会更快，但会平均分配（在所有可能的大小上）超过50％的容量；在我们的模型中，仅分配超过大小13%容量。从这个意义上讲，我们用速度来交换内存。数组容器不再增长时，可使用`trim`函数将数据复制到一个没有多余容量的新数组中。
- 新增的行程容器由16位**整数数组**组成，每两个一对，每对第一个值表示起始值，第二个值表示行程长度。例如，我们将值11、12、13、14、15存储为11、4，其中4表示除11之外，后面还有4个连续的值。除了这个数组之外，我们还需维护数组里的**行程数**。与数组容器一样，行程容器使用动态数组。序列化时，先输出行程数，再输出数组。
  
  与数组或位图容器不同，行程容器不跟踪其基数；通过对所有行程的长度求和，可以动态计算出其基数，预计在大多数应用程序中，行程数量通常很小，基数计算不应该成为瓶颈。
  
  但序列化时，会计算并存储行程容器的基数。因此，如果我们以序列化形式访问Roaring 行程位图（作为内存映射位图）时，行程容器的基数是预先计算好的。

> When starting from an empty Roaring bitmap, if a value is added, an array container is created. When inserting a new value in an array container, if the cardinality exceeds 4096, then the container is transformed into a bitmap container. On the other hand, if a value is removed from a bitmap container so that its size falls to 4096 integers, then it is transformed into an array container. Whenever a container becomes empty, it is removed from the top-level key-value structure along with the corresponding key.

从空的Roaring位图开始时，新增一个值，则会创建一个数组容器。向数组容器插入新值时，如果基数超过4096，则将容器转换为位图容器。另一方面，如果从位图容器中删除一个值，使其大小降至4096个整数，则将其转换为数组容器。每当容器变空时，它将与相应的键一起从顶层键值结构中删除。

> Thus, when first creating a Roaring bitmap, it is usually made of array and bitmap containers. Runs are not compressed. Upon request, the storage of the Roaring bitmap can be optimized using the runOptimize function. This triggers a scan through the array and bitmap containers that converts them, if helpful, to run containers. In a given application, this might be done prior to storing the bitmaps as immutable objects to be queried. Run containers may also arise from calling a function to add a range of values.

因此，在首次创建Roaring位图时，它通常由数组和位图容器组成，连续的整数不会压缩。 根据请求，可以使用`runOptimize`函数优化Roaring位图的存储。这会触发对数组和位图容器的扫描，如果有帮助，这些容器将会被转换为行程容器。在给定的应用程序中，可以在位图做为不可变对象、用于查询之前完成优化。调用函数添加一个区间内的值，也可能回产生行程容器。

> To decide the best container type, we are motivated to minimize storage. In serialized form, a run container uses 2 + 4r bytes given r runs, a bitmap container always uses 8192 bytes and an array container uses 2c + 2 bytes, where c is the cardinality. Therefore, we apply the following rules:

为了确定最佳容器类型，我们要最小化存储。在序列化形式中，给定r个行程，行程容器使用**2 + 4r**字节；位图容器总是使用**8192**字节，数组容器使用**2c + 2**字节，其中c是基数。因此，我们采用以下规则：

> - **All array containers** are such that they use no more space than they would as a bitmap container: they contain no more than 4096 values. 
> - **Bitmap containers** use less space than they would as array containers: they contain more than 4096 values.
> - **A run container** is only allowed to exist if it is smaller than either the array container or the bitmap container that could equivalently store the same values. If the run container has cardinality greater than 4096 values, then it must contain no more than **⌈(8192 − 2)/4⌉ = 2047** runs. If the run container has cardinality no more than 4096, then the number of runs must be less than half the cardinality.
>

 - **所有数组容器**使用的空间都不会超过位图容器使用的空间：它们包含的值不超过4096。

 - **位图容器**中每个整数使用的空间比数组容器少：它们包含4096个以上的值。

 - **行程容器**允许存在的情况，可以等效存储相同的值，但使用的空间小于数组或位图容器时。如果行程容器的基数大于4096，则它的行程数不能超过**⌈(8192 − 2)/4⌉ = 2047** 。如果行程容器的基数不超过4096，则行程数必须小于基数的一半。

> **Counting the number of runs** A critical step in deciding whether an array or bitmap container should be converted to a run container is to count the number of runs of consecutive numbers it contains. For array containers, we count this number by iterating through the 16-bit integers and comparing them two by two in a straightforward manner. Because array containers have at most 4096 integers, this computation is expected to be fast. For bitmap containers, Algorithm 1 shows how to compute the number of runs. We can illustrate the core operation of the algorithm using a single 32-bit word containing 6 runs of consecutive ones:

**计算行程数**：决定是否应将数组或位图容器转换为行程容器的关键步骤是，它包含多少个**连续整数**。对于数组容器，我们通过迭代16位整数数组，并以简单的方式逐个比较它们来计算这个数字。 因为数组容器最多有4096个整数，所以预计这个计算会很快。对于位图容器，算法1显示如何计算行程个数，我们可以用包含6组连续整数的32位字来说明算法的核心操作：

```shell
                Ci = 000111101111001011111011111000001,
            Ci ≪ 1 = 001111011110010111110111110000010,
(Ci ≪ 1) ANDNOT Ci = 001000010000010100000100000000010.
```

> We can verify that bitCount((Ci ≪ 1) ANDNOT Ci) = 6, that is, we have effectively computed the number of runs. In the case where a run continues up to the left-most bit, and does not continue in the next word, it does not get counted, but we add another term ((Ci ≫ 63) ANDNOT Ci+1 when using 64-bit words) to check for this case. We use only a few instructions for each word. Nevertheless, the computation may be expensive—exceeding the cost of computing the union or intersection between two bitmap containers. Thus, instead of always computing the number of runs exactly, we rely on the observation that no bitmap container with more than 2047 runs should be converted. As soon as we can produce a lower bound exceeding 2047 on the number of runs, we can stop. An exact computation of the number of runs is important only when our lower bound is less than 2048. We found that a good heuristic is to compute the number of runs in blocks of 128 words using a function inspired by Algorithm 1. We proceed block by block. As soon as the number of runs exceeds the threshold, we conclude that converting to a run container is counterproductive and abort the computation of the number of runs. We could also have applied the optimization to array containers as well, stopping the count of the number of runs at 2047, but this optimization is likely less useful because array containers have small cardinality compared to bitmap containers. A further possible optimization is to omit the last term from the sum in line 5 of Algorithm 1, thus underestimating the number of runs, typically by a few percent, but by up to 1023 in the worst case. Computing this lower bound is nearly twice as fast as computing the exact count in our tests using a recent Intel processor (Haswell microarchitecture).

可以验证**bitCount((C~i~ ≪ 1) ANDNOT C~i~) = 6**，也就是说，我们已经有效地计算了行程个数。如果连续整数持续到最高有效位，但没在下一个字中继续，则丢失了一个计数。但是我们添加另一个算式（假设使用64位字，**(C~i~ ≫ 63) ANDNOT C~i+1~**）检查这种情况。每个字只需要使用几条指令，然而计算成本可能比叫高，超过计算两个位图容器之间并集或交集的成本。因此，并不是每次都精确地计算行程个数，而是依赖于这样的观察：行程个数超过2047的位图容器不应该被转换。一旦行程个数的下限超过2047，算法就可以停止了。只有当下限小于2048时，精确计算行程个数才重要。我们发现一个好的启发式方法来使用算法1中的函数：每次计数128个字（1KB）的行程个数，一块块地计算。一旦行程个数超过阈值，就得出结论：转换为行程容器会适得其反，中止计算。我们也可以将该优化应用于数组容器，在到达2047个行程个时停止，但这种优化可能不太有用，因为与位图容器相比，数组容器具有较小的基数。另一种可能的优化方法是从算法1第5行的求和中省略最后一项，少估行程个数，通常为百分之几，最坏的情况是最多少算1023，使用最近的英特尔处理器（Haswell微架构）测试，计算这个下限的速度几乎是精确计数的两倍。

```bash
# 算法1. 用于计算位图中行程个数的函数。
# 左移和右移运算符（≪ 和≫ ）将字中的所有位移动指定的位数，以零补位。
# 按照惯例，C≫63是该字最后一位的值（1或0）。我们使用按位AND NOT运算符。
1: input: bitmap B as an array 1024 64-bit integers, C1 to C1024.
2: output: the number of runs r
3: r ← 0
4: for i ∈ {1, 2, . . . , 1023} do
5:   r ← r + bitCount((Ci ≪ 1) ANDNOT Ci)+(Ci ≫ 63) ANDNOT Ci+1
6: r ← r + bitCount((C1024 ≪ 1) ANDNOT C1024)+C1024 ≫ 63
7: return r
```

> Efficient conversions between containers are generally straightforward, except for conversions from a bitmap container to another container type. Converting from a bitmap to an array container is reviewed in Chambi et al. [7]. As for the conversion to run containers, we use Algorithm 2 to extract runs from a bitmap. It is efficient as long as locating the least significant 1-bit in a word is fast. Thankfully, recent processors include fast instructions to find the index of the least significant 1-bit in a word or, equivalently, of the number of trailing zeros (bsf or tzcnt on x64 processors, rbit followed by clz on ARM processors). They are accessible in Java through the Long.numberOfTrailingZeroes intrinsic. To locate the index of the least significant 0-bit, we negate the word, and then seek the least significant 1-bit. Otherwise the algorithm relies on inexpensive bit-manipulation techniques.

除了从位图容器转换为其他容器类型之外，容器之间的有效转换通常很简单。Chambi等人回顾了从位图到数组容器的转换[7]。至于转换为行程容器，我们使用算法2从位图中提取连续整数。只要能快速定位一个字中为1的最低有效位，它就有效。庆幸的是，最近的处理器包含快速指令，可用于查找一个字中为1的最低有效位，或者等效于求**尾随零**的数量（x64上的bsf或tzcnt，ARM上的rbit，后面跟着clz）。在Java中，通过内在函数`Long.numberOfTrailingZeroes`访问这些指令。为了找到为0的最低有效位，我们对字求反，然后查找为1的最低有效位。否则，算法依赖于廉价的位操作技术。

```bash
# 算法2 将位图中为1的位转换为行程列表的算法。
# 我们假设两个补码的64位算术。使用按位AND和OR运算符
1: input: a bitmap B, as an array of 64-bit words C1 to C1024
2: output: an array S containing runs of 1-bits found in the bitmap B
3: Let S be an initially empty list
4: Let i ← 1
5: T ← C1
6: while i ≤ 1024 do
7:   if T = 0 then
8:      i ← i + 1
9:      T ← Ci
10:  else
11:    j ← index of least significant 1-bit in T (j ∈ [0, 64))
12:    x ← j + 64 × (i − 1)
13:    T ← T OR (T − 1)                # {all bits with indexes < j are set to 1}
14:    while i + 1 ≤ 1024 and T = 0xFFFFFFFFFFFFFFFF do
15:      i ← i + 1
16:      T ← Ci
17:    if T = 0xFFFFFFFFFFFFFFFF then
18:      y ← 64 × i = 65536            # {we have i = 1024}
19:    else
20:      k ← index of least significant 0-bit in T (k ∈ [0, 64))
21:      y ← k + 64 × (i − 1)
22:    append to S a run that goes from x (inclusively) to y (exclusively)
23:    T ← T AND T + 1                 # {all bits with indexes < k are set to 0}
24: return S
```
## 5. LOGICAL OPERATIONS

### 5.1Union and intersection

There are many necessary logical operations, but we present primarily the union and intersection.They are the most often used, and the most likely operations to cause performance bottlenecks.

An important algorithm for our purposes is the galloping intersection (also called exponential intersection) to compute the intersection between two sorted arrays of sizes c1, c2. It has complexity O(min(c1, c2) logmax(c1, c2)) [21]. In this approach, we pick the next available integer i from the smaller array and seek an integer at least as big in the larger array, looking first at the next available value, then looking twice as far, and so on, until we find an integer that is not smaller than i. We then use a binary search in the larger array to find the exact location of the first integer not lower than i. We call this process a galloping search, and repeat it with each value from the smaller array.

A galloping search makes repeated random accesses in a container, and it could therefore cause expensive cache misses. However, in our case, the potential problem is mitigated by the fact that all our containers fit in CPU cache.

Intersections between two input Roaring bitmaps start by visiting the keys from both bitmaps, starting from the beginning. If a key is found in both input bitmaps, the corresponding containers are intersected and the result (if non-empty) is added to the output. Otherwise, we advance in the bitmap corresponding to the smallest key, up to the next key that is no smaller than the key of the other bitmap, using galloping search.When one bitmap runs out of keys, the intersection terminates.

Unions between Roaring data structures are handled in the conventional manner: we iterate through the keys in sorted order; if a key is in both input Roaring bitmaps, we merge the two containers, add the result to the output and advance in the two bitmaps. Otherwise, we clone the container corresponding to the smaller key, add it to the output and advance in this bitmap. When one bitmap runs out of keys, we append all the remaining content of the other bitmap to the output.

Though we do not use this technique, instead of cloning the containers during unions, we could use a copy-on-write approach whereas a reference to container is stored and used, and a copy is only made if an attempt is made to modify the container further. This approach can be implemented by adding a bit vector containing one bit per container. Initially, this bit is set to 0, but when the container cannot be safely modified without making a copy, it is set to 1. Each time the container needs to be modified, this bit needs to be checked. Whether the copy-on-write approach is worth the added complexity is a subject for future study. However, container cloning was never found to a significant computational bottleneck in the course of our development. It is also not clear whether there are applications where it would lead to substantial reduction of the memory usage. In any case, merely copying a container in memory can be several times faster than computing the union between two containers: copying containers is unlikely to be a major bottleneck.

We first briefly review the logical operations between bitmap and array containers, referring the reader to Chambi et al. [7] for algorithmic details.

- **Bitmap vs Bitmap**: To compute the intersection between two bitmaps, we first compute the cardinality of the result using the bitCount function over the bitwise AND of the corresponding pairs of words. If the intersection exceeds 4096, we materialize a bitmap container by recomputing the bitwise AND between the words and storing them in a new bitmap container. Otherwise, we generate a new array container by, once again, recomputing the bitwise ANDs, and iterating over their 1-bits. We find it important to first determine the right container type as, otherwise, we would sometimes generate the wrong container and then have to convert it—an expensive process. The performance of the intersection operation between two bitmaps depends crucially on the performance of the bitCount function.

  ==**A union between**== two bitmap containers is straightforward: we execute the bitwise OR between all pairs of corresponding words. There are 1024 words in each container, so 1024 bitwise OR operations are needed. At the same time, we compute the cardinality of the result using the bitCount function on the generated words.

- **Bitmap vs Array**: The intersection between an array and a bitmap container can be computed quickly: we iterate over the values in the array container, checking the presence of each 16-bit integer in the bitmap container and generating a new array container that has as much capacity as the input array container. The running time of this operation depends on the cardinality of the array container. Unions are also efficient: we create a copy of the bitmap and iterate over the array, setting the corresponding bits.

- **Array vs Array**: The intersection between two array containers is always a new array container.We allocate a new array container that has its capacity set to the minimum of the cardinalities of the input arrays. When the two input array containers have similar cardinalities c1 and c2 (c1/64 < c2 < 64c1), we use a straightforward merge algorithm with algorithmic complexity O(c1 + c2), otherwise we use a galloping intersection with complexity O(min(c1, c2) logmax(c1, c2)) [21]. We arrived at this threshold (c1/64 < c2 < 64c1) empirically as a reasonable choice, but it has not been finely tuned. 

  ==**For unions**==, if the sum of the cardinalities of the array containers is 4096 or less, we merge the two sorted arrays into a new array container that has its capacity set to the sum of the cardinalities of the input arrays. Otherwise, we generate an initially empty bitmap container. Though we cannot know whether the result will be a bitmap container (i.e., whether the cardinality is larger than 4096), as a heuristic, we suppose that it will be so. Iterating through the values of both arrays, we set the corresponding bits in the bitmap to 1. Using the `bitCount` function, we compute cardinality, and then convert the bitmap into an array container if the cardinality is at most 4096.

  ==**Alternatively**==, we could be more conservative and predict the cardinality of the union on the assumption that the two containers have independently distributed values over the whole chunk range (2^16^ values). Besides making the code slightly more complicated, it is not likely to change the performance characteristics, as the naïve model is close to an independencebased model. Indeed, under such a model the expected cardinality of the intersection would be $\frac{c_1}{2^{16}}\times\frac{c_2}{2^{16}}\times2^{16} = \frac{c_1c_2}{2^{16}}$. The expected cardinality of the union would be $c_1+c_2-\frac{c_1c_2}{2^{16}}$. The maximal threshold for an array container is 4096, so we can set $c_1+c_2-\frac{c_1c_2}{2^{16}} = 4096$ and solve for $ c_1 $ as a function of $ c_2 : c_1 = \frac{2^{16}(4096-c_2)}{2^{16}-c_2} $. In contrast, our simplistic model predicts a cardinality of $c_1 + c_2$ and thus a threshold at $c_1 = 4096 − c_2$. However, since $c_2 \leq 4096$, we have that $\frac{2^{16}}{2^{16}-c_2} = 1 + \frac{c_2}{2^{16}-c_2} \leq 1 + \frac{4096}{2^{16}-4096} = 1.0667$. That is, for any fixed value of one container (c~2~ here), the threshold on the cardinality of the other container, beyond which we predict a bitmap container, is at most 6.67% larger under an independence-based estimate, compared with our naïve approach.

Given array and bitmap containers, we need to have them interact with run containers. For this purpose, we introduced several new algorithms and heuristics.

- **Run vs Run**: When computing the intersection between two run containers, we first produce a new run container by a simple intersection algorithm. This new run container has its capacity set to the sum of the number of runs in both input containers. The algorithm starts by considering the first run, in each container. If they do not overlap, we advance in the container where the run occurs earlier until they do overlap, or we run out of runs in one of the containers. When we run out of runs in either container, the algorithm terminates. When two runs overlap, we always output their intersection. If the two runs end at the same value, then we advance in the two run containers. Otherwise, we advance only in the run container that ends first. Once we have computed the answer, after exhausting the runs in at least one container, we check whether the run container should be converted to either a bitmap (if it has too many runs) or to an array container (if its cardinality is too small compared to the number of runs). 

  ==**The union algorithm**== is also conceptually simple. We create a new, initially empty, run container that has its capacity set to the sum of the number of runs in both input containers. We iterate over the runs, starting from the first run in each container. Each time, we pick a run that has a minimal starting point. We append it to the output either as a new run, or as an extension of the previous run. We then advance in the container where we picked the run. Once a container has no more runs, all runs remaining in the other container are appended to the answer. After we have computed the resulting run container, we convert the run container into a bitmap container if too many runs were created. Checking whether such a conversion is needed is fast, since it can be decided only by checking the number of runs. There is no need to consider conversion to an array container, because every run present in the original inputs is either present in its entirety, or as part of an even larger run. Thus the average run length (essentially our criterion for conversion) is at least as large as in the input run containers.

- **Run vs Array**: The intersection between a run container and an array container always outputs an array container. This choice is easily justified: the result of the intersection has cardinality no larger than the array container, and it cannot contain more runs than the array container. We can allocate a new array container that has its capacity set to the cardinality of the input array container. Our algorithm is straightforward. We iterate over the values of the array, simultaneously advancing in the run container. Initially, we point at the first value in the array container and the first run in the run container. While the run ends before the array value, we advance in the run container. If the run overlaps the array value, the array value is included in the intersection, otherwise it is omitted.

  ==**Determining the best container**== for storing the union between a run container and an array is less straightforward. We could process the run container as if it were an array container, iterating through its integers and re-use our heuristic for the union between two array containers. Unfortunately, this would always result in either an array or bitmap container. We found that it is often better to predict that the outcome of the union is a run container, and to convert the result to a bitmap container, if we must. Thus, we follow the heuristic for the union between two run containers, effectively treating the array container as a run container where all runs have length one. However, once we have computed the union, we must not only check whether to convert the result to a bitmap container, but also, possibly, to an array container. This check is slightly more expensive, as we must compute the cardinality of the result.

- **Run vs Bitmap**: The intersection between a run container and a bitmap container begins by checking the cardinality of the run container. If it is no larger than 4096, then we create an initially empty array container.We then iterate over all integers contained in the run container, and check, one by one, whether they are contained in the bitmap container: when an integer is found to be in the intersection, it is appended to the output in the array container. The running time of this operation is determined by the cardinality of the run container. Otherwise, if the input run container is larger than 4096, then we create a copy of the input bitmap container. Using fast bitwise operations, we set to zero all bits corresponding to the complement of the run container (see Algorithm 3). We then check the cardinality of the result, converting to an array container if needed.

  ==**The union between**== a run container and a bitmap container is computed by first cloning the bitmap container.We then set to one all bits corresponding to the integers in the run container, using fast bitwise OR operations (see again Algorithm 3).

  ==**In some instances**==, the result of an intersection or union between two containers could be most economically represented as a run container, even if we generate an array or bitmap container. It is the case when considering the intersection or union between a run container and a bitmap container. We could possibly save memory and accelerate later computations by checking whether the result should be converted to a run container. However, this would involve keeping track of the number of runs—a relatively expensive process.

> ==TODO：==  算法3

Furthermore, we added the following optimization. Whenever we compute the union between a run container and any other container, we first check whether the run container contains a single run filling up the whole space of 16-bit values ([0, 216)). In that case, the union must be the other container and we can produce optimized code accordingly. The check itself can be computed in a few inexpensive operations. This simple optimization accelerates the possibly common case where there are extremely long runs of ones that span multiple containers.

The computation of the intersection is particularly efficient in Roaring when it involves a bitmap container and either an array container or a run container of small cardinality. It is also efficient when intersecting two array containers, with one having small cardinality compared to the other, as we use galloping intersections. Moreover, by design, Roaring can skip over entire chunks of integers, by skipping over keys that are present in only one of the two input bitmaps. Therefore, Roaring is well suited to the problem of intersecting a bitmap having a small cardinality with bitmaps having larger cardinalities. In contrast, RLE-based compression (as in WAH or Concise) offers fewer opportunities to skip input data.

The computation of the union between two Roaring bitmaps is particularly efficient when it involves run containers or array containers being intersected with bitmap containers. Indeed, these computations involve almost no branching and minimal data dependency, and they are therefore likely to be executed efficiently on superscalar processors. RLE-based compression often causes many data dependencies and much branching.

Another feature of Roaring is that some of these logical operations can be executed in place. In-place computations avoid unnecessary memory allocations and improve data locality.

- The union of a bitmap container with any other container can be written out in the input bitmap container. The intersection between two bitmap containers, or between a bitmap container and some run containers, can also be written out to an input bitmap container.
- Though array containers do not support in-place operations, we find it efficient to support in-place unions in a run container with respect to either another run container or an array container. In these cases, it is common that the result of the union is either smaller or not much larger than combined sizes of the inputs. The runs in a run container are stored in a dynamic array that grows as needed and typically has some excess capacity. So we first check whether it would be possible to store both the input run container and the output (whose size is bounded by the sum of the inputs). Otherwise, we allocate the necessary capacity. We then shift the data corresponding to the input run container from the beginning of the array to the end. That is, if the input run container had r runs, they would be stored in the first 4r bytes of an array, and we would copy them to the last 4r bytes of the array—freeing the beginning of the array. We write the result of the union at the beginning of the array, as usual. Thus, given enough capacity, this approach enables repeated unions to the same run container without new memory allocation. We trade the new allocation for a copy within the same array. Since our containers can fit in CPU cache, such a copy can be expected to be fast. We could, similarly, enable in-place intersections within run or array containers.

A common operation in applications is the aggregation of a long list of bitmaps. When the problem is to compute the intersection of many bitmaps, we can expect a naïve algorithm to work well with Roaring: we can compute the intersection of the first two bitmaps, then intersect the result with the third bitmap, and so forth. With each new intersection, the result might become smaller, and Roaring can often efficiently compute the intersection between bitmap having small cardinality and bitmaps having larger cardinalities, as already stated. Computing the union of many bitmaps requires more care. As already remarked in Chambi et al. [7], it is wasteful to update the cardinality each and every time when computing the union between several bitmap containers. Though the bitCount function is fast, it can still use a significant fraction of the running time: Chambi et al. [7] report that it reduces the speed by about 30%. Instead, we proceed with what we call a “lazy union”. We compute the union as usual, except that some unions between containers are handled differently:

- The union between a bitmap container and any other container type is done as usual, that is, we compute the resulting bitmap container, except that we do not attempt to compute the cardinality of the result. Internally, the cardinality is set to the flag value “-1”, indicating that the cardinality is currently unknown.
- When computing the union of a run container with an array container, we always output a run container or, if the number of runs is too great, a bitmap container—even when an array container might be smaller.

After the final answer is generated, we “repair it” by computing the cardinality of any bitmap container, and by checking whether any run container should be converted to an array container. For even greater speed, we could even make this repair phase optional and skip the computation of the cardinality of the bitmap containers.

We consider two strategies to compute the union of many bitmaps. One approach is a naïve two-by-two union: we first compute the union of the first two bitmaps, then the union of the result and the third bitmap and so forth, doing the computation in-place if possible. The benefit of this approach is that we always keep just one intermediate result in memory. In some instances, however, we can get better results with other algorithms. For example, we can use a heap: put all original bitmaps in a min-heap, and repeatedly poll the two smallest bitmaps, compute their union, and put them back in the heap, as long as the heap contains more than one bitmap. This approach may create many more intermediate bitmaps, but it can also be faster in some instances. To see why that must be the case, consider that the complexity of the union between two bitmaps of size B is O(B), generating a result thatmight be of size 2B. Thus, givenN bitmaps of size B, the naïve approach has complexity O(BN2), whereas the heap-based approach has complexity O(BN logN). However, this computational model, favourable to the heap-based approach, does not always apply. Indeed, suppose that the bitmaps are uncompressed bitmaps over the same range of values—as is sometimes the case when Roaring bitmaps are made of bitmap containers. In that case, the computation of a union between two bitmaps of size B has complexity O(B), and the output has size B. We have that both algorithms, naïve and heap-based, have the same optimal O(BN) complexity. However, the naïve algorithm has storage requirements in O(B) whereas the heap-based algorithm’s storage requirements are in O(BN), indicating that the latter might have worse performance. We expect that whether one algorithm or the other has better running time is data and format dependent, but in actual application, it might be advantageous to use the naïve algorithm if one wishes to have reduced memory usage.

### 5.2 Other Operations

We make available a single software library encompassing both the original Roaring and Roaring+Run (see § 6.2). We provide optimized implementations of many useful functions.

A logically complete set of operations enables Roaring to be used, via the bit-slicing approach [22], to realize arbitrary Boolean operations. Thus, our Roaring software supports negation, although it uses the more general flip approach of Java’s BitSet class, wherein negation occurs only within a range. Besides adding more flexibility, this approach means that there is no need to know the actual universe size, in the case when the bitset is intended to be over a smaller universe than 0 to 232 − 1. The flip function in Roaring first determines the affected containers. The containers found are flipped; those becoming empty are removed. Missing containers that fall entirely within the range are replaced by “full” run containers (a single run from 0 to 216). When applied to an array container, the flip function uses a binary search to first determine all values contained in the range. We can then determine whether the result of the flip should be an array container or a bitmap container. If the output is an array container, then the flip can be done inplace, assuming that there is enough capacity in the container, otherwise a new buffer is allocated. If the output must be a bitmap container, the array container is converted to a bitmap container and flipped. Flipping a bitmap container can be done in-place, if needed, using a procedure similar to Algorithm 3. In flipping a run container, we always first compute the result as a run container. When the container’s capacity permits, an in-place flip avoids memory allocation. This should be a common situation, because flipping increases the number of runs by at most one. Thus, there is a capacity problem only when the number of runs increases and the original runs fit exactly within the array. A simple case-based analysis reveals whether the flipped run container requires an extra run. Suppose we flip the range [a, b) within a container. We can prove that the number of runs is increased by one if and only if the following two conditions hold:

- both a − 1 and a are contained in the run container, or both values are missing from the run container,
- both b − 1 and b are contained in the run container, or both values are missing from the run container.

After computing the new run container, we check whether it needs to be converted to a bitmap or array container. Conversion to a bitmap container is rare, as it occurs only when the number of runs has increased from 2047 to 2048.

Although adding negation to intersection and union gives us logical completeness, efficiency is gained by supporting other Boolean operations directly. For instance, our Roaring software provides an XOR operation that is frequently useful for bit-sliced arithmetic[16] and that provides symmetric difference between sets. Roaring also provides an AND NOT operation that implements set difference, which is important in some applications. The implementation of the symmetric difference is similar to that of the union, with the added difficulty that the cardinality might be lower than that of either of the two inputs. The implementation of the difference is similar to that of the intersection.

Our software also supports fast rank and select functions: rank queries count the number of values present in a range whereas select queries seek the ith value. These queries are accelerated because array and bitmap containersmaintain their cardinality as a value that can be quickly queried. Moreover, when accessing serialized bitmaps (e.g., through memory-mapped files), the cardinality of all containers is readily available.

Our software also supports the ability to add or remove all values in an interval, to check efficiently whether two bitmaps intersect (without computing the intersection) and so forth. We allow users to quickly iterate over the values contained in a Roaring bitmap. Internally, these iterators are implemented by on-the-fly creation of iterators over containers. We also found it useful to apply the flyweight design patterns and to allow programmers to reuse iterator objects—to minimize memory allocation [23].
