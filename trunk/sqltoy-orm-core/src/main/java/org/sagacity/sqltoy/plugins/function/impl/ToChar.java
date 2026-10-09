package org.sagacity.sqltoy.plugins.function.impl;

import java.util.regex.Pattern;

import org.sagacity.sqltoy.plugins.function.FunctionUtils;
import org.sagacity.sqltoy.plugins.function.IFunction;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * @project sagacity-sqltoy
 * @description 将其它类型数据转换成字符串
 * @author zhongxuchen
 * @version v1.0,Date:2013-01-02
 */
public class ToChar extends IFunction {
	// update 2026-9-9 正则收窄为仅匹配to_char:原先同时匹配date_format/FORMATDATETIME与DateFormat
	// 职责重叠,date_format在H2目标下先被DateFormat正确转为formatdatetime,再被本函数二次转成
	// to_char(H2 2.x无此函数),最终产物取决于注册顺序,存在链式改写风险
	private static Pattern regex = Pattern.compile("(?i)\\Wto_char\\(");

	/**
	 * update 2026-10-9 日期模型标记:格式串含任一标记恒为日期模型(用于数值/日期模型判别)。
	 * 刻意不含mi:纯分钟格式' mi '不含9/0不进判别,而数值模型的MI后缀('999MI')不得误判为日期
	 */
	private static final Pattern DATE_MODEL_PATTERN = Pattern
			.compile("(?i)(yyyy|yy|mm|dd|hh|ss|mon|month|day|year|am|pm)|:|%[ymdhis]");

