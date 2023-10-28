package org.sagacity.sqltoy.dao;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.callback.InsertRowCallbackHandler;
import org.sagacity.sqltoy.callback.ReflectPropsHandler;
import org.sagacity.sqltoy.callback.StreamResultHandler;
import org.sagacity.sqltoy.callback.UpdateRowHandler;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlType;
import org.sagacity.sqltoy.executor.QueryExecutor;
import org.sagacity.sqltoy.link.Batch;
import org.sagacity.sqltoy.link.Delete;
import org.sagacity.sqltoy.link.Elastic;
import org.sagacity.sqltoy.link.Execute;
import org.sagacity.sqltoy.link.Load;
import org.sagacity.sqltoy.link.Mongo;
import org.sagacity.sqltoy.link.Query;
import org.sagacity.sqltoy.link.Save;
import org.sagacity.sqltoy.link.Store;
import org.sagacity.sqltoy.link.TreeTable;
import org.sagacity.sqltoy.link.Unique;
import org.sagacity.sqltoy.link.Update;
import org.sagacity.sqltoy.model.CacheMatchFilter;
import org.sagacity.sqltoy.model.ColumnMeta;
import org.sagacity.sqltoy.model.EntityQuery;
import org.sagacity.sqltoy.model.EntityUpdate;
import org.sagacity.sqltoy.model.LockMode;
import org.sagacity.sqltoy.model.PaginationModel;
import org.sagacity.sqltoy.model.ParallQuery;
import org.sagacity.sqltoy.model.ParallelConfig;
import org.sagacity.sqltoy.model.QueryResult;
import org.sagacity.sqltoy.model.StoreResult;
import org.sagacity.sqltoy.model.TableMeta;
import org.sagacity.sqltoy.model.TreeTableModel;
import org.sagacity.sqltoy.translate.TranslateHandler;

/**
 * @project sqltoy-orm
 * @description 提供一个便捷的dao实现,供开发过程中直接通过service调用,避免大量的自定义Dao中仅仅是一些简单的中转调用
 * @author zhongxuchen
 * @version v1.0,Date:2015年11月27日
 * @modify Date:2017-11-28 {增加link链式操作功能,开放全部DaoSupport中的功能}
 * @modify Date:2020-4-23 {对分页查询增加泛型支持}
 * @modify Date:2020-10-20 {增加loadAll(list,lock)}
 */
@SuppressWarnings({ "rawtypes" })
public interface SqlToyLazyDao {

	/**
	 * @TODO 获取sql对应的配置模型
	 * @param sqlKey
	 * @param sqlType
	 * @return SqlToyConfig
	 */
	public SqlToyConfig getSqlToyConfig(String sqlKey, SqlType sqlType);

	/**
	 * @todo 获取实体对象的跟数据库相关的信息
	 * @param entityClass
	 * @return EntityMeta
	 */
	public EntityMeta getEntityMeta(Class entityClass);

	/**
	 * @todo 判断对象属性在数据库中是否唯一
	 * @param entity
	 * @param paramsNamed 对象属性名称(不是数据库表字段名称)
	 * @return boolean true：唯一；false：不唯一
	 */
	public boolean isUnique(Serializable entity, String... paramsNamed);

	/**
	 * @todo 获取符合条件的查询对应的记录数量
	 * @param sqlOrNamedQuery
	 * @param paramsNamed
	 * @param paramsValue
	 * @return Long
	 */
	public Long getCount(String sqlOrNamedQuery, String[] paramsNamed, Object[] paramsValue);

	/**
	 * @TODO 通过map传参获取记录数量
	 * @param sqlOrNamedQuery
	 * @param paramsMap
	 * @return recordCount
	 */
	public Long getCount(String sqlOrNamedQuery, Map<String, Object> paramsMap);

	/**
	 * @TODO 通过POJO产生count语句
	 * @param entityClass
	 * @param entityQuery
	 * @return recordCount
	 */
	public Long getCount(Class entityClass, EntityQuery entityQuery);

	/**
	 * @todo 存储过程调用
	 * @param storeSqlOrKey
	 * @param inParamValues
	 * @return StoreResult
	 */
	public StoreResult executeStore(final String storeSqlOrKey, final Object[] inParamValues);

