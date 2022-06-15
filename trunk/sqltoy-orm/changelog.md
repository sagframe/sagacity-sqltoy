# v5.2.0  2022-6-18
1、实现跟spring强耦合剥离，单独出org.sagacity.sqltoy.integration包，供其他框架集成扩展

# v5.1.46 2022-06-11
1、缓存翻译配置支持多个配置文件,默认配置为:classpath:sqltoy-translate.xml;classpath:translates
2、select concat(id,'/',amt) from 查询出byte[] 数组,并映射到对象字符类型属性上的场景优化
3、where #[条件] limit 条件不成立变成了where limit ，增加了limit 关键词处理，同时补全where 1=1避免特殊情况
4、NumberUtil中数字转英文金额支持万亿以上单位

# v5.1.43 2022-05-28
1、修复postgresql identity主键策略保存返回id跟建表字段顺序有关的缺陷(identity 主键不在第一列)

# v5.1.42 2022-05-25
1、支持多字段in，例如:(id,type) in ((:ids,:types)) 或 (id,type) in (:ids,:types)

# v5.1.38 2022-05-18
1、优化级联加载OneToOne 关联多条的验证,当为多条抛出异常
2、针对loadAll 场景，优化逻辑兼容ManyToMany、ManyToOne
3、summary汇总求平均计算增加skip-single-row属性(默认false)，可设置单行分组记录不进行汇总求平均计算

# v5.1.36 2022-05-12
* 1、在sql xml的filters中扩展一个自定义条件参数处理器
* 2、优化wrapTreeTable组织树结构过程中idField数据类型判断，减少手工设置
* 3、unpivot列转行支持多组列转行(参见word文档)
* 4、@if()宏条件判断增强，支持无参数判断@if(1==1) 便于跟@loop等组合使用

# v5.1.33 2022-04-24
* 1、处理in条件查询语句参数数组长度超过1000场景的处理
```
 t.order_id (not) in (:orderIds) 转变成
( t.order_id in (?,?..) or t.order_id in (?,?..)) 或 
( t.order_id not in (?,?..) and t.order_id not in (?,?..))
```
* 2、findBySql(sql,map,resultType) 支持当查询单列时、resultType为原生类型，返回一维 List<T> 模式
* 3、分页页数超出范围处理策略参数:spring.sqltoy.pageOverToFirst默认值改为false(原本跳到第一页变为返回空集合)
* 4、升级spring等pom依赖版本

# v5.1.31 2022-03-29
* 1、兼容查询结果是空字符映射到数字等类型的处理
* 2、通过链式操作中deeply实现saveOrUpdateAllDeeply想要的功能:lazyDao.save().deeply(true).saveMode(SaveMode.UPDATE).many(entities)
* 3、优化sql日志输出，可以通过debug="true" 属性来控制日志是否输出

# v5.1.30 2022-03-21
* 1、修复updateByQuery因where参数为in (?) 模式导致参数长度和参数类型长度不一致缺陷

# v5.1.29 2022-03-19
* 1、修复summary算法，修复逆向汇总时add(0,createSummaryRows)改为addAll(0,createSummaryRows)
* 2、filters中的to-date 日期加减操作单位支持months、years

# v5.1.25 2022-02-13 
* 1、sql文件filters中增加clone方法
* 2、findByQuery(QueryExecutor query) 结果QueryResult增加List getFirstColumn(boolean distinct) 方法，便于提取结果集单列值形成一维数组

# v5.1.24 2022-01-18 
* 1、修复NumberUtil 数字转英文大写金额负数问题
* 2、优化ParamFilterUtils类，支持多个primary 参数过滤处理器场景
* 3、升级部分pom依赖版本

# v5.1.23 2022-01-04 
* 1、增加deleteByIds快捷删除方法
* 2、修复deleteByQuery中的ParamsFilter使其生效
* 3、优化缓存翻译cache-indexs配置错误引起的数组越界提示，提供完整的错误提示

