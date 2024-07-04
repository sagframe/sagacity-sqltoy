package org.sagacity.sqltoy.model;

import java.io.Serializable;

/**
 * @project sagacity-sqltoy
 * @description 表字段信息
 * @author zhongxuchen
 * @version v1.0,Date:2021-09-25
 */
public class ColumnMeta implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 3159696936177058511L;

	/**
	 * 字段名称
	 */
	private String colName;

	/**
	 * 是否是主键
	 */
	private boolean isPK;

	/**
	 * 是否分区字段(mpp数据库)
	 */
	private boolean partitionKey;

	/**
	 * 是否唯一索引
	 */
	private boolean unique = false;

	/**
	 * 是否索引
	 */
	private boolean isIndex = false;

	/**
	 * 索引名称
	 */
	private String indexName;

	/**
	 * 字段类型
	 */
	private Integer dataType;

	/**
	 * 类型名称
	 */
	private String typeName;

	private String nativeType;

	/**
	 * 默认值
	 */
	private String defaultValue;

	/**
	 * 是否自增
	 */
	private boolean autoIncrement;

	/**
	 * 是否为null
	 */
	private boolean nullable;

	/**
	 * 字段长度
	 */
	private int columnSize;

	/**
	 * 字段注释
	 */
	private String comments;

	// DECIMAL_DIGITS
	private int decimalDigits;

	// NUM_PREC_RADIX
	private int numPrecRadix;

	public String getColName() {
		return colName;
	}

	public void setColName(String colName) {
		this.colName = colName;
	}

	public Integer getDataType() {
		return dataType;
	}

	public void setDataType(Integer dataType) {
		this.dataType = dataType;
	}

	public String getTypeName() {
		return typeName;
	}

	public void setTypeName(String typeName) {
		this.typeName = typeName;
	}

	public String getDefaultValue() {
		return defaultValue;
	}

	public void setDefaultValue(String defaultValue) {
		this.defaultValue = defaultValue;
	}

	public boolean isNullable() {
		return nullable;
	}

	public void setNullable(boolean nullable) {
		this.nullable = nullable;
	}

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
	 * @return the isPK
	 */
	public boolean isPK() {
		return isPK;
	}

	/**
	 * @param isPK the isPK to set
	 */
	public void setPK(boolean isPK) {
		this.isPK = isPK;
	}

	/**
	 * @return the columnSize
	 */
	public int getColumnSize() {
		return columnSize;
	}

	/**
	 * @param columnSize the columnSize to set
	 */
	public void setColumnSize(int columnSize) {
		this.columnSize = columnSize;
	}

	/**
	 * @return the decimalDigits
	 */
	public int getDecimalDigits() {
		return decimalDigits;
	}

	/**
	 * @param decimalDigits the decimalDigits to set
	 */
	public void setDecimalDigits(int decimalDigits) {
		this.decimalDigits = decimalDigits;
	}

	/**
	 * @return the numPrecRadix
	 */
	public int getNumPrecRadix() {
		return numPrecRadix;
	}

	/**
	 * @param numPrecRadix the numPrecRadix to set
	 */
	public void setNumPrecRadix(int numPrecRadix) {
		this.numPrecRadix = numPrecRadix;
	}

	public String getComments() {
		return comments;
	}

	public void setComments(String comments) {
		this.comments = comments;
	}

	public boolean isPartitionKey() {
		return partitionKey;
	}

	public void setPartitionKey(boolean partitionKey) {
		this.partitionKey = partitionKey;
	}

	public boolean isUnique() {
		return unique;
	}

	public void setUnique(boolean unique) {
		this.unique = unique;
	}

	public boolean isIndex() {
		return isIndex;
	}

	public void setIndex(boolean isIndex) {
		this.isIndex = isIndex;
	}

	public String getIndexName() {
		return indexName;
	}

	public void setIndexName(String indexName) {
		this.indexName = indexName;
	}

	public String getNativeType() {
		return nativeType;
	}

	public void setNativeType(String nativeType) {
		this.nativeType = nativeType;
	}

}
