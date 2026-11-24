# sqltoy 6.0.10 发版说明

**发布日期：2026-11-12**

本版本是 6.0.x 系列的一次重点迭代：**函数跨库适配体系大扩充**（五族新转换器、epoch 族会话时区对齐、split_part 全保真重写等，全部经 docker 多库真库对拍实证，新增测试超 300 例）、**新增 TDengine 3.3.x 方言支持**、sqlite 方言锁语义与日期双形态完善、DB2 无 FROM 查询自动补虚表；同时修复了分页 count、多租户拦截、DDL 生成器、Spring Boot fat-jar 加载等一批经真库实证的缺陷，并完成依赖全面升级与废弃方法体系化收口。

> ⚠️ 本版本有 **3 处行为口径变化**（详见「升级注意事项」）：`to_timestamp` 单参口径、`BeanUtil` 整型文本转换口径、RSA 字段解密失败行为。升级前请务必评估存量数据。

## 一、新特性

### 1. 函数跨库适配体系大扩充

**新增五族转换器**：

- `substring_index`：mysql 系原生透传；oracle 系转 `instr`（支持 occurrence 与负向定位）；db2 转 `locate_in_string`；pg 系转 `string_to_array` 切片，边界组合以显式 case 兜底；
- `unix_timestamp` / `from_unixtime` 互逆族；
- `truncate`：oracle 系与 `trunc` 反向互转；sqlserver 转 `round(x, d, 1)`；hana 转 `ROUND_DOWN`；
- `current_date()` 带括号形态：db2 转 `CURRENT DATE` 关键字体；
- `week` / `weekday` / `dayofweek` 族：week 按 MySQL mode0 锚点法精确实现，weekday/dayofweek 按 2000 纪元锚取模并归一负数——不依赖 sqlserver `@@datefirst` 与 oracle NLS 会话参数，跨库结果一致。

**反向方言交叉多源化**——源函数拼写不再只认 MySQL 形态：`datepart`（sqlserver 习惯）、`to_timestamp`（单参 epoch 为 PG 习惯，双参解析语义在 oracle/db2 原生透传）、`split_part`、数值 `trunc` 均可被识别转换，配套修复四处静默误转。

**`split_part` 全保真重写**：负索引（PG14+ 从末尾数）与正/负超界返回 `''` 的语义在 mysql 系/oracle 系/ClickHouse 上组合实现（oracle 以负 position 定位规避 ORA-01428，CH 以 `splitByString` + `arrayElement` 规避单字符分隔符限制）。

**epoch 族会话时区一致性**：MySQL 的 `unix_timestamp` / `from_unixtime` 按会话时区解释，各目标库统一对齐该口径——sqlserver/db2 减加会话偏移（db2 `CURRENT TIMEZONE` 为 hhmmss 三段换算）、Oracle 经 `from_tz` / `sys_extract_utc` 按入参日期应用会话命名时区 DST 规则（固定偏移对历史夏令时日期会偏一小时）、H2 修正其 `extract(epoch)` 对无时区类型恒按 UTC 的口径、PG 系全族统一 `::timestamptz`、sqlite 按列 TEXT/INTEGER 双形态判别、DM 实测原生自带且时区感知正确归原生透传（含 fmt 形态）、HANA 以 `seconds_between` / `add_seconds` 组合补偿。

**其他**：

- `DateTrunc` / `truncate` 第二参引号语义按目标方言分流（mysql 系双引号是字符串，日期单位拦截防止静默截断时分秒）；
- ClickHouse 专项：`unix_timestamp` → `toUnixTimestamp`、`from_unixtime` → `fromUnixTimestamp`；lambda 侧裸 UNION 按 CH26 `union_default_mode` 渲染 `UNION DISTINCT`；
- `toMySqlFormat` 的 `mm` 按"最近 directive 为 `%H` 跨字面分隔符"判定为分钟（java 时间上下文），其余为 Oracle 月份。

以上全部经 **docker 多库真库对拍实证**，新增测试**超 300 例**。

### 2. 新增 TDengine 3.3.x 方言支持

查询、分页、随机取数、count 走通用路径，对象插入与超表标签写入经命名参数透传。

### 3. sqlite 方言增强

