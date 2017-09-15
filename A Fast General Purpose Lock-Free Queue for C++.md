# [A Fast General Purpose Lock-Free Queue for C++](http://moodycamel.com/blog/2014/a-fast-general-purpose-lock-free-queue-for-c++)

So I've been bitten by the lock-free bug! After finishing my [single-producer, single-consumer lock-free queue](http://moodycamel.com/blog/2013/a-fast-lock-free-queue-for-c++), I decided to design and implement a more general multi-producer, multi-consumer queue and see how it stacked up against existing lock-free C++ queues. After over a year of spare-time development and testing, it's finally time for a public release. TL;DR: You can [grab the C++11 implementation from GitHub](https://github.com/cameron314/concurrentqueue) (or [jump to the benchmarks](http://moodycamel.com/blog/2014/a-fast-general-purpose-lock-free-queue-for-c++#benchmarks)).

The way the queue works is interesting, I think, so that's what this blog post is about. A much more detailed and complete (but also more dry) description is available in a [sister blog post](http://moodycamel.com/blog/2014/detailed-design-of-a-lock-free-queue), by the way.

## Sharing data: Oh, the woes

At first glance, a general purpose lock-free queue seems fairly easy to implement. It isn't. The root of the problem is that the same variables necessarily need to be shared with several threads. For example, take a common linked-list based approach: At a minimum, the head and tail of the list need to be shared, because consumers all need to be able to read and update the head, and the producers all need to be able to update the tail.

This doesn't sound too bad so far, but the real problems arise when a thread needs to update more than one variable to keep the queue in a consistent state -- atomicity is only ensured for single variables, and atomicity for compound variables (structs) is almost certainly going to result in a sort of lock (on most platforms, depending on the size of the variable). For example, what if a consumer read the last item from the queue and updated only the head? The tail should not still point to it, because the object will soon be freed! But the consumer could be interrupted by the OS and suspended for a few milliseconds before it updates the tail, and during that time the tail could be updated by another thread, and then it becomes too late for the first thread to set it to null.

The solutions to this fundamental problem of shared data are the crux of lock-free programming. Often the best way is to conceive of an algorithm that doesn't need to update multiple variables to maintain consistency in the first place, or one where incremental updates still leave the data structure in a consistent state. Various tricks can be used, such as never freeing memory once allocated (this helps with reads from threads that aren't up to date), storing extra state in the last two bits of a pointer (this works with 4-byte aligned pointers), and reference counting pointers. But tricks like these only go so far; the real effort goes into developing the algorithms themselves.

## My queue

The less threads fight over the same data, the better. So, instead of using a single data structure that linearizes all operations, a set of sub-queues is used instead -- one for each producer thread. This means that different threads can enqueue items completely in parallel, independently of each other.

Of course, this also makes dequeueing slightly more complicated: Now we have to check every sub-queue for items when dequeuing. Interestingly, it turns out that the order that elements are pulled from the sub-queues really doesn't matter. All elements from a given producer thread will necessarily still be seen in that same order relative to each other when dequeued (since the sub-queue preserves that order), albeit with elements from other sub-queues possibly interleaved. Interleaving elements is OK because even in a traditional single-queue model, the order that elements get put in from from different producer threads is non-deterministic anyway (because there's a race condition between the different producers). [Edit: This is only true if the producers are independent, which isn't necessarily the case. See the comments.] The only downside to this approach is that if the queue is empty, every single sub-queue has to be checked in order to determine this (also, by the time one sub-queue is checked, a previously empty one could have become non-empty -- but in practice this doesn't cause problems). However, in the non-empty case, there is much less contention overall because sub-queues can be "paired up" with consumers. This reduces data sharing to the near-optimal level (where every consumer is matched with exactly one producer), without losing the ability to handle the general case. This pairing is done using a heuristic that takes into account the last sub-queue a producer successfully pulled from (essentially, it gives consumers an affinity). Of course, in order to do this pairing, some state has to be maintained between calls to dequeue -- this is done using consumer-specific "tokens" that the user is in charge of allocating. Note that tokens are completely optional -- the queue merely reverts to searching every sub-queue for an element without one, which is correct, just slightly slower when many threads are involved.

