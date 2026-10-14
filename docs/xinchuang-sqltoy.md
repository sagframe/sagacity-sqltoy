# 一套 SQL，跑遍 25 种数据库：换库迁移，凭什么首选 SqlToy？

> 一个 ORM，25 种数据库方言。从 Oracle 到达梦、金仓、瀚高、神通，一套 SQL 全库运行；从 count 优化到缓存翻译，把每一次数据库交互都榨出性能。

## 一、换库迁移的真痛点：不是换数据库，是换"每一条 SQL"

一个在 Oracle 上稳定跑了十年的政务/金融系统，被要求限期迁移到达梦、金仓或瀚高。真正让团队崩溃的从来不是装库，而是：

- 上千条 SQL 里的 `nvl`、`to_char`、`sysdate`、`decode`、`from dual`……每一个 Oracle 习惯，在国产库上都是一个报错；
- 同一个产品要交付不同客户：甲方用 Oracle、乙方用达梦、丙方用金仓——难道维护 N 套 SQL？
- 迁完性能不能降：分页、报表、台账类高并发场景，在国产库上一点都不能慢。

手写改 SQL 是工作量黑洞，按数据库 if/else 分支是维护噩梦。**行业需要的不是"迁移工具"，而是把多库适配做进基础设施层的框架。** 这正是 SqlToy 自 2008 年立项起持续打磨的方向：产品级跨数据库适配。

## 二、30 秒看懂 SqlToy 的日常写法

先别谈架构，看一个最普通的查询在 SqlToy 里长什么样。SQL 保持原生形态，copy 到数据库客户端就能直接跑、直接调：

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
// 传了值的参数自动拼入条件，没传值的自动跳过整段
List<StaffVO> staffs = lightDao.find("queryStaff", params, StaffVO.class);
```

`#[...]` 是 SqlToy 的动态条件：传了 `orgId` 就带上该条件，没传整段消失——不拼字符串、不写一堆把 SQL 割裂的 `<if>` 标签。再给 VO 加个 `@Translate` 注解，`org_id`、`status` 查询后在内存里由码值翻译成名称，**当场省掉一次 JOIN**。这就是 SqlToy 的日常：SQL 还是那个 SQL，但更短、更快、更好维护。

## 三、换库首选：全覆盖的国产数据库方言矩阵

SqlToy 内置 25 种数据库方言，对国产数据库全部原生深度适配：

- **国产数据库**：达梦（DM）、人大金仓（Kingbase）、神通（Oscar）、瀚高（HighGo）、MogDB、Vastbase、StarDB、GaussDB、OpenGauss；
- **主流关系型**：MySQL、Oracle、PostgreSQL、SQL Server、DB2、SAP HANA、H2、SQLite、TiDB、OceanBase、PolarDB；
- **分布式 OLAP**：ClickHouse、Doris、StarRocks、Greenplum、Impala（Kudu）、TDengine；
- **NoSQL**：Elasticsearch、MongoDB。

更关键的是，这不是"名义支持"：每种方言的分页、锁、merge、sequence、函数转换都经过**真库探针逐项验证**，并由反射穷举断言锁定覆盖完整性——新增数据库类型时漏登记任何一项适配，测试直接失败。

对尚未单独列出的国产新库，`dialectMap` 一行配置即可别名到最接近的方言，国产数据库生态再出新库也不慌：

```properties
spring.sqltoy.dialectMap.kingbase=mysql
```

## 四、多库适配如何实现？四层机制让"一套 SQL 走天下"

### 1. 对象操作与主键策略：方言差异交给框架

save/update/delete/load 等对象操作的分页、锁、merge、主键回填全部由框架按方言生成；配合程序化主键（22 位/26 位/雪花/UUID），从源头避开 identity/sequence 的方言陷阱。业务代码全程不感知数据库。

### 2. 函数自适配：28 个跨库函数运行期自动转换

一行配置开启：

```properties
spring.sqltoy.functionConverts=default
```

