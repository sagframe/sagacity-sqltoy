# 在线文档(完善进度75%,可以预览)
## [https://chenrenfei.github.io/sqltoy/](https://chenrenfei.github.io/sqltoy/)

# WORD版详细文档(完整)
## 请见:docs/睿智平台SqlToy4.12使用手册.doc

# 作者和团队说明
* 作者和团队一直从事信息化系统建设和数据平台建设
* 目前就职于中化石化销售有限公司负责ERP项目和数据平台
* 2014~2018年在拉卡拉支付集团负责数据部门，运用oracle、hive、kafka、spark、mongo、es等技术体系为各个部门、客户、代理商、其他业务系统提数据服务。
* 核心作品:sqltoy-orm、nebula星云报表平台、sagacity-cronjob调度框架、sagframe-portal

#  QQ交流群:531812227 

# 致谢
## 中化石化销售有限公司
* sqltoy经过化销公司ERP、CRM、壹化网、掌上化销、数据平台等多个项目2年多来的应用，得到了大幅的改进提升，底层代码也得到了充分优化改进。
* sqltoy的开源推广得到了化销公司各级领导、开发团队、测试团队的大力支持，决心打造化销公司践行中化集团<<知性合一、科学发展>>企业发展理念,发展新基建的排头兵,争做中国科技发展的积极参与者、奋斗者、贡献者!
* sqltoy在公司的大力支持下积极推广，希望服务更多的企业和开发者，愿大家一起携手共进:中华有我，不负韶华!
  
## 拉卡拉支付集团
  sqltoy的发展感谢拉卡拉支付有限公司的支持，将sqltoy作为公司orm的主推框架

  并于2018年7月收到拉卡拉公司的一笔6千元的创新赞助，激励sqltoy走向开源并不断完善、发展。
  - 1、在拉卡拉sqltoy经受了日均1300万笔交易流水的考验。
  - 2、通过拉卡拉的业务全场景验证:分库分表；缓存翻译的优势展示；快速分页、分页优化在每一点性能都需要极度优化考虑的场景下价值体现；elasticsearch十亿级别的数据毫秒级查询；mongodb在用户画像标签数据场景下的应用。

# 使用单位或项目: 
* 宁波农行  
* 山东农信 
* 成都银行   
* 中国建行上海开发中心 
* 合肥农信 
* 浦发电销 
* 拉卡拉支付集团   
* 中化壹化网  
* 中化石化销售
* 苏州友达光电

# 疑问解答
* sqltoy会不维护吗? 不要担心sqltoy其实发展至今已经10年多了,因为经历了2018~2019年大规模复杂场景应用非常成熟了才开始推广的,目前公司全部系统都基于此,sqltoy不但要维护更加要深入发展,变得更强更可靠。
* sqltoy难道就是写sql?不是这样的，sqltoy其实是hibernate+mybatis(含plus)的合集，还包括一个quickvo帮助从数据库生成pojo，是一个完整的体系。
* sqltoy学习困难吗? 其实sqltoy学习是极其简单的,参考范例一个晚上绝对可以学会,规则极其统一简单。用学习mybatis五分之一的精力就足够。
* sqltoy的sql对应xml文件难写吗?xml里面有schema,所有配置方式在eclipse或idea中都是可以自动提示,不需要死记硬背。

# 1. 前言
## 1.1 sqltoy-orm是什么
   sqltoy-orm是比hibernate+myBatis更加贴合项目的orm框架，具有hibernate增删改和对象加载的便捷性同时也具有比myBatis更加灵活优雅的自定义sql查询功能。
   支持以下数据库:
   * oracle 从oracle11g到19c
   * db2 9.5+,建议从10.5 开始
   * mysql 支持5.6、5.7、8.0 版本
   * postgresql 支持9.5 以及以上版本
   * sqlserver 支持2008到2019版本，建议使用2012或以上版本
   * sqlite
   * DM达梦数据库
   * elasticsearch 只支持查询,版本支持5.7+版本，建议使用7.3以上版本 
   * clickhouse 
   * mongodb (只支持查询)
   * sybase_iq 支持15.4以上版本，建议使用16版本
   
