# Introducing the Gandiva Initiative for Apache Arrow

https://www.infoworld.com/article/3318121/why-you-should-use-gandiva-for-apache-arrow.html


### Summary

We recently announced [The Gandiva Initiative](https://github.com/dremio/gandiva) for Apache Arrow. This is a new execution kernel for Arrow that is based on LLVM. Gandiva provides very significant performance improvements for low-level operations on Arrow buffers. We first included this work in Dremio to improve the efficiency and performance of analytical workloads on our platform, which will become available to users later this year. In this post we will describe the motivation for the <u>**==initiative==**</u>, implementation details, some performance results, and some plans for the future.

A note on the name: [Gandiva](https://en.wikipedia.org/wiki/Gandiva) is a mythical **<u>==bow==</u>** from the Indian epic [The Mahabharata](https://en.wikipedia.org/wiki/Mahabharata) used by the hero Arjuna. According to the story Gandiva is **<u>==indestructible==</u>**, and it makes the arrows it fires 1000x more powerful.

关于名称的注释：[Gandiva](https://en.wikipedia.org/wiki/Gandiva) 是古印度史诗《[摩诃婆罗多](https://zh.wikipedia.org/wiki/摩诃婆罗多)》中英雄[阿周那](https://zh.wikipedia.org/wiki/阿周那)所持之弓。这个故事里，Gandiva 坚不可摧，它使发射的箭（Arrow）的威力提高了1000倍。

> Gandiva，[甘提婆](https://zh.wikipedia.org/wiki/%E7%94%98%E6%8F%90%E5%A9%86)

### About Arrow

Apache Arrow is a cross-platform standard for columnar data for in-memory processing. You can think of Arrow as the in-memory counterpart to popular on-disk formats like Apache Parquet and Apache ORC, and increasingly as the standard used by many different systems. The Arrow project provides several components for users to build into their projects:

- a columnar specification that is optimized for modern CPUs and GPUs, which is designed to represent both flat and hierarchical data structures like JSON and Tensors
- zero-copy reads that eliminate the need for serializing and de-serializing data across platforms
- computational libraries in popular languages, including Java, C++, C, Python, Ruby, Go, Rust, and JavaScript

Projects like pandas in Python use Arrow to improve the efficiency and performance of accessing data in-memory through the dataframe APIs. As another example, Dremio uses Arrow in our SQL execution engine: as data is accessed from different sources, we read the data into native Arrow buffers directly, and all processing is performed on these buffers.

You can learn more about the origins and history of Arrow [here](https://www.dremio.com/origin-history-of-apache-arrow/). Details on the specification and associated libraries are available [here](https://arrow.apache.org/).

### About LLVM

LLVM is an open source compiler that was originally developed by Swift language creator Chris Lattner in 2000, and is extensively used by Apple. While LLVM is an alternative to GCC for general purpose compiling needs, it also provides Just-in-Time compilation capabilities that can incorporate runtime information to produce highly optimized assembly code for the fastest possible evaluation.

For Gandiva we wanted to take advantage of the just-in-time compilation abilities of LLVM to improve the efficiency and latency of operations on Arrow buffers. Before Gandiva, in Dremio SQL queries were dynamically compiled to efficient byte code for execution by the JVM. While this provides very significant advantages over interpreted processing of SQL expressions, we believed that by using LLVM we could take another major step forward in terms of making more optimal use of the underlying CPU and GPU architecture available for processing.

By combining LLVM with Apache Arrow libraries, Gandiva can perform low-level operations on Arrow in-memory buffers such as sorts, filters, and projections that are highly optimized for specific runtime environments, improving resource utilization and providing faster, lower-cost operations of analytical workloads.

### Gandiva Architecture

Gandiva is a new open source project licensed under the Apache license and developed in the open on GitHub. It is provided as a standalone C++ library for efficient evaluation of arbitrary SQL expressions on Arrow buffers using runtime code-generation in LLVM. While we use Gandiva at Dremio, we worked very hard to make sure Gandiva is an independent kernel that can be incorporated into any analytics system. As such, it has no runtime or compile time dependencies on Dremio or any other execution engine. It provides Java APIs that use the JNI bridge underneath to talk to C++ code for code generation and expression evaluation. Dremio’s execution engine leverages Gandiva Java APIs.

Applications submit an expression tree to the Gandiva compiler, which compiles for the local runtime environment. The request is then handed to the Gandiva execution kernel, which consumes and produces batches of Arrow buffers.

<img src="https://images.idgesg.net/images/article/2018/10/gandiva-architecture-100778804-large.jpg" alt="How applications interact with Gandiva"  />

### Gandiva Expression Library

Gandiva today supports 100s of expressions, and we hope to grow this to 1000s over the next few months. Gandiva supports both filter and project relational operators. In addition Gandiva supports:

- Arrow Math operators: +, -, /,*,%, ^
- Arrow Boolean operators: `==`,` !=`, `===`,` <`,` >`, `AND`, `OR`, `CASE … ELSE/IF … ELSE`, etc
- Arrow Dates and Times: String → Date, String → Timestamp, Date Extract, etc.

For example Gandiva can process the following expressions:

- (𝑐𝑜𝑙𝐴 % 5==0) 𝐴𝑁𝐷 (𝑡𝑜𝑑𝑎𝑦 − 𝑒𝑥𝑡𝑟𝑎𝑐t(𝑑𝑎𝑦𝑠 𝑓𝑟𝑜𝑚 𝑐𝑜𝑙𝐵)) < 10
- CASE WHEN a < 5 THEN 0 WHEN a > 10 THEN 2 ELSE 1

## Null Decomposition and Pipelining

Another optimization implemented in Gandiva is something we call null decomposition. Expressions submitted to Gandiva might involve records with NULL values. For some operations, we know that the result of many operations on expressions that include NULL values is always NULL. By separating whether a value is null (validity) from the actual value (data), we can much more efficiently process record batches.

<img src="https://www.dremio.com/img/blog/introducing-gandiva-initiative/image_1.png" alt="image alt text" style="zoom: 33%;" />

With this optimization, we can then determine nullness using bitmap intersections which can significantly reduce branching overhead by the CPU. Data values can then be batched and submitted for efficient SIMD processing on modern CPUs. It turns out this type of semantic is very common in SQL-based processing, and so this optimization is actually very effective, especially for SIMD and GPU-based processing.

### Vectorization and SIMD

Arrow memory buffers are already well organized for SIMD instructions. Depending on the hardware, Gandiva can process larger batches of values as a single operation. For example, considering the diagram below, you might have two sets of 2 bit values that need to be combined in an operation.

<img src="https://www.dremio.com/img/blog/introducing-gandiva-initiative/image_2.png" alt="Vectorization and SIMD in Apache Arrow" style="zoom: 33%;" />

For CPUs with AVX-128, Gandiva can process 8 pairs of these 2 byte values in a single vectorized operation. Additionally where available, AVX-512 processors can use Gandiva to process 4x as many values in a single operation. This optimization is performed automatically, and many others are possible. There’s [a talk](https://www.dremio.com/webinars/vectorized-query-processing-apache-arrow/) on vectorization in Arrow here as well as a blog post with some of the performance enhancements we observed from these types of changes [here](https://www.dremio.com/java-vector-enhancements-for-apache-arrow-0-8-0/).

### Asynchronous Thread Control

One of the other key things we’ve learned from working with Arrow in Dremio is the need to work to effectively share limited resources in a multi-tenant environment. This is in contrast to other environments, such as a single Python-based client for example, where the single process expects to consume all available resources. In a multi-tenant environment, you have multiple consumers of resources, and potentially different SLAs established for each. (In many cases, you’ll also be running on shared hardware, colocated with several other resource-hungry processes.)

In analytics, we’re typically bound by memory capacity and then entirely performance throttled by CPU throughput (once you have enough memory, the CPU is your bottleneck). Historically systems solve resource sharing of the CPU by creating more threads (and thus delegating the need to balance resources to the operating system thread scheduler). There are three challenges with this approach: (1) you have weak or no control over resource priorities; (2) as concurrency increases, context switching dominates CPU utilization; and (3) high priority cluster coordination operations like heartbeats may miss their scheduling requirement and cause cluster cohesion instability.

在分析中，我们通常受内存容量的约束，然后完全受CPU吞吐量的限制（如果您有足够的内存，CPU就是瓶颈）。 历史上，系统通过创建更多线程来解决CPU的资源共享（因此将平衡资源的需求委派给了操作系统线程调度程序）。 这种方法面临三个挑战：（1）您对资源优先级的控制很弱或没有控制权； （2）随着并发性的增加，上下文切换将主导CPU利用率； （3）高优先级的集群协调操作（如心跳）可能会错过其调度要求，并导致集群内聚不稳定。

We’ve designed Gandiva to allow for more flexibility in how resources are allocated to each request. For example, in Dremio we assign one thread per core and constantly **<u>==reassess==</u>** the state of workloads to rebalance work for optimum efficiency.

我们设计了 Gandiva，以便在分配资源给每个请求时提供更大的灵活性。 例如，在 Dremio 中，我们为每个内核分配一个线程，并不断<u>**==重新评估==**</u>工作负载的状态，以重新平衡工作实现最佳效率。

For example in the figure below, imagine you have three different users trying to use the system concurrently. Assuming 11 intervals of time for a single core, you might want to allocate resources to each operation differently.

例如，在下图中，假设您有三个不同的用户试图同时使用该系统。 假设单个内核有11个时间间隔，则可能要为每个操作分配不同的资源。

<img src="https://www.dremio.com/img/blog/introducing-gandiva-initiative/image_3.png" alt="Threading control in Gandiva" style="zoom:33%;" />

User 1 is allocated a single interval of time, whereas the third operation is allocated significantly more resources than the first two (eg, a “premium” user). Because Dremio processes jobs asynchronously, it can periodically <u>revisit</u> each thread (based on a <u>**quanta**</u> we define, which is designed to ensure the majority of time goes to forward progress instead of context switches) to rebalance available resources given the priorities of jobs running in the system.

用户1分配了一个时间间隔，第三项操作分配的资源比前两种明显多（例如，“高级”用户）。由于 Dremio 以异步方式处理作业，因此它可以定期<u>重新访问</u>每个线程（基于我们定义的<u>**==量子==**</u>，这是为了确保大部分时间用于处理工作，而不是上下文切换），根据系统中运行的作业的优先级重新平衡可用资源。

Gandiva works well with this model, allowing the system to operate asynchronously. It does this by allowing small amounts of work units to be concluded followed by suspension to the calling code. This pattern allows it to be used in both a traditional synchronous engine as well as more powerful asynchronous engines.

Gandiva 在此模型下运行良好，系统因此可异步运行。为此，Gandiva 停止少量工作单元，然后暂停调用代码。这种模式即可以在传统的同步引擎中使用，也可以在功能更强大的异步引擎中使用。

### Easy to Use in Multiple Languages

We built Gandiva to be compatible with many different environments. C++ and Java bindings are already available for users today. We also hope that we can work with the community to produce bindings for many other languages, such as Python, Go, JavaScript, Ruby, and more.

<img src="https://www.dremio.com/img/blog/introducing-gandiva-initiative/image_4.png" alt="Easy to use with multiple languages" style="zoom:33%;" />

To make new primitives available for use in Gandiva, the core C++ library exposes a number of capabilities that are language independent (including a consistent cross-language representation of expression trees). From there, all data is expected in the Arrow format, building on the ability of Arrow to be used across these different languages.

### Performance Observations

Generally speaking, Gandiva provides performances advantages across the board with very few compromises. First, it reduces the time to compile most queries to less than 10ms. In Dremio, Gandiva also improves the performance of creating and maintaining Data Reflections, which Dremio’s query planner can use to accelerate queries by orders of magnitude by creating a more intelligent query plan that involves less work.

To assess the benefits of Gandiva, we compared the performance of SQL queries executed through Dremio using standard Java code generation vs. compiling the queries through Gandiva. Note that the performance of Dremio using existing Java-based query compilation is on-par with state of the art SQL execution engines.

Five simple expressions were selected and the expression evaluation time alone was compared to process a JSON dataset of 500 million records. The tests were run on a Mac desktop (2.7GHz quad-core Intel Core i7 with 16GB ram).

In general, the more complex the SQL expression, the greater the advantage of using Gandiva.

**Sum**

```SQL
SELECT max(x+N2x+N3x) FROM json.d500
```

**Five output columns**

```SQL
SELECT
sum(x + N2x + N3x),
sum(x * N2x - N3x),
sum(3 * x + 2 * N2x + N3x),
count(x >= N2x - N3x),
count(x + N2x = N3x)
FROM json.d500
```

**Ten output columns**

```SQL
SELECT
sum(x + N2x + N3x),
sum(x * N2x - N3x),
sum(3 * x + 2 * N2x + N3x),
count(x >= N2x - N3x),
count(x + N2x = N3x),
sum(x - N2x + N3x),
sum(x * N2x + N3x),
sum(x + 2 * N2x + 3 * N3x),
count(x <= N2x - N3x)
count(x = N3x - N2x)
FROM json.d500
```

**CASE-10**

```SQL
SELECT count
(case
when x < 1000000 then x/1000000 + 0
when x < 2000000 then x/2000000 + 1
when x < 3000000 then x/3000000 + 2
when x < 4000000 then x/4000000 + 3
when x < 5000000 then x/5000000 + 4
when x < 6000000 then x/6000000 + 5
when x < 7000000 then x/7000000 + 6
when x < 8000000 then x/8000000 + 7
when x < 9000000 then x/9000000 + 8
when x < 10000000 then x/10000000 + 9
else 10
end)
FROM json.d500
```

**CASE-100**

Similar to case-10 but with 100 cases, and three output columns.

| Test                | Project time with Java JIT (seconds) | Project time with LLVM (seconds) | Java JIT time / LLVM time |
| ------------------- | ------------------------------------ | -------------------------------- | ------------------------- |
| SUM                 | 3.805                                | 0.558                            | 6.81x                     |
| Five output columns | 8.681                                | 1.689                            | 5.13x                     |
| Ten output columns  | 24.923                               | 3.476                            | 7.74x                     |
| CASE-10             | 4.308                                | 0.925                            | 4.66x                     |
| CASE-100            | 1361                                 | 15.187                           | 89.61x                    |

### Designed to Be Shared Across Environments

Gandiva was designed to be used in many contexts. We hope that communities using Python, Spark, Node and other environments can all find ways to embed and leverage Gandiva.

![image alt text](https://www.dremio.com/img/blog/introducing-gandiva-initiative/image_5.png)

Because Arrow is cross-platform, and because Gandiva consumes and produces Arrow, each process can efficiently interact with data from a common in-memory standard, and without serializing and deserializing the data.

This is a new approach for performing optimized processing of Arrow data structures. Gandiva builds on the Arrow’s adoption momentum to make processing on these structures even more efficient. This is work each application that implements Arrow would have otherwise implemented on their own, reinventing the wheel so to speak.

### Prior Art

Gandiva draws inspiration from many great projects. Systems like [Apache Impala](https://impala.apache.org/) and the more recent [Weld](https://github.com/weld-project/weld) work from Stanford have done a great job of setting ground rules around the use of LLVM in the context of data analytics pipelines. The work on Gandiva was inspired by many of these projects and looks to move things further forward by providing an in-memory columnar kernel with runtime compilation that is standardized in both data format and interface.

### Works Well With Apache Arrow Flight

Recently we proposed [Apache Arrow Flight](https://github.com/apache/arrow/pull/2102), a new way for applications to interact with Arrow. You can think of this as an alternative to ODBC/JDBC for in-memory analytics. Now that we have an established way for representing data in-memory, Flight defines a standardized way to exchange that data between systems.

If REST is the primary protocol for microservices, Flight is the primary protocol for data microservices. This allows organizations to stitch different data subsystems together in a non-monolithic way.

For example, in Dremio we have been consuming and producing data using Arrow from day one. As Dremio reads data from different sources, the data is read into Arrow buffers directly, regardless of the source. All processing is then performed on native Arrow buffers.

![image alt text](https://www.dremio.com/img/blog/introducing-gandiva-initiative/image_6.png)

For client applications interacting with Dremio, today we deserialize the data into a common structure. For example, for applications such as Tableau that query Dremio via ODBC, we process the query and stream the results as Arrow buffers all the way to the ODBC client before serializing to the cell-based protocol that ODBC expects. The same is true for all JDBC applications.

As soon as Arrow Flight is generally available, applications that implement Arrow can consume the Arrow buffers directly. This provides a substantial improvement in throughput and CPU consumption. For example, in our internal tests we observe from 10x-100x efficiency improvements with this approach compared to ODBC/JDBC interfaces.

### What’s Next

Our goal at Dremio is to build and drive development of modular data analysis components likes Apache Arrow, Apache Arrow Flight, and Gandiva. We hope to drive new collaborative innovation in both industry and academic communities. If we can share foundations, it allows people to leverage prior art to build truly innovative things that can also be incorporated into real world use cases. We hope Gandiva can help make technologies like columnar in-memory runtime optimized code generation accessible to more audiences.

# Gandiva, using LLVM and Arrow to JIT and evaluate Pandas expressions

https://blog.christianperone.com/2020/01/gandiva-using-llvm-and-arrow-to-jit-and-evaluate-pandas-expressions/

## Introduction

This is the post of 2020, so *happy new year* to you all !

I’m a huge fan of LLVM since 11 years ago when I started playing with it to [JIT data structures](https://blog.christianperone.com/2009/11/a-method-for-jiting-algorithms-and-data-structures-with-llvm/) such as AVLs, then later to [JIT restricted AST trees](https://blog.christianperone.com/2012/08/genetic-programming-and-a-llvm-jit-for-restricted-python-ast-expressions/) and to [JIT native code from TensorFlow graphs](https://blog.christianperone.com/2016/08/jit-native-code-generation-for-tensorflow-computation-graphs-using-python-and-llvm/). Since then, LLVM evolved into one of the most important compiler framework ecosystem and is used nowadays by a lot of important open-source projects.

One cool project that I recently became aware of is [Gandiva](https://github.com/dremio/gandiva). Gandiva was developed by [Dremio](https://www.dremio.com/) and then later [donated to Apache Arrow](https://arrow.apache.org/blog/2018/12/05/gandiva-donation/) (**kudos to Dremio team for that**). The main idea of Gandiva is that it provides a compiler to generate LLVM IR that can operate on batches of [Apache Arrow](https://arrow.apache.org/). Gandiva was written in C++ and comes with a lot of different functions implemented to build an expression tree that can be JIT’ed using LLVM. One nice feature of this design is that it can use LLVM to automatically optimize complex expressions, add native target platform vectorization such as AVX while operating on Arrow batches and execute native code to evaluate the expressions.

The image below gives an overview of Gandiva:

<img src="https://images.idgesg.net/images/article/2018/10/gandiva-architecture-100778804-large.jpg" alt="How applications interact with Gandiva"  />

In this post I’ll build a very simple expression parser supporting a limited set of operations that I will use to filter a Pandas DataFrame.

## Building simple expression with Gandiva

In this section I’ll show how to create a simple expression manually using tree builder from Gandiva.

### Using Gandiva Python bindings to JIT and expression

Before building our parser and expression builder for expressions, let’s manually build a simple expression with Gandiva. First, we will create a simple Pandas DataFrame with numbers from 0.0 to 9.0:

```python
import pandas as pd
import pyarrow as pa
import pyarrow.gandiva as gandiva

# Create a simple Pandas DataFrame
df = pd.DataFrame({"x": [1.0 * i for i in range(10)]})
table = pa.Table.from_pandas(df)
schema = pa.Schema.from_pandas(df)
```

We converted the DataFrame to an [Arrow Table](https://arrow.apache.org/docs/python/generated/pyarrow.Table.html), it is important to note that in this case it was a zero-copy operation, Arrow isn’t copying data from Pandas and duplicating the DataFrame. Later we get the `schema` from the table, that contains column types and other metadata.

After that, we want to use Gandiva to build the following expression to filter the data: 

```python
(x > 2.0) and (x < 6.0)
```

This expression will be built using nodes from Gandiva:

```python
builder = gandiva.TreeExprBuilder()

# Reference the column "x"
node_x = builder.make_field(table.schema.field("x"))

# Make two literals: 2.0 and 6.0
two = builder.make_literal(2.0, pa.float64())
six = builder.make_literal(6.0, pa.float64())

# Create a function for "x > 2.0"
gt_five_node = builder.make_function("greater_than",
                                     [node_x, two], 
                                     pa.bool_())

# Create a function for "x < 6.0"
lt_ten_node = builder.make_function("less_than",
                                    [node_x, six], 
                                    pa.bool_())
# Create an "and" node, for "(x > 2.0) and (x < 6.0)"
and_node = builder.make_and([gt_five_node, lt_ten_node])

# Make the expression a condition and create a filter
condition = builder.make_condition(and_node)
filter_ = gandiva.make_filter(table.schema, condition)
```

This code now looks a little more complex but it is easy to understand. We are basically creating the nodes of a tree that will represent the expression we showed earlier. Here is a graphical representation of what it looks like:

[![img](https://blog.christianperone.com/wp-content/uploads/2020/01/tree_expr.png)](https://blog.christianperone.com/wp-content/uploads/2020/01/tree_expr.png)

### Inspecting the generated LLVM IR

Unfortunately, haven’t found a way to dump the LLVM IR that was generated using the Arrow’s Python bindings, however, we can just use the C++ API to build the same tree and then look at the generated LLVM IR:

```C++
auto field_x = field("x", float32());
auto schema = arrow::schema({field_x});

auto node_x = TreeExprBuilder::MakeField(field_x);

auto two = TreeExprBuilder::MakeLiteral((float_t)2.0);
auto six = TreeExprBuilder::MakeLiteral((float_t)6.0);

auto gt_five_node = TreeExprBuilder::MakeFunction("greater_than",
                                                  {node_x, two}, arrow::boolean());

auto lt_ten_node = TreeExprBuilder::MakeFunction("less_than",
                                                 {node_x, six}, arrow::boolean());

auto and_node = TreeExprBuilder::MakeAnd({gt_five_node, lt_ten_node});
auto condition = TreeExprBuilder::MakeCondition(and_node);

std::shared_ptr<Filter> filter;
auto status = Filter::Make(schema, condition, TestConfiguration(), &filter);
```

The code above is the same as the Python code, but using the C++ Gandiva API. Now that we built the tree in C++, we can get the LLVM Module and dump the IR code for it. The generated IR is full of boilerplate code and the JIT’ed functions from the Gandiva registry, however the important parts are show below:

```ASM
; Function Attrs: alwaysinline norecurse nounwind readnone ssp uwtable
define internal zeroext i1 @less_than_float32_float32(float, float) local_unnamed_addr #0 {
  %3 = fcmp olt float %0, %1
  ret i1 %3
}

; Function Attrs: alwaysinline norecurse nounwind readnone ssp uwtable
define internal zeroext i1 @greater_than_float32_float32(float, float) local_unnamed_addr #0 {
  %3 = fcmp ogt float %0, %1
  ret i1 %3
}

(...)
%x = load float, float* %11
%greater_than_float32_float32 = call i1 @greater_than_float32_float32(float %x, float 2.000000e+00)
(...)
%x11 = load float, float* %15
%less_than_float32_float32 = call i1 @less_than_float32_float32(float %x11, float 6.000000e+00)
```

As you can see, on the IR we can see the call to the functions `less_than_float32_float_32` and `greater_than_float32_float32`that are the (in this case very simple) Gandiva functions to do float comparisons. Note the specialization of the function by looking at the function name prefix.

What is quite interesting is that LLVM will apply all optimizations in this code and it will generate efficient native code for the target platform while Godiva and LLVM will take care of making sure that memory alignment will be correct for extensions such as AVX to be used for vectorization.

This IR code I showed isn’t actually the one that is executed, but the optimized one. And in the optimized one we can see that LLVM inlined the functions, as shown in a part of the optimized code below:

```ASM
%x.us = load float, float* %10, align 4
%11 = fcmp ogt float %x.us, 2.000000e+00
%12 = fcmp olt float %x.us, 6.000000e+00
%not.or.cond = and i1 %12, %11
```

You can see that the expression is now much simpler after optimization as LLVM applied its powerful optimizations and inlined a lot of Gandiva funcions.

## Building a Pandas filter expression JIT with Gandiva

Now we want to be able to implement something similar as the Pandas’ `DataFrame.query()`function using Gandiva. The first problem we will face is that we need to parse a string such as `(x > 2.0) and (x < 6.0)`, later we will have to build the Gandiva expression tree using the tree builder from Gandiva and then evaluate that expression on arrow data.

Now, instead of implementing a full parsing of the expression string, I’ll use the Python AST module to parse valid Python code and build an Abstract Syntax Tree (AST) of that expression, that I’ll be later using to emit the Gandiva/LLVM nodes.

The heavy work of parsing the string will be delegated to Python AST module and our work will be mostly walking on this tree and emitting the Gandiva nodes based on that syntax tree. The code for visiting the nodes of this Python AST tree and emitting Gandiva nodes is shown below:

```python
class LLVMGandivaVisitor(ast.NodeVisitor):
    def __init__(self, df_table):
        self.table = df_table
        self.builder = gandiva.TreeExprBuilder()
        self.columns = {f.name: self.builder.make_field(f)
                        for f in self.table.schema}
        self.compare_ops = {
            "Gt": "greater_than",
            "Lt": "less_than",
        }
        self.bin_ops = {
            "BitAnd": self.builder.make_and,
            "BitOr": self.builder.make_or,
        }
    
    def visit_Module(self, node):
        return self.visit(node.body[0])
    
    def visit_BinOp(self, node):
        left = self.visit(node.left)
        right = self.visit(node.right)
        op_name = node.op.__class__.__name__
        gandiva_bin_op = self.bin_ops[op_name]
        return gandiva_bin_op([left, right])

    def visit_Compare(self, node):
        op = node.ops[0]
        op_name = op.__class__.__name__
        gandiva_comp_op = self.compare_ops[op_name]
        comparators = self.visit(node.comparators[0])
        left = self.visit(node.left)
        return self.builder.make_function(gandiva_comp_op,
                                          [left, comparators], pa.bool_())
        
    def visit_Num(self, node):
        return self.builder.make_literal(node.n, pa.float64())

    def visit_Expr(self, node):
        return self.visit(node.value)
    
    def visit_Name(self, node):
        return self.columns[node.id]
    
    def generic_visit(self, node):
        return node
    
    def evaluate_filter(self, llvm_mod):
        condition = self.builder.make_condition(llvm_mod)
        filter_ = gandiva.make_filter(self.table.schema, condition)
        result = filter_.evaluate(self.table.to_batches()[0],
                                  pa.default_memory_pool())    
        arr = result.to_array()
        pd_result = arr.to_numpy()
        return pd_result

    @staticmethod
    def gandiva_query(df, query):
        df_table = pa.Table.from_pandas(df)
        llvm_gandiva_visitor = LLVMGandivaVisitor(df_table)
        mod_f = ast.parse(query)
        llvm_mod = llvm_gandiva_visitor.visit(mod_f)
        results = llvm_gandiva_visitor.evaluate_filter(llvm_mod)
        return results
```

As you can see, the code is pretty straightforward as I’m not supporting every possible Python expressions but a minor subset of it. What we do in this class is basically a conversion of the Python AST nodes such as Comparators and BinOps (binary operations) to the Gandiva nodes. I’m also changing the semantics of the `&` and the `|` operators to represent AND and OR respectively, such as in Pandas `query()`function.

### Register as a Pandas extension

The next step is to create a simple Pandas extension using the `gandiva_query()` method that we created:

```python
@pd.api.extensions.register_dataframe_accessor("gandiva")
class GandivaAcessor:
    def __init__(self, pandas_obj):
        self.pandas_obj = pandas_obj

    def query(self, query):
         return LLVMGandivaVisitor.gandiva_query(self.pandas_obj, query)
```

And that is it, now we can use this extension to do things such as:

```python
df = pd.DataFrame({"a": [1.0 * i for i in range(nsize)]})
results = df.gandiva.query("a > 10.0")
```

As we have registered a Pandas extension called `gandiva` that is now a first-class citizen of the Pandas DataFrames.

Let’s create now a 5 million floats DataFrame and use the new `query()` method to filter it:

```python 
df = pd.DataFrame({"a": [1.0 * i for i in range(50000000)]})
df.gandiva.query("a < 4.0")

# This will output:
#     array([0, 1, 2, 3], dtype=uint32)
```

Note that the returned values are the indexes satisfying the condition we implemented, so it is different than the Pandas `query()`that returns the data already filtered.

I did some benchmarks and found that Gandiva is usually always faster than Pandas, however I’ll leave proper benchmarks for a next post on Gandiva as this post was to show how you can use it to JIT expressions.

That’s it ! I hope you liked the post as I enjoyed exploring Gandiva. It seems that we will probably have more and more tools coming up with Gandiva acceleration, specially for SQL parsing/projection/JITing. Gandiva is much more than what I just showed, but you can get started now to understand more of its architecture and how to build the expression trees.



https://loonytek.com/2018/04/26/vectorized-processing-in-analytical-query-engines/