/**
 * 
 */
package org.sagacity.sqltoy.config.model;

import java.io.Serializable;

import org.sagacity.sqltoy.SqlToyConstants;

/**
 * @project sqltoy-orm
 * @description sqltoy sql.xml中定义的翻译器参数模型
 * @author renfei.chen <a href="mailto:zhongxuchen@hotmail.com">联系作者</a>
 * @version Revision:v1.0,Date:2013-4-8
 * @Modification Date:2013-4-8 {填写修改说明}
 */
public class SqlTranslate implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 6616462798500953675L;

	/**
	 * 字段列
	 */
	private String column;

	/**
	 * 字典类别
	 */
	private String dictType;

	/**
	 * 对应的缓存名称
	 */
	private String cache;

	/**
	 * 默认第二列为value，第一列为key
	 */
	private int index = 1;

	/**
	 * 别名(预留使用)
	 */
	private String alias;

	/**
	 * 分隔表达式
	 */
	private String splitRegex;

	/**
	 * 重新连接的字符
	 */
	private String linkSign = ",";

	/**
	 * 未被缓存的模板
	 */
	private String uncached = SqlToyConstants.UNCACHED_KEY_RESULT;

	/**
	 * @return the column
	 */
	public String getColumn() {
		return column;
	}

	/**
	 * @param column
	 *            the column to set
	 */
	public void setColumn(String column) {
		this.column = column;
	}

	/**
	 * @return the dictType
	 */
	public String getDictType() {
		return dictType;
	}

	/**
	 * @param dictType
	 *            the dictType to set
	 */
	public void setDictType(String dictType) {
		this.dictType = dictType;
	}

	/**
	 * @return the cache
	 */
	public String getCache() {
		return cache;
	}

	/**
	 * @param cache
	 *            the cache to set
	 */
	public void setCache(String cache) {
		this.cache = cache;
	}

	/**
	 * @return the index
	 */
	public int getIndex() {
		return index;
	}

	/**
	 * @param index
	 *            the index to set
	 */
	public void setIndex(int index) {
		this.index = index;
	}

	/**
	 * @return the alias
	 */
	public String getAlias() {
		return alias;
	}

	/**
	 * @param alias
	 *            the alias to set
	 */
	public void setAlias(String alias) {
		this.alias = alias;
	}

	/**
	 * @return the uncached
	 */
	public String getUncached() {
		return uncached;
	}

	/**
	 * @param uncached
	 *            the uncached to set
	 */
	public void setUncached(String uncached) {
		this.uncached = uncached;
	}

	/**
	 * @return the splitRegex
	 */
	public String getSplitRegex() {
		return splitRegex;
	}

	/**
	 * @param splitRegex
	 *            the splitRegex to set
	 */
	public void setSplitRegex(String splitRegex) {
		this.splitRegex = splitRegex;
	}

	/**
	 * @return the linkSign
	 */
	public String getLinkSign() {
		return linkSign;
	}

	/**
	 * @param linkSign
	 *            the linkSign to set
	 */
	public void setLinkSign(String linkSign) {
		this.linkSign = linkSign;
	}

}
