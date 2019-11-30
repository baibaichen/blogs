# Apache Calcite: A Foundational Framework for Optimized Query Processing Over Heterogeneous Data Sources

## 4 查询代数（QUERY ALGEBRA）

> **Operators**. Relational algebra [11] lies at the core of Calcite. In addition to the operators that express the most common data manipulation operations, such as *filter*, *project*, *join* etc., Calcite includes additional operators that meet different purposes, e.g., being able to concisely represent complex operations, or recognize optimization opportunities more efficiently.
>
> For instance, it has become common for **OLAP**, decision making, and streaming applications to use window definitions to express complex analytic functions such as moving average of a quantity over a time period or number or rows. Thus, Calcite introduces a *window* operator that encapsulates the window definition, i.e., upper and lower bound, partitioning etc., and the aggregate functions to execute on each window.
>
> **Traits**. Calcite <u>**does not use different entities**</u> to represent logical and physical operators. Instead, it describes the physical properties associated with an operator using traits. These traits help the optimizer evaluate the cost of different alternative plans. <u>==Changing a trait value does not change the==</u> logical expression being **evaluated**, i.e., the rows produced by the given operator will still be the same.
>
> During optimization, Calcite tries to <u>==enforce==</u> certain traits on relational expressions, e.g., the sort order of certain columns. Relational operators can implement a converter interface that indicates how to convert traits of an expression from one value to another.
>
> Calcite includes common traits that describe the physical properties of the data produced by a relational expression, such as *ordering*, *grouping*, and *partitioning*. Similar to the SCOPE optimizer [57], the Calcite optimizer can reason about these properties and exploit them to find plans that avoid unnecessary operations. For example, if the input to the sort operator is already correctly ordered—possibly because this is the same order used for rows in the backend system—then the sort operation can be removed.
>
> In addition to these properties, **<u>one of the main features of Calcite is the *calling convention* trait</u>**. Essentially, the trait represents the data processing system where the expression will be executed. Including the calling convention as a trait allows Calcite to meet its goal of optimizing transparently queries whose execution might span over different engines i.e., the convention will be treated as any other physical property.
>
> > TODO: 图2
>
> For example, consider joining a *Products* table held in MySQL to an *Orders* table held in Splunk (see Figure 2). Initially, the scan of *Orders* takes place in the *splunk* convention and the scan of *Products* is in the *jdbc-mysql* convention. The tables have to be scanned inside their respective engines. The **<u>join</u>** is in the *logical* convention, meaning that no implementation has been chosen. Moreover, the SQL query in Figure 2 contains a filter (`where` clause) which is pushed into *splunk* by an adapter-specific rule (see Section 5). One possible implementation is to use Apache Spark as an external engine: the join is converted to *spark* convention, and its inputs are **<u>converters</u>** from *jdbc-mysql* and *splunk* to *spark* convention. But there is a more efficient implementation: exploiting the fact that Splunk can perform lookups into MySQL via ODBC, a planner rule pushes the join through the *splunk-to-spark* converter, and the join is now in *splunk* convention, running inside the Splunk engine.
>

**运算符**。关系代数[11]是Calcite的核心。除了表示最常见数据操作（如 *filter*、*project*、*join* 等）的运算符外，还包括满足<u>不同目的</u>的其他运算符，比如，能够简洁地表示复杂操作，或更有效地识别优化机会。

例如，**OLAP**、决策和流应用程序使用<u>窗口</u>定义来表达复杂的分析功能已变得很普遍，例如某个时间段或数量或一组行上的移动平均值。因此，Calcite引入了一个 *window* 操作符，封装窗口定义，即上界、下界、分区等，以及要在每个窗口上执行的聚合函数。

**特征**。 Calcite <u>**没有使用不同的实体**</u>来表示逻辑和物理运算符。 相反，它使用<u>特征</u>描述与运算符关联的物理属性。这些<u>特征</u>有助于**优化器**评估不同替代<u>执行计划</u>的成本。<u>==更改特征值不会更改==要</u>**求值**的<u>逻辑表达式</u>，即，给定运算符产生的行仍将相同。

优化过程中，Calcite尝试对关系表达式<u>==强制执行==</u>某些特征，例如，某些列的排序顺序。关系运算符可以实现一个<u>**转换器接口**</u>，用于将表达式的特征<u>从一个值转换为另一个值</u>。

Calcite 包含一些常见特征，用于描述由关系表达式产生的数据的物理属性，如**排序**、**分组**和**分区**。与SCOPE优化器[57]类似，Calcite 优化器<u>可以对这些属性进行推理</u>，并利用它们找到避免不必要操作的计划。例如，如果**排序运算符**的输入已被正确排序，可能是因为与后端系统中的**行**使用了相同的顺序，则可以删除排序操作。

