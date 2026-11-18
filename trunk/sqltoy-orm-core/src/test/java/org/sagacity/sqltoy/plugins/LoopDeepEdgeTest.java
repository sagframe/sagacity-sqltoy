package org.sagacity.sqltoy.plugins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.SqlToyThreadDataHolder;
import org.sagacity.sqltoy.config.SqlConfigParseUtils;
import org.sagacity.sqltoy.config.model.SqlToyResult;

/**
 * SecureSqlLoop与SqlLoop的深度边界矩阵:全空白迭代(首/中/尾/全部)、null元素、
 * 属性缓存跨迭代复用、多key部分空白、full形态段内空白、循环体引号紧贴#[的hasNullFilter边界
 *
 * @author zhongxuchen
 * @version v1.0,Date:2026-11-18
 */
public class LoopDeepEdgeTest {

	@BeforeEach
	public void setUp() {
		SqlToyThreadDataHolder.setCounter(0);
	}

	@AfterEach
	public void tearDown() {
		SqlToyThreadDataHolder.clearCounter();
	}

	private static SqlToyResult parse(String sql, String[] names, Object[] values) {
		return SqlConfigParseUtils.processSql(sql, names, values);
	}

	private static int count(String sql, String fragment) {
		int count = 0;
		int idx = 0;
		while ((idx = sql.indexOf(fragment, idx)) != -1) {
			count++;
			idx += fragment.length();
		}
		return count;
	}

	// ==================== SecureSqlLoop ====================

	@Test
	public void secureAllBlankRefsYieldCleanSql() {
		// 全部引用值空白:所有迭代跳过,最终sql应干净(无空括号组/孤立and/or)
		SqlToyResult result = parse(
				"select * from t where 1=1 and (@secure-loop(:names, ' #[ (t.name=:names[i] or t.alias=:ids[i])] ', ' or '))",
				new String[] { "names", "ids" }, new Object[] { new String[] { "a", "b" }, new String[] { "", null } });
		assertFalse(result.getSql().contains("t.name="), "全空白迭代不应产生条件: " + result.getSql());
		assertFalse(result.getSql().contains("or or"), "无残留or: " + result.getSql());
		assertFalse(result.getSql().contains("()"), "无空括号组: " + result.getSql());
	}

	@Test
	public void secureFirstAndLastBlankOnlyMiddleKept() {
		// 首尾空白、仅中间有效:linkSign只连接有效迭代(首个有效迭代不带前导连接符)
		SqlToyResult result = parse(
				"select * from t where 1=1 and (@secure-loop(:names, ' #[ (t.name=:ids[i])] ', ' or '))",
				new String[] { "names", "ids" },
				new Object[] { new String[] { "a", "b", "c" }, new Object[] { "", "y", "" } });
		assertEquals(1, count(result.getSql(), "t.name="), "仅中间有效: " + result.getSql());
		assertFalse(result.getSql().contains("or or"), "无残留or");
		assertTrue(java.util.Arrays.asList(result.getParamsValue()).contains("y"));
	}

	@Test
	public void secureNullElementAlsoSkipped() {
		// null元素与空白元素同样跳过(修复前null元素走段剔除会留or,空白元素条件保留)
		SqlToyResult result = parse(
				"select * from t where 1=1 and (@secure-loop(:names, ' t.flag=:ids[i] ', ' or '))",
				new String[] { "names", "ids" },
				new Object[] { new String[] { "a", "b", "c" }, new Object[] { "x", null, "z" } });
		assertEquals(2, count(result.getSql(), "t.flag="), "null元素迭代跳过: " + result.getSql());
		assertFalse(result.getSql().contains("or or"), "无残留or");
	}

	@Test
	public void secureBlankSpaceStringElementSkipped() {
		// 纯空白字符串元素(含空格/tab)同样跳过
		SqlToyResult result = parse(
				"select * from t where 1=1 and (@secure-loop(:names, ' t.flag=:ids[i] ', ' or '))",
				new String[] { "names", "ids" },
				new Object[] { new String[] { "a", "b" }, new Object[] { "  ", "x" } });
		assertEquals(1, count(result.getSql(), "t.flag="), "纯空白元素跳过: " + result.getSql());
	}

