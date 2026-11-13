package org.sagacity.sqltoy.plugins.function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 反向源适配(2026-11-10)的边界守卫:确认"新识别的源写法"不会把原有合法/应响亮报错的写法静默误转。
 *
 * <p>
 * 覆盖三类实爆风险:
 * <ol>
 * <li>DateTrunc的数值转发不能吞掉date_trunc('hour',ts)与Oracle TRUNC(ts,'HH')(第二参是日期单位而非位数)</li>
 * <li>to_timestamp在oracle/db2是原生TIMESTAMP函数(支持FF小数秒),双参不能被改写成to_date;
 * 单参字符串字面量是解析语义,不能按epoch转发(mysql/sqlite会静默NULL)</li>
 * <li>weekday/dayofweek的取模锚点在2000年,2000-01-03之前的日期差为负,oracle/dm/db2/sqlserver的
 * 取模对负被除数返回负数,须归一</li>
 * </ol>
 */
public class ReverseSourceGuardTest {

	@BeforeAll
	public static void init() {
		FunctionUtils.setFunctionConverts(Arrays.asList("default"));
	}

	private String c(String sql, String dialect) {
		return FunctionUtils.getDialectSql(sql, dialect);
	}

	// ==================== DateTrunc数值转发守卫 ====================

	@Test
	public void dateTruncUnknownUnitStaysLoud() {
		// date_trunc的第一参恒为单位,第二参是日期表达式;转发会产出truncate(ts,ts)这类静默错形态
		String sql = "select date_trunc('hour', create_time) from t";
		assertEquals(sql, c(sql, "mysql"));
		assertEquals(sql, c(sql, "oracle"));
		assertEquals(sql, c(sql, "postgresql"));
		assertEquals(sql, c(sql, "sqlserver"));
	}

	@Test
	public void oracleTimeTruncWithQuotedUnitStaysLoud() {
		// oracle的TRUNC(ts,'HH')(时间截断)单位表未收录;引号字面量不是截断位数,不能转truncate(ts,'HH')
		String sql = "select trunc(create_time,'HH') from t";
		assertEquals(sql, c(sql, "mysql"));
		assertEquals(sql, c(sql, "tidb"));
		assertEquals(sql, c(sql, "oracle"));
	}

	@Test
	public void numericTruncForwardStillWorks() {
		// 数值trunc(x,d)的转发保持:Oracle/PG系写法在MySQL系得到truncate等价
		assertEquals("select truncate(score,2) from t", c("select trunc(score,2) from t", "mysql"));
		assertEquals("select truncate(score,-1) from t", c("select trunc(score,-1) from t", "doris"));
		// 列/占位符形态的位数参数(无引号)同样转发
		assertEquals("select truncate(score,scale_col) from t", c("select trunc(score,scale_col) from t", "mysql"));
	}

	// ==================== to_timestamp原生/解析语义守卫 ====================

	@Test
	public void timestampTwoArgsNativeOnOracleAndDb2() {
		// oracle/db2原生to_timestamp:返回TIMESTAMP且支持FF小数秒,转to_date会丢精度/报ORA-01821
		String ff = "select to_timestamp('2026-11-10 10:00:00.123','YYYY-MM-DD HH24:MI:SS.FF3') from t";
		assertEquals(ff, c(ff, "oracle"));
		String plain = "select to_timestamp('2026-11-10 10:00:00','YYYY-MM-DD HH24:MI:SS') from t";
		assertEquals(plain, c(plain, "oracle"));
		assertEquals(ff, c(ff, "db2"));
		assertEquals(plain, c(plain, "db2"));
	}

	@Test
	public void timestampTwoArgsStillConvertedOnParsingTargets() {
		// h2无原生to_timestamp,双参解析仍转parsedatetime(大写YYYY/DD归一到java模型)
		assertEquals("select parsedatetime('2026-11-10','yyyy-MM-dd') from t",
				c("select to_timestamp('2026-11-10','YYYY-MM-DD') from t", "h2"));
	}

	@Test
	public void timestampParseLiteralStaysLoudInsteadOfEpoch() {
		// 字符串字面量是解析语义:mysql会静默NULL、sqlite会静默NULL、db2原生可解析(不能算成epoch数值)
		String sql = "select to_timestamp('2026-11-10 10:00:00') from t";
		assertEquals(sql, c(sql, "mysql"));
		assertEquals(sql, c(sql, "sqlite"));
		assertEquals(sql, c(sql, "db2"));
		assertEquals(sql, c(sql, "clickhouse"));
	}

