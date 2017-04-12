# Merge, join, and concatenate
pandas provides various facilities for easily combining together `Series`, `DataFrame`, and `Panel` objects with various kinds of **set logic for the indexes** and **relational algebra functionality** in the case of join / merge-type operations.

## Concatenating objects

沿着一条轴将多个对象**堆叠**在一起

The `concat` function (in the main pandas namespace) does all of the heavy lifting of performing concatenation operations **along an axis** while performing optional **set logic** (union or intersection) of the indexes (if any) on the other axes. 

> 沿着一条轴上执行连接操作
>
> 执行其他轴上（如果有的话）索引可选的集合操作（并集和交集）
>
> Note:
>    That I say “if any” because there is only a single possible axis of concatenation for Series.
>
> **What does axis in pandas mean?**
> It specifies the axis **along which** the means are computed. By default `axis=0`. This is consistent with the `numpy.mean` usage when `axis` is specified *explicitly* (in `numpy.mean`, axis==None by default, which computes the mean value over the flattened array) , in which `axis=0` along the *rows* (namely, *index* in pandas), and `axis=1` along the *columns*.
>
> ```
> +------------+---------+--------+
> |            |  A      |  B     |
> +------------+---------+---------
> |      0     | 0.626386| 1.52325|----axis=1----->
> +------------+---------+--------+
>              |         |
>              | axis=0  |
>              ↓         ↓
> ```

```python
pd.concat(objs, axis=0, join='outer', join_axes=None, ignore_index=False,
          keys=None, levels=None, names=None, verify_integrity=False,
          copy=True)
```

- [x] `objs` : a sequence or mapping of Series, DataFrame, or Panel objects. If a dict is passed, the sorted keys will be used as the keys argument, unless it is passed, in which case the values will be selected (see below). Any None objects will be dropped silently unless they are all None in which case a ValueError will be raised.
- [x] `axis` : {0, 1, ...}, default 0. The axis to concatenate along.
- [x] `join` : {‘inner’, ‘outer’}, default ‘outer’. How to handle indexes on other axis(es). Outer for union and inner for intersection.
- [ ] `ignore_index` : boolean, default False. If True, do not use the index values on the concatenation axis. The resulting axis will be labeled 0, ..., n - 1. This is useful if you are concatenating objects where the concatenation axis does not have meaningful indexing information. Note the index values on the other axes are still respected in the join.
- [ ] `join_axes` : list of Index objects. Specific indexes to use for the other n - 1 axes instead of performing inner/outer set logic. see [concatenating, pandas, and join_axes](http://stackoverflow.com/questions/27391081/concatenating-pandas-and-join-axes)
- [ ] `keys` : sequence, default None. Construct hierarchical index using the passed keys as the outermost level. If multiple levels passed, should contain tuples.
- [ ] `levels` : list of sequences, default None. Specific levels (unique values) to use for constructing a MultiIndex. Otherwise they will be inferred from the keys.
- [ ] `names` : list, default None. Names for the levels in the resulting hierarchical index.
- [ ] `verify_integrity` : boolean, default False. Check whether the new concatenated axis contains duplicates. This can be very expensive relative to the actual data concatenation.
- [ ] `copy` : boolean, default True. If False, do not copy data unnecessarily.

Without a little bit of context and example many of these arguments don’t make much sense. Let’s take the above example. Suppose we wanted to associate specific keys with each of the pieces of the chopped up DataFrame. We can do this using the `keys` argument:



```python
from pandas import Series, DataFrame
import pandas as pd
s1 = Series([0, 1], index=['a', 'b'])
s2 = Series([2, 3, 4], index=['c', 'd', 'e'])
s3 = Series([5, 6], index=['f', 'g'])
```

[Pandas | 表格整合三大神技之CONCATENATE](https://zhuanlan.zhihu.com/p/24892205)



# Spark中的实现

1. 在axis = 0 方向使用`union`
2. 在axis = 1 方向使用`zip`

## SQL UNION 操作符

UNION 操作符用于合并两个或多个 SELECT 语句的结果集。

请注意，UNION 内部的 SELECT 语句必须拥有相同数量的列。列也必须拥有相似的数据类型。同时，每条 SELECT 语句中的列的顺序必须相同。

> SQL 的 `union` 和 `union all`的区别是`union all`不去重，但是spark的 `DataSet.unionAll` 和 `DataSet.union` 没有区别。

https://issues.apache.org/jira/browse/SPARK-7460