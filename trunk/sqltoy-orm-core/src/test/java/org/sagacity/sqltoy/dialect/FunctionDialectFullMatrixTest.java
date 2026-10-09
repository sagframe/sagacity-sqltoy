package org.sagacity.sqltoy.dialect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.plugins.function.FunctionUtils;

/**
 * 函数转换全量矩阵(2026-10-4):默认注册的16个函数逐一方言分支的形态锁定 + H2/SQLite
 * 内嵌库全函数端到端执行;各方言的真库执行语义由对应*RealDbSmokeTest覆盖。
 * 形态行采用 {说明, sql, 方言, 模式, 期望} 五元组,EXACT全串比对/CONTAINS特征片段;
 * 空格差异敏感的行(转换产物含多空格)以实际实现为准,注释标注缘由。
 */
public class FunctionDialectFullMatrixTest {

	private static final int EXACT = 0;
	private static final int CONTAINS = 1;

	private static Connection h2;

	private static Connection sqlite;

	@BeforeAll
	public static void init() throws Exception {
		FunctionUtils.setFunctionConverts(java.util.Arrays.asList("default"));
		h2 = DriverManager.getConnection("jdbc:h2:mem:funcfull;DB_CLOSE_DELAY=-1", "sa", "");
		try (var st = h2.createStatement()) {
			st.execute("drop table if exists t_func");
			st.execute("create table t_func (id int, name varchar(100), score decimal(10,2), "
					+ "create_time timestamp, remark varchar(100))");
			try (var ps = h2.prepareStatement("insert into t_func values (?,?,?,?,?)")) {
				ps.setInt(1, 1);
				ps.setString(2, "admin");
				ps.setBigDecimal(3, new java.math.BigDecimal("88.5"));
				ps.setTimestamp(4, Timestamp.valueOf("2026-01-15 10:30:00"));
				ps.setString(5, null);
				ps.executeUpdate();
				ps.setInt(1, 2);
				ps.setString(2, "user");
				ps.setBigDecimal(3, new java.math.BigDecimal("72.0"));
				ps.setTimestamp(4, Timestamp.valueOf("2026-03-20 14:00:00"));
				ps.setString(5, "r2");
				ps.executeUpdate();
			}
		}
		sqlite = DriverManager.getConnection("jdbc:sqlite::memory:");
		try (var st = sqlite.createStatement()) {
			st.execute("drop table if exists t_func");
			st.execute("create table t_func (id int, name varchar(100), score decimal(10,2), "
					+ "create_time timestamp, remark varchar(100))");
			// sqlite的JDBC setTimestamp存毫秒Long,与生产绑定形态一致(date函数族依赖该形态)
			try (var ps = sqlite.prepareStatement("insert into t_func values (?,?,?,?,?)")) {
				ps.setInt(1, 1);
				ps.setString(2, "admin");
				ps.setDouble(3, 88.5);
				ps.setLong(4, java.time.LocalDateTime.of(2026, 1, 15, 10, 30, 0)
						.toInstant(java.time.ZoneOffset.UTC).toEpochMilli());
				ps.setString(5, null);
				ps.executeUpdate();
				ps.setInt(1, 2);
				ps.setString(2, "user");
				ps.setDouble(3, 72.0);
				ps.setLong(4, java.time.LocalDateTime.of(2026, 3, 20, 14, 0, 0)
						.toInstant(java.time.ZoneOffset.UTC).toEpochMilli());
				ps.setString(5, "r2");
				ps.executeUpdate();
			}
		}
	}

	@AfterAll
	public static void destroy() throws Exception {
		if (h2 != null) {
			h2.close();
		}
		if (sqlite != null) {
			sqlite.close();
		}
	}

	private String convert(String sql, String dialect) {
		return FunctionUtils.getDialectSql(sql, dialect);
	}

	private Object queryOne(Connection conn, String sql) throws Exception {
		try (PreparedStatement pst = conn.prepareStatement(sql); ResultSet rs = pst.executeQuery()) {
			rs.next();
			return rs.getObject(1);
		}
	}

