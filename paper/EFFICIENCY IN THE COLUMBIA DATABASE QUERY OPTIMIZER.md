# EFFICIENCY IN THE COLUMBIA DATABASE QUERY OPTIMIZER

[TOC]

## Chapter 2 . Terminology

In this section we review the terminology and fundamental concepts in the literature of query optimization [ElN94] [Ram97], which are also used in the description of the design and implementation of Columbia. More detailed terminology will be discussed in Chapter 4, the structure of the Columbia optimizer.

### 2.1. Query Optimization

The purpose of a query processor is to take a request expressed in the data manipulation language (DML) of a database system and evaluate it against the contents of a database.

> - [ ]  Figure 1. Query Processing

Figure 1 diagrams the steps in query processing. The original query in DML syntax is parsed into a logical expression tree over a logical algebra that is easily manipulated by later stages. This internal logical form of the query then passes to the Query Optimizer, which is responsible for transforming the logical query into a physical plan^1^ that will be executed against the physical data structure holding the data. Two kinds of transformations will be performed: Logical transformations which create alternative logical forms of the query, such as commuting the left and right children of the tree, and physical transformations which choose a particular physical algorithm to implement a logical operator, such as sort-merge join for join. This process generates, in general, a large number of plans that implement the query tree. Finding the optimal plan (relative to the cost model, which includes statistical and other catalog information) is the main concern of the query optimizer. Once an optimal (or near optimal) physical plan for the query is selected, it is passed to the query execution engine. The query execution engine executes the plan using the stored database as input, and produces the result of the query as output.

> 1. A plan is an algebra expression with a choice of implementation methods for each operator that it contains.

If we look at the user level, the query processing is hidden in the dark box of the query processor. Users will submit their queries, which in general, are written in a high level language like SQL, Quel, or OQL [Cat94] (in the case of object-oriented database systems) to the database system, with the expectation that the system will output the results of the queries correctly and quickly. Correctness is an absolute requirement of a query processor, while performance is a desirable feature and the main concern of a query processor. As we can see in the system level of query processing, the query optimizer is a critical component that contributes to high performance. There are a large number of plans that implement the query correctly, but with different execution performance for some performance measures (e.g., execution time, memory). One goal of the optimizer is to find the plan with the best^2^ execution performance. A naive way an optimizer could do this is to generate all possible plans and choose the cheapest. But exploring all possible plans is prohibitively expensive because of the large number of alternative plans for even relatively simple queries. Thus optimizers have to somehow narrow down the space of alternative plans that they consider.

Query optimization is a complex search problem. Research has shown that simplified versions of this problem are NP-hard [IbK84]. In fact, even for the simplest class of relational joins, the number of joins that have to be evaluated when using dynamic programming is exponential with the number of input relations [OnL90]. So a good search strategy is critical to the success of an optimizer.

The remainder of this chapter will review some fundamental concepts used in solving the query optimization problem. We will use these concepts to describe the design and implementation of Columbia.

> 2. In theory, optimality is the goal. However, optimality is relative to many aspects, such as cost model, catalog information and sometimes to a particular subset of the search space. So, in practice and more precisely, the goal of a query optimizer is to find a good evaluation plan for a given query.

### 2.2. Logical Operators and Query Tree

**Logical operators** are high-level operators that specify data transformations without specifying the physical execution algorithms to be used. In the relational model, logical operators generally take tables^3^ as inputs, and produce a single table as output. Each logical operator takes a fixed number of inputs (which is called the arity of the operator) and may have parameters that distinguish the variant of an operator. Two typical logical operators are GET and EQJOIN. The GET operator has no input and one argument, which is the name of the stored relation. GET retrieves the tuples of the relation from disk and outputs the tuples for further operations. The EQJOIN operator has two inputs, namely the left and right tables to be joined, and one argument which is a set of join predicates relating to the left and right tables.

> 3. Here, we define table as a collection of tuples. In the relational model, it can be a real stored relation (roughly, a disk file) or a temporary collection of tuples produced in the evaluation of a query.

A **query tree** is a tree representation of a query and serves as the input to an optimizer. Typically a query tree is represented as a tree of logical operators in which each node is a logical operator having zero or more logical operators as its inputs. The number of children of the node is exactly the arity of the operator. Leaves of the tree are operators with zero arity. An example of a query tree representation of a query is showed in Figure 2.

Query trees are used to specify the order in which operators are to be applied. In order to apply the top operator in the tree, its inputs must be applied first. In this example, EQJOIN has two inputs, which are taken from the outputs of two GET operators. The argument of EQJOIN, i.e., “Emp.dno=Dept.dno”, describes the condition of the join operation. The output of EQJOIN will produce the result of query. GET operators have no input, so they are the leaves of the tree and generally provide data sources of the query evaluation. The argument of each GET operator defines which stored relation will be retrieved.

> - [ ] Figure 2. Query Representation

### 2.3. Physical Operators and Execution Plan

Physical Operators represent specific algorithms that implement particular database operations. One or more physical execution algorithms can be used in a database for implementing a given query logical operator. For instance, the EQJOIN operator can be implemented using **nested-loops** or **sort-merge** or other algorithms. These specific algorithms can be implemented in different physical operators. Thus, two typical physical operators are LOOPS_JOIN, which implements the nested-loops join algorithm, and MERGE_JOIN, which implements the sort-merge join algorithm. The typical implementing algorithm for the GET logical operator is scanning the table in stored order, which is implemented in another physical operator FILE_SCAN. Like logical operators, each physical operator also has fixed number of inputs (which is the arity of the operator), and may have parameters.

Replacing the logical operators in a query tree by the physical operators which can implement them gives rise to a tree of physical operators which is called an Execution Plan or access plan for the given query. Figure 3 shows two possible execution plans corresponding to the query tree in Figure 2(b).

> - [ ] Figure 3. Execution plans

Execution plans specify how to evaluate the query. Each plan has an execution cost corresponding to the cost model and catalog information. In general, a good execution plan for a given query is generated by the optimizer and serves as the input to the Query Execution Engine which executes the overall algorithms against the data of database systems to produce the output result of the given query.

**物理运算符**表示实现数据库特定操作的具体算法

### 2.4. Groups

> A given query can be represented by one or another query tree that is logically equivalent. Two query trees are logically equivalent if they output exactly the same result for any population of the database [Gra95]. For each query tree, in general, there are one or more corresponding execution plans implementing the query tree and producing exactly the same result. Analogously, these execution plans are logically equivalent. Figure 4 shows several logically equivalent query trees and logically equivalent execution plans implementing the query trees.
>
> > - [ ] Figure 4. Logically equivalent query trees and plans
>
> As shown in Figure 4, we denote an **EQJOIN** operator by $\Join$, **LOOPS_JOIN** by $\Join_L$, and **MERGE_JOIN** by $\Join_M$. To simplify, we also denote a **GET** operator by its argument and **FILE_SCAN** by its argument plus sub F. In Figure 4, (a) and (b) are two logically equivalent query trees. The difference is the order of logical operators. (a-i) and (a-ii) are two logically equivalent execution plans implementing query tree (a). They use two different join algorithms.
>
> We can also use **expressions** to represent query trees and execution plans (or sub trees and sub plans). An expression consists of an operator plus zero or more input expressions. We refer to an expression as logical or physical based on the type of its operator. So query trees are logical expressions and execution plans are physical expressions.
>
> Given a logical expression, there are a number of logically equivalent logical and physical expressions. It is useful to collect them into groups and define their common characteristics. A Group is a set of logically equivalent expressions^4^. In general, a group will contain all equivalent logical forms of an expression, plus all physical expressions derivable based on selecting allowable physical operators for the corresponding logical forms. Usually, there will be more than one physical expression for each logical expression in a group. Figure 5 shows a group containing the expressions in Figure 4 and other equivalent expressions.
>
> > 4. Note that a group might not contain all equivalent expressions. In some case where a pruning technique has been applied, some expressions will not be considered and do not need to be included in the group.
>
> > - [ ] Figure 5. Equivalent expressions in groups [ABC]
>
> We usually denote a group by one of its logical expressions. For instance, $(A \Join B) \Join C$, or simply [ABC]. Figure 5 shows all^5^ equivalent logical expressions for the group [ABC] and some physical expressions. We can see that there are a number of equivalent expressions, even for logical expressions.
>
> > 5. For simple cases, the group consists of join operators only.
>
> To reduce the number of expressions in a group, **Multi-expressions** are introduced. A **Multi-expression** consists of a logical or physical operator and takes groups as inputs. <u>A multi-expression is the same as an expression except it takes groups as inputs while expressions take other expressions as inputs</u>. For instance, the multi-expression “$[AB] \Join [C]$ ” denotes the EQJOIN operator taking the groups [AB] and [C] as its inputs. ==The advantage of multi-expressions is the great savings in space because there will be fewer equivalent multi-expressions in a group==. Figure 6 shows the equivalent multi-expressions in the group [ABC]. There are many fewer multi-expressions than expressions in figure 5. In fact, one multi-expression represents several expressions by taking groups as inputs. With multi-expressions, a group can be re-defined as a set of logically equivalent multi-expressions.
>
> > - [ ] Figure 6. Equivalent multi-expressions in group [ABC]
>
> In the typical processing of a query, many intermediate results (collections of tuples) are produced before the final result is produced. An intermediate result is produced by computing an execution plan (or a physical expression) of a group. In this meaning, groups correspond to intermediate results (these groups are called intermediate groups). There is only one final result, whose group is called the final group).
>
> The **Logical properties** of a group are defined as the logical properties of the result, regardless of how the result is physically computed and organized. These properties include the cardinality (number of tuples), the schema, and other properties. Logical properties apply to all expressions in a group.
>

给定的查询可以由逻辑上等价的多个查询树表示。如果无论数据库内有什么数据，两个查询树都输出完全相同的结果，那么它们在逻辑上是等价的[Gra95]。对于每个查询树，通常都有一个或多个相应的**==执行计划==**来实现并生成完全相同的结果。类似地，这些执行计划在逻辑上是等价的。图 4 是逻辑上等价的查询树和（实现它们的）执行计划。

> - [ ] Figure 4. Logically equivalent query trees and plans

如图 4 所示，我们用 $\Join$ 表示 **EQJOIN** 运算符，用 $\Join_L$ 表示 **LOOPS_JOIN**，用 $\Join_M$ 表示 **MERGE_JOIN **。 为了简化，我们还通过其参数表示 **GET** 运算符，参数加上下标 **F** 表示 **FILE_SCAN**。图 4 中，（a）和（b）是两个逻辑上等价的查询树。区别在于逻辑运算符的顺序。（a-i）和（a-ii）是实现查询树（a）的两个逻辑上等价的执行计划。它们使用两种不同的联接算法。

我们还可以使用**表达式**来表示查询树和执行计划（或子树和子计划）。表达式由一个运算符加上零个或多个输入表达式组成。根据运算符的类型，我们将表达式称为逻辑或物理表达式。 因此，查询树是逻辑表达式，执行计划是物理表达式。

给定一个逻辑表达式，存在许多逻辑等价的逻辑和物理表达式。将它们分组并定义其共同特征很有用。 **Group** 是一组逻价上等价的表达式^4^。通常，**Group** 将包含表达式所有等价的逻辑形式，加上由此派生的物理表达式（只能产生允许的物理运算符）。通常，一个 Group 中每个逻辑表达式都会有多个物理表达式。图 5 中的这组表达式都和图 4 的表达式等价。

> 4. 请注意，Group 不一定包含所有等价的表达式。在裁剪的情况下，某些表达式将不被考虑并且不会包含在组中。

> - [ ] Figure 5. Equivalent expressions in groups [ABC]

我们通常用一个逻辑表达式来表示一个 Group。例如，$(A \Join B) \Join C$，或简称为 [ABC]。图5显示了组[ABC]所有^5^等价的逻辑表达式和一些物理表达式。我们可以看到，即使对于逻辑表达式，也存在许多等效表达式。

> 5. 对于简单的情况，Group 仅由联接运算符组成。

为了减少 Group 中表达式的数量，引入了**==多表达式==**。**多表达式**由逻辑或物理运算符组成，并将 **<u>Group</u>** 作为输入。<u>==多表达式与表达式相同，只是它将组作为输入，而表达式将其他表达式作为输入==</u>。例如，多表达式 $[AB]\Join[C]$ 表示 **EQJOIN** 运算符将组 [AB] 和 [C] 作为其输入。多表达式的优点是极大地节省了空间，因为在一个 Group 中等价的<u>==多表达式==</u>会更少。图 6 是 Group [ABC] 中等价的多表达式。与图 5 的表达式相比，多表达式要少得多。实际上，一个多表达式通过将 Group 作为输入来表示多个表达式。对于多表达式，可以将 Group 重新定义为一组逻辑等价的多表达式。

> - [ ] Figure 6. Equivalent multi-expressions in group [ABC]

在查询的典型处理过程中，会在生成最终结果之前生成许多中间结果（元组集合）。通过计算 Group 的执行计划（或物理表达式）生成中间结果。在这个意义上，Group 对应于中间结果（这些 Group 称为中间Group）。只有一个最终结果，它的 Group 被称为最终 Group。

组的逻辑属性定义为结果的逻辑属性，而不管物理上，如何计算和组织结果。这些属性包括基数（元组数）、schema 和其他属性。逻辑属性应用于组中所有表达式。

### 2.5. The Search Space

The **search space** represents logical query trees and physical plans for a given initial query. To save space, the search space is represented as a set of groups, each group takes some groups as input. There is a top group designated as the final group, corresponding to the final result from the evaluation of the initial query. Figure 7 shows the initial search space of the given query.

> - [ ] Figure 7. Initial Search Space of a given query

In the **initial search space**, each group includes only one logical expression, which came from the initial query tree. In figure 7, the top group, group [ABC], is the final group of the query. It corresponds to the final result of the joins of three relations. <u>We can derive the initial query tree from an initial search space</u>. Each node in a query tree corresponds to an operator of a multi-expression in each group of the search space. In Figure 7, top group [ABC] has a multi-expression which consists of an operator EQJOIN and two groups, [AB] and [C], as inputs. We can derive a query tree with the EQJOIN as the top operator and the input operators are derived from group [AB] and group [C], keep deriving input operators of the query tree from the input groups recursively until the considering groups are leaves (no input). The query tree derived from this initial search space is exactly the initial query tree. In other words, initial search space represents initial query tree.

