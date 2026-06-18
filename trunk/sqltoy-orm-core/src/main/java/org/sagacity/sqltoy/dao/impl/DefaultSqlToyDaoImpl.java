package org.sagacity.sqltoy.dao.impl;

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
import org.sagacity.sqltoy.dao.SqlToyDao;
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
import org.sagacity.sqltoy.support.SqlToyDaoSupport;
import org.sagacity.sqltoy.translate.TranslateHandler;

/**
 * @project sqltoy-orm
 * @description SqlToyDao 的默认实现类，直接继承 SqlToyDaoSupport， 不依赖
 *              LightDao/DefaultLightDaoImpl，实现完整规范命名的查询方法
 * @author zhongxuchen
 * @version v1.0,Date:2025年
 */
@SuppressWarnings({ "rawtypes" })
public class DefaultSqlToyDaoImpl extends SqlToyDaoSupport implements SqlToyDao {

	public DefaultSqlToyDaoImpl() {
		super();
	}

	// ============================================
	// 链式操作入口
	// ============================================

	@Override
	public Elastic elastic() {
		return super.elastic();
	}

	@Override
	public Mongo mongo() {
		return super.mongo();
	}

	@Override
	public Delete delete() {
		return super.delete();
	}

	@Override
	public Update update() {
		return super.update();
	}

	@Override
	public Store store() {
		return super.store();
	}

	@Override
	public Save save() {
		return super.save();
	}

	@Override
	public Query query() {
		return super.query();
	}

	@Override
	public Load load() {
		return super.load();
	}

	@Override
	public Unique unique() {
		return super.unique();
	}

	@Override
	public TreeTable treeTable() {
		return super.treeTable();
	}

	@Override
	public Execute execute() {
		return super.execute();
	}

	@Override
	public Batch batch() {
		return super.batch();
	}

	@Override
	public TableApi tableApi() {
		return super.tableApi();
	}

	// ============================================
	// 工具方法
	// ============================================

	@Override
	public SqlToyConfig getSqlToyConfig(String sqlKey, SqlType sqlType) {
		return super.getSqlToyConfig(sqlKey, sqlType);
	}

	@Override
	public EntityMeta getEntityMeta(Class entityClass) {
		return super.getEntityMeta(entityClass);
	}

	@Override
	public boolean isUnique(Serializable entity, String... paramsNamed) {
		return super.isUnique(entity, paramsNamed);
	}

	@Override
	public Long count(String sqlOrSqlId, Map<String, Object> paramsMap) {
		return super.getCountBySql(sqlOrSqlId, paramsMap);
	}

	@Override
	public Long count(Class entityClass, EntityQuery entityQuery) {
		return super.getCountByEntityQuery(entityClass, entityQuery);
	}

	@Override
	public void flush() {
		super.flush();
	}

	@Override
	public SqlToyContext getSqlToyContext() {
		return super.sqlToyContext;
	}

	@Override
	public DataSource getDataSource() {
		return super.getDataSource(dataSource);
	}

	@Override
	public long generateBizId(String signature, int increment) {
		return super.generateBizId(signature, increment);
	}

	@Override
	public String generateBizId(Serializable entity) {
		return super.generateBizId(entity);
	}

	@Override
	public String generateBizId(String tableName, String signature, Map<String, Object> keyValues, LocalDate bizDate,
			int length, int sequenceSize) {
		return super.generateBizId(tableName, signature, keyValues, bizDate, length, sequenceSize);
	}

	@Override
	public HashMap<String, Object[]> getTranslateCache(String cacheName, String cacheType) {
		return super.getTranslateCache(cacheName, cacheType);
	}

	@Override
	public <T> List<T> getTranslateCache(String cacheName, String cacheType, Class<T> reusltType) {
		return super.getTranslateCache(cacheName, cacheType, reusltType);
	}

	@Override
	public void translate(Collection dataSet, String cacheName, TranslateHandler handler) {
		super.translate(dataSet, cacheName, null, 1, handler);
	}

