package org.sagacity.sqltoy.plugins.ddl;

import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.FieldMeta;
import org.sagacity.sqltoy.config.model.ForeignModel;
import org.sagacity.sqltoy.config.model.IndexModel;
import org.sagacity.sqltoy.model.ColumnMeta;
import org.sagacity.sqltoy.model.JdbcTypes;
import org.sagacity.sqltoy.model.TableMeta;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * @project sagacity-sqltoy
 * @description 创建表语句的工具类，用于将EntityMeta依旧外键关系排序，转化封装为TableModel
 * @author zhongxuchen
 * @version v1.0,Date:2023-12-17
 * @modify Date:2023-12-17,修改说明
 */
public class DDLUtils {
	public static String NEWLINE = "\r\n";
	public static String TAB = "   ";

	/**
	 * 因为存在外键关系，首先需要对表进行排序，被依赖的优先创建
	 * 
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
					EntityMeta foreignMeta = tmpEntityMeta.get(foreignTable);
					if (foreignMeta != null && !sortTables.containsKey(foreignTable)) {
						sortTables.put(foreignTable, foreignMeta);
					} // 外表和当前表都已经在排序队列中
					else if (foreignMeta != null && sortTables.containsKey(tableName)
							&& !isBefore(sortTables, foreignTable, tableName)) {
						swotTables.clear();
						// 将外键关联的表放第一位置
						swotTables.put(foreignTable, foreignMeta);
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
	 * 判断外键关联表位置是否在当前表的前面
	 * 
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
	 * 将EntityMeta转化为TableMeta 便于输出表结构
	 * 
	 * @param entityMeta
	 * @param dbType
	 * @return
	 */
	public static TableMeta wrapTableMeta(EntityMeta entityMeta, Integer dbType) {
		TableMeta tableMeta = new TableMeta();
		tableMeta.setTableName(entityMeta.getTableName());
		tableMeta.setRemarks(escapeCommentForDdl(entityMeta.getTableComment()));
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
			columnMeta.setComments(escapeCommentForDdl(fieldMeta.getComments()));
			columnMeta.setAutoIncrement(fieldMeta.isAutoIncrement());
			columnMeta.setColumnSize(fieldMeta.getLength());
			columnMeta.setPartitionKey(fieldMeta.isPartitionKey());
			columnMeta.setDefaultValue(fieldMeta.getDefaultValue());
			columnMeta.setNullable(fieldMeta.isNullable());
			columnMeta.setDataType(fieldMeta.getType());
			columnMeta.setTypeName(fieldMeta.getFieldType());
			columnMeta.setPK(fieldMeta.isPK() || fieldMeta.isDdlPk());
			columnMeta.setGeneratedType(fieldMeta.getGeneratedType());
			columnMeta.setDecimalDigits(fieldMeta.getPrecision());
			columnMeta.setNumPrecRadix(fieldMeta.getScale());
			columnMeta.setNativeType(fieldMeta.getNativeType());
			columns.add(columnMeta);
		}
		tableMeta.setColumns(columns);
		return tableMeta;
	}

	/**
	 * 设置类型
	 * 
	 * @param colMeta
	 * @param dbType
	 * @return
	 */
	public static String convertType(ColumnMeta colMeta, int dbType) {
		if (colMeta.getNativeType() != null) {
			if (colMeta.getNativeType().equalsIgnoreCase("JSON")) {
				return "JSON";
			} else if (colMeta.getNativeType().equalsIgnoreCase("BSON")) {
				if (dbType == DBType.POSTGRESQL || dbType == DBType.POSTGRESQL14 || dbType == DBType.GAUSSDB
						|| dbType == DBType.OPENGAUSS || dbType == DBType.MOGDB || dbType == DBType.STARDB
						|| dbType == DBType.OSCAR || dbType == DBType.VASTBASE) {
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
			if (dbType == DBType.ORACLE || dbType == DBType.ORACLE11 || dbType == DBType.DM || dbType == DBType.H2) {
				typeName = "CLOB";
			} else {
				typeName = "TEXT";
			}
			break;
		case java.sql.Types.TIMESTAMP_WITH_TIMEZONE:
			if (dbType == DBType.POSTGRESQL || dbType == DBType.POSTGRESQL14 || dbType == DBType.GAUSSDB
					|| dbType == DBType.OPENGAUSS || dbType == DBType.MOGDB || dbType == DBType.STARDB
					|| dbType == DBType.OSCAR || dbType == DBType.VASTBASE || dbType == DBType.ORACLE
					|| dbType == DBType.ORACLE11 || dbType == DBType.DM) {
				// 精度需跟在TIMESTAMP之后:TIMESTAMP(6) WITH TIME ZONE
				typeName = "TIMESTAMP";
				if (colMeta.getColumnSize() > 0) {
					typeName = typeName + "(" + colMeta.getColumnSize() + ")";
				}
				typeName = typeName + " WITH TIME ZONE";
			} else if (dbType == DBType.SQLSERVER) {
				typeName = setTimePrecision("DATETIMEOFFSET", colMeta);
			} else {
				typeName = "TIMESTAMP";
			}
			break;
		case java.sql.Types.BLOB:
			if (dbType == DBType.POSTGRESQL || dbType == DBType.POSTGRESQL14 || dbType == DBType.GAUSSDB
					|| dbType == DBType.OPENGAUSS || dbType == DBType.MOGDB || dbType == DBType.STARDB
					|| dbType == DBType.OSCAR || dbType == DBType.VASTBASE) {
				typeName = "BYTEA";
			} else if (dbType == DBType.SQLSERVER) {
				typeName = "VARBINARY(MAX)";
			} else {
				typeName = "BLOB";
			}
			isBytes = true;
			break;
		case java.sql.Types.BINARY:
			if (dbType == DBType.POSTGRESQL || dbType == DBType.POSTGRESQL14 || dbType == DBType.GAUSSDB
					|| dbType == DBType.OPENGAUSS || dbType == DBType.STARDB || dbType == DBType.OSCAR
					|| dbType == DBType.MOGDB || dbType == DBType.VASTBASE) {
				typeName = "BYTEA";
			} else if (dbType == DBType.ORACLE || dbType == DBType.ORACLE11 || dbType == DBType.DM) {
				typeName = "BLOB";
			} else if (dbType == DBType.SQLSERVER) {
				typeName = "VARBINARY(MAX)";
			} else {
				typeName = "BINARY";
				typeName = setLength(typeName, false, colMeta);
			}
			isBytes = true;
			break;
		case java.sql.Types.VARBINARY:
		case java.sql.Types.LONGVARBINARY:
			if (dbType == DBType.POSTGRESQL || dbType == DBType.POSTGRESQL14 || dbType == DBType.GAUSSDB
					|| dbType == DBType.OPENGAUSS || dbType == DBType.STARDB || dbType == DBType.OSCAR
					|| dbType == DBType.MOGDB || dbType == DBType.VASTBASE) {
				typeName = "BYTEA";
			} else if (dbType == DBType.ORACLE || dbType == DBType.ORACLE11 || dbType == DBType.DM) {
				typeName = "BLOB";
			} else if (dbType == DBType.SQLSERVER) {
				typeName = "VARBINARY(MAX)";
			} else {
				typeName = "VARBINARY";
				typeName = setLength(typeName, false, colMeta);
			}
			isBytes = true;
			break;
		case java.sql.Types.CLOB:
		case java.sql.Types.NCLOB:
			if (dbType == DBType.ORACLE || dbType == DBType.ORACLE11 || dbType == DBType.DM || dbType == DBType.H2) {
				typeName = "CLOB";
			} else {
				typeName = "TEXT";
			}
			break;
		case java.sql.Types.TIME:
			typeName = "TIME";
			break;
		case java.sql.Types.TIMESTAMP:
			// sqlserver的TIMESTAMP是行版本戳(rowversion),不是日期时间类型
			if (dbType == DBType.SQLSERVER) {
				typeName = "DATETIME2";
			} else {
				typeName = "TIMESTAMP";
			}
			break;
		case java.sql.Types.DATE:
			// update 2026-9-5 修复dm/pg按生成DDL建表后时间部分被静默清零的缺陷:实测dm的DATE列类型
			// 只存日期(与oracle的DATE含时间不同),pg系date列同样只存日期(pg 18.6实测,timestamp
			// 写入date列静默截断时间);java.util.Date/LocalDateTime这类含时间字段(无论@Column未指定
			// type自动探测,还是quickvo生成实体显式声明type=DATE)此前均输出DATE列;
			// 依据字段Java类型(typeName,小写全类名)精化:dm输出DATETIME,pg系输出TIMESTAMP,
			// 纯日期类型(LocalDate/java.sql.Date)保持DATE语义不变;
			// update 2026-9-6 原"不得将探测归入Types.TIMESTAMP"的约束已解除:rowversion剔除
			// 改按目标库元数据校准(ensureRowVersionMeta),与实体JDBC类型解耦,LocalDateTime已归位TIMESTAMP
			if (dbType == DBType.MYSQL || dbType == DBType.MYSQL57 || dbType == DBType.SQLSERVER
					|| dbType == DBType.DORIS || dbType == DBType.STARROCKS) {
				typeName = "DATETIME";
			} else if (isTimeCarryingJavaType(colMeta.getTypeName()) && isDateOnlyDialect(dbType)) {
				typeName = (dbType == DBType.DM) ? "DATETIME" : "TIMESTAMP";
			} else {
				typeName = "DATE";
			}
			break;
		case java.sql.Types.BOOLEAN:
			if ("string".equals(colMeta.getTypeName())) {
				if (colMeta.getColumnSize() > 0) {
					typeName = "VARCHAR";
					typeName = setLength(typeName, false, colMeta);
				} else {
					typeName = "CHAR(1)";
				}
			} else if (dbType == DBType.ORACLE || dbType == DBType.ORACLE11 || dbType == DBType.DM) {
				typeName = "INTEGER";
			} else if (dbType == DBType.SQLSERVER) {
				// sqlserver无boolean类型,bit为0/1
				typeName = "BIT";
			} else if (dbType == DBType.MYSQL || dbType == DBType.MYSQL57 || dbType == DBType.DORIS
					|| dbType == DBType.STARROCKS) {
				typeName = "TINYINT(1)";
			} else {
				// postgresql/openGauss/H2等原生支持boolean
				typeName = "BOOLEAN";
			}
			break;
		case JdbcTypes.VECTOR: {
			// 向量类型:gaussdb企业版为floatvector,其余(pgvector/openGauss系/oracle 23ai/mysql
			// heatwave/sqlserver 2025/db2 12.1.2+)为vector
			// 维度通过@Column(length=xxx)指定,mysql heatwave和sqlserver 2025的维度为必填项
			if (dbType == DBType.H2) {
				// h2无向量类型,测试场景按varchar存储'[1,2,3]'字符串形式
				typeName = "VARCHAR";
				typeName = setLength(typeName, false, colMeta);
			} else {
				if (dbType == DBType.GAUSSDB) {
					typeName = "FLOATVECTOR";
				} else {
					typeName = "VECTOR";
				}
				if (colMeta.getColumnSize() > 0) {
					typeName = typeName + "(" + colMeta.getColumnSize() + ")";
				}
			}
			break;
		}
		case JdbcTypes.GEOMETRY: {
			// 空间类型:oracle/dm为SDO_GEOMETRY,其余(postgis系/mysql/sqlserver/h2)为GEOMETRY
			// 类型精度修饰(如geometry(Point,4326)、geography)通过@Column(nativeType="...")指定;
			// nativeType未配置时注解默认为空串(非null),须按blank判断,否则生成空列类型导致DDL非法
			if (StringUtil.isNotBlank(colMeta.getNativeType())) {
				typeName = colMeta.getNativeType();
			} else if (dbType == DBType.ORACLE || dbType == DBType.ORACLE11 || dbType == DBType.DM) {
				typeName = "SDO_GEOMETRY";
			} else {
				typeName = "GEOMETRY";
			}
			break;
		}
		case java.sql.Types.FLOAT:
			typeName = "FLOAT";
			break;
		case java.sql.Types.DOUBLE:
			if (dbType == DBType.ORACLE || dbType == DBType.ORACLE11) {
				typeName = "BINARY_DOUBLE";
			} else if (dbType == DBType.POSTGRESQL || dbType == DBType.POSTGRESQL14 || dbType == DBType.GAUSSDB
					|| dbType == DBType.OPENGAUSS || dbType == DBType.MOGDB || dbType == DBType.STARDB
					|| dbType == DBType.OSCAR || dbType == DBType.VASTBASE || dbType == DBType.DM
					|| dbType == DBType.H2) {
				typeName = "DOUBLE PRECISION";
			} else if (dbType == DBType.SQLSERVER) {
				typeName = "FLOAT";
			} else {
				typeName = "DOUBLE";
			}
			break;
		case java.sql.Types.DECIMAL:
		case java.sql.Types.NUMERIC:
			if (dbType == DBType.ORACLE || dbType == DBType.ORACLE11 || dbType == DBType.DM) {
				typeName = "NUMBER";
			} else if (dbType == DBType.POSTGRESQL || dbType == DBType.POSTGRESQL14) {
				typeName = "NUMERIC";
			} else {
				typeName = "DECIMAL";
			}
			typeName = setLength(typeName, true, colMeta);
			break;
		default: {
			// nativeType未配置时注解默认为空串(非null),须按blank判断,否则未知类型会生成空列类型
			if (StringUtil.isNotBlank(colMeta.getNativeType())) {
				typeName = colMeta.getNativeType();
			} else {
				typeName = "VARCHAR";
				typeName = setLength(typeName, false, colMeta);
			}
		}
		}
		// 数组类型
		if ((dbType == DBType.POSTGRESQL || dbType == DBType.POSTGRESQL14 || dbType == DBType.GAUSSDB
				|| dbType == DBType.OPENGAUSS || dbType == DBType.MOGDB || dbType == DBType.STARDB
				|| dbType == DBType.OSCAR || dbType == DBType.VASTBASE) && colMeta.getTypeName() != null
				&& colMeta.getTypeName().endsWith("[]") && !isBytes && !typeName.startsWith("_")) {
			return "_".concat(typeName);
		}
		return typeName;
	}

	/**
	 * 设置类型长度
	 * 
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
	 * 设置时间类型的精度(只取一位精度,不能带scale)
	 * 
	 * @param typeName
	 * @param colMeta
	 * @return
	 */
	public static String setTimePrecision(String typeName, ColumnMeta colMeta) {
		if (colMeta.getColumnSize() > 0) {
			return typeName + "(" + colMeta.getColumnSize() + ")";
		}
		return typeName;
	}

	/**
	 * 包装主键信息
	 * 
	 * @param tableMeta
	 * @param toUpperOrLower
	 * @param dbType
	 * @param tableSql
	 */
	public static void wrapTablePrimaryKeys(TableMeta tableMeta, String toUpperOrLower, int dbType,
			StringBuilder tableSql) {
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
			tableSql.append("PRIMARY KEY (").append(StringUtil.toLowerOrUpper(primaryKeys, toUpperOrLower)).append(")");
		}
	}

	/**
	 * 组织索引信息
	 * 
	 * @param tableMeta
	 * @param dbType
	 * @param tableSql
	 * @param outerTable create table () 括号外还是内部
	 */
	public static void wrapTableIndexes(TableMeta tableMeta, String toUpperOrLower, int dbType, StringBuilder tableSql,
			boolean outerTable) {
		if (tableMeta.getIndexes() == null || tableMeta.getIndexes().isEmpty()) {
			return;
		}
		String splitSign = ";";
		String tableName = StringUtil.toLowerOrUpper(tableMeta.getTableName(), toUpperOrLower);
		String indexName;
		// 索引
		for (IndexModel indexModel : tableMeta.getIndexes()) {
			indexName = StringUtil.toLowerOrUpper(indexModel.getName(), toUpperOrLower);
			if (outerTable) {
				tableSql.append(splitSign).append(NEWLINE);
				tableSql.append("CREATE ");
				if (indexModel.isUnique()) {
					tableSql.append("UNIQUE ");
				}
				tableSql.append("INDEX ").append(indexName);
				tableSql.append(" ON ").append(tableName);
			} else {
				tableSql.append(",").append(NEWLINE);
				tableSql.append(TAB);
				if (indexModel.isUnique()) {
					tableSql.append("UNIQUE ");
				}
				tableSql.append("KEY ").append(indexName);
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
	 * 组织外键信息
	 * 
	 * @param tableMeta
	 * @param lowerOrUpper
	 * @param dbType
	 * @param tableSql
	 * @param outerTable   create table () 括号外还是内部
	 */
	public static void wrapForeignKeys(TableMeta tableMeta, String lowerOrUpper, int dbType, StringBuilder tableSql,
			boolean outerTable) {
		if (tableMeta.getForeigns() == null || tableMeta.getForeigns().isEmpty()) {
			return;
		}
		boolean isOracle = false;
		String splitSign = ";";
		for (ForeignModel foreign : tableMeta.getForeigns()) {
			isOracle = (dbType == DBType.ORACLE || dbType == DBType.ORACLE11 || dbType == DBType.DM);
			if (outerTable) {
				tableSql.append(splitSign).append(NEWLINE);
				tableSql.append("ALTER TABLE ")
						.append(StringUtil.toLowerOrUpper(tableMeta.getTableName(), lowerOrUpper));
				tableSql.append(" ADD ");
			} else {
				tableSql.append(",").append(NEWLINE);
				tableSql.append(TAB);
			}
			tableSql.append(" CONSTRAINT ").append(foreign.getConstraintName());
			tableSql.append(" FOREIGN KEY (").append(
					StringUtil.toLowerOrUpper(StringUtil.linkAry(",", true, foreign.getColumns()), lowerOrUpper))
					.append(")");
			tableSql.append(" REFERENCES ").append(StringUtil.toLowerOrUpper(foreign.getForeignTable(), lowerOrUpper))
					.append("(");
			tableSql.append(StringUtil.toLowerOrUpper(StringUtil.linkAry(",", true, foreign.getForeignColumns()),
					lowerOrUpper));
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
	 * 统一处理表和字段的备注
	 * 
	 * @param tableMeta
	 * @param lowerOrUpper
	 * @param dbType
	 * @param tableSql
	 */
	public static void wrapTableAndColumnsComment(TableMeta tableMeta, String lowerOrUpper, int dbType,
			StringBuilder tableSql) {
		String splitSign = ";";
		// 表注释
		if (StringUtil.isNotBlank(tableMeta.getRemarks())) {
			tableSql.append(splitSign);
			tableSql.append(NEWLINE);
			tableSql.append("COMMENT ON TABLE ")
					.append(StringUtil.toLowerOrUpper(tableMeta.getTableName(), lowerOrUpper)).append(" IS '")
					.append(tableMeta.getRemarks()).append("'");
		}
		// 字段注释
		for (ColumnMeta colMeta : tableMeta.getColumns()) {
			if (StringUtil.isNotBlank(colMeta.getComments())) {
				tableSql.append(splitSign);
				tableSql.append(NEWLINE);
				tableSql.append("COMMENT ON COLUMN ")
						.append(StringUtil.toLowerOrUpper(tableMeta.getTableName(), lowerOrUpper)).append(".")
						.append(colMeta.getColName()).append(" IS '").append(colMeta.getComments()).append("'");
			}
		}
	}

	/**
	 * 判断类型默认值是否需要加单引号
	 * 
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
	 * 判断是否是日期或时间类型
	 * 
	 * @param dataType
	 * @return
	 */
	public static boolean isDate(int dataType) {
		if (dataType == Types.DATE || dataType == Types.TIME || dataType == Types.TIMESTAMP
				|| dataType == Types.TIME_WITH_TIMEZONE || dataType == Types.TIMESTAMP_WITH_TIMEZONE) {
			return true;
		}
		return false;
	}

	/**
	 * 判断字段Java类型是否为日期+时间类型(update 2026-9-5 供Types.DATE列类型精化使用):
	 * java.time.LocalDateTime与java.util.Date携带时间部分,LocalDate/java.sql.Date为纯日期;
	 * typeName取自FieldMeta.fieldType(小写全类名),可能为null(空值按纯日期处理,尊重显式声明)
	 * 
	 * @param typeName
	 * @return
	 */
	private static boolean isTimeCarryingJavaType(String typeName) {
		if (typeName == null) {
			return false;
		}
		String tmp = typeName.toLowerCase(Locale.ROOT);
		return "java.time.localdatetime".equals(tmp) || "java.util.date".equals(tmp);
	}

	/**
	 * 判断数据库的DATE列类型是否为纯日期语义(update 2026-9-5): dm的DATE列只存日期(dm
	 * 21c实测)、pg及衍生库的date只存日期(pg 18.6实测,timestamp写入静默截断时间);
	 * oracle的DATE含时间、mysql系无纯DATE列( Types.DATE已统一转DATETIME ),均不在精化范围;
	 * kingbase/openGauss/MogDB等PG衍生库的date在实例为Oracle兼容模式时含时间(此时TIMESTAMP为无损超型),
	 * PG模式时只存日期(此时TIMESTAMP修复丢时间),两种模式下输出TIMESTAMP均无损,按安全优先纳入
	 * 
	 * @param dbType
	 * @return
	 */
	private static boolean isDateOnlyDialect(int dbType) {
		return dbType == DBType.DM || dbType == DBType.POSTGRESQL || dbType == DBType.POSTGRESQL14
				|| dbType == DBType.GAUSSDB || dbType == DBType.KINGBASE || dbType == DBType.MOGDB
				|| dbType == DBType.OPENGAUSS || dbType == DBType.VASTBASE || dbType == DBType.STARDB
				|| dbType == DBType.OSCAR;
	}

	/**
	 * 判断是否是日期函数，对默认值处理时不需要加单引号
	 * 
	 * @param defaultValue
	 * @return
	 */
	public static boolean isDateFunction(String defaultValue) {
		if (defaultValue.equals("SYSDATE()") || defaultValue.equals("SYSDATE") || defaultValue.equals("NOW()")
				|| defaultValue.equals("GETDATE()") || defaultValue.equals("CURRENT_TIMESTAMP()")
				|| defaultValue.equals("CURRENT_TIMESTAMP") || defaultValue.equals("CURRENT_DATE")
				|| defaultValue.equals("CURDATE()") || defaultValue.equals("CURTIME()")
				|| defaultValue.equals("LOCALTIMESTAMP")) {
			return true;
		}
		return false;
	}

	/**
	 * 将注释中的单引号转义为标准SQL的''形式(Oracle/PostgreSQL/MySQL等主流库的字符串字面量均支持),
	 * 表和字段注释统一使用本方法保证转义一致;注释处于单引号字面量内,双引号无需转义,
	 * 反斜杠在标准SQL中是普通字符(MySQL特有转义由MySqlDDLGenerator输出时单独处理)
	 * 
	 * @param str
	 * @return
	 */
	private static String escapeCommentForDdl(String str) {
		if (str == null || str.isEmpty()) {
			return str;
		}
		return str.replace("'", "''");
	}
}
