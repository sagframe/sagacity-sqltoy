package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * matchIndex(source,pattern,start)以Matcher.region替代substring的等价性回归。
 * 该方法是引号/括号配对循环内推进式调用的热点(每次调用原实现拷贝一次尾部字符串),
 * 改region后必须与"截断出新字符串再匹配"的下标语义完全一致,尤其是锚定/边界类模式
 */
public class StringUtilMatchIndexRegionTest {

	/**
	 * 参照实现:与改造前完全一致的substring版本
	 */
	private static int[] reference(String source, Pattern pattern, int start) {
		if (source == null || source.length() <= start) {
			return new int[] { -1, -1 };
		}
		Matcher m = pattern.matcher(source.substring(start));
		if (m.find()) {
			return new int[] { m.start() + start, m.end() + start };
		}
		return new int[] { -1, -1 };
	}

	private static final String[] SOURCES = { "select a from t where b='x1' order by c", "  abc  def  ", "a1b2c3",
			"  x", "::abc::def", "T 10:20 as \"Q\"", "\nfoo\nbar", "[desc]=1 and [order] =2", "a", " ", "",
			"((1+2))*(3)", "col='it''s' and x=1" };

	private static final Pattern[] PATTERNS = { Pattern.compile("^a"), Pattern.compile("\\ba"),
			Pattern.compile("\\bfoo"), Pattern.compile("\\Z"), Pattern.compile("(?<!x)b"), Pattern.compile("(?i)t"),
			Pattern.compile("\\s+\\S"), Pattern.compile("$"), Pattern.compile("\\A."), Pattern.compile("[0-9]+"),
			Pattern.compile("(?<=\\W)a"), Pattern.compile("'"), Pattern.compile("\\("), Pattern.compile("\\)"),
			Pattern.compile("\\[|\\]"), Pattern.compile("(?i)\\Wwhere\\W"), Pattern.compile("(?s).*\\Z"),
			Pattern.compile("(?<=a)(?=1)"), Pattern.compile("\\G1"), Pattern.compile("^"), Pattern.compile("$|\\Z"),
			Pattern.compile("q", Pattern.CASE_INSENSITIVE), Pattern.compile("\\Be\\B") };

	@Test
	public void regionMatchesSubstringSemanticsForAllOffsets() {
		for (String source : SOURCES) {
			for (Pattern pattern : PATTERNS) {
				for (int start = 0; start <= source.length() + 1; start++) {
					assertArrayEquals(reference(source, pattern, start), StringUtil.matchIndex(source, pattern, start),
							"source=[" + source + "] pattern=" + pattern + " start=" + start);
				}
			}
		}
	}

	@Test
	public void nullAndOutOfRangeSource() {
		assertArrayEquals(new int[] { -1, -1 }, StringUtil.matchIndex(null, Pattern.compile("a"), 0));
		assertArrayEquals(new int[] { -1, -1 }, StringUtil.matchIndex("abc", Pattern.compile("a"), 3));
		assertArrayEquals(new int[] { -1, -1 }, StringUtil.matchIndex("abc", Pattern.compile("a"), 9));
		// 推进式调用:从上次匹配的end继续找,下标始终相对完整source
		int[] first = StringUtil.matchIndex("a1b2c3", Pattern.compile("[0-9]"), 0);
		assertArrayEquals(new int[] { 1, 2 }, first);
		assertArrayEquals(new int[] { 3, 4 }, StringUtil.matchIndex("a1b2c3", Pattern.compile("[0-9]"), first[1]));
	}
}
