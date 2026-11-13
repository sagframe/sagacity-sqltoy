package org.sagacity.sqltoy.dialect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.sql.DataSource;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.model.OperateType;
import org.sagacity.sqltoy.config.model.PageOptimize;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.model.QueryExecutor;
import org.sagacity.sqltoy.plugins.IUnifyFieldsHandler;

/**
 * 分页优化count缓存的命名空间: 实际数据源与方言必须参与隔离,否则同一sqlId在多数据源/多方言下
 * 会复用其他库的总记录数;租户key沿用旧逻辑,存在查询参数时追加authTenants
 */
public class PageOptimizeUtilsNamespaceTest {

	private static final String SQL_ID = "sqltoy_page_optimize_namespace_test";

	private static class MutableTenantHandler implements IUnifyFieldsHandler {
		private String[] tenants;

		MutableTenantHandler tenants(String... tenants) {
			this.tenants = tenants;
			return this;
		}

		@Override
		public String[] authTenants(Class entityClass, OperateType operType) {
			return tenants;
		}
	}

	/** 按operateType返回不同租户集,并记录最近一次被调用的operateType(验证key与count SQL装饰同口径) */
	private static class OperateTypeAwareTenantHandler implements IUnifyFieldsHandler {
		private OperateType lastOperType;

		@Override
		public String[] authTenants(Class entityClass, OperateType operType) {
			this.lastOperType = operType;
			if (operType == OperateType.page) {
				return new String[] { "PAGE_T" };
			}
			if (operType == OperateType.count) {
				return new String[] { "COUNT_T" };
			}
			if (operType == OperateType.singleTable) {
				return new String[] { "ENTITY_T" };
			}
			return new String[] { "OTHER_T" };
		}
	}

	private static class DemoVo {
	}

	private static SqlToyConfig config() {
		return new SqlToyConfig(SQL_ID, "select * from t_demo");
	}

	private static PageOptimize pageOptimize() {
		return new PageOptimize().aliveMax(10).aliveSeconds(60);
	}

	private static QueryExecutor queryExecutor() {
		QueryExecutor queryExecutor = new QueryExecutor("select * from t_demo");
		queryExecutor.getInnerModel().entityClass = DemoVo.class;
		return queryExecutor;
	}

	private static DataSource newDataSource() {
		return new JdbcDataSource();
	}

	@Test
	public void paramQueryAppendsAuthTenants() throws Exception {
		SqlToyContext context = new SqlToyContext();
		MutableTenantHandler handler = new MutableTenantHandler();
		context.setUnifyFieldsHandler(handler);
		handler.tenants("T1", "T2");
		QueryExecutor queryExecutor = queryExecutor();
		queryExecutor.getInnerModel().paramsName = new String[] { "status" };
		queryExecutor.getInnerModel().paramsValue = new Object[] { 1 };
		String key = PageOptimizeUtils.generateOptimizeKey(context, config(), queryExecutor, pageOptimize());
		assertTrue(key.contains("status=1"), key);
		assertTrue(key.contains("tenantIds=[T1,T2]"), "有查询参数时应追加authTenants:" + key);
	}

	@Test
	public void differentDataSourcesAreIsolated() throws Exception {
		SqlToyContext context = new SqlToyContext();
		DataSource dataSource1 = newDataSource();
		DataSource dataSource2 = newDataSource();
		SqlToyConfig config = config();
		PageOptimize pageOptimize = pageOptimize();
		String conditionsKey = PageOptimizeUtils.generateOptimizeKey(context, config, queryExecutor(), pageOptimize);
		PageOptimizeUtils.remove(SQL_ID);
		try {
			PageOptimizeUtils.registPageTotalCount(config, pageOptimize, conditionsKey, 3L, "h2", context,
					dataSource1);
			assertNull(PageOptimizeUtils.getPageTotalCount(config, pageOptimize, conditionsKey, "h2", context,
					dataSource2), "另一个数据源不能命中该数据源的count");
			assertEquals(Long.valueOf(3L), PageOptimizeUtils.getPageTotalCount(config, pageOptimize, conditionsKey,
					"h2", context, dataSource1));
		} finally {
			PageOptimizeUtils.remove(SQL_ID);
		}
	}

