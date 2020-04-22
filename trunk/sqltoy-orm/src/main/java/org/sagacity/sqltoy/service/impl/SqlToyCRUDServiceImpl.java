/**
 * 
 */
package org.sagacity.sqltoy.service.impl;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.sagacity.sqltoy.callback.ReflectPropertyHandler;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.dao.SqlToyLazyDao;
import org.sagacity.sqltoy.exception.DataAccessException;
import org.sagacity.sqltoy.executor.QueryExecutor;
import org.sagacity.sqltoy.model.PaginationModel;
import org.sagacity.sqltoy.model.TreeTableModel;
import org.sagacity.sqltoy.service.SqlToyCRUDService;
import org.sagacity.sqltoy.translate.TranslateHandler;
import org.sagacity.sqltoy.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * @project sqltoy-orm
 * @description 提供默认的增删改查业务逻辑封装
 * @author renfei.chen <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:SqlToyCRUDServiceImpl.java,Revision:v1.0,Date:2012-7-16
 */
@SuppressWarnings({ "rawtypes" })
@Service("sqlToyCRUDService")
public class SqlToyCRUDServiceImpl implements SqlToyCRUDService {
	/**
	 * 定义全局日志
	 */
	protected final Logger logger = LoggerFactory.getLogger(SqlToyCRUDServiceImpl.class);

	/**
	 * 全局懒处理dao
	 */
	protected SqlToyLazyDao sqlToyLazyDao;

	/**
	 * @param sqlToyLazyDao the sqlToyLazyDao to set
	 */
	@Autowired(required = false)
	@Qualifier(value = "sqlToyLazyDao")
	public void setSqlToyLazyDao(SqlToyLazyDao sqlToyLazyDao) {
		this.sqlToyLazyDao = sqlToyLazyDao;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.service.SqlToyCRUDService#save(java.io.Serializable
	 * )
	 */
	@Override
	@Transactional
	public Object save(Serializable entity) {
		return sqlToyLazyDao.save(entity);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.service.SqlToyCRUDService#saveAll(java.util.List,
	 * org.sagacity.core.utils.callback.ReflectPropertyHandler)
	 */
	@Override
	@Transactional
	public <T extends Serializable> Long saveAll(List<T> entities, ReflectPropertyHandler reflectPropertyHandler) {
		return sqlToyLazyDao.saveAll(entities, reflectPropertyHandler);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.service.SqlToyCRUDService#saveAll(java.util.List,
	 * org.sagacity.core.utils.callback.ReflectPropertyHandler)
	 */
	@Override
	@Transactional
	public <T extends Serializable> Long saveAll(List<T> entities) {
		return sqlToyLazyDao.saveAll(entities, null);
	}

