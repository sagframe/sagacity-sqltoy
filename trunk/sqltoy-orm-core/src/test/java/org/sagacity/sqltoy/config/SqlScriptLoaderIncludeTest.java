package org.sagacity.sqltoy.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlType;
import org.sagacity.sqltoy.plugins.function.FunctionUtils;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * 回归测试：@include展开(a)不再直接改写共享缓存实例,按命中key原子替换; (b)base
 * sql保持通用形态,不因首个查询方言固化函数转换结果; (c)参数化与方言变体模式行为不变
 */
public class SqlScriptLoaderIncludeTest {
	private SqlScriptLoader loader;
	private ConcurrentHashMap<String, SqlToyConfig> sqlCache;

	@SuppressWarnings("unchecked")
	@BeforeEach
	public void setUp() throws Exception {
		loader = new SqlScriptLoader();
		Field field = SqlScriptLoader.class.getDeclaredField("sqlCache");
		field.setAccessible(true);
		sqlCache = (ConcurrentHashMap<String, SqlToyConfig>) field.get(loader);
	}

	@AfterEach
	public void tearDown() {
		// 恢复函数转换器为空,避免影响其他测试用例
		FunctionUtils.setFunctionConverts(new ArrayList<String>());
	}

	private SqlToyConfig seed(String sqlId, String sql, String dialect) {
		SqlToyConfig config = SqlConfigParseUtils.parseSqlToyConfig(sql, dialect, SqlType.search);
		config.setId(sqlId);
		if (StringUtil.matches(sql, org.sagacity.sqltoy.SqlToyConstants.INCLUDE_PATTERN)) {
			config.setHasIncludeSql(true);
		}
		sqlCache.put(sqlId, config);
		return config;
	}

	@Test
	public void includeExpandedAndCacheReplacedOnce() throws Exception {
		seed("cond_fragment", "staff_name like :staffName", null);
		seed("sysuser_query", "select * from sys_user_info where @include(\"cond_fragment\")", null);
		SqlToyConfig cfg = loader.getSqlConfig("sysuser_query", SqlType.search, "mysql", null, false);
		// include已展开
		assertTrue(cfg.getSql().contains("staff_name like :staffName"));
		assertFalse(cfg.isHasIncludeSql());
		// 命名参数同步更新
		assertTrue(Arrays.asList(cfg.getParamsName()).contains("staffName"));
		// 缓存条目被替换为已解析对象,后续调用走快速路径
		assertFalse(sqlCache.get("sysuser_query").isHasIncludeSql());
		SqlToyConfig cfgAgain = loader.getSqlConfig("sysuser_query", SqlType.search, "mysql", null, false);
		assertEquals(cfg.getSql(), cfgAgain.getSql());
	}

	@Test
	public void baseSqlNotPollutedByFirstQueryDialect() throws Exception {
		// 注册默认函数转换器(isnull -> mysql ifnull / oracle nvl)
		List<String> functions = new ArrayList<String>();
		for (String function : FunctionUtils.functions) {
			functions.add(function);
		}
		FunctionUtils.setFunctionConverts(functions);
		seed("cond_fragment", "staff_name like :staffName", null);
		seed("sysuser_query",
				"select isnull(staff_name,'-') staff_name from sys_user_info where @include(\"cond_fragment\")", null);
		// 首次以mysql方言触发展开
		SqlToyConfig cfg = loader.getSqlConfig("sysuser_query", SqlType.search, "mysql", null, false);
		// base sql必须保持通用形态:修复前此处已被mysql固化为ifnull,其他方言查询将产生错误SQL
		assertTrue(cfg.getSql().contains("isnull"), "base sql应保持通用的isnull");
		assertFalse(cfg.getSql().contains("ifnull"), "base sql不应被首个查询方言固化");
		// 查询时按方言惰性转换且各方言正确
		assertTrue(cfg.getSql("mysql").contains("ifnull"));
		assertTrue(cfg.getSql("oracle").contains("nvl"));
		// 缓存中的base同样未被固化
		assertTrue(sqlCache.get("sysuser_query").getSql().contains("isnull"));
	}

	@Test
	public void dialectVariantResolvedUnderHitKey() throws Exception {
		seed("cond_fragment", "staff_name like :staffName", null);
		// 方言变体条目:sqlId_mysql
		seed("sysuser_mysql", "select * from sys_user_info where @include(\"cond_fragment\")", "mysql");
		SqlToyConfig cfg = loader.getSqlConfig("sysuser", SqlType.search, "mysql", null, false);
		assertTrue(cfg.getSql().contains("staff_name like :staffName"));
		// 解析结果必须回写到实际命中的变体key,而不是新增基础key
		assertFalse(sqlCache.get("sysuser_mysql").isHasIncludeSql());
		assertFalse(sqlCache.containsKey("sysuser"));
	}

	@Test
	public void parametricIncludeResolvedPerCallAndNotCached() throws Exception {
		seed("dyn_query", "select * from sys_user_info where @include(:part)", null);
		// 参数化include的参数值是sql片段文本本身,随每次调用变化
		Map<String, Object> params = new HashMap<String, Object>();
		params.put("part", "staff_name like :staffName");
		assertTrue(loader.getSqlConfig("dyn_query", SqlType.search, null, params, false).getSql()
				.contains("staff_name like :staffName"));
		params.put("part", "staff_code like :staffCode");
		assertTrue(loader.getSqlConfig("dyn_query", SqlType.search, null, params, false).getSql()
				.contains("staff_code like :staffCode"));
		// 参数化模式sql随参数变化,不回写缓存,下次调用仍会展开
		assertTrue(sqlCache.get("dyn_query").isHasIncludeSql());
	}

	@Test
	public void concurrentFirstHitAlwaysGetsCompleteSql() throws Exception {
		seed("cond_fragment", "staff_name like :staffName", null);
		seed("sysuser_query", "select * from sys_user_info where @include(\"cond_fragment\")", null);
		int threads = 8;
		int loops = 50;
		AtomicInteger failures = new AtomicInteger();
		CountDownLatch latch = new CountDownLatch(threads);
		for (int t = 0; t < threads; t++) {
			new Thread(() -> {
				try {
					for (int i = 0; i < loops; i++) {
						SqlToyConfig cfg = loader.getSqlConfig("sysuser_query", SqlType.search, "mysql", null, false);
						if (cfg == null || !cfg.getSql().contains("staff_name like :staffName")) {
							failures.incrementAndGet();
						}
					}
				} catch (Exception e) {
					failures.incrementAndGet();
				} finally {
					latch.countDown();
				}
			}).start();
		}
		latch.await();
		assertEquals(0, failures.get());
		assertFalse(sqlCache.get("sysuser_query").isHasIncludeSql());
	}
}
