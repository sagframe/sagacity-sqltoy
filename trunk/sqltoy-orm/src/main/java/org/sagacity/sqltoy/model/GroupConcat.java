package org.sagacity.sqltoy.model;

import java.io.Serializable;

public class GroupConcat implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 6903223049853812595L;

	/**
	 * 分组列
	 */
	private String[] group;

	/**
	 * 拼接列
	 */
	private String[] concat;

	/**
	 * 分割符号(默认逗号)
	 */
	private String separator = ",";

	/**
	 * 是否去除重复
	 */
	private boolean distinct = false;

	public GroupConcat group(String... group) {
		this.group = group;
		return this;
	}

	public GroupConcat concat(String... fields) {
		this.concat = fields;
		return this;
	}

	public GroupConcat separator(String separator) {
		this.separator = separator;
		return this;
	}

	public GroupConcat distinct(boolean distinct) {
		this.distinct = distinct;
		return this;
	}

	public String[] getGroup() {
		return group;
	}

	public String[] getConcat() {
		return concat;
	}

	public String getSeparator() {
		return separator;
	}

	public boolean isDistinct() {
		return distinct;
	}

}
