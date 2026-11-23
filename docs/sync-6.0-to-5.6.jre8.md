# 5.6.jre8 ← 6.0 core 全量同步机制

> 建立:2026-11-12。5.6.jre8 的 sqltoy-orm-core 定位为 **6.0 core 的 jre8 编译目标**:
> 以 6.0 发版 tag 为基线做全量文件同步,消除逐个回移的翻译风险,让 6.0 的测试直接背书。
> spring/solon 插件模块(Boot2/javax 生态)不迁移,按 core API 变化跟进适配。
>
> **首轮全量同步完成(2026-11-12)**:211 个文件(139M+71A+1D),测试矩阵 2196 例全绿。
> 适配点登记如下,后续每轮同步按清单检查是否需更新。
>
> **第二轮同步完成(2026-11-13,6.0.10 正式发版)**:18 个 main 文件(17 拷贝+DialectUtils 正则 hunk 合并)
> + 12 个本轮测试 + 补齐首轮遗漏的 SqlConfigParseUtilsTest(30 例 like 转义)/SqlUtilTest(整体替换为
> 6.0 进化版,新增 12 例 convertFieldsToCols 边界)+ 6.0 侧 8 个独有测试补入(5 个 jre8-clean 直接拷贝
> +SqlConfigParseUtilsProcessSqlTest/FileUtilCopyFolderTest/StringUtilEdgeTest 轻量适配)。
> 另:6.0 在历史 revert(ad2fb8863)中丢失的 BeanUtilTest/SqlToyConstantsTest 为 5.6 原生资产,保留不删。
>
> **对齐审计完成(2026-11-13)**:逐 hunk 核验全部残余差异,修正 5.6 侧无理由偏离——BeanUtil 级联缓存
> 恢复泛型、HttpClientUtils 的 doPostBody/wrapPath 注释对齐 6.0 原文并移植 wrapUrl 多地址取首节点
> 修复、ElasticEndpoint 移植 keystore 缺失显式报错、SqlConfigParseUtilsTest 以 6.0 当前文件重建
> (消除首轮适配引入的断言换行漂移,差异收敛为 9 处纯文本块转换)。审计后差异全景:main 11 文件
> (7 个纯语法适配+BeanUtil/JsonProviders/HttpClientUtils/ElasticEndpoint 四个登记适配)、test 14 文件
> (纯语法适配)、排除 8+原生 2,无任何业务逻辑分歧。
>
> **第三轮同步完成(2026-11-16,6.0.11 开发内容)**:6.0.11 的 20 条变更(反斜杠方言体系/like过滤元素/
> hasOrderBy深度复核/健壮性收口/主键生成器与insert缓存的多上下文隔离/JVM级静态配置首占/翻译缓存
> 与FIFO动态缓存去JVM共享/ClassValue体系化/类名缓存TCCL/JWT token缓存治理/@virtualTable前置from)
> 整体进入。main 24 文件纯拷贝+11 个登记适配文件复刻适配(BeanUtil 整体重放 record 剥离+switch
> 表达式/pattern instanceof/canAccess 转换);test 全量对齐 6.0 后统一转换(文本块 15 处、repeat 28 处、
> var 7 处、Map.of/List.of、readAllBytes 3 处、FileWriter(File,Charset) 2 处);TranslateCacheCheckersTest
> 随第 12 条删除其反射目标而移除;模块侧 spring/starter TranslateCaffeineManager 实例化(第 11 条)、
> solon DbManager 切换清空+告警与 SqlToyDbInjector 静态字段移除(第 13 条)、starter 补 junit 测试依赖、
> solon surefire 启用。门禁:core 323 类 2481 例全绿(82 例环境性跳过)+ starter 3 例 + solon 1 例 +
> spring 编译通过。发版时记得:bump 版本至 5.6.98.jre8、写 changelog、更新 README(本机无 JDK8,
> 本轮门禁以 `mvn -Dmaven.compiler.release=8` 在 JDK17 上完成,并对本地仓库安装了同步后的
> 5.6.97.jre8 供模块验证)。
>
> **第三轮复核完成(2026-11-16,仅 Java 代码)**:4 个结构适配文件签名 1:1(差异全部=登记适配);
> HttpClientUtils(409行)/ElasticEndpoint(342行)差异 hunk 级全量审阅=全部登记适配、无 11-13 后逻辑;
> 模块侧 40 个差异 Java 文件签名比对:36 个完全一致(javax/jakarta 基线),发现并回移 1 个存量缺口——
> starter 的 sqlInjectionRegexes 属性绑定(6.0 于 2025-5-15 增加,core 侧 setter 一直存在而 starter 漏移,
> Boot2 用户此前无法经 yml 配置 sql 注入表达式);其余差异为 6.0-only 的 AOT/lambda 资产(按基线不移植)。
>
> **第三轮增量补同步(2026-11-16 晚,solon 线程池参数补齐批次)**:复核期间 6.0 仓库又前进(15:15 pull+
> 16:14 提交,教训:每轮同步/复核前必须重新枚举树差异)——solon 侧"参数补齐"批次进入:新增 SolonTaskExecutor
> (默认线程池包装,TTL 上下文传播/关闭语义对齐 spring 的 ThreadPoolTaskExecutor)、Builder 在 targetPoolName
> 为 none/空时按 taskExecutor 参数构建默认线程池(**修复此前仅 targetPoolName 生效、threadNamePrefix/
> corePoolSize 等 8 项参数配置无效的缺陷**,对齐 spring starter 行为)、PluginImpl.stop 经类型识别关闭自建池、
> jsonProvider 装配补 null 守卫、SqlToyContextBuilderParityTest(5 例锁定测试)。已全部移植并验证:
> solon 模块 6 例全绿。TaskPoolProperties 字段两侧本就一致无需动;AOT 分支(strSplitTrim/scanResources/
> nativeimage 判定)按基线不移植。
>
> **第四轮同步完成(2026-11-18,loop 宏边界批次)**:6.0 当日 4 提交(SqlConfigParseUtils 的改动已被自身
> 后续提交回退,净变更为零)。实际进入:①SqlLoop 两处边界修复——`indexOf("#[")>0→>=0`(循环体首字符即#[ 时
> 被误判无段致有效条件全灭)+ 全空迭代跳过(段剔除后内容全空不拼 linkSign,修复"#[..] or #[..]"中间段消失
> 产生孤立 or/and 非法sql);②BeanUtil 的 Byte 转换归一(primitiveByteType/wrapByteType 两处,裸
> Byte.valueOf 改 enumToString+toIntegerText 归一体系,空串返回0/null,越界显式抛);③4 个测试
> (LoopDeepEdgeTest 15例/SecureSqlLoopFunctionalTest 更新19例/SqlLoopBlankRefResidueTest 3例/
> SuspectVerificationTest 6例)全部 jre8-clean 直接拷贝。门禁:326 类 2511 例全绿(82 例环境性跳过,
> 较上轮净增 30 例)。
>
> **第五轮同步完成(2026-11-19,6.0.11 正式发版日)**:6.0 侧 pom 定稿 6.0.11、changelog 扩写定稿。
> 实际进入:①**SqlToyContext 的 initialize 失败回滚**——新增 claimedStaticStateKeys 记录本context认领的
> JVM静态配置key,initialize 抛异常时经 STATIC_STATE_OWNERS.removeAll 释放首占权(否则失败的context永久
> 锁死 functionConverts/keywordSign/dialectConfig 等含注入正则在内的配置,后续上下文携带正确配置也无法
> 生效),原主体移入 doInitialize;②**FIFODynamicFetchCacheManager 清理顺序并发修复**——clear/remove 与
> 过期检测路径统一"先删过期登记再清数据"(原顺序会把并发重建写入的新登记误清/误跳过数据删除);③**BeanUtil
> 原生byte/short 空串/null 窄化修复**(3处)——null段新增 primitiveShortType 特判、case25/case29 空串
> 返回 (short)0/(byte)0(原 Integer 装箱 0 无法经 Method.invoke 窄化赋给 byte/short 形参,
> setProperty 抛 IllegalArgumentException);④**TranslateCaffeineManager.destroy 补 cacheNameSet.clear()**
> (spring/starter 双份)——否则 init 重建 manager 后 put 跳过 registerCustomCache,keepAlive/heap 配置
> 静默丢失;⑤**solon targetPoolName 三分支语义对齐 spring**——默认值 "none"→null,未配置/空时建默认池、
> 显式 "none" 不建池回落 core 的 commonPool(原 none/空都建池,与 spring starter 语义不一致)。
> 新测试:SqlToyContextInitFailureRollbackTest、BeanUtilPrimitiveBlankAssignTest、ScopeTest 增
> rebuildKeepsCustomCacheSpecs、ParityTest 重构为 Unset/None/Blank 三态。门禁:core 全量+starter 4例+
> solon 7例+spring 编译。
>
> **第六轮同步完成(2026-11-19 晚,LIKE 转义顺序批次)**:中央解析链与 lambda like 族的转义顺序缺陷修复——
> ①SqlConfigParseUtils.processLike 判定"值是否已含未转义%"由 replace("\\%","") 改为反斜杠奇偶扫描
> (\\\\%中的%是未转义通配符,旧实现误剥离致 right 模式尾部\\值先转义再补%后被误判无通配符、前缀匹配退化
> 为全模糊);②AbstractWhere 的 wrapLike/exactLike 对齐过滤器"先转义值最后补%"同序(修复 like/notLike/
> likeRight/likeExact* 尾部\\把补的%转义为字面量,单表与join双通道共用同获修复);③HttpClientUtils 的
> index 拼接防重复(url已指向/idx/_search时pathPrefix已含index,endsWith守卫跳过拼接,避免/idx/idx/
> _search)——hunk级合并至hc4形态;④DataSourceUtils 配套。测试:LikeFilterChainParityTest(6例)与
> LambdaLikeBackslashH2Test(10例)新增,**List.of→Arrays.asList 转换(10+13处)后进入适配清单**;
> UriTest 拷贝6.0版后恢复hc4异常import(新增2例index防重复);ComboEdge增3例、LikeFilterElementTest增1例。
> 门禁:core 330类 2541例全绿(82例环境性跳过,较上轮净增22例)。
>
> **第七轮同步完成(2026-11-23,6.0.12 正式发版内容,版本 5.6.99.jre8)**:5.6.98.jre8 已于 2026-11-19 发布(内容=6.0.11 同步),
> 故本轮版本号升为 **5.6.99.jre8**(上游 release-6.0.12.md 写的"jdk8 对应 5.6.98.jre8"已过时)。gitee 6.0.11..HEAD 共 76 文件
> (+5171/-186):数组参数绑定/回写/日志拟合全链路、nativeType 特殊类型族(NativeTypeUtils 解析期归族+消费点方言翻译,
> FieldMeta/ColumnMeta 新增 specialFamily/arrayComponentType)、单实体 updateFetch、lambda 编程式缓存翻译、跨库 DDL/cast/
> boolean/UUID/时区修正。做法见下方「同步流程」的分类法:干净基线文件整体拷贝(main 34 含新增 NativeTypeUtils、test 19 含
> lambda-cache-translate.xml、模块 2 个 SqlToyDaoImpl),登记适配文件三方合并——main 3 个(DialectUtils +27/-1、
> PostgreSqlDialectUtils +86/-2、BeanUtil +23/-8)+模块 dao impl 6 个(各 +6),两条不变量全部校验通过。测试仅 4 处 JDK9 写法
> (Set.of×1、List.of×3);**OracleTzNormalizeTest 按驱动能力改写(见适配清单:ojdbc8 19.24 无无参 offsetDateTimeValue(),
> 本线 Oracle TIMESTAMPTZ 读回归一不生效)**。pom 无依赖变更(上游 spring-framework 7.0.11/spring-boot 4.1.3 不适用本线)。
> 门禁:core clean 全量 347 类 2714 例全绿(82 例环境性跳过);与同步前提交(008ca5b62,独立 worktree clean 实跑 330 类
> 2583 例)逐类比对——+131 例全部归因(17 个新测试类 127 例 + DDLConvertTypeMatrixTest 7→11),无类丢失、既有类计数零变化;
> starter 4 例、solon 7 例、spring 编译通过。合并后全景:trunk 下 930 文件 836 个与上游逐字节一致,差异 92 个(本轮 17 +
> 既有 75)、上游独有 36、jre8 独有 2,全部有归属。

