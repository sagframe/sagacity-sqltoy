package org.sagacity.sqltoy.plugins.function;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 常用函数补齐(2026-10-11)的形态测试:uuid/year-month-day/last_day/add_months
 * 各方言分支逐行锁定;执行语义由CommonFuncRealDbSmokeTest在10库+H2+sqlite上验证。
 */
public class CommonFuncDialectTest {

	@BeforeAll
	public static void init() {
		FunctionUtils.setFunctionConverts(java.util.Arrays.asList("default"));
	}

	private String c(String sql, String dialect) {
		return FunctionUtils.getDialectSql(sql, dialect);
	}

	@Test
	public void uuidConversions() {
		// 原生透传:mysql系(含OB)/h2/vastbase
		assertEquals("select uuid() from t", c("select uuid() from t", "mysql"));
		assertEquals("select uuid() from t", c("select uuid() from t", "oceanbase"));
		assertEquals("select uuid() from t", c("select uuid() from t", "h2"));
		assertEquals("select uuid() from t", c("select uuid() from t", "vastbase"));
		// 36位带连字符族
		assertEquals("select gen_random_uuid() from t", c("select uuid() from t", "postgresql"));
		assertEquals("select NEWID() from t", c("select uuid() from t", "sqlserver"));
		assertEquals("select NEWID() from t", c("select uuid() from t", "dm"));
		assertEquals("select generateUUIDv4() from t", c("select uuid() from t", "clickhouse"));
		// 32位hex族(无连字符)
		assertEquals("select RAWTOHEX(SYS_GUID()) from t", c("select uuid() from t", "oracle"));
		assertEquals("select sys_guid() from t", c("select uuid() from t", "kingbase"));
		assertEquals("select TO_VARCHAR(SYSUUID) from t", c("select uuid() from t", "hana"));
		// sqlite组合形态含版本4与variant位
		String lite = c("select uuid() from t", "sqlite");
		assertEquals(true, lite.contains("randomblob(4)") && lite.contains("'-4'")
				&& lite.startsWith("select lower(hex("), "sqlite应组合v4形态并整体小写");
		// og系(PG内核)降级合成:uuid族→v4形态36位(版本4+variant位),sys_guid族→32hex md5
		// update 2026-10-12 作者要求逐成员核实:PG内核三成员(og/gaussdb/mogdb)同走合成,
		// oscar/stardb内核非PG系谱未证实走透传——成员级分派全部锁定
		String ogv = c("select uuid() from t", "opengauss");
		assertEquals(true, ogv.startsWith("select substr(md5(random()::text)")
				&& ogv.contains("'-4'") && ogv.contains("'89ab'"), "og应合成v4形态");
		for (String og : new String[] { "gaussdb", "mogdb" }) {
			assertEquals(ogv, c("select uuid() from t", og), og + "应与opengauss同走PG内核合成");
			assertEquals("select md5(random()::text || clock_timestamp()::text) from t",
					c("select sys_guid() from t", og), og + " sys_guid应合成32hex md5");
		}
		assertEquals("select md5(random()::text || clock_timestamp()::text) from t",
				c("select sys_guid() from t", "opengauss"), "og sys_guid应合成32hex md5");
		// uuid-ossp标准拼写透传(运维部署扩展后原生生效)
		assertEquals("select uuid_generate_v4() from t", c("select uuid_generate_v4() from t", "opengauss"));
		// oscar/stardb内核未证实,维持透传响亮
		assertEquals("select uuid() from t", c("select uuid() from t", "oscar"));
		// db2(12.1.3+内建GENERATE_UUID,归一generate_uuid拼写)
		assertEquals("select generate_uuid() from t", c("select uuid() from t", "db2"));
		assertEquals("select generate_uuid() from t", c("select newid() from t", "db2"));
		// db2原生拼写透传
		assertEquals("select generate_uuid() from t", c("select generate_uuid() from t", "db2"));
		// generateUUIDv4(ch写法)现为受支持源拼写:pg目标转核心函数(旧"不误命中"断言随之过时)
		assertEquals("select gen_random_uuid() from t", c("select generateUUIDv4() from t", "postgresql"));
		assertEquals("select uuid_short() from t", c("select uuid_short() from t", "mysql"));
	}

