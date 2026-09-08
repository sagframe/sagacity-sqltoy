package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * 数据库保留字处理的单元测试
 */
public class ReservedWordsUtilsTest {
	@Test
	public void testConvertReservedWords() {
		// put为累加合并语义且集合为全局静态,显式清空建立前置状态,避免其他测试类的残留影响
		ReservedWordsUtil.clear();
		ReservedWordsUtil.put("SEX_TYPE,STATUS");
		String sql = "SELECT STAFF_NAME,[SEX_TYPE],\"STATUS\" FROM SQLTOY_STAFF_INFO WHERE #[`STATUS` IN (:status)]";
		String lastSql = ReservedWordsUtil.convertSql(sql, DBType.MYSQL);
		assertEquals("SELECT STAFF_NAME,`SEX_TYPE`,`STATUS` FROM SQLTOY_STAFF_INFO WHERE #[`STATUS` IN (:status)]",
				lastSql);
	}

	@Test
	public void testH2ConvertReservedWords() {
		// put为累加合并语义且集合为全局静态,显式清空建立前置状态,避免其他测试类的残留影响
		ReservedWordsUtil.clear();
		ReservedWordsUtil.put("SEX_TYPE,STATUS");
		String sql = "SELECT STAFF_NAME,[SEX_TYPE],\"STATUS\" FROM SQLTOY_STAFF_INFO WHERE #[`STATUS` IN (:status)]";
		String lastSql = ReservedWordsUtil.convertSql(sql, DBType.H2);
		// H2遵循SQL标准,定界标识符使用双引号;单引号是字符串字面量,'STATUS' IN (:status)会变成字符串比较
		assertEquals("SELECT STAFF_NAME,\"SEX_TYPE\",\"STATUS\" FROM SQLTOY_STAFF_INFO WHERE #[\"STATUS\" IN (:status)]",
				lastSql);
	}
}
