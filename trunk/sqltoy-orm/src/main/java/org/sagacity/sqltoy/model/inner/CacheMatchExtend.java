/**
 * 
 */
package org.sagacity.sqltoy.model.inner;

import java.io.Serializable;

/**
 * @project sagacity-sqltoy
 * @description cacheMatchFilter的参数容器，避免cacheMatchFilter暴露过多参数
 * @author zhongxuchen
 * @version v1.0, Date:2021-2-24
 * @modify 2021-2-24,修改说明
 */
public class CacheMatchExtend implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 6232890514798490527L;

	/**
	 * 缓存名称
	 */
	public String cacheName;

	/**
	 * 缓存中的分类，一般数据字典场景存在
	 */
	public String cacheType;

	/**
	 * 最大匹配数量
	 */
	public int matchSize = 1000;

	/**
	 * 优先匹配相等
	 */
	public boolean priorMatchEqual = false;

	/**
	 * 缓存中key所在的列,一般为0
	 */
	public int cacheKeyIndex = 0;

	/**
	 * 缓存中用来跟名称匹配的列,默认为1
	 */
	public int[] matchIndexs = { 1 };
}