在**初始搜索空间**中，每个 Group 只包含一个来自初始查询树的逻辑表达式。在图 7 中，最上面的 Group （[ABC]）是查询的最后一个 Group。它对应于三个关系联接的最终结果。

==我们可以从初始搜索空间中得出初始查询树。查询树中的每个节点对应于搜索空间的每个组中的多表达式运算符。 在图7中，顶部的[ABC]组具有一个多表达式，该表达式由一个运算符EQJOIN和两个组[AB]和[C]作为输入组成。 我们可以使用EQJOIN作为顶部运算符来派生查询树，并且输入运算符是从组[AB]和组[C]中派生的，继续递归地从输入组派生查询树的输入运算符，直到考虑的组离开为止（ 无输入）。 从此初始搜索空间派生的查询树恰好是初始查询树。 换句话说，初始搜索空间代表初始查询树。==

==我们可以从初始搜索空间中导出初始查询树。查询树中的每个节点对应于搜索空间每组中的多表达式的一个运算符。在图7中，top group[ABC]有一个多表达式，它由一个操作符EQJOIN和两个组[AB]和[C]组成，作为输入。我们可以导出一个以EQJOIN作为顶层操作符的查询树，并且输入操作符是从组[AB]和组[C]派生的，不断地从输入组递归地派生查询树的输入操作符，直到考虑的组是叶子（没有输入）。从这个初始搜索空间派生的查询树就是初始查询树。换句话说，初始搜索空间表示初始查询树。==

In the course of optimization, the logically equivalent logical and physical expressions for each group are generated and the search space greatly expands. Each group will have a large number of logical and physical expressions. At the same time as the optimization generates physical expressions, the execution costs of the physical expressions (i.e., execution plans) are calculated. In some sense, generating all the physical expressions is the goal of the optimization since we want to find the cheapest plan and we know that costs are only related to physical expressions. But in order to generate all the physical expressions, all the logical expressions must be generated since each physical expression is the physical implementation of a logical expression. After the optimization is done, namely, all equivalent physical expressions are generated for each group and the costs of all possible execution plans are calculated, the cheapest execution plan can be located in the search space and served as the output of the optimizer. A completely expanded search space is called a final search space. Normally^6^, a final search space represents all the logically equivalent expressions (logical and physical) of a given query. In fact, all the possible query trees and execution plans can be derived from the final search space by using the recursive method we use to derive the initial query tree from the initial search space. Each (logical or physical) operator of a multi-expression in the search space serves as an operator node in a query tree or an execution plan. Since a group in the search space contains a number of logical equivalent expressions, the final search space represents a large number of query trees and execution plans.

> 6. In some cases, pruning applies to the expansion of the search space, and then some expressions may not be generated. It may be that entire groups are not expanded. Some pruning techniques will be described in Section 4.4.

Table 1 [Bil97] shows the complexity of complete logical search space of join of n relations. (Only the numbers of logical expressions are showed.) For example, the search space of join of 4 relations has 15 groups, includes 54 logical expressions and represents 120 query trees.

> - [ ] Table 1. Complexity of Join of n Relations [Bil97]

As can be seen from Table 1, even considering only the logical expressions, the size of the search space increases dramatically (exponentially) as the number of the  joined relations increases. The number of physical expressions depends on how many^7^ implementation algorithms used for the logical operators. For example, if there are N logical expressions in the search space, and M (M>=1) join algorithms are used in the database systems, then there will be M*N total physical expressions in the search space. So the number of physical expressions is at least the same as the number of logical expressions or larger.

> 7. Different database systems may choose a certain different number of algorithms to implement one logical operator. For example, nested-loops, sort-merge and indexnested-loops are the common join algorithms database systems choose.

---

**搜索空间**表示给定初始查询的逻辑查询树和物理计划。为了节省空间，搜索空间被表示为 Group 的集合，每个 Group 接受一些 Group 作为输入。有一个顶层 Group 被指定为最终 Group，与初始查询的计算结果相对应。图 7 显示了给定查询的初始搜索空间

> - [ ] Figure 7. Initial Search Space of a given query



### 2.6 Rules

> Many optimizers use rules to generate the logically equivalent expressions of a given initial query. A rule is a description of how to transform an expression to a logically equivalent expression. A new expression is generated when a rule is applied to a given expression. It is the rules that an optimizer uses to expand the initial search space and generate all the logically equivalent expressions of a given initial query.
>
> Each rule is defined as a pair of pattern and substitute. A pattern defines the structure of the logical expression that can be applied to the rule. A substitute defines the structure of the result after applying the rule. When expanding the search space, the optimizer will look at each logical expression, (note that rules only apply to logical expressions), and check if this expression matches any patterns of the rules in the rule set. If the pattern of a rule is matched, the rule is fired to generate the new logically equivalent expression according to the substitute of the rule.
>
> Cascades used expressions to represent patterns and substitutes. Patterns are always logical expressions, while substitutes can be logical or physical. Transformation rules and implementation rules are two common types of rules. A rule is called transformation rule if its substitute is a logical expression. A rule is called implementation rule if its substitute is a physical expression.
>
> For example, **EQJOIN_LTOR** is a transformation rule that applies left to right associativity to a left deep logical expression and generates a right deep logical expression that is logically equivalent to the original expression. EQJOIN_MERGEJOIN is an implementation rule that generates a physical expression  by replacing the EQJOIN operator with MERGEJOIN physical operator. This physical expression implements the original logical expression using sort-merge join algorithm. Figure 8 shows a picture of these two simple rules.
>

许多优化器使用**规则**来生成给定初始查询的逻辑上等价的表达式。**<u>规则是描述如何将表达式转换为逻辑上等价的其他表达式</u>**。将规则应用于给定表达式时，将生成一个新表达式。优化器使用规则**扩展初始搜索空间**，并生成给定初始查询所有逻辑上等价的表达式。

每个规则定义为一对模式和替代。**模式**定义**<u>==符合规则的==</u>**逻辑表达式结构。**替代**定义了应用规则后逻辑表达式结构。扩展搜索空间时，优化器将查看每个逻辑表达式（注意，<u>规则仅适用于逻辑表达式</u>），并检查此表达式是否与规则集中的任何规则模式匹配。如果匹配某个规则的模式，则根据规则的替换，触发规则以生成新的逻辑等价表达式。

Cascades 使用表达式表示模式和替代。**模式总是逻辑表达式**，而**替代**可以是逻辑或物理表达式。转换规则和实现规则是两种常见的规则类型。如果规则的替代物是逻辑表达式，则称为转换规则。如果规则的替代物是物理表达式，则称为实现规则。

例如，**EQJOIN_LTOR** 是一个转换规则，将左到右的关联性应用于左深度逻辑表达式，并生成逻辑上等价于原始表达式的右深逻辑表达式。**EQJOIN_MERGEJOIN** 是一个实现规则，通过将 **EQJOIN** 运算符替换为 **MERGEJOIN** 物理运算符来生成物理表达式。该物理表达式使用 sort-merge-join 算法实现原始逻辑表达式。 图 8 显示了这两个简单的规则。

> - [ ] Figure 8. Two types of Rules

## Chapter 3. Related Work
Pioneering work in query optimization can be traced back to two decades ago. IBM’s System R optimizer [SAC+79] succeeded and worked so well that it has served as the foundation for many current commercial optimizers.

Database systems and applications evolve and demand new generations of optimizers to handle new extensions to database systems. The relational data model is extended with more features, such as supporting new data types and new operations. The object oriented data model is introduced to handle more complex data. Since early optimizers were designed to use with a relatively simple relational data model, new generations of extensible optimizers were developed to meet the requirements of evolving database systems. The new generations of optimizers focus on extensibility as well as the difficult goal of all optimizers: efficiency. This chapter will look at some notable optimizers that contribute significantly to the query optimization literature.

### 3.1 The System R and Starburst Optimizer

### 3.2 The Exodus and Volcano Optimizer Generators

The Exodus optimizer generator [GrD87] was the first extensible optimizer framework using top-down optimization. The goal of Exodus is to build an infrastructure and tool for query optimization with minimal assumptions about the data model. The input into Exodus is a model description file, which describes a set of operators, a set of methods to be considered when building and comparing access plans, transformation rules (defining the transformations of the query tree) and implementation rules (defining the correspondence between operators and methods). To implement a query optimizer for a new data model, the DBI10 writes a model description file and a set of C procedures. The generator transforms the model file into a C program which is compiled and linked with the set of C procedures to generate a data model specific optimizer. The generated optimizer transforms the initial query tree step by step, maintaining information about all the alternatives explored so far in a data structure called MESH. At any time during the optimization there can be a set of possible next transformations, which are stored in a queue structure, called OPEN. When the OPEN is not empty, the optimizer will select a transformation from OPEN, apply it to the correct nodes in MESH, do cost estimation for the new nodes and add newly enable transformation into OPEN.

The main contribution of Exodus is the top-down optimizer generator framework which separates the search strategy of an optimizer from the data model and separates transformation rules and logical operators from implementation rules and physical operators. Although it was difficult to construct efficient optimizers, it contributed as a useful foundation for the next generation of extensible optimizers.

With the primary goal of improving the efficiency of Exodus, Volcano Optimizer Generator [GrM93] is designed to achieve more efficiency, further extensibility and effectiveness. Efficiency was achieved by combing dynamic programming with directed search based on physical properties, branch-and-bound pruning and heuristic guidance into a new search algorithm that is called directed dynamic programming. The search strategy in Volcano is a top-down, goal-oriented control strategy: sub expressions are optimized only if warranted. That is, only those expressions and plans that truly participate in promising larger plans are considered for optimization. It also uses dynamic programming to store all optimal sub plans as well as optimization failures until a query is completely optimized. Since it is very goal-oriented though the use of physical properties ( a generalization of “interesting properties” used in System R) and derives only those expressions and plans which are promising, the search algorithm is efficient. More extensibility in Volcano was achieved by generating optimizer source code from data model specifications and by encapsulating costs as well as logical and physical properties into abstract data types. Effectiveness was achieved by permitting exhaustive search, which is pruned only at the discretion of the optimizer implementers.

The efficiency of the Volcano search strategy permitted the generation of real optimizers, one for an object-oriented database system [BMG93] and one for a prototype scientific database system with many rules [Wog93].

### 3.3 The Cascades Optimizer Framework

> The Cascades Optimizer Framework [Gra95] is an extensible query optimization framework that resolves many short-comings of the EXODUS and Volcano optimizer generators. It achieves a substantial improvement over its predecessors in functionality, ease-of-use, and robustness without giving up extensibility, dynamic programming and memoization. The choosing of Cascades as the foundation for new query optimizers in Tandem’s NonStop SQL product [Cel96] and in Microsoft’s SQL Server product [Gra96] demonstrated that Cascades satisfies the requirements and demands of modern commercial database systems. The following list some of advantages of Cascades:
>
> - Optimization tasks as data structures
> - Rules as objects
> - Rules to place property enforcers such as sort operations
> - Ordering of moves by promise
> - Predicates as operators that is both logical and physical
> - Abstract interface class defining the DBI-optimizer interface and permitting DBI-defined subclass hierarchies.
> - More robust code written in C++ and a clean interface making full use of the abstraction mechanisms of C++
> - Extensive tracing support and better documentation to assist the DBI
>
>In Cascades, the optimization algorithm is broken into several parts, which are called “tasks”. Tasks are realized as objects in which a “perform” method is defined for them. All such task objects are collected in a task structure that is realized as a Last-In-First-Out stack^11^. Scheduling a task is very similar to invoking a function: the task is popped out of the stack and the “perform” method of the task is invoked. At any time during the optimization there is a stack of tasks waiting to be performed. Performing a task may result in more tasks being placed on the stack.
>
>> 11. As [Gra95] pointed out, other task structures can easily be envisioned. In particular, task objects can be reordered very easily at any point, enabling very flexible mechanisms for heuristic guidance, Moreover, There are more advantages in representing the task structure by a graph that captures dependencies or the topological ordering among tasks and permit efficient parallel search (using shared memory).
>
>The Cascades optimizer first copies the original query into the initial search space (**in Cascades, the search space is called “memo” which is inherited from Volcano**). The entire optimization process is then triggered by a task to optimize the top group of the initial search space, which in turn triggers optimization of smaller and smaller subgroups in the search space. Optimizing a group means finding the best plan in the group (which is called an “optimization goal”) and therefore applies rules to all expressions. In this process, new tasks are placed into the task stack and new groups and expressions are added into the search space. After the task of optimizing the top group is completed, which requires all the subgroups of the top group to complete their optimization, the best plan of the top group can be found, hence the optimization is done.
>
>Like the Volcano optimizer generator, Cascades begins the optimization process from the top group and is considered to use a top-down search strategy. Dynamic programming and memoization are also used in the task of optimizing a group. Before initiating optimization of all a group’s expressions, ==it checks whether the same optimization goal has been pursued already==; if so, it simply returns the plan found in the earlier search. One major difference between the search strategies in Cascades and Volcano is that Cascades only explores a group on demand while Volcano always generates all equivalent logical expressions exhaustively in the first pre-optimization phase before the actual optimization phase begin. In Cascades, there is no separation into two phases. It is not useful to derive all logically equivalent forms of all expressions, e.g., of a predicate. A group is explored using transformation rules only on demand, and it is explored only to create all members of the group that match a given pattern. Since it explores groups only for truly useful patterns, Cascades search strategy is more efficient^12^.
>
>> 12. In the worst case, exploration of Cascades is exhaustive. Thus in the worst case the efficiency of the Cascades search will equal that of the Volcano search strategy.
>
>Compared to the Volcano optimizer generator’s cumbersome user interface, Cascades provides a clean data structure abstraction and interface between DBI and optimizer. Each of the classes that makes up the interface between the Cascades optimizer and the DBI is designed to become the root of a subclass hierarchy. The optimizer relies only on the method defined in this interface; the DBI is free to add additional methods when defining subclasses. Some important interfaces include operators, cost model and rules. This clear interface is important in that it makes the optimizer more robust and makes it easier for a DBI to implement or extend an optimizer.
>
>[Bil97] describes an experimental optimizer, Model D, for optimizing the TPC-D queries [TPC95] developed under the Cascades optimizer framework. Model D has many logical operators which in turn require a number of rules and physical operators. The new operators and rules are defined and easily added to the optimizer by the DBI by deriving from the base interface class. With only a few changes to the Cascades search engine, Model D demonstrates the extensibility of the Cascade framework in the relational model.
>
>Cascades is just an optimizer framework. It proposed numerous performance improvements, but many features are currently unused or provided only in rudimentary form. The current design and implementation of Cascades leaves room for many improvements. The strong separation of optimizer framework and the DBI’s specification, extensive use of virtual methods, very frequent object allocation and deallocation can cause performance problems. Some pruning techniques can be applied to the top-down optimization to dramatically improve search performance. All these observations motivate our research in Cascades and development of a new, more efficient optimizer – the Columbia optimizer.
>

