package org.sagacity.sqltoy.support;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
import org.sagacity.sqltoy.SqlToyThreadDataHolder;
import org.sagacity.sqltoy.callback.DataSourceCallbackHandler;
import org.sagacity.sqltoy.callback.EntityUpdateCallback;
import org.sagacity.sqltoy.callback.StreamResultHandler;
import org.sagacity.sqltoy.callback.UpdateRowCallback;
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
import org.sagacity.sqltoy.dialect.executor.ParallelQueryExecutor;
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
import org.sagacity.sqltoy.model.JdbcTypes;
import org.sagacity.sqltoy.model.LockMode;
import org.sagacity.sqltoy.model.MapKit;
import org.sagacity.sqltoy.model.Page;
import org.sagacity.sqltoy.model.ParallQuery;
import org.sagacity.sqltoy.model.ParallelConfig;
import org.sagacity.sqltoy.model.ParallelQueryResult;
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
import org.sagacity.sqltoy.plugins.datasource.DataSourceSelector;
import org.sagacity.sqltoy.plugins.id.IdGenerator;
import org.sagacity.sqltoy.translate.DynamicCacheFetch;
import org.sagacity.sqltoy.translate.TranslateHandler;
import org.sagacity.sqltoy.translate.model.TranslateConfigModel;
import org.sagacity.sqltoy.utils.BeanUtil;
import org.sagacity.sqltoy.utils.BeanWrapper;
import org.sagacity.sqltoy.utils.DataSourceUtils;
import org.sagacity.sqltoy.utils.DateUtil;
import org.sagacity.sqltoy.utils.IdUtil;
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
 * @version v4.0,Date:2012-06-01
 * @modify Date:2012-08-08 增强对象级联查询、删除、保存操作机制,不支持2层以上级联
 * @modify Date:2012-08-23 新增loadAll(List entities) 方法，可以批量通过主键取回详细信息
 * @modify Date:2014-12-17 1、增加sharding功能,改进saveOrUpdate功能，2、采用merge
 *         into策略;3、优化查询 条件和查询结果，变为一个对象，返回结果支持json输出}
 * @modify Date:2016-03-07 优化存储过程调用,提供常用的执行方式,剔除过往复杂的实现逻辑和不必要的兼容性,让调用过程更加可读
 * @modify Date:2016-11-25
 *         {增加了分页优化功能,缓存相同查询条件的总记录数,在一定周期情况下无需再查询总记录数,从而提升分页查询的整体效率 }
 * @modify Date:2017-07-13 增加saveAllNotExist功能,批量保存数据时忽视已经存在的,避免重复性数据主键冲突
 * @modify Date:2017-11-01 增加对象操作分库分表功能实现,精简和优化代码
 * @modify Date:2019-03-01 增加通过缓存获取Key然后作为查询条件cache-arg 功能，从而避免二次查询或like检索
 * @modify Date:2019-06-25 将异常统一转化成RuntimeException,不在方法上显式的抛异常
 * @modify Date:2020-04-05 分页Page模型中设置skipQueryCount=true跳过查总记录,默认false
 * @modify Date:2020-08-25 增加并行查询功能,为极端场景下提升查询效率,为开发者拆解复杂sql做多次查询影响性能提供了解决之道
 * @modify Date:2020-10-20 findByQuery 增加lockMode,便于查询并锁定记录
 * @modify Date:2021-06-25
 *         {剔除linkDaoSupport、BaseDaoSupport,将link功能放入SqlToyDaoSupport}
 * @modify Date:2021-12-23 优化updateByQuery支持set field=field+1依据字段值进行计算的模式
 * @modify Date:2023-08-06 增加executeMoreResultStore存储过程支持多结果返回
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
	private volatile DistributeIdGenerator distributeIdGenerator = null;

	/**
	 * 延迟初始化分布式id产生器(double-checked locking保证线程安全)
	 * 
	 * @return 分布式id产生器实例(全局单例,首次调用时反射实例化并初始化)
	 */
	private DistributeIdGenerator getDistributeIdGenerator() {
		if (distributeIdGenerator == null) {
			synchronized (this) {
				if (distributeIdGenerator == null) {
					try {
						distributeIdGenerator = (DistributeIdGenerator) Class
								.forName(sqlToyContext.getDistributeIdGeneratorClass()).getDeclaredConstructor()
								.newInstance();
						distributeIdGenerator.initialize(sqlToyContext.getAppContext());
					} catch (Exception e) {
						logger.error("getDistributeIdGenerator method execution failed", e);
						throw new DataAccessException(
								"failed to instantiate the distributed id generator:" + e.getMessage());
					}
				}
			}
		}
		return distributeIdGenerator;
	}

	/**
	 * 各种数据库方言实现
	 */
	private DialectFactory dialectFactory = DialectFactory.getInstance();

	public void setDataSource(DataSource dataSource) {
		this.dataSource = dataSource;
	}

	/**
	 * 获取数据源,如果参数dataSource为null则返回默认的dataSource
	 * 
	 * @param pointDataSource 调用时显式指定的数据源,可为null
	 * @return 实际生效的数据源,pointDataSource为null时返回dao默认的dataSource
	 */
	protected DataSource getDataSource(DataSource pointDataSource) {
		return getDataSource(pointDataSource, null);
	}

	/**
	 * 获取sql对应的dataSource
	 * 
	 * @param pointDataSource 调用时显式指定的数据源,优先级最高,可为null
	 * @param sqltoyConfig    sqlId对应的配置模型,通过其获取sql上配置的datasource(为null时不考虑sql级配置)
	 * @return 实际生效的数据源,按 显式指定 > sql级配置 > dao注入 > 全局默认 的优先级选取
	 */
	private DataSource getDataSource(DataSource pointDataSource, SqlToyConfig sqltoyConfig) {
		// xml中定义的sql配置了datasource
		String sqlDataSource = (null == sqltoyConfig) ? null : sqltoyConfig.getDataSource();
		// 提供一个扩展，让开发者在特殊场景下可以自行定义dataSourceSelector实现数据源的选择和获取
		DataSourceSelector dataSourceSelector = sqlToyContext.getDataSourceSelector();
		return dataSourceSelector.getDataSource(sqlToyContext.getAppContext(), pointDataSource, sqlDataSource,
				dataSource, sqlToyContext.getDefaultDataSource());
	}

	/**
	 * 对象加载操作集合
	 * 
	 * @return Load链式操作对象,用于指定实体对象并执行加载
	 */
	protected Load load() {
		return new Load(sqlToyContext, getDataSource(dataSource));
	}

	/**
	 * 删除操作集合
	 * 
	 * @return Delete链式操作对象,用于指定实体对象并执行删除
	 */
	protected Delete delete() {
		return new Delete(sqlToyContext, getDataSource(dataSource));
	}

	/**
	 * 修改操作集合
	 * 
	 * @return Update链式操作对象,用于指定实体对象并执行修改
	 */
	protected Update update() {
		return new Update(sqlToyContext, getDataSource(dataSource));
	}

	/**
	 * 保存操作集合
	 * 
	 * @return Save链式操作对象,用于指定实体对象并执行保存
	 */
	protected Save save() {
		return new Save(sqlToyContext, getDataSource(dataSource));
	}

	/**
	 * 查询操作集合
	 * 
	 * @return Query链式操作对象,用于执行基于实体的单表快捷查询
	 */
	protected Query query() {
		return new Query(sqlToyContext, getDataSource(dataSource));
	}

	/**
	 * 存储过程操作集合
	 * 
	 * @return Store链式操作对象,用于执行存储过程调用
	 */
	protected Store store() {
		return new Store(sqlToyContext, getDataSource(dataSource));
	}

	/**
	 * 唯一性验证操作集合
	 * 
	 * @return Unique链式操作对象,用于执行数据唯一性校验
	 */
	protected Unique unique() {
		return new Unique(sqlToyContext, getDataSource(dataSource));
	}

	/**
	 * 树形表结构封装操作集合
	 * 
	 * @return TreeTable链式操作对象,用于构造和操作树形表
	 */
	protected TreeTable treeTable() {
		return new TreeTable(sqlToyContext, getDataSource(dataSource));
	}

	/**
	 * sql语句直接执行修改数据库操作集合
	 * 
	 * @return Execute链式操作对象,用于直接执行sql完成修改、删除等操作
	 */
	protected Execute execute() {
		return new Execute(sqlToyContext, getDataSource(dataSource));
	}

	/**
	 * 批量执行操作集合
	 * 
	 * @return Batch链式操作对象,用于批量执行sql或存储过程
	 */
	protected Batch batch() {
		return new Batch(sqlToyContext, getDataSource(dataSource));
	}

	/**
	 * 提供一个获取数据库表信息和操作表信息的TableApi集合
	 * 
	 * @return TableApi链式操作对象,用于查询表结构、复制表等表级操作
	 */
	protected TableApi tableApi() {
		return new TableApi(sqlToyContext, getDataSource(dataSource));
	}

	/**
	 * 提供基于ES的查询(仅针对查询部分)
	 * 
	 * @return Elastic链式操作对象,用于执行elasticsearch查询
	 */
	protected Elastic elastic() {
		return new Elastic(sqlToyContext, getDataSource(dataSource));
	}

	/**
	 * 提供基于mongo的查询(仅针对查询部分)
	 * 
	 * @return Mongo链式操作对象,用于执行mongo查询
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
	 * 获取sqlId 在sqltoy中的配置模型
	 * 
	 * @param sqlKey  sql语句或xml中定义的sqlId
	 * @param sqlType sql类型(查询、修改、删除等),为null时默认按search处理
	 * @return sqlKey对应的SqlToyConfig配置模型,sqlId不存在时抛出DataAccessException
	 */
	protected SqlToyConfig getSqlToyConfig(final String sqlKey, final SqlType sqlType) {
		return sqlToyContext.getSqlToyConfig(sqlKey, (sqlType == null) ? SqlType.search : sqlType, getDialect(null),
				null);
	}

	/**
	 * 判断数据库中数据是否唯一，true 表示唯一(可以插入)，false表示不唯一(数据库已经存在该数据)，用法
	 * isUnique(dictDetailVO,new String[]{"dictTypeCode","dictName"})，将会根据给定的2个参数
	 * 通过VO取到相应的值，作为组合条件到dictDetailVO对应的表中查询记录是否存在
	 * 
	 * @param entity      待校验唯一性的实体对象,以对象属性值作为判断条件值
	 * @param paramsNamed 对象属性名称(不是数据库表字段名称)
	 * @return true表示唯一(可以插入),false表示数据库中已存在相同数据
	 */
	protected boolean isUnique(final Serializable entity, final String... paramsNamed) {
		return isUnique(new UniqueExecutor(entity, paramsNamed));
	}

	/*
	 * @see isUnique(final Serializable entity, final String[] paramsNamed)
	 */
	protected boolean isUnique(final UniqueExecutor uniqueExecutor) {
		return dialectFactory.isUnique(sqlToyContext, uniqueExecutor, getDataSource(uniqueExecutor.getDataSource()));
	}

	protected Long getCountBySql(final String sqlOrSqlId, final Map<String, Object> paramsMap) {
		return getCountByQuery(new QueryExecutor(sqlOrSqlId, (paramsMap == null) ? MapKit.map() : paramsMap));
	}

	/**
	 * 获取数据库查询语句的总记录数
	 * 
	 * @param sqlOrSqlId  sql语句或xml中定义的sqlId
	 * @param paramsNamed 条件参数名称数组,与sql中的参数一一对应
	 * @param paramsValue 条件参数值数组,顺序与paramsNamed一一对应
	 * @return Long 符合条件的总记录数
	 */
	protected Long getCountBySql(final String sqlOrSqlId, final String[] paramsNamed, final Object[] paramsValue) {
		return getCountByQuery(new QueryExecutor(sqlOrSqlId, paramsNamed, paramsValue));
	}

	/**
	 * 通过entity对象来组织count查询语句
	 * 
	 * @param entityClass 单表对应的实体类
	 * @param entityQuery 查询条件构造对象(where/values/排序等),可为null表示无条件
	 * @return 符合条件的总记录数
	 */
	protected Long getCountByEntityQuery(Class entityClass, EntityQuery entityQuery) {
		if (null == entityClass) {
			throw new IllegalArgumentException("getCountByEntityQuery entityClass must not be null!");
		}
		return (Long) findEntityBase(entityClass, null, (entityQuery == null) ? EntityQuery.create() : entityQuery,
				entityClass, true);
	}

	/**
	 * 指定数据源查询记录数量
	 * 
	 * @param queryExecutor 封装了sql(或sqlId)和条件参数的查询执行对象(可动态设置数据源)
	 * @return 符合条件的总记录数
	 */
	protected Long getCountByQuery(final QueryExecutor queryExecutor) {
		QueryExecutorExtend extend = queryExecutor.getInnerModel();
		SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(queryExecutor, SqlType.search,
				getDialect(extend.dataSource));
		Long result = dialectFactory.getCountBySql(sqlToyContext, queryExecutor, sqlToyConfig,
				getDataSource(extend.dataSource, sqlToyConfig));
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
	 * 通用存储过程调用,一般数据库{?=call xxxStore(? in,? in,? out)} 针对oracle数据库只能{call
	 * xxxStore(? in,? in,? out)} 同时结果集必须通过OracleTypes.CURSOR out 参数返回
	 * 目前此方法只能返回一个结果集(集合类数据),可以返回多个非集合类数据，如果有特殊用法，则自行封装调用
	 * 
	 * @param storeSqlOrKey 可以直接传call storeName (?,?) 也可以传xml中的存储过程sqlId
	 * @param inParamsValue 输入参数值数组,顺序与存储过程定义的in参数一致
	 * @param outParamsType (可以为null)
	 * @param resultType    VOClass,HashMap或null(表示二维List)
	 * @param dataSource    显式指定的数据源,为null时使用默认数据源
	 * @return 存储过程执行结果,包含输出参数值和结果集数据
	 */
	protected StoreResult executeStore(final String storeSqlOrKey, final Object[] inParamsValue,
			final Integer[] outParamsType, final Class resultType, final DataSource dataSource) {
		SqlToyConfig sqlToyConfig = getSqlToyConfig(storeSqlOrKey, SqlType.search);
		return dialectFactory.executeStore(sqlToyContext, sqlToyConfig, inParamsValue, outParamsType,
				new Class[] { resultType }, false, null, getDataSource(dataSource, sqlToyConfig));
	}

	protected StoreResult executeMoreResultStore(final String storeSqlOrKey, final Object[] inParamsValue,
			final Integer[] outParamsType, final Class[] resultTypes) {
		SqlToyConfig sqlToyConfig = getSqlToyConfig(storeSqlOrKey, SqlType.search);
		return dialectFactory.executeStore(sqlToyContext, sqlToyConfig, inParamsValue, outParamsType, resultTypes, true,
				null, getDataSource(null, sqlToyConfig));
	}

	/**
	 * @param sqlOrSqlId  sql语句或xml中定义的sqlId
	 * @param paramsNamed 条件参数名称数组,与sql中的参数一一对应
	 * @param paramsValue 条件参数值数组,顺序与paramsNamed一一对应
	 * @return 单行单列的查询值,无数据时返回null
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
			throw new IllegalArgumentException("getSingleValue resultType must not be null!");
		}
		Object value = getSingleValue(sqlOrSqlId, paramsMap);
		if (value == null) {
			return null;
		}
		try {
			return (T) BeanUtil.convertType(value, JdbcTypes.OTHER, DataType.getType(resultType),
					resultType.getTypeName());
		} catch (Exception e) {
			throw new DataAccessException("getSingleValue failed to get a single value:" + e.getMessage(), e);
		}
	}

	/**
	 * 返回单行单列值，无数据返回null，结果集存在多条数据时抛出IllegalArgumentException
	 * 
	 * @param sqlOrSqlId  sql语句或xml中定义的sqlId
	 * @param paramsNamed 条件参数名称数组,与sql中的参数一一对应
	 * @param paramsValue 条件参数值数组,顺序与paramsNamed一一对应
	 * @param dataSource  显式指定的数据源,为null时使用默认数据源
	 * @return 查询到的单行单列值,无数据时返回null
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
	 * 根据给定的对象中的主键值获取对象完整信息
	 * 
	 * @param entity 只需提供主键值的实体对象,据此查询并回填对象其它属性
	 * @return 主键对应的完整对象,记录不存在时返回null
	 */
	protected <T extends Serializable> T load(final T entity) {
		if (entity == null) {
			return null;
		}
		EntityMeta entityMeta = this.getEntityMeta(entity.getClass());
		if (SqlConfigParseUtils.isNamedQuery(entityMeta.getLoadSql(null))) {
			return (T) loadBySql(entityMeta.getLoadSql(null), entity);
		}
		return load(entity, null, null);
	}

	/**
	 * 提供锁定功能的加载
	 * 
	 * @param entity   只需提供主键值的实体对象
	 * @param lockMode 加锁模式(如UPGRADE、UPGRADE_NOWAIT),为null时不加锁
	 * @return 主键对应的完整对象,记录不存在时返回null
	 */
	protected <T extends Serializable> T load(final T entity, final LockMode lockMode) {
		return load(entity, lockMode, null);
	}

	/**
	 * 根据主键值获取对应的记录信息
	 * 
	 * @param entity     待加载的实体对象,必须提供主键值
	 * @param lockMode   加锁模式(如UPGRADE、UPGRADE_NOWAIT),为null时不加锁
	 * @param dataSource 显式指定的数据源,为null时使用默认数据源
	 * @return 主键对应的完整对象,记录不存在时返回null
	 */
	protected <T extends Serializable> T load(final T entity, final LockMode lockMode, final DataSource dataSource) {
		return dialectFactory.load(sqlToyContext, entity, false, null, lockMode, -1, getDataSource(dataSource), -1);
	}

	/**
	 * 指定需要级联加载的类型，通过主对象加载自身和相应的子对象集合
	 * 
	 * @param entity       待加载的实体对象,必须提供主键值
	 * @param lockMode     加锁模式(如UPGRADE、UPGRADE_NOWAIT),为null时不加锁
	 * @param cascadeTypes 指定级联加载的子对象类型,不传则加载实体上配置的全部级联属性
	 * @return 加载后的主对象,级联的子对象集合一并回填
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
		return dialectFactory.load(sqlToyContext, entity, false, cascades, lockMode, -1, getDataSource(null), -1);
	}

	/**
	 * 批量根据实体对象的主键获取对象的详细信息
	 * 
	 * @param entities 待加载的实体对象集合(对象中主键值必须非空)
	 * @param lockMode 加锁模式(如UPGRADE、UPGRADE_NOWAIT),为null时不加锁
	 * @return 回填了详细信息后的对象集合
	 */
	protected <T extends Serializable> List<T> loadAll(final List<T> entities, final LockMode lockMode) {
		return dialectFactory.loadAll(sqlToyContext, entities, null, null, lockMode, -1, null, getDataSource(null), -1);
	}

	/**
	 * 根据id集合批量加载对象
	 * 
	 * @param <T>
	 * @param entityClass 实体类,必须为单一主键的POJO实体
	 * @param ids         主键值集合,支持可变参数或单个Collection
	 * @return 主键对应的对象集合,id无对应记录时不会出现在结果中
	 */
	protected <T extends Serializable> List<T> loadByIds(final Class<T> entityClass, Object... ids) {
		return loadByIds(entityClass, null, ids);
	}

	/**
	 * 通过id集合批量加载对象
	 * 
	 * @param <T>
	 * @param entityClass 实体类,必须为单一主键的POJO实体
	 * @param lockMode    加锁模式(如UPGRADE、UPGRADE_NOWAIT),为null时不加锁
	 * @param ids         主键值集合,支持可变参数或单个Collection
	 * @return 主键对应的对象集合,id无对应记录时不会出现在结果中
	 */
	protected <T extends Serializable> List<T> loadByIds(final Class<T> entityClass, final LockMode lockMode,
			Object... ids) {
		if (entityClass == null || ids == null || ids.length == 0 || (ids.length == 1 && ids[0] == null)) {
			throw new IllegalArgumentException("loadByIds entityClass and primary key values must not be null!");
		}
		EntityMeta entityMeta = getEntityMeta(entityClass);
		validEntity(entityMeta, entityClass, true);
		if (entityMeta.getIdArray().length != 1) {
			throw new IllegalArgumentException("loadByIds only supports POJO entity with a single primary key!");
		}
		Object[] realIds;
		// 单个Collection,将List转为Array数组
		if (ids.length == 1 && ids[0] instanceof Collection) {
			realIds = ((Collection) ids[0]).toArray();
		} else {
			realIds = ids;
		}
		List<T> entities = BeanUtil.wrapEntities(sqlToyContext.getTypeHandler(), entityMeta, entityClass, realIds);
		return dialectFactory.loadAll(sqlToyContext, entities, null, null, lockMode, -1, null, getDataSource(null), -1);
	}

	protected <T extends Serializable> List<T> loadAllCascade(final List<T> entities, final LockMode lockMode,
			final Class... cascadeTypes) {
		return loadAllCascade(entities, null, lockMode, cascadeTypes);
	}

	/**
	 * 批量对象级联加载,指定级联加载的子表
	 * 
	 * @param <T>
	 * @param entities     待加载的实体对象集合(对象中主键值必须非空)
	 * @param onlySubTable true时跳过主表查询,仅加载级联子表数据;null或false则主表一并加载
	 * @param lockMode     加锁模式(如UPGRADE、UPGRADE_NOWAIT),为null时不加锁
	 * @param cascadeTypes 指定级联加载的子对象类型,不传则加载实体上配置的全部级联属性
	 * @return 完成级联回填后的对象集合
	 */
	protected <T extends Serializable> List<T> loadAllCascade(final List<T> entities, final Boolean onlySubTable,
			final LockMode lockMode, final Class... cascadeTypes) {
		if (entities == null || entities.isEmpty()) {
			return entities;
		}
		Class[] cascades = cascadeTypes;
		if (cascades == null || cascades.length == 0) {
			cascades = getEntityMeta(entities.get(0).getClass()).getCascadeTypes();
		}
		return dialectFactory.loadAll(sqlToyContext, entities, onlySubTable, cascades, lockMode, -1, null,
				getDataSource(null), -1);
	}

	protected <T> T loadBySql(final String sqlOrSqlId, final Map<String, Object> paramsMap, final Class<T> resultType) {
		return (T) loadByQuery(
				new QueryExecutor(sqlOrSqlId, (paramsMap == null) ? MapKit.map() : paramsMap).resultType(resultType));
	}

	/**
	 * 根据sql语句查询并返回单个VO对象(可指定自定义对象,sqltoy根据查询label跟对象的属性名称进行匹配映射)
	 * 
	 * @param sqlOrSqlId  sql语句或xml中定义的sqlId
	 * @param paramNames  条件参数名称数组,与sql中的参数一一对应
	 * @param paramValues 条件参数值数组,顺序与paramNames一一对应
	 * @param resultType  返回的单个对象类型(VO、Map等)
	 * @return 查询到的单条记录对象,无数据时返回null,多条记录时抛出IllegalArgumentException
	 */
	protected <T> T loadBySql(final String sqlOrSqlId, final String[] paramNames, final Object[] paramValues,
			final Class<T> resultType) {
		return (T) loadByQuery(new QueryExecutor(sqlOrSqlId, paramNames, paramValues).resultType(resultType));
	}

	/**
	 * 解析sql中:named 属性到params对象获取对应的属性值作为查询条件,并将查询结果以params的class类型返回
	 * 
	 * @param sqlOrSqlId sql语句或xml中定义的sqlId
	 * @param params     查询参数对象（支持任意实现了Serializable的Bean，如VO、DTO、QueryParam等，对象的属性名将与SQL中的命名参数进行匹配）
	 * @return 查询到的单条记录(类型同params),无数据时返回null,多条记录时抛出IllegalArgumentException
	 */
	protected <T extends Serializable> T loadBySql(final String sqlOrSqlId, final T params) {
		return (T) loadByQuery(new QueryExecutor(sqlOrSqlId, params));
	}

	protected <T extends Serializable> T loadEntity(Class<T> entityClass, EntityQuery entityQuery) {
		List<T> result = findEntity(entityClass, entityQuery);
		if (result == null || result.isEmpty()) {
			return null;
		}
		if (result.size() == 1) {
			return result.get(0);
		}
		throw new IllegalArgumentException("loadEntity expect a single record but found [" + result.size()
				+ "] rows, please check the load conditions!");
	}

	/**
	 * TODO 通过构造QueyExecutor 提供更加灵活的参数传递方式，包括DataSource 比如:
	 * <li>1、new QueryExecutor(sql,entity).dataSource(dataSource)</li>
	 * <li>2、new
	 * QueryExecutor(sql).names(paramNames).values(paramValues).resultType(resultType);
	 * </li>
	 * 
	 * @param queryExecutor 封装了sql(或sqlId)、参数、返回类型、数据源等的执行对象
	 * @return 查询到的单条记录(类型由resultType决定),无数据时返回null,多条记录时抛出IllegalArgumentException
	 */
	protected Object loadByQuery(final QueryExecutor queryExecutor) {
		QueryExecutorExtend extend = queryExecutor.getInnerModel();
		SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(queryExecutor, SqlType.search,
				getDialect(extend.dataSource));
		QueryResult result = dialectFactory.findByQuery(sqlToyContext, queryExecutor, sqlToyConfig, null,
				getDataSource(extend.dataSource, sqlToyConfig));
		// 产品化场景，适配其他数据库验证查询(仅仅在设置了redoDataSources时生效)
		CrossDbAdapter.redoQuery(sqlToyContext, dialectFactory, queryExecutor);
		List rows = result.getRows();
		if (rows == null || rows.isEmpty()) {
			return null;
		}
		if (rows.size() == 1) {
			return rows.get(0);
		}
		throw new IllegalArgumentException("loadByQuery expect a single record but found [" + rows.size()
				+ "] rows, please check the query conditions!");
	}

	/**
	 * 执行无条件的sql语句,一般是一个修改、删除等操作，并返回修改的记录数量
	 * 
	 * @param sqlOrSqlId 不带参数的sql语句或xml中定义的sqlId
	 * @return 实际修改(插入、删除)的记录数量
	 */
	protected Long executeSql(final String sqlOrSqlId) {
		return executeSql(sqlOrSqlId, null, null, null, null);
	}

	/**
	 * 解析sql中的参数名称，以此名称到params中提取对应的值作为查询条件值执行sql
	 * 
	 * @param sqlOrSqlId sql语句或xml中定义的sqlId
	 * @param params     查询参数对象（支持任意实现了Serializable的Bean，如VO、DTO、QueryParam等，对象的属性名将与SQL中的命名参数进行匹配）
	 * @return 实际修改(插入、删除)的记录数量
	 */
	protected Long executeSql(final String sqlOrSqlId, final Serializable params) {
		// update 2025-4-29 兼容executeSql(sql,Object...params) 只有一个参数时的场景
		if (params == null || BeanUtil.isBaseDataType(params.getClass())) {
			return executeSql(sqlOrSqlId, null, new Object[] { params }, null, null);
		} else if (params instanceof Collection) {
			return executeSql(sqlOrSqlId, null, ((Collection) params).toArray(), null, null);
		}
		SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(sqlOrSqlId, SqlType.update, getDialect(dataSource),
				params);
		return dialectFactory.executeSql(sqlToyContext, sqlToyConfig, new QueryExecutor(sqlOrSqlId, params), null, null,
				getDataSource(null, sqlToyConfig));
	}

	protected Long executeSql(final String sqlOrSqlId, final Map<String, Object> paramsMap) {
		return executeSql(sqlOrSqlId, (Serializable) new IgnoreKeyCaseMap(paramsMap));
	}

	/**
	 * 执行无返回结果的SQL(返回updateCount)
	 * 
	 * @param sqlOrSqlId  sql语句或xml中定义的sqlId
	 * @param paramsNamed 条件参数名称数组,与sql中的参数一一对应
	 * @param paramsValue 条件参数值数组,顺序与paramsNamed一一对应
	 * @return 实际修改(插入、删除)的记录数量
	 */
	protected Long executeSql(final String sqlOrSqlId, final String[] paramsNamed, final Object[] paramsValue) {
		return executeSql(sqlOrSqlId, paramsNamed, paramsValue, null, null);
	}

	/**
	 * 执行无返回结果的SQL(返回updateCount),根据autoCommit设置是否自动提交
	 * 
	 * @param sqlOrSqlId  sql语句或xml中定义的sqlId
	 * @param paramsNamed 条件参数名称数组,与sql中的参数一一对应
	 * @param paramsValue 条件参数值数组,顺序与paramsNamed一一对应
	 * @param autoCommit  自动提交，默认可以填null
	 * @param dataSource  显式指定的数据源,为null时使用默认数据源
	 * @return 实际修改(插入、删除)的记录数量
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
	 * 通过jdbc方式批量插入数据，一般提供给数据采集时或插入临时表使用
	 * 
	 * @param sqlOrSqlId 批量操作的sql或xml中定义的sqlId
	 * @param dataSet    支持List<List>、List<Object[]>(sql中?传参) ;List<VO>、List<Map>
	 *                   形式(sql中:paramName传参)
	 * @param batchSize  每批提交的记录数量
	 * @param autoCommit 自动提交，默认可以填null
	 * @return 实际批量操作影响的记录总数
	 */
	protected Long batchUpdate(final String sqlOrSqlId, final List dataSet, final int batchSize,
			final Boolean autoCommit) {
		return batchUpdate(sqlOrSqlId, dataSet, batchSize, autoCommit, null);
	}

	/**
	 * 批量执行sql修改或删除操作
	 * 
	 * @param sqlOrSqlId 批量操作的sql或xml中定义的sqlId
	 * @param dataSet    支持List<List>、List<Object[]>(sql中?传参) ;List<VO>、List<Map>
	 *                   形式(sql中:paramName传参)
	 * @param batchSize  每批提交的记录数量
	 * @param autoCommit 自动提交，默认可以填null
	 * @param dataSource 显式指定的数据源,为null时使用默认数据源
	 * @return 实际批量操作影响的记录总数
	 */
	protected Long batchUpdate(final String sqlOrSqlId, final List dataSet, final int batchSize,
			final Boolean autoCommit, final DataSource dataSource) {
		SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(sqlOrSqlId, SqlType.update, getDialect(dataSource),
				null);
		return dialectFactory.batchUpdate(sqlToyContext, sqlToyConfig, dataSet, batchSize, null, null, null, autoCommit,
				getDataSource(dataSource, sqlToyConfig));
	}

	protected boolean wrapTreeTableRoute(final TreeTableModel treeModel) {
		return wrapTreeTableRoute(treeModel, null);
	}

	/**
	 * 构造树形表的节点路径、节点层级、节点类别(是否叶子节点)
	 * 
	 * @param treeModel  树形表数据模型(包含表名、节点字段配置及待处理的数据)
	 * @param dataSource 显式指定的数据源,为null时使用默认数据源
	 * @return 处理是否成功
	 */
	protected boolean wrapTreeTableRoute(final TreeTableModel treeModel, final DataSource dataSource) {
		return dialectFactory.wrapTreeTableRoute(sqlToyContext, treeModel, this.getDataSource(dataSource));
	}

	/**
	 * 以params对象的属性给sql中的:named 传参数，进行查询，并返回params的class类型的集合
	 * 
	 * @param sqlOrSqlId sql语句或xml中定义的sqlId
	 * @param params     查询参数对象（支持任意实现了Serializable的Bean，如VO、DTO、QueryParam等，对象的属性名将与SQL中的命名参数进行匹配）
	 * @return 以params同类型对象组成的集合,无数据时为空集合
	 */
	protected <T extends Serializable> List<T> findBySql(final String sqlOrSqlId, final T params) {
		return (List<T>) findByQuery(new QueryExecutor(sqlOrSqlId, params)).getRows();
	}

	protected <T> List<T> findBySql(final String sqlOrSqlId, final Map<String, Object> paramsMap,
			final Class<T> resultType) {
		return (List<T>) findByQuery(
				new QueryExecutor(sqlOrSqlId, (paramsMap == null) ? MapKit.map() : paramsMap).resultType(resultType))
				.getRows();
	}

	/**
	 * 查询集合
	 * 
	 * @param <T>
	 * @param sqlOrSqlId  sql语句或xml中定义的sqlId
	 * @param paramsNamed 条件参数名称数组,与sql中的参数一一对应
	 * @param paramsValue 条件参数值数组,顺序与paramsNamed一一对应
	 * @param resultType  分null(返回二维List)、voClass、HashMap.class、LinkedHashMap.class等
	 * @return 查询结果集合(元素类型由resultType决定),无数据时为空集合
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
	 * 以queryExecutor 封装sql、条件、数据库源等进行集合查询
	 * 
	 * @param queryExecutor (可动态设置数据源)
	 * @return 查询结果对象,通过getRows()获取行数据
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
	 * 按照流模式活动查询结果数据
	 * 
	 * @param queryExecutor       封装了sql(或sqlId)、参数、数据源等的执行对象
	 * @param streamResultHandler 流式处理回调,逐行消费查询结果,避免大数据量一次性载入内存
	 */
	protected void fetchStream(final QueryExecutor queryExecutor, final StreamResultHandler streamResultHandler) {
		SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(queryExecutor, SqlType.search,
				getDialect(queryExecutor.getInnerModel().dataSource));
		dialectFactory.fetchStream(sqlToyContext, queryExecutor, sqlToyConfig, streamResultHandler,
				getDataSource(queryExecutor.getInnerModel().dataSource, sqlToyConfig));
	}

	/**
	 * 以QueryExecutor 封装sql、参数等条件，实现分页查询
	 * 
	 * @param page          分页模型,提供页码pageNo和每页记录数pageSize(可设置skipQueryCount跳过总记录数查询)
	 * @param queryExecutor (可动态设置数据源)
	 * @return 分页查询结果,通过getPageResult()获取当前页数据
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
	 * 指定sql和参数名称以及名称对应的值和返回结果的类型(类型可以是java.util.HashMap),进行分页查询
	 * sql可以是一个具体的语句也可以是xml中定义的sqlId
	 * 
	 * @param page                                                                                      分页模型,提供页码pageNo和每页记录数pageSize
	 * @param sqlOrSqlId                                                                                sql语句或xml中定义的sqlId
	 * @param paramsNamed                                                                               条件参数名称数组,与sql中的参数一一对应
	 * @param paramsValue                                                                               条件参数值数组,顺序与paramsNamed一一对应
	 * @param resultType(null则返回List<List>二维集合,HashMap.class:则返回List<HashMap<columnLabel,columnValue>>)
	 * @return 当前页数据组成的Page对象
	 */
	protected <T> Page<T> findPageBySql(final Page page, final String sqlOrSqlId, final String[] paramsNamed,
			final Object[] paramsValue, Class<T> resultType) {
		QueryExecutor query = new QueryExecutor(sqlOrSqlId, paramsNamed, paramsValue);
		if (resultType != null) {
			query.resultType(resultType);
		}
		return (Page<T>) findPageByQuery(page, query).getPageResult();
	}

	protected <T extends Serializable> Page<T> findPageBySql(final Page page, final String sqlOrSqlId, final T params) {
		return (Page<T>) findPageByQuery(page, new QueryExecutor(sqlOrSqlId, params)).getPageResult();
	}

	protected <T> Page<T> findPageBySql(final Page page, final String sqlOrSqlId, final Map<String, Object> paramsMap,
			Class<T> resultType) {
		return (Page<T>) findPageByQuery(page,
				new QueryExecutor(sqlOrSqlId, (paramsMap == null) ? MapKit.map() : paramsMap).resultType(resultType))
				.getPageResult();
	}

	protected <T> List<T> findTopBySql(final String sqlOrSqlId, final Map<String, Object> paramsMap,
			final Class<T> resultType, final double topSize) {
		return (List<T>) findTopByQuery(
				new QueryExecutor(sqlOrSqlId, (paramsMap == null) ? MapKit.map() : paramsMap).resultType(resultType),
				topSize).getRows();
	}

	/**
	 * 取符合条件的结果前多少数据,topSize>1 则取整数返回记录数量，topSize<1 则按比例返回结果记录(topSize必须是大于0)
	 * 
	 * @param sqlOrSqlId                                                                                sql语句或xml中定义的sqlId
	 * @param paramsNamed                                                                               条件参数名称数组,与sql中的参数一一对应
	 * @param paramsValue                                                                               条件参数值数组,顺序与paramsNamed一一对应
	 * @param resultType(null则返回List<List>二维集合,HashMap.class:则返回List<HashMap<columnLabel,columnValue>>)
	 * @param topSize                                                                                   >1
	 *                                                                                                  取整数部分，<1
	 *                                                                                                  则表示按比例获取
	 * @return 取回的前N条(或按比例)记录集合,无数据时为空集合
	 */
	protected <T> List<T> findTopBySql(final String sqlOrSqlId, final String[] paramsNamed, final Object[] paramsValue,
			final Class<T> resultType, final double topSize) {
		return (List<T>) findTopByQuery(new QueryExecutor(sqlOrSqlId, paramsNamed, paramsValue).resultType(resultType),
				topSize).getRows();
	}

	protected <T extends Serializable> List<T> findTopBySql(final String sqlOrSqlId, final T params,
			final double topSize) {
		return (List<T>) findTopByQuery(new QueryExecutor(sqlOrSqlId, params), topSize).getRows();
	}

	/**
	 * 以queryExecutor封装sql、条件参数、数据源等进行取top集合查询
	 * 
	 * @param queryExecutor (可动态设置数据源)
	 * @param topSize       取前多少条,大于1取整数条,小于1按比例取(必须大于0)
	 * @return 查询结果对象,通过getRows()获取行数据
	 */
	protected QueryResult findTopByQuery(final QueryExecutor queryExecutor, final double topSize) {
		SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(queryExecutor, SqlType.search,
				getDialect(queryExecutor.getInnerModel().dataSource));
		QueryResult result = dialectFactory.findTop(sqlToyContext, queryExecutor, sqlToyConfig, topSize,
				getDataSource(queryExecutor.getInnerModel().dataSource, sqlToyConfig));
		// 产品化场景，适配其他数据库验证查询(仅仅在设置了redoDataSources时生效)
		CrossDbAdapter.redoTopQuery(sqlToyContext, dialectFactory, queryExecutor, topSize);
		return result;
	}

	/**
	 * 在符合条件的结果中随机提取多少条记录,randomCount>1 则取整数记录，randomCount<1 则按比例提取随机记录
	 * 如randomCount=0.05 总记录数为100,则随机取出5条记录
	 * 
	 * @param queryExecutor (可动态设置数据源)
	 * @param randomCount   随机提取的记录数量,大于1取整数条,小于1按比例提取(如0.05表示随机取总量的5%)
	 * @return 随机提取的结果对象,通过getRows()获取行数据
	 */
	protected QueryResult getRandomResult(final QueryExecutor queryExecutor, final double randomCount) {
		SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(queryExecutor, SqlType.search,
				getDialect(queryExecutor.getInnerModel().dataSource));
		QueryResult result = dialectFactory.getRandomResult(sqlToyContext, queryExecutor, sqlToyConfig, randomCount,
				getDataSource(queryExecutor.getInnerModel().dataSource, sqlToyConfig));
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
	 * 快速删除表中的数据,autoCommit为null表示按照连接的默认值(如dbcp可以配置默认是否autoCommit)
	 * 
	 * @param tableName  要清空的表名称
	 * @param autoCommit 是否自动提交,为null时按连接的默认值
	 * @param dataSource 显式指定的数据源,为null时使用默认数据源
	 */
	protected void truncate(final String tableName, final Boolean autoCommit, final DataSource dataSource) {
		if (StringUtil.isBlank(tableName)) {
			throw new IllegalArgumentException("truncate tableName is blank or null,please check!");
		}
		executeSql("truncate table ".concat(tableName), null, null, autoCommit, this.getDataSource(dataSource));
	}

	/**
	 * 保存对象数据(返回插入的主键值),会针对对象的子集进行级联保存
	 * 
	 * @param entity 待保存的实体对象
	 * @return 插入记录的主键值
	 */
	protected Object save(final Serializable entity) {
		return save(entity, null);
	}

	/**
	 * 指定数据库插入单个对象并返回主键值,会针对对象的子表集合数据进行级联保存
	 * 
	 * @param entity     待保存的实体对象
	 * @param dataSource 显式指定的数据源,为null时使用默认数据源
	 * @return 插入记录的主键值
	 */
	protected Object save(final Serializable entity, final DataSource dataSource) {
		return dialectFactory.save(sqlToyContext, entity, getDataSource(dataSource));
	}

	/**
	 * 批量插入对象(会自动根据主键策略产生主键值,并填充对象集合),不做级联操作
	 * 
	 * @param <T>
	 * @param entities 待批量插入的实体对象集合(主键值自动生成并回填到对象中)
	 * @return 实际插入的记录数量
	 */
	protected <T extends Serializable> Long saveAll(final List<T> entities) {
		return this.saveAll(entities, null);
	}

	/**
	 * 指定数据库进行批量插入
	 * 
	 * @param <T>
	 * @param entities   待批量插入的实体对象集合(主键值自动生成并回填到对象中)
	 * @param dataSource 显式指定的数据源,为null时使用默认数据源
	 * @return 实际插入的记录数量
	 */
	protected <T extends Serializable> Long saveAll(final List<T> entities, final DataSource dataSource) {
		return dialectFactory.saveAll(sqlToyContext, entities, sqlToyContext.getBatchSize(), null, null,
				getDataSource(dataSource), null);
	}

	/**
	 * 保存对象数据(返回插入的主键值),忽视已经存在的
	 * 
	 * @param entities 待保存的实体对象集合(数据库中已存在的记录被忽视)
	 * @return 实际插入的记录数量
	 */
	protected <T extends Serializable> Long saveAllIgnoreExist(final List<T> entities) {
		return saveAllIgnoreExist(entities, null);
	}

	/**
	 * 保存对象数据(返回插入的主键值),忽视已经存在的
	 * 
	 * @param entities   待保存的实体对象集合(数据库中已存在的记录被忽视)
	 * @param dataSource 显式指定的数据源,为null时使用默认数据源
	 * @return 实际插入的记录数量
	 */
	protected <T extends Serializable> Long saveAllIgnoreExist(final List<T> entities, final DataSource dataSource) {
		return dialectFactory.saveAllIgnoreExist(sqlToyContext, entities, sqlToyContext.getBatchSize(), null, null,
				getDataSource(dataSource), null);
	}

	/**
	 * update对象(值为null的属性不修改,通过forceUpdateProps指定要进行强制修改属性)
	 * 
	 * @param entity           待修改的实体对象(根据主键定位记录)
	 * @param forceUpdateProps 强制修改的属性
	 * @return 实际修改的记录数量
	 */
	protected Long update(final Serializable entity, final String... forceUpdateProps) {
		return this.update(entity, forceUpdateProps, null);
	}

	/**
	 * 根据传入的对象，通过其主键值查询并修改其它属性的值
	 * 
	 * @param entity           待修改的实体对象(根据主键定位记录,null值属性不修改)
	 * @param forceUpdateProps 强制修改的属性(null值的属性也会被更新)
	 * @param dataSource       显式指定的数据源,为null时使用默认数据源
	 * @return 实际修改的记录数量
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
				throw new IllegalArgumentException(
						"table [" + entityMeta.getTableName() + "] has @DataVersion configuration, the property ["
								+ dataVersion.getField() + "] value must not be null, please check!");
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
				throw new DataAccessException("table [" + entityMeta.getTableName() + "] data version [" + verStr
						+ "] is being modified by another user or has already been updated!");
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
				verStr = "" + (Long.parseLong(verStr) + 1);
			}
			// 更新版本号
			BeanUtil.setProperty(entity, dataVersion.getField(), verStr);
		}
		return dialectFactory.update(sqlToyContext, entity, forceUpdateProps, false, null, null,
				getDataSource(dataSource));
	}

	/**
	 * 修改对象,并通过指定级联的子对象做级联修改
	 * 
	 * @param entity                   待修改的实体对象(根据主键定位记录)
	 * @param forceUpdateProps         主表强制修改的属性
	 * @param forceCascadeClasses      (强制需要修改的子对象,当子集合数据为null,则进行清空或置为无效处理,否则则忽视对存量数据的处理)
	 * @param subTableForceUpdateProps 子表对应的强制修改属性,key为级联子对象类型,value为该子表强制修改的属性数组
	 * @return 实际修改的记录数量
	 */
	protected Long updateCascade(final Serializable entity, final String[] forceUpdateProps,
			final Class[] forceCascadeClasses, final HashMap<Class, String[]> subTableForceUpdateProps) {
		return dialectFactory.update(sqlToyContext, entity, forceUpdateProps, true, forceCascadeClasses,
				subTableForceUpdateProps, getDataSource(null));
	}

	/**
	 * 适用于库存台账、客户资金账等高并发强事务场景，一次数据库交互实现：1、锁查询；2、记录存在则修改；3、记录不存在则执行insert；4、返回修改或插入的记录信息，尽量不要使用identity、sequence主键
	 * 
	 * @param <T>
	 * @param entity           操作的实体对象(要求主键为业务主键)
	 * @param updateRowHandler 锁定记录后的修改回调,通过rs更新字段值
	 * @param uniqueProps      判断记录是否存在的唯一属性,为null时默认使用主键属性
	 * @param dataSource       显式指定的数据源,为null时使用默认数据源
	 * @return 修改或插入后的记录信息
	 */
	public <T extends Serializable> T updateSaveFetch(final T entity, final UpdateRowHandler updateRowHandler,
			final int lockWaitTimeout, final String[] uniqueProps, final DataSource dataSource) {
		return (T) dialectFactory.updateSaveFetch(sqlToyContext, entity, updateRowHandler, lockWaitTimeout, uniqueProps,
				getDataSource(dataSource));
	}

	public <T extends Serializable> T updateSaveFetch(final T entity, final UpdateRowHandler updateRowHandler,
			final String[] uniqueProps, final DataSource dataSource) {
		return (T) dialectFactory.updateSaveFetch(sqlToyContext, entity, updateRowHandler, -1, uniqueProps,
				getDataSource(dataSource));
	}

	public <T extends Serializable> T updateSaveFetch(final T entity, EntityUpdateCallback<T> callback,
			int lockWaitTimeout, final String[] uniqueProps, final DataSource dataSource) {
		Class<T> entityClazz = (Class<T>) entity.getClass();
		UpdateRowCallback updateRowCallback = BeanUtil.toSqlToyHandler(getEntityMeta(entityClazz), entityClazz,
				callback);
		return (T) dialectFactory.updateSaveFetch(sqlToyContext, entity, updateRowCallback, lockWaitTimeout,
				uniqueProps, getDataSource(dataSource));
	}

	/**
	 * 深度更新实体对象数据,根据对象的属性值全部更新对应表的字段数据,不涉及级联修改
	 * 
	 * @param entity 待深度修改的实体对象(根据主键定位记录,属性值为null则更新对应字段为null)
	 * @return 实际修改的记录数量
	 */
	protected Long updateDeeply(final Serializable entity) {
		return this.updateDeeply(entity, null);
	}

	/**
	 * 深度修改,即对象所有属性值都映射到数据库中,如果是null则数据库值被改为null
	 * 
	 * @param entity     待深度修改的实体对象(根据主键定位记录)
	 * @param dataSource 显式指定的数据源,为null时使用默认数据源
	 * @return 实际修改的记录数量
	 */
	protected Long updateDeeply(final Serializable entity, final DataSource dataSource) {
		if (entity == null) {
			logger.warn("updateDeeply entity is null,please check!");
			return 0L;
		}
		EntityMeta entityMeta = getEntityMeta(entity.getClass());
		return this.update(entity, (entityMeta == null) ? null : entityMeta.getRejectIdFieldArray(true),
				getDataSource(dataSource));
	}

	/**
	 * 批量根据主键更新每条记录,通过forceUpdateProps设置强制要修改的属性
	 * 
	 * @param <T>
	 * @param entities         待批量修改的实体对象集合(根据各对象主键定位记录)
	 * @param forceUpdateProps 强制修改的属性
	 * @return 实际修改的记录总数
	 */
	protected <T extends Serializable> Long updateAll(final List<T> entities, final String... forceUpdateProps) {
		return this.updateAll(entities, forceUpdateProps, null);
	}

	/**
	 * 指定数据库,通过集合批量修改数据库记录
	 * 
	 * @param <T>
	 * @param entities         待批量修改的实体对象集合(根据各对象主键定位记录,null值属性不修改)
	 * @param forceUpdateProps 强制修改的属性(null值的属性也会被更新)
	 * @param dataSource       显式指定的数据源,为null时使用默认数据源
	 * @return 实际修改的记录总数
	 */
	protected <T extends Serializable> Long updateAll(final List<T> entities, final String[] forceUpdateProps,
			final DataSource dataSource) {
		return dialectFactory.updateAll(sqlToyContext, entities, sqlToyContext.getBatchSize(), null, forceUpdateProps,
				null, null, getDataSource(dataSource), null);
	}

	/**
	 * 批量深度修改(参见updateDeeply,直接将集合VO中的字段值修改到数据库中,未null则置null)
	 * 
	 * @param <T>
	 * @param entities 待批量深度修改的实体对象集合(属性值为null则更新对应字段为null)
	 * @return 实际修改的记录总数
	 */
	protected <T extends Serializable> Long updateAllDeeply(final List<T> entities) {
		return updateAllDeeply(entities, null);
	}

	/**
	 * 指定数据源进行批量深度修改(对象属性值为null则设置表对应的字段为null)
	 * 
	 * @param <T>
	 * @param entities   待批量深度修改的实体对象集合(根据各对象主键定位记录)
	 * @param dataSource 显式指定的数据源,为null时使用默认数据源
	 * @return 实际修改的记录总数
	 */
	protected <T extends Serializable> Long updateAllDeeply(final List<T> entities, final DataSource dataSource) {
		if (entities == null || entities.isEmpty()) {
			logger.warn("updateAllDeeply List<POJO> is null,please check!");
			return 0L;
		}
		EntityMeta entityMeta = getEntityMeta(entities.get(0).getClass());
		return updateAll(entities, (entityMeta == null) ? null : entityMeta.getRejectIdFieldArray(true), dataSource);
	}

	protected Long saveOrUpdate(final Serializable entity, final String... forceUpdateProps) {
		return this.saveOrUpdate(entity, forceUpdateProps, null);
	}

	/**
	 * 指定数据库,对对象进行保存或修改，forceUpdateProps:当修改操作时强制修改的属性
	 * 
	 * @param entity           待操作的实体对象(主键在数据库中存在对应记录则修改,否则插入)
	 * @param forceUpdateProps 修改操作时强制修改的属性
	 * @param dataSource       显式指定的数据源,为null时使用默认数据源
	 * @return 实际插入或修改的记录数量
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
		return dialectFactory.saveOrUpdate(sqlToyContext, entity, forceUpdateProps, getDataSource(dataSource));
	}

	/**
	 * 批量保存或修改，并指定强迫修改的字段属性
	 * 
	 * @param <T>
	 * @param entities         待批量保存或修改的实体对象集合
	 * @param forceUpdateProps 修改操作时强制修改的属性
	 * @return 实际插入或修改的记录总数
	 */
	protected <T extends Serializable> Long saveOrUpdateAll(final List<T> entities, final String... forceUpdateProps) {
		return this.saveOrUpdateAll(entities, forceUpdateProps, null);
	}

	/**
	 * 批量保存或修改
	 * 
	 * @param <T>
	 * @param entities         待批量保存或修改的实体对象集合(主键存在对应记录则修改,否则插入)
	 * @param forceUpdateProps 修改操作时强制修改的属性
	 * @param dataSource       显式指定的数据源,为null时使用默认数据源
	 * @return 实际插入或修改的记录总数
	 */
	protected <T extends Serializable> Long saveOrUpdateAll(final List<T> entities, final String[] forceUpdateProps,
			final DataSource dataSource) {
		return dialectFactory.saveOrUpdateAll(sqlToyContext, entities, sqlToyContext.getBatchSize(), forceUpdateProps,
				null, null, getDataSource(dataSource), null);
	}

	/**
	 * 通过主键删除单条记录(会自动级联删除子表,根据数据库配置)
	 * 
	 * @param entity 待删除的实体对象(根据主键定位记录)
	 * @return 实际删除的记录数量
	 */
	protected Long delete(final Serializable entity) {
		return dialectFactory.delete(sqlToyContext, entity, getDataSource(null));
	}

	protected Long delete(final Serializable entity, final DataSource dataSource) {
		return dialectFactory.delete(sqlToyContext, entity, getDataSource(dataSource));
	}

	/**
	 * 提供单表简易查询进行删除操作(删除操作filters过滤无效)
	 * 
	 * @param entityClass 单表对应的实体类
	 * @param entityQuery 删除条件构造对象(where/values必须提供精准条件)
	 * @return 实际删除的记录数量
	 */
	protected Long deleteByQuery(Class entityClass, EntityQuery entityQuery) {
		// 先完成参数判空再取innerModel,参数为null时给出明确提示而非NPE
		if (null == entityClass || null == entityQuery) {
			throw new IllegalArgumentException("deleteByQuery entityClass, where, value must not be null!");
		}
		EntityQueryExtend innerModel = entityQuery.getInnerModel();
		if (StringUtil.isBlank(innerModel.where) || StringUtil.isBlank(innerModel.values)) {
			throw new IllegalArgumentException("deleteByQuery entityClass, where, value must not be null!");
		}
		EntityMeta entityMeta = getEntityMeta(entityClass);
		validEntity(entityMeta, entityClass, false);
		// 做一个必要提示
		if (!innerModel.paramFilters.isEmpty()) {
			logger.warn(
					"setting dynamic condition filters on delete operation is invalid, the delete condition must be precise!");
		}
		String where = SqlUtil.convertFieldsToColumns(entityMeta, innerModel.where);
		String sql = "delete from ".concat(entityMeta.getSchemaTable(null, null));
		// 避开1=1
		if (!where.replaceAll("\\s", "").equals("1=1")) {
			sql = sql.concat(" where ").concat(where);
		}
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
		queryExecutor.getInnerModel().showSql = innerModel.showSql;
		SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(queryExecutor, SqlType.delete,
				getDialect(innerModel.dataSource));
		sqlToyConfig.setSqlType(SqlType.delete);
		return dialectFactory.executeSql(sqlToyContext, sqlToyConfig, queryExecutor, null, null,
				getDataSource(innerModel.dataSource));
	}

	protected <T extends Serializable> Long deleteAll(final List<T> entities) {
		return deleteAll(entities, null);
	}

	/**
	 * 批量删除数据
	 * 
	 * @param <T>
	 * @param entities   待批量删除的实体对象集合(根据各对象主键定位记录)
	 * @param dataSource 显式指定的数据源,为null时使用默认数据源
	 * @return 实际删除的记录总数
	 */
	protected <T extends Serializable> Long deleteAll(final List<T> entities, final DataSource dataSource) {
		return dialectFactory.deleteAll(sqlToyContext, entities, sqlToyContext.getBatchSize(), null,
				getDataSource(dataSource), null);
	}

	/**
	 * 提供单一主键对象的批量快速删除调用方法
	 * 
	 * @param entityClass 实体类,必须为单一主键的POJO实体
	 * @param ids         主键值集合,支持可变参数或单个Collection
	 * @return 实际删除的记录数量
	 */
	protected Long deleteByIds(Class entityClass, Object... ids) {
		if (entityClass == null || ids == null || ids.length == 0 || (ids.length == 1 && ids[0] == null)) {
			throw new IllegalArgumentException("deleteByIds entityClass and primary key values must not be null!");
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
	 * 锁定记录查询，并对记录进行修改,最后将结果返回
	 * 
	 * @param queryExecutor    封装了sql(或sqlId)、参数、数据源等的执行对象
	 * @param updateRowHandler 锁定记录后的修改回调,通过rs直接更新字段值
	 * @return 修改后回显的记录集合
	 */
	protected List updateFetch(final QueryExecutor queryExecutor, final UpdateRowHandler updateRowHandler) {
		SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(queryExecutor, SqlType.search,
				getDialect(queryExecutor.getInnerModel().dataSource));
		return dialectFactory.updateFetch(sqlToyContext, queryExecutor, sqlToyConfig, updateRowHandler,
				getDataSource(queryExecutor.getInnerModel().dataSource, sqlToyConfig)).getRows();
	}

	/**
	 * 获取对象信息(对应的表以及字段、主键策略等等的信息)
	 * 
	 * @param entityClass 实体类
	 * @return 实体对应的元数据模型(表名、字段、主键及主键策略、缓存配置等)
	 */
	protected EntityMeta getEntityMeta(Class entityClass) {
		return sqlToyContext.getEntityMeta(entityClass);
	}

	/**
	 * 获取sqltoy配置的批处理每批记录量(默认为50)
	 * 
	 * @return 每批处理的记录数量
	 */
	protected int getBatchSize() {
		return sqlToyContext.getBatchSize();
	}

	/**
	 * 协助完成对对象集合的属性批量赋予相应数值
	 * 
	 * @param names 参与批量赋值的对象属性名称数组
	 * @return BeanWrapper对象,通过values(...)设置对应属性值后调用mappingSet完成批量赋值
	 */
	protected BeanWrapper wrapBeanProps(String... names) {
		return BeanWrapper.create().names(names);
	}

	/**
	 * 手工提交数据库操作,只提供当前DataSource提交
	 */
	protected void flush() {
		flush(null);
	}

	/**
	 * 手工提交数据库操作,只提供当前DataSource提交
	 * 
	 * @param dataSource 显式指定的数据源,为null时使用默认数据源
	 */
	protected void flush(DataSource dataSource) {
		DataSourceUtils.processDataSource(sqlToyContext, getDataSource(dataSource), new DataSourceCallbackHandler() {
			@Override
			public void doConnection(Connection conn, Integer dbType, String dialect) throws Exception {
				if (!conn.isClosed()) {
					conn.commit();
				}
			}
		});
	}

	/**
	 * 产生ID(可以指定增量范围，当一个表里面涉及多个业务主键时，sqltoy在配置层面只支持单个，但开发者可以调用此方法自行获取后赋值)
	 * 
	 * @param signature 唯一标识符号
	 * @param increment 唯一标识符号，默认设置为1
	 * @return 产生的分布式id值
	 */
	protected long generateBizId(String signature, int increment) {
		if (StringUtil.isBlank(signature)) {
			throw new IllegalArgumentException(
					"signature must not be blank, please specify the correct business signature!");
		}
		return getDistributeIdGenerator().generateId(signature, increment,
				SqlToyConstants.getDistributeIdCacheExpireDate());
	}

	/**
	 * 根据实体对象对应的POJO配置的业务主键策略,提取对象的属性值产生业务主键
	 * 
	 * @param entity 属性值作为产生业务主键依据的实体对象
	 * @return 产生的业务主键值
	 */
	protected String generateBizId(Serializable entity) {
		EntityMeta entityMeta = getEntityMeta(entity.getClass());
		if (entityMeta == null || !entityMeta.isHasBizIdConfig()) {
			throw new IllegalArgumentException(StringUtil.fillArgs(
					"entity:{}, has no business id generation strategy configured, please check the business id configuration of the POJO!",
					entity.getClass().getName()));
		}
		String businessIdType = entityMeta.getColumnJavaType(entityMeta.getBusinessIdField());
		Integer[] relatedColumn = entityMeta.getBizIdRelatedColIndex();
		Object[] fullParamValues = BeanUtil.reflectBeanToAry(entity, entityMeta.getFieldsArray(false));
		// 提取关联属性的值
		Object[] relatedColValue = null;
		if (relatedColumn != null) {
			relatedColValue = new Object[relatedColumn.length];
			for (int meter = 0; meter < relatedColumn.length; meter++) {
				relatedColValue[meter] = fullParamValues[relatedColumn[meter]];
				if (relatedColValue[meter] == null) {
					throw new IllegalArgumentException("generate business id for entity [" + entity.getClass().getName()
							+ "], the related field [" + relatedColumn[meter] + "] value is null, please check!");
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
	 * 根据指定的表名、业务码，业务码的属性和值map，动态获取业务主键值 例如:generateBizId("sag_test",
	 * "HW@case(orderType,SALE,SC,BUY,PO)@day(yyMMdd)", MapKit.map("orderType",
	 * "SALE"), null, 12, 2);
	 * 
	 * @param tableName    业务表名称(作为id产生的隔离标识之一)
	 * @param signature    一个表达式字符串，支持@case(name,value1,then1,val2,then2)
	 *                     和 @day(yyMMdd)或@day(yyyyMMdd)、@substr(name,start,length)
	 *                     等
	 * @param keyValues    signature中@case、@substr等引用的属性名称和对应的值
	 * @param bizDate      在signature为空时生效
	 * @param length       产生的业务id总长度
	 * @param sequenceSize id中序列部分的位数
	 * @return 产生的业务主键值
	 */
	protected String generateBizId(String tableName, String signature, Map<String, Object> keyValues, LocalDate bizDate,
			int length, int sequenceSize) {
		return IdUtil.getId(getDistributeIdGenerator(), tableName, signature, keyValues, bizDate, length, sequenceSize);
	}

	/**
	 * 获取所有缓存的名称
	 * 
	 * @return 翻译缓存名称集合
	 */
	protected Set<String> getCacheNames() {
		return this.sqlToyContext.getTranslateManager().getCacheNames();
	}

	/**
	 * 判断缓存是否存在
	 * 
	 * @param cacheName 缓存名称,对应translate配置中的cache属性
	 * @return 缓存已定义并存在返回true,否则返回false
	 */
	protected boolean existCache(String cacheName) {
		return this.sqlToyContext.getTranslateManager().existCache(cacheName);
	}

	/**
	 * 将缓存数据以对象形式获取
	 * 
	 * @param <T>
	 * @param cacheName  缓存名称,对应translate配置中的cache属性
	 * @param cacheType  如是数据字典,则传入字典类型否则为null即可
	 * @param reusltType 返回集合元素的类型,支持VO、HashMap/LinkedHashMap/IgnoreKeyCaseMap等,null则返回缓存原始行数据集合
	 * @return 缓存数据对应的对象集合
	 */
	protected <T> List<T> getTranslateCache(String cacheName, String cacheType, Class<T> reusltType) {
		TranslateConfigModel translateConfig = sqlToyContext.getTranslateManager().getCacheConfig(cacheName);
		if (null == translateConfig) {
			throw new DataAccessException(
					"the cache [" + cacheName + "] used for translate is not defined, please check the configuration!");
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
			throw new DataAccessException("the cache [" + cacheName
					+ "] used for translate has no properties defined, unable to map to VO/POJO/Map objects, please check the configuration!");
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
		return (List<T>) BeanUtil.reflectListToBean(sqlToyContext.getTypeHandler(), cacheData.values(), props, null,
				reusltType);
	}

	/**
	 * 获取缓存数据
	 * 
	 * @param cacheName 缓存名称,对应translate配置中的cache属性
	 * @param cacheType 缓存分类(如字典分类),非分类型的填null
	 * @return 缓存数据,key为主键值,value为该行各属性值组成的数组
	 */
	protected HashMap<String, Object[]> getTranslateCache(String cacheName, String cacheType) {
		return sqlToyContext.getTranslateManager().getCacheData(cacheName, cacheType);
	}

	/**
	 * @see cacheMatchKeys(CacheMatchFilter cacheMatchFilter, String...
	 *      matchRegexes)
	 * @param matchRegex       待匹配的名称关键字
	 * @param cacheMatchFilter 缓存匹配过滤条件,通过CacheMatchFilter.create().cacheName(...)指定缓存
	 * @return 匹配到的缓存key值集合
	 */
	@Deprecated
	protected String[] cacheMatchKeys(String matchRegex, CacheMatchFilter cacheMatchFilter) {
		return cacheMatchKeys(cacheMatchFilter, matchRegex);
	}

	/**
	 * 通过缓存匹配名称并返回key集合(类似数据库中的like)便于后续进行精准匹配
	 * 
	 * @param cacheMatchFilter 例如:
	 *                         CacheMatchFilter.create().cacheName("staffIdNameCache")
	 * @param matchRegexes     待匹配的名称集合,如页面传过来的员工名称、客户名称等，反查对应的员工id和客户id
	 * @return 匹配到的缓存key值集合,最大匹配数量受matchSize限制,未匹配到且设置了unMatchedReturnSelf时原样返回匹配值
	 */
	protected String[] cacheMatchKeys(CacheMatchFilter cacheMatchFilter, String... matchRegexes) {
		if (cacheMatchFilter == null || StringUtil.isBlank(cacheMatchFilter.getCacheFilterArgs().cacheName)
				|| matchRegexes == null || matchRegexes.length == 0) {
			throw new IllegalArgumentException(
					"cache reverse name matching must provide cacheName and matchRegex values!");
		}
		CacheMatchExtend extendArgs = cacheMatchFilter.getCacheFilterArgs();
		// 获取缓存数据
		HashMap<String, Object[]> cacheDatas = getTranslateCache(extendArgs.cacheName, extendArgs.cacheType);
		if (cacheDatas == null || cacheDatas.isEmpty()) {
			logger.error("cache cacheName={},cacheType={} has no data, cacheMatchKeys occurs exception, please check!",
					extendArgs.cacheName, extendArgs.cacheType);
			return new String[] {};
		}
		// 名称匹配在缓存的哪几列(正常1列，但部分场景要求:名称、别名 匹配等)
		int[] nameIndexes = extendArgs.matchIndexs;
		// 将传递匹配条件转小写
		List<String> matchLowAry = new ArrayList<String>();
		for (String str : matchRegexes) {
			matchLowAry.add(str.toLowerCase(Locale.ROOT).trim());
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
					keyLow = keyCode.toLowerCase(Locale.ROOT);
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
							if (compareValue != null
									&& compareValue.toString().toLowerCase(Locale.ROOT).equals(matchStr)) {
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
									&& StringUtil.like(compareValue.toString().toLowerCase(Locale.ROOT), matchWords)) {
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
	 * 利用sqltoy的translate缓存，通过显式调用对集合数据的列进行翻译
	 * 
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
			throw new IllegalArgumentException("cacheName must not be null!");
		}
		if (translateHandler == null) {
			throw new IllegalArgumentException("the translate handler which gets key and sets name must not be null!");
		}
		// 获取缓存,框架会自动判断null并实现缓存数据的加载和更新检测
		TranslateConfigModel cacheModel = sqlToyContext.getTranslateManager().getCacheConfig(cacheName);
		if (cacheModel == null) {
			throw new IllegalArgumentException(
					"cache:{" + cacheName + "} does not exist, please check the cache configuration file!");
		}
		// 默认名称字段列为1
		int cacheIndex = (cacheNameIndex == null) ? 1 : cacheNameIndex.intValue();
		Iterator iter = dataSet.iterator();
		Object row;
		Object key;
		Object name;
		Object[] keyRow;
		// 动态查询缓存数据模式
		if (cacheModel.isDynamicCache()) {
			DynamicCacheFetch dynamicCacheFetch = sqlToyContext.getDynamicCacheFetch();
			if (dynamicCacheFetch == null) {
				throw new RuntimeException(
						"the cache is dynamicCache which fetches data dynamically, no implementation class of DynamicCacheFetch is defined, please configure: spring.sqltoy.dynamicCacheFetch=xxxx.xxx.DynamicCacheFetchImpl");
			}
			HashMap<String, Object[]> cache = sqlToyContext.getDynamicFecthCacheManager().getDynamicCache(cacheModel,
					cacheType);
			// 构建暂停
			Set<String> notMatchedKeySet = new HashSet<>();
			// 逐行翻译
			String keyStr;
			while (iter.hasNext()) {
				row = iter.next();
				if (row != null) {
					// 反调获取需要翻译的key
					key = translateHandler.getKey(row);
					if (key != null) {
						keyStr = key.toString();
						keyRow = cache.get(keyStr);
						if (null == keyRow) {
							notMatchedKeySet.add(keyStr);
						} else {
							name = keyRow[cacheIndex];
							translateHandler.setName(row, (name == null) ? "" : name.toString());
						}
					}
				}
			}
			// 未匹配的key，组织批量查询
			if (!notMatchedKeySet.isEmpty()) {
				String[] notMatchedKeys = notMatchedKeySet.toArray(new String[0]);
				Map<String, Object[]> cacheDatas = dynamicCacheFetch.getCache(cacheName, cacheType, cacheModel.getSid(),
						cacheModel.getProperties(), notMatchedKeys);
				if (cacheDatas != null && !cacheDatas.isEmpty()) {
					// 回写缓存
					cache.putAll(cacheDatas);
					iter = dataSet.iterator();
					// 循环获取行数据
					while (iter.hasNext()) {
						row = iter.next();
						if (row != null) {
							// 反调获取需要翻译的key
							key = translateHandler.getKey(row);
							if (key != null && cacheDatas.containsKey(key)) {
								name = cacheDatas.get(key)[cacheIndex];
								translateHandler.setName(row, (name == null) ? "" : name.toString());
							}
						}
					}
				}
			}
			return;
		}
		HashMap<String, Object[]> cache = getTranslateCache(cacheName, cacheType);
		if (cache == null || cache.isEmpty()) {
			return;
		}
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
	 * 提供针对单表简易快捷查询 EntityQuery.where("#[name like ?]#[and status in
	 * (?)]").values(new Object[]{xxx,xxx})
	 * 
	 * @param <T>
	 * @param entityClass 单表对应的实体类
	 * @param entityQuery 查询条件构造对象(where/values/排序/字段过滤等),可为null表示无条件查询
	 * @return 符合条件的实体对象集合
	 */
	protected <T> List<T> findEntity(Class<T> entityClass, EntityQuery entityQuery) {
		return (List<T>) findEntity(entityClass, entityQuery, entityClass);
	}

	protected <T> List<T> findEntity(Class entityClass, EntityQuery entityQuery, Class<T> resultType) {
		if (null == entityClass) {
			throw new IllegalArgumentException("findEntityList entityClass must not be null!");
		}
		return (List<T>) findEntityBase(entityClass, null, (entityQuery == null) ? EntityQuery.create() : entityQuery,
				resultType, false);
	}

	/**
	 * 提供针对单表简易快捷分页查询 EntityQuery.where("#[name like ?]#[and status in
	 * (?)]").values(new Object[]{xxx,xxx})
	 * 
	 * @param <T>
	 * @param page        分页模型,提供页码pageNo和每页记录数pageSize
	 * @param entityClass 单表对应的实体类
	 * @param entityQuery 查询条件构造对象,可为null表示无条件查询
	 * @return 当前页实体对象组成的Page对象
	 */
	protected <T> Page<T> findPageEntity(Page page, Class<T> entityClass, EntityQuery entityQuery) {
		return (Page<T>) findPageEntity(page, entityClass, entityQuery, entityClass);
	}

	protected <T> Page<T> findPageEntity(Page page, Class entityClass, EntityQuery entityQuery, Class<T> resultType) {
		if (null == entityClass || null == page) {
			throw new IllegalArgumentException("findPageEntity entityClass and page must not be null!");
		}
		return (Page<T>) findEntityBase(entityClass, page, (entityQuery == null) ? EntityQuery.create() : entityQuery,
				resultType, false);
	}

	/**
	 * 提供findEntity的基础实现，供对外接口包装，额外开放了resultClass的自定义功能
	 * 
	 * @param entityClass 单表对应的实体类
	 * @param page        如分页查询则需指定，非分页则传null
	 * @param entityQuery 查询条件构造对象,可为null表示无条件查询
	 * @param resultClass 指定返回结果类型
	 * @param isCount     true表示执行count查询返回总记录数,false则执行数据查询
	 * @return isCount为true时返回总记录数(Long),否则返回List或Page形式的结果
	 */
	private Object findEntityBase(Class entityClass, Page page, EntityQuery entityQuery, Class resultClass,
			boolean isCount) {
		EntityMeta entityMeta = getEntityMeta(entityClass);
		validEntity(entityMeta, entityClass, false);
		EntityQueryExtend innerModel = entityQuery.getInnerModel();
		String translateFields = "";
		// 将缓存翻译对应的查询补充到select column 上,形成select keyColumn as viewColumn 模式
		if (!innerModel.translates.isEmpty()) {
			String keyColumn;
			String compareColumn;
			TranslateExtend extend;
			for (Translate translate : innerModel.translates) {
				extend = translate.getExtend();
				// 将java模式的字段名称转化为数据库字段名称
				keyColumn = entityMeta.getColumnName(extend.keyColumn);
				if (keyColumn == null) {
					keyColumn = extend.keyColumn;
				}

				// 将对象属性名称设置为表字段名称
				if (StringUtil.isNotBlank(extend.compareColumn)) {
					compareColumn = entityMeta.getColumnName(extend.compareColumn);
					if (compareColumn != null) {
						translate.setCompareColumn(compareColumn);
					}
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
			for (String field : entityMeta.getFieldsArray(false)) {
				if (!notSelect.contains(field.toLowerCase(Locale.ROOT))) {
					selectFields.add(field);
				}
			}
			if (selectFields.size() > 0) {
				selectFieldAry = selectFields.toArray(new String[0]);
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
						if (!entityMeta.getColumnFieldMap().containsKey(colName.toLowerCase(Locale.ROOT))) {
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
		// 查询超时时长
		queryExecutor.getInnerModel().timeout = innerModel.timeout;
		// 设置是否空白转null
		queryExecutor.getInnerModel().blankToNull = innerModel.blankToNull;
		// 为后续租户过滤提供判断依据(单表简单sql和对应的实体对象)
		queryExecutor.getInnerModel().entityClass = entityClass;
		// 设置额外的缓存翻译
		if (!innerModel.translates.isEmpty()) {
			queryExecutor.getInnerModel().translates.addAll(innerModel.translates);
		}
		// 设置额外的参数条件过滤
		if (!innerModel.paramFilters.isEmpty()) {
			queryExecutor.getInnerModel().paramFilters.addAll(innerModel.paramFilters);
		}
		// 设置安全脱敏
		if (!innerModel.secureMask.isEmpty()) {
			queryExecutor.getInnerModel().secureMask.putAll(innerModel.secureMask);
		}
		// 设置解密字段
		if (!innerModel.decryptColumns.isEmpty()) {
			queryExecutor.getInnerModel().decryptColumns.addAll(innerModel.decryptColumns);
		}
		// 是否输出sql
		if (innerModel.showSql != null) {
			queryExecutor.showSql(innerModel.showSql);
		}
		// 设置上下文数据
		queryExecutor.contextData(innerModel.contextData);
		// 设置分页优化
		queryExecutor.getInnerModel().pageOptimize = innerModel.pageOptimize;
		queryExecutor.getInnerModel().lockMode = innerModel.lockMode;
		queryExecutor.getInnerModel().lockWaitTimeout = innerModel.lockWaitTimeout;
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
	 * 针对单表对象查询进行更新操作(update和delete 操作filters过滤是无效的，必须是精准的条件参数)
	 * 
	 * @param entityClass  单表对应的实体类
	 * @param entityUpdate 修改构造对象,通过set(property,value)设置修改字段,where/values设置条件
	 * @update 2021-12-23 支持update table set field=field+1等计算模式
	 * @return 实际修改的记录数量
	 */
	protected Long updateByQuery(Class entityClass, EntityUpdate entityUpdate) {
		if (null == entityClass || null == entityUpdate || StringUtil.isBlank(entityUpdate.getInnerModel().where)
				|| StringUtil.isBlank(entityUpdate.getInnerModel().values)
				|| entityUpdate.getInnerModel().updateValues.isEmpty()) {
			throw new IllegalArgumentException(
					"updateByQuery: entityClass, where condition, condition values and update values must not be null!");
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
				throw new IllegalArgumentException(
						"updateByQuery: where condition uses :paramName named parameters, values can only pass a single VO or Map object!");
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
				throw new IllegalArgumentException(
						"updateByQuery: the number of ? in the where statement does not match the length of the values array, please check!");
			}
		}
		// 处理where 中写的java 字段名称为数据库表字段名称
		where = SqlUtil.convertFieldsToColumns(entityMeta, where);
		StringBuilder sql = new StringBuilder();
		sql.append("update ").append(entityMeta.getSchemaTable(null, null)).append(" set ");
		// 对统一更新字段做处理
		IUnifyFieldsHandler unifyHandler = getSqlToyContext().getUnifyFieldsHandler();
		if (unifyHandler != null) {
			Map<String, Object> updateFields = SqlToyThreadDataHolder.useUnifyFields()
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
		if (unifyHandler != null && SqlToyThreadDataHolder.useUnifyFields() && unifyHandler.forceUpdateFields() != null
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
		final String extSign = "SqlToyExtParam";
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
				fieldName = entityMeta.getColumnFieldMap().get(fields[0].trim().toLowerCase(Locale.ROOT));
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
						String[] rightSideNames = SqlConfigParseUtils.getSqlParamsName(fields[1], true);
						// 右侧无命名参数(如field=field+1自增模式),getSqlParamsName返回null,回退属性名+扩展符
						realNames[index] = (rightSideNames == null) ? fieldMeta.getFieldName().concat(extSign)
								: rightSideNames[0];
					}
				} else {
					// 2024-02-20拼接扩展字符，避免where后面有同样的参数名称
					realNames[index] = fieldMeta.getFieldName().concat(extSign);
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
				// field=nvl(?,current_timestamp) 2024-02-20 add .concat(extSign)
				sql.append(columnName).append("=").append(nvlFun).append("(")
						.append(isName ? (":" + fieldMeta.getFieldName().concat(extSign)) : "?").append(",")
						.append(currentTime).append(")");
			} else {
				realValues[index] = entry.getValue();
				if (fields.length == 1) {
					// 2024-02-20 add .concat(extSign)
					sql.append(columnName).append("=")
							.append(isName ? (":" + fieldMeta.getFieldName().concat(extSign)) : "?");
				} else {
					// field=filed+? 类似模式
					fieldSetValue = fields[1];
					sql.append(columnName).append("=");
					if (isName && fieldSetValue.contains("?")) {
						// 2024-02-20 add .concat(extSign)
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
		// where条件是1=1,则跳过条件
		if (!where.replaceAll("\\s", "").equals("1=1")) {
			sql.append(" where ").append(where);
		}
		String sqlStr = sql.toString();
		QueryExecutor queryExecutor = new QueryExecutor(sqlStr).names(realNames).values(realValues);
		queryExecutor.getInnerModel().blankToNull = (innerModel.blankToNull == null)
				? SqlToyConstants.executeSqlBlankToNull
				: innerModel.blankToNull;
		queryExecutor.getInnerModel().showSql = innerModel.showSql;
		queryExecutor.getInnerModel().contextData = innerModel.contextData;
		// 为后续租户过滤提供判断依据(单表简单sql和对应的实体对象)
		queryExecutor.getInnerModel().entityClass = entityClass;
		setEntitySharding(queryExecutor, entityMeta);
		SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(queryExecutor, SqlType.update,
				getDialect(dsDataSource));
		sqlToyConfig.setSqlType(SqlType.update);
		return dialectFactory.executeSql(sqlToyContext, sqlToyConfig, queryExecutor, null, null, dsDataSource);
	}

	/**
	 * 过滤掉无效set的属性
	 * 
	 * @param updateValues       通过set(property,value)设置的待更新字段和值(支持field=field+?计算模式)
	 * @param entityMeta         实体对应的元数据模型,用于校验属性是否为表字段
	 * @param entityClass        实体类,用于异常提示
	 * @param skipNotExistColumn 属性不是表字段(@Column对应)时是否跳过,false则直接抛出IllegalArgumentException
	 * @return 过滤后合法的待更新字段和值(保持原有顺序)
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
				fieldName = entityMeta.getColumnFieldMap().get(fields[0].trim().toLowerCase(Locale.ROOT));
				if (fieldName != null) {
					fieldMeta = entityMeta.getFieldMeta(fieldName);
				}
			}
			if (fieldMeta == null) {
				if (!skipNotExistColumn) {
					throw new IllegalArgumentException("updateByQuery: entity [" + entityClass.getName()
							+ "] property [" + fields[0]
							+ "] is not a database table field (annotated with @Column), please check the code!");
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
			throw new IllegalArgumentException("updateByQuery: entity [" + entityClass.getName()
					+ "], during set(property,value) excluding invalid properties: [" + skipFields
					+ "], there is no valid property to update (annotated with @Column), please check the code!");
		}
		return realUpdateValues;
	}

	/**
	 * 实现POJO和DTO(VO) 之间类型的相互转换和数据复制
	 * 
	 * @param <T>
	 * @param source           源对象
	 * @param resultType       转换后的目标类型
	 * @param ignoreProperties 复制时忽略的属性名称
	 * @return 转换并复制属性值后的目标类型对象
	 */
	protected <T extends Serializable> T convertType(Serializable source, Class<T> resultType,
			String... ignoreProperties) {
		return MapperUtils.map(source, resultType, ignoreProperties);
	}

	/**
	 * 实现POJO和DTO(VO) 集合之间类型的相互转换和数据复制
	 * 
	 * @param <T>
	 * @param sourceList       源对象集合
	 * @param resultType       转换后的目标类型
	 * @param ignoreProperties 复制时忽略的属性名称
	 * @return 转换并复制属性值后的目标类型对象集合
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
	 * 并行查询并返回一维List，有几个查询List中就包含几个结果对象，paramNames和paramValues是全部sql的条件参数的合集
	 * 
	 * @param <T>
	 * @param parallelQueryList 并行查询定义集合,每个ParallQuery包含sql(或sqlId)和返回类型
	 * @param paramNames        全部sql共用的条件参数名称数组
	 * @param paramValues       条件参数值数组,顺序与paramNames一一对应
	 * @return 查询结果集合,顺序与parallelQueryList一致,第n个元素对应第n个查询的结果
	 */
	protected <T> List<QueryResult<T>> parallQuery(List<ParallQuery> parallelQueryList, String[] paramNames,
			Object[] paramValues) {
		return parallQuery(parallelQueryList, paramNames, paramValues, null);
	}

	protected <T> List<QueryResult<T>> parallQuery(List<ParallQuery> parallelQueryList, Map<String, Object> paramsMap,
			ParallelConfig parallelConfig) {
		return parallQuery(parallelQueryList, null, new Object[] { new IgnoreKeyCaseMap(paramsMap) }, parallelConfig);
	}

	/**
	 * 获取表的列信息
	 * 
	 * @param catalog    目录名称,可为null
	 * @param schema     数据库模式(schema)名称,可为null
	 * @param tableName  表名称,支持模糊匹配
	 * @param dataSource 显式指定的数据源,为null时使用默认数据源
	 * @return 表的列信息集合(列名、数据类型、长度、精度等)
	 */
	protected List<ColumnMeta> getTableColumns(final String catalog, final String schema, String tableName,
			DataSource dataSource) {
		return dialectFactory.getTableColumns(sqlToyContext, catalog, schema, tableName, getDataSource(dataSource));
	}

	/**
	 * 获取数据库的表信息
	 * 
	 * @param catalog    目录名称,可为null
	 * @param schema     数据库模式(schema)名称,可为null
	 * @param tableName  表名称,支持模糊匹配
	 * @param dataSource 显式指定的数据源,为null时使用默认数据源
	 * @return 表信息集合(表名、表类型、备注等)
	 */
	protected List<TableMeta> getTables(final String catalog, final String schema, String tableName,
			DataSource dataSource) {
		return dialectFactory.getTables(sqlToyContext, catalog, schema, tableName, getDataSource(dataSource));
	}

	/**
	 * 并行查询并返回一维List，有几个查询List中就包含几个结果对象，paramNames和paramValues是全部sql的条件参数的合集
	 * 
	 * @param parallelQueryList 并行查询定义集合,每个ParallQuery包含sql(或sqlId)和返回类型
	 * @param paramNames        全部sql共用的条件参数名称数组(selfCondition的查询使用各自独立的参数)
	 * @param paramValues       条件参数值数组,顺序与paramNames一一对应
	 * @param parallelConfig    并行查询配置(最大线程数、最大等待时长等),为null时采用默认配置
	 * @return 查询结果集合,顺序与parallelQueryList一致,第n个元素对应第n个查询的结果
	 */
	protected <T> List<QueryResult<T>> parallQuery(List<ParallQuery> parallelQueryList, String[] paramNames,
			Object[] paramValues, ParallelConfig parallelConfig) {
		if (parallelQueryList == null || parallelQueryList.isEmpty()) {
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
		if (parallelQueryList.size() < thread) {
			thread = parallelQueryList.size();
		}
		List<QueryResult<T>> results = new ArrayList<QueryResult<T>>();
		ExecutorService pool = null;
		try {
			pool = Executors.newFixedThreadPool(thread);
			List<Future<ParallelQueryResult>> futureResult = new ArrayList<Future<ParallelQueryResult>>();
			SqlToyConfig sqlToyConfig;
			Future<ParallelQueryResult> future;
			for (ParallQuery query : parallelQueryList) {
				sqlToyConfig = sqlToyContext.getSqlToyConfig(
						new QueryExecutor(query.getExtend().sql).resultType(query.getExtend().resultType),
						SqlType.search, getDialect(query.getExtend().dataSource));
				// 自定义条件参数
				if (query.getExtend().selfCondition) {
					future = pool.submit(new ParallelQueryExecutor(sqlToyContext, dialectFactory, sqlToyConfig, query,
							query.getExtend().names, query.getExtend().values,
							getDataSource(query.getExtend().dataSource, sqlToyConfig)));
				} else {
					future = pool.submit(new ParallelQueryExecutor(sqlToyContext, dialectFactory, sqlToyConfig, query,
							paramNames, paramValues, getDataSource(query.getExtend().dataSource, sqlToyConfig)));
				}
				futureResult.add(future);
			}
			pool.shutdown();
			// 最大等待时长,超时则抛出(由finally统一shutdownNow中断未完成任务),
			// 避免调用线程在后续result.get()上无限期阻塞,maxWaitSeconds形同虚设
			int maxWaitSeconds = (parallConfig.getMaxWaitSeconds() != null) ? parallConfig.getMaxWaitSeconds()
					: SqlToyConstants.PARALLEL_MAXWAIT_SECONDS;
			if (!pool.awaitTermination(maxWaitSeconds, TimeUnit.SECONDS)) {
				throw new RuntimeException("parallel query timed out after waiting [" + maxWaitSeconds
						+ "] seconds, the unfinished tasks have been interrupted!");
			}
			ParallelQueryResult item;
			int index = 0;
			for (Future<ParallelQueryResult> result : futureResult) {
				index++;
				item = result.get();
				// 存在执行异常则整体抛出
				if (item != null && !item.isSuccess()) {
					throw new DataAccessException("the [{}]th sql execution occurs exception:{}!", index,
							item.getMessage());
				}
				results.add(item.getResult());
			}
		} catch (Exception e) {
			logger.error("parallQuery method execution failed", e);
			throw new DataAccessException("parallel query execution error:" + e.getMessage(), e);
		} finally {
			if (pool != null) {
				pool.shutdownNow();
			}
		}
		return results;
	}

	/**
	 * 获取当前数据库方言的名称
	 * 
	 * @param dataSource 用于探测方言的数据源,为null时使用默认数据源
	 * @return 数据库方言名称(如oracle、mysql等),全局配置显式指定了dialect时直接返回配置值
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
	 * 验证实体类操作，对应实体对象是否合法
	 * 
	 * @param entityMeta  实体对应的元数据模型
	 * @param entityClass 实体类,用于异常提示
	 * @param validatePK  是否校验实体必须存在@Id定义的主键
	 */
	private void validEntity(EntityMeta entityMeta, Class entityClass, boolean validatePK) {
		if (entityMeta == null) {
			throw new IllegalArgumentException(
					"Class=[" + entityClass.getName() + "] has no @Entity annotation, it is not a POJO entity object!");
		}
		if (entityMeta.getFieldsArray(false) == null || entityMeta.getFieldsArray(false).length == 0) {
			throw new IllegalArgumentException("Class=[" + entityClass.getName()
					+ "] has no @Column annotation to define the concrete field information!");
		}
		if (validatePK && (entityMeta.getIdArray() == null || entityMeta.getIdArray().length == 0)) {
			throw new IllegalArgumentException(
					"Class=[" + entityClass.getName() + "] has no @Id annotation to define the primary key field!");
		}
	}
}