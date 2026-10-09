package org.sagacity.sqltoy.plugins.function.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.sagacity.sqltoy.config.SqlConfigParseUtils;
import org.sagacity.sqltoy.plugins.function.IFunction;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * @project sagacity-sqltoy
 * @description 转换group_concat 分组拼接函数 在不同数据库中的实现
 * @author zhongxuchen
 * @version v1.0,Date:2019-10-21
 * @modify Date:2026-10-4 支持聚合排序子句跨库:三种源形态(listagg/string_agg的
 *         within group (order by …)尾部子句、pg源string_agg(a,'-' order by b)括号内嵌、
 *         mysql源group_concat(x order by y separator '-')残段内嵌)统一解析为
 *         (segments,sign,orderBy)三元组后按目标方言渲染;同名原生写法连同子句原样透传。
 *         DISTINCT前缀显式识别:pg ARRAY_AGG/mysql group_concat/oracle19c+ listagg/db2原生
 *         支持随expr保留;sqlserver/hana/ch无对应,响亮保留交由目标库报错
 */
public class GroupConcat extends IFunction {
	// update 2026-9-15 补listagg:原正则不含listagg,写listagg的SQL从不进入本转换
	// (mysql实测原样透传报FUNCTION listagg does not exist),三别名统一进入分派
	private static Pattern regex = Pattern.compile("(?i)\\W(group_concat|string_agg|listagg)\\(");
	private static Pattern separtorPattern = Pattern.compile("\\Wseparator\\W");
	// update 2026-10-4 尾部子句钩子:聚合的within group (order by …)附属子句
	// (pattern以\\(*结尾供框架做平衡括号消费,\s*开头承担与函数闭括号间的空白)
	private static Pattern withinGroupPattern = Pattern.compile("(?i)\\s*within\\s+group\\s*\\(");
	// update 2026-10-4 参数内嵌order by(pg源string_agg(a,'-' order by b)与mysql源残段)
	private static Pattern orderByPattern = Pattern.compile("(?i)\\s+order\\s+by\\s+");
	// update 2026-10-4 within group括号内的排序子句锚定形态(括号后紧邻order的惯用写法无前置空白)
	private static Pattern suffixOrderByPattern = Pattern.compile("(?i)^order\\s+by\\s+(.+)$");
	// update 2026-10-4 DISTINCT前缀显式识别
	private static Pattern distinctPattern = Pattern.compile("(?i)^\\s*distinct\\s+");

	@Override
	public String dialects() {
		return ALL;
	}

	@Override
	public Pattern regex() {
		return regex;
	}

	// update 2026-10-4 声明消费聚合尾部的within group (order by …)子句
	@Override
	public Pattern suffixPattern() {
		return withinGroupPattern;
	}

	@Override
	public String wrap(int dbType, String functionName, boolean hasArgs, String... args) {
		if (args == null || args.length == 0) {
			return super.IGNORE;
		}
		String funLow = functionName.toLowerCase(Locale.ROOT);
		boolean twoArgForm = "string_agg".equals(funLow) || "listagg".equals(funLow);
		Object[] parsed = parseArgs(twoArgForm, args);
		if (parsed == null) {
			// update 2026-10-8 排序键含逗号等歧义形态,响亮保留交由目标库报错
			return super.IGNORE;
		}
		@SuppressWarnings("unchecked")
		List<String> segments = (List<String>) parsed[0];
		return render(dbType, funLow, twoArgForm, segments, (String) parsed[1], (String) parsed[2]);
	}

