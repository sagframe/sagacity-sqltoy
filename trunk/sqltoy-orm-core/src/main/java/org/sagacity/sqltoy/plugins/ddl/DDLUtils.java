/**
 * 
 */
package org.sagacity.sqltoy.plugins.ddl;

import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.FieldMeta;
import org.sagacity.sqltoy.config.model.ForeignModel;
import org.sagacity.sqltoy.config.model.IndexModel;
import org.sagacity.sqltoy.model.ColumnMeta;
import org.sagacity.sqltoy.model.TableMeta;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * @project sagacity-sqltoy
 * @description 创建表语句的工具类，用于将EntityMeta依旧外键关系排序，转化封装为TableModel
 * @author zhongxuchen
 * @version v1.0, Date:2023年12月17日
 * @modify 2023年12月17日,修改说明
 */
public class DDLUtils {
	public static String NEWLINE = "\r\n";
	public static String TAB = "   ";

	/**
	 * @TODO 因为存在外键关系，首先需要对表进行排序，被依赖的优先创建
	 * @param entitysMetaMap
	 * @return
	 */
	public static List<EntityMeta> sortTables(ConcurrentHashMap<String, EntityMeta> entitysMetaMap) {
		// 构建一个暂时存放
		LinkedHashMap<String, EntityMeta> tmpEntityMeta = new LinkedHashMap<String, EntityMeta>();
		EntityMeta entityMeta;
		String tableName;
		for (Map.Entry<String, EntityMeta> entry : entitysMetaMap.entrySet()) {
			entityMeta = entry.getValue();
			tableName = entityMeta.getSchemaTable(null, null);
			tmpEntityMeta.put(tableName, entityMeta);
		}

		// 组织排序
		LinkedHashMap<String, EntityMeta> sortTables = new LinkedHashMap<String, EntityMeta>();
		LinkedHashMap<String, EntityMeta> swotTables = new LinkedHashMap<String, EntityMeta>();
		for (Map.Entry<String, EntityMeta> entry : entitysMetaMap.entrySet()) {
			entityMeta = entry.getValue();
			tableName = entityMeta.getSchemaTable(null, null);
			// 有外键依赖的表放在前面
			if (entityMeta.getForeignFields() != null) {
				String foreignTable;
				for (Map.Entry<String, ForeignModel> iter : entityMeta.getForeignFields().entrySet()) {
					foreignTable = iter.getValue().getForeignTable();
					if (entityMeta.getSchema() != null
							&& !foreignTable.startsWith(entityMeta.getSchema().concat("."))) {
						foreignTable = entityMeta.getSchema().concat(".").concat(foreignTable);
					}
					if (!sortTables.containsKey(foreignTable)) {
						sortTables.put(foreignTable, tmpEntityMeta.get(foreignTable));
					} // 外表和当前表都已经在排序队列中
					else if (sortTables.containsKey(tableName) && !isBefore(sortTables, foreignTable, tableName)) {
						swotTables.clear();
						// 将外键关联的表放第一位置
						swotTables.put(foreignTable, tmpEntityMeta.get(foreignTable));
						// 先移除外键关联表
						sortTables.remove(foreignTable);
						swotTables.putAll(sortTables);
						sortTables.clear();
						// 完成关联表放首位的调整
						sortTables.putAll(swotTables);
					}
				}
			}
			// 未被依赖过
			if (!sortTables.containsKey(tableName)) {
				sortTables.put(tableName, entityMeta);
			}
		}
		return new ArrayList<EntityMeta>(sortTables.values());
	}

	/**
	 * @TODO 判断外键关联表位置是否在当前表的前面
	 * @param sortTables
	 * @param foreignTable
	 * @param nowTable
	 * @return
	 */
	public static boolean isBefore(LinkedHashMap<String, EntityMeta> sortTables, String foreignTable, String nowTable) {
		int foreignTableIndex = 0;
		int nowTableIndex = 0;
		String tableName;
		int index = 0;
		for (Map.Entry<String, EntityMeta> entry : sortTables.entrySet()) {
			tableName = entry.getKey();
			if (foreignTable.equals(tableName)) {
				foreignTableIndex = index;
			} else if (nowTable.equals(tableName)) {
				nowTableIndex = index;
			}
			index++;
		}
		if (foreignTableIndex < nowTableIndex) {
			return true;
		}
		return false;
	}

