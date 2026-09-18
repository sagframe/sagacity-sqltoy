package org.sagacity.sqltoy.link;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.model.ParallelConfig;
import org.sagacity.sqltoy.model.SaveMode;

/**
 * @project sagacity-sqltoy
 * @description 对象保存操作
 * @author zhongxuchen
 * @version v1.0,Date:2017-10-09
 */
public class Save extends BaseLink {

	private static final long serialVersionUID = 3086927739096637361L;

	/**
	 * 记录已经存在时的处理策略
	 */
	private SaveMode saveMode = SaveMode.APPEND;

	/**
	 * 是否自动提交
	 */
	private Boolean autoCommit = null;

	/**
	 * 强制修改的字段属性
	 */
	private String[] forceUpdateProps;

	/**
	 * 是否深度修改(update 2022-3-23 支持更新全部字段)
	 */
	private boolean deeply = false;

	/**
	 * 批处理提交记录数量
	 */
	private int batchSize = 0;

	private ParallelConfig parallelConfig;

	public Save parallelConfig(ParallelConfig parallelConfig) {
		this.parallelConfig = parallelConfig;
		return this;
	}

	public Save deeply(boolean deeply) {
		this.deeply = deeply;
		return this;
	}

	/**
	 * @param sqlToyContext sqltoy全局上下文对象
	 * @param dataSource    保存操作绑定的数据源，null表示使用默认数据源
	 */
	public Save(SqlToyContext sqlToyContext, DataSource dataSource) {
		super(sqlToyContext, dataSource);
	}

	/**
	 * 设置强制修改的属性
	 * 
	 * @param forceUpdateProps 强制修改的实体属性名称，属性值为null时也作为update语句的赋值字段
	 * @return 当前Save对象，支持链式调用
	 */
	public Save forceUpdateProps(String... forceUpdateProps) {
		this.forceUpdateProps = forceUpdateProps;
		return this;
	}

	/**
	 * 设置数据源
	 * 
	 * @param dataSource 当前保存操作绑定的数据源
	 * @return 当前Save对象，支持链式调用
	 */
	public Save dataSource(DataSource dataSource) {
		this.dataSource = dataSource;
		this.defaultDataSource = false;
		return this;
	}

	public Save autoCommit(Boolean autoCommit) {
		this.autoCommit = autoCommit;
		return this;
	}

	/**
	 * 保存时遇到已经存在时的三种模式(append:依然追加/update:修改/ignore:忽视)
	 * 
	 * @param saveMode 记录已存在时的处理模式，APPEND继续追加、UPDATE转为修改、IGNORE跳过不处理
	 * @return 当前Save对象，支持链式调用
	 */
	public Save saveMode(SaveMode saveMode) {
		this.saveMode = saveMode;
		return this;
	}

	/**
	 * 批量值
	 * 
	 * @param batchSize 批量提交的记录数量，小于等于0时使用sqltoyContext中配置的batchSize
	 * @return 当前Save对象，支持链式调用
	 */
	public Save batchSize(int batchSize) {
		this.batchSize = batchSize;
		return this;
	}

	/**
	 * 保存单条记录
	 * 
	 * @param entity 待保存的实体对象，主键值为空时按主键生成策略自动产生
	 * @return 保存成功后的实体对象，操作失败返回null
	 */
	public Object one(final Serializable entity) {
		if (entity == null) {
			throw new IllegalArgumentException("save entity is null!");
		}
		if (saveMode == SaveMode.APPEND) {
			return dialectFactory.save(sqlToyContext, entity, getDataSource(null));
		}
		if (saveMode == SaveMode.UPDATE) {
			if (deeply) {
				forceUpdateProps = sqlToyContext.getEntityMeta(entity.getClass()).getRejectIdFieldArray(true);
			}
			return dialectFactory.saveOrUpdate(sqlToyContext, entity, forceUpdateProps, getDataSource(null));
		}
		if (saveMode == SaveMode.IGNORE) {
			List entities = new ArrayList();
			entities.add(entity);
			return dialectFactory.saveAllIgnoreExist(sqlToyContext, entities, 1, null, null, getDataSource(null), null);
		}
		return null;
	}

	/**
	 * 批量保存
	 * 
	 * @param <T>      实体对象的类型，须为Serializable的子类
	 * @param entities 待批量保存的实体对象集合，主键值为空时按主键生成策略自动产生
	 * @return 实际成功保存的记录数量
	 */
	public <T extends Serializable> Long many(final List<T> entities) {
		if (entities == null || entities.isEmpty()) {
			throw new IllegalArgumentException("saveAll entities is null or empty!");
		}
		int realBatchSize = (batchSize > 0) ? batchSize : sqlToyContext.getBatchSize();
		if (saveMode == SaveMode.IGNORE) {
			return dialectFactory.saveAllIgnoreExist(sqlToyContext, entities, realBatchSize, null, parallelConfig,
					getDataSource(null), autoCommit);
		}
		if (saveMode == SaveMode.UPDATE) {
			// 深度修改获取全部字段属性
			if (deeply) {
				forceUpdateProps = sqlToyContext.getEntityMeta(entities.get(0).getClass()).getRejectIdFieldArray(true);
			}
			return dialectFactory.saveOrUpdateAll(sqlToyContext, entities, realBatchSize, forceUpdateProps, null,
					parallelConfig, getDataSource(null), autoCommit);
		}
		return dialectFactory.saveAll(sqlToyContext, entities, realBatchSize, null, parallelConfig, getDataSource(null),
				autoCommit);
	}
}