	// update 2026-10-4 within group (order by …)尾部子句命中:同名原生写法连同子句原样透传;
	// 其余解析子句内排序表达式后按目标方言渲染(如listagg源转mysql的ORDER BY…SEPARATOR形态)
	@Override
	public String wrapWithSuffix(int dbType, String functionName, String suffix, String... args) {
		if (args == null || args.length == 0) {
			return super.IGNORE;
		}
		String funLow = functionName.toLowerCase(Locale.ROOT);
		if (isNativeOnDialect(dbType, funLow)) {
			return super.IGNORE;
		}
		// 剥within group ( )外壳取内部排序子句(惯用写法"…"内无空格,不能用\s+前导的order by匹配)
		String inner = suffix.trim();
		int openIdx = inner.indexOf('(');
		int closeIdx = inner.lastIndexOf(')');
		if (openIdx < 0 || closeIdx < openIdx) {
			return super.IGNORE;
		}
		java.util.regex.Matcher om = suffixOrderByPattern.matcher(inner.substring(openIdx + 1, closeIdx).trim());
		if (!om.matches()) {
			// within group内非order by内容(非预期形态),原样保留响亮
			return super.IGNORE;
		}
		String orderBy = om.group(1).trim();
		boolean twoArgForm = "string_agg".equals(funLow) || "listagg".equals(funLow);
		Object[] parsed = parseArgs(twoArgForm, args);
		if (parsed == null) {
			// update 2026-10-8 排序键含逗号等歧义形态,响亮保留交由目标库报错
			return super.IGNORE;
		}
		@SuppressWarnings("unchecked")
		List<String> segments = (List<String>) parsed[0];
		return render(dbType, funLow, twoArgForm, segments, (String) parsed[1], orderBy);
	}

	/**
	 * update 2026-10-4 统一参数解析:两参(string_agg/listagg的表达式+分隔符,分隔符可内嵌
	 * order by)与多参(group_concat的concat语义+可选separator关键字,残段可内嵌order by)
	 * 统一产出[segments, sign, orderBy(可null)];order by原混入expr/pg碰巧合法而
	 * oracle系产生非法SQL,显式拆出。
	 * update 2026-10-8 歧义形态响亮保留(返回null):两参顶层参数>2(order by多键的逗号被
	 * 切参,原实现静默丢弃第3参起;亦是string_agg/listagg本就不存在的非法形态);多参的
	 * order by段非最后segment(排序键含逗号时,mysql语法order by本位于表达式列表之后,
	 * 后续残段无法与拼接列区分,原实现将排序键静默变成拼接列)——响亮报错优于静默错值
	 */
	private Object[] parseArgs(boolean twoArgForm, String... args) {
		String sign = "','";
		String orderBy = null;
		List<String> segments = new ArrayList<String>();
		if (twoArgForm) {
			if (args.length > 2) {
				return null;
			}
			String rawSign = (args.length > 1) ? args[1] : "','";
			String[] parts = splitOrderBy(rawSign);
			if (parts != null) {
				rawSign = parts[0];
				orderBy = parts[1];
			}
			segments.add(args[0]);
			sign = rawSign;
		} else {
			for (int i = 0; i < args.length; i++) {
				String tmp = args[i];
				// update 2026-10-9 修复separator紧贴参数首位(group_concat(a,separator '-')的
				// 第二参整段即separator开头)探测不命中:正则\Wseparator\W要求前导字符,首位无字符
				// 恒不命中致separator整段被当拼接列(listagg(a||separator '-',...)),加空格哨兵探测;
				// 以matcher取命中区间换算原串坐标(哨兵充当前导\W时消耗separator+后导\W共10字符,
				// 原生前导\W时共11字符,统一按sm.start()/sm.end()-1换算,规避indexOf子串误定位)
				java.util.regex.Matcher sm = separtorPattern.matcher(" ".concat(tmp.toLowerCase(Locale.ROOT)));
				if (sm.find()) {
					int sepIdx = sm.start();
					sign = tmp.substring(sm.end() - 1).trim();
					// separator之前的残留表达式一并纳入拼接(切割点取关键字前导\W之前,与原实现
					// 逐字节一致;哨兵充当\W时首段为空)
					int headEnd = (sepIdx > 0) ? sepIdx - 1 : 0;
					if (tmp.substring(0, headEnd).trim().length() > 0) {
						segments.add(tmp.substring(0, headEnd));
					}
				} else {
					segments.add(tmp);
				}
			}
			// 残段内嵌order by取最后一个出现的(mysql源排序子句惯用位于separator之前)
			for (int i = segments.size() - 1; i >= 0; i--) {
				String[] parts = splitOrderBy(segments.get(i));
				if (parts != null) {
					if (i != segments.size() - 1) {
						// order by位于非最后segment:排序键含逗号被切参,后续segment无法
						// 区分拼接列还是排序键,响亮保留
						return null;
					}
					orderBy = parts[1];
					if (parts[0].trim().length() > 0) {
						segments.set(i, parts[0]);
					} else {
						segments.remove(i);
					}
					break;
				}
			}
		}
		return new Object[] { segments, sign, orderBy };
	}

