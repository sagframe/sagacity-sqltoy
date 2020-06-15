package org.sagacity.sqltoy.plugins;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.utils.IdUtil;

public class IdGeneratorTest {
	@Test
	public void testDefaultId() {
		String id = IdUtil.getShortNanoTimeId(null).toPlainString();
		System.out.println(id);
	}
}
