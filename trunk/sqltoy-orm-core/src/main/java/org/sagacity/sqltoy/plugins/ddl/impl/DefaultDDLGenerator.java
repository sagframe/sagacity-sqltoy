/**
 * 
 */
package org.sagacity.sqltoy.plugins.ddl.impl;

import org.sagacity.sqltoy.model.TableMeta;
import org.sagacity.sqltoy.plugins.ddl.DialectDDLGenerator;

/**
 * @project sagacity-sqltoy
 * @description 提供默认DDL未实现提示
 * @author zhongxuchen
 * @version v1.0, Date:2023年12月21日
 * @modify 2023年12月21日,修改说明
 */
public class DefaultDDLGenerator implements DialectDDLGenerator {

	@Override
	public String createTableSql(TableMeta tableMeta, String schema, int dbType) {
		throw new RuntimeException("该方言还未提供DDL产生的实现,请通过spring.sqltoy.dialectDDLGenerator=xxxxx 形式提供自定义实现!");
	}

}
