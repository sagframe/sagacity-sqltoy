# WORD版详细文档(完整)
## 请见:docs/睿智平台SqlToy5.2 使用手册.doc
## xml中sql查询完整配置 
https://github.com/sagframe/sqltoy-online-doc/blob/master/docs/sqltoy/search.md

# [gitee地址](https://gitee.com/sagacity/sagacity-sqltoy) 
# [sqltoy Lambda](https://gitee.com/gzghde/sqltoy-plus) 
# [sqltoy管理系统脚手架](https://gitee.com/momoljw/sss-rbac-admin) 

# 范例演示项目
## 快速集成演示项目
* https://gitee.com/sagacity/sqltoy-helloworld
* 阅读其readme.md学习

## 快速上手功能演示项目
* https://github.com/sagframe/sqltoy-quickstart 
* 阅读其readme.md学习 

## POJO和DTO 严格分层演示项目
* https://github.com/sagframe/sqltoy-strict

## sharding分库分表演示
* https://github.com/sagframe/sqltoy-showcase/tree/master/trunk/sqltoy-sharding

## dynamic-datasource多数据源范例
* https://gitee.com/sagacity/sqltoy-showcase/tree/master/trunk/sqltoy-dynamic-datasource

## nosql演示(mongo和elasticsearch)
* https://github.com/sagframe/sqltoy-showcase/tree/master/trunk/sqltoy-nosql

## sqltoy基于xml配置演示
* https://github.com/sagframe/sqltoy-showcase/tree/master/trunk/sqltoy-showcase

# QQ 交流群:531812227
# 码云地址: https://gitee.com/sagacity/sagacity-sqltoy

# 最新版本 
* 5.3.59 (jdk17+/jdk21、springboot3.x)  发版日期: 2023-12-11
* 5.2.86 LTS (jdk1.8+)                 发版日期: 2023-12-11
  
# 历史版本(EOL)
* 5.1.81                             发版日期: 2023-12-06
* 4.20.87(兼容所有之前版本)                            发版日期: 2023-12-05

# 1. 前言
### (为更好更快的发展，愿转赠给想做开源或信创的规模型企业，可通过QQ交流)
## 1.1 sqltoy-orm是什么
   sqltoy-orm是JPA和超强查询的融合体，是简单业务、大型SaaS化多租户ERP、大数据分析多种类型项目实践过程的总结和分享。
 ### JPA部分
* 类似JPA的对象化CRUD、对象级联加载和新增、更新
* 强化update操作，提供弹性字段修改能力，不同于hibernate先load后修改，而是一次数据库交互完成修改，确保了高并发场景下数据的准确性
* 改进了级联修改，提供了先删除或者先置无效，再覆盖的操作选项
* 增加了updateFetch、updateSaveFetch功能，强化针对强事务高并发场景的处理，类似库存台账、资金台账，实现一次数据库交互，完成锁查询、不存在则插入、存在则修改，并返回修改后的结果
* 增加了树结构封装，便于统一不同数据库树型结构数据的递归查询
* 支持分库分表、支持多种主键策略(额外支持基于redis的产生特定规则的业务主键)、加密存储、数据版本校验
* 提供了公共属性赋值(创建人、修改人、创建时间、修改时间、租户)、扩展类型处理等
* 提供了多租户统一过滤和赋值、提供了数据权限参数带入和越权校验

### 查询部分
* 极为直观的sql编写方式，便于从客户端<-->代码 双向快速迁移，便于后期变更维护
* 支持缓存翻译、反向缓存匹配key代替like模糊查询
* 提供了跨数据库支持能力：不同数据库的函数自动转换适配，多方言sql根据实际环境自动匹配、多数据库同步测试，大幅提升了产品化能力
* 提供了取top记录、随机记录等特殊场景的查询功能
* 提供了最强大的分页查询机制:1)自动优化count语句;2)提供基于缓存的分页优化，避免每次都执行count查询;3)提供了独具特色的快速分页；4)提供了并行分页
* 提供了分库分表能力
* 提供了在管理类项目中极为价值的：分组汇总计算、行列转换(行转列、列转行)、同比环比、树形排序、树形汇总 相关算法自然集成
* 提供了基于查询的层次化数据结构封装
* 提供了大量辅助功能:数据脱敏、格式化、条件参数预处理等

