package org.sagacity.sqltoy.dialect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.plugins.function.FunctionUtils;

/**
 * 函数转换矩阵验证(默认注册的12个函数):
 * 1.H2可执行场景端到端执行,验证转换产物的语法与语义;
 * 2.各方言转换输出形态断言
 */
public class FunctionConvertMatrixTest {

	private static Connection conn;

	@BeforeAll
	public static void init() throws Exception {
		conn = DriverManager.getConnection("jdbc:h2:mem:funcmatrix;DB_CLOSE_DELAY=-1", "sa", "");
		try (Statement st = conn.createStatement()) {
			st.execute("drop table if exists t_func");
			st.execute("create table t_func (id int, name varchar(100), score decimal(10,2), "
					+ "create_time timestamp)");
			st.execute("insert into t_func values (1,'admin',88.5,'2026-01-15 10:30:00')");
			st.execute("insert into t_func values (2,'user',72.0,'2026-03-20 14:00:00')");
		}
		// 注册默认函数转换集(生产环境由SqlToyContext按配置装载)
		FunctionUtils.setFunctionConverts(java.util.Arrays.asList("default"));
	}

	private Object querySingle(String sql) throws Exception {
		try (PreparedStatement pst = conn.prepareStatement(sql); ResultSet rs = pst.executeQuery()) {
			rs.next();
			return rs.getObject(1);
		}
	}

	private String convert(String sql, String dialect) {
		return FunctionUtils.getDialectSql(sql, dialect);
	}

	// ---------------- H2端到端(默认方言,sqltoy测试主环境) ----------------

	@Test
	public void h2ExecutionMatrix() throws Exception {
		// concat 3参:H2原生支持,原样执行
		assertEquals("admin-x", querySingle(convert("select concat(name,'-','x') from t_func where id=1", "h2")));
		// nvl -> coalesce(null取后者,非空取前者)
		assertEquals("admin", querySingle(convert("select nvl(name,'empty') from t_func where id=1", "h2")));
		assertEquals("admin", querySingle(convert("select nvl(null,name) from t_func where id=1", "h2")));
		// trim
		assertEquals("admin", querySingle(convert("select trim(' admin ') from t_func where id=1", "h2")));
		// length
		assertEquals(5, ((Number) querySingle(convert("select length(name) from t_func where id=1", "h2")))
				.intValue());
		// substr
		assertEquals("adm", querySingle(convert("select substr(name,1,3) from t_func where id=1", "h2")));
		// instr
		assertEquals(2, ((Number) querySingle(convert("select instr(name,'dmi') from t_func where id=1", "h2")))
				.intValue());
		// strpos(pg系写法):h2无strpos,转instr执行,语义一致
		assertEquals(2, ((Number) querySingle(convert("select strpos(name,'dmi') from t_func where id=1", "h2")))
				.intValue());
		// if -> case when
		assertEquals("high",
				querySingle(convert("select if(score > 80, 'high', 'low') from t_func where id=1", "h2")));
		assertEquals("low", querySingle(convert("select if(score > 80, 'high', 'low') from t_func where id=2", "h2")));
		// now
		assertNotNull(querySingle(convert("select now() from t_func where id=1", "h2")));
	}

	private void assertNotNull(Object obj) {
		if (obj == null) {
			throw new AssertionError("查询结果为null");
		}
	}

	// ---------------- 方言转换形态断言 ----------------

	@Test
	public void concatOracleMultiArgs() {
		String converted = convert("select concat(a,b,c) from t", "oracle");
		assertEquals("select a||b||c from t", converted.replaceAll("\\s+", " ").trim(), "oracle三参concat应转||拼接");
	}

	@Test
	public void substrSqlServerTwoArgs() {
		String converted = convert("select substr(name,2) from t", "sqlserver");
		assertTrue(converted.contains("substring(name,2,len(name))"), "sqlserver两参substr应补长度: " + converted);
	}

	@Test
	public void trimSqlServer() {
		String converted = convert("select trim(name) from t", "sqlserver");
		assertEquals("select rtrim(ltrim(name)) from t", converted.replaceAll("\\s+", " ").trim(),
				"sqlserver trim应转rtrim(ltrim)");
	}

	@Test
	public void nowSqlServerAndMysql() {
		assertTrue(convert("select getdate() from t", "mysql").contains("now()"), "mysql getdate应转now");
		assertTrue(convert("select now() from t", "sqlserver").contains("getdate()"), "sqlserver now应转getdate");
	}

	@Test
	public void dateFormatMysqlToOracle() {
		String converted = convert("select date_format(create_time,'%Y-%m-%d %H:%i:%s') from t", "oracle");
		assertTrue(converted.contains("to_char(create_time,'yyyy-MM-dd hh24:mi:ss')"),
				"mysql格式应转oracle to_char格式(格式模型大小写敏感): " + converted);
	}

	/**
	 * update 2026-10-9 java风格独立HH(小时分桶等无分钟形态)归一:冒号邻接归一(HH:mm)不命中,
	 * 原样透传时pg/oracle系的HH=12小时制静默错值;\b词边界不得误伤oracle原生HH24
	 */
	@Test
	public void dateFormatJavaStandaloneHour() {
		String pg = convert("select date_format(create_time,'yyyy-MM-dd HH') from t", "postgresql");
		assertTrue(pg.contains("'yyyy-MM-dd hh24'"), "独立HH应归一为hh24(24小时制): " + pg);
		String kingbase = convert("select to_char(create_time,'yyyy-MM-dd HH') from t", "kingbase");
		assertTrue(kingbase.contains("'yyyy-MM-dd hh24'"), "to_char独立HH应归一为hh24: " + kingbase);
		// HH24为oracle原生token,\bHH\b不得误伤(H与2间无词边界,保持原样大写)
		String hh24 = convert("select date_format(create_time,'HH24:mi:ss') from t", "postgresql");
		assertTrue(hh24.contains("'HH24:mi:ss'"), "oracle原生HH24应保持: " + hh24);
		// 冒号邻接形态既有归一不受影响
		String colon = convert("select date_format(create_time,'yyyy-MM-dd HH:mm:ss') from t", "postgresql");
		assertTrue(colon.contains("'yyyy-MM-dd hh24:mi:ss'"), "HH:mm:ss冒号邻接归一保持: " + colon);
	}

