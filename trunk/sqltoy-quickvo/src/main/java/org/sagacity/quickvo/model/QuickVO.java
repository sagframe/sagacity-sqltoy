/**
 * 
 */
package org.sagacity.quickvo.model;

import java.io.Serializable;
import java.util.List;

/**
 * @project sagacity-quickvo
 * @description quickvo 数据模型
 * @author chenrenfei $<a href="mailto:zhongxuchen@hotmail.com">联系作者</a>$
 * @version $id:QuickVO.java,Revision:v1.0,Date:Apr 19, 2009 1:12:20 AM $
 */
public class QuickVO implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -4193572162508638354L;
	/**
	 * 表类型：table 和 view 视图
	 */
	private String type = "table";
	/**
	 * 表名
	 */
	private String tableName;

	/**
	 * 表的schema
	 */
	private String schema;

	/**
	 * 表注释
	 */
	private String tableRemark;

	/**
	 * 主键约束名称
	 */
	private String pkConstraint;

	/**
	 * 被关联的表
	 */
	private List exportTables;

	/**
	 * 字段
	 */
	private List columns;

	/**
	 * 主键
	 */
	private List pkList;

	/**
	 * abstractVO路径
	 */
	private String abstractPath;

	/**
	 * 单主键
	 */
	private String singlePk = "1";

	private String voPackage;

	private String daoPackage;

	private String daoName;

	/**
	 * 版本号，针对Dao
	 */
	private String version;

	/**
	 * 项目名称
	 */
	private String projectName;

	/**
	 * 作者
	 */
	private String author;

	/**
	 * vo的序列化id
	 */
	private String voSerialUID;

	/**
	 * vo抽象类的序列化id
	 */
	private String abstractVOSerialUID;

	/**
	 * 是否包含业务主键id
	 */
	private boolean hasBusinessId;

	/**
	 * set方法返回对象自身
	 */
	private boolean returnSelf = false;

	/**
	 * @return the singlePk
	 */
	public String getSinglePk() {
		return singlePk;
	}

	/**
	 * @param singlePk the singlePk to set
	 */
	public void setSinglePk(String singlePk) {
		this.singlePk = singlePk;
	}

	private String voSubstr;
	private String voName;

	private String pkSizeEqualNotNullSize = "0";
	private String entityName;
	private String entityPackage;

	private String dateTime;

	/**
	 * 字段全部是主键
	 */
	private String pkIsAllColumn = "0";

	/**
	 * 字段是否不全为null
	 */
	private String fullNotNull;

	/**
	 * 需要导入的类
	 */
	private List imports;

	/**
	 * 支持swaggerApi
	 */
	private boolean swaggerModel = false;

	/**
	 * @return the tableRemark
	 */
	public String getTableRemark() {
		return tableRemark;
	}

	/**
	 * @param tableRemark the tableRemark to set
	 */
	public void setTableRemark(String tableRemark) {
		this.tableRemark = tableRemark;
	}

	public List getPkList() {
		return pkList;
	}

	public void setPkList(List pkList) {
		this.pkList = pkList;
	}

	/**
	 * @return the imports
	 */
	public List getImports() {
		return imports;
	}

	/**
	 * @param imports the imports to set
	 */
	public void setImports(List imports) {
		this.imports = imports;
	}

	/**
	 * @return the dateTime
	 */
	public String getDateTime() {
		return dateTime;
	}

	/**
	 * @param dateTime the dateTime to set
	 */
	public void setDateTime(String dateTime) {
		this.dateTime = dateTime;
	}

	/**
	 * @return the tableName
	 */
	public String getTableName() {
		return tableName;
	}

	/**
	 * @param tableName the tableName to set
	 */
	public void setTableName(String tableName) {
		this.tableName = tableName;
	}

	/**
	 * @return the exportTables
	 */
	public List getExportTables() {
		return exportTables;
	}

	/**
	 * @param exportTables the exportTables to set
	 */
	public void setExportTables(List exportTables) {
		this.exportTables = exportTables;
	}

	/**
	 * @return the columns
	 */
	public List getColumns() {
		return columns;
	}

	/**
	 * @param columns the columns to set
	 */
	public void setColumns(List columns) {
		this.columns = columns;
	}

	/**
	 * @return the voPackage
	 */
	public String getVoPackage() {
		return voPackage;
	}

	/**
	 * @param voPackage the voPackage to set
	 */
	public void setVoPackage(String voPackage) {
		this.voPackage = voPackage;
	}

	/**
	 * @return the daoPackage
	 */
	public String getDaoPackage() {
		return daoPackage;
	}

	/**
	 * @param daoPackage the daoPackage to set
	 */
	public void setDaoPackage(String daoPackage) {
		this.daoPackage = daoPackage;
	}

	/**
	 * @return the daoName
	 */
	public String getDaoName() {
		return daoName;
	}

	/**
	 * @param daoName the daoName to set
	 */
	public void setDaoName(String daoName) {
		this.daoName = daoName;
	}

	/**
	 * @return the voSubstr
	 */
	public String getVoSubstr() {
		return voSubstr;
	}

	/**
	 * @param voSubstr the voSubstr to set
	 */
	public void setVoSubstr(String voSubstr) {
		this.voSubstr = voSubstr;
	}

	/**
	 * @return the voName
	 */
	public String getVoName() {
		return voName;
	}

	/**
	 * @param voName the voName to set
	 */
	public void setVoName(String voName) {
		this.voName = voName;
	}

	/**
	 * @return the entityName
	 */
	public String getEntityName() {
		return entityName;
	}

	/**
	 * @param entityName the entityName to set
	 */
	public void setEntityName(String entityName) {
		this.entityName = entityName;
	}

	/**
	 * @return the entityPackage
	 */
	public String getEntityPackage() {
		return entityPackage;
	}

	/**
	 * @param entityPackage the entityPackage to set
	 */
	public void setEntityPackage(String entityPackage) {
		this.entityPackage = entityPackage;
	}

	/**
	 * @return the fullNotNull
	 */
	public String getFullNotNull() {
		return fullNotNull;
	}

	/**
	 * @param fullNotNull the fullNotNull to set
	 */
	public void setFullNotNull(String fullNotNull) {
		this.fullNotNull = fullNotNull;
	}

	/**
	 * @return the schema
	 */
	public String getSchema() {
		return schema;
	}

	/**
	 * @param schema the schema to set
	 */
	public void setSchema(String schema) {
		this.schema = schema;
	}

	/**
	 * @return the pkSizeEqualNotNullSize
	 */
	public String getPkSizeEqualNotNullSize() {
		return pkSizeEqualNotNullSize;
	}

	/**
	 * @param pkSizeEqualNotNullSize the pkSizeEqualNotNullSize to set
	 */
	public void setPkSizeEqualNotNullSize(String pkSizeEqualNotNullSize) {
		this.pkSizeEqualNotNullSize = pkSizeEqualNotNullSize;
	}

	/**
	 * @return the pkIsAllColumn
	 */
	public String getPkIsAllColumn() {
		return pkIsAllColumn;
	}

	/**
	 * @param pkIsAllColumn the pkIsAllColumn to set
	 */
	public void setPkIsAllColumn(String pkIsAllColumn) {
		this.pkIsAllColumn = pkIsAllColumn;
	}

	/**
	 * @return the version
	 */
	public String getVersion() {
		return version;
	}

	/**
	 * @param version the version to set
	 */
	public void setVersion(String version) {
		this.version = version;
	}

	/**
	 * @return the projectName
	 */
	public String getProjectName() {
		return projectName;
	}

	/**
	 * @param projectName the projectName to set
	 */
	public void setProjectName(String projectName) {
		this.projectName = projectName;
	}

	/**
	 * @return the author
	 */
	public String getAuthor() {
		return author;
	}

	/**
	 * @param author the author to set
	 */
	public void setAuthor(String author) {
		this.author = author;
	}

	/**
	 * @return the type
	 */
	public String getType() {
		return type;
	}

	/**
	 * @param type the type to set
	 */
	public void setType(String type) {
		this.type = type;
	}

	/**
	 * @return the abstractPath
	 */
	public String getAbstractPath() {
		return abstractPath;
	}

	/**
	 * @param abstractPath the abstractPath to set
	 */
	public void setAbstractPath(String abstractPath) {
		this.abstractPath = abstractPath;
	}

	/**
	 * @return the pkConstraint
	 */
	public String getPkConstraint() {
		return pkConstraint;
	}

	/**
	 * @param pkConstraint the pkConstraint to set
	 */
	public void setPkConstraint(String pkConstraint) {
		this.pkConstraint = pkConstraint;
	}

	/**
	 * @return the voSerialUID
	 */
	public String getVoSerialUID() {
		return voSerialUID;
	}

	/**
	 * @param voSerialUID the voSerialUID to set
	 */
	public void setVoSerialUID(String voSerialUID) {
		this.voSerialUID = voSerialUID;
	}

	/**
	 * @return the abstractVOSerialUID
	 */
	public String getAbstractVOSerialUID() {
		return abstractVOSerialUID;
	}

	/**
	 * @param abstractVOSerialUID the abstractVOSerialUID to set
	 */
	public void setAbstractVOSerialUID(String abstractVOSerialUID) {
		this.abstractVOSerialUID = abstractVOSerialUID;
	}

	/**
	 * @return the hasBusinessId
	 */
	public boolean isHasBusinessId() {
		return hasBusinessId;
	}

	/**
	 * @param hasBusinessId the hasBusinessId to set
	 */
	public void setHasBusinessId(boolean hasBusinessId) {
		this.hasBusinessId = hasBusinessId;
	}

	/**
	 * @return the swaggerModel
	 */
	public boolean isSwaggerModel() {
		return swaggerModel;
	}

	/**
	 * @param swaggerModel the swaggerModel to set
	 */
	public void setSwaggerModel(boolean swaggerModel) {
		this.swaggerModel = swaggerModel;
	}

	public boolean isReturnSelf() {
		return returnSelf;
	}

	public void setReturnSelf(boolean returnSelf) {
		this.returnSelf = returnSelf;
	}

}
