/**
 * 
 */
package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.config.SqlConfigParseUtils;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

import com.alibaba.fastjson2.JSON;

/**
 * @author zhongxuchen
 *
 */
public class StringUtilsTest {
	public final static Pattern EQUAL = Pattern.compile("[^\\>\\<\\!\\:]\\=\\s*$");
	public final static Pattern NOT_EQUAL = Pattern.compile("(\\!\\=|\\<\\>|\\^\\=)\\s*$");

	@Test
	public void testSplitExcludeSymMark1() {
		String source = "#[testNum],'#,#0.00'";
		String[] result = StringUtil.splitExcludeSymMark(source, ",", SqlToyConstants.filters);
		assertArrayEquals(result, new String[] { "#[testNum]", "'#,#0.00'" });
		source = ",'#,#0.00'";
		result = StringUtil.splitExcludeSymMark(source, ",", SqlToyConstants.filters);
		assertArrayEquals(result, new String[] { "", "'#,#0.00'" });

		source = "'\\'', t.`ORGAN_ID`, '\\''";
		result = StringUtil.splitExcludeSymMark(source, ",", SqlToyConstants.filters);
		assertArrayEquals(result, new String[] { "'\\''", " t.`ORGAN_ID`", " '\\''" });

		source = "orderNo,<td align=\"center\" rowspan=\"#[group('orderNo,').size()]\">,@dict(EC_PAY_TYPE,#[payType])</td>";
		result = StringUtil.splitExcludeSymMark(source, ",", SqlToyConstants.filters);
		assertArrayEquals(result,
				new String[] { "orderNo", "<td align=\"center\" rowspan=\"#[group('orderNo,').size()]\">",
						"@dict(EC_PAY_TYPE,#[payType])</td>" });
		source = "reportId=\"RPT_DEMO_005\",chart-index=\"1\",style=\"width:49%;height:350px;display:inline-block;\"";
		result = StringUtil.splitExcludeSymMark(source, ",", SqlToyConstants.filters);
		assertArrayEquals(result, new String[] { "reportId=\"RPT_DEMO_005\"", "chart-index=\"1\"",
				"style=\"width:49%;height:350px;display:inline-block;\"" });
		source = "a,\"\"\",\",a";
		result = StringUtil.splitExcludeSymMark(source, ",", SqlToyConstants.filters);
		for (String s : result) {
			System.err.println("[" + s.trim() + "]");
		}
		assertArrayEquals(result, new String[] { "a", "\"\"\",\"", "a" });
	}
	
	@Test
	public void testRegex() {
		String temp = "{Key}";
		String result = temp.replaceAll("(?i)\\$?\\{\\s*key\\s*\\}", "\\$\\{value\\}");
		System.err.println(result);
		System.err.println(result.replace("${value}", "chenren"));
	}

	@Test
	public void testMatchForUpdate() {
		String sql = "selec * from table ";
		System.err.println(SqlUtil.hasLock(sql.concat(" "), DBType.MYSQL));
		System.err.println(SqlUtil.hasLock(sql.concat(" for update"), DBType.MYSQL));
		System.err.println(SqlUtil.hasLock(sql.concat(" for update"), DBType.SQLSERVER));
		System.err.println(SqlUtil.hasLock(sql.concat(" with(rowlock xlock)"), DBType.MYSQL));
		System.err.println(SqlUtil.hasLock(sql.concat(" with(rowlock xlock)"), DBType.SQLSERVER));
		String sql1 = "select * from table with ";
		String regex = "(?i)with\\s*\\(\\s*(rowlock|xlock|updlock|holdlock)?\\,?\\s*(rowlock|xlock|updlock|holdlock)\\s*\\)";
		System.err.println(StringUtil.matches(sql1.concat("(rowlock xlock)"), regex));
		System.err.println(StringUtil.matches(sql1.concat("(rowlock,xlock)"), regex));
		System.err.println(StringUtil.matches(sql1.concat("(rowlock,updlock)"), regex));
		System.err.println(StringUtil.matches(sql1.concat("(rowlock updlock)"), regex));
		System.err.println(StringUtil.matches(sql1.concat("(holdlock updlock)"), regex));
		System.err.println(StringUtil.matches(sql1.concat("(holdlock)"), regex));
	}

