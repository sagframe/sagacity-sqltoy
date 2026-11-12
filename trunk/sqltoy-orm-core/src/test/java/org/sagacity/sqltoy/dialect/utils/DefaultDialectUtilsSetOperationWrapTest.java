package org.sagacity.sqltoy.dialect.utils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.SqlConfigParseUtils;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.dialect.Dialect;
import org.sagacity.sqltoy.dialect.impl.ClickHouseDialect;
import org.sagacity.sqltoy.dialect.impl.DB2Dialect;
import org.sagacity.sqltoy.dialect.impl.ImpalaDialect;
import org.sagacity.sqltoy.model.DBProfile;
import org.sagacity.sqltoy.model.QueryExecutor;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * update 2026-11-10 集合语句分页/top 的派生表包裹门禁(不需要任何数据库)
 *
 * <p>
 * 背景:ClickHouse 的 limit/offset 在集合运算尾部绑定右侧 SELECT(A except B limit N 的 N 修饰 B),
 * 真库实测 top(2) 返回全量、Page(2,2) 的 offset 整体失效;Impala 官方 UNION 文档明确"要让 ORDER BY/LIMIT
 * 作用于整个结果集,须把 UNION 整条包成子查询再在外层限制",属同族行为。其余库(DB2/Hana/PG/MySQL/SQLite)
 * 为标准语义,不得多包派生表(会改变执行计划)。
 *
 * <p>
 * Impala 无法在本机起容器(需 impalad/catalogd/statestored+HMS+HDFS,且 JDBC 在 Cloudera 仓库),
 * 故用"捕获 Connection 上的最终 SQL"锁定门禁形态:不依赖数据库,也能防止将来误删/误扩门禁。
 */
public class DefaultDialectUtilsSetOperationWrapTest {

	private static final String SET_SQL = "select 1 x from t_a except select 2 x from t_b";
	private static final String UNION_SQL = "select 1 x from t_a union all select 2 x from t_b";
	private static final String PLAIN_SQL = "select 1 x from t_a";

	/** 通过动态代理Connection捕获DefaultDialectUtils生成的最终SQL(捕获后即抛异常中断执行) */
	private static String captureSql(Dialect dialect, int dbType, String cfgDialect, String sql, boolean top)
			throws Exception {
		final List<String> captured = new ArrayList<String>();
		Connection conn = (Connection) Proxy.newProxyInstance(DefaultDialectUtilsSetOperationWrapTest.class.getClassLoader(),
				new Class[] { Connection.class }, (proxy, method, args) -> {
					String name = method.getName();
					if (("prepareStatement".equals(name) || "createStatement".equals(name)) && args != null
							&& args.length > 0 && args[0] instanceof String) {
						captured.add((String) args[0]);
						throw new SQLException("captured");
					}
					Class<?> returnType = method.getReturnType();
					if (returnType == boolean.class) {
						return Boolean.FALSE;
					}
					if (returnType == int.class) {
						return Integer.valueOf(0);
					}
					if (returnType == long.class) {
						return Long.valueOf(0L);
					}
					return null;
				});
		SqlToyContext context = new SqlToyContext();
		SqlToyConfig config = new SqlToyConfig(cfgDialect);
		config.setSql(sql);
		// 与生产口径一致:SqlConfigParseUtils解析期按sql内容设置hasWith标志
		config.setHasWith(SqlConfigParseUtils.hasWith(sql));
		DBProfile profile = new DBProfile(null, cfgDialect, dbType, null, 0, null, null, false);
		try {
			if (top) {
				dialect.findTopBySql(context, config, new QueryExecutor(sql), null, 2, conn, profile, 500, -1);
			} else {
				dialect.findPageBySql(context, config, new QueryExecutor(sql), null, 2L, 2, conn, profile, 500, -1);
			}
		} catch (Throwable ignore) {
			// 预期:捕获到SQL后代理抛异常中断
		}
		return captured.isEmpty() ? "<no-sql-captured>" : captured.get(0);
	}

