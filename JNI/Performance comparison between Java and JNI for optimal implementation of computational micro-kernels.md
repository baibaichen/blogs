### 4.2 Kernels sample and notations

We select basic micro-kernels, described in Table 5, for our experimentation. Tested kernels computes in double precision. Vectorization is always profitable since data are already packed into memory.

> 我们选择表5中描述的基本微内核进行实验。 经过测试的内核以双精度计算。 矢量化总是有利可图的，因为数据已经打包到内存中了。
>
> 我们选择基本的微内核（如表5所示）进行实验。被测试的核以双精度计算。矢量化总是有利可图的，因为数据已经打包到内存中。

| kernels description                                | arithmetic intensity | properties   |
| -------------------------------------------------- | -------------------- | ------------ |
| **Array addition**. Adds an array inside the other | 1/16                 | memory-bound |
|                                                    |                      |              |
|                                                    |                      |              |
|                                                    |                      |              |



### 4.3 Measurements

Measurements are performed using timers inside a caller method. The caller executes the method we want to benchmark with a given number of iterations then returns the mean time spent executing the method. 

We also iterate over the caller to get the best mean time and then calculate the performance in flop/s performed by the method. 

Measurements are taken in a steady state considering the warm-up phase to compile the hotpots, but also the memory state. Since the caller invokes the method over the same data set, the memory bandwidth reaches its maximum for the given memory complexity. 

The caller arithmetic intensity is almost equal to the arithmetic intensity of the method multiplied by the method invocation count performed by the caller. 

Theoretically, by inlining at source code, we could increase the memory bandwidth and peak performance by swapping the loop iterating over the data with that iterating over the method computation. 

However, by default compilers do not perform this kind of optimization which can disturb numerical precision by switching  floating point operations.

> 使用调用方方法中的计时器执行测量。调用者使用给定的迭代次数执行我们想要基准的方法，然后返回执行该方法所花费的平均时间。
>
> 我们还迭代调用方以获得最佳平均时间，然后计算该方法在flop/s中执行的性能。
>
> 测量是在一个稳定的状态下进行的，考虑到 hotpots 编译时的预热阶段，还有内存状态。由于调用者在同一个数据集上调用该方法，因此对于给定的内存复杂性，内存带宽将达到其最大值。
>
> 调用方算术强度几乎等于方法的算术强度乘以调用方执行的方法调用计数。
>
> 理论上，通过在源代码上进行内联，我们可以通过将数据上的循环与方法计算上的循环交换来增加内存带宽和峰值性能。
>
> 但是，默认情况下，编译器不执行这种优化，这种优化会通过切换燕尾点操作来干扰数值精度。



> 使用调用方方法中的计时器执行测量。调用者以给定的迭代次数执行我们要基准测试的方法，然后返回执行该方法所花费的平均时间。
>
> 我们还遍历调用方以获得最佳平均时间，然后计算该方法执行的flop / s的性能。
>
> 考虑到要编译热点的预热阶段以及内存状态，在稳态下进行测量。由于调用方在同一数据集上调用该方法，因此对于给定的内存复杂性，内存带宽达到其最大值。
>
> 调用者的算术强度几乎等于该方法的算术强度乘以调用者执行的方法调用计数。
>
> 从理论上讲，通过内联源代码，我们可以通过将对数据进行迭代的循环与对方法计算进行迭代的循环交换来增加内存带宽和峰值性能。
>
> 但是，默认情况下，编译器不会执行这种优化，因为这种优化可能会通过切换浮点运算来干扰数值精度。

## 5 Results and analysis

### 5.1 Most efficient implementation

The performance profile allows to locate the most efficient implementation for a given range of memory-per-invocation. This range is supposed to be known for a particular calling context. 

The most efficient implementation of the Array addition kernel is the vectorized and inlined Java version (java_inline_vect) for the whole range of memory-per-invocation considered. This is an example where the JIT works as well as GCC, hence there is no benefits from using JNI in such a case. Concerning the Horizontal sum kernel, the best implementation depends on the considered range of memory-per-invocation. Since native memory breaks the Java language, we don't consider JNI with native memory as eligible. Thus, until 2kB the best implementation is the inlined Java version with an out-of-order optimization (java_inline_ooo). From 2kB the most efficient implementation is the vectorized and out-of-order optimized JNI version (jni_vec_ooo). This kernel is an example where using static compilation leads to benefits but only when the amount of computation is sufficient to cover the invocation cost. The performance profile allows to quantify this threshold.

