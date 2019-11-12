# Why (Most) Sampling Java Profilers Are Fucking Terrible（他妈的太糟糕了）

This post builds on the basis of a [previous post on safepoints](http://psy-lob-saw.blogspot.com/2015/12/safepoints.html). If you've not read it you might feel lost and confused. If you have read it, and still feel lost and confused, and you are certain this feeling is related to the matter at hand (as opposed to an existential crisis), please ask away. So, now that we've established what safepoints are, and that:

1. Safepoint polls are dispersed at fairly arbitrary points (depending on execution mode, mostly at uncounted loop back edge or method return/entry).
2. Bringing the JVM to a global safepoint is high cost

We have all the information we need to conclude that profiling by sampling at a safepoint is perhaps a bit shite. This will not come as a surprise to some, but this issue is present in the most commonly used profilers. According to this [survey](http://pages.zeroturnaround.com/RebelLabs---All-Report-Landers_Developer-Productivity-Report-2015.html?utm_source=performance-survey-report&utm_medium=allreports&utm_campaign=rebellabs&utm_rebellabsid=97) by RebelLabs this is the breakdown:

[![img](https://1.bp.blogspot.com/-NTpQ0vTOg9o/VsytuZ7EIvI/AAAAAAAACfU/7ITCc3qLuoA/s1600/which-profiler.png)](https://1.bp.blogspot.com/-NTpQ0vTOg9o/VsytuZ7EIvI/AAAAAAAACfU/7ITCc3qLuoA/s1600/which-profiler.png)

VisualVM, NB Profiler(same thing), YourKit and JProfiler all provide a sampling CPU profiler which samples at a **safepoint**. Seeing how this is a rather common issue, lets dig into it.

## How Sampling Execution Profilers Work (in theory)

Sampling profilers are supposed to approximate the 'time spent' distribution in our application by collecting samples of where our application is at different points in time. The data collected at each sample could be:

- current instruction
- current line of code
- current method
- current stack trace

The data can be collected for a single thread or all threads at each sample. What do we need to hold for sampling to work?

> "However, for sampling to produce results that are comparable to a full (unsampled) profile, the following two conditions must hold. **First, we must have a large number of samples to get statistically significant results.** For example, if a profiler collects only a single sample in the entire program run, the profiler will assign 100% of the program execution time to the code in which it took its sample and 0% to everything else. [...]
> **Second, the profiler should sample all points in a program run with equal probability.** If a profiler does not do so, it will end up with bias in its profile. For example, let’s suppose our profiler can only sample methods that contain calls. This profiler will attribute no execution time to methods that do not contain calls even though they may account for much of the program’s execution time." - from [Evaluating the Accuracy of Java Profilers](http://plv.colorado.edu/papers/mytkowicz-pldi10.pdf), we'll get back to this article in a bit

That sounds easy enough, right?

Once we have lots of samples we can construct a list of hot methods, or even code lines in those methods (if the samples report it), we can look at the samples distributed on the call tree (if call traces are collected) and have an awesome time!

## How Do Generic Commercial Java Sampling Execution Profilers Work?

Well, I can sort of reverse engineer here from different solutions, or read through open source code bases, but instead I'll offer unsupported conjecture and you are free to call me out on it if you know better. Generic profilers rely on the JVMTI spec, which all JVMs must meet:

- JVMTI offers only safepoint sampling stack trace collection options (GetStackTrace for the calling thread doesn't require a safepoint, but that is not very useful to a profiler. On Zing  GetStackTrace to another thread will bring only that thread to a safepoint.). It follows that vendors who want their tools to work on ALL JVMs are limited to safepoint sampling.
- You hit a global safepoint whether you are sampling a single thread or all threads (at least on OpenJDK, Zing is slightly different but as a profiler vendor OpenJDK is your assumption.). All profilers I looked into go for sampling all threads. AFAIK they also do not limit the depth of the stack collected. This amounts to the following JVMTI call: *JvmtiEnv::GetAllStackTraces(0, &stack_info, &thread_count)*
- So this adds up to: setup a timer thread which triggers at 'sampling_interval' and gathers all stack traces.

This is bad for several reasons, some of which are avoidable:

1. Sampling profilers need samples, so it is common to set sampling frequency is quite high (usually 10 times a second, or every 100ms). It's instructive to set the -XX:+PrintGCApplicationStoppedTime and see what sort of pause time this introduces. It's not unusual to see a few milliseconds pause, but YMMV(depending on number of threads, stack depth, TTSP etc). A 5ms pause every 100ms will mean a 5% overhead (actual damage is likely worse than that) introduced by your profiler. You can usually control the damage here by setting the interval longer, but this also means you will need a longer profiling period to get a meaningful sample count.
2. Gathering **full** stack traces from **all** the threads means your safepoint operation cost is open ended. The more threads your application has (think application server, SEDA architecture, lots of thread pools...), and the deeper your stack traces (think Spring and Co.) the longer your application will wait for a single thread to go round taking names and filling forms. This was clearly demonstrated in the previous post. AFAIK, current profilers do nothing to help you here. If you are building your own profiler it would seem sensible to set a limit on either quantities so that you can box your overheads. The JVMTI functionality allows you to query the list of current threads, you could sample all if there's less than a 100 and otherwise pick a random subset of 100 to sample. It would make sense to perhaps bias towards sampling threads that are actually doing something as opposed to threads which spend all their time blocked.
3. As if all that was not bad enough, sampling at a safepoint is a tad meaningless.

Points 1 and 2 are about profiling overheads, which is basically about cost. In my previous post on safepoints I looked at these costs, so there's no point repeating the exercise.  Cost may be acceptable for good profiling information, but as we'll see the information is not that great.

> 1. YMMV: Your mileage may vary
> 2. TTSP: Time To Safepoint



## Safepoint Sampling: Theory

So what does sampling at a safepoint mean? It means only the safepoint polls in the running code are visible. Given hot code is likely compiled by C1/C2 (client/server compilers) we have reduced our sampling opportunities to method exit and uncounted loop backedges. This leads to the phenomena called **the profiler should sample all points in a program run with equal probability**

This may not sound so bad at first, so lets work through a simple example and see which line gets the blame.

**NOTE**: In all of the following examples I will be using [JMH](http://psy-lob-saw.blogspot.co.za/p/jmh-related-posts.html) as the test harness and make use of the 'CompilerControl' annotation to prevent inlining. This will let me control the compilation unit limits, and may seem cruel and unusual, or at least unfair, of me. Inlining decisions in the 'wild' are governed by many factors and it is safe (in my opinion) to consider them arbitrary (in the hands of several compilers/JVM vendors/command line arguments etc.). Inlining may well be the "mother of all optimizations", but it is a fickle and wily mother at that.

Let's look at something simple:

```java
  @Param("1000")
  int size;
  byte[] buffer;
  boolean result;


  @Setup
  public final void setup() {
    buffer = new byte[size];
  }


  @Benchmark
  @CompilerControl(CompilerControl.Mode.DONT_INLINE)
  public void meSoHotNoInline() {
    byte b = 0;
    for (int i = 0; i < size; i++) {
      b += buffer[i];
    }
    result = b == 1;
    // SP poll, method exit
  }


  @Benchmark
  public void meSoHotInline() {
    byte b = 0;
    for (int i = 0; i < size; i++) {
      b += buffer[i];
    }
    result = b == 1;
    // SP poll, method exit (removed when method is inlined)
  }
```

This is an easy example to think about. We can control the amount of work in the method by changing the size of the array. We know the counted loop has no safepoint poll in it (verified by looking at the assembly output), so in theory the methods above will have a safepoint at method exit. The thing is, if we let the method above get inlined the end of method safepoint poll will disappear, and the next poll will be in the measurement loop:

```java
public void meSoHotInline_avgt_jmhStub(InfraControl control, RawResults result, SafepointProfiling_jmhType l_safepointprofiling0_0, Blackhole_jmhType l_blackhole1_1) throws Throwable {
  long operations = 0;
  long realTime = 0;
  result.startTime = System.nanoTime();
  do {
    l_safepointprofiling0_0.meSoHotInline(); /* LINE 163 */
    operations++;
    // SP poll, uncounted loop
  } while (!control.isDone); /* LINE 165 */
  result.stopTime = System.nanoTime();
  result.realTime = realTime;
  result.measuredOps = operations;
  // SP poll, method exit
}
```

So, it would seem reasonable to expect the method to get blamed if it is not inlined, but if it does get inlined we can expect the measurement method to get blamed. Right? very reasonable, but a bit off.

## Safepoint Sampling: Reality

Safepoint bias is discussed in this nice paper from 2010: [Evaluating the Accuracy of Java Profilers](http://plv.colorado.edu/papers/mytkowicz-pldi10.pdf), where the writers recognize that different Java profilers identify different hotspots in the same benchmarks and go digging for reasons. What they don't do is set up some benchmarks where the hotspot is known and use those to understand what it is that safepoint biased profilers see. They state:

> "If we knew the “correct” profile for a program run, we could evaluate the profiler with respect to this correct profile. Unfortunately, there is no “correct” profile most of the time and thus we cannot definitively determine if a profiler is producing correct results."

So, if we construct a known workload... what do these profilers see?

We'll study that with the JMH safepoint biased profiler "-prof stack". It is much like JVisualVM in the profiles it presents for the same code, and a damn sight more convenient for this study.

**NOTE**: In the following sections I'm using the term sub-method to describe a method which is being called into from another method. E.g. if method A calls method B, B is a sub method of A. Maybe a better terminology exists, but that's what I mean here.

If we run the samples above we get 2 different hot lines of code (`run with -prof stack:detailLine=true`):

```bash
# Benchmark: safepoint.profiling.SafepointProfiling.meSoHotInline
....[Thread state: RUNNABLE]...
 99.6%  99.8% meSoHotInline_avgt_jmhStub:165

# Benchmark: safepoint.profiling.SafepointProfiling.meSoHotNoInline
....[Thread state: RUNNABLE]...
 99.4%  99.6% meSoHotNoInline_avgt_jmhStub:163
```

Neither one in the actually hot method. It seems that the method exit safepoint is not deemed indicative of it's own method, but rather of the line of code from which it was called. So forcing the method under measurement to not inline implicated the calling line of code in the measurement loop, while leaving it to inline meant the back edge of the loop got the blame. It also appears that an uncounted loop safepoint poll is deemed indicative of it's own method.

We may deduce(but we won't necessarily be correct) that when looking at this kind of profile without line of code data a hot method is indicative of:

1. Some non-inlined sub method is hot^1^
2. Some code (own method? inlined sub method? non-inlined sub method?) in an uncounted loop is hot^1^

Having line of code data can help disambiguate the above cases, but it's not really very useful as line of code data. A hot line of code would be indicative of:

1. Line has a method call: A method called from this line is hot (or maybe it's inlined sub-methods are)^1^
2. Line is a loop back edge: Some code (include inlined submethods) in this loop is hot^1^

> 1. Does that seem useful? Don't get your hopes up.

Because we usually have no idea which methods got inlined this can be a bit confusing (you can use `-XX:+PrintInlining` if you want to find out, but be aware that inlining decisions can change from run to run).

## Mind The Gap

If the above rules held true you could use a safepoint biased profile by examining the code under the blamed node in the execution tree. In other words, it would mean a hot method indicates the hot code lies somewhere in that code or in the method it calls. This would be good to know, and the profile could serve as a starting point for some productive digging. But sadly, these rules do not always hold true. They are missing the fact that the hot code can be anywhere between the indicated safepoint poll and the previous one. Consider the following example:

```java
 @Benchmark
  public void blameSetResult() {
    byte b = 0;
    for (int i = 0; i < size; i++) {
      b += buffer[i];
    }
    setResult(b); /* LINE 38 */
  }

  private void setResult(byte b) {
    setResult(b == 1); /* LINE 90 */
  }

  @CompilerControl(CompilerControl.Mode.DONT_INLINE)
  private void setResult(boolean b) {
    result = b;
  }
/*
....[Thread state: RUNNABLE]........................................................................
 98.6%  98.8% safepoint.profiling.SafepointProfiling.setResult:90
  0.2%   0.2% sun.misc.Unsafe.unpark:-2
  0.2%   0.2% safepoint.profiling.SafepointProfiling.blameSetResult:38
  0.2%   0.2% safepoint.profiling.generated.SafepointProfiling_blameSetResult_jmhTest.blameSetResult_avgt_jmhStub:165
*/  
```

Clearly, the time is spent in the loop before calling setResult, but the profile blames setResult. There's nothing wrong with setResult except that a method it calls into is not inlined, providing our profiler with a blame opportunity. This demonstrates the randomness with which the safepoint poll opportunities present themselves to  user code, and shows that the hot code may be anywhere between the current safepoint poll and the previous one. This means that a hot method/line-of-code in a safepoint biased profile are potentially misleading without some idea of where the previous safepoint poll is. Consider the following example:

```java
  @Benchmark
  @CompilerControl(CompilerControl.Mode.DONT_INLINE)
  public void blameSetResultDeeper() {
    byte b = 0;
    for (int i = 0; i < size; i++) {
      b += buffer[i];
    }
    setResult8(b);
  }

  private void setResult8(byte b) {
    setResult7(b);
  }

  private void setResult7(byte b) {
    setResult6(b);
  }

  @CompilerControl(CompilerControl.Mode.DONT_INLINE)
  private void setResult6(byte b) {
    setResult5(b);
  }

  private void setResult5(byte b) {
    setResult4(b);
  }

  private void setResult4(byte b) {
    setResult3(b);
  }

  private void setResult3(byte b) {
    setResult2(b);
  }

  private void setResult2(byte b) {
    setResult(b); /* Line 86 */
  }

  private void setResult(byte b) {
    setResult(b == 1); /* LINE 90 */
  }

  @CompilerControl(CompilerControl.Mode.DONT_INLINE)
  private void setResult(boolean b) {
    result = b;
  }
/*....[Thread state: RUNNABLE]........................................................................
 99.2%  99.4% safepoint.profiling.SafepointProfiling.setResult:90
  0.2%   0.2% safepoint.profiling.generated.SafepointProfiling_blameSetResultDeeper_jmhTest.blameSetResultDeeper_avgt_jmhStub:163
  0.2%   0.2% sun.misc.Unsafe.compareAndSwapInt:-2
  0.2%   0.2% safepoint.profiling.SafepointProfiling.setResult2:86
*/
```
The profiler implicates the caller to a cheap method 9 levels down the stack, but the real culprit is the loop at the topmost method. Note that inlining will prevent a method from showing, but not-inlined frames only break the gap between safepoints on return (on OpenJDK anyhow. This is vendor and whim dependent. e.g. Zing puts the method level safepoint on entry for instance and I'm not sure where J9 stands on this issue. This is not to say that one way is somehow better than the other, just that the location is arbitrary). This is why setResult6 which is not inlined and higher up the stack doesn't show up.


## Summary: What Is It Good For?

As demonstrated above, a safepoint sampling profiler can have a wildly inaccurate idea of where the hot code in your application is. This renders derived observations on "Running" threads pretty suspect, but they are at least correct observations on which threads are running. This doesn't mean they are entirely useless and sometimes all we need is a hint in the right direction for some good analysis to happen, but there's a potential for huge waste of time here. While samples of code running in the interpreter do not suffer safepoint bias, this is not very useful as hot code is quickly compiled. If your hot code is running in the interpreter you have bigger fish to fry than safepoint bias...

The stack traces for blocked threads are accurate, and so the "Waiting" profile is useful to discover blocked code profile. If blocking methods are at the root of your performance issue than this will be a handy observation.

There are better options out there! I'll get into some of them in following posts:

- Java Mission Control

- Solaris Studio

- Honest-Profiler
- Perf + perf-map-agent (or [perfasm](http://psy-lob-saw.blogspot.com/2015/07/jmh-perfasm.html) if your workload is wrapped in a JMH benchmark)


No tool is perfect, but all of the above are a damn sight better at identifying where CPU time is being spent.

----

# 糟糕

本文建立在[上一篇关于安全点的](http://psy-lob-saw.blogspot.com/2015/12/safepoints.html)基础上。如果没读过，可能会感到迷茫和困惑。如果已读过，但仍感到迷茫和困惑，并且确定这种感觉与手头的事情有关（而不是与存在的危机相对），继续往下看吧。 那么，既然我们已经确定了什么是安全点，那么：

1. 安全点轮询分散在相当任意的点上（<u>取决于执行模式，主要是在未计数的循环后缘或方法返回/入口</u>）。
2. 将JVM带到全局安全点成本很高。

我们掌握了所有需要的信息，可以得出结论：**通过在安全点进行采样进行性能分析可能会有些不足**。 对于某些人来说，这并不奇怪，但是这个问题在最常用的分析器中是存在的。根据RebelLabs的[调查](http://pages.zeroturnaround.com/RebelLabs---All-Report-Landers_Developer-Productivity-Report-2015.html?utm_source=performance-survey-report&utm_medium=allreports&utm_campaign=rebellabs&utm_rebellabsid=97)，最常用的分析器是这样细分的：

[![img](https://1.bp.blogspot.com/-NTpQ0vTOg9o/VsytuZ7EIvI/AAAAAAAACfU/7ITCc3qLuoA/s1600/which-profiler.png)](https://1.bp.blogspot.com/-NTpQ0vTOg9o/VsytuZ7EIvI/AAAAAAAACfU/7ITCc3qLuoA/s1600/which-profiler.png)

VisualVM、NB Profiler（同样的东西）、YourKit和JProfiler都提供了一个采样**CPU的Profiler**，在**安全点**进行采样。看到这是一个相当普遍的问题，让我们深入探讨一下。

## 采样分析器如何工作（理论上）

**采样分析器**应该通过在不同的执行时间点上采样应用程序，来近似估算应用程序中“执行时间”的分布。每次采样的数据可以是：

- 当前指令

- 当前代码行

- 当前方法

- 当前堆栈跟踪

可以为单个线程或所有线程采样数据。怎样做才能采样分析器工作？

> “但是，要使采样结果与完整（未采样）的概况相媲美，必须满足以下两个条件。**首先，我们必须拥有大量样本才能获得具有统计意义的结果。**例如，如果分析器在整个程序运行中只收集一个样本，分析器会将程序执行时间的100％分配给采样所在的代码，将0％分配给其他所有代码。[...]
>
> **其次，分析器应以相同的概率对程序运行中的所有点进行采样。**如果不这么做，则其采样结果将带有偏差。 例如，假设分析器只能对包含`call`的方法进行采样，那就不会将任何执行时间归因于不包含`call`的方法，即使这些有`call`的方法可能占据程序大部分执行时间。” 来自于[评估Java探查器的准确性（Evaluating the Accuracy of Java Profiler）](http://plv.colorado.edu/papers/mytkowicz-pldi10.pdf)，我们将稍后再回到该文

听起来很简单，对吧？

一旦有了很多样本，就可以构造一个热门方法列表，甚至那些方法中的代码行（如果样本有这样的信息）；还可以查看调用树上的样本分布（如果收集了调用堆栈信息），很爽吧！

## 商业的Java采样分析器一般如何工作？

好吧，我可以从不同的解决方案中进行逆向工程，或者通读开源代码库，但是我会提供不受支持的推测，如果您了解得更多，可以随时给我打电话。通用分析器依赖于**JVMTI**规范，所有**JVM**都必须满足该规范：

- JVMTI只提供了在**安全点**收集<u>采样点堆栈信息</u>的选项（对于调用线程，`GetStackTrace`不需要安全点，但这对分析器不是很有用。在Zing上，在一个线程调用另一个线程的`GetStackTrace`，只会将这个线程带到安全点）。因此，希望其工具在所有JVM上工作的供应商，只能在安全点进行采样。
- 无论是对单个线程还是所有线程进行采样，都会达到全局安全点（OpenJDK至少是这样，Zing略有不同，但作为分析器供应商，要能在OpenJDK上工作）。调查过的所有分析器都要对所有线程进行采样。据我所知，它们也不限制堆栈收集的深度。这相当于以下JVMTI调用：`JvmtiEnv：：GetAllStackTraces(0，&stack_info，&thread_count)`
- 所以这就意味着：设置一个定时器线程，按“采样频率”触发，并收集所有线程的堆栈。

**==这不太好，原因有几个，其中一些可以避免==**：

1. 采样分析器需要采样，因此通常设置很高的采样频率（通常为每秒10次，或每100毫秒）。设置`-XX:+ PrintGCApplicationStoppedTime`并查看它会引入何种暂停时间具有指导意义。出现几毫秒的暂停并不罕见，不过每个人的结果可能不同（取决于线程数、堆栈深度、在安全点的时间等）。每100毫秒暂停5毫秒，意味着分析器会带来5%的开销（实际伤害可能会更糟）。通常可以通过设置<u>更长的时间间隔</u>来控制开销，但这也意味着需要更长的分析时间才能获得有意义的样本数。
2. 从所有线程中收集全部堆栈信息，这意味着您的安全点操作成本是开放的。应用程序拥有的线程越多（考虑应用服务器、[SEDA架构](https://www.zybuluo.com/boothsun/note/887056)、大量线程池……），堆栈越深（比如Spring），获取应用程序单个线程的名称和<u>填写表单</u>的时间就越长。在上一篇文章中已经清楚地证明了这一点。 据我所知，当前的分析对此毫无办法。 如果构建自己的分析器，限制这两个数量似乎很明智，这样就可以限制开销。 **JVMTI**提供查询当前线程列表的功能，如果少于100，则对所有线程进行采样，否则随机选择100个线程进行采样。也许偏向于采样<u>实际上正在做事</u>的线程，而不是采样<u>所有时间都在阻塞</u>的线程更有意义。
3. 似乎所有这些还不够糟糕，因为在安全点进行采样毫无意义。

第1点和第2点与性能分析开销有关，基本上与成本有关。在上一篇关于安全点的文章中，我研究了这些成本，因此没必要重复。对于好的分析信息，成本是可以接受的，但正如我们将看到的，信息并不是那么好。

## 安全点采样：理论

那么，在安全点采样意味着什么？这意味着只能在运行代码的安全点采样。由于C1/C2（客户端/服务器编译器）可能会编译热代码，已经减少了抽样机会，只有在方法退出和[Uncounted loop回跳的](https://juejin.im/post/5d1b1fc46fb9a07ef7108d82#heading-8)时候进行采样。<u>这导致了这样的现象，即</u>，分析器应以相等的概率对程序运行中的所有点进行采样。

初听起来可能并不差，所以让我们通过一个简单的例子，来看看<u>热点应该归因到哪一行</u>。

**注意**：以下所有示例中，我将使用[JMH](http://psy-lob-saw.blogspot.co.za/p/JMH-related-posts.html)作为测试工具，并使用`CompilerControl`注释来防止内联。这将使我能控制编译单元的限制，对我来说可能看起来很残酷，不寻常或至少不公平。“野生”的内联决策受许多因素控制，（在我看来）可以认为它们是任意的（掌握在一些编译器/JVM供应商/命令行参数手中）。内联很可能是“所有优化之母”，但那是一个善变而狡猾的母亲。

看一些简单的东西：

```java
@Param("1000")
int size;
byte[] buffer;
boolean result;


@Setup
public final void setup() {
  buffer = new byte[size];
}


@Benchmark
@CompilerControl(CompilerControl.Mode.DONT_INLINE)
public void meSoHotNoInline() {
  byte b = 0;
  for (int i = 0; i < size; i++) {
    b += buffer[i];
  }
  result = b == 1;
  // SP poll, method exit
}


@Benchmark
public void meSoHotInline() {
  byte b = 0;
  for (int i = 0; i < size; i++) {
    b += buffer[i];
  }
  result = b == 1;
  // SP poll, method exit (removed when method is inlined)
}
```

这是一个容易思考的例子。我们可以通过更改数组的大小来控制方法中的工作量。 我们知道[计数循环](https://juejin.im/post/5d1b1fc46fb9a07ef7108d82#heading-7)中没有安全点轮询（通过查看<u>字节码</u>进行验证），因此从理论上讲，在方法退出时将有一个安全点。问题是，如果让上述方法内联，则方法退出处的安全点轮询将消失，而下一个轮询将位于测量循环中：

```java
public void meSoHotInline_avgt_jmhStub(InfraControl control, RawResults result, SafepointProfiling_jmhType l_safepointprofiling0_0, Blackhole_jmhType l_blackhole1_1) throws Throwable {
  long operations = 0;
  long realTime = 0;
  result.startTime = System.nanoTime();
  do {
    l_safepointprofiling0_0.meSoHotInline(); /* LINE 163 */
    operations++;
    // SP poll, uncounted loop
  } while (!control.isDone); /* LINE 165 */
  result.stopTime = System.nanoTime();
  result.realTime = realTime;
  result.measuredOps = operations;
  // SP poll, method exit
}
```

因此，如果没有内联，方法归因为热点似乎是合理的；但是如果方法确实内联了，我们可以期望测量方法归因为热点。对吗？很合理，但有点离谱。

## 安全点采样：现实

安全点偏差在2010年的一篇出色的论文中进行了讨论：[评估Java评测器的准确性](http://plv.colorado.edu/papers/mytkowicz-pldi10.pdf)。作者发现不同的Java分析器在同一个评测中标识了不同的热点，并挖掘了原因。他们没做的是在热点已知的地方建立一些基准，并使用这些基准来了解有安全点偏差的分析器看到的内容。他们说：

> “如果有“分析器”可以正确分析程序的运行状态，就可以基于它评估分析器。不幸的是，大多数情况下没有“正确”的分析器，因此我们无法确切确定分析器是否正在产生正确的结果。”

所以，如果我们构造一个已知的工作负载...，这些分析器会看到什么？

我们使用**JMH**安全点偏差分析器 `-prof stack`进行研究。它为相同的代码提供的分析器，非常类似于**JVisualVM**，对于这项研究来说更方便。

**注意**：在以下各节中，我将用术语<u>子方法</u>描述被另一个方法调用的方法。例如。 如果方法A调用方法B，则B是A的子方法。也许存在更好的术语，但这就是我的意思。

如果运行上面的示例，则会得到2条不同的代码热点（使用 `-prof stack：detailLine=true`运行）：

```bash
# Benchmark: safepoint.profiling.SafepointProfiling.meSoHotInline
....[Thread state: RUNNABLE]...
 99.6%  99.8% meSoHotInline_avgt_jmhStub:165    # 内联，循环的跳转处

# Benchmark: safepoint.profiling.SafepointProfiling.meSoHotNoInline
....[Thread state: RUNNABLE]...
 99.4%  99.6% meSoHotNoInline_avgt_jmhStub:163  # 没有内联，方法调用处
```

两个都不是真正的热点。看来方法退出处的安全点<u>没被认为</u>是它自己的方法，而属于调用它的代码行。因此，强制不内联的测量方法，热点会归因到循环的方法调用处；而将其作为内联意味着，热点会归因到循环的跳转处。同时，[Uncounted loop安全点](https://juejin.im/post/5d1b1fc46fb9a07ef7108d82#heading-8)的轮询似乎被视为循环处方法的轮询（而非循环中调用的子方法）。

我们可以推断（但不一定正确），当没有代码时，查看此类分析器的分析结果时，一个热点方法表示：

1. 非内联子方法是热点^1^
2. Uncounted loop中的某些代码是热点（方法自身？内联的子方法？非内联的子方法？）^1^

有一行源代码就有助于消除上述情况的歧义，没有一行源代码则没太大用处。 热点代码将指示：

1. 热点代码行有一个方法调用：此处调用的方法很热（或者它是内联的子方法）^1^
2. 热点代码行是循环的回跳处：此循环中的某些代码（包括内联子方法）很热^1^

> 1. 这看起来有用吗？别抱太大希望

因为通常不知道内联哪些方法，所以这可能会有些混乱（可以使用`-X:+PrintInlining`如了解内联信息，但要知道内联决策可能会因运行而改变）。

## 注意空隙

如果上述规则成立，使用有安全点偏差的分析器时，可以通过检查执行树中显示热点下的代码来归因。换句话说，它意味着一个热点方法指示热点代码位于该代码或它调用的子方法中的某个位置。知道这一点很不错，分析器可以作为一个高效分析的起点。但不幸的是，这些规则并不总是正确的。因为忽略了这样的事实：即热代码可以在指示的安全点轮询和上一个安全点轮询之间的任何位置。 考虑以下示例：

```java
 @Benchmark
  public void blameSetResult() {
    byte b = 0;
    for (int i = 0; i < size; i++) {
      b += buffer[i];
    }
    setResult(b); /* LINE 38 */
  }

  private void setResult(byte b) {
    setResult(b == 1); /* LINE 90 */
  }

  @CompilerControl(CompilerControl.Mode.DONT_INLINE)
  private void setResult(boolean b) {
    result = b;
  }
/*
....[Thread state: RUNNABLE]........................................................................
 98.6%  98.8% safepoint.profiling.SafepointProfiling.setResult:90
  0.2%   0.2% sun.misc.Unsafe.unpark:-2
  0.2%   0.2% safepoint.profiling.SafepointProfiling.blameSetResult:38
  0.2%   0.2% safepoint.profiling.generated.SafepointProfiling_blameSetResult_jmhTest.blameSetResult_avgt_jmhStub:165
*/  
```
显然时间花在循环中（调用`setResult`之前），但是分析器将热点归因于`setResult`。`setResult`没有问题，除了它调用没有内联的子方法，这为我们的分析器提供了一个归因热点的地方。这说明了安全点轮询用户代码呈现了自身的随机性，表明热点代码可能位于当前安全点轮询和上一个安全点轮询之间的任何位置。这意味着，如果不知道上一个安全点轮询在哪里，安全点偏差分析器所指示的热点方法和代码行可能会产生误导。考虑下面的例子：

```java
  @Benchmark
  @CompilerControl(CompilerControl.Mode.DONT_INLINE)
  public void blameSetResultDeeper() {
    byte b = 0;
    for (int i = 0; i < size; i++) {
      b += buffer[i];
    }
    setResult8(b);
  }

  private void setResult8(byte b) {
    setResult7(b);
  }

  private void setResult7(byte b) {
    setResult6(b);
  }

  @CompilerControl(CompilerControl.Mode.DONT_INLINE)
  private void setResult6(byte b) {
    setResult5(b);
  }

  private void setResult5(byte b) {
    setResult4(b);
  }

  private void setResult4(byte b) {
    setResult3(b);
  }

  private void setResult3(byte b) {
    setResult2(b);
  }

  private void setResult2(byte b) {
    setResult(b); /* Line 86 */
  }

  private void setResult(byte b) {
    setResult(b == 1); /* LINE 90 */
  }

  @CompilerControl(CompilerControl.Mode.DONT_INLINE)
  private void setResult(boolean b) {
    result = b;
  }
/*....[Thread state: RUNNABLE]........................................................................
 99.2%  99.4% safepoint.profiling.SafepointProfiling.setResult:90
  0.2%   0.2% safepoint.profiling.generated.SafepointProfiling_blameSetResultDeeper_jmhTest.blameSetResultDeeper_avgt_jmhStub:163
  0.2%   0.2% sun.misc.Unsafe.compareAndSwapInt:-2
  0.2%   0.2% safepoint.profiling.SafepointProfiling.setResult2:86
*/
```
真正的罪魁祸首是最顶层方法的循环，但分析器指示调用堆栈第9层的廉价方法为热点。请注意，内联会阻止方法在堆栈中出现，但未内联的帧只会打破返回时安全点之间的间隙（这依赖于实现，例如**Zing**将方法级别的安全点放在输入上，不太清楚Java9是怎么实现的。这并不是说一种方法总比另一种好，只是位置是任意的）。这就是为什么没有内联，且堆栈更高的`setResult6`不会出现的原因。

## 小结：有什么好处？

## 参考

1. [HBase实战：记一次Safepoint导致长时间STW的踩坑之旅](https://blog.csdn.net/pengzhouzhou/article/details/94516616)

2. [Safepoint学习笔记](http://blog.yongbin.me/2016/11/23/safepoint/)

3. [JVM CPU Profiler技术原理及源码深度解析](https://juejin.im/post/5da3d803e51d4577e9749bb4#heading-7)