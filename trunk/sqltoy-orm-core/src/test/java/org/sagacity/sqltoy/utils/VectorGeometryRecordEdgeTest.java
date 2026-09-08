package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.sagacity.sqltoy.config.model.DataType;
import org.sagacity.sqltoy.model.JdbcTypes;

/**
 * 新特性纯Java侧解析测试:
 * BeanUtil.convertType对VECTOR(向量)/GEOMETRY(空间)类型的Java侧解析,以及Record类型乱序列映射
 */
public class VectorGeometryRecordEdgeTest {

	// ---------------- vector:目标类型形态 ----------------

	@Test
	public void vectorToStringPassthrough() throws Exception {
		String result = (String) BeanUtil.convertType(null, "[1,2,3]", JdbcTypes.VECTOR, DataType.stringType,
				"java.lang.String", null);
		assertEquals("[1,2,3]", result);
	}

	@Test
	public void vectorToFloatArray() throws Exception {
		Object result = BeanUtil.convertType(null, "[1,2,3]", JdbcTypes.VECTOR, DataType.aryOtherType, "float[]",
				null);
		assertArrayEquals(new float[] { 1f, 2f, 3f }, (float[]) result, 0.0001f);
	}

	@Test
	public void vectorToBoxedFloatArray() throws Exception {
		Object result = BeanUtil.convertType(null, "[1,2,3]", JdbcTypes.VECTOR, DataType.aryOtherType,
				"java.lang.Float[]", null);
		assertArrayEquals(new Float[] { 1f, 2f, 3f }, (Float[]) result);
	}

	@Test
	public void vectorToDoubleArray() throws Exception {
		Object result = BeanUtil.convertType(null, "[1.5,2.5]", JdbcTypes.VECTOR, DataType.aryOtherType, "double[]",
				null);
		assertArrayEquals(new double[] { 1.5, 2.5 }, (double[]) result, 0.0001d);
	}

	@Test
	public void vectorToListFloatDefault() throws Exception {
		// List目标不带泛型默认按Float解析(向量维度值一般为float32)
		Object result = BeanUtil.convertType(null, "[0.5,1.5]", JdbcTypes.VECTOR, DataType.listType, "java.util.List",
				null);
		List<?> list = assertInstanceOf(List.class, result);
		assertEquals(2, list.size());
		assertEquals(Float.valueOf(0.5f), list.get(0));
		assertEquals(Float.valueOf(1.5f), list.get(1));
	}

	@Test
	public void vectorToListDoubleWithGenericType() throws Exception {
		Object result = BeanUtil.convertType(null, "[0.5,1.5]", JdbcTypes.VECTOR, DataType.listType, "java.util.List",
				Double.class);
		List<?> list = assertInstanceOf(List.class, result);
		assertEquals(Double.valueOf(0.5d), list.get(0));
		assertEquals(Double.valueOf(1.5d), list.get(1));
	}

	// ---------------- vector:格式形态 ----------------

	@Test
	public void vectorEmptyForms() throws Exception {
		assertEquals(0, ((float[]) BeanUtil.convertType(null, "[]", JdbcTypes.VECTOR, DataType.aryOtherType,
				"float[]", null)).length);
		// 剔除中括号后仅空白,同样视为空向量
		assertEquals(0, ((float[]) BeanUtil.convertType(null, "[ ]", JdbcTypes.VECTOR, DataType.aryOtherType,
				"float[]", null)).length);
		List<?> emptyList = (List<?>) BeanUtil.convertType(null, "[]", JdbcTypes.VECTOR, DataType.listType,
				"java.util.List", null);
		assertEquals(0, emptyList.size());
	}

	@Test
	public void vectorWhitespaceTolerant() throws Exception {
		// 带前后空白与项内空格
		Object result = BeanUtil.convertType(null, " [1, 2.5 ,3 ] ", JdbcTypes.VECTOR, DataType.aryOtherType,
				"float[]", null);
		assertArrayEquals(new float[] { 1f, 2.5f, 3f }, (float[]) result, 0.0001f);
	}

