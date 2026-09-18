package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.SqlConfigParseUtils;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.FieldMeta;
import org.sagacity.sqltoy.model.SqlInjectionLevel;

import com.alibaba.fastjson2.JSON;

/**
 * SqlUtil sql处理工具的单元测试
 */
public class SqlUtilTest {

	@Test
	public void testConvertFieldsToCols() {
		// 显式清空保留字集合建立前置条件:集合为全局静态且put为累加合并,
		// 其他测试类(如DialectEdgeBatchTest)put的词会残留,导致convertWord对命中列默认[]包裹
		ReservedWordsUtil.clear();
		String sql = "staffName,`sexType`,name,bizStaffName from table where #[t.staffName like ?] and sexType=:sexType";
		sql = SqlUtil.convertFieldsToColumns(buildStaffEntityMeta("staff_info"), sql);
		assertEquals(
				"STAFF_NAME,`SEX_TYPE`,name,BIZ_STAFF_NAME from table where #[t.STAFF_NAME like ?] and SEX_TYPE=:sexType",
				sql.trim());
	}

	/**
	 * 反向场景:配置保留字后,convertWord(dbType=null)对命中列默认以[]包裹
	 * (生产上该占位符由convertSimpleSql按数据库方言二次替换);表名与其他用例不同以规避convertSqlMap缓存key
	 */
	@Test
	public void testConvertFieldsToColsWithReservedWords() {
		ReservedWordsUtil.clear();
		ReservedWordsUtil.put("staff_name,sex_type");
		String sql = "staffName,`sexType`,name,bizStaffName from table where #[t.staffName like ?] and sexType=:sexType";
		sql = SqlUtil.convertFieldsToColumns(buildStaffEntityMeta("staff_info_wrapped"), sql);
		assertEquals(
				"[STAFF_NAME],`SEX_TYPE`,name,BIZ_STAFF_NAME from table where #[t.[STAFF_NAME] like ?] and [SEX_TYPE]=:sexType",
				sql.trim());
	}

	private EntityMeta buildStaffEntityMeta(String tableName) {
		EntityMeta entityMeta = new EntityMeta();
		entityMeta.setTableName(tableName);
		HashMap<String, FieldMeta> fieldsMeta = new HashMap<String, FieldMeta>();
		FieldMeta staffMeta = new FieldMeta();
		staffMeta.setFieldName("staffName");
		staffMeta.setColumnName("STAFF_NAME");
		fieldsMeta.put("staffname", staffMeta);

		FieldMeta bizStaffMeta = new FieldMeta();
		bizStaffMeta.setFieldName("bizStaffName");
		bizStaffMeta.setColumnName("BIZ_STAFF_NAME");
		fieldsMeta.put("bizstaffname", bizStaffMeta);

		FieldMeta nameMeta = new FieldMeta();
		nameMeta.setFieldName("name");
		nameMeta.setColumnName("NAME");
		fieldsMeta.put("name", nameMeta);

		FieldMeta sexMeta = new FieldMeta();
		sexMeta.setFieldName("sexType");
		sexMeta.setColumnName("SEX_TYPE");
		fieldsMeta.put("sextype", sexMeta);
		entityMeta.setFieldsMeta(fieldsMeta);
		entityMeta.setFieldsArray(new String[] { "name", "staffName", "bizStaffName", "sexType" });
		return entityMeta;
	}

	/**
	 * 测试vo属性名称转表字段名称
	 */
	@Test
	public void testConvertFieldsToCols1() {
		String sql = "sexType=:sexType";
		EntityMeta entityMeta = new EntityMeta();
		entityMeta.setTableName("staff_info");
		HashMap<String, FieldMeta> fieldsMeta = new HashMap<String, FieldMeta>();

		FieldMeta sexMeta = new FieldMeta();
		sexMeta.setFieldName("sexType");
		sexMeta.setColumnName("SEX_TYPE");
		fieldsMeta.put("sextype", sexMeta);
		entityMeta.setFieldsMeta(fieldsMeta);
		entityMeta.setFieldsArray(new String[] { "sexType" });
		sql = SqlUtil.convertFieldsToColumns(entityMeta, sql);
		System.err.println(sql);
	}

