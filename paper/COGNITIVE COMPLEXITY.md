# COGNITIVE COMPLEXITY

A new way of measuring understandability

> 认知复杂度，测量代码可读性的新方法

## Abstract
**==Cyclomatic Complexity==** was initially formulated as a measurement of the “testability and maintainability” of the control flow of a module. While it excels at measuring the former, its underlying mathematical model is unsatisfactory at producing a value that measures the latter. This white paper describes a new metric that **==breaks==** from the use of mathematical models to evaluate code in order to remedy Cyclomatic Complexity’s shortcomings and produce a measurement that more accurately reflects the relative difficulty of understanding, and therefore of maintaining methods, classes, and applications.

> **==圈复杂度==**最初被定义为<u>**模块控制流**</u>的“可测试性和可维护性”的度量。虽然它擅长于度量前者，但它的基本数学模型，使其难以得出一个好的“可维护性”的度量结果。本文描述了一种新的度量方法，它**==打破了==**使用数学模型来评估代码的做法，以弥补圈复杂度的缺点。这种新的度量，能够更准确地反映理解成本，以及维护这些代码的困难程度。

### A note on terminology

While **Cognitive Complexity** is a **==language-neutral==** metric that applies equally to files and classes, and to methods, procedures, functions, and so on, the Object-Oriented terms “class” and “method” are used for convenience.

> 虽然**认知复杂度**是一种与语言无关的度量，同样适用于文件和类，以及方法、过程、函数等，但为了方便起见，使用了面向对象的术语“类”和“方法”。

## Introduction
Thomas J. McCabe’s ==**Cyclomatic Complexity**== has long been the de facto standard for measuring the complexity of a method’s control flow. It was originally intended “to identify software modules that will be difficult to test or maintain”[1], but while it accurately calculates the minimum number of test cases required to fully cover a method, it is not a satisfactory measure of understandability. This is because methods with equal Cyclomatic Complexity do not necessarily present equal difficulty to the maintainer, leading to a sense that the measurement “cries wolf” by over-valuing some structures, while under-valuing others. 

At the same time, Cyclomatic Complexity is no longer comprehensive. Formulated in a Fortran environment in 1976, it doesn’t include modern language structures like try/catch, and lambdas.

And finally, because each method has a minimum Cyclomatic Complexity score of one, it is impossible to know whether any given class with a high aggregate Cyclomatic Complexity is a large, easily maintained domain class, or a small class with a complex control flow. Beyond the class level, it is widely acknowledged that the Cyclomatic Complexity scores of applications correlate to their lines of code totals. In other words, Cyclomatic Complexity is of little use above the method level.

As a remedy for these problems, **Cognitive Complexity** has been formulated to address modern language structures, and to produce values that are meaningful at the class and application levels. More importantly, it departs from the practice of evaluating code based on mathematical models so that it can yield assessments of control flow that correspond to programmers’ intuitions about the mental, or cognitive effort required to understand those flows.

> Thomas J. McCabe 设计的圈复杂度是一个事实上的标准，用来测量一个模块控制流的复杂度。圈复杂度最初的目的是用来识别“难以测试和维护的软件模块”，它能算出全覆盖测试所需的最少用例量，但是不能测出一个让人满意的“理解难度”。这是因为圈复杂度相同的代码，不一定会有相同的维护难度，这会导致我们对一些模块有错误估计。
>
> 圈复杂度的理论是在1976年，基于Fortran语言设计的，因此**如今使用它来衡量就不再那么全面了——它不包含一些现代的语言结构，如 try-catch 与 lambda**。
>
> 以及，每个方法都的圈复杂度至少为1。这就让我们无从得知，一个给定的类如果圈复杂度很高，它是一个大的、易维护的类，还是一个具有复杂逻辑的小类。除了类这一级之外，普遍认为应用程序的圈复杂度分数与其代码行总数相关。因此在方法层级之上，圈复杂度用处不大。
>
> 为解决这些问题，我们定义**认知复杂度(Cognitive Complexity)**以符合现代语言结构，并在类和应用程序层级都能产生一个有意义的度量值。 更重要的是，它背离了基于数学模型对代码进行评估的实践，这样它生成的评估分数，就能和程序员理解这些控制流所需的的心理或认知努力直觉相对应。

## An illustration of the problem

It is useful to begin the discussion of Cognitive Complexity with an example of the problem it is designed to address. The two following methods have equal Cyclomatic Complexity, but are strikingly different in terms of understandability.

> 这里给出一个很有用的例子，可以指出圈复杂度的问题。以下两段方法有着相同的圈复杂度，但是在理解难度上差非常多：

