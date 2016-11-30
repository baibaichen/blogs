# What are HBase Compactions?

[December 11, 2013](http://blog.cloudera.com/blog/2013/12/what-are-hbase-compactions/)[By Elliott Clark](http://blog.cloudera.com/?guest-author=Elliott%20Clark)[No Comments](http://blog.cloudera.com/blog/2013/12/what-are-hbase-compactions/#comments)

Categories: [HBase](http://blog.cloudera.com/blog/category/hbase/)

**The compactions model is changing drastically with CDH 5/HBase 0.96. Here’s what you need to know.**

[Apache HBase](http://hbase.apache.org/) is a distributed data store based upon a log-structured merge tree, so optimal read performance would come from having only one file per store (Column Family). However, that ideal isn’t possible during periods of heavy incoming writes. Instead, HBase will try to combine HFiles to reduce the maximum number of disk seeks needed for a read. This process is called *compaction*.

Compactions choose some files from a single store in a region and combine them. This process involves reading KeyValues in the input files and writing out any KeyValues that are not deleted, are inside of the time to live (TTL), and don’t violate the number of versions. The newly created combined file then replaces the input files in the region.

Now, whenever a client asks for data, HBase knows the data from the input files are held in one contiguous file on disk — hence only one seek is needed, whereas previously one for each file could be required. But disk IO isn’t free, and without careful attention, rewriting data over and over can lead to some serious network and disk over-subscription. In other words, compaction is about trading some disk IO now for fewer seeks later.

In this post, you will learn more about the use and implications of compactions in CDH 4, as well as changes to the compaction model in CDH 5 (which will be re-based on HBase 0.96).

## Compaction in CDH 4

The ideal compaction would pick the files that will reduce the most seeks in upcoming reads while also choosing files that will need the least amount of IO. Unfortunately, that problem isn’t solvable without knowledge of the future. As such, it’s just an ideal that HBase should strive for and not something that’s ever really attainable.

Instead of the impossible ideal, HBase uses a heuristic to try and choose which files in a store are likely to be good candidates. The files are chosen on the intuition that like files should be combined with like files – meaning, files that are about the same size should be combined.

The default policy in HBase 0.94 (shipping in CDH 4) looks through the list of HFiles, trying to find the first file that has a size less than the total of all files multiplied by hbase.store.compaction.ratio. Once that file is found, the HFile and all files with smaller sequence ids are chosen to be compacted.

For the default case of the largest files being the oldest, this approach works well:

![compaction1](http://blog.cloudera.com/wp-content/uploads/2013/12/compaction1.png)

However, this assumption about the correlation between age and size of files is faulty in some cases, leading the current algorithm to choose sub-optimally. Rather, bulk-loaded files can and sometimes do sort very differently from the more normally flushed HFiles, so they make great examples:

![compaction2](http://blog.cloudera.com/wp-content/uploads/2013/12/compaction2.png)

## Compaction Changes in CDH 5

Compactions have changed in significant ways recently. For HBase 0.96 and CDH 5, the file selection algorithm was made configurable via [HBASE-7516](https://issues.apache.org/jira/browse/HBASE-7516)— so it’s now possible to have user-supplied compaction policies. This change allows more experienced users to test and iterate on how they want to run compactions.

The default compaction selection algorithm was also changed to ExploringCompactionPolicy. This policy is different from the old default in that it ensures that every single file in a proposed compaction is within the given ratio. Also, it doesn’t just choose the first set of files that have sizes within the compaction ratio; instead it looks at all the possible sets that don’t violate any rules, and then chooses something that looks to be most impactful for the least amount of IO expected.  To do that, the ExploringCompactionPolicy chooses a compaction that will remove the most files within the ratio, and if there is a tie, preference is given to the set of files that are smaller in size:

![compaction3](http://blog.cloudera.com/wp-content/uploads/2013/12/compaction3.png)

More changes are planned for future releases, including tiered compaction, striped compaction, and level-based compaction.

## Conclusion

For some use cases, this work won’t have any impact at all. That’s a good thing, as compactions were already pretty well studied. However, for users who have large traffic spikes or that use bulk loads, this work can yield great improvements in IO wait times and in request latency. For a specific bulk-load use case, we have seen a 90% reduction in disk IO due to compactions.

Here are results from a test case in HBase’s PerfTestCompactionPolicies:

![compaction4](http://blog.cloudera.com/wp-content/uploads/2013/12/compaction4.png)

Check out this work in [CDH 5](http://blog.cloudera.com//content/cloudera/en/new/introducing-cloudera-enterprise-5.html) (in beta at the time of this writing) when it comes to a cluster near you.

**Further Reading:**

- [https://hbase.apache.org/apidocs/org/apache/hadoop/hbase/regionserver/compactions/ExploringCompactionPolicy.html](https://hbase.apache.org/apidocs/org/apache/hadoop/hbase/regionserver/compactions/ExploringCompactionPolicy.html)
- [https://hbase.apache.org/book/regions.arch.html#compaction.file.selection](https://hbase.apache.org/book/regions.arch.html#compaction.file.selection)
- [https://issues.apache.org/jira/browse/HBASE-7516](https://issues.apache.org/jira/browse/HBASE-7516)
- [https://issues.apache.org/jira/browse/HBASE-7678](https://issues.apache.org/jira/browse/HBASE-7678)
- [https://issues.apache.org/jira/browse/HBASE-7667](https://issues.apache.org/jira/browse/HBASE-7667)
- [https://issues.apache.org/jira/browse/HBASE-7055](https://issues.apache.org/jira/browse/HBASE-7055)
- [http://www.hbasecon.com/sessions/compaction-improvements-in-apache-hbase/](http://www.hbasecon.com/sessions/compaction-improvements-in-apache-hbase/)