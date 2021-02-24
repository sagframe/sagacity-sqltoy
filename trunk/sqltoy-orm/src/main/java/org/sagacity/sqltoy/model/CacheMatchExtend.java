/**
 * 
 */
package org.sagacity.sqltoy.model;

import java.io.Serializable;

/**
 * @project sagacity-sqltoy
 * @description 请在此说明类的功能
 * @author zhong
 * @version v1.0, Date:2021-2-24
 * @modify 2021-2-24,修改说明
 */
public class CacheMatchExtend implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 6232890514798490527L;

	public String cacheName;

	public String cacheType;

	public int matchSize = 1000;

	public int cacheKeyIndex = 0;

	public int[] matchIndexs = { 1 };
}