	@Test
	public void sameUrlAndSessionSharesNamespaceAcrossDataSources() throws Exception {
		// update 2026-11-11 稳定身份:url+账号/schema/catalog相同即同一逻辑库,连接池重建/多池可复用count;
		// 不再依赖数据源对象身份(避免长生存数据源使命名空间无限增长)
		String url = "jdbc:h2:mem:page_opt_url_identity;DB_CLOSE_DELAY=-1";
		JdbcDataSource dataSource1 = new JdbcDataSource();
		dataSource1.setURL(url);
		dataSource1.setUser("sa");
		JdbcDataSource dataSource2 = new JdbcDataSource();
		dataSource2.setURL(url);
		dataSource2.setUser("sa");
		try (java.sql.Connection conn1 = dataSource1.getConnection();
				java.sql.Connection conn2 = dataSource2.getConnection()) {
			org.sagacity.sqltoy.model.DBProfile profile = new org.sagacity.sqltoy.model.DBProfile(url, "h2",
					org.sagacity.sqltoy.utils.DataSourceUtils.DBType.H2, "H2", 2, null, null, false);
			String key1 = PageOptimizeUtils.buildDataSourceKey(profile, conn1, dataSource1);
			String key2 = PageOptimizeUtils.buildDataSourceKey(profile, conn2, dataSource2);
			assertEquals(key1, key2, "同url同账号/schema应得到同一稳定身份");
			// 不同url必须隔离
			org.sagacity.sqltoy.model.DBProfile otherProfile = new org.sagacity.sqltoy.model.DBProfile(
					"jdbc:h2:mem:page_opt_url_identity_other;DB_CLOSE_DELAY=-1", "h2",
					org.sagacity.sqltoy.utils.DataSourceUtils.DBType.H2, "H2", 2, null, null, false);
			assertFalse(key1.equals(PageOptimizeUtils.buildDataSourceKey(otherProfile, conn1, dataSource1)),
					"不同url身份必须不同");
			// 无url档案时返回null,调用侧回退对象身份
			assertNull(PageOptimizeUtils.buildDataSourceKey(null, conn1, dataSource1));
			// 两个不同数据源对象、同一稳定身份 => 共用count
			SqlToyContext context = new SqlToyContext();
			SqlToyConfig config = config();
			PageOptimize pageOptimize = pageOptimize();
			PageOptimizeUtils.remove(SQL_ID);
			try {
				PageOptimizeUtils.registPageTotalCount(config, pageOptimize, "conditions", 9L, "h2", context,
						dataSource1, key1);
				assertEquals(Long.valueOf(9L), PageOptimizeUtils.getPageTotalCount(config, pageOptimize, "conditions",
						"h2", context, dataSource2, key2), "同url+会话身份的不同数据源应复用count");
			} finally {
				PageOptimizeUtils.remove(SQL_ID);
			}
		}
	}

	@Test
	public void schemaSwitchOnSamePoolChangesIdentity() throws Exception {
		// update 2026-11-12 同一连接池经setSchema切换多租户时身份必须随会话变化,
		// 否则首次会话被永久钉死,不同schema共用count缓存(租户串用)
		String url = "jdbc:h2:mem:page_opt_schema_switch;DB_CLOSE_DELAY=-1";
		JdbcDataSource dataSource = new JdbcDataSource();
		dataSource.setURL(url);
		dataSource.setUser("sa");
		try (java.sql.Connection conn = dataSource.getConnection()) {
			org.sagacity.sqltoy.model.DBProfile profile = new org.sagacity.sqltoy.model.DBProfile(url, "h2",
					org.sagacity.sqltoy.utils.DataSourceUtils.DBType.H2, "H2", 2, null, null, false);
			String keyDefault = PageOptimizeUtils.buildDataSourceKey(profile, conn, dataSource);
			try (java.sql.Statement stmt = conn.createStatement()) {
				stmt.execute("create schema if not exists tenant_b");
			}
			conn.setSchema("TENANT_B");
			String keyTenantB = PageOptimizeUtils.buildDataSourceKey(profile, conn, dataSource);
			assertFalse(keyDefault.equals(keyTenantB), "schema切换后count缓存身份必须变化");
			conn.setSchema("PUBLIC");
			assertEquals(keyDefault, PageOptimizeUtils.buildDataSourceKey(profile, conn, dataSource),
					"切回原schema身份应恢复");
		}
	}

