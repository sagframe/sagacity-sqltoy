package org.sagacity.sqltoy.plugins.function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * FromUnixTime/Week两个转换器的方言文本形态锁定(2026-11-10):
 * 真库行为已由verify工程SecondBatchFuncRealDbTest在22个profile验证,
 * 此处补单元级形态断言——重点覆盖"响亮IGNORE"边界(epoch族oracle/dm/hana、
 * week带mode参数、from_unixtime带fmt在非mysql系),防止后续修改静默破坏防御设计。
 */
public class FromUnixTimeWeekDialectTest {

	@BeforeAll
	public static void init() {
		FunctionUtils.setFunctionConverts(Arrays.asList("default"));
	}

	private static String c(String sql, String dialect) {
		return FunctionUtils.getDialectSql(sql, dialect);
	}

	// ==================== from_unixtime ====================

	@Test
	public void fromUnixtimeMysqlFamilyPassthrough() {
		String sql = "select from_unixtime(ts_col) from t";
		assertEquals(sql, c(sql, "mysql"));
		assertEquals(sql, c(sql, "tidb"));
		// update 2026-11-12 OB归Oracle系(框架约定dialect=oceanbase即Oracle模式,与DateDiff一致):
		// Oracle模式无from_unixtime,转from_tz+sessiontimezone组合;MySQL模式应配dialect=mysql
		assertTrue(c(sql, "oceanbase").contains("at time zone sessiontimezone"),
				"OB Oracle模式应走Oracle系epoch转换");
	}

	@Test
	public void singleArgToTimestampNativeParsePassthrough() {
		// update 2026-11-12 单参to_timestamp(列/占位符)在oracle/dm/OB是原生文本解析写法
		// (6.0.9原生透传),不能按PG epoch语义转换(ORA-01722/静默NULL)
		String colSql = "select to_timestamp(create_time) from t";
		assertEquals(colSql, c(colSql, "oracle"));
		assertEquals(colSql, c(colSql, "oracle11"));
		assertEquals(colSql, c(colSql, "dm"));
		assertEquals(colSql, c(colSql, "oceanbase"));
		String paramSql = "select to_timestamp(:ts) from t";
		assertEquals(paramSql, c(paramSql, "oracle"));
		// update 2026-11-12 评审R2:数值字面量epoch意图明确,Oracle/DM/OB仍按epoch转换
		// (列/占位符才透传),避免to_timestamp(1794362400)在Oracle报ORA-00932
		String epochSql = "select to_timestamp(1794314096) from t";
		assertTrue(c(epochSql, "oracle").contains("numtodsinterval"), "oracle数值epoch应转换");
		assertEquals("select from_unixtime(1794314096) from t", c(epochSql, "dm"), "dm数值epoch转from_unixtime");
		assertTrue(c(epochSql, "oceanbase").contains("numtodsinterval"), "OB数值epoch应走Oracle转换");
		assertTrue(c("select to_timestamp('1794314096') from t", "oracle").contains("numtodsinterval"),
				"引号数值epoch应转换");
		// DB2单参原生解析真库v12实测不可用(常见格式一律-20448),保留epoch转换
		assertTrue(c(colSql, "db2").contains("1970-01-01"), "db2应保留epoch转换");
		// epoch数值入参在mysql/pg系仍正常转换(不受影响)
		assertEquals("select from_unixtime(1794314096) from t", c("select to_timestamp(1794314096) from t", "mysql"));
		assertEquals("select to_timestamp(1794314096) from t", c("select to_timestamp(1794314096) from t", "postgresql"));
	}

