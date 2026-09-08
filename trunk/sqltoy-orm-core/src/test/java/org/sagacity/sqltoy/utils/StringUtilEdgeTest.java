package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * StringUtil 边缘场景测试
 */
public class StringUtilEdgeTest {

	@Test
	public void isBlank() {
		assertTrue(StringUtil.isBlank(null));
		assertTrue(StringUtil.isBlank(""));
		assertTrue(StringUtil.isBlank("   "));
		assertTrue(StringUtil.isBlank(new ArrayList<>()));
		assertTrue(StringUtil.isBlank(new HashMap<>()));
		assertTrue(StringUtil.isBlank(new Object[0]));
		// 非空集合、单元素数组、数字对象
		assertFalse(StringUtil.isBlank(List.of("a")));
		assertFalse(StringUtil.isBlank(new Object[] { null }));
		assertFalse(StringUtil.isBlank(123));
		assertTrue(StringUtil.isNotBlank("a"));
	}

	@Test
	public void trimedEquals() {
		assertTrue(StringUtil.trimedEquals(" ab ", "ab"));
		assertTrue(StringUtil.trimedEquals(null, null));
		// null与""不相等(且不抛异常)
		assertFalse(StringUtil.trimedEquals(null, ""));
		assertFalse(StringUtil.trimedEquals("", null));
		assertTrue(StringUtil.trimedIgnoreCaseEquals(" AB ", "ab"));
	}

	@Test
	public void firstCaseConvert() {
		assertEquals("Abc", StringUtil.firstToUpperCase("abc"));
		assertEquals("aBC", StringUtil.firstToLowerCase("ABC"));
		// 中文、单字符
		assertEquals("中", StringUtil.firstToUpperCase("中"));
		assertEquals("a", StringUtil.firstToLowerCase("A"));
		assertNull(StringUtil.firstToUpperCase(null));
		assertEquals("", StringUtil.firstToLowerCase(""));
		// 空白字符串原样返回
		assertEquals(" ", StringUtil.firstToUpperCase(" "));
		// 其余字符保持不变
		assertEquals("AbC", StringUtil.firstToUpperCase("abC"));
		assertEquals("Abc", StringUtil.firstToUpperOtherToLower("ABC"));
	}

	@Test
	public void indexOfIgnoreCase() {
		assertEquals(2, StringUtil.indexOfIgnoreCase("xxSelectXX", "select"));
		assertEquals(-1, StringUtil.indexOfIgnoreCase(null, "a"));
		assertEquals(-1, StringUtil.indexOfIgnoreCase("a", null));
		// 带起始位置
		assertEquals(6, StringUtil.indexOfIgnoreCase("aBCdEFghiJK", "ghi", 3));
		// start超界返回-1不抛异常
		assertEquals(-1, StringUtil.indexOfIgnoreCase("abc", "a", 10));
	}

	@Test
	public void addSign2Len() {
		assertEquals("00abc", StringUtil.addLeftZero2Len("abc", 5));
		assertEquals("abc00", StringUtil.addRightZero2Len("abc", 5));
		assertEquals("abc  ", StringUtil.addRightBlank2Len("abc", 5));
		// 长度已达标原样返回
		assertEquals("abcdef", StringUtil.addLeftZero2Len("abcdef", 5));
		assertEquals("abc", StringUtil.addLeftZero2Len("abc", 3));
		assertNull(StringUtil.addLeftZero2Len(null, 5));
		// 负数长度按已达标处理
		assertEquals("abc", StringUtil.addLeftZero2Len("abc", -1));
	}

	@Test
	public void loopAppendWithSign() {
		assertEquals("a,a,a", StringUtil.loopAppendWithSign("a", ",", 3));
		// null按空串处理
		assertEquals(",,", StringUtil.loopAppendWithSign(null, ",", 3));
		assertEquals("", StringUtil.loopAppendWithSign("a", ",", 0));
		assertEquals("", StringUtil.loopAppendWithSign("a", ",", -1));
	}

	@Test
	public void appendStr() {
		assertEquals("**ab", StringUtil.appendStr("ab", "*", 4, true));
		assertEquals("ab**", StringUtil.appendStr("ab", "*", 4, false));
		// size小于已有长度时不截断
		assertEquals("abcdef", StringUtil.appendStr("abcdef", "*", 3, true));
	}

