package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.SqlConfigParseUtils;
import org.sagacity.sqltoy.config.annotation.Column;
import org.sagacity.sqltoy.config.annotation.Entity;
import org.sagacity.sqltoy.config.annotation.Id;
import org.sagacity.sqltoy.config.annotation.Tenant;
import org.sagacity.sqltoy.config.model.OperateType;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlToyResult;
import org.sagacity.sqltoy.config.model.SqlType;
import org.sagacity.sqltoy.config.model.SqlWithAnalysis;
import org.sagacity.sqltoy.plugins.IUnifyFieldsHandler;
import org.sagacity.sqltoy.plugins.interceptors.TenantFilterInterceptor;

/**
 * 字面量盲区修复专项验证(全面审计发现的5处遗漏):
 * 1.with链路:hasWith/SqlWithAnalysis在字面量掩码串上定位,字面量内的with xx as (不再误触发解析;
 * 2.租户拦截器:where定位、已有租户条件检查、group by/order by定位均在掩码串上进行,
 *   规避字面量内的where tenant_id=文本导致漏加租户条件(fail-open);
 * 3.hasLock:字面量内的for update/锁提示文本不再误判为已存在锁;
 * 4.hasOrderBy:字面量内的)/order by不再误判为最外层排序;
 * 5.getSqlParamsName:字面量内的'标点:名称'文本不再产生幻影参数名
 */
public class SqlLiteralBlindSpotFixTest {

	// ---------------- 1.with链路 ----------------

	@Test
	public void withInLiteralNotParsed() {
		String sql = "select a from t1 where remark=' with x as (select 1) '";
		assertFalse(SqlConfigParseUtils.hasWith(sql), "字面量内的with不应判定存在with");
		SqlWithAnalysis analysis = new SqlWithAnalysis(sql);
		assertFalse(analysis.isHasWith(), "SqlWithAnalysis不应误解析字面量内的with");
		assertEquals(sql, analysis.getRejectWithSql(), "rejectWithSql必须与原串一致");
		assertEquals("", analysis.getWithSql(), "不应提取出with片段");
	}

	@Test
	public void realWithStillParsedAndLiteralIntact() {
		// 真实with + 字面量内含with文本:with正常提取,字面量原样保留
		String sql = "with t as (select a from t1) select * from t where remark=' with x as (select 1) '";
		SqlWithAnalysis analysis = new SqlWithAnalysis(sql);
		assertTrue(analysis.isHasWith(), "真实with应正常识别");
		assertEquals("with t as (select a from t1)", analysis.getWithSql().trim(), "with片段应完整提取");
		assertEquals("select * from t where remark=' with x as (select 1) '", analysis.getRejectWithSql().trim(),
				"剔除with后的语句必须保留字面量原文");
	}

	@Test
	public void multiAsWithLiteralInBody() {
		String sql = "with t as (select a from t1) , t2 as (select b from t2) "
				+ "select * from t2 where remark=' , t3 as (select 1) '";
		SqlWithAnalysis analysis = new SqlWithAnalysis(sql);
		assertTrue(analysis.isHasWith(), "多个as的with应正常识别");
		assertEquals(2, analysis.getWithSqlSet().size(), "应提取出2个with片段");
		assertTrue(analysis.getRejectWithSql().contains("remark=' , t3 as (select 1) '"),
				"字面量必须原样保留: " + analysis.getRejectWithSql());
	}

	@Test
	public void parseConfigHasWithFlagAccurate() throws Exception {
		SqlToyConfig config = SqlConfigParseUtils.parseSqlToyConfig(
				"select a from t1 where remark=' with x as (select 1) '", "mysql", SqlType.search);
		assertFalse(config.isHasWith(), "配置层的isHasWith不应被字面量误标");
		config = SqlConfigParseUtils.parseSqlToyConfig("with t as (select a from t1) select a from t", "mysql",
				SqlType.search);
		assertTrue(config.isHasWith(), "真实with应正常标记");
	}

	// ---------------- 2.租户拦截器 ----------------

	@Entity(tableName = "t_tenant_probe")
	public static class TenantProbeVO {
		@Id
		@Column(name = "id")
		private Long id;

		@Tenant
		@Column(name = "tenant_id")
		private String tenantId;

		@Column(name = "remark")
		private String remark;

		public Long getId() {
			return id;
		}

		public void setId(Long id) {
			this.id = id;
		}

		public String getTenantId() {
			return tenantId;
		}

