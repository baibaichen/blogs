# OpenJDK’s java.utils.Collection.sort() is broken: The good, the bad and the worst case

**Abstract.** We investigate the correctness of TimSort, which is the main sorting algorithm provided by the Java standard library. The goal is functional verification with <u>**mechanical proofs**</u>. During our verification attempt we discovered a bug which causes the implementation to crash. We characterize the conditions under which the bug occurs, and from this we derive a bug-free version that does not compromise the performance<u>. We formally specify the new version</u> and mechanically verify the absence of this bug with KeY, a state-of-the-art verification tool for Java.

我们研究了 TimSort 的正确性，这是 Java 标准库提供的主要排序算法。目标是通过<u>**机械证明**</u>进行功能验证。 在验证尝试期间，我们发现了一个导致实现崩溃的 [Bug](https://bugs.openjdk.java.net/browse/JDK-8072909)。我们描述了错误发生的条件，并由此得出了不影响性能的无错误版本。<u>我们正式指定新版本</u>，并使用KeY（一种最先进的Java验证工具）机械地验证是否还存在此 BUG

## 1    Introduction

Some of the arguments often invoked against the usage of formal software veri- fication include the following: it is expensive, it is not worthwhile (compared to its cost), it is less effective than bug finding (e.g., by testing, static analysis, or model checking), it does not work for “real” software. In this article we evaluate these arguments in terms of a case study in formal verification.

The goal of this paper is functional verification of sorting algorithms written in Java with mechanical proofs. Because of the complexity of the code under verification, it is essential to break down the problem into subtasks of manage- able size. This is achieved with *contract-based* *deductive verification* [3], where the functionality and the side effects of each method are precisely specified with expressive first-order contracts. In addition, each class is equipped with an in- variant that has to be re-established by each method upon termination. These formal specifications are expressed in the Java Modeling Language (JML) [9].


 We use the state-of-art Java verification tool KeY [4], a semi-automatic, in- teractive theorem prover, which covers nearly full sequential Java. KeY typically finds more than 99% of the proof steps automatically (see Sect. 6), while the re- maining ones are interactively done by a human expert. This is facilitated by the use in KeY of symbolic execution plus invariant reasoning as its proof paradigm. That results in a close correspondence between proof nodes and symbolic pro- gram states which brings the experience of program verification somewhat close to that of debugging.

The work presented here was motivated by our recent success to verify executable Java versions of counting sort and radix sort in KeY with man- ageable effort [5]. As a further challenge we planned to verify a complicated sorting algorithm taken from the widely used OpenJDK core library. It turns out that *the default implementation* of Java’s `java.util.Arrays.sort()` and `java.util.Collection.sort()` method is an ideal candidate: it is based on a complex combination of merge sort and insertion sort [10, 13]. It had a bug history^5^, but was reported as fixed as of Java version 8. We decided to verify the implementation, stripped of generics, but otherwise completely unchanged and fully executable. The implementation is described in Sect. 2.

> 5. https://bugs.java.com/bugdatabase/view_bug.do?bug_id=8011944

During our verification attempt we discovered that the fix to the bug men- tioned above is in fact not working. We succeeded to characterize the conditions under which the bug occurs and results in a crash (Sect. 4) and from this we could derive a bug-free version (Sect. 5) that does not compromise the performance.

We provide a detailed experience report (Sect. 6) on the formal specification and mechanical verification of correctness and termination of the fixed version with KeY (Sects. 5, 6). Summarizing, our real-life case study shows that formal specification and verification, at least of library code, pays off, but also shows the limitations of current verification technology. In Sect. 7 we draw conclusions.

*Related* *work.* Several industrial case studies have already been carried out in KeY [11, 12, 1]. The implementation considered here and its proof is the most complex and one of the largest so far. The first correctness proof of a sort-ing algorithm is due to Foley and Hoare, who formally verified Quicksort by hand [8]. Since then, the development and application of (semi)-automated the- orem provers has become standard in verification. The major sorting algorithms Insertion sort, Heapsort and Quicksort were proven correct by Filliˆatre and Ma- gaud [7] using Coq, and Sternagel [14] formalized a proof of Mergesort within the interactive theorem prover Isabelle/HOL.

*Acknowledgment.* We thank Peter Wong for suggesting to verify TimSort.

## 2    Implementation of TimSort

The default implementation of `java.util.Arrays.sort` for non-primitive types is TimSort, a hybrid sorting algorithm based on mergesort and insertion sort. The algorithm reorders a specified segment of the input array incrementally from left to right by finding consecutive (disjoint) sorted segments. If these segments are not large enough, they are extended using insertion sort. The starting positions and the lengths of the generated segments are stored on a stack. During execution some of these segments are merged according to a condition on the top elements of the stack, ensuring that the lengths of the generated segments are decreasing and the length of each generated segment is greater than the sum of the next two. In the end, all segments are merged, yielding a sorted array.

We explain the algorithm in detail based on the important parts of the Java implementation. The stack of runs (a sorted segment is called here a “run”) is encapsulated by the object variable `ts`. The stack of starting positions and run lengths is represented by the arrays of integers runBase and runLen, respectively. The length of this stack is denoted by the instance variable `stackSize`. The main loop is as follows (with original comments):

```java
// Listing 1. Main loop of TimSort
do {
  // Identify next run
  int runLen = countRunAndMakeAscending(a, lo, hi, c);
  // If run is short, extend to min(minRun, nRemaining)
  if (runLen < minRun) {
    int force = nRemaining <= minRun ? nRemaining : minRun;
    binarySort(a, lo, lo + force, lo + runLen, c);
    runLen = force;
  }
   // Push run onto pending - run stack, and maybe merge
   ts.pushRun(lo, runLen);
   ts.mergeCollapse();
   // Advance to find next run
   lo += runLen;
   nRemaining -= runLen;
} while (nRemaining != 0);
// Merge all remaining runs to complete sort
assert lo == hi;
ts.mergeForceCollapse();
assert ts.stackSize == 1;
```

In each iteration of the above loop, the next run is constructed. First, a maximal ordered segment from the current position `lo` is constructed (the parameter `hi` denotes the upper bound of the entire segment of the array `a` to be sorted). This construction consists in constructing a maximal descending or ascending segment and reversing the order in case of a descending one. If the constructed run is too short (that is, less than `minRun`) then it is extended to a run of length `minRun` using **<u>binary insertion sort</u>** (`nRemaining` is the number of elements yet to be processed). Next, the **starting position** and **the length of the run** is pushed onto the stack of the object variable `ts` by the method pushRun below.

```java
// Listing 2. pushRun
private void pushRun(int runBase, int runLen) {
  this.runBase[stackSize] = runBase;
  this.runLen[stackSize] = runLen;
  stackSize++; 
}
```

The method `mergeCollapse` subsequently checks whether the invariant (lines 5—6 of Listing 3) on the stack of runs still holds, and merges runs until the invariant is restored (explained in detail below). When the main loop terminates, the method `mergeForceCollapse` completes sorting by merging all stacked runs.

```java
// Listing 3. mergeCollapse
/*1 */ //
/*2 */ // Examines the stack of runs waiting to be merged and merges
/*3 */ // adjacent runs until the stack invariants are reestablished:
/*4 */ // 1. runLen[i - 3] > runLen[i - 2] + runLen[i - 1]
/*5 */ // 2. runLen[i - 2] > runLen[i - 1]
/*6 */ // This method is called each time a new run is pushed onto the stack,
/*7 */ // so the invariants are guaranteed to hold for i < stackSize upon
/*8 */ // entry to the method.
/*9 */ 
/*10*/  private void mergeCollapse() {
/*11*/    while (stackSize > 1) {
/*12*/      int n = stackSize - 2;
/*13*/      if (n > 0 && runLen[n-1] <= runLen[n] + runLen[n+1]) {
/*14*/        if (runLen[n - 1] < runLen[n + 1])
/*15*/          n--;
/*16*/        mergeAt(n);
/*17*/      } else if (runLen[n] <= runLen[n + 1]) {
/*18*/        mergeAt(n);
/*19*/      } else {
/*20*/        break; // Invariant is established
/*21*/      }
/*22*/    }
/*23*/  }
```

The method `mergeCollapse` ensures that the top three elements of the stack satisfy the invariant given in the comments above. In more detail, let `runLen[n-1] = C`, `runlen[n] = D`, and `runLen[n+1] = E` be the top three elements. Operationally, the loop is based on the following cases:  

1. If *C  ≤ D* + *E* and *C < E*  then the runs at `n-1` and `n` are merged. 
2. If *C ≤ D* + *E* and *C ≥ E*  then the runs at `n` and `n+1` are merged. 
3. If *C > D* + *E* and *D ≤ E*  then the runs at `n` and `n+1` are merged. 
4. If *C > D* + *E* and *D > E*  then the loop exits.

---

非基本类型的 `java.util.Arrays.sort` 默认实现是 TimSort，一种基于归并排序和插入排序的混合排序算法。 通过从左到右遍历，对输入数组的指定段进行重新排序，该算法查找连续排序（不相交）的段。如果这些段不够大，则使用<u>**插入排序**</u>对其进行扩展。**<u>段</u>**的起始位置和长度存储在堆栈中。在执行期间，根据堆栈顶部元素的条件合并其中一些段，以确保段的长度（从栈的底部向顶部）降序排列，并且每个段的长度大于下两个段之和。最后合并所有段完成排序。

我们基于 Java 实现的重要部分详细说明该算法。变量 `ts` 封装了 **<u>run</u>** 的堆栈（这里将经过排序的段称为 <u>**run**</u> ）。起始位置和行程长度的堆栈分别由整数数组 `runBase` 和 `runLen` 表示。堆栈的长度由变量 `stackSize` 表示。 主循环如下（带有原始注释）：

```java
// 代码 1. TimSort 的主循环
do {
  // 确定下一个 run
  int runLen = countRunAndMakeAscending(a, lo, hi, c);
  // 如果 run 太短，则扩展到 min(minRun，nRemaining)
  if (runLen < minRun) {
    int force = nRemaining <= minRun ? nRemaining : minRun;
    binarySort(a, lo, lo + force, lo + runLen, c);
    runLen = force;
  }
   // Push run onto pending - run stack, and maybe merge
   ts.pushRun(lo, runLen);
   ts.mergeCollapse();
   // Advance to find next run
   lo += runLen;
   nRemaining -= runLen;
} while (nRemaining != 0);
// Merge all remaining runs to complete sort
assert lo == hi;
ts.mergeForceCollapse();
assert ts.stackSize == 1;
```

在上述循环的每次迭代中，将构造下一个 **<u>run</u>**。首先，从当前位置 `lo` 构造一个最大有序段（参数 `hi` 表示段在排序数组 `a` 中的上界）。这将构造最大的升序段或降序段，如果是降序，则反转顺序。如果构造的行程太短（即小于 `minRun` ），则使用<u>**二叉插入排序**</u>将其扩展为长度为 `minRun` 的行程（`nRemaining` 是尚未处理的元素数）。 接下来，通过下面的方法 `pushRun` 将**开始位置**和**行程长度**推入变量 `ts` 表示的堆栈中。

```java
// 代码 2. pushRun
private void pushRun(int runBase, int runLen) {
  this.runBase[stackSize] = runBase;
  this.runLen[stackSize] = runLen;
  stackSize++; 
}
```

 `mergeCollapse` 随后检查堆栈中有关 <u>**run**</u> 的不变式（代码 3 中的第 4-5 行）是否仍然成立，如果不成立则合并 <u>**run**</u> 直到不变式成立（下面详细说明）。 主循环结束时，`mergeForceCollapse` 合并堆栈中所有 <u>**run**</u> 来完成排序。

```java
// 代码 3. mergeCollapse
/*1 */ //
/*2 */ // 检查等待合并的 run 的堆栈，以及
/*3 */ // 合并相邻的 run，直到重新建立堆栈不变式为止：
/*4 */ // 1. runLen[i - 3] > runLen[i - 2] + runLen[i - 1]
/*5 */ // 2. runLen[i - 2] > runLen[i - 1]
/*6 */ // 每次将新的 run 压入堆栈时，都会调用此方法，
/*7 */ // 因此在进入方法时，对于 i < stackSize，
/*8 */ // 这些不变式被保证保持不变
/*9 */ 
/*10*/  private void mergeCollapse() {
/*11*/    while (stackSize > 1) {
/*12*/      int n = stackSize - 2;
/*13*/      if (n > 0 && runLen[n-1] <= runLen[n] + runLen[n+1]) {
/*14*/        if (runLen[n - 1] < runLen[n + 1])
/*15*/          n--;
/*16*/        mergeAt(n);
/*17*/      } else if (runLen[n] <= runLen[n + 1]) {
/*18*/        mergeAt(n);
/*19*/      } else {
/*20*/        break; // 不变式成立
/*21*/      }
/*22*/    }
/*23*/  }
```

`mergeCollapse` 确保堆栈的前三个元素满足上面注释中给出的不变式。更详细地讲，让 `runLen [n-1] = C`，`runlen [n] = D` 和 `runLen [n + 1] = E` 成为前三个元素。 在操作上，循环基于以下情况：

1. 如果 *C  ≤ D* + *E* 且 *C < E*，那么 `n-1` 和 `n` 处的 <u>**run**</u> 合并
2. 如果 *C ≤ D* + *E* 且 *C ≥ E*，那么 `n` 和 `n+1` 处的 <u>**run**</u> 合并
3. 如果 *C > D* + *E* 且 *D ≤ E*，那么 `n` 和 `n+1` 处的 <u>**run**</u> 合并
4. 如果 *C > D* + *E* 且 *D > E*，则退出循环

## 3    Breaking the Invariant 

We next show that the method `mergeCollapse` does not preserve the invariant in the entire run stack, contrary to what is suggested in the comments. To see this, consider as an example the situation where `runLen` consists of <u>120</u>, <u>80</u>, <u>25</u>, <u>20</u>, <u>30</u> on entry of `mergeCollapse`, directly after 30 has been added by `pushRun`. In the first iteration of the `mergeCollapse` loop there will be a merge at 25, since 25 ≤  20 + 30 and 25 *<* 30, resulting in (Listing 3, lines 15 and 16): 120^×^, 80, 45, 30. In the second iteration, it is checked that the invariant is satisfied at 80 and 45 (lines 13 and 17), which is the case since 80 *>* 45 + 30 and 45 *>* 30, and `mergeCollapse` terminates. But notice that the invariant does not hold at 120, since 120 ≤ 80 + 45. Thus, `mergeCollapse` has not fully restored the invariant.

More generally, an error (violation of the invariant) can only be introduced by merging **<u>the second-to-last element</u>** and requires precisely four elements after the position of the error, i.e., at `runLen[stackSize-5]`. Indeed, suppose `runLen` consists of four elements *A, B, C, D* satisfying the invariant (so *A > B* + *C*, *B > C* + *D* and *C > D*). We add a fifth element *E* to `runLen` using `pushRun`, after which `mergeCollapse` is called. The only possible situation in which an error can be introduced, is when *C  ≤ D* + *E* and *C < E*. In this case, *C* and *D* will be merged, yielding the stack *A, B, C* + *D,* *E*. Then `mergeCollapse` checks whether the invariant is satisfied by the new three top elements. But *A* is not among those, so it is not checked whether *A > B* + *C* + *D*. As shown by the above example, this latter inequality does not hold in general.

接下来，我们将说明 `mergeCollapse` 与其注释中的建议相反，并未在 run 的堆栈中维持不变式。为此，考虑如下的例子：`pushRun` 压入 <u>30</u> 进栈，进入 `mergeCollapse` 后，`runLen`  堆栈由 <u>120</u>， <u>80</u>，<u>25</u>，<u>20</u>，<u>30</u> 这几个值组成。 `mergeCollapse` 的第一轮迭代，由于 25 ≤  20 + 30 且 25 *<* 30，因此将在 25 处进行合并（代码 3 中的第15和16行，即上面的情况 1），从而得到：120^×^，80， 45，30。因为 80 *>* 45 + 30 和 45 *>* 30，在 80 和 45 处的检查满足不变式（第 13 和 17 行）， 第二轮迭代 `mergeCollapse` 退出。但请注意，由于 120 ≤ 80 + 45，在 120 处不变式不成立，所以 `mergeCollapse` 并没有完全恢复不变式。

更一般地，错误（违反不变式）只能通过合并<u>**倒数第二个元素**</u>来引入，且错误位置之后恰好有四个元素，即 `runLen[stackSize-5]` 处。实际上，假设 `runLen` 由四个元素组成，*A，B，C，D* 满足不变式（因此 *A > B* + *C*，*B > C* + *D* 和 *C > D*）。我们使用 `pushRun` 将第五个元素 *E* 添加到 `runLen` 中，然后调用 `mergeCollapse`。唯一可能引入的错误情况是，*C  ≤ D* + *E* 且 *C < E*。这时，*C* 和 *D* 将被合并，生成堆栈 *A，B，C* + *D*，*E*。然后 `mergeCollapse` 检查新的三个顶部元素是否满足不变式。但是 *A* 不在其中，所以不检查 *A > B* + *C* + *D*。如以上例所示，后一种不等式通常不成立。

### 3.1   The Length of `runLen`

The invariant affects the maximal size of <u>**the stack of run lengths**</u> during exection; recall that this stack is implemented by `runLen` and `stackSize`. The length of `runLen` is declared in the constructor of TimSort, based on the length of the input array `a` and, as shown below, on the assumption that the invariant holds. For performance reasons it is crucial to choose `runLen.length` as small as possible (but so that stackSize does not exceed it). The original Java implementation  is as follows^6^ (in a recent update the number 19 was changed to 24, see Sect. 4):

> 6. TimSort can also be used to sort only a segment of the input array; in this case, `len` should be based on the length of this segment. In the current implementation this is not the case, which negatively affects performance.

```java
// Listing 4. Bound of runLen based on length of the input array
/*1*/ int len = a.length;
/*2*/ int stackLen = (len <    120 ? 5 :
/*3*/                 len <   1542 ? 10 :
/*4*/                 len < 119151 ? 19 : 40);
```

We next explain these numbers, assuming the invariant to hold. Consider the sequence *(b~i~)~i≥0~*, defined inductively by *b*~0~ = 0, *b*~1~ = 16 and *b*~i+2~ = *b*~i+1~ + *b*~i~ + 1. The number 16 is a general lower bound on the run lengths. Now *b*~0~,. . . ,*b*~n~ are <u>**lower bounds**</u> on the run lengths in an array `runLen` of length *n* that satisfy the invariant; more precisely, *b*~i−1~  ≤ *runLen*[n-i] for all *i* with 0 *<* *i* ≤ *n*.

Let `runLen` be a run length array arising during execution, assume it satisfies the invariant, and let `n = stackSize`. We claim that for any number *B* such that $1 + \sum_{i=0}^{B} b_i > a.length$ we have n ≤ *B* throughout execution. This means that *B* is a safe bound, since the number of stack entries never exceeds *B*.

The crucial property of the sequence (*b~i~*) is that throughout execution we have $\sum_{i=0}^{n-1} b_i < \sum_{i=0}^{n-1}runLen[i]$ using that b~0~ = 0 < runLen[n-1] and b~i-1~ ≤ runLen[n-i]. Moreover, we have $\sum_{i=0}^{B} runLen[i] <= a.length$ since the runs in `runLen` are disjoint segments of *a*. Now for any *B* chosen as above, we have $\sum_{i=0}^{n-1} b_i < \sum_{i=0}^{n-1}runLen[i] <= a.length < 1 + \sum_{i=0}^{B} b_i$ and thus *n* ≤ *B*. Hence, we can safely take `runLen.length` to be the least *B* such that $1 + \sum_{i=0}^{B} b_i > a.length$, If `a.length < 120` we thus have 4 as the minimal choice of the bound, for `a.length < 1542` it is 9, etc. This shows that the bounds used in OpenJDK (Listing 4) are slightly suboptimal (off by 1). The default value 40 (39 is safe) is based on the maximum 2^31^ − 1 of integers in Java.

执行期间，<u>不变式</u>会影响<u>**行程长度堆栈**</u>的最大长度；回想一下，这个堆栈由 `runLen` 数组和 `stackSize` 实现。 `runLen` 数组的长度在 TimSort 的构造函数中声明，这是假设不变式成立，根据输入数组 `a` 的长度计算而得。出于性能原因，`runLen.length` 要尽可能小（但要保证 `stackSize` 不会超过它）。最初的 Java 实现如下^6^（在最近的更新中，数字 19 更改为 24，请参见第 4 节）：

> 6. TimSort 也可以用于只对输入数组的一部分进行排序。 在这种情况下，`len` 应基于这部分要排序的子数组的长度。 在当前的实现中，情况并非如此，这会对性能产生负面影响。

```java
// 代码 4. 基于输入数组长度的 runLen 的上界
/*1*/ int len = a.length;
/*2*/ int stackLen = (len <    120 ? 5 :
/*3*/                 len <   1542 ? 10 :
/*4*/                 len < 119151 ? 19 : 40);
```

现在我们来解释这些数字，假设不变式成立，考虑序列 *(b~i~)~i≥0~*，由 *b*~0~ = 0，*b*~1~ = 16 以及 *b*~i+2~ = *b*~i+1~ + *b*~i~ + 1 归纳定义。数字 16 是行程长度的一般下界。现在，*b*~0~，. . . ，*b~n~* 是长度为 *n* 的数组 `runLen` 中满足不变式的行程的**<u>下界</u>**；更准确地说，对于 0 *<* *i* ≤ *n* 的所有 *i*，*b*~i−1~ ≤ *runLen*[n-i]。

让 `runLen` 是执行过程中产生的行程长度数组，假设它满足不变式，然后让 `n=stackSize`。我们声明，对于任何数字 *B*，使得 $1+\sum_{i = 0}^{B}b_i > a.length$，在整个执行过程中 n ≤ *B*。 这意味着 *B* 是一个安全的界限，因为堆栈条目的数量永远不会超过 *B*。

在整个执行过程中，序列 (*b~i~*) 的关键属性 $\sum_{i=0}^{n-1} b_i < \sum_{i=0}^{n-1}runLen[i]$，这是通过 b~0~ = 0 < runLen[n-1] 和  b~i-1~ ≤ runLen[n-i] 得到。另外，我们有 $\sum_{i=0}^{B} runLen[i] <= a.length$，因为 `runLen` 数组中的 <u>**run**</u> 是 *a* 中不相交的段。现在对于上面选择的任何 *B*，我们有 $\sum_{i=0}^{n-1} b_i < \sum_{i=0}^{n-1}runLen[i] <= a.length < 1 + \sum_{i=0}^{B} b_i$ ，所以 *n* ≤ *B*。因此，我们可以安全地将 `runLen.length` 设为最小的 *B*，使得 $1 + \sum_{i=0}^{B} b_i > a.length$，如果 `a.length < 120`，那么 4 作为上界的最小选择，对于  `a.length < 1542`，是 9，依此类推。这表明 OpenJDK（代码 4）中使用的边界次优（差1）。默认值 40（ 39 是安全的）是基于 Java 中最大的正整数是 2^31^ − 1。

## 4    Worst Case Stack Size

In section 3 we showed that the declared length of `runLen` is based on the invariant, but that the invariant is not fully preserved. However, this does not necessarily result in an actual error at runtime. The goal is to find a bad case, i.e., an input array for TimSort of a given length *k*, so that stackSize becomes larger than runLen.length, causing an ArrayIndexOutOfBoundsException in pushRun. In this section we show how to achieve the *worst case*: the maximal size of a stack of run lengths which does not satisfy the invariant. For certain choices of *k* this *does* result in an exception during execution of TimSort, as we show in Section 4.1. Not only does this expose the bug, our analysis also provides a safe choice for `runLen.length` that avoids the out-of-bounds exception.

The general idea is to construct a list of run lengths that leads to the worst case. This list is then turned into a concrete input array for TimSort by generating actual runs with those lengths. For instance, a list (2,3,4) of run lengths is turned into the input array (0,1,0,0,1,0,0,0,1) of length *k* = 9.

The sum of all runs should eventually sum to *k*. Hence, to maximize the stack size, the runs in the worst case are short. A run that breaks the invariant is too short, so the worst case occurs with a maximal number of runs that break the invariant. However, the invariant holds for at least half of the entries:

在第 3 节中，我们证明了 `runLen` 的长度是基于不变式，但是该不变式并未完全保留。不过这并不一定会在运行时导致实际错误。我们的目标是找到一种**<u>最坏的情况</u>**，即对 TimSort 的输入数组，给定长度 *k* ，使 `stackSize` 大于 `runLen.length`，导致 `pushRun` 抛出 `ArrayIndexOutboundsException`。本节说明如何实现**最坏情况**：不满足不变式的<u>**==行程长度堆栈==**</u>的最大大小。对于某些 *k* 的选择，这**确实**在执行 TimSort 时导致异常，如我们在第 4.1节 中所示。这不仅暴露了错误，我们的分析还为 `runLen.length` 提供了一个安全的选择，避免了越界异常。

大体思路是构造导致最坏情况的行程长度列表。然后根据这些长度生成实际的 **<u>run</u>**，最后将这些 <u>**run**</u> 转换为 TimSort 的具体输入数组。例如行程长度列表（2,3,4）变成长度 *k*  = 9 的输入数组（0,1,0,0,1,0,0,0,1）。

所有 <u>**run**</u> 长度的总和最终应为 *k*。因此，为了最大化堆栈大小，最坏情况下的运行长度要短。打破不变式的行程长度太短，所以最坏的情况下破坏不变式的行程数量最多。但是，至少一半的条目满足不变式:

**Lemma 1.** *Throughout execution of TimSort, the invariant cannot be violated at two consecutive runs in* `runLen`.

*Proof.* Suppose, to the contrary, that two consecutive entries *A* and *B* of the run length stack violate the invariant. Consider the moment that the error at *B* is introduced, so *A* is already incorrect. The analysis of Sect. 3 reveals that there must be exactly four more entries after *B* on the stack (labelled *C . . . F* ) satisfying *D* ≤ *E* + *F* and *D < F* to trigger the merge below:

*A^×^   B       C        D         E       F* 
*A^×^   B^×^     C        D + E   F* 

Merging stops here (otherwise *B^×^* would be corrected), and we have <u>1. *D < F*</u> and <u>2. *C > D* + *E* + *F*</u> . Next, consider the moment that *C* was generated. Since *A^×^* is incorrect, *C* must be the result of merging some *C*~1~ and *C*~2~:

*A*     *B      C*~1~    *C*~2~    *D'*

This  gives: <u>3.  *C*~1~ + *C*~2~  = *C*</u>,  <u>4.  *C*1  *>  C*2</u>,  <u>5.  *C*1  *<  D'*</u>,  <u>6. *D' ≤  D*</u>.  Finally,  all run lengths must be positive, thus: <u>7. *E >* 0</u>. It is easy to check that constraints 1.–7. yield a contradiction.

 

---

**引理 1**：在 TimSort 的执行过程中，不会在 `runLen` 中两个连续的 <u>**run**</u> 上违反不变式。

**证明**：为了引出矛盾，假设行程长度堆栈的两个连续条目 *A* 和 *B* 违反了不变式。考虑一下引入 *B* 的错误的时刻，此时 *A* 已经不正确。 对第 3 节的分析表明，在堆栈上 *B* 之后（标记为 *C ... F*），肯定还有四个条目满足 *D* ≤ *E* + *F* 和 *D < F* 触发以下合并：

*A^×^   B       C        D         E       F* 
*A^×^   B^×^     C        D + E   F*


### 4.1   Breaking TimSort

We implemented the above construction of the worst case [6]. Executing TimSort on the generated input yields the following stack sizes (given array sizes):

| array size          | 64   | 128  | 160  | 65536    | 131072 | 67108864 | 1073741824 |
| ------------------- | ---- | ---- | ---- | -------- | ------ | -------- | ---------- |
| required stack size | 3    | 4    | 5    | 21       | 23     | 41       | 49         |
| runLen.length       | 5    | 10   | 10   | 19  (24) | 40     | 40       | 40         |

The table above lists the required stack size for the worst case of a given length. The third row shows the declared bounds in the TimSort implementation (see Listing 4). The number 19 was recently updated to 24 after a bug report^1^. 

This means that, for instance, the worst case of length 160 requires a stack size of 5, and thus the declared runLen.length = 10 suffices. Further observe that 19 does not suffice for arrays of length at least 65536, whereas 24 does. For the worst case of length 67108864, the declared bound 40 does not suffice, and running TimSort yields an unpleasant result:

```java
// Listing 5. Exception during exection of TimSort
Exception in thread ”main” java.lang.ArrayIndexOutOfBoundsException: 40 
  at  java.util.TimSort.pushRun(TimSort.java:386)
  at java.util.TimSort.sort(TimSort.java:213) at java.util.Arrays.sort(Arrays.java:659)
  at TestTimSort.main(TestTimSort.java:18)
```

## 5    Verification of a fixed version

In section 3 we showed that `mergeCollapse` does not fully reestablish the invariant, which led to an `ArrayIndexOutOfBoundsException` in `pushRun`. The previous section provides a possible workaround: adjust `runLen.length` using a worst-case analysis. That section also made clear that this analysis is based on an <u>**intricate**</u> argument that seems infeasible for a mechanized correctness proof. 

Therefore, we provide a more principled solution. We fix `mergeCollapse` so that the class invariant *is* reestablished, formally specify the new implementation in JML and provide <u>**a formal correctness proof**</u>, focussing on the most important specifications and <u>**proof obligations**</u>. This formal proof has been fully mechanized in the theorem prover KeY [4] (see section 6 for an experience report).

第 3 节说明了 `mergeCollapse` 并没有完全重建不变式，这导致了 `pushRun` 中的 `ArrayIndexOutOfBoundsException`。 上一节提供了一种可能的解决方法：使用最坏情况分析来调整 `runLen.length`。这一节还明确指出，这一分析是基于一个<u>**==复杂==**</u>的论证，对于机械化的正确性证明来说似乎是不可行的。

因此，我们提供一个更根本的解决方案。我们修复了 `mergeCollapse`，以重新建立类不变式，在 JML 中正式指定新的实现，并提供<u>**形式化的正确性证明**</u>，重点放在最重要的规范和<u>**证明义务**</u>上。这种形式化的证明已在定理证明器 KeY  [4]中得到了充分机械化（有关经验报告，请参见第6节）。

```java
//     Listing 6. Fixed version of mergeCollapse
/*1 */ private void mergeCollapse() {
/*2 */   while (stackSize > 1) {
/*3 */     int n = stackSize - 2;
/*4 */     if (   n >= 1 && runLen[n - 1] <= runLen[n] + runLen[n+1]
/*5 */         || n >= 2 && runLen[n - 2] <= runLen[n] + runLen[n-1]) {
/*6 */         if (runLen[n-1] < runLen[n+1])
/*7 */           n--;
/*8 */     } else if (runLen[n] > runLen[n+1]) {
/*9 */        break; // Invariant is established
/*10*/     }
/*11*/     mergeAt(n);
/*12*/   }
/*13*/ }
```

Listing 6 shows the new version of `mergeCollapse`. The basic idea is to check validity of the invariant on the top 4 elements of `runLen` (lines 4, 5 and 8), instead of only the last 3, as in the original implementation. Merging continues until the top 4 elements satisfy the invariant, at which point we break out of the merging loop (line 9). This guarantees that *all* runs obey the invariant, as proved below.

To obtain a human readable specification and a feasible (mechanized) proof, we introduce suitable abstractions using the auxiliary predicates below:

代码 6 是 `mergeCollapse` 的新版本。基本思想是检查 `runLen` 的前 4 个元素（第4、5和8行）上不变式的有效性，而不是像最初现中那样仅检查最后 3 个元素的有效性。合并继续进行直到前 4 个元素满足不变式，此时我们将脱离合并循环（第 9 行）。这保证了**所有的**行程遵循不变式，如下所示。

为了获得可读的规范和可行的（机械化）证明，我们使用以下辅助谓词以引入合适的抽象：


| Name                               | Definition                                                   |
| ---------------------------------- | ------------------------------------------------------------ |
| elemBiggerThanNext2(*arr, idx*)    | (0 ≤ *idx*  ∧ *idx* + 2 *< arr.length*) →  <br />*arr*[*idx*] *> arr*[*idx* + 1] + *arr*[*idx* + 2] |
| elemBiggerThanNext(*arr, idx*)     | 0 ≤ *idx*  ∧ *idx* + 1 *< arr.length* →  <br />*arr*[*idx*] *> arr*[*idx* + 1] |
| elemLargerThanBound(*arr, idx, v*) | 0 ≤ *idx < arr.length* → <br />*arr*[*idx*] ≥ *v*            |
| elemInv(*arr, idx, v*)             | elemBiggerThanNext2(*arr, idx*) ∧ <br />elemBiggerThanNext(*arr, idx*) ∧  <br />elemLargerThanBound(*arr, idx, v*) |

Intuitively, the formula `elemInv(runLen, i, 16)` is that `runLen[i]` satisfies the invariant as given in lines 5—6 of Listing 3, and has ength at least 16 (recall that this is a lower bound on the minimal run length). Aided by these predicates we are ready to express the formal specification, beginning with the class invariant. 

*Class Invariant.* A class invariant is a property that all instances of a class should satisfy. In a design by contract setting, each method is proven in isolation (assuming the contracts of methods that it calls), and the class invariant can be assumed in the precondition and must be established in the postcondition, as well as at all call-sites to other methods. The latter ensures that it is safe to assume the class invariant in a method precondition. A precondition in JML is given by a `requires` clause, and a postcondition is given by `ensures`. To avoid manually adding the class invariant at all these points, JML offers an `invariant` keyword which *implicitly* conjoins the class invariant to all pre- and postconditions. A seemingly natural candidate for the class invariant states that all runs on the stack satisfy the invariant and have a length of at least 16. However, `pushRun` does not preserve this invariant. Further, inside the loop of `mergeCollapse` (Listing 6) the `mergeAt` method is called, so the class invariant must hold. But at that point the invariant can be temporarily violated by the last 4 runs in `runLen` due to ongoing merging. Finally, the last run pushed on the stack in the main sorting loop (Listing 1) can be shorter than 16 if fewer items remain. The class invariant given below addresses all this:

