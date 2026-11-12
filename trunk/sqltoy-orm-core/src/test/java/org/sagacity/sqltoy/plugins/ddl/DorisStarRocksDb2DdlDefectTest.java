package org.sagacity.sqltoy.plugins.ddl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.MppTableMeta;
import org.sagacity.sqltoy.model.ColumnMeta;
import org.sagacity.sqltoy.model.TableMeta;
import org.sagacity.sqltoy.plugins.ddl.impl.DB2DDLGenerator;
import org.sagacity.sqltoy.plugins.ddl.impl.DorisDDLGenerator;
import org.sagacity.sqltoy.plugins.ddl.impl.StarRocksDDLGenerator;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * 2026-11-10 真库矩阵暴露的三处DDL生成缺陷回归:
 * <ol>
 * <li>DB2:主键列未显式NOT NULL,建表报SQLCODE=-542/SQLSTATE=42831(真库实测)</li>
 * <li>Doris/StarRocks:LocalDateTime映射TIMESTAMP,两库均无该类型(真库实测Doris解析器期望
 * DATETIME/TIMESTAMPTZ、StarRocks语法错)</li>
 * <li>Doris/StarRocks:无@MppTable时输出列内联PRIMARY KEY,两库均不支持(真库实测语法错);
 * 应改为键模型(Doris UNIQUE KEY+hash分布,StarRocks PRIMARY KEY)</li>
 * </ol>
 */
public class DorisStarRocksDb2DdlDefectTest {

	private static ColumnMeta col(String name, int dataType, boolean pk, boolean nullable) {
		ColumnMeta col = new ColumnMeta();
		col.setColName(name);
		col.setDataType(dataType);
		col.setPK(pk);
		col.setNullable(nullable);
		return col;
	}

	private static TableMeta table(boolean withPk) {
		TableMeta table = new TableMeta();
		table.setTableName("t_ddl_probe");
		List<ColumnMeta> cols = new ArrayList<>();
		cols.add(col("id", Types.BIGINT, withPk, true));
		ColumnMeta vc = col("vc_col", Types.VARCHAR, false, true);
		vc.setColumnSize(50);
		cols.add(vc);
		cols.add(col("ts_col", Types.TIMESTAMP, false, true));
		table.setColumns(cols);
		return table;
	}

	private static TableMeta withMpp(String keyModel, String... orderBy) {
		TableMeta table = table(true);
		MppTableMeta mpp = new MppTableMeta(null, null, keyModel, orderBy, null, -1, null);
		table.setMppTableMeta(mpp);
		return table;
	}

	// ==================== DB2 主键NOT NULL ====================

	@Test
	public void db2PrimaryKeyColumnGetsNotNull() {
		String ddl = new DB2DDLGenerator().createTableSql(table(true), null, null, DBType.DB2);
		assertTrue(ddl.contains("PRIMARY KEY (id)"), ddl);
		String idLine = null;
		for (String line : ddl.split("\\r?\\n")) {
			if (line.trim().startsWith("id ")) {
				idLine = line;
			}
		}
		assertNotNull(idLine, ddl);
		assertTrue(idLine.contains("NOT NULL"), "DB2主键列必须NOT NULL,否则-542: " + idLine);
		// 非主键列保持可空(不被误加NOT NULL)
		assertFalse(ddl.contains("vc_col VARCHAR(50) NOT NULL"), ddl);
	}

	// ==================== Doris/StarRocks 时间类型 ====================

	@Test
	public void dorisAndStarRocksTimestampMapsToDatetime() {
		ColumnMeta ts = col("ts_col", Types.TIMESTAMP, false, true);
		assertEquals("DATETIME", DDLUtils.convertType(ts, DBType.DORIS), "doris无TIMESTAMP类型");
		assertEquals("DATETIME", DDLUtils.convertType(ts, DBType.STARROCKS), "starrocks无TIMESTAMP类型");
		// 其它方言不变
		assertEquals("TIMESTAMP", DDLUtils.convertType(ts, DBType.POSTGRESQL));
		assertEquals("DATETIME2", DDLUtils.convertType(ts, DBType.SQLSERVER));
	}

