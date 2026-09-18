package org.sagacity.sqltoy.plugins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.SqlToyThreadDataHolder;
import org.sagacity.sqltoy.config.SqlConfigParseUtils;
import org.sagacity.sqltoy.config.model.SqlToyResult;

/**
 * @secure-loop功能正确性矩阵:以processSql端到端解析,锁定
 * 1.基本形态(like-or/in-list)与linkSign;2.skipBlank与@secure-loop-full的null/空白语义;
 * 3.空数组/null数组的@blank剔除;4.start/end窗口(含负起点钳0与越界钳制);
 * 5.属性形态(:x[i].prop)与并行数组最短截断;6.防注入:特殊字符值走参数绑定不拼进sql。
 */
public class SecureSqlLoopFunctionalTest {

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

	/** 属性形态用例的循环元素 */
	public static class PlainItem {
		private final String name;
		private final Integer age;

		public PlainItem(String name, Integer age) {
			this.name = name;
			this.age = age;
		}

		public String getName() {
			return name;
		}

		public Integer getAge() {
			return age;
		}
	}

	private static int countOccurrences(String sql, String fragment) {
		int count = 0;
		int idx = 0;
		while ((idx = sql.indexOf(fragment, idx)) != -1) {
			count++;
			idx += fragment.length();
		}
		return count;
	}

	@Test
	public void likeOrBasicBindsValuesInOrder() {
		SqlToyResult result = parse(
				"select * from t where 1=1 #[@secure-loop(:names, ' name like :names[i] ', ' or ')]",
				new String[] { "names" }, new Object[] { new String[] { "a", "b", "c" } });
		assertEquals(3, countOccurrences(result.getSql(), "name like"), "每个元素一组like条件");
		// 绑定值保持元素顺序(like条件值由框架包%..%通配)
		assertTrue(Arrays.asList(result.getParamsValue()).contains("%a%"), "应绑定%a%");
		assertTrue(Arrays.asList(result.getParamsValue()).contains("%b%"), "应绑定%b%");
		assertTrue(Arrays.asList(result.getParamsValue()).contains("%c%"), "应绑定%c%");
	}

	@Test
	public void inListFormWithCommaLink() {
		SqlToyResult result = parse("select * from t where id in (#[ @secure-loop(:ids, ':ids[i]', ',') ])",
				new String[] { "ids" }, new Object[] { new Long[] { 1L, 2L, 3L } });
		assertEquals(3, countOccurrences(result.getSql(), "?"), "in-list内3个绑定占位符");
		assertTrue(Arrays.asList(result.getParamsValue()).contains(1L), "应绑定1");
		assertTrue(Arrays.asList(result.getParamsValue()).contains(3L), "应绑定3");
	}

	@Test
	public void skipBlankDropsNullAndEmptyEntries() {
		SqlToyResult result = parse(
				"select * from t where 1=1 #[@secure-loop(:names, ' name like :names[i] ', ' or ')]",
				new String[] { "names" }, new Object[] { new String[] { "a", "", null, "b" } });
		assertEquals(2, countOccurrences(result.getSql(), "name like"), "null与空白元素被跳过");
		assertFalse(Arrays.asList(result.getParamsValue()).contains(""), "空白不应被绑定");
		assertFalse(Arrays.asList(result.getParamsValue()).contains(null), "null不应被绑定");
	}

	@Test
	public void fullFormKeepsNullAndEmptyEntries() {
		SqlToyResult result = parse(
				"select * from t where 1=1 #[@secure-loop-full(:names, ' name like :names[i] ', ' or ')]",
				new String[] { "names" }, new Object[] { new String[] { "a", "", "b" } });
		assertEquals(3, countOccurrences(result.getSql(), "name like"), "full形态不跳过空白");
		// 空白元素绑定值为%%(like通配包装)
		assertTrue(Arrays.asList(result.getParamsValue()).contains("%%"), "空白应被绑定");
	}

	@Test
	public void emptyArrayDropsWholeConditionBlock() {
		SqlToyResult result = parse(
				"select * from t where 1=1 #[@secure-loop(:names, ' name like :names[i] ', ' or ')]",
				new String[] { "names" }, new Object[] { new String[] {} });
		// 空数组→@blank(:names)→#[...]整块剔除,不残留like条件
		assertFalse(result.getSql().contains("name like"), "空数组应剔除条件块: " + result.getSql());
	}

