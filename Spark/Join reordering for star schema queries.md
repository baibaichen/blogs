# Join reordering for star schema queries

## 1 Objective

The objective is to provide a consistent performance improvement for star schema queries given the current Spark limitations e.g. limited statistics, no RI constraints information, no cost model support.

Star schema consists of one or more fact tables referencing a number of dimension tables. In general, queries against star schema are expected to run fast because of the established RI constraints among the tables. This design proposes a join reordering based on natural, generally accepted heuristics for star schema queries:Finds the star join with the largest fact table and places it on the driving arm of the left-deep join. ==This plan avoids large tables on the inner, and thus favors hash joins. Applies the most selective dimensions early in the plan to reduce the amount of data flow== .

## 2 Star schema detection

> In general, star schema joins are detected using the following conditions:
>
> 1. Informational RI constraints (reliable detection)
>
>    1. Dimension contains a primary key that is being joined to the fact table.
>    2. Fact table contains foreign keys referencing multiple dimension tables.
>
> 2. Cardinality based heuristics
>
>    1. Usually, the table with the highest cardinality is the fact table.
>    2. Table being joined with the most number of tables is the fact table.
>
> To detect star joins, the algorithm uses a combination of the above two conditions. The fact table is chosen based on the cardinality heuristics, and the dimension tables are chosen based on the RI constraints. **==A star join will consist of the largest fact table joined with the dimension tables on their primary keys==**. To detect that a column is a primary key, the algorithm uses the newly introduced table and column statistics. This is an approximation until informational RI constraints are supported.
>
> Since Catalyst only supports left-deep tree plans, the algorithm currently returns only the star join with the largest fact table. Choosing the largest fact table on the driving arm to avoid large inners is in general a good heuristic. This restriction can be lifted with support for bushy tree plans.
>

通常使用以下条件检测星型连接模式：

1. 信息 RI 约束（可靠检测）
   1. 维度表包含一个要连接到事实表的主键。
   2. **事实表**包含引用多个维度表的外键。
2. 基于基数的启发式
   1. 通常，基数最高的表是事实表。
   2. 连接最多的表就是事实表。

为了检测星型连接，该算法结合了上述两种条件。基于**基数启发式**选择事实表，并基于 RI 约束选择维度表。 **==星型连接将包括最大的事实表及其主键上的维表==**。<u>为了检测主键列，该算法使用新引入的表和列统计信息，在支持信息 RI 约束之前，这是一个近似值</u>。

由于 Catalyst 只支持左深树，该算法当前只返回具有最大事实表的星形联接。选择**==驱动臂==**上最大的事实表以避免内部有大的 Join 通常是一个很好的试探法。可以通过支持 **bushy tree plans** 来解除此限制。

## 3 High level design

> The highlights of the algorithm are the following. Given a set of joined tables/plans, the algorithm first verifies if they are eligible for star join detection. An eligible plan is a base table access with valid statistics. A **base table access** represents Project or Filter operators above a LeafNode. Conservatively, the algorithm only considers base table access as part of a star join since they provide reliable statistics.
>
> If some of the plans are not base table access, or statistics are not available, the algorithm falls back to the positional join reordering, since in the absence of statistics it cannot make good planning decisions. Otherwise, the algorithm finds the table with the largest cardinality (number of rows), which is assumed to be a fact table.
>
> Next, it computes the set of dimension tables for the current fact table. A dimension table is assumed to be in a RI relationship with a fact table. To infer column uniqueness, the algorithm compares the number of distinct values with the total number of rows in the table. If their relative difference is within certain limits (i.e. ndvMaxError * 2, adjusted based on tpcds data results), the column is assumed to be unique. 
>
> Given a star join, i.e. fact and dimension tables, the algorithm considers three cases:
>
> 1. The star join is an **expanding join** i.e. the fact table is joined using **==inequality predicates==**, or **==Cartesian product==**. In this case, the algorithm conservatively falls back to the default join reordering since it cannot make good planning decisions in the absence of the cost model. 
> 2. The star join is a selective join. This case is detected by observing **==local predicates==** on the dimension tables. In a star schema relationship, the join between the fact and the dimension table is in general a FK-PK join. Heuristically, a selective dimension may reduce the result of a join.
> 3. The star join is not a selective join (i.e. doesn’t reduce the number of rows). In this case, the algorithm conservatively falls back to the default join reordering. 
>
> If an eligible star join was found in step 2, the algorithm reorders the tables based on the following heuristics:
>
> - Place the largest fact table on the driving arm to avoid large tables on the inner of a join and thus favor hash joins.
> -  Apply the most selective dimensions early in the plan to reduce data flow.
>
>  Other assumptions made by the algorithm, mainly to prevent regressions in the absence of a cost model, are the following:
>
> - Only considers star joins with more than one dimension, which is a typical star join scenario.
> - If the largest tables in the join have comparable number of rows, fall back to the default join reordering. This will prevent changing the position of the large tables in the join. 

