#  QQ交流群:531812227 (使用过程中的问题可以通过群及时沟通处理，不要担心用了出问题无法解决)

# 使用单位或项目: 宁波农行  山东农信  成都银行   中国建行上海开发中心  合肥农信  浦发电销  拉卡拉   中化壹化网  中化石化销售

# 致谢

  sqltoy的发展感谢拉卡拉支付有限公司的支持，将sqltoy作为公司orm的主推框架
  
  并于2018年7月收到拉卡拉公司的一笔6千元的创新赞助，激励sqltoy走向开源并不断完善、发展。
  - 1、在拉卡拉sqltoy经受了日均1300万笔交易流水的考验。
  - 2、通过拉卡拉的业务全场景验证:分库分表；缓存翻译的优势展示；快速分页、分页优化在每一点性能都需要极度优化考虑的场景下价值体现；elasticsearch十亿级别的数据毫秒级查询；
	
      mongodb在用户画像标签数据场景下的应用。
      
# 1. 前言
## 1.1 sqltoy-orm是什么
   sqltoy-orm是比hibernate+myBatis更加贴合项目的orm框架，具有hibernate增删改的便捷性同时也具有比myBatis更加灵活优雅的自定义sql查询功能。
## 1.2 是否重复造轮子
   写开源项目是一个非常不易的事情，原则上优势太小千万不要折腾，创新不易，坚持更难！
   很显然sqltoy虽然名字很toy，但经过多年发展和项目实践已然成了一个非常有趣非常有特色的big toy!
   sqltoy 绝非重复造轮子，在查询领域，直击事物的本质,让你耳目一新，如此的极致、如此追寻内心！
## 1.3 如何看待sqltoy
   sqltoy起源于对hibernate动态查询一个非常偶然的灵感，写成了比mybatis更为直观更易于维护的sql编写方式；
   然后在带项目的过程中不断遇到挑战，集成了行列转换、分组汇总求平均、缓存翻译、快速分页、分页优化器、缓存条件过滤、分库分表等等特性。
   因此sqltoy是一个灵感和项目实践经验的集成，非常欢迎大家提供更多的意见，让大家用上更加完美的orm框架！
## 1.4 sqltoy-orm发展历程
    - 2004~2008:萌芽阶段,这个阶段仅仅是一些工具类和类似BaseDaoSupport这样的对hibernate或其他框架的封装集成。
    - 2009~2011:成型阶段:一个针对动态sql查询的偶然发现，正式开启了sqltoy框架，经过几年近10个项目的发展沉淀，sqltoy在查询方面得到了完善:支持不同数据库的分页、取随机记录、top记录、快速分页、缓存翻译、数据行列转换等。通过算法和sql的融合让开发者可以轻松面对很多复杂问题。
    - 2012~2014:形成完整的ORM框架:sqltoy之前主要是面向查询，需要跟hibernate或其他jpa混合使用，让开发者同时掌握2种框架，带来了不必要的学习成本，项目不是技术的堆叠！因此扩展了面对对象的交互功能，sqltoy终于可以独立于hibernate形成了一个完整的orm框架体系，经过一段时间的完善和项目实践，sqltoy-orm基本成型。
    - 2015~2017:优化完善:随着sqltoy应用范围的扩展，深感有必要将其推广开让更多人摆脱数据库交互的一些困难，为此设定对标mybatis和hibernate，利用业余时间对代码进行了一次彻底的重构，并增加了sharding功能、分页优化器、链式交互等特性，同时吸收很多使用者的反馈简化了配置模式（增强默认特性）、支持springboot等。
    - 2018年~至今: 经历了电商网站、以及完整ERP复杂逻辑场景实践优化，sqltoy经历了快速迭代优化
## 1.5快速特性对比