Cascades 优化器框架 [Gra95] 是一个可扩展的查询优化框架，它解决了 EXODUS 和 Volcano 优化器生成器的许多缺点。 在不放弃可扩展性、动态编程和 memoization 的情况下，它在功能、易用性和健壮性方面比之前的版本有了实质性的改进。在 Tandem 的 NonStop SQL 产品 [Cel96] 和 Microsoft 的 SQL Server 产品 [Gra96] 中选择 Cascades 作为新查询优化器的基础，表明 Cascades 满足现代商业数据库系统的需求。下面列出了 Cascades 的一些优点：

- 优化任务作为数据结构
- 规则作为对象
- 设置属性强制执行器的规则，如排序操作
- 按承诺排序动作
- 谓词作为逻辑和物理运算符
- 定义 DBI 优化器接口并允许 DBI 定义的子类层次结构的抽象接口类。
- 用 C++ 编写的更健壮的代码和一个干净的界面，充分利用 C++ 的抽象机制
- 广泛的追踪支持和更好的文档来协助 DBI

在 Cascades 中，优化算法分为几个部分，称为**任务**。任务被实现为对象，其中定义了一个 `perform` 方法。所有这些任务对象都收集在一个任务结构中，该结构实现为**后进先出**的堆栈^11^。调度任务非常类似于调用函数：将任务从堆栈中弹出，并调用任务的 `perform`方法。在优化期间的任何时候，都有一堆任务等待执行。执行一个任务可能会导致更多的任务被放置在堆栈上。

> 11. 正如 [Gra95] 所指出的，可以很容易地设想其他任务结构。特别是，任务对象可以很容易地在任何点重新排序，这为启发式指导提供了非常灵活的机制。此外，用 **graph** 来表示任务结构更有优势，**graph** 可以捕获任务之间的依赖关系或拓扑排序，并允许高效的并行搜索(使用共享内存)。

Cascades 优化器首先将原始查询复制到初始搜索空间（在 Cascades 中，搜索空间称为 **memo**，继承自 Volcano）。然后一个任务触发整个优化过程，优化==初始搜索空间的顶层组==，该任务反过来又触发对搜索空间中越来越小的子组进行优化。优化一个组意味着在组中找到最好的计划（称为“优化目标”），因此将规则应用于所有表达式。在此过程中，将新任务放入任务堆栈中，将新组和表达式添加到搜索空间中。当顶层组的优化任务完成后，需要顶层组的所有子组完成自己的优化，才能找到顶层组的最优方案，从而完成优化。

和 Volcano 优化器生成器一样，Cascades 从最上层的组开始优化过程，使用自顶向下的搜索策略。动态规划和 **memoization** 也用于优化组的任务。在对所有组的表达式进行初始优化之前，==先检查是否已经追求了相同的优化目标==；如果是，它只返回在前面的搜索中找到的计划。Cascades 和 Volcano 中的搜索策略之间的一个主要区别在于，Cascades 仅按需探索一组，而 Volcano 总是在实际优化阶段开始之前的第一个预优化阶段详尽地生成所有等效的逻辑表达式。在 Cascades 中，没有分成两个阶段。推导出所有表达式（例如谓词）的所有逻辑等价形式是没有用的。只在需要时使用转换规则探索组，并且只在组的所有成员匹配给定模式时才探索该组。由于它只探索真正有用的模式组，因此 Cascades 搜索策略更有效^12^。

> 12. 最坏的情况下 ， Cascades 彻底探索。 因此，在最坏的情况下，Cascades 搜索的效率将与 Volcano 搜索策略的效率相同。

与 Volcano 优化器生成器繁琐的用户接口相比，Cascades 在 DBI 和优化器之间提供了一个干净的数据结构抽象和接口。构成 Cascades 优化器和 DBI 之间接口的每个类都被设计成**子类层次结构的根**。优化器仅依赖于该接口中定义的方法； DBI 在定义子类时可以自由添加额外的方法。一些重要的接口包括运算符、成本模型和规则。 这个清晰的接口很重要，因为它使优化器更加健壮，并使 DBI 更容易实现或扩展优化器。

[Bil97] 描述了一个实验优化器 **Model D**，用于优化在 Cascades 优化器框架下开发的 TPC-D 查询 [TPC95]。**Model D** 有许多逻辑运算符，而这些逻辑运算符又需要许多规则和物理运算符。DBI 可以通过派生基类接口来定义新的操作符和规则，并很容易地将它们添加到优化器中。只需对 Cascades 搜索引擎进行少量更改，**Model D** 就展示了 Cascade 框架在关系模型中的可扩展性。

Cascades 只是一个优化器框架。 它提出了许多性能改进，但许多功能目前未使用或仅以基本形式提供。目前 Cascades 的设计和实现仍有许多改进的空间。优化器框架和 DBI 规范的强分离、虚方法的广泛使用、非常频繁的对象分配和释放都会导致性能问题。一些修剪技术可以应用于自上而下的优化，以显着提高搜索性能。 所有这些观察结果都激发了我们对 Cascades 的研究和开发一种新的、更有效的优化器——哥伦比亚优化器。

## Chapter 4 . Structure of the Columbia Optimizer

> Based on the Cascades framework, Columbia focuses on efficiency of optimization. This chapter will describe in detail the design and implementation of the Columbia optimizer. Comparison with Cascades will be discussed.

Columbia 基于 Cascades 框架，专注于优化器效率。本章将详细介绍 Columbia 优化器的设计和实现。与 Cascades 的比较也将讨论。

### 4.1 Overview of the Columbia Optimizer

> Figure 9 illustrates the interface of the Columbia optimizer. Columbia takes an initial query text file as input, uses a catalog and cost model information also written in text files provided by the DBI, and generates the optimal plan for the query as output.

图 9 说明了哥伦比亚优化器的界面。 Columbia 将初始查询文本文件作为输入，使用同样写入 DBI 提供的文本文件中的目录和成本模型信息，并为查询生成最佳计划作为输出。

### 4.2 The Search Engine

> Figure 12 illustrates the three important components of the Columbia search engine and their relationship. The search space is initialized by copying in the initial query expression. The goal of the optimizer is to expand the search space and find the optimal (i.e., least cost) plan from the final search space. In Columbia, the optimization process is controlled by a series of “tasks”. These tasks optimize groups and expressions in the search space, applying rules from the rule set, expanding the search space by generating new expressions and groups. After the optimization is completed (i.e., all tasks are scheduled), the optimal plan in the final search space is copied out as the output of the optimizer.

图 12 展示了 Columbia 搜索引擎的三个重要组件及其关系。通过复制初始查询表达式来初始化搜索空间。优化器的目标是扩展搜索空间，从最终的搜索空间中找到最优（即成本最低）的计划。在 Columbia 中，优化过程由一系列“任务”控制。这些任务优化搜索空间中的 Group 和表达式，通过应用规则集中的规则，生成新的表达式和 Group 来扩展搜索空间。优化完成后（即所有任务都已调度完成），最终搜索空间中的优化计划将作为优化器的输出被拷贝出来。

> - [ ] Figure 12. Main components in the Columbia Search Engine

#### 4.2.1 The Search Space

> This section will describe the structure of the Columbia search space. The components of the search space are groups. Each group contains one or more multiexpressions that are logically equivalent.

本节描述 Columbia **==搜索空间==**的结构。搜索空间的组件是<u>**组**</u>。每个<u>**组**</u>包含一个或多个逻辑上等价的<u>多重表达式</u>。

#####4.2.1.1 Search Space Structure - Class SSP

> We borrow the term Search Space from AI, where it is a tool for solving a problem. In query optimization, the problem is to find the cheapest plan for a givenquery, subject to a certain context. A Search Space typically consists of a collection of possible solutions to the problem and its sub problems. Dynamic Programming and <u>Memoization</u> are two approaches to using a Search Space to solve a problem. Both Dynamic Programming and Memoization partition the possible solutions by logical equivalence. We call each partition a **==GROUP==**. Hence, search space consists of a collection of groups.
>
> In Columbia, a structure similar to the Cascades’ MEMO structure is used to represent the search space, namely an instance of class SSP, which consists of an array of groups with a group ID identified as the root group in the search space. A group in the search space contains a collection of logically equivalent multi-expressions. As is introduced in section 2.4, a multi-expression consists of an operator and none or more groups as inputs. Hence, each group in the search space is either a root group or an input group to other group(s), i.e., from the root group, all other groups can be visited as descendants of the root group. That is why the root group must be identified. By copying in the initial query expression, The search space is initialized with several basic groups. Each basic group contains only one logical multi-expression. The further operation of the optimization will expand the search space by adding new multiexpressions and new groups into the search space. <u>The method “CopyIn” copies an expression to a multi-expression and includes the multi-expression into the search space</u>. It may either include the new multi-expression into an existing group which the multi-expression logically equivalently belongs to, or include the new multiexpression into a new group in which case the method is respondent to first create the new group and append it to the search space. The method “CopyOut” of the class SSP will output the optimal plan after the optimization is finished.

我们从 AI 借来<u>**搜索空间**</u>一词，它是解决问题的工具。查询优化是要根据**==特定上下文==**，找到<u>给定查询成本最低的计划</u>。搜索空间通常由<u>问题及其子问题</u>可能解决方案的集合组成。**动态规划**和**记忆化**是使用搜索空间解决问题的两种方法。 动态规划和<u>记忆化</u>都通过<u>逻辑等价</u>来划分可能的解决方案。 我们将每个这样的划分称为 **==GROUP==**。 因此，搜索空间由组的集合组成。

在 Columbia 中，类似于 Cascade 的 MEMO 的结构被用来表示搜索空间，即类 `SSP` 的一个实例，包含一个 **Group** 数组，Group ID 被标识为搜索空间中的 Root Group。搜索空间中的 **Group** 包含逻辑上等价的多个表达式。如第 2.4 节所述，这些表达式**==由==**一个运算符，以及一个或多个 Group 作为输入组成。因此，搜索空间中的每个组都是 Root Group，或其他 Group 的输入Group，即从 Root Group 开始，所有其他 Group 都可以作为 Root Group 后代来访问。这就是为什么必须标识 Root Group 的原因。通过复制初始查询表达式，搜索空间被初始化为几个基本 Group。每个基本组只包含一个逻辑多表达式。进一步的优化是通过在搜索空间中添加新的<u>==多表达式==</u>和新的组来扩展搜索空间。方法 `CopyIn` 将一个表达式复制到<u>==多表达式==</u>中，并将<u>==多表达式==</u>包含到搜索空间中。可以将新的<u>==多表达式==</u>包含在逻辑上等价的现有 Group 中，也可以将该新的<u>==多表达式==</u>包含在新的 Group 中，此时，将首先创建新的 Group ，再将其追加到搜索空间中。`SSP` 的 `CopyOut` 将输出优化后的计划。

##### 4.2.1.2 Duplicate Multi-expression Detection in the Search Space

> 搜索空间中检测重复的<u>多重表达式</u>

> One potential problem of including a multi-expression into the search space is that duplication may happen, i.e., there may be a multi-expression in the search space which is exactly the same as this multi-expression^13^. So, before the actual including operation, duplication of this multi-expression must be checked through the whole search space. If there is a duplicate, this multi-expression should not be added into the search space.

在搜索空间中包含<u>==多表达式==</u>的一个潜在问题是可能发生重复，即在搜索空间中可能存在与此<u>==多表达式==</u>完全相同的<u>==多表达式==</u>^13^。因此，在实际加入<u>==多表达式==</u>之前，必须在整个搜索空间中检查是否已存在该<u>==多表达式==</u>。如果已存在，则不应将此多表达式添加到搜索空间中。

> 13. Actually, duplication is unavoidable in rule-based optimizers. Duplicate identification is needed even in the presence of unique rule sets (discussed in section 4.2.2), for two reasons: (i). Unique rule sets generate side effects, e.g., rule (AB)C ->A(BC) has the group containing BC as a side effect. BC may already exist, although the expression A(BC) is guaranteed not to exist by the unique rule sets. (ii). Generalizations of unique rule sets, e.g., adding aggregate pushdown, may destroy uniqueness.

To check duplication, there are at least three algorithms:

1. some kind of tree-based search
2. extendible hashing
3. static hashing

Although there are some existing codes for algorithm 1 or 2, they are complicated and it is hard to say if they are efficient in this case. Alternative 3 is simple and easy to code, although there may be a problem when the number of multiexpressions grows exponentially. A hash table with a fixed number of buckets which is suitable for small queries will be fully filled with many entries per bucket when a large query is optimized since much more expressions are generated.

Both Cascades and Columbia use static hashing (alternative 3) to facilitate fast detection of duplicate multi-expressions. Hence the potential problem of fixed bucket size can not be avoided. The search space contains a static hash table. All the three components of a multi-expression, i.e., operator class name, operator parameters, and input group numbers are hashed into the hash table to check duplication. The major difference between Columbia and Cascades is that Columbia use an efficient hash function.

Instead of using a traditional hash function (randomizing then modulo a prime) as in Cascades, Columbia chose an efficient hash function “lookup2”, which is a modification of the original hash function LOOKUP2 written by Bob Jenkins. Jenkins [Bob97] claims LOOKUP2 is simple and very efficient compared to many traditional hash functions. Every bit of a hashed key are mixed up with bits of other three “magic” values by simple and fast operations, such as addition, subtraction and bit operations. Every bit of the key affects every bit of the return value. Another advantage of lookup2 is that the sizes of its hash tables are powers of two, which allows very fast^14^ modular operations to such hash table sizes. Instead, a traditional hash function requires a modular operation to a prime which is much slower than a modular operation to the power of two. Regarding the large number of hash operations, efficiency of the hash function is very important. Figure 13 shows the pseudo-code for the use of the function Lookup2. The return value of this pseudo-code serves as the hash value of the key.

