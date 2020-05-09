# The Practical Logical Data Warehouse: A Strategic Plan for a Modern Data Management Solution for Analytics
Adam M. Ronthal, Roxane Edjlali

The concept of the logical data warehouse is gaining market **traction** and acceptance, but data and analytics leaders still struggle with practical implementations. We demonstrate a pragmatic approach to the LDW by leveraging the data management infrastructure model. 

**Key Challenges** 

- Distributed data management platforms are required to address the diverse use cases and types of data that organizations want to consume in their analytics environments.
- The logical data warehouse (LDW) architecture adds a degree of complexity over and above the traditional data warehouse that can be difficult to engage with. 
- LDW penetration remains at less than 20% of its target market, despite being introduced nearly a decade ago. 
- Technology-led approaches to the LDW circumvent the required processes and are more prone to failure.

**Recommendations**

Data and analytics leaders focused on building a data management strategy for analytics should:

- Begin the journey to the LDW by aligning data management solutions for analytics use cases to the data management infrastructure model. 
- Make the LDW truly logical by investing in metadata management solutions early in the LDW initiative as metadata provides a solid foundation for a distributed data management platform and LDW architecture. 
- Align analytics use cases to required skills within the organization and invest in developing skills where there is a deficiency, at the same time mapping roles and skills to their most likely position on the data management infrastructure model .
- Select appropriate technologies as the last step in your LDW plan, and be sure that they are well suited to use cases, roles, skills, and the business contexts in the data management infrastructure model. The recommended starting points for technology alignments using the data management infrastructure model can provide a basis for both technology selection and rationalization of the portfolio.

## Introduction
Despite general acceptance that a distributed approach to data management is the correct approach for a modern analytics environment, we estimate that adoption of LDW architecture is still below 20% in its target market (see "Hype Cycle for Data Management, 2017"). A common theme that serves as a barrier to successful implementations is that organizations are getting caught up in the hype around new technologies and new approaches to data. This results in taking detours down these paths without adequately defining the core use cases and the associated contexts for data management. The resultant mismatch between use cases and technologies generally leads to poor results. In fact, a purely technology-led approach to the LDW will have a far higher failure rate than a use-case and requirements-led approach.

Inherent in the LDW architecture is the recognition that a single data persistence tier is generally inadequate when trying to meet the increased data and analytics demands that most organizations are seeing. As a result, analytics-savvy organizations are seeking to augment (not replace) their traditional relational data warehouse environments with nonrelational technologies. These might include Hadoop, SQL-accessible cloud-based object stores, flexible deployment models, data virtualization technologies, and the separation of storage and compute, as well as alternate use cases for relational technologies. Equally, many organizations using Hadoop, cloud object stores, and other nonrelational-based technologies are realizing the need for a traditional, structured data warehouse. These technologies are not interchangeable, but complementary, and support different use cases to solve different — but overlapping — sets of problems.

Reconciling how these different use cases and their requirements align with best practices, skills, technologies and governance requirements has become the latest dilemma facing data and analytics leaders — especially as they engage with both connections to and collections of data (see "Modern Data Management Requires a Balance Between Collecting Data and Connecting to Data").

Resolving this **==dilemma==** requires a new way of thinking about how data is managed across various infrastructures. This is where the data management infrastructure model comes into play (see "Solve Your Data Challenges with the Data Management Infrastructure Model"). It represents how different infrastructure components align to specific types of use cases, and how to evolve those use cases so that data and desired outcomes are better understood.

The data management infrastructure model aligns to the key use cases in Gartner's research into data management solutions for analytics. It can be used as a foundation to convey other ideas as well, like data curation, governance, data virtualization, self-service data preparation, business intelligence and others.

## Analysis
### Align Data Management Solutions for Analytics Use Cases to the Data Management Infrastructure Model

The data management infrastructure model encapsulates the LDW concept (which is also one of the defined use cases in the "Magic Quadrant for Data Management Solutions for Analytics" and the companion Critical Capabilities research). Different sectors also align to other defined use cases like the traditional data warehouse, the context-independent data warehouse and the real-time data warehouse. By placing these use cases within the defined axes of known and unknown data and questions (see Figure 1), we can expand its reach and applicability beyond core data persistence technologies. We can also use it to describe technology and processes across the data and analytics landscape.

> Figure 1

