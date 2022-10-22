/**
 * 
 */
package org.sagacity.sqltoy.plugins.datasource.impl;

import java.util.Map;

import javax.sql.DataSource;

import org.sagacity.sqltoy.integration.AppContext;
import org.sagacity.sqltoy.plugins.datasource.DataSourceSelector;
import org.sagacity.sqltoy.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sagacity-sqltoy
 * @description 针对数据源选择器提供默认实现
 * @author zhongxuchen
 * @version v1.0, Date:2021-4-15
 * @modify 2021-4-15,修改说明
 */
public class DefaultDataSourceSelector implements DataSourceSelector {
	/**
	 * 定义日志
	 */
	protected final Logger logger = LoggerFactory.getLogger(DefaultDataSourceSelector.class);

	@Override
	public DataSource getDataSource(AppContext appContext, DataSource pointDataSouce, String sqlDataSourceName,
			DataSource injectDataSource, DataSource defaultDataSource) {
		// 第一优先:直接指定的数据源不为空
		if (pointDataSouce != null) {
			return pointDataSouce;
		}
		DataSource result = null;
		// 第二优先:sql中指定的数据源<sql id="xxx" datasource="xxxxDataSource">
		if (StringUtil.isNotBlank(sqlDataSourceName)) {
			result = getDataSourceBean(appContext, sqlDataSourceName);
		}
		// 第三优先:dao中autowired注入的数据源
		if (result == null) {
			result = injectDataSource;
		}
		// 第四优先:sqltoy 统一设置的默认数据源
		if (result == null) {
			result = defaultDataSource;
		}
		// 如果项目中只定义了唯一的数据源，则直接使用
		if (result == null) {
			Map<String, DataSource> dataSources = appContext.getBeansOfType(DataSource.class);
			// 只有一个dataSource,直接使用
			if (dataSources.size() == 1) {
				result = dataSources.values().iterator().next();
			} else {
				logger.warn("不能获取当前线程的数据源，友情提示:多数据源场景下可通过spring.sqltoy.defaultDataSource=xxx 配置默认数据源!");
			}
		}
		return result;
	}
}
