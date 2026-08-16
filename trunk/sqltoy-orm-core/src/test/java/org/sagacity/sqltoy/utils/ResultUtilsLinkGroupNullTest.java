package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.ResultSet;

import org.junit.jupiter.api.Test;

/**
 * 回归测试：link分组列为null时单列分支返回"null"文本(与多列拼接分支一致),
 * 调用方identity.equals(preIdentity)不再对null调equals抛NPE
 */
public class ResultUtilsLinkGroupNullTest {

	private static Object linkColumnsId(Object groupValue) throws Exception {
		ResultSet rs = (ResultSet) Proxy.newProxyInstance(ResultSetUtilsClassLatch.clazz().getClassLoader(),
				new Class[] { ResultSet.class }, (proxy, method, args) -> {
					if ("getObject".equals(method.getName())) {
						return groupValue;
					}
					return null;
				});
		Method method = ResultSetUtilsClassLatch.clazz().getDeclaredMethod("getLinkColumnsId", ResultSet.class,
				String[].class);
		method.setAccessible(true);
		return method.invoke(null, rs, new String[] { "group_col" });
	}

	// 独立持有Class引用,避免测试类自身被ResultUtils同名混淆
	static class ResultSetUtilsClassLatch {
		static Class<?> clazz() {
			return ResultUtils.class;
		}
	}

	@Test
	public void nullGroupColumnReturnsTextNotNull() throws Exception {
		// 修复前:单列分组直接返回rs.getObject即null → 调用方 identity.equals(preIdentity) NPE
		Object identity = assertDoesNotThrow(() -> linkColumnsId(null));
		assertEquals("null", identity);
	}

	@Test
	public void normalGroupColumnUnchanged() throws Exception {
		assertEquals("G001", linkColumnsId("G001"));
		assertEquals(Integer.valueOf(5), linkColumnsId(5));
	}

	@Test
	public void identityNeverNullSoEqualsSafe() throws Exception {
		// 模拟首行:identity="null"文本 vs preIdentity=null(Object),equals不抛异常且判不等
		Object identity = linkColumnsId(null);
		Object preIdentity = null;
		// identity不可能为null,equals安全;首行必然不等(进入分组切换分支,行为正确)
		org.junit.jupiter.api.Assertions.assertNotEquals(preIdentity, identity);
		org.junit.jupiter.api.Assertions.assertTrue(identity.equals(preIdentity) == false);
	}
}