While the data management infrastructure model provides nice clean boundaries, in practice the use cases do not always fall entirely within a defined box. For example, while the data lake grounds itself firmly in the realm of unknown data and unknown questions, the irony is that, as we start to do analytics in the data lake, the first thing we do is apply structure. That structure looks a lot like rows, columns and tables, so the data lake begins to encroach upon the realm of the known. The difference, of course, is that the structure is applied after the fact, once the data has already been ingested. Similarly, structured data warehouses can also be used as the basis for ad hoc exploration of structured, known data, and the traditional data warehouse begins to expand its scope into the realm of the unknown — albeit unknown questions only.

Real-time analytics is the extreme endpoint of known data and known questions, and these processes are typically pushed out to the application layer. This is based on models that are developed in the top half of the model (see "Delivering Digital Business Value Using Practical Hybrid Transactional/Analytical Processing"). These models may persist results in a data lake, a traditional structured environment, a specialized data store (such as a time-series DBMS) or an in-memory hybrid transactional/analytical processing (HTAP)-enabled DBMS.

The context-independent data warehouse is the domain of data science exploration, and aligns to the top half of the data management infrastructure model.

It encompasses:
7. The exploration of unknown data aspects: Where we have neither an understanding of the value or business context of our data, nor a solid understanding of the questions we want or can ask of the data.
8. The exploration of new questions on known data: This last point is of interest as it not only builds on the relational, well-structured foundation of the relational data warehouse, but also explores new questions that were not initially planned or optimized for.

This is where technologies like in-DBMS analytics may come into play, as well as automated tuning and query optimization techniques in modern RDBMSs and in-memory DBMS approaches. Both of these reduce the need for explicit performance tuning crutches like indexes and partitioning. Additionally, the context-independent data warehouse use case may not yet have well-defined metadata or governance processes that support the operational use of data.

Finally, the whole data management infrastructure model — with the data management solutions of analytics use cases overlaid — is a graphical representation of the LDW. We are unwilling to accept the constraints of a single use case, single data persistence technology, single end-user population, or single service-level agreement. Rather, we embrace diverse data types, diverse end-user populations, diverse business contexts for our data, and diverse technologies, all in support of broad, deep and diverse use cases, and logically integrated into a single system.

> Figure 2

### Make the Logical Data Warehouse Truly Logical
The data management infrastructure model framework may appear to overlay use cases and uses of data in a static way, but data that was unknown one day becomes known the next day once savvy users discover how use it meaningfully. Allowing users to "tag" the data as they understand more about its context allows a type of metadata derived from **==crowdsourcing==**, and may increase the quality, and contextual understanding of data over time. Data that is known can be used by anyone regardless of the type of question being asked. Finally, use cases may have changing physical service levels. An algorithm developed on data at rest by data scientists may need to be run in production using real-time data. Data can also flow in the other direction. For example, aging cooler data that is being accrued for long-term trend analysis or audit purposes may be stored more economically in the data lake. The logical data warehouse requires the ability to adapt to all of these changes as they occur over time. This involves metadata management. 

Metadata management includes:
- **Managing data semantics and semantic variations of data over time**: For example, when data changes from being unknown to known, the semantics of the data evolves and is enriched. These capabilities require cataloging of the data participating in the logical data warehouse, but also lineage and impact analysis to understand where the data is coming from, how it has been used, and who uses it.
- **Tracking the use of data through auditing and statistics**: Monitoring changes in data usage allows the detection of changes in consumption patterns and the ability to adapt to them. For example, an increase in concurrency of access or query performance degradation can indicate that the user audience consuming the data has evolved, or that there is increased interest in the data. Data that was once used by only a few analysts is now being used by all. As a result, doing a federated query may no longer meet the performance requirements, and opting for physical data movement to colocate the data may be more suitable.

> Figure 3

Monitoring these changes constitutes the heart of the logical data warehouse and associated practice. This is how LDW practitioners make the implementation decisions and evolve the LDW over time. Moving from unknown data to known data may require defining a compromise representation of the data (see Note 2) so that the data can be unambiguously consumed by everyone (see "Efficiently Evolving Data From the Data Lake to the Data Warehouse").

### Map the Intersections of Analytics Use Cases and Align Them to User Skills
Different analytics use cases align to different user skills — not all of which will be present in every environment (see "Organizing Your Teams for Modern Data and Analytics Deployment").

Different use cases and contexts for data align with different skills levels for interacting with that data (see Note 1):

- **Casual users**: Interact primarily through dashboards and other defined reporting interfaces.
- **Business analysts**: Create new reports and drill down into greater detail on existing reports.
- **Egineers or miners**: Understand data sources and how they relate to business processes, and are skilled at the operations processes that ready data for analytic use.
- **Data scientists**: Engage with raw data, modeling, theory, statistical algorithms and build new context and new semantics for data.