该算法的重点如下。给定一组连接表/计划，该算法首先验证它们是否符合星型连接检测的条件。合格计划是具有有效统计信息的**==基表访问==**。**基表访问**表示 `LeafNode` 上是 `Project` 或者 `Filter` 运算符。保守地说，该算法只将基表访问视为星型连接的一部分，因为它们提供了可靠的统计信息。

如果某些计划不是基表访问，或者统计信息不可用，则该算法会**回退**到位置连接重新排序，因为在缺少统计信息的情况下，无法决策出好的计划。否则，算法将查找基数（行数）最大的表，该表被假定为事实表。

接下来，它计算当前事实表的**维度表**集。假设维度表与事实表具有 RI 关系。为了推断列的唯一性，该算法将不同值的数量与表中的总行数进行比较。如果它们的相对差异在一定范围内（即ndvMaxError * 2，根据 tpcds 数据结果进行了调整），则认为该列是唯一的。

给定星型连接，即事实和维度表，该算法考虑三种情况：

1. 如果星型连接是**==扩展连接==**，即，事实表使用**==不等式谓词==**或**==笛卡尔乘积==**连接。此时，因为在缺少成本模型的情况下无法决策出好的计划，算法保守地退回到默认的连接重排序。
2. 如果星型连接是选择连接。通过观察维表上的**==局部谓词==**可以检测到这种情况。在星型模式关系中，事实和维表之间的连接通常是 FK-PK 连接。启发式地，选择维度可以减少连接的结果。
3. 星形连接不是选择性连接（即不减少行数）。在这种情况下，算法保守地退回到默认的连接重排序。

如果在步骤 2 中找到符合条件的星形连接，该算法将基于以下启发式方法对关联表进行重新排序：

- 将最大的事实表放在**==驱动臂==**上，以避免在联接内部放置大表，从而有利于哈希联接。
- 先选择最具选择性的维度关联，以减少数据。

该算法作出的其他假设（主要是为了防止在没有成本模型的情况下出现回归）如下：

- 只考虑具有多个维度的星型连接，这是一种典型的星型连接场景。
- <u>如果联接中最大的表具有相当数量的行，则返回到默认的联接重新排序。这将防止更改联接中大表的位置</u>。

# [Literature Review on Join Reorderability](https://yunpengn.github.io/blog/2018/12/22/literature-review-join-reorder/)

Recently, I was looking at some research papers on the join reorderability. To start with, let’s understand what do we mean by *“join reorderability”* and why it is important.

## Background Knowledge

