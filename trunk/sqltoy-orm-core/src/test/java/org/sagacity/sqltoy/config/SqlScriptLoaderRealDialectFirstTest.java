package org.sagacity.sqltoy.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.SqlToyThreadDataHolder;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlType;
import org.sagacity.sqltoy.model.DBProfile;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * 回归测试：realDialectFirst开启后sqlId方言变体查找优先按连接探测的真实方言
 * (如OB按mysql方言配置而真实库为oceanbase),真实方言变体不存在时回退配置方言查找链
 */
public class SqlScriptLoaderRealDialectFirstTest {

	private SqlScriptLoader loader = new SqlScriptLoader();

	private void put(String id, String sql) throws Exception {
		loader.putSqlToyConfig(new SqlToyConfig(id, sql));
	}

	/** 探测库为realDialect/realDbType、配置覆盖为dialect/dbType的有效执行档案 */
	private DBProfile effective(String realDialect, int realDbType, String dialect, int dbType) {
		return new DBProfile("jdbc:mock:db", realDialect, realDbType, "mockdb", 1, null, null, false)
				.asEffective(dbType, dialect);
	}

	@AfterEach
	public void clear() {
		SqlToyThreadDataHolder.clearDBProfile();
	}

	/**
	 * 开关关闭(默认):线程档案存在也不参与,保持既有按配置方言查找
	 */
	@Test
	public void disabledByDefault() throws Exception {
		put("q1", "select 1 as common");
		put("q1_oracle", "select 1 as oracle_var");
		SqlToyThreadDataHolder.setDBProfile(effective("oracle", DBType.ORACLE, "mysql", DBType.MYSQL));
		assertEquals("select 1 as common", loader.getSqlConfig("q1", SqlType.search, "mysql", null, true).getSql());
	}

	/**
	 * 开关开启+真实方言变体存在:优先命中真实方言变体(优于配置方言变体)
	 */
	@Test
	public void preferRealDialectVariant() throws Exception {
		loader.setRealDialectFirst(true);
		put("q2", "select 2 as common");
		put("q2_mysql", "select 2 as mysql_var");
		put("q2_oceanbase", "select 2 as ob_var");
		SqlToyThreadDataHolder.setDBProfile(effective("oceanbase", DBType.OCEANBASE, "mysql", DBType.MYSQL));
		assertEquals("select 2 as ob_var", loader.getSqlConfig("q2", SqlType.search, "mysql", null, true).getSql());
	}

	/**
	 * 开关开启+真实方言变体不存在:回退原有配置方言查找链
	 */
	@Test
	public void fallbackWhenRealDialectVariantAbsent() throws Exception {
		loader.setRealDialectFirst(true);
		put("q3", "select 3 as common");
		put("q3_mysql", "select 3 as mysql_var");
		SqlToyThreadDataHolder.setDBProfile(effective("oceanbase", DBType.OCEANBASE, "mysql", DBType.MYSQL));
		assertEquals("select 3 as mysql_var", loader.getSqlConfig("q3", SqlType.search, "mysql", null, true).getSql());
	}

	/**
	 * 真实方言与配置方言一致(无配置覆盖场景两者同源):忽略,不触发真实方言重查
	 */
	@Test
	public void sameDialectIgnored() throws Exception {
		loader.setRealDialectFirst(true);
		put("q4", "select 4 as common");
		put("q4_mysql", "select 4 as mysql_var");
		SqlToyThreadDataHolder
				.setDBProfile(new DBProfile("jdbc:mock:db", "mysql", DBType.MYSQL, "mockdb", 1, null, null, false));
		assertEquals("select 4 as mysql_var", loader.getSqlConfig("q4", SqlType.search, "mysql", null, true).getSql());
	}

	/**
	 * 开关开启但线程无档案(顶层查询在连接外解析的场景):保持原有行为
	 */
	@Test
	public void noThreadProfileNoop() throws Exception {
		loader.setRealDialectFirst(true);
		put("q5", "select 5 as common");
		assertEquals("select 5 as common", loader.getSqlConfig("q5", SqlType.search, "mysql", null, true).getSql());
	}

	/**
	 * 查询管线统一预处理入口的重解析通道:连接档案在手时按真实方言取变体,
	 * 变体不存在返回原config引用(配置方言变体不降级为base),非sqlId形式原样返回
	 */
	@Test
	public void resolveRealDialectVariantByContext() throws Exception {
		SqlToyContext ctx = new SqlToyContext();
		ctx.setRealDialectFirst(true);
		ctx.getScriptLoader().putSqlToyConfig(new SqlToyConfig("q6", "select 6 as common"));
		ctx.getScriptLoader().putSqlToyConfig(new SqlToyConfig("q6_oceanbase", "select 6 as ob_var"));
		ctx.getScriptLoader().putSqlToyConfig(new SqlToyConfig("q7", "select 7 as common"));
		DBProfile profile = effective("oceanbase", DBType.OCEANBASE, "mysql", DBType.MYSQL);
		// 命中真实方言变体
		SqlToyConfig base = ctx.getScriptLoader().getSqlConfig("q6", SqlType.search, "mysql", null, true);
		SqlToyConfig variant = ctx.resolveRealDialectVariant("q6", base, profile);
		assertEquals("select 6 as ob_var", variant.getSql());
		// 命中变体打标记(函数替换方言跟随真实方言的前提),未命中不打
		assertTrue(variant.isRealDialectMatched(), "realDialect variant should be marked");
		assertFalse(base.isRealDialectMatched(), "base config should not be marked");
		// 变体不存在:返回原config(不降级为base)且不打标记
		SqlToyConfig common = ctx.getScriptLoader().getSqlConfig("q7", SqlType.search, "mysql", null, true);
		assertSame(common, ctx.resolveRealDialectVariant("q7", common, profile));
		assertFalse(common.isRealDialectMatched(), "fallback config should not be marked");
		// 非sqlId形式(硬编码sql):原样返回
		assertSame(common, ctx.resolveRealDialectVariant("select * from t", common, profile));
		// 真实方言与配置方言一致:原样返回
		DBProfile sameProfile = new DBProfile("jdbc:mock:db", "mysql", DBType.MYSQL, "mockdb", 1, null, null, false);
		assertSame(common, ctx.resolveRealDialectVariant("q6", common, sameProfile));
	}
}
