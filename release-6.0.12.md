# sqltoy 6.0.12 发版说明

本次聚焦「跨库类型一致性」与「lambda 完善」，全部改动经真库矩阵回归。

## 一、主要更新

1. **UUID 全链路统一**：查询读回统一 String（Map/VO 一致，UUID 字段仍可按 UUID 接收）；32 位无连字符 UUID 绑定入口自动归一；SQLServer `uniqueidentifier` 正确绑定，PG/H2 走原生 UUID。
2. **几何类型跨库 DDL**：按「匹配—转化—回落」原则——nativeType 与目标库匹配则保留精度，不支持则尽力转化，无法判断回落 nativeType。
3. **boolean 跨库**：vastbase/hana 的 boolean 写入及 update/insert cast 统一处理。
4. **时区类型保真**：H2 `TIMESTAMP WITH TIME ZONE` DDL 不再丢时区；DB2 OffsetDateTime/ZonedDateTime 绑定修正；Oracle TIMESTAMPTZ 读回归一 + 反射缓存。
5. **BLOB 跨库承载**：Doris/StarRocks BLOB 部署修正。
6. **lambda 完善**：组合翻译守卫修复，缓存翻译链路完善。

## 二、已知限制（数据库/驱动能力边界，非框架缺陷）

vastbase boolean USF 写回、StarRocks VARBINARY 置空、Doris BLOB→STRING 为 hex 文本、SQLServer VECTOR 需 2025+、HANA 几何需 spatial 扩展（矩阵未真测）。

## 三、Maven 坐标

```xml
<dependency>
    <groupId>com.sagframe</groupId>
    <artifactId>sagacity-sqltoy-spring-starter</artifactId>
    <!-- solon 适配版本 artifactId 为 sagacity-sqltoy-solon-plugin -->
    <!-- 传统 spring 项目为 sagacity-sqltoy-spring -->
    <!-- 单纯 sqltoy 为 sagacity-sqltoy -->
    <!-- jdk8 对应的版本号为：5.6.99.jre8（core 与 6.0.13 全量同步，含 6.0.12 全部内容，同样支持 quickvo-maven-plugin 插件） -->
    <version>6.0.12</version>
</dependency>
```

## 四、其他

- 本版无依赖变更；
- orm-core clean 门禁全绿；verify 工程 16 库综合矩阵 + lambda 11 库矩阵全绿；SQL 加签 `/* id=sqlId */` 经 18 库真库验证均支持；
- 配套 quickvo-maven-plugin（项目地址：https://gitee.com/sagacity/maven-quickvo-plugin，完整功能与配置说明参见其 README）。

---

**反馈渠道**：QQ 交流群 **531812227** ｜ [Gitee](https://gitee.com/sagacity/sagacity-sqltoy) ｜ [GitHub](https://github.com/sagframe/sagacity-sqltoy) ｜ [GitCode](https://gitcode.com/sqltoy/sagacity-sqltoy)