package org.sagacity.sqltoy.dao;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.callback.StreamResultHandler;
import org.sagacity.sqltoy.callback.UpdateRowHandler;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlType;
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
import org.sagacity.sqltoy.model.Page;
import org.sagacity.sqltoy.model.ParallQuery;
import org.sagacity.sqltoy.model.ParallelConfig;
import org.sagacity.sqltoy.model.QueryExecutor;
import org.sagacity.sqltoy.model.QueryResult;
import org.sagacity.sqltoy.model.StoreResult;
import org.sagacity.sqltoy.model.TableMeta;
import org.sagacity.sqltoy.model.TreeTableModel;
import org.sagacity.sqltoy.translate.TranslateHandler;

/**
 * @project sqltoy-orm
 * @description 提供一个便捷的dao实现,供开发过程中直接通过service调用,避免大量的自定义Dao中仅仅是一些简单的中转调用
 * @see 因SqlToyLazyDao是一开始逐步迭代的结果，为兼容历史项目，api存在命名规则统一性差的因素，因此重新创建了 LightDao
 * @author zhongxuchen
 * @version v1.0,Date:2015年11月27日
 * @modify Date:2017-11-28 {增加link链式操作功能,开放全部SqlToyDaoSupport中的功能}
 * @modify Date:2020-4-23 {对分页查询增加泛型支持}
 * @modify Date:2020-10-20 {增加loadAll(list,lock)}
 */
@SuppressWarnings({ "rawtypes" })
public interface SqlToyLazyDao {

	/**
	 * @TODO 获取sql对应的配置模型
	 * @param sqlKey  对应sqlId
	 * @param sqlType SqlType.search或传null
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
	 * @param sqlOrSqlId
	 * @param paramsNamed
	 * @param paramsValue
	 * @return Long 查询符合条件的记录数量
	 */
	public Long getCount(String sqlOrSqlId, String[] paramsNamed, Object[] paramsValue);

	/**
	 * @TODO 通过map传参获取记录数量
	 * @param sqlOrSqlId
	 * @param paramsMap
	 * @return Long 查询符合条件的记录数量
	 */
	public Long getCount(String sqlOrSqlId, Map<String, Object> paramsMap);

	/**
	 * @TODO 通过POJO产生count语句
	 * @param entityClass
	 * @param entityQuery 例如:EntityQuery.create().where("status=:status").names("status").values(1)
	 * @return Long 查询符合条件的记录数量
	 */
	public Long getCount(Class entityClass, EntityQuery entityQuery);

	/**
	 * @todo 存储过程调用
	 * @param storeSqlOrKey 可以是xml中的sqlId 或者直接{call storeName (?,?)}
	 * @param inParamValues
	 * @return StoreResult 用:getRows()获得查询结果
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
	 * @TODO 存储过程调用，outParams可以为null
	 * @param storeSqlOrKey 可以是xml中的sqlId 或者直接{call storeName (?,?)}
	 * @param inParamValues
	 * @param outParamsType 可以为null
	 * @param resultTypes   可以是VO、Map.class、LinkedHashMap.class、Array.class,null(二维List)
	 * @return StoreResult
	 */
	public StoreResult executeMoreResultStore(String storeSqlOrKey, Object[] inParamValues, Integer[] outParamsType,
			Class... resultTypes);

	/**
	 * @TODO 流式获取查询结果
	 * @param queryExecutor
	 * @param streamResultHandler
	 */
	public void fetchStream(final QueryExecutor queryExecutor, final StreamResultHandler streamResultHandler);

	/**
	 * @todo 保存对象,并返回主键值
	 * @param entity
	 * @return Object 返回主键值
	 */
	public Object save(Serializable entity);

	/**
	 * @TODO 批量保存对象，并返回数据更新记录量
	 * @param <T>
	 * @param entities
	 * @return Long 数据库发生变更的记录量
	 */
	public <T extends Serializable> Long saveAll(List<T> entities);