	/**
	 * @TODO 存储过程调用，outParams可以为null
	 * @param storeSqlOrKey 可以是xml中的sqlId 或者直接{call storeName (?,?)}
	 * @param inParamValues
	 * @param outParamsType 可以为null
	 * @param resultType    可以是VO、Map.class、LinkedHashMap.class、Array.class,null(二维List)
	 * @return StoreResult
	 */
	public StoreResult executeStore(String storeSqlOrKey, Object[] inParamValues, Integer[] outParamsType,
			Class resultType);

	/**
	 * @TODO 流式获取查询结果
	 * @param queryExecutor
	 * @param streamResultHandler
	 */
	public void fetchStream(final QueryExecutor queryExecutor, final StreamResultHandler streamResultHandler);

	/**
	 * @todo 保存对象,并返回主键值
	 * @param serializableVO
	 * @return primaryValue 主键值
	 */
	public Object save(Serializable serializableVO);

	/**
	 * @TODO 批量保存对象，并返回数据更新记录量
	 * @param <T>
	 * @param entities
	 * @return 数据库记录变更量(插入数据量)
	 */
	public <T extends Serializable> Long saveAll(List<T> entities);

	/**
	 * @TODO 批量保存对象并忽视已经存在的记录
	 * @param <T>
	 * @param entities
	 * @return 数据库记录变更量(插入数据量)
	 */
	public <T extends Serializable> Long saveAllIgnoreExist(List<T> entities);

	/**
	 * @todo 批量保存数据,返回数据库记录变更数量
	 * @param dataSet
	 * @param reflectPropsHandler
	 */
	@Deprecated
	public <T extends Serializable> Long saveAll(List<T> entities, ReflectPropsHandler reflectPropsHandler);

	// sqltoy的update内部处理是考虑属性为null情况的，一次交互完成，比jpa更加合理，可以深入了解
	/**
	 * @todo 修改数据并返回数据库记录变更数量(非强制修改属性，当属性值为null不参与修改)
	 * @param serializableVO
	 * @param forceUpdateProps 强制修改的字段属性
	 * @return
	 */
	public Long update(Serializable serializableVO, String... forceUpdateProps);

	/**
	 * @TODO 适用于库存台账、客户资金账等高并发强事务场景，一次数据库交互实现：1、锁查询；2、记录存在则修改；3、记录不存在则执行insert；4、返回修改或插入的记录信息，尽量不要使用identity、sequence主键
	 * @param <T>
	 * @param entity
	 * @param updateRowHandler
	 * @param uniqueProps      唯一性字段，用于做唯一性检索，不设置则按照主键进行查询
	 * @return
	 */
	public <T extends Serializable> T updateSaveFetch(final T entity, final UpdateRowHandler updateRowHandler,
			final String... uniqueProps);

	/**
	 * @TODO 基于对象单表对象查询进行数据更新
	 * @param entityClass
	 * @param entityUpdate
	 * @return
	 */
	public Long updateByQuery(Class entityClass, EntityUpdate entityUpdate);

	/**
	 * @todo 深度修改,不管是否为null全部字段强制修改
	 * @param serializableVO
	 * @return
	 */
	public Long updateDeeply(Serializable serializableVO);

	/**
	 * @todo 修改数据并返回数据库记录变更数量
	 * @param serializableVO
	 * @param forceUpdateProps
	 * @param forceCascadeClasses
	 * @param subTableForceUpdateProps
	 * @return
	 */
	public Long updateCascade(Serializable serializableVO, String[] forceUpdateProps, Class[] forceCascadeClasses,
			HashMap<Class, String[]> subTableForceUpdateProps);

	/**
	 * @TODO 批量修改操作，并可以指定强制修改的属性(非强制修改属性，当属性值为null不参与修改)
	 * @param <T>
	 * @param entities
	 * @param forceUpdateProps 强制修改字段
	 * @return
	 */
	public <T extends Serializable> Long updateAll(List<T> entities, String... forceUpdateProps);

	/**
	 * @todo 批量修改对象并返回数据库记录变更数量
	 * @param entitys
	 * @param reflectPropsHandler 用于通过反射机制设置属性值
	 * @param forceUpdateProps    强制修改的属性
	 * @return
	 */
	public <T extends Serializable> Long updateAll(List<T> entities, ReflectPropsHandler reflectPropsHandler,
			String... forceUpdateProps);

	/**
	 * @TODO 批量深度修改，即全部字段参与修改(包括为null的属性)
	 * @param <T>
	 * @param entities
	 * @return
	 */
	public <T extends Serializable> Long updateAllDeeply(List<T> entities);

