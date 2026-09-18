package org.sagacity.sqltoy.dialect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Properties;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.model.DBProfile;
import org.sagacity.sqltoy.plugins.function.FunctionUtils;
import org.sagacity.sqltoy.utils.DataSourceUtils;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * StarRocks原生驱动(starrocks-connector-j,com.starrocks.jdbc.Driver)真库冒烟:
 * 验证DataSourceUtils对jdbc:starrocks://URL的方言识别(update 2026-9-17
 * URL_SCHEMA_DIALECT新增starrocks条目——原生驱动产品名依旧伪装MySQL,URL特征
 * 优先直命中starrocks方言,resolveDialect引擎探测仅对mysql方言触发故自动跳过),
 * 以及原生驱动连接下的基础查询与方言函数转换执行。
 * 连接配置从target/starrocks-probe.properties读取(不入库);文件不存在时全部用例跳过。
 * FE查询端口9030(MySQL协议),驱动URL形态:jdbc:starrocks://host:9030/db
 */
public class StarRocksRealDbSmokeTest {

	private static Connection conn;

	private static boolean available = false;

	private static String reportedProductName;

	@BeforeAll
	public static void init() throws Exception {
		File cfg = new File("target/starrocks-probe.properties");
		if (!cfg.exists()) {
			return;
		}
		Properties p = new Properties();
		try (InputStream in = new FileInputStream(cfg)) {
			p.load(in);
		}
		// 原生驱动类存在性(starrocks-connector-j):1.1.2起类名迁移com.starrocks.cj.jdbc.Driver
		// (旧com.starrocks.jdbc.Driver仍兼容但报deprecated),优先新类名,旧驱动回退旧类名
		try {
			Class.forName("com.starrocks.cj.jdbc.Driver");
		} catch (ClassNotFoundException e) {
			Class.forName("com.starrocks.jdbc.Driver");
		}
		String url = p.getProperty("url");
		assertTrue(url != null && url.startsWith("jdbc:starrocks:"),
				"本验证针对原生驱动URL形态jdbc:starrocks://,当前url=" + url);
		conn = DriverManager.getConnection(url, p.getProperty("username"), p.getProperty("password"));
		reportedProductName = conn.getMetaData().getDatabaseProductName();
		FunctionUtils.setFunctionConverts(java.util.Arrays.asList("default",
				"org.sagacity.sqltoy.plugins.function.impl.DateDiff"));
		available = true;
	}

	private Object querySingle(String sql) throws Exception {
		try (PreparedStatement pst = conn.prepareStatement(sql); ResultSet rs = pst.executeQuery()) {
			rs.next();
			return rs.getObject(1);
		}
	}

	/**
	 * 核心验证:DataSourceUtils对jdbc:starrocks://连接的方言识别——
	 * URL特征命中starrocks方言(产品名伪装MySQL不参与),dbType=STARROCKS,
	 * 归mysql协议族(反斜杠转义/引号策略等按mysql族分派)
	 */
	@Test
	public void nativeDriverProfileDetection() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeTrue(available, "starrocks探针环境不可用,跳过");
		System.err.println("driver reported ProductName=" + reportedProductName
				+ ", ProductVersion=" + conn.getMetaData().getDatabaseProductVersion());
		DBProfile profile = DataSourceUtils.getDBProfile(conn);
		assertEquals("starrocks", profile.getDialect(), "jdbc:starrocks://应直命中starrocks方言");
		assertEquals(DBType.STARROCKS, profile.getDbType(), "dbType应为STARROCKS(47)");
		assertTrue(profile.isMysqlFamily(), "starrocks属mysql协议族");
		assertEquals(DBType.STARROCKS, DataSourceUtils.getDBType(conn), "getDBType公开API应返回STARROCKS");
		// 真实引擎自证:current_version()为StarRocks特有函数,返回版本串(如4.1.4-4a9848e)
		Object engineVersion = querySingle("select current_version()");
		System.err.println("starrocks current_version() => " + engineVersion);
		assertTrue(engineVersion != null && engineVersion.toString().matches("\\d+\\.\\d+\\.\\d+.*"),
				"current_version()应返回版本串,实为:" + engineVersion);
	}

	/**
	 * 原生驱动下的基础查询与方言函数转换执行:
	 * ifnull/date_format为mysql族原生函数;方言链nvl转换后的SQL可执行
	 */
	@Test
	public void nativeDriverBasicQuery() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeTrue(available, "starrocks探针环境不可用,跳过");
		assertEquals(1, ((Number) querySingle("select 1")).intValue(), "select 1基础查询");
		// mysql族原生ifnull
		assertEquals("a", querySingle("select ifnull(null,'a')").toString(), "ifnull原生执行");
		// date_format原生(mysql族形态)
		assertEquals("2026-01", querySingle("select date_format('2026-01-15','%Y-%m')").toString());
		// 方言函数转换链:nvl按starrocks方言转换后可执行
		String converted = FunctionUtils.getDialectSql("select nvl('a','b')", "starrocks");
		System.err.println("nvl converted for starrocks => " + converted);
		assertEquals("a", querySingle(converted).toString(), "nvl转换后执行结果");
		// 字面量安全:字面量内函数样文本不得被转换,原样执行返回字面量文本
		String literal = FunctionUtils.getDialectSql("select 'nvl(a,b)'", "starrocks");
		assertEquals("select 'nvl(a,b)'", literal, "字面量nvl不得被转换");
		assertEquals("nvl(a,b)", querySingle(literal).toString(), "字面量原样执行返回字面量文本");
	}

	/**
	 * 会话级一致性:同一连接重复解析应命中URL_PROFILE_CACHE(档案按URL缓存),
	 * 多次获取dbType结果稳定
	 */
	@Test
	public void profileCacheConsistency() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeTrue(available, "starrocks探针环境不可用,跳过");
		for (int i = 0; i < 3; i++) {
			assertEquals(DBType.STARROCKS, DataSourceUtils.getDBType(conn), "重复解析结果应稳定");
		}
	}

	@AfterAll
	public static void destroy() throws Exception {
		if (conn != null) {
			conn.close();
		}
	}
}
