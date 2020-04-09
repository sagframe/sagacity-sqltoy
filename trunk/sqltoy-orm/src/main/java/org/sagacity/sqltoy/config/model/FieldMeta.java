/**
 * 
 */
package org.sagacity.sqltoy.config.model;

import java.io.Serializable;

import org.sagacity.sqltoy.SqlToyConstants;

/**
 * @project sqltoy-orm
 * @description 数据库表字段的描述信息
 * @author renfei.chen <a href="mailto:zhongxuchen@hotmail.com">联系作者</a>
 * @version id:FieldMeta.java,Revision:v1.0,Date:2012-6-1 下午5:09:48
 */
public class FieldMeta implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = -6717053365757314662L;

	public FieldMeta() {

	}

	/**
	 * @param filedName
	 * @param columnName
	 * @param defaultValue
	 * @param type
	 * @param nullable
	 * @param keyword
	 * @param length
	 * @param precision
	 * @param scale
	 */
	public FieldMeta(String filedName, String columnName, String defaultValue, int type, boolean nullable,
			boolean keyword, int length, int precision, int scale) {
		super();
		this.fieldName = filedName;
		this.columnName = columnName;
		this.defaultValue = defaultValue;
		this.type = type;
		this.nullable = nullable;
		this.keyword = keyword;
		this.length = length;
		this.precision = precision;
		this.scale = scale;
	}

	// entity fieldName
	private String fieldName;

	// 数据库表字段名称
	private String columnName;

	// 对应数据库中的类型:java.sql.Types.xxx
	private int type;

	/**
	 * 字段java类型
	 */
	private String fieldType;

	// 是否为空
	private boolean nullable;

	/**
	 * 字段是否是关键词
	 */
	private boolean keyword = false;

	// 长度
	private int length;

	// 数字类型的总长度
	private int precision;

	// 小数位长度
	private int scale;

	/**
	 * 默认值
	 */
	private String defaultValue;

	/**
	 * 自增
	 */
	private boolean autoIncrement = false;

	/**
	 * @return the autoIncrement
	 */
	public boolean isAutoIncrement() {
		return autoIncrement;
	}

	/**
	 * @param autoIncrement the autoIncrement to set
	 */
	public void setAutoIncrement(boolean autoIncrement) {
		this.autoIncrement = autoIncrement;
	}

	/**
	 * 是否主键
	 */
	private boolean isPK;

	/**
	 * @return the type
	 */
	public int getType() {
		return type;
	}

	/**
	 * @param type the type to set
	 */
	public void setType(int type) {
		this.type = type;
	}

	/**
	 * @return the fieldName
	 */
	public String getFieldName() {
		return fieldName;
	}

	/**
	 * @param fieldName the fieldName to set
	 */
	public void setFieldName(String fieldName) {
		this.fieldName = fieldName;
	}

	/**
	 * @return the columnName
	 */
	public String getColumnName() {
		return columnName;
	}

	/**
	 * @param columnName the columnName to set
	 */
	public void setColumnName(String columnName) {
		this.columnName = columnName;
	}

	/**
	 * @todo 获取属于关键词字段的名称
	 * @return
	 */
	public String getColumnOptName() {
		if (keyword) {
			String sign = SqlToyConstants.keywordSign;
			return sign.concat(this.columnName).concat(sign);
		} else
			return columnName;
	}

	/**
	 * @return the nullable
	 */
	public boolean isNullable() {
		return nullable;
	}

	/**
	 * @param nullable the nullable to set
	 */
	public void setNullable(boolean nullable) {
		this.nullable = nullable;
	}

	/**
	 * @return the length
	 */
	public int getLength() {
		return length;
	}

	/**
	 * @param length the length to set
	 */
	public void setLength(int length) {
		this.length = length;
	}

	/**
	 * @return the precision
	 */
	public int getPrecision() {
		return precision;
	}

	/**
	 * @param precision the precision to set
	 */
	public void setPrecision(int precision) {
		this.precision = precision;
	}

	/**
	 * @return the scale
	 */
	public int getScale() {
		return scale;
	}

	/**
	 * @param scale the scale to set
	 */
	public void setScale(int scale) {
		this.scale = scale;
	}

	public boolean isPK() {
		return isPK;
	}

	public void setPK(boolean isPK) {
		this.isPK = isPK;
	}

	/**
	 * @return the defaultValue
	 */
	public String getDefaultValue() {
		return defaultValue;
	}

	/**
	 * @param defaultValue the defaultValue to set
	 */
	public void setDefaultValue(String defaultValue) {
		this.defaultValue = defaultValue;
	}

	/**
	 * @return the keyword
	 */
	public boolean isKeyword() {
		return keyword;
	}

	/**
	 * @param keyword the keyword to set
	 */
	public void setKeyword(boolean keyword) {
		this.keyword = keyword;
	}

	public String getFieldType() {
		return fieldType;
	}

	public void setFieldType(String fieldType) {
		this.fieldType = fieldType;
	}

}
