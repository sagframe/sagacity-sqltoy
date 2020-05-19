/**
 * 
 */
package org.sagacity.sqltoy.dialect;

import java.io.Serializable;
import java.sql.Connection;
import java.util.HashMap;
import java.util.List;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.callback.ReflectPropertyHandler;
import org.sagacity.sqltoy.callback.RowCallbackHandler;
import org.sagacity.sqltoy.callback.UpdateRowHandler;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.executor.QueryExecutor;
import org.sagacity.sqltoy.model.LockMode;
import org.sagacity.sqltoy.model.QueryResult;
import org.sagacity.sqltoy.model.StoreResult;

/**
 * @project sqltoy-orm
 * @description SqlToy3.0版本开始采用针对不同数据库单独进行特定封装处理，改变原先在一个方法中
 *              判断数据库并进行sql封装的模式，使得整个结构更加清晰
 * @author renfei.chen <a href="mailto:zhongxuchen@hotmail.com">联系作者</a>
 * @version Revision:v1.0,Date:2013-8-29
 * @Modification Date:2017-12-8 {修改接口定义:1、增加为开发者提供自行控制autoCommit机制; 2、增加分库分表的支持}
 * @Modification Date:2019-09-15 {统一扩展dbType和dialect传递到下层}
 */
@SuppressWarnings({ "rawtypes" })
public interface Dialect {
	/**
	 * @todo 获取随机记录
	 * @param sqlToyContext
	 * @param sqlToyConfig
	 * @param queryExecutor
	 * @param totalCount
	 * @param randomCount
	 * @param conn
	 * @param dbType
	 * @param dialect
	 * @return
	 * @throws Exception
	 */
	public QueryResult getRandomResult(final SqlToyContext sqlToyContext, final SqlToyConfig sqlToyConfig,
			final QueryExecutor queryExecutor, final Long totalCount, final Long randomCount, final Connection conn,
			final Integer dbType, final String dialect) throws Exception;

	/**
	 * @todo 分页查询
	 * @param sqlToyContext
	 * @param sqlToyConfig
	 * @param queryExecutor
	 * @param pageNo
	 * @param pageSize
	 * @param conn
	 * @param dbType
	 * @param dialect
	 * @return
	 * @throws Exception
	 */
	public QueryResult findPageBySql(final SqlToyContext sqlToyContext, final SqlToyConfig sqlToyConfig,
			final QueryExecutor queryExecutor, final Long pageNo, final Integer pageSize, final Connection conn,
			final Integer dbType, final String dialect) throws Exception;

	/**
	 * @todo 取top记录数
	 * @param sqlToyContext
	 * @param sqlToyConfig
	 * @param queryExecutor
	 * @param topSize
	 * @param conn
	 * @param dbType
	 * @param dialect
	 * @return
	 * @throws Exception
	 */
	public QueryResult findTopBySql(final SqlToyContext sqlToyContext, final SqlToyConfig sqlToyConfig,
			final QueryExecutor queryExecutor, final Integer topSize, final Connection conn, final Integer dbType,
			final String dialect) throws Exception;

	/**
	 * @todo 普通sql查询
	 * @param sqlToyContext
	 * @param sqlToyConfig
	 * @param sql
	 * @param paramsValue
	 * @param rowCallbackHandler
	 * @param conn
	 * @param dbType
	 * @param dialect
	 * @param fetchSize
	 * @param maxRows
	 * @return
	 * @throws Exception
	 */
	public QueryResult findBySql(final SqlToyContext sqlToyContext, final SqlToyConfig sqlToyConfig, final String sql,
			final Object[] paramsValue, final RowCallbackHandler rowCallbackHandler, final Connection conn,
			final LockMode lockMode, final Integer dbType, final String dialect, final int fetchSize, final int maxRows)
			throws Exception;

	/**
	 * @todo 取记录数量
	 * @param sqlToyContext
	 * @param sqlToyConfig
	 * @param sql
	 * @param paramsValue
	 * @param isLastSql
	 * @param conn
	 * @param dbType
	 * @param dialect
	 * @return
	 * @throws Exception
	 */
	public Long getCountBySql(final SqlToyContext sqlToyContext, final SqlToyConfig sqlToyConfig, final String sql,
			final Object[] paramsValue, final boolean isLastSql, final Connection conn, final Integer dbType,
			final String dialect) throws Exception;

