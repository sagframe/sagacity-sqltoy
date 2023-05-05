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
import org.sagacity.sqltoy.model.MapKit;
import org.sagacity.sqltoy.utils.FileUtil;
import org.sagacity.sqltoy.utils.StringUtil;
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
				new Object[] { 1, "chen", "1" });
		System.err.println("id==null:" + JSON.toJSONString(result));

		SqlToyResult result1 = SqlConfigParseUtils.processSql(sql, new String[] { "id", "name", "status" },
				new Object[] { "1", "chen", "1" });
		System.err.println("id<>null:" + JSON.toJSONString(result1));

		SqlToyResult result2 = SqlConfigParseUtils.processSql(sql,
				MapKit.keys("id", "name", "status").values("1", "chen", "1"));
		System.err.println("id<>null:" + JSON.toJSONString(result2));
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
	public void testNull1() throws Exception {
		String sql = "select * from table where #[id=:id ] #[status=:status]";
		SqlToyResult result = SqlConfigParseUtils.processSql(sql, new String[] { "id", "status" },
				new Object[] { null, "1" });
		System.err.println(JSON.toJSONString(result));
	}

	@Test
	public void testNull2() throws Exception {
		String sql = "select * from table where #[id=:id ] #[status=:status] limit10";
		SqlToyResult result = SqlConfigParseUtils.processSql(sql, new String[] { "id", "status" },
				new Object[] { null, null });
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
		String sql = "select * from table t where status=:status and (t.order_id not in (:oderId))";
		String[] orderIds = new String[2000];
		for (int i = 1; i <= 2000; i++) {
			orderIds[i - 1] = "" + i;
		}
		SqlToyResult result = SqlConfigParseUtils.processSql(sql, new String[] { "status", "oderId" },
				new Object[] { "1", orderIds });
		System.err.println(result.getSql());
		System.err.println(result.getParamsValue().length);
	}

	@Test
	public void testMultiFieldOverSizeIn() throws Exception {
		String sql = "select * from table t where staff_name like :staffName and (id,type) in ((:ids,:types))  "
				+ "and create_time>:beginDate and status in(:status)";
		int size = 0;
		String[] orderIds = new String[size];
		String[] types = new String[size];
		for (int i = 1; i <= size; i++) {
			orderIds[i - 1] = "" + i;
		}

		for (int i = 1; i <= size; i++) {
			// orderIds[i - 1] = "" + i;
			types[i - 1] = "T" + i;
		}

		SqlToyResult result = SqlConfigParseUtils.processSql(sql,
				new String[] { "types", "ids", "status", "beginDate", "staffName" },
				new Object[] { types, orderIds, new String[] { "1", "2" }, "2022-05-01", "张" });
		System.err.println(result.getSql());
		System.err.println(result.getParamsValue().length);
		System.err.println(JSON.toJSONString(result.getParamsValue()));
	}

	@Test
	public void testOverSizeIn1() throws Exception {
		String sql = "select * from table t where concat(t.order_id,t.type) in (:oderId)";
		String[] orderIds = new String[2000];
		for (int i = 1; i <= 2000; i++) {
			orderIds[i - 1] = "" + i;
		}
		SqlToyResult result = SqlConfigParseUtils.processSql(sql, new String[] { "oderId" }, new Object[] { orderIds });
		System.err.println(result.getSql());
		System.err.println(result.getParamsValue().length);
	}

	@Test
	public void testOverSizeIn3() throws Exception {
		String sql = "select * from table t where concat(t.order_id,t.type) in (?,?,?)";

		SqlToyResult result = SqlConfigParseUtils.processSql(sql, null, new Object[] { null, null, "S0003" });
		System.err.println(result.getSql());
		System.err.println(result.getParamsValue().length);
	}

	@Test
	public void testOverSizeIn4() throws Exception {
		String sql = "select * from table t where concat(t.order_id,t.type) in (:ids)";

		SqlToyResult result = SqlConfigParseUtils.processSql(sql, new String[] { "ids" },
				new Object[] { new Object[] {} });
		System.err.println(result.getSql());
		System.err.println(result.getParamsValue().length);
	}

	@Test
	public void testOverSizeIn5() throws Exception {
		String sql = "select * from table t where (t.order_id,t.type) in (?,?)";

		SqlToyResult result = SqlConfigParseUtils.processSql(sql, null, new Object[] { "S0001", "S0002" });
		System.err.println(result.getSql());
		System.err.println(result.getParamsValue().length);
	}

	@Test
	public void testOverSizeIn2() throws Exception {
		String sql = "select * from table t where 1=1 and (t.order_id||'\\('||'\\)') not in (:oderId))";
		String[] orderIds = new String[2000];
		for (int i = 1; i <= 2000; i++) {
			orderIds[i - 1] = "" + i;
		}
		SqlToyResult result = SqlConfigParseUtils.processSql(sql, new String[] { "oderId" }, new Object[] { orderIds });
		System.err.println(result.getSql());
		System.err.println(result.getParamsValue().length);
	}

	public static void main(String[] args) {
		String sql = "where 1=1 and concat(t.a,t.b) ";
		int paramIndex;
		String paramName = null;
		String regex = "[\\s\\(\\)\\}\\{\\]\\[]";
		if (sql.trim().endsWith(")")) {
			String reverseSql = new StringBuilder(sql).reverse().toString();
			// "concat(a,b)" 反转后 ")b,a(tacnoc" 找到对称的(符号位置
			int symIndex = StringUtil.getSymMarkIndex(")", "(", reverseSql, 0);
			int start = sql.length() - symIndex - 1;
			paramIndex = StringUtil.matchLastIndex(sql.substring(0, start), regex) + 1;
			paramName = sql.substring(paramIndex);
		}
		System.err.println("[" + paramName + "]");
	}

	@Test
	public void testInsertSql() throws Exception {
		String sql = "insert into table (id,name,status) values(:id,:name,:status)";
		SqlToyResult result = SqlConfigParseUtils.processSql(sql, new String[] { "id", "name", "status" },
				new Object[] { "1", null, "1" });
		System.err.println(JSON.toJSONString(result));
	}

	@Test
	public void testIf1() throws Exception {
		String sql = "where name=1 #[ @if(:flag==:flagValue||:flag1==:flagValue1) and #[status=:status]]";
//		SqlToyResult result = SqlConfigParseUtils.processSql(sql, new String[] { "flag", "status" },
//				new Object[] { "1", null });
//		System.err.println(JSON.toJSONString(result));
		SqlToyResult result = SqlConfigParseUtils.processSql(sql,
				new String[] { "flag", "flag1", "status", "flagValue", "flagValue1" },
				new Object[] { "1", "3", 1, "1", "2" });
		System.err.println(JSON.toJSONString(result));
	}

	@Test
	public void testOverSizeIn6() throws Exception {
		String sql = "select * from table t where t.biz_date >=:dates[0] and t.biz_date<=:dates[1]";

		SqlToyResult result = SqlConfigParseUtils.processSql(sql, new String[] { "dates" },
				new Object[] { new Object[] { "2022-10-1", "2022-10-30" } });
		System.err.println(result.getSql());
		System.err.println(result.getParamsValue().length);
	}

	@Test
	public void testLike() throws Exception {
		String sql = "select * from table t where t.name ilike :name and t.desc like :desc";

		SqlToyResult result = SqlConfigParseUtils.processSql(sql, new String[] { "name", "desc" },
				new Object[] { "张三", "验证" });
		System.err.println(result.getSql());
		for (Object obj : result.getParamsValue()) {
			System.err.println(obj);
		}
	}

	@Test
	public void testMatchNamedParam() throws Exception {
		String[] names = new String[] { "item[0]", "item[1]", "name", "status" };
		Object[] values = SqlConfigParseUtils.matchNamedParam(names, new String[] { "item", "name", "status" },
				new Object[] { new Object[] { "1", "2" }, "chen", 1 });

		for (Object obj : values) {
			System.err.println(obj);
		}
		String property = "item[120]";
		int lastIndex = property.lastIndexOf("[");
		String key = property.substring(0, lastIndex);
		int index = Integer.parseInt(property.substring(lastIndex + 1, property.length() - 1));
		System.err.println("key=[" + key + "] index=[" + index + "]");
	}

	@Test
	public void testNullValue1() throws Exception {
		String sql = "select * from table where a=? and b=?";
		SqlToyResult result = SqlConfigParseUtils.processSql(sql, null, new Object[] { "1", null });
		System.err.println(result.getSql());

		sql = "with tmp as (select * from table1 where id=? ) update table set a=? , b=? where c.name=?";
		result = SqlConfigParseUtils.processSql(sql, null, new Object[] { null, 1, null, null });
		System.err.println(result.getSql());

		sql = "with tmp as (select * from table1 where id=? ) update table SET a=? , b=? where c.name = ?";
		result = SqlConfigParseUtils.processSql(sql, null, new Object[] { null, null, 1, null });
		System.err.println(result.getSql());

		sql = "insert table a(f1,f2,f3,f4,f5) values(?,?,?,?,?)";
		result = SqlConfigParseUtils.processSql(sql, null, new Object[] { null, 1, null, 1, null });
		System.err.println(result.getSql());
	}
}