	@Test
	public void testLike() {
		String[] ary = "   a   b  c d".trim().split("\\s+");
		for (int i = 0; i < ary.length; i++) {
			System.err.println("[" + ary[i] + "]");
		}
		String sql = "支持保留字处理，对象操作自动增加保留字符号，跨数据库sql自动适配";
		System.err.println(StringUtil.like(sql, "数据库".split("\\s+")));
		System.err.println(StringUtil.like(sql, "保留  操作  ，跨数库".split("\\s+")));
		System.err.println(StringUtil.like(sql, "保留  操作  ， 数据库".split("\\s+")));

	}

	@Test
	public void testMatch() {
		String sqlLow = "from t where1 (1=1)";
		String sql = "select 1 from";
		String sqlWith = "with t as () * from";
		System.err.println(StringUtil.matches(sqlLow, "^\\s*where\\W"));
		System.err.println(StringUtil.matches(sqlLow, "^from\\W"));
		System.err.println(StringUtil.matches(sql, "^(select|with)\\W"));
		System.err.println(StringUtil.matches(sqlWith, "^(select|with)\\W"));
		String sequence = "SEQ_${tableName}";
		System.err.println(sequence.replaceFirst("(?i)\\$\\{tableName\\}", "staff_info"));
		System.err.println(sequence.replaceFirst("(?i)\\$?\\{tableName\\}", "staff_info"));
		System.err.println("A_B_C_D".replace("_", ""));

	}

	@Test
	public void testWhereMatch() {
		Pattern WHERE_CLOSE_PATTERN = Pattern
				.compile("^((order|group)\\s+by|(inner|left|right|full)\\s+join|having|union)\\W");
		System.err.println(StringUtil.matches("inner join ", WHERE_CLOSE_PATTERN));

	}

	@Test
	public void testLineMaskMatch() {
		String sql = "select 'a',\"b\",/**/ from table -- 备注";
		int lastIndex = StringUtil.matchLastIndex(sql, "\'|\"|\\*\\/");
		int lineMaskIndex = sql.indexOf("--");
		System.err.println("lastIndex=" + lastIndex + "lineMaskIndex=" + lineMaskIndex);

	}

	@Test
	public void testWhereMatch1() {

		System.err.println(StringUtil.matches("name=", EQUAL));
		System.err.println(StringUtil.matches("name:=", EQUAL));
		System.err.println(StringUtil.matches("name!=", EQUAL));
		System.err.println(StringUtil.matches("name<=", EQUAL));
		System.err.println(StringUtil.matches("name>=", EQUAL));
		System.err.println(StringUtil.matches("name=", NOT_EQUAL));
		System.err.println(StringUtil.matches("name !=", NOT_EQUAL));
		System.err.println(StringUtil.matches("name != ", NOT_EQUAL));
		System.err.println(StringUtil.matches("name <> ", NOT_EQUAL));
		System.err.println(StringUtil.matches("name <> 1", NOT_EQUAL));
		System.err.println(StringUtil.matches("name>=", NOT_EQUAL));
		System.err.println(StringUtil.matches("name^=", NOT_EQUAL));
	}

	@Test
	public void testWhereMatch2() {
		String packageName = "/com/sagframe/xdata/";
		if (packageName.charAt(0) == '/') {
			packageName = packageName.substring(1);
		}
		if (packageName.endsWith("/")) {
			packageName = packageName.substring(0, packageName.length() - 1);
		}
		packageName = packageName.replace("/", ".");
		String sql = "where t.\"tenant_id\" in (?) and id=?";
		String sql1 = "where t.'tenant_id' = ? and id=?";
		String tenantColumn = "\"TENANT_ID\"";
		System.err.println(packageName);
		// 已经有租户条件过滤，无需做处理
		System.err.println(StringUtil.matches(sql, "(?i)\\W" + tenantColumn + "(\\s*\\=|\\s+in)"));
		System.err.println(StringUtil.matches(sql1, "(?i)\\W" + tenantColumn + "(\\s*\\=|\\s+in)"));
	}