	/**
	 * @TODO 批量保存对象并忽视已经存在的记录
	 * @param <T>
	 * @param entities
	 * @return Long 数据库发生变更的记录量
	 */
	public <T extends Serializable> Long saveAllIgnoreExist(List<T> entities);

	/**
	 * @todo 修改数据并返回数据库记录变更数量(非强制修改属性，当属性值为null不参与修改)
	 * @param entity
	 * @param forceUpdateProps 强制修改的字段属性
	 * @return Long 数据库发生变更的记录量
	 */
	public Long update(Serializable entity, String... forceUpdateProps);

	/**
	 * @TODO 适用于库存台账、客户资金账等高并发强事务场景，一次数据库交互实现：
	 *       <p>
	 *       <li>1、锁查询；</li>
	 *       <li>2、记录存在则修改；</li>
	 *       <li>3、记录不存在则执行insert；</li>
	 *       <li>4、返回修改或插入的记录信息</li>
	 *       </p>
	 * @param <T>
	 * @param entity           尽量不要使用identity、sequence主键
	 * @param updateRowHandler
	 * @param uniqueProps      唯一性字段，用于做唯一性检索，不设置则按照主键进行查询
	 * @return
	 */
	public <T extends Serializable> T updateSaveFetch(final T entity, final UpdateRowHandler updateRowHandler,
			final String... uniqueProps);

	/**
	 * @todo 深度修改,不管是否为null全部字段强制修改
	 * @param serializableVO
	 * @return Long 数据库发生变更的记录量
	 */
	public Long updateDeeply(Serializable serializableVO);

	/**
	 * @TODO 基于对象单表对象查询进行数据更新
	 * @param entityClass
	 * @param entityUpdate 例如:EntityUpdate.create().set("createBy",
	 *                     "S0001").where("staffName like ?").values("张")
	 * @return Long 数据库发生变更的记录量
	 */
	public Long updateByQuery(Class entityClass, EntityUpdate entityUpdate);

	/**
	 * @todo 级联修改数据并返回数据库记录变更数量
	 * @param entity
	 * @param forceUpdateProps
	 * @param forceCascadeClasses
	 * @param subTableForceUpdateProps
	 * @return Long 数据库发生变更的记录量
	 */
	public Long updateCascade(Serializable entity, String[] forceUpdateProps, Class[] forceCascadeClasses,
			HashMap<Class, String[]> subTableForceUpdateProps);

	/**
	 * @TODO 批量修改操作，并可以指定强制修改的属性(非强制修改属性，当属性值为null不参与修改)
	 * @param <T>
	 * @param entities
	 * @param forceUpdateProps
	 * @return Long 数据库发生变更的记录量
	 */
	public <T extends Serializable> Long updateAll(List<T> entities, String... forceUpdateProps);

	/**
	 * @TODO 批量深度修改，即全部字段参与修改(包括为null的属性)
	 * @param <T>
	 * @param entities
	 * @return Long 数据库发生变更的记录量
	 */
	public <T extends Serializable> Long updateAllDeeply(List<T> entities);

	/**
	 * @todo 保存或修改数据并返回数据库记录变更数量
	 * @param entity
	 * @param forceUpdateProps 强制修改的字段
	 * @return Long 数据库发生变更的记录量
	 */
	public Long saveOrUpdate(Serializable entity, String... forceUpdateProps);

	/**
	 * @TODO 批量保存或修改操作(当已经存在就执行修改)
	 * @param <T>
	 * @param entities
	 * @param forceUpdateProps 强制修改的字段
	 * @return Long 数据库发生变更的记录量
	 */
	public <T extends Serializable> Long saveOrUpdateAll(List<T> entities, String... forceUpdateProps);

	/**
	 * @todo 删除单条对象并返回数据库记录影响的数量
	 * @param entity
	 * @return Long 数据库发生变更的记录量(删除数据量)
	 */
	public Long delete(final Serializable entity);

