package org.sagacity.sqltoy.service.impl;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.sagacity.sqltoy.dao.LightDao;
import org.sagacity.sqltoy.model.CacheMatchFilter;
import org.sagacity.sqltoy.model.Page;
import org.sagacity.sqltoy.model.ParallQuery;
import org.sagacity.sqltoy.model.ParallelConfig;
import org.sagacity.sqltoy.model.QueryResult;
import org.sagacity.sqltoy.model.TreeTableModel;
import org.sagacity.sqltoy.service.SqlToyCRUDService;
import org.sagacity.sqltoy.translate.TranslateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * @project sqltoy-orm
 * @description 提供一些非常简单业务场景，比如传统不区分dto、pojo的项目，因一些简单的增加、修改、加载操作，在自定义的service中也只是存粹的中转一下dao.xxx形式的操作
 *              如下的service中毫无附加逻辑，存粹一个结构性中转调用，因此创建了一个通用性的Service
 *              <p>
 *              public class StaffInfoService { private LightDao ligtDao; public
 *              String addStaff(StaffInfo staffInfo) { return
 *              ligtDao.save(staffInfo); } }
 *              </p>
 * @author zhongxuchen
 * @version v1.0,Date:2012-7-16
 */
@SuppressWarnings({ "rawtypes" })
//@Service("sqlToyCRUDService")
public class SqlToyCRUDServiceImpl implements SqlToyCRUDService {
	/**
	 * 定义全局日志
	 */
	protected final Logger logger = LoggerFactory.getLogger(SqlToyCRUDServiceImpl.class);

	/**
	 * 全局懒处理dao
	 */
	protected LightDao lightDao;

