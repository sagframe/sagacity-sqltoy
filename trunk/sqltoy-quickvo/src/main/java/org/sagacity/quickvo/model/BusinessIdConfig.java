/**
 * 
 */
package org.sagacity.quickvo.model;

import java.io.Serializable;

/**
 * @project sagacity-quickvo
 * @description 业务主键配置
 * @author zhongxuchen <a href="mailto:zhongxuchen@hotmail.com">联系作者</a>
 * @version id:BusinessIdConfig.java,Revision:v1.0,Date:2018年1月20日
 */
public class BusinessIdConfig implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = -6506041299589819980L;

	/**
	 * 表名
	 */
	private String tableName;

	/**
	 * 字段名称
	 */
	private String column;

	/**
	 * 识别符
	 */
	private String signature;

	/**
	 * 长度
	 */
	private int length;

	/**
	 * 流水长度
	 */
	private int sequenceSize = -1;

	/**
	 * 关联字段
	 */
	private String[] relatedColumns;

	/**
	 * 主键生成策略
	 */
	private String generator;

	/**
	 * @return the tableName
	 */
	public String getTableName() {
		return tableName;
	}

	/**
	 * @param tableName
	 *            the tableName to set
	 */
	public void setTableName(String tableName) {
		this.tableName = tableName;
	}

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
	 * @return the signature
	 */
	public String getSignature() {
		return signature;
	}

	/**
	 * @param signature
	 *            the signature to set
	 */
	public void setSignature(String signature) {
		this.signature = signature;
	}

	/**
	 * @return the length
	 */
	public int getLength() {
		return length;
	}

	/**
	 * @param length
	 *            the length to set
	 */
	public void setLength(int length) {
		this.length = length;
	}

	/**
	 * @return the relatedColumn
	 */
	public String[] getRelatedColumns() {
		return relatedColumns;
	}

	/**
	 * @param relatedColumn
	 *            the relatedColumn to set
	 */
	public void setRelatedColumns(String[] relatedColumns) {
		this.relatedColumns = relatedColumns;
	}

	/**
	 * @return the generator
	 */
	public String getGenerator() {
		return generator;
	}

	/**
	 * @param generator
	 *            the generator to set
	 */
	public void setGenerator(String generator) {
		this.generator = generator;
	}

	/**
	 * @return the sequenceSize
	 */
	public int getSequenceSize() {
		return sequenceSize;
	}

	/**
	 * @param sequenceSize the sequenceSize to set
	 */
	public void setSequenceSize(int sequenceSize) {
		this.sequenceSize = sequenceSize;
	}

}
