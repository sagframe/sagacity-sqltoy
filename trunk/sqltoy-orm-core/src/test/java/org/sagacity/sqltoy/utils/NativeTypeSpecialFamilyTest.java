package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.model.JdbcTypes;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * update 2026-11-21 nativeType特殊族机制验证:quickvo按源库生成nativeType(源方言物理类型名),
 * 解析期归一逻辑族(NativeTypeUtils.specialFamily),消费点(merge cast/DDL)按目标方言翻译
 * (specialCastType)——源形态仅在其原生域(pg内核系)原样有效,跨域翻译或回落(null=组件链/裸?),
 * 族不可识别(如dm具名VARRAY)原样透传保持精确控制
 *
 * @author zsy 2026-11-21
 */
public class NativeTypeSpecialFamilyTest {

	@Test
	public void familyRecognition() {
		// json族别名与jdbcType正路
		assertEquals(NativeTypeUtils.FAMILY_JSON, NativeTypeUtils.specialFamily("json", null));
		assertEquals(NativeTypeUtils.FAMILY_JSONB, NativeTypeUtils.specialFamily("JSONB", null));
		assertEquals(NativeTypeUtils.FAMILY_JSON, NativeTypeUtils.specialFamily(null, JdbcTypes.JSON));
		assertEquals(NativeTypeUtils.FAMILY_JSONB, NativeTypeUtils.specialFamily(null, JdbcTypes.JSONB));
		assertEquals(NativeTypeUtils.FAMILY_JSON, NativeTypeUtils.specialFamily("json", JdbcTypes.JSON));
		// 数组族(pg标准elem[]与内部前缀_varchar反向元数据形态)
		assertEquals(NativeTypeUtils.FAMILY_ARRAY, NativeTypeUtils.specialFamily("text[]", null));
		assertEquals(NativeTypeUtils.FAMILY_ARRAY, NativeTypeUtils.specialFamily("varchar(50)[]", null));
		assertEquals(NativeTypeUtils.FAMILY_ARRAY, NativeTypeUtils.specialFamily("_varchar", null));
		// vector/geometry族(含floatvector别名与带schema的db2gse形态)
		assertEquals(NativeTypeUtils.FAMILY_VECTOR, NativeTypeUtils.specialFamily("floatvector", null));
		assertEquals(NativeTypeUtils.FAMILY_GEOMETRY, NativeTypeUtils.specialFamily("geometry", null));
		assertEquals(NativeTypeUtils.FAMILY_GEOMETRY, NativeTypeUtils.specialFamily("db2gse.st_geometry", null));
		// 族不可识别:dm具名VARRAY/任意自定义类型名 → null(原样透传)
		assertNull(NativeTypeUtils.specialFamily("MY_VARCHAR_LIST", null));
		assertNull(NativeTypeUtils.specialFamily(null, null));
		assertNull(NativeTypeUtils.specialFamily("  ", null));
	}

	@Test
	public void castTranslationAcrossTargets() {
		// jsonb(pg源)→pg内核域原样(保精度)/h2归一JSON/dm与db2无对应回落null(裸?)
		assertEquals("jsonb", NativeTypeUtils.specialCastType(DBType.POSTGRESQL, NativeTypeUtils.FAMILY_JSONB, "jsonb"));
		assertEquals("jsonb", NativeTypeUtils.specialCastType(DBType.KINGBASE, NativeTypeUtils.FAMILY_JSONB, "jsonb"));
		assertEquals("JSON", NativeTypeUtils.specialCastType(DBType.H2, NativeTypeUtils.FAMILY_JSONB, "jsonb"));
		assertNull(NativeTypeUtils.specialCastType(DBType.DM, NativeTypeUtils.FAMILY_JSONB, "jsonb"));
		assertNull(NativeTypeUtils.specialCastType(DBType.DB2, NativeTypeUtils.FAMILY_JSONB, "jsonb"));
		// 数组族(pg源text[])→pg域原样;h2回落null(组件链接手VARCHAR ARRAY);dm/db2 null(裸?)
		assertEquals("text[]", NativeTypeUtils.specialCastType(DBType.POSTGRESQL, NativeTypeUtils.FAMILY_ARRAY, "text[]"));
		assertEquals("text[]", NativeTypeUtils.specialCastType(DBType.OPENGAUSS, NativeTypeUtils.FAMILY_ARRAY, "text[]"));
		assertNull(NativeTypeUtils.specialCastType(DBType.H2, NativeTypeUtils.FAMILY_ARRAY, "text[]"));
		assertNull(NativeTypeUtils.specialCastType(DBType.DM, NativeTypeUtils.FAMILY_ARRAY, "text[]"));
		// json(pg源)→mysql/oracle等非pg域的目标cast不适用(null);h2归一JSON
		assertNull(NativeTypeUtils.specialCastType(DBType.MYSQL, NativeTypeUtils.FAMILY_JSON, "json"));
		assertEquals("JSON", NativeTypeUtils.specialCastType(DBType.H2, NativeTypeUtils.FAMILY_JSON, "json"));
		// geometry:h2归一GEOMETRY;DM/DB2回落null(函数包装正路DMGEO.ST_GeomFromText/db2GeomFromTextWrap,
		// 通用cast实测DM -6801/DB2内置形态-408);pg内核系原样cast
		assertEquals("GEOMETRY", NativeTypeUtils.specialCastType(DBType.H2, NativeTypeUtils.FAMILY_GEOMETRY, "geometry"));
		assertNull(NativeTypeUtils.specialCastType(DBType.DB2, NativeTypeUtils.FAMILY_GEOMETRY, "db2gse.st_geometry"));
		assertNull(NativeTypeUtils.specialCastType(DBType.DM, NativeTypeUtils.FAMILY_GEOMETRY, "ST_GEOMETRY"));
		assertEquals("geometry",
				NativeTypeUtils.specialCastType(DBType.POSTGRESQL, NativeTypeUtils.FAMILY_GEOMETRY, "geometry"));
		// 族不可识别:原样透传(dm具名VARRAY在dm目标的精确控制)
		assertEquals("MY_VARCHAR_LIST",
				NativeTypeUtils.specialCastType(DBType.DM, null, "MY_VARCHAR_LIST"));
	}

