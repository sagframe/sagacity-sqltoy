package org.sagacity.sqltoy.plugins.function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * @description 函数转换缺陷回归:格式模型(Oracle mm/HH、Java HH:mm)、posstr参数序、
 *              position解析、trim(from)、string_agg排序、SQL Server周锚定、current_timestamp、
 *              串尾关键字、substr单参健壮性。SQL文本级用例无需数据库;H2/SQLite子集做真执行断言。
 * @author zhongxuchen
 */
@DisplayName("函数转换缺陷回归测试")
public class FunctionDefectRegressionTest {

	@BeforeAll
	public static void init() {
		FunctionUtils.setFunctionConverts(Arrays.asList("default"));
	}

	private static String c(String sql, String dialect) {
		return FunctionUtils.getDialectSql(sql, dialect);
	}

	private static String n(String sql) {
		return sql == null ? null : sql.replaceAll("\\s+", " ").trim();
	}

	@Test
	@DisplayName("Oracle小写mm(月)不得被当作分钟")
	public void oracleLowercaseMonth() {
		assertEquals("select date_format(t,'%Y-%m-%d') from t", n(c("select to_char(t,'yyyy-mm-dd') from t", "mysql")));
		assertEquals("select date_format(toDateTime(t),'%Y-%m-%d') from t",
				n(c("select to_char(t,'yyyy-mm-dd') from t", "clickhouse")));
		assertEquals("select strftime('%Y-%m-%d',datetime(t/1000,'unixepoch','localtime')) from t",
				n(c("select to_char(t,'yyyy-mm-dd') from t", "sqlite")));
		assertEquals("select date_format(t,'%Y-%m-%d %H:%i:%s') from t",
				n(c("select to_char(t,'yyyy-mm-dd hh24:mi:ss') from t", "mysql")));
		assertEquals("select STR_TO_DATE(t,'%Y-%m-%d') from t", n(c("select to_date(t,'yyyy-mm-dd') from t", "mysql")));
		assertEquals("select parsedatetime(t,'yyyy-MM-dd') from t", n(c("select to_date(t,'yyyy-mm-dd') from t", "h2")));
	}

	@Test
	@DisplayName("HH默认24小时制,hh为12小时制,HH24恒为24小时")
	public void hhTwelveHour() {
		// 裸HH按框架约定=24小时(与DateFormat/Oracle/PG分支一致)
		assertEquals("select date_format(t,'%H:%i') from t", n(c("select to_char(t,'HH:MI') from t", "mysql")));
		assertEquals("select strftime('%H:%M',datetime(t/1000,'unixepoch','localtime')) from t",
				n(c("select to_char(t,'HH:MI') from t", "sqlite")));
		assertEquals("select STR_TO_DATE(t,'%H:%i') from t", n(c("select to_date(t,'HH:MI') from t", "mysql")));
		// 小写hh为12小时制
		assertEquals("select date_format(t,'%h:%i') from t", n(c("select to_char(t,'hh:MI') from t", "mysql")));
		assertEquals("select strftime('%I:%M',datetime(t/1000,'unixepoch','localtime')) from t",
				n(c("select to_char(t,'hh:MI') from t", "sqlite")));
		assertEquals("select STR_TO_DATE(t,'%h:%i') from t", n(c("select to_date(t,'hh:MI') from t", "mysql")));
		// SQLServer:.NET的HH=24小时,hh=12小时,裸HH不再降级为12小时
		assertEquals("select FORMAT(t,'HH:mm') from t", n(c("select to_char(t,'HH:MI') from t", "sqlserver")));
		assertEquals("select FORMAT(t,'hh:mm') from t", n(c("select to_char(t,'hh:MI') from t", "sqlserver")));
		// H2的to_char原生HH为12小时,裸HH须归一为HH24
		assertEquals("select to_char(t,'HH24:MI') from t", n(c("select to_char(t,'HH:MI') from t", "h2")));
		assertEquals("select to_char(t,'hh:MI') from t", n(c("select to_char(t,'hh:MI') from t", "h2")));
		// H2 PARSEDATETIME取Java令牌:HH=24小时,hh=12小时
		assertEquals("select parsedatetime(t,'HH:mm') from t", n(c("select to_date(t,'HH:MI') from t", "h2")));
		assertEquals("select parsedatetime(t,'hh:mm') from t", n(c("select to_date(t,'hh:MI') from t", "h2")));
	}