### 支持多种数据库
* 常规的mysql、oracle、db2、postgresql、 sqlserver、dm、kingbase、sqlite、h2、 oceanBase、polardb、gaussdb、tidb、oscar(神通)
* 支持分布式olap数据库: clickhouse、StarRocks、greenplum、impala(kudu)
* 支持elasticsearch、mongodb
* 所有基于sql和jdbc 各类数据库查询

   
## 1.2 sqltoy-orm 发展轨迹
* 2007~2008年，做农行的一个管理类项目，因查询统计较多，且查询条件多而且经常要增加条件，就不停想如何快速适应这种变化，一个比较偶然的灵感发现了比mybatis强无数倍的动态sql写法，并作为hibernate jpa 查询方面的补充，收到了极为震撼的开发体验。可以看写于2009年的一篇博文: https://blog.csdn.net/iteye_2252/article/details/81683940
* 2008~2012年，因一直做金融类企业项目，所面对的数据规模基本上是千万级别的，因此sqltoy一直围绕jpa进行sql查询增强，期间已经集成了缓存翻译、快速分页、行列旋转等比其他框架更具特色的查询特性。
* 2013~2014年，因为了避免让开发者在项目中同时使用两种技术，因此在sqltoy中实现了基于对象的crud功能，形成了完整的sqltoy-orm框架。
* 2014~2017年, 在负责拉卡拉大数据平台过程中，对sqltoy进行了大幅重构，实现了底层结构的合理化，在拉卡拉CRM和日均千万累计达百亿级别的大数据平台上得到了强化和检验。
* 2018~至今,  在建设SaaS化多租户ERP复杂场景下和flink-cdc结合MPP数据库的实时数仓过程中得到了充分锤炼，sqltoy已经非常完善可靠，开始开源跟大家一起分享和共建！

# 2. 快速特点说明
## 2.1 对象操作跟jpa类似并有针对性加强(包括级联)
* 通过quickvo工具从数据库生成对应的POJO，注入sqltoy自带的LightDao完成全部操作

```java
   //三步曲：1、quickvo生成pojo，2、完成yml配置；3、service中注入dao(无需自定义各种dao)
   @Autowired
   LightDao lightDao;

   StaffInfoVO staffInfo = new StaffInfoVO(); 
   //保存
   lightDao.save(staffInfo);
   //删除
   lightDao.delete(new StaffInfoVO("S2007"));

   //public Long update(Serializable entity, String... forceUpdateProps);
   // 这里对photo 属性进行强制修改，其他为null自动会跳过
   lightDao.update(staffInfo, "photo");

   //深度修改,不管是否null全部字段修改
   lightDao.updateDeeply(staffInfo);

   List<StaffInfoVO> staffList = new ArrayList<StaffInfoVO>();
   StaffInfoVO staffInfo = new StaffInfoVO();
   StaffInfoVO staffInfo1 = new StaffInfoVO();
   staffList.add(staffInfo);
   staffList.add(staffInfo1);
   //批量保存或修改
   lightDao.saveOrUpdateAll(staffList);
   //批量保存
   lightDao.saveAll(staffList);
   ...............
   lightDao.loadByIds(StaffInfoVO.class,"S2007")
   //唯一性验证
   lightDao.isUnique(staffInfo, "staffCode");
```
## 2.2 支持代码中对象查询
* sqltoy 中统一的规则是代码中可以直接传sql也可以是对应xml文件中的sqlId
```java
/**
 * @todo 通过对象传参数,简化paramName[],paramValue[] 模式传参
 * @param <T>
 * @param sqlOrNamedSql 可以是具体sql也可以是对应xml中的sqlId
 * @param entity        通过对象传参数,并按对象类型返回结果
 */
 public <T extends Serializable> List<T> findBySql(final String sqlOrNamedSql, final T entity);
```
* 基于对象单表查询，并带缓存翻译
```java  
public Page<StaffInfoVO> findStaff(Page<StaffInfoVO> pageModel, StaffInfoVO staffInfoVO) {
     // sql可以直接在代码中编写,复杂sql建议在xml中定义
     // 单表entity查询场景下sql字段可以写成java类的属性名称
     return findPageEntity(pageModel,StaffInfoVO.class, EntityQuery.create()
	.where("#[staffName like :staffName]#[and createTime>=:beginDate]#[and createTime<=:endDate]")
	.values(staffInfoVO)
	// 字典缓存必须要设置cacheType
	// 单表对象查询需设置keyColumn构成select keyColumn as column模式
	.translates(new Translate("dictKeyName").setColumn("sexTypeName").setCacheType("SEX_TYPE")
         		.setKeyColumn("sexType"))
	.translates(new Translate("organIdName").setColumn("organName").setKeyColumn("organId")));
}
```
* 对象式查询后修改或删除
```java
//演示代码中非直接sql模式设置条件模式进行记录修改
public Long updateByQuery() {
     return lightDao.updateByQuery(StaffInfoVO.class,
		EntityUpdate.create().set("createBy", "S0001")
                     .where("staffName like ?").values("张"));
}

//代码中非直接sql模式设置条件模式进行记录删除
lightDao.deleteByQuery(StaffInfoVO.class, EntityQuery.create().where("status=?").values(0));
```
## 2.2 极致朴素的sql编写方式(本质规律的发现和抽象)

* sqltoy 的写法(一眼就看明白sql的本意,后面变更调整也非常便捷,copy到数据库客户端里稍作调整即可执行)
* sqltoy条件组织原理很简单: 如 #[order_id=:orderId] 等于if(:orderId<>null) sql.append(order_id=:orderId);#[]内只要有一个参数为null即剔除
* 支持多层嵌套:如 #[and t.order_id=:orderId #[and t.order_type=:orderType]] 
* 条件判断保留#[@if(:param>=xx ||:param<=xx1) sql语句] 这种@if()高度灵活模式,为特殊复杂场景下提供万能钥匙

