package org.sagacity.sqltoy.link;

import java.io.Serializable;
import java.util.List;

import javax.sql.DataSource;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.model.ParallelConfig;

/**
 * @project sagacity-sqltoy
 * @description 删除操作
 * @author zhongxuchen
 * @version v1.0,Date:2017-10-09
 */
public class Delete extends BaseLink {

	private static final long serialVersionUID = 3342119854878262119L;

	/**
	 * 批次处理的记录数量
	 */
	private int batchSize = 0;

	/**
	 * 是否自动提交
	 */
	private Boolean autoCommit = null;

	private ParallelConfig parallelConfig;

	public Delete parallelConfig(ParallelConfig parallelConfig) {
		this.parallelConfig = parallelConfig;
		return this;
	}

	/**
	 * @param sqlToyContext sqltoy全局上下文对象
	 * @param dataSource    删除操作绑定的数据源，null表示使用默认数据源
	 */
	public Delete(SqlToyContext sqlToyContext, DataSource dataSource) {
		super(sqlToyContext, dataSource);
	}

	/**
	 * 设置数据源
	 * 
	 * @param dataSource 当前删除操作绑定的数据源
	 * @return 当前Delete对象，支持链式调用
	 */
	public Delete dataSource(DataSource dataSource) {
		this.dataSource = dataSource;
		this.defaultDataSource = false;
		return this;
	}

	public Delete batchSize(int batchSize) {
		this.batchSize = batchSize;
		return this;
	}

	public Delete autoCommit(Boolean autoCommit) {
		this.autoCommit = autoCommit;
		return this;
	}

	/**
	 * 删除单条对象记录
	 * 
	 * @param entity 待删除的实体对象，以主键值作为删除条件
	 */
	public Long one(final Serializable entity) {
		if (entity == null) {
			throw new IllegalArgumentException("delete entity is null!");
		}
		return dialectFactory.delete(sqlToyContext, entity, getDataSource(null));
	}

	/**
	 * 批量删除对象记录
	 * 
	 * @param entities 待批量删除的实体对象集合，按主键值分批提交删除
	 */
	public <T extends Serializable> Long many(final List<T> entities) {
		if (entities == null || entities.isEmpty()) {
			throw new IllegalArgumentException("deleteAll entities is null or empty!");
		}
		int realBatchSize = (batchSize > 0) ? batchSize : sqlToyContext.getBatchSize();
		return dialectFactory.deleteAll(sqlToyContext, entities, realBatchSize, parallelConfig, getDataSource(null),
				autoCommit);
	}

}
