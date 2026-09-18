package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.SqlToyConstants;

/**
 * getSymMarkIndex相邻引号修复的穷举差分验证。
 * 将修改前的旧实现原样复制为legacy方法,在字母表{a , " ' \\}长度0..7的全部97,656个输入上
 * 与新实现逐一对照,验证两条性质:
 * P1(零影响):不含''与""相邻引号的输入,新旧结果完全一致——常规路径行为零变化;
 * P2(变更面):所有分歧输入必然包含''或""相邻引号——即修复仅影响相邻引号场景。
 * legacy方法为git HEAD版本的逐字复制,作为旧语义的固化参照。
 */
public class StringUtilGetSymMarkDifferentialTest {

	private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory
			.getLogger(StringUtilGetSymMarkDifferentialTest.class);

	private static final java.util.regex.Pattern L_QUOTA = java.util.regex.Pattern.compile("(^')|([^\\\\]')");
	private static final java.util.regex.Pattern L_QUOTA_CHK = java.util.regex.Pattern.compile("[^\\\\]'");
	private static final java.util.regex.Pattern L_TWO_QUOTA = java.util.regex.Pattern.compile("(^\")|([^\\\\]\")");
	private static final java.util.regex.Pattern L_TWO_QUOTA_CHK = java.util.regex.Pattern.compile("[^\\\\]\"");

	// ---------------- 旧实现逐字复制(git HEAD版本) ----------------

	private static int legacyGetSymMarkIndex(String beginMarkSign, String endMarkSign, String source, int startIndex) {
		if (source == null) {
			return -1;
		}
		java.util.regex.Pattern pattern = null;
		java.util.regex.Pattern chkPattern = null;
		if ("'".equals(beginMarkSign)) {
			pattern = L_QUOTA;
			chkPattern = L_QUOTA_CHK;
		} else if ("\"".equals(beginMarkSign)) {
			pattern = L_TWO_QUOTA;
			chkPattern = L_TWO_QUOTA_CHK;
		}
		boolean symMarkIsEqual = beginMarkSign.equals(endMarkSign) ? true : false;
		int beginSignIndex = -1;
		if (pattern == null) {
			beginSignIndex = source.indexOf(beginMarkSign, startIndex);
		} else {
			beginSignIndex = StringUtil.matchIndex(source, pattern, startIndex)[0];
			if (beginSignIndex > startIndex) {
				beginSignIndex = beginSignIndex + 1;
			}
		}
		if (beginSignIndex == -1) {
			return source.indexOf(endMarkSign, startIndex);
		}
		int endIndex = -1;
		if (pattern == null) {
			endIndex = source.indexOf(endMarkSign, beginSignIndex + 1);
		} else {
			endIndex = StringUtil.matchIndex(source, pattern, beginSignIndex + 1)[0];
			if (endIndex > beginSignIndex + 1) {
				endIndex = endIndex + 1;
			} else if (endIndex == beginSignIndex + 1) {
				if (StringUtil.matchIndex(source, chkPattern, beginSignIndex + 1)[0] == endIndex) {
					endIndex = endIndex + 1;
				}
			}
		}
		int preEndIndex = 0;
		while (endIndex > beginSignIndex) {
			if (pattern == null) {
				beginSignIndex = source.indexOf(beginMarkSign, (symMarkIsEqual ? endIndex : beginSignIndex) + 1);
			} else {
				beginSignIndex = StringUtil.matchIndex(source, pattern, endIndex + 1)[0];
				if (beginSignIndex > endIndex + 1) {
					beginSignIndex = beginSignIndex + 1;
				} else if (beginSignIndex == endIndex + 1) {
					if (StringUtil.matchIndex(source, chkPattern, endIndex + 1)[0] == beginSignIndex) {
						beginSignIndex = beginSignIndex + 1;
					}
				}
			}
			if (beginSignIndex == -1 || beginSignIndex > endIndex) {
				return endIndex;
			}
			preEndIndex = endIndex;
			if (pattern == null) {
				endIndex = source.indexOf(endMarkSign, (symMarkIsEqual ? beginSignIndex : endIndex) + 1);
			} else {
				endIndex = StringUtil.matchIndex(source, pattern, beginSignIndex + 1)[0];
				if (endIndex > beginSignIndex + 1) {
					endIndex = endIndex + 1;
				} else if (endIndex == beginSignIndex + 1) {
					if (StringUtil.matchIndex(source, chkPattern, beginSignIndex + 1)[0] == endIndex) {
						endIndex = endIndex + 1;
					}
				}
			}
			if (endIndex == -1) {
				return preEndIndex;
			}
		}
		return endIndex;
	}

