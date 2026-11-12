package org.sagacity.sqltoy.plugins.ddl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.model.ColumnMeta;
import org.sagacity.sqltoy.utils.DataSourceUtils;

/**
 * F14(续)回归:KingbaseES基于PG,DDL类型映射必须与vanilla PG一致。修复前DDLUtils的PG系类型映射组
 * (BSON/BYTEA/NUMERIC/数组等8处)漏掉KINGBASE,Kingbase生成DDL时静默走通用分支(如BLOB不映射BYTEA)
 */
public class KingbaseDdlTypeConvertTest {

	private static ColumnMeta col(Integer dataType, String typeName, String nativeType) {
		ColumnMeta col = new ColumnMeta();
		col.setColName("c1");
		col.setDataType(dataType);
		col.setTypeName(typeName);
		col.setNativeType(nativeType);
		return col;
	}

	private static List<ColumnMeta> probes() {
		List<ColumnMeta> cols = new ArrayList<ColumnMeta>();
		// BSON来源类型:PG系返回BSON,其它返回JSON
		cols.add(col(java.sql.Types.OTHER, null, "BSON"));
		// 二进制族:PG系统一BYTEA
		cols.add(col(java.sql.Types.BLOB, null, null));
		cols.add(col(java.sql.Types.BINARY, null, null));
		cols.add(col(java.sql.Types.LONGVARBINARY, null, null));
		// 数值精度:PG系numeric为任意精度
		cols.add(col(java.sql.Types.DECIMAL, null, null));
		return cols;
	}

	@Test
	public void kingbaseTypeMappingIdenticalToVanillaPostgresql() {
		for (ColumnMeta col : probes()) {
			assertEquals(DDLUtils.convertType(col, DataSourceUtils.DBType.POSTGRESQL),
					DDLUtils.convertType(col, DataSourceUtils.DBType.KINGBASE),
					"KINGBASE类型映射应与vanilla PG一致, dataType=" + col.getDataType() + ", nativeType="
							+ col.getNativeType());
		}
	}

	@Test
	public void probesHitPgSpecificBranchesSoEqualityIsMeaningful() {
		int pgSpecific = 0;
		for (ColumnMeta col : probes()) {
			if (!DDLUtils.convertType(col, DataSourceUtils.DBType.POSTGRESQL)
					.equals(DDLUtils.convertType(col, DataSourceUtils.DBType.MYSQL))) {
				pgSpecific++;
			}
		}
		assertTrue(pgSpecific >= 3, "至少3个探针应命中PG专属分支,实际:" + pgSpecific);
	}

	@Test
	public void kingbaseUsesPostgresTypes() {
		assertEquals("BSON",
				DDLUtils.convertType(col(java.sql.Types.OTHER, null, "BSON"), DataSourceUtils.DBType.KINGBASE));
		assertTrue(DDLUtils.convertType(col(java.sql.Types.BLOB, null, null), DataSourceUtils.DBType.KINGBASE)
				.startsWith("BYTEA"));
	}

	@Test
	public void kingbaseUsesPostgresDdlGenerator() throws Exception {
		java.lang.reflect.Method method = DDLFactory.class.getDeclaredMethod("getGenerator", Integer.class);
		method.setAccessible(true);
		Object generator = method.invoke(null, DataSourceUtils.DBType.KINGBASE);
		// 修复前落入default的DefaultDDLGenerator(任何DDL生成调用都抛异常),修复后应与vanilla PG一致
		assertEquals("PostgreSqlDDLGenerator", generator.getClass().getSimpleName());
		assertEquals(method.invoke(null, DataSourceUtils.DBType.POSTGRESQL).getClass(), generator.getClass());
	}
}
