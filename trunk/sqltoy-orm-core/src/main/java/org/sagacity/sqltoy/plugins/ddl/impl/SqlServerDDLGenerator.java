package org.sagacity.sqltoy.plugins.ddl.impl;

import java.util.Locale;

import org.sagacity.sqltoy.model.ColumnMeta;
import org.sagacity.sqltoy.model.TableMeta;
import org.sagacity.sqltoy.plugins.ddl.DDLUtils;
import org.sagacity.sqltoy.plugins.ddl.DialectDDLGenerator;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * @project sagacity-sqltoy
 * @description sqlserver数据库
 * @author zhongxuchen
 * @version v1.0,Date:2023-12-27
 * @modify Date:2023-12-27,修改说明
 */
public class SqlServerDDLGenerator implements DialectDDLGenerator {
	private String NEWLINE = "\r\n";
	private String TAB = "   ";

	/**
	 * 生成创建表sql
	 */
	public String createTableSql(TableMeta tableMeta, String schema, String upperOrLower, int dbType) {
		if (tableMeta == null) {
			return null;
		}
		StringBuilder tableSql = new StringBuilder();
		String tableName = StringUtil.toLowerOrUpper(tableMeta.getTableName(), upperOrLower);
		// update 2026-10-13 分区表:sqlserver分区=分区函数+分区方案模型(无内联PARTITION BY子句),
		// 须先建函数/方案,聚集索引(或无PK时的表)经ON子句挂到方案上实现分区对齐;
		// 边界值来自@PartitionDef(quickvo从sys.partition_range_values提取),
		// 方向来自strategy(RANGE RIGHT/RANGE LEFT,缺省RIGHT);
		// 函数/方案名按pf_/ps_+表名+列名派生(源库原名不在@Partition元数据内,派生名保证脚本可重跑)
		org.sagacity.sqltoy.config.model.PartitionMeta partitionMeta = tableMeta.getPartitionMeta();
		String schemeName = null;
		String partitionColumn = null;
		if (isRangePartitioned(partitionMeta)) {
			partitionColumn = resolvePartitionColumn(tableMeta, partitionMeta, upperOrLower);
			if (partitionColumn != null) {
				schemeName = wrapPartitionFunctionAndScheme(tableMeta, partitionMeta, partitionColumn, tableName,
						upperOrLower, tableSql);
			}
		}
		tableSql.append("CREATE TABLE ").append(tableName).append(NEWLINE);
		tableSql.append("(").append(NEWLINE);
		int index = 0;
		String splitSign = ";";
		String realSchema = StringUtil.toLowerOrUpper(schema, upperOrLower);
		for (ColumnMeta colMeta : tableMeta.getColumns()) {
			if (index > 0) {
				tableSql.append(",").append(NEWLINE);
			}
			// 字段名
			tableSql.append(TAB).append(StringUtil.toLowerOrUpper(colMeta.getColName(), upperOrLower));
			// 类型:SQLServer VARCHAR上限8000,超长降级VARCHAR(MAX);VECTOR降级VARBINARY(MAX)
			String colType = DDLUtils.convertType(colMeta, dbType);
			if (colType.startsWith("VARCHAR") || colType.startsWith("NVARCHAR")) {
				int open = colType.indexOf('(');
				int close = colType.indexOf(')', open);
				if (open > 0 && close > open) {
					try {
						int len = Integer.parseInt(colType.substring(open + 1, close).trim());
						if (len > 8000) {
							colType = colType.substring(0, open) + "(MAX)";
						}
					} catch (NumberFormatException ignore) {
					}
				}
			}
			// SQLServer无原生VECTOR类型(2025预览版才有),降级VARBINARY(MAX)
			if (colType.startsWith("VECTOR")) {
				colType = "VARBINARY(MAX)";
			}
			// 计算列:SQLServer语法为 col AS (expr) [PERSISTED],不带数据类型(带类型会让AS变成语法错误)
			if (colMeta.getGeneratedType() > 0 && StringUtil.isNotBlank(colMeta.getDefaultValue())) {
				tableSql.append(" AS (").append(colMeta.getDefaultValue()).append(") ");
				tableSql.append(colMeta.getGeneratedType() == 1 ? "" : " PERSISTED ");
			} else {
				tableSql.append(" ").append(colType);
			}
			// 是否为null
			if (!colMeta.isNullable()) {
				tableSql.append(" NOT NULL");
			}
			// 自增
			if (colMeta.isAutoIncrement()) {
				tableSql.append(" IDENTITY ");
			} else if (colMeta.getGeneratedType() == 0 && StringUtil.isNotBlank(colMeta.getDefaultValue())) {
				tableSql.append(" DEFAULT ");
				if (DDLUtils.isNotChar(colMeta.getDataType())) {
					tableSql.append(colMeta.getDefaultValue());
				} else if (DDLUtils.isDate(colMeta.getDataType())
						&& DDLUtils.isDateFunction(colMeta.getDefaultValue().toUpperCase(Locale.ROOT))) {
					tableSql.append(colMeta.getDefaultValue());
				} else {
					tableSql.append("'").append(colMeta.getDefaultValue()).append("'");
				}
			}
			index++;
		}
		// 主键:分区表的聚集主键须含分区键并ON到分区方案(对齐分区);
		// 主键不含分区键时退化为普通主键(sqlserver不允许非对齐的唯一聚集索引,表实际不分区)
		String pkColumns = collectPkColumns(tableMeta, upperOrLower);
		boolean pkAligned = schemeName != null && pkColumns != null
				&& containsColumn(tableMeta, pkColumns, partitionColumn);
		if (pkAligned) {
			tableSql.append(",").append(NEWLINE);
			tableSql.append(TAB).append("PRIMARY KEY CLUSTERED (").append(pkColumns).append(") ON ").append(schemeName)
					.append(" (").append(partitionColumn).append(")");
		} else {
			DDLUtils.wrapTablePrimaryKeys(tableMeta, upperOrLower, dbType, tableSql);
		}
		tableSql.append(NEWLINE);
		tableSql.append(")");
		// 无主键的分区表:表级ON挂到分区方案(堆表分区)
		if (schemeName != null && pkColumns == null) {
			tableSql.append(" ON ").append(schemeName).append(" (").append(partitionColumn).append(")");
		}
		// 表注释，sqlserver比较特殊，不支持comment
		if (StringUtil.isNotBlank(schema)) {
			// 表注释
			if (StringUtil.isNotBlank(tableMeta.getRemarks())) {
				// EXEC sys.sp_addextendedproperty N'MS_Description', N'表注释', N'SCHEMA', dbo,
				// N'table' , 表名 , null, null
				// update 2026-10-13 字面量必须带N前缀:无N时按库排序规则代码页解析,非中文排序规则
				// (如SQL_Latin1_General_CP1_CI_AS)下中文注释入库即变'?'(真库实测hex=3F3F..)
				tableSql.append(splitSign);
				tableSql.append(NEWLINE);
				tableSql.append(NEWLINE);
				tableSql.append("EXECUTE sp_addextendedproperty N'MS_Description',")
						.append(nLiteral(tableMeta.getRemarks())).append(",");
				tableSql.append("N'SCHEMA',").append(nLiteral(realSchema)).append(",");
				tableSql.append("N'TABLE',").append(nLiteral(tableName));
			}
			// 字段注释
			for (ColumnMeta colMeta : tableMeta.getColumns()) {
				if (StringUtil.isNotBlank(colMeta.getComments())) {
					tableSql.append(splitSign);
					tableSql.append(NEWLINE);
					tableSql.append(NEWLINE);
					tableSql.append("EXECUTE sp_addextendedproperty N'MS_Description',")
							.append(nLiteral(colMeta.getComments())).append(",");
					tableSql.append("N'SCHEMA',").append(nLiteral(realSchema)).append(",");
					tableSql.append("N'TABLE',").append(nLiteral(tableName)).append(",");
					tableSql.append("N'COLUMN',")
							.append(nLiteral(StringUtil.toLowerOrUpper(colMeta.getColName(), upperOrLower)));
				}
			}
		}
		// 索引
		DDLUtils.wrapTableIndexes(tableMeta, upperOrLower, dbType, tableSql, true);
		// 外键
		DDLUtils.wrapForeignKeys(tableMeta, upperOrLower, dbType, tableSql, true);
		return tableSql.toString();
	}

