# ColumnStores vs. RowStores: How Different Are They Really?

## ABSTRACT

There has been a significant amount of excitement and recent work on column-oriented database systems (“column-stores”). These database systems have been shown to perform more than an order of magnitude better than traditional row-oriented database systems (“row-stores”) on analytical workloads such as those found in data warehouses, decision support, and business intelligence applications. The elevator pitch behind this performance difference is straightforward: column-stores are more I/O efficient for read-only queries since they only have to read from disk (or from memory) those attributes accessed by a query.

This simplistic view leads to the assumption that one can obtain the performance benefits of a column-store using a row-store: either by vertically partitioning the schema, or by indexing every column so that columns can be accessed independently. In this paper, we demonstrate that this assumption is false. We compare the performance of a commercial row-store under a variety of different configurations with a column-store and show that the row-store performance is significantly slower on a recently proposed data warehouse benchmark. We then analyze the performance difference and show that there are some important differences between the two systems at the query executor level (in addition to the obvious differences at the storage layer level). Using the column-store, we then tease apart these differences, demonstrating the impact on performance of a variety of column-oriented query execution techniques, including vectorized query processing, compression, and a new join algorithm we introduce in this paper. We conclude that while it is not impossible for a row-store to achieve some of the performance advantages of a column-store, changes must be made to both the storage layer and the query executor to fully obtain the benefits of a column-oriented approach.

# 5. COLUMN ORIENTED EXECUTION

Now that we’ve presented our row-oriented designs, in this section, we review three common optimizations used to improve performance in column-oriented database systems, and introduce the invisible join.

> 现在，我们已经介绍了面向行的设计，在本节中，我们将回顾三种常用的优化方法，用于提高面向列的数据库系统的性能，并介绍了**<u>==隐形连接==</u>**。

### 5.1 Compression

Compressing data using column-oriented compression algorithms and keeping data in this compressed format as it is operated upon has been shown to improve query performance by up to an order of magnitude [4]. Intuitively, data stored in columns is more compressible than data stored in rows. Compression algorithms perform better on data with low information entropy (high data value locality). Take, for example, a database table containing information about customers (name, phone number, e-mail address, snail-mail address, etc.). Storing data in columns allows all of the names to be stored together, all of the phone numbers together, etc. Certainly phone numbers are more similar to each other than surrounding text fields like e-mail addresses or names. Further, if the data is sorted by one of the columns, that column will be super-compressible (for example, runs of the same value can be run-length encoded).

But of course, the above observation only immediately affects compression ratio. Disk space is cheap, and is getting cheaper rapidly (of course, reducing the number of needed disks will reduce power consumption, a cost-factor that is becoming increasingly important). However, compression improves performance (in addition to reducing disk space) since if data is compressed, then less time must be spent in I/O as data is read from disk into memory (or from memory to CPU). Consequently, some of the “heavierweight” compression schemes that optimize for compression ratio (such as Lempel-Ziv, Huffman, or arithmetic encoding), might be less suitable than “lighter-weight” schemes that sacrifice compression ratio for decompression performance [4, 26]. In fact, compression can improve query performance beyond simply saving on I/O. If a column-oriented query executor can operate directly on compressed data, decompression can be avoided completely and performance can be further improved. For example, for schemes like run-length encoding – where a sequence of repeated values is replaced by a count and the value (e.g., 1; 1; 1; 2; 2 ! 1Å~3; 2Å~2) – operating directly on compressed data results in the ability of a query executor to perform the same operation on multiple column values at once, further reducing CPU costs.

Prior work [4] concludes that the biggest difference between the cases where a column is sorted (or secondarily sorted) and there are consecutive repeats of the same value in a column. In a columnstore, it is extremely easy to summarize these value repeats and operate directly on this summary. In a row-store, the surrounding data from other attributes significantly complicates this process. Thus, in general, compression will have a larger impact on query performance if a high percentage of the columns accessed by that query have some level of order. For the benchmark we use in this paper, we do not store multiple copies of the fact table in different sort orders, and so only one of the seventeen columns in the fact table can be sorted (and two others secondarily sorted) so we expect compression to have a somewhat smaller (and more variable per query) effect on performance than it could if more aggressive redundancy was used。

