# Implementing Data Cubes Efficiently

## 1. Introduction

### 1.1 The Data Cube

...

We use the TPC-D database of size lGB as a running example throughout this paper. For more details on this benchmark refer to [Raa95].

We have only discussed the presentation of the data set as a multi-dimensional data cube to the user. The following implementation alternatives are possible:

1. Physically materialize the whole data cube. This approach gives the best query response time. However, precomputing and storing every cell is not a feasible alternative for large data cubes, as the space consumed becomes excessive. <u>It should be noted that the space consumed by the data cube is also a good indicator of the time it takes to create the data cube, which is important in many applications. The space consumed also impacts indexing and so adds to the overall cost</u>.

   > 应该注意的是，数据立方体所占用的空间也很好地指示了创建数据立方体所需的时间，这在许多应用程序中都很重要。消耗的空间也会影响构建索引的时间，因此会增加总成本。

2. Materialize nothing. In this case we need to go to the raw data and compute every cell on request. This approach punts the problem of quick query response to the database system where the raw data is stored. No extra space beyond that for the raw data is required.

3. **Materialize only part of the data cube**. We consider this approach in this paper. <u>In a data cube, the values of many cells are computable from those of other cells in the data cube. This dependency is similar to a spreadsheet where the value of cells can be expressed as **a function of the values of other cells**. ==We call such cells “dependent” cells==</u>. For instance, in Example 1.1, we can compute the value of cell (p, ALL, c) as the sum of the values of cells of (p, s~l~, c))..., (P,S~Nsupplier~, c), where N~supplier~ is the number of suppliers. The more cells we materialize, the better query performance is. For large data cubes however, we may be able to materialize only a small fraction of the cells of the data cube, due to space and other constraints. It is thus important that we pick the right cells to materialize.

   > 在数据立方体中，许多单元格的值可从数据立方体中其他单元格的值计算而来。这种依赖关系类似于电子表格，某些单元格的值可以表示为**其他单元格值的函数**。==我们称这种单元格为**从属单元格**==。

**Any cell that has an `ALL` value as one of the components of its <u>address</u> is a dependent cell.** The value of this cell is computable from those of other cells in the data cube. If a cell has no `ALL`s in its components, its value cannot be computed from those of other cells, and we must query the raw data to compute its value. The number of cells with `ALL` as one of their components is usually a large fraction of the total number of cells in the data cube. The problem of which dependent cells of to materialize, is a very real one. For example, in the TPC-D database (Example 1.1), seventy percent of all the cells in the data cube are dependent.

>  这家的 address 没理解，应该就是 group-by-keys

There is also the issue of where the materialized data cube is stored: in a relational system or a proprietary MDDB (multi-dimensional database) system. In this paper, we assume that the data cube is stored in “summary” tables in a relational system. Sets of cells of the data cube are assigned to different  tables.

The cells of the data cube are organized into different sets based on the positions of `ALL` in their addresses. Thus, for example, all cells whose addresses match the address `(-, ALL,-)` are placed in the same set. Here, “`-`” is a placeholder that matches any value but “ `ALL`”. Each of these sets corresponds to a different SQL query. The values in the set of `(-, ALL,-)` is output by the SQL query:

```SQL
SELECT Part, Customer, SUM(SP) AS Sales
FROM R
GROUP BY Part, Customer;
```
Here, R refers to the raw-data relation. The queries corresponding to the different sets of cells, differ only in the `GROUP-BY` clause. In general, attributes with `ALL` values in the description of the set of cells, do not appear in the `GROUP-BY` clause of the SQL query above. For example, supplier has an `ALL` value in the set description `(-, ALL,-)`. Hence it does not appear in the `GROUP-BY` clause of the SQL query. Since the SQL queries of the various sets of cells differ only in the grouping attributes, we use the grouping attributes to identify queries uniquely.

**Deciding which sets of cells to materialize is equivalent to deciding which of the corresponding SQL queries (views) to materialize**. In the rest of this paper we thus work with views rather than with sets of cells.

> 确定要物化的单元格集合等同于确定要物化哪一组相应的SQL查询（视图）

### 1.2 Motivating Example

...

There are some interesting questions we can now ask:

1. How many views must we materialize to get reasonable performance?
2. Given that we have space S, what views do we materialize so that we minimize average query cost?

In this paper, we provide algorithms that help us answer the above questions and provide near optimal results.

In the above example, a fully materialized data cube would have all the views materialized and thus have slightly more than 19 million rows.

