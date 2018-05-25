/**
 * 提供一个默认的多功能Dao,在Service层统一提SqlToyLazyDao引入, 
 * 这样对于一般的增删改查等操作直接使用，从而避免对一些非常简单的操作也需要写一个Dao类
 * ,简化开发提升开发效率
 */
package org.sagacity.sqltoy.dao;

import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

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
import org.sagacity.sqltoy.model.LockMode;
import org.sagacity.sqltoy.model.PaginationModel;
import org.sagacity.sqltoy.model.QueryResult;
import org.sagacity.sqltoy.model.StoreResult;
import org.sagacity.sqltoy.model.TreeTableModel;

/**
 * @project sqltoy-orm
 * @description 提供一个便捷的dao实现,供开发过程中直接通过service调用,避免大量的自定义Dao中仅仅是一些简单的中转调用
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:SqlToyLazyDao.java,Revision:v1.0,Date:2015年11月27日
 * @Modification Date:2017-11-28 {增加link链式操作功能,开放全部DaoSupport中的功能}
 */
@SuppressWarnings({ "rawtypes" })
public interface SqlToyLazyDao {

	public SqlToyConfig getSqlToyConfig(String sqlKey, SqlType sqlType);

	/**
	 * @todo 获取实体对象的跟数据库相关的信息
	 * @param entityClass
	 * @return EntityMeta
	 */
	public EntityMeta getEntityMeta(Class entityClass) throws Exception;

	/**
	 * @todo 判断对象属性在数据库中是否唯一
	 * @param entity
	 * @param paramsNamed
	 * @return boolean true：唯一；false：不唯一
	 * @throws Exception
	 */
	public boolean isUnique(Serializable entity, String[] paramsNamed) throws Exception;

	/**
	 * @todo 获取符合条件的查询对应的记录数量
	 * @param sqlOrNamedQuery
	 * @param paramsNamed
	 * @param paramsValue
	 * @return Long
	 * @throws Exception
	 */
	public Long getCount(String sqlOrNamedQuery, String[] paramsNamed, Object[] paramsValue) throws Exception;

	/**
	 * @todo 无结果存储过程调用
	 * @param storeNameOrKey
	 * @param inParamValues
	 * @throws Exception
	 */
	public StoreResult executeStore(final String storeNameOrKey, final Object[] inParamValues) throws Exception;

	public StoreResult executeStore(String storeNameOrKey, Object[] inParamValues, Integer[] outParamsType,
			Class resultType) throws Exception;

	/**
	 * @todo 保存对象
	 * @param serializableVO
	 * @return
	 * @throws Exception
	 */
	public Object save(Serializable serializableVO) throws Exception;

	public Long saveAll(List<?> entities) throws Exception;

	/**
	 * @todo 批量保存数据
	 * @param dataSet
	 * @param reflectPropertyHandler
	 * @throws Exception
	 */
	public Long saveAll(List<?> entities, ReflectPropertyHandler reflectPropertyHandler) throws Exception;

	/**
	 * @todo 非深度修改
	 * @param serializableVO
	 * @throws Exception
	 */
	public Long update(Serializable serializableVO) throws Exception;

	/**
	 * @todo 修改数据
	 * @param entitySet
	 * @param forceUpdateProps
	 *            强制修改的字段属性
	 * @throws Exception
	 */
	public Long update(Serializable serializableVO, String[] forceUpdateProps) throws Exception;

	/**
	 * @todo 深度修改
	 * @param serializableVO
	 * @throws Exception
	 */
	public Long updateDeeply(Serializable serializableVO) throws Exception;

	/**
	 * @todo 修改数据
	 * @param serializableVO
	 * @param forceUpdateProps
	 * @param emptyUpdateClass
	 * @param subTableForceUpdateProps
	 * @throws Exception
	 */
	public Long updateCascade(Serializable serializableVO, String[] forceUpdateProps, Class[] emptyUpdateClass,
			HashMap<Class, String[]> subTableForceUpdateProps) throws Exception;

	public Long updateAll(List<?> entities) throws Exception;

	public Long updateAll(List<?> entities, String[] forceUpdateProps) throws Exception;

	/**
	 * @todo 批量修改对象
	 * @param entitys
	 * @param forceUpdateProps
	 *            强制修改的属性
	 * @param reflectPropertyHandler
	 *            用于通过反射机制设置属性值
	 * @throws Exception
	 */
	public Long updateAll(List<?> entities, String[] forceUpdateProps, ReflectPropertyHandler reflectPropertyHandler)
			throws Exception;

	public Long updateAllDeeply(List<?> entities, ReflectPropertyHandler reflectPropertyHandler) throws Exception;