	/**
	 * @TODO 将EntityMeta转化为TableMeta 便于输出表结构
	 * @param entityMeta
	 * @return
	 */
	public static TableMeta wrapTableMeta(EntityMeta entityMeta) {
		TableMeta tableMeta = new TableMeta();
		tableMeta.setTableName(entityMeta.getTableName());
		tableMeta.setRemarks(translateSpecialSymbols(entityMeta.getTableComment()));
		tableMeta.setSchema(entityMeta.getSchema());
		tableMeta.setPkConstraint(entityMeta.getPkConstraint());
		// 索引信息
		if (entityMeta.getIndexModels() != null) {
			List<IndexModel> indexModels = new ArrayList<>();
			for (IndexModel indexModel : entityMeta.getIndexModels()) {
				indexModels.add(indexModel);
			}
			tableMeta.setIndexes(indexModels);
		}
		// 外键信息
		if (entityMeta.getForeignFields() != null) {
			List<ForeignModel> foreignModels = new ArrayList<>();
			for (Map.Entry<String, ForeignModel> entry : entityMeta.getForeignFields().entrySet()) {
				foreignModels.add(entry.getValue());
			}
			tableMeta.setForeigns(foreignModels);
		}
		// 列信息
		Map<String, FieldMeta> fieldMetaMap = entityMeta.getFieldsMeta();
		FieldMeta fieldMeta;
		List<ColumnMeta> columns = new ArrayList<>();
		for (Map.Entry<String, FieldMeta> entry : fieldMetaMap.entrySet()) {
			fieldMeta = entry.getValue();
			ColumnMeta columnMeta = new ColumnMeta();
			columnMeta.setColName(fieldMeta.getColumnName());
			columnMeta.setComments(translateSpecialSymbols(fieldMeta.getComments()));
			columnMeta.setAutoIncrement(fieldMeta.isAutoIncrement());
			columnMeta.setColumnSize(fieldMeta.getLength());
			columnMeta.setPartitionKey(fieldMeta.isPartitionKey());
			columnMeta.setDefaultValue(fieldMeta.getDefaultValue());
			columnMeta.setNullable(fieldMeta.isNullable());
			columnMeta.setDataType(fieldMeta.getType());
			columnMeta.setTypeName(fieldMeta.getFieldType());
			columnMeta.setPK(fieldMeta.isPK());
			columnMeta.setDecimalDigits(fieldMeta.getPrecision());
			columnMeta.setNumPrecRadix(fieldMeta.getScale());
			columnMeta.setNativeType(fieldMeta.getNativeType());
			columns.add(columnMeta);
		}
		tableMeta.setColumns(columns);
		return tableMeta;
	}

