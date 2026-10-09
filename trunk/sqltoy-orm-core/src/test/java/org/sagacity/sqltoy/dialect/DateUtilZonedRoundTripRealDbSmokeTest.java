package org.sagacity.sqltoy.dialect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.utils.DateUtil;

/**
 * DateUtil时区字符串解析×真实数据库JDBC往返冒烟(2026-10-9):
 * 今日ZONED_TIME_PATTERN纳入裸Z/z后,验证各类末尾时区形态经DateUtil解析
 * (parseString按本地墙钟/parseZonedDateTime按真实偏移)产出的java.util.Date
 * 绑定timestamp列后读回毫秒一致——即解析结果在8库的JDBC链路上无损。
 * 连接信息对齐sqltoy-verify工程(pg=5433/opengauss=5432/hana=SYSTEM等);
 * 探针表sqltoy_probe_du测完即删。容器不在位时该测试失败(非跳过),属环境前置。
 */
public class DateUtilZonedRoundTripRealDbSmokeTest {

	/** {标签, 驱动类, url, 用户, 密码, 时间列DDL} 连接信息源:sqltoy-verify的application-*.yml */
	private static final String[][] PROBES = {
			{ "postgresql", "org.postgresql.Driver", "jdbc:postgresql://localhost:5433/postgres", "postgres",
					"Sqltoy@2026", "timestamp" },
			{ "mysql", "com.mysql.cj.jdbc.Driver",
					"jdbc:mysql://localhost:3307/sqltoy?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai",
					"root", "Sqltoy@2026", "datetime(3)" },
			{ "sqlserver", "com.microsoft.sqlserver.jdbc.SQLServerDriver",
					"jdbc:sqlserver://localhost:1434;encrypt=false;trustServerCertificate=true", "sa", "Sqltoy@2026",
					"datetime2" },
			{ "db2", "com.ibm.db2.jcc.DB2Driver", "jdbc:db2://localhost:50000/SQLTOYDB", "db2inst1", "Sqltoy@2026",
					"timestamp(3)" },
			{ "dm", "dm.jdbc.driver.DmDriver", "jdbc:dm://localhost:5236?schema=SYSDBA", "SYSDBA", "Sqltoy@2026",
					"timestamp(6)" },
			{ "hana", "com.sap.db.jdbc.Driver", "jdbc:sap://localhost:39041/?databaseName=HXE", "SYSTEM",
					"Sqltoy2026", "TIMESTAMP" },
			{ "clickhouse", "com.clickhouse.jdbc.ClickHouseDriver",
					"jdbc:clickhouse://localhost:8123/default?user=sqltoy&password=Sqltoy@2026&use_time_zone=Asia/Shanghai",
					null, null, "DateTime64(3)" },
			{ "opengauss", "org.opengauss.Driver", "jdbc:opengauss://localhost:5432/postgres", "gaussdb",
					"Sqltoy@2026", "timestamp" },
			{ "kingbase", "com.kingbase8.Driver", "jdbc:kingbase8://localhost:54321/kingbase", "system",
					"12345678ab", "timestamp" }, };

	/** 样本:覆盖今日改动的裸Z/z与既有±HH:mm/[时区ID]形态,毫秒位验证精度无损 */
	private static final String[] SAMPLES = { "2026-01-15 10:30:00", "2026-01-15T10:30:00Z", "2026-01-15T10:30:00z",
			"2026-01-15 10:30:00.123Z", "2026-01-15 10:30:00+08:00", "2026-01-15 10:30:00+08:00[Asia/Shanghai]" };

	@Test
	public void zonedStringsRoundTripThroughJdbc() throws Exception {
		List<String> failures = new ArrayList<String>();
		for (String[] probe : PROBES) {
			String db = probe[0];
			try {
				roundTripOnDb(db, probe);
			} catch (Throwable e) {
				failures.add(db + " 异常: " + e);
			}
		}
		assertTrue(failures.isEmpty(), "时区字符串JDBC往返失败清单: " + failures);
	}

	private void roundTripOnDb(String db, String[] probe) throws Exception {
		Class.forName(probe[1]);
		try (Connection conn = (probe[3] == null) ? DriverManager.getConnection(probe[2])
				: DriverManager.getConnection(probe[2], probe[3], probe[4]); Statement st = conn.createStatement()) {
			try {
				st.execute("drop table sqltoy_probe_du");
			} catch (Exception e) {
				// 首次运行表不存在(db2等无if exists语法),忽略
			}
			// clickhouse的MergeTree引擎必须显式声明;db2的主键列须显式not null(-542实测)
			String engine = "clickhouse".equals(db) ? " engine=MergeTree() order by id" : "";
			String idCol = "db2".equals(db) ? "id int not null primary key" : "id int primary key";
			st.execute("create table sqltoy_probe_du (" + idCol + ", ts " + probe[5] + ")" + engine);
			try {
				int id = 0;
				for (String sample : SAMPLES) {
					// parseString:剥离时区按本地墙钟(框架参数绑定的实际语义)
					Date wallClock = DateUtil.parseString(sample);
					// parseZonedDateTime:按真实偏移取时刻(仅时区形态样本有差异)
					ZonedDateTime zdt = DateUtil.parseZonedDateTime(sample);
					Date zoned = (zdt == null) ? null : Date.from(zdt.toInstant());
					insertAndReadBack(conn, ++id, wallClock, sample + "[parseString]", db);
					if (zoned != null) {
						insertAndReadBack(conn, ++id, zoned, sample + "[parseZonedDateTime]", db);
					}
				}
			} finally {
				try {
					st.execute("drop table sqltoy_probe_du");
				} catch (Exception e) {
					// ignore
				}
			}
		}
	}

	private void insertAndReadBack(Connection conn, int id, Date value, String tag, String db) throws Exception {
		try (PreparedStatement pst = conn.prepareStatement("insert into sqltoy_probe_du (id, ts) values (?,?)")) {
			pst.setInt(1, id);
			pst.setTimestamp(2, new Timestamp(value.getTime()));
			pst.executeUpdate();
		}
		try (PreparedStatement pst = conn.prepareStatement("select ts from sqltoy_probe_du where id=?")) {
			pst.setInt(1, id);
			try (ResultSet rs = pst.executeQuery()) {
				assertTrue(rs.next(), db + " " + tag + " 应读回记录");
				long readBack = rs.getTimestamp(1).getTime();
				assertEquals(value.getTime(), readBack, db + " " + tag + " 毫秒应无损往返");
			}
		}
	}
}