`nvl`、`to_char`、`substr`、`instr`、`group_concat`、`uuid`、`date_add`、`last_day`、`add_months`、`trunc` 等 28 个函数，运行期自动转换为当前库原生形态：写 `nvl`，mysql 上变 `ifnull`；写 `decode`，非 oracle 系变 `case when`；写 `group_concat ... within group (order by ...)`，oracle 上正确落地为 `listagg ... within group ...`。

两个细节见功底：

- **源形态宽容**：不只认一种写法——`instr/locate/posstr`、`character_length/octet_length`、`iif`、`mid`、`str_to_date` 等各库用户习惯写法都能识别为源、互相转换；
- **响亮保留**：不能安全转换的形态绝不静默"猜"，原样交给目标库报错——用响亮暴露换静默错值。

### 3. 方言 sqlId + 虚表宏：极端场景的逃生舱

个别 SQL 存在函数自适配覆盖不了的**结构性差异**——典型如树形查询：oracle 的 `connect by` 层次查询高效且防环，而其余数据库要用标准递归 CTE。SqlToy 的做法是给 sqlId 挂方言变体，框架按当前库自动选取，**业务代码零 if/else**：

```xml
<!-- 基础版：递归 CTE，适用于 pg / mysql8+ / 达梦 / 金仓等 -->
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

<!-- oracle 变体：connect by 层次查询，nocycle 防脏数据死循环 -->
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

业务侧照常只调基础 sqlId：

```java
List<DeptVO> tree = lightDao.find("dept_sub_tree", params, DeptVO.class);
```

框架按 `dialect_sqlId → sqlId_dialect → sqlId` 的顺序查找实际执行的 sql：oracle 环境命中 `dept_sub_tree_oracle`，其余数据库自动落回基础版；反向命名 `oracle_dept_sub_tree` 同样支持且优先级最高。

而 6.0.6 新增的 `@virtualTable()` 虚表宏，则解决了最后一个顽疾——伪表：

```sql
select '0' id,'全局公共' name @virtualTable()
union
select id,name from sys_tenant_info
```

oracle 上自动展开为 `from dual`、db2 展开为 `from sysibm.sysdummy1`、hana 展开为 `from dummy`，mysql/pg/sqlserver 等直接剔除。**从此不再为 `from dual` 站队。**

### 4. redoDataSources：测试期就把所有目标库跑一遍

产品级适配不靠现场救火。配置 `redoDataSources` 后，任意查询在测试阶段**同时在其他目标库重放执行**，不兼容写法提前暴露，而不是等客户现场换库时才报错：

```properties
spring.sqltoy.redoDataSources[0]=dmdb
spring.sqltoy.redoDataSources[1]=kingbasedb
```

## 五、高性能如何实现？把每一次数据库交互都省出来

多库适配解决"能跑"，性能解决"跑得快"。SqlToy 的性能答案不是堆缓存，而是**减少交互次数、把计算下推**。

### 1. 四层加速的分页：一行调用

```java
Page<StaffVO> page = lightDao.findPage(new Page(10, 1), "queryStaff", params, StaffVO.class);
```

这一行代码背后，四层加速同时生效：

- **智能 count 优化（零配置）**：count 绝不是粗暴的 `select count(1) from (你的sql)`，而是智能改写——剔除不必要的 order by、切掉 select 列里的函数运算，只 count 该 count 的；
- **缓存分页**：对"条件相同、频繁翻页"的场景，`<page-optimize>` 把 count 结果缓存起来，有效期内翻页直接复用，不再次次查总数：

```xml
<sql id="queryStaff">
	<!-- parallel：count 与单页数据并行执行；alive-seconds：缓存存活秒数 -->
	<page-optimize parallel="true" alive-max="100" alive-seconds="120"/>
	<value><![CDATA[
		select t.* from sys_staff t
		where t.status = 1
		#[and t.staff_name like :staffName]
		order by t.entry_date desc
	]]></value>