	/**
	 * @TODO 设置类型
	 * @param colMeta
	 * @return
	 */
	public static String convertType(ColumnMeta colMeta, int dbType) {
		if (colMeta.getNativeType() != null) {
			if (colMeta.getNativeType().equalsIgnoreCase("JSON")) {
				return "JSON";
			} else if (colMeta.getNativeType().equalsIgnoreCase("BSON")) {
				if (dbType == DBType.POSTGRESQL || dbType == DBType.POSTGRESQL15 || dbType == DBType.GAUSSDB
						|| dbType == DBType.MOGDB) {
					return "BSON";
				} else {
					return "JSON";
				}
			}
		}
		boolean isBytes = false;
		String typeName = "VARCHAR";
		switch (colMeta.getDataType()) {
		case java.sql.Types.BIGINT:
			if (dbType == DBType.ORACLE || dbType == DBType.ORACLE11 || dbType == DBType.DM) {
				typeName = "NUMBER";
				typeName = setLength(typeName, true, colMeta);
			} else {
				typeName = "BIGINT";
			}
			break;
		case java.sql.Types.INTEGER:
			typeName = "INTEGER";
			break;
		case java.sql.Types.TINYINT:
			if (dbType == DBType.ORACLE || dbType == DBType.ORACLE11 || dbType == DBType.DM) {
				typeName = "INTEGER";
			} else {
				typeName = "TINYINT";
				typeName = setLength(typeName, true, colMeta);
			}
			break;
		case java.sql.Types.SMALLINT:
			typeName = "SMALLINT";
			break;
		case java.sql.Types.CHAR:
		case java.sql.Types.NCHAR:
			typeName = "CHAR";
			typeName = setLength(typeName, false, colMeta);
			break;
		case java.sql.Types.VARCHAR:
		case java.sql.Types.NVARCHAR:
			typeName = "VARCHAR";
			typeName = setLength(typeName, false, colMeta);
			break;
		case java.sql.Types.LONGNVARCHAR:
			if (dbType == DBType.ORACLE || dbType == DBType.ORACLE11 || dbType == DBType.DM) {
				typeName = "CLOB";
			} else {
				typeName = "TEXT";
			}
			break;
		case java.sql.Types.BLOB:
			if (dbType == DBType.POSTGRESQL || dbType == DBType.POSTGRESQL15 || dbType == DBType.GAUSSDB
					|| dbType == DBType.MOGDB) {
				typeName = "bytea";
			} else if (dbType == DBType.SQLSERVER) {
				typeName = "IMAGE";
			} else {
				typeName = "BLOB";
			}
			isBytes = true;
			break;
		case java.sql.Types.BINARY:
			if (dbType == DBType.POSTGRESQL || dbType == DBType.POSTGRESQL15 || dbType == DBType.GAUSSDB
					|| dbType == DBType.MOGDB) {
				typeName = "bytea";
			} else if (dbType == DBType.ORACLE || dbType == DBType.ORACLE11 || dbType == DBType.DM) {
				typeName = "BLOB";
			} else if (dbType == DBType.SQLSERVER) {
				typeName = "IMAGE";
			} else {
				typeName = "BINARY";
				typeName = setLength(typeName, false, colMeta);
			}
			isBytes = true;
			break;
		case java.sql.Types.VARBINARY:
		case java.sql.Types.LONGVARBINARY:
			if (dbType == DBType.POSTGRESQL || dbType == DBType.POSTGRESQL15 || dbType == DBType.GAUSSDB
					|| dbType == DBType.MOGDB) {
				typeName = "bytea";
			} else if (dbType == DBType.ORACLE || dbType == DBType.ORACLE11 || dbType == DBType.DM) {
				typeName = "BLOB";
			} else if (dbType == DBType.SQLSERVER) {
				typeName = "IMAGE";
			} else {
				typeName = "VARBINARY";
				typeName = setLength(typeName, false, colMeta);
			}
			isBytes = true;
			break;
		case java.sql.Types.CLOB:
		case java.sql.Types.NCLOB:
			if (dbType == DBType.ORACLE || dbType == DBType.ORACLE11 || dbType == DBType.DM) {
				typeName = "CLOB";
			} else {
				typeName = "TEXT";
			}
			break;
		case java.sql.Types.TIME:
			typeName = "TIME";
			break;
		case java.sql.Types.TIMESTAMP:
			typeName = "TIMESTAMP";
			break;
		case java.sql.Types.DATE:
			if (dbType == DBType.MYSQL || dbType == DBType.MYSQL57 || dbType == DBType.SQLSERVER) {
				typeName = "DATETIME";
			} else {
				typeName = "DATE";
			}
			break;
		case java.sql.Types.BOOLEAN:
			if (colMeta.getTypeName().equals("string")) {
				if (colMeta.getColumnSize() > 0) {
					typeName = "VARCHAR";
					typeName = setLength(typeName, false, colMeta);
				} else {
					typeName = "CHAR(1)";
				}
			} else if (dbType == DBType.ORACLE || dbType == DBType.ORACLE11 || dbType == DBType.DM) {
				typeName = "INTEGER";
			} else {
				typeName = "TINYINT(1)";
			}
			break;
		case java.sql.Types.FLOAT:
			typeName = "FLOAT";
			break;
		case java.sql.Types.DOUBLE:
			typeName = "DOUBLE";
			break;
		case java.sql.Types.DECIMAL:
		case java.sql.Types.NUMERIC:
			if (dbType == DBType.ORACLE || dbType == DBType.ORACLE11 || dbType == DBType.DM) {
				typeName = "NUMBER";
			} else if (dbType == DBType.POSTGRESQL || dbType == DBType.POSTGRESQL15) {
				typeName = "NUMERIC";
			} else {
				typeName = "DECIMAL";
			}
			typeName = setLength(typeName, true, colMeta);
			break;
		default: {
			if (colMeta.getNativeType() != null) {
				typeName = colMeta.getNativeType();
			} else {
				typeName = "VARCHAR";
				typeName = setLength(typeName, false, colMeta);
			}
		}
		}
		// 数组类型
		if ((dbType == DBType.POSTGRESQL || dbType == DBType.POSTGRESQL15 || dbType == DBType.GAUSSDB
				|| dbType == DBType.MOGDB) && colMeta.getTypeName().endsWith("[]") && !isBytes
				&& !typeName.startsWith("_")) {
			return "_".concat(typeName);
		}
		return typeName;
	}