	@Test
	public void nullArrayDropsWholeConditionBlock() {
		SqlToyResult result = parse(
				"select * from t where 1=1 #[@secure-loop(:names, ' name like :names[i] ', ' or ')]",
				new String[] { "names" }, new Object[] { null });
		assertFalse(result.getSql().contains("name like"), "null数组应剔除条件块: " + result.getSql());
	}

	@Test
	public void startEndWindowSelectsSubRange() {
		SqlToyResult result = parse(
				"select * from t where 1=1 #[@secure-loop(:names, ' name like :names[i] ', ' or ', 1, 3)]",
				new String[] { "names" }, new Object[] { new String[] { "a", "b", "c", "d" } });
		assertEquals(2, countOccurrences(result.getSql(), "name like"), "窗口[1,3)取b和c");
		assertTrue(Arrays.asList(result.getParamsValue()).contains("%b%"), "应绑定%b%");
		assertTrue(Arrays.asList(result.getParamsValue()).contains("%c%"), "应绑定%c%");
		assertFalse(Arrays.asList(result.getParamsValue()).contains("%a%"), "窗口外a不绑定");
	}

	@Test
	public void negativeStartClampedToZero() {
		SqlToyResult result = parse(
				"select * from t where 1=1 #[@secure-loop(:names, ' name like :names[i] ', ' or ', -2, 2)]",
				new String[] { "names" }, new Object[] { new String[] { "a", "b", "c" } });
		assertEquals(2, countOccurrences(result.getSql(), "name like"), "负起点按0处理取a和b");
	}

	@Test
	public void startBeyondLengthDropsBlock() {
		SqlToyResult result = parse(
				"select * from t where 1=1 #[@secure-loop(:names, ' name like :names[i] ', ' or ', 9, 12)]",
				new String[] { "names" }, new Object[] { new String[] { "a", "b", "c" } });
		assertFalse(result.getSql().contains("name like"), "起点越界应剔除条件块: " + result.getSql());
	}

	@Test
	public void propertyFormBindsEachPropertyInOrder() {
		Object[] staffs = new Object[] { new PlainItem("S1", 20), new PlainItem("S2", 30) };
		SqlToyResult result = parse(
				"select * from t where 1=1 #[@secure-loop(:staffs, ' s.name=:staffs[i].name and s.age=:staffs[i].age ', ' or ')]",
				new String[] { "staffs" }, new Object[] { staffs });
		assertEquals(2, countOccurrences(result.getSql(), "s.name="), "每元素一组name条件");
		assertEquals(2, countOccurrences(result.getSql(), "s.age="), "每元素一组age条件");
		// 绑定顺序:name1,age1,name2,age2
		assertEquals("S1", result.getParamsValue()[0], "第一组name");
		assertEquals(20, ((Number) result.getParamsValue()[1]).intValue(), "第一组age");
		assertEquals("S2", result.getParamsValue()[2], "第二组name");
		assertEquals(30, ((Number) result.getParamsValue()[3]).intValue(), "第二组age");
	}

	@Test
	public void parallelArraysTruncateToShortest() {
		SqlToyResult result = parse(
				"select * from t where 1=1 #[@secure-loop(:names, ' name like :names[i] and age=:ages[i] ', ' or ')]",
				new String[] { "names", "ages" },
				new Object[] { new String[] { "a", "b", "c" }, new Integer[] { 1, 2 } });
		// ages只有2个元素,循环按最短截断
		assertEquals(2, countOccurrences(result.getSql(), "name like"), "按最短并行数组截断");
	}

	@Test
	public void injectionValueStaysAsBoundParameter() {
		String injection = "x' or '1'='1";
		SqlToyResult result = parse(
				"select * from t where 1=1 #[@secure-loop(:names, ' name like :names[i] ', ' or ')]",
				new String[] { "names" }, new Object[] { new String[] { "a", injection } });
		// 防注入核心断言:恶意片段不得以文本形式出现在sql中,必须走参数绑定
		assertFalse(result.getSql().contains("'1'='1"), "恶意片段不得拼入sql: " + result.getSql());
		assertTrue(Arrays.asList(result.getParamsValue()).contains("%" + injection + "%"),
				"恶意片段应作为绑定参数值");
	}

	@Test
	public void loopContentCanReferencePlainParams() {
		SqlToyResult result = parse(
				"select * from t where 1=1 #[@secure-loop(:names, ' name like :names[i] and status=:status ', ' or ')]",
				new String[] { "names", "status" }, new Object[] { new String[] { "a", "b" }, 1 });
		assertEquals(2, countOccurrences(result.getSql(), "status="), "循环体外普通参数按原引用保留");
	}
}
