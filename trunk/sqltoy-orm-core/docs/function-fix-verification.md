# function 模块缺陷修复与真库验证清单

> 记录时间：2026-10-19
> 范围：`sqltoy-orm-core/src/main/java/org/sagacity/sqltoy/plugins/function`
> 本机验证：SQL 文本单测 + H2/SQLite 真执行；MySQL/SQLServer/Oracle/PG/DB2/DM/OB/HANA/CH 已于
> 2026-10-19 晚在本机 Docker 容器全部完成真库确认（结果见第六节，新增修复 F13-F15，并按
> “HH 默认 24 小时”口径校正 F2）。

## 一、已修复内容

| 编号 | 缺陷 | 修复点 | 回归用例 |
|---|---|---|---|
| F1 | Oracle 小写 `mm`（月）被当分钟 | `ToChar`/`ToDate` 按 Oracle 模型区分 `mm`(月)/`mi`(分) | `FunctionDefectRegressionTest#oracleLowercaseMonth` |
| F2 | `ToChar`/`ToDate` 部分分支把裸 `HH` 当 12 小时，与 `DateFormat`/Oracle/PG 分支的 24 小时不一致 | 统一口径：裸 `HH` = 24 小时，`hh` = 12 小时，`HH24` 恒为 24 小时 | `#hhTwelveHour` |
| F3 | Java `HH:mm` 到 SQLServer/SQLite/DB2 错值 | 新增 `FunctionUtils.normalizeJavaTimePattern` + `DateFormat` 的 `toMySqlFormat` | `#javaTimePattern` |
| F4 | DB2 `posstr` 参数序被反转 | `posstr` 归入 haystack-first 分支（与 `strpos` 同序） | `#posstrArgumentOrder` |
| F5 | `position(... in ...)` 字面量含 `" in "` 被切坏 | 引号/括号感知的顶层 `in` 切分 | `#positionLiteralWithIn` |
| F6 | 标准 `trim(from col)` 解析错误 | 识别串首 `from`，H2/SQLServer/SQLite 正确改写 | `#trimFromForm` |
| F7 | `string_agg` 排序在 SQLServer/PG 间语法不兼容 | SQLServer 转 `within group`；PG 转括号内 `order by` | `#stringAggOrdering` |
| F8 | SQL Server 周截断受 `DATEFIRST` 影响 | `DATEADD(day,-(DATEDIFF(day,0,x)%7),CONVERT(date,x))` | `#sqlServerWeekTruncation` |
| F9 | `current_timestamp` 到 Oracle 退化为 `sysdate` | Oracle 系保留 `CURRENT_TIMESTAMP`（可带精度） | `#currentTimestampOracle` |
| F10 | SQL 末尾裸关键字不转换 | `FunctionUtils` 增加尾部哨兵 | `#trailingKeyword` |
| F11 | 单参 `substr` 抛 `ArrayIndexOutOfBoundsException` | 参数不足直接原样保留 | `#malformedSubstrKept` |
| F12 | SQL Server 多列 `group_concat` 用 `+` 做数值加法 | 改 `case when ... then null else concat(...) end` | `#stringAggOrdering` |
| F13 | MySQL 9.3 / Oracle 26ai 不接受 `trim(from x)` | `Trim` 省略剔除字符形态统一改写为单参 `trim(x)`（leading/trailing 转 `ltrim`/`rtrim`） | `#trimFromForm` |
| F14 | SQL Server `FORMAT` 首参为字符串字面量报 varchar 非法 | `FunctionUtils.wrapSqlServerDateFormatArg` 对单引号字面量 `CAST(... AS datetime)` | `#javaTimePattern` |
| F15 | `datediff(unit,...)` 到 OceanBase 原样透传报参数个数错误 | OceanBase 并入 Oracle 分支（与 OceanBaseDialect 的 oracle 系产出一致） | `#oceanbaseDateDiff` |

同时更新了受影响的既有断言：

- `CommonFuncDialectTest#instrMultiSpellingMatrix`：`posstr` 改为 DB2 的 haystack-first 语义。
- `FunctionConvertMatrixTest#dateFormatJavaStandaloneHour`：`HH24` 大小写等价断言。

