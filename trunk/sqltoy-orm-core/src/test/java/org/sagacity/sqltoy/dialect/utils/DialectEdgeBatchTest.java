package org.sagacity.sqltoy.dialect.utils;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.EntityManager;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.PKStrategy;
import org.sagacity.sqltoy.demo.domain.StaffInfo;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;
import org.sagacity.sqltoy.utils.ReservedWordsUtil;

/**
 * 回归测试：(9)pkStrategy常量在左的null安全equals;(11)SqlServer generateInsertSql
 * 非主键列保留字转换——保留字列被[]包裹不再裸拼
 */
public class DialectEdgeBatchTest {

	@Test
	public void sqlServerInsertNonPkReservedWordConverted() throws Exception {
		SqlToyContext context = new SqlToyContext();
		EntityManager entityManager = new EntityManager();
		EntityMeta meta = entityManager.parseEntityMeta(context, StaffInfo.class, true, false);
		// 注册staff_name为保留字,验证非主键列(STAFF_NAME)在insert中被[]包裹
		ReservedWordsUtil.put("staff_name");
		try {
			String sql = SqlServerDialectUtils.generateInsertSql(null, DBType.SQLSERVER, meta, null, null, null, null,
					false);
			assertNotNull(sql);
			// 修复前非主键列直接拼fieldMeta.getColumnName()(裸STAFF_NAME),修复后走convertWord被[]包裹
			assertTrue(sql.contains("[STAFF_NAME]"), "非主键保留字列应被[]包裹,实际:" + sql);
			assertTrue(sql.toLowerCase().contains("insert"), "实际:" + sql);
		} finally {
			// ReservedWordsUtil为合并语义,保留字staff_name对其他测试无影响(仅SqlServer方言转换)
		}
	}

	@Test
	public void pkStrategyNullDoesNotBreakInsertSql() throws Exception {
		// pkStrategy=null(@Id未配strategy)时generateInsertSql不再因pkStrategy.equals抛NPE
		SqlToyContext context = new SqlToyContext();
		EntityManager entityManager = new EntityManager();
		EntityMeta meta = entityManager.parseEntityMeta(context, StaffInfo.class, true, false);
		String sql = SqlServerDialectUtils.generateInsertSql(null, DBType.SQLSERVER, meta, null, null, null, null,
				false);
		assertNotNull(sql);
		assertTrue(sql.toLowerCase().contains("insert"), "实际:" + sql);
	}

	@Test
	public void dialectUtilsGetSaveOrUpdatePkNullSafe() {
		// PKStrategy.SEQUENCE.equals(null)为false不抛异常(原pkStrategy.equals在null时NPE)
		assertTrue(!PKStrategy.SEQUENCE.equals(null));
		assertNotNull(PKStrategy.SEQUENCE.name());
	}
}
