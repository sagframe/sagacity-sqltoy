package org.sagacity.sqltoy.model;

import java.io.Serializable;
import java.util.HashMap;

/**
 * 构造一个综合数据库表XX_AA 模式字段和java对象属性剔除下划线骆驼命名法
 * 
 * @author zhong
 *
 */
public class LabelIndexModel implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 6937295390933047835L;

	private HashMap<String, Integer> labelIndexMap = new HashMap<String, Integer>();

	private HashMap<String, Integer> noUnlinelabelIndexMap = new HashMap<String, Integer>();

	public void put(String key, Integer index) {
		labelIndexMap.put(key.toLowerCase(), index);
		if (key.contains("_") || key.contains("-")) {
			labelIndexMap.put(key.toLowerCase().replaceAll("\\_|\\-", ""), index);
		}
	}

	public Integer get(String key) {
		String realKey = key.toLowerCase();
		Integer result = labelIndexMap.get(realKey);
		if (result == null) {
			result = noUnlinelabelIndexMap.get(realKey);
		}
		return result;
	}

	public boolean containsKey(String key) {
		String realKey = key.toLowerCase();
		boolean has = labelIndexMap.containsKey(realKey);
		if (has) {
			return true;
		}
		return noUnlinelabelIndexMap.containsKey(realKey);
	}
}
