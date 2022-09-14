package org.sagacity.sqltoy.utils;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ReservedWordsUtilsTest {
	@Test
	public void testConvertReservedWords() {
		String sql = "SELECT STAFF_NAME,[SEX_TYPE],\"STATUS\" FROM SQLTOY_STAFF_INFO WHERE #[`STATUS` IN (:status)]";
		ReservedWordsUtil.put("SEX_TYPE,STATUS");
		String lastSql = ReservedWordsUtil.convertSql(sql, DBType.MYSQL);
		assertEquals(lastSql,
				"SELECT STAFF_NAME,`SEX_TYPE`,`STATUS` FROM SQLTOY_STAFF_INFO WHERE #[`STATUS` IN (:status)]");
	}

    @Test
    public void testH2ConvertReservedWords() {
        String sql = "SELECT STAFF_NAME,[SEX_TYPE],\"STATUS\" FROM SQLTOY_STAFF_INFO WHERE #[`STATUS` IN (:status)]";
        ReservedWordsUtil.put("SEX_TYPE,STATUS");
        String lastSql = ReservedWordsUtil.convertSql(sql, DBType.H2);
        System.out.println(lastSql);
        assertEquals(lastSql,
                "SELECT STAFF_NAME,'SEX_TYPE','STATUS' FROM SQLTOY_STAFF_INFO WHERE #['STATUS' IN (:status)]");
    }
}