## 二、本机已完成的测试

```powershell
# 纯 SQL 文本 + H2/SQLite 真执行，无需外部数据库
mvn -o -Dtest=FunctionDefectRegressionTest test

# 全部非真库函数测试
mvn -o -Dtest=FunctionConvertMatrixTest,FunctionDialectFullMatrixTest,CommonFuncDialectTest,FunctionAuditProbeTest,FunctionRetestProbeTest,FindInSetDialectTest,KingbaseFunctionConvertTest,FunctionDefectRegressionTest,GetDialectSqlEdgeTest,FunctionUtilTest test
# 结果：89 tests, 0 failures（2026-10-19 复核，新增 oceanbase datediff 回归）
```

`FunctionDefectRegressionTest#executableOnEmbeddedDatabases` 已在 H2/SQLite 上真实执行：

- H2：`trim(from '  x  ')`、`to_date('2026-03-20','yyyy-mm-dd')`
- SQLite：`to_char('2026-03-20','yyyy-mm-dd')`、`date_format('2026-03-20 10:30:45','yyyy-MM-dd HH:mm:ss')`、`trim(from '  x  ')`

## 三、回家真库验证步骤

### 1. 配置连接

现有 `*RealDbSmokeTest` 约定读取 `target/<db>-probe.properties`，文件不存在时整组跳过（不会让普通构建失败）。示例：

```properties
# target/mysql-probe.properties
url=jdbc:mysql://localhost:3306/test?useSSL=false&serverTimezone=Asia/Shanghai
username=root
password=your_password
```

文件名对应关系：

| 数据库 | 配置文件 |
|---|---|
| MySQL | `target/mysql-probe.properties` |
| PostgreSQL | `target/pg-probe.properties` |
| SQL Server | `target/sqlserver-probe.properties` |
| Oracle | `target/oracle-probe.properties` |
| DB2 | `target/db2-probe.properties` |
| DM | `target/dm-probe.properties` |
| OceanBase | `target/ob-probe.properties` |
| HANA | `target/hana-probe.properties` |
| ClickHouse | `target/clickhouse-probe.properties`（只需 url） |
| Kingbase | `target/kingbase-probe.properties` |
| OpenGauss | `target/opengauss-probe.properties` |

注意：`target/` 不入库，凭据不会进仓库。

### 2. 先跑现有真库冒烟

```powershell
mvn -o -Dtest=MysqlRealDbSmokeTest,PgRealDbSmokeTest,SqlServerRealDbSmokeTest,OracleRealDbSmokeTest,Db2RealDbSmokeTest,DmRealDbSmokeTest,OceanbaseRealDbSmokeTest,HanaRealDbSmokeTest,ClickHouseRealDbSmokeTest test
```

这些类覆盖的是函数转换周边的既有行为；下面第四节的 SQL 才是本次修复的专项验收。

### 3. 专项 SQL 验收矩阵

建议用 `FunctionUtils.getDialectSql(sourceSql, dialect)` 生成目标 SQL 后执行，或直接执行“目标 SQL”列。

#### MySQL

| 源 SQL | 目标 SQL | 期望 |
|---|---|---|
| `select to_char('2026-03-20','yyyy-mm-dd')` | `select date_format('2026-03-20','%Y-%m-%d')` | `2026-03-20` |
| `select to_char('2026-03-20 14:30:45','yyyy-mm-dd hh24:mi:ss')` | `select date_format(...,'%Y-%m-%d %H:%i:%s')` | `2026-03-20 14:30:45` |
| `select to_char('2026-03-20 14:30:45','HH:MI')` | `select date_format(...,'%H:%i')` | `14:30` |
| `select to_char('2026-03-20 14:30:45','hh:MI')` | `select date_format(...,'%h:%i')` | `02:30` |
| `select to_date('2026-03-20','yyyy-mm-dd')` | `select STR_TO_DATE(...,'%Y-%m-%d')` | `2026-03-20` |
| `select date_format('2026-03-20 14:30:45','yyyy-MM-dd HH:mm:ss')` | `select date_format(...,'%Y-%m-%d %H:%i:%s')` | `2026-03-20 14:30:45` |
| `select trim(from '  x  ')` | `select trim('  x  ')` | `x` |
| `select position('a' in 'x in y')` | 原样/Mysql 原生 | `0` |

