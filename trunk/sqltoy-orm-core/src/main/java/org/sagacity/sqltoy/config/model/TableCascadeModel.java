/**
 * 
 */
package org.sagacity.sqltoy.config.model;

import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;

/**
 * @project sqltoy-orm
 * @description OneToMany数据库主子表级联关系配置
 * @author zhongxuchen
 * @version v1.0,Date:2012-9-5
 */
@SuppressWarnings("rawtypes")
public class TableCascadeModel implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1309393602795660950L;

	/**
	 * 1:oneToMany;2:oneToOne
	 */
	private int cascadeType = 1;

	/**
	 * 对应vo中的属性
	 */
	private Field field;

	/**
	 * 对应vo中的List集合属性
	 */
	private String property;

	/**
	 * 对应vo属性中的对应的注解
	 */
	private Annotation[] annotations;

	/**
	 * 主键关联的表
	 */
	private String mappedTable;

	/**
	 * 主表字段
	 */
	private String[] fields;

	/**
	 * 主键关联表对应的外键字段
	 */
	private String[] mappedColumns;

	/**
	 * 主键关联子表对象的属性(entityManager自动处理按主表主键顺序排列，即跟entityModel中的getIdArray顺序是一致的)
	 */
	private String[] mappedFields;

	/**
	 * 非null字段
	 */
	private String notNullField;

	/**
	 * 是否级联删除
	 */
	private boolean delete = false;

	/**
	 * 对应子表对象的类型
	 */
	private Class mappedType;

	/**
	 * 查询子表记录sql
	 */
	private String loadSubTableSql;

	/**
	 * 删除子表sql
	 */
	private String deleteSubTableSql;

	/**
	 * 级联修改子表的sql
	 */
	private String cascadeUpdateSql;

	//update 2025-5-16 提供基于in 批量查询、修改、删除的语句
	private String loadBatchSubTableSql;

	private String deleteBatchSubTableSql;

	private String cascadeBatchUpdateSql;

	/**
	 * 排序
	 */
	private String orderBy;

	/**
	 * load级联加载的扩展条件
	 */
	private String loadExtCondition;

	public Field getField() {
		return field;
	}

	public void setField(Field field) {
		this.field = field;
	}

	/**
	 * @return the property
	 */
	public String getProperty() {
		return property;
	}

	/**
	 * @param property the property to set
	 */
	public void setProperty(String property) {
		this.property = property;
	}

	public Annotation[] getAnnotations() {
		return annotations;
	}

	public void setAnnotations(Annotation[] annotations) {
		this.annotations = annotations;
	}

	/**
	 * @return the mappedTable
	 */
	public String getMappedTable() {
		return mappedTable;
	}

	/**
	 * @param mappedTable the mappedTable to set
	 */
	public void setMappedTable(String mappedTable) {
		this.mappedTable = mappedTable;
	}

	/**
	 * @return the mappedColumn
	 */
	public String[] getMappedColumns() {
		return mappedColumns;
	}

	/**
	 * @param mappedColumns the mappedColumn to set
	 */
	public void setMappedColumns(String[] mappedColumns) {
		this.mappedColumns = mappedColumns;
	}

	/**
	 * @return the mappedType
	 */
	public Class getMappedType() {
		return mappedType;
	}

	/**
	 * @param mappedType the mappedType to set
	 */
	public void setMappedType(Class mappedType) {
		this.mappedType = mappedType;
	}

	/**
	 * @return the loadSubTableSql
	 */
	public String getLoadSubTableSql() {
		return loadSubTableSql;
	}

	/**
	 * @param loadSubTableSql the loadSubTableSql to set
	 */
	public void setLoadSubTableSql(String loadSubTableSql) {
		this.loadSubTableSql = loadSubTableSql;
	}

	/**
	 * @return the deleteSubTableSql
	 */
	public String getDeleteSubTableSql() {
		return deleteSubTableSql;
	}

	/**
	 * @param deleteSubTableSql the deleteSubTableSql to set
	 */
	public void setDeleteSubTableSql(String deleteSubTableSql) {
		this.deleteSubTableSql = deleteSubTableSql;
	}

	/**
	 * @return the cascadeUpdateSql
	 */
	public String getCascadeUpdateSql() {
		return cascadeUpdateSql;
	}

	/**
	 * @param cascadeUpdateSql the cascadeUpdateSql to set
	 */
	public void setCascadeUpdateSql(String cascadeUpdateSql) {
		this.cascadeUpdateSql = cascadeUpdateSql;
	}

	/**
	 * @return the mappedFields
	 */
	public String[] getMappedFields() {
		return mappedFields;
	}

	/**
	 * @param mappedFields the mappedFields to set
	 */
	public void setMappedFields(String[] mappedFields) {
		this.mappedFields = mappedFields;
	}

	/**
	 * @return the delete
	 */
	public boolean isDelete() {
		return delete;
	}

	/**
	 * @param delete the delete to set
	 */
	public void setDelete(boolean delete) {
		this.delete = delete;
	}

	/**
	 * @return the cascadeType
	 */
	public int getCascadeType() {
		return cascadeType;
	}

	/**
	 * @param cascadeType the cascadeType to set
	 */
	public void setCascadeType(int cascadeType) {
		this.cascadeType = cascadeType;
	}

	/**
	 * @return the fields
	 */
	public String[] getFields() {
		return fields;
	}

	/**
	 * @param fields the fields to set
	 */
	public void setFields(String[] fields) {
		this.fields = fields;
	}

	/**
	 * @return the orderBy
	 */
	public String getOrderBy() {
		return orderBy;
	}

	/**
	 * @param orderBy the orderBy to set
	 */
	public void setOrderBy(String orderBy) {
		this.orderBy = orderBy;
	}

	/**
	 * @return the loadExtCondition
	 */
	public String getLoadExtCondition() {
		return loadExtCondition;
	}

	/**
	 * @param loadExtCondition the loadExtCondition to set
	 */
	public void setLoadExtCondition(String loadExtCondition) {
		this.loadExtCondition = loadExtCondition;
	}

	public String getNotNullField() {
		return notNullField;
	}

	public void setNotNullField(String notNullField) {
		this.notNullField = notNullField;
	}

	public String getLoadBatchSubTableSql() {
		return loadBatchSubTableSql;
	}

	public void setLoadBatchSubTableSql(String loadBatchSubTableSql) {
		this.loadBatchSubTableSql = loadBatchSubTableSql;
	}

	public String getDeleteBatchSubTableSql() {
		return deleteBatchSubTableSql;
	}

	public void setDeleteBatchSubTableSql(String deleteBatchSubTableSql) {
		this.deleteBatchSubTableSql = deleteBatchSubTableSql;
	}

	public String getCascadeBatchUpdateSql() {
		return cascadeBatchUpdateSql;
	}

	public void setCascadeBatchUpdateSql(String cascadeBatchUpdateSql) {
		this.cascadeBatchUpdateSql = cascadeBatchUpdateSql;
	}

}
