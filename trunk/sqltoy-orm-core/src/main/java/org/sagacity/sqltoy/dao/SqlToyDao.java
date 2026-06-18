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
import org.sagacity.sqltoy.callback.EntityUpdateCallback;
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
import org.sagacity.sqltoy.link.TableApi;
import org.sagacity.sqltoy.link.TreeTable;
import org.sagacity.sqltoy.link.Unique;
import org.sagacity.sqltoy.link.Update;
import org.sagacity.sqltoy.model.CacheMatchFilter;
import org.sagacity.sqltoy.model.EntityQuery;
import org.sagacity.sqltoy.model.EntityUpdate;
import org.sagacity.sqltoy.model.LockMode;
import org.sagacity.sqltoy.model.Page;
import org.sagacity.sqltoy.model.ParallQuery;
import org.sagacity.sqltoy.model.ParallelConfig;
import org.sagacity.sqltoy.model.QueryExecutor;
import org.sagacity.sqltoy.model.QueryResult;
import org.sagacity.sqltoy.model.StoreResult;
import org.sagacity.sqltoy.model.TreeTableModel;
import org.sagacity.sqltoy.translate.TranslateHandler;

/**
 * @project sqltoy-orm
 * @description 独立DAO接口，不继承LightDao，提供完整的规范命名方法体系。 查询方法统一命名规范： - findOneById /
 *              findAllByIds: 按主键查询 - findOneByEntity: 按实体主键查询单条 -
 *              findAllByEntities: 按实体集合批量加载 - findOneCascade / findAllCascade:
 *              级联加载 - findOne: 按条件/SQL/QueryExecutor查询单条 - findList:
 *              按条件/SQL/QueryExecutor查询多条 - findTop / findRandom / findPage:
 *              Top/随机/分页查询
 * @author zhongxuchen
 * @version v1.0,Date:2025年
 */
@SuppressWarnings({ "rawtypes" })
public interface SqlToyDao {

	// ============================================
	// 链式操作入口
	// ============================================

	public Elastic elastic();

	public Mongo mongo();

	public Delete delete();

	public Update update();

	public Store store();

	public Save save();

	public Query query();

	public Load load();

	public Unique unique();

	public TreeTable treeTable();

	public Execute execute();

	public Batch batch();

	public TableApi tableApi();

	// ============================================
	// 工具方法
	// ============================================

	public SqlToyConfig getSqlToyConfig(String sqlKey, SqlType sqlType);

	public EntityMeta getEntityMeta(Class entityClass);

	public boolean isUnique(Serializable entity, String... paramsNamed);

	public Long count(String sqlOrSqlId, Map<String, Object> paramsMap);

	public Long count(Class entityClass, EntityQuery entityQuery);

	public void flush();

	public SqlToyContext getSqlToyContext();

	public DataSource getDataSource();

	public long generateBizId(String signature, int increment);

	public String generateBizId(Serializable entity);

	public String generateBizId(String tableName, String signature, Map<String, Object> keyValues, LocalDate bizDate,
			int length, int sequenceSize);

	public HashMap<String, Object[]> getTranslateCache(String cacheName, String cacheType);

	public <T> List<T> getTranslateCache(String cacheName, String cacheType, Class<T> reusltType);

	public void translate(Collection dataSet, String cacheName, TranslateHandler handler);

	public void translate(Collection dataSet, String cacheName, String cacheType, Integer cacheNameIndex,
			TranslateHandler handler);

	public String[] cacheMatchKeys(CacheMatchFilter cacheMatchFilter, String... matchRegexes);

	public boolean existCache(String cacheName);

	public Set<String> getCacheNames();

	public <T extends Serializable> T convertType(Serializable source, Class<T> resultType, String... ignoreProperties);

	public <T extends Serializable> List<T> convertType(List sourceList, Class<T> resultType,
			String... ignoreProperties);

	public <T extends Serializable> Page<T> convertType(Page sourcePage, Class<T> resultType,
			String... ignoreProperties);

