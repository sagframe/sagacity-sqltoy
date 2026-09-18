package org.sagacity.sqltoy.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.utils.DataSourceUtils;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * update 2026-9-12 优化步骤1:DBProfile族判定与声明型事实的单测——
 * 族方法与既有分派组(ReservedWordsUtil反引号组/SqlUtil.isPGFamily/OpenGaussDialectUtils
 * wrapSelectFields组)逐字对齐的回归保障;nullFunction与各方言NVL_FUNCTION常量同源的
 * 一致性锚点(方言侧若改动需同步本表,步骤3收编后由profile唯一承载)。
 */
public class DBProfileTest {

	private DBProfile profile(int dbType) {
		return profile(dbType, "test-product");
	}

	private DBProfile profile(int dbType, String productName) {
		return new DBProfile("jdbc:test://localhost/db", "test", dbType, productName, 1, null, null, false);
	}

	@Test
	public void mysqlFamily() {
		assertTrue(profile(DBType.MYSQL).isMysqlFamily(), "mysql");
		assertTrue(profile(DBType.MYSQL57).isMysqlFamily(), "mysql57");
		assertTrue(profile(DBType.TIDB).isMysqlFamily(), "tidb");
		assertTrue(profile(DBType.DORIS).isMysqlFamily(), "doris");
		assertTrue(profile(DBType.STARROCKS).isMysqlFamily(), "starrocks");
		assertFalse(profile(DBType.TDENGINE).isMysqlFamily(), "tdengine仅引号策略同组,非语法族");
		assertFalse(profile(DBType.ORACLE).isMysqlFamily(), "oracle");
		assertFalse(profile(DBType.OCEANBASE).isMysqlFamily(), "oceanbase按URL特征归自身dbType");
	}

	@Test
	public void oracleFamily() {
		assertTrue(profile(DBType.ORACLE).isOracleFamily(), "oracle");
		assertTrue(profile(DBType.ORACLE11).isOracleFamily(), "oracle11");
		assertTrue(profile(DBType.DM).isOracleFamily(), "dm");
		assertTrue(profile(DBType.OCEANBASE).isOracleFamily(), "oceanbase的OceanBaseDialect为oracle系");
		assertFalse(profile(DBType.POSTGRESQL).isOracleFamily(), "postgresql");
	}

	@Test
	public void pgFamily() {
		assertTrue(profile(DBType.POSTGRESQL).isPGFamily(), "postgresql");
		assertTrue(profile(DBType.POSTGRESQL14).isPGFamily(), "postgresql14");
		assertTrue(profile(DBType.OPENGAUSS).isPGFamily(), "opengauss");
		assertTrue(profile(DBType.MOGDB).isPGFamily(), "mogdb");
		assertTrue(profile(DBType.GAUSSDB).isPGFamily(), "gaussdb");
		assertTrue(profile(DBType.STARDB).isPGFamily(), "stardb");
		assertTrue(profile(DBType.VASTBASE).isPGFamily(), "vastbase");
		assertTrue(profile(DBType.KINGBASE).isPGFamily(), "kingbase");
		// 与SqlUtil.isPGFamily逐字一致:oscar为老pgjdbc深度魔改衍生,PGobject绑定不适用
		assertFalse(profile(DBType.OSCAR).isPGFamily(), "oscar不在PGobject绑定族");
		// update 2026-9-16 静态单一事实源与实例方法等价(DataSourceUtils.resolvePGobjectHolder
		// 的探测守卫消费:非PG系dbType不再盲探postgresql驱动的PGobject类)
		assertTrue(DBProfile.isPGFamily(DBType.POSTGRESQL), "static:postgresql");
		assertTrue(DBProfile.isPGFamily(DBType.STARDB), "static:stardb");
		assertTrue(DBProfile.isPGFamily(DBType.KINGBASE), "static:kingbase");
		assertFalse(DBProfile.isPGFamily(DBType.OSCAR), "static:oscar不在PGobject绑定族");
		assertFalse(DBProfile.isPGFamily(DBType.MYSQL), "static:mysql");
		assertFalse(DBProfile.isPGFamily(DBType.UNDEFINE), "static:undefine");
	}

	@Test
	public void gaussFamily() {
		assertTrue(profile(DBType.OPENGAUSS).isGaussFamily(), "opengauss");
		assertTrue(profile(DBType.GAUSSDB).isGaussFamily(), "gaussdb");
		assertTrue(profile(DBType.MOGDB).isGaussFamily(), "mogdb");
		assertTrue(profile(DBType.VASTBASE).isGaussFamily(), "vastbase");
		assertTrue(profile(DBType.STARDB).isGaussFamily(), "stardb");
		assertTrue(profile(DBType.OSCAR).isGaussFamily(), "oscar(神通openGauss版)继承OpenGaussDialect");
		assertFalse(profile(DBType.KINGBASE).isGaussFamily(), "kingbase为PG系非og内核");
	}

	@Test
	public void oceanBaseByProductName() {
		// mysql租户模式产品名伪装MySQL但携带OceanBase特征
		assertTrue(profile(DBType.MYSQL, "MySQL 5.7.25-OceanBase_CE-v4.3.5.0").isOceanBase(), "CE mysql模式产品名");
		assertTrue(profile(DBType.OCEANBASE, "OceanBase 5.0.1").isOceanBase(), "oracle租户模式产品名");
		assertFalse(profile(DBType.MYSQL, "MySQL Community Server 9.3.0").isOceanBase(), "真mysql不误判");
		assertFalse(profile(DBType.MYSQL, null).isOceanBase(), "产品名null安全");
	}

	@Test
	public void nullFunctionTable() {
		// update 2026-9-14 按SQL标准(ANSI COALESCE)统一空值判定函数:全库一致
		assertEquals("nvl", profile(DBType.ORACLE).getNullFunction(), "oracle(扩展类型需NVL类型推断)");
		assertEquals("COALESCE", profile(DBType.SQLSERVER).getNullFunction(), "sqlserver");
		assertEquals("COALESCE", profile(DBType.MYSQL).getNullFunction(), "mysql");
		assertEquals("COALESCE", profile(DBType.KINGBASE).getNullFunction(), "kingbase");
		assertEquals("COALESCE", profile(DBType.POSTGRESQL).getNullFunction(), "postgresql");
		assertEquals("nvl", DataSourceUtils.getNvlFunction(DBType.ORACLE), "getNvlFunction(oracle扩展类型)");
		assertEquals("COALESCE", DataSourceUtils.getNvlFunction(DBType.MYSQL), "getNvlFunction(mysql)");
	}

	@Test
	public void updatableResultSet() {
		// 与各方言updateSaveFetch/updateFetch显式Unsupported声明同源
		assertFalse(profile(DBType.CLICKHOUSE).supportsUpdatableResultSet(), "clickhouse拒绝可更新结果集");
		assertFalse(profile(DBType.HANA).supportsUpdatableResultSet(), "hana的ngdbc拒绝CONCUR_UPDATABLE");
		assertTrue(profile(DBType.MYSQL).supportsUpdatableResultSet(), "mysql支持");
		assertTrue(profile(DBType.ORACLE).supportsUpdatableResultSet(), "oracle支持");
		assertTrue(profile(DBType.POSTGRESQL).supportsUpdatableResultSet(), "postgresql支持");
	}
}
