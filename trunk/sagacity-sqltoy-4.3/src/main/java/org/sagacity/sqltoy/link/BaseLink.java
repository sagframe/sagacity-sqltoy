/**
 * 
 */
package org.sagacity.sqltoy.link;

import javax.sql.DataSource;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.dialect.DialectFactory;

/**
 * @project sagacity-sqltoy
 * @description 基础操作类
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:BaseLink.java,Revision:v1.0,Date:2017年10月9日
 */
public abstract class BaseLink {
	/**
	 * 数据源
	 */
	protected DataSource dataSource;

	/**
	 * sqltoy上下文
	 */
	protected SqlToyContext sqlToyContext;

	/**
	 * 各种数据库方言实现
	 */
	protected DialectFactory dialectFactory = DialectFactory.getInstance();

	public BaseLink(SqlToyContext sqlToyContext, DataSource dataSource) {
		this.sqlToyContext = sqlToyContext;
		this.dataSource = dataSource;
	}
}
