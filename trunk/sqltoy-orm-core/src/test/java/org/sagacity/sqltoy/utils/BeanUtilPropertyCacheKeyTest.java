package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.KeyAndIndex;

/**
 * @project sagacity-sqltoy
 * @description 属性取值两处微优化后的行为校验：不含数组下标的属性不再走正则；getProperty 与
 *              getComplexProperty 共用同一份 get 方法缓存(缓存键改为属性名本身，去掉 ":get" 前缀)。
 * @author zhongxuchen
 */
public class BeanUtilPropertyCacheKeyTest {
	public static class Holder implements Serializable {
		private static final long serialVersionUID = 1L;
		private String name = "n";
		private List<String> tags = new ArrayList<>(Arrays.asList("a", "b", "c"));

		public String getName() {
			return name;
		}

		public List<String> getTags() {
			return tags;
		}
	}

	@Test
	public void propertyWithoutArrayIndexIsNotRegex() {
		assertNull(BeanUtil.getKeyAndIndex("name"));
		assertNull(BeanUtil.getKeyAndIndex(""));
		// 含'['但不符合 [数字] 结尾：仍按原逻辑返回null
		assertNull(BeanUtil.getKeyAndIndex("tags["));
		assertNull(BeanUtil.getKeyAndIndex("tags[x]"));
		KeyAndIndex keyAndIndex = BeanUtil.getKeyAndIndex("tags[1]");
		assertEquals("tags", keyAndIndex.getKey());
		assertEquals(1, keyAndIndex.getIndex());
	}

	@Test
	public void getPropertyAndComplexPropertyShareGetterCache() {
		Holder holder = new Holder();
		// 先走 getProperty 填充缓存，再走 getComplexProperty 应命中同一条目
		assertEquals("n", BeanUtil.getProperty(holder, "name"));
		assertEquals("n", BeanUtil.getComplexProperty(holder, "name"));
		// 反向顺序：含数组下标取值后再按同名属性取整个列表
		assertEquals("b", BeanUtil.getComplexProperty(holder, "tags[1]"));
		assertEquals(Arrays.asList("a", "b", "c"), BeanUtil.getProperty(holder, "tags"));
	}
}