# sqltoy 6.0.12 发版公告

## 一、数组类型全链路支持

PostgreSQL/kingbase/highgo/openGauss 系、H2、ClickHouse 的数组列(`text[]`/`int[]` 等)完整支持:

- 实体 save/update/saveOrUpdate/saveAll 全链路读写,`String[]`/`List`/`int[]` 等类型自动定型原生绑定
- 数组列等值查询(`where tags = :tags`)、VO 回读自动映射
- 空数组/全 null 数组不再被静默置为 SQL NULL
- 日志 SQL 可直接复制执行,消除"日志能查出而程序报错"的假阳性
- DDL 生成数组列类型;无数组列的方言自动降维 JSON/文本承载

## 二、跨库部署类型族翻译

quickvo 按 PG 开发的实体(nativeType=jsonb/text[]/uuid/geometry 等)部署到 Oracle/MySQL/DM/SQLServer 等库时,cast 与 DDL 自动按目标方言翻译,不再产生非法 SQL。uuid、timestamptz 全链路支持。

## 三、lambda 编程式缓存翻译

```java
lambdaQuery(Staff.class)
    .translate("dictCache", "statusName")   // 同列原地翻译
    .translates(Translate.of("cache", "key", "target"))  // key→target 映射
    .eq(Staff::getStatus, 1).find();
```

## 四、新 API:updateFetch

`lightDao.updateFetch(entity, callback, uniqueProps)`:按实体锁查询,存在则回调修改并返回,不存在返回 null 不插入(与 updateSaveFetch 互补),全方言支持。

## 其它

- 超时 SQL 参数渲染修复数组乱码
- createArrayOf 类型名统一,修复 gaussdb 大写类型名报错
- DDL 方言细节:tinyint/LONGVARCHAR/CLOB/DM geometry 等
- 2824+ 单测全绿,20+ 数据库真库验证,quickstart/showcase 零回归

## 升级

坐标不变升 6.0.12,零配置变更。跨库部署建议用 quickvo 重新生成实体。