	/**
	 * update 2026-10-4 统一渲染:orderBy为空时产物与既有实现逐字节一致;非空时按目标方言的
	 * 聚合排序形态生成(mysql的ORDER BY在SEPARATOR之前;pg为ARRAY_AGG内嵌order by;
	 * oracle/db2/sqlserver/hana走within group (order by …);sqlite 3.44+二参内嵌order by;
	 * ch的groupArray不支持DISTINCT与排序,响亮保留交由目标库报错)
	 */
	private String render(int dbType, String funLow, boolean twoArgForm, List<String> segments, String sign,
			String orderBy) {
		// update 2026-9-5 group_concat与string_agg参数结构不同,多列拼接expr按目标库的
		// 拼接方式重组(pg/oracle/db2系为||,sqlserver为+,mysql为concat逗号)
		String expr;
		if (twoArgForm) {
			expr = segments.get(0);
		} else {
			String joiner;
			if (dbType == DBType.SQLSERVER) {
				joiner = " + ";
			} else if (dbType == DBType.MYSQL || dbType == DBType.TIDB || dbType == DBType.MYSQL57
					|| dbType == DBType.H2 || dbType == DBType.DORIS || dbType == DBType.STARROCKS) {
				joiner = ",";
			} else {
				joiner = "||";
			}
			expr = String.join(joiner, segments);
		}
		// update 2026-9-11 clickhouse:无group_concat/string_agg,以arrayStringConcat(groupArray(expr),sep)
		// 承担;多列拼接expr沿用||连接符(CH原生支持||字符串拼接),group_concat与string_agg两形态同构
		if (dbType == DBType.CLICKHOUSE) {
			// update 2026-10-4 ch的groupArray不支持DISTINCT与排序,显式响亮保留(与原生成非法SQL同为响亮)
			if (orderBy != null || distinctPattern.matcher(segments.get(0).trim()).lookingAt()) {
				return super.IGNORE;
			}
			return " arrayStringConcat(groupArray(" + expr + ")," + sign + ") ";
		}
		// update 2026-9-14 补KINGBASE(KingbaseES基于PG,函数语法归PG系):此前漏改导致group_concat不转换
		// 原样透传,目标库报函数不存在
		// update 2026-9-15 写法与目标库同名时原样透传,反向别名统一转换:
		// listagg写法→array_to_string(string_agg写法为pg原生透传)
		if (dbType == DBType.POSTGRESQL || dbType == DBType.POSTGRESQL14 || dbType == DBType.GAUSSDB
				|| dbType == DBType.OPENGAUSS || dbType == DBType.OSCAR || dbType == DBType.STARDB
				|| dbType == DBType.MOGDB || dbType == DBType.VASTBASE || dbType == DBType.KINGBASE) {
			if ("string_agg".equals(funLow)) {
				return super.IGNORE;
			}
			// 原则上可以通过string_agg 但如果类型不是字符串就会报错
			if (orderBy != null) {
				return " array_to_string(ARRAY_AGG(" + expr + " ORDER BY " + orderBy + ")," + sign + ") ";
			}
			return " array_to_string(ARRAY_AGG(" + expr + ")," + sign + ") ";
		}
		// update 2026-9-15 listagg写法统一转group_concat(原IGNORE透传,mysql系无listagg报函数不存在)
		if (dbType == DBType.MYSQL || dbType == DBType.TIDB || dbType == DBType.MYSQL57 || dbType == DBType.H2
				|| dbType == DBType.DORIS || dbType == DBType.STARROCKS) {
			if (!twoArgForm) {
				return super.IGNORE;
			}
			// mysql要求ORDER BY位于SEPARATOR之前
			if (orderBy != null) {
				return " group_concat(" + expr + " ORDER BY " + orderBy + " SEPARATOR " + sign + ") ";
			}
			return " group_concat(" + expr + " separator " + sign + ") ";
		}
		// update 2026-9-5 补充oracle系/DB2/sqlserver的listagg与STRING_AGG转换(此前缺失,原样输出在目标库非法)
		// update 2026-9-15
		// string_agg写法统一转listagg(原IGNORE透传,oracle无string_agg报ORA-00904);
		// listagg写法为oracle系原生,原样透传(保留用户自带的within group子句)
		// update 2026-10-9 OB边界实测:listagg产物适用于OB oracle模式租户;OB CE mysql模式租户
		// 无listagg(语法报错),mysql模式租户应配dialect=mysql(group_concat原生),verify工程即此配置
		if (dbType == DBType.ORACLE || dbType == DBType.ORACLE11 || dbType == DBType.DM || dbType == DBType.OCEANBASE) {
			if ("listagg".equals(funLow)) {
				return super.IGNORE;
			}
			if (orderBy != null) {
				return " listagg(" + expr + "," + sign + ") within group (order by " + orderBy + ") ";
			}
			return " listagg(" + expr + "," + sign + ") within group (order by null) ";
		}
		// update 2026-9-5 db2的listagg不接受order by null,直接省略within group子句
		// (update 2026-10-4 真实排序表达式走within group (order by …),与order by null不同)
		if (dbType == DBType.DB2) {
			if ("listagg".equals(funLow)) {
				return super.IGNORE;
			}
			if (orderBy != null) {
				return " listagg(" + expr + "," + sign + ") within group (order by " + orderBy + ") ";
			}
			return " listagg(" + expr + "," + sign + ") ";
		}
		// 2026-9-11 hana 2.0 SPS04+提供STRING_AGG(expr,delimiter)两参形态(与sqlserver同名同构,
		// null值跳过语义一致;多列拼接expr走||连接符分支,hana原生支持||)
		// update 2026-9-15 listagg写法统一转string_agg(原IGNORE透传,sqlserver/hana无listagg)
		if (dbType == DBType.SQLSERVER || dbType == DBType.HANA) {
			if ("string_agg".equals(funLow)) {
				return super.IGNORE;
			}
			if (orderBy != null) {
				return " string_agg(" + expr + "," + sign + ") within group (order by " + orderBy + ") ";
			}
			return " string_agg(" + expr + "," + sign + ") ";
		}
		// update 2026-9-6
		// 补sqlite:无group_concat(separator)关键字语法,以group_concat(expr,sep)两参形态
		if (dbType == DBType.SQLITE) {
			// sqlite 3.44+聚合支持内嵌order by(旧版响亮报错)
			if (orderBy != null) {
				return " group_concat(" + expr + "," + sign + " order by " + orderBy + ") ";
			}
			return " group_concat(" + expr + "," + sign + ") ";
		}
		return super.IGNORE;
	}