	/**
	 * @todo 批量删除对象并返回数据库记录影响的数量
	 * @param entities
	 * @return Long 数据库记录变更量(删除数据量)
	 */
	public <T extends Serializable> Long deleteAll(final List<T> entities);

	/**
	 * @TODO 根据id集合批量删除
	 * @param entityClass
	 * @param ids
	 * @return
	 */
	public Long deleteByIds(Class entityClass, Object... ids);

	/**
	 * @TODO 基于单表查询进行删除操作,提供在代码中进行快捷操作
	 * @param entityClass
	 * @param entityQuery 例如:EntityQuery.create().where("status=?").values(0)
	 * @return Long 数据库记录变更量(插入数据量)
	 */
	public Long deleteByQuery(Class entityClass, EntityQuery entityQuery);

	/**
	 * @todo truncate 刪除全表记录,通过entityClass获得表名
	 * @param entityClass
	 */
	public void truncate(final Class entityClass);

	/**
	 * @todo 根据实体对象的主键值获取对象的详细信息
	 * @param entity
	 * @return entity
	 */
	public <T extends Serializable> T load(final T entity);

	/**
	 * @todo 根据主键获取对象,提供读取锁设定
	 * @param entity
	 * @param lockMode LockMode.UPGRADE 或LockMode.UPGRADE_NOWAIT等
	 * @return entity
	 */
	public <T extends Serializable> T load(final T entity, final LockMode lockMode);

	/**
	 * @todo 对象加载同时指定加载子类，实现级联加载
	 * @param entity
	 * @param lockMode
	 * @param cascadeTypes
	 * @return entity
	 */
	public <T extends Serializable> T loadCascade(final T entity, final LockMode lockMode, final Class... cascadeTypes);

	/**
	 * @todo 根据集合中的主键获取实体的详细信息(底层是批量加载优化了性能,同时控制了in 1000个问题)
	 * @param entities
	 * @return entities
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
	 * @TODO 通过EntityQuery模式加载单条记录
	 * @param <T>
	 * @param entityClass
	 * @param entityQuery 例如:EntityQuery.create().select(a,b,c).where("tenantId=?
	 *                    and staffId=?").values("1","S0001")
	 * @return
	 */
	public <T extends Serializable> T loadEntity(Class<T> entityClass, EntityQuery entityQuery);

	public <T extends Serializable> T loadEntity(Class entityClass, EntityQuery entityQuery, Class<T> resultType);

	/**
	 * @TODO 通过EntityQuery 组织查询条件对POJO进行单表查询,为代码中进行逻辑处理提供便捷
	 *       <li>如果要查询整个表记录:findEntity(entityClass,null) 即可</li>
	 * @param <T>
	 * @param entityClass
	 * @param entityQuery EntityQuery.create().where("status=:status #[and staffName
	 *                    like
	 *                    :staffName]").names("status","staffName").values(1,null).orderBy()
	 *                    链式设置查询逻辑
	 * @return
	 */
	public <T> List<T> findEntity(Class<T> entityClass, EntityQuery entityQuery);

	/**
	 * @TODO 通过entity实体进行查询，但返回结果类型可自行指定
	 * @param <T>
	 * @param entityClass
	 * @param entityQuery
	 * @param resultType  指定返回结果类型
	 * @return
	 */
	public <T> List<T> findEntity(Class entityClass, EntityQuery entityQuery, Class<T> resultType);

	/**
	 * @TODO 单表分页查询
	 *       <p>
	 *       1、对象传参: findPageEntity(new
	 *       Page(),StaffInfo.class,EntityQuery.create().where("status=:status").values(staffInfo))
	 *       2、数组传参: findPageEntity(new
	 *       Page(),StaffInfo.class,EntityQuery.create().where("status=?").values(1))
	 *       <p>
	 * @param <T>
	 * @param page
	 * @param entityClass
	 * @param entityQuery
	 * @return
	 */
	public <T> Page<T> findPageEntity(final Page page, Class<T> entityClass, EntityQuery entityQuery);

