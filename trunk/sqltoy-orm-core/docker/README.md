# 真库测试环境（Docker Compose + 探针配置）

本目录让"真库用例"在任何机器上可一键复现；**没起容器的机器上相关用例会自动跳过**，不会把 `mvn test` 染红。

## 1. 起容器

```bash
cd trunk/sqltoy-orm-core
# 官方镜像源：
docker compose -f docker/docker-compose.yml up -d
# 国内/受限网络(只影响 Docker Hub 镜像，DB2/SQL Server 仍走原仓库)：
REGISTRY_PREFIX=docker.1panel.live/ docker compose -f docker/docker-compose.yml up -d
docker compose -f docker/docker-compose.yml ps        # 等各服务 healthy
```

| 服务 | 容器名 | 宿主机端口 | 账号口令 | 与用例约定的对齐点 |
|---|---|---|---|---|
| MySQL 8.4 | sqltoy-mysql | 3307 | root / Sqltoy@2026 | sweep 用例：`3307/sqltoy` |
| PostgreSQL 17 | sqltoy-postgres | 5433 | postgres / Sqltoy@2026 | sweep 用例：`5433/postgres` |
| ClickHouse 25.3 | sqltoy-clickhouse | 8123 / 9000 | sqltoy / Sqltoy@2026 | sweep 用例：`8123/default?user=sqltoy` |
| Oracle 23 Free | sqltoy-oracle | 1521 | system / Sqltoy@2026 | sweep 用例：`//1521/FREEPDB1` |
| DB2 11.5.9 | sqltoy-db2 | 50000 | db2inst1 / Sqltoy@2026 | sweep 用例：`50000/SQLTOYDB` |
| SQL Server 2022 | sqltoy-sqlserver | 1434 | sa / Sqltoy@2026 | sweep 用例：`1434` |

> 口令均为**本地开发容器专用**，不要复用到任何共享/生产环境。
> DB2 初始化最慢（首次 2–5 分钟），`docker compose ps` 看到 `healthy` 再跑测试。

## 2. 生成探针配置

各真库用例读取 `target/<db>-probe.properties`（`target/` 已在 `.gitignore` 中，凭据不入库）：

```bash
# bash / git-bash
cp docker/probe-properties/*.properties target/
```
```powershell
# PowerShell
Copy-Item docker/probe-properties/*.properties target/
```

只起部分库也没问题：缺哪个库的探针，对应用例就跳过。

## 3. 跑测试

```bash
mvn test                                   # 全量：H2/SQLite 内嵌库 + 已就绪真库
mvn test -Dtest='*RealDbSmokeTest'         # 只跑真库冒烟
```

跳过语义（见 `src/test/java/org/sagacity/sqltoy/support/RealDbProbeSupport.java`）：

| 场景 | 行为 |
|---|---|
| 探针文件缺失 | 该库用例 `Assumptions.abort` → 跳过 |
| 探针已配置但端口不通 | 跳过（并打印 `probe not reachable`） |
| 多库矩阵 sweep 一个库都不可达 | 整类跳过（并列出不可达清单） |
| 多库矩阵 sweep 部分库不可达 | 不可达的库标记 `[probe-skip]`，其余库仍全量断言 |

## 4. Compose 之外的库

`hana / dm / kingbase / highgo / opengauss / vastbase / tidb / doris / starrocks` 体积大或需授权镜像，**不放进默认 Compose**；它们对应的 sweep/冒烟用例在缺库时按上表跳过，完整矩阵请放在 CI 的镜像列表里按需扩展本文件。

**Impala 特别说明**：它不是单容器能起的（需 impalad + catalogd + statestored + Hive Metastore + HDFS/S3），
且 JDBC 驱动在 Cloudera 仓库（Maven Central 没有），因此不做本地 Compose。它的"集合语句 LIMIT 只作用于
最后一个 SELECT"行为已按[官方 UNION 文档](https://impala.incubator.apache.org/docs/build/plain-html/topics/impala_union.html)
纳入 `DefaultDialectUtils` 的门禁，并由 `DefaultDialectUtilsSetOperationWrapTest`（不依赖数据库）锁定 SQL 形态。
若在真实 Impala 环境复核，跑这两条即可定界：`select x from few_ints union all select 10 limit 1`
（返回 5 行 = LIMIT 未作用于整体，需包裹）与 `select * from (select x from few_ints union all select 10) t limit 1`（1 行）。

## 5. 清理

```bash
docker compose -f docker/docker-compose.yml down          # 停容器，保留数据卷
docker compose -f docker/docker-compose.yml down -v       # 连数据卷一起删
```

## 6. 常见问题

| 现象 | 处理 |
|---|---|
| 拉镜像 `denied` / 超时 | 用 `REGISTRY_PREFIX=docker.1panel.live/`（或其他可用镜像加速前缀） |
| `up` 报 port is already allocated | 先 `docker ps` 找出占用 3307/5433/8123/1521/50000/1434 的容器并 `docker stop`（常见于手工起过同名探针容器） |
| CI 里容器没起也"绿"了 | 矩阵 sweep 默认缺库即跳过；CI 应加 `-Dsqltoy.realdb.strict=true`，缺库直接失败 |
| 本机只想跑内嵌库(H2/SQLite) | 不复制 `docker/probe-properties/*` 即可，真库用例全部跳过 |`
