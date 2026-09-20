package org.sagacity.sqltoy.utils;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * @project sagacity-sqltoy
 * @description 用来处理sql中的数据库保留字
 * @author zhongxuchen
 * @version v1.0,Date:2020-05-06
 */
public class ReservedWordsUtil {
	// 保留字集合与组合正则:写侧(多context初始化的put)synchronized + 整体替换后经volatile发布,
	// 读侧(全部业务sql处理线程)无锁读取已发布快照,不修改既发布对象
	private static volatile Set<String> reservedWords = ConcurrentHashMap.newKeySet();
	private static volatile Pattern singlePattern = null;

	private ReservedWordsUtil() {
	}

	/**
	 * @param words 加载保留字, 形成一个正则表达式
	 */
	public static synchronized void put(String words) {
		if (StringUtil.isBlank(words)) {
			return;
		}
		// 合并此前批次的保留字,保证组合正则与集合内容一致(原实现正则只含当次批次)
		Set<String> merged = new HashSet<String>(reservedWords);
		for (String str : words.split("\\,")) {
			String regex = str.trim().toLowerCase(Locale.ROOT);
			if (!"".equals(regex)) {
				merged.add(regex);
			}
		}
		StringBuilder fullRegex = new StringBuilder();
		for (String word : merged) {
			if (fullRegex.length() > 0) {
				fullRegex.append('|');
			}
			fullRegex.append(word);
		}
		// 先发布集合再发布正则(均为volatile),读侧按快照消费
		reservedWords = merged;
		singlePattern = Pattern.compile("(?i)(\\W||\\s)(`|'|\"|\\[)(" + fullRegex + ")(`|'|\"|\\])(\\s||\\W)");
	}

	/**
	 * 清空保留字配置,恢复到未配置状态(put为累加合并语义且集合为全局静态,
	 * 提供clear用于单元测试间显式建立前置状态;生产配置由SqlToyContext统一加载,正常运行不会调用)
	 */
	public static synchronized void clear() {
		// 与put相同的发布顺序:先集合后正则,读侧按volatile快照消费
		reservedWords = ConcurrentHashMap.newKeySet();
		singlePattern = null;
	}

	/**
	 * @param sql
	 * @param dbType
	 * @return 处理框架基于对象操作生成的简单sql, 对默认[]符号进行数据库转换
	 */
	public static String convertSimpleSql(String sql, Integer dbType) {
		if (reservedWords.isEmpty()) {
			return sql;
		}
		// update 2026-9-15 null防护前置:原null判断位于末尾sqlserver/sqlite分支,而前面的
		// dbType==DBType.XXX比较已触发Integer拆箱,null入参在到达判断前即NPE
		// (convertWord/convertSql均为null在条件首位短路,本方法对齐该契约:null原样返回)
		if (dbType == null || dbType == DBType.SQLSERVER || dbType == DBType.SQLITE) {
			return sql;
		}
		// update 2026-9-14 方括号为字面量:改用String.replace,免去每次调用隐式编译正则(每sql 2~4次正则编译)
		// update 2026-9-15 合并:clickhouse/impala标识符引用为反引号(ch文档明确),远端已补;
		// 本地把OCEANBASE从双引号族移入反引号族(mysql模式双引号是字符串字面量)
		if (dbType == DBType.MYSQL || dbType == DBType.TIDB || dbType == DBType.MYSQL57 || dbType == DBType.TDENGINE
				|| dbType == DBType.DORIS || dbType == DBType.STARROCKS || dbType == DBType.CLICKHOUSE
				|| dbType == DBType.IMPALA || dbType == DBType.OCEANBASE) {
			return sql.replace("[", "`").replace("]", "`");
		}
		// update 2026-9-14 补KINGBASE(KingbaseES基于PG,标识符用双引号):原形态落入末尾else被剔除括号,
		// 保留字字段(如[desc])会变成裸标识符导致语法错误
		if (dbType == DBType.ORACLE || dbType == DBType.POSTGRESQL || dbType == DBType.POSTGRESQL14
				|| dbType == DBType.DB2 || dbType == DBType.DM || dbType == DBType.GAUSSDB || dbType == DBType.MOGDB
				|| dbType == DBType.STARDB || dbType == DBType.OSCAR || dbType == DBType.OPENGAUSS
				|| dbType == DBType.VASTBASE || dbType == DBType.ORACLE11 || dbType == DBType.HANA
				|| dbType == DBType.KINGBASE) {
			return sql.replace("[", "\"").replace("]", "\"");
		}
		if (dbType == DBType.H2) {
			return sql.replace("[", "\"").replace("]", "\"");
		}
		// 剔除保留字符号
		return sql.replace("[", "").replace("]", "");
	}