	@Test
	public void testReplace() {
		String VALUE_REGEX = "(?i)\\@value\\s*\\(\\s*(\\?|null)\\s*\\)";
		String sql = "where @value(?)";
		String materValue = "$test";
		String result = sql.replaceFirst(VALUE_REGEX, Matcher.quoteReplacement(materValue));
		System.err.println(result);
	}

	@Test
	public void testReplace1() {
		ConcurrentHashMap<String, Object> sqlCache = new ConcurrentHashMap<String, Object>(256);
		sqlCache.put("1", 1);
		Map result = (Map) sqlCache;
		System.err.println(JSON.toJSONString(result));
	}

	@Test
	public void testReplaceAllStr() {
//		System.err.println(StringUtil.replaceAllStr("addcdChen8888Chen9000", "Chen", "陈", 5, 13));
//		System.err.println(StringUtil.replaceAllStr("addcdChen8888Chen9000", "Chen", "陈", 5));
//		System.err.println(StringUtil.replaceAllStr("addcdChen8888Chen9000", "Chen", "陈"));
		System.err.println(StringUtil.replaceAllStr("addcdChen8888Chen9000", "Chen", ""));
	}

	@Test
	public void testReplace2() throws UnsupportedEncodingException {
		String argValue = "/D:/personal/sqltoy/sqltoy%20&+quick中start/target/classes/com/sqltoy/quickstart/sql/sqltoy-quickstart.sql.xml";
		System.err.println(URLDecoder.decode(argValue, "GBK"));

	}

	@Test
	public void testIfMatch() throws UnsupportedEncodingException {
		Pattern IF_PATTERN = Pattern.compile("(?i)\\@if\\s*\\(");
		Pattern ELSEIF_PATTERN = Pattern.compile("(?i)\\@elseif\\s*\\(");
		Pattern ELSE_PATTERN = Pattern.compile("(?i)\\@else(\\s+|\\s*\\(\\s*\\))");
		Pattern IF_ALL_PATTERN = Pattern.compile("(?i)\\@((if|elseif)\\s*\\(|else(\\s+|\\s*\\(\\s*\\)))");
		System.err.println(StringUtil.matches("@else(and ", IF_ALL_PATTERN));
		System.err.println(StringUtil.matches("@else and ", IF_ALL_PATTERN));
		System.err.println(StringUtil.matches("@elseif and ", IF_ALL_PATTERN));
		System.err.println(StringUtil.matches("@elseif(:a==1) and ", IF_ALL_PATTERN));
		System.err.println(StringUtil.matches("@if(:a==1) and ", IF_ALL_PATTERN));
		System.err.println(StringUtil.matches("@else(:a==1) and ", IF_ALL_PATTERN));
		System.err.println(StringUtil.matches("@else() and ", IF_ALL_PATTERN));

	}

	@Test
	public void testMatchInclude() {
		Map<String, Object> sqlCache = new HashMap<>();
		System.err.println((SqlToyConfig) sqlCache.get("test_id"));
		String sql = "select * from table @include(id=\"adb\")";
		System.err.println(StringUtil.matches(sql, SqlToyConstants.INCLUDE_PATTERN));
		sql = "select * from table @include( :itemList[0].id )";
		System.err.println(StringUtil.matches(sql, SqlToyConstants.INCLUDE_PARAM_PATTERN));
		sql = "select * from table @include( :itemList )";
		System.err.println(StringUtil.matches(sql, SqlToyConstants.INCLUDE_PARAM_PATTERN));
		String tmp = "select from ";
		if (StringUtil.matches(" " + tmp, "(?i)\\Wselect\\W") && StringUtil.matches(tmp, "(?i)\\Wfrom\\W")) {
			System.err.println("ddd" + true);
		}
	}

	@Test
	public void testClearSymMark() {
		String sql = "select * from table #[and field=:field]  #[]#[]and t1=t1";
		System.err.println(StringUtil.clearSymMarkContent(sql, "#[", "]"));
	}