除了这些性质，**<u>Calcite 一个主要特征就是调用约定特征（*calling convention*）</u>**。特征本质上表示**==执行表达式的数据处理系统==**。将**<u>调用约定</u>**作为一种特性包含在内，使得 Calcite 可以透明地优化其查询，这些查询可能执行于不同的引擎中，即，该约定将被视为某种物理属性。

例如，假设用MySQL中的*Products*表**关联**Splunk中的*Orders*表，参见图2。一开始，在*splunk*约定中扫描*Orders*，在*jdbc-mysql*约定中扫描*Products*。必须在各自的引擎内扫描这些表。**<u>关联（join）</u>**还是 *logical* 约定，意味着没有选择任何实现。此外，图2中的SQL查询包含一个过滤（`where`子句），由适配器定义的优化规则下推*splunk*中（参见第5节）。一种可能的实现是使用Apache Spark：将关联（join）转换为 *spark* 约定，并且其输入是从 *jdbc-mysql* 和  *splunk* 到 *spark* 约定的<u>**==转换器==**</u>。但有一个更高效的实现：利用Splunk可以通过ODBC对MySQL执行查找的事实，一个优化规则通过 *Splunk to spark* 转换器下推关联（join）。现在，关联（join）位于 *Splunk* 约定中，运行于 Splunk 引擎。
>

## 5 适配器（ADAPTERS）

> An adapter is an architectural pattern that defines how **Calcite** incorporates diverse data sources for general access. Figure 3 depicts its components. Essentially, an adapter consists of a **model**, a **schema**, and a **schema factory**. The *model* is a specification of the physical properties of the data source being accessed. A *schema* is the definition of the data (format and layouts) found in the model. The data itself is physically accessed via tables. **Calcite** interfaces with the **<u>tables</u>** defined in the adapter to read the data as the query is being executed. The adapter may define a set of rules that are added to the **planner**. For instance, it typically includes rules to convert **various types of logical relational expressions** to <u>the corresponding relational expressions of the adapter’s convention</u>. The *schema factory* component acquires the metadata information from the model and generates a schema.
>
> > TODO: 图3
>
> As discussed in Section 4, Calcite uses a <u>physical trait</u> known as the <u>calling convention</u> to identify relational operators which correspond to a specific database backend. These physical operators **implement the access paths** for <u>the underlying tables in each adapter</u>. When a query is parsed and converted to a relational algebra expression, an operator is created for each table representing a scan of the data on that table. **It is the minimal interface that an adapter must implement**. If an adapter implements the <u>table scan operator</u>, the Calcite optimizer is then able to use client-side operators such as sorting, filtering, and joins to execute arbitrary SQL queries against these tables.
>
> This table scan operator contains the necessary information the adapter requires to issue the scan to the adapter’s backend database. To extend the functionality provided by adapters, Calcite defines an `enumerable` calling convention. Relational operators with the `enumerable` calling convention simply operate over tuples via an **iterator** interface. This calling convention allows **Calcite** to implement operators which may not be available in each adapter’s backend. For example, the `EnumerableJoin` operator implements joins by collecting rows from its child nodes and joining on <u>the desired attributes</u>.
>
> For queries which only touch a small subset of the data in a table, it is inefficient for Calcite to enumerate all tuples. Fortunately, the same rule-based optimizer can be used to implement adapter-specific rules for optimization. For example, suppose a query involves filtering and sorting on a table. <u>An adapter which can perform filtering on the backend</u> can implement <u>a rule which matches a `LogicalFilter`</u> and converts it to the adapter’s calling convention. This rule converts the LogicalFilter into another Filter instance. This new Filter node has a lower ==associated cost== that allows Calcite to optimize queries across adapters.
>
>
> The use of adapters is a powerful abstraction that enables not  only optimization of queries for a specific backend, but also across multiple backends. Calcite is able to answer queries involving tables across multiple backends by pushing down all possible logic to each backend and then performing joins and aggregations on the resulting data. Implementing an adapter can be as simple as providing a table scan operator or it can involve the design of many advanced optimizations. Any expression represented in the relational algebra can be pushed down to adapters with <u>optimizer rules</u>.
>

适配器是一种架构模式，定义了**Calcite**如何合并各种数据源以进行常规访问。 图3描绘了它的组件。本质上，适配器由**模型**，**模式**和**模式工厂**组成。<u>模型</u>是被访问数据源物理属性的规范。<u>模式</u>是在模型中找到的数据（格式和布局）的定义。数据本身可以通过表进行物理访问。 **Calcite**与适配器中定义的<u>**表接口**</u>进行交互，以在执行查询时读取数据。适配器可以定义添加到`planner`的一组规则。例如，它通常包含将<u>各种类型的逻辑关系表达式</u>转换为<u>适配器约定的对应关系表达式</u>的规则。 <u>模式工厂</u>组件从模型获取元数据信息并生成模式。