	/**
	 * @TODO 批量深度修改，即全部字段参与修改(包括为null的属性)
	 * @param <T>
	 * @param entities
	 * @param reflectPropsHandler (常规情况下传null)
	 * @return
	 */
	@Deprecated
	public <T extends Serializable> Long updateAllDeeply(List<T> entities, ReflectPropsHandler reflectPropsHandler);

	/**
	 * @todo 保存或修改数据并返回数据库记录变更数量
	 * @param serializableVO
	 * @param forceUpdateProps 强制修改的字段
	 * @return
	 */
	public Long saveOrUpdate(Serializable serializableVO, String... forceUpdateProps);

	/**
	 * @TODO 批量保存或修改操作(当已经存在就执行修改)
	 * @param <T>
	 * @param entities
	 * @param forceUpdateProps 强制修改的字段
	 * @return
	 */
	public <T extends Serializable> Long saveOrUpdateAll(List<T> entities, String... forceUpdateProps);

	/**
	 * @todo 批量修改或保存数据并返回数据库记录变更数量
	 * @param <T>
	 * @param entities
	 * @param reflectPropsHandler 常规传null
	 * @param forceUpdateProps    强制修改的字段
	 * @return
	 */
	@Deprecated
	public <T extends Serializable> Long saveOrUpdateAll(List<T> entities, ReflectPropsHandler reflectPropsHandler,
			String... forceUpdateProps);

	/**
	 * @todo 删除单条对象并返回数据库记录影响的数量
	 * @param entity
	 * @return
	 */
	public Long delete(final Serializable entity);

	/**
	 * @todo 批量删除对象并返回数据库记录影响的数量
	 * @param <T>
	 * @param entities
	 * @return
	 */
	public <T extends Serializable> Long deleteAll(final List<T> entities);

	/**
	 * @TODO 基于单表查询进行删除操作,提供在代码中进行快捷操作
	 * @param entityClass
	 * @param entityQuery
	 * @return
	 */
	public Long deleteByQuery(Class entityClass, EntityQuery entityQuery);

	/**
	 * @TODO 提供单主键对象批量删除操作
	 * @param entityClass
	 * @param ids
	 * @return
	 */
	public Long deleteByIds(Class entityClass, Object... ids);

	/**
	 * @todo truncate表
	 * @param entityClass
	 */
	public void truncate(final Class entityClass);

	/**
	 * @todo 根据实体对象的主键值获取对象的详细信息
	 * @param <T>
	 * @param entity
	 * @return
	 */
	public <T extends Serializable> T load(final T entity);

	/**
	 * @todo 根据主键获取对象,提供读取锁设定
	 * @param <T>
	 * @param entity
	 * @param lockMode
	 * @return
	 */
	public <T extends Serializable> T load(final T entity, final LockMode lockMode);

	/**
	 * @todo 指定加载子类的单记录查询
	 * @param <T>
	 * @param entity
	 * @param lockMode
	 * @param cascadeTypes
	 * @return
	 */
	public <T extends Serializable> T loadCascade(final T entity, final LockMode lockMode, final Class... cascadeTypes);

	/**
	 * @todo 根据集合中的主键获取实体的详细信息(底层是批量加载优化了性能,同时控制了in 1000个问题)
	 * @param <T>
	 * @param entities
	 * @return
	 */
	public <T extends Serializable> List<T> loadAll(List<T> entities);

	/**
	 * @todo 提供带锁记录的批量加载功能
	 * @param <T>
	 * @param entities
	 * @param lockMode
	 * @return
	 */
	public <T extends Serializable> List<T> loadAll(List<T> entities, final LockMode lockMode);

	/**
	 * @TODO 加载全表数据(不推荐使用,1、极少全表加载;2、易导致lazyDao接口功能过于宽泛)
	 * @param <T>
	 * @param resultType
	 * @return
	 */
	public <T extends Serializable> List<T> findAll(Class<T> resultType);

	/**
	 * @TODO 通过EntityQuery 组织查询条件对POJO进行单表查询,为代码中进行逻辑处理提供便捷
	 * @param <T>
	 * @param entityClass
	 * @param entityQuery
	 * @return
	 */
	public <T> List<T> findEntity(Class<T> entityClass, EntityQuery entityQuery);

