# [背景](https://en.wikipedia.org/wiki/Existential_crisis)

最近正好准备要做Benchmark相关的事，正好发现最新的 [IntelliJ IDEA 2019.2开始有Profiling的功能](https://www.jetbrains.com/help/idea/cpu-profiler.html)，**貌似**可以不再用JProfiler，或者VisualVM做本地的Profiler。IDEA的这个功能实际是集成了[Async Profiler](https://github.com/jvm-profiling-tools/async-profiler)和[Java Flight Recorder](https://docs.oracle.com/javacomponents/jmc-5-4/jfr-runtime-guide/about.htm#JFRUH170)。从[Async Profiler](https://github.com/jvm-profiling-tools/async-profiler)这引出了安全点偏差的问题。

1. 安全点相关的内容，[本文](#为啥大多数Java采样分析器不好用)
   1. [HBase实战：记一次Safepoint导致长时间STW的踩坑之旅](https://blog.csdn.net/pengzhouzhou/article/details/94516616)
   2. [Safepoint学习笔记](http://blog.yongbin.me/2016/11/23/safepoint/)
   
2. Profiler 相关的内容，[本文](#AsyncGetCallTrace分析器的优缺点)
   1. [JVM CPU Profiler技术原理及源码深度解析](https://juejin.im/post/5da3d803e51d4577e9749bb4#heading-7)
   2. [什么是即时编译?](./什么是即时编译.md)
   
3.  [如何读懂火焰图？](https://www.ruanyifeng.com/blog/2017/09/flame-graph.html)
  
   1. [官方](http://www.brendangregg.com/flamegraphs.html)
   
4. [JMH，官方的网页没啥内容](http://openjdk.java.net/projects/code-tools/jmh/)，这是另一个重要的主题

   1. [JAVA 拾遗 — JMH 与 8 个测试陷阱](https://www.cnkirito.moe/java-jmh/)

   2. [JMH Resources](http://psy-lob-saw.blogspot.com/p/jmh-related-posts.html)

      1. [Writing Java Micro Benchmarks with JMH: Juicy](http://psy-lob-saw.blogspot.com/2013/04/writing-java-micro-benchmarks-with-jmh.html)

5. 其它知识点

   1. Stub

      1. [Java Mock Frameworks Comparison](https://web.archive.org/web/20090711150137/http://www.sizovpoint.com/2009/03/java-mock-frameworks-comparison.html)
      2. [软件开发的中总能看到stub这个词。它在表述什么意思？](https://www.zhihu.com/question/21017494)

   2. [About the dynamic de-optimization of HotSpot](https://stackoverflow.com/questions/20522870/about-the-dynamic-de-optimization-of-hotspot)
   3. [AsyncGetCallTrace 源码深度剖析](https://club.perfma.com/article/145902)

   

[TOC]

# Why (Most) Sampling Java Profilers Are Fucking Terrible

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

# The Pros and Cons of AsyncGetCallTrace Profilers

So, going on from my [whingy post on safepoint bias](http://psy-lob-saw.blogspot.co.za/2016/02/why-most-sampling-java-profilers-are.html), where does one go to get their profiling kicks? One option would be to use an OpenJDK internal API call `AsyncGetCallTrace` to facilitate non-safepoint collection of stack traces.

AsyncGetCallTrace is NOT official JVM API. It's not a comforting place to be for profiler writers, and was only implemented on OpenJDK/Oracle JVMs originally (Zing has recently started supporting AGCT to enable support for Solaris Studio and other profilers, I will write a separate post on Studio). It's original use case was for Solaris Studio, and it provides the following API (see [forte.cpp](http://hg.openjdk.java.net/jdk8/jdk8/hotspot/file/tip/src/share/vm/prims/forte.cpp), the name is a left over from the Forte Analyzer days). Here's what the API adds up to:

```cpp
typedef struct {
  jint lineno;         // BCI in the source file
  jmethodID method_id; // method executed in this frame
} ASGCT_CallFrame;

typedef struct {
  JNIEnv *env_id   //Env where trace was recorded
  jint num_frames; // number of frames in this trace
  ASGCT_CallFrame *frames;
} ASGCT_CallTrace; 

void AsyncGetCallTrace(ASGCT_CallTrace *trace, // pre-allocated trace to fill
                       jint depth,             // max number of frames to walk up the stack
                       void* ucontext)         // signal context
```

Simple right? You give it a `ucontext` and it fills in the trace with call frames (or it gets the hose again).

## A Lightweight Honest Profiler

The 'async' in the name refers to the fact that AGCT is safe to call in a signal handler. This is mighty handy in a profiling API as it means you can implement your profiler as follows:

1. In a JVMTI agent, register a signal handler for signal X.
2. [Setup a timer](http://man7.org/linux/man-pages/man2/setitimer.2.html), triggering the signal X at the desired sample frequency. Honest Profiler is using the **ITIMER_PROF** option, which means we'll get signalled based on CPU time. The signal will be sent to the process and one of the running threads will end up being interrupted and calling into our signal handler. Note that this assumes the OS will distribute the signals fairly between threads so we will get a fair sample of all running threads.
3. From signal handler, call AGCT: Note that the interrupted thread (picked at 'random' from the threads currently executing on CPU) is now running your signal handler. The thread is NOT AT A SAFEPOINT. It may not be a Java thread at all.
4. Persist the call trace: Note that when running in a signal handler only 'async' code is legal to run. This means for instance that any blocking code is forbidden, including malloc and IO.
5. WIN!

The `ucontext` ingredient is the very same context handed to you by the signal handler (your signal handler is a callback of the signature *handle(int signum, siginfo_t \*info, void \*context)*). From it AGCT will dig up the instruction/frame/stack pointer values at the time of the interrupt and do it's best to find out where the hell you landed.

This exact approach was followed by Jeremy Manson, who explained the infrastructure and [open sourced a basic profiler](https://code.google.com/archive/p/lightweight-java-profiler/) (in a proof of concept, non commital sort of way). His great series of posts on the matter:

- [Profiling with JVMTI/JVMPI, SIGPROF and AsyncGetCallTrace](http://jeremymanson.blogspot.co.za/2007/05/profiling-with-jvmtijvmpi-sigprof-and.html)
- [More about profiling with SIGPROF](http://jeremymanson.blogspot.co.za/2007/06/more-about-profiling-with-sigprof.html)
- [More thoughts on SIGPROF, JVMTI and stack traces](http://jeremymanson.blogspot.co.za/2007/06/more-thoughts-on-sigprof-jvmti-and.html)
- [Why Many Profilers have Serious Problems (More on Profiling with Signals)](http://jeremymanson.blogspot.co.za/2010/07/why-many-profilers-have-serious.html)

The same code was then picked up by Richard Warburton and further improved and stabilized in [Honest-Profiler](https://github.com/RichardWarburton/honest-profiler) (to which I have made some contributions). Honest Profiler is an effort to make that initial prototype production ready, and compliment it with some tooling to make the whole thing usable. The serialization in a signal handler issue is resolved by using a lock-free MPSC ring-buffer of pre-allocated call trace structs (pointing to preallocated call frame arrays). A post-processing thread then reads the call traces, collects some extra info (like converting BCI to line number, jmethodIds into class names/file names etc) and writes to a log file. See the projects wiki for more details.

The log file is parsed offline (i.e. by some other process, on another machine, at some other time. Note that you can process the file while the JVM is still profiling, you need not wait for it to terminate.) to give you the hot methods/call tree breakdown. I'm going to use Honest-Profiler in this post, so if you want to try this out at home you're going to have to go and [build it](https://github.com/RichardWarburton/honest-profiler/wiki/How-to-build) to experiment locally (works on [OpenJDK](http://openjdk.java.net/)/[Zulu](https://www.azul.com/products/zulu/) 6/7/8 + [Oracle JVM](http://www.oracle.com/technetwork/java/javase/downloads/index.html)s + recent [Zing](https://www.azul.com/products/zing/) builds). I'll do some comparisons for the same experiments with JMC, you'll need an Oracle JVM (1.7u40 and later) to try that out.

## What Does AGCT Do?

By API we can say AGCT is a mapping between instruction/frame/stack pointer and a call trace. The call trace is an array of Java call frames (jmethodId, BCI). To produce this mapping the following process is followed:

1. Make sure thread is in 'walkable' state, in particular not when:
   - Thread is not a Java thread.
   - GC is active
   - New/uninitialized/just about to die. I.e. threads that are either before or after having Java code running on them are of no interest.
   - During a deopt

2. Find the current/last Java frame (as in actual frame on the stack, revisit Operating Systems 101 for definitions of stacks and frames):
   - The instruction pointer (commonly referred to as the PC - Program Counter) is used to look up a matching Java method (compiled/interpreter). The current PC is provided by the signal context.
   - If the PC is not in a Java method we need to find the last Java method calling into native code.
   - Failure is an option! we may be in an 'unwalkable' frame for all sorts of reasons... This is quite complex and if you must know I urge you to get comfy and dive into the maze of relevant code. Trying to qualify the top frame is where most of the complexity is for AGCT.

3. Once we have a top frame we can fill the call trace. To do this we must convert the real frame and PC into:
   - **Compiled call frames**: The PC landed on a compiled method, find the BCI (Byte Code Index) and record it and the jMethodId
   - **Virtual call frames**: The PC landed on an instruction from a compiled inlined method, record the methods/BCIs all the way up to the framing compiled method
   - **Interpreted call frames**
   - From a compiled/interpreted method we need to walk to the calling frame and repeat until we make it to the root of the Java call trace (or record enough call frames, whichever comes first)

4. WIN!

Much like medication list of potential side effects, the error code list supported by a function can be very telling. AGCT supports the following reasons for not returning a call trace:

```cpp
enum {
  ticks_no_Java_frame         =  0, // new thread
  ticks_no_class_load         = -1, // jmethodIds are not available
  ticks_GC_active             = -2, // GC action
  ticks_unknown_not_Java      = -3, // ¯\_(ツ)_/¯
  ticks_not_walkable_not_Java = -4, // ¯\_(ツ)_/¯
  ticks_unknown_Java          = -5, // ¯\_(ツ)_/¯
  ticks_not_walkable_Java     = -6, // ¯\_(ツ)_/¯
  ticks_unknown_state         = -7, // ¯\_(ツ)_/¯
  ticks_thread_exit           = -8, // dying thread
  ticks_deopt                 = -9, // mid-deopting code
  ticks_safepoint             = -10 // ¯\_(ツ)_/¯
}; 
```

While this data is reported from AGCT, it is often missing from the reports based on it. More on that later.

## The Good News!

So, looking at the bright side, we can now see between safepoint polls!!! How awesome is that? Lets see exactly how awesome by running the benchmarks which we could not measure correctly with the safepoint biased profiler in the previous post.

**Note**: Honest-Profiler reports (t X,s Y) which reflect the total % of stack trace samples containing this method+line vs. the self % of samples in which this method+line is the leaf. The output is sorted by self.:

```java
public class SafepointProfiling {
  @Param("1000")
  int size;
  byte[] buffer;
  byte[] dst;
  boolean result;

  @Setup
  public final void setup() {
    buffer = new byte[size];
    dst = new byte[size];
  }
  
  @Benchmark
  public void meSoHotInline() {
    byte b = 0;
    for (int i = 0; i < size; i++) {
      b += buffer[i];
    }
    result = b == 1;
  }
/*  
# Benchmark: safepoint.profiling.SafepointProfiling.meSoHotInline
JMH Stack profiler:
 99.6%  99.8% meSoHotInline_avgt_jmhStub:165

Honest Profiler:
 (t 98.7,s 98.7) meSoHotInline @ (bci=12,line=18)
 (t  0.8,s  0.8) meSoHotInline_avgt_jmhStub @ (bci=27,line=165)
*/
  @Benchmark
  @CompilerControl(CompilerControl.Mode.DONT_INLINE)
  public void meSoHotNoInline() {
    byte b = 0;
    for (int i = 0; i < size; i++) {
      b += buffer[i]; 
    }
    result = b == 1;
  }
/*
# Benchmark: safepoint.profiling.SafepointProfiling.meSoHotNoInline
JMH Stack profiler:
 99.4%  99.6% meSoHotNoInline_avgt_jmhStub:163

Honest Profiler:
 (t 98.9,s 98.9) meSoHotNoInline @ (bci=12,line=36)
 (t  0.4,s  0.4) AGCT::Unknown not Java[ERR=-3] @ (bci=-1,line=-100)
 (t  0.2,s  0.2) AGCT::Unknown Java[ERR=-5] @ (bci=-1,line=-100)
 (t  0.1,s  0.1) meSoHotNoInline_avgt_jmhStub @ (bci=27,line=165)
 (t 99.0,s  0.1) meSoHotNoInline_avgt_jmhStub @ (bci=14,line=163)
}*/
```

Sweet! Honest Profiler is naming the right methods as the hot methods, and nailing the right lines where the work is happening.

Let's try the copy benchmark, this time comparing the Honest-Profiler result with the JMC result:

```java
 @Benchmark
  public void copy() {
    byte b = 0;
    for (int i = 0; i < buffer.length; i++) {
      dst[i] = buffer[i];
    }
    result = dst[buffer.length-1] == 1;
  }
/*
# Honest-Profiler reports:
 (t 41.6,s 41.6) copy @ (bci=22,line=5)
 (t 33.3,s 33.3) copy @ (bci=23,line=4)
 (t  9.7,s  9.7) copy @ (bci=21,line=4)
 (t  8.5,s  8.5) copy @ (bci=8 ,line=4)
 (t  2.3,s  2.3) copy @ (bci=11,line=5)
# JMC reports:
Stack Trace                         |Samples| Percentage(%)
copy              line: 7   bci: 41 | 3,819 | 96.88
copy_avgt_jmhStub line: 165 bci: 27 |   122 |  3.095

# JMC reports with -XX:+UnlockDiagnosticVMOptions -XX:+DebugNonSafepoints:
Stack Trace                         |Samples| Percentage(%)
copy              line: 5   bci: 22 | 1,662 | 42.204
copy              line: 4   bci: 23 | 1,341 | 34.053
copy              line: 5   bci: 21 |   381 |  9.675
copy              line: 4   bci:  8 |   324 |  8.228
copy              line: 5   bci: 11 |    98 |  2.489
*/
```

Note the difference in reporting switching on the "-XX:+UnlockDiagnosticVMOptions -XX:+DebugNonSafepoints" flags makes to JMC. Honest-Profiler now ([recent change](https://github.com/RichardWarburton/honest-profiler/pull/133), hot off the press) causes the flag to be on without requiring user intervention, but JMC does not currently do that (this is understandable when attaching after the process started, but should perhaps be default if the process is started with recording on).

Further experimentation and validation exercises for the young and enthusiastic:

- Compare the Honest-Profiler results with JFR/JMC results on larger applications. What's different? Why do you think it's different?
- Run with -XX:+PrintGCApplicationStoppedTime to see no extra safepoints are caused by the profiling (test with profiler on/off).
- The extra-studious of y'all can profile the other cases discussed in previous post to see they similarly get resolved.

While this is a step forward, we still got some issues here... 

## Collection errors: Runtime Stubs

As we can see from the list of errors, any number of events can result in a failed sample. A particularly nasty type of failed sample is when you hit a runtime generated routine which AGCT fails to climb out of. Let see what happens when we don't roll our own array copy:

```java
@Benchmark
  public int systemArrayCopy() {
    System.arraycopy(buffer, 0, dst, 0, buffer.length);
    return dst[buffer.length-1];
  }
/*
# Flat Profile (by line):
 (t 62.9,s 62.9) AGCT::Unknown Java[ERR=-5] @ (bci=-1,line=-100)
 (t 19.5,s 19.5) systemArrayCopy_avgt_jmhStub @ (bci=29,line=165)
 (t 2.7,s 2.7) systemArrayCopy @ (bci=15,line=3)
 (t 2.1,s 2.1) systemArrayCopy @ (bci=26,line=4)
 (t 1.9,s 1.9) systemArrayCopy @ (bci=29,line=4)
 */
```

Now, this great unknown marker is a recent addition to Honest-Profiler, which increases it's honesty about failed samples. It indicates that for 62.9% of all samples, AGCT could not figure out what was happening and returned *ticks_unknown_java*. Given that there's very little code under test here we can deduce the missing samples all fall within System.arrayCopy (you can pick a larger array size to further prove the point, or use a native profiler for comparison).

Profiling the same benchmarks under JMC will not report failed samples, and the profiler will divide the remaining samples as if the failed samples never happened. Here's the JMC profile for systemArrayCopy:

```bash
Number of samples(over 60 seconds) : 2617
Method::Line                              Samples   %
systemArrayCopy_avgt_jmhStub(...) :: 165    1,729   66.043
systemArrayCopy()                 ::  41      331   12.643
systemArrayCopy()                 ::  42      208    7.945
systemArrayCopy_avgt_jmhStub(...) :: 166       93    3.552
systemArrayCopy_avgt_jmhStub(...) :: 163       88    3.361
```

JMC is reporting a low number of samples (the total is sort of available in the tree view as the number of samples in the root), but without knowing what the expected number of samples should be this is very easy to miss. This is particularly true for larger and noisier samples from real applications collected over longer period of time.

Is this phenomena isolated to System.arrayCopy? Not at all, here's crc32 and adler32 as a further comparison:

```java
@Benchmark
  public long adler32(){
    adler.update(buffer);
    return adler.getValue();
  }
/*
# Benchmark (size)  Mode  Cnt     Score   Error  Units
  adler32     1000  avgt   50  1003.068 ± 4.994  ns/op

# Flat Profile (by line):
  (t 98.9,s 98.9) java.util.zip.Adler32::updateBytes @ (bci=-3,line=-100)
  (t  0.3,s  0.3) AGCT::Unknown not Java[ERR=-3] @ (bci=-1,line=-100)
  (t  0.1,s  0.1) AGCT::Unknown Java[ERR=-5] @ (bci=-1,line=-100)
  (t  0.1,s  0.1) adler32_avgt_jmhStub @ (bci=32,line=165)
  (t  0.1,s  0.1) adler32 @ (bci=5,line=3)
*/	
  @Benchmark
  public long crc32(){
    crc.update(buffer);
    return crc.getValue();
  }
/*
# Benchmark (size)  Mode  Cnt    Score   Error  Units
  crc32       1000  avgt   50  134.341 ± 0.698  ns/op

# Flat Profile (by line):
 (t 96.7,s 96.7) AGCT::Unknown Java[ERR=-5] @ (bci=-1,line=-100)
 (t  1.4,s  0.6) crc32_avgt_jmhStub @ (bci=19,line=163)
 (t  0.3,s  0.3) AGCT::Unknown not Java[ERR=-3] @ (bci=-1,line=-100)
 (t  0.3,s  0.3) crc32_avgt_jmhStub @ (bci=29,line=165)
 */
```

What do Crc32 and System.arrayCopy have in common? They are both **<u>JVM intrinsics</u>**, replacing a method call (Java/native don't really matter, though in this case both are native) with a combination of inlined code and a call to a JVM runtime generated method. This method call is not guarded the same way a normal call into native methods is and thus the AGCT stack walking is broken.

Why is this important? The reason these methods are worth making into intrinsics is because they are sufficiently important bottlenecks in common applications. Intrinsics are like tombstones for past performance issues in this sense, and while the result is faster than before, the cost is unlikely to be completely gone.

Why did I pick CRC32? because I recently spent some time profiling Cassandra. Version 2 of Cassandra uses adler32 for checksum, while version 3 uses crc32. As we can see from the results above, it's potentially a good choice, but if you were to profile Cassandra 3 it would look better than it actually is because all the checksum samples are unprofilable. Profiling with a native profiler will confirm that the checksum cost is still a prominent element of the profile (of the particular setup/benchmark I was running).

**AGCT profilers are blind to runtime stubs (some or all, this may get fixed in future releases...). Failed samples are an indication of such a blind spot.**

Exercise to the reader:

- Construct a benchmark with heavy GC activity and profile. The CPU spent on GC will be absent from you JMC profile, and should show as *ticks_GC_active* in your Honest-Profiler profile.
- Construct a benchmark with heavy compiler activity and profile. As above look for compilation CPU. There's no compilation error code, but you should see allot of *ticks_unknown_not_Java* which indicate a non-Java thread has been interrupted ([this is a conflated error code, we'll be fixing it soon](https://github.com/RichardWarburton/honest-profiler/issues/138)).
- Extra points! Construct a benchmark which is spending significant time deoptimising and look for the *ticks_deopt* error in your Honest-Profiler profile.

## Blind Spot: Sleeping code

Since sleeping code doesn't actually consume CPU it will not appear in the profile. This can be confusing if you are used to the JVisualVM style reporting where all stacks are reported including WAITING (or sometimes RUNNING Unsafe.park). Profiling sleeping or blocked code is not something these profilers cover. I mention this not because they promise to do this and somehow fail to deliver, but because it's a common pitfall. For Honest-Profiler [this feature](https://github.com/RichardWarburton/honest-profiler/issues/126) should help highlight mostly off-CPU applications as such by comparing expected and actual samples (the delta is signals which were not delivered to the process as it was not running).

## Error Margin: Skidding + inlining

I'll dive deeper into this one another time, since other profilers share this issue. In essence the problem is that instruction profiling is not accurate and we can often encounter a skid effect when sampling the PC(program counter). This in effect means the instruction reported can be a number of instructions after the instruction where the time is spent. Since AsyncGetCallTrace relies on PC sampling to resolve the method and code line we get the same inaccuracy reflected through the layer of mapping (PC -> BCI -> LOC) but where on the assembly level we deal with a single blob of instructions where the proximity is meaningful, in converting back to Java we have perhaps slipped from one inlined method to the next and the culprit is no longer in a line of code nearby.

To be continued...

## Summary: What is it good for?

AsyncGetCallTrace is a step up from GetStackTraces as it operates at lower overheads and does not suffer from safepoint bias. It does require some mental model adjustments:

1. -XX:+UnlockDiagnosticVMOptions -XX:+DebugNonSafepoints : if this is not on you are still hobbled by the resolution of the debug data. This is different from safepoint bias, since you actually can sample anywhere, but the translation from PC to BCI kills you. I've not seen these flags result in measurable overhead, so I'm not convinced the default value is right here, but for now this is how it is. Honest-Profiler takes care of this for you, with JMC you'll need to add it to your command line.
2. Each sample is for a **single, on CPU, thread:** This is very different from the GetStackTraces approach which sampled all threads. It means you are getting less traces per sample, and are completely blind to sleeping/starved threads. Because the overhead is that much lower you can compensate by sampling more frequently, or over longer periods of time. **This is a good thing**, sampling all the threads at every sample is a very problematic proposition given the number of threads can be very large.

`AsyncGetCallTrace` is great for profiling most 'normal' Java code, where hotspots are in your Java code, or rather in the assembly code they result in. This seems to hold in the face of most optimizations to reasonable accuracy (but may on occasion be off by quite a bit...).

`AsyncGetCallTrace` is of limited use when:

1. Large numbers of samples fail: This can mean the application is spending it's time in GC/Deopts/Runtime code. Watch for failures. I think currently honest-profiler offers better visibility on this, but I'm sure the good folks of JMC can take a hint.
2. Performance issue is hard to glean from the Java code. E.g. see the [issue discussed in a previous post using JMH perfasm](http://psy-lob-saw.blogspot.com/2015/07/jmh-perfasm.html) (false sharing on the class id in an object header making the conditional inlining of an interface call very expensive). 
3. Due to instruction skid/compilation/available debug information the wrong Java line is blamed. This is potentially very confusing in the presence of inlining and code motion.

Using Honest-Profiler you can now profile Open/Oracle JDK6/7/8 applications on Linux and OS X. You can also use it to profile Zing applications on recent Zing builds (version 15.05.0.0 and later, all JDKs). Honest-Profiler is lovely, but I would caution readers that it is not in wide use, may contain bugs, and should be used with care. It's a useful tool, but I'm not sure I'd unleash it on my production systems just yet ;-).
JMC/JFR is available on Oracle JVMs only from JDK7u40, but works on Linux, OS X, Windows and Solaris (JFR only). JMC/JFR is free for development purposes, but requires a licence to use in production. Note that JFR collects a wealth of performance data which is beyond the scope of this post, I whole heartedly recommend you give it a go.

A big thank you to all the kind reviewers: [JP Bempel](https://twitter.com/jpbempel), [Doug Lawrie](https://twitter.com/switchology), [Marcus Hirt](https://twitter.com/hirt) and [Richard Warburton](https://twitter.com/RichardWarburto), any remaining errors will be deducted from their bonuses directly.

----

# 为啥大多数Java采样分析器不好用

本文建立在[上一篇关于安全点的文章](http://psy-lob-saw.blogspot.com/2015/12/safepoints.html)的基础上。如果你没读过，可能会感到迷茫和困惑。如果你读过它，还仍感到迷茫和困惑，并且确定这种感觉与手头的事情有关（==而不是[存在危机](https://en.wikipedia.org/wiki/Existential_crisis)==），那就继续往下看吧。 既然我们已经知道啥是安全点，那么：

1. 安全点轮询分散在相当任意的点上（<u>取决于执行模式，主要是在未计数的循环后缘或方法返回/入口</u>）。
2. 将JVM带到全局安全点成本很高。

我们掌握了所有需要的信息，可以得出结论：**通过在安全点进行采样进行性能分析可能会有些不足**。 对于某些人来说，这并不奇怪，但是这个问题在最常用的分析器中是存在的。根据RebelLabs的[调查](http://pages.zeroturnaround.com/RebelLabs---All-Report-Landers_Developer-Productivity-Report-2015.html?utm_source=performance-survey-report&utm_medium=allreports&utm_campaign=rebellabs&utm_rebellabsid=97)，最常用的分析器是这样细分的：

[![img](https://1.bp.blogspot.com/-NTpQ0vTOg9o/VsytuZ7EIvI/AAAAAAAACfU/7ITCc3qLuoA/s1600/which-profiler.png)](https://1.bp.blogspot.com/-NTpQ0vTOg9o/VsytuZ7EIvI/AAAAAAAACfU/7ITCc3qLuoA/s1600/which-profiler.png)

VisualVM、NetBeans Profiler（同样的东西）、YourKit 和 JProfiler都提供了一个在**安全点**处**采样 CPU 的 Profiler**。鉴于这是一个相当普遍的问题，需要深入探讨一下。

## 采样分析器（理论上）如何工作

**采样分析器**应该通过<u>在应用程序不同执行时间点上的采样</u>，来近似估算应用程序中“执行时间”的分布。每次采样的数据可以是：

- 当前指令

- 当前代码行

- 当前方法

- 当前堆栈跟踪

每次采样时，可以收集单个线程或所有线程样本数据。那么如何采样，Profiler 才能正确工作？

> “然而，要使采样结果与完整（未采样）的概况相媲美，必须满足以下两个条件。**首先，我们必须拥有大量样本才能获得具有统计意义的结果。**例如，如果分析器在整个程序运行中只收集一个样本，分析器会将 100% 的程序执行时间分配给采样所在的代码，将 0％ 分配给其他所有代码。[...]
>
> **其次，分析器应以相同的概率对程序运行中的所有点进行采样。**否则采样结果将带有偏差。 例如，假设分析器只能对包含 `call` 的方法进行采样，那就不会将任何执行时间归因于不包含 `call` 的方法，即使它们可能占程序的大部分执行时间。” 来自于[评估Java探查器的准确性（Evaluating the Accuracy of Java Profiler）](http://plv.colorado.edu/papers/mytkowicz-pldi10.pdf)，我们将稍后再用到该文

听起来很简单，对吧？

一旦有了大量样本，就可以构造一个热门方法列表，如果样本包含其代码行的信息，甚至可以指出方法中那些代码是热点；还可以查看调用树上的样本分布（如果收集了调用堆栈信息），很爽吧！

## 商业的Java采样分析器一般如何工作？

好吧，我可以逆向不同的解决方案，或者阅读开源代码，但是我会提供一些自己的推测，如果您知道得更多，可以随时打电话给我。分析器一般都依赖于所有 **JVM** 都必须满足的 **JVMTI** 规范：

- JVMTI 只提供了在**安全点**收集<u>采样点堆栈信息</u>的选项（对于调用线程，`GetStackTrace`不需要安全点，但这对分析器不是很有用。==在 Zing 上，在一个线程调用另一个线程的`GetStackTrace`，只会将这个线程带到安全点==）。因此，希望其工具在所 有JVM 上工作的供应商，只能在安全点进行采样。
- 无论是对单个线程还是所有线程进行采样，都会达到全局安全点（至少 OpenJDK 是这样，Zing 略有不同，但作为分析器供应商，能在 OpenJDK 上工作是必选项）。所有我调查过的分析器都要采样**全体线程**。据我所知，它们也不限制堆栈收集的深度。这相当于这样的 JVMTI 调用：`JvmtiEnv：：GetAllStackTraces(0，&stack_info，&thread_count)`
- 所以这就意味着：设置一个定时器线程，按“采样频率”触发，并收集所有线程的堆栈。

**==这有问题，原因有几个，其中一些可以避免==**：

1. 采样分析器需要样本，因此通常设置很高的采样频率（通常为每秒10次，即每 100 毫秒 1 次）。设置`-XX:+ PrintGCApplicationStoppedTime`并查看它会引入何种暂停时间很有帮助。出现几毫秒的暂停并不罕见，不过每个人的结果可能不同（取决于线程数、堆栈深度、在安全点的时间等）。每100毫秒暂停5毫秒，意味着分析器会带来5%的开销（实际伤害可能会更糟）。通常可以通过设置<u>更长的时间间隔</u>来控制开销，但这也意味着需要更长的分析时间才能获得有意义的样本数。

   > 1. `PrintGCApplicationStoppedTime` shows how much time the application was stopped at safepoint. Most often safepoints are caused by stop-the-world phases of garbage collection, however, [many other reasons exist](https://stackoverflow.com/a/29673564/3448419).
   > 2. `PrintGCApplicationConcurrentTime` is how much time the application worked without stopping, i.e. the time between two successive safepoints.

2. 从所有线程中收集全部堆栈信息，这意味着您的安全点操作成本是开放的。应用程序拥有的线程越多（考虑应用服务器、[SEDA架构](https://www.zybuluo.com/boothsun/note/887056)、大量线程池……），堆栈越深（比如Spring），获取应用程序单个线程的名称和<u>填写表单</u>的时间就越长。在上一篇文章中已经清楚地证明了这一点。 据我所知，当前的分析对此毫无办法。 如果构建自己的分析器，限制这两个数量似乎很明智，这样就可以限制开销。 **JVMTI** 提供查询当前线程列表的功能，如果少于100，则对所有线程进行采样，否则随机选择100个线程进行采样。也许偏向于采样<u>实际上正在做事</u>的线程，而不是采样<u>所有时间都在阻塞</u>的线程更有意义。

3. 在安全点进行采样毫无意义才是最糟糕的。

第 1 点和第 2 点与性能分析开销有关，基本上与成本有关。在上一篇关于安全点的文章中，我研究了这些成本，因此没必要重复。对于好的分析信息，这些成本可以接受，但正如我们将看到的，信息并不是那么好。

需要解释第 3 点，所以我们开始对意义的追寻。

## 安全点采样：理论

那么，在安全点采样意味着什么？这意味着只能在运行代码的安全点采样。由于C1/C2（客户端/服务器编译器）可能会编译热代码，已经减少了采样机会，只有在方法退出和 [Uncounted loop 回跳的](https://juejin.im/post/5d1b1fc46fb9a07ef7108d82#heading-8)时候进行采样。<u>这导致了这样的现象，即</u>，分析器应以相等的概率对程序运行中的所有点进行采样。

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

如上所述，对于应用程序中哪里是热点代码，安全点采样分析器可能没有准确的概念。这使得对“正在运行”线程的<u>派生观察</u>非常令人怀疑，但至少是这些线程的正确观察。这并不意味着它们是完全无用的，有时我们需要的只是一个正确方向的提示，以便进行一些好的分析，但这里可能会浪费大量时间。虽然对在[解释器中运行的代码](./什么是即时编译.md#分层编译的五个层次)进行采样，不会受到安全点偏差的影响，但这并不是很有用，因为热点代码很快就会被编译。如果热点代码正在解释器中运行，其实你有比安全点偏差更重要的事要做...

阻塞线程的堆栈跟踪是准确的，因此“等待”分析对于发现阻塞代码非常有用。如果阻塞方法是性能问题的根源，那么这将是一个方便的观察。

还有更好的选择！我将在以下文章中介绍其中一些：

- Java Misson Control
- Solaris Studio
- [Honest-Profiler](https://github.com/jvm-profiling-tools/honest-profiler)
- Perf + perf-map-agent（或[perfasm](http://psy-lob-saw.blogspot.com/2015/07/jmh-perfasm.html)，如果在JMH测试你的代码）

没有哪种工具是完美的，但是上述这些工具都可以更好地识别CPU时间。

# AsyncGetCallTrace分析器的优缺点

那么，从我[絮絮叨叨地抱怨安全点偏差的帖子](#为啥大多数Java采样分析器不好用)继续， 应该从那里获取分析结果呢？使用OpenJDK内部API `AsyncGetCallTrace`是一个选择，可<u>在非安全点</u>方便地收集堆栈跟踪信息。

`AsyncGetCallTrace`不是官方JVM API。对Profiler编写者而言有点烦，它最初只在OpenJDK / Oracle JVM上实现（Zing最近开始支持**AGCT**，以支持Solaris Studio和其他Profiler，我将为Solaris Studio上撰写单独的文章）。 它最初用于Solaris Studio，并提供以下API（参见[forte.cpp](http://hg.openjdk.java.net/jdk8/jdk8/hotspot/file/tip/src/share/vm/prims/forte.cpp#l457)，这个名称是Forte Analyzer时代遗留下来的）。 以下是所有的API：

```cpp
typedef struct {
  jint lineno;         // BCI in the source file
  jmethodID method_id; // method executed in this frame
} ASGCT_CallFrame;

typedef struct {
  JNIEnv *env_id   //Env where trace was recorded
  jint num_frames; // number of frames in this trace
  ASGCT_CallFrame *frames;
} ASGCT_CallTrace; 

void AsyncGetCallTrace(ASGCT_CallTrace *trace, // pre-allocated trace to fill
                       jint depth,     // max number of frames to walk up the stack
                       void* ucontext)         // signal context
```

简单吧？ 你传递一个`ucontext`，它用<u>调用帧</u>填充`trace`参数（**or it gets the hose again**）。

## 轻量级的 Honest Profiler

名称中的“异步”是指可以在**Signal Handler**（<u>信号处理程序</u>）中安全调用AGCT。 这在 <u>profiling</u> 时非常方便，因为这意味着可以按以下方式实现**Profiler**：

1. 在JVMTI的代理中，为某个信号X注册一个信号处理程序。

2. [设置一个定时器](http://man7.org/linux/man-pages/man2/setitimer.2.html)，以所需的采样频率触发信号X。Honest Profiler使用 **ITIMER_PROF** 选项，这意味着我们将根据CPU时间获得信号。信号将被发送到进程，其中一个正在运行的线程将被中断，并调用注册的信号处理程序。请注意，这假设操作系统将在线程之间公平地分配信号，所以在所有正在运行线程之间合平地采样。

   > 注意：在多线程环境下，信号会随机地分发到该进程中某个正在运行地线程。

3. 从信号处理程序中调用**AGCT**：请注意，被中断的线程（从当前在CPU上执行的线程中随机选取）将运行注册的信号处理程序。线程不在安全点。 它可能根本不是Java线程。

4. 持久化<u>调用追踪</u>：请注意，在信号处理程序中运行时，只有“异步”代码才合法。例如，这意味着禁止任何阻塞代码，包括malloc和IO。

5. WIN!

`ucontext`参数就是信号处理程序传给你的（信号处理程序是一个回调，函数定义是`handle(int signum，siginfo_t * info，void  * context)`）。AGCT将在此挖掘出中断时的**指令/帧/堆栈指针值**，并尽最大努力找出您所处的位置。

**Jeremy Manson** 就是采用此种方法，他解释了基础架构，（以一种概念验证、非确定的方式）[开源了基础的Profiler](https://code.google.com/archive/p/lightweight-java-profiler/)，他在此问题上有一系列精彩文章：

- [Profiling with JVMTI/JVMPI, SIGPROF and AsyncGetCallTrace](http://jeremymanson.blogspot.co.za/2007/05/profiling-with-jvmtijvmpi-sigprof-and.html)
- [More about profiling with SIGPROF](http://jeremymanson.blogspot.co.za/2007/06/more-about-profiling-with-sigprof.html)
- [More thoughts on SIGPROF, JVMTI and stack traces](http://jeremymanson.blogspot.co.za/2007/06/more-thoughts-on-sigprof-jvmti-and.html)
- [Why Many Profilers have Serious Problems (More on Profiling with Signals)](http://jeremymanson.blogspot.co.za/2010/07/why-many-profilers-have-serious.html)

然后，**Richard Warburton** 基于相同的代码，进一步改进和稳定了 [Honest-Profiler](https://github.com/RichardWarburton/honest-profiler)（我对此做出了一些贡献）。Honest Profiler致力于成为一个最初的产品原型，并辅以一些使整个产品可用的工具。通过预先分配`ASGCT_CallTrace`，辅以无锁的 **MPSC** 环形缓冲区（即预先分配<u>调用帧数组</u>），解决了信号处理程序中的序列化问题。然后，在后续的线程中读取<u>调用追踪信息</u>，并收集一些额外的信息（如将**BCI**转换为行号，将**jmethodIds**转换为类名/文件名等）写入日志文件。更多详细信息请参见项目Wiki。

> 1. MPSC：multi-producer, single consumer
> 2. BCI：Byte Code Instrument

离线分析日志文件，可获取细分的热点方法和相关调用树（即在另一个时间另一台机器上，由另一个进程来处理。请注意，也可在Profile JVM时处理该文件，无需等待它终止）。本文中，我将使用Honest-Profiler，如果你想尝试，则需要自己[构建](https://github.com/RichardWarburton/honest-profiler/wiki/How-to-build)（在 [OpenJDK](http://openjdk.java.net/)/[Zulu](https://www.azul.com/products/zulu/) 6/7/8 + [Oracle JVM](http://www.oracle.com/technetwork/java/javase/downloads/index.html)s + 最近的 [Zing](https://www.azul.com/products/zing/) 版本上好使）。我将用 JMC 来对比相同的实验，需要在Oracle JVM 1.7u40及更高版本上尝试。 

## AGCT做啥？

通过API，我们可以说**AGCT**是指令/帧/堆栈指针和**call trace**之间的映射。**call trace**是**Java调用帧**数组（jmethodId，BCI）。要生成此映射，执行以下过程：

1. 确保线程处于“**可遍历**”状态，尤其不要处于以下状态：
   - 不是Java线程。
   - 正在GC
   - 处于新建/未初始化/即将结束状态。即对运行Java代码之前或之后的线程不感兴趣
   - 正在[逆向优化](./什么是即时编译.md#动态逆优化)

2. 查找当前/最后一个Java帧（与堆栈上的实际帧一样，请重新访问操作系统101以了解堆[栈和帧](https://www.quora.com/What-is-the-difference-between-a-stack-pointer-and-a-frame-pointer)的定义）：
   - 指令指针（通常称为PC程序计数器）用于查找匹配（编译的/解释的）Java方法。当前PC由信号上下文提供（即`ucontext`）。
   - 如果PC不在Java方法中，我们需要找到最后一个调用本机代码的Java方法。
   - 失败是一种选择！由于种种原因，我们可能处于“无法遍历”的境地... 。这非常复杂，如果你一定要知道，我劝你深吸一口气，然后跳进相关代码的迷宫里。尝试确定**顶部帧**是**AGCT**最复杂的地方。

3. 一旦有了**顶部帧**，我们就可以填充**call trace**结构。为此，我们必须将真实帧和PC转换为：
   - **编译后的调用帧**：如果PC定位在<u>编译后的方法</u>，找到BCI（字节码索引）并记录它和对应的jMethodId
   - **虚拟调用帧**：PC定位的指令在编译的内联方法中，记录方法/BCI一直记录到编译后的调用帧上
   - **解释的调用帧**
   - 我们需要从编译/解释的方法开始遍历调用帧，重复此操作，直到到达Java调用跟踪的根（或记录足够的调用帧，以先到者为准）

4. WIN!

与潜在副作用的药物列表非常相似，函数支持的错误代码列表可以很好地说明问题。**AGCT**无法返回**call trace**的原因如下：

```cpp
enum {
  ticks_no_Java_frame         =  0, // new thread
  ticks_no_class_load         = -1, // jmethodIds are not available
  ticks_GC_active             = -2, // GC action
  ticks_unknown_not_Java      = -3, // ¯\_(ツ)_/¯
  ticks_not_walkable_not_Java = -4, // ¯\_(ツ)_/¯
  ticks_unknown_Java          = -5, // ¯\_(ツ)_/¯
  ticks_not_walkable_Java     = -6, // ¯\_(ツ)_/¯
  ticks_unknown_state         = -7, // ¯\_(ツ)_/¯
  ticks_thread_exit           = -8, // dying thread
  ticks_deopt                 = -9, // mid-deopting code
  ticks_safepoint             = -10 // ¯\_(ツ)_/¯
}; 
```

虽然由AGCT报告这些数据，但基于这些数据的报告往往缺少这些数据。稍后再谈。

## 好的方面！

所以，从好的方面来看，我们现在可以在两次安全点轮询之间采样了！！！ 那有多棒？通过运行基准测试，让我们确切地看到它多么棒，这些基准测试是我们在上一篇文章中使用安全点偏差分析器无法正确测量的。

**注**：Honest-Profiler 的报告（**t X**，**s Y**）反映了**t**：包含此代码行的堆栈样本的总百分比，与**s**：以该代码行自身作为顶层帧时，样本数占总样本数的百分比。 <u>输出按自身排序</u>：

```java
public class SafepointProfiling {
  @Param("1000")
  int size;
  byte[] buffer;
  byte[] dst;
  boolean result;

  @Setup
  public final void setup() {
    buffer = new byte[size];
    dst = new byte[size];
  }
  
  @Benchmark
  public void meSoHotInline() {
    byte b = 0;
    for (int i = 0; i < size; i++) {
      b += buffer[i];
    }
    result = b == 1;
  }
/*  
# Benchmark: safepoint.profiling.SafepointProfiling.meSoHotInline
JMH Stack profiler:
 99.6%  99.8% meSoHotInline_avgt_jmhStub:165

Honest Profiler:
 (t 98.7,s 98.7) meSoHotInline @ (bci=12,line=18)
 (t  0.8,s  0.8) meSoHotInline_avgt_jmhStub @ (bci=27,line=165)
*/
  @Benchmark
  @CompilerControl(CompilerControl.Mode.DONT_INLINE)
  public void meSoHotNoInline() {
    byte b = 0;
    for (int i = 0; i < size; i++) {
      b += buffer[i]; 
    }
    result = b == 1;
  }
/*
# Benchmark: safepoint.profiling.SafepointProfiling.meSoHotNoInline
JMH Stack profiler:
 99.4%  99.6% meSoHotNoInline_avgt_jmhStub:163

Honest Profiler:
 (t 98.9,s 98.9) meSoHotNoInline @ (bci=12,line=36)
 (t  0.4,s  0.4) AGCT::Unknown not Java[ERR=-3] @ (bci=-1,line=-100)
 (t  0.2,s  0.2) AGCT::Unknown Java[ERR=-5] @ (bci=-1,line=-100)
 (t  0.1,s  0.1) meSoHotNoInline_avgt_jmhStub @ (bci=27,line=165)
 (t 99.0,s  0.1) meSoHotNoInline_avgt_jmhStub @ (bci=14,line=163)
}*/
```

太好了！ Honest Profiler 正确定位了热点方法，标记的代码行就是工作发生的地方。

让我们试试复制基准测试，这一次比较 Honest-Profiler 和 JMC：

```java
 @Benchmark
  public void copy() {
    byte b = 0;
    for (int i = 0; i < buffer.length; i++) {
      dst[i] = buffer[i];
    }
    result = dst[buffer.length-1] == 1;
  }
/*
# Honest-Profiler reports:
 (t 41.6,s 41.6) copy @ (bci=22,line=5)
 (t 33.3,s 33.3) copy @ (bci=23,line=4)
 (t  9.7,s  9.7) copy @ (bci=21,line=4)
 (t  8.5,s  8.5) copy @ (bci=8 ,line=4)
 (t  2.3,s  2.3) copy @ (bci=11,line=5)
# JMC reports:
Stack Trace                         |Samples| Percentage(%)
copy              line: 7   bci: 41 | 3,819 | 96.88
copy_avgt_jmhStub line: 165 bci: 27 |   122 |  3.095

# JMC reports with -XX:+UnlockDiagnosticVMOptions -XX:+DebugNonSafepoints:
Stack Trace                         |Samples| Percentage(%)
copy              line: 5   bci: 22 | 1,662 | 42.204
copy              line: 4   bci: 23 | 1,341 | 34.053
copy              line: 5   bci: 21 |   381 |  9.675
copy              line: 4   bci:  8 |   324 |  8.228
copy              line: 5   bci: 11 |    98 |  2.489
*/
```

注意`-XX:+UnlockDiagnosticVMOptions -XX:+DebugNonSafepoints`标志对JMC的影响。Honest Profiler（[最近的PR](https://github.com/RichardWarburton/honest-profiler/pull/133)，刚刚出来）无需用户干预就启用该标记，但JMC当前没这样做（attach进程时可以理解，但如果进程启动时开始Profile，则应该默认打开）。

:面向年轻人和狂热者进一步的实验和验证：

- 在更大的应用上，比较 Honest Profiler 和 JFR/JMC 的结果。有何不同？为什么你觉得不同？
- 使用`-XX:+ PrintGCApplicationStoppedTime`运行，查看有没有引入额外的安全点（分别在**AGCT**启用和禁用的情况下测试）。
- 特别好学的人可以分析一下前一篇文章中讨论过的其他案例，看看它们是否同样得到了解决。

虽然这是一个进步，但我们仍然有一些问题... 

## 收集错误：运行时存根（[Stub](https://www.zhihu.com/question/24844900)）

从错误列表中可以看出，任何数量的事件都可能导致采样失败。一种特别讨厌的失败类型是，**AGCT**无法搞定运行时生成的函数。让我们看看使用系统的数组复制函数时会发生什么:

```java
@Benchmark
  public int systemArrayCopy() {
    System.arraycopy(buffer, 0, dst, 0, buffer.length);
    return dst[buffer.length-1];
  }
/*
# Flat Profile (by line):
 (t 62.9,s 62.9) AGCT::Unknown Java[ERR=-5] @ (bci=-1,line=-100)
 (t 19.5,s 19.5) systemArrayCopy_avgt_jmhStub @ (bci=29,line=165)
 (t 2.7,s 2.7) systemArrayCopy @ (bci=15,line=3)
 (t 2.1,s 2.1) systemArrayCopy @ (bci=26,line=4)
 (t 1.9,s 1.9) systemArrayCopy @ (bci=29,line=4)
 */
```

现在，这个重要的未知标记是Honest-Profiler的最新成员，更加坦诚地面对失败的样本。这表明，在62.9%的样本中，AGCT无法确定发生了什么，并返回了`ticks_unknown_java`。考虑到这里测试的代码很少，我们可以推断丢失的样本都属于`System.arraycopy`（您可以选择更大的数组来进一步证明这一点，或者使用**native Profiler**进行比较）。

用JMC分析相同的基准测试没有出现失败的样本，<u>并且分析器将划分剩余的样本，就好像失败的样本从未发生过一样</u>。以下是`System.arraycopy`的JMC分析结果：

```bash
Number of samples(over 60 seconds) : 2617
Method::Line                              Samples   %
systemArrayCopy_avgt_jmhStub(...) :: 165    1,729   66.043
systemArrayCopy()                 ::  41      331   12.643
systemArrayCopy()                 ::  42      208    7.945
systemArrayCopy_avgt_jmhStub(...) :: 166       93    3.552
systemArrayCopy_avgt_jmhStub(...) :: 163       88    3.361
```

JMC报告的样本数量很少（<u>在**树状视图**中总的数量与**根**中的样本数量相当</u>），但是如果不知道预期的样本数，很容易漏掉。对于实际应用长时间收集到的含有噪声的大量样本，尤其如此。

是否只有`System.arraycopy`这个现象？完全不是，下面进一步比较 `crc32` 和 `adler32`：

```java
@Benchmark
  public long adler32(){
    adler.update(buffer);
    return adler.getValue();
  }
/*
# Benchmark (size)  Mode  Cnt     Score   Error  Units
  adler32     1000  avgt   50  1003.068 ± 4.994  ns/op

# Flat Profile (by line):
  (t 98.9,s 98.9) java.util.zip.Adler32::updateBytes @ (bci=-3,line=-100)
  (t  0.3,s  0.3) AGCT::Unknown not Java[ERR=-3] @ (bci=-1,line=-100)
  (t  0.1,s  0.1) AGCT::Unknown Java[ERR=-5] @ (bci=-1,line=-100)
  (t  0.1,s  0.1) adler32_avgt_jmhStub @ (bci=32,line=165)
  (t  0.1,s  0.1) adler32 @ (bci=5,line=3)
*/	
  @Benchmark
  public long crc32(){
    crc.update(buffer);
    return crc.getValue();
  }
/*
# Benchmark (size)  Mode  Cnt    Score   Error  Units
  crc32       1000  avgt   50  134.341 ± 0.698  ns/op

# Flat Profile (by line):
 (t 96.7,s 96.7) AGCT::Unknown Java[ERR=-5] @ (bci=-1,line=-100)
 (t  1.4,s  0.6) crc32_avgt_jmhStub @ (bci=19,line=163)
 (t  0.3,s  0.3) AGCT::Unknown not Java[ERR=-3] @ (bci=-1,line=-100)
 (t  0.3,s  0.3) crc32_avgt_jmhStub @ (bci=29,line=165)
 */
```

`Crc32`和`System.arraycopy`有什么共同点？它们都是JVM**内置函数**，用内联代码和对JVM运行时生成的方法的调用，一起替换了原始的方法调用（是Java还是 **native 方法**并不重要，尽管在这种情况下都是**native 方法**）。此方法的调用保护方式与对 **native 方法**的常规调用不同，因此AGCT无法遍历堆栈。

为什么这很重要？ 这些方法之所以值得用内置函数的原因是，它们是常见应用程序中，足够重要的瓶颈。 从这个意义上说，内置函数就像过去性能问题的墓碑，尽管性能比以前更快，但成本却不可能完全消失。

为啥选**CRC32**？ 因为我最近花了一些时间分析**Cassandra**。2.0使用`adler32`进行校验和，3.0使用`crc32`。 从上面的结果可以看出，这可能是一个不错的选择，但是如果要分析Cassandra 3，它看起来会比实际情况要好，因为无法分析校验和的样本。使用native profiler将确认校验和的开销仍然不小（运行特定设置/基准测试的结果）。

**AGCT Profiler 对运行时存根视而不见（将来的版本中可能会部分或全部解决此问题……）。 失败的样本表明存在这种盲点。**

留给读者的练习：

- **构建一个具有大量GC活动的基准测试用于分析**。JMC 并没有显示CPU时间花在GC上，Honest-Profiler 应显示为`ticks_GC_active` 

- **构建一个具有大量编译活动的基准测试用于分析**。和上面一样，CPU时间没有花在编译上。不存在和编译相关的错误码，但是你应该看到许多 `ticks_unknown_not_Java`，这表明非Java线程已被中断（[这是一个混合的错误码，我们将尽快修复](https://github.com/jvm-profiling-tools/honest-profiler/issues/138)）。

  > 到今天（2019-11-17）仍未修复，尽快....

- 加分！构建花费大量时间进行<u>逆优化</u>的基准程序，在 Honest-Profiler 的分析结果中查找`ticks_deopt`错误码。

## 盲点：休眠的代码

由于休眠代码实际上并不消耗CPU，因此无法 profile 它们。如果您习惯于JVisualVM，它报告所有线程的堆栈，包括等待（或有时运行`Unsafe.park`）的线程，这可能会让您困惑。这些Profiler无法分析正在休眠或阻塞的代码。我提到这一点，是因为这是一个常见的陷阱，而非一个他们承诺要实现这个功能，但不知何故还未实现。对于Honest-Profiler，此[功能](https://github.com/RichardWarburton/honest-profiler/issues/126)应该有助于比较预期样本和实际样本，来突出显示没有利用CPU的应用（delta是由于进程正在休眠，而未传递到该进程的信号）。

## 误差率：滑动+内联

我还会再次深入探讨这个问题，因为其他Profiler也有同样的问题。本质问题是指令分析不准确，在对程序计数器（PC）进行采样时，我们经常会遇到<u>滑动效应</u>。这实际上意味着所报告的指令早真正耗时的指令后面，可能隔着好多条指令。因为`AsyncGetCallTrace`依赖于**PC采样**来解析方法和代码行，通过映射（PC->BCI->LOC）得到同样的不精确性，但在汇编层面上，处理的是一组指令，接近性是有意义的，转换回Java时，我们可能已经从一个内联方法滑向另一个内联方法，而<u>罪魁祸首</u>（热点代码）已经不在附近的代码行中了。

未完待续...

## 小结：有什么好处？

AsyncGetCallTrace比GetStackTraces有提高，因为它以较低的开销运行且不受安全点偏差的影响。但确实要调整一下思维模式（[mental model](https://www.zhihu.com/question/19940741)）：

1. `-XX:+UnlockDiagnosticVMOptions` `-XX:+DebugNonSafepoints`：如果未启用，则调试数据的分辨率（解析）仍然困扰着您。这与安全点偏差不同，因为您实际上可以在任何地方采样，但是从PC到BCI的转换会搞死你。还没看到这些标志会带来不可忽视的开销，所以不相信默认值是对的，但现在就是这样。Honest-Profiler会为处理这个问题，JMC则需要你将其添加到命令行中。
2. 每次采样**一个CPU线程**：这与对所有线程进行采样的`GetStackTraces`方法非常不同。这意味着每个样本获得的**trace**信息更少，并且完全不会采样睡眠/饥饿的线程。因为开销要低得多，所以可以通过更频繁或更长的时间进行采样来进行补偿。**这是一件好事**，鉴于线程数量可能很多，所以每次采样所有线程是一个非常有问题的任务。

`AsyncGetCallTrace`非常适合分析大多数“普通” Java代码，其中热点在Java代码中，或者在它们导致的汇编代码中。似乎在大多数优化面前保持了合理的准确性（但有时可能会有相当大的偏差…）。

`AsyncGetCallTrace`在以下情况下使用受到限制：

1. 采样大量失败：这可能意味着应用程序将时间浪费在GC/Deopts/Runtime代码上。小心失败。 我认为Honest Profiler目前可以对此提供更好的可见性，但我也相信JMC的好伙伴可以帮忙。
2. 从Java代码很难发现性能问题。例如，请参阅[上一篇文章，讨论使用JMH perfasm时遇到的问题](http://psy-lob-saw.blogspot.com/2015/07/JMH-perfasm.html)（错误共享对象头中`class id`，使得接口调用的<u>条件内联</u>非常昂贵）。
3. 由于指令滑动/编译/可用的调试信息，导致归因到错误的Java代码行。在存在内联和代码移动的情况下，可能非常令人困惑。

现在，可使用Honest-Profiler在Linux和OS X上分析Open / Oracle JDK6 / 7/8应用程序。还可以使用它在最新的Zing版本（15.05.0.0及更高版本的所有JDK）上分析Zing应用程序。Honest-Profiler不错，但我要提醒读者，它还没有广泛使用，可能包含错误，应谨慎使用。这是一个有用的工具，但不确定是否会在我的生产系统上使用它😉。JMC / JFR仅在JDK7u40上的Oracle JVM上可用，但在Linux，OS X，Windows和Solaris（仅JFR）上可用。 JMC / JFR出于开发目的是免费的，但需要许可证才能在生产中使用。注意，JFR收集了大量超出本文范围的性能数据，我衷心建议您尝试一下。

非常感谢所有评论者：[JP Bempel](https://twitter.com/jpbempel), [Doug Lawrie](https://twitter.com/switchology), [Marcus Hirt](https://twitter.com/hirt) 和 [Richard Warburton](https://twitter.com/RichardWarburto), 任何剩余的错误将直接从他们的奖金中扣除。