### 5.2  Late Materialization

In a column-store, information about a logical entity (e.g., a person) is stored in multiple locations on disk (e.g. name, e-mail address, phone number, etc. are all stored in separate columns), whereas in a row store such information is usually co-located in a single row of a table. **However, most queries access more than one attribute from a particular entity. Further, most database output standards (e.g., ODBC and JDBC) access database results entity-at-a-time (not column-at-a-time). Thus, at some point in most query plans, data from multiple columns must be combined together into <u>rows of information</u> about an entity. Consequently, this join-like materialization of tuples (also called “tuple construction”) is an extremely common operation in a column store**.

Naive column-stores [13, 14] store data on disk (or in memory) column-by-column, read in (to CPU from disk or memory) only those columns relevant for a particular query, construct tuples from their component attributes, and execute normal row-store operators on these rows to process (e.g., select, aggregate, and join) data. Although likely to still outperform the row-stores on data warehouse workloads, this method of constructing tuples early in a query plan (“early materialization”) leaves much of the performance potential of column-oriented databases unrealized.

More recent column-stores such as X100, C-Store, and to a lesser extent, Sybase IQ, choose to keep data in columns until much later into the query plan, operating directly on these columns. In order to do so, intermediate “position” lists often need to be constructed in order to match up operations that have been performed on different columns. ==Take, for example, a query that applies a predicate on two columns and projects a third attribute from all tuples that pass the predicates==. <u>In a column-store that uses late materialization, the predicates are applied to the column for each attribute separately and a list of positions (ordinal offsets within a column) of values that passed the predicates are produced</u>. Depending on the predicate selectivity, this list of positions can be represented as a simple array, a bit string (where a 1 in the ith bit indicates that the ith value passed the predicate) or as a set of ranges of positions. These position representations are then intersected (if they are bit-strings, bit-wise AND operations can be used) to create a single position list. This list is then sent to the third column to extract values at the desired positions.

The advantages of late materialization are four-fold. ==First, selection and aggregation operators tend to render the construction of some tuples unnecessary (if the executor waits long enough before constructing a tuple, it might be able to avoid constructing it altogether)==. Second, if data is compressed using a column-oriented compression method, it must be decompressed before the combination of values with values from other columns. This remove the advantages of operating directly on compressed data described above. ==Third, cache performance is improved when operating directly on column data, since a given cache line is not polluted with surrounding irrelevant attributes for a given operation (as shown in PAX [6])==. Fourth, the block iteration optimization described in the next subsection has a higher impact on performance for fixed length attributes. In a row-store, if any attribute in a tuple is variable width, then the entire tuple is variable width. In a late materialized column-store, fixed-width columns can be operated on separately.

