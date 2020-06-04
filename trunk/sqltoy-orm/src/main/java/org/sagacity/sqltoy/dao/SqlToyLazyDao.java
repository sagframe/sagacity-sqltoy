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
import org.sagacity.sqltoy.link.Page;
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
import org.sagacity.sqltoy.model.QueryResult;
import org.sagacity.sqltoy.model.StoreResult;
import org.sagacity.sqltoy.model.TreeTableModel;
import org.sagacity.sqltoy.translate.TranslateHandler;

/**
 * @project sqltoy-orm
 * @description 提供一个便捷的dao实现,供开发过程中直接通过service调用,避免大量的自定义Dao中仅仅是一些简单的中转调用
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:SqlToyLazyDao.java,Revision:v1.0,Date:2015年11月27日
 * @Modification Date:2017-11-28 {增加link链式操作功能,开放全部DaoSupport中的功能}
 * @Modification Date:2020-4-23 {对分页查询增加泛型支持}
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
	public boolean isUnique(Serializable entity, String[] paramsNamed);

	/**
	 * @todo 获取符合条件的查询对应的记录数量
	 * @param sqlOrNamedQuery
	 * @param paramsNamed
	 * @param paramsValue
	 * @return Long
	 */
	public Long getCount(String sqlOrNamedQuery, String[] paramsNamed, Object[] paramsValue);

	/**
	 * @todo 无结果存储过程调用
	 * @param storeNameOrKey
	 * @param inParamValues
	 */
	public StoreResult executeStore(final String storeNameOrKey, final Object[] inParamValues);

	public StoreResult executeStore(String storeNameOrKey, Object[] inParamValues, Integer[] outParamsType,
			Class resultType);

	/**
	 * @todo 保存对象
	 * @param serializableVO
	 * @return
	 */
	public Object save(Serializable serializableVO);

	public <T extends Serializable> Long saveAll(List<T> entities);

	public <T extends Serializable> Long saveAllIgnoreExist(List<T> entities);

	/**
	 * @todo 批量保存数据
	 * @param dataSet
	 * @param reflectPropertyHandler
	 */
	public <T extends Serializable> Long saveAll(List<T> entities, ReflectPropertyHandler reflectPropertyHandler);

	/**
	 * @todo 非深度修改
	 * @param serializableVO
	 */
	public Long update(Serializable serializableVO);

	/**
	 * @todo 修改数据
	 * @param entitySet
	 * @param forceUpdateProps 强制修改的字段属性
	 */
	public Long update(Serializable serializableVO, String[] forceUpdateProps);

	/**
	 * @基于对象单表对象查询进行数据更新
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
	 * @todo 修改数据
	 * @param serializableVO
	 * @param forceUpdateProps
	 * @param emptyUpdateClass
	 * @param subTableForceUpdateProps
	 */
	public Long updateCascade(Serializable serializableVO, String[] forceUpdateProps, Class[] emptyUpdateClass,
			HashMap<Class, String[]> subTableForceUpdateProps);

	public <T extends Serializable> Long updateAll(List<T> entities);

	public <T extends Serializable> Long updateAll(List<T> entities, String[] forceUpdateProps);

	/**
	 * @todo 批量修改对象
	 * @param entitys
	 * @param forceUpdateProps       强制修改的属性
	 * @param reflectPropertyHandler 用于通过反射机制设置属性值
	 */
	public <T extends Serializable> Long updateAll(List<T> entities, String[] forceUpdateProps,
			ReflectPropertyHandler reflectPropertyHandler);

	public <T extends Serializable> Long updateAllDeeply(List<T> entities,
			ReflectPropertyHandler reflectPropertyHandler);

	public Long saveOrUpdate(Serializable serializableVO);

	/**
	 * @todo 保存或修改数据
	 * @param serializableVO
	 * @param forceUpdateProps
	 */
	public Long saveOrUpdate(Serializable serializableVO, String[] forceUpdateProps);

	public <T extends Serializable> Long saveOrUpdateAll(List<T> entities);

	public <T extends Serializable> Long saveOrUpdateAll(List<T> entities, String[] forceUpdateProps);

	/**
	 * @todo 批量修改或保存数据
	 * @param entities
	 * @param forceUpdateProps
	 * @param reflectPropertyHandler
	 */
	public <T extends Serializable> Long saveOrUpdateAll(List<T> entities, String[] forceUpdateProps,
			ReflectPropertyHandler reflectPropertyHandler);

	/**
	 * @todo 删除单条对象
	 * @param entity
	 */
	public Long delete(final Serializable entity);

	/**
	 * @todo 批量删除对象
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
	 * @todo 默认加载所有子表信息
	 * @param entity
	 * @param lockMode
	 * @return
	 */
	public <T extends Serializable> T loadCascade(final T entity, final LockMode lockMode);

	/**
	 * @todo 指定加载子类的单记录查询
	 * @param entity
	 * @param cascadeTypes
	 * @param lockMode
	 * @return
	 */
	public <T extends Serializable> T loadCascade(final T entity, final Class[] cascadeTypes, final LockMode lockMode);

	/**
	 * @todo 根据集合中的主键获取实体的详细信息
	 * @param entities
	 * @return
	 */
	public <T extends Serializable> List<T> loadAll(List<T> entities);

	/**
	 * TODO 通过EntityQuery 组织查询条件对POJO进行单表查询,为代码中进行逻辑处理提供便捷
	 * 
	 * @param <T>
	 * @param resultType
	 * @param entityQuery
	 * @return
	 */
	public <T> List<T> findEntity(Class<T> resultType, EntityQuery entityQuery);

	public <T> PaginationModel<T> findEntity(Class<T> resultType, final PaginationModel paginationModel,
			EntityQuery entityQuery);

	/**
	 * @todo 级联加载子表数据
	 * @param entities
	 * @return
	 */
	public <T extends Serializable> List<T> loadAllCascade(List<T> entities);

	/**
	 * @todo 选择性的加载子表信息
	 * @param entities
	 * @param cascadeTypes
	 * @return
	 */
	public <T extends Serializable> List<T> loadAllCascade(List<T> entities, final Class[] cascadeTypes);

	public Object loadByQuery(final QueryExecutor query);

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
	 * @todo 通过对象实体传参数,框架结合sql中的参数名称来映射对象属性取值
	 * @param sqlOrNamedSql
	 * @param entity
	 * @return
	 */
	public <T extends Serializable> T loadBySql(final String sqlOrNamedSql, final T entity);

	/**
	 * @TODO 获取查询结果的第一条、第一列的值，一般用select max(x) from 等
	 * @param sqlOrNamedSql
	 * @param paramsNamed
	 * @param paramsValue
	 * @return
	 */
	public Object getSingleValue(final String sqlOrNamedSql, final String[] paramsNamed, final Object[] paramsValue);

	/**
	 * @todo 通过Query构造查询条件进行数据查询
	 * @param query
	 * @return
	 */
	public QueryResult findByQuery(final QueryExecutor query);

	/**
	 * @todo 通过对象传参数,简化paramName[],paramValue[] 模式传参
	 * @param <T>
	 * @param sqlOrNamedSql
	 * @param entity        通过对象传参数,并按对象类型返回结果
	 * @return
	 */
	public <T extends Serializable> List<T> findBySql(final String sqlOrNamedSql, final T entity);

	/**
	 * @todo 通过给定sql、sql中的参数、参数的数值以及返回结果的对象类型进行条件查询
	 * @param sqlOrNamedSql
	 * @param paramsNamed
	 * @param paramsValue   对应Named参数的值
	 * @param voClass       返回结果List中的对象类型(可以是VO、null:表示返回List<List>;HashMap.class)
	 * @return
	 */
	public <T> List<T> findBySql(final String sqlOrNamedSql, final String[] paramsNamed, final Object[] paramsValue,
			final Class<T> voClass);

	/**
	 * @TODO 将查询结果直接按二维List返回
	 * @param sqlOrNamedSql
	 * @param paramsNamed
	 * @param paramsValue
	 * @return
	 */
	public List findBySql(final String sqlOrNamedSql, final String[] paramsNamed, final Object[] paramsValue);

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
	 * @param voClass
	 * @return
	 */
	public <T> PaginationModel<T> findPageBySql(final PaginationModel paginationModel, final String sqlOrNamedSql,
			final String[] paramsNamed, final Object[] paramValues, final Class<T> voClass);

	/**
	 * @TODO 将分页结果按二维List返回
	 * @param paginationModel
	 * @param sqlOrNamedSql
	 * @param paramsNamed
	 * @param paramValues
	 * @return
	 */
	public PaginationModel findPageBySql(final PaginationModel paginationModel, final String sqlOrNamedSql,
			final String[] paramsNamed, final Object[] paramValues);

	public <T extends Serializable> PaginationModel<T> findPageBySql(final PaginationModel paginationModel,
			final String sqlOrNamedSql, final T entity);

	public QueryResult findTopByQuery(final QueryExecutor queryExecutor, final double topSize);

	/**
	 * @todo 取记录的前多少条记录
	 * @param sqlOrNamedSql
	 * @param paramsNamed
	 * @param paramValues
	 * @param voClass       返回结果List中的对象类型(可以是VO、null:表示返回List<List>;HashMap.class)
	 * @param topSize       (大于1则取固定数量的记录，小于1，则表示按比例提取)
	 * @return
	 */
	public <T> List<T> findTopBySql(final String sqlOrNamedSql, final String[] paramsNamed, final Object[] paramValues,
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
	 * @todo 批量集合通过sql进行修改操作
	 * @param sqlOrNamedSql
	 * @param dataSet
	 * @param insertCallhandler
	 * @param autoCommit
	 */
	public Long batchUpdate(final String sqlOrNamedSql, final List dataSet,
			final InsertRowCallbackHandler insertCallhandler, final Boolean autoCommit);

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
	 * @param reflectPropertyHandler
	 * @return Long updateCount
	 */
	public Long executeSql(final String sqlOrNamedSql, final Serializable entity,
			final ReflectPropertyHandler reflectPropertyHandler);

	public Long executeSql(final String sqlOrNamedSql, final String[] paramsNamed, final Object[] paramsValue);

	/**
	 * @todo 构造树形表的节点路径、层次等级、是否叶子节点等必要信息
	 * @param treeTableModel
	 * @return
	 */
	public boolean wrapTreeTableRoute(final TreeTableModel treeTableModel);

	/**
	 * 数据库提交
	 */
	public void flush();

	/**
	 * 删除操作集合
	 * 
	 * @return
	 */
	public Delete delete();

	/**
	 * 分页操作集合
	 * 
	 * @return
	 */
	public Page page();

	/**
	 * 修改操作集合
	 * 
	 * @return
	 */
	public Update update();

	/**
	 * 存储过程操作集合
	 * 
	 * @return
	 */
	public Store store();

	/**
	 * 保存操作集合
	 * 
	 * @return
	 */
	public Save save();

	/**
	 * 查询操作集合
	 * 
	 * @return
	 */
	public Query query();

	/**
	 * 对象加载操作集合
	 * 
	 * @return
	 */
	public Load load();

	/**
	 * 唯一性验证操作集合
	 * 
	 * @return
	 */
	public Unique unique();

	/**
	 * 树形表结构封装操作集合
	 * 
	 * @return
	 */
	public TreeTable treeTable();

	/**
	 * sql语句直接执行修改数据库操作集合
	 * 
	 * @return
	 */
	public Execute execute();

	/**
	 * 批量执行操作集合
	 * 
	 * @return
	 */
	public Batch batch();

	/**
	 * es操作
	 * 
	 * @return
	 */
	public Elastic elastic();

	/**
	 * mongo操作
	 * 
	 * @return
	 */
	public Mongo mongo();

	/**
	 * 获取sqltoy的上下文
	 * 
	 * @return
	 */
	public SqlToyContext getSqlToyContext();

	/**
	 * 获取当前dataSource
	 * 
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
	 * @todo 获取sqltoy中用于翻译的缓存
	 * @param cacheName
	 * @param elementId
	 * @return
	 */
	public HashMap<String, Object[]> getTranslateCache(String cacheName, String elementId);

	/**
	 * @todo 对记录进行翻译
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

}
