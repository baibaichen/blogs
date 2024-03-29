# Vectorization vs. Compilation in Query Execution

## 3. CASE STUDY: SELECT

We now turn our attention to a micro-benchmark that tests conjunctive selections:

```SQL
  WHERE col1 < v1 AND col2 < v2 AND col3 < v3
```

Selection primitives shown in Algorithm 2 create vectors of indexes for which the condition evaluates to true, called `selection vectors`. Selection primitives can also take a selection vector as parameter, to evaluate the condition only on elements of the vectors from the positions pointed to by the selection vector^5^. A vectorized conjunction is implemented by chaining selection primitives with the output selection vector of the previous one being the input selection vector of the next one, working on a tightening subset of the original vectors, evaluating this conjunction lazily only on those elements for which the previous conditions passed.

> 5. In fact, other primitives are also able to work with selection vectors, but it was removed from code snippets where not necessary for the discussed experiments.

> **Algorithm 2** Implementations of < selection primitives. All algorithms return the number of selected items (return j). For mid selectivities, branching instructions lead to branch mispredictions. In a vectorized implementation such branching can be avoided. VectorWise dynamically selects the best method depending on the observed selectivity, but in the micro-benchmark we show the results for both methods.
>
> ```C
> // Two vectorized implementations
> // (1.) medium selectivity : non−branching code
> idx sel_lt_T_col_T_val(idx n, T *res, T *col1, T *val2, idx *sel ) {
>     if (sel == NULL) {
>        for (idx i=0, idx j =0; i<n ; i++) {
>          res[j] = i; j += (col1[i] < val2[0]);
>        }
>     } else {
>        for (idx i=0, idx j=0; i<n ; i++) {
>          res[j] = sel[i]; j += (col1[sel[i]] < *val2 ) ;
>        }
>     }
>     return j ;
> }
> 
> // (2.) else : branching selection
> idx sel_lt_T_col_T_val(idx n, T *res, T *col1, T *val2, idx *sel) {
>     if (sel == NULL) {
>        for (idx i=0, idx j =0; i<n; i++)
>          if ( col1[i] < *val2 ) 
>           res[j++] = i ;
>     } else {
>        for (idx i=0, idx j =0; i<n ; i++)
>          if (col1[sel[i]] < *val2)
>            res[j++] = sel[i] ;
>     }
>     return j ;
> }
> 
> // Vectorized conjunction implementation :
> const idx LEN=1024;
> idx sel1[LEN], sel2[LEN], res[LEN], ret1, ret2, ret3;
> ret1 = sel_lt_T_col_t_val(LEN, sel1, col1, &v1, NULL);
> ret2 = sel_lt_T_col_t_val(ret1, sel2, col1, &v1, sel1);
> ret3 = sel_lt_T_col_t_val(ret2, res, col1, &v1, sel2) ;
> ```

Each condition may be evaluated with one of two implementations of selection primitive. The naive “branching” implementation of selection evaluates conditions lazily and branches out if any of the predicates fails. If the selectivity of conditions is neither very low or high, CPU branch predictors are unable to correctly guess the branch outcome. This prevents the CPU from filling its pipelines with useful future code and hinders performance. In [11] it was shown that a branch (control-dependency) in the selection code can be transformed into a data dependency for better performance.

The `sel_lt` functions in Algorithm 2 contain both approaches. The VectorWise implementation of selections uses a mechanism that chooses either the branch or non-branch strategy depending on the observed selectivity ^6^. As such, its performance achieves the minimum of the vectorized branching and non-branching lines in Figure 2.

> 6. It even re-orders dynamically the conjunctive predicates such that the most selective is evaluated first.

In this experiment, each of the columns col1, col2, col3 is an integer column, and the values v1, v2 and v3 are constants, adjusted to control the selectivity of each condition. Here, we keep the selectivity of each branch equal, hence to the cube root of the overall selectivity, which we vary from 0 to 1. We performed the experiment on 1K input tuples.

