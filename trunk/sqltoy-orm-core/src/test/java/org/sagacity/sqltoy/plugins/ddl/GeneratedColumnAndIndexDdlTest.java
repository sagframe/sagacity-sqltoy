package org.sagacity.sqltoy.plugins.ddl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.GeneratedType;
import org.sagacity.sqltoy.config.model.IndexModel;
import org.sagacity.sqltoy.model.ColumnMeta;
import org.sagacity.sqltoy.model.TableMeta;
import org.sagacity.sqltoy.plugins.ddl.impl.DB2DDLGenerator;
import org.sagacity.sqltoy.plugins.ddl.impl.MySqlDDLGenerator;
import org.sagacity.sqltoy.plugins.ddl.impl.OracleDDLGenerator;
import org.sagacity.sqltoy.plugins.ddl.impl.PostgreSqlDDLGenerator;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * update 2026-11-11 生成列GENERATED子句与索引DESC方向的跨工程信息保真测试
 * (quickvo端信息完整、sqltoy消费端不得丢弃——回灌表须保留计算语义与索引方向):
 * <ol>
 * <li>生成列:@Column(generatedType=STORED/VIRTUAL) + 表达式(经defaultValue承载)
 * →wrapTableMeta→各generator的GENERATED ALWAYS AS子句还原</li>
 * <li>索引方向:IndexModel.sortTypes(DESC/ASC)在MySQL自有索引路径(KEY前缀)的还原</li>
 * </ol>
 */
public class GeneratedColumnAndIndexDdlTest {

	private static TableMeta buildMeta() {
		TableMeta table = new TableMeta();
		table.setTableName("gen_idx_t");
		List<ColumnMeta> cols = new ArrayList<ColumnMeta>();
		ColumnMeta id = new ColumnMeta();
		id.setColName("id");
		id.setDataType(java.sql.Types.BIGINT);
		id.setPK(true);
		id.setNullable(false);
		cols.add(id);
		ColumnMeta balance = new ColumnMeta();
		balance.setColName("balance");
		balance.setDataType(java.sql.Types.DECIMAL);
		cols.add(balance);
		ColumnMeta genAmt = new ColumnMeta();
		genAmt.setColName("gen_amt");
		genAmt.setDataType(java.sql.Types.DECIMAL);
		// quickvo契约:生成列表达式经defaultValue承载
		genAmt.setDefaultValue("balance*2");
		genAmt.setGeneratedType(GeneratedType.STORED.getValue());
		cols.add(genAmt);
		ColumnMeta genVirtual = new ColumnMeta();
		genVirtual.setColName("gen_virtual");
		genVirtual.setDataType(java.sql.Types.DECIMAL);
		genVirtual.setDefaultValue("balance+1");
		genVirtual.setGeneratedType(GeneratedType.VIRTUAL.getValue());
		cols.add(genVirtual);
		table.setColumns(cols);
		List<IndexModel> indexes = new ArrayList<IndexModel>();
		indexes.add(new IndexModel("idx_gen_desc", false, new String[] { "balance", "gen_amt" },
				new String[] { "DESC", "ASC" }));
		table.setIndexes(indexes);
		return table;
	}

	@Test
	public void db2GeneratedColumnsSurviveRoundTrip() {
		String ddl = new DB2DDLGenerator().createTableSql(buildMeta(), null, null, DBType.DB2);
		assertTrue(ddl.contains("GENERATED ALWAYS AS (balance*2)"), "DB2 STORED生成列须还原GENERATED子句: " + ddl);
		// DB2生成列即存储形态,不区分STORED/VIRTUAL关键字
		assertTrue(ddl.contains("GENERATED ALWAYS AS (balance+1)"), "DB2第二生成列: " + ddl);
	}

	@Test
	public void mysqlGeneratedColumnsSurviveRoundTrip() {
		String ddl = new MySqlDDLGenerator().createTableSql(buildMeta(), null, null, DBType.MYSQL);
		assertTrue(ddl.contains("GENERATED ALWAYS AS (balance*2)") && ddl.contains("STORED"), "MySQL STORED生成列: " + ddl);
		assertTrue(ddl.contains("GENERATED ALWAYS AS (balance+1)") && ddl.contains("VIRTUAL"), "MySQL VIRTUAL生成列: " + ddl);
	}