``` java
// code 1
int sumOfPrimes(int max) {              // +1
  int total = 0;
  OUT: for (int i = 1; i <= max; ++i) { // +1
    for (int j = 2; j < i; ++j) {       // +1
      if (i % j == 0) {                 // +1
        continue OUT;
      }
    }
    total += i;
  }
  return total;
}                                       // Cyclomatic Complexity 4

// code 2
String getWords(int number) {          // +1
  switch (number) {
    case 1:                            // +1
      return "one";
    case 2:                            // +1
      return "a couple";
    case 3:                            // +1
      return “a few”;
    default:
      return "lots";
  }
}                                      // Cyclomatic Complexity 4
```

The mathematical model underlying Cyclomatic Complexity gives these two methods equal weight, yet it is intuitively obvious that the control flow of `sumOfPrimes` is more difficult to understand than that of getWords. This is why Cognitive Complexity abandons the use of mathematical models for assessing control flow in favor of a set of simple rules for turning programmer intuition into numbers.

> 圈复杂度的底层数学模型，对上图的两个方法给出相同的评分，然而从直觉上显然左边的 `sumOfPrimes` 要更难以理解一些。这也是为什么**认知复杂度**舍弃了使用数学模型来评估一段逻辑，改**用一组简单的规则，把代码的直觉理解程度转为一个数字表达**。

## Basic criteria and methodologyß

A Cognitive Complexity score is assessed according to three basic rules:
1. Ignore structures that allow multiple statements to be readably shorthanded into one

2. Increment (add one) for each break in the linear flow of the code

3. Increment when flow-breaking structures are nested

Additionally, a complexity score is made up of four different types of increments:

1. Nesting - assessed for nesting control flow structures inside each other
2. Structural - assessed on control flow structures that are subject to a nesting increment, and that increase the nesting count 
3. Fundamental - assessed on statements not subject to a **==nesting increment==**
4. Hybrid - assessed on control flow structures that are not subject to a nesting increment, but which do increase the nesting count

While the type of an increment makes no difference in the math - each increment adds one to the final score - <u>==making a distinction among the categories of features being counted==</u> makes it easier to understand where **==nesting increments==** do and do not apply.

These rules and the principles behind them are further detailed in the following sections.

> 使用下面三条基本规则计算**认知复杂度**：
>
> 1. **忽略**允许将多个语句轻松简化为一个语句的结构
> 2. 出现破坏线性代码逻辑的**==语言结构==**，增加复杂度
> 3. 当破坏代码逻辑的**==语言结构==**嵌套时，增加复杂度
>
> 具体地说，复杂度来源于以下几种不同的类型：
>
> 1.  **Nesting**：相互嵌套的控制流结构
> 2. **Structural**：受嵌套增量影响，并增加嵌套计数的控制流结构
> 3. **Fundamental**：不受嵌套增量约束的语句
> 4. **Hybrid**：不受嵌套增量约束，但会增加嵌套计数的控制流结构
>
> 尽管在数学上，不同类型之间没有区别，都只是对复杂度加一，但区分使得复杂度增加的代码类型，可以更容易地理解**==嵌套增量==**在何处适用或不适用。
>
> 以下各节将进一步详细说明这些规则及其背后的原理。

## Ignore shorthand

> 忽略简写

A guiding principle in the **==formulation==** of Cognitive Complexity has been that it should **==incent==** good coding practices. That is, it should either ignore or **==discount==** features that make code more readable.

The method structure itself is **==a prime example==**. Breaking code into methods allows you to **==condense==** multiple statements into a single, **==evocatively==** named call, i.e. to “shorthand” it. Thus, Cognitive Complexity does not increment for methods.

Cognitive Complexity also ignores the **==null-coalescing==** operators found in many languages, again because they allow short-handing multiple lines of code into one. For example, both of the following code samples do the same thing:

> 定义认知复杂度一个指导性的原则是：激励使用者写出好的编码规范。也就是说，需要无视或低估让代码更可读的语言特征（不计算进复杂度）。
>
> **方法**本身就是一个很好的例子。将代码分解为方法允许您将多个语句压缩为一个单一的、具有唤起性的命名调用，即**速记**它。因此，**方法调用**没有增加认知复杂度。
>
> 同样的，认知复杂度也会忽略掉 **null-coalescing** 操作符，`x?.myObject` 这样的操作符不增加复杂度，因为这些操作同样是把多段代码缩写为一项了。例如下面的两段代码：

``` groovy
// code 1
MyObj myObj = null;
if (a != null) {
myObj = a.myObj;
}

// code 2
MyObj myObj = a?.myObj;
```

The meaning of the version on the left takes a moment to process, while the version on the right is immediately clear once you understand the null-coalescing syntax. For that reason, Cognitive Complexity ignores null-coalescing operators.

> 左侧版本的代码需要花了一小些时间来理解，而如果你理解 **null-coalescing** 的语法，右侧版本立即能够立即看明白。出于这样的原因，计算认知复杂度时会忽略掉 **null-coalescing** 操作。

## Increment for breaks in the linear flow

> 破坏线性代码流增加复杂度