	/**
	 * @TODO 通过entity实体进行查询，但返回结果类型可自行指定
	 * @param <T>
	 * @param entityClass
	 * @param entityQuery
	 * @param resultType
	 * @return
	 */
	public <T> List<T> findEntity(Class entityClass, EntityQuery entityQuery, Class<T> resultType);

	/**
	 * @TODO 单表分页查询
	 * @param <T>
	 * @param paginationModel
	 * @param entityClass
	 * @param entityQuery
	 * @return
	 */
	public <T> PaginationModel<T> findPageEntity(final PaginationModel paginationModel, Class<T> entityClass,
			EntityQuery entityQuery);

	/**
	 * @TODO 基于pojo进行分页查询，并允许指定返回结果的类型
	 * @param <T>
	 * @param page
	 * @param entityClass
	 * @param entityQuery
	 * @param resultType
	 * @return
	 */
	public <T> PaginationModel<T> findPageEntity(final PaginationModel page, Class entityClass, EntityQuery entityQuery,
			Class<T> resultType);

	/**
	 * @todo 选择性的加载子表信息
	 * @param entities
	 * @param cascadeTypes
	 * @return
	 */
	public <T extends Serializable> List<T> loadAllCascade(List<T> entities, final Class... cascadeTypes);

	/**
	 * @TODO 锁住主表记录并级联加载子表数据
	 * @param <T>
	 * @param entities
	 * @param lockMode
	 * @param cascadeTypes
	 * @return
	 */
	public <T extends Serializable> List<T> loadAllCascade(List<T> entities, final LockMode lockMode,
			final Class... cascadeTypes);

	/**
	 * @TODO 根据id集合批量加载对象
	 * @param <T>
	 * @param voClass
	 * @param ids
	 * @return
	 */
	public <T extends Serializable> List<T> loadByIds(final Class<T> voClass, Object... ids);

	/**
	 * @TODO 根据id集合批量加载对象,并加锁
	 * @param <T>
	 * @param voClass
	 * @param lockMode
	 * @param ids
	 * @return
	 */
	public <T extends Serializable> List<T> loadByIds(final Class<T> voClass, final LockMode lockMode, Object... ids);

	/**
	 * @todo 通过sql获取单条记录
	 * @param sqlOrNamedSql 直接代码中写的sql或者xml中定义的sql id
	 * @param paramsNamed
	 * @param paramsValue
	 * @param voClass
	 * @return
	 */
	public <T> T loadBySql(final String sqlOrNamedSql, final String[] paramsNamed, final Object[] paramsValue,
			final Class<T> voClass);

	/**
	 * @TODO 通过map传参模式获取单条对象记录
	 * @param <T>
	 * @param sqlOrNamedSql
	 * @param paramsMap
	 * @param voClass
	 * @return
	 */
	public <T> T loadBySql(final String sqlOrNamedSql, final Map<String, Object> paramsMap, final Class<T> voClass);

	/**
	 * @todo 通过对象实体传参数,框架结合sql中的参数名称来映射对象属性取值
	 * @param sqlOrNamedSql
	 * @param entity
	 * @return
	 */
	public <T extends Serializable> T loadBySql(final String sqlOrNamedSql, final T entity);

	/**
	 * @TODO 通过EntityQuery模式加载单条记录
	 * @param <T>
	 * @param entityClass
	 * @param entityQuery 例如:EntityQuery.create().where("tenantId=? and
	 *                    staffId=?).values("1","S0001")
	 * @return
	 */
	public <T extends Serializable> T loadEntity(Class<T> entityClass, EntityQuery entityQuery);

	/**
	 * @TODO 根据QueryExecutor来链式操作灵活定义查询sql、条件、数据源等
	 * @param query
	 * @return
	 */
	public Object loadByQuery(final QueryExecutor query);

	/**
	 * @TODO 获取查询结果的第一条、第一列的值，一般用select max(x) from 等
	 * @see getSingleValue(final String sqlOrNamedSql, final Map<String, Object>
	 *      paramsMap)
	 * @param sqlOrNamedSql
	 * @param paramsNamed
	 * @param paramsValue
	 * @return
	 */
	@Deprecated
	public Object getSingleValue(final String sqlOrNamedSql, final String[] paramsNamed, final Object[] paramsValue);

	public Object getSingleValue(final String sqlOrNamedSql, final Map<String, Object> paramsMap);