# v5.1.22 2021-12-26
* 1、updateByQuery支持set field=field+1 模式的自依赖更新
* 2、findByQuery或findPageByQuery等QueryExecutor中可设置hiberarchy=true并通过对象中@OneToOne和@OneToMany将结果封装成对象层次结构返回

# 5.1.21 2021-12-19
* 1、去除多余的log4j2的依赖,sqltoy本身一直都不依赖log4j2(演示功能剥离时漏删除)
* 2、优化一些校验和错误提示

# 5.1.20 2021-12-11
* 1、升级log4j2依赖版本为2.15.0(实际sqltoy并不依赖log4j2,log4j2为多余的依赖，下版去除)
* 2、sql补全优化，如from table where xxx 情况下自动补全select *
* 3、增加一些容错校验判断

# 5.1.19 2021-12-04
* 1、修复updateByQuery功能对象或Map传where条件参数值的一个bug

# 5.1.18 2021-11-29
* 1、优化POJO注解解析，支持多级父类，适度优化代码书写

# 5.1.17 2021-11-18
* 1、增加 @ Translate 缓存翻译注解

# 5.1.16 2021-11-13
* 1、优化sql在跨数据库函数适配转换时支持函数嵌套
* 2、完善sqltoy中统一字段赋值，在oracle、db2、sqlserver场景下调用saveOrUpdate/All 时因无法判断是新增还是修
改，导致统一赋值中关于新增记录的诸如：创建人、创建时间等属性无法赋值，现通过组织merge into 语句时伪造成默认值形式实现
* 3、增强findEntity中select(fields) pojo属性名转表字段名称能力
* 4、增强sql中在特殊场景下使用@value(:sqlPart)引入sql片段时，做跨数据库函数适配

# 5.1.15 2021-11-10
* 1、增加数据库表字段值加密存储、查询解密，默认采用RSA算法实现
* 2、将脱敏变成接口化，框架提供默认实现，便于开发者扩展
* 3、增加MapKit便于快速构造Map进行传参

# 5.1.14 2021-10-29
* 1、优化executeSql增加存在分库分表场景
* 2、batchUpdate增加公共属性赋值(创建人、创建日期、修改人、修改日期等)
* 3、updateByQuery、deleteByQuery、updateFetch完善分库分表场景
* 4、优化sql中以？(问号)传参且有@fast()场景下按比例取Top记录和取随机记录时参数位置处理缺陷

# 5.1.13 2021-10-26
* 1、完善查询传参数合法性验证
* 2、升级依赖包版本

# 5.1.11 2021-10-20
* 1、修复5.1.9版本引起的分页查询sql语句中以？模式传参场景下的bug(:paramName模式不受影响)
* 2、优化sqlserver分页查询order by 语句在#[@if(:xxx==xxx1) order by field] 场景下@if 不成立时去除了order by 导致判断是否存在order by 缺陷

# 5.1.10 2021-10-18
* 1、增加统一数据权限传参和越权检查，参照文档统一字段处理章节配置
* 2、兼容sql中参数名称含中文的特殊场景
* 3、@loop(:paramName,sql片段)支持类似:staffInfos[i].birthday 对象子属性模式，为应对极端场景提供策略
* 4、代替group_concat的link对应的id-column改为id-columns支持多列分组(依旧兼容id-column)

# 5.1.8 2021-10-09
* 1、batchUpdate方法支持List传参数模式，同时支持子类属性
* 2、修复sql去除回车换行导致@if()中或(||)符号被替换的bug

# 5.1.6 2021-09-28
* 1、排查修复db2 updateSaveFetch锁语句为for update with rs
* 2、优化queryResult中labelTypes类型处理(针对存在缓存翻译、格式化场景改变了结果类型)
* 3、升级依赖库版本