	@Override
	public void translate(Collection dataSet, String cacheName, String cacheType, Integer cacheNameIndex,
			TranslateHandler handler) {
		super.translate(dataSet, cacheName, cacheType, cacheNameIndex, handler);
	}

	@Override
	public String[] cacheMatchKeys(CacheMatchFilter cacheMatchFilter, String... matchRegexes) {
		return super.cacheMatchKeys(cacheMatchFilter, matchRegexes);
	}

	@Override
	public boolean existCache(String cacheName) {
		return super.existCache(cacheName);
	}

	@Override
	public Set<String> getCacheNames() {
		return super.getCacheNames();
	}

	@Override
	public <T extends Serializable> T convertType(Serializable source, Class<T> resultType,
			String... ignoreProperties) {
		return super.convertType(source, resultType, ignoreProperties);
	}

	@Override
	public <T extends Serializable> List<T> convertType(List sourceList, Class<T> resultType,
			String... ignoreProperties) {
		return super.convertType(sourceList, resultType, ignoreProperties);
	}

	@Override
	public <T extends Serializable> Page<T> convertType(Page sourcePage, Class<T> resultType,
			String... ignoreProperties) {
		return super.convertType(sourcePage, resultType, ignoreProperties);
	}

	@Override
	public <T> List<QueryResult<T>> parallelQuery(List<ParallQuery> parallelQueryList, Map<String, Object> paramsMap) {
		return super.parallQuery(parallelQueryList, paramsMap, new ParallelConfig());
	}

	@Override
	public <T> List<QueryResult<T>> parallelQuery(List<ParallQuery> parallelQueryList, Map<String, Object> paramsMap,
			ParallelConfig parallelConfig) {
		return super.parallQuery(parallelQueryList, paramsMap, parallelConfig);
	}

	// ============================================
	// 存储过程
	// ============================================

	@Override
	public StoreResult executeStore(String storeSqlOrKey, Object[] inParamValues) {
		return super.executeStore(storeSqlOrKey, inParamValues);
	}

	@Override
	public StoreResult executeStore(String storeSqlOrKey, Object[] inParamValues, Integer[] outParamsType,
			Class resultType) {
		return super.executeStore(storeSqlOrKey, inParamValues, outParamsType, resultType);
	}

	@Override
	public StoreResult executeMoreResultStore(String storeSqlOrKey, Object[] inParamValues, Integer[] outParamsType,
			Class... resultTypes) {
		return super.executeMoreResultStore(storeSqlOrKey, inParamValues, outParamsType, resultTypes);
	}

	@Override
	public void findStream(QueryExecutor queryExecutor, StreamResultHandler streamResultHandler) {
		super.fetchStream(queryExecutor, streamResultHandler);
	}

	// ============================================
	// 增删改
	// ============================================

	@Override
	public Object save(Serializable entity) {
		return super.save(entity);
	}

	@Override
	public <T extends Serializable> Long saveAll(List<T> entities) {
		return super.saveAll(entities);
	}

	@Override
	public <T extends Serializable> Long saveAllIgnoreExist(List<T> entities) {
		return super.saveAllIgnoreExist(entities);
	}

	@Override
	public Long update(Serializable entity, String... forceUpdateProps) {
		return super.update(entity, forceUpdateProps);
	}

	@Override
	public Long updateDeeply(Serializable entity) {
		return super.updateDeeply(entity);
	}

	@Override
	public List updateFetch(QueryExecutor queryExecutor, UpdateRowHandler updateRowHandler) {
		return super.updateFetch(queryExecutor, updateRowHandler);
	}

	@Override
	public <T extends Serializable> T updateSaveFetch(T entity, UpdateRowHandler updateRowHandler,
			String... uniqueProps) {
		return super.updateSaveFetch(entity, updateRowHandler, uniqueProps, dataSource);
	}