```xml
//1、 条件值处理跟具体sql分离
//2、 将条件值前置通过filters 定义的通用方法加工规整(大多数是不需要额外处理的)
<sql id="show_case">
<filters>
   <!-- 参数statusAry只要包含-1(代表全部)则将statusAry设置为null不参与条件检索 -->
   <eq params="statusAry" value="-1" />
</filters>
<value><![CDATA[
	select 	*
	from sqltoy_device_order_info t 
	where #[t.status in (:statusAry)]
	#[and t.ORDER_ID=:orderId]
	#[and t.ORGAN_ID in (:authedOrganIds)]
	#[and t.STAFF_ID in (:staffIds)]
	#[and t.TRANS_DATE>=:beginAndEndDate[0]]
	#[and t.TRANS_DATE<:beginAndEndDate[1]]    
	]]>
</value>
</sql>
```

* mybatis同样的功能的写法

```xml
<select id="show_case" resultMap="BaseResultMap">
 select *
 from sqltoy_device_order_info t 
 <where>
     <if test="statusAry!=null">
	and t.status in
	<foreach collection="status" item="statusAry" separator="," open="(" close=")">  
            #{status}  
 	</foreach>  
    </if>
    <if test="orderId!=null">
	and t.ORDER_ID=#{orderId}
    </if>
    <if test="authedOrganIds!=null">
	and t.ORGAN_ID in
	<foreach collection="authedOrganIds" item="order_id" separator="," open="(" close=")">  
            #{order_id}  
 	</foreach>  
    </if>
    <if test="staffIds!=null">
	and t.STAFF_ID in
	<foreach collection="staffIds" item="staff_id" separator="," open="(" close=")">  
            #{staff_id}  
 	</foreach>  
    </if>
    <if test="beginDate!=null">
	and t.TRANS_DATE>=#{beginDate}
    </if>
    <if test="endDate!=null">
	and t.TRANS_DATE<#{endDate}
    </if>
</where>
</select>
```
## 2.3 天然防止sql注入,执行过程:

```xml
假设sql语句如下
select 	*
from sqltoy_device_order_info t 
where #[t.ORGAN_ID in (:authedOrganIds)]
      #[and t.TRANS_DATE>=:beginDate]
      #[and t.TRANS_DATE<:endDate]

java调用过程：
lightDao.find(sql, MapKit.keys("authedOrganIds","beginDate", "endDate").values(authedOrganIdAry,beginDate,null), DeviceOrderInfoVO.class);

最终执行的sql是这样的:
select 	*
from sqltoy_device_order_info t 
where t.ORDER_ID=?
      and t.ORGAN_ID in (?,?,?)
      and t.TRANS_DATE>=?

然后通过: pst.set(index,value) 设置条件值，不存在将条件直接作为字符串拼接为sql的一部分
```
 
## 2.4 最强大的分页查询
### 2.4.1 分页特点说明
* 1、快速分页:@fast() 实现先取单页数据然后再关联查询，极大提升速度。
* 2、分页优化器:page-optimize 让分页查询由两次变成1.3~1.5次(用缓存实现相同查询条件的总记录数量在一定周期内无需重复查询)
* 3、sqltoy的分页取总记录的过程不是简单的select count(1) from (原始sql)；而是智能判断是否变成:select count(1) from 'from后语句'，
并自动剔除最外层的order by
* 4、sqltoy支持并行查询：parallel="true"，同时查询总记录数和单页数据,大幅提升性能
* 5、在极特殊情况下sqltoy分页考虑是最优化的，如:with t1 as (),t2 as @fast(select * from table1) select * from xxx
这种复杂查询的分页的处理，sqltoy的count查询会是:with t1 as () select count(1) from table1,
如果是:with t1 as @fast(select * from table1) select * from t1 ,count sql 就是：select count(1) from table1

### 2.4.2 分页sql示例
```xml
<!-- 快速分页和分页优化演示 -->
<sql id="sqltoy_fastPage">
	<!-- 分页优化器,通过缓存实现查询条件一致的情况下在一定时间周期内缓存总记录数量，从而无需每次查询总记录数量 -->
	<!-- parallel:是否并行查询总记录数和单页数据，当alive-max=1 时关闭缓存优化 -->
	<!-- alive-max:最大存放多少个不同查询条件的总记录量; alive-seconds:查询条件记录量存活时长(比如120秒,超过阀值则重新查询) -->
	<page-optimize parallel="true" alive-max="100" alive-seconds="120" />
	<value><![CDATA[
	select t1.*,t2.ORGAN_NAME 
	-- @fast() 实现先分页取10条(具体数量由pageSize确定),然后再关联
	from @fast(select t.*
		   from sqltoy_staff_info t
		   where t.STATUS=1 
		   #[and t.STAFF_NAME like :staffName] 
		   order by t.ENTRY_DATE desc
		) t1 
	left join sqltoy_organ_info t2 on  t1.organ_id=t2.ORGAN_ID
	]]>
	</value>
	<!-- 这里为极特殊情况下提供了自定义count-sql来实现极致性能优化 -->
	<!-- <count-sql></count-sql> -->
</sql>
```
### 2.4.3 分页java代码调用