	@Test
	public void clearMistyChars() {
		assertEquals("a,b,c,d", StringUtil.clearMistyChars("a\tb\rc\nd", ","));
		assertNull(StringUtil.clearMistyChars(null, ","));
	}

	@Test
	public void getSymMarkIndexWithEscapedQuote() {
		// 单引号内包含转义引号
		String sql = "select 'a\\'b' from t where x=1";
		int end = StringUtil.getSymMarkIndex("'", "'", sql, 0);
		assertEquals(sql.indexOf("b'") + 1, end);
		// 括号模式:嵌套匹配最外层
		String str = "fun((a,b),c)";
		assertEquals(str.lastIndexOf(")"), StringUtil.getSymMarkIndex("(", ")", str, 0));
		// 起始符号不存在时返回结束符号位置
		String str2 = "abc)";
		assertEquals(3, StringUtil.getSymMarkIndex("(", ")", str2, 0));
	}

	@Test
	public void splitExcludeSymMark() {
		Map<String, String> filter = new HashMap<>();
		filter.put("'", "'");
		assertArrayEquals(new String[] { "a", "'b,c'", "d" }, StringUtil.splitExcludeSymMark("a,'b,c',d", ",", filter));
		// 括号场景
		Map<String, String> bracket = new HashMap<>();
		bracket.put("(", ")");
		assertArrayEquals(new String[] { "a", "b", "dd(a,c)", "dd(a,c)" },
				StringUtil.splitExcludeSymMark("a,b,dd(a,c),dd(a,c)", ",", bracket));
		// 无分隔符
		assertArrayEquals(new String[] { "abc" }, StringUtil.splitExcludeSymMark("abc", ",", bracket));
		assertNull(StringUtil.splitExcludeSymMark(null, ",", bracket));
		// 嵌套同名括号:整体处于外层括号内部,不应被切开
		assertArrayEquals(new String[] { "((a,b),c)" },
				StringUtil.splitExcludeSymMark("((a,b),c)", ",", bracket));
	}

	@Test
	public void splitExcludeSymMarkAdjacentQuotes() {
		Map<String, String> filter = new HashMap<>();
		filter.put("'", "'");
		filter.put("\"", "\"");
		// 相邻成对引号(''空字面量)内部的分隔符不参与切割,分隔符在其后正常切割
		assertArrayEquals(new String[] { "a", "''", "b" }, StringUtil.splitExcludeSymMark("a,'',b", ",", filter));
		// 双引号相邻成对(""转义)场景:中间的逗号处于符号对内部不切割
		assertArrayEquals(new String[] { "a", "\"\"\",\"", "a" }, StringUtil.splitExcludeSymMark("a,\"\"\",\",a", ",", filter));
		// getSymMarkIndex对空字面量''返回相邻终结位
		assertEquals(1, StringUtil.getSymMarkIndex("'", "'", "''x", 0));
	}

	@Test
	public void matchFiltersOnlyValidPairs() {
		Map<String, String> filterMap = new HashMap<>();
		filterMap.put("(", ")");
		// 引号对只有开始没有结束,应被排除
		filterMap.put("'", "'");
		List<String[]> result = StringUtil.matchFilters("a,'b,(c)", filterMap);
		assertEquals(1, result.size());
		assertEquals("(", result.get(0)[0]);
	}

	@Test
	public void toHumpStr() {
		assertEquals("organInfo", StringUtil.toHumpStr("ORGAN_INFO", false));
		assertEquals("OrganInfo", StringUtil.toHumpStr("organ_info", true));
		// 横杠统一按下划线处理
		assertEquals("organInfo", StringUtil.toHumpStr("organ-info", false));
		// 不移除下划线
		assertEquals("organ_Info", StringUtil.toHumpStr("organ_info", false, false));
		// 混合大小写单段输入(前导大写运行):仅首字母转小写,运行段其余大写保留
		// FIXME 行为记录:如需organInfo风格需入参方自行处理
		assertEquals("oRGANInfo", StringUtil.toHumpStr("ORGANInfo", false));
		assertNull(StringUtil.toHumpStr(null, false));
		assertEquals("", StringUtil.toHumpStr("", false));
	}

