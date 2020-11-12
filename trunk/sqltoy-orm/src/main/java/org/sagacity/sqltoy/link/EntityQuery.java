/**
 * 
 */
package org.sagacity.sqltoy.link;

import javax.sql.DataSource;

import org.sagacity.sqltoy.SqlToyContext;

/**
 * @project sagacity-sqltoy
 * @description 请在此说明类的功能
 * @author zhong
 * @version v1.0, Date:2020-11-12
 * @modify 2020-11-12,修改说明
 */
public class EntityQuery extends BaseLink {

	/**
	 * 
	 */
	private static final long serialVersionUID = 7361432162181518821L;

	/**
	 * @param sqlToyContext
	 * @param dataSource
	 */
	public EntityQuery(SqlToyContext sqlToyContext, DataSource dataSource) {
		super(sqlToyContext, dataSource);
	}

}
