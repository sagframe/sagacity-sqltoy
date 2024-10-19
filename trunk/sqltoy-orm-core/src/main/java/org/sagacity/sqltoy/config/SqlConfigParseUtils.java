package org.sagacity.sqltoy.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IllegalFormatFlagsException;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.config.model.IfLogicModel;
import org.sagacity.sqltoy.config.model.KeyAndIndex;
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
import org.sagacity.sqltoy.utils.BeanUtil;
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
 *         sql] 形式,增强sql组织拼装能力}
 * @modify {Date:2016-6-7,增加sql中的全角字符替换功能,增强sql的解析能力}
 * @modify {Date:2017-12-7,优化where和and 或or的拼接处理}
 * @modify {Date:2019-02-21,增强:named 参数匹配正则表达式,参数中必须要有字母}
 * @modify {Date:2019-06-26,修复条件参数中有问号的bug，并放开条件参数名称不能是单个字母的限制}
 * @modify {Date:2019-10-11 修复@if(:name==null) 不参与逻辑判断bug }
 * @modify {Date:2020-04-14 修复三个以上 in(?) 查询，在中间的in 参数值为null时 processIn方法处理错误}
 * @modify {Date:2020-09-23 增加@loop()组织sql功能,完善极端场景下动态组织sql的能力}
 * @modify {Date:2021-04-29 调整@value(?)处理顺序到末尾，规避参数值中存在? }
 * @modify {Date:2022-04-23 兼容in (:values) 参数数组长度超过1000的场景 }
 * @modify {Date:2022-05-25 支持(id,type) in (:ids,:types) 多字段in模式,并强化参数超1000的处理 }
 * @modify {Date:2023-03-09 改进t.field=? 参数为null时转化为t.field is (not) null }
 * @modify {Date:2023-08-25 支持itemList[0].fieldName或itemList[0].item.name 形式传参 }
 * @modify {Date:2024-03-22
 *         优化getSqlParamsName、processNamedParamsQuery方法，优化了参数名称匹配，设置了匹配偏移量 }
 * @modify {Date:2024-08-10 修复(t.id,t.type) in (:list.id,:list.type)
 *         参数都为null时自动补全双括号(t.id,t.type) in ((null,null))}
 * @modify {Date:2024-10-2 强化@if功能，增加@elseif 和 @else 的支持 }
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

	// 2022-5-24支持(id,type) in ((:idValues,:typeValues))或in(:idValues,:typeValues)模式
	public final static Pattern IN_PATTERN = Pattern.compile(
			"(?i)\\s+in\\s*((\\(\\s*\\?(\\s*\\,\\s*\\?)*\\s*\\))|((\\(\\s*){2}\\?(\\s*\\,\\s*\\?)+(\\s*\\)){2}))");
	// update 2022-11-11 兼容ilike
	public final static Pattern LIKE_PATTERN = Pattern.compile("(?i)\\s+i?like\\s+\\?");

	// add 2016-5-27
	public final static String BLANK_REGEX = "(?i)\\@blank\\s*\\(\\s*\\?\\s*\\)";
	public final static String BLANK_START_REGEX = "(?i)^\\@blank\\s*\\(\\s*\\?\\s*\\)";
	public final static Pattern BLANK_PATTERN = Pattern.compile(BLANK_REGEX);
	public final static Pattern BLANK_START_PATTERN = Pattern.compile(BLANK_START_REGEX);
	public final static String VALUE_REGEX = "(?i)\\@value\\s*\\(\\s*(\\?|null)\\s*\\)";
	public final static Pattern VALUE_PATTERN = Pattern.compile(VALUE_REGEX);
	public final static Pattern IF_PATTERN = Pattern.compile("(?i)\\@if\\s*\\(");
	public final static Pattern START_IF_PATTERN = Pattern.compile("(?i)^\\s*\\@if\\s*\\(");
	public final static Pattern ELSEIF_PATTERN = Pattern.compile("(?i)\\@elseif\\s*\\(");
	public final static Pattern START_ELSEIF_PATTERN = Pattern.compile("(?i)^\\s*\\@elseif\\s*\\(");
	public final static Pattern ELSE_PATTERN = Pattern.compile("(?i)\\@else(\\s+|\\s*\\(\\s*\\))");
	public final static Pattern START_ELSE_PATTERN = Pattern.compile("(?i)^\\s*\\@else(\\s+|\\s*\\(\\s*\\))");
	public final static Pattern IF_ALL_PATTERN = Pattern
			.compile("(?i)\\@((if|elseif)\\s*\\(|else(\\s+|\\s*\\(\\s*\\)))");

	public final static String BLANK = " ";
	// 匹配时已经转小写
	public final static Pattern IS_END_PATTERN = Pattern.compile("\\s+is\\s+(not)?\\s+$");
	public final static String ARG_NAME = "?";
	public final static String ARG_REGEX = "\\?";
	public final static String ARG_DBL_NAME = "??";
	public final static String ARG_DBL_REGEX = "\\?{2}";
	public final static Pattern ARG_NAME_PATTERN = Pattern.compile(ARG_REGEX);

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
			.compile("^((order|group)\\s+by|(inner|left|right|full)\\s+join|having|union|limit)\\W");

	public final static String DBL_QUESTMARK = "#sqltoy_dblqsmark_placeholder#";
	// field=? 判断等于号
	public final static Pattern EQUAL_PATTERN = Pattern.compile("[^\\>\\<\\!\\:]\\=\\s*$");
	// 常规数据库:update table set t.xxx=? ,t.xxx1=?
	// clickhouse:alter table update t.xxx=?
	public final static Pattern UPDATE_EQUAL_PATTERN = Pattern.compile(
			"(?i)\\s*(set\\s+|,\\s*|update\\s+)([a-zA-Z_0-9\u4e00-\u9fa5]+\\.)?('|\"|\\`|\\[)?[a-zA-Z_0-9\u4e00-\u9fa5]+('|\"|\\`|\\])?\\s*=\\s*$");
	// sql不等于
	public final static Pattern NOT_EQUAL_PATTERN = Pattern.compile("(\\!\\=|\\<\\>|\\^\\=)\\s*$");
	public final static Pattern WHERE_PATTERN = Pattern.compile("(?i)\\Wwhere\\W");
	// (t.id,t.type) in (:list.id,:list.type) 提取 t.id,t.type 用途
	public final static String MORE_IN_FIELDS_REGEX = "[\\s\\(\\)\\}\\{\\]\\[]";
	// (t.id,t.type) in (:list.id,:list.type) 语句判断是否是not in
	public final static String NOT_IN_REGEX = "\\s*not$";
	// 利用宏模式来完成@loop循环处理
	private static Map<String, AbstractMacro> macros = new HashMap<String, AbstractMacro>();

	static {
		// 默认跳过blank loopValue[i]==null或为"" 时的忽略当前i行的循环内容
		macros.put("@loop", new SqlLoop(true));
		// 全量循环
		macros.put("@loop-full", new SqlLoop(false));
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
		return StringUtil.matches(BLANK + sql, SqlToyConstants.withPattern);
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

	public static SqlToyResult processSql(String queryStr, Map<String, Object> argMap) {
		return processSql(queryStr, argMap, null);
	}

	public static SqlToyResult processSql(String queryStr, Map<String, Object> argMap, String dialect) {
		// 转成key大小写不敏感map
		IgnoreKeyCaseMap ignoreCaseMap = new IgnoreKeyCaseMap((argMap == null) ? new HashMap() : argMap);
		String[] paramsNamed = getSqlParamsName(queryStr, true);
		Object[] paramsArg = null;
		if (paramsNamed != null) {
			paramsArg = new Object[paramsNamed.length];
			for (int i = 0; i < paramsNamed.length; i++) {
				paramsArg[i] = ignoreCaseMap.get(paramsNamed[i]);
			}
		}
		return processSql(queryStr, paramsNamed, paramsArg, dialect);
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
			// update 2022-7-18
			int paramCnt = StringUtil.matchCnt(sql, ARG_NAME_PATTERN, 0);
			// 只有单个? 参数、传递的参数长度大于1、且是 in (?),则将参数转成长度为1的二维数组new Object[]{Object[]} 模式
			if (paramCnt == 1 && paramsValue.length > 1 && StringUtil.matches(sql, IN_PATTERN)) {
				paramsValue = new Object[] { paramsValue };
			}
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
	 * @TODO 用特殊字符替换掉sql中的??特殊转义符号，避免对?传参的干扰(最后再替换回来)
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
			KeyAndIndex keyAndIndex;
			String nameLow;
			for (String name : sqlParamsName) {
				nameLow = name.toLowerCase();
				result[i] = nameValueMap.get(nameLow);
				// 数组
				if (result[i] == null) {
					keyAndIndex = BeanUtil.getKeyAndIndex(nameLow);
					if (keyAndIndex != null) {
						result[i] = BeanUtil.getArrayIndexValue(nameValueMap.get(keyAndIndex.getKey()),
								keyAndIndex.getIndex());
					}
				}
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
		// update 2024-03-22 增加了偏移量
		int start = 0;
		String group;
		while (m.find(start)) {
			group = m.group();
			// 剔除\\W\\: 两位字符
			paramsName.add(group.substring(2).trim());
			lastSql.append(queryStr.substring(start, m.start() + 1)).append(ARG_NAME);
			// 参数通过\\W\\:name\s?模式匹配，判断是否空白结尾
			if (StringUtil.matches(group, SqlToyConstants.BLANK_END)) {
				start = m.end() - 1;
			} else {
				start = m.end();
			}
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
	 * @param distinct 是否去除重复
	 * @return
	 */
	public static String[] getSqlParamsName(String queryStr, boolean distinct) {
		Matcher matcher = SqlToyConstants.SQL_NAMED_PATTERN.matcher(queryStr);
		// 用来替换:paramName
		List<String> paramsNameList = new ArrayList<String>();
		HashSet<String> distinctSet = new HashSet<String>();
		String paramName;
		// update 2024-03-22 增加了偏移量
		int start = 0;
		while (matcher.find(start)) {
			// 剔除\\W\\:两位字符
			paramName = matcher.group().substring(2).trim();
			// 去除重复
			if (distinct) {
				if (!distinctSet.contains(paramName.toLowerCase())) {
					paramsNameList.add(paramName);
					distinctSet.add(paramName.toLowerCase());
				}
			} else {
				paramsNameList.add(paramName);
			}
			// 设置偏移量1
			start = matcher.end() - 1;
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
		int paramCnt, preParamCnt, beginMarkIndex, endMarkIndex;
		String preSql, markContentSql, tailSql;
		List paramValuesList = CollectionUtil.arrayToList(sqlToyResult.getParamsValue());
		int ifStart;
		int ifLogicSignStart;
		// sql内容体是否以and 或 or 结尾
		boolean isEndWithAndOr = false;
		// 0 无if、else等1:单个if；>1：if+else等
		int ifLogicCnt = 0;
		boolean isDynamicSql;
		while (pseudoMarkStart != -1) {
			ifLogicCnt = 0;
			isEndWithAndOr = false;
			// 始终从最后一个#[]进行处理
			beginMarkIndex = queryStr.lastIndexOf(SQL_PSEUDO_START_MARK);
			// update 2021-01-17 按照"["和"]" 找对称位置，兼容sql中存在[]场景
			endMarkIndex = StringUtil.getSymMarkIndex(SQL_PSEUDO_SYM_START_MARK, SQL_PSEUDO_END_MARK, queryStr,
					beginMarkIndex);
			if (endMarkIndex == -1) {
				throw new IllegalFormatFlagsException("sql语句中缺乏\"#[\" 相对称的\"]\"符号,请检查sql格式!");
			}
			// 最后一个#[前的sql
			preSql = queryStr.substring(0, beginMarkIndex).concat(BLANK);
			// 最后#[]中的查询语句,加空白减少substr(index+1)可能引起的错误
			markContentSql = BLANK
					.concat(queryStr.substring(beginMarkIndex + SQL_PSEUDO_START_MARK_LENGTH, endMarkIndex))
					.concat(BLANK);
			ifLogicSignStart = StringUtil.matchIndex(markContentSql, IF_ALL_PATTERN);
			ifStart = StringUtil.matchIndex(markContentSql, IF_PATTERN);
			// 单一的@if 逻辑
			if (ifStart == ifLogicSignStart && ifStart > 0) {
				ifLogicCnt = 1;
			}
			// 属于@elseif 或@else()
			else if (ifStart == -1 && ifLogicSignStart > 0) {
				// 逆向找到@else 或@elseif 对称的@if位置
				int symIfIndex = getStartIfIndex(preSql, SQL_PSEUDO_SYM_START_MARK, SQL_PSEUDO_END_MARK);
				if (symIfIndex == -1) {
					throw new IllegalFormatFlagsException("sql编写模式存在错误:@elseif(?==xx) @else 条件判断必须要有对应的@if()形成对称格式!");
				}
				beginMarkIndex = queryStr.substring(0, symIfIndex).lastIndexOf(SQL_PSEUDO_START_MARK);
				preSql = queryStr.substring(0, beginMarkIndex).concat(BLANK);
				markContentSql = BLANK
						.concat(queryStr.substring(beginMarkIndex + SQL_PSEUDO_START_MARK_LENGTH, endMarkIndex))
						.concat(BLANK);
				ifLogicCnt = StringUtil.matchCnt(markContentSql, IF_ALL_PATTERN);
			}
			tailSql = queryStr.substring(endMarkIndex + SQL_PSEUDO_END_MARK_LENGTH);
			// 在#[前的参数个数
			preParamCnt = StringUtil.matchCnt(preSql, ARG_NAME_PATTERN, 0);
			markContentSql = processIfLogic(markContentSql, SQL_PSEUDO_START_MARK, SQL_PSEUDO_END_MARK,
					ARG_NAME_PATTERN, paramValuesList, preSql, preParamCnt, ifLogicCnt, 0, 0);
			// 没有if逻辑,#[sqlPart] 中间的sqlPart中无参数,整体剔除，有参数则判断参数是否有为null的决定是否剔除sqlPart
			if (ifLogicCnt == 0) {
				isEndWithAndOr = StringUtil.matches(markContentSql, SqlToyConstants.AND_OR_END);
				paramCnt = StringUtil.matchCnt(markContentSql, ARG_NAME_PATTERN, 0);
				markContentSql = (paramCnt == 0) ? BLANK
						: processMarkContent(markContentSql, ARG_NAME_PATTERN, paramValuesList, preParamCnt, paramCnt,
								true);
			} else {
				isDynamicSql = isDynamicSql(markContentSql, SQL_PSEUDO_START_MARK, SQL_PSEUDO_END_MARK);
				// #[sqlPart] 中sqlPart里面没有#[]
				if (!isDynamicSql) {
					isEndWithAndOr = StringUtil.matches(markContentSql, SqlToyConstants.AND_OR_END);
					paramCnt = StringUtil.matchCnt(markContentSql, ARG_NAME_PATTERN, 0);
					// 判断sqlPart中参数是否为null，决定是否剔除sqlPart
					markContentSql = processMarkContent(markContentSql, ARG_NAME_PATTERN, paramValuesList, preParamCnt,
							paramCnt, true);
				} else {
					// sqlPart中存在#[],剔除掉所有#[],再判断剩余sql中是否有动态参数
					String clearSymMarkStr = StringUtil.clearSymMarkContent(markContentSql, SQL_PSEUDO_START_MARK,
							SQL_PSEUDO_END_MARK);
					// 剩余sql中的动态参数个数
					int clearAfterArgCnt = StringUtil.matchCnt(clearSymMarkStr, ARG_NAME_PATTERN, 0);
					// 动态参数大于0,类似 and status=:status #[xxx] 有:status参数，则变成#[and status=:status
					// #[xxx]]继续利用sqltoy的判空剔除规则
					if (clearAfterArgCnt > 0) {
						markContentSql = SQL_PSEUDO_START_MARK.concat(markContentSql).concat(SQL_PSEUDO_END_MARK);
					} else {
						isEndWithAndOr = StringUtil.matches(markContentSql, SqlToyConstants.AND_OR_END);
					}
				}
			}
			queryStr = processWhereLinkAnd(preSql, markContentSql, isEndWithAndOr, tailSql);
			pseudoMarkStart = queryStr.indexOf(SQL_PSEUDO_START_MARK);
		}
		sqlToyResult.setSql(queryStr);
		sqlToyResult.setParamsValue(paramValuesList.toArray());
	}

	/**
	 * @todo 判断sql中是否存在#[] 表示sql是动态语句
	 * @param sql
	 * @param startMark
	 * @param endMark
	 * @return
	 */
	public static boolean isDynamicSql(String sql, String startMark, String endMark) {
		int startMarkIndex = sql.indexOf(startMark);
		if (startMarkIndex >= 0 && sql.indexOf(endMark, startMarkIndex) > 0) {
			return true;
		}
		return false;
	}
	
	/**
	 * @todo 找到@elseif 或@else 对应的@if位置
	 * @param preSql
	 * @param startMark
	 * @param endMark
	 * @return
	 */
	public static int getStartIfIndex(String preSql, String startMark, String endMark) {
		String sql = preSql;
		int endIndex;
		int startIndex = -1;
		int ifIndex = -1;
		while (true) {
			endIndex = sql.lastIndexOf(endMark);
			if (endIndex == -1) {
				break;
			}
			// 通过] 逆向找#[对应位置
			startIndex = StringUtil.getSymMarkReverseIndex(startMark, endMark, sql, endIndex + endMark.length());
			if (startIndex == -1) {
				break;
			}
			// 判断是否是@if
			ifIndex = StringUtil.matchIndex(sql.substring(startIndex + startMark.length()), START_IF_PATTERN);
			if (ifIndex != -1) {
				return ifIndex + startIndex + startMark.length();
			} else {
				sql = sql.substring(0, startIndex);
			}
		}
		if (ifIndex == -1) {
			throw new IllegalFormatFlagsException("sql语句@elseif、@else 缺少对应的@if");
		}
		return ifIndex;
	}

	/**
	 * @todo 处理#[@if() sqlPart] if成立后sqlPart部分判断参数是否为null
	 * @param markContentSql
	 * @param namedPattern
	 * @param paramValuesList
	 * @param preParamCnt
	 * @param paramCnt
	 * @param sqlMode
	 * @return
	 */
	public static String processMarkContent(String markContentSql, Pattern namedPattern, List paramValuesList,
			int preParamCnt, int paramCnt, boolean sqlMode) {
		String resultStr = markContentSql;
		int beginIndex = 0;
		int endIndex = 0;
		Object paramValue;
		// sql中是否存在is
		boolean sqlhasIs;
		String sqlPart;
		int offset = sqlMode ? 1 : 2;
		// 按顺序处理#[]中sql的参数
		for (int i = preParamCnt; i < preParamCnt + paramCnt; i++) {
			paramValue = paramValuesList.get(i);
			beginIndex = endIndex;
			// fromIndex 往后移动一位，避免where id=? 始终找到同一个位置
			endIndex = StringUtil.matchIndex(markContentSql, namedPattern, beginIndex + offset)[0];
			sqlhasIs = false;
			if (sqlMode) {
				// 截取and t.field=? 中and t.field= 不含?的sql部分
				sqlPart = markContentSql.substring(beginIndex + offset, endIndex);
				sqlhasIs = StringUtil.matches(BLANK + sqlPart.toLowerCase() + BLANK, IS_END_PATTERN);
			}
			// 1、参数值为null且非is 条件sql语句
			// 2、is 条件sql语句值非null、true、false 剔除#[]部分内容，同时将参数从数组中剔除
			if ((null == paramValue && !sqlhasIs)
					|| (null != paramValue && paramValue.getClass().isArray()
							&& CollectionUtil.convertArray(paramValue).length == 0)
					|| (null != paramValue && (paramValue instanceof Collection) && ((Collection) paramValue).isEmpty())
					|| (sqlhasIs && null != paramValue && !(paramValue instanceof java.lang.Boolean))) {
				// sql中剔除最后部分的#[]内容
				resultStr = BLANK;
				for (int k = paramCnt; k > 0; k--) {
					paramValuesList.remove(k + preParamCnt - 1);
				}
				break;
			}
		}
		return resultStr;
	}

	/**
	 * @TODO 将@blank(:paramName) 设置为" "空白输出,同时在条件数组中剔除:paramName对应位置的条件值
	 * @param sqlToyResult
	 */
	private static void processBlank(SqlToyResult sqlToyResult) {
		if (null == sqlToyResult.getParamsValue() || sqlToyResult.getParamsValue().length == 0) {
			return;
		}
		String queryStr = sqlToyResult.getSql();
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
			paramCnt = StringUtil.matchCnt(queryStr.substring(0, index), ARG_NAME_PATTERN, 0);
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
		String queryStr = sqlToyResult.getSql();
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
				paramCnt = StringUtil.matchCnt(queryStr.substring(0, index), ARG_NAME_PATTERN, 0);
				// 用参数的值直接覆盖@value(:name)
				paramValue = paramValueList.get(paramCnt - valueCnt);
				// update 2024-03-03 强化对数组、枚举、日期等类型的输出
				valueStr = SqlUtil.toSqlString(paramValue, false);
				// update 2021-11-13 加强@value对应值中存在函数，进行跨数据库适配
				if (dialect != null && valueStr.contains("(") && valueStr.contains(")")) {
					valueStr = FunctionUtils.getDialectSql(valueStr, dialect);
				}
				sqlToyResult
						.setSql(sqlToyResult.getSql().replaceFirst(VALUE_REGEX, Matcher.quoteReplacement(valueStr)));
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
		return MacroUtils.replaceMacros(queryStr, keyValues, null, false, macros, null);
	}

	/**
	 * @TODO 加工处理like 部分，给参数值增加%符号
	 * @param sqlToyResult
	 */
	private static void processLike(SqlToyResult sqlToyResult) {
		if (null == sqlToyResult.getParamsValue() || sqlToyResult.getParamsValue().length == 0) {
			return;
		}
		String queryStr = sqlToyResult.getSql();
		Matcher m = LIKE_PATTERN.matcher(queryStr);
		int index = 0;
		int paramCnt = 0;
		String likeValStr;
		while (m.find()) {
			index = m.start();
			paramCnt = StringUtil.matchCnt(queryStr.substring(0, index), ARG_NAME_PATTERN, 0);
			likeValStr = (sqlToyResult.getParamsValue()[paramCnt] == null) ? null
					: sqlToyResult.getParamsValue()[paramCnt].toString();
			// 不存在%符号时，前后增加%
			if (null != likeValStr && likeValStr.indexOf("%") == -1) {
				sqlToyResult.getParamsValue()[paramCnt] = "%".concat(likeValStr).concat("%");
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
		Object[] inParamArray = null;
		String argValue;
		Collection inParamList;
		boolean overSize = false;
		int paramCnt = 0;
		while (matched) {
			end = m.end();
			partSql = ARG_NAME;
			parameterMarkCnt = StringUtil.matchCnt(queryStr, ARG_REGEX, 0, end);
			// (t.field1,t.feild2) in (:param1,:param2) 或 (t.field1,t.feild2) in
			// ((:param1,:param2))模式
			paramCnt = StringUtil.matchCnt(m.group(), ARG_REGEX);
			overSize = false;
			if (paramCnt > 1) {
				int nullCnt = 0;
				int startIndex = parameterMarkCnt - paramCnt;
				// 普通非数组类型数量
				int commTypeCnt = 0;
				for (int i = 0; i < paramCnt; i++) {
					if (paramsValue[startIndex + i] == null) {
						nullCnt++;
						commTypeCnt++;
					} else if (!paramsValue[startIndex + i].getClass().isArray()
							&& !(paramsValue[startIndex + i] instanceof Collection)) {
						commTypeCnt++;
					}
				}
				// 直接组织好的(?,?,?) 语句
				if (commTypeCnt == paramCnt) {
					partSql = StringUtil.loopAppendWithSign(ARG_NAME, ",", paramCnt);
					// 参数非数组(全部为null也是一种特例场景) in 后面是(()) 形式，要额外增加()，后面in ("+partSql+") 重新构成双括号
					// 是(t1.a,t1.b) in (?,?) 形式,也需要补充一层括号()
					if (StringUtil.matches(m.group().trim(), "(\\(\\s*){2}")
							|| isMoreFieldIn(queryStr.substring(start, m.start()))) {
						partSql = "(".concat(partSql).concat(")");
					}
				} else {
					if ((nullCnt > 0 && nullCnt < paramCnt) || commTypeCnt > 0) {
						throw new IllegalArgumentException(
								"多字段in的:(field1,field2) in (:field1Set,:field2Set) 对应参数值非法，要求是数组类型且不能为null!");
					}
					String loopArgs = "(".concat(StringUtil.loopAppendWithSign(ARG_NAME, ",", paramCnt)).concat(")");
					List<Object[]> inParamsList = new ArrayList<Object[]>();
					for (int i = 0; i < paramCnt; i++) {
						if (paramsValue[startIndex + i] instanceof Collection) {
							inParamList = (Collection) paramsValue[startIndex + i];
							inParamArray = inParamList.toArray();
						} else {
							inParamArray = CollectionUtil.convertArray(paramsValue[startIndex + i]);
						}
						inParamsList.add(inParamArray);
						if (i > 0 && (inParamArray.length != inParamsList.get(i - 1).length)) {
							throw new IllegalArgumentException(
									"多字段in的:(field1,field2) in (:field1Set,:field2Set) 数组参数的长度:" + inParamArray.length
											+ "<>" + inParamsList.get(i - 1).length + "!");
						}
					}
					// 超过1000长度，进行(name in (?,?) or name in (?,?)) 分割
					if (inParamArray.length > 1000) {
						overSize = true;
						partSql = wrapOverSizeInSql(queryStr.substring(start, m.start()), loopArgs,
								inParamArray.length);
						lastSql.append(" ").append(partSql).append(" ");
					} else if (inParamArray.length == 0) {
						partSql = "(".concat(StringUtil.loopAppendWithSign("null", ",", paramCnt)).concat(")");
					} else {
						// 循环组合成in(?,?*)
						partSql = StringUtil.loopAppendWithSign(loopArgs, ",", inParamArray.length);
					}
					for (int i = 0; i < paramCnt; i++) {
						paramValueList.remove(startIndex + incrementIndex);
					}
					int addIndex = startIndex + incrementIndex;
					for (int i = 0; i < inParamArray.length; i++) {
						for (int j = 0; j < paramCnt; j++) {
							paramValueList.add(addIndex, inParamsList.get(j)[i]);
							addIndex++;
						}
					}
					incrementIndex += inParamArray.length * paramCnt - paramCnt;
				}
			} // 单个?，且值为null不在processIn中处理，最终replaceNull统一处理
			else if (null != paramsValue[parameterMarkCnt - 1]) {
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
					// 超过1000长度，进行(name in (?,?) or name in (?,?)) 分割
					if (inParamArray.length > 1000) {
						overSize = true;
						partSql = wrapOverSizeInSql(queryStr.substring(start, m.start()), ARG_NAME,
								inParamArray.length);
						lastSql.append(BLANK).append(partSql).append(BLANK);
					} else if (inParamArray.length == 0) {
						partSql = "null";
					} else {
						// 循环组合成in(?,?*)
						partSql = StringUtil.loopAppendWithSign(ARG_NAME, ",", inParamArray.length);
					}
					paramValueList.remove(parameterMarkCnt - 1 + incrementIndex);
					paramValueList.addAll(parameterMarkCnt - 1 + incrementIndex,
							CollectionUtil.arrayToList(inParamArray));
					incrementIndex += inParamArray.length - 1;
				}
				// 逗号分隔的条件参数
				else if (paramsValue[parameterMarkCnt - 1] instanceof String) {
					argValue = (String) paramsValue[parameterMarkCnt - 1];
					// update 2023-11-21 增强field in (?) 参数值是单个字符串的处理(针对组装参数拼接场景)，避免sql注入
					// 1、用逗号进行切割，校验是'xxx','xxxx1'或"a","b" 或 122,233 三种形式
					// 2、无逗号分割：'abc'或"abc"或123 三种形式
					if (SqlUtil.validateInArg(argValue)) {
						partSql = argValue;
						paramValueList.remove(parameterMarkCnt - 1 + incrementIndex);
						incrementIndex--;
					}
				}
			}

			// 用新的?,?,,, 代替原本单? 号
			if (!overSize) {
				lastSql.append(queryStr.substring(start, m.start())).append(" in (").append(partSql).append(") ");
			}
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
	 * @todo 构造条件参数数组长度超过1000情况下的in 语句
	 * @param sqlPart
	 * @param loopArgs
	 * @param paramsSize
	 * @return
	 */
	private static String wrapOverSizeInSql(String sqlPart, String loopArgs, int paramsSize) {
		String sql = sqlPart.trim();
		// 判断是否 t.field not in (?) 模式
		int notIndex = StringUtil.matchIndex(sql.toLowerCase(), NOT_IN_REGEX);
		boolean isNotIn = false;
		if (notIndex > 0) {
			isNotIn = true;
			// 剔除掉not和not前面的空白
			sql = sql.substring(0, notIndex);
		}
		sql = " ".concat(sql);
		int paramIndex;
		String paramName;
		// MORE_IN_FIELDS_REGEX = "[\\s\\(\\)\\}\\{\\]\\[]";
		// in 前面的参数可能是(t.field||'') 或concat(t.field1,t.field2),确保精准的切取到参数
		if (sql.trim().endsWith(")")) {
			String reverseSql = new StringBuilder(sql).reverse().toString();
			// "concat(a,b)" 反转后 ")b,a(tacnoc" 找到对称的(符号位置
			int symIndex = StringUtil.getSymMarkIndex(")", "(", reverseSql, 0);
			int start = sql.length() - symIndex - 1;
			paramIndex = StringUtil.matchLastIndex(sql.substring(0, start), MORE_IN_FIELDS_REGEX) + 1;
			paramName = sql.substring(paramIndex);
		} else {
			// 提取出sql in 前面的实际字段名称(空白、括号等),如: and t.order_id 结果:t.order_id
			paramIndex = StringUtil.matchLastIndex(sql, MORE_IN_FIELDS_REGEX) + 1;
			paramName = sql.substring(paramIndex);
		}
		sql = sql.substring(0, paramIndex);
		StringBuilder result = new StringBuilder(sql);
		result.append(" (");
		int index = 0;
		// 组织条件，not in 为 t.field not in () and t.field not in ()
		// in 为t.field in () or t.field in ()
		while (paramsSize > 0) {
			result.append(BLANK);
			if (index > 0) {
				if (isNotIn) {
					result.append(" and ");
				} else {
					result.append(" or ");
				}
			}
			result.append(paramName);
			if (isNotIn) {
				result.append(" not in (");
			} else {
				result.append(" in (");
			}
			result.append(StringUtil.loopAppendWithSign(loopArgs, ",", (paramsSize > 1000) ? 1000 : paramsSize));
			result.append(") ");
			paramsSize = paramsSize - 1000;
			index++;
		}
		result.append(") ");
		return result.toString();
	}

	/**
	 * add 2024-08-10
	 * 
	 * @TODO 判断sql是否是 (t.id,t.name) in (?,?) 多字段in场景
	 * @param sqlPart
	 * @return
	 */
	private static boolean isMoreFieldIn(String sqlPart) {
		String sql = sqlPart.trim();
		// 判断是否 t.field not in (?) 模式
		int notIndex = StringUtil.matchIndex(sql.toLowerCase(), NOT_IN_REGEX);
		if (notIndex > 0) {
			// 剔除掉not和not前面的空白
			sql = sql.substring(0, notIndex);
		}
		sql = " ".concat(sql);
		int paramIndex;
		String paramName;
		// MORE_IN_FIELDS_REGEX = "[\\s\\(\\)\\}\\{\\]\\[]";
		// in 前面的参数可能是(t.field||'') 或concat(t.field1,t.field2),确保精准的切取到参数
		if (sql.trim().endsWith(")")) {
			String reverseSql = new StringBuilder(sql).reverse().toString();
			// "concat(a,b)" 反转后 ")b,a(tacnoc" 找到对称的(符号位置
			int symIndex = StringUtil.getSymMarkIndex(")", "(", reverseSql, 0);
			int start = sql.length() - symIndex - 1;
			paramIndex = StringUtil.matchLastIndex(sql.substring(0, start), MORE_IN_FIELDS_REGEX) + 1;
			paramName = sql.substring(paramIndex);
		} else {
			// 提取出sql in 前面的实际字段名称(空白、括号等),如: and t.order_id 结果:t.order_id
			paramIndex = StringUtil.matchLastIndex(sql, MORE_IN_FIELDS_REGEX) + 1;
			paramName = sql.substring(paramIndex);
		}
		if (paramName.contains(",")) {
			return true;
		}
		return false;
	}

	/**
	 * @todo 处理因字符串截取后where后面出现and 或 or 的情况,通过此功能where
	 *       后面就无需写1=1,sqltoy自动补充或去除1=1(where 后面有and 或 or则会自动去除1=1)
	 * @param preSql
	 * @param markContentSql
	 * @param isEndWithAndOr
	 * @param tailSql
	 * @return
	 */
	public static String processWhereLinkAnd(String preSql, String markContentSql, boolean isEndWithAndOr,
			String tailSql) {
		String subStr = markContentSql.concat(tailSql);
		String tmp = subStr.trim();
		int index = StringUtil.matchIndex(preSql, WHERE_END_PATTERN);
		// 前部分sql以where 结尾，后部分sql以and 或 or 开头的拼接,剔除or 和and
		if (index >= 0) {
			// where 后面拼接的条件语句是空白,剔除where
			if ("".equals(tmp)) {
				return preSql.substring(0, index + 1).concat(" ");
			}
			// and 概率更高优先判断，剔除and 或 or
			if (StringUtil.matches(tmp, AND_START_PATTERN)) {
				return preSql.concat(" ").concat(subStr.trim().substring(3)).concat(" ");
			} else if (StringUtil.matches(tmp, OR_START_PATTERN)) {
				return preSql.concat(" ").concat(subStr.trim().substring(2)).concat(" ");
			} else if ("".equals(markContentSql.trim())) {
				String tailTrim = tailSql.trim();
				// 排除部分场景直接剔除where 语句
				// 以where拼接")" 开头字符串,剔除where
				if (tailTrim.startsWith(")")) {
					return preSql.substring(0, index + 1).concat(" ").concat(tailSql).concat(" ");
				} // where 后面跟order by、group by、left join、right join、full join、having、union、limit
				else if (StringUtil.matches(tailTrim.toLowerCase(), WHERE_CLOSE_PATTERN)) {
					// 删除掉where
					return preSql.substring(0, index + 1).concat(" ").concat(tailSql).concat(" ");
				} // where 后面非关键词增加1=1
				else {
					// 注意这里1=1 要保留，where #[被剔除内容] limit 10，就会出现where limit
					// where #[field1=:val1 and] field2=:val2, and在前面#[]中形式，去除#[field1=:val1 and]
					if (isEndWithAndOr) {
						return preSql.concat(" ").concat(tailSql).concat(" ");
					} else {
						return preSql.concat(" 1=1 ").concat(tailSql).concat(" ");
					}
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
			} else if (!"".equals(markContentSql.trim())) {
				// @blank开头，保持1=1
				if (StringUtil.matches(tmp, BLANK_START_PATTERN)) {
					return preSql.concat(" ").concat(subStr).concat(" ");
				} else {
					return preSql.substring(0, index + 1).concat(" where ").concat(subStr).concat(" ");
				}
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
	 *       TYPE,为了保证通用性，将null部分数据参数 直接改为 t.field is (not) null
	 * @param sqlToyResult
	 * @param afterParamIndex
	 */
	public static void replaceNull(SqlToyResult sqlToyResult, int afterParamIndex) {
		if (null == sqlToyResult.getParamsValue()) {
			return;
		}
		String sql = sqlToyResult.getSql().concat(BLANK);
		int index = StringUtil.indexOrder(sql, ARG_NAME, afterParamIndex);
		if (index == -1) {
			return;
		}
		List paramList = CollectionUtil.arrayToList(sqlToyResult.getParamsValue());
		String preSql;
		String tailSql;
		String sqlPart;
		int compareIndex;
		// 将条件值为null的替换到sql中，同时剔除该参数
		for (int i = 0; i < paramList.size(); i++) {
			if (null == paramList.get(i)) {
				preSql = sql.substring(0, index);
				tailSql = sql.substring(index + 1);
				// 先判断不等于
				compareIndex = StringUtil.matchIndex(preSql, NOT_EQUAL_PATTERN);
				sqlPart = " is not ";
				// 判断等于
				if (compareIndex == -1) {
					compareIndex = StringUtil.matchIndex(preSql, EQUAL_PATTERN);
					if (compareIndex != -1) {
						// update field=?或sql中没有where
						if (StringUtil.matches(preSql, UPDATE_EQUAL_PATTERN)
								|| !StringUtil.matches(preSql.concat(BLANK), WHERE_PATTERN)) {
							compareIndex = -1;
						}
					}
					// [^><!]= 非某个字符开头，要往后移动一位
					if (compareIndex != -1) {
						compareIndex = compareIndex + 1;
					}
					sqlPart = " is ";
				}
				// 存在where条件参数为=或<> 改成is (not) null
				if (compareIndex != -1) {
					preSql = preSql.substring(0, compareIndex).concat(sqlPart);
				}
				sql = preSql.concat("null").concat(tailSql);
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
		if (StringUtil.matches(querySql, SqlToyConstants.NOT_PRINT_REGEX)) {
			sqlToyConfig.setShowSql(false);
		} else if (StringUtil.matches(querySql, SqlToyConstants.DO_PRINT_REGEX)) {
			sqlToyConfig.setShowSql(true);
		}
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
		sqlToyConfig.setHasUnion(SqlUtil.hasUnion(originalSql, false));
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
		sqlToyConfig.setSqlType(sqlType);
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
				if (StringUtil.matches(sqlToyConfig.getFastSql(dialect).concat(BLANK),
						"\\W".concat(aliasTableAs[0]).concat("\\W"))) {
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
							// update 2022-12-09 前后增加空格，避免mysql新驱动的缺陷
							buffer.append(" , ").append(aliasTableAs[3]);
						}
						buffer.append(" ");
						// aliasTableAs 结构{aliasName,as和括号之间的字符串,as内容,with 和aliasTable之间的参数}
						buffer.append(aliasTableAs[0]).append(aliasTableAs[4]).append(" as ").append(aliasTableAs[1])
								.append(" ( ").append(aliasTableAs[2]).append(" ) ");
					}
					sqlToyConfig.setFastWithSql(buffer.toString());
				}
			}
		}
	}

	/**
	 * @todo 处理 "@if() ] #[@elseif() ] #[@else() "中间内容体
	 * @param contentSql
	 * @param startMark
	 * @param endMark
	 * @param namedPattern
	 * @param paramsList
	 * @param preSql
	 * @param preParamsCnt
	 * @param ifLogicCnt
	 * @param offset
	 * @param sqlParamType 0:?常规sql;1:elastich sql;2:mongodb
	 * @return
	 */
	public static String processIfLogic(String contentSql, String startMark, String endMark, Pattern namedPattern,
			List paramsList, String preSql, int preParamsCnt, int ifLogicCnt, int offset, int sqlParamType) {
		if (ifLogicCnt == 0) {
			return contentSql;
		}
		List<IfLogicModel> ifLogicModelAry = new ArrayList<>();
		// @if() ] #[@elseif() ] #[@else()构造#[@if() ] #[@elseif() ] #[@else()
		// ],统一格式再处理
		String fullIfSql = startMark.concat(contentSql).concat(endMark);
		int start;
		int end;
		int ifEnd;
		int ifStart;
		// #[@if() and xxx] 剔除@if() 之外的and xxx部分sql
		String sqlPart;
		int preParamsAccount = preParamsCnt;
		boolean logicResult = false;
		int startMarkLenght = startMark.length();
		int endMarkLength = endMark.length();
		// 1:@if();2:@elseif();3:@else
		int logicType = 0;
		String realStartMark = startMark.equals(SQL_PSEUDO_START_MARK) ? SQL_PSEUDO_SYM_START_MARK : startMark;
		for (int i = 0; i < ifLogicCnt; i++) {
			logicType = 0;
			IfLogicModel ifLogicModel = new IfLogicModel();
			start = fullIfSql.indexOf(startMark);
			end = StringUtil.getSymMarkIndex(realStartMark, endMark, fullIfSql, 0);
			if (start == -1 || end == -1) {
				break;
			}
			sqlPart = fullIfSql.substring(start + startMarkLenght, end);
			ifLogicModel.setPreParamsCnt(preParamsAccount);
			ifLogicModel.setParamsCnt(StringUtil.matchCnt(sqlPart, namedPattern, offset));
			// 下一组if中的前面的参数数量
			preParamsAccount = preParamsAccount + ifLogicModel.getParamsCnt();
			if ((ifStart = StringUtil.matchIndex(sqlPart, START_IF_PATTERN)) >= 0) {
				logicType = 1;
			} else if ((ifStart = StringUtil.matchIndex(sqlPart, START_ELSEIF_PATTERN)) >= 0) {
				logicType = 2;
			} else if ((ifStart = StringUtil.matchIndex(sqlPart, START_ELSE_PATTERN)) >= 0) {
				logicType = 3;
			}
			ifLogicModel.setType(logicType);
			// @if() 和@elseif()
			if (logicType == 1 || logicType == 2) {
				ifEnd = StringUtil.getSymMarkIndex("(", ")", sqlPart, ifStart);
				ifLogicModel.setLogicExpression(sqlPart.substring(sqlPart.indexOf("(", ifStart) + 1, ifEnd));
				sqlPart = sqlPart.substring(ifEnd + 1);
				ifLogicModel.setLogicParamsCnt(
						StringUtil.matchCnt(ifLogicModel.getLogicExpression(), namedPattern, offset));
			} else if (logicType == 3) {
				// 返回{m.start+offset,m.end()+offset}
				int[] indexes = StringUtil.matchIndex(sqlPart, ELSE_PATTERN, 0);
				sqlPart = sqlPart.substring(indexes[1]);
				ifLogicModel.setLogicExpression("");
				ifLogicModel.setLogicParamsCnt(0);
			}
			// 最终#[@if(?==xxx) sqlPart]剔除if逻辑字符后的sql部分
			ifLogicModel.setSqlPart(sqlPart);
			if (!logicResult) {
				// 前面的都不成立，else则成立
				if (logicType == 3) {
					logicResult = true;
					ifLogicModel.setLogicResult(true);
				} else if (logicType == 1 || logicType == 2) {
					// @if 或 @elseif计算表达式
					boolean evalValue = MacroIfLogic.evalLogic(ifLogicModel.getLogicExpression(), paramsList,
							ifLogicModel.getPreParamsCnt(), ifLogicModel.getLogicParamsCnt(), sqlParamType);
					if (evalValue) {
						ifLogicModel.setLogicResult(true);
						logicResult = true;
					}
				}
			}
			if (logicType > 0) {
				ifLogicModelAry.add(ifLogicModel);
			} else {
				break;
			}
			// 下一个#[@elseif(?==x) and xxx] 或 #[@else and xxx]
			fullIfSql = fullIfSql.substring(end + endMarkLength);
		}
		IfLogicModel ifLogicModel;
		// 如果if条件全部不成立则返回空白
		String resultSql = BLANK;
		// 从后往前删除参数
		for (int j = ifLogicModelAry.size(); j > 0; j--) {
			ifLogicModel = ifLogicModelAry.get(j - 1);
			// 逻辑不成立,其sql不作为结果并剔除sql中所有参数参数
			if (!ifLogicModel.isLogicResult()) {
				for (int k = ifLogicModel.getParamsCnt(); k > 0; k--) {
					paramsList.remove(k + ifLogicModel.getPreParamsCnt() - 1);
				}
			} else {
				// 逻辑成立，该逻辑sql作为结果sql
				resultSql = ifLogicModel.getSqlPart();
				// 剔除逻辑部分的参数
				for (int k = 0; k < ifLogicModel.getLogicParamsCnt(); k++) {
					paramsList.remove(ifLogicModel.getPreParamsCnt());
				}
			}
		}
		return resultSql;
	}
}