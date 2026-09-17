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
 * @version v1.0,Date:2021-04-15
 * @modify Date:2021-04-15 修改说明
 */
public class DefaultDataSourceSelector implements DataSourceSelector {

	private static final Logger logger = LoggerFactory.getLogger(DefaultDataSourceSelector.class);

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
		if (appContext != null && result == null) {
			Map<String, DataSource> dataSources = appContext.getBeansOfType(DataSource.class);
			// 只有一个dataSource,直接使用
			if (dataSources.size() == 1) {
				result = dataSources.values().iterator().next();
			} else if (dataSources.size() > 1) {
				try {
					// 获取@Primary DataSource
					result = appContext.getBean(DataSource.class);
				} catch (Exception e) {
					// update 2026-9-14 补debug日志:多数据源且无@Primary时容器抛NoUniqueBeanDefinitionException,
					// 此处的吞掉是有意回退(交由调用方要求sql上显式指定datasource或注入数据源),但原空catch
					// 让该场景没有任何线索,最终"未获取到数据源"的报错无法判断是缺少@Primary还是配置缺失
					logger.debug(
							"multiple DataSource beans:{} found and none is @Primary, cannot determine the default dataSource, please specify datasource for the sql or inject one!",
							dataSources.keySet(), e);
				}
			}
		}
		return result;
	}
}
