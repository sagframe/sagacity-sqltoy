package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.SqlXMLConfigParse;
import org.sagacity.sqltoy.config.model.ParamFilterModel;
import org.sagacity.sqltoy.config.model.SqlToyConfig;

/**
 * 回归测试：to-date过滤器increment-time以${paramName}方式引用动态参数
 * (如increment-time="${incrementDaysBefore}")时:
 * (a)被引用参数名必须进入getFullParamNames()合集(否则参数值在装配阶段被丢弃,增量静默失效);
 * (b)filterValue中引用参数取值与日期增量计算端到端生效
 */
public class ToDataDynIncrementTest {

	private static ParamFilterModel toDateFilter(String param, String incrementTime) {
		ParamFilterModel filter = new ParamFilterModel();
		filter.setFilterType("to-date");
		filter.setParams(new String[] { param });
		filter.setFormat("yyyyMMdd");
		filter.setIncrementTime(incrementTime);
		return filter;
	}

	@Test
	public void dynIncrementParamIncludedInFullParamNames() {
		// 模拟截图场景:sql中仅有startDate/endDate,无cache-arg定义
		SqlToyConfig config = new SqlToyConfig("check_bidPalletStartTimeScope",
				"select * from logistics_pallet hp where hp.start_time>=:startDate and hp.start_time<:endDate");
		ParamFilterModel cloneFilter = new ParamFilterModel();
		cloneFilter.setFilterType("clone");
		cloneFilter.setParams(new String[] { "endDate" });
		cloneFilter.setParam("endDate");
		cloneFilter.setUpdateParams(new String[] { "startDate" });
		// increment-time已经过clearParamSign剥离${}
		config.addFilters(Arrays.asList(cloneFilter,
				toDateFilter("startDate", "incrementDaysBefore"), toDateFilter("endDate", "1")));
		String[] fullNames = config.getFullParamNames();
		boolean found = false;
		for (String name : fullNames) {
			if ("incrementDaysBefore".equalsIgnoreCase(name)) {
				found = true;
				break;
			}
		}
		// 修复前:cacheArgNames为空时提前返回paramsName,被引用参数名不在合集内
		assertTrue(found, "getFullParamNames应包含filters中increment-time引用的动态参数名!");
		// 固定数字增量(如1)不应被加入参数合集
		for (String name : fullNames) {
			assertTrue(!"1".equals(name), "纯数字increment-time不应进入参数名合集!");
		}
	}

	@Test
	public void dynIncrementEndToEnd() {
		ParamFilterModel cloneFilter = new ParamFilterModel();
		cloneFilter.setFilterType("clone");
		cloneFilter.setParams(new String[] { "endDate" });
		cloneFilter.setParam("endDate");
		cloneFilter.setUpdateParams(new String[] { "startDate" });

		// 参数名数组模拟修复后fullParamNames装配结果(包含被引用的incrementDaysBefore)
		Object[] result = ParamFilterUtils.filterValue(null,
				new String[] { "startDate", "endDate", "incrementDaysBefore" },
				new Object[] { null, "20260826", -7 },
				Arrays.asList(cloneFilter, toDateFilter("startDate", "incrementDaysBefore"),
						toDateFilter("endDate", "1")));
		// startDate由endDate克隆后按引用参数-7天回溯:2026-08-19
		assertTrue(result[0] instanceof LocalDate);
		assertEquals(LocalDate.of(2026, 8, 19), result[0]);
		// endDate固定+1天:2026-08-27,配合< :endDate覆盖endDate全天
		assertEquals(LocalDate.of(2026, 8, 27), result[1]);
	}

	@Test
	public void numericIncrementStillWorks() {
		Object[] result = ParamFilterUtils.filterValue(null, new String[] { "startDate", "endDate" },
				new Object[] { null, "20260826" },
				Arrays.asList(cloneEndToStart(), toDateFilter("startDate", "1"), toDateFilter("endDate", "1")));
		assertEquals(LocalDate.of(2026, 8, 27), result[0]);
		assertEquals(LocalDate.of(2026, 8, 27), result[1]);
	}

	private ParamFilterModel cloneEndToStart() {
		ParamFilterModel cloneFilter = new ParamFilterModel();
		cloneFilter.setFilterType("clone");
		cloneFilter.setParams(new String[] { "endDate" });
		cloneFilter.setParam("endDate");
		cloneFilter.setUpdateParams(new String[] { "startDate" });
		return cloneFilter;
	}

	@Test
	public void stringReferencedParamValueParsed() {
		// 引用参数值是字符串数字(如页面传参"-7")也应正确解析
		Object[] result = ParamFilterUtils.filterValue(null,
				new String[] { "endDate", "incrementDays" }, new Object[] { "20260826", "-7" },
				Collections.singletonList(toDateFilter("endDate", "incrementDays")));
		assertEquals(LocalDate.of(2026, 8, 19), result[0]);
	}

	@Test
	public void negativeReferencedParamEndToEnd() {
		// -incrementDaysBefore为负数引用(解析层由-${incrementDaysBefore}规整而来):参数传正数7,增量取反为-7天
		Object[] result = ParamFilterUtils.filterValue(null,
				new String[] { "endDate", "incrementDaysBefore" }, new Object[] { "20260826", 7 },
				Collections.singletonList(toDateFilter("endDate", "-incrementDaysBefore")));
		assertEquals(LocalDate.of(2026, 8, 19), result[0]);

		// 参数值为负数-7时遵循数学符号规则:-(-7)=+7天
		result = ParamFilterUtils.filterValue(null,
				new String[] { "endDate", "incrementDaysBefore" }, new Object[] { "20260826", -7 },
				Collections.singletonList(toDateFilter("endDate", "-incrementDaysBefore")));
		assertEquals(LocalDate.of(2026, 9, 2), result[0]);
	}

	@Test
	public void fullParamNamesStripsLeadingNegate() {
		// 负数引用时getFullParamNames应收录剥离负号后的真实参数名
		SqlToyConfig config = new SqlToyConfig("neg_param_names",
				"select * from t where d>=:startDate");
		config.addFilters(Collections.singletonList(toDateFilter("startDate", "-incrementDaysBefore")));
		boolean found = false;
		for (String name : config.getFullParamNames()) {
			if ("incrementDaysBefore".equalsIgnoreCase(name)) {
				found = true;
			} else if (name.startsWith("-")) {
				found = false;
				break;
			}
		}
		assertTrue(found, "getFullParamNames应收录剥离负号后的参数名,且不应出现带负号的伪参数名!");
	}

	@Test
	public void parseSegmentNormalizesNegativeParamReference() throws Exception {
		// 解析层将-${incrementDaysBefore}规整为-incrementDaysBefore(parseSagment默认附加blank过滤器)
		String xml = "<sql id=\"neg_ref_test\"><filters>"
				+ "<to-date params=\"startDate\" format=\"yyyyMMdd\" increment-time=\"-${incrementDaysBefore}\"/>"
				+ "</filters><value><![CDATA[select * from t where d>=:startDate]]></value></sql>";
		SqlToyConfig config = SqlXMLConfigParse.parseSagment(xml, "utf-8", "mysql");
		boolean found = false;
		for (ParamFilterModel filter : config.getFilters()) {
			if ("to-date".equals(filter.getFilterType())) {
				assertEquals("-incrementDaysBefore", filter.getIncrementTime());
				found = true;
			}
		}
		assertTrue(found, "应解析出to-date过滤器!");
	}
}
