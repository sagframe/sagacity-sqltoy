package org.sagacity.sqltoy.config.model;

import java.io.Serializable;

import org.sagacity.sqltoy.SqlToyConstants;

/**
 * @project sagacity-sqltoy
 * @description 数据库表字段的描述信息
 * @author zhongxuchen
 * @version v1.0,Date:2012-06-01
 */
public class FieldMeta implements Serializable {
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
	public FieldMeta(String filedName, String columnName, String defaultValue, String nativeType, int type,
			boolean nullable, boolean keyword, int length, int precision, int scale) {
		super();
		this.fieldName = filedName;
		this.columnName = columnName;
		this.defaultValue = defaultValue;
		this.nativeType = nativeType;
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

	// 计算列类型
	private int generatedType = 0;

	/**
	 * 字段java类型(已经转小写)
	 */
	private String fieldType;

	/**
	 * update 2026-11-21 数组/集合字段的元素组件类型全限定名(定型数组取组件类,如java.lang.String;
	 * List/Set取泛型实参,泛型实参非具体Class时为null),供h2等prepare期急切定型的方言 生成cast(? as xx ARRAY)
	 */
	private String arrayComponentType;

	/**
	 * update 2026-11-21 nativeType解析期归一的特殊类型逻辑族(NativeTypeUtils.specialFamily产物:
	 * json/jsonb/bson/vector/geometry/array,null=非特殊族保持原样精确控制)。quickvo按源库生成
	 * nativeType(源方言物理类型名),消费点(merge cast/DDL)按本族翻译为目标方言形态
	 */
	private String specialFamily;

	/**
	 * 数据库对应的字段类型
	 */
	private String nativeType;

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
	 * 是否是分区字段
	 */
	private boolean partitionKey = false;

	/**
	 * 备注
	 */
	private String comments;

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
	 * 仅用于创建ddl
	 */
	private boolean ddlPk = false;

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
	 * 获取属于关键词字段的名称
	 * 
	 * @return
	 */
	public String getColumnOptName() {
		if (keyword) {
			String sign = SqlToyConstants.keywordSign;
			return sign.concat(this.columnName).concat(sign);
		}
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

	// EntityManager解析时已经转小写
	public String getFieldType() {
		return fieldType;
	}

	public void setFieldType(String fieldType) {
		this.fieldType = fieldType;
	}

	/**
	 * @return 数组/集合字段的元素组件类型全限定名,非数组/集合字段或无法确定元素类型时null
	 */
	public String getArrayComponentType() {
		return arrayComponentType;
	}

	public void setArrayComponentType(String arrayComponentType) {
		this.arrayComponentType = arrayComponentType;
	}

	/**
	 * @return nativeType归一的特殊类型逻辑族(json/jsonb/bson/vector/geometry/array),非特殊族null
	 */
	public String getSpecialFamily() {
		return specialFamily;
	}

	public void setSpecialFamily(String specialFamily) {
		this.specialFamily = specialFamily;
	}

	/**
	 * @return the partitionKey
	 */
	public boolean isPartitionKey() {
		return partitionKey;
	}

	/**
	 * @param partitionKey the partitionKey to set
	 */
	public void setPartitionKey(boolean partitionKey) {
		this.partitionKey = partitionKey;
	}

	public String getNativeType() {
		return nativeType;
	}

	public void setNativeType(String nativeType) {
		this.nativeType = nativeType;
	}

	public String getComments() {
		return comments;
	}

	public void setComments(String comments) {
		this.comments = comments;
	}

	public boolean isDdlPk() {
		return ddlPk;
	}

	public void setDdlPk(boolean ddlPk) {
		this.ddlPk = ddlPk;
	}

	public int getGeneratedType() {
		return generatedType;
	}

	public void setGeneratedType(int generatedType) {
		this.generatedType = generatedType;
	}
}