	@Test
	public void uuidFamilyRecognitionAndCast() {
		// update 2026-11-22 缺陷A回归锁死:@Column(type=UUID)未写nativeType时jdbcType正路须判族,
		// 此前jdbcType块缺UUID致family=null,DDL uuid映射块(在specialFamily!=null内)不可达,
		// uuid列落裸VARCHAR(mysql/db2/oracle建表失败、mssql截断、pg/h2错型,真库实证)
		assertEquals(NativeTypeUtils.FAMILY_UUID, NativeTypeUtils.specialFamily(null, JdbcTypes.UUID));
		assertEquals(NativeTypeUtils.FAMILY_UUID, NativeTypeUtils.specialFamily("uuid", null));
		assertEquals(NativeTypeUtils.FAMILY_UUID, NativeTypeUtils.specialFamily("uuid", JdbcTypes.UUID));
		// uuid cast:pg内核系/h2原生uuid可cast;其余目标varchar(36)承载列裸?即正确(cast反而非法)
		assertEquals("uuid", NativeTypeUtils.specialCastType(DBType.POSTGRESQL, NativeTypeUtils.FAMILY_UUID, "uuid"));
		assertEquals("uuid", NativeTypeUtils.specialCastType(DBType.H2, NativeTypeUtils.FAMILY_UUID, "uuid"));
		assertNull(NativeTypeUtils.specialCastType(DBType.MYSQL, NativeTypeUtils.FAMILY_UUID, "uuid"));
		assertNull(NativeTypeUtils.specialCastType(DBType.DM, NativeTypeUtils.FAMILY_UUID, "uuid"));
		// jdbcType=UUID且nativeType空白:族内保源形态回落null(不拼cast(? as )空类型)
		assertNull(NativeTypeUtils.specialCastType(DBType.POSTGRESQL, NativeTypeUtils.FAMILY_UUID, ""));
	}

	@Test
	public void blankNativeTypeReturnsNullNotEmpty() {
		// update 2026-11-22 缺陷三回归锁死:@Column的nativeType默认值是空串而非null,族内
		// "保源形态return nativeType"曾返回空串,经cast(? as +x+)拼出cast(? as )空类型,
		// jdbcType判族(VECTOR/GEOMETRY/JSON)且未写nativeType的实体在pg上merge即炸
		// (ExtTypesMatrixTest真库实证)——空白统一null回落jdbcType分支正路
		assertNull(NativeTypeUtils.specialCastType(DBType.POSTGRESQL, NativeTypeUtils.FAMILY_VECTOR, ""));
		assertNull(NativeTypeUtils.specialCastType(DBType.POSTGRESQL, NativeTypeUtils.FAMILY_GEOMETRY, ""));
		assertNull(NativeTypeUtils.specialCastType(DBType.POSTGRESQL, NativeTypeUtils.FAMILY_JSONB, null));
		assertNull(NativeTypeUtils.specialCastType(DBType.POSTGRESQL, NativeTypeUtils.FAMILY_ARRAY, "  "));
		assertNull(NativeTypeUtils.specialCastType(DBType.DM, null, ""));
	}