	@Test
	public void timestampEpochStillConverted() {
		// epoch 数值/数值字面量仍按epoch转换
		assertEquals("select from_unixtime(1794314096) from t",
				c("select to_timestamp(1794314096) from t", "mysql"));
		assertEquals("select from_unixtime('1794314096') from t",
				c("select to_timestamp('1794314096') from t", "mysql"));
		assertEquals("select fromUnixTimestamp(1794314096) from t",
				c("select to_timestamp(1794314096) from t", "clickhouse"));
	}

	// ==================== DateTrunc/Truncate 第二参引号语义 ====================

	@Test
	public void truncSecondArgQuotedNumericOrIdentifier() {
		// 单引号数值:是位数,应转发(旧"任意引号即日期单位"判定会误拦,MySQL报trunc不存在)
		assertEquals("select truncate(score,'2') from t", c("select trunc(score,'2') from t", "mysql"));
		assertEquals("select truncate(score,'-1') from t", c("select trunc(score,'-1') from t", "mysql"));
		// 单引号非数值:日期单位,保持透传响亮(既有守卫)
		assertEquals("select trunc(ts,'HH') from t", c("select trunc(ts,'HH') from t", "mysql"));
	}

	@Test
	public void truncDoubleQuotedArgIsDialectAware() {
		// MySQL系默认非ANSI_QUOTES:双引号是字符串字面量,非数值内容必须按日期单位拦截——
		// 否则 truncate(ts,"HH") 把"HH"当0位小数,静默截成2026.0(MySQL 9.3真库实测)
		assertEquals("select trunc(ts,\"HH\") from t", c("select trunc(ts,\"HH\") from t", "mysql"));
		assertEquals("select trunc(score,\"d\") from t", c("select trunc(score,\"d\") from t", "mysql"));
		assertEquals("select trunc(score,\"d\") from t", c("select trunc(score,\"d\") from t", "tidb"));
		// 标准SQL/PG系/Oracle/DB2/H2/CH/SQLServer/HANA:双引号是标识符(列),放行数值转换
		assertEquals("select round(score,\"d\",1) from t", c("select trunc(score,\"d\") from t", "sqlserver"));
		assertEquals("select round(score,\"d\",ROUND_DOWN) from t",
				c("select trunc(score,\"d\") from t", "hana"));
		assertEquals("select trunc(score,\"d\") from t", c("select truncate(score,\"d\") from t", "oracle"));
		assertEquals("select trunc(score,\"d\") from t", c("select truncate(score,\"d\") from t", "postgresql"));
	}

	// ==================== split_part 源边界 ====================

	@Test
	public void splitPartFirstSegmentWithoutDelimiterKeepsWholeString() {
		// update 2026-11-10 oracle/dm:n=1且无分隔符时 currPos=0,原substr(str,1,-1)返回NULL;
		// 真库实测split_part('abc',',',1)应为'abc'(PG语义),故首段须补"无分隔符→原串"分支
		String sql = "select split_part('abc',',',1) from t";
		String oracle = c(sql, "oracle");
		assertTrue(oracle.contains("when 1=1 then"), oracle);
		assertTrue(oracle.contains("when instr('abc',',',1,1)=0 then 'abc'"), oracle);
		assertTrue(oracle.contains("else substr('abc',1,instr('abc',',',1,1)-1)"), oracle);
		assertEquals(oracle, c(sql, "dm"));
		// pg原生透传
		assertEquals(sql, c(sql, "postgresql"));
		// mysql系双substring_index组合(update 2026-11-11 全保真重写:外包段数守卫case,超界返回'')
		String mysql = c(sql, "mysql");
		assertTrue(mysql.contains("substring_index(substring_index('abc',',',1),',',-1)"), mysql);
		assertTrue(mysql.contains("then ''"), "正索引须有越界守卫: " + mysql);
	}

