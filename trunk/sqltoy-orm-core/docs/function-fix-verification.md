# function 模块缺陷修复与真库验证清单

> 记录时间：2026-10-19
> 范围：`sqltoy-orm-core/src/main/java/org/sagacity/sqltoy/plugins/function`
> 本机验证：SQL 文本单测 + H2/SQLite 真执行；MySQL/SQLServer/Oracle/PG/DB2/DM/OB/HANA/CH 需回家真库确认。

## 一、已修复内容

| 编号 | 缺陷 | 修复点 | 回归用例 |
|---|---|---|---|
| F1 | Oracle 小写 `mm`（月）被当分钟 | `ToChar`/`ToDate` 按 Oracle 模型区分 `mm`(月)/`mi`(分) | `FunctionDefectRegressionTest#oracleLowercaseMonth` |
| F2 | Oracle `HH`（12 小时）被当 24 小时 | `ToChar`/`ToDate` 裸 `HH` 映射 12 小时，`HH24` 保留 24 小时 | `#oracleTwelveHour` |
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

同时更新了受影响的既有断言：

- `CommonFuncDialectTest#instrMultiSpellingMatrix`：`posstr` 改为 DB2 的 haystack-first 语义。
- `FunctionConvertMatrixTest#dateFormatJavaStandaloneHour`：`HH24` 大小写等价断言。

## 二、本机已完成的测试

```powershell
# 纯 SQL 文本 + H2/SQLite 真执行，无需外部数据库
mvn -o -Dtest=FunctionDefectRegressionTest test

# 全部非真库函数测试
mvn -o -Dtest=FunctionConvertMatrixTest,FunctionDialectFullMatrixTest,CommonFuncDialectTest,FunctionAuditProbeTest,FunctionRetestProbeTest,FindInSetDialectTest,KingbaseFunctionConvertTest,FunctionDefectRegressionTest,GetDialectSqlEdgeTest,FunctionUtilTest test
# 结果：88 tests, 0 failures
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
| `select to_char('2026-03-20 14:30:45','HH:MI')` | `select date_format(...,'%h:%i')` | `02:30` |
| `select to_date('2026-03-20','yyyy-mm-dd')` | `select STR_TO_DATE(...,'%Y-%m-%d')` | `2026-03-20` |
| `select date_format('2026-03-20 14:30:45','yyyy-MM-dd HH:mm:ss')` | `select date_format(...,'%Y-%m-%d %H:%i:%s')` | `2026-03-20 14:30:45` |
| `select trim(from '  x  ')` | `select trim(both ' ' from '  x  ')` | `x` |
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

## 五、本次未覆盖/需真库确认的项

1. **DB2 小写 Oracle 格式模型**：`ToDate` 两参对 DB2 是原样透传，`yyyy-mm-dd` 是否被 DB2 `TO_DATE` 接受需真库确认；如不接受，需在 DB2 分支统一转大写 token。
2. **DM `concat` 多参**：需确认 DM 的 `CONCAT` 是否只支持两参；若只支持两参，`Concat` 需把 DM 加入三参以上 `||` 转换。
3. **OceanBase `datediff(unit,...)`**：需确认 OB 的 MySQL/Oracle 模式能力，再决定归入 MySQL 分支还是 Oracle 分支。
4. **`find_in_set(...)=1`**：按既有测试是“有意不消费比较符、交目标库响亮报错”，不是本次缺陷；真库请按现有 `FindInSetDialectTest` 的边界理解。
5. **SQL Server 多列 `group_concat` 的真实执行**：已改为 `concat` + null 守卫，建议用数值列和字符串列各验一次（字符串拼接、数值转字符串、任一 null 返回 null）。
