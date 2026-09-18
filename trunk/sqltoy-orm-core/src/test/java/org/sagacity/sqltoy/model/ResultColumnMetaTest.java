package org.sagacity.sqltoy.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

/**
 * update 2026-9-12 优化步骤2:ResultColumnMeta的类型名派生与枚举映射单测——
 * ExtType.of与原columnJdbcTypes类型名检测链逐字对齐的回归保障。
 */
public class ResultColumnMetaTest {

	@Test
	public void extTypeFromTypeName() {
		assertSame(ResultColumnMeta.ExtType.JSON, ResultColumnMeta.ExtType.of("json"), "json小写");
		assertSame(ResultColumnMeta.ExtType.JSON, ResultColumnMeta.ExtType.of("JSON"), "json大写");
		assertSame(ResultColumnMeta.ExtType.JSONB, ResultColumnMeta.ExtType.of("JSONB"), "jsonb");
		assertSame(ResultColumnMeta.ExtType.GEOMETRY, ResultColumnMeta.ExtType.of("geometry"), "geometry");
		assertSame(ResultColumnMeta.ExtType.GEOMETRY, ResultColumnMeta.ExtType.of("ST_GEOMETRY"), "hana空间类型");
		assertSame(ResultColumnMeta.ExtType.GEOMETRY, ResultColumnMeta.ExtType.of("SDO_GEOMETRY"), "oracle空间类型");
		assertSame(ResultColumnMeta.ExtType.VECTOR, ResultColumnMeta.ExtType.of("vector"), "pgvector/mysql向量");
		assertSame(ResultColumnMeta.ExtType.VECTOR, ResultColumnMeta.ExtType.of("FLOATVECTOR"), "vastbase向量");
		assertSame(ResultColumnMeta.ExtType.VECTOR, ResultColumnMeta.ExtType.of("REAL_VECTOR"), "hana向量预留");
		assertSame(ResultColumnMeta.ExtType.NONE, ResultColumnMeta.ExtType.of("NVARCHAR"), "承载列不误标");
		assertSame(ResultColumnMeta.ExtType.NONE, ResultColumnMeta.ExtType.of("bigint"), "常规列");
		assertSame(ResultColumnMeta.ExtType.NONE, ResultColumnMeta.ExtType.of(null), "null安全");
	}

	@Test
	public void jdbcTypeRoundtrip() {
		// 枚举↔jdbc标记往返一致
		for (ResultColumnMeta.ExtType et : ResultColumnMeta.ExtType.values()) {
			if (et == ResultColumnMeta.ExtType.NONE) {
				assertEquals(0, ResultColumnMeta.jdbcTypeOf(et), "NONE无jdbc标记");
				assertNull(ResultColumnMeta.extTypeOfJdbc(0), "0反向为null");
			} else {
				int jdbc = ResultColumnMeta.jdbcTypeOf(et);
				assertSame(et, ResultColumnMeta.extTypeOfJdbc(jdbc), et + "往返");
			}
		}
	}

	@Test
	public void readStrategyOf() {
		assertSame(ResultColumnMeta.ReadStrategy.NORMAL, ResultColumnMeta.ReadStrategy.of(0), "常规列");
		assertSame(ResultColumnMeta.ReadStrategy.TEXT_READ, ResultColumnMeta.ReadStrategy.of(1), "文本化读取列");
		assertSame(ResultColumnMeta.ReadStrategy.EXT_BYTE, ResultColumnMeta.ReadStrategy.of(2), "byte[]归一列");
		assertSame(ResultColumnMeta.ReadStrategy.NORMAL, ResultColumnMeta.ReadStrategy.of(99), "未知取值防御");
	}

	@Test
	public void voBindingApply() {
		ResultColumnMeta col = new ResultColumnMeta("json_col", "json_col", 1, "NVARCHAR",
				"NVARCHAR", ResultColumnMeta.ReadStrategy.NORMAL, ResultColumnMeta.ExtType.NONE, 0, null);
		// 承载形态列+注解json标注:extType由NONE提升为JSON(hana等无原生json库场景)
		col.applyVoBinding("jsonObj", org.sagacity.sqltoy.model.JdbcTypes.JSON, ResultColumnMeta.ExtType.JSON);
		assertEquals("jsonObj", col.getPropertyName(), "属性名回填");
		assertEquals(org.sagacity.sqltoy.model.JdbcTypes.JSON, col.getJdbcType(), "jdbc标记回填");
		assertSame(ResultColumnMeta.ExtType.JSON, col.getExtType(), "注解标注提升extType");
		// 非扩展类型注解不覆盖既有extType标记
		ResultColumnMeta col2 = new ResultColumnMeta("v", "v", 2, "vector", "vector",
				ResultColumnMeta.ReadStrategy.NORMAL, ResultColumnMeta.ExtType.VECTOR, 0, null);
		col2.applyVoBinding("v", 12, null);
		assertSame(ResultColumnMeta.ExtType.VECTOR, col2.getExtType(), "null不覆盖");
	}
}