> 在列存储中，有关逻辑实体（例如人）的信息存储在磁盘上的多个位置（如，姓名、电子邮件地址、电话号码等都存储在单独的列中），而在行存储中，这些信息通常位于表的一行中。**但大多数查询从特定实体访问多个属性。并且大多数数据库输出标准（例如，ODBC 和 JDBC）获取数据库结果是一次读取一行记录（而不是一次访问一列）**。因此，大多数查询计划在某个时刻，必须将来自多列的数据按行组合成一个个的实体。所以，这种类似**<u>联接</u>**的<u>**==元组物化==**</u>（也称为“元组构造”）是列存储中极为常见的操作。
>
> 简单的列存储[13，14]逐列将数据存储在磁盘（或内存）中，只将与特定查询相关的列从磁盘或内存中读入 **CPU**，把这些列构造为元组， 并在元组上用常规的**基于行的运算符**（如选择、聚合和联接）以处理数据。 尽管在数据仓库工作负载上仍可能胜过行存储，但**在查询计划**早期构造（及时物化）元组的方法，无法实现列存数据库的许多性能潜力。
>
> 较新的列存储，例如X100，C-Store 和 Sybase IQ（范围要小一些）则直接操作**==列==**，它们选择将数据保留在列中，直到后期<u>列数据</u>才进入查询计划。为此，通常需要构造**中间位置列表**，以匹配不同列上执行的操作。例如，一个查询在两列上应用谓词，并从所有谓词计算为真的元组中**选取**第三个属性。延迟物化的列存，分别在每个列上计算谓词，并对谓词计算为真的列生成位置列表（列中的顺序偏移量）。根据谓词的选择性，位置列表可以表示为一个简单的数组，一个位串（其中第 i 位为 1 表示第 i 位的列谓词计算为真）或一组位置范围。再对这些位置列表做交集运算（如果是位串，则可以使用按位 `AND` 操作）以创建单个位置列表。 然后将此位置列表发送到第三列以提取所需位置的列值。
>
> 延迟物化有四个好处。第一，过滤和聚合运算符趋向于使一些元组的构造变得不必要（<u>==如果执行器在构造元组之前等待足够长的时间，则可能避免完全构造它==</u>）。第二，如果使用面向列的压缩方法压缩数据，则必须先解压才能构建元组，那么上述直接操作压缩数据的优点就没了。第三，直接对列数据进行操作可提高缓存性能，因为给定的**==CPU 缓存行==**不会被<u>==给定操作周围无关属性==</u>所污染（如PAX [6]所示）。第四，下一小节描述的**==块迭代优化==**对**==固定宽度列==**的性能有更大的影响。**元组中只要有一个属性**是可变宽度的，那么在行存储中整个元组都是可变宽度的；在延迟物化的列存储中，则可以单独操作固定宽度的列。

### 5.3 Block Iteration

In order to process a series of tuples, row-stores first iterate through each tuple, and then need to extract the needed attributes from these tuples through a tuple representation interface [11]. In many cases, such as in MySQL, this leads to tuple-at-a-time processing, where there are 1-2 function calls to extract needed data from a tuple for each operation (<u>which if it is a small expression or predicate evaluation is low cost compared with the function calls</u>) [25].

Recent work has shown that some of the per-tuple overhead of tuple processing can be reduced in row-stores if blocks of tuples are available at once and operated on in a single operator call [24, 15], and this is implemented in IBM DB2 [20]. In contrast to the case by case implementation in row-stores, in all column-stores (that we are aware of), blocks of values from the same column are sent to an operator in a single function call. Further, no attribute extraction is needed, and if the column is fixed-width, these values can be iterated through directly as an array. Operating on data as an array not only minimizes per-tuple overhead, but it also exploits potential for parallelism on modern CPUs, as loop-pipelining techniques can be used [9].

> 要处理一组元组，行存储首先遍历每个元组，再通过**==元组接口==**提取所需的属性 [11]。这会导致在很多情况下，比如在MySQL中，一次只能处理一个元组，其中从元组中为每个操作提取所需数据，有 1-2 个函数调用（<u>如果是小表达式或是计算谓词，则与函数调用相比成本较低</u>）[25]。
>
> 最近的研究表明，在单个运算符的调用中[24，15]，如果每次处理的是**元组块**，则可以减少行存中处理每个元组的一些开销，这已在 IBM DB2 [20] 中实现。与行存储一个接一个的实现不同，在（我们知道的）所有列存储中，是在单个函数调用中将同一列中的**==值块==**发送给运算符。此外，不用提取列值，并且如果列是固定宽度的，则可以将这些值直接作为数组迭代。以数组的形式操作数据不仅可以最小化每元组的开销，而且因为可以使用**==循环流水线技术==**[9]，所以还利用了现代 CPU 的并行潜力。

### 5.4 Invisible Join

Queries over data warehouses, particularly over data warehouses modeled with a star schema, often have the following structure: Restrict the set of tuples in the fact table using selection predicates on one (or many) dimension tables. Then, perform some aggregation on the restricted fact table, often grouping by other dimension table attributes. ==Thus, joins between the fact table and dimension tables need to be performed for each selection predicate and for each aggregate grouping.== A good example of this is Query 3.1 from the Star Schema Benchmark.