	/**
	 * 形态矩阵:16个函数 x 各方言分支逐行锁定。行序按函数分组,便于失败时定位。
	 */
	@Test
	public void dialectShapeMatrix() {
		Object[][] rows = {
				// ---------------- SubStr ----------------
				{ "substr三参pg转substring", "select substr(name,2,3) from t", "postgresql", EXACT,
						"select substring(name,2,3) from t" },
				{ "substr两参sqlserver补长度", "select substr(name,2) from t", "sqlserver", EXACT,
						"select substring(name,2,len(name)) from t" },
				{ "substr三参sqlserver转substring", "select substr(name,2,3) from t", "sqlserver", EXACT,
						"select substring(name,2,3) from t" },
				// update 2026-10-9 负起点短串(|n|>串长)对齐mysql空串契约:两参RIGHT加长度守卫、
				// 三参守卫短串分支返回''(db2守卫的length经Length适配器链式归一为char_length)
				{ "substr负起点两参pg转RIGHT守卫", "select substr(name,-2) from t", "postgresql", EXACT,
						"select case when length(name)>=2 then RIGHT(name,2) else '' end from t" },
				{ "substr负起点三参pg转case守卫", "select substr(name,-2,1) from t", "postgresql", CONTAINS,
						"case when length(name)<2 then '' else substring(name,length(name)-2+1,1) end" },
				{ "substr负起点db2转RIGHT守卫", "select substr(name,-2) from t", "db2", EXACT,
						"select case when char_length(name)>=2 then RIGHT(name,2) else '' end from t" },
			{ "substr三参db2长度守卫", "select substr(name,3,100) from t", "db2", CONTAINS,
					// 守卫生成的length()会被Length类按db2字节/字符归一二次转为char_length(链式正确)
					"least(100,char_length(name)-(3)+1)" },
				{ "substr负起点hana转RIGHT守卫(实测pg语义)", "select substr(name,-2) from t", "hana", EXACT,
						"select case when length(name)>=2 then RIGHT(name,2) else '' end from t" },
				{ "substr非负hana原生透传", "select substr(name,1,3) from t", "hana", EXACT,
						"select substr(name,1,3) from t" },
				{ "substr mysql原生透传", "select substr(name,2,3) from t", "mysql", EXACT,
						"select substr(name,2,3) from t" },
				{ "substr kingbase转substring", "select substr(name,2,3) from t", "kingbase", EXACT,
						"select substring(name,2,3) from t" },
				// ---------------- Trim ----------------
				{ "trim单参sqlserver转rtrim(ltrim)", "select trim(name) from t", "sqlserver", EXACT,
						"select rtrim(ltrim(name)) from t" },
				{ "trim both空格sqlserver转rtrim(ltrim)", "select trim(both ' ' from name) from t", "sqlserver", EXACT,
						"select rtrim(ltrim(name)) from t" },
				{ "trim leading sqlserver转ltrim", "select trim(leading from name) from t", "sqlserver", EXACT,
						"select ltrim(name) from t" },
				{ "trim非空格字符sqlserver响亮保留", "select trim(both 'x' from name) from t", "sqlserver", EXACT,
						"select trim(both 'x' from name) from t" },
				{ "trim both字符集sqlite转二参", "select trim(both 'x' from name) from t", "sqlite", EXACT,
						"select trim(name,'x') from t" },
				{ "trim leading字符集sqlite转ltrim", "select trim(leading 'x' from name) from t", "sqlite", EXACT,
						"select ltrim(name,'x') from t" },
				{ "trim普通sqlite透传", "select trim(name) from t", "sqlite", EXACT, "select trim(name) from t" },
				{ "trim普通h2归一both空格", "select trim(name) from t", "h2", EXACT,
						"select trim(both ' ' from name) from t" },
				{ "trim修饰符h2透传", "select trim(both 'x' from name) from t", "h2", EXACT,
						"select trim(both 'x' from name) from t" },
				// ---------------- Instr ----------------
				{ "instr两参pg转position", "select instr(name,'a') from t", "postgresql", EXACT,
						"select position('a' in name) from t" },
				{ "instr三参pg转strpos组合", "select instr(name,'a',2) from t", "postgresql", CONTAINS,
						"case when strpos(substr(name,2),'a')=0" },
				{ "charindex两参sqlite转instr", "select charindex('a',name) from t", "sqlite", EXACT,
						"select instr(name,'a') from t" },
				{ "strpos两参oracle转instr", "select strpos(name,'a') from t", "oracle", EXACT,
						"select instr(name,'a') from t" },
				{ "strpos两参gauss透传", "select strpos(name,'a') from t", "gaussdb", EXACT,
						"select strpos(name,'a') from t" },
				{ "instr四参响亮保留", "select instr(name,'a',1,2) from t", "sqlserver", EXACT,
						"select instr(name,'a',1,2) from t" },
				// ---------------- Concat ----------------
				{ "concat两参oracle原生透传", "select concat(name,'-') from t", "oracle", EXACT,
						"select concat(name,'-') from t" },
				{ "concat三参oracle转拼接", "select concat(name,'-','x') from t", "oracle", EXACT,
						"select name||'-'||'x' from t" },
				{ "concat三参db2转拼接", "select concat(name,'-','x') from t", "db2", EXACT,
						"select name||'-'||'x' from t" },
				{ "concat三参oceanbase转拼接", "select concat(name,'-','x') from t", "oceanbase", EXACT,
						"select name||'-'||'x' from t" },
				// update 2026-10-9 oceanbase方言形态锁定(oracle系产物,适用于oracle模式租户;
				// CE mysql模式边界见OceanbaseRealDbSmokeTest类注释)
				{ "nvl oceanbase透传", "select nvl(name,'x') from t", "oceanbase", EXACT,
						"select nvl(name,'x') from t" },
				{ "group_concat oceanbase转listagg", "select group_concat(name separator '-') from t", "oceanbase",
						EXACT, "select  listagg(name,'-') within group (order by null)  from t" },
				{ "now oceanbase转sysdate", "select now() from t", "oceanbase", EXACT, "select sysdate from t" },
				{ "to_date oceanbase透传", "select to_date(create_time,'yyyy-MM-dd') from t", "oceanbase", EXACT,
						"select to_date(create_time,'yyyy-MM-dd') from t" },
				{ "concat两参sqlite即转拼接", "select concat(name,'-') from t", "sqlite", EXACT,
						"select name||'-' from t" },
				{ "concat多参sqlserver原生透传", "select concat(name,'-','x') from t", "sqlserver", EXACT,
						"select concat(name,'-','x') from t" },
				// ---------------- ConcatWs ----------------
				{ "concat_ws oracle转逐段跳null拼接", "select concat_ws('-',name,remark) from t", "oracle", CONTAINS,
						"case when remark is null then ''" },
				{ "concat_ws单值sqlite转coalesce", "select concat_ws('-',name) from t", "sqlite", EXACT,
						"select coalesce(name,'') from t" },
				{ "concat_ws两参sqlserver转concat", "select concat_ws('-',name) from t", "sqlserver", EXACT,
						"select concat(name,'') from t" },
				{ "concat_ws dm双引号分隔符修正", "select concat_ws(\"-\",name,remark) from t", "dm", EXACT,
						"select concat_ws('-',name,remark) from t" },
				// ---------------- Nvl ----------------
				{ "nvl sqlserver转isnull", "select nvl(name,'x') from t", "sqlserver", EXACT,
						"select isnull(name,'x') from t" },
				{ "nvl pg转coalesce", "select nvl(name,'x') from t", "postgresql", EXACT,
						"select coalesce(name,'x') from t" },
				{ "nvl mysql转ifnull", "select nvl(name,'x') from t", "mysql", EXACT,
						"select ifnull(name,'x') from t" },
				{ "nvl dm原生透传", "select nvl(name,'x') from t", "dm", EXACT, "select nvl(name,'x') from t" },
				{ "isnull单参判空语义", "select isnull(name) from t", "mysql", EXACT,
						"select case when name is null then 1 else 0 end from t" },
				{ "nvl2非oracle转case", "select nvl2(name,'y','n') from t", "db2", EXACT,
						"select case when name is not null then 'y' else 'n' end from t" },
				{ "nvl2 oracle原生透传", "select nvl2(name,'y','n') from t", "oracle", EXACT,
						"select nvl2(name,'y','n') from t" },
				// ---------------- DateFormat ----------------
				{ "date_format oracle转to_char", "select date_format(create_time,'%Y-%m-%d') from t", "oracle",
						EXACT, "select to_char(create_time,'yyyy-MM-dd') from t" },
				{ "date_format kingbase转to_char", "select date_format(create_time,'%Y-%m-%d') from t", "kingbase",
						EXACT, "select to_char(create_time,'yyyy-MM-dd') from t" },
				{ "date_format opengauss转to_char", "select date_format(create_time,'%Y-%m-%d') from t", "opengauss",
						EXACT, "select to_char(create_time,'yyyy-MM-dd') from t" },
				{ "date_format db2转VARCHAR_FORMAT", "select date_format(create_time,'%Y-%m-%d') from t", "db2",
						EXACT, "select VARCHAR_FORMAT(create_time,'yyyy-MM-dd') from t" },
				{ "date_format h2转formatdatetime", "select date_format(create_time,'%Y-%m-%d') from t", "h2", EXACT,
						"select formatdatetime(create_time,'yyyy-MM-dd') from t" },
				{ "date_format ch原生别名", "select date_format(create_time,'%Y-%m-%d') from t", "clickhouse", EXACT,
						"select date_format(toDateTime(create_time),'%Y-%m-%d') from t" },
				{ "date_format sqlite转strftime", "select date_format(create_time,'%Y-%m-%d') from t", "sqlite",
						EXACT, "select strftime('%Y-%m-%d',datetime(create_time/1000,'unixepoch','localtime')) from t" },
				{ "date_format大写模型sqlserver", "select date_format(create_time,'YYYY-MM-DD HH24:MI:SS') from t",
						"sqlserver", EXACT, "select FORMAT(create_time,'yyyy-MM-dd HH:mm:ss') from t" },
				// ---------------- Now ----------------
				{ "now mysql带fsp保留", "select now(6) from t", "mysql", EXACT, "select now(6) from t" },
				{ "now tidb带fsp保留", "select now(6) from t", "tidb", EXACT, "select now(6) from t" },
				{ "now pg保留", "select now() from t", "postgresql", EXACT, "select now() from t" },
				{ "sysdate oracle透传", "select sysdate from t", "oracle", EXACT, "select sysdate from t" },
				{ "sysdate mysql转now", "select sysdate from t", "mysql", EXACT, "select now() from t" },
				{ "getdate sqlserver透传", "select getdate() from t", "sqlserver", EXACT, "select getdate() from t" },
				{ "now带精度sqlserver转sysdatetime", "select now(6) from t", "sqlserver", EXACT,
						"select sysdatetime() from t" },
				{ "now db2转CURRENT TIMESTAMP", "select now() from t", "db2", EXACT, "select CURRENT TIMESTAMP from t" },
				{ "now hana转CURRENT_TIMESTAMP", "select now() from t", "hana", EXACT,
						"select CURRENT_TIMESTAMP from t" },
				{ "now ch保留", "select now() from t", "clickhouse", EXACT, "select now() from t" },
				{ "now sqlite本地墙钟", "select now() from t", "sqlite", EXACT,
						"select datetime('now','localtime') from t" },
				{ "systimestamp oracle透传", "select systimestamp from t", "oracle", EXACT,
						"select systimestamp from t" },
				{ "systimestamp dm透传", "select systimestamp from t", "dm", EXACT, "select systimestamp from t" },
				{ "systimestamp带精度mysql转now(fsp)", "select systimestamp(6) from t", "mysql", EXACT,
						"select now(6) from t" },
				// ---------------- CurrentDate(update 2026-10-4 新增:当前日期不带时间) ----------------
				{ "curdate mysql透传", "select curdate() from t", "mysql", EXACT, "select curdate() from t" },
				{ "current_date mysql透传", "select current_date from t", "mysql", EXACT,
						"select current_date from t" },
				{ "curdate pg转CURRENT_DATE", "select curdate() from t", "postgresql", EXACT,
						"select CURRENT_DATE from t" },
				{ "current_date pg透传", "select current_date from t", "postgresql", EXACT,
						"select current_date from t" },
				{ "curdate h2转CURRENT_DATE", "select curdate() from t", "h2", EXACT,
						"select CURRENT_DATE from t" },
				{ "curdate oracle转TRUNC(CURRENT_DATE)", "select curdate() from t", "oracle", EXACT,
						"select TRUNC(CURRENT_DATE) from t" },
				{ "current_date oracle转TRUNC(CURRENT_DATE)", "select current_date from t", "oracle", EXACT,
						"select TRUNC(CURRENT_DATE) from t" },
				{ "curdate dm转TRUNC(CURRENT_DATE)", "select curdate() from t", "dm", EXACT,
						"select TRUNC(CURRENT_DATE) from t" },
				{ "curdate sqlserver转CAST", "select curdate() from t", "sqlserver", EXACT,
						"select CAST(GETDATE() AS date) from t" },
				{ "current_date sqlserver转CAST", "select current_date from t", "sqlserver", EXACT,
						"select CAST(GETDATE() AS date) from t" },
				{ "current_date db2透传", "select current_date from t", "db2", EXACT,
						"select current_date from t" },
				{ "curdate db2转CURRENT DATE", "select curdate() from t", "db2", EXACT,
						"select CURRENT DATE from t" },
				{ "current_date sqlite本地墙钟", "select current_date from t", "sqlite", EXACT,
						"select date('now','localtime') from t" },
				{ "curdate ch转today", "select curdate() from t", "clickhouse", EXACT, "select today() from t" },
				{ "current_date ch透传", "select current_date from t", "clickhouse", EXACT,
						"select current_date from t" },
				{ "current_date hana透传", "select current_date from t", "hana", EXACT,
						"select current_date from t" },
				// ---------------- Length ----------------
				{ "len sqlserver原生透传", "select len(name) from t", "sqlserver", EXACT, "select len(name) from t" },
				{ "lengthb sqlserver转datalength", "select lengthb(name) from t", "sqlserver", EXACT,
						"select datalength(name) from t" },
				{ "len oracle转length", "select len(name) from t", "oracle", EXACT, "select length(name) from t" },
				{ "lengthb pg转octet_length", "select lengthb(name) from t", "postgresql", EXACT,
						"select octet_length(name) from t" },
				{ "lengthb vastbase转octet_length", "select lengthb(name) from t", "vastbase", EXACT,
						"select octet_length(name) from t" },
				{ "datalength db2转octet_length", "select datalength(name) from t", "db2", EXACT,
						"select octet_length(name) from t" },
				{ "char_length db2原生透传", "select char_length(name) from t", "db2", EXACT,
						"select char_length(name) from t" },
				{ "len mysql转char_length", "select len(name) from t", "mysql", EXACT,
						"select char_length(name) from t" },
				{ "lengthb mysql转length字节", "select lengthb(name) from t", "mysql", EXACT,
						"select length(name) from t" },
				{ "lengthb h2转octet_length", "select lengthb(name) from t", "h2", EXACT,
						"select octet_length(name) from t" },
				{ "lengthb hana降级length", "select lengthb(name) from t", "hana", EXACT, "select length(name) from t" },
				{ "lengthb ch转length字节", "select lengthb(name) from t", "clickhouse", EXACT,
						"select length(name) from t" },
				{ "char_length ch转lengthUTF8", "select char_length(name) from t", "clickhouse", EXACT,
						"select lengthUTF8(name) from t" },
				{ "lengthb sqlite降级length", "select lengthb(name) from t", "sqlite", EXACT,
						"select length(name) from t" },
				// ---------------- ToChar ----------------
				{ "to_char数值模型mysql转CAST", "select to_char(score,'999.99') from t", "mysql", EXACT,
						"select CAST(score AS DECIMAL(20,2)) from t" },
				{ "to_char数值模型starrocks转CAST", "select to_char(score,'999.99') from t", "starrocks", EXACT,
						"select CAST(score AS DECIMAL(20,2)) from t" },
				{ "to_char日期mysql转date_format", "select to_char(create_time,'yyyy-MM-dd') from t", "mysql", EXACT,
						"select date_format(create_time,'%Y-%m-%d') from t" },
				{ "to_char pg原生透传(列参不cast)", "select to_char(create_time,'yyyy-MM-dd') from t", "postgresql",
						EXACT, "select to_char(create_time,'yyyy-MM-dd') from t" },
				{ "to_char dm原生透传", "select to_char(create_time,'yyyy-MM-dd') from t", "dm", EXACT,
						"select to_char(create_time,'yyyy-MM-dd') from t" },
				{ "to_char sqlite转strftime", "select to_char(create_time,'yyyy-MM-dd') from t", "sqlite", EXACT,
						"select strftime('%Y-%m-%d',datetime(create_time/1000,'unixepoch','localtime')) from t" },
				{ "to_char数值模型sqlite响亮保留", "select to_char(score,'999.99') from t", "sqlite", EXACT,
						"select to_char(score,'999.99') from t" },
				{ "to_char数值模型sqlserver零占位", "select to_char(score,'999.99') from t", "sqlserver", EXACT,
						"select FORMAT(score,'000.00') from t" },
				// ---------------- If ----------------
				{ "if mysql原生透传", "select if(score>80,'high','low') from t", "mysql", EXACT,
						"select if(score>80,'high','low') from t" },
				{ "if ch原生透传", "select if(score>80,'high','low') from t", "clickhouse", EXACT,
						"select if(score>80,'high','low') from t" },
				{ "if oracle转case when", "select if(score>80,'high','low') from t", "oracle", EXACT,
						"select  case when score>80 then 'high' else 'low' end  from t" },
				// ---------------- Decode ----------------
				{ "decode oracle原生透传", "select decode(score,88.5,'high','low') from t", "oracle", EXACT,
						"select decode(score,88.5,'high','low') from t" },
				{ "decode dm原生透传", "select decode(score,88.5,'high','low') from t", "dm", EXACT,
						"select decode(score,88.5,'high','low') from t" },
				{ "decode h2原生透传", "select decode(score,88.5,'high','low') from t", "h2", EXACT,
						"select decode(score,88.5,'high','low') from t" },
				{ "decode偶数参转case带else", "select decode(score,88.5,'high','low') from t", "mysql", EXACT,
						"select  case  when score=88.5 then 'high' else 'low' end  from t" },
				{ "decode奇数参转case无else", "select decode(score,88.5,'high') from t", "mysql", EXACT,
						"select  case  when score=88.5 then 'high' end  from t" },
				{ "decode两参pg透传(二进制decode不误伤)", "select decode(name,'hex') from t", "postgresql", EXACT,
						"select decode(name,'hex') from t" },
				// ---------------- GroupConcat ----------------
				{ "group_concat separator pg转array_to_string", "select group_concat(name separator '-') from t",
						"postgresql", EXACT, "select  array_to_string(ARRAY_AGG(name),'-')  from t" },
				{ "group_concat kingbase转array_to_string", "select group_concat(name) from t", "kingbase", EXACT,
						"select  array_to_string(ARRAY_AGG(name),',')  from t" },
				{ "string_agg pg原生透传", "select string_agg(name,'-') from t", "postgresql", EXACT,
						"select string_agg(name,'-') from t" },
				{ "listagg mysql转group_concat", "select listagg(name,'-') from t", "mysql", EXACT,
						"select  group_concat(name separator '-')  from t" },
				{ "string_agg oracle转listagg", "select string_agg(name,'-') from t", "oracle", EXACT,
						"select  listagg(name,'-') within group (order by null)  from t" },
				{ "listagg oracle透传", "select listagg(name,'-') from t", "oracle", EXACT,
						"select listagg(name,'-') from t" },
				{ "group_concat db2转listagg", "select group_concat(name) from t", "db2", EXACT,
						"select  listagg(name,',')  from t" },
				{ "listagg db2透传", "select listagg(name,'-') from t", "db2", EXACT,
						"select listagg(name,'-') from t" },
				{ "string_agg hana原生透传", "select string_agg(name,'-') from t", "hana", EXACT,
						"select string_agg(name,'-') from t" },
				{ "group_concat ch转arrayStringConcat", "select group_concat(name) from t", "clickhouse", EXACT,
						"select  arrayStringConcat(groupArray(name),',')  from t" },
				{ "group_concat sqlite转二参", "select group_concat(name) from t", "sqlite", EXACT,
						"select  group_concat(name,',')  from t" },
				// update 2026-10-4 聚合排序子句跨库(三种源形态×目标方言)
				{ "listagg within group mysql转ORDER BY…SEPARATOR",
						"select listagg(name,'-') within group (order by id) from t", "mysql", EXACT,
						"select  group_concat(name ORDER BY id SEPARATOR '-')  from t" },
				{ "listagg within group pg转ARRAY_AGG内嵌排序",
						"select listagg(name,'-') within group (order by id) from t", "postgresql", EXACT,
						"select  array_to_string(ARRAY_AGG(name ORDER BY id),'-')  from t" },
				{ "listagg within group oracle原生透传",
						"select listagg(name,'-') within group (order by id) from t", "oracle", EXACT,
						"select listagg(name,'-') within group (order by id) from t" },
				{ "listagg within group sqlserver转string_agg within group",
						"select listagg(name,'-') within group (order by id) from t", "sqlserver", EXACT,
						"select  string_agg(name,'-') within group (order by id)  from t" },
				{ "string_agg括号内嵌order by mysql转ORDER BY…SEPARATOR",
						"select string_agg(name,'-' order by id) from t", "mysql", EXACT,
						"select  group_concat(name ORDER BY id SEPARATOR '-')  from t" },
				{ "string_agg括号内嵌order by oracle转listagg within group",
						"select string_agg(name,'-' order by id) from t", "oracle", EXACT,
						"select  listagg(name,'-') within group (order by id)  from t" },
				{ "string_agg括号内嵌order by pg原生透传",
						"select string_agg(name,'-' order by id) from t", "postgresql", EXACT,
						"select string_agg(name,'-' order by id) from t" },
				{ "group_concat残段order by pg转ARRAY_AGG内嵌排序",
						"select group_concat(name order by id separator '-') from t", "postgresql", EXACT,
						"select  array_to_string(ARRAY_AGG(name ORDER BY id),'-')  from t" },
				{ "group_concat残段order by oracle转listagg within group",
						"select group_concat(name order by id separator '-') from t", "oracle", EXACT,
						"select  listagg(name,'-') within group (order by id)  from t" },
				{ "group_concat残段order by mysql原生透传",
						"select group_concat(name order by id separator '-') from t", "mysql", EXACT,
						"select group_concat(name order by id separator '-') from t" },
				{ "group_concat DISTINCT ch响亮保留", "select group_concat(DISTINCT name) from t", "clickhouse",
						EXACT, "select group_concat(DISTINCT name) from t" },
				// update 2026-10-8 歧义形态响亮保留与字面量感知(实测原实现静默错值/劈开字面量)
				{ "order by多键逗号两参响亮保留(排序键不得静默丢弃)",
						"select string_agg(name,'-' order by id desc,age) from t", "mysql", EXACT,
						"select string_agg(name,'-' order by id desc,age) from t" },
				{ "order by多键逗号多参响亮保留(排序键不得变拼接列)",
						"select group_concat(name order by id,age separator '-') from t", "postgresql", EXACT,
						"select group_concat(name order by id,age separator '-') from t" },
				{ "分隔符字面量含order by不作排序子句",
						"select group_concat(name separator 'a order by b') from t", "postgresql", EXACT,
						"select  array_to_string(ARRAY_AGG(name),'a order by b')  from t" },
				{ "expr字面量含order by原样保留(不得劈开)",
						"select group_concat('a order by b') from t", "oracle", EXACT,
						"select  listagg('a order by b',',') within group (order by null)  from t" },
				{ "within group多键排序完整保留(平衡括号整段搬运)",
						"select listagg(name,'-') within group (order by id desc,age) from t", "mysql", EXACT,
						"select  group_concat(name ORDER BY id desc,age SEPARATOR '-')  from t" },
				// ---------------- ToNumber ----------------
				{ "to_number oracle原生透传", "select to_number(score) from t", "oracle", EXACT,
						"select to_number(score) from t" },
				{ "to_number pg转numeric", "select to_number(score) from t", "postgresql", EXACT,
						"select CAST(score AS numeric) from t" },
				{ "to_number mysql转DECIMAL", "select to_number(score) from t", "mysql", EXACT,
						"select CAST(score AS DECIMAL(20,6)) from t" },
				{ "to_number带模型响亮保留", "select to_number(score,'9999.99') from t", "mysql", EXACT,
						"select to_number(score,'9999.99') from t" },
				// ---------------- ToDate ----------------
				{ "to_date两参oracle透传", "select to_date(create_time,'yyyy-MM-dd') from t", "oracle", EXACT,
						"select to_date(create_time,'yyyy-MM-dd') from t" },
				{ "to_date单参oracle启发式补日期模型", "select to_date('2026-01-15') from t", "oracle", EXACT,
						"select to_date('2026-01-15','yyyy-MM-dd') from t" },
				{ "to_date单参oracle启发式含时间", "select to_date('2026-01-15 10:30:00') from t", "oracle", EXACT,
						"select to_date('2026-01-15 10:30:00','yyyy-MM-dd HH24:mi:ss') from t" },
				{ "to_date mysql单参转DATE", "select to_date('2026-01-15') from t", "mysql", EXACT,
						"select DATE('2026-01-15') from t" },
				{ "to_date mysql两参转STR_TO_DATE", "select to_date(create_time,'yyyy-MM-dd') from t", "mysql", EXACT,
						"select STR_TO_DATE(create_time,'%Y-%m-%d') from t" },
				// update 2026-10-9 大写oracle惯用形态(YYYY/DD/MI/SS残留字面量致STR_TO_DATE错值,今日补)
				{ "to_date大写oracle形态mysql日期", "select to_date(create_time,'YYYY-MM-DD') from t", "mysql", EXACT,
						"select STR_TO_DATE(create_time,'%Y-%m-%d') from t" },
				{ "to_date大写oracle形态mysql日期时间", "select to_date(create_time,'YYYY-MM-DD HH24:MI:SS') from t",
						"mysql", EXACT, "select STR_TO_DATE(create_time,'%Y-%m-%d %H:%i:%s') from t" },
				{ "to_date pg单参转CAST", "select to_date('2026-01-15') from t", "postgresql", EXACT,
						"select CAST('2026-01-15' AS date) from t" },
				{ "to_date pg两参透传", "select to_date(create_time,'yyyy-MM-dd') from t", "postgresql", EXACT,
						"select to_date(create_time,'yyyy-MM-dd') from t" },
				{ "to_date ch转toDate", "select to_date('2026-01-15') from t", "clickhouse", EXACT,
						"select toDate('2026-01-15') from t" },
				{ "to_date sqlserver风格style23", "select to_date(create_time,'yyyy-MM-dd') from t", "sqlserver",
						EXACT, "select convert(date,create_time,23) from t" },
				{ "to_date sqlserver风格style120", "select to_date(create_time,'yyyy-MM-dd hh24:mi:ss') from t",
						"sqlserver", EXACT, "select convert(datetime,create_time,120) from t" },
				{ "to_date sqlite转date", "select to_date('2026-01-15') from t", "sqlite", EXACT,
						"select date('2026-01-15') from t" },
				// ---------------- DateDiff(重点形态,真库语义由各RealDbSmokeTest覆盖) ----------------
				{ "datediff两参mysql透传", "select datediff(create_time, create_time) from t", "mysql", EXACT,
						"select datediff(create_time, create_time) from t" },
				{ "datediff三参mysql年分量差", "select datediff(year,create_time,create_time) from t", "mysql", EXACT,
						"select (YEAR(create_time) - YEAR(create_time)) from t" },
				{ "datediff三参oracle天差", "select datediff(day,a,b) from t", "oracle", EXACT,
						"select (TRUNC(b) - TRUNC(a)) from t" },
				{ "datediff两参oracle天差", "select datediff(a,b) from t", "oracle", EXACT,
						"select (TRUNC(a) - TRUNC(b)) from t" },
				{ "datediff两参sqlserver转DATEDIFF DAY反序", "select datediff(a,b) from t", "sqlserver", EXACT,
						"select DATEDIFF(DAY,b,a) from t" },
				{ "datediff三参sqlserver原生形态", "select datediff(day,a,b) from t", "sqlserver", EXACT,
						"select DATEDIFF(DAY,a,b) from t" },
				{ "timestampdiff小时mysql归一单位", "select timestampdiff(hour,a,b) from t", "mysql", EXACT,
						"select timestampdiff(HOUR,a,b) from t" },
		};
		for (Object[] row : rows) {
			String desc = (String) row[0];
			String actual = convert((String) row[1], (String) row[2]);
			int mode = (int) row[3];
			String expect = (String) row[4];
			if (mode == EXACT) {
				assertEquals(expect, actual, desc);
			} else {
				assertTrue(actual.contains(expect), desc + " 期望含[" + expect + "] 实际:" + actual);
			}
		}
	}

