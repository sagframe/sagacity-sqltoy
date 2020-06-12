/**
 * 
 */
package org.sagacity.sqltoy.plugins.sharding;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.model.IgnoreCaseLinkedMap;
import org.sagacity.sqltoy.model.ShardingDBModel;

/**
 * @project sqltoy-orm
 * @description sharding 策略接口
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:ShardingStrategy.java,Revision:v1.0,Date:2015年3月17日
 */
public interface ShardingStrategy {
	/**
	 * @todo 根据条件确定当前sql语句中的表要替换成的具体表名
	 * @param sqlToyContext
	 * @param entityClass
	 * @param tableName     传递过来的当前表名
	 * @param decisionType  决策类别
	 * @param paramsMap     传递{[参数1,参数1值],[参数2,参数2值]}
	 * @return 根据参数取得具体表名,返回null表示使用原表
	 */
	public String getShardingTable(SqlToyContext sqlToyContext, Class entityClass, String baseTableName,
			String decisionType, IgnoreCaseLinkedMap<String, Object> paramsMap);

	/**
	 * @todo 根据分库策略获取最终执行的数据库信息
	 * @param sqlToyContext
	 * @param entityClass
	 * @param tableOrSql
	 * @param decisionType  决策类别
	 * @param paramsMap     传递{[参数1,参数1值],[参数2,参数2值]}
	 * @return 根据参数取得具体dataSource,返回null则表示使用当前默认的数据源
	 */
	public ShardingDBModel getShardingDB(SqlToyContext sqlToyContext, Class entityClass, String tableOrSql,
			String decisionType, IgnoreCaseLinkedMap<String, Object> paramsMap);

	/**
	 * @TODO 初始化
	 */
	public void initialize();
}