```SQL
SELECT c.nation, s.nation, d.year,
       sum(lo.revenue) as revenue
FROM customer AS c, lineorder AS lo,
     supplier AS s, dwdate AS d
WHERE lo.custkey = c.custkey
  AND lo.suppkey = s.suppkey
  AND lo.orderdate = d.datekey
  AND c.region = 'ASIA'
  AND s.region = 'ASIA'
  AND d.year >= 1992 and d.year <= 1997
GROUP BY c.nation, s.nation, d.year
ORDER BY d.year asc, revenue desc;
```

This query finds the total revenue from customers who live in Asia and who purchase a product supplied by an Asian supplier between the years 1992 and 1997 grouped by each unique combination of the nation of the customer, the nation of the supplier, and the year of the transaction.

The traditional plan for executing these types of queries is to <u>==pipeline joins==</u> in order of predicate selectivity. For example, if `c.region = 'ASIA'` is the most selective predicate, the join on `custkey` between the `lineorder` and `customer` tables is performed first, filtering the `lineorder` table so that only orders from customers who live in Asia remain. As this join is performed, the `nation` of these customers are added to the joined `customer-order` table. These results are pipelined into a join with the `supplier` table where the `s.region = 'ASIA'` predicate is applied and `s.nation` extracted, followed by a join with the `dwdate` table and the `year` predicate applied. The results of these joins are then grouped and aggregated and the results sorted according to the `ORDER BY` clause.

An alternative to the traditional plan is the late materialized join technique [5]. In this case, a predicate is applied on the `c.region` column (`c.region = 'ASIA'`), and the customer key of the customer table is extracted at the positions that matched this predicate. These keys are then joined with the customer key column from the fact table. The results of this join are two sets of positions, one for the fact table and one for the dimension table, indicating which pairs of tuples from the respective tables passed the join predicate and are joined. In general, at most one of these two position lists are produced in sorted order (the outer table in the join, typically the fact table). Values from the `c.nation` column at this (out-of-order) set of positions are then extracted, along with values (using the ordered set of positions) from the other fact table columns (supplier key, order date, and revenue). Similar joins are then performed with the `supplier` and `dwdate` tables.

Each of these plans have a set of disadvantages. In the first (traditional) case, constructing tuples before the join precludes all of the late materialization benefits described in Section 5.2. In the second case, values from dimension table group-by columns need to be extracted in out-of-position order, which can have significant cost [5].

As an alternative to these query plans, we introduce a technique we call the *invisible join* that can be used in column-oriented databases for foreign-key/primary-key joins on star schema style tables. It is a late materialized join, but minimizes the values that need to be extracted out-of-order, thus alleviating both sets of disadvantages described above. It works by rewriting joins into predicates on the foreign key columns in the fact table. These predicates can be evaluated either by using a hash lookup (in which case a hash join is simulated), or by using more advanced methods, such as a technique we call between-predicate rewriting, discussed in Section 5.4.2 below.

By rewriting the joins as selection predicates on fact table columns, they can be executed at the same time as other selection predicates that are being applied to the fact table, and any of the predicate application algorithms described in previous work [5] can be used. For example, each predicate can be applied in parallel and the results merged together using fast bitmap operations. Alternatively, the results of a predicate application can be pipelined into another predicate application to reduce the number of times the second predicate must be applied. Only after all predicates have been applied are the appropriate tuples extracted from the relevant dimensions (this can also be done in parallel). By waiting until all predicates have been applied before doing this extraction, the number of out-of-order extractions is minimized.

The invisible join extends previous work on improving performance for star schema joins [17, 23] that are **==reminiscent==** of semijoins [8] by taking advantage of the column-oriented layout, and rewriting predicates to avoid hash-lookups, as described below.