	@Test
	public void oracleDmGeneratedColumnsSurviveRoundTrip() {
		String ddl = new OracleDDLGenerator().createTableSql(buildMeta(), null, null, DBType.ORACLE);
		// Oracle生成列为虚拟列(12c+),无STORED形态
		assertTrue(ddl.contains("GENERATED ALWAYS AS (balance*2)"), "Oracle生成列: " + ddl);
	}

	@Test
	public void postgreSqlGeneratedColumnsSurviveRoundTrip() {
		String ddl = new PostgreSqlDDLGenerator().createTableSql(buildMeta(), null, null, DBType.POSTGRESQL);
		// PG仅支持STORED生成列
		assertTrue(ddl.contains("GENERATED ALWAYS AS (balance*2)"), "PG STORED生成列: " + ddl);
	}

	@Test
	public void mysqlIndexDirectionSurvivesRoundTrip() {
		String ddl = new MySqlDDLGenerator().createTableSql(buildMeta(), null, null, DBType.MYSQL);
		assertTrue(ddl.contains("KEY idx_gen_desc (balance DESC,gen_amt ASC)"),
				"MySQL索引KEY路径须还原sortTypes方向: " + ddl);
	}

	/**
	 * wrapTableMeta级端到端锁:EntityMeta/FieldMeta(如EntityManager从@Column解析所得)经
	 * wrapTableMeta转换后generatedType与表达式(defaultValue承载)不得丢失——跨工程回灌契约
	 */
	@Test
	public void wrapTableMetaKeepsGeneratedTypeAndExpression() {
		EntityMeta entityMeta = new EntityMeta();
		entityMeta.setTableName("gen_idx_t");
		java.util.HashMap<String, org.sagacity.sqltoy.config.model.FieldMeta> fieldsMeta = new java.util.HashMap<String, org.sagacity.sqltoy.config.model.FieldMeta>();
		org.sagacity.sqltoy.config.model.FieldMeta genField = new org.sagacity.sqltoy.config.model.FieldMeta();
		genField.setColumnName("gen_amt");
		genField.setType(java.sql.Types.DECIMAL);
		genField.setDefaultValue("balance*2");
		genField.setGeneratedType(GeneratedType.STORED.getValue());
		fieldsMeta.put("genAmt", genField);
		entityMeta.setFieldsMeta(fieldsMeta);
		TableMeta tableMeta = DDLUtils.wrapTableMeta(entityMeta, DBType.DB2);
		ColumnMeta column = null;
		for (ColumnMeta col : tableMeta.getColumns()) {
			if ("gen_amt".equals(col.getColName())) {
				column = col;
			}
		}
		assertTrue(column != null && column.getGeneratedType() == GeneratedType.STORED.getValue(),
				"wrapTableMeta须传播generatedType");
		assertTrue(column != null && "balance*2".equals(column.getDefaultValue()),
				"wrapTableMeta须传播表达式(defaultValue承载)");
		String ddl = new DB2DDLGenerator().createTableSql(tableMeta, null, null, DBType.DB2);
		assertTrue(ddl.contains("GENERATED ALWAYS AS (balance*2)"), "经wrapTableMeta的DB2生成列须还原: " + ddl);
	}

	@Test
	public void ddlUtilsWrapIndexesKeepsDirectionAsControl() {
		// 对照组:DDLUtils共享路径(wrapTableIndexes)本就消费sortTypes
		StringBuilder sql = new StringBuilder();
		DDLUtils.wrapTableIndexes(buildMeta(), null, DBType.MYSQL, sql, false);
		assertTrue(sql.toString().contains("balance DESC"), "共享索引路径须还原方向: " + sql);
		assertFalse(sql.toString().contains("balance,balance"), "无重复列");
	}
}
