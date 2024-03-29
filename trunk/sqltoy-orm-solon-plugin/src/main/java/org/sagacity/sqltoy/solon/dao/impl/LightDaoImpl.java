package org.sagacity.sqltoy.solon.dao.impl;

import org.sagacity.sqltoy.solon.support.SolonDaoSupport;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.callback.StreamResultHandler;
import org.sagacity.sqltoy.callback.UpdateRowHandler;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlType;
import org.sagacity.sqltoy.dao.LightDao;
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
import org.sagacity.sqltoy.model.MapKit;
import org.sagacity.sqltoy.model.Page;
import org.sagacity.sqltoy.model.ParallQuery;
import org.sagacity.sqltoy.model.ParallelConfig;
import org.sagacity.sqltoy.model.QueryExecutor;
import org.sagacity.sqltoy.model.QueryResult;
import org.sagacity.sqltoy.model.StoreResult;
import org.sagacity.sqltoy.model.TreeTableModel;
import org.sagacity.sqltoy.translate.TranslateHandler;

import javax.sql.DataSource;
import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @project sqltoy-orm
 * @description 提供的更加简洁通用规范的Dao逻辑实现
 * @author limliu
 * @version v1.0,Date:2024年3月21日
 */
@SuppressWarnings({ "rawtypes" })
public class LightDaoImpl extends SolonDaoSupport implements LightDao {

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.support.SqlToyDaoSupport#delete()
	 */
	@Override
	public Delete delete() {
		return super.delete();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.support.SqlToyDaoSupport#update()
	 */
	@Override
	public Update update() {
		return super.update();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.support.SqlToyDaoSupport#store()
	 */
	@Override
	public Store store() {
		return super.store();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.support.SqlToyDaoSupport#save()
	 */
	@Override
	public Save save() {
		return super.save();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.support.SqlToyDaoSupport#query()
	 */
	@Override
	public Query query() {
		return super.query();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.support.SqlToyDaoSupport#load()
	 */
	@Override
	public Load load() {
		return super.load();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.support.SqlToyDaoSupport#unique()
	 */
	@Override
	public Unique unique() {
		return super.unique();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.support.SqlToyDaoSupport#treeTable()
	 */
	@Override
	public TreeTable treeTable() {
		return super.treeTable();
	}

	@Override
	public TableApi tableApi() {
		return super.tableApi();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.support.SqlToyDaoSupport#execute()
	 */
	@Override
	public Execute execute() {
		return super.execute();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.support.SqlToyDaoSupport#batch()
	 */
	@Override
	public Batch batch() {
		return super.batch();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dao.SqlToyLazyDao#elastic()
	 */
	@Override
	public Elastic elastic() {
		return super.elastic();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.support.SqlToyDaoSupport#mongo()
	 */
	@Override
	public Mongo mongo() {
		return super.mongo();
	}

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
	public Long getCount(String sqlOrSqlId, Map<String, Object> paramsMap) {
		return super.getCountBySql(sqlOrSqlId, paramsMap);
	}

	@Override
	public Long getCount(Class entityClass, EntityQuery entityQuery) {
		return super.getCountByEntityQuery(entityClass, entityQuery);
	}

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
	public void fetchStream(QueryExecutor queryExecutor, StreamResultHandler streamResultHandler) {
		super.fetchStream(queryExecutor, streamResultHandler);
	}

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
	public <T extends Serializable> T updateSaveFetch(T entity, UpdateRowHandler updateRowHandler,
			String... uniqueProps) {
		return super.updateSaveFetch(entity, updateRowHandler, uniqueProps, dataSource);
	}

	@Override
	public Long updateDeeply(Serializable entity) {
		return super.updateDeeply(entity);
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
	public <T extends Serializable> T load(T entity) {
		return super.load(entity);
	}

	@Override
	public <T extends Serializable> T load(T entity, LockMode lockMode) {
		return super.load(entity, lockMode);
	}

	@Override
	public <T extends Serializable> T loadCascade(T entity, LockMode lockMode, Class... cascadeTypes) {
		return super.loadCascade(entity, lockMode, cascadeTypes);
	}

	@Override
	public <T extends Serializable> List<T> loadAll(List<T> entities) {
		return super.loadAll(entities, null);
	}

	@Override
	public <T extends Serializable> List<T> loadAll(List<T> entities, LockMode lockMode) {
		return super.loadAll(entities, lockMode);
	}

	@Override
	public <T extends Serializable> T loadEntity(Class<T> entityClass, EntityQuery entityQuery) {
		return super.loadEntity(entityClass, entityQuery);
	}

	@Override
	public <T extends Serializable> T loadEntity(Class entityClass, EntityQuery entityQuery, Class<T> resultType) {
		List<T> result = findEntity(entityClass, entityQuery, resultType);
		if (result == null || result.isEmpty()) {
			return null;
		}
		if (result.size() == 1) {
			return result.get(0);
		}
		throw new IllegalArgumentException("loadEntity查询出:" + result.size() + " 条记录,不符合load查询单条记录的预期!");
	}

	@Override
	public <T> List<T> findEntity(Class<T> entityClass, EntityQuery entityQuery) {
		return super.findEntity(entityClass, entityQuery);
	}

	@Override
	public <T> List<T> findEntity(Class entityClass, EntityQuery entityQuery, Class<T> resultType) {
		return super.findEntity(entityClass, entityQuery, resultType);
	}

	@Override
	public <T> Page<T> findPageEntity(Page page, Class<T> entityClass, EntityQuery entityQuery) {
		return super.findPageEntity(page, entityClass, entityQuery);
	}

	@Override
	public <T> Page<T> findPageEntity(Page page, Class entityClass, EntityQuery entityQuery, Class<T> resultType) {
		return super.findPageEntity(page, entityClass, entityQuery, resultType);
	}

	@Override
	public <T extends Serializable> List<T> loadAllCascade(List<T> entities, Class... cascadeTypes) {
		return super.loadAllCascade(entities, null, cascadeTypes);
	}

	@Override
	public <T extends Serializable> List<T> loadAllCascade(List<T> entities, LockMode lockMode, Class... cascadeTypes) {
		return super.loadAllCascade(entities, lockMode, cascadeTypes);
	}

	@Override
	public <T extends Serializable> T loadById(Class<T> entityClass, Object id) {
		List<T> result = super.loadByIds(entityClass, id);
		if (result == null || result.isEmpty()) {
			return null;
		}
		if (result.size() == 1) {
			return result.get(0);
		}
		throw new IllegalArgumentException("loadById查询出:" + result.size() + " 条记录,不符合load查询预期!");
	}

	@Override
	public <T extends Serializable> List<T> loadByIds(Class<T> entityClass, Object... ids) {
		return super.loadByIds(entityClass, ids);
	}

	@Override
	public <T extends Serializable> List<T> loadByIds(Class<T> entityClass, LockMode lockMode, Object... ids) {
		return super.loadByIds(entityClass, lockMode, ids);
	}

	@Override
	public <T> T findOne(String sqlOrSqlId, Map<String, Object> paramsMap, Class<T> resultType) {
		return super.loadBySql(sqlOrSqlId, paramsMap, resultType);
	}

	@Override
	public <T> T findOne(String sqlOrSqlId, Serializable entity, Class<T> resultType) {
		return (T) super.loadByQuery(new QueryExecutor(sqlOrSqlId, entity).resultType(resultType));
	}

	@Override
	public Object loadByQuery(QueryExecutor query) {
		return super.loadByQuery(query);
	}

	@Override
	public Object getValue(String sqlOrSqlId, Map<String, Object> paramsMap) {
		return super.getSingleValue(sqlOrSqlId, paramsMap);
	}

	@Override
	public <T> T getValue(String sqlOrSqlId, Map<String, Object> paramsMap, Class<T> resultType) {
		return super.getSingleValue(sqlOrSqlId, paramsMap, resultType);
	}

	@Override
	public QueryResult findByQuery(QueryExecutor query) {
		return super.findByQuery(query);
	}

	@Override
	public <T> List<T> find(String sqlOrSqlId, Map<String, Object> paramsMap, Class<T> resultType) {
		return super.findBySql(sqlOrSqlId, paramsMap, resultType);
	}

	@Override
	public List find(String sqlOrSqlId, Map<String, Object> paramsMap) {
		return super.findBySql(sqlOrSqlId, paramsMap, null);
	}

	@Override
	public <T> List<T> find(String sqlOrSqlId, Serializable entity, Class<T> resultType) {
		return (List<T>) super.findByQuery(new QueryExecutor(sqlOrSqlId, entity).resultType(resultType)).getRows();
	}

	@Override
	public QueryResult findPageByQuery(Page page, QueryExecutor queryExecutor) {
		return super.findPageByQuery(page, queryExecutor);
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
	public <T> Page<T> findPage(Page page, String sqlOrSqlId, Serializable entity, Class<T> resultType) {
		return (Page<T>) super.findPageByQuery(page, new QueryExecutor(sqlOrSqlId, entity).resultType(resultType))
				.getPageResult();
	}

	@Override
	public <T> List<T> findTop(String sqlOrSqlId, Map<String, Object> paramsMap, Class<T> resultType, double topSize) {
		return super.findTopBySql(sqlOrSqlId, paramsMap, resultType, topSize);
	}

	@Override
	public <T> List<T> findTop(String sqlOrSqlId, Serializable entity, Class<T> resultType, double topSize) {
		return (List<T>) super.findTopByQuery(new QueryExecutor(sqlOrSqlId, entity).resultType(resultType), topSize)
				.getRows();
	}

	@Override
	public QueryResult findTopByQuery(QueryExecutor queryExecutor, double topSize) {
		return super.findTopByQuery(queryExecutor, topSize);
	}

	@Override
	public QueryResult findRandomByQuery(QueryExecutor queryExecutor, double randomCount) {
		return super.getRandomResult(queryExecutor, randomCount);
	}

	@Override
	public <T> List<T> findRandom(String sqlOrSqlId, Map<String, Object> paramsMap, Class<T> resultType,
			double randomCount) {
		return super.getRandomResult(sqlOrSqlId, paramsMap, resultType, randomCount);
	}

	@Override
	public <T> List<T> findRandom(String sqlOrSqlId, Serializable entity, Class<T> resultType, double randomCount) {
		return (List<T>) super.getRandomResult(new QueryExecutor(sqlOrSqlId, entity).resultType(resultType),
				randomCount).getRows();
	}

	@Override
	public Long batchUpdate(String sqlOrSqlId, List dataSet) {
		return super.batchUpdate(sqlOrSqlId, dataSet, null);
	}

	@Override
	public Long batchUpdate(String sqlOrSqlId, List dataSet, Boolean autoCommit) {
		return super.batchUpdate(sqlOrSqlId, dataSet, autoCommit);
	}

	@Override
	public List updateFetch(QueryExecutor queryExecutor, UpdateRowHandler updateRowHandler) {
		return super.updateFetch(queryExecutor, updateRowHandler);
	}

	@Override
	public Long executeSql(String sqlOrSqlId) {
		return super.executeSql(sqlOrSqlId, MapKit.map());
	}

	@Override
	public Long executeSql(String sqlOrSqlId, Serializable entity) {
		return super.executeSql(sqlOrSqlId, entity);
	}

	@Override
	public Long executeSql(String sqlOrSqlId, Map<String, Object> paramsMap) {
		return super.executeSql(sqlOrSqlId, paramsMap);
	}

	@Override
	public Long executeSql(String sqlOrSqlId, Object... paramsValue) {
		return super.executeSql(sqlOrSqlId, null, paramsValue);
	}

	@Override
	public boolean wrapTreeTableRoute(TreeTableModel treeTableModel) {
		return super.wrapTreeTableRoute(treeTableModel);
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
	public HashMap<String, Object[]> getTranslateCache(String cacheName, String cacheType) {
		return super.getTranslateCache(cacheName, cacheType);
	}

	@Override
	public <T> List<T> getTranslateCache(String cacheName, String cacheType, Class<T> reusltType) {
		return super.getTranslateCache(cacheName, cacheType, reusltType);
	}

	@Override
	public void translate(Collection dataSet, String cacheName, TranslateHandler handler) {
		super.translate(dataSet, cacheName, cacheName, 1, handler);
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
	public <T> List<QueryResult<T>> parallQuery(List<ParallQuery> parallQueryList, Map<String, Object> paramsMap) {
		return super.parallQuery(parallQueryList, paramsMap, new ParallelConfig());
	}

	@Override
	public <T> List<QueryResult<T>> parallQuery(List<ParallQuery> parallQueryList, Map<String, Object> paramsMap,
			ParallelConfig parallelConfig) {
		return super.parallQuery(parallQueryList, paramsMap, parallelConfig);
	}
}
