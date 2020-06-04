/**
 * 
 */
package org.sagacity.sqltoy.dao.impl;

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
import org.sagacity.sqltoy.dao.SqlToyLazyDao;
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
import org.sagacity.sqltoy.support.BaseDaoSupport;
import org.sagacity.sqltoy.translate.TranslateHandler;
import org.springframework.stereotype.Repository;

/**
 * @project sqltoy-orm
 * @description SqlToyLazyDao提供的通用Dao逻辑实现
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:SqlToyLazyDaoImpl.java,Revision:v1.0,Date:2012-7-15
 */
@SuppressWarnings({ "rawtypes" })
@Repository("sqlToyLazyDao")
public class SqlToyLazyDaoImpl extends BaseDaoSupport implements SqlToyLazyDao {

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
	public Long getCount(String sqlOrNamedQuery, String[] paramsNamed, Object[] paramsValue) {
		return super.getCountBySql(sqlOrNamedQuery, paramsNamed, paramsValue);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dao.SqlToyLazyDao#getSingleValue(java.lang.String,
	 * java.lang.String[], java.lang.Object[])
	 */
	@Override
	public Object getSingleValue(String sqlOrNamedSql, String[] paramsNamed, Object[] paramsValue) {
		return super.getSingleValue(sqlOrNamedSql, paramsNamed, paramsValue);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dao.SqlToyLazyDao#loadBySql(java.lang.String,
	 * java.lang.String[], java.lang.Object[], java.lang.Class)
	 */
	@Override
	public <T> T loadBySql(String sqlOrNamedSql, String[] paramsNamed, Object[] paramsValue, Class<T> resultType) {
		return super.loadBySql(sqlOrNamedSql, paramsNamed, paramsValue, resultType);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dao.SqlToyLazyDao#loadBySql(java.lang.String,
	 * Serializable)
	 */
	@Override
	public <T extends Serializable> T loadBySql(String sqlOrNamedSql, T entity) {
		return super.loadBySql(sqlOrNamedSql, entity);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dao.SqlToyLazyDao#findBySql(java.lang.String,
	 * java.io.Serializable)
	 */
	@Override
	public <T extends Serializable> List<T> findBySql(String sqlOrNamedSql, final T entity) {
		return super.findBySql(sqlOrNamedSql, entity);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dao.SqlToyLazyDao#findBySql(java.lang.String,
	 * java.lang.String[], java.lang.Object[], java.lang.Class)
	 */
	@Override
	public <T> List<T> findBySql(String sqlOrNamedSql, String[] paramsNamed, Object[] paramsValue, Class<T> voClass) {
		return (List<T>) super.findBySql(sqlOrNamedSql, paramsNamed, paramsValue, voClass);
	}

	@Override
	public List findBySql(String sqlOrNamedSql, String[] paramsNamed, Object[] paramsValue) {
		return super.findBySql(sqlOrNamedSql, paramsNamed, paramsValue, null);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dao.SqlToyLazyDao#findPageByQuery(org.sagacity.core
	 * .database.model.PaginationModel, org.sagacity.sqltoy.executor.QueryExecutor)
	 */
	@Override
	public QueryResult findPageByQuery(PaginationModel pageModel, QueryExecutor queryExecutor) {
		return super.findPageByQuery(pageModel, queryExecutor);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dao.SqlToyLazyDao#findPageByEntity(org.sagacity.core
	 * .database.model.PaginationModel, java.io.Serializable)
	 */
	@Override
	public <T extends Serializable> PaginationModel<T> findPageBySql(final PaginationModel paginationModel,
			final String sqlOrNamedSql, final T entity) {
		return (PaginationModel<T>) super.findPageBySql(paginationModel, sqlOrNamedSql, entity);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dao.SqlToyLazyDao#finePageBySql(org.sagacity.core
	 * .database.model.PaginationModel, java.lang.String, java.lang.String[],
	 * java.lang.Object[], java.lang.Class)
	 */
	@Override
	public <T> PaginationModel<T> findPageBySql(PaginationModel paginationModel, String sqlOrNamedSql,
			String[] paramsNamed, Object[] paramValues, Class<T> voClass) {
		return (PaginationModel<T>) super.findPageByQuery(paginationModel,
				new QueryExecutor(sqlOrNamedSql, paramsNamed, paramValues).resultType(voClass)).getPageResult();
	}