# 5.1.4 2021-09-17
* 1、findEntity可以用entity查询但返回结果类型是dto
* 2、优化IdUtil产生主键依赖的通过ip获取serverId场景，规避无网卡情况下无法获取ip的特殊情况
* 3、优化sql日志输出，减少过多的空白影响sql阅读
* 4、增强部分跨数据库sql函数自适应

# 5.1.3 2021-09-15
* 1、增加获取数据库表信息和表字段信息功能

# 5.1.2 2021-09-07
* 1、修复updateSaveFetch复合主键场景下的bug，遗漏了条件中的and
* 2、修复sqlite数据库identity主键策略insert的缺陷
* 3、优化saveOrUpdate，增加当主键值为null时直接走save操作

# 5.1.0 2021-09-01
* 1、针对类似库存台账、客户资金帐等强事务、高并发场景，提供updateSaveFetch新的一次数据库交互完成锁查询、更新、记录不存在则插入、返回的全过程的方法

# 5.0.13 2021-08-26
* 1、支持数据库表字段默认值为空白的场景(配合quickvo-4.18.18版本)
* 2、delete级联删除时增加了级联字段值是否为null的验证(针对级联字段非主键场景)，当为null抛出非法异常

# 5.0.12 2021-08-21
* 1、修复NumberUtil类数字转中文金额过亿元万位数全为零的一个缺陷
* 2、elastic sql http模式执行下增加@blank(:name)和@value(:name)

# 5.0.11 2021-08-17
* 1、优化重复sqlId处理，进行集中提示并做异常退出处理
* 2、依赖的jar包版本升级

# 5.0.9 2021-08-5
* 1、支持impalajdbc，主要针对kudu进行了集成，进一步增强大数据体系下的应用
* 2、findEntity的EntityQuery增加了groupBy()和having() 参数设置
* 3、优化支持elasticsearch原生sql取top记录的sql组织方式
* 4、优化sql文件加载日志提示

# 5.0.7 2021-07-14
* 1、修复oracle数据库oracle.sql.TIMESTAMP 转LocalDateTime的支持

# 5.0.6 2021-07-13
* 1、优化查询过程中首次调用缓存无法打印查询自身sql日志，因sql日志在当前线程中，调用缓存时会冲掉当前线程中的日志

# 5.0.5 2021-07-11
* 1、修复分页并行查询时，用到缓存翻译，且缓存正好是第一次被加载导致当前线程日志被置空产生的异常

# 5.0.3 2021-07-7
* 1、查询参数findBySql(String sql,Map paramsMap,Class resultType) map支持多层子对象属性
* 2、SqlToyDaoSupport中增加findPageBySql(final Page page, final String sqlOrNamedSql,
final Map<String, Object> paramsMap, Class voClass) 方法

# 5.0.1 2021-07-01
* 1、修复生成POJO含schema时生成update语句时2次拼接schema的错误

# 5.0.0 2021-06-23
* 1、规整4.x版本的代码目录，使其更加科学
* 2、去除executor目录，将QueryExecutor 对外的模型统一移入model目录下面
* 3、将非对外的内部模型移入到model.inner 包下面，将一些配置化的模型移入到config.model下面
* 4、将PaginationModel 改为Page，并将PageNo 由Long改为long，避免需要写1L，简化书写
* 5、优化support下面的LinkSupport，BaseSupport
* 6、剔除掉LinkSupport和BaseSupport，合并到SqlToyDaoSupport
* 7、去除一些根本用不到的方法，避免产生疑问和混淆，使得SqlToyDao更加清晰
* 8、去除updateFetchTop、updateFetchRandom
* 9、去除：public Long executeSql(String sqlOrNamedSql, Serializable entity, ReflectPropsHandler reflectPropertyHandler)带有reflectPropertyHandler 的开放方法
* 10、去除@ListSql @PageSql @LoadSql 这些注解，尽量让使用方法归一
* 11、去除ObtainDataSource，避免跟DataSourceSelector产生功能重叠
* 12、将ConnectonFactory移入org.sagacity.sqltoy.plugins.datasource包下
* 13、剔除findAll方法，用findEntity(Class voClass,null) 代替findAll方法属于极小众方法
* 14、并行查询设置分页模型方法：pageMode(Pagination pageModel)改为page(Page page)
* 15、增加loadEntity方法，通过EntityQuery获得单条记录：
 public <T extends Serializable> T loadEntity(Class<T> entityClass, EntityQuery entityQuery);
