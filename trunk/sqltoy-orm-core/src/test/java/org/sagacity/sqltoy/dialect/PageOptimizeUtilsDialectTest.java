package org.sagacity.sqltoy.dialect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.PageOptimize;
import org.sagacity.sqltoy.config.model.SqlToyConfig;

/**
 * 分页优化缓存按方言隔离:同一 sqlId/条件在不同方言下 count 不得互相串用,
 * remove 需清理该 sqlId 的全部方言缓存
 */
public class PageOptimizeUtilsDialectTest {

	@Test
	public void dialectIsolatedAndRemoveClearsAllDialects() {
		String sqlId = "sqltoy_page_optimize_dialect_test";
		SqlToyConfig config = new SqlToyConfig(sqlId, "select 1");
		PageOptimize pageOptimize = new PageOptimize().aliveMax(10).aliveSeconds(60);
		PageOptimizeUtils.remove(sqlId);
		try {
			PageOptimizeUtils.registPageTotalCount(config, pageOptimize, "same-conditions", 10L, "mysql");
			PageOptimizeUtils.registPageTotalCount(config, pageOptimize, "same-conditions", 20L, "oracle");

			assertEquals(Long.valueOf(10L),
					PageOptimizeUtils.getPageTotalCount(config, pageOptimize, "same-conditions", "mysql"));
			assertEquals(Long.valueOf(10L),
					PageOptimizeUtils.getPageTotalCount(config, pageOptimize, "same-conditions", "MYSQL"),
					"方言大小写不应产生第二份缓存");
			assertEquals(Long.valueOf(20L),
					PageOptimizeUtils.getPageTotalCount(config, pageOptimize, "same-conditions", "oracle"));
			assertNull(PageOptimizeUtils.getPageTotalCount(config, pageOptimize, "same-conditions", "postgresql"),
					"未写入的方言不应命中其他方言的 count");
		} finally {
			PageOptimizeUtils.remove(sqlId);
		}
		assertNull(PageOptimizeUtils.getPageTotalCount(config, pageOptimize, "same-conditions", "mysql"));
		assertNull(PageOptimizeUtils.getPageTotalCount(config, pageOptimize, "same-conditions", "oracle"));
	}
}
