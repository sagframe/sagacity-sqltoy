package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * ReservedWordsUtil.remove(2026-11-10新增API)的覆盖:与put的累加合并语义对称,
 * 用于运行期动态调整保留字集合及测试间恢复全局状态。
 * 起因场景:测试残留保留字会让convertWord生成 "STAFF_NAME" 带引号标识符,
 * 而PG实际列名为小写staff_name,导致"列不存在"。
 */
public class ReservedWordsUtilRemoveTest {

	@AfterEach
	public void cleanup() {
		// 全局静态状态,测试间必须恢复
		ReservedWordsUtil.clear();
	}

	@Test
	public void putThenRemoveRestoresPlain() {
		ReservedWordsUtil.put("staff_name,order");
		// put后convertWord按方言加引号
		assertEquals("\"staff_name\"", ReservedWordsUtil.convertWord("staff_name", DBType.POSTGRESQL));
		assertEquals("`order`", ReservedWordsUtil.convertWord("order", DBType.MYSQL));
		// remove单个词:该词恢复裸标识符,另一个词不受影响
		ReservedWordsUtil.remove("staff_name");
		assertEquals("staff_name", ReservedWordsUtil.convertWord("staff_name", DBType.POSTGRESQL),
				"remove后应恢复裸标识符");
		assertEquals("`order`", ReservedWordsUtil.convertWord("order", DBType.MYSQL), "未remove的词应保持引号");
	}

	@Test
	public void removeMultipleAndCaseInsensitive() {
		ReservedWordsUtil.put("alpha,beta,gamma");
		ReservedWordsUtil.remove(" ALPHA , beta ");
		assertEquals("alpha", ReservedWordsUtil.convertWord("alpha", DBType.DB2), "大小写不敏感+trim");
		assertEquals("BETA", ReservedWordsUtil.convertWord("BETA", DBType.DB2), "remove大小写不敏感");
		assertEquals("\"gamma\"", ReservedWordsUtil.convertWord("gamma", DBType.DB2), "gamma保留");
	}

	@Test
	public void removeUnknownWordIsNoop() {
		ReservedWordsUtil.put("keepme");
		ReservedWordsUtil.remove("notexist");
		assertEquals("\"keepme\"", ReservedWordsUtil.convertWord("keepme", DBType.ORACLE), "remove未知词不影响既有集合");
	}

	@Test
	public void removeBlankIsNoop() {
		ReservedWordsUtil.put("word1");
		ReservedWordsUtil.remove("");
		ReservedWordsUtil.remove(null);
		ReservedWordsUtil.remove(" , ");
		assertEquals("[word1]", ReservedWordsUtil.convertWord("word1", DBType.SQLSERVER), "空白remove为no-op");
	}

	@Test
	public void removeAllLeavesEmptyThenConvertBypasses() {
		ReservedWordsUtil.put("onlyword");
		ReservedWordsUtil.remove("onlyword");
		// 集合空时convertWord快速路径原样返回
		assertEquals("onlyword", ReservedWordsUtil.convertWord("onlyword", DBType.POSTGRESQL));
		assertEquals("anything", ReservedWordsUtil.convertWord("anything", DBType.MYSQL));
	}

	@Test
	public void putAfterRemoveReAccumulates() {
		ReservedWordsUtil.put("w1");
		ReservedWordsUtil.remove("w1");
		ReservedWordsUtil.put("w1,w2");
		assertEquals("\"w1\"", ReservedWordsUtil.convertWord("w1", DBType.H2), "remove后可重新put");
		assertEquals("\"w2\"", ReservedWordsUtil.convertWord("w2", DBType.H2));
	}
}
