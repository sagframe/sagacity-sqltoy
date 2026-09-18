package org.sagacity.sqltoy.plugins.ddl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Types;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.model.ColumnMeta;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * DDLUtils.convertType方言类型映射矩阵:覆盖核心jdbc类型在主要库族的DDL类型名,
 * 以及nativeType(JSON/BSON)直通与BSON在非PG族的JSON降级。
 */
public class DDLConvertTypeMatrixTest {

	private static ColumnMeta col(int dataType) {
		ColumnMeta col = new ColumnMeta();
		col.setDataType(dataType);
		return col;
	}

	private static ColumnMeta colNative(int dataType, String nativeType) {
		ColumnMeta col = col(dataType);
		col.setNativeType(nativeType);
		return col;
	}

	@Test
	public void bigintMapping() {
		assertEquals("NUMBER", DDLUtils.convertType(col(Types.BIGINT), DBType.ORACLE), "oracle bigint→NUMBER");
		assertEquals("NUMBER", DDLUtils.convertType(col(Types.BIGINT), DBType.DM), "dm bigint→NUMBER");
		assertEquals("BIGINT", DDLUtils.convertType(col(Types.BIGINT), DBType.MYSQL), "mysql bigint→BIGINT");
		assertEquals("BIGINT", DDLUtils.convertType(col(Types.BIGINT), DBType.POSTGRESQL), "pg bigint→BIGINT");
	}

	@Test
	public void varcharMapping() {
		// update 2026-9-15 实际实现:oracle varchar原样VARCHAR(长度由setLength阶段处理),非VARCHAR2
		assertEquals("VARCHAR", DDLUtils.convertType(col(Types.VARCHAR), DBType.ORACLE), "oracle varchar→VARCHAR");
		assertEquals("VARCHAR", DDLUtils.convertType(col(Types.VARCHAR), DBType.MYSQL), "mysql varchar→VARCHAR");
		assertEquals("VARCHAR", DDLUtils.convertType(col(Types.VARCHAR), DBType.POSTGRESQL), "pg varchar→VARCHAR");
	}

	@Test
	public void nativeTypeJsonPassThroughWithHanaFallback() {
		assertEquals("JSON", DDLUtils.convertType(colNative(Types.VARCHAR, "JSON"), DBType.MYSQL), "mysql json");
		assertEquals("NCLOB", DDLUtils.convertType(colNative(Types.VARCHAR, "JSON"), DBType.HANA),
				"hana无原生json列,降级NCLOB");
	}

	@Test
	public void bsonFallsBackToJsonOnNonPgFamily() {
		// pg族保留BSON,其余库降级JSON
		assertEquals("BSON", DDLUtils.convertType(colNative(Types.VARCHAR, "BSON"), DBType.POSTGRESQL), "pg bson");
		assertEquals("JSON", DDLUtils.convertType(colNative(Types.VARCHAR, "BSON"), DBType.ORACLE), "oracle bson→JSON");
		assertEquals("JSON", DDLUtils.convertType(colNative(Types.VARCHAR, "BSON"), DBType.MYSQL), "mysql bson→JSON");
	}

	@Test
	public void timestampMappingAcrossFamilies() {
		String ts = DDLUtils.convertType(col(Types.TIMESTAMP), DBType.ORACLE);
		assertTrue(ts != null && !ts.isEmpty(), "oracle timestamp映射非空: " + ts);
		String mysqlTs = DDLUtils.convertType(col(Types.TIMESTAMP), DBType.MYSQL);
		assertTrue(mysqlTs != null && !mysqlTs.isEmpty(), "mysql timestamp映射非空: " + mysqlTs);
	}

	@Test
	public void clobAndBlobMapping() {
		String oracleClob = DDLUtils.convertType(col(Types.CLOB), DBType.ORACLE);
		assertTrue(oracleClob.toUpperCase().contains("CLOB"), "oracle clob: " + oracleClob);
		String mysqlText = DDLUtils.convertType(col(Types.CLOB), DBType.MYSQL);
		assertTrue(mysqlText.toUpperCase().contains("TEXT") || mysqlText.toUpperCase().contains("LONGTEXT"),
				"mysql clob→TEXT族: " + mysqlText);
	}
}