	/**
	 * @param sqlToyLazyDao the sqlToyLazyDao to set
	 */
	@Autowired(required = false)
	@Qualifier(value = "lightDao")
	public void setLightDao(LightDao lightDao) {
		this.lightDao = lightDao;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.service.SqlToyCRUDService#save(java.io.Serializable)
	 * )
	 */
	@Override
	@Transactional
	public Object save(Serializable entity) {
		return lightDao.save(entity);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.service.SqlToyCRUDService#saveAll(java.util.List)
	 */
	@Override
	@Transactional
	public <T extends Serializable> Long saveAll(List<T> entities) {
		return lightDao.saveAll(entities);
	}

	@Override
	@Transactional
	public <T extends Serializable> Long saveAllIgnoreExist(List<T> entities) {
		return lightDao.saveAllIgnoreExist(entities);
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
	public Long update(Serializable entity, String... forceUpdateProps) {
		if (null == entity) {
			throw new IllegalArgumentException("update 数据对象为null!");
		}
		return lightDao.update(entity, forceUpdateProps);
	}

	@Override
	@Transactional
	public Long updateCascade(Serializable entity, String... forceUpdateProps) {
		if (null == entity) {
			throw new IllegalArgumentException("update 数据对象为null!");
		}
		return lightDao.updateCascade(entity, forceUpdateProps, null, null);
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
		if (null == entity) {
			throw new IllegalArgumentException("updateDeeply 数据对象为null!");
		}
		return lightDao.updateDeeply(entity);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.service.SqlToyCRUDService#updateAll(java.util .List,
	 * java.lang.String[])
	 */
	@Override
	@Transactional
	public <T extends Serializable> Long updateAll(List<T> entities, String... forceUpdateProps) {
		return lightDao.updateAll(entities, forceUpdateProps);
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
		return lightDao.updateAllDeeply(entities);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.service.SqlToyCRUDService#saveOrUpdate(java.io
	 * .Serializable, java.lang.String[])
	 */
	@Override
	@Transactional
	public Long saveOrUpdate(Serializable entity, String... forceUpdateProps) {
		if (null == entity) {
			throw new IllegalArgumentException("saveOrUpdate  数据对象为null!");
		}
		return lightDao.saveOrUpdate(entity, forceUpdateProps);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.service.SqlToyCRUDService#saveOrUpdateAll(java
	 * .io.Serializable, java.lang.String[])
	 */
	@Override
	@Transactional
	public <T extends Serializable> Long saveOrUpdateAll(List<T> entities, String... forceUpdateProps) {
		return lightDao.saveOrUpdateAll(entities, forceUpdateProps);
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
		return lightDao.load(entity);
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
		return lightDao.loadCascade(entity, null);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.service.SqlToyCRUDService#loadAll(java.util.List)
	 */
	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public <T extends Serializable> List<T> loadAll(List<T> entities) {
		return lightDao.loadAll(entities);
	}

	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public <T extends Serializable> List<T> loadAllCascade(List<T> entities, final Class... cascadeTypes) {
		return lightDao.loadAllCascade(entities, cascadeTypes);
	}

	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public <T extends Serializable> List<T> loadByIds(Class<T> voClass, Object... ids) {
		return lightDao.loadByIds(voClass, ids);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.sagacity.sqltoy.service.SqlToyCRUDService#delete(java.io.Serializable )
	 */
	@Override
	@Transactional
	public Long delete(Serializable entity) {
		return lightDao.delete(entity);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.service.SqlToyCRUDService#deleteAll(java.util .List)
	 */
	@Override
	@Transactional
	public <T extends Serializable> Long deleteAll(List<T> entities) {
		return lightDao.deleteAll(entities);
	}

	@Override
	public Long deleteByIds(Class entityClass, Object... ids) {
		return lightDao.deleteByIds(entityClass, ids);
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
		lightDao.truncate(entityClass);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.service.SqlToyCRUDService#isUnique(java.io.
	 * Serializable, java.lang.String[], java.lang.String)
	 */
	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public boolean isUnique(Serializable entity, String... paramsNamed) {
		return lightDao.isUnique(entity, paramsNamed);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.service.SqlToyCRUDService#wrapTreeTableRoute(
	 * java.io.Serializable, java.lang.String)
	 */
	@Override
	@Transactional
	public boolean wrapTreeTableRoute(Serializable entity, String pidField) {
		return lightDao.wrapTreeTableRoute(new TreeTableModel(entity).pidField(pidField));
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
	public boolean wrapTreeTableRoute(Serializable entity, String pidField, int appendIdSize) {
		return lightDao.wrapTreeTableRoute(new TreeTableModel(entity).pidField(pidField).idLength(appendIdSize));
	}

	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public <T> List<QueryResult<T>> parallQuery(List<ParallQuery> parallQueryList, Map<String, Object> paramsMap,
			ParallelConfig parallelConfig) {
		return lightDao.parallQuery(parallQueryList, paramsMap, parallelConfig);
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
		return lightDao.generateBizId(signature, increment);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.service.SqlToyCRUDService#generateBizId(java.io.
	 * Serializable)
	 */
	@Override
	public String generateBizId(Serializable entity) {
		return lightDao.generateBizId(entity);
	}

	@Override
	public void translate(Collection dataSet, String cacheName, TranslateHandler handler) {
		lightDao.translate(dataSet, cacheName, null, 1, handler);
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
	public void translate(Collection dataSet, String cacheName, String dictType, Integer index,
			TranslateHandler handler) {
		lightDao.translate(dataSet, cacheName, dictType, index, handler);
	}

	/**
	 * @todo 判断缓存是否存在
	 * @param cacheName
	 * @return
	 */
	@Override
	public boolean existCache(String cacheName) {
		return lightDao.existCache(cacheName);
	}

	@Override
	public Set<String> getCacheNames() {
		return lightDao.getCacheNames();
	}

	@Override
	public String[] cacheMatchKeys(CacheMatchFilter cacheMatchFilter, String... matchRegexes) {
		return lightDao.cacheMatchKeys(cacheMatchFilter, matchRegexes);
	}

	@Override
	public <T extends Serializable> List<T> convertType(List sourceList, Class<T> resultType) {
		return lightDao.convertType(sourceList, resultType);
	}

	@Override
	public <T extends Serializable> T convertType(Serializable source, Class<T> resultType) {
		return lightDao.convertType(source, resultType);
	}

	@Override
	public <T extends Serializable> Page<T> convertType(Page sourcePage, Class<T> resultType) {
		return lightDao.convertType(sourcePage, resultType);
	}

}
