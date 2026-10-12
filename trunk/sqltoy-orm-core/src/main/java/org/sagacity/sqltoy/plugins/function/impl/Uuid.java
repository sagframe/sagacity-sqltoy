package org.sagacity.sqltoy.plugins.function.impl;

import java.util.Locale;
import java.util.regex.Pattern;

import org.sagacity.sqltoy.plugins.function.IFunction;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * @project sagacity-sqltoy
 * @description 唯一id生成函数的跨库适配(2026-10-11源拼写补全:uuid/guid/newid/sys_guid
 *              四种惯用写法统一进入转换——不能只认mysql的uuid(),dm用户写guid()、sqlserver用户写
 *              newid()、oracle用户写sys_guid()同样要能跨库)。目标语义=生成唯一id,返回形态两族:
 *              36位带连字符(uuid()/newid()族:mysql系/pg/ch/sqlite/sqlserver/dm/h2)与32位hex
 *              (guid()/sys_guid()族:oracle/kingbase/hana),引擎原生能力所致,注释与测试双锁定。
 *              拼写×方言的原生透传矩阵:本库原生拼写原样保留(如guid()@dm、newid()@sqlserver、
 *              sys_guid()@oracle/kingbase、uuid()@mysql系/h2/vastbase),外来拼写转该库等价物。
 *              响亮放弃的库(2026-10-11真库探针):openGauss系(og/gaussdb/mogdb/oscar/stardb,
 *              og5.0实测无uuid()/uuid_generate_v4()/gen_random_uuid,建议应用侧生成或部署
 *              uuid-ossp扩展后自行改写)、db2(无原生,GENERATE_UNIQUE()非UUID格式)。
 * @author zhongxuchen
 * @version v1.0,Date:2026-10-11
 */
public class Uuid extends IFunction {
	// \W前缀防generateUUIDv4(/uuid_short(等含uuid子串的token误命中;sys_guid(的guid(
	// 前为"_"(单词字符)不会被guid分支单独命中,各拼写互不干扰
	// update 2026-10-11 拼写审计第二轮:补random_uuid(h2)/gen_random_uuid(pg)/
	// uuid_generate_v4(uuid-ossp扩展)/generateUUIDv4(ch)——下划线前缀使\Wuuid\(
	// 不命中(防误命中机制的副作用),须显式列出
	// update 2026-10-11 第三轮:补uuid_generate_v7(uuid-ossp扩展v7时序形态)与pg18原生
	// uuidv7()/uuidv4();v7时序特性在无v7目标的库降级为随机(类注释声明)
	private static Pattern regex = Pattern.compile(
			"(?i)\\W(uuid|guid|newid|sys_guid|random_uuid|gen_random_uuid|uuid_generate_v4|uuid_generate_v7|generateuuidv4|uuidv4|uuidv7)\\(");

	@Override
	public String dialects() {
		return ALL;
	}

	@Override
	public Pattern regex() {
		return regex;
	}

	@Override
	// update 2026-10-12 GBASE8S:Informix无uuid生成函数(UDR/EXTEND可扩展不内置),
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
		// kingbase:sys_guid()原生(实测直接返回32hex串)
		// update 2026-10-11 瀚高(PG12内核+oracle兼容):uuid()/gen_random_uuid()均无,
		// sys_guid()原生(真库实测,32hex),与kingbase同法
		if (dialect == DBType.KINGBASE || dialect == DBType.HIGHGO) {
			return "sys_guid".equals(funLow) ? super.IGNORE : "sys_guid()";
		}
		// hana:SYSUUID为无括号关键字(无法作为源拼写匹配),所有拼写统一TO_VARCHAR转32hex串
		if (dialect == DBType.HANA) {
			return "TO_VARCHAR(SYSUUID)";
		}
		// sqlserver:newid()原生
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
		// openGauss系(og/gaussdb/mogdb/oscar/stardb)与db2:无原生uuid,原样保留响亮报错
		return super.IGNORE;
	}
}
