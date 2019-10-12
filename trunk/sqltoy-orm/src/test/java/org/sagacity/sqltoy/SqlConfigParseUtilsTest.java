package org.sagacity.sqltoy;

import org.junit.Test;
import org.sagacity.sqltoy.config.SqlConfigParseUtils;
import org.sagacity.sqltoy.config.model.SqlToyResult;
import org.sagacity.sqltoy.utils.CommonUtils;

import com.alibaba.fastjson.JSON;

import junit.framework.TestCase;

public class SqlConfigParseUtilsTest extends TestCase {

	@Test
	public void testProcessSql() {
		String sql = CommonUtils.readFileAsString("classpath:scripts/ifScriptSql.sql", "UTF-8");
		SqlToyResult result = SqlConfigParseUtils.processSql(sql, new String[] { "id", "name", "status" },
				new Object[] { null, "chen", "1" });
		System.err.println("id==null:"+JSON.toJSONString(result));
		
		SqlToyResult result1 = SqlConfigParseUtils.processSql(sql, new String[] { "id", "name", "status" },
				new Object[] { "1", "chen", "1" });
		System.err.println("id<>null:"+JSON.toJSONString(result1));
	}
}
