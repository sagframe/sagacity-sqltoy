package org.sagacity.sqltoy.config;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlType;
import org.sagacity.sqltoy.utils.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson2.JSON;

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

	@Test
	public void testChinaParamName() throws Exception {
		String sql = "select * from table where 1=1 #[and id=:单据_编号_id] and name like @value(:name) #[and status=:status]";
		SqlToyConfig sqlToyConfig = SqlConfigParseUtils.parseSqlToyConfig(sql, "mysql", SqlType.search);
		System.err.println(JSON.toJSONString(sqlToyConfig));
	}
	
	@Test
	public void testWith() throws Exception {
		String sql = "with t1 (a, b) as not  materialized (select * from table),t2(c,d) as materialized(select name from ta) "
				+ ""
				+ ""
				+ "@fast(select * from t1)";
		SqlToyConfig sqlToyConfig = SqlConfigParseUtils.parseSqlToyConfig(sql, "mysql", SqlType.search);
		System.err.println(sqlToyConfig.getFastSql(null));
		System.err.println(sqlToyConfig.getFastWithSql(null));
	}
}
