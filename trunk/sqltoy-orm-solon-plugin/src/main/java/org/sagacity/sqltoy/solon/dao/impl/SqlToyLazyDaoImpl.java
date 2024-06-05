package org.sagacity.sqltoy.solon.dao.impl;

import org.sagacity.sqltoy.solon.support.SolonDaoSupport;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.callback.StreamResultHandler;
import org.sagacity.sqltoy.callback.UpdateRowHandler;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlType;
import org.sagacity.sqltoy.dao.SqlToyLazyDao;
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

import javax.sql.DataSource;
import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author limliu
 * @version v1.0, Date:2024年3月21日
 * @project sqltoy-orm
 * @description SqlToyLazyDao提供的通用Dao逻辑实现
 */
public class SqlToyLazyDaoImpl extends SolonDaoSupport implements SqlToyLazyDao {
    
    /*
     * (non-Javadoc)
     *
     * @see org.sagacity.sqltoy.support.SqlToyDaoSupport#getSqlToyConfig(java.lang
     * .String)
     */
    @Override
    public SqlToyConfig getSqlToyConfig(String sqlKey, SqlType sqlType) {
        return super.getSqlToyConfig(sqlKey, sqlType);
    }
    
    /*
     * (non-Javadoc)
     *
     * @see org.sagacity.sqltoy.dao.SqlToyLazyDao#getCount(java.lang.String,
     * java.lang.String[], java.lang.Object[])
     */
    @Override
    public Long getCount(String sqlOrSqlId, String[] paramsNamed, Object[] paramsValue) {
        return super.getCountBySql(sqlOrSqlId, paramsNamed, paramsValue);
    }
    
    @Override
    public Long getCount(String sqlOrSqlId, Map<String, Object> paramsMap) {
        return super.getCountBySql(sqlOrSqlId, paramsMap);
    }
    
    @Override
    public Long getCount(Class entityClass, EntityQuery entityQuery) {
        return super.getCountByEntityQuery(entityClass, entityQuery);
    }
    
    /*
     * (non-Javadoc)
     *
     * @see org.sagacity.sqltoy.dao.SqlToyLazyDao#getSingleValue(java.lang.String,
     * java.lang.String[], java.lang.Object[])
     */
    @Override
    public Object getSingleValue(String sqlOrSqlId, String[] paramsNamed, Object[] paramsValue) {
        return super.getSingleValue(sqlOrSqlId, paramsNamed, paramsValue);
    }
    
    @Override
    public Object getSingleValue(String sqlOrSqlId, Map<String, Object> paramsMap) {
        return super.getSingleValue(sqlOrSqlId, paramsMap);
    }
    
    @Override
    public <T> T getSingleValue(String sqlOrSqlId, Map<String, Object> paramsMap, Class<T> resultType) {
        return super.getSingleValue(sqlOrSqlId, paramsMap, resultType);
    }
    
    /*
     * (non-Javadoc)
     *
     * @see org.sagacity.sqltoy.dao.SqlToyLazyDao#loadBySql(java.lang.String,
     * java.lang.String[], java.lang.Object[], java.lang.Class)
     */
    @Override
    public <T> T loadBySql(String sqlOrSqlId, String[] paramsNamed, Object[] paramsValue, Class<T> resultType) {
        return super.loadBySql(sqlOrSqlId, paramsNamed, paramsValue, resultType);
    }
    
    /*
     * (non-Javadoc)
     *
     * @see org.sagacity.sqltoy.dao.SqlToyLazyDao#loadBySql(java.lang.String,
     * Serializable)
     */
    @Override
    public <T extends Serializable> T loadBySql(String sqlOrSqlId, T entity) {
        return super.loadBySql(sqlOrSqlId, entity);
    }
    
    @Override
    public <T> T loadBySql(String sqlOrSqlId, Map<String, Object> paramsMap, Class<T> resultType) {
        return super.loadBySql(sqlOrSqlId, paramsMap, resultType);
    }
    
    /*
     * (non-Javadoc)
     *
     * @see org.sagacity.sqltoy.dao.SqlToyLazyDao#findBySql(java.lang.String,
     * java.io.Serializable)
     */
    @Override
    public <T extends Serializable> List<T> findBySql(String sqlOrSqlId, final T entity) {
        return super.findBySql(sqlOrSqlId, entity);
    }
    