	public <T> List<QueryResult<T>> parallelQuery(List<ParallQuery> parallelQueryList, Map<String, Object> paramsMap);

	public <T> List<QueryResult<T>> parallelQuery(List<ParallQuery> parallelQueryList, Map<String, Object> paramsMap,
			ParallelConfig parallelConfig);

	// ============================================
	// 存储过程
	// ============================================

	public StoreResult executeStore(String storeSqlOrKey, Object[] inParamValues);

	public StoreResult executeStore(String storeSqlOrKey, Object[] inParamValues, Integer[] outParamsType,
			Class resultType);

	public StoreResult executeMoreResultStore(String storeSqlOrKey, Object[] inParamValues, Integer[] outParamsType,
			Class... resultTypes);

	public void findStream(QueryExecutor queryExecutor, StreamResultHandler streamResultHandler);

	// ============================================
	// 增删改
	// ============================================

	public Object save(Serializable entity);

	public <T extends Serializable> Long saveAll(List<T> entities);

	public <T extends Serializable> Long saveAllIgnoreExist(List<T> entities);

	public Long update(Serializable entity, String... forceUpdateProps);

	public Long updateDeeply(Serializable entity);

	public List updateFetch(QueryExecutor queryExecutor, UpdateRowHandler updateRowHandler);

	public <T extends Serializable> T updateSaveFetch(T entity, UpdateRowHandler updateRowHandler,
			String... uniqueProps);

	public <T extends Serializable> T updateSaveFetch(T entity, EntityUpdateCallback<T> callback,
			String... uniqueProps);

	public Long updateByQuery(Class entityClass, EntityUpdate entityUpdate);

	public Long updateCascade(Serializable entity, String[] forceUpdateProps, Class[] forceCascadeClasses,
			HashMap<Class, String[]> subTableForceUpdateProps);

	public <T extends Serializable> Long updateAll(List<T> entities, String... forceUpdateProps);

	public <T extends Serializable> Long updateAllDeeply(List<T> entities);

	public Long saveOrUpdate(Serializable entity, String... forceUpdateProps);

	public <T extends Serializable> Long saveOrUpdateAll(List<T> entities, String... forceUpdateProps);

	public Long delete(Serializable entity);

	public <T extends Serializable> Long deleteAll(List<T> entities);

	public Long deleteByIds(Class entityClass, Object... ids);

	public Long deleteByQuery(Class entityClass, EntityQuery entityQuery);

	public void truncate(Class entityClass);

	public Long batchExecute(String sqlOrSqlId, List dataSet);

	public Long batchExecute(String sqlOrSqlId, List dataSet, Boolean autoCommit);

	public Object insertReturnPrimaryKey(String sqlOrSqlId, Serializable entity, String pkField);

	public Long executeSql(String sqlOrSqlId);

	public Long executeSql(String sqlOrSqlId, Serializable params);

	public Long executeSql(String sqlOrSqlId, Object... paramsValue);

	public Long executeSql(String sqlOrSqlId, Map<String, Object> paramsMap);

	// ============================================
	// 按主键查询 - findOneById / findAllByIds
	// ============================================

	/**
	 * @TODO 根据主键加载单个实体对象
	 * @param <T>
	 * @param entityClass 实体类
	 * @param id          主键值
	 * @return 单个实体对象
	 */
	<T extends Serializable> T findOneById(Class<T> entityClass, Object id);

	/**
	 * @TODO 根据主键加载单个实体对象（带锁）
	 * @param <T>
	 * @param entityClass 实体类
	 * @param id          主键值
	 * @param lockMode    锁模式
	 * @return 单个实体对象
	 */
	<T extends Serializable> T findOneById(Class<T> entityClass, Object id, LockMode lockMode);

	/**
	 * @TODO 根据主键集合批量加载对象
	 * @param <T>
	 * @param entityClass 实体类
	 * @param ids         主键值数组
	 * @return 对象列表
	 */
	<T extends Serializable> List<T> findAllByIds(Class<T> entityClass, Object... ids);