	/**
	 * @todo 获取单个对象
	 * @param sqlToyContext
	 * @param entity
	 * @param cascadeTypes
	 * @param lockMode
	 * @param conn
	 * @param dbType
	 * @param dialect
	 * @param tableName
	 * @return
	 * @throws Exception
	 */
	public Serializable load(final SqlToyContext sqlToyContext, final Serializable entity,
			final List<Class> cascadeTypes, final LockMode lockMode, final Connection conn, final Integer dbType,
			final String dialect, final String tableName) throws Exception;

	/**
	 * @todo 批量级联查询
	 * @param sqlToyContext
	 * @param entities
	 * @param cascadeTypes
	 * @param lockMode
	 * @param conn
	 * @param dbType
	 * @param dialect
	 * @param tableName
	 * @return
	 * @throws Exception
	 */
	public List<?> loadAll(final SqlToyContext sqlToyContext, List<?> entities, List<Class> cascadeTypes,
			LockMode lockMode, final Connection conn, final Integer dbType, final String dialect,
			final String tableName) throws Exception;

	/**
	 * @todo 保存单条记录
	 * @param sqlToyContext
	 * @param entity
	 * @param conn
	 * @param dbType
	 * @param dialect
	 * @param tableName
	 * @return
	 * @throws Exception
	 */
	public Object save(final SqlToyContext sqlToyContext, final Serializable entity, final Connection conn,
			final Integer dbType, final String dialect, final String tableName) throws Exception;

	/**
	 * @todo 批量保存对象
	 * @param sqlToyContext
	 * @param entities
	 * @param batchSize
	 * @param reflectPropertyHandler
	 * @param conn
	 * @param dbType
	 * @param dialect
	 * @param autoCommit
	 * @param tableName
	 * @return
	 * @throws Exception
	 */
	public Long saveAll(final SqlToyContext sqlToyContext, final List<?> entities, final int batchSize,
			final ReflectPropertyHandler reflectPropertyHandler, final Connection conn, final Integer dbType,
			final String dialect, final Boolean autoCommit, final String tableName) throws Exception;

	/**
	 * @todo 修改单个对象
	 * @param sqlToyContext
	 * @param entity
	 * @param forceUpdateFields
	 * @param cascade
	 * @param forceCascadeClass
	 * @param subTableForceUpdateProps
	 * @param conn
	 * @param dbType
	 * @param dialect
	 * @param tableName
	 * @return
	 * @throws Exception
	 */
	public Long update(final SqlToyContext sqlToyContext, final Serializable entity, final String[] forceUpdateFields,
			final boolean cascade, final Class[] forceCascadeClass,
			final HashMap<Class, String[]> subTableForceUpdateProps, final Connection conn, final Integer dbType,
			final String dialect, final String tableName) throws Exception;

	/**
	 * @todo 批量修改对象
	 * @param sqlToyContext
	 * @param entities
	 * @param batchSize
	 * @param forceUpdateFields
	 * @param reflectPropertyHandler
	 * @param conn
	 * @param dbType
	 * @param dialect
	 * @param autoCommit
	 * @param tableName
	 * @return
	 * @throws Exception
	 */
	public Long updateAll(final SqlToyContext sqlToyContext, final List<?> entities, final int batchSize,
			final String[] forceUpdateFields, final ReflectPropertyHandler reflectPropertyHandler,
			final Connection conn, final Integer dbType, final String dialect, final Boolean autoCommit,
			final String tableName) throws Exception;

	/**
	 * @todo 保存或修改单条记录
	 * @param sqlToyContext
	 * @param entity
	 * @param forceUpdateFields
	 * @param conn
	 * @param dbType
	 * @param dialect
	 * @param autoCommit
	 * @param tableName
	 * @return
	 * @throws Exception
	 */
	public Long saveOrUpdate(final SqlToyContext sqlToyContext, final Serializable entity,
			final String[] forceUpdateFields, final Connection conn, final Integer dbType, final String dialect,
			final Boolean autoCommit, final String tableName) throws Exception;