#### SQL Server

| 源 SQL | 期望 |
|---|---|
| `select date_format('2026-03-20 14:30:45','yyyy-MM-dd HH:mm:ss')` | `2026-03-20 14:30:45`（`FORMAT` 的 `HH:mm:ss`，不是 `MM`） |
| `select to_char('2026-03-20 14:30:45','yyyy-MM-dd HH:mm:ss')` | `2026-03-20 14:30:45` |
| `select trim(from '  x  ')` | `x` |
| `select position('a' in 'x in y')` | `0`（转 `charindex('a','x in y')`，不得残缺） |
| `select trunc(cast('2026-03-20 14:30:45' as datetime),'IW')` | `2026-03-16`（周一，不受 `DATEFIRST` 影响） |
| `select string_agg(name,'-' order by id) from (values ('a',1),('b',2)) t(name,id)` | `a-b`（转 `within group`） |
| `select current_timestamp` | 正常执行（转 `getdate()`） |

#### Oracle

| 源 SQL | 期望 |
|---|---|
| `select current_timestamp from dual` | 返回 `TIMESTAMP WITH TIME ZONE`（驱动类名含 `TIMESTAMPTZ`/`TIMESTAMP`，不是 `DATE`） |
| `select to_char(date '2026-03-20','yyyy-mm-dd') from dual` | `2026-03-20` |
| `select trim(from '  x  ') from dual` | `x` |
| `select trunc(date '2026-03-20','IW') from dual` | `2026-03-16` |

#### PostgreSQL

| 源 SQL | 期望 |
|---|---|
| `select string_agg(name,'-') within group (order by id) from (values ('a',1),('b',2)) t(name,id)` | `a-b`（转 `string_agg(name,'-' order by id)`） |
| `select posstr('admin','dmi')` | `2`（转 `position('dmi' in 'admin')`） |
| `select position('a' in 'x in y')` | `0`（不得被字面量里的 `in` 切坏） |

#### DB2

| 源 SQL | 期望 |
|---|---|
| `select posstr('admin','dmi') from sysibm.sysdummy1` | `2`（DB2 原生 posstr，透传） |
| `select to_date('2026-03-20','yyyy-mm-dd') from sysibm.sysdummy1` | `2026-03-20`；**待确认 DB2 是否接受小写格式模型**（见第五节） |

#### ClickHouse

| 源 SQL | 期望 |
|---|---|
| `select to_char(toDate('2026-03-20'),'yyyy-mm-dd')` | `2026-03-20` |
| `select date_format(toDateTime('2026-03-20 14:30:45'),'yyyy-MM-dd HH:mm:ss')` | `2026-03-20 14:30:45` |

#### HANA

| 源 SQL | 期望 |
|---|---|
| `select to_char(to_date('2026-03-20'),'yyyy-mm-dd') from dummy` | `2026-03-20` |
| `select to_date('2026-03-20','yyyy-mm-dd') from dummy` | `2026-03-20` |

#### DM / OceanBase（已知覆盖缺口，重点确认）

| 数据库 | 源 SQL | 期望 | 当前风险 |
|---|---|---|---|
| DM | `select concat('a','-','b') from dual` | `a-b` | `Concat` 未把 DM 纳入三参以上 `||` 转换；若 DM 的 CONCAT 只接受两参会报错 |
| OceanBase | `select datediff(day, date '2026-03-01', date '2026-03-20') from dual` | `19` | `DateDiff` 未把 OCEANBASE 纳入任何分支，三参形态原样透传 |
| DM | `select current_timestamp from dual` | TIMESTAMP | 已按 Oracle 系保留 `CURRENT_TIMESTAMP` |
| OceanBase | `select current_timestamp from dual` | TIMESTAMP | 已按 Oracle 系保留 `CURRENT_TIMESTAMP` |

如果这两项在真库报错，就是第五节列出的待修覆盖项，请把实际报错贴回来，我按真实库能力补分支。

## 四、如何判定通过

对每条：

