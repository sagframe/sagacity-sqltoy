package org.sagacity.sqltoy.dialect.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.FieldMeta;

/**
 * update 2026-11-21 merge using-select数组列cast定型验证(pg系/openGauss系/h2三方言):
 * 优先链 nativeType显式声明 > 按arrayComponentType组件cast > 裸?兜底(未识别组件),
 * cast元素类型名与SqlUtil.setArray的createArrayOf口径对齐(h2为h2类型名体系)
 *
 * @author zsy 2026-11-21
 */
public class WrapSelectFieldsArrayCastTest {

	private static FieldMeta meta(String componentType, String nativeType) {
		FieldMeta meta = new FieldMeta();
		meta.setType(java.sql.Types.OTHER);
		meta.setArrayComponentType(componentType);
		if (nativeType != null) {
			meta.setNativeType(nativeType);
		}
		return meta;
	}

	private static String wrapPg(String componentType, String nativeType) {
		StringBuilder sql = new StringBuilder();
		PostgreSqlDialectUtils.wrapSelectFields(sql, "tags", meta(componentType, nativeType));
		return sql.toString();
	}

	private static String wrapH2(String componentType, String nativeType) {
		StringBuilder sql = new StringBuilder();
		H2DialectUtils.wrapSelectFields(sql, "tags", meta(componentType, nativeType));
		return sql.toString();
	}

	@Test
	public void pgFamilyArrayCastForms() {
		// 定型数组/集合组件 → pg元素类型cast(与createArrayOf口径一致)
		assertEquals("cast(? as varchar[]) as tags", wrapPg("java.lang.String", null));
		assertEquals("cast(? as integer[]) as tags", wrapPg("java.lang.Integer", null));
		assertEquals("cast(? as bigint[]) as tags", wrapPg("java.lang.Long", null));
		assertEquals("cast(? as numeric[]) as tags", wrapPg("java.math.BigDecimal", null));
		assertEquals("cast(? as float8[]) as tags", wrapPg("java.lang.Double", null));
		assertEquals("cast(? as bool[]) as tags", wrapPg("java.lang.Boolean", null));
		assertEquals("cast(? as int2[]) as tags", wrapPg("java.lang.Short", null));
		// 原始类型组件同样映射
		assertEquals("cast(? as integer[]) as tags", wrapPg("int", null));
	}

	@Test
	public void priorityNativeTypeOverComponentCast() {
		// nativeType显式声明最优先(用户精确控制,可表达text[]/varchar(50)[]等cast表达不了的形态)
		assertEquals("cast(? as text[]) as tags", wrapPg("java.lang.String", "text[]"));
		assertEquals("cast(? as varchar array) as tags", wrapH2("java.lang.String", "varchar array"));
	}

	@Test
	public void unmappedComponentFallsBackToBareParam() {
		// 未识别组件(日期时间等,绑定形态未实证)维持裸?现状
		assertEquals("? as tags", wrapPg("java.time.LocalDate", null));
		assertEquals("? as tags", wrapPg(null, null));
		assertEquals("? as tags", wrapH2("java.time.LocalDate", null));
	}

	@Test
	public void openGaussFamilySharesPgMapping() {
		StringBuilder sql = new StringBuilder();
		FieldMeta meta = meta("java.lang.String", null);
		// og的wrapSelectFields按profile分派oscar特例,须传真实DBProfile
		org.sagacity.sqltoy.model.DBProfile profile = new org.sagacity.sqltoy.model.DBProfile(null, "opengauss",
				org.sagacity.sqltoy.utils.DataSourceUtils.DBType.OPENGAUSS, null, 0, null, null, false);
		OpenGaussDialectUtils.wrapSelectFields(sql, "tags", meta, profile);
		assertEquals("cast(? as varchar[]) as tags", sql.toString());
	}

	@Test
	public void h2ArrayCastForms() {
		assertEquals("cast(? as VARCHAR ARRAY) as tags", wrapH2("java.lang.String", null));
		assertEquals("cast(? as INT ARRAY) as tags", wrapH2("java.lang.Integer", null));
	}