	/**
	 * @TODO 执行类似select field from table 单个字段值
	 * @param <T>
	 * @param sqlOrNamedSql
	 * @param paramsMap
	 * @param resultType    只支持基本类型，如BigDecimal、Double、String、Date、LocalDate等而非Map、VO复杂类型
	 * @return
	 */
	public <T> T getSingleValue(final String sqlOrNamedSql, final Map<String, Object> paramsMap,
			final Class<T> resultType);

	/**
	 * @todo 通过Query构造查询条件进行数据查询
	 * @param query
	 * @return
	 */
	public QueryResult findByQuery(final QueryExecutor query);

	/**
	 * @todo 通过对象传参数,简化paramName[],paramValue[] 模式传参
	 * @param <T>
	 * @param sqlOrNamedSql 可以是具体sql也可以是对应xml中的sqlId
	 * @param entity        通过对象传参数,并按对象类型返回结果
	 * @return
	 */
	public <T extends Serializable> List<T> findBySql(final String sqlOrNamedSql, final T entity);

	/**
	 * @todo 通过给定sql、sql中的参数、参数的数值以及返回结果的对象类型进行条件查询
	 * @param sqlOrSqlId
	 * @param paramsNamed 如果sql是select * from table where xxx=?
	 *                    问号传参模式，paramNamed设置为null
	 * @param paramsValue 对应Named参数的值
	 * @param voClass     返回结果List中的对象类型(可以是VO、null:表示返回List<List>;HashMap.class,Array.class
	 *                    返回List<Object[])
	 * @return
	 */
	public <T> List<T> findBySql(final String sqlOrSqlId, final String[] paramsNamed, final Object[] paramsValue,
			final Class<T> voClass);

	/**
	 * @TODO 提供基于Map传参的查询
	 * @param <T>
	 * @param sqlOrSqlId
	 * @param paramsMap
	 * @param voClass
	 * @return
	 */
	public <T> List<T> findBySql(final String sqlOrSqlId, final Map<String, Object> paramsMap, final Class<T> voClass);

	/**
	 * @TODO 将查询结果直接按二维List返回
	 * @param sqlOrSqlId
	 * @param paramsNamed
	 * @param paramsValue
	 * @return
	 */
	public List findBySql(final String sqlOrSqlId, final String[] paramsNamed, final Object[] paramsValue);

	/**
	 * @todo 根据实体对象获取select * from table 并整合wherePartSql或properties 条件参数进行分页查询
	 * @param pageModel
	 * @param queryExecutor
	 * @return
	 */
	public QueryResult findPageByQuery(final PaginationModel pageModel, final QueryExecutor queryExecutor);

	/**
	 * @todo 普通sql分页查询
	 * @param paginationModel
	 * @param sqlOrNamedSql
	 * @param paramsNamed
	 * @param paramValues
	 * @param voClass         返回结果类型(VO.class,null表示返回二维List,Map.class,LinkedHashMap.class,Array.class)
	 * @return
	 */
	public <T> PaginationModel<T> findPageBySql(final PaginationModel paginationModel, final String sqlOrNamedSql,
			final String[] paramsNamed, final Object[] paramValues, final Class<T> voClass);

	/**
	 * @TODO 提供基于Map传参的分页查询
	 * @param <T>
	 * @param paginationModel
	 * @param sqlOrNamedSql
	 * @param paramsMap
	 * @param voClass
	 * @return
	 */
	public <T> PaginationModel<T> findPageBySql(final PaginationModel paginationModel, final String sqlOrNamedSql,
			final Map<String, Object> paramsMap, final Class<T> voClass);

	/**
	 * @TODO 通过VO对象传参模式的分页，返回结果是VO的集合
	 * @param <T>
	 * @param paginationModel
	 * @param sqlOrNamedSql
	 * @param entity
	 * @return
	 */
	public <T extends Serializable> PaginationModel<T> findPageBySql(final PaginationModel paginationModel,
			final String sqlOrNamedSql, final T entity);

	/**
	 * @TODO 通过条件参数名称和value值模式分页查询，将分页结果按二维List返回
	 * @param paginationModel
	 * @param sqlOrNamedSql
	 * @param paramsNamed
	 * @param paramValues
	 * @return
	 */
	public PaginationModel findPageBySql(final PaginationModel paginationModel, final String sqlOrNamedSql,
			final String[] paramsNamed, final Object[] paramValues);

	public QueryResult findTopByQuery(final QueryExecutor queryExecutor, final double topSize);

