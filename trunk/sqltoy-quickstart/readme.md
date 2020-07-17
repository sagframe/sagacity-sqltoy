# 根据大家的反馈，重新打造一个更加清晰的快速上手演示项目

# 学习步骤
* 1. 配置pom引入sqltoy的依赖
* 2. 配置正确pom build避免sql文件无法编译到classes下面
* 3. 配置application.yml 关于sqltoy的配置
* 4. 

## 1. 请参见pom.xml 引入sqltoy,注意版本号使用最新版本

```xml
    <dependency>
		<groupId>com.sagframe</groupId>
		<artifactId>sagacity-sqltoy-starter</artifactId>
		<version>4.13.6</version>
	</dependency>
```

## 2. 注意pom中build的配置,否则导致 *.sql.xml文件无法编译到classes下面去
* 核心配置:src/main/java 下面的<include>**/*.xml</include>

```xml
	<resource>
			<directory>src/main/java</directory>
			<excludes>
				<exclude>**/*.java</exclude>
			</excludes>
			<includes>
				<include>**/*.xml</include>
			</includes>
		</resource>
		<resource>
			<directory>src/main/resources</directory>
			<includes>
				<include>**/*.xml</include>
				<include>**/*.properties</include>
				<include>**/*.yml</include>
				<include>**/*.sql</include>
				<include>**/*.jpg</include>
			</includes>
		</resource>
	</resources>
	<testResources>
		<testResource>
			<directory>src/test/java</directory>
			<excludes>
				<exclude>**/*.java</exclude>
			</excludes>
			<includes>
				<include>**/*.xml</include>
			</includes>
		</testResource>
		<testResource>
			<directory>src/test/resources</directory>
			<includes>
				<include>**/*.xml</include>
				<include>**/*.properties</include>
				<include>**/*.yml</include>
				<include>**/*.sql</include>
			</includes>
		</testResource>
	</testResources>
```

##  3. application.yml配置
* 常规配置，核心要点:sqlResourcesDir 是路径名,多个路径用逗号分隔,不要填错

```
#完整路径:spring.sqltoy
spring:
   sqltoy:
        # 多个路径用逗号分隔(这里要注意是路径,sqltoy会自动向下寻找以sql.xml结尾的文件,不要写成classpath:com/**/*.sql.xml)
        sqlResourcesDir: classpath:com/sqltoy/quickstart
        # 默认值为classpath:sqltoy-translate.xml，一致则可以不用设置
        translateConfig: classpath:sqltoy-translate.xml
        # 默认为false，debug模式将打印执行sql,并自动检测sql文件更新并重新加载
        debug: true
        # 提供统一字段:createBy createTime updateBy updateTime 等字段补漏性(为空时)赋值(可选配置)
        unifyFieldsHandler: com.sqltoy.plugins.SqlToyUnifyFieldsHandler
        # sql执行超过多长时间则进行日志输出,用于监控哪些慢sql(可选配置:默认30秒)
        printSqlTimeoutMillis: 300000
        # 数据库保留字兼容处理(原则上不要使用数据库保留字,多个用逗号分隔)
        #reservedWords: maxvalue,minvalue
```

* 最简单配置

```
#完整路径:spring.sqltoy
spring:
   sqltoy:
        # 多个路径用逗号分隔(注意这里填路径、路径!会自动相信寻找)
        sqlResourcesDir: classpath:com/sqltoy/quickstart
```