	/**
	 * @TODO 单表分页查询，同时可以指定返回对象类型为非实体对象
	 * @param <T>
	 * @param page
	 * @param entityClass
	 * @param entityQuery
	 * @param resultType
	 * @return
	 */
	public <T> Page<T> findPageEntity(final Page page, Class entityClass, EntityQuery entityQuery, Class<T> resultType);

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
	 * @param entityClass
	 * @param ids
	 * @return
	 */
	public <T extends Serializable> List<T> loadByIds(final Class<T> entityClass, Object... ids);

	/**
	 * @TODO 根据id集合批量加载对象,并加锁
	 * @param <T>
	 * @param entityClass
	 * @param lockMode
	 * @param ids
	 * @return
	 */
	public <T extends Serializable> List<T> loadByIds(final Class<T> entityClass, final LockMode lockMode,
			Object... ids);

	/**
	 * @todo 通过sql获取单条记录
	 * @param sqlOrSqlId  直接代码中写的sql或者xml中定义的sql id
	 * @param paramsNamed
	 * @param paramsValue
	 * @param resultType  可以是vo、dto、Map(默认驼峰命名)
	 * @return
	 */
	public <T> T loadBySql(final String sqlOrSqlId, final String[] paramsNamed, final Object[] paramsValue,
			final Class<T> resultType);

	/**
	 * @TODO 通过map传参模式获取单条对象记录
	 * @param <T>
	 * @param sqlOrSqlId 可以直接传sql语句，也可以是xml中定义的sql id
	 * @param paramsMap
	 * @param resultType 可以是vo、dto、Map(默认驼峰命名)
	 * @return
	 */
	public <T> T loadBySql(final String sqlOrSqlId, final Map<String, Object> paramsMap, final Class<T> resultType);

	/**
	 * @todo 通过对象实体传参数,框架结合sql中的参数名称来映射对象属性取值
	 * @param sqlOrSqlId
	 * @param entity
	 * @return
	 */
	public <T extends Serializable> T loadBySql(final String sqlOrSqlId, final T entity);

	/**
	 * @TODO 根据QueryExecutor来链式操作灵活定义查询sql、条件、数据源等
	 * @param query new QueryExecutor(sql).names().values().filters() 链式设置查询
	 * @return
	 */
	public Object loadByQuery(final QueryExecutor query);

	/**
	 * @TODO 获取查询结果的第一条、第一列的值，一般用select max(x) from 等
	 * @param sqlOrSqlId
	 * @param paramsNamed
	 * @param paramsValue
	 * @return
	 * @see #getSingleValue(String, Map)
	 */
	@Deprecated
	public Object getSingleValue(final String sqlOrSqlId, final String[] paramsNamed, final Object[] paramsValue);

	/*
	 * @see getSingleValue(final String sqlOrSqlId, final String[] paramsNamed,
	 * final Object[] paramsValue)
	 */
	public Object getSingleValue(final String sqlOrSqlId, final Map<String, Object> paramsMap);

	/**
	 * @TODO 获取查询结果的第一条、第一列的值，一般用select max(x) from 等
	 * @param <T>
	 * @param sqlOrSqlId
	 * @param paramsMap
	 * @param resultType
	 * @return
	 */
	public <T> T getSingleValue(final String sqlOrSqlId, final Map<String, Object> paramsMap,
			final Class<T> resultType);

	/**
	 * @todo 通过Query构造查询条件进行数据查询
	 * @param query 范例:new QueryExecutor(sql).names(xxx).values(xxx).filters()
	 *              链式设置查询
	 * @return
	 */
	public QueryResult findByQuery(final QueryExecutor query);

