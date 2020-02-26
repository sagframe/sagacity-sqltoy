/**
 * 
 */
package com.sagframe.sqltoy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * @author zhongxuchen
 *
 */
@SpringBootApplication
//使用SqlToyCRUDService 自身在类上加了事务注解,所以无需通过xml aop事务配置
//@ImportResource(locations = { "classpath:spring/spring-aop.xml" })
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
