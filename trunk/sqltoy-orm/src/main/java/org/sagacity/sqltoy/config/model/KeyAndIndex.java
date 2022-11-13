/**
 * 
 */
package org.sagacity.sqltoy.config.model;

import java.io.Serializable;

/**
 * @author zhongxuchen
 *
 */
public class KeyAndIndex implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 580053163840902342L;

	private String key;

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