* 16、增加numFmt（numberFormat) 对英文金额转大写的支持
   <number-format columns="total_amt" format="capital-en"/>
* 17、删除对SybaseIQ数据库的支持
* 18、优化部分不使用的代码和注释

# v4.18.25 2021-06-08
* 1、修复oracle无主键表保存操作一个NPP错误
* 2、优化defaultDataSource获取模式，适应dynamic-datasource插件场景
* 3、在sqlToyContext中扩展了ConnectionFactory 供自定义获取当前ThreadLocal中的connection的机制，为非spring场景做铺垫

# v4.18.22 2021-05-26
* 1、在findEntity中EntityQuery可以设置fetchSize
* 2、在sqltoyContext中可以全局设置fetchSize,如: spring.sqltoy.fetchSize=200
* 3、convertType 支持空集合返回空集合
* 4、针对一些特殊原因导致表名是数据库关键词的处理支持

# v4.18.21 2021-05-21
* 1、修复elasticsearch sql查询的一个NPP错误
* 2、date-format增加locale选项，支持英文等日期格式化
* 3、针对分页和取top记录和取随机记录方法适度进行了规整，避免每个方言里面重复书写

# v4.18.18 2021-04-26
* 1、优化极端场景下sql中单行注释处理

# v4.18.13 2021-04-15
* 1、增加DataSourceSelector扩展，提供在多数据源特殊场景下可自行扩展

# v4.18.8 2021-03-24
* 1、优化clickhouse修改和删除操作，增加对dorisdb的支持

# v4.18.7 2021-03-12
* 1、兼容pojo或dto的属性名称含下划线的特殊场景，如：staff_name 和对应的getStaff_Name()
* 2、分页页号越界时可统一配置是否从第一页开始:spring.sqltoy.pageOverToFirst默认为true
* 3、列转行允许将结果映射到vo/pojo,map等类型上
* 4、优化loadAll复合主键场景下的处理逻辑以及附带的级联加载逻辑

# v4.18.3 2021-2-26
* 1、级联操作进行优化，精简级联配置，增加OneToOne类型的支持

```java
	// 可以自行定义oneToMany 和oneToOne
	// fields:表示当前表字段(当单字段关联且是主键时可不填)
	// mappedFields:表示对应关联表的字段
	// delete:表示是否执行级联删除，考虑安全默认为false(有实际外键时quickvo生成会是true)
	@OneToOne(fields = { "transDate", "transCode" }, mappedFields = { "transDate", "transId" }, delete = true)
	private ComplexpkItemVO complexpkItemVO;
```
* 2、修复xml定义sql中number-format和date-format多个参数换行没有trim的缺陷
* 3、优化cache-arg 反向通过名称匹配key，将之前字符串包含变为类似数据库like模式，可以实现：中国 苏州 带空格的模式匹配
* 4、quickvo 进行级联优化适配升级，版本4.18.3

# v4.13.11 2020-7-31
* 1、修复EntityManager中对OneToMany解析bug(4.13.10 版本已经调整)。

```java
for (int i = 0; i < idSize; i++) {
	// update 2020-7-30 修复取值错误,原:var = oneToMany.mappedFields()[i];
	var = oneToMany.fields()[i];
	for (int j = 0; j < idSize; j++) {
		idFieldName = idList.get(j);
		if (var.equalsIgnoreCase(idFieldName)) {
			// 原mappedFields[j] = var;
			mappedFields[j] = oneToMany.mappedFields()[i];
			mappedColumns[j] = oneToMany.mappedColumns()[i];
			break;
		}
	}
}
```
* 2、修复loadCascade时Class... cascadeTypes 模式传参判空处理错误，导致无法实际进行加载(4.13.7 版本修改导致)