	/**
	 * @TODO 根据主键集合批量加载对象（带锁）
	 * @param <T>
	 * @param entityClass 实体类
	 * @param lockMode    锁模式
	 * @param ids         主键值数组
	 * @return 对象列表
	 */
	<T extends Serializable> List<T> findAllByIds(Class<T> entityClass, LockMode lockMode, Object... ids);

	// ============================================
	// 按实体主键查询 - findOneByEntity / findAllByEntities
	// ============================================

	/**
	 * @TODO 根据实体主键加载单个对象
	 * @param <T>
	 * @param entity 包含主键值的实体对象
	 * @return 单个实体对象
	 */
	<T extends Serializable> T findOneByEntity(T entity);

	/**
	 * @TODO 根据实体主键加载单个对象（带锁）
	 * @param <T>
	 * @param entity   包含主键值的实体对象
	 * @param lockMode 锁模式
	 * @return 单个实体对象
	 */
	<T extends Serializable> T findOneByEntity(T entity, LockMode lockMode);

	/**
	 * @TODO 根据实体集合按主键批量加载对象
	 * @param <T>
	 * @param entities 包含主键值的实体集合
	 * @return 对象列表
	 */
	<T extends Serializable> List<T> findAllByEntities(List<T> entities);

	/**
	 * @TODO 根据实体集合按主键批量加载对象（带锁）
	 * @param <T>
	 * @param entities 包含主键值的实体集合
	 * @param lockMode 锁模式
	 * @return 对象列表
	 */
	<T extends Serializable> List<T> findAllByEntities(List<T> entities, LockMode lockMode);

	// ============================================
	// 级联加载 - findOneCascade / findAllCascade
	// ============================================

	/**
	 * @TODO 加载单个对象并级联加载子对象
	 * @param <T>
	 * @param entity       包含主键值的实体对象
	 * @param cascadeTypes 需要级联加载的子类类型
	 * @return 含级联数据的实体对象
	 */
	<T extends Serializable> T findOneCascade(T entity, Class... cascadeTypes);

	/**
	 * @TODO 加载单个对象并级联加载子对象（带锁）
	 * @param <T>
	 * @param entity       包含主键值的实体对象
	 * @param lockMode     锁模式
	 * @param cascadeTypes 需要级联加载的子类类型
	 * @return 含级联数据的实体对象
	 */
	<T extends Serializable> T findOneCascade(T entity, LockMode lockMode, Class... cascadeTypes);

	/**
	 * @TODO 批量加载对象并级联加载子对象
	 * @param <T>
	 * @param entities     包含主键值的实体集合
	 * @param cascadeTypes 需要级联加载的子类类型
	 * @return 含级联数据的实体列表
	 */
	<T extends Serializable> List<T> findAllCascade(List<T> entities, Class... cascadeTypes);

	/**
	 * @TODO 批量加载对象并级联加载子对象（带锁）
	 * @param <T>
	 * @param entities     包含主键值的实体集合
	 * @param lockMode     锁模式
	 * @param cascadeTypes 需要级联加载的子类类型
	 * @return 含级联数据的实体列表
	 */
	<T extends Serializable> List<T> findAllCascade(List<T> entities, LockMode lockMode, Class... cascadeTypes);

	// ============================================
	// 查询单条记录 - findOne
	// ============================================

	/**
	 * @TODO 通过Map传参查询单个对象
	 * @param <T>
	 * @param sqlOrSqlId SQL语句或SQL ID
	 * @param paramsMap  参数Map
	 * @param resultType 返回结果类型
	 * @return 单个对象
	 */
	<T> T findOne(String sqlOrSqlId, Map<String, Object> paramsMap, Class<T> resultType);

	/**
	 * @TODO 通过对象传参查询单个对象
	 * @param <T>
	 * @param sqlOrSqlId SQL语句或SQL ID
	 * @param params     参数对象
	 * @param resultType 返回结果类型
	 * @return 单个对象
	 */
	<T> T findOne(String sqlOrSqlId, Serializable params, Class<T> resultType);