1. `FunctionUtils.getDialectSql(源 SQL, dialect)` 生成的 SQL 与上表“目标 SQL/期望”一致；
2. 在目标库执行生成后的 SQL，返回上表期望值；
3. 若返回类型敏感（Oracle `current_timestamp`），同时检查 `ResultSet.getObject(1).getClass()`。

## 五、本次未覆盖/需真库确认的项（均已确认，结论见第六节）

1. **DB2 小写 Oracle 格式模型**：`ToDate` 两参对 DB2 是原样透传，`yyyy-mm-dd` 是否被 DB2 `TO_DATE` 接受需真库确认；如不接受，需在 DB2 分支统一转大写 token。
2. **DM `concat` 多参**：需确认 DM 的 `CONCAT` 是否只支持两参；若只支持两参，`Concat` 需把 DM 加入三参以上 `||` 转换。
3. **OceanBase `datediff(unit,...)`**：需确认 OB 的 MySQL/Oracle 模式能力，再决定归入 MySQL 分支还是 Oracle 分支。
4. **`find_in_set(...)=1`**：按既有测试是“有意不消费比较符、交目标库响亮报错”，不是本次缺陷；真库请按现有 `FindInSetDialectTest` 的边界理解。
5. **SQL Server 多列 `group_concat` 的真实执行**：已改为 `concat` + null 守卫，建议用数值列和字符串列各验一次（字符串拼接、数值转字符串、任一 null 返回 null）。

## 六、2026-10-19 本机 Docker 真库确认结果

### 1. 环境

| 键 | 方言 | 容器 / 版本 | 结果 |
|---|---|---|---|
| mysql | mysql | `sqltoy-mysql9` MySQL 9.3（3307） | PASS |
| sqlserver | sqlserver | `sqltoy-mssql25` SQL Server 2025（1434） | PASS |
| oracle | oracle | `sqltoy-oracle` Oracle 26ai Free 23.26.3.0.0（1521/FREEPDB1） | PASS |
| postgresql | postgresql | `sqltoy-pgvector` PostgreSQL 18.6 + PostGIS（5433） | PASS |
| db2 | db2 | `sqltoy-db2` DB2 12.1.5.0（50000/SQLTOYDB） | PASS |
| dm | dm | `sqltoy-dm8-2026` DM8（5236） | PASS |
| oceanbase | oceanbase | `sqltoy-ob` OceanBase CE 4.4.2.1 **mysql 模式**（2881/test） | PASS（mysql 方言路径） |
| hana | hana | `sqltoy-hana` HANA Express 2.00.088（39041/HXE） | PASS |
| clickhouse | clickhouse | `sqltoy-clickhouse` ClickHouse 26.8.2.7（8123） | PASS |
| opengauss | opengauss | `sqltoy-opengauss` openGauss 5.0（5432，顺带验证串尾关键字） | PASS |

验收用例：`FunctionFixRealDbVerificationTest`（12 用例），连接配置读取
`target/<db>-probe.properties`，缺失/连接失败自动跳过。真库执行结果：**12 tests, 0 failures**。
同时回归：

- `FunctionDefectRegressionTest`（13 用例，含 F13-F15 新增断言）
- 第三节 2 的既有真库冒烟 9 个：**38 tests, 0 failures**
- 第二节非真库函数全集：**89 tests, 0 failures**（新增 1 个 oceanbase datediff 用例）

### 2. 第五节待确认项的实测结论

1. **DB2 小写格式模型：接受**。`to_date('2026-03-20','yyyy-mm-dd')` 在 DB2 12.1.5 返回
   `2026-03-20`，与 `YYYY-MM-DD` 一致，无需补大写归一。
2. **DM `concat` 多参：原生支持**。`concat('a','-','b')` 在 DM8 真执行返回 `a-b`，
   无需并入 `||` 转换。