## 1.2 是否重复造轮子，我只想首先说五个特性：
 * 根本上杜绝了sql注入问题，sql支持写注释、sql文件动态更新检测，开发时sql变更会自动重载
 * 最直观的sql编写模式，当查询条件稍微复杂一点的时候就会体现价值，后期变更维护的时候尤为凸显
 * 极为强大的缓存翻译查询：巧妙的结合缓存减少查询语句表关联，极大简化sql和提升性能。
 * 最强大的分页查询：很多人第一次了解到何为快速分页、分页优化这种极为巧妙的处理，还有在count语句上的极度优化。
 * 跨数据库函数方言替换，如：isnull/ifnull/nvl、substr/substring 等不同数据库 
 
当然这只是sqltoy其中的五个特点，还有行列转换(俗称数据旋转)、多级分组汇总、统一树结构表(如机构)查询、分库分表sharding、取随机记录、取top记录、修改并返回记录、慢sql提醒等这些贴合项目应用的功能， 当你真正了解上述特点带来的巨大优势之后，您就会对中国人创造的sqltoy-orm有了信心！
 
sqltoy-orm 来源于个人亲身经历的无数个项目的总结和思考，尤其是性能优化上不断的挖掘，至于是不是重复的轮子并不重要，希望能够帮到大家！

# 2. 快速特点说明
## 2.1 最优雅直观的sql编写模式

* sqltoy 的写法(一眼就看明白sql的本意,后面变更调整也非常便捷,copy到数据库客户端里稍做出来即可执行)
* sqltoy条件组织原理很简单: 如 #[order_id=:orderId] 等于if(:orderId<>null) sql.append(order_id=:orderId);#[]内只要有一个参数为null即剔除
* 支持多层嵌套:如 #[and t.order_id=:orderId #[and t.order_type=:orderType]] 
* 条件判断保留#[@if(:param>=xx ||:param<=xx1) sql语句] 这种@if()高度灵活模式,为特殊复杂场景下提供万能钥匙

```
<sql id="show_case">
<value>
<![CDATA[
select 	*
from sqltoy_device_order_info t 
where #[t.ORDER_ID=:orderId]
      #[and t.ORGAN_ID in (:authedOrganIds)]
      #[and t.STAFF_ID in (:staffIds)]
      #[and t.TRANS_DATE>=:beginDate]
      #[and t.TRANS_DATE<:endDate]  
]]>	
</value>
</sql>
```

* mybatis的写法(一板一眼很工程化),sqltoy比这个香多少倍?其实根本就无法比,因为mybatis这种写法就是一个负数!

```
<select id="show_case" resultMap="BaseResultMap">
 select *
 from sqltoy_device_order_info t 
 <where>
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

## 2.2 天然防止sql注入,执行过程:
* 假设sql语句如下
```xml
select 	*
from sqltoy_device_order_info t 
where #[t.ORGAN_ID in (:authedOrganIds)]
      #[and t.TRANS_DATE>=:beginDate]
      #[and t.TRANS_DATE<:endDate] 
```
* java调用过程
```java
sqlToyLazyDao.findBySql(sql, new String[] { "authedOrganIds","beginDate", "endDate"},
				new Object[] { authedOrganIdAry,beginDate,null}, DeviceOrderInfoVO.class);
```
* 最终执行的sql是这样的:
```xml
select 	*
from sqltoy_device_order_info t 
where t.ORDER_ID=?
      and t.ORGAN_ID in (?,?,?)
      and t.TRANS_DATE>=?	
