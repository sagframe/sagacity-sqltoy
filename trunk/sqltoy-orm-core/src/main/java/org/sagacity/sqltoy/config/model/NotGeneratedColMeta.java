package org.sagacity.sqltoy.config.model;

import java.io.Serializable;

/**
 * 排除计算列后的列信息,针对增加和修改操作行为
 * 
 * @date 2026-4-30
 */
public class NotGeneratedColMeta implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1620110176932995976L;

	/**
	 * 所有字段信息(主键字段放于末尾)
	 */
	private String[] fieldsArray;

	/**
	 * 所有字段的类别
	 */
	private Integer[] fieldsTypeArray;

	/**
	 * 所有字段的默认值(排除主键，提供对象save\saveAll 场景构建默认值)
	 */
	private String[] fieldsDefaultValue;

	/**
	 * 字段是否可以为null
	 */
	private Boolean[] fieldsNullable;

	/**
	 * 排除id的字段数组
	 */
	private String[] rejectIdFieldArray;

	public String[] getFieldsArray() {
		return fieldsArray;
	}

	public void setFieldsArray(String[] fieldsArray) {
		this.fieldsArray = fieldsArray;
	}

	public Integer[] getFieldsTypeArray() {
		return fieldsTypeArray;
	}

	public void setFieldsTypeArray(Integer[] fieldsTypeArray) {
		this.fieldsTypeArray = fieldsTypeArray;
	}

	public String[] getFieldsDefaultValue() {
		return fieldsDefaultValue;
	}

	public void setFieldsDefaultValue(String[] fieldsDefaultValue) {
		this.fieldsDefaultValue = fieldsDefaultValue;
	}

	public Boolean[] getFieldsNullable() {
		return fieldsNullable;
	}

	public void setFieldsNullable(Boolean[] fieldsNullable) {
		this.fieldsNullable = fieldsNullable;
	}

	public String[] getRejectIdFieldArray() {
		return rejectIdFieldArray;
	}

	public void setRejectIdFieldArray(String[] rejectIdFieldArray) {
		this.rejectIdFieldArray = rejectIdFieldArray;
	}
}
