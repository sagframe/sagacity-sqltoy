/**
 * 
 */
package org.sagacity.sqltoy.config.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.sagacity.sqltoy.plugins.id.IdGenerator;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * @project sqltoy-orm
 * @description sqltoy entity实体对象信息
 * @author renfei.chen <a href="mailto:zhongxuchen@hotmail.com">联系作者</a>
 * @version id:EntityMeta.java,Revision:v1.0,Date:2012-6-1 下午4:28:29
 */
@SuppressWarnings({ "rawtypes" })
public class EntityMeta implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = -1723897636996281118L;

	private Class entityClass;

	/**
	 * 获取所有记录sql
	 */
	private String loadAllSql;

	/**
	 * 通过主键删除记录的语句
	 */
	private String deleteByIdsSql;

	/**
	 * 表名
	 */
	private String tableName;

	/**
	 * 主键的约束名称
	 */
	private String pkConstraint;

	/**
	 * schema.table 模式字符串
	 */
	private String schemaTable;

	/**
	 * 主键列
	 */
	private String[] idArray;

	/**
	 * 所有字段信息
	 */
	private String[] fieldsArray;

	/**
	 * 所有字段的类别
	 */
	private Integer[] fieldsTypeArray;

	/**
	 * 是否存在default值
	 */
	private boolean hasDefaultValue = false;

	/**
	 * 所有字段的默认值
	 */
	private String[] fieldsDefaultValue;

	/**
	 * 字段是否可以为null
	 */
	private Boolean[] fieldsNullable;

	/**
	 * 排除id的字段数组
	 */
	private String[] rejectIdFieldArray;

	/**
	 * 字段名称对应字段信息的hashMap, 便于通过名称获取字段信息(长度、类型、默认值等等)
	 */
	private HashMap<String, FieldMeta> fieldsMeta = new HashMap<String, FieldMeta>();

	/**
	 * 字段对应的顺序
	 */
	private HashMap<String, Integer> fieldIndexs = new HashMap<String, Integer>();

	/**
	 * 业务主键生成的标志符号
	 */
	private String bizIdSignature;

	/**
	 * 业务主键生成时依赖的几个字段
	 */
	private String[] bizIdRelatedColumns = null;

	/**
	 * 业务主键依赖的字段在整个表字段中的排列顺序(对象解析完后组织赋值)
	 */
	private Integer[] bizIdRelatedColIndex = null;

	/**
	 * 业务ID的长度
	 */
	private Integer bizIdLength;

	/**
	 * 流水部分长度
	 */
	private Integer bizIdSequenceSize = -1;

	/**
	 * id产生策略
	 */
	private PKStrategy idStrategy;

	/**
	 * 分库分表策略
	 */
	private ShardingConfig shardingConfig;

	/**
	 * 对应数据库sequence
	 */
	private String sequence;

	/**
	 * id产生的类实例,保证效率直接将实例放入单个实体信息中
	 */
	private IdGenerator idGenerator;

	/**
	 * 业务主键生成策略(单例)
	 */
	private IdGenerator businessIdGenerator;

	/**
	 * 业务id字段
	 */
	private String businessIdField;

	/**
	 * 分页查询的sql
	 */
	private String pageSql;

	/**
	 * 批量集合查询
	 */
	private String listSql;

	/**
	 * 根据主键load查询的sql
	 */
	private String loadSql;

	/**
	 * 主键名称参数条件语句(where 1=1 and id=:id)
	 */
	private String idNameWhereSql;

	/**
	 * 主键?形式参数语句
	 */
	private String idArgWhereSql;

	/**
	 * 主键被关联的子表信息
	 */
	private List<OneToManyModel> oneToManys = new ArrayList<OneToManyModel>();

	/**
	 * 级联对象
	 */
	private Class[] cascadeTypes;

	private String idJavaType;

	/**
	 * 是否存在业务id配置策略
	 */
	private boolean hasBizIdConfig = false;

	/**
	 * 业务主键同时也是数据库主键
	 */
	private boolean bizIdEqPK = false;

	/**
	 * 全部字段信息,默认值*最终存放colName1,colName2这种格式
	 */
	private String allColumnNames = "*";

	/**
	 * @return the loadAllSql
	 */
	public String getLoadAllSql() {
		return loadAllSql;
	}

	/**
	 * @param loadAllSql the loadAllSql to set
	 */
	public void setLoadAllSql(String loadAllSql) {
		this.loadAllSql = loadAllSql;
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
	 * @return the fieldsType
	 */
	public HashMap<String, FieldMeta> getFieldsMeta() {
		return fieldsMeta;
	}

	public String getIdType() {
		if (idArray == null)
			return "";
		if (idJavaType == null) {
			idJavaType = getColumnJavaType(idArray[0]);
		}
		return idJavaType;
	}

	public int getIdLength() {
		if (idArray == null)
			return -1;
		return this.getFieldMeta(idArray[0]).getLength();
	}

	/**
	 * @return the idStrategy
	 */
	public PKStrategy getIdStrategy() {
		return idStrategy;
	}

	/**
	 * @param idStrategy the idStrategy to set
	 */
	public void setIdStrategy(PKStrategy idStrategy) {
		this.idStrategy = idStrategy;
	}

	/**
	 * @return the idGenerator
	 */
	public IdGenerator getIdGenerator() {
		return idGenerator;
	}

	/**
	 * @param idGenerator the idGenerator to set
	 */
	public void setIdGenerator(IdGenerator idGenerator) {
		this.idGenerator = idGenerator;
	}

	public void addFieldMeta(FieldMeta fieldMeta) {
		fieldsMeta.put(fieldMeta.getFieldName().toLowerCase(), fieldMeta);
	}

	/**
	 * @return the idArray
	 */
	public String[] getIdArray() {
		return idArray;
	}

	/**
	 * @param idArray the idArray to set
	 */
	public void setIdArray(String[] idArray) {
		this.idArray = idArray;
	}

	/**
	 * @return the fieldArray
	 */
	public String[] getFieldsArray() {
		return fieldsArray;
	}

	/**
	 * @param fieldArray the fieldArray to set
	 */
	public void setFieldsArray(String[] fieldsArray) {
		this.fieldsArray = fieldsArray;
		for (int i = 0; i < fieldsArray.length; i++) {
			fieldIndexs.put(fieldsArray[i].replaceAll("\\_", "").toLowerCase(), i);
		}
		if (this.bizIdRelatedColumns != null) {
			this.bizIdRelatedColIndex = new Integer[bizIdRelatedColumns.length];
			for (int i = 0; i < bizIdRelatedColumns.length; i++) {
				this.bizIdRelatedColIndex[i] = fieldIndexs
						.get(bizIdRelatedColumns[i].replaceAll("\\_", "").toLowerCase());
			}
		}
	}

	/**
	 * @param bizIdRelatedColumns the bizIdRelatedColumns to set
	 */
	public void setBizIdRelatedColumns(String[] bizIdRelatedColumns) {
		this.bizIdRelatedColumns = bizIdRelatedColumns;
	}

	/**
	 * @return the bizIdRelatedColumn
	 */
	public String[] getBizIdRelatedColumns() {
		return bizIdRelatedColumns;
	}

	public int getFieldIndex(String fieldName) {
		return fieldIndexs.get(fieldName.replaceAll("\\_", "").toLowerCase());
	}

	/**
	 * @return the bizIdSignature
	 */
	public String getBizIdSignature() {
		return bizIdSignature;
	}

	/**
	 * @return the bizIdRelatedColIndexs
	 */
	public Integer[] getBizIdRelatedColIndex() {
		return bizIdRelatedColIndex;
	}

	public Integer getIdIndex() {
		return (rejectIdFieldArray == null) ? 0 : rejectIdFieldArray.length;
	}

	/**
	 * @return the rejectIdFieldArray
	 */
	public String[] getRejectIdFieldArray() {
		return rejectIdFieldArray;
	}

	/**
	 * @param rejectIdFieldArray the rejectIdFieldArray to set
	 */
	public void setRejectIdFieldArray(String[] rejectIdFieldArray) {
		this.rejectIdFieldArray = rejectIdFieldArray;
	}

	public String getSequence() {
		return sequence;
	}

	public void setSequence(String sequence) {
		this.sequence = sequence;
	}

	/**
	 * @return the deleteByIdsSql
	 */
	public String getDeleteByIdsSql(String tableName) {
		if (StringUtil.isBlank(tableName))
			return deleteByIdsSql;
		return "delete from ".concat(tableName).concat(" ").concat(idArgWhereSql);
	}

	/**
	 * @param deleteByIdsSql the deleteByIdsSql to set
	 */
	public void setDeleteByIdsSql(String deleteByIdsSql) {
		this.deleteByIdsSql = deleteByIdsSql;
	}

	public String getColumnName(String fieldName) {
		FieldMeta fieldMeta = fieldsMeta.get(fieldName.toLowerCase());
		if (fieldMeta == null)
			return null;
		return fieldMeta.getColumnName();
	}

	/**
	 * @todo 针对字段采用数据库关键词命名的字段,增加相应的符合兼容
	 * @param fieldName
	 * @return
	 */
	public String getColumnOptName(String fieldName) {
		FieldMeta fieldMeta = fieldsMeta.get(fieldName.toLowerCase());
		if (fieldMeta == null)
			return null;
		return fieldMeta.getColumnOptName();
	}

	public int getColumnJdbcType(String fieldName) {
		FieldMeta fieldMeta = fieldsMeta.get(fieldName.toLowerCase());
		if (fieldMeta == null)
			return -1;
		return fieldMeta.getType();
	}

	public String getColumnJavaType(String fieldName) {
		FieldMeta fieldMeta = fieldsMeta.get(fieldName.toLowerCase());
		if (fieldMeta == null)
			return null;
		return fieldMeta.getFieldType();
	}

	/**
	 * @return the schemaTable
	 */
	public String getSchemaTable() {
		return schemaTable;
	}

	public String getSchemaTable(String tableName) {
		if (StringUtil.isNotBlank(tableName))
			return tableName;
		return schemaTable;
	}

	/**
	 * @param schemaTable the schemaTable to set
	 */
	public void setSchemaTable(String schemaTable) {
		this.schemaTable = schemaTable;
	}

	/**
	 * @return the oneToManys
	 */
	public List<OneToManyModel> getOneToManys() {
		return oneToManys;
	}

	public void addOneToMany(OneToManyModel oneToManyModel) {
		this.oneToManys.add(oneToManyModel);
	}

	/**
	 * @return the pageSql
	 */
	public String getPageSql() {
		if (StringUtil.isBlank(this.pageSql))
			return this.listSql;
		return pageSql;
	}

	/**
	 * @param pageSql the pageSql to set
	 */
	public void setPageSql(String pageSql) {
		this.pageSql = pageSql;
	}

	/**
	 * @return the listSql
	 */
	public String getListSql() {
		return listSql;
	}

	/**
	 * @param listSql the listSql to set
	 */
	public void setListSql(String listSql) {
		this.listSql = listSql;
	}

	public String getLoadSql(String tableName) {
		if (tableName == null || tableName.equals(schemaTable))
			return loadSql;
		// 针对sharding 分表情况使用重新组织表名
		return "select ".concat(allColumnNames).concat(" from ").concat(tableName).concat(" ")
				.concat(this.idNameWhereSql);
	}

	/**
	 * @param loadSql the loadSql to set
	 */
	public void setLoadSql(String loadSql) {
		this.loadSql = loadSql;
	}

	public FieldMeta getFieldMeta(String field) {
		return fieldsMeta.get(field.toLowerCase());
	}

	/**
	 * @return the fieldsTypeArray
	 */
	public Integer[] getFieldsTypeArray() {
		return fieldsTypeArray;
	}

	/**
	 * @param fieldsTypeArray the fieldsTypeArray to set
	 */
	public void setFieldsTypeArray(Integer[] fieldsTypeArray) {
		this.fieldsTypeArray = fieldsTypeArray;
	}

	/**
	 * @return the fieldsDefaultValue
	 */
	public String[] getFieldsDefaultValue() {
		return fieldsDefaultValue;
	}

	/**
	 * @param fieldsDefaultValue the fieldsDefaultValue to set
	 */
	public void setFieldsDefaultValue(String[] fieldsDefaultValue) {
		this.fieldsDefaultValue = fieldsDefaultValue;
	}

	/**
	 * @param fieldsMeta the fieldsMeta to set
	 */
	public void setFieldsMeta(HashMap<String, FieldMeta> fieldsMeta) {
		this.fieldsMeta = fieldsMeta;
	}

	/**
	 * @return the cascadeTypes
	 */
	public Class[] getCascadeTypes() {
		return cascadeTypes;
	}

	/**
	 * @param cascadeTypes the cascadeTypes to set
	 */
	public void setCascadeTypes(Class[] cascadeTypes) {
		this.cascadeTypes = cascadeTypes;
	}

	/**
	 * @return the fieldsNullable
	 */
	public Boolean[] getFieldsNullable() {
		return fieldsNullable;
	}

	/**
	 * @param fieldsNullable the fieldsNullable to set
	 */
	public void setFieldsNullable(Boolean[] fieldsNullable) {
		this.fieldsNullable = fieldsNullable;
	}

	/**
	 * @return the hasDefaultValue
	 */
	public boolean isHasDefaultValue() {
		return hasDefaultValue;
	}

	/**
	 * @param hasDefaultValue the hasDefaultValue to set
	 */
	public void setHasDefaultValue(boolean hasDefaultValue) {
		this.hasDefaultValue = hasDefaultValue;
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
	 * @return the shardingModel
	 */
	public ShardingConfig getShardingConfig() {
		return shardingConfig;
	}

	/**
	 * @param shardingModel the shardingModel to set
	 */
	public void setShardingConfig(ShardingConfig shardingConfig) {
		this.shardingConfig = shardingConfig;
	}

	/**
	 * @return the idNameWhereSql
	 */
	public String getIdNameWhereSql() {
		return idNameWhereSql;
	}

	/**
	 * @param idNameWhereSql the idNameWhereSql to set
	 */
	public void setIdNameWhereSql(String idNameWhereSql) {
		this.idNameWhereSql = idNameWhereSql;
	}

	/**
	 * @return the idArgWhereSql
	 */
	public String getIdArgWhereSql() {
		return idArgWhereSql;
	}

	/**
	 * @param idArgWhereSql the idArgWhereSql to set
	 */
	public void setIdArgWhereSql(String idArgWhereSql) {
		this.idArgWhereSql = idArgWhereSql;
	}

	/**
	 * @return the businessIdGenerator
	 */
	public IdGenerator getBusinessIdGenerator() {
		return businessIdGenerator;
	}

	/**
	 * @param businessIdGenerator the businessIdGenerator to set
	 */
	public void setBusinessIdGenerator(IdGenerator businessIdGenerator) {
		this.businessIdGenerator = businessIdGenerator;
	}

	/**
	 * @return the businessIdField
	 */
	public String getBusinessIdField() {
		return businessIdField;
	}

	/**
	 * @param businessIdField the businessIdField to set
	 */
	public void setBusinessIdField(String businessIdField) {
		this.businessIdField = businessIdField;
	}

	/**
	 * @return the bizIdLength
	 */
	public Integer getBizIdLength() {
		// 默认26位nanotime主键策略
		if (bizIdLength == null)
			return 26;
		return bizIdLength;
	}

	/**
	 * @param bizIdLength the bizIdLength to set
	 */
	public void setBizIdLength(Integer bizIdLength) {
		this.bizIdLength = bizIdLength;
	}

	/**
	 * @return the bizIdSequenceSize
	 */
	public Integer getBizIdSequenceSize() {
		return bizIdSequenceSize;
	}

	/**
	 * @param bizIdSequenceSize the bizIdSequenceSize to set
	 */
	public void setBizIdSequenceSize(Integer bizIdSequenceSize) {
		this.bizIdSequenceSize = bizIdSequenceSize;
	}

	/**
	 * @param bizIdSignature the bizIdSignature to set
	 */
	public void setBizIdSignature(String bizIdSignature) {
		this.bizIdSignature = bizIdSignature;
	}

	/**
	 * @return the entityClass
	 */
	public Class getEntityClass() {
		return entityClass;
	}

	/**
	 * @param entityClass the entityClass to set
	 */
	public void setEntityClass(Class entityClass) {
		this.entityClass = entityClass;
	}

	/**
	 * @return the hasBizIdConfig
	 */
	public boolean isHasBizIdConfig() {
		return hasBizIdConfig;
	}

	/**
	 * @param hasBizIdConfig the hasBizIdConfig to set
	 */
	public void setHasBizIdConfig(boolean hasBizIdConfig) {
		this.hasBizIdConfig = hasBizIdConfig;
	}

	/**
	 * @return the bizIdEqPK
	 */
	public boolean isBizIdEqPK() {
		return bizIdEqPK;
	}

	/**
	 * @param bizIdEqPK the bizIdEqPK to set
	 */
	public void setBizIdEqPK(boolean bizIdEqPK) {
		this.bizIdEqPK = bizIdEqPK;
	}

	/**
	 * @return the allColumnNames
	 */
	public String getAllColumnNames() {
		return allColumnNames;
	}

	/**
	 * @param allColumnNames the allColumnNames to set
	 */
	public void setAllColumnNames(String allColumnNames) {
		this.allColumnNames = allColumnNames;
	}

}