1. Now let us see if we can do better. To avoid going to the raw data, we need to materialize the view grouping by `part`, `supplier`, and `customer` (view 1), since that view cannot be constructed from any of the other views. 
2. Now consider the view grouping by `part` and `customer` (view 2). Answering any query using this view will require us to process 6 million rows. 
3. The same query can always be answered using the view grouping by `part`, `supplier`, and `customer`, which again requires processing of 6 million rows. 
4. Thus there is no advantage to materializing the view grouping by `part` and `customer`. By similar reasoning, there is no advantage materializing the view grouping by `supplier` and `customer` (view 4). 
5. Thus we can get almost the same average query cost using only 7 million rows, an improvement of more than 60% in terms of space consumed and thus in the cost, of creating the data cube.

Thus by cleverly choosing what parts of the data cube to materialize, we can reap dramatic benefits.

> 因此，通过巧妙地选择要物化那部分数据立方体，可以获得显著的好处。

### 1.3 Related Work

> 1. The annalysis in [GBLP95], assumes that every Possible cell of the data cube exists. However, in many cases, data cubes are sparse: only a small fraction of all possible cells are present. <u>In such cases, the size of the data cube can be much larger than the corresponding `GROUP-BY`. In fact, the sparser the data cube, the larger is the ratio of the size of the data cube to the size of the corresponding `GROUP-BY`.</u>
>
>    >在这种情况下，数据立方体的大小可以远远大于相应的 `GROUP-BY`。事实上，数据立方体越稀疏，数据立方体的大小与相应 `GROUP-BY` 大小的比率就越大。

### 1.4 Paper Organization

## 2. The Lattice Framework

**In this section we develop the notation for describing when one data-cube query can be answered using the results of another.** We denote a view or a query (which is the same thing) by giving its grouping attributes inside **parenthesis**. For example the query with grouping attributes part and customer is denoted by (part, customer). In Section 1.2 we saw that views defined by supersets can be used to answer queries involving subsets.

> **在本节中，我们将开发一种表示法，用于描述何时可以用一个数据立方体回答另一个数据立方体的查询**。我们通过在**括号**内给出其分组属性来表示一个**视图**或**查询**（这是同一件事）。例如，具有分组属性 `part` 和 `customer` 的查询由 `(part，customer)` 表示。在1.2节中，我们看到由超集定义的视图可以用来回答涉及子集的查询。

### 2.1 The Dependence Relation on Queries

We may generalize the observations of Section 1.2 as follows. Consider two queries Q1 and Q2. We say $Q1 \preceq Q2$ if Q1 can be answered using only the results of Q2. We then say that Q1 is dependent on Q2. For example, in Section 1.2, the query `(part)`, can be answered using only the results of the query `(part, customer)`. Thus `(part )` $\preceq$ `(part, customer)`. <u>There are certain queries that are not comparable with each other using the</u> $\preceq$ <u>operator</u>. For example: `(part)` $\npreceq$ `(customer)` and `(customer)` $\npreceq$ `(part)`. 

> 有些查询无法使用 $\preceq$ 运算符相互比较。
>
>  这里 $\preceq$ 数学符合的含有是**先于等于**

The $\preceq$ operator imposes a **==partial ordering==** on the queries. <u>We shall talk about the views of a data-cube problem as forming a lattice</u>. In order to be a lattice, any two elements (views or queries) must have **a least upper bound** and **a greatest lower bound** according to the $\preceq$ ordering. However, in practice, we only need the assumptions that $\preceq$ is a partial order, and that there is a *top* element, a view upon which every view is dependent.

> 如何物化数据立方体视图的问题转换为构建 **lattice**。
>
> 为了成为一个 **lattice**，任何两个元素（视图或查询）都必须（根据 $\preceq$ 排序），以形成**最小上界**和**最大下界**。然而，在实践中，我们只需假设 $\preceq$ 是一个偏序，并且存在一个 *top* 元素，每个 **view** 都依赖于这个**元素**。

### 2.2 Lattice Notation
We denote a lattice with set of elements (queries or views in this paper) ***L*** and dependence relation $\preceq$ by (***L***, $\preceq$). For elements a, b of the lattice, b is an ancestor of a, **if and only if** a $\preceq$ b. It is common to represent a lattice by a *lattice diagram*, a graph in which the lattice elements are nodes, and <u>there is a path downward from b to a if and only if</u> a $\preceq$ b. The <u>hypercube</u> of Fig. 1 is the lattice diagram of the set of views discussed in Section 1.2.

> - [ ] hypercube
>
> **当且仅当 a $\preceq$ b时，才存在从 b 到 a 的向下路径**，就是从细粒度往粗粒度

### 2.3 Hierarchies
In most real-life applications. dimensions of a data cube consist of more than one attribute, and the dimensions are organized as hierarchies of these attributes. A simple example is organizing the time dimension into the hierarchy: day, month, and year. Hierarchies are very important, as they underlie two very commonly used querying operations: “drilldown” and “roll-up.” Drill-down is the process of viewing data at progressively more detailed levels. For example. a user drills down by first looking at the total sales per year and then total sales per month and finally, sales on a given day. Roll-up is just the opposite: it is the process of viewing data in progressively less detail. In roll-up, a user starts with total sales on a given day, then looks at the total sales in that month and finally the total sales in that year.

