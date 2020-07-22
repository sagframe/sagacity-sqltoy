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
