package org.sagacity.sqltoy.link;

import java.io.Serializable;
import java.util.List;

import javax.sql.DataSource;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.model.LockMode;
import org.sagacity.sqltoy.model.ParallelConfig;

/**
 * @project sagacity-sqltoy
 * @description 对象加载操作
 * @author zhongxuchen
 * @version v1.0,Date:2017-10-09
 */
public class Load extends BaseLink {
	private static final long serialVersionUID = 9187056738357750608L;

	/**
	 * 锁表模式类型
	 */
	private LockMode lockMode;

	/**
	 * 锁等待时长(秒)
	 */
	private int lockWaitTimeout = -1;

	/**
	 * 查询超时时长（秒）
	 */
	private int timeout;

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

	private ParallelConfig parallelConfig;

	public Load parallelConfig(ParallelConfig parallelConfig) {
		this.parallelConfig = parallelConfig;
		return this;
	}

	/**
	 * @param sqlToyContext sqltoy全局上下文对象
	 * @param dataSource    加载操作绑定的数据源，null表示使用默认数据源
	 */
	public Load(SqlToyContext sqlToyContext, DataSource dataSource) {
		super(sqlToyContext, dataSource);
	}

	/**
	 * 额外指定数据源
	 * 
	 * @param dataSource 当前加载操作绑定的数据源
	 * @return 当前Load对象，支持链式调用
	 */
	public Load dataSource(DataSource dataSource) {
		this.dataSource = dataSource;
		this.defaultDataSource = false;
		return this;
	}

	public Load queryTimeout(int timeout) {
		this.timeout = timeout;
		return this;
	}

	/**
	 * 级联加载的对象
	 * 
	 * @param cascadeTypes 需要级联加载的关联对象属性对应的实体类型
	 * @return 当前Load对象，支持链式调用
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
	 * 锁表策略
	 * 
	 * @param lockMode 加载记录时对数据加锁的模式，如：UPGRADE等待加锁、UPGRADE_NOWAIT不等待、UPGRADE_SKIPLOCK跳过已锁定记录
	 * @return 当前Load对象，支持链式调用
	 */
	public Load lock(LockMode lockMode) {
		this.lockMode = lockMode;
		return this;
	}

	public Load lockWaitTimeout(int lockWaitTimeout) {
		this.lockWaitTimeout = lockWaitTimeout;
		return this;
	}

	public Load onlyCascade() {
		this.onlyCascade = true;
		return this;
	}

	/**
	 * 单对象加载
	 * 
	 * @param entity 承载主键值的实体对象，加载后查询结果会填充到该对象
	 * @return 加载到的实体对象（包含级联子对象），未找到返回null
	 */
	public <T extends Serializable> T one(T entity) {
		if (entity == null) {
			throw new IllegalArgumentException("load entity is null!");
		}
		if ((cascadeTypes == null || cascadeTypes.length == 0) && (cascadeAll || onlyCascade)) {
			cascadeTypes = sqlToyContext.getEntityMeta(entity.getClass()).getCascadeTypes();
		}
		return dialectFactory.load(sqlToyContext, entity, onlyCascade, cascadeTypes, lockMode, lockWaitTimeout,
				getDataSource(null), timeout);
	}

	/**
	 * 批量加载
	 * 
	 * @param entities 承载主键值的实体对象集合，按主键批量加载并回填查询结果
	 * @return 加载到的实体对象集合，未匹配到记录的对象不在集合中返回
	 */
	public <T extends Serializable> List<T> many(List<T> entities) {
		if (entities == null || entities.isEmpty()) {
			throw new IllegalArgumentException("loadAll entities is null or empty!");
		}
		if ((cascadeTypes == null || cascadeTypes.length == 0) && (cascadeAll || onlyCascade)) {
			cascadeTypes = sqlToyContext.getEntityMeta(entities.get(0).getClass()).getCascadeTypes();
		}
		return dialectFactory.loadAll(sqlToyContext, entities, onlyCascade, cascadeTypes, lockMode, lockWaitTimeout,
				parallelConfig, getDataSource(null), timeout);
	}

}