	/**
	 * @todo 取记录的前多少条记录
	 * @param sqlOrNamedSql
	 * @param paramsNamed   如果sql是select * from table where xxx=?
	 *                      问号传参模式，paramNamed设置为null
	 * @param paramValues
	 * @param voClass       返回结果List中的对象类型(可以是VO、null:表示返回List<List>;HashMap.class)
	 * @param topSize       (大于1则取固定数量的记录，小于1，则表示按比例提取)
	 * @return
	 */
	public <T> List<T> findTopBySql(final String sqlOrNamedSql, final String[] paramsNamed, final Object[] paramValues,
			final Class<T> voClass, final double topSize);

	/**
	 * @TODO 提供基于Map传参的top查询
	 * @param <T>
	 * @param sqlOrNamedSql
	 * @param paramsMap
	 * @param voClass
	 * @param topSize
	 * @return
	 */
	public <T> List<T> findTopBySql(final String sqlOrNamedSql, final Map<String, Object> paramsMap,
			final Class<T> voClass, final double topSize);

	/**
	 * @todo 基于对象传参数模式(内部会根据sql中的参数提取对象对应属性的值),并返回对象对应类型的List
	 * @param <T>
	 * @param sqlOrNamedSql
	 * @param entity
	 * @param topSize       (大于1则取固定数量的记录，小于1，则表示按比例提取)
	 * @return
	 */
	public <T extends Serializable> List<T> findTopBySql(final String sqlOrNamedSql, final T entity,
			final double topSize);

	public QueryResult getRandomResult(final QueryExecutor queryExecutor, final double randomCount);

	/**
	 * @TODO 通过对象传参模式取随机记录
	 * @param <T>
	 * @param sqlOrNamedSql
	 * @param entity
	 * @param randomCount   小于1表示按比例提取，大于1则按整数部分提取记录数量
	 * @return
	 */
	public <T extends Serializable> List<T> getRandomResult(final String sqlOrNamedSql, final T entity,
			final double randomCount);

	public <T> List<T> getRandomResult(final String sqlOrNamedSql, final String[] paramsNamed,
			final Object[] paramsValue, final Class<T> voClass, final double randomCount);

	/**
	 * @TODO 提供基于Map传参的随机记录查询
	 * @param <T>
	 * @param sqlOrNamedSql
	 * @param paramsMap
	 * @param voClass
	 * @param randomCount
	 * @return
	 */
	public <T> List<T> getRandomResult(final String sqlOrNamedSql, final Map<String, Object> paramsMap,
			final Class<T> voClass, final double randomCount);

	/**
	 * @TODO 批量集合通过sql进行修改操作,调用:batchUpdate(sqlId,List)
	 * @param sqlOrNamedSql
	 * @param dataSet
	 * @return
	 */
	public Long batchUpdate(final String sqlOrNamedSql, final List dataSet);

	/**
	 * @todo 批量集合通过sql进行修改操作,调用:batchUpdate(sqlId,List,null,null)
	 *       <p>
	 *       <li>1、VO传参模式，即:batchUpdate(sql,List<VO> dataSet),sql中用:paramName</li>
	 *       <li>2、List<List>模式，sql中直接用? 形式传参,弊端就是严格顺序</li>
	 *       </p>
	 * @param sqlOrNamedSql
	 * @param dataSet           支持List<List>、List<Object[]>、List<VO>、List<Map> 集中模式
	 * @param insertCallhandler (一般为null)
	 * @param autoCommit        (一般为null)
	 */
	public Long batchUpdate(final String sqlOrNamedSql, final List dataSet,
			final InsertRowCallbackHandler insertCallhandler, final Boolean autoCommit);

	// sqltoy的updateFetch是jpa没有的，可以深入了解其原理，一次交互完成查询、锁定、修改并返回修改后结果
	/**
	 * @todo 获取并锁定数据并进行修改(只支持针对单表查询，查询语句要简单)
	 * @param queryExecutor
	 * @param updateRowHandler
	 * @return
	 */
	public List updateFetch(final QueryExecutor queryExecutor, final UpdateRowHandler updateRowHandler);

	/**
	 * @todo 执行sql,并返回被修改的记录数量
	 * @param sqlOrNamedSql
	 * @param entity
	 * @return
	 */
	public Long executeSql(final String sqlOrNamedSql, final Serializable entity);

	/**
	 * @TODO 通过数组传参执行sql,并返回更新记录量
	 * @param sqlOrNamedSql
	 * @param paramsNamed
	 * @param paramsValue
	 * @return
	 */
	public Long executeSql(final String sqlOrNamedSql, final String[] paramsNamed, final Object[] paramsValue);