Another guiding principle in the **==formulation==** of Cognitive Complexity is that structures that **break** code’s normal linear flow from top to bottom, left to right require maintainers to work harder to understand that code. In acknowledgement of this extra effort, Cognitive Complexity **==assesses==** structural increments for:

- Loop structures: for, while, do while, ...
- Conditionals: ternary operators, if, #if, #ifdef, ...

It assesses hybrid increments for:

- else if, elif, else, …

No nesting increment is assessed for these structures because the mental cost has already been paid when reading the `if`.

These increment targets will seem familiar to those who are used to **==Cyclomatic Complexity==**. In addition, Cognitive Complexity also increments for:

> **定义认知复杂度公式**的另一个指导原则是：从上到下、从左到右**破坏**线性代码流的**结构**，使得维护人员更加难以理解代码。认识到这一额外的负担，下面的结构将会使得认知复杂度+1：
>
> - **循环**: for, while, do while, ...
> - **条件**: 三元运算符, if, #if, #ifdef...
>
> 另外，以下语句会增加 Hybrid 类的复杂度：
>
> - else if, elif, else, ...
>
> 但不计算**嵌套增量**，因为之前的 `if` 语句已经考虑了**嵌套增量**。
>
> 这些计算复杂度的公式，和圈复杂度的计算方式类似，但认知复杂度还会计算下列结构：

### Catches
**A `catch` represents a kind of branch in the control flow just as much as an `if`**. Therefore, each catch clause results in a structural increment to Cognitive Complexity. Note that a catch only adds one point to the Cognitive Complexity score, no matter how many exception types are caught. try and finally blocks are ignored altogether.

> **`catch` 与 `if` 一样表示控制流中的一种分支**。因此每个 `catch` 子句都会增加 Structural 类的认知复杂度。请注意，论 `catch` 了多少异常类型，都只 +1。完全忽略 `try` 和 `finally` 块。

### Switches
A `switch` and all its cases combined incurs a single structural increment.

Under Cyclomatic Complexity, a switch is treated as an analog to an if-else if chain. That is, each case in the switch causes an increment because it causes a branch in the mathematical model of the control flow.

But from a maintainer’s point of view, a switch - which compares a single variable to an explicitly named set of literal values - is much easier to understand than an if-else if chain because the latter may make any number of comparisons, using any number of variables and values.

In short, an if-else if chain must be read carefully, while a switch can often be taken in at a glance.

> `switch` 及其所有 `case` 一起记为一个 Structural 类型，复杂度 +1。
>
> 在圈复杂度下，`switch` 语句被视为一系列的 `if-else if` 链。因为 `case` 使得控制流分支增多，所以每个 `case` 都会增加复杂度。
>
> 从代码维护的视角来看，`switch` 将单个变量与一组显式值比较，比 `if-else if` 链易于理解， `if-else if` 链可以用任意条件做比较。
>
> 总之，必须仔细阅读 `if-else if` 链的每个条件，而 `switch` 通常一目了然。

### Sequences of logical operators

> 逻辑运算符的组合

For similar reasons, Cognitive Complexity does not increment for each binary logical operator. Instead, it assesses a fundamental increment for each sequence of binary logical operators. For instance, consider the following pairs:

```java
a && b
a && b && c && d

a || b
a || b || c || d
```

Understanding the second line in each pair isn’t that much harder than understanding the first. On the other hand, there is a marked difference in the effort to understand the following two lines:

```java
a && b && c && d
a || b && c || d
```

Because boolean expressions become more difficult to understand with mixed operators, Cognitive complexity increments for each new sequence of like operators. For instance:

```java
if (a                    // +1 for `if`
    && b && c            // +1
    || d || e            // +1
    && f)                // +1

if (a                    // +1 for `if`
    &&                   // +1
    !(b && c))           // +1
```

<u>==While Cognitive Complexity offers a “discount” for like operators relative to Cyclomatic Complexity, it does increment for all sequences of binary boolean operators such as those in variable assignments, method invocations, and return statements==</u>.

> 出于类似原因，并非基于单个**逻辑运算符**计算认知复杂度，而是考虑**一组连续的逻辑运算符**。例如下面两对操作：
>
> ```java
> a && b
> a && b && c && d
> 
> a || b
> a || b || c || d
> ```
> 每对的第二行并不比第一行更难理解。但是对于下面两行，理解难度有质的区别：
>
> ```java
> a && b && c && d
> a || b && c || d
> ```
>
> 因为混合使用逻辑运算符会使得 boolean 表达式更难理解，因此对 boolean 表达式，每出现**一组新的逻辑运算符**，认知复杂度就 +1。例如：
>
> ```java
> if (a                 // +1 for `if`
>     && b && c            // +1
>     || d || e            // +1
>     && f)                // +1
> 
> if (a                 // +1 for `if`
>     &&                   // +1
>     !(b && c))           // +1
> ```
> <u>==尽管认知复杂度相对于圈复杂度，为类似的运算符提供了“折扣”，但对所有布尔运算符序列来说，认知复杂度确实会增加（例如那些变量赋值，方法调用和返回语句）==</u>。

