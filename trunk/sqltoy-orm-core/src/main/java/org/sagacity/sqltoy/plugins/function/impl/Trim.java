package org.sagacity.sqltoy.plugins.function.impl;

import java.util.Locale;
import java.util.regex.Pattern;

import org.sagacity.sqltoy.plugins.function.IFunction;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * @project sagacity-sqltoy
 * @description 字符串去除两边的空白
 * @author zhongxuchen
 * @version v1.0,Date:2013-03-21
 */
public class Trim extends IFunction {
	private static Pattern regex = Pattern.compile("(?i)\\Wtrim\\(");

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.config.function.IFunction#dialects()
	 */
	@Override
	public String dialects() {
		return ALL;
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

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.config.function.IFunction#wrap(int,
	 * java.lang.String[])
	 */
	@Override
	public String wrap(int dialect, String functionName, boolean hasArgs, String... args) {
		if (args == null || args.length == 0) {
			return super.IGNORE;
		}
		// update 2026-9-5 修饰符形态(trim(both 'x' from col)等)按目标库分级处理:
		// H2/mysql/oracle/pg/db2原生支持标准修饰符形态原样保留;
		// sqlserver的TRIM不支持both/leading/trailing关键字,剔除字符为空格(或未写)时
		// 按语义转ltrim/rtrim/rtrim(ltrim),非空格字符无法表达则原样保留响亮报错;
		// sqlite无FROM形态,但trim/ltrim/rtrim的二参剔除字符集语义与标准一致,可无损映射
		String arg = args[0].trim();
		String argLow = arg.toLowerCase(Locale.ROOT);
		// trim(from col)是标准形态,FROM前没有前导空格,须显式识别串首from
		// update 2026-10-22 修饰符判定改在掩码串上进行(等长同位):单参trim(' from ')等
		// 字面量内含" from "文本被误判为修饰符形态,sqlite/starrocks分支据此产出trim(',')
		// (整个参数被替换为逗号常量、原值丢失,实测执行返回','而非'from');
		// 掩码由maskQuoted单趟完成:字符串'...'(标准SQL ''转义)与带引号标识符"..."/`...`/[...]
		// (clearMark剔注释时保留标识符,mysql的"..."默认为字符串故能到达此处)一并遮蔽,仅用于判定,
		// 内容截取仍用原串;注释无需处理——解析期clearMark已在函数转换前剔除(parseSqlToyConfig链路);
		// 不可分次套用maskLiterals——标识符内的另一种引号会跨引号配对,反而把真实from一起吞掉
		String maskedArgLow = maskQuoted(argLow);
		boolean modifierForm = maskedArgLow.startsWith("from ") || maskedArgLow.contains(" from ");
		// update 2026-9-9 rest按实际前导关键字长度截取:原按keyword.length()定长截取,
		// 对省略BOTH/LEADING/TRAILING关键字的trim('x' from col)形态会把remstr首部误截掉
		String keyword = "both";
		String rest = "";
		if (modifierForm) {
			if (argLow.startsWith("leading ")) {
				keyword = "leading";
				rest = arg.substring(8).trim();
			} else if (argLow.startsWith("trailing ")) {
				keyword = "trailing";
				rest = arg.substring(9).trim();
			} else if (argLow.startsWith("both ")) {
				rest = arg.substring(5).trim();
			} else {
				// trim('x' from col)/trim(from col)形态,无关键字前缀,rest保持整体
				rest = arg;
			}
		}
		// update 2026-10-22 fromIdx同样在掩码串上定位:trim(both ' from ' from col)等字面量内含
		// " from "时原indexOf会命中字面量内位置而切错参数;掩码串与原串等长同位,索引可直接用于原串
		String maskedRestLow = maskQuoted(rest.toLowerCase(Locale.ROOT));
		int fromIdx = maskedRestLow.startsWith("from ") ? 0 : maskedRestLow.indexOf(" from ");
		// update 2026-10-19 真库实测:标准组中 mysql 9.3 对 trim(from x) 报1064语法错、
		// oracle 23ai 报ORA-30000(两者均要求省略剔除字符时不能保留FROM);省略字符的
		// from形态语义即"默认空格",统一改写成单参trim/单向ltrim/rtrim(标准组原生产物,
		// 与trim(both ' ' from x)等价)。H2/SQLServer/SQLite/StarRocks已有专门分支,不在此处理
		if (modifierForm && fromIdx == 0 && dialect != DBType.H2 && dialect != DBType.SQLSERVER
				&& dialect != DBType.SQLITE && dialect != DBType.STARROCKS) {
			String col = rest.substring(5).trim();
			if (!col.isEmpty()) {
				if ("leading".equals(keyword)) {
					return "ltrim(" + col + ")";
				}
				if ("trailing".equals(keyword)) {
					return "rtrim(" + col + ")";
				}
				return "trim(" + col + ")";
			}
		}
		if (dialect == DBType.H2) {
			// H2原生支持标准修饰符形态;trim(from col)显式归一为both空格形态
			if (modifierForm) {
				if (fromIdx == 0 && "both".equals(keyword)) {
					String col = rest.substring(5).trim();
					if (!col.isEmpty()) {
						return "trim(both ' ' from " + col + ")";
					}
				}
				return super.IGNORE;
			}
			return "trim(both ' ' from " + arg + ")";
		}
		// update 2026-10-11 starrocks同样不支持trim(both..from..)关键字语法(真库实测语法
		// 报错),但支持二参字符集形态trim/ltrim/rtrim(s,col)(真库实测),与sqlite同法
		if (dialect == DBType.SQLITE || dialect == DBType.STARROCKS) {
			// sqlite无FROM形态:转trim/ltrim/rtrim,剔除字符集语义与标准一致(无剔除字符为空格)
			if (!modifierForm) {
				return super.IGNORE;
			}
			if (fromIdx == -1) {
				return super.IGNORE;
			}
			String chars = (fromIdx > 0) ? rest.substring(0, fromIdx).trim() : "";
			String col = (fromIdx == 0) ? rest.substring(5).trim() : rest.substring(fromIdx + 6).trim();
			if ("leading".equals(keyword)) {
				return "ltrim(" + col + (chars.isEmpty() ? "" : "," + chars) + ")";
			}
			if ("trailing".equals(keyword)) {
				return "rtrim(" + col + (chars.isEmpty() ? "" : "," + chars) + ")";
			}
			return "trim(" + col + (chars.isEmpty() ? "" : "," + chars) + ")";
		}
		if (dialect == DBType.SQLSERVER) {
			if (!modifierForm) {
				return "rtrim(ltrim(" + arg + "))";
			}
			if (fromIdx == -1) {
				return super.IGNORE;
			}
			// from之前的剔除字符(去引号),未写视为空格
			String chars = (fromIdx > 0) ? rest.substring(0, fromIdx).replace("'", "").trim() : "";
			String col = (fromIdx == 0) ? rest.substring(5).trim() : rest.substring(fromIdx + 6).trim();
			// 剔除字符非空格:sqlserver无法用ltrim/rtrim表达,原样保留交由目标库报错
			if (!chars.isEmpty()) {
				return super.IGNORE;
			}
			if ("leading".equals(keyword)) {
				return "ltrim(" + col + ")";
			}
			if ("trailing".equals(keyword)) {
				return "rtrim(" + col + ")";
			}
			return "rtrim(ltrim(" + col + "))";
		}
		return super.IGNORE;
	}

