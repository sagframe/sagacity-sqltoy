/**
 * 
 */
package org.sagacity.sqltoy.config.model;

import java.io.Serializable;

import org.sagacity.sqltoy.model.inner.TranslateExtend;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * @project sqltoy-orm
 * @description sqltoy sql.xml中定义的翻译器参数模型
 * @author zhongxuchen
 * @version v1.0,Date:2013-4-8
 * @modify Date:2013-4-8 {填写修改说明}
 */
public class Translate implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 6616462798500953675L;

	// 构造一个内部类将属性全部定义到其中，避免Translate对象上暴露太多get方法
	private TranslateExtend extend = new TranslateExtend();

	public Translate(String cacheName) {
		extend.cache = cacheName;
	}

	/**
	 * @param column the column to set
	 */
	public Translate setColumn(String column) {
		// 转小写,便于后续过程比较
		extend.column = column.toLowerCase();
		return this;
	}

	/**
	 * @param cacheType the cacheType to set
	 */
	public Translate setCacheType(String cacheType) {
		extend.cacheType = cacheType;
		return this;
	}

	/**
	 * @param cache the cache to set
	 */
	public Translate setCache(String cache) {
		extend.cache = cache;
		return this;
	}

	/**
	 * @param index the index to set
	 */
	public Translate setIndex(int index) {
		extend.index = index;
		return this;
	}

	/**
	 * @param alias the alias to set
	 */
	public Translate setAlias(String alias) {
		extend.alias = alias;
		return this;
	}

	public Translate setKeyColumn(String keyColumn) {
		extend.keyColumn = keyColumn;
		return this;
	}

	/**
	 * @param uncached the uncached to set
	 */
	public Translate setUncached(String uncached) {
		extend.uncached = uncached;
		return this;
	}

	/**
	 * @param splitRegex the splitRegex to set
	 */
	public Translate setSplitRegex(String splitRegex) {
		extend.splitRegex = splitRegex;
		return this;
	}

	/**
	 * @param linkSign the linkSign to set
	 */
	public Translate setLinkSign(String linkSign) {
		extend.linkSign = linkSign;
		return this;
	}

	/**
	 * @param keyTemplate the keyTemplate to set
	 */
	public Translate setKeyTemplate(String keyTemplate) {
		if (StringUtil.isNotBlank(keyTemplate)) {
			// 规范模板
			extend.keyTemplate = keyTemplate.replaceFirst("(?i)\\$?\\{\\s*(key|0|cacheKey)?\\s*\\}", "{}");
		}
		return this;
	}

	/**
	 * @return the extend
	 */
	public TranslateExtend getExtend() {
		return extend;
	}

	public Translate clone() {
		Translate result = new Translate(getExtend().cache);
		result.extend = getExtend().clone();
		return result;
	}

}