	@Override
	public PaginationModel findPageBySql(PaginationModel paginationModel, String sqlOrNamedSql, String[] paramsNamed,
			Object[] paramValues) {
		return super.findPageByQuery(paginationModel, new QueryExecutor(sqlOrNamedSql, paramsNamed, paramValues))
				.getPageResult();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dao.SqlToyLazyDao#findTopBySql(java.lang.String,
	 * java.lang.String[], java.lang.Object[], java.lang.Class, double)
	 */
	@Override
	public <T> List<T> findTopBySql(String sqlOrNamedSql, String[] paramsNamed, Object[] paramValues, Class<T> voClass,
			double topSize) {
		return super.findTopBySql(sqlOrNamedSql, paramsNamed, paramValues, voClass, topSize);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dao.SqlToyLazyDao#findTopBySql(java.lang.String,
	 * java.io.Serializable, double)
	 */
	@Override
	public <T extends Serializable> List<T> findTopBySql(final String sqlOrNamedSql, final T entity,
			final double topSize) {
		return super.findTopBySql(sqlOrNamedSql, entity, topSize);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.sagacity.sqltoy.dao.SqlToyLazyDao#getRandomResultByQuery(org.sagacity
	 * .sqltoy.executor.QueryExecutor, double)
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
	public <T extends Serializable> List<T> getRandomResult(String sqlOrNamedSql, T entity, double randomCount) {
		return (List<T>) super.getRandomResult(new QueryExecutor(sqlOrNamedSql, entity), randomCount).getRows();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dao.SqlToyLazyDao#getRandomResultBySql(java.lang.
	 * String, java.lang.String[], java.lang.Object[], java.lang.Class, double)
	 */
	@Override
	public <T> List<T> getRandomResult(String sqlOrNamedSql, String[] paramsNamed, Object[] paramsValue,
			Class<T> voClass, double randomCount) {
		return super.getRandomResult(sqlOrNamedSql, paramsNamed, paramsValue, voClass, randomCount);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dao.SqlToyLazyDao#batchUpdate(java.lang.String,
	 * java.util.List, org.sagacity.core.database.callback.InsertRowCallbackHandler,
	 * boolean)
	 */
	@Override
	public Long batchUpdate(String sqlOrNamedSql, List dataSet, InsertRowCallbackHandler insertCallhandler,
			Boolean autoCommit) {
		return super.batchUpdate(sqlOrNamedSql, dataSet, null, insertCallhandler, autoCommit);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dao.SqlToyLazyDao#wrapTreeTableRoute(org.sagacity
	 * .core.database.model.TreeTableModel)
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
	public boolean isUnique(Serializable entity, String[] paramsNamed) {
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

	public StoreResult executeStore(String storeNameOrKey, Object[] inParamValues, Integer[] outParamsType,
			Class resultType) {
		return super.executeStore(storeNameOrKey, inParamValues, outParamsType, resultType, null);
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
	 * @see org.sagacity.sqltoy.dao.SqlToyLazyDao#saveAll(java.util.List,
	 * org.sagacity.core.utils.callback.ReflectPropertyHandler)
	 */
	@Override
	public <T extends Serializable> Long saveAll(List<T> entities, ReflectPropertyHandler reflectPropertyHandler) {
		return super.saveAll(entities, reflectPropertyHandler);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dao.SqlToyLazyDao#update(java.io.Serializable)
	 */
	@Override
	public Long update(Serializable entity) {
		return super.update(entity);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dao.SqlToyLazyDao#update(java.io.Serializable,
	 * java.lang.String[])
	 */
	@Override
	public Long update(Serializable entity, String[] forceUpdateProps) {
		return super.update(entity, forceUpdateProps);
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
	public Long updateCascade(Serializable entity, String[] forceUpdateProps, Class[] emptyUpdateClass,
			HashMap<Class, String[]> subTableForceUpdateProps) {
		return super.updateCascade(entity, forceUpdateProps, emptyUpdateClass, subTableForceUpdateProps);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dao.SqlToyLazyDao#updateAll(java.util.List)
	 */
	@Override
	public <T extends Serializable> Long updateAll(List<T> entities) {
		return super.updateAll(entities);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dao.SqlToyLazyDao#updateAll(java.util.List,
	 * java.lang.String[])
	 */
	@Override
	public <T extends Serializable> Long updateAll(List<T> entities, String[] forceUpdateProps) {
		return super.updateAll(entities, forceUpdateProps);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dao.SqlToyLazyDao#updateAll(java.util.List,
	 * java.lang.String[], org.sagacity.core.utils.callback.ReflectPropertyHandler)
	 */
	@Override
	public <T extends Serializable> Long updateAll(List<T> entities, String[] forceUpdateProps,
			ReflectPropertyHandler reflectPropertyHandler) {
		return super.updateAll(entities, forceUpdateProps, reflectPropertyHandler);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.support.SqlToyDaoSupport#updateAllDeeply(java.util
	 * .List, org.sagacity.core.utils.callback.ReflectPropertyHandler)
	 */
	@Override
	public <T extends Serializable> Long updateAllDeeply(List<T> entities,
			ReflectPropertyHandler reflectPropertyHandler) {
		return super.updateAllDeeply(entities, reflectPropertyHandler);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.support.SqlToyDaoSupport#saveOrUpdate(java.io.
	 * Serializable)
	 */
	@Override
	public Long saveOrUpdate(Serializable entity) {
		return super.saveOrUpdate(entity);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dao.SqlToyLazyDao#saveOrUpdate(java.io.Serializable,
	 * java.lang.String[])
	 */
	@Override
	public Long saveOrUpdate(Serializable entity, String[] forceUpdateProps) {
		return super.saveOrUpdate(entity, forceUpdateProps);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.support.SqlToyDaoSupport#saveOrUpdateAll(java.util
	 * .List)
	 */
	@Override
	public <T extends Serializable> Long saveOrUpdateAll(List<T> entities) {
		return super.saveOrUpdateAll(entities);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.support.SqlToyDaoSupport#saveOrUpdateAll(java.util
	 * .List, java.lang.String[])
	 */
	@Override
	public <T extends Serializable> Long saveOrUpdateAll(List<T> entities, String[] forceUpdateProps) {
		return super.saveOrUpdateAll(entities, forceUpdateProps);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dao.SqlToyLazyDao#saveOrUpdateAll(java.util.List,
	 * java.lang.String[], org.sagacity.core.utils.callback.ReflectPropertyHandler)
	 */
	@Override
	public <T extends Serializable> Long saveOrUpdateAll(List<T> entities, String[] forceUpdateProps,
			ReflectPropertyHandler reflectPropertyHandler) {
		return super.saveOrUpdateAll(entities, forceUpdateProps, reflectPropertyHandler);
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
	 * org.sagacity.sqltoy.LockMode)
	 */
	@Override
	public <T extends Serializable> T load(T entity, LockMode lockMode) {
		return super.load(entity, lockMode);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dao.SqlToyLazyDao#loadCascade(java.io.Serializable,
	 * org.sagacity.sqltoy.LockMode)
	 */
	@Override
	public <T extends Serializable> T loadCascade(T entity, LockMode lockMode) {
		return super.loadCascade(entity, lockMode);
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

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dao.SqlToyLazyDao#loadAllCascade(java.util.List)
	 */
	@Override
	public <T extends Serializable> List<T> loadAllCascade(List<T> entities) {
		return super.loadAllCascade(entities, null);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dao.SqlToyLazyDao#loadAllCascade(java.util.List,
	 * java.lang.Class[])
	 */
	@Override
	public <T extends Serializable> List<T> loadAllCascade(List<T> entities, Class[] cascadeTypes) {
		return super.loadAllCascade(entities, cascadeTypes, null);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dao.SqlToyLazyDao#loadCascade(java.io.Serializable,
	 * java.lang.Class[], org.sagacity.sqltoy.LockMode)
	 */
	@Override
	public <T extends Serializable> T loadCascade(T entity, Class[] cascadeTypes, LockMode lockMode) {
		return super.loadCascade(entity, cascadeTypes, lockMode);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dao.SqlToyLazyDao#loadByQuery(org.sagacity.sqltoy
	 * .executor.QueryExecutor)
	 */
	@Override
	public Object loadByQuery(QueryExecutor queryExecutor) {
		return super.loadByQuery(queryExecutor);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dao.SqlToyLazyDao#findByQuery(org.sagacity.sqltoy
	 * .executor.QueryExecutor)
	 */
	@Override
	public QueryResult findByQuery(QueryExecutor queryExecutor) {
		return super.findByQuery(queryExecutor);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dao.SqlToyLazyDao#findTopByQuery(org.sagacity.sqltoy
	 * .executor.QueryExecutor, double)
	 */
	@Override
	public QueryResult findTopByQuery(QueryExecutor queryExecutor, double topSize) {
		return super.findTopByQuery(queryExecutor, topSize);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dao.SqlToyLazyDao#updateFatch(org.sagacity.sqltoy
	 * .executor.QueryExecutor,
	 * org.sagacity.core.database.callback.UpdateRowHandler)
	 */
	@Override
	public List updateFetch(QueryExecutor queryExecutor, UpdateRowHandler updateRowHandler) {
		return super.updateFetch(queryExecutor, updateRowHandler);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dao.SqlToyLazyDao#updateFetchTop(org.sagacity.sqltoy
	 * .executor.QueryExecutor, java.lang.Integer,
	 * org.sagacity.core.database.callback.UpdateRowHandler)
	 */
	@Override
	@Deprecated
	public List updateFetchTop(QueryExecutor queryExecutor, Integer topSize, UpdateRowHandler updateRowHandler) {
		return super.updateFetchTop(queryExecutor, topSize, updateRowHandler);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dao.SqlToyLazyDao#updateFetchRandom(org.sagacity.
	 * sqltoy.executor.QueryExecutor, java.lang.Integer,
	 * org.sagacity.core.database.callback.UpdateRowHandler)
	 */
	@Override
	@Deprecated
	public List updateFetchRandom(QueryExecutor queryExecutor, Integer random, UpdateRowHandler updateRowHandler) {
		return super.updateFetchRandom(queryExecutor, random, updateRowHandler);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dao.SqlToyLazyDao#executeSql(java.lang.String,
	 * java.io.Serializable,
	 * org.sagacity.core.utils.callback.ReflectPropertyHandler)
	 */
	@Override
	public Long executeSql(String sqlOrNamedSql, Serializable entity, ReflectPropertyHandler reflectPropertyHandler) {
		return super.executeSql(sqlOrNamedSql, entity, reflectPropertyHandler);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dao.SqlToyLazyDao#executeSql(java.lang.String,
	 * java.lang.String[], java.lang.Object[])
	 */
	@Override
	public Long executeSql(String sqlOrNamedSql, String[] paramsNamed, Object[] paramsValue) {
		return super.executeSql(sqlOrNamedSql, paramsNamed, paramsValue);
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
	 * @see org.sagacity.sqltoy.support.BaseDaoSupport#page()
	 */
	@Override
	public Page page() {
		return super.page();
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
	public HashMap<String, Object[]> getTranslateCache(String cacheName, String elementId) {
		return super.getTranslateCache(cacheName, elementId);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dao.SqlToyLazyDao#translate(java.lang.String,
	 * java.lang.String, org.sagacity.sqltoy.plugin.TranslateHandler)
	 */
	@Override
	public void translate(Collection dataSet, String cacheName, String cacheType, Integer cacheNameIndex,
			TranslateHandler handler) {
		super.translate(dataSet, cacheName, cacheType, cacheNameIndex, handler);
	}

	/**
	 * @todo 判断缓存是否存在
	 * @param cacheName
	 * @return
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
	public <T> List<T> findEntity(Class<T> entityClass, EntityQuery entityQuery) {
		return super.findEntity(entityClass, entityQuery);
	}

	@Override
	public <T> PaginationModel<T> findEntity(Class<T> resultType, PaginationModel paginationModel,
			EntityQuery entityQuery) {
		return super.findEntity(resultType, paginationModel, entityQuery);
	}

	@Override
	public Long deleteByQuery(Class entityClass, EntityQuery entityQuery) {
		return super.deleteByQuery(entityClass, entityQuery);
	}

	@Override
	public Long updateByQuery(Class entityClass, EntityUpdate entityUpdate) {
		return super.updateByQuery(entityClass, entityUpdate);
	}

}
