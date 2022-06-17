/**
 * 
 */
package org.sagacity.sqltoy.callback;

import org.sagacity.sqltoy.config.model.EntityMeta;

/**
 * @project sagacity-sqltoy
 * @description 提供给各个数据库针对唯一性验证组织取top记录的反调(sqltoy内部使用)
 * @author zhongxuchen
 * @version v1.0, Date:2020-8-5
 * @modify 2020-8-5,修改说明
 */
@FunctionalInterface
public interface UniqueSqlHandler {
	/**
	 * @TODO 获得unique性查询对应的 top 2 记录sql，如取到2条直接就判断为重复，单条则判断是否是自身，0条表示不重复
	 * @param entityMeta
	 * @param paramNames
	 * @param tableName
	 * @param topSize    一般设置为2
	 * @return
	 */
	public String process(EntityMeta entityMeta, String[] paramNames, String tableName, int topSize);
}