	@Test
	public void timestampWithTimezoneWrapFormsAcrossDialects() {
		// update 2026-11-22 2014(jdbcType=TIMESTAMP_WITH_TIMEZONE)五库using-select cast形态:
		// pg/og内核timestamptz、h2的TIMESTAMP WITH TIME ZONE、dm兼容oracle形态、db2降级TIMESTAMP
		// ——此前无分支落else以nativeType(timestamptz)原样cast,h2/dm/db2报Unknown data type(真库实证)
		FieldMeta meta = new FieldMeta();
		meta.setType(java.sql.Types.TIMESTAMP_WITH_TIMEZONE);
		meta.setNativeType("timestamptz");
		StringBuilder pg = new StringBuilder();
		PostgreSqlDialectUtils.wrapSelectFields(pg, "ts_col", meta);
		assertEquals("cast(? as timestamptz) as ts_col", pg.toString());
		org.sagacity.sqltoy.model.DBProfile ogProfile = new org.sagacity.sqltoy.model.DBProfile(null, "opengauss",
				org.sagacity.sqltoy.utils.DataSourceUtils.DBType.OPENGAUSS, null, 0, null, null, false);
		StringBuilder og = new StringBuilder();
		OpenGaussDialectUtils.wrapSelectFields(og, "ts_col", meta, ogProfile);
		assertEquals("cast(? as timestamptz) as ts_col", og.toString());
		StringBuilder h2 = new StringBuilder();
		H2DialectUtils.wrapSelectFields(h2, "ts_col", meta);
		assertEquals("cast(? as TIMESTAMP WITH TIME ZONE) as ts_col", h2.toString());
		StringBuilder dm = new StringBuilder();
		DMDialectUtils.wrapSelectFields(dm, "ts_col", meta);
		assertEquals("cast(? as TIMESTAMP WITH TIME ZONE) as ts_col", dm.toString());
		StringBuilder db2 = new StringBuilder();
		DB2DialectUtils.wrapSelectFields(db2, "ts_col", meta);
		assertEquals("cast(? as TIMESTAMP) as ts_col", db2.toString());
	}

	@Test
	public void uuidJdbcTypeWrapFormsAcrossDialects() {
		// update 2026-11-22 uuid列(族parse期上调可达jdbcType=UUID)五库cast形态:
		// pg/og内核cast(? as uuid)、h2 cast(? as UUID)、dm/db2为varchar(36)承载列裸?(防else的
		// nativeType原样cast在dm/db2非法)
		FieldMeta meta = new FieldMeta();
		meta.setType(org.sagacity.sqltoy.model.JdbcTypes.UUID);
		meta.setNativeType("uuid");
		StringBuilder pg = new StringBuilder();
		PostgreSqlDialectUtils.wrapSelectFields(pg, "uid_col", meta);
		assertEquals("cast(? as uuid) as uid_col", pg.toString());
		org.sagacity.sqltoy.model.DBProfile ogProfile = new org.sagacity.sqltoy.model.DBProfile(null, "opengauss",
				org.sagacity.sqltoy.utils.DataSourceUtils.DBType.OPENGAUSS, null, 0, null, null, false);
		StringBuilder og = new StringBuilder();
		OpenGaussDialectUtils.wrapSelectFields(og, "uid_col", meta, ogProfile);
		assertEquals("cast(? as uuid) as uid_col", og.toString());
		StringBuilder h2 = new StringBuilder();
		H2DialectUtils.wrapSelectFields(h2, "uid_col", meta);
		assertEquals("cast(? as UUID) as uid_col", h2.toString());
		StringBuilder dm = new StringBuilder();
		DMDialectUtils.wrapSelectFields(dm, "uid_col", meta);
		assertEquals("? as uid_col", dm.toString(), "dm的uuid承载列裸?");
		StringBuilder db2 = new StringBuilder();
		DB2DialectUtils.wrapSelectFields(db2, "uid_col", meta);
		assertEquals("? as uid_col", db2.toString(), "db2的uuid承载列裸?");
	}
}