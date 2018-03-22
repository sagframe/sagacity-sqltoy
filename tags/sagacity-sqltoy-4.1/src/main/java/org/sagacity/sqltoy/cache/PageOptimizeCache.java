/**
 * 
 */
package org.sagacity.sqltoy.cache;

import org.sagacity.sqltoy.config.model.SqlToyConfig;

/**
 * @project sagacity-sqltoy4.0
 * @description 定义分页查询优化器的接口,便于拓展
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:PageOptimizeCache.java,Revision:v1.0,Date:2016年11月24日
 */
public interface PageOptimizeCache {

	/**
	 * @todo 获取分页查询的总记录数
	 * @param sqlToyConfig
	 * @param pageQueryKey
	 * @return
	 */
	public Long getPageTotalCount(final SqlToyConfig sqlToyConfig, String pageQueryKey);

	/**
	 * @todo 登记单个sql不同条件查询下的分页总记录数
	 * @param sqlToyConfig
	 * @param pageQueryKey
	 * @param totalCount
	 */
	public void registPageTotalCount(final SqlToyConfig sqlToyConfig, String pageQueryKey, Long totalCount);

	/**
	 * @todo 启动,预留内部定时清理逻辑实现接口
	 */
	public void start();
}
