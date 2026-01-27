package org.sagacity.sqltoy.translate.model;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import org.sagacity.sqltoy.config.model.Translate;

/**
 * @project sagacity-sqltoy
 * @description 用于存放一个查询中哪些字段的翻译是动态捕获数据的缓存，便于后续关闭逐行取数据，而采用批量查询方式
 * @author zhongxuchen
 * @version v1.0,Date:2026年1月23日
 */
public class BatchDynamicCache implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1117769229612668458L;

	/**
	 * 使用动态查询数据的缓存，cacheName+cacheType
	 */
	private String[] dynamicCaches;

	/**
	 * Map<field,translate>配置(正常Map<field,Translate[]>,但考虑动态查询，
	 * 只提取<field,Transalte[size()-1]最后一个是动态查询数据的缓存)
	 */
	private Map<String, Translate> translates = new HashMap<>();

	private Map<String, String> cacheAndTypeForRealMap = new HashMap<>();

	private Map<String, String> cacheAndTypeForRealType = new HashMap<>();

	public String[] getDynamicCaches() {
		return dynamicCaches;
	}

	public void setDynamicCaches(String[] dynamicCaches) {
		this.dynamicCaches = dynamicCaches;
	}

	public Map<String, Translate> getTranslates() {
		return translates;
	}

	public void setTranslates(Map<String, Translate> translates) {
		this.translates = translates;
	}

	public void setCacheAndTypeForRealMap(Map<String, String> cacheAndTypeForRealMap) {
		this.cacheAndTypeForRealMap = cacheAndTypeForRealMap;
	}

	public Map<String, String> getCacheAndTypeForRealMap() {
		return cacheAndTypeForRealMap;
	}

	public Map<String, String> getCacheAndTypeForRealType() {
		return cacheAndTypeForRealType;
	}

	public void setCacheAndTypeForRealType(Map<String, String> cacheAndTypeForRealType) {
		this.cacheAndTypeForRealType = cacheAndTypeForRealType;
	}
	
	
}