## 同步流程(每个 6.0 release tag 执行一轮)

1. `git fetch` 6.0 仓库,锚定 tag
2. 按本清单镜像 core `src/main`(java+resources) + `src/test` 到 5.6,排除「排除清单」文件
3. 应用「适配清单」的定点修正
4. 合并 pom 依赖(按「pom 差异基线」jre8 化)
5. 编译门禁:JDK8 全量编译 + 全量测试(2196例)+ spring/starter/solon 模块编译
6. 更新本清单(新增的适配点登记)

**分类法(第七轮起,替代人工逐文件判断)**:gitee 与 github 两仓库的提交 SHA 不通(同一 tag 两边 SHA 不同),不能靠 git 祖先
关系求差异;两边均 `core.autocrlf=true`(仓库内为 LF),故用 `git ls-tree -r` 的 **blob 哈希逐文件比对**:

| 类别 | 判定 | 处置 |
|---|---|---|
| A 干净基线 | 上游本轮变更 且 jre8 blob == gitee@上个 tag blob | 整体拷贝(覆盖前再校验目标文件==基线,防误覆盖登记适配文件),再做 `--release 8` 编译 |
| B 登记适配 | 上游本轮变更 且 jre8 blob != gitee@上个 tag blob | 三方合并(base=gitee@上个 tag,ours=jre8,theirs=gitee@HEAD,`git merge-file`),须校验两条不变量:diff(ours→merged)≡diff(base→theirs)、diff(theirs→merged)≡diff(base→ours) |
| C 残余差异 | 上游未变 但 jre8 与上游不同 | 应全部命中「排除清单」/「适配清单」,出现未登记项即为存量缺口 |
| D | 上游已变 但 jre8 已等于上游新值 | 应为 0,否则说明基线 tag 选错 |

