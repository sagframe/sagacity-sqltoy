/**
 * 
 */
package org.sagacity.sqltoy.plugins.datasource.impl;

import java.util.Map;

import javax.sql.DataSource;

import org.sagacity.sqltoy.exception.DataAccessException;
import org.sagacity.sqltoy.integration.AppContext;
import org.sagacity.sqltoy.plugins.datasource.DataSourceSelector;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * @project sagacity-sqltoy
 * @description 针对数据源选择器提供默认实现
 * @author zhongxuchen
 * @version v1.0, Date:2021-4-15
 * @modify 2021-4-15,修改说明
 */
public class DefaultDataSourceSelector implements DataSourceSelector {

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
			int dataSourceSize = dataSources.size();
			// 只有一个dataSource,直接使用
			if (dataSourceSize == 1) {
				result = dataSources.values().iterator().next();
			} else {
				if (dataSourceSize == 0) {
					throw new DataAccessException("应用没有定义DataSource,请检查配置!");
				}
				throw new DataAccessException(
						"应用中存在多个数据源，请指定当前线程对应的dataSource,\n1、可以通过spring.sqltoy.defaultDataSource=xxxx指定默认数据源!\n"
								+ "2、如使用dynamic-dataSource类似插件,请配置primary=xxx 或@DS(\"xxxx\")\n"
								+ "3、给dao注入具体的dataSource或通过方法传入具体dataSource!");
			}
		}
		return result;
	}
}
