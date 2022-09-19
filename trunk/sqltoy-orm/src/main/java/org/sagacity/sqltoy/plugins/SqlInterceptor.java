/**
 * 
 */
package org.sagacity.sqltoy.plugins;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.model.OperateType;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlToyResult;

/**
 * @project sagacity-sqltoy
 * @description 提供sql拦截器，为一些特殊场景提供自行扩展能力,比如多租户场景下，自动扩展租户过滤条件避免越权
 * @author zhongxuchen
 * @version v1.0, Date:2022年9月8日
 * @modify 2022年9月8日,修改说明
 */
public interface SqlInterceptor {
	/**
	 * @TODO 对最终执行sql和sql参数进行处理
	 * @param sqlToyContext 支持getEntityMeta(tableName)获取表信息
	 * @param sqlToyConfig  传递原本的sql配置,可以通过获取paramNames判断是否sql中已经有相关参数
	 * @param operateType   search\page\top\random\count 等，
	 * @param sqlToyResult  存放了最终的sql 和paramValues
	 * @param entityClass   实体对象类型(只针对对象crud操作才有值)
	 * @param dbType        当前数据库类型,通过DBType.xxx 进行对比
	 * @return
	 */
	public default SqlToyResult decorate(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig,
			OperateType operateType, SqlToyResult sqlToyResult, Class entityClass, Integer dbType) {
		/**
		 * 注意: 开启sqlToyContext.getEntityMeta(tableName)获得pojo的注解(表字段等)，需要设置
		 * spring.sqltoy.packagesToScan 数组属性(pojo的路径)，让sqltoy启动时主动加载pojo
		 */
		return sqlToyResult;
	}
}
