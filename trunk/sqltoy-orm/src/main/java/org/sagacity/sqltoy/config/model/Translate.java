/**
 * 
 */
package org.sagacity.sqltoy.config.model;

import java.io.Serializable;

import org.sagacity.sqltoy.model.TranslateExtend;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * @project sqltoy-orm
 * @description sqltoy sql.xml中定义的翻译器参数模型
 * @author renfei.chen <a href="mailto:zhongxuchen@hotmail.com">联系作者</a>
 * @version Revision:v1.0,Date:2013-4-8
 * @modify Date:2013-4-8 {填写修改说明}
 */
public class Translate implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 6616462798500953675L;

	private TranslateExtend extend = new TranslateExtend();

	public Translate(String cacheName) {
		extend.cache = cacheName;
	}
//
//	/**
//	 * 字段列
//	 */
//	private String column;
//
//	/**
//	 * 缓存类型(一般为字典类别)
//	 */
//	private String cacheType;
//
//	/**
//	 * 对应的缓存名称
//	 */
//	private String cache;
//
//	/**
//	 * 默认第二列为value，第一列为key
//	 */
//	private int index = 1;
//
//	/**
//	 * 用于entityQuery场景下指定具体作为key的列
//	 */
//	private String keyColumn;
//
//	/**
//	 * 别名(预留使用)
//	 */
//	private String alias;
//
//	/**
//	 * 分隔表达式
//	 */
//	private String splitRegex;
//
//	/**
//	 * 重新连接的字符
//	 */
//	private String linkSign = ",";
//
//	/**
//	 * ${key}_ZH_CN 用于组合匹配缓存
//	 */
//	private String keyTemplate = null;
//
//	/**
//	 * 未被缓存的模板
//	 */
//	private String uncached = SqlToyConstants.UNCACHED_KEY_RESULT;

	/**
	 * @return the column
	 */
//	public String getColumn() {
//		return column;
//	}

	/**
	 * @param column the column to set
	 */
	public Translate setColumn(String column) {
		// 转小写,便于后续过程比较
		extend.column = column.toLowerCase();
		return this;
	}

	/**
	 * @return the cacheType
	 */
//	public String getCacheType() {
//		return cacheType;
//	}

	/**
	 * @param cacheType the cacheType to set
	 */
	public Translate setCacheType(String cacheType) {
		extend.cacheType = cacheType;
		return this;
	}

	/**
	 * @return the cache
	 */
//	public String getCache() {
//		return cache;
//	}

	/**
	 * @param cache the cache to set
	 */
	public Translate setCache(String cache) {
		extend.cache = cache;
		return this;
	}

	/**
	 * @return the index
	 */
//	public int getIndex() {
//		return index;
//	}

	/**
	 * @param index the index to set
	 */
	public Translate setIndex(int index) {
		extend.index = index;
		return this;
	}

	/**
	 * @return the alias
	 */
//	public String getAlias() {
//		return alias;
//	}

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

//	public String getKeyColumn() {
//		return keyColumn;
//	}

	/**
	 * @return the uncached
	 */
//	public String getUncached() {
//		return uncached;
//	}

	/**
	 * @param uncached the uncached to set
	 */
	public Translate setUncached(String uncached) {
		extend.uncached = uncached;
		return this;
	}

	/**
	 * @return the splitRegex
	 */
//	public String getSplitRegex() {
//		return splitRegex;
//	}

	/**
	 * @param splitRegex the splitRegex to set
	 */
	public Translate setSplitRegex(String splitRegex) {
		extend.splitRegex = splitRegex;
		return this;
	}

	/**
	 * @return the linkSign
	 */
//	public String getLinkSign() {
//		return linkSign;
//	}

	/**
	 * @param linkSign the linkSign to set
	 */
	public Translate setLinkSign(String linkSign) {
		extend.linkSign = linkSign;
		return this;
	}

	/**
	 * @return the keyTemplate
	 */
//	public String getKeyTemplate() {
//		return keyTemplate;
//	}

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
}
