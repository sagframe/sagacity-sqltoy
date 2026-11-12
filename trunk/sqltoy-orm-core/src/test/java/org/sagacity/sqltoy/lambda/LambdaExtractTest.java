package org.sagacity.sqltoy.lambda;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.exception.DataAccessException;
import org.sagacity.sqltoy.lambda.toolkit.FieldNature;
import org.sagacity.sqltoy.lambda.toolkit.LambdaMeta;
import org.sagacity.sqltoy.utils.BeanUtil;

/**
 * @description lambda解析单元测试:方法引用到属性名/实体类的提取,以及解析缓存
 * @author zhongxuchen
 */
@DisplayName("LambdaUtils lambda解析测试")
public class LambdaExtractTest {

	public static class DemoUser {
		private String staffName;
		private boolean deleted;
		private String url;

		public String getStaffName() {
			return staffName;
		}

		public void setStaffName(String staffName) {
			this.staffName = staffName;
		}

		public boolean isDeleted() {
			return deleted;
		}

		public void setDeleted(boolean deleted) {
			this.deleted = deleted;
		}

		public String getURL() {
			return url;
		}

		public void setURL(String url) {
			this.url = url;
		}
	}

	@Test
	@DisplayName("getter方法引用提取属性名")
	public void testGetterReference() {
		assertEquals("staffName", LambdaUtils.extractProperty(DemoUser::getStaffName));
	}

	@Test
	@DisplayName("is前缀布尔getter提取属性名")
	public void testIsGetterReference() {
		assertEquals("deleted", LambdaUtils.extractProperty(DemoUser::isDeleted));
	}

	@Test
	@DisplayName("仅支持getter方法引用,表达式lambda不支持")
	public void testLambdaExpressionUnsupported() {
		// 表达式lambda编译后为synthetic方法,无法提取属性名,应快速失败
		assertThrows(DataAccessException.class,
				() -> LambdaUtils.extractProperty((DemoUser user) -> user.getStaffName()));
	}

	@Test
	@DisplayName("属性名与实体类二元组提取")
	public void testExtractNature() {
		FieldNature nature = LambdaUtils.extractNature(DemoUser::getStaffName);
		assertEquals(DemoUser.class, nature.entityClass());
		assertEquals("staffName", nature.fieldName());
	}

	@Test
	@DisplayName("同一方法引用重复解析命中缓存")
	public void testExtractCache() {
		LambdaFn<DemoUser, String> fn = DemoUser::getStaffName;
		LambdaMeta first = LambdaUtils.extract(fn);
		LambdaMeta second = LambdaUtils.extract(fn);
		assertSame(first, second);
		// 不同调用点的lambda解析结果内容一致但对象不同
		LambdaMeta other = LambdaUtils.extract(DemoUser::getStaffName);
		assertNotSame(first, other);
		assertEquals(first.getImplMethodName(), other.getImplMethodName());
	}

	@Test
	@DisplayName("getURL保持连续大写属性名")
	public void testPropertyNamerUpperSequence() {
		assertEquals("URL", LambdaUtils.extractProperty(DemoUser::getURL));
		assertEquals("url", BeanUtil.methodToProperty("getUrl"));
	}

	@Test
	@DisplayName("非getter/setter方法名解析抛出异常")
	public void testInvalidMethod() {
		assertThrows(DataAccessException.class, () -> BeanUtil.methodToProperty("execute"));
	}
}