	@Test
	public void weekOracleLiteralShapedAgainstNls() {
		// update 2026-11-12 Oracle/DM/OB字面量入参包to_date定型:默认NLS_DATE_FORMAT(DD-MON-RR)
		// 下trunc('2026-11-11')报ORA-01861,与DateDiff/Extract口径对齐
		assertTrue(c("select week('2026-11-11') from t", "oracle").contains("to_date('2026-11-11','yyyy-MM-dd')"),
				"oracle week字面量应定型");
		assertTrue(c("select weekday('2026-11-11') from t", "dm").contains("to_date('2026-11-11','yyyy-MM-dd')"),
				"dm weekday字面量应定型");
		assertTrue(c("select dayofweek('2026-11-11 10:00:00') from t", "oceanbase")
				.contains("to_date('2026-11-11 10:00:00','yyyy-MM-dd hh24:mi:ss')"), "OB dayofweek字面量应定型");
		// 列形态不受影响
		assertTrue(c("select weekday(ts) from t", "oracle").contains("trunc(ts)"), "列形态保持原样");
		// update 2026-11-12 DB2裸串字面量报SQLCODE=-182(真库实测),cast定型
		assertTrue(c("select weekday('2026-11-11') from t", "db2").contains("cast('2026-11-11' as date)"),
				"db2 weekday字面量应cast定型");
		assertTrue(c("select week('2026-11-11 10:00:00') from t", "db2").contains("cast('2026-11-11 10:00:00' as timestamp)"),
				"db2日期时间字面量应cast为timestamp");
	}

	@Test
	public void weekOceanbaseOracleModeComposition() {
		// update 2026-11-12 OB Oracle模式无week/weekday/dayofweek,走Oracle系锚点组合
		assertTrue(c("select weekday(ts) from t", "oceanbase").contains("mod(mod(trunc(ts) - date '2000-01-03'"),
				"OB weekday应走Oracle组合");
		assertTrue(c("select week(ts) from t", "oceanbase").contains("trunc(ts,'yyyy')"), "OB week应走Oracle年锚");
	}

	@Test
	public void fromUnixtimeDialectForms() {
		assertEquals("select to_timestamp(1794314096) from t", c("select from_unixtime(1794314096) from t", "postgresql"));
		// update 2026-11-11 会话时区补偿:MySQL from_unixtime 按会话时区呈现,故加目标库当前偏移
		assertEquals(
				"select dateadd(second, (1794314096 + (datepart(tzoffset, sysdatetimeoffset()) * 60)), '1970-01-01') from t",
				c("select from_unixtime(1794314096) from t", "sqlserver"));
		// update 2026-11-11 与MySQL会话时区语义对齐(本地墙钟呈现)
		assertEquals("select datetime(1794314096, 'unixepoch', 'localtime') from t",
				c("select from_unixtime(1794314096) from t", "sqlite"));
		assertEquals("select fromUnixTimestamp(1794314096) from t",
				c("select from_unixtime(1794314096) from t", "clickhouse"));
		assertTrue(c("select from_unixtime(1794314096) from t", "db2").contains("1970-01-01"), "db2 epoch基准加法");
		// update 2026-11-11 时区补偿:from_unixtime 按会话时区呈现,SS/DB2须加当前偏移
		assertTrue(c("select from_unixtime(1794314096) from t", "db2").contains("current timezone"),
				"db2应加CURRENT TIMEZONE偏移");
	}

	@Test
	public void fromUnixtimeOracleCompensatedDmNativeHanaComposed() {
		// update 2026-11-11 oracle放开(真库Oracle23实测 1794391200→2026-11-11 10:00:00);
		// dm归原生(真库实测 from_unixtime(1794362400)='2026-11-11 10:00:00',+08墙钟呈现);
		// hana补映射(add_seconds+会话偏移,与unix_timestamp的hana分支口径对称)
		String sql = "select from_unixtime(1794314096) from t";
		// oracle:协作方精炼为from_tz(...,'UTC') at time zone sessiontimezone(DST感知,按目标时刻计算偏移)
		assertTrue(c(sql, "oracle").contains("at time zone sessiontimezone"),
				"oracle应将UTC epoch按会话时区呈现并应用目标时刻DST");
		assertEquals(sql, c(sql, "dm"), "dm原生透传");
		String hana = c(sql, "hana");
		assertTrue(hana.contains("add_seconds('1970-01-01 00:00:00', (1794314096 + seconds_between")
				&& hana.contains("current_utctimestamp"), "hana应组合add_seconds+会话偏移: " + hana);
	}