	@Test
	public void dateFormatToH2() throws Exception {
		String converted = convert("select date_format(create_time,'%Y-%m-%d') from t_func", "h2");
		System.out.println("[DateFormat->H2] " + converted);
		try {
			Object v = querySingle(converted);
			System.out.println("[DateFormat->H2] 执行结果=" + v);
		} catch (Exception e) {
			System.out.println("[DateFormat->H2] 执行失败: " + e.getMessage());
		}
	}

	@Test
	public void instrConversions() {
		// oracle的instr转到sqlserver的charindex
		String toSqlserver = convert("select instr(remark,'a') from t", "sqlserver");
		assertTrue(toSqlserver.contains("charindex('a',remark)"), "instr应转charindex(子串,源串): " + toSqlserver);
		// sqlserver的charindex转到oracle的instr
		String toOracle = convert("select charindex('a',remark) from t", "oracle");
		assertTrue(toOracle.contains("instr(remark,'a')"), "charindex应转instr(源串,子串): " + toOracle);
		// position形态
		String position = convert("select position('a' in remark) from t", "oracle");
		assertTrue(position.contains("instr(remark,'a')"), "position应转instr: " + position);
		// 4参(occurrence)charindex无法表达:原样保留交由目标库报错,不静默丢弃改变语义
		String fourArg = convert("select instr(remark,'a',1,2) from t", "sqlserver");
		assertEquals("select instr(remark,'a',1,2) from t", fourArg, "带occurrence的instr应原样保留");
		// 3参(带起始位置)正常转换
		String threeArg = convert("select instr(remark,'a',2) from t", "sqlserver");
		assertTrue(threeArg.contains("charindex('a',remark,2)"), "带起始位置的instr应正常转换: " + threeArg);
	}

	@Test
	public void strposConversions() {
		// strpos(str,sub)为pg系原生写法:pg系原生透传
		String pg = convert("select strpos(remark,'a') from t", "postgresql");
		assertEquals("select strpos(remark,'a') from t", pg, "pg系strpos原生应原样透传");
		// mysql/oracle系:strpos与instr参数序一致,直接换名
		String toMysql = convert("select strpos(remark,'a') from t", "mysql");
		assertTrue(toMysql.contains("instr(remark,'a')"), "strpos应转instr(源串,子串): " + toMysql);
		// sqlserver:charindex参数序相反,须对调
		String toSqlserver = convert("select strpos(remark,'a') from t", "sqlserver");
		assertTrue(toSqlserver.contains("charindex('a',remark)"), "strpos应转charindex(子串,源串): " + toSqlserver);
		// db2与hana都转locate,但两者参数序相反
		String toDb2 = convert("select strpos(remark,'a') from t", "db2");
		assertTrue(toDb2.contains("locate('a',remark)"), "db2的strpos应转locate(子串,源串): " + toDb2);
		String toHana = convert("select strpos(remark,'a') from t", "hana");
		assertTrue(toHana.contains("locate(remark,'a')"), "hana的strpos应转locate(源串,子串): " + toHana);
		// 三参为CH的start_pos扩展,其余库无对应形态:原样保留交由目标库报错
		String threeArg = convert("select strpos(remark,'a',2) from t", "mysql");
		assertEquals("select strpos(remark,'a',2) from t", threeArg, "三参strpos应原样保留");
	}

	@Test
	public void substrNegativeStartConversions() throws Exception {
		// update 2026-10-4 负起点字面量:mysql惯用substr(s,-n)取末n位,原仅sqlserver两参转RIGHT,
		// pg系substring(s,-n)按"串首之前偏移"返回整串静默错值、db2报SQL0138
		String pg = convert("select substr(name,-2) from t", "postgresql");
		assertTrue(pg.contains("RIGHT(name,2)") && pg.contains("else '' end"),
				"pg两参负起点应转RIGHT且短串(|n|>串长)返回空串(mysql契约): " + pg);
		String db2 = convert("select substr(name,-2) from t", "db2");
		assertTrue(db2.contains("RIGHT(name,2)") && db2.contains("else '' end"),
				"db2两参负起点应转RIGHT且短串返回空串: " + db2);
		String mssql = convert("select substr(name,-3) from t", "sqlserver");
		assertTrue(mssql.contains("RIGHT(name,3)") && mssql.contains("else '' end"),
				"sqlserver两参负起点应转RIGHT且短串返回空串: " + mssql);
		// 三参负起点转case守卫形态(update 2026-10-9 短串分支对齐mysql返回空串,
		// 原substring(name,1,1)在串长不足n时与mysql的''偏离)
		String threeArg = convert("select substr(name,-2,1) from t", "postgresql");
		assertTrue(threeArg.contains("case when length(name)<2 then '' else substring(name,length(name)-2+1,1) end"),
				"三参负起点应转case守卫形态且短串返回空串: " + threeArg);
		// H2端到端:两参取末2位、三参起点为串长-n+1
		assertEquals("in", querySingle(convert("select substr(name,-2) from t_func where id=1", "h2")));
		assertEquals("i", querySingle(convert("select substr(name,-2,1) from t_func where id=1", "h2")));
	}