> **Algorithm 3** Four compiled implementations of a conjunctive selection. Branching cannot be avoided in loopcompilation, which combines selection with other operations, without executing these operations eagerly. The four implementations balance between branching and eager computation.
>
> ```C
> // (1.) all predicates branching (”lazy”)
> idx c0001(idx n ,T* res, T* col1, T* col2, T* col3, T* v1, T* v2, T* v3) {
>      idx i , j =0;
>      for (i=0; i<n; i++)
>         if (col1[i] < *v1 && col2[i] < *v2 && col3[i] < *v3)
>           res[j++] = i ;
>      return j ; // return number of selected items .
> }
> 
> // (2.) branching 1 ,2 , non−br. 3
> idx c0002(idx n ,T* res, T* col1, T* col2, T* col3, T* v1, T* v2, T* v3) {
>      idx i , j =0;
>      for (j=0; i<n; i++)
>         if (col1[i] < *v1 && col2[i] < *v2 ) {
>           res[j] = i ; 
>           j += col3[i] < *v3 ;
>        }
>      return j;
> }
> 
> // (3.) branching 1 , non−br. 2 ,3
> idx c0003(idx n ,T* res, T* col1, T* col2, T* col3, T* v1, T* v2, T* v3) {
>      idx i , j =0;
>      for (i=0; i<n; i++)
>         if(col1[i] < *v1 ) {
>           res[j] = i; j += col2[i] < *v2 & col3[i] < *v3
>        }
>      return j ;
> }
> 
> // (4.) non−branching 1 ,2 ,3 , (”compute−all ”)
> idx c0004(idx n ,T* res, T* col1, T* col2, T* col3, T* v1, T* v2, T* v3) {
>      idx i , j =0;
>      for ( i =0; i<n ; i++) {
>         res[j] = i ;
>         j+= (col1[i] < *v1 & col2[i] < *v2 & col3[i] < *v3 )
>      }
>      return j ;
> }
> ```

Figure 2 shows that compilation of conjunctive Select is inferior to the pure vectorized approach. The lazy compiled program does slightly outperform vectorized branching, but for the medium selectivities branching is by far not the best option. The gist of the problem is that the trick of (i) converting all control dependencies in data dependencies while still (ii) avoiding unnecessary evaluation, cannot be achieved in a single loop. If one avoids all branches (the “compute-all” approach in Algorithm 3), all conditions always get evaluated, wasting resources if a prior condition already failed. One can try mixed approaches, branching on the first predicates and using data dependency on the remaining ones. They perform better in some selectivity ranges, but maintain the basic problems – their worst behavior is when the selectivity after branching predicates is around 50%.

----

现在我们将注意力转向过滤条件 `AND` 的**==微观基准==**：

```SQL
  WHERE col1 < v1 AND col2 < v2 AND col3 < v3
```

算法 2 中的**==选择原语==**创建**过滤条件为 `true` 的索引向量**，称为 `selection vectors`。选择原语也可以将选择向量作为参数，仅对<u>**==选择向量==**</u>^5^指向位置上的元素进行条件求值。向量化 `AND` 是通过串联**==选择原语==**来实现的，前一个选择原语输出的**选择向量**是下一个选择原语的输入向量，是一个比原始向量更小的子集，只对已通过先前条件测试的那些元素进行惰性计算。

> 5. 实际上，其他原语也可以与<u>**==选择向量==**</u>一起使用，但是已从代码段中删除了这些代码，这对于所讨论的实验不是必需的。

