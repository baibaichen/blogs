#The world beyond batch: Streaming 102

> 术语
>
> 1. Bounded data： 有穷数据
> 2. Unbounded data：无穷数据
> 3. Pipeline：管道
> 4. ==transformation==：转换
> 5. windowing：窗口，分窗

## 简介

欢迎回来！如果你错过了我上次的文章， [The world beyond batch: Streaming 101](https://www.oreilly.com/ideas/the-world-beyond-batch-streaming-101)，我强烈建议你先花时间读读。那篇文章为本文讨论的主题奠定了必要的基础，我会假设你已经熟悉这里介绍的术语和概念。==读者们请知道这点==。

另外，请注意，这篇文章包含许多动画，所以尝试打印它们的人将会丢失一些最好的部分。打印机请知道这点 :haha:

免责声明结束，派对开始。 简要回顾一下，上次主要集中在三个方面：**精确定义术语**，比如像“Streaming”这样滥用术语的含义；**对比批处理系统和流式系统**，比较它们的理论能力，总结出只需要==完整性和时间工具==这两样东西，就可以让流式系统超越批处理系统；**分析了数据处理模式**，处理有界和无界数据时，批处理系统和流系统使用的基本方法。

在本文中，我想以具体的例子，通过更多的细节，深入分析上次讲过的数据理模式。主要分为两部分：

- Streaming 101 重温：简要介绍Streaming 101中引入的概念，并增加了一个运行的例子来突出显示要点。
- Streaming 102：Streaming 101的姊妹篇，详细介绍了处理无穷数据时其它的重要概念，并继续使用具体示例作为解释它们的工具。

本文覆盖的核心原则和概念，即，==推论时间的工具==，用于处理乱序数据，它们让流式系统真正超越批处理系统。

为了让读者理解这些概念在实战中的使用方式，我将利用Dataflow SDK中的代码片段（即Google Cloud Dataflow的API），再加上动画来可视化地表示。使用Dataflow SDK，而不用人们更熟悉的系统，比如Spark Streaming或Storm，是因为现在，其他系统无法表达本文涉及的所有例子。好消息是它们正朝着这个方向发展。更好的消息是，我们（Google）今天向Apache软件基金会提交了一项建议，以创建一个Apache Dataflow孵化器项目（与data Artisans，Cloudera，Talend等几家公司合作），希望能够基于Dataflow模型，围绕其强大的**乱序处理语义**，构建 一个开放的社区和生态系统。这应该会让2016年变得非常有趣，好吧，我有点离题。

抱歉，本文缺少了比较部分，我上次承诺过，但我错误地低估^[1]^了我想在本文包含的内容，以及完成的时间。现在，我不能再拖了，也不能为了它扩充更多的内容。如果要算安慰，我最终在2015年新加坡Strata + Hadoop World 大会上发表了一个演讲，标题是『[大规模数据处理的演进](http://conferences.oreilly.com/strata/big-data-conference-sg-2015/public/schedule/detail/44947)』（在[2016年6月的Strata + Hadoop World 的大会上](https://www.youtube.com/watch?v=9J_cWustI-A)也有更新），很多我想在比较部分中讨论的材料，都涵盖其中，幻灯片很漂亮，可为你提供阅读乐趣。内容不完全一样，但可以肯定的是，有干货。

现在，到流处理了。

## 回顾和展望

在Streaming 101中，我首先澄清了一些术语。以区分有穷数据和无穷数据开始：有穷数据源的数据量有限，常称为“批量”数据；无穷数据源则可能有无限数据量，常称为“流式”数据。涉及到数据源时，我尝试避免使用术语批量和流式，因为它们带有一些误导性的含义，且常有限制。

接着，我定义了批处理引擎和流式处理引擎的区别：批处理引擎的设计只考虑了有穷数据，而流式处理引擎则是针对无穷数据设计。我的目标是在引用执行引擎时才使用术语批处理和流式处理。

澄清完术语之后，我介绍了两个重要的基本概念，与处理无穷数据有关。我首先建立了事件时间（事件发生的时间）和处理时间（在处理过程中观察到事件的时间）之间的关键区别。这为Streaming 101中的一个==主要论题==提供了基础：如果你关心正确性和事件发生的上下文，你必须基于数据自带的事件时间，而不能以分析过程中处理数据时的时间（即处理时间）来分析数据。

接下来，我引入了分窗的概念（沿着时间边界划分数据集），==理论上==，无穷数据源可能永远不会结束，因此这是处理无穷数据源一种常用的方法。较为简单的分窗策略是固定窗口和滑动窗口，但更复杂的窗口类型，例如Session窗口（其中窗口由数据本身的特征定义，例如捕获每个用户的活动Session，如果用户随后不活动**==的间隙达到某个阀值，则Session结束==**）也有广泛的用途。

除了这两个，本文又多出三个概念：

- **水位**（Watermark）：**水位是在事件时间维度表示输入完整性的概念**。水位的值是X，表示的意思是“所有事件时间小于X的输入数据，都已经被观察到”。因此，当观察没有已知结束点的无穷数据源时。
- **触发器**（Trigger）：**触发器作为一种机制，用于声明在怎样的外部信号下，窗口应该有输出**。触发器提供了选择何时输出的灵活性。这也使得，随着时间的推移，窗口可能会输出多次。这反过来又打开了完善结果的大门，这样可以在数据到达时就提供一个猜测性的结果，随后当上游数据发生改变（有修订），或者相对于当前水位延迟的数据到达时（例如，移动的场景，在手机离线时记录各种动作及其对应的事件时间，恢复连接后再继续上载这些事件以便进行处理），==再输出完善后的结果==。
- **累积**（Accumulation）：**累积模式指定了同一窗口多次输出的关系**。<u>这些输出结果可能完全不相关（即随着时间的推移代表独立的变化），或者可能存在重叠。</u> 不同的累积模式具有不同的语义和与之相关的成本，因此要找到适用于自身场景的累计模式。


最后，为了更容易理解这些概念之间的关系，我们将回答四个==不同维度的==问题，借此重新审视旧的，并同时探索新的内容，对于处理无穷数据时遇到的每一个难题，每个维度都至关重要：

- ***What***：计算逻辑是**什么**？这个问题由管道内**transformation**的类型来回答，包括计算总和，构建直方图，训练机器学习模型等。这也基本上是传统批处理引擎要回答的问题。
- ***Where***：计算**什么时候**（事件时间维度上）的数据？这个问题通过在管道中使用基于事件时间的窗口来回答。包括Streaming 101中的常规窗口示例（固定窗口、滑动窗口，Session窗口），似乎不存在窗口概念的场景（例如，Streaming 101中描述的与时间无关的场景，传统批处理一般也属于此类），和其他更复杂的窗口类型，例如限时拍卖。还要注意，如果将记录进入系统时的处理时间作为其事件时间，那么窗口就是基于处理时间的。
- ***When***：在**什么时候**（处理时间维度上）进行计算，并输出结果？这个问题由水位和触发器来回答。这个主题有无限的变化，但是最常见的模式是，对于给定窗口，使用水位来描述其输入完成度，使用触发器允许输出早期结果（在窗口的输入结束之前，输出猜测性的，或者部分结果）和处理晚到的数据并输出晚期结果（在水位仅仅是窗口输入完整性估计的情况下，对于给定窗口，即使水位声明该窗口的输入已经结束，仍然可能会有更多的数据进入该窗口）。
- ***How***：**如何**细化**窗口多次输出**的结果？这个问题由用到的累积类型来回答：**丢弃**（每次输出的结果独立且不相关），**累积**（后续的结果建立在先前的结果上），或**累积且回收**（输出累积值，并回收上次触发时输出的结果）。

本文后面的文章中将会详细地讨论这些问题。是的，我将在本文中一直使用这种配色方案，试图使大家清楚地知道哪些概念与What / Where / When / How 中的哪个问题有关。不用谢<眨眼笑/>^[2]^。

## Streaming 101 重温

首先，我们来回顾一下Streaming 101中提出的一些概念，但是这一次还有一些详细的例子，有助于使这些概念更具体。

###*What*：transformation

传统批处理中使用**transformation**回答这个问题：计算逻辑是**什么**？尽管许多人可能早就熟悉传统的批处理，但还是要从此开始，因为我们将以它为基础，添加所有其他概念。

本节，我们看一个简单的例子：在一个由10个值组成的简单数据集上，按键值分组并求和。如果想稍微务实一点的话，你可以把它想像成为某个团队计算总分，团队正在玩某种手机游戏，需要将团队中个人成绩综合起来。可以想象，这也同样适用于计费和使用监控的场景。

对于每个示例，为了使管道的定义更具体，我将包含一个简短的Dataflow Java SDK伪码片段。我有时会修改规则以使示例更清晰，清除细节（比如没有引入具体的I / O 数据源）或简化名称（当前Java触发器的名称太TM冗长了，为了清晰起见，将使用更简单的名称）。除了这些小修改（大部分我在后记中有明确列举）之外，它基本上就是真正的Dataflow SDK代码。稍后，我还将提供实际[代码走读的链接](https://cloud.google.com/dataflow/examples/gaming-example)，那些对类似例子感兴趣的人，自己可以编译和运行。

如果你至少熟悉像Spark Streaming或Flink这样的系统，==你应该有一个相对轻松的时间来彻底了解Dataflow 的代码做了什么。~~也为你准备了一个速成课程~~==，Dataflow有两个基本的原语：

- `PCollection`s，表示数据集（可能是海量数据集），可以执行并行转换（名字开头“P”的由来，即表示parallel）。
- `PTransform`s：应用于`PCollection`s，以创建新的`PCollection`s。`PTransform`s可以执行元素级别的转换，也可以把多个元素聚合到一起，或是其它`PTransform`s的复合组合。

![图1，transformation的类型](102-figure-1.png) 图1，transformation的类型

如果发现自己越来越不能理解，或者只是想要查查参考手册，可以去看看[Dataflow Java SDK]()的官方文档。

对于我们的例子而言，假设从一个`PCollection <KV<String，Integer>>`开始，命名为`input`（即，包含字符串和整数的键/值对的`PCollection`，其中字符串类似于团队名称，整数是相应团队中的个人得分）。但构建真实管道时，可能从I / O源（如日志文件）读取数据以获得原始输入数据集（`PCollection <String>`），然后解析日志记录将其转换为相应的键/值对（`PCollection <KV <String，Integer >>`）。为了在第一个例子中清楚起见，我将包括所有这些步骤的伪码，但是在随后的例子中，我删除了I / O和解析部分。

因此从I / O源读取数据，解析出团队/分数这样的键/值对，计算每队总分，这个简单管道的伪码如下：

```java
PCollection<String> raw = IO.read(...);
PCollection<KV<String, Integer>> input = raw.apply(ParDo.of(new ParseFn());
PCollection<KV<String, Integer>> scores = input.apply(Sum.integersPerKey());
```
*列表1 Summation pipeline. Key/value data are read from an I/O source, with String (e.g., team name) as the key and Integer (e.g., individual team member scores) as the values. The values for each key are then summed together to generate per-key sums (e.g., total team score) in the output collection.*

对于所有示例，会先看构建管道的代码片段，并分析之，然后用动画演示管道在具体数据集上的执行情况。更具体地说，管道的输入数据集有10条不同的记录，但只有一个键值（即每条记录的键值一样）。在真实的管道中，你可以想象类似的操作将在多台机器上并行发生，但对于示例而言，越简单越清晰。

每个动画在两个维度上绘制输入和输出：事件时间（X轴）和处理时间（Y轴）。这样，管道的处理进度是从底部向上移动，表示了当前真实的处理时间，如图中的白色粗线所示。输入用圆圈表示，圆圈内的数字表示该条记录的值。圆圈开始是灰色，一旦管道处理了某条记录，圆圈就会变色。

当管道处理记录时，会将其累积到管道的内部状态中，并最终输出聚合的结果。状态和输出由矩形表示，聚合值靠近顶部显示，矩形由事件时间和处理时间表示，其覆盖区域里的数值会被累积到结果中。对于列表1中的管道，在传统的批处理引擎上执行的效果如下图：
> 译注：
> 1. 这里贴出的是静态图像，要看动图请点击<u>动画</u>
> 2. 输出最终结果时，靠近顶部显示的结果值会变色

![图2，传统的批处理](102-figure-2.png) 图2，传统的批处理，[动画](https://embedwistia-a.akamaihd.net/deliveries/3116f7c9159e25b3bd5ff05fa6a3adf1f53c6252/file.mp4)

Since this is a batch pipeline, it accumulates state until it’s seen all of the inputs (represented by the dashed green line at the top), at which point, it produces its single output of 51. In this example, we’re calculating a sum over all of event time since we haven’t applied any specific windowing transformations; hence, the rectangles for state and output cover the entirety of the X axis. If we want to process an unbounded data source, however, classic batch processing won’t be sufficient; we can’t wait for the input to end since it effectively never will. One of the concepts we’ll want is windowing, which we introduced in Streaming 101. Thus, within the context of our second question: “Where in event-time are results calculated?” we’ll now briefly revisit windowing.

由于是批处理管道，因此==会积累状态==，直到==看到==所有的输入（在顶部的绿色虚线处）才产生单个输出51。本例没有使用窗口转换，所以对事件时间维度出现的所有值求和，因此，表示『状态和输出』的矩形覆盖了整个X轴。传统的批处理引擎无法处理无穷数据，因为不可能等到输入结束再计算，然而，无穷数据实际上永远不会结束。我们需要的一个概念是窗口，在Streaming 101中有介绍。因此，在我们的第二个问题的背景下：计算**什么时候**（事件时间维度上）的数据？现在简要回顾一下窗口。

###*Where*：窗口

如上次讨论的那样，分窗是沿着时间边界分割数据源的过程。 常见的窗口策略包括固定窗口，滑动窗口和Session窗口：

![图3](102-figure-3.png) *图3 Example windowing strategies. Each example is shown for three different keys, highlighting the difference between aligned windows (which apply across all the data) and unaligned windows (which apply across a subset of the data). Credit: Tyler Akidau, inspired by an illustration from Robert Bradshaw.*

To get a better sense of what windowing looks like in practice, let’s take our integer summation pipeline and window it into fixed, two-minute windows. With the Dataflow SDK, the change is a simple addition of a Window.into transform (highlighted in blue text):

```java
PCollection<KV<String, Integer>> scores = input
  .apply(Window.into(FixedWindows.of(Duration.standardMinutes(2))))
  .apply(Sum.integersPerKey());
```
列表2：Windowed summation code.

Recall that Dataflow provides a unified model that works in both batch and streaming, since semantically batch is really just a subset of streaming. As such, let’s first execute this pipeline on a batch engine; the mechanics are more straightforward, and it will give us something to directly compare against when we switch to a streaming engine.

![图4](102-figure-4.png) *图4. Windowed summation on a batch engine.[动画](https://embedwistia-a.akamaihd.net/deliveries/c525f1a06e7cfd19e37001d7c47fe33454591cfe/file.mp4)*

As before, inputs are accumulated in state until they are entirely consumed, after which output is produced. In this case, however, instead of one output we get four: a single output for each of the four relevant two-minute event-time windows.

At this point, we’ve revisited the two main concepts introduced in Streaming 101: the relationship between the event-time and processing-time domains, and windowing. If we want to go any further, we’ll need to start adding the new concepts mentioned at the beginning of this section: watermarks, triggers, and accumulation. Thus begins Streaming 102.

##Streaming 102