	@Override
	public String dialects() {
		return ALL;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.sagacity.sqltoy.config.function.IFunction#wrap(java.lang.String [])
	 */
	@Override
	public String wrap(int dialect, String functionName, boolean hasArgs, String... args) {
		if (args == null || args.length < 2) {
			return super.IGNORE;
		}
		String format;
		switch (dialect) {
		case DBType.MYSQL:
		case DBType.TIDB:
		case DBType.DORIS:
		case DBType.STARROCKS:
		case DBType.MYSQL57: {
			// update 2026-9-11 数值格式模型分流:to_char(123.45,'999.99')原落date_format,
			// 数值参数静默返回NULL(date_format为日期语义,mysql9实测);改CAST AS DECIMAL(20,scale)
			// 保留oracle数值模型语义(scale=小数点后9/0占位数;无千分位分隔,与oracle形态一致)
			// update 2026-10-9 数值模型判别精化:原"含9/0即数值"会将日期格式中的字面数字
			// ('yyyy-MM-dd 00:00:00')误判为数值模型转CAST DECIMAL(日期被cast成小数,静默错值),
			// 含日期模型标记时恒按日期处理
			if (isNumericModel(args[1])) {
				return "CAST(" + args[0] + " AS DECIMAL(20," + numericScale(args[1]) + "))";
			}
			// 日期
			// update 2026-10-9 补大写oracle惯用形态YYYY/YY/DD(oracle模型大小写不敏感,'YYYY-MM-DD'
			// 与'yyyy-mm-dd'同义;原仅时间token补了大写,日期token漏补致YYYY/DD残留字面量,
			// mysql目标静默输出'YYYY-10-DD'垃圾文本;YYYY须先于YY替换避免子串误命中)
			format = args[1].replace("yyyy", "%Y").replace("YYYY", "%Y").replace("yy", "%y").replace("YY", "%y")
					.replace("MM", "%m").replace("dd", "%d").replace("DD", "%d");
			// 时间处理(update 2026-9-5 补java 24小时制HH→%H与分钟mm→%i;需置于hh24/hh映射之后)
			// update 2026-9-15 修复HH24大写形态:原仅替换小写hh24,大写HH24先被HH→%H命中
			// 残留字面"24"(mysql实测'HH24:mi:ss'得'1024:30'),改为HH24/hh24双形态优先替换,
			// 并补大写MI/SS形态
			format = format.replace("HH24", "%H").replace("hh24", "%H").replace("HH", "%H").replace("hh", "%h")
					.replace("mm", "%i").replace("MI", "%i").replace("mi", "%i").replace("SS", "%s")
					.replace("ss", "%s");
			return "date_format(" + args[0] + "," + format + ")";
		}
		case DBType.POSTGRESQL:
		case DBType.POSTGRESQL14:
		case DBType.ORACLE:
		case DBType.GAUSSDB:
		case DBType.MOGDB:
		case DBType.STARDB:
		case DBType.OSCAR:
		case DBType.OPENGAUSS:
		case DBType.VASTBASE:
			// update 2026-9-14 补KINGBASE(KingbaseES基于PG,TO_CHAR归PG系格式模型)
		case DBType.KINGBASE:
		case DBType.OCEANBASE:
		case DBType.DM:
			// 2026-9-11 hana的TO_CHAR为oracle兼容格式模型(日期yyyy/MM/dd/hh24/mi/ss与数值9/0双场景)
		case DBType.HANA:
		case DBType.ORACLE11: {
			// 日期
			format = args[1].replace("%Y", "yyyy").replace("%y", "yy").replace("%m", "MM").replace("%d", "dd");
			// 时间处理
			format = format.replace("%T", "hh24:mi:ss");
			format = format.replace("%H", "hh24").replace("%h", "hh").replace("%i", "mi").replace("%s", "ss");
			// update 2026-10-9 补java风格时间token归一(同DateFormat的pg/oracle系分支):
			// java的HH=24小时/mm=分钟,oracle模型HH=12小时/mm=月,原样透传静默错值;
			// 仅归一冒号邻接的HH:mm/hh:mm形态,HH24等oracle token与纯日期裸mm不受影响
			// update 2026-10-9 补独立HH(小时分桶等无分钟形态):\b词边界不伤HH24,HH:mm已被上步消费
			format = format.replace("HH:mm", "hh24:mi").replace("hh:mm", "hh:mi").replaceAll("\\bHH\\b", "hh24");
			// update 2026-9-10 PG语法系裸?首参补::timestamp:vanilla PG对to_char(unknown,unknown)
			// 报重载歧义(pgjdbc日期参数UNSPECIFIED OID);to_char兼有数值格式化场景,格式模型含
			// 9/0数值占位时不cast(numericModelPossible=true,数值参数定型绑定原生可解重载)
			return "to_char(" + FunctionUtils.pgToCharParamCast(dialect, args[0], format, true) + "," + format + ")";
		}
		case DBType.H2: {
			// H2原生支持to_char(oracle兼容格式模型,端到端实测验证);
			// update 2026-9-9 修正%y(两位年)误映射为yyyy(四位年)
			format = args[1].replace("%Y", "yyyy").replace("%y", "yy").replace("%m", "MM").replace("%d", "dd");
			// 时间处理(update 2026-9-5 H2 to_char为oracle兼容格式模型,%H应为HH24 24小时制,原hh为12小时制)
			format = format.replace("%T", "hh24:mi:ss");
			format = format.replace("%H", "hh24").replace("%h", "hh").replace("%i", "mi").replace("%s", "ss");
			return "to_char(" + args[0] + "," + format + ")";
		}
		case DBType.CLICKHOUSE: {
			// update 2026-9-11 数值格式模型分流(同mysql分支:toDateTime包裹数值会按epoch秒误析)
			// update 2026-10-9 数值模型判别精化(同mysql分支,日期格式含字面数字不再误判)
			if (isNumericModel(args[1])) {
				return "CAST(" + args[0] + " AS Decimal(20," + numericScale(args[1]) + "))";
			}
			// update 2026-9-9 clickhouse无to_char,以date_format(formatDateTime的mysql兼容别名)承担,
			// token与mysql分支同构(%i分钟/%s秒)
			// update 2026-10-9 补大写oracle形态YYYY/YY/DD/MI/SS(同mysql分支,原残留字面量)
			format = args[1].replace("yyyy", "%Y").replace("YYYY", "%Y").replace("yy", "%y").replace("YY", "%y")
					.replace("MM", "%m").replace("dd", "%d").replace("DD", "%d");
			format = format.replace("HH24", "%H").replace("hh24", "%H").replace("hh", "%h").replace("HH", "%H")
					.replace("mm", "%i").replace("mi", "%i").replace("MI", "%i").replace("ss", "%s")
					.replace("SS", "%s");
			// update 2026-10-4 补%T(mysql源原生token,时:分:秒),原残留字面%T
			format = format.replace("%T", "%H:%i:%s");
			// update 2026-9-11 首参包toDateTime(同DateFormat的CLICKHOUSE分支:驱动日期参数
			// 以String发送,formatDateTime(String)报Illegal type)
			return "date_format(toDateTime(" + args[0] + ")," + format + ")";
		}
		case DBType.SQLSERVER: {
			// update 2026-9-10 sqlserver无to_char(原样透传报'to_char' is not a recognized
			// built-in function name,2022/2025实测),以FORMAT承担(2012+):.NET自定义格式token
			// 与oracle模型部分同形(yyyy/yy/MM/dd直接一致),差异token映射hh24→HH、mi→mm;
			// 数值格式模型(含9/0占位)将9映射为0(.NET '000.00'自定义数值格式);
			// %token先归一到oracle模型再映射(与其余分支入参口径一致)
			format = args[1].replace("%Y", "yyyy").replace("%y", "yy").replace("%m", "MM").replace("%d", "dd");
			format = format.replace("%T", "hh24:mi:ss");
			format = format.replace("%H", "hh24").replace("%h", "hh").replace("%i", "mi").replace("%s", "ss");
			if (isNumericModel(format)) {
				// 数值格式模型:9→0(.NET零占位),FM/S999等oracle前缀修饰不在支持范围
				format = format.replace("9", "0");
			} else {
				// update 2026-10-4 补大写oracle惯用形态与月/分token歧义:.NET FORMAT的token
				// 大小写敏感(yyyy年/MM月/dd日/HH时/mm分/ss秒),原仅映射小写hh24/mi,大写
				// 'YYYY-MM-DD HH24:MI:SS'的YYYY/DD/MI/SS错值;且oracle的mm(月)/mi(分)常全
				// 小写书写,月归一须先于分钟摘取(此时分钟尚为mi/MI无歧义),占位符@M1@不含
				// mi/MI/mm子串,避免替换互相击中
				format = format.replace("HH24", "HH").replace("hh24", "HH");
				format = format.replace("mm", "MM");
				format = format.replace("MI", "@M1@");
				format = format.replace("mi", "@M1@");
				format = format.replace("@M1@", "mm").replace("SS", "ss")
						.replace("YYYY", "yyyy").replace("YY", "yy").replace("DD", "dd");
			}
			// update 2026-10-4 引号归一:带引号的格式串原样保留(内嵌"字面量"转义不被破坏),
			// 裸格式才补包裹引号(原裸格式直接输出FORMAT第二参不带引号报语法错)
			String fmtArg = format.trim();
			if (!(fmtArg.length() > 1 && fmtArg.startsWith("'") && fmtArg.endsWith("'"))) {
				fmtArg = "'" + fmtArg.replace("'", "") + "'";
			}
			return "FORMAT(" + args[0] + "," + fmtArg + ")";
		}
		case DBType.SQLITE: {
			// update 2026-9-10 sqlite无to_char,以strftime承担(token映射:yyyy→%Y、MM→%m、
			// dd→%d、hh24→%H、hh→%I、mi→%M、ss→%S);参数归一与DateFormat同源;数值格式
			// 模型(含9/0占位)非strftime语义,原样保留交目标库响亮报错
			if (isNumericModel(args[1])) {
				return super.IGNORE;
			}
			// update 2026-10-9 补java风格时间token归一(HH:mm→hh24:mi):java的mm=分钟在
			// sqlite链无映射原样残留;归一后由下方hh24→%H、mi→%M链接手
			format = args[1].replace("HH:mm", "hh24:mi").replace("hh:mm", "hh:mi");
			format = format.replace("%Y", "yyyy").replace("%y", "yy").replace("%m", "MM").replace("%d", "dd");
			format = format.replace("%T", "hh24:mi:ss");
			format = format.replace("%H", "hh24").replace("%h", "hh").replace("%i", "mi").replace("%s", "ss");
			format = format.replace("yyyy", "%Y").replace("YYYY", "%Y").replace("yy", "%y").replace("YY", "%y")
					.replace("MM", "%m").replace("dd", "%d").replace("DD", "%d");
			// update 2026-10-9 补大写MI/SS形态(原'HH24:MI:SS'的MI/SS残留字面量,strftime静默输出垃圾文本)
			format = format.replace("HH24", "%H").replace("hh24", "%H").replace("hh", "%I").replace("mi", "%M")
					.replace("MI", "%M").replace("ss", "%S").replace("SS", "%S");
			return "strftime(" + format + "," + FunctionUtils.sqliteDateTextExpr(args[0]) + ")";
		}
		default:
			return super.IGNORE;
		}
	}

	/**
	 * update 2026-9-11 数值格式模型的小数位数:小数点后9/0占位符个数(无小数点返回0),
	 * 如'999.99'→2、'999'→0,供mysql系/CH的CAST AS DECIMAL(20,scale)数值to_char承担
	 */
	private static int numericScale(String format) {
		int dot = format.lastIndexOf('.');
		if (dot < 0) {
			return 0;
		}
		int scale = 0;
		for (int i = dot + 1; i < format.length(); i++) {
			char c = format.charAt(i);
			if (c == '9' || c == '0') {
				scale++;
			}
		}
		return scale;
	}

	/**
	 * update 2026-10-9 数值/日期模型判别:含9或0且无任何日期模型标记(DATE_MODEL_PATTERN)
	 * 才是数值模型;日期格式含字面数字(如'yyyy-MM-dd 00:00:00')不再误判
	 * 
	 * @param format 格式模型串
	 * @return
	 */
	private static boolean isNumericModel(String format) {
		if (format.indexOf('9') < 0 && format.indexOf('0') < 0) {
			return false;
		}
		return !DATE_MODEL_PATTERN.matcher(format).find();
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.sagacity.sqltoy.config.function.IFunction#regex()
	 */
	@Override
	public Pattern regex() {
		return regex;
	}
}
