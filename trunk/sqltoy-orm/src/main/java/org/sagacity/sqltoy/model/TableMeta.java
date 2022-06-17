package org.sagacity.sqltoy.model;

import java.io.Serializable;

/**
 * @project sagacity-sqltoy
 * @description 数据库表元信息
 * @author zhongxuchen
 * @version v1.0,Date:2021-09-25
 */
public class TableMeta implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 4301565874545099339L;

	/**
	 * 名称
	 */
	private String tableName;

	/**
	 * 表所属schema
	 */
	private String schema;

	/**
	 * table or view
	 */
	private String type;

	/**
	 * 备注
	 */
	private String remarks;

	public String getTableName() {
		return tableName;
	}

	public void setTableName(String tableName) {
		this.tableName = tableName;
	}

	public String getSchema() {
		return schema;
	}

	public void setSchema(String schema) {
		this.schema = schema;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getRemarks() {
		return remarks;
	}

	public void setRemarks(String remarks) {
		this.remarks = remarks;
	}
}
