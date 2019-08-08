#  QQ交流群:531812227
# 使用单位或项目: 宁波农行  山东农信  成都银行   中国建行上海开发中心  合肥农信  浦发电销  拉卡拉   中化壹化网  中化石化销售

# 致谢

  sqltoy的发展感谢拉卡拉支付有限公司的支持，将sqltoy作为公司orm的主推框架
  
  并于2018年7月收到拉卡拉公司的一笔6千元的创新资助，激励sqltoy走向开源并不断完善、发展。
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
   因此sqltoy是一个灵感和项目实践经验的集成，可能在代码实现上并不很震撼，因此非常欢迎大家提供更多的意见，让大家用上更加完美的orm框架！
## 1.4 sqltoy-orm发展历程
    - 2004~2008:萌芽阶段,这个阶段仅仅是一些工具类和类似BaseDaoSupport这样的对hibernate或其他框架的封装集成。
    - 2009~2011:成型阶段:一个针对动态sql查询的偶然发现，正式开启了sqltoy框架，经过几年近10个项目的发展沉淀，sqltoy在查询方面得到了完善:支持不同数据库的分页、取随机记录、top记录、快速分页、缓存翻译、数据行列转换等。通过算法和sql的融合让开发者可以轻松面对很多复杂问题。
    - 2012~2014:形成完整的ORM框架:sqltoy之前主要是面向查询，需要跟hibernate或其他jpa混合使用，让开发者同时掌握2种框架，带来了不必要的学习成本，项目不是技术的堆叠！因此扩展了面对对象的交互功能，sqltoy终于可以独立于hibernate形成了一个完整的orm框架体系，经过一段时间的完善和项目实践，sqltoy-orm基本成型。
    - 2015~2017:优化完善:随着sqltoy应用范围的扩展，深感有必要将其推广开让更多人摆脱数据库交互的一些困难，为此设定对标mybatis和hibernate，利用业余时间对代码进行了一次彻底的重构，并增加了sharding功能、分页优化器、链式交互等特性，同时吸收很多使用者的反馈简化了配置模式（增强默认特性）、支持springboot等。
    - 2018年~至今: 经历了电商网站、以及完整ERP复杂逻辑场景实践优化，sqltoy经历了快速迭代优化
## 1.5快速特性对比

