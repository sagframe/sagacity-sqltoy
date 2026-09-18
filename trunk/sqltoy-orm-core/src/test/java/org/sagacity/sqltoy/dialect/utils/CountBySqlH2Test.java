package org.sagacity.sqltoy.dialect.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.utils.DataSourceUtils;

/**
 * getCountBySql 字面量感知修复的H2端到端验证。
 * count语句生成自运行sql(含字面量),任何字面量盲区导致的截断/错位都会以
 * SQLException(语法破坏)或错误count值(参数错位)两种形式暴露。
 * 常规场景(无字面量干扰)为等价性基线,修复前后必须一致。
 */
public class CountBySqlH2Test {

	private static Connection conn;

	private static SqlToyContext context;

	@BeforeAll
	public static void init() throws Exception {
		conn = DriverManager.getConnection("jdbc:h2:mem:countbytest;DB_CLOSE_DELAY=-1", "sa", "");
		try (Statement st = conn.createStatement()) {
			st.execute("drop table if exists t");
			st.execute("create table t (id int, remark varchar(100), name varchar(100))");
			st.execute("insert into t values (1,'order by 1','张三')");
			st.execute("insert into t values (2,'普通','李四')");
			st.execute("insert into t values (3,'a?b','王五')");
		}
		context = new SqlToyContext();
	}

	private long count(String sql, Object[] params) throws Exception {
		return count(sql, params, false);
	}

	private long count(String sql, Object[] params, boolean hasWith) throws Exception {
		SqlToyConfig config = new SqlToyConfig(null);
		config.setHasWith(hasWith);
		return DialectUtils.getCountBySql(context, config, sql, params, false, null, conn,
				DataSourceUtils.DBType.H2);
	}

	// ---------------- 等价性基线(无字面量干扰,修复前后一致) ----------------

	@Test
	public void simpleCount() throws Exception {
		assertEquals(1L, count("select * from t where id=?", new Object[] { 1 }));
	}

	@Test
	public void orderStripReal() throws Exception {
		assertEquals(1L, count("select * from t where id=? order by id", new Object[] { 1 }));
	}

	@Test
	public void distinctWrapped() throws Exception {
		assertEquals(3L, count("select distinct remark from t where id<=?", new Object[] { 3 }));
	}

	@Test
	public void unionWrapped() throws Exception {
		assertEquals(2L, count("select id from t where id=? union select id from t where id=?",
				new Object[] { 1, 2 }));
	}

	@Test
	public void groupByInnerWrapped() throws Exception {
		assertEquals(3L, count(
				"select * from (select remark, count(1) as cnt from t group by remark) x", new Object[] {}));
	}

	@Test
	public void cteControl() throws Exception {
		assertEquals(1L, count("with w as (select id from t where id=?) select * from w",
				new Object[] { 2 }, true));
	}

	// ---------------- CTE字面量/嵌套(方案B修复目标) ----------------

	@Test
	public void cteLiteralParen() throws Exception {
		// 修复前:字面量内的)被误当with体终结,提取截断,count必然失败
		assertEquals(1L, count("with w as (select ')' as mark from t where id=?) select * from w",
				new Object[] { 2 }, true));
	}

	@Test
	public void cteNestedParenAndLiteral() throws Exception {
		// 修复前:嵌套括号+字面量内)导致配对错乱
		assertEquals(1L, count("with w as (select concat('(', id) as v from t where id=?) select * from w",
				new Object[] { 3 }, true));
	}

	@Test
	public void cteMultipleAsWithLiteral() throws Exception {
		assertEquals(1L, count(
				"with w1 as (select id from t where remark='a)b'), "
						+ "w2 as (select id from t where id=?) select * from w2",
				new Object[] { 2 }, true));
	}

	// ---------------- 多SQL写法矩阵(各种语句结构) ----------------

	@Test
	public void styleLikeWithEscape() throws Exception {
		assertEquals(1L, count("select * from t where name like ?", new Object[] { "%张%" }));
	}

	@Test
	public void styleBetween() throws Exception {
		assertEquals(3L, count("select * from t where id between ? and ?", new Object[] { 1, 3 }));
	}

	@Test
	public void styleInList() throws Exception {
		assertEquals(2L, count("select * from t where id in (?,?)", new Object[] { 1, 2 }));
	}

	@Test
	public void styleNotInAndIsNotNull() throws Exception {
		assertEquals(2L, count("select * from t where id not in (?) and name is not null",
				new Object[] { 1 }));
	}

	@Test
	public void styleExistsSubquery() throws Exception {
		assertEquals(1L, count(
				"select * from t a where exists (select 1 from t b where b.id=a.id and b.id=?)",
				new Object[] { 3 }));
	}

	@Test
	public void styleSubqueryInFrom() throws Exception {
		assertEquals(1L, count("select * from (select id from t where id>?) z where id<?",
				new Object[] { 1, 3 }));
	}