	@Test
	public void testConvertFieldsToCols2() {
		String sql = " detail_id = :detailId and res_type = : resType";
		EntityMeta entityMeta = new EntityMeta();
		entityMeta.setTableName("staff_info");
		HashMap<String, FieldMeta> fieldsMeta = new HashMap<String, FieldMeta>();

		FieldMeta sexMeta = new FieldMeta();
		sexMeta.setFieldName("detailId");
		sexMeta.setColumnName("detail_id");
		fieldsMeta.put("detailid", sexMeta);

		FieldMeta resType = new FieldMeta();
		resType.setFieldName("resType");
		resType.setColumnName("res_type");
		fieldsMeta.put("restype", resType);

		entityMeta.setFieldsMeta(fieldsMeta);
		entityMeta.setFieldsArray(new String[] { "detailId", "resType" });
		String[] paramNames = SqlConfigParseUtils.getSqlParamsName(sql, false);
		sql = SqlUtil.convertFieldsToColumns(entityMeta, sql);
		System.err.println(JSON.toJSONString(paramNames));
		System.err.println(sql);
	}

	// ======================== convertFieldsToColumns 边界用例(update 2026-9-15) ========================
	// 说明:convertSqlMap为全局静态缓存(key=tableName+"_"+sql),以下用例统一使用独立表名
	// (staff_edge/staff_kw/staff_kw2等)规避与既有用例及其它测试类的缓存串扰;
	// 涉及convertWord的用例开头显式clear()建立保留字前置条件(与既有用例同款约定)

	/**
	 * 大小写不敏感匹配:sql中属性名任意大小写形态均应转换为列名
	 */
	@Test
	public void testConvertFieldsToCols_caseInsensitive() {
		ReservedWordsUtil.clear();
		EntityMeta entityMeta = buildStaffEntityMeta("staff_edge");
		assertEquals("STAFF_NAME like ? and STAFF_NAME is not null",
				SqlUtil.convertFieldsToColumns(entityMeta, "STAFFNAME like ? and StaffName is not null").trim());
	}

	/**
	 * 词边界:字母、数字、下划线紧邻粘连的形态均不得转换(避免误伤标识符片段)
	 */
	@Test
	public void testConvertFieldsToCols_wordBoundary() {
		ReservedWordsUtil.clear();
		EntityMeta entityMeta = buildStaffEntityMeta("staff_edge");
		String sql = "staffNameX=1,xstaffName=2,staffName2=3,_staffName=4,staffName_=5";
		assertEquals(sql, SqlUtil.convertFieldsToColumns(entityMeta, sql).trim());
	}

	/**
	 * 命名参数保护::field与: field(冒号+空白)形态均不转换,普通位置正常转换
	 */
	@Test
	public void testConvertFieldsToCols_namedParamGuard() {
		ReservedWordsUtil.clear();
		EntityMeta entityMeta = buildStaffEntityMeta("staff_edge");
		assertEquals("STAFF_NAME=:staffName",
				SqlUtil.convertFieldsToColumns(entityMeta, "staffName=:staffName").trim());
		assertEquals(": staffName=1", SqlUtil.convertFieldsToColumns(entityMeta, ": staffName=1").trim());
	}

	/**
	 * 括号语义:field(形态视为函数名不转换;count(field)括号内参数正常转换
	 */
	@Test
	public void testConvertFieldsToCols_functionAndParen() {
		ReservedWordsUtil.clear();
		EntityMeta entityMeta = buildStaffEntityMeta("staff_edge");
		assertEquals("count(STAFF_NAME)+staffName(x)",
				SqlUtil.convertFieldsToColumns(entityMeta, "count(staffName)+staffName(x)").trim());
	}