```java
/**
 *  基于对象传参数模式
 */
public void findPageByEntity() {
	Page pageModel = new Page();
	StaffInfoVO staffVO = new StaffInfoVO();
	// 作为查询条件传参数
	staffVO.setStaffName("陈");
	// 使用了分页优化器
	// 第一次调用:执行count 和 取记录两次查询
	Page result = lightDao.findPage(pageModel, "sqltoy_fastPage", staffVO);
	System.err.println(JSON.toJSONString(result));
	// 第二次调用:过滤条件一致，则不会再次执行count查询
	//设置为第二页
	pageModel.setPageNo(2);
	result = lightDao.findPage(pageModel, "sqltoy_fastPage", staffVO);
	System.err.println(JSON.toJSONString(result));
}
	
```

## 2.5 最巧妙的缓存应用，减少表关联查询
* 1、 通过缓存翻译:<translate> 将代码转化为名称，避免关联查询，极大简化sql并提升查询效率 
* 2、 通过缓存名称模糊匹配:<cache-arg> 获取精准的编码作为条件，避免关联like 模糊查询
```java
//支持对象属性注解模式进行缓存翻译
@Translate(cacheName = "dictKeyName", cacheType = "DEVICE_TYPE", keyField = "deviceType")
private String deviceTypeName;

@Translate(cacheName = "staffIdName", keyField = "staffId")
private String staffName;
```		
```xml
<sql id="sqltoy_order_search">
	<!-- 缓存翻译设备类型
        cache:具体的缓存定义的名称，
        cache-type:一般针对数据字典，提供一个分类条件过滤
	columns:sql中的查询字段名称，可以逗号分隔对多个字段进行翻译
	cache-indexs:缓存数据名称对应的列,不填则默认为第二列(从0开始,1则表示第二列)，
	      例如缓存的数据结构是:key、name、fullName,则第三列表示全称
	-->
	<translate cache="dictKeyName" cache-type="DEVICE_TYPE" columns="deviceTypeName" cache-indexs="1"/>
	<!-- 员工名称翻译,如果同一个缓存则可以同时对几个字段进行翻译 -->
	<translate cache="staffIdName" columns="staffName,createName" />
	<filters>
		<!-- 反向利用缓存通过名称匹配出id用于精确查询 -->
		<cache-arg cache-name="staffIdNameCache" param="staffName" alias-name="staffIds"/>
	</filters>
	<value>
	<![CDATA[
	select 	ORDER_ID,
		DEVICE_TYPE,
		DEVICE_TYPE deviceTypeName,-- 设备分类名称
		STAFF_ID,
		STAFF_ID staffName, -- 员工姓名
		ORGAN_ID,
		CREATE_BY,
		CREATE_BY createName -- 创建人名称
	from sqltoy_device_order_info t 
	where #[t.ORDER_ID=:orderId]
	      #[and t.STAFF_ID in (:staffIds)]
	]]>
	</value>
</sql>
```
## 2.6 并行查询

* 接口规范

```java
// parallQuery 面向查询(不要用于事务操作过程中),sqltoy提供强大的方法，但是否恰当使用需要使用者做合理的判断
/**
  * @TODO 并行查询并返回一维List，有几个查询List中就包含几个结果对象，paramNames和paramValues是全部sql的条件参数的合集
  * @param parallQueryList
  * @param paramNames
  * @param paramValues
  */
public <T> List<QueryResult<T>> parallQuery(List<ParallQuery> parallQueryList, String[] paramNames,
			Object[] paramValues);
```
* 使用范例

```java
//定义参数
String[] paramNames = new String[] { "userId", "defaultRoles", "deployId", "authObjType" };
Object[] paramValues = new Object[] { userId, defaultRoles, GlobalConstants.DEPLOY_ID,
		SagacityConstants.TempAuthObjType.GROUP };
// 使用并行查询同时执行2个sql,条件参数是2个查询的合集
List<QueryResult<TreeModel>> list = super.parallQuery(
		Arrays.asList(
		        ParallQuery.create().sql("webframe_searchAllModuleMenus").resultType(TreeModel.class),
				ParallQuery.create().sql("webframe_searchAllUserReports").resultType(TreeModel.class)),
		paramNames, paramValues);
		
```