    /*
     * (non-Javadoc)
     *
     * @see org.sagacity.sqltoy.dao.SqlToyLazyDao#findBySql(java.lang.String,
     * java.lang.String[], java.lang.Object[], java.lang.Class)
     */
    @Override
    public <T> List<T> findBySql(String sqlOrSqlId, String[] paramsNamed, Object[] paramsValue, Class<T> resultType) {
        return (List<T>) super.findBySql(sqlOrSqlId, paramsNamed, paramsValue, resultType);
    }
    
    @Override
    public <T> List<T> findBySql(String sqlOrSqlId, Map<String, Object> paramsMap, Class<T> resultType) {
        return (List<T>) super.findBySql(sqlOrSqlId, paramsMap, resultType);
    }
    
    @Override
    public List findBySql(String sqlOrSqlId, String[] paramsNamed, Object[] paramsValue) {
        return super.findBySql(sqlOrSqlId, paramsNamed, paramsValue, null);
    }
    
    /*
     * (non-Javadoc)
     *
     * @see
     * org.sagacity.sqltoy.dao.SqlToyLazyDao#findPageByQuery(org.sagacity.sqltoy.
     * model.Page, org.sagacity.sqltoy.model.QueryExecutor)
     */
    @Override
    public QueryResult findPageByQuery(Page page, QueryExecutor queryExecutor) {
        return super.findPageByQuery(page, queryExecutor);
    }
    
    @Override
    public <T extends Serializable> Page<T> findPageBySql(final Page page, final String sqlOrSqlId, final T entity) {
        return (Page<T>) super.findPageBySql(page, sqlOrSqlId, entity);
    }
    
    /*
     * (non-Javadoc)
     *
     * @see
     * org.sagacity.sqltoy.dao.SqlToyLazyDao#finePageBySql(org.sagacity.sqltoy.model
     * .Page, java.lang.String, java.lang.String[], java.lang.Object[],
     * java.lang.Class)
     */
    @Override
    public <T> Page<T> findPageBySql(Page page, String sqlOrSqlId, String[] paramsNamed, Object[] paramValues,
            Class<T> resultType) {
        return (Page<T>) super.findPageByQuery(page,
                new QueryExecutor(sqlOrSqlId, paramsNamed, paramValues).resultType(resultType)).getPageResult();
    }
    
    @Override
    public Page findPageBySql(Page page, String sqlOrSqlId, String[] paramsNamed, Object[] paramValues) {
        return super.findPageByQuery(page, new QueryExecutor(sqlOrSqlId, paramsNamed, paramValues)).getPageResult();
    }
    
    @Override
    public <T> Page<T> findPageBySql(Page page, String sqlOrSqlId, Map<String, Object> paramsMap, Class<T> resultType) {
        return (Page<T>) super.findPageBySql(page, sqlOrSqlId, paramsMap, resultType);
    }
    
    /*
     * (non-Javadoc)
     *
     * @see org.sagacity.sqltoy.dao.SqlToyLazyDao#findTopBySql(java.lang.String,
     * java.lang.String[], java.lang.Object[], java.lang.Class, double)
     */
    @Override
    public <T> List<T> findTopBySql(String sqlOrSqlId, String[] paramsNamed, Object[] paramValues, Class<T> resultType,
            double topSize) {
        return super.findTopBySql(sqlOrSqlId, paramsNamed, paramValues, resultType, topSize);
    }
    
    @Override
    public <T> List<T> findTopBySql(String sqlOrSqlId, Map<String, Object> paramsMap, Class<T> resultType,
            double topSize) {
        return super.findTopBySql(sqlOrSqlId, paramsMap, resultType, topSize);
    }
    
    /*
     * (non-Javadoc)
     *
     * @see org.sagacity.sqltoy.dao.SqlToyLazyDao#findTopBySql(java.lang.String,
     * java.io.Serializable, double)
     */
    @Override
    public <T extends Serializable> List<T> findTopBySql(final String sqlOrSqlId, final T entity,
            final double topSize) {
        return super.findTopBySql(sqlOrSqlId, entity, topSize);
    }
    
    /*
     * (non-Javadoc)
     *
     * @see
     * org.sagacity.sqltoy.dao.SqlToyLazyDao#getRandomResultByQuery(org.sagacity.
     * sqltoy.model.QueryExecutor, double)
     */
    @Override
    public QueryResult getRandomResult(QueryExecutor queryExecutor, double randomCount) {
        return super.getRandomResult(queryExecutor, randomCount);
    }
    
