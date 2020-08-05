/**
 * 
 */
package org.sagacity.sqltoy.callback;

import org.sagacity.sqltoy.config.model.EntityMeta;

/**
 * @project sagacity-sqltoy
 * @description 请在此说明类的功能
 * @author zhong
 * @version v1.0, Date:2020-8-5
 * @modify 2020-8-5,修改说明
 */
public abstract class UniqueTopSqlHandler {
	public abstract String process(EntityMeta entityMeta, String[] realParamNamed, String tableName, Integer dbType,
			int topSize);
}