## 2.7 最跨数据库
* 1、提供类似hibernate性质的对象操作，自动生成相应数据库的方言。
* 2、提供了最常用的:分页、取top、取随机记录等查询，避免了各自不同数据库不同的写法。
* 3、提供了树形结构表的标准钻取查询方式，代替以往的递归查询，一种方式适配所有数据库。
* 4、sqltoy提供了大量基于算法的辅助实现，最大程度上用算法代替了以往的sql，实现了跨数据库
* 5、sqltoy提供了函数替换功能，比如可以让oracle的语句在mysql或sqlserver上执行(sql加载时将函数替换成了mysql的函数),最大程度上实现了代码的产品化。
    <property name="functionConverts" value="default" /> 
    default:SubStr\Trim\Instr\Concat\Nvl 函数；可以参见org.sagacity.sqltoy.plugins.function.Nvl 代码实现
    
 ```properties
# 开启sqltoy默认的函数自适配转换函数
spring.sqltoy.functionConverts=default
# 如在mysql场景下同时测试其他类型数据库，验证sql适配不同数据库，主要用于产品化软件
spring.sqltoy.redoDataSources[0]=pgdb
# 也可以自定义函数来实现替换Nvl
# spring.sqltoy.functionConverts=default,com.yourpackage.Nvl
# 启用框架自带Nvl、Instr
# spring.sqltoy.functionConverts=Nvl,Instr
# 启用自定义Nvl、Instr
# spring.sqltoy.functionConverts=com.yourpackage.Nvl,com.yourpackage.Instr
```
* 6、通过sqlId+dialect模式，可针对特定数据库写sql,sqltoy根据数据库类型获取实际执行sql,顺序为:
    dialect_sqlId->sqlId_dialect->sqlId，
	如数据库为mysql,调用sqlId:sqltoy_showcase,则实际执行:sqltoy_showcase_mysql
```xml
<sql id="sqltoy_showcase">
	<value>
	<![CDATA[
	select * from sqltoy_user_log t 
	where t.user_id=:userId 
	]]>
	</value>
</sql>
<!-- sqlId_数据库方言(小写) -->
<sql id="sqltoy_showcase_mysql">
	<value>
	<![CDATA[
	select * from sqltoy_user_log t 
	where t.user_id=:userId 
	]]>
	</value>
</sql>
```
  
## 2.8 提供行列转换、分组汇总、同比环比、树排序汇总等

* 水果销售记录表

品类|销售月份|销售笔数|销售数量(吨)|销售金额(万元)
----|-------|-------|----------|------------
苹果|2019年5月|12 | 2000|2400
苹果|2019年4月|11 | 1900|2600
苹果|2019年3月|13 | 2000|2500
香蕉|2019年5月|10 | 2000|2000
香蕉|2019年4月|12 | 2400|2700
香蕉|2019年3月|13 | 2300|2700

### 2.8.1 行转列(列转行也支持)

```xml
<!-- 行转列 -->
<sql id="pivot_case">
	<value>
	<![CDATA[
	select t.fruit_name,t.order_month,t.sale_count,t.sale_quantity,t.total_amt 
	from sqltoy_fruit_order t
	order by t.fruit_name ,t.order_month
	]]>
	</value>
	<!-- 行转列,将order_month作为分类横向标题，从sale_count列到total_amt 三个指标旋转成行 -->
	<pivot start-column="sale_count" end-column="total_amt"	group-columns="fruit_name" category-columns="order_month" />
</sql>
```
* 效果

<table>
<thead>
	<tr>
	<th rowspan="2">品类</th>
	<th colspan="3">2019年3月</th>
	<th colspan="3">2019年4月</th>
	<th colspan="3">2019年5月</th>
	</tr>
	<tr>
	    <th>笔数</th><th>数量</th><th>总金额</th>
	    <th>笔数</th><th>数量</th><th>总金额</th>
	    <th>笔数</th><th>数量</th><th>总金额</th>
	</tr>
	</thead>
	<tbody>
	<tr>
		<td>香蕉</td>
		<td>13</td>
		<td>2300</td>
		<td>2700</td>
		<td>12</td>
		<td>2400</td>
		<td>2700</td>
		<td>10</td>
		<td>2000</td>
		<td>2000</td>
	</tr>
		<tr>
		<td>苹果</td>
		<td>13</td>
		<td>2000</td>
		<td>2500</td>
		<td>11</td>
		<td>1900</td>
		<td>2600</td>
		<td>12</td>
		<td>2000</td>
		<td>2400</td>
	</tr>
	</tbody>
</table>


### 2.8.2 分组汇总、求平均(可任意层级)
```xml
<sql id="group_summary_case">
	<value>
	<![CDATA[
	select t.fruit_name,t.order_month,t.sale_count,t.sale_quantity,t.total_amt 
	from sqltoy_fruit_order t
	order by t.fruit_name ,t.order_month
	]]>
	</value>
	<!-- reverse 是否反向 -->	
	<summary columns="sale_count,sale_quantity,total_amt" reverse="true">
		<!-- 层级顺序保持从高到低 -->
		<global sum-label="总计" label-column="fruit_name" />
		 <!-- order-column: 分组排序列(对同分组进行排序)，order-with-sum:默认为true，order-way:desc/asc -->
		<group group-column="fruit_name" sum-label="小计" label-column="fruit_name" />
	</summary>
</sql>
```
* 效果

