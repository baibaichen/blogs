# MDX at First Glance: Introduction to SQL Server MDX Essentials

**By William Pearson**

*Unlock data warehouses with the analytical language of OLAP. Join Author Bill Pearson in the first of a new series of tutorials designed to get you up and running with the fundamentals of Multidimensional Expressions (MDX).*

### About the Series...

This is the first article of my new series, **MDX Essentials**. The primary focus of this series will be an introduction to the MDX language. The series is designed to provide hands-on application of the fundamentals of the **Multidimensional Expressions (MDX)** language, with each tutorial progressively adding features designed to meet specific real-world needs.As we progress through the series, we will build upon previous lessons and the concepts we have introduced therein. However, one of my objectives is to make each lesson as "standalone" as possible, meaning that we should not encounter cases where we cannot complete a given lesson without components or objects that we have created in previous lessons. This should make it easier for "casual" visitors to join us with any lesson, and still successfully complete that session, given an existing understanding of concepts and principles that we have accumulated up to that point.

### What We Will Need to Complete the Tutorials

To get the most out of the *MDX Essentials* series, we need to have installed at least the Analysis Services component of MSSQL Server 2000. While the full installation of SQL Server 2000 allows for virtually any exercise we might undertake, the majority of our sessions center on Analysis Services, the PivotTable Service, and their constituent parts. Installation of Analysis Services from the Standard Edition of MSSQL Server 2000 will be adequate for the vast majority of our activities.

For purposes of carrying out limited Microsoft Office -- related activities, Microsoft Excel 2000 and, to a lesser extent, Microsoft FrontPage 2000 will come in handy. We will also make use of the Microsoft OLAP Provider, included in a typical Excel 2000 installation, which consists of the data source driver and the client software needed to access cubes created by Microsoft SQL Server 2000 Analysis Services.

For purposes of the series, it is assumed that MSSQL Server 2000 and, specifically, the MSSQL Server 2000 Analysis Services components (I will often substitute the term "*Analysis Services*" going forward, to save time and space) are accessible to/installed on the PC, with the appropriate access rights to the sample cubes provided in a *Typical* installation of Analysis Services. It is also assumed that the computer(s) involved meet the system requirements, including hardware and operating systems, of the applications we have mentioned.


### Introduction to MDX: The First Blush

This initial tutorial will introduce the MDX query in its simplest form. We'll take a look at some of the basic keywords, focusing only on simple queries, upon which we will build on in later lessons. Beginning in our first lesson, and throughout most of our series, we will discuss the element(s) of the language under consideration and then perform practice activities, with meaningful examples, to reinforce the concepts we have introduced. In this session, we will explore the **==rudiments==** of MDX queries within their simplest contexts (syntax) and introduce terms (semantics) that are applicable as they arise. This lesson will include:

- A brief introduction to MDX;
- A discussion of the basic keywords commonly used in MDX;
- A breakdown of a simple MDX query into its component parts;
- Brief comparisons and contrasts of MDX to SQL where useful;
- Other introductory items.

Let's begin by creating and executing a basic MDX query that will provide us a **==tangible==** starting point for discussing keywords and components.

### Multidimensional Expressions (MDX) as a Language

**MDX** emerged circa 1998, when it first began to appear in commercial applications. MDX was created to query OLAP databases, and has become widely adopted within the realm of analytical applications. MDX forms the language component of *OLE DB for OLAP*, and was designed by Microsoft as a standard for issuing queries to, and exchanging data with, multidimensional data sources. The language is rapidly gaining the support of many OLAP providers, and this trend is expected to continue.

The focus of the **MDX Essentials** series will be MDX as implemented in *MSSQL 2000 Analysis Services*. MDX acts in two capacities within Analysis Services: as an *expression language* that is used to calculate values, and as a *query language* that is accessed and used by client applications to retrieve data. We will address aspects of both perspectives throughout the series.

### The Basic MDX Query

