package org.sagacity.sqltoy.model;

import java.io.Serializable;
import java.util.HashMap;

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
