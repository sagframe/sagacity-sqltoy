/**
 * 
 */
package org.sagacity.sqltoy.utils;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.alibaba.fastjson.JSON;

/**
 * @project sagacity-sqltoy
 * @description 请在此说明类的功能
 * @author zhong
 * @version v1.0, Date:2020-9-27
 * @modify 2020-9-27,修改说明
 */
public class CollectionUtilTest {
	@Test
	public void testSortTree() {
		Object[][] treeArray = new Object[][] { { 2, 1, "" }, { 3, 2, "" }, { 4, 2, "" }, { 5, 3, "" }, { 6, 0, "" },
				{ 7, 0, "" }, { 8, 6, "" }, { 10, 6, "" }, { 9, 8, "" } };

		List treeList = CollectionUtil.arrayToDeepList(treeArray);
		List result = CollectionUtil.sortTreeList(treeList, (obj) -> {
			return new Object[] { ((List) obj).get(0), ((List) obj).get(1) };
		}, 1, 0);
		System.err.println(JSON.toJSONString(result));
	}
}