</sql>
```

- **快速分页**：大表 join 分页时，`@fast()` "先分页、后关联"——子查询先按 pageSize 取出当页记录，再与其他表关联，join 的数据规模不再随结果集膨胀；非分页查询时框架自动剔除 `@fast`，不影响其他调用：

```xml
<sql id="queryStaffWithOrgan">
	<value><![CDATA[
		select t1.*, t2.organ_name
		from @fast(
			select t.* from sys_staff t
			where t.status = 1
			#[and t.staff_name like :staffName]
			order by t.entry_date desc
		) t1
		left join sys_organ t2 on t1.organ_id = t2.organ_id
	]]></value>
</sql>
```

- **并行分页**：`parallel="true"` 让 count 与取数两个串行步骤压缩为一步的耗时。

极特殊场景还能手写 `<count-sql>` 做终极优化；无限下拉这类不需要总数的场景，`page.setSkipQueryCount(true)` 直接跳过 count。

### 2. 缓存翻译：消灭 JOIN，零网络开销

码值转名称（机构、字典、状态……）的传统做法是 JOIN 字典表。SqlToy 里 sql 只查码值，翻译声明一行搞定：

```xml
<sql id="queryOrder">
	<!-- organ_id 码值 → organName 名称列，查询后内存翻译 -->
	<translate cache="organIdNameCache" columns="organName"/>
	<value><![CDATA[
		select t.order_id, t.organ_id, t.order_type
		from sqltoy_order_info t
		where t.status = 1
	]]></value>
</sql>
```

或者更彻底——sql 里什么都不写，注解加在 VO 上：

```java
@Translate(cacheName = "organIdNameCache", keyField = "organId")
private String organName;
```

查询返回后，框架在**本地内存**把码值翻译成名称：字典数据提前加载进缓存（支持增量更新、REST 远程缓存），超大规模主数据还有 FIFO 动态缓存兜底——**少一次 JOIN，就少一个性能损耗点**，字典表也从此告别数据库压力热点。

### 3. 单次交互哲学：弹性更新与 updateFetch

- **弹性更新**：不同于 Hibernate"先 load 再改"的两次交互，SqlToy 一次交互完成修改，null 字段自动跳过；
- **updateFetch / updateSaveFetch**：库存、资金等高并发台账场景，锁查询→校验→修改→返回结果**一次数据库交互**完成，既消除竞态窗口，又省掉往返开销。

### 4. 并行查询与流式取数

`parallelQuery` 让多个独立查询并发执行、结果一次汇总，天生适合驾驶舱式页面；流式查询以 fetchSize 控制内存，支撑超大数据集导出。

### 5. 工程级保障

慢 SQL 自动识别处理、SQL 签名（`/*-id-*/`）便于数据库日志秒级定位、debug 热加载免重启调 SQL、GraalVM AOT 原生镜像支持——性能优化做进日常开发，而不是事后救火。

## 六、为什么是"首选"？因为它把适配从"项目"变成"配置"

回顾 SqlToy 的设计哲学，一条主线贯穿始终：**产品化**——一套软件产品同时面对 Oracle、MySQL、达梦、金仓、瀚高交付不同客户，代码不分支。这恰是换库迁移的核心命题：

- **投标选型**：方言矩阵与真库验证记录，就是技术应答书；
- **迁移实施**：函数自适配 + 方言 sqlId + 虚表宏，就是施工工具；
- **长期运维**：redoDataSources + 慢 SQL 处理 + 缓存翻译，就是运维底座。

面向 JDK 17+ 与 Spring Boot 3/4，同时支持传统 Spring、Solon 与纯 Java 项目；JDK 8 存量系统亦有 5.6 线长期版本可平滑承接。

## 现在开始

```xml
<dependency>
    <groupId>com.sagframe</groupId>
    <artifactId>sagacity-sqltoy-spring-starter</artifactId>
    <version>6.0.6</version>
</dependency>
```

- 在线文档：<https://sagframe.github.io/sqltoy-docs/>
- 换库迁移适配指引：<https://sagframe.github.io/sqltoy-docs/#/introduction/xinchuang>
- GitHub：<https://github.com/sagframe/sagacity-sqltoy>
- Gitee：<https://gitee.com/sagacity/sagacity-sqltoy>
- QQ 交流群：**531812227**

> SqlToy——JPA 式对象操作与原生 SQL 查询的融合体，让每一次换库迁移，都从容不迫。
