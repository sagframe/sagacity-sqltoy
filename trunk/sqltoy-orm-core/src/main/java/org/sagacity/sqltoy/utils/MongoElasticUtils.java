package org.sagacity.sqltoy.utils;

import java.util.Collection;
import java.util.HashMap;
import java.util.IllegalFormatFlagsException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.SqlConfigParseUtils;
import org.sagacity.sqltoy.config.model.FieldTranslate;
import org.sagacity.sqltoy.config.model.NoSqlFieldsModel;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlToyResult;
import org.sagacity.sqltoy.config.model.Translate;
import org.sagacity.sqltoy.translate.DynamicCacheFetch;
import org.sagacity.sqltoy.translate.FieldTranslateCacheHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sagacity-sqltoy
 * @description 提供针对mongodb、elasticSearch集成的处理函数和逻辑
 * @author zhongxuchen
 * @version v1.0,Date:2017年3月10日
 * @modify {Date:2024-10-2 强化@if功能，增加@elseif 和 @else 的支持,elastic
 *         sql增加field=null改为field is null }
 */
public class MongoElasticUtils {
	/**
	 * 定义全局日志
	 */
	protected final static Logger logger = LoggerFactory.getLogger(MongoElasticUtils.class);

	/**
	 * sql伪指令开始标记,#[]符号等于 null==?判断
	 */
	private final static String SQL_PSEUDO_START_MARK = "#[";
	public final static String SQL_PSEUDO_SYM_START_MARK = "[";
	private final static String MQL_PSEUDO_START_MARK = "<#>";

	/**
	 * sql伪指令收尾标记
	 */
	private final static String SQL_PSEUDO_END_MARK = "]";
	private final static String MQL_PSEUDO_END_MARK = "</#>";
	private final static String BLANK = " ";
	public final static String BLANK_REGEX = "(?i)\\@blank\\s*\\(\\s*\\:[A-Za-z_0-9\\-]+\\s*\\)";
	public final static Pattern BLANK_PATTERN = Pattern.compile(BLANK_REGEX);
	public final static String VALUE_REGEX = "(?i)\\@value\\s*\\(\\s*\\:[A-Za-z_0-9\\-]+\\s*\\)";
	public final static Pattern VALUE_PATTERN = Pattern.compile(VALUE_REGEX);

	private MongoElasticUtils() {
	}

	/**
	 * @TODO 处理elastic sql
	 * @param sqlToyConfig
	 * @param paramNames
	 * @param paramValues
	 * @return
	 */
	private static SqlToyResult wrapNoSql(SqlToyConfig sqlToyConfig, String[] paramNames, Object[] paramValues) {
		String mql = sqlToyConfig.getSql(null);
		// 提取条件参数
		String[] fullNames = null;
		boolean sqlMode = sqlToyConfig.getNoSqlConfigModel().isSqlMode();
		if (sqlMode) {
			fullNames = SqlConfigParseUtils.getSqlParamsName(mql, false);
		} else {
			fullNames = SqlConfigParseUtils.getNoSqlParamsName(mql, false);
		}
		Pattern namedPattern = sqlMode ? SqlToyConstants.SQL_NAMED_PATTERN : SqlToyConstants.NOSQL_NAMED_PATTERN;
		// 提取参数值
		Object[] fullParamValues = SqlConfigParseUtils.matchNamedParam(fullNames, paramNames, paramValues);
		SqlToyResult sqlToyResult = processNullConditions(mql, fullParamValues, sqlMode);
		// 处理@blank(:name)
		processBlank(sqlToyResult, namedPattern, sqlMode);
		// 处理@value(:name)
		processValue(sqlToyResult, namedPattern, sqlMode);
		return sqlToyResult;
	}

