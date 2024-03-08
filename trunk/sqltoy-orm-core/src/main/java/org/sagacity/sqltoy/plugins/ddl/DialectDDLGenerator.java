/**
 * 
 */
package org.sagacity.sqltoy.plugins.ddl;

import org.sagacity.sqltoy.model.TableMeta;

/**
 * @project sagacity-sqltoy
 * @description 不同方言通过POJO产生DDL的实现器接口定义
 * @author zhongxuchen
 * @version v1.0, Date:2023年12月17日
 * @modify 2023年12月17日,修改说明
 */
public interface DialectDDLGenerator {

	/**
	 * @TODO 根据单个POJO产生创建表的sql
	 * @param tableMeta
	 * @param schema    sqlserver需要
	 * @param dbType
	 * @return
	 */
	public default String createTableSql(TableMeta tableMeta, String schema, int dbType) {
		return null;
	}
}