		public void setTenantId(String tenantId) {
			this.tenantId = tenantId;
		}

		public String getRemark() {
			return remark;
		}

		public void setRemark(String remark) {
			this.remark = remark;
		}
	}

	private SqlToyResult decorate(String sql) {
		SqlToyContext context = new SqlToyContext();
		context.setUnifyFieldsHandler(new IUnifyFieldsHandler() {
			@Override
			public String getUserTenantId() {
				return "T1";
			}
		});
		TenantFilterInterceptor interceptor = new TenantFilterInterceptor();
		return interceptor.decorate(context, new SqlToyConfig(null), OperateType.singleTable,
				new SqlToyResult(sql, new Object[] {}), TenantProbeVO.class, DataSourceUtils.DBType.H2);
	}

	@Test
	public void tenantFilterNotSkippedByLiteralTenantText() {
		// 字面量内含"where tenant_id=88"文本:不得误判已过滤而漏加租户条件(修复前fail-open)
		String sql = "select * from t_tenant_probe where remark='select * from orders where tenant_id=88' and id=1";
		SqlToyResult result = decorate(sql);
		String decorated = result.getSql();
		assertTrue(decorated.contains("tenant_id='T1'"), "必须注入租户条件: " + decorated);
		assertTrue(decorated.contains("where tenant_id="), "租户条件必须注入真实where之后: " + decorated);
		assertTrue(decorated.contains("'select * from orders where tenant_id=88'"), "字面量必须原样保留: " + decorated);
	}

	@Test
	public void tenantFilterSkipStillWorksForRealCondition() {
		String sql = "select * from t_tenant_probe where tenant_id='T9' and id=1";
		assertEquals(sql, decorate(sql).getSql(), "已存在真实租户条件时不应重复注入");
	}

	@Test
	public void tenantFilterLiteralWhereNotCorrupted() {
		// 字面量内的where不得被误替换为租户条件(修复前replaceFirst命中字面量)
		String sql = "select ' where x ' from t_tenant_probe where id=1";
		SqlToyResult result = decorate(sql);
		String decorated = result.getSql();
		assertTrue(decorated.contains("' where x '"), "字面量必须原样保留: " + decorated);
		assertTrue(decorated.contains("tenant_id='T1'"), "租户条件必须注入真实where处: " + decorated);
	}

	@Test
	public void tenantFilterNoWhereWithLiteralOrderBy() {
		// 无真实where且字面量内含order by:租户条件应追加到末尾而非注入字面量内部
		String sql = "select ' order by id ' from t_tenant_probe";
		SqlToyResult result = decorate(sql);
		String decorated = result.getSql();
		assertTrue(decorated.contains("' order by id '"), "字面量必须原样保留: " + decorated);
		assertTrue(decorated.trim().endsWith("tenant_id='T1'"), "租户条件应追加在末尾: " + decorated);
	}

	// ---------------- 3.hasLock ----------------

	@Test
	public void hasLockLiteralSafety() {
		assertFalse(SqlUtil.hasLock("select * from t1 where remark='waiting for update'",
				DataSourceUtils.DBType.ORACLE), "字面量内的for update不应判定已存在锁");
		assertTrue(SqlUtil.hasLock("select * from t1 where id=1 for update", DataSourceUtils.DBType.ORACLE),
				"真实for update应正确判定");
		assertTrue(SqlUtil.hasLock("select * from t1 with (rowlock xlock) where id=1",
				DataSourceUtils.DBType.SQLSERVER), "sqlserver锁提示应正确判定");
		assertFalse(SqlUtil.hasLock("select * from t1 where remark=' with (rowlock xlock) '",
				DataSourceUtils.DBType.SQLSERVER), "字面量内的锁提示不应判定已存在锁");
	}

	// ---------------- 4.hasOrderBy ----------------

	@Test
	public void hasOrderByLiteralSafety() {
		assertFalse(SqlUtil.hasOrderBy("select a from t1 where remark=' order by id'", true),
				"字面量内的order by不应判定为最外层排序");
		assertFalse(SqlUtil.hasOrderBy("select a from (select b from t2) x where remark=' order by 1'", true),
				"字面量内的)与order by不应干扰判定");
		assertTrue(SqlUtil.hasOrderBy("select a from t1 where remark='x)' order by a", true),
				"真实order by应正确判定");
		assertTrue(SqlUtil.hasOrderBy("select a from t1 order by a", true), "简单order by应正确判定");
		assertFalse(SqlUtil.hasOrderBy("select a from t1", true), "无order by应正确判定");
	}

