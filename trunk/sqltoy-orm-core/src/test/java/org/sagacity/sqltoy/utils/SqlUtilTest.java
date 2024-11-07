package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.SqlConfigParseUtils;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.FieldMeta;

import com.alibaba.fastjson2.JSON;

public class SqlUtilTest {

	@Test
	public void testConvertFieldsToCols() {
		String sql = "staffName,`sexType`,name,bizStaffName from table where #[t.staffName like ?] and sexType=:sexType";
		EntityMeta entityMeta = new EntityMeta();
		entityMeta.setTableName("staff_info");
		HashMap<String, FieldMeta> fieldsMeta = new HashMap<String, FieldMeta>();
		FieldMeta staffMeta = new FieldMeta();
		staffMeta.setFieldName("staffName");
		staffMeta.setColumnName("STAFF_NAME");
		fieldsMeta.put("staffname", staffMeta);

		FieldMeta bizStaffMeta = new FieldMeta();
		bizStaffMeta.setFieldName("bizStaffName");
		bizStaffMeta.setColumnName("BIZ_STAFF_NAME");
		fieldsMeta.put("bizstaffname", bizStaffMeta);

		FieldMeta nameMeta = new FieldMeta();
		nameMeta.setFieldName("name");
		nameMeta.setColumnName("NAME");
		fieldsMeta.put("name", nameMeta);

		FieldMeta sexMeta = new FieldMeta();
		sexMeta.setFieldName("sexType");
		sexMeta.setColumnName("SEX_TYPE");
		fieldsMeta.put("sextype", sexMeta);
		entityMeta.setFieldsMeta(fieldsMeta);
		entityMeta.setFieldsArray(new String[] { "name", "staffName", "bizStaffName", "sexType" });
		sql = SqlUtil.convertFieldsToColumns(entityMeta, sql);
		assertEquals(sql.trim(),
				"STAFF_NAME,`SEX_TYPE`,name,BIZ_STAFF_NAME from table where #[t.STAFF_NAME like ?] and SEX_TYPE=:sexType");
	}

	/**
	 * @TODO 测试vo属性名称转表字段名称
	 */
	@Test
	public void testConvertFieldsToCols1() {
		String sql = "sexType=:sexType";
		EntityMeta entityMeta = new EntityMeta();
		entityMeta.setTableName("staff_info");
		HashMap<String, FieldMeta> fieldsMeta = new HashMap<String, FieldMeta>();

		FieldMeta sexMeta = new FieldMeta();
		sexMeta.setFieldName("sexType");
		sexMeta.setColumnName("SEX_TYPE");
		fieldsMeta.put("sextype", sexMeta);
		entityMeta.setFieldsMeta(fieldsMeta);
		entityMeta.setFieldsArray(new String[] { "sexType" });
		sql = SqlUtil.convertFieldsToColumns(entityMeta, sql);
		System.err.println(sql);
	}

	@Test
	public void testConvertFieldsToCols2() {
		String sql = " detail_id = :detailId and res_type = : resType";
		EntityMeta entityMeta = new EntityMeta();
		entityMeta.setTableName("staff_info");
		HashMap<String, FieldMeta> fieldsMeta = new HashMap<String, FieldMeta>();

		FieldMeta sexMeta = new FieldMeta();
		sexMeta.setFieldName("detailId");
		sexMeta.setColumnName("detail_id");
		fieldsMeta.put("detailid", sexMeta);

		FieldMeta resType = new FieldMeta();
		resType.setFieldName("resType");
		resType.setColumnName("res_type");
		fieldsMeta.put("restype", resType);

		entityMeta.setFieldsMeta(fieldsMeta);
		entityMeta.setFieldsArray(new String[] { "detailId", "resType" });
		String[] paramNames = SqlConfigParseUtils.getSqlParamsName(sql, false);
		sql = SqlUtil.convertFieldsToColumns(entityMeta, sql);
		System.err.println(JSON.toJSONString(paramNames));
		System.err.println(sql);
	}

	@Test
	public void testValidateSqlInArg() {
		String argValue = "'alter1 table'";
		boolean hasSqlKeyWord = StringUtil.matches(" " + argValue, SqlUtil.SQLINJECT_PATTERN);
		System.err.println(hasSqlKeyWord);
		System.err.println(SqlUtil.validateInArg(argValue));
	}

	@Test
	public void testGetFromIndex() {
		String sql = "select sum(a),(day from(ab)) as b from (select * from tableb) as b where (a+b)>5 and c.a>(1+5)";
		int fromIndex = SqlUtil.getSymMarkIndexExcludeKeyWords(sql, "select\\s+", "\\s+from[\\(\\s+]", 0);
		System.err.println(fromIndex);

		sql = "select sum(a),(day xxx(ab)) as b from (select * from tableb) as b where (a+b)>5 and c.a>(1+5)";
		fromIndex = SqlUtil.getSymMarkIndexExcludeKeyWords(sql, "select\\s+", "\\s+from[\\(\\s+]", 0);
		System.err.println(fromIndex);

		sql = "select a, b from (select * from tableb) as b where (a+b)>5 and c.a>(1+5)";
		fromIndex = SqlUtil.getSymMarkIndexExcludeKeyWords(sql, "select\\s+", "\\s+from[\\(\\s+]", 0);
		System.err.println(fromIndex);

		sql = "select a, b from (select * from tableb where 1=1 and t.m>100) as b where (a+b)>5 and c.a>(1+5)";
		fromIndex = SqlUtil.getSymMarkIndexExcludeKeyWords(sql, "\\s+from[\\(\\s+]", "\\s+where[\\(\\s+]", 0);
		System.err.println(fromIndex);
	}

	@Test
	public void testGetFromIndex1() {
		String sql = "select a, b from (select * from tableb where 1=1 and t.m>100) as b where (a+b)>5 and c.a>(1+5)";
		int fromIndex = SqlUtil.getSymMarkIndexExcludeKeyWords(sql, "select\\s+", "\\s+from[\\(\\s+]", 0);
		fromIndex = SqlUtil.getSymMarkIndexExcludeKeyWords(sql, "\\s+from[\\(\\s+]", "\\s+where[\\(\\s+]", fromIndex - 1);
		System.err.println(fromIndex);
	}

}