	/**
	 * @todo 批量保存或修改记录
	 * @param sqlToyContext
	 * @param entities
	 * @param batchSize
	 * @param reflectPropertyHandler
	 * @param forceUpdateFields
	 * @param conn
	 * @param dbType
	 * @param dialect
	 * @param autoCommit
	 * @param tableName
	 * @return
	 * @throws Exception
	 */
	public Long saveOrUpdateAll(final SqlToyContext sqlToyContext, final List<?> entities, final int batchSize,
			final ReflectPropertyHandler reflectPropertyHandler, final String[] forceUpdateFields,
			final Connection conn, final Integer dbType, final String dialect, final Boolean autoCommit,
			final String tableName) throws Exception;

	/**
	 * @todo 批量保存,主键冲突的则忽视
	 * @param sqlToyContext
	 * @param entities
	 * @param batchSize
	 * @param reflectPropertyHandler
	 * @param conn
	 * @param dbType
	 * @param dialect
	 * @param autoCommit
	 * @param tableName
	 * @return
	 * @throws Exception
	 */
	public Long saveAllIgnoreExist(final SqlToyContext sqlToyContext, final List<?> entities, final int batchSize,
			final ReflectPropertyHandler reflectPropertyHandler, final Connection conn, final Integer dbType,
			final String dialect, final Boolean autoCommit, final String tableName) throws Exception;

	/**
	 * @todo 删除单个对象
	 * @param sqlToyContext
	 * @param entity
	 * @param conn
	 * @param dbType
	 * @param dialect
	 * @param tableName
	 * @return
	 * @throws Exception
	 */
	public Long delete(final SqlToyContext sqlToyContext, final Serializable entity, final Connection conn,
			final Integer dbType, final String dialect, final String tableName) throws Exception;

	/**
	 * @todo 批量删除对象
	 * @param sqlToyContext
	 * @param entities
	 * @param batchSize
	 * @param conn
	 * @param dbType
	 * @param dialect
	 * @param autoCommit
	 * @param tableName
	 * @return
	 * @throws Exception
	 */
	public Long deleteAll(final SqlToyContext sqlToyContext, final List<?> entities, final int batchSize,
			final Connection conn, final Integer dbType, final String dialect, final Boolean autoCommit,
			final String tableName) throws Exception;

	/**
	 * @todo lock记录查询，并立即修改查询的结果反写到数据库
	 * @param sqlToyContext
	 * @param sqlToyConfig
	 * @param sql
	 * @param paramValues
	 * @param updateRowHandler
	 * @param conn
	 * @param dbType
	 * @param dialect
	 * @return
	 * @throws Exception
	 */
	public QueryResult updateFetch(final SqlToyContext sqlToyContext, final SqlToyConfig sqlToyConfig, final String sql,
			final Object[] paramValues, final UpdateRowHandler updateRowHandler, final Connection conn,
			final Integer dbType, final String dialect) throws Exception;

	@Deprecated
	public QueryResult updateFetchTop(final SqlToyContext sqlToyContext, final SqlToyConfig sqlToyConfig, String sql,
			Object[] paramsValue, final Integer topSize, final UpdateRowHandler updateRowHandler, final Connection conn,
			final Integer dbType, final String dialect) throws Exception;

	@Deprecated
	public QueryResult updateFetchRandom(final SqlToyContext sqlToyContext, final SqlToyConfig sqlToyConfig, String sql,
			Object[] paramsValue, final Integer random, final UpdateRowHandler updateRowHandler, final Connection conn,
			final Integer dbType, final String dialect) throws Exception;

	/**
	 * @todo 执行存储过程
	 * @param sqlToyContext
	 * @param sqlToyConfig
	 * @param sql
	 * @param inParamsValue
	 * @param outParamsType
	 * @param conn
	 * @param dbType
	 * @param dialect
	 * @return
	 * @throws Exception
	 */
	public StoreResult executeStore(final SqlToyContext sqlToyContext, final SqlToyConfig sqlToyConfig,
			final String sql, final Object[] inParamsValue, final Integer[] outParamsType, final Connection conn,
			final Integer dbType, final String dialect) throws Exception;
}
