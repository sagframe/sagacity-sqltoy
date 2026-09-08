package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.SqlConfigParseUtils;
import org.sagacity.sqltoy.config.model.OperateType;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlToyResult;
import org.sagacity.sqltoy.config.model.SqlType;
import org.sagacity.sqltoy.config.model.SqlWithAnalysis;
import org.sagacity.sqltoy.dialect.utils.DialectUtils;
import org.sagacity.sqltoy.dialect.utils.OracleDialectUtils;
import org.sagacity.sqltoy.model.LockMode;
import org.sagacity.sqltoy.plugins.interceptors.TenantFilterInterceptor;

/**
 * 字面量盲区修复的引用链路级验证(非单独函数测试):
 * 1.with链路:parseSqlToyConfig标记 -> getCountBySql的with剥离 -> count改写 -> H2执行;
 * 2.with+union链路:hasUnion的with剥离 -> union-all判定;
 * 3.租户拦截器链路:context.interceptors -> DialectUtils.doInterceptors真实迭代调用;
 * 4.oracle锁链路:hasLock守卫 -> getLockSql注入 -> 再次hasLock幂等;
 * 5.oracle分页链路:hasOrderBy决定外层包裹的决策输入;
 * 6.参数链路:parseSqlToyConfig.paramsName与named转写的参数名对齐,字面量幻影参数不影响绑定
 */
public class SqlLiteralChainImpactTest {

	private static Connection conn;

	private static SqlToyContext context = new SqlToyContext();

	@BeforeAll
	public static void init() throws Exception {
		conn = DriverManager.getConnection("jdbc:h2:mem:chainimpact;DB_CLOSE_DELAY=-1", "sa", "");
		try (Statement st = conn.createStatement()) {
			st.execute("drop table if exists t1");
			st.execute("drop table if exists t2");
			st.execute("create table t1 (id int, remark varchar(200), a int)");
			st.execute("insert into t1 values (1,'x',10)");
			st.execute("insert into t1 values (2,' with x as (select 1) ',20)");
			st.execute("insert into t1 values (3,'waiting for update',30)");
			st.execute("create table t2 (id int, b int)");
			st.execute("insert into t2 values (11,110)");
			st.execute("insert into t2 values (12,120)");
		}
	}

	// ---------------- 1.with -> count链路(H2端到端) ----------------

	@Test
	public void withConfigToCountChain() throws Exception {
		String sql = "with w as (select a from t1) select a from w";
		SqlToyConfig config = SqlConfigParseUtils.parseSqlToyConfig(sql, "h2", SqlType.search);
		assertTrue(config.isHasWith(), "真实with应标记");
		Long count = DialectUtils.getCountBySql(context, config, sql, new Object[] {}, false, null, conn,
				DataSourceUtils.DBType.H2);
		assertEquals(3L, count.longValue(), "with剥离+count改写链路应返回全表行数");
	}

	@Test
	public void withLiteralBodyToCountChain() throws Exception {
		// CTE体内查询的字面量含with文本:count链路字面量作为过滤条件生效(仅id=2行命中)
		String sql = "with w as (select a, remark from t1) select a from w where remark=' with x as (select 1) '";
		SqlToyConfig config = SqlConfigParseUtils.parseSqlToyConfig(sql, "h2", SqlType.search);
		assertTrue(config.isHasWith(), "真实with应标记");
		Long count = DialectUtils.getCountBySql(context, config, sql, new Object[] {}, false, null, conn,
				DataSourceUtils.DBType.H2);
		assertEquals(1L, count.longValue(), "字面量过滤条件必须在count链路中生效(修复前where被截断会得到0)");
	}

	// ---------------- 2.with + union链路 ----------------

	@Test
	public void withUnionChain() {
		String sql = "with w as (select a from t1) select a from w union all select a from t2";
		assertTrue(SqlUtil.hasUnion(sql, false), "with剥离后的union应被识别");
		SqlWithAnalysis analysis = new SqlWithAnalysis(sql);
		assertFalse(analysis.getRejectWithSql().toLowerCase().startsWith(" with"),
				"剥离with后不应以with开头");
		assertTrue(analysis.getRejectWithSql().contains(" union all "), "union片段必须保留");
		// 字面量内的union all不触发(与with剥离守卫协同)
		assertFalse(SqlUtil.hasUnion(
				"with w as (select a from t1) select a from w where remark=' union all '", false),
				"字面量union不应判定存在union");
	}

	// ---------------- 3.租户拦截器 -> doInterceptors链路 ----------------