3. **OceanBase `datediff`：按模式分流**。本机 OB 为 CE mysql 模式：
   - 按 verify 工程约定配 `dialect=mysql`：`datediff(day,'2026-03-01','2026-03-20')`
     转 `DATEDIFF('2026-03-20','2026-03-01')`，真执行返回 **19**；
   - 框架默认识别为 `oceanbase` 方言时，原实现无分支原样透传，OB 报
     `Incorrect parameter count in the call to native function 'datediff'`。

   结论：`oceanbase` 方言与 `OceanBaseDialect` 的 oracle 系产出保持一致，`DateDiff`
   并入 Oracle 分支，生成 `TRUNC(to_date(...)) - TRUNC(to_date(...))`（F15）；mysql
   模式租户仍按既定约定显式配 `dialect=mysql`（真执行路径已验 19）。
   OB oracle 模式租户本机未部署，F15 只锁定产物形态，执行面由 Oracle 26ai 同构覆盖。
4. **`find_in_set(...)=1`**：`FindInSetDialectTest` 12 用例通过，维持既有边界，未改动。
5. **SQL Server 多列 `group_concat` 真执行**：`(123,'x')` → `123x`（数值走字符串拼接、
   不做加法）；`(1,'y'),(2,null)` → `1y`（null 行被跳过）；全 null 行返回 null。
   三条均在 SQL Server 2025 真库通过。

### 3. 真库验收新发现并已修复的缺陷

| 编号 | 现象（修复前真库报错/错值） | 修复 |
|---|---|---|
| F13 | MySQL 9.3：`select trim(from '  x  ')` → 1064 语法错；Oracle 26ai：ORA-30000 | `Trim` 对省略剔除字符的 `trim(from x)` 改写为 `trim(x)`；`leading/trailing` 改写为 `ltrim/rtrim` |
| F14 | SQL Server：`select FORMAT('2026-03-20 14:30:45','yyyy-MM-dd HH:mm:ss')` → “Argument data type varchar is invalid for argument 1 of format function” | 日期格式化时对单引号字面量 `CAST(... AS datetime)`；列/表达式保持原类型 |
| F15 | OceanBase：`datediff(unit,...)` → 参数个数错误 | `DateDiff` 将 OCEANBASE 并入 Oracle 分支（F15） |

F13/F14 的修复在真库复测通过：MySQL `trim('  x  ')` → `x`，Oracle `trim('  x  ')` → `x`，
SQL Server `FORMAT(CAST('2026-03-20 14:30:45' AS datetime),'yyyy-MM-dd HH:mm:ss')` →
`2026-03-20 14:30:45`。

### 4. HH 口径修正（F2）

按“裸 `HH` = 24 小时、`hh` = 12 小时、`HH24` 恒为 24 小时”统一后复测：

| 目标库 | 源格式 | 生成/执行结果 |
|---|---|---|
| MySQL | `HH:MI` | `date_format(...,'%H:%i')` → `14:30` |
| MySQL | `hh:MI` | `date_format(...,'%h:%i')` → `02:30` |
| SQL Server | `HH:MI` | `FORMAT(t,'HH:mm')` → `14:30` |
| SQL Server | `hh:MI` | `FORMAT(t,'hh:mm')` → `02:30` |
| SQLite | `HH:MI` | `strftime('%H:%M',...)` → `14:30` |
| SQLite | `hh:MI` | `strftime('%I:%M',...)` → `02:30` |
| DB2 | `HH:MI` | `to_char(...,'HH24:MI')` → `14:30` |
| DB2 | `hh:MI` | `to_char(...,'hh:MI')` → `02:30` |
| H2 | `HH:MI` | `to_char(...,'HH24:MI')` → `14:30` |
| H2 | `hh:MI` | `to_char(...,'hh:MI')` → `02:30` |
| ClickHouse | `HH:MI` | `date_format(toDateTime(...),'%H:%i')` → `14:30` |
| ClickHouse | `hh:MI` | `date_format(toDateTime(...),'%h:%i')` → `02:30` |

H2（`to_char` 裸 `HH` 原生为 12 小时，实测须转 `HH24`）与 SQLite 的执行断言已加入
`FunctionDefectRegressionTest#executableOnEmbeddedDatabases`；MySQL/SQLServer/DB2/
ClickHouse 的两种大小写已在 `FunctionFixRealDbVerificationTest` 真库执行验证。

### 5. 未纳入本轮的范围

- Kingbase、Vastbase、HighGo、TiDB、StarRocks、Doris 容器本轮未启动；这些方言的
  回归由 SQL 文本矩阵覆盖（89 用例全绿）。