> 数据仓库的查询，特别是星型建模的数仓查询，通常有以下结构：在一个（或多个）维度表上使用<u>选择谓词</u>来限制事实表中的元组集。然后，对受限事实表执行一些聚合，通常按其他维度表上的属性分组。==因此，事实表和维度表之间的联接需要针对每个选择谓词和每个聚合分组进行==。一个很好的例子是来自 Star Schema Benchmark 的查询 3.1。
>
> 此查询查找 1992 年至 1997 年期间，在亚洲居住的客户，购买亚洲供应商产品的总收入，并按客户的国家、供应商国家和交易年份的唯一组合进行分组。
>
> 传统计划执行这些类型查询是按照<u>谓词选择性</u>的顺序进行<u>==流水性联接==</u>。例如，如果 `c.region ='ASIA'` 是最有选择性的谓词，则首先执行`lineorder` 表和 `customer` 表之间的 `custkey` 联接，过滤 `lineorder`表，以便只保留居住在亚洲的客户的订单。执行此联接时，这些客户的 `nation` 被添加到联接后的 `customer-order` 表中。这些结果通过<u>==流水线==</u>与 `supplier` 表的联接，在这应用 `s.region ='ASIA'` 谓词并提取 `s.nation`，然后是和 `dwdate` 表的联接，这里用到 `year` 谓词。然后将联接的结果进行分组和汇总，并根据 `ORDER BY` 子句对结果进行排序。
>
> 一个替代传统计划的方案是延迟物化联接技术[5]。本例中，在 `c.region` 列上应用谓词 `c.region='ASIA'`，并在与该谓词匹配的位置提取 `customer` 表的主键。然后，这些主键与事实表中的表示客户的外键联接，联接的结果是两组位置，一组用于事实表，另一组用于维度表，表示来自各个表的那些元组通过了联接谓词，并被联接在一起。通常这两个位置列表最多有一个是有序（联接中的外表一般是事实表）。然后提取这些位置上（使用无序位置列表）的 `c.nation` 列、以及事实表（使用有序位置列表）其他列（供应商外键、订单日期和收入）的值。然后对 `supplier` 和 `dwdate` 表执行类似的联接。
>
> 两种执行计划有各自的缺点。第一种（传统的）方案，在联接之前构造元组排除了第 5.2 节中描述的延迟物化的所有好处。第二种方案，维度表中按列分组的值是无序提取的，这可能会有很大的成本[5]。
>
> 作为这些查询计划的替代方案，我们引入了一种称为<u>==隐式联接==</u>的技术，在列式数据库中，用于采用星形方式建模的表上的外键和主键之间的联接。它采用延迟的物化联接，但最小化需要无序提取的值，从而减轻了上述两组缺点。它的工作原理是将联接谓词重写为事实表上外键列的谓词。可以通过哈希查找（在这种情况下模拟哈希连接）或使用更高级的方法来计算这些谓词，例如下面 5.4.2 节讨论的 betwee-谓词重写。
>
> 通过将联接重写为事实表列上的选择谓词，它们可以与应用于事实表的其他选择谓词同时执行，并且能使用所有在[5]中描述的谓词计算算法。 例如，并行计算每个谓词，并使用快速位图操作将结果合并在一起。或者，可以将谓词计算的结果以流水线的方式用于另一个谓词计算中，以减少第二个谓词的计算次数。只有在计算了所有谓词之后，才会从相关维度提取适当的元组（这也可以并行完成）。等到计算完所有谓词之后再提取值，得以最小化无序提取的数量。
>
> **隐式联接**扩展了先前[17、23]有关提高**星型联接**性能的工作，这些工作通过利用面向列的布局==<u>使人联想到</u>==半联接[8]，并重写谓词以避免哈希查找，如下所述。

#### 5.4.1 Join Details

The invisible join performs joins in three phases. First, each predicate is applied to the appropriate dimension table to extract a list of dimension table keys that satisfy the predicate. These keys are used to build a hash table that can be used to test whether a particular key value satisfies the predicate (the hash table should easily fit in memory since dimension tables are typically small and the table contains only keys). An example of the execution of this first phase for the above query on some sample data is displayed in Figure 2.

> TODO: 图2

In the next phase, each hash table is used to extract the positions of records in the fact table that satisfy the corresponding predicate. This is done by probing into the hash table with each value in the foreign key column of the fact table, creating a list of all the positions in the foreign key column that satisfy the predicate. Then, the position lists from all of the predicates are intersected to generate a list of satisfying positions P in the fact table. An example of the execution of this second phase is displayed in Figure 3. Note that a position list may be an explicit list of positions, or a bitmap as shown in the example.

