package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * 回归测试：普通Map行缺失某属性key时必须补null占位,保证每行长度与属性数一致, 修复前行长度不足,后续按下标消费整体左移错位
 */
public class BeanUtilMapRowAlignmentTest {

	@Test
	public void missingMapKeyPaddedWithNull() {
		Map<String, Object> row1 = new HashMap<String, Object>();
		row1.put("name", "alice");
		row1.put("age", 20);
		Map<String, Object> row2 = new HashMap<String, Object>();
		row2.put("name", "bob");
		// row2 缺失 age
		List datas = new ArrayList();
		datas.add(row1);
		datas.add(row2);
		List result = BeanUtil.reflectBeansToList(datas, new String[] { "name", "age" });
		// 两行长度都必须等于属性数
		assertEquals(2, ((List) result.get(0)).size());
		assertEquals(2, ((List) result.get(1)).size());
		// 缺失列在正确位置为null,后续列不左移
		assertEquals("bob", ((List) result.get(1)).get(0));
		assertNull(((List) result.get(1)).get(1));
	}

	@Test
	public void matchedKeyStillCaseInsensitive() {
		Map<String, Object> row = new HashMap<String, Object>();
		row.put("NAME", "carl");
		List datas = new ArrayList();
		datas.add(row);
		List result = BeanUtil.reflectBeansToList(datas, new String[] { "name" });
		assertEquals("carl", ((List) result.get(0)).get(0));
	}

	@Test
	public void beanRowsBehaviorUnchanged() {
		// bean分支行为对照:无对应getter的属性本来就补null
		List datas = new ArrayList();
		datas.add(new SampleBean());
		List result = BeanUtil.reflectBeansToList(datas, new String[] { "name", "notExists" });
		assertEquals(2, ((List) result.get(0)).size());
		assertEquals("dave", ((List) result.get(0)).get(0));
		assertNull(((List) result.get(0)).get(1));
	}

	public static class SampleBean {
		public String getName() {
			return "dave";
		}
	}
}
