package org.sagacity.sqltoy.dialect;

import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.dialect.utils.DialectUtils;
import org.sagacity.sqltoy.utils.StringUtil;

public class SqlServerDialectTest {
	private static final Pattern ORDER_BY = Pattern.compile("(?i)\\Worder\\s*by\\W");

	@Test
	public void testPageSql() {
		String realSql = "select top partation( order by) from (select from table ) t1 where t1.name=?";
		//realSql = "select * from (select from order by a ) t1 where t1.name=? order by name";
		StringBuilder sql = new StringBuilder(realSql);
		// order by位置
		int orderByIndex = StringUtil.matchIndex(realSql, ORDER_BY);
		// 存在order by，继续判断order by 是否在子查询内
		if (orderByIndex > 0) {
			String clearSql = DialectUtils.clearDisturbSql(realSql);
			// 剔除select 和from 之间内容，剔除sql中所有()之间的内容,即剔除所有子查询，再判断是否有order by
			orderByIndex = StringUtil.matchIndex(clearSql, ORDER_BY);
		}
		// 不存在order by或order by存在于子查询中
		if (orderByIndex < 0) {
			sql.append(" order by NEWID() ");
		}

		System.err.println(sql.toString());
	}
}
