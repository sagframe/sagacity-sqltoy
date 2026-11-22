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
		assertEquals("NUMBER", DDLUtils.convertType(col(Types.BIGINT), DBType.OCEANBASE),
				"oceanbase(oracle 模式) bigint→NUMBER");
		assertEquals("BIGINT", DDLUtils.convertType(col(Types.BIGINT), DBType.MYSQL), "mysql bigint→BIGINT");
		assertEquals("BIGINT", DDLUtils.convertType(col(Types.BIGINT), DBType.POSTGRESQL), "pg bigint→BIGINT");
	}

	@Test
	public void varcharMapping() {
		// update 2026-9-15 实际实现:oracle varchar原样VARCHAR(长度由setLength阶段处理),非VARCHAR2
		assertEquals("VARCHAR", DDLUtils.convertType(col(Types.VARCHAR), DBType.ORACLE), "oracle varchar→VARCHAR");
		assertEquals("VARCHAR", DDLUtils.convertType(col(Types.VARCHAR), DBType.MYSQL), "mysql varchar→VARCHAR");
		assertEquals("VARCHAR", DDLUtils.convertType(col(Types.VARCHAR), DBType.POSTGRESQL), "pg varchar→VARCHAR");
		// update 2026-11-22 sqlserver unicode:VARCHAR非unicode码页致中文乱码("????"),默认NVARCHAR(真库A/B实证)
		assertEquals("NVARCHAR", DDLUtils.convertType(col(Types.VARCHAR), DBType.SQLSERVER), "sqlserver varchar→NVARCHAR");
		assertEquals("NVARCHAR", DDLUtils.convertType(col(Types.NVARCHAR), DBType.SQLSERVER), "sqlserver nvarchar→NVARCHAR");
	}

	@Test
	public void nativeTypeJsonPassThroughWithHanaFallback() {
		assertEquals("JSON", DDLUtils.convertType(colNative(Types.VARCHAR, "JSON"), DBType.MYSQL), "mysql json");
		assertEquals("NCLOB", DDLUtils.convertType(colNative(Types.VARCHAR, "JSON"), DBType.HANA),
				"hana无原生json列,降级NCLOB");
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

	@Test
	public void oceanBaseFollowsOracleDdlTypes() {
		assertEquals("INTEGER", DDLUtils.convertType(col(Types.BOOLEAN), DBType.OCEANBASE),
				"oceanbase(oracle 模式) boolean→INTEGER");
		assertEquals("BINARY_DOUBLE", DDLUtils.convertType(col(Types.DOUBLE), DBType.OCEANBASE),
				"oceanbase(oracle 模式) double→BINARY_DOUBLE");
		assertEquals("CLOB", DDLUtils.convertType(col(Types.CLOB), DBType.OCEANBASE),
				"oceanbase(oracle 模式) clob→CLOB");
	}

	private static ColumnMeta colVec(int size) {
		ColumnMeta col = col(org.sagacity.sqltoy.model.JdbcTypes.VECTOR);
		col.setColumnSize(size);
		return col;
	}

	@Test
	public void vectorNativeDimensionSingleSuffixAndUnboundedSentinel() {
		// update 2026-11-22 回退glm5.3的openGauss系/kingbase VECTOR→VARCHAR承载半截改动后,VECTOR列
		// 回归原生vector类型族(与dialect insert-wrap/merge-cast的cast(? as vector)链路一致):
		// gaussdb/vastbase为FLOATVECTOR,其余pg内核系(openGauss/mogdb/stardb/oscar/kingbase/pg)为VECTOR。
		// 维度(columnSize)有效时单(n)后缀;0维度与无界哨兵Integer.MAX_VALUE(pgvector经JDBC取到的
		// COLUMN_SIZE)均渲染裸类型名,绝不挂双维度或哨兵维度(此前glm5.3 VARCHAR承载拼出VARCHAR(n)(n)非法)
		for (int dbType : new int[] { DBType.OPENGAUSS, DBType.MOGDB, DBType.STARDB, DBType.OSCAR, DBType.KINGBASE,
				DBType.POSTGRESQL }) {
			assertEquals("VECTOR(3)", DDLUtils.convertType(colVec(3), dbType), "pg内核系vector单维度,dbType=" + dbType);
			assertEquals("VECTOR", DDLUtils.convertType(colVec(0), dbType), "0维度裸vector,dbType=" + dbType);
			assertEquals("VECTOR", DDLUtils.convertType(colVec(Integer.MAX_VALUE), dbType),
					"无界哨兵裸vector,dbType=" + dbType);
		}
		assertEquals("FLOATVECTOR(3)", DDLUtils.convertType(colVec(3), DBType.GAUSSDB), "gaussdb floatvector单维度");
		assertEquals("FLOATVECTOR(3)", DDLUtils.convertType(colVec(3), DBType.VASTBASE), "vastbase floatvector单维度");
		assertEquals("FLOATVECTOR", DDLUtils.convertType(colVec(Integer.MAX_VALUE), DBType.GAUSSDB),
				"gaussdb无界哨兵裸floatvector");
	}

	@Test
	public void longVarcharMysqlNativeTypeHonoredOthersTranslated() {
		// update 2026-11-22 LONGVARCHAR归并CLOB家族(db2无TEXT类型真库-204修复)后,mysql协议系目标须保留
		// 源nativeType(longtext/mediumtext),否则longtext降级TEXT(64KB上限)截断大文本;其余目标翻译为
		// CLOB/NCLOB/TEXT。CLOB/NCLOB自身行为不受影响(仅LONGVARCHAR按getDataType隔离透传nativeType)
		ColumnMeta lv = colNative(Types.LONGVARCHAR, "longtext");
		assertEquals("longtext", DDLUtils.convertType(lv, DBType.MYSQL), "mysql保留longtext精度");
		assertEquals("longtext", DDLUtils.convertType(lv, DBType.MYSQL57), "mysql57保留longtext精度");
		assertEquals("longtext", DDLUtils.convertType(lv, DBType.TIDB), "tidb保留longtext精度");
		assertEquals("CLOB", DDLUtils.convertType(lv, DBType.DB2), "db2无TEXT/longtext,翻译CLOB(避免-204)");
		assertEquals("CLOB", DDLUtils.convertType(lv, DBType.ORACLE), "oracle翻译CLOB");
		assertEquals("NCLOB", DDLUtils.convertType(lv, DBType.HANA), "hana翻译NCLOB");
		assertEquals("TEXT", DDLUtils.convertType(lv, DBType.POSTGRESQL), "pg翻译TEXT");
		assertEquals("TEXT", DDLUtils.convertType(colNative(Types.CLOB, "longtext"), DBType.MYSQL),
				"CLOB行为不变,mysql仍TEXT(不透传nativeType)");
	}

	/**
	 * update 2026-11-22 复查修复批次回归锁死:①json/jsonb精度以nativeType词面为准(此前type优先导致
	 * jsonb被静默降级为json);②显式标量jdbcType优先于数组组件类型推断(与wrapSelectFields的jdbcType
	 * 优先链统一,避免List<String>+type=CLOB被改成varchar[]、String[]+type=VARCHAR与merge cast相反);
	 * ③UUID族/type=UUID尊重显式nativeType(BINARY(16)等紧凑承载);④HANA保留原生TINYINT且不追加显示宽度
	 */
	@Test
	public void fixBatchJsonPrecisionScalarPriorityUuidAndHanaTinyint() {
		// ① jsonb精度:显式type与nativeType词面冲突时以nativeType为准(与specialCastType同口径)
		assertEquals("jsonb",
				DDLUtils.convertType(colNative(org.sagacity.sqltoy.model.JdbcTypes.JSON, "jsonb"), DBType.POSTGRESQL),
				"type=JSON+nativeType=jsonb须保jsonb(此前降级json)");
		assertEquals("json",
				DDLUtils.convertType(colNative(org.sagacity.sqltoy.model.JdbcTypes.JSONB, "json"), DBType.POSTGRESQL),
				"type=JSONB+nativeType=json须保json(此前升级jsonb)");
		assertEquals("json",
				DDLUtils.convertType(colNative(org.sagacity.sqltoy.model.JdbcTypes.JSON, null), DBType.POSTGRESQL));
		assertEquals("jsonb",
				DDLUtils.convertType(colNative(org.sagacity.sqltoy.model.JdbcTypes.JSONB, null), DBType.POSTGRESQL));

		// ② 显式标量jdbcType优先于组件推断
		ColumnMeta clobList = new ColumnMeta();
		clobList.setDataType(Types.CLOB);
		clobList.setArrayComponentType("java.lang.String");
		assertEquals("TEXT", DDLUtils.convertType(clobList, DBType.POSTGRESQL),
				"List<String>+type=CLOB须保CLOB族(此前被改成varchar[])");
		ColumnMeta varcharArr = new ColumnMeta();
		varcharArr.setDataType(Types.VARCHAR);
		varcharArr.setColumnSize(100);
		varcharArr.setArrayComponentType("java.lang.String");
		assertEquals("VARCHAR(100)", DDLUtils.convertType(varcharArr, DBType.POSTGRESQL),
				"String[]+type=VARCHAR须与merge cast的cast(? as VARCHAR(100))一致(此前生成varchar[])");
		assertEquals("VARCHAR(100)", DDLUtils.convertType(varcharArr, DBType.H2));

		// ③ uuid族显式nativeType优先
		assertEquals("BINARY(16)", DDLUtils.convertType(
				colNative(org.sagacity.sqltoy.model.JdbcTypes.UUID, "BINARY(16)"), DBType.MYSQL),
				"uuid紧凑承载BINARY(16)须原样保留(此前强制VARCHAR(36))");
		assertEquals("VARCHAR(36)",
				DDLUtils.convertType(colNative(org.sagacity.sqltoy.model.JdbcTypes.UUID, "uuid"), DBType.MYSQL));

		// ④ HANA TINYINT原生,且不追加mysql式显示宽度
		assertEquals("TINYINT", DDLUtils.convertType(col(Types.TINYINT), DBType.HANA));
		ColumnMeta tiny8 = col(Types.TINYINT);
		tiny8.setColumnSize(8);
		assertEquals("TINYINT", DDLUtils.convertType(tiny8, DBType.HANA), "hana不接受TINYINT(n)");
		assertEquals("SMALLINT", DDLUtils.convertType(col(Types.TINYINT), DBType.POSTGRESQL), "pg无tinyint");
	}

	/**
	 * update 2026-11-22 TIMESTAMP_WITH_TIMEZONE 方言分派:此前H2漏落"TIMESTAMP"丢时区(仅存墙钟,
	 * 读回按JVM时区补齐),与merge cast的cast(? as TIMESTAMP WITH TIME ZONE)口径不一致;
	 * H2原生支持该类型且能完整保留偏移(真库+09:00往返实证)
	 */
	@Test
	public void timestampWithTimezoneDdlDispatch() {
		assertEquals("TIMESTAMP WITH TIME ZONE", DDLUtils.convertType(col(Types.TIMESTAMP_WITH_TIMEZONE), DBType.H2),
				"h2 2014须原生TIMESTAMP WITH TIME ZONE");
		assertEquals("TIMESTAMP WITH TIME ZONE",
				DDLUtils.convertType(col(Types.TIMESTAMP_WITH_TIMEZONE), DBType.POSTGRESQL));
		assertEquals("TIMESTAMP WITH TIME ZONE", DDLUtils.convertType(col(Types.TIMESTAMP_WITH_TIMEZONE), DBType.DM));
		assertEquals("DATETIMEOFFSET", DDLUtils.convertType(col(Types.TIMESTAMP_WITH_TIMEZONE), DBType.SQLSERVER));
		// db2无该类型,承载TIMESTAMP(绑定时已toLocalDateTime,真库实证)
		assertEquals("TIMESTAMP", DDLUtils.convertType(col(Types.TIMESTAMP_WITH_TIMEZONE), DBType.DB2));
	}

	/**
	 * update 2026-11-22 sqlserver VARCHAR→NVARCHAR 的长度上限回归锁死:sqlserver NVARCHAR 上限4000,
	 * varchar源列(length=8000)直接拼 NVARCHAR(8000) 建表报
	 * "The size (8000) given to the parameter ... exceeds the maximum allowed (4000)"(真库实证),
	 * 超出上限改按 NVARCHAR(MAX) 承载,4000及以内沿用长度
	 */
	@Test
	public void sqlserverNvarcharLengthCap() {
		ColumnMeta v50 = col(Types.VARCHAR);
		v50.setColumnSize(50);
		assertEquals("NVARCHAR(50)", DDLUtils.convertType(v50, DBType.SQLSERVER), "常规长度保留");
		ColumnMeta v4000 = col(Types.VARCHAR);
		v4000.setColumnSize(4000);
		assertEquals("NVARCHAR(4000)", DDLUtils.convertType(v4000, DBType.SQLSERVER), "4000为NVARCHAR上限");
		ColumnMeta v8000 = col(Types.VARCHAR);
		v8000.setColumnSize(8000);
		assertEquals("NVARCHAR(MAX)", DDLUtils.convertType(v8000, DBType.SQLSERVER), "超上限按MAX承载");
		ColumnMeta nv8000 = col(Types.NVARCHAR);
		nv8000.setColumnSize(8000);
		assertEquals("NVARCHAR(MAX)", DDLUtils.convertType(nv8000, DBType.SQLSERVER), "NVARCHAR超上限同样MAX");
		// 其它库不受影响
		ColumnMeta pg8000 = col(Types.VARCHAR);
		pg8000.setColumnSize(8000);
		assertEquals("VARCHAR(8000)", DDLUtils.convertType(pg8000, DBType.POSTGRESQL), "pg不套用sqlserver上限");
	}
}