	/**
	 * @todo 结合条件组织mongodb 的查询语句
	 * @param sqlToyConfig
	 * @param paramNames
	 * @param paramValues
	 * @return
	 */
	public static String wrapMql(SqlToyConfig sqlToyConfig, String[] paramNames, Object[] paramValues) {
		if (paramNames == null || paramNames.length == 0) {
			return sqlToyConfig.getSql(null);
		}
		SqlToyResult sqlToyResult = wrapNoSql(sqlToyConfig, paramNames, paramValues);
		if (sqlToyConfig.getNoSqlConfigModel().isSqlMode()) {
			return replaceSqlParams(sqlToyResult.getSql(), sqlToyResult.getParamsValue(), true);
		}
		String mongoJson = replaceNoSqlParams(sqlToyResult.getSql(), sqlToyResult.getParamsValue(), true).trim();
		// json格式补全
		if (!mongoJson.startsWith("{")) {
			mongoJson = "{".concat(mongoJson);
		}
		if (!mongoJson.endsWith("}")) {
			mongoJson = mongoJson.concat("}");
		}
		return mongoJson;
	}

	/**
	 * @todo 结合条件组织elasticSearch最终的执行语句
	 * @param sqlToyConfig
	 * @param paramNames
	 * @param paramValues
	 * @return
	 */
	public static String wrapES(SqlToyConfig sqlToyConfig, String[] paramNames, Object[] paramValues) {
		if (paramNames == null || paramNames.length == 0) {
			return sqlToyConfig.getSql(null);
		}
		SqlToyResult sqlToyResult = wrapNoSql(sqlToyConfig, paramNames, paramValues);
		// 替换mql中的参数(双引号)
		if (sqlToyConfig.getNoSqlConfigModel().isSqlMode()) {
			return replaceSqlParams(sqlToyResult.getSql(), sqlToyResult.getParamsValue(), true);
		}
		String elasticJson = replaceNoSqlParams(sqlToyResult.getSql(), sqlToyResult.getParamsValue(), false).trim();
		// json格式补全
		if (!elasticJson.startsWith("{")) {
			elasticJson = "{".concat(elasticJson);
		}
		if (!elasticJson.endsWith("}")) {
			elasticJson = elasticJson.concat("}");
		}
		return elasticJson;
	}

