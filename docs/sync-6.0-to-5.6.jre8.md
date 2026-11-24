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
>
> **第八轮同步完成(2026-11-24,6.0.12 发版后开发内容,版本仍 5.6.99.jre8 未发布)**:上游 af0dc7c25..b1c980ab3 共 7 提交
> 62 文件(+5367/-350),无新 tag。内容:CollectionUtil 树排序 O(n²)→O(n)(新增公开 appendTreeChildren)、TreeDataSort
> 索引化与 id/pid 宽松比较(修 bigint id 对 int pid 全被当根节点)、pivot 类别索引、extractCategory/clearRepeat 判重键
> 撞键修复、分组比较空安全、BeanUtil 枚举记忆与缓存键简化、MapperUtils setter 元数据缓存、SqlUtil @fast 与
> StringUtil splitRegex 正则预编译。**关键发现:本侧提交 9adab7bee(95 文件)已包含与上游 766bdb937 完全相同的 IDE
> 格式化(第七轮交付为 72 文件),故本轮 D 类 34 个——上游已变而本侧已等于新值,核对 blob 确认属实,无需再动**。
> 实际进入:A 类 2 个 main(TreeDataSort/MapperUtils)+17 个新测试文件(其中 4 个为差分参照实现与夹具,不作为测试执行);
> B 类 9 个三方合并,ElasticEndpoint/HttpClientUtils 的 6 处冲突全部取 jre8 侧(上游只是重排本侧不存在的 hc5 代码形态
> 与"rest5"措辞),两文件与提交逐字节一致;CollectionUtil/ResultUtils/SqlUtil 合并后与上游逐字一致;BeanUtil/StringUtil/
> DialectUtils/PostgreSqlDialectUtils 的登记适配保留。19 个新文件全部 jre8-clean,无需语法转换。门禁:core clean 全量
> 360 类 2806 例全绿(82 例环境性跳过),与上轮 347 类 2714 例逐类比对——+92 例全部归因于 13 个新测试类,无类丢失、
> 既有类计数零变化;starter 4 例、solon 7 例、spring 编译通过。全景:947 文件 853 个与上游一致,差异 92(本轮 6 + 既有 86)。
>
> **第九轮同步完成(2026-11-24 晚,计算器与结果处理算法修复,版本仍 5.6.99.jre8)**:上游 b1c980ab3..e532640cb 共 5 提交
> 9 文件(+1075/-59),全部为 A 类干净基线(B=0、D=0),直接拷贝、无合并无冲突。内容:①ColsChainRelative/RowsChainRelative/
> ReverseList 负数下标钳到 0、负数 end 改 python 式 dataSize+end(原差 1)、ReverseList 交换次数 (end-start+1)/2
> (原 2 行时完全不反转)、RowsChainRelative 循环边界含首/末组(原 groupSize=1 漏行);②MapperUtils 父类链 null 守卫
> (接口 getSuperclass() 为 null)与 IS-A 可赋值直接赋值(修 Map←HashMap/Number←Integer 等被丢弃);③ResultUtils 多列 link
> 键与一对多分组键改逐列列表(消除值含下划线/逗号的撞键)、排序按当前比较对动态判型(修混合类型列 CCE)。
> 4 个新测试文件在上游为 LF 结尾,拷入后转 CRLF 与本侧工作区一致(blob 不受影响)。门禁:core clean 全量 364 类 2838 例
> 全绿(82 例环境性跳过),与上轮 360 类 2806 例逐类比对——+32 例全部归因于 4 个新测试类(12+9+6+5),无类丢失、
> 既有类计数零变化;starter 4 例、solon 7 例、spring 编译通过。
>
> **第十轮同步完成(2026-11-24,混合类型排序与日志口径,版本仍 5.6.99.jre8)**:上游 e532640cb..e4e0b67ef 单提交 7 文件
> (+127/-22),src 下 6 个全部 A 类(B=0、D=0),直接拷贝。实质改动仅两处:①CollectionUtil.compareValue 与 ResultUtils.sortList
> 同口径(两侧都符合 dataType 才强转,否则退回文本比较),修 sortList/groupSort 混合类型列 CCE;②TreeDataSort 的
> "所有行都是根"提示 warn→debug(过滤后的树形查询属正常用法,避免刷日志)。其余为注释/javadoc(RowsChainRelative 补
> groupSize>=3 越界说明、MapperUtils javadoc 归位、ResultUtils 键形态描述)。新测试文件仍为 LF,已转 CRLF。
> 门禁:core clean 全量 365 类 2842 例全绿(82 例环境性跳过),+4 例归因于 CollectionUtilSortMixedTypeTest,既有类计数零变化;
> starter 4 例、solon 7 例、spring 编译通过。另:上游 changelog 将计算器边界修正标注为"产物可见的行为变更,升级需注意",
> 本侧 changelog 第 6 条已同步该标注与 groupSize>=3 的越界说明。
>
> **第十一轮同步完成(2026-11-24,死循环修复与独立复核套件,版本仍 5.6.99.jre8)**:上游 e4e0b67ef..e804418dd 共 4 提交
> 7 文件(+4152),全部 A 类(B=0、D=0)。主代码仅 RowsChainRelative:①起止行间无数据时 groupSize 为 0 会让循环步长为 0
> **死循环**(上轮边界改动的跟进修复),钳到 1;②relative-index 超出分组长度时 divIndex/divedIndex 落在起止行外
> (原数组越界),改为跳过。其余 6 个为新包 `org.sagacity.sqltoy.recheck` 下的独立复核套件(43 例,参考实现按优化前源码
> 重新转写,不复用既有对拍基准类;含死循环修复的真实链路回归 sql xml→H2→ResultUtils→RowsChainRelative)。
> jre8 适配 1 处:CacheAndRegexRecheckTest 的 `String.isBlank()`(JDK11)→`trim().isEmpty()`。门禁:core clean 全量
> 371 类 2885 例全绿(82 例环境性跳过),+43 例全部归因于 6 个新类,既有类计数零变化;starter 4 例、solon 7 例、spring 编译通过。
>
> **第十二轮同步完成(2026-11-24,复核用例调整)**:上游 e804418dd..469bcee09(含一个 merge,系同一文件两处并行编辑后的
> 合并),净变更仅 RowsChainRelativeH2RecheckTest(+10/-10):对照用例改用默认乘数 1、保留 3 位小数(期望值相应更新),
> context 捕获为局部 final 变量供工作线程使用。纯测试调整,无产品代码变化。本侧该文件尚未提交,故按"已修改"而非"新增"
> 走带保险拷贝(先校验工作区副本==上游基线)。门禁:core clean 全量 371 类 2885 例全绿(82 跳过),类数/例数与上轮完全一致;
> starter 4 例、solon 7 例、spring 编译通过。
>
> **第十三轮同步完成(2026-11-24,IS-A 直赋的副本语义修正,版本仍 5.6.99.jre8)**:上游 469bcee09..9e7e1a886 共 2 提交 4 文件
> (+342/-5)。MapperUtils 修正第九轮 IS-A 直赋的副作用:同类型嵌套 DTO 原应递归映射产生**独立副本**,第九轮改成共用实例;
> 现"类型相同且有无参构造"仍走递归映射,"类型相同但无无参构造(不可变对象/非静态内部类)"才直赋,"递归映射不出结果
> (集合类、Locale 等)且值可赋值"也直赋(原先静默丢弃)。BeanUtil 仅 cachedNoArgConstructor 的 javadoc(B 类,三方合并
> +2/-1,适配 +47/-219 保留,校验通过);MapperUtils 与 2 个测试文件为 A 类拷贝。本轮工作区有未提交改动,ours 清单改由
> **工作区内容**生成(wt-tree.js),未提交的 recheck 文件因此被正确识别为"已修改、干净基线"。门禁:core clean 全量
> 371 类 2887 例全绿(82 跳过),+2 例落在 MappingAndSortRecheckTest(5→6)与 MapperUtilsSubclassMappingTest(6→7),
> 其余零变化;starter 4 例、solon 7 例、spring 编译通过。
>
> **第十四轮同步完成(2026-11-24,越界告警与热路径优化,版本仍 5.6.99.jre8)**:上游 9e7e1a886..ac90f3021 共 2 提交
> (e4c8cb8f2 + 跑测试期间新增的 ac90f3021),src 下 2 个文件均 A 类,直接拷贝。①RowsChainRelative:relative-index 超出
> [0,groupSize) 时一次性 warn 告警(跳过行为不变,使"少算值"的配置错误可见);②MapperUtils:setter 原始参数类型缓存进
> 元数据 meta[5](Class[] methodParamTypes),两条映射路径的热路径改读数组,消除每单元格 getParameterTypes() 的数组 clone;
> ac90f3021 为 getSetterMeta 的 @return javadoc 补第 6 个元素说明(纯注释)。**教训:上游在本轮门禁跑测试期间又前进了一次,
> 收尾复核时用 `git show HEAD:` 直接比对才发现——每轮收尾必须重新确认上游 HEAD 未再移动**。无测试改动。
> 门禁:core clean 全量 371 类 2887 例全绿(82 跳过),类数/例数与上轮完全一致;starter 4 例、solon 7 例、spring 编译通过。
>
> **版本对应关系(2026-11-24 定版)**:5.6.99.jre8 的内容等同上游 **6.0.13**(= 6.0.12 正式发版 + 其后全部开发内容,
> 至上游提交 ac90f3021;上游 4 个 pom 已升 6.0.13)。第七~十四轮各段中"与 6.0.12 逐字节一致"等表述是各轮当时的基线记述,
> 保留不改;对外措辞(两个仓库的 README 徽章/最新版本/依赖示例/jdk8 注释、helloworld、changelog)已统一到 6.0.13。
> 注意:6.0.12 及以前的发版说明属历史记录,其 jdk8 对应版本不得改写(如 release-6.0.11.md 仍对应已发布的 5.6.98.jre8)。

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
| D | 上游已变 但 jre8 已等于上游新值 | 通常为 0;非 0 有两种成因——基线 tag 选错,或**本侧已先行应用同一改动**(第八轮:本侧提交含与上游相同的 IDE 格式化,34 个文件属此)。须逐文件核对 blob 是否真等于上游新值来区分 |

