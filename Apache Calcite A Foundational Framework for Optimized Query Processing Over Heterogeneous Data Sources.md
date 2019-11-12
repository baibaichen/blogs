# Apache Calcite: A Foundational Framework for Optimized Query Processing Over Heterogeneous Data Sources

## 5 ADAPTERS

An adapter is an architectural pattern that defines how **Calcite** incorporates diverse data sources for general access. Figure 3 depicts its components. Essentially, an adapter consists of a **model**, a **schema**, and a **schema factory**. The *model* is a specification of the physical properties of the data source being accessed. A *schema* is the definition of the data (format and layouts) found in the model. The data itself is physically accessed via tables. **Calcite** interfaces with the tables defined in the adapter to read the data as the query is being executed. The adapter may define a set of rules that are added to the **planner**. For instance, it typically includes rules to convert **various types of logical relational expressions** to <u>the corresponding relational expressions of the adapter’s convention</u>. The *schema factory* component acquires the metadata information from the model and generates a schema.

> TODO: 图3

> 适配器是一种架构模式，定义了**Calcite**如何合并各种数据源以进行常规访问。 图3描绘了它的组件。本质上，适配器由**模型**，**模式**和**模式工厂**组成。<u>模型</u>是被访问数据源物理属性的规范。<u>模式</u>是在模型中找到的数据（格式和布局）的定义。数据本身可以通过表进行物理访问。 **Calcite**与适配器中定义的表接口进行交互，以在执行查询时读取数据。适配器可以定义添加到planner的一组规则。例如，它通常包含将<u>各种类型的逻辑关系表达式</u>转换为<u>适配器约定的对应关系表达式</u>的规则。 <u>模式工厂</u>组件从模型获取元数据信息并生成模式。

As discussed in Section 4, Calcite uses a <u>physical trait</u> known as the calling convention to identify relational operators which correspond to a specific database backend. These physical operators **implement the access paths** for <u>the underlying tables in each adapter</u>. When a query is parsed and converted to a relational algebra expression, an operator is created for each table representing a scan of the data on that table. It is the minimal interface that an adapter must implement. **If an adapter implements the table scan operator, the Calcite optimizer is then able to use client-side operators such as sorting, filtering, and joins to execute arbitrary SQL queries against these tables**.

> 如第4节所述，Calcite使用一种称为调用规范的<u>物理特征</u>来识别与<u>特定数据库后端相对应</u>的**关系运算符**。这些物理运算符<u>为每个适配器中的基础表</u>**实现访问路径**。当查询被解析并转换为关系代数表达式时，将为每个表创建一个运算符，表示对该表上数据的扫描。它是适配器必须实现的最小接口。 **如果适配器实现了<u>表扫描运算符</u>，则Calcite优化器将能够使用客户端运算符（例如排序，过滤和联接）对这些表执行任意SQL查询**。

This table scan operator contains the necessary information the adapter requires to issue the scan to the adapter’s backend database. To extend the functionality provided by adapters, Calcite defines an `enumerable` calling convention. Relational operators with the `enumerable` calling convention simply operate over tuples via an **iterator** interface. This calling convention allows **Calcite** to implement operators which may not be available in each adapter’s backend. For example, the `EnumerableJoin` operator implements joins by collecting rows from its child nodes and joining on <u>the desired attributes</u>.

> 这个**表扫描运算符**包含适配器向其后端数据库发出扫描所需的必要信息。为了扩展适配器提供的功能，Calcite定义了`enumerable`调用规范。具有`enumerable`调用规范的关系运算符只需通过**迭代器**接口对元组进行操作。这个调用规范使**Calcite**可实现适配器后端数据库可能并不存在的运算符。例如`EnumerableJoin`运算符，它实现联接的方式是，从其子节点收集记录，并在<u>所需属性</u>上进行联接。

For queries which only touch a small subset of the data in a table, it is inefficient for Calcite to enumerate all tuples. Fortunately, the same rule-based optimizer can be used to implement adapter-specific rules for optimization. For example, suppose a query involves filtering and sorting on a table. <u>An adapter which can perform filtering on the backend</u> can implement <u>a rule which matches a `LogicalFilter`</u> and converts it to the adapter’s calling convention. This rule converts the LogicalFilter into another Filter instance. This new Filter node has a lower ==associated cost== that allows Calcite to optimize queries across adapters.

> 对于仅涉及表中一小部分数据的查询，**Calcite**没有必要枚举所有元组。幸运的是，可以使用相同的基于规则的优化器来实现特定于适配器的规则以进行优化。例如，假设查询涉及对表进行过滤和排序。<u>可在后端执行过滤的适配器</u>可以实现<u>匹配`LogicalFilter`的规则</u>，将其转换为适配器的调用规范。 此规则将`LogicalFilter`转换为另一种`Filter`的实例。 这个新的`Filter`节点具有较低的==关联成本==，使得Calcite可以跨适配器优化查询。

The use of adapters is a powerful abstraction that enables not  only optimization of queries for a specific backend, but also across multiple backends. Calcite is able to answer queries involving tables across multiple backends by pushing down all possible logic to each backend and then performing joins and aggregations on the resulting data. Implementing an adapter can be as simple as providing a table scan operator or it can involve the design of many advanced optimizations. Any expression represented in the relational algebra can be pushed down to adapters with <u>optimizer rules</u>.

> 适配器是一个强大的抽象，使用它不仅可以针对特定的后端优化查询，还可以在多个后端之间进行优化。通过将所有可能的逻辑下推到每个后端，然后关联和聚合结果数据，**Calcite**能够执行涉及多个后端表的查询。实现适配器可以像提供表扫描操作符一样简单，也可以涉及许多高级优化的设计。用关系代数表示的任何表达式都可以利用<u>优化器规则</u>下推到适配器。























I 