> **算法2 ** 选择原语 `<` 的实现。 所有算法都返回所选项目的数量（返回 `j`）。对于中等选择性，分支指令会导致分支预测错误。向量化的实现可以避免这种分支。 VectorWise 会根据观察到的选择性动态选择最佳方法，但在微基准测试中，我们将显示两种方法的结果。
>
> ```C
> // 两种向量化的实现
> // (1.) 中等选择性：非分支代码
> idx sel_lt_T_col_T_val(idx n, T *res, T *col1, T *val2, idx *sel ) {
>   if (sel == NULL) {
>       for (idx i=0, idx j=0; i<n ; i++) {
>          res[j] = i; j += (col1[i] < val2[0]); // val2[0] <=> *val2
>      }
>   } else {
>      for (idx i=0, idx j=0; i<n ; i++) {
>          res[j] = sel[i]; j += (col1[sel[i]] < *val2 ) ;
>      }
>   }
>   return j ;
> }
> 
> // (2.) else：分支选择代码
> idx sel_lt_T_col_T_val(idx n, T *res, T *col1, T *val2, idx *sel) {
>   if (sel == NULL) {
>      for (idx i=0, idx j =0; i<n; i++)
>          if ( col1[i] < *val2 ) 
>           res[j++] = i ;
>   } else {
>      for (idx i=0, idx j =0; i<n ; i++)
>          if (col1[sel[i]] < *val2)
>            res[j++] = sel[i] ;
>   }
>   return j ;
> }
> 
> // Vectorized conjunction implementation :
> const idx LEN=1024;
> idx sel1[LEN], sel2[LEN], res[LEN], ret1, ret2, ret3;
> ret1 = sel_lt_T_col_t_val( LEN, sel1, col1, &v1, NULL);
> ret2 = sel_lt_T_col_t_val(ret1, sel2, col1, &v1, sel1);
> ret3 = sel_lt_T_col_t_val(ret2,  res, col1, &v1, sel2) ;
> ```

**==选择原语==**有两种实现方法。简单的 `if-else` 实现，延迟计算条件，不满足条件的时候分叉。如果条件的选择性一般（既不低也高），CPU 的分支预测器就不能正确地猜测分支结果。这会使得 CPU 在其执行管道中填充没用的执行代码，因此降低性能。在[11]中，为了获得更好的性能，<u>**可以将选择代码中的分支（控制依赖）转换为数据依赖**</u>。

算法2中的 `sel_lt` 函数两种方法都有实现。VectorWise 基于观测到的选择性^6^，来判断是使用分支策略还是非分支策略。因此，其性能达到了图2中向量化分支和非分支线的最小值。

> 6. 甚至可以动态地重排连接谓词，以便首先计算最有选择性的谓词。

在我们的实验中，col1、col2、col3 都是整数列，v1、v2 和 v3 是常数，调整常数值可控制每种条件的选择性。这里每个条件的选择性相等，总的选择性是其三次方，从0到1变化。实验的输入元组有 1K 行。

> **算法3 ** 三个 `<` 条件 `AND` 的四个编译实现。 循环编译无法避免分支，因为循环编译将选择与其他操作结合在一起，而不急于执行这些操作。 四种实现在分支计算和及时计算之间取得平衡。
>
> **算法3** 连接选择的四个编译实现。在循环编译中，分支是不可避免的，循环编译将选择与其他操作结合在一起，而不急于执行这些操作。这四种实现在分支和急切计算之间取得了平衡。
>
> ```C
> // (1.) all predicates branching (”lazy”)
> idx c0001(idx n ,T* res, T* col1, T* col2, T* col3, T* v1, T* v2, T* v3) {
>   idx i , j =0;
>   for (i=0; i<n; i++)
>      if (col1[i] < *v1 && col2[i] < *v2 && col3[i] < *v3)
>        res[j++] = i ;
>   return j ; // return number of selected items .
> }
> 
> // (2.) branching 1 ,2 , non−br. 3
> idx c0002(idx n ,T* res, T* col1, T* col2, T* col3, T* v1, T* v2, T* v3) {
>   idx i , j =0;
>   for (j=0; i<n; i++)
>      if (col1[i] < *v1 && col2[i] < *v2 ) {
>        res[j] = i ; 
>        j += col3[i] < *v3 ;
>     }
>   return j;
> }
> 
> // (3.) branching 1 , non−br. 2 ,3
> idx c0003(idx n ,T* res, T* col1, T* col2, T* col3, T* v1, T* v2, T* v3) {
>   idx i , j =0;
>   for (i=0; i<n; i++)
>      if(col1[i] < *v1 ) {
>        res[j] = i; j += col2[i] < *v2 & col3[i] < *v3
>     }
>   return j ;
> }
> 
> // (4.) non−branching 1 ,2 ,3 , (”compute−all ”)
> idx c0004(idx n ,T* res, T* col1, T* col2, T* col3, T* v1, T* v2, T* v3) {
>     idx i , j =0;
>     for ( i =0; i<n ; i++) {
>        res[j] = i ;
>        j+= (col1[i] < *v1 & col2[i] < *v2 & col3[i] < *v3 )
>     }
>     return j ;
> }
> ```

