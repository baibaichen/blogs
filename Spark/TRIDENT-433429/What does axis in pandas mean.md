# Merge, join, and concatenate
pandas provides various facilities for easily combining together `Series`, `DataFrame`, and `Panel` objects with various kinds of **set logic for the indexes** and **relational algebra functionality** in the case of join / merge-type operations.

## Concatenating objects

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