	/**
	 * @todo 通过对象传参数,简化paramName[],paramValue[] 模式传参
	 * @param <T>
	 * @param sqlOrSqlId 可以是具体sql也可以是对应xml中的sqlId
	 * @param entity     通过对象传参数,并按对象类型返回结果
	 * @return
	 */
	public <T extends Serializable> List<T> findBySql(final String sqlOrSqlId, final T entity);

	/**
	 * @todo 通过给定sql、sql中的参数、参数的数值以及返回结果的对象类型进行条件查询
	 * @param sqlOrSqlId
	 * @param paramsNamed 如果sql是select * from table where xxx=?
	 *                    问号传参模式，paramNamed设置为null
	 * @param paramsValue 对应Named参数的值
	 * @param resultType  返回结果List中的对象类型(可以是VO、null:表示返回List<List>;HashMap.class(驼峰命名),Array.class
	 *                    返回List<Object[])
	 * @return
	 */
	public <T> List<T> findBySql(final String sqlOrSqlId, final String[] paramsNamed, final Object[] paramsValue,
			final Class<T> resultType);

	/**
	 * @TODO 提供基于Map传参的查询5.1.34+ 开始支持 findBySql("select 单列 from
	 *       table",map,Integer.class) 返回单列值的一维数组
	 * @param <T>
	 * @param sqlOrSqlId
	 * @param paramsMap
	 * @param resultType 可以是vo、dto、Map(默认驼峰命名)
	 * @return
	 */
	public <T> List<T> findBySql(final String sqlOrSqlId, final Map<String, Object> paramsMap,
			final Class<T> resultType);

	/**
	 * @TODO 将查询结果直接按二维List返回
	 * @param sqlOrSqlId
	 * @param paramsNamed
	 * @param paramsValue
	 * @return
	 */
	public List findBySql(final String sqlOrSqlId, final String[] paramsNamed, final Object[] paramsValue);

	/**
	 * @todo 通过QueryExecutor来构造查询逻辑进行分页查询
	 * @param page
	 * @param queryExecutor 范例:new
	 *                      QueryExecutor(sql).names(xxx).values(xxx).filters()
	 *                      链式设置查询
	 * @return
	 */
	public QueryResult findPageByQuery(final Page page, final QueryExecutor queryExecutor);

	/**
	 * @todo 普通sql分页查询
	 * @param page
	 * @param sqlOrSqlId
	 * @param paramsNamed
	 * @param paramValues
	 * @param resultType  返回结果类型(VO.class,null表示返回二维List,Map.class(驼峰命名),LinkedHashMap.class,Array.class)
	 * @return
	 */
	public <T> Page<T> findPageBySql(final Page page, final String sqlOrSqlId, final String[] paramsNamed,
			final Object[] paramValues, final Class<T> resultType);

	/**
	 * @TODO 提供基于Map传参的分页查询
	 * @param <T>
	 * @param page
	 * @param sqlOrSqlId
	 * @param paramsMap
	 * @param resultType 可以是vo、dto、Map(默认驼峰命名)
	 * @return
	 */
	public <T> Page<T> findPageBySql(final Page page, final String sqlOrSqlId, final Map<String, Object> paramsMap,
			final Class<T> resultType);

	/**
	 * @TODO 通过VO对象传参模式的分页，返回结果是VO类型的集合
	 * @param <T>
	 * @param page
	 * @param sqlOrSqlId
	 * @param entity
	 * @return
	 */
	public <T extends Serializable> Page<T> findPageBySql(final Page page, final String sqlOrSqlId, final T entity);

	/**
	 * @TODO 通过条件参数名称和value值模式分页查询，将分页结果按二维List返回
	 * @param page
	 * @param sqlOrSqlId
	 * @param paramsNamed
	 * @param paramValues
	 * @return
	 */
	public Page findPageBySql(final Page page, final String sqlOrSqlId, final String[] paramsNamed,
			final Object[] paramValues);

