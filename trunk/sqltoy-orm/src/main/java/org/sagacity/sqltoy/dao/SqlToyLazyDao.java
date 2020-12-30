/**
 * 提供一个默认的多功能Dao,在Service层统一提SqlToyLazyDao引入, 
 * 这样对于一般的增删改查等操作直接使用，从而避免对一些非常简单的操作也需要写一个Dao类
 * ,简化开发提升开发效率
 */
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
import org.sagacity.sqltoy.callback.ReflectPropertyHandler;
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
import org.sagacity.sqltoy.model.EntityQuery;
import org.sagacity.sqltoy.model.EntityUpdate;
import org.sagacity.sqltoy.model.LockMode;
import org.sagacity.sqltoy.model.PaginationModel;
import org.sagacity.sqltoy.model.ParallQuery;
import org.sagacity.sqltoy.model.ParallelConfig;
import org.sagacity.sqltoy.model.QueryResult;
import org.sagacity.sqltoy.model.StoreResult;
import org.sagacity.sqltoy.model.TreeTableModel;
import org.sagacity.sqltoy.translate.TranslateHandler;

/**
 * @project sqltoy-orm
 * @description 提供一个便捷的dao实现,供开发过程中直接通过service调用,避免大量的自定义Dao中仅仅是一些简单的中转调用
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:SqlToyLazyDao.java,Revision:v1.0,Date:2015年11月27日
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
	 * @return
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
	 * @param paramsNamed
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
	 * @return
	 */
	public Long getCount(String sqlOrNamedQuery, Map<String, Object> paramsMap);

	/**
	 * @TODO 通过POJO产生count语句
	 * @param entityClass
	 * @param entityQuery
	 * @return
	 */
	public Long getCount(Class entityClass, EntityQuery entityQuery);

	/**
	 * @todo 存储过程调用
	 * @param storeSqlOrKey 可以是xml中的sqlId 或者直接{call storeName (?,?)}
	 * @param inParamValues
	 */
	public StoreResult executeStore(final String storeSqlOrKey, final Object[] inParamValues);

	/**
	 * @TODO 存储过程调用，outParams可以为null
	 * @param storeSqlOrKey 可以是xml中的sqlId 或者直接{call storeName (?,?)}
	 * @param inParamValues
	 * @param outParamsType 可以为null
	 * @param resultType    可以是VO、Map.class、LinkedHashMap.class、Array.class,null(二维List)
	 * @return
	 */
	public StoreResult executeStore(String storeSqlOrKey, Object[] inParamValues, Integer[] outParamsType,
			Class resultType);

	/**
	 * @todo 保存对象,并返回主键值
	 * @param serializableVO
	 * @return
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
	 * @param reflectPropertyHandler
	 */
	@Deprecated
	public <T extends Serializable> Long saveAll(List<T> entities, ReflectPropertyHandler reflectPropertyHandler);

	// sqltoy的update内部处理是考虑属性为null情况的，一次交互完成，比jpa更加合理，可以深入了解
	/**
	 * @todo 修改数据并返回数据库记录变更数量(非强制修改属性，当属性值为null不参与修改)
	 * @param entitySet
	 * @param forceUpdateProps 强制修改的字段属性
	 */
	public Long update(Serializable serializableVO, String... forceUpdateProps);

	/**
	 * @TODO 基于对象单表对象查询进行数据更新
	 * @param entityClass
	 * @param entityUpdate
	 * @return
	 */
	public Long updateByQuery(Class entityClass, EntityUpdate entityUpdate);

	/**
	 * @todo 深度修改
	 * @param serializableVO
	 */
	public Long updateDeeply(Serializable serializableVO);

	/**
	 * @todo 修改数据并返回数据库记录变更数量
	 * @param serializableVO
	 * @param forceUpdateProps
	 * @param emptyUpdateClass
	 * @param subTableForceUpdateProps
	 */
	public Long updateCascade(Serializable serializableVO, String[] forceUpdateProps, Class[] emptyUpdateClass,
			HashMap<Class, String[]> subTableForceUpdateProps);

	/**
	 * @TODO 批量修改操作，并可以指定强制修改的属性(非强制修改属性，当属性值为null不参与修改)
	 * @param <T>
	 * @param entities
	 * @param forceUpdateProps
	 * @return
	 */
	public <T extends Serializable> Long updateAll(List<T> entities, String... forceUpdateProps);

	/**
	 * @todo 批量修改对象并返回数据库记录变更数量
	 * @param entitys
	 * @param reflectPropertyHandler 用于通过反射机制设置属性值
	 * @param forceUpdateProps       强制修改的属性
	 */
	public <T extends Serializable> Long updateAll(List<T> entities, ReflectPropertyHandler reflectPropertyHandler,
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
	 * @param reflectPropertyHandler
	 * @return
	 */
	@Deprecated
	public <T extends Serializable> Long updateAllDeeply(List<T> entities,
			ReflectPropertyHandler reflectPropertyHandler);

	/**
	 * @todo 保存或修改数据并返回数据库记录变更数量
	 * @param serializableVO
	 * @param forceUpdateProps
	 */
	public Long saveOrUpdate(Serializable serializableVO, String... forceUpdateProps);

	/**
	 * @TODO 批量保存或修改操作
	 * @param <T>
	 * @param entities
	 * @param forceUpdateProps
	 * @return
	 */
	public <T extends Serializable> Long saveOrUpdateAll(List<T> entities, String... forceUpdateProps);

	/**
	 * @todo 批量修改或保存数据并返回数据库记录变更数量
	 * @param <T>
	 * @param entities
	 * @param reflectPropertyHandler
	 * @param forceUpdateProps
	 * @return
	 */
	@Deprecated
	public <T extends Serializable> Long saveOrUpdateAll(List<T> entities,
			ReflectPropertyHandler reflectPropertyHandler, String... forceUpdateProps);

	/**
	 * @todo 删除单条对象并返回数据库记录影响的数量
	 * @param entity
	 */
	public Long delete(final Serializable entity);

	/**
	 * @todo 批量删除对象并返回数据库记录影响的数量
	 * @param entities
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
	 * @todo truncate表
	 * @param entityClass
	 */
	public void truncate(final Class entityClass);

	/**
	 * @todo 根据实体对象的主键值获取对象的详细信息
	 * @param entity
	 * @return
	 * @throws Exception
	 */
	public <T extends Serializable> T load(final T entity);

	/**
	 * @todo 根据主键获取对象,提供读取锁设定
	 * @param entity
	 * @param lockMode
	 * @return
	 */
	public <T extends Serializable> T load(final T entity, final LockMode lockMode);

	/**
	 * @todo 指定加载子类的单记录查询
	 * @param entity
	 * @param lockMode
	 * @param cascadeTypes
	 * @return
	 */
	public <T extends Serializable> T loadCascade(final T entity, final LockMode lockMode, final Class... cascadeTypes);

	// sqltoy的loadAll 性能是极为优化的
	/**
	 * @todo 根据集合中的主键获取实体的详细信息
	 * @param entities
	 * @return
	 */
	public <T extends Serializable> List<T> loadAll(List<T> entities);

	public <T extends Serializable> List<T> loadAll(List<T> entities, final LockMode lockMode);

	/**
	 * @TODO 加载全表数据(不推荐使用)
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
	 * @TODO 单表分页查询
	 * @param <T>
	 * @param entityClass
	 * @param paginationModel
	 * @param entityQuery
	 * @return
	 */
	public <T> PaginationModel<T> findEntity(Class<T> entityClass, final PaginationModel paginationModel,
			EntityQuery entityQuery);

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
	 * @TODO 根据QueryExecutor来链式操作灵活定义查询sql、条件、数据源等
	 * @param query
	 * @return
	 */
	public Object loadByQuery(final QueryExecutor query);

	/**
	 * @TODO 获取查询结果的第一条、第一列的值，一般用select max(x) from 等
	 * @param sqlOrNamedSql
	 * @param paramsNamed
	 * @param paramsValue
	 * @return
	 */
	public Object getSingleValue(final String sqlOrNamedSql, final String[] paramsNamed, final Object[] paramsValue);

	public Object getSingleValue(final String sqlOrNamedSql, final Map<String, Object> paramsMap);

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
	 * @param randomCount
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
	 * @todo 批量集合通过sql进行修改操作
	 * @param sqlOrNamedSql
	 * @param dataSet
	 * @param insertCallhandler
	 * @param autoCommit
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

	@Deprecated
	public List updateFetchTop(final QueryExecutor queryExecutor, final Integer topSize,
			final UpdateRowHandler updateRowHandler);

	/**
	 * @todo 随机提取符合条件的记录,锁定并进行修改
	 * @param queryExecutor
	 * @param random
	 * @param updateRowHandler
	 * @return
	 */
	@Deprecated
	public List updateFetchRandom(final QueryExecutor queryExecutor, final Integer random,
			final UpdateRowHandler updateRowHandler);

	/**
	 * @todo 执行sql,并返回被修改的记录数量
	 * @param sqlOrNamedSql
	 * @param entity
	 * @return
	 */
	public Long executeSql(final String sqlOrNamedSql, final Serializable entity);

	/**
	 * @todo 执行sql,并返回被修改的记录数量
	 * @param sqlOrNamedSql
	 * @param entity
	 * @param reflectPropertyHandler (一般传null)
	 * @return Long updateCount
	 */
	@Deprecated
	public Long executeSql(final String sqlOrNamedSql, final Serializable entity,
			final ReflectPropertyHandler reflectPropertyHandler);

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
	 * @todo 获取业务ID
	 * @param signature
	 * @param increment
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
	 * @param cacheName
	 * @param matchRegex
	 * @param matchIndexes
	 * @return
	 */
	public String[] cacheMatchKeys(String cacheName, String matchRegex, int... matchIndexes);

	/**
	 * @TODO 通过缓存将名称进行模糊匹配取得key的集合
	 * @param cacheName
	 * @param cacheType
	 * @param matchRegex
	 * @param matchIndexes
	 * @return
	 */
	public String[] cacheMatchKeys(String cacheName, String cacheType, String matchRegex, int... matchIndexes);

	/**
	 * @TODO 实现VO和POJO之间属性值的复制
	 * @param <T>
	 * @param source
	 * @param resultType
	 * @return
	 * @throws Exception
	 */
	public <T extends Serializable> T convertType(Serializable source, Class<T> resultType);

	/**
	 * @TODO 实现VO和POJO 集合之间属性值的复制
	 * @param <T>
	 * @param sourceList
	 * @param resultType
	 * @return
	 */
	public <T extends Serializable> List<T> convertType(List sourceList, Class<T> resultType);

	/**
	 * @TODO 实现分页对象的类型转换
	 * @param <T>
	 * @param sourcePage
	 * @param resultType
	 * @return
	 */
	public <T extends Serializable> PaginationModel<T> convertType(PaginationModel sourcePage, Class<T> resultType);

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
	 * @param parallelConfig 例如:ParallelConfig.create().maxThreads(20)
	 * @return
	 */
	public <T> List<QueryResult<T>> parallQuery(List<ParallQuery> parallQueryList, Map<String, Object> paramsMap,
			ParallelConfig parallelConfig);

	/** ------- 链式操作，功能就是上面参数直传模式的用链式赋值的封装(优雅但易遗漏赋值)，不推荐使用 ------------ */

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
}
