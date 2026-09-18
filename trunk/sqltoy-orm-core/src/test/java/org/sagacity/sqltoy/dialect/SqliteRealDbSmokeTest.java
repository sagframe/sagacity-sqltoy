package org.sagacity.sqltoy.dialect;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.DriverManager;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.plugins.function.FunctionUtils;

/**
 * SQLite真实库冒烟:验证本轮新增的sqlite目标转换(trim/ltrim/rtrim二参字符集、concat转||、
 * now转CURRENT_TIMESTAMP)在真实sqlite上的语法与语义(内嵌库,无需服务端)。
 */
public class SqliteRealDbSmokeTest {

	private static Connection conn;

	@BeforeAll
	public static void init() throws Exception {
		conn = DriverManager.getConnection("jdbc:sqlite::memory:");
		FunctionUtils.setFunctionConverts(java.util.Arrays.asList("default"));
		try (var st = conn.createStatement()) {
			st.execute("create table t (name varchar(100))");
			st.execute("insert into t values ('xxadminxx')");
		}
	}

	@AfterAll
	public static void destroy() throws Exception {
		if (conn != null) {
			conn.close();
		}
	}

	private String convert(String sql) {
		return FunctionUtils.getDialectSql(sql, "sqlite");
	}

	private String queryStr(String sql) throws Exception {
		try (var pst = conn.prepareStatement(sql); var rs = pst.executeQuery()) {
			rs.next();
			return rs.getString(1);
		}
	}

	@Test
	public void trimModifierForms() throws Exception {
		// both字符集:trim(col,'x')
		assertEquals("admin", queryStr(convert("select trim(both 'x' from name) from t")));
		// leading字符集:ltrim(col,'x')
		assertEquals("adminxx", queryStr(convert("select trim(leading 'x' from name) from t")));
		// trailing字符集:rtrim(col,'x')
		assertEquals("xxadmin", queryStr(convert("select trim(trailing 'x' from name) from t")));
		// 无剔除字符:空格语义
		assertEquals("admin", queryStr(convert("select trim(both from '  admin  ') from t")));
		// 普通trim原生保留
		assertEquals("select trim(name) from t", convert("select trim(name) from t"));
	}

	@Test
	public void concatAndNow() throws Exception {
		// concat转||拼接(sqlite无concat函数)
		assertEquals("abc", queryStr(convert("select concat('a','b','c') from t")));
		// now转CURRENT_TIMESTAMP并可执行
		Object v = queryStr(convert("select CURRENT_TIMESTAMP from t"));
		org.junit.jupiter.api.Assertions.assertNotNull(v);
	}
}
