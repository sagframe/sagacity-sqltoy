package org.sagacity.sqltoy.dialect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.plugins.function.FunctionUtils;

/**
 * 常用函数补齐(2026-10-11)的真库执行冒烟:uuid/year/month/day/last_day/add_months
 * 在10个docker库+H2+sqlite上的转换执行语义。连接信息对齐sqltoy-verify。
 * 断言口径:uuid按家族断言36位带连字符或32位hex;year/month/day对固定日期列取值;
 * last_day锚定2026-03-31;add_months=+2月得2026-05-20,月末钳制用例(1-31+1月=2-28)
 * 仅在钳制语义库上断言(sqlite不转换不执行)。
 */
public class CommonFuncRealDbSmokeTest {

	private static final String[][] PROBES = {
			{ "postgresql", "org.postgresql.Driver", "jdbc:postgresql://localhost:5433/postgres", "postgres",
					"Sqltoy@2026", "postgresql" },
			{ "opengauss", "org.opengauss.Driver", "jdbc:opengauss://localhost:5432/postgres", "gaussdb",
					"Sqltoy@2026", "opengauss" },
			{ "kingbase", "com.kingbase8.Driver", "jdbc:kingbase8://localhost:54321/kingbase", "system",
					"12345678ab", "kingbase" },
			{ "mysql", "com.mysql.cj.jdbc.Driver",
					"jdbc:mysql://localhost:3307/sqltoy?useSSL=false&allowPublicKeyRetrieval=true", "root",
					"Sqltoy@2026", "mysql" },
			{ "sqlserver", "com.microsoft.sqlserver.jdbc.SQLServerDriver",
					"jdbc:sqlserver://localhost:1434;encrypt=false;trustServerCertificate=true", "sa", "Sqltoy@2026",
					"sqlserver" },
			{ "db2", "com.ibm.db2.jcc.DB2Driver", "jdbc:db2://localhost:50000/SQLTOYDB", "db2inst1", "Sqltoy@2026",
					"db2" },
			{ "dm", "dm.jdbc.driver.DmDriver", "jdbc:dm://localhost:5236?schema=SYSDBA", "SYSDBA", "Sqltoy@2026",
					"dm" },
			{ "hana", "com.sap.db.jdbc.Driver", "jdbc:sap://localhost:39041/?databaseName=HXE", "SYSTEM",
					"Sqltoy2026", "hana" },
			{ "clickhouse", "com.clickhouse.jdbc.ClickHouseDriver",
					"jdbc:clickhouse://localhost:8123/default?user=sqltoy&password=Sqltoy@2026", null, null,
					"clickhouse" },
			{ "oceanbase", "com.oceanbase.jdbc.Driver", "jdbc:oceanbase://localhost:2881/test", "root@test",
					"Sqltoy2026", "mysql" },
			// update 2026-10-11 补齐docker全部关系型库(连接信息对齐sqltoy-verify)
			{ "vastbase", "cn.com.vastbase.Driver", "jdbc:vastbase://localhost:5436/postgres", "sqltoy",
					"Sqltoy@2026", "vastbase" },
			{ "oracle", "oracle.jdbc.OracleDriver", "jdbc:oracle:thin:@//localhost:1521/FREEPDB1",
					"system", "Sqltoy@2026", "oracle" },
			{ "og7", "org.opengauss.Driver", "jdbc:opengauss://localhost:5435/postgres", "gaussdb",
					"Sqltoy@2026", "opengauss" },
			{ "tidb", "com.mysql.cj.jdbc.Driver",
					"jdbc:mysql://localhost:4001/test?useSSL=false&allowPublicKeyRetrieval=true", "root", "",
					"tidb" },
			{ "doris", "com.mysql.cj.jdbc.Driver",
					"jdbc:mysql://localhost:9031/sqltoy_verify?useSSL=false&allowPublicKeyRetrieval=true", "root", "",
					"doris" },
			{ "starrocks", "com.mysql.cj.jdbc.Driver",
					"jdbc:mysql://localhost:9030/sqltoy_verify?useSSL=false&allowPublicKeyRetrieval=true", "root", "",
					"starrocks" }, };