	@Test
	public void nullCheckGapsConversions() throws Exception {
		// update 2026-10-4 单参isnull判空语义(mysql返回0/1):原映射nvl(x)/coalesce(x)
		// (均要求>=2参)目标库报参数个数错
		assertEquals("select case when name is null then 1 else 0 end from t",
				convert("select isnull(name) from t", "oracle"));
		assertEquals("select case when name is null then 1 else 0 end from t",
				convert("select isnull(name) from t", "postgresql"));
		// nvl2(oracle源):非oracle系转case when判非空,oracle系原生透传
		String nvl2Mysql = convert("select nvl2(name,'y','n') from t", "mysql");
		assertTrue(nvl2Mysql.contains("case when name is not null then 'y' else 'n' end"),
				"nvl2应转case when: " + nvl2Mysql);
		assertEquals("select nvl2(name,'y','n') from t", convert("select nvl2(name,'y','n') from t", "oracle"),
				"oracle系nvl2应原生透传");
		// H2端到端
		assertEquals(0, ((Number) querySingle(convert("select isnull(name) from t_func where id=1", "h2")))
				.intValue());
		assertEquals("y", querySingle(convert("select nvl2(name,'y','n') from t_func where id=1", "h2")));
	}

	@Test
	public void nowAndLengthMiscGaps() throws Exception {
		// update 2026-10-4 sqlite的now取本地墙钟(CURRENT_TIMESTAMP为UTC,东八区差8小时)
		assertEquals("select datetime('now','localtime') from t", convert("select now() from t", "sqlite"));
		// systimestamp(oracle源):非oracle系按now路径分派,oracle系原生透传
		assertTrue(convert("select systimestamp from t", "mysql").contains("now()"), "systimestamp应转now");
		assertEquals("select systimestamp from t", convert("select systimestamp from t", "oracle"),
				"oracle系systimestamp应透传");
		// hana目标now转CURRENT_TIMESTAMP(真库执行见HanaRealDbSmokeTest)
		assertEquals("select CURRENT_TIMESTAMP from t", convert("select now() from t", "hana"));
		// systimestamp带精度参数:mysql系转now(fsp)保留
		assertEquals("select now(6) from t", convert("select systimestamp(6) from t", "mysql"));
		// len为字符语义(sqlserver len按字符数):mysql目标转char_length(原转length按字节,非ASCII错值)
		String len = convert("select len(name) from t", "mysql");
		assertTrue(len.contains("char_length(name)"), "len应转char_length: " + len);
		// %T token:CH目标转%H:%i:%s(原残留字面%T)
		String pctT = convert("select to_char(create_time,'%T') from t", "clickhouse");
		assertTrue(pctT.contains("%H:%i:%s"), "%T应转%H:%i:%s: " + pctT);
		// H2端到端:len字符长度、lengthb字节长度(octet_length承接)
		assertEquals(5, ((Number) querySingle(convert("select len(name) from t_func where id=1", "h2"))).intValue());
		assertEquals(5, ((Number) querySingle(convert("select lengthb(name) from t_func where id=1", "h2")))
				.intValue());
		// update 2026-10-4 大写oracle惯用格式模型:.NET FORMAT token大小写敏感,YYYY/DD/MI/SS须映射
		assertEquals("select FORMAT(create_time,'yyyy-MM-dd HH:mm:ss') from t",
				convert("select to_char(create_time,'YYYY-MM-DD HH24:MI:SS') from t", "sqlserver"),
				"to_char大写oracle模型应映射为.NET token");
		assertEquals("select FORMAT(create_time,'yyyy-MM-dd HH:mm:ss') from t",
				convert("select date_format(create_time,'YYYY-MM-DD HH24:MI:SS') from t", "sqlserver"),
				"date_format大写oracle模型应映射为.NET token");
		// 小写与%token形态回归:小写oracle模型中mm(月)须归一为大写MM,.NET才识别为月份
		// (mi转出的分钟mm与月份mm占位符隔离,不误改)
		assertEquals("select FORMAT(create_time,'yyyy-MM-dd HH:mm:ss') from t",
				convert("select to_char(create_time,'yyyy-mm-dd hh24:mi:ss') from t", "sqlserver"),
				"小写oracle模型月/分应分别映射MM/mm");
		assertEquals("select FORMAT(create_time,'yyyy-MM-dd') from t",
				convert("select to_char(create_time,'yyyy-mm-dd') from t", "sqlserver"),
				"全小写模型的mm应识别为月份");
		// %token(java/mysql样式)形态回归:行为不变
		assertEquals("select FORMAT(create_time,'yyyy-MM-dd HH:mm:ss') from t",
				convert("select date_format(create_time,'%Y-%m-%d %H:%i:%s') from t", "sqlserver"),
				"%token样式应保持原有映射");
	}