	@Test
	public void yearMonthDayConversions() {
		// 原生透传:mysql系/sqlserver/db2/dm/h2/ch
		assertEquals("select year(create_time) from t", c("select year(create_time) from t", "sqlserver"));
		assertEquals("select month(create_time) from t", c("select month(create_time) from t", "db2"));
		// hana:year/month原生,day()不存在(真库实测)走EXTRACT
		assertEquals("select EXTRACT(DAY FROM create_time) from t", c("select day(create_time) from t", "hana"));
		assertEquals("select year(create_time) from t", c("select year(create_time) from t", "hana"));
		// kingbase:year()可用但day()不存在,统一EXTRACT(真库实测)
		assertEquals("select EXTRACT(YEAR FROM create_time) from t",
				c("select year(create_time) from t", "kingbase"));
		// oracle/pg系/og系转EXTRACT
		assertEquals("select EXTRACT(YEAR FROM create_time) from t",
				c("select year(create_time) from t", "oracle"));
		assertEquals("select EXTRACT(MONTH FROM create_time) from t",
				c("select month(create_time) from t", "postgresql"));
		assertEquals("select EXTRACT(DAY FROM create_time) from t", c("select day(create_time) from t", "opengauss"));
		// sqlite转cast+strftime(%m/%d零填充文本,cast对齐mysql数值语义;字面量透传形态)
		assertEquals("select cast(strftime('%Y','2026-03-20') as integer) from t",
				c("select year('2026-03-20') from t", "sqlite"));
		assertEquals("select cast(strftime('%m','2026-03-20') as integer) from t",
				c("select month('2026-03-20') from t", "sqlite"));
		// 关键字形态不误命中:dateadd/datediff的分量词后无括号(datediff的DATE化产物系
		// DateDiff适配器自身的单位归一化行为,与year/month/day无关)
		assertEquals("select dateadd(month, 2, create_time) from t",
				c("select dateadd(month, 2, create_time) from t", "sqlserver"));
		assertEquals("select DATEDIFF(DAY, a, b) from t", c("select datediff(day, a, b) from t", "sqlserver"));
	}

	@Test
	public void lastDayConversions() {
		// 原生透传:oracle系/dm/hana/db2/h2/og系/kingbase/mysql系
		assertEquals("select last_day(create_time) from t", c("select last_day(create_time) from t", "oracle"));
		assertEquals("select last_day(create_time) from t", c("select last_day(create_time) from t", "opengauss"));
		assertEquals("select last_day(create_time) from t", c("select last_day(create_time) from t", "kingbase"));
		assertEquals("select last_day(create_time) from t", c("select last_day(create_time) from t", "mysql"));
		assertEquals("select last_day(create_time) from t", c("select last_day(create_time) from t", "db2"));
		// vanilla pg组合形态
		assertEquals("select ((date_trunc('month',create_time) + interval '1 month' - interval '1 day')::date) from t",
				c("select last_day(create_time) from t", "postgresql"));
		// sqlserver/ch/sqlite
		assertEquals("select EOMONTH(create_time) from t", c("select last_day(create_time) from t", "sqlserver"));
		assertEquals("select toLastDayOfMonth(create_time) from t",
				c("select last_day(create_time) from t", "clickhouse"));
		String lite = c("select last_day(create_time) from t", "sqlite");
		assertEquals(true, lite.contains("'start of month','+1 month','-1 day'"), "sqlite修饰符链形态: " + lite);
	}

	@Test
	public void addMonthsConversions() {
		// 原生透传:oracle系/dm/hana/og系/kingbase
		assertEquals("select add_months(create_time,2) from t", c("select add_months(create_time,2) from t", "oracle"));
		assertEquals("select add_months(create_time,2) from t", c("select add_months(create_time,2) from t", "opengauss"));
		assertEquals("select add_months(create_time,2) from t", c("select add_months(create_time,2) from t", "kingbase"));
		// mysql系转DATE_ADD
		assertEquals("select DATE_ADD(create_time, INTERVAL 2 MONTH) from t",
				c("select add_months(create_time,2) from t", "mysql"));
		assertEquals("select DATEADD(month, 2, create_time) from t",
				c("select add_months(create_time,2) from t", "sqlserver"));
		// vanilla pg的make_interval(命名参数)
		assertEquals("select (create_time + make_interval(months => 2)) from t",
				c("select add_months(create_time,2) from t", "postgresql"));
		assertEquals("select (create_time + 2 MONTHS) from t", c("select add_months(create_time,2) from t", "db2"));
		assertEquals("select addMonths(create_time,2) from t", c("select add_months(create_time,2) from t", "clickhouse"));
		assertEquals("select DATEADD('MONTH', 2, create_time) from t",
				c("select add_months(create_time,2) from t", "h2"));
		// sqlite响亮放弃(月份修饰符不钳制月末,实测2026-01-31+1月=2026-03-03)
		assertEquals("select add_months(create_time,2) from t", c("select add_months(create_time,2) from t", "sqlite"));
	}
	// ============ 2026-10-11 全量拼写审计:别库用户的原生写法作为源 ==========