	@Override
	@Transactional
	public <T extends Serializable> Long saveAllIgnoreExist(List<T> entities) {
		return sqlToyLazyDao.saveAllIgnoreExist(entities);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.sagacity.sqltoy.service.SqlToyCRUDService#update(java.io.Serializable ,
	 * boolean)
	 */
	@Override
	@Transactional
	public Long update(Serializable entity) {
		if (null == entity)
			throw new IllegalArgumentException("数据对象为null!");
		return sqlToyLazyDao.update(entity);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.sagacity.sqltoy.service.SqlToyCRUDService#update(java.io.Serializable ,
	 * boolean)
	 */
	@Override
	@Transactional
	public Long updateDeeply(Serializable entity) {
		if (null == entity)
			throw new IllegalArgumentException("数据对象为null!");
		return sqlToyLazyDao.updateDeeply(entity);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.sagacity.sqltoy.service.SqlToyCRUDService#update(java.io.Serializable ,
	 * java.lang.String[])
	 */
	@Override
	@Transactional
	public Long update(Serializable entity, String[] forceUpdateProps) {
		if (null == entity)
			throw new IllegalArgumentException("数据对象为null!");
		return sqlToyLazyDao.update(entity, forceUpdateProps);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.service.SqlToyCRUDService#updateAll(java.util .List,
	 * java.lang.String[], org.sagacity.core.utils.callback.ReflectPropertyHandler)
	 */
	@Override
	@Transactional
	public <T extends Serializable> Long updateAll(List<T> entities, String[] forceUpdateProps,
			ReflectPropertyHandler reflectPropertyHandler) {
		return sqlToyLazyDao.updateAll(entities, forceUpdateProps, reflectPropertyHandler);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.service.SqlToyCRUDService#updateAll(java.util.List)
	 */
	@Override
	@Transactional
	public <T extends Serializable> Long updateAll(List<T> entities) {
		return sqlToyLazyDao.updateAll(entities);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.service.SqlToyCRUDService#updateAll(java.util .List,
	 * java.lang.String[])
	 */
	@Override
	@Transactional
	public <T extends Serializable> Long updateAll(List<T> entities, String[] forceUpdateProps) {
		return sqlToyLazyDao.updateAll(entities, forceUpdateProps);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.service.SqlToyCRUDService#updateAllDeeply(java
	 * .util.List)
	 */
	@Override
	@Transactional
	public <T extends Serializable> Long updateAllDeeply(List<T> entities) {
		return sqlToyLazyDao.updateAllDeeply(entities, null);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.service.SqlToyCRUDService#saveOrUpdate(java.io
	 * .Serializable)
	 */
	@Override
	@Transactional
	public Long saveOrUpdate(Serializable entity) {
		if (null == entity)
			throw new IllegalArgumentException("数据对象为null!");
		return sqlToyLazyDao.saveOrUpdate(entity);

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.service.SqlToyCRUDService#saveOrUpdate(java.io
	 * .Serializable, java.lang.String[])
	 */
	@Override
	@Transactional
	public Long saveOrUpdate(Serializable entity, String[] forceUpdateProps) {
		if (null == entity)
			throw new IllegalArgumentException("数据对象为null!");
		return sqlToyLazyDao.saveOrUpdate(entity, forceUpdateProps);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.service.SqlToyCRUDService#saveOrUpdateAll(java
	 * .io.Serializable)
	 */
	@Override
	@Transactional
	public <T extends Serializable> Long saveOrUpdateAll(List<T> entities) {
		return sqlToyLazyDao.saveOrUpdateAll(entities);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.service.SqlToyCRUDService#saveOrUpdateAll(java
	 * .io.Serializable, java.lang.String[])
	 */
	@Override
	@Transactional
	public <T extends Serializable> Long saveOrUpdateAll(List<T> entities, String[] forceUpdateProps) {
		return sqlToyLazyDao.saveOrUpdateAll(entities, forceUpdateProps);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.service.SqlToyCRUDService#saveOrUpdateAll(java
	 * .io.Serializable, java.lang.String[],
	 * org.sagacity.core.utils.callback.ReflectPropertyHandler)
	 */
	@Override
	@Transactional
	public <T extends Serializable> Long saveOrUpdateAll(List<T> entities, String[] forceUpdateProps,
			ReflectPropertyHandler reflectPropertyHandler) {
		return sqlToyLazyDao.saveOrUpdateAll(entities, forceUpdateProps, reflectPropertyHandler);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.service.SqlToyCRUDService#load(java.io.Serializable
	 * )
	 */
	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public <T extends Serializable> T load(T entity) {
		return sqlToyLazyDao.load(entity);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.service.SqlToyCRUDService#loadCascade(java.io.
	 * Serializable)
	 */
	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public <T extends Serializable> T loadCascade(T entity) {
		return sqlToyLazyDao.loadCascade(entity, null);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.service.SqlToyCRUDService#loadAll(java.util.List)
	 */
	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public <T extends Serializable> List<T> loadAll(List<T> entities) {
		return sqlToyLazyDao.loadAll(entities);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.sagacity.sqltoy.service.SqlToyCRUDService#delete(java.io.Serializable )
	 */
	@Transactional
	public Long delete(Serializable entity) {
		return sqlToyLazyDao.delete(entity);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.service.SqlToyCRUDService#deleteAll(java.util .List)
	 */
	@Override
	@Transactional
	public <T extends Serializable> Long deleteAll(List<T> entities) {
		return sqlToyLazyDao.deleteAll(entities);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.service.SqlToyCRUDService#truncate(java.io.
	 * Serializable)
	 */
	@Override
	@Transactional
	public void truncate(final Class entityClass) {
		sqlToyLazyDao.truncate(entityClass);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.service.SqlToyCRUDService#isNotUnique(java.io
	 * .Serializable)
	 */
	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public boolean isUnique(Serializable entity) {
		return sqlToyLazyDao.isUnique(entity, null);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.service.SqlToyCRUDService#isUnique(java.io.
	 * Serializable, java.lang.String[], java.lang.String)
	 */
	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public boolean isUnique(Serializable entity, String[] paramsNamed) {
		return sqlToyLazyDao.isUnique(entity, paramsNamed);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.service.SqlToyCRUDService#wrapTreeTableRoute(
	 * java.io.Serializable, java.lang.String)
	 */
	@Override
	@Transactional
	public boolean wrapTreeTableRoute(Serializable entity, String pid) {
		return sqlToyLazyDao.wrapTreeTableRoute(new TreeTableModel(entity).pidField(pid));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.sagacity.sqltoy.service.SqlToyCRUDService#wrapTreeTableRoute(java.io.
	 * Serializable, java.lang.String, int)
	 */
	@Override
	@Transactional
	public boolean wrapTreeTableRoute(Serializable entity, String pid, int appendIdSize) {
		return sqlToyLazyDao.wrapTreeTableRoute(new TreeTableModel(entity).pidField(pid).idLength(appendIdSize));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.service.SqlToyCRUDService#findFrom(java.io.
	 * Serializable )
	 */
	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public <T extends Serializable> List<T> findFrom(T entity) {
		EntityMeta entityMeta = sqlToyLazyDao.getEntityMeta(entity.getClass());
		if (StringUtil.isBlank(entityMeta.getListSql())) {
			throw new DataAccessException(
					"findFromByEntity[" + entity.getClass().getName() + "]沒有在类上用注解@ListSql()定义查询sql!");
		}
		return sqlToyLazyDao.findBySql(entityMeta.getListSql(), entity);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.service.SqlToyCRUDService#findFrom(java.io.
	 * Serializable , org.sagacity.core.utils.callback.ReflectPropertyHandler)
	 */
	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public <T extends Serializable> List<T> findFrom(T entity, ReflectPropertyHandler reflectPropertyHandler) {
		EntityMeta entityMeta = sqlToyLazyDao.getEntityMeta(entity.getClass());
		if (StringUtil.isBlank(entityMeta.getListSql())) {
			throw new DataAccessException(
					"findFromByEntity[" + entity.getClass().getName() + "]沒有在类上用注解@ListSql()定义查询sql!");
		}
		return (List<T>) sqlToyLazyDao.findByQuery(
				new QueryExecutor(entityMeta.getListSql(), entity).reflectPropertyHandler(reflectPropertyHandler))
				.getRows();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.service.SqlToyCRUDService#findPageFrom(org.sagacity
	 * .core.database.model.PaginationModel, java.io.Serializable)
	 */
	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public <T extends Serializable> PaginationModel<T> findPageFrom(PaginationModel paginationModel, T entity) {
		EntityMeta entityMeta = sqlToyLazyDao.getEntityMeta(entity.getClass());
		if (StringUtil.isBlank(entityMeta.getPageSql())) {
			throw new DataAccessException(
					"findPageFromByEntity[" + entity.getClass().getName() + "]沒有在类上用注解@PaginationSql() 定义分页sql!");
		}
		return sqlToyLazyDao.findPageBySql(paginationModel, entityMeta.getPageSql(), entity);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.service.SqlToyCRUDService#findPageFrom(org.sagacity
	 * .core.database.model.PaginationModel, java.io.Serializable,
	 * org.sagacity.core.utils.callback.ReflectPropertyHandler)
	 */
	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public <T extends Serializable> PaginationModel<T> findPageFrom(PaginationModel paginationModel, T entity,
			ReflectPropertyHandler reflectPropertyHandler) {
		EntityMeta entityMeta = sqlToyLazyDao.getEntityMeta(entity.getClass());
		if (StringUtil.isBlank(entityMeta.getPageSql())) {
			throw new DataAccessException(
					"findPageFromByEntity[" + entity.getClass().getName() + "]沒有在类上用注解@PaginationSql() 定义分页sql!");
		}
		return (PaginationModel<T>) sqlToyLazyDao.findPageByQuery(paginationModel,
				new QueryExecutor(entityMeta.getPageSql(), entity).reflectPropertyHandler(reflectPropertyHandler))
				.getPageResult();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.service.SqlToyCRUDService#findTop(java.io.
	 * Serializable, long)
	 */
	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public <T extends Serializable> List<T> findTopFrom(T entity, double topSize) {
		EntityMeta entityMeta = sqlToyLazyDao.getEntityMeta(entity.getClass());
		if (StringUtil.isBlank(entityMeta.getListSql())) {
			throw new DataAccessException(
					"findTopFromByEntity[" + entity.getClass().getName() + "]沒有在类上用注解@ListSql()定义查询sql!");
		}
		return (List<T>) sqlToyLazyDao.findTopBySql(entityMeta.getListSql(), entity, topSize);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.service.SqlToyCRUDService#getRandomResult(java
	 * .io.Serializable, int)
	 */
	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public <T extends Serializable> List<T> getRandomFrom(T entity, double randomCount) {
		EntityMeta entityMeta = sqlToyLazyDao.getEntityMeta(entity.getClass());
		if (StringUtil.isBlank(entityMeta.getListSql()) && StringUtil.isBlank(entityMeta.getPageSql())) {
			throw new DataAccessException("getRandomFromByEntity[" + entity.getClass().getName()
					+ "]沒有在类上用注解@ListSql()或@PaginationSql() 定义查询sql!");
		}
		return (List<T>) sqlToyLazyDao.getRandomResult(
				StringUtil.isBlank(entityMeta.getListSql()) ? entityMeta.getPageSql() : entityMeta.getListSql(), entity,
				randomCount);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.sagacity.sqltoy.service.SqlToyCRUDService#generateBizId(java.lang.String,
	 * int)
	 */
	@Override
	public long generateBizId(String signature, int increment) {
		return sqlToyLazyDao.generateBizId(signature, increment);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.service.SqlToyCRUDService#generateBizId(java.io.
	 * Serializable)
	 */
	@Override
	public String generateBizId(Serializable entity) {
		return sqlToyLazyDao.generateBizId(entity);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.sagacity.sqltoy.service.SqlToyCRUDService#translate(java.util.Collection,
	 * java.lang.String, java.lang.String, java.lang.Integer,
	 * org.sagacity.sqltoy.plugin.TranslateHandler)
	 */
	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public void translate(Collection dataSet, String cacheName, String dictType, Integer index,
			TranslateHandler handler) {
		sqlToyLazyDao.translate(dataSet, cacheName, dictType, index, handler);
	}

	/**
	 * @todo 判断缓存是否存在
	 * @param cacheName
	 * @return
	 */
	@Override
	public boolean existCache(String cacheName) {
		return sqlToyLazyDao.existCache(cacheName);
	}

	@Override
	public Set<String> getCacheNames() {
		return sqlToyLazyDao.getCacheNames();
	}
}