	@Test
	public void lengthAndNowEdgeCases() {
		// PG系lengthb转octet_length
		String pgLengthb = convert("select lengthb(name) from t", "postgresql");
		assertTrue(pgLengthb.contains("octet_length(name)"), "PG系lengthb应转octet_length: " + pgLengthb);
		// oracle原生lengthb保留
		String oracleLengthb = convert("select lengthb(name) from t", "oracle");
		assertTrue(oracleLengthb.contains("lengthb(name)"), "oracle原生lengthb应保留: " + oracleLengthb);
		// mysql的char_length原生保留(字符长度语义)
		String mysqlCharLen = convert("select char_length(name) from t", "mysql");
		assertTrue(mysqlCharLen.contains("char_length(name)"), "mysql char_length应保留: " + mysqlCharLen);
		// oracle的sysdate原样;mysql的sysdate转now()
		assertEquals("select sysdate from t", convert("select sysdate from t", "oracle"), "oracle sysdate应原样");
		assertTrue(convert("select sysdate from t", "mysql").contains("now()"), "mysql sysdate应转now()");
	}

	@Test
	public void trimModifierFormKeptNative() throws Exception {
		// 带修饰符+剔除空格:mysql写法用在sqlserver按语义转ltrim/rtrim(修复前原样保留导致语法错误)
		String bothSpace = convert("select trim(both ' ' from name) from t", "sqlserver");
		assertEquals("select rtrim(ltrim(name)) from t", bothSpace.replaceAll("\\s+", " ").trim(),
				"both空格应转rtrim(ltrim): " + bothSpace);
		String leadingSpace = convert("select trim(leading from name) from t", "sqlserver");
		assertEquals("select ltrim(name) from t", leadingSpace.replaceAll("\\s+", " ").trim(),
				"leading空格应转ltrim: " + leadingSpace);
		String trailingSpace = convert("select trim(trailing from name) from t", "sqlserver");
		assertEquals("select rtrim(name) from t", trailingSpace.replaceAll("\\s+", " ").trim(),
				"trailing空格应转rtrim: " + trailingSpace);
		// 剔除字符非空格:sqlserver无法表达,原样保留交由目标库报错
		String nonSpace = convert("select trim(both 'x' from name) from t", "sqlserver");
		assertEquals("select trim(both 'x' from name) from t", nonSpace, "非空格剔除字符应原样保留");
		// H2原生支持标准修饰符形态,原样保留
		String h2Modifier = convert("select trim(both ' ' from name) from t", "h2");
		assertEquals("select trim(both ' ' from name) from t", h2Modifier, "H2修饰符形态应原样保留");
		// 普通形态仍正常转换
		assertEquals("select rtrim(ltrim(name)) from t",
				convert("select trim(name) from t", "sqlserver").replaceAll("\\s+", " ").trim(),
				"普通trim应转rtrim(ltrim)");
		// H2端到端执行
		Object v = querySingle(convert("select trim(both ' ' from '  admin  ') from t_func", "h2"));
		assertEquals("admin", v, "修饰符形态在H2应正确执行");
		// sqlite无FROM形态:转trim/ltrim/rtrim二参剔除字符集形态(语义与标准一致)
		String sqliteBoth = convert("select trim(both 'x' from name) from t", "sqlite");
		assertEquals("select trim(name,'x') from t", sqliteBoth.replaceAll("\\s+", " ").trim(),
				"sqlite both应转trim(col,'x'): " + sqliteBoth);
		String sqliteLeading = convert("select trim(leading 'x' from name) from t", "sqlite");
		assertEquals("select ltrim(name,'x') from t", sqliteLeading.replaceAll("\\s+", " ").trim(),
				"sqlite leading应转ltrim(col,'x'): " + sqliteLeading);
		// sqlite普通trim原生保留
		assertEquals("select trim(name) from t", convert("select trim(name) from t", "sqlite"), "sqlite普通trim应保留");
		// sqlite now -> datetime('now','localtime')(update 2026-10-4:原CURRENT_TIMESTAMP为
		// UTC墙钟,东八区差8小时)
		assertTrue(convert("select now() from t", "sqlite").contains("datetime('now','localtime')"),
				"sqlite now应转本地墙钟");
	}

	// ---------------- group_concat多目标转换与多列拼接 ----------------

	@Test
	public void concatAndNowAndSubStrEdges() {
		// sqlite无concat函数:两参起转||
		String sqliteConcat = convert("select concat(a,b) from t", "sqlite");
		assertEquals("select a||b from t", sqliteConcat.replaceAll("\\s+", " ").trim(), "sqlite concat应转||");
		// db2三参转||;两参原生保留
		String db2Three = convert("select concat(a,b,c) from t", "db2");
		assertTrue(db2Three.replaceAll("\\s+", " ").trim().contains("a||b||c"), "db2三参concat应转||: " + db2Three);
		String db2Two = convert("select concat(a,b) from t", "db2");
		assertEquals("select concat(a,b) from t", db2Two.replaceAll("\\s+", " ").trim(), "db2两参concat原生保留");
		// oceanbase(oracle模式)三参转||,与Nvl/Now等函数的oracle系归属一致
		String obThree = convert("select concat(a,b,c) from t", "oceanbase");
		assertTrue(obThree.replaceAll("\\s+", " ").trim().contains("a||b||c"), "oceanbase三参concat应转||: "
				+ obThree);
		// mysql特有now(6)fsp参数:pg目标应丢弃参数
		String nowPg = convert("select now(6) from t", "postgresql");
		assertEquals("select now() from t", nowPg.replaceAll("\\s+", " ").trim(), "now(6)转pg应丢弃fsp参数");
		String nowMysql = convert("select now(6) from t", "mysql");
		assertTrue(nowMysql.contains("now(6)"), "mysql目标now(6)应原样保留: " + nowMysql);
		// 三参instr在mysql转locate
		String instrMysql = convert("select instr(remark,'a',2) from t", "mysql");
		assertTrue(instrMysql.contains("locate('a',remark,2)"), "mysql三参instr应转locate: " + instrMysql);
		// sqlserver负起点substr转RIGHT
		String negSub = convert("select substr(name,-2) from t", "sqlserver");
		assertTrue(negSub.contains("RIGHT(name,2)"), "负起点substr应转RIGHT: " + negSub);
	}

