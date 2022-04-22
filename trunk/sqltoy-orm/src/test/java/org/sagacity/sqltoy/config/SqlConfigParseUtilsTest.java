package org.sagacity.sqltoy.config;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlToyResult;
import org.sagacity.sqltoy.utils.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.alibaba.fastjson.JSON;

public class SqlConfigParseUtilsTest {
	private static DocumentBuilderFactory domFactory = DocumentBuilderFactory.newInstance();
	/**
	 * 定义全局日志
	 */
	protected final static Logger logger = LoggerFactory.getLogger(SqlConfigParseUtilsTest.class);

	@Test
	public void testProcessSql() {
		String sql = FileUtil.readFileAsStr("classpath:scripts/ifScriptSql.sql", "UTF-8");
		SqlToyResult result = SqlConfigParseUtils.processSql(sql, new String[] { "id", "name", "status" },
				new Object[] { null, "chen", "1" });
		System.err.println("id==null:" + JSON.toJSONString(result));

		SqlToyResult result1 = SqlConfigParseUtils.processSql(sql, new String[] { "id", "name", "status" },
				new Object[] { "1", "chen", "1" });
		System.err.println("id<>null:" + JSON.toJSONString(result1));
	}

	@Test
	public void testParseSql() throws Exception {
		String sqlFile = "classpath:scripts/showcase-sql.sql.xml";
		List<SqlToyConfig> result = new ArrayList<SqlToyConfig>();
		InputStream fileIS = FileUtil.getFileInputStream(sqlFile);
		domFactory.setFeature(SqlToyConstants.XML_FETURE, false);
		DocumentBuilder domBuilder = domFactory.newDocumentBuilder();
		Document doc = domBuilder.parse(fileIS);
		NodeList sqlElts = doc.getDocumentElement().getChildNodes();
		if (sqlElts == null || sqlElts.getLength() == 0)
			return;
		// 解析单个sql
		Element sqlElt;
		Node obj;
		for (int i = 0; i < sqlElts.getLength(); i++) {
			obj = sqlElts.item(i);
			if (obj.getNodeType() == Node.ELEMENT_NODE) {
				sqlElt = (Element) obj;
				result.add(SqlXMLConfigParse.parseSingleSql(sqlElt, "mysql"));
			}
		}
		for (SqlToyConfig config : result) {
			System.err.println(config.getSql());
		}
	}

	@Test
	public void testNull() throws Exception {
		String sql = "select * from table where 1=1 #[and id=:id and name like :name] #[and status=:status]";
		SqlToyResult result = SqlConfigParseUtils.processSql(sql, new String[] { "id", "name", "status" },
				new Object[] { "1", null, "1" });
		System.err.println(JSON.toJSONString(result));
	}

	@Test
	public void testAllInnerNull() throws Exception {
		String sql = "select * from (select * from table where 1=1 #[and id=:id and name like :name] #[and status=:status]) left join table2 on";
		SqlToyResult result = SqlConfigParseUtils.processSql(sql, new String[] { "id", "name", "status" },
				new Object[] { null, null, null });
		System.err.println(JSON.toJSONString(result));
	}

	@Test
	public void testAllNull() throws Exception {
		String sql = "select * from table where #[id=:id and name like :name] #[and status=:status]";
		SqlToyResult result = SqlConfigParseUtils.processSql(sql, new String[] { "id", "name", "status" },
				new Object[] { null, null, null });
		System.err.println(JSON.toJSONString(result));
	}

	@Test
	public void testLoop() throws Exception {
		String sql = "select * from table where #[id=:id and name like :name] #[and @loop(:status,' status=':status[i]'','or')]";
		SqlToyResult result = SqlConfigParseUtils.processSql(sql, new String[] { "id", "name", "status" },
				new Object[] { "1", null, new Object[] { "1", "2", "3" } });
		System.err.println(JSON.toJSONString(result));
		result = SqlConfigParseUtils.processSql(sql, new String[] { "id", "name", "status" },
				new Object[] { "1", null, null });
		System.err.println(JSON.toJSONString(result));
	}

	@Test
	public void testSynSign() throws Exception {
		String sql = "select * from table where #[id in [arraystringconcat(name)] and id=:id ]#[and name like :name] #[and status=:status]";
		SqlToyResult result = SqlConfigParseUtils.processSql(sql, new String[] { "id", "name", "status" },
				new Object[] { "1", null, "1" });
		System.err.println(JSON.toJSONString(result));
	}

	@Test
	public void testOptSign() throws Exception {
		String sql = "select * from table where #[id=id+:id ]#[and name like :name] #[and status=:status]";
		SqlToyResult result = SqlConfigParseUtils.processSql(sql, new String[] { "id", "name", "status" },
				new Object[] { "1", null, "1" });
		System.err.println(JSON.toJSONString(result));
	}

	@Test
	public void testAtValue() throws Exception {
		String sql = "select * from table where 1=1 #[and id=:id] and name like @value(:name) #[and status=:status]";
		SqlToyResult result = SqlConfigParseUtils.processSql(sql, new String[] { "id", "name", "status" },
				new Object[] { "1", null, "1" });
		System.err.println(JSON.toJSONString(result));
	}

	@Test
	public void testChinaParamName() throws Exception {
		String sql = "select * from table where 1=1 #[and id=:单据_编号_id] and name like @value(:name) #[and status=:status]";
		SqlToyResult result = SqlConfigParseUtils.processSql(sql, new String[] { "单据_编号_id", "name", "status" },
				new Object[] { "1", null, "1" });
		System.err.println(JSON.toJSONString(result));
	}

	@Test
	public void testGetParamNames() throws Exception {
		String sql = "select * from table where a=:a1_a and status=:staff.工号_id #[and id=:单据_编号_id] and name like @value(:name.id[i]) #[and status=:status]@loop(:group.staffIds,:group.staffIds[i].id)";
		String[] result = SqlConfigParseUtils.getSqlParamsName(sql, true);
		System.err.println(JSON.toJSONString(result));
	}

	@Test
	public void testOverSizeIn() throws Exception {
		String sql = "select * from table t where status=:status and t.order_id in (:oderId)";
		String[] orderIds = new String[2000];
		for (int i = 1; i <= 2000; i++) {
			orderIds[i - 1] = "" + i;
		}
		SqlToyResult result = SqlConfigParseUtils.processSql(sql, new String[] { "status", "oderId" },
				new Object[] { "1", orderIds });
		System.err.println(result.getSql());
		System.err.println(result.getParamsValue().length);
	}
	
//	@Test
//	public void testOverSizeIn() throws Exception {
//		String sql = " and t.order_id not ";
//		String result = SqlConfigParseUtils.wrapOverSizeInSql(sql, 2001);
//		System.err.println(result);
//		
//		sql = " and )order_id not ";
//		result = SqlConfigParseUtils.wrapOverSizeInSql(sql, 2001);
//		System.err.println(result);
//		
//		sql = "	[ order_id not ";
//		result = SqlConfigParseUtils.wrapOverSizeInSql(sql, 2000);
//		System.err.println(result);
//	}
}