	/**
	 * @TODO 提供基于Map传参的sql执行
	 * @param sqlOrNamedSql
	 * @param paramsMap
	 * @return
	 */
	public Long executeSql(final String sqlOrNamedSql, final Map<String, Object> paramsMap);

	/**
	 * @todo 构造树形表的节点路径、层次等级、是否叶子节点等必要信息
	 * @param treeTableModel
	 * @return
	 */
	public boolean wrapTreeTableRoute(final TreeTableModel treeTableModel);

	/**
	 * @TODO 数据库提交(针对特殊场景使用,正常情况下此方法不要使用)
	 */
	public void flush();

	/**
	 * @TODO es操作
	 * @return
	 */
	public Elastic elastic();

	/**
	 * @TODO mongo操作
	 * @return
	 */
	public Mongo mongo();

	/**
	 * @TODO 获取sqltoy的上下文
	 * @return
	 */
	public SqlToyContext getSqlToyContext();

	/**
	 * @TODO 获取当前dataSource
	 * @return
	 */
	public DataSource getDataSource();

	/**
	 * @todo 获取业务ID(当一个表里面涉及多个业务主键时，sqltoy在配置层面只支持单个，但开发者可以调用此方法自行获取后赋值)
	 * @param signature 唯一标识符号
	 * @param increment 增量
	 * @return
	 */
	public long generateBizId(String signature, int increment);

	/**
	 * @todo 根据实体对象对应的POJO配置的业务主键策略,提取对象的属性值产生业务主键
	 * @param entity
	 * @return
	 */
	public String generateBizId(Serializable entity);

	/**
	 * @todo 获取sqltoy中用于翻译的缓存,方便用于页面下拉框选项、checkbox选项、suggest组件等
	 * @param cacheName
	 * @param elementId 如是数据字典,则为字典类型否则为null即可
	 * @return
	 */
	public HashMap<String, Object[]> getTranslateCache(String cacheName, String elementId);

	/**
	 * @TODO 将缓存数据以对象形式获取
	 * @param <T>
	 * @param cacheName
	 * @param cacheType  如是数据字典,则传入字典类型否则为null即可
	 * @param reusltType
	 * @return
	 */
	public <T> List<T> getTranslateCache(String cacheName, String cacheType, Class<T> reusltType);

	/**
	 * @TODO 通过反调对集合数据进行翻译处理
	 * @param dataSet
	 * @param cacheName
	 * @param handler
	 */
	public void translate(Collection dataSet, String cacheName, TranslateHandler handler);

	/**
	 * @todo 对记录通过反调自定义对那个属性进行翻译
	 * @param dataSet
	 * @param cacheName
	 * @param cacheType
	 * @param cacheNameIndex
	 * @param handler
	 */
	public void translate(Collection dataSet, String cacheName, String cacheType, Integer cacheNameIndex,
			TranslateHandler handler);

	/**
	 * @todo 判断缓存是否存在
	 * @param cacheName
	 * @return
	 */
	public boolean existCache(String cacheName);

	/**
	 * @todo 获取所有缓存的名称
	 * @return
	 */
	public Set<String> getCacheNames();

	/**
	 * @TODO 通过缓存将名称进行模糊匹配取得key的集合
	 * @param matchRegex
	 * @param cacheMatchFilter 例如:
	 *                         CacheMatchFilter.create().cacheName("staffIdNameCache")
	 * @return
	 */
	@Deprecated
	public String[] cacheMatchKeys(String matchRegex, CacheMatchFilter cacheMatchFilter);

	/**
	 * update 2022-12-15 支持数组
	 * @TODO 通过缓存将名称进行模糊匹配取得key的集合
	 * @param cacheMatchFilter
	 * @param matchRegexes 数组
	 * @return
	 */
	public String[] cacheMatchKeys(CacheMatchFilter cacheMatchFilter, String... matchRegexes);

	/**
	 * @TODO 实现VO和POJO之间属性值的复制
	 * @param <T>
	 * @param source
	 * @param resultType
	 * @param ignoreProperties
	 * @return
	 * @throws Exception
	 */
	public <T extends Serializable> T convertType(Serializable source, Class<T> resultType, String... ignoreProperties);

