package org.sagacity.sqltoy.plugins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.demo.vo.StaffInfoVO;
import org.sagacity.sqltoy.model.IgnoreKeyCaseMap;
import org.sagacity.sqltoy.plugins.id.macro.impl.SqlLoop;

/**
 * @loop(SqlLoop)行为回归:参数形态、空值与跳过、起始/结束下标、连接符、最短数组截断、
 * 大小写与[index]别名、以及循环体内#[ ]条件参数为空时的剔除
 */
public class SqlLoopBehaviorTest {

	private static final String WHERE_PRE_SQL = "select * from t where 1=1 ";

	private static IgnoreKeyCaseMap<String, Object> of(String key, Object value) {
		IgnoreKeyCaseMap<String, Object> keyValues = new IgnoreKeyCaseMap<String, Object>();
		keyValues.put(key, value);
		return keyValues;
	}

	private static String run(String[] params, Map<String, Object> keyValues) {
		return run(true, params, keyValues, null);
	}

	private static String run(boolean skipBlank, String[] params, Map<String, Object> keyValues, String preSql) {
		return new SqlLoop(skipBlank).execute(params, keyValues, null, preSql, null);
	}

	/**
	 * 收敛空白:循环宏在连接符两侧各补一个空白,断言结构时不应耦合这些填充
	 * (测试取值内部无连续空白,归一化不影响字面量内容)
	 */
	private static String norm(String sql) {
		return sql.replaceAll("\\s+", " ").trim();
	}

	// ============ 入参与空集合 ============

	@Test
	public void missingOrEmptyParamsReturnBlank() {
		// keyValues为空/为null、params不足两项:直接返回空白占位
		assertEquals(" ", new SqlLoop().execute(null, of("ids", new String[] { "A" }), null, null, null));
		assertEquals(" ", new SqlLoop().execute(new String[] { "ids" }, of("ids", new String[] { "A" }), null, null, null));
		assertEquals(" ", new SqlLoop().execute(new String[] { "ids", "x=:ids[i]" }, new HashMap<String, Object>(), null,
				null, null));
		assertEquals(" ", new SqlLoop().execute(new String[] { "ids", "x=:ids[i]" }, null, null, null, null));
	}

	@Test
	public void emptyOrAbsentLoopArrayReturnsBlankPlaceholder() {
		// 便于所在#[]内容被整体剔除,故以@blank(:paramName)占位
		assertEquals(" @blank(:ids) ", run(new String[] { "ids", "x=:ids[i]" }, of("ids", new ArrayList<String>())));
		assertEquals(" @blank(:ids) ",
				run(new String[] { "ids", "x=:ids[i]" }, of("other", new String[] { "A" })));
	}

	// ============ 参数形态 ============

	@Test
	public void surroundingQuotesAndLeadingColonAreStripped() {
		IgnoreKeyCaseMap<String, Object> keyValues = of("ids", new String[] { "A", "B" });
		// '...' / "..." / {...} 包裹与参数前导冒号均被剔除
		assertTrue(norm(run(new String[] { "'ids'", "'x=:ids[i]'", "' or '" }, keyValues)).contains("x='A' or x='B'"));
		assertTrue(norm(run(new String[] { "\":ids\"", "\"x=:ids[i]\"", "\" or \"" }, keyValues)).contains("x='A' or x='B'"));
		assertTrue(norm(run(new String[] { "{ids}", "{x=:ids[i]}", "{ or }" }, keyValues)).contains("x='A' or x='B'"));
	}

	@Test
	public void linkSignJoinsRows() {
		String result = run(new String[] { "ids", "x=:ids[i]", " and " }, of("ids", new String[] { "A", "B", "C" }));
		assertTrue(result.contains("x='A' and x='B' and x='C'"), "实际:" + result);
	}

	@Test
	public void indexAliasAndCaseInsensitiveKeyBothWork() {
		IgnoreKeyCaseMap<String, Object> keyValues = of("ids", new String[] { "A", "B" });
		assertTrue(norm(run(new String[] { "ids", "x=:ids[index]", " or " }, keyValues)).contains("x='A' or x='B'"));
		// 循环体里写大写的参数名、[I] 大写,均能识别
		assertTrue(norm(run(new String[] { "ids", "x=:IDS[I]", " or " }, keyValues)).contains("x='A' or x='B'"));
	}

	// ============ 起始/结束下标 ============

	@Test
	public void startAndEndLimitRows() {
		IgnoreKeyCaseMap<String, Object> keyValues = of("ids", new String[] { "A", "B", "C", "D" });
		// start=1,end=3 → 取下标1、2
		String result = run(new String[] { "ids", "x=:ids[i]", " or ", "1", "3" }, keyValues);
		assertTrue(result.contains("x='B' or x='C'"), "实际:" + result);
		assertFalse(result.contains("x='A'") || result.contains("x='D'"), "实际:" + result);
		// end超出长度按长度钳制
		assertTrue(norm(run(new String[] { "ids", "x=:ids[i]", " or ", "2", "99" }, keyValues)).contains("x='C' or x='D'"));
		// start超出可循环范围返回占位
		assertEquals(" @blank(:ids) ", run(new String[] { "ids", "x=:ids[i]", " or ", "4" }, keyValues));
	}

