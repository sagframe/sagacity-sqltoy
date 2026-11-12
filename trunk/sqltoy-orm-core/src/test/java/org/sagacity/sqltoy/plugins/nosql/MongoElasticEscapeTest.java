package org.sagacity.sqltoy.plugins.nosql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * nosql参数拼接的取值语义:框架不做值内转义,值按原样内联(调用方可能已按目标方自行转义,
 * 框架再转一次会造成二次转义改变值语义),该决策的边界由本测试固化为:
 * 1) 危险字符(引号/括号/$)仍按原样剔除;
 * 2) es sql模式的单引号仍按既有约定转义为\';
 * 3) 反斜杠(含值以\结尾)不做任何加工,原样内联
 */
public class MongoElasticEscapeTest {

	@Test
	public void mqlModeDangerCharsRemoved() {
		String result = MongoElasticOperations.replaceNoSqlParams("db.t.find({a:@(:x)})", new Object[] { "v'\"{}[]$" },
				true);
		assertEquals("db.t.find({a:'v'})", result);
	}

	@Test
	public void mqlModeBackslashInlinedAsIs() {
		// 值以\结尾时不转义:字面量收尾引号是否被吞由调用方与目标方言负责
		String result = MongoElasticOperations.replaceNoSqlParams("db.t.find({name:@(:name)})",
				new Object[] { "abc\\" }, true);
		assertEquals("db.t.find({name:'abc\\'})", result);
	}

	@Test
	public void mqlModeWindowsPathInlinedAsIs() {
		String result = MongoElasticOperations.replaceNoSqlParams("db.t.find({path:@(:path)})",
				new Object[] { "C:\\tmp\\a" }, false);
		assertEquals("db.t.find({path:C:\\tmp\\a})", result);
	}

	@Test
	public void esSqlModeQuoteEscapingUnchanged() {
		String result = MongoElasticOperations.replaceSqlParams("select * from t where name=:name",
				new Object[] { "it's" }, true);
		assertTrue(result.contains("name='it\\'s'"), "实际:" + result);
	}

	@Test
	public void esSqlModeBackslashInlinedAsIs() {
		String result = MongoElasticOperations.replaceSqlParams("select * from t where dir=:dir",
				new Object[] { "C:\\tmp\\" }, true);
		assertEquals("select * from t where dir='C:\\tmp\\'", result);
	}

	@Test
	public void esSqlNormalValueUnchanged() {
		assertEquals("select * from t where age=18",
				MongoElasticOperations.replaceSqlParams("select * from t where age=:age", new Object[] { 18 }, true));
	}
}
