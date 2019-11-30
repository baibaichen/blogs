# Processing Petabytes of Data in Seconds with Databricks Delta

## Introduction

[Databricks Delta](https://databricks.com/product/databricks-delta) is a unified data management system that brings data reliability and fast analytics to cloud data lakes. In this blog post, we take a peek under the hood to examine what makes Databricks Delta capable of sifting through petabytes of data within seconds. In particular, we discuss **Data Skipping** and **ZORDER Clustering**.

These two features combined enable the [Databricks Runtime](https://databricks.com/product/databricks-runtime) to dramatically reduce the amount of data that needs to be scanned in order to answer highly selective queries against large Delta tables, which typically translates into orders-of-magnitude runtime improvements and cost savings.

You can see these features in action in a [keynote speech](https://databricks.com/session/keynote-from-apple) from the 2018 [Spark + AI Summit](https://databricks.com/sparkaisummit/north-america), where Apple’s Dominique Brezinski demonstrated their use case for Databricks Delta as a unified solution for data engineering and data science in the context of cyber-security monitoring and threat response.

## How to Use Data Skipping and ZORDER Clustering

To take advantage of data skipping, all you need to do is use Databricks Delta. The feature is automatic and kicks in whenever your SQL queries or Dataset operations include filters of the form “column op literal”, where:

- `column` is an attribute of some Databricks Delta table, be it top-level or nested, whose data type is string / numeric / date/ timestamp
- `op` is a binary comparison operator, `StartsWith / LIKE ‘pattern%’, or IN `
- `literal` is an explicit (list of) value(s) of the same data type as a column

`AND / OR / NOT` are also supported, as well as “literal op column” predicates.

As we’ll explain below, even though data skipping always kicks in when the above conditions are met, it may not always be very effective. But, if there are a few columns that you frequently filter by and want to make sure that’s fast, then you can explicitly optimize your data layout with respect to skipping effectiveness by running the following command:

```
OPTIMIZE <table> [WHERE <partition_filter>]
ZORDER BY (<column>[, …])
```

More on this later. First, let’s take a step back and put things in context.

## How Data Skipping and ZORDER Clustering Work

The general use-case for these features is to improve the performance of needle-in-the-haystack kind of queries against huge data sets. The typical RDBMS solution, namely secondary indexes, is not practical in a big data context due to scalability reasons.

If you’re familiar with big data systems (be it Apache Spark, Hive, Impala, Vertica, etc.), you might already be thinking: (horizontal) [partitioning](https://en.wikipedia.org/wiki/Partition_(database)).

**Quick reminder**: In Spark, just like Hive, partitioning [1](https://databricks.com/blog/2018/07/31/processing-petabytes-of-data-in-seconds-with-databricks-delta.html#fn-30778-1) works by having one subdirectory for every distinct value of the partition column(s). Queries with filters on the partition column(s) can then benefit from *partition pruning*, i.e., avoid scanning any partition that doesn’t satisfy those filters.

The main question is: *What columns do you partition by?*
And the typical answer is: *The ones you’re most likely to filter by in time-sensitive queries.*
But… *What if there are multiple (say 4+), equally relevant columns?*

The problem, in that case, is that you end up with a huge number of unique combinations of values, which means a huge number of partitions and therefore files. Having data split across many small files brings up the following main issues:

- Metadata becomes as large as the data itself, causing performance issues for various driver-side operations
- In particular, file listing is affected, becoming very slow
- Compression effectiveness is compromised, leading to wasted space and slower IO

So while data partitioning in Spark generally works great for dates or categorical columns, it is not well suited for high-cardinality columns and, in practice, it is usually limited to one or two columns at most.

### Data Skipping

Apart from partition pruning, another common technique that’s used in the data warehousing world, but which Spark currently lacks, is I/O pruning based on [Small Materialized Aggregates](https://dl.acm.org/citation.cfm?id=671173). In short, the idea is to:

1. Keep track of simple statistics such as minimum and maximum values at a certain granularity that’s correlated with I/O granularity.
2. Leverage those statistics at query planning time in order to avoid unnecessary I/O.

This is exactly what Databricks Delta’s [data skipping](https://docs.databricks.com/delta/optimizations.html#data-skipping) feature is about. As new data is inserted into a Databricks Delta table, file-level min/max statistics are collected for all columns (including nested ones) of supported types. Then, when there’s a lookup query against the table, Databricks Delta first consults these statistics in order to determine which files can safely be skipped. But, as they say, a GIF is worth a thousand words, so here you go:

![img](https://databricks.com/wp-content/uploads/2018/07/image7.gif)

On the one hand, this is a lightweight and flexible (the granularity can be tuned) technique that is easy to implement and reason about. It’s also completely orthogonal to partitioning: it works great alongside it, but doesn’t depend on it. On the other hand, it’s a probabilistic indexing approach which, like bloom filters, may give false-positives, especially when data is not clustered. Which brings us to our next technique.

### ZORDER Clustering

For I/O pruning to be effective data needs to be **clustered** so that min-max ranges are narrow and, ideally, non-overlapping. That way, for a given point lookup, the number of min-max range hits is minimized, i.e. skipping is maximized.

Sometimes, data just happens to be naturally clustered: monotonically increasing IDs, columns that are correlated with insertion time (e.g., dates / timestamps) or the partition key (e.g., *pk_brand_name – model_name*). When that’s not the case, you can still enforce clustering by explicitly sorting or [range-partitioning](https://spark.apache.org/docs/2.3.0/api/scala/index.html#org.apache.spark.sql.Dataset@repartitionByRange(numPartitions:Int,partitionExprs:org.apache.spark.sql.Column*):org.apache.spark.sql.Dataset[T]) your data before insertions.

But, again, suppose your workload consists of equally frequent/relevant single-column predicates on (e.g. *n = 4*) different columns.

In that case, “linear” a.k.a. “lexicographic” or “major-minor” sorting by all of the n columns will strongly favor the first one that’s specified, clustering its values perfectly. However, it won’t do much, if anything at all (depending on how many duplicate values there are on that first column) for the second one, and so on. Therefore, in all likelihood, there will be no clustering on the nth column and therefore no skipping possible for lookups involving it.

*So how can we do better?* More precisely, *how can we achieve similar skipping effectiveness along every individual dimension?*

If we think about it, what we’re looking for is a way of assigning n-dimensional data points to data files, such that points assigned to the same file are also close to each other along each of the n dimensions individually. In other words, we want to *map multi-dimensional points to one-dimensional values in a way that preserves locality*.

This is a well-known problem, encountered not only in the database world, but also in domains such as computer graphics and geohashing. The answer is: locality-preserving [space-filling curves](https://en.wikipedia.org/wiki/Space-filling_curve), the most commonly used ones being the **Z-order** and **Hilbert curves**.

Below is a simple illustration of how Z-ordering can be applied for improving data layout with regard to data skipping effectiveness. Legend:

- *Gray* dot = data point e.g., chessboard square coordinates
- *Gray* box = data file; in this example, we aim for files of 4 points each
- *Yellow* box = data file that’s read for the given query
- *Green* dot = data point that passes the query’s filter and answers the query
- *Red* dot = data point that’s read, but doesn’t satisfy the filter; “false positive”

![img](https://databricks.com/wp-content/uploads/2018/07/Screen-Shot-2018-07-30-at-2.03.55-PM.png)

## An Example in Cybersecurity Analysis

Okay, enough theory, let’s get back to the Spark + AI Summit keynote and see how Databricks Delta can be used for real-time cybersecurity threat response.

Say you’re using [Bro](http://www.bro.org/), the popular open-source network traffic analyzer, which produces real-time, comprehensive network activity information[2](https://databricks.com/blog/2018/07/31/processing-petabytes-of-data-in-seconds-with-databricks-delta.html#fn-30778-2). The more popular your product is, the more heavily your services get used and, therefore, the more data Bro starts outputting. Writing this data at a fast enough pace to persistent storage in a more structured way for future processing is the first big data challenge you’ll face.

This is exactly what Databricks Delta was designed for in the first place, making this task easy and reliable. What you could do is use [structured streaming](https://databricks.com/glossary/what-is-structured-streaming) to pipe your Bro conn data into a date-partitioned Databricks Delta table, which you’ll periodically run [OPTIMIZE](https://docs.databricks.com/delta/optimizations.html#compaction-bin-packing) on so that your log records end up evenly distributed across reasonably-sized data files. But that’s not the focus of this blog post, so, for illustration purposes, let’s keep it simple and use a *non-streaming, non-partitioned Databricks Delta table* consisting of uniformly distributed *random data*.

Faced with a potential cyber-attack threat, the kind of ad-hoc data analysis you’ll want to run is a series of interactive “point lookups” against the logged network connection data. For example, “find all recent network activity involving this suspicious IP address.” We’ll model this workload by assuming it’s made out of *basic lookup queries with single-column equality filters*, using both random and sampled IPs and ports. Such simple queries are IO-bound, i.e. their runtime depends linearly on the amount of data scanned.

These lookup queries will typically turn into full table scans that might run for hours, depending on how much data you’re storing and how far back you’re looking. Your end goal is likely to minimize the total amount of time spent on running these queries, but, for illustration purposes, let’s instead define our *cost function* as the *total number of records scanned*. This metric should be a good approximation of total runtime and has the benefit of being well defined and deterministic, allowing interested readers to easily and reliably reproduce our experiments.

So here we go, this is what we’ll work with, concretely:

```language-scala
case class ConnRecord(src_ip: String, src_port: Int, dst_ip: String, dst_port: Int)

def randomIPv4(r: Random) = Seq.fill(4)(r.nextInt(256)).mkString(".")
def randomPort(r: Random) = r.nextInt(65536)

def randomConnRecord(r: Random) = ConnRecord(
   src_ip = randomIPv4(r), src_port = randomPort(r),
   dst_ip = randomIPv4(r), dst_port = randomPort(r))
case class TestResult(numFilesScanned: Long, numRowsScanned: Long, numRowsReturned: Long)

def testFilter(table: String, filter: String): TestResult = {
   val query = s"SELECT COUNT(*) FROM $table WHERE $filter"

   val(result, metrics) = collectWithScanMetrics(sql(query).as[Long])
   TestResult(
      numFilesScanned = metrics("filesNum"),
      numRowsScanned = metrics.get("numOutputRows").getOrElse(0L),
      numRowsReturned = result.head)
}

// Runs testFilter() on all given filters and returns the percent of rows skipped
// on average, as a proxy for Data Skipping effectiveness: 0 is bad, 1 is good
def skippingEffectiveness(table: String, filters: Seq[String]): Double = { ... }
```

Here’s how a randomly generated table of 100 files, 1K random records each, might look like:

```language-sql
  SELECT row_number() OVER (ORDER BY file) AS file_id,
       count(*) as numRecords, min(src_ip), max(src_ip), min(src_port), 
       max(src_port), min(dst_ip), max(dst_ip), min(dst_port), max(dst_port)
  FROM (
  SELECT input_file_name() AS file, * FROM conn_random)
  GROUP BY file
```

![img](https://databricks.com/wp-content/uploads/2018/07/Screen-Shot-2018-07-30-at-2.22.38-PM.png)

Seeing how every file’s min-max ranges cover almost the entire domain of values, it is easy to predict that there will be very little opportunity for file skipping. Our evaluation function confirms that:

```language-scala
skippingEffectiveness(connRandom, singleColumnFilters)
```

![img](https://databricks.com/wp-content/uploads/2018/07/Screen-Shot-2018-07-30-at-2.53.57-PM.png)

Ok, that’s expected, as our data is randomly generated and so there are no correlations. So let’s try explicitly sorting data before writing it.

```language-scala
spark.read.table(connRandom)
     .repartitionByRange($"src_ip", $"src_port", $"dst_ip", $"dst_port")
     // or just .sort($"src_ip", $"src_port", $"dst_ip", $"dst_port")
     .write.format("delta").saveAsTable(connSorted)
skippingEffectiveness(connRandom, singleColumnFilters)
```

![img](https://databricks.com/wp-content/uploads/2018/07/Screen-Shot-2018-07-30-at-2.54.46-PM.png)

Hmm, we have indeed improved our metric, but 25% is still not great. Let’s take a closer look:

```language-scala
val src_ip_eff = skippingEffectiveness(connSorted, srcIPv4Filters)
val src_port_eff = skippingEffectiveness(connSorted, srcPortFilters)
val dst_ip_eff = skippingEffectiveness(connSorted, dstIPv4Filters)
val dst_port_eff = skippingEffectiveness(connSorted, dstPortFilters)
```

![img](https://databricks.com/wp-content/uploads/2018/07/Screen-Shot-2018-07-30-at-2.56.51-PM.png)

Turns out *src_ip* lookups are really fast but all others are basically just full table scans. Again, that’s no surprise. As explained earlier, that’s what you get with linear sorting: the resulting data is clustered perfectly along the first dimension (*src_ip* in our case), but almost not at all along further dimensions.

*So how can we do better*? By enforcing **ZORDER** clustering.

```language-scala
spark.read.table(connRandom)
     .write.format("delta").saveAsTable(connZorder)

sql(s"OPTIMIZE $connZorder ZORDER BY (src_ip, src_port, dst_ip, dst_port)")
skippingEffectiveness(connZorder, singleColumnFilters)
```

![img](https://databricks.com/wp-content/uploads/2018/07/Screen-Shot-2018-07-30-at-4.46.00-PM.png)

Quite a bit better than the 0.25 obtained by linear sorting, right? Also, here’s the breakdown:

```language-scala
val src_ip_eff = skippingEffectiveness(connZorder, srcIPv4Filters)
val src_port_eff = skippingEffectiveness(connZorder, srcPortFilters)
val dst_ip_eff = skippingEffectiveness(connZorder, dstIPv4Filters)
val dst_port_eff = skippingEffectiveness(connZorder, dstPortFilters)
```

![img](https://databricks.com/wp-content/uploads/2018/07/Screen-Shot-2018-07-30-at-2.48.20-PM.png)

A couple of observations worth noting:

- It is expected that skipping effectiveness on src_ip is now lower than with linear ordering, as the latter would ensure perfect clustering, unlike z-ordering. However, the other columns’ score is now almost just as good, unlike before when it was 0.
- It is also expected that the more columns you z-order by, the lower the effectiveness.
  For example, `ZORDER BY (src_ip, dst_ip)` achieves **0.82**. So it is up to you to decide what filters you care about the most.

In the real-world use case presented at the Spark + AI summit, the skipping effectiveness on a typical `WHERE src_ip = x AND dst_ip = y` query was even higher. In a data set of **504 terabytes (over 11 trillion rows)**, only **36.5 terabytes** needed to be scanned thanks to data skipping. That’s a significant reduction of **92.4%** in the number of bytes and **93.2%** in the number of rows.

## Conclusion

Using Databricks Delta’s built-in data skipping and `ZORDER` clustering features, large cloud data lakes can be queried in a matter of seconds by skipping files not relevant to the query. In a real-world cybersecurity analysis use case, **93.2%** of the records in a **504 terabytes** dataset were skipped for a typical query, reducing query times by up to two orders of magnitude.

In other words, Databricks Delta can speed up your queries by as much as **100X**.

**Note**: Data skipping has been offered as an independent option outside of Databricks Delta in the past as a separate preview. That option will be deprecated in the near future. We highly recommend you move to Databricks Delta to take advantage of the data skipping capability.

# Diving Into Delta Lake: Schema Enforcement & Evolution

Data, like our experiences, is always evolving and accumulating. To keep up, our mental models of the world must adapt to new data, some of which contains new dimensions – new ways of seeing things we had no conception of before. These mental models are not unlike a table’s schema, defining how we categorize and process new information.

This brings us to schema management. As business problems and requirements evolve over time, so too does the structure of your data. With Delta Lake, as the data changes, incorporating new dimensions is easy. Users have access to simple semantics to control the schema of their tables. These tools include **schema enforcement,** which prevents users from accidentally polluting their tables with mistakes or garbage data, as well as **schema evolution,** which enables them to automatically add new columns of rich data when those columns belong. In this blog, we’ll dive into the use of these tools.

## Understanding Table Schemas

Every DataFrame in Apache Spark™ contains a schema, a blueprint that defines the shape of the data, such as data types and columns, and metadata. With Delta Lake, the table’s schema is saved in JSON format inside the transaction log.

## What Is Schema Enforcement?

Schema enforcement, also known as **schema validation*****,\*** is a safeguard in Delta Lake that ensures data quality by rejecting writes to a table that do not match the table’s schema. Like the front desk manager at a busy restaurant that only accepts reservations, it checks to see whether each column in data inserted into the table is on its list of expected columns (in other words, whether each one has a “reservation”), and rejects any writes with columns that aren’t on the list.

## How Does Schema Enforcement Work?

Delta Lake uses schema validation *on write*, which means that all new writes to a table are checked for compatibility with the target table’s schema at write time. If the schema is not compatible, Delta Lake cancels the transaction altogether (no data is written), and raises an exception to let the user know about the mismatch.

To determine whether a write to a table is compatible, Delta Lake uses the following rules. The DataFrame to be written:

- **Cannot contain any additional columns that are not present in the target table’s schema.** Conversely, it’s OK if the incoming data doesn’t contain every column in the table – those columns will simply be assigned null values.
- **Cannot have column data types that differ from the column data types in the target table.** If a target table’s column contains StringType data, but the corresponding column in the DataFrame contains IntegerType data, schema enforcement will raise an exception and prevent the write operation from taking place.
- **Can not contain column names that differ only by case.** This means that you cannot have columns such as ‘Foo’ and ‘foo’ defined in the same table. While Spark can be used in case sensitive or insensitive (default) mode, Delta Lake is case-preserving but insensitive when storing the schema. [Parquet](https://databricks.com/glossary/what-is-parquet) is case sensitive when storing and returning column information. To avoid potential mistakes, data corruption or loss issues (which we’ve personally experienced at Databricks), we decided to add this restriction.

To illustrate, take a look at what happens in the code below when an attempt to append some newly calculated columns to a Delta Lake table that isn’t yet set up to accept them.

```
# Generate a DataFrame of loans that we'll append to our Delta Lake table
loans = sql("""
            SELECT addr_state, CAST(rand(10)*count as bigint) AS count,
            CAST(rand(10) * 10000 * count AS double) AS amount
            FROM loan_by_state_delta
            """)

# Show original DataFrame's schema
original_loans.printSchema()
 
"""
root
  |-- addr_state: string (nullable = true)
  |-- count: integer (nullable = true)
"""
 
# Show new DataFrame's schema
loans.printSchema()
 
"""
root
  |-- addr_state: string (nullable = true)
  |-- count: integer (nullable = true)
  |-- amount: double (nullable = true) # new column
"""
 
# Attempt to append new DataFrame (with new column) to existing table
loans.write.format("delta") \
           .mode("append") \
           .save(DELTALAKE_PATH)

""" Returns:

A schema mismatch detected when writing to the Delta table.
 
To enable schema migration, please set:
'.option("mergeSchema", "true")\'
 
Table schema:
root
-- addr_state: string (nullable = true)
-- count: long (nullable = true)
 
 
Data schema:
root
-- addr_state: string (nullable = true)
-- count: long (nullable = true)
-- amount: double (nullable = true)
 
If Table ACLs are enabled, these options will be ignored. Please use the ALTER TABLE command for changing the schema.

"""
```

Rather than automatically adding the new columns, Delta Lake enforces the schema and stops the write from occurring. To help identify which column(s) caused the mismatch, Spark prints out both schemas in the stack trace for comparison.

## How Is Schema Enforcement Useful?

Because it’s such a stringent check, schema enforcement is an excellent tool to use as a gatekeeper of a clean, fully transformed data set that is ready for production or consumption. It’s typically enforced on tables that directly feed:

- Machine learning algorithms
- BI dashboards
- Data analytics and visualization tools
- Any production system requiring highly structured, strongly typed, semantic schemas

In order to prepare their data for this final hurdle, many users employ a simple “multi-hop” architecture that progressively adds structure to their tables. To learn more, take a look at the post entitled [Productionizing Machine Learning With Delta Lake](https://databricks.com/blog/2019/08/14/productionizing-machine-learning-with-delta-lake.html).

Of course, schema enforcement can be used anywhere in your pipeline, but be aware that it can be a bit frustrating to have your streaming write to a table fail because you forgot that you added a single column to the incoming data, for example.

## Preventing Data Dilution

At this point, you might be asking yourself, what’s all the fuss about? After all, sometimes an unexpected “schema mismatch” error can trip you up in your workflow, especially if you’re new to Delta Lake. Why not just let the schema change however it needs to so that I can write my DataFrame no matter what?

As the old saying goes, “an ounce of prevention is worth a pound of cure.” At some point, if you don’t enforce your schema, issues with data type compatibility will rear their ugly heads – seemingly homogenous sources of raw data can contain edge cases, corrupted columns, misformed mappings, or other scary things that go bump in the night. A much better approach is to stop these enemies at the gates – using schema enforcement – and deal with them in the daylight rather than later on, when they’ll be lurking in the shadowy recesses of your production code.

Schema enforcement provides peace of mind that your table’s schema will not change unless you make the affirmative choice to change it. It prevents data “dilution,” which can occur when new columns are appended so frequently that formerly rich, concise tables lose their meaning and usefulness due to the data deluge. By encouraging you to be intentional, set high standards, and expect high quality, schema enforcement is doing exactly what it was designed to do – keeping you honest, and your tables clean.

If, upon further review, you decide that you really *did* mean to add that new column, it’s an easy, one line fix, as discussed below. The solution is schema evolution!

## What Is Schema Evolution?

Schema evolution is a feature that allows users to easily change a table’s current schema to accommodate data that is changing over time. Most commonly, it’s used when performing an append or overwrite operation, to automatically adapt the schema to include one or more new columns.

## How Does Schema Evolution Work?

Following up on the example from the previous section, developers can easily use schema evolution to add the new columns that were previously rejected due to a schema mismatch. Schema evolution is activated by adding ` .option('mergeSchema', 'true')` to your `.write` or `.writeStream` Spark command.

```
# Add the mergeSchema option
loans.write.format("delta") \
           .option("mergeSchema", "true") \
           .mode("append") \
           .save(DELTALAKE_SILVER_PATH)
```

To view the plot, execute the following [Spark SQL](https://databricks.com/glossary/what-is-spark-sql) statement.

```
# Create a plot with the new column to confirm the write was successful
%sql
SELECT addr_state, sum(`amount`) AS amount
FROM loan_by_state_delta
GROUP BY addr_state
ORDER BY sum(`amount`)
DESC LIMIT 10
```

[![Bar chart showing the number of loans per state after successfully using schema enforcement and schema evolution.](https://databricks.com/wp-content/uploads/2019/09/delta-lake-schema-evolution-1.png)](https://databricks.com/wp-content/uploads/2019/09/delta-lake-schema-evolution-1.png)

> Alternatively, you can set this option for the entire Spark session by adding `spark.databricks.delta.schema.autoMerge = True` to your Spark configuration. Use with caution, as schema enforcement will no longer warn you about unintended schema mismatches.

By including the `mergeSchema` option in your query, any columns that are present in the DataFrame but not in the target table are automatically added on to the end of the schema as part of a write transaction. Nested fields can also be added, and these fields will get added to the end of their respective struct columns as well.

Data engineers and scientists can use this option to add new columns (perhaps a newly tracked metric, or a column of this month’s sales figures) to their existing machine learning production tables without breaking existing models that rely on the old columns.

The following types of schema changes are eligible for schema evolution during table appends or overwrites:

- Adding new columns (this is the most common scenario)
- Changing of data types from NullType -> any other type, or upcasts from ByteType -> ShortType -> IntegerType

Other changes, which are not eligible for schema evolution, require that the **schema and data** are overwritten by adding `.option("mergeSchema", "true")`. For example, in the case where the column “Foo” was originally an `integer` data type and the new schema would be a string data type, then all of the Parquet (data) files would need to be re-written. Those changes include:

- Dropping a column
- Changing an existing column’s data type (in place)
- Renaming column names that differ only by case (e.g. “Foo” and “foo”)

Finally, with the upcoming release of Spark 3.0, explicit DDL (using `**ALTER TABLE**`) will be fully supported, allowing users to perform the following actions on table schemas:

- Adding columns
- Changing column comments
- Setting table properties that define the behavior of the table, such as setting the retention duration of the transaction log

## How is Schema Evolution Useful?

Schema evolution can be used anytime you *intend* to change the schema of your table (as opposed to where you accidentally added columns to your DataFrame that shouldn’t be there). It’s the easiest way to migrate your schema because it automatically adds the correct column names and data types, without having to declare them explicitly.

## Summary

Schema enforcement rejects any new columns or other schema changes that aren’t compatible with your table. By setting and upholding these high standards, analysts and engineers can trust that their data has the highest levels of integrity, and reason about it with clarity, allowing them to make better business decisions.

On the flip side of the coin, schema evolution complements enforcement by making it easy for *intended* schema changes to take place automatically. After all, it shouldn’t be hard to add a column.

Schema enforcement is the yin to schema evolution’s yang. When used together, these features make it easier than ever to block out the noise, and tune in to the signal.

# Diving Into Delta Lake: Unpacking The Transaction Log


The transaction log is key to understanding Delta Lake because it is the common thread that runs through many of its most important features, including ACID transactions, scalable metadata handling, time travel, and more. In this article, we’ll explore what the Delta Lake transaction log is, how it works at the file level, and how it offers an elegant solution to the problem of multiple concurrent reads and writes.

## What Is the Delta Lake Transaction Log?

The Delta Lake transaction log (also known as the `DeltaLog`) is an ordered record of every transaction that has ever been performed on a Delta Lake table since its inception.

## What Is the Transaction Log Used For?

### Single Source of Truth

Delta Lake is built on top of Apache Spark™ in order to allow multiple readers and writers of a given table to all work on the table at the same time. In order to show users correct views of the data at all times, the Delta Lake transaction log serves as a **single source of truth** – the central repository that tracks all changes that users make to the table.

When a user reads a Delta Lake table for the first time or runs a new query on an open table that has been modified since the last time it was read, **Spark checks the transaction log to see what new transactions have posted to the table, and then updates the end user’s table with those new changes.** This ensures that a user’s version of a table is always synchronized with the master record as of the most recent query, and that users cannot make divergent, conflicting changes to a table.

### The Implementation of Atomicity on Delta Lake

One of the four properties of ACID transactions, **atomicity**, guarantees that operations (like an INSERT or UPDATE) performed on your [data lake](https://databricks.com/glossary/data-lake) either complete fully, or don’t complete at all. Without this property, it’s far too easy for a hardware failure or a software bug to cause data to be only partially written to a table, resulting in messy or corrupted data.

**The transaction log is the mechanism through which Delta Lake is able to offer the guarantee of atomicity.** For all intents and purposes, if it’s not recorded in the transaction log, it never happened. By only recording transactions that execute fully and completely, and using that record as the single source of truth, the Delta Lake transaction log allows users to reason about their data, and have peace of mind about its fundamental trustworthiness, at petabyte scale.

## How Does the Transaction Log Work?

### Breaking Down Transactions Into Atomic Commits

Whenever a user performs an operation to modify a table (such as an INSERT, UPDATE or DELETE), Delta Lake breaks that operation down into a series of discrete steps composed of one or more of the **actions** below.

- **Add file** – adds a data file.
- **Remove file** – removes a data file.
- **Update metadata** – Updates the table’s metadata (e.g., changing the table’s name, schema or partitioning).
- **Set transaction** – Records that a [structured streaming](https://databricks.com/glossary/what-is-structured-streaming) job has committed a micro-batch with the given ID.
- **Change protocol** – enables new features by switching the Delta Lake transaction log to the newest software protocol.
- **Commit info** – Contains information around the commit, which operation was made, from where and at what time.

Those actions are then recorded in the transaction log as ordered, atomic units known as **commits.**

For example, suppose a user creates a transaction to add a new column to a table plus add some more data to it. Delta Lake would break that transaction down into its component parts, and once the transaction completes, add them to the transaction log as the following commits:

1. Update metadata – change the schema to include the new column
2. Add file – for each new file added

### The Delta Lake Transaction Log at the File Level

**When a user creates a Delta Lake table, that table’s transaction log is automatically created in the** `**_delta_log**` **subdirectory. As he or she makes changes to that table, those changes are recorded as ordered, atomic commits in the transaction log.** Each commit is written out as a JSON file, starting with `000000.json`. Additional changes to the table generate subsequent JSON files in ascending numerical order so that the next commit is written out as `000001.json`, the following as `000002.json`, and so on.

[![Diagram of the Delta Lake Transaction Log file structure.](https://databricks.com/wp-content/uploads/2019/08/image7.png)](https://databricks.com/wp-content/uploads/2019/08/image7.png)

So, as an example, perhaps we might add additional records to our table from the data files `1.parquet` and `2.parquet`. That transaction would automatically be added to the transaction log, saved to disk as commit `000000.json`. Then, perhaps we change our minds and decide to remove those files and add a new file instead (`3.parquet`). Those actions would be recorded as the next commit in the transaction log, as `000001.json`, as shown below.

[![Diagram illustrating two commits that perform operations on the same file.](https://databricks.com/wp-content/uploads/2019/08/image3-6.png)](https://databricks.com/wp-content/uploads/2019/08/image3-6.png)

Even though `1.parquet` and `2.parquet` are no longer part of our Delta Lake table, their addition and removal are still recorded in the transaction log because those operations were performed on our table – despite the fact that they ultimately canceled each other out. **Delta Lake still retains atomic commits like these to ensure that in the event we need to audit our table or use “time travel” to see what our table looked like at a given point in time, we could do so accurately.**

**Also, Spark does not eagerly remove the files from disk,** even though we removed the underlying data files from our table. Users can delete the files that are no longer needed by using [VACUUM](https://docs.delta.io/latest/delta-utility.html#vacuum).

### Quickly Recomputing State With Checkpoint Files

Once we’ve made a total of 10 commits to the transaction log, Delta Lake saves a checkpoint file in Parquet format in the same `_delta_log` subdirectory. **Delta Lake automatically generates checkpoint files every 10 commits.**

[![Diagram illustrating the Delta Lake Transaction Log file structure, including partition directories.](https://databricks.com/wp-content/uploads/2019/08/image6-1.png)](https://databricks.com/wp-content/uploads/2019/08/image6-1.png)

**These checkpoint files save the entire state of the table at a point in time – in native Parquet format that is quick and easy for Spark to read.** In other words, they offer the Spark reader a sort of “shortcut” to fully reproducing a table’s state that allows Spark to avoid reprocessing what could be thousands of tiny, inefficient JSON files.

**To get up to speed, Spark can run a** `**listFrom**` **operation to view all the files in the transaction log, quickly skip to the newest checkpoint file, and only process those JSON commits made since the most recent checkpoint file was saved.**

To demonstrate how this works, imagine that we’ve created commits all the way through `000007.json` as shown in the diagram below. Spark is up to speed through this commit, having automatically cached the most recent version of the table in memory. In the meantime, though, several other writers (perhaps your overly eager teammates) have written new data to the table, adding commits all the way through `0000012.json`.

To incorporate these new transactions and update the state of our table, Spark will then run a `listFrom version 7` operation to see the new changes to the table.

[![Diagram illustrating how Spark reads recent checkpoint files to quickly compute table state.](https://databricks.com/wp-content/uploads/2019/08/image2-3.png)](https://databricks.com/wp-content/uploads/2019/08/image2-3.png)

Rather than processing all of the intermediate JSON files, Spark can skip ahead to the most recent checkpoint file, since it contains the entire state of the table at commit #10. Now, Spark only has to perform incremental processing of `0000011.json` and `0000012.json` to have the current state of the table. Spark then caches version 12 of the table in memory. By following this workflow, Delta Lake is able to use Spark to keep the state of a table updated at all times in an efficient manner.

### Dealing With Multiple Concurrent Reads and Writes

Now that we understand how the Delta Lake transaction log works at a high level, let’s talk about concurrency. So far, our examples have mostly covered scenarios in which users commit transactions linearly, or at least without conflict. But what happens when Delta Lake is dealing with multiple concurrent reads and writes?

The answer is simple. **Since Delta Lake is powered by Apache Spark, it’s not only possible for multiple users to modify a table at once – it’s expected. To handle these situations, Delta Lake employs** ***optimistic concurrency control.\***

### What Is Optimistic Concurrency Control?

Optimistic concurrency control is a method of dealing with concurrent transactions that assumes that transactions (changes) made to a table by different users can complete without conflicting with one another. It is incredibly fast because when dealing with petabytes of data, there’s a high likelihood that users will be working on different parts of the data altogether, allowing them to complete non-conflicting transactions simultaneously.

For example, imagine that you and I are working on a jigsaw puzzle together. As long as we’re both working on different parts of it – you on the corners, and me on the edges, for example – there’s no reason why we can’t each work on our part of the bigger puzzle at the same time, and finish the puzzle twice as fast. It’s only when we need the same pieces, at the same time, that there’s a conflict. That’s optimistic concurrency control.

Of course, even with optimistic concurrency control, sometimes users do try to modify the same parts of the data at the same time. Luckily, Delta Lake has a protocol for that.

### Solving Conflicts Optimistically

In order to offer ACID transactions, Delta Lake has a protocol for figuring out how commits should be ordered (known as the concept of **serializability** in databases), and determining what to do in the event that two or more commits are made at the same time. Delta Lake handles these cases by implementing a rule of **mutual exclusion***,* then attempting to solve any conflict optimistically. This protocol allows Delta Lake to deliver on the ACID principle of **isolation*****,\*** which ensures that the resulting state of the table after multiple, concurrent writes is the same as if those writes had occurred serially, in isolation from one another.

In general, the process proceeds like this:

1. **Record the starting table version.**
2. **Record reads/writes.**
3. **Attempt a commit.**
4. **If someone else wins, check whether anything you read has changed.**
5. **Repeat.**

To see how this all plays out in real time, let’s take a look at the diagram below to see how Delta Lake manages conflicts when they do crop up. Imagine that two users read from the same table, then each go about attempting to add some data to it.

[![Illustrating optimistic concurrency control by showing two users with conflicting commits.](https://databricks.com/wp-content/uploads/2019/08/image4-1.png)](https://databricks.com/wp-content/uploads/2019/08/image4-1.png)

- **Delta Lake records the starting table version of the table (version 0) that is read prior to making any changes.**
- Users 1 and 2 both attempt to append some data to the table at the same time. Here, we’ve run into a conflict because only one commit can come next and be recorded as `000001.json`.
- Delta Lake handles this conflict with the concept of “mutual exclusion,” which means that only one user can successfully make commit `000001.json`. User 1’s commit is accepted, while User 2’s is rejected.
- Rather than throw an error for User 2, Delta Lake prefers to handle this conflict *optimistically*. It checks to see whether any new commits have been made to the table, and updates the table silently to reflect those changes, then simply retries User 2’s commit on the newly updated table (without any data processing), successfully committing `000002.json`.

**In the vast majority of cases, this reconciliation happens silently, seamlessly, and successfully.** However, in the event that there’s an irreconcilable problem that Delta Lake cannot solve optimistically (for example, if User 1 deleted a file that User 2 also deleted), the only option is to throw an error.

As a final note, since all of the transactions made on Delta Lake tables are stored directly to disk, this process satisfies the ACID property of **durability**, meaning it will persist even in the event of system failure.

## Other Use Cases

### Time Travel

Every table is the result of the sum total of all of the commits recorded in the Delta Lake transaction log – no more and no less. The transaction log provides a step-by-step instruction guide, detailing exactly how to get from the table’s original state to its current state.

Therefore, we can recreate the state of a table at any point in time by starting with an original table, and processing only commits made prior to that point. This powerful ability is known as “time travel,” or data versioning, and can be a lifesaver in any number of situations. For more information, please refer to [Introducing Delta Time Travel for Large Scale Data Lakes](https://databricks.com/blog/2019/02/04/introducing-delta-time-travel-for-large-scale-data-lakes.html).

### Data Lineage and Debugging

As the definitive record of every change ever made to a table, the Delta Lake transaction log offers users a verifiable data lineage that is useful for governance, audit and compliance purposes. It can also be used to trace the origin of an inadvertent change or a bug in a pipeline back to the exact action that caused it. Users can run [DESCRIBE HISTORY](https://docs.delta.io/latest/delta-utility.html#history) to see metadata around the changes that were made.

## Delta Lake Transaction Log Summary

In this blog, we dove into the details of how the Delta Lake transaction log works, including:

- What the transaction log is, how it’s structured, and how commits are stored as files on disk.
- How the transaction log serves as a single source of truth, allowing Delta Lake to implement the principle of atomicity.
- How Delta Lake computes the state of each table – including how it uses the transaction log to catch up from the most recent checkpoint.
- Using optimistic concurrency control to allow multiple concurrent reads and writes even as tables change.
- How Delta Lake uses mutual exclusion to ensure that commits are serialized properly, and how they are retried silently in the event of a conflict.

# Brand Safety with Structured Streaming, Delta Lake, and Databricks

> The original blog is from Eyeview Engineering’s blog [Brand Safety with Spark Streaming and Delta Lake](https://www.eyeviewdigital.com/tech/brand-safety-with-spark-streaming-and-delta-lake/) reproduced with permission.

Eyeview serves data-driven online video ads for its clients. Brands know the importance of ad placement and ensuring their ads are not placed next to unfavorable content. The Internet Advertising Bureau (IAB), defines brand safety as keeping a brand’s reputation safe when they advertise [online](https://blog.bannerflow.com/the-ultimate-guide-to-brand-safety/). In practice, this means avoiding the ads placement next to inappropriate content. The content of the webpage defines the segments of that page URL (e.g. CNN.com has news about the presidential election, then politics can be segments of that page).

## Brand Safety at Eyeview

The diagram below shows how Eyeview implemented the concept of brand safety and what challenges were faced.

[![img](https://databricks.com/wp-content/uploads/2019/09/image2-4.png)](https://databricks.com/wp-content/uploads/2019/09/image2-4.png)

Eyeview’s cluster of real-time bidders requires the information on whether a URL is brand safe or not in order to make the decision to serve the ad. It gets this information from Aerospike (our real-time database with latency close to 10ms), but to persist this information in Aerospike we defined an offline solution that loads the segment information. Once Eyeview’s cluster of bidders gets the bid request, the URL of that bid request is dumped into S3. The number of requests the cluster gets is close to 500k requests per second. Getting numerous URLs every second dumped into S3 after the deduping process (so that HTTP call is not made for the same URLs multiple times in the same timeframe) can create a massive problem processing a large number of small files simultaneously.

## Big Data Challenges

Many of the developers in the big data world face this problem in Spark or [Hadoop](https://databricks.com/glossary/hadoop) environments. Below is the list of challenges that can arise while batch processing a large number of small files.

### Challenge #1: Underutilized Resources

Underutilizing the cluster resources (seen in the photo below) for reading a small size file (~KB size) using a 1TB cluster would be like cutting bread using a sword instead of a butter knife.

[![img](https://databricks.com/wp-content/uploads/2019/09/image1-3.png)](https://databricks.com/wp-content/uploads/2019/09/image1-3.png)

### Challenge #2: Manual Checkpointing

It is important to perform manual checkpointing to track which files were processed and which were not. This can be extremely tedious in cases involving reprocessing of files or failures. Also, this may not be scalable if the data size becomes very large.

### Challenge #3: Parquet Table Issues

Let’s assume somehow we managed to process these large number of small files and we are not caching/persisting data on the cluster and writing directly to a [parquet](https://databricks.com/glossary/what-is-parquet) table via Spark, we then would end up writing too many small files to the tables. The problem with parquet files is that the continuous append on the table is too slow. We leveraged overwrite mode to save the data which ended up creating millisecond partitions to table.

### Challenge #4: No Concurrent Reads on Parquet Tables

The latency of the offline jobs became so high that the job was continuously writing the data to the parquet tables, which means no other jobs can query that table and parquet does not work great with a very large number of partitions.

## Databricks Spark Streaming and Delta Lake

To solve the above challenges we introduced two new technologies: Databricks Spark Streaming and Delta Lake. The source of most of a large number of small files can be converted from batch processing to streaming processing. Databricks Spark streaming helped in solving the first two challenges. Instead of a cluster of bidders writing files which contain the URLs to S3, we started sending URLs directly to a kinesis stream. This way we didn’t have a small number of files; all the data is in the streams, which would lead to utilizing our Spark resources efficiently.

[![img](https://databricks.com/wp-content/uploads/2019/09/image3-5.png)](https://databricks.com/wp-content/uploads/2019/09/image3-5.png)

By connecting Spark Streaming with Kinesis streams we no longer need to do manual checkpointing. Since Spark Streaming is inherently fault-tolerant we don’t have worry about failures and reprocessing of files. The code snippet below reads the data from the Kinesis stream.

```
import org.apache.spark.sql.types._
val jsonSchema = new StructType()
        .add("entry", StringType)
        .add("ts", LongType)
val kinesisDF = spark.readStream
  .format("kinesis")
  .option("streamName", "kinesis Stream Name")
  .option("initialPosition", "LATEST")
  .option("region", "aws-region")
  .load()
val queryDf = kinesisDF
  .map(row => Charset.forName("UTF-8").newDecoder().decode(ByteBuffer.wrap(new Base64().decode(row.get(1)).asInstanceOf[Array[Byte]])).toString)
  .selectExpr("cast (value as STRING) jsonData")
  .select(from_json(col("jsonData"), jsonSchema).as("bc"))
  .withColumn("entry",lit($"bc.entry"))
  .withColumn("_tmp", split($"entry", "\\,"))
  .select(
  $"_tmp".getItem(0).as("device_type"),
  $"_tmp".getItem(1).as("url"),
  $"_tmp".getItem(2).as("os"),
  $"bc.ts".as("ts")
).drop("_tmp")
```

The other two challenges with the parquet table are solved by introducing a new table format, [Delta Lake](https://delta.io/). Delta Lake supports ACID transactions, which basically means we can concurrently and reliably read/write this table. Delta Lake tables are also very efficient with continuous appends to the tables. A table in Delta Lake is both a batch table, as well as a streaming source and sink. The below code shows persisting the data into delta lake. This also helped us in removing the millisecond partitions; see the below code for reference (partitions are up to only hour level).

```
val sparkStreaming = queryDf.as[(String,String,String,Long)].mapPartitions{partition=>
    val http_client = new HttpClient
    http_client.start
    val partitionsResult = partition.map{record=>
      try{
          val api_url = ApiCallingUtility.createAPIUrl(record._2,record._1,record._3)
          val result = ApiCallingUtility.apiCall(http_client.newRequest(api_url).timeout(500, TimeUnit.MILLISECONDS).send(),record._2,record._1)
          aerospikeWrite(api_url,result)
          result
      }
      catch{
          case e:Throwable=>{
            println(e)
          }
      } 
    }
  partitionsResult
}
```

## Conclusion and Results

By default, our cluster of bidders makes no bid as the decision if the bidders did not have segment information of a URL. This would result in less advertising traffic which would have a substantial monetary impact. The latency of the old architecture was so high that the result was filtered out URLs – i.e. no ads. By switching the process to Databricks Spark Streaming and Delta Lake, we decreased the number of bid calls to be filtered by 50%! Once we had moved the architecture from batch processing to a streaming solution, we were able to reduce the cluster size of the Spark jobs, thus significantly reducing the cost of the solution. More impressively, now we only require one job to take care of all the brand safety providers, which further reduced costs.

# Scalable near real-time S3 access logging analytics with Apache Spark™ and Delta Lake

> The [original blog](https://www.linkedin.com/pulse/scalable-near-real-time-s3-access-logging-analytics-spark-inozemtsev/) is from Viacheslav Inozemtsev, Senior Data Engineer at Zalando, reproduced with permission.

## Introduction

Many organizations use AWS S3 as their main storage infrastructure for their data. Moreover, by using Apache Spark™ on Databricks they often perform [transformations](https://databricks.com/glossary/what-are-transformations) of that data and save the refined results back to S3 for further analysis. When the size of data and the amount of processing reach a certain scale, it often becomes necessary to observe the data access patterns. Common questions that arise include (but are not limited to): Which [datasets](https://databricks.com/glossary/what-are-datasets) are used the most? What is the ratio between accessing new and past data? How quickly a dataset can be moved to a cheaper storage class without affecting the performance of the users? Etc.

In [Zalando](https://zalando.com/), we have faced this issue since data and computation became a commodity for us in the last few years. Almost all of our ~200 engineering teams regularly perform analytics, reporting, or machine learning meaning they all read data from the central [data lake](https://databricks.com/glossary/data-lake). The main motivation to enable observability over this data was to reduce the cost of storage and processing by deleting unused data and by shrinking resource usage of the pipelines that produce that data. An additional driver was to understand if our engineering teams needed to query historical data or if they are only interested in the recent state of the data.

To answer these types of questions S3 provides a useful feature – [S3 Server Access Logging](https://docs.aws.amazon.com/AmazonS3/latest/dev/ServerLogs.html). When enabled, it constantly dumps logs about every read and write access in the observed bucket. The problem that appears almost immediately, and especially at a higher scale, is that these logs are in the form of comparatively small text files, with a format similar to the logs of Apache Web Server.

To query these logs we have leveraged capabilities of Apache Spark™ [Structured Streaming](https://databricks.com/glossary/what-is-structured-streaming) on Databricks and built a streaming pipeline that constructs [Delta Lake](http://delta.io/) tables. These tables – for each observed bucket – contain well-structured data of the S3 Access Logs, they are partitioned, can be sorted if needed, and, as a result, enable extended and efficient analysis of the access patterns of the company’s data. This allows us to answer the previously mentioned questions and many more. In this blog post we are going to describe the production architecture we designed in Zalando, and to show in detail how you can deploy such a pipeline yourself.

## Solution

Before we start, let us make two qualifications.

The first note is about why we chose [Delta Lake](http://delta.io/), and not plain [Parquet](https://databricks.com/glossary/what-is-parquet) or any other format. As you will see, to solve the problem described we are going to create a continuous application using Spark Structured Streaming. The properties of the Delta Lake, in this case, will give us the following benefits:

- ACID Transactions: No corrupted/inconsistent reads by the consumers of the table in case write operation is still in progress or has failed leaving partial results on S3. More information is also available in [Diving into Delta Lake: Unpacking the Transaction Log](https://databricks.com/blog/2019/08/21/diving-into-delta-lake-unpacking-the-transaction-log.html).
- Schema Enforcement: The metadata is controlled by the table; there is no chance that we break the schema if there is a bug in the code of the Spark job or if the format of the logs has changed. More information is available in [Diving Into Delta Lake: Schema Enforcement & Evolution](https://databricks.com/blog/2019/09/24/diving-into-delta-lake-schema-enforcement-evolution.html).
- Schema Evolution: On the other hand, if there is a change in the log format – we can purposely extend the schema by adding new fields. More information is available in [Diving Into Delta Lake: Schema Enforcement & Evolution](https://databricks.com/blog/2019/09/24/diving-into-delta-lake-schema-enforcement-evolution.html).
- Open Format: All the benefits of the plain Parquet format for readers apply, e.g. predicate push-down, column projection, etc.
- Unified Batch and Streaming Source and Sink: Opportunity to chain downstream Spark Structured Streaming jobs to produce aggregations based on the new content

The second note is about the datasets that are being read by the clients of our data lake. For the most part, the mentioned datasets consist of 2 categories: 1) snapshots of the [data warehouse](https://databricks.com/glossary/data-warehouse) tables from the BI databases, and 2) continuously appended streams of events from the central event bus of the company. This means that there are 2 types of patterns of how data gets written in the first place – full snapshot once per day and continuously appended stream, respectively.

In both cases we have a hypothesis that the data generated in the last day is consumed most often. For the snapshots we also know of infrequent comparisons between the current snapshot and past versions, for example one from a year ago. We are aware of the use case when the whole month or even year of historical data for a certain stream of event data has to be processed. This gives us an idea of what to look for, and this is where the described pipeline should help us to prove or disprove our hypotheses.

Let us now dive into the technical details of the implementation of this pipeline. The only entity we have at the current stage is the S3 bucket. Our goal is to analyze what patterns appear in the read and write access to this bucket.

To give you an idea of what we are going to show, on the diagram below you can see the final architecture, that represents the final state of the pipeline. The flow it depicts is the following:

1. AWS constantly monitors the S3 bucket **data-bucket**
2. It writes raw text logs to the target S3 bucket **raw-logs-bucket**
3. For every created object an Event Notification is sent to the SQS queue **new-log-objects-queue**
4. Once every hour a Spark job gets started by Databricks
5. Spark job reads all the new messages from the queue
6. Spark job reads all the objects (described in the messages from the queue) from raw-logs-bucket
7. Spark job writes the new data in *append* mode to the Delta Lake table in the **delta-logs-bucket** S3 bucket (optionally also executes [OPTIMIZE](https://docs.databricks.com/delta/optimizations/file-mgmt.html#compaction-bin-packing) and [VACUUM](https://docs.databricks.com/delta/optimizations/file-mgmt.html#garbage-collection), or runs in the [Auto-Optimize mode](https://docs.databricks.com/delta/optimizations/auto-optimize.html))
8. This Delta Lake table can be queried for the analysis of the access patterns

[![img](https://databricks.com/wp-content/uploads/2019/10/0.png)](https://databricks.com/wp-content/uploads/2019/10/0.png)

 

## Administrative Setup

First we will perform the administrative setup of configuring our S3 Server Access Logging and creating an SQS Queue.

### Configure S3 Server Access Logging

First of all you need to configure S3 Server Access Logging for the data-bucket. To store the raw logs you first need to [create](https://docs.aws.amazon.com/AmazonS3/latest/gsg/CreatingABucket.html) an additional bucket – let’s call it raw-logs-bucket. Then you can configure logging [via UI](https://docs.aws.amazon.com/AmazonS3/latest/user-guide/server-access-logging.html) or [using API](https://docs.aws.amazon.com/cli/latest/reference/s3api/put-bucket-logging.html). Let’s assume that we specify target prefix as **data-bucket-logs/**, so that we can use this bucket for S3 access logs of multiple data buckets.

After this is done – raw logs will start appearing in the raw-logs-bucket as soon as someone is doing requests to the data-bucket. The number and the size of the objects with logs will depend on the intensity of requests. We experienced three different patterns for three different buckets as noted in the table below.

[![img](https://databricks.com/wp-content/uploads/2019/10/0-1.png)](https://databricks.com/wp-content/uploads/2019/10/0-1.png)

You can see that the velocity of data can be rather different, which means you have to account for this when processing these disparate sources of data.

### Create an SQS queue

Now, when logs are being created, you can start thinking about how to read them with Spark to produce the desired Delta Lake table. Because S3 logs are written in the append-only mode – only new objects get created, and no object ever gets modified or deleted – this is a perfect case to leverage the [S3-SQS Spark reader](https://docs.databricks.com/spark/latest/structured-streaming/sqs.html) created by Databricks. To use it, you need first of all to [create an SQS queue](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-create-queue.html). We recommend to set *Message Retention Period* to **7 days**, and *Default Visibility Timeout* to **5 minutes**. From our experience, these are good defaults, that as well match defaults of the Spark S3-SQS reader. Let’s refer to the queue with the name new-log-objects-queue.

Now you need to configure the [policy of the queue](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-basic-examples-of-sqs-policies.html) to allow sending messages to the queue from the raw-logs-bucket. To achieve this you can edit it directly in the Permissions tab of the queue in the UI, or do it [via API](https://docs.aws.amazon.com/cli/latest/reference/sqs/set-queue-attributes.html). This is how the statement should look like:

```
{
    "Effect": "Allow",
    "Principal": "*",
    "Action": "SQS:SendMessage",
    "Resource": "arn:aws:sqs:{REGION}:{MAIN_ACCOUNT_ID}:new-log-objects-queue",
    "Condition": {
        "ArnEquals": {
            "aws:SourceArn": "arn:aws:s3:::raw-logs-bucket"
        }
    }
}
```

### Configure S3 event notification

Now, you are ready to connect raw-logs-bucket and new-log-objects-queue, so that for each new object there is a message sent to the queue. To achieve this you can configure the S3 Event Notification [in the UI](https://docs.aws.amazon.com/AmazonS3/latest/dev/NotificationHowTo.html) or [via API](https://docs.aws.amazon.com/cli/latest/reference/s3api/put-bucket-notification-configuration.html). We show here how the JSON version of this configuration would look like:

```
{

    "QueueConfigurations": [
        {
            "Id": "raw-logs",
            "QueueArn": "arn:aws:sqs:{REGION}:{MAIN_ACCOUNT_ID}:new-log-objects-queue",
            "Events": ["s3:ObjectCreated:*"]
        }
    ]
}
```

## Operational Setup

In this section, we will perform the necessary cluster configurations including creating IAM roles and prepare the cluster configuration.

### Create IAM roles

To be able to run the Spark job, you need to create two IAM roles – one for the job (cluster role), and one to access S3 (assumed role). The reason you need to additionally assume a separate S3 role is that the cluster and its cluster role are located in the dedicated AWS account for Databricks EC2 instances and roles, whereas the raw-logs-bucket is located in the AWS account where the original source bucket resides. And because every log object is written by the Amazon role – there is an implication that cluster role doesn’t have permission to read any of the logs in accordance to the ACL of the log objects. You can read more about it in [Secure Access to S3 Buckets Across Accounts Using IAM Roles with an AssumeRole Policy](https://docs.databricks.com/administration-guide/cloud-configurations/aws/assume-role.html).

The cluster role, referred here as **cluster-role**, should be created in the AWS account dedicated for Databricks, and should have these 2 policies:

```
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Action": [
                "sqs:ReceiveMessage",
                "sqs:DeleteMessage",
                "sqs:GetQueueAttributes"
            ],
            "Resource": ["arn:aws:sqs:{REGION}:{DATABRICKS_ACCOUNT_ID}:new-log-objects-queue"],
            "Effect": "Allow"
        }
    ]
}
```

and

```
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Action": "sts:AssumeRole",
            "Resource": "arn:aws:iam::{DATABRICKS_ACCOUNT_ID}:role/s3-access-role-to-assume"
        }
    ]
}
```

You will also need to add the instance profile of this role as usual to the Databricks platform.

The role to access S3, referred here as **s3-access-role-to-assume**, should be created in the same account, where both buckets reside. It should refer to the cluster-role by its ARN in the *assumed_by* parameter, and should have these 2 policies:

```
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Action": [
                "s3:ListBucket",
                "s3:GetBucketLocation",
                "s3:GetObject",
                "s3:GetObjectMetadata"
            ],
            "Resource": [
                "arn:aws:s3:::raw-logs-bucket",
                "arn:aws:s3:::raw-logs-bucket/*"
            ],
            "Effect": "Allow"
        }
    ]
}
```

and

```
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Action": [
                "s3:ListBucket",
                "s3:GetBucketLocation",
                "s3:GetObject",
                "s3:GetObjectMetadata",
                "s3:PutObject",
                "s3:PutObjectAcl",
                "s3:DeleteObject"
            ],
            "Resource": [
                "arn:aws:s3:::delta-logs-bucket",
                "arn:aws:s3:::delta-logs-bucket/data-bucket-logs/*"
            ]
            "Effect": "Allow"
        }
    ]
}
```

where delta-logs-bucket is another bucket you need to create, where the resulting Delta Lake tables will be located.

### Prepare cluster configuration

Here we outline the spark_conf [settings that are necessary](https://docs.databricks.com/administration-guide/cloud-configurations/aws/iam-roles.html) in the cluster configuration so that the job can run correctly:

```
spark.hadoop.fs.s3.impl com.databricks.s3a.S3AFileSystem
spark.hadoop.fs.s3n.impl com.databricks.s3a.S3AFileSystem
spark.hadoop.fs.s3a.impl com.databricks.s3a.S3AFileSystem
spark.hadoop.fs.s3a.acl.default BucketOwnerFullControl
spark.hadoop.fs.s3a.canned.acl BucketOwnerFullControl
spark.hadoop.fs.s3a.credentialsType AssumeRole
spark.hadoop.fs.s3a.stsAssumeRole.arn arn:aws:iam::{MAIN_ACCOUNT_ID}:role/s3-access-role-to-assume
```

If you go for more than one bucket, we also recommend these settings to enable FAIR scheduler, external shuffling, and RocksDB for keeping state:

```
spark.sql.streaming.stateStore.providerClass com.databricks.sql.streaming.state.RocksDBStateStoreProvider
spark.dynamicAllocation.enabled true
spark.shuffle.service.enabled true
spark.scheduler.mode FAIR
```

## Generate Delta Lake table with a Continuous Application

In the previous sections you completed the perfunctory administrative and operational setups. Now that this is done, you can write the code that will finally produce the desired Delta Lake table, and run it in a Continuous Application mode.

### The notebook

The code is written in Scala. First we define a record case class:

[![img](https://databricks.com/wp-content/uploads/2019/10/0-2.png)](https://databricks.com/wp-content/uploads/2019/10/0-2.png)

Then we create a few helper functions for parsing:

[![img](https://databricks.com/wp-content/uploads/2019/10/0-1-1.png)](https://databricks.com/wp-content/uploads/2019/10/0-1-1.png)

[
![img](https://databricks.com/wp-content/uploads/2019/10/0-2-1.png)](https://databricks.com/wp-content/uploads/2019/10/0-2-1.png)

[![img](https://databricks.com/wp-content/uploads/2019/10/0-3.png)](https://databricks.com/wp-content/uploads/2019/10/0-3.png)

And finally we define the Spark job:[![img](https://databricks.com/wp-content/uploads/2019/10/0-4.png)](https://databricks.com/wp-content/uploads/2019/10/0-4.png)

### Create Databricks job

The last step is to make the whole pipeline run. For this you need to create a Databricks job. You can use the “New Automated Cluster” type, add *spark_conf* we defined above, and schedule it to be run, for example, once every hour using “Schedule” section. This is it – as soon as you confirm creation of the job, and when it starts running by scheduler – you should be able to see that messages from the SQS queue are getting consumed, and that the job is writing to the output Delta Lake table.

## Execute Notebook Queries

At this point data is available, and you can create your notebook and execute your queries to answer questions we started with in the beginning of this blog post.

### Create interactive cluster and a notebook to run analytics

As soon as Delta Lake table has the data – you can start querying it. For this you can create a permanent cluster with the role that only needs to be able to read the delta-logs-bucket. This means it doesn’t need to use the *AssumeRole* technique, but only need *ListBucket* and *GetObject* permissions. After that you can attach a notebook to this cluster and execute your first analysis.

### Queries to analyze access patterns

Let’s get back to one of the questions that we asked in the beginning – *which datasets are used the most?* If we assume that in the source bucket every dataset is located under prefix *data/{DATASET_NAME}/*, then to answer it, we could come up with a query like this one:

```
SELECT dataset, count(*) AS cnt
FROM (
    SELECT regexp_extract(key, '^data\/([^/]+)\/.+', 1) AS dataset
    FROM delta.`s3://delta-logs-bucket/data-bucket-logs/`
    WHERE date = 'YYYY-MM-DD' AND bucket = 'data-bucket' AND key rlike '^data\/' AND operation = 'REST.GET.OBJECT'
)
GROUP BY dataset
ORDER BY cnt DESC;
```

The outcome of the query can look like this:

[![img](https://databricks.com/wp-content/uploads/2019/10/0-5.png)](https://databricks.com/wp-content/uploads/2019/10/0-5.png)

This query tells us how many individual *GetObject* requests were made to each dataset during one day, ordered from the top most accessed down to the less intensively accessed. By itself it might not be enough to say, if one dataset is accessed more often. We can normalize each aggregation by the number of objects in each dataset. Also, we can group by dataset and day, so that we also see the correlation in time. There are many further options, but the point is that having at hand this Delta Lake table we can answer any kind of question about what access patterns in the bucket.

### Extensibility

The pipeline we have shown is extensible out of the box. You can fully reuse the same SQS queue and add more buckets with logging into the pipeline, by simply using the same raw-logs-bucket to store S3 Server Access Logs. Because the Spark job already partitions by *date* and *bucket*, it will keep working fine, and your Delta Lake table will contain log data from the new buckets.

One piece of advice we can give is to use AWS CDK to handle infrastructure, i.e. to configure the buckets raw-logs-bucket and delta-logs-bucket, SQS queue, and the role s3-access-role-to-assume. This will simplify operations and make the infrastructure become code as well.

## Conclusion

In this blog post we have described how S3 Server Access Logging can be transformed into Delta Lake in a continuous fashion, so that analysis of the access patterns to the data can be performed. We showed that Spark Structured Streaming together with the S3-SQS reader can be used to read raw logging data. We described what kind of IAM policies and *spark_conf* parameters you will need to make this pipeline work. Overall, this solution is easy to deploy and operate, and it can give you a good benefit by providing observability over the access to the data.