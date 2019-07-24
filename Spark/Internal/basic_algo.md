# 基本算法

## 闭包变量如何传递？

看下面的代码

- **1**处是在**drvier**端定义变量`eventTimeStats`
- **2**处对`eventTimeStats.add(...)`调用是在**worker**端

```scala
case class EventTimeWatermarkExec {

  val eventTimeStats = new EventTimeStatsAccum()                 // 1.
  sparkContext.register(eventTimeStats)

  override protected def doExecute(): RDD[InternalRow] = {
    child.execute().mapPartitions { iter =>
      //...
      iter.map { row =>
        eventTimeStats.add(getEventTime(row).getLong(0) / 1000)  // 2.
        row
      }
    }
  }
 // ....
}  
```

看起来是在写本地的代码，实际是分布式的。

## 累加器V2

累加器，等价于从worker像master传数据

1. [Simplify accumulators and task metrics](https://issues.apache.org/jira/browse/SPARK-14626)
   2. [New accumulator API](https://issues.apache.org/jira/browse/SPARK-14654)
3. [Look into whether accumulator mechanism can replace TaskMetrics](https://issues.apache.org/jira/browse/SPARK-10620) 有相关的文档

## 表达式

一组可用于表示关系表达式树的类。表达式库的一个关键目标，是为操纵关系运算符树的开发人员隐藏命名和作用域的细节。因此，除了标准的表达式之外，还定义了一种特殊类型的表达式，[[NamedExpression]]。

==标准表达式==
标准表达式库（例如，`Add`，`EqualTo`），聚合（例如，`SUM`，`COUNT`）和其他计算（例如`UDF`）。每个表达式的类型，都可以根据其子表达式的输出schema以确定其自身的输出schema。

==命名表达式==
某些表达式被命名，因此可以被数据流图中的后续运算符引用。有两种类型的命名表达式：`AttributeReference`和`Alias`。`AttributeReference`引用给定运算符输入元组的属性，并形成某些表达式树的叶子。`Alias`为中间计算指定名称，例如，在SQL语句`SELECT a + b AS c FROM ...`中，表达式`a`和`b`将由`AttributeReference`表示，而`c`将由`Alias`表示。

在[分析](package analysis)期间，为所有命名表达式分配一个全局唯一**表达式id**，用于相等比较。虽然出于调试目的而保留原始名称，但它们永远不能用于检查两个属性是否引用相同的值，因为**计划转换**可能引入**命名模糊性**。例如，考虑一个包含两个子查询的计划，这两个子查询都是从同一个表中读取的。如果优化删除子查询，则会破坏作用域信息，从而无法推断哪个子查询生成了给定属性。

==计算==
可以使用`Expression.apply(Row)`方法计算表达式的结果。

![表达式体系](https://g.gravizo.com/source/custom_mark00?https://raw.githubusercontent.com/baibaichen/blogs/master/Spark/Internal/basic_algo.md)
<details> 
<summary></summary>
custom_mark00
digraph G {
    node  [shape=box]
    rankdir = BT
    Alias->UnaryExpression->Expression [arrowhead=empty]
    AttributeReference->Attribute->LeafExpression->Expression [arrowhead=empty]
    Alias->NamedExpression [penwidth=3]
    NamedExpression->Expression [arrowhead=empty]
    Attribute->NamedExpression [penwidth=3]
    Attribute->NullIntolerant [penwidth=3]
    NullIntolerant->Expression [arrowhead=empty]
    AttributeReference->Unevaluable[penwidth=3]
    Unevaluable->Expression [arrowhead=empty]
}
custom_mark00
</details>

###  计算，代码生成

[Spark Catalyst的实现分析](https://github.com/ColZer/DigAndBuried/blob/master/spark/spark-catalyst.md#spark-catalyst的实现分析)

1. Attribute
2. BoundReference







