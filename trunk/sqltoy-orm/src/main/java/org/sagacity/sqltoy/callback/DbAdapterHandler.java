package org.sagacity.sqltoy.callback;

import javax.sql.DataSource;

import org.sagacity.sqltoy.config.model.SqlToyConfig;

/**
 * @project sagacity-sqltoy
 * @description 提供sql查询语句跨库适配反调函数
 *              <p>
 *              <li>主要用于做产品化软件，一套软件适用多种数据库</li>
 *              <li>要求在一种数据库下开发，然后查询同时在其他数据库下执行，检验sql的跨数据库适配性</li>
 *              </p>
 * @author zhongxuchen
 * @version v1.0,Date:2022-8-13
 */
@FunctionalInterface
public interface DbAdapterHandler {
	public void query(SqlToyConfig sqlToyConfig, DataSource dataSource);
}
