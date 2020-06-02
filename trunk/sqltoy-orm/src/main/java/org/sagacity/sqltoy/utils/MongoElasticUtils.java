/**
 * 
 */
package org.sagacity.sqltoy.utils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.SqlConfigParseUtils;
import org.sagacity.sqltoy.config.model.NoSqlFieldsModel;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlToyResult;
import org.sagacity.sqltoy.config.model.SqlTranslate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sagacity-sqltoy4.1
 * @description 提供针对mongodb、elasticSearch集成的处理函数和逻辑
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:MongoElasticUtils.java,Revision:v1.0,Date:2017年3月10日
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
	private final static String MQL_PSEUDO_START_MARK = "<#>";

	/**
	 * sql伪指令收尾标记
	 */
	private final static String SQL_PSEUDO_END_MARK = "]";
	private final static String MQL_PSEUDO_END_MARK = "</#>";
	private final static String BLANK = " ";

	private static SqlToyResult wrapNoSql(SqlToyConfig sqlToyConfig, String[] paramNames, Object[] paramValues) {
		String mql = sqlToyConfig.getSql(null);
		// 提取条件参数
		String[] fullNames = null;
		if (sqlToyConfig.getNoSqlConfigModel().isSqlMode()) {
			fullNames = SqlConfigParseUtils.getSqlParamsName(mql, false);
		} else {
			fullNames = SqlConfigParseUtils.getNoSqlParamsName(mql, false);
		}
		// 提取参数值
		Object[] fullParamValues = SqlConfigParseUtils.matchNamedParam(fullNames, paramNames, paramValues);
		return processNullConditions(mql, fullParamValues, sqlToyConfig.getNoSqlConfigModel().isSqlMode());
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
			return replaceSqlParams(sqlToyResult.getSql(), sqlToyResult.getParamsValue(), "'");
		}
		String mongoJson = replaceNoSqlParams(sqlToyResult.getSql(), sqlToyResult.getParamsValue(), "'").trim();
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
			return replaceSqlParams(sqlToyResult.getSql(), sqlToyResult.getParamsValue(), "'");
		}
		String elasticJson = replaceNoSqlParams(sqlToyResult.getSql(), sqlToyResult.getParamsValue(), "\"").trim();
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
		if (queryStr.indexOf(SQL_PSEUDO_START_MARK) == -1 && queryStr.indexOf(MQL_PSEUDO_START_MARK) == -1)
			return sqlToyResult;
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

		int beginIndex, endIndex, paramCnt, preParamCnt, beginMarkIndex, endMarkIndex;
		String preSql, markContentSql, tailSql;
		List paramValuesList = CollectionUtil.arrayToList(paramValues);
		while (pseudoMarkStart != -1) {
			// 始终从最后一个#[]进行处理
			beginMarkIndex = queryStr.lastIndexOf(startMark);
			endMarkIndex = StringUtil.getSymMarkIndex(startMark, endMark, queryStr, beginMarkIndex + startMarkLength);
			// 最后一个#[前的sql
			preSql = queryStr.substring(0, beginMarkIndex).concat(BLANK);
			// 最后#[]中的查询语句,加空白减少substr(index+1)可能引起的错误
			markContentSql = BLANK.concat(queryStr.substring(beginMarkIndex + startMarkLength, endMarkIndex))
					.concat(BLANK);
			tailSql = queryStr.substring(endMarkIndex + endMarkLength);
			// 获取#[]中的参数数量
			paramCnt = StringUtil.matchCnt(markContentSql, namedPattern);
			// #[]中无参数，拼接preSql+markContentSql+tailSql
			if (paramCnt == 0) {
				queryStr = preSql.concat(BLANK).concat(tailSql);
			} else {
				// 在#[前的参数个数
				preParamCnt = StringUtil.matchCnt(preSql, namedPattern);
				boolean logicValue = true;
				int start = markContentSql.toLowerCase().indexOf("@if(");
				// sql中存在逻辑判断
				if (start > -1) {
					int end = StringUtil.getSymMarkIndex("(", ")", markContentSql, start);
					String evalStr = BLANK
							.concat(markContentSql.substring(markContentSql.indexOf("(", start) + 1, end));
					int logicParamCnt = StringUtil.matchCnt(evalStr, namedPattern);
					// update 2017-4-14 增加@if()简单逻辑判断
					logicValue = MacroIfLogic.evalLogic(evalStr, paramValuesList, preParamCnt, logicParamCnt);
					// 逻辑不成立,剔除sql和对应参数
					if (!logicValue) {
						markContentSql = BLANK;
						for (int k = paramCnt; k > 0; k--) {
							paramValuesList.remove(k + preParamCnt - 1);
						}
					} else {
						// 逻辑成立,去除@if()部分sql和对应的参数,同时将剩余参数数量减掉@if()中的参数数量
						markContentSql = markContentSql.substring(0, start).concat(markContentSql.substring(end + 1));
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
					Object value;
					boolean isNull;
					// 按顺序处理#[]中sql的参数
					for (int i = preParamCnt; i < preParamCnt + paramCnt; i++) {
						value = paramValuesList.get(i);
						beginIndex = endIndex;
						endIndex = StringUtil.matchIndex(markContentSql.substring(beginIndex), namedPattern);
						isNull = false;
						if (null == value) {
							isNull = true;
						} else if (null != value) {
							if (value.getClass().isArray() && CollectionUtil.convertArray(value).length == 0) {
								isNull = true;
							} else if ((value instanceof Collection) && ((Collection) value).isEmpty()) {
								isNull = true;
							}
						}

						// 1、参数值为null且非is 条件sql语句
						// 2、is 条件sql语句值非null、true、false 剔除#[]部分内容，同时将参数从数组中剔除
						if (isNull) {
							// sql中剔除最后部分的#[]内容
							markContentSql = BLANK;
							for (int k = paramCnt; k > 0; k--) {
								paramValuesList.remove(k + preParamCnt - 1);
							}
							break;
						}
					}
				}
				if (sqlMode) {
					queryStr = SqlConfigParseUtils.processWhereLinkAnd(preSql, markContentSql, tailSql);
				} else {
					queryStr = preSql.concat(BLANK).concat(markContentSql).concat(BLANK).concat(tailSql);
				}
			}
			pseudoMarkStart = queryStr.indexOf(startMark);
		}
		sqlToyResult.setSql(sqlMode ? queryStr : processComma(queryStr));
		sqlToyResult.setParamsValue(paramValuesList.toArray());
		return sqlToyResult;
	}

	/**
	 * @todo 通过@dot()方式补充sql处理后前后逗号衔接
	 * @param sql
	 * @return
	 */
	public static String processComma(String sql) {
		String[] sqlSlice = sql.split("(?i)\\@(dot|comma)\\(\\s*\\)");
		if (sqlSlice.length == 1)
			return sql;
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
	 * @param charSign
	 * @return
	 */
	public static String replaceNoSqlParams(String sql, Object[] paramValues, String charSign) {
		if (paramValues == null || paramValues.length == 0)
			return sql;
		Matcher m = SqlToyConstants.NOSQL_NAMED_PATTERN.matcher(sql);
		StringBuilder realMql = new StringBuilder();
		String method;
		String groupStr;
		int start = 0;
		int index = 0;
		Object value;
		boolean isAry = false;
		while (m.find()) {
			groupStr = m.group();
			realMql.append(sql.substring(start, m.start()));
			start = m.end();
			method = groupStr.substring(1, groupStr.indexOf("(")).toLowerCase().trim();
			value = paramValues[index];
			if (method.equals("") || method.equals("param") || method.equals("value")) {
				Object[] ary = null;
				isAry = true;
				if (value.getClass().isArray()) {
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
				int i = 0;
				for (Object var : ary) {
					if (i > 0) {
						realMql.append(",");
					}
					if (var instanceof Number) {
						realMql.append(var.toString());
					} else if ((var instanceof Date) || (var instanceof LocalDateTime)) {
						realMql.append(charSign).append(DateUtil.formatDate(var, "yyyy-MM-dd HH:mm:ss"))
								.append(charSign);
					} else if ((var instanceof LocalDate)) {
						realMql.append(charSign).append(DateUtil.formatDate(var, "yyyy-MM-dd")).append(charSign);
					} else if ((var instanceof LocalTime)) {
						realMql.append(charSign).append(DateUtil.formatDate(var, "HH:mm:ss")).append(charSign);
					} else {
						realMql.append(charSign).append(removeDangerWords(var.toString())).append(charSign);
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
	 * @param charSign
	 * @return
	 */
	public static String replaceSqlParams(String sql, Object[] paramValues, String charSign) {
		if (paramValues == null || paramValues.length == 0)
			return sql;
		Matcher m = SqlToyConstants.SQL_NAMED_PATTERN.matcher(sql);
		StringBuilder realMql = new StringBuilder();
		int start = 0;
		int index = 0;
		Object value;
		while (m.find()) {
			// m.start()+1 补偿\\W开始的字符,如 t.name=:name 保留下=号
			realMql.append(sql.substring(start, m.start() + 1));
			start = m.end();
			value = paramValues[index];
			Object[] ary = null;
			if (value.getClass().isArray()) {
				ary = CollectionUtil.convertArray(value);
			} else if (value instanceof Collection) {
				ary = ((Collection) value).toArray();
			} else {
				ary = new Object[] { value };
			}
			int i = 0;
			for (Object var : ary) {
				if (i > 0) {
					realMql.append(",");
				}
				if (var instanceof Number) {
					realMql.append(var.toString());
				} else if ((var instanceof Date) || (var instanceof LocalDateTime)) {
					realMql.append(charSign).append(DateUtil.formatDate(var, "yyyy-MM-dd HH:mm:ss")).append(charSign);
				} else if (var instanceof LocalDate) {
					realMql.append(charSign).append(DateUtil.formatDate(var, "yyyy-MM-dd")).append(charSign);
				} else if (var instanceof LocalTime) {
					realMql.append(charSign).append(DateUtil.formatDate(var, "HH:mm:ss")).append(charSign);
				} else {
					realMql.append(charSign).append(removeDangerWords(var.toString())).append(charSign);
				}
				i++;
			}
			index++;
			realMql.append(BLANK);
		}
		// 切去尾部sql
		realMql.append(sql.substring(start));
		return realMql.toString();
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
	 * @throws Exception
	 */
	public static void processTranslate(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig, List resultSet,
			String[] fields) {
		// 判断是否有缓存翻译器定义
		boolean hasTranslate = (sqlToyConfig.getTranslateMap() == null || sqlToyConfig.getTranslateMap().isEmpty())
				? false
				: true;
		HashMap<String, SqlTranslate> translateMap = hasTranslate ? sqlToyConfig.getTranslateMap() : null;
		HashMap<String, HashMap<String, Object[]>> translateCache = null;
		// 存在缓存翻译,利用sqltoy的缓存管理
		if (hasTranslate) {
			translateCache = sqlToyContext.getTranslateManager().getTranslates(sqlToyContext, null, translateMap);
			if (translateCache == null || translateCache.isEmpty()) {
				logger.warn("mongo or elastic cache:{} has no data!{}", sqlToyConfig.getTranslateMap().keySet(),
						sqlToyConfig.getSql());
				hasTranslate = false;
			}
		}
		// 缓存翻译
		if (hasTranslate) {
			translate(translateCache, translateMap, resultSet, null, fields);
		}
	}

	/**
	 * @todo 对结果集合进行缓存翻译
	 * @param translateCache
	 * @param translateMap
	 * @param dataSet
	 * @param dataMap
	 * @param fields
	 */
	private static void translate(HashMap<String, HashMap<String, Object[]>> translateCache,
			HashMap<String, SqlTranslate> translateMap, List<List> dataSet, Map dataMap, String[] fields) {
		if (translateMap == null || translateMap.isEmpty())
			return;
		if ((dataSet == null || dataSet.isEmpty()) && (dataMap == null || dataMap.isEmpty()))
			return;
		int[] cacheMapIndex = new int[translateMap.size()];
		int[] realIndex = new int[translateMap.size()];
		String[] lables = new String[translateMap.size()];
		String field;
		int index = 0;
		SqlTranslate translateModel;
		HashMap<String, Integer> map = new HashMap<String, Integer>();
		for (int i = 0; i < fields.length; i++) {
			map.put(fields[i].toLowerCase(), i);
		}
		for (int i = 0; i < fields.length; i++) {
			field = fields[i].toLowerCase();
			if (translateMap.containsKey(field)) {
				translateModel = translateMap.get(field);
				cacheMapIndex[index] = i;
				// alias是对应有效列
				realIndex[index] = map.get(translateModel.getAlias());
				// 实际对应的列
				lables[index] = field;
				index++;
			}
		}
		Object value;
		HashMap<String, Object[]> keyValues;
		int cacheIndex;
		Object[] translateAry;
		if (dataSet != null) {
			int size = dataSet.size();
			int colIndex;
			for (int i = 0; i < cacheMapIndex.length; i++) {
				colIndex = cacheMapIndex[i];
				keyValues = translateCache.get(lables[i]);
				translateModel = translateMap.get(lables[i]);
				cacheIndex = translateModel.getIndex();
				for (int j = 0; j < size; j++) {
					value = dataSet.get(j).get(realIndex[i]);
					if (value != null) {
						translateAry = keyValues.get(value.toString());
						if (null != translateAry) {
							dataSet.get(j).set(colIndex, keyValues.get(value.toString())[cacheIndex]);
						}
					}
				}
			}
		} else {
			for (int i = 0; i < cacheMapIndex.length; i++) {
				keyValues = translateCache.get(lables[i]);
				translateModel = translateMap.get(lables[i]);
				cacheIndex = translateModel.getIndex();
				// 实际列
				value = dataMap.get(translateModel.getAlias());
				if (value != null) {
					translateAry = keyValues.get(value.toString());
					if (null != translateAry) {
						dataMap.put(lables[i], keyValues.get(value.toString())[cacheIndex]);
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
