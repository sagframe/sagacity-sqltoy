package org.sagacity.sqltoy.plugins.function.impl;

import java.util.Locale;
import java.util.regex.Pattern;

import org.sagacity.sqltoy.plugins.function.IFunction;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

	/**
	 * @project sagacity-sqltoy
	 * @description 唯一id生成函数的跨库适配:源拼写补全为uuid/guid/newid/sys_guid四种惯用写法统一进入转换——
	 * 不能只认mysql的uuid(),dm用户写guid()、sqlserver用户写newid()、oracle用户写sys_guid()同样要能跨库。
	 * 目标语义=生成唯一id,返回两族形态(36位带连字符/32hex)。适配策略三级:1)优先转库内同质原生函数;
	 * 2)无原生函数但有随机/散列原语的,用SQL表达式合成唯一id降级(零运维依赖);3)原语也没有但运维可部署的
	 * (uuid-ossp扩展等),保留用户拼写透传,部署即生效,未部署则响亮报错可察。
	 * 拼写×方言原生透传矩阵:本库原生拼写原样保留(guid()@dm、newid()@sqlserver、uuid()/get_uuid()返回36位
	 * 大写带连字符(实证CAFD6E1E-5D4F-4C06-83F1-072940A50A20)、sys_guid()返回32位大写hex(实证
	 * A06A928E5A2E4F7BB389CD4550F704CD)、另有uuid_short()/uuid_to_bin()/md5(),getObject直读无varchar空串
	 * 问题),故uuid与sys_guid拼写原样透传,guid拼写转sys_guid(),其余拼写转uuid();get_uuid()/uuid_short()因下划线
	 * 前缀不进正则,透传即原生生效。数据库能力结论必须以系统目录(sysprocedures)与实际调用为证,不能凭探针报错
	 * 信息推断。db2(12.1.5容器实测):12.1.3+内建GENERATE_UUID()返回36位带连字符标准UUID(实证
	 * 2F30BE39-8E19-4CD1-A405-A618DA412998),各拼写归一到generate_uuid();11.5/12.1.0-12.1.2无此函数报
	 * SQL0440N(响亮)。openGauss系(og5.0/og7.0容器实证):og/gaussdb/mogdb为PG内核,无uuid族函数但有
	 * random()/md5()/clock_timestamp(),按策略2合成降级——uuid族拼写合成v4形态36位(实证81c38b77-a8a8-4900-
	 * b6e7-569a8c350a10),sys_guid/guid拼写合成32hex md5(实证1601eb623fc3bca613ef02a4ad417c29);
	 * uuid_generate_v4/v7为uuid-ossp标准拼写,按策略3透传,运维CREATE EXTENSION后原生生效。oscar/stardb
	 * 内核非PG系谱未证实,不注入PG语法,维持透传响亮(注入未证实语法=静默错误,比响亮报错更糟)。
	 * @author zhongxuchen
	 * @version v1.0,Date:2026-10-11
	 */
public class Uuid extends IFunction {
	// update 2026-10-11 \W前缀防generateUUIDv4(/uuid_short(等含uuid子串的token误命中;sys_guid(的guid(
	// 前为"_"(单词字符)不会被guid分支单独命中,各拼写互不干扰
	// 拼写审计补全显式列出的扩展/原生拼写:random_uuid(h2)/gen_random_uuid(pg)/uuid_generate_v4
	// (uuid-ossp扩展)/generateUUIDv4(ch)——下划线前缀使\Wuuid\(不命中(防误命中机制的副作用),须显式列出;
	// 另补uuid_generate_v7(uuid-ossp扩展v7时序形态)与pg18原生uuidv7()/uuidv4();v7时序特性在无v7目标的库降级为随机(类注释声明)
	private static Pattern regex = Pattern.compile(
			"(?i)\\W(uuid|guid|newid|sys_guid|random_uuid|gen_random_uuid|uuid_generate_v4|uuid_generate_v7|generateuuidv4|uuidv4|uuidv7)\\(");

	// og系降级合成:uuid族拼写的v4形态(每段独立md5(random()::text)切片,版本nibble置4、
	// variant取89ab之一,形状8-4-4-4-12)
	private static final String OG_V4 = "substr(md5(random()::text),1,8)||'-'||substr(md5(random()::text),1,4)||'-4'"
			+ "||substr(md5(random()::text),1,3)||'-'||substr('89ab',1+(floor(random()*4))::int,1)"
			+ "||substr(md5(random()::text),1,3)||'-'||substr(md5(random()::text),1,12)";

	// og系降级合成:sys_guid/guid拼写的32hex md5(random+时钟双熵源)
	private static final String OG_MD5 = "md5(random()::text || clock_timestamp()::text)";

	@Override
	public String dialects() {
		return ALL;
	}

	@Override
	public Pattern regex() {
		return regex;
	}