	@Override
	public <T extends Serializable> T updateSaveFetch(T entity, EntityUpdateCallback<T> callback,
			String... uniqueProps) {
		return super.updateSaveFetch(entity, callback, uniqueProps, dataSource);
	}

	@Override
	public Long updateByQuery(Class entityClass, EntityUpdate entityUpdate) {
		return super.updateByQuery(entityClass, entityUpdate);
	}

	@Override
	public Long updateCascade(Serializable entity, String[] forceUpdateProps, Class[] forceCascadeClasses,
			HashMap<Class, String[]> subTableForceUpdateProps) {
		return super.updateCascade(entity, forceUpdateProps, forceCascadeClasses, subTableForceUpdateProps);
	}

	@Override
	public <T extends Serializable> Long updateAll(List<T> entities, String... forceUpdateProps) {
		return super.updateAll(entities, forceUpdateProps);
	}

	@Override
	public <T extends Serializable> Long updateAllDeeply(List<T> entities) {
		return super.updateAllDeeply(entities);
	}

	@Override
	public Long saveOrUpdate(Serializable entity, String... forceUpdateProps) {
		return super.saveOrUpdate(entity, forceUpdateProps);
	}

	@Override
	public <T extends Serializable> Long saveOrUpdateAll(List<T> entities, String... forceUpdateProps) {
		return super.saveOrUpdateAll(entities, forceUpdateProps);
	}

	@Override
	public Long delete(Serializable entity) {
		return super.delete(entity);
	}

	@Override
	public <T extends Serializable> Long deleteAll(List<T> entities) {
		return super.deleteAll(entities);
	}

	@Override
	public Long deleteByIds(Class entityClass, Object... ids) {
		return super.deleteByIds(entityClass, ids);
	}

	@Override
	public Long deleteByQuery(Class entityClass, EntityQuery entityQuery) {
		return super.deleteByQuery(entityClass, entityQuery);
	}

	@Override
	public void truncate(Class entityClass) {
		super.truncate(entityClass, null);
	}

	@Override
	public Long batchExecute(String sqlOrSqlId, List dataSet) {
		return super.batchUpdate(sqlOrSqlId, dataSet, null);
	}

	@Override
	public Long batchExecute(String sqlOrSqlId, List dataSet, Boolean autoCommit) {
		return super.batchUpdate(sqlOrSqlId, dataSet, autoCommit);
	}

	@Override
	public Object insertReturnPrimaryKey(String sqlOrSqlId, Serializable entity, String pkField) {
		return super.execute().sql(sqlOrSqlId).entity(entity).insertReturnPrimaryKey(pkField);
	}

	@Override
	public Long executeSql(String sqlOrSqlId) {
		return super.executeSql(sqlOrSqlId);
	}

	@Override
	public Long executeSql(String sqlOrSqlId, Serializable params) {
		return super.executeSql(sqlOrSqlId, params);
	}

	@Override
	public Long executeSql(String sqlOrSqlId, Object... paramsValue) {
		return super.executeSql(sqlOrSqlId, null, paramsValue);
	}

	@Override
	public Long executeSql(String sqlOrSqlId, Map<String, Object> paramsMap) {
		return super.executeSql(sqlOrSqlId, paramsMap);
	}

	// ============================================
	// 按主键查询 - findOneById / findAllByIds
	// ============================================

	@Override
	public <T extends Serializable> T findOneById(Class<T> entityClass, Object id) {
		List<T> result = super.loadByIds(entityClass, id);
		if (result == null || result.isEmpty()) {
			return null;
		}
		if (result.size() == 1) {
			return result.get(0);
		}
		throw new IllegalArgumentException("findOneById 查询出:" + result.size() + " 条记录，不符合查询单条记录的预期!");
	}

	@Override
	public <T extends Serializable> T findOneById(Class<T> entityClass, Object id, LockMode lockMode) {
		List<T> result = super.loadByIds(entityClass, lockMode, id);
		if (result == null || result.isEmpty()) {
			return null;
		}
		if (result.size() == 1) {
			return result.get(0);
		}
		throw new IllegalArgumentException("findOneById 查询出:" + result.size() + " 条记录，不符合查询单条记录的预期!");
	}