	@Test
	public void uuidMultiSpellingMatrix() {
		// 本库原生拼写透传
		assertEquals("select guid() from t", c("select guid() from t", "dm"));
		assertEquals("select newid() from t", c("select newid() from t", "sqlserver"));
		assertEquals("select sys_guid() from t", c("select sys_guid() from t", "oracle"));
		// 外来拼写转该库等价物
		assertEquals("select uuid() from t", c("select guid() from t", "mysql"));
		assertEquals("select uuid() from t", c("select newid() from t", "mysql"));
		assertEquals("select uuid() from t", c("select sys_guid() from t", "mysql"));
		assertEquals("select gen_random_uuid() from t", c("select guid() from t", "postgresql"));
		assertEquals("select NEWID() from t", c("select uuid() from t", "dm"));
		assertEquals("select sys_guid() from t", c("select guid() from t", "kingbase"));
		assertEquals("select random_uuid() from t", c("select guid() from t", "h2"));
	}

	@Test
	public void lastDayMultiSpellingMatrix() {
		assertEquals("select eomonth(create_time) from t", c("select eomonth(create_time) from t", "sqlserver"));
		assertEquals("select last_day(create_time) from t", c("select eomonth(create_time) from t", "mysql"));
		assertEquals("select last_day(create_time) from t", c("select eomonth(create_time) from t", "oracle"));
		assertEquals("select last_day(create_time) from t", c("select eomonth(create_time) from t", "h2"));
		assertEquals("select ((date_trunc('month',create_time) + interval '1 month' - interval '1 day')::date) from t",
				c("select eomonth(create_time) from t", "postgresql"));
	}

	@Test
	public void instrMultiSpellingMatrix() {
		// db2原生posstr为haystack-first(haystack,needle),db2目标透传
		assertEquals("select locate('a',name) from t", c("select locate('a',name) from t", "mysql"));
		assertEquals("select locate('a',name) from t", c("select locate('a',name) from t", "db2"));
		assertEquals("select posstr(name,'a') from t", c("select posstr(name,'a') from t", "db2"));
		// 外来拼写转换
		assertEquals("select position('a' in name) from t", c("select locate('a',name) from t", "postgresql"));
		assertEquals("select instr(name,'a',2) from t", c("select locate('a',name,2) from t", "oracle"));
		// posstr(haystack,needle)与strpos同序:目标SQLServer为charindex(needle,haystack)
		assertEquals("select charindex('a',name) from t", c("select posstr(name,'a') from t", "sqlserver"));
		// posstr到PG转标准position(needle in haystack)
		assertEquals("select position('a' in name) from t", c("select posstr(name,'a') from t", "postgresql"));
		assertEquals("select locate(name,'a') from t", c("select locate('a',name) from t", "hana"));
		assertEquals("select instr(name,'a') from t", c("select locate('a',name) from t", "sqlite"));
		assertEquals("select position(name,'a') from t", c("select locate('a',name) from t", "clickhouse"));
		// pg的3参locate无对应形态响亮保留
		assertEquals("select locate('a',name,2) from t", c("select locate('a',name,2) from t", "postgresql"));
	}

	@Test
	public void lengthMultiSpellingMatrix() {
		assertEquals("select len(name) from t", c("select character_length(name) from t", "sqlserver"));
		assertEquals("select length(name) from t", c("select character_length(name) from t", "oracle"));
		assertEquals("select lengthb(name) from t", c("select octet_length(name) from t", "oracle"));
		assertEquals("select datalength(name) from t", c("select octet_length(name) from t", "sqlserver"));
		assertEquals("select octet_length(name) from t", c("select octet_length(name) from t", "postgresql"));
		assertEquals("select length(name) from t", c("select octet_length(name) from t", "mysql"));
	}