	@Test
	public void humpToSplitStr() {
		assertEquals("organ_Info", StringUtil.humpToSplitStr("organInfo", "_"));
		// 前导连续大写运行段(ORGAN+Info)整体算一个运行,运行首字符位于0位不切分
		assertEquals("ORGANInfo", StringUtil.humpToSplitStr("ORGANInfo", "_"));
		assertNull(StringUtil.humpToSplitStr(null, "_"));
		// 驼峰标准形式:每个新的大写运行前插入分隔符(O是首个运行在0位不切,I是第二个运行切分)
		assertEquals("Organ_Info", StringUtil.humpToSplitStr("OrganInfo", "_"));
	}

	@Test
	public void humpFieldNames() {
		String[] result = StringUtil.humpFieldNames(new String[] { "STAFF_NAME", "count:ORDER_CNT", null });
		assertArrayEquals(new String[] { "staffName", "orderCnt", null }, result);
		assertNull(StringUtil.humpFieldNames(null));
	}

	@Test
	public void fillArgs() {
		assertEquals("a=1,b=x", StringUtil.fillArgs("a=${},b=${}", 1, "x"));
		assertEquals("a=null", StringUtil.fillArgs("a=${}", (Object) null));
		// 占位符多于参数,剩余占位符保留
		assertEquals("1,${}", StringUtil.fillArgs("${},${}", 1));
		// 替换值中含特殊正则字符不会被误解析
		assertEquals("a$b", StringUtil.fillArgs("${}", "a$b"));
		// 无参数时原样返回
		assertEquals("abc", StringUtil.fillArgs("abc"));
	}

	@Test
	public void replaceAllStr() {
		assertEquals("a-b-c", StringUtil.replaceAllStr("a,b,c", ",", "-"));
		// fromIndex/endIndex边界
		assertEquals("a,b-c", StringUtil.replaceAllStr("a,b,c", ",", "-", 3));
		// fromIndex大于endIndex返回原串
		assertEquals("a,b,c", StringUtil.replaceAllStr("a,b,c", ",", "-", 2, 0));
		// 替换为更长字符串不死循环
		assertEquals("a--b", StringUtil.replaceAllStr("a-b", "-", "--"));
		assertNull(StringUtil.replaceAllStr(null, "a", "b"));
		// template与target相同原样返回
		assertEquals("a,a", StringUtil.replaceAllStr("a,a", ",", ","));
	}

	@Test
	public void replaceFirstStr() {
		assertEquals("a-b,c", StringUtil.replaceFirstStr("a,b,c", ",", "-"));
		assertEquals("a,b-c", StringUtil.replaceFirstStr("a,b,c", ",", "-", 3));
		// target为空串或null原样返回
		assertEquals("abc", StringUtil.replaceFirstStr("abc", "", "-"));
		assertEquals("abc", StringUtil.replaceFirstStr("abc", null, "-"));
		// replacement为null按空串处理(删除目标串)
		assertEquals("ab,c", StringUtil.replaceFirstStr("a,b,c", ",", null, 1));
	}

	@Test
	public void matchCntAndIndex() {
		assertEquals(3, StringUtil.matchCnt("a,b,c,d", ","));
		// 重叠匹配
		assertEquals(3, StringUtil.matchCnt("aaa", Pattern.compile("a"), 1));
		assertEquals(0, StringUtil.matchCnt(null, "a"));
		assertEquals(0, StringUtil.matchCnt("abc", "z"));
		// 指定范围
		assertEquals(1, StringUtil.matchCnt("a,b,c", ",", 3, 5));
		// matchLastIndex
		assertEquals(5, StringUtil.matchLastIndex("a,b,c,d", ","));
		assertEquals(-1, StringUtil.matchLastIndex("abc", ","));
	}

	@Test
	public void indexOrder() {
		assertEquals(1, StringUtil.indexOrder("a,b,a", ",", 0));
		assertEquals(3, StringUtil.indexOrder("a,b,a", ",", 1));
		assertEquals(-1, StringUtil.indexOrder("a,b,a", ",", 2));
		assertEquals(-1, StringUtil.indexOrder(null, ",", 0));
	}