- OceanBase oracle 模式租户未部署，F15 仅做产物形态断言。

## 七、2026-10-20 function / lambda 补充审计

### 1. 本轮新修复

| 编号 | 缺陷（真库/单测复现） | 修复 |
|---|---|---|
| F16 | `date_format`/`to_char` 首参为字符串日期字面量时：Oracle 报 ORA-01722（按数字隐式转换）、PG 报 `to_char(unknown,unknown) is not unique`、H2 报 `Invalid value for parameter dateTime` | 字面量显式定型：Oracle 包 `to_date`、PG 加 `::timestamp`、H2 `CAST(... AS TIMESTAMP)`；数值格式模型（`999.99`）不受影响 |
| F17 | `left/right(str,0)` 与负数长度：Oracle 的 `substr(x,-0)` 返回整串、`substr(x,1,0)` 返回 NULL，SQLite `substr(x,-0)` 返回整串；MySQL 返回空串 | 长度字面量 `<=0` 统一转 `substr(x,1,0)`（SQLite 得空串、Oracle 得空值，至少不返回整串） |
| F18 | `mid(...)` 到 SQLite 报 `no such function: mid` | SQLite 上非 `substr` 拼写统一换名为 `substr` |
| F19 | `to_date(str,'...hh24:mi:ss')` 在 PG/HANA/CH/SQLite 只返回日期，时分秒被静默截断 | 格式含时间 token 时改用 `to_timestamp`（PG/HANA）、`toDateTime`（CH；字符串字面量按 `SqlToyConstants.getZoneId()` 解析）、`datetime`（SQLite） |
| F20 | `substr(str,0,n)` 各库 0 语义不同：MySQL 空串、Oracle 当 1、PG/SQLServer 少取 1 个、DB2 报 SQL0138 | 按“0=从开头截取”统一：字面量 0 归一为目标库首位（当前接管的方言首位均为 1），全库 `substr(x,0,n)` 等价 `substr(x,1,n)` |
| F21 | `find_in_set('a,b','a,b')`：MySQL 返回 0（首参含逗号不是完整元素），改写形态却命中 | 字面量首参含逗号时直接按未命中返回 `(0>0)`/`(0=0)`；列/占位符运行时含逗号仍属 MySQL 文档标注的不可用用法 |
| F22 | `find_in_set` 在投影/位置比较场景：改写只返回 boolean，SQLServer/HANA/DM 的 SELECT 列表报语法错，`=2` 等位置比较不被 suffix 消费 | 增加投影上下文识别：投影返回整数元素序号（补逗号定位→逗号计数还原），`=1/=2/>1/<3` 等按序号比较；条件裸用法仍返回 boolean |
| F23 | `instr(str,sub,pos,occurrence)` 四参在 SQLServer/MySQL/OB/PG/openGauss 等报参数个数错误 | 两档适配：① Oracle/DM/DB2/H2/HANA 原生四参透传；② SQLServer 2025/MySQL 8+/OceanBase/PG 15+/openGauss/GaussDB 套用 `REGEXP_INSTR`；其余（MySQL57/TiDB/Doris/StarRocks/PG14/SQLite/ClickHouse/Kingbase 等）原样输出交目标库报错 |
| L1 | `groupBy(空集合)` 生成 `GROUP BY ` 非法片段 | 空集合直接跳过 |
| L2 | `having("")` 生成 `HAVING ` 非法片段 | `condition=true` 且 SQL 空白时快速失败 |

回归断言：`FunctionDefectRegressionTest#dateLiteralTyping/leftRightNonPositive/midOnSqlite/toDatePreservesTime/substrZeroStart/instrFourArg`、
`FunctionFixRealDbVerificationTest#substrZeroStartUnified`（全库 `substr(...,0,2)` 均得 `ab`）及
`#findInSetProjectionOrdinal`（全库元素序号 1/2/0 与 `=2` 命中）、`#instrFourArg`（全库四参返回 4）、`#db2Matrix`、
`FindInSetDialectTest#literalWithCommaIsNotAnElement/positionComparisonSuffixSupported/projectionReturnsIntegerPosition`、
`LambdaDefectRegressionTest#p3EmptyGroupBy/p3BlankHaving/p2LikeUnderscoreEscapedByFramework`。

