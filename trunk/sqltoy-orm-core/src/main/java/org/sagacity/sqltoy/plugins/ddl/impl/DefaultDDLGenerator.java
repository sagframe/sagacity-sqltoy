package org.sagacity.sqltoy.plugins.ddl.impl;

import org.sagacity.sqltoy.model.TableMeta;
import org.sagacity.sqltoy.plugins.ddl.DialectDDLGenerator;

/**
 * @project sagacity-sqltoy
 * @description 提供默认DDL未实现提示
 * @author zhongxuchen
 * @version v1.0,Date:2023-12-21
 * @modify Date:2023-12-21,修改说明
 */
public class DefaultDDLGenerator implements DialectDDLGenerator {

	@Override
	public String createTableSql(TableMeta tableMeta, String schema, String upperOrLower, int dbType) {
		throw new RuntimeException(
				"DDL generation is not implemented for this dialect, please provide a custom implementation via spring.sqltoy.dialectDDLGenerator=xxxxx!");
	}

}
