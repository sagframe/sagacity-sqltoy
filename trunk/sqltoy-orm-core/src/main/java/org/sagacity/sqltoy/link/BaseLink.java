/**
 * 
 */
package org.sagacity.sqltoy.link;

import java.io.Serializable;

import javax.sql.DataSource;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.dialect.DialectFactory;
import org.sagacity.sqltoy.plugins.datasource.DataSourceSelector;
import org.sagacity.sqltoy.utils.DataSourceUtils;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * @project sagacity-sqltoy
 * @description 链式基础操作类，提供SqlToyContext、dataSource等必要属性的注入
 * @author zhongxuchen
 * @version v1.0,Date:2017年10月9日
 */
public abstract class BaseLink implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 6032935116286610811L;

	/**
	 * 数据源
	 */
	protected DataSource dataSource;

	/**
	 * sqltoy上下文
	 */
	protected SqlToyContext sqlToyContext;

	/**
	 * 是否默认dataSource
	 */
	protected boolean defaultDataSource = true;

	/**
	 * 各种数据库方言实现
	 */
	protected DialectFactory dialectFactory = DialectFactory.getInstance();

	public BaseLink(SqlToyContext sqlToyContext, DataSource dataSource) {
		this.sqlToyContext = sqlToyContext;
		this.dataSource = dataSource;
	}

	public DataSource getDataSource(SqlToyConfig sqltoyConfig) {
		// xml中定义的sql配置了datasource
		String sqlDataSource = (null == sqltoyConfig) ? null : sqltoyConfig.getDataSource();
		// 数据源选择扩展
		DataSourceSelector dataSourceSelector = sqlToyContext.getDataSourceSelector();
		return dataSourceSelector.getDataSource(sqlToyContext.getAppContext(), defaultDataSource ? null : dataSource,
				sqlDataSource, (defaultDataSource == false) ? null : dataSource, sqlToyContext.getDefaultDataSource());
	}

	/**
	 * @TODO 获取当前数据库的方言名称
	 * @return
	 */
	public String getDialect() {
		if (StringUtil.isNotBlank(sqlToyContext.getDialect())) {
			return sqlToyContext.getDialect();
		}
		return DataSourceUtils.getDialect(sqlToyContext, getDataSource(null));
	}
}
