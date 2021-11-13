package org.sagacity.sqltoy.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.config.model.SqlParamsModel;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlToyResult;
import org.sagacity.sqltoy.config.model.SqlType;
import org.sagacity.sqltoy.config.model.SqlWithAnalysis;
import org.sagacity.sqltoy.model.IgnoreKeyCaseMap;
import org.sagacity.sqltoy.plugins.function.FunctionUtils;
import org.sagacity.sqltoy.plugins.id.macro.AbstractMacro;
import org.sagacity.sqltoy.plugins.id.macro.MacroUtils;
import org.sagacity.sqltoy.plugins.id.macro.impl.SqlLoop;
import org.sagacity.sqltoy.utils.CollectionUtil;
import org.sagacity.sqltoy.utils.DataSourceUtils;
import org.sagacity.sqltoy.utils.MacroIfLogic;
import org.sagacity.sqltoy.utils.ReservedWordsUtil;
import org.sagacity.sqltoy.utils.SqlUtil;
import org.sagacity.sqltoy.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sagacity-sqltoy
 * @description 提供sqlToy 针对sql语句以及查询条件加工处理的通用函数(sqltoy中最关键的sql加工)
 * @author zhongxuchen
 * @version v1.0,Date:2009-12-14
 * @modify {Date:2010-6-10, 修改replaceNull函数}
 * @modify {Date:2011-6-4, 修改了因sql中存在":"符号导致的错误}
 * @modify {Date:2011-12-11, 优化了StringMatch方式，将Pattern放在外面定义，避免每次重复定义消耗性能}
 * @modify {Date:2012-7-10, 完善了in ()条件查询，提供了数组扩充参数和字符串替换成 in (value)两种模式
 *         解决了可能通过in()模式的sql注入 }
 * @modify {Date:2012-8-3,
 *         修改了:named匹配正则表达式以及匹配处理，排除to_char(date,'HH:mm:ss')形式出现的错误}
 * @modify {Date:2012-8-23, 修复了直接用?替代变量名称导致=符合丢失错误}
 * @modify {Date:2012-9-11, 对于xml中配置的sql文件已经通过sql加载时完成了参数名称的替换,避免每次执行时的替换}
 * @modify {Date:2012-11-15,将in (:named)
 *         named对应的值因使用combineInStr数组长度为1自动添加了'value', 单引号而导致查询错误问题}
 * @modify {Date:2015-12-09,修改#[sql],sql中如果没有参数剔除#[sql]}
 * @modify {Date:2016-5-27,在sql语句中提供#[@blank(:named) sql] 以及 #[@value(:named)
 *         sql] 形式,使得增强sql组织拼装能力}
 * @modify {Date:2016-6-7,增加sql中的全角字符替换功能,增强sql的解析能力}
 * @modify {Date:2017-12-7,优化where和and 或or的拼接处理}
 * @modify {Date:2019-02-21,增强:named 参数匹配正则表达式,参数中必须要有字母}
 * @modify {Date:2019-06-26,修复条件参数中有问号的bug，并放开条件参数名称不能是单个字母的限制}
 * @modify {Date:2019-10-11 修复@if(:name==null) 不参与逻辑判断bug }
 * @modify {Date:2020-04-14 修复三个以上 in(?) 查询，在中间的in 参数值为null时 processIn方法处理错误}
 * @modify {Date:2020-09-23 增加@loop()组织sql功能,完善极端场景下动态组织sql的能力}
 * @modify {Date:2021-04-29 调整@value(?)处理顺序到末尾，规避参数值中存在? }
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public class SqlConfigParseUtils {

	/**
	 * 定义全局日志
	 */
	protected final static Logger logger = LoggerFactory.getLogger(SqlConfigParseUtils.class);

	/**
	 * sql伪指令开始标记,#[]符号等于 null==?判断
	 */
	public final static String SQL_PSEUDO_START_MARK = "#[";
	// 2021-01-17 改进对称位置寻找策略,兼容sql中存在[] 符号
	public final static String SQL_PSEUDO_SYM_START_MARK = "[";
	public final static int SQL_PSEUDO_START_MARK_LENGTH = SQL_PSEUDO_START_MARK.length();

	/**
	 * sql伪指令收尾标记
	 */
	public final static String SQL_PSEUDO_END_MARK = "]";
	public final static int SQL_PSEUDO_END_MARK_LENGTH = SQL_PSEUDO_END_MARK.length();

	/**
	 * 先通过分页最小化符合条件的数据集,然后再关联查询,建议@fast(xx)模式,@fastPage(xx) 为兼容旧版
	 */
	public final static Pattern FAST_PATTERN = Pattern.compile("(?i)\\@fast(Page)?\\([\\w\\W]+\\)");

	// sql中 in (?)条件
	public final static Pattern IN_PATTERN = Pattern.compile("(?i)\\s+in\\s*\\(\\s*\\?\\s*\\)");
	public final static Pattern LIKE_PATTERN = Pattern.compile("(?i)\\s+like\\s+\\?");

	// add 2016-5-27 by chenrenfei
	public final static String BLANK_REGEX = "(?i)\\@blank\\s*\\(\\s*\\?\\s*\\)";
	public final static Pattern BLANK_PATTERN = Pattern.compile(BLANK_REGEX);
	public final static String VALUE_REGEX = "(?i)\\@value\\s*\\(\\s*(\\?|null)\\s*\\)";
	public final static Pattern VALUE_PATTERN = Pattern.compile(VALUE_REGEX);
	public final static Pattern IF_PATTERN = Pattern.compile("(?i)\\@if\\s*\\(");

	public final static String BLANK = " ";
	// 匹配时已经小写转换
	public final static Pattern IS_PATTERN = Pattern.compile("\\s+is\\s+(not)?\\s+\\?");
	public final static String ARG_NAME = "?";
	public final static String ARG_REGEX = "\\?";
	public final static String ARG_DBL_NAME = "??";
	public final static String ARG_DBL_REGEX = "\\?{2}";
	public final static Pattern ARG_NAME_PATTERN = Pattern.compile(ARG_REGEX);
	public final static String ARG_NAME_BLANK = "? ";

	// sql 拼接时判断前部分sql是否是where 结尾,update 2017-12-4 增加(?i)忽视大小写
	public final static Pattern WHERE_END_PATTERN = Pattern.compile("(?i)\\Wwhere\\s*$");
	// where 1=1 结尾模式
	public final static Pattern WHERE_ONE_EQUAL_PATTERN = Pattern.compile("(?i)\\Wwhere\\s*1\\s*=\\s*1\\s*$");

	public final static Pattern AND_START_PATTERN = Pattern.compile("(?i)^and\\W");
	public final static Pattern OR_START_PATTERN = Pattern.compile("(?i)^or\\W");

	// update set 语法
	public final static Pattern UPDATE_SET_PATTERN = Pattern.compile("(?i)\\Wset\\s*$");

	// sqlId 必须是字母数字和-和_符号组成的字符串
	public final static Pattern SQL_ID_PATTERN = Pattern.compile("^[A-Za-z_0-9\\-]+$");

	public final static Pattern WHERE_CLOSE_PATTERN = Pattern
			.compile("^((order|group)\\s+by|(inner|left|right|full)\\s+join|having|union)\\W");

	public final static String DBL_QUESTMARK = "#sqltoy_dblqsmark_placeholder#";

	// 利用宏模式来完成@loop循环处理
	private static Map<String, AbstractMacro> macros = new HashMap<String, AbstractMacro>();

	static {
		macros.put("@loop", new SqlLoop());
	}

	// 避免实例化
	private SqlConfigParseUtils() {

	}

	/**
	 * @todo 判断sql语句中是否存在:named 方式的参数
	 * @param sql
	 * @return
	 */
	public static boolean hasNamedParam(String sql) {
		if (sql == null) {
			return false;
		}
		return StringUtil.matches(sql, SqlToyConstants.SQL_NAMED_PATTERN);
	}

	/**
	 * @todo 判定是否存在内部快速子查询
	 * @param sql
	 * @return
	 */
	public static boolean hasFast(String sql) {
		return StringUtil.matches(sql, FAST_PATTERN);
	}

	/**
	 * @todo 判断是否存在with形式的查询
	 * @param sql
	 * @return
	 */
	public static boolean hasWith(String sql) {
		return StringUtil.matches(sql, SqlToyConstants.withPattern);
	}

	/**
	 * @todo 判断查询语句是query命名还是直接就是查询sql
	 * @param queryStr
	 * @return
	 */
	public static boolean isNamedQuery(String queryStr) {
		if (StringUtil.isBlank(queryStr)) {
			return false;
		}
		// 强制约定sqlId key必须没有空格、回车、tab和换行符号
		return StringUtil.matches(queryStr.trim(), SQL_ID_PATTERN);
	}

	public static SqlToyResult processSql(String queryStr, String[] paramsNamed, Object[] paramsArg) {
		return processSql(queryStr, paramsNamed, paramsArg, null);
	}

	/**
	 * @todo 判断条件为null,过滤sql的组合查询条件example: queryStr= select t1.* from xx_table t1
	 *       where #[t1.status=?] #[and t1.auditTime=?]
	 * @param queryStr
	 * @param paramsNamed
	 * @param paramsArg
	 * @param dialect
	 * @return
	 */
	public static SqlToyResult processSql(String queryStr, String[] paramsNamed, Object[] paramsArg, String dialect) {
		Object[] paramsValue = paramsArg;
		if (paramsNamed != null && paramsNamed.length > 0) {
			// 构造全是null的条件值，将全部条件去除
			if (null == paramsArg || paramsArg.length == 0) {
				paramsValue = new Object[paramsNamed.length];
			}
		} // 无参数别名也无条件值
		else if (null == paramsArg || paramsArg.length == 0) {
			return new SqlToyResult(queryStr, paramsArg);
		}
		SqlToyResult sqlToyResult = new SqlToyResult();
		// 是否:paramName 形式的参数模式
		boolean isNamedArgs = StringUtil.matches(queryStr, SqlToyConstants.SQL_NAMED_PATTERN);
		SqlParamsModel sqlParam;
		// 将sql中的问号临时先替换成特殊字符
		String questionMark = "#sqltoy_qsmark_placeholder#";
		if (isNamedArgs) {
			String sql = queryStr.replaceAll(ARG_REGEX, questionMark);
			// update 2020-09-23 处理sql中的循环(提前处理循环，避免循环中存在其它条件参数)
			sql = processLoop(sql, paramsNamed, paramsValue);
			sqlParam = processNamedParamsQuery(sql);
		} else {
			// 将sql中的??符号替换成特殊字符,?? 符号在json场景下有特殊含义
			String sql = queryStr.replaceAll(ARG_DBL_REGEX, DBL_QUESTMARK);
			sqlParam = processNamedParamsQuery(sql);
		}
		sqlToyResult.setSql(sqlParam.getSql());
		// 参数和参数值进行匹配
		sqlToyResult.setParamsValue(matchNamedParam(sqlParam.getParamsName(), paramsNamed, paramsValue));
		// 剔除查询条件为null的sql语句和对应的参数
		processNullConditions(sqlToyResult);
		// 替换@blank(?)为空白,增强sql组织能力
		processBlank(sqlToyResult);
		// 检查 like 对应参数部分，如果参数中不存在%符合则自动两边增加%
		processLike(sqlToyResult);
		// in 处理策略2012-7-10 进行了修改，提供参数preparedStatement.setObject()机制，并同时兼容
		// 用具体数据替换 in (?)中问号的处理机制
		processIn(sqlToyResult);
		// 参数为null的处理策略(用null直接代替变量)
		replaceNull(sqlToyResult, 0);
		// update 2021-4-29 放在最后，避免参数值中存在?号
		// 替换@value(?) 为参数对应的数值
		processValue(sqlToyResult, dialect);
		// 将特殊字符替换回问号
		if (isNamedArgs) {
			sqlToyResult.setSql(sqlToyResult.getSql().replaceAll(questionMark, ARG_NAME));
		} else {
			// 将代表json中的?? 符号换回
			sqlToyResult.setSql(sqlToyResult.getSql().replaceAll(DBL_QUESTMARK, ARG_DBL_NAME));
		}
		return sqlToyResult;
	}

	/**
	 * @TODO 剔除掉sql中的??特殊转义符号，避免对?传参的干扰
	 * @param sql
	 * @return
	 */
	public static String clearDblQuestMark(String sql) {
		if (StringUtil.isBlank(sql)) {
			return sql;
		}
		return sql.replaceAll(ARG_DBL_REGEX, DBL_QUESTMARK);
	}

	/**
	 * @TODO 恢复??特殊转义符号
	 * @param sql
	 * @return
	 */
	public static String recoverDblQuestMark(String sql) {
		if (StringUtil.isBlank(sql)) {
			return sql;
		}
		return sql.replaceAll(DBL_QUESTMARK, ARG_DBL_NAME);
	}

	/**
	 * @TODO 判断sql中是否有?条件参数
	 * @param sql
	 * @return
	 */
	public static boolean hasQuestMarkArgs(String sql) {
		if (StringUtil.isBlank(sql)) {
			return false;
		}
		String lastSql = clearDblQuestMark(sql);
		return lastSql.indexOf(ARG_NAME) != -1;
	}

	/**
	 * @todo 通过xml文件中的sql named参数跟给定的参数名称和数值进行匹配，构造sql参数 对应的数据值数组
	 * @param sqlParamsName
	 * @param paramsNameOrder
	 * @param paramsValue
	 * @return
	 */
	public static Object[] matchNamedParam(String[] sqlParamsName, String[] paramsNameOrder, Object[] paramsValue) {
		if (null == sqlParamsName || sqlParamsName.length == 0) {
			if (null == paramsNameOrder || paramsNameOrder.length == 0) {
				return paramsValue;
			}
			return null;
		}
		Object[] result = new Object[sqlParamsName.length];
		if (null != paramsNameOrder && paramsNameOrder.length > 0) {
			HashMap<String, Object> nameValueMap = new HashMap<String, Object>();
			int i = 0;
			for (String name : paramsNameOrder) {
				nameValueMap.put(name.toLowerCase(), paramsValue[i]);
				i++;
			}
			i = 0;
			// 不区分大小写匹配
			for (String name : sqlParamsName) {
				result[i] = nameValueMap.get(name.toLowerCase());
				i++;
			}
		}
		return result;
	}

	/**
	 * @todo 处理named 条件参数，将所有:paramName 替换成? 并重构参数值数组
	 * @param queryStr
	 * @return
	 */
	public static SqlParamsModel processNamedParamsQuery(String queryStr) {
		// 提取sql语句中的命名参数
		SqlParamsModel sqlParam = new SqlParamsModel();
		sqlParam.setSql(queryStr);
		Matcher m = SqlToyConstants.SQL_NAMED_PATTERN.matcher(queryStr);
		// 用来替换:paramName
		List<String> paramsName = new ArrayList<String>();
		StringBuilder lastSql = new StringBuilder();
		int start = 0;
		String group;
		while (m.find()) {
			group = m.group();
			// 剔除\\W\\: 两位字符
			paramsName.add(group.substring(2).trim());
			lastSql.append(BLANK).append(queryStr.substring(start, m.start())).append(group.charAt(0))
					.append(ARG_NAME_BLANK);
			start = m.end();
		}
		// 没有别名参数
		if (start == 0) {
			return sqlParam;
		}
		// 添加尾部sql
		lastSql.append(queryStr.substring(start));
		sqlParam.setSql(lastSql.toString());
		sqlParam.setParamsName(paramsName.toArray(new String[paramsName.size()]));
		return sqlParam;
	}

	/**
	 * @todo 提取sql中参数(:paramName)名称组成数组返回(去除重复)
	 * @param queryStr
	 * @param distinct
	 * @return
	 */
	public static String[] getSqlParamsName(String queryStr, boolean distinct) {
		Matcher m = SqlToyConstants.SQL_NAMED_PATTERN.matcher(queryStr);
		// 用来替换:paramName
		List<String> paramsNameList = new ArrayList<String>();
		HashSet<String> distinctSet = new HashSet<String>();
		String paramName;
		while (m.find()) {
			// 剔除\\W\\:两位字符
			paramName = m.group().substring(2).trim();
			// 去除重复
			if (distinct) {
				if (!distinctSet.contains(paramName.toLowerCase())) {
					paramsNameList.add(paramName);
					distinctSet.add(paramName.toLowerCase());
				}
			} else {
				paramsNameList.add(paramName);
			}
		}
		// 没有别名参数
		if (paramsNameList.isEmpty()) {
			return null;
		}
		return paramsNameList.toArray(new String[paramsNameList.size()]);
	}

	/**
	 * @todo 提取nosql语句中参数(:paramName)名称组成数组返回
	 * @param queryStr
	 * @param distinct
	 * @return
	 */
	public static String[] getNoSqlParamsName(String queryStr, boolean distinct) {
		Matcher m = SqlToyConstants.NOSQL_NAMED_PATTERN.matcher(queryStr);
		// 用来替换:paramName
		List<String> paramsNameList = new ArrayList<String>();
		HashSet<String> distinctSet = new HashSet<String>();
		String paramName;
		String groupStr;
		while (m.find()) {
			groupStr = m.group();
			paramName = groupStr.substring(groupStr.indexOf(":") + 1, groupStr.indexOf(")")).trim();
			// 去除重复
			if (distinct) {
				if (!distinctSet.contains(paramName.toLowerCase())) {
					paramsNameList.add(paramName);
					distinctSet.add(paramName.toLowerCase());
				}
			} else {
				paramsNameList.add(paramName);
			}
		}
		// 没有别名参数
		if (paramsNameList.isEmpty()) {
			return null;
		}
		return paramsNameList.toArray(new String[paramsNameList.size()]);
	}

	/**
	 * @todo 判断条件是否为null,过滤sql的组合查询条件 example:
	 *       <p>
	 *       select t1.* from xx_table t1 where #[t1.status=?] #[and t1.auditTime=?]
	 *       </p>
	 * @param sqlToyResult
	 */
	private static void processNullConditions(SqlToyResult sqlToyResult) {
		String queryStr = sqlToyResult.getSql();
		int pseudoMarkStart = queryStr.indexOf(SQL_PSEUDO_START_MARK);
		if (pseudoMarkStart == -1) {
			return;
		}
		int beginIndex, endIndex, paramCnt, preParamCnt, beginMarkIndex, endMarkIndex;
		String preSql, markContentSql, tailSql, iMarkSql;
		List paramValuesList = CollectionUtil.arrayToList(sqlToyResult.getParamsValue());
		boolean logicValue;
		int ifStart;
		int ifEnd;
		Object paramValue;
		// sql中是否存在is
		boolean sqlhasIs;
		String evalStr;
		int logicParamCnt;
		while (pseudoMarkStart != -1) {
			// 始终从最后一个#[]进行处理
			beginMarkIndex = queryStr.lastIndexOf(SQL_PSEUDO_START_MARK);
			// update 2021-01-17 按照"["和"]" 找对称位置，兼容sql中存在[]场景
			endMarkIndex = StringUtil.getSymMarkIndex(SQL_PSEUDO_SYM_START_MARK, SQL_PSEUDO_END_MARK, queryStr,
					beginMarkIndex);
			// 最后一个#[前的sql
			preSql = queryStr.substring(0, beginMarkIndex).concat(BLANK);
			// 最后#[]中的查询语句,加空白减少substr(index+1)可能引起的错误
			markContentSql = BLANK
					.concat(queryStr.substring(beginMarkIndex + SQL_PSEUDO_START_MARK_LENGTH, endMarkIndex))
					.concat(BLANK);
			tailSql = queryStr.substring(endMarkIndex + SQL_PSEUDO_END_MARK_LENGTH);
			// 获取#[]中的参数数量
			paramCnt = StringUtil.matchCnt(markContentSql, ARG_NAME_PATTERN);
			// #[]中无参数，拼接preSql+markContentSql+tailSql
			if (paramCnt == 0) {
				queryStr = processWhereLinkAnd(preSql, BLANK, tailSql);
			} else {
				// 在#[前的参数个数
				preParamCnt = StringUtil.matchCnt(preSql, ARG_NAME_PATTERN);
				// 判断是否有@if(xx==value1||xx>=value2) 形式的逻辑判断
				logicValue = true;
				ifStart = StringUtil.matchIndex(markContentSql, IF_PATTERN);
				// sql中存在逻辑判断
				if (ifStart > -1) {
					ifEnd = StringUtil.getSymMarkIndex("(", ")", markContentSql, ifStart);
					evalStr = markContentSql.substring(markContentSql.indexOf("(", ifStart) + 1, ifEnd);
					logicParamCnt = StringUtil.matchCnt(evalStr, ARG_NAME_PATTERN);
					// update 2019-10-11 修复@if(:name==null) 不参与逻辑判断bug
					logicValue = MacroIfLogic.evalLogic(evalStr, paramValuesList, preParamCnt, logicParamCnt);
					// 逻辑不成立,剔除sql和对应参数
					if (!logicValue) {
						markContentSql = BLANK;
						for (int k = paramCnt; k > 0; k--) {
							paramValuesList.remove(k + preParamCnt - 1);
						}
					} else {
						// 逻辑成立,去除@if()部分sql和对应的参数,同时将剩余参数数量减掉@if()中的参数数量
						markContentSql = markContentSql.substring(0, ifStart)
								.concat(markContentSql.substring(ifEnd + 1));
						for (int k = 0; k < logicParamCnt; k++) {
							paramValuesList.remove(preParamCnt);
						}
						paramCnt = paramCnt - logicParamCnt;
					}
				}
				// 逻辑成立,继续sql中参数是否为null的逻辑判断
				if (logicValue) {
					beginIndex = 0;
					endIndex = 0;
					// 按顺序处理#[]中sql的参数
					for (int i = preParamCnt; i < preParamCnt + paramCnt; i++) {
						sqlhasIs = false;
						beginIndex = endIndex;
						endIndex = markContentSql.indexOf(ARG_NAME, beginIndex);
						// 不是#[]中的最后一个参数
						if (i - preParamCnt + 1 < paramCnt) {
							iMarkSql = markContentSql.substring(beginIndex + 1,
									StringUtil.indexOrder(markContentSql, ARG_NAME, i - preParamCnt + 1));
						} else {
							iMarkSql = markContentSql.substring(beginIndex + 1);
						}

						// 判断是否是is 条件
						if (StringUtil.matches(iMarkSql.toLowerCase(), IS_PATTERN)) {
							sqlhasIs = true;
						}
						paramValue = paramValuesList.get(i);
						// 1、参数值为null且非is 条件sql语句
						// 2、is 条件sql语句值非null、true、false 剔除#[]部分内容，同时将参数从数组中剔除
						if ((null == paramValue && !sqlhasIs)
								|| (null != paramValue && paramValue.getClass().isArray()
										&& CollectionUtil.convertArray(paramValue).length == 0)
								|| (null != paramValue && (paramValue instanceof Collection)
										&& ((Collection) paramValue).isEmpty())
								|| (sqlhasIs && null != paramValue && !(paramValue instanceof java.lang.Boolean))) {
							// sql中剔除最后部分的#[]内容
							markContentSql = BLANK;
							for (int k = paramCnt; k > 0; k--) {
								paramValuesList.remove(k + preParamCnt - 1);
							}
							break;
						}
					}
				}
				queryStr = processWhereLinkAnd(preSql, markContentSql, tailSql);
			}
			pseudoMarkStart = queryStr.indexOf(SQL_PSEUDO_START_MARK);
		}
		sqlToyResult.setSql(queryStr);
		sqlToyResult.setParamsValue(paramValuesList.toArray());
	}

	/**
	 * @TODO 将@blank(:paramName) 设置为" "空白输出,同时在条件数组中剔除:paramName对应位置的条件值
	 * @param sqlToyResult
	 */
	private static void processBlank(SqlToyResult sqlToyResult) {
		if (null == sqlToyResult.getParamsValue() || sqlToyResult.getParamsValue().length == 0) {
			return;
		}
		String queryStr = sqlToyResult.getSql().toLowerCase();
		Matcher m = BLANK_PATTERN.matcher(queryStr);
		int index = 0;
		int paramCnt = 0;
		int blankCnt = 0;
		List paramValueList = null;
		while (m.find()) {
			if (blankCnt == 0) {
				paramValueList = CollectionUtil.arrayToList(sqlToyResult.getParamsValue());
			}
			index = m.start();
			paramCnt = StringUtil.matchCnt(queryStr.substring(0, index), ARG_NAME_PATTERN);
			// 剔除参数@blank(?) 对应的参数值
			paramValueList.remove(paramCnt - blankCnt);
			blankCnt++;
		}
		if (blankCnt > 0) {
			sqlToyResult.setSql(sqlToyResult.getSql().replaceAll(BLANK_REGEX, BLANK));
			sqlToyResult.setParamsValue(paramValueList.toArray());
		}
	}

	/**
	 * @update 2021-04-29 @value放在最后处理，同时兼容replaceNull 造成@value(?) 变成@value(null)的情况
	 * @TODO 处理直接显示参数值:#[@value(:paramNamed) sql]
	 * @param sqlToyResult
	 * @param dialect
	 */
	private static void processValue(SqlToyResult sqlToyResult, String dialect) {
		if (null == sqlToyResult.getParamsValue() || sqlToyResult.getParamsValue().length == 0) {
			return;
		}
		String queryStr = sqlToyResult.getSql().toLowerCase();
		// @value(?) 或@value(null)
		Matcher m = VALUE_PATTERN.matcher(queryStr);
		int index = 0;
		int paramCnt = 0;
		int valueCnt = 0;
		List paramValueList = null;
		Object paramValue = null;
		String valueStr;
		while (m.find()) {
			if (valueCnt == 0) {
				paramValueList = CollectionUtil.arrayToList(sqlToyResult.getParamsValue());
			}
			index = m.start();
			// @value(?)
			if (m.group().contains(ARG_NAME)) {
				paramCnt = StringUtil.matchCnt(queryStr.substring(0, index), ARG_NAME_PATTERN);
				// 用参数的值直接覆盖@value(:name)
				paramValue = paramValueList.get(paramCnt - valueCnt);
				// update 2021-11-13 加强@value中是sql片段，做一次函数跨数据库适配
				valueStr = (paramValue == null) ? "null" : paramValue.toString();
				if (dialect != null && valueStr.contains("(") && valueStr.contains(")")) {
					valueStr = FunctionUtils.getDialectSql(valueStr, dialect);
				}
				sqlToyResult.setSql(sqlToyResult.getSql().replaceFirst(VALUE_REGEX, valueStr));
				// 剔除参数@value(?) 对应的参数值
				paramValueList.remove(paramCnt - valueCnt);
				valueCnt++;
			} // @value(null)
			else {
				sqlToyResult.setSql(sqlToyResult.getSql().replaceFirst(VALUE_REGEX, "null"));
			}
		}
		if (valueCnt > 0) {
			sqlToyResult.setParamsValue(paramValueList.toArray());
		}
	}

	/**
	 * @update 2020-09-22 增加sql中的循环功能
	 * @TODO 处理sql中@loop() 循环,动态组织sql进行替换，具体格式
	 *       <li>loop(:loopAry,loopContent)</li>
	 *       <li>loop(:loopArgs,loopContent,linkSign)</li>
	 *       <li>范例:#[or @loop(:beginDates,'(startTime between :beginDates[i] and
	 *       endDates[i])',or)]</li>
	 * @param queryStr
	 * @param paramsNamed
	 * @param paramsValue
	 * @return
	 */
	private static String processLoop(String queryStr, String[] paramsNamed, Object[] paramsValue) {
		if (null == paramsValue || paramsValue.length == 0) {
			return queryStr;
		}
		IgnoreKeyCaseMap<String, Object> keyValues = new IgnoreKeyCaseMap<String, Object>();
		for (int i = 0; i < paramsNamed.length; i++) {
			keyValues.put(paramsNamed[i], paramsValue[i]);
		}
		// 这里是借用业务主键生成里面的宏处理模式来解决
		return MacroUtils.replaceMacros(queryStr, keyValues, false, macros);
	}

	/**
	 * @TODO 加工处理like 部分，给参数值增加%符号
	 * @param sqlToyResult
	 */
	private static void processLike(SqlToyResult sqlToyResult) {
		if (null == sqlToyResult.getParamsValue() || sqlToyResult.getParamsValue().length == 0) {
			return;
		}
		String queryStr = sqlToyResult.getSql().toLowerCase();
		Matcher m = LIKE_PATTERN.matcher(queryStr);
		int index = 0;
		String likeParamValue;
		int paramCnt = 0;
		while (m.find()) {
			index = m.start();
			paramCnt = StringUtil.matchCnt(queryStr.substring(0, index), ARG_NAME_PATTERN);
			likeParamValue = (String) sqlToyResult.getParamsValue()[paramCnt];
			// 不存在%符号时，前后增加%
			if (null != likeParamValue && likeParamValue.indexOf("%") == -1) {
				likeParamValue = "%".concat(likeParamValue).concat("%");
				sqlToyResult.getParamsValue()[paramCnt] = likeParamValue;
			}
		}
	}

	/**
	 * @update 2020-4-14 修复参数为null时,忽视了匹配的in(?)
	 * @TODO 处理sql 语句中的in 条件，功能有2类:
	 *       <li>1、将字符串类型且条件值为逗号分隔的，将对应的sql 中的 in(?) 替换成in(具体的值)</li>
	 *       <li>2、如果对应in (?)位置上的参数数据时Object[] 数组类型，则将in (?)替换成 in (?,?),具体问号个数由
	 *       数组长度决定</li>
	 * @param sqlToyResult
	 */
	private static void processIn(SqlToyResult sqlToyResult) {
		if (null == sqlToyResult.getParamsValue() || sqlToyResult.getParamsValue().length == 0) {
			return;
		}
		int end = 0;
		String queryStr = sqlToyResult.getSql();
		Matcher m = IN_PATTERN.matcher(queryStr);
		boolean matched = m.find(end);
		if (!matched) {
			return;
		}
		int start = 0;
		Object[] paramsValue = sqlToyResult.getParamsValue();
		List paramValueList = CollectionUtil.arrayToList(paramsValue);
		// ?符合出现的次数累计
		int parameterMarkCnt = 0;
		// 通过 in (?) 扩展成 in (?,?,,,)多出来的参数量
		int incrementIndex = 0;
		StringBuilder lastSql = new StringBuilder();
		String partSql = null;
		Object[] inParamArray;
		String argValue;
		Collection inParamList;
		while (matched) {
			end = m.end();
			partSql = ARG_NAME;
			parameterMarkCnt = StringUtil.matchCnt(queryStr, ARG_REGEX, 0, end);
			if (null != paramsValue[parameterMarkCnt - 1]) {
				// 数组或集合数据类型
				if (paramsValue[parameterMarkCnt - 1].getClass().isArray()
						|| paramsValue[parameterMarkCnt - 1] instanceof Collection) {
					// update 2012-12-5 增加了对Collection数据类型的处理
					if (paramsValue[parameterMarkCnt - 1] instanceof Collection) {
						inParamList = (Collection) paramsValue[parameterMarkCnt - 1];
						inParamArray = inParamList.toArray();
					} else {
						inParamArray = CollectionUtil.convertArray(paramsValue[parameterMarkCnt - 1]);
					}
					// 循环组合成in(?,?*)
					partSql = StringUtil.loopAppendWithSign(ARG_NAME, ",", (inParamArray).length);
					paramValueList.remove(parameterMarkCnt - 1 + incrementIndex);
					paramValueList.addAll(parameterMarkCnt - 1 + incrementIndex,
							CollectionUtil.arrayToList(inParamArray));
					incrementIndex += inParamArray.length - 1;
				}
				// 逗号分隔的条件参数
				else if (paramsValue[parameterMarkCnt - 1] instanceof String) {
					argValue = (String) paramsValue[parameterMarkCnt - 1];
					// update 2012-11-15 将'xxx'(单引号) 形式的字符串纳入直接替换模式，解决因为使用combineInStr
					// 数组长度为1,构造出来的in 条件存在''(空白)符合直接用?参数导致的问题
					if (argValue.indexOf(",") != -1 || (argValue.startsWith("'") && argValue.endsWith("'"))) {
						partSql = (String) paramsValue[parameterMarkCnt - 1];
						paramValueList.remove(parameterMarkCnt - 1 + incrementIndex);
						incrementIndex--;
					}
				}
			}

			// 用新的?,?,,, 代替原本单? 号
			lastSql.append(queryStr.substring(start, m.start())).append(" in (").append(partSql).append(") ");
			start = end;
			matched = m.find(end);
		}
		// 添加尾部sql
		if (end != 0 && null != partSql) {
			lastSql.append(queryStr.substring(end));
			sqlToyResult.setSql(lastSql.toString());
			sqlToyResult.setParamsValue(paramValueList.toArray());
		}
	}

	/**
	 * @todo 处理因字符串截取后where后面出现and 或 or 的情况,通过此功能where
	 *       后面就无需写1=1,sqltoy自动补充或去除1=1(where 后面有and 或 or则会自动去除1=1)
	 * @param preSql
	 * @param markContentSql
	 * @param tailSql
	 * @return
	 */
	public static String processWhereLinkAnd(String preSql, String markContentSql, String tailSql) {
		String subStr = markContentSql.concat(tailSql);
		String tmp = subStr.trim();
		int index = StringUtil.matchIndex(preSql, WHERE_END_PATTERN);
		// 前部分sql以where 结尾，后部分sql以and 或 or 开头的拼接,剔除or 和and
		if (index >= 0) {
			// where 后面拼接的条件语句是空白,剔除where
			if (tmp.equals("")) {
				return preSql.substring(0, index + 1).concat(" ");
			}
			// and 概率更高优先判断，剔除and 或 or
			if (StringUtil.matches(tmp, AND_START_PATTERN)) {
				return preSql.concat(" ").concat(subStr.trim().substring(3)).concat(" ");
			} else if (StringUtil.matches(tmp, OR_START_PATTERN)) {
				return preSql.concat(" ").concat(subStr.trim().substring(2)).concat(" ");
			} else if (markContentSql.trim().equals("")) {
				// 排除部分场景直接剔除where 语句
				// 以where拼接")" 开头字符串,剔除where
				if (tailSql.trim().startsWith(")")) {
					return preSql.substring(0, index + 1).concat(" ").concat(tailSql).concat(" ");
				} // where 后面跟order by、group by、left join、right join、full join、having、union
				else if (StringUtil.matches(tailSql.trim().toLowerCase(), WHERE_CLOSE_PATTERN)) {
					return preSql.substring(0, index + 1).concat(" ").concat(tailSql).concat(" ");
				} else {
					return preSql.concat(" 1=1 ").concat(tailSql).concat(" ");
				}
			}
		}
		// update 2017-12-4
		// where 1=1 结尾
		index = StringUtil.matchIndex(preSql, WHERE_ONE_EQUAL_PATTERN);
		if (index >= 0) {
			// 剔除1=1 进行sql拼接
			if (StringUtil.matches(tmp, AND_START_PATTERN)) {
				// index+1 因为正则表达式是\\Wwhere,保留where前的非字母字
				return preSql.substring(0, index + 1).concat(" where ").concat(subStr.trim().substring(3)).concat(" ");
			} else if (StringUtil.matches(tmp, OR_START_PATTERN)) {
				return preSql.substring(0, index + 1).concat(" where ").concat(subStr.trim().substring(2)).concat(" ");
			} else if (tmp.startsWith(")")) {
				return preSql.substring(0, index + 1).concat(subStr).concat(" ");
			} else if (StringUtil.matches(tmp.toLowerCase(), WHERE_CLOSE_PATTERN)) {
				return preSql.substring(0, index + 1).concat(subStr).concat(" ");
			} else if (!markContentSql.trim().equals("")) {
				return preSql.substring(0, index + 1).concat(" where ").concat(subStr).concat(" ");
			}
		}
		// update 语句 set 后面连接逗号"," 情况下去除逗号
		// modify 2019-7-16
		if (StringUtil.matches(preSql, UPDATE_SET_PATTERN) && tmp.startsWith(",")) {
			return preSql.concat(" ").concat(subStr.trim().substring(1)).concat(" ");
		}
		return preSql.concat(" ").concat(subStr);
	}

	/**
	 * @todo 当sql语句中对应?号的值为null时，将该?号用字符串null替换 其意义在于jdbc 对null参数必须要指定NULL
	 *       TYPE,为了保证通用性，将null部分数据参数 直接改为 t.field = null
	 * @param sqlToyResult
	 * @param afterParamIndex
	 */
	public static void replaceNull(SqlToyResult sqlToyResult, int afterParamIndex) {
		if (null == sqlToyResult.getParamsValue()) {
			return;
		}
		String sql = sqlToyResult.getSql().concat(BLANK);
		List paramList = CollectionUtil.arrayToList(sqlToyResult.getParamsValue());
		int index = StringUtil.indexOrder(sql, ARG_NAME, afterParamIndex);
		if (index == -1) {
			return;
		}
		// 将条件值为null的替换到sql中，同时剔除该参数
		for (int i = 0; i < paramList.size(); i++) {
			if (null == paramList.get(i)) {
				sql = sql.substring(0, index).concat(" null ").concat(sql.substring(index + 1));
				paramList.remove(i);
				i--;
				index = sql.indexOf(ARG_NAME, index);
			} else {
				index = sql.indexOf(ARG_NAME, index + 1);
			}
		}
		sqlToyResult.setSql(sql);
		sqlToyResult.setParamsValue(paramList.toArray());
	}

	/**
	 * @todo 将动态的sql解析组合成一个SqlToyConfig模型，以便统一处理
	 * @param querySql
	 * @param dialect  当前的数据库类型,默认为null不指定
	 * @param sqlType
	 * @return
	 */
	public static SqlToyConfig parseSqlToyConfig(String querySql, String dialect, SqlType sqlType) {
		SqlToyConfig sqlToyConfig = new SqlToyConfig(dialect);
		// debug模式下面关闭sql打印
		sqlToyConfig.setShowSql(!StringUtil.matches(querySql, SqlToyConstants.NOT_PRINT_REGEX));
		// 是否忽视空记录
		sqlToyConfig.setIgnoreEmpty(StringUtil.matches(querySql, SqlToyConstants.IGNORE_EMPTY_REGEX));
		// 清理sql中的一些注释、以及特殊的符号
		String originalSql = SqlUtil.clearMistyChars(SqlUtil.clearMark(querySql), BLANK).concat(BLANK);
		// 对sql中的函数进行特定数据库方言转换
		originalSql = FunctionUtils.getDialectSql(originalSql, dialect);
		// 对关键词根据数据库类型进行转换,比如mysql的 ``变成mssql时变为[]
		originalSql = ReservedWordsUtil.convertSql(originalSql, DataSourceUtils.getDBType(dialect));
		// 判定是否有with查询模式
		sqlToyConfig.setHasWith(hasWith(originalSql));
		// 判定是否有union语句(先验证有union 然后再精确判断union 是否有效,在括号内的局部union 不起作用)
		if (StringUtil.matches(originalSql, SqlUtil.UNION_PATTERN)) {
			sqlToyConfig.setHasUnion(SqlUtil.hasUnion(originalSql, false));
		}
		// 只有在查询模式前提下才支持fastPage机制
		if (SqlType.search.equals(sqlType)) {
			// 判断是否有快速分页@fast 宏
			Matcher matcher = FAST_PATTERN.matcher(originalSql);
			if (matcher.find()) {
				int start = matcher.start();
				String preSql = originalSql.substring(0, start);
				String matchedFastSql = matcher.group();
				int endMarkIndex = StringUtil.getSymMarkIndex("(", ")", matchedFastSql, 0);
				// 得到分页宏处理器中的sql
				String fastSql = matchedFastSql.substring(matchedFastSql.indexOf("(") + 1, endMarkIndex);
				String tailSql = originalSql.substring(start + endMarkIndex + 1);
				// sql剔除掉快速分页宏,在分页查询时再根据presql和tailsql、fastsql自行组装，从而保障正常的非分页查询直接提取sql
				if (preSql.trim().endsWith("(") && tailSql.trim().startsWith(")")) {
					sqlToyConfig.setSql(preSql.concat(fastSql).concat(tailSql));
					sqlToyConfig.setIgnoreBracket(true);
				} else {
					sqlToyConfig.setSql(preSql.concat(" (").concat(fastSql).concat(") ").concat(tailSql));
				}
				sqlToyConfig.setFastSql(fastSql);
				sqlToyConfig.setFastPreSql(preSql);
				sqlToyConfig.setFastTailSql(tailSql);
				// 判断是否有快速分页
				sqlToyConfig.setHasFast(true);
			} else {
				sqlToyConfig.setSql(originalSql);
			}
		} else {
			sqlToyConfig.setSql(originalSql);
		}
		// 提取with fast查询语句
		processFastWith(sqlToyConfig, dialect);
		// 提取sql中的参数名称
		sqlToyConfig.setParamsName(getSqlParamsName(sqlToyConfig.getSql(dialect), true));
		return sqlToyConfig;
	}

	/**
	 * @todo 提取fastWith(@fast 涉及到的cte 查询,这里是很别致的地方，假如sql中存在with as t1 (),t2 (),t3 ()
	 *       select * from @fast(t1,t2) 做count查询时将执行: with as t1(),t2 () select
	 *       count(1) from xxx,而不会额外的多执行t3)
	 * @param sqlToyConfig
	 * @param dialect
	 */
	public static void processFastWith(SqlToyConfig sqlToyConfig, String dialect) {
		// 不存在fast 和with 不做处理
		if (!sqlToyConfig.isHasFast() || !sqlToyConfig.isHasWith()) {
			return;
		}
		// 提取with as 和fast部分的sql，用于分页或取随机记录查询记录数量提供最简sql
		SqlWithAnalysis sqlWith = new SqlWithAnalysis(sqlToyConfig.getSql(dialect));
		// 存在with xxx as () 形式的查询
		if (null != sqlWith.getWithSqlSet()) {
			String[] aliasTableAs;
			int endIndex = -1;
			int withSqlSize = sqlWith.getWithSqlSet().size();
			// 判定fast查询引用到第几个位置的with
			for (int i = withSqlSize - 1; i >= 0; i--) {
				aliasTableAs = sqlWith.getWithSqlSet().get(i);
				if (StringUtil.matches(sqlToyConfig.getFastSql(dialect), "\\W".concat(aliasTableAs[0]).concat("\\W"))) {
					endIndex = i;
					sqlToyConfig.setFastWithIndex(endIndex);
					break;
				}
			}
			// 组装with xx as () +fastsql
			if (endIndex != -1) {
				if (endIndex == withSqlSize - 1) {
					sqlToyConfig.setFastWithSql(sqlWith.getWithSql());
				} else {
					StringBuilder buffer = new StringBuilder();
					for (int i = 0; i < endIndex + 1; i++) {
						aliasTableAs = sqlWith.getWithSqlSet().get(i);
						if (i == 0) {
							buffer.append(" with ").append(aliasTableAs[3]);
						}
						if (i > 0) {
							buffer.append(",").append(aliasTableAs[3]);
						}
						// aliasTableAs 结构{aliasName,as和括号之间的字符串,as内容,with 和aliasTable之间的参数}
						buffer.append(aliasTableAs[0]).append(" as ").append(aliasTableAs[1]).append(" (")
								.append(aliasTableAs[2]).append(") ");
					}
					sqlToyConfig.setFastWithSql(buffer.toString());
				}
			}
		}
	}

}