	@Test
	public void splitPartNegativeAndOutOfRangeFullFidelity() {
		// update 2026-11-11 全保真重写锁定(PG真库ground truth:负索引从末尾数、正超界返回''):
		// 旧组合对负索引错一位(mysql的-2返回'e'应'd')、正超界返回边界段(应'')
		// mysql负字面量:符号分流走负支(外层substring_index取1=末|n|段的第一段),含段数守卫
		String negMysql = c("select split_part('a,b,c,d,e',',',-2) from t", "mysql");
		assertTrue(negMysql.contains("substring_index(substring_index('a,b,c,d,e',',',-2),',',1)"), negMysql);
		assertTrue(negMysql.contains("(0 - (-2))"), "取负须防--注释形态: " + negMysql);
		assertTrue(negMysql.contains("then ''"), "负索引须有越界守卫: " + negMysql);
		// mysql正字面量:越界守卫
		String posMysql = c("select split_part('a,b,c,d,e',',',2) from t", "mysql");
		assertTrue(posMysql.contains("substring_index(substring_index('a,b,c,d,e',',',2),',',-1)"), posMysql);
		assertTrue(posMysql.contains("then ''"), posMysql);
		// 动态n:全量CASE按符号分流
		String dynMysql = c("select split_part(name,',',idx) from t", "mysql");
		assertTrue(dynMysql.contains("when idx>0 then") && dynMysql.contains("when idx<0 then"), dynMysql);
		// n=0病态输入统一空串(PG报错,转换层响亮价值低)
		assertEquals("select '' from t", c("select split_part(name,',',0) from t", "mysql"));
		// oracle负字面量-1:position=-1自串尾定位(occurrence=1,oracle/dm的occurrence必须为正)
		String neg1Oracle = c("select split_part(name,',',-1) from t", "oracle");
		assertTrue(neg1Oracle.contains("instr(name,',',-1,1)"), neg1Oracle);
		assertFalse(neg1Oracle.contains(",1,-1)"), "不得把负值当occurrence(ORA-01428): " + neg1Oracle);
		assertFalse(neg1Oracle.contains(",1,0)"), "不得出现occurrence=0: " + neg1Oracle);
		// oracle负字面量-2:position=-1+occurrence=|n|/|n|-1(镜像正索引)
		String neg2Oracle = c("select split_part(name,',',-2) from t", "oracle");
		assertTrue(neg2Oracle.contains("instr(name,',',-1,(0 - (-2)))"), neg2Oracle);
		assertTrue(neg2Oracle.contains("instr(name,',',-1,(0 - (-2))-1)"), neg2Oracle);
		assertFalse(neg2Oracle.contains(",1,-"), "不得把负值当occurrence: " + neg2Oracle);
		// oracle动态n:四分支(0/正/-1/负)
		String dynOracle = c("select split_part(name,',',idx) from t", "oracle");
		assertTrue(dynOracle.contains("when idx=0 then ''") && dynOracle.contains("when idx=-1 then"), dynOracle);
		assertTrue(dynOracle.contains("instr(name,',',-1,(0 - (idx)))"), dynOracle);
		// CH:splitByString+arrayElement(多字符分隔符/负索引/超界''),不再用单字符受限的substringIndex
		String ch = c("select split_part('a||b||c','||',-2) from t", "clickhouse");
		assertEquals("select arrayElement(splitByString('||','a||b||c'), -2) from t", ch);
		// pg原生透传不受影响
		assertEquals("select split_part(name,',',-2) from t", c("select split_part(name,',',-2) from t", "postgresql"));
		// 未适配方言响亮
		assertEquals("select split_part(name,',',-2) from t", c("select split_part(name,',',-2) from t", "sqlserver"));
	}

	@Test
	public void splitPartPositiveGeneralAndBoundaryForms() {
		// oracle正索引通用路径(n≥2):posN/posNm1镜像定位,段落在第n-1与第n个分隔符之间;
		// n=cnt时第n个分隔符不存在(posN=0),取第n-1个分隔符之后到串尾(末段边界分支)
		String sql = "select split_part('a,b,c',',',2) from t";
		String oracle = c(sql, "oracle");
		assertTrue(oracle.contains("instr('a,b,c',',',1,2)"), "第n个分隔符定位:" + oracle);
		assertTrue(oracle.contains("instr('a,b,c',',',1,2-1)"), "第n-1个分隔符定位:" + oracle);
		assertTrue(oracle.contains("when instr('a,b,c',',',1,2)=0 then substr('a,b,c',instr('a,b,c',',',1,2-1)+length(','))"),
				"末段边界(第n个分隔符不存在)分支:" + oracle);
		assertTrue(oracle.contains("2>((length('a,b,c')-length(replace('a,b,c',',','')))/length(',')+1)"),
				"段数守卫(replace差值法):" + oracle);
		assertEquals(oracle, c(sql, "dm"), "dm与oracle同组合形态");
		// n=0病态输入:mysql家族与oracle/dm统一短路为''(dm/oracle此前未锁定)
		assertEquals("select '' from t", c("select split_part(name,',',0) from t", "oracle"));
		assertEquals("select '' from t", c("select split_part(name,',',0) from t", "dm"));
		assertEquals("select '' from t", c("select split_part(name,',',0) from t", "doris"));
	}

