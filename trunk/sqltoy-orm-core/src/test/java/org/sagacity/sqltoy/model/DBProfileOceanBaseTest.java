package org.sagacity.sqltoy.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.utils.DataSourceUtils;

/**
 * OB判定统一回归:分派点由DialectExtUtils.isOceanBaseAsMysql()(读ThreadLocal的actuallyDBType)
 * 统一为DBProfile.isOceanBase()(按产品名判定)。本用例锁住统一成立的两个前提:
 * 1、OB按mysql方言配置(执行链dbType为MYSQL)时产品名仍携带OceanBase字样;
 * 2、asEffective(配置覆盖dbType/dialect)必须保留产品名这一"本体事实"。
 */
public class DBProfileOceanBaseTest {

	private static DBProfile profile(String dialect, int dbType, String productName) {
		return new DBProfile("jdbc:" + dialect + "://host:3306/db", dialect, dbType, productName, 5, null, null, false);
	}

	@Test
	public void oceanBaseConfiguredAsMysqlIsDetected() {
		// mysql租户模式驱动上报的产品名形态
		DBProfile obAsMysql = profile("mysql", DataSourceUtils.DBType.MYSQL, "MySQL 5.7.25-OceanBase_CE-v4.3.5.0");
		assertTrue(obAsMysql.isOceanBase(), "OB按mysql方言配置时须判定为OceanBase");
		// oracle租户模式产品名同样携带OceanBase字样
		assertTrue(profile("oracle", DataSourceUtils.DBType.OCEANBASE, "OceanBase 4.3.5.0").isOceanBase());
	}

	@Test
	public void realMysqlAndOthersAreNotOceanBase() {
		assertFalse(profile("mysql", DataSourceUtils.DBType.MYSQL, "MySQL").isOceanBase());
		assertFalse(profile("postgresql", DataSourceUtils.DBType.POSTGRESQL, "PostgreSQL").isOceanBase());
		// 产品名缺失时不得误判
		assertFalse(profile("mysql", DataSourceUtils.DBType.MYSQL, null).isOceanBase());
	}

	@Test
	public void effectiveProfileKeepsOceanBaseFact() {
		// asEffective:以配置覆盖dbType/dialect(即"OB按mysql方言配置"的取代表达),产品名事实必须保留
		DBProfile raw = profile("oceanbase", DataSourceUtils.DBType.OCEANBASE, "OceanBase 4.3.5.0");
		DBProfile effective = raw.asEffective(DataSourceUtils.DBType.MYSQL, "mysql");
		assertEquals(DataSourceUtils.DBType.MYSQL, effective.getDbType());
		assertTrue(effective.isOceanBase(), "asEffective必须保留产品名事实,否则分派点会误判为真mysql");
	}
}