	public Long saveOrUpdate(Serializable serializableVO) throws Exception;

	/**
	 * @todo 保存或修改数据
	 * @param serializableVO
	 * @param forceUpdateProps
	 * @throws Exception
	 */
	public Long saveOrUpdate(Serializable serializableVO, String[] forceUpdateProps) throws Exception;

	public Long saveOrUpdateAll(List<?> entities) throws Exception;

	public Long saveOrUpdateAll(List<?> entities, String[] forceUpdateProps) throws Exception;

	/**
	 * @todo 批量修改或保存数据
	 * @param entities
	 * @param forceUpdateProps
	 * @param reflectPropertyHandler
	 * @throws Exception
	 */
	public Long saveOrUpdateAll(List<?> entities, String[] forceUpdateProps,
			ReflectPropertyHandler reflectPropertyHandler) throws Exception;

	/**
	 * @todo 删除单条对象
	 * @param entity
	 * @throws Exception
	 */
	public Long delete(final Serializable entity) throws Exception;

	/**
	 * @todo 批量删除对象
	 * @param entities
	 * @throws Exception
	 */
	public Long deleteAll(final List<?> entities) throws Exception;

	/**
	 * @todo truncate表
	 * @param entityClass
	 * @throws Exception
	 */
	public void truncate(final Class entityClass) throws Exception;

	/**
	 * @todo 根据实体对象的主键值获取对象的详细信息
	 * @param entity
	 * @return
	 * @throws Exception
	 */
	public Serializable load(final Serializable entity) throws Exception;

	/**
	 * @todo 根据主键获取对象,提供读取锁设定
	 * @param entity
	 * @param lockMode
	 * @return
	 * @throws Exception
	 */
	public Serializable load(final Serializable entity, final LockMode lockMode) throws Exception;

	/**
	 * @todo 默认加载所有子表信息
	 * @param entity
	 * @param lockMode
	 * @return
	 * @throws Exception
	 */
	public Serializable loadCascade(final Serializable entity, final LockMode lockMode) throws Exception;

	/**
	 * @todo 指定加载子类的单记录查询
	 * @param entity
	 * @param cascadeTypes
	 * @param lockMode
	 * @return
	 * @throws Exception
	 */
	public Serializable loadCascade(final Serializable entity, final Class[] cascadeTypes, final LockMode lockMode)
			throws Exception;

	/**
	 * @todo 根据集合中的主键获取实体的详细信息
	 * @param entities
	 * @return
	 * @throws Exception
	 */
	public List loadAll(List<?> entities) throws Exception;

	/**
	 * @todo 级联加载子表数据
	 * @param entities
	 * @return
	 * @throws Exception
	 */
	public List loadAllCascade(List<?> entities) throws Exception;

	/**
	 * @todo 选择性的加载子表信息
	 * @param entities
	 * @param cascadeTypes
	 * @return
	 * @throws Exception
	 */
	public List loadAllCascade(List<?> entities, final Class[] cascadeTypes) throws Exception;

	public Object loadByQuery(final QueryExecutor query) throws Exception;

	/**
	 * @todo 通过sql获取单条记录
	 * @param sqlOrNamedSql
	 * @param paramsNamed
	 * @param paramsValue
	 * @param voClass
	 * @return
	 * @throws Exception
	 */
	public Object loadBySql(final String sqlOrNamedSql, final String[] paramsNamed, final Object[] paramsValue,
			final Class voClass) throws Exception;

	public Object loadBySql(final String sqlOrNamedSql, final Serializable entity) throws Exception;

	public Object getSingleValue(final String sqlOrNamedSql, final String[] paramsNamed, final Object[] paramsValue)
			throws Exception;

	/**
	 * @todo 通过Query构造查询条件进行数据查询
	 * @param query
	 * @return
	 * @throws Exception
	 */
	public QueryResult findByQuery(final QueryExecutor query) throws Exception;

	public List findBySql(final String sqlOrNamedSql, final Serializable entity) throws Exception;

	/**
	 * @todo 通过给定sql、sql中的参数、参数的数值以及返回结果的对象类型进行条件查询
	 * @param sqlOrNamedSql
	 * @param paramsNamed
	 * @param paramsValue
	 *            对应Named参数的值
	 * @param voClass
	 *            返回结果List中的对象类型
	 * @return
	 * @throws Exception
	 */
	public List findBySql(final String sqlOrNamedSql, final String[] paramsNamed, final Object[] paramsValue,
			final Class voClass) throws Exception;