```
* 然后通过: pst.set(index,value) 设置条件值，不存在将条件直接作为字符串拼接为sql的一部分
 
## 2.3 最强大的分页查询
### 2.3.1 分页特点说明
* 1、快速分页:@fast() 实现先取单页数据然后再关联查询，极大提升速度。
* 2、分页优化器:page-optimize 让分页查询由两次变成1.3~1.5次(用缓存实现相同查询条件的总记录数量在一定周期内无需重复查询)
* 3、sqltoy的分页取总记录的过程不是简单的select count(1) from (原始sql)；而是智能判断是否变成:select count(1) from 'from后语句'，
并自动剔除最外层的order by
* 4、在极特殊情况下sqltoy分页考虑是最优化的，如:with t1 as (),t2 as @fast(select * from table1) select * from xxx
这种复杂查询的分页的处理，sqltoy的count查询会是:with t1 as () select count(1) from table1,
如果是:with t1 as @fast(select * from table1) select * from t1 ,count sql 就是：select count(1) from table1

### 2.3.1 分页sql示例
```xml
<!-- 快速分页和分页优化演示 -->
<sql id="sqltoy_fastPage">
	<!-- 分页优化器,通过缓存实现查询条件一致的情况下在一定时间周期内缓存总记录数量，从而无需每次查询总记录数量 -->
	<!-- alive-max:最大存放多少个不同查询条件的总记录量; alive-seconds:查询条件记录量存活时长(比如120秒,超过阀值则重新查询) -->
	<page-optimize alive-max="100" alive-seconds="120" />
	<value>
		<![CDATA[
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
### 2.3.3 分页java代码调用

```java
/**
 *  基于对象传参数模式
 */
public void findPageByEntity() {
	PaginationModel pageModel = new PaginationModel();
	StaffInfoVO staffVO = new StaffInfoVO();
	// 作为查询条件传参数
	staffVO.setStaffName("陈");
	// 使用了分页优化器
	// 第一次调用:执行count 和 取记录两次查询
	PaginationModel result = sqlToyLazyDao.findPageBySql(pageModel, "sqltoy_fastPage", staffVO);
	System.err.println(JSON.toJSONString(result));
	// 第二次调用:过滤条件一致，则不会再次执行count查询
	//设置为第二页
	pageModel.setPageNo(2);
	result = sqlToyLazyDao.findPageBySql(pageModel, "sqltoy_fastPage", staffVO);
	System.err.println(JSON.toJSONString(result));
}

/**
 *  基于参数数组传参数
 */
public void findPageByParams() {
	//默认pageSize 为10，pageNo 为1
	PaginationModel pageModel = new PaginationModel();
	String[] paramNames=new String[]{"staffName"};
	Object[] paramValues=new  Object[]{"陈"};
	PaginationModel result = sqlToyLazyDao.findPageBySql(pageModel, "sqltoy_fastPage",paramNames,paramValues,StaffInfoVO.class);
	System.err.println(JSON.toJSONString(result));
}
	
```

## 2.4 最巧妙的缓存应用，将多表关联查询尽量变成单表(看下面的sql,如果不用缓存翻译需要关联多少张表?sql要有多长?多难以维护?)
* 1、 通过缓存翻译:<translate> 将代码转化为名称，避免关联查询，极大简化sql并提升查询效率 
* 2、 通过缓存名称模糊匹配:<cache-arg> 获取精准的编码作为条件，避免关联like 模糊查询
	
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
## 2.4 最跨数据库
* 1、提供类似hibernate性质的对象操作，自动生成相应数据库的方言。
* 2、提供了最常用的:分页、取top、取随机记录等查询，避免了各自不同数据库不同的写法。
* 3、提供了树形结构表的标准钻取查询方式，代替以往的递归查询，一种方式适配所有数据库。
* 4、sqltoy提供了大量基于算法的辅助实现，最大程度上用算法代替了以往的sql，实现了跨数据库
* 5、sqltoy提供了函数替换功能，比如可以让oracle的语句在mysql或sqlserver上执行(sql加载时将函数替换成了mysql的函数),最大程度上实现了代码的产品化。
    <property name="functionConverts" value="default" /> 
    default:SubStr\Trim\Instr\Concat\Nvl 函数；可以参见org.sagacity.sqltoy.plugins.function.Nvl 代码实现
 ```xml
        <!-- 跨数据库函数自动替换(非必须项),适用于跨数据库软件产品,如mysql开发，oracle部署 -->
	<property name="functionConverts" value="default">
	<!-- 也可以这样自行根据需要进行定义和扩展
	<property name="functionConverts">
		<list>
			<value>org.sagacity.sqltoy.plugins.function.Nvl</value>
			<value>org.sagacity.sqltoy.plugins.function.SubStr</value>
			<value>org.sagacity.sqltoy.plugins.function.Now</value>
			<value>org.sagacity.sqltoy.plugins.function.Length</value>
		</list>
	</property> -->
</bean>

```
  
## 2.5 提供行列转换(数据旋转)，避免写复杂的sql或存储过程，用算法来化解对sql的高要求，同时实现数据库无关(不管是mysql还是sqlserver)

```xml
        <!-- 列转行测试 -->
	<sql id="sys_unpvoitSearch">
		<value>
		<![CDATA[
		SELECT TRANS_DATE, 
		       sum(TOTAL_AMOUNT) TOTAL_AMOUNT,
		       sum(PERSON_AMOUNT) PERSON_AMOUNT,
		       sum(COMPANY_AMOUNT) COMPANY_AMOUNT
		FROM sys_unpivot_data
		group by TRANS_DATE
		]]>
		</value>
		<!-- 将指定的列变成行(这里3列变成了3行) -->
		<unpivot columns="TOTAL_AMOUNT:总金额,PERSON_AMOUNT:个人金额,COMPANY_AMOUNT:企业金额"
			values-as-column="TRANS_AMOUNT" labels-as-column="AMOUNT_TYPE" />
	</sql>

	<!-- 行转列测试 -->
	<sql id="sys_pvoitSearch">
		<value>
		<![CDATA[
		select t.TRANS_DATE,t.TRANS_CHANNEL,TRANS_CODE,sum(t.TRANS_AMT) TRANS_AMT from sys_summary_case t
		group by t.TRANS_DATE,t.TRANS_CHANNEL,TRANS_CODE
		order by t.TRANS_DATE,t.TRANS_CHANNEL,TRANS_CODE
		]]>
		</value>
		<pivot category-columns="TRANS_CHANNEL,TRANS_CODE" start-column="TRANS_AMT"
			default-value="0" default-type="decimal" end-column="TRANS_AMT"
			group-columns="TRANS_DATE" />
	</sql>
```
## 2.6 提供分组汇总求平均算法(用算法代替sql避免跨数据库语法不一致)
```xml
	<!-- 汇总计算 (场景是sql先汇总，页面上还需要对已有汇总再汇总的情况,如果用sql实现在跨数据库的时候就存在问题)-->
	<sql id="sys_summarySearch">
		<!-- 数据源sharding，多库将请求压力分摊到多个数据库节点上，支撑更多并发请求 -->	
		<sharding-datasource strategy="multiDataSource" />
		<value>
		<![CDATA[
		select	t.TRANS_CHANNEL,t.TRANS_CODE,sum( t.TRANS_AMT )
		from sys_summary_case t
		group by t.TRANS_CHANNEL,t.TRANS_CODE
		]]>
		</value>
		<!-- reverse 表示将汇总信息在上面显示(如第1行是汇总值，第2、3、4行为明细，反之，1、2、3行未明细，第4行为汇总)  -->
		<summary columns="2" reverse="true" sum-site="left" radix-size="2">
			<global sum-label="总计" label-column="0" />
                        <!-- 可以无限层级的分组下去-->
			<group sum-label="小计/平均" label-column="0" group-column="0" average-label="平均" />
		</summary>
	</sql>
```
## 2.7 分库分表
### 2.7.1 查询分库分表（分库和分表策略可以同时使用）
```xml
        sql参见showcase项目:com/sagframe/sqltoy/showcase/sqltoy-showcase.sql.xml 文件
        sharding策略配置参见:src/main/resources/spring/spring-sqltoy-sharding.xml 配置
        <!-- 演示分库 -->
	<sql id="sqltoy_db_sharding_case">
		<sharding-datasource
			strategy="hashBalanceDBSharding" params="userId" />
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
	<sql id="sqltoy_15d_table_sharding_case">
		<sharding-table tables="sqltoy_trans_info_15d"
			strategy="historyTableStrategy" params="beginDate" />
		<value>
			<![CDATA[
			select * from sqltoy_trans_info_15d t 
			where t.trans_date>=:beginDate
			#[and t.trans_date<=:endDate]
				]]>
		</value>
	</sql>
        
```
   
### 2.7.2 操作分库分表(vo对象由quickvo工具自动根据数据库生成，且自定义的注解不会被覆盖)

@Sharding 在对象上通过注解来实现分库分表的策略配置

参见:com.sagframe.sqltoy.showcase.ShardingCaseServiceTest 进行演示

```java
package com.sagframe.sqltoy.showcase.vo;

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
## 2.8 五种非数据库相关主键生成策略
    主键策略除了数据库自带的 sequence\identity 外包含以下数据库无关的主键策略。通过quickvo配置，自动生成在VO对象中。
### 2.8.1 shortNanoTime 22位有序安全ID，格式: 13位当前毫秒+6位纳秒+3位主机ID
### 2.8.2 nanoTimeId 26位有序安全ID,格式:15位:yyMMddHHmmssSSS+6位纳秒+2位(线程Id+随机数)+3位主机ID
### 2.8.3 uuid:32 位uuid
### 2.8.4 SnowflakeId 雪花算法ID
### 2.8.5 redisId  基于redis 来产生规则的ID主键
   根据对象属性值,产生规则有序的ID,比如:订单类型为采购:P  销售:S，贸易类型：I内贸;O 外贸;
   订单号生成规则为:1位订单类型+1位贸易类型+yyMMdd+3位流水(超过3位自动扩展)
   最终会生成单号为:SI191120001 
   

## 2.9 elastic原生查询支持
## 2.10 elasticsearch-sql 插件模式sql模式支持

# 3.集成说明

  * 参见trunk 下面的sqltoy-showcase 和 sqltoy-starter-showcase
  * sqltoy-showcase 是演示springboot 和sqltoy基于xml配置模式的集成，大多数功能演示在此项目中，其中tools/quickvo 目录是利用数据库生成POJO的配置示例(具体是VO还是其它可根据实际情况修改配置)
  * sqltoy-starter-showcase：演示无xml配置形式的基于boot-starter模式的集成
  
  ```java
 package com.sagframe.sqltoy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * @author zhongxuchen
 */
@SpringBootApplication
@ComponentScan(basePackages = { "com.sagframe.sqltoy" })
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
application.properties sqltoy部分配置
```javascript
##  sqltoy 配置 
# sql.xml 文件的路径,多个路径用;符合分割(原则上也是可选配置，如果只用对象操作的话,但不建议)
spring.sqltoy.sqlResourcesDir=/com/sagframe/sqltoy/showcase
# 缓存翻译的配置(可选配置)
spring.sqltoy.translateConfig=classpath:sqltoy-translate.xml
# 是否debug模式,debug 模式会打印执行的sql和参数信息(可选配置)
spring.sqltoy.debug=true
# 设置默认使用的datasource(可选配置,不配置会自动注入)
spring.sqltoy.defaultDataSource=dataSource
# 提供统一字段:createBy createTime updateBy updateTime 等字段补漏性(为空时)赋值(可选配置)
spring.sqltoy.unifyFieldsHandler=com.sagframe.sqltoy.plugins.SqlToyUnifyFieldsHandler
# sql执行超过多长时间则进行日志输出(可选配置:默认30秒)，用于监控哪些慢sql
spring.sqltoy.printSqlTimeoutMillis=30000

```
缓存翻译的配置文件sqltoy-translate.xml 

```xml
<?xml version="1.0" encoding="UTF-8"?>
<sagacity
	xmlns="http://www.sagframe.com/schema/sqltoy-translate"
	xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
	xsi:schemaLocation="http://www.sagframe.com/schema/sqltoy-translate http://www.sagframe.com/schema/sqltoy/sqltoy-translate.xsd">
	<!-- 缓存有默认失效时间，默认为1小时,因此只有较为频繁的缓存才需要及时检测 -->
	<cache-translates
		disk-store-path="./sqltoy-showcase/translateCaches">
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

		<!-- 员工ID和姓名的缓存 -->
		<sql-translate cache="staffIdName"
			datasource="dataSource">
			<sql>
			<![CDATA[
				select STAFF_ID,STAFF_NAME,STATUS
				from SQLTOY_STAFF_INFO
			]]>
			</sql>
		</sql-translate>
	</cache-translates>

	<!-- 缓存刷新检测,可以提供多个基于sql、service、rest服务检测 -->
	<cache-update-checkers>
		<!-- 基于sql的缓存更新检测,间隔为秒，可以分段设置，也可以直接设置一个数组如60，表示一分钟检测一次-->
		<sql-checker
			check-frequency="30"
			datasource="dataSource">
			<sql><![CDATA[
			--#not_debug#--
			select distinct 'staffIdName' cacheName,null cache_type
			from SQLTOY_STAFF_INFO t1
			where t1.UPDATE_TIME >=:lastUpdateTime
			-- 数据字典key和name缓存检测
			union all 
			select distinct 'dictKeyName' cacheName,t2.DICT_TYPE cache_type
			from SQLTOY_DICT_DETAIL t2
			where t2.UPDATE_TIME >=:lastUpdateTime
			]]></sql>
		</sql-checker>
	</cache-update-checkers>
</sagacity>
```
* 实际业务开发使用，直接利用SqlToyCRUDService 就可以进行常规的操作，避免简单的对象操作自己写service，
另外针对复杂逻辑则自己写service直接通过调用sqltoy提供的：SqlToyLazyDao 完成数据库交互操作！

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
		staffInfo.setPhoto(ShowCaseUtils.getBytes(ShowCaseUtils.getFileInputStream("classpath:/mock/staff_photo.jpg")));
		staffInfo.setCountry("86");
		sqlToyCRUDService.save(staffInfo);
	}
 }