	@Test
	public void namespaceCacheIsBoundedByLru() {
		// update 2026-11-11 命名空间硬上限:大量长生存数据源/账号时不线性增长,超限按最近访问淘汰(只影响命中率)
		SqlToyContext context = new SqlToyContext();
		DataSource dataSource = newDataSource();
		PageOptimize pageOptimize = pageOptimize();
		int total = PageOptimizeUtils.NAMESPACE_MAX_SIZE + 20;
		SqlToyConfig first = null;
		SqlToyConfig last = null;
		for (int i = 0; i < total; i++) {
			SqlToyConfig config = new SqlToyConfig("sqltoy_page_lru_" + i, "select * from t_demo");
			if (i == 0) {
				first = config;
			}
			last = config;
			PageOptimizeUtils.registPageTotalCount(config, pageOptimize, "conditions", (long) i, "h2", context,
					dataSource);
		}
		assertTrue(PageOptimizeUtils.namespaceCount() <= PageOptimizeUtils.NAMESPACE_MAX_SIZE,
				"命名空间数量不得超过上限: " + PageOptimizeUtils.namespaceCount());
		assertNull(PageOptimizeUtils.getPageTotalCount(first, pageOptimize, "conditions", "h2", context, dataSource),
				"最久未访问(最早登记)的命名空间应被淘汰");
		assertEquals(Long.valueOf(total - 1),
				PageOptimizeUtils.getPageTotalCount(last, pageOptimize, "conditions", "h2", context, dataSource),
				"最新登记的命名空间应保留");
		for (int i = 0; i < total; i++) {
			PageOptimizeUtils.remove("sqltoy_page_lru_" + i);
		}
	}

	@Test
	public void blankUserTenantIdStillParticipatesInKey() throws Exception {
		// update 2026-11-11 拦截器对 getUserTenantId() 返回空串同样拼 tenant_id='' 过滤,
		// key 必须区分"空串租户"与"无租户",否则两者共用同一份count(跨租户复用)
		SqlToyContext blankContext = new SqlToyContext();
		blankContext.setUnifyFieldsHandler(new IUnifyFieldsHandler() {
			@Override
			public String getUserTenantId() {
				return "";
			}
		});
		String blankKey = PageOptimizeUtils.generateOptimizeKey(blankContext, config(), queryExecutor(), pageOptimize());
		assertTrue(blankKey.contains("tenantIds=[]"), "空串租户必须进入key: " + blankKey);

		SqlToyContext noneContext = new SqlToyContext();
		noneContext.setUnifyFieldsHandler(new IUnifyFieldsHandler() {
		});
		String noneKey = PageOptimizeUtils.generateOptimizeKey(noneContext, config(), queryExecutor(), pageOptimize());
		assertFalse(noneKey.contains("tenantIds="), "无租户不应含租户段: " + noneKey);
		assertFalse(blankKey.equals(noneKey), "空串租户与无租户不能共用count缓存");
	}

	@Test
	public void blankAuthTenantEntryIsNotDropped() throws Exception {
		// authTenants 数组中的空串同样会进入拦截器 in (...) 条件,key不能丢弃
		SqlToyContext context = new SqlToyContext();
		MutableTenantHandler handler = new MutableTenantHandler();
		context.setUnifyFieldsHandler(handler);
		handler.tenants("", "T1");
		String key = PageOptimizeUtils.generateOptimizeKey(context, config(), queryExecutor(), pageOptimize());
		assertTrue(key.contains("tenantIds=[,T1]"), "authTenants中的空串不能丢弃(拦截器会拼in('',T1)): " + key);
	}

