English | [简体中文](README.zh-CN.md)

<p align="center">
    <img src="https://img.shields.io/badge/SqlToy-6.0.2-blue" alt="version">
    <a target="_blank" href="LICENSE"><img src="https://img.shields.io/:license-Apache%202.0-blue.svg"></a>
    <a target="_blank" href="https://github.com/sagframe/sagacity-sqltoy"><img src="https://img.shields.io/github/stars/sagframe/sagacity-sqltoy.svg?style=social"/></a>
    <a target="_blank" href="https://gitee.com/sagacity/sagacity-sqltoy"><img src="https://gitee.com/sagacity/sagacity-sqltoy/badge/star.svg?theme=white" /></a>
    <a target="_blank" href="https://github.com/sagframe/sagacity-sqltoy/releases"><img src="https://img.shields.io/github/v/release/sagframe/sagacity-sqltoy?logo=github"></a>
    <a href="https://mvnrepository.com/artifact/com.sagframe/sagacity-sqltoy">
        <img alt="maven" src="https://img.shields.io/maven-central/v/com.sagframe/sagacity-sqltoy?style=flat-square">
    </a>
</p>

# SqlToy ORM

SqlToy is a fusion of JPA and ultra-powerful querying — the distilled and shared experience of real-world projects of many kinds: simple business apps, large SaaS-based multi-tenant ERPs, and big data analytics. Starting from the best dynamic SQL authoring model, it pioneered high-value features such as Cache Translate, Pagination Optimize, Fast Pagination, and cross-database SQL function auto-adaptation!

## 📚 Documentation & Resources

