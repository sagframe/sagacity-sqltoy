package org.sagacity.sqltoy.dialect;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Properties;

import org.junit.jupiter.api.Test;

public class TempDb2WaitTest {
	@Test
	public void probe() throws Exception {
		Class.forName("com.ibm.db2.jcc.DB2Driver");
		Properties p = new Properties();
		p.load(new java.io.FileInputStream("target/db2-probe.properties"));
		for (int i = 0; i < 60; i++) {
			try (Connection c = DriverManager.getConnection(p.getProperty("url"), p.getProperty("username"),
					p.getProperty("password"))) {
				System.out.println("[READY] db2连接成功,等待了约" + (i * 10) + "秒");
				return;
			} catch (Exception e) {
				System.out.println("[WAIT] 第" + (i + 1) + "次: " + e.getMessage().split("\n")[0]);
			}
			Thread.sleep(10000);
		}
		throw new IllegalStateException("db2 10分钟内未就绪");
	}
}