### Recursion

Unlike Cyclomatic Complexity, Cognitive Complexity adds **==a fundamental increment==** for each method in a recursion cycle, whether direct or indirect. There are two motivations for this decision. First, recursion represents a kind of “meta-loop”, and Cognitive Complexity increments for loops. Second, Cognitive Complexity is about estimating the relative difficulty of understanding the control flow of a method, and even some **==seasoned==** programmers find recursion difficult to understand.

>与圈复杂度不同，对每一个递归调用（不管直接还是间接），Fundamental 类的认知复杂度都会 +1，这基于两个原因。首先，递归表达了一种“元循环”，循环会增加认知复杂度；第二，认知复杂度是用于估计**<u>理解每个方法内控制流</u>**的相对难度，即使经验丰富的程序员也觉得递归难以理解。

### Jumps to labels
goto, and break or continue to a label add fundamental increments to Cognitive Complexity. But because an early return can often make code much clearer, no other jumps or early exits cause an increment.

> 跳转到某个 **label** 的 `goto`, `break` 和 `continue` ，会增加 Fundamental 类的认知复杂度。**但在代码中提前返回，使代码更清晰**，所以其它类型的 `continue` ，`break`，`return` 不会增加认知复杂度。

## Increment for nested flow-break structures
It seems intuitively obvious that a linear series of five if and for structures would be easier to understand than that same five structures successively nested, regardless of the number of execution paths through each series. Because such nesting increases the mental demands to understand the code, **==Cognitive Complexity assesses a nesting increment for it==**.

<u>==Specifically, each time a structure that causes a structural or hybrid increment is nested inside another such structure, a nesting increment is added for each level of nesting==</u>. For instance, in the following example, there is no nesting increment for the method itself or for the try because neither structure results in either a structural or a hybrid increment:

> 直观地看，由5个 `if` 和 `for` 组成的线性序列结构，无论每个序列的执行路径有多少，都比 5 个相同的结构连续嵌套更容易理解。因为这样的嵌套会增加理解代码的成本，==所以会将会增加 Nesting 类的认知复杂度==。
>
> <u>特别地，每一次有一个导致了Structural类或Hybrid类复杂的结构体，嵌套了另一个结构时，每一层嵌套都要再加一次Nesting类复杂度</u>。例如下面的例子，这个方法本身和try这两项就不会计入Nesting类的复杂，因为它们即不是Structure类也不是Hybrid类的复杂结构：

```java
void myMethod () {
  try {
    if (condition1) {                  // +1
      for (int i = 0; i < 10; i++) {   // +2 (nesting=1)
        while (condition2) { … }       // +3 (nesting=2)
      }
    }
  } catch (ExcepType1 | ExcepType2 e) { // +1
    if (condition2) { … }               // +2 (nesting=1)
  }
} // Cognitive Complexity 9
```

However, the if, for, while, and catch structures are all subject to both structural and nesting increments.

Additionally, while top-level methods are ignored, and there is no structural increment for lambdas, nested methods, and similar features, such methods do increment the nesting level when nested inside other method-like structures:

> <u>==然而，对于if\for\while\catch这些结构，全部被视为Structural类和Nesting类的复杂==</u>。
>
> <u>==此外，虽然最外层的方法被忽略了，并且lambda、#ifdef等类似功能也都不会视为Structral类的增量，但是它们会计入嵌套的层级数==</u>：

```c
void myMethod2 () {
  Runnable r = () -> {       // +0 (but nesting level is now 1)
    if (condition1) { … }    // +2 (nesting=1)
  };
}                            // Cognitive Complexity 2

#if DEBUG                     // +1 for if
void myMethod2 () {           // +0 (nesting level is still 0)
  Runnable r = () -> {        // +0 (but nesting level is now 1)
   if (condition1) { … }      // +3 (nesting=2)
  };
}                             // Cognitive Complexity 4
#endif
```
## The implications

> **影响**

Cognitive Complexity was formulated with the primary goal of calculating method scores that more accurately reflect methods’ relative understandability, and with secondary goals of addressing modern language constructs and producing metrics that are valuable above the method level. Demonstrably, the goal of addressing modern language constructs has been achieved. The other two goals are examined below.

> 计算认知复杂度的主要目的是为方法计算出一个**分数**，准确地反应出此该方法的相对理解难度。次要目标有两个，一是可以为现代的语言结构计算复杂度，二是在方法层级之上产生有价值的指标。 显然，为现代语言结构计算复杂度的目标已实现。 其他两个目标将在下面讨论。

### Intuitively ‘right’ complexity scores

> 直觉上**<u>正确地</u>**复杂度打分

This discussion began with a pair of methods with equal Cyclomatic Complexity but decidedly unequal understandability. Now it is time to re-examine those methods and calculate their Cognitive Complexity scores:

> 在本篇开头的时候讨论了一对圈复杂度相同，但可理解性明显不同的两个方法。现在回过头来检查这两个方法的认知复杂度：

```java
int sumOfPrimes(int max) {
  int total = 0;
    OUT: for (int i = 1; i <= max; ++i) { // +1
      for (int j = 2; j < i; ++j) {       // +2
        if (i % j == 0) {                 // +3
          continue OUT;                   // +1
        }
    }
    total += i;
  }
  return total;
}                                          // Cognitive Complexity 7
```

```java
String getWords(int number) {
  switch (number) {                        // +1
    case 1:
      return "one";
    case 2:
      return "a couple";
    case 3:
      return “a few”;
    default:
      return "lots";
  }
}                                          // Cognitive Complexity 1
```

The Cognitive Complexity algorithm gives these two methods markedly different scores, ones that are far more reflective of their relative understandability.

> 认知复杂度算法为这两个方法给出了明显不同的两个分数，更能反映出它们的相对可理解性。

### Metrics that are valuable above the method level

> 方法层级之上有价值的指标

Further, because Cognitive Complexity does not increment for the method structure, aggregate numbers become useful. Now you can tell the difference between a domain class - one with a large number of simple getters and setters - and one that contains a complex control flow by simply comparing their metric values. Cognitive Complexity thus becomes a tool for measuring the relative understandability of classes and applications.

> 此外，由于方法结构的认知复杂性没有增加，因此聚合数变得有用。现在您可以区分域类（一个具有大量简单getter和setter的域类）和包含复杂控制流的域类，只需比较它们的度量值。认知复杂性因此成为衡量类和应用程序的相对可理解性的工具。
>
> 更进一步的，因为认知复杂度不会因为方法这个结构增加，复杂度的总和开始有用了起来。现在你可以看出两个类：一个有大量的getter()\setter()方法，另一个类仅有一个极其复杂的控制流，可以简单的通过比较二者的认知复杂度就行了。认知复杂度可以成为衡量一个类或应用的相对复杂度的工具。

## Conclusion
The processes of writing and maintaining code are human processes. Their outputs must adhere to mathematical models, but they do not fit into mathematical models themselves. This is why mathematical models are inadequate to assess the effort they require.

Cognitive Complexity breaks from the practice of using mathematical models to assess software maintainability. <u>==It starts from the precedents set by Cyclomatic Complexity, but uses human judgment to assess how structures should be counted, and to decide what should be added to the model as a whole==</u>. As a result, it yields method complexity scores which strike programmers as fairer relative assessments of understandability than have been available with previous models. Further, because Cognitive Complexity charges no “cost of entry” for a method, it produces those fairer relative assessments not just at the method level, but also at the class and application levels.

> 编写和维护代码是一个人为过程，输出必须遵守数学模型，但过程本身不适合数学模型。 这就是为什么数学模型不足以评估其所需工作量的原因。
>
> 认知复杂度不同于使用数学模型评估软件可维护性的实践。 <u>==它从圈复杂度设定的先例开始，但是通过人工来判断如何对结构进行计分，并决定应该将什么作为一个整体添加到模型中==</u>。结果，它得出的方法复杂度的得分比以前的模型更能吸引程序员，因为它们是对可理解性更公平的相对评估。此外，由于认知复杂度不考虑方法的“调用成本”，因此不仅在方法级别，在类和应用级别，也产生了相对更加公平的评估结果。

## Appendix B: Specification
The purpose of this section is to give a **==concise==** enumeration of the structures and circumstances that increment Cognitive Complexity, subject to the exceptions listed in Appendix A. This is meant to be a comprehensive listing **==without being language exhaustive==**. That is, if a language has an **==atypical==** spelling for a key word, such as elif for else if, its omission here is not intended to omit it from the specification.

> 本节的目的是简要概述增加认知复杂度的结构和情况，但附录 A 中列出的例外情况除外。这意味着要列出完整的清单，但不会列举所有语言。也就是说，如果一种语言对某个关键字有非典型拼写，比如用 `elif` 表示  `else if`，那么尽管这里有遗漏，但并不是表示要从规范中省略它。
>

### B1. Increments
There is an increment for each of the following:

> 以下结构，认知复杂度 +1

- `if`, `else if`, `else`, ternary operator（三元运算符）
- `switch`
- `for`, `foreach`
- `while`, `do while`
- `catch`
- `goto LABEL`, `break LABEL`, `continue LABEL`
- sequences of binary logical operators（二元逻辑算符的序列）
- each method in a recursion cycle （递归调用的方法）

### B2. Nesting level
The following structures increment the nesting level:

> 以下结构，嵌套级别 +1

- `if`, `else if`, `else`, ternary operator（三元运算符）
- `switch`
- `for`, `foreach`
- `while`, `do while`
- `catch`
- nested methods and method-like structures such as lambdas （嵌套方法和类似方法的结构，例如 **lambda**）

