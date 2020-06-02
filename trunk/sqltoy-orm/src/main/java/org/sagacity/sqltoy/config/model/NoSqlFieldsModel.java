package org.sagacity.sqltoy.config.model;

import java.io.Serializable;

/**
 * NoSql 字段模型
 * 
 * @author zhongxuchen
 *
 */
public class NoSqlFieldsModel implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -7811757019736292394L;
	private String[] fields;

	private String[] aliasLabels;

	/**
	 * @return the fields
	 */
	public String[] getFields() {
		return fields;
	}

	/**
	 * @param fields the fields to set
	 */
	public void setFields(String[] fields) {
		this.fields = fields;
	}

	/**
	 * @return the aliasLabels
	 */
	public String[] getAliasLabels() {
		return aliasLabels;
	}

	/**
	 * @param aliasLabels the aliasLabels to set
	 */
	public void setAliasLabels(String[] aliasLabels) {
		this.aliasLabels = aliasLabels;
	}
}