	/**
	 * 行尾字段:方法内部末尾补一位空白使tailChar判定不越界,精确断言返回值含该尾随空白(既有约定,调用方trim)
	 */
	@Test
	public void testConvertFieldsToCols_endOfString() {
		ReservedWordsUtil.clear();
		EntityMeta entityMeta = buildStaffEntityMeta("staff_edge");
		assertEquals("where STAFF_NAME ", SqlUtil.convertFieldsToColumns(entityMeta, "where staffName"));
	}

	/**
	 * 同一字段多次出现:非参数形态全部转换,命名参数形态保留
	 */
	@Test
	public void testConvertFieldsToCols_multipleOccurrences() {
		ReservedWordsUtil.clear();
		EntityMeta entityMeta = buildStaffEntityMeta("staff_edge");
		assertEquals("STAFF_NAME is null or STAFF_NAME like :staffName",
				SqlUtil.convertFieldsToColumns(entityMeta, "staffName is null or staffName like :staffName").trim());
	}

	/**
	 * 标识符引号前缀([、`、")直接拼接原始列名,绕过convertWord保留字包裹;裸字段仍按保留字包裹
	 */
	@Test
	public void testConvertFieldsToCols_quotedIdentifierBypassKeyword() {
		ReservedWordsUtil.clear();
		ReservedWordsUtil.put("staff_name");
		try {
			EntityMeta entityMeta = buildStaffEntityMeta("staff_kw");
			assertEquals("[STAFF_NAME]=1 and [STAFF_NAME]=2 and `STAFF_NAME`=3", SqlUtil
					.convertFieldsToColumns(entityMeta, "[staffName]=1 and staffName=2 and `staffName`=3").trim());
		} finally {
			// 清理本用例put的保留字,避免残留影响后续测试类(全局静态集合)
			ReservedWordsUtil.clear();
		}
	}

	/**
	 * 字段名与列名一致但列名为保留字时仍需处理(convertWord包裹),非保留字同名列则跳过
	 */
	@Test
	public void testConvertFieldsToCols_keywordSameNameField() {
		ReservedWordsUtil.clear();
		ReservedWordsUtil.put("name");
		try {
			EntityMeta entityMeta = buildStaffEntityMeta("staff_kw2");
			assertEquals("[NAME]=1 and SEX_TYPE=2",
					SqlUtil.convertFieldsToColumns(entityMeta, "name=1 and sexType=2").trim());
		} finally {
			ReservedWordsUtil.clear();
		}
	}

	/**
	 * 空白sql原样返回(null/空串/纯空白),不进入转换与缓存
	 */
	@Test
	public void testConvertFieldsToCols_blankSql() {
		EntityMeta entityMeta = buildStaffEntityMeta("staff_edge");
		assertEquals(null, SqlUtil.convertFieldsToColumns(entityMeta, null));
		assertEquals("", SqlUtil.convertFieldsToColumns(entityMeta, ""));
		assertEquals("   ", SqlUtil.convertFieldsToColumns(entityMeta, "   "));
	}

	/**
	 * 单引号字符串字面量内的字段名不转换(2026-9-15修复:检索改在maskLiterals等长掩码串上进行);
	 * 字面量外的字段名正常转换,含''成对转义与like字面量场景
	 */
	@Test
	public void testConvertFieldsToCols_stringLiteralNotConverted() {
		ReservedWordsUtil.clear();
		EntityMeta entityMeta = buildStaffEntityMeta("staff_edge");
		// 字面量内容恰为字段名:保持原样
		assertEquals("remark='staffName'",
				SqlUtil.convertFieldsToColumns(entityMeta, "remark='staffName'").trim());
		// 字面量内外同字段:仅外部转换
		assertEquals("STAFF_NAME='staffName'",
				SqlUtil.convertFieldsToColumns(entityMeta, "staffName='staffName'").trim());
		// ''成对转义:整个字面量(含转义段)内的字段名均不转换
		assertEquals("remark='it''s staffName' and STAFF_NAME=1",
				SqlUtil.convertFieldsToColumns(entityMeta, "remark='it''s staffName' and staffName=1").trim());
		// like字面量:通配串内的字段名不转换
		assertEquals("STAFF_NAME like '%staffName%'",
				SqlUtil.convertFieldsToColumns(entityMeta, "staffName like '%staffName%'").trim());
		// 字面量收尾后的字段正常转换(终结引号后回到sql正文)
		assertEquals("remark='x' and STAFF_NAME=1",
				SqlUtil.convertFieldsToColumns(entityMeta, "remark='x' and staffName=1").trim());
		// 字面量夹在两个转换字段之间:多轮字段替换下掩码串与原串平行拼接保持偏移对齐
		assertEquals("STAFF_NAME='lit' and SEX_TYPE=2",
				SqlUtil.convertFieldsToColumns(entityMeta, "staffName='lit' and sexType=2").trim());
	}