### B3. Nesting increments
The following structures receive a nesting increment **==commensurate==** with their nested depth inside B2 structures:

> 以下结构的嵌套增量与其在 B.2 结构中的嵌套深度相对应：

- `if`, ternary operator（三元运算符）
- `switch`
- `for`, `foreach`
- `while`, `do while`
- `catch`

# 认知复杂度

认知复杂度，测量代码可读性的新方法

## 摘要
**==圈复杂度==**最初被定义为<u>**模块控制流**</u>的“可测试性和可维护性”的度量。虽然它擅长于度量前者，但它的基本数学模型，使其难以得出一个好的“可维护性”的度量结果。本文描述了一种新的度量方法，它**==打破了==**使用数学模型来评估代码的做法，以弥补圈复杂度的缺点。这种新的度量，能够更准确地反映理解成本，以及维护这些代码的困难程度。

### 术语说明

虽然**认知复杂度**是一种与语言无关的度量，同样适用于文件和类，以及方法、过程、函数等，但为了方便起见，使用了面向对象的术语“类”和“方法”。

## Introduction
Thomas J. McCabe 设计的圈复杂度是一个事实上的标准，用来测量一个模块控制流的复杂度。圈复杂度最初的目的是用来识别“难以测试和维护的软件模块”，它能算出全覆盖测试所需的最少用例量，但是不能测出一个让人满意的“理解难度”。这是因为圈复杂度相同的代码，不一定会有相同的维护难度，这会导致我们对一些模块有错误估计。

圈复杂度的理论是在1976年，基于Fortran语言设计的，因此**如今使用它来衡量就不再那么全面了——它不包含一些现代的语言结构，如 try-catch 与 lambda**。

以及，每个方法都的圈复杂度至少为1。这就让我们无从得知，一个给定的类如果圈复杂度很高，它是一个大的、易维护的类，还是一个具有复杂逻辑的小类。除了类这一级之外，普遍认为应用程序的圈复杂度分数与其代码行总数相关。因此在方法层级之上，圈复杂度用处不大。

为解决这些问题，我们定义**认知复杂度(Cognitive Complexity)**以符合现代语言结构，并在类和应用程序层级都能产生一个有意义的度量值。 更重要的是，它背离了基于数学模型对代码进行评估的实践，这样它生成的评估分数，就能和程序员理解这些控制流所需的的心理或认知努力直觉相对应。

## 问题说明

这里给出一个很有用的例子，可以指出圈复杂度的问题。以下两段方法有着相同的圈复杂度，但是在理解难度上差非常多：

``` java
// code 1
int sumOfPrimes(int max) {              // +1
  int total = 0;
  OUT: for (int i = 1; i <= max; ++i) { // +1
    for (int j = 2; j < i; ++j) {       // +1
      if (i % j == 0) {                 // +1
        continue OUT;
      }
    }
    total += i;
  }
  return total;
}                                       // Cyclomatic Complexity 4

// code 2
String getWords(int number) {          // +1
  switch (number) {
    case 1:                            // +1
      return "one";
    case 2:                            // +1
      return "a couple";
    case 3:                            // +1
      return “a few”;
    default:
      return "lots";
  }
}                                      // Cyclomatic Complexity 4
```

圈复杂度的底层数学模型，对上图的两个方法给出相同的评分，然而从直觉上显然左边的 `sumOfPrimes` 要更难以理解一些。这也是为什么**认知复杂度**舍弃了使用数学模型来评估一段逻辑，改**用一组简单的规则，把代码的直觉理解程度转为一个数字表达**。

## 基本标准和方法论

使用下面三条基本规则计算**认知复杂度**：

1. **忽略**允许将多个语句轻松简化为一个语句的结构
2. 出现破坏线性代码逻辑的**==语言结构==**，增加复杂度
3. 当破坏代码逻辑的**==语言结构==**嵌套时，增加复杂度

具体地说，复杂度来源于以下几种不同的类型：

1.  **Nesting**：相互嵌套的控制流结构
2. **Structural**：受嵌套增量影响，并增加嵌套计数的控制流结构
3. **Fundamental**：不受嵌套增量约束的语句
4. **Hybrid**：不受嵌套增量约束，但会增加嵌套计数的控制流结构

尽管在数学上，不同类型之间没有区别，都只是对复杂度加一，但区分使得复杂度增加的代码类型，可以更容易地理解**==嵌套增量==**在何处适用或不适用。

以下各节将进一步详细说明这些规则及其背后的原理。

## Ignore shorthand

> 忽略简写

定义认知复杂度一个指导性的原则是：激励使用者写出好的编码规范。也就是说，需要无视或低估让代码更可读的语言特征（不计算进复杂度）。

**方法**本身就是一个很好的例子。将代码分解为方法允许您将多个语句压缩为一个单一的、具有唤起性的命名调用，即**速记**它。因此，**方法调用**没有增加认知复杂度。

