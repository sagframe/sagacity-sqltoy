package org.sagacity.sqltoy.dialect;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;

import org.junit.jupiter.api.Test;

public class TempSeqDiagTest {
	@Test
	public void probe() throws Exception {
		File cfg = new File("target/sqlserver-probe.properties");
		System.out.println("[DIAG] cfg exists=" + cfg.exists() + " cwd=" + new File("").getAbsolutePath());
		if (!cfg.exists()) {
			return;
		}
		try {
			Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
			System.out.println("[DIAG] driver ok");
			Connection conn = DriverManager.getConnection(
					"jdbc:sqlserver://localhost:1433;encrypt=false;trustServerCertificate=true;loginTimeout=5",
					"sa", "Sqltoy@2026");
			System.out.println("[DIAG] conn ok");
			conn.close();
		} catch (Exception e) {
			System.out.println("[DIAG] error: " + e.getMessage());
		}
	}
}
