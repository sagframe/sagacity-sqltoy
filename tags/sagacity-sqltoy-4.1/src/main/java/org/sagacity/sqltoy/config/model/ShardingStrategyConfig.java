/**
 * 
 */
package org.sagacity.sqltoy.config.model;

import java.io.Serializable;

/**
 * @project sagacity-sqltoy4.0
 * @description 分库分表策略配置
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:ShardingStrategyConfig.java,Revision:v1.0,Date:2017年11月5日
 */
public class ShardingStrategyConfig implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -1424263873061119413L;

	/**
	 * 策略
	 */
	private String name;
	/**
	 * 字段
	 */
	private String[] fields;

	/**
	 * 字段的别名
	 */
	private String[] aliasNames;

	/**
	 * 决策类别
	 */
	private String decisionType;

	/**
	 * @return the fields
	 */
	public String[] getFields() {
		return fields;
	}

	/**
	 * @param fields
	 *            the fields to set
	 */
	public void setFields(String[] fields) {
		this.fields = fields;
	}

	/**
	 * @return the aliasNames
	 */
	public String[] getAliasNames() {
		return aliasNames;
	}

	/**
	 * @param aliasNames
	 *            the aliasNames to set
	 */
	public void setAliasNames(String[] aliasNames) {
		this.aliasNames = aliasNames;
	}

	/**
	 * @return the decisionType
	 */
	public String getDecisionType() {
		return decisionType;
	}

	/**
	 * @param decisionType
	 *            the decisionType to set
	 */
	public void setDecisionType(String decisionType) {
		this.decisionType = decisionType;
	}

	/**
	 * @return the name
	 */
	public String getName() {
		return name;
	}

	/**
	 * @param name
	 *            the name to set
	 */
	public void setName(String name) {
		this.name = name;
	}

}
