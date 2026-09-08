package org.sagacity.sqltoy.dialect;

import java.sql.Connection;
import java.sql.DriverManager;

import org.junit.jupiter.api.Test;

public class TempSqlServerWaitTest {
	@Test
	public void probe() throws Exception {
		Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
		for (int i = 0; i < 24; i++) {
			try (Connection c = DriverManager.getConnection(
					"jdbc:sqlserver://localhost:1433;encrypt=false;trustServerCertificate=true;loginTimeout=5",
					"sa", "Sqltoy@2026")) {
				System.out.println("[READY] sqlserver连接成功,等待了约" + (i * 5) + "秒");
				return;
			} catch (Exception e) {
				System.out.println("[WAIT] 第" + (i + 1) + "次: " + e.getMessage().split("\n")[0]);
			}
			Thread.sleep(5000);
		}
		throw new IllegalStateException("sqlserver 2分钟内未就绪");
	}
}