	/**
	 * 缓存key跨表碰撞回归(2026-9-15修复:key分隔符由"_"改为\u0000)——
	 * 表"staff"+sql"info_sexType=1"与表"staff_info"+sql"sexType=1"不再产生相同key,
	 * 后调用者按自身元数据正确转换,不再命中前者缓存
	 */
	@Test
	public void testConvertFieldsToCols_cacheKeyNoCollision() {
		ReservedWordsUtil.clear();
		EntityMeta metaA = buildStaffEntityMeta("staff");
		EntityMeta metaB = buildStaffEntityMeta("staff_info");
		// 先调用A:info_前缀下划线边界,sexType不转换,原样返回
		String resultA = SqlUtil.convertFieldsToColumns(metaA, "info_sexType=1");
		assertEquals("info_sexType=1", resultA.trim());
		// 后调用B:key不再碰撞,按B的元数据正常转换
		String resultB = SqlUtil.convertFieldsToColumns(metaB, "sexType=1");
		assertEquals("SEX_TYPE=1", resultB.trim());
		// 再次调用A:命中自身缓存仍返回正确结果
		assertEquals("info_sexType=1", SqlUtil.convertFieldsToColumns(metaA, "info_sexType=1").trim());
	}

	@Test
	public void testValidateSqlInArg() {
		String argValue = "'alter1 table'";
		System.err.println(SqlUtil.validateInArg(argValue));
	}

	@Test
	public void testGetFromIndex() {
		String sql = "select sum(a),(day from(ab)) as b from (select * from tableb) as b where (a+b)>5 and c.a>(1+5)";
		int fromIndex = SqlUtil.getSymMarkIndexExcludeKeyWords(sql, "select\\s+", "\\s+from[\\(\\s+]", 0);
		System.err.println(fromIndex);

		sql = "select sum(a),(day xxx(ab)) as b from (select * from tableb) as b where (a+b)>5 and c.a>(1+5)";
		fromIndex = SqlUtil.getSymMarkIndexExcludeKeyWords(sql, "select\\s+", "\\s+from[\\(\\s+]", 0);
		System.err.println(fromIndex);

		sql = "select a, b from (select * from tableb) as b where (a+b)>5 and c.a>(1+5)";
		fromIndex = SqlUtil.getSymMarkIndexExcludeKeyWords(sql, "select\\s+", "\\s+from[\\(\\s+]", 0);
		System.err.println(fromIndex);

		sql = "select a, b from (select * from tableb where 1=1 and t.m>100) as b where (a+b)>5 and c.a>(1+5)";
		fromIndex = SqlUtil.getSymMarkIndexExcludeKeyWords(sql, "\\s+from[\\(\\s+]", "\\s+where[\\(\\s+]", 0);
		System.err.println(fromIndex);
	}

	@Test
	public void testGetFromIndex1() {
		String sql = "select a, b from (select * from tableb where 1=1 and t.m>100) as b where (a+b)>5 and c.a>(1+5)";
		int fromIndex = SqlUtil.getSymMarkIndexExcludeKeyWords(sql, "select\\s+", "\\s+from[\\(\\s+]", 0);
		fromIndex = SqlUtil.getSymMarkIndexExcludeKeyWords(sql, "\\s+from[\\(\\s+]", "\\s+where[\\(\\s+]",
				fromIndex - 1);
		System.err.println(fromIndex);
	}