	/**
	 * update 2026-11-22 复查修复批次回归锁死:
	 * <li>typmod形态归族:vector(3)/floatvector(3)/geometry(Point,4326)此前漏判(只有含geometry的宽松判定命中)</li>
	 * <li>pg内核系族cast归一:源库nativeType若不是目标库合法拼写(CLOB承载的json、ST_GEOMETRY、mysql的vector),
	 * 原样透传会在pg解析期报 type "xxx" does not exist(真库实证cast(? as CLOB)使saveOrUpdate整体失败)</li>
	 * <li>非pg且非h2的目标不再原样返回nativeType(统一回落jdbcType分支)</li>
	 */
	@Test
	public void typmodFamilyAndPgKernelCastNormalization() {
		// typmod归族
		assertEquals(NativeTypeUtils.FAMILY_VECTOR, NativeTypeUtils.specialFamily("vector(3)", null));
		assertEquals(NativeTypeUtils.FAMILY_VECTOR, NativeTypeUtils.specialFamily("floatvector(3)", null));
		assertEquals(NativeTypeUtils.FAMILY_VECTOR, NativeTypeUtils.specialFamily("VECTOR( 3 )", null));
		assertEquals(NativeTypeUtils.FAMILY_GEOMETRY, NativeTypeUtils.specialFamily("geometry(Point,4326)", null));
		assertEquals(NativeTypeUtils.FAMILY_ARRAY, NativeTypeUtils.specialFamily("varchar(50)[]", null));
		// 带精度的常规数值类型不误判
		assertNull(NativeTypeUtils.specialFamily("numeric(10,2)", null));
		assertNull(NativeTypeUtils.specialFamily("character varying(50)", null));
		assertNull(NativeTypeUtils.specialFamily("timestamp(6) with time zone", null));

		// pg内核系:外部拼写归一为合法类型
		assertEquals("json", NativeTypeUtils.specialCastType(DBType.POSTGRESQL, NativeTypeUtils.FAMILY_JSON, "CLOB"));
		assertEquals("json", NativeTypeUtils.specialCastType(DBType.POSTGRESQL, NativeTypeUtils.FAMILY_JSON, "nvarchar"));
		assertEquals("jsonb", NativeTypeUtils.specialCastType(DBType.POSTGRESQL, NativeTypeUtils.FAMILY_JSONB, "CLOB"));
		assertEquals("jsonb", NativeTypeUtils.specialCastType(DBType.POSTGRESQL, NativeTypeUtils.FAMILY_JSON, "jsonb"));
		assertEquals("json", NativeTypeUtils.specialCastType(DBType.POSTGRESQL, NativeTypeUtils.FAMILY_JSONB, "json"));
		assertEquals("geometry",
				NativeTypeUtils.specialCastType(DBType.POSTGRESQL, NativeTypeUtils.FAMILY_GEOMETRY, "ST_GEOMETRY"));
		assertEquals("geometry", NativeTypeUtils.specialCastType(DBType.POSTGRESQL, NativeTypeUtils.FAMILY_GEOMETRY,
				"SDO_GEOMETRY"));
		assertEquals("geometry", NativeTypeUtils.specialCastType(DBType.POSTGRESQL, NativeTypeUtils.FAMILY_GEOMETRY,
				"db2gse.st_geometry"));
		// 族自身拼写保留精度(typmod)
		assertEquals("geometry(Point,4326)", NativeTypeUtils.specialCastType(DBType.POSTGRESQL,
				NativeTypeUtils.FAMILY_GEOMETRY, "geometry(Point,4326)"));
		assertEquals("vector(3)",
				NativeTypeUtils.specialCastType(DBType.POSTGRESQL, NativeTypeUtils.FAMILY_VECTOR, "vector(3)"));
		// gaussdb/vastbase只有floatvector
		assertEquals("floatvector",
				NativeTypeUtils.specialCastType(DBType.GAUSSDB, NativeTypeUtils.FAMILY_VECTOR, "vector(3)"));
		assertEquals("floatvector(3)",
				NativeTypeUtils.specialCastType(DBType.VASTBASE, NativeTypeUtils.FAMILY_VECTOR, "floatvector(3)"));
		assertEquals("vector",
				NativeTypeUtils.specialCastType(DBType.POSTGRESQL, NativeTypeUtils.FAMILY_VECTOR, "floatvector(3)"));
		// 其余目标不再原样透传nativeType(回落jdbcType分支)
		assertNull(NativeTypeUtils.specialCastType(DBType.MYSQL, NativeTypeUtils.FAMILY_GEOMETRY, "ST_GEOMETRY"));
		assertNull(NativeTypeUtils.specialCastType(DBType.ORACLE, NativeTypeUtils.FAMILY_JSON, "jsonb"));
		// OSCAR(传统Oscar驱动)无json/vector类型:族前置须回null,交回og工具的oscar裸?分支
		assertNull(NativeTypeUtils.specialCastType(DBType.OSCAR, NativeTypeUtils.FAMILY_JSON, "json"));
		assertNull(NativeTypeUtils.specialCastType(DBType.OSCAR, NativeTypeUtils.FAMILY_JSONB, "jsonb"));
		assertNull(NativeTypeUtils.specialCastType(DBType.OSCAR, NativeTypeUtils.FAMILY_VECTOR, "vector"));
		assertNull(NativeTypeUtils.specialCastType(DBType.OSCAR, NativeTypeUtils.FAMILY_BSON, "bson"));
	}
}