	// ============ 空值处理与跳过 ============

	@Test
	public void nullAndBlankElementsSkippedByDefault() {
		IgnoreKeyCaseMap<String, Object> keyValues = of("ids", new String[] { "A", null, " ", "B" });
		String result = run(new String[] { "ids", "x=:ids[i]", " or " }, keyValues);
		assertTrue(result.contains("x='A' or x='B'"), "null与空白应被跳过,实际:" + result);
	}

	@Test
	public void skipBlankFalseKeepsNullAndTurnsIntoIsNull() {
		IgnoreKeyCaseMap<String, Object> keyValues = of("ids", new String[] { "A", null });
		// where场景:=null 调整为 is null
		String result = run(false, new String[] { "ids", "x=:ids[i]", " or " }, keyValues, WHERE_PRE_SQL);
		assertTrue(result.contains("x='A' or x is null"), "实际:" + result);
		// <>null 调整为 is not null
		String notEqual = run(false, new String[] { "ids", "x<>:ids[i]", " or " }, keyValues, WHERE_PRE_SQL);
		assertTrue(notEqual.contains("x is not null"), "实际:" + notEqual);
	}

	@Test
	public void updateSetKeepsEqualNull() {
		IgnoreKeyCaseMap<String, Object> keyValues = of("ids", new String[] { null });
		// update set field=:param 场景不能被改成 is null(类注释记录的既有约定)
		String result = run(false, new String[] { "ids", "field=:ids[i]" }, keyValues, "update t set ");
		assertTrue(result.contains("field=null"), "实际:" + result);
		assertFalse(result.contains("is null"), "实际:" + result);
	}

	/**
	 * null值与各比较符号的转换矩阵:仅=、<>、!= 被改写为 is (not) null,其余符号保持 null 由数据库按SQL语义处理
	 */
	@Test
	public void nullValueComparisonMatrix() {
		IgnoreKeyCaseMap<String, Object> keyValues = of("ids", new String[] { null });
		assertTrue(run(false, new String[] { "ids", "a=:ids[i]" }, keyValues, WHERE_PRE_SQL).contains("a is null"));
		assertTrue(run(false, new String[] { "ids", "a<>:ids[i]" }, keyValues, WHERE_PRE_SQL).contains("a is not null"));
		assertTrue(run(false, new String[] { "ids", "a!=:ids[i]" }, keyValues, WHERE_PRE_SQL).contains("a is not null"));
		// 其余比较符号不改写(>,>=,<,<= 与 null 比较是合法SQL)
		assertTrue(run(false, new String[] { "ids", "a>=:ids[i]" }, keyValues, WHERE_PRE_SQL).contains("a>=null"));
		assertTrue(run(false, new String[] { "ids", "a<=:ids[i]" }, keyValues, WHERE_PRE_SQL).contains("a<=null"));
		assertTrue(run(false, new String[] { "ids", "a>:ids[i]" }, keyValues, WHERE_PRE_SQL).contains("a>null"));
		assertTrue(run(false, new String[] { "ids", "a<:ids[i]" }, keyValues, WHERE_PRE_SQL).contains("a<null"));
	}

	@Test
	public void nullPropertyValueOfElementTurnsIntoIsNull() {
		List<StaffInfoVO> staffs = new ArrayList<StaffInfoVO>();
		StaffInfoVO staff = new StaffInfoVO();
		staff.setStaffId("S1");
		staff.setBirthday(null);
		staffs.add(staff);
		IgnoreKeyCaseMap<String, Object> keyValues = of("staffs", staffs);
		String result = run(false, new String[] { "staffs", "birthday=:staffs[i].birthday" }, keyValues, WHERE_PRE_SQL);
		assertTrue(result.contains("birthday is null"), "实际:" + result);
	}

	// ============ 值类型与引号策略 ============

	@Test
	public void nonStringValuesAreInlinedWithoutQuotes() {
		IgnoreKeyCaseMap<String, Object> keyValues = new IgnoreKeyCaseMap<String, Object>();
		keyValues.put("ages", new Integer[] { 18, 20 });
		assertTrue(norm(run(new String[] { "ages", "age=:ages[i]", " or " }, keyValues)).contains("age=18 or age=20"));
	}

	@Test
	public void valueInsideExistingLiteralIsNotQuotedTwice() {
		// 循环体自身已带引号(like场景):值直接内联,不再补引号
		IgnoreKeyCaseMap<String, Object> keyValues = of("ids", new String[] { "S1", "S2" });
		String result = run(new String[] { "ids", " and staffId like '%:ids[i]%'" }, keyValues);
		assertTrue(result.contains("staffId like '%S1%'"), "实际:" + result);
		assertTrue(result.contains("staffId like '%S2%'"), "实际:" + result);
	}

