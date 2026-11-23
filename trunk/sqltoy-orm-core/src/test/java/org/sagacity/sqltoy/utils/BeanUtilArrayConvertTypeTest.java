package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Array;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.DataType;

/**
 * update 2026-11-21 convertType的List/Set分支数组归一加固验证:
 * 此前java.sql.Array分支的(Object[])强转与instanceof Object[]判定,在"驱动getArray()
 * 返回原始类型数组"或"归一化透传原始数组"场景会CCE/漏转(setter报argument type
 * mismatch)。现经CollectionUtil.convertArray统一装箱(Object[]直通)。主流驱动均返回
 * 装箱数组(真库矩阵实测),此测试用伪造驱动形态锁死加固路径的行为。
 *
 * @author zsy 2026-11-21
 */
public class BeanUtilArrayConvertTypeTest {

	/** 伪造返回原始类型int[]的java.sql.Array(矩阵内无此驱动形态,纯加固锁死用) */
	private static Array primitiveArrayResult() {
		return new Array() {
			@Override
			public Object getArray() {
				return new int[] { 1, 2 };
			}

			@Override
			public Object getArray(long index, int count) throws SQLException {
				throw new UnsupportedOperationException();
			}

			@Override
			public Object getArray(Map<String, Class<?>> map) throws SQLException {
				throw new UnsupportedOperationException();
			}

			@Override
			public Object getArray(long index, int count, Map<String, Class<?>> map) throws SQLException {
				throw new UnsupportedOperationException();
			}

			@Override
			public int getBaseType() throws SQLException {
				return java.sql.Types.INTEGER;
			}

			@Override
			public String getBaseTypeName() throws SQLException {
				return "int4";
			}

			@Override
			public java.sql.ResultSet getResultSet() throws SQLException {
				throw new UnsupportedOperationException();
			}

			@Override
			public java.sql.ResultSet getResultSet(Map<String, Class<?>> map) throws SQLException {
				throw new UnsupportedOperationException();
			}

			@Override
			public java.sql.ResultSet getResultSet(long index, int count) throws SQLException {
				throw new UnsupportedOperationException();
			}

			@Override
			public java.sql.ResultSet getResultSet(long index, int count, Map<String, Class<?>> map)
					throws SQLException {
				throw new UnsupportedOperationException();
			}

			@Override
			public void free() throws SQLException {
			}
		};
	}

	@Test
	public void fakeArrayReturningPrimitiveToListAndSet() throws Exception {
		// 旧实现此处抛ClassCastException:int[]无法强转Object[]
		Object list = BeanUtil.convertType(null, primitiveArrayResult(), java.sql.Types.OTHER, DataType.listType,
				"java.util.List", Integer.class);
		assertEquals(Arrays.asList(1, 2), list, "List<Integer>:Array.getArray()返回int[]应装箱转换");
		Object set = BeanUtil.convertType(null, primitiveArrayResult(), java.sql.Types.OTHER, DataType.setType,
				"java.util.Set", Integer.class);
		// jre8同步适配:Set.of(JDK9)→HashSet(Arrays.asList(..))
		assertEquals(new HashSet<>(Arrays.asList(1, 2)), set, "Set<Integer>:Array.getArray()返回int[]应装箱转换");
	}

	@Test
	public void primitiveArrayDirectToList() throws Exception {
		// 归一化透传的原始数组:旧实现漏转导致setter报argument type mismatch
		Object list = BeanUtil.convertType(null, new int[] { 3, 4 }, java.sql.Types.OTHER, DataType.listType,
				"java.util.List", Integer.class);
		assertEquals(Arrays.asList(3, 4), list, "int[]直传List<Integer>应装箱转换");
	}

	@Test
	public void boxedArrayPassthroughUnchanged() throws Exception {
		// 装箱数组(主流驱动实际形态)行为不变:直通+泛型元素转换
		Object list = BeanUtil.convertType(null, new String[] { "a", "b" }, java.sql.Types.OTHER, DataType.listType,
				"java.util.List", String.class);
		assertEquals(Arrays.asList("a", "b"), list, "String[]直传List<String>保持原行为");
		Object longs = BeanUtil.convertType(null, new Long[] { 5L }, java.sql.Types.OTHER, DataType.listType,
				"java.util.List", Long.class);
		assertEquals(Arrays.asList(5L), longs, "Long[]直传List<Long>保持原行为");
	}
}