上游变更范围以 gitee 自身的 `git diff --stat <上个 tag> HEAD` 为准;合并后用同一哈希法对工作区做全景复核,所有差异必须能归入
上述清单。B 类合并的三方输入一律取 git blob(`git show`),输出再转回工作区的 CRLF。

## 排除清单(不进入 5.6.jre8 的文件/代码)

| 项 | 原因 | 处置 |
|---|---|---|
| `plugins/json/Jackson3JsonProvider.java` | jackson3(tools.jackson) 为 JDK17 生态,jre8 无此依赖 | 不拷入;JsonProviders 候选列表/别名/提示文案同步剔除 jackson3 |
| BeanUtil 的 records 支持分支 | `Class.isRecord()/getRecordComponents()` 为 JDK16 API,jre8 无解 | 拷入后剥离 record 分支(保留其余全部: BeanMappingPlan/classFieldTypeMap/loadClass/methodToProperty/isGetter/isSetter) |
| `config/model/PropertyType.java` | record 支持专用模型,BeanUtil 剥离后无引用 | 不拷入 |
| `lambda/toolkit/FieldNature`(record 语法) | jre8 无 record 语法 | 改写为普通不可变类(保留组件方法名 entityClass()/fieldName() 以兼容 lambda 引用) |
| ES栈的rest5-client | rest5-client(co.elastic.clients) 为 JDK17 字节码(class 61) | 仅ElasticEndpoint与HttpClientUtils保留hc4+elasticsearch-rest-client形态,但已合并6.0于2026-11-13的改进批次(单/多地址统一走rest客户端、直连httpclient路径移除、超时/字符集endpoint默认+语句级覆盖、按超时组合缓存客户端、pathPrefix网关子路径、池化连接失效重试一次、4xx响应经ResponseException取回走统一错误解析);ElasticSearchUtils/ElasticSearchPlugin/ElasticSqlPlugin/link/Elastic/NoSqlConfigModel/SqlToyContext与6.0逐字一致 |
| 测试中record用例 | record特性在jre8不存在 | 不拷入(BeanUtilEdgeTest/BeanUtilRecordColumnTypesTest/BeanUtilSmokeTest/VectorGeometryRecordEdgeTest/Student fixture) |
| `plugins/json/JsonProviderMatrixTest.java` | jackson3 候选矩阵含 JDK17 专属用例+文本块 | 不拷入(5.6 的 JsonProvider fastjson2/jackson2 实现由既有测试背书;如需矩阵化再按 jackson3 剔除改造) |