	/**
	 * @TODO 通过QueryExecutor查询单个对象
	 * @param queryExecutor 查询执行器
	 * @return 单个对象
	 */
	Object findOne(QueryExecutor queryExecutor);

	/**
	 * @TODO 通过EntityQuery条件查询单个对象
	 * @param <T>
	 * @param entityClass 实体类
	 * @param entityQuery 查询条件
	 * @return 单个实体对象
	 */
	<T extends Serializable> T findOne(Class<T> entityClass, EntityQuery entityQuery);

	/**
	 * @TODO 通过EntityQuery条件查询单个对象（指定返回类型）
	 * @param <T>
	 * @param entityClass 实体类
	 * @param entityQuery 查询条件
	 * @param resultType  返回结果类型
	 * @return 单个对象
	 */
	<T> T findOne(Class entityClass, EntityQuery entityQuery, Class<T> resultType);

	// ============================================
	// 查询多条记录 - findList
	// ============================================

	/**
	 * @TODO 通过SQL查询对象列表（Map传参）
	 * @param <T>
	 * @param sqlOrSqlId SQL语句或SQL ID
	 * @param paramsMap  参数Map
	 * @param resultType 返回结果类型
	 * @return 对象列表
	 */
	<T> List<T> findList(String sqlOrSqlId, Map<String, Object> paramsMap, Class<T> resultType);

	/**
	 * @TODO 通过SQL查询对象列表（Map传参，无指定类型）
	 * @param sqlOrSqlId SQL语句或SQL ID
	 * @param paramsMap  参数Map
	 * @return 对象列表
	 */
	List findList(String sqlOrSqlId, Map<String, Object> paramsMap);

	/**
	 * @TODO 通过SQL查询对象列表（对象传参）
	 * @param <T>
	 * @param sqlOrSqlId SQL语句或SQL ID
	 * @param params     参数对象
	 * @param resultType 返回结果类型
	 * @return 对象列表
	 */
	<T> List<T> findList(String sqlOrSqlId, Serializable params, Class<T> resultType);

	/**
	 * @TODO 通过QueryExecutor查询对象列表
	 * @param queryExecutor 查询执行器
	 * @return QueryResult 包含对象列表
	 */
	QueryResult findList(QueryExecutor queryExecutor);

	/**
	 * @TODO 通过EntityQuery条件查询对象列表
	 * @param <T>
	 * @param entityClass 实体类
	 * @param entityQuery 查询条件
	 * @return 对象列表
	 */
	<T> List<T> findList(Class<T> entityClass, EntityQuery entityQuery);

	/**
	 * @TODO 通过EntityQuery条件查询对象列表（指定返回类型）
	 * @param <T>
	 * @param entityClass 实体类
	 * @param entityQuery 查询条件
	 * @param resultType  返回结果类型
	 * @return 对象列表
	 */
	<T> List<T> findList(Class entityClass, EntityQuery entityQuery, Class<T> resultType);

	// ============================================
	// Top/随机/分页查询 - findTop / findRandom / findPage
	// ============================================

	/**
	 * @TODO 查询前N条记录（Map传参）
	 * @param <T>
	 * @param sqlOrSqlId SQL语句或SQL ID
	 * @param paramsMap  参数Map
	 * @param resultType 返回结果类型
	 * @param topSize    前N条（大于1为固定数量，小于1为比例）
	 * @return 对象列表
	 */
	<T> List<T> findTop(String sqlOrSqlId, Map<String, Object> paramsMap, Class<T> resultType, double topSize);

	/**
	 * @TODO 查询前N条记录（对象传参）
	 * @param <T>
	 * @param sqlOrSqlId SQL语句或SQL ID
	 * @param params     参数对象
	 * @param resultType 返回结果类型
	 * @param topSize    前N条（大于1为固定数量，小于1为比例）
	 * @return 对象列表
	 */
	<T> List<T> findTop(String sqlOrSqlId, Serializable params, Class<T> resultType, double topSize);