	private static int[] legacyGetStartEndIndex(String source, String[] filter, int skipIndex, int splitIndex) {
		int[] result = { -1, -1 };
		java.util.regex.Pattern pattern = null;
		if ("'".equals(filter[0])) {
			pattern = L_QUOTA;
		} else if ("\"".equals(filter[0])) {
			pattern = L_TWO_QUOTA;
		}
		String tmp;
		if (pattern == null) {
			result[0] = source.indexOf(filter[0], skipIndex);
			if (result[0] >= 0) {
				result[1] = legacyGetSymMarkIndex(filter[0], filter[1], source, skipIndex);
			}
		} else {
			result[0] = StringUtil.matchIndex(source, pattern, skipIndex)[0];
			if (result[0] >= 0) {
				tmp = source.substring(result[0], result[0] + 1);
				if (!"'".equals(tmp) && !"\"".equals(tmp)) {
					result[0] = result[0] + 1;
				}
				result[1] = legacyGetSymMarkIndex(filter[0], filter[1], source, result[0]);
			}
		}
		while (result[1] > 0 && result[1] < splitIndex) {
			if (pattern == null) {
				result[0] = source.indexOf(filter[0], result[1] + 1);
				if (result[0] > 0) {
					result[1] = legacyGetSymMarkIndex(filter[0], filter[1], source, result[0]);
				} else {
					result[1] = -1;
				}
			} else {
				tmp = source.substring(result[1], result[1] + 1);
				if (!"'".equals(tmp) && !"\"".equals(tmp)) {
					result[0] = StringUtil.matchIndex(source, pattern, result[1] + 2)[0];
				} else {
					result[0] = StringUtil.matchIndex(source, pattern, result[1] + 1)[0];
				}
				if (result[0] > 0) {
					tmp = source.substring(result[0], result[0] + 1);
					if (!"'".equals(tmp) && !"\"".equals(tmp)) {
						result[0] = result[0] + 1;
					}
					result[1] = legacyGetSymMarkIndex(filter[0], filter[1], source, result[0]);
				} else {
					result[1] = -1;
				}
			}
		}
		return result;
	}

	private static String[] legacySplitExcludeSymMark(String source, String splitSign,
			java.util.Map<String, String> filterMap) {
		if (source == null) {
			return null;
		}
		int splitIndex = source.indexOf(splitSign);
		if (splitIndex == -1) {
			return new String[] { source };
		}
		if (filterMap == null || filterMap.isEmpty()) {
			return StringUtil.splitRegex(source, splitSign, false);
		}
		java.util.List<String[]> filters = StringUtil.matchFilters(source, filterMap);
		if (filters.isEmpty()) {
			return StringUtil.splitRegex(source, splitSign, false);
		}
		int splitSignLen = splitSign.length();
		int start = 0;
		int skipIndex = 0;
		int preSplitIndex = splitIndex;
		java.util.ArrayList<String> splitResults = new java.util.ArrayList<String>();
		int max = -1;
		int[] startEnd;
		while (splitIndex != -1) {
			max = -1;
			for (String[] filter : filters) {
				startEnd = legacyGetStartEndIndex(source, filter, skipIndex, splitIndex);
				if (startEnd[0] >= 0 && startEnd[0] <= splitIndex && startEnd[1] >= splitIndex && startEnd[1] > max) {
					max = startEnd[1];
				}
			}
			if (max > -1) {
				skipIndex = max + 1;
				splitIndex = source.indexOf(splitSign, skipIndex);
			}
			if (preSplitIndex == splitIndex) {
				splitResults.add(source.substring(start, preSplitIndex));
				start = preSplitIndex + splitSignLen;
				skipIndex = start;
				splitIndex = source.indexOf(splitSign, skipIndex);
				preSplitIndex = splitIndex;
			} else {
				preSplitIndex = splitIndex;
			}
		}
		splitResults.add(source.substring(start));
		return splitResults.toArray(new String[0]);
	}