	@Test
	public void testUniformMarks() {
		String sql = """
				select *
				from sqltoy_order_info soi
				where 1=1
				-- @fast_start
				(
					select * from table1
				)
				-- @fast_end
				and status=1
								""";
		sql = SqlUtil.uniformFastMarks(sql);
		System.err.println(sql);
	}

	@Test
	public void testUniformMarks1() {
		String sql = """
				select *
				from sqltoy_order_info soi
				where 1=1
				/*@fast_start*/(
					select * from table1
				)/*@fast_end*/
				and status=1
								""";
		sql = SqlUtil.uniformFastMarks(sql);
		System.err.println(sql);
	}

	@Test
	public void testUniformMarksql2() {
		String sql = """
				select *
				from sqltoy_order_info soi
				where 1=1
				-- @fast_start
				(
					select * from table1
				)/*@fast_end*/
				and status=1
								""";
		sql = SqlUtil.uniformFastMarks(sql);
		System.err.println(sql);
	}

	@Test
	public void testSqlInjection() {
		System.err.println(SqlUtil.isSqlInjection(SqlInjectionLevel.SQL_KEYWORD, "select"));
		System.err.println(SqlUtil.isSqlInjection(SqlInjectionLevel.SQL_KEYWORD, "from"));
		System.err.println(SqlUtil.isSqlInjection(SqlInjectionLevel.SQL_KEYWORD, "order by"));
		System.err.println(SqlUtil.isSqlInjection(SqlInjectionLevel.SQL_KEYWORD, "or t.field>0"));
		System.err.println(SqlUtil.isSqlInjection(SqlInjectionLevel.SQL_KEYWORD, "or t.field between1"));
		System.err.println(SqlUtil.isSqlInjection(SqlInjectionLevel.SQL_KEYWORD, "/*+*/"));
		System.err.println(SqlUtil.isSqlInjection(SqlInjectionLevel.SQL_KEYWORD, "/**/"));
		System.err.println(SqlUtil.isSqlInjection(SqlInjectionLevel.SQL_KEYWORD, "sleep"));
		System.err.println(SqlUtil.isSqlInjection(SqlInjectionLevel.SQL_KEYWORD, "sleep("));
		System.err.println(SqlUtil.isSqlInjection(SqlInjectionLevel.SQL_KEYWORD, "sleep()"));
		System.err.println(SqlUtil.isSqlInjection(SqlInjectionLevel.SQL_KEYWORD, "sleep(10)"));
		System.err.println("=====================================================");
		System.err.println(StringUtil.matches("sleep(5)", SqlUtil.FUNCTION_PATTERN));
		System.err.println(SqlUtil.isSqlInjection(SqlInjectionLevel.SQL_KEYWORD, "sleep(5)"));
		Pattern pattern = Pattern.compile("(?i)\\b(or|and)\\s+[\\w\\W]+(>|>=|<>|=|<|<=|(is\\s+)|!=)\\s*");
		System.err.println(StringUtil.matches("or 'a' is", pattern));
		Pattern SQL_KEYWORD_PATTERN1 = Pattern.compile(
				"(?i)\\b(SELECT|INSERT|UPDATE|DELETE|DROP|CREATE|ALTER|UNION|JOIN|WHERE|FROM|DISTINCT|EXECUTE|EXEC|HAVING|(TRUNCATE\\s+TABLE)|(ORDER\\s+BY)|(GROUP\\s+BY)|(MERGE\\s+INTO)|(LIMIT\\s+\\d+)|(OFFSET\\s+\\d+))\\b");
		Pattern[] SQL_INJECTION_KEY_WORDS1 = { SqlUtil.FUNCTION_PATTERN, SqlUtil.COMMENT_PATTERN,
				SqlUtil.SQL_KEYWORD_PATTERN };

		System.err.println(StringUtil.matches("selec+", SqlUtil.SQL_KEYWORD_PATTERN));
		System.err.println(StringUtil.matches("group by ", SQL_KEYWORD_PATTERN1));
		System.err.println(StringUtil.matches("group by1", SQL_KEYWORD_PATTERN1));
		System.err.println(StringUtil.matches("group by+", SQL_KEYWORD_PATTERN1));
		System.err.println(StringUtil.matches("limit ", SQL_KEYWORD_PATTERN1));
		System.err.println(StringUtil.matches("limit+10", SQL_KEYWORD_PATTERN1));
		System.err.println(StringUtil.matches("limit 10", SQL_KEYWORD_PATTERN1));
		System.err.println("=====================================================");
		Pattern CONDITION_PATTERN = Pattern
				.compile("(?i)\\b((or|and)\\s+)?[\\w\\W]+(>|>=|<>|=|<|<=|!=|(is\\s+)|between)\\s*");

		System.err.println(StringUtil.matches("or t.field<>0", CONDITION_PATTERN));
		System.err.println(StringUtil.matches("or field<>0", CONDITION_PATTERN));
		System.err.println(StringUtil.matches("field<>0", CONDITION_PATTERN));
		System.err.println(StringUtil.matches("or t.field<>0", CONDITION_PATTERN));
		System.err.println(StringUtil.matches("or t.field is ", CONDITION_PATTERN));
		System.err.println(StringUtil.matches("or t.field ", CONDITION_PATTERN));
		System.err.println(StringUtil.matches("or t.field between", CONDITION_PATTERN));
		System.err.println(StringUtil.matches("or t.field>", CONDITION_PATTERN));
		System.err.println("=====================================================");
		// Pattern COMMENT_PATTERN =
		// Pattern.compile("(?i)\\/\\*\\s*\\+[\\w\\W]*\\*\\/");
		System.err.println(StringUtil.matches("/*+ */", SqlUtil.COMMENT_PATTERN));
		System.err.println(StringUtil.matches("/*+(", SqlUtil.COMMENT_PATTERN));
		System.err.println(StringUtil.matches("/*+*/", SqlUtil.COMMENT_PATTERN));
		System.err.println(StringUtil.matches("/* + chen*/", SqlUtil.COMMENT_PATTERN));
		System.err.println(StringUtil.matches("/*+(*/", SqlUtil.COMMENT_PATTERN));
		System.err.println("=====================================================");
		Pattern RELAXED_WORD = Pattern.compile("^[a-zA-Z0-9_-\u4e00-\u9fa5\\.\\%'\"@\\[\\]\\（\\）\\【\\】]+$");
		System.err.println(StringUtil.matches("chen.abc", RELAXED_WORD));
		System.err.println(StringUtil.matches("chen.abc ", RELAXED_WORD));
		System.err.println(StringUtil.matches("'chen.abc", RELAXED_WORD));
		System.err.println(StringUtil.matches("'chen>abc", RELAXED_WORD));
		System.err.println(StringUtil.matches("'chen%abc", RELAXED_WORD));
		System.err.println(StringUtil.matches("'chen-abc_f", RELAXED_WORD));
		System.err.println(StringUtil.matches("'chen@abc.com]", RELAXED_WORD));
		System.err.println(StringUtil.matches("'chen@abc.com)", RELAXED_WORD));
		System.err.println(StringUtil.matches("'chen@abc.com（）", RELAXED_WORD));
	}