- **锁语义统一降级**：sqlite 单写者模型下，`FOR UPDATE` 类锁请求全部跳过为普通读（锁谓词在 sqlite 恒为假，乐观锁 CAS 由 `UPDATE ... WHERE` 承担）——不再抛异常挡住"MySQL 逻辑在 sqlite 上测试"的常规用法；
- **日期函数 TEXT/INTEGER 双形态运行时判别**：ORM `setTimestamp` 写入的毫秒 INTEGER 自动 /1000 归一，外部工具写入的 TEXT 按本地墙钟解析——修复此前对 TEXT 列数值化导致 week 族/unix_timestamp 静默错值的问题。

### 4. DB2 无 FROM 常量查询自动补虚表

`select 99` 类自定义 countSql 自动补 `SYSIBM.SYSDUMMY1` 虚表：字面量内的 from 不误判（掩码判定）、只认顶层 FROM、已带 `with ur` 不重复追加；VALUES 形式查询本身合法不补。

## 二、缺陷修复

### 1. DDL 生成器（真库实测）

- **DB2**：主键列显式 `NOT NULL`（否则建表 SQLCODE=-542）；
- **Doris / StarRocks**：无 TIMESTAMP 类型映射 DATETIME，OffsetDateTime 在 Doris 映射 TIMESTAMPTZ；两库不支持列内联 PRIMARY KEY，无 `@MppTable` 时按键模型收口（Doris UNIQUE KEY + hash 分布 / StarRocks PRIMARY KEY），UNIQUE/AGGREGATE 键模型未显式指定分布时按键列 hash（默认随机分布实测报错）；键列按键序前置为 schema 有序前缀（实体 `@Id` 非首位时自动重排，注解 orderBy 与实体列名大小写错配时仍正确前置）；
- **StarRocks PRIMARY 模型键列强制 NOT NULL**（真库实测 "primary key column[id] cannot be nullable"），orderBy 排序键列同样强制，非键列保持可空；Doris 各模型键列可空合法，保持原语义（避免过度修复破坏存量写入）。

### 2. 分页与随机取数对集合运算语句的包裹

- sqlserver 的 findTopBySql / findPageBySql 无 order by 时的派生表包裹判定由只认 union 扩展为 union/intersect/except/minus 全集；
- ClickHouse / Impala 上 `limit/offset` 会绑定集合尾部 SELECT（实测 `A except B limit N` 的 N 只修饰 B），现先包派生表再 limit；
- 集合运算判定排除 `minus(a,b)` 等函数调用形态（关键字后紧跟括号且内容非 select）——修复 ClickHouse 的 `minus()` 算术函数被误判为集合运算致分页误包派生表（内层重复列名报 Duplicate column name）；count 改写守卫、top/random 包裹判定三条链路同步对齐，`except(select ...)` 等集合写法不受影响。

### 3. 分页 count 与多租户

- **count 改写器识别 minus/except/intersect**：此前不识别致分页记录数静默失真；顶层 `group by + having` 子查询不再误判为内层分组；
- **分页 count 缓存会话身份**：schema/catalog 改为每次从当前连接读取——同一连接池 `setSchema`/`setCatalog` 切换多租户时不再钉死首次会话致 count 串用；账号读取失败不缓存；
- **并行分页 count 租户上下文**：count 的 SQL 准备（参数求值 + 租户/权限拦截器）移至调用线程完成，工作线程只做 JDBC 执行——应用租户上下文通常是普通 ThreadLocal（TTL executor 也只传播 TransmittableThreadLocal），修复前工作线程构建 count SQL 丢租户过滤、count 值失真且登记进含租户 key 的缓存造成跨租户串用（H2 真库 A/B 实证：修复前两租户 count 均为全局 8，修复后 5/3 正确）；
- **租户拦截器分页尾缀定位**：无 where 的实体分页 SQL，租户条件插在 limit / offset rows / fetch first 之前——原实现只考虑 group/order by，条件 concat 到 limit 之后报语法错（H2 真库复现）；尾边界改零宽前瞻，兼容 `limit ?` / `limit 10 offset 20` 结尾（无尾随空白）的 top SQL 形态。

### 4. 函数转换口径修正（真库实证）