	@Test
	public void like() {
		String[] keys = { "select", "from" };
		assertTrue(StringUtil.like("select * from t", keys));
		assertFalse(StringUtil.like("select * t", keys));
		// 顺序敏感
		assertFalse(StringUtil.like("from t select", keys));
		assertFalse(StringUtil.like(null, keys));
		assertFalse(StringUtil.like("abc", new String[0]));
	}

	@Test
	public void splitRegexCommonSigns() {
		assertArrayEquals(new String[] { "a", "b" }, StringUtil.splitRegex("a.b", ".", true));
		assertArrayEquals(new String[] { "a", "b" }, StringUtil.splitRegex("a|b", "|", true));
		assertArrayEquals(new String[] { "a", "b" }, StringUtil.splitRegex("a?b", "?", true));
		assertArrayEquals(new String[] { "a", "b" }, StringUtil.splitRegex("a||b", "||", true));
		// 连续空白切割
		assertArrayEquals(new String[] { "a", "b" }, StringUtil.splitRegex("a  b", " ", true));
		assertNull(StringUtil.splitRegex(null, ",", true));
	}

	@Test
	public void splitByIndex() {
		assertArrayEquals(new String[] { "a", "b", "c" }, StringUtil.splitByIndex("a->b->c", "->"));
		assertArrayEquals(new String[] { "a", "b", "c" }, StringUtil.splitByIndex("a->b->c", "->", true));
		// 分隔符连续出现,产生空段
		assertArrayEquals(new String[] { "a", "", "c" }, StringUtil.splitByIndex("a->->c", "->"));
		assertArrayEquals(new String[] {}, StringUtil.splitByIndex("", ","));
		// 分隔符为null按原串返回
		assertArrayEquals(new String[] { "abc" }, StringUtil.splitByIndex("abc", null));
		// 结尾分隔符产生空尾段
		assertArrayEquals(new String[] { "a", "" }, StringUtil.splitByIndex("a,", ","));
	}

	@Test
	public void secureMask() {
		assertEquals("ab***yz", StringUtil.secureMask("abcdefghyz", 2, 2, "***"));
		// 长度不足时原样返回
		assertEquals("abc", StringUtil.secureMask("abc", 2, 2, "***"));
		assertNull(StringUtil.secureMask(null, 2, 2, "***"));
		// maskStr为空默认***
		assertEquals("a***z", StringUtil.secureMask("abcz", 1, 1, ""));
	}

	@Test
	public void maskByRate() {
		String masked = StringUtil.maskByRate("1234567890", "*", 50);
		assertEquals(10, masked.length());
		// 脱敏字符数量应为5个
		assertEquals(5, StringUtil.matchCnt(masked, "\\*"));
		// 100%全脱敏
		assertEquals("*****", StringUtil.maskByRate("abcde", "*", 100));
		// 0%或负数原样返回
		assertEquals("abcde", StringUtil.maskByRate("abcde", "*", 0));
		assertEquals("abcde", StringUtil.maskByRate("abcde", "*", -10));
		assertNull(StringUtil.maskByRate(null, "*", 50));
		assertEquals("abc", StringUtil.maskByRate("abc", null, 50));
	}

	@Test
	public void removeStartEndQuote() {
		assertEquals("abc", StringUtil.removeStartEndQuote("'abc'"));
		assertEquals("abc", StringUtil.removeStartEndQuote("\"abc\""));
		// 不成对原样返回
		assertEquals("'abc", StringUtil.removeStartEndQuote("'abc"));
		assertEquals("abc'", StringUtil.removeStartEndQuote("abc'"));
		// 长度小于2原样返回
		assertEquals("'", StringUtil.removeStartEndQuote("'"));
		assertNull(StringUtil.removeStartEndQuote(null));
	}

	@Test
	public void linkAry() {
		assertEquals("a,b", StringUtil.linkAry(",", true, "a", "b"));
		// skipNull=false时null以字符串形式参与
		assertEquals("a,null,b", StringUtil.linkAry(",", false, "a", null, "b"));
		assertEquals("a,b", StringUtil.linkAry(",", true, "a", null, "b"));
		assertEquals("", StringUtil.linkAry(",", true));
		// sign为null默认逗号
		assertEquals("a,b", StringUtil.linkAry(null, true, "a", "b"));
	}

