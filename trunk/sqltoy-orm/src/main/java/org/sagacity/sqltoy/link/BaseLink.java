/**
 * 
 */
package org.sagacity.sqltoy.link;

import java.io.Serializable;

import javax.sql.DataSource;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.dialect.DialectFactory;
import org.sagacity.sqltoy.utils.DataSourceUtils;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * @project sagacity-sqltoy
 * @description 基础操作类
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
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

	public DataSource getDataSource(SqlToyConfig sqlToyConfig) {
		DataSource result = dataSource;
		// 数据源为空或非强制指定了数据源，则使用sql中指定的数据源
		if ((null == result || defaultDataSource == false)
				&& (sqlToyConfig != null && null != sqlToyConfig.getDataSource())) {
			result = sqlToyContext.getDataSourceBean(sqlToyConfig.getDataSource());
		}
		if (null == result) {
			result = sqlToyContext.obtainDataSource(sqlToyConfig.getDataSource());
		}
		return result;
	}

	/**
	 * @TODO 获取当前数据库的方言名称
	 * @return
	 */
	public String getDialect() {
		if (StringUtil.isNotBlank(sqlToyContext.getDialect())) {
			return sqlToyContext.getDialect();
		}
		return DataSourceUtils.getDialect(getDataSource(null));
	}
}
