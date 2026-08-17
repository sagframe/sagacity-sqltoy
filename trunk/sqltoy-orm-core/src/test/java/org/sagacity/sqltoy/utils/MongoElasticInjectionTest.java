package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.SqlToyResult;

/**
 * 回归测试：(a)@value(:param)值替换经quoteReplacement+removeDangerWords,
 * 值含$/\不再异常或错误替换,危险字符被清洗;(b)es sql模式字符串值内单引号转义,不能逃出字面量
 */
public class MongoElasticInjectionTest {

	// 不匹配任何命名参数的空模式,使paramCnt恒为0,直接取第一个参数值
	private static final Pattern NO_NAMED = Pattern.compile("(?!x)x");

	private static String processValue(String sql, Object value) throws Exception {
		SqlToyResult result = new SqlToyResult(sql, new Object[] { value });
		java.lang.reflect.Method method = MongoElasticUtils.class.getDeclaredMethod("processValue", SqlToyResult.class,
				Pattern.class, boolean.class);
		method.setAccessible(true);
		method.invoke(null, result, NO_NAMED, false);
		return result.getSql();
	}

	@Test
	public void dollarAndBackslashValueNotBreakingReplacement() throws Exception {
		// 修复前:$被当作组引用抛IllegalArgumentException或错误替换,\触发非法转义
		String result = processValue("db.staff.find({name:@value(:name)})", "a$1b\\c");
		// $被removeDangerWords清洗,\经quoteReplacement保持字面量,无异常无组引用
		assertTrue(result.contains("a1b\\c"), "实际:" + result);
		assertFalse(result.contains("$"), "实际:" + result);
	}

	@Test
	public void dangerousCharsSanitized() throws Exception {
		// 引号/括号被removeDangerWords清洗,不能改写查询结构
		String result = processValue("db.t.find({a:@value(:x)})", "v'\"{}[]$");
		assertEquals("db.t.find({a:v})", result);
	}

	@Test
	public void normalValueAndNullUnchanged() throws Exception {
		assertEquals("db.t.find({a:100})", processValue("db.t.find({a:@value(:x)})", 100));
		assertEquals("db.t.find({a:null})", processValue("db.t.find({a:@value(:x)})", (Object) null));
	}

	@Test
	public void esSqlSingleQuoteEscaped() {
		// 修复前:'it's'直接断裂/逃出字面量;修复后:'it\'s'
		String result = MongoElasticUtils.replaceSqlParams("select * from t where name=:name", new Object[] { "it's" },
				true);
		assertTrue(result.contains("name='it\\'s'"), "实际:" + result);
		// 注入载荷被禁锢在字面量内
		assertFalse(result.contains("' or '1'='1"), "实际:" + result);
	}

	@Test
	public void esSqlNormalValueUnchanged() {
		String result = MongoElasticUtils.replaceSqlParams("select * from t where name=:name and age=:age",
				new Object[] { "tom", 18 }, true);
		assertTrue(result.contains("name='tom'") && result.contains("age=18"), "实际:" + result);
	}
}