	/**
	 * @TODO 设置类型长度
	 * @param typeName
	 * @param isNumber
	 * @param colMeta
	 * @return
	 */
	public static String setLength(String typeName, boolean isNumber, ColumnMeta colMeta) {
		if (isNumber) {
			if (colMeta.getNumPrecRadix() > 0) {
				return typeName + "(" + colMeta.getColumnSize() + "," + colMeta.getNumPrecRadix() + ")";
			}
		}
		if (colMeta.getColumnSize() > 0) {
			if (typeName.equals("CHAR") || typeName.equals("VARCHAR")) {
				return typeName + "(" + (colMeta.getColumnSize() > 10485760 ? 10485760 : colMeta.getColumnSize()) + ")";
			}
			return typeName + "(" + colMeta.getColumnSize() + ")";
		}
		return typeName;
	}

	/**
	 * @TODO 包装主键信息
	 * @param tableMeta
	 * @param dbType
	 * @param tableSql
	 */
	public static void wrapTablePrimaryKeys(TableMeta tableMeta, int dbType, StringBuilder tableSql) {
		String primaryKeys = "";
		for (ColumnMeta colMeta : tableMeta.getColumns()) {
			if (colMeta.isPK()) {
				if (primaryKeys.equals("")) {
					primaryKeys = colMeta.getColName();
				} else {
					primaryKeys = primaryKeys + "," + colMeta.getColName();
				}
			}
		}
		// 主键
		if (!primaryKeys.equals("")) {
			tableSql.append(",").append(NEWLINE);
			tableSql.append(TAB);
			tableSql.append("primary key (").append(primaryKeys).append(")");
		}
	}

	/**
	 * @TODO 组织索引信息
	 * @param tableMeta
	 * @param dbType
	 * @param tableSql
	 * @param outerTable
	 */
	public static void wrapTableIndexes(TableMeta tableMeta, int dbType, StringBuilder tableSql, boolean outerTable) {
		if (tableMeta.getIndexes() == null || tableMeta.getIndexes().isEmpty()) {
			return;
		}
		String splitSign = ";";
		// 索引
		for (IndexModel indexModel : tableMeta.getIndexes()) {
			if (outerTable) {
				tableSql.append(splitSign).append(NEWLINE);
				tableSql.append("create ");
				if (indexModel.isUnique()) {
					tableSql.append("UNIQUE ");
				}
				tableSql.append("index ").append(indexModel.getName());
				tableSql.append(" on ").append(tableMeta.getTableName());
			} else {
				tableSql.append(",").append(NEWLINE);
				tableSql.append(TAB);
				if (indexModel.isUnique()) {
					tableSql.append("UNIQUE ");
				}
				tableSql.append("KEY ").append(indexModel.getName());
			}
			tableSql.append(" (");
			int meter = 0;
			String[] sortTypes = indexModel.getSortTypes();
			int typeLen = (sortTypes == null) ? 0 : sortTypes.length;
			for (String col : indexModel.getColumns()) {
				if (meter > 0) {
					tableSql.append(",");
				}
				tableSql.append(col);
				if (meter < typeLen && StringUtil.isNotBlank(sortTypes[meter])) {
					tableSql.append(" ").append(sortTypes[meter]);
				}
				meter++;
			}
			tableSql.append(")");
		}
	}

