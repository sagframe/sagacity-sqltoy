package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.FieldMeta;

public class SqlUtilTest {

	@Test
	public void testConvertFieldsToCols() {
		String sql = "select staffName,`sexType`,name,bizStaffName from table where #[t.staffName like ?] and sexType=:sexType";
		EntityMeta entityMeta = new EntityMeta();
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
				"select STAFF_NAME,`SEX_TYPE`,name,BIZ_STAFF_NAME from table where #[t.STAFF_NAME like ?] and SEX_TYPE=:sexType");
	}
}
