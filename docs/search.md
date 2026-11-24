# 1. sqltoy的sql完整规范
```xml
<?xml version="1.0" encoding="utf-8"?>
<sqltoy xmlns="http://www.sagframe.com/schema/sqltoy" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
	xsi:schemaLocation="http://www.sagframe.com/schema/sqltoy https://sagframe.github.io/schema/sqltoy.xsd">
<!-- id 命名建议遵循: moduleName+functionName 模式,避免不同模块之间重复,
     debug:表示是否开启sql日志输出(默认依据sqltoy全局的debug值)
     blank-to-null:空字符是否转null，默认true,在filters中只要出现<blank params="xx参数" />设置具体参数空白转null就自动关闭了默认
-->	
<sql id="sqltoy_sql_specs" debug="false" blank-to-null="true">
      <!-- filters 用来对参与查询或执行的参数值进行转化处理 -->
      <filters>	
	    <!-- 参数等于某个值则将参数值设置为null -->
	    <eq params="organType" value="-1" />
	    <!-- 将参数条件值转换为日期格式,format可以是yyyy-MM-dd这种自定义格式也可以是: 
	        first_day:月的第一天;last_day:月的最后一天,first_year_day:年的第一天,last_year_day年的最后一天,increment-time 加减时间，increment-unit默认为days -->
	    <to-date params="" format="yyyyMMdd" increment-time="1" increment-unit="days"/>
	    <!-- 场景:前端只有单日期条件时，构造一个endDate形成日期范围过滤,一般和to-data(利用increment-time 加一天) 功能结合使用 -->
	    <clone param="beginDate" as-param="endDate"/>
	    <to-number params="" data-type="decimal" />
	    <!-- 将参数转为字符串,add-quote:none，single，double -->
	    <to-string params="" add-quote="single" />
	    <!-- 在参数的左边加% ,sqltoy默认规则是:参数里面有%符号不做处理，没有%符号则两边加% -->
	    <l-like params="staffName"/>
	    <!-- 在参数的右边边加% -->
	    <r-like params="staffName"/>
	    <!-- 通过缓存将名称用类似like模式匹配出对应的编码作为条件进行精准查询 -->
	    <cache-arg param="" cache-name="" cache-type="" alias-name="" prior-match-equal="">
		<!-- 对缓存进行过滤，比如个人授权的机构、状态为生效的缓存数据等 -->
		<!-- compare-param: 可以是一个参数的属性名称也可以是具体的值，cache-index对应缓存数据的第几列 -->
		<filter compare-param="1" cache-index="4"/>
	    </cache-arg>
	    <!-- 首要参数，比如页面上精准输入了订单编号，此时除特定条件外其他条件全部设置为null不参与查询
		     特定的比如:授权访问机构(避免越权访问)             -->
	    <primary param="orderId" excludes="organIds" />
	    <!-- 将数组转化成in 的参数条件并增加单引号 -->
	    <to-in-arg params="" />
	    <!-- 将日期格式化字符串，可以结合to-number 实现将日期转换为数字，如月份202112 -->
	    <date-format params="" format=""/>	
	    <!-- 空白转为null，一般无需配置，默认就是所有空白自动转为null -->
	    <blank params="*" excludes="staffName" />
	    <!-- 参数值在某个区间则转为null -->
	    <between params="" start-value="0" end-value="9999"	excludes="" />
	    <!-- 将前端传过来的字符串切割成数组 -->
	    <split params="staffAuthOrgs" data-type="string" split-sign="," />
	    <!-- 参数小于等于某个值时转为null -->
	    <lte params="" value="" />
	    <!-- 参数小于某个值时转为null -->
	    <lt params="" value="" />
	    <!-- 参数大于等于某个值时转为null -->
	    <gte params="" value="" />
	    <!-- 参数大于某个值时转为null -->
	    <gt params="" value="" />
	    <!-- 字符替换,默认根据正则表达进行全部替换，is-first为true时只替换首个 -->
	    <replace params="" regex="" value="" is-first="false" />
	    <!--设置参数默认值:sysdate()-1d(d:天，h:小时,w:周;m:月,y:年);
                    first_of_month-3d/first_of_year/first_of_week/last_of_month/last_of_week/last_of_year  -->
	    <default params="beginDate" data-type="localDate" value="sysdate()-1d" />
	    <!-- 排他性参数,当某个参数是xxx值时,将其他参数设置为特定值 -->
	    <exclusive param="" compare-type="eq" compare-values=""	set-params="" set-value="" />
	    <!-- 自定义实现方式的条件参数处理器，实现类通过:spring.sqltoy.customFilterHandler
                   (实现接口:org.sagacity.sqltoy.plugins.FilterHandler)进行设置，一般用于处理一些枚举类型等 -->
	    <custom-handler params="" type=""/>	
	</filters>

	<!-- 缓存翻译,可以对例如:A,B 这种拼连的进行翻译(要指定分隔符号和再次拼装符号 split-regex="," link-sign=",")
	    uncached-template 是针对未能匹配时显示的补充,${value} 表示显示key值,可以key=[${value}未定义这种写法 -->
	<translate cache="dictCache" cache-type="POST_TYPE" columns="POST_TYPE" cache-indexs="1" uncached-template="" 
	where="order_type==purchase"/>
	<!-- 安全掩码:tel\姓名\地址\卡号 -->
	<!--最简单用法: <secure-mask columns="" type="tel"/> -->
	<secure-mask columns="" type="name" head-size="3" tail-size="4" mask-code="*****" mask-rate="50" />
	<!-- 分库策略 -->
	<sharding-datasource strategy="multiDataBase" />
	<!-- 分表策略 -->
	<sharding-table tables="" strategy="hisRealTable" params="" />
	<!-- 分页优化,缓存相同查询条件的分页总记录数量, alive-max:表示相同的一个sql保留100个不同条件查询 
	 alive-seconds:相同的查询条件分页总记录数保留时长(单位秒) -->
	<page-optimize alive-max="100" alive-seconds="600" />
	<!-- 日期格式化 -->
	<date-format columns="" format="yyyy-MM-dd HH:mm:ss" />
	<!-- 数字格式：包括:#,###.00(可自定义)、captial(数字转中文大写)、capital-rmb(大写金额),财务单据上经常要用到 -->
	<number-format columns="" format="capital-rmb" />
	<!-- 树型结构数据排序、层级内部排序、汇总计算 -->
	<tree-sort id-column="organ_id" pid-column="organ_pid"	sum-columns="staff_cnt" level-order-column="staff_cnt" >
	     <!-- 状态为0的不参与汇总计算 -->
	    <sum-filter column="status"  compare-type="neq" compare-values="0"/>
	</tree-sort>
	<value>
	<![CDATA[
	-- sql 中是可以直接写注释的
	select t1.*,t2.ORGAN_NAME from 
	-- 注意:快速分页为了让sql可以直接运行，允许用注释模式标记分页sql的起始位置
	 -- @fast_start
	/*@fast_start*/(
		  select * from sys_staff_info t
		  where #[t.sexType=:sexType]
			#[and t.JOIN_DATE>:beginDate]
			#[and t.STAFF_NAME like :staffName]
			-- 是否虚拟员工@if()做逻辑判断,支持@if(!:isVirtual)
			#[@if(:isVirtual||:isVirtual==0) and t.IS_VIRTUAL=1]
			) t1,
			sys_organ_info t2
    where t1.ORGAN_ID=t2.ORGAN_ID
	]]>	
	</value>
	<!-- count-sql(只针对分页查询有效,sqltoy分页针对计算count的sql进行了智能处理, 
	 一般不需要额外定义countsql，特殊场景下 sqltoy 提供了针对性优化的口子) -->
	<count-sql><![CDATA[]]></count-sql>
	<!-- 汇总和求平均 -->
	<summary sum-columns="" average-columns="" average-radix-sizes="2" reverse="false" sum-site="left" average-skip-null="false">
		<global sum-label="" label-column="" />
		 <!-- order-column: 分组排序列，order-with-sum:默认为true，order-way:desc/asc -->
		<group sum-label="" label-column="" group-column="" order-column=""/>
	</summary>
	<!-- 拼接某列,mysql中等同于group_concat\oracle 中的WMSYS.WM_CONCAT功能,id-columns表示以哪列值为分组(可以多列) -->
	<link id-columns="" sign="," column="" distinct="true"/>
	<!-- 行转列 (跟unpivot互斥) -->
	<pivot category-columns="" group-columns="" start-column=""	end-column="" default-value="0" />
	<!-- 列转行 -->
	<unpivot columns-to-rows="1:xxx,2:xxxx" new-columns-labels="" />
     </sql>
</sqltoy>
```
# sqltoy的核心逻辑:规则很单一,靠filters配置逻辑来规整统一
* #[t1.type in (:types)]等同于 if(types==null) 则剔除#[] 中间的语句
* #[]是支持嵌套的,如:#[and t1.status=:status #[and t.amt>=:amt]] 当status为null这段全部不参与查询
```sql
select * from table t1
where #[t1.type in (:types)]
      #[and t1.bizDate>=:beginDate]
      #[and t1.bizDate<=:endDate]
      #[and t1.status=:status #[and t.amt>=:amt]]
```

# 常规用法说明
* 常规sql,上面规范是完整功能罗列

```xml
<sql id="sqltoy_sql_specs">
	<!-- filters 用来对参与查询或执行的参数值进行转化处理 -->
	<filters>
		<!-- 参数等于某个值则将参数值设置为null -->
		<eq params="organType" value="-1" />
	</filters>
	<!-- 缓存翻译,可以对例如:A,B 这种拼连的进行翻译(要指定分隔符号后最后拼装符号 split-regex="," link-sign=",")
	    uncached-template 是针对未能匹配时显示的补充,${value} 表示显示key值,可以key=[${value}未定义 
		这种写法 -->
	<translate cache="dictCache" cache-type="POST_TYPE" columns="POST_TYPE"/>
	<!-- 日期格式化 -->
	<date-format columns="" format="yyyy-MM-dd HH:mm:ss" />
	<!-- 数字格式 -->
	<number-format columns="" format="capital-rmb" />
	<value>
	<![CDATA[
	-- sql 中是可以直接写注释的
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
</sql>
```


