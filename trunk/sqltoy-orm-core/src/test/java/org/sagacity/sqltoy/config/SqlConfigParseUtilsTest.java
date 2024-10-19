package org.sagacity.sqltoy.config;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.config.model.LinkModel;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlToyResult;
import org.sagacity.sqltoy.model.MapKit;
import org.sagacity.sqltoy.utils.FileUtil;
import org.sagacity.sqltoy.utils.SqlUtil;
import org.sagacity.sqltoy.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.alibaba.fastjson2.JSON;

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
	public void testParseShowCaseSql() throws Exception {
		String sqlFile = "classpath:scripts/showcase.sql.xml";
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
			System.err.println(JSON.toJSONString(config));
		}
	}

	@Test
	public void testSqlToyConfigClone() throws Exception {
		SqlToyConfig sqltoyConfig = new SqlToyConfig("1000", "select * from table");
		LinkModel linkModel = new LinkModel();
		linkModel.setColumns(new String[] { "id", "name" });
		sqltoyConfig.setLinkModel(linkModel);

		SqlToyConfig sqltoy1 = sqltoyConfig.clone();
		LinkModel linkModel1 = new LinkModel();
		linkModel1.setColumns(new String[] { "sexType", "name" });
		sqltoy1.setLinkModel(linkModel1);
		System.err.println(JSON.toJSONString(sqltoyConfig.getLinkModel()));
		System.err.println(JSON.toJSONString(sqltoy1.getLinkModel()));
	}

	@Test
	public void testNull() throws Exception {
		String sql = "select * from table where 1=1 #[and id=:id and name like  :name] #[and status=:status]";
		SqlToyResult result = SqlConfigParseUtils.processSql(sql, new String[] { "id", "name", "status" },
				new Object[] { "1", "chen", "1" });
		System.err.println(JSON.toJSONString(result));
	}

	@Test
	public void testNull1() throws Exception {
		String sql = "select * from table where #[id=:id ] #[ status=:status]";
		SqlToyResult result = SqlConfigParseUtils.processSql(sql, new String[] { "id", "status" },
				new Object[] { null, "1" });
		System.err.println(JSON.toJSONString(result));
	}

	@Test
	public void testNull2() throws Exception {
		String sql = "select * from table where #[id=:id and] status=:status limit10";
		SqlToyResult result = SqlConfigParseUtils.processSql(sql, new String[] { "id", "status" },
				new Object[] { null, 1 });
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
	public void testLoopNull() throws Exception {
		String sql = "update table set name=:name #[,@loop-full(:status,{ t2.\"status\"=:status[i]},{,})]";
		SqlToyResult result = SqlConfigParseUtils.processSql(sql, new String[] { "name", "status" },
				new Object[] { "chenfenfei", new Object[] { "1", null, "3" } });
		System.err.println(JSON.toJSONString(result));

	}

	@Test
	public void testLoop1() throws Exception {
		String sql = "@loop(:cols,\":cols[i]\",\",\")";
		SqlToyResult result = SqlConfigParseUtils.processSql(sql, new String[] { "cols", "name" },
				new Object[] { new Object[] { "field1", "field2", "field3" }, "chen" });
		System.err.println(JSON.toJSONString(result));

	}

	@Test
	public void testLoopTsName() throws Exception {
		String sql = "select t.value \"field_2\",\r\n" + "                u.dname \"field_3\",\r\n"
				+ "                @loop(:fields,\"\r\n"
				+ "                    sum(case when field_16 = :year then :fields[i] else 0 end) \"this_:fields[i]\",\r\n"
				+ "                    sum(case when field_16 = :lastYear then :fields[i] else 0 end) \"last_:fields[i]\",\r\n"
				+ "                    sum(case when field_16 = :year then :fields[i] else 0 end) - sum(case when field_16 = :lastYear then :fields[i] else 0 end) \"sub_:fields[i]\",\r\n"
				+ "                \")\r\n" + "                field_16 \"field_16\"\r\n"
				+ "        from dm_dtab_0153\r\n" + "        left join org_unit u on u.id = field_3\r\n"
				+ "        left join tip_enum t on t.id = field_2\r\n"
				+ "        where field_16 in (:lastYear, :year)\r\n" + "            and field_17 = :period\r\n"
				+ "            group by field_16,u.dname,t.value";
		SqlToyResult result = SqlConfigParseUtils.processSql(sql,
				new String[] { "fields", "year", "period", "lastYear" },
				new Object[] { new String[] { "name", "sex_type", "id", "birthday" }, "2022", "Q4", "2023" });
		System.err.println(result.getSql());

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
	public void testGetParamNamesOrder() throws Exception {
		String sql = "select * from sqltoy_staff_info where STAFF_NAME=to-date('2024-03-02 21:12:32','yyyy-MM-dd HH:mm:ss') order by :staffCode :orderWay";
		String[] result = SqlConfigParseUtils.getSqlParamsName(sql, true);
		System.err.println(JSON.toJSONString(result));
	}

	@Test
	public void testGetParamNames1() throws Exception {
		String sql = "select * from sqltoy_fruit_order where fruit_name = :limitList[0].fruitName or fruit_name = :limitList[1].fruit_name or fruit_name = :limitList[2]";
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
		String sql = "where name=1 #[ @if(:flag||:flag1==:flagValue1||3==:flag1) and #[status=:status]]";
//		SqlToyResult result = SqlConfigParseUtils.processSql(sql, new String[] { "flag", "status" },
//				new Object[] { "1", null });
//		System.err.println(JSON.toJSONString(result));
		SqlToyResult result = SqlConfigParseUtils.processSql(SqlUtil.clearMark(sql),
				new String[] { "flag", "flag1", "status", "flagValue", "flagValue1" },
				new Object[] { false, "3", 1, "5", "2" });
		System.err.println(JSON.toJSONString(result));
	}

	@Test
	public void testIfElse() throws Exception {
		String sql = """
				select * from table where name=1
				#[@if(:flag==1) #[and status=:status] #[and saleType is not :saleType] ]
				#[@elseif(:flag==2) and name like :name]
				#[@else and orderType=:orderType]
				""";
		SqlToyResult result = SqlConfigParseUtils.processSql(SqlUtil.clearMark(sql),
				new String[] { "flag", "status", "name", "orderType", "saleType" },
				new Object[] { 2, 1, "陈", "SALE", null });
		System.err.println(JSON.toJSONString(result));
	}

	@Test
	public void testInnerIfElse() throws Exception {
		String sql = """
				select * from table where name=1
				#[@if(:flag==1)
				    #[@if(:operateType==1) and status=:status]
				    #[@elseif(:operateType==2) and saleType is not :saleType]
				    #[@else and saleType is :saleType]
				]
				#[@elseif(:flag==2) and name like :name]
				#[@else and orderType=:orderType]
				""";
		SqlToyResult result = SqlConfigParseUtils.processSql(SqlUtil.clearMark(sql),
				new String[] { "flag", "status", "name", "orderType", "saleType", "operateType" },
				new Object[] { 1, 1, "张", "SALE", null, 1 });
		System.err.println(JSON.toJSONString(result));
	}

	@Test
	public void testMultiInnerIfElse() throws Exception {
		String sql = """
				select * from table where 1=1
				#[@if(:flag==1) and status=:status
				    #[@if(:operateType==2) and saleType is not :saleType]
				    #[@else and saleType is :saleType]
				]
				#[@elseif(:flag==2) and name like :name]
				#[@else and orderType=:orderType]
				#[@if(:tenantId==4) and tenant=1]
				#[@elseif(:tenantId==3) and tenant=3]
				""";
		SqlToyResult result = SqlConfigParseUtils.processSql(SqlUtil.clearMark(sql),
				new String[] { "flag", "status", "name", "orderType", "saleType", "operateType", "tenantId" },
				new Object[] { 1, 1, "张", "SALE", null, 4, 3 });
		System.err.println(JSON.toJSONString(result));
	}

	@Test
	public void testMultiInnerIfElse1() throws Exception {
		String sql = """
				select * from table where 1=1
				#[@if(:flag==1) and name like :name]
				#[@elseif(:flag==2)
				 	#[@if(:operateType==1) and status=:status]
				    #[@elseif(:operateType==2) and saleType is not :saleType]
				    #[@else and saleType is :saleType]
				]
				#[@else and orderType=:orderType]
				#[@if(:tenantId==4) and tenant=1]
				#[@elseif(:tenantId==3) and tenant=3]
				""";
		SqlToyResult result = SqlConfigParseUtils.processSql(SqlUtil.clearMark(sql),
				new String[] { "flag", "status", "name", "orderType", "saleType", "operateType", "tenantId" },
				new Object[] { 2, 1, "陈", "SALE", null, 4, 3 });
		System.err.println(JSON.toJSONString(result));
	}

	@Test
	public void testReversIndex() throws Exception {
		String sql = """
				select * from table where name=1
				#[@if(:flag==1)
				    and status=1

				    -- 下面的都不成立，或参数都为null,前面的and status=1 会留下

				    #[@if(:operateType==1) and status=:status]
				    #[@elseif(:operateType==2) and saleType is not :saleType]
				    --- saleType 为null，去除
				    #[@else and saleType=:saleType]
				]
				#[@elseif(:flag==2) and name like :name]
				#[@else and orderType=:orderType]
				""";
		sql = SqlUtil.clearMark(sql);
		System.err.println("#[@if(:flag==1) index=" + sql.indexOf("#[@if(:flag==1)"));
		System.err.println("A=" + sql.lastIndexOf("A"));
		// System.err.println(StringUtil.getSymMarkReverseIndex("#[", "]", sql,
		// sql.lastIndexOf("A")));
		System.err.println("]=" + sql.lastIndexOf("]"));
		System.err.println("else" + sql.lastIndexOf("#[@else "));
		System.err.println(StringUtil.getSymMarkReverseIndex("#[", "]", sql, sql.lastIndexOf("]") + 1));
	}

	@Test
	public void testIfElse1() throws Exception {
		String sql = """
				select * from table where name=1
				-- 非if逻辑场景下,内部动态参数为null，最终为and status=1 也要自动剔除
				#[and status=1 #[and type=:type] #[and orderName like :orderName] ]


				-- flag==1成立，因为内容存在动态参数，所以继续变成#[and status=:status]
				#[@if(:flag==1) and status=:status]

				-- 成立,因为and status=1 没有动态参数,直接拼接:  and status=1
				#[@if(:flag==1) and status=1 ]

				#[@else and status in (1,2)]

				""";
		SqlToyResult result = SqlConfigParseUtils.processSql(SqlUtil.clearMark(sql),
				new String[] { "flag", "status", "type" }, new Object[] { 1, null, null });
		System.err.println(JSON.toJSONString(result));
	}

	@Test
	public void testOverSizeIn6() throws Exception {
		String sql = "select * from table where name=1\n#[@if(?==1) and #[status=?]]\n ";

		System.err.println("length=" + sql.length() + "lastIndex=" + sql.lastIndexOf("]"));
	}

	@Test
	public void testLike() throws Exception {
		String sql = "select * from table t where t.name ilike N:name and t.desc like :desc";

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

	@Test
	public void testUpdateNull() throws Exception {
		String sql = "update table set t1.'field'=? where name=? and sex=?";
		SqlToyResult result = SqlConfigParseUtils.processSql(sql, null, new Object[] { null, null, null });
		System.err.println(result.getSql());
	}

	@Test
	public void testMultBlank() throws Exception {
		String sql = "select * from view_lowcode_postion where 1=1 #[@blank(:roleId) and role_id=:roleId]"
				+ "#[@blank(:id) and id=:id]";
		SqlToyResult result = SqlConfigParseUtils.processSql(sql, new String[] { "roleId", "id" },
				new Object[] { "a", null });
		System.err.println(result.getSql());
	}

	// 如何解决@loop() 循环中存在in (:ids) ids数据超过1000的问题，用@include(:sqlScript) 来替换loop,
	@Test
	public void testDynamicInclude() throws Exception {
		// 会先变成select * from view_lowcode_postion where 1=1 and equipId1 in (:equipId1)
		// and equipId2 in (:equipId2)"
		// 当变成常规的in时候，sqltoy会自动解决in参数超过1000个的问题
		String sql = "select * from view_lowcode_postion where 1=1 @include(:sqlScript)";

		// 演示，不具体赋值，结构就是List<Object[]>
		List<Object[]> multiInArray = new ArrayList();
		Map params = new HashMap();
		// 构造sqlScript，里面包含条件参数
		String sqlScript = "";
		for (int i = 0; i < multiInArray.size(); i++) {
			sqlScript = sqlScript.concat("and emp.equipId" + i + " in (:equipId" + i + ")");
			// 动态将sqlScript里面的条件参数和值放入map
			params.put("equipId" + i, multiInArray.get(i));
		}
		params.put("sqlScript", sqlScript);

	}
}
