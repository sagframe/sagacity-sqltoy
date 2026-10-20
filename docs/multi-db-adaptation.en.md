# One SQL, Every Database: How SqlToy Makes Cross-Database Adaptation Disappear

> One ORM, 25 SQL dialects. Oracle, PostgreSQL, MySQL, SQL Server, DB2, SAP HANA; China's domestic engines (DM, Kingbase, HighGo, Oscar, MogDB, Vastbase, StarDB, GaussDB, OpenGauss); OLAP stores (ClickHouse, Doris, StarRocks, Greenplum, Impala, TDengine); plus Elasticsearch and MongoDB. Write your SQL once — SqlToy adapts it to every database you ship to.

## The Real Cost of "We Support Multiple Databases"

Product software lives a multi-database life: customer A runs Oracle, customer B PostgreSQL, customer C a domestic engine mandated by compliance. Every dialect habit becomes a per-database maintenance tax:

- `nvl` vs `ifnull` vs `coalesce`; `to_char` vs `date_format` vs `FORMAT`; `decode`; `sysdate` vs `now()` vs `getdate()`;
- `from dual` — mandatory on Oracle/DB2/HANA, a syntax error on PostgreSQL/SQL Server;
- hierarchical queries: `connect by` on Oracle vs recursive CTE everywhere else;
- pagination: `rownum` vs `limit offset` vs `offset fetch` vs `top`;
- and the verification burden: discovering incompatibilities at the customer site, after the switch.

Hand-rewriting SQL per database is a bottomless pit; if/else branching by database is a maintenance nightmare. **What product teams need is not a migration tool but a framework that turns multi-database adaptation into infrastructure.** That has been SqlToy's focus since 2008.

## A 30-Second Look

SQL stays native — copy it into any database client and it runs as-is:

```xml
<sql id="queryStaff">
	<value>
	<![CDATA[
		select s.staff_id, s.staff_name, s.org_id, s.status
		from sys_staff s
		where s.status = 1
		#[and s.org_id = :orgId]
		#[and s.staff_name like :staffName]
	]]>
	</value>
</sql>
```

```java
// Parameters with values join the query; absent parameters drop their whole condition
List<StaffVO> staffs = lightDao.find("queryStaff", params, StaffVO.class);
```

`#[...]` is SqlToy's dynamic condition: no string concatenation, no `<if>` tags chopping your SQL into fragments. Add a `@Translate` annotation on the VO and code-to-name translation happens in memory after the query — **one JOIN eliminated**.

## The Dialect Matrix

SqlToy ships 25 dialects, auto-detected from the JDBC `productName` via `DialectFactory`:

- **Mainstream relational**: MySQL, Oracle, PostgreSQL, SQL Server, DB2, SAP HANA, H2, SQLite, TiDB, OceanBase, PolarDB;
- **Domestic Chinese engines**: DM (Dameng), Kingbase, Oscar, HighGo, MogDB, Vastbase, StarDB, GaussDB, OpenGauss;
- **Distributed OLAP**: ClickHouse, Doris, StarRocks, Greenplum, Impala (Kudu), TDengine;
- **NoSQL**: Elasticsearch, MongoDB.

This is not nominal support: pagination, locking, merge, sequence and function conversion for every dialect are verified against **real databases**, with reflection-exhaustive test assertions locking coverage completeness — add a database type without registering its adaptation and the test suite fails loudly.

Engines without a dedicated dialect (especially PostgreSQL derivatives) alias onto the nearest one with a single property:

```properties
spring.sqltoy.dialectMap.kingbase=mysql
```

## Four Layers of Adaptation

### 1. Object operations: the framework owns the dialect

save/update/delete/load — pagination, locking, merge and primary-key backfill are generated per dialect by the framework. Pair them with programmatic primary keys (22/26-digit ordered ids, snowflake, UUID) and identity/sequence dialect traps disappear at the source. Business code never sees the database.

### 2. Function auto-adaptation: 28 functions converted at runtime

One property switches it on:

```properties
spring.sqltoy.functionConverts=default
```

`nvl`, `to_char`, `substr`, `instr`, `group_concat`, `uuid`, `date_add`, `last_day`, `add_months`, `trunc` and 20 more are converted to the current database's native form at runtime: write `nvl` and MySQL gets `ifnull`; write `decode` and non-Oracle databases get `case when`; write `group_concat ... within group (order by ...)` and Oracle gets a correct `listagg ... within group ...`.

Two details show the depth:

- **Source-form tolerance**: not just one spelling per function — `instr/locate/posstr`, `character_length/octet_length`, `iif`, `mid`, `str_to_date` and other dialect-native spellings are all recognized as sources and converted between each other;
- **Loud retention**: forms that cannot be converted safely are never silently "guessed" — they pass through untouched for the target database to reject. Loud errors instead of silent wrong values.

### 3. Escape hatches for what functions cannot reach

**Dialect sqlId variants** cover structural differences — the classic case being tree queries, where Oracle's `connect by` beats recursive CTEs on efficiency and cycle-safety:

