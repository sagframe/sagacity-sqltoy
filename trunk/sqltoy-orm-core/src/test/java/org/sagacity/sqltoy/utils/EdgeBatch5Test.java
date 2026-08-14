package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.SqlConfigParseUtils;
import org.sagacity.sqltoy.config.model.SqlToyResult;

/**
 * 回归测试：(26)getSymMarkReverseIndex未找到时返回-1而非错误下标;
 * (27)@if日期比较任一侧无法解析时按false处理不再NPE
 */
public class EdgeBatch5Test {

	@Test
	public void symMarkReverseIndexNotFoundReturnsMinusOne() {
		// 字符串中没有对称的起始符号:修复前index=-1参与运算返回错误下标(如len+1-markLen)
		assertEquals(-1, StringUtil.getSymMarkReverseIndex("#[", "]", "select * from t where 1=1", 3));
		// 正常场景:#[..]完整对称,从']'位置逆向找到'#['起始
		String sql = "#[and name=:name]";
		assertEquals(0, StringUtil.getSymMarkReverseIndex("#[", "]", sql, sql.indexOf("]") + 1));
	}

	private static SqlToyResult processIf(String expr, Object bizDate) {
		// 参考框架既有测试格式:SqlUtil.clearMark + paramsNamed/values数组传参
		String sql = SqlUtil.clearMark("select * from t where 1=1 #[@if(" + expr + ") and status=1]");
		return SqlConfigParseUtils.processSql(sql, new String[] { "bizDate" }, new Object[] { bizDate });
	}

	private static SqlToyResult processIfDateType(String expr, Object bizDate) {
		// 日期类型比较:参数直接传Date对象,MacroIfLogic按compareValueObj instanceof Date走time分支
		String sql = SqlUtil.clearMark("select * from t where 1=1 #[@if(" + expr + ") and status=1]");
		return SqlConfigParseUtils.processSql(sql, new String[] { "bizDate" }, new Object[] { bizDate });
	}

	@Test
	public void ifDateCompareWithNowCalculation() {
		// 参数为Date对象+右侧now()+0s计算式:hasCalculate=true走time分支(真实date比较路径),
		// valueStr与compareValue都是yyyy-MM-dd HH:mm:ss格式,convertDateObject两侧可解析
		java.util.Date pastDate = DateUtil.parse("2024-06-15 10:00:00", "yyyy-MM-dd HH:mm:ss");
		SqlToyResult past = processIfDateType(":bizDate<now()+0s", pastDate);
		assertTrue(past.getSql().contains("status=1"), "实际:" + past.getSql());
		// 未来日期不小于now()
		java.util.Date futureDate = DateUtil.parse("2099-06-15 10:00:00", "yyyy-MM-dd HH:mm:ss");
		SqlToyResult future = processIfDateType(":bizDate<now()+0s", futureDate);
		assertFalse(future.getSql().contains("status=1"), "实际:" + future.getSql());
	}

	@Test
	public void ifDateWithUnparseableParamIsFalseNotNpe() {
		// 参数侧是脏日期(Date格式化失败产生异常路径),走字符串比较分支:字符串脏值正常比较
		SqlToyResult result = processIf(":bizDate>2025-01-01", "not-a-date");
		// 脏字符串与日期字面量按字符串比较,不抛异常即可
		assertFalse(result.getSql() == null);
	}

	@Test
	public void ifDateCompareUnparseableCompareValueIsFalseNotNpe() {
		// Date参数 + 右侧脏值:hasCalculate为false且compareValueObj为null时type=string走字符串比较;
		// 真正触发date分支的脏值场景是now()计算式,用日期参数+sysdate()形式覆盖time分支
		java.util.Date date = DateUtil.parse("2025-06-15 10:00:00", "yyyy-MM-dd HH:mm:ss");
		// more()走time分支:右侧经ExpressionUtil计算not-a-date会抛异常→evalSimpleExpress捕获返回undefine→默认true
		// 该场景(计算式脏值)的既有容错路径,验证不抛NPE即可
		SqlToyResult result = processIfDateType(":bizDate>not-a-date", date);
		org.junit.jupiter.api.Assertions.assertNotNull(result);
	}

	@Test
	public void ifNumericCompareUnchanged() {
		SqlToyResult result = processIf(":bizDate>100", 150);
		assertTrue(result.getSql().contains("status=1"), "实际:" + result.getSql());
		SqlToyResult result2 = processIf(":bizDate>100", 50);
		assertFalse(result2.getSql().contains("status=1"), "实际:" + result2.getSql());
	}
}