```
# 4. sqltoy sql关键说明
## 4.1 sqltoy sql最简单规则#[] 对称符号
* #[] 等于if(中间语句参数是否有null)? true: 剔除#[] 整块代码，false：拿掉#[ 和 ] ,将中间的sql作为执行的一部分。
* #[] 支持嵌套，如#[t.status=:status  #[and t.createDate>=:createDate]] 会先从内而外执行if(null)逻辑
* 利用filters条件值预处理实现判断null的统一,下面是sqltoy完整提供的条件过滤器和其他函数
  不要被大段的说明吓一跳，99%都用不上，正常filters里面只会用到eq 和 to-date 

```xml
<sql id="show_case">
	<!-- 通过filters里面的逻辑将查询条件转为null，部分逻辑则对参数进行二次转换
	     默认条件参数为空白、空集合、空数组都转为null
             parmas 表示可以用逗号写多个参数，param 表示只支持单个参数
	-->	
	<filters>
		<!-- 等于，如机构类别前端传负一就转为null不参与条件过滤 -->
		<eq params="organType" value="-1" />
		<!-- 条件值在某个区间则转为null -->
		<between params="" start-value="0" end-value="9999" />

		<!-- 将参数条件值转换为日期格式,format可以是yyyy-MM-dd这种自定义格式也可以是:
		 first_day:月的第一天;last_day:月的最后一天,first_year_day:年的第一天,last_year_day年的最后一天 -->
		<to-date params="" format="yyyyMMdd" increment-days="1" />
		<!-- 将参数转为数字 --> 
		<to-number params="" data-type="decimal" />
		<!-- 将前端传过来的字符串切割成数组 -->
		<split data-type="string" params="staffAuthOrgs" split-sign=","/>
		<!-- 小于等于 -->
		<lte params="" value=""  />
		<!-- 小于 -->
		<lt  params=""  value="" />
		<!-- 大于等于 -->
		<gte params="" value=""  />
		<!-- 大于 -->
		<gt params="" value=""  />
		<!-- 字符替换,默认根据正则表达进行全部替换，is-first为true时只替换首个 -->
		<replace params="" regex="" value="" is-first="false" />
		<!-- 首要参数，即当某个参数不为null时，excludes是指被排除之外的参数全部为null -->
		<primary param="orderId" excludes="organIds" />
		<!-- 排他性参数,当某个参数是xxx值时,将其他参数设置为特定值  -->
		<exclusive param="" compare-type="eq" compare-values=""
			set-params="" set-value="" />
		<!-- 通过缓存进行文字模糊匹配获取精确的代码值参与精确查询 -->	
		<cache-arg cache-name="" cache-type="" param="" cache-mapping-indexes="" alias-name=""/>
		<!-- 将数组转化成in 的参数条件并增加单引号 -->
		<to-in-arg params=""/>
	</filters>
		
	<!-- 缓存翻译,可以多个，uncached-template 是针对未能匹配时显示的补充,${value} 表示显示key值,可以key=[${value}未定义 这种写法 -->
	<translate cache="dictKeyName" cache-type="POST_TYPE" columns="POST_TYPE"
		cache-indexs="1" uncached-template=""/>

	<!-- 安全掩码:tel\姓名\地址\卡号 -->
	<!--最简单用法: <secure-mask columns="" type="tel"/> -->
	<secure-mask columns="" type="name" head-size="3" tail-size="4"
		mask-code="*****" mask-rate="50" />
	<!-- 分库策略 -->
	<sharding-datasource strategy="" />
	<!-- 分表策略 -->
	<sharding-table tables="" strategy="" params="" />
	<!-- 分页优化,缓存相同查询条件的分页总记录数量, alive-max:表示相同的一个sql保留100个不同条件查询 alive-seconds:相同的查询条件分页总记录数保留时长(单位秒) -->
	<page-optimize alive-max="100" alive-seconds="600" />
	<!-- 日期格式化 -->
	<date-format columns="" format="yyyy-MM-dd HH:mm:ss"/>
	<!-- 数字格式 -->
        <number-format columns="" format=""/>
	<value>
	<![CDATA[
	select t1.*,t2.ORGAN_NAME from 
	@fast(select * from sys_staff_info t
		  where #[t.sexType=:sexType]
			#[and t.JOIN_DATE>:beginDate]
			#[and t.STAFF_NAME like :staffName]
			-- 是否虚拟员工@if()做逻辑判断
			#[@if(:isVirtual==true||:isVirtual==0) and t.IS_VIRTUAL=1]
			) t1,sys_organ_info t2
        where t1.ORGAN_ID=t2.ORGAN_ID
	]]>	
	</value>

	<!-- 为极致分页提供自定义写sql -->
	<count-sql><![CDATA[]]></count-sql>
	<!-- 汇总和求平均，通过算法实现复杂的sql，同时可以变成数据库无关 -->
	<summary columns="" radix-size="2" reverse="false" sum-site="left">
		<global sum-label="" label-column="" />
		<group sum-label="" label-column="" group-column="" />
	</summary>
	<!-- 拼接某列,mysql中等同于group_concat\oracle 中的WMSYS.WM_CONCAT功能 -->
	<link sign="," column="" />
	<!-- 行转列 (跟unpivot互斥)，算法实现数据库无关 -->
	<pivot category-columns="" group-columns="" start-column="" end-column=""
		default-value="0" />
	<!-- 列转行 -->
	<unpivot columns="" values-as-column="" />
</sql>
```

# 5. sqltoy关键代码说明

* sqltoy-orm 主要分以下几个部分：
  - BaseDaoSupport:提供给开发者Dao继承的基本Dao,集成了所有对数据库操作的方法。
  - SqlToyLazyDao:提供给开发者快捷使用的Dao,等同于开发者自己写的Dao，用于在简单场景下开发者可以不用写Dao，而直接写Service。
  - SqltoyCRUDService:简单Service的封装，一些简单的对象增删改开发者写Service也是简单的调用Dao,针对这种场景提供一个简单功能的Service调用，开发者自己的Service用于封装相对复杂的业务逻辑。
  - DialectFactory:数据库方言工厂类，sqltoy根据当前连接的方言调用不同数据库的实现封装。
  - SqlToyContext:sqltoy上下文配置,是整个框架的核心配置和交换区，spring配置主要是配置sqltoyContext。
  - EntityManager:封装于SqlToyContext，用于托管POJO对象，建立对象跟数据库表的关系。sqltoy通过SqlToyEntity注解扫描加载对象。
  - ScriptLoader:sql配置文件加载解析器,封装于SqlToyContext中。sql文件严格按照*.sql.xml规则命名。
  - TranslateManager:缓存翻译管理器,用于加载缓存翻译的xml配置文件和缓存实现类，sqltoy提供了接口并提供了默认基于ehcache的实现，缓存翻译最好是使用ehcache本地缓存(或ehcache rmi模式的分布式缓存)，这样效率是最高的，而redis这种分布式缓存IO开销太大，缓存翻译是一个高频度的调用，一般会缓存注入员工、机构、数据字典、产品品类、地区等相对变化不频繁的稳定数据。
  - ShardingStragety:分库分表策略管理器，4.x版本之后策略管理器并不需要显式定义，只有通过spring定义，sqltoy会在使用时动态管理。
  

* 快速阅读理解sqltoy:

  - 从BaseDaoSupport(或SqlToyDaoSupport)作为入口,你会看到sqltoy的所有提供的功能，通过LinkDaoSupport则可以按照不同分类视角看到sqltoy的功能组织形式。
  - 从DialectFactory会进入不同数据库方言的实现入口。可以跟踪看到具体数据库的实现逻辑。你会看到oracle、mysql等分页、取随机记录、快速分页的封装等。
  - EntityManager:你会找到如何扫描POJO并构造成模型，知道通过POJO操作数据库实质会变成相应的sql进行交互。
  - ParallelUtils:对象分库分表并行执行器，通过这个类你会看到分库分表批量操作时如何将集合分组到不同的库不同的表并进行并行调度的。
  - SqlToyContext:sqltoy配置的上下文,通过这个类可以看到sqltoy全貌。
  - PageOptimizeCacheImpl:可以看到分页优化默认实现原理。
