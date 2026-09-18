package org.sagacity.sqltoy.plugins;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.utils.IdUtil;

/**
 * 主键值生成策略的单元测试
 */
public class IdGeneratorTest {
	@Test
	public void testDefaultId() {
		String id = IdUtil.getShortNanoTimeId(null).toPlainString();
		System.out.println(id);
	}
}
