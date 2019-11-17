[TOC]
# Writing Java Micro Benchmarks with JMH: Juicy

Demonstrating use of JMH and exploring how the framework can squeeze every last drop out of a simple benchmark.

Writing micro benchmarks for Java code has always been a rather tricky affair with many pitfalls to lookout for:

- JIT:
  - Pre/Post compilation behaviour: After 10K(default, tuneable via -XX:CompileThreshold) invocations your code with morph into compiled assembly making it hopefully faster, and certainly different from it's interpreted version.
  - Specialisation: The JIT will optimise away code that does nothing (i.e. has no side effects), will optimise for single interface/class implementations. A smaller code base, like a micro-benchmark, is a prime candidate.
  - Loop unrolling and OSR can make benchmark code (typically a loop of calls to the profiled method) perform different to how it would in real life.
-  GC effects:
  - Escape analysis may succeed in a benchmark where it would fail in real code.
  - A buildup to a GC might be ignored in a run or a collection may be included.
- Application/Threading warmup: during initialisation threading behaviour and resources allocation can lead to significantly different behaviour than steady state behaviour. 
- Environmental variance:
  - Hardware: CPU/memory/NIC etc...
  - OS
  - JVM: which one? running with which flags?
  - Other applications sharing resources

Here's a bunch of articles on Java micro-benchmarks which discuss the issue further:

- [Robust Java Benchmarking](http://www.ibm.com/developerworks/java/library/j-benchmark1/index.html)
- [The perils of benchmarking under dynamic compilation](http://www.ibm.com/developerworks/library/j-jtp12214/)
- [How to write a benchmark?(from StackOverflow)](http://stackoverflow.com/questions/504103/how-do-i-write-a-correct-micro-benchmark-in-java)
- Read the footnotes of the JMH samples for further highlights on the topic.

Some of these issues are hard to solve, but some are addressable via a framework and indeed many frameworks have been written to tackle the above.

## Let's talk about JMH

JMH (Java Micro-benchmarks Harness or Juicy Munchy Hummus, hard to say as they don't tell you on the site) is the latest and as it comes out of the workshop of the very people who work hard to make the OpenJDK JVM fly it promises to deliver more accuracy and better tooling then most.

The source/project is [here](http://openjdk.java.net/projects/code-tools/jmh/) and you will currently need to build it locally to have it in your maven repository, as per the instructions. Once you've done that you are good to go, and can set yourself up with a maven dependency on it.

[Here](https://github.com/nitsanw/jmh-samples) is the project I'll be using throughout this post, feel free to C&P to your hearts content. It's a copy of the JMH samples project with the JMH jar built and maven sorted (see update below on current state of samples) and all that so you can just clone and run without setting up JMH locally. **The original samples are pure gold in terms of highlighting the complexities of benchmarking, READ THEM!** The command line output is detailed and informative, so have a look to see what hides in the tool box.

I added my sample on top of the original samples, it is basic (very very basic) in it's use of the framework but the intention here is to help you get started, not drown you in detail, and give you a feel of how much you can get out of it for very little effort. Here goes...

## It's fun to have fun, but you've got to know how

For the sake of easy comparison and reference I'll use JMH to benchmark the same bit of code I benchmarked with a hand rolled framework [here](http://psy-lob-saw.blogspot.com/2012/12/encode-utf-8-string-to-bytebuffer-faster.html) and later on with Caliper [here](http://psy-lob-saw.blogspot.com/2013/01/using-caliper-for-writing-micro-benchmarks.html). We're benchmarking my novel way of encoding UTF-8 strings into ByteBuffers vs String.getBytes() vs best practice recommendation of using a CharsetEncoder. The benchmark compares the 3 methods by encoding a test set of UTF-8 strings samples.

Here's what the benchmark looks like when using JMH:

```java
...
@State(Scope.Thread)
public class Utf8EncodingBenchmark  {
	// experiment test input
	private List<String> strings = new ArrayList<String>();

	// CharsetEncoder helper buffers
	private char[] chars;
	private CharBuffer charBuffer;
	private CharsetEncoder encoder;
	
	// My own encoder
	private CustomUtf8Encoder customEncoder;
	
	// Destination buffer, the slayer
	private ByteBuffer buffySummers;
	
	@Setup
	public void init() {
		boolean useDirectBuffer = Boolean
		        .getBoolean("Utf8EncodingBenchmark.directBuffer");
		InputStream testTextStream = null;
		InputStreamReader inStreamReader = null;
		BufferedReader buffReader = null;
		try {
			testTextStream = getClass().getResourceAsStream("/Utf8Samples.txt");
			inStreamReader = new InputStreamReader(testTextStream, "UTF-8");
			buffReader = new BufferedReader(inStreamReader);
			String line;
			while ((line = buffReader.readLine()) != null) {
				strings.add(line);
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		} finally {
			closeStream(testTextStream);
			closeReader(inStreamReader);
			closeReader(buffReader);
		}
	
		if (useDirectBuffer) {
			buffySummers = ByteBuffer.allocateDirect(4096);
		} else {
			buffySummers = ByteBuffer.allocate(4096);
		}
		chars = new char[4096];
		charBuffer = CharBuffer.wrap(chars);
		encoder = Charset.forName("UTF-8").newEncoder();
		customEncoder = new CustomUtf8Encoder();
	}
	
	private void closeStream(InputStream inStream) {
		if (inStream != null) {
			try {
				inStream.close();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
	}
	
	private void closeReader(Reader buffReader) {
		if (buffReader != null) {
			try {
				buffReader.close();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
	}
	
	@GenerateMicroBenchmark
	public int customEncoder() {
		int countBytes = 0;
		for (int stringIndex = 0; stringIndex < strings.size(); stringIndex++) {
			customEncoder.encodeString(strings.get(stringIndex), buffySummers);
			countBytes += buffySummers.position();
			buffySummers.clear();
		}
		return countBytes;
	}
	
	@GenerateMicroBenchmark
	public int stringGetBytes() throws UnsupportedEncodingException {
		int countBytes = 0;
		for (int stringIndex = 0; stringIndex < strings.size(); stringIndex++) {
			buffySummers.put(strings.get(stringIndex).getBytes("UTF-8"));
			countBytes += buffySummers.position();
			buffySummers.clear();
		}
		return countBytes;
	}
	
	@GenerateMicroBenchmark
	public int charsetEncoder() throws UnsupportedEncodingException {
		int countBytes = 0;
		for (int stringIndex = 0; stringIndex < strings.size(); stringIndex++) {
			String source = strings.get(stringIndex);
			int length = source.length();
			source.getChars(0, length, chars, 0);
			charBuffer.position(0);
			charBuffer.limit(length);
			encoder.reset();
			encoder.encode(charBuffer, buffySummers, true);
			countBytes += buffySummers.position();
			buffySummers.clear();
		}
		return countBytes;
	}	
}
```

We're using three JMH annotations here:

1. **State** - This annotation tells JMH how to share benchmark object state. I'm using the Thread scope which means no sharing is desirable. There are 2 other scopes available Group (for sharing the state between a group of threads) and Benchmark (for sharing the state across all benchmark threads).
2. **Setup** - Much like the JUnit counterpart the Setup annotation tells JMH this method needs to be called before it starts hammering my methods. Setup methods are executed appropriately for your chosen State scope.
3. **GenerateMicroBenchmark** - Tells JMH to fry this method with onions.

## A lot of good tricks, I will show them to you, your mother will not mind at all if I do

To get our benchmarks going we need to run the generated microbenchmarks.jar. This is what we get:

```bash
$ java -DUtf8EncodingBenchmark.directBuffer=false -jar target/microbenchmarks.jar -wi 3 -i 3 ".*Utf8EncodingBenchmark.*"
# Measurement Section
# Runtime (per iteration): 5s
# Iterations: 3
# Thread counts (concurrent threads per iteration): [1, 1, 1]
# Threads will synchronize iterations
# Running: psy.lob.saw.utf8.generated.throughput.Utf8EncodingBenchmark.charsetEncoder
# Warmup Iteration   1 (3s in 1 thread): 2.517 ops/msec
# Warmup Iteration   2 (3s in 1 thread): 2.405 ops/msec
# Warmup Iteration   3 (3s in 1 thread): 2.598 ops/msec
Iteration   1 (5s in 1 thread): 2.579 ops/msec
Iteration   2 (5s in 1 thread): 2.618 ops/msec
Iteration   3 (5s in 1 thread): 2.595 ops/msec

Run result "charsetEncoder": 2.597 ±(95%) 0.048 ±(99%) 0.110 ops/msec
Run statistics "charsetEncoder": min = 2.579, avg = 2.597, max = 2.618, stdev = 0.019
Run confidence intervals "charsetEncoder": 95% [2.549, 2.645], 99% [2.487, 2.708]

... similarly continues for 2 more benchamrks ...

Benchmark                                          Thr    Cnt  Sec         Mean   Mean error          Var    Units
p.l.s.u.g.t.Utf8EncodingBenchmark.charsetEncoder     1      3    5        2.487        0.848        0.022 ops/msec
p.l.s.u.g.t.Utf8EncodingBenchmark.customEncoder      1      3    5        4.592        0.087        0.000 ops/msec
p.l.s.u.g.t.Utf8EncodingBenchmark.stringGetBytes     1      3    5        2.298        0.607        0.011 ops/msec
```

Nice innit?

Here's the extra knobs we get on our experiment for our effort:

- I'm using some command line options to control the number of iterations/warmup iterations, here's the available knobs on that topic:

  - **i** - number of benchmarked iterations, use 10 or more to get a good idea
  - **r** - how long to run each benchmark iteration
  - **wi** - number of warmup iterations
  - **w** - how long to run each warmup iteration (give ample room for warmup, how much will depend on the code you try and measure, try and have it execute 100K times or so)

- To choose which benchmarks we want to run we need to supply a regular expression to filter them or ".*" to run all of them. If you can't remember what you packed use:

  - **v** - verbose run will also print out the list of available benchmarks and which ones were selected by your expression
  - **l** - to list the available benchmarks

- If you wish to isolate GC effects between iterations you can use the **gc** option, this is often desirable to help getting more uniform results.

- Benchmarks are forked into separate VMs by default. If you wish to run them together add "**-f 0**" (you shouldn't really do this unless you are trying to debug something... forking is good). The framework also allows you to run several forks for each benchmark to help identify run to run variance. 

The output is given for every iteration, then a summary of the stats. As I'm running 3 iterations these are not very informative (**this is not recommended practice and was done for the sake of getting sample outputs rather than accurate measurement, I recommend you run more than 10 iterations and compare several JMH runs for good measure**) but if I was to run 50 iterations they'd give me more valuable data. We can choose from a variety of several output formats to generate graphs/reports later. To get CSV format output add "-of csv" to your command line, which leaves you to draw your own conclusions from the data (no summary stats here):

The above has your basic requirements from a benchmark framework covered:

1. Make it easy to write benchmarks
2. Integrate with my build tool
3. Make it easy to run benchmarks (there's IDE integration on the cards to make it even easier)
4. Give me output in a format I can work with

I'm particularly happy with the runnable jar as a means to packaging the benchmarks as I can now take the same jar and try it out on different environments which is important to my work process. My only grumble is the lack of support for parametrization which leads me to use a system property to switch between the direct and heap buffer output tests. I'm assured this is also in the cards.

## I will show you another good game that I know
There's even more! Whenever I run any type of experiment the first question is how to explain the results and what differences one implementation has over the other. For small bits of code the answer will usually be 'read the code you lazy bugger' but when comparing 3rd party libraries or when putting large  compound bits of functionality to the test profiling is often the answer, which is why JMH comes with a set of profilers:

1. gc: GC profiling via standard MBeans
2. comp: JIT compiler profiling via standard MBeans
3. cl: Classloader profiling via standard MBeans
4. hs_rt: HotSpot (tm) runtime profiling via implementation-specific MBeans
5. hs_cl: HotSpot (tm) classloader profiling via implementation-specific MBeans
6. hs_comp: HotSpot (tm) JIT compiler profiling via implementation-specific MBeans
7. hs_gc: HotSpot (tm) memory manager (GC) profiling via implementation-specific MBeans
8. hs_thr: HotSpot (tm) threading subsystem via implementation-specific MBeans
9. stack: Simple and naive Java stack profiler

Covering the lot exceeds the scope of this blog post, let's focus on obvious ones that might prove helpful for this experiment. Running with the gc and hs_gc profiler (note: this should be done with fixed heap for best result, just demonstrating output here) give this output:

``` bash
# Running: psy.lob.saw.utf8.generated.throughput.Utf8EncodingBenchmark.charsetEncoder
...
Iteration   1 (1s in 1 thread): 2.219 ops/msec
      HS(GC) | difference: {sun.gc.generation.2.space.0.used=3784}
             |
...
# Running: psy.lob.saw.utf8.generated.throughput.Utf8EncodingBenchmark.customEncoder
...
Iteration   1 (1s in 1 thread): 4.770 ops/msec
      HS(GC) | difference: {sun.gc.generation.2.space.0.used=3784}
             |
# Running: psy.lob.saw.utf8.generated.throughput.Utf8EncodingBenchmark.stringGetBytes
...
Iteration   1 (1s in 1 thread): 2.237 ops/msec
          GC | wall time = 0.999 secs,  GC time = 0.001 secs, GC% = 0.10%, GC count = +1
             |
      HS(GC) | difference: {sun.gc.collector.0.invocations=1, sun.gc.collector.0.lastEntryTime=1107305, sun.gc.collector.0.lastExitTime=1107008, sun.gc.collector.0.time=1025, sun.gc.generation.0.capacity=116129792, sun.gc.generation.0.space.0.used=14459512, sun.gc.generation.0.space.1.capacity=131072, sun.gc.generation.0.space.1.used=-98304, sun.gc.generation.0.space.2.used=65536, sun.gc.generation.1.space.0.used=98304, sun.gc.generation.2.space.0.used=2984, sun.gc.policy.avgBaseFootprint=7584, sun.gc.policy.avgMinorIntervalTime=178, sun.gc.policy.avgMinorPauseTime=-2, sun.gc.policy.avgPromotedAvg=-1894, sun.gc.policy.avgPromotedDev=-10602, sun.gc.policy.avgPromotedPaddedAvg=-33699, sun.gc.policy.avgSurvivedAvg=-86087, sun.gc.policy.avgSurvivedDev=45497, sun.gc.policy.avgSurvivedPaddedAvg=50403, sun.gc.policy.avgYoungLive=-86087, sun.gc.policy.desiredSurvivorSize=65536, sun.gc.policy.edenSize=116195328, sun.gc.policy.freeSpace=116195328, sun.gc.policy.liveSpace=-78528, sun.gc.policy.minorGcCost=-1, sun.gc.policy.minorPauseTime=-1, sun.gc.policy.minorPauseYoungSlope=-1, sun.gc.policy.mutatorCost=1, sun.gc.policy.promoted=98304, sun.gc.policy.survived=-32768, sun.gc.policy.tenuringThreshold=-1, sun.gc.policy.youngCapacity=181403648, sun.gc.tlab.alloc=22643227, sun.gc.tlab.gcWaste=57205, sun.gc.tlab.maxGcWaste=57205, sun.gc.tlab.maxSlowWaste=8, sun.gc.tlab.slowWaste=8}
             |
```

The above supports the theory that getBytes() is slower because it generates more garbage than the alternatives, and highlights the low garbage impact of custom/charset encoder. Running with the stack and hs_rt profilers gives us the following output:

```bash
# Running: psy.lob.saw.utf8.generated.throughput.Utf8EncodingBenchmark.charsetEncoder
...
Iteration   1 (1s in 1 thread): 2.433 ops/msec
      HS(RT) | 128 fat monitors remaining, +0 monitors inflated, +1 monitors deflated
             |                +0 contended lock attempts, +0 parks, +0 notify()'s, +0 futile wakeup(s)
             |                +84 safepoints hit(s), +13459 ms spent on sync safepoints, +20441 ms spent on safepoints
             |
       Stack |  69.9%   RUNNABLE sun.nio.cs.UTF_8$Encoder.encodeArrayLoop
             |  21.7%   RUNNABLE sun.nio.cs.UTF_8.updatePositions
             |   4.8%   RUNNABLE psy.lob.saw.utf8.Utf8EncodingBenchmark.charsetEncoder
             |   3.6%   RUNNABLE sun.nio.cs.UTF_8$Encoder.encodeLoop
             |   0.0%            (other)
             |
# Running: psy.lob.saw.utf8.generated.throughput.Utf8EncodingBenchmark.customEncoder
...
Iteration   1 (1s in 1 thread): 4.478 ops/msec
      HS(RT) | 128 fat monitors remaining, +0 monitors inflated, +1 monitors deflated
             |                +0 contended lock attempts, +0 parks, +0 notify()'s, +0 futile wakeup(s)
             |                +88 safepoints hit(s), +10115 ms spent on sync safepoints, +15418 ms spent on safepoints
             |
       Stack |  85.1%   RUNNABLE psy.lob.saw.utf8.CustomUtf8Encoder.encode
             |  12.6%   RUNNABLE psy.lob.saw.utf8.CustomUtf8Encoder.encodeStringToHeap
             |   1.1%   RUNNABLE psy.lob.saw.utf8.generated.throughput.Utf8EncodingBenchmark.customEncoder_measurementLoop
             |   1.1%   RUNNABLE psy.lob.saw.utf8.Utf8EncodingBenchmark.customEncoder
             |   0.0%            (other)
             |
# Running: psy.lob.saw.utf8.generated.throughput.Utf8EncodingBenchmark.stringGetBytes
...
Iteration   1 (1s in 1 thread): 2.649 ops/msec
      HS(RT) | 128 fat monitors remaining, +0 monitors inflated, +1 monitors deflated
             |                +0 contended lock attempts, +0 parks, +0 notify()'s, +0 futile wakeup(s)
             |                +88 safepoints hit(s), +11087 ms spent on sync safepoints, +19757 ms spent on safepoints
             |
       Stack |  58.1%   RUNNABLE java.lang.StringCoding$StringEncoder.encode
             |  23.3%   RUNNABLE sun.nio.cs.UTF_8$Encoder.encode
             |  16.3%   RUNNABLE psy.lob.saw.utf8.Utf8EncodingBenchmark.stringGetBytes
             |   2.3%   RUNNABLE psy.lob.saw.utf8.generated.throughput.Utf8EncodingBenchmark.stringGetBytes_measurementLoop
             |   0.0%            (other)
             |
```

What I can read from it is that getBytes() spends less time in encoding then the other 2 due to the overheads involved in getting to the encoding phase. Custom encoder spends the most time on on encoding, but what is significant is that as it outperforms charset encoder, and the ratios are similar we can deduce that the encoding algorithm itself is faster.

## But that is not all, oh no, that is not all
The free functionality does not stop here! [To quote Tom Waits](http://www.youtube.com/watch?v=y8YcEmKleJk):

> *It gets rid of your gambling debts, it quits smoking*
> *It's a friend, and it's a companion,*
> *And it's the only product you will ever need*
> *Follow these easy assembly instructions it never needs ironing*
> *Well it takes weights off hips, bust, thighs, chin, midriff,*
> *Gives you dandruff, and it finds you a job, it is a job*
> *...*
> *'Cause it's effective, it's defective, it creates household odors,*
> *It disinfects, it sanitizes for your protection*
> *It gives you an erection, it wins the election*
> *Why put up with painful corns any longer?*
> *It's a redeemable coupon, no obligation, no salesman will visit your home*

'What more?' you ask, well... there's loads more functionality around multi threading I will not attempt to try in this post and several more annotations to play with. In a further post I'd like to go back and compare this awesome new tool with the previous 2 variations of this benchmark and see if, how and why results differ...

Many thanks to the great dudes who built this framework of whom I'm only familiar with [Master Shipilev](https://twitter.com/shipilev) (who also took time to review, thanks again), they had me trial it a few months back and I've been struggling to shut up about it ever since :-)

### Related JMH posts:

1. Benchmarking Multi-Threaded code with JMH
2. Using JMH to benchmark performance counters
3. Using JMH to benchmark Queue latency

**UPDATE (1/08/2013)**: If you are looking for more JMH related info, see [Shipilev's slides on benchmarking](https://shipilev.net/#benchmarking).

**UPDATE (1/07/2014)**: The samples repository has been updated to reflect JMH progress, sample code may have minor differences from the code presented above and command line options may differ. I will follow up at some point with a re-vamp of JMH related posts but for now you can always reproduce above results by reviving the older code from history.



# 用JMH编写Java的微基准测试：干货

演示JMH的用法，并探索该框架如何从简单的基准测试中挤出最后一滴水。

为Java代码编写微基准测试一直是一件相当棘手的事情，有许多陷阱需要注意：

- JIT:
  - **编译前/编译后的行为**：调用10K次（默认值，可通过`-XX:CompileThreshold`进行调整）之后，你的代码将被编译为汇编程序，从而使其有望更快，并且肯定不同于其解释版本。
  - **特化（Specialisation）**：JIT将优化不执行任何操作的代码（即没有副作用），针对单个接口/类的实现进行优化。较小的代码库（如微基准测试）是最佳选择。
  - **循环展开和OSR**可以使基准代码的执行与真实环境不同，通常展开的是被测方法的调用循环。
- GC 效果:
  - **[逃逸分析]()**在基准测试中可能会成功，而在实际代码中可能会失败
  - 运行过程中，GC的累积可能会被忽略，或者可能会包含一个集合。
- **应用程序/线程预热**：在初始化期间，线程的行为和资源分配可能导致程序的执行与稳定运行时的执行大不相同。
- 环境差异：
  - 硬件: CPU/memory/<u>NIC</u> 等...
  - 操作系统
  - JVM: 哪家的哪个版本？啥运行标志？
  - 其他应用程序共享的资源

下面是一系列关于Java微基准测试的文章，它们深入讨论了这个问题：

- [Robust Java Benchmarking](http://www.ibm.com/developerworks/java/library/j-benchmark1/index.html)
- [The perils of benchmarking under dynamic compilation](http://www.ibm.com/developerworks/library/j-jtp12214/)
- [How to write a benchmark?(from StackOverflow)](http://stackoverflow.com/questions/504103/how-do-i-write-a-correct-micro-benchmark-in-java)
- 阅读JMH示例的脚注，以进一步了解该主题的要点。

其中有些问题很难解决，但有些问题可以通过框架来解决，实际上许多框架都是为解决上述问题而编写的。

## JMH 简介

JMH（Java微基准测试工具或<u>Juicy Munchy Hummus</u>，很难说，因为他们在网站上没有告诉您）是最新的微基准测试框架，出自那些致力于使**OpenJDK JVM**飞速发展的人们的工作室，它有望比大多数提供产品更高的准确性和更好的工具。

源代码/项目主页位于[此处](http://openjdk.java.net/projects/code-tools/jmh/)，根据说明，当前需要在本地 build，才能将其安装于本地**maven**仓库中。之后就可在 ***maven*** 项目中依赖它，并开始工作。

[这里](https://github.com/nitsanw/jmh-samples)是我将在本文中使用的项目，请随意拷贝粘贴到你的项目中。它是JMH示例项目的副本（参见下面关于示例当前状态的更新），但 build 出 **JMH jar**，并对maven进行了排序，这样您就可以直接克隆并运行，而无需在本地设置JMH。**就强调基准测试的复杂性而言，原始示例真金白银，请阅读！**命令行输出非常详细且信息丰富，所以要查看一下该工具背后隐藏的内容。

原始示例的基础上，我添加了自己的示例，是框架的基础使用（非常基础），此处的目的是帮助您入门，而不是用细节淹没您，并让你感受到只需很少的努力就可以摆脱困境。开始...