In the presence of hierarchies, the dependency lattice (***L***, $\preceq$) is more complex than a hypercube lattice. For example, consider a query that groups on the time dimension and no other. When we use the time hierarchy given earlier, we have the following three queries possible: `(day)`, `(month)`, `(year)`, each of which groups at a different granularity of the time dimension. Further, `(year)` $\preceq$ `(month)` $\preceq$ `(day)`. In other words, if we have total sales grouped by month, for example, we can use the results to compute the total sales grouped by year. **Hierarchies introduce query dependencies that we must account for when determining what queries to materialize**.

**To make things more complex, hierarchies often are not total orders but partial orders on the attributes that make up a dimension.** Consider the time dimension with the hierarchy `day`, `week`, `month`, and `year`. Since months and years cannot be divided evenly into weeks, if we do the grouping by `week` we cannot determine the grouping by month or year. In other words: `(month)` $\npreceq$ `(week)`, `(week)` $\npreceq$ `(month)`, and similarly for `week` and `year`. When we include the **none** view corresponding to no time grouping at all, we get the lattice for the time dimension shown in the diagram of Fig. 2.

### 2.4 Composite Lattices for Multiple, Hierarchical Dimensions
**We are faced with query dependencies of two types**: query dependencies caused by the interaction of the different dimensions with one another (the example in Section 1.2 and the corresponding 1attice in Fig. 1 is an example of this sort of dependency) and query dependencies within a dimension caused by attribute hierarchies.

> 面对两种类型的查询依赖：
>
> 1. 不同维度之间的交互导致的查询依赖关系
> 2. 维度层级

If we are allowed to create views that independently group by any or no member of the hierarchy for each of n dimensions, then we can represent each view by an n-tuple (a~l~, a~2~, . . , a~n~), where each a, is a point in the hierarchy for the **i**th dimension. This lattice is called the *direct product* of the dimensional lattices. We directly get a $\preceq$ operator for these views by the rule

​				(a~l~, a~2~, . . , a~n~) $\preceq$ (b~l~, b~2~, . . , b~n~) if and only if a~l~ $\preceq$ b~1~

We illustrate the building of this *direct product* lattice in the presence of hierarchies using an example based on the TPC-D benchmark.

- [ ] **Example 2.2**  ...

The lattice framework, we present and advocate in this paper, is advantageous for several reasons:

1. It provides a clean framework to reason with dimensional hierarchies, since hierarchies are themselves lattices. As can be seen in Fig. 4, the direct-product lattice is not always a hypercube when hierarchies are not simple.

2. We can model the common queries asked by users better using a lattice framework. Users usually do not jump between unconnected elements in this lattice, they move along the edges of the lattice. In fact, drill-down is going up (going from a lower to higher level) a path in this lattice, while roll-up is going down a path.

3. The lattice approach also tells us in what order to materialize the views. By using views that have already been materialized to materialize other views, we can reduce access to the raw data and so decrease the total materialization time. <u>A simple descending-order topological sort</u> on the $\preceq$ operator gives the required order of materialization. The details are in [HRU95].

   > 一种简单的降序拓扑排序

## 3. The Cost Model

In this section, we review and justify our assumptions about, the **linear cost model**, in which the time to answer a query is taken to be equal to the space occupied by the view from which the query is answered. We then consider some points about estimating sizes of views without materializing them and give some experimental validation of the linear cost model.

> 在本节中，我们回顾并证明关于**线性成本模型**的假设，在该模型中，**回答查询的时间**等于**回答查询视图所占用的空间**。然后，我们考虑在不物化视图的情况下估计视图大小的一些问题，并对线性陈本模型进行了实验验证。

### 3.1 The Linear Cost Model
Let (***L***, $\preceq$) be a lattice of queries (views). To answer a query ***Q*** we choose an ancestor of ***Q*** , say ***Q~A~*** , that has been materialized. We thus need to process the table corresponding to ***Q~A~*** to answer ***Q*** . The cost of answering ***Q***  is a function of the size of the table for***Q~A~***. In this paper, we choose the simplest costmodel:

- The cost of answering ***Q*** is the number of rows present in the table for that query ***Q~A~*** used to construct ***Q***. 

  > ==回答 ***Q*** 的成本是表中用于构造 ***Q*** 的查询 ***Q~A~*** 的行数==。

<u>As we discussed in Section 1.2, not all queries ask for an entire view, such as a request for the sales of all parts, It is at least as likely that the user would like to see sales for a particular part or for a few parts</u>. If we have the appropriate index structure, and the view `(part)` is materialized,. then we can get our answer in ***O(1)*** time. If there is not an appropriate index structure, then we would have to search the entire `(part)` view, and the query for a single part takes almost as long as producing the entire view.

