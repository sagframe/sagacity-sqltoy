package org.sagacity.sqltoy.dialect;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.plugins.function.FunctionUtils;

/**
 * 全函数×全数据库执行场景总扫(2026-10-11):23个注册适配器的典型用法在16个docker关系型库
 * +H2+sqlite共18环境逐条转换执行断言。用例表{说明,源SQL,断言种类,期望,跳过标签CSV};
 * 断言种类:EQ全等/PREFIX前缀/INT前导整数/NONNULL非空/UUID(36带连字符或32hex)。
 * 标量用例经各库dual形态;表用例经探针表sqltoy_all_t(2行:admin/user)。
 * 连接信息对齐sqltoy-verify;响亮放弃设计(og系uuid/ch聚合排序/sqlite区间加法)按标签跳过。
 */
public class AllFunctionsAllDbsRealDbSmokeTest {

	private static final String[][] PROBES = {
			{ "postgresql", "org.postgresql.Driver", "jdbc:postgresql://localhost:5433/postgres", "postgres",
					"Sqltoy@2026", "postgresql", "" },
			{ "kingbase", "com.kingbase8.Driver", "jdbc:kingbase8://localhost:54321/kingbase", "system",
					"12345678ab", "kingbase", "" },
			{ "vastbase", "cn.com.vastbase.Driver", "jdbc:vastbase://localhost:5436/postgres", "sqltoy",
					"Sqltoy@2026", "vastbase", "" },
			{ "mysql", "com.mysql.cj.jdbc.Driver",
					"jdbc:mysql://localhost:3307/sqltoy?useSSL=false&allowPublicKeyRetrieval=true", "root",
					"Sqltoy@2026", "mysql", "" },
			{ "tidb", "com.mysql.cj.jdbc.Driver",
					"jdbc:mysql://localhost:4001/test?useSSL=false&allowPublicKeyRetrieval=true", "root", "", "tidb",
					"" },
			{ "doris", "com.mysql.cj.jdbc.Driver",
					"jdbc:mysql://localhost:9031/sqltoy_verify?useSSL=false&allowPublicKeyRetrieval=true", "root", "",
					"doris", "" },
			{ "starrocks", "com.mysql.cj.jdbc.Driver",
					"jdbc:mysql://localhost:9030/sqltoy_verify?useSSL=false&allowPublicKeyRetrieval=true", "root", "",
					"starrocks", "" },
			{ "sqlserver", "com.microsoft.sqlserver.jdbc.SQLServerDriver",
					"jdbc:sqlserver://localhost:1434;encrypt=false;trustServerCertificate=true", "sa", "Sqltoy@2026",
					"sqlserver", "" },
			{ "db2", "com.ibm.db2.jcc.DB2Driver", "jdbc:db2://localhost:50000/SQLTOYDB", "db2inst1", "Sqltoy@2026",
					"db2", "" },
			{ "dm", "dm.jdbc.driver.DmDriver", "jdbc:dm://localhost:5236?schema=SYSDBA", "SYSDBA", "Sqltoy@2026",
					"dm", "" },
			{ "hana", "com.sap.db.jdbc.Driver", "jdbc:sap://localhost:39041/?databaseName=HXE", "SYSTEM",
					"Sqltoy2026", "hana", "" },
			{ "clickhouse", "com.clickhouse.jdbc.ClickHouseDriver",
					"jdbc:clickhouse://localhost:8123/default?user=sqltoy&password=Sqltoy@2026", null, null,
					"clickhouse", "" },
			{ "oceanbase", "com.oceanbase.jdbc.Driver", "jdbc:oceanbase://localhost:2881/test", "root@test",
					"Sqltoy2026", "mysql", "" },
			{ "oracle", "oracle.jdbc.OracleDriver", "jdbc:oracle:thin:@//localhost:1521/FREEPDB1",
					"system", "Sqltoy@2026", "oracle", "" },
			// update 2026-10-11 瀚高(PG12内核+oracle兼容层,社区镜像xuxuclassmate/highgo,
			// 宿主5867→容器5866,highgo/Sqltoy@2026;vanilla pg驱动+显式dialect=highgo)
			{ "highgo", "org.postgresql.Driver", "jdbc:postgresql://localhost:5867/highgo", "highgo",
					"Sqltoy@2026", "highgo", "" } };

