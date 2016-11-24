https://databricks.com/blog/2016/02/01/faster-stateful-stream-processing-in-apache-spark-streaming.html
1. [很像Java](#1-很像Java)
1. 统一的对象模型
1. 函数也是**第一等级的值**
1. [统一的抽象模型【Scala has uniform and powerful abstraction concepts for both types and values】](#4-统一的抽象模型【Scala has uniform and powerful abstraction concepts for both types and values】)
1. OOP，特征混入（trait mixin）
1. 模式匹配
1. 隐式转换

> TODO
> - [ ] 知乎上 [[Scala 是一门怎样的语言，具有哪些优缺点？]](https://www.zhihu.com/question/19748408) 得票最高的答案中的如下链接
>   * **Scala集合库的性能**，[视频在这](https://www.youtube.com/watch?v=tRiv35gNPoI&spfreload=10)。 讲的是Scala集合的运行速度，是一个来自Goldman Sachs的程序员讲他们为Java写的集合库（GSCollection）速度和内存消耗，但同时比较了[gs-collection](https://github.com/goldmansachs/gs-collections)，Java，和Scala库的速度。最后Scala的可变集合mutable原生库完爆Java，和gs-collection基本持平。
>   * **为了追求速度**，Scala社区是绝对不会管所谓的“简单”或者是“好玩”，怎样有效率就怎样弄。与其专注于JVM的改进，Scala社区大部分在编译器上下功夫，比如很著名的 [Miniboxing](http://scala-miniboxing.org/)，这是一个编译器增进器。Miniboxing做的是什么呢？只做一件事：防止auto-boxing和auto-unboxing。所有的泛型，尤其是原生类泛型（Primitive Types），诸如Int、Double等等，在进行各种操作的时候会自动取出和装回它们所属的类中去——这个我解释的不太好，但是可以看[这里(Java 的自动装箱与拆箱)](http://www.cnblogs.com/danne823/archive/2011/04/22/2025332.html)
> - 『自动装箱和拆箱』引出来 `@specialized`（可以参考 [Specializing for primitive types](http://www.scala-notes.org/2011/04/specializing-for-primitive-types/)），但这个功能用的并不多（泛型集合库没用，参见 *Programming Scala 2nd* 的8.2和12.4节，以及 [Why are so few things @specialized in Scala's standard library?](http://stackoverflow.com/questions/5477675/why-are-so-few-things-specialized-in-scalas-standard-library)），因此引出了 [Miniboxing](http://scala-miniboxing.org/)。
> - Java不允许使用**原语类型**（如 `int`）做参数类型，Scala 则**在语法层面**允许
>   ```scala
>      def doubleInt(x: Int) = x * 2
>      
>      class Reference[T] {
>        private var contents: T = _
>        def set(value: T) { contents = value }
>        def get: T = contents
>      }
>   ```
>   语法层面上，确实可以用 Scala.Int（对应 Java 的 int）做为类型参数，但在运行时还是会『自动装箱和拆箱』
>   ```scala
>      val cell = new Reference[Int]     // 实际上对应的是 Reference<Integer>
>      cell.set(13)                      // 运行时会分别调用 boxToInteger
>      doubleInt(cell.get)               // 和 unboxToInt
>   ```
>   上面的代码实际是：
>   ```scala
>      cell.set(BoxesRunTime.boxToInteger(13))
>      doubleInt(BoxesRunTime.unboxToInt(cell.get()))
>   ```

## 1 很像Java

- [Java to Scala cheatsheet](http://rea.tech/java-to-scala-cheatsheet/) 
- [Java developer's Scala cheatsheet](http://mbonaci.github.io/scala/)
- [谈谈Scala中的枚举](http://kubicode.me/2015/06/06/Scala/Enum-in-Scala/)

### 语句和表达式

语句没有值，比如`Class`定义，`Object`定义，`trait`定义，以及变量声明。反之，表达式有值，下面的几个语法构造在其它语言里是语句，在Scala中是表达式
- if-else
- while 和 for 循环
- throw

## 2 统一的对象模型

![image](https://raw.githubusercontent.com/mbonaci/scala/master/resources/Scala-class-hierarchy.gif)

    AnyRef
      ==
      !=
      eq
      ne

## 3 函数也是**第一等级的值**

## 4 统一的抽象模型【Scala has uniform and powerful abstraction concepts for both types and values】
1. 参数化类型，即，泛型
2. 抽象类型

> Progamming Scala 第14.5节

**`参数化类型`**可以很好地用于**集合这类**容器中，此时，**类型参数所表示的元素类型和容器并没有什么联系**。举例来说，字符串列表、浮点数列表和整数列表的工作方式都相同。如果我们将其换成`抽象类型`会如何？以下 `Some` 的声明是从标准库中摘录的：
```scala
case final class Some[+A](val value : A) { ... }
```
如果我们试图将其换成`抽象类型`，那么
```scala
case final class Some(val value : ???) {
  type A
  ... 
}
```
由于类型 `A` 不在`构造函数`参数列表的作用域内，所以，我们无法表达参数 `value` 的类型。当需要在`构造函数`中使用类型的参数时，唯一合适的就是选择就是`参数化类型`

相反，`抽象类型`在相互联系密切的“类型家族”中则非常有用

> [Scala类型系统的目的——Martin Odersky访谈（三）](http://www.infoq.com/cn/articles/scala-type-system)，搜索**抽象类型成员**
> 
> **Bill Venners**: 在Scala中，一个类型可以是另一种类型的内部成员，正如方法和字段可以是类型的内部成员。而且，Scala中的这些类型成员可以是抽象成员，就像Java方法那样抽象。那么抽象类型成员和泛型参数不就成了重复功能吗？为什么Scala两个功能都支持？抽象类型，相比泛型，能额外给你们带来什么好处？
> 
> **Martin Odersky**: 抽象类型，相比泛型，的确有些额外好处。不过还是让我先说几句通用原理吧。对于抽象，业界一直有两套不同机制：参数化和抽象成员。Java也一样支持两套抽象，只不过Java的两套抽象取决于对什么进行抽象。**Java支持抽象方法，但不支持把方法作为参数；Java不支持抽象字段，但支持把值作为参数；Java不支持抽象类型成员，但支持把类型作为参数**。所以，在Java中，三者都可以抽象。但是对三者进行抽象时，原理有所区别。所以，你可以批判Java，三者区别太过武断。
> 
> 我们在Scala中，试图把这些抽象支持得更完备、更正交。我们决定对上述三类成员都采用相同的构造原理。**所以，你既可以使用抽象字段，也可以使用值参数；既可以把方法（即“函数”）作为参数，也可以声明抽象方法；既可以指定类型参数也可以声明抽象类型**。总之，我们找到了三者的统一概念，可以按某一类成员的相同用法来使用另一类成员。至少在原则上，我们可以用同一种面向对象抽象成员的形式，表达全部三类参数。因此，在某种意义上可以说Scala是一种更正交、更完备的语言。
> 
> 现在的问题来了，这对你有什么好处？具体到抽象类型，能带来的好处是，它能很好地处理我们先前谈到的协变问题。举个老生常谈的例子：动物和食物的问题。**这道题是这样的：从前有个Animal类，其中有个eat方法，可以用来吃东西。问题是，如果从Animal派生出一个类，比如Cow，那么就只能吃某一种食物，比如Grass。Cow不可以吃Fish之类的其他食物。你希望有办法可以声明，Cow拥有一个eat方法，且该方法只能用来吃Grass，而不能吃其他东西**。实际上，这个需求在Java中实现不了，因为你最终一定会构造出有矛盾的情形，类似我先前讲过的把Fruit赋值给Apple一样。
> 
> 请问你该怎么做？Scala的答案是，在Animal类中增加一个抽象类型成员。比方说，Scala版的Animal类内部可以声明一个SuitableFood类型，但不定义它具体是什么。那么这就是抽象类型。你不给出类型实现，直接让Animal的eat方法吃下SuitableFood即可。然后，在Cow中声明：“好！这是一只Cow，派生自Animal。对Cow来说，其SuitableFood是Grass。”所以，抽象类型提供了一种机制：先在父类中声明未知类型，稍后再在子类中填上某种已知类型。
> 
> 现在你可能会说，哎呀，我用参数也可以实现同样功能。确实可以。你可以给Animal增加参数，表示它能吃的食物。但实践中，当你需要支持许多不同功能时，就会导致参数爆炸。而且通常情况下，更要命的问题是，参数的边界。在1998年的ECOOP（欧洲面向对象编程会议）上，我和Kim Bruce、Phil Wadler发表了一篇论文。我们证明，当你线性增加未知概念数量时，一般来说程序规模会呈二次方增长。所以，我们有了很好的理由不用参数而用抽象成员，即为了避免二次方级别的代码膨胀。

## 5 OOP

> Patterns -> Abstractions
> Constraints -> Specifications | Contracts | Types

### 快学Scala的第5章和第6章

> TODO，根据 Spark 的 `Dataset` 来整理内容

### 单例对象

 -   |静态成员
 --- |---
Scala|不支持，以单例对象替代
 Java|支持

- Scala **不能定义静态成员**，这是比 Java 更为面向对象的地方
- 除了用 **`object`** 关键字替换 `class` 关键字之外，定义单例对象看上去和定义类一致
- 和『类同名的单例对象』称之为**伴生对象**，类和它的伴生对象
  * 必须定义在一个源文件里
  * 可以互相访问私有成员
- 单例对象不带参数，而类可以。这是因为单例对象不是用 `new` 关键字实例化的，所以没有机会给它传参数


> TODO 例子
> - [ ] 每个单例对象都被实现为虚构类（**synthetic class**）的实例，并指向静态的变量，因此它们与 Java 静态类有着相同的初始化语义
> - [ ] 虚构类（**synthetic class**）是对象名加上一个美元符号 **$**

### Cake Pattern(DI)
> TODO
> - [ ] [Dependency Injection in Scala](http://blog.yunglinho.com/blog/2012/04/22/dependency-injection-in-scala/)
> - [ ] [Cake Pattern Resources](http://scabl.blogspot.com/p/cbdi.html)
> - [ ] Cake Pattern 现在还在用吗？Spark里能找到对应的例子吗？

### 特征混入（`trait` mixin）
Spark的 `StructType` 混入了 `Seq[StrcutField]`

```scala
case class StructType(fields: Array[StructField]) extends DataType with Seq[StructField] {
    override def apply(fieldIndex: Int): StructField = fields(fieldIndex)
    override def length: Int = fields.length
    override def iterator: Iterator[StructField] = fields.iterator
}
```

## 6 模式匹配

> TODO：整理下面的内容
> - [ ] [An Introduction to Pattern Matching in Scala](https://newcircle.com/s/post/1696/an_Introduction_to_pattern_matching_in_scala_brian_clapper_tutorial)
> - [ ] [Case Classes and Pattern Matching](http://www.artima.com/pins1ed/case-classes-and-pattern-matching.html)（*Scala 编程*的第15章）
> - [ ] Case语句的中置表式法（参见*快学 Scala *的**14.11**节）
> - [ ] [[PDF] Matching Objects with patterns](https://infoscience.epfl.ch/record/98468/files/MatchingObjectsWithPatterns-TR.pdf)
>
> 特别是这篇PDF需要认真整理


# 其它

## 错误处理
- [Error Handling in Scala](https://tersesystems.com/2012/12/27/error-handling-in-scala/)
- [Option](https://windor.gitbooks.io/beginners-guide-to-scala/content/chp5-the-option-type.html)
- [Try](https://windor.gitbooks.io/beginners-guide-to-scala/content/chp6-error-handling-with-try.html)
- [Either](https://windor.gitbooks.io/beginners-guide-to-scala/content/chp7-the-either-type.html)

## 集合

1. [ ] [性能特点](http://docs.scala-lang.org/zh-cn/overviews/collections/performance-characteristics)

### [集合的架构](http://docs.scala-lang.org/overviews/core/architecture-of-scala-collections.html)

To summarize, if you want to fully integrate a new collection class into the framework you need to pay attention to the following points:

1. Decide whether the collection should be mutable or immutable.
1. Pick the right base traits for the collection.
1. Inherit from the right implementation trait to implement most collection operations.
1. If you want map and similar operations to return instances of your collection type, provide an implicit CanBuildFrom in your class’s companion object.

You have now seen how Scala’s collections are built and how you can add new kinds of collections. Because of Scala’s rich support for abstraction, each new collection type has a large number of methods without having to reimplement them all over again.

https://github.com/scala/scala/commit/3de96153e5bfbde16dcc89bfbd71ff6e8cf1f6c6


```
TraversableOnce --> GenTraversableOnce
  ^                    ^
  |                    |
Traversable     --> GenTraversable
  ^                    ^
  |                    |
Iterable        --> GenIterable        <-- ParIterable
  ^                    ^                      ^
  |                    |                      |
Seq             --> GenSeq             <-- ParSeq


TraversableOnce --> GenTraversableOnce
  ^                    ^                              |--------------|
  |      |-------------|--------------------------->  |Parallelizable|
TraversableLike --> GenTraversableLike------------->  |--------------|
  ^         ^          ^           ^
  |         |          |           |                  |--------------------------|
  |      |--|----------|-----------|----------------> |GenericTraversableTemplate|
Traversable |   --> GenTraversable |  --------------> |--------------------------|
  ^         |        ^             |                            ^
  |  IterableLike    |-->  GenIterableLike                      |
  |   ^              |      ^                                   |
  |   |              |      |                                   |
Iterable        --> GenIterable---------------------------------|
   |------------------------------------------------------------|
```

> [Traversable]
> Any HasNewBuilder FilterMonadic GenTraversableOnce TraversableOnce Parallelizable GenTraversableLike TraversableLike  GenericTraversableTemplate GenTraversable Traversable

> [Iterable]
> Any HasNewBuilder FilterMonadic GenTraversableOnce TraversableOnce Parallelizable GenTraversableLike TraversableLike  GenericTraversableTemplate GenTraversable Traversable GenIterableLike
GenIterable Equals IterableLike Iterable

> [AbstractIterable]
> Any HasNewBuilder FilterMonadic GenTraversableOnce TraversableOnce Parallelizable GenTraversableLike TraversableLike  GenericTraversableTemplate GenTraversable Traversable AbstractTraversable GenIterableLike GenIterable Equals IterableLike Iterable AbstractIterable


### [数组](http://docs.scala-lang.org/zh-cn/overviews/collections/arrays)

在Scala中，数组是一种特殊的collection。一方面，Scala数组与Java数组是一一对应的。即Scala数组Array[Int]可看作Java的Int[]，Array[Double]可看作Java的double[]，以及Array[String]可看作Java的String[]。但Scala数组比Java数组提供了更多内容。
- 首先，Scala数组是一种泛型。即可以定义一个Array[T]，T可以是一种类型参数或抽象类型。
- 其次，Scala数组与Scala序列是兼容的 - 在需要Seq[T]的地方可由Array[T]代替。
- 最后，Scala数组支持所有的序列操作

#### 创建【编译器有特殊支持】

如果**类型已知**，比如下面的代码：

    val a1 = new Array[Int](10)
    val a2 = Array(1, 2, 3)
    a1(1)=a2(2)

编译器会直接翻译成如下的Java代码

    int[] a1 = new int[10];
    int[] a2 = { 1, 2, 3, 4 };
    a1[1] = a2[2];
    
如果**类型未知**，比如：

    def evenElems[T: ClassTag](xs: Vector[T]): Array[T] = {
      val arr = new Array[T]((xs.length + 1) / 2) // 代码1
      for (i <- 0 until xs.length by 2)           // 这个代码有可读性吗?
        arr(i / 2) = xs(i)                        // 代码2
        arr
    }
    
函数必须要声明一个`ClassTag[T]`的隐式参数（具体的隐式值由编译器提供），这时代码会被翻译成:

    final Object arr = evidence$1.newArray((xs.length() + 1) / 2); //代码1，注意evidence$1就是传进来的隐式值
    ScalaRunTime.array_update(arr, i/2, xs.apply(2))               //代码2，并不是arr.update(i/2)这样的标准语法

`ClassTag.newArry()`会根据**类型T的运行时信息**，在**运行时**选择恰当的方法创建数组

### 不变集合的数据结构
> TODO
> - [ ] 调研 Spark 中的 `TreeNode`
>   * `TreeNode` 为什么要从 `Product` 扩展？
>   * `TreeNode` 类层次是什么？
>   * `makeCopy`的实现原理，用到了那些和 runtime 相关的方法？
>   * **persistent data structure** 是如何实现的？


# 例子

#### 变量可以 `override` 方法

    RuleExecutor/*...*/ {
       abstract class Strategy { def maxIterations: Int }
       
       case class FixedPoint(maxIterations: Int) extends Strategy
    }

在超类中实际定义的是 `maxIterations` 方法，但是在 'FixedPoint` 子类中，我们定义的是一个**变量**！

#### Loop没有`break`和`continue`

首先，Scala的 `for` **和Java不一样，其次语言本身不推荐使用手写的循环，所以没有 `break` 和 `continue`， 目的是使之不好用。 但有时候需要 `break` 或者 `continue`，一般用 `while` 模拟实现，参见[`RuleExecutor.execute`]()，摘取的代码如下：

    var continue = true
    while(continue){
      if( ... ){
        ....
        continue = false  // 如果是Java 使用break
      }
    }
    
#### 语法糖，大括号里的case语句是[偏函数]()

注意，Scala集合中的`flodLeft`的声明是这样的：

    /* 
     * 1. 按科里化方式声明flodLeft
     * 2. 第一个参数：类型为B的初始值
     * 3. 第二个参数：类型是二元函数，其参数类型分别为B，A，返回类型是B
     */
    def foldLeft[B](z: B)(op: (B, A) => B): B 

参见[`RuleExecutor.execute`]()，摘取的代码如下

    curPlan = batch.rules.foldLeft(curPlan) {
      case (plan, rule) =>
        ...
        val result = rule(plan)
        ...
        result
    }

去除语法糖之后，其实是这样的

    curPlan = batch.rules.foldLeft(curPlan) { 
      new AbstractFunction2(){
        public final TreeType apply(TreeType plan, Rule<TreeType> rule){
          ....
          //偏函数，如果前面不能匹配成功，运行到这会抛除 MatchError 异常
          throw new MatchError(localTuple2);
        }
      }
    }

注意这里是**多态**的

class | plan的类型 | rule的类型 |阶段
---|---|---|---
[`Analyzer`]() | [`LogicalPlan`]() | [`Rule[LogicalPlan]`]()|分析阶段
[`Optimizer`]() | [`LogicalPlan`]() | [`Rule[LogicalPlan]`]()|逻辑优化阶段

> 物理优化阶段没有用这个类，原因是这里是 `LogicalPlan => LogicalPlan` 的转化，而那里是`LogicalPlan => SparkPlan`的转化


#### 和JAVA的互操作
> TODO 
> - [ ] 解释 `val func: (T) => Iterator[U] = x => f.call(x).`**`asScala`**

```scala
    /**
    * :: Experimental ::
    * (Scala-specific)
    * Returns a new Dataset by first applying a function to all elements of this Dataset,
    * and then flattening the results.
    *
    * @group typedrel
    * @since 1.6.0
    */
    @Experimental
    def flatMap[U : Encoder](func: T => TraversableOnce[U]): Dataset[U] =
      mapPartitions(_.flatMap(func))

    /**
    * :: Experimental ::
    * (Java-specific)
    * Returns a new Dataset by first applying a function to all elements of this Dataset,
    * and then flattening the results.
    *
    * @group typedrel
    * @since 1.6.0
    */
    def flatMap[U](f: FlatMapFunction[T, U], encoder: Encoder[U]): Dataset[U] = {
      val func: (T) => Iterator[U] = x => f.call(x).asScala
      flatMap(func)(encoder)
    }
```

注意 `flatMap` 的定义
- scala的版本需要一个隐式参数
- java的版本则需要两个参数，注意是如何传隐式参数的 `flatMap(func)`*`(encoder)`*

#### 如何实现一个集合


> TODO 
> - [ ] 调研[`StreamProgress`](https://github.com/apache/spark/blob/master/sql/core/src/main/scala/org/apache/spark/sql/execution/streaming/StreamProgress.scala)是如何实现的


#### 字面量
**字面量就是写在代码里的常量**

> TODO 
> - [ ] 函数字面量
> 

#### 相等性

Scala的 `==` 与Java的有何差别? **Java里的 `==` 既可以比较原始类型也可以比较引用类型。**
- 首先，Scala的 `==` **不是操作符！！！**，它是**定义在 `Any` 中的 `final` 方法（即不可重载）**。
- 对于原始类型（**值类型**），Scala和Java一样，`==` 用于比较**值的相等性**。
- 对于引用类型，Java的 `==` 用于比较**引用相等性**，也就是说这两个变量是否都指向于JVM堆里的同一个对象。Scala也提供了这种机制，名字是**`eq`**，是**定义在 `AnyRef` 中的 `final` 方法**。但是，`eq` 和它的反义词，`ne`，仅仅应用于可以直接映射到Java的对象。此外，Java的 `Object` 有一个 **`equals`** 方法，用于『让用户定义引用类型』的逻辑相等性，**缺省使用`==`来实现**，因此Java中对象的每个实例**只与它自身相等**。Scala的 `==` 是这样实现的：

```scala
  //定义在Any中
  final def==(that: Any): Boolean =
    if (this eq null ) that eq null
    else this.equals(that)
```

> TODO
> - [ ] [如何用Java语言编写相等方法](http://www.artima.com/lejava/articles/equality.html)，相关的内容包含在*Programming in Scala* 的中文版的第28章
> - [ ]*Effective Java* 中文版的 [条款8]() 和 [条款9]()

对于样例类，除非**显式地定义**，将自动生成 `toString` **`equals`** `hashCode` 和 `copy` 方法。Spark的 `Literal`

```scala
case class Literal protected (value: Any, dataType: DataType)
  extends LeafExpression with CodegenFallback {
  ...
  override def toString: String = if (value != null) value.toString else "null"
  override def hashCode(): Int = 
    31 * (31 * Objects.hashCode(dataType)) + Objects.hashCode(value)
  override def equals(other: Any): Boolean = other match {
    case o: Literal =>
      dataType.equals(o.dataType) &&
        (value == null && null == o.value || value != null && value.equals(o.value))
    case _ => false
  }
}
```

> TODO `Literal` 为什么要显式定义这些方法？

#### 中置类型 `Predef.<:<`

中置类型是指以**中置语法**表示『带有两个类型参数』的类型，此时，类型名称写在两个类型参数之间。比如：

```scala
Map[String,Int]
```

可以写成

```scala
String Map Int
```
##### 类型证明（中置类型的应用）
在 `Predef` 对象中，定义了这么如下两个抽象类，用于类型约束

  类型证明|含义
---------|---
`T =:= U`|类型 `T` 是否等于类型 `U`
`T <:< U`|类型 `T` 是否是类型 `U` 的子类型


抽象类（Scala 2.11.8） `<:<` 的的定义如下
```scala
  /**
   * An instance of `A <:< B` witnesses that `A` is a subtype of `B`.
   * Requiring an implicit argument of the type `A <:< B` encodes
   * the generalized constraint `A <: B`.
   *
   * @note we need a new type constructor `<:<` and evidence `conforms`,
   * as reusing `Function1` and `identity` leads to ambiguities in
   * case of type errors (`any2stringadd` is inferred)
   *
   * To constrain any abstract type T that's in scope in a method's
   * argument list (not just the method's own type parameters) simply
   * add an implicit argument of type `T <:< U`, where `U` is the required
   * upper bound; or for lower-bounds, use: `L <:< T`, where `L` is the
   * required lower bound.
   *
   * In part contributed by Jason Zaugg.
   */
  @implicitNotFound(msg = "Cannot prove that ${From} <:< ${To}.")
  sealed abstract class <:<[-From, +To] extends (From => To) with Serializable
  private[this] final val singleton_<:< = new <:<[Any,Any] { def apply(x: Any): Any = x }
  // The dollar prefix is to dodge accidental shadowing of this method
  // by a user-defined method of the same name (SI-7788).
  // The collections rely on this method.
  implicit def $conforms[A]: A <:< A = singleton_<:<.asInstanceOf[A <:< A]
```
一般的使用方式是为『方法』增加一个类型为 `T <:< U` 的隐式参数，比如：

    def firstLast[A,C](it : C)(implicit ev : C <:< Iterable[A]) = 
      (it.head, it.last)

注意到，`<:<` 对 `From` 是逆变，对 `To` 是协变，因此** `String <:< AnyRef` 是 `String <:< String` 的父类**，即

```scala
var  evChild : <:<[String,String] = $conforms[String]  
val evParent : <:<[String,AnyRef] = evChild
```

所以编译器在处理**约束** `implicit ev : C <:< Iterable[A]` 时，实际上是在看『能否将函数 `$conforms[C]` 的返回值赋给隐式参数 `ev`』，也就是在测试 **`C <:< Iterable[A]` 是否是 `C <:< C` 的父类**：

```scala
var  evChild : <:<[C,C]           = $conforms[C]
val       ev : <:<[C,Iterable[A]] = evChild      //编译通过，则说明 C 是 Iterable[A]的子类型
```

现在，到了一个比较**难**的点，从 Java 的角度来看 `ev` 是 `Function1` 对象的实例，在 Scala 中 `ev` 是一个 `C => Iterable[A]` 的函数，它是一个**恒等函数**，即返回传入的参数，等价于『做了一次向上的类型转换』。

```scala
    private[this] final val singleton_<:< = new <:<[Any,Any] { def apply(x: Any): Any = x }
```

编译器在编译 `firstLast` 时，并不知道 `C` 的具体类型，也就是说 `it.head` 和 `it.last` 这两个调用**不合法**。，而 `ev` 是个隐式参数，因此又触发了一次**隐式转换**，`(it.head, it.last)` 实际上被转换成

      (ev(it).head, ev(it).last)

Spark 中并没有使用到 `Predef.<:<`，但是集合库中的 `Map` 有用到
```scala
trait Map[A, +B] extends ... { 
  ...
  override def toMap[T, U](implicit ev: (A, B) <:< (T, U)): immutable.Map[T, U] =
    self.asInstanceOf[immutable.Map[T, U]]

}
```

> 吐槽：中置表示法，以及用**其它语言中的操作符**来声明类，是不是真的提高了可读性？
> TODO：隐式转换的规则
> TODO：Spark 大量用到了 Scala 反射API中 `TypeApi.<:<` 这个方法


#### `Predef.identity`
为什么需要一个预定义的 `identity` 函数？ 参见：
1. [What does Predef.identity do in scala?](http://stackoverflow.com/questions/28407482/what-does-predef-identity-do-in-scala)
2. [Is there a scala identity function?](http://stackoverflow.com/questions/1797502/is-there-a-scala-identity-function)

关于2的解释
```scala
    // 如何从 List[Option[String] 转成 List[String]， 比如有如下列表
    val l = List(Some("Hello"), None, Some("World"))

    //我们想得到 List(Hello, World)，有三种做法
    val a = l.flatMap(identity[Option[String]])
    val b = for (x <- l; y<-x ) yield y
    val c = l.flatMap( x => x)
```
- 首先，`List.flatMap`需要一个 `A => GenTraversableOnce[B]` 的函数
- 其次，`Option` 的伴生对象里有一个隐式转换 `option2Iterable` 把 `Option` 的实例转换成 `Iterable` 的实例

所以
- 变量 `a`，实际上是 `l.flatMap( x => Option.option2Iterable(identity(x)))`
- 变量 `b`，再加上for产生式的规则，实际上是`l.flatMap(x => Option.option2Iterable(x.map(y => y))`
- 但最简单的是变量 `c`，隐式转换后是 `l.flatMap( x => Option.option2Iterable(x))`，

当然了这三种方法由于涉及到**隐式转换**，个人觉得对新手而言，并没有可读性，反而让人看不懂！

**`identity` 一般的用法主要是传给高阶函数，使代码更加易于组合，另一面也可以达到自描述的目的。比如 Spark 的工具类 `StringKeyHashMap`**
```scala
package org.apache.spark.sql.catalyst.util

/**
 * Build a map with String type of key, and it also supports either key case
 * sensitive or insensitive.
 */
object StringKeyHashMap {
  def apply[T](caseSensitive: Boolean): StringKeyHashMap[T] = caseSensitive match {
    case false => new StringKeyHashMap[T](_.toLowerCase)
    case true  => new StringKeyHashMap[T](identity)
  }
}

class StringKeyHashMap[T](normalizer: (String) => String) {
...
}
```
这里 `StringKeyHashMap` 类需要一个**策略**函数 `normalizer: (String) => String`，而其伴生对象则根据 `caseSensitive` 使用不同的 `normalizer` 创建 `StringKeyHashMap` 实例。这样就既可以创建**键值大小写敏感**的哈希表，也可以创建**键值大小写不敏感**的哈希表。

#### 惰性求值（即，惰值）

[内容参见*快学 Scala* 的2.11节]()。当 `val` 被声明为 `lazy` 时，它的初始化将会延迟到『第一次使用它』时。我们来看下 Spark中的 `QueryExecution` 类，

    // 在 QueryExecution 类中定义惰值 executedPlan
    lazy val executedPlan: SparkPlan = prepareForExecution(sparkPlan)
    
    // QueryExecution 成员变量列表
                            type          val       lazy
    QueryExecution
      sparkSession    -> SparkSession     [Y]
      logical         -> LogicalPlan      [Y]
      analyzed        -> LogicalPlan      [Y]        [Y]
      withCachedData  -> LogicalPlan      [Y]        [Y]
      optimizedPlan   -> LogicalPlan      [Y]        [Y]
      sparkPlan       -> SparkPlan        [Y]        [Y]
      executedPlan    -> SparkPlan        [Y]        [Y]
      toRdd           -> RDD[InternalRow] [Y]        [Y]

注意：使用 Spark 的 `Dataset` 获取最终的结果时，大致的思路是先调用 `SparkPlan.execute()`，获得 `RDD`，然后再调用 `RDD` 的 Action。

```scala
//伪码
class Dataset[T]{
  def collect : Array[T] = {             //缺省是Public的
    queryExecution.
      executedPlan.                      //这个 executedPlan 是 lazy 的
        executeCollect()
  }	
}

abstract class SparkPlan {
  def executeCollect(): Array[InternalRow] = {
    val byteArrayRdd = getByteArrayRdd()  //获取RDD

    // 调用RDD的Action，获取最终的结果
    byteArrayRdd.collect()
    ...
  }
}
```

> TODO 画图
> 依赖关系：```logical -> analyzed -> withCachedData -> optimizedPlan -> sparkPlan -> executedPlan```
> 求值顺序：```logical <- analyzed <- withCachedData <- optimizedPlan <- sparkPlan <- executedPlan```
> 
> 吐槽：惰性求值推迟了初始化，在语言层面上提供了**按需求值**的语义，但是
> 1. 有额外的开销，每次访问**惰值**，都会调用一个『线程安全的』方法来检查该值是否已被初始化。
> 2. 可读性，初始化的地点，并非是你定义**惰值**的地方。

#### Pass by Name

例子
- *ExecutorClassLoader.scala* 中的 `getClassFileInputStreamFromSparkRPC`，看怎么实现 `toClassNotFound`。


#### Scala的 `Product` 特质

> TODO
> - [ ] [Playing with Scala Products](http://erikengbrecht.blogspot.com/2010/12/playing-with-scala-products.html)

例子， TreeNode.scala 的 `mapProductIterator`

```scala
abstract class TreeNode[...] extends Produce{
  protected def mapProductIterator[B: ClassTag](f: Any => B): Array[B] = {
    val arr = Array.ofDim[B](productArity)
    var i = 0
    while (i < arr.length) {
      arr(i) = f(productElement(i))
      i += 1
    }
    arr
  }
}
```

#### 链式 API

参见 `DataFrameReader` 的设计

#### 变长参数

1. *快学 Scala* 第2.9节
2. 参见 `DataFrameReader.text` 了解 `_*` 的用法
3. 为了 Java 互操作的 scala.annotation.varargs
4. 变长参数允许 0 个 或多个，如何表达至少有一个参数？ 可参考 `RelationalGroupedDataset.agg`

```scala
// 你这么定义 sumInt
def sumInt(xs: Int*) = { }

//下面两个调用都合法
sumInt(1,2,3)
sumInt()

//怎么表达至少一个参数？
```