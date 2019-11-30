# Lattices

A lattice is a framework for creating and populating materialized views, and for recognizing that a materialized view can be used to solve a particular query.

- [Concept](https://calcite.apache.org/docs/lattice.html#concept)
- [Demonstration](https://calcite.apache.org/docs/lattice.html#demonstration)
- [Statistics](https://calcite.apache.org/docs/lattice.html#statistics)
- [Lattice suggester](https://calcite.apache.org/docs/lattice.html#lattice-suggester)
- [Further directions](https://calcite.apache.org/docs/lattice.html#further-directions)
- [References](https://calcite.apache.org/docs/lattice.html#references)

## Concept

A lattice represents a star (or snowflake) schema, not a general schema. In particular, all relationships must be many-to-one, heading from a fact table at the center of the star.

The name derives from the mathematics: a [lattice](https://en.wikipedia.org/wiki/Lattice_(order)) is a [partially ordered set](https://en.wikipedia.org/wiki/Partially_ordered_set) where any two elements have a unique greatest lower bound and least upper bound.

[[HRU96](https://calcite.apache.org/docs/lattice.html#ref-hru96)] observed that the set of possible materializations of a data cube forms a lattice, and presented an algorithm to choose a good set of materializations. Calcite’s recommendation algorithm is derived from this.

The lattice definition uses a SQL statement to represent the star. SQL is a useful short-hand to represent several tables joined together, and assigning aliases to the column names (it more convenient than inventing a new language to represent relationships, join conditions and cardinalities).

Unlike regular SQL, order is important. If you put A before B in the FROM clause, and make a join between A and B, you are saying that there is a many-to-one foreign key relationship from A to B. (E.g. in the example lattice, the Sales fact table occurs before the Time dimension table, and before the Product dimension table. The Product dimension table occurs before the ProductClass outer dimension table, further down an arm of a snowflake.)

A lattice implies constraints. In the A to B relationship, there is a foreign key on A (i.e. every value of A’s foreign key has a corresponding value in B’s key), and a unique key on B (i.e. no key value occurs more than once). These constraints are really important, because it allows the planner to remove joins to tables whose columns are not being used, and know that the query results will not change.

Calcite does not check these constraints. If they are violated, Calcite will return wrong results.

A lattice is a big, virtual join view. It is not materialized (it would be several times larger than the star schema, because of denormalization) and you probably wouldn’t want to query it (far too many columns). So what is it useful for? As we said above, (a) the lattice declares some very useful primary and foreign key constraints, (b) it helps the query planner map user queries onto filter-join-aggregate materialized views (the most useful kind of materialized view for DW queries), (c) gives Calcite a framework within which to gather stats about data volumes and user queries, (d) allows Calcite to automatically design and populate materialized views.

Most star schema models force you to choose whether a column is a dimension or a measure. In a lattice, every column is a dimension column. (That is, it can become one of the columns in the GROUP BY clause to query the star schema at a particular dimensionality). Any column can also be used in a measure; you define measures by giving the column and an aggregate function.

If “unit_sales” tends to be used much more often as a measure rather than a dimension, that’s fine. Calcite’s algorithm should notice that it is rarely aggregated, and not be inclined to create tiles that aggregate on it. (By “should” I mean “could and one day will”. The algorithm does not currently take query history into account when designing tiles.)

But someone might want to know whether orders with fewer than 5 items were more or less profitable than orders with more than 100. All of a sudden, “unit_sales” has become a dimension. If there’s virtually zero cost to declaring a column a dimension column, I figured let’s make them all dimension columns.

The model allows for a particular table to be used more than once, with a different table alias. You could use this to model say OrderDate and ShipDate, with two uses to the Time dimension table.

Most SQL systems require that the column names in a view are unique. This is hard to achieve in a lattice, because you often include primary and foreign key columns in a join. So Calcite lets you refer to columns in two ways. If the column is unique, you can use its name, [“unit_sales”]. Whether or not it is unique in the lattice, it will be unique in its table, so you can use it qualified by its table alias. Examples:

- [“sales”, “unit_sales”]
- [“ship_date”, “time_id”]
- [“order_date”, “time_id”]

A “tile” is a materialized table in a lattice, with a particular dimensionality. The “tiles” attribute of the [lattice JSON element](https://calcite.apache.org/docs/model.html#lattice) defines an initial set of tiles to materialize.

## Demonstration

Create a model that includes a lattice:

```
{
  "version": "1.0",
  "defaultSchema": "foodmart",
  "schemas": [ {
    "type": "jdbc",
    "name": "foodmart",
    "jdbcUser": "FOODMART",
    "jdbcPassword": "FOODMART",
    "jdbcUrl": "jdbc:hsqldb:res:foodmart",
    "jdbcSchema": "foodmart"
  },
  {
    "name": "adhoc",
    "lattices": [ {
      "name": "star",
      "sql": [
        "select 1 from \"foodmart\".\"sales_fact_1997\" as \"s\"",
        "join \"foodmart\".\"product\" as \"p\" using (\"product_id\")",
        "join \"foodmart\".\"time_by_day\" as \"t\" using (\"time_id\")",
        "join \"foodmart\".\"product_class\" as \"pc\" on \"p\".\"product_class_id\" = \"pc\".\"product_class_id\""
      ],
      "auto": true,
      "algorithm": true,
      "rowCountEstimate": 86837,
      "defaultMeasures": [ {
        "agg": "count"
      } ]
    } ]
  } ]
}
```

This is a cut-down version of [hsqldb-foodmart-lattice-model.json](https://github.com/apache/calcite/blob/master/core/src/test/resources/hsqldb-foodmart-lattice-model.json) that does not include the “tiles” attribute, because we are going to generate tiles automatically. Let’s log into sqlline and connect to this schema:

```
$ sqlline version 1.3.0
sqlline> !connect jdbc:calcite:model=core/src/test/resources/hsqldb-foodmart-lattice-model.json "sa" ""
```

You’ll notice that it takes a few seconds to connect. Calcite is running the optimization algorithm, and creating and populating materialized views. Let’s run a query and check out its plan:

```
sqlline> select "the_year","the_month", count(*) as c
. . . .> from "sales_fact_1997"
. . . .> join "time_by_day" using ("time_id")
. . . .> group by "the_year","the_month";
+----------+-----------+------+
| the_year | the_month |    C |
+----------+-----------+------+
| 1997     | September | 6663 |
| 1997     | April     | 6590 |
| 1997     | January   | 7034 |
| 1997     | June      | 6912 |
| 1997     | August    | 7038 |
| 1997     | February  | 6844 |
| 1997     | March     | 7710 |
| 1997     | October   | 6479 |
| 1997     | May       | 6866 |
| 1997     | December  | 8717 |
| 1997     | July      | 7752 |
| 1997     | November  | 8232 |
+----------+-----------+------+
12 rows selected (0.147 seconds)

sqlline> explain plan for
. . . .> select "the_year","the_month", count(*) as c
. . . .> from "sales_fact_1997"
. . . .> join "time_by_day" using ("time_id")
. . . .> group by "the_year","the_month";
+--------------------------------------------------------------------------------+
| PLAN                                                                           |
+--------------------------------------------------------------------------------+
| EnumerableCalc(expr#0..2=[{inputs}], the_year=[$t1], the_month=[$t0], C=[$t2]) |
|   EnumerableAggregate(group=[{3, 4}], C=[$SUM0($7)])                           |
|     EnumerableTableScan(table=[[adhoc, m{16, 17, 27, 31, 32, 36, 37}]])        |
+--------------------------------------------------------------------------------+
```

The query gives the right answer, but plan is somewhat surprising. It doesn’t read the `sales_fact_1997` or `time_by_day` tables, but instead reads from a table called `m{16, 17, 27, 31, 32, 36, 37}`. This is one of the tiles created at the start of the connection.

It’s a real table, and you can even query it directly. It has only 120 rows, so is a more efficient way to answer the query:

```
sqlline> !describe "adhoc"."m{16, 17, 27, 31, 32, 36, 37}"
+-------------+-------------------------------+--------------------+-----------+-----------------+
| TABLE_SCHEM | TABLE_NAME                    | COLUMN_NAME        | DATA_TYPE | TYPE_NAME       |
+-------------+-------------------------------+--------------------+-----------+-----------------+
| adhoc       | m{16, 17, 27, 31, 32, 36, 37} | recyclable_package | 16        | BOOLEAN         |
| adhoc       | m{16, 17, 27, 31, 32, 36, 37} | low_fat            | 16        | BOOLEAN         |
| adhoc       | m{16, 17, 27, 31, 32, 36, 37} | product_family     | 12        | VARCHAR(30)     |
| adhoc       | m{16, 17, 27, 31, 32, 36, 37} | the_month          | 12        | VARCHAR(30)     |
| adhoc       | m{16, 17, 27, 31, 32, 36, 37} | the_year           | 5         | SMALLINT        |
| adhoc       | m{16, 17, 27, 31, 32, 36, 37} | quarter            | 12        | VARCHAR(30)     |
| adhoc       | m{16, 17, 27, 31, 32, 36, 37} | fiscal_period      | 12        | VARCHAR(30)     |
| adhoc       | m{16, 17, 27, 31, 32, 36, 37} | m0                 | -5        | BIGINT NOT NULL |
+-------------+-------------------------------+--------------------+-----------+-----------------+

sqlline> select count(*) as c
. . . .> from "adhoc"."m{16, 17, 27, 31, 32, 36, 37}";
+-----+
|   C |
+-----+
| 120 |
+-----+
1 row selected (0.12 seconds)
```

Let’s list the tables, and you will see several more tiles. There are also tables of the `foodmart` schema, and the system tables `TABLES` and `COLUMNS`, and the lattice itself, which appears as a table called `star`.

```
sqlline> !tables
+-------------+-------------------------------+--------------+
| TABLE_SCHEM | TABLE_NAME                    | TABLE_TYPE   |
+-------------+-------------------------------+--------------+
| adhoc       | m{16, 17, 18, 32, 37}         | TABLE        |
| adhoc       | m{16, 17, 19, 27, 32, 36, 37} | TABLE        |
| adhoc       | m{4, 7, 16, 27, 32, 37}       | TABLE        |
| adhoc       | m{4, 7, 17, 27, 32, 37}       | TABLE        |
| adhoc       | m{7, 16, 17, 19, 32, 37}      | TABLE        |
| adhoc       | m{7, 16, 17, 27, 30, 32, 37}  | TABLE        |
| adhoc       | star                          | STAR         |
| foodmart    | customer                      | TABLE        |
| foodmart    | product                       | TABLE        |
| foodmart    | product_class                 | TABLE        |
| foodmart    | promotion                     | TABLE        |
| foodmart    | region                        | TABLE        |
| foodmart    | sales_fact_1997               | TABLE        |
| foodmart    | store                         | TABLE        |
| foodmart    | time_by_day                   | TABLE        |
| metadata    | COLUMNS                       | SYSTEM_TABLE |
| metadata    | TABLES                        | SYSTEM_TABLE |
+-------------+-------------------------------+--------------+
```

## Statistics

The algorithm that chooses which tiles of a lattice to materialize depends on a lot of statistics. It needs to know `select count(distinct a, b, c) from star` for each combination of columns (`a, b, c`) it is considering materializing. As a result the algorithm takes a long time on schemas with many rows and columns.

We are working on a [data profiler](https://issues.apache.org/jira/browse/CALCITE-1616) to address this.

## Lattice suggester

If you have defined a lattice, Calcite will self-tune within that lattice. But what if you have not defined a lattice?

Enter the Lattice Suggester, which builds lattices based on incoming queries. Create a model with a schema that has `"autoLattice": true`:

```
{
  "version": "1.0",
  "defaultSchema": "foodmart",
  "schemas": [ {
    "type": "jdbc",
    "name": "foodmart",
    "jdbcUser": "FOODMART",
    "jdbcPassword": "FOODMART",
    "jdbcUrl": "jdbc:hsqldb:res:foodmart",
    "jdbcSchema": "foodmart"
  }, {
    "name": "adhoc",
    "autoLattice": true
  } ]
}
```

This is a cut-down version of [hsqldb-foodmart-lattice-model.json](https://github.com/apache/calcite/blob/master/core/src/test/resources/hsqldb-foodmart-lattice-model.json)

As you run queries, Calcite will start to build lattices based on those queries. Each lattice is based on a particular fact table. As it sees more queries on that fact table, it will evolve the lattice, joining more dimension tables to the star, and adding measures.

Each lattice will then optimize itself based on both the data and the queries. The goal is to create summary tables (tiles) that are reasonably small but are based on more frequently used attributes and measures.

This feature is still experimental, but has the potential to make databases more “self-tuning” than before.

## Further directions

Here are some ideas that have not yet been implemented:

- The algorithm that builds tiles takes into account a log of past queries.
- Materialized view manager sees incoming queries and builds tiles for them.
- Materialized view manager drops tiles that are not actively used.
- Lattice suggester adds lattices based on incoming queries, transfers tiles from existing lattices to new lattices, and drops lattices that are no longer being used.
- Tiles that cover a horizontal slice of a table; and a rewrite algorithm that can answer a query by stitching together several tiles and going to the raw data to fill in the holes.
- API to invalidate tiles, or horizontal slices of tiles, when the underlying data is changed.

## References

- [HRU96] V. Harinarayan, A. Rajaraman and J. Ullman. [Implementing data cubes efficiently](https://web.eecs.umich.edu/~jag/eecs584/papers/implementing_data_cube.pdf). In *Proc. ACM SIGMOD Conf.*, Montreal, 1996.

# Materialized Views

There are several different ways to exploit materialized views in Calcite.

- [Materialized views maintained by Calcite](https://calcite.apache.org/docs/materialized_views.html#materialized-views-maintained-by-calcite)
- Expose materialized views to Calcite
  - View-based query rewriting
    - [Substitution via rules transformation](https://calcite.apache.org/docs/materialized_views.html#substitution-via-rules-transformation)
    - Rewriting using plan structural information
      - Rewriting coverage
        - [Join rewriting](https://calcite.apache.org/docs/materialized_views.html#join-rewriting)
        - [Aggregate rewriting](https://calcite.apache.org/docs/materialized_views.html#aggregate-rewriting)
        - [Aggregate rewriting (with aggregation rollup)](https://calcite.apache.org/docs/materialized_views.html#aggregate-rewriting-with-aggregation-rollup)
        - [Query partial rewriting](https://calcite.apache.org/docs/materialized_views.html#query-partial-rewriting)
        - [View partial rewriting](https://calcite.apache.org/docs/materialized_views.html#view-partial-rewriting)
        - [Union rewriting](https://calcite.apache.org/docs/materialized_views.html#union-rewriting)
        - [Union rewriting with aggregate](https://calcite.apache.org/docs/materialized_views.html#union-rewriting-with-aggregate)
      - [Limitations](https://calcite.apache.org/docs/materialized_views.html#limitations)
- [References](https://calcite.apache.org/docs/materialized_views.html#references)

## Materialized views maintained by Calcite

For details, see the [lattices documentation](https://calcite.apache.org/docs/lattice.html).

## Expose materialized views to Calcite

Some Calcite adapters as well as projects that rely on Calcite have their own notion of materialized views.

For example, Apache Cassandra allows the user to define materialized views based on existing tables which are automatically maintained. The Cassandra adapter automatically exposes these materialized views to Calcite.

Another example is Apache Hive. When a materialized view is created in Hive, the user can specify whether the view may be used in query optimization. If the user chooses to do so, the materialized view will be registered with Calcite.

By registering materialized views in Calcite, the optimizer has the opportunity to automatically rewrite queries to use these views.

### View-based query rewriting

View-based query rewriting aims to take an input query which can be answered using a preexisting view and rewrite the query to make use of the view. Currently Calcite has two implementations of view-based query rewriting.

#### Substitution via rules transformation

**<u>The first approach</u>** is based on view substitution. `SubstitutionVisitor` and its extension `MaterializedViewSubstitutionVisitor` aim to substitute part of the relational algebra tree with an equivalent expression which makes use of a materialized view. The scan over the materialized view and the materialized view definition plan are registered with the planner. Afterwards, transformation rules that try to unify expressions in the plan are triggered. Expressions do not need to be equivalent to be replaced: the visitor might add a residual predicate on top of the expression if needed.

The following example is taken from the documentation of `SubstitutionVisitor`:

- Query: `SELECT a, c FROM t WHERE x = 5 AND b = 4`
- Target (materialized view definition): `SELECT a, b, c FROM t WHERE x = 5`
- Result: `SELECT a, c FROM mv WHERE b = 4`

Note that `result` uses the materialized view table `mv` and a simplified condition `b = 4`.

While this approach can accomplish a large number of rewritings, it has some limitations. Since the rule relies on transformation rules to create the equivalence between expressions in the query and the materialized view, it might need to enumerate exhaustively all possible equivalent rewritings for a given expression to find a materialized view substitution. However, this is not scalable in the presence of complex views, e.g., views with an arbitrary number of join operators.

#### Rewriting using plan structural information

In turn, an alternative rule that attempts to match queries to views by extracting some structural information about the expression to replace has been proposed.

`AbstractMaterializedViewRule` builds on the ideas presented in [[GL01](https://calcite.apache.org/docs/materialized_views.html#ref-gl01)] and introduces some additional extensions. The rule can rewrite expressions containing arbitrary chains of Join, Filter, and Project operators. Additionally, the rule can rewrite expressions rooted at an Aggregate operator, rolling aggregations up if necessary. In turn, it can also produce rewritings using Union operators if the query can be partially answered from a view.

To produce a larger number of rewritings, the rule relies on information exposed as constraints defined over the database tables, e.g., *foreign keys*, *primary keys*, *unique keys* or *not null*.

##### Rewriting coverage

Let us illustrate with some examples the coverage of the view rewriting algorithm implemented in `AbstractMaterializedViewRule`. The examples are based on the following database schema.

```
CREATE TABLE depts(
  deptno INT NOT NULL,
  deptname VARCHAR(20),
  PRIMARY KEY (deptno)
);
CREATE TABLE locations(
  locationid INT NOT NULL,
  state CHAR(2),
  PRIMARY KEY (locationid)
);
CREATE TABLE emps(
  empid INT NOT NULL,
  deptno INT NOT NULL,
  locationid INT NOT NULL,
  empname VARCHAR(20) NOT NULL,
  salary DECIMAL (18, 2),
  PRIMARY KEY (empid),
  FOREIGN KEY (deptno) REFERENCES depts(deptno),
  FOREIGN KEY (locationid) REFERENCES locations(locationid)
);
```

###### Join rewriting

The rewriting can handle different join orders in the query and the view definition. In addition, the rule tries to detect when a compensation predicate could be used to produce a rewriting using a view.

- Query:

```
SELECT empid
FROM depts
JOIN (
  SELECT empid, deptno
  FROM emps
  WHERE empid = 1) AS subq
ON depts.deptno = subq.deptno
```

- Materialized view definition:

```
SELECT empid
FROM emps
JOIN depts USING (deptno)
```

- Rewriting:

```
SELECT empid
FROM mv
WHERE empid = 1
```

###### Aggregate rewriting

- Query:

```
SELECT deptno
FROM emps
WHERE deptno > 10
GROUP BY deptno
```

- Materialized view definition:

```
SELECT empid, deptno
FROM emps
WHERE deptno > 5
GROUP BY empid, deptno
```

- Rewriting:

```
SELECT deptno
FROM mv
WHERE deptno > 10
GROUP BY deptno
```

###### Aggregate rewriting (with aggregation rollup)

- Query:

```
SELECT deptno, COUNT(*) AS c, SUM(salary) AS s
FROM emps
GROUP BY deptno
```

- Materialized view definition:

```
SELECT empid, deptno, COUNT(*) AS c, SUM(salary) AS s
FROM emps
GROUP BY empid, deptno
```

- Rewriting:

```
SELECT deptno, SUM(c), SUM(s)
FROM mv
GROUP BY deptno
```

###### Query partial rewriting

Through the declared constraints, the rule can detect joins that only append columns without altering the tuples multiplicity and produce correct rewritings.

- Query:

```
SELECT deptno, COUNT(*)
FROM emps
GROUP BY deptno
```

- Materialized view definition:

```
SELECT empid, depts.deptno, COUNT(*) AS c, SUM(salary) AS s
FROM emps
JOIN depts USING (deptno)
GROUP BY empid, depts.deptno
```

- Rewriting:

```
SELECT deptno, SUM(c)
FROM mv
GROUP BY deptno
```

###### View partial rewriting

- Query:

```
SELECT deptname, state, SUM(salary) AS s
FROM emps
JOIN depts ON emps.deptno = depts.deptno
JOIN locations ON emps.locationid = locations.locationid
GROUP BY deptname, state
```

- Materialized view definition:

```
SELECT empid, deptno, state, SUM(salary) AS s
FROM emps
JOIN locations ON emps.locationid = locations.locationid
GROUP BY empid, deptno, state
```

- Rewriting:

```
SELECT deptname, state, SUM(s)
FROM mv
JOIN depts ON mv.deptno = depts.deptno
GROUP BY deptname, state
```

###### Union rewriting

- Query:

```
SELECT empid, deptname
FROM emps
JOIN depts ON emps.deptno = depts.deptno
WHERE salary > 10000
```

- Materialized view definition:

```
SELECT empid, deptname
FROM emps
JOIN depts ON emps.deptno = depts.deptno
WHERE salary > 12000
```

- Rewriting:

```
SELECT empid, deptname
FROM mv
UNION ALL
SELECT empid, deptname
FROM emps
JOIN depts ON emps.deptno = depts.deptno
WHERE salary > 10000 AND salary <= 12000
```

###### Union rewriting with aggregate

- Query:

```
SELECT empid, deptname, SUM(salary) AS s
FROM emps
JOIN depts ON emps.deptno = depts.deptno
WHERE salary > 10000
GROUP BY empid, deptname
```

- Materialized view definition:

```
SELECT empid, deptname, SUM(salary) AS s
FROM emps
JOIN depts ON emps.deptno = depts.deptno
WHERE salary > 12000
GROUP BY empid, deptname
```

- Rewriting:

```
SELECT empid, deptname, SUM(s)
FROM (
  SELECT empid, deptname, s
  FROM mv
  UNION ALL
  SELECT empid, deptname, SUM(salary) AS s
  FROM emps
  JOIN depts ON emps.deptno = depts.deptno
  WHERE salary > 10000 AND salary <= 12000
  GROUP BY empid, deptname) AS subq
GROUP BY empid, deptname
```

##### Limitations

This rule still presents some limitations. In particular, the rewriting rule attempts to match all views against each query. We plan to implement more refined filtering techniques such as those described in [[GL01](https://calcite.apache.org/docs/materialized_views.html#ref-gl01)].

## References

- [GL01] Jonathan Goldstein and Per-åke Larson. [Optimizing queries using materialized views: A practical, scalable solution](https://citeseerx.ist.psu.edu/viewdoc/summary?doi=10.1.1.95.113). In *Proc. ACM SIGMOD Conf.*, 2001.