Let's begin by creating a rudimentary query using the MDX Sample Application. The Sample Application is installed with Analysis Services and allows us to create an MDX statement, execute it, and see the results in a grid-like results pane. We'll start the application and proceed, taking the following steps:Go to the Start button on the PC, and then navigate to Microsoft SQL Server -> Analysis Services, then to the MDX Sample Application.We are initially greeted by the Connect dialog, shown in Illustration 1 below.
[![null](https://www.databasejournal.com/img/DJ_MDX01-001.jpg)](https://www.databasejournal.com/img/DJ_MDX01-001.jpg)
**Illustration 1: The Connect Dialog for the MDX Sample Application** [(Enlarge)](https://www.databasejournal.com/img/DJ_MDX01-001.jpg)

The illustration above depicts the name of one of my servers, RAPHAEL, and properly indicates that we will be connecting via the MSOLAP provider (the default).



1. Click OK.

(We might also choose to cancel the dialog box and connect later by clicking Connect on the File menu.)

The MDX Sample Application window appears.

1. Clear the top area (the Query pane) of any remnants of queries that might appear.
2. Ensure that FoodMart 2000 is selected as the database name in the DB box of the toolbar.
3. Select the Warehouse cube in the Cube drop-down list box.

The MDX Sample Application window should resemble that shown below, complete with the information from the Warehouse cube displaying in the Metadata tree (in the left section of the Metadata pane between the Query pane at the top of the application window and the Results pane at the bottom.).

![null](https://www.databasejournal.com/img/DJ_MDX01-002.jpg)
**Illustration 2: The MDX Sample Application Window (Compressed)**


We type our MDX statements into the Query pane, as we shall see. Above the Query pane are the menu bar and toolbar. The Metadata pane allows us to visually inspect structural information about the selected cube. Syntax examples on the right side of this middle pane assist us in the creation of our statements. The Results pane at the bottom of the application window presents the output of our MDX queries. We will discuss various attributes of the MDX Sample Application where they are relevant to the exercises we undertake, but it is highly useful to explore the *Books Online* for a wealth of detail about the application.

We will begin with an example: We are asked by an information consumer to provide the total sales and total cost amounts for the years 1997 and 1998 individually for all USA-based stores (including all products). We are asked, moreover, to provide the information in a two-dimensional grid, with the sales and cost amounts (called measures in our data warehouse) in the rows and the years (1997 and 1998) in the columns.



1. Type the following query into the Query pane:

   ```
   --MDX01-1:  Basic Query
   SELECT
   {[Time].[1997],[Time].[1998]}ON COLUMNS,
   {[Measures].[Warehouse Sales],[Measures].[Warehouse Cost]}  ON ROWS
   FROM Warehouse
   WHERE  ([Store].[All Stores].[USA])
   ```

The diagram below labels the various parts of the query:

![null](https://www.databasejournal.com/img/DJ_MDX01-003.jpg)
**Illustration 3: Labeled Parts of a Basic MDX Query**

The following general discussion items apply to the syntax above, and to MDX queries in general:

- The top line of the query is a comment. We will discuss comments later in the series, but suffice it to say for now that the two dashes (--) represent one of three typical ways to place a comment in MDX syntax, so that it is ignored when the MDX is parsed.

  I introduce this at the present stage because I like to "label" queries in this way as I create them, so as to make them easy to identify for reuse or review. This is particularly handy when using the Sample Application, because the application displays the initial characters of the query in the dropdown selector (labeled "Query:") to the right of the database ("DB:") selector in the toolbar. Selection of a given query from a query file is easy, therefore, given the use of intuitive names/descriptions in the top line of the syntax.

- The cube that is targeted by the query (the query scope) appears in the FROM clause of the query. The FROM clause in MDX works much as it does in SQL (Structured Query Language), where it stipulates the tables used as sources for the query.

- The query syntax also uses other keywords that are common in SQL, such as SELECT and WHERE. Even though there are apparent similarities in the two languages, there are also significant differences. A prominent difference is that the output of an MDX query, which uses a cube as a data source, isanother cube, whereas the output of an SQL query (which uses a columnar table as a source) is typically *columnar*

  It is important to realize that MDX's cube output allows us to place any dimension from the ***source\* cube** onto any **axis** of the query's ***result\* cube**. Many axes can exist, and it is often better to think in terms of "axes" than in "dimensions" (as is quite common among both developers and information consumers) when designing an MDX query. This is for two main reasons: The "axes" concept allows for distinction between the *source dimensions* and the apparent *result cube dimensions*, which may be very different indeed. Another reason is that a given axis can contain a number of cube dimensions in *combination*. Axis references are therefore more precise, and less subject to misinterpretation.

- A query has one or more axes. The query above has two. (The first three axes that are found in MDX queries are known as rows, columns and pages.) We stipulate the axes above through our use of the "columns" and "rows" specifications. Keep in mind that columns always come before rows, and rows always precede pages, within the query.

- Curled brackets "{}" are used in MDX to represent a set of members of a dimension or group of dimensions. The query above has one dimension each on the two query axes. The dimensions that appear are the *Measures* and *Time* dimensions.

- We can display more than one dimension on a result axis. When we do this, an "intersection" occurs, in effect, and each cell appearing in the associated axis relates to the *combination* of a member from each of the indicated dimensions. When more than one dimension is mapped onto an axis, the axis is said to consist of "tuples," containing the members of each of the mapped dimensions.

- Dimensions that are not specified within the axes of a query will have members specified by default; we can also stipulate such members in the WHERE clause, as shown in our query above.

Click the Run Query button (the button sporting the green, arrowhead-shaped icon -- a tool tip will alight when the cursor is placed upon the button to positively identify it for us).

We see the results below, which appear as soon as Analysis Services fills the cells that it determines to be specified by the query.

![null](https://www.databasejournal.com/img/DJ_MDX01-004.jpg)
**Illustration 4: The Initial Query Results**

1. Save the query by selecting File -> Save As and call the file MDX01-1, as shown in the illustration below.

![null](https://www.databasejournal.com/img/DJ_MDX01-005.jpg)
**Illustration 5: Saving the MDX Query via the Save As Dialog**

>  **Note:** I typically prefer to save files to a context-oriented directory/folder (for example a folder I have created for a client for whom I am writing MDX queries as a part of an engagement, or for a presentation I am assembling). This is obviously a point of personal taste; the objective is simply to keep track of where the queries *are*, so that we can find them in time of need. Much rewriting and confusion between altered versions can be avoided by storing the queries in a logical system of some sort to keep organized. My favorite way to do this is to create a database within which to store the query strings, together with descriptions, author and keyword information, along with date time data, "version" information, and other specifics, if applicable.

Let's create another query to conclude this introductory session. This time, let's say that information consumers have asked for a comparison between the total US warehouse sales for the first and second quarters of 1997. We will again create a query against our Warehouse cube to generate this information.

Click the New Query button (depicted in the illustration below).

![null](https://www.databasejournal.com/img/DJ_MDX01-005.jpg)
**Illustration 6: The New Query Button**
We might also have selected File -> New from the top menu.Type the following query into the Query pane:

```Sql
--MDX01-2:  Basic Query 2 
SELECT {[Time].[1997].[Q1],[Time].[1997].[Q2]}ON COLUMNS, 
{[Warehouse].[All Warehouses].[USA]}  ON ROWS
FROM Warehouse 
WHERE  ([Measures].[Warehouse Sales]) 
```

Because we have specified the Warehouse Sales measure in the WHERE statement, we have made it the **slicer dimension**. The slicer shows that we have picked only the Warehouse Sales measure from the measures dimension. We will work with slicer dimensions, as well as with the other components of the simple queries we have examined in this lesson (and far more), as we progress through the *MDX Essentials* series.Click Query on the top menu, then select Run, as shown below:
![null](https://www.databasejournal.com/img/DJ_MDX01-007.jpg)
**Illustration 7: Select Run from the Query Menu**
Alternatively, F5 or the Run Query button might be selected for the same effect.We see the results below, which appear as soon as Analysis Services fills the cells specified by the query.
![null](https://www.databasejournal.com/img/DJ_MDX01-008.jpg)
**Illustration 8: The Query Results**
Save the query as MDX01-2.Exit the Sample Application.Our intent with the above examples is to begin our exploration of MDX and to provide a first exposure to simple query structure. We will cover the details of the syntax and its arrangement, and a host of other considerations, as we progress through the series.

### Next in Our Series...

With this tutorial article, *MDX at First Glance: Introduction to MDX Essentials*, we began the new **MDX Essentials Series**. Our objective in this lesson was to introduce the MDX query in its simplest form. We took a look at some of the basic keywords, focusing only on simple queries, as a basis upon which to build in later lessons. In this lesson, we began a discussion of the elements of the MDX language that will carry forward as we progress through the series, and then performed practice activities, as we will do throughout the entire MDX Essentials series, to reinforce the concepts we introduce.

We explored the rudiments of MDX queries within their simplest contexts (syntax), and introduced several terms (semantics) that were applicable as they arose. We provided a brief introduction to MDX, and then discussed several basic keywords commonly used in MDX. We examined a breakdown of a simple MDX query into its component parts, comparing and contrasting MDX to SQL where useful. Finally, we discussed other relevant introductory keywords and components throughout the lesson as part of creating and executing basic MDX queries.

In our next lesson, *Structure of the MDX Data Model*, we will introduce the MDX data model, together with numerous of its components. These components will include cubes, dimensions, and several other terms we have already exposed. We will focus on the composition and use of tuples and sets in detail, and provide hands-on exposure to these building blocks. Rules of syntax will be emphasized, and will provide a basis for more complex query building later in the series. Finally, we will step through practice exercises to demonstrate tangible results to reinforce our discussions with examples.