# Overview of ClickHouse Architecture[ ](https://clickhouse.tech/docs/en/development/architecture/#overview-of-clickhouse-architecture)

ClickHouse is a true column-oriented DBMS. Data is stored by columns, and during the execution of arrays (vectors or chunks of columns). Whenever possible, operations are dispatched on arrays, rather than on individual values. It is called “vectorized query execution” and it helps lower the cost of actual data processing.

> This idea is nothing new. It dates back to the `APL` (A programming language, 1957) and its descendants: `A +` (APL dialect), `J` (1990), `K` (1993), and `Q` (programming language from Kx Systems, 2003). Array programming is used in scientific data processing. Neither is this idea something new in relational databases: for example, it is used in the `VectorWise` system (also known as Actian Vector Analytic Database by Actian Corporation).

There are two different approaches for speeding up query processing: vectorized query execution and runtime code generation. The latter removes all indirection and dynamic dispatch. Neither of these approaches is strictly better than the other. Runtime code generation can be better when it fuses many operations, thus fully utilizing CPU execution units and the pipeline. **Vectorized query execution can be less practical** because it involves temporary vectors that must be written to the cache and read back. If the temporary data does not fit in the L2 cache, this becomes an issue. But vectorized query execution more easily utilizes the SIMD capabilities of the CPU. A [research paper](http://15721.courses.cs.cmu.edu/spring2016/papers/p5-sompolski.pdf) written by our friends shows that it is better to combine both approaches. ClickHouse uses vectorized query execution and has limited initial support for runtime code generation.

> ClickHouse 是一个完全面向列式的分布式数据库。数据通过列存储，在查询过程中，数据通过数组来处理(向量或者列块)。当进行查询时，操作被转发到数组上，而不是在特定的值上。因此被称为**向量化查询执行**，相对于实际的数据处理成本，向量化处理降低了实际的数据处理开销。
>
> > 这个设计思路并不是新的思路理念。历史可以追溯到`APL`编程语言时代：`A+`, `J`, `K`, and `Q`。数组编程广泛用于科学数据处理领域。而在关系型数据库中：也应用了`向量化`系统。
>
> 在加速查询处理上，有两种方法：向量化查询执行和运行时代码生成。后者删除了所有的间接和动态调度。这两个方法没有绝对的优胜者，当多个操作一起执行时，运行时代码生成会更好，可以充分累用 CPU 执行单元和流水线。**向量化查询执行实用性并不那么高**，因为它涉及必须写到缓存并读回的临时向量。如果 L2 缓存容纳不下临时数据，那这将成为一个问题。但向量化查询执行更容易利用 **CPU** 的 **SIMD** 能力。这篇[研究论文](http://15721.courses.cs.cmu.edu/spring2016/papers/p5-sompolski.pdf)显示将两个方法结合到一起效果会更好。ClickHouse 主要使用**向量化查询执行**，并**有限支持运行时代码生成**（仅`GROUP BY`内部循环第一阶段被编译）。

## Columns[ ](https://clickhouse.tech/docs/en/development/architecture/#columns)

`IColumn` interface is used to represent columns in memory (actually, chunks of columns). This interface provides helper methods for the implementation of various relational operators. Almost all operations are immutable: they do not modify the original column, but create a new modified one. For example, the `IColumn :: filter` method accepts a filter byte mask. It is used for the `WHERE` and `HAVING` relational operators. Additional examples: the `IColumn :: permute` method to support `ORDER BY`, the `IColumn :: cut` method to support `LIMIT`.

Various `IColumn` implementations (`ColumnUInt8`, `ColumnString`, and so on) are responsible for the memory layout of columns. The memory layout is usually a contiguous array. For the integer type of columns, it is just one contiguous array, like `std :: vector`. For `String` and `Array` columns, it is two vectors: one for all array elements, placed contiguously, and a second one for offsets to the beginning of each array. There is also `ColumnConst` that stores just one value in memory, but looks like a column.

> `IColumn` 接口用于表示内存中的列（实际上是大块的列）。该接口提供了用于实现各种关系运算符的辅助方法。**<u>几乎所有操作都是不可变的</u>**：不会修改原始列，而是创建一个新列。 例如，用于 `WHERE` 和`HAVING`关系运算符的 `IColumn::filter` 方法，接受<u>过滤器字节掩码</u>。 其他示例：支持 `ORDER BY` 的 `IColumn::permute` 方法，支持 `LIMIT` 的 `IColumn::cut` 方法。
>
> 各种 `IColumn` 实现（`ColumnUInt8`，`ColumnString`等）负责列的内存布局。**<u>内存布局</u>**通常是连续的数组。对于**<u>整数类型</u>**的列，它只是一个如 `std::vector` 的连续数组。 对于 `String` 和 `Array` 列，是两个数组：一个用于所有的数组元素，是连续放置的；第二个数组用于保存每个数组开头的偏移量。 还有一个 `ColumnConst`，仅在内存中存储一个值，但看起来像一列。

## Field[ ](https://clickhouse.tech/docs/en/development/architecture/#field)

Nevertheless, it is possible to work with individual values as well. To represent an individual value, the `Field` is used. `Field` is just a discriminated union of `UInt64`, `Int64`, `Float64`, `String` and `Array`. `IColumn` has the `operator []` method to get the n-th value as a `Field`, and the `insert` method to append a `Field` to the end of a column. These methods are not very efficient, because they require dealing with temporary `Field` objects representing an individual value. There are more efficient methods, such as `insertFrom`, `insertRangeFrom`, and so on.

`Field` doesn’t have enough information about a specific data type for a table. For example, `UInt8`, `UInt16`, `UInt32`, and `UInt64` are all represented as `UInt64` in a `Field`.

> 但也可使用利用 `Field` 表示的单个值， `Field` 不过是 `UInt64`， `Int64`， `Float64`， `String` 和 `Array` 的[可区分联合](https://zh.wikipedia.org/wiki/%E6%A0%87%E7%AD%BE%E8%81%94%E5%90%88)。 `IColumn`  有 `operator[]` 方法，将第 n 个值按 `Field` 返回，并且有 `insert` 方法将 `Field` 追加到列尾。这些方法并不高效，因为需要处理代表单个值的 `Field` 的临时对象。可以使用像 `insertFrom`， `insertRangeFrom` 这些更有效的方法。
>
> 对于表的数据类型，`Field` 没有足够的信息。比如，`UInt8`， `UInt16`， `UInt32` 和 `UInt64` 在 `Field` 中均表示为 `UInt64`。
>

## Leaky Abstractions[ ](https://clickhouse.tech/docs/en/development/architecture/#leaky-abstractions)

`IColumn` has methods for common relational transformations of data, but they don’t meet all needs. For example, `ColumnUInt64` doesn’t have a method to calculate the sum of two columns, and `ColumnString` doesn’t have a method to run a substring search. These countless routines are implemented outside of `IColumn`.

Various functions on columns can be implemented in a generic, non-efficient way using `IColumn` methods to extract `Field` values, or in a specialized way using knowledge of inner memory layout of data in a specific `IColumn` implementation. It is implemented by casting functions to a specific `IColumn` type and deal with internal representation directly. For example, `ColumnUInt64` has the `getData` method that returns a reference to an internal array, then a separate routine reads or fills that array directly. We have “leaky abstractions” to allow efficient specializations of various routines.

>`IColumn` 有通用的方法用于转换关系数据，但不能满足所有需求。例如，`ColumnUInt64` 没有计算两列总和的方法，而 `ColumnString` 没有子字符串搜索的方法。有很多函数是在 `IColumn` 之外实现的。
>
>列上的各种函数可以使用 `IColumn`提取 `Field`值以通用、低效的方式实现；也可以通过知道列内部内存布局的知识以专用方式来实现。通过将函数强制转换为特定的 `IColumn` 类型，并直接处理内部表示来完成。 例如，`ColumnUInt64` 具有 `getData` 方法，该方法返回对内部数组的引用，一个单独的函数可以直接读取或填充该数组。 我们通过**<u>抽象泄漏</u>**，以特定的方式高效实现各种函数。

## Data Types[ ](https://clickhouse.tech/docs/en/development/architecture/#data_types)

`IDataType` is responsible for serialization and deserialization: for reading and writing chunks of columns or individual values in binary or text form. `IDataType` directly corresponds to data types in tables. For example, there are `DataTypeUInt32`, `DataTypeDateTime`, `DataTypeString` and so on.

`IDataType` and `IColumn` are only loosely related to each other. Different data types can be represented in memory by the same `IColumn` implementations. For example, `DataTypeUInt32` and `DataTypeDateTime` are both represented by `ColumnUInt32` or `ColumnConstUInt32`. In addition, the same data type can be represented by different `IColumn` implementations. For example, `DataTypeUInt8` can be represented by `ColumnUInt8` or `ColumnConstUInt8`.

`IDataType` only stores metadata. For instance, `DataTypeUInt8` doesn’t store anything at all (except virtual pointer `vptr`) and `DataTypeFixedString` stores just `N` (the size of fixed-size strings).

`IDataType` has helper methods for various data formats. Examples are methods to serialize a value with possible quoting, to serialize a value for JSON, and to serialize a value as part of the XML format. There is no direct correspondence to data formats. For example, the different data formats `Pretty` and `TabSeparated` can use the same `serializeTextEscaped` helper method from the `IDataType` interface.

> `IDataType`负责**序列化**和**反序列化**：用于读写二进制或文本形式的列块或单个值。`IDataType` 直接对应于表中的数据类型。例如，有 `DataTypeUInt32`、`DataTypeDateTime`、`DataTypeString` 等。
>
> `IDataType` 和 `IColumn` 并非强相关。在内存中，可用相同的 `IColumn` 表示不同的数据类型。例如，`ColumnUInt32` 或 `ColumnConstUInt32` 表示 `DataTypeUInt32` 和 `DataTypeDateTime`。也可用不同的 `IColumn` 表示同一数据类型。例如，用 `ColumnUInt8` 或 `ColumnConstUInt8` 表示 `DataTypeUInt8` 。
>
> `IDataType` 只存储元数据。例如，`DataTypeUInt8` 不存储任何内容（除虚拟指针 `vptr` 外），而 `DataTypeFixedString` 只存储固定大小字符串的大小 `N`。
>
> `IDataType` 有各种数据格式的辅助方法。比如，为了序列化 JSON 值，或将值序列化为 XML 格式的一部分，可能需要将数据放在双引号中。但没有直接对应数据格式，不同数据格式的 `TabSeparated` 和 `Pretty` 都可使用 `IDataType` 接口的 `serializeTextEscaped` 辅助方法。
>
> > `serializeTextEscaped` 是 `IDataType` 接口的虚方法。

## Block[ ](https://clickhouse.tech/docs/en/development/architecture/#block)

A `Block` is a container that represents a subset (chunk) of a table in memory. It is just a set of triples: `(IColumn, IDataType, column name)`. During query execution, data is processed by `Block`s. If we have a `Block`, we have data (in the `IColumn` object), we have information about its type (in `IDataType`) that tells us how to deal with that column, and we have the column name. It could be either the original column name from the table or some artificial name assigned for getting temporary results of calculations.

When we calculate some function over columns in a block, we add another column with its result to the block, and we don’t touch columns for arguments of the function because operations are immutable. Later, unneeded columns can be removed from the block, but not modified. It is convenient for the elimination of common subexpressions.

Blocks are created for every processed chunk of data. Note that for the same type of calculation, the column names and types remain the same for different blocks, and only column data changes. It is better to split block data from the block header because small block sizes have a high overhead of temporary strings for copying shared_ptrs and column names.

`Block` 是一个容器，代表内存中表的一个子集（一个 `Chunk`），是 `IColumn`, `IDataType`, 和列名的三元组。执行查询时，数据按 `Block` 来处理。有了 `Block`， 就有数据（`IColumn` 对象），也有数据类型的信息（ `IDataType` ）告诉<u>**我们如何处理此列**</u>，同时还有列名，可以是表中的原始列名，也可以是为获得临时计算结果而分配的人工名字。

因为操作是不可变的，当基于块（`Block`）上的列计算某个函数时，我们会将计算结果以另一列的形式写回块中，不会修改作为函数参数的列。稍后可以从块中删除不需要的列，但不对其进行修改。它便于消除公共子表达式。

`Block` 用于处理数据块。注意，对于相同类型的计算，不同数据块的<u>列名</u>和<u>类型</u>保持不变，只更改列数据。最好把块数据（block data）和块头（block header）分开，因为复制太小块的列名和共享指的开销较大。

>```cpp
>// 三元组
>/** Column data along with its data type and name.
> * Column data could be nullptr - to represent just 'header' of column.
> * Name could be either name from a table or some temporary generated name 
> * during expression evaluation.
> */
>struct ColumnWithTypeAndName
>{
>    ColumnPtr column;
>    DataTypePtr type;
>    String name;
>  //....
>};
>```

## Block Streams[ ](https://clickhouse.tech/docs/en/development/architecture/#block-streams)

Block streams are for processing data. We use streams of blocks to read data from somewhere, perform data transformations, or write data to somewhere. `IBlockInputStream` has the `read` method to fetch the next block while available. `IBlockOutputStream` has the `write` method to push the block somewhere.

Streams are responsible for:

1. Reading or writing to a table. The table just returns a stream for reading or writing blocks.
2. Implementing data formats. For example, if you want to output data to a terminal in `Pretty` format, you create a block output stream where you push blocks, and it formats them.
3. Performing data transformations. Let’s say you have `IBlockInputStream` and want to create a filtered stream. You create `FilterBlockInputStream` and initialize it with your stream. Then when you pull a block from `FilterBlockInputStream`, it pulls a block from your stream, filters it, and returns the filtered block to you. Query execution pipelines are represented this way.

There are more sophisticated transformations. For example, when you pull from `AggregatingBlockInputStream`, it reads all data from its source, aggregates it, and then returns a stream of aggregated data for you. Another example: `UnionBlockInputStream` accepts many input sources in the constructor and also a number of threads. It launches multiple threads and reads from multiple sources in parallel.

> Block streams use the “pull” approach to control flow: when you pull a block from the first stream, it consequently pulls the required blocks from nested streams, and the entire execution pipeline will work. Neither “pull” nor “push” is the best solution, because control flow is implicit, and that limits the implementation of various features like simultaneous execution of multiple queries (merging many pipelines together). This limitation could be overcome with coroutines or just running extra threads that wait for each other. We may have more possibilities if we make control flow explicit: if we locate the logic for passing data from one calculation unit to another outside of those calculation units. Read this [article](http://journal.stuffwithstuff.com/2013/01/13/iteration-inside-and-out/) for more thoughts.

We should note that the query execution pipeline creates temporary data at each step. We try to keep block size small enough so that temporary data fits in the CPU cache. With that assumption, writing and reading temporary data is almost free in comparison with other calculations. We could consider an alternative, which is to fuse many operations in the pipeline together. It could make the pipeline as short as possible and remove much of the temporary data, which could be an advantage, but it also has drawbacks. For example, a split pipeline makes it easy to implement caching intermediate data, stealing intermediate data from similar queries running at the same time, and merging pipelines for similar queries.

> 块流（Block streams）用于处理数据。我们使用**块流**从某处读取数据，转换数据或将数据写入某处。 `IBlockInputStream` 有一个 `read` 方法，获取下一个可用的块。 `IBlockOutputStream` 有一个 `write` 方法，将块**<u>==推到==</u>**某个地方。
>
> 流负责：
>
> 1. 读写表。 表仅返回用于读取或写入的块流。
> 2. 格式化数据。例如，将数据以 `Pretty` 格式输出到终端，则创建一个输出块流，在其中格式化数据后再推送。
> 3. 转换数据。 比如过滤  `IBlockInputStream`，则创建一个 `FilterBlockInputStream`，用 `IBlockInputStream` 对其初始化。 从 `FilterBlockInputStream` 中提取一个块时，会先从 `IBlockInputStream` 中提取一个块，并对其过滤，再返回过滤后的块。我们以这种方式表示**查询执行管道**。
>
> 还有更复杂的转换。例如，当从 `AggregatingBlockInputStream` 中拉取数据时，它将从数据源中读取所有数据，并进行聚合，然后返回**<u>聚合数据流</u>**。另一个例子：`UnionBlockInputStream` 的构造函数接受多个输入源以及一组线程，然后启动多个线程并从多个源并行读取。
>
> > 块流使用 **<u>==pull==</u>** 来控制流：从第一个流中读取块时，它会从**嵌套流**中提取所需的块，整个执行管道都将工作。**<u>==pull==</u>** 和 **<u>==push==</u>** 都不是最好的解决方案，因为控制流是隐式的，这限制了各种特性的实现，例如同时执行多个查询（将多个指针管道合并在一起）。这种限制可以通过协程，或运行额外的线程来解决。如果将控制流显式化，可能会有更多的可能性：将数据从一个计算单元传递到另一个计算单元的逻辑，是在这些计算单元之外。更多的想法参考这个[此文](http://journal.stuffithstuff.com/2013/01/13/iteration-inside-and-out/)。
>
> 我们应该注意，查询执行管道在每个步骤都会创建临时数据。我们尽量保持足够小块，以便将临时数据装进 **CPU** 缓存中。**在这种假设下，与其他计算相比，写入和读取临时数据几乎是免费的**。 可以考虑一种替代方法，将管道中的多个操作融合在一起。它可以使流水线尽可能短，并删除大量临时数据，这是一个优点，但也有缺点。例如，拆分管道可以很容易地缓存中间数据、从同时运行、类似的查询中窃取中间数据以及合并类似查询的管道。

## Formats[ ](https://clickhouse.tech/docs/en/development/architecture/#formats)

Data formats are implemented with block streams. There are “presentational” formats only suitable for the output of data to the client, such as `Pretty` format, which provides only `IBlockOutputStream`. And there are input/output formats, such as `TabSeparated` or `JSONEachRow`.

There are also row streams: `IRowInputStream` and `IRowOutputStream`. They allow you to pull/push data by individual rows, not by blocks. And they are only needed to simplify the implementation of row-oriented formats. The wrappers `BlockInputStreamFromRowInputStream` and `BlockOutputStreamFromRowOutputStream` allow you to convert row-oriented streams to regular block-oriented streams.

> 通过块流实现数据格式。有些格式只适用于向客户端输出数据，例如 `Pretty` 格式，它只提供 `IBlockOutputStream`。有些格式支持输入/输出，如 `TabSeparated` 或 `JSONEachRow` 。
>
> 流也支持有行（Row）：`IRowInputStream` 和  `IRowOutputStream`，按单独的行而不是按块读写数据。 它们只是用于简化面向行格式的实现。包装器 `BlockInputStreamFromRowInputStream` 和 `BlockOutputStreamFromRowOutputStream` 将面向行的流转换为常规的面向块的流。

## I/O[ ](https://clickhouse.tech/docs/en/development/architecture/#io)

For byte-oriented input/output, there are `ReadBuffer` and `WriteBuffer` abstract classes. They are used instead of C++ `iostream`s. Don’t worry: every mature C++ project is using something other than `iostream`s for good reasons.

`ReadBuffer` and `WriteBuffer` are just a contiguous buffer and a cursor pointing to the position in that buffer. Implementations may own or not own the memory for the buffer. There is a virtual method to fill the buffer with the following data (for `ReadBuffer`) or to flush the buffer somewhere (for `WriteBuffer`). The virtual methods are rarely called.

Implementations of `ReadBuffer`/`WriteBuffer` are used for working with files and file descriptors and network sockets, for implementing compression (`CompressedWriteBuffer` is initialized with another `WriteBuffer` and performs compression before writing data to it), and for other purposes – the names `ConcatReadBuffer`, `LimitReadBuffer`, and `HashingWriteBuffer` speak for themselves.

Read/WriteBuffers only deal with bytes. There are functions from `ReadHelpers` and `WriteHelpers` header files to help with formatting input/output. For example, there are helpers to write a number in decimal format.

Let’s look at what happens when you want to write a result set in `JSON` format to stdout. You have a result set ready to be fetched from `IBlockInputStream`. You create `WriteBufferFromFileDescriptor(STDOUT_FILENO)` to write bytes to stdout. You create `JSONRowOutputStream`, initialized with that `WriteBuffer`, to write rows in `JSON` to stdout. You create `BlockOutputStreamFromRowOutputStream` on top of it, to represent it as `IBlockOutputStream`. Then you call `copyData` to transfer data from `IBlockInputStream` to `IBlockOutputStream`, and everything works. Internally, `JSONRowOutputStream` will write various JSON delimiters and call the `IDataType::serializeTextJSON` method with a reference to `IColumn` and the row number as arguments. Consequently, `IDataType::serializeTextJSON` will call a method from `WriteHelpers.h`: for example, `writeText` for numeric types and `writeJSONString` for `DataTypeString`.

> 对于面向字节的输入/输出，用 `ReadBuffer` 和 `WriteBuffer` 抽象类代替 C++ 的 `iostream`。 不必担心：每个成熟的 C++ 项目都有充分理由使用 `iostream` 之外的输入输出流。
>
> `ReadBuffer` 和 `WriteBuffer` 只是一个连续的缓冲区和一个指向该缓冲区中某个位置的游标。具体的实现可以拥有或不拥有缓冲区的内存。有一个虚拟方法用以下数据填充缓冲区（对于`ReadBuffer` ）或在某处刷新缓冲区（对于 `WriteBuffer` ）。很少调用虚拟方法。
>
> `ReadBuffer` 和 `WriteBuffer ` 的派生类用于处理文件、文件描述符和网络套接字，用于实现压缩（ `CompressedWriteBuffer` 使用另一个 `WriteBuffer` 初始化，向其写入数据之前执行压缩），以及用于其他目的：`ConcatReadBuffer`，`LimitReadBuffer` 和 `HashingWriteBuffer` 的名称是说明了它们的用途。
>
> `ReadBuffer` 和 `WriteBuffer` 只处理字节。`ReadHelpers` 和 `WriteHelpers` 头文件中有一些函数可以用于格式化输入/输出。例如，用十进制格式编写数字的函数。
>
> 现在来看一下将 **JSON** 格式的结果集写入 stdout 会发生什么。结果集已经在 `IBlockInputStream` 中准备好。
>
> 1. 创建 `WriteBufferFromFileDescriptor(STDOUT_FILENO)` 将字节写入 stdout。
>2. 创建使用该 `WriteBuffer` 初始化的 `JSONRowOutputStream`，以便将数据按 `JSON` 的格式输出至标准输出。
> 3. 在其顶部创建  `BlockOutputStreamFromRowOutputStream`， 以将其表示为 `IBlockOutputStream`。
>4. 接下来，调用 `copyData` 将数据从 `IBlockInputStream` 传输到 `IBlockOutputStream`，一切正常。 `JSONRowOutputStream` 在内部编写各种 **JSON** 分隔符，并以 `IColumn` 和行号作为参数调用 `IDataType::serializeTextJSON` 方法。 
> 5. 最后，`IDataType::serializeTextJSON` 将调用 `WriteHelpers.h` 中的某个方法，例如：输出数值类型用 `writeText`，输出 `DataTypeString` 用 `writeJSONString`。

## Tables[ ](https://clickhouse.tech/docs/en/development/architecture/#tables)

The `IStorage` interface represents tables. Different implementations of that interface are different table engines. Examples are `StorageMergeTree`, `StorageMemory`, and so on. Instances of these classes are just tables.

The key `IStorage` methods are `read` and `write`. There are also `alter`, `rename`, `drop`, and so on. The `read` method accepts the following arguments: the set of columns to read from a table, the `AST` query to consider, and the desired number of streams to return. It returns one or multiple `IBlockInputStream` objects and information about the stage of data processing that was completed inside a table engine during query execution.

In most cases, the read method is only responsible for reading the specified columns from a table, not for any further data processing. All further data processing is done by the query interpreter and is outside the responsibility of `IStorage`.

But there are notable exceptions:

- The AST query is passed to the `read` method, and the table engine can use it to derive index usage and to read fewer data from a table.
- Sometimes the table engine can process data itself to a specific stage. For example, `StorageDistributed` can send a query to remote servers, ask them to process data to a stage where data from different remote servers can be merged, and return that preprocessed data. The query interpreter then finishes processing the data.

The table’s `read` method can return multiple `IBlockInputStream` objects to allow parallel data processing. These multiple block input streams can read from a table in parallel. Then you can wrap these streams with various transformations (such as expression evaluation or filtering) that can be calculated independently and create a `UnionBlockInputStream` on top of them, to read from multiple streams in parallel.

There are also `TableFunction`s. These are functions that return a temporary `IStorage` object to use in the `FROM` clause of a query.

To get a quick idea of how to implement your table engine, look at something simple, like `StorageMemory` or `StorageTinyLog`.

> As the result of the `read` method, `IStorage` returns `QueryProcessingStage` – information about what parts of the query were already calculated inside storage.

----

> `IStorage` 接口表示**表**，各种表引擎是该接口不同的实现。比如 `StorageMergeTree`， `StorageMemory` 等，这些类的实例就是表。
>
> `IStorage` 的关键方法是 `read` 和 `write`。还有 `alter`，`rename`，`drop` 等。 `read` 方法需要以下这些参数：从表中读取的列集、要考虑的 AST 查询、以及希望返回**<u>==几条数据流==</u>**。它返回一个或多个 `IBlockInputStream` 对象，以及查询执行期间，在表引擎内部完成的数据处理阶段的信息。
>
> 大多数时候，`read` 方法只负责从表中读取指定的列，进一步的数据处理不是 `IStorage` 的职责，而是由查询解释器完成。
>
> 但也有一些例外：
>
> - 表引擎的 `read` 方法可以通过 `AST` 获取索引使用情况，从而读取更少的数据。
> - 表引擎有时可以将数据处理到特定阶段。例如，`StorageDistributed` 以向远程服务器发送查询，要求它们将数据处理到可以合并的阶段，再返回这些预处理的数据。 然后，由查询解释器完成数据处理。
>
> 并行数据处理通过表的 `read` 方法，返回多个 `IBlockInputStream` 对象来完成。多个块输入流从表中并行读取，然后各自独立地执行转换（如表达式求值或过滤），再在这些流上创建一个 `UnionBlockInputStream` ，以便于在多个流中并行读取。
>
> 还有 `TableFunction`。这些函数返回临时的 `IStorage` 对象，用在查询的 `FROM` 子句中。
>
> 要快速了解如何实现表引擎，请看一些简单的实现，如 `StorageMemory` 或 `StorageTinyLog`。
>
> > 作为 `read` 方法的结果，`IStorage` 返回 `QueryProcessingStage`  – 关于查询的哪些部分已经在存储内部计算的信息。

## Parsers[ ](https://clickhouse.tech/docs/en/development/architecture/#parsers)

A hand-written recursive descent parser parses a query. For example, `ParserSelectQuery` just recursively calls the underlying parsers for various parts of the query. Parsers create an `AST`. The `AST` is represented by nodes, which are instances of `IAST`.

> Parser generators are not used for historical reasons.

## Interpreters[ ](https://clickhouse.tech/docs/en/development/architecture/#interpreters)

Interpreters are responsible for creating the query execution pipeline from an `AST`. There are simple interpreters, such as `InterpreterExistsQuery` and `InterpreterDropQuery`, or the more sophisticated `InterpreterSelectQuery`. The query execution pipeline is a combination of block input or output streams. For example, the result of interpreting the `SELECT` query is the `IBlockInputStream` to read the result set from; the result of the INSERT query is the `IBlockOutputStream` to write data for insertion to, and the result of interpreting the `INSERT SELECT` query is the `IBlockInputStream` that returns an empty result set on the first read, but that copies data from `SELECT` to `INSERT` at the same time.

`InterpreterSelectQuery` uses `ExpressionAnalyzer` and `ExpressionActions` machinery for query analysis and transformations. This is where most rule-based query optimizations are done. `ExpressionAnalyzer` is quite messy and should be rewritten: various query transformations and optimizations should be extracted to separate classes to allow modular transformations or query.

> 解释器负责从 `AST` 创建查询执行管道。有些解释器简单，例如 `InterpreterExistsQuery` 和 `InterpreterDropQuery`，有些则更复杂，如 `InterpreterSelectQuery`。查询执行管道是块输入流或输出流的组合。例如，执行 `SELECT` 查询的结果是 `IBlockInputStream`，从中读取结果集； `INSERT` 查询的结果是 `IBlockOutputStream`，将要插入的数据写入其中；而 `INSERT SELECT` 查询的执行结果是 `IBlockInputStream`，第一次读取时返回空结果集，但随后会将 `SELECT` 的数据同时复制为 `INSERT` 的数据。
>
> `InterpreterSelectQuery` 使用 `ExpressionAnalyzer` 和 `ExpressionActions` 机制进行查询分析和转换，大多数基于规则的查询优化在这里完成。`ExpressionAnalyzer` 相当混乱，应该重写：各种查询转换和优化应该提取到单独的类中，以允许模块化转换或查询。

## Functions[ ](https://clickhouse.tech/docs/en/development/architecture/#functions)

There are ordinary functions and aggregate functions. For aggregate functions, see the next section.

Ordinary functions don’t change the number of rows – they work as if they are processing each row independently. In fact, functions are not called for individual rows, but for `Block`’s of data to implement vectorized query execution.

There are some miscellaneous functions, like [blockSize](https://clickhouse.tech/docs/en/sql-reference/functions/other-functions/#function-blocksize), [rowNumberInBlock](https://clickhouse.tech/docs/en/sql-reference/functions/other-functions/#function-rownumberinblock), and [runningAccumulate](https://clickhouse.tech/docs/en/sql-reference/functions/other-functions/#runningaccumulate), that exploit block processing and violate the independence of rows.

ClickHouse has strong typing, so there’s no implicit type conversion. If a function doesn’t support a specific combination of types, it throws an exception. But functions can work (be overloaded) for many different combinations of types. For example, the `plus` function (to implement the `+` operator) works for any combination of numeric types: `UInt8` + `Float32`, `UInt16` + `Int8`, and so on. Also, some variadic functions can accept any number of arguments, such as the `concat` function.

Implementing a function may be slightly inconvenient because a function explicitly dispatches supported data types and supported `IColumns`. For example, the `plus` function has code generated by instantiation of a C++ template for each combination of numeric types, and constant or non-constant left and right arguments.

It is an excellent place to implement runtime code generation to avoid template code bloat. Also, it makes it possible to add fused functions like fused multiply-add or to make multiple comparisons in one loop iteration.

Due to vectorized query execution, functions are not short-circuited. For example, if you write `WHERE f(x) AND g(y)`, both sides are calculated, even for rows, when `f(x)` is zero (except when `f(x)` is a zero constant expression). But if the selectivity of the `f(x)` condition is high, and calculation of `f(x)` is much cheaper than `g(y)`, it’s better to implement multi-pass calculation. It would first calculate `f(x)`, then filter columns by the result, and then calculate `g(y)` only for smaller, filtered chunks of data.

> `CK` 有**普通函数**和聚合函数。有关聚合函数，请参阅下一节。
>
> **普通函数**不会更改行数，它们的工作方式就好像独立处理每一行一样。实际上，并不是针对单个行调用函数，而是基于数据的 `Block` 调用，以实现向量化查询执行。
>
> 还有一些函数，例如 [blockSize](https://clickhouse.tech/docs/en/sql-reference/functions/other-functions/#function-blocksize)，[rowNumberInBlock](https://clickhouse.tech / docs / zh-CN / sql-reference / functions / other-functions /＃function-rownumberinblock) 和 [runningAccumulate](https://clickhouse.tech/docs/zh-CN/sql-reference/functions/other-functions/#runningaccumulate)，利用块完成功能，因此违反了行的独立性。
>
> `ClickHouse` 是强类型，因此没有隐式类型转换。如果某个函数不支持特定的类型组合，则将引发异常。但是函数可以通过重载为各种不同类型的组合工作。例如，`plus` 函数（实现 `+` 运算符）适用于任何数字类型的组合：`UInt8 + Float32`，`UInt16 + Int8`，依此类推。同样，某些可变参数函数可以接受任意数量的参数，例如 `concat` 函数。
>
> 实现函数可能有点烦，因为需要包含所有支持该函数的数据类型和 `IColumn` 类型。例如，`plus` 函数通过 `C++` 模板，针对数字类型、常量或非常量左右参数的每种组合，生成具体的代码。
>
> 这是一个实现动态代码生成的好地方，从而能够避免模板代码膨胀。同样，运行时代码生成也使得实现融合函数成为可能，比如融合**乘**和**加**操作，或者在一次循环迭代中进行多次比较。
>
> 由于执行向量化查询，函数不会**<u>==短路==</u>**。比如  `WHERE f(x) AND g(y)`，即使对  `f(x)` 为 0 的行（除非 `f(x)` 是为 0 的常量表达式），仍然会分别计算 `f(x)` 和 `g(y)`。但如果 `f(x)` 的过滤性很好，并且计算 `f(x)` 比计算 `g(y)` 要划算得多，那么最好进行多轮计算：首先计算 `f(x)`，根据计算结果对列数据进行过滤，然后计算 `g(y)`，之后只需对较小数量的数据进行过滤。

## Aggregate Functions[ ](https://clickhouse.tech/docs/en/development/architecture/#aggregate-functions)

Aggregate functions are stateful functions. They accumulate passed values into some state and allow you to get results from that state. They are managed with the `IAggregateFunction` interface. States can be rather simple (the state for `AggregateFunctionCount` is just a single `UInt64` value) or quite complex (the state of `AggregateFunctionUniqCombined` is a combination of a linear array, a hash table, and a `HyperLogLog` probabilistic data structure).

States are allocated in `Arena` (a memory pool) to deal with multiple states while executing a high-cardinality `GROUP BY` query. States can have a non-trivial constructor and destructor: for example, complicated aggregation states can allocate additional memory themselves. It requires some attention to creating and destroying states and properly passing their ownership and destruction order.

Aggregation states can be serialized and deserialized to pass over the network during distributed query execution or to write them on the disk where there is not enough RAM. They can even be stored in a table with the `DataTypeAggregateFunction` to allow incremental aggregation of data.

> The serialized data format for aggregate function states is not versioned right now. It is ok if aggregate states are only stored temporarily. But we have the `AggregatingMergeTree` table engine for incremental aggregation, and people are already using it in production. It is the reason why backward compatibility is required when changing the serialized format for any aggregate function in the future.

## Server[ ](https://clickhouse.tech/docs/en/development/architecture/#server)

The server implements several different interfaces:

- An HTTP interface for any foreign clients.
- A TCP interface for the native ClickHouse client and for cross-server communication during distributed query execution.
- An interface for transferring data for replication.

Internally, it is just a primitive multithreaded server without coroutines or fibers. Since the server is not designed to process a high rate of simple queries but to process a relatively low rate of complex queries, each of them can process a vast amount of data for analytics.

The server initializes the `Context` class with the necessary environment for query execution: the list of available databases, users and access rights, settings, clusters, the process list, the query log, and so on. Interpreters use this environment.

We maintain full backward and forward compatibility for the server TCP protocol: old clients can talk to new servers, and new clients can talk to old servers. But we don’t want to maintain it eternally, and we are removing support for old versions after about one year.

> Note
>
> For most external applications, we recommend using the HTTP interface because it is simple and easy to use. The TCP protocol is more tightly linked to internal data structures: it uses an internal format for passing blocks of data, and it uses custom framing for compressed data. We haven’t released a C library for that protocol because it requires linking most of the ClickHouse codebase, which is not practical.

---

> 服务器实现了几个不同的接口：
>
> - 一个所有外部客户端使用的 `HTTP` 接口。
> - 一个本机 `ClickHouse` 客户端使用，以及在分布式查询执行中跨服务器通信的 `TCP` 接口。
> - 一个**<u>==复制==</u>**用于传输数据的接口。
>
> 内部就是一个基础的多线程服务器，没有用到协程和纤程。服务器不是为处理高速率的简单查询设计的，而是为处理相对低速率的复杂查询设计的，每一个复杂查询能够对大量的数据进行处理分析。
>
> 服务器使用查询执行所必需的环境来初始化 `Context` 类：可用数据库列表、用户和访问权限、配置、集群、进程列表和查询日志等。这些环境被解释器使用。
>
> 服务器的 `TCP` 协议向后向前完全兼容：旧客户端可以和新服务器通信，新客户端也可以和旧服务器通信。但是我们并不想永远维护它，将在大约一年后删除对旧版本的支持。
>
> > 注意
> >
> > 对于所有的外部应用，我们推荐使用 `HTTP` 接口，因为该接口很简单，容易使用。`TCP` 接口与内部数据结构的联系更加紧密：它使用内部格式传递数据块，并使用自定义帧来压缩数据。我们没有发布该协议的 `C` 库，因为它需要链接大部分的 ClickHouse 代码库，这是不切实际的。

## Distributed Query Execution[ ](https://clickhouse.tech/docs/en/development/architecture/#distributed-query-execution)

Servers in a cluster setup are mostly independent. You can create a `Distributed` table on one or all servers in a cluster. The `Distributed` table does not store data itself – it only provides a “view” to all local tables on multiple nodes of a cluster. When you SELECT from a `Distributed` table, it rewrites that query, chooses remote nodes according to load balancing settings, and sends the query to them. The `Distributed` table requests remote servers to process a query just up to a stage where intermediate results from different servers can be merged. Then it receives the intermediate results and merges them. The distributed table tries to distribute as much work as possible to remote servers and does not send much intermediate data over the network.

Things become more complicated when you have subqueries in IN or JOIN clauses, and each of them uses a `Distributed` table. We have different strategies for the execution of these queries.

There is no global query plan for distributed query execution. Each node has its local query plan for its part of the job. We only have simple one-pass distributed query execution: we send queries for remote nodes and then merge the results. But this is not feasible for complicated queries with high cardinality GROUP BYs or with a large amount of temporary data for JOIN. In such cases, we need to “reshuffle” data between servers, which requires additional coordination. ClickHouse does not support that kind of query execution, and we need to work on it.

> 集群中的服务器几乎都是独立的。可以在群集中的一台或所有服务器上创建 `Distributed` 表。`Distributed` 表本身不存储数据，它只为集群的多个节点上的所有本地表提供**<u>视图</u>**。在 `Distributed` 表上执行 `SELECT` 时，会重写该查询，根据负载平衡的配置设置选择远程节点，并将查询发送给它们。`Distributed` 表请求**<u>远程服务器</u>**处理查询，直到远程服务器返回可以合并的中间结果为止；然后，接收中间结果并将其合并。分布式表将尽可能多的工作分配给远程服务器，并且不在网络中传输太多的中间数据。
>
> 如果在 `IN` 或 `JOIN` 子句中有子查询，并且每个子查询都使用一个 `Distributed` 表，情况变得更复杂。我们有不同的策略来执行这些查询。
>
> 分布式查询没有全局的查询执行计划。每个节点都有针对其自身那部分查询的本地查询计划。我们只有一个简单的、单次的分布式查询执行：向发送远程节点的查询，然后合并结果。但这对高基数 `GROUP BY` 或有大量临时数据的 `JOIN` 是不可行的。此时得在服务器之间 `reshuffle` 数据，这需要额外的协调。`ClickHouse` 不支持这种查询执行，我们需要对此进行研究。

## Merge Tree[ ](https://clickhouse.tech/docs/en/development/architecture/#merge-tree)

`MergeTree` is a family of storage engines that supports indexing by primary key. The primary key can be an arbitrary tuple of columns or expressions. Data in a `MergeTree` table is stored in “parts”. Each part stores data in the primary key order, so data is ordered lexicographically by the primary key tuple. All the table columns are stored in separate `column.bin` files in these parts. The files consist of compressed blocks. Each block is usually from 64 KB to 1 MB of uncompressed data, depending on the average value size. The blocks consist of column values placed contiguously one after the other. Column values are in the same order for each column (the primary key defines the order), so when you iterate by many columns, you get values for the corresponding rows.

The primary key itself is “sparse”. It doesn’t address every single row, but only some ranges of data. A separate `primary.idx` file has the value of the primary key for each N-th row, where N is called `index_granularity` (usually, N = 8192). Also, for each column, we have `column.mrk` files with “marks,” which are offsets to each N-th row in the data file. Each mark is a pair: the offset in the file to the beginning of the compressed block, and the offset in the decompressed block to the beginning of data. Usually, compressed blocks are aligned by marks, and the offset in the decompressed block is zero. Data for `primary.idx` always resides in memory, and data for `column.mrk` files is cached.

When we are going to read something from a part in `MergeTree`, we look at `primary.idx` data and locate ranges that could contain requested data, then look at `column.mrk` data and calculate offsets for where to start reading those ranges. Because of sparseness, excess data may be read. ClickHouse is not suitable for a high load of simple point queries, because the entire range with `index_granularity` rows must be read for each key, and the entire compressed block must be decompressed for each column. We made the index sparse because we must be able to maintain trillions of rows per single server without noticeable memory consumption for the index. Also, because the primary key is sparse, it is not unique: it cannot check the existence of the key in the table at INSERT time. You could have many rows with the same key in a table.

When you `INSERT` a bunch of data into `MergeTree`, that bunch is sorted by primary key order and forms a new part. There are background threads that periodically select some parts and merge them into a single sorted part to keep the number of parts relatively low. That’s why it is called `MergeTree`. Of course, merging leads to “write amplification”. All parts are immutable: they are only created and deleted, but not modified. When SELECT is executed, it holds a snapshot of the table (a set of parts). After merging, we also keep old parts for some time to make a recovery after failure easier, so if we see that some merged part is probably broken, we can replace it with its source parts.

`MergeTree` is not an LSM tree because it doesn’t contain “memtable” and “log”: inserted data is written directly to the filesystem. This makes it suitable only to INSERT data in batches, not by individual row and not very frequently – about once per second is ok, but a thousand times a second is not. We did it this way for simplicity’s sake, and because we are already inserting data in batches in our applications.

> MergeTree tables can only have one (primary) index: there aren’t any secondary indices. It would be nice to allow multiple physical representations under one logical table, for example, to store data in more than one physical order or even to allow representations with pre-aggregated data along with original data.

There are MergeTree engines that are doing additional work during background merges. Examples are `CollapsingMergeTree` and `AggregatingMergeTree`. This could be treated as special support for updates. Keep in mind that these are not real updates because users usually have no control over the time when background merges are executed, and data in a `MergeTree` table is almost always stored in more than one part, not in completely merged form.

> `MergeTree` 是存储引擎系列，支持按主键索引。主键是**<u>==列或表达式的任意元组==</u>**。`MergeTree` 表中的数据存储在**<u>==分块==</u>**中。每个**<u>==分块==</u>**按主键顺序存储数据，因此数据按**<u>==主键==</u>**的字典序排序。表的所有列都独立存储在**<u>==分块==</u>**的 `column.bin` 文件中。**文件由压缩块组成**。根据平均值大小，每个块压缩通常是 64KB 到 1MB 的未压缩数据。块由一个接一个地连续放置的列值组成。每个列的列值顺序相同（按主键定义顺序），因此遍历多个列时，将获得相应行的值。
>
> **主键本身稀疏**，不会定位到每行，而是定位到数据范围。单独的一个 `primary.idx` 文件有 N 行主键值，其中 N 称为 `index_granularity`（索引粒度，通常为 N = 8192）。 另外每一列都有 `column.mrk` 文件，标记了数据文件中第 N 行的偏移量。 每个标记都是一对：到**<u>==压缩块头==</u>**的**文件偏移量**，解压缩块中到**<u>==数据头==</u>**的**偏移量**。通常，压缩块通过标记对齐，解压缩块中的偏移量为零。`primary.idx` 的数据始终驻留在内存中，缓存 `column.mrk` 文件的数据。
>
> 当我们要从 `MergeTree` 的某个**<u>==分块==</u>**读取某些内容时，我们查看 `primary.idx` 并定位到可能包含请求数据的范围，然后查看 `column.mrk` 并计算读取这些数据的偏移量。由于稀疏，可能会读取多余的数据。`ClickHouse` 不适合用于大量的简单点查询，因为必须为每个键读取整个 `index_granularity`  行的数据，且必须为每一列解压整个压缩块。 我们使索引稀疏，是因为必须能够在每台服务器上维护数万亿行，而不用消耗大量内存来保存索引。另外，由于主键稀疏，所以不唯一：不能在 `INSERT` 时检查主键是否存在于表中。<u>==一个表中可能有许多行有相同的主键==</u>。
>
> 将一组数据 `INSERT` 到 `MergeTree` 时，数据按主键顺序排序并创建新的**<u>==分块==</u>**。后台线程会定时选择一些**<u>==分块==</u>**，将它们合并到一个单独排序的**<u>==分块==</u>**中，以保持相对较少的**<u>==分块==</u>**数量。这就是为什么称它为 `MergeTree`，当然，合并会导致写放大。所有**<u>==分块==</u>**都不可变：只能创建和删除，而不能修改它们。执行 `SELECT` 时，会持有表（一组**<u>==分块==</u>**）的快照。合并后，我们还保留旧**<u>==分块==</u>**一段时间，以便在发生故障时更容易恢复。因此，如果我们看到某个合并的**<u>==分块==</u>**可能已损坏，则可以用其原始**<u>==分块==</u>**替换它。
>
> `MergeTree` 不是 LSM 树，它不包含 **memtable** 和 **log** ：插入的数据直接写入文件系统。这使其仅适用于批量而不适用于逐行插入数据，也不能非常频繁地插入数据，大约每秒一次可以，但每秒不能超过一千次。这样做是为了简单起见，并且我们的应用程序本身就是批量插入数据。
>
> `MergeTree` 表只能有一个主索引：<u>==没有任何二级索引==</u>。在一个逻辑表下允许多种**物理存储**，是一个很好的设计，例如，用不止一种的物理顺序来存储数据，甚至允许将预聚合的数据与原始数据一起存储。
>
> 有些 `MergeTree` 引擎在后台合并期间会执行额外的工作。 例如 `CollapsingMergeTree` 和 `AggregatingMergeTree`。 这可以视为对更新的特殊支持。 请记住，这些并不是真正的更新，因为用户通常无法控制后台执行合并的时间，并且 `MergeTree` 表中的数据几乎总是存储在多个**<u>==分块==</u>**中，而不是以完全合并的形式存储。

## Replication[ ](https://clickhouse.tech/docs/en/development/architecture/#replication)

Replication in ClickHouse can be configured on a per-table basis. You could have some replicated and some non-replicated tables on the same server. You could also have tables replicated in different ways, such as one table with two-factor replication and another with three-factor.

Replication is implemented in the `ReplicatedMergeTree` storage engine. The path in `ZooKeeper` is specified as a parameter for the storage engine. All tables with the same path in `ZooKeeper` become replicas of each other: they synchronize their data and maintain consistency. Replicas can be added and removed dynamically simply by creating or dropping a table.

Replication uses an asynchronous multi-master scheme. You can insert data into any replica that has a session with `ZooKeeper`, and data is replicated to all other replicas asynchronously. Because ClickHouse doesn’t support UPDATEs, replication is conflict-free. As there is no quorum acknowledgment of inserts, just-inserted data might be lost if one node fails.

Metadata for replication is stored in ZooKeeper. There is a replication log that lists what actions to do. Actions are: get part; merge parts; drop a partition, and so on. Each replica copies the replication log to its queue and then executes the actions from the queue. For example, on insertion, the “get the part” action is created in the log, and every replica downloads that part. Merges are coordinated between replicas to get byte-identical results. All parts are merged in the same way on all replicas. It is achieved by electing one replica as the leader, and that replica initiates merges and writes “merge parts” actions to the log.

Replication is physical: only compressed parts are transferred between nodes, not queries. Merges are processed on each replica independently in most cases to lower the network costs by avoiding network amplification. Large merged parts are sent over the network only in cases of significant replication lag.

Besides, each replica stores its state in ZooKeeper as the set of parts and its checksums. When the state on the local filesystem diverges from the reference state in ZooKeeper, the replica restores its consistency by downloading missing and broken parts from other replicas. When there is some unexpected or broken data in the local filesystem, ClickHouse does not remove it, but moves it to a separate directory and forgets it.

> Note
>
> The ClickHouse cluster consists of independent shards, and each shard consists of replicas. The cluster is **not elastic**, so after adding a new shard, data is not rebalanced between shards automatically. Instead, the cluster load is supposed to be adjusted to be uneven. This implementation gives you more control, and it is ok for relatively small clusters, such as tens of nodes. But for clusters with hundreds of nodes that we are using in production, this approach becomes a significant drawback. We should implement a table engine that spans across the cluster with dynamically replicated regions that could be split and balanced between clusters automatically.

---

> `ClickHouse` 中的复制可以基于每个表进行配置。在同一台服务器上可以有一些复制表和一些非复制表。还可以用不同的方式复制表，例如一个表使用两备份复制，另一个表使用三备份复制。
>
> 在 `ReplicatedMergeTree` 存储引擎中实现复制。`ZooKeeper` 中的路径被指定为存储引擎的参数。 `ZooKeeper` 中所有具有相同路径的表都是彼此的副本：它们同步数据并保持一致性。只需创建或删除表，即可动态添加和删除副本。
>
> 复制使用异步多主方案。数据将被插入到有 `ZooKeeper` Session 的副本，然后异步复制到其他所有副本。因为 `ClickHouse` 不支持更新，所以复制不存在冲突。对于插入的数据并需要过半的节点确认，所以如果一个节点发生故障，则刚插入的数据可能会丢失。
>
> 用于复制的元数据存储在 `ZooKeeper` 中。有一个包含所有要执行操作的复制日志：获取**<u>==分块==</u>**、合并分块和删除分区等。每个副本将复制日志拷贝到其队列中，然后执行队列中操作。比如插入时，在复制日志中创建**<u>==获取分块==</u>**一操作，然后每个副本都会去下载该分块。为了获得相同的字节，副本之间会协调**<u>==合并==</u>**，以便<u>在所有副本上</u>以相同方式合并所有分块。因此，先将其中一个副本选为 `leader`，该副本首先进行合并，并把**<u>==合并分块==</u>**操作写到日志中。
>
> 复制是物理的：在节点之间只会传输压缩的分块，而非查询。为了降低网络成本（避免网络放大），大多数情况下，会在每一个副本上独立地处理合并。只有在复制严重滞后的情况下，才会通过网络发送合并后的大型分块。
>
> 此外，每个副本在 `ZooKeeper` 中将其状态存储为分块集及其校验和。当本地文件系统上的状态与 `ZooKeeper` 中的参考状态不同时，该副本通过从其他副本下载丢失和损坏的分块来恢复其一致性。当本地文件系统中有一些意外或损坏的数据时，`ClickHouse` 不会将其删除，而是将其移动到一个单独的目录中并将其忽略。
>
> > 注意
> >
> > ClickHouse 群集由独立的分片组成，每个分片由多个副本组成。集群不是弹性的，添加新的分片后，数据不会在分片之间自动重新平衡。相反，集群负载将变得不均衡。这么实现提供了更多的控制，对于相对较小的集群，比如数十个节点也可以。但是对于在生产环境中有数百个节点的集群，这是一个明显的缺点。我们应该实现一个可以跨集群动态复制 `Region`（小块数据）的表引擎，这些 `Region` 可以在集群之间自动拆分和平衡。


----

# Copy-on-write 的共享指针
Allows to work with shared immutable objects and sometimes unshare and mutate you own unique copy. Usage:

> 允许使用共享的**不可变对象**，还支持取消共享，在自己唯一的副本上修改。用法：

```cpp
class Column : public COW<Column>
{
private:
    friend class COW<Column>;
    /// Leave all constructors in private section. They will be available through 'create' method.
    Column();
    /// Provide 'clone' method. It can be virtual if you want polymorphic behaviour.
    virtual Column * clone() const;
public:
    /// Correctly use const qualifiers in your interface.
    virtual ~Column() {}
};
```

It will provide `create` and `mutate` methods. And `Ptr` and `MutablePtr` types. `Ptr` is refcounted pointer to immutable object. `MutablePtr` is refcounted noncopyable pointer to mutable object. `MutablePtr` can be assigned to `Ptr` through move assignment. 
- `create` method creates `MutablePtr`: you cannot share mutable objects. To share, move-assign to immutable pointer.
- `mutate` method allows to create mutable noncopyable object from immutable object: either by cloning or by using directly, if it is not shared.

These methods are thread-safe. Example:

```cpp
/// Creating and assigning to immutable ptr.
Column::Ptr x = Column::create(1);
/// Sharing single immutable object in two ptrs.
Column::Ptr y = x;
/// Now x and y are shared.
/// Change value of x.
{
    /// Creating mutable ptr. It can clone an object under the hood if it was shared.
    Column::MutablePtr mutate_x = std::move(*x).mutate();
    /// Using non-const methods of an object.
    mutate_x->set(2);
    /// Assigning pointer 'x' to mutated object.
    x = std::move(mutate_x);
}
/// Now x and y are unshared and have different values.
```

Note. You may have heard that COW is bad practice. Actually it is, if your values are small or if copying is done implicitly.  This is the case for string implementations. In contrast, COW is intended for the cases when you need to share states of large objects, (when you usually will use std::shared_ptr) but you also want precise control over modification of this shared state.

> 注意。您可能听说过 **COW** 是不好的做法。如果值很小或有隐式复制，则确实如此，字符串的实现就是这种情况。相比之下，**COW** 用于需共享大型对象状态的情况（通常使用 `std :: shared_ptr`），同时还希望精确控制此共享状态的修改。

Caveats:

- after a call to `mutate` method, you can still have a reference to immutable ptr somewhere.
- as `mutable_ptr` should be unique, it's refcount is redundant - probably it would be better to use `std::unique_ptr` for it somehow.

---
# How to speed up LZ4 decompression in ClickHouse?

When you run queries in [ClickHouse](https://clickhouse.yandex/), you might notice that the profiler often shows the `LZ_decompress_fast` function near the top. What is going on? This question had us wondering how to choose the best compression algorithm.

ClickHouse stores data in compressed form. When running queries, ClickHouse tries to do as little as possible, in order to conserve CPU resources. In many cases, all the potentially time-consuming computations are already well optimized, plus the user wrote a well thought-out query. Then all that's left to do is to perform decompression.

![img](https://habrastorage.org/getpro/habr/post_images/057/302/aba/057302aba5041790af404c2c781c4dd3.png)

So why does LZ4 decompression becomes a bottleneck? LZ4 seems like an [extremely light algorithm](https://github.com/lz4/lz4/): the data decompression rate is usually from 1 to 3 GB/s per processor core, depending on the data. This is much faster than the typical disk subsystem. Moreover, we use all available CPU cores, and decompression scales linearly across all physical cores.

However, there are two points to keep in mind. First, compressed data is read from disk, but the decompression speed is given in terms of the amount of uncompressed data. If the compression ratio is large enough, there is almost nothing to read from the disks. But there will be a lot of decompressed data, and this naturally affects CPU utilization: in the case of LZ4, the amount of work necessary to decompress data is almost proportional to the volume of the decompressed data itself.

Second, if data is cached, you might not need to read data from disks at all. You can rely on page cache or use your own cache. ==Caching is more efficient in column-oriented databases, since only frequently used columns stay in the cache. This is why LZ4 often appears to be a bottleneck in terms of CPU load==.

> 在面向列的数据库中，缓存保留的是经常使用的列，所以缓存效率更高。这就是为什么 LZ4 经常看起来是 CPU 瓶颈。

This brings up two more questions. First, if decompression is slowing us down, is it worth compressing data to begin with? But this speculation is irrelevant in practice. Until recently, the ClickHouse configuration offered only two data compression options — LZ4 and [Zstandard](https://github.com/facebook/zstd/). LZ4 is used by default. Switching to Zstandard makes compression stronger and slower. ==But there wasn't an option to completely disable compression, since LZ4 is assumed to provide a reasonable minimal compression that can always be used. (Which is exactly why I love LZ4.)==

> 但没有完全禁用压缩的选项，因为假定 LZ4 提供了一个始终可用的、合理的、最小压缩（这正是我喜欢 LZ4 的原因）。

But then a mysterious stranger appeared in the [international ClickHouse support chat](https://t.me/clickhouse_en) who said that he has a very fast disk subsystem (with NVMe SSD) and decompression is the only thing slowing his queries down, so it would be nice to be able to store data without compression. I replied that we don't have this option, but it would be easy to add. A few days later, we got a [pull request](https://github.com/yandex/ClickHouse/pull/1045) implementing the compression method `none`. I asked the contributor to report back on how much this option helped to accelerate queries. The response was that this new feature turned out to be useless in practice, since the uncompressed data started to take up too much disk space and didn't fit into those NVMe drives.

The second question that arises is that if there is a cache, why not use it to store data that is already decompressed? This is a viable possibility that will eliminate the need for decompression in many cases. ClickHouse also has a cache like this: [the cache of decompressed blocks](https://clickhouse.yandex/docs/en/operations/settings/settings/#use_uncompressed_cache). But it's a pity to waste a lot of RAM on this. So usually it only makes sense to use on small, sequential queries that use nearly identical data.

Our conclusion is that it's always preferable to store data in compressed format. Always write data to disk in compressed format. Transmit data over the network with compression, as well. In my opinion, default compression is justifiable even when transferring data within a single data center in a 10 GB network without oversubscription, while transferring uncompressed data between data centers is just unacceptable.

### Why LZ4?


Why choose LZ4? Couldn't we choose something even lighter? Theoretically, we could, and this is a good thought. But let's look at the class of algorithms that LZ4 belongs to.

First of all, it's generic and doesn't adapt the data type. For example, if you know in advance that you will have an array of integers, you can use one of the VarInt algorithms and this will use the CPU more effectively. Second, LZ4 is not overly dependent on data model assumptions. Let's say you have an ordered time series of sensor values, an array of floating-point numbers. If you take this into account you can calculate deltas between these numbers and then compress them with generic algorithm, which will result in higher compression ratio.

You won't have any problems using LZ4 with any byte arrays or any files. Of course it does have a specialization (more on that later), and in some cases its use is pointless. But if we call it a general-purpose algorithm, we'll be fairly close to the truth. We should note that thanks to its internal design, LZ4 automatically implements the [RLE](https://en.wikipedia.org/wiki/Run-length_encoding) algorithm as a special case.

However, the more important question is whether LZ4 is the most optimal algorithm of this class in terms of overall speed and strength of compression. Optimal algorithms are called the **<u>==Pareto frontier==</u>**, which means that there is no other algorithm that is definitively better in one way and not worse in other ways (and on a wide variety of datasets, as well). Some algorithms are faster but result in a smaller compression ratio, while others have stronger compression but are slower to compress or decompress.

To be honest, LZ4 is not really the Pareto frontier — there are some options available that are just a tiny bit better. For instance, look at [LZTURBO](https://sites.google.com/site/powturbo/) from a developer nicknamed [powturbo](https://github.com/powturbo). There is no doubt about the reliability of the results, thanks to the [encode.ru](https://encode.ru/) community (the largest and possibly the only forum on data compression). Unfortunately, the developer does not distribute the source code or binaries; they are only available to a limited number of people for testing, or for a lot of money (though it looks like no one has yet paid for it, yet). Also take a look at [Lizard](https://github.com/inikep/lizard/) (previously LZ5) and [Density](https://github.com/centaurean/density). They might work slightly better than LZ4 when you select a certain compression level. Another really interesting option is [LZSSE](https://github.com/ConorStokes/LZSSE/). But finish reading this article before you check it out.

> 为什么选择 LZ4？不能选择更轻量级的压缩算法吗？ 理论可以，也是个好主意。 但让我们看看 LZ4 所属的算法类别。
>
> 首先，它是通用的压缩算法，不挑数据类型。例如，如果你事先知道是一个整数数组，可以选用 [VarInt 算法](#wiki)，将更有效地利用 CPU。其次，LZ4 并不过分假设数据模型。假设有一个有序的传感器时间序列值，一个浮点数数组。如果是这样的数据模型，可以计算数字之间的增量，然后用通用算法压缩，能获得更高的压缩比。
>
> 使用 LZ4 压缩任何字节数组或任何文件不会有任何问题。 当然，它确实具有专门性（稍后会详细介绍），而且在某些情况下，使用它没有意义。但我们将其称为通用算法，也的确非常接近事实。 请注意，由于其内部设计，LZ4 将 [RLE](https://en.wikipedia.org/wiki/Run-length_encoding) 算法作为一个特例自动实现。
>
> 但是，更重要的问题是，就整体速度和压缩强度而言，LZ4 是否是此类中的最佳算法。最优算法被称为**<u>==帕累托前沿 （Pareto-frontier）==</u>**，意味着没有一个算法在某方面绝对更好，而在其他方式（以及各种各样的数据集）上却不差。有些算法速度更快，但压缩率更小；而其他算法压缩能力更强，但压缩或解压缩速度较慢。
>
>
> 老实说，LZ4 并不是真正的**<u>==帕累托前沿==</u>**：有些选择只是稍好一点。例如， [LZTURBO](https://sites.google.com/site/powturbo/) 来自一个绰号为 [powturbo](https://github.com/powturbo) 的开发者。多亏了 [encode.ru](https://encode.ru/) 社区（最大的，可能也是唯一的数据压缩论坛），结果的可靠性是毋庸置疑的。不幸的是，开发人员并没有分发源代码或二进制文件；只提供给有限数量的人进行测试，或者付很多钱买它（尽管看起来还没有人为此付费）。还可以看看 [Lizard](https://github.com/inikep/lizard/)（以前是LZ5）和 [Density](https://github.com/centaurean/density)，选择某个特定的压缩级别时，可能比 LZ4 稍好一些。另一个非常有趣的选择是 [LZSSE](https://github.com/ConorStokes/LZSSE/)。但先看完这篇文章再看它。
>

### How LZ4 works


Let's look at how LZ4 works in general. This is one of the implementations of the LZ77 algorithm. L and Z represent the developers' names (Lempel and Ziv), and 77 is for the year 1977 when the algorithm was published. It has many other implementations: QuickLZ, FastLZ, BriefLZ, LZF, LZO, and gzip and zip if low compression levels are used.

A data block compressed using LZ4 contains a sequence of **<u>==entries==</u>** (commands or instructions) of two types:

1. **Literals**: "Take the following N bytes as-is and copy them to the result".
2. **Match**: "Take N bytes from the decompressed result starting at the offset value relative to the current position".


Example. Before compression:

```
Hello world Hello
```


After compression:

```
literals 12 "Hello world " match 5 12
```


If we take a compressed block and iterate the cursor through it while running these commands, we will get the original uncompressed data as the result.

So that's basically how data is decompressed. The basic idea is clear: to perform compression, the algorithm encodes a repeated sequence of bytes using matches.

Some characteristics are also clear. This byte-oriented algorithm does not dissect individual bytes; it only copies them in their entirety. This is how it differs from entropy encoding. For instance, [zstd](https://github.com/facebook/zstd/) is a combination of LZ77 and entropy encoding.

Note that the size of the compressed block shouldn't be too large. The size is chosen to avoid wasting a lot of RAM during decompression, to avoid slowing down random access too much in the compressed file (which consists of a large number of compressed blocks), and sometimes so the block will fit in a CPU cache. For example, you can choose 64 KB so that the buffers for compressed and uncompressed data will fit in the L2 cache with half still free.

If we need to compress a larger file, we can concatenate the compressed blocks. This is also convenient for storing additional data (like a checksum) with each compressed block.

The maximum offset for the match is limited. In LZ4, the limit is 64 kilobytes. This amount is called the **sliding window**. This means that matches can be found in a window of 64 kilobytes preceding the cursor, which slides with the cursor as it moves forward.

Now let's look at how to compress data, or, in other words, how to find matching sequences in a file. You can always use a suffix trie (it's great if you've actually heard of this). There are methods that guarantee that the longest match is located in the preceding bytes after compression. This is called optimal parsing and it provides [nearly](http://fastcompression.blogspot.com/2011/12/advanced-parsing-strategies.html) the best compression ratio for a fixed-format compressed block. But there are better approaches, such as finding a good-enough match that is not necessarily the longest. The most efficient way to find it is using a hash table.

To do this, we iterate the cursor through the original block of data and take a few bytes after the cursor (let's say 4 bytes). We hash them and put the offset from the beginning of the block (where the 4 bytes were taken from) into the hash table. The value 4 is called "min-match" — using this hash table, we can find matches of at least 4 bytes.

If we look at the hash table and it already has a matching record, and the offset doesn't exceed the sliding window, we check to see how many more bytes match after those 4 bytes. Maybe there are a lot more matches. It is also possible that there is a collision in the hash table and nothing matches, but this is not a big deal. You can just replace the value in the hash table with a new one. Collisions in the hash table will simply lead to a lower compression ratio, since there will be fewer matches. By the way, this type of hash table (with a fixed size and no resolution of collisions) is called a "cache table". This name makes sense because in the event of a collision, the cache table simply forgets about the old entry.

> A challenge for the careful reader. Let's assume that the data is an array of UInt32 numbers in little endian format that represents a part of a sequence of natural numbers: 0, 1, 2… Explain why this data isn't compressed when using LZ4 (the size of the compressed data isn't any smaller compared to the uncompressed data).

>先看一下 LZ4 的总体工作原理，它是 LZ77 算法的实现之一。 L和Z代表开发人员的名称（Lempel和Ziv），而 77 代表该算法于 1977 年发布。还有其他各种实现：QuickLZ，FastLZ，BriefLZ，LZF，LZO，gzip 和 zip（使用低压缩级别时）。
>
>使用LZ4压缩的数据块包含两种类型的**<u>==记录==</u>**（命令或指令）序列：
>
>1. **字面量**（Literals）：将以下 N 个字节按原样复制到结果中。
>2. **匹配**（Match）：从相对于当前位置的偏移值开始，从解压缩结果中获取 N 个字节。
>
>例子。压缩前：
>
>```
>Hello world Hello
>```
>
>压缩后：
>
>```
>literals 12 "Hello world " match 5 12
>```
>
>通过**游标**遍历压缩块并执行这些命令，将得到原始的未压缩数据。
>
>因此，这就是数据解压缩的基本方式。 基本算法思想很清楚：找到匹配的重复字节序列，然后编码以完成压缩。
>
>有些特点很明显，这种面向字节的算法不分解单个字节；它只复制整个字节。这就是它与**==熵编码==**的区别。例如，[zstd ](https://github.com/facebook/zstd/) 是 LZ77 和熵编码的组合。
>
>请注意，压缩块的大小不应太大，要避免在解压期间浪费大量 RAM；避免在压缩文件（由大量压缩块组成）中出现过多地随机访问，从而降低速度；压缩块能装入 CPU 缓存最好。例如，选择64 KB，以便压缩和未压缩数据的缓冲区装入 L2 缓存后，还有一半可用。
>
>如果需要压缩更大的文件，则可以串联压缩块。 这也便于在每个压缩块中存储其它数据（如校验和）。
>
>**<u>==匹配==</u>**的最大偏移量有上限。在 LZ4 中是 64 KB，称为**滑动窗口**。这意味着可以在游标前面 64KB 的窗口中找到匹配项，当游标前移时，滑窗会随游标一起前移。
>
>现在让我们看看如何压缩数据，或者换句话说，如何在文件中找到匹配的序列。你总是可以使用一个**后缀 trie**（如果你真的听说过这个，那就太棒了）。**<u>==有一些方法可以保证最长匹配在压缩后位于前面的字节中。这称为最优解析，它为固定格式压缩块提供了[几乎](http://fastcompression.blogspot.com/2011/12/advanced-parsing-strategies.html)最佳的压缩比==</u>**。但也有更好的方法，比如找到一个足够好的匹配，而不一定是最长的。最有效方法是使用哈希表查找匹配序列。
>
>为此，我们使用游标遍历原始数据块的数据，用游标之后的几个字节（比方说 4 个字节）。这几个字节作为哈希表的键，并将从块开头的偏移量（获取这4个字节的偏移量）作为值放入哈希表中。 值 4 称为**最小匹配**：即使用此哈希表，我们可以找到至少 4 个字节的匹配项。
>
>如果我们检查哈希表，发现有一个匹配的记录，偏移量也在滑窗内，那么我们会检查这4个字节之后还可匹配多少字节。也许还有更多的匹配，也许只是哈希表冲突，并没有匹配项，但这不是什么大问题。可以将哈希表中的值替换为新值。 哈希表中的冲突只会导致较低的压缩率，因为匹配项会更少。顺便说一句，这种类型的哈希表（具有固定的大小，没有冲突解决方案）称为**缓存表**。 这个名称很有意义，因为在发生冲突的情况下，缓存表只是忽略旧记录。
>
>> 对认真读者的挑战。假设数据是小端格式 `UInt32` 的数字数组，表示自然数序列的一部分：`0、1、2…`，解释 LZ4 为什么不能压缩此数据（压缩后的数据量并没有变小）。

### How to speed everything up


So I want to speed up LZ4 decompression. Let's see how the decompression loop looks like. Here it is in pseudocode:

```C
while (...)
{
    read(input_pos, literal_length, match_length);

    copy(output_pos, input_pos, literal_length);
    output_pos += literal_length;

    read(input_pos, match_offset);

    copy(output_pos, output_pos - match_offset,
        match_length);
    output_pos += match_length;
}
```


LZ4 format is designed so that literals and matches alternate in a compressed file. Obviously, the literal always comes first (because there's nowhere to take a match from at the very beginning). Therefore, their lengths are encoded together.

It's actually a little more complicated than that. One byte is read from the file, and then it's split into two nibbles (half-bytes) which contain the encoded numbers 0 to 15. If the corresponding number is not 15, it is assumed to be the length of the literal and match, respectively. And if it is 15, the length is longer and it is encoded in the following bytes. Then the next byte is read, and its value is added to the length. If it is equal to 255, the same thing is done with the next byte.

Note that the maximum compression ratio for LZ4 format does not reach 255. And another useless observation is that if your data is very redundant, using LZ4 twice will improve the compression ratio.

When we read the length of a literal (and then the match length and the match offset), just copying two blocks of memory is enough to decompress it.

> 所以，我们想加快 LZ4 的解压速度。先看看解压循环是啥样子。下面是伪代码：
>
> ```C
> while (...) {
>   read(input_pos, literal_length, match_length);
>    copy(output_pos, input_pos, literal_length);
>   output_pos += literal_length;
>  
>    read(input_pos, match_offset);
>   copy(output_pos, output_pos - match_offset, match_length);
>    output_pos += match_length;
> }
>  ```
>    
>  LZ4 的格式使得压缩文件中交替出现字面量和匹配项。显然，字面量总是先出现的（因为从一开始也没地方匹配）。因此，字面量的长度也被一起编码。
> 
> 其实比这要复杂一点。从文件中读取一个字节，然后将其分为两半，每半 4 位，包含编码数字0到15。如果对应的数字小于15，则假定它分别是字面量和匹配的长度。如果它是15，那么长度就更长，表明高位编码在接下来的字节中，读取下一个字节，并将其值加到长度中。如果它等于255，则对下一个字节执行相同的操作。
>
> 请注意，LZ4 格式的最大压缩率达不到 255。另一个没用的观察是，如果数据非常冗余，则使用 LZ4 两次会提高压缩率。
>
> **<u>==读取字面量长度，然后读取匹配长度和匹配偏移量，再复制两个内存块就足以完成解压==</u>**。

### How to copy a memory block


It would seem that you could just use the `memcpy` function, which is designed to copy memory blocks. But this is not the optimal approach and not really appropriate.

Using memcpy isn't optimal because:

1. It is usually located in the libc library (and the libc library is usually dynamically linked, so the memcpy call will be made indirectly via PLT).
2. It is not inlined by compiler if the size argument is unknown at compile time.
3. It puts out a lot of effort to correctly process the leftovers of a memory block that are not multiples of the machine word length or register.


The last point is the most important. Let's say we asked the memcpy function to copy exactly 5 bytes. It would be great to copy 8 bytes right away, using two movq instructions.

```
Hello world Hello wo...^^^^^^^^ - src      ^^^^^^^^ - dst
```

But then we'll be copying three extra bytes, so we'll be writing outside of buffer bounds. The `memcpy` function doesn't have permission to do this, because it could overwrite some data in our program and lead to a memory stomping bug. And if we wrote to an unaligned address, these extra bytes could land on an unallocated page of virtual memory or on a page without write access. That would give us a segmentation fault (this is good).

But in our case, we can almost always write extra bytes. We can read extra bytes in the input buffer as long as the extra bytes are located entirely inside it. Under the same conditions, we can write the extra bytes to the output buffer, because we will still overwrite them on the next iteration.

This optimization is already in the original implementation of LZ4:

```c
inline void copy8(UInt8 * dst, const UInt8 * src)
{
    memcpy(dst, src, 8);    /// Note that memcpy isn't actually called here.
}

inline void wildCopy8(UInt8 * dst, const UInt8 * src, UInt8 * dst_end)
{
    do
    {
        copy8(dst, src);
        dst += 8;
        src += 8;
    } while (dst < dst_end);
}
```


To take advantage of this optimization, we just need to make sure that we are far enough away from the buffer bounds. This shouldn't cost anything, because we are already checking for buffer overflow. And processing the last few bytes, the "leftover" data, can be done after the main loop.

However, there are still a few nuances. Copying occurs twice in the loop: with a literal and a match. However, when using the `LZ4_decompress_fast` function (instead of `LZ4_decompress_safe`), the check is performed only once, when we need to copy the literal. The check is not performed when copying the match, but the [specification for the LZ4 format](https://github.com/lz4/lz4/blob/master/doc/lz4_Block_format.md) has conditions that allow you to avoid it:

> The last 5 bytes are always literals.
> The last match must start at least 12 bytes before the end of block.
> Consequently, a block with less than 13 bytes cannot be compressed.


Specially selected input data may lead to memory corruption. If you use the `LZ4_decompress_fast` function, you need protection from bad data. At the very least, you should calculate checksums for the compressed data. If you need protection from hackers, use the `LZ4_decompress_safe` function. Other options: take a cryptographic hash function as the checksum (although this is likely to destroy performance); allocate more memory for buffers; allocate memory for buffers with a separate `mmap` call and create a guard page.

When I see code that copies 8 bytes of data, I immediately wonder why exactly 8 bytes. You can copy 16 bytes using SSE registers:

```C
inline void copy16(UInt8 * dst, const UInt8 * src)
{
#if __SSE2__
    _mm_storeu_si128(reinterpret_cast<__m128i *>(dst),
        _mm_loadu_si128(reinterpret_cast<const __m128i *>(src)));
#else
    memcpy(dst, src, 16);
#endif
}

inline void wildCopy16(UInt8 * dst, const UInt8 * src, UInt8 * dst_end)
{
    do
    {
        copy16(dst, src);
        dst += 16;
        src += 16;
    } while (dst < dst_end);
}
```


The same thing works for copying 32 bytes for AVX and 64 bytes for AVX-512. In addition, you can unroll the loop several times. If you have ever looked at how `memcpy` is implemented, this is exactly the approach that is used. (By the way, the compiler won't unroll or vectorize the loop in this case, because this will require inserting bulky checks.)

Why didn't the original LZ4 implementation do this? First, it isn't clear whether this is better or worse. The resulting gain depends on the size of the blocks to copy, so if they are all short, it would be creating extra work for nothing. And secondly, it ruins the provisions in the LZ4 format that help avoid an unnecessary branch in the internal loop.

However, we will keep this option in mind for the time being.

> 看起来只能使用 `memcpy` 函数，它就是用来复制内存块的。 但这不是最佳方法，也非真正合适的方法。
>
> `memcpy` 不是最佳选择，是因为：
>
> 1. 它通常位于libc库中（通常动态链接 libc 库，因此将通过PLT（过程链接表）间接调用 `memcpy`）。
> 2. 如果在编译时不知道 size 参数，则编译器不会内联。
> 3. 正确处理**<u>==不是机器字长或寄存器倍数的==</u>**剩余内存，需要花费大量精力。
>
> 最后一点最重要。 假设要 `memcpy` 函数精确复制 5 个字节。用两个 `movq` 指令立即复制 8 个字节，是个不错的选择。
>
> ```
> Hello world Hello wo...^^^^^^^^ - src      ^^^^^^^^ - dst
> ```
>
> 但这会复制三个额外的字节，所以将会写到缓冲区边界之外。`memcpy` 函数无权执行此操作，因为这可能会覆盖程序中的某些数据并导致内存占用错误。如果我们写到一个未对齐的地址，这些额外的字节可能会落在虚拟内存未分配的页上，也可能落在没有写访问权限的页上。这将导致 [segmentation fault](https://blog.csdn.net/u010150046/article/details/77775114)（这种情况下出现这种错误是对的）。
>
> 解压的场景下，几乎总是可以多复制额外的字节。只要多余的字节完全位于输入缓冲区内部，就可以多读字节。同样的条件下，我们也可以将额外的字节拷贝到输出缓冲区，因为将在下一次迭代中覆盖本轮多拷贝的字节。
>
> 此优化已在 LZ4 的原始实现中实现：
>
> ```C
> inline void copy8(UInt8 * dst, const UInt8 * src) {
>     memcpy(dst, src, 8);    /// Note that memcpy isn't actually called here.
> }
> 
> inline void wildCopy8(UInt8 * dst, const UInt8 * src, UInt8 * dst_end)  {
>     do {
>         copy8(dst, src);
>         dst += 8;
>         src += 8;
>     } while (dst < dst_end);
> }
> ```
>
> 要利用这种优化，只要确保距缓冲区边界足够远即可。 因为我们已经检查了缓冲区溢出，这就没啥代价。 处理最后几个字节，即“剩余”数据，可以在主循环之后完成。
>
> 但仍有一些细微差别。复制在循环中发生两次：一次复制字面量，一次复制匹配项。但当使用`LZ4_decompress_fast`函数（而不是`LZ4_decompress_safe`）时，只在复制字面量时检查一次，复制匹配项时不检查，[LZ4格式的规范](https://github.com/lz4/lz4/blob/master/doc/lz4_Block_format.md) 允许某些条件下可以避免检查：
>
> 1. 最后 5 个字节总是**字面量**。
>
> 2. 最后一个匹配必须在块结束之前至少 12 个字节开始。
>
> 3. 因此不能压缩小于 13 字节的块。
>
> 特别选择的输入数据可能会导致内存损坏。 如果使用 `LZ4_decompress_fast` 函数，则需要避免受到坏数据的影响。至少，要计算压缩数据的校验和。如果要防止黑客攻击，则使用 `LZ4_decompress_safe` 函数。其他选择：采用加密哈希函数作为校验和（尽管这可能会破坏性能）； 为缓冲区分配更多内存； 通过单独的 `mmap` 调用为缓冲区分配内存，并创建一个保护页。

### Tricky copying


Let's go back to the question of whether it's always possible to copy data this way. Let's say we need to copy a match, that is, take a piece of memory from the output buffer that is located at some offset behind the cursor and copy it to the cursor position.

Imagine a simple case when you need to copy 5 bytes at an offset of 12:

```
Hello world ...........^^^^^ - src      ^^^^^ - dstHello world Hello wo...^^^^^ - src      ^^^^^ - dst
```

But there is a more difficult case, when we need to copy a block of memory that is longer than the offset. In other words, it includes some data that has not yet been written to the output buffer.

Copy 10 bytes at an offset of 3:

```
abc.............^^^^^^^^^^ - src  ^^^^^^^^^^ - dstabcabcabcabca...^^^^^^^^^^ - src  ^^^^^^^^^^ - dst
```

We have all the data during the compression process, and such a match may well be found. The `memcpy` function is not suitable for copying it, because it doesn't support the case when ranges of memory blocks overlap. The `memmove` function won't work either, because the block of memory that the data should be taken from has not been fully initialized yet. We need to copy the same way as if we were copying byte by byte.

```
op[0] = match[0];
op[1] = match[1];
op[2] = match[2];
op[3] = match[3];
...
```


Here's how it works:

```
abca............^ - src  ^ - dstabcab........... ^ - src  ^ - dstabcabc.......... ^ - src   ^ - dstabcabca.........  ^ - src   ^ - dstabcabcab........  ^ - src    ^ - dst
```

In other words, we must create a repeating sequence. The original implementation of LZ4 used some surprisingly strange code to do this:

```
const unsigned dec32table[] = {0, 1, 2, 1, 4, 4, 4, 4};
const int dec64table[] = {0, 0, 0, -1, 0, 1, 2, 3};

const int dec64 = dec64table[offset];
op[0] = match[0];
op[1] = match[1];
op[2] = match[2];
op[3] = match[3];
match += dec32table[offset];
memcpy(op+4, match, 4);
match -= dec64;
```


It copies the first 4 bytes one by one, skips ahead by some magic number, copies the next 4 bytes entirely, and moves the cursor to a match using another magic number. The author of the code ([Yan Collet](http://fastcompression.blogspot.com/)) somehow forgot to leave a comment about what this means. In addition, the variable names are confusing. They are both named dec...table, but one is added and the other is subtracted. In addition, one of them is unsigned, and the other is int. However, the author recently improved this place in the code.

Here's how it actually works. We copy the first 4 bytes one at a time:

```
abcabca.........^^^^ - src  ^^^^ - dst
```

Now we can copy 4 bytes at once:

```
abcabcabcab..... ^^^^ - src    ^^^^ - dst
```

We can continue as usual, copying 8 bytes at once:

```
abcabcabcabcabcabca..... ^^^^^^^^ - src      ^^^^^^^^ - dst
```

As we all know from experience, sometimes the best way to understand code is to rewrite it. Here's what we came up with:

```
inline void copyOverlap8(UInt8 * op, const UInt8 *& match, const size_t offset)
{
    /// 4 % n.
    /// Or if 4 % n is zero, we use n.
    /// It gives an equivalent result, but is more CPU friendly for unknown reasons.
    static constexpr int shift1[] = { 0, 1, 2, 1, 4, 4, 4, 4 };

    /// 8 % n - 4 % n
    static constexpr int shift2[] = { 0, 0, 0, 1, 0, -1, -2, -3 };

    op[0] = match[0];
    op[1] = match[1];
    op[2] = match[2];
    op[3] = match[3];

    match += shift1[offset];
    memcpy(op + 4, match, 4);
    match += shift2[offset];
}
```


As expected, this doesn't change the performance at all. I just really wanted to try optimization for copying 16 bytes at once.

However, this complicates the "special case" and causes it to be called more often (the `offset < 16` condition is performed at least as often as `offset < 8`). Copying overlapping ranges with 16-byte copying looks like this (only the beginning shown):

```
inline void copyOverlap16(UInt8 * op, const UInt8 *& match, const size_t offset)
{
    /// 4 % n.
    static constexpr int shift1[]
        = { 0,  1,  2,  1,  4,  4,  4,  4,  4,  4,  4,  4,  4,  4,  4,  4 };

    /// 8 % n - 4 % n
    static constexpr int shift2[]
        = { 0,  0,  0,  1,  0, -1, -2, -3, -4,  4,  4,  4,  4,  4,  4,  4 };

    /// 16 % n - 8 % n
    static constexpr int shift3[]
        = { 0,  0,  0, -1,  0, -2,  2,  1,  8, -1, -2, -3, -4, -5, -6, -7 };

    op[0] = match[0];
    op[1] = match[1];
    op[2] = match[2];
    op[3] = match[3];

    match += shift1[offset];
    memcpy(op + 4, match, 4);
    match += shift2[offset];
    memcpy(op + 8, match, 8);
    match += shift3[offset];
}
```


Can this function be implemented more effectively? We would like to find a magic SIMD instruction for such complex code, because all we want to do is write 16 bytes, which consist entirely of a few bytes of input data (from 1 to 15). Then they just need to be repeated in the correct order.

There is an instruction like this called `pshufb` (packed shuffle bytes) that is part of SSSE3 (three S's). It accepts two 16-byte registers. One of the registers contains the source data. The other one has the "selector": each byte contains a number from 0 to 15, depending on which byte of the source register to take the result from. If the selector's byte value is greater than 127, the corresponding byte of the result is filled with zero.

Here is an example:

```
xmm0: abc.............
xmm1: 0120120120120120

pshufb %xmm1, %xmm0

xmm0: abcabcabcabcabca
```


Every byte of the result is filled with the selected byte of the source data — this is exactly what we need! Here's what the code looks like in the result:

```
inline void copyOverlap16Shuffle(UInt8 * op, const UInt8 *& match, const size_t offset)
{
#ifdef __SSSE3__

    static constexpr UInt8 __attribute__((__aligned__(16))) masks[] =
    {
        0,  1,  2,  1,  4,  1,  4,  2,  8,  7,  6,  5,  4,  3,  2,  1, /* offset = 0, not used as mask, but for shift amount instead */
        0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0, /* offset = 1 */
        0,  1,  0,  1,  0,  1,  0,  1,  0,  1,  0,  1,  0,  1,  0,  1,
        0,  1,  2,  0,  1,  2,  0,  1,  2,  0,  1,  2,  0,  1,  2,  0,
        0,  1,  2,  3,  0,  1,  2,  3,  0,  1,  2,  3,  0,  1,  2,  3,
        0,  1,  2,  3,  4,  0,  1,  2,  3,  4,  0,  1,  2,  3,  4,  0,
        0,  1,  2,  3,  4,  5,  0,  1,  2,  3,  4,  5,  0,  1,  2,  3,
        0,  1,  2,  3,  4,  5,  6,  0,  1,  2,  3,  4,  5,  6,  0,  1,
        0,  1,  2,  3,  4,  5,  6,  7,  0,  1,  2,  3,  4,  5,  6,  7,
        0,  1,  2,  3,  4,  5,  6,  7,  8,  0,  1,  2,  3,  4,  5,  6,
        0,  1,  2,  3,  4,  5,  6,  7,  8,  9,  0,  1,  2,  3,  4,  5,
        0,  1,  2,  3,  4,  5,  6,  7,  8,  9, 10,  0,  1,  2,  3,  4,
        0,  1,  2,  3,  4,  5,  6,  7,  8,  9, 10, 11,  0,  1,  2,  3,
        0,  1,  2,  3,  4,  5,  6,  7,  8,  9, 10, 11, 12,  0,  1,  2,
        0,  1,  2,  3,  4,  5,  6,  7,  8,  9, 10, 11, 12, 13,  0,  1,
        0,  1,  2,  3,  4,  5,  6,  7,  8,  9, 10, 11, 12, 13, 14,  0,
    };

    _mm_storeu_si128(reinterpret_cast<__m128i *>(op),
        _mm_shuffle_epi8(
            _mm_loadu_si128(reinterpret_cast<const __m128i *>(match)),
            _mm_load_si128(reinterpret_cast<const __m128i *>(masks) + offset)));

    match += masks[offset];

#else
    copyOverlap16(op, match, offset);
#endif
}
```


Here `_mm_shuffle_epi8` is an [intrinsic](https://software.intel.com/sites/landingpage/IntrinsicsGuide/#text=_mm_shuffle_epi8), which compiles to the `pshufb` CPU instruction.

Can we perform this operation for more bytes at once using newer instructions? After all, SSSE3 is a very old instruction set that has been around since 2006. AVX2 has an instruction that does this for 32 bytes at once, but separately for individual 16-byte lanes. This is called vector permute bytes, rather than packed shuffle bytes — the words are different, but the meaning is the same. AVX-512 VBMI has another instruction that works for 64 bytes at once, but processors that support it have only appeared recently. ARM NEON has similar instructions called vtbl (vector table lookup), but they only allow writing 8 bytes.

In addition, there is a version of the `pshufb` instruction with 64-bit MMX registers in order to form 8 bytes. It is just right for replacing the original version of the code. However, I decided to use the 16-byte option instead (for serious reasons).

At the Highload++ Siberia conference, an attendee came up to me after my presentation and mentioned that for the 8-byte case, you can just use multiplication by a specially selected constant (you will also need an offset) — this hadn't even occurred to me before!

### How to remove a superfluous if statement


Let's say I want to use a variant that copies 16 bytes. How can I avoid having to do an additional check for buffer overflow?

I decided that I just wouldn't do this check. The comments on the function will say that the developer should allocate a block of memory for a specified number of bytes more than it is required, so that we can read and write unnecessary garbage there. The interface of the function will be harder to use, but this is a different issue.

Actually, there could be negative consequences. Let's say the data that we need to decompress was formed from blocks of 65,536 bytes each. Then the user gives us a piece of memory that is 65,536 bytes for the decompressed data. But with the new function interface, the user will be required to allocate a memory block that is 65,551 bytes, for example. Then the allocator may be forced to actually allocate 96 or even 128 kilobytes, depending on its implementation. If the allocator is very bad, it might suddenly stop caching memory in "heap" and start using `mmap` and `munmap` each time for memory allocation (or release memory using `madvice`). This process will be extremely slow because of page faults. As a result, this little bit of optimization might end up slowing everything down.

### Is there any acceleration?


So I made a version of the code that uses three optimizations:

1. Copying 16 bytes instead of 8.
2. Using the shuffle instructions for the `offset < 16` case.
3. Removed one extra if.


I started testing this code on different sets of data and got unexpected results.

Example 1:
Xeon E2650v2, Yandex Browser data, AppVersion column.
Reference: 1.67 GB/sec.
16 bytes, shuffle: 2.94 GB/sec (76% faster).

Example 2:
Xeon E2650v2, Yandex Direct data, ShowsSumPosition column.
Reference: 2.30 GB/sec.
16 bytes, shuffle: 1.91 GB/sec (20% slower).

I was really happy at first, when I saw that everything had accelerated by such a large percentage. Then I saw that nothing was any faster with other files. It was even a little bit slower for some of them. I concluded that the results depend on the compression ratio. The more compressed the file, the greater the advantage of switching to 16 bytes. This feels natural: the larger the compression ratio, the longer the average length of fragments to copy.

To investigate, I used C++ templates to make code variants for four cases: using 8-byte or 16-byte chunks, and with or without the shuffle instruction.

```cpp
template <size_t copy_amount, bool use_shuffle>
void NO_INLINE decompressImpl(
    const char * const source,
    char * const dest,
    size_t dest_size)
```


Completely different variants of the code performed better on different files, but when testing on a desktop the version with shuffle always won. Testing on a desktop is inconvenient because you have to do this:

```
sudo echo 'performance' | tee /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor
kill -STOP $(pidof firefox) $(pidof chromium)
```


Then I went on one of the old "development" servers (with the Xeon E5645 processor), took even more datasets, and got almost the opposite results, which totally confused me. It turns out that the choice of optimal algorithm depends on the processor model, in addition to the compression ratio. The processor determines when it is best to use the shuffle instruction, as well as the threshold for when to start using 16-byte copying.

By the way, when testing on our servers, it makes sense to do this:

```shell
sudo kill -STOP $(pidof python) $(pidof perl) $(pgrep -u skynet) $(pidof cqudp-client)
```


Otherwise, the results will be unstable. Also watch out for thermal throttling and power capping.

### How to choose the best algorithm


So we have four variants of the algorithm and we need to choose the best one for the conditions. We could create a representative set of data and hardware, then perform serious load testing and choose the method that is best on average. But we don't have a representative dataset. For testing I used a sample of data from [Yandex Metrica](https://clickhouse.yandex/docs/en/getting_started/example_datasets/metrica/), Yandex Direct, Yandex Browser, and [flights in the United States](https://clickhouse.yandex/docs/en/getting_started/example_datasets/ontime/). But this isn't sufficient, because ClickHouse is used by hundreds of companies around the world. By over-optimizing on one dataset, we might cause a drop in performance with other data and not even realize it. And if the results depend on the processor model, we'll have to explicitly write the conditions in the code and test it on each model (or consult the reference manual on timing instructions, what do you think?). In either case, this is too time-consuming.

So I decided to use another method, which is obvious to colleagues who studied at our School of Data Analysis: ["multi-armed bandits"](https://learnforeverlearn.com/bandits/). The point is that the variant of the algorithm is chosen randomly, and then we use statistics to progressively more often choose the variants that perform better.

We have many blocks of data that need to be decompressed, so we need independent function calls for decompressing data. We could choose one of the four algorithms for each block and measure its execution time. An operation like this usually costs nothing in comparison with processing a block of data, and in ClickHouse a block of uncompressed data is at least 64 KB. (Read this [article](http://btorpey.github.io/blog/2014/02/18/clock-sources-in-linux/) about measuring time.)

To get a better understanding of how the "multi-armed bandits" algorithm works, let's look at where the name comes from. This is an analogy with slot machines in a casino which have several levers that a player can pull to get some random amount of money. The player can pull the levers multiple times in any order. Each lever has a fixed probability for the corresponding amount of money given out, but the player does not know how it works and can only learn it from experience playing the game. Once they figure it out, they can maximize their winnings.

One approach to maximizing the reward is to evaluate the probability distribution for each lever at each step based on the game statistics from previous steps. Then we mentally "win" a random reward for each lever, based on the distributions received. Finally, we pull the lever that had the best outcome in our mental game. This approach is called Thompson Sampling.

But we are choosing a decompression algorithm. The result is the execution time in picoseconds per byte: the fewer, the better. We will consider the execution time to be a random variable and evaluate its distribution using mathematical statistics. The Bayesian approach is often used for tasks like this, but it would be cumbersome to insert complex formulas into C++ code. We can use a parametric approach and say that a random variable belongs to a parametric family of random variables, and then evaluate its parameters.

How do we select the family of random variables? As an example, we could assume that the code execution time has normal distribution. But this is absolutely wrong. First, the execution time can't be negative, and normal distribution takes values everywhere on the number line. Second, I assume that the execution time will have a heavy "tail" on the right end.

However, there are factors that could make it a good idea to estimate normal distribution only for the purposes of Thompson Sampling (despite the fact that the distribution of the target variable is not necessarily normal). The reason for this is that it is very easy to calculate the mathematical expectation and the variance, and after a sufficient number of iterations, a normal distribution becomes fairly narrow, not much different from the distributions that we would have obtained using other methods. If we aren't too concerned with the convergence rate at the first steps, these details can be ignored.

This may seem like a somewhat ignorant approach. Experience has shown us that the average time for query execution, website page loading, and so on is "garbage" that isn't worth calculating. It would be better to calculate the median, which is a [robust statistic](https://en.wikipedia.org/wiki/Robust_statistics). But this is a little more difficult, and as I will show later, the described method justifies itself for practical purposes.

At first I implemented calculation of the mathematical expectation and variance, but then I decided that this is too good, and I need to simplify the code to make it "worse":

```C
/// For better convergence, we don't use proper estimate of stddev.
/// We want to eventually separate the two algorithms even in cases
///  when there is no statistical significant difference between them.
double sigma() const
{
    return mean() / sqrt(adjustedCount());
}

double sample(pcg64 & rng) const
{
    ...
    return std::normal_distribution<>(mean(), sigma())(rng);
}
```


I wrote it so that the first few iterations were not taken into account, to eliminate the effect of memory latencies.

The result is a test program that can select the best algorithm for the input data, with optional modes that use the reference implementation of LZ4 or a specific version of the algorithm.

So there are six options:
— Reference (baseline): original LZ4 without our modifications.
— Variant 0: copy 8 bytes at a time without shuffle.
— Variant 1: copy 8 bytes at a time with shuffle.
— Variant 2: copy 16 bytes at a time without shuffle.
— Variant 3: copy 16 bytes at a time with shuffle.
— The "bandit" option, which selects the best of the four optimized variants.



### Testing on different CPUs


If the result strongly depends on the CPU model, it would be interesting to find out exactly how it is affected. There might be an exceptionally large difference on certain CPUs.

I prepared a set of datasets from different tables in ClickHouse with real data, for a total of 256 different files each with 100 MB of uncompressed data (the number 256 was coincidental). Then I looked at the CPUs of the servers where I can run benchmarks. I found servers with the following CPUs:
— Intel® Xeon® CPU E5-2650 v2 @ 2.60GHz
— Intel® Xeon® CPU E5-2660 v4 @ 2.00GHz
— Intel® Xeon® CPU E5-2660 0 @ 2.20GHz
— Intel® Xeon® CPU E5645 @ 2.40GHz
— Intel Xeon E312xx (Sandy Bridge)
— AMD Opteron(TM) Processor 6274
— AMD Opteron(tm) Processor 6380
— Intel® Xeon® CPU E5-2683 v4 @ 2.10GHz
— Intel® Xeon® CPU E5530 @ 2.40GHz
— Intel® Xeon® CPU E5440 @ 2.83GHz
— Intel® Xeon® CPU E5-2667 v2 @ 3.30GHz

The most interesting part comes next — the processors provided by the R&D department:
— AMD EPYC 7351 16-Core Processor, a new AMD server processor.
— Cavium ThunderX2, which is AArch64, not x86. For these, my SIMD optimization needed to be reworked a bit. The server has 224 logical and 56 physical cores.

There are 13 servers in total, and each of them runs the test on 256 files in 6 variants (reference, 0, 1, 2, 3, adaptive). The test is run 10 times, alternating between the options in random order. It outputs 199,680 results that we can compare.

For example, we can compare different CPUs with each other. But we shouldn't jump to conclusions from these results, because we are only testing the LZ4 decompression algorithm on a single core (this is a very narrow case, so we only get a micro-benchmark). For example, the Cavium has the lowest performance per single core. But I tested ClickHouse on it myself, and it wins out over Xeon E5-2650 v2 on heavy queries due to the greater number of cores, even though it is missing many optimizations that are made in ClickHouse specifically for the x86.



```
┌─cpu───────────────────┬──ref─┬─adapt─┬──max─┬─best─┬─adapt_boost─┬─max_boost─┬─adapt_over_max─┐
│ E5-2667 v2 @ 3.30GHz  │ 2.81 │  3.19 │ 3.15 │    3 │        1.14 │      1.12 │           1.01 │
│ E5-2650 v2 @ 2.60GHz  │ 2.5  │  2.84 │ 2.81 │    3 │        1.14 │      1.12 │           1.01 │
│ E5-2683 v4 @ 2.10GHz  │ 2.26 │  2.63 │ 2.59 │    3 │        1.16 │      1.15 │           1.02 │
│ E5-2660 v4 @ 2.00GHz  │ 2.15 │  2.49 │ 2.46 │    3 │        1.16 │      1.14 │           1.01 │
│ AMD EPYC 7351         │ 2.03 │  2.44 │ 2.35 │    3 │        1.20 │      1.16 │           1.04 │
│ E5-2660 0 @ 2.20GHz   │ 2.13 │  2.39 │ 2.37 │    3 │        1.12 │      1.11 │           1.01 │
│ E312xx (Sandy Bridge) │ 1.97 │  2.2  │ 2.18 │    3 │        1.12 │      1.11 │           1.01 │
│ E5530 @ 2.40GHz       │ 1.65 │  1.93 │ 1.94 │    3 │        1.17 │      1.18 │           0.99 │
│ E5645 @ 2.40GHz       │ 1.65 │  1.92 │ 1.94 │    3 │        1.16 │      1.18 │           0.99 │
│ AMD Opteron 6380      │ 1.47 │  1.58 │ 1.56 │    1 │        1.07 │      1.06 │           1.01 │
│ AMD Opteron 6274      │ 1.15 │  1.35 │ 1.35 │    1 │        1.17 │      1.17 │              1 │
│ E5440 @ 2.83GHz       │ 1.35 │  1.33 │ 1.42 │    1 │        0.99 │      1.05 │           0.94 │
│ Cavium ThunderX2      │ 0.84 │  0.87 │ 0.87 │    0 │        1.04 │      1.04 │              1 │
└───────────────────────┴──────┴───────┴──────┴──────┴─────────────┴───────────┴────────────────┘
```



- ref, adapt, max — The speed in gigabytes per second (the value that is the reverse of the arithmetic mean of time for all launches on all datasets).
- best — The number of the best algorithm among the optimized variants, from 0 to 3.
- adapt_boost — The relative advantage of the adaptive algorithm compared to the baseline.
- max_boost — The relative advantage of the best of the non-adaptive variants compared to the baseline.
- adapt_over_max — The relative advantage of the adaptive algorithm over the best non-adaptive one.


The results show that we were able to speed up decompression by 12-20% on modern x86 processors. Even on ARM we saw 4% improvement, despite the fact that we didn't optimize much for this architecture. It is also clear that on average for different datasets, the "bandit" algorithm comes out ahead of the pre-selected best variant on all processors (except for very old Intel CPUs).

### Conclusion


In practice, the usefulness of this work is dubious. Yes, LZ4 decompression was accelerated on average by 12-20%, and on some datasets the performance more than doubled. But in general, this doesn't have much effect on query execution time. It's difficult to find real queries that gain more than a couple percent in speed.

We decided to use ZStandard level 1 instead of LZ4 on several Yandex Metrica clusters intended for executing long queries, because it is more important to save IO and disk space on cold data. Keep this in mind if you have similar workload.

We observed the greatest benefits from optimizing decompression in highly compressible data, such as columns with mostly duplicate string values. However, we have developed a separate solution specifically for this scenario that allows us to significantly speed up queries over this kind of data.

Another point to remember is that optimization of decompression speed is often limited by the format of the compressed data. LZ4 uses a very good format, but Lizard, Density and LZSSE have other formats that can work faster. Perhaps instead of trying to accelerate LZ4, it would be better to just integrate LZSSE into ClickHouse.

It's unlikely that these optimizations will be implemented in the mainstream LZ4 library: in order to use them, the library interface would have to be modified. In fact, this is often the case with improving algorithms — optimizations don't fit into old abstractions and they have to be revised. However, variable names have already been corrected in the original implementation. For instance, inc and dec tables have been [corrected](https://github.com/lz4/lz4/blob/dev/lib/lz4.c#L313). In addition, about a month ago, the original implementation accelerated decompression by the same 12-15% by copying 32 bytes instead of 16, as discussed above. We tried the 32-byte option ourselves and the results were not that great, but they were still [faster](https://habrastorage.org/webt/d0/jn/ia/d0jniaidtjaqnnu2ek3fbznv8ji.png).

If you look at the profile at the beginning of the article, you may notice that we could have removed one extra copying operation from the page cache to userspace (either using `mmap`, or using `O_DIRECT` and userspace page cache, but both options are problematic). We also could have slightly improved the checksum calculation (CityHash128 is currently used without CRC32-C, but we could use HighwayHash, FARSH or XXH3). Acceleration of these two operations is useful for weakly compressed data, since they are performed on compressed data.

In any case, the changes have already been added to master more than a year ago, and the ideas that resulted from this research have been applied in other tasks. You can also watch the [video](https://www.youtube.com/watch?v=V2CqQBICt7M) from HighLoad++ Siberia, or view the [presentation](https://yandex.github.io/clickhouse-presentations/highload_siberia_2018/) (both in Russian).