package org.sagacity.sqltoy.plugins.function.impl;

import java.util.regex.Pattern;

import org.sagacity.sqltoy.plugins.function.IFunction;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * @project sagacity-sqltoy
 * @description substring_index(str,delim,count)的跨库适配(2026-11-10 补齐):
 *              MySQL系原生;Oracle系/DB2以instr族(含occurrence与负向)组合case when;
 *              PG系以split_part+length组合。count=0返回空串、|count|超界返回原串、
 *              负count取末|count|段——与MySQL语义逐项对齐(边界组合内显式case)。
 *              sqlserver(无occurrence定位能力)/sqlite/hana暂不适配,原样透传响亮报错。
 *              OB为MySQL模式原生透传(Oracle模式无此函数,按模式自行规避)。
 * @author zhongxuchen
 * @version v1.0,Date:2026-11-10
 */
public class SubstringIndex extends IFunction {

	private static Pattern regex = Pattern.compile("(?i)\\W(substring_index|split_part)\\(");

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
		String funLow = functionName.toLowerCase(java.util.Locale.ROOT);
		// update 2026-11-10 补split_part源(PG的分段习惯):split_part(str,delim,n)取第n段(正索引),
		// 序同substring_index;pg系目标原生透传,其余方言以"前n段的最后一段"等价组合
		if ("split_part".equals(funLow)) {
			return wrapSplitPart(dialect, args);
		}
		if (args == null || args.length < 3) {
			return super.IGNORE;
		}
		String str = args[0].trim();
		String delim = args[1].trim();
		String count = args[2].trim();
		// MySQL系与CH(MySQL兼容函数)原生
		// (update 2026-11-11 真库实测CH的substring_index:负count/0/超界语义与MySQL一致,
		// 但分隔符仅接受单字符,多字符报BAD_ARGUMENTS响亮——CH原生substringIndex同限,透传口径成立)
		if (dialect == DBType.MYSQL || dialect == DBType.MYSQL57 || dialect == DBType.TIDB || dialect == DBType.DORIS
				|| dialect == DBType.STARROCKS || dialect == DBType.CLICKHOUSE || dialect == DBType.OCEANBASE) {
			return super.IGNORE;
		}
		// Oracle系:instr带occurrence与负向定位;DB2的locate_in_string同语义
		if (dialect == DBType.ORACLE || dialect == DBType.ORACLE11 || dialect == DBType.DM || dialect == DBType.DB2) {
			String instr = (dialect == DBType.DB2) ? "locate_in_string" : "instr";
			// occurrence做case包装:count=0/负时包装为合法正整数(该分支结果不被外层case采用,
			// 但常量0/负在DB2编译期即报SQLCODE=-171,Oracle的instr同防)
			String posOcc = "(case when " + count + ">0 then " + count + " else 1 end)";
			String negOcc = "(case when " + count + "<0 then abs(" + count + ") else 1 end)";
			// 正count:第count次出现前截取;instr=0(delim不足count次)返回原串(MySQL超界语义)
			String positive = "(case when " + instr + "(" + str + "," + delim + ",1," + posOcc + ")=0 then " + str
					+ " else substr(" + str + ",1," + instr + "(" + str + "," + delim + ",1," + posOcc + ")-1) end)";
			// 负count:倒数第|count|次出现之后取剩余;超界同样返回原串
			String negative = "(case when " + instr + "(" + str + "," + delim + ",-1," + negOcc + ")=0 then " + str
					+ " else substr(" + str + "," + instr + "(" + str + "," + delim + ",-1," + negOcc + ")+length("
					+ delim + ")) end)";
			return "(case when " + count + "=0 then '' when " + count + ">0 then " + positive + " else " + negative
					+ " end)";
		}
		// PG系:string_to_array数组切片(切片边界越界自动裁剪,超界返回原串与MySQL对齐;count=0为空数组→'')
		if (dialect == DBType.POSTGRESQL || dialect == DBType.POSTGRESQL14 || dialect == DBType.OPENGAUSS
				|| dialect == DBType.GAUSSDB || dialect == DBType.MOGDB || dialect == DBType.VASTBASE
				|| dialect == DBType.STARDB || dialect == DBType.OSCAR || dialect == DBType.KINGBASE
				|| dialect == DBType.HIGHGO) {
			String ary = "(string_to_array(" + str + "," + delim + "))";
			String aryLen = "array_length(" + ary + ",1)";
			// 切片冒号后补空格:a[1: 2]合法但a[1:2]的":2"会被sqltoy参数解析链当作序号参数
			// (kingbase真库报No value specified for parameter 1),空格破坏紧邻形态
			// 正count:前count段(count>=total切片自动截到total→原串)
			String positive = "array_to_string(" + ary + "[1: " + count + "]," + delim + ")";
			// 负count:末|count|段=第(total+1+count)到total段(|count|>=total下界<1自动从1→原串)
			String negative = "array_to_string(" + ary + "[" + aryLen + "+1+" + count + ": " + aryLen + "]," + delim
					+ ")";
			return "(case when " + count + "=0 then '' when " + count + ">0 then " + positive + " else " + negative
					+ " end)";
		}
		return super.IGNORE;
	}

	/**
	 * @description update 2026-11-10
	 *              split_part源的方言适配:PG的split_part(str,delim,n)取第n段。 update
	 *              2026-11-11 全保真重写(PG真库为ground truth逐项对拍):
	 *              ①正索引超界(n>段数)返回''(原组合返回边界段,静默错值,实测mysql返回'c'应为'');
	 *              ②负索引(PG14+,从末尾数)原组合错一位(实测mysql的-2返回'e'应为'd'),
	 *              按符号分流:正=前n段的最后一段/负=末|n|段的第一段; ③n=0在PG报错,此处统一返回''(病态输入,响亮价值低);
	 *              ④oracle的''即NULL(库语义),探针/调用方按null容忍。
	 *              PG15+(DBType.POSTGRESQL)原生透传;PG系<14内核(Kingbase实测/OpenGauss族/POSTGRESQL14)
	 *              以正向split_part+段数守卫模拟负索引;mysql系以substring_index组合+段数守卫;CH用
	 *              arrayElement(splitByString(delim,str),n)(支持多字符分隔符/负索引/超界返回'');
	 *              oracle/dm以instr组合(负索引用负position从末尾定位而非负occurrence——Oracle/DM的
	 *              occurrence必须为正,传负数报ORA-01428/-6860真库实测);DB2的locate_in_string超界报-171
	 *              不可组合,与sqlserver/sqlite/h2一并响亮IGNORE。
	 */
	private String wrapSplitPart(int dialect, String... args) {
		if (args == null || args.length < 3) {
			return super.IGNORE;
		}
		String str = args[0].trim();
		String delim = args[1].trim();
		String n = args[2].trim();
		// update 2026-11-12 PG15+(DBType.POSTGRESQL 映射15+)原生支持负索引(PG14新增),直接透传
		if (dialect == DBType.POSTGRESQL) {
			return super.IGNORE;
		}
		// update 2026-11-12 其余PG系内核可能早于14:KingbaseES V9R1真库实测
		// split_part('a,b,c,d',',',-1) 报 "field position must be greater than zero";
		// POSTGRESQL14 承接majorVersion<15(含12/13),OpenGauss/MogDB/GaussDB(PG9.2/12系)、
		// Vastbase/StarDB/HighGo 同样不能假定负索引。用正向split_part+段数守卫等价模拟PG14语义,
		// 正整数字面量仍原样透传(常见正向用法SQL不变)
		if (dialect == DBType.POSTGRESQL14 || dialect == DBType.KINGBASE || dialect == DBType.OPENGAUSS
				|| dialect == DBType.GAUSSDB || dialect == DBType.MOGDB || dialect == DBType.VASTBASE
				|| dialect == DBType.STARDB || dialect == DBType.HIGHGO) {
			return splitPartBySplitPart(str, delim, n);
		}
		// update 2026-11-11 CH:substringIndex只接受单字符分隔符(多字符报BAD_ARGUMENTS,真库实测),
		// 改用splitByString+arrayElement:多字符分隔符、负索引从末尾、超界返回''、n=0返回''、NULL透传,
		// 均与PG split_part真库对拍一致
		if (dialect == DBType.CLICKHOUSE) {
			return "arrayElement(splitByString(" + delim + "," + str + "), " + n + ")";
		}
		boolean mysqlFamily = dialect == DBType.MYSQL || dialect == DBType.MYSQL57 || dialect == DBType.TIDB
				|| dialect == DBType.DORIS || dialect == DBType.STARROCKS || dialect == DBType.OCEANBASE;
		boolean oracleFamily = dialect == DBType.ORACLE || dialect == DBType.ORACLE11 || dialect == DBType.DM;
		if (!mysqlFamily && !oracleFamily) {
			// db2/sqlserver/sqlite/h2未适配响亮
			return super.IGNORE;
		}
		// n=0病态输入(PG报错),统一返回空串
		if ("0".equals(n)) {
			return "''";
		}
		// 段数=分隔符出现次数+1(replace差值法;delim为空串时PG本身报错,此处不防御除零)
		String cnt = "((length(" + str + ")-length(replace(" + str + "," + delim + ",'')))/length(" + delim + ")+1)";
		// 字面量判定:正/负字面量走精简单支,动态表达式(列/占位符)走全量CASE
		Long literal = parseLiteralLong(n);
		if (mysqlFamily) {
			return splitPartBySubstringIndex(str, delim, n, cnt, literal);
		}
		return splitPartByInstr(str, delim, n, cnt, literal);
	}

	/**
	 * update 2026-11-12 PG系<14内核(KingbaseES V9R1真库实测负索引报错;OpenGauss/MogDB/GaussDB等PG9.2/12系同理)
	 * 的split_part负索引模拟:用正向split_part(cnt+1+n)换算,并以段数守卫把越界负索引收敛为''(PG14语义)。
	 * 正整数字面量原生可用→IGNORE保持原SQL;n=0统一返回''(病态输入,与其它方言家族口径一致)。
	 * 
	 * @param str
	 * @param delim
	 * @param n
	 * @return
	 */
	private String splitPartBySplitPart(String str, String delim, String n) {
		Long literal = parseLiteralLong(n);
		if (literal != null && literal > 0) {
			return super.IGNORE;
		}
		if (literal != null && literal == 0) {
			return "''";
		}
		// 段数=分隔符出现次数+1(与其它方言家族同口径;delim为空串时原生同样报错,不做除零防御)
		String cnt = "((length(" + str + ")-length(replace(" + str + "," + delim + ",'')))/length(" + delim + ")+1)";
		// 负索引=第(cnt+1+n)段;|n|超界返回''(PG14负索引越界语义)
		String negative = "(case when (0 - (" + n + "))>" + cnt + " then '' else split_part(" + str + "," + delim + ","
				+ cnt + "+1+(" + n + ")) end)";
		if (literal != null) {
			return negative;
		}
		return "(case when " + n + "=0 then '' when " + n + ">0 then split_part(" + str + "," + delim + "," + n
				+ ") else " + negative + " end)";
	}

	/**
	 * @return n为纯整数字面量时返回其值,否则null(动态表达式)
	 */
	private static Long parseLiteralLong(String n) {
		try {
			return Long.valueOf(n);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/**
	 * mysql系:substring_index(s,d,n)正n取前n段、负n取末|n|段;第n段=
	 * 正n时前n段的最后一段(外层-1)、负n时末|n|段的第一段(外层1);越界由段数守卫返回''
	 */
	private String splitPartBySubstringIndex(String str, String delim, String n, String cnt, Long literal) {
		// 取负统一写(0 - (n)):避免"--"被SQL当行注释(如n=-2直接拼接0--2)
		String posExpr = "(case when " + n + ">" + cnt + " then '' else substring_index(substring_index(" + str + ","
				+ delim + "," + n + ")," + delim + ",-1) end)";
		String negExpr = "(case when (0 - (" + n + "))>" + cnt + " then '' else substring_index(substring_index(" + str
				+ "," + delim + "," + n + ")," + delim + ",1) end)";
		if (literal != null) {
			return literal > 0 ? posExpr : negExpr;
		}
		return "(case when " + n + ">0 then " + posExpr + " when " + n + "<0 then " + negExpr + " else '' end)";
	}

	/**
	 * oracle/dm:instr定位。Oracle/DM的INSTR第4参occurrence必须为正(传负数报ORA-01428/-6860,真库实测),
	 * 从末尾搜索靠第3参position=-1,故负索引为"倒序位置+正occurrence":
	 * 正n:1≤n≤cnt时第n段在第n-1与第n个分隔符之间(n=1无前分隔符取串首,n=cnt时第n个不存在取到串尾);
	 * 负n:n=-1取末段(position=-1,occurrence=1);n≤-2时第|n|段在第|n|与|n|-1个"自末尾数"的分隔符之间
	 * (|n|=cnt即首段,此时第|n|个不存在,取串首到第|n|-1个之前); 越界由段数守卫返回''(oracle中''即NULL,库语义)
	 */
	private String splitPartByInstr(String str, String delim, String n, String cnt, Long literal) {
		String ld = "length(" + delim + ")";
		// 正索引:自串首按正occurrence定位
		String pos1 = "instr(" + str + "," + delim + ",1,1)";
		String posN = "instr(" + str + "," + delim + ",1," + n + ")";
		String posNm1 = "instr(" + str + "," + delim + ",1," + n + "-1)";
		String posExpr = "(case when " + n + ">" + cnt + " then ''" + " when " + n + "=1 then (case when " + pos1
				+ "=0 then " + str + " else substr(" + str + ",1," + pos1 + "-1) end)" + " when " + posN
				+ "=0 then substr(" + str + "," + posNm1 + "+" + ld + ")" + " else substr(" + str + "," + posNm1 + "+"
				+ ld + "," + posN + "-" + posNm1 + "-" + ld + ") end)";
		// 负索引:position=-1自串尾反向搜索,occurrence取正
		String absN = "(0 - (" + n + "))";
		String posLast = "instr(" + str + "," + delim + ",-1,1)";
		String neg1Expr = "(case when " + posLast + "=0 then " + str + " else substr(" + str + "," + posLast + "+" + ld
				+ ") end)";
		String posK = "instr(" + str + "," + delim + ",-1," + absN + ")";
		String posKm1 = "instr(" + str + "," + delim + ",-1," + absN + "-1)";
		String negExpr = "(case when " + absN + ">" + cnt + " then ''" + " when " + posK + "=0 then substr(" + str
				+ ",1," + posKm1 + "-1)" + " else substr(" + str + "," + posK + "+" + ld + "," + posKm1 + "-" + posK
				+ "-" + ld + ") end)";
		if (literal != null) {
			if (literal > 0) {
				return posExpr;
			}
			return literal == -1 ? neg1Expr : negExpr;
		}
		return "(case when " + n + "=0 then '' when " + n + ">0 then " + posExpr + " when " + n + "=-1 then " + neg1Expr
				+ " else " + negExpr + " end)";
	}
}
