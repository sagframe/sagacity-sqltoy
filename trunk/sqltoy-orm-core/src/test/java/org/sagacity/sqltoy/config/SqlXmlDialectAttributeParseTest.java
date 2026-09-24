package org.sagacity.sqltoy.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.plugins.function.FunctionUtils;

/**
 * 回归测试：&lt;sql dialect="xxx"&gt;声明本条sql按指定方言形态解析固化——
 * 加载时按声明方言做函数/保留字转换并固化解析标签;执行期当前库方言与声明一致时
 * 既有早退机制跳过函数替换,异方言走惰性转换;无属性/非法值/mql保持既有行为
 */
public class SqlXmlDialectAttributeParseTest {

	@BeforeAll
	public static void registerDefaultFunctions() {
		// 注册默认函数转换器(Nvl/DateFormat等),函数转换的前提
		FunctionUtils.setFunctionConverts(Arrays.asList("default"));
	}

	private SqlToyConfig parse(String xml) throws Exception {
		// 全局解析方言固定为mysql,模拟迁移期配置
		return SqlXMLConfigParse.parseSagment(xml, "UTF-8", "mysql");
	}

	/**
	 * 属性生效+按声明方言固化:nvl按kingbase转换为coalesce,解析标签=kingbase
	 */
	@Test
	public void parsedByDeclaredDialect() throws Exception {
		SqlToyConfig config = parse(
				"<sql id=\"t1\" dialect=\"kingbase\"><value><![CDATA[select nvl(staff_name,'x') from sqltoy_staff_info]]></value></sql>");
		assertEquals("kingbase", config.getDialect());
		String sql = config.getSql(null);
		assertTrue(sql.contains("coalesce("), "nvl should be rendered to kingbase coalesce at load:" + sql);
		assertFalse(sql.contains("nvl("), "nvl should not remain after kingbase parse:" + sql);
	}

	/**
	 * 同方言执行早退+异方言惰性反向适配:date_format按kingbase固化为to_char,
	 * kingbase查询早退保持原形态,mysql查询惰性转回date_format(缓存后结果稳定)
	 */
	@Test
	public void sameDialectEarlyExitAndCrossDialectConvert() throws Exception {
		SqlToyConfig config = parse(
				"<sql id=\"t2\" dialect=\"kingbase\"><value><![CDATA[select date_format(create_time,'yyyy-MM-dd') from sqltoy_staff_info]]></value></sql>");
		assertEquals("kingbase", config.getDialect());
		String loadForm = config.getSql(null);
		assertTrue(loadForm.contains("to_char("), "date_format should be rendered to kingbase to_char:" + loadForm);
		// 当前库方言==声明方言:早退,函数替换不执行,与加载形态逐字一致
		String kingbaseSql = config.getSql("kingbase");
		assertEquals(loadForm, kingbaseSql, "same dialect should early-exit without conversion");
		// 异方言:mysql查询惰性转换回date_format
		String mysqlSql = config.getSql("mysql");
		assertTrue(mysqlSql.contains("date_format("), "mysql query should lazily convert to date_format:" + mysqlSql);
		assertFalse(mysqlSql.contains("to_char("), "mysql query should not keep to_char:" + mysqlSql);
		// 二次调用命中dialectSqlMap缓存,结果稳定
		assertEquals(mysqlSql, config.getSql("mysql"));
	}

	/**
	 * 无属性回归:按全局方言解析,标签=全局方言,nvl固化为mysql形态ifnull
	 */
	@Test
	public void noAttributeUsesGlobalDialect() throws Exception {
		SqlToyConfig config = parse(
				"<sql id=\"t3\"><value><![CDATA[select nvl(staff_name,'x') from sqltoy_staff_info]]></value></sql>");
		assertEquals("mysql", config.getDialect());
		String sql = config.getSql(null);
		assertTrue(sql.contains("ifnull("), "nvl should be rendered to mysql ifnull by global dialect:" + sql);
	}

	/**
	 * 非法方言值:warn提示并按全局方言解析(表现为属性不存在)
	 */
	@Test
	public void invalidDialectFallsBackToGlobal() throws Exception {
		SqlToyConfig config = parse(
				"<sql id=\"t4\" dialect=\"notadb\"><value><![CDATA[select nvl(staff_name,'x') from sqltoy_staff_info]]></value></sql>");
		assertEquals("mysql", config.getDialect());
		assertTrue(config.getSql(null).contains("ifnull("), "invalid dialect should parse by global mysql form");
	}

	/**
	 * mql元素:即使写了dialect属性仍强制mongo(优先级:mql/eql强制>元素属性>全局)
	 */
	@Test
	public void mqlForcesMongoDespiteAttribute() throws Exception {
		SqlToyConfig config = parse(
				"<mql id=\"t5\" dialect=\"kingbase\" collection=\"sqltoy_staff_info\" fields=\"staffName\"><![CDATA[{'status':1}]]></mql>");
		assertEquals("mongo", config.getDialect());
	}

	/**
	 * count-sql与主sql使用同一生效方言:按kingbase形态转换
	 */
	@Test
	public void countSqlUsesDeclaredDialect() throws Exception {
		SqlToyConfig config = parse(
				"<sql id=\"t6\" dialect=\"kingbase\"><value><![CDATA[select nvl(staff_name,'x') from sqltoy_staff_info]]></value>"
						+ "<count-sql><![CDATA[select count(1) from (select nvl(staff_name,'x') from sqltoy_staff_info) cnt]]></count-sql></sql>");
		String countSql = config.getCountSql(null);
		assertTrue(countSql.contains("coalesce("), "count-sql should be converted by declared dialect:" + countSql);
		assertFalse(countSql.contains("nvl("), "count-sql should not keep nvl:" + countSql);
	}
}