	/**
	 * @TODO 实现VO和POJO 集合之间属性值的复制
	 * @param <T>
	 * @param sourceList
	 * @param resultType
	 * @param ignoreProperties
	 * @return
	 */
	public <T extends Serializable> List<T> convertType(List sourceList, Class<T> resultType,
			String... ignoreProperties);

	/**
	 * @TODO 实现分页对象的类型转换
	 * @param <T>
	 * @param sourcePage
	 * @param resultType
	 * @param ignoreProperties
	 * @return
	 */
	public <T extends Serializable> PaginationModel<T> convertType(PaginationModel sourcePage, Class<T> resultType,
			String... ignoreProperties);

	// parallQuery 面向查询(不要用于事务操作过程中),sqltoy提供强大的方法，但是否恰当使用需要使用者做合理的判断
	/**
	 * -- 避免开发者将全部功能用一个超级sql完成，提供拆解执行的同时确保执行效率，达到了效率和可维护的平衡
	 * 
	 * @TODO 并行查询并返回一维List，有几个查询List中就包含几个结果对象，paramNames和paramValues是全部sql的条件参数的合集
	 * @param parallQueryList
	 * @param paramNames
	 * @param paramValues
	 * @return
	 */
	public <T> List<QueryResult<T>> parallQuery(List<ParallQuery> parallQueryList, String[] paramNames,
			Object[] paramValues);

	/**
	 * @TODO 并行查询并返回一维List，有几个查询List中就包含几个结果对象，paramNames和paramValues是全部sql的条件参数的合集
	 * @param parallQueryList
	 * @param paramNames
	 * @param paramValues
	 * @param parallelConfig
	 * @return
	 */
	public <T> List<QueryResult<T>> parallQuery(List<ParallQuery> parallQueryList, String[] paramNames,
			Object[] paramValues, ParallelConfig parallelConfig);

	/**
	 * @TODO 提供基于Map传参的并行查询
	 * @param <T>
	 * @param parallQueryList
	 * @param paramsMap
	 * @return
	 */
	public <T> List<QueryResult<T>> parallQuery(List<ParallQuery> parallQueryList, Map<String, Object> paramsMap);

	/**
	 * @TODO 提供基于Map传参的并行查询,并提供并行线程数、最大等待时长等参数设置
	 * @param <T>
	 * @param parallQueryList
	 * @param paramsMap
	 * @param parallelConfig  例如:ParallelConfig.create().maxThreads(20)
	 * @return
	 */
	public <T> List<QueryResult<T>> parallQuery(List<ParallQuery> parallQueryList, Map<String, Object> paramsMap,
			ParallelConfig parallelConfig);

	/** ------- 链式操作，功能就是上面参数直传模式的用链式赋值的封装(优雅但易遗漏赋值)，根据情况使用 ---- */

	/**
	 * @TODO 提供链式操作模式删除操作集合
	 * 
	 * @return
	 */
	public Delete delete();

	/**
	 * @TODO 提供链式操作模式修改操作集合
	 * @return
	 */
	public Update update();

	/**
	 * @TODO 提供链式操作模式存储过程操作集合
	 * @return
	 */
	public Store store();

	/**
	 * @TODO 提供链式操作模式保存操作集合
	 * @return
	 */
	public Save save();

	/**
	 * @TODO 提供链式操作模式查询操作集合
	 * @return
	 */
	public Query query();

	/**
	 * @TODO 提供链式操作模式对象加载操作集合
	 * @return
	 */
	public Load load();

	/**
	 * @TODO 提供链式操作模式唯一性验证操作集合
	 * 
	 * @return
	 */
	public Unique unique();

	/**
	 * @TODO 提供链式操作模式树形表结构封装操作集合
	 * 
	 * @return
	 */
	public TreeTable treeTable();

	/**
	 * @TODO 提供链式操作模式sql语句直接执行修改数据库操作集合
	 * 
	 * @return
	 */
	public Execute execute();

	/**
	 * @TODO 提供链式操作模式批量执行操作集合
	 * 
	 * @return
	 */
	public Batch batch();

	/**
	 * @TODO 获得表的字段信息
	 * @param catalog
	 * @param schema
	 * @param tableName
	 * @return
	 */
	public List<ColumnMeta> getTableColumns(final String catalog, final String schema, final String tableName);

	/**
	 * @TODO 获得数据库的表信息
	 * @param catalog
	 * @param schema
	 * @param tableName
	 * @return
	 */
	public List<TableMeta> getTables(final String catalog, final String schema, final String tableName);
}