	@Test
	public void entityCountKeyUsesSingleTableOperateType() throws Exception {
		// update 2026-11-11 count SQL在DialectFactory按(实体?singleTable:count)装饰租户过滤,
		// 缓存key必须同口径:实体查询走singleTable而非page,否则handler按operateType返回不同租户时
		// key与实际过滤维度错位,导致跨租户复用count
		SqlToyContext context = new SqlToyContext();
		OperateTypeAwareTenantHandler handler = new OperateTypeAwareTenantHandler();
		context.setUnifyFieldsHandler(handler);
		String key = PageOptimizeUtils.generateOptimizeKey(context, config(), queryExecutor(), pageOptimize());
		assertEquals(OperateType.singleTable, handler.lastOperType,
				"实体count缓存key须按singleTable取授权租户(与count SQL装饰一致)");
		assertTrue(key.contains("tenantIds=[ENTITY_T]"), "key应含singleTable租户集: " + key);
		assertFalse(key.contains("PAGE_T"), "key不得使用page口径租户: " + key);
	}

	@Test
	public void nonEntityCountKeyUsesCountOperateType() throws Exception {
		// 非实体查询(entityClass为null),count SQL按count装饰,key须同口径
		SqlToyContext context = new SqlToyContext();
		OperateTypeAwareTenantHandler handler = new OperateTypeAwareTenantHandler();
		context.setUnifyFieldsHandler(handler);
		QueryExecutor queryExecutor = new QueryExecutor("select * from t_demo");
		String key = PageOptimizeUtils.generateOptimizeKey(context, config(), queryExecutor, pageOptimize());
		assertEquals(OperateType.count, handler.lastOperType,
				"非实体count缓存key须按count取授权租户(与count SQL装饰一致)");
		assertTrue(key.contains("tenantIds=[COUNT_T]"), "key应含count租户集: " + key);
		assertFalse(key.contains("PAGE_T"), "key不得使用page口径租户: " + key);
	}

	@Test
	public void contextDefaultDataSourceIsUsedWhenNoExplicitDataSource() throws Exception {
		SqlToyContext context = new SqlToyContext();
		DataSource dataSource1 = newDataSource();
		DataSource dataSource2 = newDataSource();
		context.setDefaultDataSource(dataSource1);
		SqlToyConfig config = config();
		PageOptimize pageOptimize = pageOptimize();
		String conditionsKey = PageOptimizeUtils.generateOptimizeKey(context, config, queryExecutor(), pageOptimize);
		PageOptimizeUtils.remove(SQL_ID);
		try {
			// 登记时未显式传数据源,应落到context的默认数据源上
			PageOptimizeUtils.registPageTotalCount(config, pageOptimize, conditionsKey, 11L, "h2", context, null);
			assertEquals(Long.valueOf(11L), PageOptimizeUtils.getPageTotalCount(config, pageOptimize, conditionsKey,
					"h2", context, dataSource1), "显式传默认数据源应命中同一命名空间");
			assertNull(PageOptimizeUtils.getPageTotalCount(config, pageOptimize, conditionsKey, "h2", context,
					dataSource2));
		} finally {
			PageOptimizeUtils.remove(SQL_ID);
		}
	}

	@Test
	public void removeClearsEveryDataSourceNamespace() {
		SqlToyContext context = new SqlToyContext();
		DataSource dataSource1 = newDataSource();
		DataSource dataSource2 = newDataSource();
		SqlToyConfig config = config();
		PageOptimize pageOptimize = pageOptimize();
		PageOptimizeUtils.remove(SQL_ID);
		PageOptimizeUtils.registPageTotalCount(config, pageOptimize, "conditions", 1L, "h2", context, dataSource1);
		PageOptimizeUtils.registPageTotalCount(config, pageOptimize, "conditions", 2L, "mysql", context, dataSource2);

		PageOptimizeUtils.remove(SQL_ID);

		assertNull(PageOptimizeUtils.getPageTotalCount(config, pageOptimize, "conditions", "h2", context, dataSource1));
		assertNull(
				PageOptimizeUtils.getPageTotalCount(config, pageOptimize, "conditions", "mysql", context, dataSource2));
	}
}