图 2 表明，过滤条件 `AND` 的场景下，编译效果不如单纯使用向量化方法。延迟编译的性能略优于分支版本的向量化方案，但对于中等选择性来说，分支方案远远不是最佳选择。问题的关键在于：i）将所有控制依存关系转换为数据依存关系，同时又（ii）避免不必要的计算，这一技巧不能在单个循环中实现。如果想避免所有分支（算法3中**全部计算**的方法），则要始终计算所有条件，如果前面的条件已经失败，则浪费资源。可以尝试混合方法，在第一个谓词上使用分支，对剩余的谓词使用数据依赖性。它们在某些选择性范围内表现更好，但仍然存在基本问题——当分支谓词后的选择性约为50％时，性能最差。

## 4. CASE STUDY: HASH JOIN

Our last micro-benchmark concerns Hash Joins:


```SQL
SELECT
  build.col1, build.col2, build.col3 
WHERE 
  probe.key1 = build.key1 AND 
  probe.key2 = build.key2 
FROM probe, build
```

We focus on an equi-join condition involving keys consisting of two (integer) columns, because such composite keys are more challenging for vectorized executors. This discussion assumes simple bucket-chaining, such as used in VectorWise, presented in Figure 3. This means that keys are hashed on buckets in an array B with size N which is a power of two. Each bucket contains the offset of a tuple in a value space V . This space can either be organized using DSM or NSM layout; VectorWise supports both [14]. It contains the values of the build relation, as well as a next-offset, which implements the bucket chain. A bucket may have a chain of length > 1 either due to hash collisions, or because there are multiple tuples in the build relation with the same key.

```C
// Algorithm 4 Vectorized implementation of hash probing.

/* 初始计算第一列的哈希值 */
map_hash_T_col(idx n , idx* res, T* col1 ){
  for (idx i=0; i<n ; i++)
    res[i] = HASH(col1[i]) ;
}
/* 计算其他列的哈希值，使用 ^ 组合其他列的哈希值 */
map_rehash_idx_col_T_col(idx n , idx* res, idx* col1, T* col2) {
  for (idx i=0; i<n ; i++)
    res[i] = col1[i] ˆ HASH(col2[i]) ;
}

map_fetch_idx_col_T_col(idx n , T* res, idx* col1, T* base, idx* sel){
  if(sel) {
    for (idx i=0; i<n ; i++)
      res[sel[i]] = base[col1[sel[i]]] ;
  } else {
    /* sel == NULL, omitted  */
  }
}

/*是否真正匹配*/
map_check_T_col_idx_col_col_T_col(idx n, chr* res, T* keys, T* base, idx* pos, 
                                  idx* sel) {
  /*
  keys => build relation key
  base => probe relation key
  pos  => found chain head position in HT 
  sel  => 哈希值数组的索引，也是 build relation key 数组的索引
  n    => 是根据 sel 的长度来定的
  */
  if (sel) {
    for (idx i=0; i<n ; i++)
      res[sel[i]] = (keys[sel[i]] != base[pos[sel[i]]]);
  } else {
    /* sel == NULL, omitted  */
  }
}

map_recheck_chr_col_T_col_idx_col_T_col(idx n, chr* res, chr* col1, T* keys,
                                        T* base, idx* pos, idx* sel) {
  if(sel){
    for (idx i=0; i<n ; i++)
      res[sel[i]] = col1[sel[i]] || (keys[sel[i]] != base[pos[sel[i]]]) ;
  } else {
    /* sel == NULL, omitted  */
  }
}

ht_lookup_initial(idx n, idx* pos, idx* match, idx* H, idx* B) {
  /* H: hash 值，其实就是哈希数组 B 的索引值 */
  for (idx i=0, k=0; i<n; i++) {
    // saving found chain head position in HT
    pos[i] = B[H[i]] ;
    // saving to a sel.vector if non−zero
    if (pos[i]) {
      match[k++] = i;
    }
  }
}

ht_lookup_next (idx n, idx* pos, idx* match, idx* next ) {
  for (idx i=0, k=0; i<n ; i++) {
    // advancing to next in chain
    pos[match[i]] = next[pos[match[i]]];
    // saving to a sel.vec. if non−empty
    if (pos[match[i]]) {
      match[k++] = match[i]; 
    }
  }
}
/* ----------------------------------------------------*/
procedure HTprobe(V, B[0..N − 1],K1..k(in),R1..v(out))

// Iterative hash-number computation
H <- map_hash(K1)
for each remaining key vectors Ki do
  H <- map_rehash(H , Ki)  
H <- map_bitwise_and(H, N−1)
  
// Initial lookup of candidate matches
Pos,Match <- ht_lookup_initial(H,B)
while Match not empty do
  // Candidate value verification
  Check <- map_check(K1, V_key1, Pos, Match)
  for each remaining key vector Ki do
    Check <- map_recheck(Check, Ki, Vkeyi, Pos, Match)
    Match <- sel_nonzero(Check,Match)
    // Chain following
    Pos, Match <- ht_lookup_next(Pos,Match, Vnext)

Hits <- sel_nonzero(Pos)
// Fetching the non-key attributes
for each result vector Ri do
  Ri <- map_fetch(Pos, Vvaluei, Hits)
```

