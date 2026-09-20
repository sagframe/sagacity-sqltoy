package org.sagacity.sqltoy.model;

import java.io.Serializable;
import java.util.List;

import org.sagacity.sqltoy.config.model.ForeignModel;
import org.sagacity.sqltoy.config.model.IndexModel;
import org.sagacity.sqltoy.config.model.MppTableMeta;
import org.sagacity.sqltoy.config.model.PartitionMeta;

/**
 * @project sagacity-sqltoy
 * @description 数据库表元信息
 * @author zhongxuchen
 * @version v1.0,Date:2021-09-25
 */
public class TableMeta implements Serializable {
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

	/**
	 * 表字段信息
	 */
	private List<ColumnMeta> columns;

	/**
	 * 索引信息
	 */
	private List<IndexModel> indexes;

	/**
	 * 外键信息
	 */
	private List<ForeignModel> foreigns;

	/**
	 * 主键约束名称
	 */
	private String pkConstraint;

	/**
	 * 表分区元数据(分区策略和分区键)
	 */
	private PartitionMeta partitionMeta;

	/**
	 * MPP分析库表引擎元数据
	 */
	private MppTableMeta mppTableMeta;

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

	public List<ColumnMeta> getColumns() {
		return columns;
	}

	public void setColumns(List<ColumnMeta> columns) {
		this.columns = columns;
	}

	/**
	 * @return the indexes
	 */
	public List<IndexModel> getIndexes() {
		return indexes;
	}

	/**
	 * @param indexes the indexes to set
	 */
	public void setIndexes(List<IndexModel> indexes) {
		this.indexes = indexes;
	}

	public List<ForeignModel> getForeigns() {
		return foreigns;
	}

	public void setForeigns(List<ForeignModel> foreigns) {
		this.foreigns = foreigns;
	}

	public String getPkConstraint() {
		return pkConstraint;
	}

	public void setPkConstraint(String pkConstraint) {
		this.pkConstraint = pkConstraint;
	}

	public PartitionMeta getPartitionMeta() {
		return partitionMeta;
	}

	public void setPartitionMeta(PartitionMeta partitionMeta) {
		this.partitionMeta = partitionMeta;
	}
	public MppTableMeta getMppTableMeta() {
		return mppTableMeta;
	}

	public void setMppTableMeta(MppTableMeta mppTableMeta) {
		this.mppTableMeta = mppTableMeta;
	}
}