品类|销售月份|销售笔数|销售数量(吨)|销售金额(万元)
----|-------|-------|----------|------------
总计|       |   71  |    12600 |14900
小计|       |  36  | 5900   | 7500
苹果|2019年5月|12 | 2000|2400
苹果|2019年4月|11 | 1900|2600
苹果|2019年3月|13 | 2000|2500
小计|       | 35  | 6700|7400
香蕉|2019年5月|10 | 2000|2000
香蕉|2019年4月|12 | 2400|2700
香蕉|2019年3月|13 | 2300|2700

### 2.8.3 先行转列再环比计算

```xml
<!-- 列与列环比演示 -->
<sql id="cols_relative_case">
	<value>
	<![CDATA[
	select t.fruit_name,t.order_month,t.sale_count,t.sale_amt,t.total_amt 
	from sqltoy_fruit_order t
	order by t.fruit_name ,t.order_month
	]]>
	</value>
	<!-- 数据旋转,行转列,将order_month 按列显示，每个月份下面有三个指标 -->
	<pivot start-column="sale_count" end-column="total_amt"	group-columns="fruit_name" category-columns="order_month" />
	<!-- 列与列之间进行环比计算 -->
	<cols-chain-relative group-size="3" relative-indexs="1,2" start-column="1" format="#.00%" />
</sql>
```
* 效果

<table>
<thead>
	<tr>
	<th rowspan="2" nowrap="nowrap">品类</th>
	<th colspan="5">2019年3月</th>
	<th colspan="5">2019年4月</th>
	<th colspan="5">2019年5月</th>
	</tr>
	<tr>
	    <th nowrap="nowrap">笔数</th><th nowrap="nowrap">数量</th><th nowrap="nowrap">比上月</th><th nowrap="nowrap">总金额</th><th nowrap="nowrap">比上月</th>
	    <th nowrap="nowrap">笔数</th><th nowrap="nowrap">数量</th><th nowrap="nowrap">比上月</th><th nowrap="nowrap">总金额</th><th nowrap="nowrap">比上月</th>
	    <th nowrap="nowrap">笔数</th><th nowrap="nowrap">数量</th><th nowrap="nowrap">比上月</th><th nowrap="nowrap">总金额</th><th nowrap="nowrap">比上月</th>
	</tr>
	</thead>
	<tbody>
	<tr>
		<td>香蕉</td>
		<td>13</td>
		<td>2300</td>
		<td></td>
		<td>2700</td>
		<td></td>
		<td>12</td>
		<td>2400</td>
		<td>4.30%</td>
		<td>2700</td>
		<td>0.00%</td>
		<td>10</td>
		<td>2000</td>
		<td>-16.70%</td>
		<td>2000</td>
		<td>-26.00%</td>
	</tr>
		<tr>
		<td>苹果</td>
		<td>13</td>
		<td>2000</td>
		<td></td>
		<td>2500</td>
		<td></td>
		<td>11</td>
		<td>1900</td>
		<td>-5.10%</td>
		<td>2600</td>
		<td>4.00%</td>
		<td>12</td>
		<td>2000</td>
		<td>5.20%</td>
		<td>2400</td>
		<td>-7.70%</td>
	</tr>
	</tbody>
</table>

### 2.8.4 树排序汇总
```xml
<!-- 树排序、汇总 -->
<sql id="treeTable_sort_sum">
	<value>
	<![CDATA[
	select t.area_code,t.pid_area,sale_cnt from sqltoy_area_sales t
	]]>
	</value>
	<!-- 组织树形上下归属结构，同时将底层节点值逐层汇总到父节点上，并且对同层级按照降序排列  -->
	<tree-sort id-column="area_code" pid-column="pid_area"	sum-columns="sale_cnt" level-order-column="sale_cnt" order-way="desc"/>
</sql>
```
* 效果

<table>
<thead>
	<tr>
	<th>地区</th>
	<th>归属地区</th>
	<th>销售量</th>
	</tr>
</thead>
<tbody>
	<tr>
	<td>上海</td>
	<td>中国</td>
	<td>300</td>
	</tr>
	<tr>
	<td>&nbsp;&nbsp;&nbsp;&nbsp;松江</td>
	<td>上海</td>
	<td>&nbsp;&nbsp;&nbsp;&nbsp;120</td>
	</tr>
 	<tr>
	<td>&nbsp;&nbsp;&nbsp;&nbsp;杨浦</td>
	<td>上海</td>
	<td>&nbsp;&nbsp;&nbsp;&nbsp;116</td>
	</tr>
 	<tr>
	<td>&nbsp;&nbsp;&nbsp;&nbsp;浦东</td>
	<td>上海</td>
	<td>&nbsp;&nbsp;&nbsp;&nbsp;64</td>
	</tr>
	<tr>
	<td>江苏</td>
	<td>中国</td>
	<td>270</td>
	</tr>
	<tr>
	<td>&nbsp;&nbsp;&nbsp;&nbsp;南京</td>
	<td>江苏</td>
	<td>&nbsp;&nbsp;&nbsp;&nbsp;110</td>
	</tr>
 	<tr>
	<td>&nbsp;&nbsp;&nbsp;&nbsp;苏州</td>
	<td>江苏</td>
	<td>&nbsp;&nbsp;&nbsp;&nbsp;90</td>
	</tr>
 	<tr>
	<td>&nbsp;&nbsp;&nbsp;&nbsp;无锡</td>
	<td>江苏</td>
	<td>&nbsp;&nbsp;&nbsp;&nbsp;70</td>
	</tr>
