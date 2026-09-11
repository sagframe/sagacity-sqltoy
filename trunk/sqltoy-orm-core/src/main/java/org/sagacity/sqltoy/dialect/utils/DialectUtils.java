package org.sagacity.sqltoy.dialect.utils;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Type;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.sagacity.sqltoy.SqlExecuteStat;
import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.SqlToyThreadDataHolder;
import org.sagacity.sqltoy.callback.CallableStatementResultHandler;
import org.sagacity.sqltoy.callback.DecryptHandler;
import org.sagacity.sqltoy.callback.GenerateSavePKStrategy;
import org.sagacity.sqltoy.callback.GenerateSqlHandler;
import org.sagacity.sqltoy.callback.InsertRowCallbackHandler;
import org.sagacity.sqltoy.callback.LockSqlHandler;
import org.sagacity.sqltoy.callback.PreparedStatementResultHandler;
import org.sagacity.sqltoy.callback.ReflectPropsHandler;
import org.sagacity.sqltoy.callback.UniqueSqlHandler;
import org.sagacity.sqltoy.callback.UpdateRowHandler;
import org.sagacity.sqltoy.config.SqlConfigParseUtils;
import org.sagacity.sqltoy.config.model.DataVersionConfig;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.FieldMeta;
import org.sagacity.sqltoy.config.model.FieldSecureConfig;
import org.sagacity.sqltoy.config.model.FieldTranslate;
import org.sagacity.sqltoy.config.model.OperateType;
import org.sagacity.sqltoy.config.model.PKStrategy;
import org.sagacity.sqltoy.config.model.ShardingStrategyConfig;
import org.sagacity.sqltoy.config.model.SqlParamsModel;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlToyResult;
import org.sagacity.sqltoy.config.model.SqlType;
import org.sagacity.sqltoy.config.model.SqlWithAnalysis;
import org.sagacity.sqltoy.config.model.TableCascadeModel;
import org.sagacity.sqltoy.dialect.model.SavePKStrategy;
import org.sagacity.sqltoy.exception.DataAccessException;
import org.sagacity.sqltoy.model.IgnoreCaseSet;
import org.sagacity.sqltoy.model.IgnoreKeyCaseMap;
import org.sagacity.sqltoy.model.JdbcTypes;
import org.sagacity.sqltoy.model.LockMode;
import org.sagacity.sqltoy.model.MapKit;
import org.sagacity.sqltoy.model.QueryExecutor;
import org.sagacity.sqltoy.model.QueryResult;
import org.sagacity.sqltoy.model.SecureType;
import org.sagacity.sqltoy.model.StoreResult;
import org.sagacity.sqltoy.model.inner.QueryExecutorExtend;
import org.sagacity.sqltoy.plugins.IUnifyFieldsHandler;
import org.sagacity.sqltoy.plugins.SqlInterceptor;
import org.sagacity.sqltoy.plugins.secure.DesensitizeProvider;
import org.sagacity.sqltoy.plugins.secure.FieldsSecureProvider;
import org.sagacity.sqltoy.plugins.sharding.ShardingUtils;
import org.sagacity.sqltoy.utils.BeanUtil;
import org.sagacity.sqltoy.utils.CollectionUtil;
import org.sagacity.sqltoy.utils.DataSourceUtils;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;
import org.sagacity.sqltoy.utils.DateUtil;
import org.sagacity.sqltoy.utils.QueryExecutorBuilder;
import org.sagacity.sqltoy.utils.ReservedWordsUtil;
import org.sagacity.sqltoy.utils.ResultUtils;
import org.sagacity.sqltoy.utils.SqlUtil;
import org.sagacity.sqltoy.utils.SqlUtilsExt;
import org.sagacity.sqltoy.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author zhongxuchen
 * @version v1.0,Date:2014-12-26
 * @project sagacity-sqltoy
 * @description 提供一些不同数据库都通用的逻辑处理, 避免在各个数据库工具类中写重复代码
 * @modify Date:2017-02-24 优化count sql处理逻辑,排除统计型查询导致的问题,本质统计性查询不应该用分页方式查询
 * @modify Date:2018-01-06 优化对数据库表字段默认值的处理,提供统一的处理方法
 * @modify Date:2018-01-22 增加业务主键生成赋值,同时对saveAll等操作返回生成的主键值映射到VO集合中
 * @modify Date:2018-05-03 修复getCountBySql关于剔除order by部分的逻辑错误
 * @modify Date:2018-09-25 修复select和from对称判断问题,影响分页查询时剔除from之前语句构建select
 *         count(1) from错误}
 * @modify Date:2024-03-22 修复分页取count记录剔除order by片段未剔除对应参数的缺陷
 * @modify Date:2024-07-21 增加sqlInterceptor场景下，merge into on (条件) update
 *         set需要跳过on ()中存在的字段}
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public class DialectUtils {

	// update 2026-9-8 级联loadAll的字段匹配正则缓存(字段名->预编译Pattern)
	private static final java.util.concurrent.ConcurrentHashMap<String, Pattern> CASCADE_FIELD_PATTERN_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
	/**
	 * 定义日志
	 */
	protected final static Logger logger = LoggerFactory.getLogger(DialectUtils.class);

	// union 匹配模式
	public static final Pattern UNION_PATTERN = Pattern.compile("(?i)\\W+union\\W+");

	public static final Pattern ORDER_BY_PATTERN = Pattern.compile("(?i)\\Worder\\s+by\\W");

	public static final Pattern GROUP_BY_PATTERN = Pattern.compile("(?i)\\Wgroup\\s+by\\W");

	/**
	 * 存储过程格式
	 */
	public static final Pattern STORE_PATTERN = Pattern.compile("^(\\s*\\{)?\\s*\\?");

	// distinct 匹配模式
	public static final Pattern DISTINCT_PATTERN = Pattern.compile("(?i)^select\\s+distinct\\s+");

	/**
	 * 统计正则表达式
	 */
	public static final Pattern STAT_PATTERN = Pattern
			.compile("\\W(sum|avg|min|max|first|last|first_value|last_value)\\(");

	/**
	 * 查询select 匹配
	 */
	private static final String SELECT_REGEX = "select\\s+";

	/**
	 * 查询from 匹配
	 */
	private static final String FROM_REGEX = "\\s+from[\\(\\s+]";

	private static final String WHERE_REGEX = "\\s+where[\\(\\s+]";

	private static final HashMap<String, String> QuesFilters = new HashMap<String, String>() {
		private static final long serialVersionUID = 7135705054559913831L;

		{
			put("'", "'");
			put("\"", "\"");
		}
	};

	/**
	 * 处理分页sql的参数
	 * 
	 * @param sqlToyContext
	 * @param sqlToyConfig
	 * @param queryExecutor
	 * @param pageSql
	 * @param startIndex
	 * @param endIndex
	 * @param dialect
	 * @return
	 * @throws Exception
	 */
	public static SqlToyResult wrapPageSqlParams(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig,
			QueryExecutor queryExecutor, String pageSql, Object startIndex, Object endIndex, String dialect)
			throws Exception {
		QueryExecutorExtend extend = queryExecutor.getInnerModel();
		String[] paramsNamed = extend.getParamsName();
		Object[] paramsValue = extend.getParamsValue(sqlToyContext, sqlToyConfig);
		if (startIndex == null && endIndex == null) {
			return SqlConfigParseUtils.processSql(pageSql, paramsNamed, paramsValue, dialect);
		}
		String[] realParamNamed = null;
		Object[] realParamValue = null;
		int paramLength;
		// 分页2个参数，top一个参数
		int extendSize = (endIndex == null) ? 1 : 2;
		if (sqlToyConfig.isNamedParam()) {
			paramLength = paramsNamed.length;
			// 防止传null
			if (paramsValue == null) {
				paramsValue = new Object[paramLength];
			}
			realParamValue = new Object[paramLength + extendSize];
			// 设置开始记录和截止记录参数名称和对应的值
			realParamNamed = new String[paramLength + extendSize];
			if (paramLength > 0) {
				System.arraycopy(paramsNamed, 0, realParamNamed, 0, paramLength);
				System.arraycopy(paramsValue, 0, realParamValue, 0, paramLength);
			}
			realParamNamed[paramLength] = SqlToyConstants.PAGE_FIRST_PARAM_NAME;
			realParamValue[paramLength] = startIndex;
			if (extendSize == 2) {
				realParamNamed[paramLength + 1] = SqlToyConstants.PAGE_LAST_PARAM_NAME;
				realParamValue[paramLength + 1] = endIndex;
			}
		} else {
			// 参数计数需字面量感知,规避字面量内的?被计入导致分页参数错位
			boolean backslashEscape = SqlConfigParseUtils.isBackslashEscapeDialect(DataSourceUtils.getDBType(dialect));
			int totalParamCnt = getParamsCount(sqlToyConfig.getSql(null), backslashEscape);
			// sql中没有?条件参数
			if (totalParamCnt == 0) {
				realParamValue = new Object[extendSize];
				realParamValue[0] = startIndex;
				if (extendSize == 2) {
					realParamValue[1] = endIndex;
				}
			} else {
				if (paramsValue == null && totalParamCnt > 0) {
					paramsValue = new Object[totalParamCnt];
				}
				paramLength = (paramsValue == null) ? 0 : paramsValue.length;
				realParamValue = new Object[paramLength + extendSize];
				if (sqlToyConfig.isHasFast()) {
					int tailSqlParamCnt = getParamsCount(sqlToyConfig.getFastTailSql(null), backslashEscape);
					// @fast() tail 前面部分参数数量
					int tailPreParamCnt = totalParamCnt - tailSqlParamCnt;
					System.arraycopy(paramsValue, 0, realParamValue, 0, tailPreParamCnt);
					realParamValue[tailPreParamCnt] = startIndex;
					if (extendSize == 2) {
						realParamValue[tailPreParamCnt + 1] = endIndex;
					}
					if (tailSqlParamCnt > 0) {
						System.arraycopy(paramsValue, tailPreParamCnt, realParamValue, tailPreParamCnt + extendSize,
								tailSqlParamCnt);
					}
				} else {
					if (paramLength > 0) {
						System.arraycopy(paramsValue, 0, realParamValue, 0, paramLength);
					}
					realParamValue[paramLength] = startIndex;
					if (extendSize == 2) {
						realParamValue[paramLength + 1] = endIndex;
					}
				}
			}
		}
		// 通过参数处理最终的sql和参数值
		return SqlConfigParseUtils.processSql(pageSql, realParamNamed, realParamValue, dialect);
	}

	/**
	 * @param sqlToyContext
	 * @param sqlToyConfig
	 * @param sql
	 * @param paramsValue
	 * @param extend
	 * @param decryptHandler 解密
	 * @param conn
	 * @param dbType
	 * @param startIndex
	 * @param fetchSize
	 * @param maxRows
	 * @return
	 * @throws Exception 实现普通的sql语句查询
	 */
	public static QueryResult findBySql(final SqlToyContext sqlToyContext, final SqlToyConfig sqlToyConfig,
			final String sql, final Object[] paramsValue, final QueryExecutorExtend extend,
			final DecryptHandler decryptHandler, final Connection conn, final Integer dbType, final int startIndex,
			final int fetchSize, final int maxRows) throws Exception {
		// 清除分页、取随机记录、取top 封装的开始和截止特殊标记
		String lastSql = SqlUtilsExt.clearOriginalSqlMark(sql);
		// 做sql签名
		lastSql = SqlUtilsExt.signSql(lastSql, dbType, sqlToyConfig);
		// 打印sql
		SqlExecuteStat.showSql("execute query", lastSql, paramsValue);
		PreparedStatement pst = null;
		// 常规单查询语句(load 查询extend为null)
		if (extend == null || !extend.sqlSegment) {
			pst = conn.prepareStatement(lastSql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
			if (fetchSize > 0) {
				pst.setFetchSize(fetchSize);
			}
			if (maxRows > 0) {
				pst.setMaxRows(maxRows);
			}
		} else {
			// sql段落，含多句sql(正常不使用)
			pst = conn.prepareStatement(lastSql);
		}
		// 设置查询超时(秒)
		if (extend != null && extend.timeout != null && extend.timeout > 0) {
			pst.setQueryTimeout(extend.timeout);
		} else if (SqlToyConstants.defaultStatementTimeout != null && SqlToyConstants.defaultStatementTimeout > 0) {
			pst.setQueryTimeout(SqlToyConstants.defaultStatementTimeout);
		}
		ResultSet rs = null;
		return (QueryResult) SqlUtil.preparedStatementProcess(null, pst, rs, new PreparedStatementResultHandler() {
			@Override
			public void execute(Object obj, PreparedStatement pst, ResultSet rs) throws Exception {
				try {
					SqlUtil.setParamsValue(sqlToyContext.getTypeHandler(), conn, dbType, pst, paramsValue, null, 0);
					rs = pst.executeQuery();
					this.setResult(ResultUtils.processResultSet(dbType, sqlToyContext, sqlToyConfig, conn, rs, extend,
							null, decryptHandler, startIndex));
				} catch (Exception e) {
					throw e;
				} finally {
					if (rs != null) {
						rs.close();
						rs = null;
					}
				}
			}
		});
	}

	/**
	 * 实现普通的sql语句查询
	 * 
	 * @param sqlToyContext
	 * @param sqlToyConfig
	 * @param sql
	 * @param paramsValue
	 * @param updateRowHandler
	 * @param conn
	 * @param dbType
	 * @param startIndex
	 * @param fetchSize
	 * @param maxRows
	 * @return
	 * @throws Exception
	 */
	public static QueryResult updateFetchBySql(final SqlToyContext sqlToyContext, final SqlToyConfig sqlToyConfig,
			final String sql, final Object[] paramsValue, final UpdateRowHandler updateRowHandler,
			final Connection conn, final Integer dbType, final int startIndex, final int fetchSize, final int maxRows)
			throws Exception {
		// 做sql签名
		String lastSql = SqlUtilsExt.signSql(sql, dbType, sqlToyConfig);
		// 打印sql
		SqlExecuteStat.showSql("execute updateFetch", lastSql, paramsValue);
		PreparedStatement pst = null;
		if (updateRowHandler == null) {
			pst = conn.prepareStatement(lastSql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
		} else {
			pst = conn.prepareStatement(lastSql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_UPDATABLE);
		}
		if (fetchSize > 0) {
			pst.setFetchSize(fetchSize);
		}
		if (maxRows > 0) {
			pst.setMaxRows(maxRows);
		}
		ResultSet rs = null;
		return (QueryResult) SqlUtil.preparedStatementProcess(null, pst, rs, new PreparedStatementResultHandler() {
			@Override
			public void execute(Object obj, PreparedStatement pst, ResultSet rs) throws Exception {
				try {
					SqlUtil.setParamsValue(sqlToyContext.getTypeHandler(), conn, dbType, pst, paramsValue, null, 0);
					rs = pst.executeQuery();
					this.setResult(ResultUtils.processResultSet(dbType, sqlToyContext, sqlToyConfig, conn, rs, null,
							updateRowHandler, null, startIndex));
				} catch (Exception e) {
					throw e;
				} finally {
					if (rs != null) {
						rs.close();
						rs = null;
					}
				}
			}
		});
	}

	/**
	 * 通用的查询记录总数(包含剔除order by和智能判断是直接select count from () 还是直接剔除from之前的语句补充select
	 * count)
	 * 
	 * @param sqlToyContext
	 * @param sqlToyConfig
	 * @param sql
	 * @param paramsValue
	 * @param isLastSql
	 * @param conn
	 * @param dbType
	 * @return
	 * @throws Exception
	 */
	public static Long getCountBySql(final SqlToyContext sqlToyContext, final SqlToyConfig sqlToyConfig,
			final String sql, final Object[] paramsValue, final boolean isLastSql, final QueryExecutorExtend extend,
			final Connection conn, final Integer dbType) throws Exception {
		String lastCountSql;
		int paramCnt = 0;
		int withParamCnt = 0;
		// order by 语句片段中存在的参数数量
		int orderByParamsCnt = 0;
		// 通过配置直接给定的最优化count 语句
		if (isLastSql) {
			lastCountSql = sql;
		} else {
			String countPart = " count(1) ";
			// es count(1) 不起作用
			if (dbType.equals(DBType.ES)) {
				countPart = " count(*) ";
			}
			String query_tmp = sql;
			String withSql = "";
			// with as分析器(避免每次做with 检测,提升效率)
			if (sqlToyConfig != null && sqlToyConfig.isHasWith()) {
				SqlWithAnalysis sqlWith = new SqlWithAnalysis(sql);
				// 判断with as是否在开始位置，如果在内部不做优化处理
				if (StringUtil.isBlank(sqlWith.getPreSql())) {
					query_tmp = sqlWith.getRejectWithSql();
					withSql = sqlWith.getWithSql();
				}
			}
			// 检测类扫描(order by/group by/from/distinct/union定位)在字面量掩码串上执行,
			// 掩码串与原串等长,偏移可直接用于原串截取;规避字面量内的)/order by/from等
			// 被误识别为语法特征导致count sql破碎(字符串手术仍使用原串)
			boolean backslashEscape = SqlConfigParseUtils.isBackslashEscapeDialect(dbType);
			String maskedSql = SqlConfigParseUtils.maskLiterals(query_tmp, backslashEscape);
			// 最后一个真实括号:字面量内的)已置空,规避其干扰order by所在层级的判断
			int lastBracketIndex = maskedSql.lastIndexOf(")");
			int sql_from_index = 0;
			// sql不以from开头，截取from 后的部分语句
			if (StringUtil.indexOfIgnoreCase(maskedSql, "from") != 0) {
				// update 2024-11-8 优化select (day from) as a from xxx 语句的兼容
				sql_from_index = SqlUtil.getSymMarkIndexExcludeKeyWords(maskedSql, SELECT_REGEX, FROM_REGEX, 0);
			}
			// 剔除order提高运行效率
			int orderByIndex = StringUtil.matchLastIndex(maskedSql, ORDER_BY_PATTERN, 1);
			// order by 在from 之后
			if (orderByIndex > sql_from_index) {
				// orderBy片段
				String orderBySql = null;
				// 剔除order by 语句
				if (orderByIndex > lastBracketIndex) {
					orderBySql = query_tmp.substring(orderByIndex + 1);
					query_tmp = query_tmp.substring(0, orderByIndex + 1);
				} else {
					// 剔除掉order by 后面语句对称的() 内容
					String orderJudgeSql = clearDisturbSql(maskedSql.substring(orderByIndex + 1));
					// 在order by 不在子查询内,说明可以整体切除掉order by
					if (orderJudgeSql.indexOf(")") == -1) {
						orderBySql = query_tmp.substring(orderByIndex + 1);
						query_tmp = query_tmp.substring(0, orderByIndex + 1);
					}
				}
				// 存在order by被剔除，获取其参数数量，从全部参数数组中剔除
				if (null != orderBySql) {
					orderByParamsCnt = getParamsCount(orderBySql, backslashEscape);
				}
			}
			int groupIndex = StringUtil.matchLastIndex(maskedSql, GROUP_BY_PATTERN, 1);
			// 判断group by 是否是内层，如select * from (select * from table group by)
			// 外层group by 必须要进行包裹(update by chenrenfei 2016-4-21)
			boolean isInnerGroup = false;
			if (groupIndex != -1) {
				isInnerGroup = clearDisturbSql(maskedSql.substring(groupIndex + 1)).lastIndexOf(")") != -1;
			}
			final StringBuilder countQueryStr = new StringBuilder();
			// 是否包含union,update 2024-2-1(改为每次都判断，避免极端情况)
			boolean hasUnion = SqlUtil.hasUnion(maskedSql, false);
			// 不包含distinct和group by 等,则剔除[select * ] from 变成select count(1) from
			// 性能最优
			if (!StringUtil.matches(maskedSql.trim(), DISTINCT_PATTERN) && !hasUnion
					&& (groupIndex == -1 || (groupIndex < lastBracketIndex && isInnerGroup))) {
				int selectIndex = StringUtil.matchIndex(maskedSql.toLowerCase(Locale.ROOT), SELECT_REGEX);
				// 截取出select 和from之间的语句(取掩码串,规避字面量内的count/sum等统计词误判)
				String selectFields = (sql_from_index < 1) ? ""
						: maskedSql.substring(selectIndex + 6, sql_from_index).toLowerCase(Locale.ROOT);
				// 剔除嵌套的子查询语句中select 和 from 之间的内容,便于判断统计函数的作用位置
				selectFields = clearSymSelectFromSql(selectFields);
				// 存在统计函数 update by chenrenfei ,date: 2017-2-24
				if (StringUtil.matches(selectFields, STAT_PATTERN)) {
					countQueryStr.append("select ").append(countPart).append(" from (").append(query_tmp)
							.append(") sag_count_tmpTable ");
				} else {
					// 截取from后的部分
					countQueryStr.append("select ").append(countPart)
							.append((sql_from_index != -1 ? query_tmp.substring(sql_from_index) : query_tmp));
				}
			} // 包含distinct 或包含union则直接将查询作为子表(普通做法)
			else {
				countQueryStr.append("select ").append(countPart).append(" from (").append(query_tmp)
						.append(") sag_count_tmpTable ");
			}
			boolean countBackslashEscape = SqlConfigParseUtils.isBackslashEscapeDialect(dbType);
			paramCnt = getParamsCount(countQueryStr.toString(), countBackslashEscape);
			withParamCnt = getParamsCount(withSql, countBackslashEscape);
			countQueryStr.insert(0, withSql + " ");
			lastCountSql = countQueryStr.toString();
		}
		Object[] realParamsTemp = paramsValue;
		if (realParamsTemp != null && !isLastSql) {
			// 剔除order by 语句中的参数对应的值
			if (orderByParamsCnt > 0) {
				realParamsTemp = CollectionUtil.subtractArray(realParamsTemp, realParamsTemp.length - orderByParamsCnt,
						orderByParamsCnt);
			}
			// 将select from之间语句中的参数剔除
			realParamsTemp = CollectionUtil.subtractArray(realParamsTemp, withParamCnt,
					realParamsTemp.length - withParamCnt - paramCnt);
		}
		final Object[] realParams = realParamsTemp;
		// 做sql签名
		lastCountSql = SqlUtilsExt.signSql(lastCountSql, dbType, sqlToyConfig);
		// 打印sql
		SqlExecuteStat.showSql("execute count query", lastCountSql, realParams);
		PreparedStatement pst = conn.prepareStatement(lastCountSql);
		// 设置查询超时时长(秒)
		if (extend != null && extend.timeout != null && extend.timeout > 0) {
			pst.setQueryTimeout(extend.timeout);
		} else if (SqlToyConstants.defaultStatementTimeout != null && SqlToyConstants.defaultStatementTimeout > 0) {
			pst.setQueryTimeout(SqlToyConstants.defaultStatementTimeout);
		}
		ResultSet rs = null;
		return (Long) SqlUtil.preparedStatementProcess(null, pst, rs, new PreparedStatementResultHandler() {
			@Override
			public void execute(Object obj, PreparedStatement pst, ResultSet rs) throws SQLException, IOException {
				long resultCount = 0;
				try {
					if (realParams != null) {
						SqlUtil.setParamsValue(sqlToyContext.getTypeHandler(), conn, dbType, pst, realParams, null, 0);
					}
					rs = pst.executeQuery();
					if (rs.next()) {
						resultCount = rs.getLong(1);
					}
					this.setResult(resultCount);
				} catch (Exception e) {
					throw e;
				} finally {
					if (rs != null) {
						rs.close();
						rs = null;
					}
				}
			}
		});
	}

	/**
	 * 统一将查询的sql参数由?形式变成:named形式(分页和查询随机记录时)
	 * 
	 * @param sqlToyContext
	 * @param sqlToyConfig
	 * @param queryExecutor
	 * @param dialect
	 * @param wrapNamed     只在分页场景下需要将?模式传参统一成:name模式，便于跟后面分页startIndex和endIndex参数结合，从而利用sql预编译功能
	 * @return
	 * @throws Exception
	 */
	public static SqlToyConfig getUnifyParamsNamedConfig(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig,
			QueryExecutor queryExecutor, String dialect, boolean wrapNamed) throws Exception {
		QueryExecutorExtend extend = queryExecutor.getInnerModel();
		// 本身就是:named参数形式或sql中没有任何参数
		boolean isNamed = false;
		// 在QueryExecutorBuilder中已经对wrappedParamNames做了判断赋值
		if (!extend.wrappedParamNames) {
			// sql中是否存在? 形式参数
			boolean hasQuestArg = SqlConfigParseUtils.hasQuestMarkArgs(sqlToyConfig.getSql());
			isNamed = ((extend.paramsName != null && extend.paramsName.length > 0) || !hasQuestArg);
		}
		// 以queryExecutor自定义的分表策略覆盖sql xml中定义的
		List<ShardingStrategyConfig> tableShardings = sqlToyConfig.getTableShardings();
		if (!extend.tableShardings.isEmpty()) {
			tableShardings = extend.tableShardings;
		}
		// sql条件以:named形式、无分表、无扩展缓存翻译则不存在对SqlToyConfig 内容的修改，直接返回
		if ((isNamed || !wrapNamed) && tableShardings.isEmpty() && extend.translates.isEmpty()
				&& extend.linkModel == null) {
			return sqlToyConfig;
		}
		// clone sqltoyConfig避免直接修改原始的sql配置对后续执行产生影响
		SqlToyConfig result = sqlToyConfig.clone();
		// 存在扩展的缓存翻译
		if (!extend.translates.isEmpty()) {
			HashMap<String, FieldTranslate> transMap = result.getTranslateMap();
			// colName自动已经小写，无需再处理
			extend.translates.forEach((translate) -> {
				String colName = translate.getExtend().column.toLowerCase(Locale.ROOT);
				if (transMap.containsKey(colName)) {
					transMap.get(colName).put(translate);
				} else {
					FieldTranslate fieldTranslate = new FieldTranslate();
					fieldTranslate.colName = colName;
					fieldTranslate.put(translate);
					transMap.put(colName, fieldTranslate);
				}
			});
			result.setTranslateMap(transMap);
		}
		// 扩展的link计算
		if (extend.linkModel != null) {
			result.setLinkModel(extend.linkModel);
		}
		// ?传参且分页模式,原因是分页存在取count场景，在@fast()情况下无法断定paramValues的值跟?的参数对应关系
		if (!isNamed && wrapNamed) {
			SqlParamsModel sqlParams;
			// 将?形式的参数替换成:named形式参数
			// 存在fast查询
			if (result.isHasFast()) {
				// @fast 前部分
				String fastPreSql = SqlConfigParseUtils.clearDblQuestMark(result.getFastPreSql(null));
				sqlParams = convertParamsToNamed(fastPreSql, 0);
				fastPreSql = SqlConfigParseUtils.recoverDblQuestMark(sqlParams.getSql());
				result.setFastPreSql(fastPreSql);
				int index = sqlParams.getParamCnt();
				// @fas() 中间部分
				String fastSql = SqlConfigParseUtils.clearDblQuestMark(result.getFastSql(null));
				sqlParams = convertParamsToNamed(fastSql, index);
				fastSql = SqlConfigParseUtils.recoverDblQuestMark(sqlParams.getSql());
				result.setFastSql(fastSql);
				index = index + sqlParams.getParamCnt();
				// 尾部
				String tailSql = SqlConfigParseUtils.clearDblQuestMark(result.getFastTailSql(null));
				sqlParams = convertParamsToNamed(tailSql, index);
				tailSql = SqlConfigParseUtils.recoverDblQuestMark(sqlParams.getSql());
				result.setFastTailSql(tailSql);
				// 完整sql
				result.setSql(fastPreSql.concat(" (").concat(fastSql).concat(") ").concat(tailSql));
				// 构造对应?参数个数的:named模式参数名数组
				String[] paramsName = new String[index];
				for (int i = 0; i < index; i++) {
					paramsName[i] = SqlToyConstants.DEFAULT_PARAM_NAME + (i + 1);
				}
				result.setParamsName(paramsName);
			} else {
				sqlParams = convertParamsToNamed(SqlConfigParseUtils.clearDblQuestMark(result.getSql(null)), 0);
				result.setSql(SqlConfigParseUtils.recoverDblQuestMark(sqlParams.getSql()));
				result.setParamsName(sqlParams.getParamsName());
			}
			// 自定义分页的count sql，一般无需定义
			sqlParams = convertParamsToNamed(SqlConfigParseUtils.clearDblQuestMark(result.getCountSql(null)), 0);
			result.setCountSql(SqlConfigParseUtils.recoverDblQuestMark(sqlParams.getSql()));
			// 清空方言缓存
			result.clearDialectSql();
			SqlConfigParseUtils.processFastWith(result, dialect);
		}
		// sharding table 替换sql中的表名称
		ShardingUtils.replaceShardingSqlToyConfig(sqlToyContext, result, tableShardings, dialect,
				extend.getTableShardingParamsName(), extend.getTableShardingParamsValue());
		return result;
	}

	/**
	 * update 2020-08-15 增强对非条件参数?的判断处理
	 * 
	 * sql中替换?为:sagParamName+i形式,便于查询处理(主要针对分页和取随机记录的查询)
	 * 
	 * @param sql
	 * @param startIndex
	 * @return
	 */
	public static SqlParamsModel convertParamsToNamed(String sql, int startIndex) {
		SqlParamsModel sqlParam = new SqlParamsModel();
		if (sql == null || "".equals(sql.trim())) {
			return sqlParam;
		}
		// 以?号对字符串进行切割，并忽视'' 和"" 之间的
		String[] strs = StringUtil.splitExcludeSymMark(sql, SqlConfigParseUtils.ARG_NAME, QuesFilters);
		int size = strs.length;
		if (size == 1) {
			sqlParam.setSql(sql);
			return sqlParam;
		}
		String preName = SqlToyConstants.DEFAULT_PARAM_NAME;
		StringBuilder result = new StringBuilder();
		String[] paramsName = new String[size - 1];
		int index;
		for (int i = 0; i < size - 1; i++) {
			index = i + startIndex + 1;
			result.append(strs[i]).append(":" + preName + index);
			paramsName[i] = preName + index;
		}
		result.append(strs[size - 1]);
		sqlParam.setSql(result.toString());
		sqlParam.setParamsName(paramsName);
		sqlParam.setParamCnt(size - 1);
		return sqlParam;
	}

	/**
	 * 执行批量保存或修改操作
	 * 
	 * @param sqlToyContext
	 * @param entities
	 * @param batchSize
	 * @param entityMeta
	 * @param forceUpdateFields
	 * @param generateSqlHandler
	 * @param reflectPropsHandler
	 * @param conn
	 * @param dbType
	 * @param autoCommit
	 * @return
	 * @throws Exception
	 */
	public static Long saveOrUpdateAll(SqlToyContext sqlToyContext, List<?> entities, final int batchSize,
			EntityMeta entityMeta, String[] forceUpdateFields, GenerateSqlHandler generateSqlHandler,
			ReflectPropsHandler reflectPropsHandler, Connection conn, final Integer dbType, Boolean autoCommit)
			throws Exception {
		// 重新构造修改或保存的属性赋值反调
		ReflectPropsHandler handler = getSaveOrUpdateReflectHandler(entityMeta.getIdArray(), reflectPropsHandler,
				forceUpdateFields, sqlToyContext.getUnifyFieldsHandler());
		// 字段加密处理
		handler = getSecureReflectHandler(handler, sqlToyContext.getFieldsSecureProvider(),
				sqlToyContext.getDesensitizeProvider(), entityMeta.getSecureFields());
		List<Object[]> paramValues = BeanUtil.reflectBeansToInnerAry(entities, entityMeta.getFieldsArray(true), null,
				handler);
		int generatedColCnt = entityMeta.getGeneratedColsCnt();
		int pkIndex = entityMeta.getIdIndex() - generatedColCnt;
		// 是否存在业务ID
		boolean hasBizId = (entityMeta.getBusinessIdGenerator() == null) ? false : true;
		int bizIdColIndex = hasBizId ? entityMeta.getFieldIndex(entityMeta.getBusinessIdField()) - generatedColCnt : 0;
		// 标识符
		String signature = entityMeta.getBizIdSignature();
		Integer[] relatedColumn = entityMeta.getBizIdRelatedColIndex();
		int relatedColumnSize = (relatedColumn == null) ? 0 : relatedColumn.length;
		boolean hasId = (null != entityMeta.getIdStrategy() && null != entityMeta.getIdGenerator()) ? true : false;
		// 生成主键、业务主键值，并回写填充到POJO，便于前端获取(saveOrUpdate不回写数据版本号的值)
		if (hasId || hasBizId) {
			int bizIdLength = entityMeta.getBizIdLength();
			int idLength = entityMeta.getIdLength();
			Object[] rowData;
			Object[] relatedColValue = null;
			String idJdbcType = entityMeta.getIdType();
			String businessIdType = hasBizId ? entityMeta.getColumnJavaType(entityMeta.getBusinessIdField()) : "";
			for (int i = 0, end = paramValues.size(); i < end; i++) {
				rowData = (Object[]) paramValues.get(i);
				// 获取主键策略关联字段的值
				if (relatedColumn != null) {
					relatedColValue = new Object[relatedColumnSize];
					for (int meter = 0; meter < relatedColumnSize; meter++) {
						relatedColValue[meter] = rowData[relatedColumn[meter] - generatedColCnt];
					}
					// 这里不做是否为null的校验，因为不明确是新增还是修改
				}
				// 主键
				if (hasId && StringUtil.isBlank(rowData[pkIndex])) {
					rowData[pkIndex] = entityMeta.getIdGenerator().getId(entityMeta.getTableName(), signature,
							entityMeta.getBizIdRelatedColumns(), relatedColValue, null, idJdbcType, idLength,
							entityMeta.getBizIdSequenceSize());
					// 回写主键值
					BeanUtil.setProperty(entities.get(i), entityMeta.getIdArray()[0], rowData[pkIndex]);
				}
				// 业务主键
				if (hasBizId && StringUtil.isBlank(rowData[bizIdColIndex])) {
					rowData[bizIdColIndex] = entityMeta.getBusinessIdGenerator().getId(entityMeta.getTableName(),
							signature, entityMeta.getBizIdRelatedColumns(), relatedColValue, null, businessIdType,
							bizIdLength, entityMeta.getBizIdSequenceSize());
					// 回写业务主键值
					BeanUtil.setProperty(entities.get(i), entityMeta.getBusinessIdField(), rowData[bizIdColIndex]);
				}
			}
		}
		// 批量回写saveOrUpdate公共属性值
		BeanUtil.batchBackWriteUnifyFields(entities,
				BeanUtil.getUnifyFieldIndex(sqlToyContext.getUnifyFieldsHandler(), entityMeta.getFieldsArray(true), 3),
				paramValues);
		String saveOrUpdateSql = generateSqlHandler.generateSql(entityMeta, forceUpdateFields);
		List<Object[]> realParams = paramValues;
		String realSql = saveOrUpdateSql;
		if (sqlToyContext.hasSqlInterceptors()) {
			SqlToyConfig sqlToyConfig = new SqlToyConfig(DataSourceUtils.getDialect(dbType));
			sqlToyConfig.setSqlType(SqlType.insert);
			sqlToyConfig.setSql(saveOrUpdateSql);
			sqlToyConfig.setParamsName(entityMeta.getFieldsArray(true));
			SqlToyResult sqlToyResult = new SqlToyResult(saveOrUpdateSql, paramValues.toArray());
			sqlToyResult = doInterceptors(sqlToyContext, sqlToyConfig, OperateType.saveOrUpdate, sqlToyResult,
					entities.get(0).getClass(), dbType);
			realSql = sqlToyResult.getSql();
			realParams = CollectionUtil.arrayToList(sqlToyResult.getParamsValue());
		}
		SqlExecuteStat.showSql("execute saveOrUpdate sql", realSql, null);
		final Integer[] updateTypes = entityMeta.getFieldsTypeArray(true);
		InsertRowCallbackHandler bindHandler = null;
		// sqlserver的timestamp(rowversion)列不能赋值,merge/insert语句生成时已排除该列,绑定时同步跳过对应参数保持占位符对齐
		// update 2026-9-5
		// 判据改按目标库元数据校准(entityMeta.isRowVersionField),规避oracle迁移场景误伤datetime2业务列
		if (dbType != null && dbType.intValue() == DBType.SQLSERVER) {
			// 参数按fieldsArray(true)反射,与fieldMeta一一对应,预计算跳过标志
			final String[] bindFields = entityMeta.getFieldsArray(true);
			final boolean[] skipBind = new boolean[bindFields.length];
			boolean hasSkip = false;
			for (int i = 0; i < bindFields.length; i++) {
				skipBind[i] = entityMeta.isRowVersionField(entityMeta.getFieldMeta(bindFields[i]));
				hasSkip = hasSkip || skipBind[i];
			}
			// 全部字段可绑定时走默认绑定,保持行为不变
			if (hasSkip) {
				bindHandler = (pst, index, rowData) -> {
					Object[] rowValues = CollectionUtil.convertArray(rowData);
					int bindIndex = 0;
					for (int i = 0; i < rowValues.length; i++) {
						if (!skipBind[i]) {
							try {
								SqlUtil.setParamValue(sqlToyContext.getTypeHandler(), conn, dbType, pst, rowValues[i],
										updateTypes[i], ++bindIndex);
							} catch (IOException e) {
								throw new SQLException(
										"sqlserver failed to bind parameters when executing saveOrUpdate!", e);
							}
						}
					}
				};
			}
		}
		return SqlUtil.batchUpdateByJdbc(sqlToyContext.getTypeHandler(), realSql, realParams, batchSize, bindHandler,
				updateTypes, autoCommit, conn, dbType);
	}

	/**
	 * 处理加工对象基于db2、oracle数据库的saveOrUpdateSql
	 * 
	 * @param sqlToyContext
	 * @param unifyFieldsHandler
	 * @param dbType
	 * @param entityMeta
	 * @param pkStrategy
	 * @param forceUpdateFields
	 * @param fromTable
	 * @param isNullFunction
	 * @param sequence
	 * @param isAssignPK
	 * @param tableName
	 * @return
	 */
	public static String getSaveOrUpdateSql(SqlToyContext sqlToyContext, IUnifyFieldsHandler unifyFieldsHandler,
			Integer dbType, EntityMeta entityMeta, PKStrategy pkStrategy, String[] forceUpdateFields, String fromTable,
			String isNullFunction, String sequence, boolean isAssignPK, String tableName) {
		String realTable = entityMeta.getSchemaTable(tableName, dbType);
		// postgresql15+ 不支持别名,目标表列限定只能用不带schema的表名(schema.table.col三段式列引用非法)
		String pgNoSchemaTable = ReservedWordsUtil
				.convertWord((StringUtil.isBlank(tableName)) ? entityMeta.getTableName() : tableName, dbType);
		if (entityMeta.getSchema() != null && pgNoSchemaTable.startsWith(entityMeta.getSchema().concat("."))) {
			pgNoSchemaTable = pgNoSchemaTable.substring(entityMeta.getSchema().length() + 1);
		}
		// 在无主键的情况下产生insert sql语句
		if (entityMeta.getIdArray() == null) {
			return DialectExtUtils.generateInsertSql(unifyFieldsHandler, dbType, entityMeta, pkStrategy, isNullFunction,
					sequence, isAssignPK, realTable);
		}
		// 将新增记录统一赋值属性模拟成默认值模式
		IgnoreKeyCaseMap<String, Object> createUnifyFields = null;
		if (unifyFieldsHandler != null && unifyFieldsHandler.createUnifyFields() != null
				&& !unifyFieldsHandler.createUnifyFields().isEmpty()) {
			createUnifyFields = new IgnoreKeyCaseMap<String, Object>();
			createUnifyFields.putAll(unifyFieldsHandler.createUnifyFields());
		}
		// 创建记录时，创建时间、最后修改时间等取数据库时间
		IgnoreCaseSet createSqlTimeFields = (unifyFieldsHandler == null
				|| unifyFieldsHandler.createSqlTimeFields() == null) ? new IgnoreCaseSet()
						: unifyFieldsHandler.createSqlTimeFields();
		// 修改记录时，最后修改时间等取数据库时间
		IgnoreCaseSet updateSqlTimeFields = (unifyFieldsHandler == null
				|| unifyFieldsHandler.updateSqlTimeFields() == null) ? new IgnoreCaseSet()
						: unifyFieldsHandler.updateSqlTimeFields();
		IgnoreCaseSet forceUpdateSqlTimeFields = (unifyFieldsHandler == null
				|| unifyFieldsHandler.forceUpdateFields() == null) ? new IgnoreCaseSet()
						: unifyFieldsHandler.forceUpdateFields();
		String currentTimeStr;
		String[] fieldsArray = entityMeta.getFieldsArray(true);
		int columnSize = fieldsArray.length;
		StringBuilder sql = new StringBuilder(columnSize * 30 + 100);
		String columnName;
		sql.append("merge into ");
		sql.append(realTable);
		// postgresql15+ 在set 环节不支持别名
		if (DBType.POSTGRESQL != dbType) {
			sql.append(" ta ");
		}
		sql.append(" using (select ");
		FieldMeta fieldMeta;
		// update 2026-9-7 实际输出列计数:sqlserver的rowversion列跳过输出后,逗号按实际输出列组织
		int usingCols = 0;
		for (int i = 0; i < columnSize; i++) {
			fieldMeta = entityMeta.getFieldMeta(fieldsArray[i]);
			// update 2026-9-7 sqlserver的timestamp(rowversion)列不可写入,merge各环节整体排除
			// (判据entityMeta.isRowVersionField按目标库元数据校准,非sqlserver库不启用,自SqlServerDialectUtils迁入)
			if (DBType.SQLSERVER == dbType && entityMeta.isRowVersionField(fieldMeta)) {
				continue;
			}
			columnName = ReservedWordsUtil.convertWord(fieldMeta.getColumnName(), dbType);
			if (usingCols > 0) {
				sql.append(",");
			}
			// postgresql15+ 需要case(? as type) as column
			// postgresql14 不走merge into
			if (DBType.POSTGRESQL == dbType || DBType.KINGBASE == dbType) {
				PostgreSqlDialectUtils.wrapSelectFields(sql, columnName, fieldMeta);
			} else if (DBType.GAUSSDB == dbType || DBType.OPENGAUSS == dbType || DBType.MOGDB == dbType
					|| DBType.VASTBASE == dbType || DBType.STARDB == dbType || DBType.OSCAR == dbType) {
				OpenGaussDialectUtils.wrapSelectFields(sql, columnName, fieldMeta, dbType);
			} else if (DBType.H2 == dbType) {
				H2DialectUtils.wrapSelectFields(sql, columnName, fieldMeta);
			} else if (DBType.DB2 == dbType) {
				DB2DialectUtils.wrapSelectFields(sql, columnName, fieldMeta);
			} else if (DBType.DM == dbType) {
				DMDialectUtils.wrapSelectFields(sql, columnName, fieldMeta);
			} else {
				// sqlserver(2008+)、oracle(23ai)支持vector类型;sqlserver(2008+)支持geometry类型
				// merge into的using select子查询中显式cast保证类型正确
				// update 2026-9-6 oracle geometry改由驱动层SDO STRUCT绑定(见SqlUtil.setGeometryValue),
				// SQL层SDO_UTIL.FROM_WKTGEOMETRY包装在null参数时报ORA-29532,故还原为普通?占位
				// update 2026-9-7 sqlserver VECTOR同样还原为裸?:实测2025版cast(? as VECTOR)缺维度报
				// "not a defined system type",而字符串到vector为隐式转换(自SqlServerDialectUtils迁入)
				int extType = fieldMeta.getType();
				// 统一模式:先按数据类型分支,再在分支内部按数据库方言处理(与generateInsertSql保持一致)
				if (extType == JdbcTypes.GEOMETRY) {
					if (DBType.SQLSERVER == dbType) {
						sql.append("cast(? as geometry)");
					} else {
						// oracle由驱动层SDO STRUCT绑定(见SqlUtil.setGeometryValue),维持裸?
						sql.append("?");
					}
				} else if (extType == JdbcTypes.VECTOR) {
					// update 2026-9-7 实测sqlserver 2025的cast(? as VECTOR)缺维度报
					// "not a defined system type",而字符串到vector为隐式转换,维持裸?
					sql.append("?");
				} else if (extType == JdbcTypes.JSON || extType == JdbcTypes.JSONB) {
					// update 2026-9-10 移除服务器级convert(json,?)门控,统一裸?:2025 GA实测
					// 裸?(按nvarchar传递)+matched update的coalesce包裹对原生json列与nvarchar
					// 承载列双形态均成立(见下方update环节分派);convert(json,?)产物为json类型值,
					// 赋给GA服务器上的存量nvarchar承载列反报"Implicit conversion json to
					// nvarchar not allowed"(服务器级探测无法区分列级承载形态,门控废弃)
					sql.append("?");
				} else {
					sql.append("?");
				}
				sql.append(" as ");
				sql.append(columnName);
			}
			usingCols++;
		}
		if (StringUtil.isNotBlank(fromTable)) {
			sql.append(" from ").append(fromTable);
		}
		// sql.append(") tv on (");
		sql.append(SqlToyConstants.MERGE_ALIAS_ON);
		StringBuilder idColumns = new StringBuilder();
		// 组织on部分的主键条件判断
		for (int i = 0, n = entityMeta.getIdArray().length; i < n; i++) {
			columnName = entityMeta.getColumnName(entityMeta.getIdArray()[i]);
			columnName = ReservedWordsUtil.convertWord(columnName, dbType);
			if (i > 0) {
				sql.append(" and ");
				idColumns.append(",");
			}
			// 不支持别名
			if (DBType.POSTGRESQL == dbType) {
				sql.append(pgNoSchemaTable + ".");
			} else {
				sql.append("ta.");
			}
			sql.append(columnName).append("=tv.").append(columnName);
			idColumns.append("ta.").append(columnName);
		}
		sql.append(" ) ");
		// 排除id的其他字段信息
		StringBuilder insertRejIdCols = new StringBuilder();
		StringBuilder insertRejIdColValues = new StringBuilder();
		String[] rejectIdFieldArray = entityMeta.getRejectIdFieldArray(true);
		// 是否全部是ID,匹配上则无需进行更新，只需将未匹配上的插入即可
		boolean allIds = (rejectIdFieldArray == null);
		// update 2024-7-21 增加sqlInterceptor场景下，merge into on (条件) update 需要跳过on 中存在的字段
		// (sqlToyContext为null时为第三方直调工具类的兼容路径,无拦截器可取)
		IgnoreCaseSet tenantFields = (sqlToyContext == null) ? new IgnoreCaseSet()
				: getTenantFields(sqlToyContext.getSqlInterceptors(), entityMeta, OperateType.saveOrUpdate);
		if (!allIds) {
			// update 操作
			sql.append(SqlToyConstants.MERGE_UPDATE);
			int rejectIdColumnSize = rejectIdFieldArray.length;
			// 需要被强制修改的字段
			HashSet<String> fupc = new HashSet<String>();
			if (forceUpdateFields != null) {
				for (String field : forceUpdateFields) {
					fupc.add(ReservedWordsUtil.convertWord(entityMeta.getColumnName(field), dbType));
				}
			}
			String defaultValue;
			boolean ignoreUpdate = false;
			boolean notFirst = false;
			int insCols = 0;
			// update 只针对非主键字段进行修改
			for (int i = 0; i < rejectIdColumnSize; i++) {
				fieldMeta = entityMeta.getFieldMeta(rejectIdFieldArray[i]);
				// update 2026-9-7
				// sqlserver的rowversion列在update/insert环节均不参与(自SqlServerDialectUtils迁入)
				if (DBType.SQLSERVER == dbType && entityMeta.isRowVersionField(fieldMeta)) {
					continue;
				}
				columnName = ReservedWordsUtil.convertWord(fieldMeta.getColumnName(), dbType);
				ignoreUpdate = tenantFields.contains(fieldMeta.getFieldName());
				// 修改字段
				currentTimeStr = SqlUtil.getDBTime(dbType, fieldMeta, updateSqlTimeFields);
				// 被统一sqlInterceptor中on 中增加了条件，update不能修改
				if (!ignoreUpdate) {
					if (notFirst) {
						sql.append(",");
					}
					// postgresql15+ 不支持别名
					if (DBType.POSTGRESQL != dbType) {
						sql.append(" ta.");
					}
					sql.append(columnName).append("=");
					if (null != currentTimeStr && forceUpdateSqlTimeFields.contains(fieldMeta.getFieldName())) {
						sql.append(currentTimeStr);
					} else if (fupc.contains(columnName)) {
						sql.append("tv.").append(columnName);
					} else {
						// update 2026-9-8 oracle的SDO_GEOMETRY换full镜像实测:using select裸?
						// 以驱动STRUCT绑定(MDSYS.SDO_GEOMETRY)定型后,nvl(tv.g,ta.g)同型可行,
						// 非null更新生效、null参数保留原值(弹性语义完整),统一纳入nvl包裹
						// 注意逻辑，nvl(tv.field,ta.field) 不要改变
						// update 2026-9-10 sqlserver的JSON/JSONB列改coalesce包裹(与getUpdateSql的
						// SQLSERVER+JSON分派同款):isnull取首参类型(tv侧裸?按nvarchar传递),原生json列
						// 报"Implicit conversion json to nvarchar not allowed";coalesce按类型优先级
						// 解析,2025 GA实测裸?+coalesce对原生json列与nvarchar承载列双形态均为
						// 更新生效、null保留原值
						if (DBType.SQLSERVER == dbType
								&& (fieldMeta.getType() == JdbcTypes.JSON || fieldMeta.getType() == JdbcTypes.JSONB)) {
							sql.append("coalesce");
						} else {
							sql.append(isNullFunction);
						}
						if (DBType.SQLSERVER == dbType && fieldMeta.getType() == java.sql.Types.DECIMAL) {
							// update 2026-9-7 sqlserver经isnull赋值会丢decimal小数位,cast定长保精度
							// (自SqlServerDialectUtils迁入)
							int decimalLength = (fieldMeta.getLength() > 35) ? fieldMeta.getLength() : 35;
							int decimalScale = (fieldMeta.getScale() > 5) ? fieldMeta.getScale() : 5;
							sql.append("(cast(tv.").append(columnName)
									.append(" as decimal(" + decimalLength + "," + decimalScale + "))");
						} else {
							sql.append("(tv.").append(columnName);
						}
						sql.append(",");
						if (null != currentTimeStr) {
							sql.append(currentTimeStr);
						} else {
							// postgresql15 set环节不支持别名
							if (DBType.POSTGRESQL == dbType) {
								sql.append(pgNoSchemaTable + ".");
							} else {
								sql.append("ta.");
							}
							sql.append(columnName);
						}
						sql.append(")");
					}
					notFirst = true;
				}
				if (insCols > 0) {
					insertRejIdCols.append(",");
					insertRejIdColValues.append(",");
				}
				insertRejIdCols.append(columnName);
				insCols++;
				// 新增
				currentTimeStr = SqlUtil.getDBTime(dbType, fieldMeta, createSqlTimeFields);
				if (null != currentTimeStr && forceUpdateSqlTimeFields.contains(fieldMeta.getFieldName())) {
					insertRejIdColValues.append(currentTimeStr);
				} else {
					// 将创建人、创建时间等模拟成默认值
					defaultValue = DialectExtUtils.getInsertDefaultValue(createUnifyFields, dbType, fieldMeta);
					// 存在默认值
					if (null != defaultValue) {
						insertRejIdColValues.append(isNullFunction);
						insertRejIdColValues.append("(tv.").append(columnName).append(",");
						DialectExtUtils.processDefaultValue(insertRejIdColValues, dbType, fieldMeta, defaultValue);
						insertRejIdColValues.append(")");
					} else {
						if (null != currentTimeStr) {
							insertRejIdColValues.append(isNullFunction);
							insertRejIdColValues.append("(tv.").append(columnName).append(",");
							insertRejIdColValues.append(currentTimeStr);
							insertRejIdColValues.append(")");
						} else {
							insertRejIdColValues.append("tv.").append(columnName);
						}
					}
				}
			}
		}
		// 主键未匹配上则进行插入操作
		sql.append(SqlToyConstants.MERGE_INSERT);
		sql.append(" (");
		String idsColumnStr = idColumns.toString();
		// 不考虑只有一个字段且还是主键的情况
		if (allIds) {
			sql.append(idsColumnStr.replace("ta.", ""));
			sql.append(") values (");
			sql.append(idsColumnStr.replace("ta.", "tv."));
		} else {
			sql.append(insertRejIdCols.toString());
			// sequence方式主键(pkStrategy为null时@Id未配strategy/generator,判空防御避免NPE)
			if (PKStrategy.SEQUENCE.equals(pkStrategy)) {
				columnName = entityMeta.getColumnName(entityMeta.getIdArray()[0]);
				columnName = ReservedWordsUtil.convertWord(columnName, dbType);
				sql.append(",");
				sql.append(columnName);
				sql.append(") values (");
				sql.append(insertRejIdColValues).append(",");
				if (isAssignPK) {
					sql.append(isNullFunction);
					sql.append("(tv.").append(columnName).append(",");
					sql.append(sequence).append(") ");
				} else {
					sql.append(sequence);
				}
			} else if (PKStrategy.IDENTITY.equals(pkStrategy)) {
				columnName = entityMeta.getColumnName(entityMeta.getIdArray()[0]);
				columnName = ReservedWordsUtil.convertWord(columnName, dbType);
				if (isAssignPK) {
					sql.append(",");
					sql.append(columnName);
				}
				sql.append(") values (");
				// identity 模式insert无需写插入该字段语句
				sql.append(insertRejIdColValues);
				if (isAssignPK) {
					sql.append(",").append("tv.").append(columnName);
				}
			} else {
				sql.append(",");
				sql.append(idsColumnStr.replace("ta.", ""));
				sql.append(") values (");
				sql.append(insertRejIdColValues).append(",");
				sql.append(idsColumnStr.replace("ta.", "tv."));
			}
		}
		sql.append(")");
		return sql.toString();
	}

	/**
	 * 获取参与条件过滤的租户字段
	 * 
	 * @param sqlInterceptors
	 * @param entityMeta
	 * @param operateType
	 * @return
	 */
	public static IgnoreCaseSet getTenantFields(List<SqlInterceptor> sqlInterceptors, EntityMeta entityMeta,
			OperateType operateType) {
		IgnoreCaseSet result = new IgnoreCaseSet();
		if (sqlInterceptors == null || sqlInterceptors.isEmpty()) {
			return result;
		}
		String[] fields;
		for (SqlInterceptor sqlInterceptor : sqlInterceptors) {
			fields = sqlInterceptor.tenantFieldNames(entityMeta, operateType);
			if (fields != null) {
				for (String str : fields) {
					if (null != str) {
						result.add(str);
					}
				}
			}
		}
		return result;
	}

	/**
	 * 产生对象update的语句
	 * 
	 * @param unifyFieldsHandler
	 * @param dbType
	 * @param entityMeta
	 * @param nullFunction
	 * @param forceUpdateFields
	 * @param tableName          已经增加了schema
	 * @return
	 */
	private static String generateUpdateSql(IUnifyFieldsHandler unifyFieldsHandler, Integer dbType,
			EntityMeta entityMeta, String nullFunction, String[] forceUpdateFields, String tableName) {
		if (entityMeta.getIdArray() == null) {
			return null;
		}
		StringBuilder sql = new StringBuilder(entityMeta.getFieldsArray(true).length * 30 + 30);
		sql.append(" update  ");
		// 已经增加了schema
		sql.append(tableName);
		sql.append(" set ");
		String columnName;
		// 需要被强制修改的字段
		HashSet<String> fupc = new HashSet<String>();
		if (forceUpdateFields != null) {
			for (String field : forceUpdateFields) {
				fupc.add(ReservedWordsUtil.convertWord(entityMeta.getColumnName(field), dbType));
			}
		}
		FieldMeta fieldMeta;
		IgnoreCaseSet updateSqlTimeFields = new IgnoreCaseSet();
		if (unifyFieldsHandler != null && unifyFieldsHandler.updateSqlTimeFields() != null) {
			updateSqlTimeFields = unifyFieldsHandler.updateSqlTimeFields();
		}
		// kingbase/gaussdb同为pg内核,bytea未定型参数同样需要cast(2023-06-11误删,现恢复)
		boolean convertBlob = (dbType == DBType.POSTGRESQL || dbType == DBType.POSTGRESQL14 || dbType == DBType.GAUSSDB
				|| dbType == DBType.KINGBASE);
		boolean isMSsql = (dbType == DBType.SQLSERVER);
		int meter = 0;
		int decimalLength;
		int decimalScale;
		String currentTimeStr;
		String[] rejectIdFieldArray = entityMeta.getRejectIdFieldArray(true);
		for (int i = 0, n = rejectIdFieldArray.length; i < n; i++) {
			fieldMeta = entityMeta.getFieldMeta(rejectIdFieldArray[i]);
			// 排除sqlserver timestamp类型(update 2026-9-5
			// 判据改按目标库元数据校准,规避oracle迁移场景误伤datetime2业务列)
			if (!(isMSsql && entityMeta.isRowVersionField(fieldMeta))) {
				columnName = ReservedWordsUtil.convertWord(fieldMeta.getColumnName(), dbType);
				if (meter > 0) {
					sql.append(",");
				}
				sql.append(columnName);
				sql.append("=");
				// 判断强制修改
				if (fupc.contains(columnName)) {
					sql.append("?");
				} else {
					String defaultColName = columnName;
					// 注意这里nvl的逻辑是数据弹性修改，请不要随意去除
					// update 2026-9-7 实测部分库的nvl/isnull对geometry/json类型对物理不支持
					// (dm -5403参数不兼容/db2 -408值转换失败/ob 5083 Invalid data type/
					// sqlserver udt转换不支持),这些库组合以coalesce包裹替代(实测coalesce接受
					// geometry/json类型对,null不覆盖原值语义完整保持);其余库沿用nullFunction
					String wrapFunc = nullFunction;
					// 直赋形态(不包裹null保持函数):部分库扩展类型无法参与类型合并
					boolean directAssign = false;
					if (fieldMeta.getType() == JdbcTypes.GEOMETRY
							&& (dbType == DBType.DM || dbType == DBType.DB2 || dbType == DBType.OCEANBASE
									|| DialectExtUtils.isOceanBaseAsMysql() || dbType == DBType.SQLSERVER)) {
						wrapFunc = "coalesce";
					} else if ((fieldMeta.getType() == JdbcTypes.JSON || fieldMeta.getType() == JdbcTypes.JSONB)
							&& dbType == DBType.SQLSERVER) {
						wrapFunc = "coalesce";
					} else if (fieldMeta.getType() == JdbcTypes.VECTOR
							&& (dbType == DBType.OCEANBASE || DialectExtUtils.isOceanBaseAsMysql())) {
						// update 2026-9-10 ob 4.3.5实测vector列(ob内部为array类型)null保持包裹
						// 全形态失败:coalesce(?,col)/coalesce(col,?)报"merge array with other type
						// is not supported"(1235)、ifnull(col,?)报5083 Invalid data type、
						// case when同报merge array、cast(? as vector)语法不支持——唯一可行形态为
						// 直赋col=?(null将覆盖原值,ob vector更新的能力边界,与geometry/json的
						// coalesce可行结论不同)
						directAssign = true;
					}
					// 强调:这里必须要加类似nvl(?,col)模式，逻辑就是弹性修改(directAssign直赋形态除外)
					if (!directAssign) {
						sql.append(wrapFunc).append("(");
					}
					// 统一模式:先按数据类型分支,类型内再按数据库分派
					if (fieldMeta.getType() == JdbcTypes.GEOMETRY) {
						// geometry: 函数名按库分派;ob的ifnull对geometry类型对拒绝(5083),
						// 用coalesce包裹(实测coalesce+ST_GeomFromText参数化null保持可行)
						if (dbType == DBType.DM) {
							sql.append("DMGEO.ST_GeomFromText(?,0)");
						} else if (dbType == DBType.DB2) {
							// update 2026-9-10 按GSE探测+nativeType分派(12.1起内置引擎与GSE可并存,
							// 纯版本分派会在GSE列上误选内置函数报-408),详见db2GeomFromTextWrap
							sql.append(DialectExtUtils.db2GeomFromTextWrap(fieldMeta));
						} else if (dbType == DBType.SQLSERVER) {
							sql.append("geometry::STGeomFromText(?,0)");
						} else if (dbType == DBType.OCEANBASE) {
							sql.append("ST_GeomFromText(?,0)");
						} else if (dbType == DBType.MYSQL || dbType == DBType.MYSQL57 || dbType == DBType.TIDB) {
							sql.append("ST_GeomFromText(?,0)");
						} else if (dbType == DBType.KINGBASE) {
							sql.append("cast(? as geometry)");
						} else {
							sql.append("?");
						}
					} else if (fieldMeta.getType() == JdbcTypes.VECTOR) {
						if (dbType == DBType.OCEANBASE || DialectExtUtils.isOceanBaseAsMysql()) {
							sql.append("?");
						} else if (dbType == DBType.MYSQL || dbType == DBType.MYSQL57) {
							// update 2026-9-6 实测mysql 9.x的vector列update裸?+setString报
							// 类型转换错误,以string_to_vector(?)包装
							sql.append("string_to_vector(?)");
						} else if (dbType == DBType.GAUSSDB || dbType == DBType.VASTBASE) {
							// update 2026-9-10 vastbase G100 3.0实测向量类型名为floatvector(无vector
							// 别名),cast+外层nvl包裹两端同型,null不覆盖原值语义保持
							// (nvl('[7,8,9]'::floatvector,v)实库验证可行);gaussdb企业版同型floatvector,
							// 与setVectorValue/OpenGaussDialectUtils既有分派对齐
							sql.append("cast(? as floatvector)");
						} else if (dbType == DBType.KINGBASE || dbType == DBType.OPENGAUSS || dbType == DBType.MOGDB
								|| dbType == DBType.STARDB) {
							// update 2026-9-7 实测og7内核nvl/coalesce(?,vector)裸?绑定报"could not
							// convert type vector to text"(unknown参数被内核降解为text),以cast产生
							// 类型化表达式后nvl两端同型,null不覆盖原值语义完整保持(实库验证null更新
							// 后原值保留,非null更新生效);kingbase(og同源内核)同型;
							// update 2026-9-8 og同源内核mogdb/stardb统一纳入该分派
							// update 2026-9-10 oscar摘除:驱动jar解包实证传统Oscar(com.oscar.Driver)为
							// 老pgjdbc深度魔改衍生(默认Oracle兼容模式),非og同源内核,无vector类型
							// 亦无PGobject同构类,cast(? as vector)必败,回归裸?+setString
							// (神通openGauss版走opengauss驱动识别为OPENGAUSS,不落本分派)
							sql.append("cast(? as vector)");
						} else {
							// 其余数据库裸?绑定(实测隐式转换可行)
							sql.append("?");
						}
					} else if (fieldMeta.getType() == JdbcTypes.JSON || fieldMeta.getType() == JdbcTypes.JSONB) {
						// update 2026-9-10 oscar摘除:传统Oscar无json类型(json数据以CLOB/VARCHAR列
						// 承载,社区7.0.8实测),cast(? as json)必败,裸?+setString为正确形态
						// (驱动jar解包实证非og同源内核,详见上方vector分派注释)
						if (dbType == DBType.OPENGAUSS || dbType == DBType.GAUSSDB || dbType == DBType.VASTBASE
								|| dbType == DBType.MOGDB || dbType == DBType.STARDB || dbType == DBType.KINGBASE) {
							sql.append("cast(? as ").append((fieldMeta.getType() == JdbcTypes.JSONB) ? "jsonb" : "json")
									.append(")");
						} else {
							// 其余数据库裸?绑定(json参数经JSONTypeUtil按json类型绑定)
							sql.append("?");
						}
					} // 2020-6-13 修复postgresql\kingbase bytea类型处理错误
					else if (fieldMeta.getType() == JdbcTypes.BLOB) {
						if (convertBlob && "byte[]".equals(fieldMeta.getFieldType())) {
							sql.append("cast(? as bytea)");
						} else {
							sql.append("?");
						}
					} else {
						// 解决sqlserver decimal 类型小数位丢失问题
						if (isMSsql && fieldMeta.getType() == java.sql.Types.DECIMAL) {
							decimalLength = (fieldMeta.getLength() > 35) ? fieldMeta.getLength() : 35;
							decimalScale = (fieldMeta.getScale() > 5) ? fieldMeta.getScale() : 5;
							sql.append("cast(? as decimal(" + decimalLength + "," + decimalScale + "))");
						} else {
							// 2023-5-11 这里待完善修改时间取数据库时间问题nvl(?,current_timestamp)
							currentTimeStr = SqlUtil.getDBTime(dbType, fieldMeta, updateSqlTimeFields);
							if (null != currentTimeStr) {
								defaultColName = currentTimeStr;
							}
							sql.append("?");
						}
					}
					// nvl的收尾(directAssign直赋形态无包裹无需收尾)
					if (!directAssign) {
						sql.append(",").append(defaultColName).append(")");
					}
				}
				meter++;
			}
		}
		sql.append(" where ");
		for (int i = 0, n = entityMeta.getIdArray().length; i < n; i++) {
			columnName = entityMeta.getColumnName(entityMeta.getIdArray()[i]);
			columnName = ReservedWordsUtil.convertWord(columnName, dbType);
			if (i > 0) {
				sql.append(" and ");
			}
			sql.append(columnName);
			sql.append("=?");
		}
		return sql.toString();
	}

	/**
	 * 加载获取单笔数据库记录
	 * 
	 * @param sqlToyContext
	 * @param sqlToyConfig
	 * @param sql
	 * @param entityMeta
	 * @param entity
	 * @param cascadeTypes
	 * @param conn
	 * @param dbType
	 * @return
	 * @throws Exception
	 */
	public static Serializable load(final SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig, String sql,
			EntityMeta entityMeta, Serializable entity, boolean onlySubTables, List<Class> cascadeTypes,
			Connection conn, final Integer dbType, final Integer queryTimeout) throws Exception {
		Object[] pkValues = BeanUtil.reflectBeanToAry(entity, entityMeta.getIdArray());
		// 检查主键值是否合法
		for (int i = 0; i < pkValues.length; i++) {
			if (StringUtil.isBlank(pkValues[i])) {
				throw new IllegalArgumentException(entityMeta.getSchemaTable(null, dbType)
						+ " load method must assign value for pk,null pk field is:" + entityMeta.getIdArray()[i]);
			}
		}
		SqlToyResult sqlToyResult = null;
		Serializable result = null;
		// 加载主表
		if (!onlySubTables) {
			sqlToyResult = SqlConfigParseUtils.processSql(sql, entityMeta.getIdArray(), pkValues, null);
			// 加密字段解密
			DecryptHandler decryptHandler = null;
			if (entityMeta.getSecureColumns() != null) {
				decryptHandler = new DecryptHandler(sqlToyContext.getFieldsSecureProvider(),
						entityMeta.getSecureColumns());
			}
			// 增加sql执行拦截器 update 2022-9-10
			sqlToyResult = doInterceptors(sqlToyContext, sqlToyConfig, OperateType.load, sqlToyResult,
					entity.getClass(), dbType);
			QueryExecutorExtend extend = null;
			if (queryTimeout > 0) {
				extend = new QueryExecutorExtend();
				extend.timeout = queryTimeout;
			}
			QueryResult queryResult = findBySql(sqlToyContext, sqlToyConfig, sqlToyResult.getSql(),
					sqlToyResult.getParamsValue(), extend, decryptHandler, conn, dbType, 0, -1, -1);
			List rows = queryResult.getRows();
			Class entityClass;
			if (rows != null && rows.size() > 0) {
				entityClass = BeanUtil.getEntityClass(entity.getClass());
				rows = BeanUtil.reflectListToBean(sqlToyContext.getTypeHandler(), rows,
						ResultUtils.humpFieldNames(queryResult.getLabelNames(), entityMeta.getColumnFieldMap()),
						queryResult.getLabelTypes(), entityClass);
				result = (Serializable) rows.get(0);
				// 处理类中的@Translate注解，进行缓存翻译
				ResultUtils.wrapResultTranslate(sqlToyContext, result, entityClass);
			}
			if (result == null) {
				return null;
			}
		} else {
			result = entity;
		}
		// 存在主表对应子表
		if (null != cascadeTypes && !cascadeTypes.isEmpty() && !entityMeta.getCascadeModels().isEmpty()) {
			List pkRefDetails;
			EntityMeta mappedMeta;
			Object[] mainFieldValues;
			String loadSubTableSql;
			for (TableCascadeModel cascadeModel : entityMeta.getCascadeModels()) {
				// 判定是否要加载
				if (cascadeTypes.contains(cascadeModel.getMappedType())) {
					mainFieldValues = BeanUtil.reflectBeanToAry(result, cascadeModel.getFields());
					loadSubTableSql = ReservedWordsUtil.convertSql(cascadeModel.getLoadSubTableSql(), dbType);
					sqlToyResult = SqlConfigParseUtils.processSql(loadSubTableSql, cascadeModel.getMappedFields(),
							mainFieldValues, null);
					SqlExecuteStat.showSql("cascade sub table load query", sqlToyResult.getSql(),
							sqlToyResult.getParamsValue());
					mappedMeta = sqlToyContext.getEntityMeta(cascadeModel.getMappedType());
					// 子表加密字段解密
					DecryptHandler subDecryptHandler = null;
					if (mappedMeta.getSecureColumns() != null) {
						subDecryptHandler = new DecryptHandler(sqlToyContext.getFieldsSecureProvider(),
								mappedMeta.getSecureColumns());
					}
					SqlToyConfig subLoadConfig = new SqlToyConfig(DataSourceUtils.getDialect(dbType));
					subLoadConfig.setSql(sqlToyResult.getSql());
					subLoadConfig.setParamsName(cascadeModel.getFields());
					sqlToyResult = doInterceptors(sqlToyContext, subLoadConfig, OperateType.load, sqlToyResult,
							cascadeModel.getMappedType(), dbType);
					pkRefDetails = SqlUtil.findByJdbcQuery(sqlToyContext.getTypeHandler(), sqlToyResult.getSql(),
							sqlToyResult.getParamsValue(), cascadeModel.getMappedType(), null, subDecryptHandler, conn,
							dbType, false, mappedMeta.getColumnFieldMap(), SqlToyConstants.FETCH_SIZE, -1,
							queryTimeout);
					// 处理子类中@Translate注解，进行缓存翻译
					ResultUtils.wrapResultTranslate(sqlToyContext, pkRefDetails, cascadeModel.getMappedType());
					if (null != pkRefDetails && !pkRefDetails.isEmpty()) {
						// oneToMany
						if (cascadeModel.getCascadeType() == 1) {
							BeanUtil.setProperty(result, cascadeModel.getProperty(), pkRefDetails);
						} else {
							// update 2022-5-18 增加oneToOne 级联数据校验
							if (pkRefDetails.size() > 1) {
								throw new DataAccessException("@OneToOne cascade query for entity ["
										+ entityMeta.getEntityClass().getName() + "] returned size="
										+ pkRefDetails.size()
										+ " >1 rows, expect a single record, please check the @OneToOne cascade configuration!");
							}
							BeanUtil.setProperty(result, cascadeModel.getProperty(), pkRefDetails.get(0));
						}
					}
				}
			}
		}
		return result;
	}

	/**
	 * 提供统一的loadAll处理机制
	 * 
	 * @param sqlToyContext
	 * @param entities
	 * @param onlySubTables
	 * @param cascadeTypes
	 * @param lockMode
	 * @param conn
	 * @param dbType
	 * @param tableName
	 * @param lockSqlHandler
	 * @param fetchSize
	 * @param maxRows
	 * @return
	 * @throws Exception
	 */
	public static List<?> loadAll(final SqlToyContext sqlToyContext, List<?> entities, boolean onlySubTables,
			List<Class> cascadeTypes, LockMode lockMode, Connection conn, final Integer dbType, String tableName,
			LockSqlHandler lockSqlHandler, final int fetchSize, final int maxRows, Integer queryTimeout)
			throws Exception {
		if (entities == null || entities.isEmpty()) {
			return entities;
		}
		Class entityClass = BeanUtil.getEntityClass(entities.get(0).getClass());
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entityClass);
		// 没有主键不能进行load相关的查询
		if (null == entityMeta.getIdArray() || entityMeta.getIdArray().length < 1) {
			throw new IllegalArgumentException(
					entityClass.getName() + " Entity Object hasn't primary key,cann't use loadAll method!");
		}
		List<?> entitySet = null;
		if (!onlySubTables) {
			SqlToyResult sqlToyResult = null;
			DecryptHandler decryptHandler = null;
			if (entityMeta.getSecureColumns() != null) {
				decryptHandler = new DecryptHandler(sqlToyContext.getFieldsSecureProvider(),
						entityMeta.getSecureColumns());
			}
			int idSize = entityMeta.getIdArray().length;
			List<Object[]> sortIds = new ArrayList();
			// 单主键
			if (idSize == 1) {
				// 按行提取原始主键值(sliceToArray会过滤null导致行号错位,null主键实体被静默跳过),
				// null主键给出带行号的明确错误(与复合主键分支防护对称),而非悄悄少查
				List idRows = BeanUtil.reflectBeansToList(entities, new String[] { entityMeta.getIdArray()[0] }, null);
				if (idRows == null || idRows.isEmpty()) {
					throw new IllegalArgumentException(
							tableName + " loadAll method must assign value for pk field:" + entityMeta.getIdArray()[0]);
				}
				List idList = new ArrayList(idRows.size());
				Object cellValue;
				for (int i = 0, n = idRows.size(); i < n; i++) {
					cellValue = (idRows.get(i) == null) ? null : ((List) idRows.get(i)).get(0);
					if (cellValue == null) {
						throw new IllegalArgumentException(tableName + " loadAll method must assign value for pk,row:"
								+ i + " pk field:" + entityMeta.getIdArray()[0]);
					}
					idList.add(cellValue);
				}
				Object[] idValues = idList.toArray(new Object[idList.size()]);
				for (int i = 0; i < idValues.length; i++) {
					sortIds.add(new Object[] { idValues[i] });
				}
				// 组织loadAll sql语句
				String sql = wrapLoadAll(entityMeta, idValues.length, tableName, lockSqlHandler, lockMode, dbType);
				sqlToyResult = SqlConfigParseUtils.processSql(sql, null, new Object[] { idValues }, null);
			} // 复合主键
			else {
				List<Object[]> idValues = BeanUtil.reflectBeansToInnerAry(entities, entityMeta.getIdArray(), null,
						null);
				sortIds = idValues;
				Object[] rowData;
				Object cellValue;
				// 将条件构造成一个数组
				Object[] realValues = new Object[idValues.size() * idSize];
				int index = 0;
				for (int i = 0, n = idValues.size(); i < n; i++) {
					rowData = idValues.get(i);
					for (int j = 0; j < idSize; j++) {
						cellValue = rowData[j];
						// 验证主键值是否合法
						if (StringUtil.isBlank(cellValue)) {
							throw new IllegalArgumentException(
									tableName + " loadAll method must assign value for pk,row:" + i + " pk field:"
											+ entityMeta.getIdArray()[j]);
						}
						realValues[index] = cellValue;
						index++;
					}
				}
				// 组织loadAll sql语句
				String sql = wrapLoadAll(entityMeta, idValues.size(), tableName, lockSqlHandler, lockMode, dbType);
				sqlToyResult = new SqlToyResult(sql, realValues);
			}
			// 增加sql执行拦截器 update 2022-9-10
			SqlToyConfig sqlToyConfig = new SqlToyConfig(DataSourceUtils.getDialect(dbType));
			sqlToyConfig.setSqlType(SqlType.search);
			sqlToyConfig.setSql(sqlToyResult.getSql());
			sqlToyConfig.setParamsName(entityMeta.getIdArray());
			sqlToyResult = doInterceptors(sqlToyContext, sqlToyConfig, OperateType.loadAll, sqlToyResult, entityClass,
					dbType);
			SqlExecuteStat.showSql("execute batch query by primary key", sqlToyResult.getSql(),
					sqlToyResult.getParamsValue());
			entitySet = SqlUtil.findByJdbcQuery(sqlToyContext.getTypeHandler(), sqlToyResult.getSql(),
					sqlToyResult.getParamsValue(), entityClass, null, decryptHandler, conn, dbType, false,
					entityMeta.getColumnFieldMap(), fetchSize, maxRows, queryTimeout);
			// 处理类中的@Translate注解，进行缓存翻译
			ResultUtils.wrapResultTranslate(sqlToyContext, entitySet, entityClass);
			if (entitySet == null || entitySet.isEmpty()) {
				return entitySet;
			}
			// 按传入的集合顺序进行排序 update 2022-9-9 由网友夜孤城反馈
			// update 2026-9-8 重排算法由双层循环逐条比对+ArrayList.remove(整体O(n²)~O(n³),
			// 万级行明显劣化)改为主键组合键Map一次归位O(n);Arrays.asList的equals/hashCode
			// 基于元素逐一比较,与原逐字段equals语义一致
			List<Object[]> resultIds = BeanUtil.reflectBeansToInnerAry(entitySet, entityMeta.getIdArray(), null, null);
			Map<List, Object> idEntityMap = new HashMap<>(entitySet.size() * 2);
			for (int j = 0; j < resultIds.size(); j++) {
				idEntityMap.put(Arrays.asList(resultIds.get(j)), entitySet.get(j));
			}
			List sortEntities = new ArrayList();
			for (int i = 0; i < sortIds.size(); i++) {
				Object matched = idEntityMap.remove(Arrays.asList(sortIds.get(i)));
				if (matched != null) {
					sortEntities.add(matched);
				}
			}
			entitySet = sortEntities;
		} else {
			entitySet = entities;
		}
		// 存在主表对应子表
		if (null != cascadeTypes && !cascadeTypes.isEmpty() && !entityMeta.getCascadeModels().isEmpty()) {
			StringBuilder subTableSql = new StringBuilder();
			List items;
			SqlToyResult subToyResult;
			EntityMeta mappedMeta;
			int fieldSize;
			List<Object[]> idValues = null;
			String colName;
			Object[] rowData;
			Object cellValue;
			int dataSize = entitySet.size();
			boolean supportMultiFieldIn = DataSourceUtils.isSupportMultiFieldIn(dbType);
			for (TableCascadeModel cascadeModel : entityMeta.getCascadeModels()) {
				if (cascadeTypes.contains(cascadeModel.getMappedType())) {
					mappedMeta = sqlToyContext.getEntityMeta(cascadeModel.getMappedType());
					// 清空buffer
					subTableSql.delete(0, subTableSql.length());
					fieldSize = cascadeModel.getMappedFields().length;
					// loadSql中用in作为条件，支持批量
					if (cascadeModel.isLoadSqlSupportBatch()) {
						String sql = cascadeModel.getLoadBatchSubTableSql();
						Pattern pattern;
						Matcher matcher;
						String group;
						StringBuilder result = new StringBuilder();
						boolean hasMatched = false;
						// 替换in (:id)笔名为?
						for (int i = 0; i < fieldSize; i++) {
							hasMatched = false;
							// update 2026-9-8 字段名固定的级联匹配正则按cascadedModel字段名缓存(原每次loadAll现场编译)
							pattern = CASCADE_FIELD_PATTERN_CACHE.computeIfAbsent(cascadeModel.getMappedFields()[i],
									k -> Pattern.compile("(?i)\\:" + k + "\\W"));
							matcher = pattern.matcher(sql);
							while (matcher.find()) {
								hasMatched = true;
								group = matcher.group();
								matcher.appendReplacement(result, "?" + group.substring(group.length() - 1));
							}
							if (hasMatched) {
								matcher.appendTail(result);
								sql = result.toString();
								result.delete(0, result.length());
							}
						}
						subTableSql.append(ReservedWordsUtil.convertSimpleSql(sql, dbType));
					} else {
						// 构造查询语句,update 2019-12-09 使用完整字段
						subTableSql.append(ReservedWordsUtil.convertSimpleSql(mappedMeta.getLoadAllSql(), dbType))
								.append(" where ");
						String orderCols = "";
						boolean hasOrder = StringUtil.isNotBlank(cascadeModel.getOrderBy());
						boolean hasExtCondtion = StringUtil.isNotBlank(cascadeModel.getLoadExtCondition()) ? true
								: false;
						// 单主键
						if (fieldSize == 1) {
							colName = cascadeModel.getMappedColumns()[0];
							colName = ReservedWordsUtil.convertWord(colName, dbType);
							subTableSql.append(colName);
							subTableSql.append(" in (?) ");
							if (hasOrder) {
								orderCols = orderCols.concat(colName).concat(",");
							}
						} // 复合主键
						else {
							if (supportMultiFieldIn) {
								String columns = "(";
								String condition = "(";
								for (int i = 0; i < fieldSize; i++) {
									if (i > 0) {
										columns = columns.concat(",");
										condition = condition.concat(",");
									}
									colName = cascadeModel.getMappedColumns()[i];
									colName = ReservedWordsUtil.convertWord(colName, dbType);
									condition = condition.concat("?");
									columns = columns.concat(colName);
									if (hasOrder) {
										orderCols = orderCols.concat(colName).concat(",");
									}
								}
								condition = condition.concat(")");
								columns = columns.concat(")");
								StringBuilder conditionStr = new StringBuilder();
								int groupIndex = 0;
								int groupCnt = 0;
								int groupSize = 1000;
								if (dataSize > groupSize) {
									subTableSql.append(" ( ");
								}
								for (int i = 0; i < dataSize; i++) {
									if (groupIndex > 0) {
										conditionStr.append(",");
									}
									conditionStr.append(condition);
									groupIndex++;
									// in 最大支持1000
									if (((i + 1) % groupSize) == 0 || i + 1 == dataSize) {
										if (groupCnt > 0) {
											subTableSql.append(" or ");
										}
										subTableSql.append(columns).append(" in (").append(conditionStr.toString())
												.append(")");
										groupIndex = 0;
										groupCnt++;
										// 清空
										conditionStr.setLength(0);
									}
								}
								if (dataSize > groupSize) {
									subTableSql.append(" ) ");
								}
							} else {
								// 构造(field1=? and field2=?)
								String condition = " (";
								for (int i = 0; i < fieldSize; i++) {
									colName = cascadeModel.getMappedColumns()[i];
									colName = ReservedWordsUtil.convertWord(colName, dbType);
									if (i > 0) {
										condition = condition.concat(" and ");
									}
									condition = condition.concat(colName).concat("=?");
									if (hasOrder) {
										orderCols = orderCols.concat(colName).concat(",");
									}
								}
								condition = condition.concat(") ");
								// 构造成 (field1=? and field2=?) or (field1=? and field2=?)
								if (hasExtCondtion) {
									subTableSql.append(" (");
								}
								for (int i = 0; i < dataSize; i++) {
									if (i > 0) {
										subTableSql.append(" or ");
									}
									subTableSql.append(condition);
								}
								if (hasExtCondtion) {
									subTableSql.append(") ");
								}
							}
						}
						// 自定义扩展条件
						if (hasExtCondtion) {
							subTableSql.append(" and ").append(cascadeModel.getLoadExtCondition());
						}
						if (hasOrder) {
							subTableSql.append(" order by ").append(orderCols).append(cascadeModel.getOrderBy());
						}
					}
					// 单主键
					if (fieldSize == 1) {
						Object[] pkValues = BeanUtil.sliceToArray(entitySet, cascadeModel.getFields()[0]);
						subToyResult = SqlConfigParseUtils.processSql(subTableSql.toString(), null,
								new Object[] { pkValues }, null);
					} else {
						idValues = BeanUtil.reflectBeansToInnerAry(entitySet, cascadeModel.getFields(), null, null);
						// 复合主键,将条件值构造成一个数组
						Object[] realValues = new Object[idValues.size() * fieldSize];
						int index = 0;
						for (int i = 0, n = idValues.size(); i < n; i++) {
							rowData = idValues.get(i);
							for (int j = 0; j < fieldSize; j++) {
								cellValue = rowData[j];
								realValues[index] = cellValue;
								index++;
							}
						}
						subToyResult = new SqlToyResult(subTableSql.toString(), realValues);
					}
					// 加密字段解密
					DecryptHandler subDecryptHandler = null;
					if (mappedMeta.getSecureColumns() != null) {
						subDecryptHandler = new DecryptHandler(sqlToyContext.getFieldsSecureProvider(),
								mappedMeta.getSecureColumns());
					}
					SqlToyConfig subLoadConfig = new SqlToyConfig(DataSourceUtils.getDialect(dbType));
					subLoadConfig.setSqlType(SqlType.search);
					subLoadConfig.setSql(subToyResult.getSql());
					subToyResult = doInterceptors(sqlToyContext, subLoadConfig, OperateType.loadAll, subToyResult,
							cascadeModel.getMappedType(), dbType);
					SqlExecuteStat.showSql("cascade load sub table", subToyResult.getSql(),
							subToyResult.getParamsValue());
					items = SqlUtil.findByJdbcQuery(sqlToyContext.getTypeHandler(), subToyResult.getSql(),
							subToyResult.getParamsValue(), cascadeModel.getMappedType(), null, subDecryptHandler, conn,
							dbType, false, mappedMeta.getColumnFieldMap(), SqlToyConstants.FETCH_SIZE, maxRows,
							queryTimeout);
					// 处理子类中的@Translate注解，进行缓存翻译
					ResultUtils.wrapResultTranslate(sqlToyContext, items, cascadeModel.getMappedType());
					SqlExecuteStat.debug("sub table load result", "sub records:{} rows", items.size());
					// 将item的值分配映射到main主表对象上
					BeanUtil.loadAllMapping(entitySet, items, cascadeModel);
				}
			}
		}
		return entitySet;
	}

	/**
	 * @param entityMeta
	 * @param dataSize
	 * @param tableName
	 * @param lockSqlHandler
	 * @param lockMode
	 * @param dbType
	 * @return 组织loadAll sql语句
	 */
	private static String wrapLoadAll(EntityMeta entityMeta, int dataSize, String tableName,
			LockSqlHandler lockSqlHandler, LockMode lockMode, Integer dbType) {
		int idSize = entityMeta.getIdArray().length;
		StringBuilder loadSql = new StringBuilder();
		loadSql.append("select ").append(ReservedWordsUtil.convertSimpleSql(entityMeta.getAllColumnNames(), dbType));
		loadSql.append(" from ");
		loadSql.append(entityMeta.getSchemaTable(tableName, dbType));
		// sqlserver 锁语句不同于其他
		if (dbType == DBType.SQLSERVER && lockMode != null) {
			switch (lockMode) {
			case UPGRADE:
				loadSql.append(" with (rowlock xlock) ");
				break;
			case UPGRADE_NOWAIT:
				// nowait语义是拿不到锁立即报错,不能用readpast(会静默跳过被锁行)
				loadSql.append(" with (rowlock xlock nowait) ");
				break;
			case UPGRADE_SKIPLOCK:
				loadSql.append(" with (rowlock readpast) ");
				break;
			}
		}
		loadSql.append(" where ");
		String field;
		String colName;
		// 单主键
		if (idSize == 1) {
			field = entityMeta.getIdArray()[0];
			colName = ReservedWordsUtil.convertWord(entityMeta.getColumnName(field), dbType);
			loadSql.append(colName);
			loadSql.append(" in (?) ");
		} else {
			// 构造 (id,code) in ((?,?),(?,?)) 形式
			if (DataSourceUtils.isSupportMultiFieldIn(dbType)) {
				String columns = "(";
				String condition = "(";
				for (int i = 0; i < idSize; i++) {
					if (i > 0) {
						columns = columns.concat(",");
						condition = condition.concat(",");
					}
					field = entityMeta.getIdArray()[i];
					colName = ReservedWordsUtil.convertWord(entityMeta.getColumnName(field), dbType);
					condition = condition.concat("?");
					columns = columns.concat(colName);
				}
				condition = condition.concat(")");
				columns = columns.concat(")");
				StringBuilder conditionStr = new StringBuilder();
				int groupIndex = 0;
				int groupCnt = 0;
				int groupSize = 1000;
				if (dataSize > groupSize) {
					loadSql.append(" ( ");
				}
				for (int i = 0; i < dataSize; i++) {
					if (groupIndex > 0) {
						conditionStr.append(",");
					}
					conditionStr.append(condition);
					groupIndex++;
					// in 最大支持1000
					if (((i + 1) % groupSize) == 0 || i + 1 == dataSize) {
						if (groupCnt > 0) {
							loadSql.append(" or ");
						}
						loadSql.append(columns).append(" in (").append(conditionStr.toString()).append(")");
						groupIndex = 0;
						groupCnt++;
						// 清空
						conditionStr.setLength(0);
					}
				}
				if (dataSize > groupSize) {
					loadSql.append(" ) ");
				}
			} else {
				// 复合主键构造 (field1=? and field2=?)
				String condition = " (";
				for (int i = 0; i < idSize; i++) {
					field = entityMeta.getIdArray()[i];
					colName = ReservedWordsUtil.convertWord(entityMeta.getColumnName(field), dbType);
					if (i > 0) {
						condition = condition.concat(" and ");
					}
					condition = condition.concat(colName).concat("=?");
				}
				condition = condition.concat(")");
				// 构造 (field1=? and field2=?) or (field1=? and field2=?)
				for (int i = 0; i < dataSize; i++) {
					if (i > 0) {
						loadSql.append(" or ");
					}
					loadSql.append(condition);
				}
			}
		}
		if (lockSqlHandler != null) {
			loadSql.append(lockSqlHandler.getLockSql(loadSql.toString(), dbType, lockMode));
		}
		return loadSql.toString();
	}

	/**
	 * @param sqlToyContext
	 * @param entityMeta
	 * @param pkStrategy
	 * @param isAssignPK
	 * @param insertSql
	 * @param entity
	 * @param generateSqlHandler
	 * @param generateSavePKStrategy
	 * @param conn
	 * @param dbType
	 * @return
	 * @throws Exception 保存对象
	 */
	public static Object save(final SqlToyContext sqlToyContext, final EntityMeta entityMeta,
			final PKStrategy pkStrategy, final boolean isAssignPK, final String insertSql, Serializable entity,
			final GenerateSqlHandler generateSqlHandler, final GenerateSavePKStrategy generateSavePKStrategy,
			final Connection conn, final Integer dbType) throws Exception {
		final boolean isIdentity = (pkStrategy != null && PKStrategy.IDENTITY.equals(pkStrategy));
		final boolean isSequence = (pkStrategy != null && PKStrategy.SEQUENCE.equals(pkStrategy));
		String[] reflectColumns;
		if ((isIdentity && !isAssignPK) || (isSequence && !isAssignPK)) {
			reflectColumns = entityMeta.getRejectIdFieldArray(true);
		} else {
			reflectColumns = entityMeta.getFieldsArray(true);
		}
		// 构造全新的新增记录参数赋值反射(覆盖之前的)
		ReflectPropsHandler handler = getAddReflectHandler(entityMeta, null, sqlToyContext.getUnifyFieldsHandler());
		// 字段加密处理
		handler = getSecureReflectHandler(handler, sqlToyContext.getFieldsSecureProvider(),
				sqlToyContext.getDesensitizeProvider(), entityMeta.getSecureFields());
		// update 2022-7-16 增加默认值的代入
		Object[] fullParamValues = BeanUtil.reflectBeanToAry(entity, reflectColumns,
				SqlUtilsExt.getDefaultValues(entityMeta, true), handler);
		boolean hasId = (pkStrategy != null && null != entityMeta.getIdGenerator()) ? true : false;
		// 业务主键取值赋值
		boolean hasBizId = (entityMeta.getBusinessIdGenerator() == null) ? false : true;
		int generatedColCnt = entityMeta.getGeneratedColsCnt();
		int bizIdColIndex = hasBizId ? entityMeta.getFieldIndex(entityMeta.getBusinessIdField()) - generatedColCnt : 0;
		// 主键采用assign方式赋予，则调用generator产生id并赋予其值
		boolean needUpdatePk = false;

		int pkIndex = entityMeta.getIdIndex() - generatedColCnt;
		// 主键、业务主键生成并回写对象
		if (hasId || hasBizId) {
			Integer[] relatedColumn = entityMeta.getBizIdRelatedColIndex();
			Object[] relatedColValue = null;
			// 优先提取业务主键所依赖的字段属性的值，用于产生业务主键
			if (relatedColumn != null) {
				int relatedColumnSize = relatedColumn.length;
				relatedColValue = new Object[relatedColumnSize];
				for (int meter = 0; meter < relatedColumnSize; meter++) {
					relatedColValue[meter] = fullParamValues[relatedColumn[meter] - generatedColCnt];
					if (StringUtil.isBlank(relatedColValue[meter])) {
						throw new IllegalArgumentException("generate business id for entity ["
								+ entityMeta.getEntityClass().getName() + "], the related field ["
								+ entityMeta.getBizIdRelatedColumns()[meter] + "] value is null, please check!");
					}
				}
			}
			// 主键
			if (hasId && StringUtil.isBlank(fullParamValues[pkIndex])) {
				// id通过generator机制产生，设置generator产生的值
				fullParamValues[pkIndex] = entityMeta.getIdGenerator().getId(entityMeta.getTableName(),
						entityMeta.getBizIdSignature(), entityMeta.getBizIdRelatedColumns(), relatedColValue, null,
						entityMeta.getIdType(), entityMeta.getIdLength(), entityMeta.getBizIdSequenceSize());
				needUpdatePk = true;
			}
			// 业务主键
			if (hasBizId && StringUtil.isBlank(fullParamValues[bizIdColIndex])) {
				fullParamValues[bizIdColIndex] = entityMeta.getBusinessIdGenerator().getId(entityMeta.getTableName(),
						entityMeta.getBizIdSignature(), entityMeta.getBizIdRelatedColumns(), relatedColValue, null,
						entityMeta.getColumnJavaType(entityMeta.getBusinessIdField()), entityMeta.getBizIdLength(),
						entityMeta.getBizIdSequenceSize());
				// 回写业务主键值
				BeanUtil.setProperty(entity, entityMeta.getBusinessIdField(), fullParamValues[bizIdColIndex]);
			}
		}
		// 回写save公共属性值
		BeanUtil.backWriteUnifyFields(entity,
				BeanUtil.getUnifyFieldIndex(sqlToyContext.getUnifyFieldsHandler(), reflectColumns, 1), fullParamValues);
		SqlToyConfig sqlToyConfig = new SqlToyConfig(DataSourceUtils.getDialect(dbType));
		sqlToyConfig.setSqlType(SqlType.insert);
		sqlToyConfig.setSql(insertSql);
		sqlToyConfig.setParamsName(reflectColumns);
		SqlToyResult sqlToyResult = new SqlToyResult(insertSql, fullParamValues);
		sqlToyResult = doInterceptors(sqlToyContext, sqlToyConfig, OperateType.insert, sqlToyResult, entity.getClass(),
				dbType);
		String realInsertSql = sqlToyResult.getSql();
		SqlExecuteStat.showSql("single record insert", realInsertSql, null);
		final Object[] paramValues = sqlToyResult.getParamsValue();
		// update 2026-9-7 sqlserver的rowversion列在insert语句生成时已排除,绑定参数与类型需按reflectColumns
		// 平行剔除,保持占位符与参数严格1:1(自SqlServerDialectUtils.save迁入;identity/sequence场景参数
		// 按rejectId反射,类型与参数必须同源,不能直接用fieldsTypeArray)
		Object[] rowBindValues = paramValues;
		Integer[] rowBindTypes = entityMeta.getFieldsTypeArray(true);
		if (dbType != null && dbType.intValue() == DBType.SQLSERVER) {
			int[] keepIndex = new int[reflectColumns.length];
			int keepCnt = 0;
			for (int i = 0; i < reflectColumns.length; i++) {
				if (!entityMeta.isRowVersionField(entityMeta.getFieldMeta(reflectColumns[i]))) {
					keepIndex[keepCnt++] = i;
				}
			}
			Integer[] msBindTypes = new Integer[keepCnt];
			for (int i = 0; i < keepCnt; i++) {
				msBindTypes[i] = entityMeta.getFieldMeta(reflectColumns[keepIndex[i]]).getType();
			}
			rowBindTypes = msBindTypes;
			if (keepCnt != reflectColumns.length) {
				Object[] tmpValues = new Object[keepCnt];
				for (int i = 0; i < keepCnt; i++) {
					tmpValues[i] = paramValues[keepIndex[i]];
				}
				rowBindValues = tmpValues;
			}
		}
		final Object[] bindValues = rowBindValues;
		final Integer[] bindTypes = rowBindTypes;
		// update 2026-9-7 sqlserver的sequence主键通过select
		// @mySeqVariable结果集回填,不能走getGeneratedKeys
		// (自SqlServerDialectUtils.save迁入)
		final boolean isMsSequence = (isSequence && dbType != null && dbType.intValue() == DBType.SQLSERVER);
		PreparedStatement pst = null;
		if (isIdentity || (isSequence && !isMsSequence)) {
			// RETURN_GENERATED_KEYS 适合auto_increment,不适合sequence
			// pst = conn.prepareStatement(realInsertSql, Statement.RETURN_GENERATED_KEYS);
			pst = conn.prepareStatement(realInsertSql, new String[] { DataSourceUtils
					.getReturnPrimaryKeyColumn(entityMeta.getColumnName(entityMeta.getIdArray()[0]), dbType) });
		} else {
			pst = conn.prepareStatement(realInsertSql);
		}
		// 设置全局statementTimeout，默认为null
		if (SqlToyConstants.defaultStatementTimeout != null && SqlToyConstants.defaultStatementTimeout > 0) {
			pst.setQueryTimeout(SqlToyConstants.defaultStatementTimeout);
		}
		Object result = SqlUtil.preparedStatementProcess(null, pst, null, new PreparedStatementResultHandler() {
			@Override
			public void execute(Object obj, PreparedStatement pst, ResultSet rs) throws SQLException, IOException {
				SqlUtil.setParamsValue(sqlToyContext.getTypeHandler(), conn, dbType, pst, bindValues, bindTypes, 0);
				if (isMsSequence) {
					ResultSet msKeyResult = pst.executeQuery();
					if (msKeyResult != null) {
						while (msKeyResult.next()) {
							this.setResult(msKeyResult.getObject(1));
						}
						msKeyResult.close();
					}
					return;
				}
				pst.execute();
				if (isIdentity || isSequence) {
					ResultSet keyResult = pst.getGeneratedKeys();
					if (keyResult != null) {
						while (keyResult.next()) {
							this.setResult(keyResult.getObject(1));
						}
						keyResult.close();
					}
				}
			}
		});
		// 回写数据版本号
		if (entityMeta.getDataVersion() != null) {
			String dataVersionField = entityMeta.getDataVersion().getField();
			int dataVersionIndex = entityMeta.getFieldIndex(dataVersionField) - generatedColCnt;
			BeanUtil.setProperty(entity, dataVersionField, fullParamValues[dataVersionIndex]);
		}
		// 无主键直接返回null
		if (entityMeta.getIdArray() == null) {
			return null;
		}
		if (result == null && pkIndex < fullParamValues.length) {
			result = fullParamValues[pkIndex];
		}
		// 回置到entity 主键值
		if (needUpdatePk || isIdentity || isSequence) {
			BeanUtil.setProperty(entity, entityMeta.getIdArray()[0], result);
		}
		// 判定是否有级联子表数据保存
		if (!entityMeta.getCascadeModels().isEmpty()) {
			List subTableData = null;
			EntityMeta subTableEntityMeta;
			String insertSubTableSql;
			SavePKStrategy savePkStrategy;
			for (TableCascadeModel cascadeModel : entityMeta.getCascadeModels()) {
				final String[] mappedFields = cascadeModel.getMappedFields();
				final Object[] mappedFieldValues = BeanUtil.reflectBeanToAry(entity, cascadeModel.getFields());
				subTableEntityMeta = sqlToyContext.getEntityMeta(cascadeModel.getMappedType());
				// update 2026-9-7
				// sqlserver的级联子表按目标库元数据校准rowversion判据(自SqlServerDialectUtils.save迁入)
				if (dbType != null && dbType.intValue() == DBType.SQLSERVER) {
					SqlServerDialectUtils.ensureRowVersionMeta(sqlToyContext, conn, dbType, null,
							cascadeModel.getMappedType());
				}
				// oneToMany
				if (cascadeModel.getCascadeType() == 1) {
					subTableData = (List) BeanUtil.getProperty(entity, cascadeModel.getProperty());
				} else {
					subTableData = new ArrayList();
					Object item = BeanUtil.getProperty(entity, cascadeModel.getProperty());
					if (item != null) {
						subTableData.add(item);
					}
				}
				if (subTableData != null && !subTableData.isEmpty()) {
					logger.info("batch save the cascade sub table:{} for save operation!",
							subTableEntityMeta.getTableName());
					SqlExecuteStat.debug("cascade save sub table", null);
					// 回写关联字段赋值
					BeanUtil.batchSetProperties(subTableData, mappedFields, mappedFieldValues, true);
					insertSubTableSql = generateSqlHandler.generateSql(subTableEntityMeta, null);
					savePkStrategy = generateSavePKStrategy.generate(subTableEntityMeta);
					saveAll(sqlToyContext, subTableEntityMeta, savePkStrategy.getPkStrategy(),
							savePkStrategy.isAssginValue(), insertSubTableSql, subTableData,
							sqlToyContext.getBatchSize(), null, conn, dbType, null);
				} else {
					logger.info(
							"batch save of the cascade sub table:{} is not executed for save operation, sub table data is empty!",
							subTableEntityMeta.getTableName());
				}
			}
		}
		return result;
	}

	/**
	 * @param sqlToyContext
	 * @param entityMeta
	 * @param pkStrategy
	 * @param isAssignPK
	 * @param insertSql
	 * @param entities
	 * @param batchSize
	 * @param reflectPropsHandler
	 * @param conn
	 * @param dbType
	 * @param autoCommit
	 * @return
	 * @throws Exception 保存批量对象数据
	 */
	public static Long saveAll(SqlToyContext sqlToyContext, EntityMeta entityMeta, PKStrategy pkStrategy,
			boolean isAssignPK, String insertSql, List<?> entities, final int batchSize,
			ReflectPropsHandler reflectPropsHandler, Connection conn, final Integer dbType, final Boolean autoCommit)
			throws Exception {
		boolean isIdentity = pkStrategy != null && PKStrategy.IDENTITY.equals(pkStrategy);
		boolean isSequence = pkStrategy != null && PKStrategy.SEQUENCE.equals(pkStrategy);
		String[] reflectColumns;
		if ((isIdentity && !isAssignPK) || (isSequence && !isAssignPK)) {
			reflectColumns = entityMeta.getRejectIdFieldArray(true);
		} else {
			reflectColumns = entityMeta.getFieldsArray(true);
		}
		// 构造全新的新增记录参数赋值反射(覆盖之前的)
		ReflectPropsHandler handler = getAddReflectHandler(entityMeta, reflectPropsHandler,
				sqlToyContext.getUnifyFieldsHandler());
		// 字段加密处理
		handler = getSecureReflectHandler(handler, sqlToyContext.getFieldsSecureProvider(),
				sqlToyContext.getDesensitizeProvider(), entityMeta.getSecureFields());
		// update 2022-7-16 增加了默认值代入
		List<Object[]> paramValues = BeanUtil.reflectBeansToInnerAry(entities, reflectColumns,
				SqlUtilsExt.getDefaultValues(entityMeta, true), handler);
		// 标识符
		String signature = entityMeta.getBizIdSignature();
		Integer[] relatedColumn = entityMeta.getBizIdRelatedColIndex();
		String[] relatedColumnNames = entityMeta.getBizIdRelatedColumns();
		int relatedColumnSize = (relatedColumn == null) ? 0 : relatedColumn.length;
		boolean hasDataVersion = (entityMeta.getDataVersion() == null) ? false : true;
		boolean hasId = (pkStrategy != null && null != entityMeta.getIdGenerator()) ? true : false;
		int generatedColCnt = entityMeta.getGeneratedColsCnt();
		int dataVerIndex = hasDataVersion
				? entityMeta.getFieldIndex(entityMeta.getDataVersion().getField()) - generatedColCnt
				: 0;
		int pkIndex = entityMeta.getIdIndex() - generatedColCnt;
		// 是否存在业务ID
		boolean hasBizId = (entityMeta.getBusinessIdGenerator() == null) ? false : true;
		int bizIdColIndex = hasBizId ? entityMeta.getFieldIndex(entityMeta.getBusinessIdField()) - generatedColCnt : 0;
		Object[] relatedColValue = null;
		String businessIdType = hasBizId ? entityMeta.getColumnJavaType(entityMeta.getBusinessIdField()) : "";
		Object[] rowData;
		for (int i = 0, end = paramValues.size(); i < end; i++) {
			rowData = (Object[]) paramValues.get(i);
			// 业务主键关联字段值校验
			if (relatedColumn != null) {
				relatedColValue = new Object[relatedColumnSize];
				for (int meter = 0; meter < relatedColumnSize; meter++) {
					relatedColValue[meter] = rowData[relatedColumn[meter] - generatedColCnt];
					if (StringUtil.isBlank(relatedColValue[meter])) {
						throw new IllegalArgumentException("generate business id for entity ["
								+ entityMeta.getEntityClass().getName() + "], the related field ["
								+ relatedColumnNames[meter] + "] value is null, please check!");
					}
				}
			}
			// 主键值为null,调用主键生成策略并赋值
			if (hasId && StringUtil.isBlank(rowData[pkIndex])) {
				rowData[pkIndex] = entityMeta.getIdGenerator().getId(entityMeta.getTableName(), signature,
						relatedColumnNames, relatedColValue, null, entityMeta.getIdType(), entityMeta.getIdLength(),
						entityMeta.getBizIdSequenceSize());
				// 回写主键值
				BeanUtil.setProperty(entities.get(i), entityMeta.getIdArray()[0], rowData[pkIndex]);
			}
			if (hasBizId && StringUtil.isBlank(rowData[bizIdColIndex])) {
				rowData[bizIdColIndex] = entityMeta.getBusinessIdGenerator().getId(entityMeta.getTableName(), signature,
						relatedColumnNames, relatedColValue, null, businessIdType, entityMeta.getBizIdLength(),
						entityMeta.getBizIdSequenceSize());
				// 回写业务主键值
				BeanUtil.setProperty(entities.get(i), entityMeta.getBusinessIdField(), rowData[bizIdColIndex]);
			}
			// 回写数据版本
			if (hasDataVersion) {
				BeanUtil.setProperty(entities.get(i), entityMeta.getDataVersion().getField(), rowData[dataVerIndex]);
			}
		}
		// 批量回写save公共属性值
		BeanUtil.batchBackWriteUnifyFields(entities,
				BeanUtil.getUnifyFieldIndex(sqlToyContext.getUnifyFieldsHandler(), reflectColumns, 1), paramValues);
		List<Object[]> realParams = paramValues;
		String realSql = insertSql;
		if (sqlToyContext.hasSqlInterceptors()) {
			SqlToyConfig sqlToyConfig = new SqlToyConfig(DataSourceUtils.getDialect(dbType));
			sqlToyConfig.setSqlType(SqlType.insert);
			sqlToyConfig.setSql(insertSql);
			sqlToyConfig.setParamsName(reflectColumns);
			SqlToyResult sqlToyResult = new SqlToyResult(insertSql, paramValues.toArray());
			sqlToyResult = doInterceptors(sqlToyContext, sqlToyConfig, OperateType.insertAll, sqlToyResult,
					entities.get(0).getClass(), dbType);
			realSql = sqlToyResult.getSql();
			realParams = CollectionUtil.arrayToList(sqlToyResult.getParamsValue());
		}
		SqlExecuteStat.showSql("batch save [" + realParams.size() + "] rows", realSql, null);
		// update 2026-9-7 sqlserver的rowversion列在insert语句生成时已排除,绑定参数行与类型数组需按
		// reflectColumns平行剔除,保持占位符与参数严格1:1(自SqlServerDialectUtils.saveAll迁入;
		// identity/sequence场景参数本就按rejectId反射,类型必须与参数同源,不能直接用fieldsTypeArray)
		Integer[] bindTypes = entityMeta.getFieldsTypeArray(true);
		List<Object[]> bindParams = realParams;
		if (dbType != null && dbType.intValue() == DBType.SQLSERVER) {
			int[] keepIndex = new int[reflectColumns.length];
			int keepCnt = 0;
			for (int i = 0; i < reflectColumns.length; i++) {
				if (!entityMeta.isRowVersionField(entityMeta.getFieldMeta(reflectColumns[i]))) {
					keepIndex[keepCnt++] = i;
				}
			}
			Integer[] rowBindTypes = new Integer[keepCnt];
			for (int i = 0; i < keepCnt; i++) {
				rowBindTypes[i] = entityMeta.getFieldMeta(reflectColumns[keepIndex[i]]).getType();
			}
			bindTypes = rowBindTypes;
			if (keepCnt != reflectColumns.length) {
				List<Object[]> filteredParams = new ArrayList<Object[]>(realParams.size());
				for (Object[] row : realParams) {
					Object[] tmpRow = new Object[keepCnt];
					for (int i = 0; i < keepCnt; i++) {
						tmpRow[i] = row[keepIndex[i]];
					}
					filteredParams.add(tmpRow);
				}
				bindParams = filteredParams;
			}
		}
		return SqlUtil.batchUpdateForPOJO(sqlToyContext.getTypeHandler(), realSql, bindParams, bindTypes, batchSize,
				autoCommit, conn, dbType);
	}

	/**
	 * @param sqlToyContext
	 * @param entities
	 * @param batchSize
	 * @param entityMeta
	 * @param generateSqlHandler
	 * @param reflectPropsHandler
	 * @param conn
	 * @param dbType
	 * @param autoCommit
	 * @return
	 * @throws Exception 执行批量保存或修改操作
	 */
	public static Long saveAllIgnoreExist(SqlToyContext sqlToyContext, List<?> entities, final int batchSize,
			EntityMeta entityMeta, GenerateSqlHandler generateSqlHandler, ReflectPropsHandler reflectPropsHandler,
			Connection conn, final Integer dbType, Boolean autoCommit) throws Exception {
		// 构造全新的新增记录参数赋值反射(覆盖之前的)
		ReflectPropsHandler handler = getAddReflectHandler(entityMeta, reflectPropsHandler,
				sqlToyContext.getUnifyFieldsHandler());
		// 字段加密处理
		handler = getSecureReflectHandler(handler, sqlToyContext.getFieldsSecureProvider(),
				sqlToyContext.getDesensitizeProvider(), entityMeta.getSecureFields());
		// update 2022-7-16 增加默认值代入,insert sql上去除了nvl(?,default) 适应一些框架
		List<Object[]> paramValues = BeanUtil.reflectBeansToInnerAry(entities, entityMeta.getFieldsArray(true),
				SqlUtilsExt.getDefaultValues(entityMeta, true), handler);
		// 是否存在业务ID
		boolean hasBizId = (entityMeta.getBusinessIdGenerator() == null) ? false : true;
		int generatedColCnt = entityMeta.getGeneratedColsCnt();
		int bizIdColIndex = hasBizId ? entityMeta.getFieldIndex(entityMeta.getBusinessIdField()) - generatedColCnt : 0;
		// 标识符
		String signature = entityMeta.getBizIdSignature();
		Integer[] relatedColumn = entityMeta.getBizIdRelatedColIndex();
		String[] relatedColumnNames = entityMeta.getBizIdRelatedColumns();
		int relatedColumnSize = (relatedColumn == null) ? 0 : relatedColumn.length;
		int pkIndex = entityMeta.getIdIndex() - generatedColCnt;
		boolean hasId = (null != entityMeta.getIdStrategy() && null != entityMeta.getIdGenerator()) ? true : false;
		// 主键、业务主键生成并回写对象(不回写数据版本号,因为已经存在数据版本号重新生成就不一致了)
		if (hasId || hasBizId) {
			Object[] rowData;
			Object[] relatedColValue = null;
			String businessIdType = hasBizId ? entityMeta.getColumnJavaType(entityMeta.getBusinessIdField()) : "";
			for (int i = 0, end = paramValues.size(); i < end; i++) {
				rowData = (Object[]) paramValues.get(i);
				// 业务主键关联字段值校验
				if (relatedColumn != null) {
					relatedColValue = new Object[relatedColumnSize];
					for (int meter = 0; meter < relatedColumnSize; meter++) {
						relatedColValue[meter] = rowData[relatedColumn[meter] - generatedColCnt];
						if (relatedColValue[meter] == null) {
							throw new IllegalArgumentException("generate business id for entity ["
									+ entityMeta.getEntityClass().getName() + "], the related field ["
									+ relatedColumnNames[meter] + "] value is null, please check!");
						}
					}
				}
				// 主键
				if (hasId && StringUtil.isBlank(rowData[pkIndex])) {
					rowData[pkIndex] = entityMeta.getIdGenerator().getId(entityMeta.getTableName(), signature,
							relatedColumnNames, relatedColValue, null, entityMeta.getIdType(), entityMeta.getIdLength(),
							entityMeta.getBizIdSequenceSize());
					// 回写主键值
					BeanUtil.setProperty(entities.get(i), entityMeta.getIdArray()[0], rowData[pkIndex]);
				}
				// 业务主键
				if (hasBizId && StringUtil.isBlank(rowData[bizIdColIndex])) {
					rowData[bizIdColIndex] = entityMeta.getBusinessIdGenerator().getId(entityMeta.getTableName(),
							signature, relatedColumnNames, relatedColValue, null, businessIdType,
							entityMeta.getBizIdLength(), entityMeta.getBizIdSequenceSize());
					// 回写业务主键值
					BeanUtil.setProperty(entities.get(i), entityMeta.getBusinessIdField(), rowData[bizIdColIndex]);
				}
			}
		}
		String saveAllNotExistSql = generateSqlHandler.generateSql(entityMeta, null);
		List<Object[]> realParams = paramValues;
		String realSql = saveAllNotExistSql;
		if (sqlToyContext.hasSqlInterceptors()) {
			SqlToyConfig sqlToyConfig = new SqlToyConfig(DataSourceUtils.getDialect(dbType));
			sqlToyConfig.setSqlType(SqlType.insert);
			sqlToyConfig.setSql(saveAllNotExistSql);
			sqlToyConfig.setParamsName(entityMeta.getFieldsArray(true));
			SqlToyResult sqlToyResult = new SqlToyResult(saveAllNotExistSql, paramValues.toArray());
			sqlToyResult = doInterceptors(sqlToyContext, sqlToyConfig, OperateType.insertAll, sqlToyResult,
					entities.get(0).getClass(), dbType);
			realSql = sqlToyResult.getSql();
			realParams = CollectionUtil.arrayToList(sqlToyResult.getParamsValue());
		}
		SqlExecuteStat.showSql("batch insert ignore existed records", realSql, null);
		// update 2026-9-6 sqlserver的rowversion列在merge/insert语句生成时已按校准判据排除,
		// 绑定参数行与类型数组需同步剔除该列,保持占位符与参数严格1:1(此前saveAllIgnoreExist路径遗漏)
		Integer[] bindTypes = entityMeta.getFieldsTypeArray(true);
		if (dbType != null && dbType.intValue() == DBType.SQLSERVER) {
			String[] fields = entityMeta.getFieldsArray(true);
			int rejectCnt = 0;
			for (String field : fields) {
				if (entityMeta.isRowVersionField(entityMeta.getFieldMeta(field))) {
					rejectCnt++;
				}
			}
			if (rejectCnt > 0) {
				int[] keepIndex = new int[fields.length - rejectCnt];
				int keepCnt = 0;
				for (int i = 0; i < fields.length; i++) {
					if (!entityMeta.isRowVersionField(entityMeta.getFieldMeta(fields[i]))) {
						keepIndex[keepCnt++] = i;
					}
				}
				Integer[] tmpTypes = new Integer[keepCnt];
				for (int i = 0; i < keepCnt; i++) {
					tmpTypes[i] = bindTypes[keepIndex[i]];
				}
				bindTypes = tmpTypes;
				List<Object[]> filteredParams = new ArrayList<Object[]>(realParams.size());
				for (Object[] row : realParams) {
					Object[] tmpRow = new Object[keepCnt];
					for (int i = 0; i < keepCnt; i++) {
						tmpRow[i] = row[keepIndex[i]];
					}
					filteredParams.add(tmpRow);
				}
				realParams = filteredParams;
			}
		}
		return SqlUtil.batchUpdateForPOJO(sqlToyContext.getTypeHandler(), realSql, realParams, bindTypes, batchSize,
				autoCommit, conn, dbType);
	}

	/**
	 * @param sqlToyContext
	 * @param entity
	 * @param entityMeta
	 * @param nullFunction
	 * @param forceUpdateFields
	 * @param conn
	 * @param dbType
	 * @param tableName
	 * @return
	 * @throws Exception 单笔记录修改
	 */
	public static Long update(SqlToyContext sqlToyContext, Serializable entity, EntityMeta entityMeta,
			String nullFunction, String[] forceUpdateFields, Connection conn, final Integer dbType, String tableName)
			throws Exception {
		String realTable = entityMeta.getSchemaTable(tableName, dbType);
		// 无主键
		if (entityMeta.getIdArray() == null) {
			throw new IllegalArgumentException("table [" + realTable
					+ "] has no primary key, does not match the update/updateAll rules, please check the table design!");
		}
		// 全部是主键则无需update
		if (entityMeta.getRejectIdFieldArray(true) == null) {
			logger.warn(
					"table:{} all fields are primary keys and there is no field to update, skip the update operation!",
					realTable);
			return 0L;
		}
		// 构造全新的修改记录参数赋值反射(覆盖之前的)
		ReflectPropsHandler handler = getUpdateReflectHandler(null, forceUpdateFields,
				sqlToyContext.getUnifyFieldsHandler());
		// 字段加密处理
		handler = getSecureReflectHandler(handler, sqlToyContext.getFieldsSecureProvider(),
				sqlToyContext.getDesensitizeProvider(), entityMeta.getSecureFields());
		Object[] fieldsValues = BeanUtil.reflectBeanToAry(entity, entityMeta.getFieldsArray(true), null, handler);
		// 判断主键是否为空
		int pkIndex = entityMeta.getIdIndex() - entityMeta.getGeneratedColsCnt();
		for (int i = pkIndex, end = pkIndex + entityMeta.getIdArray().length; i < end; i++) {
			if (StringUtil.isBlank(fieldsValues[i])) {
				throw new IllegalArgumentException("update table [" + realTable
						+ "] by entity, the primary key fields must be assigned, please check!");
			}
		}
		// 构建update语句
		String updateSql = generateUpdateSql(sqlToyContext.getUnifyFieldsHandler(), dbType, entityMeta, nullFunction,
				forceUpdateFields, realTable);
		if (updateSql == null) {
			throw new IllegalArgumentException(
					"update sql is null, the reason is that no fields to update are configured, please check!");
		}
		SqlToyConfig sqlToyConfig = new SqlToyConfig(DataSourceUtils.getDialect(dbType));
		sqlToyConfig.setSqlType(SqlType.update);
		sqlToyConfig.setSql(updateSql);
		sqlToyConfig.setParamsName(entityMeta.getFieldsArray(true));
		SqlToyResult sqlToyResult = new SqlToyResult(updateSql, fieldsValues);
		// 增加sql执行拦截器 update 2022-9-10
		sqlToyResult = doInterceptors(sqlToyContext, sqlToyConfig, OperateType.update, sqlToyResult, entity.getClass(),
				dbType);
		// 回写update公共属性值
		BeanUtil.backWriteUnifyFields(entity,
				BeanUtil.getUnifyFieldIndex(sqlToyContext.getUnifyFieldsHandler(), entityMeta.getFieldsArray(true), 2),
				fieldsValues);
		// update 2026-9-5 sqlserver的rowversion列在update set生成时被排除,绑定的参数数组需同步剔除,保持占位符对齐
		Object[] bindValues = fieldsValues;
		Integer[] bindTypes = entityMeta.getFieldsTypeArray(true);
		if (dbType != null && dbType.intValue() == DBType.SQLSERVER) {
			String[] fields = entityMeta.getFieldsArray(true);
			List<Object> tmpValues = new ArrayList<Object>();
			List<Integer> tmpTypes = new ArrayList<Integer>();
			for (int i = 0; i < fields.length; i++) {
				if (!entityMeta.isRowVersionField(entityMeta.getFieldMeta(fields[i]))) {
					tmpValues.add(fieldsValues[i]);
					tmpTypes.add(bindTypes[i]);
				}
			}
			if (tmpValues.size() != fields.length) {
				bindValues = tmpValues.toArray();
				bindTypes = tmpTypes.toArray(new Integer[0]);
			}
		}
		return SqlUtil.executeSql(sqlToyContext.getTypeHandler(), sqlToyResult.getSql(), bindValues, bindTypes, conn,
				dbType, null, false);
	}

	/**
	 * @param sqlToyContext
	 * @param entity
	 * @param nullFunction
	 * @param forceUpdateFields
	 * @param cascade
	 * @param generateSqlHandler
	 * @param forceCascadeClasses
	 * @param subTableForceUpdateProps
	 * @param conn
	 * @param dbType
	 * @param tableName
	 * @throws Exception 单个对象修改，包含接连修改
	 */
	public static Long update(SqlToyContext sqlToyContext, Serializable entity, String nullFunction,
			String[] forceUpdateFields, final boolean cascade, final GenerateSqlHandler generateSqlHandler,
			final Class[] forceCascadeClasses, final HashMap<Class, String[]> subTableForceUpdateProps, Connection conn,
			final Integer dbType, String tableName) throws Exception {
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entity.getClass());
		String realTable = entityMeta.getSchemaTable(tableName, dbType);
		// 无主键
		if (entityMeta.getIdArray() == null) {
			throw new IllegalArgumentException("table [" + realTable
					+ "] has no primary key, does not match the update/updateAll rules, please check the table design!");
		}
		// 全部是主键则无需update
		if (entityMeta.getRejectIdFieldArray(true) == null) {
			logger.warn(
					"table:{} all fields are primary keys and there is no field to update, skip the update operation!",
					realTable);
			return 0L;
		}
		Long updateCnt = update(sqlToyContext, entity, entityMeta, nullFunction, forceUpdateFields, conn, dbType,
				tableName);
		// 不存在级联操作
		if (!cascade || entityMeta.getCascadeModels().isEmpty()) {
			return updateCnt;
		}
		// 级联保存
		HashMap<Type, String> typeMap = new HashMap<Type, String>();
		// 即使子对象数据是null,也强制进行级联修改(null表示删除子表数据)
		if (forceCascadeClasses != null) {
			for (Type type : forceCascadeClasses) {
				typeMap.put(type, "");
			}
		}
		// 级联子表数据
		List subTableData = null;
		String[] forceUpdateProps = null;
		EntityMeta subTableEntityMeta;
		// 对子表进行级联处理
		for (TableCascadeModel cascadeModel : entityMeta.getCascadeModels()) {
			final String[] mappedFields = cascadeModel.getMappedFields();
			final Object[] mappedFieldValues = BeanUtil.reflectBeanToAry(entity, cascadeModel.getFields());
			subTableEntityMeta = sqlToyContext.getEntityMeta(cascadeModel.getMappedType());
			// update 2026-9-7
			// sqlserver的级联子表按目标库元数据校准rowversion判据(自SqlServerDialectUtils.update迁入)
			if (dbType != null && dbType.intValue() == DBType.SQLSERVER) {
				SqlServerDialectUtils.ensureRowVersionMeta(sqlToyContext, conn, dbType, null,
						cascadeModel.getMappedType());
			}
			forceUpdateProps = (subTableForceUpdateProps == null) ? null
					: subTableForceUpdateProps.get(cascadeModel.getMappedType());
			// oneToMany
			if (cascadeModel.getCascadeType() == 1) {
				subTableData = (List) BeanUtil.getProperty(entity, cascadeModel.getProperty());
			} else {
				subTableData = new ArrayList();
				Object item = BeanUtil.getProperty(entity, cascadeModel.getProperty());
				if (item != null) {
					subTableData.add(item);
				}
			}
			// 针对子表存量数据,调用级联修改的语句，分delete 和update两种操作 1、删除存量数据;2、设置存量数据状态为停用
			if (cascadeModel.getCascadeUpdateSql() != null && ((subTableData != null && !subTableData.isEmpty())
					|| typeMap.containsKey(cascadeModel.getMappedType()))) {
				SqlExecuteStat.debug("update existed records before cascade update sub table", null);
				// 根据quickvo配置文件针对cascade中update-cascade配置组织具体操作sql
				SqlToyResult sqlToyResult = SqlConfigParseUtils.processSql(cascadeModel.getCascadeUpdateSql(),
						mappedFields, mappedFieldValues, null);
				SqlToyConfig sqlToyConfig = new SqlToyConfig(DataSourceUtils.getDialect(dbType));
				sqlToyConfig.setSqlType(SqlType.update);
				sqlToyConfig.setSql(cascadeModel.getCascadeUpdateSql());
				sqlToyConfig.setParamsName(mappedFields);
				// 增加sql执行拦截器 update 2022-9-10
				sqlToyResult = doInterceptors(sqlToyContext, sqlToyConfig, OperateType.execute, sqlToyResult,
						cascadeModel.getMappedType(), dbType);
				SqlUtil.executeSql(sqlToyContext.getTypeHandler(), sqlToyResult.getSql(), sqlToyResult.getParamsValue(),
						null, conn, dbType, null, true);
			}
			// 子表数据不为空,采取saveOrUpdateAll操作
			if (subTableData != null && !subTableData.isEmpty()) {
				logger.info("update the main table:{} with its cascade sub table:{} for update operation!", realTable,
						subTableEntityMeta.getTableName());
				SqlExecuteStat.debug("cascade update sub table", null);
				// 回写关联字段赋值
				BeanUtil.batchSetProperties(subTableData, mappedFields, mappedFieldValues, true);
				// update 2020-07-30,针对mysql和postgresql、sqlite常用数据库做针对性处理
				// 这里需要进行修改,mysql\postgresql\sqlite 等存在缺陷(字段值不为null时会报错)
				if (dbType == DBType.MYSQL || dbType == DBType.MYSQL57 || dbType == DBType.TIDB
						|| dbType == DBType.DORIS || dbType == DBType.STARROCKS) {
					mysqlSaveOrUpdateAll(sqlToyContext, subTableEntityMeta, subTableData, null, forceUpdateProps, conn,
							dbType);
				} else if (dbType == DBType.POSTGRESQL14) {
					postgres14SaveOrUpdateAll(sqlToyContext, subTableEntityMeta, subTableData, null, forceUpdateProps,
							conn, dbType);
				} else if (dbType == DBType.OCEANBASE) {
					oceanBaseSaveOrUpdateAll(sqlToyContext, subTableEntityMeta, subTableData, null, forceUpdateProps,
							conn, dbType);
				} else if (dbType == DBType.SQLITE) {
					sqliteSaveOrUpdateAll(sqlToyContext, subTableEntityMeta, subTableData, null, forceUpdateProps, conn,
							dbType);
				}
				// db2/oracle/mssql/postgresql15+/gaussdb/kingbase/dm/h2 通过merge 方式
				else {
					saveOrUpdateAll(sqlToyContext, subTableData, sqlToyContext.getBatchSize(), subTableEntityMeta,
							forceUpdateProps, generateSqlHandler,
							// 设置关联外键字段的属性值(来自主表的主键)
							null, conn, dbType, null);
				}
			} else {
				logger.info(
						"update of the main table:{} with its cascade sub table:{} is not executed, sub table data is empty!",
						realTable, subTableEntityMeta.getTableName());
			}
		}
		return updateCnt;
	}

	// update 2020-07-30
	// update 级联操作时，子表会涉及saveOrUpdateAll动作,而mysql和postgresql 对应的
	// ON DUPLICATE KEY UPDATE 当字段为非空时报错，因此需特殊处理
	private static void mysqlSaveOrUpdateAll(SqlToyContext sqlToyContext, final EntityMeta entityMeta, List<?> entities,
			ReflectPropsHandler reflectPropsHandler, final String[] forceUpdateFields, Connection conn,
			final Integer dbType) throws Exception {
		int batchSize = sqlToyContext.getBatchSize();
		final String tableName = entityMeta.getSchemaTable(null, dbType);
		Long updateCnt = updateAll(sqlToyContext, entities, batchSize, forceUpdateFields, reflectPropsHandler, "ifnull",
				conn, dbType, null, tableName, true);
		// 如果修改的记录数量跟总记录数量一致,表示全部是修改
		if (updateCnt >= entities.size()) {
			logger.debug("updated records of the cascade sub table{} is:{}", tableName, updateCnt);
			return;
		}
		// mysql只支持identity,sequence 值忽略
		boolean isAssignPK = MySqlDialectUtils.allowAssignPKValue(entityMeta.getIdStrategy(), dbType);
		String insertSql = DialectExtUtils.generateInsertSql(sqlToyContext.getUnifyFieldsHandler(), dbType, entityMeta,
				entityMeta.getIdStrategy(), "ifnull", "NEXTVAL FOR " + entityMeta.getSequence(), isAssignPK, tableName)
				.replaceFirst("(?i)insert ", "insert ignore ");
		Long saveCnt = saveAll(sqlToyContext, entityMeta, entityMeta.getIdStrategy(), isAssignPK, insertSql, entities,
				batchSize, reflectPropsHandler, conn, dbType, null);
		logger.debug("cascade sub table:{} updated records:{}, saved records:{}", tableName, updateCnt, saveCnt);
	}

	// 针对oceanBase
	private static void oceanBaseSaveOrUpdateAll(SqlToyContext sqlToyContext, final EntityMeta entityMeta,
			List<?> entities, ReflectPropsHandler reflectPropsHandler, final String[] forceUpdateFields,
			Connection conn, final Integer dbType) throws Exception {
		int batchSize = sqlToyContext.getBatchSize();
		final String tableName = entityMeta.getSchemaTable(null, dbType);
		Long updateCnt = updateAll(sqlToyContext, entities, batchSize, forceUpdateFields, reflectPropsHandler, "nvl",
				conn, dbType, null, tableName, true);
		// 如果修改的记录数量跟总记录数量一致,表示全部是修改
		if (updateCnt >= entities.size()) {
			logger.debug("updated records of the cascade sub table{} is:{}", tableName, updateCnt);
			return;
		}
		Long saveCnt = saveAllIgnoreExist(sqlToyContext, entities, batchSize, entityMeta, new GenerateSqlHandler() {
			@Override
			public String generateSql(EntityMeta entityMeta, String[] forceUpdateFields) {
				PKStrategy pkStrategy = entityMeta.getIdStrategy();
				String sequence = entityMeta.getSequence() + ".nextval";
				if (pkStrategy != null && PKStrategy.IDENTITY.equals(pkStrategy)) {
					pkStrategy = PKStrategy.SEQUENCE;
					sequence = entityMeta.getFieldMeta(entityMeta.getIdArray()[0]).getDefaultValue();
				}
				return DialectExtUtils.mergeIgnore(sqlToyContext.getUnifyFieldsHandler(), dbType, entityMeta,
						pkStrategy, "dual", "nvl", sequence, OracleDialectUtils.allowAssignPKValue(pkStrategy),
						tableName);
			}
		}, reflectPropsHandler, conn, dbType, null);
		logger.debug("cascade sub table:{} updated records:{}, saved records:{}", tableName, updateCnt, saveCnt);
	}

	// 针对postgresql14以下版本 数据库
	private static void postgres14SaveOrUpdateAll(SqlToyContext sqlToyContext, final EntityMeta entityMeta,
			List<?> entities, ReflectPropsHandler reflectPropsHandler, final String[] forceUpdateFields,
			Connection conn, final Integer dbType) throws Exception {
		int batchSize = sqlToyContext.getBatchSize();
		final String tableName = entityMeta.getSchemaTable(null, dbType);
		Long updateCnt = updateAll(sqlToyContext, entities, batchSize, forceUpdateFields, reflectPropsHandler,
				"COALESCE", conn, dbType, null, tableName, true);
		// 如果修改的记录数量跟总记录数量一致,表示全部是修改
		if (updateCnt >= entities.size()) {
			logger.debug("updated records of the cascade sub table{} is:{}", tableName, updateCnt);
			return;
		}
		// identity不再伪造成sequence+DEFAULT(COALESCE(?,DEFAULT)在pg系是非法sql,DEFAULT只能裸用于VALUES项),
		// 统一改走saveAll:identity主键按rejectId反射,与insertIgnore省略主键列的sql保持占位符与参数对齐
		PKStrategy pkStrategy = entityMeta.getIdStrategy();
		String sequence = "nextval('" + entityMeta.getSequence() + "')";
		if ((dbType == DBType.GAUSSDB || dbType == DBType.OPENGAUSS || dbType == DBType.STARDB || dbType == DBType.OSCAR
				|| dbType == DBType.MOGDB || dbType == DBType.VASTBASE) && pkStrategy != null
				&& PKStrategy.SEQUENCE.equals(pkStrategy)) {
			sequence = entityMeta.getSequence() + ".nextval";
		}
		boolean isAssignPK = PostgreSqlDialectUtils.allowAssignPKValue(pkStrategy);
		if (dbType == DBType.GAUSSDB || dbType == DBType.MOGDB || dbType == DBType.STARDB || dbType == DBType.OSCAR
				|| dbType == DBType.OPENGAUSS || dbType == DBType.VASTBASE) {
			isAssignPK = OpenGaussDialectUtils.allowAssignPKValue(pkStrategy);
		}
		String insertIgnoreSql = DialectExtUtils.insertIgnore(sqlToyContext.getUnifyFieldsHandler(), dbType, entityMeta,
				pkStrategy, "COALESCE", sequence, isAssignPK, tableName);
		Long saveCnt = saveAll(sqlToyContext, entityMeta, pkStrategy, isAssignPK, insertIgnoreSql, entities, batchSize,
				reflectPropsHandler, conn, dbType, null);
		logger.debug("cascade sub table:{} updated records:{}, saved records:{}", tableName, updateCnt, saveCnt);
	}

	// 针对sqlite 数据库
	private static void sqliteSaveOrUpdateAll(SqlToyContext sqlToyContext, final EntityMeta entityMeta,
			List<?> entities, ReflectPropsHandler reflectPropsHandler, final String[] forceUpdateFields,
			Connection conn, final Integer dbType) throws Exception {
		int batchSize = sqlToyContext.getBatchSize();
		final String tableName = entityMeta.getSchemaTable(null, dbType);
		Long updateCnt = updateAll(sqlToyContext, entities, batchSize, forceUpdateFields, reflectPropsHandler, "ifnull",
				conn, dbType, null, tableName, true);
		// 如果修改的记录数量跟总记录数量一致,表示全部是修改
		if (updateCnt >= entities.size()) {
			logger.debug("updated records of the cascade sub table{} is:{}", tableName, updateCnt);
			return;
		}
		// sqlite只支持identity,sequence 值忽略
		boolean isAssignPK = SqliteDialectUtils.allowAssignPKValue(entityMeta.getIdStrategy());
		String insertSql = DialectExtUtils.generateInsertSql(sqlToyContext.getUnifyFieldsHandler(), dbType, entityMeta,
				entityMeta.getIdStrategy(), "ifnull", "NEXTVAL FOR " + entityMeta.getSequence(), isAssignPK, tableName)
				.replaceFirst("(?i)insert ", "insert or ignore ");
		Long saveCnt = saveAll(sqlToyContext, entityMeta, entityMeta.getIdStrategy(), isAssignPK, insertSql, entities,
				batchSize, reflectPropsHandler, conn, dbType, null);
		logger.debug("cascade sub table:{} updated records:{}, saved records:{}", tableName, updateCnt, saveCnt);
	}

	/**
	 * @param sqlToyContext
	 * @param entities
	 * @param batchSize
	 * @param forceUpdateFields
	 * @param reflectPropsHandler
	 * @param nullFunction
	 * @param conn
	 * @param dbType
	 * @param autoCommit
	 * @param tableName
	 * @param skipNull
	 * @return
	 * @throws Exception 批量对象修改
	 */
	public static Long updateAll(SqlToyContext sqlToyContext, List<?> entities, final int batchSize,
			final String[] forceUpdateFields, ReflectPropsHandler reflectPropsHandler, String nullFunction,
			Connection conn, final Integer dbType, final Boolean autoCommit, String tableName, boolean skipNull)
			throws Exception {
		if (entities == null || entities.isEmpty()) {
			return 0L;
		}
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entities.get(0).getClass());
		String realTable = entityMeta.getSchemaTable(tableName, dbType);
		// 无主键
		if (entityMeta.getIdArray() == null) {
			throw new IllegalArgumentException("table [" + realTable
					+ "] has no primary key, does not match the update/updateAll rules, please check the table design!");
		}
		// 全部是主键则无需update
		if (entityMeta.getRejectIdFieldArray(true) == null) {
			logger.warn(
					"table:{} all fields are primary keys and there is no field to update, skip the update operation!",
					realTable);
			return 0L;
		}
		// 构造全新的修改记录参数赋值反射(覆盖之前的)
		ReflectPropsHandler handler = getUpdateReflectHandler(reflectPropsHandler, forceUpdateFields,
				sqlToyContext.getUnifyFieldsHandler());
		// 字段加密处理
		handler = getSecureReflectHandler(handler, sqlToyContext.getFieldsSecureProvider(),
				sqlToyContext.getDesensitizeProvider(), entityMeta.getSecureFields());
		List<Object[]> paramsValues = BeanUtil.reflectBeansToInnerAry(entities, entityMeta.getFieldsArray(true), null,
				handler);
		// 批量回写update公共属性值(update 2026-1-20 移到判主键为null前面，避免数据被移除回写无法对应)
		BeanUtil.batchBackWriteUnifyFields(entities,
				BeanUtil.getUnifyFieldIndex(sqlToyContext.getUnifyFieldsHandler(), entityMeta.getFieldsArray(true), 2),
				paramsValues);
		// 判断主键是否为空
		int pkIndex = entityMeta.getIdIndex() - entityMeta.getGeneratedColsCnt();
		int end = pkIndex + entityMeta.getIdArray().length;
		int index = 0;
		// 累计多少行为空
		int skipCount = 0;
		Iterator<Object[]> iter = paramsValues.iterator();
		Object[] rowValues;
		while (iter.hasNext()) {
			rowValues = iter.next();
			for (int i = pkIndex; i < end; i++) {
				// 判断主键值是否为空
				if (StringUtil.isBlank(rowValues[i])) {
					// 跳过主键值为空的
					if (skipNull) {
						skipCount++;
						iter.remove();
						break;
					} else {
						throw new IllegalArgumentException("updateAll table [" + realTable
								+ "] by entity, the primary key fields must be assigned! row:" + index
								+ " pk field value is null!");
					}
				}
			}
			index++;
		}
		if (skipCount > 0) {
			logger.debug("a total of:{} rows were skipped for update because their primary key values are null!",
					skipCount);
		}
		// 构建update语句
		String updateSql = generateUpdateSql(sqlToyContext.getUnifyFieldsHandler(), dbType, entityMeta, nullFunction,
				forceUpdateFields, realTable);
		if (updateSql == null) {
			throw new IllegalArgumentException(
					"updateAll sql is null, the reason is that no fields to update are configured, please check!");
		}
		List<Object[]> realParams = paramsValues;
		String realSql = updateSql;
		if (sqlToyContext.hasSqlInterceptors()) {
			SqlToyConfig sqlToyConfig = new SqlToyConfig(DataSourceUtils.getDialect(dbType));
			sqlToyConfig.setSqlType(SqlType.update);
			sqlToyConfig.setSql(updateSql);
			sqlToyConfig.setParamsName(entityMeta.getFieldsArray(true));
			SqlToyResult sqlToyResult = new SqlToyResult(updateSql, paramsValues.toArray());
			sqlToyResult = doInterceptors(sqlToyContext, sqlToyConfig, OperateType.updateAll, sqlToyResult,
					entities.get(0).getClass(), dbType);
			realSql = sqlToyResult.getSql();
			realParams = CollectionUtil.arrayToList(sqlToyResult.getParamsValue());
		}
		SqlExecuteStat.showSql("batch update [" + realParams.size() + "] rows", realSql, null);
		// update 2026-9-5 sqlserver的rowversion列在update set生成时被排除,绑定的参数需同步剔除,保持占位符对齐
		Integer[] bindTypes = entityMeta.getFieldsTypeArray(true);
		if (dbType != null && dbType.intValue() == DBType.SQLSERVER) {
			String[] fields = entityMeta.getFieldsArray(true);
			boolean hasRowVersion = false;
			for (String field : fields) {
				if (entityMeta.isRowVersionField(entityMeta.getFieldMeta(field))) {
					hasRowVersion = true;
					break;
				}
			}
			if (hasRowVersion) {
				int[] keepIndex = new int[fields.length];
				int keepCnt = 0;
				for (int i = 0; i < fields.length; i++) {
					if (!entityMeta.isRowVersionField(entityMeta.getFieldMeta(fields[i]))) {
						keepIndex[keepCnt++] = i;
					}
				}
				Integer[] tmpTypes = new Integer[keepCnt];
				for (int i = 0; i < keepCnt; i++) {
					tmpTypes[i] = bindTypes[keepIndex[i]];
				}
				bindTypes = tmpTypes;
				List<Object[]> filteredParams = new ArrayList<Object[]>(realParams.size());
				for (Object[] row : realParams) {
					Object[] tmpRow = new Object[keepCnt];
					for (int i = 0; i < keepCnt; i++) {
						tmpRow[i] = row[keepIndex[i]];
					}
					filteredParams.add(tmpRow);
				}
				realParams = filteredParams;
			}
		}
		return SqlUtil.batchUpdateForPOJO(sqlToyContext.getTypeHandler(), realSql, realParams, bindTypes, batchSize,
				autoCommit, conn, dbType);
	}

	/**
	 * @param sqlToyContext
	 * @param entity
	 * @param conn
	 * @param dbType
	 * @param tableName
	 * @return
	 * @throws Exception 删除单个对象以及其级联表数据
	 */
	public static Long delete(SqlToyContext sqlToyContext, Serializable entity, Connection conn, final Integer dbType,
			final String tableName) throws Exception {
		if (entity == null) {
			return 0L;
		}
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entity.getClass());
		String realTable = entityMeta.getSchemaTable(tableName, dbType);
		if (null == entityMeta.getIdArray()) {
			throw new IllegalArgumentException(
					"delete operation, table [" + realTable + "] has no primary key, please check the table design!");
		}
		Object[] idValues = BeanUtil.reflectBeanToAry(entity, entityMeta.getIdArray());
		Integer[] parameterTypes = new Integer[idValues.length];
		boolean validator = true;
		// 判断主键值是否为空
		for (int i = 0, n = idValues.length; i < n; i++) {
			parameterTypes[i] = entityMeta.getColumnJdbcType(entityMeta.getIdArray()[i]);
			if (StringUtil.isBlank(idValues[i])) {
				validator = false;
				break;
			}
		}
		if (!validator) {
			throw new IllegalArgumentException(realTable
					+ " delete operate is illegal,table must has primary key and all primaryKey's value must has value!");
		}
		// 级联删除子表数据
		if (!entityMeta.getCascadeModels().isEmpty()) {
			int mapFieldSize;
			for (TableCascadeModel cascadeModel : entityMeta.getCascadeModels()) {
				EntityMeta subMeta;
				// 如果数据库本身通过on delete cascade机制，则sqltoy无需进行删除操作
				if (cascadeModel.isDelete()) {
					subMeta = sqlToyContext.getEntityMeta(cascadeModel.getMappedType());
					Object[] mainFieldValues = BeanUtil.reflectBeanToAry(entity, cascadeModel.getFields());
					mapFieldSize = cascadeModel.getFields().length;
					for (int i = 0; i < mapFieldSize; i++) {
						if (mainFieldValues[i] == null) {
							throw new IllegalArgumentException("table [" + realTable + "] cascade delete sub table ["
									+ subMeta.getTableName() + "], the mapped property [" + cascadeModel.getFields()[i]
									+ "] value is null, please check!");
						}
					}
					Integer[] subTableFieldType = new Integer[mapFieldSize];
					for (int i = 0, n = mapFieldSize; i < n; i++) {
						subTableFieldType[i] = subMeta.getColumnJdbcType(cascadeModel.getMappedFields()[i]);
					}
					SqlExecuteStat.debug("cascade delete", null);
					SqlToyConfig sqlToyConfig = new SqlToyConfig(DataSourceUtils.getDialect(dbType));
					sqlToyConfig.setSqlType(SqlType.delete);
					sqlToyConfig.setSql(cascadeModel.getDeleteSubTableSql());
					sqlToyConfig.setParamsName(cascadeModel.getFields());
					SqlToyResult sqlToyResult = new SqlToyResult(cascadeModel.getDeleteSubTableSql(), mainFieldValues);
					// 增加sql执行拦截器 update 2022-9-10
					sqlToyResult = doInterceptors(sqlToyContext, sqlToyConfig, OperateType.deleteAll, sqlToyResult,
							cascadeModel.getMappedType(), dbType);
					SqlUtil.executeSql(sqlToyContext.getTypeHandler(), sqlToyResult.getSql(),
							sqlToyResult.getParamsValue(), subTableFieldType, conn, dbType, null, true);
				}
			}
		}
		// 与deleteAll保持一致,对delete语句做保留字转换(idArgWhereSql中的保留字以sqlserver的[]形式标记)
		String deleteSql = ReservedWordsUtil
				.convertSql("delete from ".concat(realTable).concat(" ").concat(entityMeta.getIdArgWhereSql()), dbType);
		SqlToyConfig sqlToyConfig = new SqlToyConfig(DataSourceUtils.getDialect(dbType));
		sqlToyConfig.setSqlType(SqlType.delete);
		sqlToyConfig.setSql(deleteSql);
		sqlToyConfig.setParamsName(entityMeta.getIdArray());
		SqlToyResult sqlToyResult = new SqlToyResult(deleteSql, idValues);
		// 增加sql执行拦截器 update 2022-9-10
		sqlToyResult = doInterceptors(sqlToyContext, sqlToyConfig, OperateType.delete, sqlToyResult, entity.getClass(),
				dbType);
		return SqlUtil.executeSql(sqlToyContext.getTypeHandler(), sqlToyResult.getSql(), sqlToyResult.getParamsValue(),
				parameterTypes, conn, dbType, null, true);
	}

	/**
	 * @param sqlToyContext
	 * @param entities
	 * @param batchSize
	 * @param conn
	 * @param dbType
	 * @param autoCommit
	 * @param tableName
	 * @return
	 * @throws Exception 批量删除对象并级联删除掉子表数据
	 */
	public static Long deleteAll(SqlToyContext sqlToyContext, List<?> entities, final int batchSize, Connection conn,
			final Integer dbType, final Boolean autoCommit, final String tableName) throws Exception {
		if (null == entities || entities.isEmpty()) {
			return 0L;
		}
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entities.get(0).getClass());
		String realTable = entityMeta.getSchemaTable(tableName, dbType);
		if (null == entityMeta.getIdArray()) {
			throw new IllegalArgumentException("delete/deleteAll operation, table [" + realTable
					+ "] has no primary key, please check the table design!");
		}
		List<Object[]> idValues = BeanUtil.reflectBeansToInnerAry(entities, entityMeta.getIdArray(), null, null);
		// 判断主键值是否存在空
		Object[] idsValue;
		for (int i = 0, n = idValues.size(); i < n; i++) {
			idsValue = idValues.get(i);
			for (Object obj : idsValue) {
				if (StringUtil.isBlank(obj)) {
					throw new IllegalArgumentException(
							"row:" + i + " pk field value is blank, batch delete is based on primary key, table ["
									+ realTable + "] primary key must not be null!");
				}
			}
		}
		int idsLength = entityMeta.getIdArray().length;
		Integer[] parameterTypes = new Integer[idsLength];
		for (int i = 0, n = idsLength; i < n; i++) {
			parameterTypes[i] = entityMeta.getColumnJdbcType(entityMeta.getIdArray()[i]);
		}
		// 数据库是否支持(field1,field2) in (:value1,:value2) 多字段in
		boolean supportMultiFieldIn = DataSourceUtils.isSupportMultiFieldIn(dbType);
		// 级联批量删除子表数据
		if (!entityMeta.getCascadeModels().isEmpty()) {
			EntityMeta subTableMeta;
			String delSubTableSql;
			int mapFieldSize;
			int meter = 0;
			for (TableCascadeModel cascadeModel : entityMeta.getCascadeModels()) {
				// 如果数据库本身通过on delete cascade机制，则sqltoy无需进行删除操作
				if (cascadeModel.isDelete()) {
					subTableMeta = sqlToyContext.getEntityMeta(cascadeModel.getMappedType());
					List<Object[]> mainFieldValues = BeanUtil.reflectBeansToInnerAry(entities, cascadeModel.getFields(),
							null, null);
					mapFieldSize = cascadeModel.getFields().length;
					meter = 0;
					for (Object[] row : mainFieldValues) {
						for (int i = 0; i < mapFieldSize; i++) {
							if (row[i] == null) {
								throw new IllegalArgumentException(
										"row:" + meter + ", table [" + realTable + "] cascade delete sub table ["
												+ subTableMeta.getTableName() + "], the mapped property ["
												+ cascadeModel.getFields()[i] + "] value is null, please check!");
							}
						}
						meter++;
					}
					// 单主键或多主键且数据库支持in 多字段
					if (mapFieldSize == 1 || supportMultiFieldIn) {
						deleteByIds(sqlToyContext, subTableMeta, cascadeModel.getFields(),
								cascadeModel.getMappedFields(), conn, dbType, autoCommit,
								subTableMeta.getSchemaTable(null, null), entities);
					} else {
						Integer[] subTableFieldType = new Integer[mapFieldSize];
						for (int i = 0, n = mapFieldSize; i < n; i++) {
							subTableFieldType[i] = subTableMeta.getColumnJdbcType(cascadeModel.getMappedFields()[i]);
						}
						delSubTableSql = ReservedWordsUtil.convertSql(cascadeModel.getDeleteSubTableSql(), dbType);
						List<Object[]> realParams = mainFieldValues;
						String realSql = delSubTableSql;
						if (sqlToyContext.hasSqlInterceptors()) {
							SqlToyConfig sqlToyConfig = new SqlToyConfig(DataSourceUtils.getDialect(dbType));
							sqlToyConfig.setSqlType(SqlType.delete);
							sqlToyConfig.setSql(delSubTableSql);
							sqlToyConfig.setParamsName(cascadeModel.getFields());
							SqlToyResult sqlToyResult = new SqlToyResult(delSubTableSql, mainFieldValues.toArray());
							sqlToyResult = doInterceptors(sqlToyContext, sqlToyConfig, OperateType.deleteAll,
									sqlToyResult, cascadeModel.getMappedType(), dbType);
							realSql = sqlToyResult.getSql();
							realParams = CollectionUtil.arrayToList(sqlToyResult.getParamsValue());
						}
						SqlExecuteStat.showSql("cascade delete sub table records", realSql, null);
						SqlUtil.batchUpdateForPOJO(sqlToyContext.getTypeHandler(), realSql, realParams,
								subTableFieldType, sqlToyContext.getBatchSize(), null, conn, dbType);
					}
				}
			}
		}
		// 单主键或多主键且数据库支持in 多字段
		if (idsLength == 1 || supportMultiFieldIn) {
			return deleteByIds(sqlToyContext, entityMeta, entityMeta.getIdArray(), entityMeta.getIdArray(), conn,
					dbType, autoCommit, realTable, entities);
		}

		String deleteSql = ReservedWordsUtil
				.convertSql("delete from ".concat(realTable).concat(" ").concat(entityMeta.getIdArgWhereSql()), dbType);
		List<Object[]> realParams = idValues;
		String realSql = deleteSql;
		if (sqlToyContext.hasSqlInterceptors()) {
			SqlToyConfig sqlToyConfig = new SqlToyConfig(DataSourceUtils.getDialect(dbType));
			sqlToyConfig.setSqlType(SqlType.delete);
			sqlToyConfig.setSql(deleteSql);
			sqlToyConfig.setParamsName(entityMeta.getIdArray());
			SqlToyResult sqlToyResult = new SqlToyResult(deleteSql, idValues.toArray());
			sqlToyResult = doInterceptors(sqlToyContext, sqlToyConfig, OperateType.deleteAll, sqlToyResult,
					entities.get(0).getClass(), dbType);
			realSql = sqlToyResult.getSql();
			realParams = CollectionUtil.arrayToList(sqlToyResult.getParamsValue());
		}
		SqlExecuteStat.showSql("batch delete [" + realParams.size() + "] rows", realSql, null);
		return SqlUtil.batchUpdateForPOJO(sqlToyContext.getTypeHandler(), realSql, realParams, parameterTypes,
				batchSize, autoCommit, conn, dbType);
	}

	/**
	 * 根据主键或外键组织in (?,?) 或多字段in模式批量删除
	 * 
	 * @param sqlToyContext
	 * @param entityMeta
	 * @param fields
	 * @param mappedFields
	 * @param conn
	 * @param dbType
	 * @param autoCommit
	 * @param tableName
	 * @param entities
	 * @return
	 * @throws Exception
	 */
	private static Long deleteByIds(SqlToyContext sqlToyContext, EntityMeta entityMeta, String[] fields,
			String[] mappedFields, Connection conn, Integer dbType, Boolean autoCommit, String tableName,
			List<?> entities) throws Exception {
		StringBuilder sqlString;
		String key = "entities";
		if (dbType == DBType.CLICKHOUSE) {
			// alter table tableName delete where
			sqlString = new StringBuilder("alter table ".concat(tableName).concat(" delete where "));
		} else {
			sqlString = new StringBuilder("delete from ".concat(tableName).concat(" where "));
		}
		// 构造成:where (column1,column2) in ((:entities.field1,:entities.field2))
		// 是否多个条件字段
		int fieldsCnt = fields.length;
		if (fieldsCnt > 1) {
			sqlString.append(" (");
		}
		int index = 0;
		StringBuilder argsParamName = new StringBuilder();
		for (String mappedField : mappedFields) {
			if (index > 0) {
				sqlString.append(",");
				argsParamName.append(",");
			}
			sqlString.append(ReservedWordsUtil.convertWord(entityMeta.getColumnName(mappedField), dbType));
			argsParamName.append(":").append(key).append(".").append(fields[index]);
			index++;
		}
		if (fieldsCnt > 1) {
			sqlString.append(") ");
		}
		sqlString.append(" in ");
		if (fieldsCnt > 1) {
			sqlString.append(" ((").append(argsParamName.toString()).append("))");
		} else {
			sqlString.append(" (").append(argsParamName.toString()).append(")");
		}
		String dialect = DataSourceUtils.getDialect(dbType);
		// 借用查询语句参数提取机制
		QueryExecutor query = new QueryExecutor(sqlString.toString(), MapKit.map(key, entities));
		SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(query, SqlType.delete, dialect);
		QueryExecutorBuilder.initQueryExecutor(sqlToyContext, query.getInnerModel(), sqlToyConfig, false, false);
		SqlToyResult queryParam = SqlConfigParseUtils.processSql(sqlToyConfig.getSql(null),
				sqlToyConfig.getParamsName(), query.getInnerModel().getParamsValue(sqlToyContext, sqlToyConfig),
				dialect);
		queryParam = doInterceptors(sqlToyContext, sqlToyConfig, OperateType.deleteAll, queryParam,
				entityMeta.getEntityClass(), dbType);
		return SqlUtil.executeSql(sqlToyContext.getTypeHandler(), queryParam.getSql(), queryParam.getParamsValue(),
				null, conn, dbType, autoCommit, false);
	}

	/**
	 * @param sqlToyContext
	 * @param entity
	 * @param paramsNamed
	 * @param conn
	 * @param dbType
	 * @param tableName
	 * @param uniqueSqlHandler
	 * @return 进行唯一性查询判定
	 */
	public static boolean isUnique(SqlToyContext sqlToyContext, Serializable entity, final String[] paramsNamed,
			Connection conn, final Integer dbType, final String tableName, final UniqueSqlHandler uniqueSqlHandler,
			final Integer queryTimeout) {
		try {
			EntityMeta entityMeta = sqlToyContext.getEntityMeta(entity.getClass());
			String[] realParamNamed;
			Object[] paramValues;
			// 边界必须与getFieldsArray(false)对齐(含生成列,尾部为主键),用(false)变体取长度,
			// 避免混用(true)变体导致有计算列时非主键/主键分界前移、字段漏判
			int rejectIdFieldsSize = (entityMeta.getRejectIdFieldArray(false) == null) ? 0
					: entityMeta.getRejectIdFieldArray(false).length;
			// 如果没有特别指定属性，则通过数据是否为null来判断具体的字段
			if (paramsNamed == null || paramsNamed.length == 0) {
				String[] fieldsArray = entityMeta.getFieldsArray(false);
				Object[] fieldValues = BeanUtil.reflectBeanToAry(entity, fieldsArray);
				List paramValueList = new ArrayList();
				List<String> paramNames = new ArrayList<String>();
				boolean hasNoPkField = false;
				for (int i = 0; i < fieldValues.length; i++) {
					if (null != fieldValues[i]) {
						// 非主键字段
						if (i < rejectIdFieldsSize) {
							hasNoPkField = true;
						}
						// 存在主键字段，则主键值仅仅作为返回结果的比较，判断是否是记录本身
						if (i >= rejectIdFieldsSize && hasNoPkField) {
							break;
						}
						paramNames.add(fieldsArray[i]);
						paramValueList.add(fieldValues[i]);
					}
				}
				paramValues = paramValueList.toArray();
				realParamNamed = paramNames.toArray(new String[0]);
			} else {
				realParamNamed = paramsNamed;
				paramValues = BeanUtil.reflectBeanToAry(entity, paramsNamed);
			}
			// 取出符合条件的2条记录
			String queryStr = uniqueSqlHandler.process(entityMeta, realParamNamed, tableName, 2);
			// update 2023-3-5 优化参数值为null场景，构造成paramName is null
			SqlToyResult sqlToyResult = SqlConfigParseUtils.processSql(queryStr, realParamNamed, paramValues);
			// 增加sql执行拦截器 update 2022-9-10
			SqlToyConfig sqlToyConfig = new SqlToyConfig(DataSourceUtils.getDialect(dbType));
			sqlToyConfig.setSqlType(SqlType.search);
			sqlToyConfig.setSql(queryStr);
			sqlToyConfig.setParamsName(realParamNamed);
			sqlToyResult = doInterceptors(sqlToyContext, sqlToyConfig, OperateType.unique, sqlToyResult,
					entity.getClass(), dbType);
			SqlExecuteStat.showSql("uniqueness validation", sqlToyResult.getSql(), sqlToyResult.getParamsValue());
			List result = SqlUtil.findByJdbcQuery(sqlToyContext.getTypeHandler(), sqlToyResult.getSql(),
					sqlToyResult.getParamsValue(), null, null, null, conn, dbType, false, null, -1, -1, queryTimeout);
			SqlExecuteStat.debug("uniqueness validation result", "record count:{}", result.size());
			if (result.size() == 0) {
				return true;
			}
			if (result.size() > 1) {
				return false;
			}
			// 表没有主键,单条记录算重复
			if (null == entityMeta.getIdArray()) {
				return false;
			}
			boolean allPK = false;
			// 判断是否是主键字段的唯一性验证
			if (realParamNamed.length == entityMeta.getIdArray().length) {
				allPK = true;
				for (String field : realParamNamed) {
					if (!entityMeta.getFieldMeta(field).isPK()) {
						allPK = false;
						break;
					}
				}
			}
			// 针对主键字段的唯一性验证,查询有记录则表示主键已经存在
			if (allPK) {
				return false;
			}
			// 判断是否是本身
			Object[] idValues = BeanUtil.reflectBeanToAry(entity, entityMeta.getIdArray());
			List compareValues = (List) result.get(0);
			// 相等表示唯一
			boolean isEqual = true;
			for (int i = 0, n = idValues.length; i < n; i++) {
				// result 第一列数据为固定的1(select 1,pk1,pk2模式),因此compareValues(i+1);
				if (null == idValues[i] || null == compareValues.get(i + 1)
						|| !idValues[i].toString().equals(compareValues.get(i + 1).toString())) {
					isEqual = false;
					break;
				}
			}
			return isEqual;
		} catch (Exception e) {
			logger.error("uniqueness query failed:{}", e.getMessage());
			logger.error("isUnique method execution failed", e);
			throw new DataAccessException(
					"isUnique query on entity=" + entity.getClass().getName() + " occurs exception:" + e.getMessage(),
					e);
		}
	}

	/**
	 * @param queryStr
	 * @return 判断是否复杂分页查询(union, 多表关联 、 存在top 、 distinct等)
	 */
	public static boolean isComplexPageQuery(String queryStr) {
		// 清除不必要的字符并转小写
		String tmpQuery = SqlUtil.clearMistyChars(queryStr.toLowerCase(Locale.ROOT), " ");
		// 字面量内容掩为等长空白,规避字面量内的join/union/','/'('参与复杂度判定
		// (hasUnion/getSymMarkIndexExcludeKeyWords内部亦会掩码,此处先掩码保证fromLastStr等截取扫描同样不受字面量干扰)
		tmpQuery = SqlConfigParseUtils.maskLiterals(tmpQuery, false);
		boolean isComplexQuery = SqlUtil.hasUnion(tmpQuery, false);
		// from 和 where之间有","表示多表查询
		if (!isComplexQuery) {
			int fromIndex = SqlUtil.getSymMarkIndexExcludeKeyWords(tmpQuery, SELECT_REGEX, FROM_REGEX, 0);
			int fromWhereIndex = SqlUtil.getSymMarkIndexExcludeKeyWords(tmpQuery, FROM_REGEX, WHERE_REGEX,
					(fromIndex < 1) ? 0 : (fromIndex - 1));
			String fromLastStr = (fromWhereIndex == -1) ? tmpQuery.substring(fromIndex)
					: tmpQuery.substring(fromIndex, fromWhereIndex);
			if (fromLastStr.indexOf(",") != -1 || fromLastStr.indexOf(" join ") != -1
					|| fromLastStr.indexOf("(") != -1) {
				isComplexQuery = true;
			}

			// 不存在union且非复杂关联查询
			if (!isComplexQuery) {
				// 截取select 到 from之间的字段
				String tmpColumn = tmpQuery.substring(0, fromIndex);
				if (tmpColumn.indexOf(" top ") != -1 || tmpColumn.indexOf(" distinct ") != -1) {
					isComplexQuery = true;
				}
			}
		}
		return isComplexQuery;
	}

	/**
	 * @param sql
	 * @return 判断是否有order by 和union 逻辑语句
	 */
	public static boolean hasOrderByOrUnion(String sql) {
		String unDisturbSql = clearDisturbSql(sql);
		if (StringUtil.matches(unDisturbSql, UNION_PATTERN) || StringUtil.matches(unDisturbSql, ORDER_BY_PATTERN)) {
			return true;
		}
		return false;
	}

	/**
	 * @param sql
	 * @return 去除掉sql中的所有对称的括号中的内容，排除干扰
	 *         字面量内容不参与判定:先将'...'字面量内部掩为等长空白,规避字面量内的'('、')'干扰括号剔除
	 */
	public static String clearDisturbSql(String sql) {
		// 字面量内容掩为等长空白,掩码串与原串等长,位置偏移不受影响
		String maskedSql = SqlConfigParseUtils.maskLiterals(sql, false);
		StringBuilder lastSql = new StringBuilder(maskedSql);
		// 找到第一个select 所对称的from位置，排除掉子查询中的内容
		int fromIndex = StringUtil.getSymMarkMatchIndex(SELECT_REGEX, FROM_REGEX, maskedSql.toLowerCase(Locale.ROOT),
				0);
		if (fromIndex != -1) {
			lastSql.delete(0, fromIndex);
		}
		// 删除所有对称的括号中的内容
		int start = lastSql.indexOf("(");
		int symMarkEnd;
		while (start != -1) {
			symMarkEnd = StringUtil.getSymMarkIndex("(", ")", lastSql.toString(), start);
			if (symMarkEnd != -1) {
				lastSql.delete(start, symMarkEnd + 1);
				start = lastSql.indexOf("(");
			} else {
				break;
			}
		}
		return lastSql.toString();
	}

	/**
	 * @param sql
	 * @return 去除掉sql中的所有对称的select 和 from 中的内容，排除干扰
	 */
	private static String clearSymSelectFromSql(String sql) {
		// 先转化为小写
		String realSql = sql.toLowerCase(Locale.ROOT);
		StringBuilder lastSql = new StringBuilder(realSql);
		String SELECT_REGEX = "\\Wselect\\s+";
		String FROM_REGEX = "\\sfrom[\\(|\\s+]";

		// 删除所有对称的括号中的内容
		int start = StringUtil.matchIndex(realSql, SELECT_REGEX);
		int symMarkEnd;
		while (start != -1) {
			symMarkEnd = StringUtil.getSymMarkMatchIndex(SELECT_REGEX, FROM_REGEX, lastSql.toString(), start);
			if (symMarkEnd != -1) {
				lastSql.delete(start + 1, symMarkEnd + 5);
				start = StringUtil.matchIndex(lastSql.toString(), SELECT_REGEX);
			} else {
				break;
			}
		}
		return lastSql.toString();
	}

	/**
	 * @param sqlToyConfig
	 * @param sqlToyContext
	 * @param storeSql
	 * @param inParamValues
	 * @param outParamTypes
	 * @param moreResult
	 * @param conn
	 * @param dbType
	 * @param fetchSize
	 * @return
	 * @throws Exception 通用的存储过程调用，inParam需放在outParam前面
	 */
	public static StoreResult executeStore(final SqlToyConfig sqlToyConfig, final SqlToyContext sqlToyContext,
			final String storeSql, final Object[] inParamValues, final Integer[] outParamTypes,
			final boolean moreResult, final Connection conn, final Integer dbType, final int fetchSize,
			final Integer timeout) throws Exception {
		CallableStatement callStat = null;
		ResultSet rs = null;
		return (StoreResult) SqlUtil.callableStatementProcess(null, callStat, rs, new CallableStatementResultHandler() {
			@Override
			public void execute(Object obj, CallableStatement callStat, ResultSet rs) throws Exception {
				try {
					callStat = conn.prepareCall(storeSql);
					if (fetchSize > 0) {
						callStat.setFetchSize(fetchSize);
					}
					Integer realTimeout = sqlToyConfig.getQueryTimeout();
					if (timeout != null && timeout > 0) {
						realTimeout = timeout;
					}
					if (realTimeout != null && realTimeout > 0) {
						callStat.setQueryTimeout(realTimeout);
					} else if (SqlToyConstants.defaultStatementTimeout != null
							&& SqlToyConstants.defaultStatementTimeout > 0) {
						callStat.setQueryTimeout(SqlToyConstants.defaultStatementTimeout);
					}
					boolean isFirstResult = StringUtil.matches(storeSql, STORE_PATTERN);
					int addIndex = isFirstResult ? 1 : 0;
					SqlUtil.setParamsValue(sqlToyContext.getTypeHandler(), conn, dbType, callStat, inParamValues, null,
							addIndex);
					int inCount = (inParamValues == null) ? 0 : inParamValues.length;
					int outCount = (outParamTypes == null) ? 0 : outParamTypes.length;
					// 注册输出参数
					if (outCount != 0) {
						if (isFirstResult) {
							callStat.registerOutParameter(1, outParamTypes[0]);
						}
						for (int i = addIndex; i < outCount; i++) {
							callStat.registerOutParameter(i + inCount + 1, outParamTypes[i]);
						}
					}
					StoreResult storeResult = new StoreResult();
					// 存在多个返回集合
					if (moreResult) {
						boolean hasResult = callStat.execute();
						List<String[]> labelsList = new ArrayList<String[]>();
						List<String[]> labelTypesList = new ArrayList<String[]>();
						List<List> dataSets = new ArrayList<List>();
						int meter = 0;
						SqlToyConfig notFirstConfig = new SqlToyConfig(sqlToyConfig.getId(), sqlToyConfig.getSql());
						while (hasResult) {
							rs = callStat.getResultSet();
							if (rs != null) {
								QueryResult tempResult = ResultUtils.processResultSet(dbType, sqlToyContext,
										(meter == 0) ? sqlToyConfig : notFirstConfig, conn, rs, null, null, null, 0);
								labelsList.add(tempResult.getLabelNames());
								labelTypesList.add(tempResult.getLabelTypes());
								dataSets.add(tempResult.getRows());
								meter++;
							}
							hasResult = callStat.getMoreResults();
						}
						storeResult.setLabelsList(labelsList);
						storeResult.setLabelTypesList(labelTypesList);
						List[] moreResults = new List[dataSets.size()];
						dataSets.toArray(moreResults);
						storeResult.setMoreResults(moreResults);
						// 默认第一个集合作为后续sql 配置处理的对象(如缓存翻译、格式化等)
						if (dataSets.size() > 0) {
							storeResult.setLabelNames(labelsList.get(0));
							storeResult.setLabelTypes(labelTypesList.get(0));
							storeResult.setRows(dataSets.get(0));
						}
					} else {
						boolean hasResult = callStat.execute();
						if (hasResult) {
							rs = callStat.getResultSet();
							if (rs != null) {
								QueryResult tempResult = ResultUtils.processResultSet(dbType, sqlToyContext,
										sqlToyConfig, conn, rs, null, null, null, 0);
								storeResult.setLabelNames(tempResult.getLabelNames());
								storeResult.setLabelTypes(tempResult.getLabelTypes());
								storeResult.setRows(tempResult.getRows());
							}
						}
					}

					// 有返回参数如:(?=call (? in,? out) )
					if (outCount != 0) {
						Object[] outParams = new Object[outCount];
						if (isFirstResult) {
							outParams[0] = callStat.getObject(1);
						}
						for (int i = addIndex; i < outCount; i++) {
							outParams[i] = callStat.getObject(i + inCount + 1);
						}
						storeResult.setOutResult(outParams);
					}
					storeResult.setUpdateCount(Long.valueOf(callStat.getUpdateCount()));
					this.setResult(storeResult);
				} catch (Exception e) {
					throw e;
				} finally {
					try {
						if (rs != null) {
							rs.close();
						}
					} catch (SQLException e) {
					}
					try {
						if (callStat != null) {
							callStat.close();
						}
					} catch (SQLException e) {
					}
				}
			}
		});
	}

	/**
	 * @param entityMeta
	 * @param preHandler
	 * @param unifyFieldsHandler
	 * @return 构造新增记录参数反射赋值处理器
	 */
	public static ReflectPropsHandler getAddReflectHandler(EntityMeta entityMeta, final ReflectPropsHandler preHandler,
			IUnifyFieldsHandler unifyFieldsHandler) {
		// 数据版本，新增记录时初始化
		final DataVersionConfig versionConfig = (entityMeta == null) ? null : entityMeta.getDataVersion();
		if (unifyFieldsHandler == null && versionConfig == null) {
			return preHandler;
		}
		final Map<String, Object> keyValues = (unifyFieldsHandler == null) ? null
				: unifyFieldsHandler.createUnifyFields();
		if ((keyValues == null || keyValues.isEmpty()) && versionConfig == null) {
			return preHandler;
		}
		Integer dataVersion = 1;
		// 数据版本号以日期开头
		if (versionConfig != null && versionConfig.isStartDate()) {
			dataVersion = Integer.valueOf(DateUtil.formatDate(DateUtil.getNowTime(), DateUtil.FORMAT.DATE_8CHAR) + 1);
		}
		// 强制修改字段赋值
		IgnoreCaseSet tmpSet = (unifyFieldsHandler == null) ? null : unifyFieldsHandler.forceUpdateFields();
		final IgnoreCaseSet forceUpdateFields = (!SqlToyThreadDataHolder.useUnifyFields() || tmpSet == null)
				? new IgnoreCaseSet()
				: tmpSet;
		final Integer realVersion = dataVersion;
		ReflectPropsHandler handler = new ReflectPropsHandler() {
			@Override
			public void process() {
				if (preHandler != null) {
					preHandler.setPropertyIndexMap(this.getPropertyIndexMap());
					preHandler.setRowIndex(this.getRowIndex());
					preHandler.setRowData(this.getRowData());
					preHandler.process();
				}
				if (keyValues != null) {
					for (Map.Entry<String, Object> entry : keyValues.entrySet()) {
						if (StringUtil.isBlank(this.getValue(entry.getKey()))
								|| forceUpdateFields.contains(entry.getKey())) {
							this.setValue(entry.getKey(), entry.getValue());
						}
					}
				}
				if (versionConfig != null) {
					this.setValue(versionConfig.getField(), realVersion);
				}
			}
		};
		return handler;
	}

	/**
	 * @param preHandler
	 * @param forceUpdateProps
	 * @param unifyFieldsHandler
	 * @return 构造修改记录参数反射赋值处理器
	 */
	public static ReflectPropsHandler getUpdateReflectHandler(final ReflectPropsHandler preHandler,
			String[] forceUpdateProps, IUnifyFieldsHandler unifyFieldsHandler) {
		if (unifyFieldsHandler == null || !SqlToyThreadDataHolder.useUnifyFields()) {
			return preHandler;
		}
		final Map<String, Object> keyValues = unifyFieldsHandler.updateUnifyFields();
		if (keyValues == null || keyValues.isEmpty()) {
			return preHandler;
		}
		// update操作强制更新字段优先
		final Set<String> forceSet = new HashSet<String>();
		if (forceUpdateProps != null && forceUpdateProps.length > 0) {
			for (String field : forceUpdateProps) {
				forceSet.add(field.toLowerCase(Locale.ROOT).replace("_", ""));
			}
		}
		// 强制修改字段赋值
		IgnoreCaseSet tmpSet = unifyFieldsHandler.forceUpdateFields();
		final IgnoreCaseSet forceUpdateFields = (tmpSet == null) ? new IgnoreCaseSet() : tmpSet;
		ReflectPropsHandler handler = new ReflectPropsHandler() {
			@Override
			public void process() {
				if (preHandler != null) {
					preHandler.setPropertyIndexMap(this.getPropertyIndexMap());
					preHandler.setRowIndex(this.getRowIndex());
					preHandler.setRowData(this.getRowData());
					preHandler.process();
				}
				// 修改操作
				for (Map.Entry<String, Object> entry : keyValues.entrySet()) {
					// 统一修改字段不在强制更新字段范围内
					if (!forceSet.contains(entry.getKey().toLowerCase(Locale.ROOT))) {
						if (StringUtil.isBlank(this.getValue(entry.getKey()))
								|| forceUpdateFields.contains(entry.getKey())) {
							this.setValue(entry.getKey(), entry.getValue());
						}
					}
				}
			}
		};
		return handler;
	}

	/**
	 * @param preHandler
	 * @param fieldsSecureProvider
	 * @param desensitizeProvider
	 * @param secureFields
	 * @return 对字段值进行加密
	 */
	public static ReflectPropsHandler getSecureReflectHandler(final ReflectPropsHandler preHandler,
			final FieldsSecureProvider fieldsSecureProvider, final DesensitizeProvider desensitizeProvider,
			List<FieldSecureConfig> secureFields) {
		if (fieldsSecureProvider == null || secureFields == null || secureFields.isEmpty()) {
			return preHandler;
		}
		ReflectPropsHandler handler = new ReflectPropsHandler() {
			@Override
			public void process() {
				if (preHandler != null) {
					preHandler.setPropertyIndexMap(this.getPropertyIndexMap());
					preHandler.setRowIndex(this.getRowIndex());
					preHandler.setRowData(this.getRowData());
					preHandler.process();
				}
				Object value;
				String contents;
				String field;
				String sourceField;
				// 提取加密的字段，加密跟脱敏不能同时存在
				Set<String> secureColumns = new HashSet<>();
				for (FieldSecureConfig config : secureFields) {
					if (SecureType.ENCRYPT.equals(config.getSecureType())) {
						secureColumns.add(config.getField());
					}
				}
				// 加密操作
				for (FieldSecureConfig config : secureFields) {
					field = config.getField();
					sourceField = config.getSourceField();
					// 安全脱敏便于检索的字段，优先依据其来源加密字段
					if (StringUtil.isNotBlank(sourceField)) {
						value = this.getValue(sourceField);
					} else {
						value = this.getValue(field);
					}
					if (value != null) {
						contents = value.toString();
						if (!"".equals(contents)) {
							// 加密
							if (SecureType.ENCRYPT.equals(config.getSecureType())) {
								this.setValue(field, fieldsSecureProvider.encrypt(contents));
							} // 脱敏
							else {
								if (!secureColumns.contains(field)) {
									this.setValue(field, desensitizeProvider.desensitize(contents, config.getMask()));
								}
							}
						}
					}
				}
			}
		};
		return handler;
	}

	/**
	 * @param idFields
	 * @param prepHandler
	 * @param forceUpdateProps
	 * @param unifyFieldsHandler
	 * @return 构造创建和修改记录时的反射
	 */
	public static ReflectPropsHandler getSaveOrUpdateReflectHandler(final String[] idFields,
			final ReflectPropsHandler prepHandler, String[] forceUpdateProps, IUnifyFieldsHandler unifyFieldsHandler) {
		if (unifyFieldsHandler == null) {
			return prepHandler;
		}
		final Map<String, Object> addKeyValues = unifyFieldsHandler.createUnifyFields();
		final Map<String, Object> updateKeyValues = SqlToyThreadDataHolder.useUnifyFields()
				? unifyFieldsHandler.updateUnifyFields()
				: null;
		if ((addKeyValues == null || addKeyValues.isEmpty())
				&& (updateKeyValues == null || updateKeyValues.isEmpty())) {
			return prepHandler;
		}
		// update操作强制更新字段优先
		final Set<String> forceSet = new HashSet<String>();
		if (forceUpdateProps != null && forceUpdateProps.length > 0) {
			for (String field : forceUpdateProps) {
				forceSet.add(field.toLowerCase(Locale.ROOT).replace("_", ""));
			}
		}
		// 强制修改字段赋值
		IgnoreCaseSet tmpSet = unifyFieldsHandler.forceUpdateFields();
		final IgnoreCaseSet forceUpdateFields = (tmpSet == null) ? new IgnoreCaseSet() : tmpSet;
		final int idLength = (idFields == null) ? 0 : idFields.length;
		// 构造一个新的包含update和save 的字段处理
		ReflectPropsHandler handler = new ReflectPropsHandler() {
			@Override
			public void process() {
				if (prepHandler != null) {
					prepHandler.setPropertyIndexMap(this.getPropertyIndexMap());
					prepHandler.setRowIndex(this.getRowIndex());
					prepHandler.setRowData(this.getRowData());
					prepHandler.process();
				}
				// 主键为空表示save操作
				if (idLength > 0 && this.getValue(idFields[0]) == null && addKeyValues != null) {
					for (Map.Entry<String, Object> entry : addKeyValues.entrySet()) {
						if (StringUtil.isBlank(this.getValue(entry.getKey()))) {
							this.setValue(entry.getKey(), entry.getValue());
						}
					}
				}
				// 修改属性值
				if (updateKeyValues != null) {
					for (Map.Entry<String, Object> entry : updateKeyValues.entrySet()) {
						// 统一修改字段不在强制更新字段范围内
						if (!forceSet.contains(entry.getKey().toLowerCase(Locale.ROOT))) {
							if (StringUtil.isBlank(this.getValue(entry.getKey()))
									|| forceUpdateFields.contains(entry.getKey())) {
								this.setValue(entry.getKey(), entry.getValue());
							}
						}
					}
				}
			}
		};
		return handler;
	}

	/**
	 * @param queryStr
	 * @return 提取sql中参数的个数(按标准SQL ''成对转义识别字面量,字面量内的?/:xxx不参与计数)
	 */
	public static int getParamsCount(String queryStr) {
		return getParamsCount(queryStr, false);
	}

	/**
	 * 提取sql中参数的个数,字面量内容不参与计数 (规避'50?'等字面量中的?/:xxx被误计入,导致分页参数错位或数量不匹配)
	 * 
	 * @param queryStr
	 * @param backslashEscape true时\'不终结字面量(mysql系);false时仅''成对转义(标准SQL)
	 * @return
	 */
	public static int getParamsCount(String queryStr, boolean backslashEscape) {
		if (StringUtil.isBlank(queryStr)) {
			return 0;
		}
		String sql = SqlConfigParseUtils.clearDblQuestMark(queryStr);
		// 字面量内容掩码(与原串等长),规避字面量内的参数形态干扰计数
		String maskedSql = SqlConfigParseUtils.maskLiterals(sql, backslashEscape);
		// 判断sql中参数模式，?或:named 模式，两种模式不可以混合使用
		if (sql.indexOf(SqlConfigParseUtils.ARG_NAME) == -1) {
			return StringUtil.matchCnt(maskedSql, SqlToyConstants.SQL_NAMED_PATTERN, 1);
		}
		return StringUtil.matchCnt(maskedSql, SqlConfigParseUtils.ARG_REGEX);
	}

	/**
	 * @param sqlToyContext
	 * @param entity
	 * @return 判断主键值是否为空，用于saveOrUpdate判断是否save
	 */
	public static boolean isEmptyPK(SqlToyContext sqlToyContext, Serializable entity) {
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entity.getClass());
		if (entityMeta.getIdArray() != null && entityMeta.getIdArray().length > 0) {
			Object[] idValues = BeanUtil.reflectBeanToAry(entity, entityMeta.getIdArray());
			for (Object obj : idValues) {
				if (null == obj) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * @param sql
	 * @param reflectPropsHandler
	 * @param unifyFieldsHandler
	 * @return 针对批量sql执行，判断是新增还是修改，并统一填充公共字段信息
	 */
	public static ReflectPropsHandler wrapReflectWithUnifyFields(String sql, ReflectPropsHandler reflectPropsHandler,
			IUnifyFieldsHandler unifyFieldsHandler) {
		if ((reflectPropsHandler == null && unifyFieldsHandler == null) || StringUtil.isBlank(sql)) {
			return null;
		}
		ReflectPropsHandler result = null;
		// insert 语句
		if (StringUtil.matches(sql.trim(), "(?i)^insert\\s+into\\W")) {
			result = getAddReflectHandler(null, reflectPropsHandler, unifyFieldsHandler);
		} // update
		else if (StringUtil.matches(sql.trim(), "(?i)^update\\s+")
				|| StringUtil.matches(sql.trim(), "(?i)^merge\\s+into\\W")
				|| StringUtil.matches(sql.trim(), "(?i)^replace\\s+into\\W")) {
			result = getUpdateReflectHandler(reflectPropsHandler, null, unifyFieldsHandler);
		}
		return result;
	}

	/**
	 * @param sqlToyContext
	 * @param sqlToyConfig
	 * @param operateType
	 * @param sqlToyResult
	 * @param entityClass
	 * @param dbType
	 * @return 执行自定义sql拦截器, 对sql进行二次加工，比如加入租户过滤等
	 */
	public static SqlToyResult doInterceptors(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig,
			OperateType operateType, SqlToyResult sqlToyResult, Class entityClass, Integer dbType) {
		if (!sqlToyContext.hasSqlInterceptors()) {
			return sqlToyResult;
		}
		SqlToyResult result = sqlToyResult;
		for (SqlInterceptor interceptor : sqlToyContext.getSqlInterceptors()) {
			result = interceptor.decorate(sqlToyContext, sqlToyConfig, operateType, result, entityClass, dbType);
		}
		return result;
	}

	/**
	 * 统一根据主键是否赋值，来调整save行为的主键策略
	 * 
	 * @param entityMeta
	 * @param entity
	 * @param dbType
	 * @return
	 */
	public static PKStrategy getSavePKStrategy(EntityMeta entityMeta, Serializable entity, Integer dbType) {
		PKStrategy pkStrategy = entityMeta.getIdStrategy();
		// 主键值已经存在，则主键策略改为assign，避免跳号
		if (pkStrategy != null && PKStrategy.SEQUENCE.equals(pkStrategy)) {
			Object id = BeanUtil.getProperty(entity, entityMeta.getIdArray()[0]);
			if (StringUtil.isNotBlank(id)) {
				pkStrategy = PKStrategy.ASSIGN;
			}
		}
		return pkStrategy;
	}
}