```java
protected <T extends Serializable> T loadCascade(T entity, LockMode lockMode, Class... cascadeTypes) {
	if (entity == null) {
		return null;
	}
	Class[] cascades = cascadeTypes;
	// 当没有指定级联子类默认全部级联加载(update 2020-7-31 缺失了cascades.length == 0 判断)
	if (cascades == null || cascades.length == 0) {
		cascades = sqlToyContext.getEntityMeta(entity.getClass()).getCascadeTypes();
	}
	return dialectFactory.load(sqlToyContext, entity, cascades, lockMode, this.getDataSource(null));
}
```

* 3、全部修复update级联时，在mysql、postgresql子表采用原生sql进行saveOrUpdateAll的bug，分解为:先update后saveIgnoreExist模式
* 4、提供代码中动态查询增加filters,便于今后文本块应用sql直接写于代码中情况下可以动态调用缓存翻译、filters等功能

```java
@Test
public void findEntityByVO() {
	List<StaffInfoVO> staffVOs = sqlToyLazyDao.findEntity(StaffInfoVO.class,
			EntityQuery.create().where("#[staffId=:staffId]#[and staffName like :staffName] #[ and status=:status]")
					.values(new StaffInfoVO().setStatus(-1).setStaffName("陈").setStaffId("S0005"))
					.filters(new ParamsFilter("status").eq(-1)).filters(new ParamsFilter("staffName").rlike())
					.filters(new ParamsFilter("staffId").primary()));
	System.err.println(JSON.toJSONString(staffVOs));
}
```

# v4.8.2 2019-10-12
* 1、修复#[@if(:param==null) and t.name=:name] 其中:param为null则剔除整个#[] 之间sql的不合理规则
* 2、开放TranslateCacheManager的扩展注入，便于可以替换目前的默认实现
* 3、增加了缓存更新检测的集群节点时间差异参数[sqltoy-translate.xml配置]，保障集群环境下缓存更新检测的时效：
   <cache-update-checkers cluster-time-deviation="1">

# v4.8.1 2019-09-24
* 1、优化将查询部分PreparedStatement 原: ResultSet.TYPE_SCROLL_INSENSITIVE 改为ResultSet.TYPE_FORWARD_ONLY，提升效率。
* 2、修复根据方言提取sql部分缺陷。

# v4.8.0 2019-09-17
* 1、调整了plugin包为plugins，下面的类目录做了适当调整，便于今后扩展
* 2、大幅优化改进跨数据库函数替换处理模式，将之前执行时函数替换改进为缓存模式，相同数据库类型的存在则直接提取，不存在则进行函数替换并放入缓存中，大幅提升效率
* 3、优化了IUnifyFieldsHandler接口，增加public IgnoreCaseSet forceUpdateFields()方法，便于提供统一公共字段强制修改策略，如最后修改时间

```java     
    /* (non-Javadoc)
	 * @see org.sagacity.sqltoy.plugins.IUnifyFieldsHandler#forceUpdateFields()
	 */
	@Override
	public IgnoreCaseSet forceUpdateFields() {
		//强制updateTime 和 systemTime 进行修改
		IgnoreCaseSet forceUpdates=new IgnoreCaseSet();
		forceUpdates.add("updateTime");
		forceUpdates.add("systemTime");
		return forceUpdates;
	}
```
* 4、修复函数替换sysdate 无括号匹配处理的缺陷
* 5、其他一些优化

# v4.6.4 2019-09-10
* 1、改进缓存更新检测机制由timer定时改为线程内循环检测。
* 2、改进shardingDataSource检测机制由Timer定时改为线程内循环检测
* 3、修复DTO 中当同时有 isName 和 Name （isXXXX 和 XXXX 字段）时映射错误,强化isXXXX形式是boolean类型判断。
* 4、增强函数to_char和date_format 跨数据库时自动替换，实现mysql和oracle等情况下sql的通用性
* 5、升级fastjson依赖版本
* 6、其他一些优化