同样的，认知复杂度也会忽略掉 **null-coalescing** 操作符，`x?.myObject` 这样的操作符不增加复杂度，因为这些操作同样是把多段代码缩写为一项了。例如下面的两段代码：

``` groovy
// code 1
MyObj myObj = null;
if (a != null) {
myObj = a.myObj;
}

// code 2
MyObj myObj = a?.myObj;
```

左侧版本的代码需要花了一小些时间来理解，而如果你理解 **null-coalescing** 的语法，右侧版本立即能够立即看明白。出于这样的原因，计算认知复杂度时会忽略掉 **null-coalescing** 操作。

## Increment for breaks in the linear flow

> 破坏线性代码流复杂度+1

**定义认知复杂度公式**的另一个指导原则是：从上到下、从左到右**破坏**线性代码流的**结构**，使得维护人员更加难以理解代码。认识到这一额外的负担，下面的结构将会使得认知复杂度+1：

- **循环**: for, while, do while, ...
- **条件**: 三元运算符, if, #if, #ifdef...

另外，以下语句会增加 Hybrid 类的复杂度：

- else if, elif, else, ...

但不计算**嵌套增量**，因为之前的 `if` 语句已经考虑了**嵌套增量**。

这些计算复杂度的公式，和圈复杂度的计算方式类似，但认知复杂度还会计算下列结构：

### Catches
**`catch` 与 `if` 一样表示控制流中的一种分支**。因此每个 `catch` 子句都会增加 Structural 类的认知复杂度。请注意，论 `catch` 了多少异常类型，都只 +1。完全忽略 `try` 和 `finally` 块。

### Switches
`switch` 及其所有 `case` 一起记为一个 Structural 类型，复杂度 +1。

在圈复杂度下，`switch` 语句被视为一系列的 `if-else if` 链。因为 `case` 使得控制流分支增多，所以每个 `case` 都会增加复杂度。

从代码维护的视角来看，`switch` 将单个变量与一组显式值比较，比 `if-else if` 链易于理解， `if-else if` 链可以用任意条件做比较。

总之，必须仔细阅读 `if-else if` 链的每个条件，而 `switch` 通常一目了然。

### Sequences of logical operators

> 逻辑运算符的组合

出于类似原因，并非基于单个**逻辑运算符**计算认知复杂度，而是考虑**一组连续的逻辑运算符**。例如下面两对操作：

```java
a && b
a && b && c && d

a || b
a || b || c || d
```
每对的第二行并不比第一行更难理解。但是对于下面两行，理解难度有质的区别：

```java
a && b && c && d
a || b && c || d
```

因为混合使用逻辑运算符会使得 boolean 表达式更难理解，因此对 boolean 表达式，每出现**一组新的逻辑运算符**，认知复杂度就 +1。例如：

```java
if (a                 // +1 for `if`
 && b && c            // +1
 || d || e            // +1
 && f)                // +1

if (a                 // +1 for `if`
 &&                   // +1
 !(b && c))           // +1
```
<u>==尽管认知复杂度相对于圈复杂度，为类似的运算符提供了“折扣”，但对所有布尔运算符序列来说，认知复杂度确实会增加（例如那些变量赋值，方法调用和返回语句）==</u>。

### Recursion

与圈复杂度不同，对每一个递归调用（不管直接还是间接），Fundamental 类的认知复杂度都会 +1，这基于两个原因。首先，递归表达了一种“元循环”，循环会增加认知复杂度；第二，认知复杂度是用于估计**<u>理解每个方法内控制流</u>**的相对难度，即使经验丰富的程序员也觉得递归难以理解。

### Jumps to labels
跳转到某个 **label** 的 `goto`, `break` 和 `continue` ，会增加 Fundamental 类的认知复杂度。**但在代码中提前返回，使代码更清晰**，所以其它类型的 `continue` ，`break`，`return` 不会增加认知复杂度。

## Increment for nested flow-break structures
直观地看，由5个 `if` 和 `for` 组成的线性序列结构，无论每个序列的执行路径有多少，都比 5 个相同的结构连续嵌套更容易理解。因为这样的嵌套会增加理解代码的成本，所以会将会增加 Nesting 类的认知复杂度。

<u>特别地，每一次有一个导致了Structural类或Hybrid类复杂的结构体，嵌套了另一个结构时，每一层嵌套都要再加一次Nesting类复杂度</u>。例如下面的例子，这个方法本身和try这两项就不会计入Nesting类的复杂，因为它们即不是Structure类也不是Hybrid类的复杂结构：

```java
void myMethod () {
  try {
    if (condition1) {                  // +1
      for (int i = 0; i < 10; i++) {   // +2 (nesting=1)
        while (condition2) { … }       // +3 (nesting=2)
      }
    }
  } catch (ExcepType1 | ExcepType2 e) { // +1
    if (condition2) { … }               // +2 (nesting=1)
  }
} // Cognitive Complexity 9
```