> 正如我们在第 1.2 节中所讨论的，并不是所有的查询都要求一个完整的视图，例如用户查看所有零件的销售请求，至少和查看特定零件或少数零件的销售请求一样多。
>
> 如果物化视图有索引，成本是 ***O(1)***，否则是 ***O(n)***。

If, for example, we need to answer a query about a single part from some ancestor view such as `(part, supplier)` we need to examine the entire view. It can be seen that a single scan of the view is sufficient to get the **sales** of a particular part. On the other hand, if we wish to find the **sales** for each part from the ancestor view `(part, supplier)`, we need to do an aggregation over this view. We can use either hashing or sorting (with early aggregation) [Gra93] to do this aggregation. <u>The cost of doing the aggregation is a function of the amount of memory available and the ratio of the number of rows in the input to that in the output</u>. In the best case, a single pass of the input is sufficient (for example, when the hash table fits in main memory). In practice, it has been observed that most aggregations take between one and two passes of the input data.

> **聚合成本**是可用内存量和输入行数与输出行数之比的函数

While the actual cost of queries that ask for single cells, or small numbers of cells, rather than a complete view, is thus complex, ==we feel it is appropriate to make an assumption of uniformity==. We provide a rationale for this assumption in [HRU95]. Thus: 

> 虽然要求单个单元格或少量单元格（而不是完整视图）的查询的实际成本很复杂，但我们认为假设一致是适当的。我们在[HRU95]中为这一假设提供了理论基础。因此：

- We assume that all queries are identical to some element (view) in the given lattice

  > 我们假设所有查询都与给定 **lattice** 中的某个元素（视图）相同

Clearly there are other factors, not considered here, that influence query cost. Among them are the clustering of the materialized views on some attribute, and the indexes that may be present. More complicated cost models are certainly possible, but we believe the cost model we pick, being both simple and realistic, enables us to design and analyze powerful algorithms. Moreover, our analysis of the algorithms we develop in Sections 4 and 5 reflects their performance under other cost models as well as under the model we use here. [GHRU96] investigates a more detailed model incorporating indexes.

### 3.2 Experimental Examination of the Linear Cost Model
An experimental validation of our cost model is shown in Fig. 5. On the TPC-D data, we asked for the **total sales for a single supplier**, using views of four different granularities. We find an almost linear relationship between size and running time of the query. This linear relationship can be expressed by the formula: `T = m * S+c`. Here `T` is the running time of the query on a view of size `S`, `c` gives the fixed cost (the overhead of running this query on a view of negligible size), and `m` is the ratio of the query time to the size of the view, after accounting for the fixed cost. As can be seen in Fig. 5 this ratio is almost the same for the different views.

>
>- [ ] Figure 5.

### 3.3 Determining View Sizes
Our algorithms require knowledge of the number of rows present in each view. There are many ways of estimating the sizes of the views without materializing all the views. <u>One commonly used approach is to run our algorithms on a statistically representative but small subset of the raw data. In such a case, we can get the sizes of the views by actually materializing the views. We use this subset of raw data to determine which views we want to materialize</u>.

> 一种常用的方法是在具有统计意义的原始数据的一小部分上运行我们的算法。这种情况下，我们可以通过物化视图来获得视图的大小。我们使用原始数据的子集来确定要物化哪些视图。

We can use sampling and analytical methods to compute the sizes of the different views if we only materialize the largest element v~l~ in the lattice (the view that groups by the largest attribute in each dimension). For a view, if we know that the grouping attributes are statistically independent, we can estimate the size of the view analytically, given the size of  v~l~. Otherwise we can sample  v~l~ (or the raw data) to estimate the size of the other views. The size of a given view is the number of distinct values of the attributes it groups by, There are many well-known sampling techniques that we can use to determine the number of distinct values of attributes in a relation [HNSS95].

> 如何取样？

## 4 Optimizing Data-Cube Lattices
**Our most important objective is to develop techniques for optimizing the space-time tradeoff when implementing a lattice of views**. <u>The problem can be approached from many angles, since we may in one situation favor time, in another space, and in a third be willing to trade time for space as long as we get good “value” for what we trade away. In this section, we shall begin with a simple optimization problem</u>. in which

> 这个问题可以从多个角度来解决，因为在一种情况下，我们可能偏好**时间**，在另一种情况下偏好**空间**，在第三种情况下，只要我们为所交换的东西获得良好的“价值”，我们愿意用时间来交换空间。在本节中，我们将从一个简单的优化问题开始。

1. We wish to minimize the average time taken to evaluate the set of queries **that are identical to the views**.

   > 我们希望最小化计算**与视图相同的**查询集所用的平均时间。

2. We are constrained to materialize a fixed number of views, regardless of the space they use.

   > 我们受限于物化固定数量的视图，而不管使用了多少空间。

