package org.sagacity.sqltoy.plugins.function.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.utils.DataSourceUtils;

/**
 * 回归测试:DateFormat 的 mysql/clickhouse 分支对"带引号字面量"与"裸格式"必须给出同一token口径
 *
 * <p>
 * 历史问题:带引号格式串先经 FunctionUtils.toMySqlFormat 归一层(mm=月,HH邻接的小写mm=分钟),
 * 而裸格式(占位符/表达式形态)走分支内的replace链,只做无差别mm→%m,
 * 导致 to_char(t,'HHmmss') 得 %H%i%s、裸 to_char(t,HHmmss) 得 %H%m%s(月份),同一函数两种语义。
 * 现两处共用 FunctionUtils.toMySqlFormatTokens。
 */
public class DateFormatBareFormatConsistencyTest {

	private static String wrap(int dbType, String format) {
		return new DateFormat().wrap(dbType, "date_format", true, "t.time", format);
	}

	@Test
	public void mysqlBareAndQuotedAgreeOnMinute() {
		assertEquals("date_format(t.time,'%H%i%s')", wrap(DataSourceUtils.DBType.MYSQL, "'HHmmss'"));
		assertEquals("date_format(t.time,%H%i%s)", wrap(DataSourceUtils.DBType.MYSQL, "HHmmss"));
		assertEquals("date_format(t.time,%H%i)", wrap(DataSourceUtils.DBType.MYSQL, "HHmm"));
		assertEquals("date_format(t.time,%H%i)", wrap(DataSourceUtils.DBType.MYSQL, "HH24mm"));
	}

	@Test
	public void mysqlBareAndQuotedAgreeOnMonth() {
		assertEquals("date_format(t.time,'%Y%m%d')", wrap(DataSourceUtils.DBType.MYSQL, "'yyyyMMdd'"));
		assertEquals("date_format(t.time,%Y%m%d)", wrap(DataSourceUtils.DBType.MYSQL, "yyyyMMdd"));
		assertEquals("date_format(t.time,%Y-%m-%d)", wrap(DataSourceUtils.DBType.MYSQL, "yyyy-Mm-dd"));
		// Oracle HHMM(小时+月份)两种形态都要保留月份语义
		assertEquals("date_format(t.time,%H%m)", wrap(DataSourceUtils.DBType.MYSQL, "HHMM"));
	}

	@Test
	public void clickHouseBareAndQuotedAgree() {
		assertEquals("date_format(toDateTime(t.time),'%H%i%s')",
				wrap(DataSourceUtils.DBType.CLICKHOUSE, "'HHmmss'"));
		assertEquals("date_format(toDateTime(t.time),%H%i%s)", wrap(DataSourceUtils.DBType.CLICKHOUSE, "HHmmss"));
		assertEquals("date_format(toDateTime(t.time),%Y%m%d)", wrap(DataSourceUtils.DBType.CLICKHOUSE, "yyyyMMdd"));
	}

	@Test
	public void percentStyleStaysUntouched() {
		// 已是%风格的格式串不得被二次映射(引擎对%内容幂等)
		assertEquals("date_format(t.time,'%Y-%m-%d')", wrap(DataSourceUtils.DBType.MYSQL, "'%Y-%m-%d'"));
	}
}
