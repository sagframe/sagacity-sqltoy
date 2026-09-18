package org.sagacity.sqltoy.plugins.interceptors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.plugins.IUnifyFieldsHandler;
import org.sagacity.sqltoy.config.model.OperateType;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlToyResult;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * 回归测试：租户拦截器对租户值做'→''转义(单租户与多租户in分支),
 * 含引号的租户id被禁锢在字面量内,不能改写SQL破坏租户隔离
 */
public class TenantFilterInterceptorTest {

	// 最小化SqlToyContext子类:提供UnifyFieldsHandler与含租户字段的EntityMeta
	static class TestContext extends SqlToyContext {
		private final String[] tenants;

		TestContext(String... tenants) {
			this.tenants = tenants;
		}

		@Override
		public org.sagacity.sqltoy.plugins.IUnifyFieldsHandler getUnifyFieldsHandler() {
			return new org.sagacity.sqltoy.plugins.IUnifyFieldsHandler() {
				@Override
				public String[] authTenants(Class entityClass, OperateType operateType) {
					return tenants;
				}

				@Override
				public String getUserTenantId() {
					return (tenants != null && tenants.length > 0) ? tenants[0] : null;
				}
			};
		}

		@Override
		public EntityMeta getEntityMeta(Class<?> entityClass) {
			EntityMeta meta = new EntityMeta();
			meta.setTableName("staff_info");
			// 模拟@Tenant(field="tenantId") + @Column(name="TENANT_ID")
			meta.setTenantField("tenantId");
			meta.addFieldMeta(new org.sagacity.sqltoy.config.model.FieldMeta("tenantId", "TENANT_ID", null, null,
					java.sql.Types.VARCHAR, true, false, 20, 0, 0));
			return meta;
		}
	}

	private static String decorate(SqlToyContext context, String sql) {
		TenantFilterInterceptor interceptor = new TenantFilterInterceptor();
		SqlToyResult result = interceptor.decorate(context, new SqlToyConfig("mysql"), OperateType.singleTable,
				new SqlToyResult(sql, new Object[0]), StaffBean.class, DBType.MYSQL);
		return result.getSql();
	}

	public static class StaffBean {
	}

	@Test
	public void singleTenantWithQuoteEscaped() {
		String sql = decorate(new TestContext("T001' or '1'='1"), "select * from staff_info");
		// 修复前:tenant_id='T001' or '1'='1' → or条件逃出字面量,租户隔离被破坏
		assertTrue(sql.contains("'T001'' or ''1''=''1'"), "实际:" + sql);
		assertFalse(sql.contains("'T001' or"), "未转义的引号不应出现,实际:" + sql);
	}

	@Test
	public void multiTenantInWithQuoteEscaped() {
		String sql = decorate(new TestContext("A1", "B'2"), "select * from staff_info");
		// in分支每个值独立转义
		assertTrue(sql.contains("'A1'") && sql.contains("'B''2'"), "实际:" + sql);
		assertFalse(sql.contains("'B'2"), "实际:" + sql);
	}

	@Test
	public void normalTenantUnchanged() {
		String sql = decorate(new TestContext("T001"), "select * from staff_info");
		assertTrue(sql.contains("TENANT_ID='T001'"), "实际:" + sql);
	}
}