	@Override
	public <T extends Serializable> List<T> findAllByIds(Class<T> entityClass, Object... ids) {
		return super.loadByIds(entityClass, ids);
	}

	@Override
	public <T extends Serializable> List<T> findAllByIds(Class<T> entityClass, LockMode lockMode, Object... ids) {
		return super.loadByIds(entityClass, lockMode, ids);
	}

	// ============================================
	// 按实体主键查询 - findOneByEntity / findAllByEntities
	// ============================================

	@Override
	public <T extends Serializable> T findOneByEntity(T entity) {
		return super.load(entity);
	}

	@Override
	public <T extends Serializable> T findOneByEntity(T entity, LockMode lockMode) {
		return super.load(entity, lockMode);
	}

	@Override
	public <T extends Serializable> List<T> findAllByEntities(List<T> entities) {
		return super.loadAll(entities, null);
	}

	@Override
	public <T extends Serializable> List<T> findAllByEntities(List<T> entities, LockMode lockMode) {
		return super.loadAll(entities, lockMode);
	}

	// ============================================
	// 级联加载 - findOneCascade / findAllCascade
	// ============================================

	@Override
	public <T extends Serializable> T findOneCascade(T entity, Class... cascadeTypes) {
		return super.loadCascade(entity, null, cascadeTypes);
	}

	@Override
	public <T extends Serializable> T findOneCascade(T entity, LockMode lockMode, Class... cascadeTypes) {
		return super.loadCascade(entity, lockMode, cascadeTypes);
	}

	@Override
	public <T extends Serializable> List<T> findAllCascade(List<T> entities, Class... cascadeTypes) {
		return super.loadAllCascade(entities, null, cascadeTypes);
	}

	@Override
	public <T extends Serializable> List<T> findAllCascade(List<T> entities, LockMode lockMode, Class... cascadeTypes) {
		return super.loadAllCascade(entities, lockMode, cascadeTypes);
	}

	// ============================================
	// 查询单条记录 - findOne
	// ============================================

	@Override
	public <T> T findOne(String sqlOrSqlId, Map<String, Object> paramsMap, Class<T> resultType) {
		return super.loadBySql(sqlOrSqlId, paramsMap, resultType);
	}

	@Override
	public <T> T findOne(String sqlOrSqlId, Serializable params, Class<T> resultType) {
		return (T) super.loadByQuery(new QueryExecutor(sqlOrSqlId, params).resultType(resultType));
	}

	@Override
	public Object findOne(QueryExecutor queryExecutor) {
		return super.loadByQuery(queryExecutor);
	}

	@Override
	public <T extends Serializable> T findOne(Class<T> entityClass, EntityQuery entityQuery) {
		return super.loadEntity(entityClass, entityQuery);
	}

	@Override
	public <T> T findOne(Class entityClass, EntityQuery entityQuery, Class<T> resultType) {
		List<T> result = super.findEntity(entityClass, entityQuery, resultType);
		if (result == null || result.isEmpty()) {
			return null;
		}
		if (result.size() == 1) {
			return result.get(0);
		}
		throw new IllegalArgumentException("findOne查询出:" + result.size() + " 条记录,不符合查询单条记录的预期!");
	}

	// ============================================
	// 查询多条记录 - findList
	// ============================================

	@Override
	public <T> List<T> findList(String sqlOrSqlId, Map<String, Object> paramsMap, Class<T> resultType) {
		return super.findBySql(sqlOrSqlId, paramsMap, resultType);
	}

	@Override
	public List findList(String sqlOrSqlId, Map<String, Object> paramsMap) {
		return super.findBySql(sqlOrSqlId, paramsMap, null);
	}

	@Override
	public <T> List<T> findList(String sqlOrSqlId, Serializable params, Class<T> resultType) {
		return (List<T>) super.findByQuery(new QueryExecutor(sqlOrSqlId, params).resultType(resultType)).getRows();
	}