上游变更范围以 gitee 自身的 `git diff --stat <上个 tag> HEAD` 为准;合并后用同一哈希法对工作区做全景复核,所有差异必须能归入
上述清单。B 类合并的三方输入一律取 git blob(`git show`),输出再转回工作区的 CRLF。
**每轮必须重新导出 base/theirs/ours 三份清单**:沿用上一轮快照会把本轮已同步好的文件误判为"仍有差异"(第十二轮曾因此误报,
重新导出后归零);工作区有未提交改动时,ours 须取工作区内容而非 HEAD。
**收尾必须重新确认上游 HEAD 未再移动**:门禁跑测试的几分钟里上游可能又提交(第十四轮即如此),收尾时用
`git show HEAD:<path>` 与本地文件直接比对可发现;发现移动则补同步并重跑门禁,勿用旧 HEAD 的数字交差。

**校验不变量(第八轮修正)**:记 U=diff(base→theirs)、A=diff(base→ours)、P=diff(ours→merged)、R=diff(theirs→merged),
按"变更行多重集"校验四条子集关系:①P⊆U(只应用了上游改动)②R⊆A(残留只含登记适配)③U⊆P+A(上游改动没被丢:要么本轮应用、
要么本侧已有)④A⊆R+U(适配没丢:要么保留、要么被上游改动正当替换)。第七轮用的"等式"形式(P=U 且 R=A)仅在 ours 不含任何
上游改动时成立;本侧已先行应用部分上游改动(如格式化)时必须用子集形式,否则会误报。
**纯格式化批次的处理口径**:上游 IDE 重排若落在登记适配区内(如 hc4 代码形态、"rest"措辞),冲突取 jre8 侧、不跟进该处重排;
此时 ③ 会报 upstream-dropped,须人工确认被跳过的行确实位于登记适配区(第八轮 6 处冲突即此情形,合并结果与 ours 逐字节一致)。

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
| `recheck/CacheAndRegexRecheckTest` | `String.isBlank()`(JDK11) | `trim().isEmpty()` |
| `DateUtilScenarioTest` | 中文短名期望值按 JDK9+ 的 CLDR 写死(MMM=8月、EEE=周二) | 按运行 JDK 选择期望值:JDK8 用传统 JRE locale 数据(MMM=八月、EEE=星期二),JDK9+ 用 CLDR。**非框架缺陷**(DateUtil 只是按 locale 委托 SimpleDateFormat),全名与英文/日文形态两代一致;断言仍为精确匹配,不放宽为"两者皆可" |

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
- **JDK8 真机门禁(2026-11-24 起本机已装 E:\jdk1.8,1.8.0_513)**:`set JAVA_HOME=E:\jdk1.8` 后 `mvn -o clean test`,pom 的 source/target 1.8 原生生效,无需 `--release`。**`--release 8` 于 JDK17 并不等价于 JDK8 真机**:它只校验语法与 API 签名,不覆盖运行期 JDK 数据/行为差异——第十四轮后的发版自检即在 JDK8 上抓到 `DateUtilScenarioTest` 的中文短名 locale 差异(JDK17 全绿而 JDK8 失败),故发版前必须跑 JDK8 真机全量
- 链式CTE用例(testCteRefsCteExecution):H2≤2.2.224按版本守卫跳过(引擎限制,非框架缺陷)
- fat jar集成测试(EntityScannerFatJarTest):需pom拷贝boot-loader(JDK17生态),jre8下跳过
- docker真库冒烟:复用6.0的容器矩阵,测试代码jre8语法化
- 移植测试的已知改写规则:文本块→拼接(**关键:收尾"""独占一行时内容以\n结尾,末段字面量必须补\n;定界符前还有空行则补\n\n——第三轮曾整体漏掉,经JDK17文本块求值对比器TBVerify逐对修复并验证15/15等价**)、record fixture→剔除、`var`→显式类型、`Map.of`→HashMap、`.repeat`→nCopies、`readAllBytes`→循环读取
- 删除过源码的测试务必 `mvn clean`(陈旧 class 会被 surefire 捡起误报,如 TranslateCacheCheckersTest/record 系排除用例)
- spring/spring-starter/solon 模块:编译验证通过;starter/solon 自第三轮起有真实单测(caffeine 双上下文 3 例/DbManager 作用域 1 例),构建前需先 `mvn install` 同步后的 core
- 版本号:已发布的版本(如 5.6.98.jre8)不得再用 `mvn install` 覆盖本地仓库——同版本本地构件会与中央仓库不一致,且 `_remote.repositories` 残留远端来源标记使 Maven 不再重新下载(第七轮曾误覆盖,已删除该版本目录后 `dependency:get` 从镜像恢复);模块验证一律安装**新的未发布版本号**(core→spring→starter 有依赖顺序,三模块无聚合 pom)
- 统计口径(第七轮实测):同步前 008ca5b62 = 330 类 2583 例/82 跳过,同步后 = 347 类 2714 例/82 跳过。核对测试数时注意——①带 `@DisplayName` 的类,`Running` 行与汇总行输出的是显示名而非类全名(第七轮有 18 个类如此),且控制台日志为 GBK;按类统计应数 `target/surefire-reports/TEST-*.xml`,或用紧邻的 `Running` 行归属汇总行,否则会漏计;②`EntityScannerFatJarTest` 的 5 例是否跳过取决于构建目录是否遗留 boot-loader(clean 检出跳过→82,增量目录执行并通过→77),属环境差异非代码差异;③要证明"零回归",把同步前的提交检出到独立 worktree(`git worktree add --detach <tmp> HEAD`)clean 实跑一遍,再与同步后的日志逐类比对
- 第八轮实测:360 类 2806 例/82 跳过,较第七轮 +13 类 +92 例,既有类计数零变化。新增测试文件中不以 `Test` 结尾的(如 `oracle/` 下的差分参照实现 `CollectionUtilLegacyOracle`/`TreeDataSortLegacyOracle` 与夹具 `TreeFixtures`/`DiffSupport`)不被 surefire 执行,统计"新增测试类"时须扣除(第八轮 17 个新文件中 13 个是测试类)
- 第九轮实测:364 类 2838 例/82 跳过,较第八轮 +4 类 +32 例,既有类计数零变化。上游新建文件可能是 LF 结尾(第九轮 4 个新测试文件即如此),拷入后转 CRLF 与本侧工作区一致;`core.autocrlf=true` 下 blob 均为 LF,不影响哈希比对