	/**
	 * @todo 处理sql中的参数过滤逻辑
	 * @param queryStr
	 * @param paramValues
	 * @param sqlMode
	 * @return
	 */
	private static SqlToyResult processNullConditions(String queryStr, Object[] paramValues, boolean sqlMode) {
		SqlToyResult sqlToyResult = new SqlToyResult();
		sqlToyResult.setSql(queryStr);
		sqlToyResult.setParamsValue(paramValues);
		if (queryStr.indexOf(SQL_PSEUDO_START_MARK) == -1 && queryStr.indexOf(MQL_PSEUDO_START_MARK) == -1) {
			return sqlToyResult;
		}
		boolean isMqlMark = false;
		if (queryStr.indexOf(MQL_PSEUDO_START_MARK) != -1) {
			isMqlMark = true;
		}
		// 兼容#[] 和<#></#> 两种模式配置
		String startMark = isMqlMark ? MQL_PSEUDO_START_MARK : SQL_PSEUDO_START_MARK;
		String endMark = isMqlMark ? MQL_PSEUDO_END_MARK : SQL_PSEUDO_END_MARK;
		Pattern namedPattern = sqlMode ? SqlToyConstants.SQL_NAMED_PATTERN : SqlToyConstants.NOSQL_NAMED_PATTERN;
		int startMarkLength = startMark.length();
		int endMarkLength = endMark.length();
		int pseudoMarkStart = queryStr.indexOf(startMark);
		int paramCnt, preParamCnt, beginMarkIndex, endMarkIndex;
		String preSql, markContentSql, tailSql;
		List paramValuesList = CollectionUtil.arrayToList(paramValues);
		int ifStart;
		int ifLogicSignStart;
		// sql内容体是否以and 或 or 结尾
		boolean isEndWithAndOr = false;
		// 0 无if、else等1:单个if；>1：if+else等
		int ifLogicCnt = 0;
		boolean isDynamicSql;
		int offset = sqlMode ? 1 : 0;
		int sqlParamType = sqlMode ? 1 : 2;
		while (pseudoMarkStart != -1) {
			ifLogicCnt = 0;
			isEndWithAndOr = false;
			// 始终从最后一个#[]进行处理
			beginMarkIndex = queryStr.lastIndexOf(startMark);
			// update 2021-01-17 兼容sql中存在"["和"]"符号场景
			endMarkIndex = StringUtil.getSymMarkIndex(
					startMark.equals(SQL_PSEUDO_START_MARK) ? SQL_PSEUDO_SYM_START_MARK : startMark, endMark, queryStr,
					beginMarkIndex);
			if (endMarkIndex == -1) {
				throw new IllegalFormatFlagsException(
						"json查询语句中缺乏:\"" + startMark + "\" 相对称的:\"" + endMark + "\"符号,请检查json查询语句格式!");
			}
			// 最后一个#[前的sql
			preSql = queryStr.substring(0, beginMarkIndex).concat(BLANK);
			// 最后#[]中的查询语句,加空白减少substr(index+1)可能引起的错误
			markContentSql = BLANK.concat(queryStr.substring(beginMarkIndex + startMarkLength, endMarkIndex))
					.concat(BLANK);
			ifLogicSignStart = StringUtil.matchIndex(markContentSql, SqlConfigParseUtils.IF_ALL_PATTERN);
			ifStart = StringUtil.matchIndex(markContentSql, SqlConfigParseUtils.IF_PATTERN);
			// 单一的@if 逻辑
			if (ifStart == ifLogicSignStart && ifStart > 0) {
				ifLogicCnt = 1;
			}
			// 属于@elseif 或@else()
			else if (ifStart == -1 && ifLogicSignStart > 0) {
				// 逆向找到@else 或@elseif 对称的@if位置
				int symIfIndex = SqlConfigParseUtils.getStartIfIndex(preSql,
						startMark.equals(SQL_PSEUDO_START_MARK) ? SQL_PSEUDO_SYM_START_MARK : startMark, endMark);
				if (symIfIndex == -1) {
					throw new IllegalFormatFlagsException("编写模式存在错误:@elseif(?==xx) @else 条件判断必须要有对应的@if()形成对称格式!");
				}
				beginMarkIndex = queryStr.substring(0, symIfIndex).lastIndexOf(startMark);
				preSql = queryStr.substring(0, beginMarkIndex).concat(BLANK);
				markContentSql = BLANK.concat(queryStr.substring(beginMarkIndex + startMarkLength, endMarkIndex))
						.concat(BLANK);
				ifLogicCnt = StringUtil.matchCnt(markContentSql, SqlConfigParseUtils.IF_ALL_PATTERN);
			}
			tailSql = queryStr.substring(endMarkIndex + endMarkLength);
			// 在#[前的参数个数
			preParamCnt = StringUtil.matchCnt(preSql, namedPattern, offset);
			markContentSql = SqlConfigParseUtils.processIfLogic(markContentSql, startMark, endMark, namedPattern,
					paramValuesList, preSql, preParamCnt, ifLogicCnt, offset, sqlParamType);
			// 没有@if 或@else 等逻辑。简单的最内层单一的#[sqlPart]对sqlPart的处理
			if (ifLogicCnt == 0) {
				if (sqlMode) {
					isEndWithAndOr = StringUtil.matches(markContentSql, SqlToyConstants.AND_OR_END);
				}
				// 判断sqlPart中是否有动态参数
				paramCnt = StringUtil.matchCnt(markContentSql, namedPattern, offset);
				// 无参数，整体剔除;有参数，判断参数是否为null决定是否剔除sqlPart
				markContentSql = (paramCnt == 0) ? BLANK
						: SqlConfigParseUtils.processMarkContent(markContentSql, namedPattern, paramValuesList,
								preParamCnt, paramCnt, sqlMode);
			} else {
				isDynamicSql = SqlConfigParseUtils.isDynamicSql(markContentSql, startMark, endMark);
				// #[sqlPart] 中sqlPart里面没有#[]
				if (!isDynamicSql) {
					if (sqlMode) {
						isEndWithAndOr = StringUtil.matches(markContentSql, SqlToyConstants.AND_OR_END);
					}
					paramCnt = StringUtil.matchCnt(markContentSql, namedPattern, offset);
					// 判断sqlPart中参数是否为null，决定是否剔除sqlPart
					markContentSql = SqlConfigParseUtils.processMarkContent(markContentSql, namedPattern,
							paramValuesList, preParamCnt, paramCnt, sqlMode);
				} else {
					// sqlPart中存在#[],剔除掉所有#[],再判断剩余sql中是否有动态参数
					String clearSymMarkStr = StringUtil.clearSymMarkContent(markContentSql, startMark, endMark);
					// 剩余sql中的动态参数个数
					int clearAfterArgCnt = StringUtil.matchCnt(clearSymMarkStr, namedPattern, offset);
					// 动态参数大于0,类似 and status=:status #[xxx] 有:status参数，则变成#[and status=:status
					// #[xxx]]继续利用sqltoy的判空剔除规则
					if (clearAfterArgCnt > 0) {
						markContentSql = startMark.concat(markContentSql).concat(endMark);
					} else {
						if (sqlMode) {
							isEndWithAndOr = StringUtil.matches(markContentSql, SqlToyConstants.AND_OR_END);
						}
					}
				}
			}
			if (sqlMode) {
				queryStr = SqlConfigParseUtils.processWhereLinkAnd(preSql, markContentSql, isEndWithAndOr, tailSql);
			} else {
				queryStr = preSql.concat(BLANK).concat(markContentSql).concat(BLANK).concat(tailSql);
			}
			pseudoMarkStart = queryStr.indexOf(startMark);
		}
		sqlToyResult.setSql(sqlMode ? queryStr : processComma(queryStr));
		sqlToyResult.setParamsValue(paramValuesList.toArray());
		return sqlToyResult;
	}

