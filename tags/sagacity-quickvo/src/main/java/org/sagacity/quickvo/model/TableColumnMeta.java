package org.sagacity.quickvo.model;

/**
 * @project sagacity-quickvo
 * @description 数据库表字段属性
 * @author chenrenfei $<a href="mailto:zhongxuchen@hotmail.com">联系作者</a>$
 * @version $id:TableColumnMeta.java,Revision:v1.0,Date:2009-2-24 下午03:27:29 $
 */
public class TableColumnMeta implements java.io.Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1011600518530456162L;

	/**
	 * 字段名称
	 */
	private String colName;

	/**
	 * 字段index
	 */
	private int colIndex;

	private int voPropertyIndex;

	/**
	 * 别名或简称
	 */
	private String aliasName;

	/**
	 * 字段类型
	 */
	private int dataType;

	/**
	 * 类别名称
	 */
	private String typeName;

	/**
	 * 是否为null
	 */
	private boolean nullable;

	/**
	 * 字段注释
	 */
	private String colRemark;

	/**
	 * 长度
	 */
	private int length = -1;

	/**
	 * 数据总长度
	 */
	private int precision;

	/**
	 * 小数位
	 */
	private int scale;

	/**
	 * 基数
	 */
	private int numPrecRadix;

	/**
	 * 是否为主键
	 */
	private boolean isPrimaryKey;

	/**
	 * 是否为自增字段
	 */
	private boolean isAutoIncrement = false;

	/**
	 * 字段默认值
	 */
	private String colDefault;
	
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

	public String getColName() {
		return colName;
	}

	public void setColName(String colName) {
		this.colName = colName;
	}

	public int getDataType() {
		return dataType;
	}

	public void setDataType(int dataType) {
		this.dataType = dataType;
	}

	public String getTypeName() {
		return typeName;
	}

	public void setTypeName(String typeName) {
		this.typeName = typeName;
	}

	public boolean isNullable() {
		return nullable;
	}

	public void setNullable(boolean nullable) {
		this.nullable = nullable;
	}

	public String getColRemark() {
		return colRemark;
	}

	public void setColRemark(String colRemark) {
		this.colRemark = colRemark;
	}

	/**
	 * @return the precision
	 */
	public int getPrecision() {
		return precision;
	}

	/**
	 * @param precision
	 *            the precision to set
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
	 * @param scale
	 *            the scale to set
	 */
	public void setScale(int scale) {
		this.scale = scale;
	}

	public String getColDefault() {
		return colDefault;
	}

	public void setColDefault(String colDefault) {
		this.colDefault = colDefault;
	}

	public boolean getIsPrimaryKey() {
		return isPrimaryKey;
	}

	public void setIsPrimaryKey(boolean isPrimaryKey) {
		this.isPrimaryKey = isPrimaryKey;
	}

	public String getAliasName() {
		return aliasName;
	}

	public void setAliasName(String aliasName) {
		this.aliasName = aliasName;
	}

	public int getColIndex() {
		return colIndex;
	}

	public void setColIndex(int colIndex) {
		this.colIndex = colIndex;
	}

	/**
	 * @return the numPrecRadix
	 */
	public int getNumPrecRadix() {
		return numPrecRadix;
	}

	/**
	 * @param numPrecRadix
	 *            the numPrecRadix to set
	 */
	public void setNumPrecRadix(int numPrecRadix) {
		this.numPrecRadix = numPrecRadix;
	}

	/**
	 * @return the voPropertyIndex
	 */
	public int getVoPropertyIndex() {
		return voPropertyIndex;
	}

	/**
	 * @param voPropertyIndex
	 *            the voPropertyIndex to set
	 */
	public void setVoPropertyIndex(int voPropertyIndex) {
		this.voPropertyIndex = voPropertyIndex;
	}

	/**
	 * @return the isAutoIncrement
	 */
	public boolean isAutoIncrement() {
		return isAutoIncrement;
	}

	/**
	 * @param isAutoIncrement
	 *            the isAutoIncrement to set
	 */
	public void setAutoIncrement(boolean isAutoIncrement) {
		this.isAutoIncrement = isAutoIncrement;
	}

}