    /*
     * (non-Javadoc)
     *
     * @see org.sagacity.sqltoy.dao.SqlToyLazyDao#getRandomResultBySql(java.lang.
     * String, java.io.Serializable, double)
     */
    @Override
    public <T extends Serializable> List<T> getRandomResult(String sqlOrSqlId, T entity, double randomCount) {
        return (List<T>) super.getRandomResult(new QueryExecutor(sqlOrSqlId, entity), randomCount).getRows();
    }
    
    @Override
    public <T> List<T> getRandomResult(String sqlOrSqlId, Map<String, Object> paramsMap, Class<T> resultType,
            double randomCount) {
        return super.getRandomResult(sqlOrSqlId, paramsMap, resultType, randomCount);
    }
    
    /*
     * (non-Javadoc)
     *
     * @see org.sagacity.sqltoy.dao.SqlToyLazyDao#getRandomResultBySql(java.lang.
     * String, java.lang.String[], java.lang.Object[], java.lang.Class, double)
     */
    @Override
    public <T> List<T> getRandomResult(String sqlOrSqlId, String[] paramsNamed, Object[] paramsValue,
            Class<T> resultType, double randomCount) {
        return super.getRandomResult(sqlOrSqlId, paramsNamed, paramsValue, resultType, randomCount);
    }
    
    @Override
    public Long batchUpdate(String sqlOrSqlId, List dataSet) {
        return super.batchUpdate(sqlOrSqlId, dataSet, null);
    }
    
    @Override
    public Long batchUpdate(String sqlOrSqlId, List dataSet, Boolean autoCommit) {
        return super.batchUpdate(sqlOrSqlId, dataSet, autoCommit);
    }
    
    /*
     * (non-Javadoc)
     *
     * @see
     * org.sagacity.sqltoy.dao.SqlToyLazyDao#wrapTreeTableRoute(org.sagacity.sqltoy.
     * model.TreeTableModel)
     */
    @Override
    public boolean wrapTreeTableRoute(TreeTableModel treeTableModel) {
        return super.wrapTreeTableRoute(treeTableModel, null);
    }
    
    /*
     * (non-Javadoc)
     *
     * @see
     * org.sagacity.sqltoy.dao.SqlToyLazyDao#getEntityMeta(java.io.Serializable)
     */
    @Override
    public EntityMeta getEntityMeta(Class entityClass) {
        return super.getEntityMeta(entityClass);
    }
    
    /*
     * (non-Javadoc)
     *
     * @see org.sagacity.sqltoy.dao.SqlToyLazyDao#isUnique(java.io.Serializable,
     * java.lang.String[])
     */
    @Override
    public boolean isUnique(Serializable entity, String... paramsNamed) {
        return super.isUnique(entity, paramsNamed);
    }
    
    /*
     * (non-Javadoc)
     *
     * @see
     * org.sagacity.sqltoy.dao.SqlToyLazyDao#callNoResultStore(java.lang.String,
     * java.lang.Object[])
     */
    @Override
    public StoreResult executeStore(String storeNameOrKey, Object[] inParamValues) {
        return super.executeStore(storeNameOrKey, inParamValues, null, null, null);
    }
    
    @Override
    public StoreResult executeStore(String storeNameOrKey, Object[] inParamValues, Integer[] outParamsType,
            Class resultType) {
        return super.executeStore(storeNameOrKey, inParamValues, outParamsType, resultType, null);
    }
    
    public StoreResult executeMoreResultStore(String storeSqlOrKey, Object[] inParamsValue, Integer[] outParamsType,
            Class... resultTypes) {
        return super.executeMoreResultStore(storeSqlOrKey, inParamsValue, outParamsType, resultTypes);
    }
    
    @Override
    public void fetchStream(QueryExecutor queryExecutor, StreamResultHandler streamResultHandler) {
        super.fetchStream(queryExecutor, streamResultHandler);
    }
    
    /*
     * (non-Javadoc)
     *
     * @see org.sagacity.sqltoy.dao.SqlToyLazyDao#save(java.io.Serializable)
     */
    @Override
    public Object save(Serializable entity) {
        return super.save(entity);
    }
    
