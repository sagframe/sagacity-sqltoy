package org.sagacity.sqltoy.plugins.function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 三个新函数转换器(2026-11-10)的方言文本锁定:substring_index/unix_timestamp/truncate
 * 在各方言的转换形态(真值行为由verify工程的真库探针覆盖,此处锁文本形态与透传边界)。
 */
public class ThreeFunctionsDialectTest {

	@BeforeAll
	public static void init() {
		// default集展开(含三个新函数)
		FunctionUtils.setFunctionConverts(Arrays.asList("default"));
	}

	private static String convert(String sql, String dialect) {
		return FunctionUtils.getDialectSql(sql, dialect);
	}

	// ==================== substring_index ====================

	@Test
	public void substringIndexMysqlFamilyPassthrough() {
		String sql = "select substring_index(name,',',2) from t";
		assertEquals(sql, convert(sql, "mysql"));
		assertEquals(sql, convert(sql, "tidb"));
		assertEquals(sql, convert(sql, "doris"));
		assertEquals(sql, convert(sql, "clickhouse"));
	}

	@Test
	public void substringIndexOracleCombinesInstr() {
		String out = convert("select substring_index(name,',',2) from t", "oracle");
		assertTrue(out.contains("instr(name,',',1,"), "oracle应组合instr occurrence:" + out);
		assertTrue(out.contains("case when 2=0 then ''"), "count=0空串边界:" + out);
		assertTrue(out.contains("substr(name,1,instr(name,',',1,"), "正count截取substr+instr:" + out);
		assertTrue(out.contains("instr(name,',',-1,"), "负count分支instr(-1,n):" + out);
	}

	@Test
	public void substringIndexDb2UsesLocateInString() {
		String out = convert("select substring_index(name,',',2) from t", "db2");
		assertTrue(out.contains("locate_in_string(name,',',1,"), "db2应组合locate_in_string:" + out);
		assertTrue(out.contains("case when 2>0 then 2 else 1 end"), "occurrence防0/负包装:" + out);
	}

	@Test
	public void substringIndexPgCombinesSplitPart() {
		String out = convert("select substring_index(name,',',2) from t", "postgresql");
		assertTrue(out.contains("string_to_array(name,',')"), "pg应组合string_to_array:" + out);
		assertTrue(out.contains("array_to_string"), "切片重组array_to_string:" + out);
		assertTrue(out.contains("[1: 2]"), "正count切片[1:n](冒号后空格防参数误解析):" + out);
	}

	// ==================== unix_timestamp ====================

	@Test
	public void unixTimestampMysqlFamilyPassthrough() {
		assertEquals("select unix_timestamp(create_time) from t",
				convert("select unix_timestamp(create_time) from t", "mysql"));
	}

	@Test
	public void unixTimestampClickHouseNativeForm() {
		// update 2026-11-10 CH无unix_timestamp(真库UNKNOWN_FUNCTION提示toUnixTimestamp),转CH原生
		assertEquals("select toUnixTimestamp(create_time) from t",
				convert("select unix_timestamp(create_time) from t", "clickhouse"));
		assertEquals("select toUnixTimestamp(now()) from t",
				convert("select unix_timestamp() from t", "clickhouse"));
	}

	@Test
	public void unixTimestampDialectForms() {
		assertTrue(convert("select unix_timestamp(create_time) from t", "postgresql")
				.contains("extract(epoch from (create_time)::timestamptz)"));
		assertTrue(convert("select unix_timestamp(create_time) from t", "sqlserver")
				.contains("datediff(second, '1970-01-01', create_time)"), convert("select unix_timestamp(create_time) from t", "sqlserver"));
		// update 2026-11-11 时区补偿:MySQL按会话时区解释,须减去目标库会话偏移(否则东八区静默偏8小时)
		assertTrue(convert("select unix_timestamp(create_time) from t", "sqlserver").contains("tzoffset"),
				"sqlserver应减会话时区偏移");
		assertTrue(convert("select unix_timestamp(create_time) from t", "db2").contains("current timezone"),
				"db2应减CURRENT TIMEZONE偏移");
		assertTrue(convert("select unix_timestamp(create_time) from t", "db2")
				.contains("midnight_seconds"));
		// update 2026-11-11 列按typeof双形态归一:TEXT列不再被 /1000 数值化静默错值
		assertTrue(convert("select unix_timestamp(create_time) from t", "sqlite")
				.contains("strftime('%s', case when typeof(create_time) in ('integer','real') then "
						+ "datetime(create_time/1000,'unixepoch') else datetime(create_time,'utc') end)"),
				"sqlite列按typeof归一:INTEGER毫秒取绝对epoch,TEXT按本地墙钟解释");
		// update 2026-11-10 hana同墙钟差口径降级IGNORE(seconds_between基于字面量墙钟,服务器时区敏感)
	}

