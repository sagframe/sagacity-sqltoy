package org.sagacity.sqltoy.config;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
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
		String sql = FileUtil.readFileAsStr("classpath:scripts/report.xml", "UTF-8");
		try {
			SqlToyConfig result = SqlXMLConfigParse.parseSagment(sql, "utf-8", "mysql");
			System.err.println(JSON.toJSONString(result));
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * 测试外部报表平台集成sqltoy传递xml字符串片段解析
	 */
	@Test
	public void testParseSqlXML() {
		String sql = FileUtil.readFileAsStr("classpath:scripts/sql.xml", "UTF-8");
		try {
			SqlToyConfig result = SqlXMLConfigParse.parseSagment(sql, "utf-8", "mysql");
			System.err.println(JSON.toJSONString(result));
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	/**
	 * 测试外部报表平台集成sqltoy传递xml字符串片段解析
	 */
	@Test
	public void testParseFastWith() {
		String sql = FileUtil.readFileAsStr("classpath:scripts/fast.xml", "UTF-8");
		try {
			SqlToyConfig result = SqlXMLConfigParse.parseSagment(sql, "utf-8", "mysql");
			System.err.println(result.getFastWithSql(null));
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
