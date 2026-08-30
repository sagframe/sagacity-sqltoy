package org.sagacity.sqltoy.config;

import static org.junit.jupiter.api.Assertions.assertNotSame;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlType;

/**
 * 回归测试：代码sql缓存(codeSqlCache)的blankToNull维度隔离
 * blank过滤器固化在缓存实例上,修复前同一sql先true后false会命中带过滤器的缓存条目,
 * 导致false调用时空白参数被静默过滤;修复后blankToNull参与缓存key(true原key,false尾部\u0000标记)
 */
public class SqlScriptLoaderCodeSqlCacheTest {

	private SqlScriptLoader loader = new SqlScriptLoader();

	private static final String RAW_SQL = "select * from sqltoy_staff_info where staff_name=:name";

	/**
	 * blankToNull=true:缓存实例带blank过滤器,重复调用命中同一实例(缓存仍生效)
	 */
	@Test
	public void blankToNullTrueHasFilterAndCaches() {
		SqlToyConfig first = loader.getSqlConfig(RAW_SQL, SqlType.search, null, null, true);
		assertTrue(first.getFilters() != null && first.getFilters().size() == 1,
				"blankToNull=true should carry one blank filter, got: " + first.getFilters());
		SqlToyConfig again = loader.getSqlConfig(RAW_SQL, SqlType.search, null, null, true);
		assertSame(first, again, "same sql + same blankToNull should hit the same cached instance");
	}

	/**
	 * 同一sql先true后false:false调用应拿到不带过滤器的独立实例(修复前命中true的缓存条目)
	 */
	@Test
	public void blankToNullFalseGetsIndependentEntry() {
		SqlToyConfig withFilter = loader.getSqlConfig(RAW_SQL, SqlType.search, null, null, true);
		SqlToyConfig withoutFilter = loader.getSqlConfig(RAW_SQL, SqlType.search, null, null, false);
		assertNotSame(withFilter, withoutFilter, "different blankToNull should use independent cache entries");
		assertTrue(withoutFilter.getFilters() == null || withoutFilter.getFilters().isEmpty(),
				"blankToNull=false entry must not carry the blank filter, got: " + withoutFilter.getFilters());
	}

	/**
	 * 未传dialect的调用路径不受影响(null方言被归一成空串标签,缓存条目正常)
	 */
	@Test
	public void blankToNullTrueKeepsPlainKey() {
		SqlToyConfig config = loader.getSqlConfig(RAW_SQL, SqlType.search, null, null, true);
		assertTrue(config.getDialect() == null || config.getDialect().isEmpty(),
				"no dialect passed, label should be blank, got: " + config.getDialect());
	}
}