> 14. The trick is using a bit-mask operation. For example, the module of a value to 2^n^ is equal to the value applying a bit-mask which masks off the higher bits expect the lower n bits of the value. Bit operation is much faster than any others.

> - [ ] Figure 13. Pseudo-code for the Use of lookup2()

Since duplication only happens for logical multi-expressions during the optimization (physical expressions are generated uniquely from logical expressions), all logical multi-expressions the optimizer generates are hashed to check the duplication when they are to be included into the search space. A multi-expression has three components: an operator class name, operator arguments and none or more input groups. To maximize the distribution of hash values, Columbia uses all of the three components as parameters of the key of a multi-expression. All of the three components are applied to the hash function successively: the operator class name is first hashed to a value which is used for the initial value to hash the operator parameters. This hash value is then in turn used as the initial value to hash the input groups. The final hash value yields the hash value for the multi-expression.

The method “FindDup()” of the class SSP implements duplicate detection. The hash table in the search space contains pointers to the logical multi-expressions in the search space. The FindDup method takes a multi-expression as parameter and returns the duplicate multi-expression in the search space if a duplicate is found. Here is the algorithm of FindDup: The hash value of the multi-expression is calculated, then the hash table is looked up to see whether there is a collision. If so, comparison of two multi-expressions is done by the order of simplicity, i.e., first compares arity of the operators, then input groups, finally parameters of the operators. If no duplicate is found, the new multi-expression is linked to the multi-expression with the same hash value. In the case of no collision in the hash table, the new multi-expression is added to the hash table, and no duplication is found.

Recalling that the number of multi-expressions in the search space is very large, this hash mechanism in Columbia enables simple and efficient duplicate elimination of multi-expressions in the whole search space.

##### 4.2.1.3 GROUP

> The class GROUP is central to top-down optimization. A group contains a collection of logically equivalent logical and physical multi-expressions. Since all of these multi-expressions have the same logical properties, the class GROUP also stores a pointer to the logical property these multi-expressions shared. For dynamic programming and <u>==memoization==</u>, a winner structure that records the optimal plans of the group is included. Beyond these basic elements of a group, Columbia improved upon this class to facilitate an efficient search strategy. Compared to Cascades, the improvement includes the addition of a lower bound member, the separation of physical and logical multi-expressions and a better structure for winners.
>
> **The Lower Bound of a Group**. A lower bound for a group is a value L such that every plan^15^ P in the group satisfies: cost(P) >= L. Lower bound is an important measure for top-down optimization where group pruning could happen when the lower bound of a group is greater than the current upper bound, i.e., cost limit for the current optimization. Group pruning can avoid enumeration of entire input groups without missing the optimal plan. Section 4.4.1 will discuss the details of group pruning in Columbia which is a main contribution to the efficiency of the Columbia optimizer. The lower bound of a group is calculated when the group is created and stored in the group to be used in future optimizing operations.
>
>  > 15. Actually, a plan in a group is derived from the physical multi-expressions explicitly stored in the group.
>
>  This section describes how the lower bound of a group is obtained in Columbia. **Obviously, a higher lower bound is better**. The goal is to find the highest possible lower bound according to the information we gathered from a group. When a group is constructed, the logical property is gathered, including the cardinality and the schema of the group, from which our lower bound is derived. <u>Since the lower bound is based only on the group’s logical property, it can be calculated without enumerating any expressions in the group</u>.
>
> Before the calculation of the lower bound is described, some definitions are presented:
>
> - `touchcopy()` is a function which returns a numeric value such that for any join the value is less than cost of a join divided by cardinality of the join output. This function represents the cost of touching the two tuples needed to make the output tuple, and the cost of copying the result out.
>- `Fetch()` is the amortized cost of fetching one byte from disk, assuming data is fetched in blocks.
> - |G| denotes the cardinality of group G.
> - Given a group G, we say that a base table A is in the schema of G if A.X is in the schema of G for some attribute X of A. <u>Then `cucard(A.X)` denotes the unique cardinality of a column A.X in G, `cucard(A)` in G denotes the maximum value of `cucard(A.X)` over all attributes A.X in the schema of G</u>. Without loss of generality we assume the base tables in the schema of G are A1, . . . , An, n >= 1, and cucard(A1) <= ... <= cucard(An).
> 
> The calculation of the lower bound of a group is shown in Figure 14.
>
> ```C
> // Figure 14. Pseudo-code for calculating lower bound of a group
> if G contains Get(A)
>   LowerBound = cost(FILE_SCAN(A)).
> Else
>   LowerBound = touchcopy() * |G|                                    // From top join
>                +
>                touchcopy() * sum (cucard(Ai) where i = 2, ..., n-1) // from other non-top joins 
>                +
>                Fetch() * sum ( cucard(Ai) where i = 1, ..., n)      // from leaves
>              
> /*
>    Note: For each Ai which has no index on a join (interesting) order, replace cucard(Ai) in the “from leaves” term above with |Ai| and it yields a better lower bound.
> */
> ```
>
> In Figure 14, we defined three kinds of lower bounds for a group. Detailed discussions are presented in the following paragraphs. These three kinds of lower bounds are independent, hence the sum of them provides the lower bound for a group^16^.
>
> > 16. There is one criticism which could be made of all these approaches: they depend on cardinality and cucard estimates, which are notoriously inaccurate. Cucard estimates are even worse than cardinality. Although there exist more accurate but sophisticated estimation methods, such as using histograms, Columbia uses simple estimates and allows further improvement in this case.
>
>  **(1)**. The touch-copy bound from top join of G. It is based on G’s cardinality since the set of tuples outputted by any plan in G is just the result output of the top join of the group. By the definition of touchcopy(), the cost of any join (including the copy-out cost) is at least touchcopy() times the cardinality of the resulting join.÷
>
> **(2)**. The touch-copy bound from the non-top joins of G. It is based on the unique cardinality of columns in G, i.e., the cucards of attributes in G’s schema. We can prove that this touch-copy bound is a lower bound of the non-top joins.
>
> ***Theorem***: A lower bound corresponding to non-top joins of G is given by `touchcopy() * sum(cucard(Ai) where i=2, …, n)`
>
> ***Motivation***: Think of the left deep plan with the Ai’s in order. The first join has A2 in its schema so a lower bound for the join of A1 and A2 is touchcopy()*C2 where C2=cucard(A2). Other joins Ai (i>2) have the same properties. So the sum of them yields the result of the theorem. The following lemma says this works for any ordering of the Ai and for any join graph, not only left deep.
>
> ***Lemma***: Let L be an operator tree such that schema(L) contains attributes from base tables A1, …, An. Let J be the set of joins in L and let A* be an arbitrary table in the schema of L. There is a map f: J -> schema(L) such that
>
> 1. the range of f is all of schema(L) except A*
>
> 2. for each j in J, f(j) is in the schema of j
>
> ***Proof of Lemma***: by induction on `k = size of schema(L)`. The case k = 2 is obvious. Induction step: Let L have k tables in its schema. Map the top join to any table on the side without A*. Induction succeeds on the two sub-trees since each of the sub-trees has less than k tables in its schema.
>
> ***Proof of theorem***: For any ordering of Ai and any join graph of the group G, there are n-1 joins in G. Let J be a set of joins of G and Ji (where i= 2, …, n) be a join of G. The schema of G contains attributes from base table A1, …, An. According to the lemma, there is a map from J to the schema of G, such that Ji (i=2, …, n) maps to Ai (i=2, …, n) respectively and Ai is in the schema of Ji. Thus, a lower bound for the join Ji is touchcopy() * Ci where Ci>=cucard(Ai). Hence, the sum of these lower bounds for join Ji (where I=2,…, n) is touchcopy() * sum ( cucard(Ai) where i=2, …, n ), which prove the theorem.
>
> **(3)**. The fetch bound from the leaves (base tables) of G. It is also based on the cucards of attributes in G’s schema, corresponding to the cost of fetching tuples from base tables. The reason why this fetch cost is a bound for G is:
>
> ***Theorem***: Suppose T.A is an attribute of a group G, where T is a range variable ranging over a base table which we also call T, and A is an attribute of the base table. Let c = the cucard of T.A. Then any plan in G must fetch (retrieve from disc) at least c tuples from A. Especially, if c is the max cucard value over all T.As in G, it yields a higher fetch cost.
>
> ***Proof***: Each relational operator preserves values of attributes (assuming the attributes are still in the output, e.g., are not projected out). Thus if there are two tuples in a plan from G with distinct T.A values, just descend the plan tree to T in order to find two tuples in T with these same T.A values.
>
> **Separation of logical and physical multi-expressions**. Cascades stored logical and physical multi-expressions in a single linked list. We store them in separate lists, which saves time in two cases.
>
> First, rule bindings take all logical multi-expressions as inputs to check if they match patterns, so we need not skip over physical multi-expressions. A group generally contains a huge number of logical and physical multi-expressions which may occupy several pages of virtual memory, so a single reference of physical multi-expressions may cause memory page fault which greatly slow down the program execution. Generally, the number of physical multi-expressions in a group is twice or three times as the number of logical multi-expressions. By separating logical and physical expressions and only looking at logical expressions, binding in Columbia should be faster than that in Cascades.
>
> Second, if a group has been optimized and we are optimizing it for a different property, we can handle the physical and logical multi-expressions in the group separately. The physical multi-expressions in the physical list are scanned only to check whether the desired property is satisfied and calculate costs directly, and the logical multi-expressions in the logical list are scanned only to see if all appropriate rules have been fired. Only when a rule has not been applied to an expression before, is the logical expression to be optimized. In Cascades, the task for optimizing a group does not look at the physical multi-expressions. Instead, all logical multi-expressions are to be optimized again. Obviously, the approach in Columbia to optimize a group is superior to that in Cascades, and is facilitated by the separation of logical and physical linked lists in a group.
>
> **Better Structure for Winners**. The key idea of dynamic programming and memoization is to save the winners of searches for future use. Each search for the cheapest solution to a problem or subproblem is done relative to some context. Here a context consists of required physical properties (e.g. the solution must be sorted on A.X ) and an upper bound (e.g. the solution must cost less than 5). A **WINNER** is the multi-expression (physical) which won the search for the context which guided a search. Since different search contexts may yield different winners for a group, an array of winner objects is stored into a group structure.
>
> In Cascades, a Winner class contains a pair consisting of a context which guided a search, and a multi-expression which is the winner of that search. A winner class in Cascades also contains a pointer to link to the next winner indicating that there may be another winner for this group for a different search context.
>
> In Columbia, a simplified structure is used to represent a winner. Without storing a context and a link to other winners, a winner class in Columbia consists of a multi-expression which won the search, the cost of the expression (i.e., the winner), and the required physical property of the search. **<u>A winner object in a group represents the result of one possible search for the group</u>**. Since a group contains an array of winners, there is no need to store a pointer to the next winner of the group. It is obvious that a winner structure in Columbia is simpler and smaller than that in Cascades.In Columbia, a winner is also used to store the temporary result of a search. While the costs of physical multi-expressions of a group are being calculated, <u>==the cheapest yet found expression==</u> is stored as a winner. During the optimization process, the winner is improved and finally the best (cheapest) plan is found. Sometimes, when no physical multi-expression can be found with the required physical property, we store the multi-expression pointer as NULL to indicate no winner for that physical property. Since no winner is also a solution for a search of this sub-problem, this information is memoized and will be useful in the future optimizing process. The following is the definition of data members in a WINNER class in Columbia:
>
>  ```c++
> Class WINNER { 
>   M_EXPR * MPlan;       // Which plan is the best so far? NULL means no
>                         // physical mexpr with this property found so far.
>   PHYS_PROP * PhysProp; // What property are we trying to obtain?
>   COST * Cost;          // cost of MPlan, Best cost so far.
> }
>  ```

`GROUP` 类是**自顶向下**优化的核心，是逻辑上等价的**逻辑和物理<u>多表达式</u>**的集合。 由于所有这些<u>多表达式</u>都具有相同的逻辑属性，因此 `GROUP` 还存储了指向这些<u>多表达式</u>共享的<u>逻辑属性</u>的指针。对于动态规划和缓存（<u>==memoization==</u>），包含了一个记录了组内最优计划的 `WINNER`。除了这些基本元素外，Columbia 还改进了 `GROUP`，使得搜索策略更加高效。与 Cascade 相比，该算法增加了一个下界成员，分离了物理表达式和逻辑<u>==多表达式==</u>，并为胜者提供了更好的结构。

**Group 的下界**。Group 的下界是一个值 L，Group 中的每个计划 P ^15^ 都满足：$cost(P) >= L$。下界是自上而下优化的重要措施，当 Group 的下界大于当前上界（即当前优化的成本限制）时，可能会裁剪该 Group ，它可以避免枚举整个输入 Group 而不会丢失最优方案。第 4.4.1 节将讨论在 Columbia 进行Group 裁剪的细节，这是 Columbia 优化器对提高效率的主要贡献。在创建 Group 并将其追加到搜索空间时，将计算 Group 的下界，以便后续优化时使用，

> 15. 实际上，Group 中的计划是从显式存储在 Group 中的物理**==多表达式==**<u>派生的</u>。

本节介绍如何在 Columbia 中计算 Group 的下界。**显然，下界越高越好**。我们的目标是根据我们从 Group 中收集到的信息找到最高的下界。构造 Group 时，将收集逻辑属性，包括基数和 Group 的 Schema，并从中计算出下界。**由于计算下界仅基于组的逻辑属性，因此可以在不枚举组中任何表达式的情况下进行计算**。

在介绍如何计算下界之前，先给出一些定义：

- 函数 `touchcopy()`  返回一个数值，对于任何联接，该值均小于联接成本除以联接的输出基数。该函数表示产生每条输出记录<u>访问</u>所需两个 tuple 的开销，以及复制结果的开销。

- `Fetch()` 是从磁盘中读取一个字节的<u>==均摊开销==</u>，假设数据是以块的形式读取的。

- $|G|$ 表示组 G 的基数。

