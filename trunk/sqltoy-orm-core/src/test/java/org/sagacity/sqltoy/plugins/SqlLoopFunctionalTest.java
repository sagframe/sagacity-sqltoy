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
 * @loop / @loop-full 功能正确性矩阵:以processSql端到端解析,锁定
 * 1.like-or/in-list基本形态与linkSign;2.skipBlank与@loop-full的null/空白语义;
 * 3.空/null数组的@blank剔除;4.start/end窗口(负起点钳0/越界剔除);
 * 5.属性形态(:x[i].prop);6.并行数组最短截断;7.多次@loop共存。
 * 注意@loop为拼接式语义:值经toSqlString内联,字符串引号由内容体提供,
 * 内容体含单引号时宏参数建议用双引号包裹(单引号壳内的引号需转义易错)。
 */
public class SqlLoopFunctionalTest {

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

	private static String normalize(String sql) {
		return sql.replaceAll("\\s+", " ").trim();
	}

	@Test
	public void likeOrBasicConcatenatesQuotedValues() {
		SqlToyResult result = parse(
				"select * from t where 1=1 #[@loop(:names, \" name like '%:names[i]%' \", ' or ')]",
				new String[] { "names" }, new Object[] { new String[] { "a", "b", "c" } });
		String norm = normalize(result.getSql());
		// 拼接式语义:值以字面量直接进入sql(引号由内容体提供)
		assertTrue(norm.contains("name like '%a%' or name like '%b%' or name like '%c%'"),
				"拼接结果: " + norm);
		// 无参数引用残留
		assertFalse(norm.contains(":names"), "拼接后不应残留参数引用: " + norm);
	}

	@Test
	public void inListRawNumberConcatenation() {
		SqlToyResult result = parse("select * from t where id in (#[@loop(:ids, ':ids[i]', ',')])",
				new String[] { "ids" }, new Object[] { new Long[] { 1L, 2L, 3L } });
		String norm = normalize(result.getSql());
		assertTrue(norm.contains("id in ( 1 , 2 , 3 )"), "数值原样内联: " + norm);
	}

	@Test
	public void skipBlankDropsNullAndEmptyEntries() {
		SqlToyResult result = parse(
				"select * from t where 1=1 #[@loop(:names, \" name=':names[i]' \", ' or ')]",
				new String[] { "names" }, new Object[] { new String[] { "a", "", null, "b" } });
		String norm = normalize(result.getSql());
		assertTrue(norm.contains("name='a' or name='b'"), "null与空白元素跳过: " + norm);
		assertFalse(norm.contains("is null"), "跳过语义不应产生is null: " + norm);
	}

	@Test
	public void fullFormConvertsNullToIsNull() {
		SqlToyResult result = parse(
				"select * from t where 1=1 #[@loop-full(:vals, \" name=:vals[i] \", ' or ')]",
				new String[] { "vals" }, new Object[] { new String[] { null, "x" } });
		String norm = normalize(result.getSql());
		// null值在where比较位转is null
		assertTrue(norm.contains("name is null or name='x'"), "null转is null: " + norm);
	}

	@Test
	public void emptyArrayDropsWholeConditionBlock() {
		SqlToyResult result = parse(
				"select * from t where 1=1 #[@loop(:names, \" name like '%:names[i]%' \", ' or ')]",
				new String[] { "names" }, new Object[] { new String[] {} });
		assertFalse(normalize(result.getSql()).contains("name like"), "空数组应剔除条件块: " + result.getSql());
	}

	@Test
	public void nullArrayDropsWholeConditionBlock() {
		SqlToyResult result = parse(
				"select * from t where 1=1 #[@loop(:names, \" name like '%:names[i]%' \", ' or ')]",
				new String[] { "names" }, new Object[] { null });
		assertFalse(normalize(result.getSql()).contains("name like"), "null数组应剔除条件块: " + result.getSql());
	}

	@Test
	public void startEndWindowSelectsSubRange() {
		SqlToyResult result = parse(
				"select * from t where id in (#[@loop(:ids, ':ids[i]', ',', 1, 3)])",
				new String[] { "ids" }, new Object[] { new Long[] { 1L, 2L, 3L, 4L } });
		String norm = normalize(result.getSql());
		assertTrue(norm.contains("2 , 3"), "窗口[1,3)取2和3: " + norm);
		assertFalse(norm.contains("1 , 2 ,"), "窗口外1不在: " + norm);
	}

	@Test
	public void negativeStartClampedToZero() {
		SqlToyResult result = parse(
				"select * from t where id in (#[@loop(:ids, ':ids[i]', ',', -2, 2)])",
				new String[] { "ids" }, new Object[] { new Long[] { 1L, 2L, 3L } });
		String norm = normalize(result.getSql());
		assertTrue(norm.contains("1 , 2"), "负起点按0处理: " + norm);
	}

	@Test
	public void startBeyondLengthDropsBlock() {
		SqlToyResult result = parse(
				"select * from t where 1=1 #[@loop(:ids, ' id=:ids[i] ', ' or ', 9, 12)]",
				new String[] { "ids" }, new Object[] { new Long[] { 1L, 2L, 3L } });
		assertFalse(normalize(result.getSql()).contains("id="), "起点越界应剔除条件块: " + result.getSql());
	}

	@Test
	public void propertyFormConcatenatesEachProperty() {
		Object[] staffs = new Object[] { new LoopStaff("S1", 20), new LoopStaff("S2", 30) };
		SqlToyResult result = parse(
				"select * from t where 1=1 #[@loop(:staffs, \" s.name=':staffs[i].name' and s.age=:staffs[i].age \", ' or ')]",
				new String[] { "staffs" }, new Object[] { staffs });
		String norm = normalize(result.getSql());
		assertTrue(norm.contains("s.name='S1' and s.age=20 or s.name='S2' and s.age=30"),
				"属性形态拼接: " + norm);
	}

	@Test
	public void parallelArraysTruncateToShortest() {
		SqlToyResult result = parse(
				"select * from t where 1=1 #[@loop(:names, \" name=':names[i]' and age=:ages[i] \", ' or ')]",
				new String[] { "names", "ages" },
				new Object[] { new String[] { "a", "b", "c" }, new Integer[] { 1, 2 } });
		String norm = normalize(result.getSql());
		assertTrue(norm.contains("name='b' and age=2"), "按最短并行数组截断: " + norm);
		assertFalse(norm.contains("'c'"), "超出最短数组的元素不参与: " + norm);
	}

	@Test
	public void multipleLoopsCoexistInSameSql() {
		SqlToyResult result = parse(
				"select * from t where 1=1 #[@loop(:names, \" name like '%:names[i]%' \", ' or ')]"
						+ " #[@loop(:cities, \" city=':cities[i]' \", ' or ')]",
				new String[] { "names", "cities" },
				new Object[] { new String[] { "a" }, new String[] { "SH", "BJ" } });
		String norm = normalize(result.getSql());
		assertTrue(norm.contains("name like '%a%'"), "第一个loop生效: " + norm);
		assertTrue(norm.contains("city='SH' or city='BJ'"), "第二个loop生效: " + norm);
	}

	public static class LoopStaff {
		private final String name;
		private final Integer age;

		public LoopStaff(String name, Integer age) {
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
}