    /*
     * (non-Javadoc)
     *
     * @see org.sagacity.sqltoy.dao.SqlToyLazyDao#saveAll(java.util.List)
     */
    @Override
    public <T extends Serializable> Long saveAll(List<T> entities) {
        return super.saveAll(entities);
    }
    
    @Override
    public <T extends Serializable> Long saveAllIgnoreExist(List<T> entities) {
        return super.saveAllIgnoreExist(entities);
    }
    
    /*
     * (non-Javadoc)
     *
     * @see org.sagacity.sqltoy.dao.SqlToyLazyDao#update(java.io.Serializable,
     * java.lang.String[])
     */
    @Override
    public Long update(Serializable entity, String... forceUpdateProps) {
        return super.update(entity, forceUpdateProps);
    }
    
    @Override
    public <T extends Serializable> T updateSaveFetch(T entity, UpdateRowHandler updateRowHandler,
            String... uniqueProps) {
        return super.updateSaveFetch(entity, updateRowHandler, uniqueProps, null);
    }
    
    /*
     * (non-Javadoc)
     *
     * @see org.sagacity.sqltoy.dao.SqlToyLazyDao#updateDeeply(java.io.Serializable)
     */
    @Override
    public Long updateDeeply(Serializable entity) {
        return super.updateDeeply(entity);
    }
    
    /*
     * (non-Javadoc)
     *
     * @see
     * org.sagacity.sqltoy.dao.SqlToyLazyDao#updateCascade(java.io.Serializable,
     * java.lang.String[], java.lang.Class[], java.util.HashMap)
     */
    @Override
    public Long updateCascade(Serializable entity, String[] forceUpdateProps, Class[] forceCascadeClasses,
            HashMap<Class, String[]> subTableForceUpdateProps) {
        return super.updateCascade(entity, forceUpdateProps, forceCascadeClasses, subTableForceUpdateProps);
    }
    
    /*
     * (non-Javadoc)
     *
     * @see org.sagacity.sqltoy.dao.SqlToyLazyDao#updateAll(java.util.List,
     * java.lang.String[])
     */
    @Override
    public <T extends Serializable> Long updateAll(List<T> entities, String... forceUpdateProps) {
        return super.updateAll(entities, forceUpdateProps);
    }
    
    @Override
    public <T extends Serializable> Long updateAllDeeply(List<T> entities) {
        return super.updateAllDeeply(entities, null);
    }
    
    /*
     * (non-Javadoc)
     *
     * @see org.sagacity.sqltoy.dao.SqlToyLazyDao#saveOrUpdate(java.io.Serializable,
     * java.lang.String[])
     */
    @Override
    public Long saveOrUpdate(Serializable entity, String... forceUpdateProps) {
        return super.saveOrUpdate(entity, forceUpdateProps);
    }
    
    /*
     * (non-Javadoc)
     *
     * @see org.sagacity.sqltoy.support.SqlToyDaoSupport#saveOrUpdateAll(java.util
     * .List, java.lang.String[])
     */
    @Override
    public <T extends Serializable> Long saveOrUpdateAll(List<T> entities, String... forceUpdateProps) {
        return super.saveOrUpdateAll(entities, forceUpdateProps);
    }
    
    /*
     * (non-Javadoc)
     *
     * @see org.sagacity.sqltoy.dao.SqlToyLazyDao#delete(java.io.Serializable)
     */
    @Override
    public Long delete(Serializable entity) {
        return super.delete(entity);
    }
    
    /*
     * (non-Javadoc)
     *
     * @see org.sagacity.sqltoy.dao.SqlToyLazyDao#deleteAll(java.util.List)
     */
    @Override
    public <T extends Serializable> Long deleteAll(List<T> entities) {
        return super.deleteAll(entities);
    }
    
    @Override
    public Long deleteByIds(Class entityClass, Object... ids) {
        return super.deleteByIds(entityClass, ids);
    }
    
    /*
     * (non-Javadoc)
     *
     * @see org.sagacity.sqltoy.dao.SqlToyLazyDao#truncate(java.io.Serializable)
     */
    @Override
    public void truncate(final Class entityClass) {
        super.truncate(entityClass, null);
    }
    
    /*
     * (non-Javadoc)
     *
     * @see org.sagacity.sqltoy.dao.SqlToyLazyDao#load(java.io.Serializable)
     */
    @Override
    public <T extends Serializable> T load(T entity) {
        return super.load(entity);
    }
    