	@Test
	public void unixTimestampSqliteCastsToInteger() {
		// strftime('%s')恒返回TEXT,SQLite类型序INTEGER/REAL<TEXT会令 unix_timestamp(x)>N 恒真、<N 恒假
		// (ORDER BY亦按文本排序),必须cast为integer才与MySQL数值语义一致;列形态与无参形态均须包裹
		String col = convert("select unix_timestamp(create_time) from t", "sqlite");
		assertTrue(col.contains("cast(strftime('%s',") && col.contains("end) as integer)"),
				"sqlite列形态须cast为integer: " + col);
		assertEquals("select cast(strftime('%s', 'now') as integer) from t",
				convert("select unix_timestamp() from t", "sqlite"));
		// 数值比较场景:cast后左侧为INTEGER,与整数字面量按数值比较(不再恒真/恒假)
		String cmp = convert("select 1 from t where unix_timestamp(create_time) > 1700000000", "sqlite");
		assertTrue(cmp.contains("as integer) > 1700000000"), "比较左侧须为integer: " + cmp);
	}

	@Test
	public void unixTimestampNoArgCurrentTime() {
		assertTrue(convert("select unix_timestamp() from t", "postgresql").contains("extract(epoch from now())"));
		assertTrue(convert("select unix_timestamp() from t", "sqlserver").contains("getdate()"));
		assertTrue(convert("select unix_timestamp() from t", "oracle").contains("sys_extract_utc(current_timestamp)"));
		assertEquals("select unix_timestamp() from t", convert("select unix_timestamp() from t", "mysql"));
	}

	@Test
	public void unixTimestampOracleCompensatedDmNativeHanaComposed() {
		// update 2026-11-11 oracle放开(真库Oracle23实测),协作方精炼为FROM_TZ+sessiontimezone:
		// 按入参日期应用会话命名时区及DST规则,SYS_EXTRACT_UTC再取绝对时刻(历史夏令时日期不偏一小时);
		// 字面量入参仍IGNORE(NLS_DATE_FORMAT敏感)
		assertTrue(convert("select unix_timestamp(create_time) from t", "oracle").contains("sessiontimezone"),
				"oracle应按会话时区解释墙钟时间");
		assertEquals("select unix_timestamp('2026-11-11 10:00:00') from t",
				convert("select unix_timestamp('2026-11-11 10:00:00') from t", "oracle"));
		// update 2026-11-11 dm归原生:dm8自带unix_timestamp且会话时区感知正确(真库实测
		// unix_timestamp('2026-11-11 10:00:00')=1794362400),原生透传即正确
		assertEquals("select unix_timestamp() from t", convert("select unix_timestamp() from t", "dm"));
		assertEquals("select unix_timestamp(create_time) from t",
				convert("select unix_timestamp(create_time) from t", "dm"));
		assertEquals("select unix_timestamp('2026-11-11 10:00:00') from t",
				convert("select unix_timestamp('2026-11-11 10:00:00') from t", "dm"), "dm原生字面量直查");
		// update 2026-11-11 hana补映射:seconds_between墙钟差-会话偏移(与sqlserver/db2/oracle口径一致)
		String hana = convert("select unix_timestamp(create_time) from t", "hana");
		assertTrue(hana.contains("seconds_between('1970-01-01 00:00:00', create_time)")
				&& hana.contains("seconds_between(current_utctimestamp, current_timestamp)"),
				"hana应组合seconds_between并减会话偏移: " + hana);
		assertTrue(convert("select unix_timestamp() from t", "hana").contains("current_timestamp"),
				"hana无参形态取当前时刻并减偏移");
	}

	// ==================== truncate ====================

	@Test
	public void truncateMysqlFamilyPassthrough() {
		assertEquals("select truncate(score,2) from t", convert("select truncate(score,2) from t", "mysql"));
		// DDL的TRUNCATE TABLE不带括号,不误命中
		assertEquals("truncate table t", convert("truncate table t", "oracle"));
	}

	@Test
	public void truncateDialectForms() {
		assertEquals("select trunc(score,2) from t", convert("select truncate(score,2) from t", "oracle"));
		assertEquals("select trunc(score,2) from t", convert("select truncate(score,2) from t", "postgresql"));
		assertEquals("select trunc(score,2) from t", convert("select truncate(score,2) from t", "db2"));
		assertEquals("select round(score,2,1) from t", convert("select truncate(score,2) from t", "sqlserver"));
		assertEquals("select round(score,2,ROUND_DOWN) from t",
				convert("select truncate(score,2) from t", "hana"));
	}

	@Test
	public void truncateSingleArgIgnored() {
		// 单参属误用,原样保留交由目标库响亮报错
		assertEquals("select truncate(score) from t", convert("select truncate(score) from t", "oracle"));
	}
}