	@Test
	public void ifMultiSpellingMatrix() {
		// iif为sqlserver(2012+)/sqlite(3.32+)原生
		assertEquals("select iif(score>80,'h','l') from t", c("select iif(score>80,'h','l') from t", "sqlserver"));
		assertEquals("select iif(score>80,'h','l') from t", c("select iif(score>80,'h','l') from t", "sqlite"));
		assertEquals("select if(score>80,'h','l') from t", c("select iif(score>80,'h','l') from t", "mysql"));
		assertEquals("select  case when score>80 then 'h' else 'l' end  from t",
				c("select iif(score>80,'h','l') from t", "oracle"));
	}

	@Test
	public void nowMultiSpellingMatrix() {
		// 标准SQL关键字:sqlite的UTC墙钟转本地,其余库为原生等价物
		assertEquals("select datetime('now','localtime') from t", c("select current_timestamp from t", "sqlite"));
		assertEquals("select now() from t", c("select current_timestamp from t", "mysql"));
		assertEquals("select now() from t", c("select current_timestamp from t", "postgresql"));
	}

	@Test
	public void substrMultiSpellingMatrix() {
		assertEquals("select substr(name,2,3) from t", c("select mid(name,2,3) from t", "mysql"));
		assertEquals("select substring(name,2,3) from t", c("select mid(name,2,3) from t", "postgresql"));
		// hana无mid():非substr拼写换名
		assertEquals("select substr(name,1,3) from t", c("select mid(name,1,3) from t", "hana"));
	}

	@Test
	public void yearMonthDayExtendedParts() {
		// hour/minute/second:sqlserver无函数形态转DATEPART;pg系/oracle转EXTRACT;mysql原生
		assertEquals("select DATEPART(hour,create_time) from t", c("select hour(create_time) from t", "sqlserver"));
		assertEquals("select EXTRACT(MINUTE FROM create_time) from t",
				c("select minute(create_time) from t", "postgresql"));
		assertEquals("select EXTRACT(SECOND FROM create_time) from t",
				c("select second(create_time) from t", "hana"));
		assertEquals("select hour(create_time) from t", c("select hour(create_time) from t", "mysql"));
		// dayofmonth:mysql原生;sqlserver换名day;oracle转EXTRACT
		assertEquals("select dayofmonth(create_time) from t", c("select dayofmonth(create_time) from t", "mysql"));
		assertEquals("select day(create_time) from t", c("select dayofmonth(create_time) from t", "sqlserver"));
		assertEquals("select EXTRACT(DAY FROM create_time) from t",
				c("select dayofmonth(create_time) from t", "oracle"));
		// sqlite的hour:cast+strftime(数值语义)
		assertEquals("select cast(strftime('%H',datetime(create_time/1000,'unixepoch','localtime')) as integer) from t",
				c("select hour(create_time) from t", "sqlite"));
	}