如第4节所述，Calcite使用一种称为<u>调用约定</u>的<u>物理特征</u>来识别与<u>特定数据库后端相对应</u>的**关系运算符**。这些物理运算符<u>为每个适配器中的基础表</u>**实现访问路径**。当解析完查询并转换为关系代数表达式后，将为每个表创建一个运算符，表示对该表上数据的扫描。**它是适配器必须实现的最小接口**。如果适配器实现了<u>表扫描运算符</u>，则Calcite优化器将能够使用客户端运算符（例如排序，过滤和联接）对这些表执行任意SQL查询。

这个**表扫描运算符**包含适配器向其后端数据库发出扫描所需的必要信息。为了扩展适配器提供的功能，Calcite定义了`enumerable`调用规范。具有`enumerable`调用规范的关系运算符只需通过**迭代器**接口对元组进行操作。这个调用规范使**Calcite**可实现适配器后端数据库可能并不存在的运算符。例如`EnumerableJoin`运算符，它实现联接的方式是，从其子节点收集记录，并在<u>所需属性</u>上进行联接。

对于仅涉及表中一小部分数据的查询，**Calcite**没有必要枚举所有元组。幸运的是，可以使用相同的基于规则的优化器来实现特定于适配器的规则以进行优化。例如，假设查询涉及对表进行过滤和排序。<u>可在后端执行过滤的适配器</u>可以实现<u>匹配`LogicalFilter`的规则</u>，将其转换为适配器的调用规范。 此规则将`LogicalFilter`转换为另一种`Filter`的实例。 这个新的`Filter`节点具有较低的==关联成本==，使得Calcite可以跨适配器优化查询。

适配器是一个强大的抽象，使用它不仅可以针对特定的后端优化查询，还可以在多个后端之间进行优化。通过将所有可能的逻辑下推到每个后端，然后关联和聚合结果数据，**Calcite**能够执行涉及多个后端表的查询。实现适配器可以像提供表扫描操作符一样简单，也可以涉及许多高级优化的设计。用关系代数表示的任何表达式都可以利用<u>优化器规则</u>下推到适配器。

## 6 QUERY PROCESSING AND OPTIMIZATION

The query optimizer is the main component in the framework. Calcite optimizes queries by repeatedly applying planner rules to a relational expression. A cost model guides the process, and the planner engine tries to generate an alternative expression that has the same semantics as the original but a lower cost.

Every component in the optimizer is extensible. Users can add relational operators, rules, cost models, and statistics.

**Planner rules**. Calcite includes a set of planner rules to transform expression trees. In particular, a rule matches a given pattern in the tree and executes a transformation that preserves semantics of that expression. Calcite includes several hundred optimization rules. However, it is rather common for data processing systems relying on Calcite for optimization to include their own rules to allow specific rewritings.

For example, Calcite provides an adapter for Apache Cassandra [29], a wide column store which partitions data by a subset of columns in a table and then within each partition, sorts rows based on another subset of columns. As discussed in Section 5, it is beneficial for adapters to push down as much query processing as possible to each backend for efficiency. A rule to push a Sort into Cassandra must check two conditions:

1. the table has been previously filtered to a single partition (since rows are only sorted within a partition) and
2. the sorting of partitions in Cassandra has some common prefix with the required sort.

This requires that a LogicalFilter has been rewritten to a CassandraFilter to ensure the partition filter is pushed down to the database. The effect of the rule is simple (convert a LogicalSort into a CassandraSort) but the flexibility in rule matching enables backends to push down operators even in complex scenarios.

For an example of a rule with more complex effects, consider the following query:


``` sql
SELECT products .name , COUNT (*) 
FROM sales JOIN products USING ( productId ) 
WHERE sales . discount IS NOT NULL 
GROUP BY products . name
ORDER BY COUNT (*) DESC ;
```

The query corresponds to the relational algebra expression presented in Figure 4a. Because the WHERE clause only applies to the sales table, we can move the filter before the join as in Figure 4b. This optimization can significantly reduce query execution time since we do not need to perform the join for rows which do match the predicate. Furthermore, if the sales and products tables were contained in separate backends, moving the filter before the join also potentially enables an adapter to push the filter into the backend. Calcite implements this optimization via FilterIntoJoinRule which matches a filter node with a join node as a parent and checks if the filter can be performed by the join. This optimization illustrates the flexibility of the Calcite approach to optimization.

**Metadata providers**. Metadata is an important part of Calcite’s optimizer, and it serves two main purposes: (i) guiding the planner towards the goal of reducing the cost of the overall query plan, and (ii) providing information to the rules while they are being applied. 