- **单参 `to_timestamp`**：Oracle / DM / OB（Oracle 模式）的 `to_timestamp(列/占位符)` 按原生文本解析透传（原误按 PG epoch 语义转 `numtodsinterval` 报 ORA-01722）；数值字面量（含引号数值）epoch 意图明确仍按 epoch 转换（一刀切透传会报 ORA-00932）；DB2 保留 epoch 转换（真库 v12.1.5 实测单参原生解析对常见格式一律 SQLCODE=-20448 不可用）；
- **week / weekday / dayofweek 族字面量定型**：Oracle/DM/OB 字面量日期入参包 `to_date` 规避默认 NLS_DATE_FORMAT 下 ORA-01861（与 DateDiff/Extract 口径对齐）；DB2 字面量 cast 为 date/timestamp 定型（裸串 `days()`/`dayofweek()` 报 SQLCODE=-182）；
- **OceanBase 方言归属统一**：unix_timestamp / from_unixtime / substring_index（含 split_part）/ truncate / week 族及 DateTrunc / FindInSet 统一归 Oracle 系（`dialect=oceanbase` 即 Oracle 模式租户，MySQL 模式应配 `dialect=mysql`），与 DateDiff / GroupConcat 的既有框架约定一致。

### 5. Spring Boot fat-jar 重复加载修复

- 嵌套根已覆盖外层 app.jar 时，`java.class.path` 不再补外层根——同一 SQL 不再以 nested / 外层 jar 两种 URL 形态各命中一次（`breakWhenSqlRepeat` 默认 true 下重复 sqlId 会阻断启动）；仅 lib 嵌套根时不漏 classes 资源；
- `SqlXMLConfigParse` 打开嵌套 jar 资源流的策略与扫描侧对齐：嵌套 jar 的 JarFile 由加载器缓存共享，不再无条件 `setUseCaches(false)`，规避流关闭连带关闭共享 JarFile 的 "zip file closed" 风险，普通 jar 行为不变。

### 6. 其他缺陷修复

- `BeanUtil` primitive long 经 Double 中转致雪花 ID（>2^53）静默失真，改精确解析；
- gaussdb 系 `Long[]` 数组绑定类型不一致；
- `findByJdbcQuery` setter 异常时关闭已创建的 statement；byte[] 属性 String 绑定显式 UTF-8；
- lambda 组合共享成员栈式归还：成员同时被外层 union 与内层 CTE 共享引用时，内层渲染结束恢复外层已下发的 dbType 与递归标记（原无条件置 null/清除致外层随后渲染回落 -1 方言失配）；递归标记覆盖递归成员自身的 CTE/@fast 内层（原只遍历 unions，DB2 嵌套场景漏标隐式 JOIN 改写报 SQLCODE=-345），身份去重防共享成员重复记录与循环引用。

## 三、性能与稳定性优化

- **loadAll 级联装配 O(n×m) 改索引分组 O(n+m)**；link 多列结果集列元数据提出行循环；
- **缓存类加载语义**：cascadeModels / dtoEntityMapperCache 等按 Class 对象的两级缓存改用 `ClassValue` 承载——应用类卸载即条目回收，静态 Map 不再强引用已卸载类的 Class 对象（经类卸载 / 8 线程并发 / 真实注解 / 反向对照四维度深测）；
- 六处缓存键由类名改为 Class 对象身份，规避热部署类重载残留；两处方法匹配缓存加容量上限；
- `CollectionUtil.putBounded` 超限回收加 CAS 单飞门闩：原实现每个写入线程各自回收 1/4，并发突发时过度淘汰近乎清空缓存（A/B 实证：16 线程突发旧键存活 177/400 → 修复后精确 300/400）；现同一时刻仅一个线程按水位循环回收至 3/4，容量为软上限，读取路径无锁不变；codeSqlCache、BeanUtil 方法与映射计划、convertSqlMap 等 5 处消费缓存统一受益；
- 分页会话身份元数据读取 catch Throwable（老驱动 getSchema 抛 AbstractMethodError）；lambda 同层重复成员下发身份去重防恢复残留；`clearDisturbSql` 免重复掩码；`BeanUtil.toIntegerText` 对巨指数 double 防护。

