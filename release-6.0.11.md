# sqltoy 6.0.11 发版说明

**发布日期：2026-11-19**

本版本聚焦三大方向：**多上下文/热部署场景的资源治理**（静态共享全面收口）、**LIKE 转义与 SQL 解析的实证修复**（过滤器链/lambda/中央解析链口径统一）、**Elasticsearch rest5 客户端统一**；同时补齐 Solon 插件参数配置、修复 JWT 缓存与虚表宏等一批问题。无破坏性变更（仅 DAO 接口新增 updateFetch，直接实现 LightDao/SqlToyDao/SqlToyLazyDao 的扩展方需补充实现）。

## 一、主要更新

### 1. 多上下文 / 热部署资源治理（本版重点）

- **翻译缓存管理器、动态缓存管理器、主键生成器去 JVM 级共享**：ehcache/caffeine 翻译缓存、FIFO 动态缓存（含检测线程）、IdGenerator 全部随 `SqlToyContext` 实例隔离——多上下文/热部署时不再串用数据、互不销毁影响，检测线程按需启动；
- **按类/类名的静态缓存统一改 `ClassValue`**：BeanUtil/EnumUtil 的方法匹配、映射计划、无参构造器等，MapperUtils 字段别名、`@Translate` 注解配置——条目随 Class 卸载回收，热部署不再逐代泄漏；`enumClassMap` 改用 TCCL 解析；
- **JVM 级静态配置首占口径**（`functionConverts`/`keywordSign`）：setter 仅记录、initialize 阶段首占生效，初始化失败自动释放首占权（安全配置不再被失败上下文永久锁死）；
- **insert/merge 静态 SQL 缓存 key 补齐上下文/连接维度**：多上下文、多数据源（MySQL 与 OceanBase 共存等）不再串用 SQL。

### 2. Elasticsearch：统一 rest5 客户端

- 单地址/多地址/eql 自定义 url 行为一致；连接池 keep-alive 复用、陈旧连接自动重试一次；
- 网关子路径（base path）由 pathPrefix 承载，url 已带 `/_sql`、`/_search` 尾缀自动剥离；
- **超时与字符集"endpoint 默认 + 语句覆盖"**：eql 上 `request-timeout`/`connection-timeout`/`socket-timeout`/`charset` 优先，未配置取 ElasticConfig——修复此前"XML 属性只对单地址生效、charset 完全无效"的静默分叉。

### 3. LIKE 转义与 SQL 解析

- **新增 `<like>` 过滤元素**：`mode`（both/left/right）、`escape-mode`（raw/legacy）、`escape-percent`、`append-str`，并统一 LIKE 转义（按方言追加正确的 ESCAPE 子句）；
- **like 参数计数忽略字面量内 `?`**（`'50?'` 不再导致参数对位错乱）；**反斜杠奇偶判定**区分通配符 `%` 与字面量 `%`；**lambda like 族**补 `%` 前先双写反斜杠——过滤器链、lambda、中央解析链三方口径统一，H2 真库矩阵验证；
- **SQL 解析反斜杠方言体系**：CTE 括号配对、函数参数切分按方言区分 `\'` 转义语义（MySQL 系 vs 标准 SQL），修复 CTE 截断/越界；
- **hasOrderBy 误判修复**：`order by nvl(a,0)` 等排序表达式自带括号不再被误判为无排序（避免 Oracle 11g 分页错行）。

### 4. Solon 插件参数补齐

- 新增 `solon.sqltoy.jsonProvider` 配置（与 spring 同语义）；
- `taskExecutor` 线程池 8 项参数全部生效：未配置时构建专属命名线程池（TTL 上下文传播、插件 stop 时统一关闭），显式 `none` 不建池；
- `DbManager` 上下文切换时清空缓存的 DAO 并告警。

### 5. 其他修复

- **JWT token 缓存**：过期条目主动清理 + 容量上限（2000），修复刷新窗口内旧 token 无法覆盖；
- **@virtualTable() 兼容前置 from**：`select 1 from @virtualTable()` 在伪表族只输出一个 from、无 from 族连 from 一并剔除；
- **@loop / @secure-loop 空白过滤**：循环内引用的集合元素为 null/空白时跳过迭代（与循环变量同口径），`#[..] or #[..]` 模式不再残留孤立连接符；
- **DATE/TIME/TIMESTAMP 字符串参数**解析失败改为带值上下文的明确异常；**Short/Byte** 整型文本转换与 Integer/Long 同口径（越界显式报错）；
- **Boot fat-jar 嵌套根**按原始/URL 编码双形态比对（部署路径含空格/中文不再重复加载阻断启动）。

### 6. 特殊类型族跨库适配 / UUID / updateFetch / lambda 缓存翻译（2026-11-22 批次）