<u>Metadata providers are responsible for supplying that information to the optimizer</u>. In particular, the default metadata providers implementation in Calcite contains functions that return the overall cost of executing a subexpression in the operator tree, the number of rows and the data size of the results of that expression, and the maximum degree of parallelism with which it can be executed. In turn, it can also provide information about the plan structure, e.g., filter conditions that are present below a certain tree node.

<u>Calcite provides interfaces that allow data processing systems to plug their metadata information into the framework.</u> These systems may choose to write providers that override the existing functions, or provide their own new metadata functions that might be used during the optimization phase. However, for many of them, it is sufficient to provide statistics about their input data, e.g., number of rows and size of a table, whether values for a given column are unique etc., and Calcite will do the rest of the work by using its default implementation.

As the metadata providers are pluggable, they are compiled and instantiated at runtime using Janino [27], a Java lightweight compiler. Their implementation includes a cache for metadata results, which yields significant performance improvements, e.g., when we need to compute multiple types of metadata such as *cardinality*, *average row size*, and *selectivity* for a given join, and all these computations rely on the cardinality of their inputs.

**Planner engines**. The main goal of a planner engine is to trigger the rules provided to the engine until it reaches a given objective. At the moment, Calcite provides two different engines. New engines are pluggable in the framework.

The first one, a cost-based planner engine, triggers the input rules with the goal of reducing the overall expression cost. The engine uses a <u>dynamic programming algorithm</u>, similar to Volcano [20], to create and track different alternative plans created by firing the rules given to the engine. Initially, each expression is registered with the planner, together with a digest based on the expression attributes and its inputs. When a rule is fired on an expression e1 and the rule produces a new expression e2, the planner will add e2 to the set of equivalence expressions Sa that e1 belongs to. In addition, the planner generates a digest for the new expression, which is compared with those previously registered in the planner. If a similar digest associated with an expression e3 that belongs to a set Sb is found, the planner has found a duplicate and hence will merge Sa and Sb into a new set of equivalences. The process continues until the planner reaches a configurable fix point. In particular, it can (i) exhaustively explore the search space until all rules have been applied on all expressions, or (ii) use a heuristicbased approach to stop the search when the plan cost has not improved by more than a given threshold δ in the last planner iterations. The cost function that allows the optimizer to decide which plan to choose is supplied through metadata providers. The default cost function implementation combines estimations for 1CPU, IO, and memory resources used by a given expression.

The second engine is an exhaustive planner, which triggers rules exhaustively until it generates an expression that is no longer modified by any rules. This planner is useful to quickly execute rules without taking into account the cost of each expression.

Users may choose to use one of the existing planner engines depending on their concrete needs, and switching from one to another, when their system requirements change, is straightforward. Alternatively, users may choose to generate multi-stage optimization logic, in which different sets of rules are applied in consecutive phases of the optimization process. Importantly, the existence of two planners allows Calcite users to reduce the overall optimization time by guiding the search for different query plans.

**Materialized views**. One of the most powerful techniques to accelerate query processing in data warehouses is the precomputation of relevant summaries or materialized views. Multiple Calcite adapters and projects relying on Calcite have their own notion of materialized views. For instance, Cassandra allows the user to define materialized views based on existing tables which are automatically maintained by the system.

These engines expose their materialized views to Calcite. The optimizer then has the opportunity to rewrite incoming queries to use these views instead of the original tables. In particular, Calcite provides an implementation of two different materialized viewbased rewriting algorithms.

The first approach is based on view substitution [10, 18]. The aim is to substitute part of the relational algebra tree with an equivalent expression which makes use of a materialized view, and the algorithm proceeds as follows: (i) the scan operator over the materialized view and the materialized view definition plan are registered with the planner, and (ii) transformation rules that try to unify expressions in the plan are triggered. Views do not need to exactly match expressions in the query being replaced, as the rewriting algorithm in Calcite can produce partial rewritings that include additional operators to compute the desired expression, e.g., filters with residual predicate conditions.

The second approach is based on lattices [22]. Once the data sources are declared to form a lattice, Calcite represents each of the materializations as a tile which in turn can be used by the optimizer to answer incoming queries. On the one hand, the rewriting algorithm is especially efficient in matching expressions over data sources organized in a star schema, which are common in OLAP applications. On the other hand, it is more restrictive than view substitution, as it imposes restrictions on the underlying schema.



----

# 适配器模式

### [Defining a custom schema](https://calcite.apache.org/docs/adapter.html#defining-a-custom-schema)