	@Test
	public void splitPartUnadaptedDialectsStayLoud() {
		// db2(locate_in_string超界occurrence报-171不可组合)/h2未适配:原样透传由库响亮报错
		String sql = "select split_part(name,',',2) from t";
		assertEquals(sql, c(sql, "db2"));
		assertEquals(sql, c(sql, "h2"));
	}

	@Test
	public void splitPartMysqlFamilySharesGuardedComposition() {
		// mysql家族其余成员(doris/starrocks/mysql57)与mysql同组合+段数守卫
		// update 2026-11-12 OB移出mysql家族:框架约定dialect=oceanbase即Oracle模式,走instr组合
		String sql = "select split_part('a,b,c,d,e',',',-2) from t";
		for (String dialect : new String[] { "doris", "starrocks", "mysql57" }) {
			String out = c(sql, dialect);
			assertTrue(out.contains("substring_index(substring_index('a,b,c,d,e',',',-2),',',1)"),
					dialect + "负索引组合:" + out);
			assertTrue(out.contains("then ''"), dialect + "越界守卫:" + out);
		}
		String ob = c(sql, "oceanbase");
		assertTrue(ob.contains("instr("), "OB Oracle模式split_part应走instr组合:" + ob);
	}

	@Test
	public void splitPartClickHouseDynamicIndexPassesThrough() {
		// CH动态n:arrayElement原生支持负索引/越界default '',无需CASE包装,n原样透传
		assertEquals("select arrayElement(splitByString(',',name), idx) from t",
				c("select split_part(name,',',idx) from t", "clickhouse"));
	}

	// ==================== week族负取模归一 ====================

	@Test
	public void weekdayBefore2000AnchorIsNormalized() {
		// 2000-01-03为周一锚点;1999-12-31(周五)差值为负,oracle的mod(-3,7)=-3须归一为4
		assertEquals("select mod(mod(trunc(date '1999-12-31') - date '2000-01-03', 7) + 7, 7) from t",
				c("select weekday(date '1999-12-31') from t", "oracle"));
		// update 2026-11-12 DB2字面量cast定型:week组合的'串'-N days日期算术要求显式date类型
		// (裸串在days()/dayofweek()可用,但日期减法报SQLCODE=-182,真库v12实测)
		assertEquals("select mod(mod(days(cast('1999-12-31' as date)) - days('2000-01-03'), 7) + 7, 7) from t",
				c("select weekday('1999-12-31') from t", "db2"));
		assertEquals("select ((datediff(day, '2000-01-03', '1999-12-31') % 7 + 7) % 7) from t",
				c("select weekday('1999-12-31') from t", "sqlserver"));
	}

	@Test
	public void dayofweekBefore2000AnchorIsNormalized() {
		// 2000-01-02为周日锚点;1999-12-31差值为负,归一后+1为6(周五)
		assertEquals("select mod(mod(trunc(date '1999-12-31') - date '2000-01-02', 7) + 7, 7) + 1 from t",
				c("select dayofweek(date '1999-12-31') from t", "oracle"));
		assertEquals("select (((datediff(day, '2000-01-02', '1999-12-31') % 7) + 7) % 7 + 1) from t",
				c("select dayofweek('1999-12-31') from t", "sqlserver"));
		// sqlite/h2/pg的原始表达式本身非负,不受影响(形态不变)
		assertEquals("select (cast(strftime('%w', '1999-12-31') as integer) + 6) % 7 from t",
				c("select weekday('1999-12-31') from t", "sqlite"));
		assertEquals("select (dayofweek(create_time) + 5) % 7 from t",
				c("select weekday(create_time) from t", "h2"));
	}

	@Test
	public void weekdayModernDatesUnchangedInValue() {
		// 归一化对2000年后的日期幂等:形态变了但算术结果一致(mod(x+7,7)==mod(x,7) for x>=0)
		assertEquals("select mod(mod(trunc(create_time) - date '2000-01-03', 7) + 7, 7) from t",
				c("select weekday(create_time) from t", "oracle"));
	}
}