	/**
	 * @param column
	 * @param dbType
	 * @return 转换单词
	 */
	public static String convertWord(String column, Integer dbType) {
		if (column == null) {
			return null;
		}
		// 非保留字
		if (reservedWords.isEmpty()) {
			return column;
		}
		// 不属于关键词
		if (!reservedWords.contains(column.toLowerCase(Locale.ROOT))) {
			return column;
		}
		// 默认加上[]符合便于后面根据不同数据库类型进行替换,而其他符号则难以替换
		if (dbType == null || dbType == DBType.SQLSERVER || dbType == DBType.SQLITE) {
			return "[".concat(column).concat("]");
		}
		// update 2026-9-15 补clickhouse/impala(反引号引用标识符),原落末尾else返回裸标识符
		if (dbType == DBType.MYSQL || dbType == DBType.TIDB || dbType == DBType.MYSQL57 || dbType == DBType.TDENGINE
				|| dbType == DBType.DORIS || dbType == DBType.STARROCKS || dbType == DBType.CLICKHOUSE
				|| dbType == DBType.IMPALA || dbType == DBType.OCEANBASE) {
			return "`".concat(column).concat("`");
		}
		if (dbType == DBType.H2) {
			return "\"".concat(column).concat("\"");
		}
		if (dbType == DBType.ORACLE || dbType == DBType.POSTGRESQL || dbType == DBType.POSTGRESQL14
				|| dbType == DBType.KINGBASE || dbType == DBType.DB2 || dbType == DBType.GAUSSDB
				|| dbType == DBType.MOGDB || dbType == DBType.OPENGAUSS || dbType == DBType.VASTBASE
				|| dbType == DBType.STARDB || dbType == DBType.OSCAR || dbType == DBType.DM || dbType == DBType.ORACLE11
				|| dbType == DBType.HANA) {
			return "\"".concat(column).concat("\"");
		}
		return column;
	}

	/**
	 * 对整个sql进行保留字处理
	 * 
	 * @param sql
	 * @param dbType
	 * @return
	 */
	public static String convertSql(String sql, Integer dbType) {
		// 按发布快照读取,模式判空防御极端可见性窗口
		Pattern pattern = singlePattern;
		if (reservedWords.isEmpty() || pattern == null) {
			return sql;
		}
		if (dbType == null || dbType == DBType.ES || dbType == DBType.MONGO || dbType == DBType.UNDEFINE) {
			return sql;
		}
		StringBuilder sqlBuff = new StringBuilder();
		Matcher matcher;
		int start = 0;
		String keyWord;
		matcher = pattern.matcher(sql);
		while (matcher.find()) {
			// update 2026-9-14 改按关键字组(第3组)位置定位:原实现用matcher.start()+1、match.substring(1)
			// 与subSize的定长边界算术,隐含"首尾边界各消费1个字符"的假定,边界为空匹配时(sql以[col]收尾,
			// 或[col]紧邻单词字符)会把引用符号与相邻字符错位并丢空白,输出被改坏的sql
			// 关键字组前后各恰好一个单字符引用符号(第2、4组),故替换区间取组前后各一个字符,
			// 边界是否命中不再影响定位
			keyWord = matcher.group(3);
			sqlBuff.append(sql, start, matcher.start(3) - 1);
			if (dbType == DBType.POSTGRESQL || dbType == DBType.POSTGRESQL14 || dbType == DBType.ORACLE
					|| dbType == DBType.DB2 || dbType == DBType.KINGBASE || dbType == DBType.GAUSSDB
					|| dbType == DBType.MOGDB || dbType == DBType.OPENGAUSS || dbType == DBType.VASTBASE
					|| dbType == DBType.DM || dbType == DBType.ORACLE11 || dbType == DBType.STARDB
					|| dbType == DBType.OSCAR || dbType == DBType.HANA) {
				sqlBuff.append("\"").append(keyWord).append("\"");
			} else if (dbType == DBType.SQLSERVER || dbType == DBType.SQLITE) {
				sqlBuff.append("[").append(keyWord).append("]");
			} else if (dbType == DBType.MYSQL || dbType == DBType.TIDB || dbType == DBType.MYSQL57
					|| dbType == DBType.TDENGINE || dbType == DBType.DORIS || dbType == DBType.STARROCKS
					|| dbType == DBType.CLICKHOUSE || dbType == DBType.IMPALA || dbType == DBType.OCEANBASE) {
				sqlBuff.append("`").append(keyWord).append("`");
			} else if (dbType == DBType.H2) {
				sqlBuff.append("\"").append(keyWord).append("\"");
			} else {
				sqlBuff.append(keyWord);
			}
			start = matcher.end(3) + 1;
		}

		if (start > 0) {
			sqlBuff.append(sql.substring(start));
			return sqlBuff.toString();
		}
		return sql;
	}

	/**
	 * 判断列名称是否是关键词
	 * 
	 * @param column
	 * @return
	 */
	public static boolean isKeyWord(String column) {
		if (column == null) {
			return false;
		}
		return reservedWords.contains(column.toLowerCase(Locale.ROOT));
	}
}
