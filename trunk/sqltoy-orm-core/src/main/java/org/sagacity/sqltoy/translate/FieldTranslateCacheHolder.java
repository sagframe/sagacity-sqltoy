package org.sagacity.sqltoy.translate;

import java.io.Serializable;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;

import org.sagacity.sqltoy.config.model.Translate;
import org.sagacity.sqltoy.model.inner.TranslateExtend;
import org.sagacity.sqltoy.utils.BeanUtil;
import org.sagacity.sqltoy.utils.TranslateUtils;

/**
 * @project sagacity-sqltoy
 * @description 提供对某个字段进行翻译的聚合缓存数据、翻译逻辑的处理器，从而实现对一个字段可以有多个翻译器的逻辑
 * @author zhongxuchen
 * @version v1.0,Date:2024-12-28
 */
public class FieldTranslateCacheHolder implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -8556392032470721886L;

	/**
	 * 一列上存在几个缓存翻译(正常情况下一个)
	 */
	private int translateSize = 1;

	/**
	 * 针对VO上@Translate注解,缓存翻译的字段依据某个key字段的值
	 */
	private String keyField;

	/**
	 * 多个缓存翻译器数组
	 */
	private Translate[] translates;

	/**
	 * 多个缓存翻译器对应的缓存数据
	 */
	private HashMap<String, Object[]>[] cacheArray;

	/**
	 * @TODO 针对一行数据进行翻译
	 * @param dynamicCacheFetch
	 * @param rowList
	 * @param colIndexMap
	 * @param key
	 * @return
	 */
	public Object getRowCacheValue(DynamicCacheFetch dynamicCacheFetch, List<?> rowList,
			HashMap<String, Integer> colIndexMap, String key) {
		TranslateExtend translateExtand;
		Object translateValue = key;
		Object compareValue;
		boolean doTranslate;
		for (int i = 0; i < translateSize; i++) {
			translateExtand = translates[i].getExtend();
			doTranslate = true;
			compareValue = null;
			// 存在逻辑判断
			if (translateExtand.hasLogic) {
				compareValue = rowList.get(colIndexMap.get(translateExtand.compareColumn));
				// 执行逻辑判断，得出是否执行缓存翻译
				doTranslate = TranslateUtils.judgeTranslate(compareValue, translateExtand.compareType,
						translateExtand.compareValues);
			}
			if (doTranslate) {
				translateValue = TranslateUtils.translateKey(translateExtand, dynamicCacheFetch, cacheArray[i],
						translateValue);
			}
		}
		return translateValue;
	}

	/**
	 * @TODO 针对ResultSet 进行翻译
	 * @param dynamicCacheFetch
	 * @param rs
	 * @param key
	 * @return
	 * @throws SQLException
	 */
	public Object getRSCacheValue(DynamicCacheFetch dynamicCacheFetch, ResultSet rs, String key) throws SQLException {
		TranslateExtend translateExtand;
		Object translateValue = key;
		Object compareValue;
		boolean doTranslate;
		for (int i = 0; i < translateSize; i++) {
			doTranslate = true;
			translateExtand = translates[i].getExtend();
			compareValue = null;
			if (translateExtand.hasLogic) {
				compareValue = rs.getObject(translateExtand.compareColumn);
				doTranslate = TranslateUtils.judgeTranslate(compareValue, translateExtand.compareType,
						translateExtand.compareValues);
			}
			if (doTranslate) {
				translateValue = TranslateUtils.translateKey(translateExtand, dynamicCacheFetch, cacheArray[i],
						translateValue);
			}
		}
		return translateValue;
	}

	/**
	 * @TODO 针对VO\DTO的属性进行翻译
	 * @param dynamicCacheFetch
	 * @param item
	 * @param key
	 * @return
	 */
	public Object getBeanCacheValue(DynamicCacheFetch dynamicCacheFetch, Object item, String key) {
		TranslateExtend translateExtand;
		Object translateValue = key;
		Object compareValue;
		boolean doTranslate;
		for (int i = 0; i < translateSize; i++) {
			doTranslate = true;
			translateExtand = translates[i].getExtend();
			compareValue = null;
			if (translateExtand.hasLogic) {
				compareValue = BeanUtil.getProperty(item, translateExtand.compareColumn);
				doTranslate = TranslateUtils.judgeTranslate(compareValue, translateExtand.compareType,
						translateExtand.compareValues);
			}
			if (doTranslate) {
				translateValue = TranslateUtils.translateKey(translateExtand, dynamicCacheFetch, cacheArray[i],
						translateValue);
			}
		}
		return translateValue;
	}

	public HashMap<String, Object[]>[] getCacheArray() {
		return cacheArray;
	}

	public void setCacheArray(HashMap<String, Object[]>[] cacheArray) {
		this.translateSize = cacheArray.length;
		this.cacheArray = cacheArray;
	}

	public Translate[] getTranslates() {
		return translates;
	}

	public void setTranslates(Translate[] translates) {
		this.translates = translates;
	}

	public String getAlias() {
		return translates[0].getExtend().alias;
	}

	/**
	 * @return the keyField
	 */
	public String getKeyField() {
		return keyField;
	}

	/**
	 * @param keyField the keyField to set
	 */
	public void setKeyField(String keyField) {
		this.keyField = keyField;
	}
}
