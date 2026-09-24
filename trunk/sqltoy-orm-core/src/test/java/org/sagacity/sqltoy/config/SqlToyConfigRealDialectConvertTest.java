package org.sagacity.sqltoy.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.SqlToyThreadDataHolder;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlType;
import org.sagacity.sqltoy.model.DBProfile;
import org.sagacity.sqltoy.plugins.function.FunctionUtils;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * 回归测试：realDialectFirst下sql片段的函数/保留字惰性转换按真实方言的适用范围——
 * 仅限经realDialectFirst变体匹配选中的sql(realDialectMatched标记),未命中变体回退base的
 * sql、开关关闭、真实方言与配置一致、线程无档案时均保持原有行为
 */
public class SqlToyConfigRealDialectConvertTest {

	@BeforeAll
	public static void registerDefaultFunctions() {
		// 注册默认函数转换器(Nvl等),函数转换的前提
		FunctionUtils.setFunctionConverts(Arrays.asList("default"));
	}

	@AfterAll
	public static void resetFlag() {
		SqlToyConstants.realDialectFirst = false;
	}

	@AfterEach
	public void clear() {
		SqlToyThreadDataHolder.clearDBProfile();
	}

	/** 探测库为realDialect/realDbType、配置覆盖为dialect/dbType的有效执行档案 */
	private DBProfile effective(String realDialect, int realDbType, String dialect, int dbType) {
		return new DBProfile("jdbc:mock:db", realDialect, realDbType, "mockdb", 1, null, null, false)
				.asEffective(dbType, dialect);
	}

	/**
	 * 范围约束:开关开启+线程档案真实方言kingbase,未打标记的base sql(标签=mysql)查询方言
	 * ==标签早退,mysql形态ifnull直发,不被顺带转换
	 */
	@Test
	public void baseSqlWithoutMarkNotConverted() throws Exception {
		SqlToyConstants.realDialectFirst = true;
		SqlToyConfig config = SqlConfigParseUtils.parseSqlToyConfig(
				"select ifnull(staff_name,'x') from sqltoy_staff_info", "mysql", SqlType.search);
		SqlToyThreadDataHolder.setDBProfile(effective("kingbase", DBType.KINGBASE, "mysql", DBType.MYSQL));
		String sql = config.getSql("mysql");
		assertTrue(sql.contains("ifnull("), "unmatched base sql should keep mysql form:" + sql);
		assertFalse(sql.contains("coalesce("), "unmatched base sql should not be converted:" + sql);
	}

	/**
	 * 迁移场景:经realDialectFirst变体匹配选中的sql(带标记)按mysql解析固化ifnull形态,
	 * 执行时线程档案真实方言kingbase参与转换,ifnull被改写为coalesce(PG系形态)
	 */
	@Test
	public void convertOnlyForRealDialectMatched() throws Exception {
		SqlToyConstants.realDialectFirst = true;
		SqlToyConfig config = SqlConfigParseUtils.parseSqlToyConfig(
				"select ifnull(staff_name,'x') from sqltoy_staff_info", "mysql", SqlType.search);
		config.markRealDialectMatched();
		SqlToyThreadDataHolder.setDBProfile(effective("kingbase", DBType.KINGBASE, "mysql", DBType.MYSQL));
		String sql = config.getSql("mysql");
		assertTrue(sql.contains("coalesce("), "matched sql should convert ifnull to coalesce:" + sql);
		assertFalse(sql.contains("ifnull("), "matched sql should not keep ifnull:" + sql);
	}

	/**
	 * 开关关闭:即使带标记也不换算(查询方言==解析标签早退,既有行为)
	 */
	@Test
	public void flagOffKeepConfiguredDialect() throws Exception {
		SqlToyConstants.realDialectFirst = false;
		SqlToyConfig config = SqlConfigParseUtils.parseSqlToyConfig(
				"select ifnull(staff_name,'x') from sqltoy_staff_info", "mysql", SqlType.search);
		config.markRealDialectMatched();
		SqlToyThreadDataHolder.setDBProfile(effective("kingbase", DBType.KINGBASE, "mysql", DBType.MYSQL));
		String sql = config.getSql("mysql");
		assertTrue(sql.contains("ifnull("), "flag off should keep ifnull unchanged:" + sql);
	}

	/**
	 * 真实方言与配置方言一致(无配置覆盖场景两者同源):即使带标记也不触发换算,保持早退
	 */
	@Test
	public void sameDialectNoConvert() throws Exception {
		SqlToyConstants.realDialectFirst = true;
		SqlToyConfig config = SqlConfigParseUtils.parseSqlToyConfig(
				"select ifnull(staff_name,'x') from sqltoy_staff_info", "mysql", SqlType.search);
		config.markRealDialectMatched();
		SqlToyThreadDataHolder
				.setDBProfile(new DBProfile("jdbc:mock:db", "mysql", DBType.MYSQL, "mockdb", 1, null, null, false));
		String sql = config.getSql("mysql");
		assertTrue(sql.contains("ifnull("), "same realDialect should keep ifnull unchanged:" + sql);
	}

	/**
	 * 线程无档案(连接外解析场景):带标记也保持传入方言的既有行为
	 * (查询方言≠解析标签时仍按传入方言转换)
	 */
	@Test
	public void noThreadProfileFollowsPassedDialect() throws Exception {
		SqlToyConstants.realDialectFirst = true;
		SqlToyConfig config = SqlConfigParseUtils.parseSqlToyConfig(
				"select ifnull(staff_name,'x') from sqltoy_staff_info", "oracle", SqlType.search);
		config.markRealDialectMatched();
		// 无档案不换算:kingbase≠标签oracle→按传入方言kingbase转换(既有行为)
		String sql = config.getSql("kingbase");
		assertTrue(sql.contains("coalesce("), "passed dialect kingbase should convert ifnull:" + sql);
	}
}