	@Test
	public void groupConcatMatrix() throws Exception {
		// H2端到端:group_concat原生
		assertEquals("admin,user",
				querySingle(convert("select group_concat(name separator ',') from t_func", "h2")));
		// 多列拼接:转到pg应为||重组(修复前丢失列)
		String toPg = convert("select group_concat(a, b separator '-') from t", "postgresql");
		assertTrue(toPg.replaceAll("\\s+", "").contains("ARRAY_AGG(a||b)")
				&& toPg.replaceAll("\\s+", "").contains("),'-'"),
				"PG多列应以||重组且分隔符生效: " + toPg);
		// oracle补listagg转换(修复前缺失,原样输出在oracle非法)
		String toOracle = convert("select group_concat(name separator '-') from t", "oracle");
		assertTrue(toOracle.contains("listagg(name,'-') within group (order by null)"),
				"oracle应转listagg: " + toOracle);
		// sqlserver补string_agg转换(修复前缺失)
		String toSqlserver = convert("select group_concat(name separator '-') from t", "sqlserver");
		assertTrue(toSqlserver.contains("string_agg(name,'-')"), "sqlserver应转string_agg: " + toSqlserver);
		// db2补listagg转换(修复前缺失);db2的listagg不接受order by null,省略within group子句
		String toDb2 = convert("select group_concat(name separator '-') from t", "db2");
		assertTrue(toDb2.contains("listagg(name,'-')") && !toDb2.contains("within group"),
				"db2应转listagg(DB2不接受order by null,省略within group): " + toDb2);
		// mysql原生string_agg转group_concat
		String mysqlStrAgg = convert("select string_agg(name,'-') from t", "mysql");
		assertTrue(mysqlStrAgg.contains("group_concat(name separator '-')"), "string_agg应转group_concat: "
				+ mysqlStrAgg);
		// H2端到端执行string_agg转group_concat结果
		assertEquals("admin-user", querySingle(convert("select string_agg(name,'-') from t_func", "h2")));
	}

	@Test
	public void toDateImplementation() throws Exception {
		// 正则收窄:mysql原生date()不再被误伤(to_date仅匹配自身)
		assertEquals("select date(create_time) from t", convert("select date(create_time) from t", "mysql"),
				"mysql原生date()应原样保留");
		// mysql:1参DATE()取日期;2参STR_TO_DATE+格式token互换
		String mysql1 = convert("select to_date(create_time) from t", "mysql");
		assertEquals("select DATE(create_time) from t", mysql1.replaceAll("\\s+", " ").trim(),
				"mysql 1参to_date应转DATE()");
		String mysql2 = convert("select to_date(create_time,'yyyy-MM-dd') from t", "mysql");
		assertTrue(mysql2.contains("STR_TO_DATE(create_time,'%Y-%m-%d')"),
				"mysql 2参应转STR_TO_DATE且格式token互换: " + mysql2);
		// pg:2参to_date(text,text)原生保留;1参转CAST AS date
		String pg2 = convert("select to_date(create_time,'yyyy-MM-dd') from t", "postgresql");
		assertEquals("select to_date(create_time,'yyyy-MM-dd') from t", pg2, "pg 2参to_date原生应保留");
		String pg1 = convert("select to_date(create_time) from t", "postgresql");
		assertTrue(pg1.contains("CAST(create_time AS date)"), "pg 1参应转CAST AS date: " + pg1);
		// oracle原样
		String oracle2 = convert("select to_date(create_time,'yyyy-MM-dd') from t", "oracle");
		assertEquals("select to_date(create_time,'yyyy-MM-dd') from t", oracle2, "oracle原生to_date应保留");
		// H2端到端:2参parsedatetime解析执行
		Object v = querySingle(convert("select to_date('2024-01-01','yyyy-MM-dd') from t_func where id=1", "h2"));
		assertTrue(v.toString().startsWith("2024-01-01"), "H2 to_date解析执行结果: " + v);
	}

	@Test
	public void functionTextInsideLiteralNotConverted() throws Exception {
		// 系统性修复验证:replaceFunction匹配走字面量掩码串,
		// 字面量内的函数文本(如'nvl(a,b)'、'sysdate'、'if(a,b)')不得被转换破坏
		String literalOnly = "select remark from t_func where remark='nvl(a,b)'";
		assertEquals(literalOnly, convert(literalOnly, "mysql"), "字面量含函数文本时语句必须原样保留");
		String mixed = convert("select nvl(remark,'nvl(a,b)') from t_func where id=1", "mysql");
		assertTrue(mixed.contains("ifnull(remark,'nvl(a,b)')"), "外层真实nvl应转换且字面量原样保留: " + mixed);
		assertEquals("select remark from t_func where remark='sysdate'",
				convert("select remark from t_func where remark='sysdate'", "mysql"), "sysdate字面量不得转now()");
		assertEquals("select remark from t_func where remark='if(a,b)'",
				convert("select remark from t_func where remark='if(a,b)'", "mysql"), "if字面量不得转case when");
	}