So, that's the high-level design. What about the core algorithm used within each sub-queue? Well, instead of being based on a linked-list of nodes (which implies constantly allocating and freeing or re-using elements, and typically relies on a compare-and-swap loop which can be slow under heavy contention), I based my queue on an array model. Instead of linking individual elements, I have a "block" of several elements. The logical head and tail indices of the queue are represented using atomically-incremented integers. Between these logical indices and the blocks lies a scheme for mapping each index to its block and sub-index within that block. An enqueue operation simply increments the tail (remember that there's only one producer thread for each sub-queue). A dequeue operation increments the head if it sees that the head is less than the tail, and then it checks to see if it accidentally incremented the head past the tail (this can happen under contention -- there's multiple consumer threads per sub-queue). If it did over-increment the head, a correction counter is incremented (making the queue eventually consistent), and if not, it goes ahead and increments another integer which gives it the actual final logical index. The increment of this final index always yields a valid index in the actual queue, regardless of what other threads are doing or have done; this works because the final index is only ever incremented when there's guaranteed to be at least one element to dequeue (which was checked when the first index was incremented).

So there you have it. An enqueue operation is done with a single atomic increment, and a dequeue is done with two atomic increments in the fast-path, and one extra otherwise. (Of course, this is discounting all the block allocation/re-use/referencing counting/block mapping goop, which, while important, is not very interesting -- in any case, most of those costs are amortized over an entire block's worth of elements.) The really interesting part of this design is that it allows extremely efficient bulk operations -- in terms of atomic instructions (which tend to be a bottleneck), enqueueing X items in a block has exactly the same amount of overhead as enqueueing a single item (ditto for dequeueing), provided they're in the same block. That's where the real performance gains come in :-)

## I heard there was code

Since I thought there was rather a lack of high-quality lock-free queues for C++, I wrote one using this design I came up with. (While there are others, notably the ones in Boost and Intel's TBB, mine has more features, such as having no restrictions on the element type, and is faster to boot.) You can find it over at [GitHub](https://github.com/cameron314/concurrentqueue). It's all contained in a single header, and available under the simplified BSD license. Just drop it in your project and enjoy!

## Benchmarks, yay!

So, the fun part of creating data structures is writing synthetic benchmarks and seeing how fast yours is versus other existing ones. For comparison, I used the Boost 1.55 lock-free queue, Intel's TBB 4.3 `concurrent_queue`, another linked-list based lock-free queue of my own (a naïve design for reference), a lock-based queue using `std::mutex`, and a normal `std::queue` (for reference against a regular data structure that's accessed purely from one thread). Note that the graphs below only show a subset of the results, and omit both the naïve lock-free and single-threaded `std::queue` implementations.

Here are the results! Detailed raw data follows the pretty graphs (note that I had to use a **logarithmic scale** due to the enormous differences in absolute throughput).

[![img](http://moodycamel.com/blog/2014/enqueue8.png)](https://plot.ly/~cameron314/18)[![img](http://moodycamel.com/blog/2014/dequeue8.png)](https://plot.ly/~cameron314/20)[![img](http://moodycamel.com/blog/2014/heavy8.png)](https://plot.ly/~cameron314/22)[![img](http://moodycamel.com/blog/2014/enqueue32.png)](https://plot.ly/~cameron314/26)[![img](http://moodycamel.com/blog/2014/dequeue32.png)](https://plot.ly/~cameron314/24)[![img](http://moodycamel.com/blog/2014/heavy32.png)](https://plot.ly/~cameron314/28)

#### 64-bit 8-core AWS instance (c1.xlarge), Ubuntu, g++

```
Running 64-bit benchmarks on an Intel(R) Xeon(R) CPU           E5506  @ 2.13GHz
    (precise mode)
Note that these are synthetic benchmarks. Take them with a grain of salt.

Legend:
    'Avg':     Average time taken per operation, normalized to be per thread
    'Range':   The minimum and maximum times taken per operation (per thread)
    'Ops/s':   Overall operations per second
    'Ops/s/t': Operations per second per thread (inverse of 'Avg')
    Operations include those that fail (e.g. because the queue is empty).
    Each logical enqueue/dequeue counts as an individual operation when in bulk.

balanced:
  (Measures the average operation speed with multiple symmetrical threads
  under reasonable load -- small random intervals between accesses)
  > moodycamel::ConcurrentQueue
      Without tokens
         2   threads:  Avg: 2.1439us  Range: [2.0316us, 2.1834us]  Ops/s: 932.90k  Ops/s/t: 466.45k
         3   threads:  Avg: 3.8096us  Range: [3.3842us, 4.1643us]  Ops/s: 787.49k  Ops/s/t: 262.50k
         4   threads:  Avg: 5.5406us  Range: [5.0081us, 5.6616us]  Ops/s: 721.94k  Ops/s/t: 180.48k
         8   threads:  Avg: 0.0120ms  Range: [0.0118ms, 0.0123ms]  Ops/s: 664.05k  Ops/s/t:  83.01k
         12  threads:  Avg: 0.0189ms  Range: [0.0188ms, 0.0191ms]  Ops/s: 633.72k  Ops/s/t:  52.81k
         16  threads:  Avg: 0.0246ms  Range: [0.0240ms, 0.0248ms]  Ops/s: 650.46k  Ops/s/t:  40.65k
         Operations per second per thread (weighted average): 133.15k

      With tokens
         2   threads:  Avg: 2.0623us  Range: [2.0364us, 2.0712us]  Ops/s: 969.77k  Ops/s/t: 484.88k
         3   threads:  Avg: 3.1724us  Range: [3.1542us, 3.2327us]  Ops/s: 945.67k  Ops/s/t: 315.22k
         4   threads:  Avg: 4.4476us  Range: [4.2467us, 4.6332us]  Ops/s: 899.36k  Ops/s/t: 224.84k
         8   threads:  Avg: 0.0101ms  Range: [9.7889us, 0.0102ms]  Ops/s: 795.01k  Ops/s/t:  99.38k
         12  threads:  Avg: 0.0155ms  Range: [0.0150ms, 0.0158ms]  Ops/s: 773.39k  Ops/s/t:  64.45k
         16  threads:  Avg: 0.0213ms  Range: [0.0206ms, 0.0218ms]  Ops/s: 750.30k  Ops/s/t:  46.89k
         Operations per second per thread (weighted average): 153.72k

  > boost::lockfree::queue
     2   threads:  Avg: 2.3908us  Range: [2.2293us, 2.6284us]  Ops/s: 836.54k  Ops/s/t: 418.27k
     3   threads:  Avg: 4.2701us  Range: [3.5851us, 4.3783us]  Ops/s: 702.55k  Ops/s/t: 234.18k
     4   threads:  Avg: 6.0101us  Range: [5.8873us, 6.0534us]  Ops/s: 665.55k  Ops/s/t: 166.39k
     8   threads:  Avg: 0.0123ms  Range: [0.0120ms, 0.0125ms]  Ops/s: 647.96k  Ops/s/t:  81.00k
     12  threads:  Avg: 0.0191ms  Range: [0.0189ms, 0.0193ms]  Ops/s: 626.99k  Ops/s/t:  52.25k
     16  threads:  Avg: 0.0244ms  Range: [0.0234ms, 0.0248ms]  Ops/s: 656.16k  Ops/s/t:  41.01k
     Operations per second per thread (weighted average): 123.33k

  > tbb::concurrent_queue
     2   threads:  Avg: 2.2272us  Range: [2.1376us, 2.3070us]  Ops/s: 898.01k  Ops/s/t: 449.00k
     3   threads:  Avg: 4.0825us  Range: [4.0605us, 4.1017us]  Ops/s: 734.84k  Ops/s/t: 244.95k
     4   threads:  Avg: 5.4065us  Range: [5.3347us, 5.4531us]  Ops/s: 739.86k  Ops/s/t: 184.96k
     8   threads:  Avg: 0.0115ms  Range: [0.0112ms, 0.0116ms]  Ops/s: 692.97k  Ops/s/t:  86.62k
     12  threads:  Avg: 0.0171ms  Range: [0.0169ms, 0.0172ms]  Ops/s: 702.47k  Ops/s/t:  58.54k
     16  threads:  Avg: 0.0228ms  Range: [0.0225ms, 0.0232ms]  Ops/s: 701.16k  Ops/s/t:  43.82k
     Operations per second per thread (weighted average): 132.93k

  > SimpleLockFreeQueue
     2   threads:  Avg: 2.2096us  Range: [2.2034us, 2.2123us]  Ops/s: 905.15k  Ops/s/t: 452.58k
     3   threads:  Avg: 3.9077us  Range: [3.5631us, 4.2803us]  Ops/s: 767.71k  Ops/s/t: 255.90k
     4   threads:  Avg: 5.8294us  Range: [5.7747us, 5.8686us]  Ops/s: 686.17k  Ops/s/t: 171.54k
     8   threads:  Avg: 0.0125ms  Range: [0.0121ms, 0.0129ms]  Ops/s: 641.39k  Ops/s/t:  80.17k
     12  threads:  Avg: 0.0187ms  Range: [0.0184ms, 0.0189ms]  Ops/s: 640.76k  Ops/s/t:  53.40k
     16  threads:  Avg: 0.0255ms  Range: [0.0244ms, 0.0262ms]  Ops/s: 626.32k  Ops/s/t:  39.15k
     Operations per second per thread (weighted average): 129.20k

  > LockBasedQueue
     2   threads:  Avg: 2.6149us  Range: [2.2393us, 2.8581us]  Ops/s: 764.85k  Ops/s/t: 382.43k
     3   threads:  Avg: 5.0743us  Range: [4.9853us, 5.0976us]  Ops/s: 591.21k  Ops/s/t: 197.07k
     4   threads:  Avg: 7.6434us  Range: [7.3726us, 7.7969us]  Ops/s: 523.33k  Ops/s/t: 130.83k
     8   threads:  Avg: 0.0302ms  Range: [0.0269ms, 0.0328ms]  Ops/s: 264.68k  Ops/s/t:  33.09k
     12  threads:  Avg: 0.0637ms  Range: [0.0509ms, 0.0682ms]  Ops/s: 188.37k  Ops/s/t:  15.70k
     16  threads:  Avg: 0.1120ms  Range: [0.1001ms, 0.1184ms]  Ops/s: 142.90k  Ops/s/t:   8.93k
     Operations per second per thread (weighted average):  85.99k

  > std::queue
     (skipping, benchmark not supported...)

only enqueue:
  (Measures the average operation speed when all threads are producers)
  > moodycamel::ConcurrentQueue
      Without tokens
         1    thread:  Avg: 0.0229us  Range: [0.0229us, 0.0230us]  Ops/s:  43.58M  Ops/s/t:  43.58M
         2   threads:  Avg: 0.0418us  Range: [0.0416us, 0.0419us]  Ops/s:  47.84M  Ops/s/t:  23.92M
         4   threads:  Avg: 0.0821us  Range: [0.0821us, 0.0822us]  Ops/s:  48.71M  Ops/s/t:  12.18M
         8   threads:  Avg: 0.1621us  Range: [0.1616us, 0.1625us]  Ops/s:  49.35M  Ops/s/t:   6.17M
         12  threads:  Avg: 0.2503us  Range: [0.2498us, 0.2507us]  Ops/s:  47.94M  Ops/s/t:   3.99M
         16  threads:  Avg: 0.3417us  Range: [0.3406us, 0.3428us]  Ops/s:  46.82M  Ops/s/t:   2.93M
         Operations per second per thread (weighted average):  12.03M

      With tokens
         1    thread:  Avg: 0.0146us  Range: [0.0146us, 0.0146us]  Ops/s:  68.45M  Ops/s/t:  68.45M
         2   threads:  Avg: 0.0315us  Range: [0.0313us, 0.0316us]  Ops/s:  63.53M  Ops/s/t:  31.77M
         4   threads:  Avg: 0.0614us  Range: [0.0613us, 0.0615us]  Ops/s:  65.10M  Ops/s/t:  16.27M
         8   threads:  Avg: 0.1220us  Range: [0.1216us, 0.1222us]  Ops/s:  65.56M  Ops/s/t:   8.20M
         12  threads:  Avg: 0.1901us  Range: [0.1884us, 0.1909us]  Ops/s:  63.14M  Ops/s/t:   5.26M
         16  threads:  Avg: 0.2694us  Range: [0.2658us, 0.2733us]  Ops/s:  59.38M  Ops/s/t:   3.71M
         Operations per second per thread (weighted average):  16.96M

  > boost::lockfree::queue
     1    thread:  Avg: 0.0745us  Range: [0.0744us, 0.0745us]  Ops/s:  13.43M  Ops/s/t:  13.43M
     2   threads:  Avg: 0.7351us  Range: [0.6376us, 0.8720us]  Ops/s:   2.72M  Ops/s/t:   1.36M
     4   threads:  Avg: 5.9754us  Range: [5.5307us, 6.1937us]  Ops/s: 669.41k  Ops/s/t: 167.35k
     8   threads:  Avg: 0.0276ms  Range: [0.0263ms, 0.0282ms]  Ops/s: 289.71k  Ops/s/t:  36.21k
     12  threads:  Avg: 0.0486ms  Range: [0.0222ms, 0.0555ms]  Ops/s: 246.97k  Ops/s/t:  20.58k
     16  threads:  Avg: 0.1059ms  Range: [0.1018ms, 0.1092ms]  Ops/s: 151.02k  Ops/s/t:   9.44k
     Operations per second per thread (weighted average):   1.45M

  > tbb::concurrent_queue
     1    thread:  Avg: 0.0443us  Range: [0.0442us, 0.0443us]  Ops/s:  22.60M  Ops/s/t:  22.60M
     2   threads:  Avg: 0.3436us  Range: [0.3082us, 0.3696us]  Ops/s:   5.82M  Ops/s/t:   2.91M
     4   threads:  Avg: 1.6788us  Range: [1.6172us, 1.7061us]  Ops/s:   2.38M  Ops/s/t: 595.67k
     8   threads:  Avg: 8.5452us  Range: [8.4615us, 8.6459us]  Ops/s: 936.20k  Ops/s/t: 117.03k
     12  threads:  Avg: 0.0475ms  Range: [0.0469ms, 0.0482ms]  Ops/s: 252.43k  Ops/s/t:  21.04k
     16  threads:  Avg: 0.1398ms  Range: [0.1317ms, 0.1445ms]  Ops/s: 114.48k  Ops/s/t:   7.16k
     Operations per second per thread (weighted average):   2.61M

  > SimpleLockFreeQueue
     1    thread:  Avg: 0.0739us  Range: [0.0738us, 0.0739us]  Ops/s:  13.54M  Ops/s/t:  13.54M
     2   threads:  Avg: 0.4404us  Range: [0.4023us, 0.4634us]  Ops/s:   4.54M  Ops/s/t:   2.27M
     4   threads:  Avg: 3.1988us  Range: [3.1510us, 3.2464us]  Ops/s:   1.25M  Ops/s/t: 312.62k
     8   threads:  Avg: 0.0103ms  Range: [0.0102ms, 0.0105ms]  Ops/s: 774.38k  Ops/s/t:  96.80k
     12  threads:  Avg: 0.0215ms  Range: [0.0212ms, 0.0216ms]  Ops/s: 557.77k  Ops/s/t:  46.48k
     16  threads:  Avg: 0.0401ms  Range: [0.0394ms, 0.0407ms]  Ops/s: 398.68k  Ops/s/t:  24.92k
     Operations per second per thread (weighted average):   1.62M

  > LockBasedQueue
     1    thread:  Avg: 0.0696us  Range: [0.0696us, 0.0696us]  Ops/s:  14.36M  Ops/s/t:  14.36M
     2   threads:  Avg: 0.8199us  Range: [0.6931us, 0.8818us]  Ops/s:   2.44M  Ops/s/t:   1.22M
     4   threads:  Avg: 5.0074us  Range: [4.9400us, 5.0589us]  Ops/s: 798.82k  Ops/s/t: 199.70k
     8   threads:  Avg: 0.0233ms  Range: [0.0220ms, 0.0240ms]  Ops/s: 342.66k  Ops/s/t:  42.83k
     12  threads:  Avg: 0.0427ms  Range: [0.0218ms, 0.0492ms]  Ops/s: 281.23k  Ops/s/t:  23.44k
     16  threads:  Avg: 0.0845ms  Range: [0.0782ms, 0.0872ms]  Ops/s: 189.45k  Ops/s/t:  11.84k
     Operations per second per thread (weighted average):   1.53M

  > std::queue
     1    thread:  Avg: 0.0109us  Range: [0.0108us, 0.0109us]  Ops/s:  92.13M  Ops/s/t:  92.13M
     Operations per second per thread (weighted average):  92.13M

only enqueue (pre-allocated):
  (Measures the average operation speed when all threads are producers,
  and the queue has been stretched out first)
  > moodycamel::ConcurrentQueue
      Without tokens
         1    thread:  Avg: 0.0175us  Range: [0.0175us, 0.0176us]  Ops/s:  56.98M  Ops/s/t:  56.98M
         2   threads:  Avg: 0.0343us  Range: [0.0337us, 0.0368us]  Ops/s:  58.31M  Ops/s/t:  29.15M
         4   threads:  Avg: 0.1314us  Range: [0.1059us, 0.1515us]  Ops/s:  30.43M  Ops/s/t:   7.61M
         8   threads:  Avg: 0.5502us  Range: [0.4973us, 0.5676us]  Ops/s:  14.54M  Ops/s/t:   1.82M
         Operations per second per thread (weighted average):  16.37M

      With tokens
         1    thread:  Avg: 8.0792ns  Range: [8.0657ns, 8.0857ns]  Ops/s: 123.77M  Ops/s/t: 123.77M
         2   threads:  Avg: 0.0199us  Range: [0.0198us, 0.0200us]  Ops/s: 100.56M  Ops/s/t:  50.28M
         4   threads:  Avg: 0.0362us  Range: [0.0361us, 0.0363us]  Ops/s: 110.40M  Ops/s/t:  27.60M
         8   threads:  Avg: 0.0730us  Range: [0.0724us, 0.0734us]  Ops/s: 109.61M  Ops/s/t:  13.70M
         Operations per second per thread (weighted average):  39.88M

  > boost::lockfree::queue
     1    thread:  Avg: 0.0477us  Range: [0.0476us, 0.0478us]  Ops/s:  20.96M  Ops/s/t:  20.96M
     2   threads:  Avg: 0.7644us  Range: [0.6611us, 0.8702us]  Ops/s:   2.62M  Ops/s/t:   1.31M
     4   threads:  Avg: 6.5308us  Range: [6.0638us, 6.9217us]  Ops/s: 612.48k  Ops/s/t: 153.12k
     8   threads:  Avg: 0.0276ms  Range: [0.0192ms, 0.0292ms]  Ops/s: 290.08k  Ops/s/t:  36.26k
     Operations per second per thread (weighted average):   3.21M

  > tbb::concurrent_queue
     1    thread:  Avg: 0.0440us  Range: [0.0439us, 0.0440us]  Ops/s:  22.75M  Ops/s/t:  22.75M
     2   threads:  Avg: 0.2923us  Range: [0.2799us, 0.3120us]  Ops/s:   6.84M  Ops/s/t:   3.42M
     4   threads:  Avg: 1.5782us  Range: [1.4991us, 1.6202us]  Ops/s:   2.53M  Ops/s/t: 633.63k
     8   threads:  Avg: 8.5111us  Range: [8.4543us, 8.6016us]  Ops/s: 939.95k  Ops/s/t: 117.49k
     Operations per second per thread (weighted average):   4.03M

  > SimpleLockFreeQueue
     1    thread:  Avg: 0.0563us  Range: [0.0563us, 0.0564us]  Ops/s:  17.76M  Ops/s/t:  17.76M
     2   threads:  Avg: 0.6085us  Range: [0.6031us, 0.6223us]  Ops/s:   3.29M  Ops/s/t:   1.64M
     4   threads:  Avg: 4.8504us  Range: [3.0550us, 5.2267us]  Ops/s: 824.68k  Ops/s/t: 206.17k
     8   threads:  Avg: 0.0211ms  Range: [0.0205ms, 0.0215ms]  Ops/s: 378.88k  Ops/s/t:  47.36k
     Operations per second per thread (weighted average):   2.85M

  > LockBasedQueue
     1    thread:  Avg: 0.0698us  Range: [0.0698us, 0.0698us]  Ops/s:  14.33M  Ops/s/t:  14.33M
     2   threads:  Avg: 0.7667us  Range: [0.7002us, 0.8237us]  Ops/s:   2.61M  Ops/s/t:   1.30M
     4   threads:  Avg: 5.0945us  Range: [4.9227us, 5.1724us]  Ops/s: 785.17k  Ops/s/t: 196.29k
     8   threads:  Avg: 0.0233ms  Range: [0.0224ms, 0.0241ms]  Ops/s: 343.59k  Ops/s/t:  42.95k
     Operations per second per thread (weighted average):   2.30M

  > std::queue
     1    thread:  Avg: 0.0107us  Range: [0.0107us, 0.0107us]  Ops/s:  93.28M  Ops/s/t:  93.28M
     Operations per second per thread (weighted average):  93.28M

only enqueue bulk:
  (Measures the average speed of enqueueing an item in bulk when all threads are producers)
  > moodycamel::ConcurrentQueue
      Without tokens
         1    thread:  Avg: 8.7326ns  Range: [8.7251ns, 8.7431ns]  Ops/s: 114.51M  Ops/s/t: 114.51M
         2   threads:  Avg: 0.0174us  Range: [0.0171us, 0.0176us]  Ops/s: 114.97M  Ops/s/t:  57.49M
         4   threads:  Avg: 0.0346us  Range: [0.0342us, 0.0347us]  Ops/s: 115.60M  Ops/s/t:  28.90M
         8   threads:  Avg: 0.0669us  Range: [0.0605us, 0.0692us]  Ops/s: 119.51M  Ops/s/t:  14.94M
         12  threads:  Avg: 0.1146us  Range: [0.1120us, 0.1169us]  Ops/s: 104.72M  Ops/s/t:   8.73M
         16  threads:  Avg: 0.1741us  Range: [0.1700us, 0.1769us]  Ops/s:  91.92M  Ops/s/t:   5.75M
         Operations per second per thread (weighted average):  28.44M

      With tokens
         1    thread:  Avg: 8.7317ns  Range: [8.7133ns, 8.7451ns]  Ops/s: 114.53M  Ops/s/t: 114.53M
         2   threads:  Avg: 0.0186us  Range: [0.0184us, 0.0187us]  Ops/s: 107.81M  Ops/s/t:  53.91M
         4   threads:  Avg: 0.0382us  Range: [0.0378us, 0.0383us]  Ops/s: 104.74M  Ops/s/t:  26.19M
         8   threads:  Avg: 0.0793us  Range: [0.0790us, 0.0796us]  Ops/s: 100.83M  Ops/s/t:  12.60M
         12  threads:  Avg: 0.1229us  Range: [0.1217us, 0.1232us]  Ops/s:  97.64M  Ops/s/t:   8.14M
         16  threads:  Avg: 0.1745us  Range: [0.1677us, 0.1802us]  Ops/s:  91.69M  Ops/s/t:   5.73M
         Operations per second per thread (weighted average):  27.74M

  > boost::lockfree::queue
     (skipping, benchmark not supported...)

  > tbb::concurrent_queue
     (skipping, benchmark not supported...)

  > SimpleLockFreeQueue
     (skipping, benchmark not supported...)

  > LockBasedQueue
     (skipping, benchmark not supported...)

  > std::queue
     (skipping, benchmark not supported...)

only enqueue bulk (pre-allocated):
  (Measures the average speed of enqueueing an item in bulk when all threads are producers,
  and the queue has been stretched out first)
  > moodycamel::ConcurrentQueue
      Without tokens
         1    thread:  Avg: 9.0071ns  Range: [8.9924ns, 9.0178ns]  Ops/s: 111.02M  Ops/s/t: 111.02M
         2   threads:  Avg: 0.0187us  Range: [0.0184us, 0.0188us]  Ops/s: 106.79M  Ops/s/t:  53.40M
         4   threads:  Avg: 0.0395us  Range: [0.0393us, 0.0397us]  Ops/s: 101.25M  Ops/s/t:  25.31M
         8   threads:  Avg: 0.0831us  Range: [0.0823us, 0.0835us]  Ops/s:  96.28M  Ops/s/t:  12.04M
         Operations per second per thread (weighted average):  37.45M

      With tokens
         1    thread:  Avg: 8.7146ns  Range: [8.7099ns, 8.7187ns]  Ops/s: 114.75M  Ops/s/t: 114.75M
         2   threads:  Avg: 0.0185us  Range: [0.0181us, 0.0187us]  Ops/s: 108.27M  Ops/s/t:  54.14M
         4   threads:  Avg: 0.0383us  Range: [0.0376us, 0.0384us]  Ops/s: 104.54M  Ops/s/t:  26.13M
         8   threads:  Avg: 0.0798us  Range: [0.0794us, 0.0801us]  Ops/s: 100.25M  Ops/s/t:  12.53M
         Operations per second per thread (weighted average):  38.52M

  > boost::lockfree::queue
     (skipping, benchmark not supported...)

  > tbb::concurrent_queue
     (skipping, benchmark not supported...)

  > SimpleLockFreeQueue
     (skipping, benchmark not supported...)

  > LockBasedQueue
     (skipping, benchmark not supported...)

  > std::queue
     (skipping, benchmark not supported...)

only dequeue:
  (Measures the average operation speed when all threads are consumers)
  > moodycamel::ConcurrentQueue
      Without tokens
         1    thread:  Avg: 0.0479us  Range: [0.0478us, 0.0479us]  Ops/s:  20.90M  Ops/s/t:  20.90M
         2   threads:  Avg: 0.4099us  Range: [0.3972us, 0.4170us]  Ops/s:   4.88M  Ops/s/t:   2.44M
         4   threads:  Avg: 2.7666us  Range: [2.0147us, 2.8988us]  Ops/s:   1.45M  Ops/s/t: 361.46k
         8   threads:  Avg: 9.0300us  Range: [8.8027us, 9.1078us]  Ops/s: 885.94k  Ops/s/t: 110.74k
         12  threads:  Avg: 0.0179ms  Range: [0.0178ms, 0.0179ms]  Ops/s: 672.15k  Ops/s/t:  56.01k
         16  threads:  Avg: 0.0338ms  Range: [0.0332ms, 0.0340ms]  Ops/s: 473.79k  Ops/s/t:  29.61k
         Operations per second per thread (weighted average):   1.56M

      With tokens
         1    thread:  Avg: 0.0404us  Range: [0.0404us, 0.0404us]  Ops/s:  24.75M  Ops/s/t:  24.75M
         2   threads:  Avg: 0.0827us  Range: [0.0826us, 0.0827us]  Ops/s:  24.18M  Ops/s/t:  12.09M
         4   threads:  Avg: 0.1745us  Range: [0.1738us, 0.1748us]  Ops/s:  22.92M  Ops/s/t:   5.73M
         8   threads:  Avg: 0.3536us  Range: [0.3514us, 0.3559us]  Ops/s:  22.62M  Ops/s/t:   2.83M
         12  threads:  Avg: 0.5554us  Range: [0.5457us, 0.5628us]  Ops/s:  21.60M  Ops/s/t:   1.80M
         16  threads:  Avg: 0.7653us  Range: [0.7439us, 0.7776us]  Ops/s:  20.91M  Ops/s/t:   1.31M
         Operations per second per thread (weighted average):   5.07M

  > boost::lockfree::queue
     1    thread:  Avg: 0.0356us  Range: [0.0355us, 0.0356us]  Ops/s:  28.11M  Ops/s/t:  28.11M
     2   threads:  Avg: 0.4965us  Range: [0.4550us, 0.5404us]  Ops/s:   4.03M  Ops/s/t:   2.01M
     4   threads:  Avg: 4.5768us  Range: [4.4660us, 4.6482us]  Ops/s: 873.96k  Ops/s/t: 218.49k
     8   threads:  Avg: 0.0189ms  Range: [0.0129ms, 0.0203ms]  Ops/s: 423.34k  Ops/s/t:  52.92k
     12  threads:  Avg: 0.0424ms  Range: [0.0411ms, 0.0432ms]  Ops/s: 282.73k  Ops/s/t:  23.56k
     16  threads:  Avg: 0.0767ms  Range: [0.0523ms, 0.0825ms]  Ops/s: 208.48k  Ops/s/t:  13.03k
     Operations per second per thread (weighted average):   1.85M

  > tbb::concurrent_queue
     1    thread:  Avg: 0.0290us  Range: [0.0286us, 0.0292us]  Ops/s:  34.47M  Ops/s/t:  34.47M
     2   threads:  Avg: 0.2343us  Range: [0.2270us, 0.2470us]  Ops/s:   8.54M  Ops/s/t:   4.27M
     4   threads:  Avg: 2.0731us  Range: [1.9837us, 2.1597us]  Ops/s:   1.93M  Ops/s/t: 482.36k
     8   threads:  Avg: 0.0103ms  Range: [9.5712us, 0.0106ms]  Ops/s: 779.17k  Ops/s/t:  97.40k
     12  threads:  Avg: 0.0451ms  Range: [0.0433ms, 0.0480ms]  Ops/s: 266.36k  Ops/s/t:  22.20k
     16  threads:  Avg: 0.1436ms  Range: [0.1360ms, 0.1520ms]  Ops/s: 111.41k  Ops/s/t:   6.96k
     Operations per second per thread (weighted average):   2.51M

  > SimpleLockFreeQueue
     1    thread:  Avg: 0.0275us  Range: [0.0275us, 0.0276us]  Ops/s:  36.31M  Ops/s/t:  36.31M
     2   threads:  Avg: 0.8440us  Range: [0.7192us, 0.8839us]  Ops/s:   2.37M  Ops/s/t:   1.18M
     4   threads:  Avg: 6.3769us  Range: [5.6198us, 6.7313us]  Ops/s: 627.27k  Ops/s/t: 156.82k
     8   threads:  Avg: 0.0292ms  Range: [0.0263ms, 0.0300ms]  Ops/s: 274.34k  Ops/s/t:  34.29k
     12  threads:  Avg: 0.0642ms  Range: [0.0548ms, 0.0662ms]  Ops/s: 186.79k  Ops/s/t:  15.57k
     16  threads:  Avg: 0.1196ms  Range: [0.1172ms, 0.1206ms]  Ops/s: 133.76k  Ops/s/t:   8.36k
     Operations per second per thread (weighted average):   2.22M

  > LockBasedQueue
     1    thread:  Avg: 0.0617us  Range: [0.0617us, 0.0618us]  Ops/s:  16.20M  Ops/s/t:  16.20M
     2   threads:  Avg: 1.4892us  Range: [1.3851us, 1.5315us]  Ops/s:   1.34M  Ops/s/t: 671.48k
     4   threads:  Avg: 8.0076us  Range: [4.8463us, 8.5494us]  Ops/s: 499.53k  Ops/s/t: 124.88k
     8   threads:  Avg: 0.0295ms  Range: [0.0202ms, 0.0314ms]  Ops/s: 271.52k  Ops/s/t:  33.94k
     12  threads:  Avg: 0.0594ms  Range: [0.0364ms, 0.0639ms]  Ops/s: 201.87k  Ops/s/t:  16.82k
     16  threads:  Avg: 0.1049ms  Range: [0.0564ms, 0.1191ms]  Ops/s: 152.58k  Ops/s/t:   9.54k
     Operations per second per thread (weighted average):   1.02M

  > std::queue
     1    thread:  Avg: 3.8947ns  Range: [3.8927ns, 3.8964ns]  Ops/s: 256.76M  Ops/s/t: 256.76M
     Operations per second per thread (weighted average): 256.76M

only dequeue bulk:
  (Measures the average speed of dequeueing an item in bulk when all threads are consumers)
  > moodycamel::ConcurrentQueue
      Without tokens
         1    thread:  Avg: 1.8063ns  Range: [1.8008ns, 1.8111ns]  Ops/s: 553.61M  Ops/s/t: 553.61M
         2   threads:  Avg: 5.3528ns  Range: [5.3256ns, 5.3836ns]  Ops/s: 373.63M  Ops/s/t: 186.82M
         4   threads:  Avg: 0.0278us  Range: [0.0257us, 0.0288us]  Ops/s: 143.65M  Ops/s/t:  35.91M
         8   threads:  Avg: 0.0860us  Range: [0.0839us, 0.0868us]  Ops/s:  93.02M  Ops/s/t:  11.63M
         12  threads:  Avg: 0.1500us  Range: [0.1429us, 0.1535us]  Ops/s:  79.98M  Ops/s/t:   6.66M
         16  threads:  Avg: 0.2670us  Range: [0.2490us, 0.2774us]  Ops/s:  59.92M  Ops/s/t:   3.74M
         Operations per second per thread (weighted average):  63.04M

      With tokens
         1    thread:  Avg: 1.6650ns  Range: [1.6632ns, 1.6664ns]  Ops/s: 600.59M  Ops/s/t: 600.59M
         2   threads:  Avg: 4.6593ns  Range: [4.5874ns, 4.7052ns]  Ops/s: 429.25M  Ops/s/t: 214.62M
         4   threads:  Avg: 0.0159us  Range: [0.0158us, 0.0160us]  Ops/s: 251.62M  Ops/s/t:  62.91M
         8   threads:  Avg: 0.0515us  Range: [0.0498us, 0.0528us]  Ops/s: 155.40M  Ops/s/t:  19.43M
         12  threads:  Avg: 0.0787us  Range: [0.0764us, 0.0798us]  Ops/s: 152.54M  Ops/s/t:  12.71M
         16  threads:  Avg: 0.1374us  Range: [0.1252us, 0.1435us]  Ops/s: 116.47M  Ops/s/t:   7.28M
         Operations per second per thread (weighted average):  79.24M

  > boost::lockfree::queue
     (skipping, benchmark not supported...)

  > tbb::concurrent_queue
     (skipping, benchmark not supported...)

  > SimpleLockFreeQueue
     (skipping, benchmark not supported...)

  > LockBasedQueue
     (skipping, benchmark not supported...)

  > std::queue
     (skipping, benchmark not supported...)

mostly enqueue:
  (Measures the average operation speed when most threads are enqueueing)
  > moodycamel::ConcurrentQueue
      Without tokens
         2   threads:  Avg: 0.0937us  Range: [0.0927us, 0.0963us]  Ops/s:  21.35M  Ops/s/t:  10.68M
         4   threads:  Avg: 0.1862us  Range: [0.1816us, 0.1900us]  Ops/s:  21.48M  Ops/s/t:   5.37M
         8   threads:  Avg: 0.8762us  Range: [0.7998us, 0.9349us]  Ops/s:   9.13M  Ops/s/t:   1.14M
         Operations per second per thread (weighted average):   4.66M

      With tokens
         2   threads:  Avg: 0.0843us  Range: [0.0841us, 0.0847us]  Ops/s:  23.72M  Ops/s/t:  11.86M
         4   threads:  Avg: 0.1088us  Range: [0.1029us, 0.1111us]  Ops/s:  36.75M  Ops/s/t:   9.19M
         8   threads:  Avg: 0.3251us  Range: [0.2290us, 0.4168us]  Ops/s:  24.61M  Ops/s/t:   3.08M
         Operations per second per thread (weighted average):   7.02M

  > boost::lockfree::queue
     2   threads:  Avg: 0.1152us  Range: [0.1118us, 0.1171us]  Ops/s:  17.37M  Ops/s/t:   8.68M
     4   threads:  Avg: 2.5790us  Range: [1.8601us, 3.1117us]  Ops/s:   1.55M  Ops/s/t: 387.75k
     8   threads:  Avg: 0.0170ms  Range: [0.0166ms, 0.0176ms]  Ops/s: 469.45k  Ops/s/t:  58.68k
     Operations per second per thread (weighted average):   2.12M

  > tbb::concurrent_queue
     2   threads:  Avg: 0.3593us  Range: [0.3540us, 0.3657us]  Ops/s:   5.57M  Ops/s/t:   2.78M
     4   threads:  Avg: 1.1523us  Range: [1.1393us, 1.1810us]  Ops/s:   3.47M  Ops/s/t: 867.86k
     8   threads:  Avg: 5.9296us  Range: [5.4979us, 6.1961us]  Ops/s:   1.35M  Ops/s/t: 168.65k
     Operations per second per thread (weighted average): 984.95k

  > SimpleLockFreeQueue
     2   threads:  Avg: 0.2142us  Range: [0.1944us, 0.2233us]  Ops/s:   9.34M  Ops/s/t:   4.67M
     4   threads:  Avg: 2.7030us  Range: [2.4932us, 2.7637us]  Ops/s:   1.48M  Ops/s/t: 369.96k
     8   threads:  Avg: 0.0127ms  Range: [9.8947us, 0.0138ms]  Ops/s: 628.60k  Ops/s/t:  78.58k
     Operations per second per thread (weighted average):   1.21M

  > LockBasedQueue
     2   threads:  Avg: 0.2057us  Range: [0.1952us, 0.2165us]  Ops/s:   9.72M  Ops/s/t:   4.86M
     4   threads:  Avg: 6.1602us  Range: [3.7945us, 7.2017us]  Ops/s: 649.33k  Ops/s/t: 162.33k
     8   threads:  Avg: 0.0375ms  Range: [0.0334ms, 0.0419ms]  Ops/s: 213.17k  Ops/s/t:  26.65k
     Operations per second per thread (weighted average):   1.17M

  > std::queue
     (skipping, benchmark not supported...)

mostly enqueue bulk:
  (Measures the average speed of enqueueing an item in bulk under light contention)
  > moodycamel::ConcurrentQueue
      Without tokens
         2   threads:  Avg: 0.0172us  Range: [0.0172us, 0.0173us]  Ops/s: 116.00M  Ops/s/t:  58.00M
         4   threads:  Avg: 0.0366us  Range: [0.0363us, 0.0369us]  Ops/s: 109.38M  Ops/s/t:  27.34M
         8   threads:  Avg: 0.0797us  Range: [0.0794us, 0.0801us]  Ops/s: 100.37M  Ops/s/t:  12.55M
         Operations per second per thread (weighted average):  27.58M

      With tokens
         2   threads:  Avg: 0.0171us  Range: [0.0171us, 0.0171us]  Ops/s: 117.11M  Ops/s/t:  58.55M
         4   threads:  Avg: 0.0304us  Range: [0.0280us, 0.0320us]  Ops/s: 131.48M  Ops/s/t:  32.87M
         8   threads:  Avg: 0.0714us  Range: [0.0612us, 0.0770us]  Ops/s: 112.07M  Ops/s/t:  14.01M
         Operations per second per thread (weighted average):  30.14M

  > boost::lockfree::queue
     (skipping, benchmark not supported...)

  > tbb::concurrent_queue
     (skipping, benchmark not supported...)

  > SimpleLockFreeQueue
     (skipping, benchmark not supported...)

  > LockBasedQueue
     (skipping, benchmark not supported...)

  > std::queue
     (skipping, benchmark not supported...)

mostly dequeue:
  (Measures the average operation speed when most threads are dequeueing)
  > moodycamel::ConcurrentQueue
      Without tokens
         2   threads:  Avg: 0.1035us  Range: [0.1034us, 0.1036us]  Ops/s:  19.32M  Ops/s/t:   9.66M
         4   threads:  Avg: 1.4781us  Range: [1.4440us, 1.4955us]  Ops/s:   2.71M  Ops/s/t: 676.56k
         8   threads:  Avg: 3.8335us  Range: [2.7691us, 4.1910us]  Ops/s:   2.09M  Ops/s/t: 260.86k
         Operations per second per thread (weighted average):   2.52M

      With tokens
         2   threads:  Avg: 0.0843us  Range: [0.0842us, 0.0844us]  Ops/s:  23.72M  Ops/s/t:  11.86M
         4   threads:  Avg: 0.7221us  Range: [0.6558us, 0.7557us]  Ops/s:   5.54M  Ops/s/t:   1.38M
         8   threads:  Avg: 1.0831us  Range: [0.8750us, 1.2979us]  Ops/s:   7.39M  Ops/s/t: 923.26k
         Operations per second per thread (weighted average):   3.55M

  > boost::lockfree::queue
     2   threads:  Avg: 0.4447us  Range: [0.3836us, 0.4890us]  Ops/s:   4.50M  Ops/s/t:   2.25M
     4   threads:  Avg: 2.3882us  Range: [1.9014us, 2.7969us]  Ops/s:   1.67M  Ops/s/t: 418.73k
     8   threads:  Avg: 0.0132ms  Range: [7.8331us, 0.0142ms]  Ops/s: 604.38k  Ops/s/t:  75.55k
     Operations per second per thread (weighted average): 677.76k

  > tbb::concurrent_queue
     2   threads:  Avg: 0.1613us  Range: [0.1609us, 0.1618us]  Ops/s:  12.40M  Ops/s/t:   6.20M
     4   threads:  Avg: 1.6693us  Range: [1.5484us, 1.7851us]  Ops/s:   2.40M  Ops/s/t: 599.07k
     8   threads:  Avg: 7.8647us  Range: [7.4879us, 8.0231us]  Ops/s:   1.02M  Ops/s/t: 127.15k
     Operations per second per thread (weighted average):   1.65M

  > SimpleLockFreeQueue
     2   threads:  Avg: 0.4576us  Range: [0.4205us, 0.4906us]  Ops/s:   4.37M  Ops/s/t:   2.19M
     4   threads:  Avg: 3.7064us  Range: [3.5114us, 3.8460us]  Ops/s:   1.08M  Ops/s/t: 269.81k
     8   threads:  Avg: 0.0188ms  Range: [0.0186ms, 0.0189ms]  Ops/s: 426.30k  Ops/s/t:  53.29k
     Operations per second per thread (weighted average): 605.60k

  > LockBasedQueue
     2   threads:  Avg: 0.7979us  Range: [0.7404us, 0.8972us]  Ops/s:   2.51M  Ops/s/t:   1.25M
     4   threads:  Avg: 7.7466us  Range: [7.5002us, 7.9109us]  Ops/s: 516.35k  Ops/s/t: 129.09k
     8   threads:  Avg: 0.0258ms  Range: [0.0248ms, 0.0267ms]  Ops/s: 310.04k  Ops/s/t:  38.76k
     Operations per second per thread (weighted average): 342.85k

  > std::queue
     (skipping, benchmark not supported...)

mostly dequeue bulk:
  (Measures the average speed of dequeueing an item in bulk under light contention)
  > moodycamel::ConcurrentQueue
      Without tokens
         2   threads:  Avg: 8.3404ns  Range: [7.7191ns, 8.9024ns]  Ops/s: 239.80M  Ops/s/t: 119.90M
         4   threads:  Avg: 0.0264us  Range: [0.0223us, 0.0274us]  Ops/s: 151.37M  Ops/s/t:  37.84M
         8   threads:  Avg: 0.1042us  Range: [0.0994us, 0.1064us]  Ops/s:  76.75M  Ops/s/t:   9.59M
         Operations per second per thread (weighted average):  43.63M

      With tokens
         2   threads:  Avg: 4.9164ns  Range: [4.8544ns, 5.0084ns]  Ops/s: 406.80M  Ops/s/t: 203.40M
         4   threads:  Avg: 0.0156us  Range: [0.0155us, 0.0158us]  Ops/s: 256.40M  Ops/s/t:  64.10M
         8   threads:  Avg: 0.0518us  Range: [0.0448us, 0.0607us]  Ops/s: 154.57M  Ops/s/t:  19.32M
         Operations per second per thread (weighted average):  75.37M

  > boost::lockfree::queue
     (skipping, benchmark not supported...)

  > tbb::concurrent_queue
     (skipping, benchmark not supported...)

  > SimpleLockFreeQueue
     (skipping, benchmark not supported...)

  > LockBasedQueue
     (skipping, benchmark not supported...)

  > std::queue
     (skipping, benchmark not supported...)

single-producer, multi-consumer (measuring all but 1 thread):
  (Measures the average speed of dequeueing with only one producer, but multiple consumers)
  > moodycamel::ConcurrentQueue
      Without tokens
         2   threads:  Avg: 0.1041us  Range: [0.0978us, 0.1122us]  Ops/s:   9.60M  Ops/s/t:   9.60M
         4   threads:  Avg: 1.9386us  Range: [1.7129us, 2.3281us]  Ops/s:   1.55M  Ops/s/t: 515.83k
         8   threads:  Avg: 0.7837us  Range: [0.5718us, 1.4367us]  Ops/s:   8.93M  Ops/s/t:   1.28M
         16  threads:  Avg: 0.8587us  Range: [0.8096us, 1.0077us]  Ops/s:  17.47M  Ops/s/t:   1.16M
         Operations per second per thread (weighted average):   1.99M

      With tokens
         2   threads:  Avg: 0.1034us  Range: [0.0984us, 0.1079us]  Ops/s:   9.67M  Ops/s/t:   9.67M
         4   threads:  Avg: 1.4904us  Range: [1.3998us, 1.6162us]  Ops/s:   2.01M  Ops/s/t: 670.97k
         8   threads:  Avg: 0.9243us  Range: [0.3801us, 6.2399us]  Ops/s:   7.57M  Ops/s/t:   1.08M
         16  threads:  Avg: 0.7863us  Range: [0.6860us, 0.8744us]  Ops/s:  19.08M  Ops/s/t:   1.27M
         Operations per second per thread (weighted average):   2.01M

  > boost::lockfree::queue
     2   threads:  Avg: 0.1390us  Range: [0.1325us, 0.1545us]  Ops/s:   7.19M  Ops/s/t:   7.19M
     4   threads:  Avg: 0.4625us  Range: [0.2754us, 0.5561us]  Ops/s:   6.49M  Ops/s/t:   2.16M
     8   threads:  Avg: 0.7324us  Range: [0.5028us, 1.2586us]  Ops/s:   9.56M  Ops/s/t:   1.37M
     16  threads:  Avg: 0.4086us  Range: [0.3950us, 0.4355us]  Ops/s:  36.71M  Ops/s/t:   2.45M
     Operations per second per thread (weighted average):   2.60M

  > tbb::concurrent_queue
     2   threads:  Avg: 0.0396us  Range: [0.0395us, 0.0396us]  Ops/s:  25.27M  Ops/s/t:  25.27M
     4   threads:  Avg: 0.4785us  Range: [0.3555us, 2.2952us]  Ops/s:   6.27M  Ops/s/t:   2.09M
     8   threads:  Avg: 6.6917us  Range: [2.8804us, 8.9630us]  Ops/s:   1.05M  Ops/s/t: 149.44k
     16  threads:  Avg: 6.2738us  Range: [4.4906us, 0.0135ms]  Ops/s:   2.39M  Ops/s/t: 159.39k
     Operations per second per thread (weighted average):   3.23M

  > SimpleLockFreeQueue
     2   threads:  Avg: 0.1923us  Range: [0.1744us, 0.2023us]  Ops/s:   5.20M  Ops/s/t:   5.20M
     4   threads:  Avg: 1.5853us  Range: [0.4197us, 2.7908us]  Ops/s:   1.89M  Ops/s/t: 630.79k
     8   threads:  Avg: 1.1113us  Range: [0.8406us, 1.7108us]  Ops/s:   6.30M  Ops/s/t: 899.83k
     16  threads:  Avg: 0.5356us  Range: [0.5155us, 0.5548us]  Ops/s:  28.01M  Ops/s/t:   1.87M
     Operations per second per thread (weighted average):   1.72M

  > LockBasedQueue
     2   threads:  Avg: 0.1914us  Range: [0.1719us, 0.2080us]  Ops/s:   5.22M  Ops/s/t:   5.22M
     4   threads:  Avg: 2.8230us  Range: [2.6594us, 2.9641us]  Ops/s:   1.06M  Ops/s/t: 354.23k
     8   threads:  Avg: 8.1605us  Range: [7.5421us, 9.6067us]  Ops/s: 857.79k  Ops/s/t: 122.54k
     16  threads:  Avg: 0.0330ms  Range: [0.0316ms, 0.0339ms]  Ops/s: 455.18k  Ops/s/t:  30.35k
     Operations per second per thread (weighted average): 678.72k

  > std::queue
     (skipping, benchmark not supported...)

single-producer, multi-consumer (pre-produced):
  (Measures the average speed of dequeueing from a queue pre-filled by one thread)
  > moodycamel::ConcurrentQueue
      Without tokens
         1    thread:  Avg: 0.0479us  Range: [0.0478us, 0.0479us]  Ops/s:  20.89M  Ops/s/t:  20.89M
         3   threads:  Avg: 1.0918us  Range: [0.9990us, 1.1343us]  Ops/s:   2.75M  Ops/s/t: 915.91k
         7   threads:  Avg: 6.1592us  Range: [6.0475us, 6.1973us]  Ops/s:   1.14M  Ops/s/t: 162.36k
         15  threads:  Avg: 0.0243ms  Range: [0.0240ms, 0.0245ms]  Ops/s: 617.10k  Ops/s/t:  41.14k
         Operations per second per thread (weighted average):   2.49M

      With tokens
         1    thread:  Avg: 0.0404us  Range: [0.0404us, 0.0404us]  Ops/s:  24.74M  Ops/s/t:  24.74M
         3   threads:  Avg: 0.9008us  Range: [0.8752us, 0.9113us]  Ops/s:   3.33M  Ops/s/t:   1.11M
         7   threads:  Avg: 4.7648us  Range: [4.7195us, 4.8146us]  Ops/s:   1.47M  Ops/s/t: 209.87k
         15  threads:  Avg: 0.0194ms  Range: [0.0191ms, 0.0196ms]  Ops/s: 772.72k  Ops/s/t:  51.51k
         Operations per second per thread (weighted average):   2.96M

  > boost::lockfree::queue
     1    thread:  Avg: 0.0356us  Range: [0.0355us, 0.0357us]  Ops/s:  28.11M  Ops/s/t:  28.11M
     3   threads:  Avg: 2.1016us  Range: [1.8879us, 2.2375us]  Ops/s:   1.43M  Ops/s/t: 475.82k
     7   threads:  Avg: 0.0161ms  Range: [0.0149ms, 0.0163ms]  Ops/s: 435.41k  Ops/s/t:  62.20k
     15  threads:  Avg: 0.0692ms  Range: [0.0458ms, 0.0738ms]  Ops/s: 216.64k  Ops/s/t:  14.44k
     Operations per second per thread (weighted average):   3.15M

  > tbb::concurrent_queue
     1    thread:  Avg: 0.0286us  Range: [0.0286us, 0.0287us]  Ops/s:  34.91M  Ops/s/t:  34.91M
     3   threads:  Avg: 0.9964us  Range: [0.9581us, 1.0265us]  Ops/s:   3.01M  Ops/s/t:   1.00M
     7   threads:  Avg: 5.4370us  Range: [5.3420us, 5.4884us]  Ops/s:   1.29M  Ops/s/t: 183.93k
     15  threads:  Avg: 0.1103ms  Range: [0.1003ms, 0.1155ms]  Ops/s: 135.94k  Ops/s/t:   9.06k
     Operations per second per thread (weighted average):   4.02M

  > SimpleLockFreeQueue
     1    thread:  Avg: 0.0275us  Range: [0.0275us, 0.0275us]  Ops/s:  36.31M  Ops/s/t:  36.31M
     3   threads:  Avg: 3.3246us  Range: [2.6774us, 3.5321us]  Ops/s: 902.37k  Ops/s/t: 300.79k
     7   threads:  Avg: 0.0204ms  Range: [6.8770us, 0.0235ms]  Ops/s: 342.89k  Ops/s/t:  48.98k
     15  threads:  Avg: 0.0959ms  Range: [0.0412ms, 0.1095ms]  Ops/s: 156.35k  Ops/s/t:  10.42k
     Operations per second per thread (weighted average):   4.00M

  > LockBasedQueue
     1    thread:  Avg: 0.0616us  Range: [0.0615us, 0.0616us]  Ops/s:  16.24M  Ops/s/t:  16.24M
     3   threads:  Avg: 2.7538us  Range: [2.6907us, 2.8152us]  Ops/s:   1.09M  Ops/s/t: 363.14k
     7   threads:  Avg: 0.0215ms  Range: [0.0190ms, 0.0225ms]  Ops/s: 325.43k  Ops/s/t:  46.49k
     15  threads:  Avg: 0.0868ms  Range: [0.0813ms, 0.0919ms]  Ops/s: 172.89k  Ops/s/t:  11.53k
     Operations per second per thread (weighted average):   1.84M

  > std::queue
     (skipping, benchmark not supported...)

multi-producer, single-consumer (measuring 1 thread):
  (Measures the average speed of dequeueing with only one consumer, but multiple producers)
  > moodycamel::ConcurrentQueue
      Without tokens
         2   threads:  Avg: 0.0625us  Range: [0.0624us, 0.0626us]  Ops/s:  15.99M  Ops/s/t:  15.99M
         4   threads:  Avg: 0.0639us  Range: [0.0625us, 0.0644us]  Ops/s:  15.66M  Ops/s/t:  15.66M
         8   threads:  Avg: 0.0671us  Range: [0.0628us, 0.0715us]  Ops/s:  14.89M  Ops/s/t:  14.89M
         16  threads:  Avg: 0.0916us  Range: [0.0869us, 0.0946us]  Ops/s:  10.92M  Ops/s/t:  10.92M
         Operations per second per thread (weighted average):  14.37M

      With tokens
         2   threads:  Avg: 0.0556us  Range: [0.0555us, 0.0557us]  Ops/s:  17.99M  Ops/s/t:  17.99M
         4   threads:  Avg: 0.0454us  Range: [0.0452us, 0.0455us]  Ops/s:  22.03M  Ops/s/t:  22.03M
         8   threads:  Avg: 0.0410us  Range: [0.0410us, 0.0411us]  Ops/s:  24.37M  Ops/s/t:  24.37M
         16  threads:  Avg: 0.0418us  Range: [0.0412us, 0.0421us]  Ops/s:  23.93M  Ops/s/t:  23.93M
         Operations per second per thread (weighted average):  22.08M

  > boost::lockfree::queue
     2   threads:  Avg: 0.0444us  Range: [0.0420us, 0.0516us]  Ops/s:  22.53M  Ops/s/t:  22.53M
     4   threads:  Avg: 0.2854us  Range: [0.2487us, 0.4188us]  Ops/s:   3.50M  Ops/s/t:   3.50M
     8   threads:  Avg: 0.5135us  Range: [0.5043us, 0.5177us]  Ops/s:   1.95M  Ops/s/t:   1.95M
     16  threads:  Avg: 0.5293us  Range: [0.5162us, 0.5350us]  Ops/s:   1.89M  Ops/s/t:   1.89M
     Operations per second per thread (weighted average):   7.47M

  > tbb::concurrent_queue
     2   threads:  Avg: 0.1757us  Range: [0.1333us, 0.2080us]  Ops/s:   5.69M  Ops/s/t:   5.69M
     4   threads:  Avg: 0.1487us  Range: [0.1463us, 0.1507us]  Ops/s:   6.72M  Ops/s/t:   6.72M
     8   threads:  Avg: 0.1561us  Range: [0.1542us, 0.1565us]  Ops/s:   6.41M  Ops/s/t:   6.41M
     16  threads:  Avg: 0.6278us  Range: [0.5630us, 0.6839us]  Ops/s:   1.59M  Ops/s/t:   1.59M
     Operations per second per thread (weighted average):   5.10M

  > SimpleLockFreeQueue
     2   threads:  Avg: 0.0464us  Range: [0.0392us, 0.0543us]  Ops/s:  21.55M  Ops/s/t:  21.55M
     4   threads:  Avg: 0.4172us  Range: [0.3421us, 0.4385us]  Ops/s:   2.40M  Ops/s/t:   2.40M
     8   threads:  Avg: 0.2543us  Range: [0.2397us, 0.2625us]  Ops/s:   3.93M  Ops/s/t:   3.93M
     16  threads:  Avg: 0.2392us  Range: [0.2348us, 0.2422us]  Ops/s:   4.18M  Ops/s/t:   4.18M
     Operations per second per thread (weighted average):   8.01M

  > LockBasedQueue
     2   threads:  Avg: 0.1546us  Range: [0.1125us, 0.1981us]  Ops/s:   6.47M  Ops/s/t:   6.47M
     4   threads:  Avg: 0.6662us  Range: [0.5840us, 0.7603us]  Ops/s:   1.50M  Ops/s/t:   1.50M
     8   threads:  Avg: 0.7431us  Range: [0.6830us, 0.7976us]  Ops/s:   1.35M  Ops/s/t:   1.35M
     16  threads:  Avg: 0.7052us  Range: [0.6063us, 0.7579us]  Ops/s:   1.42M  Ops/s/t:   1.42M
     Operations per second per thread (weighted average):   2.68M

  > std::queue
     (skipping, benchmark not supported...)

dequeue from empty:
  (Measures the average speed of attempting to dequeue from an empty queue
  (that eight separate threads had at one point enqueued to))
  > moodycamel::ConcurrentQueue
      Without tokens
         1    thread:  Avg: 0.0409us  Range: [0.0406us, 0.0416us]  Ops/s:  24.45M  Ops/s/t:  24.45M
                ^ Note: No contention -- measures raw failed dequeue speed on empty queue
         2   threads:  Avg: 0.0905us  Range: [0.0825us, 0.0962us]  Ops/s:  22.10M  Ops/s/t:  11.05M
         8   threads:  Avg: 0.3867us  Range: [0.3854us, 0.3899us]  Ops/s:  20.69M  Ops/s/t:   2.59M
         Operations per second per thread (weighted average):   9.04M

      With tokens
         1    thread:  Avg: 0.0237us  Range: [0.0200us, 0.0249us]  Ops/s:  42.21M  Ops/s/t:  42.21M
                ^ Note: No contention -- measures raw failed dequeue speed on empty queue
         2   threads:  Avg: 0.0524us  Range: [0.0500us, 0.0539us]  Ops/s:  38.19M  Ops/s/t:  19.09M
         8   threads:  Avg: 0.2339us  Range: [0.1980us, 0.2642us]  Ops/s:  34.21M  Ops/s/t:   4.28M
         Operations per second per thread (weighted average):  15.51M

  > boost::lockfree::queue
     1    thread:  Avg: 6.5831ns  Range: [6.5815ns, 6.5847ns]  Ops/s: 151.90M  Ops/s/t: 151.90M
            ^ Note: No contention -- measures raw failed dequeue speed on empty queue
     2   threads:  Avg: 0.0122us  Range: [0.0122us, 0.0122us]  Ops/s: 163.52M  Ops/s/t:  81.76M
     8   threads:  Avg: 0.0488us  Range: [0.0488us, 0.0489us]  Ops/s: 163.91M  Ops/s/t:  20.49M
     Operations per second per thread (weighted average):  62.08M

  > tbb::concurrent_queue
     1    thread:  Avg: 5.1712ns  Range: [5.1697ns, 5.1721ns]  Ops/s: 193.38M  Ops/s/t: 193.38M
            ^ Note: No contention -- measures raw failed dequeue speed on empty queue
     2   threads:  Avg: 0.0104us  Range: [0.0103us, 0.0104us]  Ops/s: 193.18M  Ops/s/t:  96.59M
     8   threads:  Avg: 0.0413us  Range: [0.0413us, 0.0413us]  Ops/s: 193.79M  Ops/s/t:  24.22M
     Operations per second per thread (weighted average):  76.01M

  > SimpleLockFreeQueue
     1    thread:  Avg: 7.0505ns  Range: [7.0486ns, 7.0525ns]  Ops/s: 141.83M  Ops/s/t: 141.83M
            ^ Note: No contention -- measures raw failed dequeue speed on empty queue
     2   threads:  Avg: 0.0160us  Range: [0.0160us, 0.0160us]  Ops/s: 125.02M  Ops/s/t:  62.51M
     8   threads:  Avg: 0.0638us  Range: [0.0638us, 0.0638us]  Ops/s: 125.33M  Ops/s/t:  15.67M
     Operations per second per thread (weighted average):  52.37M

  > LockBasedQueue
     1    thread:  Avg: 0.0291us  Range: [0.0291us, 0.0291us]  Ops/s:  34.32M  Ops/s/t:  34.32M
            ^ Note: No contention -- measures raw failed dequeue speed on empty queue
     2   threads:  Avg: 0.2276us  Range: [0.1948us, 0.2441us]  Ops/s:   8.79M  Ops/s/t:   4.39M
     8   threads:  Avg: 5.2936us  Range: [2.8883us, 6.2920us]  Ops/s:   1.51M  Ops/s/t: 188.91k
     Operations per second per thread (weighted average):   7.83M

  > std::queue
     1    thread:  Avg: 0.9395ns  Range: [0.9392ns, 0.9396ns]  Ops/s:   1.06G  Ops/s/t:   1.06G
            ^ Note: No contention -- measures raw failed dequeue speed on empty queue
     Operations per second per thread (weighted average):   1.06G

enqueue-dequeue pairs:
  (Measures the average operation speed with each thread doing an enqueue
  followed by a dequeue)
  > moodycamel::ConcurrentQueue
      Without tokens
         1    thread:  Avg: 0.0292us  Range: [0.0292us, 0.0292us]  Ops/s:  34.23M  Ops/s/t:  34.23M
                ^ Note: No contention -- measures speed of immediately dequeueing the item that was just enqueued
         2   threads:  Avg: 0.2037us  Range: [0.1940us, 0.2153us]  Ops/s:   9.82M  Ops/s/t:   4.91M
         4   threads:  Avg: 1.7694us  Range: [1.7308us, 1.7856us]  Ops/s:   2.26M  Ops/s/t: 565.17k
         8   threads:  Avg: 6.5195us  Range: [6.3181us, 6.6066us]  Ops/s:   1.23M  Ops/s/t: 153.39k
         Operations per second per thread (weighted average):   5.90M

      With tokens
         1    thread:  Avg: 0.0226us  Range: [0.0226us, 0.0226us]  Ops/s:  44.19M  Ops/s/t:  44.19M
                ^ Note: No contention -- measures speed of immediately dequeueing the item that was just enqueued
         2   threads:  Avg: 0.0487us  Range: [0.0485us, 0.0488us]  Ops/s:  41.10M  Ops/s/t:  20.55M
         4   threads:  Avg: 0.1205us  Range: [0.1195us, 0.1214us]  Ops/s:  33.20M  Ops/s/t:   8.30M
         8   threads:  Avg: 0.3268us  Range: [0.3128us, 0.3348us]  Ops/s:  24.48M  Ops/s/t:   3.06M
         Operations per second per thread (weighted average):  13.60M

  > boost::lockfree::queue
     1    thread:  Avg: 0.0437us  Range: [0.0436us, 0.0437us]  Ops/s:  22.90M  Ops/s/t:  22.90M
            ^ Note: No contention -- measures speed of immediately dequeueing the item that was just enqueued
     2   threads:  Avg: 0.3969us  Range: [0.3245us, 0.4216us]  Ops/s:   5.04M  Ops/s/t:   2.52M
     4   threads:  Avg: 3.3639us  Range: [3.2929us, 3.4045us]  Ops/s:   1.19M  Ops/s/t: 297.27k
     8   threads:  Avg: 0.0146ms  Range: [0.0139ms, 0.0148ms]  Ops/s: 548.28k  Ops/s/t:  68.54k
     Operations per second per thread (weighted average):   3.76M

  > tbb::concurrent_queue
     1    thread:  Avg: 0.0288us  Range: [0.0287us, 0.0289us]  Ops/s:  34.72M  Ops/s/t:  34.72M
            ^ Note: No contention -- measures speed of immediately dequeueing the item that was just enqueued
     2   threads:  Avg: 0.3518us  Range: [0.3293us, 0.3811us]  Ops/s:   5.69M  Ops/s/t:   2.84M
     4   threads:  Avg: 2.4316us  Range: [2.3749us, 2.5254us]  Ops/s:   1.64M  Ops/s/t: 411.24k
     8   threads:  Avg: 0.0119ms  Range: [0.0109ms, 0.0127ms]  Ops/s: 670.40k  Ops/s/t:  83.80k
     Operations per second per thread (weighted average):   5.49M

  > SimpleLockFreeQueue
     1    thread:  Avg: 0.0477us  Range: [0.0476us, 0.0477us]  Ops/s:  20.99M  Ops/s/t:  20.99M
            ^ Note: No contention -- measures speed of immediately dequeueing the item that was just enqueued
     2   threads:  Avg: 0.4059us  Range: [0.3067us, 0.4286us]  Ops/s:   4.93M  Ops/s/t:   2.46M
     4   threads:  Avg: 3.6382us  Range: [3.5906us, 3.6695us]  Ops/s:   1.10M  Ops/s/t: 274.86k
     8   threads:  Avg: 0.0152ms  Range: [0.0140ms, 0.0157ms]  Ops/s: 526.95k  Ops/s/t:  65.87k
     Operations per second per thread (weighted average):   3.48M

  > LockBasedQueue
     1    thread:  Avg: 0.0646us  Range: [0.0646us, 0.0646us]  Ops/s:  15.48M  Ops/s/t:  15.48M
            ^ Note: No contention -- measures speed of immediately dequeueing the item that was just enqueued
     2   threads:  Avg: 0.6880us  Range: [0.6445us, 0.7316us]  Ops/s:   2.91M  Ops/s/t:   1.45M
     4   threads:  Avg: 6.0035us  Range: [5.9227us, 6.0622us]  Ops/s: 666.28k  Ops/s/t: 166.57k
     8   threads:  Avg: 0.0200ms  Range: [0.0106ms, 0.0247ms]  Ops/s: 400.14k  Ops/s/t:  50.02k
     Operations per second per thread (weighted average):   2.49M

  > std::queue
     1    thread:  Avg: 4.4183ns  Range: [4.4094ns, 4.4228ns]  Ops/s: 226.33M  Ops/s/t: 226.33M
            ^ Note: No contention -- measures speed of immediately dequeueing the item that was just enqueued
     Operations per second per thread (weighted average): 226.33M

heavy concurrent:
  (Measures the average operation speed with many threads under heavy load)
  > moodycamel::ConcurrentQueue
      Without tokens
         2   threads:  Avg: 0.2189us  Range: [0.2185us, 0.2194us]  Ops/s:   9.14M  Ops/s/t:   4.57M
         3   threads:  Avg: 0.3700us  Range: [0.3134us, 0.4042us]  Ops/s:   8.11M  Ops/s/t:   2.70M
         4   threads:  Avg: 1.1026us  Range: [1.0439us, 1.1272us]  Ops/s:   3.63M  Ops/s/t: 906.94k
         8   threads:  Avg: 3.1887us  Range: [3.0584us, 3.2292us]  Ops/s:   2.51M  Ops/s/t: 313.61k
         12  threads:  Avg: 6.0866us  Range: [5.9009us, 6.2746us]  Ops/s:   1.97M  Ops/s/t: 164.30k
         16  threads:  Avg: 9.9339us  Range: [9.5659us, 0.0102ms]  Ops/s:   1.61M  Ops/s/t: 100.67k
         Operations per second per thread (weighted average): 959.54k

      With tokens
         2   threads:  Avg: 0.0502us  Range: [0.0475us, 0.0523us]  Ops/s:  39.86M  Ops/s/t:  19.93M
         3   threads:  Avg: 0.1121us  Range: [0.1088us, 0.1140us]  Ops/s:  26.76M  Ops/s/t:   8.92M
         4   threads:  Avg: 0.4054us  Range: [0.3912us, 0.4140us]  Ops/s:   9.87M  Ops/s/t:   2.47M
         8   threads:  Avg: 0.7169us  Range: [0.5758us, 0.8319us]  Ops/s:  11.16M  Ops/s/t:   1.39M
         12  threads:  Avg: 1.2992us  Range: [1.0963us, 1.5459us]  Ops/s:   9.24M  Ops/s/t: 769.70k
         16  threads:  Avg: 1.8166us  Range: [1.4060us, 1.9616us]  Ops/s:   8.81M  Ops/s/t: 550.47k
         Operations per second per thread (weighted average):   3.72M

  > boost::lockfree::queue
     2   threads:  Avg: 0.3558us  Range: [0.3235us, 0.3937us]  Ops/s:   5.62M  Ops/s/t:   2.81M
     3   threads:  Avg: 1.7224us  Range: [1.5520us, 1.8241us]  Ops/s:   1.74M  Ops/s/t: 580.60k
     4   threads:  Avg: 2.2439us  Range: [1.8891us, 2.4835us]  Ops/s:   1.78M  Ops/s/t: 445.65k
     8   threads:  Avg: 8.8162us  Range: [8.5410us, 9.0435us]  Ops/s: 907.42k  Ops/s/t: 113.43k
     12  threads:  Avg: 0.0184ms  Range: [0.0179ms, 0.0187ms]  Ops/s: 653.30k  Ops/s/t:  54.44k
     16  threads:  Avg: 0.0289ms  Range: [0.0236ms, 0.0301ms]  Ops/s: 554.55k  Ops/s/t:  34.66k
     Operations per second per thread (weighted average): 422.31k

  > tbb::concurrent_queue
     2   threads:  Avg: 0.3223us  Range: [0.3021us, 0.3596us]  Ops/s:   6.20M  Ops/s/t:   3.10M
     3   threads:  Avg: 0.8995us  Range: [0.8900us, 0.9129us]  Ops/s:   3.34M  Ops/s/t:   1.11M
     4   threads:  Avg: 1.7776us  Range: [1.6889us, 1.8523us]  Ops/s:   2.25M  Ops/s/t: 562.55k
     8   threads:  Avg: 6.1223us  Range: [5.5484us, 6.5143us]  Ops/s:   1.31M  Ops/s/t: 163.34k
     12  threads:  Avg: 0.0175ms  Range: [0.0137ms, 0.0198ms]  Ops/s: 686.07k  Ops/s/t:  57.17k
     16  threads:  Avg: 0.0461ms  Range: [0.0382ms, 0.0519ms]  Ops/s: 347.11k  Ops/s/t:  21.69k
     Operations per second per thread (weighted average): 530.15k

  > SimpleLockFreeQueue
     2   threads:  Avg: 0.4770us  Range: [0.3797us, 0.5594us]  Ops/s:   4.19M  Ops/s/t:   2.10M
     3   threads:  Avg: 1.4174us  Range: [1.1771us, 1.6083us]  Ops/s:   2.12M  Ops/s/t: 705.51k
     4   threads:  Avg: 3.1814us  Range: [2.8453us, 3.4126us]  Ops/s:   1.26M  Ops/s/t: 314.33k
     8   threads:  Avg: 0.0115ms  Range: [7.8236us, 0.0142ms]  Ops/s: 693.66k  Ops/s/t:  86.71k
     12  threads:  Avg: 0.0136ms  Range: [9.8911us, 0.0154ms]  Ops/s: 879.37k  Ops/s/t:  73.28k
     16  threads:  Avg: 0.0194ms  Range: [0.0178ms, 0.0210ms]  Ops/s: 823.59k  Ops/s/t:  51.47k
     Operations per second per thread (weighted average): 357.56k

  > LockBasedQueue
     2   threads:  Avg: 0.5884us  Range: [0.5593us, 0.6079us]  Ops/s:   3.40M  Ops/s/t:   1.70M
     3   threads:  Avg: 2.7292us  Range: [2.5104us, 2.8136us]  Ops/s:   1.10M  Ops/s/t: 366.41k
     4   threads:  Avg: 6.0285us  Range: [5.7452us, 6.1933us]  Ops/s: 663.52k  Ops/s/t: 165.88k
     8   threads:  Avg: 0.0233ms  Range: [8.6898us, 0.0286ms]  Ops/s: 342.92k  Ops/s/t:  42.87k
     12  threads:  Avg: 0.0594ms  Range: [0.0545ms, 0.0609ms]  Ops/s: 202.13k  Ops/s/t:  16.84k
     16  threads:  Avg: 0.1016ms  Range: [0.0870ms, 0.1082ms]  Ops/s: 157.46k  Ops/s/t:   9.84k
     Operations per second per thread (weighted average): 232.46k

  > std::queue
     (skipping, benchmark not supported...)

Overall average operations per second per thread (where higher-concurrency runs have more weight):
(Take this summary with a grain of salt -- look at the individual benchmark results for a much
better idea of how the queues measure up to each other):
    moodycamel::ConcurrentQueue (including bulk):  19.04M
    boost::lockfree::queue:   4.07M
    tbb::concurrent_queue:   5.09M
    SimpleLockFreeQueue:   3.76M
    LockBasedQueue:   1.36M
    std::queue (single thread only): 297.43M
```

#### 64-bit 32-core AWS instance (cc2.8xlarge), Ubuntu, g++

```
Running 64-bit benchmarks on a        Intel(R) Xeon(R) CPU E5-2670 0 @ 2.60GHz
    (precise mode)
Note that these are synthetic benchmarks. Take them with a grain of salt.

Legend:
    'Avg':     Average time taken per operation, normalized to be per thread
    'Range':   The minimum and maximum times taken per operation (per thread)
    'Ops/s':   Overall operations per second
    'Ops/s/t': Operations per second per thread (inverse of 'Avg')
    Operations include those that fail (e.g. because the queue is empty).
    Each logical enqueue/dequeue counts as an individual operation when in bulk.

balanced:
  (Measures the average operation speed with multiple symmetrical threads
  under reasonable load -- small random intervals between accesses)
  > moodycamel::ConcurrentQueue
      Without tokens
         2   threads:  Avg: 0.9490us  Range: [0.9444us, 0.9512us]  Ops/s:   2.11M  Ops/s/t:   1.05M
         3   threads:  Avg: 1.6217us  Range: [1.6120us, 1.6288us]  Ops/s:   1.85M  Ops/s/t: 616.63k
         4   threads:  Avg: 2.5471us  Range: [2.5150us, 2.5578us]  Ops/s:   1.57M  Ops/s/t: 392.60k
         8   threads:  Avg: 8.7667us  Range: [8.7260us, 8.8109us]  Ops/s: 912.54k  Ops/s/t: 114.07k
         12  threads:  Avg: 0.0194ms  Range: [0.0186ms, 0.0196ms]  Ops/s: 618.06k  Ops/s/t:  51.51k
         16  threads:  Avg: 0.0347ms  Range: [0.0343ms, 0.0348ms]  Ops/s: 461.73k  Ops/s/t:  28.86k
         32  threads:  Avg: 0.1103ms  Range: [0.1095ms, 0.1110ms]  Ops/s: 290.14k  Ops/s/t:   9.07k
         Operations per second per thread (weighted average): 190.15k

      With tokens
         2   threads:  Avg: 0.6626us  Range: [0.6322us, 0.6736us]  Ops/s:   3.02M  Ops/s/t:   1.51M
         3   threads:  Avg: 1.1325us  Range: [1.1000us, 1.1511us]  Ops/s:   2.65M  Ops/s/t: 882.97k
         4   threads:  Avg: 1.8496us  Range: [1.8032us, 1.8770us]  Ops/s:   2.16M  Ops/s/t: 540.65k
         8   threads:  Avg: 6.9275us  Range: [6.8699us, 6.9555us]  Ops/s:   1.15M  Ops/s/t: 144.35k
         12  threads:  Avg: 0.0152ms  Range: [0.0150ms, 0.0153ms]  Ops/s: 791.01k  Ops/s/t:  65.92k
         16  threads:  Avg: 0.0252ms  Range: [0.0248ms, 0.0254ms]  Ops/s: 634.43k  Ops/s/t:  39.65k
         32  threads:  Avg: 0.0634ms  Range: [0.0629ms, 0.0638ms]  Ops/s: 504.94k  Ops/s/t:  15.78k
         Operations per second per thread (weighted average): 266.86k

  > boost::lockfree::queue
     2   threads:  Avg: 1.0256us  Range: [0.6384us, 1.0845us]  Ops/s:   1.95M  Ops/s/t: 975.07k
     3   threads:  Avg: 1.8734us  Range: [1.8230us, 1.8918us]  Ops/s:   1.60M  Ops/s/t: 533.80k
     4   threads:  Avg: 2.6625us  Range: [2.4000us, 2.8020us]  Ops/s:   1.50M  Ops/s/t: 375.59k
     8   threads:  Avg: 0.0138ms  Range: [0.0130ms, 0.0144ms]  Ops/s: 577.73k  Ops/s/t:  72.22k
     12  threads:  Avg: 0.0471ms  Range: [0.0434ms, 0.0487ms]  Ops/s: 254.73k  Ops/s/t:  21.23k
     16  threads:  Avg: 0.1145ms  Range: [0.1138ms, 0.1154ms]  Ops/s: 139.74k  Ops/s/t:   8.73k
     32  threads:  Avg: 0.4516ms  Range: [0.4458ms, 0.4532ms]  Ops/s:  70.85k  Ops/s/t:   2.21k
     Operations per second per thread (weighted average): 160.22k

  > tbb::concurrent_queue
     2   threads:  Avg: 1.0053us  Range: [0.9950us, 1.0112us]  Ops/s:   1.99M  Ops/s/t: 994.72k
     3   threads:  Avg: 1.5741us  Range: [1.5690us, 1.5797us]  Ops/s:   1.91M  Ops/s/t: 635.27k
     4   threads:  Avg: 2.4572us  Range: [2.4326us, 2.4669us]  Ops/s:   1.63M  Ops/s/t: 406.96k
     8   threads:  Avg: 7.5870us  Range: [7.0968us, 7.6851us]  Ops/s:   1.05M  Ops/s/t: 131.80k
     12  threads:  Avg: 0.0156ms  Range: [0.0155ms, 0.0158ms]  Ops/s: 767.09k  Ops/s/t:  63.92k
     16  threads:  Avg: 0.0265ms  Range: [0.0262ms, 0.0266ms]  Ops/s: 604.29k  Ops/s/t:  37.77k
     32  threads:  Avg: 0.0928ms  Range: [0.0879ms, 0.0957ms]  Ops/s: 344.98k  Ops/s/t:  10.78k
     Operations per second per thread (weighted average): 195.65k

  > SimpleLockFreeQueue
     2   threads:  Avg: 1.1227us  Range: [1.0641us, 1.1357us]  Ops/s:   1.78M  Ops/s/t: 890.75k
     3   threads:  Avg: 1.6745us  Range: [1.4539us, 1.9843us]  Ops/s:   1.79M  Ops/s/t: 597.20k
     4   threads:  Avg: 3.5253us  Range: [3.5045us, 3.5347us]  Ops/s:   1.13M  Ops/s/t: 283.66k
     8   threads:  Avg: 0.0196ms  Range: [0.0182ms, 0.0200ms]  Ops/s: 408.60k  Ops/s/t:  51.08k
     12  threads:  Avg: 0.0687ms  Range: [0.0680ms, 0.0691ms]  Ops/s: 174.55k  Ops/s/t:  14.55k
     16  threads:  Avg: 0.1619ms  Range: [0.1597ms, 0.1634ms]  Ops/s:  98.85k  Ops/s/t:   6.18k
     32  threads:  Avg: 0.6604ms  Range: [0.5891ms, 0.6739ms]  Ops/s:  48.46k  Ops/s/t:   1.51k
     Operations per second per thread (weighted average): 146.45k

  > LockBasedQueue
     2   threads:  Avg: 1.6278us  Range: [0.7309us, 1.7716us]  Ops/s:   1.23M  Ops/s/t: 614.31k
     3   threads:  Avg: 3.8231us  Range: [3.7828us, 3.8525us]  Ops/s: 784.71k  Ops/s/t: 261.57k
     4   threads:  Avg: 8.2348us  Range: [6.7550us, 8.7739us]  Ops/s: 485.75k  Ops/s/t: 121.44k
     8   threads:  Avg: 0.0468ms  Range: [0.0459ms, 0.0474ms]  Ops/s: 170.89k  Ops/s/t:  21.36k
     12  threads:  Avg: 0.1094ms  Range: [0.1087ms, 0.1099ms]  Ops/s: 109.74k  Ops/s/t:   9.14k
     16  threads:  Avg: 0.1959ms  Range: [0.1928ms, 0.1980ms]  Ops/s:  81.69k  Ops/s/t:   5.11k
     32  threads:  Avg: 0.9527ms  Range: [0.9363ms, 0.9593ms]  Ops/s:  33.59k  Ops/s/t:   1.05k
     Operations per second per thread (weighted average):  79.79k

  > std::queue
     (skipping, benchmark not supported...)

only enqueue:
  (Measures the average operation speed when all threads are producers)
  > moodycamel::ConcurrentQueue
      Without tokens
         1    thread:  Avg: 0.0141us  Range: [0.0141us, 0.0141us]  Ops/s:  70.69M  Ops/s/t:  70.69M
         2   threads:  Avg: 0.0249us  Range: [0.0249us, 0.0250us]  Ops/s:  80.19M  Ops/s/t:  40.10M
         4   threads:  Avg: 0.0499us  Range: [0.0498us, 0.0499us]  Ops/s:  80.20M  Ops/s/t:  20.05M
         8   threads:  Avg: 0.1010us  Range: [0.1007us, 0.1011us]  Ops/s:  79.21M  Ops/s/t:   9.90M
         12  threads:  Avg: 0.1543us  Range: [0.1536us, 0.1548us]  Ops/s:  77.78M  Ops/s/t:   6.48M
         16  threads:  Avg: 0.2154us  Range: [0.2142us, 0.2161us]  Ops/s:  74.28M  Ops/s/t:   4.64M
         32  threads:  Avg: 0.6913us  Range: [0.6885us, 0.6931us]  Ops/s:  46.29M  Ops/s/t:   1.45M
         48  threads:  Avg: 1.0539us  Range: [1.0423us, 1.0712us]  Ops/s:  45.55M  Ops/s/t: 948.90k
         Operations per second per thread (weighted average):  11.32M

      With tokens
         1    thread:  Avg: 9.0936ns  Range: [9.0686ns, 9.1014ns]  Ops/s: 109.97M  Ops/s/t: 109.97M
         2   threads:  Avg: 0.0208us  Range: [0.0207us, 0.0208us]  Ops/s:  96.32M  Ops/s/t:  48.16M
         4   threads:  Avg: 0.0417us  Range: [0.0415us, 0.0417us]  Ops/s:  96.04M  Ops/s/t:  24.01M
         8   threads:  Avg: 0.0843us  Range: [0.0841us, 0.0845us]  Ops/s:  94.85M  Ops/s/t:  11.86M
         12  threads:  Avg: 0.1313us  Range: [0.1308us, 0.1316us]  Ops/s:  91.38M  Ops/s/t:   7.62M
         16  threads:  Avg: 0.1812us  Range: [0.1799us, 0.1820us]  Ops/s:  88.28M  Ops/s/t:   5.52M
         32  threads:  Avg: 0.5041us  Range: [0.5035us, 0.5046us]  Ops/s:  63.48M  Ops/s/t:   1.98M
         48  threads:  Avg: 0.7848us  Range: [0.7781us, 0.7938us]  Ops/s:  61.16M  Ops/s/t:   1.27M
         Operations per second per thread (weighted average):  14.89M

  > boost::lockfree::queue
     1    thread:  Avg: 0.0533us  Range: [0.0533us, 0.0533us]  Ops/s:  18.76M  Ops/s/t:  18.76M
     2   threads:  Avg: 1.3973us  Range: [1.3962us, 1.3985us]  Ops/s:   1.43M  Ops/s/t: 715.65k
     4   threads:  Avg: 7.1735us  Range: [4.8830us, 7.7483us]  Ops/s: 557.61k  Ops/s/t: 139.40k
     8   threads:  Avg: 0.0413ms  Range: [0.0391ms, 0.0419ms]  Ops/s: 193.47k  Ops/s/t:  24.18k
     12  threads:  Avg: 0.1030ms  Range: [0.0992ms, 0.1066ms]  Ops/s: 116.47k  Ops/s/t:   9.71k
     16  threads:  Avg: 0.1928ms  Range: [0.1892ms, 0.1949ms]  Ops/s:  82.97k  Ops/s/t:   5.19k
     32  threads:  Avg: 0.7135ms  Range: [0.7109ms, 0.7147ms]  Ops/s:  44.85k  Ops/s/t:   1.40k
     48  threads:  Avg: 1.7082ms  Range: [1.7023ms, 1.7125ms]  Ops/s:  28.10k  Ops/s/t:  585.42
     Operations per second per thread (weighted average):   1.00M

  > tbb::concurrent_queue
     1    thread:  Avg: 0.0301us  Range: [0.0301us, 0.0301us]  Ops/s:  33.21M  Ops/s/t:  33.21M
     2   threads:  Avg: 0.5175us  Range: [0.5168us, 0.5178us]  Ops/s:   3.86M  Ops/s/t:   1.93M
     4   threads:  Avg: 1.9453us  Range: [1.9412us, 1.9502us]  Ops/s:   2.06M  Ops/s/t: 514.07k
     8   threads:  Avg: 0.0107ms  Range: [0.0106ms, 0.0107ms]  Ops/s: 748.95k  Ops/s/t:  93.62k
     12  threads:  Avg: 0.0256ms  Range: [0.0233ms, 0.0261ms]  Ops/s: 468.77k  Ops/s/t:  39.06k
     16  threads:  Avg: 0.0450ms  Range: [0.0447ms, 0.0451ms]  Ops/s: 355.86k  Ops/s/t:  22.24k
     32  threads:  Avg: 0.2274ms  Range: [0.2266ms, 0.2281ms]  Ops/s: 140.70k  Ops/s/t:   4.40k
     48  threads:  Avg: 0.7102ms  Range: [0.7052ms, 0.7157ms]  Ops/s:  67.58k  Ops/s/t:   1.41k
     Operations per second per thread (weighted average):   1.84M

  > SimpleLockFreeQueue
     1    thread:  Avg: 0.0496us  Range: [0.0496us, 0.0496us]  Ops/s:  20.18M  Ops/s/t:  20.18M
     2   threads:  Avg: 1.0667us  Range: [1.0643us, 1.0678us]  Ops/s:   1.87M  Ops/s/t: 937.43k
     4   threads:  Avg: 4.5046us  Range: [3.9727us, 4.6711us]  Ops/s: 887.98k  Ops/s/t: 222.00k
     8   threads:  Avg: 0.0156ms  Range: [0.0146ms, 0.0166ms]  Ops/s: 513.34k  Ops/s/t:  64.17k
     12  threads:  Avg: 0.0378ms  Range: [0.0367ms, 0.0385ms]  Ops/s: 317.46k  Ops/s/t:  26.45k
     16  threads:  Avg: 0.0684ms  Range: [0.0671ms, 0.0691ms]  Ops/s: 233.94k  Ops/s/t:  14.62k
     32  threads:  Avg: 0.1494ms  Range: [0.1483ms, 0.1498ms]  Ops/s: 214.18k  Ops/s/t:   6.69k
     48  threads:  Avg: 0.3392ms  Range: [0.3367ms, 0.3400ms]  Ops/s: 141.51k  Ops/s/t:   2.95k
     Operations per second per thread (weighted average):   1.14M

  > LockBasedQueue
     1    thread:  Avg: 0.0546us  Range: [0.0546us, 0.0546us]  Ops/s:  18.30M  Ops/s/t:  18.30M
     2   threads:  Avg: 0.4666us  Range: [0.4610us, 0.4700us]  Ops/s:   4.29M  Ops/s/t:   2.14M
     4   threads:  Avg: 5.9669us  Range: [5.0405us, 6.2625us]  Ops/s: 670.37k  Ops/s/t: 167.59k
     8   threads:  Avg: 0.0217ms  Range: [0.0186ms, 0.0223ms]  Ops/s: 368.76k  Ops/s/t:  46.09k
     12  threads:  Avg: 0.0395ms  Range: [0.0227ms, 0.0467ms]  Ops/s: 304.07k  Ops/s/t:  25.34k
     16  threads:  Avg: 0.0775ms  Range: [0.0753ms, 0.0790ms]  Ops/s: 206.41k  Ops/s/t:  12.90k
     32  threads:  Avg: 0.2710ms  Range: [0.2028ms, 0.2853ms]  Ops/s: 118.07k  Ops/s/t:   3.69k
     48  threads:  Avg: 0.5390ms  Range: [0.5018ms, 0.5553ms]  Ops/s:  89.05k  Ops/s/t:   1.86k
     Operations per second per thread (weighted average):   1.13M

  > std::queue
     1    thread:  Avg: 5.0901ns  Range: [5.0309ns, 5.0997ns]  Ops/s: 196.46M  Ops/s/t: 196.46M
     Operations per second per thread (weighted average): 196.46M

only enqueue (pre-allocated):
  (Measures the average operation speed when all threads are producers,
  and the queue has been stretched out first)
  > moodycamel::ConcurrentQueue
      Without tokens
         1    thread:  Avg: 0.0126us  Range: [0.0126us, 0.0126us]  Ops/s:  79.19M  Ops/s/t:  79.19M
         2   threads:  Avg: 0.0337us  Range: [0.0337us, 0.0337us]  Ops/s:  59.37M  Ops/s/t:  29.69M
         4   threads:  Avg: 0.1239us  Range: [0.1214us, 0.1253us]  Ops/s:  32.28M  Ops/s/t:   8.07M
         8   threads:  Avg: 0.5276us  Range: [0.5163us, 0.5358us]  Ops/s:  15.16M  Ops/s/t:   1.90M
         32  threads:  Avg: 0.0168ms  Range: [0.0165ms, 0.0169ms]  Ops/s:   1.91M  Ops/s/t:  59.62k
         Operations per second per thread (weighted average):  11.09M

      With tokens
         1    thread:  Avg: 7.5041ns  Range: [7.4886ns, 7.5103ns]  Ops/s: 133.26M  Ops/s/t: 133.26M
         2   threads:  Avg: 0.0168us  Range: [0.0167us, 0.0168us]  Ops/s: 119.21M  Ops/s/t:  59.60M
         4   threads:  Avg: 0.0331us  Range: [0.0330us, 0.0331us]  Ops/s: 120.88M  Ops/s/t:  30.22M
         8   threads:  Avg: 0.0661us  Range: [0.0655us, 0.0665us]  Ops/s: 121.05M  Ops/s/t:  15.13M
         32  threads:  Avg: 0.3257us  Range: [0.3239us, 0.3267us]  Ops/s:  98.25M  Ops/s/t:   3.07M
         Operations per second per thread (weighted average):  26.22M

  > boost::lockfree::queue
     1    thread:  Avg: 0.0358us  Range: [0.0357us, 0.0358us]  Ops/s:  27.96M  Ops/s/t:  27.96M
     2   threads:  Avg: 0.2404us  Range: [0.2268us, 0.2599us]  Ops/s:   8.32M  Ops/s/t:   4.16M
     4   threads:  Avg: 7.3158us  Range: [6.9033us, 8.1491us]  Ops/s: 546.76k  Ops/s/t: 136.69k
     8   threads:  Avg: 0.0487ms  Range: [0.0465ms, 0.0492ms]  Ops/s: 164.38k  Ops/s/t:  20.55k
     32  threads:  Avg: 0.5564ms  Range: [0.5395ms, 0.5664ms]  Ops/s:  57.51k  Ops/s/t:   1.80k
     Operations per second per thread (weighted average):   2.65M

  > tbb::concurrent_queue
     1    thread:  Avg: 0.0301us  Range: [0.0301us, 0.0301us]  Ops/s:  33.25M  Ops/s/t:  33.25M
     2   threads:  Avg: 0.5946us  Range: [0.5919us, 0.5973us]  Ops/s:   3.36M  Ops/s/t:   1.68M
     4   threads:  Avg: 1.9060us  Range: [1.9048us, 1.9069us]  Ops/s:   2.10M  Ops/s/t: 524.66k
     8   threads:  Avg: 0.0105ms  Range: [0.0105ms, 0.0105ms]  Ops/s: 761.71k  Ops/s/t:  95.21k
     32  threads:  Avg: 0.2255ms  Range: [0.2248ms, 0.2259ms]  Ops/s: 141.91k  Ops/s/t:   4.43k
     Operations per second per thread (weighted average):   2.87M

  > SimpleLockFreeQueue
     1    thread:  Avg: 0.0460us  Range: [0.0460us, 0.0460us]  Ops/s:  21.73M  Ops/s/t:  21.73M
     2   threads:  Avg: 1.2065us  Range: [1.2053us, 1.2073us]  Ops/s:   1.66M  Ops/s/t: 828.82k
     4   threads:  Avg: 4.9670us  Range: [4.3212us, 5.1403us]  Ops/s: 805.32k  Ops/s/t: 201.33k
     8   threads:  Avg: 0.0192ms  Range: [0.0188ms, 0.0194ms]  Ops/s: 417.06k  Ops/s/t:  52.13k
     32  threads:  Avg: 0.2600ms  Range: [0.2589ms, 0.2607ms]  Ops/s: 123.06k  Ops/s/t:   3.85k
     Operations per second per thread (weighted average):   1.82M

  > LockBasedQueue
     1    thread:  Avg: 0.0553us  Range: [0.0552us, 0.0553us]  Ops/s:  18.09M  Ops/s/t:  18.09M
     2   threads:  Avg: 0.4806us  Range: [0.4753us, 0.4859us]  Ops/s:   4.16M  Ops/s/t:   2.08M
     4   threads:  Avg: 4.9843us  Range: [4.7319us, 5.1623us]  Ops/s: 802.51k  Ops/s/t: 200.63k
     8   threads:  Avg: 0.0218ms  Range: [0.0202ms, 0.0223ms]  Ops/s: 366.19k  Ops/s/t:  45.77k
     32  threads:  Avg: 0.2786ms  Range: [0.2736ms, 0.2813ms]  Ops/s: 114.85k  Ops/s/t:   3.59k
     Operations per second per thread (weighted average):   1.67M

  > std::queue
     1    thread:  Avg: 5.1703ns  Range: [5.1616ns, 5.1739ns]  Ops/s: 193.41M  Ops/s/t: 193.41M
     Operations per second per thread (weighted average): 193.41M

only enqueue bulk:
  (Measures the average speed of enqueueing an item in bulk when all threads are producers)
  > moodycamel::ConcurrentQueue
      Without tokens
         1    thread:  Avg: 4.3278ns  Range: [4.3220ns, 4.3302ns]  Ops/s: 231.07M  Ops/s/t: 231.07M
         2   threads:  Avg: 8.6521ns  Range: [8.6212ns, 8.6733ns]  Ops/s: 231.16M  Ops/s/t: 115.58M
         4   threads:  Avg: 0.0175us  Range: [0.0174us, 0.0175us]  Ops/s: 228.85M  Ops/s/t:  57.21M
         8   threads:  Avg: 0.0340us  Range: [0.0338us, 0.0341us]  Ops/s: 235.47M  Ops/s/t:  29.43M
         12  threads:  Avg: 0.0595us  Range: [0.0593us, 0.0597us]  Ops/s: 201.65M  Ops/s/t:  16.80M
         16  threads:  Avg: 0.1129us  Range: [0.1018us, 0.1191us]  Ops/s: 141.69M  Ops/s/t:   8.86M
         32  threads:  Avg: 0.3418us  Range: [0.3295us, 0.3519us]  Ops/s:  93.62M  Ops/s/t:   2.93M
         48  threads:  Avg: 0.5520us  Range: [0.5256us, 0.5639us]  Ops/s:  86.96M  Ops/s/t:   1.81M
         Operations per second per thread (weighted average):  32.34M

      With tokens
         1    thread:  Avg: 4.0631ns  Range: [4.0621ns, 4.0644ns]  Ops/s: 246.12M  Ops/s/t: 246.12M
         2   threads:  Avg: 8.6975ns  Range: [8.6641ns, 8.7179ns]  Ops/s: 229.95M  Ops/s/t: 114.98M
         4   threads:  Avg: 0.0182us  Range: [0.0181us, 0.0182us]  Ops/s: 220.30M  Ops/s/t:  55.08M
         8   threads:  Avg: 0.0392us  Range: [0.0390us, 0.0393us]  Ops/s: 203.85M  Ops/s/t:  25.48M
         12  threads:  Avg: 0.0685us  Range: [0.0682us, 0.0687us]  Ops/s: 175.15M  Ops/s/t:  14.60M
         16  threads:  Avg: 0.1134us  Range: [0.1122us, 0.1142us]  Ops/s: 141.05M  Ops/s/t:   8.82M
         32  threads:  Avg: 0.3856us  Range: [0.3822us, 0.3872us]  Ops/s:  82.99M  Ops/s/t:   2.59M
         48  threads:  Avg: 0.5514us  Range: [0.5408us, 0.5617us]  Ops/s:  87.05M  Ops/s/t:   1.81M
         Operations per second per thread (weighted average):  32.54M

  > boost::lockfree::queue
     (skipping, benchmark not supported...)

  > tbb::concurrent_queue
     (skipping, benchmark not supported...)

  > SimpleLockFreeQueue
     (skipping, benchmark not supported...)

  > LockBasedQueue
     (skipping, benchmark not supported...)

  > std::queue
     (skipping, benchmark not supported...)

only enqueue bulk (pre-allocated):
  (Measures the average speed of enqueueing an item in bulk when all threads are producers,
  and the queue has been stretched out first)
  > moodycamel::ConcurrentQueue
      Without tokens
         1    thread:  Avg: 4.3689ns  Range: [4.3676ns, 4.3710ns]  Ops/s: 228.89M  Ops/s/t: 228.89M
         2   threads:  Avg: 8.9038ns  Range: [8.8807ns, 8.9141ns]  Ops/s: 224.62M  Ops/s/t: 112.31M
         4   threads:  Avg: 0.0188us  Range: [0.0188us, 0.0189us]  Ops/s: 212.28M  Ops/s/t:  53.07M
         8   threads:  Avg: 0.0411us  Range: [0.0410us, 0.0413us]  Ops/s: 194.54M  Ops/s/t:  24.32M
         32  threads:  Avg: 0.4054us  Range: [0.3987us, 0.4083us]  Ops/s:  78.93M  Ops/s/t:   2.47M
         Operations per second per thread (weighted average):  44.70M

      With tokens
         1    thread:  Avg: 4.0712ns  Range: [4.0682ns, 4.0741ns]  Ops/s: 245.63M  Ops/s/t: 245.63M
         2   threads:  Avg: 8.9105ns  Range: [8.8472ns, 8.9616ns]  Ops/s: 224.46M  Ops/s/t: 112.23M
         4   threads:  Avg: 0.0185us  Range: [0.0184us, 0.0185us]  Ops/s: 216.46M  Ops/s/t:  54.12M
         8   threads:  Avg: 0.0397us  Range: [0.0396us, 0.0399us]  Ops/s: 201.28M  Ops/s/t:  25.16M
         32  threads:  Avg: 0.3868us  Range: [0.3856us, 0.3875us]  Ops/s:  82.73M  Ops/s/t:   2.59M
         Operations per second per thread (weighted average):  46.39M

  > boost::lockfree::queue
     (skipping, benchmark not supported...)

  > tbb::concurrent_queue
     (skipping, benchmark not supported...)

  > SimpleLockFreeQueue
     (skipping, benchmark not supported...)

  > LockBasedQueue
     (skipping, benchmark not supported...)

  > std::queue
     (skipping, benchmark not supported...)

only dequeue:
  (Measures the average operation speed when all threads are consumers)
  > moodycamel::ConcurrentQueue
      Without tokens
         1    thread:  Avg: 0.0357us  Range: [0.0357us, 0.0357us]  Ops/s:  27.99M  Ops/s/t:  27.99M
         2   threads:  Avg: 0.7098us  Range: [0.7086us, 0.7108us]  Ops/s:   2.82M  Ops/s/t:   1.41M
         4   threads:  Avg: 2.6890us  Range: [2.6831us, 2.6970us]  Ops/s:   1.49M  Ops/s/t: 371.89k
         8   threads:  Avg: 8.9909us  Range: [8.5916us, 9.1297us]  Ops/s: 889.79k  Ops/s/t: 111.22k
         12  threads:  Avg: 0.0187ms  Range: [0.0185ms, 0.0188ms]  Ops/s: 642.71k  Ops/s/t:  53.56k
         16  threads:  Avg: 0.0317ms  Range: [0.0310ms, 0.0321ms]  Ops/s: 504.92k  Ops/s/t:  31.56k
         32  threads:  Avg: 0.0862ms  Range: [0.0855ms, 0.0865ms]  Ops/s: 371.38k  Ops/s/t:  11.61k
         48  threads:  Avg: 0.2111ms  Range: [0.2092ms, 0.2126ms]  Ops/s: 227.42k  Ops/s/t:   4.74k
         Operations per second per thread (weighted average): 984.16k

      With tokens
         1    thread:  Avg: 0.0312us  Range: [0.0312us, 0.0312us]  Ops/s:  32.04M  Ops/s/t:  32.04M
         2   threads:  Avg: 0.0692us  Range: [0.0691us, 0.0693us]  Ops/s:  28.89M  Ops/s/t:  14.45M
         4   threads:  Avg: 0.1392us  Range: [0.1389us, 0.1394us]  Ops/s:  28.74M  Ops/s/t:   7.18M
         8   threads:  Avg: 0.2820us  Range: [0.2804us, 0.2831us]  Ops/s:  28.36M  Ops/s/t:   3.55M
         12  threads:  Avg: 0.4334us  Range: [0.4315us, 0.4349us]  Ops/s:  27.69M  Ops/s/t:   2.31M
         16  threads:  Avg: 0.5946us  Range: [0.5828us, 0.6055us]  Ops/s:  26.91M  Ops/s/t:   1.68M
         32  threads:  Avg: 8.8624us  Range: [7.6002us, 9.7372us]  Ops/s:   3.61M  Ops/s/t: 112.84k
         48  threads:  Avg: 0.0125ms  Range: [0.0115ms, 0.0136ms]  Ops/s:   3.83M  Ops/s/t:  79.79k
         Operations per second per thread (weighted average):   3.48M

  > boost::lockfree::queue
     1    thread:  Avg: 0.0293us  Range: [0.0293us, 0.0293us]  Ops/s:  34.15M  Ops/s/t:  34.15M
     2   threads:  Avg: 1.2354us  Range: [1.2319us, 1.2374us]  Ops/s:   1.62M  Ops/s/t: 809.45k
     4   threads:  Avg: 4.7702us  Range: [3.9572us, 4.8897us]  Ops/s: 838.53k  Ops/s/t: 209.63k
     8   threads:  Avg: 0.0188ms  Range: [0.0185ms, 0.0189ms]  Ops/s: 426.57k  Ops/s/t:  53.32k
     12  threads:  Avg: 0.0436ms  Range: [0.0429ms, 0.0439ms]  Ops/s: 275.09k  Ops/s/t:  22.92k
     16  threads:  Avg: 0.0829ms  Range: [0.0807ms, 0.0835ms]  Ops/s: 192.92k  Ops/s/t:  12.06k
     32  threads:  Avg: 0.3059ms  Range: [0.3049ms, 0.3070ms]  Ops/s: 104.60k  Ops/s/t:   3.27k
     48  threads:  Avg: 0.7265ms  Range: [0.7189ms, 0.7301ms]  Ops/s:  66.07k  Ops/s/t:   1.38k
     Operations per second per thread (weighted average):   1.10M

  > tbb::concurrent_queue
     1    thread:  Avg: 0.0197us  Range: [0.0197us, 0.0197us]  Ops/s:  50.68M  Ops/s/t:  50.68M
     2   threads:  Avg: 0.3747us  Range: [0.2215us, 0.3976us]  Ops/s:   5.34M  Ops/s/t:   2.67M
     4   threads:  Avg: 2.1049us  Range: [1.4759us, 2.1970us]  Ops/s:   1.90M  Ops/s/t: 475.08k
     8   threads:  Avg: 0.0109ms  Range: [0.0107ms, 0.0111ms]  Ops/s: 731.00k  Ops/s/t:  91.38k
     12  threads:  Avg: 0.0255ms  Range: [0.0254ms, 0.0255ms]  Ops/s: 471.40k  Ops/s/t:  39.28k
     16  threads:  Avg: 0.0431ms  Range: [0.0386ms, 0.0456ms]  Ops/s: 371.22k  Ops/s/t:  23.20k
     32  threads:  Avg: 0.1530ms  Range: [0.1520ms, 0.1536ms]  Ops/s: 209.18k  Ops/s/t:   6.54k
     48  threads:  Avg: 0.6456ms  Range: [0.6427ms, 0.6491ms]  Ops/s:  74.35k  Ops/s/t:   1.55k
     Operations per second per thread (weighted average):   1.82M

  > SimpleLockFreeQueue
     1    thread:  Avg: 0.0244us  Range: [0.0244us, 0.0245us]  Ops/s:  40.92M  Ops/s/t:  40.92M
     2   threads:  Avg: 1.5279us  Range: [1.5107us, 1.5381us]  Ops/s:   1.31M  Ops/s/t: 654.49k
     4   threads:  Avg: 9.8623us  Range: [9.7481us, 9.8987us]  Ops/s: 405.59k  Ops/s/t: 101.40k
     8   threads:  Avg: 0.0504ms  Range: [0.0470ms, 0.0522ms]  Ops/s: 158.77k  Ops/s/t:  19.85k
     12  threads:  Avg: 0.1378ms  Range: [0.1328ms, 0.1407ms]  Ops/s:  87.11k  Ops/s/t:   7.26k
     16  threads:  Avg: 0.2761ms  Range: [0.2702ms, 0.2790ms]  Ops/s:  57.96k  Ops/s/t:   3.62k
     32  threads:  Avg: 0.9643ms  Range: [0.9583ms, 0.9689ms]  Ops/s:  33.18k  Ops/s/t:   1.04k
     48  threads:  Avg: 2.1728ms  Range: [2.1511ms, 2.1824ms]  Ops/s:  22.09k  Ops/s/t:  460.24
     Operations per second per thread (weighted average):   1.28M

  > LockBasedQueue
     1    thread:  Avg: 0.0457us  Range: [0.0457us, 0.0457us]  Ops/s:  21.87M  Ops/s/t:  21.87M
     2   threads:  Avg: 0.3881us  Range: [0.3595us, 0.4002us]  Ops/s:   5.15M  Ops/s/t:   2.58M
     4   threads:  Avg: 8.5581us  Range: [8.3268us, 8.6466us]  Ops/s: 467.39k  Ops/s/t: 116.85k
     8   threads:  Avg: 0.0367ms  Range: [0.0346ms, 0.0372ms]  Ops/s: 218.20k  Ops/s/t:  27.27k
     12  threads:  Avg: 0.0824ms  Range: [0.0806ms, 0.0830ms]  Ops/s: 145.71k  Ops/s/t:  12.14k
     16  threads:  Avg: 0.1363ms  Range: [0.1032ms, 0.1443ms]  Ops/s: 117.36k  Ops/s/t:   7.34k
     32  threads:  Avg: 0.4370ms  Range: [0.4082ms, 0.4561ms]  Ops/s:  73.23k  Ops/s/t:   2.29k
     48  threads:  Avg: 0.7839ms  Range: [0.7233ms, 0.8163ms]  Ops/s:  61.23k  Ops/s/t:   1.28k
     Operations per second per thread (weighted average): 837.50k

  > std::queue
     1    thread:  Avg: 3.2964ns  Range: [3.2952ns, 3.2980ns]  Ops/s: 303.36M  Ops/s/t: 303.36M
     1    thread:  Avg: 3.2976ns  Range: [3.2950ns, 3.2985ns]  Ops/s: 303.25M  Ops/s/t: 303.25M
     Operations per second per thread (weighted average): 303.31M

only dequeue bulk:
  (Measures the average speed of dequeueing an item in bulk when all threads are consumers)
  > moodycamel::ConcurrentQueue
      Without tokens
         1    thread:  Avg: 1.3818ns  Range: [1.3812ns, 1.3828ns]  Ops/s: 723.68M  Ops/s/t: 723.68M
         2   threads:  Avg: 8.9961ns  Range: [8.9915ns, 8.9977ns]  Ops/s: 222.32M  Ops/s/t: 111.16M
         4   threads:  Avg: 0.0390us  Range: [0.0389us, 0.0391us]  Ops/s: 102.59M  Ops/s/t:  25.65M
         8   threads:  Avg: 0.1611us  Range: [0.1607us, 0.1614us]  Ops/s:  49.65M  Ops/s/t:   6.21M
         12  threads:  Avg: 0.3702us  Range: [0.3327us, 0.3803us]  Ops/s:  32.42M  Ops/s/t:   2.70M
         16  threads:  Avg: 0.6996us  Range: [0.6953us, 0.7054us]  Ops/s:  22.87M  Ops/s/t:   1.43M
         32  threads:  Avg: 2.3975us  Range: [2.3849us, 2.4046us]  Ops/s:  13.35M  Ops/s/t: 417.11k
         48  threads:  Avg: 5.1981us  Range: [5.1573us, 5.2400us]  Ops/s:   9.23M  Ops/s/t: 192.38k
         Operations per second per thread (weighted average):  30.54M

      With tokens
         1    thread:  Avg: 1.2074ns  Range: [1.2052ns, 1.2110ns]  Ops/s: 828.20M  Ops/s/t: 828.20M
         2   threads:  Avg: 2.8806ns  Range: [2.8747ns, 2.8845ns]  Ops/s: 694.30M  Ops/s/t: 347.15M
         4   threads:  Avg: 5.7023ns  Range: [5.6673ns, 5.7191ns]  Ops/s: 701.47M  Ops/s/t: 175.37M
         8   threads:  Avg: 0.0126us  Range: [0.0126us, 0.0127us]  Ops/s: 634.07M  Ops/s/t:  79.26M
         12  threads:  Avg: 0.0213us  Range: [0.0213us, 0.0213us]  Ops/s: 563.18M  Ops/s/t:  46.93M
         16  threads:  Avg: 0.0323us  Range: [0.0323us, 0.0324us]  Ops/s: 494.72M  Ops/s/t:  30.92M
         32  threads:  Avg: 0.1146us  Range: [0.1121us, 0.1151us]  Ops/s: 279.12M  Ops/s/t:   8.72M
         48  threads:  Avg: 0.1535us  Range: [0.1530us, 0.1538us]  Ops/s: 312.73M  Ops/s/t:   6.52M
         Operations per second per thread (weighted average):  86.51M

  > boost::lockfree::queue
     (skipping, benchmark not supported...)

  > tbb::concurrent_queue
     (skipping, benchmark not supported...)

  > SimpleLockFreeQueue
     (skipping, benchmark not supported...)

  > LockBasedQueue
     (skipping, benchmark not supported...)

  > std::queue
     (skipping, benchmark not supported...)

mostly enqueue:
  (Measures the average operation speed when most threads are enqueueing)
  > moodycamel::ConcurrentQueue
      Without tokens
         2   threads:  Avg: 0.1469us  Range: [0.0750us, 0.1572us]  Ops/s:  13.61M  Ops/s/t:   6.81M
         4   threads:  Avg: 0.1857us  Range: [0.1846us, 0.1868us]  Ops/s:  21.54M  Ops/s/t:   5.38M
         8   threads:  Avg: 0.8476us  Range: [0.8309us, 0.8565us]  Ops/s:   9.44M  Ops/s/t:   1.18M
         32  threads:  Avg: 9.9740us  Range: [9.8477us, 0.0101ms]  Ops/s:   3.21M  Ops/s/t: 100.26k
         Operations per second per thread (weighted average):   2.04M

      With tokens
         2   threads:  Avg: 0.0950us  Range: [0.0891us, 0.0964us]  Ops/s:  21.04M  Ops/s/t:  10.52M
         4   threads:  Avg: 0.0852us  Range: [0.0852us, 0.0853us]  Ops/s:  46.94M  Ops/s/t:  11.74M
         8   threads:  Avg: 0.2579us  Range: [0.1719us, 0.4591us]  Ops/s:  31.02M  Ops/s/t:   3.88M
         32  threads:  Avg: 3.4474us  Range: [1.9589us, 4.7942us]  Ops/s:   9.28M  Ops/s/t: 290.07k
         Operations per second per thread (weighted average):   4.28M

  > boost::lockfree::queue
     2   threads:  Avg: 0.1359us  Range: [0.1334us, 0.1373us]  Ops/s:  14.71M  Ops/s/t:   7.36M
     4   threads:  Avg: 4.1708us  Range: [3.5076us, 4.3484us]  Ops/s: 959.05k  Ops/s/t: 239.76k
     8   threads:  Avg: 0.0262ms  Range: [0.0259ms, 0.0264ms]  Ops/s: 305.26k  Ops/s/t:  38.16k
     32  threads:  Avg: 0.4770ms  Range: [0.4692ms, 0.4830ms]  Ops/s:  67.08k  Ops/s/t:   2.10k
     Operations per second per thread (weighted average): 924.62k

  > tbb::concurrent_queue
     2   threads:  Avg: 0.1219us  Range: [0.1113us, 0.1395us]  Ops/s:  16.41M  Ops/s/t:   8.20M
     4   threads:  Avg: 1.5456us  Range: [1.5278us, 1.5544us]  Ops/s:   2.59M  Ops/s/t: 647.00k
     8   threads:  Avg: 7.6602us  Range: [7.3877us, 7.8992us]  Ops/s:   1.04M  Ops/s/t: 130.54k
     32  threads:  Avg: 0.1652ms  Range: [0.1648ms, 0.1655ms]  Ops/s: 193.70k  Ops/s/t:   6.05k
     Operations per second per thread (weighted average):   1.12M

  > SimpleLockFreeQueue
     2   threads:  Avg: 0.9759us  Range: [0.9742us, 0.9783us]  Ops/s:   2.05M  Ops/s/t:   1.02M
     4   threads:  Avg: 4.5384us  Range: [3.3258us, 4.7206us]  Ops/s: 881.38k  Ops/s/t: 220.34k
     8   threads:  Avg: 0.0169ms  Range: [0.0165ms, 0.0172ms]  Ops/s: 472.04k  Ops/s/t:  59.00k
     32  threads:  Avg: 0.2282ms  Range: [0.2272ms, 0.2293ms]  Ops/s: 140.23k  Ops/s/t:   4.38k
     Operations per second per thread (weighted average): 174.92k

  > LockBasedQueue
     2   threads:  Avg: 0.2195us  Range: [0.2176us, 0.2217us]  Ops/s:   9.11M  Ops/s/t:   4.55M
     4   threads:  Avg: 6.7491us  Range: [4.7114us, 7.6459us]  Ops/s: 592.67k  Ops/s/t: 148.17k
     8   threads:  Avg: 0.0326ms  Range: [0.0304ms, 0.0351ms]  Ops/s: 245.33k  Ops/s/t:  30.67k
     32  threads:  Avg: 0.4410ms  Range: [0.3794ms, 0.4928ms]  Ops/s:  72.57k  Ops/s/t:   2.27k
     Operations per second per thread (weighted average): 574.60k

  > std::queue
     (skipping, benchmark not supported...)

mostly enqueue bulk:
  (Measures the average speed of enqueueing an item in bulk under light contention)
  > moodycamel::ConcurrentQueue
      Without tokens
         2   threads:  Avg: 8.0674ns  Range: [8.0590ns, 8.0748ns]  Ops/s: 247.91M  Ops/s/t: 123.96M
         4   threads:  Avg: 0.0184us  Range: [0.0183us, 0.0185us]  Ops/s: 217.04M  Ops/s/t:  54.26M
         8   threads:  Avg: 0.0383us  Range: [0.0380us, 0.0384us]  Ops/s: 208.95M  Ops/s/t:  26.12M
         32  threads:  Avg: 0.2972us  Range: [0.2858us, 0.3040us]  Ops/s: 107.67M  Ops/s/t:   3.36M
         Operations per second per thread (weighted average):  31.66M

      With tokens
         2   threads:  Avg: 8.0878ns  Range: [7.2902ns, 8.2116ns]  Ops/s: 247.29M  Ops/s/t: 123.64M
         4   threads:  Avg: 0.0143us  Range: [0.0136us, 0.0146us]  Ops/s: 279.47M  Ops/s/t:  69.87M
         8   threads:  Avg: 0.0328us  Range: [0.0306us, 0.0378us]  Ops/s: 243.54M  Ops/s/t:  30.44M
         32  threads:  Avg: 0.2700us  Range: [0.2470us, 0.2801us]  Ops/s: 118.51M  Ops/s/t:   3.70M
         Operations per second per thread (weighted average):  35.43M

  > boost::lockfree::queue
     (skipping, benchmark not supported...)

  > tbb::concurrent_queue
     (skipping, benchmark not supported...)

  > SimpleLockFreeQueue
     (skipping, benchmark not supported...)

  > LockBasedQueue
     (skipping, benchmark not supported...)

  > std::queue
     (skipping, benchmark not supported...)

mostly dequeue:
  (Measures the average operation speed when most threads are dequeueing)
  > moodycamel::ConcurrentQueue
      Without tokens
         2   threads:  Avg: 0.1215us  Range: [0.1195us, 0.1224us]  Ops/s:  16.46M  Ops/s/t:   8.23M
         4   threads:  Avg: 1.6265us  Range: [1.6144us, 1.6423us]  Ops/s:   2.46M  Ops/s/t: 614.83k
         8   threads:  Avg: 5.0893us  Range: [5.0332us, 5.1180us]  Ops/s:   1.57M  Ops/s/t: 196.49k
         Operations per second per thread (weighted average):   2.15M

      With tokens
         2   threads:  Avg: 0.1570us  Range: [0.1397us, 0.1603us]  Ops/s:  12.74M  Ops/s/t:   6.37M
         4   threads:  Avg: 1.5041us  Range: [1.5001us, 1.5064us]  Ops/s:   2.66M  Ops/s/t: 664.87k
         8   threads:  Avg: 1.5055us  Range: [1.4913us, 1.5111us]  Ops/s:   5.31M  Ops/s/t: 664.24k
         Operations per second per thread (weighted average):   1.96M

  > boost::lockfree::queue
     2   threads:  Avg: 0.4529us  Range: [0.4522us, 0.4535us]  Ops/s:   4.42M  Ops/s/t:   2.21M
     4   threads:  Avg: 2.2630us  Range: [2.0034us, 2.3220us]  Ops/s:   1.77M  Ops/s/t: 441.90k
     8   threads:  Avg: 0.0116ms  Range: [0.0115ms, 0.0116ms]  Ops/s: 690.84k  Ops/s/t:  86.35k
     Operations per second per thread (weighted average): 680.85k

  > tbb::concurrent_queue
     2   threads:  Avg: 0.3612us  Range: [0.3582us, 0.3629us]  Ops/s:   5.54M  Ops/s/t:   2.77M
     4   threads:  Avg: 0.9626us  Range: [0.5721us, 1.1233us]  Ops/s:   4.16M  Ops/s/t:   1.04M
     8   threads:  Avg: 5.8279us  Range: [5.2630us, 6.2185us]  Ops/s:   1.37M  Ops/s/t: 171.59k
     Operations per second per thread (weighted average):   1.04M

  > SimpleLockFreeQueue
     2   threads:  Avg: 1.1719us  Range: [1.1633us, 1.1786us]  Ops/s:   1.71M  Ops/s/t: 853.34k
     4   threads:  Avg: 4.9511us  Range: [3.2836us, 5.4309us]  Ops/s: 807.90k  Ops/s/t: 201.97k
     8   threads:  Avg: 0.0270ms  Range: [0.0260ms, 0.0275ms]  Ops/s: 295.91k  Ops/s/t:  36.99k
     Operations per second per thread (weighted average): 274.78k

  > LockBasedQueue
     2   threads:  Avg: 0.3299us  Range: [0.3162us, 0.3417us]  Ops/s:   6.06M  Ops/s/t:   3.03M
     4   threads:  Avg: 5.8541us  Range: [5.5493us, 6.1109us]  Ops/s: 683.28k  Ops/s/t: 170.82k
     8   threads:  Avg: 0.0239ms  Range: [0.0227ms, 0.0248ms]  Ops/s: 334.93k  Ops/s/t:  41.87k
     Operations per second per thread (weighted average): 760.33k

  > std::queue
     (skipping, benchmark not supported...)

mostly dequeue bulk:
  (Measures the average speed of dequeueing an item in bulk under light contention)
  > moodycamel::ConcurrentQueue
      Without tokens
         2   threads:  Avg: 0.0108us  Range: [0.0103us, 0.0109us]  Ops/s: 185.78M  Ops/s/t:  92.89M
         4   threads:  Avg: 0.0373us  Range: [0.0372us, 0.0373us]  Ops/s: 107.36M  Ops/s/t:  26.84M
         8   threads:  Avg: 0.1495us  Range: [0.1462us, 0.1561us]  Ops/s:  53.52M  Ops/s/t:   6.69M
         Operations per second per thread (weighted average):  32.67M

      With tokens
         2   threads:  Avg: 3.7186ns  Range: [3.7151ns, 3.7217ns]  Ops/s: 537.84M  Ops/s/t: 268.92M
         4   threads:  Avg: 6.9914ns  Range: [6.9631ns, 7.0045ns]  Ops/s: 572.13M  Ops/s/t: 143.03M
         8   threads:  Avg: 0.0141us  Range: [0.0140us, 0.0143us]  Ops/s: 565.65M  Ops/s/t:  70.71M
         Operations per second per thread (weighted average): 138.78M

  > boost::lockfree::queue
     (skipping, benchmark not supported...)

  > tbb::concurrent_queue
     (skipping, benchmark not supported...)

  > SimpleLockFreeQueue
     (skipping, benchmark not supported...)

  > LockBasedQueue
     (skipping, benchmark not supported...)

  > std::queue
     (skipping, benchmark not supported...)

single-producer, multi-consumer (measuring all but 1 thread):
  (Measures the average speed of dequeueing with only one producer, but multiple consumers)
  > moodycamel::ConcurrentQueue
      Without tokens
         2   threads:  Avg: 0.2405us  Range: [0.2389us, 0.2410us]  Ops/s:   4.16M  Ops/s/t:   4.16M
         4   threads:  Avg: 4.2169us  Range: [3.0232us, 4.7858us]  Ops/s: 711.43k  Ops/s/t: 237.14k
         8   threads:  Avg: 5.7117us  Range: [4.7950us, 6.1325us]  Ops/s:   1.23M  Ops/s/t: 175.08k
         16  threads:  Avg: 8.3830us  Range: [8.1155us, 8.6670us]  Ops/s:   1.79M  Ops/s/t: 119.29k
         Operations per second per thread (weighted average): 593.96k

      With tokens
         2   threads:  Avg: 0.2474us  Range: [0.2461us, 0.2485us]  Ops/s:   4.04M  Ops/s/t:   4.04M
         4   threads:  Avg: 3.3233us  Range: [3.1959us, 3.4740us]  Ops/s: 902.71k  Ops/s/t: 300.90k
         8   threads:  Avg: 5.0620us  Range: [4.0865us, 5.8400us]  Ops/s:   1.38M  Ops/s/t: 197.55k
         16  threads:  Avg: 0.0105ms  Range: [6.5440us, 0.0153ms]  Ops/s:   1.42M  Ops/s/t:  94.84k
         Operations per second per thread (weighted average): 589.43k

  > boost::lockfree::queue
     2   threads:  Avg: 0.1318us  Range: [0.1267us, 0.1376us]  Ops/s:   7.59M  Ops/s/t:   7.59M
     4   threads:  Avg: 0.3896us  Range: [0.3650us, 0.4586us]  Ops/s:   7.70M  Ops/s/t:   2.57M
     8   threads:  Avg: 0.8681us  Range: [0.8362us, 0.9220us]  Ops/s:   8.06M  Ops/s/t:   1.15M
     16  threads:  Avg: 2.4247us  Range: [2.3627us, 2.4832us]  Ops/s:   6.19M  Ops/s/t: 412.42k
     Operations per second per thread (weighted average):   1.80M

  > tbb::concurrent_queue
     2   threads:  Avg: 0.3890us  Range: [0.3863us, 0.3925us]  Ops/s:   2.57M  Ops/s/t:   2.57M
     4   threads:  Avg: 0.3788us  Range: [0.2911us, 1.0901us]  Ops/s:   7.92M  Ops/s/t:   2.64M
     8   threads:  Avg: 0.6557us  Range: [0.5637us, 1.4010us]  Ops/s:  10.67M  Ops/s/t:   1.52M
     16  threads:  Avg: 4.2543us  Range: [2.0247us, 0.0336ms]  Ops/s:   3.53M  Ops/s/t: 235.06k
     Operations per second per thread (weighted average):   1.31M

  > SimpleLockFreeQueue
     2   threads:  Avg: 0.5829us  Range: [0.4318us, 0.6558us]  Ops/s:   1.72M  Ops/s/t:   1.72M
     4   threads:  Avg: 3.7357us  Range: [2.8465us, 5.8089us]  Ops/s: 803.07k  Ops/s/t: 267.69k
     8   threads:  Avg: 8.7515us  Range: [3.1555us, 0.0191ms]  Ops/s: 799.86k  Ops/s/t: 114.27k
     16  threads:  Avg: 0.0202ms  Range: [0.0126ms, 0.0367ms]  Ops/s: 742.55k  Ops/s/t:  49.50k
     Operations per second per thread (weighted average): 288.98k

  > LockBasedQueue
     2   threads:  Avg: 0.1033us  Range: [0.1021us, 0.1044us]  Ops/s:   9.68M  Ops/s/t:   9.68M
     4   threads:  Avg: 2.7103us  Range: [2.6647us, 2.7505us]  Ops/s:   1.11M  Ops/s/t: 368.97k
     8   threads:  Avg: 8.2018us  Range: [7.9971us, 8.4244us]  Ops/s: 853.48k  Ops/s/t: 121.93k
     16  threads:  Avg: 0.0284ms  Range: [0.0277ms, 0.0289ms]  Ops/s: 527.88k  Ops/s/t:  35.19k
     Operations per second per thread (weighted average):   1.17M

  > std::queue
     (skipping, benchmark not supported...)

single-producer, multi-consumer (pre-produced):
  (Measures the average speed of dequeueing from a queue pre-filled by one thread)
  > moodycamel::ConcurrentQueue
      Without tokens
         1    thread:  Avg: 0.0357us  Range: [0.0357us, 0.0358us]  Ops/s:  27.98M  Ops/s/t:  27.98M
         3   threads:  Avg: 1.0896us  Range: [1.0892us, 1.0903us]  Ops/s:   2.75M  Ops/s/t: 917.77k
         7   threads:  Avg: 7.4992us  Range: [7.4811us, 7.5053us]  Ops/s: 933.43k  Ops/s/t: 133.35k
         15  threads:  Avg: 0.0361ms  Range: [0.0359ms, 0.0362ms]  Ops/s: 414.99k  Ops/s/t:  27.67k
         Operations per second per thread (weighted average):   3.25M

      With tokens
         1    thread:  Avg: 0.0312us  Range: [0.0312us, 0.0312us]  Ops/s:  32.05M  Ops/s/t:  32.05M
         3   threads:  Avg: 1.8365us  Range: [1.8311us, 1.8400us]  Ops/s:   1.63M  Ops/s/t: 544.51k
         7   threads:  Avg: 8.9960us  Range: [8.9815us, 9.0019us]  Ops/s: 778.12k  Ops/s/t: 111.16k
         15  threads:  Avg: 0.0384ms  Range: [0.0366ms, 0.0393ms]  Ops/s: 390.49k  Ops/s/t:  26.03k
         Operations per second per thread (weighted average):   3.61M

  > boost::lockfree::queue
     1    thread:  Avg: 0.0292us  Range: [0.0291us, 0.0293us]  Ops/s:  34.26M  Ops/s/t:  34.26M
     3   threads:  Avg: 2.4113us  Range: [2.3990us, 2.4172us]  Ops/s:   1.24M  Ops/s/t: 414.71k
     7   threads:  Avg: 0.0127ms  Range: [0.0125ms, 0.0127ms]  Ops/s: 552.70k  Ops/s/t:  78.96k
     15  threads:  Avg: 0.0676ms  Range: [0.0670ms, 0.0681ms]  Ops/s: 221.74k  Ops/s/t:  14.78k
     Operations per second per thread (weighted average):   3.81M

  > tbb::concurrent_queue
     1    thread:  Avg: 0.0199us  Range: [0.0199us, 0.0199us]  Ops/s:  50.16M  Ops/s/t:  50.16M
     3   threads:  Avg: 1.3713us  Range: [1.3567us, 1.3797us]  Ops/s:   2.19M  Ops/s/t: 729.25k
     7   threads:  Avg: 4.9943us  Range: [4.1482us, 5.3368us]  Ops/s:   1.40M  Ops/s/t: 200.23k
     15  threads:  Avg: 0.0289ms  Range: [0.0224ms, 0.0332ms]  Ops/s: 519.87k  Ops/s/t:  34.66k
     Operations per second per thread (weighted average):   5.63M

  > SimpleLockFreeQueue
     1    thread:  Avg: 0.0245us  Range: [0.0244us, 0.0245us]  Ops/s:  40.88M  Ops/s/t:  40.88M
     3   threads:  Avg: 3.0130us  Range: [2.3021us, 3.4232us]  Ops/s: 995.68k  Ops/s/t: 331.89k
     7   threads:  Avg: 0.0293ms  Range: [0.0260ms, 0.0304ms]  Ops/s: 238.94k  Ops/s/t:  34.13k
     15  threads:  Avg: 0.2168ms  Range: [0.2128ms, 0.2191ms]  Ops/s:  69.19k  Ops/s/t:   4.61k
     Operations per second per thread (weighted average):   4.49M

  > LockBasedQueue
     1    thread:  Avg: 0.0458us  Range: [0.0457us, 0.0458us]  Ops/s:  21.85M  Ops/s/t:  21.85M
     3   threads:  Avg: 1.5927us  Range: [1.3496us, 1.9027us]  Ops/s:   1.88M  Ops/s/t: 627.87k
     7   threads:  Avg: 0.0235ms  Range: [0.0226ms, 0.0239ms]  Ops/s: 297.80k  Ops/s/t:  42.54k
     15  threads:  Avg: 0.0935ms  Range: [0.0502ms, 0.1009ms]  Ops/s: 160.48k  Ops/s/t:  10.70k
     Operations per second per thread (weighted average):   2.50M

  > std::queue
     (skipping, benchmark not supported...)

multi-producer, single-consumer (measuring 1 thread):
  (Measures the average speed of dequeueing with only one consumer, but multiple producers)
  > moodycamel::ConcurrentQueue
      Without tokens
         2   threads:  Avg: 0.0729us  Range: [0.0726us, 0.0730us]  Ops/s:  13.72M  Ops/s/t:  13.72M
         4   threads:  Avg: 0.0507us  Range: [0.0506us, 0.0508us]  Ops/s:  19.73M  Ops/s/t:  19.73M
         8   threads:  Avg: 0.0844us  Range: [0.0839us, 0.0849us]  Ops/s:  11.85M  Ops/s/t:  11.85M
         16  threads:  Avg: 0.2197us  Range: [0.2190us, 0.2208us]  Ops/s:   4.55M  Ops/s/t:   4.55M
         Operations per second per thread (weighted average):  12.46M

      With tokens
         2   threads:  Avg: 0.0604us  Range: [0.0476us, 0.0624us]  Ops/s:  16.55M  Ops/s/t:  16.55M
         4   threads:  Avg: 0.0356us  Range: [0.0354us, 0.0357us]  Ops/s:  28.11M  Ops/s/t:  28.11M
         8   threads:  Avg: 0.0331us  Range: [0.0330us, 0.0332us]  Ops/s:  30.21M  Ops/s/t:  30.21M
         16  threads:  Avg: 0.0326us  Range: [0.0325us, 0.0326us]  Ops/s:  30.70M  Ops/s/t:  30.70M
         Operations per second per thread (weighted average):  26.39M

  > boost::lockfree::queue
     2   threads:  Avg: 0.0476us  Range: [0.0460us, 0.0491us]  Ops/s:  21.02M  Ops/s/t:  21.02M
     4   threads:  Avg: 0.5888us  Range: [0.4460us, 0.6260us]  Ops/s:   1.70M  Ops/s/t:   1.70M
     8   threads:  Avg: 0.2697us  Range: [0.1050us, 0.7683us]  Ops/s:   3.71M  Ops/s/t:   3.71M
     16  threads:  Avg: 1.0713us  Range: [1.0330us, 1.0920us]  Ops/s: 933.44k  Ops/s/t: 933.44k
     Operations per second per thread (weighted average):   6.84M

  > tbb::concurrent_queue
     2   threads:  Avg: 0.2368us  Range: [0.1076us, 0.2808us]  Ops/s:   4.22M  Ops/s/t:   4.22M
     4   threads:  Avg: 0.2267us  Range: [0.2262us, 0.2269us]  Ops/s:   4.41M  Ops/s/t:   4.41M
     8   threads:  Avg: 0.2114us  Range: [0.2098us, 0.2120us]  Ops/s:   4.73M  Ops/s/t:   4.73M
     16  threads:  Avg: 0.2197us  Range: [0.2171us, 0.2202us]  Ops/s:   4.55M  Ops/s/t:   4.55M
     Operations per second per thread (weighted average):   4.48M

  > SimpleLockFreeQueue
     2   threads:  Avg: 0.3092us  Range: [0.2127us, 0.4384us]  Ops/s:   3.23M  Ops/s/t:   3.23M
     4   threads:  Avg: 0.4952us  Range: [0.4757us, 0.5015us]  Ops/s:   2.02M  Ops/s/t:   2.02M
     8   threads:  Avg: 0.3858us  Range: [0.3843us, 0.3862us]  Ops/s:   2.59M  Ops/s/t:   2.59M
     16  threads:  Avg: 0.3647us  Range: [0.3629us, 0.3660us]  Ops/s:   2.74M  Ops/s/t:   2.74M
     Operations per second per thread (weighted average):   2.65M

  > LockBasedQueue
     2   threads:  Avg: 0.1043us  Range: [0.0981us, 0.1714us]  Ops/s:   9.59M  Ops/s/t:   9.59M
     4   threads:  Avg: 0.7658us  Range: [0.7038us, 0.8431us]  Ops/s:   1.31M  Ops/s/t:   1.31M
     8   threads:  Avg: 0.6372us  Range: [0.6062us, 0.6615us]  Ops/s:   1.57M  Ops/s/t:   1.57M
     16  threads:  Avg: 0.5428us  Range: [0.5134us, 0.5528us]  Ops/s:   1.84M  Ops/s/t:   1.84M
     Operations per second per thread (weighted average):   3.58M

  > std::queue
     (skipping, benchmark not supported...)

dequeue from empty:
  (Measures the average speed of attempting to dequeue from an empty queue
  (that eight separate threads had at one point enqueued to))
  > moodycamel::ConcurrentQueue
      Without tokens
         1    thread:  Avg: 0.1438us  Range: [0.0266us, 0.1617us]  Ops/s:   6.96M  Ops/s/t:   6.96M
                ^ Note: No contention -- measures raw failed dequeue speed on empty queue
         2   threads:  Avg: 0.2659us  Range: [0.0935us, 0.3182us]  Ops/s:   7.52M  Ops/s/t:   3.76M
         8   threads:  Avg: 1.4211us  Range: [1.2605us, 1.5397us]  Ops/s:   5.63M  Ops/s/t: 703.66k
         32  threads:  Avg: 0.0106ms  Range: [9.5731us, 0.0123ms]  Ops/s:   3.02M  Ops/s/t:  94.42k
         Operations per second per thread (weighted average):   1.36M

      With tokens
         1    thread:  Avg: 0.0609us  Range: [0.0541us, 0.0784us]  Ops/s:  16.42M  Ops/s/t:  16.42M
                ^ Note: No contention -- measures raw failed dequeue speed on empty queue
         2   threads:  Avg: 0.1062us  Range: [0.0897us, 0.1442us]  Ops/s:  18.84M  Ops/s/t:   9.42M
         8   threads:  Avg: 0.4811us  Range: [0.3461us, 0.5677us]  Ops/s:  16.63M  Ops/s/t:   2.08M
         32  threads:  Avg: 5.0579us  Range: [3.0195us, 7.0480us]  Ops/s:   6.33M  Ops/s/t: 197.71k
         Operations per second per thread (weighted average):   3.37M

  > boost::lockfree::queue
     1    thread:  Avg: 4.6996ns  Range: [4.6987ns, 4.7002ns]  Ops/s: 212.79M  Ops/s/t: 212.79M
            ^ Note: No contention -- measures raw failed dequeue speed on empty queue
     2   threads:  Avg: 8.7301ns  Range: [8.7266ns, 8.7359ns]  Ops/s: 229.09M  Ops/s/t: 114.55M
     8   threads:  Avg: 0.0349us  Range: [0.0348us, 0.0349us]  Ops/s: 229.54M  Ops/s/t:  28.69M
     32  threads:  Avg: 0.2145us  Range: [0.2145us, 0.2145us]  Ops/s: 149.19M  Ops/s/t:   4.66M
     Operations per second per thread (weighted average):  44.25M

  > tbb::concurrent_queue
     1    thread:  Avg: 3.6913ns  Range: [3.6889ns, 3.6921ns]  Ops/s: 270.91M  Ops/s/t: 270.91M
            ^ Note: No contention -- measures raw failed dequeue speed on empty queue
     2   threads:  Avg: 7.3977ns  Range: [7.3920ns, 7.4032ns]  Ops/s: 270.36M  Ops/s/t: 135.18M
     8   threads:  Avg: 0.0296us  Range: [0.0296us, 0.0296us]  Ops/s: 270.48M  Ops/s/t:  33.81M
     32  threads:  Avg: 0.1744us  Range: [0.1743us, 0.1745us]  Ops/s: 183.45M  Ops/s/t:   5.73M
     Operations per second per thread (weighted average):  54.14M

  > SimpleLockFreeQueue
     1    thread:  Avg: 4.8994ns  Range: [4.8967ns, 4.9013ns]  Ops/s: 204.11M  Ops/s/t: 204.11M
            ^ Note: No contention -- measures raw failed dequeue speed on empty queue
     2   threads:  Avg: 0.0102us  Range: [0.0102us, 0.0102us]  Ops/s: 196.03M  Ops/s/t:  98.02M
     8   threads:  Avg: 0.0407us  Range: [0.0407us, 0.0408us]  Ops/s: 196.35M  Ops/s/t:  24.54M
     32  threads:  Avg: 0.3004us  Range: [0.3000us, 0.3009us]  Ops/s: 106.53M  Ops/s/t:   3.33M
     Operations per second per thread (weighted average):  39.54M

  > LockBasedQueue
     1    thread:  Avg: 0.0251us  Range: [0.0251us, 0.0251us]  Ops/s:  39.89M  Ops/s/t:  39.89M
            ^ Note: No contention -- measures raw failed dequeue speed on empty queue
     2   threads:  Avg: 0.1986us  Range: [0.1951us, 0.2009us]  Ops/s:  10.07M  Ops/s/t:   5.03M
     8   threads:  Avg: 6.3745us  Range: [5.5998us, 6.5522us]  Ops/s:   1.25M  Ops/s/t: 156.87k
     32  threads:  Avg: 0.0864ms  Range: [0.0852ms, 0.0876ms]  Ops/s: 370.26k  Ops/s/t:  11.57k
     Operations per second per thread (weighted average):   4.36M

  > std::queue
     1    thread:  Avg: 0.6708ns  Range: [0.6705ns, 0.6710ns]  Ops/s:   1.49G  Ops/s/t:   1.49G
            ^ Note: No contention -- measures raw failed dequeue speed on empty queue
     Operations per second per thread (weighted average):   1.49G

enqueue-dequeue pairs:
  (Measures the average operation speed with each thread doing an enqueue
  followed by a dequeue)
  > moodycamel::ConcurrentQueue
      Without tokens
         1    thread:  Avg: 0.0214us  Range: [0.0214us, 0.0214us]  Ops/s:  46.84M  Ops/s/t:  46.84M
                ^ Note: No contention -- measures speed of immediately dequeueing the item that was just enqueued
         2   threads:  Avg: 0.1197us  Range: [0.0976us, 0.1773us]  Ops/s:  16.71M  Ops/s/t:   8.35M
         4   threads:  Avg: 1.3761us  Range: [1.2900us, 1.4179us]  Ops/s:   2.91M  Ops/s/t: 726.67k
         8   threads:  Avg: 5.7697us  Range: [5.6598us, 5.8091us]  Ops/s:   1.39M  Ops/s/t: 173.32k
         32  threads:  Avg: 0.0754ms  Range: [0.0748ms, 0.0759ms]  Ops/s: 424.33k  Ops/s/t:  13.26k
         Operations per second per thread (weighted average):   4.70M

      With tokens
         1    thread:  Avg: 0.0170us  Range: [0.0170us, 0.0170us]  Ops/s:  58.79M  Ops/s/t:  58.79M
                ^ Note: No contention -- measures speed of immediately dequeueing the item that was just enqueued
         2   threads:  Avg: 0.0498us  Range: [0.0485us, 0.0505us]  Ops/s:  40.12M  Ops/s/t:  20.06M
         4   threads:  Avg: 0.1080us  Range: [0.1024us, 0.1103us]  Ops/s:  37.03M  Ops/s/t:   9.26M
         8   threads:  Avg: 0.3397us  Range: [0.3264us, 0.3478us]  Ops/s:  23.55M  Ops/s/t:   2.94M
         32  threads:  Avg: 9.0299us  Range: [8.7799us, 9.2197us]  Ops/s:   3.54M  Ops/s/t: 110.74k
         Operations per second per thread (weighted average):   8.89M

  > boost::lockfree::queue
     1    thread:  Avg: 0.0328us  Range: [0.0328us, 0.0328us]  Ops/s:  30.45M  Ops/s/t:  30.45M
            ^ Note: No contention -- measures speed of immediately dequeueing the item that was just enqueued
     2   threads:  Avg: 1.1448us  Range: [0.4117us, 1.2515us]  Ops/s:   1.75M  Ops/s/t: 873.54k
     4   threads:  Avg: 4.6480us  Range: [4.0236us, 4.8069us]  Ops/s: 860.58k  Ops/s/t: 215.15k
     8   threads:  Avg: 0.0198ms  Range: [0.0190ms, 0.0208ms]  Ops/s: 403.50k  Ops/s/t:  50.44k
     32  threads:  Avg: 0.4098ms  Range: [0.4018ms, 0.4142ms]  Ops/s:  78.09k  Ops/s/t:   2.44k
     Operations per second per thread (weighted average):   2.50M

  > tbb::concurrent_queue
     1    thread:  Avg: 0.0224us  Range: [0.0224us, 0.0224us]  Ops/s:  44.66M  Ops/s/t:  44.66M
            ^ Note: No contention -- measures speed of immediately dequeueing the item that was just enqueued
     2   threads:  Avg: 0.5825us  Range: [0.5799us, 0.5844us]  Ops/s:   3.43M  Ops/s/t:   1.72M
     4   threads:  Avg: 2.5531us  Range: [2.1310us, 2.6899us]  Ops/s:   1.57M  Ops/s/t: 391.67k
     8   threads:  Avg: 0.0101ms  Range: [0.0101ms, 0.0102ms]  Ops/s: 789.08k  Ops/s/t:  98.64k
     32  threads:  Avg: 0.1343ms  Range: [0.1311ms, 0.1371ms]  Ops/s: 238.28k  Ops/s/t:   7.45k
     Operations per second per thread (weighted average):   3.74M

  > SimpleLockFreeQueue
     1    thread:  Avg: 0.0429us  Range: [0.0429us, 0.0429us]  Ops/s:  23.30M  Ops/s/t:  23.30M
            ^ Note: No contention -- measures speed of immediately dequeueing the item that was just enqueued
     2   threads:  Avg: 0.9275us  Range: [0.9030us, 0.9385us]  Ops/s:   2.16M  Ops/s/t:   1.08M
     4   threads:  Avg: 5.2883us  Range: [5.2680us, 5.2959us]  Ops/s: 756.39k  Ops/s/t: 189.10k
     8   threads:  Avg: 0.0297ms  Range: [0.0267ms, 0.0315ms]  Ops/s: 269.42k  Ops/s/t:  33.68k
     32  threads:  Avg: 0.6158ms  Range: [0.6028ms, 0.6242ms]  Ops/s:  51.96k  Ops/s/t:   1.62k
     Operations per second per thread (weighted average):   1.96M

  > LockBasedQueue
     1    thread:  Avg: 0.0503us  Range: [0.0503us, 0.0504us]  Ops/s:  19.86M  Ops/s/t:  19.86M
            ^ Note: No contention -- measures speed of immediately dequeueing the item that was just enqueued
     2   threads:  Avg: 0.4039us  Range: [0.3996us, 0.4073us]  Ops/s:   4.95M  Ops/s/t:   2.48M
     4   threads:  Avg: 4.8038us  Range: [4.0986us, 5.4712us]  Ops/s: 832.68k  Ops/s/t: 208.17k
     8   threads:  Avg: 0.0324ms  Range: [0.0286ms, 0.0335ms]  Ops/s: 246.75k  Ops/s/t:  30.84k
     32  threads:  Avg: 0.3925ms  Range: [0.3335ms, 0.4503ms]  Ops/s:  81.52k  Ops/s/t:   2.55k
     Operations per second per thread (weighted average):   1.85M

  > std::queue
     1    thread:  Avg: 2.7071ns  Range: [2.7069ns, 2.7074ns]  Ops/s: 369.39M  Ops/s/t: 369.39M
            ^ Note: No contention -- measures speed of immediately dequeueing the item that was just enqueued
     Operations per second per thread (weighted average): 369.39M

heavy concurrent:
  (Measures the average operation speed with many threads under heavy load)
  > moodycamel::ConcurrentQueue
      Without tokens
         2   threads:  Avg: 0.1211us  Range: [0.1163us, 0.1233us]  Ops/s:  16.51M  Ops/s/t:   8.26M
         3   threads:  Avg: 0.4467us  Range: [0.3247us, 0.4672us]  Ops/s:   6.72M  Ops/s/t:   2.24M
         4   threads:  Avg: 0.9646us  Range: [0.8831us, 1.0081us]  Ops/s:   4.15M  Ops/s/t:   1.04M
         8   threads:  Avg: 2.3965us  Range: [2.3389us, 2.4271us]  Ops/s:   3.34M  Ops/s/t: 417.27k
         12  threads:  Avg: 4.2654us  Range: [4.2042us, 4.3185us]  Ops/s:   2.81M  Ops/s/t: 234.44k
         16  threads:  Avg: 6.5713us  Range: [6.1844us, 6.6608us]  Ops/s:   2.43M  Ops/s/t: 152.18k
         32  threads:  Avg: 0.0330ms  Range: [0.0318ms, 0.0333ms]  Ops/s: 970.98k  Ops/s/t:  30.34k
         48  threads:  Avg: 0.0612ms  Range: [0.0592ms, 0.0625ms]  Ops/s: 784.29k  Ops/s/t:  16.34k
         Operations per second per thread (weighted average): 731.94k

      With tokens
         2   threads:  Avg: 0.0491us  Range: [0.0434us, 0.0508us]  Ops/s:  40.77M  Ops/s/t:  20.39M
         3   threads:  Avg: 0.1539us  Range: [0.1536us, 0.1541us]  Ops/s:  19.50M  Ops/s/t:   6.50M
         4   threads:  Avg: 0.4153us  Range: [0.3947us, 0.4259us]  Ops/s:   9.63M  Ops/s/t:   2.41M
         8   threads:  Avg: 0.7587us  Range: [0.5327us, 0.8894us]  Ops/s:  10.54M  Ops/s/t:   1.32M
         12  threads:  Avg: 1.3762us  Range: [1.2403us, 1.5247us]  Ops/s:   8.72M  Ops/s/t: 726.65k
         16  threads:  Avg: 2.6196us  Range: [2.2556us, 2.8414us]  Ops/s:   6.11M  Ops/s/t: 381.74k
         32  threads:  Avg: 0.0162ms  Range: [9.3597us, 0.0203ms]  Ops/s:   1.97M  Ops/s/t:  61.66k
         48  threads:  Avg: 0.0143ms  Range: [0.0111ms, 0.0173ms]  Ops/s:   3.35M  Ops/s/t:  69.85k
         Operations per second per thread (weighted average):   1.91M

  > boost::lockfree::queue
     2   threads:  Avg: 1.2196us  Range: [1.2159us, 1.2220us]  Ops/s:   1.64M  Ops/s/t: 819.95k
     3   threads:  Avg: 2.2890us  Range: [2.2667us, 2.3046us]  Ops/s:   1.31M  Ops/s/t: 436.88k
     4   threads:  Avg: 2.9693us  Range: [2.9229us, 2.9822us]  Ops/s:   1.35M  Ops/s/t: 336.78k
     8   threads:  Avg: 8.9729us  Range: [8.8928us, 9.0693us]  Ops/s: 891.57k  Ops/s/t: 111.45k
     12  threads:  Avg: 0.0208ms  Range: [0.0205ms, 0.0209ms]  Ops/s: 577.87k  Ops/s/t:  48.16k
     16  threads:  Avg: 0.0369ms  Range: [0.0355ms, 0.0376ms]  Ops/s: 433.75k  Ops/s/t:  27.11k
     32  threads:  Avg: 0.3347ms  Range: [0.3205ms, 0.3437ms]  Ops/s:  95.62k  Ops/s/t:   2.99k
     48  threads:  Avg: 0.7204ms  Range: [0.7067ms, 0.7367ms]  Ops/s:  66.63k  Ops/s/t:   1.39k
     Operations per second per thread (weighted average): 114.43k

  > tbb::concurrent_queue
     2   threads:  Avg: 0.5035us  Range: [0.4944us, 0.5064us]  Ops/s:   3.97M  Ops/s/t:   1.99M
     3   threads:  Avg: 1.1369us  Range: [1.1313us, 1.1402us]  Ops/s:   2.64M  Ops/s/t: 879.61k
     4   threads:  Avg: 1.8101us  Range: [1.8060us, 1.8148us]  Ops/s:   2.21M  Ops/s/t: 552.45k
     8   threads:  Avg: 8.2873us  Range: [7.6817us, 8.3997us]  Ops/s: 965.34k  Ops/s/t: 120.67k
     12  threads:  Avg: 0.0182ms  Range: [0.0174ms, 0.0187ms]  Ops/s: 657.96k  Ops/s/t:  54.83k
     16  threads:  Avg: 0.0403ms  Range: [0.0354ms, 0.0419ms]  Ops/s: 397.38k  Ops/s/t:  24.84k
     32  threads:  Avg: 0.1491ms  Range: [0.1353ms, 0.1525ms]  Ops/s: 214.58k  Ops/s/t:   6.71k
     48  threads:  Avg: 0.3679ms  Range: [0.3642ms, 0.3710ms]  Ops/s: 130.46k  Ops/s/t:   2.72k
     Operations per second per thread (weighted average): 218.55k

  > SimpleLockFreeQueue
     2   threads:  Avg: 0.8196us  Range: [0.3717us, 0.8868us]  Ops/s:   2.44M  Ops/s/t:   1.22M
     3   threads:  Avg: 1.6837us  Range: [1.6827us, 1.6845us]  Ops/s:   1.78M  Ops/s/t: 593.94k
     4   threads:  Avg: 4.5087us  Range: [3.7115us, 4.9618us]  Ops/s: 887.18k  Ops/s/t: 221.79k
     8   threads:  Avg: 0.0222ms  Range: [0.0199ms, 0.0232ms]  Ops/s: 360.16k  Ops/s/t:  45.02k
     12  threads:  Avg: 0.0532ms  Range: [0.0515ms, 0.0538ms]  Ops/s: 225.48k  Ops/s/t:  18.79k
     16  threads:  Avg: 0.0920ms  Range: [0.0743ms, 0.0959ms]  Ops/s: 174.00k  Ops/s/t:  10.88k
     32  threads:  Avg: 0.4328ms  Range: [0.3996ms, 0.4452ms]  Ops/s:  73.94k  Ops/s/t:   2.31k
     48  threads:  Avg: 0.8777ms  Range: [0.8347ms, 0.8909ms]  Ops/s:  54.69k  Ops/s/t:   1.14k
     Operations per second per thread (weighted average): 123.28k

  > LockBasedQueue
     2   threads:  Avg: 0.3872us  Range: [0.3804us, 0.3916us]  Ops/s:   5.17M  Ops/s/t:   2.58M
     3   threads:  Avg: 3.1674us  Range: [2.7845us, 3.3797us]  Ops/s: 947.16k  Ops/s/t: 315.72k
     4   threads:  Avg: 6.6254us  Range: [6.4227us, 6.7718us]  Ops/s: 603.74k  Ops/s/t: 150.93k
     8   threads:  Avg: 0.0247ms  Range: [0.0213ms, 0.0259ms]  Ops/s: 323.74k  Ops/s/t:  40.47k
     12  threads:  Avg: 0.0514ms  Range: [0.0499ms, 0.0524ms]  Ops/s: 233.56k  Ops/s/t:  19.46k
     16  threads:  Avg: 0.0895ms  Range: [0.0873ms, 0.0908ms]  Ops/s: 178.78k  Ops/s/t:  11.17k
     32  threads:  Avg: 0.3723ms  Range: [0.3626ms, 0.3779ms]  Ops/s:  85.95k  Ops/s/t:   2.69k
     48  threads:  Avg: 0.7242ms  Range: [0.7031ms, 0.7378ms]  Ops/s:  66.28k  Ops/s/t:   1.38k
     Operations per second per thread (weighted average): 169.58k

  > std::queue
     (skipping, benchmark not supported...)

Overall average operations per second per thread (where higher-concurrency runs have more weight):
(Take this summary with a grain of salt -- look at the individual benchmark results for a much
better idea of how the queues measure up to each other):
    moodycamel::ConcurrentQueue (including bulk):  18.50M
    boost::lockfree::queue:   3.27M
    tbb::concurrent_queue:   4.21M
    SimpleLockFreeQueue:   2.90M
    LockBasedQueue:   1.12M
    std::queue (single thread only): 436.10M

```

#### 64-bit 8-core, Fedora 19, g++ 4.81 (a Linode VM)

```
Running 64-bit benchmarks on a       Intel(R) Xeon(R) CPU E5-2650L 0 @ 1.80GHz
    (precise mode)
Note that these are synthetic benchmarks. Take them with a grain of salt.

Legend:
    'Avg':     Average time taken per operation, normalized to be per thread
    'Range':   The minimum and maximum times taken per operation (per thread)
    'Ops/s':   Overall operations per second
    'Ops/s/t': Operations per second per thread (inverse of 'Avg')
    Operations include those that fail (e.g. because the queue is empty).
    Each logical enqueue/dequeue counts as an individual operation when in bulk.

balanced:
  (Measures the average operation speed with multiple symmetrical threads
  under reasonable load -- small random intervals between accesses)
  > moodycamel::ConcurrentQueue
      Without tokens
         2   threads:  Avg: 3.9753us  Range: [3.9540us, 3.9879us]  Ops/s: 503.11k  Ops/s/t: 251.55k
         3   threads:  Avg: 6.0741us  Range: [6.0383us, 6.1039us]  Ops/s: 493.90k  Ops/s/t: 164.63k
         4   threads:  Avg: 8.3842us  Range: [8.3230us, 8.4119us]  Ops/s: 477.09k  Ops/s/t: 119.27k
         8   threads:  Avg: 0.0199ms  Range: [0.0196ms, 0.0200ms]  Ops/s: 402.49k  Ops/s/t:  50.31k
         12  threads:  Avg: 0.0290ms  Range: [0.0288ms, 0.0292ms]  Ops/s: 414.03k  Ops/s/t:  34.50k
         16  threads:  Avg: 0.0403ms  Range: [0.0394ms, 0.0408ms]  Ops/s: 397.36k  Ops/s/t:  24.84k
         Operations per second per thread (weighted average):  80.36k

      With tokens
         2   threads:  Avg: 3.8409us  Range: [3.8075us, 3.8688us]  Ops/s: 520.71k  Ops/s/t: 260.35k
         3   threads:  Avg: 5.8761us  Range: [5.8086us, 5.9162us]  Ops/s: 510.55k  Ops/s/t: 170.18k
         4   threads:  Avg: 7.9556us  Range: [7.8602us, 7.9969us]  Ops/s: 502.79k  Ops/s/t: 125.70k
         8   threads:  Avg: 0.0179ms  Range: [0.0177ms, 0.0180ms]  Ops/s: 447.20k  Ops/s/t:  55.90k
         12  threads:  Avg: 0.0270ms  Range: [0.0267ms, 0.0272ms]  Ops/s: 443.80k  Ops/s/t:  36.98k
         16  threads:  Avg: 0.0364ms  Range: [0.0357ms, 0.0370ms]  Ops/s: 440.16k  Ops/s/t:  27.51k
         Operations per second per thread (weighted average):  84.89k

  > boost::lockfree::queue
     2   threads:  Avg: 4.1898us  Range: [4.1386us, 4.2131us]  Ops/s: 477.35k  Ops/s/t: 238.68k
     3   threads:  Avg: 6.3612us  Range: [6.3393us, 6.3831us]  Ops/s: 471.61k  Ops/s/t: 157.20k
     4   threads:  Avg: 8.6152us  Range: [8.5799us, 8.6417us]  Ops/s: 464.30k  Ops/s/t: 116.07k
     8   threads:  Avg: 0.0199ms  Range: [0.0198ms, 0.0200ms]  Ops/s: 401.50k  Ops/s/t:  50.19k
     12  threads:  Avg: 0.0286ms  Range: [0.0278ms, 0.0290ms]  Ops/s: 419.55k  Ops/s/t:  34.96k
     16  threads:  Avg: 0.0369ms  Range: [0.0363ms, 0.0374ms]  Ops/s: 433.47k  Ops/s/t:  27.09k
     Operations per second per thread (weighted average):  78.59k

  > tbb::concurrent_queue
     2   threads:  Avg: 4.0596us  Range: [4.0407us, 4.0735us]  Ops/s: 492.65k  Ops/s/t: 246.33k
     3   threads:  Avg: 6.1844us  Range: [6.1648us, 6.2013us]  Ops/s: 485.09k  Ops/s/t: 161.70k
     4   threads:  Avg: 8.3020us  Range: [8.2448us, 8.3411us]  Ops/s: 481.81k  Ops/s/t: 120.45k
     8   threads:  Avg: 0.0189ms  Range: [0.0184ms, 0.0190ms]  Ops/s: 423.50k  Ops/s/t:  52.94k
     12  threads:  Avg: 0.0270ms  Range: [0.0262ms, 0.0275ms]  Ops/s: 444.25k  Ops/s/t:  37.02k
     16  threads:  Avg: 0.0381ms  Range: [0.0375ms, 0.0384ms]  Ops/s: 420.40k  Ops/s/t:  26.27k
     Operations per second per thread (weighted average):  81.12k

  > SimpleLockFreeQueue
     2   threads:  Avg: 4.1909us  Range: [4.1500us, 4.2127us]  Ops/s: 477.22k  Ops/s/t: 238.61k
     3   threads:  Avg: 6.4012us  Range: [6.3794us, 6.4177us]  Ops/s: 468.67k  Ops/s/t: 156.22k
     4   threads:  Avg: 8.5954us  Range: [8.5562us, 8.6192us]  Ops/s: 465.37k  Ops/s/t: 116.34k
     8   threads:  Avg: 0.0200ms  Range: [0.0197ms, 0.0201ms]  Ops/s: 400.75k  Ops/s/t:  50.09k
     12  threads:  Avg: 0.0287ms  Range: [0.0285ms, 0.0289ms]  Ops/s: 418.09k  Ops/s/t:  34.84k
     16  threads:  Avg: 0.0394ms  Range: [0.0390ms, 0.0397ms]  Ops/s: 405.60k  Ops/s/t:  25.35k
     Operations per second per thread (weighted average):  78.02k

  > LockBasedQueue
     2   threads:  Avg: 4.2615us  Range: [4.2250us, 4.2780us]  Ops/s: 469.31k  Ops/s/t: 234.66k
     3   threads:  Avg: 6.4749us  Range: [6.4288us, 6.5182us]  Ops/s: 463.33k  Ops/s/t: 154.44k
     4   threads:  Avg: 9.0602us  Range: [8.7692us, 9.2544us]  Ops/s: 441.49k  Ops/s/t: 110.37k
     8   threads:  Avg: 0.0274ms  Range: [0.0263ms, 0.0281ms]  Ops/s: 292.30k  Ops/s/t:  36.54k
     12  threads:  Avg: 0.0390ms  Range: [0.0368ms, 0.0405ms]  Ops/s: 308.01k  Ops/s/t:  25.67k
     16  threads:  Avg: 0.0632ms  Range: [0.0607ms, 0.0661ms]  Ops/s: 253.15k  Ops/s/t:  15.82k
     Operations per second per thread (weighted average):  69.67k

  > std::queue
     (skipping, benchmark not supported...)

only enqueue:
  (Measures the average operation speed when all threads are producers)
  > moodycamel::ConcurrentQueue
      Without tokens
         1    thread:  Avg: 0.0258us  Range: [0.0257us, 0.0259us]  Ops/s:  38.73M  Ops/s/t:  38.73M
         2   threads:  Avg: 0.0437us  Range: [0.0434us, 0.0439us]  Ops/s:  45.76M  Ops/s/t:  22.88M
         4   threads:  Avg: 0.0841us  Range: [0.0840us, 0.0842us]  Ops/s:  47.54M  Ops/s/t:  11.89M
         8   threads:  Avg: 0.1742us  Range: [0.1728us, 0.1766us]  Ops/s:  45.92M  Ops/s/t:   5.74M
         Operations per second per thread (weighted average):  15.34M

      With tokens
         1    thread:  Avg: 0.0170us  Range: [0.0169us, 0.0171us]  Ops/s:  58.70M  Ops/s/t:  58.70M
         2   threads:  Avg: 0.0341us  Range: [0.0340us, 0.0342us]  Ops/s:  58.67M  Ops/s/t:  29.33M
         4   threads:  Avg: 0.0673us  Range: [0.0671us, 0.0674us]  Ops/s:  59.47M  Ops/s/t:  14.87M
         8   threads:  Avg: 0.1324us  Range: [0.1315us, 0.1330us]  Ops/s:  60.43M  Ops/s/t:   7.55M
         Operations per second per thread (weighted average):  20.89M

  > boost::lockfree::queue
     1    thread:  Avg: 0.0890us  Range: [0.0882us, 0.0893us]  Ops/s:  11.23M  Ops/s/t:  11.23M
     2   threads:  Avg: 0.5708us  Range: [0.4042us, 0.6624us]  Ops/s:   3.50M  Ops/s/t:   1.75M
     4   threads:  Avg: 4.1473us  Range: [4.0496us, 4.2130us]  Ops/s: 964.49k  Ops/s/t: 241.12k
     8   threads:  Avg: 0.0223ms  Range: [0.0171ms, 0.0239ms]  Ops/s: 358.37k  Ops/s/t:  44.80k
     Operations per second per thread (weighted average):   1.98M

  > tbb::concurrent_queue
     1    thread:  Avg: 0.0551us  Range: [0.0550us, 0.0553us]  Ops/s:  18.15M  Ops/s/t:  18.15M
     2   threads:  Avg: 0.4317us  Range: [0.4103us, 0.4609us]  Ops/s:   4.63M  Ops/s/t:   2.32M
     4   threads:  Avg: 2.3467us  Range: [2.3320us, 2.3652us]  Ops/s:   1.70M  Ops/s/t: 426.12k
     8   threads:  Avg: 0.0116ms  Range: [0.0113ms, 0.0117ms]  Ops/s: 692.01k  Ops/s/t:  86.50k
     Operations per second per thread (weighted average):   3.11M

  > SimpleLockFreeQueue
     1    thread:  Avg: 0.0896us  Range: [0.0890us, 0.0899us]  Ops/s:  11.16M  Ops/s/t:  11.16M
     2   threads:  Avg: 0.4875us  Range: [0.3707us, 0.5435us]  Ops/s:   4.10M  Ops/s/t:   2.05M
     4   threads:  Avg: 2.6748us  Range: [2.0577us, 2.8082us]  Ops/s:   1.50M  Ops/s/t: 373.86k
     8   threads:  Avg: 0.0126ms  Range: [0.0122ms, 0.0128ms]  Ops/s: 635.70k  Ops/s/t:  79.46k
     Operations per second per thread (weighted average):   2.08M

  > LockBasedQueue
     1    thread:  Avg: 0.0928us  Range: [0.0926us, 0.0930us]  Ops/s:  10.77M  Ops/s/t:  10.77M
     2   threads:  Avg: 0.6854us  Range: [0.6488us, 0.7116us]  Ops/s:   2.92M  Ops/s/t:   1.46M
     4   threads:  Avg: 3.3918us  Range: [3.3343us, 3.4234us]  Ops/s:   1.18M  Ops/s/t: 294.83k
     8   threads:  Avg: 0.0258ms  Range: [0.0252ms, 0.0261ms]  Ops/s: 309.97k  Ops/s/t:  38.75k
     Operations per second per thread (weighted average):   1.87M

  > std::queue
     1    thread:  Avg: 9.8436ns  Range: [9.7873ns, 9.9017ns]  Ops/s: 101.59M  Ops/s/t: 101.59M
     Operations per second per thread (weighted average): 101.59M

only enqueue (pre-allocated):
  (Measures the average operation speed when all threads are producers,
  and the queue has been stretched out first)
  > moodycamel::ConcurrentQueue
      Without tokens
         1    thread:  Avg: 0.0217us  Range: [0.0215us, 0.0218us]  Ops/s:  46.15M  Ops/s/t:  46.15M
         2   threads:  Avg: 0.0406us  Range: [0.0401us, 0.0410us]  Ops/s:  49.23M  Ops/s/t:  24.61M
         4   threads:  Avg: 0.1207us  Range: [0.1190us, 0.1219us]  Ops/s:  33.14M  Ops/s/t:   8.28M
         8   threads:  Avg: 0.4243us  Range: [0.4208us, 0.4259us]  Ops/s:  18.86M  Ops/s/t:   2.36M
         Operations per second per thread (weighted average):  14.39M

      With tokens
         1    thread:  Avg: 0.0120us  Range: [0.0120us, 0.0120us]  Ops/s:  83.25M  Ops/s/t:  83.25M
         2   threads:  Avg: 0.0254us  Range: [0.0253us, 0.0254us]  Ops/s:  78.78M  Ops/s/t:  39.39M
         4   threads:  Avg: 0.0504us  Range: [0.0502us, 0.0505us]  Ops/s:  79.42M  Ops/s/t:  19.85M
         8   threads:  Avg: 0.1078us  Range: [0.1020us, 0.1092us]  Ops/s:  74.20M  Ops/s/t:   9.27M
         Operations per second per thread (weighted average):  28.29M

  > boost::lockfree::queue
     1    thread:  Avg: 0.0558us  Range: [0.0557us, 0.0559us]  Ops/s:  17.93M  Ops/s/t:  17.93M
     2   threads:  Avg: 0.5962us  Range: [0.5408us, 0.6396us]  Ops/s:   3.35M  Ops/s/t:   1.68M
     4   threads:  Avg: 4.5705us  Range: [4.4259us, 4.6954us]  Ops/s: 875.18k  Ops/s/t: 218.80k
     8   threads:  Avg: 0.0196ms  Range: [4.1197us, 0.0236ms]  Ops/s: 408.32k  Ops/s/t:  51.04k
     Operations per second per thread (weighted average):   2.88M

  > tbb::concurrent_queue
     1    thread:  Avg: 0.0581us  Range: [0.0576us, 0.0583us]  Ops/s:  17.21M  Ops/s/t:  17.21M
     2   threads:  Avg: 0.4374us  Range: [0.4017us, 0.4489us]  Ops/s:   4.57M  Ops/s/t:   2.29M
     4   threads:  Avg: 2.3334us  Range: [2.2983us, 2.3511us]  Ops/s:   1.71M  Ops/s/t: 428.56k
     8   threads:  Avg: 0.0116ms  Range: [0.0114ms, 0.0117ms]  Ops/s: 692.48k  Ops/s/t:  86.56k
     Operations per second per thread (weighted average):   2.97M

  > SimpleLockFreeQueue
     1    thread:  Avg: 0.0768us  Range: [0.0767us, 0.0769us]  Ops/s:  13.02M  Ops/s/t:  13.02M
     2   threads:  Avg: 0.5945us  Range: [0.4558us, 0.6580us]  Ops/s:   3.36M  Ops/s/t:   1.68M
     4   threads:  Avg: 3.3484us  Range: [2.1610us, 3.7927us]  Ops/s:   1.19M  Ops/s/t: 298.65k
     8   threads:  Avg: 0.0181ms  Range: [0.0175ms, 0.0185ms]  Ops/s: 441.55k  Ops/s/t:  55.19k
     Operations per second per thread (weighted average):   2.23M

  > LockBasedQueue
     1    thread:  Avg: 0.0930us  Range: [0.0927us, 0.0932us]  Ops/s:  10.76M  Ops/s/t:  10.76M
     2   threads:  Avg: 0.6540us  Range: [0.6248us, 0.6744us]  Ops/s:   3.06M  Ops/s/t:   1.53M
     4   threads:  Avg: 3.4617us  Range: [3.3743us, 3.4877us]  Ops/s:   1.16M  Ops/s/t: 288.87k
     8   threads:  Avg: 0.0227ms  Range: [0.0218ms, 0.0232ms]  Ops/s: 352.22k  Ops/s/t:  44.03k
     Operations per second per thread (weighted average):   1.88M

  > std::queue
     1    thread:  Avg: 0.0100us  Range: [9.9960ns, 0.0100us]  Ops/s:  99.91M  Ops/s/t:  99.91M
     Operations per second per thread (weighted average):  99.91M

only enqueue bulk:
  (Measures the average speed of enqueueing an item in bulk when all threads are producers)
  > moodycamel::ConcurrentQueue
      Without tokens
         1    thread:  Avg: 8.5403ns  Range: [8.4784ns, 8.5612ns]  Ops/s: 117.09M  Ops/s/t: 117.09M
         2   threads:  Avg: 0.0160us  Range: [0.0160us, 0.0161us]  Ops/s: 124.90M  Ops/s/t:  62.45M
         4   threads:  Avg: 0.0270us  Range: [0.0268us, 0.0273us]  Ops/s: 148.05M  Ops/s/t:  37.01M
         8   threads:  Avg: 0.0474us  Range: [0.0457us, 0.0485us]  Ops/s: 168.80M  Ops/s/t:  21.10M
         Operations per second per thread (weighted average):  46.82M

      With tokens
         1    thread:  Avg: 8.1486ns  Range: [8.1126ns, 8.1773ns]  Ops/s: 122.72M  Ops/s/t: 122.72M
         2   threads:  Avg: 0.0166us  Range: [0.0162us, 0.0169us]  Ops/s: 120.42M  Ops/s/t:  60.21M
         4   threads:  Avg: 0.0334us  Range: [0.0330us, 0.0335us]  Ops/s: 119.91M  Ops/s/t:  29.98M
         8   threads:  Avg: 0.0720us  Range: [0.0623us, 0.0745us]  Ops/s: 111.14M  Ops/s/t:  13.89M
         Operations per second per thread (weighted average):  42.40M

  > boost::lockfree::queue
     (skipping, benchmark not supported...)

  > tbb::concurrent_queue
     (skipping, benchmark not supported...)

  > SimpleLockFreeQueue
     (skipping, benchmark not supported...)

  > LockBasedQueue
     (skipping, benchmark not supported...)

  > std::queue
     (skipping, benchmark not supported...)

only enqueue bulk (pre-allocated):
  (Measures the average speed of enqueueing an item in bulk when all threads are producers,
  and the queue has been stretched out first)
  > moodycamel::ConcurrentQueue
      Without tokens
         1    thread:  Avg: 8.6011ns  Range: [8.5285ns, 8.6324ns]  Ops/s: 116.26M  Ops/s/t: 116.26M
         2   threads:  Avg: 0.0165us  Range: [0.0164us, 0.0165us]  Ops/s: 121.30M  Ops/s/t:  60.65M
         4   threads:  Avg: 0.0337us  Range: [0.0334us, 0.0339us]  Ops/s: 118.75M  Ops/s/t:  29.69M
         8   threads:  Avg: 0.0727us  Range: [0.0708us, 0.0746us]  Ops/s: 110.04M  Ops/s/t:  13.76M
         Operations per second per thread (weighted average):  41.46M

      With tokens
         1    thread:  Avg: 8.1239ns  Range: [8.1000ns, 8.1366ns]  Ops/s: 123.09M  Ops/s/t: 123.09M
         2   threads:  Avg: 0.0162us  Range: [0.0161us, 0.0163us]  Ops/s: 123.57M  Ops/s/t:  61.79M
         4   threads:  Avg: 0.0327us  Range: [0.0325us, 0.0328us]  Ops/s: 122.51M  Ops/s/t:  30.63M
         8   threads:  Avg: 0.0650us  Range: [0.0643us, 0.0661us]  Ops/s: 123.17M  Ops/s/t:  15.40M
         Operations per second per thread (weighted average):  43.53M

  > boost::lockfree::queue
     (skipping, benchmark not supported...)

  > tbb::concurrent_queue
     (skipping, benchmark not supported...)

  > SimpleLockFreeQueue
     (skipping, benchmark not supported...)

  > LockBasedQueue
     (skipping, benchmark not supported...)

  > std::queue
     (skipping, benchmark not supported...)

only dequeue:
  (Measures the average operation speed when all threads are consumers)
  > moodycamel::ConcurrentQueue
      Without tokens
         1    thread:  Avg: 0.0592us  Range: [0.0591us, 0.0593us]  Ops/s:  16.89M  Ops/s/t:  16.89M
         2   threads:  Avg: 0.4769us  Range: [0.4276us, 0.5157us]  Ops/s:   4.19M  Ops/s/t:   2.10M
         4   threads:  Avg: 1.8856us  Range: [1.8214us, 2.0263us]  Ops/s:   2.12M  Ops/s/t: 530.33k
         8   threads:  Avg: 6.9621us  Range: [6.8872us, 7.0058us]  Ops/s:   1.15M  Ops/s/t: 143.64k
         Operations per second per thread (weighted average):   2.94M

      With tokens
         1    thread:  Avg: 0.0522us  Range: [0.0521us, 0.0523us]  Ops/s:  19.15M  Ops/s/t:  19.15M
         2   threads:  Avg: 0.1078us  Range: [0.1074us, 0.1080us]  Ops/s:  18.55M  Ops/s/t:   9.28M
         4   threads:  Avg: 0.2205us  Range: [0.2178us, 0.2233us]  Ops/s:  18.14M  Ops/s/t:   4.53M
         8   threads:  Avg: 0.4826us  Range: [0.4639us, 0.5050us]  Ops/s:  16.58M  Ops/s/t:   2.07M
         Operations per second per thread (weighted average):   6.52M

  > boost::lockfree::queue
     1    thread:  Avg: 0.0463us  Range: [0.0462us, 0.0463us]  Ops/s:  21.62M  Ops/s/t:  21.62M
     2   threads:  Avg: 0.5207us  Range: [0.4642us, 0.5516us]  Ops/s:   3.84M  Ops/s/t:   1.92M
     4   threads:  Avg: 2.6303us  Range: [0.9500us, 3.1910us]  Ops/s:   1.52M  Ops/s/t: 380.19k
     8   threads:  Avg: 0.0174ms  Range: [0.0167ms, 0.0177ms]  Ops/s: 460.26k  Ops/s/t:  57.53k
     Operations per second per thread (weighted average):   3.49M

  > tbb::concurrent_queue
     1    thread:  Avg: 0.0345us  Range: [0.0344us, 0.0345us]  Ops/s:  28.99M  Ops/s/t:  28.99M
     2   threads:  Avg: 0.3395us  Range: [0.2961us, 0.3672us]  Ops/s:   5.89M  Ops/s/t:   2.95M
     4   threads:  Avg: 2.0345us  Range: [1.8242us, 2.1027us]  Ops/s:   1.97M  Ops/s/t: 491.52k
     8   threads:  Avg: 0.0113ms  Range: [0.0110ms, 0.0115ms]  Ops/s: 707.21k  Ops/s/t:  88.40k
     Operations per second per thread (weighted average):   4.75M

  > SimpleLockFreeQueue
     1    thread:  Avg: 0.0405us  Range: [0.0404us, 0.0406us]  Ops/s:  24.69M  Ops/s/t:  24.69M
     2   threads:  Avg: 0.5289us  Range: [0.2447us, 0.5972us]  Ops/s:   3.78M  Ops/s/t:   1.89M
     4   threads:  Avg: 3.8209us  Range: [3.4123us, 4.0981us]  Ops/s:   1.05M  Ops/s/t: 261.72k
     8   threads:  Avg: 0.0192ms  Range: [0.0180ms, 0.0199ms]  Ops/s: 417.23k  Ops/s/t:  52.15k
     Operations per second per thread (weighted average):   3.87M

  > LockBasedQueue
     1    thread:  Avg: 0.0747us  Range: [0.0744us, 0.0748us]  Ops/s:  13.39M  Ops/s/t:  13.39M
     2   threads:  Avg: 1.6140us  Range: [0.9652us, 1.8031us]  Ops/s:   1.24M  Ops/s/t: 619.57k
     4   threads:  Avg: 7.4764us  Range: [7.3353us, 7.5629us]  Ops/s: 535.02k  Ops/s/t: 133.75k
     8   threads:  Avg: 0.0322ms  Range: [0.0110ms, 0.0442ms]  Ops/s: 248.58k  Ops/s/t:  31.07k
     Operations per second per thread (weighted average):   2.02M

  > std::queue
     1    thread:  Avg: 5.3382ns  Range: [5.3169ns, 5.3460ns]  Ops/s: 187.33M  Ops/s/t: 187.33M
     Operations per second per thread (weighted average): 187.33M

only dequeue bulk:
  (Measures the average speed of dequeueing an item in bulk when all threads are consumers)
  > moodycamel::ConcurrentQueue
      Without tokens
         1    thread:  Avg: 2.1588ns  Range: [2.1556ns, 2.1642ns]  Ops/s: 463.22M  Ops/s/t: 463.22M
         2   threads:  Avg: 6.9126ns  Range: [6.8439ns, 6.9818ns]  Ops/s: 289.33M  Ops/s/t: 144.66M
         4   threads:  Avg: 0.0343us  Range: [0.0332us, 0.0355us]  Ops/s: 116.73M  Ops/s/t:  29.18M
         8   threads:  Avg: 0.1784us  Range: [0.1752us, 0.1825us]  Ops/s:  44.85M  Ops/s/t:   5.61M
         Operations per second per thread (weighted average): 102.45M

      With tokens
         1    thread:  Avg: 1.6079ns  Range: [1.6044ns, 1.6112ns]  Ops/s: 621.93M  Ops/s/t: 621.93M
         2   threads:  Avg: 3.4957ns  Range: [3.4832ns, 3.5004ns]  Ops/s: 572.13M  Ops/s/t: 286.07M
         4   threads:  Avg: 7.1365ns  Range: [7.1169ns, 7.1531ns]  Ops/s: 560.50M  Ops/s/t: 140.12M
         8   threads:  Avg: 0.0163us  Range: [0.0161us, 0.0167us]  Ops/s: 490.03M  Ops/s/t:  61.25M
         Operations per second per thread (weighted average): 204.34M

  > boost::lockfree::queue
     (skipping, benchmark not supported...)

  > tbb::concurrent_queue
     (skipping, benchmark not supported...)

  > SimpleLockFreeQueue
     (skipping, benchmark not supported...)

  > LockBasedQueue
     (skipping, benchmark not supported...)

  > std::queue
     (skipping, benchmark not supported...)

mostly enqueue:
  (Measures the average operation speed when most threads are enqueueing)
  > moodycamel::ConcurrentQueue
      Without tokens
         2   threads:  Avg: 0.1172us  Range: [0.1141us, 0.1188us]  Ops/s:  17.07M  Ops/s/t:   8.54M
         4   threads:  Avg: 0.1784us  Range: [0.1739us, 0.1809us]  Ops/s:  22.42M  Ops/s/t:   5.61M
         8   threads:  Avg: 0.5951us  Range: [0.5256us, 0.6458us]  Ops/s:  13.44M  Ops/s/t:   1.68M
         Operations per second per thread (weighted average):   4.49M

      With tokens
         2   threads:  Avg: 0.1097us  Range: [0.1057us, 0.1120us]  Ops/s:  18.23M  Ops/s/t:   9.11M
         4   threads:  Avg: 0.1336us  Range: [0.1324us, 0.1343us]  Ops/s:  29.93M  Ops/s/t:   7.48M
         8   threads:  Avg: 0.4040us  Range: [0.2980us, 0.5020us]  Ops/s:  19.80M  Ops/s/t:   2.48M
         Operations per second per thread (weighted average):   5.58M

  > boost::lockfree::queue
     2   threads:  Avg: 0.2114us  Range: [0.1789us, 0.2367us]  Ops/s:   9.46M  Ops/s/t:   4.73M
     4   threads:  Avg: 2.7809us  Range: [2.7316us, 2.8150us]  Ops/s:   1.44M  Ops/s/t: 359.60k
     8   threads:  Avg: 0.0137ms  Range: [0.0132ms, 0.0139ms]  Ops/s: 584.64k  Ops/s/t:  73.08k
     Operations per second per thread (weighted average):   1.22M

  > tbb::concurrent_queue
     2   threads:  Avg: 0.2596us  Range: [0.2080us, 0.2972us]  Ops/s:   7.70M  Ops/s/t:   3.85M
     4   threads:  Avg: 1.2173us  Range: [1.2013us, 1.2292us]  Ops/s:   3.29M  Ops/s/t: 821.50k
     8   threads:  Avg: 6.8225us  Range: [6.7245us, 6.8822us]  Ops/s:   1.17M  Ops/s/t: 146.57k
     Operations per second per thread (weighted average):   1.20M

  > SimpleLockFreeQueue
     2   threads:  Avg: 0.3485us  Range: [0.2800us, 0.3978us]  Ops/s:   5.74M  Ops/s/t:   2.87M
     4   threads:  Avg: 2.7128us  Range: [2.5456us, 2.7836us]  Ops/s:   1.47M  Ops/s/t: 368.62k
     8   threads:  Avg: 0.0130ms  Range: [0.0126ms, 0.0131ms]  Ops/s: 616.55k  Ops/s/t:  77.07k
     Operations per second per thread (weighted average): 802.97k

  > LockBasedQueue
     2   threads:  Avg: 0.3517us  Range: [0.3371us, 0.3670us]  Ops/s:   5.69M  Ops/s/t:   2.84M
     4   threads:  Avg: 4.2386us  Range: [3.9773us, 4.4195us]  Ops/s: 943.71k  Ops/s/t: 235.93k
     8   threads:  Avg: 0.0319ms  Range: [0.0313ms, 0.0324ms]  Ops/s: 250.64k  Ops/s/t:  31.33k
     Operations per second per thread (weighted average): 733.86k

  > std::queue
     (skipping, benchmark not supported...)

mostly enqueue bulk:
  (Measures the average speed of enqueueing an item in bulk under light contention)
  > moodycamel::ConcurrentQueue
      Without tokens
         2   threads:  Avg: 0.0163us  Range: [0.0161us, 0.0163us]  Ops/s: 122.86M  Ops/s/t:  61.43M
         4   threads:  Avg: 0.0351us  Range: [0.0349us, 0.0353us]  Ops/s: 113.88M  Ops/s/t:  28.47M
         8   threads:  Avg: 0.0735us  Range: [0.0716us, 0.0767us]  Ops/s: 108.78M  Ops/s/t:  13.60M
         Operations per second per thread (weighted average):  29.20M

      With tokens
         2   threads:  Avg: 9.2662ns  Range: [7.5763ns, 0.0153us]  Ops/s: 215.84M  Ops/s/t: 107.92M
         4   threads:  Avg: 0.0227us  Range: [0.0224us, 0.0234us]  Ops/s: 175.99M  Ops/s/t:  44.00M
         8   threads:  Avg: 0.0630us  Range: [0.0521us, 0.0696us]  Ops/s: 126.96M  Ops/s/t:  15.87M
         Operations per second per thread (weighted average):  45.73M

  > boost::lockfree::queue
     (skipping, benchmark not supported...)

  > tbb::concurrent_queue
     (skipping, benchmark not supported...)

  > SimpleLockFreeQueue
     (skipping, benchmark not supported...)

  > LockBasedQueue
     (skipping, benchmark not supported...)

  > std::queue
     (skipping, benchmark not supported...)

mostly dequeue:
  (Measures the average operation speed when most threads are dequeueing)
  > moodycamel::ConcurrentQueue
      Without tokens
         2   threads:  Avg: 0.1147us  Range: [0.1046us, 0.1173us]  Ops/s:  17.43M  Ops/s/t:   8.72M
         4   threads:  Avg: 0.9262us  Range: [0.9049us, 0.9498us]  Ops/s:   4.32M  Ops/s/t:   1.08M
         8   threads:  Avg: 3.0595us  Range: [2.9788us, 3.0996us]  Ops/s:   2.61M  Ops/s/t: 326.85k
         Operations per second per thread (weighted average):   2.47M

      With tokens
         2   threads:  Avg: 0.0930us  Range: [0.0865us, 0.1028us]  Ops/s:  21.51M  Ops/s/t:  10.76M
         4   threads:  Avg: 0.9531us  Range: [0.9170us, 0.9809us]  Ops/s:   4.20M  Ops/s/t:   1.05M
         8   threads:  Avg: 1.4378us  Range: [1.0370us, 1.8924us]  Ops/s:   5.56M  Ops/s/t: 695.51k
         Operations per second per thread (weighted average):   3.09M

  > boost::lockfree::queue
     2   threads:  Avg: 0.4833us  Range: [0.3832us, 0.5034us]  Ops/s:   4.14M  Ops/s/t:   2.07M
     4   threads:  Avg: 1.8954us  Range: [1.7417us, 1.9705us]  Ops/s:   2.11M  Ops/s/t: 527.59k
     8   threads:  Avg: 8.5828us  Range: [6.3044us, 9.8062us]  Ops/s: 932.10k  Ops/s/t: 116.51k
     Operations per second per thread (weighted average): 690.60k

  > tbb::concurrent_queue
     2   threads:  Avg: 0.1649us  Range: [0.1389us, 0.1819us]  Ops/s:  12.13M  Ops/s/t:   6.07M
     4   threads:  Avg: 1.0857us  Range: [1.0471us, 1.1069us]  Ops/s:   3.68M  Ops/s/t: 921.09k
     8   threads:  Avg: 5.3119us  Range: [5.1655us, 5.3679us]  Ops/s:   1.51M  Ops/s/t: 188.26k
     Operations per second per thread (weighted average):   1.75M

  > SimpleLockFreeQueue
     2   threads:  Avg: 0.4274us  Range: [0.3019us, 0.4857us]  Ops/s:   4.68M  Ops/s/t:   2.34M
     4   threads:  Avg: 2.2468us  Range: [2.1875us, 2.2895us]  Ops/s:   1.78M  Ops/s/t: 445.07k
     8   threads:  Avg: 9.7230us  Range: [5.7420us, 0.0115ms]  Ops/s: 822.79k  Ops/s/t: 102.85k
     Operations per second per thread (weighted average): 719.26k

  > LockBasedQueue
     2   threads:  Avg: 0.8770us  Range: [0.8159us, 0.9367us]  Ops/s:   2.28M  Ops/s/t:   1.14M
     4   threads:  Avg: 5.0059us  Range: [4.7889us, 5.0749us]  Ops/s: 799.06k  Ops/s/t: 199.77k
     8   threads:  Avg: 0.0329ms  Range: [0.0302ms, 0.0343ms]  Ops/s: 243.48k  Ops/s/t:  30.44k
     Operations per second per thread (weighted average): 336.10k

  > std::queue
     (skipping, benchmark not supported...)

mostly dequeue bulk:
  (Measures the average speed of dequeueing an item in bulk under light contention)
  > moodycamel::ConcurrentQueue
      Without tokens
         2   threads:  Avg: 9.8168ns  Range: [9.3033ns, 0.0101us]  Ops/s: 203.73M  Ops/s/t: 101.87M
         4   threads:  Avg: 0.0290us  Range: [0.0282us, 0.0295us]  Ops/s: 137.74M  Ops/s/t:  34.44M
         8   threads:  Avg: 0.1433us  Range: [0.1368us, 0.1469us]  Ops/s:  55.85M  Ops/s/t:   6.98M
         Operations per second per thread (weighted average):  37.27M

      With tokens
         2   threads:  Avg: 4.3748ns  Range: [4.3514ns, 4.3898ns]  Ops/s: 457.17M  Ops/s/t: 228.58M
         4   threads:  Avg: 8.9674ns  Range: [8.8150ns, 9.0488ns]  Ops/s: 446.06M  Ops/s/t: 111.52M
         8   threads:  Avg: 0.0205us  Range: [0.0203us, 0.0209us]  Ops/s: 389.55M  Ops/s/t:  48.69M
         Operations per second per thread (weighted average): 109.57M

  > boost::lockfree::queue
     (skipping, benchmark not supported...)

  > tbb::concurrent_queue
     (skipping, benchmark not supported...)

  > SimpleLockFreeQueue
     (skipping, benchmark not supported...)

  > LockBasedQueue
     (skipping, benchmark not supported...)

  > std::queue
     (skipping, benchmark not supported...)

single-producer, multi-consumer (measuring all but 1 thread):
  (Measures the average speed of dequeueing with only one producer, but multiple consumers)
  > moodycamel::ConcurrentQueue
      Without tokens
         2   threads:  Avg: 0.0878us  Range: [0.0863us, 0.0922us]  Ops/s:  11.40M  Ops/s/t:  11.40M
         4   threads:  Avg: 2.0406us  Range: [2.0044us, 2.0721us]  Ops/s:   1.47M  Ops/s/t: 490.05k
         8   threads:  Avg: 3.8939us  Range: [3.0656us, 4.7150us]  Ops/s:   1.80M  Ops/s/t: 256.81k
         16  threads:  Avg: 1.1516us  Range: [0.8709us, 1.4386us]  Ops/s:  13.03M  Ops/s/t: 868.36k
         Operations per second per thread (weighted average):   1.76M

      With tokens
         2   threads:  Avg: 0.1021us  Range: [0.0805us, 0.1185us]  Ops/s:   9.80M  Ops/s/t:   9.80M
         4   threads:  Avg: 2.0643us  Range: [2.0420us, 2.0983us]  Ops/s:   1.45M  Ops/s/t: 484.43k
         8   threads:  Avg: 3.2532us  Range: [2.0597us, 4.5513us]  Ops/s:   2.15M  Ops/s/t: 307.39k
         16  threads:  Avg: 1.0387us  Range: [0.9508us, 1.1214us]  Ops/s:  14.44M  Ops/s/t: 962.78k
         Operations per second per thread (weighted average):   1.64M

  > boost::lockfree::queue
     2   threads:  Avg: 0.1953us  Range: [0.1255us, 0.2504us]  Ops/s:   5.12M  Ops/s/t:   5.12M
     4   threads:  Avg: 0.6180us  Range: [0.5063us, 0.7395us]  Ops/s:   4.85M  Ops/s/t:   1.62M
     8   threads:  Avg: 1.2901us  Range: [0.3619us, 2.5359us]  Ops/s:   5.43M  Ops/s/t: 775.15k
     16  threads:  Avg: 0.5587us  Range: [0.5391us, 0.5822us]  Ops/s:  26.85M  Ops/s/t:   1.79M
     Operations per second per thread (weighted average):   1.83M

  > tbb::concurrent_queue
     2   threads:  Avg: 0.0504us  Range: [0.0503us, 0.0505us]  Ops/s:  19.83M  Ops/s/t:  19.83M
     4   threads:  Avg: 0.3747us  Range: [0.3519us, 0.4136us]  Ops/s:   8.01M  Ops/s/t:   2.67M
     8   threads:  Avg: 1.0650us  Range: [0.9783us, 1.3288us]  Ops/s:   6.57M  Ops/s/t: 938.94k
     16  threads:  Avg: 4.9238us  Range: [4.1458us, 6.8823us]  Ops/s:   3.05M  Ops/s/t: 203.10k
     Operations per second per thread (weighted average):   3.00M

  > SimpleLockFreeQueue
     2   threads:  Avg: 0.1745us  Range: [0.1117us, 0.2339us]  Ops/s:   5.73M  Ops/s/t:   5.73M
     4   threads:  Avg: 1.0479us  Range: [0.9467us, 1.1571us]  Ops/s:   2.86M  Ops/s/t: 954.25k
     8   threads:  Avg: 1.6628us  Range: [0.8011us, 2.5645us]  Ops/s:   4.21M  Ops/s/t: 601.41k
     16  threads:  Avg: 0.7777us  Range: [0.6937us, 0.8517us]  Ops/s:  19.29M  Ops/s/t:   1.29M
     Operations per second per thread (weighted average):   1.51M

  > LockBasedQueue
     2   threads:  Avg: 0.2711us  Range: [0.2503us, 0.3654us]  Ops/s:   3.69M  Ops/s/t:   3.69M
     4   threads:  Avg: 2.0288us  Range: [1.9517us, 2.1707us]  Ops/s:   1.48M  Ops/s/t: 492.91k
     8   threads:  Avg: 0.0121ms  Range: [5.6724us, 0.0141ms]  Ops/s: 577.35k  Ops/s/t:  82.48k
     16  threads:  Avg: 0.0334ms  Range: [0.0253ms, 0.0569ms]  Ops/s: 449.71k  Ops/s/t:  29.98k
     Operations per second per thread (weighted average): 527.19k

  > std::queue
     (skipping, benchmark not supported...)

single-producer, multi-consumer (pre-produced):
  (Measures the average speed of dequeueing from a queue pre-filled by one thread)
  > moodycamel::ConcurrentQueue
      Without tokens
         1    thread:  Avg: 0.0593us  Range: [0.0592us, 0.0594us]  Ops/s:  16.85M  Ops/s/t:  16.85M
         3   threads:  Avg: 1.2845us  Range: [1.2593us, 1.2992us]  Ops/s:   2.34M  Ops/s/t: 778.51k
         7   threads:  Avg: 6.3588us  Range: [6.1795us, 6.4430us]  Ops/s:   1.10M  Ops/s/t: 157.26k
         15  threads:  Avg: 0.0239ms  Range: [0.0212ms, 0.0245ms]  Ops/s: 628.32k  Ops/s/t:  41.89k
         Operations per second per thread (weighted average):   2.03M

      With tokens
         1    thread:  Avg: 0.0522us  Range: [0.0521us, 0.0523us]  Ops/s:  19.16M  Ops/s/t:  19.16M
         3   threads:  Avg: 1.2192us  Range: [1.1359us, 1.2706us]  Ops/s:   2.46M  Ops/s/t: 820.22k
         7   threads:  Avg: 6.6076us  Range: [6.3552us, 6.7226us]  Ops/s:   1.06M  Ops/s/t: 151.34k
         15  threads:  Avg: 0.0247ms  Range: [0.0241ms, 0.0250ms]  Ops/s: 606.63k  Ops/s/t:  40.44k
         Operations per second per thread (weighted average):   2.29M

  > boost::lockfree::queue
     1    thread:  Avg: 0.0462us  Range: [0.0461us, 0.0462us]  Ops/s:  21.66M  Ops/s/t:  21.66M
     3   threads:  Avg: 1.5532us  Range: [1.5270us, 1.5706us]  Ops/s:   1.93M  Ops/s/t: 643.82k
     7   threads:  Avg: 0.0129ms  Range: [0.0126ms, 0.0131ms]  Ops/s: 540.60k  Ops/s/t:  77.23k
     15  threads:  Avg: 0.0609ms  Range: [0.0529ms, 0.0628ms]  Ops/s: 246.40k  Ops/s/t:  16.43k
     Operations per second per thread (weighted average):   2.49M

  > tbb::concurrent_queue
     1    thread:  Avg: 0.0346us  Range: [0.0345us, 0.0347us]  Ops/s:  28.93M  Ops/s/t:  28.93M
     3   threads:  Avg: 0.9686us  Range: [0.8993us, 0.9962us]  Ops/s:   3.10M  Ops/s/t:   1.03M
     7   threads:  Avg: 6.5002us  Range: [6.2817us, 6.6565us]  Ops/s:   1.08M  Ops/s/t: 153.84k
     15  threads:  Avg: 0.0955ms  Range: [0.0925ms, 0.0973ms]  Ops/s: 157.07k  Ops/s/t:  10.47k
     Operations per second per thread (weighted average):   3.37M

  > SimpleLockFreeQueue
     1    thread:  Avg: 0.0406us  Range: [0.0406us, 0.0407us]  Ops/s:  24.62M  Ops/s/t:  24.62M
     3   threads:  Avg: 0.9775us  Range: [0.7102us, 1.1289us]  Ops/s:   3.07M  Ops/s/t:   1.02M
     7   threads:  Avg: 0.0125ms  Range: [0.0109ms, 0.0134ms]  Ops/s: 560.68k  Ops/s/t:  80.10k
     15  threads:  Avg: 0.0537ms  Range: [0.0482ms, 0.0570ms]  Ops/s: 279.46k  Ops/s/t:  18.63k
     Operations per second per thread (weighted average):   2.88M

  > LockBasedQueue
     1    thread:  Avg: 0.0749us  Range: [0.0747us, 0.0751us]  Ops/s:  13.35M  Ops/s/t:  13.35M
     3   threads:  Avg: 2.7371us  Range: [2.4621us, 2.8206us]  Ops/s:   1.10M  Ops/s/t: 365.35k
     7   threads:  Avg: 0.0283ms  Range: [0.0277ms, 0.0290ms]  Ops/s: 247.11k  Ops/s/t:  35.30k
     15  threads:  Avg: 0.1168ms  Range: [0.0455ms, 0.1511ms]  Ops/s: 128.41k  Ops/s/t:   8.56k
     Operations per second per thread (weighted average):   1.53M

  > std::queue
     (skipping, benchmark not supported...)

multi-producer, single-consumer (measuring 1 thread):
  (Measures the average speed of dequeueing with only one consumer, but multiple producers)
  > moodycamel::ConcurrentQueue
      Without tokens
         2   threads:  Avg: 0.0787us  Range: [0.0740us, 0.0844us]  Ops/s:  12.71M  Ops/s/t:  12.71M
         4   threads:  Avg: 0.0761us  Range: [0.0755us, 0.0764us]  Ops/s:  13.15M  Ops/s/t:  13.15M
         8   threads:  Avg: 0.1050us  Range: [0.1046us, 0.1053us]  Ops/s:   9.53M  Ops/s/t:   9.53M
         16  threads:  Avg: 0.1227us  Range: [0.1054us, 0.1465us]  Ops/s:   8.15M  Ops/s/t:   8.15M
         Operations per second per thread (weighted average):  10.88M

      With tokens
         2   threads:  Avg: 0.0715us  Range: [0.0625us, 0.0801us]  Ops/s:  13.99M  Ops/s/t:  13.99M
         4   threads:  Avg: 0.0584us  Range: [0.0582us, 0.0585us]  Ops/s:  17.12M  Ops/s/t:  17.12M
         8   threads:  Avg: 0.0553us  Range: [0.0552us, 0.0555us]  Ops/s:  18.07M  Ops/s/t:  18.07M
         16  threads:  Avg: 0.0543us  Range: [0.0541us, 0.0545us]  Ops/s:  18.40M  Ops/s/t:  18.40M
         Operations per second per thread (weighted average):  16.90M

  > boost::lockfree::queue
     2   threads:  Avg: 0.1204us  Range: [0.0959us, 0.1492us]  Ops/s:   8.30M  Ops/s/t:   8.30M
     4   threads:  Avg: 0.3535us  Range: [0.3389us, 0.3591us]  Ops/s:   2.83M  Ops/s/t:   2.83M
     8   threads:  Avg: 0.4242us  Range: [0.4127us, 0.4340us]  Ops/s:   2.36M  Ops/s/t:   2.36M
     16  threads:  Avg: 0.4959us  Range: [0.4858us, 0.5012us]  Ops/s:   2.02M  Ops/s/t:   2.02M
     Operations per second per thread (weighted average):   3.88M

  > tbb::concurrent_queue
     2   threads:  Avg: 0.1540us  Range: [0.1379us, 0.1610us]  Ops/s:   6.49M  Ops/s/t:   6.49M
     4   threads:  Avg: 0.1178us  Range: [0.1174us, 0.1182us]  Ops/s:   8.49M  Ops/s/t:   8.49M
     8   threads:  Avg: 0.1788us  Range: [0.1769us, 0.1802us]  Ops/s:   5.59M  Ops/s/t:   5.59M
     16  threads:  Avg: 0.4524us  Range: [0.4471us, 0.4555us]  Ops/s:   2.21M  Ops/s/t:   2.21M
     Operations per second per thread (weighted average):   5.70M

  > SimpleLockFreeQueue
     2   threads:  Avg: 0.2191us  Range: [0.1969us, 0.2384us]  Ops/s:   4.56M  Ops/s/t:   4.56M
     4   threads:  Avg: 0.2822us  Range: [0.2790us, 0.2841us]  Ops/s:   3.54M  Ops/s/t:   3.54M
     8   threads:  Avg: 0.2778us  Range: [0.1914us, 0.2998us]  Ops/s:   3.60M  Ops/s/t:   3.60M
     16  threads:  Avg: 0.2838us  Range: [0.2790us, 0.2869us]  Ops/s:   3.52M  Ops/s/t:   3.52M
     Operations per second per thread (weighted average):   3.81M

  > LockBasedQueue
     2   threads:  Avg: 0.2405us  Range: [0.2110us, 0.2647us]  Ops/s:   4.16M  Ops/s/t:   4.16M
     4   threads:  Avg: 0.4786us  Range: [0.4487us, 0.5009us]  Ops/s:   2.09M  Ops/s/t:   2.09M
     8   threads:  Avg: 0.6288us  Range: [0.6146us, 0.6619us]  Ops/s:   1.59M  Ops/s/t:   1.59M
     16  threads:  Avg: 0.6400us  Range: [0.6148us, 0.6605us]  Ops/s:   1.56M  Ops/s/t:   1.56M
     Operations per second per thread (weighted average):   2.35M

  > std::queue
     (skipping, benchmark not supported...)

dequeue from empty:
  (Measures the average speed of attempting to dequeue from an empty queue
  (that eight separate threads had at one point enqueued to))
  > moodycamel::ConcurrentQueue
      Without tokens
         1    thread:  Avg: 0.0450us  Range: [0.0446us, 0.0453us]  Ops/s:  22.21M  Ops/s/t:  22.21M
                ^ Note: No contention -- measures raw failed dequeue speed on empty queue
         2   threads:  Avg: 0.1120us  Range: [0.1101us, 0.1137us]  Ops/s:  17.86M  Ops/s/t:   8.93M
         8   threads:  Avg: 0.5365us  Range: [0.4941us, 0.5660us]  Ops/s:  14.91M  Ops/s/t:   1.86M
         Operations per second per thread (weighted average):   7.65M

      With tokens
         1    thread:  Avg: 0.0261us  Range: [0.0234us, 0.0280us]  Ops/s:  38.35M  Ops/s/t:  38.35M
                ^ Note: No contention -- measures raw failed dequeue speed on empty queue
         2   threads:  Avg: 0.0536us  Range: [0.0526us, 0.0545us]  Ops/s:  37.32M  Ops/s/t:  18.66M
         8   threads:  Avg: 0.3407us  Range: [0.2978us, 0.3646us]  Ops/s:  23.48M  Ops/s/t:   2.93M
         Operations per second per thread (weighted average):  13.93M

  > boost::lockfree::queue
     1    thread:  Avg: 6.7505ns  Range: [6.7148ns, 6.7679ns]  Ops/s: 148.14M  Ops/s/t: 148.14M
            ^ Note: No contention -- measures raw failed dequeue speed on empty queue
     2   threads:  Avg: 0.0135us  Range: [0.0135us, 0.0135us]  Ops/s: 148.29M  Ops/s/t:  74.14M
     8   threads:  Avg: 0.0578us  Range: [0.0556us, 0.0596us]  Ops/s: 138.38M  Ops/s/t:  17.30M
     Operations per second per thread (weighted average):  57.59M

  > tbb::concurrent_queue
     1    thread:  Avg: 5.0561ns  Range: [5.0460ns, 5.0629ns]  Ops/s: 197.78M  Ops/s/t: 197.78M
            ^ Note: No contention -- measures raw failed dequeue speed on empty queue
     2   threads:  Avg: 0.0102us  Range: [0.0101us, 0.0102us]  Ops/s: 196.89M  Ops/s/t:  98.44M
     8   threads:  Avg: 0.0436us  Range: [0.0427us, 0.0445us]  Ops/s: 183.65M  Ops/s/t:  22.96M
     Operations per second per thread (weighted average):  76.67M

  > SimpleLockFreeQueue
     1    thread:  Avg: 8.2113ns  Range: [8.1694ns, 8.2488ns]  Ops/s: 121.78M  Ops/s/t: 121.78M
            ^ Note: No contention -- measures raw failed dequeue speed on empty queue
     2   threads:  Avg: 0.0170us  Range: [0.0170us, 0.0170us]  Ops/s: 117.47M  Ops/s/t:  58.74M
     8   threads:  Avg: 0.0769us  Range: [0.0687us, 0.0830us]  Ops/s: 104.08M  Ops/s/t:  13.01M
     Operations per second per thread (weighted average):  46.09M

  > LockBasedQueue
     1    thread:  Avg: 0.0415us  Range: [0.0413us, 0.0416us]  Ops/s:  24.08M  Ops/s/t:  24.08M
            ^ Note: No contention -- measures raw failed dequeue speed on empty queue
     2   threads:  Avg: 0.3330us  Range: [0.2910us, 0.3575us]  Ops/s:   6.01M  Ops/s/t:   3.00M
     8   threads:  Avg: 0.0115ms  Range: [0.0113ms, 0.0117ms]  Ops/s: 693.59k  Ops/s/t:  86.70k
     Operations per second per thread (weighted average):   5.45M

  > std::queue
     1    thread:  Avg: 1.1189ns  Range: [1.1174ns, 1.1201ns]  Ops/s: 893.71M  Ops/s/t: 893.71M
            ^ Note: No contention -- measures raw failed dequeue speed on empty queue
     Operations per second per thread (weighted average): 893.71M

enqueue-dequeue pairs:
  (Measures the average operation speed with each thread doing an enqueue
  followed by a dequeue)
  > moodycamel::ConcurrentQueue
      Without tokens
         1    thread:  Avg: 0.0355us  Range: [0.0355us, 0.0356us]  Ops/s:  28.13M  Ops/s/t:  28.13M
                ^ Note: No contention -- measures speed of immediately dequeueing the item that was just enqueued
         2   threads:  Avg: 0.2504us  Range: [0.2444us, 0.2626us]  Ops/s:   7.99M  Ops/s/t:   3.99M
         4   threads:  Avg: 0.9991us  Range: [0.9811us, 1.0048us]  Ops/s:   4.00M  Ops/s/t:   1.00M
         8   threads:  Avg: 3.9295us  Range: [3.8626us, 3.9785us]  Ops/s:   2.04M  Ops/s/t: 254.48k
         Operations per second per thread (weighted average):   5.04M

      With tokens
         1    thread:  Avg: 0.0284us  Range: [0.0284us, 0.0285us]  Ops/s:  35.17M  Ops/s/t:  35.17M
                ^ Note: No contention -- measures speed of immediately dequeueing the item that was just enqueued
         2   threads:  Avg: 0.0664us  Range: [0.0639us, 0.0675us]  Ops/s:  30.10M  Ops/s/t:  15.05M
         4   threads:  Avg: 0.1407us  Range: [0.1393us, 0.1422us]  Ops/s:  28.43M  Ops/s/t:   7.11M
         8   threads:  Avg: 0.3319us  Range: [0.3263us, 0.3370us]  Ops/s:  24.10M  Ops/s/t:   3.01M
         Operations per second per thread (weighted average):  10.93M

  > boost::lockfree::queue
     1    thread:  Avg: 0.0551us  Range: [0.0549us, 0.0553us]  Ops/s:  18.15M  Ops/s/t:  18.15M
            ^ Note: No contention -- measures speed of immediately dequeueing the item that was just enqueued
     2   threads:  Avg: 0.4973us  Range: [0.4543us, 0.5281us]  Ops/s:   4.02M  Ops/s/t:   2.01M
     4   threads:  Avg: 2.7075us  Range: [2.6333us, 2.7333us]  Ops/s:   1.48M  Ops/s/t: 369.34k
     8   threads:  Avg: 0.0122ms  Range: [0.0117ms, 0.0124ms]  Ops/s: 658.25k  Ops/s/t:  82.28k
     Operations per second per thread (weighted average):   3.03M

  > tbb::concurrent_queue
     1    thread:  Avg: 0.0374us  Range: [0.0373us, 0.0375us]  Ops/s:  26.73M  Ops/s/t:  26.73M
            ^ Note: No contention -- measures speed of immediately dequeueing the item that was just enqueued
     2   threads:  Avg: 0.3919us  Range: [0.3765us, 0.3979us]  Ops/s:   5.10M  Ops/s/t:   2.55M
     4   threads:  Avg: 1.5805us  Range: [1.5665us, 1.5899us]  Ops/s:   2.53M  Ops/s/t: 632.70k
     8   threads:  Avg: 6.7988us  Range: [6.6864us, 6.8373us]  Ops/s:   1.18M  Ops/s/t: 147.09k
     Operations per second per thread (weighted average):   4.42M

  > SimpleLockFreeQueue
     1    thread:  Avg: 0.0654us  Range: [0.0650us, 0.0660us]  Ops/s:  15.28M  Ops/s/t:  15.28M
            ^ Note: No contention -- measures speed of immediately dequeueing the item that was just enqueued
     2   threads:  Avg: 0.5166us  Range: [0.4368us, 0.5703us]  Ops/s:   3.87M  Ops/s/t:   1.94M
     4   threads:  Avg: 2.2357us  Range: [0.8571us, 3.0570us]  Ops/s:   1.79M  Ops/s/t: 447.30k
     8   threads:  Avg: 0.0150ms  Range: [0.0146ms, 0.0153ms]  Ops/s: 532.29k  Ops/s/t:  66.54k
     Operations per second per thread (weighted average):   2.64M

  > LockBasedQueue
     1    thread:  Avg: 0.0819us  Range: [0.0817us, 0.0820us]  Ops/s:  12.21M  Ops/s/t:  12.21M
            ^ Note: No contention -- measures speed of immediately dequeueing the item that was just enqueued
     2   threads:  Avg: 0.9000us  Range: [0.8402us, 0.9348us]  Ops/s:   2.22M  Ops/s/t:   1.11M
     4   threads:  Avg: 4.4576us  Range: [4.3944us, 4.5059us]  Ops/s: 897.34k  Ops/s/t: 224.33k
     8   threads:  Avg: 0.0325ms  Range: [0.0322ms, 0.0329ms]  Ops/s: 245.85k  Ops/s/t:  30.73k
     Operations per second per thread (weighted average):   1.98M

  > std::queue
     1    thread:  Avg: 4.7801ns  Range: [4.7600ns, 4.7944ns]  Ops/s: 209.20M  Ops/s/t: 209.20M
            ^ Note: No contention -- measures speed of immediately dequeueing the item that was just enqueued
     Operations per second per thread (weighted average): 209.20M

heavy concurrent:
  (Measures the average operation speed with many threads under heavy load)
  > moodycamel::ConcurrentQueue
      Without tokens
         2   threads:  Avg: 0.2274us  Range: [0.2084us, 0.2361us]  Ops/s:   8.80M  Ops/s/t:   4.40M
         3   threads:  Avg: 0.3628us  Range: [0.3108us, 0.3876us]  Ops/s:   8.27M  Ops/s/t:   2.76M
         4   threads:  Avg: 0.8110us  Range: [0.8024us, 0.8255us]  Ops/s:   4.93M  Ops/s/t:   1.23M
         8   threads:  Avg: 2.3682us  Range: [2.2998us, 2.4158us]  Ops/s:   3.38M  Ops/s/t: 422.26k
         16  threads:  Avg: 7.1416us  Range: [6.9666us, 7.3096us]  Ops/s:   2.24M  Ops/s/t: 140.02k
         Operations per second per thread (weighted average):   1.27M

      With tokens
         2   threads:  Avg: 0.0617us  Range: [0.0607us, 0.0624us]  Ops/s:  32.44M  Ops/s/t:  16.22M
         3   threads:  Avg: 0.1539us  Range: [0.1453us, 0.1600us]  Ops/s:  19.49M  Ops/s/t:   6.50M
         4   threads:  Avg: 0.4326us  Range: [0.3373us, 0.4557us]  Ops/s:   9.25M  Ops/s/t:   2.31M
         8   threads:  Avg: 1.1528us  Range: [0.6981us, 1.3157us]  Ops/s:   6.94M  Ops/s/t: 867.42k
         16  threads:  Avg: 2.5832us  Range: [1.7887us, 2.8307us]  Ops/s:   6.19M  Ops/s/t: 387.12k
         Operations per second per thread (weighted average):   3.58M

  > boost::lockfree::queue
     2   threads:  Avg: 0.4679us  Range: [0.3875us, 0.5422us]  Ops/s:   4.27M  Ops/s/t:   2.14M
     3   threads:  Avg: 1.5423us  Range: [1.2787us, 1.6510us]  Ops/s:   1.95M  Ops/s/t: 648.37k
     4   threads:  Avg: 2.1368us  Range: [2.0835us, 2.1668us]  Ops/s:   1.87M  Ops/s/t: 467.99k
     8   threads:  Avg: 7.0047us  Range: [6.8172us, 7.1692us]  Ops/s:   1.14M  Ops/s/t: 142.76k
     16  threads:  Avg: 0.0217ms  Range: [0.0119ms, 0.0265ms]  Ops/s: 738.87k  Ops/s/t:  46.18k
     Operations per second per thread (weighted average): 473.47k

  > tbb::concurrent_queue
     2   threads:  Avg: 0.3758us  Range: [0.3676us, 0.3830us]  Ops/s:   5.32M  Ops/s/t:   2.66M
     3   threads:  Avg: 0.6918us  Range: [0.5712us, 0.7622us]  Ops/s:   4.34M  Ops/s/t:   1.45M
     4   threads:  Avg: 1.2247us  Range: [1.2153us, 1.2353us]  Ops/s:   3.27M  Ops/s/t: 816.55k
     8   threads:  Avg: 4.8072us  Range: [4.7176us, 4.8768us]  Ops/s:   1.66M  Ops/s/t: 208.02k
     16  threads:  Avg: 0.0314ms  Range: [0.0283ms, 0.0342ms]  Ops/s: 508.84k  Ops/s/t:  31.80k
     Operations per second per thread (weighted average): 719.46k

  > SimpleLockFreeQueue
     2   threads:  Avg: 0.3386us  Range: [0.2242us, 0.4207us]  Ops/s:   5.91M  Ops/s/t:   2.95M
     3   threads:  Avg: 1.3641us  Range: [1.2620us, 1.4514us]  Ops/s:   2.20M  Ops/s/t: 733.07k
     4   threads:  Avg: 2.5347us  Range: [2.3151us, 2.7468us]  Ops/s:   1.58M  Ops/s/t: 394.52k
     8   threads:  Avg: 0.0116ms  Range: [0.0101ms, 0.0129ms]  Ops/s: 689.76k  Ops/s/t:  86.22k
     16  threads:  Avg: 0.0179ms  Range: [0.0151ms, 0.0191ms]  Ops/s: 893.24k  Ops/s/t:  55.83k
     Operations per second per thread (weighted average): 559.71k

  > LockBasedQueue
     2   threads:  Avg: 0.8267us  Range: [0.6615us, 0.8974us]  Ops/s:   2.42M  Ops/s/t:   1.21M
     3   threads:  Avg: 2.2000us  Range: [2.1430us, 2.2375us]  Ops/s:   1.36M  Ops/s/t: 454.55k
     4   threads:  Avg: 4.5203us  Range: [4.3255us, 4.5829us]  Ops/s: 884.89k  Ops/s/t: 221.22k
     8   threads:  Avg: 0.0317ms  Range: [0.0198ms, 0.0347ms]  Ops/s: 252.75k  Ops/s/t:  31.59k
     16  threads:  Avg: 0.1313ms  Range: [0.1260ms, 0.1356ms]  Ops/s: 121.86k  Ops/s/t:   7.62k
     Operations per second per thread (weighted average): 255.55k

  > std::queue
     (skipping, benchmark not supported...)

Overall average operations per second per thread (where higher-concurrency runs have more weight):
(Take this summary with a grain of salt -- look at the individual benchmark results for a much
better idea of how the queues measure up to each other):
    moodycamel::ConcurrentQueue (including bulk):  23.23M
    boost::lockfree::queue:   4.75M
    tbb::concurrent_queue:   6.44M
    SimpleLockFreeQueue:   4.07M
    LockBasedQueue:   1.28M
    std::queue (single thread only): 298.35M

```

#### 64-bit dual-core, Windows 7, MinGW-w64 g++ 4.81 (a netbook)

```
Running 64-bit benchmarks on an AMD C-50 Processor with 2 cores @ 1.0GHz
    (precise mode)
Note that these are synthetic benchmarks. Take them with a grain of salt.

Legend:
    'Avg':     Average time taken per operation, normalized to be per thread
    'Range':   The minimum and maximum times taken per operation (per thread)
    'Ops/s':   Overall operations per second
    'Ops/s/t': Operations per second per thread (inverse of 'Avg')
    Operations include those that fail (e.g. because the queue is empty).
    Each logical enqueue/dequeue counts as an individual operation when in bulk.

balanced:
  (Measures the average operation speed with multiple symmetrical threads
  under reasonable load -- small random intervals between accesses)
  > moodycamel::ConcurrentQueue
      Without tokens
         2   threads:  Avg: 0.6824us  Range: [0.5126us, 0.7394us]  Ops/s:   2.93M  Ops/s/t:   1.47M
         3   threads:  Avg: 0.8942us  Range: [0.6801us, 0.9944us]  Ops/s:   3.35M  Ops/s/t:   1.12M
         4   threads:  Avg: 1.2901us  Range: [1.2430us, 1.3249us]  Ops/s:   3.10M  Ops/s/t: 775.11k
         Operations per second per thread (weighted average):   1.08M

      With tokens
         2   threads:  Avg: 0.5335us  Range: [0.5023us, 0.5511us]  Ops/s:   3.75M  Ops/s/t:   1.87M
         3   threads:  Avg: 0.6989us  Range: [0.5523us, 0.7436us]  Ops/s:   4.29M  Ops/s/t:   1.43M
         4   threads:  Avg: 0.8901us  Range: [0.7294us, 1.0176us]  Ops/s:   4.49M  Ops/s/t:   1.12M
         Operations per second per thread (weighted average):   1.43M

  > boost::lockfree::queue
     2   threads:  Avg: 1.0185us  Range: [0.9988us, 1.0281us]  Ops/s:   1.96M  Ops/s/t: 981.82k
     3   threads:  Avg: 1.0706us  Range: [0.6640us, 1.2621us]  Ops/s:   2.80M  Ops/s/t: 934.05k
     4   threads:  Avg: 1.3243us  Range: [0.8594us, 1.6103us]  Ops/s:   3.02M  Ops/s/t: 755.11k
     Operations per second per thread (weighted average): 877.64k

  > tbb::concurrent_queue
     2   threads:  Avg: 0.8333us  Range: [0.8103us, 0.8432us]  Ops/s:   2.40M  Ops/s/t:   1.20M
     3   threads:  Avg: 1.0095us  Range: [0.6400us, 1.0816us]  Ops/s:   2.97M  Ops/s/t: 990.59k
     4   threads:  Avg: 1.1098us  Range: [0.8329us, 1.3179us]  Ops/s:   3.60M  Ops/s/t: 901.02k
     Operations per second per thread (weighted average):   1.01M

  > SimpleLockFreeQueue
     2   threads:  Avg: 0.8720us  Range: [0.7974us, 0.9197us]  Ops/s:   2.29M  Ops/s/t:   1.15M
     3   threads:  Avg: 1.0043us  Range: [0.7678us, 1.0872us]  Ops/s:   2.99M  Ops/s/t: 995.70k
     4   threads:  Avg: 1.2877us  Range: [0.8881us, 1.5254us]  Ops/s:   3.11M  Ops/s/t: 776.59k
     Operations per second per thread (weighted average): 952.08k

  > LockBasedQueue
     2   threads:  Avg: 0.0157ms  Range: [0.0122ms, 0.0179ms]  Ops/s: 127.48k  Ops/s/t:  63.74k
     3   threads:  Avg: 0.0183ms  Range: [9.7330us, 0.0271ms]  Ops/s: 164.09k  Ops/s/t:  54.70k
     4   threads:  Avg: 0.0141ms  Range: [0.0132ms, 0.0165ms]  Ops/s: 283.88k  Ops/s/t:  70.97k
     Operations per second per thread (weighted average):  63.51k

  > std::queue
     (skipping, benchmark not supported...)

only enqueue:
  (Measures the average operation speed when all threads are producers)
  > moodycamel::ConcurrentQueue
      Without tokens
         1    thread:  Avg: 0.0800us  Range: [0.0799us, 0.0801us]  Ops/s:  12.51M  Ops/s/t:  12.51M
         2   threads:  Avg: 0.1405us  Range: [0.1398us, 0.1415us]  Ops/s:  14.23M  Ops/s/t:   7.12M
         4   threads:  Avg: 0.2840us  Range: [0.2778us, 0.2872us]  Ops/s:  14.08M  Ops/s/t:   3.52M
         Operations per second per thread (weighted average):   6.71M

      With tokens
         1    thread:  Avg: 0.0336us  Range: [0.0334us, 0.0337us]  Ops/s:  29.79M  Ops/s/t:  29.79M
         2   threads:  Avg: 0.0703us  Range: [0.0692us, 0.0713us]  Ops/s:  28.43M  Ops/s/t:  14.22M
         4   threads:  Avg: 0.1442us  Range: [0.1415us, 0.1466us]  Ops/s:  27.73M  Ops/s/t:   6.93M
         Operations per second per thread (weighted average):  14.44M

  > boost::lockfree::queue
     1    thread:  Avg: 0.3496us  Range: [0.3475us, 0.3504us]  Ops/s:   2.86M  Ops/s/t:   2.86M
     2   threads:  Avg: 1.3211us  Range: [1.2986us, 1.3384us]  Ops/s:   1.51M  Ops/s/t: 756.94k
     4   threads:  Avg: 2.4626us  Range: [2.1835us, 2.6067us]  Ops/s:   1.62M  Ops/s/t: 406.08k
     Operations per second per thread (weighted average):   1.07M

  > tbb::concurrent_queue
     1    thread:  Avg: 0.1014us  Range: [0.1001us, 0.1021us]  Ops/s:   9.86M  Ops/s/t:   9.86M
     2   threads:  Avg: 0.6901us  Range: [0.6653us, 0.7117us]  Ops/s:   2.90M  Ops/s/t:   1.45M
     4   threads:  Avg: 4.5818us  Range: [0.5737us, 6.0795us]  Ops/s: 873.01k  Ops/s/t: 218.25k
     Operations per second per thread (weighted average):   2.80M

  > SimpleLockFreeQueue
     1    thread:  Avg: 0.1729us  Range: [0.1718us, 0.1734us]  Ops/s:   5.78M  Ops/s/t:   5.78M
     2   threads:  Avg: 0.9181us  Range: [0.8509us, 0.9554us]  Ops/s:   2.18M  Ops/s/t:   1.09M
     4   threads:  Avg: 1.5708us  Range: [1.3950us, 1.7283us]  Ops/s:   2.55M  Ops/s/t: 636.64k
     Operations per second per thread (weighted average):   1.95M

  > LockBasedQueue
     1    thread:  Avg: 3.1054us  Range: [3.0999us, 3.1087us]  Ops/s: 322.02k  Ops/s/t: 322.02k
     2   threads:  Avg: 0.0245ms  Range: [0.0222ms, 0.0271ms]  Ops/s:  81.54k  Ops/s/t:  40.77k
     4   threads:  Avg: 0.0790ms  Range: [0.0250ms, 0.1326ms]  Ops/s:  50.64k  Ops/s/t:  12.66k
     Operations per second per thread (weighted average):  91.75k

  > std::queue
     1    thread:  Avg: 0.0214us  Range: [0.0213us, 0.0215us]  Ops/s:  46.73M  Ops/s/t:  46.73M
     Operations per second per thread (weighted average):  46.73M

only enqueue (pre-allocated):
  (Measures the average operation speed when all threads are producers,
  and the queue has been stretched out first)
  > moodycamel::ConcurrentQueue
      Without tokens
         1    thread:  Avg: 0.0783us  Range: [0.0780us, 0.0784us]  Ops/s:  12.78M  Ops/s/t:  12.78M
         2   threads:  Avg: 0.1474us  Range: [0.1468us, 0.1480us]  Ops/s:  13.57M  Ops/s/t:   6.78M
         4   threads:  Avg: 0.2980us  Range: [0.2843us, 0.3053us]  Ops/s:  13.42M  Ops/s/t:   3.36M
         Operations per second per thread (weighted average):   6.59M

      With tokens
         1    thread:  Avg: 0.0268us  Range: [0.0267us, 0.0268us]  Ops/s:  37.36M  Ops/s/t:  37.36M
         2   threads:  Avg: 0.0557us  Range: [0.0553us, 0.0562us]  Ops/s:  35.89M  Ops/s/t:  17.94M
         4   threads:  Avg: 0.1105us  Range: [0.1098us, 0.1111us]  Ops/s:  36.20M  Ops/s/t:   9.05M
         Operations per second per thread (weighted average):  18.31M

  > boost::lockfree::queue
     1    thread:  Avg: 0.1283us  Range: [0.1280us, 0.1287us]  Ops/s:   7.79M  Ops/s/t:   7.79M
     2   threads:  Avg: 0.7111us  Range: [0.6135us, 0.7794us]  Ops/s:   2.81M  Ops/s/t:   1.41M
     4   threads:  Avg: 1.1166us  Range: [0.6167us, 1.3582us]  Ops/s:   3.58M  Ops/s/t: 895.56k
     Operations per second per thread (weighted average):   2.62M

  > tbb::concurrent_queue
     1    thread:  Avg: 0.1009us  Range: [0.1005us, 0.1013us]  Ops/s:   9.91M  Ops/s/t:   9.91M
     2   threads:  Avg: 0.6381us  Range: [0.5011us, 0.6934us]  Ops/s:   3.13M  Ops/s/t:   1.57M
     4   threads:  Avg: 2.9372us  Range: [0.4636us, 5.9396us]  Ops/s:   1.36M  Ops/s/t: 340.46k
     Operations per second per thread (weighted average):   2.90M

  > SimpleLockFreeQueue
     1    thread:  Avg: 0.1656us  Range: [0.1652us, 0.1659us]  Ops/s:   6.04M  Ops/s/t:   6.04M
     2   threads:  Avg: 0.9964us  Range: [0.9337us, 1.0278us]  Ops/s:   2.01M  Ops/s/t:   1.00M
     4   threads:  Avg: 1.4095us  Range: [1.2388us, 1.5327us]  Ops/s:   2.84M  Ops/s/t: 709.48k
     Operations per second per thread (weighted average):   2.01M

  > LockBasedQueue
     1    thread:  Avg: 3.0501us  Range: [3.0382us, 3.0576us]  Ops/s: 327.86k  Ops/s/t: 327.86k
     2   threads:  Avg: 0.0196ms  Range: [0.0131ms, 0.0235ms]  Ops/s: 101.91k  Ops/s/t:  50.95k
     4   threads:  Avg: 0.0842ms  Range: [0.0438ms, 0.1204ms]  Ops/s:  47.52k  Ops/s/t:  11.88k
     Operations per second per thread (weighted average):  95.98k

  > std::queue
     1    thread:  Avg: 0.0211us  Range: [0.0210us, 0.0211us]  Ops/s:  47.43M  Ops/s/t:  47.43M
     Operations per second per thread (weighted average):  47.43M

only enqueue bulk:
  (Measures the average speed of enqueueing an item in bulk when all threads are producers)
  > moodycamel::ConcurrentQueue
      Without tokens
         1    thread:  Avg: 0.0158us  Range: [0.0157us, 0.0158us]  Ops/s:  63.25M  Ops/s/t:  63.25M
         2   threads:  Avg: 0.0364us  Range: [0.0360us, 0.0367us]  Ops/s:  54.92M  Ops/s/t:  27.46M
         4   threads:  Avg: 0.0736us  Range: [0.0727us, 0.0742us]  Ops/s:  54.32M  Ops/s/t:  13.58M
         Operations per second per thread (weighted average):  29.28M

      With tokens
         1    thread:  Avg: 0.0153us  Range: [0.0153us, 0.0154us]  Ops/s:  65.26M  Ops/s/t:  65.26M
         2   threads:  Avg: 0.0334us  Range: [0.0328us, 0.0338us]  Ops/s:  59.82M  Ops/s/t:  29.91M
         4   threads:  Avg: 0.0672us  Range: [0.0654us, 0.0686us]  Ops/s:  59.51M  Ops/s/t:  14.88M
         Operations per second per thread (weighted average):  31.11M

  > boost::lockfree::queue
     (skipping, benchmark not supported...)

  > tbb::concurrent_queue
     (skipping, benchmark not supported...)

  > SimpleLockFreeQueue
     (skipping, benchmark not supported...)

  > LockBasedQueue
     (skipping, benchmark not supported...)

  > std::queue
     (skipping, benchmark not supported...)

only enqueue bulk (pre-allocated):
  (Measures the average speed of enqueueing an item in bulk when all threads are producers,
  and the queue has been stretched out first)
  > moodycamel::ConcurrentQueue
      Without tokens
         1    thread:  Avg: 0.0157us  Range: [0.0157us, 0.0158us]  Ops/s:  63.54M  Ops/s/t:  63.54M
         2   threads:  Avg: 0.0360us  Range: [0.0353us, 0.0364us]  Ops/s:  55.56M  Ops/s/t:  27.78M
         4   threads:  Avg: 0.0740us  Range: [0.0733us, 0.0746us]  Ops/s:  54.06M  Ops/s/t:  13.52M
         Operations per second per thread (weighted average):  29.42M

      With tokens
         1    thread:  Avg: 0.0153us  Range: [0.0153us, 0.0154us]  Ops/s:  65.32M  Ops/s/t:  65.32M
         2   threads:  Avg: 0.0337us  Range: [0.0333us, 0.0340us]  Ops/s:  59.30M  Ops/s/t:  29.65M
         4   threads:  Avg: 0.0676us  Range: [0.0665us, 0.0687us]  Ops/s:  59.14M  Ops/s/t:  14.78M
         Operations per second per thread (weighted average):  31.00M

  > boost::lockfree::queue
     (skipping, benchmark not supported...)

  > tbb::concurrent_queue
     (skipping, benchmark not supported...)

  > SimpleLockFreeQueue
     (skipping, benchmark not supported...)

  > LockBasedQueue
     (skipping, benchmark not supported...)

  > std::queue
     (skipping, benchmark not supported...)

only dequeue:
  (Measures the average operation speed when all threads are consumers)
  > moodycamel::ConcurrentQueue
      Without tokens
         1    thread:  Avg: 0.1165us  Range: [0.1163us, 0.1167us]  Ops/s:   8.58M  Ops/s/t:   8.58M
         2   threads:  Avg: 0.6081us  Range: [0.3983us, 0.6949us]  Ops/s:   3.29M  Ops/s/t:   1.64M
         4   threads:  Avg: 1.3773us  Range: [1.2187us, 1.5266us]  Ops/s:   2.90M  Ops/s/t: 726.07k
         Operations per second per thread (weighted average):   2.80M

      With tokens
         1    thread:  Avg: 0.1144us  Range: [0.1126us, 0.1165us]  Ops/s:   8.74M  Ops/s/t:   8.74M
         2   threads:  Avg: 0.2450us  Range: [0.2388us, 0.2477us]  Ops/s:   8.16M  Ops/s/t:   4.08M
         4   threads:  Avg: 0.4822us  Range: [0.4760us, 0.4851us]  Ops/s:   8.30M  Ops/s/t:   2.07M
         Operations per second per thread (weighted average):   4.23M

  > boost::lockfree::queue
     1    thread:  Avg: 0.1044us  Range: [0.1041us, 0.1045us]  Ops/s:   9.58M  Ops/s/t:   9.58M
     2   threads:  Avg: 0.6626us  Range: [0.6523us, 0.6739us]  Ops/s:   3.02M  Ops/s/t:   1.51M
     4   threads:  Avg: 1.4675us  Range: [1.3134us, 1.6303us]  Ops/s:   2.73M  Ops/s/t: 681.42k
     Operations per second per thread (weighted average):   2.96M

  > tbb::concurrent_queue
     1    thread:  Avg: 0.0962us  Range: [0.0958us, 0.0965us]  Ops/s:  10.40M  Ops/s/t:  10.40M
     2   threads:  Avg: 0.8670us  Range: [0.7704us, 0.9092us]  Ops/s:   2.31M  Ops/s/t:   1.15M
     4   threads:  Avg: 2.9464us  Range: [2.6072us, 3.2898us]  Ops/s:   1.36M  Ops/s/t: 339.40k
     Operations per second per thread (weighted average):   2.88M

  > SimpleLockFreeQueue
     1    thread:  Avg: 0.0879us  Range: [0.0877us, 0.0881us]  Ops/s:  11.38M  Ops/s/t:  11.38M
     2   threads:  Avg: 0.5947us  Range: [0.1847us, 0.7029us]  Ops/s:   3.36M  Ops/s/t:   1.68M
     4   threads:  Avg: 1.4889us  Range: [1.1207us, 1.7064us]  Ops/s:   2.69M  Ops/s/t: 671.62k
     Operations per second per thread (weighted average):   3.42M

  > LockBasedQueue
     1    thread:  Avg: 2.9784us  Range: [2.9697us, 2.9859us]  Ops/s: 335.75k  Ops/s/t: 335.75k
     2   threads:  Avg: 0.0265ms  Range: [0.0199ms, 0.0298ms]  Ops/s:  75.48k  Ops/s/t:  37.74k
     4   threads:  Avg: 0.1215ms  Range: [0.0902ms, 0.1350ms]  Ops/s:  32.91k  Ops/s/t:   8.23k
     Operations per second per thread (weighted average):  91.88k

  > std::queue
     1    thread:  Avg: 0.0168us  Range: [0.0168us, 0.0168us]  Ops/s:  59.49M  Ops/s/t:  59.49M
     Operations per second per thread (weighted average):  59.49M

only dequeue bulk:
  (Measures the average speed of dequeueing an item in bulk when all threads are consumers)
  > moodycamel::ConcurrentQueue
      Without tokens
         1    thread:  Avg: 5.5943ns  Range: [5.5849ns, 5.6040ns]  Ops/s: 178.75M  Ops/s/t: 178.75M
         2   threads:  Avg: 0.0184us  Range: [0.0176us, 0.0186us]  Ops/s: 108.83M  Ops/s/t:  54.41M
         4   threads:  Avg: 0.0318us  Range: [0.0314us, 0.0324us]  Ops/s: 125.90M  Ops/s/t:  31.47M
         Operations per second per thread (weighted average):  72.19M

      With tokens
         1    thread:  Avg: 5.0906ns  Range: [5.0838ns, 5.0961ns]  Ops/s: 196.44M  Ops/s/t: 196.44M
         2   threads:  Avg: 0.0121us  Range: [0.0120us, 0.0121us]  Ops/s: 165.13M  Ops/s/t:  82.57M
         4   threads:  Avg: 0.0243us  Range: [0.0240us, 0.0244us]  Ops/s: 164.89M  Ops/s/t:  41.22M
         Operations per second per thread (weighted average):  89.63M

  > boost::lockfree::queue
     (skipping, benchmark not supported...)

  > tbb::concurrent_queue
     (skipping, benchmark not supported...)

  > SimpleLockFreeQueue
     (skipping, benchmark not supported...)

  > LockBasedQueue
     (skipping, benchmark not supported...)

  > std::queue
     (skipping, benchmark not supported...)

mostly enqueue:
  (Measures the average operation speed when most threads are enqueueing)
  > moodycamel::ConcurrentQueue
      Without tokens
         2   threads:  Avg: 0.4424us  Range: [0.2400us, 0.5108us]  Ops/s:   4.52M  Ops/s/t:   2.26M
         4   threads:  Avg: 0.4271us  Range: [0.3528us, 0.4655us]  Ops/s:   9.36M  Ops/s/t:   2.34M
         Operations per second per thread (weighted average):   2.31M

      With tokens
         2   threads:  Avg: 0.2470us  Range: [0.2336us, 0.2581us]  Ops/s:   8.10M  Ops/s/t:   4.05M
         4   threads:  Avg: 0.2328us  Range: [0.1404us, 0.2728us]  Ops/s:  17.18M  Ops/s/t:   4.30M
         Operations per second per thread (weighted average):   4.19M

  > boost::lockfree::queue
     2   threads:  Avg: 0.4311us  Range: [0.4071us, 0.4438us]  Ops/s:   4.64M  Ops/s/t:   2.32M
     4   threads:  Avg: 1.8933us  Range: [1.6289us, 1.9567us]  Ops/s:   2.11M  Ops/s/t: 528.17k
     Operations per second per thread (weighted average):   1.27M

  > tbb::concurrent_queue
     2   threads:  Avg: 0.2621us  Range: [0.1655us, 0.3532us]  Ops/s:   7.63M  Ops/s/t:   3.82M
     4   threads:  Avg: 3.1446us  Range: [0.8478us, 3.8621us]  Ops/s:   1.27M  Ops/s/t: 318.01k
     Operations per second per thread (weighted average):   1.77M

  > SimpleLockFreeQueue
     2   threads:  Avg: 0.3761us  Range: [0.3144us, 0.4321us]  Ops/s:   5.32M  Ops/s/t:   2.66M
     4   threads:  Avg: 1.3820us  Range: [1.1667us, 1.5381us]  Ops/s:   2.89M  Ops/s/t: 723.59k
     Operations per second per thread (weighted average):   1.53M

  > LockBasedQueue
     2   threads:  Avg: 0.0219ms  Range: [6.6121us, 0.0253ms]  Ops/s:  91.25k  Ops/s/t:  45.62k
     4   threads:  Avg: 0.1021ms  Range: [0.0457ms, 0.1239ms]  Ops/s:  39.16k  Ops/s/t:   9.79k
     Operations per second per thread (weighted average):  24.63k

  > std::queue
     (skipping, benchmark not supported...)

mostly enqueue bulk:
  (Measures the average speed of enqueueing an item in bulk under light contention)
  > moodycamel::ConcurrentQueue
      Without tokens
         2   threads:  Avg: 0.0304us  Range: [0.0303us, 0.0306us]  Ops/s:  65.70M  Ops/s/t:  32.85M
         4   threads:  Avg: 0.0648us  Range: [0.0529us, 0.0719us]  Ops/s:  61.77M  Ops/s/t:  15.44M
         Operations per second per thread (weighted average):  22.65M

      With tokens
         2   threads:  Avg: 0.0284us  Range: [0.0282us, 0.0285us]  Ops/s:  70.53M  Ops/s/t:  35.26M
         4   threads:  Avg: 0.0562us  Range: [0.0482us, 0.0678us]  Ops/s:  71.14M  Ops/s/t:  17.78M
         Operations per second per thread (weighted average):  25.02M

  > boost::lockfree::queue
     (skipping, benchmark not supported...)

  > tbb::concurrent_queue
     (skipping, benchmark not supported...)

  > SimpleLockFreeQueue
     (skipping, benchmark not supported...)

  > LockBasedQueue
     (skipping, benchmark not supported...)

  > std::queue
     (skipping, benchmark not supported...)

mostly dequeue:
  (Measures the average operation speed when most threads are dequeueing)
  > moodycamel::ConcurrentQueue
      Without tokens
         2   threads:  Avg: 0.3370us  Range: [0.3165us, 0.3504us]  Ops/s:   5.94M  Ops/s/t:   2.97M
         4   threads:  Avg: 0.5739us  Range: [0.2841us, 0.6900us]  Ops/s:   6.97M  Ops/s/t:   1.74M
         Operations per second per thread (weighted average):   2.25M

      With tokens
         2   threads:  Avg: 0.2321us  Range: [0.2208us, 0.2407us]  Ops/s:   8.62M  Ops/s/t:   4.31M
         4   threads:  Avg: 0.4434us  Range: [0.2650us, 0.4834us]  Ops/s:   9.02M  Ops/s/t:   2.26M
         Operations per second per thread (weighted average):   3.11M

  > boost::lockfree::queue
     2   threads:  Avg: 0.6810us  Range: [0.6688us, 0.7021us]  Ops/s:   2.94M  Ops/s/t:   1.47M
     4   threads:  Avg: 0.6482us  Range: [0.3601us, 0.8150us]  Ops/s:   6.17M  Ops/s/t:   1.54M
     Operations per second per thread (weighted average):   1.51M

  > tbb::concurrent_queue
     2   threads:  Avg: 0.4504us  Range: [0.3686us, 0.4826us]  Ops/s:   4.44M  Ops/s/t:   2.22M
     4   threads:  Avg: 0.7818us  Range: [0.4763us, 0.9027us]  Ops/s:   5.12M  Ops/s/t:   1.28M
     Operations per second per thread (weighted average):   1.67M

  > SimpleLockFreeQueue
     2   threads:  Avg: 0.7089us  Range: [0.6896us, 0.7275us]  Ops/s:   2.82M  Ops/s/t:   1.41M
     4   threads:  Avg: 0.6683us  Range: [0.4217us, 0.9445us]  Ops/s:   5.99M  Ops/s/t:   1.50M
     Operations per second per thread (weighted average):   1.46M

  > LockBasedQueue
     2   threads:  Avg: 0.0213ms  Range: [0.0182ms, 0.0241ms]  Ops/s:  94.11k  Ops/s/t:  47.06k
     4   threads:  Avg: 0.1003ms  Range: [0.0451ms, 0.1279ms]  Ops/s:  39.86k  Ops/s/t:   9.97k
     Operations per second per thread (weighted average):  25.33k

  > std::queue
     (skipping, benchmark not supported...)

mostly dequeue bulk:
  (Measures the average speed of dequeueing an item in bulk under light contention)
  > moodycamel::ConcurrentQueue
      Without tokens
         2   threads:  Avg: 0.0260us  Range: [0.0258us, 0.0261us]  Ops/s:  76.84M  Ops/s/t:  38.42M
         4   threads:  Avg: 0.0456us  Range: [0.0453us, 0.0458us]  Ops/s:  87.76M  Ops/s/t:  21.94M
         Operations per second per thread (weighted average):  28.77M

      With tokens
         2   threads:  Avg: 0.0147us  Range: [0.0144us, 0.0148us]  Ops/s: 136.11M  Ops/s/t:  68.05M
         4   threads:  Avg: 0.0289us  Range: [0.0283us, 0.0297us]  Ops/s: 138.50M  Ops/s/t:  34.63M
         Operations per second per thread (weighted average):  48.47M

  > boost::lockfree::queue
     (skipping, benchmark not supported...)

  > tbb::concurrent_queue
     (skipping, benchmark not supported...)

  > SimpleLockFreeQueue
     (skipping, benchmark not supported...)

  > LockBasedQueue
     (skipping, benchmark not supported...)

  > std::queue
     (skipping, benchmark not supported...)

single-producer, multi-consumer (measuring all but 1 thread):
  (Measures the average speed of dequeueing with only one producer, but multiple consumers)
  > moodycamel::ConcurrentQueue
      Without tokens
         2   threads:  Avg: 0.1426us  Range: [0.1352us, 0.1462us]  Ops/s:   7.01M  Ops/s/t:   7.01M
         4   threads:  Avg: 1.2177us  Range: [0.9412us, 1.4320us]  Ops/s:   2.46M  Ops/s/t: 821.24k
         Operations per second per thread (weighted average):   3.09M

      With tokens
         2   threads:  Avg: 0.1504us  Range: [0.1360us, 0.1606us]  Ops/s:   6.65M  Ops/s/t:   6.65M
         4   threads:  Avg: 0.7253us  Range: [0.4129us, 0.9226us]  Ops/s:   4.14M  Ops/s/t:   1.38M
         Operations per second per thread (weighted average):   3.31M

  > boost::lockfree::queue
     2   threads:  Avg: 0.3493us  Range: [0.1637us, 0.5469us]  Ops/s:   2.86M  Ops/s/t:   2.86M
     4   threads:  Avg: 0.1801us  Range: [0.1380us, 0.2321us]  Ops/s:  16.66M  Ops/s/t:   5.55M
     Operations per second per thread (weighted average):   4.57M

  > tbb::concurrent_queue
     2   threads:  Avg: 0.1373us  Range: [0.1034us, 0.2169us]  Ops/s:   7.28M  Ops/s/t:   7.28M
     4   threads:  Avg: 0.2629us  Range: [0.1769us, 0.4252us]  Ops/s:  11.41M  Ops/s/t:   3.80M
     Operations per second per thread (weighted average):   5.08M

  > SimpleLockFreeQueue
     2   threads:  Avg: 0.3192us  Range: [0.0925us, 0.4334us]  Ops/s:   3.13M  Ops/s/t:   3.13M
     4   threads:  Avg: 0.3504us  Range: [0.2090us, 0.4648us]  Ops/s:   8.56M  Ops/s/t:   2.85M
     Operations per second per thread (weighted average):   2.96M

  > LockBasedQueue
     2   threads:  Avg: 8.7334us  Range: [3.6908us, 0.0137ms]  Ops/s: 114.50k  Ops/s/t: 114.50k
     4   threads:  Avg: 0.0936ms  Range: [8.8969us, 0.1140ms]  Ops/s:  32.04k  Ops/s/t:  10.68k
     Operations per second per thread (weighted average):  48.68k

  > std::queue
     (skipping, benchmark not supported...)

single-producer, multi-consumer (pre-produced):
  (Measures the average speed of dequeueing from a queue pre-filled by one thread)
  > moodycamel::ConcurrentQueue
      Without tokens
         1    thread:  Avg: 0.1162us  Range: [0.1161us, 0.1163us]  Ops/s:   8.60M  Ops/s/t:   8.60M
         3   threads:  Avg: 0.8814us  Range: [0.7712us, 0.9528us]  Ops/s:   3.40M  Ops/s/t:   1.13M
         Operations per second per thread (weighted average):   3.87M

      With tokens
         1    thread:  Avg: 0.1166us  Range: [0.1165us, 0.1168us]  Ops/s:   8.57M  Ops/s/t:   8.57M
         3   threads:  Avg: 0.8026us  Range: [0.5731us, 0.9180us]  Ops/s:   3.74M  Ops/s/t:   1.25M
         Operations per second per thread (weighted average):   3.93M

  > boost::lockfree::queue
     1    thread:  Avg: 0.1049us  Range: [0.1047us, 0.1051us]  Ops/s:   9.53M  Ops/s/t:   9.53M
     3   threads:  Avg: 0.9315us  Range: [0.8573us, 0.9574us]  Ops/s:   3.22M  Ops/s/t:   1.07M
     Operations per second per thread (weighted average):   4.17M

  > tbb::concurrent_queue
     1    thread:  Avg: 0.0956us  Range: [0.0951us, 0.0958us]  Ops/s:  10.47M  Ops/s/t:  10.47M
     3   threads:  Avg: 2.4941us  Range: [1.9916us, 2.5995us]  Ops/s:   1.20M  Ops/s/t: 400.95k
     Operations per second per thread (weighted average):   4.08M

  > SimpleLockFreeQueue
     1    thread:  Avg: 0.0878us  Range: [0.0877us, 0.0879us]  Ops/s:  11.39M  Ops/s/t:  11.39M
     3   threads:  Avg: 0.8759us  Range: [0.7589us, 0.9733us]  Ops/s:   3.42M  Ops/s/t:   1.14M
     Operations per second per thread (weighted average):   4.89M

  > LockBasedQueue
     1    thread:  Avg: 2.9825us  Range: [2.9711us, 2.9880us]  Ops/s: 335.29k  Ops/s/t: 335.29k
     3   threads:  Avg: 0.0510ms  Range: [0.0273ms, 0.0622ms]  Ops/s:  58.87k  Ops/s/t:  19.62k
     Operations per second per thread (weighted average): 135.17k

  > std::queue
     (skipping, benchmark not supported...)

multi-producer, single-consumer (measuring 1 thread):
  (Measures the average speed of dequeueing with only one consumer, but multiple producers)
  > moodycamel::ConcurrentQueue
      Without tokens
         2   threads:  Avg: 0.2415us  Range: [0.2096us, 0.2516us]  Ops/s:   4.14M  Ops/s/t:   4.14M
         4   threads:  Avg: 0.1585us  Range: [0.1453us, 0.1621us]  Ops/s:   6.31M  Ops/s/t:   6.31M
         Operations per second per thread (weighted average):   5.22M

      With tokens
         2   threads:  Avg: 0.1733us  Range: [0.1679us, 0.1763us]  Ops/s:   5.77M  Ops/s/t:   5.77M
         4   threads:  Avg: 0.1284us  Range: [0.1237us, 0.1312us]  Ops/s:   7.79M  Ops/s/t:   7.79M
         Operations per second per thread (weighted average):   6.78M

  > boost::lockfree::queue
     2   threads:  Avg: 0.5650us  Range: [0.5312us, 0.5794us]  Ops/s:   1.77M  Ops/s/t:   1.77M
     4   threads:  Avg: 0.1195us  Range: [0.1085us, 0.1307us]  Ops/s:   8.37M  Ops/s/t:   8.37M
     Operations per second per thread (weighted average):   5.07M

  > tbb::concurrent_queue
     2   threads:  Avg: 0.2632us  Range: [0.2479us, 0.2771us]  Ops/s:   3.80M  Ops/s/t:   3.80M
     4   threads:  Avg: 0.2673us  Range: [0.2162us, 0.4811us]  Ops/s:   3.74M  Ops/s/t:   3.74M
     Operations per second per thread (weighted average):   3.77M

  > SimpleLockFreeQueue
     2   threads:  Avg: 0.4706us  Range: [0.4295us, 0.5352us]  Ops/s:   2.12M  Ops/s/t:   2.12M
     4   threads:  Avg: 0.1236us  Range: [0.0986us, 0.1327us]  Ops/s:   8.09M  Ops/s/t:   8.09M
     Operations per second per thread (weighted average):   5.11M

  > LockBasedQueue
     2   threads:  Avg: 0.0126ms  Range: [8.7123us, 0.0143ms]  Ops/s:  79.55k  Ops/s/t:  79.55k
     4   threads:  Avg: 0.0127ms  Range: [0.0118ms, 0.0131ms]  Ops/s:  78.95k  Ops/s/t:  78.95k
     Operations per second per thread (weighted average):  79.25k

  > std::queue
     (skipping, benchmark not supported...)

dequeue from empty:
  (Measures the average speed of attempting to dequeue from an empty queue
  (that eight separate threads had at one point enqueued to))
  > moodycamel::ConcurrentQueue
      Without tokens
         1    thread:  Avg: 0.1010us  Range: [0.1010us, 0.1011us]  Ops/s:   9.90M  Ops/s/t:   9.90M
                ^ Note: No contention -- measures raw failed dequeue speed on empty queue
         2   threads:  Avg: 0.2152us  Range: [0.2145us, 0.2155us]  Ops/s:   9.29M  Ops/s/t:   4.65M
         Operations per second per thread (weighted average):   6.82M

      With tokens
         1    thread:  Avg: 0.0407us  Range: [0.0399us, 0.0435us]  Ops/s:  24.56M  Ops/s/t:  24.56M
                ^ Note: No contention -- measures raw failed dequeue speed on empty queue
         2   threads:  Avg: 0.0876us  Range: [0.0865us, 0.0897us]  Ops/s:  22.83M  Ops/s/t:  11.42M
         Operations per second per thread (weighted average):  16.86M

  > boost::lockfree::queue
     1    thread:  Avg: 0.0192us  Range: [0.0192us, 0.0192us]  Ops/s:  52.10M  Ops/s/t:  52.10M
            ^ Note: No contention -- measures raw failed dequeue speed on empty queue
     2   threads:  Avg: 0.0431us  Range: [0.0427us, 0.0433us]  Ops/s:  46.37M  Ops/s/t:  23.18M
     Operations per second per thread (weighted average):  35.16M

  > tbb::concurrent_queue
     1    thread:  Avg: 0.0142us  Range: [0.0142us, 0.0142us]  Ops/s:  70.57M  Ops/s/t:  70.57M
            ^ Note: No contention -- measures raw failed dequeue speed on empty queue
     2   threads:  Avg: 0.0322us  Range: [0.0318us, 0.0324us]  Ops/s:  62.11M  Ops/s/t:  31.05M
     Operations per second per thread (weighted average):  47.42M

  > SimpleLockFreeQueue
     1    thread:  Avg: 0.0273us  Range: [0.0273us, 0.0273us]  Ops/s:  36.59M  Ops/s/t:  36.59M
            ^ Note: No contention -- measures raw failed dequeue speed on empty queue
     2   threads:  Avg: 0.0606us  Range: [0.0589us, 0.0612us]  Ops/s:  32.98M  Ops/s/t:  16.49M
     Operations per second per thread (weighted average):  24.81M

  > LockBasedQueue
     1    thread:  Avg: 2.8034us  Range: [2.8004us, 2.8059us]  Ops/s: 356.71k  Ops/s/t: 356.71k
            ^ Note: No contention -- measures raw failed dequeue speed on empty queue
     2   threads:  Avg: 0.0215ms  Range: [0.0167ms, 0.0259ms]  Ops/s:  92.96k  Ops/s/t:  46.48k
     Operations per second per thread (weighted average): 174.98k

  > std::queue
     1    thread:  Avg: 5.0446ns  Range: [5.0418ns, 5.0477ns]  Ops/s: 198.23M  Ops/s/t: 198.23M
            ^ Note: No contention -- measures raw failed dequeue speed on empty queue
     Operations per second per thread (weighted average): 198.23M

enqueue-dequeue pairs:
  (Measures the average operation speed with each thread doing an enqueue
  followed by a dequeue)
  > moodycamel::ConcurrentQueue
      Without tokens
         1    thread:  Avg: 0.0923us  Range: [0.0923us, 0.0924us]  Ops/s:  10.83M  Ops/s/t:  10.83M
                ^ Note: No contention -- measures speed of immediately dequeueing the item that was just enqueued
         2   threads:  Avg: 0.4628us  Range: [0.3523us, 0.4883us]  Ops/s:   4.32M  Ops/s/t:   2.16M
         4   threads:  Avg: 0.7692us  Range: [0.6056us, 0.8575us]  Ops/s:   5.20M  Ops/s/t:   1.30M
         Operations per second per thread (weighted average):   3.73M

      With tokens
         1    thread:  Avg: 0.0674us  Range: [0.0673us, 0.0674us]  Ops/s:  14.84M  Ops/s/t:  14.84M
                ^ Note: No contention -- measures speed of immediately dequeueing the item that was just enqueued
         2   threads:  Avg: 0.1482us  Range: [0.1469us, 0.1491us]  Ops/s:  13.49M  Ops/s/t:   6.75M
         4   threads:  Avg: 0.2887us  Range: [0.2812us, 0.2943us]  Ops/s:  13.85M  Ops/s/t:   3.46M
         Operations per second per thread (weighted average):   7.09M

  > boost::lockfree::queue
     1    thread:  Avg: 0.1063us  Range: [0.1061us, 0.1065us]  Ops/s:   9.41M  Ops/s/t:   9.41M
            ^ Note: No contention -- measures speed of immediately dequeueing the item that was just enqueued
     2   threads:  Avg: 0.5539us  Range: [0.4211us, 0.6343us]  Ops/s:   3.61M  Ops/s/t:   1.81M
     4   threads:  Avg: 1.0231us  Range: [0.5522us, 1.2066us]  Ops/s:   3.91M  Ops/s/t: 977.46k
     Operations per second per thread (weighted average):   3.15M

  > tbb::concurrent_queue
     1    thread:  Avg: 0.0894us  Range: [0.0892us, 0.0895us]  Ops/s:  11.18M  Ops/s/t:  11.18M
            ^ Note: No contention -- measures speed of immediately dequeueing the item that was just enqueued
     2   threads:  Avg: 0.6722us  Range: [0.6325us, 0.7061us]  Ops/s:   2.98M  Ops/s/t:   1.49M
     4   threads:  Avg: 2.2212us  Range: [0.3867us, 3.7638us]  Ops/s:   1.80M  Ops/s/t: 450.21k
     Operations per second per thread (weighted average):   3.21M

  > SimpleLockFreeQueue
     1    thread:  Avg: 0.1370us  Range: [0.1369us, 0.1371us]  Ops/s:   7.30M  Ops/s/t:   7.30M
            ^ Note: No contention -- measures speed of immediately dequeueing the item that was just enqueued
     2   threads:  Avg: 0.7587us  Range: [0.6783us, 0.8147us]  Ops/s:   2.64M  Ops/s/t:   1.32M
     4   threads:  Avg: 1.2061us  Range: [1.0951us, 1.2795us]  Ops/s:   3.32M  Ops/s/t: 829.13k
     Operations per second per thread (weighted average):   2.45M

  > LockBasedQueue
     1    thread:  Avg: 3.0499us  Range: [3.0363us, 3.0647us]  Ops/s: 327.88k  Ops/s/t: 327.88k
            ^ Note: No contention -- measures speed of immediately dequeueing the item that was just enqueued
     2   threads:  Avg: 0.0230ms  Range: [0.0150ms, 0.0287ms]  Ops/s:  86.81k  Ops/s/t:  43.41k
     4   threads:  Avg: 0.0814ms  Range: [0.0347ms, 0.1144ms]  Ops/s:  49.13k  Ops/s/t:  12.28k
     Operations per second per thread (weighted average):  93.75k

  > std::queue
     1    thread:  Avg: 0.0143us  Range: [0.0143us, 0.0143us]  Ops/s:  69.99M  Ops/s/t:  69.99M
            ^ Note: No contention -- measures speed of immediately dequeueing the item that was just enqueued
     Operations per second per thread (weighted average):  69.99M

heavy concurrent:
  (Measures the average operation speed with many threads under heavy load)
  > moodycamel::ConcurrentQueue
      Without tokens
         2   threads:  Avg: 0.4484us  Range: [0.3894us, 0.4837us]  Ops/s:   4.46M  Ops/s/t:   2.23M
         3   threads:  Avg: 0.5666us  Range: [0.5258us, 0.5872us]  Ops/s:   5.30M  Ops/s/t:   1.77M
         4   threads:  Avg: 0.6681us  Range: [0.4460us, 0.8570us]  Ops/s:   5.99M  Ops/s/t:   1.50M
         Operations per second per thread (weighted average):   1.79M

      With tokens
         2   threads:  Avg: 0.1509us  Range: [0.1499us, 0.1518us]  Ops/s:  13.26M  Ops/s/t:   6.63M
         3   threads:  Avg: 0.2381us  Range: [0.1902us, 0.2797us]  Ops/s:  12.60M  Ops/s/t:   4.20M
         4   threads:  Avg: 0.4002us  Range: [0.3306us, 0.4937us]  Ops/s:   9.99M  Ops/s/t:   2.50M
         Operations per second per thread (weighted average):   4.21M

  > boost::lockfree::queue
     2   threads:  Avg: 0.5976us  Range: [0.2773us, 0.6748us]  Ops/s:   3.35M  Ops/s/t:   1.67M
     3   threads:  Avg: 1.0917us  Range: [0.9830us, 1.1773us]  Ops/s:   2.75M  Ops/s/t: 916.02k
     4   threads:  Avg: 1.1793us  Range: [1.0690us, 1.2686us]  Ops/s:   3.39M  Ops/s/t: 847.94k
     Operations per second per thread (weighted average):   1.10M

  > tbb::concurrent_queue
     2   threads:  Avg: 0.5967us  Range: [0.4621us, 0.6802us]  Ops/s:   3.35M  Ops/s/t:   1.68M
     3   threads:  Avg: 0.9434us  Range: [0.3221us, 1.1513us]  Ops/s:   3.18M  Ops/s/t:   1.06M
     4   threads:  Avg: 1.3151us  Range: [0.4924us, 1.7355us]  Ops/s:   3.04M  Ops/s/t: 760.40k
     Operations per second per thread (weighted average):   1.11M

  > SimpleLockFreeQueue
     2   threads:  Avg: 0.7336us  Range: [0.6230us, 0.8236us]  Ops/s:   2.73M  Ops/s/t:   1.36M
     3   threads:  Avg: 0.6925us  Range: [0.4716us, 0.8721us]  Ops/s:   4.33M  Ops/s/t:   1.44M
     4   threads:  Avg: 0.6623us  Range: [0.5031us, 0.8796us]  Ops/s:   6.04M  Ops/s/t:   1.51M
     Operations per second per thread (weighted average):   1.45M

  > LockBasedQueue
     2   threads:  Avg: 0.0210ms  Range: [0.0127ms, 0.0273ms]  Ops/s:  95.11k  Ops/s/t:  47.56k
     3   threads:  Avg: 0.0370ms  Range: [9.9256us, 0.0798ms]  Ops/s:  81.04k  Ops/s/t:  27.01k
     4   threads:  Avg: 0.0970ms  Range: [0.0147ms, 0.1225ms]  Ops/s:  41.22k  Ops/s/t:  10.31k
     Operations per second per thread (weighted average):  26.17k

  > std::queue
     (skipping, benchmark not supported...)

Overall average operations per second per thread (where higher-concurrency runs have more weight):
(Take this summary with a grain of salt -- look at the individual benchmark results for a much
better idea of how the queues measure up to each other):
    moodycamel::ConcurrentQueue (including bulk):  16.69M
    boost::lockfree::queue:   4.07M
    tbb::concurrent_queue:   4.97M
    SimpleLockFreeQueue:   3.53M
    LockBasedQueue:  75.32k
    std::queue (single thread only):  84.37M

```

As you can see, my queue is generally at least as good as the next fastest implementation, and often much faster (especially the bulk operations, which are in a league of their own!). The only benchmark that shows a significant reduction in throughput with respect to the other queues is the 'dequeue from empty' one, which is the worst case dequeue scenario for my implementation because it has to check *all* the inner queues; it's still faster on average than a successful dequeue operation, though. The impact of this can also be seen in certain other benchmarks, such as the single-producer multi-consumer one, where most of the dequeue operations fail (because the producer can't keep up with demand), and the relative throughput of my queue compared to the others suffers slightly as a result. Also note that the LockBasedQueue is extremely slow on my Windows netbook; I think this is a quality-of-implementation issue with the MinGW `std::mutex`, which does not use Windows's efficient `CRITICAL_SECTION` and seems to suffer terribly as a result.

I think the most important thing that can be gleaned from these benchmarks is that the total system throughput *at best* stays roughly constant as the number of threads increases (and often falls off anyway). This means that even with the fastest queue of each benchmark, the amount of work each thread accomplishes individually declines with each thread added, with approximately the same total amount of work being accomplished by e.g. four threads and sixteen. And that's the best case. There is no linear scaling at this level of contention and throughput; the moral of the story is to stay away from designs that require intensively sharing data if you care about performance. Of course, real applications tend not to be purely moving things around in queues, and thus have far lower contention and *do* have the possibility of scaling upwards (at least a little) as threads are added.

On the whole, I'm very happy with the results. Enjoy the queue!

# [Detailed Design of a Lock-Free Queue](http://moodycamel.com/blog/2014/detailed-design-of-a-lock-free-queue)

3 years ago

This post outlines, in quite some detail, my design for an efficient lock-free queue that supports multiple concurrent producers and consumers (an MPMC queue). My C++11 implementation of this design can be found [on GitHub](https://github.com/cameron314/concurrentqueue). A more high-level overview of the queue, including benchmark results, can be found in my [initial blog post introducing my implementation](http://moodycamel.com/blog/2014/a-fast-general-purpose-lock-free-queue-for-c++).

One key insight is that the total order that elements come out of a queue is irrelevant, as long as the order that they went in on a given thread matches the order they come out on another thread. This means that the queue can be safely implemented as a set of independent queues, one for each producer; writing a single-producer, multi-consumer lock free queue is much easier than writing a multi-producer, multi-consumer one, and can be implemented much more efficiently too. The SPMC queue can be generalized to an MPMC one by having the consumers pull from different SPMC queues as needed (and this can be done efficiently as well with some cleverness). A heuristic is used to speed up dequeueing in the typical case, pairing consumers with producers as much as possible (which drastically reduces the overall contention in the system).

Apart from the high-level set-of-SPMC-queues design, the other key part of the queue is the core queueing algorithm itself, which is brand new (I devised it myself) and unlike any other that I've heard of. It uses an atomic counter to track how many elements are available, and once one or more have been claimed (by incrementing a corresponding elements-consumed counter and checking whether that increment turned out to be valid), an atomic index can be safely incremented to get the actual IDs of the elements to reference. The problem is then reduced to mapping integer IDs to individual elements, without having to worry about other threads referencing the same objects (each ID is given to only one thread). Details are below!

## System Overview

The queue is composed of a series of single-producer, multi-consumer (SPMC) queues. There is one SPMC queue per producer; the consumers use a heuristic to determine which of these queues to consume from next. The queue is lock-free (though not quite wait-free). It is designed to be robust, insanely fast (especially on x86), and allow bulk enqueueing and dequeueing with very little additional overhead (vs. single items).

Some thread-local data is required for each producer, and thread-local data can also optionally be used to speed up consumers. This thread-local data can be either associated with user-allocated *tokens*, or, to simplify the interface, if no token is provided by the user for a producer, a lock-free hash table is used (keyed to the current thread ID) to look up a thread-local producer queue: An SPMC queue is created for each explicitly allocated producer token, and another *implicit* one for each thread that produces items without providing a token. Since tokens contain what amounts to thread-specific data, they should never be used from multiple threads simultaneously (although it's OK to transfer ownership of a token to another thread; in particular, this allows tokens to be used inside thread-pool tasks even if the thread running the task changes part-way through).

All the producer queues link themselves together into a lock-free linked list. When an explicit producer no longer has elements being added to it (i.e. its token is destroyed), it's flagged as being unassociated to any producer, but it's kept in the list and its memory is not freed; the next new producer reuses the old producer's memory (the lock-free producer list is add-only this way). Implicit producers are never destroyed (until the high-level queue itself is) since there's no way to know whether a given thread is done using the data structure or not. Note that the worst-case dequeue speed depends on how many producers queues there are, even if they're all empty.

There is a fundamental difference in the lifetimes of the explicit versus implicit producer queues: the explicit one has a finite production lifetime tied to the token's lifetime, whereas the implicit one has an unbounded lifetime and exists for the duration of the high-level queue itself. Because of this, two slightly different SPMC algorithms are used in order to maximize both speed and memory usage. In general, the explicit producer queue is designed to be slightly faster and hog slightly more memory, while the implicit producer queue is designed to be slightly slower but recycle more memory back into the high-level queue's global pool. For best speed, always use an explicit token (unless you find it too inconvenient).

Any memory allocated is only freed when the high-level queue is destroyed (though there are several re-use mechanisms). Memory allocation can be done up front, with operations that fail if there's not enough memory (instead of allocating more). Various default size parameters (and the memory allocation functions used by the queue) can be overridden by the used if desired.

## Full API (pseudocode)

```
# Allocates more memory if necessary
enqueue(item) : bool
enqueue(prod_token, item) : bool
enqueue_bulk(item_first, count) : bool
enqueue_bulk(prod_token, item_first, count) : bool

# Fails if not enough memory to enqueue
try_enqueue(item) : bool
try_enqueue(prod_token, item) : bool
try_enqueue_bulk(item_first, count) : bool
try_enqueue_bulk(prod_token, item_first, count) : bool

# Attempts to dequeue from the queue (never allocates)
try_dequeue(item&) : bool
try_dequeue(cons_token, item&) : bool
try_dequeue_bulk(item_first, max) : size_t
try_dequeue_bulk(cons_token, item_first, max) : size_t

# If you happen to know which producer you want to dequeue from
try_dequeue_from_producer(prod_token, item&) : bool
try_dequeue_bulk_from_producer(prod_token, item_first, max) : size_t

# A not-necessarily-accurate count of the total number of elements
size_approx() : size_t

```

## Producer Queue (SPMC) Design

### Shared Design Across Implicit and Explicit Versions

The producer queue is made up of blocks (both the explicit and implicit producer queues use the same block objects to allow better memory sharing). Initially, it starts out with no blocks. Each block can hold a fixed number of elements (all blocks have the same capacity which is a power of 2). Additionally, blocks contain a flag indicating whether a filled slot has been completely consumed or not (used by the explicit version to determine when a block is empty), as well as an atomic counter of the number of elements completely dequeued (used by the implicit version to determine when a block is empty).

The producer queue, for the purposes of lock-free manipulation, can be thought of as an abstract infinite array. A *tail index* indicates the next available slot for the producer to fill; it also doubles as the count of the number of elements ever enqueued (the *enqueue count*). The tail index is written to solely by the producer, and always increases (except when it overflows and wraps around, which is still considered "increasing" for our purposes). Producing an item is trivial owing to the fact that only a single thread is updating the variables involved. A *head index* indicates what element can be consumed next. The head index is atomically incremented by the consumers, potentially concurrently. In order to prevent the head index from reaching/passing the perceived tail index, an additional atomic counter is employed: the *dequeue count*. The dequeue count is optimistic, i.e. it is incremented by consumers when they speculatively believe there is something to dequeue. If the value of the dequeue count after being incremented is less than the enqueue count (tail), then there is guaranteed to be at least one element to dequeue (even taking concurrency into account), and it is safe to increment the head index, knowing that it will be less than the tail index afterwards. On the other hand, if after being incremented the dequeue count exceeds (or is equal to) the tail, the dequeue operation fails and the dequeue count is logically decremented (to keep it eventually consistent with the enqueue count): this could be done by directly decrementing the dequeue count, but instead (to increase parallelism and keep all variables involved increasing monotonically) a *dequeue overcommit* counter is incremented instead. To get the *logical* value of the dequeue count, we subtract the dequeue overcommit value from the dequeue count variable.

When consuming, once a valid index is determined as outlined above, it still needs to be mapped to a block and an offset into that block; some sort of indexing data structure is used for this purpose (which one depends on whether it's an implicit or explicit queue). Finally, the element can be moved out, and some sort of status is updated so that it's possible to eventually know when the block is completely spent. Full descriptions of these mechanisms are provided below in the individual sections covering the implicit- and explicit-specific details.

The tail and head indices/counts, as previously mentioned, will eventually overflow. This is expected and taken into account. The index/count is thus considered as existing on a circle the size of the maximum integer value (akin to a circle of 360 degrees, where 359 precedes 1). In order to check whether one index/count, say `a`, comes before another, say `b`, (i.e., logical less-than) we must determine whether `a` is closer to `b` via a clockwise arc on the circle or not. The following algorithm for circular less-than is used (32-bit version): `a < b` becomes `a - b > (1U << 31U)`. `a <= b` becomes `a - b - 1ULL > (1ULL << 31ULL)`. Note that circular subtraction "just works" with normal unsigned integers (assuming two's complement). Care is taken to ensure that the tail index is not incremented past the head index (which would corrupt the queue). Note that despite this, there's still technically a race condition in which an index value that the consumer (or producer, for that matter) sees is so stale that it's almost a whole circle's worth (or more!) behind its current value, causing the internal state of the queue to become corrupted. In practice, however, this is not an issue, because it takes a while to go through 2^31 values (for a 32-bit index type) and the other cores would see something more up to date by then. In fact, many lock-free algorithms are based on a related tag-pointer idiom, where the first 16 bits is used for a tag that gets repeatedly incremented, and the second 16 bits for a pointer value; this relies on the similar assumption that one core can't increment the tag more than 2^15 times without the other cores knowing so. Nevertheless, the default index type for the queue is 64 bits wide (which, if even 16-bits seems to be enough, should prevent any potential races even in theory).

Memory allocation failure is also handled properly and will never corrupt the queue (it is simply reported as failure). However, the elements themselves are assumed never to throw exceptions while being manipulated by the queue.

### Block Pools

There are two different pools of blocks that are used: First, there is the initial array of pre-allocated blocks. Once consumed, this pool remains empty forever. This simplifies its wait-free implementation to a single fetch-and-add atomic instruction (to get the next index of a free block) with a check (to make sure that that index is in range). Second, there is a lock-free (though not wait-free) global free list ("global" meaning global to the high-level queue) of spent blocks that are ready to be re-used, implemented as a lock-free singly linked list: A head pointer initially points to nothing (null). To add a block to the free list, the block's *next* pointer is set to the head pointer, and the head pointer is then updated to point at the block using compare-and-swap (CAS) on the condition that the head hasn't changed; if it has, the process is repeated (this is a classic lock-free CAS-loop design pattern). To remove a block from the free list, a similar algorithm is used: the head block's next pointer is read, and the head is then set to that next pointer (using CAS) conditional to the fact that the head hasn't changed in the meantime. To avoid the ABA problem, each block has a reference count which is incremented before doing the CAS to remove a block, and decremented afterwards; if an attempt is made to re-add a block to the free list while its reference count is above 0, then a flag indicating that the block should be on the free list is set, and the next thread that finishes holding the last reference checks this flag and adds the block to the list at that time (this works because we don't care about order). I've described the exact design and implementation of this lock-free free list in further detail [in another blog post](http://moodycamel.com/blog/2014/solving-the-aba-problem-for-lock-free-free-lists). When a producer queue needs a new block, it first checks the initial block pool, then the global free list, and only if it can't find a free block there does it allocate a new one on the heap (or fail, if memory allocation is not allowed).

### Explicit Producer Queue

The explicit producer queue is implemented as a circular singly-linked list of blocks. It is wait-free on the fast path, but merely lock-free when a block needs to be acquired from a block pool (or a new one allocated); this only happens when its internal cache of blocks are all full (or there are none, which is the case at the beginning).

Once a block is added into an explicit producer queue's circular linked list, it is never removed. A *tail block*pointer is maintained by the producer that points to the block which elements are currently being inserted into; when the tail block is full, the next block is checked to determine if it is empty. If it is, the tail block pointer is updated to point to that block; if not, a new block is requisitioned and inserted into the linked list immediately following the current tail block, which is then updated to point to this new block.

When an element is finished being dequeued from a block, a per-element flag is set to indicate that the slot is full. (Actually, all the flags start off set, and are only turned off when the slot becomes empty.) The producer checks if a block is empty by checking all of these flags. If the block size is small, this is fast enough; otherwise, for larger blocks, instead of the flag system, a block-level atomic count is incremented each time a consumer finishes with an element. When this count is equal to the size of the block, or all the flags are off, the block is empty and can be safely re-used.

To index the blocks in constant time (i.e. quickly find the block that an element is in given that element's global index from the dequeue algorithm), a circular buffer (contiguous array) is used. This index is maintained by the producer; consumers read from it but never write to it. At the *front* of the array is the most recently written-to block (the tail block). At the *rear* of the array is the last block that may have elements in it. It is helpful to think of this index (from a high-level perspective) as a single long ribbon of the history of which blocks have been used. The front is incremented whenever the producer starts on another block (which may be either newly allocated or re-used from its circular list of blocks). The rear is incremented whenever a block that was already in the circular list is re-used (since blocks are only re-used when they're empty, it is always safe to increment the rear in this case). Instead of storing the rear explicitly, a count of the number of slots used is kept (this avoids the need for a spare element in the circular buffer, and simplifies the implementation). If there is not enough room in the index to add a new item, a new index array is allocated which is twice the size of the previous array (obviously, this is only allowed if memory allocation is allowed -- if not, the entire enqueue operation fails gracefully). Since consumers could still be using the old index, it is not freed, but instead simply linked to the new one (this forms a chain of index blocks that can be properly freed when the high-level queue is destructed). When the enqueue count of the producer queue is incremented, it releases all writes to the index; when a consumer performs an acquire (which it already needs for the dequeue algorithm), then from that point on whichever index the consumer sees will contain a reference to the block that the consumer is interested in. Since blocks are all the same size, and a power of 2, we can use shifting and masking to determine the number of blocks our target block is offset from any other block in the index (and the offset in the target block) provided we know the base index of a given block in the index. So, the index contains not just block pointers, but also the corresponding base index of each of those blocks. The block that is chosen as a reference point (to compute an offset against) in the index must be not be overwritten by the producer while it is being used -- using the (perceived) front of the index as the reference point guarantees this since (knowing the block index is at least as up to date as the enqueue count which is ahead the dequeue index we're looking up) the front of the index has to be at or ahead of the target block, and the target block will never be overwritten in the index until it (and all blocks before) is empty, and it can't be empty until after the dequeue operation itself finishes. The index size is a power of two, which allows for faster wrapping of the front/rear variables.

The explicit producer queue requires a user-allocated "producer token" to be passed when enqueueing. This token merely contains a pointer to a producer queue object. When the token is created, a corresponding producer queue is created; when the token is destroyed, the producer queue may still contain unconsumed elements, and hence the queue itself outlives the token. In fact, once allocated, a producer queue is never destroyed (until the high-level queue is destructed), but it *is* re-used the next time a producer token is created (instead of resulting in a heap allocation for a new producer queue).

### Implicit Producer Queue

The implicit producer queue is implemented as an un-linked set of blocks. It lock-free, but not wait-free, because the implementation of the main free block free-list is lock-free, and blocks are continuously acquired from and inserted back into that pool (resizing the block index is also not constant-time, and requires memory allocation). The actual enqueue and dequeue operations are still wait free within a single block.

A *current* block pointer is maintained; this is the block that is currently being enqueued in. When a block fills up, a new one is requisitioned, and the old one (from the producer's perspective) is forgotten about. Before an element is added to a block, the block is inserted in the block index (which allows consumers to find blocks the producer has already forgotten about). When the last element in a block is finished being consumed, the block is logically removed from the block index.

The implicit producer queue is never re-used -- once created, it lives throughout the lifetime of the high-level queue. So, in order to reduce memory consumption, instead of hogging all the blocks that it once used (like the explicit producer), it instead returns spent blocks to the global free list. To do this, an atomic dequeue count in each block is incremented as soon as a consumer is done dequeueing an item; when the counter reaches the size of the block, the consumer that sees this knows that it just dequeued the last item, and puts the block in the global free list.

The implicit producer queue uses a circular buffer to implement its block index, which allows constant-time searching for a block given its base index. Each index entry is composed of a key-value pair representing the base index of the block, and a pointer to the corresponding block itself. Since blocks are always inserted in order, the base index of each block in the index is guaranteed to increase by exactly one block size's worth between adjacent entries. This means that any block that's known to be in the index can be easily found by looking at the last inserted base index, computing the offset to the desired base index, and looking up the index entry at that offset. Special care is taken to ensure that the arithmetic still works out when the block index wraps around (although it is assumed that at any given moment the same base index won't be present (or seen to be present) twice in the index).

When a block is spent, it is removed from the index (to make room for future block insertions); since another consumer could still be making use of that entry in the index (to calculate an offset), the index entry is not removed completely, but the block pointer is set to null instead, indicating to the producer that the slot can be re-used; the block base is left untouched for any consumers that are still using it to calculate an offset. Since the producer only re-uses a slot once all preceding slots are also free, and when a consumer is looking up a block in the index there's necessarily at least one non-free slot in the index (corresponding to the block it's looking up), and the block index entry the consumer is using to look up a block is at least as recently enqueued as that the one for that block, there is never a race condition between the producer re-using a slot and a consumer looking up a block using that slot.

When the consumer wishes to enqueue an item and there's no room in the block index, it (if permitted) allocates another block index (linked to the old one so that its memory can eventually be freed when the queue is destructed), which becomes the main index from then on. The new index is a copy of the old one, except twice as large; copying over all the index entries allows consumers to only have to look in one index to find the block they're after (constant time dequeueing within a block). Because a consumer could be in the process of marking an index entry as being free (by setting the block pointer to null) when the new index is being constructed, the index entries themselves are not copied, but rather pointers to them. This ensures that any change to an old index by a consumer properly affects the current index as well.

## Hash of Implicit Producer Queues

A lock-free hash-table is used to map thread IDs to implicit producers; this is used when no explicit producer token is provided for the various enqueueing methods. It is based on [Jeff Preshing's lock-free hash algorithm](http://preshing.com/20130605/the-worlds-simplest-lock-free-hash-table), with a few adjustments: the keys are the same size as a platform-dependent numeric thread ID type; the values are pointers; when the hash becomes too small (the number of elements is tracked with an atomic counter) a new one is allocated and linked to the old one, and elements in the old one are transferred lazily as they are read. Since an atomic count of the number of elements is available, and elements are never deleted, a thread that wishes to insert an element in a hash table that is too small must attempt to resize or wait for another thread to finish resizing (resizing is protected with a lock to prevent spurious allocations). In order to speed up resizing under contention (i.e. minimize the amount of spin-waiting that threads do waiting for another thread to finish allocating), items can be inserted in the old hash table up to a threshold that is significantly greater than the threshold that triggers resizing (e.g. a load factor of 0.5 would cause a resize to commence, and in the meantime elements can be inserted in the old one up to a load factor of 0.75, say).

## Linked List of Producers

As mentioned previously, a singly-linked (LIFO) list of all the producers is maintained. This list is implemented using a tail pointer, and intrusive *next* (really, "prev") pointers for each producer. The tail initially points to null; when a new producer is created, it adds itself to the list by first reading the tail, then using setting its next to that tail, then using a CAS operation to set the tail (if it hasn't changed) to the new producer (looping as necessary). Producers are never removed from the list, but can be marked as inactive.

When a consumer wants to dequeue an item, it simply walks the list of producers looking for an SPMC queue with an item in it (since the number of producers is unbounded, this is partly what makes the high-level queue merely lock-free instead of wait-free).

## Dequeue Heuristics

Consumers may pass a token to the various dequeue methods. The purpose of this token is to speed up selection of an appropriate inner producer queue from which to attempt to dequeue. A simple scheme is used whereby every explicit consumer is assigned an auto-incrementing offset representing the index of the producer queue it should dequeue from. In this fashion, consumers are distributed as fairly as possible across the producers; however, not all producers have the same number of elements available, and some consumers may consume faster than others; to address this, the first consumer that consumes 256 items in a row from the same inner producer queue increments a global offset, causing all the consumers to rotate on their next dequeue operation and start consuming from the next producer. (Note that this means the rate of rotation is dictated by the fastest consumer.) If no elements are available on a consumer's designated queue, it moves on to the next one that has an element available. This simple heuristic is efficient and is able to pair consumers with producers with near-perfect scaling, leading to impressive dequeue speedups.

## A Note About Linearizability

A data structure is *linearizable* ([this paper](http://mcg.cs.tau.ac.il/papers/opodis2010-quasi.pdf) has a good definition) if all of its operations appear to execute in some sequential (linear) order, even under concurrency. While this is a useful property in that it makes a concurrent algorithm obviously correct and easier to reason about, it is a very *strong* consistency model. The queue I've presented here is not linearizable, since to make it so would result in much poorer performance; however, I believe it is still quite useful. My queue has the following consistency model: Enqueue operations on any given thread are (obviously) linearizable on that thread, but not others (this should not matter since even with a fully linearizable queue the final order of elements is non-deterministic since it depends on races between threads). Note that even though enqueue operations are not linearizable across threads, they are still atomic -- only elements that are completely enqueued can be dequeued. Dequeue operations are allowed to fail if all the producer queues appeared empty *at the time they were checked*. This means dequeue operations are also non-linearizable, because the queue as a whole was not necessarily empty at any one point during a failed dequeue operation. (Even a single producer queue's emptiness check is technically non-linearizable, since it's possible for an enqueue operation to complete but the memory effects not yet be propagated to a dequeueing thread -- again, this shouldn't matter anyway since it depends on non-deterministic race conditions either way.)

What this non-linearizability means in practical terms is that a dequeue operation may fail before the queue is completely empty *if there are still other producers enqueueing* (regardless of whether other threads are dequeueing or not). Note that this this race condition exists anyway even with a fully linearizable queue. If the queue has stabilized (i.e. all enqueue operations have completed and their memory effects have become visible to any potential consumer threads), then a dequeue operation will never fail as long as the queue is not empty. Similarly, if a given set of elements is visible to all dequeueing threads, dequeue operations on those threads will never fail until at least that set of elements is consumed (but may fail afterwards even if the queue is not completely empty).

## Conclusion

So there you have it! More than you ever wanted to know about my design for a general purpose lock-free queue. I've implemented this design using C++11, but I'm sure it can be ported to other languages. If anybody does implement this design in another language I'd love to hear about it!