	@Override
	public String wrap(int dialect, String functionName, boolean hasArgs, String... args) {
		String funLow = functionName.toLowerCase(Locale.ROOT);
		boolean uuidSpelling = "uuid".equals(funLow);
		// mysql系原生uuid()(tidb/doris/starrocks/OB同源):uuid拼写透传,外来拼写转uuid()
		if (dialect == DBType.MYSQL || dialect == DBType.MYSQL57 || dialect == DBType.TIDB || dialect == DBType.DORIS
				|| dialect == DBType.STARROCKS || dialect == DBType.OCEANBASE) {
			return uuidSpelling ? super.IGNORE : "uuid()";
		}
		// h2原生uuid()/random_uuid()(random_uuid为h2惯用拼写)
		if (dialect == DBType.H2) {
			return (uuidSpelling || "random_uuid".equals(funLow)) ? super.IGNORE : "random_uuid()";
		}
		// vastbase原生uuid()(真库实测),其余拼写亦转uuid()
		if (dialect == DBType.VASTBASE) {
			return uuidSpelling ? super.IGNORE : "uuid()";
		}
		// pg13+:gen_random_uuid()内置;uuidv7()/uuidv4()为pg18原生、uuid_generate_v7依赖
		// uuid-ossp扩展——此四拼写透传保真(扩展/pg18不在目标库时响亮报错,可察);其余拼写
		// (含uuid_generate_v4扩展写法)归一到核心函数gen_random_uuid
		if (dialect == DBType.POSTGRESQL || dialect == DBType.POSTGRESQL14) {
			if ("gen_random_uuid".equals(funLow) || "uuidv4".equals(funLow) || "uuidv7".equals(funLow)
					|| "uuid_generate_v7".equals(funLow)) {
				return super.IGNORE;
			}
			return "gen_random_uuid()";
		}
		// oracle:sys_guid()原生(返回RAW);外来拼写转RAWTOHEX定型的32hex串
		if (dialect == DBType.ORACLE || dialect == DBType.ORACLE11) {
			return "sys_guid".equals(funLow) ? super.IGNORE : "RAWTOHEX(SYS_GUID())";
		}
		// update 2026-10-11 kingbase:sys_guid()原生(实测直接返回32hex串)
		// 瀚高(PG12内核+oracle兼容):uuid()/gen_random_uuid()均无,sys_guid()原生(真库实测,32hex),与kingbase同法
		if (dialect == DBType.KINGBASE || dialect == DBType.HIGHGO) {
			return "sys_guid".equals(funLow) ? super.IGNORE : "sys_guid()";
		}
		// hana:SYSUUID为无括号关键字(无法作为源拼写匹配),所有拼写统一TO_VARCHAR转32hex串
		if (dialect == DBType.HANA) {
			return "TO_VARCHAR(SYSUUID)";
		}
		// sqlserver:newID()原生
		if (dialect == DBType.SQLSERVER) {
			return "newid".equals(funLow) ? super.IGNORE : "NEWID()";
		}
		// dm:guid()与newid()均原生(真库实测guid()为32hex、newid()为36位带连字符)
		if (dialect == DBType.DM) {
			return ("guid".equals(funLow) || "newid".equals(funLow)) ? super.IGNORE : "NEWID()";
		}
		// ch原生generateUUIDv4(该拼写透传)
		if (dialect == DBType.CLICKHOUSE) {
			return "generateuuidv4".equals(funLow) ? super.IGNORE : "generateUUIDv4()";
		}
		if (dialect == DBType.SQLITE) {
			// sqlite无uuid函数,以randomblob组合RFC4122 v4形态(版本4 nibble+variant 89ab),
			// 整体lower对齐mysql的小写hex(实测未包裹时hex()产出大写段)
			return "lower(hex(randomblob(4))||'-'||hex(randomblob(2))||'-4'||substr(hex(randomblob(2)),2)"
					+ "||'-'||substr('89ab',abs(random())%4+1,1)||substr(hex(randomblob(2)),2)"
					+ "||'-'||hex(randomblob(6)))";
		}
		// db2:12.1.3+内建GENERATE_UUID()(36位带连字符标准UUID),generate_uuid拼写透传,
		// 其余拼写归一;11.5/12.1.0-12.1.2无此函数时报SQL0440N响亮可察
		// (GENERATE_UNIQUE()为时间戳形态非UUID,不采用)
		if (dialect == DBType.DB2) {
			return "generate_uuid".equals(funLow) ? super.IGNORE : "generate_uuid()";
		}
		// uuid()为36位大写带连字符、sys_guid()为32位大写hex;guid拼写转sys_guid()(同32hex
		// 家族),其余拼写转uuid();get_uuid()/uuid_short()不进正则自然透传即原生

		// openGauss系之PG内核成员(og/gaussdb/mogdb):无uuid族函数但有random()/md5()/
		// clock_timestamp(),降级合成(策略2):uuid族→v4形态36位,sys_guid/guid→32hex md5;
		// uuid_generate_v4/v7为uuid-ossp标准拼写、gen_random_uuid为pgcrypto标准拼写,
		// 均透传(策略3:运维CREATE EXTENSION后原生生效,未部署响亮报错;
		// gen_random_uuid透传使部署pgcrypto的环境走原生CSPRNG而非md5降级)
		if (dialect == DBType.OPENGAUSS || dialect == DBType.GAUSSDB || dialect == DBType.MOGDB) {
			if ("uuid_generate_v4".equals(funLow) || "uuid_generate_v7".equals(funLow)
					|| "gen_random_uuid".equals(funLow)) {
				return super.IGNORE;
			}
			return ("sys_guid".equals(funLow) || "guid".equals(funLow)) ? OG_MD5 : OG_V4;
		}
		// oscar/stardb:内核非PG系谱未证实,不注入未证实语法,IGNORE透传响亮报错
		// (部署带uuid函数时用户拼写直接生效)
		return super.IGNORE;
	}
}