	/**
	 * @todo 取记录的前多少条记录
	 * @param sqlOrSqlId
	 * @param paramsNamed 如果sql是select * from table where xxx=?
	 *                    问号传参模式，paramNamed设置为null
	 * @param paramValues
	 * @param resultType  返回结果List中的对象类型(可以是VO、null:表示返回List<List>;HashMap.class
	 *                    (默认驼峰命名))
	 * @param topSize     (大于1则取固定数量的记录，小于1，则表示按比例提取)
	 * @return
	 */
	public <T> List<T> findTopBySql(final String sqlOrSqlId, final String[] paramsNamed, final Object[] paramValues,
			final Class<T> resultType, final double topSize);

	/**
	 * @TODO 提供基于Map传参的top查询
	 * @param <T>
	 * @param sqlOrSqlId
	 * @param paramsMap
	 * @param resultType 可以是vo、dto、Map(默认驼峰命名)
	 * @param topSize
	 * @return
	 */
	public <T> List<T> findTopBySql(final String sqlOrSqlId, final Map<String, Object> paramsMap,
			final Class<T> resultType, final double topSize);

	/**
	 * @todo 基于对象传参数模式(内部会根据sql中的参数提取对象对应属性的值),并返回对象对应类型的List
	 * @param <T>
	 * @param sqlOrSqlId
	 * @param entity
	 * @param topSize    (大于1则取固定数量的记录，小于1，则表示按比例提取)
	 * @return
	 */
	public <T extends Serializable> List<T> findTopBySql(final String sqlOrSqlId, final T entity, final double topSize);

	/*
	 * 用QueryExecutor组织查询逻辑
	 * 
	 * @see findTopBySql(String sqlOrSqlId, T entity, double topSize)
	 */
	public QueryResult findTopByQuery(final QueryExecutor queryExecutor, final double topSize);

	/**
	 * @TODO 通过对象传参模式取随机记录
	 * @param <T>
	 * @param sqlOrSqlId
	 * @param entity
	 * @param randomCount 小于1表示按比例提取，大于1则按整数部分提取记录数量
	 * @return
	 */
	public <T extends Serializable> List<T> getRandomResult(final String sqlOrSqlId, final T entity,
			final double randomCount);

	public QueryResult getRandomResult(final QueryExecutor queryExecutor, final double randomCount);

	public <T> List<T> getRandomResult(final String sqlOrSqlId, final String[] paramsNamed, final Object[] paramsValue,
			final Class<T> resultType, final double randomCount);

	/**
	 * @TODO 提供基于Map传参的随机记录查询
	 * @param <T>
	 * @param sqlOrSqlId
	 * @param paramsMap
	 * @param resultType  可以是vo、dto、Map(默认驼峰命名)
	 * @param randomCount
	 * @return
	 */
	public <T> List<T> getRandomResult(final String sqlOrSqlId, final Map<String, Object> paramsMap,
			final Class<T> resultType, final double randomCount);

	/**
	 * @TODO 批量集合通过sql进行修改操作,调用:batchUpdate(sqlId,List)
	 * @param sqlOrSqlId
	 * @param dataSet    支持List<List>、List<Object[]>(sql中?传参) ;List<VO>、List<Map>
	 *                   形式(sql中:paramName传参)
	 * @return
	 */
	public Long batchUpdate(final String sqlOrSqlId, final List dataSet);

	/**
	 * @todo 批量集合通过sql进行修改操作,调用:batchUpdate(sqlId,List,null,null)
	 *       <p>
	 *       <li>1、VO传参模式，即:batchUpdate(sql,List<VO> dataSet),sql中用:paramName</li>
	 *       <li>2、List<List>模式，sql中直接用? 形式传参,弊端就是严格顺序</li>
	 *       </p>
	 * @param sqlOrSqlId
	 * @param dataSet    支持List<List>、List<Object[]>(sql中?传参) ;List<VO>、List<Map>
	 *                   形式(sql中:paramName传参)
	 * @param autoCommit (一般为null)
	 */
	public Long batchUpdate(final String sqlOrSqlId, final List dataSet, final Boolean autoCommit);

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
	 * @param sqlOrSqlId
	 * @param entity
	 * @return Long 数据库发生变更的记录数
	 */
	public Long executeSql(final String sqlOrSqlId, final Serializable entity);