	/** unicode字面量:N前缀+单引号翻倍转义(注释等含中文/引号的值须按nvarchar解析,避免排序规则代码页丢字) */
	private String nLiteral(String value) {
		return "N'" + (value == null ? "" : value.replace("'", "''")) + "'";
	}

	/** 是否为可还原的RANGE分区(有策略且有边界值明细;无边界值无法建分区函数,退化普通表) */
	private boolean isRangePartitioned(org.sagacity.sqltoy.config.model.PartitionMeta partitionMeta) {
		return partitionMeta != null && partitionMeta.getStrategy() != null
				&& partitionMeta.getStrategy().toUpperCase(Locale.ROOT).startsWith("RANGE")
				&& partitionMeta.getPartitionValues() != null && partitionMeta.getPartitionValues().length > 0;
	}

	/** 分区键列名(按输出大小写策略);@Partition.columns缺失或列不存在返回null */
	private String resolvePartitionColumn(TableMeta tableMeta,
			org.sagacity.sqltoy.config.model.PartitionMeta partitionMeta, String upperOrLower) {
		String[] columns = partitionMeta.getColumns();
		if (columns == null || columns.length == 0) {
			return null;
		}
		for (ColumnMeta colMeta : tableMeta.getColumns()) {
			if (colMeta.getColName().equalsIgnoreCase(columns[0])) {
				return StringUtil.toLowerOrUpper(colMeta.getColName(), upperOrLower);
			}
		}
		return null;
	}