    /*
     * (non-Javadoc)
     *
     * @see org.sagacity.sqltoy.dao.SqlToyLazyDao#load(java.io.Serializable,
     * org.sagacity.sqltoy.model.LockMode)
     */
    @Override
    public <T extends Serializable> T load(T entity, LockMode lockMode) {
        return super.load(entity, lockMode);
    }
    
    /*
     * (non-Javadoc)
     *
     * @see org.sagacity.sqltoy.dao.SqlToyLazyDao#loadAll(java.util.List)
     */
    @Override
    public <T extends Serializable> List<T> loadAll(List<T> entities) {
        return super.loadAll(entities, null);
    }
    
    @Override
    public <T extends Serializable> List<T> loadAll(List<T> entities, LockMode lockMode) {
        return super.loadAll(entities, lockMode);
    }
    
    /*
     * (non-Javadoc)
     *
     * @see org.sagacity.sqltoy.dao.SqlToyLazyDao#loadAllCascade(java.util.List,
     * java.lang.Class[])
     */
    @Override
    public <T extends Serializable> List<T> loadAllCascade(List<T> entities, Class... cascadeTypes) {
        return super.loadAllCascade(entities, null, cascadeTypes);
    }
    
    /*
     * (non-Javadoc)
     *
     * @see org.sagacity.sqltoy.dao.SqlToyLazyDao#loadAllCascade(java.util.List,
     * java.lang.Class[])
     */
    @Override
    public <T extends Serializable> List<T> loadAllCascade(List<T> entities, LockMode lockMode, Class... cascadeTypes) {
        return super.loadAllCascade(entities, lockMode, cascadeTypes);
    }
    
    @Override
    public <T extends Serializable> List<T> loadByIds(Class<T> entityClass, LockMode lockMode, Object... ids) {
        return super.loadByIds(entityClass, lockMode, ids);
    }
    
    @Override
    public <T extends Serializable> List<T> loadByIds(Class<T> entityClass, Object... ids) {
        return super.loadByIds(entityClass, null, ids);
    }
    
    /*
     * (non-Javadoc)
     *
     * @see org.sagacity.sqltoy.dao.SqlToyLazyDao#loadCascade(java.io.Serializable,
     * java.lang.Class[], org.sagacity.sqltoy.model.LockMode)
     */
    @Override
    public <T extends Serializable> T loadCascade(T entity, LockMode lockMode, Class... cascadeTypes) {
        return super.loadCascade(entity, lockMode, cascadeTypes);
    }
    
    /*
     * (non-Javadoc)
     *
     * @see
     * org.sagacity.sqltoy.dao.SqlToyLazyDao#loadByQuery(org.sagacity.sqltoy.model.
     * QueryExecutor)
     */
    @Override
    public Object loadByQuery(QueryExecutor queryExecutor) {
        return super.loadByQuery(queryExecutor);
    }
    
    /*
     * (non-Javadoc)
     *
     * @see
     * org.sagacity.sqltoy.dao.SqlToyLazyDao#findByQuery(org.sagacity.sqltoy.model.
     * QueryExecutor)
     */
    @Override
    public QueryResult findByQuery(QueryExecutor queryExecutor) {
        return super.findByQuery(queryExecutor);
    }
    
    /*
     * (non-Javadoc)
     *
     * @see
     * org.sagacity.sqltoy.dao.SqlToyLazyDao#findTopByQuery(org.sagacity.sqltoy.
     * model.QueryExecutor, double)
     */
    @Override
    public QueryResult findTopByQuery(QueryExecutor queryExecutor, double topSize) {
        return super.findTopByQuery(queryExecutor, topSize);
    }
    
    /*
     * (non-Javadoc)
     *
     * @see
     * org.sagacity.sqltoy.dao.SqlToyLazyDao#updateFatch(org.sagacity.sqltoy.model.
     * QueryExecutor, org.sagacity.sqltoy.callback.UpdateRowHandler)
     */
    @Override
    public List updateFetch(QueryExecutor queryExecutor, UpdateRowHandler updateRowHandler) {
        return super.updateFetch(queryExecutor, updateRowHandler);
    }
    
    @Override
    public Long executeSql(String sqlOrSqlId, Serializable entity) {
        return super.executeSql(sqlOrSqlId, entity);
    }
    