> TODO: 图3

<u>The third phase of the join uses the list of satisfying positions P in the fact table. For each column C in the fact table containing a foreign key reference to a dimension table that is needed to answer the query (e.g., where the dimension column is referenced in the select list, group by, or aggregate clauses), foreign key values from C are extracted using P and are looked up in the corresponding dimension table</u>. Note that if the dimension table key is a sorted, contiguous list of identifiers starting from 1 (which is the common case), then the foreign key actually represents the position of the desired tuple in dimension table. This means that the needed dimension table columns can be extracted directly using this position list (and this is simply a fast array look-up).

This direct array extraction is the reason (along with the fact that dimension tables are typically small so the column being looked up can often fit inside the L2 cache) why this join does not suffer from the above described pitfalls of previously published late materialized join approaches [5] where this final position list extraction is very expensive due to the out-of-order nature of the dimension table value extraction. Further, the number values that need to be extracted is minimized since the number of positions in P is dependent on the selectivity of the entire query, instead of the selectivity of just the part of the query that has been executed so far.

An example of the execution of this third phase is displayed in Figure 4. Note that for the date table, the key column is not a sorted, contiguous list of identifiers starting from 1, so a full join must be performed (rather than just a position extraction). Further, note that since this is a foreign-key primary-key join, and since all predicates have already been applied, there is guaranteed to be one and only one result in each dimension table for each position in the intersected position list from the fact table. This means that there are the same number of results for each dimension table join from this third phase, so each join can be done separately and the results combined (stitched together) at a later point in the query plan.

> TODO: 图 4

>隐式联接分为三个阶段。**==首先==**在对应维度表上计算每个谓词，得到满足该谓词的维度表主键列表。用这些主键构建哈希表，用于测试特定键值是否满足谓词（哈希表应该很容易放入内存，因为维度表通常很小，并且哈希表只包含键）。 图 2 是在一些示例数据执行上述查询的第一阶段的示例。
>
>**第二阶段**，通过哈希表获取事实表中满足相应谓词记录的位置：用事实表外键列中的每个值探查哈希表，并为满足谓词的外键列创建一张位置列表，每个哈希表对应一张位置列表。然后将所有的位置列表相交，生成事实表中满足所有谓词的位置 *P* 的列表。第二阶段执行示例参见图 3。请注意，位置列表可以是位置的显式列表，也可以是示例中所示的位图。
>
>第三阶段，使用记录事实表位置 *P* 的列表。对于事实数据表中用于回答查询所需的每一个外键列 `C` （在 select list、group by 或 aggregate 子句中引用维度列），从 *P* 处提取 `C` 的外键值，并在相应的维度表中查找。注意，如果维度表主键是从 1 开始，连续排序的标识符列表（这是常见情况），那么外键实际上是表示所需元组在维表中的位置。这意味着可以使用此位置列表直接提取所需的维表列（这只是快速的数组查找）。
>
>数组的直接提取（再加上维度表通常很小，所以要查找的列通常可以放在二级缓存中），就是为什么这种联接算法不受前述延迟物化联接法[5]缺陷影响的原因，前面的方案由于读取维度值的无序性，最终位置列表的提取非常昂贵。此外，由于 *P* 的个数取决于整个查询的选择性，而不是到目前为止已执行的部分选择性，因此最小化了要提取的数量。
>
>第三阶段的执行示例参见图 4。注意，对于 `dwdate` 表，主键列不是从 1 开始排序的、连续的标识符列表，因此必须执行完全联接（而不仅仅是位置提取）。此外，由于是外键和主键联接，且已经计算了所有谓词，因此对于事实表中相交位置列表中的每个位置，维度表保证只返回一个结果（不放大结果集）。这意味着第三阶段的每个维度表联接都有相同数量的结果，因此每个联接都可以单独进行，并在以后的查询计划中合并结果（缝合在一起）。

#### 5.4.2 Between Predicate Rewriting

