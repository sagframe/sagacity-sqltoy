package org.sagacity.sqltoy.dialect.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.FieldMeta;
import org.sagacity.sqltoy.model.JdbcTypes;
import org.sagacity.sqltoy.utils.NativeTypeUtils;

/**
 * update 2026-11-22 geometry族merge using-select消费点回归:特殊族前置翻译不得 bypass各库
 * 专属geometry函数包装。DM实测cast(? as ST_GEOMETRY)报-6801、DB2内置形态cast(? as ST_GEOMETRY)
 * 报-408,正路DMGEO.ST_GeomFromText/db2GeomFromTextWrap才正确——故geometry族在DM/DB2须回落null
 * 交由jdbcType=GEOMETRY分支;family-set与family-null产物须一致(前置不改变正路)
 *
 * @author zsy 2026-11-22
 */
public class WrapSelectFieldsGeometryTest {

	private static FieldMeta meta(String nativeType, boolean withFamily) {
		FieldMeta meta = new FieldMeta();
		meta.setType(JdbcTypes.GEOMETRY);
		meta.setNativeType(nativeType);
		if (withFamily) {
			meta.setSpecialFamily(NativeTypeUtils.specialFamily(nativeType, JdbcTypes.GEOMETRY));
		}
		return meta;
	}

	private static String dm(String nativeType, boolean withFamily) {
		StringBuilder sql = new StringBuilder();
		DMDialectUtils.wrapSelectFields(sql, "geom", meta(nativeType, withFamily));
		return sql.toString();
	}

	private static String db2(String nativeType, boolean withFamily) {
		StringBuilder sql = new StringBuilder();
		DB2DialectUtils.wrapSelectFields(sql, "geom", meta(nativeType, withFamily));
		return sql.toString();
	}

	@Test
	public void dmGeometryDefersToFunctionWrap() {
		// family-set不得bypass DMGEO函数包装(实测cast -6801)
		assertEquals("DMGEO.ST_GeomFromText(?,0) as geom", dm("ST_GEOMETRY", true));
		assertEquals("DMGEO.ST_GeomFromText(?,0) as geom", dm("ST_GEOMETRY", false));
		assertEquals("DMGEO.ST_GeomFromText(?,0) as geom", dm(null, true));
	}

	@Test
	public void db2GeometryDefersToFunctionWrap() {
		// family-set不得bypass db2GeomFromTextWrap(实测内置形态cast -408);无连接档案默认db2gse形态
		assertEquals("db2gse.ST_GeomFromText(?,0) as geom", db2("db2gse.st_geometry", true));
		assertEquals("db2gse.ST_GeomFromText(?,0) as geom", db2("db2gse.st_geometry", false));
		assertEquals("db2gse.ST_GeomFromText(?,0) as geom", db2("ST_GEOMETRY", true));
	}

	@Test
	public void pgAndH2GeometryCastUnchanged() {
		// pg/h2的geometry正路即cast,前置翻译产物与jdbcType分支一致(无回归)
		StringBuilder pg = new StringBuilder();
		PostgreSqlDialectUtils.wrapSelectFields(pg, "geom", meta("geometry", true));
		assertEquals("cast(? as geometry) as geom", pg.toString());
		StringBuilder h2 = new StringBuilder();
		H2DialectUtils.wrapSelectFields(h2, "geom", meta("geometry", true));
		assertEquals("cast(? as GEOMETRY) as geom", h2.toString());
	}
}