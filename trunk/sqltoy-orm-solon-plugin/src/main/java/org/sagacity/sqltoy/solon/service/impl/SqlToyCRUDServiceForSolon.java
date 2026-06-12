package org.sagacity.sqltoy.solon.service.impl;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Singleton;
import org.noear.solon.data.annotation.Transaction;
import org.sagacity.sqltoy.dao.LightDao;
import org.sagacity.sqltoy.model.CacheMatchFilter;
import org.sagacity.sqltoy.model.LockMode;
import org.sagacity.sqltoy.model.MapKit;
import org.sagacity.sqltoy.model.Page;
import org.sagacity.sqltoy.model.ParallQuery;
import org.sagacity.sqltoy.model.ParallelConfig;
import org.sagacity.sqltoy.model.QueryResult;
import org.sagacity.sqltoy.model.TreeTableModel;
import org.sagacity.sqltoy.service.SqlToyCRUDService;
import org.sagacity.sqltoy.translate.TranslateHandler;

/**
 *
 * @author 夜の孤城
 * @since 1.10
 */
@Singleton(false) // 因为会被多数据源使用，所以不能是单例
@Component
public class SqlToyCRUDServiceForSolon implements SqlToyCRUDService {
	protected LightDao lightDao;

	public void setLightDao(LightDao lightDao) {
		this.lightDao = lightDao;
	}

	@Transaction
	public Object save(Serializable entity) {
		return this.lightDao.save(entity);
	}

	@Transaction
	public <T extends Serializable> Long saveAll(List<T> entities) {
		return this.lightDao.saveAll(entities);
	}

	@Transaction
	public <T extends Serializable> Long saveAllIgnoreExist(List<T> entities) {
		return this.lightDao.saveAllIgnoreExist(entities);
	}

	@Transaction
	public Long update(Serializable entity, String... forceUpdateProps) {
		if (null == entity) {
			throw new IllegalArgumentException("update 数据对象为null!");
		} else {
			return this.lightDao.update(entity, forceUpdateProps);
		}
	}

	@Transaction
	public Long updateCascade(Serializable entity, String... forceUpdateProps) {
		if (null == entity) {
			throw new IllegalArgumentException("update 数据对象为null!");
		} else {
			return this.lightDao.updateCascade(entity, forceUpdateProps, (Class[]) null, (HashMap) null);
		}
	}

	@Transaction
	public Long updateDeeply(Serializable entity) {
		if (null == entity) {
			throw new IllegalArgumentException("updateDeeply 数据对象为null!");
		} else {
			return this.lightDao.updateDeeply(entity);
		}
	}

	@Transaction
	public <T extends Serializable> Long updateAll(List<T> entities, String... forceUpdateProps) {
		return this.lightDao.updateAll(entities, forceUpdateProps);
	}

	@Transaction
	public <T extends Serializable> Long updateAllDeeply(List<T> entities) {
		return this.lightDao.updateAllDeeply(entities);
	}

	@Transaction
	public Long saveOrUpdate(Serializable entity, String... forceUpdateProps) {
		if (null == entity) {
			throw new IllegalArgumentException("saveOrUpdate  数据对象为null!");
		} else {
			return this.lightDao.saveOrUpdate(entity, forceUpdateProps);
		}
	}

	@Transaction
	public <T extends Serializable> Long saveOrUpdateAll(List<T> entities, String... forceUpdateProps) {
		return this.lightDao.saveOrUpdateAll(entities, forceUpdateProps);
	}

	public <T extends Serializable> T load(T entity) {
		return this.lightDao.load(entity);
	}

	public <T extends Serializable> T loadCascade(T entity) {
		return this.lightDao.loadCascade(entity, (LockMode) null, new Class[0]);
	}

	public <T extends Serializable> List<T> loadAll(List<T> entities) {
		return this.lightDao.loadAll(entities);
	}

	public <T extends Serializable> List<T> loadAllCascade(List<T> entities, Class... cascadeTypes) {
		return this.lightDao.loadAllCascade(entities, cascadeTypes);
	}

