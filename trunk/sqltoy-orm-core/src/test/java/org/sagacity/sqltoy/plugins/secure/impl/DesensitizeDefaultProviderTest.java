package org.sagacity.sqltoy.plugins.secure.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.SecureMask;

/**
 * 回归测试：email类型脱敏遇到无@的脏数据时不再substring(-1)越界(修复前一条脏数据
 * 中断整批结果脱敏),退化为首字符+掩码;正常邮箱脱敏结果不变
 */
public class DesensitizeDefaultProviderTest {

	private final DesensitizeDefaultProvider provider = new DesensitizeDefaultProvider();

	private SecureMask emailMask() {
		SecureMask mask = new SecureMask();
		mask.setType("email");
		return mask;
	}

	@Test
	public void normalEmailMaskUnchanged() {
		assertEquals("z***@gmail.com", provider.desensitize("zhangsan@gmail.com", emailMask()));
	}

	@Test
	public void dirtyValueWithoutAtSignDegradesGracefully() {
		// 修复前:StringIndexOutOfBoundsException
		assertEquals("n***", provider.desensitize("notanemail", emailMask()));
	}

	@Test
	public void blankContentPassThrough() {
		assertEquals(null, provider.desensitize(null, emailMask()));
		assertEquals("", provider.desensitize("", emailMask()));
	}
}
