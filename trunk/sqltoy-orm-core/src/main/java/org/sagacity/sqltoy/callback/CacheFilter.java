/**
 * 
 */
package org.sagacity.sqltoy.callback;

/**
 * @project sagacity-sqltoy
 * @description 提供缓存名称映射key的过滤反调函数，用于过滤比如租户、状态信息
 * @author zhongxuchen
 * @version v1.0, Date:2022年12月19日
 * @modify 2022年12月19日,修改说明
 */
@FunctionalInterface
public interface CacheFilter {
	public boolean doFilter(Object[] cacheRow);
}