For the Horner kernel we provide two different algorithms. The coefficient-1st iterates over the polynomial coefficients at first then over the input values. The data-1st iterates over the data-values at first then over the coefficient values. The  most efficient implementation considering both algorithm is provided by the JNI version which is vectorized and out-of-order optimized (jni_vect_ooo). This is true for the whole range of memory-per-invocation considered.

> 性能配置文件允许在给定的每次调用内存范围内找到最有效的实现。 对于特定的调用上下文，应该知道此范围。
>
> 数组添加内核的最有效实现是针对每次调用的整个内存范围的矢量化和内联Java版本（Java inline vect）。这是一个JIT和GCC一起工作的例子，因此在这种情况下使用JNI没有任何好处。关于水平和内核，最佳实现取决于每次调用所考虑的内存范围。由于本机内存破坏了Java语言，我们不认为具有本机内存的JNI是合格的。因此，在2kB之前，最好的实现是带有无序优化的内联Java版本（Java_inline_ooo）。从2kB开始，最有效的实现是矢量化和无序优化的JNI版本（JNI-vec-ooo）。这个内核就是这样一个例子：使用静态编译会带来好处，但只有在计算量足以支付调用成本时才会这样做。性能配置文件允许量化此阈值。
>
> Array添加内核的最有效实现是针对整个调用内存范围的矢量化和内联Java版本（java_inline_vect）。 这是一个JIT与GCC一样工作的示例，因此在这种情况下使用JNI没有任何好处。 关于水平和内核，最佳实现取决于所考虑的每次调用内存范围。 由于本机内存破坏了Java语言，因此我们不认为具有本机内存的JNI是合格的。 因此，直到2kB为止，最好的实现是具有乱序优化的内联Java版本（java_inline_ooo）。 从2kB开始，最有效的实现是向量化且无序优化的JNI版本（jni_vec_ooo）。 该内核是一个示例，其中使用静态编译会带来好处，但前提是计算量足以覆盖调用成本。 性能配置文件允许量化此阈值。
>
> 对于Horner内核，我们提供了两种不同的算法。 系数-1st首先对多项式系数进行迭代，然后对输入值进行迭代。 data-1st首先对数据值进行迭代，然后对系数值进行迭代。 考虑到这两种算法的最有效的实现是由JNI版本提供的，该版本已向量化且无序优化（jni_vect_ooo）。 对于所考虑的每次调用内存的整个范围都是如此。
>
> 对于Horner核，我们提供了两种不同的算法。系数1首先在多项式系数上迭代，然后在输入值上迭代。data-1st首先遍历数据值，然后遍历系数值。考虑到这两种算法，最有效的实现是由矢量化和无序优化的JNI版本（JNI-vect-ooo）提供的。对于所考虑的每次调用的整个内存范围，这是正确的。



### 5.2

Java implementations of the Horizontal sum kernel are not vectorized since the JIT doesn't support the vectorization of reduction idioms.



## 7 结论

In this paper we have presented a performance analysis for a set of micro-kernels considering the JIT  vectorization limitation and the JNI invocation cost. By plotting the performance profile for several different implementations of a kernel, we have aimed to select the most efficient implementation for specific amount of computation.  

We have showed that one major performance issue in Java concerns reduction kernels that are not vectorized.

 As a consequence Java implementations may suffer from severe performance penalties compared to JNI ones. By using native memory we showed that JNI suffers from a major performance penalty  coming from callbacks use to access data inside the Java heap.



本文在考虑JIT矢量化限制和JNI调用代价的情况下，对一组微内核进行了性能分析。通过绘制一个内核的几个不同实现的性能配置文件，我们旨在为特定的计算量选择最有效的实现。我们已经证明了Java中的一个主要性能问题是减少没有矢量化的内核。因此，与JNI实现相比，Java实现可能会遭受严重的性能损失。通过使用本机内存，我们展示了JNI由于用于访问Java堆内数据的回调而遭受的主要性能损失。

