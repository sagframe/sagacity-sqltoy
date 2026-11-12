# 5.6.jre8 ← 6.0 core 全量同步机制

> 建立:2026-11-12。5.6.jre8 的 sqltoy-orm-core 定位为 **6.0 core 的 jre8 编译目标**:
> 以 6.0 发版 tag 为基线做全量文件同步,消除逐个回移的翻译风险,让 6.0 的测试直接背书。
> spring/solon 插件模块(Boot2/javax 生态)不迁移,按 core API 变化跟进适配。
>
> **首轮全量同步完成(2026-11-12)**:211 个文件(139M+71A+1D),测试矩阵 2196 例全绿。
> 适配点登记如下,后续每轮同步按清单检查是否需更新。

## 同步流程(每个 6.0 release tag 执行一轮)

1. `git fetch` 6.0 仓库,锚定 tag
2. 按本清单镜像 core `src/main`(java+resources) + `src/test` 到 5.6,排除「排除清单」文件
3. 应用「适配清单」的定点修正
4. 合并 pom 依赖(按「pom 差异基线」jre8 化)
5. 编译门禁:JDK8 全量编译 + 全量测试(2196例)+ spring/starter/solon 模块编译
6. 更新本清单(新增的适配点登记)

## 排除清单(不进入 5.6.jre8 的文件/代码)

| 项 | 原因 | 处置 |
|---|---|---|
| `plugins/json/Jackson3JsonProvider.java` | jackson3(tools.jackson) 为 JDK17 生态,jre8 无此依赖 | 不拷入;JsonProviders 候选列表/别名/提示文案同步剔除 jackson3 |
| BeanUtil 的 records 支持分支 | `Class.isRecord()/getRecordComponents()` 为 JDK16 API,jre8 无解 | 拷入后剥离 record 分支(保留其余全部: BeanMappingPlan/classFieldTypeMap/loadClass/methodToProperty/isGetter/isSetter) |
| `config/model/PropertyType.java` | record 支持专用模型,BeanUtil 剥离后无引用 | 不拷入 |
| `lambda/toolkit/FieldNature`(record 语法) | jre8 无 record 语法 | 改写为普通不可变类(保留组件方法名 entityClass()/fieldName() 以兼容 lambda 引用) |
| ES栈的rest5-client | rest5-client(co.elastic.clients) 为 JDK17 字节码(class 61) | ElasticEndpoint/ElasticSearchPlugin/ElasticSearchUtils/ElasticSqlPlugin/link/Elastic 保留5.6版本(httpclient4+elasticsearch-rest-client) |
| 测试中record用例 | record特性在jre8不存在 | 不拷入(BeanUtilEdgeTest/BeanUtilRecordColumnTypesTest/BeanUtilSmokeTest/VectorGeometryRecordEdgeTest/Student fixture) |

## 适配清单(定点语法/API 修正)

| 文件 | 6.0 用法 | jre8 等价 |
|---|---|---|
| `dialect/utils/PostgreSqlDialectUtils.java` | 文本块(getTables的pg_inherits SQL) | 字符串拼接 |
| `utils/FileUtil.java` | `FileWriter(File,Charset,boolean)`(JDK11)、`String.isBlank()`(JDK11) | `OutputStreamWriter(FileOutputStream,Charset)`、`trim().isEmpty()` |
| `utils/StringUtil.java` | `String.repeat()`(JDK11) | StringBuilder 循环拼接 |
| `utils/DateUtil.java` | `Matcher.appendReplacement(StringBuilder,...)`(JDK9+) | 改 `StringBuffer` |
| `dialect/utils/DialectUtils.java` | `Matcher.appendReplacement(StringBuilder,...)`(JDK9+) | 改 `StringBuffer` |
| `config/EntityScanner.java` | `StringBuilder.isEmpty()`(JDK15) | `length() > 0` |
| `plugins/nosql/HttpClientUtils.java` | rest5-client + hc5 | 保留 hc4 ES 提交 + hc5 rest通用提交(FQN共存) |
| BeanUtil | switch 表达式/pattern instanceof/canAccess | if-else 链/传统 cast/isAccessible(全量同步后仅余 getClassFieldMap 的 ClassValue 化,语法适配点已随剥离消除) |
| 测试文件(StringUtil/NumberUtil等) | `.repeat()`/`.toList()`/`var`/`List.of()`/`Map.of()`/`readAllBytes()`/文本块 | nCopies/collect/显式类型/HashMap/循环读取/字符串拼接 |

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

## 测试门禁

- 全量测试(2196例):`mvn test`(pom已设surefire skip=false)
- 链式CTE用例(testCteRefsCteExecution):H2≤2.2.224按版本守卫跳过(引擎限制,非框架缺陷)
- fat jar集成测试(EntityScannerFatJarTest):需pom拷贝boot-loader(JDK17生态),jre8下跳过
- docker真库冒烟:复用6.0的容器矩阵,测试代码jre8语法化
- 移植测试的已知改写规则:文本块→拼接、record fixture→剔除、`var`→显式类型、`Map.of`→HashMap、`.repeat`→nCopies、`readAllBytes`→循环读取
- spring/spring-starter/solon 模块:编译验证通过(API兼容,无需改动)