	@Test
	public void fromUnixtimeWithFmtOnlyMysqlFamily() {
		// 带fmt格式参数:仅mysql系/dm原生(dm8真库实测支持MySQL风格fmt),其余方言响亮
		// (格式串跨库转换不成比例)
		String sql = "select from_unixtime(ts_col,'%Y-%m-%d') from t";
		assertEquals(sql, c(sql, "mysql"));
		assertEquals(sql, c(sql, "dm"), "dm原生支持fmt形态");
		assertEquals(sql, c(sql, "postgresql"));
		assertEquals(sql, c(sql, "sqlserver"));
		assertEquals(sql, c(sql, "hana"), "hana无fmt形态,响亮");
	}

	// ==================== week族 ====================

	@Test
	public void weekFamilyMysqlFamilyPassthrough() {
		assertEquals("select week(ts) from t", c("select week(ts) from t", "mysql"));
		assertEquals("select weekday(ts) from t", c("select weekday(ts) from t", "tidb"));
		assertEquals("select dayofweek(ts) from t", c("select dayofweek(ts) from t", "doris"));
	}

	@Test
	public void weekWithModeParamLoudOnNonMysql() {
		// week(d,mode)的8种mode语义各异,仅mysql原生,其余响亮
		String sql = "select week(ts,3) from t";
		assertEquals(sql, c(sql, "mysql"));
		assertEquals(sql, c(sql, "postgresql"));
		assertEquals(sql, c(sql, "oracle"));
	}

	@Test
	public void weekdayForms() {
		// pg:extract(dow)周日=0 → (dow+6)%7 周一=0基
		assertEquals("select (extract(dow from (ts))::int + 6) % 7 from t", c("select weekday(ts) from t", "postgresql"));
		// oracle:2000-01-03周一锚+双重取模归一(负数日期差)
		assertTrue(c("select weekday(ts) from t", "oracle").contains("mod(mod(trunc(ts) - date '2000-01-03', 7) + 7, 7)"));
		// sqlserver:datediff锚+归一
		assertTrue(c("select weekday(ts) from t", "sqlserver")
				.contains("(datediff(day, '2000-01-03', ts) % 7 + 7) % 7"));
		// db2/h2 weekday为转换形态(db2无weekday函数)
		assertTrue(c("select weekday(ts) from t", "db2").contains("days('2000-01-03')"));
		assertTrue(c("select weekday(ts) from t", "h2").contains("(dayofweek(ts) + 5) % 7"));
	}

	@Test
	public void dayofweekForms() {
		// db2/h2原生DAYOFWEEK即ODBC语义(周日=1),透传
		assertEquals("select dayofweek(ts) from t", c("select dayofweek(ts) from t", "db2"));
		assertEquals("select dayofweek(ts) from t", c("select dayofweek(ts) from t", "h2"));
		// pg:extract(dow)+1
		assertEquals("select (extract(dow from (ts))::int + 1) from t", c("select dayofweek(ts) from t", "postgresql"));
		// sqlite:strftime('%w')+1
		assertTrue(c("select dayofweek(ts) from t", "sqlite").contains("strftime('%w'"));
	}

	@Test
	public void weekMode0AnchorForms() {
		// oracle系:trunc(d,'yyyy')年锚+首周日锚点法
		String oracle = c("select week(ts) from t", "oracle");
		assertTrue(oracle.contains("trunc(ts,'yyyy')") && oracle.contains("date '2000-01-02'"), oracle);
		// pg系:doy/首周日doy,case+floor+::int(openGauss内核浮点除法归一)
		String pg = c("select week(ts) from t", "postgresql");
		assertTrue(pg.contains("extract(doy from (ts))") && pg.contains("::int"), pg);
		// sqlserver:datefromparts年锚
		assertTrue(c("select week(ts) from t", "sqlserver").contains("datefromparts(year(ts), 1, 1)"));
		// sqlite:start of year修饰符
		assertTrue(c("select week(ts) from t", "sqlite").contains("'start of year'"));
	}

	@Test
	public void weekFamilyLoudOnChHana() {
		// CH/HANA第一版未适配week族,响亮透传
		assertEquals("select week(ts) from t", c("select week(ts) from t", "clickhouse"));
		assertEquals("select weekday(ts) from t", c("select weekday(ts) from t", "hana"));
	}
}