	@Test
	public void styleSubqueryWithParamsBothLevels() throws Exception {
		assertEquals(1L, count("select * from (select id from t where id=?) z where z.id=?",
				new Object[] { 2, 2 }));
	}

	@Test
	public void styleCaseWhen() throws Exception {
		assertEquals(1L, count(
				"select case when id=? then 'x' else 'y' end as v from t where id=?",
				new Object[] { 1, 2 }));
	}

	@Test
	public void styleInnerJoin() throws Exception {
		assertEquals(1L, count("select * from t a join t b on a.id=b.id and a.id=?",
				new Object[] { 2 }));
	}

	@Test
	public void styleLeftJoin() throws Exception {
		assertEquals(1L, count(
				"select a.id from t a left join t b on a.id=b.id and b.id=? where a.remark like ?",
				new Object[] { 2, "%普通%" }));
	}

	@Test
	public void styleGroupByHavingParam() throws Exception {
		assertEquals(3L, count(
				"select remark from t group by remark having count(1) > ?", new Object[] { 0 }));
	}

	@Test
	public void styleUnionAll() throws Exception {
		assertEquals(2L, count("select id from t where id=? union all select id from t where id=?",
				new Object[] { 1, 1 }));
	}

	@Test
	public void styleNestedParensAroundParam() throws Exception {
		assertEquals(1L, count("select * from t where id=((?))", new Object[] { 2 }));
	}

	@Test
	public void styleConcatParams() throws Exception {
		assertEquals(1L, count("select * from t where name=concat(?,?)",
				new Object[] { "王", "五" }));
	}

	@Test
	public void styleCast() throws Exception {
		assertEquals(1L, count("select * from t where id=cast(? as int)", new Object[] { 2 }));
	}

	@Test
	public void styleSelectFieldParamCoalesce() throws Exception {
		// select字段中的参数在count优化时被正确剔除,仅保留where参数
		assertEquals(1L, count("select coalesce(?, remark) as v from t where id=?",
				new Object[] { "x", 3 }));
	}

	@Test
	public void styleLiteralQuestionMarkWithRealParams() throws Exception {
		assertEquals(1L, count(
				"select * from t where remark='a?b' and id=? and name like ?",
				new Object[] { 3, "%王%" }));
	}

	@Test
	public void styleBacktickIdentifier() throws Exception {
		assertEquals(1L, count("select `remark` from t where `id` = ?", new Object[] { 2 }));
	}

	@Test
	public void styleInWithSubquery() throws Exception {
		assertEquals(1L, count("select * from t where id in (select id from t where id=?)",
				new Object[] { 2 }));
	}

	// ---------------- 多行SQL与字面量内回车换行 ----------------

	@Test
	public void multilineSqlWithLiteralQuestionMark() throws Exception {
		// SQL整体跨行且字面量内含\r\n?:占位符保护不干扰count,恢复后字面量保真;
		// 种子数据remark为'a?b'(无换行),字面量按原文参与比较自然不匹配,验证无假命中
		assertEquals(0L, count(
				"select *\nfrom t\nwhere remark='a\r\n?b'\nand id=?", new Object[] { 2 }));
	}

	@Test
	public void multilineCteWithLiteralParenAndNewlines() throws Exception {
		// 多行CTE体内字面量含)与换行:findBodyEnd字面量感知配对正确
		assertEquals(1L, count(
				"with w as (\n    select ')' as mark, id\n    from t where id=?\n)\nselect * from w",
				new Object[] { 2 }, true));
	}

	@Test
	public void multilineOrderByOnSeparateLine() throws Exception {
		// order by独立成行(前后为换行符)仍被正确剔除;%a%仅命中'a?b'一行
		assertEquals(1L, count(
				"select * from t\nwhere remark like ?\norder by\nid", new Object[] { "%a%" }));
	}

	// ---------------- 字面量盲区(修复目标) ----------------

	@Test
	public void literalOrderByStrippedCorrectly() throws Exception {
		// 修复前:剔除点落入字面量内,count sql字面量未闭合→SQLException
		assertEquals(1L, count("select * from t where remark='order by 1'", new Object[] {}));
	}

	@Test
	public void literalOrderByPlusRealOrderBy() throws Exception {
		// 修复前:真实order by与字面量内order by并存时剔除错位;字面量只匹配id=1一行
		assertEquals(1L, count("select * from t where remark='order by 1' order by id", new Object[] {}));
	}

	@Test
	public void literalFromInSelectFields() throws Exception {
		// 修复前:from定位落入字面量内,count sql破碎
		assertEquals(1L, count("select '从 from 开始' as label, id from t where id=?",
				new Object[] { 2 }));
	}

	@Test
	public void literalFromWithParams() throws Exception {
		// 修复前:from定位错位导致参数切片错误
		assertEquals(2L, count("select 'a from b' as x, id from t where id in (?,?)",
				new Object[] { 1, 2 }));
	}
}
