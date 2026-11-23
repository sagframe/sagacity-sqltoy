package org.sagacity.sqltoy.plugins.ddl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.model.ColumnMeta;
import org.sagacity.sqltoy.plugins.ddl.DDLUtils;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * update 2026-11-21 实体数组/集合字段DDL类型映射验证:此前数组字段落default统一VARCHAR
 * (pg系仅String[]靠typeName以[]结尾碰巧生成_VARCHAR,数值/布尔数组错型;h2丢数组维度),
 * 现按ColumnMeta.arrayComponentType分派——pg内核系9库elem[]、h2的elem ARRAY,
 * 无标准数组列方言与未识别组件维持switch现状(nativeType口子优先)
 *
 * @author zsy 2026-11-21
 */
public class DDLArrayConvertTypeTest {

	private static ColumnMeta arrayCol(String componentType, Integer jdbcType, String nativeType) {
		ColumnMeta meta = new ColumnMeta();
		meta.setDataType(jdbcType == null ? java.sql.Types.OTHER : jdbcType);
		meta.setArrayComponentType(componentType);
		if (nativeType != null) {
			meta.setNativeType(nativeType);
		}
		return meta;
	}

	@Test
	public void pgFamilyArrayColumnTypes() {
		assertEquals("varchar[]", DDLUtils.convertType(arrayCol("java.lang.String", null, null), DBType.POSTGRESQL));
		assertEquals("integer[]", DDLUtils.convertType(arrayCol("java.lang.Integer", null, null), DBType.KINGBASE));
		assertEquals("bigint[]", DDLUtils.convertType(arrayCol("java.lang.Long", null, null), DBType.HIGHGO));
		assertEquals("numeric[]", DDLUtils.convertType(arrayCol("java.math.BigDecimal", null, null), DBType.OPENGAUSS));
		assertEquals("float8[]", DDLUtils.convertType(arrayCol("java.lang.Double", null, null), DBType.VASTBASE));
		assertEquals("bool[]", DDLUtils.convertType(arrayCol("java.lang.Boolean", null, null), DBType.MOGDB));
		// 原始类型组件(List<Integer>与int[]同达)
		assertEquals("integer[]", DDLUtils.convertType(arrayCol("int", null, null), DBType.POSTGRESQL));
	}

	@Test
	public void h2ArrayColumnTypes() {
		assertEquals("VARCHAR ARRAY", DDLUtils.convertType(arrayCol("java.lang.String", null, null), DBType.H2));
		assertEquals("INT ARRAY", DDLUtils.convertType(arrayCol("java.lang.Integer", null, null), DBType.H2));
	}

	@Test
	public void nativeTypePriorityOverComponent() {
		// nativeType显式声明最优先(配置了就不参与组件推断;无数组列方言的正路:dm VARRAY类型名/blob承载等)
		assertEquals("text[]",
				DDLUtils.convertType(arrayCol("java.lang.String", null, "text[]"), DBType.POSTGRESQL));
		// OTHER类型经default分支透传nativeType(mysql无数组列,由用户显式指定承载)
		assertEquals("VARCHAR(500)",
				DDLUtils.convertType(arrayCol("java.lang.String", null, "VARCHAR(500)"), DBType.MYSQL));
	}

	@Test
	public void nonArrayDialectsUseJsonCarrier() {
		// update 2026-11-24 无数组列方言以JSON/文本承载(jsonCarrierType矩阵):此前pg源text[]/
		// integer[]形态原样输出非法DDL,或裸VARCHAR在mysql/oracle缺长度非法
		assertEquals("JSON", DDLUtils.convertType(arrayCol("java.lang.String", null, null), DBType.MYSQL));
		assertEquals("JSON", DDLUtils.convertType(arrayCol("java.lang.String", null, null), DBType.ORACLE));
		assertEquals("CLOB", DDLUtils.convertType(arrayCol("java.lang.Integer", null, null), DBType.ORACLE11));
		assertEquals("CLOB", DDLUtils.convertType(arrayCol("java.math.BigDecimal", null, null), DBType.DB2));
		assertEquals("NVARCHAR(MAX)", DDLUtils.convertType(arrayCol("java.lang.String", null, null), DBType.SQLSERVER));
		assertEquals("NCLOB", DDLUtils.convertType(arrayCol("java.lang.String", null, null), DBType.HANA));
	}

	@Test
	public void arrayFamilyOnSiteFallbackWithoutField() {
		// update 2026-11-22 缺陷二回归锁死:手工构造/反向工程链路ColumnMeta不带specialFamily字段,
		// 仅nativeType=text[]时须现场兜底识别数组族——非pg目标回落JSON承载(此前原样输出非法text[])
		ColumnMeta manual = arrayCol("java.lang.String", null, "text[]");
		// arrayCol不设置specialFamily字段(手工形态)
		assertEquals("text[]", DDLUtils.convertType(manual, DBType.POSTGRESQL), "pg目标保源形态");
		assertEquals("JSON", DDLUtils.convertType(manual, DBType.MYSQL), "mysql回落JSON承载");
		assertEquals("CLOB", DDLUtils.convertType(manual, DBType.DB2), "db2回落CLOB承载");
	}

	@Test
	public void unknownComponentKeepLegacy() {
		// 未识别组件(日期时间,绑定形态未实证):pg目标回落switch(nativeType原样date[]在pg合法)
		assertEquals(true,
				DDLUtils.convertType(arrayCol("java.time.LocalDate", null, null), DBType.POSTGRESQL)
						.contains("VARCHAR"),
				"pg未识别组件回落default");
	}

	@Test
	public void arrayFamilyNativeTypeNonPgFallback() {
		// update 2026-11-22 手工构造/反向工程链路ColumnMeta不携带specialFamily字段,仅nativeType=text[]
		// (pg源数组形态)+arrayComponentType:非pg目标须按现场识别的数组族回落JSON承载,而非原样输出
		// 非法text[](与json/bson族现场兜底识别同构);pg目标仍原样text[]
		assertEquals("JSON", DDLUtils.convertType(arrayCol("java.lang.String", null, "text[]"), DBType.MYSQL));
		assertEquals("JSON", DDLUtils.convertType(arrayCol("java.lang.String", null, "text[]"), DBType.DM));
		assertEquals("CLOB", DDLUtils.convertType(arrayCol("java.lang.Integer", null, "integer[]"), DBType.DB2));
		assertEquals("text[]", DDLUtils.convertType(arrayCol("java.lang.String", null, "text[]"), DBType.POSTGRESQL));
	}
}