- 给定一个组 G，如果基表 A 的某列 A.X 在 G 的 Schema 中，那么基表 A 就在 G 的 Schema 中。然后用 `cucard(A.X)` 表示 G 中 A.X 列的<u>**去重基数**</u>，G 中的 `cucard(A)` 表示 G 的 Schema 中，A中所有列 `cucard(A.X)` 的最大值。在不失一般性的前提下，我们假定 G 的 Schema 中基表为 A~1~，. . . ，A~n~，n> = 1，cucard(A~1~) <= ... <= cucard(A~n~)。

如何计算 Group 的下界，如图 14 所示：

```C
// Figure 14. 计算 Group 下界的伪码
if G contains Get(A)
  LowerBound = cost(FILE_SCAN(A)).
Else
  LowerBound = touchcopy() * |G|                                    // 来自于顶部联接
               +
               touchcopy() * sum (cucard(Ai) where i = 2, ..., n-1) // 来自于其他非顶部联接 
               +
               Fetch() * sum ( cucard(Ai) where i = 1, ..., n)      // 来自于叶节点
             
/*
注意：对于每个Ai，在联接顺序上没有的对应索引，将上面来自于叶节点中的 cucard(Ai）替换为 |A|, 以产生更好的下限。?
*/
```

图 14 中，我们为组定义了三种下界。下文将详细讨论。这三种下界相互独立，因此它们的总和提供了一个 Group 的下界^16^。

> 16. 对所有这些方法都有一种批评：它们依赖于基数和 `cucard` 估计，这是出了名的不准确。`cucard` 估计甚至比基数更糟糕。尽管存在更精确但更复杂的估计方法，例如使用直方图，但 Columbia 使用简单的估计，并允许在这种情况下进一步改进。

**（1）** G **顶部联接**的 <u>**touch-copy**</u> 界，基于 G 的基数，因为 G 的任何计划输出的元组集合是该组顶部联接的结果输出。根据 `touchcopy()` 的定义，任何联接的成本（包括复制成本）至少是 touchcopy() 乘以所得联接的基数。

**（2）** G **非顶部联接**的 <u>**touch-copy**</u> 界，基于 G 中列的唯一基数，即 G 的 Schema 中属性的 `curcard`。我们可以证明这个  <u>**touch-copy**</u> 界是**非顶部联接**的下界。

**定理**：对应于 G 的非顶部联接的下界由 `touchcopy() * sum(cucard(Ai) where i=2, …, n)` 给出

**动机**：按照 A~i~ 的顺序考虑左深计划树。第一个联接的 Schema 有 A~2~，因此 A~1~ 和 A~2~ 的联接下界是 `touchcopy() * C2`，其中 `C2 = cucard(A2)`。其他联接 A~i~（i> 2）的 join 具有相同的属性。因此，它们的总和就是定理的结果。下面的引理表明，这适用于各种联接序以及任何联接图，而不仅仅是左深联接。

**引理**：设 $L$ 是一个运算符树，使得 $schema(L)$ 包含来自基表A~1~，…，A~n~的列。设 $J$ 是 $L$ 中的**<u>==联接集==</u>**，设 A~*~ 是 $L$ 中任意一个基表。存在这样一个映射 $f$：$J \longleftrightarrow schema(L)$ ，这样：

1. $f$ 的范围是除 A~*~ 之外所有的 $schema(L)$
2. 对于 $J$ 中的每个 **j**，$f(j)$ 在 **j** 的 **schema** 中。

**引理证明**：使用归纳法，令 `k = size of schema(L)`。`k = 2` 时显然成立，归纳步骤：让 L 在其 Schema 中有 k 个表。将<u>**顶部联接**</u>映射到不带 A~*~ 的一侧。归纳在两个子树上成功，因为每个子树在其 Schema 中表少于 k 个。

**定理证明**：对于 A~i~ 的任意排序和 Group 中 G 的任何联接图，G 中都有 n-1个联接。设 J 是 G 的一组连接，J~i~（i=2，…，n）是 G 的一个联接。G 的 Schema 包含基表A~1~，…，A~n~中的属性。根据引理，存在一个从 J 到 G 的 Schema 的映射，使得J~i~（i=2，…，n）分别映射到 A~i~（i=2，…，n），A~i~ 在 J~i~ 的 Schema 中。所以，`touchcopy()*Ci` 是联接 J~i~ 的下界，其中`Ci >= cucard(Ai)` 。因此， `touchcopy() * sum(cucard(Ai) i = 2，…，n)`  是联接 J~i~（ i = 2，…，n）的下界之和，这证明了定理。

**（3）** 读取 G 中的叶子节点（基表）的下界，也基于 G 的 Schema 中列的 `cucard`，对应于从基表获取元组的开销。读取开销是 G 的一个界的原因是：

**定理**：假设 T.A 是 G 的一个属性，其中 T 是基表（也称为T）上的范围变量，而 A 是基表的列。令 *c* 为 T.A 的 `cucard`，那么，G 中的任何<u>**==计划==**</u>都必须从 A 中获取至少 *c* 个元组（从磁盘中检索）。特别地，如果 *c* 是 T上所有列最大的 `cucard` 值，则 G 有更高的读取成本。

**证明**：每个关系运算符保留<u>==属性==</u>的值（假设**<u>==属性==</u>**仍在输出中，比如，还未被投影出来）。因此，如果 G 的计划中两个具有不同 T.A 值的元组，则只需将<u>==计划树==</u>下推到 T，即可在 T 中找到这两个元组。

**逻辑和物理多表达式分离**。Cascade 将逻辑和物理<u>==多表达式==</u>存储在一个链表中。我们将它们分别存储在不同的链表中，这在两种情况下节省了时间。

首先，**规则绑定**将所有逻辑<u>**多表达式**</u>作为输入来检查它们是否与模式匹配，因此我们不必跳过物理<u>多表达式</u>。一个 Group 通常包含大量逻辑和物理<u>==多表达式==</u>，可能占用好几页的虚拟内存，因此，物理<u>==多表达式==</u>的单个引用可能会导致内存页面错误，从而大大降低程序执行速度。通常，Group 中物理多表达式的数量是逻辑多表达式数量的两到三倍。通过分离逻辑表达式和物理表达式并仅查看逻辑表达式，Columbia 中的绑定应该比 Cascades 中的绑定更快。

其次，如果已经优化过一个 Group，并且我们针对不同的属性正在对其进行优化，那么我们可以分别处理该 Group 中的物理和逻辑多表达式。只扫描物理列表中的物理<u>==多表达式==</u>，以检查是否满足所需属性并直接计算成本，只扫描逻辑列表中的逻辑<u>==多表达式==</u>，以查看是否已触发所有适当的规则。只有当规则以前没有应用于<u>表达式</u>时，才需要优化逻辑表达式。在 Cascades 中，优化 Group 的<u>==任务==</u>不会查看物理<u>**多表达式**</u>。相反，将再次优化所有逻辑多表达式。显然，Columbia 优化 Group 的方法优于 Cascades，并且通过将逻辑链表和物理链表分开，简化了该方法。

**胜者的数据结构更优**。动态规划和<u>**记忆**</u>的关键思想是缓存胜者的信息，以便将来使用。针对每个问题或子问题，搜索其最佳的解决方案都是相对于某些上下文进行。这里，上下文由所需的物理属性（例如，结果集必须按 A.X 排序）和上界（例如，执行计划的成本必须小于5）组成。**胜者**是一个（物理）多表达式，在某个上下文的搜索中获胜。由于不同的搜索上下文可能会为一个 Group 产生不同的赢家，所以 Group 中存储的是赢家对象的数组。

Cascade 中，`Winner` 类包含一个 `Pair`，由引导搜索的上下文和该上下文中胜者的<u>==多表达式==</u>组成。Cascades 的 `Winner` 类还包含指向下一个 `Winner` 的指针，该指针表示对于不同的搜索上下文，该 Group 可能还有另一个 `Winner`。

Columbia 采用简化的结构代表获胜者。在不存储上下文和与其他获胜者的链接的情况下，Columbia 的 `Winner` 类由赢得搜索的多表达式、表达式（即胜者）的执行成本、以及搜索所需的物理属性组成。<u>**Group 中的胜者对象表示一次可能搜索该 Group 的结果**</u>。因为 Group 包含一个胜者数组，所以不需要存储指向该组下一个胜者的指针。显然，Columbia 胜者的结构比 Cascade 的结构更简单、更小。Columbia 中的胜者也被用来存储搜索的临时结果。计算 Group 中物理<u>==多表达式==</u>的成本时， 成本最低的表达式被存为胜者。在优化过程中，不断降低胜者的成本，最终找到最优（成本最低的）方案。有时，当找不到具有所需物理属性的物理<u>多表达式</u>时，我们将多表达式指针存储为 `NULL`，以指示该物理属性没有胜者。因为没有胜者也是搜索此子问题的解决方案，因此该信息将被保存，并在后续的优化过程中有用。以下是 Columbia 中 `WINNER` 类数据成员的定义：

 ```c++
Class WINNER { 
  M_EXPR * MPlan;       // 到目前为止，哪个计划最好？NULL 表明尚未找到具有此属性的物理 mexpr
  PHYS_PROP * PhysProp; // 需要的属性
  COST * Cost;          // MPlan 的成本，目前为止最低的成本。
}
 ```

##### 4.2.1.4 Expressions

> There are two kinds of expression objects: `EXPR` and `M_EXPR`. An `EXPR` object corresponds to an expression in query optimization, which represents a query or a sub-query in the optimizer. An EXPR object is modeled as an operator with arguments (class OP), plus pointers to input expressions (class EXPR). For convenience, it retains the operator's arity. EXPRs are used to represent the initial and final query and are involved in the definitions and bindings of rules.
>
> An M_EXPR implements a multi-expression. It is a compact form of EXPR which utilizes sharing. An M_EXPR is modeled as an operator with arguments plus pointers to input GROUPs instead of EXPRs, so an M_EXPR embodies several EXPRs. M_EXPRs are the main component of groups and all searching is done over M_EXPRs. So there must be some state associated with M_EXPRs. Table 3 shows the definition of data members in the class M_EXPR and Table 4 shows the definition of corresponding class EXPR_LIST implementing multi-expressions in Cascades.
>
> ```c++
> // Table 3. Data Member Definition of class M_EXPR in Columbia
> Class M_EXPR {
>   private:
>     OP*         Op;        // Operator
>     GRP_ID*     Inputs;    // input groups
>     GRP_ID      GrpID;     // I reside in this group
>     M_EXPR*     NextMExpr; // link to the next mexpr in the same group
>     M_EXPR *    HashPtr;   // list within hash bucket
>     BIT_VECTOR  RuleMask;  // If the index bit is on, do not fire rule with that index
> }
> ```
>
> ```c++
> // Table 4. Data Member Definition of class EXPR_LIST in Cascades
> class EXPR_LIST {
>   private:
>     OP_ARG*       op_arg;         // operator
>     GROUP_NO*     input_group_no; // input groups
>     GROUP_NO      group_no;       // I reside in this group
>     EXPR_LIST*    group_next;     // list within group
>     EXPR_LIST*    bucket_next;    // list within hash bucket
>     BIT_VECTOR    dont_fire;      //If the index bit is on, do not fire rule with that index
>     int           arity;          // cache arity of the operator
>     int           task_no;        //Task that created me, for book keeping
>     PROPERTY_SET* phys_prop;      // phys props of the mexpr if it is physical
>     COST*         cost;           // cost of the mexpr if it is physical
> }
> ```
>
> Table 3 and 4 illustrate the two class implementations of multi-expressions in Columbia and Cascades. We can see that comparing to the corresponding class EXPR_LIST in Cascades, class M_EXPR has fewer data members. The extra data members in EXPR_LIST are not needed in M_EXPR: The arity of the mexpr can be gotten from the operator. There is no need to keep track of the tasks which created the mexpr and store the physical properties and cost of the physical mexpr because they are no longer used anywhere once they are calculated and the decision is made. Since multi-expressions occupy the main part of the search space memory, it is very critical to make this data structure as succinct as possible. For example, an M_EXPR object takes 24 bytes of memory while an EXPR_LIST object takes 40 bytes of memory. The memory usage ratio between class EXPR_LIST and M_EXPR is about 1.67 : 1. If the initial query is a join of 10 tables, there are at least 57k logical multi-expressions according to Table 1 shown in Section 2.5. In Columbia these logical multi-expression may take up to 24*57k = 1368k bytes of memory. In Cascades, they may take up to 40*57k = 2280k bytes of memory. So this succinct data structure in Columbia causes a big saving in memory.
>

表达式对象有两种：`EXPR` 和 `M_EXPR`。 `EXPR` 对象对应于查询优化中的**表达式**，代表优化器中的**查询**或**子查询**。 `EXPR` 对象被建模为<u>带参数的运算符</u>（`OP` 类），**且**含有指向<u>输入表达式</u>（`EXPR` 类）的指针。为方便起见，它保留了运算符参数的个数。 `EXPR` 用于表示初始查询和最终查询，并参与规则的定义和绑定。

`M_EXPR` 实现了<u>==多表达式==</u>，是 `EXPR` 的一种紧凑形式，利用了共享。M_EXPR 被建模为带参数的运算符，**且**含有指向输入 `GROUP` 的指针（不是指向 `EXPR` 的指针），因此 `M_EXPR` 包含了多个 `EXPR`。`M_EXPR` 是 **Group** 的主要组成部分，所有搜索都在 `M_EXPR` 上完成。因此，必须有一些与 `M_EXPR` 相关的状态。表 3 是 `M_EXPR` 类数据成员的定义，表 4 是 Cascade 中对应实现<u>==多表达式==</u>的类 `EXPR_LIST` 的定义。

```c++
// Table 3. Columbia 中 M_EXPR 类数据成员的定义
Class M_EXPR {
  private:
    OP*         Op;        // 运算符
    GRP_ID*     Inputs;    // 输入 groups
    GRP_ID      GrpID;     // 这个 mexpr 所在的 group
    M_EXPR*     NextMExpr; // 链接到同一 group 内的下一个 mexpr
    M_EXPR *    HashPtr;   // 哈希桶中的链表
    BIT_VECTOR  RuleMask;  // 如果索引位打开，则不要使用该索引触发规则
}
```