**Vectorized Hash Probing**. For space reasons we only discuss the probe phase in Algorithm 4, we show code for the DSM data representation and we focus on the scenario when there is at most one hit for each probe tuple (as is common with relations joined with a foreign-key referential constraint). Probing starts by vectorized computation of a hash number from a key in a column-by-column fashion using map-primitives. A `map_hash_T_col` primitive first hashes each key of type T onto a lng long integer. If the key is composite, we iteratively refine the hash value using a `map_rehash_lng_col_T_col` primitive, passing in the previously computed hash values and the next key column. A bitwise-and map-primitive is used to compute a bucket number from the hash values: H&(N-1).

> **向量化哈希探测**。由于篇幅的原因，我们只讨论算法 4 中的探测阶段，我们展示的代码使用 DSM 布局的数据，并将重点放在每个探测元组最多命中一次的场景上（这对于用外键连接的关系（ 特别是有引用约束）很常见）。探测开始于使用 **map 原语**以逐列的方式，向量化计算 Key 的哈希值。 `map_hash_T_col` 原语首先将 T 类型的每个 Key 哈希到一个 `lng` 长整数。 如果是复合键，则传入先前计算的哈希值和下一个键列，使用 `map_rehash_lng_col_T_col` 原语依次细化每个哈希值，**bitwise-and 的 map 原语**用于从哈希值计算桶号：`H&(N-1)`。

To read the positions of heads of chains for the calculated buckets we use a special primitive `ht_lookup_initial`. It behaves like a selection primitive, creating a selection vector *Match* of positions in the bucket number vector *H* for which a match was found. Also, it fills the *Pos* vector with positions of the candidate matching tuples in the hash table. If the value (offset) in the bucket is 0, there is no key in the hash table – these tuples store 0 in *Pos* and are not part of *Match*.

> 为了读取计算出的==**桶**==的链头位置，我们使用了一个特殊的原语 `ht_lookup_initial`。其行为就像一个**选择原语**，在**桶编号**向量 *H* 中找到匹配的位置，并创建一个选择向量 *Match*。此外，它用哈希表中**候选匹配元组**的位置填充 *Pos* 向量。如果桶中的值（偏移量）为 0，则表示哈希表中没有该键 —— 这些元组在 *Pos* 中存储 0，不是 *Match* 的一部分。

Having identified the indices of possible matching tuples, the next task is to “check” if the key values actually match. This is implemented using a specialized map primitive that combines fetching a value by offset with testing for nonequality: `map_check`. Similar to hashing, composite keys are supported using a `map_recheck` primitive which gets the boolean output of the previous check as an extra first parameter. The resulting booleans mark positions for which the check failed. The positions can then be selected using a `select sel_nonzero` primitive, overwriting the selection vector *Match* with positions for which probing should advance to the “next”position in the chain. Advancing is done by a special primitive `ht_lookup_next`, which for each probe tuple in *Match* fills *Pos* with the next position in the bucket-chain of *V* . It also guards for ends of chain by reducing *Match* to its subset for which the resulting position in *Pos* was non-zero.