	@Test
	@DisplayName("Java风格HH:mm到SQLServer/SQLite/DB2应保持24小时+分钟")
	public void javaTimePattern() {
		assertEquals("select FORMAT(t,'yyyy-MM-dd HH:mm:ss') from t",
				n(c("select date_format(t,'yyyy-MM-dd HH:mm:ss') from t", "sqlserver")));
		assertEquals("select strftime('%Y-%m-%d %H:%M:%S',datetime(t/1000,'unixepoch','localtime')) from t",
				n(c("select date_format(t,'yyyy-MM-dd HH:mm:ss') from t", "sqlite")));
		assertEquals("select VARCHAR_FORMAT(t,'yyyy-MM-dd HH24:MI:SS') from t",
				n(c("select date_format(t,'yyyy-MM-dd HH:mm:ss') from t", "db2")));
		assertEquals("select FORMAT(t,'yyyy-MM-dd HH:mm:ss') from t",
				n(c("select to_char(t,'yyyy-MM-dd HH:mm:ss') from t", "sqlserver")));
		// update 2026-10-19 sqlserver 2025真库实测:FORMAT不接受varchar首参,字面量须CAST定型
		assertEquals("select FORMAT(CAST('2026-03-20 14:30:45' AS datetime),'yyyy-MM-dd HH:mm:ss') from t",
				n(c("select date_format('2026-03-20 14:30:45','yyyy-MM-dd HH:mm:ss') from t", "sqlserver")));
	}

	@Test
	@DisplayName("OceanBase的datediff按oracle系生成(与OceanBaseDialect一致)")
	public void oceanbaseDateDiff() {
		String sql = n(c("select datediff(day,'2026-03-01','2026-03-20') from dual", "oceanbase"));
		assertEquals("select (TRUNC(to_date('2026-03-20','yyyy-MM-dd')) - TRUNC(to_date('2026-03-01','yyyy-MM-dd'))) from dual",
				sql);
	}

	@Test
	@DisplayName("DB2 posstr为haystack-first,跨库不得反转参数")
	public void posstrArgumentOrder() {
		assertEquals("select instr(name,'-') from t", n(c("select posstr(name,'-') from t", "oracle")));
		assertEquals("select charindex('-',name) from t", n(c("select posstr(name,'-') from t", "sqlserver")));
		assertEquals("select position('-' in name) from t", n(c("select posstr(name,'-') from t", "postgresql")));
		assertEquals("select posstr(name,'-') from t", n(c("select posstr(name,'-') from t", "db2")));
		assertEquals("select position(name,'-') from t", n(c("select posstr(name,'-') from t", "clickhouse")));
	}

	@Test
	@DisplayName("position(...in...)只按顶层关键字切分")
	public void positionLiteralWithIn() {
		assertEquals("select charindex('a','x in y') from t",
				n(c("select position('a' in 'x in y') from t", "sqlserver")));
		assertEquals("select instr('x in y','a') from t", n(c("select position('a' in 'x in y') from t", "oracle")));
	}

	@Test
	@DisplayName("标准trim(from col)在各目标库正确改写")
	public void trimFromForm() {
		assertEquals("select trim(both ' ' from name) from t", n(c("select trim(from name) from t", "h2")));
		assertEquals("select rtrim(ltrim(name)) from t", n(c("select trim(from name) from t", "sqlserver")));
		assertEquals("select trim(name) from t", n(c("select trim(from name) from t", "sqlite")));
		// update 2026-10-19 真库实测:mysql/oracle不接受省略剔除字符的trim(from x)
		// (mysql 1064/oracle ORA-30000),须改写为等价单参trim
		assertEquals("select trim(name) from t", n(c("select trim(from name) from t", "mysql")));
		assertEquals("select trim(name) from t", n(c("select trim(from name) from t", "oracle")));
		assertEquals("select ltrim(name) from t", n(c("select trim(leading from name) from t", "mysql")));
	}