Evidently item (2) does not minimize space, but in Section 4.5 we shall show how to adapt our techniques to a model that does optimize space utilization.

> 显然第 (2) 项并没有使空间最小化，但是在第4.5节，将展示如何将我们的技术应用于能够优化空间利用率的模型。

Even in this simple setting, the optimization problem is **NP-complete**: there is a straightforward reduction from **Set-Cover**. **Thus, we are motivated to look at heuristics to produce approximate solutions.** The obvious choice of heuristic is a “greedy” algorithm, where we select a sequence of views, each of which is the best choice given <u>==what has gone before==</u>. We shall see that this approach is always fairly close to optimal and in some cases can be shown to produce the best possible selection of views to materialize.

> 使用启发式方案，那么显而易见的一个选择是**贪心算法**，选择一系列视图，考虑到<u>==以前的情况==</u>，每种视图都是最佳选择。
>
> 这里的 what has gone before 是指什么？

### 4.1 The Greedy Algorithm
Suppose we are given a data-cube **lattice** with space costs associated with each view. In this paper, the space cost is the number of rows in the view. Let ***C(v)*** be the cost of view ***v***. The set of views we materialize should always include the top view, because there is no other view that can be used to answer the query corresponding to that view. Suppose there is a limit ***k*** on the number of views. in addition to the top view, that we may select. After selecting some set ***S*** of views, the **benefit** of view ***v*** relative to ***S***, denoted by ***B(v, S)***, is defined as follows.

> 假设我们有一个数据立方体的 **lattice**，每个视图都有相关的空间开销。本文视图行数表示空间成本。让***C(v)*** 作为视图 ***v*** 的成本。我们物化的**视图集**应该始终包含顶部视图（即粒度最细的视图），因为没有其他视图可以回答与该视图对应的查询。假设除了顶部视图，我们可以选择的视图数量有一个限制 ***k***。在选择了一组视图 ***S*** 之后，视图 ***v*** 相对于 ***S*** 的**收益**由 ***B(v，S)*** 表示， 定义如下。

1. For each ***w*** $\preceq$ ***v***, define the quantity ***B~W~*** by:
   1. Let ***u*** be the view of least cost in ***S*** such that ***w*** $\preceq$ ***u***. Note that since the top view is in ***S***, there must be at least one such view in ***S***. 
   2. If ***C(v)*** < ***C(u)***, then ***B~W~*** = ***C(v)*** – ***C(u)***. Otherwise, B~W~ = 0.
2. Define $B(v,S) = \sum_{w \preceq v}B_{w}$

> 1. 对于每个 ***w*** $\preceq$ ***v***，通过以下方式定义数量***B~w~***：
>    1. 设 ***u*** 是 ***S***中成本最小的视图，这样 ***w*** $\preceq$ ***u***。请注意，由于顶部视图位于 ***S*** 中，因此在 ***S*** 中必然有一个这样的视图。
>    2. 如果 ***C(v)*** < ***C(u)***, 那么 ***B~W~*** = ***C(v)*** – ***C(u)***. 否则, B~W~ = 0.
> 2. 定义 $B(v,S) = \sum_{w \preceq v}B_{w}$

That is, we compute the **benefit** of ***v*** by considering how it can improve the cost of evaluating views, including itself. For each view ***w*** that ***v*** covers, we compare the cost of evaluating ***w*** using ***v*** and using whatever view from ***S*** offered the cheapest way of evaluating ***w***. If ***v*** helps, i.e., the cost of ***v*** is less than the cost of its competitor, then the difference represents part of the benefit of selecting ***v*** as a materialized view. The total benefit ***B(v, s)*** is the sum over all views ***w*** of the benefit of using v to evaluate w, providing that benefit is positive.

Now, we can define the ***Greedy Algorithm*** for selecting a set of ***k*** views to materialize. The algorithm is shown in Fig. 6.

>也就是说，我们通过考虑如何降低视图（包括视图自身）的计算成本来计算视图 **v** 的**效益**。对于 ***v*** 涵盖的每个视图 ***w***，我们比较：使用 ***v*** 计算 ***w*** 的成本，和使用 ***S*** 中成本最低的视图计算 ***w*** 的成本。如果 ***v*** 有帮助，即使用 ***v*** 的成本低于其竞争对手的成本，则成本差代表了选择 ***v*** 作为物化视图的部分好处。总收益 ***B(v, s)*** 是使用 ***v*** 计算所有 ***w*** 视图的收益总和，前提是该收益为正。
>
>现在，我们可以定义***贪心算法*** 以选择 ***k*** 个视图来物化。该算法如图 6 所示。

```bash
s = {top view};
for i=1 to k do begin
  select that view v not in S such
         that B(v, S) is maximized;
  S = S union {v};
end;

# Figure 6: The Greedy Algorithm #
```
**EXAMPLE 4.1** Consider the lattice of Fig. 7. Eight views, named a through h have space costs as indicated on the figure. The top view a, with cost 100, must be chosen. Suppose we wish to choose three more views.