> 在确定了可能匹配元组的索引之后，下一个任务是“检查”键值是否真正匹配。这用到一个专门的 `map` 原语：`map_check`，它**<u>将通过偏移量获取值</u>**与<u>**测试不相等性**</u>相结合。 与哈希类似，使用 `map_recheck` 原语支持复合键，该原语将<u>前面检查的布尔输出</u>作为额外的第一个参数。结果布尔值标记检查失败的位置。然后可以使用 `select sel_nonzero` 原语选择位置，如果需要前进到 <u>hash 链</u>中的“下一个”位置，则覆盖选择向量 *Match* 中对应的位置。这由一个特殊的原语 `ht_lookup_next` 完成，对于 *Match* 中每个探测元组，它用 *V* 的桶链中的下一个位置填充 *Pos*。它还通过将 *Match* 减少到其在 *Pos* 中的结果位置不为零的子集来保护链的末端。
>

The loop finishes when the *Match* selection vector becomes empty, either because of reaching end of chain (element in *Pos* equals 0, a miss) or because checking succeeded (element in *Pos* pointing to a position in *V* , a hit).

Hits can be found by selecting the elements of *Pos* which ultimately produced a match with a `sel_nonzero` primitive. *Pos* with selection vector *Hits* becomes a pivot vector for fetching. This pivot is subsequently used to fetch (non-key) result values from the build value area into the hash join result vectors; using one fetch primitive for each result column.

**Partial Compilation**. There are three opportunities to apply compilation to vectorized hashing. The first is to compile the full sequence of hash/rehash/bitwise-and and bucket fetch into a single primitive. The second combines the check and iterative re-check (in case of composite keys) and the select > 0 into a single select-primitive. Since the test for a key in a well-tuned hash table has a selectivity around 75%, we can restrict ourselves to a non-branching implementation. These two opportunities re-iterate the compilation benefits of Project and Select primitives, as discussed in the previous sections, so we omit the code.

The third opportunity is in the fetch code. Here, we can generate a composite fetch primitive that, given a vector of positions, fetches multiple columns. The main benefit of this is obtained in case of NSM organisation of the value space V . Vectorization fetches values column-at-a-time, hence passes over the NSM value space as many times as there are result columns (here: 3), accessing random positions from the value space on each pass. On efficient vector-sizes, the amount of random accesses is surely larger than TLB cache size and may even exceed the amount of cache lines, such that TLB and cache trashing occurs, with pages and cache lines from the previous pass being already evicted from the caches before the next. The compiled fetch fetches all columns from the same position at once, achieving more data locality. Figure 4 shows that in normal vectorized hashing performance of NSM and DSM is similar, but compilation makes NSM clearly superior.

---

我们最后一个**==微观基准==**涉及哈希连接：
```SQL
SELECT
  build.col1, build.col2, build.col3 
WHERE 
  probe.key1 = build.key1 AND 
  probe.key2 = build.key2 
FROM probe, build
```
我们关注包含两个（整数）列的 equi-join ，因为这样的复合键对于向量化执行器更具挑战性。此讨论假设简单的 **bucket 链接**，在 VectorWise 中使用，如图 3 所示。这意味着 Key 在大小为 N 的数组 B 上散列，N 是 2 的幂。每个 bucket 包含**==值空间==** V 中一个元组的偏移量。可以使用 DSM 或 NSM 布局来自组织值空间；VectorWise 同时支持两者[14]。<u>它包含==构建关系==的值，以及实现==桶链的下一个偏移量==</u>。 由于哈希冲突，或者由于构建关系中有多个具有相同键的元组，bucket 的链长度可能大于1。

```c

```

## 5. CONCLUSIONS