	/**
	 * 生成分区函数+分区方案前置语句(DROP IF EXISTS保证脚本可重跑),返回方案名。 文件组固定ALL TO
	 * ([PRIMARY]):源方案的文件组映射不在@Partition元数据内,PRIMARY为默认文件组
	 */
	private String wrapPartitionFunctionAndScheme(TableMeta tableMeta,
			org.sagacity.sqltoy.config.model.PartitionMeta partitionMeta, String partitionColumn, String tableName,
			String upperOrLower, StringBuilder tableSql) {
		// 分区键列类型:CREATE PARTITION FUNCTION须声明键类型
		String keyType = null;
		for (ColumnMeta colMeta : tableMeta.getColumns()) {
			if (colMeta.getColName().equalsIgnoreCase(partitionColumn)) {
				keyType = DDLUtils.convertType(colMeta, DBType.SQLSERVER);
				break;
			}
		}
		if (StringUtil.isBlank(keyType)) {
			return null;
		}
		String baseName = (tableName + "_" + partitionColumn).toLowerCase(Locale.ROOT);
		String functionName = StringUtil.toLowerOrUpper("pf_" + baseName, upperOrLower);
		String schemeName = StringUtil.toLowerOrUpper("ps_" + baseName, upperOrLower);
		String direction = partitionMeta.getStrategy().toUpperCase(Locale.ROOT).contains("LEFT") ? "LEFT" : "RIGHT";
		StringBuilder values = new StringBuilder();
		for (String value : partitionMeta.getPartitionValues()) {
			if (StringUtil.isBlank(value)) {
				continue;
			}
			if (values.length() > 0) {
				values.append(", ");
			}
			values.append(DDLUtils.quotePartitionValue(value));
		}
		if (values.length() == 0) {
			return null;
		}
		tableSql.append("IF EXISTS (SELECT 1 FROM sys.partition_schemes WHERE name = '").append(schemeName)
				.append("') DROP PARTITION SCHEME ").append(schemeName).append(";").append(NEWLINE);
		tableSql.append("IF EXISTS (SELECT 1 FROM sys.partition_functions WHERE name = '").append(functionName)
				.append("') DROP PARTITION FUNCTION ").append(functionName).append(";").append(NEWLINE);
		tableSql.append("CREATE PARTITION FUNCTION ").append(functionName).append(" (").append(keyType)
				.append(") AS RANGE ").append(direction).append(" FOR VALUES (").append(values).append(");")
				.append(NEWLINE);
		tableSql.append("CREATE PARTITION SCHEME ").append(schemeName).append(" AS PARTITION ").append(functionName)
				.append(" ALL TO ([PRIMARY]);").append(NEWLINE).append(NEWLINE);
		return schemeName;
	}

	/** 主键列清单(逗号拼接,按输出大小写策略);无主键返回null */
	private String collectPkColumns(TableMeta tableMeta, String upperOrLower) {
		StringBuilder primaryKeys = new StringBuilder();
		for (ColumnMeta colMeta : tableMeta.getColumns()) {
			if (colMeta.isPK()) {
				if (primaryKeys.length() > 0) {
					primaryKeys.append(",");
				}
				primaryKeys.append(StringUtil.toLowerOrUpper(colMeta.getColName(), upperOrLower));
			}
		}
		return primaryKeys.length() == 0 ? null : primaryKeys.toString();
	}

	/** 主键列清单是否包含指定列(大小写不敏感) */
	private boolean containsColumn(TableMeta tableMeta, String pkColumns, String columnName) {
		if (columnName == null) {
			return false;
		}
		for (String pk : pkColumns.split(",")) {
			if (pk.trim().equalsIgnoreCase(columnName)) {
				return true;
			}
		}
		return false;
	}
}