> **例4.1** 考虑图7的lattice。如图所7示，从 a 到 h 有八个带有空间成本的视图。必须选择顶部视图 a，其成本为100。假设我们想再选择三个视图。

<p align="center">
 <img src="./Implementing Data Cubes Efficiently/Figure_7.png" />
 图 7  Example lattice with space costs
</p>
To execute the greedy algorithm on this lattice, we must make three successive choices of view to materialize. The column headed "First Choice" in Fig. 8 gives us the benefit of each of the views besides ***a***. When calculating the benefit, we begin with the assumption that each view is evaluated using ***a***, and will therefore have a cost of 100.

> 在这个 lattice 上执行贪心算法，要连续三次选择要物化的视图。图8中的第一列（选择1）为我们提供了除 ***a*** 之外，使用其他视图的收益。在计算收益时，我们首先假设每个视图都是使用 ***a*** 进行评估的，因此成本为100。

|      | Choice 1         | Choice 2         | Choice 3         |
| ---- | ---------------- | ---------------- | ---------------- |
| b    | **50 * 5 = 250** |                  |                  |
| c    | 25 * 5 = 125     | 25 * 2 = 50      | 25 * 1 = 25      |
| d    | 80 * 2 = 160     | 30 *2 = 60       | **30 * 2 = 60**  |
| e    | 70 * 3 = 210     | 20 * 3 = 60      | 2 * 2- + 10 = 50 |
| f    | 60 * 2 = 120     | **60 + 10 = 70** |                  |
| g    | 99 * 1 = 99      | 49 * 1 = 49      | 49 * 1 = 49      |
| h    | 90 * 1 = 90      | 40 * 1 = 40      | 30 * 1 = 30      |
*图8: 每轮选择的收益*

If we pick view ***b*** to materialize first, then we reduce by 50 its cost and that of each of the views ***d***, ***e***, ***g***, and ***h*** below it. The benefit is thus 50 times 5, or 250, as indicated in the row ***b*** and first column of Fig. 8. As another example, if we pick ***e*** first then it and the views below it — ***g*** and ***h*** — each have their costs reduced by 70, from 100 to 30. Thus, the benefit of ***e*** is 210.

> 如果我们选择先物化视图 ***b***，那么我们将其下的视图 ***d***、***e***、***g***、和 ***h*** 的成本各减少50。因此，如图8的行 ***b*** 和第一栏所示，收益是5的50倍，或250。再举一个例子，如果我们先选择 ***e***， 那么它下面的视图 ***g*** 和 ***h*** 每个视图的成本都减少了70，从100降到30。因此，***e*** 的收益是210。

**Evidently**, the winner in the first round is ***b***, so we pick that view as one of the materialized views. Now, we must recalculate the benefit of each view ***V***, given that the view will be created either from ***b***, at a cost of 50, if ***b*** is above ***V***, or from ***a*** at a cost of 100, if not. The benefits are shown in the second column of Fig. 8.

> **显然**，第一轮的胜利者是 ***b***，所以我们选择首先物化它。现在，我们必须重新计算每个视图 ***v*** 的收益，如果***b*** 在 ***v***的上方，则从***b***创建，成本为50；否则从***a***创建，成本100。其收益如图 8的第二栏所示。

For example, the benefit of ***c*** is now 50, 25 each for itself and ***f***. Choosing ***c*** no longer improves the cost of ***e***, ***g***, or ***h***, so we do not count an improvement of 25 for those views. As another example, choosing ***f*** yields a benefit of 60 for itself, from 100 to 40. For ***h***, it yields a benefit of 10, from 50 to 40, since the choice of ***b*** already improved the cost associated with ***h*** to 50. The winner of the second round is thus ***f***, with a benefit of 70. Notice that ***f*** wasn’t even close to the best choice at the first round.

> 例如，***c*** 的收益现在为50，其本身和 ***f***  的收益分别为25和25。选择 ***c*** 不再会降低 ***e***，***g*** 或 ***h*** 的成本，因此 ***c*** 的收益不会包含这三个视图。再举一个例子，选择 ***f*** 会为自己带来60的收益，从100到40；对于 ***h***，则会产生10的收益，从50到40，因为选择 ***b*** 已经将与 ***h*** 相关的成本降低到50。因此，第二轮获胜者是 ***f***，收益为70。请注意，在第一轮计算中，***f*** 甚至没有接近最佳选择。

Our third choice is summarized in the last column of Fig. 8. The winner of the third round is *d*, with a benefit of 60, gained from the improvement to its own cost and that of ***g***.

The greedy selection is thus ***b***, ***d***, and ***f***. These, together with ***a***, reduces the total cost of evaluating all the views from 800, which would be the case if only a was materialized, to 420. That cost is actually optimal. 