	@Test
	public void testSqlInjectionStrictWord() {
		System.err.println(SqlUtil.isSqlInjection(SqlInjectionLevel.STRICT_WORD, "select"));
		System.err.println(SqlUtil.isSqlInjection(SqlInjectionLevel.STRICT_WORD, "sel ect"));
		System.err.println(SqlUtil.isSqlInjection(SqlInjectionLevel.STRICT_WORD, "sel+ect"));
		System.err.println(SqlUtil.isSqlInjection(SqlInjectionLevel.STRICT_WORD, "sel-ect"));
		System.err.println(SqlUtil.isSqlInjection(SqlInjectionLevel.STRICT_WORD, "t1.name"));
	}

	@Test
	public void testSqlInjectionSqlKeyWord() {
		System.err.println(SqlUtil.isSqlInjection(SqlInjectionLevel.SQL_KEYWORD, "S0009()"));
		String sql = "select * from table group by id order by name desc";
		String sqlPart = " where tenant_id=2 ";
		int groupByIndex = StringUtil.matchIndex(sql, SqlUtil.GROUP_BY_PATTERN);
		// \\Worder,匹配到位置要往后移1位
		int orderByIndex = StringUtil.matchIndex(sql, SqlUtil.ORDER_BY_PATTERN);
		if (groupByIndex < 0) {
			if (orderByIndex < 0) {
				sql = sql.concat(sqlPart);
			} else {
				sql = sql.substring(0, orderByIndex + 1).concat(sqlPart).concat(sql.substring(orderByIndex + 1));
			}
		} else {
			sql = sql.substring(0, groupByIndex + 1).concat(sqlPart).concat(sql.substring(groupByIndex + 1));
		}
		System.err.println("[" + sql + "]");
	}