	@Test
	public void toDateMultiSpellingMatrix() {
		// str_to_date为mysql原生写法
		assertEquals("select str_to_date('2026-01-15','%Y-%m-%d') from t",
				c("select str_to_date('2026-01-15','%Y-%m-%d') from t", "mysql"));
		// 其余库:%token反转为oracle风格后按to_date两参路径
		assertEquals("select to_date('2026-01-15','yyyy-MM-dd') from t",
				c("select str_to_date('2026-01-15','%Y-%m-%d') from t", "oracle"));
		// update 2026-10-20 pg的to_date只返回date,含时间token时改to_timestamp(真库实测保留时分秒)
		assertEquals("select to_timestamp('2026-01-15 10:30:00','yyyy-MM-dd hh24:mi:ss') from t",
				c("select str_to_date('2026-01-15 10:30:00','%Y-%m-%d %H:%i:%s') from t", "postgresql"));
		assertEquals("select parsedatetime('2026-01-15','yyyy-MM-dd') from t",
				c("select str_to_date('2026-01-15','%Y-%m-%d') from t", "h2"));
	}
	/**
	 * update 2026-10-11 日期区间加减族:date_add/date_sub/dateadd/adddate/subdate源形态
	 */
	@Test
	public void dateAddFamilyConversions() {
		// mysql系:本库原生拼写透传,外来拼写归一DATE_ADD
		assertEquals("select date_add(create_time, INTERVAL 2 MONTH) from t",
				c("select date_add(create_time, INTERVAL 2 MONTH) from t", "mysql"));
		assertEquals("select DATE_ADD(create_time, INTERVAL (2) MONTH) from t",
				c("select dateadd(month,2,create_time) from t", "mysql"));
		assertEquals("select DATE_ADD(create_time, INTERVAL -(3) DAY) from t",
				c("select date_sub(create_time, INTERVAL 3 DAY) from t", "mysql"));
		// sqlserver:dateadd透传,外来拼写归一DATEADD
		assertEquals("select dateadd(day, 2, create_time) from t",
				c("select dateadd(day, 2, create_time) from t", "sqlserver"));
		assertEquals("select DATEADD(month, (2), create_time) from t",
				c("select date_add(create_time, INTERVAL 2 MONTH) from t", "sqlserver"));
		// oracle系/kingbase:月走add_months,日走算术,时分秒折算
		assertEquals("select add_months(create_time,(2)) from t",
				c("select date_add(create_time, INTERVAL 2 MONTH) from t", "oracle"));
		assertEquals("select (create_time + (3)) from t",
				c("select dateadd(day,3,create_time) from t", "oracle"));
		assertEquals("select (create_time - (3)) from t",
				c("select date_sub(create_time, INTERVAL 3 DAY) from t", "oracle"));
		assertEquals("select (create_time + (5)/24) from t",
				c("select date_add(create_time, INTERVAL 5 HOUR) from t", "oracle"));
		assertEquals("select add_months(create_time,(2)) from t",
				c("select date_add(create_time, INTERVAL 2 MONTH) from t", "kingbase"));
		// vanilla pg:区间乘法
		assertEquals("select (create_time + (2) * interval '1 month') from t",
				c("select date_add(create_time, INTERVAL 2 MONTH) from t", "postgresql"));
		// og系:月走add_months,日走区间
		assertEquals("select add_months(create_time,(2)) from t",
				c("select date_add(create_time, INTERVAL 2 MONTH) from t", "opengauss"));
		// db2/hana/ch/h2
		assertEquals("select (create_time + (2) MONTHS) from t",
				c("select date_add(create_time, INTERVAL 2 MONTH) from t", "db2"));
		assertEquals("select ADD_DAYS(create_time,(3)) from t",
				c("select dateadd(day,3,create_time) from t", "hana"));
		assertEquals("select addMonths(create_time,(2)) from t",
				c("select date_add(create_time, INTERVAL 2 MONTH) from t", "clickhouse"));
		assertEquals("select DATEADD('MONTH', (2), create_time) from t",
				c("select date_add(create_time, INTERVAL 2 MONTH) from t", "h2"));
		// sqlite响亮放弃(月份修饰符不钳制月末)
		assertEquals("select date_add(create_time, INTERVAL 2 MONTH) from t",
				c("select date_add(create_time, INTERVAL 2 MONTH) from t", "sqlite"));
		// adddate/subdate简写(天)
		assertEquals("select adddate(create_time, 5) from t",
				c("select adddate(create_time, 5) from t", "mysql"));
		assertEquals("select (create_time + (5)) from t",
				c("select adddate(create_time, 5) from t", "oracle"));
		assertEquals("select (create_time - (5)) from t",
				c("select subdate(create_time, 5) from t", "oracle"));
	}
	@Test
	public void uuidSecondRoundSpellings() {
		// update 2026-10-11 拼写审计第二轮:h2/pg/ch/扩展的uuid族原生写法作为源
		assertEquals("select random_uuid() from t", c("select random_uuid() from t", "h2"));
		assertEquals("select uuid() from t", c("select random_uuid() from t", "mysql"));
		assertEquals("select RAWTOHEX(SYS_GUID()) from t", c("select random_uuid() from t", "oracle"));
		assertEquals("select sys_guid() from t", c("select random_uuid() from t", "kingbase"));
		assertEquals("select gen_random_uuid() from t", c("select gen_random_uuid() from t", "postgresql"));
		assertEquals("select uuid() from t", c("select gen_random_uuid() from t", "mysql"));
		// uuid-ossp扩展写法:归一到pg核心函数(是否装扩展不可探测)
		assertEquals("select gen_random_uuid() from t", c("select uuid_generate_v4() from t", "postgresql"));
		assertEquals("select generateUUIDv4() from t", c("select generateUUIDv4() from t", "clickhouse"));
		assertEquals("select uuid() from t", c("select generateUUIDv4() from t", "mysql"));
		// og系对新拼写走合成降级(uuid族→v4形态,不再透传响亮)
		assertEquals(true, c("select random_uuid() from t", "opengauss").startsWith("select substr(md5(random()::text)"),
				"og random_uuid应合成v4形态");
		// update 2026-10-11 第三轮:uuid_generate_v7(uuid-ossp)与pg18原生uuidv7/uuidv4
		assertEquals("select uuid_generate_v7() from t", c("select uuid_generate_v7() from t", "postgresql"));
		assertEquals("select uuidv7() from t", c("select uuidv7() from t", "postgresql"));
		assertEquals("select uuidv4() from t", c("select uuidv4() from t", "postgresql"));
		// 无v7的库:转该库唯一id生成器(v7时序特性降级,类注释声明)
		assertEquals("select uuid() from t", c("select uuid_generate_v7() from t", "mysql"));
		assertEquals("select RAWTOHEX(SYS_GUID()) from t", c("select uuidv7() from t", "oracle"));
		assertEquals("select random_uuid() from t", c("select uuidv7() from t", "h2"));
	}