# v4.6.2 2019-08-26
* 1、优化SQL文件发生变更重新加载的机制，变成独立的进程进行文件变更监测，代替原来根据sqlId获取sql内容时检测机制。
* 2、增强了缓存翻译缓存初始化文件策略
* 3、增加多数据源连接信息debug输出，便于开发阶段识别
* 4、增加了容器销毁时自动销毁定时检测任务和缓存管理器

```xml
<bean id="sqlToyContext" class="org.sagacity.sqltoy.SqlToyContext" init-method="initialize" destroy-method="destroy">
</bean>
```
	
# v4.6.0 2019-08-12
* 1、全面支持jdk8 的LocalDate、LocalDateTime、LocalTime 日期类型；
* 2、优化EntityManager 解析对象的代码，避免子类中定义父类中属性导致被覆盖问题；
* 3、quickvo也同步进行了修改，默认类型为jdk8的日期类型
* 4、提交sqltoy-showcase 范例代码，请参见src\test 下面的用例 和 src\main\resources 下面的配置生成vo请参见sqltoy-showcase\tools\quickvo 下面的配置

# v4.5.3 2019-07-19
* 1、优化一些注释
* 2、增强泛型支持，避免：(VO)load(xxxVO) 这种前面需要强转换的操作
* 3、增强with as 语法几种模式的解析，如：with t1 (p1,p2) as () select * from t1 模式
* 4、sql文件变更解析改成同步机制，避免应用启动阶段因个别任务执行sql导致重复加载
* 5、sql模型增加是否有union语法的判断变量并优化部分机制，避免执行过程中去解析判断是否有union 和with as语法，从而提升效率。

# v4.5.2 2019-07-16
* 1、将统一接口层面的异常抛出去除，统一内部抛出RuntimeException，避免开发者强制要进行异常捕获处理（关键更新，因此版本号从4.3升级到4.5，但不影响之前的使用）
* 2、cache-arg 缓存条件转换二级过滤逻辑bug修复。
* 3、sql查询filter中to-date 过滤处理器里面增加了first_week_day，last_week_day 类型，取指定日期所在周的第一和最后一天
* 4、部分注释增强和少量代码优化
* 5、针对jdk11 进行了适应性代码改造，将已经废弃的写法重新依据新标准改写，因此sqltoy完全可以在jdk8和以后的版本中应用
* 6、增强对update xx set #[, field=？ ] 场景语句set 和 后续语句连接存在多余的逗号问题的兼容处理

# v4.3.12 2019-06-27
* 1、修复select * from table where instr(a.name,'xxx?x') and a.type=:type 形式查询问号导致的问题
* 2、放开参数条件名称不能是单个字母的限制，如 where a.name like :n ，单个字母参数命名一般不严谨，不建议如此简化，基本无法表达意思，考虑有些开发思维不够严谨，特此兼容
* 3、优化sql执行时长超过阀值的打印日志格式，便于从日志中快速找到性能慢的sql
* 4、优化部分代码，明确Exception的类型，避免部分条件检查直接抛出Exception，而是明确具体的条件非法或其他的准确的异常，避免事务控制时无法判断RunException 还是checkException

# v4.3.10 2019-05-08
* 1、支持elasticsearch6.x版本的restclient
* 2、强化sql查询固定参数中的问号处理
* 3、注释优化

# v4.3.7.2 2019-04-12
* 1、修复sql文件加载剔除非相同方言的sql文件的bug

# v4.3.7 2019-04-02
* 1、增加数字格式化精度设置(四舍五入、去位、进位等)
* 2、增加基于springsecurity安全框架下通用字段的赋值实现(可选,根据项目情况选择使用，不影响项目)