## 适配清单(定点语法/API 修正)

| 文件 | 6.0 用法 | jre8 等价 |
|---|---|---|
| `dialect/utils/PostgreSqlDialectUtils.java` | 文本块(getTables的pg_inherits SQL) | 字符串拼接 |
| `utils/FileUtil.java` | `FileWriter(File,Charset,boolean)`(JDK11)、`String.isBlank()`(JDK11) | `OutputStreamWriter(FileOutputStream,Charset)`、`trim().isEmpty()` |
| `utils/StringUtil.java` | `String.repeat()`(JDK11) | StringBuilder 循环拼接 |
| `utils/DateUtil.java` | `Matcher.appendReplacement(StringBuilder,...)`(JDK9+) | 改 `StringBuffer` |
| `dialect/utils/DialectUtils.java` | `Matcher.appendReplacement(StringBuilder,...)`(JDK9+) | 改 `StringBuffer` |
| `config/EntityScanner.java` | `StringBuilder.isEmpty()`(JDK15) | `length() > 0` |
| `plugins/nosql/HttpClientUtils.java` | rest5-client + hc5 | 保留 hc4 ES 提交 + hc5 rest通用提交(FQN共存);ES方法的JSON已切JsonProviders(2026-11-13),doPostBody/doPostEntity与6.0同构,rest路径改wrapPath相对路径(修多地址url拼接),仅HTTP执行层为hc4 |
| BeanUtil | switch 表达式/pattern instanceof/canAccess | if-else 链/传统 cast/isAccessible(全量同步后仅余 getClassFieldMap 的 ClassValue 化,语法适配点已随剥离消除) |
| 测试文件(StringUtil/NumberUtil等) | `.repeat()`/`.toList()`/`var`/`List.of()`/`Map.of()`/`readAllBytes()`/文本块 | nCopies/collect/显式类型/HashMap/循环读取/字符串拼接 |
| `ClassValueCacheStructureTest` | 断言 BeanUtil 的 `recordProperties` 为 ClassValue(6.0.11 新增) | 剔除该字段断言——本侧 BeanUtil 无 record 分支 |
| `HttpClientUtilsUriTest.staleConnectionClassification` | 传入 hc5 异常类(`org.apache.hc.core5.http.*`) | 改 import hc4 异常类(`org.apache.http.*`),与本侧 isStaleConnection 实现一致 |
| `BeanUtilGlobalCacheBoundLogicTest` | `record TestRecord` 映射用例(6.0.11 新增) | 剔除 record fixture 与 recordMappingRepeatedStable 用例 |
| `BeanUtilEnumClassReloadTest` | `ClassLoader.getPlatformClassLoader()`(JDK9+) | `getSystemClassLoader().getParent()`(扩展类加载器,同样看不到测试 classpath) |
| `LambdaCompositionH2Test` | 6.0(H2 2.5.252)已移除链式 CTE 版本守卫 | 恢复 `@DisabledIf`+H2LegacyChainedCteParamBug 守卫(H2≤2.2.224 引擎限制) |
| solon `DbManager.getService` | `crudService.setLightDao(...)`(6.0 服务类签名) | 保持本模块 `setSqlToyLazyDao(...)` 形态(第 13 条的切换清空逻辑照常移植) |
| `OracleTzNormalizeTest` | 断言 TIMESTAMPTZ 必解析出无参 `offsetDateTimeValue()`(ojdbc11 23.x 具备该方法) | 按驱动能力分派断言——ojdbc8 19.24(JDK8 最后一条 Oracle 驱动线)的 TIMESTAMPTZ 只有 `offsetDateTimeValue(Connection)`,javap 实证无无参方法:此时断言解析为 null。产品代码 `ResultUtils` 与 6.0 逐字一致(反射不到方法即保留驱动对象=6.0.12 之前行为),故**本线 Oracle TIMESTAMPTZ 读回归一不生效**,需要可读值时 SQL 端 to_char 或 VO 用 Timestamp 接收;若日后 ojdbc8 线提供无参方法,产品代码自动生效、测试自动切换断言 |
| `BeanUtilArrayConvertTypeTest`/`SqlUtilsExtArrayResultUpdateTest` | `Set.of(..)`/`List.of(..)`(JDK9) | `new HashSet<>(Arrays.asList(..))`/`Arrays.asList(..)` |