	/** H2端到端:16个函数逐一转换后真实执行(默认测试方言) */
	@Test
	public void h2ExecutionMatrix() throws Exception {
		assertEquals("dmi", queryOne(h2, convert("select substr(name,2,3) from t_func where id=1", "h2")).toString());
		// 负起点取末2位
		assertEquals("in", queryOne(h2, convert("select substr(name,-2) from t_func where id=1", "h2")).toString());
		// update 2026-10-9 超串长守卫(今日契约修正):|n|>串长时mysql语义返回空串('admin'长5)
		assertEquals("", queryOne(h2, convert("select substr(name,-6) from t_func where id=1", "h2")).toString(),
				"两参超串长应返回空串");
		assertEquals("", queryOne(h2, convert("select substr(name,-6,2) from t_func where id=1", "h2")).toString(),
				"三参超串长应返回空串");
		// 边界:n=串长时恰返回整串(守卫条件>=)
		assertEquals("admin", queryOne(h2, convert("select substr(name,-5) from t_func where id=1", "h2")).toString(),
				"n=串长应返回整串");
		assertEquals(2, ((Number) queryOne(h2, convert("select instr(name,'dmi') from t_func where id=1", "h2")))
				.intValue());
		assertEquals(2, ((Number) queryOne(h2, convert("select strpos(name,'dmi') from t_func where id=1", "h2")))
				.intValue());
		assertEquals("admin-x",
				queryOne(h2, convert("select concat(name,'-','x') from t_func where id=1", "h2")).toString());
		// concat_ws跳null:remark为null的行得'admin'
		assertEquals("admin",
				queryOne(h2, convert("select concat_ws('-',name,remark) from t_func where id=1", "h2")).toString());
		assertEquals("none", queryOne(h2, convert("select nvl(remark,'none') from t_func where id=1", "h2")).toString());
		assertEquals(0, ((Number) queryOne(h2, convert("select isnull(name) from t_func where id=1", "h2")))
				.intValue());
		assertEquals("n", queryOne(h2, convert("select nvl2(remark,'y','n') from t_func where id=1", "h2")).toString());
		assertNotNull(queryOne(h2, convert("select now() from t_func where id=1", "h2")));
		assertEquals(5, ((Number) queryOne(h2, convert("select length(name) from t_func where id=1", "h2")))
				.intValue());
		assertEquals(5, ((Number) queryOne(h2, convert("select char_length(name) from t_func where id=1", "h2")))
				.intValue());
		assertEquals(5, ((Number) queryOne(h2, convert("select lengthb(name) from t_func where id=1", "h2")))
				.intValue());
		assertEquals(5, ((Number) queryOne(h2, convert("select len(name) from t_func where id=1", "h2"))).intValue());
		assertEquals("2026-01-15",
				queryOne(h2, convert("select to_char(create_time,'yyyy-MM-dd') from t_func where id=1", "h2"))
						.toString());
		assertEquals("2026-01-15",
				queryOne(h2, convert("select date_format(create_time,'%Y-%m-%d') from t_func where id=1", "h2"))
						.toString());
		// to_date单参转parsedatetime
		assertNotNull(queryOne(h2, convert("select to_date('2026-01-15') from t_func where id=1", "h2")));
		// to_number转CAST执行
		assertEquals(88.5, ((Number) queryOne(h2, convert("select to_number(score) from t_func where id=1", "h2")))
				.doubleValue(), 0.0001);
		// datediff三参转DATEDIFF(DAY,CAST,CAST)
		assertEquals(5, ((Number) queryOne(h2, convert(
				"select datediff(day, '2026-01-15', '2026-01-20') from dual", "h2"))).intValue());
		// decode/if原生
		assertEquals("high", queryOne(h2, convert("select decode(score,88.5,'high','low') from t_func where id=1", "h2"))
				.toString());
		assertEquals("high", queryOne(h2, convert("select if(score>80,'high','low') from t_func where id=1", "h2"))
				.toString());
		// group_concat原生
		assertNotNull(queryOne(h2, convert("select group_concat(name separator '-') from t_func", "h2")));
		// trim普通形态归一执行
		assertEquals("admin", queryOne(h2, convert("select trim(' admin ') from t_func where id=1", "h2")).toString());
		// update 2026-10-4 current_date透传/curdate转CURRENT_DATE执行=今天
		assertEquals(java.time.LocalDate.now().toString(),
				queryOne(h2, convert("select current_date from t_func where id=1", "h2")).toString());
		assertEquals(java.time.LocalDate.now().toString(),
				queryOne(h2, convert("select curdate() from t_func where id=1", "h2")).toString());
	}

