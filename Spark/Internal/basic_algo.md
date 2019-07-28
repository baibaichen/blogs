# 基本算法

参考

1. https://www.jianshu.com/p/a3c1add7466d

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

一组可用于表示关系表达式树的类。表达式库的一个关键目标，是为操纵关系运算符树的开发人员隐藏命名和作用域的细节。因此，除了标准的表达式之外，还定义了一种特殊类型的表达式，`NamedExpression`。

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
    BoundReference[color=lightgrey style=filled]
    AttributeReference[color=lightgrey style=filled]
    Alias[color=lightgrey style=filled]
    Alias->UnaryExpression->Expression [arrowhead=empty]
    AttributeReference->Attribute->LeafExpression->Expression [arrowhead=empty]
    Alias->NamedExpression [penwidth=3]
    NamedExpression->Expression [arrowhead=empty]
    Attribute->NamedExpression [penwidth=3]
    Attribute->NullIntolerant [penwidth=3]
    NullIntolerant->Expression [arrowhead=empty]
    AttributeReference->Unevaluable[penwidth=3]
    Unevaluable->Expression [arrowhead=empty]
    BoundReference->LeafExpression[arrowhead=empty]
}
custom_mark00
</details>

<img src='https://g.gravizo.com/svg?
digraph G {
    node  [shape=box]
    rankdir = BT
    Last[color=lightgrey style=filled]
    HyperLogLogPlusPlus[color=lightgrey style=filled]
    TypedAggregateExpression[color=chartreuse style=filled]
    Serializable[color=chartreuse style=filled]
    Unevaluable[color=chartreuse style=filled]
    CodegenFallback[color=chartreuse style=filled]
    {rank=same Expression Serializable}
    {rank=same AggregateFunction AggregateExpression}
    {rank=same DeclarativeAggregate ImperativeAggregate TypedAggregateExpression}
    AggregateExpression->Expression [arrowhead=empty]
    AggregateExpression->Unevaluable[penwidth=3]
    AggregateExpression->AggregateFunction [dir=both arrowtail = diamond label="aggregateFunction"]
    AggregateFunction->Expression [arrowhead=empty]
    Unevaluable->Expression [arrowhead=empty]
    CodegenFallback->Expression [arrowhead=empty]
    DeclarativeAggregate->AggregateFunction[arrowhead=empty]
    ImperativeAggregate->AggregateFunction[arrowhead=empty]
    TypedAggregateExpression->AggregateFunction[arrowhead=empty]
    DeclarativeAggregate->Serializable[penwidth=3]
    DeclarativeAggregate->Unevaluable[penwidth=3]
    ImperativeAggregate->CodegenFallback[penwidth=3]
    Last->DeclarativeAggregate[arrowhead=empty]
    HyperLogLogPlusPlus->ImperativeAggregate[arrowhead=empty]
}
'>

###  代码生成

代码生成分为两部分，一部分是最基本的表达式代码生成，另一部分称为全阶段代码生成，用来将多个处理器逻辑整合到单个代码模块中。

代码生成的实现中`CodegenContext`可以算是最重要的类，`CodegenContext`作为代码生成的上下文，记录了将要生成的代码中的各种元素，包括变量、函数等。如图9.22所示，可以将`CodegenContext`中的元素分为几个大的类别。

1. **变量相关**
   1. RDD partition 相关
   2. 引用
2. 函数
3. 数据类型
4. 工具函数

----

https://github.com/apache/spark/pull/10735 whole stage gen

[Spark Catalyst的实现分析](https://github.com/ColZer/DigAndBuried/blob/master/spark/spark-catalyst.md#spark-catalyst的实现分析)


``` scala
// BucketedReadSuite
//...
    val getBucketId = UnsafeProjection.create(
        HashPartitioning(attrs, 8).partitionIdExpression :: Nil, // 1.
          output)  // 2.
```
1. 上述代码**1**处，是一个`Expression`
2. 上述代码**2**处，output 输入的schema
3. 真正生成代码之前，需要将1和2 转换成`BoundReference`，`BoundReference`指向输入元组中的特定槽，允许更有效地检索实际值。 见下面的代码**3**处：

    ```scala
    def create(exprs: Seq[Expression], inputSchema: Seq[Attribute]): UnsafeProjection = {
      create(toBoundExprs(exprs, inputSchema))  // 3
    }
    ```

生成的java代码如下：
```java
public java.lang.Object generate(Object[] references) {
	return new SpecificUnsafeProjection(references);
}

class SpecificUnsafeProjection
 extends org.apache.spark.sql.catalyst.expressions.UnsafeProjection {

	private Object[] references;
	private org.apache.spark.sql.catalyst.expressions.codegen.UnsafeRowWriter[] mutableStateArray_0 = 
	new org.apache.spark.sql.catalyst.expressions.codegen.UnsafeRowWriter[1];

	public SpecificUnsafeProjection(Object[] references) {
		this.references = references;
		mutableStateArray_0[0] = new org.apache.spark.sql.catalyst.expressions.codegen.UnsafeRowWriter(1, 0);
	}
	public void initialize(int partitionIndex) {}

	// Scala.Function1 need this
	public java.lang.Object apply(java.lang.Object row) {
		return apply((InternalRow) row);
	}

	public UnsafeRow apply(InternalRow i) {

		mutableStateArray_0[0].reset();
		mutableStateArray_0[0].zeroOutNullBytes();

		boolean isNull_0 = false;
		int value_0 = -1;

		if (8 == 0) {
			isNull_0 = true;
		} else {
			int value_1 = 42;
			boolean isNull_2 = i.isNullAt(0);
			int value_2 = isNull_2 ?-1 : (i.getInt(0));
			if (!isNull_2) {
				value_1 = org.apache.spark.unsafe.hash.Murmur3_x86_32.hashInt(value_2, value_1);
			}
			boolean isNull_3 = i.isNullAt(1);
			UTF8String value_3 = isNull_3 ?null : (i.getUTF8String(1));
			if (!isNull_3) {
				value_1 = 
				org.apache.spark.unsafe.hash.Murmur3_x86_32.hashUnsafeBytes(
					value_3.getBaseObject(), value_3.getBaseOffset(), value_3.numBytes(), value_1);
			}
			int remainder_0 = value_1 % 8;
			if (remainder_0 < 0) {
				value_0=(remainder_0 + 8) % 8;
			} else {
				value_0=remainder_0;
			}
		}

		if (isNull_0) {
			mutableStateArray_0[0].setNullAt(0);
		} else {
			mutableStateArray_0[0].write(0, value_0);
		}
		return (mutableStateArray_0[0].getRow());
	}
}
```










