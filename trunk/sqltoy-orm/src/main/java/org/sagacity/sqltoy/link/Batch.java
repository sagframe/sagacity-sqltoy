/**
 * 
 */
package org.sagacity.sqltoy.link;

import java.util.List;

import javax.sql.DataSource;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.callback.InsertRowCallbackHandler;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlType;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * @project sagacity-sqltoy
 * @description 批量执行
 * @author zhongxuchen
 * @version v1.0,Date:2017年10月24日
 */
public class Batch extends BaseLink {

	/**
	 * 
	 */
	private static final long serialVersionUID = -3564526241047940595L;

	/**
	 * 批量处理的数据集合
	 */
	private List<?> dataSet;

	/**
	 * 批次记录数量
	 */
	private int batchSize;

	/**
	 * 插入反调处理器
	 */
	private InsertRowCallbackHandler insertCallhandler;

	/**
	 * 是否自动提交
	 */
	private Boolean autoCommit = false;

	/**
	 * sql语句
	 */
	private String sql;

	/**
	 * @param sqlToyContext
	 * @param dataSource
	 */
	public Batch(SqlToyContext sqlToyContext, DataSource dataSource) {
		super(sqlToyContext, dataSource);
	}

	public Batch dataSource(DataSource dataSource) {
		this.dataSource = dataSource;
		this.defaultDataSource = false;
		return this;
	}

	public Batch sql(String sql) {
		this.sql = sql;
		return this;
	}

	public Batch dataSet(List<?> dataSet) {
		this.dataSet = dataSet;
		return this;
	}

	public Batch batchSize(int batchSize) {
		this.batchSize = batchSize;
		return this;
	}

	public Batch autoCommit(Boolean autoCommit) {
		this.autoCommit = autoCommit;
		return this;
	}

	public Batch insertHandler(InsertRowCallbackHandler insertCallhandler) {
		this.insertCallhandler = insertCallhandler;
		return this;
	}

	public Long submit() {
		if (StringUtil.isBlank(sql)) {
			throw new IllegalArgumentException("batch execute sql is null!");
		}
		int realBatchSize = (batchSize > 0) ? batchSize : sqlToyContext.getBatchSize();
		SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(sql, SqlType.update, super.getDialect(), null);
		return dialectFactory.batchUpdate(sqlToyContext, sqlToyConfig, dataSet, realBatchSize, null, insertCallhandler,
				autoCommit, getDataSource(sqlToyConfig));
	}
}
