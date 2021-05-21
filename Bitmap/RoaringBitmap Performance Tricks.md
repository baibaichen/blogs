# RoaringBitmap Performance Tricks

Jan 26, 2020 | [java](https://richardstartin.github.io/tags/java)[roaring](https://richardstartin.github.io/tags/roaring)

I have made various contributions to the [RoaringBitmap Java library](https://github.com/RoaringBitmap/RoaringBitmap) since early 2017, often creating performance improvements. Sometimes these performance improvements are completely transparent and just kick in when users update version, but some of them require modified usage in order to benefit. The slightly modified APIs aren’t widely used, and I thought about just adding some more documentation to the library to make sure people find them, but blog posts can be more engaging. I learnt a lot from contributing to this library, so this post will share some tips for getting the most performance from the library, and take some detours into things I learnt making these contributions.

There are no benchmarks in the post, but everything I recommend should lead to at least a 2x performance improvement if you try it, given the outlined assumptions.

1. Background
   1. [What is RoaringBitmap?](https://richardstartin.github.io/posts/roaringbitmap-performance-tricks#what-is-roaringbitmap)
   2. [Who uses RoaringBitmap?](https://richardstartin.github.io/posts/roaringbitmap-performance-tricks#who-uses-roaringbitmap)
2. Optimal Sequential Construction
   1. Container Appender
      1. [Dodging G1 Write Barriers](https://richardstartin.github.io/posts/roaringbitmap-performance-tricks#dodging-g1-write-barriers)
   2. [Constant Memory Appender](https://richardstartin.github.io/posts/roaringbitmap-performance-tricks#constant-memory-appender)
   3. [Comparison with Add](https://richardstartin.github.io/posts/roaringbitmap-performance-tricks#comparison-with-add)
3. [Parallel Aggregation](https://richardstartin.github.io/posts/roaringbitmap-performance-tricks#parallel-aggregation)
4. [Multi-Release Jars: Upgrade your JDK!](https://richardstartin.github.io/posts/roaringbitmap-performance-tricks#multi-release-jars-upgrade-your-jdk)
5. Batch Iteration
   1. [Streamlined Bit Extraction](https://richardstartin.github.io/posts/roaringbitmap-performance-tricks#streamlined-bit-extraction)
   2. [Apache Druid Vectorised Query Engine](https://richardstartin.github.io/posts/roaringbitmap-performance-tricks#apache-druid-vectorised-query-engine)
6. [Serialisation](https://richardstartin.github.io/posts/roaringbitmap-performance-tricks#serialisation)
7. [Property Based Testing](https://richardstartin.github.io/posts/roaringbitmap-performance-tricks#property-based-testing)
8. [Unsigned Bugs](https://richardstartin.github.io/posts/roaringbitmap-performance-tricks#unsigned-bugs)

# Background

## What is RoaringBitmap?

`RoaringBitmap` is like `java.util.BitSet`, except compressed. Wherever you could imagine using a `BitSet`, you could use a `RoaringBitmap`, and often profit from the compression. There are two benefits of compression:

1. Take up less space in RAM and on disk.
2. Taking up less space means faster operations because of better memory locality and cache efficiency.

Knowing a little bit about the compression mechanism helps understand when to use it (or not) and how *not* to benchmark it. The compression mechanism is prefix compression: the higher 16 bits of each value are stored in an array in the top level of a tree. The lower 16 bits of each value are stored in a *container* which stores all of the values in a range corresponding to the same higher 16 bits. Recognising that each 16 bit range can have different density and clustering characteristics, there are three types of container, always requiring less than 8KB:

1. **Sparse**: `ArrayContainer` - a sorted array of 16 bit values plus a 16 bit cardinality. Always fewer than 4096 elements.
2. **Dense**: `BitmapContainer` - a `long[]` just like `java.util.BitSet`, requires one bit per value, plus a 16 bit cardinality. Never fewer than 4096 elements.
3. **Really dense, or clustered**: `RunContainer` - another sorted array of 16 bit values, where each even value is the start of a *run* of set bits, and each odd value is the length of the run. Converted to whenever it saves space.

To understand the compression, imagine you have a set of integer values between 70,000 and 130,000. If you store them in `java.util.BitSet`, you need to store all the values from 0-70,000 even though they are all zero, because it is offset based. This means you need 130,000/8 = ~16KB to store the set. In a `RoaringBitmap` you need to store the value 1 for the higher 16 bits in an array in the top level of the tree, and one container with type depending on the data, plus the overhead for some object references.

If you only have two values in that range, you will end up with an `ArrayContainer`: you need 4 bytes for the cardinality, 4 bytes for the values, and 2 bytes in the top level for the higher bits (plus the object references which are significant overhead at this level of sparseness).

If you have about 5000 values spread evenly throughout the range, you will end up with a `BitmapContainer`: you need 4 bytes for the cardinality, 8KB for the `long[]`, and 2 bytes in the upper level, plus object references.

If you have, say, two runs of values in that range, you will end up with a `RunContainer`: you need 4 bytes for the number of runs, 8 bytes to store the runs, 2 bytes for the higher bits and object reference overhead.

In all of these cases you beat `java.util.BitSet`, but this isn’t true for all data sets. What if you want to store all the odd numbers in a range? In each 16 bit range, you need to store 215215 values, run length encoding can’t help, so need 8KB for a `BitmapContainer`, but you also have the higher bits, cardinalities and object references: marginally more space than `java.util.BitSet`.

What’s the first thing everyone does when they benchmark a data structure like `RoaringBitmap` against `java.util.BitSet`? Generate random data. Decently random data is kryptonite to a compressed data structure because it is effectively incompressible. If you look at the sources of data you might want to store in a bit set, however, you will normally find some process which is either deterministic or has some statistical regularity. For instance, if you are indexing FX trades by currency pair, you will find a Pareto distribution with lots of EURGBP trades and virtually no USDTHB trades. Almost all of the currency pair bitmaps will be sparse, and you will have a few dense bitmaps: EURGBP, USDGBP, and so on. With `RoaringBitmap`, how much space the dense bitmaps take up (whether you get runs or bitmaps) depends on how you sort the data set: sorting by currency pair or a correlated attribute will eliminate most of the space requirement. Using `java.util.BitSet`, the space required in bytes will always be the number of trades times the number of currency pairs divided by eight.

In essence, `RoaringBitmap` can replace `java.util.BitSet` in a lot of cases, but it requires some judgement.

There are interoperable implementations in several languages, including Go, Node, and C++.

## Who uses RoaringBitmap?

Lots of open source data related projects use this library, mostly for implementing [bitmap indexes](https://richardstartin.github.io/posts/how-a-bitmap-index-works). Notably the projects below use RoaringBitmaps, along with many others:

- [Apache Druid](https://github.com/apache/druid)
- [Apache Pinot (incubating)](https://github.com/apache/incubator-pinot)
- [Apache Kylin](http://kylin.apache.org/)
- [Netflix Atlas](https://github.com/Netflix/atlas)

Less obvious open source applications are [Apache Spark](https://github.com/apache/spark), which uses a `RoaringBitmap` to store the statuses of map operations in its memory-constrained driver process, and [Tablesaw](https://github.com/jtablesaw/tablesaw), a data frame written in Java.

# Optimal Sequential Construction

If you have a `RoaringBitmap` and want to add a bit to it, the simplest way is to call [`RoaringBitmap.add`](https://github.com/RoaringBitmap/RoaringBitmap/blob/master/RoaringBitmap/src/main/java/org/roaringbitmap/RoaringBitmap.java#L1051). One of the consequences of the compression strategy used is that adding values is not constant time: it requires a binary search in the top level of the tree. If the bitmap is very sparse, you probably don’t care (𝑙𝑜𝑔21=0log21=0, after all) but if you have a bitmap covering a moderate range, say, one which needs more than a handful of containers, and you do this search every time you add a bit, you can start to notice it.

As you build the bitmap, you may experience container conversions, where containers are automatically converted to more appropriate container types. This is in your long term interest, but creates garbage to collect in the short term. The decision is better deferred to when you have collected all the values for the container.

Another aspect to consider is run length encoding, which is usually deferred until the entire bitmap has been constructed by calling [`RoaringBitmap.runOptimize`](https://github.com/RoaringBitmap/RoaringBitmap/blob/master/RoaringBitmap/src/main/java/org/roaringbitmap/RoaringBitmap.java#L2536), and the data may have been evicted from L1 cache by then. The decision to convert containers to runs cannot depend on data outside the container, so as soon as you have buffered the bits into the container, still fresh in cache, is the best time to make the decision; any new sequentially added data cannot possibly change the outcome. Of course, none of this is really true unless you build the bitmap sequentially.

One of the main use cases for building bitmaps is building a bitmap index from a file or stream of data, where there is a natural ordering and an easy way to assign an integer identity to each row of the data. In these cases, the bitmap will always be built sequentially. [RoaringBitmapWriter](https://github.com/RoaringBitmap/RoaringBitmap/blob/master/RoaringBitmap/src/main/java/org/roaringbitmap/RoaringBitmapWriter.java) aims to make these benefits available. It will let you add bits in any order without penalty, but works much better when the bitmaps are only appended to. If bits are added sequentially, you never need to call `RoaringBitmap.runOptimize`, it happens during the build. They can be built using a fluent API, with a range of heuristic options which can be informed by the collection of historical statistics.

The basic idea is to track the higher 16 bits of the added values (the *key*): whenever this value changes, there is some work to do (flushing) and a container will be appended to the bitmap. In each case, if a non-sequential value is seen, the writer will flush if necessary, and the value will be added to the bitmap.

There are two flavours, one requires a bit more memory than the other.

## Container Appender

Simply accumulates values into a container before appending it to the bitmap, with a configurable guess at which type of container will minimise the number of conversions as the data is buffered. By default, the container will begin its life as an `ArrayContainer` which is probably the best option. `ArrayContainer` itself tries to reward sequential addition.

```
IntStream ints = ...
var writer = RoaringBitmapWriter.writer()
                                .initialCapacity(initialCapacity) // Let's say I have historical data about this and want to reduce some allocations
                                .optimiseForRuns() // in case you *know* the bitmaps typically built are very dense
                                .get();
ints.forEach(writer::add);
var bitmap = writer.get();
```

### Dodging G1 Write Barriers

Every time you add a value to a container, you might get a different container back, because the current implementation may no longer be the best one. For instance, `ArrayContainer` won’t breach 4096 elements because it would take up more space than a `BitmapContainer` (see [`ArrayContainer.add`](https://github.com/RoaringBitmap/RoaringBitmap/blob/master/RoaringBitmap/src/main/java/org/roaringbitmap/ArrayContainer.java#L156)).

```
  // Transform the ArrayContainer to a BitmapContainer
  // when cardinality = DEFAULT_MAX_SIZE
  if (cardinality >= DEFAULT_MAX_SIZE) {
    return toBitmapContainer().add(x);
  }
```

You never need to worry about this as a user of RoaringBitmap, this is just a little tip about the cost of G1 write barriers.

I [wrote about how expensive G1 write barriers can be, on the evening of November 8th 2018, after finding and removing a bottleneck in a parser I had implemented](https://richardstartin.github.io/posts/garbage-collectors-affect-microbenchmarks). Coincidentally, Martin Thompson must have been profiling something similar, with exactly the same garbage collector settings as mine, [and reached the same conclusion 5 days later](https://github.com/real-logic/agrona/commit/532f97e31e47045983b528e700258b9d17b591e1); my findings were in good company.

> SBE 1.10.0 is out. Java codecs see ~25% performance bump with this release and more if using G1 GC (those GC write barriers can hurt!).https://t.co/mXTx1XYSOG
>
> — Martin Thompson (@mjpt777)
>
>  
>
> November 25, 2018

Most of the time, the container reference returned will not point to a new container, and if you just store it, you’ll actually hit the G1 write barrier unnecessarily. This [diff](https://github.com/RoaringBitmap/RoaringBitmap/pull/294/files) recovers ~25% throughput if you use G1.

```
-    container = container.add(lowbits(value));
+    C tmp = container.add(lowbits(value));
+    if (tmp != container) {
+      container = tmp;
+    }
```

G1 is now the default garbage collector!

The branch will almost never be taken, especially if you make a good guess for the type of container to start from, so is likely predictable. Note that scalarisation is not the answer because the container will always escape when it is appended to the bitmap in this case.

## Constant Memory Appender

Uncompressed bitsets have the magic property of being both random access and sorted, making them quite a good data structure to buffer distinct integers into. Given that we only need 8KB, the buffer should always fit in L1 cache as the bitmap is being built. The constant memory bitmap writer makes a space/time/temporary memory tradeoff by buffering all values into 1024 element `long[]`. Whenever a new key is encountered, the `long[]` is flushed. The best type of container is chosen from what’s in the `long[]` *once* and the container is appended to the bitmap being built. In general, this allocates less short term memory and is faster, and can be reused to build another bitmap at the end. If you are building millions of bitmaps at the same time, the cost of the buffer may be prohibitively expensive.

```
IntStream ints = ...
var writer = RoaringBitmapWriter.writer().constantMemory().get();
ints.forEach(writer::add);
var bitmap = writer.get();
```

## Comparison with Add

If you know that you add data in totally random order, this API offers you nothing, except for a small increase in code complexity. Likewise if you know that there is a very hard limit on the *range*, as opposed to cardinality, of the values in the bitmap, it’s not really worth it.

For instance, I went through the [exercise of hacking](https://github.com/apache/spark/pull/24310) this into Apache Spark and withdrew the PR. Since the bitmaps in `HighlyCompressedMapStatus` would almost always have a largest element less than 200,000, the length of the array in the top level of the tree would be at most 3: you can binary search it as much as you like without ever noticing. If this is similar to your application, perhaps don’t bother, unless you want to control temporary memory.

However, I got a [PR accepted to Apache Druid](https://github.com/apache/druid/pull/6764) which used the container appender strategy, showed good improvement in benchmarks, and the change went in to version 0.14.0. Druid is a project which seems to welcome contributions, and I think this was treated as a fairly safe change: limited in scope, and modifying well tested code. Having never been a Druid user, I don’t know if this was ever a particular hot spot, but can only assume it didn’t cause any problems because the change hasn’t been rolled back…

# Parallel Aggregation

A single threaded aggregation, like OR or XOR, can use the `FastAggregation` utility methods.

```
var combined = FastAggregation.or(bitmaps);
```

Since the data structure is naturally splittable into chunks, it’s very easy to parallelise aggregations like OR and XOR. In time linear in the number of containers, you can group the containers by their high bits into a `Map<Character, List<Container>>`. Each entry in that map can be processed in parallel, without any task skew, and reasonable results can be obtained from the high level streams API without bringing in any external dependencies. I implemented [this](https://github.com/RoaringBitmap/RoaringBitmap/pull/211) in early 2018, and should be very easy to use:

```
var combined = ParallelAggregation.or(bitmaps);
```

This gets reasonable results; throughput of large aggregations should get much faster, depending sublinearly on the number of cores. It could be much faster though.

1. The grouping stage is pure overhead; the faster it gets, the better. One option would be to use a primitive hashmap implementation from another library, but another is to wait for inline types, when this will likely automatically get faster.
2. Overhead could be removed by not using streams.
3. It would be cheap to balance the work between workers prior to parallel execution, rather than relying on FJP work stealing.

I can’t personally justify the time and effort required to do something better, and I have only used this in production once, in an application where performance was unimportant, but got satisfying results.

# Multi-Release Jars: Upgrade your JDK!

Lots of useful APIs for working at the kind of level of abstraction `RoaringBitmap` exists at came out with JDK9, but so many projects are stuck with JDK8 for now. How do you make use of these new features when you have users stuck on JDK8? You can’t just increase the language level. [JEP 238](https://openjdk.java.net/jeps/238) proposed multi release jars, which were implemented in JDK9. The basic idea is that you can have two different classes with the same name in two different directories, one implemented in terms of a newer JDK, and the other at the base level, e.g. JDK8.

In RoaringBitmap, there is a module called [shims](https://github.com/RoaringBitmap/RoaringBitmap/tree/master/shims) which has the structure below:

```
src/main/java/org/roaringbitmap/ArraysShim.java
src/java11/java/org/roaringbitmap/ArraysShim.java
```

It is built like this:

```
sourceSets {
    create("java11") {
        java {
            srcDir("src/java11/main")
        }
    }
}

tasks.named<JavaCompile>("compileJava11Java") {
    // Arrays.equals exists since JDK9, but we make it available for 11+ so we can test the shim by using Java 11
    // and the old way by using Java 10, which will compile the new code but not use it..
    sourceCompatibility = "9"
    targetCompatibility = "9"
    options.compilerArgs = listOf("--release", "9")
}

tasks.named<Jar>("jar") {
    into("META-INF/versions/11") {
        from(sourceSets.named("java11").get().output)
    }
    manifest.attributes(
            Pair("Multi-Release", "true")
    )

    // normally jar is just main classes but we also have another sourceset
    dependsOn(tasks.named("compileJava11Java"))
}
```

> Credit: [Marshal Pierce](https://twitter.com/runswithbricks) ported the build to Gradle and cleaned it up a lot in the process

That is, the files under `src/java11/java` are compiled separately, and are packaged at `META-INF/versions/11`, and an attribute `Multi-Release: true` is added to the jar’s manifest. This all means that if you use JDK11 or better, you get access to a vectorised intrinsic `Arrays.equals` overload introduced in JDK9, which makes certain array comparisons at least 3x faster.

```
  /**
   * Checks if the two arrays are equal within the given range.
   * @param x the first array
   * @param xmin the inclusive minimum of the range of the first array
   * @param xmax the exclusive maximum of the range of the first array
   * @param y the second array
   * @param ymin the inclusive minimum of the range of the second array
   * @param ymax the exclusive maximum of the range of the second array
   * @return true if the arrays are equal in the specified ranges
   */
  public static boolean equals(char[] x, int xmin, int xmax, char[] y, int ymin, int ymax) {
    return Arrays.equals(x, xmin, xmax, y, ymin, ymax);
  }
```

If you use JDK8, this is what you get:

```
  /**
   * Checks if the two arrays are equal within the given range.
   * @param x the first array
   * @param xmin the inclusive minimum of the range of the first array
   * @param xmax the exclusive maximum of the range of the first array
   * @param y the second array
   * @param ymin the inclusive minimum of the range of the second array
   * @param ymax the exclusive maximum of the range of the second array
   * @return true if the arrays are equal in the specified ranges
   */
  public static boolean equals(char[] x, int xmin, int xmax, char[] y, int ymin, int ymax) {
    int xlen = xmax - xmin;
    int ylen = ymax - ymin;
    if (xlen != ylen) {
      return false;
    }
    for (int i = xmin, j = ymin; i < xmax && j < ymax; ++i, ++j) {
      if (x[i] != y[j]) {
        return false;
      }
    }
    return true;
  }
```

The gain here is huge, and pays for the complexity but it introduces a few challenges. How do you test both implementations? You need to test with at least two JDK versions now. You either need to have conditional logic in your build to skip a compilation step based on the building JDK (e.g. if you build with JDK8, you cannot compile the JDK11 source code, so need a profile), or you need to choose a JDK version capable of compiling both sets of source code, but deciding *not* to load the newer code for that version. This is simplified by Gradle, thanks to Marshal Pierce, but this was first implemented with Maven and an ant task and was already a mess. Given that there’s now a throwaway JDK release every 6 months, you can rely on this to some extent.

Also, it’s common to shade/shadow jars, even though it breaks encapsulation. Unless the end user ensures that the shaded jar is also `Multi-Release: true`, then they will not benefit because the new code will not be loaded.

Anyway, 3x performance gains (and more, the wider the available vector registers) without messing around with threads is worth this mess. You can use the same technique to get access to nice and much awaited utilities like `Thread.onSpinWait` in library code.

# Batch Iteration

The great thing Daniel Lemire does with his projects is make sure they get implemented in lots of languages. This means you get more people contributing code, increase the probability that someone does something generally useful, and it’s usually easier to port features from languages than to imagine a new one. I have ported features from Go and from C, and implemented features which have been ported to C (and possibly to Go too). Ben Shaw wrote [batch iterators](https://github.com/RoaringBitmap/roaring/pull/150) in the [Go version](https://github.com/RoaringBitmap/roaring) and reported they were a much faster way to get data out of a bitmap. I thought it was a dismal idea at first, but after convincing myself of the [magic of batching](https://richardstartin.github.io/posts/stages), I [implemented them](https://github.com/RoaringBitmap/RoaringBitmap/pull/243) in Java.

They’re kind of awkward to use:

```
    int[] buffer = new int[256];
    RoaringBatchIterator it = bitmap.getBatchIterator();
    while (it.hasNext()) {
      int batch = it.nextBatch(buffer);
      for (int i = 0; i < batch; ++i) {
        doIt(buffer[i]);
      }
    }
```

However, they are much faster (2-10x) than standard iterators. Since this is really a library for library implementors to use, this complexity/performance tradeoff seems justified.

## Streamlined Bit Extraction

Batch iteration or not, you still need to extract bits from `long` values whenever you have `BitmapContainer`s. I think if you really need performance, you need to have some idea about how your code gets JIT compiled, and adjust for it from time to time. You can only choose one JIT compiler to adjust for. C2 still beats Graal on any *CPU intensive* Java benchmark in 2020, so C2 was certainly worth optimising code for in early 2018. This [diff](https://github.com/RoaringBitmap/RoaringBitmap/pull/227) improved bit extraction performance marginally by helping C2 make better use of x86 BMI instructions, just because it recognises the introduced idiom:

```
-    long t = bitset & -bitset;	        
-    array[pos++] = (short) (base + Long.bitCount(t - 1));
-    bitset ^= t;
+    array[pos++] = (short) (base + numberOfTrailingZeros(bitset));
+    bitset &= (bitset - 1);
```

The main change below is that `blsi` and `xor` below are replaced by `blsr`; `tzcnt` and `popcnt` have about the same cost.

```asm
blsi    rbx,r10           
mov     rdi,rbx
xor     rdi,r10           
mov     qword ptr [r11+10h],rdi  
mov     ecx,r8d
shl     ecx,6h
dec     rbx
popcnt  rbx,rbx
add     ecx,ebx 
mov     edi,r10d
shl     edi,6h
tzcnt   r8,r11
add     edi,r8d        
blsr    r11,r11   
```

This made up to 15μs difference per `BitmapContainer` iteration in micobenchmarks. Every little helps!

## Apache Druid Vectorised Query Engine

A new [vectorised query engine](https://github.com/apache/druid/issues/7093) was built into Apache Druid, released in version 0.16.0, which operates on data in batches, rather than one row at a time. It was a [big change](https://github.com/apache/druid/pull/6794) and there’s a bit more to it than picking up batch iterators (I feel obliged to point out that I am English and this was understatement), but these iterators were one of the motivations, and the benefits for Druid users were quite large:

![Druid Vectorised Query Engine](https://richardstartin.github.io/assets/2020/01/roaringbitmap-tips-and-tricks/druid_benchmark.png)

Read more about this in [Imply’s announcement](https://imply.io/post/announcing-druid-0.16.0) of the release of Druid 0.16.0.

# Serialisation

`RoaringBitmap` has a well defined serialisation format which is portable between implementations in different languages, but it turned out the Java serialisation was fairly inefficient. A user [reported](https://github.com/RoaringBitmap/RoaringBitmap/issues/319) it was actually much faster to map the serialised data (you can memory map directly to the serialised format) and then convert it into a `RoaringBitmap` than to use the deserialisation method. I remember being fairly sceptical about this, but it turned out that this code really was very slow:

```
RoaringBitmap bitmap = new RoaringBitmap();
ByteArrayInputStream bais = new ByteArrayInputStream(data);
try (DataInputStream dis = new DataInputStream(bais)) {
    bitmap.deserialize(dis);
 }
```

[Benoit Lacelle](https://github.com/blacelle), a former colleague, supplied a more efficient deserialisation method which deserialised from a `ByteBuffer` directly. Meanwhile, I noticed that if I swapped `DataInputStream` out for an implementation of `DataInput` which operated on a `ByteBuffer`, I could get much better performance (but not as good as using a `ByteBuffer` directly).

```
public static class BufferDataInput implements DataInput {

    private final ByteBuffer data;

    public BufferDataInput(ByteBuffer data) {
      this.data = data;
    }

    @Override
    public short readShort() throws IOException {
      return data.getShort();
    }

    @Override
    public long readLong() throws IOException {
      return data.getLong();
    }
...
  }
```

After I did some profiling, it turned out that the reason `DataInputStream` was so bad was because every time you read a `long` it assembles it from the bytes! This big chunk of code was taken from [`DataInputStream`](https://github.com/openjdk/jdk/blob/a8a2246158bc53414394b007cbf47413e62d942e/src/java.base/share/classes/java/io/DataInputStream.java#L419).

```
    public final long readLong() throws IOException {
        readFully(readBuffer, 0, 8);
        return (((long)readBuffer[0] << 56) +
                ((long)(readBuffer[1] & 255) << 48) +
                ((long)(readBuffer[2] & 255) << 40) +
                ((long)(readBuffer[3] & 255) << 32) +
                ((long)(readBuffer[4] & 255) << 24) +
                ((readBuffer[5] & 255) << 16) +
                ((readBuffer[6] & 255) <<  8) +
                ((readBuffer[7] & 255) <<  0));
    }
```

You benefit by implementing `DataInput` yourself because you get access to `Unsafe.getLong` via `ByteBuffer.getLong()` which is much faster than the code above.

> If you are sceptical about the inefficiency of `DataInputStream`, try running this [benchmark](https://github.com/richardstartin/runtime-benchmarks/blob/master/src/main/java/com/openkappa/runtime/datainput/DataInputBenchmark.java).

Why is it faster to go directly to the `ByteBuffer`? It turned out to be bounds check elimination.

I later [implemented](https://github.com/RoaringBitmap/RoaringBitmap/pull/325) serialisation to `ByteBuffer` which was also much faster. Cutting `DataInput` out of the serialisation/deserialisation API improved performance by 20x in each direction. I had another [PR](https://github.com/apache/druid/pull/7408) accepted to Druid, which went into version 0.15.0. This change was also [picked up by Pinot](https://github.com/apache/incubator-pinot/pull/4087), upgrading from 0.5.10 to 0.8.0, where a very large reduction in query latency was noted.

> “…I was recently looking into the performance of a query against one of our tables; It had a huge IN clause and also had a couple of NOT IN clauses… The base latency of this query was ~450ms. With only updating to roaringbitmap 0.8.0, I see that the latency drops to ~70ms. Thats pretty neat.” - **Sunitha Beeram, LinkedIn**

Whilst these performance improvements can hardly be attributed to the serialisation change alone, and reflect a lot of improvements by lots of people, particularly [Przemek Piotrowski](https://twitter.com/ppiotrow), over several years, that’s quite a big difference.

You can use this API as follows:

```java
    ByteBuffer buffer = ...
    RoaringBitmap deserialised = new RoaringBitmap();
    deserialised.deserialize(buffer);
    ...
    RoaringBitmap bitmap = ...
    ByteBuffer buffer2 = ByteBuffer.allocate(bitmap.serializedSizeInBytes());
    bitmap.serialize(buffer2);
```

If you write new code, be sure to use it!

# Property Based Testing

The library has lots of unit tests but writing these is full of human bias. It’s much more revealing to write down a high level identity between two expressions. That is, to find two equivalent ways of producing the same value, generate lots of random inputs in parallel and check that the results really do match for each of a very large number of cases. Examples of such identities for sets would be De Morgan’s laws and so on. I [implemented](https://github.com/RoaringBitmap/RoaringBitmap/pull/206) a [basic property based test suite](https://github.com/RoaringBitmap/RoaringBitmap/pull/206) for RoaringBitmap, which mostly checks equivalence of basic computations, running thousands of randomised inputs in the fork join pool. For instance:

```
  @Test
  public void xorInvariance() {
    verifyInvariance("xorInvariance", ITERATIONS, 1 << 9,
            (l, r) -> RoaringBitmap.xor(l, r),
            (l, r) -> RoaringBitmap.andNot(RoaringBitmap.or(l, r), RoaringBitmap.and(l, r)));
  }
```

This has found a lot of bugs that you just might not imagine test cases for (often in code I have written), and records the output to a JSON file so it can be reproduced and debugged. This is very useful when evaluating a pull request.

This kind of testing is not enough; it’s complementary to unit testing. When failures from fuzzing are found and understood, deterministic regression test cases must be added.

# Unsigned Bugs

A lot of the stored values in RoaringBitmap need to be 16 bits wide, but need to behave like `int` as far as arithmetic is concerned. Actually using `int` is not an option, because it would roughly double the size of each bitmap. Using `short` is the most obvious option, because it is thought of as a number, and it fits the space requirement. However, it’s signed, which means whenever you need to use it like an `int`, you need to mask it with `0xFFFF`, otherwise it will sign extend. The problem is, if you forget to do this, half the time (whenever the most significant bit is unset) you won’t notice your error. Line and branch coverage statistics don’t tell you whether you tested for all possible values of the *data*, and this is a data value dependent bug. You might have a test for it, or you might not. Over time contributing to this library, I have created, encountered, and fixed a large number of unsigned bugs relating to accidental sign extension in conversion of `short` to `int`. Property based testing is amazing at finding issues like this, but Java actually has an unsigned 16 bit integer type, it’s called `char`!

I [replaced](https://github.com/RoaringBitmap/RoaringBitmap/pull/364) all of the usages of `short` with `char` on a flight from Cyprus to the UK. This was a relatively difficult change to make with IDE based refactoring tools, and it actually took the entire flight to get all the tests passing. This change eliminates this kind of bug forever, along with a lot of masking with `0xFFFF`.

<iframe class="utterances-frame" title="Comments" scrolling="no" src="https://utteranc.es/utterances.html?src=https%3A%2F%2Futteranc.es%2Fclient.js&amp;repo=richardstartin%2Frichardstartin.github.io&amp;issue-term=pathname&amp;theme=github-light&amp;crossorigin=anonymous&amp;url=https%3A%2F%2Frichardstartin.github.io%2Fposts%2Froaringbitmap-performance-tricks&amp;origin=https%3A%2F%2Frichardstartin.github.io&amp;pathname=posts%2Froaringbitmap-performance-tricks&amp;title=RoaringBitmap%20Performance%20Tricks%20%7C%20Richard%20Startin%E2%80%99s%20Blog&amp;description=I%20have%20made%20various%20contributions%20to%20the%20RoaringBitmap%20Java%20library%20since%20early%202017%2C%20often%20creating%20performance%20improvements.%20Sometimes%20these%20performance%20improvements%20are%20completely%20transparent%20and%20just%20kick%20in%20when%20users%20update%20version%2C%20but%20some%20of%20them%20require%20modified%20usage%20in%20order%20to%20benefit.%20The%20slightly%20modified%20APIs%20aren%E2%80%99t%20widely%20used%2C%20and%20I%20thought%20about%20just%20adding%20some%20more%20documentation%20to%20the%20library%20to%20make%20sure%20people%20find%20them%2C%20but%20blog%20posts%20can%20be%20more%20engaging.%20I%20learnt%20a%20lot%20from%20contributing%20to%20this%20library%2C%20so%20this%20post%20will%20share%20some%20tips%20for%20getting%20the%20most%20performance%20from%20the%20library%2C%20and%20take%20some%20detours%20into%20things%20I%20learnt%20making%20these%20contributions.&amp;og%3Atitle=RoaringBitmap%20Performance%20Tricks" style="position: absolute; left: 0px; right: 0px; width: 1px; min-width: 100%; max-width: 100%; height: 268px; border: 0px;"></iframe>



## Richard Startin's Blog

- Richard Startin's Blog

- [ richardstartin](https://github.com/richardstartin)
- [ richardstartin](https://www.twitter.com/richardstartin)