<u>==然而，对于if\for\while\catch这些结构，全部被视为Structural类和Nesting类的复杂==</u>。

<u>==此外，虽然最外层的方法被忽略了，并且lambda、#ifdef等类似功能也都不会视为Structral类的增量，但是它们会计入嵌套的层级数==</u>：

```c
void myMethod2 () {
  Runnable r = () -> {       // +0 (but nesting level is now 1)
    if (condition1) { … }    // +2 (nesting=1)
  };
}                            // Cognitive Complexity 2

#if DEBUG                     // +1 for if
void myMethod2 () {           // +0 (nesting level is still 0)
  Runnable r = () -> {        // +0 (but nesting level is now 1)
   if (condition1) { … }      // +3 (nesting=2)
  };
}                             // Cognitive Complexity 4
#endif
```
## The implications

> **影响**

计算认知复杂度的主要目的是为方法计算出一个**分数**，准确地反应出此该方法的相对理解难度。次要目标有两个，一是可以为现代的语言结构计算复杂度，二是在方法层级之上产生有价值的指标。 显然，为现代语言结构计算复杂度的目标已实现。 其他两个目标将在下面讨论。

### Intuitively ‘right’ complexity scores

> 直觉上**<u>正确地</u>**复杂度打分

在本篇开头的时候讨论了一对圈复杂度相同，但可理解性明显不同的两个方法。现在回过头来检查这两个方法的认知复杂度：

```java
int sumOfPrimes(int max) {
  int total = 0;
    OUT: for (int i = 1; i <= max; ++i) { // +1
      for (int j = 2; j < i; ++j) {       // +2
        if (i % j == 0) {                 // +3
          continue OUT;                   // +1
        }
    }
    total += i;
  }
  return total;
}                                          // Cognitive Complexity 7
```

```java
String getWords(int number) {
  switch (number) {                        // +1
    case 1:
      return "one";
    case 2:
      return "a couple";
    case 3:
      return “a few”;
    default:
      return "lots";
  }
}                                          // Cognitive Complexity 1
```

认知复杂度算法为这两个方法给出了明显不同的两个分数，更能反映出它们的相对可理解性。

### Metrics that are valuable above the method level

> 方法层级之上有价值的指标

此外，由于方法结构的认知复杂性没有增加，因此聚合数变得有用。现在您可以区分域类（一个具有大量简单getter和setter的域类）和包含复杂控制流的域类，只需比较它们的度量值。认知复杂性因此成为衡量类和应用程序的相对可理解性的工具。

更进一步的，因为认知复杂度不会因为方法这个结构增加，复杂度的总和开始有用了起来。现在你可以看出两个类：一个有大量的getter()\setter()方法，另一个类仅有一个极其复杂的控制流，可以简单的通过比较二者的认知复杂度就行了。认知复杂度可以成为衡量一个类或应用的相对复杂度的工具。

## 结论
编写和维护代码是一个人为过程，输出必须遵守数学模型，但过程本身不适合数学模型。 这就是为什么数学模型不足以评估其所需工作量的原因。

认知复杂度不同于使用数学模型评估软件可维护性的实践。 <u>==它从圈复杂度设定的先例开始，但是通过人工来判断如何对结构进行计分，并决定应该将什么作为一个整体添加到模型中==</u>。结果，它得出的方法复杂度的得分比以前的模型更能吸引程序员，因为它们是对可理解性更公平的相对评估。此外，由于认知复杂度不考虑方法的“调用成本”，因此不仅在方法级别，在类和应用级别，也产生了相对更加公平的评估结果。

## 附录 B: 规范
本节的目的是简要概述增加认知复杂度的结构和情况，但附录 A 中列出的例外情况除外。这意味着要列出完整的清单，但不会列举所有语言。也就是说，如果一种语言对某个关键字有非典型拼写，比如用 `elif` 表示  `else if`，那么尽管这里有遗漏，但并不是表示要从规范中省略它。

### B1. Increments
以下结构，认知复杂度 +1

- `if`, `else if`, `else`, ternary operator（三元运算符）
- `switch`
- `for`, `foreach`
- `while`, `do while`
- `catch`
- `goto LABEL`, `break LABEL`, `continue LABEL`
- 二元逻辑算符的序列
- 递归调用的方法

### B2. Nesting level
以下结构，嵌套级别 +1

- `if`, `else if`, `else`, ternary operator（三元运算符）
- `switch`
- `for`, `foreach`
- `while`, `do while`
- `catch`
- 嵌套方法和类似方法的结构，例如 **lambda**

### B3. Nesting increments
以下结构的嵌套增量与其在 B.2 结构中的嵌套深度相对应：

- `if`, ternary operator（三元运算符）
- `switch`
- `for`, `foreach`
- `while`, `do while`
- `catch`