```c++
// Table 4. Cascades 中 EXPR_LIST 类数据成员的定义
class EXPR_LIST {
  private:
    OP_ARG*       op_arg;         // 运算符
    GROUP_NO*     input_group_no; // 输入 groups
    GROUP_NO      group_no;       // 这个 mexpr 所在的 group
    EXPR_LIST*    group_next;     // group 内的链表
    EXPR_LIST*    bucket_next;    // 哈希桶中的链表
    BIT_VECTOR    dont_fire;      // 如果索引位打开，则不要使用该索引触发规则
    int           arity;          // 运算符的参数个数
    int           task_no;        // 创建改 mexpr 的任务，用于簿记
    PROPERTY_SET* phys_prop;      // 如果是物理多表达式，这是其属性
    COST*         cost;           // 如果是物理多表达式，这是其成本
}
```

表 3 和表 4 是 Columbia 和 Cascade 中<u>多表达式</u>的两个实现类。可以看到，与 Cascade 中的对应的 `EXPR_LIST` 类相比，`M_EXPR` 的数据成员更少。`EXPR_LIST` 中额外的数据成员在 `M_EXPR` 中不需要：运算符参数的个数可以从运算符那获得。无需跟踪创建 `mexpr` 的任务，也无需存储物理多表达式的物理属性和成本，因为一旦计算出它们并做出决策，就没有地方再用到它们。由于<u>==多表达式==</u>占据了搜索空间内存的主要部分，因此该数据结构越简洁越好。例如，一个 `M_EXPR` 对象24个字节，而一个 `EXPR_LIST` 对象40个字节。类 `EXPR_LIST` 和 `M_EXPR` 之间的内存使用率约为 `1.67:1`。如果初始查询是 10 个表的联接，那么根据 2.5 节中的表 1，至少有 57k 个逻辑多表达式。这些逻辑多表达式在 Columbia 中，可能占用多达 `24 * 57k=1368k` 字节的内存。在 Cascade 中，它们可能占用多达`40 * 57k=2280k` 字节的内存。因此，Columbia 这种简洁的数据结构大大节省了内存。

#### 4.2.2 Rules

> Rules by which the optimizing search is guided are defined in a **rule set** which is independent of the search structure and algorithm. The rule set can be modified independently by adding or removing some rules. Appendix C shows a simple rule set used for optimizing simple join queries.
>
> All rules are instances of the class RULE, which provides for rule name, an antecedent (the “pattern”), and a consequent (the “substitute”). Pattern and substitute are represented as expressions (EXPR objects) which contain leaf operators. A leaf operator is a special operator only used in rules. It has no input and is a leaf in a pattern or substitute expression tree. During the matching of a rule, a leaf operator node of the pattern matches any sub-tree. For example, the Left To Right (LTOR) join associative rule has these member data, in which L(i) stands for Leaf operator i:
>
> 1. Pattern: ( L(1) join L(2) ) join L(3)
>
> 2. Substitute: L(1) join ( L(2) join L(3) )
>
>
> The pattern and substitute describe how to produce new multi-expressions in the search space. The production of these new multi-expressions is done by `APPLY_RULE::perform()`, in two parts: First a BINDERY object produces a binding of the pattern to an EXPR in the search space; Then `RULE::next_substitute()` produces the new expression, which is integrated into the search space by `SSP::copy_in()`.
>
> There are other methods in the class `RULE` to facilitate the operations of rules. The method `top_match()` checks whether the top operator of a rule matches the top operator of the current expression wanted to apply the rule. This top matching is done before the actual binding of the rule, hence eliminates a lot of obviously non-match expressions.
>
> The method `promise()` is used to decide the order in which rules are applied, or even do not apply the rule. The promise() method returns <u>**a promise value**</u> of the rule according to the optimizing context, e.g., the required physical properties we are considering. So it is a run time value and informs the optimizer how useful the rule might be. A promise value of 0 or less means not to schedule this rule here. Higher promise values mean schedule this rule earlier. By default, an implementation rule has a promise of 2 and others a promise of 1, indicating implementation rules are always scheduled earlier. This rule scheduling mechanism allows the optimizer to control search order and benefit from it by scheduling rules to obtain searching bounds as quick as possible, as low as possible.
>
> Columbia inherited the basic design of rule mechanism from Cascades but made several improvements, including the binding algorithm and the handling of **<u>enforcers</u>**. The following sections will discuss these improvements in detail.

**规则集**中定义了引导优化搜索的**<u>规则</u>**，**规则集**与搜索结构和算法无关。通过添加或删除一些规则，可以独立地修改规则集。附录 C 是一个简单规则集，用于优化简单的联接查询。

所有规则都是 `RULE` 类的实例，它提供了<u>**规则名称**</u>，一个<u>**模式**</u>和一个<u>**替换项**</u>。 <u>**模式**</u>和<u>**替换项**</u>表示为包含<u>叶运算符</u>的表达式（`EXPR` 对象）。叶运算符是只在规则中使用的**特殊运算符**。它没有输入，是**模式**或**替换项**<u>表达式树</u>中的叶节点。在匹配规则期间，模式的<u>叶运算符节点</u>会**匹配**<u>任何子树</u>。 如，从左到右（`LTOR`）联接规则具有以下成员数据，其中 $L(i)$ 表示叶运算符 $i$：

1. 模式：$( L(1) \Join L(2) ) \Join L(3)$

2. 替换：$L(1) \Join ( L(2) \Join L(3) )$

模式和替代描述了如何在搜索空间中产生新的多表达式。这些新的多表达式由 `APPLY_RULE::perform()` 分为两步生成：首先，一个 `BINDERY` 对象在搜索空间中将模式绑定到 `EXPR`。然后 `RULE::next_substitute()`  产生新的表达式，该表达式通过 `SSP::copy_in()` 集成到搜索空间中。

`RULE` 类中还有其他方法可以方便规则的操作。`top_match()` 检查规则的顶层的运算符，是否与<u>要应用规则的当前表达式</u>的顶层运算符匹配。顶层匹配是在规则的实际绑定之前完成的，因此消除了许多明显不匹配的表达式。

方法 `promise()` 用于确定应用规则的顺序，或者不应用规则。`promise()`  会根据优化的上下文（例如所需的物理属性）返回规则的<u>**承诺**</u>值。因此，它是一个运行时值，并通知优化器该规则可能多有用。该值等于或小于 0 意味着不在此处应用此规则。较高的**<u>承诺</u>**值意味着可以更早地应用此规则。默认情况下，<u>**==实现规则==**</u>的承诺值为 2，其他规则的承诺值为 1，表示总是更早应用<u>**==实现规则==**</u>。这种规则调度机制使得优化器可以控制搜索顺序，并通过规则调度，以尽可能快的速度、尽可能低的成本获得搜索边界，从而从中获益。

Columbia 从 Cascade 继承了规则机制的基本设计，但做了一些改进，包括绑定算法和**<u>强制规则</u>**的处理。以下各节将详细讨论这些改进。

##### 4.2.2.1 Rule Binding

> All rule-based optimizers must bind patterns to expressions in the search space. For example, consider the LTOR join associative rule, which includes these two member data. Here L(i) stands for the LEAF_OP with index i:
>
> Pattern: (L(1) join L(2)) join L(3)
>
> Substitute: L(1) join (L(2) join L(3))
>
> Each time the optimizer applies this rule, it must bind the pattern to an expression in the search space. A sample binding is to the expression
>
> $(G_7 \Join G_4 ) \Join G_{10}$
>
>  where G~i~ is the group with GROUP_NO i.
>
> A **BINDERY** object (a bindery) performs the nontrivial task of identifying all bindings for a given pattern. A BINDERY object will, over its lifetime, produce all such bindings. In order to produce a binding, a bindery must spawn one bindery for each input subgroup. For instance, consider a bindery for the LTOR associativity rule. It will spawn a bindery for the left input, which will seek all bindings to the pattern L(1) join L(2) and a bindery for the right input, which will seek all bindings for the pattern L(3). The right bindery will find only one binding, to the entire right input group. The left bindery will typically find many bindings, one per join in the left input group.
>
> `BINDERY` objects (binderies) are of two types: expression bindery and group bindery. Expression binderies bind the pattern to only one multi-expression in a group. An expression bindery is used by a rule in the top group, to bind a single expression. Group binderies, which are spawned for use in input groups, bind to all multi-expressions in a group. Because Columbia and its predecessors apply rules only to logical multi-expressions, binderies bind logical operators only.
>
> Because of the huge number of multi-expressions in the search space, rule binding is a time-consuming task. In fact, in Cascades, the function BINDERY::advance() which finds a binding is the most expensive among all the functions in the optimizer system. Any improvement on the algorithm of rule binding will surely result in the performance improvement of the optimizer. Columbia refined the BINDERY class and binding algorithm to make rule binding more efficient.
>
> Since a bindery may bind several EXPRs in the search space, it will go through several stages, basically they are: start, then loop over several valid bindings, then finish. In Columbia, these stages are represented by three binding states, each of which is a value of an enum type BINDERY_STATE. It is shown in the following C++ type definition:
>
> ```c++
> typedef enum BINDERY_STATE { 
>   start,         // This is a new MExpression
>   valid_binding, // A binding was found.
>   finished,      // Finished with this expression
> } BINDERY_STATE;
> ```
>
> In Cascades, the binding algorithm used more states to keep track of all the binding stages, hence complicating the algorithm and consuming more CPU time. In Cascades, the binding stages are represented by six binding states. The following is the Cascades version of the binding state definition:
>
> ```c++
> typedef enum BINDING_STATE {
>   start_group,      // newly created for an entire group
>   start_expr,       // newly created for a single expression
>   valid_binding,    // last binding was succeeded
>   almost_exhausted, // last binding succeeded, but no further ones
>   finished,         // iteration through bindings is exhausted
>   expr_finished     // current expr is finished; in advance() only
> } BINDING_STATE;
> ```
>
> In Columbia, the binding algorithm is implemented in the function `BINDERY::advance()`, which is called by `APPLY_RULE::perform()`. The binding function walks the many trees embedded in the search space structure in order to find possible bindings. The walking is done with a finite state machine, as shown in Figure 15.
>
> ```c
> /*Figure 15. Finite State Machine for BINDERY::advance()*/
> 
> State start:
>   If the pattern is a leaf, we are done.
>     State = finished
>     Return TRUE
>   Skip over non-logical, non-matching expressions.
>     State = finished
>     break
>   Create a group bindery for each input and try to create a binding for each input.
>   If successful
>     State = valid_binding
>     Return TRUE
>   else
>     delete input binderys
>     State = finished
>     Break
> 
> State valid_binding:
>     Increment input bindings in right-to-left order.
>     If we found a next binding,
>       State = valid_binding
>       return TRUE
>     else
>       delete input binderys
>       state = finished
>       break
> 
> State finished:
>   If pattern is a leaf OR         // second time through, so we are done     
>      this is an expr bindery OR   // we finished the first expression, so done     
>      there is no next expression
>      return FALSE
>   else
>     state = start
>   break
> ```
> Since the finite state machine only has three states, the algorithm in Columbia obtains its simplicity and efficiency compared to the more complex finite state machine in Cascades with six states. Moreover, as mentioned in section 4.2.1.3, the separation of logical and physical multi-expressions into two link lists in Columbia made the binding much faster because there is no need to skip over all the physical expressions in the group.

所有基于规则的优化器都必须将模式绑定到搜索空间中的表达式。 例如，联接规则 `LTOR` 包括两个成员数据。这里$L(i)$ 代表索引为 *i* 的 `LEAF_OP`：

1. 模式：$( L(1) \Join L(2) ) \Join L(3)$
2. 替换：$L(1) \Join ( L(2) \Join L(3) )$

每次优化器应用此规则时，都必须将模式绑定到搜索空间中的表达式。 绑定表达式的例子为：

$(G_7 \Join G_4 ) \Join G_{10}$

其中 G~i~ 是 `GROUP_NO`  为 $i$ 的 Group。

`BINDERY` 对象的重要任务是**识别**<u>给定模式所有绑定</u>，将在其生命周期内产生所有此类绑定。为了产生绑定，必须为每个输入子 Group 生成一个 `BINDERY` 实例。

例如，考虑联接规则 `LTOR`  的 bindery。它将为左输入生成一个 bindery，该 bindery 将查找模式 $L(1) \Join L(2)$ 的所有绑定，并为右输入生成一个 bindery，该 bindery 将查找模式 L(3) 的所有绑定。右侧的 bindery 只会找到整个右输入组的一个绑定。左侧的 bindery 通常会找到许多绑定，左输入组中每个联接一个绑定。

`BINDERY` 对象有两种类型：表达式 `BINDERY` 和 Group `BINDERY`。表达式 `BINDERY`  只将模式绑定到 Group 中的一个<u>==多表达式==</u>。<u>顶层 Group 中的规则使用表达式 `BINDERY` 来绑定单个表达式</u>。为每个输入 Group 生成一个 Group `BINDERY`，用于绑定 Group 中所有的<u>==多表达式==</u>。因为 Columbia 及其前身只对逻辑多表达式应用规则，所以 `BINDERY` 只绑定逻辑运算符。

由于搜索空间中有大量的<u>==多表达式==</u>，规则绑定是一项耗时的任务。实际上，查找绑定的函数 `BINDERY::advance()` 是 Cascade 优化器所有函数中最耗时的。对规则绑定算法的任何改进都必然导致优化器性能的提高。Columbia 改进了 `BINDERY` 类及其绑定算法，使规则绑定更加高效。

由于一个 `BINDERY` 可以在搜索空间中绑定多个 `EXPR`，它将经历几个阶段，基本上是：**开始**，然后在几个有效绑定中**循环**，最后**结束**。在 Columbia 中，这些阶段由三个绑定状态表示，每个绑定状态对应枚举类型 `BINDERY_STATE` 。其 C ++ 类型定义如下：

```c++
typedef enum BINDERY_STATE { 
  start,         // 新的 MExpression
  valid_binding, // 发现一个有效的绑定
  finished,      // 完成此表达式
} BINDERY_STATE;
```

Cascade 中，绑定算法使用更多状态来跟踪所有绑定阶段，使的算法更加复杂，并消耗更多的CPU时间。Cascade 的绑定阶段由 6 个绑定状态表示。Cascade 的绑定状态定义如下：

