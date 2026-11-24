# sqltoy 6.0.3 发版说明

**发布日期：2026-10-01**

本版本包含 **2 项新特性**、**1 项缺陷修复** 和若干依赖升级，重点解决多方言数据库（OB、代理库、国产库迁移等）场景下方言变体选择与 SQL 固化解析的问题。默认行为零变化，可放心升级。

## 一、新特性

### 1. 新增 `realDialectFirst` 配置（默认 false）：sqlId 方言变体查找优先按真实方言

**背景**：典型如 OceanBase 按 mysql 方言配置而真实库为 oceanbase、代理库场景等，连接探测出的真实方言（`DBProfile.realDialect`）与配置方言不一致，此时按配置方言挑选的 sqlId 方言变体（如 `xxx_mysql`）与真实库形态并不匹配。

**开启后**：

- sqlId 方言变体查找优先按连接探测的**真实方言**进行（如真实库为 kingbase 时优先找 `xxx_kingbase`）；
- 真实方言变体不存在时，回退原有的配置方言查找链；真实方言与配置方言一致时行为不变；
- 同步补齐函数替换收口点（`SqlToyConfig.getDialectSql` 惰性转换）：仅对经 `realDialectFirst` 变体匹配选中的 sql，将转换方言换算为真实方言——迁移场景下全局 `dialect=mysql` 固化的 `ifnull` 等 mysql 形态可按 kingbase 真实库自动转换为 `coalesce`；未命中变体回退 base 的 sql 保持配置方言行为，不受影响；
- 分页等执行策略的 dbType 分派不受影响，仅 sql 形态层（函数与保留字）参与换算。

**配置方式**：

```yaml
# Spring Boot（spring-boot-starter）
spring:
  sqltoy:
    realDialectFirst: true

# Solon（solon.sqltoy 或 sqltoy 前缀均可）
solon:
  sqltoy:
    realDialectFirst: true
```

配置随 `SqlToyContext.initialize` 写入 `SqlToyConstants.realDialectFirst` 静态常量（JVM 级首个 context 生效），便于后续环节随时获取。

### 2. 新增 `<sql dialect="xxx">` 元素属性：声明本条 sql 按指定方言形态解析固化

**背景**：此前 `<sql>` 元素上的 `dialect` 属性不被解析而被静默忽略，写了也不生效。

**现在**：

```xml
<!-- 声明本条 sql 按 kingbase 方言形态解析固化 -->
<sql id="order_query_kingbase" dialect="kingbase">
    <value><![CDATA[
        select coalesce(a.total_amt, 0) as total_amt
        from order_info a where a.status = :status
    ]]></value>
</sql>
```

- **加载时**按声明方言做一次函数/保留字转换并固化解析标签；**执行期**当前数据库方言与声明一致时，复用既有"查询方言 == 解析标签"早退机制跳过函数替换；异方言查询走既有惰性转换 + `dialectSqlMap` 缓存反向适配；
- **count-sql 与主 sql 使用同一生效方言**（此前 count-sql 按全局方言转换，属性覆盖后会形态错配）；
- 优先级为：mql/eql 强制 mongo/es > 元素 dialect 属性 > 全局方言（mql/eql 不支持该属性）；
- 属性值经 `getDBType` 校验，未识别的方言给出 warn 提示并回退全局方言（打错字表现为属性不存在，避免产生永不早退的标签）；
- `sqltoy.xsd` 的 `sqlToySqlType` 同步声明该属性（仅 sql，mql/eql 不加），便于 IDE 校验与补全；远程发布副本 `https://sagframe.github.io/schema/sqltoy.xsd` 随发布流程另行同步；
- **与 realDialectFirst 协同**：`<sql id="x_kingbase" dialect="kingbase">` 被变体匹配命中时，加载已固化 kingbase 形态且标签相等，执行连惰性转换都省略。

> ⚠️ **升级注意**：如果既有 xml 中已经写过 `dialect` 属性（此前当作无效属性），本版本起将真实生效——sql 会按该方言形态解析固化，请检查存量配置。

## 二、缺陷修复

### 1. convertType 数字分支强化：解析前剥离千分位分组逗号

- **现象**：sql 中 `number-format` 等带 `#,##` 格式化的列（如 `"8,888.0000"`）映射 VO 数值属性（BigDecimal/Integer/Long/Double 等）时，抛出 `Character , is neither a decimal digit number...` 异常；
- **修复**：字符串值解析为数值类型前剥离千分位分组逗号（`BeanUtil.convertBoolean` 统一收口，`XMLUtil` 数值解析同步受益）；
- **边界保障**：仅严格匹配分组形态（如 `8,888.0000`、`-12,345,678.9`）才剥离；非法形态如 `"1,23"` 或含逗号的普通文本保持原有解析报错行为，常规值不含逗号时走快速短路，热路径零正则开销。

## 三、依赖升级

| 依赖 | 升级前 | 升级后 | 说明 |
| --- | --- | --- | --- |
| mongodb-driver-sync | 5.11.1 | 5.13.0 | core/spring/solon |
| slf4j | 2.0.19 | 2.0.20 | core/spring/solon |
| caffeine | 3.2.4 | 3.3.0 | spring/starter |
| h2 | 2.5.250 | 2.5.252 | test scope |
| vastbase-jdbc | 2.16v | 2.16.1v | - |
| mssql-jdbc | 13.4.0.jre11 | 13.6.0.jre11 | test scope |

## 四、Maven 坐标

```xml
<dependency>
    <groupId>com.sagframe</groupId>
    <artifactId>sagacity-sqltoy-spring-starter</artifactId>
    <!-- solon 适配版本 artifactId 为 sagacity-sqltoy-solon-plugin -->
    <!-- 传统 spring 项目为 sagacity-sqltoy-spring -->
    <!-- 单纯 sqltoy 为 sagacity-sqltoy -->
    <!-- jdk8 对应的版本号为：5.6.96.jre8（jre8 最终版本，同样支持 quickvo-maven-plugin 插件） -->
    <version>6.0.3</version>
</dependency>
```

## 五、其他

- 新增配套单元测试：`realDialectFirst` 变体查找（SqlScriptLoaderRealDialectFirstTest）、真实方言函数转换（SqlToyConfigRealDialectConvertTest）、`dialect` 属性解析（SqlXmlDialectAttributeParseTest）、convertType 数字边界（BeanUtilEdgeTest）；
- README、helloworld 文档同步更新版本号与 jdk8 配套版本说明。

---

**反馈渠道**：QQ 交流群 **531812227** ｜ [Gitee](https://gitee.com/sagacity/sagacity-sqltoy) ｜ [GitHub](https://github.com/sagframe/sagacity-sqltoy) ｜ [GitCode](https://gitcode.com/sqltoy/sagacity-sqltoy)
