package org.sagacity.sqltoy.config.model;

import java.io.Serializable;
import java.util.HashMap;

/**
 * @project sqltoy-orm
 * @description 构造一个综合数据库表XX_AA 模式字段和java对象属性剔除下划线骆驼命名法
 * @author zhongxuchen
 * @version v1.0,Date:2020-8-1
 */
public class LabelIndexModel implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 6937295390933047835L;

	private HashMap<String, Integer> labelIndexMap = new HashMap<String, Integer>();

	// 无下划线
	private HashMap<String, Integer> noUnlinelabelIndexMap = new HashMap<String, Integer>();

	public void put(String key, Integer index) {
		String realKey = key.toLowerCase();
		// 统一转小写
		labelIndexMap.put(realKey, index);
		if (realKey.contains("_") || realKey.contains("-")) {
			noUnlinelabelIndexMap.put(realKey.replaceAll("\\_|\\-", ""), index);
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
		if (labelIndexMap.containsKey(realKey)) {
			return true;
		}
		return noUnlinelabelIndexMap.containsKey(realKey);
	}
}
