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

public class SqlUtilTest {

	@Test
	public void testConvertFieldsToCols() {
		String sql = "staffName,`sexType`,name,bizStaffName from table where #[t.staffName like ?] and sexType=:sexType";
		EntityMeta entityMeta = new EntityMeta();
		entityMeta.setTableName("staff_info");
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
		sql = SqlUtil.convertFieldsToColumns(entityMeta, sql);
		assertEquals(sql.trim(),
				"STAFF_NAME,`SEX_TYPE`,name,BIZ_STAFF_NAME from table where #[t.STAFF_NAME like ?] and SEX_TYPE=:sexType");
	}

	/**
	 * @TODO 测试vo属性名称转表字段名称
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

	@Test
	public void testValidateSqlInArg() {
		String argValue = "'alter1 table'";
		boolean hasSqlKeyWord = StringUtil.matches(" " + argValue, SqlUtil.SQL_INJECT_PATTERN);
		System.err.println(hasSqlKeyWord);
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
	}
}