> 我们的第三个选择总结在图8的最后一栏。第三轮的赢家是*d*，从自身成本和 ***g*** 的改进中获得60的收益。
>
> 因此贪心算法的选择是 ***b***，***d***，和 ***f***。加上 ***a***，将所有视图的总成本从 800（如果只有 ***a*** 被实现的话，情况就是这样）降低到 420。这个成本实际上是最优的。

**EXAMPLE 4.2** Let us now examine the lattice suggested by Fig. 9. This lattice is, as we shall see, essentially as bad as a lattice can be for the case ***k = 2***. The greedy algorithm, starting with only the top view ***a***, first picks ***c***, whose benefit is 4141. That is, ***c*** and the 40 views below it are each improved from 200 to 99, when we use ***c*** in place of ***a***.

For our second choice, we can pick either ***b*** or ***d***. They both have a benefit of 2100. Specifically, consider ***b***. It improves itself and the 20 nodes at the far left by 100 each. Thus, with ***k = 2***, the greedy algorithm produces a solution with a benefit of 6241.

However, the optimal choice is to pick ***b*** and ***d***. Together, these two views improve, by 100 each, themselves and the 80 views of the four chains. Thus, the optimal solution has a benefit of 8200. the ratio of greedy/optimal is **6241/8200**, which is about **3/4.** In fact, by making the cost of ***c*** closer to 100, and by making the four chains have arbitrarily large numbers of views, we can find examples for ***k = 2*** with ratio arbitrarily close to ***3/4***, but no worse.

### 4.2 An Experiment With the Greedy Algorithm

We ran the greedy algorithm on the lattice of Fig. 4, using the TPC-D database described earlier. Figure 10 shows the resulting order of views, from the first (top view, which is mandatory) to the twelfth and last view. The units of Benefit, Total Time and Total Space are number of rows. Note, the average query time is the total time divided by the number of views (12 in this case).

This example shows why it is important to materialize some views and also why materializing all views is not a good choice. The graph in Fig. 11 has the total time taken and the space consumed on the Y-axis, and the number of views picked on the X-axis. It is clear that for the first few views we pick, with minimal addition of space, the query time is reduced substantially. After we have picked 5 views however, we cannot improve total query time substantially even by using up large amounts of space. For this example, there is a clear choice of when to stop picking views. If we pick the first five views — ***cp***, ***ns***, ***nt***, ***c***, and ***p*** — (i. e., ***k = 4***, since the top view is included in the table), then we get almost the minimum possible total time, while the total space used is hardly more than the mandatory space used for just the top view.

### 4.3 A Performance Guarantee for the Greedy Algorithm

We can show that no matter what lattice we are given, the greedy algorithm never performs too badly. Specifically, the ***benefit*** of the greedy algorithm is at least 63% of the benefit of the optimal algorithm. The precise fraction is ***(e — 1)/e***, where ***e*** is the base of the natural logarithms. 

> 可以证明，无论给定何种 **lattice**，贪心算法都不会表现得太差。具体而言，贪心算法的**收益**至少是最优算法的 **63％**。 精确分数是 ***(e - 1) / e***，其中 ***e*** 是自然对数的底数。

To begin our explanation, we need to develop some notation. Let ***m*** be the number of views in the lattice. Suppose we had no views selected except for the top view (which is mandatory). Then the time to answer each query is just the number of rows in the top view. Denote this time by T~0~. Suppose that in addition to the top view, we choose a set of views **V**. Denote the average time to answer a query by T~v~. The ***benefit*** of the set of views ***V*** is the reduction in average time to answer a query, that is, T~0~ – T~v~. <u>Thus minimizing the average time to answer a query is equivalent to maximizing the benefit of a set of views.</u>

> 因此，最小化查询响应的平均时间**等价于**最大化一组视图的收益。

Let v~1~, v~2~, ..., v~k~ be the ***k*** views selected in order by the greedy algorithm. Let a~i~ be the benefit achieved by the selection of v~i~, ***for i = 1, 2, . . . . k***. That is, a~i~ is the benefit of v~i~, with respect to the set consisting of the top view and v~1~, v~2~, ..., v~i-1~. Let V= {v~1~, v~2~, ..., v~k~}.

Let W = {w~1~, w~2~, ..., w~k~} be an optimal set of k views, i.e., those that give the maximum total benefit. The order in which these views appear is arbitrary, but we need to pick an order. Given the w’s in order w~1~, w~2~, ..., w~k~, define b~i~ to be the benefit of w~i~ with respect to the set consisting of the top view plus w~1~, w~2~, ..., w~i-1~. Define $A = \sum_{i=1}^k a_{i}$ and $B = \sum_{i=1}^k b_{i}$