    @Override
    public Long executeSql(String sqlOrSqlId, Map<String, Object> paramsMap) {
        return super.executeSql(sqlOrSqlId, paramsMap);
    }
    
    /*
     * (non-Javadoc)
     *
     * @see org.sagacity.sqltoy.dao.SqlToyLazyDao#executeSql(java.lang.String,
     * java.lang.String[], java.lang.Object[])
     */
    @Override
    public Long executeSql(String sqlOrSqlId, String[] paramsNamed, Object[] paramsValue) {
        return super.executeSql(sqlOrSqlId, paramsNamed, paramsValue);
    }
    
    /*
     * (non-Javadoc)
     *
     * @see org.sagacity.sqltoy.dao.SqlToyLazyDao#flush()
     */
    @Override
    public void flush() {
        super.flush();
    }
    
    /*
     * (non-Javadoc)
     *
     * @see org.sagacity.sqltoy.support.SqlToyDaoSupport#getSqlToyContext()
     */
    @Override
    public SqlToyContext getSqlToyContext() {
        return super.getSqlToyContext();
    }
    
    /*
     * (non-Javadoc)
     *
     * @see org.sagacity.sqltoy.dao.SqlToyLazyDao#getDataSource()
     */
    @Override
    public DataSource getDataSource() {
        return super.getDataSource(null);
    }
    
    /*
     * (non-Javadoc)
     *
     * @see org.sagacity.sqltoy.support.BaseDaoSupport#delete()
     */
    @Override
    public Delete delete() {
        return super.delete();
    }
    
    /*
     * (non-Javadoc)
     *
     * @see org.sagacity.sqltoy.support.BaseDaoSupport#update()
     */
    @Override
    public Update update() {
        return super.update();
    }
    
    /*
     * (non-Javadoc)
     *
     * @see org.sagacity.sqltoy.support.BaseDaoSupport#store()
     */
    @Override
    public Store store() {
        return super.store();
    }
    
    /*
     * (non-Javadoc)
     *
     * @see org.sagacity.sqltoy.support.BaseDaoSupport#save()
     */
    @Override
    public Save save() {
        return super.save();
    }
    
    /*
     * (non-Javadoc)
     *
     * @see org.sagacity.sqltoy.support.BaseDaoSupport#query()
     */
    @Override
    public Query query() {
        return super.query();
    }
    
    /*
     * (non-Javadoc)
     *
     * @see org.sagacity.sqltoy.support.BaseDaoSupport#load()
     */
    @Override
    public Load load() {
        return super.load();
    }
    
    /*
     * (non-Javadoc)
     *
     * @see org.sagacity.sqltoy.support.BaseDaoSupport#unique()
     */
    @Override
    public Unique unique() {
        return super.unique();
    }
    
    /*
     * (non-Javadoc)
     *
     * @see org.sagacity.sqltoy.support.BaseDaoSupport#treeTable()
     */
    @Override
    public TreeTable treeTable() {
        return super.treeTable();
    }
    
    /*
     * (non-Javadoc)
     *
     * @see org.sagacity.sqltoy.support.BaseDaoSupport#execute()
     */
    @Override
    public Execute execute() {
        return super.execute();
    }
    
    /*
     * (non-Javadoc)
     *
     * @see org.sagacity.sqltoy.support.BaseDaoSupport#batch()
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
     * @see org.sagacity.sqltoy.support.BaseDaoSupport#mongo()
     */
    @Override
    public Mongo mongo() {
        return super.mongo();
    }
    
    /*
     * (non-Javadoc)
     *
     * @see
     * org.sagacity.sqltoy.support.SqlToyDaoSupport#generateBizId(java.lang.String,
     * int)
     */
    @Override
    public long generateBizId(String signature, int increment) {
        return super.generateBizId(signature, increment);
    }
    
    /*
     * (non-Javadoc)
     *
     * @see org.sagacity.sqltoy.support.SqlToyDaoSupport#generateBizId(java.io.
     * Serializable)
     */
    @Override
    public String generateBizId(Serializable entity) {
        return super.generateBizId(entity);
    }
    
    /*
     * (non-Javadoc)
     *
     * @see
     * org.sagacity.sqltoy.support.SqlToyDaoSupport#getTranslateCache(java.lang.
     * String, java.lang.String)
     */
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
    
