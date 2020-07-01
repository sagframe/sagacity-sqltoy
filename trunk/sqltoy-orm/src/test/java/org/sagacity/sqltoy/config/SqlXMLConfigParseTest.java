package org.sagacity.sqltoy.config;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.SqlToyResult;
import org.sagacity.sqltoy.utils.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSON;

public class SqlXMLConfigParseTest {
	/**
	 * 定义全局日志
	 */
	protected final static Logger logger = LoggerFactory.getLogger(SqlXMLConfigParseTest.class);

	/**
	 * 测试外部报表平台集成sqltoy传递xml字符串片段解析
	 */
	@Test
	public void testParseSegmentXML() {
		String sql = FileUtil.readFileAsString("classpath:scripts/ifScriptSql.sql", "UTF-8");
		SqlToyResult result = SqlConfigParseUtils.processSql(sql, new String[] { "id", "name", "status" },
				new Object[] { null, "chen", "1" });
		System.err.println("id==null:" + JSON.toJSONString(result));

		SqlToyResult result1 = SqlConfigParseUtils.processSql(sql, new String[] { "id", "name", "status" },
				new Object[] { "1", "chen", "1" });
		System.err.println("id<>null:" + JSON.toJSONString(result1));
	}
}
