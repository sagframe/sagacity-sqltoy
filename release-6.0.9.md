# SqlToy 6.0.9 发布说明

**2026-10-23** · 依赖坐标不变，升级版本号即可：`com.sagframe:sagacity-sqltoy` → `6.0.9`

本版以**跨库实证修补**为主，无破坏性变更。

## 更新内容

- **CTE 物化 hint 跨库自适应**：`WITH xx AS [NOT] MATERIALIZED (..)` 在 PostgreSQL 系（含 openGauss/金仓/Vastbase/GAUSSDB/瀚高）保留渲染，其余库自动剔除——只失物化优化、不改查询语义，一套 SQL 走天下，不再需要按方言各写一份。
- **函数适配真库实证修正**（8 项，均经多库实测）：
  - `to_char`/`to_date` 裸 `HH` 统一为 **24 小时制**（小写 `hh` 为 12 小时）——修正 DB2/H2 上按 12 小时输出的静默错值；
  - `to_date` 目标为 pg 系/HANA/ClickHouse 且格式含时间时自动改 `to_timestamp`/`toDateTime`，杜绝时间被静默截断；
  - `trim(from x)` 在 mysql 9.3/oracle 23ai 报语法错，自动改写等价 `trim(x)`/`ltrim`/`rtrim`；
  - `substr` 起点 0 按"从开头截取"统一；sqlite 目标下 `mid` 自动转 `substr`；
  - `left/right(str,0)` 与负数长度按 mysql 契约返回空串；`instr` 支持四参形态；
  - `find_in_set` 支持 `=1/=2/>1/<3` 位置比较；`datediff` 的 oceanbase 并入 oracle 分支。
- **分页 count 缓存按方言隔离**：同一 SQL 经 `redoDataSources`/多数据源在不同方言库执行时，count 结果不再互相串用；修复跨库适配自定义 countSql 污染共享配置的缺陷。
- **lambda 细化**：新增 `likeExact/likeExactLeft/likeExactRight`（值中的 `%` 按字面量匹配，用户输入含 `%` 不再被当通配符）；`setExpr` 引号内字面量 `?` 快速失败；空 groupBy/having 跳过组装。
- **SqlScriptLoader 线程安全加固**。

## 配套

- quickstart 新增 [Lesson 10：lambda 条件](https://github.com/sagframe/sqltoy-quickstart)（含 `likeExact` 通配符对比、`setExpr`、getOne/matchAll 守卫、join 演示）；helloworld 演示项目同步 lambda 用例。
- 文档：[升级指南](https://sagframe.github.io/sqltoy-docs/#/introduction/upgrade_6.0)新增 6.0.9 节；`updateSaveFetch` 说明完善并置于 `updateFetch` 之前（台账类高并发场景首选）。