	// ============ 2026-10-11 A方案:left/right、lpad/rpad、quarter、EXTRACT源、日期截断族 ==========

	@Test
	public void leftRightAndPadConversions() {
		// 原生透传(mysql系/pg系/db2/dm/h2/ch/hana/kingbase等真库实测)
		assertEquals("select left(name,2) from t", c("select left(name,2) from t", "mysql"));
		assertEquals("select right(name,2) from t", c("select right(name,2) from t", "kingbase"));
		// oracle/sqlite无left/right:转substr(两库substr负起点均自尾部取)
		assertEquals("select substr(name,1,2) from t", c("select left(name,2) from t", "oracle"));
		assertEquals("select substr(name,-2) from t", c("select right(name,2) from t", "oracle"));
		assertEquals("select substr(name,1,2) from t", c("select left(name,2) from t", "sqlite"));
		// lpad/rpad:仅sqlserver组合(超长截断保前段语义);sqlite响亮放弃
		assertEquals("select lpad(id,3,'0') from t", c("select lpad(id,3,'0') from t", "mysql"));
		assertEquals("select lpad(id,3,'0') from t", c("select lpad(id,3,'0') from t", "hana"));
		assertEquals("select RIGHT(REPLICATE('0',3)+LEFT(id,3),3) from t",
				c("select lpad(id,3,'0') from t", "sqlserver"));
		assertEquals("select LEFT(LEFT(id,3)+REPLICATE('0',3),3) from t",
				c("select rpad(id,3,'0') from t", "sqlserver"));
		assertEquals("select lpad(id,3,'0') from t", c("select lpad(id,3,'0') from t", "sqlite"));
	}

	@Test
	public void quarterConversions() {
		// mysql系/db2/dm/h2/ch原生(真库实测=1)
		assertEquals("select quarter(create_time) from t", c("select quarter(create_time) from t", "mysql"));
		assertEquals("select quarter(create_time) from t", c("select quarter(create_time) from t", "dm"));
		// sqlserver无quarter():DATEPART
		assertEquals("select DATEPART(quarter,create_time) from t",
				c("select quarter(create_time) from t", "sqlserver"));
		// pg系/kingbase:EXTRACT(QUARTER)
		assertEquals("select EXTRACT(QUARTER FROM create_time) from t",
				c("select quarter(create_time) from t", "kingbase"));
		// oracle系:EXTRACT不支持QUARTER(实测ORA-00907),to_char('Q')
		assertEquals("select to_number(to_char(create_time,'Q')) from t",
				c("select quarter(create_time) from t", "oracle"));
		// hana实测返回'2026-Q1'非数值:月份算术
		assertEquals("select (floor((month(create_time)-1)/3)+1) from t",
				c("select quarter(create_time) from t", "hana"));
	}