While many associate the data scientist role exclusively with the data lake as a data science exploration platform, there is frequently a need to do data science on known data. For example, sales forecasting, customer churn analysis, and fraud detection all use sophisticated algorithms that run primarily on well-known, structured data. Thus, we seem the data scientist role span both known and unknown data on the top half of the data management infrastructure model.

And while data scientists love to build predictive models, they generally have little interest in operationalizing them. Data engineers and advanced analysts bridge the gap between data science and the operational models consumed by casual users.

Different user skills and use cases also require different optimization targets. These are well represented by the different service-level agreements or business contexts of data for data management solutions of analytics that were first introduced in "Avoid a Big Data Warehouse Mistake by Evolving to the Logical Data Warehouse Now."

Essentially, there are three defined levels of expectation for users engaging with analytics use cases:

- **Compromise**: Using repositories to deliver high-performance, predictable, prequalified and (possibly) summarized data to the least-data-savvy analytics users will remain a significant demand. Data is known, and the questions we are asking of it are known.
- **Contender**: These are contending models of the data, contending use cases, and often vary from one community of users to another (or even within a single community). As such, they are not able to "compromise." They are also contenders in the sense that they may be new interpretations of the data, for example, enabled by data virtualization. Either data or the business questions we are asking are unknown. This is where we determine which models are viable.
- **Candidate**: Users with advanced knowledge of data sourcing, overall data architecture and business processes often seek data that is as near to its native format and structure as possible. Data engineers and scientists actually develop candidate structures and semantics for reading the data at hand. This is the realm of data exploration and discovery. Once the context, use of the data and questions are established, they may become contenders, and ultimately compromise models once they are broadly operationalized. Both data (sources and use) and business questions are unknown.

For more details on these service levels, see Note 1.

Known data and known questions align to the compromise context. We know what problems we are solving and, as a result, we can optimize for performance and cost. Anything that does not meet this goal is generally discarded or ignored — thus the compromise.

On the other end of the spectrum — unknown data and unknown questions — we optimize for semantic flexibility in support of exploratory use of data. We do not yet know the problems we are trying to solve, and want relatively free access to explore the data without unnecessary constraints. This is where we build our candidate contexts for data.

The remaining context — contender — falls into the other scenarios where we have either known data or known questions, but not both. In the bottom right corner of the model (unknown data, known questions) we have an understanding of the questions we are asking, but have not yet determined the right data that will support and answer those questions. Therefore, we try out different views, perhaps leveraging data virtualization technology to build a semantic access layer "on the fly." The goal may be to get to known data and known questions, where we can optimize explicitly for the answers (if the use case and model is deemed to be of sufficient value to operationalize and optimize). Not every exploration will result in a progression to contender or compromise. In the top left corner of the model (known data, unknown questions), we are exploring new questions and capabilities that can be supported by our well-known data.

Using the data management infrastructure model, it is straightforward to align these use cases, skills and contexts for data to gain a sound understanding of the parameters of data management cases that they engage with. It will also expose where gaps exist in our current skill sets that may require internal development or hiring to meet specific needs

> Figure 4.

### Select Appropriate Technologies to Meet the Required Use Cases

Once the use cases, end-user roles and consumers, and the SLAs have all been defined, it is time to look at the technologies that can be deployed to meet these diverse requirements. Note that not all environments will require implementation of all four boxes of the model, but the overall big picture should be kept in mind as a strategic vision and target for a complete, mature data management solution for analytics. Organizations should define long-term strategic goals — as well as shorter-term tactical ones — to address immediate needs. The LDW architecture allows data and analytics leaders to do exactly that, without sacrificing an overall unified view of, and access to, the various data sources.

A possible alignment of technologies is shown in Figure 5.

> Figure 5.

Remember that this alignment is not rigid, and that the various technologies listed can manifest themselves in any of the four boxes of the data management infrastructure model, and many of the defined use cases. For example, while we may introduce data virtualization technology in the "establishing value" box (unknown data, known questions), we can leverage it across the LDW encapsulated in the data management infrastructure model. Similarly, while a traditional relational data warehouse may start in the "foundational core" box (known data, known questions), it could also be used in data science exploratory activities on the top half of the data management infrastructure model. The technologies listed are suggested starting points and will likely evolve in response to use-case applicability, user demands, and SLA requirements.

## Gartner Recommended Reading

### Evidence
The findings in this document draw on:

- An online survey of Gartner's Research Circle members (that is, Gartner's managed community of IT and business leaders representing broad levels of experience, wide geography and diverse vertical industries) was conducted in March 2017:
  - The survey was conducted by Gartner's primary research team in conjunction with subject area expert analysts regarding practices and adoption of data warehousing and LDWs.
  - The survey included standard questionnaire-style delivery and gamification concepts to obtain alternative perspectives, delivered alongside a complementary research thodology (n = 175 completions achieved). 
  - Gartner analyst inquiries relative to data management, analytics, data warehousing, data integration and data management solutions for analytics topics were reviewed for additional inputs and perspectives.
- Big data adoption survey conducted from 23 May through 26 June 2017 among Gartner Research Circle members (a Gartner-managed panel composed of IT and business leaders) as well as an external sample source. In total, 196 respondents completed the survey (92 Gartner Research Circle/104 external sample). The survey was developed collaboratively by a team of Gartner analysts, and was reviewed, tested and administered by Gartner's Research Data and Analytics team
- Data from Gartner's client inquiry service on topics such as operational DBMSs, data warehousing and data management solutions for analytics, DBMS cloud deployments and data integration.
- We also draw on the customer reference survey from "Magic Quadrant for Data Management Solutions for Analytics."
- Insights gathered during customer inquiries and vendor briefings during the past 12 months.

### Note 1 Understanding Different User Skills Levels Within the Organization
- "Casual" users (apprentices): Casual users/apprentices use canned queries and reports, or utilize prebuilt drill-downs and filters. They generally utilize established key performance indicators (KPIs) and metrics that support the KPIs. They export data but generally use it as is, within a more list-based approach. Casual users are the least demanding of the new roles, often exhibiting 1,000 users for every one data scientist, or for up to 90 business analysts.
- Business analysts (journeymen): Analysts will utilize existing reports and canned queries as little more than starting positions for their work. They will most definitely utilize extracts based on existing queries. Their usage pattern includes frequently creating cross-references of data and even inference values to derive missing data points. Generally, they understand business processes very well, but often will attempt analysis that exceeds their understanding of data and even the statistics they attempt to utilize. These users are the most challenging group for data and analytics leaders. For every five data engineers or single data scientist, there exists approximately 90 business analysts.
- Engineers or miners ("master" level): Data engineers understand sourcing; that is, the systems and the business processes that populate those systems with data. They tend to keep in mind mental models of various data assets and business process precedent/dependency relationships whenever working through their analysis. Engineers frequently develop highly accurate inference and derived reference values. They know the limitations of the data they use and their own skills, and rarely overstep either. Engineers are actually rare (many business analysts believe they are engineers), usually about five for every 1,000 casual users.
- Data scientists: Not only the data scientist, but also data-science-related roles, understand how abstract data is and quickly embrace creating their own abstractions to represent analytic outcomes. They embrace mathematics and create highly interpretive models that imply new context and new semantics for data. There is usually only one data scientist for every 1,100 users in other categories.

### Note 2 Understanding Different Data Management Service-Level Requirements (Contexts) for Analytic Use Cases
**Compromise**: Using repositories to deliver high performance, predictable, prequalified and possibly summarized data to the least-data-savvy analytics users will remain a significant demand. These repositories are compromise data models — they compromise on sourcing, compromise on data governance, quality and even more. The repositories do not necessarily have to be a singular data store, but access across many stores should be facilitated with some type of standardized access tier, language or shared infrastructure. Remember that casual/apprentice users can include your consumer and customer bases doing simple reports on your portal. All analysts (even data scientists) will use this part of the solution and should have access and provide input to its design. This is easily deployed using a variety of Mode 1 approaches.

==**Contender**==: Virtualized data access tiers that present views or cached data are popular with business analysts who understand business process, but are not quite comfortable with data architecture and infrastructure issues. Or who simply need the data in a new form to enable a new piece of analysis. These are contending models of the data, contending use cases, and often vary from one community of users to another or even within a single community. As such, they are not able to "compromise." Most casual users will not venture into this service level. Virtualization is not the only solution available (see "Market Guide for Data Virtualization"). Temporary data marts, where data is put into a new form are another simple and historical example.

**Candidate**: Users with advanced knowledge of data sourcing, overall data architecture and business processes often seek data as near to native format and structure as possible. Data engineers and scientists actually develop candidate structures and semantics for even reading the data at hand — even before they build their analysis models and sometimes changing the structure of the data as a result of those analytic models. Data engineers and data scientists utilize all three service levels equally, but here they often prefer to use their own platforms and languages, and your infrastructure and organizational structure must support that. As a result, distributed processing solutions (search or Hadoop, for example) are popular because these solutions allow the users to restructure data with regard to form, but also integrity (or not) and even temporal/event relationships. Such environments also allow users to load data without needing to define its form upfront. Candidate service levels are clearly Mode 2 approaches. Gartner,