	@Test
	public void toNumberImplementation() throws Exception {
		// 单参:H2端到端执行(CAST AS DECIMAL)
		assertEquals(88.5, ((Number) querySingle(convert("select to_number('88.5') from t_func where id=1", "h2")))
				.doubleValue(), 0.000001, "单参to_number应转CAST并正确执行");
		assertEquals(88.5,
				((Number) querySingle(convert("select to_number(score) from t_func where id=1", "h2")))
						.doubleValue(),
				0.000001, "数值列to_number应正常");
		// 方言形态:pg系numeric无损;mysql/sqlserver DECIMAL
		String toPg = convert("select to_number(score) from t", "postgresql");
		assertTrue(toPg.contains("CAST(score AS numeric)"), "pg单参to_number应转numeric: " + toPg);
		String toMysql = convert("select to_number(score) from t", "mysql");
		assertTrue(toMysql.contains("CAST(score AS DECIMAL(20,6))"), "mysql单参to_number应转DECIMAL: " + toMysql);
		// oracle原生保留
		assertEquals("select to_number(score) from t", convert("select to_number(score) from t", "oracle"),
				"oracle原生to_number应原样保留");
		// 带格式模型的2参形态不转换(格式模型各库差异大)
		String twoArg = convert("select to_number(score,'9999.99') from t", "mysql");
		assertEquals("select to_number(score,'9999.99') from t", twoArg, "格式模型形态应原样保留");
	}

	@Test
	public void nowAndToCharEdgeCases() throws Exception {
		// db2补CURRENT TIMESTAMP(修复前sysdate/now原样输出在db2非法)
		String toDb2 = convert("select sysdate from t", "db2");
		assertTrue(toDb2.contains("CURRENT TIMESTAMP"), "db2 sysdate应转CURRENT TIMESTAMP: " + toDb2);
		// clickhouse补now()
		assertTrue(convert("select now() from t", "clickhouse").contains("now()"), "clickhouse now应支持");
		// oracle的sysdate原样
		assertEquals("select sysdate from t", convert("select sysdate from t", "oracle"));
		// ToChar H2分支:%H为24小时制应转hh24(修复前hh为12小时制)
		String toCharH2 = convert("select to_char(create_time,'%Y-%m-%d %H:%i:%s') from t", "h2");
		assertTrue(toCharH2.contains("hh24:mi:ss"), "to_char H2的%H应转hh24: " + toCharH2);
		// H2端到端执行to_char
		assertEquals("2026-01-15", querySingle(convert(
				"select to_char(create_time,'%Y-%m-%d') from t_func where id=1", "h2")));
	}

	// ---------------- 显式启用函数(Decode/DateDiff) ----------------

	@Test
	public void decodeOddAndEvenArgs() {
		FunctionUtils.setFunctionConverts(java.util.Arrays
				.asList("org.sagacity.sqltoy.plugins.function.impl.Decode"));
		try {
			// 奇数参数(带默认值):decode(type,'1','普通','2','VIP','其他')
			String odd = convert("select decode(type,'1','普通','2','VIP','其他') from t", "oracle");
			assertEquals("select decode(type,'1','普通','2','VIP','其他') from t", odd, "oracle原样支持decode");
			String toMysql = convert("select decode(type,'1','普通','2','VIP','其他') from t", "mysql");
			assertTrue(toMysql.contains("when type='1' then '普通'") && toMysql.contains("else '其他'"),
					"decode应转case when: " + toMysql);
			// 偶数参数(无默认值):oracle语义为无匹配返回null,最后一个结果值不得误当默认值
			String even = convert("select decode(type,'1','普通','2','VIP') from t", "mysql");
			assertFalse(even.contains("else"), "偶数参数不应产生else子句(最后一个结果值被误当默认值): " + even);
		} finally {
			FunctionUtils.setFunctionConverts(java.util.Arrays.asList("default"));
		}
	}