It is easy to show that the benefit of the set ***V*** chosen by the greedy algorithm, B~greedy~, is ***T~0~ –T~v~ = A/m***, and the benefit of the optimal choice W is ***B~opt~ = T~0~ – T~w~ = B/m***. In the full version of this paper [HRU95], we show that:

​			$\frac{B_{greedy}}{B_{opt}} = \frac{A}{B} \geq 1-(\frac{k-1}{k})^k$

For example, for k = 2 we get $\frac{A}{B} \geq \frac{3}{4}$; i.e., the greedy algorithm is at least 3/4 of optimal. We saw in Example 4.2 that for k = 2 there were specific lattices that approached 3/4 as the ratio of the benefits of the greedy and optimal algorithms. In [HRU95] we show how for any k we can construct a lattice such that the ratio $\frac{A}{B} = 1-(\frac{k-1}{k})^k$. 

As $k \to \infty$ , $(\frac{k-1}{k})^k$ approaches ***1/e***, so $\frac{A}{B} \geq 1-\frac{1}{e} = \frac{e-1}{e} = 0.63$. <u>That is, for no lattice whatsoever does the greedy algorithm give a benefit less than 63% of the optimal benefit. Conversely, the sequence of bad examples we can construct shows that this ratio cannot be improved upon. We summarize our results in the following theorem</u>:

> 也就是说，对于任何一个 **lattice**，贪心算法的收益都不小于最优收益的63%。相反，我们可以构建的一系列坏例子表明，这个比率无法提高。我们用以下的定理中总结结果：

**Theorem 4.1** For any lattice, let ***B~greedy~*** be the benefit of ***k*** views chosen by the greedy algorithm and let ***B~opt~*** be the benefit of an optimal set of k views. Then $\frac{B_{greedy}}{B_{opt}} \geq 1-\frac{1}{e}$. Moreover, this bound is tight: that is, there are lattices such that $\frac{B_{greedy}}{B_{opt}}$ is arbitrarily close to $1-\frac{1}{e}$. 

An interesting point is that the greedy algorithm does as well as we can hope any polynomial-time  algorithm to do. Chekuri [Che96] has shown, using the recently published result of Feige [Fei96], that unless ***P = NP***, there is no deterministic polynomial-time algorithm that can guarantee a better bound than the greedy.

### 4.4 Cases Where Greedy is Optimal
The analysis of Section 4.3 also lets us discover certain cases when the greedy approach is optimal, or very close to optimal. Here are two situations where we never have to look further than the greedy solution.

> 通过对第4.3节的分析，我们还可以发现贪心方法是最优的或非常接近最优的某些情况。在这两种情况下，我们永远不必再去寻找比贪心算法更好的解决方案。

1. If a~l~ is much larger than the other a’s, then greedy is close to optimal.

   > 如果 a~l~ 比其他的 a大得多，则贪心算法接近最优。

2. If all the a‘s are equal then greedy is optimal. 

   > 如果所有的a都相等，那么贪心算法是最优的。

**==The justifications for these claims==** are based on the proof of Theorem 4.1 and appear in [HRU95].

> **==这些声明的依据==**基于定理4.1的证明，见[HRU95]

### 4.5 Extensions to the Basic Model
There are at least two ways in which our model fails to reflect reality.

1. The views in a lattice are unlikely to have the same probability of being requested in a query. Rather, we might be able to associate some probability with each view, representing the frequency with which it is queried.

2. Instead of asking for some fixed number of views to materialize, we might instead allocate a fixed amount of space to views (other than the top view, which must always be materialized).

Point (1) requires little extra thought. When computing benefits, we weight each view by its probability. The greedy algorithm will then have exactly the same bounds on its performance: at least 63% of optimal.

> 第 (1) 点不需要额外考虑。在计算收益时，我们根据每个视图的概率对其进行加权。贪婪算法的性能将完全相同：至少63%是最优的。

Point (2) presents an additional problem. If we do not restrict the number of views selected but fix their total space, then we need to consider the benefit of each view ***per unit space*** used by a materialization of that view. The greedy algorithm again seems appropriate, but there is the additional complication that we might have a very small view with a very high benefit per unit space, and a very large view with almost the same benefit per unit space. Choosing the small view excludes the large view, because there is not enough space available for the large view after we choose the small. However, we can prove the following theorem [HRU95], which says that if we ignore “**boundary cases**” like the one above, the performance guarantee of the greedy algorithm is the same as in the simple case. The theorem assumes that we use the ***benefit per unit space*** in the greedy algorithm as discussed above.

**Theorem 4.2** Let  ***B~greedy~*** be the benefit and *S* the space occupied by some set of views chosen using the greedy algorithm, and let ***B~opt~*** be the benefit of an optimal set of views that occupy no more than ***S*** units of space. Then $\frac{B_{greedy}}{B_{opt}} \geq 1-\frac{1}{e}$ and this bound is tight.

## 5. The Hypercube Lattice
