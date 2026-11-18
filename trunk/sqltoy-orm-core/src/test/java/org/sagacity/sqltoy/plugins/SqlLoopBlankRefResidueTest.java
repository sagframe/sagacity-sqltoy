package org.sagacity.sqltoy.plugins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.SqlToyThreadDataHolder;
import org.sagacity.sqltoy.config.SqlConfigParseUtils;
import org.sagacity.sqltoy.config.model.SqlToyResult;

/**
 * @loop的#[...]段被引用集合null/空白值剔除后的连接符残留回归:
 * 原实现在段剔除前拼linkSign,剔空迭代留下孤立or/and("#[..] or #[..]"模式
 * 中间段消失后产生"or or"非法sql);现全空迭代不拼连接符不输出(与@secure-loop跳过语义对齐)
 *
 * @author zhongxuchen
 * @version v1.0,Date:2026-11-18
 */
public class SqlLoopBlankRefResidueTest {

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
	public void orLinkSignNoResidueWhenSegmentRemoved() {
		SqlToyResult result = parse(
				"select * from t where 1=1 and (@loop(:names, ' #[ (t.name=:names[i] or t.alias=:ids[i])] ', ' or '))",
				new String[] { "names", "ids" },
				new Object[] { new String[] { "a", "b", "c" }, new String[] { "x", "", "z" } });
		assertFalse(result.getSql().contains("or or"), "不应残留孤立or: " + result.getSql());
		assertEquals(2, countOccurrences(result.getSql(), "t.name="), "空白迭代剔空后不输出,a与c保留");
	}

	@Test
	public void andLinkSignNoResidueWhenSegmentRemoved() {
		SqlToyResult result = parse(
				"select * from t where 1=1 and (@loop(:names, ' #[ (t.name=:names[i] and t.alias=:ids[i])] ', ' and '))",
				new String[] { "names", "ids" },
				new Object[] { new String[] { "a", "b", "c" }, new Object[] { "x", "", "z" } });
		assertFalse(result.getSql().contains("and and"), "不应残留孤立and: " + result.getSql());
		assertEquals(2, countOccurrences(result.getSql(), "t.name="), "空白迭代剔空后不输出");
	}

	@Test
	public void textOutsideSegmentKeepsIteration() {
		// 循环体含段外文本:段剔除后非全空,迭代正常输出(不误跳),连接符正常连接
		SqlToyResult result = parse(
				"select * from t where 1=1 and (@loop(:names, ' #[ (t.name=:ids[i])] and t.flag=1 ', ' or '))",
				new String[] { "names", "ids" },
				new Object[] { new String[] { "a", "b", "c" }, new String[] { "x", "", "z" } });
		assertEquals(3, countOccurrences(result.getSql(), "t.flag="), "段外文本的迭代不跳过: " + result.getSql());
		assertEquals(2, countOccurrences(result.getSql(), "t.name="), "空白段剔除,段外文本保留");
		assertFalse(result.getSql().contains("or or"), "连接符不应残留");
	}
}