```c++
typedef enum BINDING_STATE {
  start_group,      // 为整个 Group 新建
  start_expr,       // 为单个表达式新建
  valid_binding,    // 上次绑定成功
  almost_exhausted, // 上次绑定成功，没有其他绑定了
  finished,         // 绑定的迭代已完成
  expr_finished     // 当前表达式已完成；仅限于 advance()
} BINDING_STATE;
```
Columbia 中的绑定算法在函数 `BINDERY::advance()` 中实现，该函数由 `APPLY_RULE::perform()` 调用。绑定函数**遍历**包含在搜索空间中的查询树，以找到可能的绑定。遍历是通过有限状态机完成的，如图 15 所示。

```c
/*Figure 15. BINDERY::advance() 的有限状态自动机 */
State start:
  If the pattern is a leaf, we are done.
    State = finished
    Return TRUE
  Skip over non-logical, non-matching expressions.
    State = finished
    break
  Create a group bindery for each input and try to create a binding for each input.
  If successful
    State = valid_binding
    Return TRUE
  else
    delete input binderys
    State = finished
    Break

State valid_binding:
    Increment input bindings in right-to-left order.
    If we found a next binding,
      State = valid_binding
      return TRUE
    else
      delete input binderys
      state = finished
      break

State finished:
  If pattern is a leaf OR         // 第二次通过，所以完成了 
     this is an expr bindery OR   // 我们完成了第一个表达式，所以完成了
     there is no next expression
     return FALSE
  else
    state = start
  break
```
由于有限状态机只有三个状态，因此与有 6 个状态的 Cascade 中更复杂的有限状态机相比，Columbia 的算法具有简单、高效的特点。此外，如第4.2.1.3节所述，Columbia 将逻辑和物理多表达式分离为两个链接列表，因为不需要跳过组中的所有物理表达式，所以绑定速度更快。

##### 4.2.2.2 Enforcer Rule

An enforcer rule is a special kind of rule that inserts physical operators that enforce or guarantee desired physical properties. The physical operator inserted by an enforcer rule is called an enforcer. Typically, an enforcer takes a group as input and outputs the same group but with a different physical property. For instance, the QSORT physical operator is an enforcer, which implements the QSORT algorithm over a collection of tuples represented by a group in the search space. The rule SORT_RULE is an enforcer rule, which inserts the QSORT operator into the substitute. It can be represented as:

Pattern: L(1) Substitute: QSORT L(1) Where L(i) stands for the LEAF_OP with index i.

An enforcer rule is fired when and only when a search context requires a sorted physical property. For example, when a merge-join is being optimized, the search context for its inputs has a required physical property which requires the input is sorted on the merge-join attributes. Consider the multi-expression

MERGE_JOIN(A.X, B.X), G1, G2.

When we are optimizing this multi-expression using the top-down approach, the inputs are to be optimized first with certain contexts. For the left input group G1, the required physical property in the searching context is sorted on A.X, while the right input group G2 will have a required physical property sorted on B.X. When the searching requires a sorted property, the SORT_RULE is fired to insert the QSORT operator to force the input groups to have the required properties.

It is similar with other enforcer rules, for example, HASH_RULE, which enforces a hashed physical property. Whether an enforcer rule is fired or not is determined by the promise() method in the rule object. The promise() method returns a positive promise value if and only if the search context has a required physical property, for example, sorted or hashed. If there is no required physical property, a zero promise value is returned indicating that the enforcer rule will not be fired.

There are two differences in the handling of enforcer rules between Cascades and Columbia.

**First, excluded property**. Cascades used excluded properties in the promise() function to determine the promise value of an enforcer. When both the required physical property set and excluded physical property set are not empty, the promise() function return a non-zero promise value. The purpose of using an excluded property is to avoid repeatedly applying an enforcer rule for a group. But those excluded properties are difficult to track and use more memory (it requires that a search context include a pointer to an excluded property), and also make the search algorithm complicated to handle enforcers. Instead, Columbia does not use excluded properties at all. A context only includes a required property and an upper bound. The promise() function determines a rule’s promise only by the required physical property. To avoid the potential problem of repeatedly applying an enforcer rule, the unique rule set technique is applied to enforcer rules. That is, the RuleMask data member in each M_EXPR has a bit for each enforcer rule. When an enforcer rule has been fired, the bit associated to this rule is set to on, which means the enforcer rule has been fired for this multi-expression. The other time the enforcer rule is to be fired, the rule mask bit is checked and the rule will not be fired repeatedly. On the other hand, this simple approach raises a potential problem: if a group has been optimized and we are optimizing it for a different property, an enforcer rule bit in a multi-expression may have been set to on because of the last optimization. In this new optimization phrase, the enforcer rule will not have a chance to be fired for the different property, even it has a very good promise for this new physical property. Thus, the optimizer may give a wrong answer for this optimization phrase. The solution to this problem yields another improvement of Columbia over Cascades. It is discussed in the following paragraph as the second difference than Cascades.

**Second, representation of enforcers**. In Cascades, an enforcer is represented as a physical operator with some parameters. For example, a QSORT operator has two parameters: one is the attributes needed to sort on, the other is the sorting order (ascending or descending). The method QSORT::input_reqd_prop() returns no required physical property and a sorted excluded property for inputs. It provides the searching contexts for inputs when optimizing a multi-expression downward. An enforcer is actually generated by an enforcer rule. After an enforcer rule is successfully bound to an expression, method RULE::next_substitute() is invoked to produce a new expression where the enforcer is inserted. The parameters of the enforcer are produced according to the required physical properties of the searching context. For example, if the search context has a required physical property of being sorted on attributes <A.X, A.Y>, the enforcer generated will be a QSORT with parameter <A.X,A.Y>, denoted as QSORT(<A.X,A.Y>). This new expression with the enforcer will be included into the same group as the “before” expression in the search space. Since enforcers have parameters, enforcers with the same name but different parameters are treated as different enforcers. We can see from this that if the searches come with many different required physical properties, such as sorted on different attributes, there may be many enforcers with the same name but different parameters in a group in the search space. This could be a potential waste.

In Columbia, instead, an enforcer is represented as a physical operator without any parameter. For example, a QSORT enforcer is denoted as QSORT(), which does not contain any parameter. Only one QSORT operator will be generated and included into a group during the whole optimizing process because after the first SORT_RULE is fired, the corresponding rule bit in the expression is set to on and prevents the future SORT_RULE applications. This approach is safe because we assume that sorting of an input stream costs same regardless of sort keys. Here is how the Columbia optimizer works: If a group has been optimized for a property, an enforcer multi-expression has been added into the group. Now that we are optimizing it for a different property, then the same enforcer will not be generated because the corresponding rule bit has been set. Thus the enforcer rule will not be fired. On the other hand, all the physical multiexpressions in the group (including the enforcer multi-expression) will be checked to see whether the desired property is satisfied and costs will be calculated directly under the new context with the new required physical property. Since the enforcer has no parameter, it satisfies the new physical property and hence the cost of this enforcer multi-expression will be calculated out under the new physical property. If an enforcer multi-expression becomes the winner for a physical property, it and the physical property are stored in the winner structure just like the normal multi-expression winners.

When the optimizer is going to copy out the optimal plan, the enforcer winners need a special treatment which is to append the parameters to them according to the corresponding required physical properties, since the actual enforcer implementation requires parameters. For example, suppose the enforcer multi-expression “QSORT(), G1” is a winner for the physical property “sorted on A.X”. When we copy out this winner, the actual plan is “QSORT(A.X), G1” which appends the actual parameter to the enforcer.

#### 4.2.3 Tasks -- Searching Algorithm

> A **task** is an activity within the search process. The original task is to optimize the entire query. Tasks create and schedule each other; when no undone tasks remain, optimization terminates. Each task is associated with a certain context and has a method `perform`() which actually performs the task. Class `TASK` is an abstract class from which specific tasks are inherited. It contains a pointer to the context, the parent tasks number which creates this task, and a pure virtual function “perform()” needed to be implemented in the subclasses. Class `PTASKS` contains a collection of undone tasks needed to be scheduled and performed. PTASKS is currently implemented as a stack structure which has method “pop()” to remove a task for performing and method “push()” to store a task into the stack structure. A PTASKS object “PTasks” is created at the beginning of the optimization and the original task of optimizing the top group is pushed into PTasks. When optimization begins, the original task is popped out and the perform() method in the task is invoked to begin the actual optimization. The optimization will create follow-up tasks which will be pushed into PTasks for further scheduling. Figure 16 shows the pseudo-code for the main optimization process. By using the abstract class, we can see that a simple and clean programming structure is achieved.
>
> ```c++
> // Figure 16. Main Loop of Optimization in Columbia
> optimize() {
>   // start optimization with top group
>   PTasks.push ( new O_GROUP ( TopGroup ) );
>   // main loop of optimization
>   // while there are tasks undone, do one
>   while (! PTasks.empty ()) {
>     TASK * NextTask = PTasks.pop (); // get the next task
>     NextTask -> perform (); // perform it
>   }
>   // optimization completed, copy out the best plan
>   Ssp.CopyOut() ;
> }
> ```
>
> The whole search algorithm is performed by all the specific tasks in the optimizer. The tasks in Columbia are: group optimization (O_GROUP), group exploration (E_GROUP), expression optimization (O_EXPR), input optimization (O_INPUTS), rule application (APPLY_RULE). Figure 17 shows the relationship between these tasks. Arrows indicate which type of task schedules (invokes) which other type. The remainder of this section will describe the Columbia implementation of each task in detail. In the description of each task, a comparison with Cascades is discussed.
>
> > - [ ] Figure 17. Relationship between Tasks
>

**任务**是搜索过程中的活动。原始任务是优化整个查询。任务相互创建和调度；当没有未完成的剩余任务时，优化将结束。每个任务都与特定的上下文相关联，并有一个 `perform()` 方法，实际执行任务。类 `TASK` 是一个抽象类，特定的任务从该类继承。它包含一个指向上下文的指针、创建此任务的父任务编号，及需要在子类中实现的纯虚拟函数 `perform()`。类 `PTASKS` 包含<u>需要调度执行的未完成任务</u>的集合。 `PTASKS` 目前被实现为一个堆栈，其中 `pop()` 弹出要执行的任务，`push()` 用于将任务存储到堆栈中。优化开始时创建 `PTASKS` 对象 `PTasks` ，将优化顶层 Group 的原始任务推入 `PTASKS`。优化开始后，会弹出原始任务，并调用任务的 `perform()` 方法开始实际的优化。优化将创建后续任务，这些任务将被 `push` 到 `PTasks` 中以便进一步调度。图 16 显示了主要优化过程的伪代码。通过使用抽象类，我们实现了一个简单而干净的编程结构。

```c++
// Figure 16. Columbia 优化的主循环
optimize() {
  // 从顶层 Group 开始优化
  PTasks.push ( new O_GROUP ( TopGroup ) );
  // 优化的主循环
  // 当有任务未完成时，继续做
  while (! PTasks.empty ()) {
      TASK * NextTask = PTasks.pop (); // 获取下一个任务
    NextTask -> perform (); // 执行之
  }
  // 优化完成，复制出最佳方案
  Ssp.CopyOut() ;
}
```

整个搜索算法由优化器中的所有特定任务执行。 Columbia 中的任务包括：Group 优化（**O_GROUP**），Group 探索（**E_GROUP**），表达式优化（**O_EXPR**），输入优化（**O_INPUTS**），规则应用（**APPLY_RULE**）。图 17 显示了这些任务之间的关系。箭头指示哪种类型的任务调度（调用）哪种类型的其他任务。本节的其余部分将详细描述 Columbia 中如何实现每个任务。描述每个任务时，比较了 Cascades 的实现。

> - [ ] Figure 17. Relationship between Tasks

##### 4.2.3.1 O_GROUP - Task to Optimize a Group

This task finds the cheapest plan in this group, for a given set of contexts, and stores it (with the contexts) in the group's winner structure. If there is no cheapest plan (e.g. the upper bound cannot be met), the context with a null plan is stored in the winner structure. This task generates all relevant logical and physical expressions in the group, costs all the physical expressions and chooses the cheapest one. Two other types of tasks are created by O_GROUP task to generate and optimize the expressions in a group: O_EXPR and O_INPUTS.

Dynamic programming and memoization are used in this task. Before initiating optimization of all of a groups’ expressions, it checks whether the same optimization goal (i.e., same searching context) has been pursued already; if so, it simply returns the plan found in the earlier search. Reusing plans derived earlier is the crucial aspect of dynamic programming and memoization.

Figure 18 illustrates the process in O_GROUP task. It is implemented by `O_GROUP::perform()` method.

```c++
/* Figure 18. Algorithm for O_GROUP */

//find the best plan for a group with certain context
O_GROUP::perform( context ) {
  If ( lower bound of the group greater than upper bound in the context)
    return; // impossible goal
If ( there is a winner for the context )
  Return; // done, no further optimization needed

  // else, more search needed
  // optimize all the logical mexprs with the same context
  For ( each logical log_mexpr in the group )
    PTasks.push (new O_EXPR( log_mexpr, context ) );

  // cost all the physical mexprs with the same context
  For ( each physical phys_mexpr in the group )
    PTasks.push ( new O_INPUTS( phys_mexpr , context ) ) ;
}
/* Note: Since the tasks are pushed into a stack, O_INPUTS tasks are actually scheduled earlier than O_EXPR tasks. It is desired because a winner may be produced earlier. */
```

As seen in figure 18, the separation of logical and physical multi-expressions in a group facilitates the search in this task. There are two cases for performing a O_GROUP task.

**First**, the first time optimizing a group (i.e., searching a group for a context): In this case, only one logical mexpr (the initial mexpr) is in the group. By this algorithm, only one task, O_EXPR the intial mexpr, is created and pushed into the task stack, which will generate other expressions by applying rules.

The **second** case occurs when optimizing a group under a different context, e.g., a different required physical property: In this case, the group has been optimized and may have some winners. So there may be more than one logical and physical multi-expression in the group. Two things are needed: 1. We need to perform O_EXPR on each logical multi-expression with the new context. Because under the new context, some rules in the rule set that can not be applied to a mexpr become applicable. Due to the unique rule set technique, we will not fire the same rule twice, thus avoiding duplicate multi-expressions generated into the group; 2. We need to perform O_INPUTS on each physical mexpr with the new context to calculate the cost of the physical mexpr and produce a winner for the context if possible.