	/**
	 * @TODO 组织外键信息
	 * @param tableMeta
	 * @param dbType
	 * @param tableSql
	 * @param outerTable
	 */
	public static void wrapForeignKeys(TableMeta tableMeta, int dbType, StringBuilder tableSql, boolean outerTable) {
		if (tableMeta.getForeigns() == null || tableMeta.getForeigns().isEmpty()) {
			return;
		}
		boolean isOracle = false;
		String splitSign = ";";
		for (ForeignModel foreign : tableMeta.getForeigns()) {
			isOracle = (dbType == DBType.ORACLE || dbType == DBType.ORACLE11 || dbType == DBType.DM);
			if (outerTable) {
				tableSql.append(splitSign).append(NEWLINE);
				tableSql.append("alter table ").append(tableMeta.getTableName());
				tableSql.append(" add ");
			} else {
				tableSql.append(",").append(NEWLINE);
				tableSql.append(TAB);
			}
			tableSql.append(" CONSTRAINT ").append(foreign.getConstraintName());
			tableSql.append(" FOREIGN KEY (").append(StringUtil.linkAry(",", true, foreign.getColumns())).append(")");
			tableSql.append(" REFERENCES ").append(foreign.getForeignTable()).append("(");
			tableSql.append(StringUtil.linkAry(",", true, foreign.getForeignColumns()));
			tableSql.append(")");
			if (foreign.getDeleteRestict() == 1) {
				if (!isOracle) {
					tableSql.append(" ON DELETE RESTRICT");
				}
			} else if (foreign.getDeleteRestict() == 0) {
				tableSql.append(" ON DELETE CASCADE");
			} else if (foreign.getDeleteRestict() == 2) {
				tableSql.append(" ON DELETE SET NULL");
			} else if (foreign.getDeleteRestict() == 3) {
				tableSql.append(" ON DELETE NO ACTION");
			} else if (foreign.getDeleteRestict() == 4) {
				tableSql.append(" ON DELETE SET DEFAULT");
			}
			if (!isOracle) {
				if (foreign.getUpdateRestict() == 1) {
					tableSql.append(" ON UPDATE RESTRICT");
				} else if (foreign.getUpdateRestict() == 0) {
					tableSql.append(" ON UPDATE CASCADE");
				} else if (foreign.getUpdateRestict() == 2) {
					tableSql.append(" ON UPDATE SET NULL");
				} else if (foreign.getUpdateRestict() == 3) {
					tableSql.append(" ON UPDATE NO ACTION");
				} else if (foreign.getUpdateRestict() == 4) {
					tableSql.append(" ON UPDATE SET DEFAULT");
				}
			}
		}
	}

	/**
	 * @TODO 统一处理表和字段的备注
	 * @param tableMeta
	 * @param dbType
	 * @param tableSql
	 */
	public static void wrapTableAndColumnsComment(TableMeta tableMeta, int dbType, StringBuilder tableSql) {
		String splitSign = ";";
		// 表注释
		if (StringUtil.isNotBlank(tableMeta.getRemarks())) {
			tableSql.append(splitSign);
			tableSql.append(NEWLINE);
			tableSql.append("COMMENT ON TABLE ").append(tableMeta.getTableName()).append(" IS '")
					.append(tableMeta.getRemarks()).append("'");
		}
		// 字段注释
		for (ColumnMeta colMeta : tableMeta.getColumns()) {
			if (StringUtil.isNotBlank(colMeta.getComments())) {
				tableSql.append(splitSign);
				tableSql.append(NEWLINE);
				tableSql.append("COMMENT ON COLUMN ").append(tableMeta.getTableName()).append(".")
						.append(colMeta.getColName()).append(" IS '").append(colMeta.getComments()).append("'");
			}
		}
	}

	/**
	 * @TODO 判断类型默认值是否需要加单引号
	 * @param dataType
	 * @return
	 */
	public static boolean isNotChar(int dataType) {
		if (dataType == Types.BIGINT || dataType == Types.INTEGER || dataType == Types.BOOLEAN
				|| dataType == Types.DECIMAL || dataType == Types.DOUBLE || dataType == Types.NUMERIC
				|| dataType == Types.FLOAT || dataType == Types.REAL || dataType == Types.SMALLINT
				|| dataType == Types.TINYINT || dataType == Types.BIT) {
			return true;
		}
		return false;
	}

	/**
	 * @TODO 转化单引号、双引号
	 * @param str
	 * @return
	 */
	private static String translateSpecialSymbols(String str) {
		if (str == null) {
			return str;
		}
		return str.replaceAll("\'", "\\\\\'").replaceAll("\"", "\\\\\"");
	}
}
