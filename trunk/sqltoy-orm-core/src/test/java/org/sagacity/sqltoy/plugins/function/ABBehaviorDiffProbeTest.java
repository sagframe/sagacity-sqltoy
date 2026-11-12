package org.sagacity.sqltoy.plugins.function;

import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.SqlConfigParseUtils;
import org.sagacity.sqltoy.config.model.SqlToyResult;

/**
 * update 2026-11-11 A/B版本行为差分探针电池(非断言测试——产出canonical行为清单供新旧版本diff):
 * <p>
 * 同一份探针分别运行于6.0.9 tag与当前HEAD,输出到target/ab_battery.txt逐行diff:
 * <ul>
 * <li>老时代已有的函数(nvl/locate/ifnull/instr/to_char/substr/add_months等):diff=非预期变更,须排查</li>
 * <li>近两日新增/重写的函数(split_part/epoch族/week族/trunc族等):diff应与v6.0.10 changelog逐条对应</li>
 * <li>processSql动态sql电池(#[...]判空/嵌套/悬挂连接符清理):热路径优化的行为漂移检测</li>
 * </ul>
 * 输出每行格式:CASE|方言|结果,双版本行序完全一致可直接diff。
 */
public class ABBehaviorDiffProbeTest {

	private static final String[] DIALECTS = { "mysql", "postgresql", "oracle", "dm", "clickhouse", "tidb", "doris",
			"starrocks", "oceanbase", "sqlserver", "db2", "h2", "sqlite", "hana", "kingbase", "mysql57" };

	/** 近两日新增/重写的函数面 */
	private static final String[] NEW_SURFACE = { "select split_part('a,b,c,d,e',',',3)",
			"select split_part('a,b,c,d,e',',',-2)", "select split_part('a,b,c,d,e',',',9)",
			"select split_part('a,b,c,d,e',',',-9)", "select split_part(name,',',idx) from t",
			"select split_part('abc',',',1)", "select substring_index('a,b,c',',',2)",
			"select substring_index('a,b,c',',',-2)", "select substring_index('a,b,c',',',0)",
			"select substring_index(name,',',2) from t",
			"select unix_timestamp(create_time) from t", "select unix_timestamp()",
			"select unix_timestamp('2026-11-11 10:00:00')", "select from_unixtime(1794314096)",
			"select from_unixtime(ts_col,'%Y-%m-%d') from t", "select to_timestamp(1794314096)",
			"select to_timestamp('2026-11-10 10:00:00')",
			"select to_timestamp('2026-11-10 10:00:00','YYYY-MM-DD HH24:MI:SS')",
			"select week(create_time) from t", "select weekday(create_time) from t",
			"select dayofweek(create_time) from t", "select week('2026-01-01')",
			"select trunc(score,2) from t", "select trunc(score,'2') from t",
			"select trunc(create_time,'MM') from t", "select trunc(ts,\"HH\") from t",
			"select date_trunc('month',create_time) from t", "select truncate(score,2) from t",
			"select current_date() from t", "select datepart(year,create_time) from t",
			"select extract(year from create_time) from t", "select date_part('year',create_time) from t",
			"select date_format(ts,'HH-mm') from t", "select date_format(ts,'HH时mm分ss秒') from t",
			"select date_format(ts,'HHmmss') from t", "select date_format(ts,'HHMM') from t",
			"select date_format(ts,'yyyy-mm-dd') from t", "select date_format(ts,'yyyyMmdd') from t",
			"select date_format(ts,'HH-mm-ss') from t" };

