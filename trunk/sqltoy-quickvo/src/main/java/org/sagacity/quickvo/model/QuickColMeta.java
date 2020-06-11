/**
 * 
 */
package org.sagacity.quickvo.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * @project sagacity-quickvo
 * @description quickvo 存放列信息的对象
 * @author chenrenfei <a href="mailto:zhongxuchen@hotmail.com">联系作者</a>
 * @version id:QuickColMeta.java,Revision:v1.0,Date:Apr 19, 2009 10:34:20 AM
 */
public class QuickColMeta implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 3913303659891945911L;

	/**
	 * 字段名称
	 */
	private String colName;

	/**
	 * 字段注释
	 */
	private String colRemark;

	/**
	 * 字段java名称
	 */
	private String colJavaName;

	/**
	 * 字段类型
	 */
	private String colType;

	/**
	 * jdbc中的数据类型
	 */
	private String jdbcType;

	/**
	 * jdbc中的数据类型
	 */
	private String dataType;

	/**
	 * 数据总长度
	 */
	private Integer precision;

	/**
	 * 小数位
	 */
	private Integer scale;

	/**
	 * 代码中的类型
	 */
	private String resultType;

	/**
	 * 可否位空
	 */
	private String nullable;

	/**
	 * 是否为数字类型标识
	 */
	private String colTypeFlag = "0";

	/**
	 * 外键对应的表名称
	 */
	private String fkRefTableName;

	/**
	 * 外键对应的表java名称
	 */
	private String fkRefJavaTableName;

	/**
	 * 外键对应表字段名称
	 */
	private String fkRefTableColName;

	/**
	 * 外键对应表字段java名称
	 */
	private String fkRefTableColJavaName;

	/**
	 * 是否为主键
	 */
	private String pkFlag = "0";

	/**
	 * 主键生成策略
	 */
	private String strategy = "assign";

	private String sequence;

	private String generator;

	private String autoIncrement = "false";

	private List<TableConstractModel> pkRefConstract = new ArrayList<TableConstractModel>();

	private BusinessIdConfig businessIdConfig;

	/**
	 * default值
	 */
	private String defaultValue;

	public String getPkFlag() {
		return pkFlag;
	}

	public void setPkFlag(String pkFlag) {
		this.pkFlag = pkFlag;
	}

	/**
	 * @return the colName
	 */
	public String getColName() {
		return colName;
	}

	/**
	 * @param colName the colName to set
	 */
	public void setColName(String colName) {
		this.colName = colName;
	}

	/**
	 * @return the colJavaName
	 */
	public String getColJavaName() {
		return colJavaName;
	}

	/**
	 * @param colJavaName the colJavaName to set
	 */
	public void setColJavaName(String colJavaName) {
		this.colJavaName = colJavaName;
	}

	/**
	 * @return the jdbcType
	 */
	public String getJdbcType() {
		return jdbcType;
	}

	/**
	 * @param jdbcType the jdbcType to set
	 */
	public void setJdbcType(String jdbcType) {
		this.jdbcType = jdbcType;
	}

	/**
	 * @return the precision
	 */
	public Integer getPrecision() {
		return precision;
	}

	/**
	 * @param precision the precision to set
	 */
	public void setPrecision(Integer precision) {
		this.precision = precision;
	}

	/**
	 * @return the scale
	 */
	public Integer getScale() {
		return scale;
	}

	/**
	 * @param scale the scale to set
	 */
	public void setScale(Integer scale) {
		this.scale = scale;
	}

	/**
	 * @return the resultType
	 */
	public String getResultType() {
		return resultType;
	}

	/**
	 * @param resultType the resultType to set
	 */
	public void setResultType(String resultType) {
		this.resultType = resultType;
	}

	/**
	 * @return the nullable
	 */
	public String getNullable() {
		return nullable;
	}

	/**
	 * @param nullable the nullable to set
	 */
	public void setNullable(String nullable) {
		this.nullable = nullable;
	}

	/**
	 * @return the fkRefTableName
	 */
	public String getFkRefTableName() {
		return fkRefTableName;
	}

	/**
	 * @param fkRefTableName the fkRefTableName to set
	 */
	public void setFkRefTableName(String fkRefTableName) {
		this.fkRefTableName = fkRefTableName;
	}

	/**
	 * @return the fkRefJavaTableName
	 */
	public String getFkRefJavaTableName() {
		return fkRefJavaTableName;
	}

	/**
	 * @param fkRefJavaTableName the fkRefJavaTableName to set
	 */
	public void setFkRefJavaTableName(String fkRefJavaTableName) {
		this.fkRefJavaTableName = fkRefJavaTableName;
	}

	/**
	 * @return the fkRefTableColName
	 */
	public String getFkRefTableColName() {
		return fkRefTableColName;
	}

	/**
	 * @param fkRefTableColName the fkRefTableColName to set
	 */
	public void setFkRefTableColName(String fkRefTableColName) {
		this.fkRefTableColName = fkRefTableColName;
	}

	/**
	 * @return the fkRefTableColJavaName
	 */
	public String getFkRefTableColJavaName() {
		return fkRefTableColJavaName;
	}

	/**
	 * @param fkRefTableColJavaName the fkRefTableColJavaName to set
	 */
	public void setFkRefTableColJavaName(String fkRefTableColJavaName) {
		this.fkRefTableColJavaName = fkRefTableColJavaName;
	}

	/**
	 * @return the colRemark
	 */
	public String getColRemark() {
		return colRemark;
	}

	/**
	 * @param colRemark the colRemark to set
	 */
	public void setColRemark(String colRemark) {
		this.colRemark = colRemark;
	}

	/**
	 * @return the colTypeFlag
	 */
	public String getColTypeFlag() {
		return colTypeFlag;
	}

	/**
	 * @param colTypeFlag the colTypeFlag to set
	 */
	public void setColTypeFlag(String colTypeFlag) {
		this.colTypeFlag = colTypeFlag;
	}

	/**
	 * @return the dataType
	 */
	public String getDataType() {
		return dataType;
	}

	/**
	 * @param dataType the dataType to set
	 */
	public void setDataType(String dataType) {
		this.dataType = dataType;
	}

	/**
	 * @return the strategy
	 */
	public String getStrategy() {
		return strategy;
	}

	/**
	 * @param strategy the strategy to set
	 */
	public void setStrategy(String strategy) {
		this.strategy = strategy;
	}

	/**
	 * @return the sequence
	 */
	public String getSequence() {
		return sequence;
	}

	/**
	 * @param sequence the sequence to set
	 */
	public void setSequence(String sequence) {
		this.sequence = sequence;
	}

	/**
	 * @return the generator
	 */
	public String getGenerator() {
		return generator;
	}

	/**
	 * @param generator the generator to set
	 */
	public void setGenerator(String generator) {
		this.generator = generator;
	}

	/**
	 * @return the autoIncrement
	 */
	public String getAutoIncrement() {
		return autoIncrement;
	}

	/**
	 * @param autoIncrement the autoIncrement to set
	 */
	public void setAutoIncrement(String autoIncrement) {
		this.autoIncrement = autoIncrement;
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
	 * @return the pkRefConstract
	 */
	public List<TableConstractModel> getPkRefConstract() {
		return pkRefConstract;
	}

	/**
	 * @param pkRefConstract the pkRefConstract to set
	 */
	public void setPkRefConstract(List<TableConstractModel> pkRefConstract) {
		this.pkRefConstract = pkRefConstract;
	}

	public void addPkRefConstract(TableConstractModel tableConstractModel) {
		pkRefConstract.add(tableConstractModel);
	}

	/**
	 * @return the businessIdConfig
	 */
	public BusinessIdConfig getBusinessIdConfig() {
		return businessIdConfig;
	}

	/**
	 * @param businessIdConfig the businessIdConfig to set
	 */
	public void setBusinessIdConfig(BusinessIdConfig businessIdConfig) {
		this.businessIdConfig = businessIdConfig;
	}

	public String getColType() {
		return colType;
	}

	public void setColType(String colType) {
		this.colType = colType;
	}

}
