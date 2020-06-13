/**
 * 
 */
package org.sagacity.sqltoy.dialect.utils;

import static java.lang.System.out;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Type;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.sagacity.sqltoy.SqlExecuteStat;
import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.callback.CallableStatementResultHandler;
import org.sagacity.sqltoy.callback.PreparedStatementResultHandler;
import org.sagacity.sqltoy.callback.ReflectPropertyHandler;
import org.sagacity.sqltoy.callback.RowCallbackHandler;
import org.sagacity.sqltoy.callback.UpdateRowHandler;
import org.sagacity.sqltoy.config.SqlConfigParseUtils;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.FieldMeta;
import org.sagacity.sqltoy.config.model.OneToManyModel;
import org.sagacity.sqltoy.config.model.PKStrategy;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlToyResult;
import org.sagacity.sqltoy.config.model.SqlWithAnalysis;
import org.sagacity.sqltoy.config.model.UnifySqlParams;
import org.sagacity.sqltoy.dialect.handler.GenerateSavePKStrategy;
import org.sagacity.sqltoy.dialect.handler.GenerateSqlHandler;
import org.sagacity.sqltoy.dialect.model.ReturnPkType;
import org.sagacity.sqltoy.dialect.model.SavePKStrategy;
import org.sagacity.sqltoy.executor.QueryExecutor;
import org.sagacity.sqltoy.model.IgnoreCaseSet;
import org.sagacity.sqltoy.model.QueryResult;
import org.sagacity.sqltoy.model.StoreResult;
import org.sagacity.sqltoy.plugins.sharding.ShardingUtils;
import org.sagacity.sqltoy.utils.BeanUtil;
import org.sagacity.sqltoy.utils.CollectionUtil;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;
import org.sagacity.sqltoy.utils.DebugUtil;
import org.sagacity.sqltoy.utils.ReservedWordsUtil;
import org.sagacity.sqltoy.utils.ResultUtils;
import org.sagacity.sqltoy.utils.SqlUtil;
import org.sagacity.sqltoy.utils.SqlUtilsExt;
import org.sagacity.sqltoy.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sqltoy-orm
 * @description 提供一些不同数据库都通用的逻辑处理,避免在各个数据库工具类中写重复代码
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:DialectUtils.java,Revision:v1.0,Date:2014年12月26日
 * @Modification {Date:2017-2-24,优化count sql处理逻辑,排除统计型查询导致的问题,本质统计性查询不应该用分页方式查询}
 * @Modification {Date:2018-1-6,优化对数据库表字段默认值的处理,提供统一的处理方法}
 * @Modification {Date:2018-1-22,增加业务主键生成赋值,同时对saveAll等操作返回生成的主键值映射到VO集合中}
 * @Modification {Date:2018-5-3,修复getCountBySql关于剔除order by部分的逻辑错误}
 * @Modification {Date:2018-9-25,修复select和from对称判断问题,影响分页查询时剔除from之前语句构建select
 *               count(1) from错误}
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public class DialectUtils {
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
	 * 判断日期格式
	 */
	public static final Pattern DATE_PATTERN = Pattern.compile("(\\:|\\-|\\.|\\/|\\s+)?\\d+");

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

	/**
	 * @todo 处理分页sql的参数
	 * @param sqlToyContext
	 * @param sqlToyConfig
	 * @param queryExecutor
	 * @param pageSql
	 * @param startIndex
	 * @param endIndex
	 * @return
	 * @throws Exception
	 */
	public static SqlToyResult wrapPageSqlParams(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig,
			QueryExecutor queryExecutor, String pageSql, Object startIndex, Object endIndex) throws Exception {
		String[] paramsNamed = queryExecutor.getParamsName(sqlToyConfig);
		Object[] paramsValue = queryExecutor.getParamsValue(sqlToyContext, sqlToyConfig);
		if (startIndex == null && endIndex == null) {
			return SqlConfigParseUtils.processSql(pageSql, paramsNamed, paramsValue);
		}
		String[] realParamNamed = null;
		Object[] realParamValue = null;
		int paramLength;
		// 针对sqlserver2008以及2005版本分页只需扩展一个参数
		int extendSize = (endIndex == null) ? 1 : 2;
		if (sqlToyConfig.isNamedParam()) {
			paramLength = paramsNamed.length;
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
			int preSqlParamCnt = getParamsCount(sqlToyConfig.getFastPreSql(null));
			int tailSqlParamCnt = getParamsCount(sqlToyConfig.getFastTailSql(null));
			paramLength = (paramsValue == null) ? 0 : paramsValue.length;
			realParamValue = new Object[paramLength + extendSize];
			if (sqlToyConfig.isHasFast()) {
				if (preSqlParamCnt > 0) {
					System.arraycopy(paramsValue, 0, realParamValue, 0, preSqlParamCnt);
				}
				realParamValue[preSqlParamCnt] = startIndex;
				if (extendSize == 2) {
					realParamValue[preSqlParamCnt + 1] = endIndex;
				}
				if (tailSqlParamCnt > 0) {
					System.arraycopy(paramsValue, preSqlParamCnt, realParamValue, preSqlParamCnt + extendSize,
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
		// 通过参数处理最终的sql和参数值
		return SqlConfigParseUtils.processSql(pageSql, realParamNamed, realParamValue);
	}

	/**
	 * @todo 实现普通的sql语句查询
	 * @param sqlToyContext
	 * @param sqlToyConfig
	 * @param sql
	 * @param paramsValue
	 * @param rowCallbackHandler
	 * @param conn
	 * @param dbType
	 * @param startIndex
	 * @param fetchSize
	 * @param maxRows
	 * @return
	 * @throws Exception
	 */
	public static QueryResult findBySql(final SqlToyContext sqlToyContext, final SqlToyConfig sqlToyConfig,
			final String sql, final Object[] paramsValue, final RowCallbackHandler rowCallbackHandler,
			final Connection conn, final Integer dbType, final int startIndex, final int fetchSize, final int maxRows)
			throws Exception {
		// 打印sql
		SqlExecuteStat.showSql(sql, paramsValue);
		PreparedStatement pst = conn.prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
		if (fetchSize > 0) {
			pst.setFetchSize(fetchSize);
		}
		if (maxRows > 0) {
			pst.setMaxRows(maxRows);
		}
		ResultSet rs = null;
		return (QueryResult) SqlUtil.preparedStatementProcess(null, pst, rs, new PreparedStatementResultHandler() {
			public void execute(Object obj, PreparedStatement pst, ResultSet rs) throws Exception {
				SqlUtil.setParamsValue(conn, dbType, pst, paramsValue, null, 0);
				rs = pst.executeQuery();
				this.setResult(ResultUtils.processResultSet(sqlToyContext, sqlToyConfig, conn, rs, rowCallbackHandler,
						null, startIndex));
			}
		});
	}

	/**
	 * @todo 实现普通的sql语句查询
	 * @param sqlToyContext
	 * @param sqlToyConfig
	 * @param sql
	 * @param paramsValue
	 * @param updateRowHandler
	 * @param conn
	 * @param dbType
	 * @param startIndex
	 * @return
	 * @throws Exception
	 */
	public static QueryResult updateFetchBySql(final SqlToyContext sqlToyContext, final SqlToyConfig sqlToyConfig,
			final String sql, final Object[] paramsValue, final UpdateRowHandler updateRowHandler,
			final Connection conn, final Integer dbType, final int startIndex) throws Exception {
		// 打印sql
		SqlExecuteStat.showSql(sql, paramsValue);
		PreparedStatement pst = null;
		if (updateRowHandler == null) {
			pst = conn.prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
		} else {
			pst = conn.prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_UPDATABLE);
		}
		ResultSet rs = null;
		return (QueryResult) SqlUtil.preparedStatementProcess(null, pst, rs, new PreparedStatementResultHandler() {
			public void execute(Object obj, PreparedStatement pst, ResultSet rs) throws Exception {
				SqlUtil.setParamsValue(conn, dbType, pst, paramsValue, null, 0);
				rs = pst.executeQuery();
				this.setResult(ResultUtils.processResultSet(sqlToyContext, sqlToyConfig, conn, rs, null,
						updateRowHandler, startIndex));
			}
		});
	}

	/**
	 * @todo 通用的查询记录总数
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
			final String sql, final Object[] paramsValue, final boolean isLastSql, final Connection conn,
			final Integer dbType) throws Exception {
		String lastCountSql;
		int paramCnt = 0;
		int withParamCnt = 0;
		// 通过配置直接给定的最优化count 语句
		if (isLastSql) {
			lastCountSql = sql;
		} else {
			String query_tmp = sql;
			String withSql = "";
			// with as分析器(避免每次做with 检测,提升效率)
			if (sqlToyConfig != null && sqlToyConfig.isHasWith()) {
				SqlWithAnalysis sqlWith = new SqlWithAnalysis(sql);
				query_tmp = sqlWith.getRejectWithSql();
				withSql = sqlWith.getWithSql();
			}
			int lastBracketIndex = query_tmp.lastIndexOf(")");
			int sql_from_index = 0;
			// sql不以from开头，截取from 后的部分语句
			if (StringUtil.indexOfIgnoreCase(query_tmp, "from") != 0) {
				sql_from_index = StringUtil.getSymMarkMatchIndex(SELECT_REGEX, FROM_REGEX, query_tmp.toLowerCase(), 0);
			}
			// 剔除order提高运行效率
			int orderByIndex = StringUtil.matchLastIndex(query_tmp, ORDER_BY_PATTERN);
			// order by 在from 之后
			if (orderByIndex > sql_from_index) {
				// 剔除order by 语句
				if (orderByIndex > lastBracketIndex) {
					query_tmp = query_tmp.substring(0, orderByIndex + 1);
				} else {
					// 剔除掉order by 后面语句对称的() 内容
					String orderJudgeSql = clearDisturbSql(query_tmp.substring(orderByIndex + 1));
					// 在order by 不在子查询内,说明可以整体切除掉order by
					if (orderJudgeSql.indexOf(")") == -1) {
						query_tmp = query_tmp.substring(0, orderByIndex + 1);
					}
				}
			}

			int groupIndex = StringUtil.matchLastIndex(query_tmp, GROUP_BY_PATTERN);
			// 判断group by 是否是内层，如select * from (select * from table group by)
			// 外层group by 必须要进行包裹(update by chenrenfei 2016-4-21)
			boolean isInnerGroup = false;
			if (groupIndex != -1) {
				isInnerGroup = clearDisturbSql(query_tmp.substring(groupIndex + 1)).lastIndexOf(")") != -1;
			}
			final StringBuilder countQueryStr = new StringBuilder();
			// 是否包含union,update 2012-11-21
			boolean hasUnion = StringUtil.matches(query_tmp, UNION_PATTERN);
			// 不包含distinct和group by 等,则剔除[select * ] from 变成select count(1) from
			// 性能最优
			if (!StringUtil.matches(query_tmp.trim(), DISTINCT_PATTERN) && !hasUnion
					&& (groupIndex == -1 || (groupIndex < lastBracketIndex && isInnerGroup))) {
				int selectIndex = StringUtil.matchIndex(query_tmp.toLowerCase(), SELECT_REGEX);
				// 截取出select 和from之间的语句
				String selectFields = (sql_from_index < 1) ? ""
						: query_tmp.substring(selectIndex + 6, sql_from_index).toLowerCase();
				// 剔除嵌套的子查询语句中select 和 from 之间的内容,便于判断统计函数的作用位置
				selectFields = clearSymSelectFromSql(selectFields);
				// 存在统计函数 update by chenrenfei ,date: 2017-2-24
				if (StringUtil.matches(selectFields, STAT_PATTERN)) {
					countQueryStr.append("select count(1) from (").append(query_tmp).append(") sag_count_tmpTable ");
				} else {
					// 截取from后的部分
					countQueryStr.append("select count(1) ")
							.append((sql_from_index != -1 ? query_tmp.substring(sql_from_index) : query_tmp));
				}
			} // 包含distinct 或包含union则直接将查询作为子表(普通做法)
			else {
				countQueryStr.append("select count(1) from (").append(query_tmp).append(") sag_count_tmpTable ");
			}

			paramCnt = getParamsCount(countQueryStr.toString());
			withParamCnt = getParamsCount(withSql);
			countQueryStr.insert(0, withSql + " ");
			lastCountSql = countQueryStr.toString();
		}
		final int paramCntFin = paramCnt;
		final int withParamCntFin = withParamCnt;
		Object[] realParamsTemp = null;
		if (paramsValue != null) {
			// 将from前的参数剔除
			realParamsTemp = isLastSql ? paramsValue
					: CollectionUtil.subtractArray(paramsValue, withParamCntFin,
							paramsValue.length - paramCntFin - withParamCntFin);
		}
		final Object[] realParams = realParamsTemp;
		// 打印sql
		SqlExecuteStat.showSql(lastCountSql, realParams);
		PreparedStatement pst = conn.prepareStatement(lastCountSql);
		ResultSet rs = null;
		return (Long) SqlUtil.preparedStatementProcess(null, pst, rs, new PreparedStatementResultHandler() {
			public void execute(Object obj, PreparedStatement pst, ResultSet rs) throws SQLException, IOException {
				long resultCount = 0;
				if (realParams != null) {
					SqlUtil.setParamsValue(conn, dbType, pst, realParams, null, 0);
				}
				rs = pst.executeQuery();
				if (rs.next()) {
					resultCount = rs.getLong(1);
				}
				this.setResult(resultCount);
			}
		});
	}

	/**
	 * @todo 统一将查询的sql参数由?形式变成:named形式(分页和查询随机记录时)
	 * @param sqlToyContext
	 * @param sqlToyConfig
	 * @param queryExecutor
	 * @param dialect
	 * @param wrapNamed
	 * @return
	 * @throws Exception
	 */
	public static SqlToyConfig getUnifyParamsNamedConfig(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig,
			QueryExecutor queryExecutor, String dialect, boolean wrapNamed) throws Exception {
		// 本身就是:named参数形式或sql中没有任何参数
		boolean isNamed = (sqlToyConfig.isNamedParam()
				|| sqlToyConfig.getSql(dialect).indexOf(SqlConfigParseUtils.ARG_NAME) == -1);
		boolean sameDialect = BeanUtil.equalsIgnoreType(sqlToyContext.getDialect(), dialect, true);
		// sql条件以:named形式并且当前数据库类型跟sqltoyContext配置的数据库类型一致
		if ((isNamed || !wrapNamed) && sameDialect && null == sqlToyConfig.getTablesShardings()) {
			return sqlToyConfig;
		}
		// clone一个,然后替换sql中的?并进行必要的参数加工
		SqlToyConfig result = sqlToyConfig.clone();
		if (!isNamed && wrapNamed) {
			UnifySqlParams sqlParams;
			// 将?形式的参数替换成:named形式参数
			// 存在fast查询
			if (result.isHasFast()) {
				sqlParams = convertParamsToNamed(result.getFastPreSql(dialect), 0);
				result.setFastPreSql(sqlParams.getSql());
				int index = sqlParams.getParamCnt();
				sqlParams = convertParamsToNamed(result.getFastSql(dialect), index);
				result.setFastSql(sqlParams.getSql());
				index = index + sqlParams.getParamCnt();
				sqlParams = convertParamsToNamed(result.getFastTailSql(dialect), index);
				result.setFastTailSql(sqlParams.getSql());
				result.setSql(result.getFastPreSql(dialect).concat(" (").concat(result.getFastSql(dialect)).concat(") ")
						.concat(result.getFastTailSql(dialect)));
				String[] paramsName = new String[index];
				for (int i = 0; i < index; i++) {
					paramsName[i] = SqlToyConstants.DEFAULT_PARAM_NAME + (i + 1);
				}
				result.setParamsName(paramsName);
			} else {
				sqlParams = convertParamsToNamed(result.getSql(dialect), 0);
				result.setSql(sqlParams.getSql());
				result.setParamsName(sqlParams.getParamsName());
			}
			sqlParams = convertParamsToNamed(result.getCountSql(dialect), 0);
			result.setCountSql(sqlParams.getSql());
			SqlConfigParseUtils.processFastWith(result, dialect);
		}

		// 替换sharding table
		ShardingUtils.replaceShardingSqlToyConfig(sqlToyContext, result, dialect,
				queryExecutor.getTableShardingParamsName(sqlToyConfig),
				queryExecutor.getTableShardingParamsValue(sqlToyConfig));
		return result;
	}

	/**
	 * @todo sql中替换?为:sagParamName+i形式,便于查询处理(主要针对分页和取随机记录的查询)
	 * @param sql
	 * @param startIndex
	 * @return
	 */
	public static UnifySqlParams convertParamsToNamed(String sql, int startIndex) {
		UnifySqlParams sqlParam = new UnifySqlParams();
		if (sql == null || sql.trim().equals(""))
			return sqlParam;
		// 不以转义符开始的问号
		// Pattern ARG_NAME_PATTERN = Pattern.compile("[^\\\\]\\?");
		Matcher m = SqlConfigParseUtils.ARG_NAME_PATTERN.matcher(sql);
		StringBuilder lastSql = new StringBuilder();
		String group;
		int start = 0;
		int index = 0;
		while (m.find()) {
			index++;
			group = m.group();
			lastSql.append(sql.substring(start, m.start()));
			lastSql.append(group.replace("?", ":" + SqlToyConstants.DEFAULT_PARAM_NAME + (index + startIndex)));
			start = m.end();
		}
		sqlParam.setParamCnt(index);
		if (index == 0) {
			sqlParam.setSql(sql);
		} else {
			// 添加尾部sql
			lastSql.append(sql.substring(start));
			String[] paramsName = new String[index];
			for (int i = 0; i < index; i++) {
				paramsName[i] = SqlToyConstants.DEFAULT_PARAM_NAME + (i + startIndex + 1);
			}
			sqlParam.setSql(lastSql.toString());
			sqlParam.setParamsName(paramsName);
		}
		return sqlParam;
	}

	/**
	 * @todo 执行批量保存或修改操作
	 * @param sqlToyContext
	 * @param entities
	 * @param batchSize
	 * @param entityMeta
	 * @param forceUpdateFields
	 * @param generateSqlHandler
	 * @param reflectPropertyHandler
	 * @param conn
	 * @param dbType
	 * @param autoCommit
	 * @return
	 * @throws Exception
	 */
	public static Long saveOrUpdateAll(SqlToyContext sqlToyContext, List<?> entities, final int batchSize,
			EntityMeta entityMeta, String[] forceUpdateFields, GenerateSqlHandler generateSqlHandler,
			ReflectPropertyHandler reflectPropertyHandler, Connection conn, final Integer dbType, Boolean autoCommit)
			throws Exception {
		// 重新构造修改或保存的属性赋值反调
		ReflectPropertyHandler handler = getSaveOrUpdateReflectHandler(sqlToyContext, entityMeta.getIdArray(),
				reflectPropertyHandler);
		List<Object[]> paramValues = BeanUtil.reflectBeansToInnerAry(entities, entityMeta.getFieldsArray(), null,
				handler, false, 0);
		int pkIndex = entityMeta.getIdIndex();
		// 是否存在业务ID
		boolean hasBizId = (entityMeta.getBusinessIdGenerator() == null) ? false : true;
		int bizIdColIndex = hasBizId ? entityMeta.getFieldIndex(entityMeta.getBusinessIdField()) : 0;
		// 标识符
		String signature = entityMeta.getBizIdSignature();
		Integer[] relatedColumn = entityMeta.getBizIdRelatedColIndex();
		int relatedColumnSize = (relatedColumn == null) ? 0 : relatedColumn.length;
		// 无主键以及多主键以及assign或通过generator方式产生主键策略
		if (null != entityMeta.getIdStrategy() && null != entityMeta.getIdGenerator()) {
			int bizIdLength = entityMeta.getBizIdLength();
			int idLength = entityMeta.getIdLength();
			Object[] rowData;
			Object[] relatedColValue = null;
			String idJdbcType = entityMeta.getIdType();
			String businessIdType = hasBizId ? entityMeta.getColumnJavaType(entityMeta.getBusinessIdField()) : "";
			for (int i = 0; i < paramValues.size(); i++) {
				rowData = (Object[]) paramValues.get(i);
				// 获取主键策略关联字段的值
				if (relatedColumn != null) {
					relatedColValue = new Object[relatedColumnSize];
					for (int meter = 0; meter < relatedColumnSize; meter++) {
						relatedColValue[meter] = rowData[relatedColumn[meter]];
					}
				}
				if (StringUtil.isBlank(rowData[pkIndex])) {
					rowData[pkIndex] = entityMeta.getIdGenerator().getId(entityMeta.getTableName(), signature,
							entityMeta.getBizIdRelatedColumns(), relatedColValue, null, idJdbcType, idLength,
							entityMeta.getBizIdSequenceSize());
					// 回写主键值
					BeanUtil.setProperty(entities.get(i), entityMeta.getIdArray()[0], rowData[pkIndex]);
				}
				if (hasBizId && StringUtil.isBlank(rowData[bizIdColIndex])) {
					rowData[bizIdColIndex] = entityMeta.getBusinessIdGenerator().getId(entityMeta.getTableName(),
							signature, entityMeta.getBizIdRelatedColumns(), relatedColValue, null, businessIdType,
							bizIdLength, entityMeta.getBizIdSequenceSize());
					// 回写主键值
					BeanUtil.setProperty(entities.get(i), entityMeta.getBusinessIdField(), rowData[bizIdColIndex]);
				}
			}
		}

		String saveOrUpdateSql = generateSqlHandler.generateSql(entityMeta, forceUpdateFields);
		SqlExecuteStat.showSql("saveOrUpdateSql=" + saveOrUpdateSql, null);
		return SqlUtil.batchUpdateByJdbc(saveOrUpdateSql, paramValues, batchSize, null, entityMeta.getFieldsTypeArray(),
				autoCommit, conn, dbType);
	}

	/**
	 * @todo 执行批量保存或修改操作
	 * @param sqlToyContext
	 * @param entities
	 * @param batchSize
	 * @param entityMeta
	 * @param generateSqlHandler
	 * @param reflectPropertyHandler
	 * @param conn
	 * @param dbType
	 * @param autoCommit
	 * @return
	 * @throws Exception
	 */
	public static Long saveAllIgnoreExist(SqlToyContext sqlToyContext, List<?> entities, final int batchSize,
			EntityMeta entityMeta, GenerateSqlHandler generateSqlHandler, ReflectPropertyHandler reflectPropertyHandler,
			Connection conn, final Integer dbType, Boolean autoCommit) throws Exception {
		// 构造全新的新增记录参数赋值反射(覆盖之前的)
		ReflectPropertyHandler handler = getAddReflectHandler(sqlToyContext, reflectPropertyHandler);
		List<Object[]> paramValues = BeanUtil.reflectBeansToInnerAry(entities, entityMeta.getFieldsArray(), null,
				handler, false, 0);
		int pkIndex = entityMeta.getIdIndex();
		// 是否存在业务ID
		boolean hasBizId = (entityMeta.getBusinessIdGenerator() == null) ? false : true;
		int bizIdColIndex = hasBizId ? entityMeta.getFieldIndex(entityMeta.getBusinessIdField()) : 0;
		// 标识符
		String signature = entityMeta.getBizIdSignature();
		Integer[] relatedColumn = entityMeta.getBizIdRelatedColIndex();
		String[] relatedColumnNames = entityMeta.getBizIdRelatedColumns();
		int relatedColumnSize = (relatedColumn == null) ? 0 : relatedColumn.length;
		// 无主键以及多主键以及assign或通过generator方式产生主键策略
		if (null != entityMeta.getIdStrategy() && null != entityMeta.getIdGenerator()) {
			int bizIdLength = entityMeta.getBizIdLength();
			int idLength = entityMeta.getIdLength();
			Object[] rowData;
			Object[] relatedColValue = null;
			String idJdbcType = entityMeta.getIdType();
			String businessIdType = hasBizId ? entityMeta.getColumnJavaType(entityMeta.getBusinessIdField()) : "";
			for (int i = 0; i < paramValues.size(); i++) {
				rowData = (Object[]) paramValues.get(i);
				// 关联字段赋值
				if (relatedColumn != null) {
					relatedColValue = new Object[relatedColumnSize];
					for (int meter = 0; meter < relatedColumnSize; meter++) {
						relatedColValue[meter] = rowData[relatedColumn[meter]];
						if (relatedColValue[meter] == null) {
							throw new IllegalArgumentException("对象:" + entityMeta.getEntityClass().getName()
									+ " 生成业务主键依赖的关联字段:" + relatedColumnNames[meter] + " 值为null!");
						}
					}
				}
				if (StringUtil.isBlank(rowData[pkIndex])) {
					rowData[pkIndex] = entityMeta.getIdGenerator().getId(entityMeta.getTableName(), signature,
							entityMeta.getBizIdRelatedColumns(), relatedColValue, null, idJdbcType, idLength,
							entityMeta.getBizIdSequenceSize());
					// 回写主键值
					BeanUtil.setProperty(entities.get(i), entityMeta.getIdArray()[0], rowData[pkIndex]);
				}
				if (hasBizId && StringUtil.isBlank(rowData[bizIdColIndex])) {
					rowData[bizIdColIndex] = entityMeta.getBusinessIdGenerator().getId(entityMeta.getTableName(),
							signature, entityMeta.getBizIdRelatedColumns(), relatedColValue, null, businessIdType,
							bizIdLength, entityMeta.getBizIdSequenceSize());
					// 回写业务主键值
					BeanUtil.setProperty(entities.get(i), entityMeta.getBusinessIdField(), rowData[bizIdColIndex]);
				}
			}
		}

		String saveAllNotExistSql = generateSqlHandler.generateSql(entityMeta, null);
		SqlExecuteStat.showSql("saveAllNotExistSql=" + saveAllNotExistSql, null);
		return SqlUtil.batchUpdateByJdbc(saveAllNotExistSql, paramValues, batchSize, null,
				entityMeta.getFieldsTypeArray(), autoCommit, conn, dbType);
	}

	/**
	 * @todo 产生对象对应的insert sql语句
	 * @param dbType
	 * @param entityMeta
	 * @param pkStrategy
	 * @param isNullFunction
	 * @param sequence
	 * @param isAssignPK
	 * @param tableName
	 * @return
	 */
	public static String generateInsertSql(Integer dbType, EntityMeta entityMeta, PKStrategy pkStrategy,
			String isNullFunction, String sequence, boolean isAssignPK, String tableName) {
		int columnSize = entityMeta.getFieldsArray().length;
		StringBuilder sql = new StringBuilder(columnSize * 20 + 30);
		StringBuilder values = new StringBuilder(columnSize * 2 - 1);
		sql.append("insert into ");
		sql.append(entityMeta.getSchemaTable(tableName));
		sql.append(" (");
		FieldMeta fieldMeta;
		String field;
		boolean isStart = true;
		boolean isSupportNULL = StringUtil.isBlank(isNullFunction) ? false : true;
		String columnName;
		for (int i = 0; i < columnSize; i++) {
			field = entityMeta.getFieldsArray()[i];
			fieldMeta = entityMeta.getFieldMeta(field);
			columnName = ReservedWordsUtil.convertWord(fieldMeta.getColumnName(), dbType);
			if (fieldMeta.isPK()) {
				// identity主键策略，且支持主键手工赋值
				if (pkStrategy.equals(PKStrategy.IDENTITY)) {
					// 目前只有mysql支持
					if (isAssignPK) {
						if (!isStart) {
							sql.append(",");
							values.append(",");
						}
						sql.append(columnName);
						values.append("?");
						isStart = false;
					}
				} // sequence 策略，oracle12c之后的identity机制统一转化为sequence模式
				else if (pkStrategy.equals(PKStrategy.SEQUENCE)) {
					if (!isStart) {
						sql.append(",");
						values.append(",");
					}
					sql.append(columnName);
					if (isAssignPK && isSupportNULL) {
						values.append(isNullFunction);
						values.append("(?,").append(sequence).append(")");
					} else {
						values.append(sequence);
					}
					isStart = false;
				} else {
					if (!isStart) {
						sql.append(",");
						values.append(",");
					}
					sql.append(columnName);
					values.append("?");
					isStart = false;
				}
			} else {
				if (!isStart) {
					sql.append(",");
					values.append(",");
				}
				sql.append(columnName);
				if (isSupportNULL && StringUtil.isNotBlank(fieldMeta.getDefaultValue())) {
					values.append(isNullFunction);
					values.append("(?,");
					processDefaultValue(values, dbType, fieldMeta.getType(), fieldMeta.getDefaultValue());
					values.append(")");
				} else {
					values.append("?");
				}
				isStart = false;
			}
		}
		// OVERRIDING SYSTEM VALUE
		sql.append(") ");
		/*
		 * if ((dbType == DBType.POSTGRESQL || dbType == DBType.GAUSSDB) && isAssignPK)
		 * { sql.append(" OVERRIDING SYSTEM VALUE "); }
		 */
		sql.append(" values (");
		sql.append(values);
		sql.append(")");
		return sql.toString();
	}

	/**
	 * @todo 处理加工对象基于db2、oracle、informix、sybase数据库的saveOrUpdateSql
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
	public static String getSaveOrUpdateSql(Integer dbType, EntityMeta entityMeta, PKStrategy pkStrategy,
			String[] forceUpdateFields, String fromTable, String isNullFunction, String sequence, boolean isAssignPK,
			String tableName) {
		String realTable = (tableName == null) ? entityMeta.getSchemaTable() : tableName;
		// 在无主键的情况下产生insert sql语句
		if (entityMeta.getIdArray() == null) {
			return generateInsertSql(dbType, entityMeta, pkStrategy, isNullFunction, sequence, isAssignPK, realTable);
		}
		boolean isSupportNUL = StringUtil.isBlank(isNullFunction) ? false : true;
		int columnSize = entityMeta.getFieldsArray().length;
		StringBuilder sql = new StringBuilder(columnSize * 30 + 100);
		String columnName;
		sql.append("merge into ");
		sql.append(entityMeta.getSchemaTable());
		sql.append(" ta ");
		sql.append(" using (select ");
		for (int i = 0; i < columnSize; i++) {
			columnName = entityMeta.getColumnName(entityMeta.getFieldsArray()[i]);
			columnName = ReservedWordsUtil.convertWord(columnName, dbType);
			if (i > 0) {
				sql.append(",");
			}
			sql.append("? as ");
			sql.append(columnName);
		}
		if (StringUtil.isNotBlank(fromTable)) {
			sql.append(" from ").append(fromTable);
		}
		sql.append(") tv on (");
		StringBuilder idColumns = new StringBuilder();
		// 组织on部分的主键条件判断
		for (int i = 0, n = entityMeta.getIdArray().length; i < n; i++) {
			columnName = entityMeta.getColumnName(entityMeta.getIdArray()[i]);
			columnName = ReservedWordsUtil.convertWord(columnName, dbType);
			if (i > 0) {
				sql.append(" and ");
				idColumns.append(",");
			}
			sql.append(" ta.").append(columnName).append("=tv.").append(columnName);
			idColumns.append("ta.").append(columnName);
		}
		sql.append(" ) ");
		// 排除id的其他字段信息
		StringBuilder insertRejIdCols = new StringBuilder();
		StringBuilder insertRejIdColValues = new StringBuilder();
		// 是否全部是ID,匹配上则无需进行更新，只需将未匹配上的插入即可
		boolean allIds = (entityMeta.getRejectIdFieldArray() == null);
		if (!allIds) {
			// update 操作
			sql.append(" when matched then update set ");
			int rejectIdColumnSize = entityMeta.getRejectIdFieldArray().length;
			// 需要被强制修改的字段
			HashSet<String> fupc = new HashSet<String>();
			if (forceUpdateFields != null) {
				for (String field : forceUpdateFields) {
					fupc.add(ReservedWordsUtil.convertWord(entityMeta.getColumnName(field), dbType));
				}
			}
			FieldMeta fieldMeta;
			// update 只针对非主键字段进行修改
			for (int i = 0; i < rejectIdColumnSize; i++) {
				fieldMeta = entityMeta.getFieldMeta(entityMeta.getRejectIdFieldArray()[i]);
				columnName = ReservedWordsUtil.convertWord(fieldMeta.getColumnName(), dbType);
				if (i > 0) {
					sql.append(",");
					insertRejIdCols.append(",");
					insertRejIdColValues.append(",");
				}
				sql.append(" ta.").append(columnName).append("=");
				// 强制修改
				if (fupc.contains(columnName)) {
					sql.append("tv.").append(columnName);
				} else {
					sql.append(isNullFunction);
					sql.append("(tv.").append(columnName);
					sql.append(",ta.").append(columnName);
					sql.append(")");
				}
				insertRejIdCols.append(columnName);
				// 存在默认值
				if (isSupportNUL && StringUtil.isNotBlank(fieldMeta.getDefaultValue())) {
					insertRejIdColValues.append(isNullFunction);
					insertRejIdColValues.append("(tv.").append(columnName).append(",");
					processDefaultValue(insertRejIdColValues, dbType, fieldMeta.getType(), fieldMeta.getDefaultValue());
					insertRejIdColValues.append(")");
				} else {
					insertRejIdColValues.append("tv.").append(columnName);
				}
			}
		}
		// 主键未匹配上则进行插入操作
		sql.append(" when not matched then insert (");
		String idsColumnStr = idColumns.toString();
		// 不考虑只有一个字段且还是主键的情况
		if (allIds) {
			sql.append(idsColumnStr.replaceAll("ta.", ""));
			sql.append(") values (");
			sql.append(idsColumnStr.replaceAll("ta.", "tv."));
		} else {
			sql.append(insertRejIdCols.toString());
			// sequence方式主键
			if (pkStrategy.equals(PKStrategy.SEQUENCE)) {
				columnName = entityMeta.getColumnName(entityMeta.getIdArray()[0]);
				columnName = ReservedWordsUtil.convertWord(columnName, dbType);
				sql.append(",");
				sql.append(columnName);
				sql.append(") values (");
				sql.append(insertRejIdColValues).append(",");
				if (isAssignPK && isSupportNUL) {
					sql.append(isNullFunction);
					sql.append("(tv.").append(columnName).append(",");
					sql.append(sequence).append(") ");
				} else {
					sql.append(sequence);
				}
			} else if (pkStrategy.equals(PKStrategy.IDENTITY)) {
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
				sql.append(idsColumnStr.replaceAll("ta.", ""));
				sql.append(") values (");
				sql.append(insertRejIdColValues).append(",");
				sql.append(idsColumnStr.replaceAll("ta.", "tv."));
			}
		}
		sql.append(")");
		return sql.toString();
	}

	/**
	 * @todo 处理加工对象基于db2、oracle、informix、sybase数据库的saveIgnoreExist
	 * @param dbType
	 * @param entityMeta
	 * @param pkStrategy
	 * @param fromTable
	 * @param isNullFunction
	 * @param sequence
	 * @param isAssignPK
	 * @param tableName
	 * @return
	 */
	public static String getSaveIgnoreExistSql(Integer dbType, EntityMeta entityMeta, PKStrategy pkStrategy,
			String fromTable, String isNullFunction, String sequence, boolean isAssignPK, String tableName) {
		// 在无主键的情况下产生insert sql语句
		String realTable = (tableName == null) ? entityMeta.getSchemaTable() : tableName;
		if (entityMeta.getIdArray() == null) {
			return generateInsertSql(dbType, entityMeta, pkStrategy, isNullFunction, sequence, isAssignPK, realTable);
		}
		boolean isSupportNUL = StringUtil.isBlank(isNullFunction) ? false : true;
		int columnSize = entityMeta.getFieldsArray().length;
		StringBuilder sql = new StringBuilder(columnSize * 30 + 100);
		String columnName;
		sql.append("merge into ");
		sql.append(realTable);
		sql.append(" ta ");
		sql.append(" using (select ");
		for (int i = 0; i < columnSize; i++) {
			columnName = entityMeta.getColumnName(entityMeta.getFieldsArray()[i]);
			columnName = ReservedWordsUtil.convertWord(columnName, dbType);
			if (i > 0) {
				sql.append(",");
			}
			sql.append("? as ");
			sql.append(columnName);
		}
		if (StringUtil.isNotBlank(fromTable)) {
			sql.append(" from ").append(fromTable);
		}
		sql.append(") tv on (");
		StringBuilder idColumns = new StringBuilder();
		// 组织on部分的主键条件判断
		for (int i = 0, n = entityMeta.getIdArray().length; i < n; i++) {
			columnName = entityMeta.getColumnName(entityMeta.getIdArray()[i]);
			columnName = ReservedWordsUtil.convertWord(columnName, dbType);
			if (i > 0) {
				sql.append(" and ");
				idColumns.append(",");
			}
			sql.append(" ta.").append(columnName).append("=tv.").append(columnName);
			idColumns.append("ta.").append(columnName);
		}
		sql.append(" ) ");
		// 排除id的其他字段信息
		StringBuilder insertRejIdCols = new StringBuilder();
		StringBuilder insertRejIdColValues = new StringBuilder();
		// 是否全部是ID,匹配上则无需进行更新，只需将未匹配上的插入即可
		boolean allIds = (entityMeta.getRejectIdFieldArray() == null);
		if (!allIds) {
			int rejectIdColumnSize = entityMeta.getRejectIdFieldArray().length;
			FieldMeta fieldMeta;
			// update 只针对非主键字段进行修改
			for (int i = 0; i < rejectIdColumnSize; i++) {
				fieldMeta = entityMeta.getFieldMeta(entityMeta.getRejectIdFieldArray()[i]);
				columnName = ReservedWordsUtil.convertWord(fieldMeta.getColumnName(), dbType);
				if (i > 0) {
					insertRejIdCols.append(",");
					insertRejIdColValues.append(",");
				}
				insertRejIdCols.append(columnName);
				// 存在默认值
				if (isSupportNUL && StringUtil.isNotBlank(fieldMeta.getDefaultValue())) {
					insertRejIdColValues.append(isNullFunction);
					insertRejIdColValues.append("(tv.").append(columnName).append(",");
					processDefaultValue(insertRejIdColValues, dbType, fieldMeta.getType(), fieldMeta.getDefaultValue());
					insertRejIdColValues.append(")");
				} else {
					insertRejIdColValues.append("tv.").append(columnName);
				}
			}
		}
		// 主键未匹配上则进行插入操作
		sql.append(" when not matched then insert (");
		String idsColumnStr = idColumns.toString();
		// 不考虑只有一个字段且还是主键的情况
		if (allIds) {
			sql.append(idsColumnStr.replaceAll("ta.", ""));
			sql.append(") values (");
			sql.append(idsColumnStr.replaceAll("ta.", "tv."));
		} else {
			sql.append(insertRejIdCols.toString());
			// sequence方式主键
			if (pkStrategy.equals(PKStrategy.SEQUENCE)) {
				columnName = entityMeta.getColumnName(entityMeta.getIdArray()[0]);
				columnName = ReservedWordsUtil.convertWord(columnName, dbType);
				sql.append(",");
				sql.append(columnName);
				sql.append(") values (");
				sql.append(insertRejIdColValues).append(",");
				if (isAssignPK && isSupportNUL) {
					sql.append(isNullFunction);
					sql.append("(tv.").append(columnName).append(",");
					sql.append(sequence).append(") ");
				} else {
					sql.append(sequence);
				}
			} else if (pkStrategy.equals(PKStrategy.IDENTITY)) {
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
				sql.append(idsColumnStr.replaceAll("ta.", ""));
				sql.append(") values (");
				sql.append(insertRejIdColValues).append(",");
				sql.append(idsColumnStr.replaceAll("ta.", "tv."));
			}
		}
		sql.append(")");
		return sql.toString();
	}

	/**
	 * @todo 统一对表字段默认值进行处理
	 * @param sql
	 * @param dbType
	 * @param fieldType
	 * @param defaultValue
	 */
	public static void processDefaultValue(StringBuilder sql, int dbType, int fieldType, String defaultValue) {
		if (fieldType == java.sql.Types.CHAR || fieldType == java.sql.Types.CLOB || fieldType == java.sql.Types.VARCHAR
				|| fieldType == java.sql.Types.NCHAR || fieldType == java.sql.Types.NVARCHAR
				|| fieldType == java.sql.Types.LONGVARCHAR || fieldType == java.sql.Types.LONGNVARCHAR
				|| fieldType == java.sql.Types.NCLOB) {
			if (!defaultValue.startsWith("'")) {
				sql.append("'");
			}
			sql.append(defaultValue);
			if (!defaultValue.endsWith("'")) {
				sql.append("'");
			}
		} else {
			String tmpValue = SqlToyConstants.getDefaultValue(dbType, defaultValue);
			if (tmpValue.startsWith("'") && tmpValue.endsWith("'")) {
				sql.append(tmpValue);
			}
			// 时间格式,避免默认日期没有单引号问题
			else if (fieldType == java.sql.Types.TIME || fieldType == java.sql.Types.DATE
					|| fieldType == java.sql.Types.TIME_WITH_TIMEZONE || fieldType == java.sql.Types.TIMESTAMP
					|| fieldType == java.sql.Types.TIMESTAMP_WITH_TIMEZONE) {
				if (StringUtil.matches(tmpValue, DATE_PATTERN)) {
					sql.append("'").append(tmpValue).append("'");
				} else {
					sql.append(tmpValue);
				}
			} else {
				sql.append(tmpValue);
			}
		}
	}

	/**
	 * @todo 产生对象update的语句
	 * @param dbType
	 * @param entityMeta
	 * @param nullFunction
	 * @param forceUpdateFields
	 * @param tableName
	 * @return
	 */
	public static String generateUpdateSql(Integer dbType, EntityMeta entityMeta, String nullFunction,
			String[] forceUpdateFields, String tableName) {
		if (entityMeta.getIdArray() == null)
			return null;
		StringBuilder sql = new StringBuilder(entityMeta.getFieldsArray().length * 30 + 30);
		sql.append(" update  ");
		sql.append(entityMeta.getSchemaTable(tableName));
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
		boolean isPostgre = (dbType == DBType.POSTGRESQL || dbType == DBType.GAUSSDB);
		for (int i = 0, n = entityMeta.getRejectIdFieldArray().length; i < n; i++) {
			fieldMeta = entityMeta.getFieldMeta(entityMeta.getRejectIdFieldArray()[i]);
			columnName = ReservedWordsUtil.convertWord(fieldMeta.getColumnName(), dbType);
			if (i > 0) {
				sql.append(",");
			}
			sql.append(columnName);
			sql.append("=");
			if (fupc.contains(columnName)) {
				sql.append("?");
			} else {
				//2020-6-13 修复postgresql bytea类型处理错误
				if (isPostgre && fieldMeta.getFieldType().equals("byte[]")) {
					sql.append(" cast(");
					sql.append(nullFunction);
					sql.append("(cast(? as varchar),").append("cast(").append(columnName).append(" as varchar))");
					sql.append(" as bytea)");
				} else {
					sql.append(nullFunction);
					sql.append("(?,").append(columnName).append(")");
				}
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
	 * @todo 加载获取单笔数据库记录
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
			EntityMeta entityMeta, Serializable entity, List<Class> cascadeTypes, Connection conn, final Integer dbType)
			throws Exception {
		Object[] pkValues = BeanUtil.reflectBeanToAry(entity, entityMeta.getIdArray(), null, null);
		// 检查主键值是否合法
		for (int i = 0; i < pkValues.length; i++) {
			if (StringUtil.isBlank(pkValues[i])) {
				throw new IllegalArgumentException(entityMeta.getSchemaTable()
						+ " load method must assign value for pk,null pk field is:" + entityMeta.getIdArray()[i]);
			}
		}
		SqlToyResult sqlToyResult = SqlConfigParseUtils.processSql(sql, entityMeta.getIdArray(), pkValues);

		QueryResult queryResult = findBySql(sqlToyContext, sqlToyConfig, sqlToyResult.getSql(),
				sqlToyResult.getParamsValue(), null, conn, dbType, 0, -1, -1);
		List rows = queryResult.getRows();
		Serializable result = null;
		if (rows != null && rows.size() > 0) {
			rows = BeanUtil.reflectListToBean(rows, ResultUtils.humpFieldNames(queryResult.getLabelNames()),
					entity.getClass());
			result = (Serializable) rows.get(0);
		}
		if (result == null)
			return null;

		// 存在主表对应子表
		if (null != cascadeTypes && !cascadeTypes.isEmpty() && !entityMeta.getOneToManys().isEmpty()) {
			List pkRefDetails;
			for (OneToManyModel oneToMany : entityMeta.getOneToManys()) {
				// 判定是否要加载
				if (cascadeTypes.contains(oneToMany.getMappedType())) {
					sqlToyResult = SqlConfigParseUtils.processSql(oneToMany.getLoadSubTableSql(),
							oneToMany.getMappedFields(), pkValues);
					SqlExecuteStat.showSql("cascade load subtable sql:" + sqlToyResult.getSql(),
							sqlToyResult.getParamsValue());
					pkRefDetails = SqlUtil.findByJdbcQuery(sqlToyResult.getSql(), sqlToyResult.getParamsValue(),
							oneToMany.getMappedType(), null, conn, dbType, false);
					if (null != pkRefDetails && !pkRefDetails.isEmpty()) {
						BeanUtil.setProperty(result, oneToMany.getProperty(), pkRefDetails);
					}
				}
			}
		}
		return result;
	}

	/**
	 * @todo 提供统一的loadAll处理机制
	 * @param sqlToyContext
	 * @param sql
	 * @param entities
	 * @param cascadeTypes
	 * @param conn
	 * @param dbType
	 * @return
	 * @throws Exception
	 */
	public static List<?> loadAll(final SqlToyContext sqlToyContext, String sql, List<?> entities,
			List<Class> cascadeTypes, Connection conn, final Integer dbType) throws Exception {
		if (entities == null || entities.isEmpty())
			return entities;
		Class entityClass = entities.get(0).getClass();
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entityClass);

		// 没有主键不能进行load相关的查询
		if (entityMeta.getIdArray() == null) {
			throw new IllegalArgumentException(
					"表:" + entityMeta.getSchemaTable() + " 没有主键,不符合load或loadAll规则,请检查表设计是否合理!");
		}
		// 主键值
		List pkValues = BeanUtil.reflectBeansToList(entities, entityMeta.getIdArray());
		int idSize = entityMeta.getIdArray().length;
		// 构造内部的listz(如果复合主键，形成{p1v1,p1v2,p1v3},{p2v1,p2v2,p2v3}) 格式，然后一次查询出结果
		List[] idValues = new List[idSize];
		for (int i = 0; i < idSize; i++) {
			idValues[i] = new ArrayList();
		}
		List rowList;
		// 检查主键值,主键值必须不为null
		Object value;
		for (int i = 0, n = pkValues.size(); i < n; i++) {
			rowList = (List) pkValues.get(i);
			for (int j = 0; j < idSize; j++) {
				value = rowList.get(j);
				// 验证主键值是否合法
				if (StringUtil.isBlank(value)) {
					throw new IllegalArgumentException(
							entityMeta.getSchemaTable() + " loadAll method must assign value for pk,row:" + i
									+ " pk field:" + entityMeta.getIdArray()[j]);
				}
				if (!idValues[j].contains(value)) {
					idValues[j].add(value);
				}
			}
		}
		SqlToyResult sqlToyResult = SqlConfigParseUtils.processSql(sql, entityMeta.getIdArray(), idValues);

		SqlExecuteStat.showSql(sqlToyResult.getSql(), sqlToyResult.getParamsValue());

		List<?> entitySet = SqlUtil.findByJdbcQuery(sqlToyResult.getSql(), sqlToyResult.getParamsValue(), entityClass,
				null, conn, dbType, false);
		// 存在主表对应子表
		if (null != cascadeTypes && !cascadeTypes.isEmpty() && !entityMeta.getOneToManys().isEmpty()) {
			StringBuilder subTableSql = new StringBuilder();
			List items;
			SqlToyResult subToyResult;
			EntityMeta mappedMeta;
			for (OneToManyModel oneToMany : entityMeta.getOneToManys()) {
				if (cascadeTypes.contains(oneToMany.getMappedType())) {
					mappedMeta = sqlToyContext.getEntityMeta(oneToMany.getMappedType());
					// 清空buffer
					subTableSql.delete(0, subTableSql.length());
					// 构造查询语句,update 2019-12-09 使用完整字段
					subTableSql.append("select ").append(mappedMeta.getAllColumnNames()).append(" from ")
							.append(oneToMany.getMappedTable()).append(" where ");
					for (int i = 0; i < idSize; i++) {
						if (i > 0) {
							subTableSql.append(" and ");
						}
						subTableSql.append(oneToMany.getMappedColumns()[i]);
						subTableSql.append(" in (:" + entityMeta.getIdArray()[i] + ") ");
					}
					subToyResult = SqlConfigParseUtils.processSql(subTableSql.toString(), entityMeta.getIdArray(),
							idValues);
					SqlExecuteStat.showSql(subToyResult.getSql(), subToyResult.getParamsValue());
					items = SqlUtil.findByJdbcQuery(subToyResult.getSql(), subToyResult.getParamsValue(),
							oneToMany.getMappedType(), null, conn, dbType, false);
					// 调用vo中mapping方法,将子表对象规整到主表对象的oneToMany集合中
					BeanUtil.invokeMethod(entities.get(0),
							"mapping" + StringUtil.firstToUpperCase(oneToMany.getProperty()),
							new Object[] { entitySet, items });
				}
			}
		}
		return entitySet;
	}

	/**
	 * @todo 保存对象
	 * @param sqlToyContext
	 * @param entityMeta
	 * @param pkStrategy
	 * @param isAssignPK
	 * @param returnPkType
	 * @param insertSql
	 * @param entity
	 * @param generateSqlHandler
	 * @param generateSavePKStrategy
	 * @param conn
	 * @return
	 * @throws Exception
	 */
	public static Object save(SqlToyContext sqlToyContext, final EntityMeta entityMeta, final PKStrategy pkStrategy,
			final boolean isAssignPK, final ReturnPkType returnPkType, final String insertSql, Serializable entity,
			final GenerateSqlHandler generateSqlHandler, final GenerateSavePKStrategy generateSavePKStrategy,
			final Connection conn, final Integer dbType) throws Exception {
		final boolean isIdentity = (pkStrategy != null && pkStrategy.equals(PKStrategy.IDENTITY));
		final boolean isSequence = (pkStrategy != null && pkStrategy.equals(PKStrategy.SEQUENCE));
		String[] reflectColumns;
		if ((isIdentity && !isAssignPK) || (isSequence && !isAssignPK)) {
			reflectColumns = entityMeta.getRejectIdFieldArray();
		} else {
			reflectColumns = entityMeta.getFieldsArray();
		}
		// 构造全新的新增记录参数赋值反射(覆盖之前的)
		ReflectPropertyHandler handler = getAddReflectHandler(sqlToyContext, null);
		Object[] fullParamValues = BeanUtil.reflectBeanToAry(entity, reflectColumns, null, handler);
		boolean needUpdatePk = false;

		int pkIndex = entityMeta.getIdIndex();
		// 是否存在业务ID
		boolean hasBizId = (entityMeta.getBusinessIdGenerator() == null) ? false : true;
		int bizIdColIndex = hasBizId ? entityMeta.getFieldIndex(entityMeta.getBusinessIdField()) : 0;
		// 标识符
		String signature = entityMeta.getBizIdSignature();
		Integer[] relatedColumn = entityMeta.getBizIdRelatedColIndex();
		String[] relatedColumnNames = entityMeta.getBizIdRelatedColumns();
		int relatedColumnSize = (relatedColumn == null) ? 0 : relatedColumn.length;
		// 主键采用assign方式赋予，则调用generator产生id并赋予其值
		if (entityMeta.getIdStrategy() != null && null != entityMeta.getIdGenerator()) {
			int bizIdLength = entityMeta.getBizIdLength();
			int idLength = entityMeta.getIdLength();
			Object[] relatedColValue = null;
			String businessIdType = hasBizId ? entityMeta.getColumnJavaType(entityMeta.getBusinessIdField()) : "";
			if (relatedColumn != null) {
				relatedColValue = new Object[relatedColumnSize];
				for (int meter = 0; meter < relatedColumnSize; meter++) {
					relatedColValue[meter] = fullParamValues[relatedColumn[meter]];
					if (StringUtil.isBlank(relatedColValue[meter])) {
						throw new IllegalArgumentException("对象:" + entityMeta.getEntityClass().getName()
								+ " 生成业务主键依赖的关联字段:" + relatedColumnNames[meter] + " 值为null!");
					}
				}
			}
			if (StringUtil.isBlank(fullParamValues[pkIndex])) {
				// id通过generator机制产生，设置generator产生的值
				fullParamValues[pkIndex] = entityMeta.getIdGenerator().getId(entityMeta.getTableName(), signature,
						entityMeta.getBizIdRelatedColumns(), relatedColValue, null, entityMeta.getIdType(), idLength,
						entityMeta.getBizIdSequenceSize());
				needUpdatePk = true;
			}
			if (hasBizId && StringUtil.isBlank(fullParamValues[bizIdColIndex])) {
				fullParamValues[bizIdColIndex] = entityMeta.getBusinessIdGenerator().getId(entityMeta.getTableName(),
						signature, entityMeta.getBizIdRelatedColumns(), relatedColValue, null, businessIdType,
						bizIdLength, entityMeta.getBizIdSequenceSize());
				// 回写业务主键值
				BeanUtil.setProperty(entity, entityMeta.getBusinessIdField(), fullParamValues[bizIdColIndex]);
			}
		}
		SqlExecuteStat.showSql("save insertSql=" + insertSql, null);
		final Object[] paramValues = fullParamValues;
		final Integer[] paramsType = entityMeta.getFieldsTypeArray();
		PreparedStatement pst = null;
		Object result = SqlUtil.preparedStatementProcess(null, pst, null, new PreparedStatementResultHandler() {
			public void execute(Object obj, PreparedStatement pst, ResultSet rs) throws SQLException, IOException {
				if (isIdentity || isSequence) {
					if (returnPkType.equals(ReturnPkType.GENERATED_KEYS)) {
						pst = conn.prepareStatement(insertSql, PreparedStatement.RETURN_GENERATED_KEYS);
					} else if (returnPkType.equals(ReturnPkType.PREPARD_ID)) {
						pst = conn.prepareStatement(insertSql,
								new String[] { entityMeta.getColumnName(entityMeta.getIdArray()[0]) });
					} else {
						pst = conn.prepareStatement(insertSql);
					}
				} else {
					pst = conn.prepareStatement(insertSql);
				}
				SqlUtil.setParamsValue(conn, dbType, pst, paramValues, paramsType, 0);
				ResultSet keyResult = null;
				if ((isIdentity || isSequence) && returnPkType.equals(ReturnPkType.RESULT_GET)) {
					keyResult = pst.executeQuery();
				} else {
					pst.execute();
				}
				if (isIdentity || isSequence) {
					if (!returnPkType.equals(ReturnPkType.RESULT_GET)) {
						keyResult = pst.getGeneratedKeys();
					}
					if (keyResult != null) {
						List result = new ArrayList();
						while (keyResult.next()) {
							result.add(keyResult.getObject(1));
						}
						if (result.size() == 1) {
							this.setResult(result.get(0));
						} else {
							this.setResult(result.toArray());
						}
					}
				}
			}
		});
		// 无主键直接返回null
		if (entityMeta.getIdArray() == null) {
			return null;
		}
		if (result == null) {
			result = fullParamValues[pkIndex];
		}
		// 回置到entity 主键值
		if (needUpdatePk || isIdentity || isSequence) {
			BeanUtil.setProperty(entity, entityMeta.getIdArray()[0], result);
		}
		// 判定是否有级联子表数据保存
		if (!entityMeta.getOneToManys().isEmpty()) {
			List subTableData;
			final Object[] idValues = BeanUtil.reflectBeanToAry(entity, entityMeta.getIdArray(), null, null);
			EntityMeta subTableEntityMeta;
			String insertSubTableSql;
			SavePKStrategy savePkStrategy;
			for (OneToManyModel oneToMany : entityMeta.getOneToManys()) {
				final String[] mappedFields = oneToMany.getMappedFields();
				subTableEntityMeta = sqlToyContext.getEntityMeta(oneToMany.getMappedType());
				logger.info("执行save操作的级联子表{}批量保存!", subTableEntityMeta.getTableName());
				subTableData = (List) BeanUtil.getProperty(entity, oneToMany.getProperty());
				if (subTableData != null && !subTableData.isEmpty()) {
					insertSubTableSql = generateSqlHandler.generateSql(subTableEntityMeta, null);
					savePkStrategy = generateSavePKStrategy.generate(subTableEntityMeta);
					saveAll(sqlToyContext, subTableEntityMeta, savePkStrategy.getPkStrategy(),
							savePkStrategy.isAssginValue(), insertSubTableSql, subTableData,
							sqlToyContext.getBatchSize(), new ReflectPropertyHandler() {
								public void process() {
									for (int i = 0; i < mappedFields.length; i++) {
										this.setValue(mappedFields[i], idValues[i]);
									}
								}
							}, conn, dbType, null);
				}
			}
		}
		return result;
	}

	/**
	 * @todo 保存批量对象数据
	 * @param sqlToyContext
	 * @param entityMeta
	 * @param pkStrategy
	 * @param isAssignPK
	 * @param insertSql
	 * @param entities
	 * @param batchSize
	 * @param reflectPropertyHandler
	 * @param conn
	 * @param autoCommit
	 * @throws Exception
	 */
	public static Long saveAll(SqlToyContext sqlToyContext, EntityMeta entityMeta, PKStrategy pkStrategy,
			boolean isAssignPK, String insertSql, List<?> entities, final int batchSize,
			ReflectPropertyHandler reflectPropertyHandler, Connection conn, final Integer dbType,
			final Boolean autoCommit) throws Exception {
		boolean isIdentity = pkStrategy != null && pkStrategy.equals(PKStrategy.IDENTITY);
		boolean isSequence = pkStrategy != null && pkStrategy.equals(PKStrategy.SEQUENCE);
		String[] reflectColumns;
		if ((isIdentity && !isAssignPK) || (isSequence && !isAssignPK)) {
			reflectColumns = entityMeta.getRejectIdFieldArray();
		} else {
			reflectColumns = entityMeta.getFieldsArray();
		}
		// 构造全新的新增记录参数赋值反射(覆盖之前的)
		ReflectPropertyHandler handler = getAddReflectHandler(sqlToyContext, reflectPropertyHandler);
		List paramValues = BeanUtil.reflectBeansToInnerAry(entities, reflectColumns, null, handler, false, 0);
		int pkIndex = entityMeta.getIdIndex();
		// 是否存在业务ID
		boolean hasBizId = (entityMeta.getBusinessIdGenerator() == null) ? false : true;
		int bizIdColIndex = hasBizId ? entityMeta.getFieldIndex(entityMeta.getBusinessIdField()) : 0;
		// 标识符
		String signature = entityMeta.getBizIdSignature();
		Integer[] relatedColumn = entityMeta.getBizIdRelatedColIndex();
		String[] relatedColumnNames = entityMeta.getBizIdRelatedColumns();
		int relatedColumnSize = (relatedColumn == null) ? 0 : relatedColumn.length;
		// 无主键值以及多主键以及assign或通过generator方式产生主键策略
		if (pkStrategy != null && null != entityMeta.getIdGenerator()) {
			int bizIdLength = entityMeta.getBizIdLength();
			int idLength = entityMeta.getIdLength();
			Object[] rowData;
			boolean isAssigned = true;
			String idJdbcType = entityMeta.getIdType();
			Object[] relatedColValue = null;
			String businessIdType = hasBizId ? entityMeta.getColumnJavaType(entityMeta.getBusinessIdField()) : "";
			List<Object[]> idSet = new ArrayList<Object[]>();
			for (int i = 0, s = paramValues.size(); i < s; i++) {
				rowData = (Object[]) paramValues.get(i);
				// 判断主键策略关联的字段是否有值,合法性验证
				if (relatedColumn != null) {
					relatedColValue = new Object[relatedColumnSize];
					for (int meter = 0; meter < relatedColumnSize; meter++) {
						relatedColValue[meter] = rowData[relatedColumn[meter]];
						if (StringUtil.isBlank(relatedColValue[meter])) {
							throw new IllegalArgumentException("对象:" + entityMeta.getEntityClass().getName()
									+ " 生成业务主键依赖的关联字段:" + relatedColumnNames[meter] + " 值为null!");
						}
					}
				}
				// 主键值为null,调用主键生成策略并赋值
				if (StringUtil.isBlank(rowData[pkIndex])) {
					isAssigned = false;
					rowData[pkIndex] = entityMeta.getIdGenerator().getId(entityMeta.getTableName(), signature,
							entityMeta.getBizIdRelatedColumns(), relatedColValue, null, idJdbcType, idLength,
							entityMeta.getBizIdSequenceSize());
				}
				if (hasBizId && StringUtil.isBlank(rowData[bizIdColIndex])) {
					rowData[bizIdColIndex] = entityMeta.getBusinessIdGenerator().getId(entityMeta.getTableName(),
							signature, entityMeta.getBizIdRelatedColumns(), relatedColValue, null, businessIdType,
							bizIdLength, entityMeta.getBizIdSequenceSize());
					// 回写业务主键值
					BeanUtil.setProperty(entities.get(i), entityMeta.getBusinessIdField(), rowData[bizIdColIndex]);
				}
				idSet.add(new Object[] { rowData[pkIndex] });
			}
			// 批量反向设置最终得到的主键值
			if (!isAssigned) {
				BeanUtil.mappingSetProperties(entities, entityMeta.getIdArray(), idSet, new int[] { 0 }, true);
			}
		}

		SqlExecuteStat.showSql("saveAll insertSql=" + insertSql, null);
		return SqlUtilsExt.batchUpdateByJdbc(insertSql, paramValues, batchSize, entityMeta.getFieldsTypeArray(),
				autoCommit, conn, dbType);
	}

	/**
	 * @todo 单笔记录修改
	 * @param sqlToyContext
	 * @param entity
	 * @param entityMeta
	 * @param nullFunction
	 * @param forceUpdateFields
	 * @param conn
	 * @param tableName
	 * @throws Exception
	 */
	public static Long update(SqlToyContext sqlToyContext, Serializable entity, EntityMeta entityMeta,
			String nullFunction, String[] forceUpdateFields, Connection conn, final Integer dbType, String tableName)
			throws Exception {
		String realTable = entityMeta.getSchemaTable(tableName);
		// 全部是主键则无需update，无主键则同样不符合修改规则
		if (entityMeta.getRejectIdFieldArray() == null || entityMeta.getIdArray() == null) {
			throw new IllegalArgumentException("表:" + realTable + " 字段全部是主键或无主键,不符合update规则,请检查表设计是否合理!");
		}

		// 构造全新的修改记录参数赋值反射(覆盖之前的)
		ReflectPropertyHandler handler = getUpdateReflectHandler(sqlToyContext, null);
		Object[] fieldsValues = BeanUtil.reflectBeanToAry(entity, entityMeta.getFieldsArray(), null, handler);
		// 判断主键是否为空
		int pkIndex = entityMeta.getIdIndex();
		for (int i = pkIndex; i < pkIndex + entityMeta.getIdArray().length; i++) {
			if (StringUtil.isBlank(fieldsValues[i])) {
				throw new IllegalArgumentException("通过对象对表:" + realTable + " 进行update操作,主键字段必须要赋值!");
			}
		}
		// 构建update语句
		String updateSql = generateUpdateSql(dbType, entityMeta, nullFunction, forceUpdateFields, tableName);
		if (updateSql == null) {
			throw new IllegalArgumentException("update sql is null,引起问题的原因是没有设置需要修改的字段!");
		}

		SqlExecuteStat.showSql("update execute sql=" + updateSql, null);
		return executeSql(sqlToyContext, updateSql, fieldsValues, entityMeta.getFieldsTypeArray(), conn, dbType, null);
	}

	/**
	 * @todo 单个对象修改，包含接连修改
	 * @param sqlToyContext
	 * @param entity
	 * @param nullFunction
	 * @param forceUpdateFields
	 * @param cascade
	 * @param generateSqlHandler
	 * @param forceCascadeClasses
	 * @param subTableForceUpdateProps
	 * @param conn
	 * @param tableName
	 * @throws Exception
	 */
	public static Long update(SqlToyContext sqlToyContext, Serializable entity, String nullFunction,
			String[] forceUpdateFields, final boolean cascade, final GenerateSqlHandler generateSqlHandler,
			final Class[] forceCascadeClasses, final HashMap<Class, String[]> subTableForceUpdateProps, Connection conn,
			final Integer dbType, String tableName) throws Exception {
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entity.getClass());
		Long updateCnt = update(sqlToyContext, entity, entityMeta, nullFunction, forceUpdateFields, conn, dbType,
				tableName);
		// 级联保存
		if (cascade && !entityMeta.getOneToManys().isEmpty()) {
			HashMap<Type, String> typeMap = new HashMap<Type, String>();
			// 即使子对象数据是null,也强制进行级联修改(null表示删除子表数据)
			if (forceCascadeClasses != null) {
				for (Type type : forceCascadeClasses) {
					typeMap.put(type, "");
				}
			}
			// 级联子表数据
			List subTableData;
			final Object[] IdValues = BeanUtil.reflectBeanToAry(entity, entityMeta.getIdArray(), null, null);
			String[] forceUpdateProps = null;
			EntityMeta subTableEntityMeta;
			// 对子表进行级联处理
			for (OneToManyModel oneToMany : entityMeta.getOneToManys()) {
				subTableEntityMeta = sqlToyContext.getEntityMeta(oneToMany.getMappedType());
				forceUpdateProps = (subTableForceUpdateProps == null) ? null
						: subTableForceUpdateProps.get(oneToMany.getMappedType());
				subTableData = (List) BeanUtil.invokeMethod(entity,
						"get".concat(StringUtil.firstToUpperCase(oneToMany.getProperty())), null);
				final String[] mappedFields = oneToMany.getMappedFields();
				/**
				 * 针对子表存量数据,调用级联修改的语句，分delete 和update两种操作 1、删除存量数据;2、设置存量数据状态为停用
				 */
				if (oneToMany.getCascadeUpdateSql() != null && ((subTableData != null && !subTableData.isEmpty())
						|| typeMap.containsKey(oneToMany.getMappedType()))) {
					// 根据quickvo配置文件针对cascade中update-cascade配置组织具体操作sql
					SqlToyResult sqlToyResult = SqlConfigParseUtils.processSql(oneToMany.getCascadeUpdateSql(),
							mappedFields, IdValues);
					executeSql(sqlToyContext, sqlToyResult.getSql(), sqlToyResult.getParamsValue(), null, conn, dbType,
							null);
				}
				// 子表数据不为空,采取saveOrUpdateAll操作
				if (subTableData != null && !subTableData.isEmpty()) {
					saveOrUpdateAll(sqlToyContext, subTableData, sqlToyContext.getBatchSize(), subTableEntityMeta,
							forceUpdateProps, generateSqlHandler,
							// 设置关联外键字段的属性值(来自主表的主键)
							new ReflectPropertyHandler() {
								public void process() {
									for (int i = 0; i < mappedFields.length; i++) {
										this.setValue(mappedFields[i], IdValues[i]);
									}
								}
							}, conn, dbType, null);
				}
			}
		}
		return updateCnt;
	}

	/**
	 * @todo 批量对象修改
	 * @param sqlToyContext
	 * @param entities
	 * @param batchSize
	 * @param forceUpdateFields
	 * @param reflectPropertyHandler
	 * @param nullFunction
	 * @param conn
	 * @param dbType
	 * @param autoCommit
	 * @param tableName
	 * @param skipNull
	 * @return
	 * @throws Exception
	 */
	public static Long updateAll(SqlToyContext sqlToyContext, List<?> entities, final int batchSize,
			final String[] forceUpdateFields, ReflectPropertyHandler reflectPropertyHandler, String nullFunction,
			Connection conn, final Integer dbType, final Boolean autoCommit, String tableName, boolean skipNull)
			throws Exception {
		if (entities == null || entities.isEmpty())
			return 0L;
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entities.get(0).getClass());
		String realTable = entityMeta.getSchemaTable(tableName);
		// 全部是主键则无需update，无主键则同样不符合修改规则
		if (entityMeta.getRejectIdFieldArray() == null || entityMeta.getIdArray() == null) {
			throw new IllegalArgumentException("表:" + realTable + " 字段全部是主键或无主键,不符合update/updateAll规则,请检查表设计是否合理!");
		}
		// 构造全新的修改记录参数赋值反射(覆盖之前的)
		ReflectPropertyHandler handler = getUpdateReflectHandler(sqlToyContext, reflectPropertyHandler);
		List<Object[]> paramsValues = BeanUtil.reflectBeansToInnerAry(entities, entityMeta.getFieldsArray(), null,
				handler, false, 0);
		// 判断主键是否为空
		int pkIndex = entityMeta.getIdIndex();
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
						throw new IllegalArgumentException(
								"通过对象对表" + realTable + " 进行updateAll操作,主键字段必须要赋值!第:" + index + " 条记录主键为null!");
					}
				}
			}
			index++;
		}
		if (skipCount > 0) {
			if (logger.isDebugEnabled()) {
				logger.debug("共有{}行记录因为主键值为空跳过修改操作!", skipCount);
			} else {
				System.out.println("共有:" + skipCount + " 行记录因为主键值为空跳过修改操作!");
			}
		}

		// 构建update语句
		String updateSql = generateUpdateSql(dbType, entityMeta, nullFunction, forceUpdateFields, tableName);
		if (updateSql == null) {
			throw new IllegalArgumentException("update sql is null,引起问题的原因是没有设置需要修改的字段!");
		}
		SqlExecuteStat.showSql("update execute sql=" + updateSql, null);
		return SqlUtilsExt.batchUpdateByJdbc(updateSql.toString(), paramsValues, batchSize,
				entityMeta.getFieldsTypeArray(), autoCommit, conn, dbType);
	}

	/**
	 * @todo 删除单个对象以及其级联表数据
	 * @param sqlToyContext
	 * @param entity
	 * @param conn
	 * @param dbType
	 * @param tableName
	 * @return
	 * @throws Exception
	 */
	public static Long delete(SqlToyContext sqlToyContext, Serializable entity, Connection conn, final Integer dbType,
			final String tableName) throws Exception {
		if (entity == null)
			return 0L;
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entity.getClass());
		String realTable = entityMeta.getSchemaTable(tableName);
		if (null == entityMeta.getIdArray()) {
			throw new IllegalArgumentException("delete 操作,表:" + realTable + " 没有主键,请检查表设计!");
		}
		Object[] idValues = BeanUtil.reflectBeanToAry(entity, entityMeta.getIdArray(), null, null);
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
		if (!entityMeta.getOneToManys().isEmpty()) {
			for (OneToManyModel oneToMany : entityMeta.getOneToManys()) {
				// 如果数据库本身通过on delete cascade机制，则sqltoy无需进行删除操作
				if (oneToMany.isDelete()) {
					if (sqlToyContext.isDebug()) {
						logger.debug("cascade delete sub table sql:{}", oneToMany.getDeleteSubTableSql());
					}
					executeSql(sqlToyContext, oneToMany.getDeleteSubTableSql(), idValues, parameterTypes, conn, dbType,
							null);
				}
			}
		}
		SqlExecuteStat.showSql("delete sql=" + entityMeta.getDeleteByIdsSql(tableName), null);
		return executeSql(sqlToyContext, entityMeta.getDeleteByIdsSql(tableName), idValues, parameterTypes, conn,
				dbType, null);
	}

	/**
	 * @todo 批量删除对象并级联删除掉子表数据
	 * @param sqlToyContext
	 * @param entities
	 * @param batchSize
	 * @param conn
	 * @param dbType
	 * @param autoCommit
	 * @param tableName
	 * @return
	 * @throws Exception
	 */
	public static Long deleteAll(SqlToyContext sqlToyContext, List<?> entities, final int batchSize, Connection conn,
			final Integer dbType, final Boolean autoCommit, final String tableName) throws Exception {
		if (null == entities || entities.isEmpty())
			return 0L;
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entities.get(0).getClass());
		String realTable = entityMeta.getSchemaTable(tableName);
		if (null == entityMeta.getIdArray()) {
			throw new IllegalArgumentException("delete/deleteAll 操作,表:" + realTable + " 没有主键,请检查表设计!");
		}
		List<Object[]> idValues = BeanUtil.reflectBeansToInnerAry(entities, entityMeta.getIdArray(), null, null, false,
				0);
		// 判断主键值是否存在空
		Object[] idsValue;
		for (int i = 0, n = idValues.size(); i < n; i++) {
			idsValue = idValues.get(i);
			for (Object obj : idsValue) {
				if (StringUtil.isBlank(obj)) {
					throw new IllegalArgumentException("第[" + i + "]行数据主键值存在空,批量删除以主键为依据，表:" + realTable + " 主键不能为空!");
				}
			}
		}
		int idsLength = entityMeta.getIdArray().length;
		Integer[] parameterTypes = new Integer[idsLength];
		for (int i = 0, n = idsLength; i < n; i++) {
			parameterTypes[i] = entityMeta.getColumnJdbcType(entityMeta.getIdArray()[i]);
		}
		// 级联批量删除子表数据
		if (!entityMeta.getOneToManys().isEmpty()) {
			for (OneToManyModel oneToMany : entityMeta.getOneToManys()) {
				// 如果数据库本身通过on delete cascade机制，则sqltoy无需进行删除操作
				if (oneToMany.isDelete()) {
					if (sqlToyContext.isDebug()) {
						logger.debug("cascade batch delete sub table sql:{}", oneToMany.getDeleteSubTableSql());
					}
					SqlUtilsExt.batchUpdateByJdbc(oneToMany.getDeleteSubTableSql(), idValues,
							sqlToyContext.getBatchSize(), parameterTypes, null, conn, dbType);
				}
			}
		}

		SqlExecuteStat.showSql("delete all sql=" + entityMeta.getDeleteByIdsSql(tableName), null);
		return SqlUtilsExt.batchUpdateByJdbc(entityMeta.getDeleteByIdsSql(tableName), idValues, batchSize,
				parameterTypes, autoCommit, conn, dbType);
	}

	/**
	 * @todo 进行唯一性查询判定
	 * @param sqlToyContext
	 * @param entity
	 * @param paramsNamed
	 * @param conn
	 * @param dbType
	 * @param tableName
	 * @return
	 */
	public static boolean isUnique(SqlToyContext sqlToyContext, Serializable entity, final String[] paramsNamed,
			Connection conn, final Integer dbType, String tableName) {
		try {
			EntityMeta entityMeta = sqlToyContext.getEntityMeta(entity.getClass());
			String[] realParamNamed;
			Object[] paramValues;
			int rejectIdFieldsSize = (entityMeta.getRejectIdFieldArray() == null) ? 0
					: entityMeta.getRejectIdFieldArray().length;
			// 如果没有特别指定属性，则通过数据是否为null来判断具体的字段
			if (paramsNamed == null || paramsNamed.length == 0) {
				String[] fieldsArray = entityMeta.getFieldsArray();
				Object[] fieldValues = BeanUtil.reflectBeanToAry(entity, fieldsArray, null, null);
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
				realParamNamed = paramNames.toArray(new String[paramNames.size()]);
			} else {
				realParamNamed = paramsNamed;
				paramValues = BeanUtil.reflectBeanToAry(entity, paramsNamed, null, null);
			}
			// 构造查询语句
			StringBuilder queryStr = new StringBuilder("select 1");
			// 如果存在主键，则查询主键字段
			if (null != entityMeta.getIdArray()) {
				for (String idFieldName : entityMeta.getIdArray()) {
					queryStr.append(",");
					queryStr.append(ReservedWordsUtil.convertWord(entityMeta.getColumnName(idFieldName), dbType));
				}
			}
			queryStr.append(" from ");
			queryStr.append(entityMeta.getSchemaTable(tableName));
			queryStr.append(" where  ");
			for (int i = 0; i < realParamNamed.length; i++) {
				if (i > 0) {
					queryStr.append(" and ");
				}
				queryStr.append(ReservedWordsUtil.convertWord(entityMeta.getColumnName(realParamNamed[i]), dbType))
						.append("=? ");
			}

			// 防止数据量过大，先用count方式查询提升效率
			long recordCnt = getCountBySql(sqlToyContext, null, queryStr.toString(), paramValues, true, conn, dbType);
			if (recordCnt == 0) {
				return true;
			}
			if (recordCnt > 1) {
				return false;
			}
			SqlExecuteStat.showSql("isUnique sql=" + queryStr.toString(), paramValues);
			List result = SqlUtil.findByJdbcQuery(queryStr.toString(), paramValues, null, null, conn, dbType, false);
			if (result.size() == 0) {
				return true;
			}
			if (result.size() > 1) {
				return false;
			}
			// 表没有主键,单条记录算重复
			if (null == entityMeta.getIdArray())
				return false;
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
			if (allPK)
				return false;
			// 判断是否是本身
			Object[] idValues = BeanUtil.reflectBeanToAry(entity, entityMeta.getIdArray(), null, null);
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
			logger.error("执行唯一性查询失败:{}", e.getMessage());
			e.printStackTrace();
		}
		return false;
	}

	/**
	 * @todo 判断是否复杂分页查询(union,多表关联、存在top 、distinct等)
	 * @param queryStr
	 * @return
	 */
	public static boolean isComplexPageQuery(String queryStr) {
		// 清除不必要的字符并转小写
		String tmpQuery = StringUtil.clearMistyChars(queryStr.toLowerCase(), " ");
		boolean isComplexQuery = SqlUtil.hasUnion(tmpQuery, false);
		// from 和 where之间有","表示多表查询
		if (!isComplexQuery) {
			int fromIndex = StringUtil.getSymMarkMatchIndex(SELECT_REGEX, FROM_REGEX, tmpQuery, 0);
			int fromWhereIndex = StringUtil.getSymMarkMatchIndex(FROM_REGEX, WHERE_REGEX, tmpQuery, fromIndex - 1);
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
	 * @todo 判断是否有order by 和union 逻辑语句
	 * @param sql
	 * @return
	 */
	public static boolean hasOrderByOrUnion(String sql) {
		String unDisturbSql = clearDisturbSql(sql);
		if (StringUtil.matches(unDisturbSql, UNION_PATTERN) || StringUtil.matches(unDisturbSql, ORDER_BY_PATTERN))
			return true;
		return false;
	}

	/**
	 * @todo 去除掉sql中的所有对称的括号中的内容，排除干扰
	 * @param sql
	 * @return
	 */
	public static String clearDisturbSql(String sql) {
		StringBuilder lastSql = new StringBuilder(sql);
		// 找到第一个select 所对称的from位置，排除掉子查询中的内容
		int fromIndex = StringUtil.getSymMarkMatchIndex(SELECT_REGEX, FROM_REGEX, sql.toLowerCase(), 0);
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
	 * @todo 去除掉sql中的所有对称的select 和 from 中的内容，排除干扰
	 * @param sql
	 * @return
	 */
	private static String clearSymSelectFromSql(String sql) {
		// 先转化为小写
		String realSql = sql.toLowerCase();
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

	public static Long executeSql(final SqlToyContext sqlToyContext, final String executeSql, final Object[] params,
			final Integer[] paramsType, final Connection conn, final Integer dbType, final Boolean autoCommit)
			throws Exception {
		if (sqlToyContext.isDebug()) {
			out.println("=================executeSql执行的语句====================");
			out.println(" execute sql:" + executeSql);
			DebugUtil.printAry(params, ";", false);
			out.println("======================================================");
		}
		return SqlUtil.executeSql(executeSql, params, paramsType, conn, dbType, autoCommit);
	}

	/**
	 * @todo <b>通用的存储过程调用，inParam需放在outParam前面</b>
	 * @param sqlToyConfig
	 * @param sqlToyContext
	 * @param storeSql
	 * @param inParamValues
	 * @param outParamTypes
	 * @param conn
	 * @param dbType
	 * @return
	 * @throws Exception
	 */
	public static StoreResult executeStore(final SqlToyConfig sqlToyConfig, final SqlToyContext sqlToyContext,
			final String storeSql, final Object[] inParamValues, final Integer[] outParamTypes, final Connection conn,
			final Integer dbType) throws Exception {
		CallableStatement callStat = null;
		ResultSet rs = null;
		return (StoreResult) SqlUtil.callableStatementProcess(null, callStat, rs, new CallableStatementResultHandler() {
			public void execute(Object obj, CallableStatement callStat, ResultSet rs) throws Exception {
				callStat = conn.prepareCall(storeSql);
				boolean isFirstResult = StringUtil.matches(storeSql, STORE_PATTERN);
				int addIndex = isFirstResult ? 1 : 0;
				SqlUtil.setParamsValue(conn, dbType, callStat, inParamValues, null, addIndex);
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
				callStat.execute();
				rs = callStat.getResultSet();
				// 执行查询 解决存储过程返回多个结果集问题，取最后一个结果集
				while (callStat.getMoreResults()) {
					rs = callStat.getResultSet();
				}
				StoreResult storeResult = new StoreResult();
				if (rs != null) {
					QueryResult tempResult = ResultUtils.processResultSet(sqlToyContext, sqlToyConfig, conn, rs, null,
							null, 0);
					storeResult.setLabelNames(tempResult.getLabelNames());
					storeResult.setLabelTypes(tempResult.getLabelTypes());
					storeResult.setRows(tempResult.getRows());
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
				this.setResult(storeResult);
			}
		});
	}

	/**
	 * @todo 构造新增记录参数反射赋值处理器
	 * @param sqlToyContext
	 * @param preHandler
	 * @return
	 */
	public static ReflectPropertyHandler getAddReflectHandler(SqlToyContext sqlToyContext,
			final ReflectPropertyHandler preHandler) {
		if (sqlToyContext.getUnifyFieldsHandler() == null)
			return preHandler;
		final Map<String, Object> keyValues = sqlToyContext.getUnifyFieldsHandler().createUnifyFields();
		if (keyValues == null || keyValues.isEmpty())
			return preHandler;
		// 强制修改字段赋值
		IgnoreCaseSet tmpSet = sqlToyContext.getUnifyFieldsHandler().forceUpdateFields();
		final IgnoreCaseSet forceUpdateFields = (tmpSet == null) ? new IgnoreCaseSet() : tmpSet;
		ReflectPropertyHandler handler = new ReflectPropertyHandler() {
			@Override
			public void process() {
				if (preHandler != null) {
					preHandler.setPropertyIndexMap(this.getPropertyIndexMap());
					preHandler.setRowIndex(this.getRowIndex());
					preHandler.setRowData(this.getRowData());
					preHandler.process();
				}
				for (Map.Entry<String, Object> entry : keyValues.entrySet()) {
					if (StringUtil.isBlank(this.getValue(entry.getKey()))
							|| forceUpdateFields.contains(entry.getKey())) {
						this.setValue(entry.getKey(), entry.getValue());
					}
				}
			}
		};
		return handler;
	}

	/**
	 * @todo 构造修改记录参数反射赋值处理器
	 * @param sqlToyContext
	 * @param preHandler
	 * @return
	 */
	public static ReflectPropertyHandler getUpdateReflectHandler(SqlToyContext sqlToyContext,
			final ReflectPropertyHandler preHandler) {
		if (sqlToyContext.getUnifyFieldsHandler() == null)
			return preHandler;
		final Map<String, Object> keyValues = sqlToyContext.getUnifyFieldsHandler().updateUnifyFields();
		if (keyValues == null || keyValues.isEmpty())
			return preHandler;
		// 强制修改字段赋值
		IgnoreCaseSet tmpSet = sqlToyContext.getUnifyFieldsHandler().forceUpdateFields();
		final IgnoreCaseSet forceUpdateFields = (tmpSet == null) ? new IgnoreCaseSet() : tmpSet;
		ReflectPropertyHandler handler = new ReflectPropertyHandler() {
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
					if (StringUtil.isBlank(this.getValue(entry.getKey()))
							|| forceUpdateFields.contains(entry.getKey())) {
						this.setValue(entry.getKey(), entry.getValue());
					}
				}
			}
		};
		return handler;
	}

	/**
	 * @todo 构造创建和修改记录时的反射
	 * @param sqlToyContext
	 * @param idFields
	 * @param preHandler
	 * @return
	 */
	public static ReflectPropertyHandler getSaveOrUpdateReflectHandler(SqlToyContext sqlToyContext,
			final String[] idFields, final ReflectPropertyHandler preHandler) {
		if (sqlToyContext.getUnifyFieldsHandler() == null)
			return preHandler;
		final Map<String, Object> addKeyValues = sqlToyContext.getUnifyFieldsHandler().createUnifyFields();
		final Map<String, Object> updateKeyValues = sqlToyContext.getUnifyFieldsHandler().updateUnifyFields();
		if ((addKeyValues == null || addKeyValues.isEmpty()) && (updateKeyValues == null || updateKeyValues.isEmpty()))
			return preHandler;
		// 强制修改字段赋值
		IgnoreCaseSet tmpSet = sqlToyContext.getUnifyFieldsHandler().forceUpdateFields();
		final IgnoreCaseSet forceUpdateFields = (tmpSet == null) ? new IgnoreCaseSet() : tmpSet;
		final int idLength = (idFields == null) ? 0 : idFields.length;
		ReflectPropertyHandler handler = new ReflectPropertyHandler() {
			@Override
			public void process() {
				if (preHandler != null) {
					preHandler.setPropertyIndexMap(this.getPropertyIndexMap());
					preHandler.setRowIndex(this.getRowIndex());
					preHandler.setRowData(this.getRowData());
					preHandler.process();
				}
				// 主键为空表示save操作
				if (idLength > 0 && this.getValue(idFields[0]) == null) {
					for (Map.Entry<String, Object> entry : addKeyValues.entrySet()) {
						if (StringUtil.isBlank(this.getValue(entry.getKey()))) {
							this.setValue(entry.getKey(), entry.getValue());
						}
					}
				}
				// 修改属性值
				for (Map.Entry<String, Object> entry : updateKeyValues.entrySet()) {
					if (StringUtil.isBlank(this.getValue(entry.getKey()))
							|| forceUpdateFields.contains(entry.getKey())) {
						this.setValue(entry.getKey(), entry.getValue());
					}
				}
			}
		};
		return handler;
	}

	/**
	 * @todo 提取sql中参数的个数
	 * @param queryStr
	 * @return
	 */
	private static int getParamsCount(String queryStr) {
		if (StringUtil.isBlank(queryStr))
			return 0;
		// 判断sql中参数模式，?或:named 模式，两种模式不可以混合使用
		if (queryStr.indexOf("?") == -1) {
			return StringUtil.matchCnt(queryStr, SqlToyConstants.SQL_NAMED_PATTERN);
		}
		return StringUtil.matchCnt(queryStr, "\\?");
	}

	public static void main(String[] args) {
		String sql = "select * from table where #[`status` in (?)]";
		String lastSql = DialectUtils.convertParamsToNamed(sql, 0).getSql();
		System.err.println(lastSql);
	}
}