In Cascades, the task of optimizing a group did not deal with physical multiexpressions. For all the logical multi-expressions in a group, the task creates and pushes the O_EXPR task for each logical multi-expression. Then all the physical multi-expressions will be generated and the costs are calculated. In the case of optimizing a group the second time, all physical multi-expressions would be generated again for cost calculations under a different context. And because all logical and physical multi-expressions are stored in one linked list, this method must skip over all the physical multi-expressions in a group. From this comparison, the algorithm of optimizing a group in Columbia is more efficient than that in Cascades.

---

对于给定的一组上下文，该任务在该组中查找成本最低的计划，并将其（与上下文一起）存储在组的胜者结构中。如果没有成本最低的计划（例如无法满足上限），那么胜者结构中存储的上下文的计划为空。该任务将在组中生成所有相关的逻辑和物理表达式，计算所有物理表达式的开销，并选择最便宜的一个。`O_GROUP` 创建两种其他类型的任务：`O_EXPR` 和 `O_INPUTS` 来生成和优化 **==Group==** 中的表达式。

这个任务使用动态规划和<u>==**缓存**==</u>。在优化 Group 中所有的表达式之前，它先检查是否已经搜索了相同的优化目标（即，相同的搜索上下文）； 如果是这样，只需要简单地返回先前搜索中找到的计划。重用早先找到的计划是动态编程的关键。

图 18 描述了 `O_GROUP` 任务。 它由 `O_GROUP :: perform()` 实现。

> - [ ] 图 18

如图 18 所示，Group 中逻辑和物理多表达式的分离有助于此任务中的搜索。执行 `O_GROUP` 任务有两种情况。

**首先**，第一次优化 Group（即在 Group 中搜索上下文）：在这种情况下，Group 中只有一个逻辑 **mexpr**（初始 mexpr）。该算法只创建一个任务 `O_EXPR`，即初始 **mexpr**，并将其推入任务栈，`O_EXPR` 将通过应用规则生成其他表达式。

**第二种**情况是在<u>**不同的上下文**</u>下（例如，所需物理属性不同）优化 Group 时：在这种情况下，已优化了 Group，可能有一些胜者。

因此，组中可能有多个逻辑和物理多表达式。需要两件事：1。我们需要用新的上下文对每个逻辑多表达式执行O\ EXPR。因为在新的上下文中，规则集中不能应用于mexpr的一些规则变得适用。由于独特的规则集技术，我们不会两次触发同一个规则，从而避免在组中生成重复的多个表达式；2。我们需要使用新的上下文对每个物理mexpr执行O\u输入，以计算物理mexpr的成本，并在可能的情况下为上下文生成赢家。

因此，该组中可能存在多个逻辑和物理多重表达式。 需要做两件事：1.我们需要在具有新上下文的每个逻辑多重表达式上执行O_EXPR。 因为在新的上下文中，规则集中的某些规则不能应用到mexpr上，因此变得适用。 由于采用了独特的规则集技术，因此我们不会两次触发同一条规则，从而避免了在组中生成重复的多重表达式； 2.我们需要使用新的上下文在每个物理mexpr上执行O_INPUTS，以计算物理mexpr的成本，并在可能的情况下为该上下文产生赢家。

##### 4.2.3.2 E_GROUP - Task to expand the group

Some rules require that their inputs contain particular target operators, typically logical operators. For example, a join associativity rule requires that one of the inputs must be a join. Consider the left associativity rule of a join. When the join operator in a multi-expression matches the top join operator in the rule, the left input group of the multi-expression must be expanded to see if there is any join in the group since the rule requires that the left input must be a join. And all the joins in the group should match the left join in the rule.

An E_GROUP task expands a group by creating all target logical operators that could belong to the group, e.g., fire whatever rules are necessary to create all joins that could belong to a join group. This task is only invoked on demand when an exploration is required by a rule according to the rule’s pattern. It is created and scheduled by O_EXPR task when necessary.

Figure 19 shows the process of exploring a group. It is implemented by the E_GROUP::perform() method.

```c++
// Figure 19. Algorithm for E_GROUP

// derive all logical multi-expression for matching a pattern
E_GROUP::perform(context)
{
If ( the group has been explored before)
Return;
// else, the group has not yet been explored
for ( each log_mexpr in the group )
PTasks.push (new O_EXPR( log_mexpr, context, exploring ) );
Mark the group explored;
}
```

Dynamic programming is also used here to avoid duplicate work. Before exploring a group’s expressions, the task checks whether the group has already been explored. If so, the task terminates immediately without spawning other tasks. When exploration is needed, the task invokes an O_EXPR task for each logical multi-expression with “exploring” flag to inform the O_EXPR task to explore the expression, i.e., only fire transformation rule for the expression.

In Cascades, An E_GROUP task spawns another type of task, E_EXPR, to explore a multi-expression. Since E_EXPR has similar actions with O_EXPR, Columbia does not have an E_EXPR task. Instead, Columbia simply reuses O_EXPR with a flag indicating the purpose of the task.

##### 4.2.3.3 O_EXPR - Task to optimize a multi-expression

There are two purposes for an O_EXPR task in Columbia: One is to optimize a multi-expression. This task fires all rules in the rule set for the multi-expression, in order of promise. In this task, transformation rules are fired to expand the expression, generating new logical multi-expressions; while implementation rules are fired to generate corresponding physical multi-expressions. The other purpose for O_EXPR is to explore a multi-expression to prepare for rule matching. In this case, only transformation rules are fired and only new logical multi-expressions are generated. There is a flag associated with an O_EXPR task, indicating the purpose for the task. Be default, the flag is set to “optimizing”, since O_EXPR task is mostly used for optimizing an expression. If the task is used for exploring, especially spawned by E_GROUP task, the flag is set to “exploring”.

Figure 20 shows the algorithm for task O_EXPR, which is implemented in the method O_EXPR::perform(). In O_EXPR::perform(), the optimizer decides which rules are pushed onto the PTASK stack. Notice that a rule's promise is evaluated before inputs of the top-most operator are expanded and matched against the rule's pattern while promise values are used to decide whether or not to expand those inputs.

```c++
/* Figure 20. Algorithm for O_EXPR */
// optimize or explore a multi-expression, firing all appropriate rules.
O_EXPR::perform( mexpr, context , exploring ) {
  // Identify valid and promising rules
  For (each rule in the rule set) {
    // check rule bit in mexpr
    if ( rule has been fired for mexpr ) continue;
  
    // only fire transformation rules for exploring
    if (exploring && rule is implementation rule ) continue;
    
    // check top operator and promise
    if (top_match(rule, mexpr) && promise(rule,context) > 0 )
      store the rule with the promise;
  }
  
  sort the rules in order of promises;
  
  // fire the rules in order
  for ( each rule in order of promise) {
    // apply the rule
    PTasks.push ( new APPLY_RULE ( rule, mexpr, context, exploring ) );
    
    // explore the input group if necessary
    for (each input of the rule pattern )
    {
      if ( arity of input > 0 )
         PTasks.push ( new E_GROUP( input grp_no, context) );
    }
  }
}
```

There is no major difference between the algorithm of optimizing a multiexpression in Columbia and in Cascades, except Columbia reused O_EXPR task to explore a multi-expression. In Cascades, a new type of task, “E_EXPR”, is used for exploring.

##### 4.2.3.4 APPLY_RULE - Task to Apply a Rule to a Multi-Expression

There is no difference between the algorithm of applying a rule in Columbia and Cascades. A rule is only applied to logical expressions. APPLY_RULE is the task to apply a rule to a logical multi-expression and generate new logical or physical multi-expressions into the search space. Given a rule and a logical multi-expression, the task determines all sensible pattern bindings to expressions currently available in the search space, then applies the rule and includes new substitute expressions into the search space. The new generated multi-expressions will then be optimized for further transformations if it is a logical multi-expression, or be calculated its cost if it is a physical multi-expression.

Figure 21 shows the algorithm for task APPLY_RULE, which is implemented by the method APPLY_RULE::perform(). This algorithm in Columbia is the same as that in Cascades.

```c++
/* Figure 21. Algorithm for APPLY_RULE */

// apply a transformation or implementation rule
APPLY_RULE::perform( mexpr, rule, context, exploring ) {
  // check rule bit in mexpr
  if ( rule has been fired for mexpr ) return;
  
  for (each binding for the mexpr and rule) {
    before = binding->extract_expr(); // get the binding expression
    
    if ( rule->condition(before) not satisfied ) continue; // check condition
    
    after = rule->next_substitute(expr ); // get the substitute from the rule
    
    new_mexpr = Ssp->CopyIn(after); // include the substitute into SSP
    
    // further transformations to optimize new expr
    if ( new_mexpr is logical )
      PTasks.push (new O_EXPR (new_mexpr, context, exploring));
    
    // calculate the cost of the new physical mexpr
    if ( new_mexpr is physical )
      PTasks.push (new O_INPUTS (new_mexpr, context));
  }
  mexpr->set_rule_bit(rule); // mark the rule has been fired
}
```

After a binding is found, the method RULE::condition() is invoked to determine whether the rule can actually apply to the expression. For example, a rule that pushes a select below a join requires a condition about compatibility of schemas. This condition can not be checked until after the binding, since schemas of input groups are only available after the binding.

After the rule is applied for the multi-expression, the corresponding rule bit in the multi-expression must be set so that the other time the same rule will not be applied again to this multi-expression and hence duplicate work is avoided.

##### 4.2.3.5 O_INPUTS - Task to optimize inputs and derive cost of an expression

After an implementation rule has been applied during optimization, i.e., an implementation algorithm has been considered for one node in a query tree, optimization continues by optimizing each input to the implementation algorithm. The goal of task O_INPUTS is to compute the cost of a physical multi-expression. It first computes the costs of the multi-expression’s inputs then adds them up to the cost of the top operator. Member data input_no in class O_INPUTS, initially 0, indicates which input has been costed. This task is unique to other tasks in that it does not terminate after scheduling other tasks. It first pushes itself onto the stack, then schedules the optimization of the next input. When all inputs are costed, it calculates the cost of the entire physical multi-expression.

This task is the major task for the implementation of Columbia’s pruning techniques which are discussed in detail in section 4.3. Based on the same task in Cascades, O_INPUTS in Columbia re-engineers the algorithm and adds pruning related logic to implement the pruning techniques that are new in Columbia.

Figure 22 illustrates the pseudo-code of method O_INPUTS_perform(), which implements the algorithm of task O_INPUTS.

> **NOTATION**:
>
> - **G**: the group being optimized.
> - **IG**: various inputs to expressions in G.
> - **GLB**: the Group Lower Bound of an input group, stored as a data member of the group.
> - **Full winner**: a winner whose plan is non-null.
> - **InputCost**[]: contains actual (or lower bound) costs of optimal inputs to G.
> - **LocalCost**: cost of the top operator being optimized.
> - **CostSoFar**: LocalCost + sum of all InputCost[] entries.

```c
/* Figure 22 Pseudo-code of O_INPUTS::perform() */

// On the first (and no other) execution, the code initializes O_INPUTS member InputCost.
For each input group IG
  If (Starburst case) InputCost is zero;
  Determine property required of search in IG;
  If (no such property) terminate this task.;
  Get Winner for IG with that property;
  If (the Winner from IG is a Full winner) InputCost[IG] = cost of that winner;
  else if (!CuCardPruning) InputCost[IG] = 0 //Group Pruning case
    else if (no Winner) InputCost[IG] = GLB //remainder is Lower Bound Pruning case
      else // Winner has a null plan, find a lower bound for IG
        InputCost[IG] = max(cost of winner, IG Lower Bound)
EndFor // initialize InputCost

//The rest of the code should be executed on every execution of this method.
If (Pruning && CostSoFar >= upper bound) 
  terminate this task. // Note1: group pruning applied
        
//Calculate cost of remaining inputs
For each remaining (from InputNo to arity) input group IG;
  Probe IG to see if there is a winner;
    If (there is a Full Winner in IG)
      store its cost in InputCost;
      if (Pruning && CostSoFar exceeds G's context's upper bound) 
        terminate this task;
    else If (we did not just return from O_GROUP on IG)
      //optimize this input; seek a winner for it
      push this task;
      push O_GROUP for IG;
      return;
    else // we just returned from O_GROUP on IG, this is an impossible plan
      if(There is a winner in IG with a null plan)
         If appropriate, update null-plan winner in IG;
         terminate this task;
      else // There is no winner in IG
        Create a new null-plan winner in IG;
        terminate this task;
EndFor //calculate the cost of remaining inputs

//Now all inputs have been optimized
if (arity==0 and required property can not be satisfied) terminate this task;
if (CostSoFar >= than G's context's upper bound) terminate this task;

//Now we know current expression satisfies current context
if (GlobepsPruning && CostSoFar <= GLOBAL_EPS) // Note2: global epsilon pruning applied
  Make current expression a winner for G;
  mark the current context as done;
  terminate this task;

if (either there is no winner in G or CostSoFar is cheaper than the cost of the winner in G)
  //so the expression being optimized is a new winner
  Make the expression being optimized a new winner
  update the upperbound of the current context
  return;
```

There are three pruning flags in the algorithm: Pruning, CuCardPruning and GlobepsPruning. Users of the optimizer can set these flags accordingly to experiment different pruning techniques in Columbia.

There are four cases on which we can run benchmarks for Columbia. The O_INPUTS algorithm handles these four cases with different logic. In Cascades, only case 1 and 2 are addressed.

1. Starburst - [!Pruning && !CuCardPruning] : no pruning applied, generates all expressions for input groups, i.e., expands input groups thoroughly.

2. Simple Pruning - [Pruning && !CuCardPruning] : tries to avoid input group expansions by aggressively checking limits at all times, i.e., if CostSoFar during optimization of the expression is greater than the upper bound of the optimizing context, the task is terminated with no further optimization. In this case, InputCost[] entries only store the winner costs of inputs, if the input has a winner for the optimizing context.

3. Lower Bound Pruning - [CuCardPruning] : tries to avoid input group expansions as much as possible. The difference between this and simple pruning is: if there is no winner for an input group, it stores the input group's GLB in the InputCost[] entry. This case assumes that the Pruning flag is on, i.e., the code forces Pruning flag to be true if CuCardPruning is true.

4. Global Epsilon Pruning - [GlobepsPruning] : If a plan costs less than the global epsilon value (GLOBAL_EPS), it is considered as a final winner for G, hence no further optimization is needed (i.e., the task is terminated). This flag is independent of others. It  can be combined with the other three cases in an optimization.

### 4.3 Pruning Techniques