	@Test
	public void clickHouseAndImpalaWrapSetOperation() throws Exception {
		for (Dialect dialect : new Dialect[] { new ClickHouseDialect(), new ImpalaDialect() }) {
			int dbType = (dialect instanceof ClickHouseDialect) ? DBType.CLICKHOUSE : DBType.IMPALA;
			String cfgDialect = (dialect instanceof ClickHouseDialect) ? "clickhouse" : "impala";
			String topSql = captureSql(dialect, dbType, cfgDialect, SET_SQL, true);
			assertTrue(topSql.contains("select * from (") && topSql.contains("SAG_INTERMEDIATE_TABLE"),
					cfgDialect + " 集合语句top应包派生表后挂limit, 实际: " + topSql);
			assertTrue(topSql.trim().endsWith("limit 2"), cfgDialect + " limit应作用于包裹后的整体, 实际: " + topSql);
			// union all 同属集合语句
			String unionTop = captureSql(dialect, dbType, cfgDialect, UNION_SQL, true);
			assertTrue(unionTop.contains("SAG_INTERMEDIATE_TABLE"), cfgDialect + " union all 也应包裹, 实际: " + unionTop);
			// 分页同口径
			String pageSql = captureSql(dialect, dbType, cfgDialect, SET_SQL, false);
			assertTrue(pageSql.contains("SAG_INTERMEDIATE_TABLE") && pageSql.contains("offset"),
					cfgDialect + " 集合语句分页应包派生表后挂limit/offset, 实际: " + pageSql);
		}
	}

	@Test
	public void standardDialectsKeepOriginalForm() throws Exception {
		// DB2/Hana等标准语义库:集合语句也不得额外包裹(避免改变执行计划)
		String db2Sql = captureSql(new DB2Dialect(), DBType.DB2, "db2", SET_SQL, true);
		assertFalse(db2Sql.contains("SAG_INTERMEDIATE_TABLE"), "DB2不应包裹, 实际: " + db2Sql);
		assertTrue(db2Sql.contains("except"), "DB2原SQL形态应保留, 实际: " + db2Sql);
	}

	@Test
	public void nonSetQueryNeverWrapped() throws Exception {
		// 非集合语句即便在CH也保持原形态
		String plain = captureSql(new ClickHouseDialect(), DBType.CLICKHOUSE, "clickhouse", PLAIN_SQL, true);
		assertFalse(plain.contains("SAG_INTERMEDIATE_TABLE"), "非集合语句不应包裹, 实际: " + plain);
		assertTrue(plain.trim().endsWith("limit 2"), "非集合语句应直接追加limit, 实际: " + plain);
	}

	@Test
	public void withSetOperationHoistsCteBeforeWrap() throws Exception {
		// update 2026-11-12 Impala的WITH不支持子查询块内CTE:包裹前必须把WITH剥离置前,
		// 否则产物select * from (with ...)派生表在Impala直接语法错误;CH两种形态均合法,统一上提
		String withSql = "with s as (select 1 as a from t_a) select * from s except select 2 as a from t_b";
		for (Dialect dialect : new Dialect[] { new ClickHouseDialect(), new ImpalaDialect() }) {
			int dbType = (dialect instanceof ClickHouseDialect) ? DBType.CLICKHOUSE : DBType.IMPALA;
			String cfgDialect = (dialect instanceof ClickHouseDialect) ? "clickhouse" : "impala";
			// top形态: with必须出现在"select * from ("之前
			String topSql = captureSql(dialect, dbType, cfgDialect, withSql, true);
			assertTrue(topSql.contains("SAG_INTERMEDIATE_TABLE"), cfgDialect + " 集合语句应包派生表, 实际: " + topSql);
			assertTrue(topSql.indexOf("with s as") >= 0
					&& topSql.indexOf("with s as") < topSql.indexOf("select * from ("),
					cfgDialect + " WITH应上提到派生表之前, 实际: " + topSql);
			assertFalse(topSql.contains("(with s as"), cfgDialect + " WITH不得留在派生表内, 实际: " + topSql);
			// 分页同口径
			String pageSql = captureSql(dialect, dbType, cfgDialect, withSql, false);
			assertTrue(pageSql.indexOf("with s as") < pageSql.indexOf("select * from ("),
					cfgDialect + " 分页WITH应上提, 实际: " + pageSql);
			assertTrue(pageSql.contains("offset"), cfgDialect + " 分页应含offset, 实际: " + pageSql);
		}
	}

	@Test
	public void withQueryWithoutSetOperationKeepsOriginalForm() throws Exception {
		// 非集合语句的WITH查询不包裹也不剥离(原样limit追加,与历史行为一致)
		String withPlain = "with s as (select 1 as a from t_a) select * from s";
		String topSql = captureSql(new ClickHouseDialect(), DBType.CLICKHOUSE, "clickhouse", withPlain, true);
		assertFalse(topSql.contains("SAG_INTERMEDIATE_TABLE"), "非集合WITH语句不应包裹, 实际: " + topSql);
		assertTrue(topSql.trim().endsWith("limit 2"), "非集合WITH语句应直接追加limit, 实际: " + topSql);
	}
}
