# 学习sqltoy-orm的基本原则
* 不要带入mybatis(plus)等开源项目的使用惯性思维
* 用显式逻辑和直截了当的思维来看待和学习sqltoy-orm
* 看到一些跟以往项目模式有些许差异的地方，不要急于否定

# 快速搭建sqltoy项目的步骤

## 1、创建一个springboot项目，并配置好数据源
* 参见:[sqltoy演示项目sqltoy-helloworld](https://gitee.com/sagacity/sqltoy-helloworld)

```java
@SpringBootApplication
@ComponentScan(basePackages = { "com.sqltoy.helloworld" })
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
* 数据库连接池使用hikari(可以根据自己情况自行选择)

```xml
<!-- spring自带的数据库连接池 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-jdbc</artifactId>
    <version>3.4.6</version>
</dependency>

```
* 配置application.yml

```yml
spring:
    datasource:
       name: dataSource
       type: com.zaxxer.hikari.HikariDataSource
       driver-class-name: com.mysql.cj.jdbc.Driver
       username: helloworld
       password: helloworld
       isAutoCommit: false
       url: jdbc:mysql://127.0.0.1:3306/helloworld?useUnicode=true&characterEncoding=utf-8&serverTimezone=GMT%2B8&useSSL=false&allowPublicKeyRetrieval=true
    sqltoy:
       #非必选项，设置sql.xml文件存放路径(会自动向下扫描,多路径范例:classpath:com/sqltoy/helloworld,classpath:com/sqltoy/system
       sqlResourcesDir: classpath:com/sqltoy/helloworld
       # 默认为false，debug模式将打印执行sql,并自动检测sql文件更新并重新加载
       debug: true
  
```

## 2、在pom.xml中引入sqltoy-orm-spring-starter
* springboot场景

```xml
<dependency>
	<groupId>com.sagframe</groupId>
	<artifactId>sagacity-sqltoy-spring-starter</artifactId>
	<!-- jdk1.8则使用5.6.49.jre8 -->
	<version>5.6.49</version>
</dependency>
```
* solon场景

```xml
<dependency>
	<groupId>com.sagframe</groupId>
	<artifactId>sagacity-sqltoy-solon-plugin</artifactId>
	<!-- jdk1.8则使用5.6.49.jre8 -->
	<version>5.6.49</version>
</dependency>
```
## 3、创建表:sqltoy_order_info

```sql
DROP TABLE IF EXISTS SQLTOY_ORDER_INFO;
CREATE TABLE SQLTOY_ORDER_INFO(
    `ORDER_ID` VARCHAR(32) NOT NULL  COMMENT '订单编号' ,
    `ORDER_TYPE` VARCHAR(32)   COMMENT '订单类别' ,
    `PRODUCT_CODE` VARCHAR(32)   COMMENT '商品代码' ,
    `UOM` VARCHAR(30)   COMMENT '计量单位' ,
    `PRICE` DECIMAL(24,6)   COMMENT '价格' ,
    `QUANTITY` DECIMAL(24,6)   COMMENT '数量' ,
    `TOTAL_AMT` DECIMAL(24,6)   COMMENT '订单总金额' ,
    `STAFF_CODE` VARCHAR(32)   COMMENT '销售员' ,
    `ORGAN_ID` VARCHAR(32)   COMMENT '销售部门' ,
    `STATUS` INT   COMMENT '订单状态' ,
    `CREATE_BY` VARCHAR(32)   COMMENT '创建人' ,
    `CREATE_TIME` DATETIME   COMMENT '创建时间' ,
    `UPDATE_BY` VARCHAR(32)   COMMENT '更新人' ,
    `UPDATE_TIME` DATETIME   COMMENT '更新时间' ,
    PRIMARY KEY (ORDER_ID)
)  COMMENT = 'sqltoy订单信息演示表';

```
## 4、配置sqltoy生成pojo、dto的maven插件quickvo-maven-plugin

* pom.xml中加入quickvo-maven-plugin

```xml
<plugin>
	<groupId>com.sagframe</groupId>
	<artifactId>quickvo-maven-plugin</artifactId>
	<version>1.0.7</version>
	<configuration>
		<configFile>./src/main/resources/quickvo.xml</configFile>
		<baseDir>${project.basedir}</baseDir>
	</configuration>
	<dependencies>
		<dependency>
			<groupId>com.mysql</groupId>
			<artifactId>mysql-connector-j</artifactId>
			<version>${mysql.version}</version>
		</dependency>
	</dependencies>
</plugin>
```

* 在src/main/resources下面创建quickvo.xml
  有关quickvo.xml的配置，详细请参见:[quickvo-maven-plugin](https://gitee.com/sagacity/maven-quickvo-plugin)
  
```xml
<?xml version="1.0" encoding="UTF-8"?>
<quickvo xmlns="http://www.sagframe.com/schema/quickvo"
	xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
	xsi:schemaLocation="http://www.sagframe.com/schema/quickvo http://www.sagframe.com/schema/sqltoy/quickvo.xsd">
	<!-- db配置文件 -->
	<property file="src/main/resources/application.yml" />
	<property name="project.version" value="1.0.0" />
	<property name="project.name" value="sqltoy-helloworld" />
	<!-- 定义项目的默认包路径 -->
	<property name="project.package" value="com.sqltoy.helloworld" />
	<!-- 数据库定义,这里可以直接写具体值 -->
	<datasource name="helloworld" url="${spring.datasource.url}"
		driver="com.mysql.cj.jdbc.Driver" schema="${spring.datasource.username}"
		username="${spring.datasource.username}" password="${spring.datasource.password}" />
	<!-- dist 定义生成的java代码存放路径,相对于pom中baseDir -->
	<tasks dist="src/main/java" encoding="UTF-8">
		<!-- 可以设置多个任务便于将pojo生成到不同包路径下 -->
		<task datasource="helloworld" author="zhongxuchen"	include="^SQLTOY_\w+" active="true">
			<entity package="${project.package}.entity" substr="Sqltoy"	name="#{subName}" lombok-chain="true" />
			<vo package="${project.package}.dto" lombok-chain="true" substr="Sqltoy" name="#{subName}VO" />
		</task>
	</tasks>
</quickvo>
```
## 5、执行quickvo，生成pojo和dto
* 在项目根路径下执行:mvn quickvo:quickvo
* 在src/main/java/目录下com.sqltoy.helloworld.dto包下面会生成OrderInfoVO.java
* 在src/main/java/目录下com.sqltoy.helloworld.entity包下面会生成OrderInfo.java  
  会包含:@Entity、@Id、@Column 等描述对象跟数据库表关系的注解

```java
@Data
@Accessors(chain = true)
@Entity(tableName="sqltoy_order_info",comment="sqltoy订单信息演示表",pk_constraint="PRIMARY")
public class OrderInfo implements Serializable {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 7200696852961513069L;
/*---begin-auto-generate-don't-update-this-area--*/	

	/**
	 * 订单编号
	 */
	@Id(strategy="generator",generator="org.sagacity.sqltoy.plugins.id.impl.NanoTimeIdGenerator")
	@Column(name="ORDER_ID",comment="订单编号",length=32L,type=java.sql.Types.VARCHAR,nativeType="VARCHAR",nullable=false)
	private String orderId;

	/**
	 * 订单类别
	 */
	@Column(name="ORDER_TYPE",comment="订单类别",length=32L,type=java.sql.Types.VARCHAR,nativeType="VARCHAR",nullable=true)
	private String orderType;

	/**
	 * 商品代码
	 */
	@Column(name="PRODUCT_CODE",comment="商品代码",length=32L,type=java.sql.Types.VARCHAR,nativeType="VARCHAR",nullable=true)
	private String productCode;

	/**
	 * 计量单位
	 */
	@Column(name="UOM",comment="计量单位",length=30L,type=java.sql.Types.VARCHAR,nativeType="VARCHAR",nullable=true)
	private String uom;

	/**
	 * 价格
	 */
	@Column(name="PRICE",comment="价格",length=24L,scale=6,type=java.sql.Types.DECIMAL,nativeType="DECIMAL",nullable=true)
	private BigDecimal price;

	/**
	 * 数量
	 */
	@Column(name="QUANTITY",comment="数量",length=24L,scale=6,type=java.sql.Types.DECIMAL,nativeType="DECIMAL",nullable=true)
	private BigDecimal quantity;

	/**
	 * 订单总金额
	 */
	@Column(name="TOTAL_AMT",comment="订单总金额",length=24L,scale=6,type=java.sql.Types.DECIMAL,nativeType="DECIMAL",nullable=true)
	private BigDecimal totalAmt;

	/**
	 * 销售员
	 */
	@Column(name="STAFF_CODE",comment="销售员",length=32L,type=java.sql.Types.VARCHAR,nativeType="VARCHAR",nullable=true)
	private String staffCode;

	/**
	 * 销售部门
	 */
	@Column(name="ORGAN_ID",comment="销售部门",length=32L,type=java.sql.Types.VARCHAR,nativeType="VARCHAR",nullable=true)
	private String organId;

	/**
	 * 订单状态
	 */
	@Column(name="STATUS",comment="订单状态",length=10L,type=java.sql.Types.INTEGER,nativeType="INT",nullable=true)
	private Integer status;

	/**
	 * 创建人
	 */
	@Column(name="CREATE_BY",comment="创建人",length=32L,type=java.sql.Types.VARCHAR,nativeType="VARCHAR",nullable=true)
	private String createBy;

	/**
	 * 创建时间
	 */
	@Column(name="CREATE_TIME",comment="创建时间",length=19L,type=java.sql.Types.DATE,nativeType="DATETIME",nullable=true)
	private LocalDateTime createTime;

	/**
	 * 更新人
	 */
	@Column(name="UPDATE_BY",comment="更新人",length=32L,type=java.sql.Types.VARCHAR,nativeType="VARCHAR",nullable=true)
	private String updateBy;

	/**
	 * 更新时间
	 */
	@Column(name="UPDATE_TIME",comment="更新时间",length=19L,type=java.sql.Types.DATE,nativeType="DATETIME",nullable=true)
	private LocalDateTime updateTime;
	/** default constructor */
	public OrderInfo() {
	}
	
	/** pk constructor */
	public OrderInfo(String orderId)
	{
		this.orderId=orderId;
	}
/*---end-auto-generate-don't-update-this-area--*/
}
```

## 6、创建一个service和单元测试类

* 1、创建OrderInfoService接口

```java
package com.sqltoy.helloworld.service;

import org.sagacity.sqltoy.model.Page;
import com.sqltoy.helloworld.dto.OrderInfoVO;

public interface OrderInfoService {
	/**
	 * 创建订单
	 * 
	 * @param orderInfoVO
	 */
	public void createOrderInfo(OrderInfoVO orderInfoVO);

	/**
	 * 分页查询订单信息
	 * 
	 * @param pageModel
	 * @param queryMap
	 * @return
	 */
	public Page<OrderInfoVO> searchOrderInfo(Page pageModel, Map queryMap);
}
```

* 2、编写OrderInfoService实现类OrderInfoServiceImpl

```java
/**
 * 订单服务逻辑实现
 * 
 * @author zhongxuchen
 * @date 2025/2/5
 */
@Service("orderInfoService")
public class OrderInfoServiceImpl implements OrderInfoService {
	// 注入sqltoy框架自带的LightDao
	@Autowired
	LightDao lightDao;

	// 所有单表操作对象化完成，类似jpa
	@Override
	@Transactional
	public void createOrderInfo(OrderInfoVO orderInfoVO) {
		// 调用框架自带的dto<-->pojo 映射方法创建Entity实例
		OrderInfo orderInfoEntity = lightDao.convertType(orderInfoVO, OrderInfo.class);
		// 调用save完成保存
		lightDao.save(orderInfoEntity);
	}

	// 复杂查询通过sql.xml 定义具体sql内容
	@Override
	public Page<OrderInfoVO> searchOrderInfo(Page pageModel, Map queryMap) {
		String sql = """
				select * from SQLTOY_ORDER_INFO t
				where 1=1
				#[and t.status in (:statusAry)]
				#[and t.create_time>=:beginTime]
				#[and t.create_time<=:endTime]
				""";
		return lightDao.findPage(pageModel, sql, queryMap, OrderInfoVO.class);
	}

}
```

* 3、编写单元测试类OrderInfoServiceTest

```java
@SpringBootTest
public class OrderInfoServiceTest {
	@Autowired
	OrderInfoService orderInfoService;

	@Test
	public void testCreateOrderInfo() {
		OrderInfoVO orderInfoVO = new OrderInfoVO();
		orderInfoVO.setOrderType("PO");
		orderInfoVO.setOrganId("T001");
		orderInfoVO.setProductCode("P0001");
		orderInfoVO.setPrice(BigDecimal.valueOf(100));
		orderInfoVO.setQuantity(BigDecimal.valueOf(100));
		orderInfoVO.setTotalAmt(BigDecimal.valueOf(10000));
		orderInfoVO.setUom("KG");
		orderInfoVO.setStaffCode("S0001");
		orderInfoVO.setCreateBy("S0001");
		orderInfoVO.setCreateTime(LocalDateTime.now());
		orderInfoVO.setStatus(1);
		orderInfoVO.setUpdateBy("S0001");
		orderInfoVO.setUpdateTime(LocalDateTime.now());
		orderInfoService.createOrderInfo(orderInfoVO);
	}

	@Test
	public void testSearchOrderInfo() {
		Page pageModel = orderInfoService.searchOrderInfo(new Page(10, 1),
				MapKit.keys("statusAry", "beginTime", "endTime").values(new Integer[] { 1 },
						LocalDateTime.parse("2024-10-17T00:00:01"), null));
		System.err.println(JSON.toJSONString(pageModel));
	}

}
```