	@Test
	public void dateDiffContractMatrix() {
		FunctionUtils.setFunctionConverts(java.util.Arrays
				.asList("org.sagacity.sqltoy.plugins.function.impl.DateDiff"));
		try {
			// 两参契约d1-d2
			String oracle2 = convert("select datediff(end_time,start_time) from t", "oracle");
			assertEquals("select (TRUNC(end_time) - TRUNC(start_time)) from t",
					oracle2.replaceAll("\\s+", " ").trim(), "oracle两参应为d1-d2");
			String sqlserver2 = convert("select datediff(end_time,start_time) from t", "sqlserver");
			assertEquals("select DATEDIFF(DAY,start_time,end_time) from t",
					sqlserver2.replaceAll("\\s+", " ").trim(), "sqlserver两参应转DATEDIFF(DAY,d2,d1)");
			// mysql两参datediff原生支持,原样保留
			String mysql2 = convert("select datediff(end_time,start_time) from t", "mysql");
			assertEquals("select datediff(end_time,start_time) from t", mysql2, "mysql两参datediff应原样保留");
			// 三参契约d2-d1
			String oracle3 = convert("select datediff(day,start_time,end_time) from t", "oracle");
			assertEquals("select (TRUNC(end_time) - TRUNC(start_time)) from t",
					oracle3.replaceAll("\\s+", " ").trim(), "oracle三参应为d2-d1");
			String sqlserver3 = convert("select datediff(day,start_time,end_time) from t", "sqlserver");
			assertEquals("select DATEDIFF(DAY,start_time,end_time) from t",
					sqlserver3.replaceAll("\\s+", " ").trim(), "sqlserver三参参数顺序一致,仅映射单位");
			// update 2026-9-5 口径统一:DAY为自然天差,mysql统一转DATEDIFF(timestampdiff的DAY按完整24小时锚定,跨库不同值)
			String mysql3 = convert("select timestampdiff(DAY,start_time,end_time) from t", "mysql");
			assertEquals("select DATEDIFF(end_time,start_time) from t", mysql3.replaceAll("\\s+", " ").trim(),
					"mysql三参DAY应转自然天差DATEDIFF");
			String mysql3Datediff = convert("select datediff(day,start_time,end_time) from t", "mysql");
			assertEquals("select DATEDIFF(end_time,start_time) from t", mysql3Datediff.replaceAll("\\s+", " ").trim(),
					"mysql三参datediff(day)应转DATEDIFF");
			// 年/月为年月分量差
			String mysqlYear = convert("select datediff(year,start_time,end_time) from t", "mysql");
			assertEquals("select (YEAR(end_time) - YEAR(start_time)) from t",
					mysqlYear.replaceAll("\\s+", " ").trim(), "mysql年差应为年分量差");
			String mysqlMonth = convert("select datediff(month,start_time,end_time) from t", "mysql");
			assertEquals("select ((YEAR(end_time) - YEAR(start_time))*12 + MONTH(end_time) - MONTH(start_time)) from t",
					mysqlMonth.replaceAll("\\s+", " ").trim(), "mysql月差应为年月分量差");
			// pg系:两参/三参day用::date自然天差值(规避date_part应用于date相减整数报错)
			String pg2 = convert("select datediff(end_time,start_time) from t", "postgresql");
			assertEquals("select (end_time::date - start_time::date) from t", pg2.replaceAll("\\s+", " ").trim(),
					"pg两参应为::date自然天差值");
			String pg3Day = convert("select datediff(day,start_time,end_time) from t", "postgresql");
			assertEquals("select (end_time::date - start_time::date) from t", pg3Day.replaceAll("\\s+", " ").trim(),
					"pg三参day应为d2-d1自然天差值");
			// 口径统一:周为自然天差/7整数截断,时为完整小时截断
			// update 2026-9-14 补trunc断言:2026-9-10 vastbase G100实测整数/整数返回double,
			// 周差统一trunc截断保证跨库同值(vanilla PG上trunc幂等),本断言同步更新
			String pgWeek = convert("select datediff(week,start_time,end_time) from t", "postgresql");
			assertEquals("select trunc(((end_time::date - start_time::date)/7)) from t",
					pgWeek.replaceAll("\\s+", " ").trim(), "pg周差应为自然天差/7整数截断");
			String pgHour = convert("select datediff(hour,start_time,end_time) from t", "postgresql");
			assertEquals("select trunc(extract(epoch from(end_time::timestamp - start_time::timestamp))/3600) from t",
					pgHour.replaceAll("\\s+", " ").trim(), "pg小时差应为完整小时截断");
			// sqlserver口径统一:周/时改自然天差与秒差整除(原生按边界计数且WEEK受DATEFIRST影响),年保持原生(恰为分量差)
			String ssWeek = convert("select datediff(week,start_time,end_time) from t", "sqlserver");
			assertEquals("select (DATEDIFF(DAY,start_time,end_time)/7) from t",
					ssWeek.replaceAll("\\s+", " ").trim(), "sqlserver周差应为自然天差/7");
			String ssHour = convert("select datediff(hour,start_time,end_time) from t", "sqlserver");
			assertEquals("select (DATEDIFF(SECOND,start_time,end_time)/3600) from t",
					ssHour.replaceAll("\\s+", " ").trim(), "sqlserver小时差应为秒差/3600整除");
			String ssYear = convert("select datediff(year,start_time,end_time) from t", "sqlserver");
			assertEquals("select DATEDIFF(YEAR,start_time,end_time) from t", ssYear.replaceAll("\\s+", " ").trim(),
					"sqlserver年差边界计数恰为年分量差,保持原生");
			// oracle三参周期单位:周为自然天差/7截断(TRUNC后已DATE化),时为CAST AS DATE后完整小时截断
			// (修复:timestamp列直接相减返回INTERVAL,interval乘除系数仍是interval,java侧拿到非数值)
			String oracleWeek = convert("select datediff(week,start_time,end_time) from t", "oracle");
			assertEquals("select TRUNC((TRUNC(end_time) - TRUNC(start_time))/7) from t",
					oracleWeek.replaceAll("\\s+", " ").trim(), "oracle周差应为自然天差/7截断");
			String oracleHour = convert("select datediff(hour,start_time,end_time) from t", "oracle");
			assertEquals("select TRUNC((CAST(end_time AS DATE) - CAST(start_time AS DATE))*24) from t",
					oracleHour.replaceAll("\\s+", " ").trim(), "oracle小时差应为CAST AS DATE后完整小时截断");
			// oracle年/月为年月分量差
			String oracleYear = convert("select datediff(year,start_time,end_time) from t", "oracle");
			assertEquals("select (EXTRACT(YEAR FROM end_time) - EXTRACT(YEAR FROM start_time)) from t",
					oracleYear.replaceAll("\\s+", " ").trim(), "oracle年差应为年分量差");
			String oracleMonth = convert("select datediff(month,start_time,end_time) from t", "oracle");
			assertEquals("select MONTHS_BETWEEN(TRUNC(end_time,'MM'),TRUNC(start_time,'MM')) from t",
					oracleMonth.replaceAll("\\s+", " ").trim(), "oracle月差应为年月分量差");
			// openGauss系:date-date返回interval(与vanilla PG整数天不同)且无原生datediff,
			// 两参/三参day走date_part('day',interval)取整数天,周/时走截断换算
			String og2 = convert("select datediff(end_time,start_time) from t", "opengauss");
			assertEquals("select (date_part('day',(end_time::date - start_time::date))) from t",
					og2.replaceAll("\\s+", " ").trim(), "openGauss两参应为date_part整数天差");
			String og3Day = convert("select datediff(day,start_time,end_time) from t", "opengauss");
			assertEquals("select (date_part('day',(end_time::date - start_time::date))) from t",
					og3Day.replaceAll("\\s+", " ").trim(), "openGauss三参day应为d2-d1整数天差");
			String ogWeek = convert("select datediff(week,start_time,end_time) from t", "opengauss");
			assertEquals("select trunc(date_part('day',(end_time::date - start_time::date))/7) from t",
					ogWeek.replaceAll("\\s+", " ").trim(), "openGauss周差应为自然天差/7整数截断");
			String ogHour = convert("select datediff(hour,start_time,end_time) from t", "opengauss");
			assertEquals("select trunc(extract(epoch from(end_time::timestamp - start_time::timestamp))/3600) from t",
					ogHour.replaceAll("\\s+", " ").trim(), "openGauss小时差应为完整小时截断");
			// oracle裸字符串字面量参数包to_date(修复前TRUNC('...')在默认NLS_DATE_FORMAT下报ORA-01722/ORA-01861)
			String oracleLit2 = convert("select datediff('2026-01-20','2026-01-15') from dual", "oracle");
			assertEquals(
					"select (TRUNC(to_date('2026-01-20','yyyy-MM-dd')) - TRUNC(to_date('2026-01-15','yyyy-MM-dd'))) from dual",
					oracleLit2.replaceAll("\\s+", " ").trim(), "oracle两参字面量应包to_date");
			String oracleLitTime = convert("select datediff('2026-01-20 10:30:00','2026-01-15 08:00:00') from dual",
					"oracle");
			assertTrue(oracleLitTime.replace(" ", "").contains("to_date('2026-01-2010:30:00','yyyy-MM-ddhh24:mi:ss')"),
					"oracle含时间字面量应按hh24:mi:ss包to_date: " + oracleLitTime);
			// 列与字面量混合参数:仅字面量被包to_date
			String oracleColMixed = convert("select datediff(day,'2026-01-15',end_time) from t", "oracle");
			assertTrue(oracleColMixed.replace(" ", "")
					.contains("(TRUNC(end_time)-TRUNC(to_date('2026-01-15','yyyy-MM-dd')))"),
					"oracle混合参数应仅字面量包to_date: " + oracleColMixed);
		} finally {
			FunctionUtils.setFunctionConverts(java.util.Arrays.asList("default"));
		}
	}

