/**
 * 
 */
package com.sagframe.sqltoy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ImportResource;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * @author zhongxuchen
 *
 */
@SpringBootApplication
@ImportResource(locations = { "classpath:spring/spring-aop.xml" })
@ComponentScan(basePackages = { "org.sagacity.sqltoy", "com.sagframe.sqltoy" })
@EnableTransactionManagement
public class SqlToyApplication {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		SpringApplication.run(SqlToyApplication.class, args);
	}

}