	/**
	 * @TODO 查询前N条记录（QueryExecutor模式）
	 * @param queryExecutor 查询执行器
	 * @param topSize       前N条（大于1为固定数量，小于1为比例）
	 * @return QueryResult 包含对象列表
	 */
	QueryResult findTop(QueryExecutor queryExecutor, double topSize);

	/**
	 * @TODO 随机查询记录（对象传参）
	 * @param <T>
	 * @param sqlOrSqlId  SQL语句或SQL ID
	 * @param params      参数对象
	 * @param resultType  返回结果类型
	 * @param randomCount 随机数量（大于1为固定数量，小于1为比例）
	 * @return 对象列表
	 */
	<T> List<T> findRandom(String sqlOrSqlId, Serializable params, Class<T> resultType, double randomCount);

	/**
	 * @TODO 随机查询记录（Map传参）
	 * @param <T>
	 * @param sqlOrSqlId  SQL语句或SQL ID
	 * @param paramsMap   参数Map
	 * @param resultType  返回结果类型
	 * @param randomCount 随机数量（大于1为固定数量，小于1为比例）
	 * @return 对象列表
	 */
	<T> List<T> findRandom(String sqlOrSqlId, Map<String, Object> paramsMap, Class<T> resultType, double randomCount);

	/**
	 * @TODO 随机查询记录（QueryExecutor模式）
	 * @param queryExecutor 查询执行器
	 * @param randomCount   随机数量（大于1为固定数量，小于1为比例）
	 * @return QueryResult 包含对象列表
	 */
	QueryResult findRandom(QueryExecutor queryExecutor, double randomCount);

	/**
	 * @TODO 分页查询（Map传参，无指定类型）
	 * @param page       分页对象
	 * @param sqlOrSqlId SQL语句或SQL ID
	 * @param paramsMap  参数Map
	 * @return 分页结果
	 */
	Page findPage(Page page, String sqlOrSqlId, Map<String, Object> paramsMap);

	/**
	 * @TODO 分页查询（Map传参）
	 * @param <T>
	 * @param page       分页对象
	 * @param sqlOrSqlId SQL语句或SQL ID
	 * @param paramsMap  参数Map
	 * @param resultType 返回结果类型
	 * @return 分页结果
	 */
	<T> Page<T> findPage(Page page, String sqlOrSqlId, Map<String, Object> paramsMap, Class<T> resultType);

	/**
	 * @TODO 分页查询（对象传参）
	 * @param <T>
	 * @param page       分页对象
	 * @param sqlOrSqlId SQL语句或SQL ID
	 * @param params     参数对象
	 * @param resultType 返回结果类型
	 * @return 分页结果
	 */
	<T> Page<T> findPage(Page page, String sqlOrSqlId, Serializable params, Class<T> resultType);

	/**
	 * @TODO 分页查询（QueryExecutor模式）
	 * @param page          分页对象
	 * @param queryExecutor 查询执行器
	 * @return QueryResult 包含分页结果
	 */
	QueryResult findPage(Page page, QueryExecutor queryExecutor);

	/**
	 * @TODO 通过EntityQuery进行分页查询
	 * @param <T>
	 * @param page        分页对象
	 * @param entityClass 实体类
	 * @param entityQuery 查询条件
	 * @return 分页结果
	 */
	<T> Page<T> findPage(Page page, Class<T> entityClass, EntityQuery entityQuery);

	/**
	 * @TODO 通过EntityQuery进行分页查询（指定返回类型）
	 * @param <T>
	 * @param page        分页对象
	 * @param entityClass 实体类
	 * @param entityQuery 查询条件
	 * @param resultType  返回结果类型
	 * @return 分页结果
	 */
	<T> Page<T> findPage(Page page, Class entityClass, EntityQuery entityQuery, Class<T> resultType);

	// ============================================
	// 其他工具
	// ============================================

	public Object getValue(String sqlOrSqlId, Map<String, Object> paramsMap);

	public <T> T getValue(String sqlOrSqlId, Map<String, Object> paramsMap, Class<T> resultType);

	public boolean wrapTreeTableRoute(TreeTableModel treeTableModel);

}