    /*
     * (non-Javadoc)
     *
     * @see org.sagacity.sqltoy.dao.SqlToyLazyDao#translate(java.lang.String,
     * java.lang.String, org.sagacity.sqltoy.translate.TranslateHandler)
     */
    @Override
    public void translate(Collection dataSet, String cacheName, String cacheType, Integer cacheNameIndex,
            TranslateHandler handler) {
        super.translate(dataSet, cacheName, cacheType, cacheNameIndex, handler);
    }
    
    /**
     * @param cacheName
     * @return
     * @todo 判断缓存是否存在
     */
    @Override
    public boolean existCache(String cacheName) {
        return super.existCache(cacheName);
    }
    
    @Override
    public Set<String> getCacheNames() {
        return super.getCacheNames();
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
        throw new IllegalArgumentException("loadEntity查询出:" + result.size() + " 条记录,不符合load查询预期!");
    }
    
    @Override
    public <T> List<T> findEntity(Class<T> entityClass, EntityQuery entityQuery) {
        return super.findEntity(entityClass, entityQuery);
    }
    
    @Override
    public <T> List<T> findEntity(Class entityClass, EntityQuery entityQuery, Class<T> resultType) {
        return (List<T>) super.findEntity(entityClass, entityQuery, resultType);
    }
    
    @Override
    public <T> Page<T> findPageEntity(Page page, Class<T> entityClass, EntityQuery entityQuery) {
        return super.findPageEntity(page, entityClass, entityQuery, entityClass);
    }
    
    @Override
    public <T> Page<T> findPageEntity(Page page, Class entityClass, EntityQuery entityQuery, Class<T> resultType) {
        return (Page<T>) super.findPageEntity(page, entityClass, entityQuery, resultType);
    }
    
    @Override
    public Long deleteByQuery(Class entityClass, EntityQuery entityQuery) {
        return super.deleteByQuery(entityClass, entityQuery);
    }
    
    @Override
    public Long updateByQuery(Class entityClass, EntityUpdate entityUpdate) {
        return super.updateByQuery(entityClass, entityUpdate);
    }
    
    @Override
    public String[] cacheMatchKeys(String matchRegex, CacheMatchFilter cacheMatchFilter) {
        return super.cacheMatchKeys(cacheMatchFilter, matchRegex);
    }
    
    @Override
    public String[] cacheMatchKeys(CacheMatchFilter cacheMatchFilter, String... matchRegexes) {
        return super.cacheMatchKeys(cacheMatchFilter, matchRegexes);
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
    
    /**
     * @param <T>
     * @param sourcePage
     * @param resultType
     * @param ignoreProperties
     * @return
     * @TODO 转换分页类型
     */
    @Override
    public <T extends Serializable> Page<T> convertType(Page sourcePage, Class<T> resultType,
            String... ignoreProperties) {
        return super.convertType(sourcePage, resultType, ignoreProperties);
    }
    
    @Override
    public <T> List<QueryResult<T>> parallQuery(List<ParallQuery> parallQueryList, String[] paramNames,
            Object[] paramValues) {
        return super.parallQuery(parallQueryList, paramNames, paramValues, null);
    }
    
    @Override
    public <T> List<QueryResult<T>> parallQuery(List<ParallQuery> parallQueryList, String[] paramNames,
            Object[] paramValues, ParallelConfig parallelConfig) {
        return super.parallQuery(parallQueryList, paramNames, paramValues, parallelConfig);
    }
    
    @Override
    public <T> List<QueryResult<T>> parallQuery(List<ParallQuery> parallQueryList, Map<String, Object> paramsMap) {
        return super.parallQuery(parallQueryList, paramsMap, null);
    }
    
    @Override
    public <T> List<QueryResult<T>> parallQuery(List<ParallQuery> parallQueryList, Map<String, Object> paramsMap,
            ParallelConfig parallelConfig) {
        return super.parallQuery(parallQueryList, paramsMap, parallelConfig);
    }
    
    @Override
    public List<ColumnMeta> getTableColumns(String catalog, String schema, String tableName) {
        return super.getTableColumns(catalog, schema, tableName, null);
    }
    
    @Override
    public List<TableMeta> getTables(String catalog, String schema, String tableName) {
        return super.getTables(catalog, schema, tableName, null);
    }
    
}