`find_in_set` 的投影/条件判定按函数左侧最近的顶层关键字：`select/then/else/order|group by`、
逗号或运算符 → 投影（返回整数元素序号）；`where/having/when/on/and/or/not` → 条件（返回
boolean）；无法判定时按条件处理，保持既有 boolean 产物。投影序号由“补逗号子串的字符位置”
经逗号计数还原（`a,b` 中 `b` 的字符位置 3 → 元素序号 2），并支持 `=1/=2/>1/<3` 等位置比较。

`instr` 四参第二档的 `REGEXP_INSTR` 第二参按目标库正则解释：字面量含 `. * + ? ( ) [ ] { } ^ $ | \`
时需自行转义（SQLServer/MySQL 也可用 `\Q...\E`，PG/openGauss 用 `\.` 等）；负起点不在第二档支持范围。

ClickHouse `to_date` 含时间时，字符串字面量生成 `toDateTime(literal, '<zoneId>')`，时区取
`SqlToyConstants.getZoneId()`，不再依赖服务端 UTC 与 JDBC `use_time_zone` 是否一致；列/占位符
仍生成 `toDateTime(x)`，避免对已有 DateTime 值做二次转换。

### 2. 复测结果

- function 文本矩阵：**97 tests, 0 failures**
- function 真库专项：**15 tests, 0 failures**（Oracle/PG/HANA 的字面量日期格式化与含时间 `to_date` 已加入真库断言）
- 既有 9 库真库冒烟：**38 tests, 0 failures**
- lambda 全部套件：**172 tests, 0 failures**

关于 lambda `like` 的下划线：lambda 层只负责补 `%` 通配符（绑定值为 `%a_b%`），执行期
`SqlConfigParseUtils.processLike` 会把 `_` 转义为 `\_` 并按方言追加 `ESCAPE`；H2 真执行
验证 `like(name,'a_b')` 只命中 `a_b`、不命中 `axb`（见 `LambdaDefectRegressionTest#p2LikeUnderscoreEscapedByFramework`）。

### 3. 仍存在的边界（本轮未改，按使用场景选择）

| 项 | 现象 | 说明 |
|---|---|---|
| `find_in_set` 首参含逗号（非字面量） | 字面量形态已由 F21 按 0 处理；列/占位符运行时含逗号仍按“补逗号+子串定位”，可能误命中 | MySQL 文档明确该用法不可用；业务侧应保证首参不含逗号 |
| `instr` 四参在不支持的库 | MySQL57/TiDB/Doris/StarRocks/PG14/SQLite/ClickHouse/Kingbase 等既无原生四参、也无 `REGEXP_INSTR` | 原样输出交目标库报错；需要时用嵌套 `CHARINDEX`/`LOCATE`/`strpos` 或 CTE/UDF |
| `length('abc ')` | SQLServer `LEN` 忽略尾随空格（3），MySQL/Oracle/PG 为 4 | 需要严格字符数请改用 `datalength` 或业务侧处理 |
| OceanBase `oceanbase` 方言 | CE mysql 模式下 `to_date`/`datediff` 等 oracle 系产物报函数不存在 | 沿用既有约定：mysql 模式租户显式配 `dialect=mysql`（verify 工程即此配置） |

补充注意：

- `find_in_set` 的投影/条件判定基于函数左侧最近的顶层关键字，属于启发式；像
  `where coalesce(find_in_set(...),0)` 这类嵌套在值表达式里的场景无法完全判定，默认按条件
  （boolean）处理。确定是投影时建议外层显式写 `select (find_in_set(...) + 0)` 或独立子查询。
- `find_in_set` 的投影序号表达式包含 `substr/replace/length/coalesce`，列表很长时比原生
  `find_in_set` 重；WHERE 布尔用法仍走轻量定位。
- lambda `setExpr` 表达式内不允许出现 `=`（框架负责赋值等号）；`in(空集合)` 按文档不参与组装，
  查询会退化为无该条件，更新/删除则仍受无条件守卫保护。