	@Test
	public void tenantInterceptorChainViaDoInterceptors() {
		SqlToyContext ctx = new SqlToyContext();
		ctx.setUnifyFieldsHandler(new org.sagacity.sqltoy.plugins.IUnifyFieldsHandler() {
			@Override
			public String getUserTenantId() {
				return "T1";
			}
		});
		ctx.setSqlInterceptors(Arrays.asList(new TenantFilterInterceptor()));
		// 字面量内含"where tenant_id=88":修复前被误判已过滤而漏加租户条件
		String sql = "select * from t_tenant_probe where remark='select * from orders where tenant_id=88' and id=1";
		SqlToyResult result = DialectUtils.doInterceptors(ctx, new SqlToyConfig(null), OperateType.singleTable,
				new SqlToyResult(sql, new Object[] {}), SqlLiteralBlindSpotFixTest.TenantProbeVO.class,
				DataSourceUtils.DBType.H2);
		assertTrue(result.getSql().contains("tenant_id='T1'"), "拦截器链路必须注入租户条件: " + result.getSql());
		// 已有真实租户条件:链路不重复注入
		SqlToyResult untouched = DialectUtils.doInterceptors(ctx, new SqlToyConfig(null), OperateType.singleTable,
				new SqlToyResult("select * from t_tenant_probe where tenant_id='T9' and id=1", new Object[] {}),
				SqlLiteralBlindSpotFixTest.TenantProbeVO.class, DataSourceUtils.DBType.H2);
		assertEquals("select * from t_tenant_probe where tenant_id='T9' and id=1", untouched.getSql(),
				"已有真实租户条件时不应重复注入");
	}

	// ---------------- 4.oracle锁链路:守卫->注入->幂等 ----------------

	@Test
	public void oracleLockChainIdempotent() {
		String loadSql = "select * from t1 where remark='waiting for update' and id=1";
		// 修复前:字面量'waiting for update'令hasLock=true,守卫直接放行,行锁被静默跳过
		assertFalse(SqlUtil.hasLock(loadSql, DataSourceUtils.DBType.ORACLE), "字面量不应判定已存在锁");
		String lockTail = OracleDialectUtils.getLockSql(loadSql, DataSourceUtils.DBType.ORACLE, LockMode.UPGRADE, -1);
		assertEquals(" for update ", lockTail, "守卫放行后必须产出锁后缀");
		String locked = loadSql.concat(lockTail);
		// 注入后的sql必须被守卫识别(重复load不会再追加,保证幂等)
		assertTrue(SqlUtil.hasLock(locked, DataSourceUtils.DBType.ORACLE), "注入后的真实锁必须被识别");
		assertEquals("", OracleDialectUtils.getLockSql(locked, DataSourceUtils.DBType.ORACLE, LockMode.UPGRADE, -1),
			 "已加锁的sql再次进入必须返回空串(幂等)");
	}

	// ---------------- 5.oracle分页决策链路:hasOrderBy -> 外层包裹 ----------------

	@Test
	public void hasOrderByOraclePageDecision() {
		// 真实order by -> 外层包裹
		assertTrue(SqlUtil.hasOrderBy("select a from t1 order by a", true), "真实order by应包裹");
		// 字面量内的order by -> 不包裹(修复前误判包裹导致排序错乱)
		assertFalse(SqlUtil.hasOrderBy("select a from t1 where remark=' order by a'", true),
				"字面量order by不应触发包裹");
		// 子查询内真实order by + 字面量order by共存:仍不应判定为最外层
		assertFalse(SqlUtil.hasOrderBy("select * from (select a from t1 order by a) x where remark=' order by b'",
				true), "子查询内排序+字面量排序不应判定为最外层排序");
	}

	// ---------------- 6.参数链路:config.paramsName与named转写对齐 ----------------

	@Test
	public void paramNameAlignmentChain() {
		String sql = "select * from t1 where remark='（注意）:beizhu' and status=:status and code=:code";
		SqlToyConfig config = SqlConfigParseUtils.parseSqlToyConfig(sql, "h2", SqlType.search);
		org.sagacity.sqltoy.config.model.SqlParamsModel model = SqlConfigParseUtils
				.processNamedParamsQuery(config.getSql("h2"), false);
		org.junit.jupiter.api.Assertions.assertArrayEquals(config.getParamsName(), model.getParamsName(),
				"配置参数名与named转写参数名必须对齐(幻影参数会错位)");
		// map取值链路:幻影参数不得占用绑定位置
		Map<String, Object> values = new HashMap<>();
		values.put("status", 1);
		values.put("code", "c");
		SqlToyResult r = SqlConfigParseUtils.processSql(sql, values, "h2");
		org.junit.jupiter.api.Assertions.assertArrayEquals(new Object[] { 1, "c" }, r.getParamsValue(),
				"绑定值必须与真实参数对齐");
		assertTrue(r.getSql().contains("'（注意）:beizhu'"), "字面量必须原样保留: " + r.getSql());
		assertTrue(r.getSql().contains("status=?") && r.getSql().contains("code=?"), "实际:" + r.getSql());
	}
}