To define a custom schema, you need to implement [`interface SchemaFactory`](https://calcite.apache.org/apidocs/org/apache/calcite/schema/SchemaFactory.html).

**During query preparation**, Calcite will call this interface to find out what tables and sub-schemas your schema contains. When a table in your schema is referenced in a query, Calcite will ask your schema to create an instance of [`interface Table`](https://calcite.apache.org/apidocs/org/apache/calcite/schema/Table.html).

That table will be wrapped in a [`TableScan`](https://calcite.apache.org/apidocs/org/apache/calcite/rel/core/TableScan.html) and will undergo the query optimization process.

### [Reflective schema](https://calcite.apache.org/docs/adapter.html#reflective-schema)

A reflective schema ([`class ReflectiveSchema`](https://calcite.apache.org/apidocs/org/apache/calcite/adapter/java/ReflectiveSchema.html)) is a way of wrapping a Java object so that it appears as a schema. Its collection-valued fields will appear as tables.

It is not a schema factory but an actual schema; you have to create the object and wrap it in the schema by calling APIs.

See [`class ReflectiveSchemaTest`](https://github.com/apache/calcite/blob/master/core/src/test/java/org/apache/calcite/test/ReflectiveSchemaTest.java).

### [Defining a custom table](https://calcite.apache.org/docs/adapter.html#defining-a-custom-table)

To define a custom table, you need to implement [`interface TableFactory`](https://calcite.apache.org/apidocs/org/apache/calcite/schema/TableFactory.html). Whereas a schema factory a set of named tables, a table factory produces a single table when bound to a schema with a particular name (and optionally a set of extra operands).

### [Modifying data](https://calcite.apache.org/docs/adapter.html#modifying-data)

If your table is to support DML operations (INSERT, UPDATE, DELETE, MERGE), your implementation of `interface Table` must implement [`interface ModifiableTable`](https://calcite.apache.org/apidocs/org/apache/calcite/schema/ModifiableTable.html).

### [Streaming](https://calcite.apache.org/docs/adapter.html#streaming)

If your table is to support streaming queries, your implementation of `interface Table` must implement [`interface StreamableTable`](https://calcite.apache.org/apidocs/org/apache/calcite/schema/StreamableTable.html).

See [`class StreamTest`](https://github.com/apache/calcite/blob/master/core/src/test/java/org/apache/calcite/test/StreamTest.java) for examples.

### [Pushing operations down to your table](https://calcite.apache.org/docs/adapter.html#pushing-operations-down-to-your-table)

If you wish to push processing down to your custom table’s source system, consider implementing either [`interface FilterableTable`](https://calcite.apache.org/apidocs/org/apache/calcite/schema/FilterableTable.html) or [`interface ProjectableFilterableTable`](https://calcite.apache.org/apidocs/org/apache/calcite/schema/ProjectableFilterableTable.html).

If you want more control, you should write a [planner rule](#planner-rule). This will allow you to push down expressions, to make a cost-based decision about whether to push down processing, and push down more complex operations such as join, aggregation, and sort.

### [Type system](https://calcite.apache.org/docs/adapter.html#type-system)

You can customize some aspects of the type system by implementing [`interface RelDataTypeSystem`](https://calcite.apache.org/apidocs/org/apache/calcite/rel/type/RelDataTypeSystem.html).

### [关系运算符（Relational operators）](https://calcite.apache.org/docs/adapter.html#relational-operators)

> All relational operators implement [`interface RelNode`](https://calcite.apache.org/apidocs/org/apache/calcite/rel/RelNode.html) and most extend [`class AbstractRelNode`](https://calcite.apache.org/apidocs/org/apache/calcite/rel/AbstractRelNode.html). The core operators (used by [`SqlToRelConverter`](https://calcite.apache.org/apidocs/org/apache/calcite/sql2rel/SqlToRelConverter.html) and covering conventional relational algebra) are [`TableScan`](https://calcite.apache.org/apidocs/org/apache/calcite/rel/core/TableScan.html), [`TableModify`](https://calcite.apache.org/apidocs/org/apache/calcite/rel/core/TableModify.html), [`Values`](https://calcite.apache.org/apidocs/org/apache/calcite/rel/core/Values.html), [`Project`](https://calcite.apache.org/apidocs/org/apache/calcite/rel/core/Project.html), [`Filter`](https://calcite.apache.org/apidocs/org/apache/calcite/rel/core/Filter.html), [`Aggregate`](https://calcite.apache.org/apidocs/org/apache/calcite/rel/core/Aggregate.html), [`Join`](https://calcite.apache.org/apidocs/org/apache/calcite/rel/core/Join.html), [`Sort`](https://calcite.apache.org/apidocs/org/apache/calcite/rel/core/Sort.html), [`Union`](https://calcite.apache.org/apidocs/org/apache/calcite/rel/core/Union.html), [`Intersect`](https://calcite.apache.org/apidocs/org/apache/calcite/rel/core/Intersect.html), [`Minus`](https://calcite.apache.org/apidocs/org/apache/calcite/rel/core/Minus.html), [`Window`](https://calcite.apache.org/apidocs/org/apache/calcite/rel/core/Window.html) and [`Match`](https://calcite.apache.org/apidocs/org/apache/calcite/rel/core/Match.html).
>
> Each of these has a “pure” logical sub-class, [`LogicalProject`](https://calcite.apache.org/apidocs/org/apache/calcite/rel/logical/LogicalProject.html) and so forth. Any given adapter will have counterparts for the operations that its engine can implement efficiently; for example, the Cassandra adapter has [`CassandraProject`](https://calcite.apache.org/apidocs/org/apache/calcite/rel/cassandra/CassandraProject.html) but there is no `CassandraJoin`.
>
> You can define your own sub-class of `RelNode` to add a new operator, or an implementation of an existing operator in a particular engine.
>
> To make an operator useful and powerful, you will need [planner rules](https://calcite.apache.org/docs/adapter.html#planner-rule) to combine it with existing operators. (And also provide metadata, see [below](https://calcite.apache.org/docs/adapter.html#statistics-and-cost)). **This being algebra, the effects are combinatorial: you write a few rules, but they combine to handle an exponential number of query patterns**.
>
> If possible, make your operator a sub-class of an existing operator; then you may be able to re-use or adapt its rules. Even better, if your operator is a logical operation that you can rewrite (again, via a planner rule) in terms of existing operators, you should do that. You will be able to re-use the rules, metadata and implementations of those operators with no extra work.
>

所有关系运算符都实现[`接口RelNode`](https://calcite.apache.org/apidocs/org/apache/calcite/rel/RelNode.html)，并且大多数扩展了[`class AbstractRelNode`](https：// calcite .apache.org / apidocs / org / apache / calcite / rel / AbstractRelNode.html)。由[`SqlToRelConverter`](https://calcite.apache.org/apidocs/org/apache/calcite/sql2rel/SqlToRelConverter.html)使用，由于表达传统关系代数的核心运算符是 [`TableScan`](https://calcite.apache.org/apidocs/org/apache/calcite/rel/core/tabscan.html)、[`TableModify`](https://calcite.apache.org/apidocs/org/apache/calcite/rel/core/TableModify.html)、[`Values`](https://calcite.apache.org/apidocs/org/apache/calcite/rel/core/Values.html)、[`Project`](https://calcite.apache.org/apidocs/org/apache/calcite/rel/core/Project.html)、[`Filter`](https://calcite.apache.org/apidocs/org/apache/calcite/rel/core/Filter.html)、 [`Aggregate`](https://calcite.apache.org/apidocs/org/apache/calcite/rel/core/Aggregate.html)，[`Join`](https://calcite.apache.org/apidocs/org/apache/calcite/rel/core/Join.html)、[`Sort`](https://calcite.apache.org/apidocs/org/apache/calcite/rel/core/Sort.html)、[`Union`](https://calcite.apache.org/apidocs/org/apache/calcite/rel/core/Union.html)、[`Intersect`](https://calcite.apache.org/apidocs/org/apache/calcite/rel/core/Intersect.html)、[`Minus`](https://calcite.apache.org/apidocs/org/apache/calcite/rel/core/Minus.html)，[`Window`](https://calcite.apache.org/apidocs/org/apache/calcite/rel/core/Window.html)和[`Match`](https://calcite.apache.org/apidocs/org/apache/calcite/rel/core/Match.html)。

每个类都有一个“纯”逻辑子类，比如[`LogicalProject`](https://calcite.apache.org/apidocs/org/apache/calcite/rel/logical/LogicalProject.html)。任何引擎的适配器，如果能高效地实现某种运算符，则要有一个对应的副本；例如，**Cassandra**适配器有 [`CassandraProject`](https://calcite.apache.org/apidocs/org/apache/calcite/rel/cassandra/CassandraProject.html)，但没有`CassandraJoin`。

可以自定义`RelNode`的子类，以添加新的**运算符**，或者在特定引擎中实现现有运算符。

为了使运算符实用且有效，要用 [planner 规则]()将其与现有运算符结合（当然需要提供元数据，见[下文]()）。**这是代数，但效果是组合的：编写一些规则，组合起来可处理指数级的查询模式**。

如果可能，从现有运算符的派生出你的子类，然后可以重用或修改其规则。更好的是，如果自定义的运算符是<u>可以根据现有运算符重写的逻辑运算符</u>（同样，通过 planner 规则），则应该这样做。你将能够重用这些运算符的规则、元数据和实现，而没有额外的工作。


### Planner rule

> A planner rule ([`class RelOptRule`](https://calcite.apache.org/apidocs/org/apache/calcite/plan/RelOptRule.html)) transforms a relational expression into an equivalent relational expression.
>
> A planner engine has many planner rules registered and **fires** them to transform the input query into something more efficient. Planner rules are therefore central to the optimization process, but surprisingly each planner rule does not concern itself with cost. The planner engine is responsible for firing rules in a sequence that produces an optimal plan, but each individual rules only concerns itself with correctness.
>
> Calcite has two built-in planner engines: [`class VolcanoPlanner`](https://calcite.apache.org/apidocs/org/apache/calcite/plan/volcano/VolcanoPlanner.html) uses dynamic programming and is good for exhaustive search, whereas [`class HepPlanner`](https://calcite.apache.org/apidocs/org/apache/calcite/plan/hep/HepPlanner.html) fires a sequence of rules in a more fixed order.

Planner 规则 ([`class RelOptRule`](https://calcite.apache.org/apidocs/org/apache/calcite/plan/RelOptRule.html)) 将关系表达式转换为等效的关系表达式。Planner 引擎已注册了许多规则，（根据匹配的关系表达式以）<u>**触发**它们</u>将输入的查询<u>转换为</u>更有效的查询。因此，planner 规则是优化过程的核心，但令人惊讶的是，规则本身并不关心成本。Planner 引擎负责按顺序触发规则，从而生成最佳计划，但是每个规则仅考虑自身的正确性。

Calcite具有两个内置的计划器引擎：[`class VolcanoPlanner`](https://calcite.apache.org/apidocs/org/apache/calcite/plan/volcano/VolcanoPlanner.html)使用动态编程，可以穷举。 搜索，而[`class HepPlanner`](https://calcite.apache.org/apidocs/org/apache/calcite/plan/hep/HepPlanner.html)会以更固定的顺序触发一系列规则。

### [调用约定（Calling conventions）](https://calcite.apache.org/docs/adapter.html#calling-conventions)

> A calling convention is a protocol used by a particular data engine. For example, the Cassandra engine has a collection of relational operators, `CassandraProject`, `CassandraFilter` and so forth, and these operators can be connected to each other without the data having to be converted from one format to another.
>
> If data needs to <u>be converted from one calling convention to another</u>, Calcite uses a special sub-class of relational expression called a converter (see [`class Converter`](https://calcite.apache.org/apidocs/org/apache/calcite/rel/convert/Converter.html)). But of course converting data has a runtime cost.
>
> When planning a query that uses multiple engines, Calcite “colors” regions of the relational expression tree according to their calling convention. The planner pushes operations into data sources by firing rules. If the engine does not support a particular operation, the rule will not fire. Sometimes an operation can occur in more than one place, and ultimately the best plan is chosen according to cost.
>
> A calling convention is a class that implements [`interface Convention`](https://calcite.apache.org/apidocs/org/apache/calcite/plan/Convention.html), an auxiliary interface (for instance [`interface CassandraRel`](https://calcite.apache.org/apidocs/org/apache/calcite/adapter/cassandra/CassandraRel.html)), and a set of sub-classes of [`class RelNode`](https://calcite.apache.org/apidocs/org/apache/calcite/rel/RelNode.html) that implement that interface for the core relational operators ([`Project`](https://calcite.apache.org/apidocs/org/apache/calcite/rel/core/Project.html), [`Filter`](https://calcite.apache.org/apidocs/org/apache/calcite/rel/core/Filter.html), [`Aggregate`](https://calcite.apache.org/apidocs/org/apache/calcite/rel/core/Aggregate.html), and so forth).

调用约定是特定数据引擎使用的协议。例如，Cassandra引擎有一组关系运算符， `CassandraProject`， `CassandraFilter` 等等，这些运算符可以相互连接，而无需将数据从一种格式转换为另一种格式。

如果需要将数据<u>从一个调用约定转换为另一个调用约定</u>，Calcite使用关系表达式的一个特殊子类，称为转换器，请参见[`class Converter`](https://calcite.apache.org/apidocs/org/apache/calcite/rel/convert/Converter.html)。当然，转换数据有运行时成本。

如果查询使用多个引擎，Calcite根据调用约定，对关系表达式树进行“<u>==上色==</u>”。Planner通过触发规则将运算符下推到数据源中。如果数据引擎不支持某个运算符，则不触发规则。<u>有时，一个运算符可以在多个地方出现，最终根据成本选择最佳方案</u>。

**调用约定**是一个实现了[`interface Convention`](https://calcite.apache.org/apidocs/org/apache/calcite/plan/Convention.html)的类、一个辅助接口（例如，[`interface CassandraRel`](https://calcite.apache.org/apidocs/org/apache/calcite/adapter/cassandra/CassandraRel.html)）和一组 [`interface RelNode`](https://calcite.apache.org/apidocs/org/apache/calcite/rel/RelNode.html)的子类，它们实现了核心关系操作符，如[`Project`](https://calcite.apache.org/apidocs/org/apache/calcite/rel/core/Project.html)，[`Filter`](https://calcite.apache.org/apidocs/org/apache/calcite/rel/core/Filter.html)，[`Aggregate`](https://calcite.apache.org/apidocs/org/apache/calcite/rel/core/Aggregate.html) 等等。

### 内置SQL实现

> How does Calcite implement SQL, if an adapter does not implement all of the core relational operators?
>
> The answer is a particular built-in calling convention, [`EnumerableConvention`](https://calcite.apache.org/apidocs/org/apache/calcite/adapter/EnumerableConvention.html). Relational expressions of enumerable convention are implemented as “built-ins”: Calcite generates Java code, compiles it, and executes inside its own JVM. Enumerable convention is less efficient than, say, a distributed engine running over column-oriented data files, but it can implement all core relational operators and all built-in SQL functions and operators. If a data source cannot implement a relational operator, enumerable convention is a fall-back.

如果适配器没有实现所有核心关系运算符，Calcite如何完成SQL查询？

答案是内置的一个特殊的调用约定：[`EnumerableConvention`](https://calcite.apache.org/apidocs/org/apache/calcite/adapter/EnumerableConvention.html)。**可枚举调用约定**的关系表达式作为“内置”实现：Calcite生成Java代码，编译它，并在自己的JVM中执行。它比在面向列的数据文件上运行的分布式引擎效率低，但是它可以实现所有核心关系运算符，以及内置的所有SQL函数和运算符。它是数据源无法实现关系运算符时的一个后备。

### [Statistics and cost](https://calcite.apache.org/docs/adapter.html#statistics-and-cost)

Calcite has a metadata system that allow you to define cost functions and statistics about relational operators, collectively referred to as *metadata*. Each kind of metadata has an interface with (usually) one method. For example, selectivity is defined by [`interface RelMdSelectivity`](https://calcite.apache.org/apidocs/org/apache/calcite/rel/metadata/RelMdSelectivity.html) and the method [`getSelectivity(RelNode rel, RexNode predicate)`](https://calcite.apache.org/apidocs/org/apache/calcite/rel/metadata/RelMetadataQuery.html#getSelectivity-org.apache.calcite.rel.RelNode-org.apache.calcite.rex.RexNode-).

There are many built-in kinds of metadata, including [collation](https://calcite.apache.org/apidocs/org/apache/calcite/rel/metadata/RelMdCollation.html), [column origins](https://calcite.apache.org/apidocs/org/apache/calcite/rel/metadata/RelMdColumnOrigins.html), [column uniqueness](https://calcite.apache.org/apidocs/org/apache/calcite/rel/metadata/RelMdColumnUniqueness.html), [distinct row count](https://calcite.apache.org/apidocs/org/apache/calcite/rel/metadata/RelMdDistinctRowCount.html), [distribution](https://calcite.apache.org/apidocs/org/apache/calcite/rel/metadata/RelMdDistribution.html), [explain visibility](https://calcite.apache.org/apidocs/org/apache/calcite/rel/metadata/RelMdExplainVisibility.html), [expression lineage](https://calcite.apache.org/apidocs/org/apache/calcite/rel/metadata/RelMdExpressionLineage.html), [max row count](https://calcite.apache.org/apidocs/org/apache/calcite/rel/metadata/RelMdMaxRowCount.html), [node types](https://calcite.apache.org/apidocs/org/apache/calcite/rel/metadata/RelMdNodeTypes.html), [parallelism](https://calcite.apache.org/apidocs/org/apache/calcite/rel/metadata/RelMdParallelism.html), [percentage original rows](https://calcite.apache.org/apidocs/org/apache/calcite/rel/metadata/RelMdPercentageOriginalRows.html), [population size](https://calcite.apache.org/apidocs/org/apache/calcite/rel/metadata/RelMdPopulationSize.html), [predicates](https://calcite.apache.org/apidocs/org/apache/calcite/rel/metadata/RelMdPredicates.html), [row count](https://calcite.apache.org/apidocs/org/apache/calcite/rel/metadata/RelMdRowCount.html), [selectivity](https://calcite.apache.org/apidocs/org/apache/calcite/rel/metadata/RelMdSelectivity.html), [size](https://calcite.apache.org/apidocs/org/apache/calcite/rel/metadata/RelMdSize.html), [table references](https://calcite.apache.org/apidocs/org/apache/calcite/rel/metadata/RelMdTableReferences.html), and [unique keys](https://calcite.apache.org/apidocs/org/apache/calcite/rel/metadata/RelMdUniqueKeys.html); you can also define your own.

You can then supply a *metadata provider* that computes that kind of metadata for particular sub-classes of `RelNode`. Metadata providers can handle built-in and extended metadata types, and built-in and extended `RelNode` types. While preparing a query Calcite combines all of the applicable metadata providers and maintains a cache so that a given piece of metadata (for example the selectivity of the condition `x > 10` in a particular `Filter` operator) is computed only once.