# v4.3.6.1 2019-02-25
* 1、修复查询语句中to-date('2019-01-01 12:20:30') 给定固定值情况下将:dd 作为:paramNamed 变量参数条件问题
* 2、强化分页查询对分组和统计型语句的判断，解决因为子查询中存在统计函数，将sql误判为统计型查询，count语句没有最优化
* 3、优化缓存条件过滤，未匹配情况下的赋值问题

# v4.3.6 2019-02-22
* 1、修复sql中别名参数正则表达式，避免to-date('2019-02-01 12:21:30','yyyy-MM-dd HH:mm:ss') 直接将具体数值写入sql时未能区分出:paramNamed 格式
* 2、优化缓存条件筛选过滤机制，当未匹配上时返回指定值

# v4.3.5 2019-01-28
* 1、优化redis全局业务主键生成格式，变成sqltoy_global_id:tableName:xxxxx 树形格式，之前是sqltoy_global_id_tablename_xxx 导致redis里面结构不清晰

# v4.3.4 2019-01-22
* 1、优化缓存检测更新时间，按照:yyyy-MM-dd HH:mm:ss 格式，避免检测存在毫秒级判断误差

# v4.3.3 2019-01-15
* 1、增加从缓存中模糊查询获取sql条件中的key值，直接作为条件参与查询，避免sql中关联模糊查询，从而简化sql并提升查询性能

# v4.2.22 2018-12-16
* 1、filter equals 判断当 时，当参数是日期类型对比异常，增加容错性。
* 2、将中文金额的圆统一成元，2004年后国家统一标准
* 3、修复缓存翻译定时检测参数处理问题

# v4.2.20 2018-11-27
* 1、mysql8 锁记录语法优化为 for update [of table] skip locked
* 2、对代码进行注释加强

# v4.2.17 2018-11-10
* 1、sql剔除末尾的分号和逗号，增强容错性，分号开发者会经常从客户端copy过来容易忘记剔除
* 2、在mysql中带有sum等统计函数的查询，结果集存在全是nulll的情况(一般结果就一条)，sqltoy通过sql中增加注释--#ignore_all_null_set# 方式告诉框架进行剔除，避免无效记录

# v4.2.16 2018-10-18
* 1、修复当设置缓存翻译配置文件为空白时，文件加载错误的bug
* 2、优化部分代码性能和规范一些正则表达式的统一定义

# v4.2.15 2018-9-30
* 1、修复不使用缓存翻译时，未对文件是否存在进行判断。
* 2、修复sql语句中进行注释时，先剔除-- xx -- 行注释，导致注释模式被剔除中间部分导致失效

# v4.2.14 2018-9-17
* 1、支持多个sql配置路径

```xml
<property name="sqlResourcesDir" value="classpath:com/sagframe;classpath:sqltoy" />
```

# v4.2.13 2018-9-6
* 1、修复业务主键跟数据库主键属于同一个字段时的长度控制问题
* 2、修复业务主键跟数据库主键同一个字段批量保存时的生成参数未指定正确问题

# v4.2.10 2018-8-24
* 1、修复分页查询组合count sql时：select a,from_days() days from table 取from位置bug

# v4.2.8 2018-6-5
* 1、增加日期格式化和数字格式化

```xml
<sql id="companyTrans">
    <!-- 敏感数据安全脱敏 -->
	<secure-mask columns="account_code" type="public-account"/>
	<secure-mask columns="link_tel" type="tel"/>
	<!-- 日期格式化 -->
	<date-format columns="trans_date" format="yyyy-MM-dd"/>
	<!-- 数字格式化,分:#,###.00 、capital、capital-rmb 等形式 -->
	<number-format columns="total_amt" format="capital-rmb"/>
</sql>
```

# v4.2.7 2018-5-28
* 1、业务主键策略可以根据多个字段组合形成。quickvo业务主键配置：  1),signature 增加${}引用related-columns 设置相关的列的值，@case() 进行类似oracle的decode函数处理，@df(${xxx},fmt) 对日期进行格式化，第一个参数缺省表示当天，第二个参数缺省为:yyMMdd。
    2)，related-columns可以维护多个数据库字段，用逗号分隔。