	@Test
	public void vectorScientificNotation() throws Exception {
		Object result = BeanUtil.convertType(null, "[1e2,-3E-1]", JdbcTypes.VECTOR, DataType.aryOtherType, "float[]",
				null);
		assertArrayEquals(new float[] { 100f, -0.3f }, (float[]) result, 0.0001f);
	}

	@Test
	public void vectorNonVectorValueFallsThroughRaw() throws Exception {
		// 非[...]形态的值返回null交回常规处理,最终原值返回(类型不匹配由赋值环节暴露)
		Object result = BeanUtil.convertType(null, "abc", JdbcTypes.VECTOR, DataType.aryOtherType, "float[]", null);
		assertEquals("abc", result);
	}

	@Test
	public void vectorMalformedNumberThrows() throws Exception {
		// 行为记录:向量项非法数字直接抛NumberFormatException(数据源头可控场景可接受)
		assertThrows(NumberFormatException.class, () -> BeanUtil.convertType(null, "[1,abc]", JdbcTypes.VECTOR,
				DataType.aryOtherType, "float[]", null));
	}

	// ---------------- geometry ----------------

	@Test
	public void geometryWktStringToPoint() throws Exception {
		Object result = BeanUtil.convertType(null, "POINT (1 2)", JdbcTypes.GEOMETRY, DataType.objectType,
				"org.locationtech.jts.geom.Point", null);
		Point point = assertInstanceOf(Point.class, result);
		assertEquals(1.0, point.getX(), 0.0001);
		assertEquals(2.0, point.getY(), 0.0001);
	}

	@Test
	public void geometryStringTargetPassthrough() throws Exception {
		String value = "POINT (1 2)";
		assertSame(value, BeanUtil.convertType(null, value, JdbcTypes.GEOMETRY, DataType.stringType,
				"java.lang.String", null));
	}

	@Test
	public void geometryWkbBytesToWktString() throws Exception {
		byte[] wkb = new org.locationtech.jts.io.WKBWriter()
				.write(new GeometryFactory().createPoint(new Coordinate(1, 2)));
		Object result = BeanUtil.convertType(null, wkb, JdbcTypes.GEOMETRY, DataType.stringType, "java.lang.String",
				null);
		assertEquals("POINT (1 2)", result);
	}

	@Test
	public void geometryEwktWithSrid() throws Exception {
		Object result = BeanUtil.convertType(null, "SRID=4326;POINT(1 2)", JdbcTypes.GEOMETRY, DataType.objectType,
				"org.locationtech.jts.geom.Point", null);
		Point point = assertInstanceOf(Point.class, result);
		assertEquals(4326, point.getSRID());
	}

	@Test
	public void geometryGarbageFallsThroughRaw() throws Exception {
		// 无法解析的值返回null交回常规处理,最终原值返回
		Object result = BeanUtil.convertType(null, "not-a-geometry", JdbcTypes.GEOMETRY, DataType.objectType,
				"org.locationtech.jts.geom.Point", null);
		assertEquals("not-a-geometry", result);
	}

	@Test
	public void geometryTypeNameRecognition() throws Exception {
		assertTrue(GeometryTypeUtil.isGeometryTypeName("POINT"));
		assertTrue(GeometryTypeUtil.isGeometryTypeName("GEOMETRY"));
		assertEquals(false, GeometryTypeUtil.isGeometryTypeName("VARCHAR"));
	}

	// ---------------- Record乱序列映射 ----------------

	record PointRecord(int x, String label) {
	}

	/**
	 * 列顺序与record组件顺序不一致时,按属性名映射而非按下标对位
	 */
	@Test
	public void recordMapsByNameNotByColumnOrder() throws Exception {
		java.util.List<Object[]> rows = new java.util.ArrayList<>();
		rows.add(new Object[] { "p1", 7 });
		rows.add(new Object[] { "p2", 8 });
		java.util.List result = BeanUtil.reflectListToBean(null, rows, new int[] { 0, 1 },
				new String[] { "label", "x" }, null, PointRecord.class);
		assertEquals(2, result.size());
		// 第1行:["p1",7] → label="p1",x=7
		assertEquals(new PointRecord(7, "p1"), result.get(0));
		assertEquals(new PointRecord(8, "p2"), result.get(1));
	}
}
