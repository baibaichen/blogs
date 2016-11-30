## HBase Scan流程分析

HBase的读流程目前看来比较复杂，主要由于：

- HBase的表数据分为多个层次,HRegion->HStore->[HFile,HFile,...,MemStore]
- RegionServer的LSM-Like存储引擎，不断flush产生新的HFile，同时产生新的MemStore用于后续数据写入，并且为了防止由于HFile过多而导致Scan时需要扫描的文件过多而导致的性能下降，后台线程会适时的进行Compaction，Compaction的过程会产生新的HFile，并且会删除Compact完成的HFile
- 具体实现中的各种优化，比如[lazy seek优化](https://issues.apache.org/jira/browse/HBASE-4465)，导致代码比较复杂

读流程中充斥着各种Scanner，如下图：

```
                                 +--------------+
                                 |              |
                     +-----------+ RegionScanner+----------+
                     |           +------+-------+          |
                     |                  |                  |
                     |                  |                  |
               +-----v+-------+  +------v-------+   +------v+------+
               |              |  |              |   |              |
               | StoreScanner |  | StoreScanner |   | StoreScanner |
               |              |  |              |   |              |
               +--------------+  +--+---+-----+-+   +--------------+
                                    |   |     |
            +-----------------------+   |     +----------+
            |                           |                |
            |                           |                |
    +-------v---------+   +-------------v----+ +---------v------+
    |                 |   |                  | |                |
    |StoreFileScanner |   | StoreFileScanner | | MemStoreScanner|
    |                 |   |                  | |                |
    +-------+---------+   +--------+---------+ +-------+--------+
            |                      |                   |
            |                      |                   |
            |                      |                   |
            |                      |                   |
    +-------v---------+   +--------v---------+ +-------v--------+
    |                 |   |                  | |                |
    |  HFileScanner   |   |  HFileScanner    | | HFileScanner   |
    |                 |   |                  | |                |
    +-----------------+   +------------------+ +----------------+
```

**在HBase中，一张表可以有多个Column Family，在一次Scan的流程中，每个Column Family(后续叫Store)的数据读取由一个StoreScanner对象负责**。每个`Store`的数据由一个内存中的`MemStore`和磁盘上的`HFile`文件组成，相对应的，`StoreScanner`对象<u>雇佣</u>一个`MemStoreScanner`和N个`StoreFileScanner`来进行实际的数据读取。

从逻辑上看，读取一行的数据需要

1. 按照顺序读取出每个`Store`，也就是每个**Column Family**。
2. 对于每个`Store`，合并`Store`下面的相关的`HFile`和内存中的`MemStore`

实现上，这两步都是通过堆完成。`RegionScanner`的读取通过下面的多个`StoreScanner`组成的堆
完成，使用`RegionScanner`的成员变量`KeyValueHeap` `storeHeap`表示

组成`StoreScanner`的多个**Scanner**在`RegionScannerImpl`构造函数中获得：

```java
for (Map.Entry<byte[], NavigableSet<byte[]>> entry :
          scan.getFamilyMap().entrySet()) {
        Store store = stores.get(entry.getKey());
        // 实际是StoreScanner类型
        KeyValueScanner scanner = store.getScanner(scan, entry.getValue(), this.readPt);
        if (this.filter == null || !scan.doLoadColumnFamiliesOnDemand()
          || this.filter.isFamilyEssential(entry.getKey())) {
          scanners.add(scanner);
        } else {
          joinedScanners.add(scanner);
        }
}
```

`store.getScanner(scan, entry.getValue(), this.readPt)`内部就是new 一个`StoreScanner`，逻辑都在`StoreScanner`的构造函数中

构造函数内部其实就是找到相关的HFile和MemStore，然后建堆。注意，这个堆是StoreScanner级别的，一个StoreScanner一个堆，堆中的元素就是底下包含的HFile和MemStore对应的StoreFileScanner和MemStoreScanner
得到相关的HFile和MemStore逻辑在StoreScanner::getScannersNoCompaction()中，内部会根据请求指定的TimeRange,KeyRange过滤掉不需要的HFile，同时也会利用**bloom filter**过滤掉不需要的HFIle.接着，调用

```
seekScanners(scanners, matcher.getStartKey(), explicitColumnQuery && lazySeekEnabledGlobally,
        isParallelSeekEnabled);
```

对这些StoreFileScanner和MemStoreScanner分别进行seek，seekKey是matcher.getStartKey()，
如下构造

```
 return new KeyValue(row, family, null, HConstants.LATEST_TIMESTAMP,
        Type.DeleteFamily);
```

### Seek语义

seek是针对KeyValue的，seek的语义是seek到指定KeyValue，如果指定KeyValue不存在，则seek到指定KeyValue的下一
个。举例来说，假设名为X的column family里有两列a和b，文件中有两行rowkey分别为aaa和
bbb，如下表所示.

|        | Column Family X |          |
| ------ | --------------- | -------- |
| rowkey | column a        | column b |
| aaa    | 1               | abc      |
| bbb    | 2               | def      |

HBase客户端设置scan请求的start key为aaa，那么matcher.getStartKey()会被初始化为(rowkey, family, qualifier,timestamp,type)=(aaa,X,null,LATEST_TIMESTAMP,Type.DeleteFamily)，根据KeyValue的比较原则，这个KeyValue比aaa行的第一个列a更
小(因为没有qualifier)，所以对这个StoreFileScanner seek时，会seek到aaa这行的第一列a

实际上

```
seekScanners(scanners, matcher.getStartKey(), explicitColumnQuery && lazySeekEnabledGlobally,
        isParallelSeekEnabled);
```

有可能不会对StoreFileScanner进行实际的seek，而是进行lazy seek，seek的工作放到不得不做的时候。后续会专门说lazy seek

上面得到了请求scan涉及到的所有的column family对应的StoreScanner，随后调用如下函数进行建堆:

```java
     protected void initializeKVHeap(List<KeyValueScanner> scanners,
        List<KeyValueScanner> joinedScanners, HRegion region)
        throws IOException {
      this.storeHeap = new KeyValueHeap(scanners, region.comparator);
      if (!joinedScanners.isEmpty()) {
        this.joinedHeap = new KeyValueHeap(joinedScanners, region.comparator);
      }
    }
```

KeyValueScanner是一个接口，表示一个可以向外迭代出KeyValue
的Scanner，StoreFileScanner,MemStoreScanner和StoreScanner都实现了该接口。这里的comparator类型为KVScannerComparator，用于比较两个KeyValueScanner，实际上内部使用了KVComparator，它是用来比较两个KeyValue的。从后面可以看出，实际上，这个由KeyValueScanner组成的堆，堆顶KeyValueScanner满足的特征是： 它的堆顶(KeyValue)最小

堆用类KeyValueHeap表示,看KeyValueHeap构造函数做了什么

```java
    KeyValueHeap(List<? extends KeyValueScanner> scanners,
      KVScannerComparator comparator) throws IOException {
    this.comparator = comparator;
        if (!scanners.isEmpty()) {
          // 根据传入的KeyValueScanner构造出一个优先级队列(内部实现就是堆)
          this.heap = new PriorityQueue<KeyValueScanner>(scanners.size(),
              this.comparator);
          for (KeyValueScanner scanner : scanners) {
            if (scanner.peek() != null) {
              this.heap.add(scanner);
            } else {
              scanner.close();
            }
          }
        //以上将元素加入堆中
        // 从堆顶pop出一个KeyValueScanner放入成员变量current,那么这个堆的堆顶
        // 就是current这个KeyValueScanner的堆顶，KeyValueHeap的peek()取堆顶
        // 操作直接返回current.peek()
          this.current = pollRealKV();
        }
    }
```

在看pollRealKV()怎么做的之前需要先看看HBase 0.94引入的Lazy Seek

### [Lazy Seek优化](https://issues.apache.org/jira/browse/HBASE-4465)

在这个优化之前，读取一个column family(Store)，需要seek其下的所有HFile和MemStore到指定的查询KeyValue(seek的语义为如果KeyValue存在则seek到对应位置，如果不存在，则seek到这个KeyValue的后一个KeyValue，假设Store下有3个HFile和一个MemStore，按照时序递增记为[HFile1, HFile2, HFile3, MemStore],在lazy seek优化之前，需要对所有的HFile和MemStore进行seek，对HFile文件的seek比较慢，往往需要将HFile相应的block加载到内存，然后定位。在有了lazy seek优化之后，如果需要的KeyValue在HFile3中就存在，那么HFIle1和HFile2都不需要进行seek，大大提高速度。大体来说，思路是请求seek某个KeyValue时实际上没有对StoreFileScanner进行真正的seek，而是对于每个StoreFileScanner，设置它的peek为(rowkey,family,qualifier,lastTimestampInStoreFile)

KeyValueHeap有两个重要的接口，peek()和next()，他们都是返回堆顶，区别在于next()会将堆顶出堆，然后重新调整堆，对外来说就是迭代器向前移动，而peek()不会将堆顶出堆，堆顶不变。实现中，
peek()操作非常简单，只需要调用堆的成员变量current的peek()方法操作即可.拿StoreScanner堆举例，current要么是StoreFileScanner类型要么是MemStore，那么到底current是如何选择出来的以及Lazy Seek是如何实现的?

下面举个例子说明。

##### 前提：

HBase开启了Lazy Seek优化(实际上默认开启)

##### 假设：

Store下有三个HFile和MemStore，按照时间顺序记作[HFile1,HFile2,HFile3,MemStore],seek KeyValue为(rowkey,family,qualifier,timestamp)，记作seekKV.
并且它只在HFile3中存在，不在其他HFile和MemStore中存在

##### Lazy Seek过程

seekScanner()的逻辑，如果是lazy seek，则对于每个Scanner都调
用requestSeek(seekKV)方法，方法内部首先进行rowcol类型的bloom filter过滤

1. 如果结果判定seekKV在StoreFile中肯定不存在，则直接设置StoreFileScanner的peek(实际上StoreFileScanner不是一个
   堆只是为了统一代码)为 kv.createLastOnRowCol()，并且将realSeekDone设置true，表示实际的seek完成.

   ```
   public KeyValue createLastOnRowCol() {
   return new KeyValue(
       bytes, getRowOffset(), getRowLength(),
       bytes, getFamilyOffset(), getFamilyLength(),
       bytes, getQualifierOffset(), getQualifierLength(),
       HConstants.OLDEST_TIMESTAMP, Type.Minimum, null, 0, 0);
     }
   ```

   可以看出ts设置为最小，说明这个KeyValue排在所有的同rowkey同column family同qualifier的KeyValue最后。显然，当上层StoreScanner取堆顶时，
   如果其它StoreFileScanner/MemStoreScanner中存在同rowkey同column family同qualifier的真实的KeyValue则会优先弹出。

2. 如果seekKV在StoreFile中，那么会执行如下逻辑：

   ```java
    realSeekDone = false;
    long maxTimestampInFile = reader.getMaxTimestamp();
    long seekTimestamp = kv.getTimestamp();
    if (seekTimestamp > maxTimestampInFile) {
    // Create a fake key that is not greater than the real next key.
    // (Lower timestamps correspond to higher KVs.)
    // To understand this better, consider that we are asked to seek
    // to
    // a higher timestamp than the max timestamp in this file. We
    // know that
    // the next point when we have to consider this file again is
    // when we
    // pass the max timestamp of this file (with the same
    // row/column).
    cur = kv.createFirstOnRowColTS(maxTimestampInFile);
     } else {
    enforceSeek();
     }
   ```

   显然，当kv的ts比HFile中最大的ts都更大时，那么这个HFile中显然不存在seekKV，但是可能存在
   相同rowkey,family,qualifier的不同ts的KeyValue,那么这里设置堆顶时要注意，不能把堆顶设置为比当前HFile文件中的可能真实存在的相同rowkey,family,qualifier的KeyValue大，如下：

   ```java
   public KeyValue createFirstOnRowColTS(long ts) {
   return new KeyValue(
       bytes, getRowOffset(), getRowLength(),
       bytes, getFamilyOffset(), getFamilyLength(),
       bytes, getQualifierOffset(), getQualifierLength(),
       ts, Type.Maximum, bytes, getValueOffset(), getValueLength());
     }
   ```

   Type的比较中，Type.Maximum最小，这样产生的KeyValue保证了不会大于当前HFile文件中的可能存在的相同rowkey，family，qualifier的KeyValue，同时将seekKV保存到StoreFileScanner成员变量delayedSeekKV中，以便后续真正seek的时候获取.
   考虑一下如果seekKV的ts比当前HFile中的maxTimestamp更小怎么办?可以设置一个ts为latest_timestamp
   的KeyValue么?如果设置了，它会比其它HFile中存在实际的KeyValue先弹出，这样顺序就乱了,所以这种情况下，只能进行实际的seek，enforceSeek()函数中进行实际的seek后，将realSeekDone设置为
   true.

##### 取StoreScanner堆顶逻辑

因为HFile3的latestTimestampInStoreFile最大，所以会首先取到HFile3对应的StoreFileScanner的pee
k(**KeyValue的比较原则是timestamp大的KeyValue更小**)，
这个时候会检查这个KeyValueScanner是否进行了实际的seek(对于StoreFileScanner来说，通过布尔变量realSeekDone进行标记，对于MemStoreScanner来说，始终返回true)，在这里，没有进行real seek
，接着进行实际的seek操作，seek到HFile3中存在的seekKV，接着拿着seekKV去和HFile2的peek进行比较，显然seekKV比HFile2的peek小(由于timestamp > lastTimestampInStoreFile2),故
StoreScanner的peek操作返回seekKV。

实现中，KeyValueHeap有两个重要的接口，peek()和next()，他们都是返回堆顶，区别在于next()会将堆顶出堆，然后重新调整堆，对外来说就是迭代器向前移动，而peek()不会将堆顶出堆，堆顶不变。实现中，
peek()操作非常简单，只需要调用堆的成员变量current的peek()方法操作即可.拿StoreScanner堆举例，current要么是StoreFileScanner类型要么是MemStore，而current的选择则是pollRealKV()
完成的，这个函数之所以内部有while循环就是因为考虑了Lazy Seek优化，实际上，pollRealKV()代码的逻辑就是例子中"取StoreScanner堆顶逻辑"。pollRealKV()的返回值会赋给current

```java
  protected KeyValueScanner pollRealKV() throws IOException {
    KeyValueScanner kvScanner = heap.poll();
    if (kvScanner == null) {
      return null;
    }

    while (kvScanner != null && !kvScanner.realSeekDone()) {
      if (kvScanner.peek() != null) {
        kvScanner.enforceSeek();
        KeyValue curKV = kvScanner.peek();
        if (curKV != null) {
          KeyValueScanner nextEarliestScanner = heap.peek();
          if (nextEarliestScanner == null) {
            // The heap is empty. Return the only possible scanner.
            return kvScanner;
          }

          // Compare the current scanner to the next scanner. We try to avoid
          // putting the current one back into the heap if possible.
          KeyValue nextKV = nextEarliestScanner.peek();
          if (nextKV == null || comparator.compare(curKV, nextKV) < 0) {
            // We already have the scanner with the earliest KV, so return it.
            return kvScanner;
          }

          // Otherwise, put the scanner back into the heap and let it compete
          // against all other scanners (both those that have done a "real
          // seek" and a "lazy seek").
          heap.add(kvScanner);
        } else {
          // Close the scanner because we did a real seek and found out there
          // are no more KVs.
          kvScanner.close();
        }
      } else {
        // Close the scanner because it has already run out of KVs even before
        // we had to do a real seek on it.
        kvScanner.close();
      }
      kvScanner = heap.poll();
    }

    return kvScanner;
  }
```

### Store下HFile集合发生变化如何处理

内存中的Memstore被flush到文件系统或者compaction完成都会改变Store的HFile文件集合。
在每次做完一批mutate操作后，会通过HRegion::isFlushSize(newSize)检查是否需要对当前HRegion内的memstore进行flush
其实就是判断HRegion内的所有的memstore大小和是否大于hbase.hregion.memstore.flush.size，默认128MB，如果需要flush，会将请求放入后台flush线程(MemStoreFlusher)的队列中，由后台flush线程处理，调用路径HRegion::flushcache()->internalFlushcache(...)－>StoreFlushContext.flushCache(...)->StoreFlushContext.commit(...)=>HStore::updateStorefiles()，这块逻辑在[HBase Snapshot原理和实现](http://www.cnblogs.com/foxmailed/p/3914117.html)中有讲到，这里不赘述。只说一下最后一步的updateStorefiles()操作，该函数主要工作是拿住HStore级别的写锁，然后将新产生的HFile文件插入到StoreEngine中，解写锁，然后释放snapshot，最后调用
notifyChangedReadersObservers()，如下：

```java
 this.lock.writeLock().lock();
 try {
   this.storeEngine.getStoreFileManager().insertNewFiles(sfs);
   this.memstore.clearSnapshot(set);
 } finally {
   // We need the lock, as long as we are updating the storeFiles
   // or changing the memstore. Let us release it before calling
   // notifyChangeReadersObservers. See HBASE-4485 for a possible
   // deadlock scenario that could have happened if continue to hold
   // the lock.
   this.lock.writeLock().unlock();
 }
 // Tell listeners of the change in readers.
 notifyChangedReadersObservers();
```

重点在于notifyChangedReadersObservers()，看看代码：

```
  private void notifyChangedReadersObservers() throws IOException {
    for (ChangedReadersObserver o: this.changedReaderObservers) {
      o.updateReaders();
    }
  }
```

实际上，每个observer类型都是StoreScanner，每次新开一个StoreScanner都会注册在Store内部的这个observer集合中，当Store下面的HFile集合变化时，通知这些注册上来的StoreScanner即可。
具体的通知方式就是首先拿住StoreScanner的锁，将这个时候的堆顶保存在成员变量lastTop中，
然后将StoreScanner内部的堆置为null(this.heap=null)最后解锁，而StoreScanner那边next/seek/reseek时，都会首先通过函数checkReseek()函数来检查是否this.heap为null，为null
，为null说明当前Store下的HFile集合改变了，那么调用resetScannerStack(lastTop)，将当前
Store下的所有StoreFileScanner/MemStoreScanner都seek到lastTop，然后重新建StoreScanner对应的堆。checkReseek()代码如下:

```
  protected boolean checkReseek() throws IOException {
    if (this.heap == null && this.lastTop != null) {
      resetScannerStack(this.lastTop);
      if (this.heap.peek() == null
          || store.getComparator().compareRows(this.lastTop, this.heap.peek()) != 0) {
        LOG.debug("Storescanner.peek() is changed where before = " + this.lastTop.toString()
            + ",and after = " + this.heap.peek());
        this.lastTop = null;
        return true;
      }
      this.lastTop = null; // gone!
    }
    // else dont need to reseek
    return false;
  }
```

#### 参考资料

[https://github.com/apache/hbase/tree/0.98](https://github.com/apache/hbase/tree/0.98)

[https://issues.apache.org/jira/browse/HBASE-4465](https://issues.apache.org/jira/browse/HBASE-4465)

### 相关的代码

#### 接口

1. `InternalScanner`
2. `RegionScanner`
3. ​