	/**
	 * @todo 根据实体对象获取select * from table 并整合wherePartSql或properties 条件参数进行分页查询
	 * @param pageModel
	 * @param query
	 * @return
	 * @throws Exception
	 */
	public QueryResult findPageByQuery(final PaginationModel pageModel, final QueryExecutor queryExecutor)
			throws Exception;

	public PaginationModel findPageBySql(final PaginationModel paginationModel, final String sqlOrNamedSql,
			final Serializable entity) throws Exception;

	/**
	 * @todo 普通sql分页查询
	 * @param paginationModel
	 * @param sqlOrNamedSql
	 * @param paramsNamed
	 * @param paramValues
	 * @param voClass
	 * @return
	 * @throws Exception
	 */
	public PaginationModel findPageBySql(final PaginationModel paginationModel, final String sqlOrNamedSql,
			final String[] paramsNamed, final Object[] paramValues, final Class voClass) throws Exception;

	public QueryResult findTopByQuery(final QueryExecutor queryExecutor, final double topSize) throws Exception;

	/**
	 * @todo 取记录的前多少条记录
	 * @param sqlOrNamedSql
	 * @param paramsNamed
	 * @param paramValues
	 * @param voClass
	 * @param topSize
	 * @return
	 * @throws Exception
	 */
	public List findTopBySql(final String sqlOrNamedSql, final String[] paramsNamed, final Object[] paramValues,
			final Class voClass, final double topSize) throws Exception;

	public List findTopBySql(final String sqlOrNamedSql, final Serializable entity, final double topSize)
			throws Exception;

	public QueryResult getRandomResult(final QueryExecutor queryExecutor, final double randomCount) throws Exception;

	public List getRandomResult(final String sqlOrNamedSql, final Serializable entity, final double randomCount)
			throws Exception;

	public List getRandomResult(final String sqlOrNamedSql, final String[] paramsNamed, final Object[] paramsValue,
			final Class voClass, final double randomCount) throws Exception;

	/**
	 * @todo 批量集合通过sql进行修改操作
	 * @param sqlOrNamedSql
	 * @param dataSet
	 * @param insertCallhandler
	 * @param autoCommit
	 * @throws Exception
	 */
	public Long batchUpdate(final String sqlOrNamedSql, final List dataSet,
			final InsertRowCallbackHandler insertCallhandler, final Boolean autoCommit) throws Exception;

	/**
	 * @todo 获取并锁定数据并进行修改(只支持针对单表查询，查询语句要简单)
	 * @param queryExecutor
	 * @param updateRowHandler
	 * @return
	 * @throws Exception
	 */
	public List updateFetch(final QueryExecutor queryExecutor, final UpdateRowHandler updateRowHandler)
			throws Exception;

	@Deprecated
	public List updateFetchTop(final QueryExecutor queryExecutor, final Integer topSize,
			final UpdateRowHandler updateRowHandler) throws Exception;

	/**
	 * @todo 随机提取符合条件的记录,锁定并进行修改
	 * @param queryExecutor
	 * @param random
	 * @param updateRowHandler
	 * @return
	 * @throws Exception
	 */
	@Deprecated
	public List updateFetchRandom(final QueryExecutor queryExecutor, final Integer random,
			final UpdateRowHandler updateRowHandler) throws Exception;

	/**
	 * @todo 执行sql,并返回被修改的记录数量
	 * @param sqlOrNamedSql
	 * @param entity
	 * @param reflectPropertyHandler
	 * @return Long updateCount
	 * @throws Exception
	 */
	public Long executeSql(final String sqlOrNamedSql, final Serializable entity,
			final ReflectPropertyHandler reflectPropertyHandler) throws Exception;

	public Long executeSql(final String sqlOrNamedSql, final String[] paramsNamed, final Object[] paramsValue)
			throws Exception;

	/**
	 * @todo 构造树形表的节点路径、层次等级、是否叶子节点等必要信息
	 * @param treeTableModel
	 * @return
	 * @throws Exception
	 */
	public boolean wrapTreeTableRoute(final TreeTableModel treeTableModel) throws Exception;

	/**
	 * 数据库提交
	 */
	public void flush() throws Exception;

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
	 * @throws Exception
	 */
	public long generateBizId(String signature, int increment) throws Exception;

	/**
	 * @todo 根据对象类型以及通过对象配置的业务主键策略产生ID
	 * @param entityClass
	 * @param bizDate
	 * @return
	 * @throws Exception
	 */
	public String generateBizId(Class entityClass, Date bizDate) throws Exception;

	/**
	 * @todo 根据实体对象对应的POJO配置的业务主键策略,提取对象的属性值产生业务主键
	 * @param entity
	 * @return
	 * @throws Exception
	 */
	public String generateBizId(Serializable entity) throws Exception;

}