	@Test
	public void testMatchCount() {
		String sql = "select * from table #[[[]]]";
		System.err.println(StringUtil.matchCnt(sql, Pattern.compile("\\["), 0));
	}

	@Test
	public void testMatchIndex() {
		String sql = "select * from table #[[";
		int[] indexes = StringUtil.matchIndex(sql, Pattern.compile("\\#\\["), 0);
		System.err.println("firstIndex=" + indexes[0]);
		System.err.println("firstEnd=" + indexes[1]);
		System.err.println("nextStart=" + StringUtil.matchIndex(sql, Pattern.compile("\\["), indexes[1])[0]);
		System.err.println("nextEnd=" + StringUtil.matchIndex(sql, Pattern.compile("\\["), indexes[1])[1]);
	}

	@Test
	public void testMatchReplaceRegex() {
		String[] fields = new String[] { "t.name desc" };
		System.err.println(!StringUtil.matches(fields[0].trim(), "\\s+"));
		String sql = "select * from @value(?) and @value(null) t.name like 'df'";
		System.err.println(StringUtil.replaceRegex(sql, SqlConfigParseUtils.VALUE_PATTERN, "1=1", 2, 6));
	}

	@Test
	public void testSql() {
		String sql = "select * from table where (id,type) in (:ids,:type)";
		Pattern pattern;
		Matcher matcher;
		String group;
		boolean hasMatched = false;
		StringBuffer result = new StringBuffer();
		String[] fields = { "id", "type" };
		for (int i = 0; i < 2; i++) {
			hasMatched = false;
			pattern = Pattern.compile("(?i)\\:" + fields[i] + "\\W");
			matcher = pattern.matcher(sql);
			while (matcher.find()) {
				hasMatched = true;
				group = matcher.group();
				matcher.appendReplacement(result, "?" + group.substring(group.length() - 1));
			}
			if (hasMatched) {
				matcher.appendTail(result);
				sql = result.toString();
				result.delete(0, result.length());
			}
		}

		System.err.println(sql);
	}

	@Test
	public void testSplitByIndex() {
		String tmpStr = "select * from ->table where-> (id,type) in-> (:ids,:type)";
		String[] result = StringUtil.splitByIndex(tmpStr, "->", false);

		for (String str : result) {
			System.err.println("[" + str + "]");
		}
	}

	@Test
	public void testEscopeComment() {
		String tmp = "\\\\$\\{{datetime}} {{product-RGB}}";
		System.err.println(StringUtil.escapeComment(tmp));
	}

	@Test
	public void testTrimArray() {
		String[] tmp = { " dd ", "ffed ", "abc" };
		String[] result = StringUtil.trimArray(tmp);
		for (String str : result) {
			System.err.println("[" + str + "]");
		}
	}

	@Test
	public void loopAppendWithSign() {
		String result = StringUtil.loopAppendWithSign("?", ",", 10);
		System.err.println("[" + result + "]");
		result = StringUtil.loopAppendWithSign("?", ",", 1);
		System.err.println("[" + result + "]");
	}

	@Test
	public void loopAppendWithSign1() {
//		String reg = "(\\d{2,4})-(\\d)(?=-)-(\\d)(?=\\s)";
//
//        System.out.println("26-6-1 18:20:00".replaceAll(reg, "$1-0$2-0$3"));    // 26-06-01 18:20:00
//        System.out.println("2026-6-1 18:20:00".replaceAll(reg, "$1-0$2-0$3"));  // 2026-06-01 18:20:00
//        System.out.println("26-12-05 09:00:00".replaceAll(reg, "$1-0$2-0$3"));  // 26-12-05 09:00:00
//        System.out.println("2026-12-05 09:00:00".replaceAll(reg, "$1-0$2-0$3"));// 2026-12-05 09:00:00
//        System.out.println("09:00:00".replaceAll(reg, "$1-0$2-0$3"));// 2026-12-05 09:00:00
//        
//        System.err.println(DateUtil.parseString("20231130112031033456789"));
//		System.err.println(DateUtil.parseString("20231130112031033456"));
//		System.err.println(DateUtil.parseString("20231130112031033"));
		// System.err.println(DateUtil.parseString("202311301120311"));
		System.err.println(DateUtil.parseLocalDateTime("2023-1-1 123030.123345321"));
		// System.err.println(DateUtil.parseString("2023-06-22 12:22:11"));
		// LocalDateTime dateValue = LocalDateTime.parse("2023-11-29T20:23:23.123456");
		// System.err.println(DateUtil.formatDate(dateValue, "yyyy-MM-dd
		// HH:mm:ss.SSSSSSSSS"));

	}