	@Test
	public void dorisKeepsTimeZoneStarRocksDegrades() {
		ColumnMeta ts = col("ts_col", Types.TIMESTAMP_WITH_TIMEZONE, false, true);
		// Doris原生TIMESTAMPTZ(真库实测可建表);StarRocks无TIMESTAMPTZ(真库实测语法错)降级DATETIME
		assertEquals("TIMESTAMPTZ", DDLUtils.convertType(ts, DBType.DORIS));
		assertEquals("DATETIME", DDLUtils.convertType(ts, DBType.STARROCKS));
	}

	// ==================== Doris/StarRocks 键模型 ====================

	@Test
	public void dorisDerivesUniqueKeyInsteadOfInlinePrimaryKey() {
		String ddl = new DorisDDLGenerator().createTableSql(table(true), null, null, DBType.DORIS);
		assertFalse(ddl.contains("PRIMARY KEY"), "Doris不支持PRIMARY KEY: " + ddl);
		assertFalse(ddl.contains(", PRIMARY KEY"), "不得输出列内联主键: " + ddl);
		assertTrue(ddl.contains("UNIQUE KEY(id)"), ddl);
		// UNIQUE KEY要求hash分布(默认随机分布真库实测报错),缺省按键列hash
		assertTrue(ddl.contains("DISTRIBUTED BY HASH(id)"), ddl);
		assertTrue(ddl.contains("ts_col DATETIME"), ddl);
	}

	@Test
	public void starRocksDerivesPrimaryKeyClause() {
		String ddl = new StarRocksDDLGenerator().createTableSql(table(true), null, null, DBType.STARROCKS);
		assertFalse(ddl.contains(", PRIMARY KEY"), "不得输出列内联主键: " + ddl);
		assertTrue(ddl.contains("PRIMARY KEY(id)"), ddl);
		// StarRocks的PRIMARY KEY允许默认分布,不强制DISTRIBUTED BY(真库实测)
		assertFalse(ddl.contains("DISTRIBUTED BY"), ddl);
		assertTrue(ddl.contains("ts_col DATETIME"), ddl);
	}

	@Test
	public void noPrimaryKeyKeepsPlainTable() {
		String doris = new DorisDDLGenerator().createTableSql(table(false), null, null, DBType.DORIS);
		assertFalse(doris.contains("UNIQUE KEY"), doris);
		assertFalse(doris.contains("PRIMARY KEY"), doris);
		String starrocks = new StarRocksDDLGenerator().createTableSql(table(false), null, null, DBType.STARROCKS);
		assertFalse(starrocks.contains("PRIMARY KEY"), starrocks);
	}

	@Test
	public void mppKeyModelWithoutDistributionGetsDefaultHash() {
		// UNIQUE键模型未显式指定distribution:缺省按键列hash(Doris/StarRocks默认随机分布均报错)
		String doris = new DorisDDLGenerator().createTableSql(withMpp("UNIQUE", "id"), null, null, DBType.DORIS);
		assertTrue(doris.contains("UNIQUE KEY(id)"), doris);
		assertTrue(doris.contains("DISTRIBUTED BY HASH(id)"), doris);
		String starrocks = new StarRocksDDLGenerator().createTableSql(withMpp("UNIQUE", "id"), null, null,
				DBType.STARROCKS);
		assertTrue(starrocks.contains("UNIQUE KEY(id)"), starrocks);
		assertTrue(starrocks.contains("DISTRIBUTED BY HASH(id)"), starrocks);
	}