> Here, we are **==looking at==** a query optimization problem, specifically join optimization. As mentioned by [Benjamin Nevarez](http://www.benjaminnevarez.com/2010/06/optimizing-join-orders/), there are two factors in join optimization: **selection of a join order** and **choice of a join algorithm**.

在这里，我们**==研究==**查询优化中的连接优化问题，。如 [Benjamin Nevarez](http://www.benjaminnevarez.com/2010/06/optimizing-join-orders/) 所述，连接优化有两个因素：**选择连接顺序**和**选择连接算法**。

As stated by Tan Kian Lee’s [lecture notes](https://www.comp.nus.edu.sg/~tankl/cs3223/slides/opr.pdf), common join algorithms include iteration-based nested loop join *(tuple-based, page-based, block-based)*, sort-based merge join and partition-based hash join. We should consider a few factors when deciding which algorithm to use: 1) types of the join predicate (equality predicate v.s. non-equality predicate); 2) sizes of the left v.s. right join operand; 3) available buffer space & access methods.

For a query attempting to join `n` tables together, we need `n - 1` individual joins. Apart from the join algorithm applied to each join, we have to decide in which order these `n` tables should be joined. We could represent such join queries on multiple tables as a tree. The tree could have different shapes, such as left-deep tree, right-deep tree and bushy tree. The 3 types of trees are compared below on an example of joining 4 tables together.

![3 types of join trees](https://yunpengn.github.io/blog/images/join_order_tree.jpg)

For a join on `n` tables, there could be `n!` left-deep trees and `n!` right-deep trees respectively. There could be even more bushy trees, etc. Given so many different join orders, it is important to find an optimal one among them. There are many different algorithms to find the optimal join order: exhaustive, greedy, randomized, transformation, dynamic programming (with pruning).

## Join Reorderability

> As illustrated in the last section, we have developed algorithms to find the optimal join order of queries. However, we could meet problems when we try to apply such algorithms on outer joins & **==anti-joins==**. This is because such joins do not have the same nice properties of commutativity and assocativitity associativity as inner joins.
>
> Further, this means our algorithms cannot safely search on the entire space to find an optimal order *(i.e. a significant subset of the search space is invalid)*. Such dilemma puts us into two questions: 1) which part of the search space is valid? 2) what can we do with the invalid part of the search space?
>
> Up to now, hopefully the topic has become much clearer to you. In *join reorderability*, we are trying to figure out “the ability to manipulate the join query to a certain join order”.
>

如上一节所述，已经有一些算法用来查找查询的最佳连接顺序。 但是，当我们尝试将此类算法应用于**==外部连接==**和**==反连接==**时，可能会遇到问题。 这是因为此类连接不具有与==内部连接==相同的<u>可交换性</u>和结合性。

此外，这意味着我们的算法无法安全地在整个空间中搜索以找到最优顺序（**即搜索空间的重要子集无效**）。这种困境使我们陷入两个问题：1）搜索空间的哪一部分有效？ 2）如何处理搜索空间的无效部分？

到现在为止，希望您对本主题有了更清晰的了解。在 *join reorderability* 中，我们试图找出**将连接查询操纵为特定连接顺序的能力**。

## Recent Researches

As follows, I summarize some recent researches on this topic and give my *naive* literature reviews on them.

### Moerkotte, G., Fender, P., & Eich, M. (2013). On the correct and complete enumeration of the core search space. doi:10.1145/2463676.2465314

This paper begins by pointing out two major approaches (bottom-up dynamic programming & top-down memoization) to find optimal join order requires the considered search space to be valid. In other words, this probably only works when we consider inner joins only. Such algorithms could not work on outerjoins, antijoins, semijoins, groupjoins, etc.

To (partially) solve this problem, this paper presents 3 *conflict detectors*, `CD-A`, `CD-B` & `CD-C` (all correct, but only `CD-C` is also complete). It also shows 2 approaches (NEL/EEL & SES/TES) proposed in previous researches are buggy. The authors also propose a desired conflict detector to have the following properties:

- correct: no invalid plans will be included;
- complete: all valid plans will be generated;
- easy to understand and implement;
- flexible:
  - *null-tolerant:* the predicates are not required to reject nulls (opposite to *null-intolerant*);
  - *complex:* the predicates could reference more than 2 relations;
- extensible: extend the set of binary operators considered *(by a table-driven approach)*.

Then, the paper introduces the **“core search space”**, all valid ordering defined by a set of transformation rules. Notice that based on commutativity and assocativitity, the left & right asscom property is also proposed. A “conflict” means application of such transformations *(4 kinds of transformations based on 4 properties: commutativity, associativity, l-asscom & r-asscom)* will result in an invalid plan. “Conflict detector” basically tries to find out such “conflict”s.

If a predicate contained in binary operators do not reference tables from both operands, it is called a *degenerate predicate*. It is observed that for *non-degenerate predicate*:

- For left nesting tree: can apply either associativity or l-asscom (but not both); and
- For right nesting tree: can apply either associativity or r-asscom (but not both).

![3 basic properties](https://yunpengn.github.io/blog/images/join_order_asscom.png)

This observation in fact makes our life much easier. For either left or right nesting tree, we only need to consider one kind of transformation (rather than two kinds). We can further observe we usually at most need to apply commutativity *once* to each operator. We probably only need to apply associativity, l-asscom, or r-asscom less than once per operator as well. All valid & invalid transformations based on the 3 above properties are summarized in the following tables.

![Valid & invalid transformations](https://yunpengn.github.io/blog/images/join_order_TBA.png)

From this, we can infer that it is possible to create an algorithm to iterate through the whole search space in finite steps. Thereafter, the authors proposed an algorithm that extends the classical dynamic programming algorithm, as shown below.

![Pseudocode for DP algorithm](https://yunpengn.github.io/blog/images/join_order_dp_algo.png)

Notice that the procedure above calls a sub-procedure `Applicable`, which tests whether a certain operator is applicable. Talking about “reorderability”, the rest would discuss how to implement this sub-procedure `Applicable`.

We introduce a few terms: syntactic eligibility sets (SES), total eligibility sets (TES). When commutativity does not hold, we want to prevent operators from both sides to communicate with each other. Thus, we further define L-TES and R-TES. By merging tables from either left or right operand, we can eliminate those invalid plans depending on whether l-asscom or r-asscom holds. This introduces the `CD-A` algorithm, as shown below.

![Pseudocode for CD A](https://yunpengn.github.io/blog/images/join_order_CD_A.png)

By introducing some *conflict rules* (CRs), `CD-B` is proposed as follows.

![Pseudocode for CD B](https://yunpengn.github.io/blog/images/join_order_CD_B.png)

`CD-C` only improves the CRs.

![Pseudocode for CD C](https://yunpengn.github.io/blog/images/join_order_CD_C.png)

### Rao, J., Pirahesh, H., & Zuzarte, C. (2004). Canonical abstraction for outerjoin optimization. doi:10.1145/1007568.1007643

Similar to the previous paper, this paper also recognizes the difficulties in optimizing outerjoins due to the lack of commutativity and assocativitity. The authors believe that a canonical representation of inner joins would be `the Cartesian products of all relations, followed by a sequence of selection operations, each applying a conjunct in the join predicates`. So can we find a canonical abstraction for outerjoins as well? This outlines the objective of this work.

The two examples below goes with the following 3 tables, `R`, `T` and `S`.

| Table R | k    | a    | b    | c    |
| :------ | :--- | :--- | :--- | :--- |
|         | r    | 1    | 1    | 1    |

| Table S | k    | a    | b    |
| :------ | :--- | :--- | :--- |
|         | s    | 1    | 1    |

| Table T | k    | a    | c    |
| :------ | :--- | :--- | :--- |
| N/A     | -    | -    | -    |

#### Example 1 => 结合律不起作用

1)

```sql
S INNER JOIN T ON S.a = T.a
```

will result in empty data.

2)

```sql
-- 1 => S INNER JOIN T ON S.a = T.a
R LEFT JOIN (1) ON R.b = S.b AND R.c = S.c
```

will result in

| k    | a    | b    | c    | k    | a    | b    | k    | a    | c    |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| r    | 1    | 1    | 1    | -    | -    | -    | -    | -    | -    |

##### Example 1 reordered

1)

```sql
R LEFT JOIN S ON R.b = S.b
```

will result in

| k    | a    | b    | c    | k    | a    | b    |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| r    | 1    | 1    | 1    | s    | 1    | 1    |

2)

```sql
-- 1 => R LEFT JOIN S ON R.b = S.b
(1) INNER JOIN T S.a = T.a AND R.c = T.c
```

will result in empty data.

#### Example 2 交换律不起作用

```sql
-- 1
S LEFT JOIN T ON S.a = T.a
```

will result in

| k    | a    | b    | k    | a    | c    |
| :--- | :--- | :--- | :--- | :--- | :--- |
| s    | 1    | 1    | -    | -    | -    |

2)

```sql
-- 1 => S LEFT JOIN T ON S.a = T.a
R LEFT JOIN (1) ON R.a = S.a
```

will result in

| k    | a    | b    | c    | k    | a    | b    | k    | a    | c    |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| r    | 1    | 1    | 1    | s    | 1    | 1    | -    | -    | -    |

##### Example 2 reordered

```sql
- 1
R LEFT JOIN T ON R.a = T.a
```

will result in

| k    | a    | b    | k    | a    | c    |
| :--- | :--- | :--- | :--- | :--- | :--- |
| s    | 1    | 1    | -    | -    | -    |

2)

```sql
-- 1 R LEFT JOIN T ON R.a = T.a
(1) LEFT JOIN S ON R.a = S.a and T.a = S.a
```

will result in

| k    | a    | b    | c    | k    | a    | b    | k    | a    | c    |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| -    | -    | -    | -    | s    | 1    | 1    | -    | -    | -    |

> Thus, we can see that the re-ordering of both R1 and R2 are invalid.

To solve the problem, this paper proposes a canonical abstraction for queries involving both inner and outer joins by 3 operators: *outer Cartesian products* × (conventional Cartesian products + if either operand is empty, perform an outer join so that the other side is fully preserved), *nullification* 𝞴 (for rows that don’t satisfy the nullification predicate `P`, set their nullified attributes `a` to `null`) and *best match* 𝞫 (a filter for rows in a table such that only those which are not dominated by other rows, and also not all-null themselves are left). Notice that such canonical abstraction would maintain both commutativity and transitivity, after which makes it much easier to find the optimal plan. This abstraction for left outerjoin and inner join is shown as follows.

![Canonical abstraction for inner join and left outer join](https://yunpengn.github.io/blog/images/join_order_canonical_abstraction.png)

We can easily understand the rationale behind the relation above (we call this representation *“bestmatch-nullification representation”*, shortened as `BNR`). Let’s take the left outerjoin for an example. In a left outerjoin, the left operand is the *preserving side* while the right operand is the *null-producing side*. Thus, we have to nullify the right operand `S` with the predicate `P` (similarly, in an inner join, we need to nullify both operands). To prevent the results from containing spurious tuples, we further apply the best match operator. The image below summarizes some commutative rules for the two compensation operators.

![Commutative rules for compensation operators](https://yunpengn.github.io/blog/images/join_order_compensation_commutative.png)

Notice that although the nullification operator is not interchangable (commutativity), we can add another nullification operator to short-circuit the ripple effect and fix this.

### TaiNing, W., & Chee-Yong, Chan. (2018). Improving Join Reorderability with Compensation Operators. doi:10.1145/3183713.3183731

This recent work extends the paper in 2004 by following a similar compensation-based approach (CBA) to solve the join reordering problem. In a simple query, all outerjoin predicates have only one conjunct, must be binary predicate referring to only 2 tables, no Cartesian product, and all predicates are null-intolerant. This paper also provides complete join reorderability for single-sided outerjoin, antijoins. For full outerjoin, the approach in this paper is better than previous work.

To formalize the notion of complete join reorderability, the join order is modelled as an unordered binary tree, with leaf nodes as the relations and internal nodes as the predicates & join operators. In this definition, the join ordering focuses on the order of all operands rather than the specific join operators used. In other words, to achieve a certain join order, we could possibly change the join operators used. Given a query class `C` and a set of compensation operators `O`, `C` is completely reorderable with respect to `O` if `O` can help every query `Q` in `C` to reorder to every possible order in `JoinOrder(Q)`. Thus, this paper further purposes an Enhanced Compensation-based Approach (ECA), to enable reordering support for antijoins (by adding 2 more compensation operators). Specifically, antijoins are rewritten in the following way.

![Rewriting rule for antijoins](https://yunpengn.github.io/blog/images/join_order_antijoin.png)

The above rule makes sense since the gamme operator basically removes those rows in `R1` which could otherwise join with some row(s) from `R2` (notice that a left antijoin basically means `R1` - `R1` left semijoin `R2`; and left semijoin means a projection to only include left operand attributes after a natural join). This two-step approach enables the pruning step to be postponed. The design of ECA has 4 desirable factors:

- The operators must be able to maximize the join reorderability;
- An efficient query plan enumeration algorithm;
- The number of compensation operators should be small;
- There exists efficient implementation to each compensation operator (both SQL level and system native level).

This paper introduces two new operators 𝞬 and 𝞬* . The first operator 𝞬 removes all tuples where the projection of a certain subset of attributes `A` is not null. The second operator 𝞬* modifies those tuples not selected by first operator by setting their attributes (excluding those in the subset of attributes to `B`) and then merge the two parts together. These two operators could be interchanged with conventional join operators as shown in the table below.

![The 2 new compensation operators](https://yunpengn.github.io/blog/images/join_order_gamma.png)

The above properties in fact lead to more rewriting rules as compared to the original CBA approach. The rules are shown in the table as follows.

![The rewriting rules for ECA approach](https://yunpengn.github.io/blog/images/join_order_ECA_rules.png)

## References

- [Cost Based Transformation](https://slideplayer.com/slide/7520334/)
- [NUS CS3223 Lecture Notes - Relational Operators](https://www.comp.nus.edu.sg/~tankl/cs3223/slides/opr.pdf)
- [NUS CS3223 Lecture Notes - Query Optimizer](https://www.comp.nus.edu.sg/~tankl/cs3223/slides/opt.pdf)
- [Optimizing Join Orders](http://www.benjaminnevarez.com/2010/06/optimizing-join-orders/)