	/**
	 * update 2026-10-22 把SQL片段中的非代码文本替换为等长空格(定界符保留、偏移不变),供from关键字判定: 单引号字符串(标准SQL
	 * '' 成对转义)与双引号/反引号/方括号标识符不参与判定;注释无需处理——
	 * 解析期clearMark已在函数转换前剔除(parseSqlToyConfig链路),本方法看不到注释。
	 * 单趟互斥状态机:进入一种引号后,其中出现的其它引号只当内容,规避分次掩码时跨引号配对
	 * (如标识符"a'b"内的单引号与真实from之后的引号配对会把from吞掉);未闭合的引号按既有的 字面量惯例遮蔽到串尾(仅出现在畸形sql中)
	 *
	 * @param text 待掩码的sql片段
	 * @return 等长掩码串;不含任何引号时原样返回
	 */
	private static String maskQuoted(String text) {
		if (text == null || text.isEmpty()) {
			return text;
		}
		int n = text.length();
		char[] chars = text.toCharArray();
		char[] result = null;
		int i = 0;
		while (i < n) {
			char c = chars[i];
			// 字符串/带引号标识符:''、""、`` 为成对转义(两字符均属内容),未闭合遮蔽到串尾
			if (c == '\'' || c == '"' || c == '`') {
				if (result == null) {
					result = chars.clone();
				}
				char close = c;
				i++;
				while (i < n) {
					char t = chars[i];
					if (t == close) {
						if (i + 1 < n && chars[i + 1] == close) {
							result[i] = ' ';
							result[i + 1] = ' ';
							i += 2;
							continue;
						}
						i++;
						break;
					}
					result[i] = ' ';
					i++;
				}
				continue;
			}
			// 方括号标识符:]] 为转义;不按嵌套处理(SQLServer语义,[ ]内不再嵌套,
			// PG数组下标的嵌套下标只影响遮蔽范围,不影响其外层真实from的定位)
			if (c == '[') {
				if (result == null) {
					result = chars.clone();
				}
				i++;
				while (i < n) {
					char t = chars[i];
					if (t == ']') {
						if (i + 1 < n && chars[i + 1] == ']') {
							result[i] = ' ';
							result[i + 1] = ' ';
							i += 2;
							continue;
						}
						i++;
						break;
					}
					result[i] = ' ';
					i++;
				}
				continue;
			}
			i++;
		}
		return result == null ? text : new String(result);
	}
}