	/** SQLite端到端:sqlite目标转换链全函数真实执行(内嵌库,毫秒时间戳形态与生产绑定一致) */
	@Test
	public void sqliteExecutionMatrix() throws Exception {
		assertEquals(2, ((Number) queryOne(sqlite,
				convert("select instr(name,'dmi') from t_func where id=1", "sqlite"))).intValue());
		assertEquals(2, ((Number) queryOne(sqlite,
				convert("select strpos(name,'dmi') from t_func where id=1", "sqlite"))).intValue());
		assertEquals(2, ((Number) queryOne(sqlite,
				convert("select charindex('dmi',name) from t_func where id=1", "sqlite"))).intValue());
		// sqlite原生substr负起点即mysql语义,透传直接可用
		assertEquals("in", queryOne(sqlite, convert("select substr(name,-2) from t_func where id=1", "sqlite"))
				.toString());
		assertEquals("admin-x",
				queryOne(sqlite, convert("select concat(name,'-','x') from t_func where id=1", "sqlite")).toString());
		assertEquals("admin",
				queryOne(sqlite, convert("select concat_ws('-',name,remark) from t_func where id=1", "sqlite"))
						.toString());
		assertEquals("none",
				queryOne(sqlite, convert("select nvl(remark,'none') from t_func where id=1", "sqlite")).toString());
		assertEquals(1, ((Number) queryOne(sqlite,
				convert("select isnull(remark) from t_func where id=1", "sqlite"))).intValue());
		assertEquals("n",
				queryOne(sqlite, convert("select nvl2(remark,'y','n') from t_func where id=1", "sqlite")).toString());
		assertNotNull(queryOne(sqlite, convert("select now() from t_func where id=1", "sqlite")));
		assertEquals(5, ((Number) queryOne(sqlite,
				convert("select length(name) from t_func where id=1", "sqlite"))).intValue());
		assertEquals(5, ((Number) queryOne(sqlite,
				convert("select lengthb(name) from t_func where id=1", "sqlite"))).intValue());
		// 毫秒时间戳列经datetime归一后格式化
		assertEquals("2026-01-15",
				queryOne(sqlite, convert("select to_char(create_time,'yyyy-MM-dd') from t_func where id=1", "sqlite"))
						.toString());
		assertEquals("2026-01-15",
				queryOne(sqlite, convert("select date_format(create_time,'%Y-%m-%d') from t_func where id=1", "sqlite"))
						.toString());
		assertEquals("2026-01-15",
				queryOne(sqlite, convert("select to_date('2026-01-15') from t_func where id=1", "sqlite")).toString());
		assertEquals(88.5, ((Number) queryOne(sqlite,
				convert("select to_number(score) from t_func where id=1", "sqlite"))).doubleValue(), 0.0001);
		String agg = queryOne(sqlite, convert("select group_concat(name separator '-') from t_func", "sqlite"))
				.toString();
		assertTrue("admin-user".equals(agg) || "user-admin".equals(agg), "group_concat拼接实际:" + agg);
		assertEquals("high",
				queryOne(sqlite, convert("select decode(score,88.5,'high','low') from t_func where id=1", "sqlite"))
						.toString());
		assertEquals("high",
				queryOne(sqlite, convert("select if(score>80,'high','low') from t_func where id=1", "sqlite"))
						.toString());
		// datediff两参自然天差
		assertEquals(0, ((Number) queryOne(sqlite,
				convert("select datediff(create_time, create_time) from t_func where id=1", "sqlite"))).intValue());
		// update 2026-10-4 current_date转本地墙钟执行=今天(sqlite内嵌库与JVM同时区)
		assertEquals(java.time.LocalDate.now().toString(),
				queryOne(sqlite, convert("select current_date from t_func where id=1", "sqlite")).toString());
	}
}
