package org.sagacity.sqltoy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlType;
import org.sagacity.sqltoy.model.QueryExecutor;
import org.sagacity.sqltoy.model.XMLBinding;

/**
 * 回归测试：动态xml绑定(XMLBinding)的dialect标签保持解析方言
 * base sql按全局方言解析渲染,getDialectSql依赖dialect标签判断早退;
 * 修复前标签被本次查询方言改写,全局方言形态的sql跳过适配直发当前库,且首个方言永远拿不到函数/保留字适配
 */
public class SqlToyContextXmlBindingDialectTest {

	/**
	 * 全局方言已配置:标签应保持解析方言(oracle),不被查询方言(mysql)改写
	 */
	@Test
	public void xmlBindingKeepsParseDialectLabel() {
		SqlToyContext ctx = new SqlToyContext();
		ctx.setDialect("oracle");
		String xml = "<sql id=\"dyn_m1_q1\"><![CDATA[select * from sqltoy_staff_info where staff_id=:staffId]]></sql>";
		QueryExecutor queryExecutor = new QueryExecutor(new XMLBinding(xml).id("dyn_m1_q1"));
		SqlToyConfig config = ctx.getSqlToyConfig(queryExecutor, SqlType.search, "mysql");
		// 修复前:config.getDialect()被改写成"mysql",oracle形态base直发mysql库(getDialectSql早退放行错误形态)
		assertEquals("oracle", config.getDialect(), "dialect label should stay the parse dialect, not the query dialect");
	}

	/**
	 * 全局方言未配置:标签保持null,getSql(查询方言)才会走函数/保留字适配分支(与静态xml sql行为一致)
	 */
	@Test
	public void xmlBindingNoGlobalDialectKeepsNullLabel() {
		SqlToyContext ctx = new SqlToyContext();
		String xml = "<sql id=\"dyn_m1_q2\"><![CDATA[select * from sqltoy_staff_info where staff_id=:staffId]]></sql>";
		QueryExecutor queryExecutor = new QueryExecutor(new XMLBinding(xml).id("dyn_m1_q2"));
		SqlToyConfig config = ctx.getSqlToyConfig(queryExecutor, SqlType.search, "mysql");
		assertNull(config.getDialect(), "no global dialect configured, label should stay null for lazy conversion");
	}
}
