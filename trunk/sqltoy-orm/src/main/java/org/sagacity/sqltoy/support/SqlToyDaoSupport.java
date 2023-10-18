package org.sagacity.sqltoy.support;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.callback.DataSourceCallbackHandler;
import org.sagacity.sqltoy.callback.StreamResultHandler;
import org.sagacity.sqltoy.callback.UpdateRowHandler;
import org.sagacity.sqltoy.config.SqlConfigParseUtils;
import org.sagacity.sqltoy.config.model.DataType;
import org.sagacity.sqltoy.config.model.DataVersionConfig;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.FieldMeta;
import org.sagacity.sqltoy.config.model.ShardingStrategyConfig;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlType;
import org.sagacity.sqltoy.config.model.Translate;
import org.sagacity.sqltoy.dialect.DialectFactory;
import org.sagacity.sqltoy.dialect.executor.ParallQueryExecutor;
import org.sagacity.sqltoy.dialect.utils.DialectUtils;
import org.sagacity.sqltoy.exception.DataAccessException;
import org.sagacity.sqltoy.integration.DistributeIdGenerator;
import org.sagacity.sqltoy.link.Batch;
import org.sagacity.sqltoy.link.Delete;
import org.sagacity.sqltoy.link.Elastic;
import org.sagacity.sqltoy.link.Execute;
import org.sagacity.sqltoy.link.Load;
import org.sagacity.sqltoy.link.Mongo;
import org.sagacity.sqltoy.link.Query;
import org.sagacity.sqltoy.link.Save;
import org.sagacity.sqltoy.link.Store;
import org.sagacity.sqltoy.link.TableApi;
import org.sagacity.sqltoy.link.TreeTable;
import org.sagacity.sqltoy.link.Unique;
import org.sagacity.sqltoy.link.Update;
import org.sagacity.sqltoy.model.CacheMatchFilter;
import org.sagacity.sqltoy.model.ColumnMeta;
import org.sagacity.sqltoy.model.EntityQuery;
import org.sagacity.sqltoy.model.EntityUpdate;
import org.sagacity.sqltoy.model.IgnoreCaseLinkedMap;
import org.sagacity.sqltoy.model.IgnoreCaseSet;
import org.sagacity.sqltoy.model.IgnoreKeyCaseMap;
import org.sagacity.sqltoy.model.LockMode;
import org.sagacity.sqltoy.model.MapKit;
import org.sagacity.sqltoy.model.Page;
import org.sagacity.sqltoy.model.ParallQuery;
import org.sagacity.sqltoy.model.ParallQueryResult;
import org.sagacity.sqltoy.model.ParallelConfig;
import org.sagacity.sqltoy.model.QueryExecutor;
import org.sagacity.sqltoy.model.QueryResult;
import org.sagacity.sqltoy.model.SaveMode;
import org.sagacity.sqltoy.model.StoreResult;
import org.sagacity.sqltoy.model.TableMeta;
import org.sagacity.sqltoy.model.TreeTableModel;
import org.sagacity.sqltoy.model.UniqueExecutor;
import org.sagacity.sqltoy.model.inner.CacheMatchExtend;
import org.sagacity.sqltoy.model.inner.EntityQueryExtend;
import org.sagacity.sqltoy.model.inner.EntityUpdateExtend;
import org.sagacity.sqltoy.model.inner.QueryExecutorExtend;
import org.sagacity.sqltoy.model.inner.TranslateExtend;
import org.sagacity.sqltoy.plugins.CrossDbAdapter;
import org.sagacity.sqltoy.plugins.IUnifyFieldsHandler;
import org.sagacity.sqltoy.plugins.UnifyUpdateFieldsController;
import org.sagacity.sqltoy.plugins.datasource.DataSourceSelector;
import org.sagacity.sqltoy.plugins.id.IdGenerator;
import org.sagacity.sqltoy.translate.TranslateHandler;
import org.sagacity.sqltoy.translate.model.TranslateConfigModel;
import org.sagacity.sqltoy.utils.BeanUtil;
import org.sagacity.sqltoy.utils.BeanWrapper;
import org.sagacity.sqltoy.utils.DataSourceUtils;
import org.sagacity.sqltoy.utils.DateUtil;
import org.sagacity.sqltoy.utils.MapperUtils;
import org.sagacity.sqltoy.utils.QueryExecutorBuilder;
import org.sagacity.sqltoy.utils.ReservedWordsUtil;
import org.sagacity.sqltoy.utils.SqlUtil;
import org.sagacity.sqltoy.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sagacity-sqltoy
 * @description sqltoy的对外服务层,基础Dao支持工具类，用于被继承扩展自己的Dao，一般情况下推荐直接使用LightDao(旧项目SqlToyLazyDao)
 * @author zhongxuchen
 * @version v4.0,Date:2012-6-1
 * @modify Date:2012-8-8 {增强对象级联查询、删除、保存操作机制,不支持2层以上级联}
 * @modify Date:2012-8-23 {新增loadAll(List entities) 方法，可以批量通过主键取回详细信息}
 * @modify Date:2014-12-17 {1、增加sharding功能,改进saveOrUpdate功能，2、采用merge
 *         into策略;3、优化查询 条件和查询结果，变为一个对象，返回结果支持json输出}
 * @modify Date:2016-3-07 {优化存储过程调用,提供常用的执行方式,剔除过往复杂的实现逻辑和不必要的兼容性,让调用过程更加可读 }
 * @modify Date:2016-11-25
 *         {增加了分页优化功能,缓存相同查询条件的总记录数,在一定周期情况下无需再查询总记录数,从而提升分页查询的整体效率 }
 * @modify Date:2017-7-13 {增加saveAllNotExist功能,批量保存数据时忽视已经存在的,避免重复性数据主键冲突}
 * @modify Date:2017-11-1 {增加对象操作分库分表功能实现,精简和优化代码}
 * @modify Date:2019-3-1 {增加通过缓存获取Key然后作为查询条件cache-arg 功能，从而避免二次查询或like检索}
 * @modify Date:2019-6-25 {将异常统一转化成RuntimeException,不在方法上显式的抛异常}
 * @modify Date:2020-4-5 {分页Page模型中设置skipQueryCount=true跳过查总记录,默认false}
 * @modify Date:2020-8-25 {增加并行查询功能,为极端场景下提升查询效率,为开发者拆解复杂sql做多次查询影响性能提供了解决之道}
 * @modify Date:2020-10-20 {findByQuery 增加lockMode,便于查询并锁定记录}
 * @modify Date:2021-06-25
 *         {剔除linkDaoSupport、BaseDaoSupport,将link功能放入SqlToyDaoSupport}
 * @modify Date:2021-12-23 {优化updateByQuery支持set field=field+1依据字段值进行计算的模式}
 * @modify Date:2023-08-06 {增加executeMoreResultStore存储过程支持多结果返回}
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public class SqlToyDaoSupport {
	/**
	 * 定义日志
	 */
	protected final Logger logger = LoggerFactory.getLogger(SqlToyDaoSupport.class);

	/**
	 * 数据源
	 */
	protected DataSource dataSource;

	// 修改模式
	protected SaveMode UPDATE = SaveMode.UPDATE;

	// 忽视已经存在的记录
	protected SaveMode IGNORE = SaveMode.IGNORE;

	/**
	 * sqlToy上下文定义
	 */
	protected SqlToyContext sqlToyContext;

	/**
	 * 分布式id产生器
	 */
	private DistributeIdGenerator distributeIdGenerator = null;

	/**
	 * 各种数据库方言实现
	 */
	private DialectFactory dialectFactory = DialectFactory.getInstance();

	public void setDataSource(DataSource dataSource) {
		this.dataSource = dataSource;
	}

	/**
	 * @todo 获取数据源,如果参数dataSource为null则返回默认的dataSource
	 * @param pointDataSource
	 * @return
	 */
	protected DataSource getDataSource(DataSource pointDataSource) {
		return getDataSource(pointDataSource, null);
	}

	/**
	 * @TODO 获取sql对应的dataSource
	 * @param pointDataSource
	 * @param sqltoyConfig
	 * @return
	 */
	private DataSource getDataSource(DataSource pointDataSource, SqlToyConfig sqltoyConfig) {
		// xml中定义的sql配置了datasource
		String sqlDataSource = (null == sqltoyConfig) ? null : sqltoyConfig.getDataSource();
		// 提供一个扩展，让开发者在特殊场景下可以自行定义dataSourceSelector实现数据源的选择和获取
		DataSourceSelector dataSourceSelector = sqlToyContext.getDataSourceSelector();
		return dataSourceSelector.getDataSource(sqlToyContext.getAppContext(), pointDataSource, sqlDataSource,
				this.dataSource, sqlToyContext.getDefaultDataSource());
	}

	/**
	 * @todo 对象加载操作集合
	 * @return
	 */
	protected Load load() {
		return new Load(sqlToyContext, getDataSource(dataSource));
	}

	/**
	 * @todo 删除操作集合
	 * @return
	 */
	protected Delete delete() {
		return new Delete(sqlToyContext, getDataSource(dataSource));
	}

	/**
	 * @todo 修改操作集合
	 * @return
	 */
	protected Update update() {
		return new Update(sqlToyContext, getDataSource(dataSource));
	}

	/**
	 * @todo 保存操作集合
	 * @return
	 */
	protected Save save() {
		return new Save(sqlToyContext, getDataSource(dataSource));
	}

	/**
	 * @todo 查询操作集合
	 * @return
	 */
	protected Query query() {
		return new Query(sqlToyContext, getDataSource(dataSource));
	}

	/**
	 * @todo 存储过程操作集合
	 * @return
	 */
	protected Store store() {
		return new Store(sqlToyContext, getDataSource(dataSource));
	}

	/**
	 * @todo 唯一性验证操作集合
	 * @return
	 */
	protected Unique unique() {
		return new Unique(sqlToyContext, getDataSource(dataSource));
	}

	/**
	 * @todo 树形表结构封装操作集合
	 * @return
	 */
	protected TreeTable treeTable() {
		return new TreeTable(sqlToyContext, getDataSource(dataSource));
	}

	/**
	 * @todo sql语句直接执行修改数据库操作集合
	 * @return
	 */
	protected Execute execute() {
		return new Execute(sqlToyContext, getDataSource(dataSource));
	}

	/**
	 * @todo 批量执行操作集合
	 * @return
	 */
	protected Batch batch() {
		return new Batch(sqlToyContext, getDataSource(dataSource));
	}

	/**
	 * @todo 提供一个获取数据库表信息和操作表信息的TableApi集合
	 * @return
	 */
	protected TableApi tableApi() {
		return new TableApi(sqlToyContext, getDataSource(dataSource));
	}

	/**
	 * @todo 提供基于ES的查询(仅针对查询部分)
	 * @return
	 */
	protected Elastic elastic() {
		return new Elastic(sqlToyContext, getDataSource(dataSource));
	}

	/**
	 * @todo 提供基于mongo的查询(仅针对查询部分)
	 * @return
	 */
	protected Mongo mongo() {
		return new Mongo(sqlToyContext, getDataSource(dataSource));
	}

	/**
	 * @param sqlToyContext the sqlToyContext to set
	 */
	public void setSqlToyContext(SqlToyContext sqlToyContext) {
		this.sqlToyContext = sqlToyContext;
	}

	/**
	 * @return the sqlToyContext
	 */
	protected SqlToyContext getSqlToyContext() {
		return sqlToyContext;
	}

	/**
	 * @todo 获取sqlId 在sqltoy中的配置模型
	 * @param sqlKey
	 * @param sqlType
	 * @return
	 */
	protected SqlToyConfig getSqlToyConfig(final String sqlKey, final SqlType sqlType) {
		return sqlToyContext.getSqlToyConfig(sqlKey, (sqlType == null) ? SqlType.search : sqlType, getDialect(null),
				null);
	}

	/**
	 * @todo 判断数据库中数据是否唯一，true 表示唯一(可以插入)，false表示不唯一(数据库已经存在该数据)，用法
	 *       isUnique(dictDetailVO,new
	 *       String[]{"dictTypeCode","dictName"})，将会根据给定的2个参数
	 *       通过VO取到相应的值，作为组合条件到dictDetailVO对应的表中查询记录是否存在
	 * @param entity
	 * @param paramsNamed 对象属性名称(不是数据库表字段名称)
	 * @return
	 */
	protected boolean isUnique(final Serializable entity, final String... paramsNamed) {
		return isUnique(new UniqueExecutor(entity, paramsNamed));
	}

	/*
	 * @see isUnique(final Serializable entity, final String[] paramsNamed)
	 */
	protected boolean isUnique(final UniqueExecutor uniqueExecutor) {
		return dialectFactory.isUnique(sqlToyContext, uniqueExecutor,
				this.getDataSource(uniqueExecutor.getDataSource()));
	}

	protected Long getCountBySql(final String sqlOrSqlId, final Map<String, Object> paramsMap) {
		return getCountByQuery(new QueryExecutor(sqlOrSqlId, (paramsMap == null) ? MapKit.map() : paramsMap));
	}

	/**
	 * @todo 获取数据库查询语句的总记录数
	 * @param sqlOrSqlId
	 * @param paramsNamed
	 * @param paramsValue
	 * @return Long
	 */
	protected Long getCountBySql(final String sqlOrSqlId, final String[] paramsNamed, final Object[] paramsValue) {
		return getCountByQuery(new QueryExecutor(sqlOrSqlId, paramsNamed, paramsValue));
	}

	/**
	 * @TODO 通过entity对象来组织count查询语句
	 * @param entityClass
	 * @param entityQuery
	 * @return
	 */
	protected Long getCountByEntityQuery(Class entityClass, EntityQuery entityQuery) {
		if (null == entityClass) {
			throw new IllegalArgumentException("getCountByEntityQuery entityClass值不能为空!");
		}
		return (Long) findEntityBase(entityClass, null, (entityQuery == null) ? EntityQuery.create() : entityQuery,
				entityClass, true);
	}

	/**
	 * @todo 指定数据源查询记录数量
	 * @param queryExecutor
	 * @return
	 */
	protected Long getCountByQuery(final QueryExecutor queryExecutor) {
		QueryExecutorExtend extend = queryExecutor.getInnerModel();
		SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(queryExecutor, SqlType.search,
				getDialect(extend.dataSource));
		Long result = dialectFactory.getCountBySql(sqlToyContext, queryExecutor, sqlToyConfig,
				this.getDataSource(extend.dataSource, sqlToyConfig));
		// 产品化场景，适配其他数据库验证查询(仅仅在设置了redoDataSources时生效)
		CrossDbAdapter.redoCountQuery(sqlToyContext, dialectFactory, queryExecutor);
		return result;
	}

	protected StoreResult executeStore(final String storeSqlOrKey, final Object[] inParamValues,
			final Integer[] outParamsType, final Class resultType) {
		return executeStore(storeSqlOrKey, inParamValues, outParamsType, resultType, null);
	}

	protected StoreResult executeStore(final String storeSqlOrKey, final Object[] inParamValues) {
		return executeStore(storeSqlOrKey, inParamValues, null, null, null);
	}

	/**
	 * @todo 通用存储过程调用,一般数据库{?=call xxxStore(? in,? in,? out)} 针对oracle数据库只能{call
	 *       xxxStore(? in,? in,? out)} 同时结果集必须通过OracleTypes.CURSOR out 参数返回
	 *       目前此方法只能返回一个结果集(集合类数据),可以返回多个非集合类数据，如果有特殊用法，则自行封装调用
	 * @param storeSqlOrKey 可以直接传call storeName (?,?) 也可以传xml中的存储过程sqlId
	 * @param inParamsValue
	 * @param outParamsType (可以为null)
	 * @param resultType    VOClass,HashMap或null(表示二维List)
	 * @param dataSource
	 * @return
	 */
	protected StoreResult executeStore(final String storeSqlOrKey, final Object[] inParamsValue,
			final Integer[] outParamsType, final Class resultType, final DataSource dataSource) {
		SqlToyConfig sqlToyConfig = getSqlToyConfig(storeSqlOrKey, SqlType.search);
		return dialectFactory.executeStore(sqlToyContext, sqlToyConfig, inParamsValue, outParamsType,
				new Class[] { resultType }, false, getDataSource(dataSource, sqlToyConfig));
	}

	protected StoreResult executeMoreResultStore(final String storeSqlOrKey, final Object[] inParamsValue,
			final Integer[] outParamsType, final Class[] resultTypes) {
		SqlToyConfig sqlToyConfig = getSqlToyConfig(storeSqlOrKey, SqlType.search);
		return dialectFactory.executeStore(sqlToyContext, sqlToyConfig, inParamsValue, outParamsType, resultTypes, true,
				getDataSource(null, sqlToyConfig));
	}

	/**
	 * @param sqlOrSqlId
	 * @param paramsNamed
	 * @param paramsValue
	 * @return
	 * @see #getSingleValue(String, Map)
	 */
	@Deprecated
	protected Object getSingleValue(final String sqlOrSqlId, final String[] paramsNamed, final Object[] paramsValue) {
		return getSingleValue(sqlOrSqlId, paramsNamed, paramsValue, null);
	}

	protected Object getSingleValue(final String sqlOrSqlId, final Map<String, Object> paramsMap) {
		Object queryResult = loadByQuery(new QueryExecutor(sqlOrSqlId, (paramsMap == null) ? MapKit.map() : paramsMap));
		if (null != queryResult) {
			return ((List) queryResult).get(0);
		}
		return null;
	}

	// add 2022-2-25
	protected <T> T getSingleValue(final String sqlOrSqlId, final Map<String, Object> paramsMap,
			final Class<T> resultType) {
		if (resultType == null) {
			throw new IllegalArgumentException("getSingleValue resultType 不能为null!");
		}
		Object value = getSingleValue(sqlOrSqlId, paramsMap);
		if (value == null) {
			return null;
		}
		try {
			return (T) BeanUtil.convertType(value, DataType.getType(resultType), resultType.getTypeName());
		} catch (Exception e) {
			throw new DataAccessException("getSingleValue方法获取单个值失败:" + e.getMessage(), e);
		}
	}

	/**
	 * @todo 返回单行单列值，如果结果集存在多条数据则返回null
	 * @param sqlOrSqlId
	 * @param paramsNamed
	 * @param paramsValue
	 * @param dataSource
	 * @return
	 */
	protected Object getSingleValue(final String sqlOrSqlId, final String[] paramsNamed, final Object[] paramsValue,
			final DataSource dataSource) {
		Object queryResult = loadByQuery(
				new QueryExecutor(sqlOrSqlId, paramsNamed, paramsValue).dataSource(dataSource));
		if (null != queryResult) {
			return ((List) queryResult).get(0);
		}
		return null;
	}

	/**
	 * @todo 根据给定的对象中的主键值获取对象完整信息
	 * @param entity
	 * @return
	 */
	protected <T extends Serializable> T load(final T entity) {
		if (entity == null) {
			return null;
		}
		EntityMeta entityMeta = this.getEntityMeta(entity.getClass());
		if (SqlConfigParseUtils.isNamedQuery(entityMeta.getLoadSql(null))) {
			return (T) this.loadBySql(entityMeta.getLoadSql(null), entity);
		}
		return load(entity, null, null);
	}

	/**
	 * @todo 提供锁定功能的加载
	 * @param entity
	 * @param lockMode
	 * @return
	 */
	protected <T extends Serializable> T load(final T entity, final LockMode lockMode) {
		return load(entity, lockMode, null);
	}

	/**
	 * @todo <b>根据主键值获取对应的记录信息</b>
	 * @param entity
	 * @param lockMode
	 * @param dataSource
	 * @return
	 */
	protected <T extends Serializable> T load(final T entity, final LockMode lockMode, final DataSource dataSource) {
		return dialectFactory.load(sqlToyContext, entity, null, lockMode, this.getDataSource(dataSource));
	}

	/**
	 * @todo 指定需要级联加载的类型，通过主对象加载自身和相应的子对象集合
	 * @param entity
	 * @param lockMode
	 * @param cascadeTypes
	 * @return
	 */
	protected <T extends Serializable> T loadCascade(T entity, LockMode lockMode, Class... cascadeTypes) {
		if (entity == null) {
			return null;
		}
		Class[] cascades = cascadeTypes;
		// 当没有指定级联子类默认全部级联加载(update 2020-7-31 缺失了cascades.length == 0 判断)
		if (cascades == null || cascades.length == 0) {
			cascades = getEntityMeta(entity.getClass()).getCascadeTypes();
		}
		return dialectFactory.load(sqlToyContext, entity, cascades, lockMode, this.getDataSource(null));
	}

	/**
	 * @todo 批量根据实体对象的主键获取对象的详细信息
	 * @param entities
	 * @param lockMode
	 * @return
	 */
	protected <T extends Serializable> List<T> loadAll(final List<T> entities, final LockMode lockMode) {
		return dialectFactory.loadAll(sqlToyContext, entities, null, lockMode, this.getDataSource(null));
	}

	/**
	 * @TODO 根据id集合批量加载对象
	 * @param <T>
	 * @param entityClass
	 * @param ids
	 * @return
	 */
	protected <T extends Serializable> List<T> loadByIds(final Class<T> entityClass, Object... ids) {
		return loadByIds(entityClass, null, ids);
	}

	/**
	 * @TODO 通过id集合批量加载对象
	 * @param <T>
	 * @param entityClass
	 * @param lockMode
	 * @param ids
	 * @return
	 */
	protected <T extends Serializable> List<T> loadByIds(final Class<T> entityClass, final LockMode lockMode,
			Object... ids) {
		if (entityClass == null || ids == null || ids.length == 0 || (ids.length == 1 && ids[0] == null)) {
			throw new IllegalArgumentException("loadByIds操作:entityClass、主键值数据不能为空!");
		}
		EntityMeta entityMeta = getEntityMeta(entityClass);
		validEntity(entityMeta, entityClass, true);
		if (entityMeta.getIdArray().length != 1) {
			throw new IllegalArgumentException("loadByIds操作只支持单主键POJO对象!");
		}
		Object[] realIds;
		// 单个Collection,将List转为Array数组
		if (ids.length == 1 && ids[0] instanceof Collection) {
			realIds = ((Collection) ids[0]).toArray();
		} else {
			realIds = ids;
		}
		List<T> entities = BeanUtil.wrapEntities(sqlToyContext.getTypeHandler(), entityMeta, entityClass, realIds);
		return dialectFactory.loadAll(sqlToyContext, entities, null, lockMode, this.getDataSource(null));
	}

	/**
	 * @todo 批量对象级联加载,指定级联加载的子表
	 * @param entities
	 * @param lockMode
	 * @param cascadeTypes
	 * @return
	 */
	protected <T extends Serializable> List<T> loadAllCascade(final List<T> entities, final LockMode lockMode,
			final Class... cascadeTypes) {
		if (entities == null || entities.isEmpty()) {
			return entities;
		}
		Class[] cascades = cascadeTypes;
		if (cascades == null || cascades.length == 0) {
			cascades = getEntityMeta(entities.get(0).getClass()).getCascadeTypes();
		}
		return dialectFactory.loadAll(sqlToyContext, entities, cascades, lockMode, this.getDataSource(null));
	}

	protected <T> T loadBySql(final String sqlOrSqlId, final Map<String, Object> paramsMap, final Class<T> resultType) {
		return (T) loadByQuery(
				new QueryExecutor(sqlOrSqlId, (paramsMap == null) ? MapKit.map() : paramsMap).resultType(resultType));
	}

	/**
	 * @todo 根据sql语句查询并返回单个VO对象(可指定自定义对象,sqltoy根据查询label跟对象的属性名称进行匹配映射)
	 * @param sqlOrSqlId
	 * @param paramNames
	 * @param paramValues
	 * @param resultType
	 * @return
	 */
	protected <T> T loadBySql(final String sqlOrSqlId, final String[] paramNames, final Object[] paramValues,
			final Class<T> resultType) {
		return (T) loadByQuery(new QueryExecutor(sqlOrSqlId, paramNames, paramValues).resultType(resultType));
	}

	/**
	 * @todo 解析sql中:named 属性到entity对象获取对应的属性值作为查询条件,并将查询结果以entity的class类型返回
	 * @param sqlOrSqlId
	 * @param entity
	 * @return
	 */
	protected <T extends Serializable> T loadBySql(final String sqlOrSqlId, final T entity) {
		return (T) loadByQuery(new QueryExecutor(sqlOrSqlId, entity));
	}

	protected <T extends Serializable> T loadEntity(Class<T> entityClass, EntityQuery entityQuery) {
		List<T> result = findEntity(entityClass, entityQuery);
		if (result == null || result.isEmpty()) {
			return null;
		}
		if (result.size() == 1) {
			return result.get(0);
		}
		throw new IllegalArgumentException("loadEntity查询出:" + result.size() + " 条记录,不符合load查询单条记录的预期!");
	}

	/**
	 * TODO 通过构造QueyExecutor 提供更加灵活的参数传递方式，包括DataSource 比如:
	 * <li>1、new QueryExecutor(sql,entity).dataSource(dataSource)</li>
	 * <li>2、new
	 * QueryExecutor(sql).names(paramNames).values(paramValues).resultType(resultType);
	 * </li>
	 * 
	 * @param queryExecutor
	 * @return
	 */
	protected Object loadByQuery(final QueryExecutor queryExecutor) {
		QueryExecutorExtend extend = queryExecutor.getInnerModel();
		SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(queryExecutor, SqlType.search,
				getDialect(extend.dataSource));
		QueryResult result = dialectFactory.findByQuery(sqlToyContext, queryExecutor, sqlToyConfig, null,
				this.getDataSource(extend.dataSource, sqlToyConfig));
		// 产品化场景，适配其他数据库验证查询(仅仅在设置了redoDataSources时生效)
		CrossDbAdapter.redoQuery(sqlToyContext, dialectFactory, queryExecutor);
		List rows = result.getRows();
		if (rows == null || rows.isEmpty()) {
			return null;
		}
		if (rows.size() == 1) {
			return rows.get(0);
		}
		throw new IllegalArgumentException("loadByQuery查询出:" + rows.size() + " 条记录,不符合load查询单条记录的预期!");
	}

	/**
	 * @todo 执行无条件的sql语句,一般是一个修改、删除等操作，并返回修改的记录数量
	 * @param sqlOrSqlId
	 * @return
	 */
	protected Long executeSql(final String sqlOrSqlId) {
		return executeSql(sqlOrSqlId, null, null, null, null);
	}

	/**
	 * @todo 解析sql中的参数名称，以此名称到entity中提取对应的值作为查询条件值执行sql
	 * @param sqlOrSqlId
	 * @param entity
	 * @return
	 */
	protected Long executeSql(final String sqlOrSqlId, final Serializable entity) {
		SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(sqlOrSqlId, SqlType.update, getDialect(dataSource),
				entity);
		return dialectFactory.executeSql(sqlToyContext, sqlToyConfig, new QueryExecutor(sqlOrSqlId, entity), null, null,
				getDataSource(null, sqlToyConfig));
	}

	protected Long executeSql(final String sqlOrSqlId, final Map<String, Object> paramsMap) {
		return executeSql(sqlOrSqlId, (Serializable) new IgnoreKeyCaseMap(paramsMap));
	}

	/**
	 * @todo 执行无返回结果的SQL(返回updateCount)
	 * @param sqlOrSqlId
	 * @param paramsNamed
	 * @param paramsValue
	 * @return
	 */
	protected Long executeSql(final String sqlOrSqlId, final String[] paramsNamed, final Object[] paramsValue) {
		return executeSql(sqlOrSqlId, paramsNamed, paramsValue, null, null);
	}

	/**
	 * @todo 执行无返回结果的SQL(返回updateCount),根据autoCommit设置是否自动提交
	 * @param sqlOrSqlId
	 * @param paramsNamed
	 * @param paramsValue
	 * @param autoCommit  自动提交，默认可以填null
	 * @param dataSource
	 * @return
	 */
	protected Long executeSql(final String sqlOrSqlId, final String[] paramsNamed, final Object[] paramsValue,
			final Boolean autoCommit, final DataSource dataSource) {
		QueryExecutor query = new QueryExecutor(sqlOrSqlId).names(paramsNamed).values(paramsValue);
		SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(query, SqlType.update, getDialect(dataSource));
		return dialectFactory.executeSql(sqlToyContext, sqlToyConfig, query, null, autoCommit,
				getDataSource(dataSource, sqlToyConfig));
	}

	protected Long batchUpdate(final String sqlOrSqlId, final List dataSet, final Boolean autoCommit) {
		// 例如sql 为:merge into table update set xxx=:param
		// dataSet可以是VO List,可以根据属性自动映射到:param
		return batchUpdate(sqlOrSqlId, dataSet, sqlToyContext.getBatchSize(), autoCommit, null);
	}

	/**
	 * @todo 通过jdbc方式批量插入数据，一般提供给数据采集时或插入临时表使用
	 * @param sqlOrSqlId
	 * @param dataSet    支持List<List>、List<Object[]>(sql中?传参) ;List<VO>、List<Map>
	 *                   形式(sql中:paramName传参)
	 * @param batchSize
	 * @param autoCommit 自动提交，默认可以填null
	 * @return
	 */
	protected Long batchUpdate(final String sqlOrSqlId, final List dataSet, final int batchSize,
			final Boolean autoCommit) {
		return batchUpdate(sqlOrSqlId, dataSet, batchSize, autoCommit, null);
	}

	/**
	 * @todo 批量执行sql修改或删除操作
	 * @param sqlOrSqlId
	 * @param dataSet    支持List<List>、List<Object[]>(sql中?传参) ;List<VO>、List<Map>
	 *                   形式(sql中:paramName传参)
	 * @param batchSize
	 * @param autoCommit
	 * @param dataSource
	 * @return
	 */
	protected Long batchUpdate(final String sqlOrSqlId, final List dataSet, final int batchSize,
			final Boolean autoCommit, final DataSource dataSource) {
		SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(sqlOrSqlId, SqlType.update, getDialect(dataSource),
				null);
		return dialectFactory.batchUpdate(sqlToyContext, sqlToyConfig, dataSet, batchSize, null, null, autoCommit,
				getDataSource(dataSource, sqlToyConfig));
	}

	protected boolean wrapTreeTableRoute(final TreeTableModel treeModel) {
		return wrapTreeTableRoute(treeModel, null);
	}

	/**
	 * @todo 构造树形表的节点路径、节点层级、节点类别(是否叶子节点)
	 * @param treeModel
	 * @param dataSource
	 * @return
	 */
	protected boolean wrapTreeTableRoute(final TreeTableModel treeModel, final DataSource dataSource) {
		return dialectFactory.wrapTreeTableRoute(sqlToyContext, treeModel, this.getDataSource(dataSource));
	}

	/**
	 * @todo 以entity对象的属性给sql中的:named 传参数，进行查询，并返回entityClass类型的集合
	 * @param sqlOrSqlId
	 * @param entity
	 * @return
	 */
	protected <T extends Serializable> List<T> findBySql(final String sqlOrSqlId, final T entity) {
		return (List<T>) findByQuery(new QueryExecutor(sqlOrSqlId, entity)).getRows();
	}

	protected <T> List<T> findBySql(final String sqlOrSqlId, final Map<String, Object> paramsMap,
			final Class<T> resultType) {
		return (List<T>) findByQuery(
				new QueryExecutor(sqlOrSqlId, (paramsMap == null) ? MapKit.map() : paramsMap).resultType(resultType))
				.getRows();
	}

	/**
	 * @TODO 查询集合
	 * @param <T>
	 * @param sqlOrSqlId
	 * @param paramsNamed
	 * @param paramsValue
	 * @param resultType  分null(返回二维List)、voClass、HashMap.class、LinkedHashMap.class等
	 * @return
	 */
	protected <T> List<T> findBySql(final String sqlOrSqlId, final String[] paramsNamed, final Object[] paramsValue,
			final Class<T> resultType) {
		QueryExecutor query = new QueryExecutor(sqlOrSqlId, paramsNamed, paramsValue);
		if (resultType != null) {
			query.resultType(resultType);
		}
		return (List<T>) findByQuery(query).getRows();
	}

	/**
	 * @TODO 以queryExecutor 封装sql、条件、数据库源等进行集合查询
	 * @param queryExecutor (可动态设置数据源)
	 * @return
	 */
	protected QueryResult findByQuery(final QueryExecutor queryExecutor) {
		SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(queryExecutor, SqlType.search,
				getDialect(queryExecutor.getInnerModel().dataSource));
		QueryResult result = dialectFactory.findByQuery(sqlToyContext, queryExecutor, sqlToyConfig,
				queryExecutor.getInnerModel().lockMode,
				getDataSource(queryExecutor.getInnerModel().dataSource, sqlToyConfig));
		// 产品化场景，适配其他数据库验证查询(仅仅在设置了redoDataSources时生效)
		CrossDbAdapter.redoQuery(sqlToyContext, dialectFactory, queryExecutor);
		return result;
	}

	/**
	 * @TODO 按照流模式活动查询结果数据
	 * @param queryExecutor
	 * @param streamResultHandler
	 */
	protected void fetchStream(final QueryExecutor queryExecutor, final StreamResultHandler streamResultHandler) {
		SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(queryExecutor, SqlType.search,
				getDialect(queryExecutor.getInnerModel().dataSource));
		dialectFactory.fetchStream(sqlToyContext, queryExecutor, sqlToyConfig, streamResultHandler,
				getDataSource(queryExecutor.getInnerModel().dataSource, sqlToyConfig));
	}

	/**
	 * @todo 以QueryExecutor 封装sql、参数等条件，实现分页查询
	 * @param page
	 * @param queryExecutor (可动态设置数据源)
	 * @return
	 */
	protected QueryResult findPageByQuery(final Page page, final QueryExecutor queryExecutor) {
		String dialect = getDialect(queryExecutor.getInnerModel().dataSource);
		SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(queryExecutor, SqlType.search, dialect);
		// 自定义countsql
		String countSql = queryExecutor.getInnerModel().countSql;
		if (StringUtil.isNotBlank(countSql)) {
			// 存在@include(sqlId) 或 @include(:sqlScript)
			if (StringUtil.matches(countSql, SqlToyConstants.INCLUDE_PATTERN)) {
				SqlToyConfig countSqlConfig = sqlToyContext.getSqlToyConfig(countSql, SqlType.search, dialect,
						QueryExecutorBuilder.getParamValues(queryExecutor));
				sqlToyConfig.setCountSql(countSqlConfig.getSql());
			} else {
				sqlToyConfig.setCountSql(countSql);
			}
		}
		QueryResult result;
		// 跳过查询总记录数量
		if (page.getSkipQueryCount() != null && page.getSkipQueryCount()) {
			result = dialectFactory.findSkipTotalCountPage(sqlToyContext, queryExecutor, sqlToyConfig, page.getPageNo(),
					page.getPageSize(), getDataSource(queryExecutor.getInnerModel().dataSource, sqlToyConfig));
		} else {
			result = dialectFactory.findPage(sqlToyContext, queryExecutor, sqlToyConfig, page.getPageNo(),
					page.getPageSize(), page.getOverPageToFirst(),
					getDataSource(queryExecutor.getInnerModel().dataSource, sqlToyConfig));
		}
		// 产品化场景，适配其他数据库验证查询(仅仅在设置了redoDataSources时生效)
		CrossDbAdapter.redoPageQuery(sqlToyContext, dialectFactory, queryExecutor, page);
		return result;
	}

	/**
	 * @todo 指定sql和参数名称以及名称对应的值和返回结果的类型(类型可以是java.util.HashMap),进行分页查询
	 *       sql可以是一个具体的语句也可以是xml中定义的sqlId
	 * @param page
	 * @param sqlOrSqlId
	 * @param paramsNamed
	 * @param paramsValue
	 * @param resultType(null则返回List<List>二维集合,HashMap.class:则返回List<HashMap<columnLabel,columnValue>>)
	 * @return
	 */
	protected <T> Page<T> findPageBySql(final Page page, final String sqlOrSqlId, final String[] paramsNamed,
			final Object[] paramsValue, Class<T> resultType) {
		QueryExecutor query = new QueryExecutor(sqlOrSqlId, paramsNamed, paramsValue);
		if (resultType != null) {
			query.resultType(resultType);
		}
		return (Page<T>) findPageByQuery(page, query).getPageResult();
	}

	protected <T extends Serializable> Page<T> findPageBySql(final Page page, final String sqlOrSqlId, final T entity) {
		return (Page<T>) findPageByQuery(page, new QueryExecutor(sqlOrSqlId, entity)).getPageResult();
	}

	protected <T> Page<T> findPageBySql(final Page page, final String sqlOrSqlId, final Map<String, Object> paramsMap,
			Class<T> resultType) {
		return (Page<T>) findPageByQuery(page, new QueryExecutor(sqlOrSqlId, paramsMap).resultType(resultType))
				.getPageResult();
	}

	protected <T> List<T> findTopBySql(final String sqlOrSqlId, final Map<String, Object> paramsMap,
			final Class<T> resultType, final double topSize) {
		return (List<T>) findTopByQuery(
				new QueryExecutor(sqlOrSqlId, (paramsMap == null) ? MapKit.map() : paramsMap).resultType(resultType),
				topSize).getRows();
	}

	/**
	 * @todo 取符合条件的结果前多少数据,topSize>1 则取整数返回记录数量，topSize<1 则按比例返回结果记录(topSize必须是大于0)
	 * @param sqlOrSqlId
	 * @param paramsNamed
	 * @param paramsValue
	 * @param resultType(null则返回List<List>二维集合,HashMap.class:则返回List<HashMap<columnLabel,columnValue>>)
	 * @param topSize                                                                                   >1
	 *                                                                                                  取整数部分，<1
	 *                                                                                                  则表示按比例获取
	 * @return
	 */
	protected <T> List<T> findTopBySql(final String sqlOrSqlId, final String[] paramsNamed, final Object[] paramsValue,
			final Class<T> resultType, final double topSize) {
		return (List<T>) findTopByQuery(new QueryExecutor(sqlOrSqlId, paramsNamed, paramsValue).resultType(resultType),
				topSize).getRows();
	}

	protected <T extends Serializable> List<T> findTopBySql(final String sqlOrSqlId, final T entity,
			final double topSize) {
		return (List<T>) findTopByQuery(new QueryExecutor(sqlOrSqlId, entity), topSize).getRows();
	}

	/**
	 * @TODO 以queryExecutor封装sql、条件参数、数据源等进行取top集合查询
	 * @param queryExecutor (可动态设置数据源)
	 * @param topSize
	 * @return
	 */
	protected QueryResult findTopByQuery(final QueryExecutor queryExecutor, final double topSize) {
		SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(queryExecutor, SqlType.search,
				getDialect(queryExecutor.getInnerModel().dataSource));
		QueryResult result = dialectFactory.findTop(sqlToyContext, queryExecutor, sqlToyConfig, topSize,
				this.getDataSource(queryExecutor.getInnerModel().dataSource, sqlToyConfig));
		// 产品化场景，适配其他数据库验证查询(仅仅在设置了redoDataSources时生效)
		CrossDbAdapter.redoTopQuery(sqlToyContext, dialectFactory, queryExecutor, topSize);
		return result;
	}

	/**
	 * @todo 在符合条件的结果中随机提取多少条记录,randomCount>1 则取整数记录，randomCount<1 则按比例提取随机记录
	 *       如randomCount=0.05 总记录数为100,则随机取出5条记录
	 * @param queryExecutor (可动态设置数据源)
	 * @param randomCount
	 * @return
	 */
	protected QueryResult getRandomResult(final QueryExecutor queryExecutor, final double randomCount) {
		SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(queryExecutor, SqlType.search,
				getDialect(queryExecutor.getInnerModel().dataSource));
		QueryResult result = dialectFactory.getRandomResult(sqlToyContext, queryExecutor, sqlToyConfig, randomCount,
				this.getDataSource(queryExecutor.getInnerModel().dataSource, sqlToyConfig));
		// 产品化场景，适配其他数据库验证查询(仅仅在设置了redoDataSources时生效)
		CrossDbAdapter.redoRandomQuery(sqlToyContext, dialectFactory, queryExecutor, randomCount);
		return result;
	}

	protected <T> List<T> getRandomResult(final String sqlOrSqlId, final Map<String, Object> paramsMap,
			Class<T> resultType, final double randomCount) {
		return (List<T>) getRandomResult(
				new QueryExecutor(sqlOrSqlId, (paramsMap == null) ? MapKit.map() : paramsMap).resultType(resultType),
				randomCount).getRows();
	}

	// resultType(null则返回List<List>二维集合,HashMap.class:则返回List<HashMap<columnLabel,columnValue>>)
	protected <T> List<T> getRandomResult(final String sqlOrSqlId, final String[] paramsNamed,
			final Object[] paramsValue, Class<T> resultType, final double randomCount) {
		return (List<T>) getRandomResult(new QueryExecutor(sqlOrSqlId, paramsNamed, paramsValue).resultType(resultType),
				randomCount).getRows();
	}

	protected void truncate(final Class entityClass, final Boolean autoCommit) {
		if (null == entityClass) {
			throw new IllegalArgumentException("entityClass is null,please check!");
		}
		EntityMeta entityMeta = getEntityMeta(entityClass);
		validEntity(entityMeta, entityClass, false);
		truncate(entityMeta.getSchemaTable(null, null), autoCommit, null);
	}

	/**
	 * @todo <b>快速删除表中的数据,autoCommit为null表示按照连接的默认值(如dbcp可以配置默认是否autoCommit)</b>
	 * @param tableName
	 * @param autoCommit
	 * @param dataSource
	 */
	protected void truncate(final String tableName, final Boolean autoCommit, final DataSource dataSource) {
		if (StringUtil.isBlank(tableName)) {
			throw new IllegalArgumentException("truncate tableName is blank or null,please check!");
		}
		this.executeSql("truncate table ".concat(tableName), null, null, autoCommit, this.getDataSource(dataSource));
	}

	/**
	 * @todo 保存对象数据(返回插入的主键值),会针对对象的子集进行级联保存
	 * @param entity
	 * @return
	 */
	protected Object save(final Serializable entity) {
		return this.save(entity, null);
	}

	/**
	 * @todo <b>指定数据库插入单个对象并返回主键值,会针对对象的子表集合数据进行级联保存</b>
	 * @param entity
	 * @param dataSource
	 * @return
	 */
	protected Object save(final Serializable entity, final DataSource dataSource) {
		return dialectFactory.save(sqlToyContext, entity, this.getDataSource(dataSource));
	}

	/**
	 * @todo 批量插入对象(会自动根据主键策略产生主键值,并填充对象集合),不做级联操作
	 * @param <T>
	 * @param entities
	 * @return
	 */
	protected <T extends Serializable> Long saveAll(final List<T> entities) {
		return this.saveAll(entities, null);
	}

	/**
	 * @todo <b>指定数据库进行批量插入</b>
	 * @param <T>
	 * @param entities
	 * @param dataSource
	 * @return
	 */
	protected <T extends Serializable> Long saveAll(final List<T> entities, final DataSource dataSource) {
		return dialectFactory.saveAll(sqlToyContext, entities, sqlToyContext.getBatchSize(), null,
				this.getDataSource(dataSource), null);
	}

	/**
	 * @todo 保存对象数据(返回插入的主键值),忽视已经存在的
	 * @param entities
	 * @return
	 */
	protected <T extends Serializable> Long saveAllIgnoreExist(final List<T> entities) {
		return this.saveAllIgnoreExist(entities, null);
	}

	/**
	 * @todo 保存对象数据(返回插入的主键值),忽视已经存在的
	 * @param entities
	 * @param dataSource
	 * @return
	 */
	protected <T extends Serializable> Long saveAllIgnoreExist(final List<T> entities, final DataSource dataSource) {
		return dialectFactory.saveAllIgnoreExist(sqlToyContext, entities, sqlToyContext.getBatchSize(), null,
				this.getDataSource(dataSource), null);
	}

	/**
	 * @todo update对象(值为null的属性不修改,通过forceUpdateProps指定要进行强制修改属性)
	 * @param entity
	 * @param forceUpdateProps 强制修改的属性
	 * @return
	 */
	protected Long update(final Serializable entity, final String... forceUpdateProps) {
		return this.update(entity, forceUpdateProps, null);
	}

	/**
	 * @todo <b>根据传入的对象，通过其主键值查询并修改其它属性的值</b>
	 * @param entity
	 * @param forceUpdateProps
	 * @param dataSource
	 * @return
	 */
	protected Long update(final Serializable entity, final String[] forceUpdateProps, final DataSource dataSource) {
		if (entity == null) {
			logger.warn("update entity is null,please check!");
			return 0L;
		}
		Class entityClass = entity.getClass();
		EntityMeta entityMeta = getEntityMeta(entityClass);
		validEntity(entityMeta, entityClass, true);
		DataVersionConfig dataVersion = entityMeta.getDataVersion();
		if (dataVersion != null) {
			Object version = BeanUtil.getProperty(entity, dataVersion.getField());
			if (version == null) {
				throw new IllegalArgumentException("表:" + entityMeta.getTableName() + " 存在版本@DataVersion配置，属性:"
						+ dataVersion.getField() + " 值不能为空!");
			}
			String where = "";
			for (String field : entityMeta.getIdArray()) {
				where = where.concat(field).concat("=:").concat(field).concat(" and ");
			}
			where = where.concat(dataVersion.getField()).concat("=:").concat(dataVersion.getField());
			// 锁住id+version条件符合的记录
			Serializable versionEntity = loadEntity(entity.getClass(), EntityQuery.create()
					.select(entityMeta.getIdArray()).where(where).values(entity).lock(LockMode.UPGRADE_NOWAIT));
			String verStr = version.toString();
			if (versionEntity == null) {
				throw new DataAccessException(
						"表:" + entityMeta.getTableName() + " 数据版本:" + verStr + " 正在被其他用户修改或已经被更新!");
			}
			// 以日期开头
			if (dataVersion.isStartDate()) {
				String nowDate = DateUtil.formatDate(DateUtil.getNowTime(), DateUtil.FORMAT.DATE_8CHAR);
				if (verStr.startsWith(nowDate)) {
					verStr = nowDate + (Integer.parseInt(verStr.substring(8)) + 1);
				} else {
					verStr = nowDate + 1;
				}
			} else {
				verStr = "" + (Integer.parseInt(verStr) + 1);
			}
			// 更新版本号
			BeanUtil.setProperty(entity, dataVersion.getField(), verStr);
		}
		return dialectFactory.update(sqlToyContext, entity, forceUpdateProps, false, null, null,
				this.getDataSource(dataSource));
	}

	/**
	 * @todo 修改对象,并通过指定级联的子对象做级联修改
	 * @param entity
	 * @param forceUpdateProps
	 * @param forceCascadeClasses      (强制需要修改的子对象,当子集合数据为null,则进行清空或置为无效处理,否则则忽视对存量数据的处理)
	 * @param subTableForceUpdateProps
	 * @return
	 */
	protected Long updateCascade(final Serializable entity, final String[] forceUpdateProps,
			final Class[] forceCascadeClasses, final HashMap<Class, String[]> subTableForceUpdateProps) {
		return dialectFactory.update(sqlToyContext, entity, forceUpdateProps, true, forceCascadeClasses,
				subTableForceUpdateProps, this.getDataSource(null));
	}

	/**
	 * @TODO 适用于库存台账、客户资金账等高并发强事务场景，一次数据库交互实现：1、锁查询；2、记录存在则修改；3、记录不存在则执行insert；4、返回修改或插入的记录信息，尽量不要使用identity、sequence主键
	 * @param <T>
	 * @param entity
	 * @param updateRowHandler
	 * @param uniqueProps
	 * @param dataSource
	 * @return
	 */
	public <T extends Serializable> T updateSaveFetch(final T entity, final UpdateRowHandler updateRowHandler,
			final String[] uniqueProps, final DataSource dataSource) {
		return (T) dialectFactory.updateSaveFetch(sqlToyContext, entity, updateRowHandler, uniqueProps,
				getDataSource(dataSource));
	}

	/**
	 * @todo 深度更新实体对象数据,根据对象的属性值全部更新对应表的字段数据,不涉及级联修改
	 * @param entity
	 * @return
	 */
	protected Long updateDeeply(final Serializable entity) {
		return this.updateDeeply(entity, null);
	}

	/**
	 * @todo <b>深度修改,即对象所有属性值都映射到数据库中,如果是null则数据库值被改为null</b>
	 * @param entity
	 * @param dataSource
	 * @return
	 */
	protected Long updateDeeply(final Serializable entity, final DataSource dataSource) {
		if (entity == null) {
			logger.warn("updateDeeply entity is null,please check!");
			return 0L;
		}
		EntityMeta entityMeta = getEntityMeta(entity.getClass());
		return this.update(entity, (entityMeta == null) ? null : entityMeta.getRejectIdFieldArray(),
				this.getDataSource(dataSource));
	}

	/**
	 * @todo 批量根据主键更新每条记录,通过forceUpdateProps设置强制要修改的属性
	 * @param <T>
	 * @param entities
	 * @param forceUpdateProps
	 * @return
	 */
	protected <T extends Serializable> Long updateAll(final List<T> entities, final String... forceUpdateProps) {
		return this.updateAll(entities, forceUpdateProps, null);
	}

	/**
	 * @todo <b>指定数据库,通过集合批量修改数据库记录</b>
	 * @param <T>
	 * @param entities
	 * @param forceUpdateProps
	 * @param dataSource
	 * @return
	 */
	protected <T extends Serializable> Long updateAll(final List<T> entities, final String[] forceUpdateProps,
			final DataSource dataSource) {
		return dialectFactory.updateAll(sqlToyContext, entities, sqlToyContext.getBatchSize(), null, forceUpdateProps,
				null, this.getDataSource(dataSource), null);
	}

	/**
	 * @todo 批量深度修改(参见updateDeeply,直接将集合VO中的字段值修改到数据库中,未null则置null)
	 * @param <T>
	 * @param entities
	 * @return
	 */
	protected <T extends Serializable> Long updateAllDeeply(final List<T> entities) {
		return updateAllDeeply(entities, null);
	}

	/**
	 * @todo 指定数据源进行批量深度修改(对象属性值为null则设置表对应的字段为null)
	 * @param <T>
	 * @param entities
	 * @param dataSource
	 * @return
	 */
	protected <T extends Serializable> Long updateAllDeeply(final List<T> entities, final DataSource dataSource) {
		if (entities == null || entities.isEmpty()) {
			logger.warn("updateAllDeeply List<POJO> is null,please check!");
			return 0L;
		}
		EntityMeta entityMeta = getEntityMeta(entities.get(0).getClass());
		return updateAll(entities, (entityMeta == null) ? null : entityMeta.getRejectIdFieldArray(), null);
	}

	protected Long saveOrUpdate(final Serializable entity, final String... forceUpdateProps) {
		return this.saveOrUpdate(entity, forceUpdateProps, null);
	}

	/**
	 * @todo 指定数据库,对对象进行保存或修改，forceUpdateProps:当修改操作时强制修改的属性
	 * @param entity
	 * @param forceUpdateProps
	 * @param dataSource
	 * @return
	 */
	protected Long saveOrUpdate(final Serializable entity, final String[] forceUpdateProps,
			final DataSource dataSource) {
		if (entity == null) {
			logger.warn("saveOrUpdate: entity is null,please check!");
			return 0L;
		}
		Class entityClass = entity.getClass();
		EntityMeta entityMeta = getEntityMeta(entityClass);
		validEntity(entityMeta, entityClass, false);
		// 存在数据版本控制，如果主键和版本数据都有值，表示做更新操作
		if (entityMeta.getDataVersion() != null && entityMeta.getIdArray() != null) {
			String[] props = new String[entityMeta.getIdArray().length + 1];
			System.arraycopy(entityMeta.getIdArray(), 0, props, 0, entityMeta.getIdArray().length);
			props[props.length - 1] = entityMeta.getDataVersion().getField();
			Object[] values = BeanUtil.reflectBeanToAry(entity, props);
			boolean isUpdate = true;
			for (int i = 0; i < props.length; i++) {
				if (values[i] == null) {
					isUpdate = false;
					break;
				}
			}
			// 全部有值，且版本字段值不为0表示是更新操作
			if (isUpdate && !"0".equals(values[props.length - 1].toString())) {
				return update(entity, forceUpdateProps, dataSource);
			}
		}
		return dialectFactory.saveOrUpdate(sqlToyContext, entity, forceUpdateProps, this.getDataSource(dataSource));
	}

	/**
	 * @todo 批量保存或修改，并指定强迫修改的字段属性
	 * @param <T>
	 * @param entities
	 * @param forceUpdateProps
	 * @return
	 */
	protected <T extends Serializable> Long saveOrUpdateAll(final List<T> entities, final String... forceUpdateProps) {
		return this.saveOrUpdateAll(entities, forceUpdateProps, null);
	}

	/**
	 * @todo <b>批量保存或修改</b>
	 * @param <T>
	 * @param entities
	 * @param forceUpdateProps
	 * @param dataSource
	 * @return
	 */
	protected <T extends Serializable> Long saveOrUpdateAll(final List<T> entities, final String[] forceUpdateProps,
			final DataSource dataSource) {
		return dialectFactory.saveOrUpdateAll(sqlToyContext, entities, sqlToyContext.getBatchSize(), forceUpdateProps,
				null, this.getDataSource(dataSource), null);
	}

	/**
	 * @todo 通过主键删除单条记录(会自动级联删除子表,根据数据库配置)
	 * @param entity
	 * @return
	 */
	protected Long delete(final Serializable entity) {
		return dialectFactory.delete(sqlToyContext, entity, this.getDataSource(null));
	}

	protected Long delete(final Serializable entity, final DataSource dataSource) {
		return dialectFactory.delete(sqlToyContext, entity, this.getDataSource(dataSource));
	}

	/**
	 * @TODO 提供单表简易查询进行删除操作(删除操作filters过滤无效)
	 * @param entityClass
	 * @param entityQuery
	 * @return
	 */
	protected Long deleteByQuery(Class entityClass, EntityQuery entityQuery) {
		EntityQueryExtend innerModel = entityQuery.getInnerModel();
		if (null == entityClass || null == entityQuery || StringUtil.isBlank(innerModel.where)
				|| StringUtil.isBlank(innerModel.values)) {
			throw new IllegalArgumentException("deleteByQuery entityClass、where、value 值不能为空!");
		}
		EntityMeta entityMeta = getEntityMeta(entityClass);
		validEntity(entityMeta, entityClass, false);
		// 做一个必要提示
		if (!innerModel.paramFilters.isEmpty()) {
			logger.warn("删除操作设置动态条件过滤是无效的,数据删除查询条件必须是精准的!");
		}
		String where = SqlUtil.convertFieldsToColumns(entityMeta, innerModel.where);
		String sql = "delete from ".concat(entityMeta.getSchemaTable(null, null)).concat(" where ").concat(where);
		QueryExecutor queryExecutor = null;
		// :named 模式
		if (SqlConfigParseUtils.hasNamedParam(where) && StringUtil.isBlank(innerModel.names)) {
			queryExecutor = new QueryExecutor(sql, (Serializable) innerModel.values[0]);
		} else {
			queryExecutor = new QueryExecutor(sql).names(innerModel.names).values(innerModel.values);
		}
		queryExecutor.getInnerModel().blankToNull = innerModel.blankToNull;
		if (innerModel.paramFilters != null && innerModel.paramFilters.size() > 0) {
			queryExecutor.getInnerModel().paramFilters.addAll(innerModel.paramFilters);
		}
		// 分库分表策略
		setEntitySharding(queryExecutor, entityMeta);
		// 为后续租户过滤提供判断依据(单表简单sql和对应的实体对象)
		queryExecutor.getInnerModel().entityClass = entityClass;
		SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(queryExecutor, SqlType.delete,
				getDialect(innerModel.dataSource));
		sqlToyConfig.setSqlType(SqlType.delete);
		return dialectFactory.executeSql(sqlToyContext, sqlToyConfig, queryExecutor, null, null,
				getDataSource(innerModel.dataSource));
	}

	protected <T extends Serializable> Long deleteAll(final List<T> entities) {
		return this.deleteAll(entities, null);
	}

	/**
	 * @todo <b>批量删除数据</b>
	 * @param <T>
	 * @param entities
	 * @param dataSource
	 * @return
	 */
	protected <T extends Serializable> Long deleteAll(final List<T> entities, final DataSource dataSource) {
		return dialectFactory.deleteAll(sqlToyContext, entities, sqlToyContext.getBatchSize(),
				this.getDataSource(dataSource), null);
	}

	/**
	 * @TODO 提供单一主键对象的批量快速删除调用方法
	 * @param entityClass
	 * @param ids
	 * @return
	 */
	protected Long deleteByIds(Class entityClass, Object... ids) {
		if (entityClass == null || ids == null || ids.length == 0 || (ids.length == 1 && ids[0] == null)) {
			throw new IllegalArgumentException("deleteByIds操作:entityClass参数、主键数据不能为空!");
		}
		EntityMeta entityMeta = getEntityMeta(entityClass);
		validEntity(entityMeta, entityClass, true);
		Object[] realIds;
		// 单个List,将List转为Array数组
		if (ids.length == 1 && ids[0] instanceof Collection) {
			realIds = ((Collection) ids[0]).toArray();
		} else {
			realIds = ids;
		}
		// 为什么统一转成对象集合?便于后面存在分库分表场景、ids超过1000条等场景
		List entities = BeanUtil.wrapEntities(sqlToyContext.getTypeHandler(), entityMeta, entityClass, realIds);
		return this.deleteAll(entities, null);
	}

	/**
	 * @todo 锁定记录查询，并对记录进行修改,最后将结果返回
	 * @param queryExecutor
	 * @param updateRowHandler
	 * @return
	 */
	protected List updateFetch(final QueryExecutor queryExecutor, final UpdateRowHandler updateRowHandler) {
		SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(queryExecutor, SqlType.search,
				getDialect(queryExecutor.getInnerModel().dataSource));
		return dialectFactory.updateFetch(sqlToyContext, queryExecutor, sqlToyConfig, updateRowHandler,
				this.getDataSource(queryExecutor.getInnerModel().dataSource, sqlToyConfig)).getRows();
	}

	/**
	 * @todo 获取对象信息(对应的表以及字段、主键策略等等的信息)
	 * @param entityClass
	 * @return
	 */
	protected EntityMeta getEntityMeta(Class entityClass) {
		return sqlToyContext.getEntityMeta(entityClass);
	}

	/**
	 * @todo 获取sqltoy配置的批处理每批记录量(默认为50)
	 * @return
	 */
	protected int getBatchSize() {
		return sqlToyContext.getBatchSize();
	}

	/**
	 * @todo 协助完成对对象集合的属性批量赋予相应数值
	 * @param names
	 * @return
	 */
	protected BeanWrapper wrapBeanProps(String... names) {
		return BeanWrapper.create().names(names);
	}

	/**
	 * @todo <b>手工提交数据库操作,只提供当前DataSource提交</b>
	 */
	protected void flush() {
		flush(null);
	}

	/**
	 * @todo <b>手工提交数据库操作,只提供当前DataSource提交</b>
	 * @param dataSource
	 */
	protected void flush(DataSource dataSource) {
		DataSourceUtils.processDataSource(sqlToyContext, this.getDataSource(dataSource),
				new DataSourceCallbackHandler() {
					@Override
					public void doConnection(Connection conn, Integer dbType, String dialect) throws Exception {
						if (!conn.isClosed()) {
							conn.commit();
						}
					}
				});
	}

	/**
	 * @todo 产生ID(可以指定增量范围，当一个表里面涉及多个业务主键时，sqltoy在配置层面只支持单个，但开发者可以调用此方法自行获取后赋值)
	 * @param signature 唯一标识符号
	 * @param increment 唯一标识符号，默认设置为1
	 * @return
	 */
	protected long generateBizId(String signature, int increment) {
		if (StringUtil.isBlank(signature)) {
			throw new IllegalArgumentException("signature 必须不能为空,请正确指定业务标志符号!");
		}
		if (distributeIdGenerator == null) {
			try {
				distributeIdGenerator = (DistributeIdGenerator) Class
						.forName(sqlToyContext.getDistributeIdGeneratorClass()).newInstance();
				distributeIdGenerator.initialize(sqlToyContext.getAppContext());
			} catch (Exception e) {
				e.printStackTrace();
				throw new DataAccessException("实例化分布式id产生器失败:" + e.getMessage());
			}
		}
		return distributeIdGenerator.generateId(signature, increment, null);
	}

	/**
	 * @todo 根据实体对象对应的POJO配置的业务主键策略,提取对象的属性值产生业务主键
	 * @param entity
	 * @return
	 */
	protected String generateBizId(Serializable entity) {
		EntityMeta entityMeta = this.getEntityMeta(entity.getClass());
		if (entityMeta == null || !entityMeta.isHasBizIdConfig()) {
			throw new IllegalArgumentException(
					StringUtil.fillArgs("对象:{},没有配置业务主键生成策略,请检查POJO 的业务主键配置!", entity.getClass().getName()));
		}
		String businessIdType = entityMeta.getColumnJavaType(entityMeta.getBusinessIdField());
		Integer[] relatedColumn = entityMeta.getBizIdRelatedColIndex();
		Object[] fullParamValues = BeanUtil.reflectBeanToAry(entity, entityMeta.getFieldsArray());
		// 提取关联属性的值
		Object[] relatedColValue = null;
		if (relatedColumn != null) {
			relatedColValue = new Object[relatedColumn.length];
			for (int meter = 0; meter < relatedColumn.length; meter++) {
				relatedColValue[meter] = fullParamValues[relatedColumn[meter]];
				if (relatedColValue[meter] == null) {
					throw new IllegalArgumentException("对象:" + entity.getClass().getName() + " 生成业务主键依赖的关联字段:"
							+ relatedColumn[meter] + " 值为null!");
				}
			}
		}
		IdGenerator idGenerator = (entityMeta.getBusinessIdGenerator() == null) ? entityMeta.getIdGenerator()
				: entityMeta.getBusinessIdGenerator();
		return idGenerator.getId(entityMeta.getTableName(), entityMeta.getBizIdSignature(),
				entityMeta.getBizIdRelatedColumns(), relatedColValue, new Date(), businessIdType,
				entityMeta.getBizIdLength(), entityMeta.getBizIdSequenceSize()).toString();
	}

	/**
	 * @todo 获取所有缓存的名称
	 * @return
	 */
	protected Set<String> getCacheNames() {
		return this.sqlToyContext.getTranslateManager().getCacheNames();
	}

	/**
	 * @todo 判断缓存是否存在
	 * @param cacheName
	 * @return
	 */
	protected boolean existCache(String cacheName) {
		return this.sqlToyContext.getTranslateManager().existCache(cacheName);
	}

	/**
	 * @TODO 将缓存数据以对象形式获取
	 * @param <T>
	 * @param cacheName
	 * @param cacheType  如是数据字典,则传入字典类型否则为null即可
	 * @param reusltType
	 * @return
	 */
	protected <T> List<T> getTranslateCache(String cacheName, String cacheType, Class<T> reusltType) {
		TranslateConfigModel translateConfig = sqlToyContext.getTranslateManager().getCacheConfig(cacheName);
		if (null == translateConfig) {
			throw new DataAccessException("缓存翻译中对应的缓存:" + cacheName + " 没有定义,请正确检查配置!");
		}
		HashMap<String, Object[]> cacheData = sqlToyContext.getTranslateManager().getCacheData(cacheName, cacheType);
		if (cacheData.isEmpty()) {
			return new ArrayList<T>();
		}
		if (null == reusltType || reusltType == Array.class) {
			return new ArrayList(cacheData.values());
		}
		String[] props = translateConfig.getProperties();
		// 注意直接sql定义的缓存，框架会自动获取label
		if (props == null || props.length == 0) {
			throw new DataAccessException("缓存翻译中的缓存:[" + cacheName + "]没有正确定义properties属性,无法映射到VO/POJO/Map对象!");
		}
		// 转map类型
		if (reusltType == Map.class || reusltType == HashMap.class || reusltType == LinkedHashMap.class
				|| reusltType == IgnoreKeyCaseMap.class || reusltType == IgnoreCaseLinkedMap.class) {
			List result = new ArrayList();
			Iterator<Object[]> iter = cacheData.values().iterator();
			Object[] row;
			int mapType = 1;
			if (reusltType == LinkedHashMap.class) {
				mapType = 2;
			} else if (reusltType == IgnoreKeyCaseMap.class) {
				mapType = 3;
			} else if (reusltType == IgnoreCaseLinkedMap.class) {
				mapType = 4;
			}
			while (iter.hasNext()) {
				Map map;
				if (mapType == 2) {
					map = new LinkedHashMap();
				} else if (mapType == 3) {
					map = new IgnoreKeyCaseMap();
				} else if (mapType == 4) {
					map = new IgnoreCaseLinkedMap();
				} else {
					map = new HashMap();
				}
				row = iter.next();
				for (int i = 0; i < props.length; i++) {
					map.put(props[i], row[i]);
				}
				result.add(map);
			}
			return result;
		}
		return (List<T>) BeanUtil.reflectListToBean(sqlToyContext.getTypeHandler(), cacheData.values(), props,
				reusltType);
	}

	/**
	 * @todo 获取缓存数据
	 * @param cacheName
	 * @param cacheType
	 * @return
	 */
	protected HashMap<String, Object[]> getTranslateCache(String cacheName, String cacheType) {
		return sqlToyContext.getTranslateManager().getCacheData(cacheName, cacheType);
	}

	/**
	 * @see cacheMatchKeys(CacheMatchFilter cacheMatchFilter, String...
	 *      matchRegexes)
	 * @param matchRegex
	 * @param cacheMatchFilter
	 * @return
	 */
	@Deprecated
	protected String[] cacheMatchKeys(String matchRegex, CacheMatchFilter cacheMatchFilter) {
		return cacheMatchKeys(cacheMatchFilter, matchRegex);
	}

	/**
	 * @TODO 通过缓存匹配名称并返回key集合(类似数据库中的like)便于后续进行精准匹配
	 * @param cacheMatchFilter 例如:
	 *                         CacheMatchFilter.create().cacheName("staffIdNameCache")
	 * @param cacheMatchFilter 如: 页面传过来的员工名称、客户名称等，反查对应的员工id和客户id
	 * @param matchRegexes
	 * @return
	 */
	protected String[] cacheMatchKeys(CacheMatchFilter cacheMatchFilter, String... matchRegexes) {
		if (cacheMatchFilter == null || StringUtil.isBlank(cacheMatchFilter.getCacheFilterArgs().cacheName)
				|| matchRegexes == null || matchRegexes.length == 0) {
			throw new IllegalArgumentException("缓存反向名称匹配key必须要提供cacheName和matchRegex值!");
		}
		CacheMatchExtend extendArgs = cacheMatchFilter.getCacheFilterArgs();
		// 获取缓存数据
		HashMap<String, Object[]> cacheDatas = getTranslateCache(extendArgs.cacheName, extendArgs.cacheType);
		if (cacheDatas == null || cacheDatas.isEmpty()) {
			logger.error("缓存cacheName={},cacheType={} 没有数据,cacheMatchKeys异常,请检查!", extendArgs.cacheName,
					extendArgs.cacheType);
			return new String[] {};
		}
		// 名称匹配在缓存的哪几列(正常1列，但部分场景要求:名称、别名 匹配等)
		int[] nameIndexes = extendArgs.matchIndexs;
		// 将传递匹配条件转小写
		List<String> matchLowAry = new ArrayList<String>();
		for (String str : matchRegexes) {
			matchLowAry.add(str.toLowerCase().trim());
		}
		// 缓存key值列
		int cacheKeyIndex = extendArgs.cacheKeyIndex;
		// 最大允许匹配数量,缓存匹配一般用于in (?,?)形式的查询,in 参数有数量限制
		int maxLimit = extendArgs.matchSize;
		// 匹配到的key集合
		Set<String> matchedKeys = new HashSet<String>();
		String keyCode;
		String matchStr;
		String[] matchWords;
		Object compareValue;
		// 是否优先判断相等
		boolean priorMatchEqual = extendArgs.priorMatchEqual;
		boolean hasFilter = (extendArgs.cacheFilter == null) ? false : true;
		boolean include = true;
		// 优先匹配名称相同,名称相同直接剔除掉对比参数不再进行后续匹配
		if (priorMatchEqual) {
			String keyLow;
			for (Object[] row : cacheDatas.values()) {
				keyCode = row[cacheKeyIndex].toString();
				include = true;
				if (hasFilter) {
					include = extendArgs.cacheFilter.doFilter(row);
				}
				if (include) {
					keyLow = keyCode.toLowerCase();
					skipLoop: for (int i = 0; i < matchLowAry.size(); i++) {
						matchStr = matchLowAry.get(i);
						// 模糊查询条件直接就跟key 相同
						if (matchStr.equals(keyLow)) {
							matchedKeys.add(keyCode);
							// 剔除
							matchLowAry.remove(i);
							break;
						}
						// 名称相同
						for (int index : nameIndexes) {
							compareValue = row[index];
							if (compareValue != null && compareValue.toString().toLowerCase().equals(matchStr)) {
								matchedKeys.add(keyCode);
								// 剔除
								matchLowAry.remove(i);
								break skipLoop;
							}
						}
					}
					// 完全匹配到相等、匹配量到最大量
					if (matchLowAry.isEmpty() || matchedKeys.size() >= maxLimit) {
						break;
					}
				}
			}
		}
		// 排除相等优先后，进行模糊like 匹配
		if (!matchLowAry.isEmpty() && matchedKeys.size() < maxLimit) {
			int likeArgSize = matchLowAry.size();
			// 将匹配参数切割成分词数组
			List<String[]> paramsMatchWords = new ArrayList<String[]>();
			for (int i = 0; i < likeArgSize; i++) {
				paramsMatchWords.add(matchLowAry.get(i).split("\\s+"));
			}
			for (Object[] row : cacheDatas.values()) {
				keyCode = row[cacheKeyIndex].toString();
				include = true;
				// 已经存在无需再比较
				if (matchedKeys.contains(keyCode)) {
					include = false;
				}
				// 对缓存进行过滤(比如过滤本人授权访问机构下面的员工或当期状态为生效的员工)
				if (hasFilter && include) {
					include = extendArgs.cacheFilter.doFilter(row);
				}
				if (include) {
					skipLoop: for (int i = 0; i < likeArgSize; i++) {
						// like 匹配
						matchWords = paramsMatchWords.get(i);
						for (int index : nameIndexes) {
							compareValue = row[index];
							if (compareValue != null
									&& StringUtil.like(compareValue.toString().toLowerCase(), matchWords)) {
								matchedKeys.add(keyCode);
								break skipLoop;
							}
						}
					}
					// 超出阈值跳出
					if (matchedKeys.size() >= maxLimit) {
						break;
					}
				}
			}
		}
		// 没有从缓存匹配到，返回自身
		if (matchedKeys.isEmpty() && extendArgs.unMatchedReturnSelf) {
			return matchRegexes;
		}
		String[] result = new String[matchedKeys.size()];
		matchedKeys.toArray(result);
		return result;
	}

	/**
	 * @todo 利用sqltoy的translate缓存，通过显式调用对集合数据的列进行翻译
	 * @param dataSet          要翻译的数据集合
	 * @param cacheName        缓存名称
	 * @param cacheType        缓存分类(如字典分类),非分类型的填null
	 * @param cacheNameIndex   缓存名称对应的列，默认为1(null也表示1)
	 * @param translateHandler 2个方法:getKey(Object row),setName(Object row,String
	 *                         name)
	 */
	protected void translate(Collection dataSet, String cacheName, String cacheType, Integer cacheNameIndex,
			TranslateHandler translateHandler) {
		// 数据以及合法性校验
		if (dataSet == null || dataSet.isEmpty()) {
			return;
		}
		if (cacheName == null) {
			throw new IllegalArgumentException("缓存名称不能为空!");
		}
		if (translateHandler == null) {
			throw new IllegalArgumentException("缓存翻译行取key和设置name的反调函数不能为null!");
		}
		// 获取缓存,框架会自动判断null并实现缓存数据的加载和更新检测
		final HashMap<String, Object[]> cache = getTranslateCache(cacheName, cacheType);
		if (cache == null || cache.isEmpty()) {
			return;
		}
		Iterator iter = dataSet.iterator();
		Object row;
		Object key;
		Object name;
		// 默认名称字段列为1
		int cacheIndex = (cacheNameIndex == null) ? 1 : cacheNameIndex.intValue();
		Object[] keyRow;
		// 循环获取行数据
		while (iter.hasNext()) {
			row = iter.next();
			if (row != null) {
				// 反调获取需要翻译的key
				key = translateHandler.getKey(row);
				if (key != null) {
					keyRow = cache.get(key.toString());
					// 从缓存中获取对应的名称
					name = (keyRow == null) ? null : keyRow[cacheIndex];
					// 反调设置行数据中具体列或属性翻译后的名称
					translateHandler.setName(row, (name == null) ? "" : name.toString());
				}
			}
		}
	}

	/**
	 * @TODO 提供针对单表简易快捷查询 EntityQuery.where("#[name like ?]#[and status in
	 *       (?)]").values(new Object[]{xxx,xxx})
	 * @param <T>
	 * @param entityClass
	 * @param entityQuery
	 * @return
	 */
	protected <T> List<T> findEntity(Class<T> entityClass, EntityQuery entityQuery) {
		return (List<T>) findEntity(entityClass, entityQuery, entityClass);
	}

	protected <T> List<T> findEntity(Class entityClass, EntityQuery entityQuery, Class<T> resultType) {
		if (null == entityClass) {
			throw new IllegalArgumentException("findEntityList entityClass值不能为空!");
		}
		return (List<T>) findEntityBase(entityClass, null, (entityQuery == null) ? EntityQuery.create() : entityQuery,
				resultType, false);
	}

	/**
	 * @TODO 提供针对单表简易快捷分页查询 EntityQuery.where("#[name like ?]#[and status in
	 *       (?)]").values(new Object[]{xxx,xxx})
	 * @param <T>
	 * @param page
	 * @param entityClass
	 * @param entityQuery
	 * @return
	 */
	protected <T> Page<T> findPageEntity(Page page, Class<T> entityClass, EntityQuery entityQuery) {
		return (Page<T>) findPageEntity(page, entityClass, entityQuery, entityClass);
	}

	protected <T> Page<T> findPageEntity(Page page, Class entityClass, EntityQuery entityQuery, Class<T> resultType) {
		if (null == entityClass || null == page) {
			throw new IllegalArgumentException("findPageEntity entityClass、page值不能为空!");
		}
		return (Page<T>) findEntityBase(entityClass, page, (entityQuery == null) ? EntityQuery.create() : entityQuery,
				resultType, false);
	}

	/**
	 * @TODO 提供findEntity的基础实现，供对外接口包装，额外开放了resultClass的自定义功能
	 * @param entityClass
	 * @param page        如分页查询则需指定，非分页则传null
	 * @param entityQuery
	 * @param resultClass 指定返回结果类型
	 * @param isCount
	 * @return
	 */
	private Object findEntityBase(Class entityClass, Page page, EntityQuery entityQuery, Class resultClass,
			boolean isCount) {
		EntityMeta entityMeta = getEntityMeta(entityClass);
		validEntity(entityMeta, entityClass, false);
		EntityQueryExtend innerModel = entityQuery.getInnerModel();
		String translateFields = "";
		// 将缓存翻译对应的查询补充到select column 上,形成select keyColumn as viewColumn 模式
		if (!innerModel.translates.isEmpty()) {
			Iterator<Translate> iter = innerModel.translates.values().iterator();
			String keyColumn;
			TranslateExtend extend;
			while (iter.hasNext()) {
				extend = iter.next().getExtend();
				// 将java模式的字段名称转化为数据库字段名称
				keyColumn = entityMeta.getColumnName(extend.keyColumn);
				if (keyColumn == null) {
					keyColumn = extend.keyColumn;
				}
				// 保留字处理
				keyColumn = ReservedWordsUtil.convertWord(keyColumn, null);
				translateFields = translateFields.concat(",").concat(keyColumn).concat(" as ").concat(extend.column);
			}
		}
		// 将notSelect构造成select，形成统一处理机制
		String[] selectFieldAry = null;
		Set<String> notSelect = innerModel.notSelectFields;
		if (notSelect != null) {
			List<String> selectFields = new ArrayList<String>();
			for (String field : entityMeta.getFieldsArray()) {
				if (!notSelect.contains(field.toLowerCase())) {
					selectFields.add(field);
				}
			}
			if (selectFields.size() > 0) {
				selectFieldAry = new String[selectFields.size()];
				selectFields.toArray(selectFieldAry);
			}
		} else {
			selectFieldAry = innerModel.fields;
		}
		// 指定的查询字段
		String fields = "";
		if (selectFieldAry != null && selectFieldAry.length > 0) {
			int index = 0;
			String colName;
			HashSet<String> cols = new HashSet<String>();
			boolean notAllPureField = false;
			for (String field : selectFieldAry) {
				// 去除重复字段
				if (!cols.contains(field)) {
					colName = entityMeta.getColumnName(field);
					// 非表字段对应pojo的属性名称
					if (colName == null) {
						colName = field;
						// 非字段名称
						if (!entityMeta.getColumnFieldMap().containsKey(colName.toLowerCase())) {
							notAllPureField = true;
						} else {
							// 保留字处理
							colName = ReservedWordsUtil.convertWord(colName, null);
						}
					} else {
						// 保留字处理
						colName = ReservedWordsUtil.convertWord(colName, null);
					}
					if (index > 0) {
						fields = fields.concat(",");
					}
					fields = fields.concat(colName);
					index++;
					cols.add(field);
				}
			}
			// select 字段中可能存在max(field)或field as xxx等非字段形式
			if (notAllPureField) {
				fields = SqlUtil.convertFieldsToColumns(entityMeta, fields);
			}
		} else {
			fields = entityMeta.getAllColumnNames();
		}
		String sql = "select ".concat((innerModel.distinct) ? " distinct " : "").concat(fields).concat(translateFields)
				.concat(" from ").concat(entityMeta.getSchemaTable(null, null));
		// where条件
		String where = "";
		// 动态组织where 后面的条件语句,此功能并不建议使用,where 一般需要指定明确条件
		if (StringUtil.isBlank(innerModel.where)) {
			if (innerModel.values != null && innerModel.values.length > 0) {
				where = SqlUtil.wrapWhere(entityMeta);
			}
		} else {
			where = SqlUtil.convertFieldsToColumns(entityMeta, innerModel.where);
		}
		if (StringUtil.isNotBlank(where)) {
			sql = sql.concat(" where ").concat(where);
		}
		// 分组和having
		if (StringUtil.isNotBlank(innerModel.groupBy)) {
			sql = sql.concat(" group by ").concat(SqlUtil.convertFieldsToColumns(entityMeta, innerModel.groupBy));
			if (StringUtil.isNotBlank(innerModel.having)) {
				sql = sql.concat(" having ").concat(SqlUtil.convertFieldsToColumns(entityMeta, innerModel.having));
			}
		}
		// 处理order by 排序
		if (!innerModel.orderBy.isEmpty()) {
			sql = sql.concat(" order by ");
			Iterator<Entry<String, String>> iter = innerModel.orderBy.entrySet().iterator();
			Entry<String, String> entry;
			String columnName;
			int index = 0;
			while (iter.hasNext()) {
				entry = iter.next();
				columnName = entityMeta.getColumnName(entry.getKey());
				if (columnName == null) {
					columnName = entry.getKey();
				}
				// 保留字处理
				columnName = ReservedWordsUtil.convertWord(columnName, null);
				if (index > 0) {
					sql = sql.concat(",");
				}
				// entry.getValue() is order way,like: desc or " "
				sql = sql.concat(columnName).concat(entry.getValue());
				index++;
			}
		}
		QueryExecutor queryExecutor;
		Class resultType = (resultClass == null) ? entityClass : resultClass;
		// :named 模式(named模式参数值必须存在)
		if (SqlConfigParseUtils.hasNamedParam(where) && StringUtil.isBlank(innerModel.names)) {
			queryExecutor = new QueryExecutor(sql,
					(innerModel.values == null || innerModel.values.length == 0) ? null
							: (Serializable) innerModel.values[0])
					.resultType(resultType).dataSource(getDataSource(innerModel.dataSource))
					.fetchSize(innerModel.fetchSize).maxRows(innerModel.maxRows);
		} else {
			queryExecutor = new QueryExecutor(sql).names(innerModel.names).values(innerModel.values)
					.resultType(resultType).dataSource(getDataSource(innerModel.dataSource))
					.fetchSize(innerModel.fetchSize).maxRows(innerModel.maxRows);
		}
		// 设置是否空白转null
		queryExecutor.getInnerModel().blankToNull = innerModel.blankToNull;
		// 为后续租户过滤提供判断依据(单表简单sql和对应的实体对象)
		queryExecutor.getInnerModel().entityClass = entityClass;
		// 设置额外的缓存翻译
		if (!innerModel.translates.isEmpty()) {
			queryExecutor.getInnerModel().translates.putAll(innerModel.translates);
		}
		// 设置额外的参数条件过滤
		if (!innerModel.paramFilters.isEmpty()) {
			queryExecutor.getInnerModel().paramFilters.addAll(innerModel.paramFilters);
		}
		// 设置安全脱敏
		if (!innerModel.secureMask.isEmpty()) {
			queryExecutor.getInnerModel().secureMask.putAll(innerModel.secureMask);
		}
		// 是否输出sql
		if (innerModel.showSql != null) {
			queryExecutor.showSql(innerModel.showSql);
		}
		// 设置分页优化
		queryExecutor.getInnerModel().pageOptimize = innerModel.pageOptimize;
		SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(queryExecutor, SqlType.search,
				getDialect(queryExecutor.getInnerModel().dataSource));
		// 加密字段，查询时解密
		if (entityMeta.getSecureColumns() != null) {
			sqlToyConfig.setDecryptColumns(entityMeta.getSecureColumns());
		}
		// 分库分表策略
		setEntitySharding(queryExecutor, entityMeta);
		if (innerModel.dbSharding != null) {
			queryExecutor.getInnerModel().dbSharding = innerModel.dbSharding;
		}
		if (innerModel.tableSharding != null) {
			ShardingStrategyConfig shardingConfig = innerModel.tableSharding;
			// 补充表名称
			shardingConfig.setTables(new String[] { entityMeta.getTableName() });
			List<ShardingStrategyConfig> tableShardings = new ArrayList<ShardingStrategyConfig>();
			tableShardings.add(shardingConfig);
			queryExecutor.getInnerModel().tableShardings = tableShardings;
		}
		DataSource realDataSource = getDataSource(queryExecutor.getInnerModel().dataSource, sqlToyConfig);
		Object result;
		// 取count数量
		if (isCount) {
			result = dialectFactory.getCountBySql(sqlToyContext, queryExecutor, sqlToyConfig, realDataSource);
			// 产品化场景，适配其他数据库验证查询(仅仅在设置了redoDataSources时生效)
			CrossDbAdapter.redoCountQuery(sqlToyContext, dialectFactory, queryExecutor);
			return result;
		}
		// 非分页
		if (page == null) {
			// 取top
			if (innerModel.pickType == 0) {
				result = dialectFactory
						.findTop(sqlToyContext, queryExecutor, sqlToyConfig, innerModel.pickSize, realDataSource)
						.getRows();
				// 产品化场景，适配其他数据库验证查询(仅仅在设置了redoDataSources时生效)
				CrossDbAdapter.redoTopQuery(sqlToyContext, dialectFactory, queryExecutor, innerModel.pickSize);
			} // 取随机记录
			else if (innerModel.pickType == 1) {
				result = dialectFactory.getRandomResult(sqlToyContext, queryExecutor, sqlToyConfig, innerModel.pickSize,
						realDataSource).getRows();
				// 产品化场景，适配其他数据库验证查询(仅仅在设置了redoDataSources时生效)
				CrossDbAdapter.redoRandomQuery(sqlToyContext, dialectFactory, queryExecutor, innerModel.pickSize);
			} else {
				result = dialectFactory
						.findByQuery(sqlToyContext, queryExecutor, sqlToyConfig, innerModel.lockMode, realDataSource)
						.getRows();
				// 产品化场景，适配其他数据库验证查询(仅仅在设置了redoDataSources时生效)
				CrossDbAdapter.redoQuery(sqlToyContext, dialectFactory, queryExecutor);
			}
			return result;
		} else {
			// 跳过总记录数形式的分页
			if (page.getSkipQueryCount()) {
				result = dialectFactory.findSkipTotalCountPage(sqlToyContext, queryExecutor, sqlToyConfig,
						page.getPageNo(), page.getPageSize(), realDataSource).getPageResult();
			} else {
				result = dialectFactory.findPage(sqlToyContext, queryExecutor, sqlToyConfig, page.getPageNo(),
						page.getPageSize(), page.getOverPageToFirst(), realDataSource).getPageResult();
			}
			// 产品化场景，适配其他数据库验证查询(仅仅在设置了redoDataSources时生效)
			CrossDbAdapter.redoPageQuery(sqlToyContext, dialectFactory, queryExecutor, page);
			return result;
		}
	}

	/**
	 * @TODO 针对单表对象查询进行更新操作(update和delete 操作filters过滤是无效的，必须是精准的条件参数)
	 * @param entityClass
	 * @param entityUpdate
	 * @update 2021-12-23 支持update table set field=field+1等计算模式
	 * @return
	 */
	protected Long updateByQuery(Class entityClass, EntityUpdate entityUpdate) {
		if (null == entityClass || null == entityUpdate || StringUtil.isBlank(entityUpdate.getInnerModel().where)
				|| StringUtil.isBlank(entityUpdate.getInnerModel().values)
				|| entityUpdate.getInnerModel().updateValues.isEmpty()) {
			throw new IllegalArgumentException("updateByQuery: entityClass、where条件、条件值value、变更值setValues不能为空!");
		}
		EntityMeta entityMeta = getEntityMeta(entityClass);
		validEntity(entityMeta, entityClass, false);
		EntityUpdateExtend innerModel = entityUpdate.getInnerModel();
		// 过滤无效set(field),即校验field不是数据库实际字段(@Column对应的属性)
		IgnoreCaseLinkedMap<String, Object> realUpdateValues = wrapRealUpdateValues(innerModel.updateValues, entityMeta,
				entityClass, innerModel.skipNotExistColumn);
		boolean isName = SqlConfigParseUtils.hasNamedParam(innerModel.where);
		Object[] values = innerModel.values;
		String[] paramNames = null;
		String where = innerModel.where;
		int valueSize = (values == null) ? 0 : values.length;
		// 重新通过对象反射获取参数条件的值
		if (isName) {
			// 校验必须是dto、map形式传参数
			if (values.length > 1) {
				throw new IllegalArgumentException("updateByQuery: where条件采用:paramName形式传参,values只能传递单个VO或Map对象!");
			}
			paramNames = SqlConfigParseUtils.getSqlParamsName(where, false);
			values = BeanUtil.reflectBeanToAry(values[0], paramNames);
			// 重新设置值数组的长度
			valueSize = values.length;
		} else {
			int paramCnt = DialectUtils.getParamsCount(where);
			if (paramCnt == 1 && StringUtil.matches(where, SqlConfigParseUtils.IN_PATTERN) && valueSize > 1) {
				values = new Object[] { values };
				valueSize = 1;
			}
			if (paramCnt != valueSize) {
				throw new IllegalArgumentException("updateByQuery: where语句中的?数量跟对应values 数组长度不一致,请检查!");
			}
		}
		// 处理where 中写的java 字段名称为数据库表字段名称
		where = SqlUtil.convertFieldsToColumns(entityMeta, where);
		StringBuilder sql = new StringBuilder();
		sql.append("update ").append(entityMeta.getSchemaTable(null, null)).append(" set ");
		// 对统一更新字段做处理
		IUnifyFieldsHandler unifyHandler = getSqlToyContext().getUnifyFieldsHandler();
		if (unifyHandler != null) {
			Map<String, Object> updateFields = UnifyUpdateFieldsController.useUnifyFields()
					? unifyHandler.updateUnifyFields()
					: null;
			if (updateFields != null && !updateFields.isEmpty()) {
				Iterator<Entry<String, Object>> updateIter = updateFields.entrySet().iterator();
				String columnName;
				Entry<String, Object> entry;
				while (updateIter.hasNext()) {
					entry = updateIter.next();
					columnName = entityMeta.getColumnName(entry.getKey());
					// 是数据库表的字段
					if (columnName != null) {
						// 是否已经主动update
						if (realUpdateValues.containsKey(entry.getKey()) || realUpdateValues.containsKey(columnName)) {
							// 存在强制更新
							if (unifyHandler.forceUpdateFields() != null
									&& unifyHandler.forceUpdateFields().contains(entry.getKey())) {
								// 覆盖主动设置的值
								// 以表字段名称模式设置的值
								if (realUpdateValues.containsKey(columnName)) {
									realUpdateValues.put(columnName, entry.getValue());
								} else {
									// 属性名称设置的值
									realUpdateValues.put(entry.getKey(), entry.getValue());
								}
							}
						} else {
							realUpdateValues.put(entry.getKey(), entry.getValue());
						}
					}
				}
			}
		}

		Object[] realValues = new Object[realUpdateValues.size() + valueSize];
		if (valueSize > 0) {
			System.arraycopy(values, 0, realValues, realUpdateValues.size(), valueSize);
		}
		String[] realNames = null;
		if (isName) {
			realNames = new String[realValues.length];
			System.arraycopy(paramNames, 0, realNames, realUpdateValues.size(), valueSize);
		}
		// 强制修改的日期时间字段，且以数据库时间为准
		IgnoreCaseSet forceUpdateSqlFields = new IgnoreCaseSet();
		if (UnifyUpdateFieldsController.useUnifyFields() && unifyHandler.forceUpdateFields() != null
				&& unifyHandler.updateSqlTimeFields() != null) {
			unifyHandler.forceUpdateFields().forEach((sqlUpdateField) -> {
				if (unifyHandler.updateSqlTimeFields().contains(sqlUpdateField)) {
					forceUpdateSqlFields.add(sqlUpdateField);
				}
			});
		}
		int index = 0;
		String columnName;
		Iterator<Entry<String, Object>> iter = realUpdateValues.entrySet().iterator();
		String fieldSetValue;
		// 设置一个扩展标志，避免set field=field+? 场景构造成field=field+:fieldExtParam跟where
		// field=:field名称冲突
		final String extSign = "ExtParam";
		DataSource dsDataSource = getDataSource(innerModel.dataSource, null);
		Integer dbType = DataSourceUtils.getDBType(sqlToyContext, dsDataSource);
		String nvlFun = DataSourceUtils.getNvlFunction(dbType);
		String currentTime;
		String[] fields;
		FieldMeta fieldMeta;
		String fieldName;
		Entry<String, Object> entry;
		while (iter.hasNext()) {
			entry = iter.next();
			// 考虑 field=filed+? 模式，分割成2部分
			fields = entry.getKey().split("=");
			fieldMeta = entityMeta.getFieldMeta(fields[0].trim());
			// entry.getKey() 直接是数据库字段名称
			if (fieldMeta == null) {
				// 先通过数据字段名称获得类的属性名称再获取fieldMeta
				fieldName = entityMeta.getColumnFieldMap().get(fields[0].trim().toLowerCase());
				fieldMeta = entityMeta.getFieldMeta(fieldName);
			}
			// 保留字处理
			columnName = ReservedWordsUtil.convertWord(fieldMeta.getColumnName(), dbType);
			if (isName) {
				if (fields.length > 1) {
					if (fields[1].contains("?")) {
						// 拼接扩展字符，避免where后面有同样的参数名称
						realNames[index] = fieldMeta.getFieldName().concat(extSign);
					} else {
						realNames[index] = SqlConfigParseUtils.getSqlParamsName(fields[1], true)[0];
					}
				} else {
					realNames[index] = fieldMeta.getFieldName();
				}
			}
			if (index > 0) {
				sql.append(",");
			}
			currentTime = SqlUtil.getDBTime(dbType, fieldMeta, forceUpdateSqlFields);
			// 将参数值设置为null，update语句构造成update table set field=nvl(?,current_timestamp) 形式
			if (currentTime != null) {
				// 原设定的参数设置为null，
				realValues[index] = null;
				// 剔除掉已经处理的字段
				forceUpdateSqlFields.remove(fieldMeta.getFieldName());
				// field=nvl(?,current_timestamp)
				sql.append(columnName).append("=").append(nvlFun).append("(")
						.append(isName ? (":" + fieldMeta.getFieldName()) : "?").append(",").append(currentTime)
						.append(")");
			} else {
				realValues[index] = entry.getValue();
				if (fields.length == 1) {
					sql.append(columnName).append("=").append(isName ? (":" + fieldMeta.getFieldName()) : "?");
				} else {
					// field=filed+? 类似模式
					fieldSetValue = fields[1];
					sql.append(columnName).append("=");
					if (isName && fieldSetValue.contains("?")) {
						fieldSetValue = fieldSetValue.replace("?", ":" + fieldMeta.getFieldName().concat(extSign));
					}
					fieldSetValue = SqlUtil.convertFieldsToColumns(entityMeta, fieldSetValue);
					sql.append(fieldSetValue);
				}
			}
			index++;
		}
		// 针对独立的sql 时间字段进行赋值更新，update table set field=current_timestamp
		forceUpdateSqlFields.forEach((sqlField) -> {
			FieldMeta sqlFieldMeta = entityMeta.getFieldMeta(sqlField);
			String dateStr = SqlUtil.getDBTime(dbType, sqlFieldMeta, forceUpdateSqlFields);
			if (dateStr != null) {
				sql.append(",");
				sql.append(ReservedWordsUtil.convertWord(sqlFieldMeta.getColumnName(), null)).append("=")
						.append(dateStr);
			}
		});
		sql.append(" where ").append(where);
		String sqlStr = sql.toString();
		QueryExecutor queryExecutor = new QueryExecutor(sqlStr).names(realNames).values(realValues);
		queryExecutor.getInnerModel().blankToNull = (innerModel.blankToNull == null)
				? SqlToyConstants.executeSqlBlankToNull
				: innerModel.blankToNull;
		queryExecutor.getInnerModel().showSql = innerModel.showSql;
		// 为后续租户过滤提供判断依据(单表简单sql和对应的实体对象)
		queryExecutor.getInnerModel().entityClass = entityClass;
		setEntitySharding(queryExecutor, entityMeta);
		SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(queryExecutor, SqlType.update,
				getDialect(dsDataSource));
		sqlToyConfig.setSqlType(SqlType.update);
		return dialectFactory.executeSql(sqlToyContext, sqlToyConfig, queryExecutor, null, null, dsDataSource);
	}

	/**
	 * @TODO 过滤掉无效set的属性
	 * @param updateValues
	 * @param entityMeta
	 * @param entityClass
	 * @param skipNotExistColumn
	 * @return
	 */
	private IgnoreCaseLinkedMap wrapRealUpdateValues(IgnoreCaseLinkedMap updateValues, EntityMeta entityMeta,
			Class entityClass, boolean skipNotExistColumn) {
		IgnoreCaseLinkedMap<String, Object> realUpdateValues = new IgnoreCaseLinkedMap<String, Object>();
		// 先过滤不合法数据
		Iterator<Entry<String, Object>> iter = updateValues.entrySet().iterator();
		String[] fields;
		FieldMeta fieldMeta;
		String fieldName;
		Entry<String, Object> entry;
		String skipFields = new String();
		while (iter.hasNext()) {
			entry = iter.next();
			fields = entry.getKey().split("=");
			fieldMeta = entityMeta.getFieldMeta(fields[0].trim());
			if (fieldMeta == null) {
				// 先通过数据字段名称获得类的属性名称再获取fieldMeta
				fieldName = entityMeta.getColumnFieldMap().get(fields[0].trim().toLowerCase());
				if (fieldName != null) {
					fieldMeta = entityMeta.getFieldMeta(fieldName);
				}
			}
			if (fieldMeta == null) {
				if (!skipNotExistColumn) {
					throw new IllegalArgumentException("updateByQuery: 实体对象:" + entityClass.getName() + "属性:"
							+ fields[0] + " 不是数据库表字段(@Column注解的表示数据库字段),请检查代码!");
				} else {
					if (skipFields.length() > 0) {
						skipFields = skipFields.concat(",").concat(fields[0]);
					} else {
						skipFields = fields[0];
					}
				}
			} else {
				realUpdateValues.put(entry.getKey(), entry.getValue());
			}
		}
		if (realUpdateValues.isEmpty()) {
			throw new IllegalArgumentException("updateByQuery: 实体对象:" + entityClass.getName()
					+ ",set(property,value)过程中，排除无效数据库字段属性:" + skipFields + "后，无有效更新属性(@Column注解的为数据库字段),请检查代码!");
		}
		return realUpdateValues;
	}

	/**
	 * @TODO 实现POJO和DTO(VO) 之间类型的相互转换和数据复制
	 * @param <T>
	 * @param source
	 * @param resultType
	 * @param ignoreProperties
	 * @return
	 */
	protected <T extends Serializable> T convertType(Serializable source, Class<T> resultType,
			String... ignoreProperties) {
		return MapperUtils.map(source, resultType, ignoreProperties);
	}

	/**
	 * @TODO 实现POJO和DTO(VO) 集合之间类型的相互转换和数据复制
	 * @param <T>
	 * @param sourceList
	 * @param resultType
	 * @param ignoreProperties
	 * @return
	 */
	protected <T extends Serializable> List<T> convertType(List sourceList, Class<T> resultType,
			String... ignoreProperties) {
		return MapperUtils.mapList(sourceList, resultType, ignoreProperties);
	}

	protected <T extends Serializable> Page<T> convertType(Page sourcePage, Class<T> resultType,
			String... ignoreProperties) {
		return MapperUtils.map(sourcePage, resultType, ignoreProperties);
	}

	// parallQuery 面向查询(不要用于事务操作过程中),sqltoy提供强大的方法，但是否恰当使用需要使用者做合理的判断
	/**
	 * -- 避免开发者将全部功能用一个超级sql完成，提供拆解执行的同时确保执行效率，达到了效率和可维护的平衡
	 * 
	 * @TODO 并行查询并返回一维List，有几个查询List中就包含几个结果对象，paramNames和paramValues是全部sql的条件参数的合集
	 * @param <T>
	 * @param parallQueryList
	 * @param paramNames
	 * @param paramValues
	 * @return
	 */
	protected <T> List<QueryResult<T>> parallQuery(List<ParallQuery> parallQueryList, String[] paramNames,
			Object[] paramValues) {
		return parallQuery(parallQueryList, paramNames, paramValues, null);
	}

	protected <T> List<QueryResult<T>> parallQuery(List<ParallQuery> parallQueryList, Map<String, Object> paramsMap,
			ParallelConfig parallelConfig) {
		return parallQuery(parallQueryList, null, new Object[] { new IgnoreKeyCaseMap(paramsMap) }, parallelConfig);
	}

	/**
	 * @TODO 获取表的列信息
	 * @param catalog
	 * @param schema
	 * @param tableName
	 * @param dataSource
	 * @return
	 */
	protected List<ColumnMeta> getTableColumns(final String catalog, final String schema, String tableName,
			DataSource dataSource) {
		return dialectFactory.getTableColumns(sqlToyContext, catalog, schema, tableName, getDataSource(dataSource));
	}

	/**
	 * @TODO 获取数据库的表信息
	 * @param catalog
	 * @param schema
	 * @param tableName
	 * @param dataSource
	 * @return
	 */
	protected List<TableMeta> getTables(final String catalog, final String schema, String tableName,
			DataSource dataSource) {
		return dialectFactory.getTables(sqlToyContext, catalog, schema, tableName, getDataSource(dataSource));
	}

	/**
	 * @TODO 并行查询并返回一维List，有几个查询List中就包含几个结果对象，paramNames和paramValues是全部sql的条件参数的合集
	 * @param parallQueryList
	 * @param paramNames
	 * @param paramValues
	 * @param parallelConfig
	 * @return
	 */
	protected <T> List<QueryResult<T>> parallQuery(List<ParallQuery> parallQueryList, String[] paramNames,
			Object[] paramValues, ParallelConfig parallelConfig) {
		if (parallQueryList == null || parallQueryList.isEmpty()) {
			return null;
		}
		ParallelConfig parallConfig = parallelConfig;
		if (parallConfig == null) {
			parallConfig = new ParallelConfig();
		}
		// 并行线程数量(默认最大十个)
		if (parallConfig.getMaxThreads() == null) {
			parallConfig.maxThreads(10);
		}
		int thread = parallConfig.getMaxThreads();
		if (parallQueryList.size() < thread) {
			thread = parallQueryList.size();
		}
		List<QueryResult<T>> results = new ArrayList<QueryResult<T>>();
		ExecutorService pool = null;
		try {
			pool = Executors.newFixedThreadPool(thread);
			List<Future<ParallQueryResult>> futureResult = new ArrayList<Future<ParallQueryResult>>();
			SqlToyConfig sqlToyConfig;
			Future<ParallQueryResult> future;
			for (ParallQuery query : parallQueryList) {
				sqlToyConfig = sqlToyContext.getSqlToyConfig(
						new QueryExecutor(query.getExtend().sql).resultType(query.getExtend().resultType),
						SqlType.search, getDialect(query.getExtend().dataSource));
				// 自定义条件参数
				if (query.getExtend().selfCondition) {
					future = pool.submit(new ParallQueryExecutor(sqlToyContext, dialectFactory, sqlToyConfig, query,
							query.getExtend().names, query.getExtend().values,
							getDataSource(query.getExtend().dataSource, sqlToyConfig)));
				} else {
					future = pool.submit(new ParallQueryExecutor(sqlToyContext, dialectFactory, sqlToyConfig, query,
							paramNames, paramValues, getDataSource(query.getExtend().dataSource, sqlToyConfig)));
				}
				futureResult.add(future);
			}
			pool.shutdown();
			// 设置最大等待时长
			if (parallConfig.getMaxWaitSeconds() != null) {
				pool.awaitTermination(parallConfig.getMaxWaitSeconds(), TimeUnit.SECONDS);
			} else {
				pool.awaitTermination(SqlToyConstants.PARALLEL_MAXWAIT_SECONDS, TimeUnit.SECONDS);
			}
			ParallQueryResult item;
			int index = 0;
			for (Future<ParallQueryResult> result : futureResult) {
				index++;
				item = result.get();
				// 存在执行异常则整体抛出
				if (item != null && !item.isSuccess()) {
					throw new DataAccessException("第:{} 个sql执行异常:{}!", index, item.getMessage());
				}
				results.add(item.getResult());
			}
		} catch (Exception e) {
			e.printStackTrace();
			throw new DataAccessException("并行查询执行错误:" + e.getMessage(), e);
		} finally {
			if (pool != null) {
				pool.shutdownNow();
			}
		}
		return results;
	}

	/**
	 * @TODO 获取当前数据库方言的名称
	 * @param dataSource
	 * @return
	 */
	protected String getDialect(DataSource dataSource) {
		if (StringUtil.isNotBlank(sqlToyContext.getDialect())) {
			return sqlToyContext.getDialect();
		}
		return DataSourceUtils.getDialect(sqlToyContext, getDataSource(dataSource));
	}

	private void setEntitySharding(QueryExecutor queryExecutor, EntityMeta entityMeta) {
		// 分库分表策略
		if (entityMeta.getShardingConfig() != null) {
			// db sharding
			if (entityMeta.getShardingConfig().getShardingDBStrategy() != null) {
				queryExecutor.getInnerModel().dbSharding = entityMeta.getShardingConfig().getShardingDBStrategy();
			}
			// table sharding
			if (entityMeta.getShardingConfig().getShardingTableStrategy() != null) {
				List<ShardingStrategyConfig> shardingConfig = new ArrayList<ShardingStrategyConfig>();
				shardingConfig.add(entityMeta.getShardingConfig().getShardingTableStrategy());
				queryExecutor.getInnerModel().tableShardings = shardingConfig;
			}
		}
	}

	/**
	 * @TODO 验证实体类操作，对应实体对象是否合法
	 * @param entityMeta
	 * @param entityClass
	 * @param validatePK
	 */
	private void validEntity(EntityMeta entityMeta, Class entityClass, boolean validatePK) {
		if (entityMeta == null) {
			throw new IllegalArgumentException("Class=[" + entityClass.getName() + "]没有@Entity标记为POJO实体对象!");
		}
		if (entityMeta.getFieldsArray() == null || entityMeta.getFieldsArray().length == 0) {
			throw new IllegalArgumentException("Class=[" + entityClass.getName() + "]没有@Column定义具体的字段信息!");
		}
		if (validatePK && (entityMeta.getIdArray() == null || entityMeta.getIdArray().length == 0)) {
			throw new IllegalArgumentException("Class=[" + entityClass.getName() + "]没有@Id定义主键字段!");
		}
	}
}