	/**
	 * @TODO 将@blank(:paramName) 设置为" "空白输出,同时在条件数组中剔除:paramName对应位置的条件值
	 * @param sqlToyResult
	 * @param argNamedPattern
	 * @param sqlMode
	 */
	private static void processBlank(SqlToyResult sqlToyResult, Pattern argNamedPattern, boolean sqlMode) {
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
			paramCnt = StringUtil.matchCnt(queryStr.substring(0, index), argNamedPattern, sqlMode ? 1 : 0);
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
	 * @TODO 处理直接显示参数值:#[@value(:paramNamed) sql]
	 * @param sqlToyResult
	 * @param argNamedPattern
	 * @param sqlMode
	 */
	private static void processValue(SqlToyResult sqlToyResult, Pattern namedPattern, boolean sqlMode) {
		if (null == sqlToyResult.getParamsValue() || sqlToyResult.getParamsValue().length == 0) {
			return;
		}
		String queryStr = sqlToyResult.getSql().toLowerCase();
		// @value(:paramName)
		Matcher m = VALUE_PATTERN.matcher(queryStr);
		int index = 0;
		int paramCnt = 0;
		int valueCnt = 0;
		List paramValueList = null;
		Object paramValue = null;
		while (m.find()) {
			if (valueCnt == 0) {
				paramValueList = CollectionUtil.arrayToList(sqlToyResult.getParamsValue());
			}
			index = m.start();
			paramCnt = StringUtil.matchCnt(queryStr.substring(0, index), namedPattern, sqlMode ? 1 : 0);
			// 用参数的值直接覆盖@value(:name)
			paramValue = paramValueList.get(paramCnt - valueCnt);
			sqlToyResult.setSql(sqlToyResult.getSql().replaceFirst(VALUE_REGEX,
					(paramValue == null) ? "null" : paramValue.toString()));
			// 剔除参数@value(:name) 对应的参数值
			paramValueList.remove(paramCnt - valueCnt);
			valueCnt++;
		}
		if (valueCnt > 0) {
			sqlToyResult.setParamsValue(paramValueList.toArray());
		}
	}

	/**
	 * @todo 通过@dot()方式补充sql处理后前后逗号衔接
	 * @param sql
	 * @return
	 */
	public static String processComma(String sql) {
		String[] sqlSlice = sql.split("(?i)\\@(dot|comma)\\(\\s*\\)");
		if (sqlSlice.length == 1) {
			return sql;
		}
		StringBuilder result = new StringBuilder();
		String fragment;
		for (int i = 0; i < sqlSlice.length; i++) {
			if (i > 0) {
				result.append(" , ");
			}
			fragment = sqlSlice[i].trim();
			if (fragment.startsWith(",")) {
				fragment = fragment.substring(1);
			}
			// 剔除掉最后的逗号
			if (fragment.endsWith(",")) {
				fragment = fragment.substring(0, fragment.length() - 1);
			}
			result.append(fragment);
		}
		return result.toString();
	}

	/**
	 * @todo 将参数的值带入实际查询语句中
	 * @param sql
	 * @param paramValues
	 * @param addSingleQuotation
	 * @return
	 */
	public static String replaceNoSqlParams(String sql, Object[] paramValues, boolean addSingleQuotation) {
		if (paramValues == null || paramValues.length == 0) {
			return sql;
		}
		Matcher m = SqlToyConstants.NOSQL_NAMED_PATTERN.matcher(sql);
		StringBuilder realMql = new StringBuilder();
		String method;
		String groupStr;
		int start = 0;
		int index = 0;
		Object value;
		boolean isAry = false;
		Object[] ary = null;
		int i;
		String sign = addSingleQuotation ? "'" : "";
		while (m.find()) {
			groupStr = m.group();
			realMql.append(sql.substring(start, m.start()));
			start = m.end();
			method = groupStr.substring(1, groupStr.indexOf("(")).toLowerCase().trim();
			value = paramValues[index];
			if ("".equals(method) || "param".equals(method) || "value".equals(method)) {
				isAry = true;
				if (value != null && value.getClass().isArray()) {
					ary = CollectionUtil.convertArray(value);
				} else if (value instanceof Collection) {
					ary = ((Collection) value).toArray();
				} else {
					ary = new Object[] { value };
					isAry = false;
				}
				if (isAry) {
					realMql.append("[");
				}
				i = 0;
				for (Object item : ary) {
					if (i > 0) {
						realMql.append(",");
					}
					if (item == null) {
						realMql.append("null");
					} else if (item instanceof CharSequence) {
						realMql.append(sign).append(removeDangerWords(item.toString())).append(sign);
					} else {
						realMql.append(SqlUtil.toSqlString(item, addSingleQuotation));
					}
					i++;
				}
				if (isAry) {
					realMql.append("]");
				}
			}
			index++;
		}
		realMql.append(sql.substring(start));
		return realMql.toString();
	}

	/**
	 * @todo 替换sql模式的查询参数
	 * @param sql
	 * @param paramValues
	 * @param addSingleQuotation
	 * @return
	 */
	public static String replaceSqlParams(String sql, Object[] paramValues, boolean addSingleQuotation) {
		if (paramValues == null || paramValues.length == 0) {
			return sql;
		}
		Matcher m = SqlToyConstants.SQL_NAMED_PATTERN.matcher(sql);
		StringBuilder realMql = new StringBuilder();
		int start = 0;
		int index = 0;
		Object value;
		String sqlPart;
		boolean isUpdateOrNotWhere = false;
		String preSql;
		while (m.find(start)) {
			value = paramValues[index];
			// m.start()+1 补偿\\W开始的字符,如 t.name=:name 保留下=号
			sqlPart = sql.substring(start, m.start() + 1);
			if (value == null) {
				preSql = sql.substring(0, m.start() + 1);
				isUpdateOrNotWhere = false;
				// update field=?或sql中没有where
				if (StringUtil.matches(preSql, SqlConfigParseUtils.UPDATE_EQUAL_PATTERN)
						|| !StringUtil.matches(preSql, SqlConfigParseUtils.WHERE_PATTERN)) {
					isUpdateOrNotWhere = true;
				}
				// processNull，针对=null和!=null 逻辑调整为is null和 is not null
				realMql.append(processNull(sqlPart, isUpdateOrNotWhere)).append("null");
			} else {
				realMql.append(sqlPart);
				realMql.append(SqlUtil.toSqlString(value, true));
			}
			index++;
			// 参数正则表达式:param\s? 末尾可能为空白
			if (StringUtil.matches(m.group(), SqlToyConstants.BLANK_END)) {
				start = m.end() - 1;
			} else {
				start = m.end();
			}
		}
		// 切去尾部sql
		realMql.append(sql.substring(start));
		return realMql.toString();
	}

	/**
	 * 针对条件查询中的参数为null时,将=null、<>null 语句调整为is null和is not null
	 * 
	 * @param sqlContent
	 * @param isUpdateOrNotWhere
	 * @return
	 */
	private static String processNull(String sqlContent, boolean isUpdateOrNotWhere) {
		int compareIndex = StringUtil.matchIndex(sqlContent, SqlConfigParseUtils.NOT_EQUAL_PATTERN);
		String sqlPart = " is not ";
		// 判断等于
		if (compareIndex == -1) {
			compareIndex = StringUtil.matchIndex(sqlContent, SqlConfigParseUtils.EQUAL_PATTERN);
			if (compareIndex != -1) {
				// update field=?或sql中没有where
				if (isUpdateOrNotWhere) {
					compareIndex = -1;
				}
			}
			// [^><!]= 非某个字符开头占用了一位，要往后移动一位
			if (compareIndex != -1) {
				compareIndex = compareIndex + 1;
			}
			sqlPart = " is ";
		}
		// 存在where条件参数为=或<> 改成is (not) null
		if (compareIndex != -1) {
			return sqlContent.substring(0, compareIndex).concat(sqlPart);
		}
		return sqlContent;
	}

	/**
	 * @todo 去除危险词,如{\[\}\]\"\' $等符号,让json拼接的结果只能是一个对比变量，而无法形成新的语句
	 * @param paramValue
	 * @return
	 */
	private static String removeDangerWords(String paramValue) {
		return paramValue.replaceAll("(\"|\'|\\{|\\[|\\}|\\]|\\$|&quot;)", "");
	}

	/**
	 * @todo 处理缓存翻译
	 * @param sqlToyContext
	 * @param sqlToyConfig
	 * @param resultSet
	 * @param fields
	 */
	public static void processTranslate(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig, List resultSet,
			String[] fields) {
		// 判断是否有缓存翻译器定义
		HashMap<String, FieldTranslate> translateMap = sqlToyConfig.getTranslateMap();
		// 存在缓存翻译,获取缓存数据
		if (!sqlToyConfig.getTranslateMap().isEmpty()) {
			HashMap<String, FieldTranslateCacheHolder> translateCache = sqlToyContext.getTranslateManager()
					.getTranslates(translateMap);
			translate(sqlToyContext.getDynamicCacheFetch(), sqlToyConfig.getTranslateMap(), translateCache, resultSet,
					fields);
		}
	}

	/**
	 * @todo 对结果集合进行缓存翻译
	 * @param translateCache
	 * @param dataSet
	 * @param fields
	 */
	private static void translate(DynamicCacheFetch dynamicCacheFetch, HashMap<String, FieldTranslate> translateMap,
			HashMap<String, FieldTranslateCacheHolder> translateCache, List<List> dataSet, String[] fields) {
		if (translateCache == null || translateCache.isEmpty()) {
			return;
		}
		if (dataSet == null || dataSet.isEmpty()) {
			return;
		}
		HashMap<String, Integer> colIndexMap = new HashMap<String, Integer>();
		int fieldCnt = fields.length;
		int[] realIndex = new int[fieldCnt];
		for (int i = 0; i < fieldCnt; i++) {
			colIndexMap.put(fields[i].toLowerCase(), i);
		}
		// 校验缓存翻译的配置是否正确
		translateCache.forEach((fieldName, fieldTranslateCacheHolder) -> {
			for (Translate translate : fieldTranslateCacheHolder.getTranslates()) {
				if (translate.getExtend().hasLogic) {
					if (!colIndexMap.containsKey(translate.getExtend().compareColumn)) {
						throw new IllegalArgumentException(
								"缓存翻译配置where表达式中的逻辑判断列:[" + translate.getExtend().compareColumn + "]不存在,请检查缓存翻译!");
					}
				}
			}
		});
		// 针对mongodb存在别名模式,翻译的字段依赖另外的字段值作为基础
		String fieldLow;
		FieldTranslate fieldTranslate;
		for (int i = 0; i < fieldCnt; i++) {
			fieldLow = fields[i].toLowerCase();
			realIndex[i] = i;
			if (translateMap.containsKey(fieldLow)) {
				fieldTranslate = translateMap.get(fieldLow);
				// alias是对应有效列,即原始值列
				if (fieldTranslate.aliasName != null) {
					realIndex[i] = colIndexMap.get(fieldTranslate.aliasName.toLowerCase());
				}
			}
		}
		int size = dataSet.size();
		List rowList;
		Object cellValue;
		FieldTranslateCacheHolder fieldTranslateHandler;
		for (int i = 0; i < fieldCnt; i++) {
			fieldTranslateHandler = translateCache.get(fields[i].toLowerCase());
			if (fieldTranslateHandler != null) {
				for (int j = 0; j < size; j++) {
					rowList = dataSet.get(j);
					// 取realIndex实际对应的值列
					cellValue = rowList.get(realIndex[i]);
					if (cellValue != null) {
						rowList.set(i, fieldTranslateHandler.getRowCacheValue(dynamicCacheFetch, rowList, colIndexMap,
								cellValue.toString()));
					}
				}
			}
		}
	}

	/**
	 * @TODO 统一解析elastic或mongodb 的fields 信息,分解成fieldName 和 aliasName
	 * @param fields
	 * @param fieldMap
	 * @return
	 */
	public static NoSqlFieldsModel processFields(String[] fields, HashMap<String, String[]> fieldMap) {
		NoSqlFieldsModel result = new NoSqlFieldsModel();
		String[] realFields = new String[fields.length];
		String[] aliasFields = new String[fields.length];
		int aliasIndex = 0;
		for (int i = 0; i < fields.length; i++) {
			realFields[i] = fields[i];
			aliasFields[i] = fields[i];
			aliasIndex = fields[i].indexOf(":");
			if (aliasIndex != -1) {
				realFields[i] = fields[i].substring(0, aliasIndex).trim();
				aliasFields[i] = fields[i].substring(aliasIndex + 1).trim();
			} else {
				aliasIndex = fields[i].lastIndexOf(".");
				if (aliasIndex != -1) {
					aliasFields[i] = fields[i].substring(aliasIndex + 1).trim();
				}
			}
			// 放入缓存为了提升效率
			if (fieldMap != null && realFields[i].contains(".")) {
				fieldMap.put(realFields[i], realFields[i].split("\\."));
			}
		}
		result.setFields(realFields);
		result.setAliasLabels(aliasFields);
		return result;
	}
}