![image](https://github.com/chenrenfei/sagacity-sqltoy/blob/master/docs/sqltoy-orm-show-1.jpg)

# 2. 快速特点展示(参见:sqltoy-showcase 中的源码，基于springboot+mysql 的集成演示)

## 2.1 最优雅直观的sql编写模式

* sqltoy 的写法(一眼就看明白sql的本意,后面变更调整也非常便捷,copy到数据库客户端里稍做出来即可执行)
* sqltoy条件组织原理很简单: 如 #[order_id=:orderId] 等于if(:orderId<>null) sql.append(order_id=:orderId);#[]内只要有一个参数为null即剔除
* 支持多层嵌套:如 #[and t.order_id=:orderId #[and t.order_type=:orderType]] 
* 条件判断保留#[@if(:param>=xx ||:param<=xx1) sql语句] 这种@if()高度灵活模式,为特殊复杂场景下提供万能钥匙

```
select 	*
from sqltoy_device_order_info t 
where #[t.ORDER_ID=:orderId]
      #[and t.ORGAN_ID in (:authedOrganIds)]
      #[and t.STAFF_ID in (:staffIds)]
      #[and t.TRANS_DATE>=:beginDate]
      #[and t.TRANS_DATE<:endDate]  
```

* mybatis的写法(一板一眼很工程化)

```
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
```

## 2.2 天然防止sql注入,因为不存在条件语句直接拼接，全部:
    preparedStatement.set(index,value)
## 2.3 最强大的分页查询
* 1、快速分页:@fast() 实现先取单页数据然后再关联查询，极大提升速度。
* 2、分页优化器:page-optimize 让分页查询由两次变成1.3~1.5次(用缓存实现相同查询条件的总记录数量在一定周期内无需重复查询)
* 3、sqltoy的分页取总记录的过程不是简单的select count(1) from (原始sql)；而是智能判断是否变成:select count(1) from 'from后语句'

```xml
<!-- 快速分页和分页优化演示 -->
<sql id="sqltoy_fastPage">
	<!-- 分页优化器,通过缓存实现查询条件一致的情况下在一定时间周期内缓存总记录数量，从而无需每次查询总记录数量 -->
	<!-- alive-max:最大存放多少个不同查询条件的总记录量; alive-seconds:查询条件记录量存活时长(比如120秒,超过阀值则重新查询) -->
	<page-optimize alive-max="100" alive-seconds="120" />
	<!-- 安全脱敏,type提供了几种标准的脱敏模式
		mask-rate:脱敏比例
		mask-code:自定义脱敏掩码,一般***,默认为***
		head-size:前面保留多长字符
		tail-size:尾部保留多长字符
	 -->
	<secure-mask columns="address" type="address" />
	<secure-mask columns="tel_no" type="tel"/>
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
## 2.4 最巧妙的缓存应用，将多表关联查询尽量变成单表(看下面的sql,如果不用缓存翻译需要关联多少张表?sql要有多长?多难以维护?)
* 1、 通过缓存翻译:<translate> 将代码转化为名称，避免关联查询，极大简化sql并提升查询效率 
* 2、 通过缓存名称模糊匹配:<cache-arg> 获取精准的编码作为条件，避免关联like 模糊查询
	
```xml
<sql id="sqltoy_order_search">
	<!-- 缓存翻译设备类型 
	cache:具体的缓存定义的名称
	cache-type:一般针对数据字典，提供一个分类条件过滤
	columns:sql中的查询字段名称，可以逗号分隔对多个字段进行翻译
	cache-indexs:缓存数据名称对应的列,不填则默认为第二列(从0开始,1则表示第二列)，
	      例如缓存的数据结构是:key、name、fullName,则第三列表示全称
	-->
	<translate cache="dictKeyNameCache" cache-type="DEVICE_TYPE" columns="deviceTypeName" cache-indexs="1"/>
	<!-- 缓存翻译购销类型 -->
	<translate cache="dictKeyNameCache" cache-type="PURCHASE_SALE_TYPE" columns="psTypeName" />
	<!-- 缓存翻译订单状态 -->
	<translate cache="dictKeyNameCache" cache-type="ORDER_STATUS" columns="statusName" />
	<!-- 员工名称翻译,如果同一个缓存则可以同时对几个字段进行翻译 -->
	<translate cache="staffIdNameCache" columns="staffName,createName" />
	<!-- 机构名称翻译 -->
	<translate cache="organIdNameCache" columns="organName" />
	<filters>
		<cache-arg cache-name="staffIdNameCache" param="staffName" alias-name="staffIds">
			<!--
			   可选配置:这里的filter是排除的概念,将符合条件的排除掉(可以不使用)
			   compare-param:可以是具体的一个条件参数名称,也可以是一个固定值
			   cache-index:针对缓存具体哪一列进行值对比
			   compare-type:目前分 eq和neq两种情况，这里表示将状态无效的员工过滤掉
			-->
			<filter compare-param="0" cache-index="2" compare-type="eq"/>
		</cache-arg>
		<!-- 千万不要to_str(trans_date)>=:xxx 模式,sqltoy提供了日期、数字等类型转换,另外了解format的选项可以大幅简化代码处理 -->
		<to-date params="beginDate" format="yyyy-MM-dd"/>
		<!-- 对截止日期加1,从而达到类似于 trans_date<='yyyy-MM-dd 23:59:59' 平衡时分秒因素 -->
		<to-date params="endDate" format="yyyy-MM-dd" increment-days="1"/>
	</filters>
	<value>
	<![CDATA[
	select 	ORDER_ID,
		DEVICE_TYPE,
		DEVICE_TYPE deviceTypeName,-- 设备分类名称
		PS_TYPE,
		PS_TYPE as psTypeName, -- 购销类别名称
		TOTAL_CNT,
		TOTAL_AMT,
		BUYER,
		SALER,
		TRANS_DATE,
		DELIVERY_TERM,
		STAFF_ID,
		STAFF_ID staffName, -- 员工姓名
		ORGAN_ID,
		ORGAN_ID organName, -- 机构名称
		CREATE_BY,
		CREATE_BY createName, -- 创建人名称
		CREATE_TIME,
		UPDATE_BY,
		UPDATE_TIME,
		STATUS,
		STATUS statusName -- 状态名称
	from sqltoy_device_order_info t 
	where #[t.ORDER_ID=:orderId]
	      -- 当前用户能够访问的授权组织机构，控制数据访问权限(一般登录后直接放于用户session中)
		  #[and t.ORGAN_ID in (:authedOrganIds)]
		  #[and t.STAFF_ID in (:staffIds)]
		  #[and t.TRANS_DATE>=:beginDate]
		  #[and t.TRANS_DATE<:endDate]
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
		<!-- 可以这样自行根据需要进行定义和扩展
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
```
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

## 2.8 elastic原生查询支持
## 2.9 elasticsearch-sql 插件模式sql模式支持

# 3. sqltoy框架介绍

![image](https://github.com/chenrenfei/sagacity-sqltoy/blob/master/docs/sqltoy-orm-struts.jpg)

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
  

快速阅读理解sqltoy:

  - 从BaseDaoSupport(或SqlToyDaoSupport)作为入口,你会看到sqltoy的所有提供的功能，通过LinkDaoSupport则可以按照不同分类视角看到sqltoy的功能组织形式。
  - 从DialectFactory会进入不同数据库方言的实现入口。可以跟踪看到具体数据库的实现逻辑。你会看到oracle、mysql等分页、取随机记录、快速分页的封装等。
  - EntityManager:你会找到如何扫描POJO并构造成模型，知道通过POJO操作数据库实质会变成相应的sql进行交互。
  - ParallelUtils:对象分库分表并行执行器，通过这个类你会看到分库分表批量操作时如何将集合分组到不同的库不同的表并进行并行调度的。
  - SqlToyContext:sqltoy配置的上下文,通过这个类可以看到sqltoy全貌。
  - PageOptimizeCacheImpl:可以看到分页优化默认实现原理。
 










    