## pom 差异基线(5.6 侧 jre8 化规则)

| 6.0 依赖 | 5.6.jre8 处置 | 说明 |
|---|---|---|
| ojdbc11 23.26.4 | ojdbc8 19.24.0.0 | jre8 兼容线 |
| jackson3 + jackson3-annotations | 剔除 | JDK17 生态 |
| httpclient5 5.6.5 | 保留 | class 52 验证通过 |
| elasticsearch-rest5-client 9.5.6 | → elasticsearch-rest-client 8.17.4 + httpclient4 4.5.14 + httpcore 4.4.16 | ES栈整体保留5.6形态 |
| h2 2.5.252 | **2.2.224** | 最后一个 jre8 兼容版;2.2.224与2.1.214均有链式CTE参数绑定限制(该用例按版本守卫跳过) |
| ehcache 3.12.0 | **3.10.8** | 3.12为JDK17字节码;3.10.8为最后jre8版 |
| sqlserver 13.6.2.jre11 | 12.8.1.jre8 | jre8变体 |
| clickhouse-jdbc 0.11.1 | **0.10.0** | 0.11.1的jdbc-v2/client-v2传递依赖不可解析;0.10.0的all jar完整且为class 52 |
| junit 6.1.3 | 5.11.4/1.11.4 | JUnit 6 需 JDK17 |
| 新增驱动(starrocks/mysql/sqlite/hana/kingbase/oceanbase/db2/dm等,test scope) | 随同步引入 | 保持 test 作用域 |
| 编译配置 | source/target 1.8,移除release | |
| spring-framework 7.0.11 / spring-boot 4.1.3(6.0.12 升级) | 不跟进 | 本线为 Boot2/javax 生态,模块 pom 维持 5.6 线依赖;6.0.12 自身无 core 依赖变更 |