## 四、工程治理

### 1. 依赖升级与 pom 收口

- 依赖版本全部升级至当前最新正式版，并收口 properties 统一管理；
- orm-core 数据库驱动收口 test 作用域（不再传递给使用方）；spring 模块清理无用依赖（社区反馈 #IKUCI2）。

### 2. 废弃方法体系化收口（行为全部等价，零 API 移除）

- USF 锁游标回读迁移至 `processResultRow` 新签名——`ResultColumnMeta[]` 随 usfTypeNames / usfKinds 在行循环外查询级一次构建（原走废弃桥接签名每次回读逐列重建列元数据）；自此废弃签名无任何框架内部调用方，仅保留第三方兼容；
- 废弃 shim 委派收口，消除与现行方法重复的赋值体：`TreeTableModel.rootId → pidValue`、`Update.forceUpdateProps → forceUpdateFields`、`SpringAppContext.setContext → setApplicationContext`（spring 与 starter 双份），三者全仓均无子类覆写，多态路由不变；
- javadoc 迁移指引：`LightDao.update()` 链式示例改用 `forceUpdateFields`、`SqlToyLazyDao.cacheMatchKeys` 废弃签名补 `@see`；
- core 经 javac deprecation 扫描零 "uses or overrides a deprecated API" 警告（框架内部不再调用任何废弃方法）。

## 五、升级注意事项

1. **`to_timestamp` 单参口径**：Oracle / DM / OB（Oracle 模式）的 `to_timestamp(列/占位符)` 按原生文本解析（与 6.0.9 一致），数值字面量（含引号数值）保留 epoch 转换；**epoch 列/占位符场景请改用 `from_unixtime`**（如 `from_unixtime(:epoch)`），否则 Oracle 报 ORA-00932、DM 报 -6118。DB2 保留 epoch 转换。MySQL / PG 系行为不变。
2. **`BeanUtil` 数值转换口径收窄**：整型文本转换不再经 Double 中转——越界值（如 `"99999999999"` 转 int 原静默饱和为 2147483647）、NaN（原静默为 0）、`"10d"` 类后缀（原静默解析）现显式抛 NumberFormatException；雪花 ID 等 >2^53 大整数保精度。正常小数/科学计数仍向零截断、首尾空白容忍。**存量数据含越界 / NaN 值时，查询将由"静默错值"变"报错"，升级前请清洗或调整字段类型**。
3. **RSA 字段解密失败抛异常**（6.0.6 引入，非本次新变更，提示从更老版本升级的用户）：库中未加密历史明文 / 换钥数据会使查询链路硬失败（原静默返回 `""` 与空值不可区分），升级前须完成新密钥重加密迁移或清洗历史数据；加密失败同样抛异常阻断写入（原返回 `""` 落库致原文永久丢失）。

## 六、Maven 坐标

```xml
<dependency>
    <groupId>com.sagframe</groupId>
    <artifactId>sagacity-sqltoy-spring-starter</artifactId>
    <!-- solon 适配版本 artifactId 为 sagacity-sqltoy-solon-plugin -->
    <!-- 传统 spring 项目为 sagacity-sqltoy-spring -->
    <!-- 单纯 sqltoy 为 sagacity-sqltoy -->
    <!-- jdk8 对应的版本号为：5.6.97.jre8（core 与 6.0.10 全量同步，同样支持 quickvo-maven-plugin 插件） -->
    <version>6.0.10</version>
</dependency>
```

## 七、其他

- 函数适配扩充配套新增测试**超 300 例**，全部经 docker 多库真库对拍实证；
- 发版前经三批内部缺陷检查与一轮外部评审（红→绿 A/B + 真库实证）复核修复；
- 配套的 quickvo-maven-plugin 升级至 **2.0.6**（项目地址：https://gitee.com/sagacity/maven-quickvo-plugin，完整功能与配置说明参见其 README）。

---

**反馈渠道**：QQ 交流群 **531812227** ｜ [Gitee](https://gitee.com/sagacity/sagacity-sqltoy) ｜ [GitHub](https://github.com/sagframe/sagacity-sqltoy) ｜ [GitCode](https://gitcode.com/sqltoy/sagacity-sqltoy)