	@Test
	@DisplayName("string_agg排序子句在SQLServer/PG间正确转换")
	public void stringAggOrdering() {
		assertEquals("select string_agg(name,'-') within group (order by id) from t",
				n(c("select string_agg(name,'-' order by id) from t", "sqlserver")));
		assertEquals("select string_agg(name,'-' order by id) from t",
				n(c("select string_agg(name,'-') within group (order by id) from t", "postgresql")));
		// 多列数值拼接不得用+做加法;CONCAT+null守卫保持mysql concat语义
		String sqlServerMulti = n(c("select group_concat(a,b) from t", "sqlserver"));
		assertEquals("select string_agg(case when a is null or b is null then null else concat(a,b) end,',') from t",
				sqlServerMulti);
	}

	@Test
	@DisplayName("SQL Server周截断按ISO周一锚定,不受DATEFIRST影响")
	public void sqlServerWeekTruncation() {
		assertEquals("select DATEADD(day,-((DATEDIFF(day,0,create_time))%7),CONVERT(date,create_time)) from t",
				n(c("select trunc(create_time,'IW') from t", "sqlserver")));
	}

	@Test
	@DisplayName("current_timestamp到Oracle保留TIMESTAMP WITH TIME ZONE语义")
	public void currentTimestampOracle() {
		assertEquals("select CURRENT_TIMESTAMP from t", n(c("select current_timestamp from t", "oracle")));
		assertEquals("select CURRENT_TIMESTAMP(6) from t", n(c("select current_timestamp(6) from t", "oracle")));
	}

	@Test
	@DisplayName("SQL末尾的裸关键字也必须转换")
	public void trailingKeyword() {
		assertEquals("select now()", n(c("select sysdate", "postgresql")));
		assertEquals("select CAST(GETDATE() AS date)", n(c("select current_date", "sqlserver")));
	}

	@Test
	@DisplayName("非法单参substr不得抛ArrayIndexOutOfBoundsException")
	public void malformedSubstrKept() {
		assertEquals("select substr(name) from t", n(c("select substr(name) from t", "postgresql")));
	}

	@Test
	@DisplayName("H2/SQLite可执行子集:改写产物真实执行结果正确")
	public void executableOnEmbeddedDatabases() throws Exception {
		try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:funcdefect;DB_CLOSE_DELAY=-1", "sa", "");
				Statement st = conn.createStatement()) {
			assertEquals("x", query(st, c("select trim(from '  x  ') from (select 1) x", "h2")));
			assertTrue(query(st, c("select to_date('2026-03-20','yyyy-mm-dd') from (select 1) x", "h2"))
					.startsWith("2026-03-20"));
			// HH默认24小时、hh为12小时的真执行锁定
			assertEquals("14:30",
					query(st, c("select to_char(timestamp '2026-03-20 14:30:45','HH:MI') from (select 1) x", "h2")));
			assertEquals("02:30",
					query(st, c("select to_char(timestamp '2026-03-20 14:30:45','hh:MI') from (select 1) x", "h2")));
		}
		try (Connection conn = DriverManager.getConnection("jdbc:sqlite::memory:");
				Statement st = conn.createStatement()) {
			assertEquals("2026-03-20", query(st, c("select to_char('2026-03-20','yyyy-mm-dd') from (select 1) x", "sqlite")));
			assertEquals("2026-03-20 10:30:45",
					query(st, c("select date_format('2026-03-20 10:30:45','yyyy-MM-dd HH:mm:ss') from (select 1) x", "sqlite")));
			assertEquals("x", query(st, c("select trim(from '  x  ') from (select 1) x", "sqlite")));
			assertEquals("14:30",
					query(st, c("select to_char('2026-03-20 14:30:45','HH:MI') from (select 1) x", "sqlite")));
			assertEquals("02:30",
					query(st, c("select to_char('2026-03-20 14:30:45','hh:MI') from (select 1) x", "sqlite")));
		}
	}

	private static String query(Statement st, String sql) throws Exception {
		try (ResultSet rs = st.executeQuery(sql)) {
			rs.next();
			return String.valueOf(rs.getObject(1));
		}
	}
}