As described thus far, this algorithm is not much more than another way of thinking about a column-oriented semijoin or a late materialized hash join. Even though the hash part of the join is expressed as a predicate on a fact table column, practically there is little difference between the way the predicate is applied and the way a (late materialization) hash join is executed. The advantage of expressing the join as a predicate comes into play in the surprisingly common case (for star schema joins) where the set of keys in dimension table that remain after a predicate has been applied are contiguous. When this is the case, a technique we call “between predicate rewriting” can be used, where the predicate can be rewritten from a hash-lookup predicate on the fact table to a “between” predicate where the foreign key falls between two ends of the key range. For example, if the contiguous set of keys that are valid after a predicate has been applied are keys 1000-2000, then instead of inserting each of these keys into a hash table and probing the hash table for each foreign key value in the fact table, we can simply check to see if the foreign key is in between 1000 and 2000. If so, then the tuple joins; otherwise it does not. Between-predicates are faster to execute for obvious reasons as they can be evaluated directly without looking anything up.

The ability to apply this optimization **<u>hinges on</u>** the set of these valid dimension table keys being contiguous. In many instances, this property does not hold. For example, a range predicate on a non-sorted field results in non-contiguous result positions. And even for predicates on sorted fields, the process of sorting the dimension table by that attribute likely reordered the primary keys so they are no longer an ordered, contiguous set of identifiers. However, the latter concern can be easily alleviated through the use of dictionary encoding for the purpose of key reassignment (rather than compression). Since the keys are unique, dictionary encoding the column results in the dictionary keys being an ordered, contiguous list starting from 0. As long as the fact table foreign key column is encoded using the same dictionary table, the hash-table to between-predicate rewriting can be performed.

Further, the assertion that the optimization works only on predicates on the sorted column of a dimension table is not entirely true. In fact, dimension tables in data warehouses often contain sets of attributes of increasingly finer granularity. For example, the date table in SSBM has a `year` column, a `yearmonth` column, and the complete `date` column. If the table is sorted by `year`, secondarily sorted by `yearmonth`, and tertiarily sorted by the complete `date`, then equality predicates on any of those three columns will result in a contiguous set of results (or a range predicate on the sorted column). As another example, the `supplier` table has a region column, a `nation` column, and a `city` column (a region has many nations and a nation has many cities). Again, sorting from left-to-right will result in predicates on any of those three columns producing a contiguous range output. Data warehouse queries often access these columns, due to the OLAP practice of rolling-up data in successive queries (tell me profit by region, tell me profit by nation, tell me profit by city). Thus, “between predicate rewriting” can be used more often than one might initially expect, and (as we show in the next section), often yields a significant performance gain.

Note that predicate rewriting does not require changes to the query optimizer to detect when this optimization can be used. The code that evaluates predicates against the dimension table is capable of detecting whether the result set is contiguous. If so, the fact table predicate is rewritten at run-time.

> 目前为止，该算法只是面向列的<u>**半联接**</u>或<u>**延迟物化哈希联接**</u>的另一种方法。即使联接的哈希部分在事实表列上表示为谓词，但实际上应用谓词的方式与执行（延迟物化）哈希联接的方式之间几乎没有区别。对于星型联接，将联接表示为谓词的优势在一个非常常见案例中发挥了作用，<u>**==即在应用完谓词后，维表中的剩下的键保持连续==**</u>。如果是这样，可以使用一种我们称之为 betwee-谓词重写的技术，此时，事实表上的哈希查找，重写为 betwee-and，查找位于键范围的两端之间的外键。 例如，如果在谓词应用后，连续键集合是1000-2000，那么不必将每个键插入哈希表，并为事实表中的每个外键值探测哈希表，而是简单地检查外键是否在1000到2000之间，如果在这之间，则联接元组，否则就不联接。因为可以直接计算而不用查找任何内容，betwee-谓词的执行速度显然更快。
>
> 应用这种优化的能力**<u>取决于</u>**一组有效的、连续的维键。很多情况下此条件不成立。例如，未排序字段上的范围谓词会导致结果位置不连续。即使是排序字段上的谓词，但是基于该字段对维度表排序可能会重排主键，因此不再是有序、连续的标识符集。然而，通过使用字典码进行重新分配主键（而不是压缩），可以很容易减轻后一个问题。由于是唯一主键，因此对列进行字典编码会导致字典键是从 0 开始、有序连续列表。只要事实表外键列使用相同的字典表进行编码，就可以把哈希表查找重写为 betwee-谓词。
>
> 此外，该优化只对维表排序列上的谓词起作用的说法并不完全正确。事实上，数仓中的维度表通常包含粒度越来越细的属性集。例如，SSBM 中的日期表有以一个 `year` 列、`yearmonth` 列和完整的 `date` 列。如果表按 `year` 排序，其次按 `yearmonth` 排序，再按完整的 `date` 排序，那么这三列中任何一个的相等谓词都将生成一组连续的结果（或排序列上的范围谓词）。另一个例子是 `supplier` 表有一个 `region` 列、`nation`列和一个`city`列（一个地区有许多国家，一个国家有许多城市）。同样，从左到右的排序将导致这三列中任何一列上的（相等）谓词产生连续的范围输出。数仓查询通常会访问这些列，这是因为实践中连续的 OLAP 查询，总是这样上卷数据：告诉我按地区划分的利润，按国家划分的利润，按城市划分的利润。
>
> 请注意，谓词重写不需要更改查询优化器即可检测何时可以使用此优化。 根据维度表计算谓词的代码能够检测结果集是否连续。如果是这样，则在<u>==运行时==</u>**重写事实表的谓词**。