```xml
   <business-primary-key >
       <table name="OD_CONTRACT_INFO" column="CONTRACT_ID" 
          signature="${periodType}@case(${orderType},P,PO,S,SO,BN)${tradeType}@df(yyMMdd)" 
         related-columns="periodType,orderType,tradeType" length="12" generator="redis" />
   </business-primary-key>
```
* 2、缓存翻译可以一组代码进行同时翻译。如:某个字段结构是A,B,C这种格式，翻译结果为:A名称,B名称,C名称：

```xml
     <translate cache="dictKeyNameCache" columns="SEX_TYPE" split-regex="," link-sign=","/>
```

# v4.2.6 2018-5-19 
* 1、修复mysql、postgresql 执行saveOrUpdate时报：发生SQL 错误 [1048] [23000]: Column 'NAME' cannot be null

# v4.2.5 2018-5-12
* 1、修复mysql8.0 树形表设置节点路径时报sql错误的问题。

# v4.2.4 2018-5-3
* 1、修复@if（:param=='value' && :param1=='-1'）带单双引号后面紧跟+_符号的逻辑处理。
* 2、优化原生elasticsearch json语法解析错误提醒。
* 3、修复 elastic suggest 场景查询无法处理的问题
* 4、修复分页查询count语句优化处理时，在select from 之间有order by语句时处理异常问题。

# v4.2.3 2018-4-12
* 1、优化pom依赖,避免每次依赖oracle和其它一下特定需求情况下的依赖。
* 2、优化查询传参数验证提醒。
* 3、优化分页查询取count记录数时sql判断order by 并剔除的判断逻辑，确保剔除的精准。

# v4.2.2 2018-3-31
* 1、缓存翻译全部改为ehcache3.5.2版本，无需再定义cacheManager和ehcache.xml等，大幅减少配置。
* 2、缓存翻译采用了新的xml schema，支持sql、rest、service等策略。
* 3、缓存翻译增加了主动侦测数据是否发生变化，然后清空缓存的功能，且配置灵活，支持不同时间不同频率。
## bug修复：
* 1、sql语句@if(a== 'xxx' )逻辑判断，等号后面对比数据有空格时判断错误问题。
* 2、修复elasticsearch Sql查询时select count(*) count from xxxxx 没有group 时没有判断为聚合查询的bug。

# v4.1.0 2018-2-20
* 1、正式支持elasticsearch（两种模式:1、通过elasticsearch-sql模式和json原生模式）,已经经过项目应用。
* 2、正式支持redis集中式主键策略，已经正式项目应用通过。
* 3、正式支持redis缓存翻译，已经可以同时支持ehcache和redis
* 4、修复sql参数过滤的一个bug，将默认blank处理作为第一处理顺序。
* 5、对schema xsd文件进行了调整优化
* 6、quickvo 支持swagger api

# v4.0.9 2018-2-3
* 1、支持elasticsearch以及elasticsearch-sql插件

# v3.2.2 (2017-2-28)
* 1、优化取总记录数查询sql分析,排除统计性查询用分页形式查询产生的记录数量错误
* 2、更新依赖包

# v3.2.1 2016-12-1
* 1、修复pivot行转列的参照category列排序问题

# v3.2版本(2016年11月25日发布)
* 1、增加unpivot 列转行功能
* 2、修改了存储过程调用模式，剔除掉存储过程分页查询，修复oracle存储过程返回结果的执行错误
* 3、删除StoreUtils类
* 4、sql语句中增加#[@blank(:paramNamed) sql] 控制特性，便于组织sql
* 5、增加分页优化功能，避免每次都查询2次，在查询条件一致的情况下不再查询分页总记录数

```xml
<page-optimize alive-max="100" alive-seconds="900"/>
```