	@Transaction
	public <T extends Serializable> List<T> loadByIds(Class<T> voClass, Object... ids) {
		return this.lightDao.loadByIds(voClass, ids);
	}

	@Transaction
	public Long delete(Serializable entity) {
		return this.lightDao.delete(entity);
	}

	@Transaction
	public <T extends Serializable> Long deleteAll(List<T> entities) {
		return this.lightDao.deleteAll(entities);
	}

	@Transaction
	public Long deleteByIds(Class entityClass, Object... ids) {
		return this.lightDao.deleteByIds(entityClass, ids);
	}

	@Transaction
	public void truncate(Class entityClass) {
		this.lightDao.truncate(entityClass);
	}

	@Transaction
	public boolean isUnique(Serializable entity, String... paramsNamed) {
		return this.lightDao.isUnique(entity, paramsNamed);
	}

	@Transaction
	public boolean wrapTreeTableRoute(Serializable entity, String pidField) {
		return this.lightDao.wrapTreeTableRoute((new TreeTableModel(entity)).pidField(pidField));
	}

	@Transaction
	public boolean wrapTreeTableRoute(Serializable entity, String pidField, int appendIdSize) {
		return this.lightDao.wrapTreeTableRoute((new TreeTableModel(entity)).pidField(pidField).idLength(appendIdSize));
	}

	public <T> List<QueryResult<T>> parallQuery(List<ParallQuery> parallQueryList, String[] paramNames,
			Object[] paramValues) {
		return this.lightDao.parallQuery(parallQueryList, MapKit.keys(paramNames).values(paramValues),
				(ParallelConfig) null);
	}

	public <T> List<QueryResult<T>> parallQuery(List<ParallQuery> parallQueryList, String[] paramNames,
			Object[] paramValues, ParallelConfig parallelConfig) {
		return this.lightDao.parallQuery(parallQueryList, MapKit.keys(paramNames).values(paramValues), parallelConfig);
	}

	public <T> List<QueryResult<T>> parallQuery(List<ParallQuery> parallQueryList, Map<String, Object> paramsMap,
			ParallelConfig parallelConfig) {
		return this.lightDao.parallQuery(parallQueryList, paramsMap, parallelConfig);
	}

	public long generateBizId(String signature, int increment) {
		return this.lightDao.generateBizId(signature, increment);
	}

	public String generateBizId(Serializable entity) {
		return this.lightDao.generateBizId(entity);
	}

	public void translate(Collection dataSet, String cacheName, TranslateHandler handler) {
		this.lightDao.translate(dataSet, cacheName, (String) null, 1, handler);
	}

	public void translate(Collection dataSet, String cacheName, String dictType, Integer index,
			TranslateHandler handler) {
		this.lightDao.translate(dataSet, cacheName, dictType, index, handler);
	}

	public boolean existCache(String cacheName) {
		return this.lightDao.existCache(cacheName);
	}

	public Set<String> getCacheNames() {
		return this.lightDao.getCacheNames();
	}

	public String[] cacheMatchKeys(String matchRegex, CacheMatchFilter cacheMatchFilter) {
		return this.lightDao.cacheMatchKeys(cacheMatchFilter, matchRegex);
	}

	@Override
	public String[] cacheMatchKeys(CacheMatchFilter cacheMatchFilter, String... strings) {
		return this.lightDao.cacheMatchKeys(cacheMatchFilter, strings);
	}

	public <T extends Serializable> List<T> convertType(List sourceList, Class<T> resultType) {
		return this.lightDao.convertType(sourceList, resultType, new String[0]);
	}

	public <T extends Serializable> T convertType(Serializable source, Class<T> resultType) {
		return this.lightDao.convertType(source, resultType, new String[0]);
	}

	public <T extends Serializable> Page<T> convertType(Page sourcePage, Class<T> resultType) {
		return this.lightDao.convertType(sourcePage, resultType, new String[0]);
	}
}