## 7. CONCLUSION

In this paper, we compared the performance of C-Store to several  variants of a commercial row-store system on the data warehousing benchmark, SSBM. We showed that attempts to emulate the physical layout of a column-store in a row-store via techniques like vertical partitioning and index-only plans do not yield good performance. **==We attribute this slowness to high tuple reconstruction costs, as well as the high per-tuple overheads in narrow, vertically partitioned tables==**. <u>We broke down the reasons why a column-store is able to process column-oriented data so effectively, finding that late materialization improves performance by a factor of three, and that compression provides about a factor of two on average, or an order-of-magnitude on queries that access sorted data. We also proposed a new join technique, called invisible joins, that further improves performance by about 50%</u>.

The conclusion of this work is not that simulating a columnstore in a row-store is impossible. Rather, it is that this simulation performs poorly on today’s row-store systems (our experiments were performed on a very recent product release of System X). A successful column-oriented simulation will require some important system improvements, such as virtual record-ids, reduced tuple overhead, fast merge joins of sorted data, run-length encoding across multiple tuples, and some column-oriented query execution techniques like operating directly on compressed data, block processing, invisible joins, and late materialization. Some of these improvements have been implemented or proposed to be implemented in various different row-stores [12, 13, 20, 24]; however, building a complete row-store that can transform into a column-store on workloads where column-stores perform well is an interesting research problem to pursue。

> 基于数据仓库基准 SSBM，我们比较了 C-Store 与商业行存系统几种变体的性能。我们表明通过诸如垂直分区、只用索引计划之类的技术，在行存储中模拟列存储的物理布局，并不会有好的性能。**==我们将慢归因于过高的元组重建成本，以及窄的垂直分区表中每元组的高开销==**。我们分析了列存储能够如此有效地处理面向列的数据的原因，发现延迟物化可以将性能提高三倍，而数据压缩大约平均提高了两倍的性能，如果是访问排序数据，则性能可以提高一个数量级。我们还提出了一种新的**连接技术**，称为**==隐形连接==**，进一步将性能提高约50%。
>
> 这项工作的结论并不是说不可能在行存储中模拟列存储。 而是，这种模拟在当今的行存储系统上表现不佳（我们的实验是在 System X 的最新产品版本上进行的）。成功的面向列的模拟需要一些重要的系统改进，如虚拟记录ID、减少元组开销、排序数据的快速合并连接、跨多个元组的行程编码，以及一些面向列的查询执行技术，如直接在压缩数据上操作、块处理、**==隐式联接==**和延迟物化。其中一些改进已在各种不同的行存储中实现或建议实现[12、13、20、24]；但构建一个完整的行存储，在列存储可以高效执行的场景将数据转换为列式存储，是一个有趣的研究问题。