	@Test
	public void commonFuncsOnRealDbs() throws Exception {
		FunctionUtils.setFunctionConverts(Arrays.asList("default"));
		List<String> failures = new ArrayList<String>();
		for (String[] probe : PROBES) {
			try {
				verifyOnDb(probe[0], probe[1], probe[2], probe[3], probe[4], probe[5], failures);
			} catch (Throwable e) {
				failures.add(probe[0] + " 环境异常: " + e);
			}
		}
		try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:cfsmoke;DB_CLOSE_DELAY=-1", "sa", "")) {
			try (Statement st = conn.createStatement()) {
				st.execute("create table sqltoy_cf_t (id int primary key, create_time timestamp)");
			}
			insertRow(conn);
			verifyQueries("h2", conn, "h2", failures);
		} catch (Throwable e) {
			failures.add("h2 环境异常: " + e);
		}
		try (Connection conn = DriverManager.getConnection("jdbc:sqlite::memory:"); Statement st = conn.createStatement()) {
			st.execute("create table sqltoy_cf_t (id int primary key, create_time bigint)");
			// 生产绑定形态:JDBC绑定经xerial存毫秒Long,sqliteDateTextExpr按此归一(东八区墙钟)
			st.execute("insert into sqltoy_cf_t values (1," + java.sql.Timestamp.valueOf("2026-03-20 10:30:00").getTime()
					+ ")");
			// sqlite:uuid组合/year-month-day/last_day执行;add_months不转换(响亮放弃)
			String[] exec = { "year", "month", "day", "last_day", "uuid" };
			for (String f : exec) {
				runCase("sqlite", conn, "sqlite", f, failures);
			}
		} catch (Throwable e) {
			failures.add("sqlite 环境异常: " + e);
		}
		assertTrue(failures.isEmpty(), "常用函数真库验证失败清单: " + failures);
	}


	/**
	 * update 2026-10-11 全量拼写审计的真库执行:别库原生写法作为源(locate/posstr/iif/mid/
	 * character_length/octet_length/current_timestamp/hour/dayofmonth/str_to_date等)转换后
	 * 在各库真实执行取值正确。字面量驱动免表依赖。
	 */
	@Test
	public void multiSpellingExecutions() throws Exception {
		FunctionUtils.setFunctionConverts(Arrays.asList("default"));
		List<String> failures = new ArrayList<String>();
		for (String[] probe : PROBES) {
			String label = probe[0], dialect = probe[5];
			// {源sql, 期望前导整数或前缀串}
			List<String[]> cases = new ArrayList<String[]>();
			if ("postgresql".equals(label)) {
				cases.add(new String[] { "select locate('dmi','admin') from (select 1) x", "2" });
				cases.add(new String[] { "select mid('admin',2,3) from (select 1) x", "dmi" });
				cases.add(new String[] { "select character_length('admin') from (select 1) x", "5" });
				cases.add(new String[] { "select hour(timestamp '2026-03-20 10:30:00') from (select 1) x", "10" });
				cases.add(new String[] { "select str_to_date('2026-01-15','%Y-%m-%d') from (select 1) x", "2026-01-15" });
			} else if ("mysql".equals(label) || "oceanbase".equals(label) || "tidb".equals(label)
				|| "doris".equals(label) || "starrocks".equals(label)) {
				cases.add(new String[] { "select iif(1=1,'y','n') from (select 1) x", "y" });
				cases.add(new String[] { "select locate('dmi','admin') from (select 1) x", "2" });
				cases.add(new String[] { "select dayofmonth(date '2026-03-20') from (select 1) x", "20" });
			} else if ("sqlserver".equals(label)) {
				cases.add(new String[] { "select iif(1=1,'y','n')", "y" });
				cases.add(new String[] { "select character_length('admin')", "5" });
				cases.add(new String[] { "select octet_length('admin')", "5" });
				cases.add(new String[] { "select locate('dmi','admin')", "2" });
				cases.add(new String[] { "select hour('2026-03-20 10:30:00')", "10" });
			} else if ("kingbase".equals(label)) {
				cases.add(new String[] { "select locate('dmi','admin') from (select 1) x", "2" });
				cases.add(new String[] { "select hour(timestamp '2026-03-20 10:30:00') from (select 1) x", "10" });
				cases.add(new String[] { "select iif(1=1,'y','n') from (select 1) x", "y" });
			} else if ("hana".equals(label)) {
				cases.add(new String[] { "select locate('dmi','admin') from dummy", "2" });
				cases.add(new String[] { "select dayofmonth('2026-03-20') from dummy", "20" });
			}
			if (cases.isEmpty()) {
				continue;
			}
			Class.forName(probe[1]);
			try (Connection conn = (probe[3] == null) ? DriverManager.getConnection(probe[2])
					: DriverManager.getConnection(probe[2], probe[3], probe[4])) {
				for (String[] cs : cases) {
					String sql = FunctionUtils.getDialectSql(cs[0], dialect);
					try (PreparedStatement pst = conn.prepareStatement(sql); ResultSet rs = pst.executeQuery()) {
						rs.next();
						String actual = String.valueOf(rs.getObject(1));
						boolean ok = actual.startsWith(cs[1]);
						if (!ok) {
							failures.add(label + " " + cs[0] + " 期望~" + cs[1] + " 实际[" + actual + "] sql=" + sql);
						}
					} catch (Exception e) {
						failures.add(label + " " + cs[0] + " 异常: " + e + " sql=" + sql);
					}
				}
			} catch (Throwable e) {
				failures.add(label + " 环境异常: " + e);
			}
		}
		// update 2026-10-11 拼写第二轮:h2/pg/ch原生uuid写法执行
		try (Connection conn = DriverManager.getConnection("jdbc:postgresql://localhost:5433/postgres", "postgres", "Sqltoy@2026")) {
			String pgUuid = execOne(conn, c("select gen_random_uuid()", "postgresql")).toString();
			org.junit.jupiter.api.Assertions.assertTrue(pgUuid.length() == 36 && pgUuid.charAt(8) == '-',
					"pg gen_random_uuid透传应返回36位: " + pgUuid);
		}
		// h2内嵌:mid换名/iif转case/current_timestamp等价now
		try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:cfspl;DB_CLOSE_DELAY=-1", "sa", "")) {
			assertEquals("dmi", execOne(conn, c("select mid('admin',2,3) from (select 1) x", "h2")).toString());
			assertEquals("y", execOne(conn, c("select iif(1=1,'y','n') from (select 1) x", "h2")).toString());
			org.junit.jupiter.api.Assertions.assertNotNull(execOne(conn, c("select current_timestamp", "h2")));
		String h2Ru = execOne(conn, c("select random_uuid()", "h2")).toString();
		org.junit.jupiter.api.Assertions.assertTrue(h2Ru.length() == 36 && h2Ru.charAt(8) == '-',
				"h2 random_uuid透传应返回36位: " + h2Ru);
		}
		// sqlite内嵌:current_timestamp转本地墙钟/hour数值化
		try (Connection conn = DriverManager.getConnection("jdbc:sqlite::memory:")) {
			String nowLocal = execOne(conn, c("select current_timestamp", "sqlite")).toString();
			org.junit.jupiter.api.Assertions.assertTrue(
					nowLocal.startsWith(java.time.LocalDate.now().toString())
							|| nowLocal.startsWith(java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString()),
					"sqlite current_timestamp应为本地今天: " + nowLocal);
			String hr = execOne(conn, c("select hour('2026-03-20 10:30:00') from (select 1) x", "sqlite"))
					.toString();
			org.junit.jupiter.api.Assertions.assertTrue(hr.startsWith("10"), "sqlite hour应=10: " + hr);
		}
		assertTrue(failures.isEmpty(), "多拼写真库执行失败清单: " + failures);
	}

	/**
	 * update 2026-10-11 日期区间加减族真库执行:含月末钳制语义(01-31+1月=02-28)
	 * 与减法方向;sqlite不转换(响亮放弃)不执行。
	 */
	@Test
	public void dateAddFamilyExecutions() throws Exception {
		FunctionUtils.setFunctionConverts(Arrays.asList("default"));
		List<String> failures = new ArrayList<String>();
		for (String[] probe : PROBES) {
			String label = probe[0], dialect = probe[5];
			List<String[]> cases = new ArrayList<String[]>();
			if ("postgresql".equals(label)) {
				cases.add(new String[] { "select date_add(date '2026-01-31', INTERVAL 1 MONTH) from (select 1) x", "2026-02-28" });
				cases.add(new String[] { "select date_sub(date '2026-01-31', INTERVAL 10 DAY) from (select 1) x", "2026-01-21" });
				cases.add(new String[] { "select dateadd(hour,5,timestamp '2026-03-20 10:30:00') from (select 1) x", "2026-03-20 15:30" });
			} else if ("mysql".equals(label) || "oceanbase".equals(label)) {
				cases.add(new String[] { "select dateadd(month,2,'2026-01-31') from (select 1) x", "2026-03-31" });
				cases.add(new String[] { "select adddate('2026-03-20', 5) from (select 1) x", "2026-03-25" });
			} else if ("sqlserver".equals(label)) {
				cases.add(new String[] { "select date_add('2026-03-20', INTERVAL 5 DAY)", "2026-03-25" });
				cases.add(new String[] { "select dateadd(month,1,'2026-01-31')", "2026-02-28" });
			} else if ("kingbase".equals(label) || "opengauss".equals(label) || "og7".equals(label)
				|| "vastbase".equals(label)) {
				cases.add(new String[] { "select date_add(date '2026-01-31', INTERVAL 1 MONTH) from (select 1) x", "2026-02-28" });
			} else if ("dm".equals(label)) {
				cases.add(new String[] { "select dateadd(day,5,date '2026-03-20') from dual", "2026-03-25" });
				cases.add(new String[] { "select date_add(date '2026-01-31', INTERVAL 1 MONTH) from dual", "2026-02-28" });
			} else if ("oracle".equals(label)) {
				cases.add(new String[] { "select dateadd(day,5,date '2026-03-20') from dual", "2026-03-25" });
				cases.add(new String[] { "select date_add(date '2026-01-31', INTERVAL 1 MONTH) from dual", "2026-02-28" });
				cases.add(new String[] { "select date_sub(date '2026-03-20', INTERVAL 3 DAY) from dual", "2026-03-17" });
			} else if ("db2".equals(label)) {
				cases.add(new String[] { "select date_add(date '2026-03-20', INTERVAL 5 DAY) from sysibm.sysdummy1", "2026-03-25" });
			} else if ("hana".equals(label)) {
				cases.add(new String[] { "select dateadd(day,3,'2026-03-20') from dummy", "2026-03-23" });
				cases.add(new String[] { "select date_add(to_timestamp('2026-03-20 10:30:00','YYYY-MM-DD HH24:MI:SS'), INTERVAL 5 HOUR) from dummy", "2026-03-20 15:30" });
			} else if ("clickhouse".equals(label)) {
				cases.add(new String[] { "select date_add(toDate('2026-01-31'), INTERVAL 1 MONTH)", "2026-02-28" });
			}
			if (cases.isEmpty()) {
				continue;
			}
			Class.forName(probe[1]);
			try (Connection conn = (probe[3] == null) ? DriverManager.getConnection(probe[2])
					: DriverManager.getConnection(probe[2], probe[3], probe[4])) {
				for (String[] cs : cases) {
					String sql = FunctionUtils.getDialectSql(cs[0], dialect);
					try (PreparedStatement pst = conn.prepareStatement(sql); ResultSet rs = pst.executeQuery()) {
						rs.next();
						String actual = String.valueOf(rs.getObject(1));
						if (!actual.startsWith(cs[1])) {
							failures.add(label + " " + cs[0] + " 期望~" + cs[1] + " 实际[" + actual + "] sql=" + sql);
						}
					} catch (Exception e) {
						failures.add(label + " " + cs[0] + " 异常: " + e + " sql=" + sql);
					}
				}
			} catch (Throwable e) {
				failures.add(label + " 环境异常: " + e);
			}
		}
		// h2内嵌:月加/天减
		try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:cfda;DB_CLOSE_DELAY=-1", "sa", "")) {
			org.junit.jupiter.api.Assertions.assertTrue(execOne(conn,
					c("select date_add(date '2026-01-31', INTERVAL 1 MONTH)", "h2")).toString().startsWith("2026-02-28"));
			org.junit.jupiter.api.Assertions.assertTrue(execOne(conn,
					c("select date_sub(date '2026-01-31', INTERVAL 3 DAY)", "h2")).toString().startsWith("2026-01-28"));
		}
		assertTrue(failures.isEmpty(), "日期区间加减真库执行失败清单: " + failures);
	}

	/**
	 * update 2026-10-11 第三轮uuid拼写(uuid_generate_v7/uuidv7/uuidv4)的真库严谨验证:
	 * 可执行库实际执行降级转换产物;pg16上三个透传形态断言真库确报函数不存在
	 * (uuidv7/uuidv4为pg18新增,uuid_generate_v7依赖uuid-ossp扩展,不在即响亮——设计自证);
	 * uuid_generate_v4源在pg归一到核心gen_random_uuid()真执行。
	 */
	@Test
	public void uuidV7FamilyRealDbExecutions() throws Exception {
		FunctionUtils.setFunctionConverts(Arrays.asList("default"));
		List<String> failures = new ArrayList<String>();
		for (String[] probe : PROBES) {
			String label = probe[0], dialect = probe[5];
			List<String[]> cases = new ArrayList<String[]>();
			if ("mysql".equals(label) || "oceanbase".equals(label)) {
				// mysql系无v7函数:降级转uuid()执行(36位带连字符)
				cases.add(new String[] { "uuid_generate_v7", "36" });
				cases.add(new String[] { "uuidv7", "36" });
			} else if ("kingbase".equals(label)) {
				// kingbase:降级转sys_guid()(32位hex)
				cases.add(new String[] { "uuidv7", "32" });
			} else if ("sqlserver".equals(label)) {
				// sqlserver:降级转NEWID()(36位)
				cases.add(new String[] { "uuid_generate_v7", "36" });
			}
			if (cases.isEmpty()) {
				continue;
			}
			Class.forName(probe[1]);
			try (Connection conn = (probe[3] == null) ? DriverManager.getConnection(probe[2])
					: DriverManager.getConnection(probe[2], probe[3], probe[4])) {
				for (String[] cs : cases) {
					// sqlserver的派生表列必须具名(No column name was specified)
					String sql = FunctionUtils.getDialectSql("select " + cs[0] + "() from (select 1 as one) x", dialect);
					try (PreparedStatement pst = conn.prepareStatement(sql); ResultSet rs = pst.executeQuery()) {
						rs.next();
						String actual = String.valueOf(rs.getObject(1));
						boolean ok = "36".equals(cs[1])
								? (actual.length() == 36 && actual.charAt(8) == '-' && actual.charAt(13) == '-')
								: (actual.length() == 32 && actual.matches("(?i)[0-9a-f]{32}"));
						if (!ok) {
							failures.add(label + " " + cs[0] + " 期望" + cs[1] + "位 实际[" + actual + "] sql=" + sql);
						}
					} catch (Exception e) {
						failures.add(label + " " + cs[0] + " 异常: " + e + " sql=" + sql);
					}
				}
			}
		}
		// pg16真库:uuid_generate_v4源归一gen_random_uuid()真执行;
		// uuidv7/uuidv4/uuid_generate_v7为pg18/扩展形态,pg16透传后真库确报不存在(响亮自证)
		try (Connection conn = DriverManager.getConnection(
				"jdbc:postgresql://localhost:5433/postgres", "postgres", "Sqltoy@2026")) {
			String v4 = execOne(conn, c("select uuid_generate_v4()", "postgresql")).toString();
			org.junit.jupiter.api.Assertions.assertTrue(v4.length() == 36 && v4.charAt(8) == '-',
					"pg的uuid_generate_v4应归一gen_random_uuid执行: " + v4);
			String[] loud = { "uuidv7", "uuidv4", "uuid_generate_v7" };
			for (String f : loud) {
				String sql = c("select " + f + "()", "postgresql");
				try (PreparedStatement pst = conn.prepareStatement(sql); ResultSet rs = pst.executeQuery()) {
					failures.add("pg16 " + f + " 应响亮失败但可执行: " + sql);
				} catch (Exception e) {
					// 期望路径:pg16无该函数/扩展,真库报错即透传设计的响亮自证
				}
			}
		}
		// h2内嵌:uuidv7源降级random_uuid()执行
		try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:uuv7;DB_CLOSE_DELAY=-1", "sa", "")) {
			String h2v7 = execOne(conn, c("select uuidv7()", "h2")).toString();
			org.junit.jupiter.api.Assertions.assertTrue(h2v7.length() == 36 && h2v7.charAt(8) == '-',
					"h2的uuidv7应降级random_uuid执行: " + h2v7);
		}
		// sqlite内嵌:uuid_generate_v7源走randomblob组合执行
		try (Connection conn = DriverManager.getConnection("jdbc:sqlite::memory:")) {
			String sl = execOne(conn, c("select uuid_generate_v7()", "sqlite")).toString();
			org.junit.jupiter.api.Assertions.assertTrue(sl.length() == 36 && sl.charAt(8) == '-',
					"sqlite的uuid_generate_v7应组合v4形态执行: " + sl);
		}
		assertTrue(failures.isEmpty(), "第三轮uuid拼写真库验证失败清单: " + failures);
	}

	private Object execOne(Connection conn, String sql) throws Exception {
		try (PreparedStatement pst = conn.prepareStatement(sql); ResultSet rs = pst.executeQuery()) {
			rs.next();
			return rs.getObject(1);
		}
	}


	private void verifyOnDb(String label, String driver, String url, String user, String pass, String dialect,
			List<String> failures) throws Exception {
		Class.forName(driver);
		try (Connection conn = (user == null) ? DriverManager.getConnection(url)
				: DriverManager.getConnection(url, user, pass); Statement st = conn.createStatement()) {
			try {
				st.execute("drop table sqltoy_cf_t");
			} catch (Exception e) {
				// ignore
			}
			boolean ch = "clickhouse".equals(label);
			if (ch) {
				st.execute("create table sqltoy_cf_t (id Int32, create_time Nullable(DateTime)) "
						+ "engine=MergeTree() order by id");
			} else if ("doris".equals(dialect) || "starrocks".equals(dialect)) {
				// update 2026-10-11 doris/starrocks建表须显式unique模型+分布式子句
				// doris无timestamp类型,用datetime(starrocks同享)
				st.execute("create table sqltoy_cf_t (id int, create_time datetime)"
						+ " unique key(id) distributed by hash(id) buckets 1");
			} else {
				String idCol = "db2".equals(dialect) ? "id int not null primary key" : "id int primary key";
				// sqlserver的timestamp为rowversion保留类型(不可显式插入),用datetime2
				String tsCol = "sqlserver".equals(dialect) ? "datetime2" : "timestamp";
				st.execute("create table sqltoy_cf_t (" + idCol + ", create_time " + tsCol + ")");
			}
			try {
				insertRow(conn);
				verifyQueries(label, conn, dialect, failures);
			} finally {
				try {
					st.execute("drop table sqltoy_cf_t");
				} catch (Exception e) {
					// ignore
				}
			}
		}
	}

	private void insertRow(Connection conn) throws Exception {
		try (PreparedStatement pst = conn.prepareStatement("insert into sqltoy_cf_t values (?,?)")) {
			pst.setInt(1, 1);
			pst.setTimestamp(2, java.sql.Timestamp.valueOf("2026-03-20 10:30:00"));
			pst.executeUpdate();
		}
	}

	private void verifyQueries(String label, Connection conn, String dialect, List<String> failures) {
		String[] funcs = { "year", "month", "day", "last_day", "add_months", "uuid" };
		for (String f : funcs) {
			// openGauss系(og/og7)/db2的uuid为响亮放弃形态,断言其真库确无此函数(而非执行)
			if ("uuid".equals(f) && ("opengauss".equals(label) || "og7".equals(label) || "db2".equals(label))) {
				runCase(label, conn, dialect, "uuid-loud", failures);
			} else {
				runCase(label, conn, dialect, f, failures);
			}
		}
	}

	private void runCase(String label, Connection conn, String dialect, String func, List<String> failures) {
		String sql = null;
		String expectDesc = null;
		switch (func) {
		case "year":
			sql = c("select year(create_time) from sqltoy_cf_t where id=1", dialect);
			expectDesc = "2026";
			break;
		case "month":
			sql = c("select month(create_time) from sqltoy_cf_t where id=1", dialect);
			expectDesc = "3";
			break;
		case "day":
			sql = c("select day(create_time) from sqltoy_cf_t where id=1", dialect);
			expectDesc = "20";
			break;
		case "last_day":
			sql = c("select last_day(create_time) from sqltoy_cf_t where id=1", dialect);
			expectDesc = "2026-03-31";
			break;
		case "add_months":
			sql = c("select add_months(create_time,2) from sqltoy_cf_t where id=1", dialect);
			expectDesc = "2026-05-20";
			break;
		case "uuid":
			sql = c("select uuid() from sqltoy_cf_t where id=1", dialect);
			expectDesc = "uuid";
			break;
		case "uuid-loud":
			// openGauss系:适配器响亮放弃(无原生uuid),断言真库确报函数不存在
			sql = c("select uuid() from sqltoy_cf_t where id=1", dialect);
			expectDesc = "uuid-loud";
			break;
		}
		if ("uuid-loud".equals(expectDesc)) {
			try (PreparedStatement pst = conn.prepareStatement(sql); ResultSet rs = pst.executeQuery()) {
				failures.add(label + " uuid应响亮失败但真库可执行: " + sql);
			} catch (Exception e) {
				// 期望路径:真库报函数不存在,证明响亮放弃设计正确
			}
			return;
		}
		try {
			String actual;
			try (PreparedStatement pst = conn.prepareStatement(sql); ResultSet rs = pst.executeQuery()) {
				rs.next();
				Object v = rs.getObject(1);
				actual = (v == null) ? "null" : v.toString();
			}
			boolean ok;
			if ("uuid".equals(func)) {
				// 36位带连字符族 vs 32位hex族(hana/kingbase);sqlite/H2的uuid同36位
				boolean dashed = actual.length() == 36 && actual.charAt(8) == '-' && actual.charAt(13) == '-';
				boolean hex32 = actual.length() == 32 && actual.matches("(?i)[0-9a-f]{32}");
				ok = dashed || hex32;
			} else if ("year".equals(func) || "month".equals(func) || "day".equals(func)) {
				// 前导整数比较:mysql9驱动把YEAR()的YEAR类型映射为java.sql.Date("2026-01-01")
				// (值为2026),og/kingbase的extract返回numeric(2026.0),取前导数字段统一口径
				java.util.regex.Matcher nm = java.util.regex.Pattern.compile("^\\d+").matcher(actual);
				ok = nm.find() && expectDesc.equals(nm.group());
			} else {
				// 日期形态:各驱动返回带时间尾或LocalDate,断言日期前缀
				ok = actual.startsWith(expectDesc);
			}
			if (!ok) {
				failures.add(label + " " + func + " 期望~" + expectDesc + " 实际[" + actual + "] sql=" + sql);
			}
		} catch (Exception e) {
			failures.add(label + " " + func + " 执行异常: " + e + " sql=" + sql);
		}
	}

	private String c(String sql, String dialect) {
		return FunctionUtils.getDialectSql(sql, dialect);
	}
}
