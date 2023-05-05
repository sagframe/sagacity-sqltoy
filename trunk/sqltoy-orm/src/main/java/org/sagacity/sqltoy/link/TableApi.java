/**
 * 
 */
package org.sagacity.sqltoy.link;

import javax.sql.DataSource;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.model.SqlToyConfig;

/**
 * @project sagacity-sqltoy
 * @description 请在此说明类的功能
 * @author zhong
 * @version v1.0, Date:2023年5月5日
 * @modify 2023年5月5日,修改说明
 */
public class TableApi extends BaseLink {

	/**
	 * 
	 */
	private static final long serialVersionUID = -6239897514441516513L;

	/**
	 * @param sqlToyContext
	 * @param dataSource
	 */
	public TableApi(SqlToyContext sqlToyContext, DataSource dataSource) {
		super(sqlToyContext, dataSource);
	}

	@Override
	public DataSource getDataSource(SqlToyConfig sqltoyConfig) {
		// TODO Auto-generated method stub
		return super.getDataSource(sqltoyConfig);
	}

	@Override
	public String getDialect() {
		// TODO Auto-generated method stub
		return super.getDialect();
	}

}