	@Test
	public void securePropertyCacheReuseAcrossSkippedIterations() {
		// 属性缓存跨迭代复用:迭代序列为[有效,空白,有效],跳过迭代后缓存槽位被后续迭代正确重填
		Object[] items = new Object[] { new SecureSqlLoopFunctionalTest.PlainItem("S1", 20),
				new SecureSqlLoopFunctionalTest.PlainItem("", 30), new SecureSqlLoopFunctionalTest.PlainItem("S3", 40) };
		SqlToyResult result = parse(
				"select * from t where 1=1 and (@secure-loop(:items, ' #[ s.name=:items[i].name and s.age=:items[i].age] ', ' or '))",
				new String[] { "items" }, new Object[] { items });
		assertEquals(2, count(result.getSql(), "s.name="), "空白name迭代跳过,S1/S3保留: " + result.getSql());
		java.util.List<Object> params = java.util.Arrays.asList(result.getParamsValue());
		assertTrue(params.contains("S3") && params.contains(40), "跳过后缓存重填,属性值正确: " + params);
		// 顺序锁定:name1,age1,name3,age3
		assertEquals("S1", result.getParamsValue()[0]);
		assertEquals(20, ((Number) result.getParamsValue()[1]).intValue());
		assertEquals("S3", result.getParamsValue()[2]);
		assertEquals(40, ((Number) result.getParamsValue()[3]).intValue());
	}

	@Test
	public void secureMultiKeyPartialBlankSkipsIteration() {
		// 多个引用key:任一key本迭代值空白即跳过整个迭代(另一key有效也一并跳过)
		SqlToyResult result = parse(
				"select * from t where 1=1 and (@secure-loop(:names, ' #[ (t.a=:xs[i] and t.b=:ys[i])] ', ' or '))",
				new String[] { "names", "xs", "ys" },
				new Object[] { new String[] { "n1", "n2" }, new Object[] { "x1", "x2" }, new Object[] { "y1", "" } });
		assertEquals(1, count(result.getSql(), "t.a="), "ys空白即整迭代跳过: " + result.getSql());
		assertFalse(result.getSql().contains("or or"));
	}

	@Test
	public void secureFullFormKeepsBlankPropertyInSegment() {
		// full形态:段内属性空白不剔除迭代,属性空白原值绑定(段保留)
		Object[] items = new Object[] { new SecureSqlLoopFunctionalTest.PlainItem("S1", 20),
				new SecureSqlLoopFunctionalTest.PlainItem("", 30) };
		SqlToyResult result = parse(
				"select * from t where 1=1 and (@secure-loop-full(:items, ' s.name=:items[i].name and s.age=:items[i].age ', ' or '))",
				new String[] { "items" }, new Object[] { items });
		assertEquals(2, count(result.getSql(), "s.name="), "full形态空白属性迭代保留");
		assertTrue(java.util.Arrays.asList(result.getParamsValue()).contains(""), "full空白属性原值绑定");
	}

	@Test
	public void secureNestedDynamicSegments() {
		// 循环体内嵌套#[]:内层#[引用数组、外层含普通参数,嵌套段剔除正常
		SqlToyResult result = parse(
				"select * from t where 1=1 and (@secure-loop(:names, ' #[ t.a=:xs[i] #[ and t.b=:status] ] ', ' or '))",
				new String[] { "names", "xs", "status" },
				new Object[] { new String[] { "n1", "n2" }, new Object[] { "x1", "" }, 1 });
		// xs[1]空白→迭代2跳过;迭代1的status有效内层保留
		assertEquals(1, count(result.getSql(), "t.a="), "空白迭代跳过: " + result.getSql());
		assertEquals(1, count(result.getSql(), "t.b="), "内层#[ status有效保留");
	}

	// ==================== SqlLoop ====================

	@Test
	public void sqlLoopAllSegmentsRemovedYieldCleanSql() {
		// 全部迭代段剔空:最终sql干净
		SqlToyResult result = parse(
				"select * from t where 1=1 and (@loop(:names, ' #[ (t.name=:names[i] or t.alias=:ids[i])] ', ' or '))",
				new String[] { "names", "ids" }, new Object[] { new String[] { "a", "b" }, new String[] { "", null } });
		assertFalse(result.getSql().contains("t.name="), "全剔空: " + result.getSql());
		assertFalse(result.getSql().contains("or or"), "无残留or: " + result.getSql());
	}

	@Test
	public void sqlLoopFirstSegmentRemovedNoLeadingLinkSign() {
		// 首迭代剔空:首个有效迭代不拼前导linkSign(index计数正确)
		SqlToyResult result = parse(
				"select * from t where 1=1 and (@loop(:names, ' #[ (t.name=:ids[i])] ', ' or '))",
				new String[] { "names", "ids" },
				new Object[] { new String[] { "a", "b", "c" }, new Object[] { "", "y", "z" } });
		String sql = result.getSql();
		assertEquals(2, count(sql, "t.name="), "首迭代剔空,y/z保留: " + sql);
		int firstCond = sql.indexOf("t.name=");
		// 首个有效条件之前不应出现linkSign连接符"or"
		assertFalse(sql.substring(0, firstCond).trim().endsWith("or"), "首个有效迭代不应有前导or: " + sql);
	}

