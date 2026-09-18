package org.sagacity.sqltoy.dialect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.plugins.function.FunctionUtils;

/**
 * F14回归:KingbaseES基于PG,函数转换与vanilla PG基本一致。修复前11个函数转换文件的PG组枚举全部漏掉
 * KINGBASE,导致nvl/date_format/group_concat/datediff等跨库函数原样透传,Kingbase报函数不存在。
 * 判定方式:逐条探针SQL比较KINGBASE与POSTGRESQL的转换结果(只比等价、不硬编码期望SQL文本),
 * 这样将来新增函数实现文件若漏写KINGBASE同样会被本用例捕获
 *
 * update 2026-9-14 KingbaseES V9(V009R001C010,默认oracle兼容模式)真库实测出现两处与vanilla
 * PG的有意分化(已各自单测断言,等价断言排除):
 * 1)DateDiff:date类型含时间(::date不截断时间,与oracle DATE同语义),date-date返回小数天
 *   (1天12小时=1.5),天差须trunc向零截断方为自然天差(vanilla PG为整数天,无需trunc);
 * 2)Concat:sys.concat(text,text)两参遮蔽pg_catalog.concat(VARIADIC "any"),三参起报
 *   函数不存在,须转||拼接(V9下||将null按空串处理,与concat跳过null语义一致)
 */
public class KingbaseFunctionConvertTest {

	private static final String[] PROBES = { "select nvl(name,'empty') from t_func", // Nvl
			"select lengthb(name) from t_func", // Length(pgFamily分支)
			"select datalength(name) from t_func", // Length(pgFamily分支)
			"select substr(name,1,3) from t_func", // SubStr
			"select instr(name,'dmi') from t_func", // Instr
			"select to_number(score) from t_func", // ToNumber
			"select to_date('2026-01-15') from t_func", // ToDate
			"select to_char(create_time,'yyyy-MM-dd') from t_func", // ToChar
			"select date_format(create_time,'%Y-%m-%d %H:%i:%s') from t_func", // DateFormat
			"select datediff(create_time,create_time) from t_func", // DateDiff
			"select group_concat(name) from t_func", // GroupConcat
			"select now() from t_func", // Now
			"select trim(name) from t_func" // Trim(无PG分支,应保持原样)
	};

	/** 明确必须发生转换的探针:保证上面的等价断言不是空转 */
	private static final String[] CONVERTED_PROBES = { "select nvl(name,'empty') from t_func",
			"select lengthb(name) from t_func", "select datalength(name) from t_func",
			"select instr(name,'dmi') from t_func", "select to_number(score) from t_func",
			"select to_date('2026-01-15') from t_func",
			"select date_format(create_time,'%Y-%m-%d %H:%i:%s') from t_func",
			"select datediff(create_time,create_time) from t_func", "select group_concat(name) from t_func" };

	@BeforeAll
	public static void init() {
		// 注册默认函数转换集(生产环境由SqlToyContext按配置装载)
		FunctionUtils.setFunctionConverts(Arrays.asList("default"));
	}

	private static String convert(String sql, String dialect) {
		return FunctionUtils.getDialectSql(sql, dialect);
	}

	@Test
	public void kingbaseConvertsIdenticallyToVanillaPostgresql() {
		for (String sql : PROBES) {
			// update 2026-9-14 datediff在V9下因date-date为小数天有意分化(trunc包裹),单独断言
			if (sql.contains("datediff")) {
				continue;
			}
			assertEquals(convert(sql, "postgresql"), convert(sql, "kingbase"), "KINGBASE应与vanilla PG转换一致: " + sql);
		}
	}

	@Test
	public void kingbaseDateDiffWrapsTruncForFractionalDayDiff() {
		// V9真库实测:date类型含时间且date-date返回小数天(1天12小时=1.5),
		// 两参/日差/周差须trunc向零截断方为自然天差口径(vanilla PG整数天trunc幂等不混淆)
		assertTrue(convert("select datediff(create_time,create_time) from t_func", "kingbase").contains("trunc("),
				"KINGBASE V9两参datediff应trunc截断小数天差");
		assertTrue(convert("select datediff(day,create_time,end_time) from t_func", "kingbase").contains("trunc("),
				"KINGBASE V9三参day应trunc截断小数天差");
		assertTrue(convert("select datediff(week,create_time,end_time) from t_func", "kingbase").contains("trunc("),
				"KINGBASE V9三参week应trunc截断小数天差");
	}

	@Test
	public void probesHitConvertersSoEqualityIsMeaningful() {
		for (String sql : CONVERTED_PROBES) {
			assertNotEquals(sql, convert(sql, "postgresql"), "探针未命中转换,需调整探针SQL: " + sql);
		}
	}

	@Test
	public void kingbaseIsNotLeftUntranslatedForCrossDbFunctions() {
		// 修复目标直断:这些跨库函数在KINGBASE下必须被改写(修复前原样透传,目标库报函数不存在)
		assertTrue(convert("select nvl(name,'empty') from t_func", "kingbase").contains("coalesce"));
		String groupConcat = convert("select group_concat(name) from t_func", "kingbase");
		assertTrue(groupConcat.contains("string_agg") || groupConcat.contains("ARRAY_AGG"), groupConcat);
		assertTrue(convert("select date_format(create_time,'%Y-%m-%d') from t_func", "kingbase").contains("to_char"));
	}
}
