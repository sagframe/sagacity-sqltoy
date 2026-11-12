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

	@Test
	public void tenantConditionInsertedBeforePageTail() {
		// update 2026-11-12 无where的实体分页SQL已带分页尾缀,租户条件必须插在其前:
		// 原实现concat到末尾,条件落在limit之后,串行/并行分页均报语法错(H2真库复现)
		String mysql = decorate(new TestContext("T1"), "select id,vc_col from staff_info  limit ? offset ?  ");
		assertTrue(mysql.contains("where TENANT_ID='T1' limit"), "租户条件应在limit前,实际:" + mysql);
		// sqlserver:order by + offset rows fetch next,取最早位置(order by)插入
		String ss = decorate(new TestContext("T1"),
				"select id from staff_info order by id offset ? rows fetch next ? rows only ");
		assertTrue(ss.indexOf("where TENANT_ID") > 0 && ss.indexOf("where TENANT_ID") < ss.indexOf("order by"),
				"租户条件应在order by前,实际:" + ss);
		// db2/oracle:fetch first尾缀
		String db2 = decorate(new TestContext("T1"), "select id from staff_info fetch first 10 rows only ");
		assertTrue(db2.contains("where TENANT_ID='T1' fetch"), "租户条件应在fetch前,实际:" + db2);
		// fetch_size类列名不误判为分页尾缀,仍追加末尾
		String col = decorate(new TestContext("T1"), "select fetch_size from staff_info");
		assertTrue(col.contains("from staff_info where TENANT_ID='T1'"), "列名fetch_size不应触发尾缀定位,实际:" + col);
		// 字面量内的limit不干扰定位(掩码口径):已有where走替换分支,租户条件在字面量条件之前
		String literal = decorate(new TestContext("T1"), "select id from staff_info where name='limit 5'");
		assertTrue(literal.contains("TENANT_ID='T1'") && literal.contains("name='limit 5'")
				&& literal.indexOf("TENANT_ID") < literal.indexOf("name="), "已有where走替换分支,实际:" + literal);
	}

	@Test
	public void pageTailAtEndOfSqlWithoutTrailingBlank() {
		// update 2026-11-12 评审R1:SQL以limit尾缀结束(无尾随空白)同样须命中——
		// findTop实体查询的MySQL/DB2/Oracle top SQL正是"... limit <topSize>"形态;
		// 多位数字limit也须命中(单位数字匹配会在"limit 10"的1后停下)
		String q = decorate(new TestContext("T1"), "select id from staff_info limit ?");
		assertTrue(q.contains("where TENANT_ID='T1' limit"), "limit ?结尾:" + q);
		String n = decorate(new TestContext("T1"), "select id from staff_info limit 10");
		assertTrue(n.contains("where TENANT_ID='T1' limit"), "limit 10结尾:" + n);
		String no = decorate(new TestContext("T1"), "select id from staff_info limit 10 offset 20");
		assertTrue(no.contains("where TENANT_ID='T1' limit"), "limit 10 offset 20结尾:" + no);
		String comma = decorate(new TestContext("T1"), "select id from staff_info limit ?,?");
		assertTrue(comma.contains("where TENANT_ID='T1' limit"), "limit ?,?形态:" + comma);
	}
}