	@Test
	public void sqlLoopLastSegmentRemovedNoTrailingLinkSign() {
		// 尾迭代剔空:末尾不残留连接符
		SqlToyResult result = parse(
				"select * from t where 1=1 and (@loop(:names, ' #[ (t.name=:ids[i])] ', ' or '))",
				new String[] { "names", "ids" },
				new Object[] { new String[] { "a", "b", "c" }, new Object[] { "x", "y", "" } });
		String sql = result.getSql();
		assertEquals(2, count(sql, "t.name="), "尾迭代剔空: " + sql);
		assertTrue(sql.trim().endsWith(")"), "末尾无残留连接符: " + sql);
	}

	@Test
	public void sqlLoopFullFormSegmentRemovedAlsoSkips() {
		// full形态:循环变量空白不跳迭代,但段内引用值空白剔段后迭代全空同样跳过(零残留)
		SqlToyResult result = parse(
				"select * from t where 1=1 and (@loop-full(:names, ' #[ (t.name=:names[i] and t.alias=:ids[i])] ', ' or '))",
				new String[] { "names", "ids" },
				new Object[] { new String[] { "a", "b" }, new Object[] { "x", "" } });
		assertFalse(result.getSql().contains("or or"), "full形态剔空迭代同样无残留: " + result.getSql());
		assertEquals(1, count(result.getSql(), "t.name="), "空白段迭代跳过");
	}

	@Test
	public void sqlLoopSegmentAtContentStartEdge() {
		// 边界修复锁定:循环体引号紧贴#[开头(hasNullFilter原用indexOf("#[")>0判定,首字符即#[时
		// 漏判→值先行内联→全局管线把段当无参片段整段剔除,有效条件全灭);修复后与带空格写法行为一致
		SqlToyResult result = parse(
				"select * from t where 1=1 and (@loop(:names, '#[ (t.name=:ids[i])]', ' or '))",
				new String[] { "names", "ids" },
				new Object[] { new String[] { "a", "b", "c" }, new Object[] { "x", "", "z" } });
		assertEquals(2, count(result.getSql(), "t.name="), "紧贴#[形态有效条件不应全灭: " + result.getSql());
		assertFalse(result.getSql().contains("or or"), "紧贴#[形态也不应残留or: " + result.getSql());
	}

	@Test
	public void sqlLoopEmptyParenConsistentWithEmptyArray() {
		// 全空迭代与空数组在"无外层#[]包裹"写法下的行为一致性对照:两者均残留and ( )空括号组
		// (既有边界,非本次引入;规范写法应外层包#[...],空时经@blank机制整段剔除)
		SqlToyResult allBlank = parse(
				"select * from t where 1=1 and (@loop(:names, ' #[ (t.name=:ids[i])] ', ' or '))",
				new String[] { "names", "ids" }, new Object[] { new String[] { "a", "b" }, new String[] { "", null } });
		SqlToyResult emptyAry = parse(
				"select * from t where 1=1 and (@loop(:names, ' #[ (t.name=:ids[i])] ', ' or '))",
				new String[] { "names", "ids" }, new Object[] { new String[] { "a", "b" }, new String[] {} });
		assertFalse(allBlank.getSql().contains("or or"));
		assertFalse(emptyAry.getSql().contains("or or"));
		// 行为一致性:两种"无有效条件"的输入产出形态等价(空括号组为既有行为,与6.0.10一致)
		assertEquals(emptyAry.getSql().replace(" @blank(:ids) ", "").trim().replaceAll("\\s+", ""),
				allBlank.getSql().trim().replaceAll("\\s+", ""), "全空迭代与空数组行为对齐");
	}

	@Test
	public void sqlLoopComboLoopVarAndRefBlank() {
		// 循环变量空白跳过与段剔空跳过的组合:两类跳过混排零残留
		SqlToyResult result = parse(
				"select * from t where 1=1 and (@loop(:names, ' #[ (t.name=:names[i] or t.alias=:ids[i])] ', ' or '))",
				new String[] { "names", "ids" },
				new Object[] { new Object[] { "a", "", "c", "d" }, new Object[] { "x", "y", "", "z" } });
		String sql = result.getSql();
		// 迭代1(a,x)有效;迭代2循环变量空白跳;迭代3(c,ids空白)段剔空跳;迭代4(d,z)有效
		assertEquals(2, count(sql, "t.name="), "两类跳过组合: " + sql);
		assertFalse(sql.contains("or or"), "零残留");
	}
}