	@Test
	public void duplicatesAndStarRocksPrimaryKeepDefaultDistribution() {
		// DUPLICATE键模型允许默认分布
		String dorisDup = new DorisDDLGenerator().createTableSql(withMpp("DUPLICATE", "id"), null, null, DBType.DORIS);
		assertTrue(dorisDup.contains("DUPLICATE KEY(id)"), dorisDup);
		assertFalse(dorisDup.contains("DISTRIBUTED BY"), dorisDup);
		// StarRocks的PRIMARY允许默认分布
		String srPrimary = new StarRocksDDLGenerator().createTableSql(withMpp("PRIMARY", "id"), null, null,
				DBType.STARROCKS);
		assertTrue(srPrimary.contains("PRIMARY KEY(id)"), srPrimary);
		assertFalse(srPrimary.contains("DISTRIBUTED BY"), srPrimary);
	}

	@Test
	public void keyColumnNotFirstGetsReorderedToSchemaPrefix() {
		// update 2026-11-11 Doris/StarRocks均要求键列是schema有序前缀(真库实测非前缀报
		// "Key columns should be a ordered prefix"/"must be the first few columns");
		// 实体@Id列声明在非首位时,生成DDL须把键列前置
		TableMeta table = new TableMeta();
		table.setTableName("t_ddl_reorder");
		List<ColumnMeta> cols = new ArrayList<>();
		ColumnMeta name = col("name", Types.VARCHAR, false, true);
		name.setColumnSize(50);
		cols.add(name);
		cols.add(col("id", Types.BIGINT, true, true));
		cols.add(col("ts_col", Types.TIMESTAMP, false, true));
		table.setColumns(cols);
		String doris = new DorisDDLGenerator().createTableSql(table, null, null, DBType.DORIS);
		// id必须先于name出现(键列前置)
		int idIdx = doris.indexOf("id");
		int nameIdx = doris.indexOf("name");
		assertTrue(idIdx >= 0 && nameIdx >= 0 && idIdx < nameIdx, "键列须前置: " + doris);
		assertTrue(doris.contains("UNIQUE KEY(id)"), doris);
		String starrocks = new StarRocksDDLGenerator().createTableSql(table, null, null, DBType.STARROCKS);
		int idIdx2 = starrocks.indexOf("id");
		int nameIdx2 = starrocks.indexOf("name");
		assertTrue(idIdx2 >= 0 && nameIdx2 >= 0 && idIdx2 < nameIdx2, "键列须前置: " + starrocks);
		assertTrue(starrocks.contains("PRIMARY KEY(id)"), starrocks);
		// 键列前置后其余列保持原始相对顺序(ts_col仍在name之后)
		int tsIdx = doris.indexOf("ts_col");
		assertTrue(tsIdx > nameIdx, "非键列保持原序: " + doris);
	}

	@Test
	public void mppOrderByCaseMismatchStillFrontsKeyColumn() {
		// update 2026-11-11 注解orderBy是用户原文、列名来自实体映射,大小写错配时等值匹配
		// 漏前置会被Doris/SR以"非有序前缀"拒表,匹配须大小写不敏感
		TableMeta table = new TableMeta();
		table.setTableName("t_ddl_case");
		List<ColumnMeta> cols = new ArrayList<>();
		ColumnMeta name = col("name", Types.VARCHAR, false, true);
		name.setColumnSize(50);
		cols.add(name);
		cols.add(col("id", Types.BIGINT, false, true));
		table.setColumns(cols);
		MppTableMeta mpp = new MppTableMeta(null, null, "UNIQUE", new String[] { "ID" }, null, -1, null);
		table.setMppTableMeta(mpp);
		String doris = new DorisDDLGenerator().createTableSql(table, null, null, DBType.DORIS);
		int idIdx = doris.indexOf("id");
		int nameIdx = doris.indexOf("name");
		assertTrue(idIdx >= 0 && nameIdx >= 0 && idIdx < nameIdx, "大小写错配仍须前置: " + doris);
		assertTrue(doris.contains("UNIQUE KEY(ID)"), doris);
	}
}