	@Test
	public void testMaskByRate() {
		String str = "HelloWorld";
		// 30% → 3个字符被脱敏
		String result = StringUtil.maskByRate(str, "*", 30);
		System.err.println("30%: " + str + " -> " + result);
		assertEquals(str.length(), result.length());
		assertEquals(3, countChar(result, '*'));
		assertTrue(result.contains("*"));
		// 确保脱敏字符不是连续的（离散性验证）
		assertTrue(isDispersed(result, '*'), "脱敏字符应离散分布，非连续块");

		// 50% → 5个字符被脱敏
		result = StringUtil.maskByRate(str, "#", 50);
		System.err.println("50%: " + str + " -> " + result);
		assertEquals(5, countChar(result, '#'));

		// 100% → 全部脱敏
		result = StringUtil.maskByRate(str, "*", 100);
		System.err.println("100%: " + str + " -> " + result);
		assertEquals("**********", result);

		// 10% → 1个字符被脱敏
		result = StringUtil.maskByRate(str, "*", 10);
		System.err.println("10%: " + str + " -> " + result);
		assertEquals(1, countChar(result, '*'));
	}

	@Test
	public void testMaskByRateNullAndEmpty() {
		assertNull(StringUtil.maskByRate(null, "*", 50));
		assertEquals("", StringUtil.maskByRate("", "*", 50));
	}

	@Test
	public void testMaskByRateInvalidMaskCode() {
		String str = "HelloWorld";
		assertEquals(str, StringUtil.maskByRate(str, null, 50));
		assertEquals(str, StringUtil.maskByRate(str, "", 50));
	}

	@Test
	public void testMaskByRateInvalidRate() {
		String str = "HelloWorld";
		assertEquals(str, StringUtil.maskByRate(str, "*", 0));
		assertEquals(str, StringUtil.maskByRate(str, "*", -1));
	}

	@Test
	public void testMaskByRateSingleChar() {
		assertEquals("*", StringUtil.maskByRate("a", "*", 50));
		assertEquals("*", StringUtil.maskByRate("a", "*", 100));
	}

	@Test
	public void testMaskByRateRateOver100() {
		String str = "HelloWorld";
		String result = StringUtil.maskByRate(str, "*", 200);
		System.err.println("200%: " + str + " -> " + result);
		assertEquals("**********", result);
	}

	@Test
	public void testMaskByRateMultiByteMaskCode() {
		String str = "HelloWorld";
		// maskCode取首字符
		String result = StringUtil.maskByRate(str, "XY", 30);
		System.err.println("multi-byte maskCode: " + str + " -> " + result);
		assertEquals(3, countChar(result, 'X'));
	}

	@Test
	public void testMaskByRateChineseString() {
		String str = "你好世界测试字符串";
		int length = str.length();
		String result = StringUtil.maskByRate(str, "*", 40);
		System.err.println("中文40%: " + str + " -> " + result);
		assertEquals(length, result.length());
		int expected = (int) Math.ceil(length * 0.4);
		assertEquals(expected, countChar(result, '*'));
		assertTrue(isDispersed(result, '*'), "中文脱敏字符应离散分布");
	}