	// ---------------- concat_ws多目标转换 ----------------

	@Test
	public void concatWsMatrix() throws Exception {
		// H2端到端:H2原生concat_ws
		assertEquals("a-b", querySingle(convert("select concat_ws('-','a','b') from t_func where id=1", "h2")));
		// oracle/sqlite/db2/oceanbase:转||拼接(修复前原样输出concat_ws在这些库非法)
		// update 2026-9-15 null语义修复:改逐段case when跳过null参数及其分隔符(原朴素
		// name||'-'||remark在remark为null时得'abc-'悬挂分隔符,违背mysql concat_ws跳null契约),
		// 断言生成形态含首参coalesce兜底与null参数的case when跳过段
		String toOracle = convert("select concat_ws('-',name,remark) from t_func where id=1", "oracle");
		assertTrue(toOracle.contains("coalesce(name,'')") && toOracle.contains("case when remark is null then ''"),
				"oracle应转逐段跳null拼接: " + toOracle);
		String toSqlite = convert("select concat_ws('-',name,remark) from t_func where id=1", "sqlite");
		assertTrue(toSqlite.contains("coalesce(name,'')") && toSqlite.contains("case when remark is null then ''"),
				"sqlite应转逐段跳null拼接: " + toSqlite);
		String toDb2 = convert("select concat_ws('-',name,remark) from t_func where id=1", "db2");
		// db2的coalesce(:param,'')会把参数推断为VARCHAR(0)致-302,兜底空串须显式CAST定型
		assertTrue(toDb2.contains("CAST('' AS VARCHAR(4000))") && toDb2.contains("case when remark is null then ''"),
				"db2应转逐段跳null拼接且兜底cast: " + toDb2);
		String toOb = convert("select concat_ws('-',name,remark) from t_func where id=1", "oceanbase");
		assertTrue(toOb.contains("coalesce(name,'')") && toOb.contains("case when remark is null then ''"),
				"oceanbase应转逐段跳null拼接: " + toOb);
		// mysql原生保留
		String mysql = convert("select concat_ws('-',name,remark) from t_func where id=1", "mysql");
		assertTrue(mysql.contains("concat_ws('-'"), "mysql原生concat_ws应保留: " + mysql);
	}

	// ---------------- 下划线前缀自定义函数防误命中 ----------------

	/**
	 * update 2026-9-10 锁定保证:base64_decode/my_substr等下划线前缀的自定义函数名不被误改写。
	 * 机制:函数正则统一\W前缀,Java正则中_属于\w(非\W),xxx_decode(的下划线位构不成匹配;
	 * 若未来调整正则前缀形态(如改零宽断言),本用例防止该行为回退。
	 */
	@Test
	public void underscorePrefixedCustomFunctionsUntouched() {
		String sql = "select base64_decode(col,'a','b'),my_substr(col,1,2),xxx_decode(a,b,c),"
				+ "my_nvl(a,b),x_to_char(d,'yyyy'),my_group_concat(col) from t";
		for (String dialect : new String[] { "mysql", "oracle", "sqlserver", "h2", "postgresql", "db2", "sqlite",
				"oceanbase" }) {
			assertEquals(sql, convert(sql, dialect), dialect + "不应改写下划线前缀自定义函数");
		}
	}
}
