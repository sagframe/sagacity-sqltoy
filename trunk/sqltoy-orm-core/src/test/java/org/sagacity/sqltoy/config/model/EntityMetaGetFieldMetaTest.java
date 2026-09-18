package org.sagacity.sqltoy.config.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * 回归测试：getFieldMeta(null)返回null而非NPE;大小写不敏感与不存在的属性名行为不变
 */
public class EntityMetaGetFieldMetaTest {

	@Test
	public void nullFieldNameReturnsNull() {
		EntityMeta entityMeta = new EntityMeta();
		entityMeta.addFieldMeta(
				new FieldMeta("userName", "USER_NAME", null, null, java.sql.Types.VARCHAR, true, false, 50, 0, 0));
		// 修复前:field.toLowerCase()直接NPE
		assertNull(entityMeta.getFieldMeta(null));
	}

	@Test
	public void caseInsensitiveAndMissingBehaviorUnchanged() {
		EntityMeta entityMeta = new EntityMeta();
		entityMeta.addFieldMeta(
				new FieldMeta("userName", "USER_NAME", null, null, java.sql.Types.VARCHAR, true, false, 50, 0, 0));
		assertEquals("USER_NAME", entityMeta.getFieldMeta("userName").getColumnName());
		assertEquals("USER_NAME", entityMeta.getFieldMeta("USERNAME").getColumnName());
		assertNull(entityMeta.getFieldMeta("notExist"));
	}
}