	/**
	 * @TODO 通过数组传参执行sql,并返回更新记录量
	 * @param sqlOrSqlId
	 * @param paramsNamed (非别名模式可以为null,sql例如:insert table (id,name) values(?,?))
	 * @param paramsValue
	 * @return
	 */
	public Long executeSql(final String sqlOrSqlId, final String[] paramsNamed, final Object[] paramsValue);

	/**
	 * @TODO 提供基于Map传参的sql执行
	 * @param sqlOrSqlId
	 * @param paramsMap
	 * @return
	 */
	public Long executeSql(final String sqlOrSqlId, final Map<String, Object> paramsMap);

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
	 * @TODO 根据指定的表名、业务码，业务码的属性和值map，动态获取业务主键值
	 * 例如:generateBizId("sag_test", "HW@case(orderType,SALE,SC,BUY,PO)@day(yyMMdd)",
				MapKit.map("orderType", "SALE"), null, 12, 2);
	 * @param tableName
	 * @param signature 一个表达式字符串，支持@case(name,value1,then1,val2,then2) 和 @day(yyMMdd)或@day(yyyyMMdd)、@substr(name,start,length) 等
	 * @param keyValues
	 * @param bizDate 在signature为空时生效
	 * @param length
	 * @param sequenceSize
	 * @return
	 */
	public String generateBizId(String tableName, String signature, Map<String, Object> keyValues, LocalDate bizDate,
			int length, int sequenceSize);