	@Test
	public void escapeComment() {
		assertEquals("a\\\\b", StringUtil.escapeComment("a\\b"));
		// 已转义的保持原样不重复转义
		assertEquals("a\\\\b", StringUtil.escapeComment("a\\\\b"));
		// 双引号转义
		assertEquals("a\\\"b", StringUtil.escapeComment("a\"b"));
		assertNull(StringUtil.escapeComment(null));
		assertEquals("", StringUtil.escapeComment(""));
		// 结尾孤立反斜杠
		assertEquals("a\\\\", StringUtil.escapeComment("a\\"));
	}

	@Test
	public void trimArray() {
		assertArrayEquals(new String[] { "a", "b" }, StringUtil.trimArray(new String[] { " a ", "b" }));
		// 数组内元素为null保持null
		String[] result = StringUtil.trimArray(new String[] { null, "b" });
		assertNull(result[0]);
		assertEquals("b", result[1]);
		assertNull(StringUtil.trimArray(null));
	}

	@Test
	public void ifBlank() {
		assertEquals("dft", StringUtil.ifBlank(" ", "dft"));
		assertEquals("dft", StringUtil.ifBlank(null, "dft"));
		assertEquals("v", StringUtil.ifBlank("v", "dft"));
	}

	@Test
	public void matchPatternAndRegex() {
		// matches用find语义
		assertTrue(StringUtil.matches("abc123", "[0-9]+"));
		assertFalse(StringUtil.matches("abc", "[0-9]+"));
		assertFalse(StringUtil.matches("  ", "[0-9]+"));
		assertFalse(StringUtil.matches("abc", (String) null));
		// matchIndex
		assertEquals(3, StringUtil.matchIndex("abc123", Pattern.compile("[0-9]+")));
		assertEquals(-1, StringUtil.matchIndex(null, Pattern.compile("a")));
	}

	@Test
	public void toDBC() {
		assertEquals(";?.:'\",[]()=", StringUtil.toDBC("；？．：＇＂，【】（）＝"));
		assertEquals("a", StringUtil.toDBC("a"));
		assertEquals("", StringUtil.toDBC(""));
		assertNull(StringUtil.toDBC(null));
	}

	@Test
	public void replaceRegex() {
		Pattern p = Pattern.compile("\\d+");
		// 替换第2次匹配
		assertEquals("a1-bX-c123", StringUtil.replaceRegex("a1-b2-c123", p, "X", 2, 0));
		// 匹配次数不足原样返回
		assertEquals("a1", StringUtil.replaceRegex("a1", p, "X", 5, 0));
		assertNull(StringUtil.replaceRegex(null, p, "X", 1, 0));
	}

	@Test
	public void str2ASCII() {
		assertArrayEquals(new int[] { 97, 98 }, StringUtil.str2ASCII("ab"));
		assertEquals(0, StringUtil.str2ASCII(null).length);
	}

	@Test
	public void getSymMarkReverseIndex() {
		String sql = "select * from t where a in (1,2) and b in (3,4)";
		// 从末尾")"逆向找对应的"("位置
		int idx = StringUtil.getSymMarkReverseIndex("(", ")", sql, sql.length());
		assertEquals(sql.lastIndexOf("("), idx);
	}

	@Test
	public void clearSymMarkContent() {
		assertEquals("select  from t", StringUtil.clearSymMarkContent("select (a,b) from t", "(", ")"));
		// 引号内括号对同样被区间删除(从(到)整段移除,前一个引号保留)
		String sql = "select '(" + ")" + "' from t";
		assertEquals("select '' from t", StringUtil.clearSymMarkContent(sql, "(", ")"));
		assertNull(StringUtil.clearSymMarkContent(null, "(", ")"));
	}

	@Test
	public void getSymMarkMatchIndex() {
		String sql = "case when a then b end from t";
		assertEquals(sql.indexOf("end"), StringUtil.getSymMarkMatchIndex("case", "end", sql, 0));
	}

	@Test
	public void toLowerOrUpper() {
		assertEquals("ABC", StringUtil.toLowerOrUpper("abc", "upper"));
		assertEquals("abc", StringUtil.toLowerOrUpper("ABC", "lower"));
		// 指令不识别时原样返回
		assertEquals("abc", StringUtil.toLowerOrUpper("abc", "other"));
		assertNull(StringUtil.toLowerOrUpper(null, "upper"));
	}
}