	@Test
	public void allFunctionsSweep() throws Exception {
		FunctionUtils.setFunctionConverts(Arrays.asList("default"));
		List<String> failures = new ArrayList<String>();
		int executed = 0;
		for (String[] probe : PROBES) {
			executed += runOnEnv(probe[0], probe[1], probe[2], probe[3], probe[4], probe[5], failures);
		}
		try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:allsweep;DB_CLOSE_DELAY=-1", "sa", "")) {
			try (Statement st = conn.createStatement()) {
				createTable("h2", st, conn);
			}
			executed += runBattery("h2", conn, "h2", " from (select 1) x", failures);
		}
		try (Connection conn = DriverManager.getConnection("jdbc:sqlite::memory:"); Statement st = conn.createStatement()) {
			st.execute("create table sqltoy_all_t (id int primary key, name varchar(50), score double, "
					+ "create_time bigint, remark varchar(50))");
			// 生产绑定形态:毫秒Long
			long ms = java.sql.Timestamp.valueOf("2026-03-20 10:30:00").getTime();
			st.execute("insert into sqltoy_all_t values (1,'admin',88.5," + ms + ",null)");
			st.execute("insert into sqltoy_all_t values (2,'user',72.0," + ms + ",'r2')");
			executed += runBattery("sqlite", conn, "sqlite", " from (select 1) x", failures);
		}
		assertTrue(failures.isEmpty(),
				"全函数总扫失败清单(执行" + executed + "例): " + failures.subList(0, Math.min(20, failures.size())));
	}

	private int runOnEnv(String label, String drv, String url, String user, String pass, String dialect,
			List<String> failures) throws Exception {
		Class.forName(drv);
		try (Connection conn = (user == null || user.isEmpty()) && pass == null ? DriverManager.getConnection(url)
				: DriverManager.getConnection(url, user, pass); Statement st = conn.createStatement()) {
			try {
				st.execute("drop table sqltoy_all_t");
			} catch (Exception e) {
				// ignore
			}
			createTable(dialect, st, conn);
			String dual;
			switch (label) {
			case "oracle":
				dual = " from dual";
				break;
			case "db2":
				dual = " from sysibm.sysdummy1";
				break;
			case "hana":
				dual = " from dummy";
				break;
			case "sqlserver":
				dual = " from (select 1 as one) x";
				break;
			default:
				dual = " from (select 1) x";
			}
			int n = runBattery(label, conn, dialect, dual, failures);
			try {
				st.execute("drop table sqltoy_all_t");
			} catch (Exception e) {
				// ignore
			}
			return n;
		} catch (Throwable e) {
			failures.add(label + " 环境异常: " + e);
			return 0;
		}
	}

	private void createTable(String dialect, Statement st, Connection conn) throws Exception {
		if ("clickhouse".equals(dialect)) {
			st.execute("create table sqltoy_all_t (id Int32, name String, create_time Nullable(DateTime)) "
					+ "engine=MergeTree() order by id");
		} else if ("doris".equals(dialect) || "starrocks".equals(dialect)) {
			st.execute("create table sqltoy_all_t (id int, name varchar(50), create_time datetime)"
					+ " unique key(id) distributed by hash(id) buckets 1");
		} else {
			String idCol = "db2".equals(dialect) ? "id int not null primary key" : "id int primary key";
			String tsCol = "sqlserver".equals(dialect) ? "datetime2" : "timestamp";
			String strCol = "varchar(50)";
			// 瀚高name为保留字(真库实测语法错),列名加引号(用例SQL引用name的已按标签跳过)
			String nameCol = "highgo".equals(dialect) ? "\"name\"" : "name";
			st.execute("create table sqltoy_all_t (" + idCol + ", " + nameCol + " " + strCol + ", create_time " + tsCol
					+ ", remark " + strCol + ")");
		}
		insertRows(dialect, conn);
	}

	private void insertRows(String dialect, Connection conn) throws Exception {
		if ("clickhouse".equals(dialect)) {
			try (Statement st = conn.createStatement()) {
				st.execute("insert into sqltoy_all_t values (1,'admin','2026-03-20 10:30:00')");
				st.execute("insert into sqltoy_all_t values (2,'user','2026-03-20 10:30:00')");
			}
			return;
		}
		boolean threeCol = "doris".equals(dialect) || "starrocks".equals(dialect);
		try (PreparedStatement pst = conn
				.prepareStatement("insert into sqltoy_all_t values (" + (threeCol ? "?,?,?" : "?,?,?,?") + ")")) {
			String[][] rows = { { "1", "admin", "2026-03-20 10:30:00", null },
					{ "2", "user", "2026-03-20 10:30:00", "r2" } };
			for (String[] r : rows) {
				pst.setInt(1, Integer.parseInt(r[0]));
				pst.setString(2, r[1]);
				pst.setTimestamp(3, java.sql.Timestamp.valueOf(r[2]));
				if (!threeCol) {
					if (r[3] == null) {
						pst.setNull(4, java.sql.Types.VARCHAR);
					} else {
						pst.setString(4, r[3]);
					}
				}
				pst.executeUpdate();
			}
		}
	}

	/** {说明, 源SQL模板({D}=dual占位), 断言种类, 期望, 跳过标签CSV} */
	private String[][] battery() {
		return new String[][] {
				// -------- 字符串族 --------
				{ "substr三参", "select substr('admin',2,3){D}", "EQ", "dmi", "" },
				{ "substr负起点", "select substr('admin',-2){D}", "EQ", "in", "" },
				{ "trim空格", "select trim('  admin  '){D}", "EQ", "admin", "" },
				{ "trim修饰符字符集", "select trim(both 'x' from 'xxaxx'){D}", "EQ", "a", "" },
				{ "instr", "select instr('admin','dmi'){D}", "INT", "2", "" },
				{ "strpos源", "select strpos('admin','dmi'){D}", "INT", "2", "" },
				{ "locate源", "select locate('dmi','admin'){D}", "INT", "2", "" },
				{ "concat三参", "select concat('a','-','b'){D}", "EQ", "a-b", "" },
				{ "concat_ws", "select concat_ws('-','a','b'){D}", "EQ", "a-b", "" },
				{ "lengthb字节", "select lengthb('admin'){D}", "INT", "5", "" },
				{ "char_length字符", "select char_length('admin'){D}", "INT", "5", "" },
				// -------- 判空/条件族 --------
				{ "nvl", "select nvl(null,'x'){D}", "EQP", "x", "" },
				{ "nvl2", "select nvl2(null,'y','n'){D}", "EQ", "n", "" },
				{ "isnull单参", "select isnull(null){D}", "INT", "1", "" },
				{ "iif", "select iif(1=1,'y','n'){D}", "EQ", "y", "" },
				{ "decode", "select decode(1,1,'a','b'){D}", "EQ", "a", "" },
				// -------- 数值/日期标量族 --------
				{ "to_number", "select to_number('123.5'){D}", "PREFIX", "123.5", "" },
				// update 2026-10-12 uuid三级适配策略落地:og系合成v4/32hex、db2归一
				// generate_uuid、g8原生透传(目录级实证),og/og7/db2解除跳过全走真库;
				// vastbase亦解除(真库实证:原生sys_guid()报do not support,经适配转uuid()
				// 返回36位标准形态,转换是必需路径,必须覆盖);oracle保留跳过(原生sys_guid
				// 返回RAW byte[16],透传即oracle用户本意,getString得乱码形态属驱动行为)
				{ "uuid", "select uuid(){D}", "UUID", "", "" },
				{ "sys_guid源", "select sys_guid(){D}", "UUID", "", "oracle" },
				{ "newid源", "select newid(){D}", "UUID", "", "" },
				{ "guid源", "select guid(){D}", "UUID", "", "" },
				{ "now族", "select now(){D}", "PREFIX", "20", "" },
				{ "current_date", "select current_date{D}", "TODAY", "", "" },
				// -------- 表基用例 --------
				{ "to_char列", "select to_char(create_time,'yyyy-MM-dd') from sqltoy_all_t where id=1", "EQ",
						"2026-03-20", "" },
				{ "date_format列", "select date_format(create_time,'%Y-%m-%d') from sqltoy_all_t where id=1", "EQ",
						"2026-03-20", "" },
				{ "to_date标量", "select to_date('2026-03-20','yyyy-MM-dd'){D}", "PREFIX", "2026-03-20", "" },
				{ "datediff三参列", "select datediff(day, create_time, create_time) from sqltoy_all_t where id=1",
						"INT", "0", "" },
				{ "find_in_set计数", "select count(*) from sqltoy_all_t where find_in_set('user','admin,user')>0",
						"INT", "2", "" },
				// update 2026-10-12 乌龙排查(逐跳过组合真库直接执行适配产物定界):
				// ch解除跳过(arrayStringConcat(groupArray)实测'admin-user')
				{ "group_concat", "select group_concat(name separator '-') from sqltoy_all_t", "EQ2", "admin-user,user-admin",
						"" },
				// ch保留(listagg拼写无转换分支,透传ch语法错实证)
				{ "listagg排序", "select listagg(name,'-') within group (order by id desc) from sqltoy_all_t", "EQ",
						"user-admin", "clickhouse" },
				{ "year", "select year(create_time) from sqltoy_all_t where id=1", "INTY", "2026", "" },
				{ "month", "select month(create_time) from sqltoy_all_t where id=1", "INT", "3", "" },
				{ "day", "select day(create_time) from sqltoy_all_t where id=1", "INT", "20", "" },
				{ "hour", "select hour(create_time) from sqltoy_all_t where id=1", "INT", "10", "" },
				{ "last_day", "select last_day(create_time) from sqltoy_all_t where id=1", "PREFIX", "2026-03-31",
						"" },
				// sqlite跳过族(乌龙排查证据):无add_months/date_add/date_sub/adddate函数,
				// 适配器对sqlite响亮不转换(实测透传均报no such function);原生date(x,'+n unit')
				// 可用但月份不钳制月末(实测2026-01-31+1月=2026-03-03,与mysql/oracle语义相悖)
				// 且sqltoy对sqlite的create_time常为毫秒Long绑定(date()按儒略日数解析会静默
				// 错值),故不转换;lpad/rpad同理无函数不转换
				{ "add_months", "select add_months(create_time,2) from sqltoy_all_t where id=1", "PREFIX",
						"2026-05-20", "sqlite" },
				{ "date_add月", "select date_add(create_time, INTERVAL 1 MONTH) from sqltoy_all_t where id=1",
						"PREFIX", "2026-04-20", "sqlite" },
				{ "dateadd日", "select dateadd(day,3,create_time) from sqltoy_all_t where id=1", "PREFIX",
						"2026-03-23", "sqlite" },
				{ "date_sub日", "select date_sub(create_time, INTERVAL 3 DAY) from sqltoy_all_t where id=1", "PREFIX",
						"2026-03-17", "sqlite" },
				{ "adddate", "select adddate(create_time, 5) from sqltoy_all_t where id=1", "PREFIX", "2026-03-25",
						"sqlite" },
				{ "concat列", "select concat(name,'-','x') from sqltoy_all_t where id=1", "EQ", "admin-x", "" },
				// update 2026-10-11 A方案:left/right、lpad、quarter、EXTRACT源、日期截断族
				{ "left", "select left('admin',2){D}", "EQ", "ad", "" },
				{ "right", "select right('admin',2){D}", "EQ", "in", "" },
				{ "lpad", "select lpad('7',3,'0'){D}", "EQ", "007", "sqlite" },
				{ "quarter列", "select quarter(create_time) from sqltoy_all_t where id=1", "INT", "1", "" },
				{ "extract源列", "select extract(year from create_time) from sqltoy_all_t where id=1", "INT", "2026", "" },
				{ "date_part源列", "select date_part('year', create_time) from sqltoy_all_t where id=1", "INT", "2026", "" },
				{ "trunc日列(2参DD)", "select trunc(create_time,'DD') from sqltoy_all_t where id=1", "PREFIX", "2026-03-20", "" },
				{ "trunc月列", "select trunc(create_time,'MM') from sqltoy_all_t where id=1", "PREFIX", "2026-03-01", "" },
				{ "trunc季列", "select trunc(create_time,'Q') from sqltoy_all_t where id=1", "PREFIX", "2026-01-01", "" },
				{ "date_trunc月列", "select date_trunc('month', create_time) from sqltoy_all_t where id=1", "PREFIX", "2026-03-01", "" },
				{ "trunc周列", "select trunc(create_time,'IW') from sqltoy_all_t where id=1", "PREFIX", "2026-03-16", "" },
				{ "mod取模", "select mod(7,3){D}", "INT", "1", "" },
				// -------- 正式版补充:日期函数深度边界 --------
				{ "datediff跨年月", "select datediff(month,'2025-11-15','2026-02-20') from sqltoy_all_t where id=1",
						"INT", "3", "" },
				{ "datediff年", "select datediff(year,'2024-03-20','2026-03-20') from sqltoy_all_t where id=1",
						"INT", "2", "" },
				{ "datediff周", "select datediff(week,'2026-03-02','2026-03-23') from sqltoy_all_t where id=1",
						"INT", "3", "" },
				{ "datediff负值", "select datediff(day,'2026-03-25','2026-03-20') from sqltoy_all_t where id=1",
						"INT", "-5", "" },
				{ "to_char时分秒", "select to_char(create_time,'yyyy-MM-dd hh24:mi:ss') from sqltoy_all_t where id=1",
						"EQ", "2026-03-20 10:30:00", "" },
				{ "date_format时分秒",
						"select date_format(create_time,'%Y-%m-%d %H:%i:%s') from sqltoy_all_t where id=1", "EQ",
						"2026-03-20 10:30:00", "" },
				{ "extract季度", "select extract(quarter from create_time) from sqltoy_all_t where id=1", "INT", "1",
						"" },
				// update 2026-10-12 乌龙排查解除跳过:g8的rpad/octet_length/extract小时/
				// dateadd月负/dateadd周、sqlserver的rpad——适配产物真库直接执行全部OK
				// (rpad@sqlserver=LEFT(LEFT+REPLICATE)→xabab;rpad@g8原生直读;HOUR()@g8原生;
				// lpad/rpad跳过(实测多字符填充只取首字符:lpad('x',5,'ab')=aaaax非ababx)
				{ "extract小时", "select extract(hour from create_time) from sqltoy_all_t where id=1", "INT", "10",
						"" },
				{ "dateadd月负", "select dateadd(month,-2,create_time) from sqltoy_all_t where id=1", "PREFIX",
						"2026-01-20", "sqlite" },
				{ "dateadd周", "select dateadd(week,1,create_time) from sqltoy_all_t where id=1", "PREFIX",
						"2026-03-27", "sqlite" },
				// -------- 正式版补充:字符串函数深度边界 --------
				{ "substr首", "select substr('admin',1,2){D}", "EQ", "ad", "" },
				{ "substr超长", "select substr('ab',1,10){D}", "EQ", "ab", "" },
				{ "instr未找到", "select instr('admin','zz')  {D}", "INT", "0", "" },
				// h2跳过证据(乌龙排查实测):多字符填充只取首字符,lpad('x',5,'ab')='aaaax'
				// 而非ababx、rpad='xaaaa'而非xabab——非适配器可修复的引擎语义差异
				{ "lpad长填充", "select lpad('x',5,'ab'){D}", "EQ", "ababx", "sqlite,h2" },
				{ "rpad", "select rpad('x',5,'ab'){D}", "EQ", "xabab", "sqlite,h2" },
				// -------- 正式版补充:多拼写源 --------
				{ "curdate源", "select curdate(){D}", "TODAY", "", "" },
				{ "character_length源", "select character_length('admin'){D}", "INT", "5", "" },
				{ "octet_length源", "select octet_length('admin'){D}", "INT", "5", "" },
		};
	}

	private int runBattery(String label, Connection conn, String dialect, String dual, List<String> failures) {
		int n = 0;
		for (String[] c : battery()) {
			if (c[4].contains(label)) {
				continue;
			}
			String sql = FunctionUtils.getDialectSql(c[1].replace("{D}", dual), dialect);
			try (PreparedStatement pst = conn.prepareStatement(sql); ResultSet rs = pst.executeQuery()) {
				rs.next();
				String actual = String.valueOf(rs.getObject(1));
				if (!judge(c[2], c[3], actual)) {
					failures.add(label + " " + c[0] + " 期望[" + c[3] + "] 实际[" + actual + "] sql=" + sql);
				}
				n++;
			} catch (Exception e) {
				failures.add(label + " " + c[0] + " 异常: " + e + " sql=" + sql);
			}
		}
		return n;
	}

	private boolean judge(String kind, String expect, String actual) {
		if (actual == null) {
			return "NONNULL".equals(kind) ? false : false;
		}
		switch (kind) {
		case "EQ":
			return actual.equals(expect);
		case "EQ2": // 逗号分隔候选集,任一命中(行序不保证的聚合形态)
			return java.util.Arrays.asList(expect.split(",")).contains(actual);
		case "EQP":
			// update 2026-10-12 等值但容忍尾部空格填充(g8的nvl等char类型返回按char(255)
			// 定长填充,尾部空格为char类型固有语义非错值;去尾空白后严格等值)
			return actual.replaceFirst("\\s+$", "").equals(expect);
		case "PREFIX":
			return actual.startsWith(expect);
		case "INT": {
			// update 2026-10-12 严格化:原^\d+前缀匹配被interval文本("0 00:00:00")等
			// 形态骗过(verify轮datediff假绿的根因),改全串数值等值——trim后须为纯数值
			// 文本(容许负号与驱动返回的".0"小数尾),且与期望数值相等;interval/带时间
			// 后缀/空串均判失败
			String t = actual.trim();
			if (!t.matches("-?\\d+(\\.\\d+)?")) {
				return false;
			}
			try {
				return Double.parseDouble(t) == Double.parseDouble(expect.trim());
			} catch (NumberFormatException e) {
				return false;
			}
		}
		case "INTY": {
			// 整数严格等值,或mysql驱动物化YEAR类型为java.sql.Date的"2026-01-01"形态
			// (Connector/J对YEAR列的既定行为,前缀年份即语义正确)
			return judge("INT", expect, actual)
					|| actual.trim().matches("^" + java.util.regex.Pattern.quote(expect.trim()) + "-\\d{2}-\\d{2}.*");
		}
		case "TODAY": {
			// 容器UTC时区跨午夜窗口：JVM本地与UTC前一天均可接受
			String localToday = java.time.LocalDate.now().toString();
			String utcToday = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString();
			return actual.startsWith(localToday) || actual.startsWith(utcToday);
		}
		case "UUID":
			boolean dashed = actual.length() == 36 && actual.charAt(8) == '-' && actual.charAt(13) == '-';
			boolean hex32 = actual.length() == 32 && actual.matches("(?i)[0-9a-f]{32}");
			return dashed || hex32;
		default:
			return actual != null;
		}
	}
}
