package org.sagacity.sqltoy.dialect.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.FieldMeta;
import org.sagacity.sqltoy.config.model.NotGeneratedColMeta;
import org.sagacity.sqltoy.config.model.PKStrategy;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.plugins.id.macro.impl.Include;
import org.sagacity.sqltoy.utils.DataSourceUtils;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;
import org.sagacity.sqltoy.utils.DataSourceUtils.Dialect;

/**
 * 回归测试：方言相关缺陷修复
 * 1) getDialect(Integer)补KINGBASE映射(修复前退化为undefine导致方言变体sql失效);
 * 2) @include宏的方言变体查找补齐oracle11/postgresql14/mysql57回退(与getSqlConfig查找链一致);
 * 3) pg14的insertIgnore在identity主键下省略主键列,配合saveAll的rejectId反射保持参数对齐
 */
public class DialectDefectsFixTest {

	/**
	 * kingbase的dbType应映射成kingbase方言常量(修复前落入default返回undefine)
	 */
	@Test
	public void kingbaseDbTypeMapsToDialect() {
		assertEquals(Dialect.KINGBASE, DataSourceUtils.getDialect(DBType.KINGBASE));
	}

	/**
	 * @include宏查找:oracle11回退到_oracle命名
	 */
	@Test
	public void includeFallbackOracle11() throws Exception {
		SqlToyConfig variant = new SqlToyConfig("y_oracle", "select 1 as oracle_sql");
		Map<String, Object> cache = new HashMap<String, Object>();
		cache.put("y_oracle", variant);
		assertSame(variant, invokeIncludeLookup(cache, "y", "oracle11"));
	}

	/**
	 * @include宏查找:postgresql14回退到_postgresql命名
	 */
	@Test
	public void includeFallbackPostgresql14() throws Exception {
		SqlToyConfig variant = new SqlToyConfig("y_postgresql", "select 1 as pg_sql");
		Map<String, Object> cache = new HashMap<String, Object>();
		cache.put("y_postgresql", variant);
		assertSame(variant, invokeIncludeLookup(cache, "y", "postgresql14"));
	}

	/**
	 * @include宏查找:mysql57回退到_mysql命名
	 */
	@Test
	public void includeFallbackMysql57() throws Exception {
		SqlToyConfig variant = new SqlToyConfig("y_mysql", "select 1 as mysql_sql");
		Map<String, Object> cache = new HashMap<String, Object>();
		cache.put("y_mysql", variant);
		assertSame(variant, invokeIncludeLookup(cache, "y", "mysql57"));
	}

	/**
	 * @include宏查找:精确版本变体优先于回退变体
	 */
	@Test
	public void includeExactVariantTakesPrecedence() throws Exception {
		SqlToyConfig exact = new SqlToyConfig("y_mysql57", "select 1 as mysql57_exact");
		SqlToyConfig base = new SqlToyConfig("y_mysql", "select 1 as mysql_base");
		Map<String, Object> cache = new HashMap<String, Object>();
		cache.put("y_mysql57", exact);
		cache.put("y_mysql", base);
		assertSame(exact, invokeIncludeLookup(cache, "y", "mysql57"));
	}

	/**
	 * pg14+identity主键:insertIgnore省略主键列,?数量与saveAll的rejectId反射参数对齐
	 * (修复前经saveAllIgnoreExist全字段反射,参数比?多一个导致绑定越界)
	 */
	@Test
	public void insertIgnoreIdentityOnPg14() {
		EntityMeta meta = buildEntityMeta();
		String sql = DialectExtUtils.insertIgnore(null, DBType.POSTGRESQL14, meta, PKStrategy.IDENTITY, "COALESCE",
				"nextval('seq')", false, null);
		String lowerSql = sql.toLowerCase();
		// identity主键列整体省略,由数据库生成;且不能出现非法的COALESCE(?,DEFAULT)形态
		assertTrue(!lowerSql.contains("coalesce(?,default)"), "COALESCE(?,DEFAULT) is invalid pg sql, got: " + sql);
		assertTrue(!lowerSql.contains("default"), "identity should not reference DEFAULT, got: " + sql);
		assertEquals(1, countPlaceholders(sql), "only name column holds placeholder, got: " + sql);
	}

	/**
	 * pg14+sequence主键:主键列以COALESCE(?,nextval)呈现,?数量与saveAll全字段反射参数对齐
	 */
	@Test
	public void insertIgnoreSequenceOnPg14() {
		EntityMeta meta = buildEntityMeta();
		String sql = DialectExtUtils.insertIgnore(null, DBType.POSTGRESQL14, meta, PKStrategy.SEQUENCE, "COALESCE",
				"nextval('seq')", true, null);
		String lowerSql = sql.toLowerCase();
		assertTrue(lowerSql.contains("coalesce(?,nextval('seq'))"), "sequence pk should render COALESCE(?,nextval), got: " + sql);
		assertEquals(2, countPlaceholders(sql), "name and id both hold placeholders, got: " + sql);
	}

	private SqlToyConfig invokeIncludeLookup(Map<String, Object> cache, String sqlId, String dialect) throws Exception {
		Include include = new Include();
		Method method = Include.class.getDeclaredMethod("getSqlToyConfig", Map.class, String.class, String.class);
		method.setAccessible(true);
		return (SqlToyConfig) method.invoke(include, cache, sqlId, dialect);
	}

	private EntityMeta buildEntityMeta() {
		EntityMeta meta = new EntityMeta();
		meta.setEntityClass(getClass());
		meta.setTableName("t_pg14");
		meta.addFieldMeta(new FieldMeta("name", "name", null, null, java.sql.Types.VARCHAR, true, false, 50, 0, 0));
		// insertIgnore按fieldMeta.isPK()分派主键策略分支,必须显式标记
		FieldMeta idMeta = new FieldMeta("id", "id", null, null, java.sql.Types.BIGINT, false, false, 19, 0, 0);
		idMeta.setPK(true);
		meta.addFieldMeta(idMeta);
		String[] fieldsArray = { "name", "id" };
		String[] rejectIdFields = { "name" };
		meta.setIdArray(new String[] { "id" });
		meta.setFieldsArray(fieldsArray);
		meta.setRejectIdFieldArray(rejectIdFields);
		NotGeneratedColMeta notGenerated = new NotGeneratedColMeta();
		notGenerated.setFieldsArray(fieldsArray);
		notGenerated.setRejectIdFieldArray(rejectIdFields);
		meta.setNotGeneratedColMeta(notGenerated);
		return meta;
	}

	private int countPlaceholders(String sql) {
		return sql.length() - sql.replace("?", "").length();
	}
}
