package org.sagacity.sqltoy.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.FieldMeta;
import org.sagacity.sqltoy.model.JdbcTypes;
import org.sagacity.sqltoy.utils.NativeTypeUtils;

/**
 * update 2026-11-22 nativeType族parse期jdbcType上调的直接单测(族机制枢纽):
 * quickvo对特殊类型列(json/jsonb/vector/geometry/uuid)常只写nativeType不写type
 * (@Column的type默认OTHER),EntityManager解析期将type上调为对应JdbcTypes常量,
 * 使bindTypes→绑定机器(JSONTypeUtil/setVectorValue/setGeometryValue/UUID分派)、
 * DDL case、merge cast全部可达既有正路。显式type不受上调影响;未识别族不动;
 * 既有"OTHER按字段java类型自动补充type"的推断路径不得被上调守卫破坏(回归保护)。
 *
 * @author zsy 2026-11-22
 */
public class EntityManagerNativeTypeUpliftTest {

	@org.sagacity.sqltoy.config.annotation.Entity(tableName = "uplift_probe")
	private static class UpliftVO implements java.io.Serializable {
		private static final long serialVersionUID = 1L;

		@org.sagacity.sqltoy.config.annotation.Id
		@org.sagacity.sqltoy.config.annotation.Column(name = "id")
		private Long id;

		/** 只写nativeType=pg源jsonb形态,type默认OTHER → 应上调JdbcTypes.JSONB */
		@org.sagacity.sqltoy.config.annotation.Column(name = "js_col", nativeType = "jsonb")
		private String jsCol;

		/** pg源vector → 上调JdbcTypes.VECTOR */
		@org.sagacity.sqltoy.config.annotation.Column(name = "vec_col", nativeType = "vector")
		private String vecCol;

		/** pg源geometry → 上调JdbcTypes.GEOMETRY */
		@org.sagacity.sqltoy.config.annotation.Column(name = "geo_col", nativeType = "geometry")
		private String geoCol;

		/** pg源uuid → 上调JdbcTypes.UUID */
		@org.sagacity.sqltoy.config.annotation.Column(name = "uid_col", nativeType = "uuid")
		private String uidCol;

		/** 显式type=VARCHAR+nativeType=jsonb → 显式优先不上调(用户声明字符串承载语义) */
		@org.sagacity.sqltoy.config.annotation.Column(name = "js_explicit", type = java.sql.Types.VARCHAR, nativeType = "jsonb")
		private String jsExplicit;

		/** 未识别族(hstore) → 不上调,type保持OTHER、族null(用户精确控制路径) */
		@org.sagacity.sqltoy.config.annotation.Column(name = "hs_col", nativeType = "hstore")
		private String hsCol;

		/** 无nativeType的普通String,type默认OTHER → 既有"按字段类型推断VARCHAR"必须仍工作 */
		@org.sagacity.sqltoy.config.annotation.Column(name = "plain_col")
		private String plainCol;

		/** 数组族:text[] → 族=array+组件类型提取(String[]定型数组) */
		@org.sagacity.sqltoy.config.annotation.Column(name = "tags_col", nativeType = "text[]")
		private String[] tagsCol;

		/** 数组族:List泛型组件提取 */
		@org.sagacity.sqltoy.config.annotation.Column(name = "nums_col", nativeType = "int8[]")
		private java.util.List<Long> numsCol;
	}

	private FieldMeta fm(EntityMeta entityMeta, String field) {
		return entityMeta.getFieldMeta(field);
	}

	@Test
	public void specialFamilyUpliftToJdbcTypes() {
		EntityManager em = new EntityManager();
		EntityMeta entityMeta = em.getEntityMeta(new SqlToyContext(), UpliftVO.class);
		// 族识别+type上调(json/jsonb/vector/geometry/uuid)
		assertEquals(JdbcTypes.JSONB, fm(entityMeta, "jsCol").getType(), "jsonb应上调JSONB");
		assertEquals(NativeTypeUtils.FAMILY_JSONB, fm(entityMeta, "jsCol").getSpecialFamily());
		assertEquals(JdbcTypes.VECTOR, fm(entityMeta, "vecCol").getType(), "vector应上调VECTOR");
		assertEquals(JdbcTypes.GEOMETRY, fm(entityMeta, "geoCol").getType(), "geometry应上调GEOMETRY");
		assertEquals(JdbcTypes.UUID, fm(entityMeta, "uidCol").getType(), "uuid应上调UUID");
	}

	@Test
	public void explicitTypeNotUplifted() {
		EntityManager em = new EntityManager();
		EntityMeta entityMeta = em.getEntityMeta(new SqlToyContext(), UpliftVO.class);
		// 显式type=VARCHAR优先,不上调(字符串承载语义由用户声明);族仍识别(消费点翻译用)
		assertEquals(java.sql.Types.VARCHAR, fm(entityMeta, "jsExplicit").getType(), "显式type不得被上调覆盖");
		assertEquals(NativeTypeUtils.FAMILY_JSONB, fm(entityMeta, "jsExplicit").getSpecialFamily());
	}

	@Test
	public void unrecognizedFamilyKeepsLegacyInference() {
		EntityManager em = new EntityManager();
		EntityMeta entityMeta = em.getEntityMeta(new SqlToyContext(), UpliftVO.class);
		// 未识别族(hstore):族null不触发上调;type=OTHER由既有"按java字段类型推断"补为VARCHAR
		// (2026-9-6机制,String字段→VARCHAR字符串承载,行为正确且必须保持)
		assertEquals(java.sql.Types.VARCHAR, fm(entityMeta, "hsCol").getType(),
				"未识别族走既有字段类型推断(String→VARCHAR)");
		assertNull(fm(entityMeta, "hsCol").getSpecialFamily(), "hstore不识别为族");
	}

	@Test
	public void legacyFieldTypeInferenceStillWorks() {
		EntityManager em = new EntityManager();
		EntityMeta entityMeta = em.getEntityMeta(new SqlToyContext(), UpliftVO.class);
		// 回归保护:上调守卫(双OTHER条件)不得破坏既有"OTHER按java字段类型补充"——
		// 普通String字段仍被推断为VARCHAR(2026-9-6既有机制)
		assertEquals(java.sql.Types.VARCHAR, fm(entityMeta, "plainCol").getType(),
				"无nativeType的String字段既有推断必须保留");
	}

	@Test
	public void arrayFamilyComponentExtraction() {
		EntityManager em = new EntityManager();
		EntityMeta entityMeta = em.getEntityMeta(new SqlToyContext(), UpliftVO.class);
		// 数组族:族识别+组件提取(定型数组取组件类,List取泛型实参)
		assertEquals(NativeTypeUtils.FAMILY_ARRAY, fm(entityMeta, "tagsCol").getSpecialFamily());
		assertEquals("java.lang.String", fm(entityMeta, "tagsCol").getArrayComponentType());
		assertEquals(NativeTypeUtils.FAMILY_ARRAY, fm(entityMeta, "numsCol").getSpecialFamily());
		assertEquals("java.lang.Long", fm(entityMeta, "numsCol").getArrayComponentType(), "List泛型实参提取");
		// 数组族不上调jdbcType(无数组JdbcTypes常量,由arrayComponentType驱动DDL/绑定)
		assertEquals(java.sql.Types.OTHER, fm(entityMeta, "tagsCol").getType());
	}
}