	@Override
	public QueryResult findList(QueryExecutor queryExecutor) {
		return super.findByQuery(queryExecutor);
	}

	@Override
	public <T> List<T> findList(Class<T> entityClass, EntityQuery entityQuery) {
		return super.findEntity(entityClass, entityQuery);
	}

	@Override
	public <T> List<T> findList(Class entityClass, EntityQuery entityQuery, Class<T> resultType) {
		return super.findEntity(entityClass, entityQuery, resultType);
	}

	// ============================================
	// Top/随机/分页查询 - findTop / findRandom / findPage
	// ============================================

	@Override
	public <T> List<T> findTop(String sqlOrSqlId, Map<String, Object> paramsMap, Class<T> resultType, double topSize) {
		return super.findTopBySql(sqlOrSqlId, paramsMap, resultType, topSize);
	}

	@Override
	public <T> List<T> findTop(String sqlOrSqlId, Serializable params, Class<T> resultType, double topSize) {
		return (List<T>) super.findTopByQuery(new QueryExecutor(sqlOrSqlId, params).resultType(resultType), topSize)
				.getRows();
	}

	@Override
	public QueryResult findTop(QueryExecutor queryExecutor, double topSize) {
		return super.findTopByQuery(queryExecutor, topSize);
	}

	@Override
	public <T> List<T> findRandom(String sqlOrSqlId, Serializable params, Class<T> resultType, double randomCount) {
		return (List<T>) super.getRandomResult(new QueryExecutor(sqlOrSqlId, params).resultType(resultType),
				randomCount).getRows();
	}

	@Override
	public <T> List<T> findRandom(String sqlOrSqlId, Map<String, Object> paramsMap, Class<T> resultType,
			double randomCount) {
		return super.getRandomResult(sqlOrSqlId, paramsMap, resultType, randomCount);
	}

	@Override
	public QueryResult findRandom(QueryExecutor queryExecutor, double randomCount) {
		return super.getRandomResult(queryExecutor, randomCount);
	}

	@Override
	public Page findPage(Page page, String sqlOrSqlId, Map<String, Object> paramsMap) {
		return super.findPageBySql(page, sqlOrSqlId, paramsMap, null);
	}

	@Override
	public <T> Page<T> findPage(Page page, String sqlOrSqlId, Map<String, Object> paramsMap, Class<T> resultType) {
		return super.findPageBySql(page, sqlOrSqlId, paramsMap, resultType);
	}

	@Override
	public <T> Page<T> findPage(Page page, String sqlOrSqlId, Serializable params, Class<T> resultType) {
		return (Page<T>) super.findPageByQuery(page, new QueryExecutor(sqlOrSqlId, params).resultType(resultType))
				.getPageResult();
	}

	@Override
	public QueryResult findPage(Page page, QueryExecutor queryExecutor) {
		return super.findPageByQuery(page, queryExecutor);
	}

	@Override
	public <T> Page<T> findPage(Page page, Class<T> entityClass, EntityQuery entityQuery) {
		return super.findPageEntity(page, entityClass, entityQuery);
	}

	@Override
	public <T> Page<T> findPage(Page page, Class entityClass, EntityQuery entityQuery, Class<T> resultType) {
		return super.findPageEntity(page, entityClass, entityQuery, resultType);
	}

	// ============================================
	// 其他工具
	// ============================================

	@Override
	public Object getValue(String sqlOrSqlId, Map<String, Object> paramsMap) {
		return super.getSingleValue(sqlOrSqlId, paramsMap);
	}

	@Override
	public <T> T getValue(String sqlOrSqlId, Map<String, Object> paramsMap, Class<T> resultType) {
		return super.getSingleValue(sqlOrSqlId, paramsMap, resultType);
	}

	@Override
	public boolean wrapTreeTableRoute(TreeTableModel treeTableModel) {
		return super.wrapTreeTableRoute(treeTableModel);
	}

}