</tbody>
</table>

## 2.9 分库分表
### 2.9.1 查询分库分表（分库和分表策略可以同时使用）
```xml
sql参见quickstart项目:com/sqltoy/quickstart/sqltoy-quickstart.sql.xml 文件
<!-- 演示分库 -->
<sql id="qstart_db_sharding_case">
	<sharding-datasource strategy="hashDataSource" params="userId" />
	<value>
	<![CDATA[
	select * from sqltoy_user_log t 
	-- userId 作为分库关键字段属于必备条件
	where t.user_id=:userId 
	#[and t.log_date>=:beginDate]
	#[and t.log_date<=:endDate]
	]]>
	</value>
</sql>

<!-- 演示分表 -->
<sql id="qstart_sharding_table_case">
	<sharding-table tables="sqltoy_trans_info_15d"	strategy="realHisTable" params="beginDate" />
	<value><![CDATA[
	select * from sqltoy_trans_info_15d t 
	where t.trans_date>=:beginDate
	#[and t.trans_date<=:endDate]
	]]>
	</value>
</sql>
        
```
   
### 2.9.2 操作分库分表(vo对象由quickvo工具自动根据数据库生成，且自定义的注解不会被覆盖)

@Sharding 在对象上通过注解来实现分库分表的策略配置

参见:com.sqltoy.quickstart.ShardingSearchTest 进行演示

```java
package com.sqltoy.showcase.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.sagacity.sqltoy.config.annotation.Sharding;
import org.sagacity.sqltoy.config.annotation.SqlToyEntity;
import org.sagacity.sqltoy.config.annotation.Strategy;

import com.sagframe.sqltoy.showcase.vo.base.AbstractUserLogVO;

/**
 * @project sqltoy-showcase
 * @author zhongxuchen
 * @version 1.0.0 Table: sqltoy_user_log,Remark:用户日志表
 */
/*
 * db则是分库策略配置,table 则是分表策略配置，可以同时配置也可以独立配置
 * 策略name要跟spring中的bean定义name一致,fields表示要以对象的哪几个字段值作为判断依据,可以一个或多个字段
 * maxConcurrents:可选配置，表示最大并行数 maxWaitSeconds:可选配置，表示最大等待秒数
 */
@Sharding(db = @Strategy(name = "hashBalanceDBSharding", fields = { "userId" }),
		// table = @Strategy(name = "hashBalanceSharding", fields = {"userId" }),
		maxConcurrents = 10, maxWaitSeconds = 1800)
@SqlToyEntity
public class UserLogVO extends AbstractUserLogVO {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1296922598783858512L;

	/** default constructor */
	public UserLogVO() {
		super();
	}
}


```

# 3.集成说明

## 3.1 参见trunk 下面的quickstart,并阅读readme.md进行上手
  
```java
package com.sqltoy.quickstart;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 
 * @project sqltoy-quickstart
 * @description quickstart 主程序入口
 * @author zhongxuchen
 * @version v1.0, Date:2020年7月17日
 * @modify 2020年7月17日,修改说明
 */
@SpringBootApplication
@ComponentScan(basePackages = { "com.sqltoy.config", "com.sqltoy.quickstart" })
@EnableTransactionManagement
public class SqlToyApplication {
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		SpringApplication.run(SqlToyApplication.class, args);
	}
}

```

## 3.2 application.properties sqltoy部分配置

```properties
# sqltoy config
spring.sqltoy.sqlResourcesDir=classpath:com/sqltoy/quickstart
spring.sqltoy.translateConfig=classpath:sqltoy-translate.xml
spring.sqltoy.debug=true
#spring.sqltoy.reservedWords=status,sex_type
#dataSourceSelector: org.sagacity.sqltoy.plugins.datasource.impl.DefaultDataSourceSelector
#spring.sqltoy.defaultDataSource=dataSource
spring.sqltoy.unifyFieldsHandler=com.sqltoy.plugins.SqlToyUnifyFieldsHandler
#spring.sqltoy.printSqlTimeoutMillis=200000

```

## 3.3 缓存翻译的配置文件sqltoy-translate.xml 