	/** 老时代已有函数面(diff须为零) */
	private static final String[] OLD_SURFACE = { "select ifnull(name,'x') from t", "select nvl(name,'x') from t",
			"select isnull(name) from t", "select nvl2(name,'y','n') from t", "select locate('.baidu',name) from t",
			"select locate('.baidu',name,5) from t", "select instr(name,'a') from t",
			"select instr(name,'a',1,2) from t", "select strpos(name,'a') from t",
			"select charindex('a',name) from t", "select substr(name,-2) from t",
			"select substr(name,1,3) from t", "select substring_index(name,',',1) from t",
			"select to_char(create_time,'yyyy-mm-dd') from t", "select to_date('2026-01-01','yyyy-mm-dd') from t",
			"select last_day(create_time) from t", "select add_months(create_time,2) from t",
			"select year(create_time) from t", "select now() from t", "select sysdate from t",
			"select concat(name,'-','x') from t", "select concat_ws('-',name,remark) from t",
			"select length(name) from t", "select trim(create_time) from t", "select uuid() from t",
			"select decode(status,'A',1,0) from t", "select datediff(day,create_time,update_time) from t",
			"select to_number('123') from t", "select mod(score,2) from t" };

	@BeforeAll
	public static void init() {
		FunctionUtils.setFunctionConverts(Arrays.asList("default"));
	}

	@Test
	public void writeBattery() throws Exception {
		StringBuilder out = new StringBuilder();
		appendFunctionBattery(out, "NEW", NEW_SURFACE);
		appendFunctionBattery(out, "OLD", OLD_SURFACE);
		appendProcessSqlBattery(out);
		try (Writer writer = new OutputStreamWriter(new FileOutputStream("target/ab_battery.txt"), "UTF-8")) {
			writer.write(out.toString());
		}
	}

	private static void appendFunctionBattery(StringBuilder out, String tag, String[] sqls) {
		for (String sql : sqls) {
			for (String dialect : DIALECTS) {
				String converted = FunctionUtils.getDialectSql(sql, dialect);
				out.append(tag).append('|').append(dialect).append('|').append(sql).append(" => ").append(converted)
						.append('\n');
			}
		}
	}

	private static void appendProcessSqlBattery(StringBuilder out) {
		String[] dynamicSqls = {
				"select * from t_order where 1=1 #[and name=:name] #[and status=:status]",
				"select * from t_order #[where 1=1] #[and ( #[t.a=:a] #[or t.b=:b] )]",
				"select * from t_order where 1=1 #[and (t.g0=:g0 or t.h1=:h1)] #[and c1=:c1]",
				"select * from t_order where 1=1 #[and name like :name]",
				"update t_order set name=:name where 1=1 #[and id=:id]" };
		Map<String, Object>[] paramSets = buildParamSets();
		for (String sql : dynamicSqls) {
			for (int i = 0; i < paramSets.length; i++) {
				for (String dialect : new String[] { "mysql", "oracle", "db2" }) {
					SqlToyResult result = SqlConfigParseUtils.processSql(sql, paramSets[i], dialect);
					out.append("DYN|").append(dialect).append('|').append(i).append('|').append(sql).append(" => ")
							.append(result.getSql()).append(" params=")
							.append(Arrays.toString(result.getParamsValue())).append('\n');
				}
			}
		}
	}

	private static Map<String, Object>[] buildParamSets() {
		// 各形态参数组合:null剔除/全有值/部分null/嵌套组null
		Map<String, Object> all = new HashMap<String, Object>();
		all.put("name", "n1");
		all.put("status", "A");
		all.put("a", "va");
		all.put("b", "vb");
		all.put("g0", "vg");
		all.put("h1", "vh");
		all.put("c1", "vc");
		all.put("id", "1");
		Map<String, Object> nameNull = new HashMap<String, Object>(all);
		nameNull.put("name", null);
		Map<String, Object> groupNull = new HashMap<String, Object>(all);
		groupNull.put("g0", null);
		groupNull.put("h1", null);
		Map<String, Object> allNull = new HashMap<String, Object>();
		for (Map.Entry<String, Object> entry : all.entrySet()) {
			allNull.put(entry.getKey(), null);
		}
		@SuppressWarnings("unchecked")
		Map<String, Object>[] sets = new Map[] { all, nameNull, groupNull, allNull };
		return sets;
	}
}