	@Test
	public void testSqlInjectionRELAXEDWord() {
		System.err.println(SqlUtil.isSqlInjection(SqlInjectionLevel.RELAXED_WORD, "select"));
		System.err.println(SqlUtil.isSqlInjection(SqlInjectionLevel.RELAXED_WORD, "sel ect"));
		System.err.println(SqlUtil.isSqlInjection(SqlInjectionLevel.RELAXED_WORD, "sel+ect"));
		System.err.println(SqlUtil.isSqlInjection(SqlInjectionLevel.RELAXED_WORD, "sel-ect"));
		System.err.println(SqlUtil.isSqlInjection(SqlInjectionLevel.RELAXED_WORD, "sel-ect（"));
		System.err.println(SqlUtil.isSqlInjection(SqlInjectionLevel.RELAXED_WORD, "sel-ect("));
		System.err.println(SqlUtil.isSqlInjection(SqlInjectionLevel.RELAXED_WORD, "s中文el-ect("));
		System.err.println(SqlUtil.isSqlInjection(SqlInjectionLevel.RELAXED_WORD, "sum{"));
		System.err.println(SqlUtil.isSqlInjection(SqlInjectionLevel.RELAXED_WORD, "t.name"));
	}

	@Test
	public void testReplaceEmbedSqlParams() {
		// String sql = "select * from user where name = ${:name} and age = ${age} and
		// id = ${ userId }";
		String sql = "select * from user where name = :name and age = age and id = userId";
		String result = SqlUtil.replaceEmbedSqlParams(sql);
		System.out.println(result);
	}

	@Test
	public void testClearDefaultValue() {
		String[] tests = { "3.14::real", "3.14::double precision", "name::varchar(50)", "price::numeric(10,2)", // 带逗号
				"age::int", "col::char(10)", "create_time::timestamp(6)", "now()::text", // 不会误删函数括号
				"id::bigint", "3.14::double precision(10,2)", "3.14::double precision(10,2) as ", """
								CASE order_status
						   WHEN 0 THEN '待支付'::text
						   WHEN 1 THEN '已支付'::text
						   WHEN 2 THEN '已完成'::text
						   ELSE '取消'::text
						END
								""", "(a+b)::numberic(10,2)", "NULL::text" };
		for (String t : tests) {
			System.out.println(t + "  ->  [" + SqlUtil.clearDefaultValue(t) + "]");
		}
	}

	public static void main(String[] args) {
		String sql = "select * from table group by id order by name desc";
		String sqlPart = " where tenant_id=2 ";
		int groupByIndex = StringUtil.matchIndex(sql, SqlUtil.GROUP_BY_PATTERN);
		// \\Worder,匹配到位置要往后移1位
		int orderByIndex = StringUtil.matchIndex(sql, SqlUtil.ORDER_BY_PATTERN);
		if (groupByIndex < 0) {
			if (orderByIndex < 0) {
				sql = sql.concat(sqlPart);
			} else {
				sql = sql.substring(0, orderByIndex + 1).concat(sqlPart).concat(sql.substring(orderByIndex + 1));
			}
		} else {
			sql = sql.substring(0, groupByIndex + 1).concat(sqlPart).concat(sql.substring(groupByIndex + 1));
		}
		System.err.println("[" + sql + "]");
	}
}