- **nativeType 特殊类型族跨库适配**：quickvo 按源库生成的 `nativeType`（json/jsonb/vector/geometry/uuid/array）解析期归族 + jdbcType 上调，DDL 与 merge cast 按**目标方言**翻译（pg 内核系保源精度、外部拼写归一、非 pg 目标承载映射），源库实体跨库部署不再产生非法 cast/DDL；
- **UUID 全链路**：insert/update/updateSaveFetch 回写（pg 内核系 `updateObject(UUID)`）、读回统一 String（`java.util.UUID` 字段经 `convertType` 还原）、32 位无连字符 hex 规范化、sqlserver `UNIQUEIDENTIFIER` 承载；
- **updateFetch**：锁查询存在则修改返回、不存在返回 null 不插入（`updateSaveFetch` 行为保持不变）；
- **lambda 编程式缓存翻译**：`translates(Translate...)`/`translate(cache,key,target)`/同列 `translate(cache,column)`，单表与 join 通道按结果列原地翻译，distinct/groupBy/投影/组合分支边界快速失败；
- **跨库绑定/DDL 真库修复**：vastbase boolean（`setObject(OTHER)` 全上下文）、hana boolean update cast、db2 timestamptz（`toLocalDateTime`）、oracle 数组 null/TIMESTAMPTZ 读归一、sqlserver 原生 VECTOR + NVARCHAR 中文、highgo BLOB（`isPgKernel` setBytes）、dm geometry→ST_GEOMETRY、h2 vector/TSTZ、doris/starrocks BLOB 承载。

## 二、⚠️ 升级注意事项（行为变化）

1. **Short/Byte 越界与非法文本**：原静默回绕/抛裸异常，现统一抛 `NumberFormatException`；存量数据含越界值时请先清洗；
2. **`functionConverts`/`keywordSign` 需在 initialize 前配置**：运行中调用 setter 不再即时改变 JVM 全局；
3. **翻译缓存管理器**的 `cacheManager` 字段由 static 改为实例字段——已编译的第三方子类需重新编译（接口与公共方法不变）；
4. **Solon 默认线程池**：未配置 targetPoolName 时构建专属池（替代原 commonPool 兜底，避免与并行流争抢）；显式 `none` 不建池；
5. **@loop/@secure-loop**：循环内引用集合的空白元素现在跳过整个迭代（此前空白条件保留/产生孤立连接符）。

### 已知限制（数据库/驱动能力边界，非框架缺陷）

- **vastbase boolean 的 updateSaveFetch 回写**：驱动 `updateRow` 内部生成裸 UPDATE 无法注入 cast，boolean 列 USF 回写不支持（insert/update/merge 均已支持）；
- **starrocks VARBINARY 更新的 null 语义**：coalesce 不支持 VARBINARY，采用直赋（directAssign），非 forceUpdate 的部分更新中 null 会覆盖原值（与 OceanBase vector 同类边界，业务侧需显式传值）；
- **doris BLOB 承载**：doris 无 BLOB/VARBINARY 建表类型，以 STRING 承载，setBytes 落库为 hex 文本，二进制不可无损往返；
- **sqlserver 原生 VECTOR**：仅 SQL Server 2025+ 支持，2022 及更早版本须 `@Column(nativeType=..)` 指定承载类型或升级。
- **openGauss/KingbaseES/Vastbase geometry**：geometry 能力依赖 postgis 兼容扩展；未装扩展时显式 geometry 列建表/merge 报 `type "geometry" does not exist`（生产安装扩展即正常，测试容器未装属环境限制；与 DM 固定 ST_GEOMETRY 不同，不宜一刀切收敛承载）。

## 三、Maven 坐标

```xml
<dependency>
    <groupId>com.sagframe</groupId>
    <artifactId>sagacity-sqltoy-spring-starter</artifactId>
    <!-- solon 适配版本 artifactId 为 sagacity-sqltoy-solon-plugin -->
    <!-- 传统 spring 项目为 sagacity-sqltoy-spring -->
    <!-- 单纯 sqltoy 为 sagacity-sqltoy -->
    <!-- jdk8 对应的版本号为：5.6.98.jre8（core 与 6.0.11 全量同步，同样支持 quickvo-maven-plugin 插件） -->
    <version>6.0.11</version>
</dependency>
```

## 四、其他

- 依赖全部为当前最新正式版，本版无依赖变更；
- core 模块全量 **2827 例测试全绿**（clean 门禁；含 like 过滤链/反斜杠矩阵、lambda like 族 H2、多上下文隔离、ClassValue 类卸载、特殊类型族跨库适配、UUID 全链路、lambda 缓存翻译、updateFetch 等回归）；verify 工程 16 库 CrossDbDeploy 跨库部署矩阵 + 12 库×9 套件综合矩阵全绿；
- 配套的 quickvo-maven-plugin 升级至 **2.0.7**（项目地址：https://gitee.com/sagacity/maven-quickvo-plugin，完整功能与配置说明参见其 README）。

---

**反馈渠道**：QQ 交流群 **531812227** ｜ [Gitee](https://gitee.com/sagacity/sagacity-sqltoy) ｜ [GitHub](https://github.com/sagframe/sagacity-sqltoy) ｜ [GitCode](https://gitcode.com/sqltoy/sagacity-sqltoy)