![image](https://github.com/chenrenfei/sagacity-sqltoy/blob/master/docs/sqltoy-orm-show-1.jpg)

# 2. 快速特点展示

## 2.1 最优雅直观的sql编写模式

* sqltoy 的写法(一眼就看明白sql的本意,后面变更调整也非常便捷,copy到数据库客户端里稍做出来即可执行)
* sqltoy条件判断逻辑很简单#[order_id=:orderId] 就等于if(:orderId<>null) sql.append(order_id=:orderId);#[]内只要有一个参数为null即剔除
  #[and t.order_id=:orderId #[and t.order_type=:orderType]] 即支持任意层嵌套
```
select 	*
from sqltoy_device_order_info t 
where #[t.ORDER_ID=:orderId]
      #[and t.ORGAN_ID in (:authedOrganIds)]
      #[and t.STAFF_ID in (:staffIds)]
      #[and t.TRANS_DATE>=:beginDate]
      #[and t.TRANS_DATE<:endDate]  
```

* mybatis的写法(写起来真的太痛苦了,如同嚼蜡,完全是一个工程化的硬搞死搞模式)

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

## 2.2 天然防止sql注入,因为不存在条件语句直接拼接，全部preparedStatement.set(index,value)
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
## 2.4 提供行列转换(数据旋转)，避免写负责的sql甚至上存储过程，用算法集成来化解对sql的要求
## 2.5 提供分组汇总求平均算法(用算法代替sql避免跨数据库语法不一致)
## 2.6 elastic原生查询支持
## 2.7 elasticsearch-sql 插件模式sql模式支持

# 2. sqltoy框架介绍

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
  - EntityManager:你会找到如何扫描POJO并构造成模型，知道通过POJO操作数据库实质会变成响应的sql进行交互。
  - ParallelUtils:对象分库分表并行执行器，通过这个类你会看到分库分表批量操作时如何将集合分组到不同的库不同的表并进行并行调度的。
  - SqlToyContext:sqltoy配置的上下文,通过这个类可以看到sqltoy全貌。
  - PageOptimizeCacheImpl:可以看到分页优化默认实现原理。
 
# 3. sqltoy-orm项目环境搭建

  ## 3.1 环境准备,配置eclipse或ide的sqltoy schema。这里以eclipse为例(4.2.2 版本无需配置,参见xml文件头部的改变)
  
    从sqltoy release目录下面提取quickvo.xsd\sqltoy-4.0.xsd 放于eclipse 安装路径下。
    点击:eclipse window--> Prefences-->XML-->XML Catalog -->Add,如下图:
![image](D:/eclipse_schema.jpg)
    分别配置完sqltoy-4.x.xsd和quickvo.xsd,编写quickvo.xml和*.sql.xml就可以自动提示。

  ## 3.2 sqltoy是基于spring框架的，依赖spring-core、spring-jdbc、spring-beans、spring-context、spring-context-support、spring-tx，详见其pom.xml文件。
  
    sqltoy仍然可以跟hibernate和mybatis混合使用，如你用hibernate则可只用sqltoy的查询功能，如果用mybatis你可以只用sqltoy的对象操作。sqltoy所有功能不是强制性的！

  ## 3.3 配置spring
  
```
<!-- 配置辅助sql处理工具用于sql查询条件的处理 -->
	<bean id="sqlToyContext" name="sqlToyContext" class="org.sagacity.sqltoy.SqlToyContext"
		init-method="initialize">
		<!-- 指定sql.xml 文件的路径实现目录的递归查找,非必须属性 -->
		<property name="sqlResourcesDir" value="classpath:sqltoy/showcase/" />
		<!-- 针对不同数据库函数进行转换,非必须属性 -->
		<!--<property name="functionConverts" value="default"/>-->
		<property name="functionConverts" value="default"/>
		<!-- pojo 包路径(可以写一个高层包路径),非必须属性,sqltoy4.0.1之后会在调用VO操作时动态解析 -->
		<property name="packagesToScan">
			<list>
				<value>sqltoy.showcase.sagacity.vo</value>
				<value>sqltoy.showcase.system.vo</value>
			</list>
		</property>
		<!-- elasticsearch支持,可以配置多个集群地址 -->
		<property name="elasticEndpoints">
			<list>
				<bean class="org.sagacity.sqltoy.config.model.ElasticEndpoint">
					<!-- 指定url地址 -->
					<constructor-arg value="${elastic.url}"/>
					<property name="id" value="${elastic.id}"/>
					<property name="username" value="${elastic.username}"/>
					<property name="password" value="${elastic.password}"/>
				</bean>
			</list>
		</property>
        <!-- 缓存翻译管理器,非必须属性 -->
		<property name="translateConfig" value="classpath:sqltoy-translate.xml" />
		<!-- 默认值为:false -->
		<property name="debug" value="${sqltoy.debug}" />
		<!-- 默认值为:50,提供sqltoy批量更新的batch量 -->
		<property name="batchSize" value="${sqltoy.batchSize}" />
		<!-- 如果是单一类型的数据库，建议dialect一定要设置,可避免不必要的数据库类型判断 -->
		<property name="dialect" value="${sqltoy.dialect}" />
		<!-- 默认值为:100000,设置分页查询最大的提取数据记录量,防止恶意提取数据造成系统内存压力以及保障数据安全 -->
		<property name="pageFetchSizeLimit" value="50000" />
		<!-- 3.3.4 开始增加的参数便于为Dao设置基本的数据源,非必填项 -->
		<property name="defaultDataSource" ref="dataSource" />
	</bean>
	
	<!-- lazyDao定义,为开发者提供简单的Dao避免每个业务都写Dao -->
	<bean id="sqlToyLazyDao" name="sqlToyLazyDao"
		class="org.sagacity.sqltoy.dao.impl.SqlToyLazyDaoImpl" />

    <!-- 简单的CRUD 操作服务,简单操作开发者无需写自己的Service -->
	<bean id="sqlToyCRUDService" name="sqlToyCRUDService"
		class="org.sagacity.sqltoy.service.impl.SqlToyCRUDServiceImpl" />
```

上述是一个较为完整的写法,如果是简单项目最简配置如下：

```
<!-- 配置辅助sql处理工具用于sql查询条件的处理 -->
	<bean id="sqlToyContext" name="sqlToyContext" class="org.sagacity.sqltoy.SqlToyContext"
		init-method="initialize">
		<!-- 指定sql.xml 文件的路径实现目录的递归查找,非必须属性 -->
		<property name="sqlResourcesDir" value="classpath:sqltoy/showcase/" />
		<!-- 缓存翻译管理器,非必须属性 -->
		<property name="translateConfig" value="classpath:sqltoy-translate.xml" />
		<!-- 如果是单一类型的数据库，建议dialect一定要设置,可避免不必要的数据库类型判断 -->
		<property name="dialect" value="${sqltoy.dialect}" />
	</bean>
	
	<bean id="sqlToyLazyDao" name="sqlToyLazyDao"
		class="org.sagacity.sqltoy.dao.impl.SqlToyLazyDaoImpl" />

	<bean id="sqlToyCRUDService" name="sqlToyCRUDService"
		class="org.sagacity.sqltoy.service.impl.SqlToyCRUDServiceImpl" />
```

如果你不用缓存翻译，配置还可以这样,够简单吧:


```
<!-- 配置辅助sql处理工具用于sql查询条件的处理 -->
	<bean id="sqlToyContext" name="sqlToyContext" class="org.sagacity.sqltoy.SqlToyContext"
		init-method="initialize">
		<!-- 指定sql.xml 文件的路径实现目录的递归查找,非必须属性 -->
		<property name="sqlResourcesDir" value="classpath:sqltoy/showcase/" />
	</bean>
```

## 3.4 quickvo生成POJO对象

  参考sqltoy-showcase项目tools目录下面的quickvo目录，下面有：
  
  - drivers目录:将您的数据库驱动放于其目录下面,确保quickvo可以加载响应数据库的驱动。
  - quickvo.xml:生成VO的配置文件
  - sagacity-quickvo.jar:含有main程序的quickvo类包。
  - quickvo.bat:windows 下面的执行脚本，用来执行sagacity-quickvo.jar
  
  quickvo.xml配置说明:


```
<?xml version="1.0" encoding="UTF-8"?>
<quickvo xmlns="http://www.sagframe.com/schema/quickvo" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" 
      xsi:schemaLocation="http://www.sagframe.com/schema/quickvo http://www.sagframe.com/schema/sqltoy/quickvo.xsd">
	<!-- 可以不引入properties文件(目的是跟项目的properties一致),相关配置固化 -->
	<property file="../../src/main/resources/system.properties" />
	<!-- 最大小数位长度,避免个别数据库NUMBER(m,0)的情况下获取出来的默认小数位长度都很长 -->
	<property name="max.scale.length" value="3" />
	<property name="project.version" value="1.0.0" />
	<property name="project.name" value="sqltoy-showcase" />
	<property name="project.package" value="sqltoy.showcase" />
	<property name="include.schema" value="false" />
	<!-- oracle支持schema,mysql\DB2:catalog -->
    <!-- 通过name支持多数据库模式 -->
	<datasource name="sagframe" url="${jdbc.connection.url}" driver="${jdbc.connection.driver_class}"  
	     catalog="${jdbc.connection.catalog}"
		username="${jdbc.connection.username}" password="${jdbc.connection.password}" />
	<tasks dist="../../src/main/java" encoding="UTF-8">
        <!-- 
           增加了swagger-mode 便于VO生成swagger的modeApi 
           增加datasource属性关联具体datasouce 的name
        -->
		<task active="true" author="zhongxuchen" include="^SAG_\w+" swagger-model="true" >
			<vo package="${project.package}.sagacity.vo" substr="Sag" name="#{subName}VO" />
			<dao package="${project.package}.sagacity.dao" name="#{subName}Dao"
				active="true" />
		</task>
		<task active="true" author="zhongxuchen" include="^SYS_\w+">
			<vo package="${project.package}.system.vo" substr="Sys" name="#{subName}VO" />
			<dao package="${project.package}.system.dao" name="#{subName}Dao"
				active="false" />
		</task>
	</tasks>

	<!-- 
		主键策略配置:identity类型的会自动产生响应的主键策略，其他场景sqltoy根据主键类型和长度自动分配响应的策略方式.
		strategy:分:sequence\assign\generator 三种,sequence为数据库sequence模式需要指定具体的sequence名称，assign 为手工赋值，Generator则为指定具体产生策略
		generator:主键产生器,目前分:default:22位长度的主键、nanotime:26位纳秒形式;snowflake雪花算法、uuid:32位唯一算法
	-->
	<primary-key>
		<!-- <table name="SAG_\w+|SYS_\w+" strategy="generator" generator="default"/> -->
		<!-- <table name="xxxTABLE" strategy="sequence" sequence="SEQ_XXXX"/> -->
		<!--<table name="sys_staff_info" strategy="generator" generator="snowflake"/>-->
	</primary-key>
	
    <!-- 业务主键定义,column 为主键列，则主键策略以业务主键策略为准，signature 支持特定前缀和日期格式组合
        length定义主键总长度(限制的是最小长度,超出则以实际为准)
     -->
	<business-primary-key>
		<table name="xxxTable" column="xxColumn" signature="PO@df('yyyyMMdd')" length="16" generator="redis"/>
	</business-primary-key>

	<!-- 主子表的级联关系 update-cascade:delete 表示对存量数据进行先做删除然后再插入,
	也可以写成:ENABLED=0(sql片段,置状态为无效) -->
	<cascade>
		<table name="SAG_DICT_DETAIL" update-cascade="delete" load="ENABLED=1" />
	</cascade>

	<!-- 数据类型对应关系，native-types表示特定数据库返回的字段类型; jdbc-type：表示对应jdbc标准的类型(见:java.sql.Types), 
		主要用于vo @Column注解中，设置其类型,方便操作数据库插入或修改时设置类型;java-type:表示对应java对象的属性类型 -->
	<type-mapping>
		<sql-type native-types="DATE,DATETIME" jdbc-type="DATE"
			java-type="java.util.Date" />
		<sql-type native-types="TIMESTAMP(6),TIMESTAMP" jdbc-type="TIMESTAMP"
			java-type="java.sql.Timestamp" />
		<sql-type native-types="bpchar" jdbc-type="CHAR" java-type="String" />
		<sql-type native-types="CHAR,CLOB" java-type="String" />
		<sql-type native-types="TEXT,VARCHAR,VARCHAR2,LVARCHAR,long varchar"
			jdbc-type="VARCHAR" java-type="String" />
		<sql-type native-types="serial" jdbc-type="INTEGER"
			java-type="Integer" />
		<sql-type native-types="TINYINT,INT,INTEGER" java-type="Integer" />
		<sql-type native-types="BIGINT" java-type="Long" />
		<sql-type native-types="NUMBER,DECIMAL,NUMERIC" precision="1..8"
			scale="0" jdbc-type="INTEGER" java-type="Integer" />
		<sql-type native-types="NUMBER,DECIMAL,NUMERIC" precision="9..18"
			scale="0" jdbc-type="INTEGER" java-type="Long" />
		<sql-type native-types="NUMBER,DECIMAL,NUMERIC" precision="2..14"
			scale="1..6" jdbc-type="DOUBLE" java-type="Double" />
		<sql-type native-types="NUMBER,DECIMAL,NUMERIC" precision="19..36"
			scale="0..6" jdbc-type="DECIMAL" java-type="java.math.BigDecimal" />
		<sql-type native-types="BYTEA,BYTE,long binary" jdbc-type="BINARY"
			java-type="byte[]" />
		<sql-type native-types="image,BLOB,LONGBLOB,MEDIUMBLOB"
			jdbc-type="BLOB" java-type="byte[]" />
	</type-mapping>
</quickvo>
```

 quickvo 可以根据表的前缀将POJO分别生成到不同包路径下面，这样做的目的是让项目更加模块化，开发者各自关注自身模块下面的表和对象。
 
 配置完点击quickvo.bat 则完成POJO对象生成。
 
 ![image](D:/POJO.bmp)
 
 以SYS_STAFF_INFO 表为例，产生AbstractStaffInfoVO 和 StaffInfoVO两个类，AbstractStaffInfoVO会根据数据库变化而变化，StaffInfoVO则只进行构造函数部分的修改，并保留开发者自行修改部分内容。
```
/**
 *@Generated by sagacity-quickvo 3.2
 */
package sqltoy.showcase.system.vo.base;

import java.io.Serializable;
import org.sagacity.sqltoy.config.annotation.Entity;
import org.sagacity.sqltoy.config.annotation.Id;
import org.sagacity.sqltoy.config.annotation.Column;
import java.util.Date;


/**
 * @project sqltoy-showcase
 * @version 1.0.0
 * Table: sys_staff_info,Remark:员工信息表   
 */
@Entity(tableName="sys_staff_info",pk_constraint="PRIMARY")
public abstract class AbstractStaffInfoVO implements Serializable,
	java.lang.Cloneable {
	 /*--------------- properties string,handier to copy ---------------------*/
	 //full properties 
	 //staffId,staffCode,organId,staffName,sexType,mobileTel,birthday,dutyDate,outDutyDate,post,nativePlace,email,operator,operateDate,status
	 
	 //not null properties
	 //staffId,staffCode,organId,staffName,operator,operateDate,status

	/**
	 * 
	 */
	private static final long serialVersionUID = 2289977593575720719L;
	
	/**
	 * 员工ID
	 */
	@Id(strategy="generator",generator="org.sagacity.sqltoy.plugin.id.DefaultIdGenerator")
	@Column(name="STAFF_ID",length=22L,type=java.sql.Types.VARCHAR,nullable=false)
	protected String staffId;
	
	/**
	 * 员工工号
	 */
	@Column(name="STAFF_CODE",length=22L,type=java.sql.Types.VARCHAR,nullable=false)
	protected String staffCode;
	
	/**
	 * 机构编号
	 */
	@Column(name="ORGAN_ID",length=22L,type=java.sql.Types.VARCHAR,nullable=false)
	protected String organId;
	
```

 StaffInfoVO类代码片段
 
 
```
/**
 * @project sqltoy-showcase
 * @author zhongxuchen
 * @version 1.0.0 员工信息表 StaffInfoVO generated by sys_staff_info
 */
@SqlToyEntity
/*
 * db则是分库策略配置,table 则是分表策略配置，可以同时配置也可以独立配置
 * 策略name要跟spring中的bean定义name一致,fields表示要以对象的哪几个字段值作为判断依据,可以一个或多个字段
 * maxConcurrents:可选配置，表示最大并行数
 * maxWaitSeconds:可选配置，表示最大等待秒数
 */
@Sharding(db = @Strategy(name = "hashDataSourceSharding", fields = {
		"staffId" }), table = @Strategy(name = "hashDataSourceSharding", fields = {
				"staffId" }), maxConcurrents = 10, maxWaitSeconds = 3600)
public class StaffInfoVO extends AbstractStaffInfoVO {
	/**
	 * 
	 */
	private static final long serialVersionUID = 4657027516282416619L;

	/** default constructor */
	public StaffInfoVO() {
		super();
	}

	/*---begin-constructor-area---don't-update-this-area--*/
	/** pk constructor */
	public StaffInfoVO(String staffId) {
		this.staffId = staffId;
	}

	/** minimal constructor */
	public StaffInfoVO(String staffId, String staffCode, String organId, String staffName, String operator,
			Date operateDate, String status) {
		this.staffId = staffId;
		this.staffCode = staffCode;
		this.organId = organId;
		this.staffName = staffName;
		this.operator = operator;
		this.operateDate = operateDate;
		this.status = status;
	}

	/** full constructor */
	public StaffInfoVO(String staffId, String staffCode, String organId, String staffName, String sexType,
			String mobileTel, Date birthday, Date dutyDate, Date outDutyDate, String post, String nativePlace,
			String email, String operator, Date operateDate, String status) {
		this.staffId = staffId;
		this.staffCode = staffCode;
		this.organId = organId;
		this.staffName = staffName;
		this.sexType = sexType;
		this.mobileTel = mobileTel;
		this.birthday = birthday;
		this.dutyDate = dutyDate;
		this.outDutyDate = outDutyDate;
		this.post = post;
		this.nativePlace = nativePlace;
		this.email = email;
		this.operator = operator;
		this.operateDate = operateDate;
		this.status = status;
	}

	/*---end-constructor-area---don't-update-this-area--*/

	/**
	 * 机构名称
	 */
	private String organName;

	/**
	 * 性别名称
	 */
	private String sexName;

	/**
	 * 岗位名称
	 */
	private String postName;

	/**
	 * @todo vo columns to String
	 */
	public String toString() {
		return super.toString();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#clone()
	 */
	public StaffInfoVO clone() {
		try {
			// TODO Auto-generated method stub
			return (StaffInfoVO) super.clone();
		} catch (CloneNotSupportedException e) {
			e.printStackTrace();
		}
		return null;
	}


```

 参见上述片段quickvo会自动修改下述标记内容之间的构造函数内容，请不要在此片段之间写代码
 
```
 /*---begin-constructor-area---don't-update-this-area--*/
 /*---end-constructor-area---don't-update-this-area--*/

```

# 4. sqltoy-orm 开发详解

 ## 4.1 sql查询类操作
 ### 4.1.1 sql完整编写定义(不要吓一跳感觉很复杂，常态下面很简单)
 
```
<?xml version="1.0" encoding="utf-8"?>
<sqltoy xmlns="http://www.sagframe.com/schema/sqltoy" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" 
      xsi:schemaLocation="http://www.sagframe.com/schema/sqltoy http://www.sagframe.com/schema/sqltoy/sqltoy.xsd">
	<sql id="show_case">
		<filters>
			<!-- 空白转为null -->
			<blank params="*" excludes="staffName" />
			<!-- 等于 -->
			<eq params="organType" value="-1" />
			<!-- 区间 -->
			<between params="" start-value="0" end-value="9999" excludes="" />
			<!-- 字符替换,默认根据正则表达进行全部替换，is-first为true时只替换首个 -->
			<replace params="" regex="" value="" is-first="false" />
			<!-- 将参数条件值转换为日期格式,format可以是yyyy-MM-dd这种自定义格式也可以是:
			 first_day:月的第一天;last_day:月的最后一天,first_year_day:年的第一天,last_year_day年的最后一天 -->
			<to-date params="" format="" increment-days="1" />
			<to-number params="" data-type="decimal" />
			<!-- 小于等于 -->
			<lte value="" params="" />
			<!-- 小于 -->
			<lt value="" params="" />
			<!-- 大于等于 -->
			<gte value="" params="" />
			<!-- 大于 -->
			<gt value="" params="" />
			<!-- 首要参数，即当某个参数不为null时，excludes指定的参数全部为null -->
			<primary param="" excludes="*" />
			<!-- 排他性参数,当某个参数是xxx值时,将其他参数设置为特定值  -->
			<exclusive param="" compare-type="eq" compare-values=""
				set-params="" set-value="" />
		</filters>
		<!-- 缓存翻译,可以多个 -->
		<translate cache="dictCache" cache-type="POST_TYPE" columns="POST_TYPE"
			cache-indexs="1" />
		<!-- 安全掩码:tel\姓名\地址\卡号 -->
		<!--最简单用法: <secure-mask column="" type="tel"/> -->
		<secure-mask column="" type="name" head-size="" tail-size=""
			mask-code="*****" mask-rate="50%" />
		<!-- 分库策略 -->
		<sharding-datasource strategy="" />
		<!-- 分表策略 -->
		<sharding-table tables="" strategy="" params="" />
		<!-- 分页优化,缓存相同查询条件的分页总记录数量, alive-max:表示相同的一个sql保留100个不同条件查询 alive-seconds:相同的查询条件分页总记录数保留时长(单位秒) -->
		<page-optimize alive-max="100" alive-seconds="600" />
		<value>
		<![CDATA[
		select t1.*,t2.ORGAN_NAME from 
		@fast(select * from sys_staff_info t
			  where #[t.sexType=:sexType]
			        #[and t.JOIN_DATE>:beginDate]
			        #[and t.STAFF_NAME like :staffName]
			        -- 是否虚拟员工@if()做逻辑判断
			        #[@if(:isVirtual==true) and t.IS_VIRTUAL=1]
			        ) t1,sys_organ_info t2
	    where t1.ORGAN_ID=t2.ORGAN_ID
		]]>	
		</value>
		<!-- count-sql(只针对分页查询有效,sqltoy分页针对计算count的sql进行了智能处理, 一般不需要额外定义countsql,除极为苛刻的性能优化，sqltoy提供了极度优化的口子) -->
		<count-sql><![CDATA[]]></count-sql>
		<!-- 汇总和求平均 -->
		<summary columns="" radix-size="2" reverse="false" sum-site="left">
			<global sum-label="" label-column="" />
			<group sum-label="" label-column="" group-column="" />
		</summary>
		<!-- 拼接某列,mysql中等同于group_concat\oracle 中的WMSYS.WM_CONCAT功能 -->
		<link sign="," column="" />
		<!-- 行转列 (跟unpivot互斥) -->
		<pivot group-columns="" start-column="" end-column=""
			default-value="0" />
		<!-- 列转行 -->
		<unpivot columns="" values-as-column="" />
	</sql>
</sqltoy>
```

 - 常态写法：
  
  
```
<!-- 查询机构信息 -->
	<sql id="sys_searchOrganInfo">
		<filters>
			<eq value="-1" params="enabled,organProperty" />
		</filters>
		<translate cache="organIdName"
			columns="sign_pid_name,sign_tid_name,parent_alias_name" />
		<translate cache="organTypeName" columns="organ_type_Name"
			cache-indexs="2" />
		<value><![CDATA[
			select
				t.*,t.sign_pid sign_pid_name,t.sign_tid sign_tid_name,t.organ_type organ_type_Name,t.organ_pid parent_alias_name,
				(select count(*) from SYS_ORGAN_INFO c where c.ORGAN_PID = t.ORGAN_ID) as CHILD_NUM
			from 
				SYS_ORGAN_INFO t 
			where t.ORGAN_PID=:organPid
			#[and (instr(t.organ_name,:organName)>0 or instr(t.alias_name,:organName)>0 or t.organ_id like :organName)]
			#[and t.enabled=:enabled]
		]]></value>
	</sql>
```

 - 最常态的写法：
  
```
<!-- 查询法定行政日历 -->
	<sql id="sys_find_lawcalendar">
		<value><![CDATA[
			select lc.HOLIDAY_ID,lc.HOLIDAY_TYPE,lc.COMMENTS,lc.BEGIN_DATE,lc.END_DATE,lc.IS_HOLIDAY 
			from SYS_LAW_CALENDAR lc 
			where 
			#[and lc.BEGIN_DATE >= :beginDate]
			#[and lc.END_DATE <= :endDate]
			#[and lc.IS_HOLIDAY = :isHoliday]
		]]></value>
	</sql>
```

**sql的id命名建议:moduleName_+业务名称,避免不同模块下面id重名。**

### 4.1.2 sql编写规则说明：
   - 基本原则:sqltoy 通过#[] 等同于if(参数值==null) 则#[] 区间的片段全部剔除。#[]可以嵌套，如#[and t.name=:name #[t.sexType=:sexType] ]  
   - filters:参数过滤器，一般通过逻辑处理将参数转为null，便于将#[]的逻辑统一。
   - translate:缓存翻译，将sql的字段结合缓存进行keyValue提取，一般用于如查询员工的机构名称，在缓存中放机构Id和机构名称，这样查询员工机构名称就无需管理机构表而直接通过缓存获取机构名称，从而提升效率，translate可以配置多个。
   - secure-mask:安全脱敏，一般用于金融类项目，对查询结果字段进行脱敏，如账号、手机号显示等。
   - page-optimize：分页优化器，用于缓存查询条件，当在一定周期内查询条件一样，数据库则不再进行记录数查询，而是直接从缓存中提取。从而降低数据库查询次数实现效率提升。
   - @fast():针对分页、取top、取随机记录等，先分页取出小范围数据然后再跟其他表进行关联，一定范围适用，提供性能优化的可能。
   - sharding-datasource：查询分库策略配置，最简单的场景根据条件将数据源定向到不同库，进行分流。
   - sharding-table:查询分表，如实时表和历史表，根据日期条件查询不同表，实现分流提升查询效率。
   - summary:汇总，一般用于对查询结果数据再次进行多层分组汇总求平均，如结果是按照产品类别进行了汇总，还需要对产品大类进行汇总，通过sqltoy的提供的算法实现跨数据库功能，一般分组汇总sql比较复杂，不同数据库语法又不一样。
   - pivot\unpivot:行转列，列转行，即俗称的数据旋转，非常适用于统计分析类报表查询，如交叉报表特别适用，同时让开发者无需写复杂的sql，同时实现跨数据库。同时也提升性能，数据提取出来进行内存处理性能肯定高于sql处理（数据规模是一样的）。
   - count-sql:为极端情况下诸如分页、取top、取随机记录提供求总记录数的自定义最优化查询语句。因为一些复杂查询框架未必能够最优化的组织count语句。如union和 select sum()、decode(),(select from ) from 等子查询。
   - link:分组字段值合并，如查询结果为:'苹果,香蕉' 用逗号拼接模式，sqltoy提供link操作实现跨数据库查询。
 
### 4.3 定义sqltoy的缓存翻译

    - 参见sqltoy-showcase/src/main/resources 目录下面的sqltoy-translate.xml配置
    
```
<?xml version="1.0" encoding="UTF-8"?>
<sagacity
	xmlns="http://www.sagframe.com/schema/sqltoy-translate"
	xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
	xsi:schemaLocation="http://www.sagframe.com/schema/sqltoy-translate http://www.sagframe.com/schema/sqltoy/sqltoy-translate.xsd">
	<cache-translates disk-store-path="./sinochem-oms/translateCaches">
		<sql-translate cache="dictKeyName"
			datasource="dataSource">
			<sql>
			<![CDATA[
				select t.DICT_KEY,t.DICT_NAME,t.SEGMENT
				from sag_dict_detail t
		        where t.DICT_TYPE_CODE=?
		        order by t.SHOW_INDEX
			]]>
			</sql>
		</sql-translate>
		<!-- 机构缓存，翻译使用 -->
		<sql-translate cache="organIdName"
			sql="sys_organIdNameCache" datasource="dataSource" />
		<!-- 人员缓存，翻译使用 -->
		<sql-translate cache="staffIdName"
			sql="sys_staffIdNameCache" datasource="dataSource" />

		<!-- 国家地区翻译 -->
		<sql-translate cache="areaIdName"
			datasource="dataSource">
			<sql><![CDATA[
			select area_id,area_name,city_code,city_name,province_code,province_name,full_name from sys_area_info 
		]]></sql>
		</sql-translate>
		<!-- 销售组织机构翻译 -->
		<sql-translate cache="salesOrganizationIdName"
			datasource="dataSource">
			<sql><![CDATA[
			select org_id,org_name from UC_SALES_ORGANIZATION 
		]]></sql>
		</sql-translate>

		<!-- 资质类别翻译 -->
		<sql-translate cache="qualificationTypeIdName"
			datasource="dataSource">
			<sql><![CDATA[
			select QUALIFICATION_CODE,QUALIFICATION_NAME from UC_QUALIFICATION_TYPE
		]]></sql>
		</sql-translate>
	</cache-translates>

	<!-- 缓存刷新检测 -->
	<cache-update-checkers>
		<!-- 基于sql的缓存更新检测 -->
		<sql-checker
			check-frequency="0..8:30?3000,8:30..20?120,20..24?3600"
			datasource="dataSource">
			<sql><![CDATA[
			--#not_debug#--
			-- 机构缓存更新检测
			select distinct 'organIdName' cacheName,null cache_type
			from sys_organ_info t
			where t.UPDATE_TIME >=:lastUpdateTime
			-- 员工工号姓名缓存检测
			union all 
			select distinct 'staffIdName' cacheName,null cache_type
			from sys_staff_info t1
			where t1.UPDATE_TIME >=:lastUpdateTime
			-- 数据字典key和name缓存检测
			union all 
			select distinct 'dictKeyName' cacheName,t2.DICT_TYPE_CODE cache_type
			from sag_dict_detail t2
			where t2.UPDATE_TIME >=:lastUpdateTime
			-- 资质类别翻译
			union all 
			select distinct 'qualificationTypeIdName' cacheName,null cache_type 
			from UC_QUALIFICATION_TYPE tuc
			where tuc.UPDATE_TIME >=:lastUpdateTime
			-- 国家地区翻译
			union all 
			select distinct 'areaIdName' cacheName,null cache_type 
			from sys_area_info area
			where area.UPDATE_TIME >=:lastUpdateTime
			-- 销售组织机构
			union all 
			select distinct 'salesOrganizationIdName' cacheName,null cache_type 
			from UC_SALES_ORGANIZATION sales
			where sales.UPDATE_TIME >=:lastUpdateTime
			]]></sql>
		</sql-checker>
	</cache-update-checkers>
</sagacity>
```
 - sqltoy配置缓存翻译,参见sqltoy-showcase 项目中src/main/resources 目录下spring-sqltoy.xml配置,片段截取如下:
 
```
<!-- 配置辅助sql处理工具用于sql查询条件的处理 -->
	<bean id="sqlToyContext" name="sqlToyContext" class="org.sagacity.sqltoy.SqlToyContext"
		init-method="initialize">
		<!-- 指定sql.xml 文件的路径实现目录的递归查找,非必须属性 -->
		<property name="sqlResourcesDir" value="classpath:sqltoy/showcase/" />
		<!-- 缓存翻译管理器,非必须属性 -->
		<property name="translateConfig" value="classpath:sqltoy-translate.xml" />
	</bean>
```

### 4.1.3 调用说明
    这里都以Dao为例进行说明

#### 4.1.3.1 普通查询

- sqltoy 查询的变化主要在java调用过程的变化，如分页、top记录、随机记录提取和普通查询可以共用一个sql。
    
    
```
    <sql id="sys_findStaff">
		<!-- 安全脱敏:将姓名进行脱敏 -->
		<secure-mask type="name" column="STAFF_NAME" />
		<value>
		<![CDATA[
		select STAFF_ID,STAFF_CODE,STAFF_NAME,POST,SEX_TYPE, ORGAN_ID,ORGAN_ID ORGAN_NAME
	    from sys_staff_info
	    where 1=1 #[and STAFF_NAME like :staffName]
		]]>
		</value>
	</sql>
	
	java调用过程:
	
	/**
	 * 普通sql查询
	 * 
	 * @param staffInfoVO
	 * @return
	 * @throws Exception
	 */
	public List<StaffInfoVO> searchStaff(StaffInfoVO staffInfoVO) throws Exception {
		//staffInfoVO 中staffName=张
		return super.findBySql("sys_findStaff", staffInfoVO);
	}
	
	另外一种:
	public List<StaffInfoVO> searchStaff(StaffInfoVO staffInfoVO) throws Exception {
		//如果对象属性跟sql中的参数名称不一样
		return super.findBySql("sys_findStaff", new String[] { "staffName" }, new Object[] { staffInfoVO.getStaffName() },
				StaffInfoVO.class);
	}
	
	分页查询:
	
	public PaginationModel findPage(PaginationModel pageModel, StaffInfoVO staffInfoVO) throws Exception {
		// 传统模式
		return super.findPageBySql(pageModel, "sys_findStaff", staffInfoVO);
	}
	
	或者:
	
	public PaginationModel findPage(PaginationModel pageModel, StaffInfoVO staffInfoVO) throws Exception {
		// 链式操作
		return page().pageModel(pageModel).sql("sys_findStaff").entity(staffInfoVO).submit();
	}
	
	取Top记录:topSize  整数表示取整数条，小数表示按百分比提取
	
	/**
	 * 取top记录数
	 * @param staffInfoVO
	 * @param topSize 如果是小数表示按照比例提取
	 * @return
	 * @throws Exception
	 */
	public List<StaffInfoVO> findTopStaff(StaffInfoVO staffInfoVO, double topSize) throws Exception {
		return super.findTopBySql("sys_findStaff", staffInfoVO, topSize);
	}
	
	取随机记录:randomSize如果是小数则按比例随机提取
	
	public List<StaffInfoVO> findRandomStaff(StaffInfoVO staffInfoVO, double randomSize) throws Exception {
		return super.getRandomResult("sys_findStaff", new String[] { "staffName" },
				new Object[] { staffInfoVO.getStaffName() }, StaffInfoVO.class, randomSize);
	}
	
```
#### 4.1.3.2 缓存翻译
    如下代码<translate>实现了机构ID转换成机构名称的翻译过程。
    其中cache是:LinkedMap<String,Object[]> 结构，Object[]数组是{key,name,name1,name2}形式。
    <translate cache="cacheName" cache-type="类别" columns="sql列"
			cache-indexs="1" />
	cache-type:指的是类别参数，如数据字典类别码
	columns:表示可以对多列通过同一个cache进行翻译，如机构全称、机构简称
	cache-indexs:默认为1，当客户需要取机构简称时则cache-indexs="2"
    
```
<sql id="sys_findStaff">
		<!-- 从机构Id和Name的缓存中提取机构名称,避免关联数据库查询，提升效率，简化sql -->
		<translate cache="organIdNameCache" columns="ORGAN_NAME" />
		<value>
		<![CDATA[
		select STAFF_ID,STAFF_CODE,STAFF_NAME,POST,SEX_TYPE, ORGAN_ID,ORGAN_ID ORGAN_NAME
	    from sys_staff_info
	    where 1=1 #[and STAFF_NAME like :staffName]
		]]>
		</value>
	</sql>
```


#### 4.1.3.3 分页优化
    如下:通过<page-optimize/>实现分页查询优化，这个配置只有执行分页查询时起作用。
    alive-max:表示支持多少个不同条件的查询缓存。默认100
    alive-seconds:表示一个不同条件的查询活跃的时长。默认900

```
<sql id="sys_findStaff">
		<!-- 优化分页查询,核心是避免相同的查询条件每次查询总记录数量 -->
		<page-optimize alive-max="100" alive-seconds="600" />
		<value>
		<![CDATA[
		select STAFF_ID,STAFF_CODE,STAFF_NAME,POST,SEX_TYPE, ORGAN_ID,ORGAN_ID ORGAN_NAME
	    from sys_staff_info
	    where 1=1 #[and STAFF_NAME like :staffName]
		]]>
		</value>
	</sql>
```


#### 4.1.3.4 汇总查询


```
<!-- 汇总计算 (场景是sql先汇总，页面上还需要对已有汇总再汇总的情况,如果用sql实现在跨数据库的时候就存在问题) -->
	<sql id="sys_summarySearch">
		<sharding-datasource strategy="multiDataSource" />
		<value>
		<![CDATA[
		select	t.TRANS_CHANNEL,t.TRANS_CODE,sum( t.TRANS_AMT )
		from sys_summary_case t
		group by t.TRANS_CHANNEL,t.TRANS_CODE
		]]>
		</value>
		<summary columns="2" reverse="true" sum-site="left"
			radix-size="2">
			<global sum-label="总计" label-column="0" />
			<group sum-label="小计/平均" label-column="0" group-column="0"
				average-label="平均" />
		</summary>
	</sql>
```


#### 4.1.3.5 旋转查询


```
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


#### 4.1.3.6 link查询

#### 4.1.3.7 @fast快速查询

#### 4.1.3.8 树形结构查询

#### 4.1.3.9 分库分表查询

#### 4.1.3.10 唯一性判断查询

## 4.2 sqltoy对象操作

### 4.2.1 save操作

### 4.2.2 update操作

### 4.2.3 delete操作

### 4.2.4 load操作


## 4.3 其他操作

### 4.3.1 树形结构辅助字段封装

### 4.3.2 存储过程调用

### 4.3.3 sql批量执行

## 4.4 sqltoy链式操作

### 4.4.1 save链式操作

### 4.4.2 update 链式操作

### 4.4.3 delete链式操作 

### 4.4.4 batch链式操作

### 4.4.5 load链式操作






    













