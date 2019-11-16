# [Spark 3.0 preview release feature list and major changes](http://apache-spark-developers-list.1001551.n3.nabble.com/Spark-3-0-preview-release-feature-list-and-major-changes-td28050.html)

1. [A new approach to do adaptive execution in Spark SQL](https://issues.apache.org/jira/browse/SPARK-23128)

2. [Dynamic partition pruning](https://issues.apache.org/jira/browse/SPARK-11150)

3. [SPIP: Accelerator-aware task scheduling for Spark](https://issues.apache.org/jira/browse/SPARK-24615)

4. [data source V2 API refactoring](https://issues.apache.org/jira/browse/SPARK-25390)，https://issues.apache.org/jira/browse/SPARK-15689
   5. [Partially push down disjunctive predicated in Parquet/ORC](https://issues.apache.org/jira/browse/SPARK-27699)

   2. [Add documentation for v2 data sources](https://issues.apache.org/jira/browse/SPARK-27708)



[Apache Spark 3.0 预览版正式发布，多项重大功能发布](https://www.infoq.cn/article/oBXcj0dre2r3ii415oTr)

[看完这篇文章还不懂 Spark 的 Adaptive Execution ，我去跪榴莲](https://mp.weixin.qq.com/s?__biz=MzA5MTc0NTMwNQ==&mid=2650718363&idx=1&sn=d20fffebafdd2bed6939eaeb39f5e6e3&chksm=887dddedbf0a54fb5a68f6d06e684d11d94a39f4bb7b81e79bf1a790d1a3752c9ed5c4b81215&scene=21#wechat_redirect)



## DataSource V2

[一文理解 Apache Spark DataSource V2 诞生背景及入门实战](https://mp.weixin.qq.com/s?__biz=MzA5MTc0NTMwNQ==&mid=2650717658&idx=1&sn=722e060f32ea72415e180c19b98eb142&chksm=887da0acbf0a29babfb7a9edae9f5a577b6073ce65e8c087b6a89fe6a6ac77d634fcfaa138fe&scene=21#wechat_redirect)

[Exploring Spark DataSource V2 - Part 8 : Transactional Writes](http://blog.madhukaraphatak.com/spark-datasource-v2-part-8/)

[Migrating to Spark 2.4 Data Source API](http://blog.madhukaraphatak.com/migrate-spark-datasource-2.4/)

[Data Source V2 improvements](https://issues.apache.org/jira/browse/SPARK-22386)

### why 重构？

通过[这个PR](https://github.com/apache/spark/pull/23086)，发现了[文档](https://docs.google.com/document/d/1uUmKCpWLdh9vHxP7AWJ9EgbwB_U6T3EJYNjhISGmiQg/edit#heading=h.l22hv3trducc)，最后找到这个邮件列表里的讨论：[data source api v2 refactoring](http://apache-spark-developers-list.1001551.n3.nabble.com/data-source-api-v2-refactoring-td24848.html)



###  Join Push down

1. [Pushing down arbitrary logical plans to data sources](https://issues.apache.org/jira/browse/SPARK-12449)

2. [Extending Apache Spark SQL Data Source APIs with Join Push Down](https://databricks.com/session/extending-apache-spark-sql-data-source-apis-with-join-push-down)

3. [Data Source V2: Join Push Down](https://issues.apache.org/jira/browse/SPARK-24130)