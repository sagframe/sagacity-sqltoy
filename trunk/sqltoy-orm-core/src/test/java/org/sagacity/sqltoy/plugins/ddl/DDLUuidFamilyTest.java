package org.sagacity.sqltoy.plugins.ddl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.model.ColumnMeta;
import org.sagacity.sqltoy.model.JdbcTypes;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * uuid族DDL列型：原生uuid方言(pg内核系/h2/sqlserver)保原生，其余方言以变长36位字符承载。
 * db2等不用CHAR(36)——CHAR空白填充会给去连字符的32位值补尾空格，读回带空格、跨库迁移后
 * 尾空格变成有效字符，等值/唯一约束失真。
 */
public class DDLUuidFamilyTest {

	private static ColumnMeta nativeUuid() {
		ColumnMeta col = new ColumnMeta();
		col.setNativeType("uuid");
		return col;
	}

	private static ColumnMeta jdbcUuid() {
		ColumnMeta col = new ColumnMeta();
		col.setDataType(JdbcTypes.UUID);
		return col;
	}

	@Test
	public void nativeUuidAcrossDialects() {
		assertEquals("uuid", DDLUtils.convertType(nativeUuid(), DBType.POSTGRESQL), "pg原生uuid");
		assertEquals("uuid", DDLUtils.convertType(nativeUuid(), DBType.KINGBASE), "kingbase原生uuid");
		assertEquals("UUID", DDLUtils.convertType(nativeUuid(), DBType.H2), "h2原生UUID");
		assertEquals("UNIQUEIDENTIFIER", DDLUtils.convertType(nativeUuid(), DBType.SQLSERVER), "sqlserver原生");
		assertEquals("VARCHAR2(36)", DDLUtils.convertType(nativeUuid(), DBType.ORACLE), "oracle变长36");
		assertEquals("VARCHAR2(36)", DDLUtils.convertType(nativeUuid(), DBType.ORACLE11), "oracle11变长36");
		// db2禁用CHAR(36):空白填充语义
		assertEquals("VARCHAR(36)", DDLUtils.convertType(nativeUuid(), DBType.DB2), "db2变长36");
		assertEquals("VARCHAR(36)", DDLUtils.convertType(nativeUuid(), DBType.MYSQL), "mysql变长36");
		assertEquals("VARCHAR(36)", DDLUtils.convertType(nativeUuid(), DBType.DM), "dm变长36");
		assertEquals("NVARCHAR(36)", DDLUtils.convertType(nativeUuid(), DBType.HANA), "hana变长36");
		assertEquals("TEXT", DDLUtils.convertType(nativeUuid(), DBType.SQLITE), "sqlite TEXT");
	}

	@Test
	public void jdbcUuidAcrossDialects() {
		assertEquals("uuid", DDLUtils.convertType(jdbcUuid(), DBType.POSTGRESQL), "pg jdbcType uuid");
		assertEquals("VARCHAR(36)", DDLUtils.convertType(jdbcUuid(), DBType.DB2), "db2 jdbcType uuid变长36");
		assertEquals("VARCHAR2(36)", DDLUtils.convertType(jdbcUuid(), DBType.ORACLE11), "oracle11 jdbcType uuid");
	}
}