```xml
<!-- base version (recursive CTE): pg / mysql8+ / DM / Kingbase etc. -->
<sql id="dept_sub_tree">
	<value>
	<![CDATA[
		with recursive sub as (
			select d.dept_id, d.dept_name, d.parent_id, 0 as lvl
			from sys_dept d
			where d.dept_id = :rootId
			union all
			select d.dept_id, d.dept_name, d.parent_id, s.lvl + 1
			from sys_dept d
			join sub s on d.parent_id = s.dept_id
		)
		select dept_id, dept_name, lvl from sub order by lvl
	]]>
	</value>
</sql>

<!-- oracle variant: connect by hierarchical query, nocycle guards dirty data -->
<sql id="dept_sub_tree_oracle">
	<value>
	<![CDATA[
		select d.dept_id, d.dept_name, (level - 1) as lvl
		from sys_dept d
		start with d.dept_id = :rootId
		connect by nocycle prior d.dept_id = d.parent_id
		order by lvl
	]]>
	</value>
</sql>
```

Business code calls only the base sqlId; the framework resolves `dialect_sqlId → sqlId_dialect → sqlId` and picks `dept_sub_tree_oracle` on Oracle, the base elsewhere. The `<sql dialect="...">` attribute (solidify parsing in a declared dialect) and `spring.sqltoy.realDialectFirst` (variant lookup follows the connection-probed real dialect, for proxied databases) complete the layer.

**The `@virtualTable()` macro** (new in 6.0.6) removes the last stubborn difference — pseudo-tables:

```sql
select '0' id,'Global' name @virtualTable()
union
select id,name from sys_tenant_info
```

Expanded at parse time per dialect: `from dual` on the Oracle family (oracle/oracle11/dm/oceanbase), `from sysibm.sysdummy1` on DB2, `from dummy` on HANA, stripped entirely on MySQL/PostgreSQL/SQL Server and everyone else who supports FROM-less select natively. Aliases `@dual()` / `@dummyTable()` are equivalent, case-insensitive, and the macro may appear once per constant select. **No more picking sides on `from dual`.**

### 4. Verify against every target database before shipping

Product-grade adaptation must not rely on firefighting at customer sites. Configure `redoDataSources` and every query is **replayed once on each additional data source** during testing, exposing incompatible constructs immediately:

```properties
spring.sqltoy.redoDataSources[0]=dmdb
spring.sqltoy.redoDataSources[1]=kingbasedb
```

Combined with the framework's own guarantees (real-database probing per dialect, reflection-exhaustive coverage assertions, loud retention), the errors you see in verification are exactly your adaptation surface — nothing hides.

## Performance That Survives the Switch

Multi-database adaptation answers "does it run"; performance answers "does it run fast". SqlToy's answer is not more caching but **fewer database interactions and pushed-down computation**:

- **Pagination, four layers deep**: `findPage` gets smart count optimization by default (rewrites the count, strips order by and select-list computation); `<page-optimize parallel="true" alive-max="100" alive-seconds="120"/>` caches the count for frequently paged queries and runs count + page data in parallel; `@fast()` paginates the deciding subquery first and joins afterwards, so join volume no longer grows with the result set; `page.setSkipQueryCount(true)` skips the count entirely for infinite-scroll UIs;
- **Cache translate**: code-to-name display (department, status, dictionary values) translates in local memory after the query instead of JOINing dictionary tables — with incremental cache updates and a FIFO dynamic cache for ultra-large master data. One JOIN fewer is one performance loss fewer;
- **Single-interaction philosophy**: elastic update modifies in one round trip (null fields skipped), unlike load-then-update ORMs; `updateFetch` / `updateSaveFetch` complete lock-query → check → modify → return in **one** database interaction for high-concurrency ledger scenarios (inventory, funds);
- **Parallel query & streaming**: `parallelQuery` runs independent queries concurrently and aggregates in one call; streaming queries bound memory via fetchSize for massive exports;
- **Engineering guardrails**: automatic slow-SQL identification and handling, SQL signatures (`/*-id-*/`) for seconds-level location in database logs, debug hot-reload, GraalVM native-image support.

## Get Started

```xml
<dependency>
    <groupId>com.sagframe</groupId>
    <artifactId>sagacity-sqltoy-spring-starter</artifactId>
    <version>6.0.8</version>
</dependency>
```

- Online documentation: <https://sagframe.github.io/sqltoy-docs/#/en/>
- GitHub: <https://github.com/sagframe/sagacity-sqltoy>
- Gitee: <https://gitee.com/sagacity/sagacity-sqltoy>

> SqlToy — JPA-style object operations fused with best-in-class native SQL querying. One codebase, every customer, every database.

*Companions in the source repository: `docs/multi-db-adaptation.md` (Chinese counterpart, database-migration selection focus) and the online docs' Database Migration Guide — a six-step assess → configure → refactor → verify → perform → cut-over checklist built on the mechanisms above.*