	@Test
	public void dateAndLocalDateValuesAreFormatted() {
		List<StaffInfoVO> staffs = new ArrayList<StaffInfoVO>();
		StaffInfoVO staff = new StaffInfoVO();
		staff.setStaffId("S1");
		staff.setBirthday(LocalDate.of(2026, 9, 14));
		staffs.add(staff);
		String result = run(new String[] { "staffs", "birthday=:staffs[i].birthday" }, of("staffs", staffs));
		assertTrue(result.contains("birthday='2026-09-14'"), "实际:" + result);
	}

	// ============ 非循环参数与最短数组 ============

	@Test
	public void nonLoopParamsAreLeftUntouched() {
		IgnoreKeyCaseMap<String, Object> keyValues = of("ids", new String[] { "A" });
		keyValues.put("status", 1);
		keyValues.put("createTime", null);
		String result = run(new String[] { "ids", "(id=:ids[i] and status=:status and createTime>:createTime)", " or " },
				keyValues);
		// 非循环参数由外层参数处理流程负责,循环宏只替换循环变量
		assertTrue(result.contains("status=:status"), "实际:" + result);
		assertTrue(result.contains("createTime>:createTime"), "实际:" + result);
	}

	@Test
	public void referencedShorterArrayTruncatesLoop() {
		IgnoreKeyCaseMap<String, Object> keyValues = new IgnoreKeyCaseMap<String, Object>();
		keyValues.put("ids", new String[] { "1", "2", "3" });
		keyValues.put("names", new String[] { "one", "two" });
		String result = run(new String[] { "ids", "(:names[i]=:ids[i])", " or " }, keyValues);
		// 引号策略:仅当参数紧跟比较符号时才补单引号,故左操作数按裸值内联(常规写法是列名在左、参数在右),
		// 右侧参数位于"="之后,加引号
		assertTrue(norm(result).contains("(one='1') or (two='2')"), "实际:" + result);
		// 按最短数组截断,第三个元素不参与
		assertFalse(result.contains("'3'"), "实际:" + result);
	}

	// ============ 循环体内 #[ ] 条件参数为空 ============

	@Test
	public void nullConditionParamRemovesPseudoBlock() {
		IgnoreKeyCaseMap<String, Object> keyValues = of("ids", new String[] { "A" });
		keyValues.put("status", 1);
		keyValues.put("type", null);
		String kept = run(new String[] { "ids", "(id=:ids[i] #[and status=:status])", " or " }, keyValues);
		assertTrue(kept.contains("and status=:status"), "非空条件应保留,实际:" + kept);
		String removed = run(new String[] { "ids", "(id=:ids[i] #[and type=:type])", " or " }, keyValues);
		assertFalse(removed.contains("type=:type"), "空值条件应整体剔除,实际:" + removed);
		assertFalse(removed.contains("#["), "不应残留条件标记,实际:" + removed);
	}

	@Test
	public void pseudoBlockWithoutParamsIsRemoved() {
		IgnoreKeyCaseMap<String, Object> keyValues = of("ids", new String[] { "A" });
		String result = run(new String[] { "ids", "(id=:ids[i] #[and 1=1])", " or " }, keyValues);
		assertFalse(result.contains("#["), "实际:" + result);
		assertTrue(result.contains("(id='A'"), "实际:" + result);
	}

	@Test
	public void multiplePseudoBlocksHandledPerRow() {
		IgnoreKeyCaseMap<String, Object> keyValues = of("ids", new String[] { "A", "B" });
		keyValues.put("status", 1);
		keyValues.put("type", null);
		String result = run(new String[] { "ids",
				"(id=:ids[i] #[and status=:status] #[and type=:type])", " or " }, keyValues);
		assertTrue(result.contains("id='A'") && result.contains("id='B'"), "实际:" + result);
		assertFalse(result.contains("type=:type"), "实际:" + result);
		assertEquals(2, countOf(result, "and status=:status"), "每行都应保留非空条件,实际:" + result);
	}

	@Test
	public void unbalancedPseudoMarkThrows() {
		IgnoreKeyCaseMap<String, Object> keyValues = of("ids", new String[] { "A" });
		keyValues.put("status", 1);
		// 缺少配对"]"时应给出明确异常而不是静默
		assertThrows(java.util.IllegalFormatFlagsException.class,
				() -> run(new String[] { "ids", "(id=:ids[i] #[and status=:status)", " or " }, keyValues));
	}

	private static int countOf(String source, String sub) {
		int count = 0;
		int index = source.indexOf(sub);
		while (index != -1) {
			count++;
			index = source.indexOf(sub, index + sub.length());
		}
		return count;
	}
}