	// 目标方言对该聚合写法原生支持(同名透传)时返回true,连同尾部子句原样保留
	private boolean isNativeOnDialect(int dbType, String funLow) {
		if ("listagg".equals(funLow)) {
			return dbType == DBType.ORACLE || dbType == DBType.ORACLE11 || dbType == DBType.DM
					|| dbType == DBType.OCEANBASE || dbType == DBType.DB2;
		}
		if ("string_agg".equals(funLow)) {
			return dbType == DBType.POSTGRESQL || dbType == DBType.POSTGRESQL14 || dbType == DBType.GAUSSDB
					|| dbType == DBType.OPENGAUSS || dbType == DBType.OSCAR || dbType == DBType.STARDB
					|| dbType == DBType.MOGDB || dbType == DBType.VASTBASE || dbType == DBType.KINGBASE
					|| dbType == DBType.SQLSERVER || dbType == DBType.HANA;
		}
		if ("group_concat".equals(funLow)) {
			return dbType == DBType.MYSQL || dbType == DBType.TIDB || dbType == DBType.MYSQL57
					|| dbType == DBType.H2 || dbType == DBType.DORIS || dbType == DBType.STARROCKS;
		}
		return false;
	}

	// 文本中的order by子句拆分:[前段,排序表达式],无order by返回null
	// update 2026-10-8 字面量感知:字面量内的order by文本(如分隔符'... order by ...')不得
	// 误判为排序子句劈开字面量(实测原实现把group_concat('a order by b')劈成listagg('a,','));
	// 在等长掩码串上定位命中区间,按同位索引切分原串(掩码与原串逐字符等长)
	private String[] splitOrderBy(String text) {
		String masked = SqlConfigParseUtils.maskLiterals(text, false);
		java.util.regex.Matcher m = orderByPattern.matcher(masked);
		if (!m.find()) {
			return null;
		}
		// orderByPattern以\s+开头匹配至order by之后的空白,排序表达式自匹配结束起
		return new String[] { text.substring(0, m.start()).trim(), text.substring(m.end()).trim() };
	}
}
