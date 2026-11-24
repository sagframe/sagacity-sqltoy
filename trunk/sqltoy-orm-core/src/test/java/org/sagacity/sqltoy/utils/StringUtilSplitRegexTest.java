package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * @project sagacity-sqltoy
 * @description splitRegex 正则分支改用缓存 Pattern 后的行为校验：结果必须与 String.split 完全一致
 *              (按分隔符切分、limit=0 丢弃尾部空串、doTrim 逐段去空白)。
 * @author zhongxuchen
 */
public class StringUtilSplitRegexTest {
	@Test
	public void fastPathSeparators() {
		// 单字符分隔符走JDK快路径,行为不变
		assertArrayEquals(new String[] { "a", "b" }, StringUtil.splitRegex("a,b", ",", false));
		assertArrayEquals(new String[] { "a", "b" }, StringUtil.splitRegex("a;b", ";", false));
		assertArrayEquals(new String[] { "a", "b" }, StringUtil.splitRegex("a:b", ":", false));
		assertArrayEquals(new String[] { "a", "b" }, StringUtil.splitRegex("a?b", "?", false));
		assertArrayEquals(new String[] { "a", "b" }, StringUtil.splitRegex("a.b", ".", false));
		assertArrayEquals(new String[] { "a", "b" }, StringUtil.splitRegex("a|b", "|", false));
		// 尾部空串按 String.split 语义丢弃
		assertArrayEquals(new String[] { "a" }, StringUtil.splitRegex("a,", ",", false));
	}

	@Test
	public void whitespacePatternSplitsLikeStringSplit() {
		// 空白分隔符 -> \s+(连续空白视为一个分隔)
		assertArrayEquals(new String[] { "A", "B", "C" }, StringUtil.splitRegex("A  B\tC ", " ", true));
		assertArrayEquals(new String[] { "A", "B", "C", "D" }, StringUtil.splitRegex("A B  C \n D ", "   ", true));
	}

	@Test
	public void multiCharAndCustomPatterns() {
		assertArrayEquals(new String[] { "a", "b" }, StringUtil.splitRegex("a||b", "||", false));
		assertArrayEquals(new String[] { "a", "b" }, StringUtil.splitRegex("a&&b", "&&", false));
		assertArrayEquals(new String[] { "a", "b", "c" }, StringUtil.splitRegex("a;b,c", "[;,]|;", false));
		assertArrayEquals(new String[] { "a", "b" }, StringUtil.splitRegex("a,,,,b", "\\,{2,}", false));
	}

	@Test
	public void trimAppliesToEachSegment() {
		assertArrayEquals(new String[] { "a", "b" }, StringUtil.splitRegex(" a , b ", ",", true));
	}

	@Test
	public void nullSourceReturnsNull() {
		assertNull(StringUtil.splitRegex(null, ",", true));
	}
}