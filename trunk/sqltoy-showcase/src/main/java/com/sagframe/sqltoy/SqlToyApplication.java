/**
 * 
 */
package com.sagframe.sqltoy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ImportResource;

/**
 * @project sqltoy-showcase
 * @description 请在此说明类的功能
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:SqlToyApplication.java,Revision:v1.0,Date:2019年7月12日
 */
@SpringBootApplication
@ImportResource("classpath:spring-context.xml")
public class SqlToyApplication {
	static {
		System.setProperty("log4j.configurationFile", "log4j2.xml");
	}

	public static void main(String[] args) {
		SpringApplication.run(SqlToyApplication.class, args);
	}
}
