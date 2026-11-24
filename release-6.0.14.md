# sqltoy 6.0.14 发版说明

本次聚焦计算与映射的正确性收口、热点路径性能，并以差分测试固化行为等价。**本公告合并 6.0.13 与 6.0.14 两版内容**，从 6.0.12 升级的用户看这一份即可。

## 一、主要更新

1. **树形与集合计算 O(n²) → O(n)**：树排序按 pid 分组前序挂接、逐层汇总「探测 16 行 + id 索引」找父节点、数据旋转按类别文本建索引；顺序与结果与原先一致。
2. **树形 id/pid 类型容错**：改宽松比较，修复 id 为 bigint 而 pid 为 int 时整批被当根节点、树形与汇总静默不发生；无根节点、id 为 null 改为带说明的异常。
3. **复合键消除拼接歧义**：link 键、行转列类别判重、一对多分组、in 参数判重一律改逐列文本列表，修复值含下划线/逗号时撞键丢数据。
4. **排序混合类型不再抛异常**：按当前比较对判型，两侧同类型才强转，否则退回文本比较；同构列结果不变。
5. **MapperUtils 映射修复**：接口父类遍历空指针、子类/实现值被静默丢弃（Map←HashMap、Number←Integer 等）、递归映射无结果时兜底直赋。
6. **缓存与正则收口**（不改变取值结果）：枚举转换记忆化、setter 元数据与参数类型缓存、`@fast` 与 `splitRegex` 正则预编译。
7. **solon 插件 `@Db` 支持注入 `SqlToyDao`**（6.0.14 新增）：`DbManager` 增加按数据源缓存的 `getSqlToyDao`（切换上下文时随其余缓存一并清空），注入分支补齐 `SqlToyDao` 类型（此前仅支持 `SqlToyLazyDao`/`LightDao`/`SqlToyCRUDService`）。

## 二、行为变更（升级需注意）

- 负数 end-row/end-column 改 Python 式从末尾计（`-1` 即最后一行/列，原先差 1）。
- reverse 交换次数改区间长度一半（原先偶数长度漏换中间一对，**2 行时完全不反转**）。
- rows-chain-relative 升序末行/降序首行改为参与计算（默认 group-size=1 时原先恒为空）。
- 同类型嵌套 DTO 仍递归映射产生**独立副本**，仅严格子类或字段类型无无参构造时直赋。

## 三、边界

- `groupSize>=3` 首/末分组不完整时跳过该组（原先越界）；`relative-index` 越界跳过并一次性告警。
- 起止行间无数据致 group-size=0 的**死循环**已修复；挂不到树的行仍丢弃但给出告警。

## 四、Maven 坐标

```xml
<dependency>
    <groupId>com.sagframe</groupId>
    <artifactId>sagacity-sqltoy-spring-starter</artifactId>
    <!-- solon 适配版本 artifactId 为 sagacity-sqltoy-solon-plugin -->
    <!-- 传统 spring 项目为 sagacity-sqltoy-spring -->
    <!-- 单纯 sqltoy 为 sagacity-sqltoy -->
    <!-- jdk8 对应的版本号为：5.6.100.jre8（core 与 6.0.14 全量同步，同样支持 quickvo-maven-plugin 插件） -->
    <version>6.0.14</version>
</dependency>
```

## 五、其他

- 本版无依赖变更；orm-core 全量 2930 例 / 0 失败 / 0 错误（76 skip 为需外部真库的冒烟测试）。
- 新增差分测试（冻结优化前实现作参照）与 `recheck` 独立复核套件。
- jdk8 线 5.6.100.jre8 已在真实 JDK8 上完成全量门禁，与 JDK17 结果一致。
- 配套 quickvo-maven-plugin：https://gitee.com/sagacity/maven-quickvo-plugin

---

**反馈渠道**：QQ 交流群 **531812227** ｜ [Gitee](https://gitee.com/sagacity/sagacity-sqltoy) ｜ [GitHub](https://github.com/sagframe/sagacity-sqltoy) ｜ [GitCode](https://gitcode.com/sqltoy/sagacity-sqltoy)
