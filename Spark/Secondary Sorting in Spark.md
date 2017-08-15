# Secondary Sorting in Spark

Secondary sorting is the technique that allows for ordering by value(s) (in addition to sorting by key) in the reduce phase of a Map-Reduce job. For example, you may want to anyalize user logons to your application. Having results sorted by day and time as well as user-id (the natural key) will help to spot user trends. The additional ordering by day and time is an example of secondary sorting. While I have written before on [Secondary Sorting in Hadoop](http://codingjunkie.net/secondary-sort/), this post is going to cover how we perform secondary sorting in Spark.

### Setup

We will use the RDD API to implement secondary sorting. While this could be easily accomplished using [DataFrames](http://spark.apache.org/docs/latest/sql-programming-guide.html#dataframes), we won’t be going over that approach. The data we are using is the airline on-time performance data from the [Bureau of Transportation](http://transtats.bts.gov/DL_SelectFields.asp?Table_ID=236&DB_Short_Name=On-Time). There are several data points available, but we’ll focus on which airlines have the most late arrivals and at which airport these late arrivals occur. From that statement we can determine our sort order: airlineId, airport id and delay time. *Disclaimer:* This example is for demonstration purposes only! It’s not meant to infer or determine actual airline performance.

### Creating the Key-Value Pairs

The data is downloaded in CSV format and will be converted into key-value format. The crucial part of secondary sorting is which value(s) to include in the key to enable the additional ordering. The ‘natural’ key is the airlineId and arrivalAirportId and arrivalyDelay are the values we’ll include in the key. This is represented by the `FlightKey` case class:

```scala
case class FlightKey(airLineId: String, arrivalAirPortId: Int, arrivalDelay: Double)
```

Our first attempt at creating our key-value pairs looks like this:

```scala
val rawDataArray = sc.textFile(args(0)).map(line => line.split(","))
  //Using keyBy but retains entire array in value
  val keyedByData = rawDataArray.keyBy(arr => createKey(arr))
 //supporting code
  def createKey(data: Array[String]): FlightKey = {
    FlightKey(data(UNIQUE_CARRIER), safeInt(data(DEST_AIRPORT_ID)), safeDouble(data(ARR_DELAY)))
  }
```

This example is simple example of using the `keyBy` function. The `keyBy` function takes a function that returns a key of a given type, `FlightKey` in this case. We have one issue with this approach. Part of the data we want to anaylize is in the key *and* remains in the original array of values. While the individual values themselves are not very large, when considering the volume of data we are working with, we’ll want to make sure we are not transferring around duplicated data. Also, the final data we will analyze only contains 7 fields. There are 24 fields in the original data. We will drop those unused fields as well. Here’s our second pass at creating our key-value pairs:

```scala
val rawDataArray = sc.textFile(args(0)).map(line => line.split(","))
val airlineData = rawDataArray.map(arr => createKeyValueTuple(arr))

 //supporting code
def createKeyValueTuple(data: Array[String]) :(FlightKey,List[String]) = {
  (createKey(data),listData(data))
}
def createKey(data: Array[String]): FlightKey = {
  FlightKey(data(UNIQUE_CARRIER), safeInt(data(DEST_AIRPORT_ID)), safeDouble(data(ARR_DELAY)))
}
def listData(data: Array[String]): List[String] = {
  List(data(FL_DATE), data(ORIGIN_AIRPORT_ID), data(ORIGIN_CITY_MARKET_ID), data(DEST_CITY_MARKET_ID))
}
```

In this example we use `map` to transform our lines into key-value pairs. We are using the same `createKey` function but we added `listData` that returns a list containing only the values we will analyze. Overall the number of fields in our value has gone from 24 to 4, which should net us some performane improvements.

### Partitioning and Sorting Code

Now we need to consider how to partition and sort our data. There are two points we need to consider.

1. We need to group the data by airline id to land in the same partition during the reduce phase. But our key is a composite key with 3 fields. Simply paritioning by key won’t work for us. So we’ll create a custom partitioner that knows which value to use in determining the partition the data will flow to.
2. We also need to tell Spark how we want our data sorted: airlineId first, then arrivalAirportId and finally arrivalDelay. Additionally we want the arrivalDelay to be in descending order, so flights with the biggest delay are listed first.

#### Partitioner Code

The partitioner code is simple. We extend the [Partitioner](http://spark.apache.org/docs/latest/api/scala/index.html#org.apache.spark.Partitioner) class like so:

```scala
class AirlineFlightPartitioner(partitions: Int) extends Partitioner {
  require(partitions >= 0, s"Number of partitions ($partitions) cannot be negative.")
  override def numPartitions: Int = partitions
  override def getPartition(key: Any): Int = {
    val k = key.asInstanceOf[FlightKey]
    k.airLineId.hashCode() % numPartitions
  }
}
```

The `AirlineFlightPartitioner` class is simple, and we can see that partitions are determined by using the `airlineId` only.

#### Sorting Code

Now we need to define how the data will be sorted once we have it located in the correct partitions. To achieve sorting we create a companion object for `FlightKey` and define an implicit [Ordering](http://www.scala-lang.org/api/2.10.4/#scala.math.Ordering) method:

```scala
object FlightKey {
  implicit def orderingByIdAirportIdDelay[A <: FlightKey] : Ordering[A] ={
    Ordering.by(fk => (fk.airLineId, fk.arrivalAirPortId, fk.arrivalDelay * -1))
  }
}
```

We used an implicit `Ordering` instead of having the `FlightKey` class extend [Ordered](http://www.scala-lang.org/api/2.10.4/#scala.math.Ordered). This is so because the `Ordered` trait implies sorting by a single ‘natural’ value and `Ordering` implies sorting on multple values. To fully explain the use of implicits in sorting is beyond the scope of this post, but the curious can go [here](http://stackoverflow.com/questions/19345030/easy-idiomatic-way-to-define-ordering-for-a-simple-case-class) for a better explaination. Now the `FlightKey` will be sorted in the correct order: airlineId first, arrivalAirportId second and finally by the amount of the delay (in descending order).

### Putting It All Together

Now it’s time to put our partitioning and sorting into action. This is achieved by using the `repartitionAndSortWithinPartitions` method on the [OrderedRDDFunctions](http://spark.apache.org/docs/latest/api/scala/index.html#org.apache.spark.rdd.OrderedRDDFunctions) class. To quote the scala doc the `repartitionAndSortWithPartions` does the following:

> Repartition the RDD according to the given partitioner and, within each resulting partition, sort records by their keys.
>
> This is more efficient than calling repartition and then sorting within each partition because it can push the sorting down into the shuffle machinery.

The partitioner we’ll use is the `AirlineFlightPartitioner` described above in the “Partitioner” section.

Now with all the pieces in order we do the following to execute our secodary sort:

```scala
object SecondarySort extends SparkJob {
  def runSecondarySortExample(args: Array[String]): Unit = {
    val sc = context("SecondarySorting")
    val rawDataArray = sc.textFile(args(0)).map(line => line.split(","))
    val airlineData = rawDataArray.map(arr => createKeyValueTuple(arr))
    val keyedDataSorted = airlineData.repartitionAndSortWithinPartitions(new AirlineFlightPartitioner(1))
    //only done locally for demo purposes, usually write out to HDFS
    keyedDataSorted.collect().foreach(println)
  }
}
```

After all the buildup in this post, we end up with a somewhat anti-climatic one-liner! The number of partitions provided here is set to 1 because these examples were run locally. On a real cluster you’d want to use a different value.

### Results

Running the spark job yields these results:

```scala
(FlightKey("AA",10397,-2.0),List(2015-01-01, 11298, 30194, 30397))
(FlightKey("AA",11278,-2.0),List(2015-01-01, 11298, 30194, 30852))
(FlightKey("AA",11278,-14.0),List(2015-01-01, 12892, 32575, 30852))
(FlightKey("AA",11292,24.0),List(2015-01-01, 13930, 30977, 30325))
(FlightKey("AA",11298,133.0),List(2015-01-01, 13891, 32575, 30194))
(FlightKey("AA",11298,109.0),List(2015-01-01, 12173, 32134, 30194))
(FlightKey("AA",11298,55.0),List(2015-01-01, 14107, 30466, 30194))
(FlightKey("AA",11298,49.0),List(2015-01-01, 12478, 31703, 30194))
(FlightKey("AA",11298,40.0),List(2015-01-01, 14771, 32457, 30194))
(FlightKey("AA",11298,35.0),List(2015-01-01, 12094, 34699, 30194))
(FlightKey("AA",11298,24.0),List(2015-01-01, 13830, 33830, 30194))
(FlightKey("AA",11298,23.0),List(2015-01-01, 14869, 34614, 30194))
(FlightKey("AA",11298,19.0),List(2015-01-01, 11433, 31295, 30194))
(FlightKey("AA",11298,17.0),List(2015-01-01, 12264, 30852, 30194))
```

We can see our results sorted by airlineId, arrivalAirportId and the delay. It’s worth noting negative numbers represent flights that arrived *early* (who would have thought that flights could arrive early!). These results would be more meaningful by translating the codes into recognizable names. In a future post we’ll demonstate just that using [Broadcast Variables](http://spark.apache.org/docs/latest/programming-guide.html#broadcast-variables).

### Conclusion

Hopefully, we can see the usefulness of secondary sorting as well as the ease of implementing it in Spark. As a matter fact, I consider this post to have a high noise-to-signal ratio, meaning that we did a lot of discussion on what secondary sorting is and how we go about it, but the actual code we excute is only a few lines. Thanks for your time.

### Resources

- [Source Code for post](https://github.com/bbejeck/spark-experiments/blob/master/src/main/scala-2.10/bbejeck/sorting/SecondarySort.scala)
- [OrderedRDDFunctions](http://spark.apache.org/docs/latest/api/scala/index.html#org.apache.spark.rdd.OrderedRDDFunctions)
- [Spark Scala API](http://spark.apache.org/docs/latest/api/scala/index.html#org.apache.spark.package)
- Cloudera Blog Post referencing [secondary sorting](http://blog.cloudera.com/blog/2015/03/how-to-tune-your-apache-spark-jobs-part-1/)
- [Department Of Transportation On-Time Flight Data](http://transtats.bts.gov/DL_SelectFields.asp?Table_ID=236&DB_Short_Name=On-Time)