| Resource | Link |
| --- | --- |
| 📖 Online documentation (recommended) | <https://sagframe.github.io/sqltoy-docs/> |
| 📄 Complete configuration of sql queries in xml | [Online docs · Dynamic SQL specification](https://sagframe.github.io/sqltoy-docs/#/query/dynamic_sql) |
| 📄 Detailed manual (Word) | `docs/睿智平台SqlToy5.6使用手册.doc` (Word manual, in Chinese; for 6.0 changes see the [Upgrade Guide](https://sagframe.github.io/sqltoy-docs/#/introduction/upgrade_6.0)) |
| 🚀 Quick integration demo | <https://gitee.com/sagacity/sqltoy-helloworld> |
| 🚀 Feature demo (quickstart) | <https://github.com/sagframe/sqltoy-quickstart> |
| 🚀 Solon demo | <https://github.com/CoCoTeaNet/sqltoy-solon-demo> |
| 🚀 POJO/DTO strict layering demo | <https://github.com/sagframe/sqltoy-strict> |
| 🚀 Database & table sharding demo | <https://github.com/sagframe/sqltoy-showcase/tree/master/trunk/sqltoy-sharding> |
| 🚀 Multi-datasource demo | <https://gitee.com/sagacity/sqltoy-showcase/tree/master/trunk/sqltoy-dynamic-datasource> |
| 🚀 NoSQL demo (mongo/es) | <https://github.com/sagframe/sqltoy-showcase/tree/master/trunk/sqltoy-nosql> |
| 🚀 xml configuration demo | <https://github.com/sagframe/sqltoy-showcase/tree/master/trunk/sqltoy-showcase> |
| 🔌 quickvo code generator (official) | <https://gitee.com/sagacity/maven-quickvo-plugin> |
| 🔌 sqltoy-plus (Lambda enhancements) | <https://gitee.com/gzghde/sqltoy-plus> |
| 🔌 Admin system scaffold | <https://github.com/CoCoTeaNet/CyreneAdmin> |
| 🔌 IDEA plugin | <https://github.com/imyuyu/sqltoy-idea-plugin> |

## 💬 Community

QQ group: **531812227** | [Gitee](https://gitee.com/sagacity/sagacity-sqltoy) | [GitHub](https://github.com/sagframe/sagacity-sqltoy) | [GitCode](https://gitcode.com/sqltoy/sagacity-sqltoy)

## 📦 Latest Version 6.0.2

```xml
<dependency>
	<groupId>com.sagframe</groupId>
	<artifactId>sagacity-sqltoy-spring-starter</artifactId>
	<!-- solon adapter version <artifactId>sagacity-sqltoy-solon-plugin</artifactId> -->
	<!-- traditional Spring project <artifactId>sagacity-sqltoy-spring</artifactId> -->
	<!-- plain sqltoy only <artifactId>sagacity-sqltoy</artifactId> -->
	<!-- for jdk8 the corresponding version is: 5.6.95.jre8 (final version) -->
	<version>6.0.2</version>
</dependency>
```

> [!NOTE]
> For the changes when upgrading from 5.6.x to 6.0.x (the `parallQuery→parallelQuery` rename, the changed `DBProfile` signature of the connection callback, package structure adjustments, etc.), see the [6.0 Upgrade Guide](https://sagframe.github.io/sqltoy-docs/#/introduction/upgrade_6.0).

## ✨ Features

| Category | Core Capabilities |
| --- | --- |
| Object operations | JPA-style CRUD, elastic update, updateFetch / updateSaveFetch, cascades, query hierarchy packaging, tree-table routing |
| SQL query | Dynamic SQL (`#[]` + filters), Cache Translate (incl. extra-large FIFO dynamic cache), the most powerful pagination (count optimization / cached / fast / parallel), parallel query, stored procedures, streaming query |
| Data analysis | Row-to-column / column-to-row pivot, grouped totals, year-over-year / period-over-period comparison, tree-sorted aggregation, group concatenation, date and number formatting |
| Cross-database | 24 dialects (incl. SAP HANA), automatic function replacement, multi-dialect sqlId, multi-database adaptation verification |
| Enterprise-grade | Database & table sharding, multi-tenancy, data permission and privilege-escalation validation, data masking with encryption/decryption, data version control, SQL interception, slow SQL handling |
| NoSQL | Elasticsearch (sql / json dual mode), MongoDB (query / aggregation + Cache Translate) |
| Engineering | quickvo code generation, autoDDL automatic table creation, debug hot reload, GraalVM AOT, Spring Boot / Spring / Solon / plain Java |

<details>
<summary><b>Feature details</b></summary>

### JPA part
* JPA-like object-oriented CRUD, with object-level cascade loading, insert and update
* Supports generating DDL from POJOs and creating tables directly in the database
* Enhanced update operation with elastic field modification: unlike hibernate's load-then-modify approach, the modification is done in a single database interaction, ensuring data accuracy in high-concurrency scenarios
* Improved cascade modification, offering the option to delete (or invalidate) first and then overwrite
* Added updateFetch and updateSaveFetch for strongly transactional, high-concurrency scenarios such as inventory and capital ledgers: one database interaction performs a locking query, inserts if absent or updates if present, and returns the modified result
* Added tree-structure packaging, making recursive queries over tree-structured data uniform across different databases
* Supports database & table sharding, multiple primary-key strategies (additionally supports Redis-based generation of business keys with specific rules), encrypted storage, and data version validation
* Provides common-field auto-assignment (created-by, updated-by, create time, update time, tenant), extended type handling, and more
* Provides unified multi-tenant filtering and assignment, plus data-permission parameter injection and privilege-escalation validation

### Query part
* Extremely intuitive sql authoring: easy to migrate in both directions between database clients and code, and easy to change and maintain later
* Supports Cache Translate and reverse cache matching by key as a replacement for like fuzzy queries
* Provides cross-database support: automatic conversion and adaptation of functions across different databases, multi-dialect sql automatically matched to the actual environment, and synchronized testing across multiple databases — greatly improving productization
* Provides query functions for special scenarios such as fetching top records and random records
* Provides the most powerful pagination mechanism: 1) automatic optimization of the count statement; 2) cache-based Pagination Optimize that avoids executing the count query every time; 3) the distinctive Fast Pagination (@fast); 4) parallel pagination
* Provides database & table sharding
* Provides algorithms of great value in management-style projects, naturally integrated: grouped totals, row/column pivot (row-to-column, column-to-row), year-over-year / period-over-period comparison, tree sorting, and tree aggregation
* Provides query-based hierarchy packaging of data structures
* Provides many auxiliary features: data masking, formatting, condition-parameter preprocessing, and more

</details>

### Supported Databases
* Common databases: mysql, oracle, db2, postgresql, sqlserver, dm, kingbase, hana, sqlite, h2, oceanBase, polardb, gaussdb, tidb, oscar (Shentong), HighGo, mogdb, vastbase, stardb
* Distributed OLAP databases: clickhouse, doris, StarRocks, greenplum, impala(kudu), TDengine
* Supports elasticsearch and mongodb
* Any database query based on sql and jdbc

## 🚀 Quick Start

Three steps: **1. Generate POJOs with [quickvo](https://gitee.com/sagacity/maven-quickvo-plugin); 2. Complete the yml configuration; 3. Inject LightDao in your Service (no need to write custom Dao classes)**

```java
@Autowired
LightDao lightDao;

StaffInfoVO staffInfo = new StaffInfoVO();
// save
lightDao.save(staffInfo);
// update (elastic: null fields are automatically skipped)
lightDao.update(staffInfo, "photo");
// delete
lightDao.delete(new StaffInfoVO("S2007"));
```

It is recommended to start with [sqltoy-helloworld](https://gitee.com/sagacity/sqltoy-helloworld) or [sqltoy-quickstart](https://github.com/sagframe/sqltoy-quickstart) and learn by reading their readme.md.

## 🔍 Feature Details

### 1. JPA-like Object Operations with Targeted Enhancements (incl. Cascades)

```java
// Generate the POJOs from the database with the quickvo tool, then inject sqltoy's built-in LightDao to complete all operations
@Autowired
LightDao lightDao;

// uniqueness validation
lightDao.isUnique(staffInfo, "staffCode");
// force-update the photo property; other null properties are automatically skipped
lightDao.update(staffInfo, "photo");
// update only the specified fields
lightDao.update().updateFields("name","status").one(entity);
lightDao.update().updateFields("name","status").many(entities);
// deep update: all fields are updated regardless of null
lightDao.updateDeeply(staffInfo);
// batch save or update
lightDao.saveOrUpdateAll(staffList);
// batch save
lightDao.saveAll(staffList);
// parallel save
lightDao.save().parallelConfig(ParallelConfig.create().groupSize(5000).maxThreads(10)).many(entities);
// batch load by primary key
lightDao.loadByIds(StaffInfoVO.class,"S2007");
```

### 2. Object Queries Directly in Code

The uniform rule in sqltoy is that in code you can pass either the sql directly or the sqlId from the corresponding xml file.

> [!NOTE]
> The `findBySql` family of methods belongs to the `SqlToyLazyDao` interface; the equivalent form on `LightDao` is `find(sql, entity, resultType)`.

```java
/**
 * @todo Pass parameters via an object, simplifying the paramName[],paramValue[] parameter-passing style (SqlToyLazyDao interface; the equivalent LightDao form is find(sql, entity, resultType))
 */
public <T extends Serializable> List<T> findBySql(final String sqlOrSqlId, final T entity);
```

Single-table query by object, with Cache Translate:

```java
public Page<StaffInfoVO> findStaff(Page<StaffInfoVO> pageModel, StaffInfoVO staffInfoVO) {
     // sql can be written directly in code; complex sql is recommended to be defined in xml
     // in single-table entity queries, sql fields can be written as java class property names
     return findPageEntity(pageModel,StaffInfoVO.class, EntityQuery.create()
	.where("#[staffName like :staffName]#[and createTime>=:beginDate]#[and createTime<=:endDate]")
	.values(staffInfoVO)
	// the dictionary cache must set cacheType
	// single-table object queries must set keyColumn to form the select keyColumn as column pattern
	.translates(new Translate("dictKeyName").setColumn("sexTypeName").setCacheType("SEX_TYPE")
         		.setKeyColumn("sexType"))
	.translates(new Translate("organIdName").setColumn("organName").setKeyColumn("organId")));
}
```

Update or delete after an object-style query:

```java
// update records by condition
public Long updateByQuery() {
     return lightDao.updateByQuery(StaffInfoVO.class,
		EntityUpdate.create().set("createBy", "S0001")
                     .where("staffName like ?").values("Zhang"));
}

// delete records by condition
lightDao.deleteByQuery(StaffInfoVO.class, EntityQuery.create().where("status=?").values(0));
```

### 3. Extremely Plain SQL Authoring (Discovery and Abstraction of the Essential Rules)

* sqltoy's style (the intent of the sql is obvious at a glance, later changes and adjustments are very convenient, and you can copy it into a database client and execute it with minor tweaks)
* The principle behind sqltoy condition assembly is simple: e.g. `#[order_id=:orderId]` equals if(:orderId<>null) sql.append(order_id=:orderId); as soon as one parameter inside `#[]` is null, the fragment is removed
* Supports multi-level nesting: e.g. `#[and t.order_id=:orderId #[and t.order_type=:orderType]]`
* Condition evaluation retains the highly flexible `#[@if(:param>=xx ||:param<=xx1) sql statement]` @if() pattern, providing a master key for special complex scenarios

sqltoy's style:

```xml
<!-- 1. Condition-value handling is separated from the actual sql -->
<!-- 2. Condition values are preprocessed beforehand via generic methods defined in filters (most of them need no extra processing) -->
<sql id="show_case">
<filters>
   <!-- If the parameter statusAry contains -1 (meaning all), statusAry is set to null and excluded from the condition search -->
   <eq params="statusAry" value="-1" />
</filters>
<value><![CDATA[
	select 	*
	from sqltoy_device_order_info t 
	where #[t.status in (:statusAry)]
	#[and t.ORDER_ID=:orderId]
	#[and t.ORGAN_ID in (:authedOrganIds)]
	#[and t.STAFF_ID in (:staffIds)]
	#[and t.TRANS_DATE>=:beginAndEndDate[0]]
	#[and t.TRANS_DATE<:beginAndEndDate[1]]    
	]]>
</value>
</sql>
```

MyBatis equivalent for the same functionality (for comparison):

```xml
<select id="show_case" resultMap="BaseResultMap">
 select *
 from sqltoy_device_order_info t 
 <where>
     <if test="statusAry!=null">
	and t.status in
	<foreach collection="status" item="statusAry" separator="," open="(" close=")">  
            #{status}  
 	</foreach>  
    </if>
    <if test="orderId!=null">
	and t.ORDER_ID=#{orderId}
    </if>
    <if test="authedOrganIds!=null">
	and t.ORGAN_ID in
	<foreach collection="authedOrganIds" item="order_id" separator="," open="(" close=")">  
            #{order_id}  
 	</foreach>  
    </if>
    <if test="staffIds!=null">
	and t.STAFF_ID in
	<foreach collection="staffIds" item="staff_id" separator="," open="(" close=")">  
            #{staff_id}  
 	</foreach>  
    </if>
    <if test="beginDate!=null">
	and t.TRANS_DATE>=#{beginDate}
    </if>
    <if test="endDate!=null">
	and t.TRANS_DATE<#{endDate}
    </if>
</where>
</select>
```

### 4. Natural SQL Injection Protection — Execution Process

```text
Suppose the sql statement is as follows
select 	*
from sqltoy_device_order_info t 
where #[t.ORGAN_ID in (:authedOrganIds)]
      #[and t.TRANS_DATE>=:beginDate]
      #[and t.TRANS_DATE<:endDate]

java invocation:
lightDao.find(sql, MapKit.keys("authedOrganIds","beginDate", "endDate").values(authedOrganIdAry,beginDate,null), DeviceOrderInfoVO.class);

The sql finally executed looks like this:
select 	*
from sqltoy_device_order_info t 
where t.ORDER_ID=?
      and t.ORGAN_ID in (?,?,?)
      and t.TRANS_DATE>=?

Then condition values are set via: pst.set(index,value); there is no concatenating condition values directly into the sql as string fragments
```

### 5. The Most Powerful Pagination

**Pagination highlights**

* 1. Fast Pagination: `@fast()` fetches the single page of data first and then performs the join query, greatly improving speed.
* 2. Pagination Optimize: page-optimize reduces a paginated query from two executions to about 1.3~1.5 (using a cache, the total record count for identical query conditions need not be re-queried within a certain period)
* 3. sqltoy's way of getting the total record count for pagination is not a naive select count(1) from (original sql); it intelligently decides whether it can become: select count(1) from 'the statement after from', and automatically strips the outermost order by
* 4. sqltoy supports parallel query: parallel="true" queries the total record count and the single page of data simultaneously, greatly improving performance
* 5. In extremely special cases sqltoy pagination is optimally handled, e.g.: with t1 as (),t2 as @fast(select * from table1) select * from xxx
For the pagination of such a complex query, sqltoy's count query would be: with t1 as () select count(1) from table1;
and for: with t1 as @fast(select * from table1) select * from t1, the count sql is: select count(1) from table1

**Pagination sql example**

```xml
<!-- Fast Pagination and Pagination Optimize demo -->
<sql id="sqltoy_fastPage">
	<!-- Pagination optimize: when the query conditions are identical, the total record count is cached for a period of time, so it does not have to be queried on every call -->
	<!-- parallel: whether to query the total record count and the single page of data in parallel; when alive-max=1 the cache optimization is disabled -->
	<!-- alive-max: how many total counts for different query conditions can be stored; alive-seconds: how long a cached count for a query condition stays alive (e.g. 120 seconds; past the threshold it is re-queried) -->
	<page-optimize parallel="true" alive-max="100" alive-seconds="120" />
	<value><![CDATA[
	select t1.*,t2.ORGAN_NAME 
	-- @fast() paginates first to take 10 rows (the exact number is determined by pageSize), then joins
	from @fast(select t.*
		   from sqltoy_staff_info t
		   where t.STATUS=1 
		   #[and t.STAFF_NAME like :staffName] 
		   order by t.ENTRY_DATE desc
		) t1 
	left join sqltoy_organ_info t2 on  t1.organ_id=t2.ORGAN_ID
	]]>
	</value>
	<!-- For extremely special cases a custom count-sql can be provided here to achieve ultimate performance optimization -->
	<!-- <count-sql></count-sql> -->
</sql>
```

**Pagination java invocation**

```java
/**
 *  Parameter passing via object mode
 */
public void findPageByEntity() {
	Page pageModel = new Page();
	StaffInfoVO staffVO = new StaffInfoVO();
	// passed as query conditions
	staffVO.setStaffName("Chen");
	// uses the pagination optimizer
	// first call: two queries are executed - the count and the record fetch
	Page result = lightDao.findPage(pageModel, "sqltoy_fastPage", staffVO, StaffInfoVO.class);
	System.err.println(JSON.toJSONString(result));
	// second call: the filter conditions are identical, so the count query is not executed again
	// set to page 2
	pageModel.setPageNo(2);
	result = lightDao.findPage(pageModel, "sqltoy_fastPage", staffVO, StaffInfoVO.class);
	System.err.println(JSON.toJSONString(result));
}
```

### 6. The Cleverest Cache Usage — Eliminating Table JOINs

* 1. Cache translate via `<translate>`: converts codes into names, avoiding JOIN queries, greatly simplifying SQL and improving query efficiency
* 2. Fuzzy matching against cache names via `<cache-arg>`: obtains the exact codes as query criteria, avoiding JOIN and LIKE fuzzy queries
* 3. Ultra-large-scale data scenarios (e.g., platform e-commerce SKUs, over a million entries): FIFO dynamic cache — the cache is not fully loaded; missed keys are dynamically fetched in batches, and only the most frequently used data is kept locally (see [Online Docs · Ultra-Large-Scale Master Data Cache](https://sagframe.github.io/sqltoy-docs/#/translate/sqltoy_FIFO_translate))

```java
// Cache translate via object property annotations
@Translate(cacheName = "dictKeyName", cacheType = "DEVICE_TYPE", keyField = "deviceType")
private String deviceTypeName;

@Translate(cacheName = "staffIdName", keyField = "staffId")
private String staffName;
```

```xml
<sql id="sqltoy_order_search">
	<!-- Cache translate for device type
        cache: the name of the specific cache definition,
        cache-type: generally for data dictionaries, provides a category filter
	columns: the query field names in the sql; multiple fields can be comma-separated for translation
	cache-indexs: the column holding the name in the cache data; if omitted, defaults to the second column (starting from 0, so 1 means the second column),
	      e.g. if the cache data structure is: key, name, fullName, then the third column holds the full name
	-->
	<translate cache="dictKeyName" cache-type="DEVICE_TYPE" columns="deviceTypeName" cache-indexs="1"/>
	<!-- Staff name translation; with the same cache, several fields can be translated at once -->
	<translate cache="staffIdName" columns="staffName,createName" />
	<filters>
			<!-- Reverse cache matching: match out the ids by name for precise queries -->
			<cache-arg cache-name="staffIdName" param="staffName" alias-name="staffIds"/>
	</filters>
	<value>
	<![CDATA[
	select 	ORDER_ID,
		DEVICE_TYPE,
		DEVICE_TYPE deviceTypeName,-- device type name
		STAFF_ID,
		STAFF_ID staffName, -- staff name
		ORGAN_ID,
		CREATE_BY,
		CREATE_BY createName -- creator name
	from sqltoy_device_order_info t 
	where #[t.ORDER_ID=:orderId]
	      #[and t.STAFF_ID in (:staffIds)]
	]]>
	</value>
</sql>
```

### 7. Parallel Query

```java
/**
 * @TODO Runs queries in parallel and returns the collection of results; the List contains one result object per query
 *       paramsMap is the union of all sql condition parameters; each ParallelQuery can also set its own condition parameters
 * @param parallelQueryList Collection of ParallelQuery; setting .page(page) makes it a paginated query
 * @param paramsMap         Map of values for the named parameters in the sql
 */
public <T> List<QueryResult<T>> parallelQuery(List<ParallelQuery> parallelQueryList, Map<String, Object> paramsMap);

// The parallel thread count and max wait duration can be set via ParallelConfig
public <T> List<QueryResult<T>> parallelQuery(List<ParallelQuery> parallelQueryList, Map<String, Object> paramsMap,
		ParallelConfig parallelConfig);
```

Usage example:

```java
// Use parallel query to execute 2 sqls at the same time; the condition parameters are the union of the 2 queries
List<QueryResult<TreeModel>> list = lightDao.parallelQuery(
		Arrays.asList(
		        ParallelQuery.create().sql("webframe_searchAllModuleMenus").resultType(TreeModel.class),
				ParallelQuery.create().sql("webframe_searchAllUserReports").resultType(TreeModel.class)),
		MapKit.keys("userId", "defaultRoles", "deployId", "authObjType")
		      .values(userId, defaultRoles, GlobalConstants.DEPLOY_ID,
					SagacityConstants.TempAuthObjType.GROUP));

// Alternatively, set the parallel thread count and max wait duration via ParallelConfig
List<QueryResult<TreeModel>> list2 = lightDao.parallelQuery(queries, paramsMap,
		ParallelConfig.create().maxThreads(20).maxWaitSeconds(60));
```

> Note: Parallel query targets query scenarios (**do not use it in the middle of transactional operations**); whether it is appropriate to use must be reasonably judged by the user based on the actual scenario.
> 6.0 rename note: `parallQuery`/`ParallQuery` in 5.6.x are renamed to `parallelQuery`/`ParallelQuery` starting from 6.0.

### 8. The Strongest Cross-Database Capability

* 1. Provides hibernate-like object operations, automatically generating the dialect of the corresponding database.
* 2. Provides the most common queries: pagination, top-N, random records, etc., avoiding the different syntax of different databases.
* 3. Provides a standard drill-down query approach for tree-structured tables, replacing recursive queries; one approach fits all databases.
* 4. sqltoy provides a large number of algorithm-based helper implementations, replacing traditional SQL with algorithms to the greatest extent, achieving cross-database compatibility.
* 5. sqltoy provides function replacement, e.g., allowing oracle statements to run on mysql or sqlserver (functions are replaced with mysql functions when the sql is loaded), achieving productization of the code to the greatest extent.

```properties
# Enable sqltoy's built-in default auto-adaptive function conversion
spring.sqltoy.functionConverts=default
# Test other types of databases alongside mysql to verify that the sql adapts to different databases; mainly for productized software
spring.sqltoy.redoDataSources[0]=pgdb
# You can also define custom functions to replace Nvl
# spring.sqltoy.functionConverts=default,com.yourpackage.Nvl
# Enable the framework's built-in Nvl, Instr
# spring.sqltoy.functionConverts=Nvl,Instr
# Enable custom Nvl, Instr
# spring.sqltoy.functionConverts=com.yourpackage.Nvl,com.yourpackage.Instr
```

* 6. With the sqlId+dialect pattern, you can write sql for specific databases; sqltoy retrieves the actual sql to execute based on the database type, in the order:
    dialect_sqlId->sqlId_dialect->sqlId,
	e.g., if the database is mysql and the sqlId called is sqltoy_showcase, then sqltoy_showcase_mysql is actually executed

```xml
<sql id="sqltoy_showcase">
	<value>
	<![CDATA[
	select * from sqltoy_user_log t 
	where t.user_id=:userId 
	]]>
	</value>
</sql>
<!-- sqlId_database dialect (lowercase) -->
<sql id="sqltoy_showcase_mysql">
	<value>
	<![CDATA[
	select * from sqltoy_user_log t 
	where t.user_id=:userId 
	]]>
	</value>
</sql>
```

### 9. Pivot, Group Summary, YoY & MoM, Tree Sort & Rollup

* Fruit sales record table

Category|Sale Month|Sale Count|Sale Quantity (tons)|Sale Amount (10K CNY)
----|-------|-------|----------|------------
Apple|2019-05|12 | 2000|2400
Apple|2019-04|11 | 1900|2600
Apple|2019-03|13 | 2000|2500
Banana|2019-05|10 | 2000|2000
Banana|2019-04|12 | 2400|2700
Banana|2019-03|13 | 2300|2700

#### 9.1 Pivot (unpivot is also supported)

```xml
<!-- Pivot -->
<sql id="pivot_case">
	<value>
	<![CDATA[
	select t.fruit_name,t.order_month,t.sale_count,t.sale_quantity,t.total_amt 
	from sqltoy_fruit_order t
	order by t.fruit_name ,t.order_month
	]]>
	</value>
	<!-- Pivot: use order_month as the horizontal category headings; rotate the three measures from column sale_count to total_amt into rows -->
	<pivot start-column="sale_count" end-column="total_amt"	group-columns="fruit_name" category-columns="order_month" />
</sql>
```

* Result

<table>
<thead>
	<tr>
	<th rowspan="2">Category</th>
	<th colspan="3">2019-03</th>
	<th colspan="3">2019-04</th>
	<th colspan="3">2019-05</th>
	</tr>
	<tr>
	    <th>Count</th><th>Quantity</th><th>Total Amount</th>
	    <th>Count</th><th>Quantity</th><th>Total Amount</th>
	    <th>Count</th><th>Quantity</th><th>Total Amount</th>
	</tr>
	</thead>
	<tbody>
	<tr>
		<td>Banana</td>
		<td>13</td>
		<td>2300</td>
		<td>2700</td>
		<td>12</td>
		<td>2400</td>
		<td>2700</td>
		<td>10</td>
		<td>2000</td>
		<td>2000</td>
	</tr>
	<tr>
		<td>Apple</td>
		<td>13</td>
		<td>2000</td>
		<td>2500</td>
		<td>11</td>
		<td>1900</td>
		<td>2600</td>
		<td>12</td>
		<td>2000</td>
		<td>2400</td>
	</tr>
	</tbody>
</table>

#### 9.2 Group Summary and Averaging (at any level)

```xml
<sql id="group_summary_case">
	<value>
	<![CDATA[
	select t.fruit_name,t.order_month,t.sale_count,t.sale_quantity,t.total_amt 
	from sqltoy_fruit_order t
	order by t.fruit_name ,t.order_month
	]]>
	</value>
	<!-- reverse: whether to reverse -->	
	<summary sum-columns="sale_count,sale_quantity,total_amt" reverse="true">
		<!-- Keep the hierarchy order from high to low -->
		<global sum-label="Total" label-column="fruit_name" />
		 <!-- order-column: the group sort column (sorts within the same group); order-with-sum: defaults to true; order-way: desc/asc -->
		<group group-column="fruit_name" sum-label="Subtotal" label-column="fruit_name" />
	</summary>
</sql>
```

* Result

Category|Sale Month|Sale Count|Sale Quantity (tons)|Sale Amount (10K CNY)
----|-------|-------|----------|------------
Total|       |   71  |    12600 |14900
Subtotal|       |  36  | 5900   | 7500
Apple|2019-05|12 | 2000|2400
Apple|2019-04|11 | 1900|2600
Apple|2019-03|13 | 2000|2500
Subtotal|       | 35  | 6700|7400
Banana|2019-05|10 | 2000|2000
Banana|2019-04|12 | 2400|2700
Banana|2019-03|13 | 2300|2700

#### 9.3 Pivot First, Then MoM Calculation

```xml
<!-- Demonstration of column-to-column MoM calculation -->
<sql id="cols_relative_case">
	<value>
	<![CDATA[
	select t.fruit_name,t.order_month,t.sale_count,t.sale_amt,t.total_amt 
	from sqltoy_fruit_order t
	order by t.fruit_name ,t.order_month
	]]>
	</value>
	<!-- Rotate the data (pivot): display order_month as columns, with three measures under each month -->
	<pivot start-column="sale_count" end-column="total_amt"	group-columns="fruit_name" category-columns="order_month" />
	<!-- Perform MoM calculation between columns -->
	<cols-chain-relative group-size="3" relative-indexs="1,2" start-column="1" format="#.00%" />
</sql>
```

* Result

<table>
<thead>
	<tr>
	<th rowspan="2" nowrap="nowrap">Category</th>
	<th colspan="5">2019-03</th>
	<th colspan="5">2019-04</th>
	<th colspan="5">2019-05</th>
	</tr>
	<tr>
	    <th nowrap="nowrap">Count</th><th nowrap="nowrap">Quantity</th><th nowrap="nowrap">vs. Last Month</th><th nowrap="nowrap">Total Amount</th><th nowrap="nowrap">vs. Last Month</th>
	    <th nowrap="nowrap">Count</th><th nowrap="nowrap">Quantity</th><th nowrap="nowrap">vs. Last Month</th><th nowrap="nowrap">Total Amount</th><th nowrap="nowrap">vs. Last Month</th>
	    <th nowrap="nowrap">Count</th><th nowrap="nowrap">Quantity</th><th nowrap="nowrap">vs. Last Month</th><th nowrap="nowrap">Total Amount</th><th nowrap="nowrap">vs. Last Month</th>
	</tr>
	</thead>
	<tbody>
	<tr>
		<td>Banana</td>
		<td>13</td>
		<td>2300</td>
		<td></td>
		<td>2700</td>
		<td></td>
		<td>12</td>
		<td>2400</td>
		<td>4.30%</td>
		<td>2700</td>
		<td>0.00%</td>
		<td>10</td>
		<td>2000</td>
		<td>-16.70%</td>
		<td>2000</td>
		<td>-26.00%</td>
	</tr>
		<tr>
		<td>Apple</td>
		<td>13</td>
		<td>2000</td>
		<td></td>
		<td>2500</td>
		<td></td>
		<td>11</td>
		<td>1900</td>
		<td>-5.10%</td>
		<td>2600</td>
		<td>4.00%</td>
		<td>12</td>
		<td>2000</td>
		<td>5.20%</td>
		<td>2400</td>
		<td>-7.70%</td>
	</tr>
	</tbody>
</table>

#### 9.4 Tree Sort & Rollup

```xml
<!-- Tree sort and rollup -->
<sql id="treeTable_sort_sum">
	<value>
	<![CDATA[
	select t.area_code,t.pid_area,sale_cnt from sqltoy_area_sales t
	]]>
	</value>
	<!-- Build the parent-child tree hierarchy, roll up the child node values level by level onto the parent nodes, and sort nodes at the same level in descending order -->
	<tree-sort id-column="area_code" pid-column="pid_area"	sum-columns="sale_cnt" level-order-column="sale_cnt" order-way="desc"/>
</sql>
```

* Result

<table>
<thead>
	<tr>
	<th>Region</th>
	<th>Parent Region</th>
	<th>Sales Volume</th>
	</tr>
</thead>
<tbody>
	<tr>
	<td>Shanghai</td>
	<td>China</td>
	<td>300</td>
	</tr>
	<tr>
	<td>&nbsp;&nbsp;&nbsp;&nbsp;Songjiang</td>
	<td>Shanghai</td>
	<td>&nbsp;&nbsp;&nbsp;&nbsp;120</td>
	</tr>
 	<tr>
	<td>&nbsp;&nbsp;&nbsp;&nbsp;Yangpu</td>
	<td>Shanghai</td>
	<td>&nbsp;&nbsp;&nbsp;&nbsp;116</td>
	</tr>
 	<tr>
	<td>&nbsp;&nbsp;&nbsp;&nbsp;Pudong</td>
	<td>Shanghai</td>
	<td>&nbsp;&nbsp;&nbsp;&nbsp;64</td>
	</tr>
	<tr>
	<td>Jiangsu</td>
	<td>China</td>
	<td>270</td>
	</tr>
	<tr>
	<td>&nbsp;&nbsp;&nbsp;&nbsp;Nanjing</td>
	<td>Jiangsu</td>
	<td>&nbsp;&nbsp;&nbsp;&nbsp;110</td>
	</tr>
 	<tr>
	<td>&nbsp;&nbsp;&nbsp;&nbsp;Suzhou</td>
	<td>Jiangsu</td>
	<td>&nbsp;&nbsp;&nbsp;&nbsp;90</td>
	</tr>
 	<tr>
	<td>&nbsp;&nbsp;&nbsp;&nbsp;Wuxi</td>
	<td>Jiangsu</td>
	<td>&nbsp;&nbsp;&nbsp;&nbsp;70</td>
	</tr>
</tbody>
</table>

### 10. Database & Table Sharding

#### 10.1 Query with Database & Table Sharding (database sharding and table sharding strategies can be used together)

```xml
For the sql, see the quickstart project: the com/sqltoy/quickstart/sqltoy-quickstart.sql.xml file
<!-- Database sharding demo -->
<sql id="qstart_db_sharding_case">
	<sharding-datasource strategy="hashDataSource" params="userId" />
	<value>
	<![CDATA[
	select * from sqltoy_user_log t 
	-- userId is the database sharding key and is a mandatory condition
	where t.user_id=:userId 
	#[and t.log_date>=:beginDate]
	#[and t.log_date<=:endDate]
	]]>
	</value>
</sql>

<!-- Table sharding demo -->
<sql id="qstart_sharding_table_case">
	<sharding-table tables="sqltoy_trans_info_15d"	strategy="realHisTable" params="beginDate" />
	<value><![CDATA[
	select * from sqltoy_trans_info_15d t 
	where t.trans_date>=:beginDate
	#[and t.trans_date<=:endDate]
	]]>
	</value>
</sql>
```

#### 10.2 CRUD with Database & Table Sharding (vo objects are auto-generated from the database by the quickvo tool, and custom annotations will not be overwritten)

@Sharding configures the database & table sharding strategies on objects via annotations

For a complete demo, see the sqltoy-sharding sub-project of the demo project [sqltoy-showcase](https://github.com/sagframe/sqltoy-showcase) (strategy bean definitions are in its spring-sqltoy-sharding.xml)

```java
package com.sagframe.sqltoy.showcase.vo;

import org.sagacity.sqltoy.config.annotation.Sharding;
import org.sagacity.sqltoy.config.annotation.SqlToyEntity;
import org.sagacity.sqltoy.config.annotation.Strategy;

import com.sagframe.sqltoy.showcase.vo.base.AbstractStaffInfoVO;

/**
 * db is the database sharding strategy config; table is the table sharding strategy config; they can be configured together or independently
 * the strategy name must match the bean definition name in spring; fields indicates which object field values serve as the decision basis, one or more fields
 * maxConcurrents: optional, the maximum concurrency; maxWaitSeconds: optional, the maximum wait time in seconds
 */
@SqlToyEntity
@Sharding(db = @Strategy(name = "hashBalanceDBSharding", fields = { "staffId" })
// table sharding is similar to database sharding
//,table = @Strategy(name = "hashBalanceDBSharding", fields = { "staffId" })
)
public class StaffInfoVO extends AbstractStaffInfoVO {

	private static final long serialVersionUID = 4609820466201465046L;

	/** default constructor */
	public StaffInfoVO() {
		super();
	}
}
```

# Integration Guide

## See the quickstart under trunk and read its readme.md to get started

```java
package com.sqltoy.quickstart;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ImportResource;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 
 * @project sqltoy-quickstart
 * @description quickstart main application entry point
 * @author zhongxuchen
 */
@SpringBootApplication
@ImportResource("classpath:spring-context.xml")
@EnableTransactionManagement
public class SqlToyApplication {

	public static void main(String[] args) {
		SpringApplication.run(SqlToyApplication.class, args);
	}
}
```

## sqltoy-related configuration in application.properties

```properties
# sqltoy config
spring.sqltoy.sqlResourcesDir=classpath:com/sqltoy/quickstart
spring.sqltoy.translateConfig=classpath:sqltoy-translate.xml
spring.sqltoy.debug=true
#spring.sqltoy.reservedWords=status,sex_type
#dataSourceSelector: org.sagacity.sqltoy.plugins.datasource.impl.DefaultDataSourceSelector
#spring.sqltoy.defaultDataSource=dataSource
spring.sqltoy.unifyFieldsHandler=com.sqltoy.plugins.SqlToyUnifyFieldsHandler
#spring.sqltoy.printSqlTimeoutMillis=200000

```

## The cache-translate config file sqltoy-translate.xml 

```xml
<?xml version="1.0" encoding="UTF-8"?>
<sagacity
	xmlns="https://www.sagframe.com/schema/sqltoy-translate"
	xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
	xsi:schemaLocation="https://www.sagframe.com/schema/sqltoy-translate https://sagframe.github.io/schema/sqltoy-translate.xsd">
	<!-- Caches have a default expiration time of 1 hour, so only the more frequently used caches need timely change detection -->
	<cache-translates>
		<!-- Load the cache via a direct SQL query -->
		<sql-translate cache="dictKeyName"
			datasource="dataSource">
			<sql>
			<![CDATA[
			select t.DICT_KEY,t.DICT_NAME,t.STATUS
			from SQLTOY_DICT_DETAIL t
		        where t.DICT_TYPE=:dictType
		        order by t.SHOW_INDEX
			]]>
			</sql>
		</sql-translate>
	</cache-translates>

	<!-- Cache refresh detection: you can provide multiple checkers based on sql, service, or rest services -->
	<cache-update-checkers>
		<!-- Incremental update: when the query result has internal grouping, the first column is the group -->
		<sql-increment-checker cache="dictKeyName"
			check-frequency="15" has-inside-group="true" datasource="dataSource">
			<sql><![CDATA[
			--#not_debug#--
			select t.DICT_TYPE,t.DICT_KEY,t.DICT_NAME,t.STATUS
			from SQLTOY_DICT_DETAIL t
	      		where t.UPDATE_TIME >=:lastUpdateTime
			]]></sql>
		</sql-increment-checker>
	</cache-update-checkers>
</sagacity>

```

* In real business development, you can use SqlToyCRUDService directly for routine operations, avoiding writing your own service for simple object operations; for complex logic, write your own service and call the LightDao provided by sqltoy to complete the database interactions!

```java
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = SqlToyApplication.class)
public class CrudCaseServiceTest {
	@Autowired
	private SqlToyCRUDService sqlToyCRUDService;

	/**
	 * Create a staff record
	 */
	@Test
	public void saveStaffInfo() {
		StaffInfoVO staffInfo = new StaffInfoVO();
		staffInfo.setStaffId("S190715005");
		staffInfo.setStaffCode("S190715005");
		staffInfo.setStaffName("Test Employee4");
		staffInfo.setSexType("M");
		staffInfo.setEmail("test3@aliyun.com");
		staffInfo.setEntryDate(LocalDate.now());
		staffInfo.setStatus(1);
		staffInfo.setOrganId("C0001");
		staffInfo.setPhoto(FileUtil.readAsBytes("classpath:/mock/staff_photo.jpg"));
		staffInfo.setCountry("86");
		sqlToyCRUDService.save(staffInfo);
	}
 }
```

# Key Source-Code Guide

## sqltoy-orm mainly consists of the following parts:

  - SqlToyDaoSupport: the base Dao provided for developers to extend, integrating all database operation methods.
  - LightDao: a convenient Dao for developers, letting them focus solely on writing Service business logic; call lightDao directly in the service.
  - DialectFactory: the database dialect factory; sqltoy dispatches to the implementation wrapper of the specific database according to the dialect of the current connection.
  - SqlToyContext: the sqltoy context configuration, the core configuration and exchange area of the entire framework; the spring configuration mainly configures sqltoyContext.
  - EntityManager: encapsulated in SqlToyContext, it manages POJO objects and establishes the relationship between objects and database tables. sqltoy scans and loads entities via the SqlToyEntity annotation.
  - ScriptLoader: the loader/parser for sql config files, encapsulated in SqlToyContext. sql files must strictly follow the *.sql.xml naming convention.
  - TranslateManager: the cache translate manager, used to load the cache-translate xml config file and the cache implementation class. sqltoy provides the interface and ships a default ehcache-based local cache implementation, which is the most efficient; a distributed cache such as redis carries too much IO overhead, and cache translation is a high-frequency call — it typically caches relatively stable, infrequently changing data such as staff, organizations, data dictionaries, product categories, and regions.
  - ShardingStrategy: the sharding strategy manager. Since the 4.x version, strategy managers do not need to be explicitly defined; they only need to be defined via spring, and sqltoy manages them dynamically at use time.

## Quick reading guide to understanding sqltoy:

  - Start from LightDao as the entry point to learn all the features sqltoy provides.
  - SqlToyDaoSupport is the concrete implementation of LightDao's functionality.
  - DialectFactory is the entry point into the different database dialect implementations; follow it to trace the concrete implementation logic of each database — you will see the encapsulations for pagination, fetching random records, fast paging, and more for oracle, mysql, and other databases.
  - EntityManager: here you will find how POJOs are scanned and built into the object model, and learn that operating on the database through POJOs essentially turns into interactions with the corresponding sql.
  - ParallelUtils (in the dialect.executor package): the parallel executor for objects under sharding (database and table); through this class you can see how, during batch sharding operations, collections are grouped into different databases and different tables and scheduled in parallel.
  - SqlToyContext: the context of sqltoy configuration; through this class you can see the full picture of sqltoy.
  - PageOptimizeUtils (in the dialect package): you can see the principle behind the default implementation of pagination optimization.

## Project History

* 2007~2008: while building a management system project for the Agricultural Bank of China, there were many query and statistics needs, with numerous query conditions that kept being added, so I kept thinking about how to adapt quickly to such changes. From a rather accidental flash of inspiration I discovered a way of writing dynamic sql vastly more powerful than mybatis, which served as a complement to hibernate jpa queries and delivered an extremely striking development experience. See a blog post written in 2009: https://blog.csdn.net/iteye_2252/article/details/81683940
* 2008~2012: since I kept working on financial enterprise projects, the data volumes were basically in the tens of millions of rows, so sqltoy continuously focused on enhancing sql queries around jpa. During this period it already integrated cache translation, fast pagination, pivot (row/column rotation), and other query features more distinctive than those of other frameworks.
* 2013~2014: to avoid making developers use two technologies side by side in one project, sqltoy implemented object-based crud functionality, forming the complete sqltoy-orm framework.
* 2014~2017: while leading the Lakala big data platform, sqltoy underwent a substantial refactor that rationalized its underlying structure, and it was strengthened and validated on the Lakala CRM and a big data platform with a daily average in the tens of millions, accumulating to the tens-of-billions level.
* 2018~present: fully tempered in complex scenarios such as building a SaaS multi-tenant ERP and real-time data warehouses combining flink-cdc with MPP databases, sqltoy is now very complete and reliable, and has been open sourced to share and co-build with the community!
