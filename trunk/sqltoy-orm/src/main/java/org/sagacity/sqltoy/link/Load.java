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
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:Load.java,Revision:v1.0,Date:2017年10月9日
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

	/**
	 * @todo 锁表策略
	 * @param lockMode
	 * @return
	 */
	public Load lock(LockMode lockMode) {
		this.lockMode = lockMode;
		return this;
	}

	/**
	 * @todo 单对象加载
	 * @param entity
	 * @return
	 */
	public <T extends Serializable> T one(T entity) {
		if (entity == null)
			throw new IllegalArgumentException("load entity is null!");
		return dialectFactory.load(sqlToyContext, entity, cascadeTypes, lockMode, dataSource);
	}

	/**
	 * @todo 批量加载
	 * @param entities
	 * @return
	 */
	public <T extends Serializable> List<T> many(List<T> entities) {
		if (entities == null || entities.isEmpty())
			throw new IllegalArgumentException("loadAll entities is null or empty!");
		return dialectFactory.loadAll(sqlToyContext, entities, cascadeTypes, lockMode, dataSource);
	}

}
