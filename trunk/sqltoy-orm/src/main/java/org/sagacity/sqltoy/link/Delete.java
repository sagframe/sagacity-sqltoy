/**
 * 
 */
package org.sagacity.sqltoy.link;

import java.io.Serializable;
import java.util.List;

import javax.sql.DataSource;

import org.sagacity.sqltoy.SqlToyContext;

/**
 * @project sagacity-sqltoy
 * @description 删除操作
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:Delete.java,Revision:v1.0,Date:2017年10月9日
 */
public class Delete extends BaseLink {

	/**
	 * 
	 */
	private static final long serialVersionUID = 3342119854878262119L;

	/**
	 * 批次处理的记录数量
	 */
	private int batchSize = 0;

	/**
	 * 是否自动提交
	 */
	private Boolean autoCommit = null;

	/**
	 * @param sqlToyContext
	 * @param dataSource
	 */
	public Delete(SqlToyContext sqlToyContext, DataSource dataSource) {
		super(sqlToyContext, dataSource);
	}

	/**
	 * @todo 设置数据源
	 * @param dataSource
	 * @return
	 */
	public Delete dataSource(DataSource dataSource) {
		this.dataSource = dataSource;
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
	 * @todo 删除单条对象记录
	 * @param entity
	 */
	public Long one(final Serializable entity) {
		if (entity == null)
			throw new IllegalArgumentException("delete entity is null!");
		return dialectFactory.delete(sqlToyContext, entity, dataSource);
	}

	/**
	 * @todo 批量删除对象记录
	 * @param entities
	 */
	public <T extends Serializable> Long many(final List<T> entities) {
		if (entities == null || entities.isEmpty())
			throw new IllegalArgumentException("deleteAll entities is null or empty!");
		int realBatchSize = (batchSize > 0) ? batchSize : sqlToyContext.getBatchSize();
		return dialectFactory.deleteAll(sqlToyContext, entities, realBatchSize, dataSource, autoCommit);
	}

}
