/**
 * 
 */
package org.sagacity.sqltoy.link;

import java.io.Serializable;
import java.util.List;

import javax.sql.DataSource;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.model.LockMode;

/**
 * @project sagacity-sqltoy
 * @description 对象加载操作
 * @author zhongxuchen
 * @version v1.0,Date:2017年10月9日
 */
public class Load extends BaseLink {
	/**
	 * 
	 */
	private static final long serialVersionUID = 9187056738357750608L;

	/**
	 * 锁表模式类型
	 */
	private LockMode lockMode;

	/**
	 * 级联的对象类型
	 */
	private Class<?>[] cascadeTypes;

	/**
	 * 级联加载所有子对象
	 */
	private boolean cascadeAll = false;

	/**
	 * 仅仅只加载子对象，主对象无需重复加载查询
	 */
	private boolean onlyCascade = false;

	/**
	 * @param sqlToyContext
	 * @param dataSource
	 */
	public Load(SqlToyContext sqlToyContext, DataSource dataSource) {
		super(sqlToyContext, dataSource);
	}

	/**
	 * @todo 额外指定数据源
	 * @param dataSource
	 * @return
	 */
	public Load dataSource(DataSource dataSource) {
		this.dataSource = dataSource;
		this.defaultDataSource = false;
		return this;
	}

	/**
	 * @todo 级联加载的对象
	 * @param cascadeTypes
	 * @return
	 */
	public Load cascade(Class<?>... cascadeTypes) {
		this.cascadeTypes = cascadeTypes;
		return this;
	}

	public Load cascadeAll() {
		this.cascadeAll = true;
		return this;
	}

	/**
	 * @todo 锁表策略
	 * @param lockMode
	 * @return
	 */
	public Load lock(LockMode lockMode) {
		this.lockMode = lockMode;
		return this;
	}

	public Load onlyCascade() {
		this.onlyCascade = true;
		return this;
	}

	/**
	 * @todo 单对象加载
	 * @param entity
	 * @return
	 */
	public <T extends Serializable> T one(T entity) {
		if (entity == null) {
			throw new IllegalArgumentException("load entity is null!");
		}
		if ((cascadeTypes == null || cascadeTypes.length == 0) && (cascadeAll || onlyCascade)) {
			cascadeTypes = sqlToyContext.getEntityMeta(entity.getClass()).getCascadeTypes();
		}
		return dialectFactory.load(sqlToyContext, entity, onlyCascade, cascadeTypes, lockMode, getDataSource(null));
	}

	/**
	 * @todo 批量加载
	 * @param entities
	 * @return
	 */
	public <T extends Serializable> List<T> many(List<T> entities) {
		if (entities == null || entities.isEmpty()) {
			throw new IllegalArgumentException("loadAll entities is null or empty!");
		}
		if ((cascadeTypes == null || cascadeTypes.length == 0) && (cascadeAll || onlyCascade)) {
			cascadeTypes = sqlToyContext.getEntityMeta(entities.get(0).getClass()).getCascadeTypes();
		}
		return dialectFactory.loadAll(sqlToyContext, entities, onlyCascade, cascadeTypes, lockMode,
				getDataSource(null));
	}

}
