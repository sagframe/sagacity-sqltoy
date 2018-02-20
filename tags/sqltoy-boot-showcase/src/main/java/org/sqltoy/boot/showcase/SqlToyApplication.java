package org.sqltoy.boot.showcase;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

@EnableAutoConfiguration
public class SqlToyApplication {
	public static void main(String[] args) {
		SpringApplication.run(SqlToyApplication.class, args);
	}
}