	@Test
	public void extractSourceConversions() {
		// pg/oracle用户的标准写法作源:归一EXTRACT形态(原生族幂等)
		assertEquals("select EXTRACT(YEAR FROM create_time) from t",
				c("select extract(year from create_time) from t", "mysql"));
		assertEquals("select EXTRACT(YEAR FROM create_time) from t",
				c("select date_part('year', create_time) from t", "postgresql"));
		// sqlserver转DATEPART;sqlite转cast+strftime
		assertEquals("select DATEPART(year,create_time) from t",
				c("select extract(year from create_time) from t", "sqlserver"));
		assertEquals("select cast(strftime('%Y','2026-03-20') as integer) from t",
				c("select extract(year from '2026-03-20') from t", "sqlite"));
		// oracle:QUARTER转to_char('Q'),时分秒包CAST(须timestamp定型)
		assertEquals("select to_number(to_char(create_time,'Q')) from t",
				c("select extract(quarter from create_time) from t", "oracle"));
		assertEquals("select EXTRACT(HOUR FROM CAST(create_time AS TIMESTAMP)) from t",
				c("select extract(hour from create_time) from t", "oracle"));
		// hana的QUARTER月份算术;db2的QUARTER走原生函数
		assertEquals("select (floor((month(create_time)-1)/3)+1) from t",
				c("select extract(quarter from create_time) from t", "hana"));
		assertEquals("select QUARTER(create_time) from t", c("select date_part('quarter', create_time) from t", "db2"));
	}

	@Test
	public void dateTruncConversions() {
		// oracle/db2/og系/kingbase/vastbase/dm:trunc原生透传(真库探针)
		assertEquals("select trunc(create_time,'MM') from t", c("select trunc(create_time,'MM') from t", "oracle"));
		assertEquals("select trunc(create_time,'MM') from t", c("select trunc(create_time,'MM') from t", "kingbase"));
		assertEquals("select trunc_timestamp(create_time,'Q') from t",
				c("select trunc_timestamp(create_time,'Q') from t", "db2"));
		// pg/ch/h2/doris:date_trunc原生透传(pg序)
		assertEquals("select date_trunc('month', create_time) from t",
				c("select date_trunc('month', create_time) from t", "postgresql"));
		// 交叉转换:date_trunc源→trunc库;trunc源→date_trunc库
		assertEquals("select TRUNC(create_time,'MM') from t",
				c("select date_trunc('month', create_time) from t", "oracle"));
		assertEquals("select TRUNC(create_time,'Q') from t",
				c("select date_trunc('quarter', create_time) from t", "dm"));
		assertEquals("select date_trunc('month',create_time) from t",
				c("select trunc(create_time,'MM') from t", "postgresql"));
		// mysql系/mysql语法族:组合形态
		assertEquals("select DATE_FORMAT(create_time,'%Y-%m-01') from t",
				c("select trunc(create_time,'MM') from t", "mysql"));
		assertEquals("select DATE_FORMAT(DATE_SUB(create_time, INTERVAL (MONTH(create_time)-1)%3 MONTH),'%Y-%m-01') from t",
				c("select trunc(create_time,'Q') from t", "starrocks"));
		assertEquals("select DATE_SUB(DATE(create_time), INTERVAL WEEKDAY(create_time) DAY) from t",
				c("select trunc(create_time,'IW') from t", "mysql"));
		// sqlserver:DATEFROMPARTS族
		assertEquals("select DATEFROMPARTS(YEAR(create_time),MONTH(create_time),1) from t",
				c("select date_trunc('month', create_time) from t", "sqlserver"));
		// hana:TO_DATE+TO_CHAR组合
		assertEquals("select TO_DATE(TO_CHAR(create_time,'YYYY-MM'),'YYYY-MM') from t",
				c("select trunc(create_time,'MM') from t", "hana"));
		// sqlite:修饰符链(季截断printf形态)
		assertEquals("select date(datetime(create_time/1000,'unixepoch','localtime'),'start of month') from t",
				c("select trunc(create_time,'MM') from t", "sqlite"));
		// 安全边界:1参trunc全库透传;数值trunc响亮
		assertEquals("select trunc(create_time) from t", c("select trunc(create_time) from t", "mysql"));
		assertEquals("select trunc(score,2) from t", c("select trunc(score,2) from t", "mysql"));
	}


	@Test
	public void modConversions() {
		// 18环境仅sqlserver无mod函数(真库实测),转%运算符;其余原生透传
		assertEquals("select mod(score,3) from t", c("select mod(score,3) from t", "mysql"));
		assertEquals("select mod(score,3) from t", c("select mod(score,3) from t", "hana"));
		assertEquals("select (score % 3) from t", c("select mod(score,3) from t", "sqlserver"));
		assertEquals("select mod(score,3) from t", c("select mod(score,3) from t", "sqlite"));
	}
}