	/**
	 * @todo 获取sqltoy中用于翻译的缓存,方便用于页面下拉框选项、checkbox选项、suggest组件等
	 * @param cacheName
	 * @param cacheType 如是数据字典,则传入字典类型否则为null即可
	 * @return
	 */
	public HashMap<String, Object[]> getTranslateCache(String cacheName, String cacheType);

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
	 * @todo 对数据集合通过反调函数对具体属性进行翻译
	 *       <p>
	 *       sqlToyLazyDao.translate(staffVOs<StaffInfoVO>, "staffIdName", new
	 *       TranslateHandler() { //告知key值 public Object getKey(Object row) { return
	 *       ((StaffInfoVO)row).getStaffId(); } // 将翻译后的名称值设置到对应的属性上 public void
	 *       setName(Object row, String name) {
	 *       ((StaffInfoVO)row).setStaffName(name); } });
	 *       </p>
	 * @param dataSet        数据集合
	 * @param cacheName      缓存名称
	 * @param cacheType      例如数据字典存在分类的缓存填写字典分类，其它的如员工、机构等填null
	 * @param cacheNameIndex
	 * @param handler
	 */
	public void translate(Collection dataSet, String cacheName, String cacheType, Integer cacheNameIndex,
			TranslateHandler handler);

	/**
	 * @TODO 通过缓存将名称进行模糊匹配取得key的集合，比如前端传了一个企业名称，然后通过企业信息的缓存反向通过名称匹配到企业id，用于精准查询
	 * @param matchRegex       匹配的表达式，如:中 上海,内容按照此顺序出现相关文字即可匹配上
	 * @param cacheMatchFilter 例如:
	 *                         CacheMatchFilter.create().cacheName("staffIdNameCache")
	 * @return
	 */
	@Deprecated
	public String[] cacheMatchKeys(String matchRegex, CacheMatchFilter cacheMatchFilter);

	/**
	 * update 2022-12-15 支持数组
	 * 
	 * @TODO 通过缓存将名称进行模糊匹配取得key的集合
	 * @param cacheMatchFilter
	 * @param matchRegexes     数组
	 * @return
	 */
	public String[] cacheMatchKeys(CacheMatchFilter cacheMatchFilter, String... matchRegexes);

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
	 * @TODO 实现VO和POJO之间属性值的复制
	 * @param <T>
	 * @param source
	 * @param resultType
	 * @param ignoreProperties 忽略映射匹配的属性
	 * @return
	 */
	public <T extends Serializable> T convertType(Serializable source, Class<T> resultType, String... ignoreProperties);

	/**
	 * @TODO 实现VO和POJO 集合之间属性值的复制
	 * @param <T>
	 * @param sourceList
	 * @param resultType
	 * @param ignoreProperties 忽略映射匹配的属性
	 * @return
	 */
	public <T extends Serializable> List<T> convertType(List sourceList, Class<T> resultType,
			String... ignoreProperties);

	/**
	 * @TODO 实现分页对象的类型转换
	 * @param <T>
	 * @param sourcePage
	 * @param resultType
	 * @param ignoreProperties 忽略映射匹配的属性
	 * @return
	 */
	public <T extends Serializable> Page<T> convertType(Page sourcePage, Class<T> resultType,
			String... ignoreProperties);

	/**
	 * @TODO 并行查询并返回一维List，有几个查询List中就包含几个结果对象，paramNames和paramValues是全部sql的条件参数的合集
	 *       <p>
	 *       //定义参数 String[] paramNames = new String[] { "userId", "defaultRoles",
	 *       "deployId", "authObjType" }; Object[] paramValues = new Object[] {
	 *       userId, defaultRoles,
	 *       GlobalConstants.DEPLOY_ID,SagacityConstants.TempAuthObjType.GROUP }; //
	 *       使用并行查询同时执行2个sql,条件参数是2个查询的合集 List<QueryResult<TreeModel>> list =
	 *       super.parallQuery( Arrays.asList(
	 *       ParallQuery.create().sql("webframe_searchAllModuleMenus").resultType(TreeModel.class),
	 *       ParallQuery.create().sql("webframe_searchAllUserReports").resultType(TreeModel.class)),
	 *       paramNames, paramValues,);
	 *       </p>
	 * @param parallQueryList<ParallQuery> ParallQuery中可以单独对本查询设置条件参数
	 * @param paramNames
	 * @param paramValues
	 * @return
	 */
	public <T> List<QueryResult<T>> parallQuery(List<ParallQuery> parallQueryList, String[] paramNames,
			Object[] paramValues);

	/**
	 * @TODO 并行查询并返回一维List，有几个查询List中就包含几个结果对象，paramNames和paramValues是全部sql的条件参数的合集
	 * @param parallQueryList<ParallQuery> ParallQuery中可以单独对本查询设置条件参数
	 * @param paramNames
	 * @param paramValues
	 * @param parallelConfig               设置并行参数:ParallelConfig.create().maxThreads(5).maxWaitSeconds(600)
	 * @return
	 */
	public <T> List<QueryResult<T>> parallQuery(List<ParallQuery> parallQueryList, String[] paramNames,
			Object[] paramValues, ParallelConfig parallelConfig);

	/**
	 * @TODO 提供基于Map传参的并行查询
	 * @param <T>
	 * @param parallQueryList<ParallQuery> ParallQuery中可以单独对本查询设置条件参数
	 * @param paramsMap
	 * @return
	 */
	public <T> List<QueryResult<T>> parallQuery(List<ParallQuery> parallQueryList, Map<String, Object> paramsMap);

	/**
	 * @TODO 提供基于Map传参的并行查询,并提供并行线程数、最大等待时长等参数设置
	 * @param <T>
	 * @param parallQueryList<ParallQuery> ParallQuery中可以单独对本查询设置条件参数
	 * @param paramsMap
	 * @param parallelConfig               例如:ParallelConfig.create().maxThreads(20)
	 * @return
	 */
	public <T> List<QueryResult<T>> parallQuery(List<ParallQuery> parallQueryList, Map<String, Object> paramsMap,
			ParallelConfig parallelConfig);

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