	private static String legacyClearSymMarkContent(String sql, String startMark, String endMark) {
		if (sql == null) {
			return null;
		}
		StringBuilder lastSql = new StringBuilder(sql);
		int endMarkLength = endMark.length();
		int start = lastSql.indexOf(startMark);
		int symMarkEnd;
		while (start != -1) {
			symMarkEnd = legacyGetSymMarkIndex(startMark, endMark, lastSql.toString(), start);
			if (symMarkEnd != -1) {
				lastSql.delete(start, symMarkEnd + endMarkLength);
				start = lastSql.indexOf(startMark);
			} else {
				break;
			}
		}
		return lastSql.toString();
	}

	// ---------------- 穷举差分 ----------------

	private static boolean hasAdjacentQuotes(String s) {
		return s.contains("''") || s.contains("\"\"");
	}

	@Test
	public void exhaustiveDifferential() {
		char[] alphabet = { 'a', ',', '"', '\'', '\\' };
		int maxLen = 7;
		int total = 0;
		int divergeSymMark = 0;
		int divergeSplit = 0;
		int divergeClearQuote = 0;
		StringBuilder divergeSamples = new StringBuilder();
		for (int len = 0; len <= maxLen; len++) {
			int combos = (int) Math.pow(alphabet.length, len);
			for (int code = 0; code < combos; code++) {
				char[] chars = new char[len];
				int v = code;
				for (int i = 0; i < len; i++) {
					chars[i] = alphabet[v % alphabet.length];
					v /= alphabet.length;
				}
				String s = new String(chars);
				total++;
				boolean adjacent = hasAdjacentQuotes(s);
				// getSymMarkIndex:单双引号两种标记
				for (String[] marks : new String[][] { { "'", "'" }, { "\"", "\"" } }) {
					int oldR = legacyGetSymMarkIndex(marks[0], marks[1], s, 0);
					int newR = StringUtil.getSymMarkIndex(marks[0], marks[1], s, 0);
					if (oldR != newR) {
						divergeSymMark++;
						assertTrue(adjacent, "分歧输入必须含相邻引号,实际:" + s + " old=" + oldR + " new=" + newR);
						if (divergeSamples.length() < 2000) {
							divergeSamples.append("getSymMark[").append(marks[0]).append("] old=").append(oldR)
									.append(" new=").append(newR).append(" src=").append(s).append('\n');
						}
					}
				}
				// splitExcludeSymMark
				String[] oldArr = legacySplitExcludeSymMark(s, ",", SqlToyConstants.filters);
				String[] newArr = StringUtil.splitExcludeSymMark(s, ",", SqlToyConstants.filters);
				if (!Arrays.equals(oldArr, newArr)) {
					divergeSplit++;
					assertTrue(adjacent, "分歧输入必须含相邻引号,实际:" + s + " old=" + Arrays.toString(oldArr) + " new="
							+ Arrays.toString(newArr));
					if (divergeSamples.length() < 4000) {
						divergeSamples.append("split old=").append(Arrays.toString(oldArr)).append(" new=")
								.append(Arrays.toString(newArr)).append(" src=").append(s).append('\n');
					}
				}
				// clearSymMarkContent:引号标记(受影响面)与括号标记(null分支,必须完全一致)
				String oldC = legacyClearSymMarkContent(s, "'", "'");
				String newC = StringUtil.clearSymMarkContent(s, "'", "'");
				if (!oldC.equals(newC)) {
					divergeClearQuote++;
					assertTrue(adjacent, "分歧输入必须含相邻引号,实际:" + s);
				}
				assertEquals(legacyClearSymMarkContent(s, "(", ")"), StringUtil.clearSymMarkContent(s, "(", ")"),
						"括号标记走indexOf分支,必须零变化:" + s);
			}
		}
		logger.info("differential total={} divergeSymMark={} divergeSplit={} divergeClearQuote={}", total,
				divergeSymMark, divergeSplit, divergeClearQuote);
		System.err.println("[differential] total=" + total + " divergeSymMark=" + divergeSymMark + " divergeSplit="
				+ divergeSplit + " divergeClearQuote=" + divergeClearQuote);
		System.err.println("[differential samples]\n" + divergeSamples);
		// 存在实际分歧(证明对照有效,旧实现对相邻引号本就产生不同结果)
		assertTrue(divergeSymMark > 0 || divergeSplit > 0, "穷举应捕获到相邻引号场景的新旧行为差异");
	}
}
