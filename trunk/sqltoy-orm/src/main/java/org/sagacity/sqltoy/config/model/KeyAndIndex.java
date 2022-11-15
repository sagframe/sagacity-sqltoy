/**
 * 
 */
package org.sagacity.sqltoy.config.model;

import java.io.Serializable;

/**
 * @project sagacity-sqltoy
 * @description 解析item[index]数组属性模型，获取item属性和index值，用于支持sql中参数平铺引用数组的值
 * @author zhongxuchen
 * @version v1.0, Date:2022年11月15日
 * @modify 2022年11月15日,修改说明
 */
public class KeyAndIndex implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 580053163840902342L;

	/**
	 * 属性名称
	 */
	private String key;

	/**
	 * 数组的index值
	 */
	private int index;

	public String getKey() {
		return key;
	}

	public void setKey(String key) {
		this.key = key;
	}

	public int getIndex() {
		return index;
	}

	public void setIndex(int index) {
		this.index = index;
	}

}