For database architects seeking a way to increase the computational performance of a database engine, there might seem to be a choice between vectorizing the expression engine versus introducing expression compilation. Vectorization is a form of block-oriented processing, and if a system already has an operator API that is tuple-at-a-time, there will be many changes needed beyond expression calculation, notably in all query operators as well as in the storage layer. If high computational performance is the goal, such deep changes cannot be avoided, as we have shown that if one would keep adhering to a tuple-a-time operator API, expression compilation alone only provides marginal improvement.

Our main message is that one does not need to choose between compilation and vectorization, as we show that best results are obtained if the two are combined. As to what this combining entails, we have shown that ”loop-compilation” techniques as have been proposed recently can be inferior to plain vectorization, due to better (i) SIMD alignment, (ii) ability to avoid branch mispredictions and (iii) parallel memory accesses. Thus, in such cases, compilation should better be split in multiple loops, materializing intermediate vectorized results. <u>**Also, we have signaled cases where an interpreted (but vectorized) evaluation strategy provides optimization opportunities which are very hard with compilation, like dynamic selection of a predicate evaluation method or predicate evaluation order**</u>.

Thus, a simple compilation strategy is not enough; **state-of-the art algorithmic methods** may use certain complex transformations of the problem at hand, sometimes require run-time adaptivity, and always benefit from careful tuning. To reach the same level of **sophistication**, compilation based query engines would require significant added complexity, possibly even higher than that of interpreted engines. Also, it shows that vectorized execution, which is an evolution of the iterator model, thanks to enhancing it with compilation further evolves into an even more efficient and more flexible solution without making dramatic changes to the DBMS architecture. It obtains very good performance while maintaining clear modularization, simplified testing and easy performance and quality tracking, which are key properties of a software product.

> 对于寻求提高数据库引擎计算性能的数据库架构师来说，似乎可以在**向量化表达式引擎**和**编译表达式**之间进行选择。向量化是一种面向块的处理形式，如果系统已经有了一次处理一个元组的运算符 API，那么除了表达式计算之外，还需要进行许多更改，特别是在所有查询运算符和存储层中。==如果以高计算性能为目标，那么无法避免这种深刻的变化，正如我们所展示的那样，如果坚持使用一次处理一个元组的 API，那么表达式编译就只能提供很小的改进==。
>
> 主要结论是，不需要在编译和矢量化之间进行选择，因为我们表明，如果将两者结合起来，将获得最佳结果。<u>关于这种结合的必要性</u>，我们已证明，==普通的向量化技术==可能比最新提出的**循环编译技术**好，因为它具有更好的（i）SIMD对齐，（ii）避免分支预测失误的能力，以及（iii）并行内存访问。因此，在这种情况下，最好将编译分成多个循环，以实现中间结果向量化。此外，我们还指出了这样的情况：解释的（但向量化的）计算策略有优化机会，比如动态选择谓词求值方法或谓词求值顺序，而编译的非常困难。
>
> > 关于将编译分成多个循环，参考[这](https://mp.weixin.qq.com/s?__biz=MzA5MTc0NTMwNQ==&mid=2650721240&idx=1&sn=f4563cb395f53d607f5fdb89f0e1650f&chksm=887dd6aebf0a5fb883dba19081697f834de1fdd8cee4198feeb1064e614e019e982fe0ccca71&mpshare=1&scene=1&srcid=07063AHZxV0iMp1P8a9a21cv&sharer_sharetime=1594037164322&sharer_shareid=863efd09892d5f2daa80a01f22ecb2ed&version=3.0.27.2279&platform=mac&rd2werd=1#wechat_redirect)
>
> 因此，简单的编译策略并不够。最先进的算法方法可能需要对手头的问题进行某些复杂的转换，有时需要运行时自适应性，并且总是可以从仔细调整中获益。为了达到同样的复杂程度，基于编译的查询引擎需要显著增加复杂性，甚至可能比解释引擎还要复杂。此外，还表明向量化执行是迭代器模型的演进，这要归功于通过编译增强了迭代器模型，而无需对 DBMS  架构进行重大更改，它就可以进一步发展成为一种更高效，更灵活的解决方案。它在保持清晰的模块化，简化的测试以及轻松的性能和质量跟踪（这些是软件产品的关键特性）的同时获得了非常好的性能。