	@Test
	public void testMaskByRateLongString() {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 100; i++) {
			sb.append((char) ('A' + (i % 26)));
		}
		String str = sb.toString();
		String result = StringUtil.maskByRate(str, "*", 25);
		System.err.println("100字符25%: " + str + " -> " + result);
		assertEquals(100, result.length());
		assertEquals(25, countChar(result, '*'));
		assertTrue(isDispersed(result, '*'), "长字符串脱敏字符应离散分布");
	}

	@Test
	public void testMaskByRateOnePercent() {
		String str = "abcdefghijklmnop"; // 16 chars, 1% → ceil(0.16) = 1
		String result = StringUtil.maskByRate(str, "*", 1);
		System.err.println("1%: " + str + " -> " + result);
		assertEquals(1, countChar(result, '*'));
	}

	@Test
	public void testMaskByRateMaskCountAccuracy() {
		// 多种比例验证脱敏数量精确性
		String str = "abcdefghij"; // 10 chars
		for (int rate = 1; rate <= 100; rate++) {
			String result = StringUtil.maskByRate(str, "*", rate);
			int expected = Math.min((int) Math.ceil(str.length() * rate / 100.0), str.length());
			int actual = countChar(result, '*');
			if (actual != expected) {
				System.err.println("FAILED rate=" + rate + " expected=" + expected + " actual=" + actual + " result="
						+ result);
			}
			assertEquals(expected, actual, "rate=" + rate + " 脱敏数量不正确");
		}
	}

	@Test
	public void testMaskByRateDispersed() {
		// 验证脱敏字符不会形成连续的大块（离散性核心验证）
		String str = "0123456789ABCDEFGHIJ"; // 20 chars
		String result = StringUtil.maskByRate(str, "*", 30);
		System.err.println("离散验证30%: " + str + " -> " + result);
		assertEquals(6, countChar(result, '*'));
		assertTrue(isDispersed(result, '*'), "脱敏字符应离散分布");

		// 对比：旧的连续脱敏算法会产生"0123****************"这样的连续块
		// 新算法应该将*分散开
		int maxConsecutive = maxConsecutiveChar(result, '*');
		System.err.println("最大连续脱敏字符数: " + maxConsecutive);
		assertTrue(maxConsecutive <= 3, "离散脱敏不应出现超过3个连续脱敏字符");
	}

	@Test
	public void testMaskByRatePositionDistributed() {
		// 验证脱敏字符分布在整个字符串范围内（头、中、尾都有）
		String str = "0123456789012345678901234567890123456789"; // 40 chars
		String result = StringUtil.maskByRate(str, "*", 50);
		System.err.println("分布验证50%: " + str + " -> " + result);
		assertEquals(20, countChar(result, '*'));

		// 前1/4区域是否有脱敏字符
		boolean hasInHead = result.substring(0, 10).contains("*");
		// 中间区域是否有脱敏字符
		boolean hasInMiddle = result.substring(15, 25).contains("*");
		// 后1/4区域是否有脱敏字符
		boolean hasInTail = result.substring(30, 40).contains("*");
		assertTrue(hasInHead, "头部区域应有脱敏字符");
		assertTrue(hasInMiddle, "中间区域应有脱敏字符");
		assertTrue(hasInTail, "尾部区域应有脱敏字符");
	}

	@Test
	public void testMaskByRateUnmaskedCharsPreserved() {
		// 验证未被脱敏的字符保持原样
		String str = "HelloWorld";
		String result = StringUtil.maskByRate(str, "*", 30);
		System.err.println("保留验证: " + str + " -> " + result);
		for (int i = 0; i < str.length(); i++) {
			if (result.charAt(i) != '*') {
				assertEquals(str.charAt(i), result.charAt(i), "位置" + i + "的字符应保持原样");
			}
		}
	}

	/**
	 * 统计字符出现次数
	 */
	private int countChar(String str, char c) {
		int count = 0;
		for (int i = 0; i < str.length(); i++) {
			if (str.charAt(i) == c) {
				count++;
			}
		}
		return count;
	}

	/**
	 * 判断脱敏字符是否离散分布（不全部连续）
	 */
	private boolean isDispersed(String str, char maskChar) {
		int maskCount = countChar(str, maskChar);
		if (maskCount <= 1) {
			return true;
		}
		// 如果所有脱敏字符都连续在一起，则不是离散的
		return maxConsecutiveChar(str, maskChar) < maskCount;
	}

	/**
	 * 获取字符的最大连续出现次数
	 */
	private int maxConsecutiveChar(String str, char c) {
		int max = 0;
		int current = 0;
		for (int i = 0; i < str.length(); i++) {
			if (str.charAt(i) == c) {
				current++;
				if (current > max) {
					max = current;
				}
			} else {
				current = 0;
			}
		}
		return max;
	}

	@Test
	@org.junit.jupiter.api.Timeout(2)
	public void testMatchCntOffsetInfiniteLoop() {
		assertEquals(2, StringUtil.matchCnt("aaa", Pattern.compile("aa"), 2));
	}

	@Test
	@org.junit.jupiter.api.Timeout(2)
	public void testMatchLastIndexOffsetInfiniteLoop() {
		assertTrue(StringUtil.matchLastIndex("aaa", Pattern.compile("aa"), 2) >= 0);
	}

	@Test
	@org.junit.jupiter.api.Timeout(2)
	public void testReplaceRegexOffsetInfiniteLoop() {
		assertNotNull(StringUtil.replaceRegex("aaa", Pattern.compile("aa"), "b", 1, 2));
	}

	@Test
	public void testNullSafety() {
		assertEquals(-1, StringUtil.getSymMarkIndex("(", ")", null, 0));
		assertEquals(-1, StringUtil.getSymMarkMatchIndex("(", ")", null, 0));
		assertEquals(-1, StringUtil.getSymMarkReverseIndex("(", ")", null, 1));
		assertNull(StringUtil.clearSymMarkContent(null, "(", ")"));
		assertEquals(-1, StringUtil.matchIndex(null, Pattern.compile("a")));
		assertArrayEquals(new int[] { -1, -1 }, StringUtil.matchIndex(null, Pattern.compile("a"), 0));
		assertEquals(0, StringUtil.matchCnt(null, "a", 0, 1));
		assertEquals(0, StringUtil.matchCnt(null, "a", 0, 1, 0));
		assertEquals(-1, StringUtil.indexOrder(null, "a", 0));
		assertArrayEquals(new int[0], StringUtil.str2ASCII(null));
		assertFalse(StringUtil.like(null, new String[] { "a" }));
		assertFalse(StringUtil.matches("abc", (String) null));
	}

	@Test
	public void testLoopAppendWithSignNullSource() {
		String result = StringUtil.loopAppendWithSign(null, ",", 3);
		assertEquals(",,", result);
	}

	@Test
	public void testSymMarkMatchIndexWithRegex() {
		// getSymMarkMatchIndex accepts regex patterns (not literal strings)
		int idx = StringUtil.getSymMarkMatchIndex("\\(", "\\)", "a(b+c)", 0);
		assertTrue(idx > 0);
	}

	@Test
	public void testSplitRegexDotAndPipe() {
		assertArrayEquals(new String[] { "1", "2", "3" }, StringUtil.splitRegex("1.2.3", ".", false));
		assertArrayEquals(new String[] { "a", "b" }, StringUtil.splitRegex("a|b", "|", false));
	}

	@Test
	public void testSecureMaskNegativeParams() {
		String result = StringUtil.secureMask("hello", -1, 2, "***");
		assertNotNull(result);
	}

	@Test
	public void testReplaceFirstStrNullReplacement() {
		assertEquals("ac", StringUtil.replaceFirstStr("abc", "b", null));
	}

	@Test
	public void testHumpFieldNamesWithNullElement() {
		String[] result = StringUtil.humpFieldNames(new String[] { "a_b", null, "c_d" });
		assertEquals("aB", result[0]);
		assertNull(result[1]);
		assertEquals("cD", result[2]);
	}

	@Test
	public void testToDBC() {
		assertEquals("hello?world:test", StringUtil.toDBC("hello？world：test"));
		assertEquals("a,b;c.d", StringUtil.toDBC("a，b；c．d"));
		assertEquals("x=y(z[w])", StringUtil.toDBC("x＝y（z【w】）"));
	}

	@Test
	public void testMatchesNullRegex() {
		assertFalse(StringUtil.matches("abc", (String) null));
	}

}