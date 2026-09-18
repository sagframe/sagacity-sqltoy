package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.plugins.TypeHandler;

/**
 * 回归测试：record属性与查询列匹配后,列类型必须取匹配列(j)而不是record属性下标(i),
 * 否则列序与属性序不一致时类型探测取错列,record字段多于查询列时直接数组越界
 */
public class BeanUtilRecordColumnTypesTest {

	/**
	 * 3个record字段对应2个查询列:remark无匹配列(容忍为null),ext匹配第2列;
	 * 修复前i=2时读columnTypes[2]抛ArrayIndexOutOfBoundsException
	 */
	public record OrderInfo(String id, String remark, String ext) {
	}

	/**
	 * 含泛型字段的record:修复前getRecordFields对List<String>取getGenericType()
	 * 得到ParameterizedType,强转Class直接抛ClassCastException
	 */
	public record StaffInfo(String id, List<String> roles) {
	}

	@SuppressWarnings("unchecked")
	private List<Object> invoke(Collection datas, int[] indexs, String[] properties, String[] columnTypes,
			Class recordType) throws Exception {
		Method method = BeanUtil.class.getDeclaredMethod("reflectListToRecord", TypeHandler.class, Collection.class,
				int[].class, String[].class, String[].class, Class.class, boolean.class);
		method.setAccessible(true);
		return (List<Object>) method.invoke(null, null, datas, indexs, properties, columnTypes, recordType, true);
	}

	@Test
	public void recordWithMoreFieldsThanColumnsMapsWithoutError() throws Exception {
		Collection datas = new ArrayList<List<Object>>();
		datas.add(new ArrayList<>(List.of("A001", "{\"k\":1}")));
		List<Object> result = invoke(datas, new int[] { 0, 1 }, new String[] { "ID", "EXT" },
				new String[] { "varchar", "json" }, OrderInfo.class);
		OrderInfo order = (OrderInfo) result.get(0);
		assertEquals("A001", order.id());
		// 未匹配列的字段容忍为null
		assertEquals(null, order.remark());
		assertEquals("{\"k\":1}", order.ext());
	}

	@Test
	public void reversedColumnOrderStillMapsByMatchedColumn() throws Exception {
		// 查询列顺序(EXT在前ID在后)与record属性顺序(id在前)相反,值必须按列名正确对应
		Collection datas = new ArrayList<List<Object>>();
		datas.add(new ArrayList<>(List.of("hello", "A002")));
		List<Object> result = invoke(datas, new int[] { 0, 1 }, new String[] { "EXT", "ID" },
				new String[] { "json", "varchar" }, OrderInfo.class);
		OrderInfo order = (OrderInfo) result.get(0);
		assertEquals("A002", order.id());
		assertEquals("hello", order.ext());
	}

	@Test
	public void recordWithGenericFieldMaps() throws Exception {
		Collection datas = new ArrayList<List<Object>>();
		datas.add(new ArrayList<>(Arrays.asList("S001", null)));
		List<Object> result = invoke(datas, new int[] { 0, 1 }, new String[] { "ID", "ROLES" },
				new String[] { "varchar", "varchar" }, StaffInfo.class);
		StaffInfo staff = (StaffInfo) result.get(0);
		assertEquals("S001", staff.id());
		assertEquals(null, staff.roles());
	}
}
