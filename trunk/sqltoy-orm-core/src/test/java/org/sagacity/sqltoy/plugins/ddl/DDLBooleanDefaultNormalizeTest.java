package org.sagacity.sqltoy.plugins.ddl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.sql.Types;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.FieldMeta;
import org.sagacity.sqltoy.model.TableMeta;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * update 2026-10-22 boolean默认值按目标库归一的回归(数值承载方言true/false→1/0)。
 * oracle系(BOOLEAN列映射INTEGER)与sqlserver(BIT)无true/false字面量,裸DEFAULT true建表
 * 报ORA-00904/无效列名;归一挂在wrapTableMeta单一入口,全部generator的DEFAULT路径共享
 */
public class DDLBooleanDefaultNormalizeTest {

	// ---------- 数值承载方言:转1/0 ----------

	@Test
	public void numericCarrierDialectsNormalizeTo01() {
		assertEquals("1", DDLUtils.normalizeBooleanDefault("true", Types.BOOLEAN, DBType.ORACLE));
		assertEquals("0", DDLUtils.normalizeBooleanDefault("false", Types.BOOLEAN, DBType.ORACLE));
		// 大小写不敏感
		assertEquals("1", DDLUtils.normalizeBooleanDefault("TRUE", Types.BOOLEAN, DBType.ORACLE11));
		assertEquals("0", DDLUtils.normalizeBooleanDefault("False", Types.BOOLEAN, DBType.DM));
		assertEquals("1", DDLUtils.normalizeBooleanDefault("true", Types.BOOLEAN, DBType.OCEANBASE));
		assertEquals("1", DDLUtils.normalizeBooleanDefault("true", Types.BOOLEAN, DBType.SQLSERVER));
		assertEquals("0", DDLUtils.normalizeBooleanDefault("false", Types.BOOLEAN, DBType.SQLSERVER));
	}

	// ---------- 原生TRUE/FALSE字面量方言:原样保留 ----------

	@Test
	public void nativeLiteralDialectsKeep() {
		assertEquals("true", DDLUtils.normalizeBooleanDefault("true", Types.BOOLEAN, DBType.MYSQL));
		assertEquals("true", DDLUtils.normalizeBooleanDefault("true", Types.BOOLEAN, DBType.TIDB));
		assertEquals("true", DDLUtils.normalizeBooleanDefault("true", Types.BOOLEAN, DBType.POSTGRESQL));
		assertEquals("true", DDLUtils.normalizeBooleanDefault("true", Types.BOOLEAN, DBType.KINGBASE));
		assertEquals("true", DDLUtils.normalizeBooleanDefault("true", Types.BOOLEAN, DBType.H2));
		assertEquals("true", DDLUtils.normalizeBooleanDefault("true", Types.BOOLEAN, DBType.SQLITE));
	}

	// ---------- 非boolean类型/表达式/空值不受影响 ----------

	@Test
	public void nonBooleanOrExpressionUntouched() {
		// 字符串列的"true"是字符串默认值,不归一
		assertEquals("true", DDLUtils.normalizeBooleanDefault("true", Types.VARCHAR, DBType.ORACLE));
		// 生成列表达式不受影响(非精确匹配)
		assertEquals("(a>b)", DDLUtils.normalizeBooleanDefault("(a>b)", Types.BOOLEAN, DBType.ORACLE));
		// 已是数值形态原样保留
		assertEquals("1", DDLUtils.normalizeBooleanDefault("1", Types.BOOLEAN, DBType.ORACLE));
		assertNull(DDLUtils.normalizeBooleanDefault(null, Types.BOOLEAN, DBType.ORACLE));
	}

	// ---------- wrapTableMeta单一入口集成 ----------

	private FieldMeta boolField(String defValue) {
		FieldMeta fieldMeta = new FieldMeta();
		fieldMeta.setFieldName("enabled");
		fieldMeta.setColumnName("ENABLED");
		fieldMeta.setType(Types.BOOLEAN);
		fieldMeta.setFieldType("java.lang.Boolean");
		fieldMeta.setDefaultValue(defValue);
		fieldMeta.setNullable(true);
		return fieldMeta;
	}

	@Test
	public void wrapTableMetaNormalizesForOracle() {
		EntityMeta entityMeta = new EntityMeta();
		entityMeta.setTableName("t_bool");
		entityMeta.addFieldMeta(boolField("true"));
		TableMeta tableMeta = DDLUtils.wrapTableMeta(entityMeta, DBType.ORACLE);
		assertEquals("1", tableMeta.getColumns().get(0).getDefaultValue(), "oracle目标应归一为1");
		// 同一实体元数据按sqlserver目标同样归一
		TableMeta sqlServerMeta = DDLUtils.wrapTableMeta(entityMeta, DBType.SQLSERVER);
		assertEquals("1", sqlServerMeta.getColumns().get(0).getDefaultValue());
		// mysql目标保留原生字面量
		TableMeta mysqlMeta = DDLUtils.wrapTableMeta(entityMeta, DBType.MYSQL);
		assertEquals("true", mysqlMeta.getColumns().get(0).getDefaultValue());
		// dbType为null(生成脚本未指定库)时原样返回不NPE
		TableMeta nullTypeMeta = DDLUtils.wrapTableMeta(entityMeta, null);
		assertEquals("true", nullTypeMeta.getColumns().get(0).getDefaultValue());
	}
}
