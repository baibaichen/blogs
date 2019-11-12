## Why (Most) Sampling Java Profilers Are Fucking Terrible（他妈的太糟糕了）

This post builds on the basis of a [previous post on safepoints](http://psy-lob-saw.blogspot.com/2015/12/safepoints.html). If you've not read it you might feel lost and confused. If you have read it, and still feel lost and confused, and you are certain this feeling is related to the matter at hand (as opposed to an existential crisis), please ask away. So, now that we've established what safepoints are, and that:

1. Safepoint polls are dispersed at fairly arbitrary points (depending on execution mode, mostly at uncounted loop back edge or method return/entry).
2. Bringing the JVM to a global safepoint is high cost

We have all the information we need to conclude that profiling by sampling at a safepoint is perhaps a bit shite. This will not come as a surprise to some, but this issue is present in the most commonly used profilers. According to this [survey](http://pages.zeroturnaround.com/RebelLabs---All-Report-Landers_Developer-Productivity-Report-2015.html?utm_source=performance-survey-report&utm_medium=allreports&utm_campaign=rebellabs&utm_rebellabsid=97) by RebelLabs this is the breakdown:

[![img](https://1.bp.blogspot.com/-NTpQ0vTOg9o/VsytuZ7EIvI/AAAAAAAACfU/7ITCc3qLuoA/s1600/which-profiler.png)](https://1.bp.blogspot.com/-NTpQ0vTOg9o/VsytuZ7EIvI/AAAAAAAACfU/7ITCc3qLuoA/s1600/which-profiler.png)

VisualVM, NB Profiler(same thing), YourKit and JProfiler all provide a sampling CPU profiler which samples at a **safepoint**. Seeing how this is a rather common issue, lets dig into it.

### How Sampling Execution Profilers Work (in theory)

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

### How Do Generic Commercial Java Sampling Execution Profilers Work?

Well, I can sort of reverse engineer here from different solutions, or read through open source code bases, but instead I'll offer unsupported conjecture and you are free to call me out on it if you know better. Generic profilers rely on the JVMTI spec, which all JVMs must meet:

- JVMTI offers only safepoint sampling stack trace collection options (GetStackTrace for the calling thread doesn't require a safepoint, but that is not very useful to a profiler. On Zing  GetStackTrace to another thread will bring only that thread to a safepoint.). It follows that vendors who want their tools to work on ALL JVMs are limited to safepoint sampling.
- You hit a global safepoint whether you are sampling a single thread or all threads (at least on OpenJDK, Zing is slightly different but as a profiler vendor OpenJDK is your assumption.). All profilers I looked into go for sampling all threads. AFAIK they also do not limit the depth of the stack collected. This amounts to the following JVMTI call: *JvmtiEnv::GetAllStackTraces(0, &stack_info, &thread_count)*
- So this adds up to: setup a timer thread which triggers at 'sampling_interval' and gathers all stack traces.

This is bad for several reasons, some of which are avoidable:

1. Sampling profilers need samples, so it is common to set sampling frequency is quite high (usually 10 times a second, or every 100ms). It's instructive to set the -XX:+PrintGCApplicationStoppedTime and see what sort of pause time this introduces. It's not unusual to see a few milliseconds pause, but YMMV(depending on number of threads, stack depth, TTSP etc). A 5ms pause every 100ms will mean a 5% overhead (actual damage is likely worse than that) introduced by your profiler. You can usually control the damage here by setting the interval longer, but this also means you will need a longer profiling period to get a meaningful sample count.
2. Gathering **full** stack traces from **all** the threads means your safepoint operation cost is open ended. The more threads your application has (think application server, SEDA architecture, lots of thread pools...), and the deeper your stack traces (think Spring and Co.) the longer your application will wait for a single thread to go round taking names and filling forms. This was clearly demonstrated in the previous post. AFAIK, current profilers do nothing to help you here. If you are building your own profiler it would seem sensible to set a limit on either quantities so that you can box your overheads. The JVMTI functionality allows you to query the list of current threads, you could sample all if there's less than a 100 and otherwise pick a random subset of 100 to sample. It would make sense to perhaps bias towards sampling threads that are actually doing something as opposed to threads which spend all their time blocked.
3. As if all that was not bad enough, sampling at a safepoint is a tad meaningless.

Points 1 and 2 are about profiling overheads, which is basically about cost. In my previous post on safepoints I looked at these costs, so there's no point repeating the exercise.  Cost may be acceptable for good profiling information, but as we'll see the information is not that great.



### Safepoint Sampling: Theory

So what does sampling at a safepoint mean? It means only the safepoint polls in the running code are visible. Given hot code is likely compiled by C1/C2 (client/server compilers) we have reduced our sampling opportunities to method exit and uncounted loop backedges. This leads to the phenomena called **the profiler should sample all points in a program run with equal probability**

This may not sound so bad at first, so lets work through a simple example and see which line gets the blame.

**NOTE**: In all of the following examples I will be using [JMH](http://psy-lob-saw.blogspot.co.za/p/jmh-related-posts.html) as the test harness and make use of the 'CompilerControl' annotation to prevent inlining. This will let me control the compilation unit limits, and may seem cruel and unusual, or at least unfair, of me. Inlining decisions in the 'wild' are governed by many factors and it is safe (in my opinion) to consider them arbitrary (in the hands of several compilers/JVM vendors/command line arguments etc.). Inlinining may well be the "mother of all optimizations", but it is a fickle and wily mother at that.
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

### Safepoint Sampling: Reality

Safepoint bias is discussed in this nice paper from 2010: [Evaluating the Accuracy of Java Profilers](http://plv.colorado.edu/papers/mytkowicz-pldi10.pdf), where the writers recognize that different Java profilers identify different hotspots in the same benchmarks and go digging for reasons. What they don't do is set up some benchmarks where the hotspot is known and use those to understand what it is that safepoint biased profilers see. They state:

> "If we knew the “correct” profile for a program run, we could evaluate the profiler with respect to this correct profile. Unfortunately, there is no “correct” profile most of the time and thus we cannot definitively determine if a profiler is producing correct results."

So, if we construct a known workload... what do these profilers see?

We'll study that with the JMH safepoint biased profiler "-prof stack". It is much like JVisualVM in the profiles it presents for the same code, and a damn sight more convenient for this study.
*NOTE: In the following sections I'm using the term sub-method to describe a method which is being called into from another method. E.g. if method A calls method B, B is a sub method of A. Maybe a better terminology exists, but that's what I mean here.*

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

1. Some non-inlined sub method is hot*
2. Some code (own method? inlined sub method? non-inlined sub method?) in an uncounted loop is hot*

Having line of code data can help disambiguate the above cases, but it's not really very useful as line of code data. A hot line of code would be indicative of:

1. Line has a method call: A method called from this line is hot (or maybe it's inlined sub-methods are)*
2. Line is a loop back edge: Some code (include inlined submethods) in this loop is hot*

 **Does that seem useful? Don't get your hopes up.*



### Mind The Gap

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


### Summary: What Is It Good For?

As demonstrated above, a safepoint sampling profiler can have a wildly inaccurate idea of where the hot code in your application is. This renders derived observations on "Running" threads pretty suspect, but they are at least correct observations on which threads are running. This doesn't mean they are entirely useless and sometimes all we need is a hint in the right direction for some good analysis to happen, but there's a potential for huge waste of time here. While samples of code running in the interpreter do not suffer safepoint bias, this is not very useful as hot code is quickly compiled. If your hot code is running in the interpreter you have bigger fish to fry than safepoint bias...

The stack traces for blocked threads are accurate, and so the "Waiting" profile is useful to discover blocked code profile. If blocking methods are at the root of your performance issue than this will be a handy observation.

There are better options out there! I'll get into some of them in following posts:

\- Java Mission Control

\- Solaris Studio

\- Honest-Profiler

\- Perf + perf-map-agent (or [perfasm](http://psy-lob-saw.blogspot.com/2015/07/jmh-perfasm.html) if your workload is wrapped in a JMH benchmark)

No tool is perfect, but all of the above are a damn sight better at identifying where CPU time is being spent.

----

## 糟糕

本文建立在[上一篇关于安全点的文章](http://psy-lob-saw.blogspot.com/2015/12/safepoints.html)基础上。如果您没有阅读过，您可能会感到迷茫和困惑。如果您已阅读该书，但仍感到迷茫和困惑，并且确定这种感觉与手头的事情有关（而不是与存在的危机相对），继续往下看吧。 因此，既然我们已经确定了什么安全点，那么：

1. 安全点轮询分散在相当任意的点上（<u>取决于执行模式，主要是在未计数的循环后缘或方法返回/入口</u>）。
2. 将JVM带到全局安全点成本很高。

我们掌握了所有需要的信息，可以得出结论：**通过在安全点进行采样进行性能分析可能会有些不足**。 对于某些人来说，这并不奇怪，但是这个问题在最常用的分析器中是存在的。根据RebelLabs的[调查](http://pages.zeroturnaround.com/RebelLabs---All-Report-Landers_Developer-Productivity-Report-2015.html?utm_source=performance-survey-report&utm_medium=allreports&utm_campaign=rebellabs&utm_rebellabsid=97)，最常用的分析器是这样细分的：

[![img](https://1.bp.blogspot.com/-NTpQ0vTOg9o/VsytuZ7EIvI/AAAAAAAACfU/7ITCc3qLuoA/s1600/which-profiler.png)](https://1.bp.blogspot.com/-NTpQ0vTOg9o/VsytuZ7EIvI/AAAAAAAACfU/7ITCc3qLuoA/s1600/which-profiler.png)

VisualVM、NB Profiler（同样的东西）、YourKit和JProfiler都提供了一个采样**CPU的Profiler**，在**安全点**进行采样。看到这是一个相当普遍的问题，让我们深入探讨一下。