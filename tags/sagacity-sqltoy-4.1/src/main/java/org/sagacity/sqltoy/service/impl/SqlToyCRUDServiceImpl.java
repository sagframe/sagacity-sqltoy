/**
 * 
 */
package org.sagacity.sqltoy.service.impl;

import java.io.Serializable;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.sagacity.sqltoy.callback.ReflectPropertyHandler;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.dao.SqlToyLazyDao;
import org.sagacity.sqltoy.exception.BaseException;
import org.sagacity.sqltoy.executor.QueryExecutor;
import org.sagacity.sqltoy.model.PaginationModel;
import org.sagacity.sqltoy.model.TreeTableModel;
import org.sagacity.sqltoy.service.SqlToyCRUDService;
import org.sagacity.sqltoy.utils.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
	protected final Logger logger = LogManager.getLogger(getClass());

	/**
	 * 全局懒处理dao
	 */
	protected SqlToyLazyDao sqlToyLazyDao;

	/**
	 * @param sqlToyLazyDao
	 *            the sqlToyLazyDao to set
	 */
	@Autowired(required = false)
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
	public Object save(Serializable entity) throws BaseException {
		try {
			return sqlToyLazyDao.save(entity);
		} catch (Exception e) {
			e.printStackTrace();
			throw new BaseException(e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.service.SqlToyCRUDService#saveAll(java.util.List,
	 * org.sagacity.core.utils.callback.ReflectPropertyHandler)
	 */
	@Override
	public Long saveAll(List<?> entities, ReflectPropertyHandler reflectPropertyHandler) throws BaseException {
		try {
			return sqlToyLazyDao.saveAll(entities, reflectPropertyHandler);
		} catch (Exception e) {
			e.printStackTrace();
			throw new BaseException(e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.service.SqlToyCRUDService#saveAll(java.util.List,
	 * org.sagacity.core.utils.callback.ReflectPropertyHandler)
	 */
	@Override
	public Long saveAll(List<?> entities) throws BaseException {
		try {
			return sqlToyLazyDao.saveAll(entities, null);
		} catch (Exception e) {
			e.printStackTrace();
			throw new BaseException(e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.sagacity.sqltoy.service.SqlToyCRUDService#update(java.io.Serializable ,
	 * boolean)
	 */
	@Override
	public Long update(Serializable entity) throws BaseException {
		try {
			if (null == entity)
				throw new BaseException("数据对象为null!");
			return sqlToyLazyDao.update(entity);
		} catch (Exception e) {
			e.printStackTrace();
			throw new BaseException(e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.sagacity.sqltoy.service.SqlToyCRUDService#update(java.io.Serializable ,
	 * boolean)
	 */
	@Override
	public Long updateDeeply(Serializable entity) throws BaseException {
		try {
			if (null == entity)
				throw new BaseException("数据对象为null!");
			return sqlToyLazyDao.updateDeeply(entity);
		} catch (Exception e) {
			e.printStackTrace();
			throw new BaseException(e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.sagacity.sqltoy.service.SqlToyCRUDService#update(java.io.Serializable ,
	 * java.lang.String[])
	 */
	@Override
	public Long update(Serializable entity, String[] forceUpdateProps) throws BaseException {
		try {
			if (null == entity)
				throw new BaseException("数据对象为null!");
			return sqlToyLazyDao.update(entity, forceUpdateProps);
		} catch (Exception e) {
			e.printStackTrace();
			throw new BaseException(e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.service.SqlToyCRUDService#updateAll(java.util .List,
	 * java.lang.String[], org.sagacity.core.utils.callback.ReflectPropertyHandler)
	 */
	@Override
	public Long updateAll(List<?> entities, String[] forceUpdateProps, ReflectPropertyHandler reflectPropertyHandler)
			throws BaseException {
		try {
			return sqlToyLazyDao.updateAll(entities, forceUpdateProps, reflectPropertyHandler);
		} catch (Exception e) {
			e.printStackTrace();
			throw new BaseException(e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.service.SqlToyCRUDService#updateAll(java.util.List)
	 */
	@Override
	public Long updateAll(List<?> entities) throws BaseException {
		try {
			return sqlToyLazyDao.updateAll(entities);
		} catch (Exception e) {
			e.printStackTrace();
			throw new BaseException(e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.service.SqlToyCRUDService#updateAll(java.util .List,
	 * java.lang.String[])
	 */
	@Override
	public Long updateAll(List<?> entities, String[] forceUpdateProps) throws BaseException {
		try {
			return sqlToyLazyDao.updateAll(entities, forceUpdateProps);
		} catch (Exception e) {
			e.printStackTrace();
			throw new BaseException(e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.service.SqlToyCRUDService#updateAllDeeply(java
	 * .util.List)
	 */
	@Override
	public Long updateAllDeeply(List<?> entities) throws Exception {
		try {
			return sqlToyLazyDao.updateAllDeeply(entities, null);
		} catch (Exception e) {
			e.printStackTrace();
			throw new BaseException(e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.service.SqlToyCRUDService#saveOrUpdate(java.io
	 * .Serializable)
	 */
	@Override
	public Long saveOrUpdate(Serializable entity) throws BaseException {
		try {
			if (null == entity)
				throw new BaseException("数据对象为null!");
			return sqlToyLazyDao.saveOrUpdate(entity);
		} catch (Exception e) {
			e.printStackTrace();
			throw new BaseException(e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.service.SqlToyCRUDService#saveOrUpdate(java.io
	 * .Serializable, java.lang.String[])
	 */
	@Override
	public Long saveOrUpdate(Serializable entity, String[] forceUpdateProps) throws BaseException {
		try {
			if (null == entity)
				throw new BaseException("数据对象为null!");
			return sqlToyLazyDao.saveOrUpdate(entity, forceUpdateProps);
		} catch (Exception e) {
			e.printStackTrace();
			throw new BaseException(e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.service.SqlToyCRUDService#saveOrUpdateAll(java
	 * .io.Serializable)
	 */
	@Override
	public Long saveOrUpdateAll(List<?> entities) throws BaseException {
		try {
			return sqlToyLazyDao.saveOrUpdateAll(entities);
		} catch (Exception e) {
			e.printStackTrace();
			throw new BaseException(e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.service.SqlToyCRUDService#saveOrUpdateAll(java
	 * .io.Serializable, java.lang.String[])
	 */
	@Override
	public Long saveOrUpdateAll(List<?> entities, String[] forceUpdateProps) throws BaseException {
		try {
			return sqlToyLazyDao.saveOrUpdateAll(entities, forceUpdateProps);
		} catch (Exception e) {
			e.printStackTrace();
			throw new BaseException(e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.service.SqlToyCRUDService#saveOrUpdateAll(java
	 * .io.Serializable, java.lang.String[],
	 * org.sagacity.core.utils.callback.ReflectPropertyHandler)
	 */
	@Override
	public Long saveOrUpdateAll(List<?> entities, String[] forceUpdateProps,
			ReflectPropertyHandler reflectPropertyHandler) throws BaseException {
		try {
			return sqlToyLazyDao.saveOrUpdateAll(entities, forceUpdateProps, reflectPropertyHandler);
		} catch (Exception e) {
			e.printStackTrace();
			throw new BaseException(e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.service.SqlToyCRUDService#load(java.io.Serializable
	 * )
	 */
	@Override
	public Serializable load(Serializable entity) throws BaseException {
		try {
			return sqlToyLazyDao.load(entity);
		} catch (Exception e) {
			e.printStackTrace();
			throw new BaseException(e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.service.SqlToyCRUDService#loadCascade(java.io.
	 * Serializable)
	 */
	@Override
	public Serializable loadCascade(Serializable entity) throws BaseException {
		try {
			return sqlToyLazyDao.loadCascade(entity, null);
		} catch (Exception e) {
			e.printStackTrace();
			throw new BaseException(e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.service.SqlToyCRUDService#loadAll(java.util.List)
	 */
	@Override
	public List loadAll(List<?> entities) throws BaseException {
		try {
			return sqlToyLazyDao.loadAll(entities);
		} catch (Exception e) {
			e.printStackTrace();
			throw new BaseException(e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.sagacity.sqltoy.service.SqlToyCRUDService#delete(java.io.Serializable )
	 */
	public Long delete(Serializable entity) throws BaseException {
		try {
			return sqlToyLazyDao.delete(entity);
		} catch (Exception e) {
			e.printStackTrace();
			throw new BaseException(e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.service.SqlToyCRUDService#deleteAll(java.util .List)
	 */
	@Override
	public Long deleteAll(List<?> entities) throws BaseException {
		try {
			return sqlToyLazyDao.deleteAll(entities);
		} catch (Exception e) {
			e.printStackTrace();
			throw new BaseException(e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.service.SqlToyCRUDService#truncate(java.io.
	 * Serializable)
	 */
	@Override
	public void truncate(final Class entityClass) throws BaseException {
		try {
			sqlToyLazyDao.truncate(entityClass);
		} catch (Exception e) {
			e.printStackTrace();
			throw new BaseException(e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.service.SqlToyCRUDService#isNotUnique(java.io
	 * .Serializable)
	 */
	@Override
	public boolean isUnique(Serializable entity) throws BaseException {
		try {
			return sqlToyLazyDao.isUnique(entity, null);
		} catch (Exception e) {
			e.printStackTrace();
			throw new BaseException(e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.service.SqlToyCRUDService#isUnique(java.io.
	 * Serializable, java.lang.String[], java.lang.String)
	 */
	@Override
	public boolean isUnique(Serializable entity, String[] paramsNamed) throws BaseException {
		try {
			return sqlToyLazyDao.isUnique(entity, paramsNamed);
		} catch (Exception e) {
			e.printStackTrace();
			throw new BaseException(e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.service.SqlToyCRUDService#wrapTreeTableRoute(
	 * java.io.Serializable, java.lang.String)
	 */
	@Override
	public boolean wrapTreeTableRoute(Serializable entity, String pid) throws BaseException {
		try {
			return sqlToyLazyDao.wrapTreeTableRoute(new TreeTableModel(entity).pidField(pid));
		} catch (Exception e) {
			e.printStackTrace();
			throw new BaseException(e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.sagacity.sqltoy.service.SqlToyCRUDService#wrapTreeTableRoute(java.io.
	 * Serializable, java.lang.String, int)
	 */
	@Override
	public boolean wrapTreeTableRoute(Serializable entity, String pid, int appendIdSize) throws BaseException {
		try {
			return sqlToyLazyDao.wrapTreeTableRoute(new TreeTableModel(entity).pidField(pid).idLength(appendIdSize));
		} catch (Exception e) {
			e.printStackTrace();
			throw new BaseException(e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.service.SqlToyCRUDService#findFrom(java.io.
	 * Serializable )
	 */
	@Override
	public List findFrom(Serializable entity) throws BaseException {
		try {
			EntityMeta entityMeta = sqlToyLazyDao.getEntityMeta(entity.getClass());
			return sqlToyLazyDao.findBySql(entityMeta.getListSql(), entity);
		} catch (Exception e) {
			e.printStackTrace();
			throw new BaseException(e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.service.SqlToyCRUDService#findFrom(java.io.
	 * Serializable , org.sagacity.core.utils.callback.ReflectPropertyHandler)
	 */
	@Override
	public List findFrom(Serializable entity, ReflectPropertyHandler reflectPropertyHandler) throws BaseException {
		try {
			EntityMeta entityMeta = sqlToyLazyDao.getEntityMeta(entity.getClass());
			return sqlToyLazyDao.findByQuery(
					new QueryExecutor(entityMeta.getListSql(), entity).reflectPropertyHandler(reflectPropertyHandler))
					.getRows();
		} catch (Exception e) {
			e.printStackTrace();
			throw new BaseException(e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.service.SqlToyCRUDService#findPageFrom(org.sagacity
	 * .core.database.model.PaginationModel, java.io.Serializable)
	 */
	@Override
	public PaginationModel findPageFrom(PaginationModel paginationModel, Serializable entity) throws BaseException {
		try {
			EntityMeta entityMeta = sqlToyLazyDao.getEntityMeta(entity.getClass());
			return sqlToyLazyDao.findPageBySql(paginationModel, entityMeta.getPageSql(), entity);
		} catch (Exception e) {
			e.printStackTrace();
			throw new BaseException(e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.service.SqlToyCRUDService#findPageFrom(org.sagacity
	 * .core.database.model.PaginationModel, java.io.Serializable,
	 * org.sagacity.core.utils.callback.ReflectPropertyHandler)
	 */
	@Override
	public PaginationModel findPageFrom(PaginationModel paginationModel, Serializable entity,
			ReflectPropertyHandler reflectPropertyHandler) throws BaseException {
		try {
			EntityMeta entityMeta = sqlToyLazyDao.getEntityMeta(entity.getClass());
			return sqlToyLazyDao.findPageByQuery(paginationModel,
					new QueryExecutor(entityMeta.getPageSql(), entity).reflectPropertyHandler(reflectPropertyHandler))
					.getPageResult();
		} catch (Exception e) {
			e.printStackTrace();
			throw new BaseException(e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.service.SqlToyCRUDService#findTop(java.io.
	 * Serializable, long)
	 */
	@Override
	public List findTopFrom(Serializable entity, double topSize) throws BaseException {
		try {
			EntityMeta entityMeta = sqlToyLazyDao.getEntityMeta(entity.getClass());
			return sqlToyLazyDao.findTopBySql(entityMeta.getListSql(), entity, topSize);
		} catch (Exception e) {
			e.printStackTrace();
			throw new BaseException(e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.service.SqlToyCRUDService#getRandomResult(java
	 * .io.Serializable, int)
	 */
	@Override
	public List getRandomFrom(Serializable entity, double randomCount) throws BaseException {
		try {
			EntityMeta entityMeta = sqlToyLazyDao.getEntityMeta(entity.getClass());
			return sqlToyLazyDao.getRandomResult(
					StringUtil.isBlank(entityMeta.getListSql()) ? entityMeta.getPageSql() : entityMeta.getListSql(),
					entity, randomCount);
		} catch (Exception e) {
			e.printStackTrace();
			throw new BaseException(e);
		}
	}

}
