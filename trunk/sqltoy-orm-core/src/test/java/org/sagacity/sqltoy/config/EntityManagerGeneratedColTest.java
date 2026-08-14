package org.sagacity.sqltoy.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.annotation.Column;
import org.sagacity.sqltoy.config.annotation.Entity;
import org.sagacity.sqltoy.config.annotation.Id;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.GeneratedType;

/**
 * 回归测试：实体含计算列(generatedType)且无任何字段配置defaultValue时,
 * processNotGeneratedColMeta对null的fieldsDefaultValue不再arraycopy(null)启动期NPE
 */
public class EntityManagerGeneratedColTest {

	@Entity(tableName = "test_gen_col")
	public static class GenColEntity {
		@Id
		private String id;

		@Column(name = "CODE", generatedType = GeneratedType.VIRTUAL)
		private String code;

		@Column(name = "NAME")
		private String name;

		public String getId() {
			return id;
		}

		public String getCode() {
			return code;
		}

		public String getName() {
			return name;
		}
	}

	@Test
	public void generatedColWithoutAnyDefaultValueParses() {
		SqlToyContext context = new SqlToyContext();
		EntityManager entityManager = new EntityManager();
		// 修复前:arraycopy(null,...)抛NPE,实体解析失败
		EntityMeta meta = assertDoesNotThrow(
				() -> entityManager.parseEntityMeta(context, GenColEntity.class, true, false));
		assertNotNull(meta);
		// 无默认值场景下notGeneratedColMeta的defaultValue合法为null(下游已判空)
		assertNull(meta.getNotGeneratedColMeta().getFieldsDefaultValue());
		assertNotNull(meta.getNotGeneratedColMeta().getFieldsArray());
	}
}