	// ---------------- 5.getSqlParamsName ----------------

	@Test
	public void getSqlParamsNameLiteralSafety() {
		String[] names = SqlConfigParseUtils.getSqlParamsName(
				"select * from t1 where remark='（注意）:beizhu' and status=:status", true);
		assertEquals(1, names.length, "字面量内的:名称不得产生幻影参数");
		assertEquals("status", names[0], "真实参数应正常提取");
		// 常规场景不回归
		names = SqlConfigParseUtils.getSqlParamsName("select * from t1 where a=:a and b=:b", true);
		assertEquals(2, names.length, "真实参数应全部提取");
		// ::jsonb强转场景不回归(冒号前为字母不识别为参数)
		names = SqlConfigParseUtils.getSqlParamsName("select * from t1 where x::jsonb=:y", true);
		assertEquals(1, names.length, "::jsonb不应被识别为参数");
		assertEquals("y", names[0]);
	}

	// ---------------- 6.引号感知括号配对工具及同族接入点 ----------------

	@Test
	public void getSymMarkIndexSkipQuotedBasics() {
		// 字面量内的(和)均不参与配对:f的收括号在字面量之后
		assertEquals(7, StringUtil.getSymMarkIndexSkipQuoted("(", ")", "f(a,'(') , g(')')", 0),
				"字面量内括号不参与配对");
		// 从g开始配对:字面量内的)不参与
		assertEquals(16, StringUtil.getSymMarkIndexSkipQuoted("(", ")", "f(a,'(') , g(')')", 12),
				"字面量内括号不参与配对(g)");
		// 嵌套+字面量共存
		assertEquals(18, StringUtil.getSymMarkIndexSkipQuoted("(", ")", "f(a, (b, 'x)') , c)", 0),
				"嵌套配对应到最外层收括号");
		// 字面量含成对转义引号(字面量为体:收括号紧跟字面量之后的第一个真实)
		String doubled = "f('it''s (') , b)";
		assertEquals(doubled.indexOf(")"), StringUtil.getSymMarkIndexSkipQuoted("(", ")", doubled, 0),
				"''转义字面量应整体跳过(收括号为字面量后的真实)");
		// 未配对/无开始标记
		assertEquals(-1, StringUtil.getSymMarkIndexSkipQuoted("(", ")", "f(a,'('", 0), "未配对返回-1");
		assertEquals(-1, StringUtil.getSymMarkIndexSkipQuoted("(", ")", "abc", 0), "无开始标记返回-1");
	}

	@Test
	public void fastMacroBodyWithLiteralParens() throws Exception {
		// @fast体内的字面量含')'与'(':宏体不得被字面量截断
		String sql = "select * from (@fast(select a from t1 where remark=')' and code='(' and id=:id)) sag_tmp";
		SqlToyConfig config = SqlConfigParseUtils.parseSqlToyConfig(sql, "h2", SqlType.search);
		assertTrue(config.isHasFast(), "fast宏应正常识别");
		assertTrue(config.isIgnoreBracket(), "预括号形态应识别ignoreBracket");
		String realSql = config.getSql("h2");
		assertTrue(realSql.contains("remark=')'"), "字面量')'必须原样保留: " + realSql);
		assertTrue(realSql.contains("code='('"), "字面量'('必须原样保留: " + realSql);
		assertTrue(realSql.trim().endsWith("sag_tmp"), "宏体外围结构不得残缺: " + realSql);
	}

	@Test
	public void dialectFunctionArgsWithLiteralParens() {
		// 注册nvl转换函数(生产环境由SqlToyContext初始化装载,参数为类名列表)
		org.sagacity.sqltoy.plugins.function.FunctionUtils.setFunctionConverts(java.util.Arrays
				.asList("org.sagacity.sqltoy.plugins.function.impl.Nvl"));
		try {
			// nvl函数参数含字面量'(':函数体配对不受字面量内括号干扰,转换后字面量原样保留
			String converted = org.sagacity.sqltoy.plugins.function.FunctionUtils
					.getDialectSql("select nvl(remark,'(') from t1", "mysql");
			assertTrue(converted.contains("ifnull(remark,'(')"),
					"nvl应转换为ifnull且字面量原样保留: " + converted);
		} finally {
			org.sagacity.sqltoy.plugins.function.FunctionUtils.setFunctionConverts(new java.util.ArrayList<>());
		}
	}
}