## 测试门禁

- 全量测试(2196例):`mvn test`(pom已设surefire skip=false)
- 全量测试(第三轮后 2481 例/323 类):`mvn test`(pom已设surefire skip=false);本机无 JDK8 时以 `-Dmaven.compiler.release=8` 于 JDK17 上等价执行(语法+API 双校验,勿用 source/target 8——jdk17 下 java.beans 会误报 PropertyInfo 不可访问)
- 链式CTE用例(testCteRefsCteExecution):H2≤2.2.224按版本守卫跳过(引擎限制,非框架缺陷)
- fat jar集成测试(EntityScannerFatJarTest):需pom拷贝boot-loader(JDK17生态),jre8下跳过
- docker真库冒烟:复用6.0的容器矩阵,测试代码jre8语法化
- 移植测试的已知改写规则:文本块→拼接(**关键:收尾"""独占一行时内容以\n结尾,末段字面量必须补\n;定界符前还有空行则补\n\n——第三轮曾整体漏掉,经JDK17文本块求值对比器TBVerify逐对修复并验证15/15等价**)、record fixture→剔除、`var`→显式类型、`Map.of`→HashMap、`.repeat`→nCopies、`readAllBytes`→循环读取
- 删除过源码的测试务必 `mvn clean`(陈旧 class 会被 surefire 捡起误报,如 TranslateCacheCheckersTest/record 系排除用例)
- spring/spring-starter/solon 模块:编译验证通过;starter/solon 自第三轮起有真实单测(caffeine 双上下文 3 例/DbManager 作用域 1 例),构建前需先 `mvn install` 同步后的 core
- 版本号:已发布的版本(如 5.6.98.jre8)不得再用 `mvn install` 覆盖本地仓库——同版本本地构件会与中央仓库不一致,且 `_remote.repositories` 残留远端来源标记使 Maven 不再重新下载(第七轮曾误覆盖,已删除该版本目录后 `dependency:get` 从镜像恢复);模块验证一律安装**新的未发布版本号**(core→spring→starter 有依赖顺序,三模块无聚合 pom)
- 统计口径(第七轮实测):同步前 008ca5b62 = 330 类 2583 例/82 跳过,同步后 = 347 类 2714 例/82 跳过。核对测试数时注意——①带 `@DisplayName` 的类,`Running` 行与汇总行输出的是显示名而非类全名(第七轮有 18 个类如此),且控制台日志为 GBK;按类统计应数 `target/surefire-reports/TEST-*.xml`,或用紧邻的 `Running` 行归属汇总行,否则会漏计;②`EntityScannerFatJarTest` 的 5 例是否跳过取决于构建目录是否遗留 boot-loader(clean 检出跳过→82,增量目录执行并通过→77),属环境差异非代码差异;③要证明"零回归",把同步前的提交检出到独立 worktree(`git worktree add --detach <tmp> HEAD`)clean 实跑一遍,再与同步后的日志逐类比对