```xml
<?xml version="1.0" encoding="UTF-8"?>
<sagacity
	xmlns="http://www.sagframe.com/schema/sqltoy-translate"
	xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
	xsi:schemaLocation="http://www.sagframe.com/schema/sqltoy-translate http://www.sagframe.com/schema/sqltoy/sqltoy-translate.xsd">
	<!-- 缓存有默认失效时间，默认为1小时,因此只有较为频繁的缓存才需要及时检测 -->
	<cache-translates>
		<!-- 基于sql直接查询的方式获取缓存 -->
		<sql-translate cache="dictKeyName"
			datasource="dataSource">
			<sql>
			<![CDATA[
			select t.DICT_KEY,t.DICT_NAME,t.STATUS
			from SQLTOY_DICT_DETAIL t
		        where t.DICT_TYPE=:dictType
		        order by t.SHOW_INDEX
			]]>
			</sql>
		</sql-translate>
	</cache-translates>

	<!-- 缓存刷新检测,可以提供多个基于sql、service、rest服务检测 -->
	<cache-update-checkers>
		<!-- 增量更新，带有内部分类的查询结果第一列是分类 -->
		<sql-increment-checker cache="dictKeyName"
			check-frequency="15" has-inside-group="true" datasource="dataSource">
			<sql><![CDATA[
			--#not_debug#--
			select t.DICT_TYPE,t.DICT_KEY,t.DICT_NAME,t.STATUS
			from SQLTOY_DICT_DETAIL t
	      		where t.UPDATE_TIME >=:lastUpdateTime
			]]></sql>
		</sql-increment-checker>
	</cache-update-checkers>
</sagacity>

```
* 实际业务开发使用，直接利用SqlToyCRUDService 就可以进行常规的操作，避免简单的对象操作自己写service，
另外针对复杂逻辑则自己写service直接通过调用sqltoy提供的：LightDao 完成数据库交互操作！

```java
@RunWith(SpringRunner.class)
@SpringBootTest(classes = SqlToyApplication.class)
public class CrudCaseServiceTest {
	@Autowired
	private SqlToyCRUDService sqlToyCRUDService;

	/**
	 * 创建一条员工记录
	 */
	@Test
	public void saveStaffInfo() {
		StaffInfoVO staffInfo = new StaffInfoVO();
		staffInfo.setStaffId("S190715005");
		staffInfo.setStaffCode("S190715005");
		staffInfo.setStaffName("测试员工4");
		staffInfo.setSexType("M");
		staffInfo.setEmail("test3@aliyun.com");
		staffInfo.setEntryDate(LocalDate.now());
		staffInfo.setStatus(1);
		staffInfo.setOrganId("C0001");
		staffInfo.setPhoto(FileUtil.readAsBytes("classpath:/mock/staff_photo.jpg"));
		staffInfo.setCountry("86");
		sqlToyCRUDService.save(staffInfo);
	}
 }
```

# 4. sqltoy关键代码说明

## 4.1 sqltoy-orm 主要分以下几个部分：
  - SqlToyDaoSupport:提供给开发者Dao继承的基本Dao,集成了所有对数据库操作的方法。
  - LightDao:提供给开发者快捷使用的Dao,让开发者只关注写Service业务逻辑代码，在service中直接调用lightDao
  - DialectFactory:数据库方言工厂类，sqltoy根据当前连接的方言调用不同数据库的实现封装。
  - SqlToyContext:sqltoy上下文配置,是整个框架的核心配置和交换区，spring配置主要是配置sqltoyContext。
  - EntityManager:封装于SqlToyContext，用于托管POJO对象，建立对象跟数据库表的关系。sqltoy通过SqlToyEntity注解扫描加载对象。
  - ScriptLoader:sql配置文件加载解析器,封装于SqlToyContext中。sql文件严格按照*.sql.xml规则命名。
  - TranslateManager:缓存翻译管理器,用于加载缓存翻译的xml配置文件和缓存实现类，sqltoy提供了接口并提供了默认基于ehcache的本地缓存实现，这样效率是最高的，而redis这种分布式缓存IO开销太大，缓存翻译是一个高频度的调用，一般会缓存注入员工、机构、数据字典、产品品类、地区等相对变化不频繁的稳定数据。
  - ShardingStragety:分库分表策略管理器，4.x版本之后策略管理器并不需要显式定义，只有通过spring定义，sqltoy会在使用时动态管理。
  

## 4.2 快速阅读理解sqltoy:

  - 从LightDao作为入口，了解sqltoy提供的所有功能
  - SqlToyDaoSupport 是LightDao 具体功能实现。
  - 从DialectFactory会进入不同数据库方言的实现入口。可以跟踪看到具体数据库的实现逻辑。你会看到oracle、mysql等分页、取随机记录、快速分页的封装等。
  - EntityManager:你会找到如何扫描POJO并构造成模型，知道通过POJO操作数据库实质会变成相应的sql进行交互。
  - ParallelUtils:对象分库分表并行执行器，通过这个类你会看到分库分表批量操作时如何将集合分组到不同的库不同的表并进行并行调度的。
  - SqlToyContext:sqltoy配置的上下文,通过这个类可以看到sqltoy全貌。
  - PageOptimizeUtils:可以看到分页优化默认实现原理。
