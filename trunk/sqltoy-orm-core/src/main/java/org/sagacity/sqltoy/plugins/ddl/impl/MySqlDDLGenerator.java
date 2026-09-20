package org.sagacity.sqltoy.plugins.ddl.impl;

import java.util.Locale;

import org.sagacity.sqltoy.model.ColumnMeta;
import org.sagacity.sqltoy.model.TableMeta;
import org.sagacity.sqltoy.plugins.ddl.DDLUtils;
import org.sagacity.sqltoy.plugins.ddl.DialectDDLGenerator;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * @project sagacity-sqltoy
 * @description mysql数据库通过POJO生成创建表结构的ddl语句
 * @author zhongxuchen
 * @version v1.0,Date:2023-12-17
 * @modify Date:2023-12-17,修改说明
 */
public class MySqlDDLGenerator implements DialectDDLGenerator {
	private String NEWLINE = "\r\n";
	private String TAB = "   ";

	/** 索引输出:跳过与FK约束同名的索引(MySQL的FK自动创建同名索引,重复输出报Duplicate) */
	private void ddlIndexesSkipFk(TableMeta tableMeta, String upperOrLower, int dbType, StringBuilder tableSql) {
		if (tableMeta.getIndexes() == null || tableMeta.getIndexes().isEmpty()) {
			return;
		}
		// 收集FK约束名(小写)
		java.util.Set<String> fkNames = new java.util.HashSet<>();
		if (tableMeta.getForeigns() != null) {
			for (org.sagacity.sqltoy.config.model.ForeignModel fk : tableMeta.getForeigns()) {
				if (fk.getConstraintName() != null) {
					fkNames.add(fk.getConstraintName().toLowerCase());
				}
			}
		}
		for (org.sagacity.sqltoy.config.model.IndexModel indexModel : tableMeta.getIndexes()) {
			String indexName = StringUtil.toLowerOrUpper(indexModel.getName(), upperOrLower);
			// FK同名索引跳过(FK已隐式创建同名索引)
			if (fkNames.contains(indexName.toLowerCase())) {
				continue;
			}
			tableSql.append(",").append(NEWLINE);
			tableSql.append(TAB);
			if (indexModel.isUnique()) {
				tableSql.append("UNIQUE ");
			}
			tableSql.append("KEY ").append(indexName).append(" (");
			String[] cols = indexModel.getColumns();
			for (int i = 0; i < cols.length; i++) {
				if (i > 0) {
					tableSql.append(",");
				}
				tableSql.append(StringUtil.toLowerOrUpper(cols[i], upperOrLower));
			}
			tableSql.append(")");
		}
	}

	private String mysqlType(ColumnMeta colMeta, int dbType) {
		String type = DDLUtils.convertType(colMeta, dbType);
		// utf8mb4字符集下VARCHAR上限16383,超长降级TEXT(mysql无法建VARCHAR(65535))
		if (type.startsWith("VARCHAR")) {
			int open = type.indexOf('(');
			int close = type.indexOf(')', open);
			if (open > 0 && close > open) {
				try {
					int len = Integer.parseInt(type.substring(open + 1, close).trim());
					if (len > 16383) {
						return "TEXT";
					}
				} catch (NumberFormatException ignore) {
				}
			}
		}
		// DATETIME/TIMESTAMP带小数秒精度时列类型须带(n),否则DEFAULT CURRENT_TIMESTAMP(n)不匹配
		if (type.equals("DATETIME") || type.equals("TIMESTAMP")) {
			int prec = colMeta.getDecimalDigits();
			if (prec <= 0 && colMeta.getDefaultValue() != null) {
				java.util.regex.Matcher m = java.util.regex.Pattern
						.compile("(?i)(?:CURRENT_TIMESTAMP|NOW|LOCALTIMESTAMP)\\s*\\(\\s*(\\d)\\s*\\)")
						.matcher(colMeta.getDefaultValue());
				if (m.find()) {
					prec = Integer.parseInt(m.group(1));
				}
			}
			if (prec > 0 && prec <= 6) {
				return type + "(" + prec + ")";
			}
		}
		return type;
	}

	public String createTableSql(TableMeta tableMeta, String schema, String upperOrLower, int dbType) {
		if (tableMeta == null) {
			return null;
		}
		StringBuilder tableSql = new StringBuilder();
		String tableName = StringUtil.toLowerOrUpper(tableMeta.getTableName(), upperOrLower);
		tableSql.append("CREATE TABLE ").append(tableName).append(NEWLINE);
		tableSql.append("(").append(NEWLINE);
		int index = 0;
		for (ColumnMeta colMeta : tableMeta.getColumns()) {
			if (index > 0) {
				tableSql.append(",").append(NEWLINE);
			}
			// 字段名
			tableSql.append(TAB).append(StringUtil.toLowerOrUpper(colMeta.getColName(), upperOrLower));
			// 类型
			tableSql.append(" ").append(mysqlType(colMeta, dbType));
			// 计算列
			if (colMeta.getGeneratedType() > 0 && StringUtil.isNotBlank(colMeta.getDefaultValue())) {
				tableSql.append(" GENERATED ALWAYS AS (").append(colMeta.getDefaultValue()).append(") ");
				tableSql.append(colMeta.getGeneratedType() == 1 ? " VIRTUAL " : " STORED ");
			}
			// 是否为null
			if (!colMeta.isNullable()) {
				tableSql.append(" NOT NULL");
			}
			// 自增
			if (colMeta.isAutoIncrement()) {
				tableSql.append(" AUTO_INCREMENT");
			} else if (colMeta.getGeneratedType() == 0 && StringUtil.isNotBlank(colMeta.getDefaultValue())) {
				// TEXT/BLOB降级列不允许字面量DEFAULT(mysql 8.0.13+须DEFAULT (expr)表达式形式)
				String typeLower = mysqlType(colMeta, dbType);
				if (typeLower.equals("TEXT") || typeLower.equals("MEDIUMTEXT") || typeLower.equals("LONGTEXT")) {
					// skip DEFAULT for TEXT columns
				} else {
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
			}
			// 列注释(单引号已在上游统一转义为'',MySQL字符串中反斜杠是转义符需额外转义)
			if (StringUtil.isNotBlank(colMeta.getComments())) {
				tableSql.append(" COMMENT '").append(colMeta.getComments().replace("\\", "\\\\")).append("'");
			}
			index++;
		}
		// 主键
		DDLUtils.wrapTablePrimaryKeys(tableMeta, upperOrLower, dbType, tableSql);
		// 外键(先于索引:FK自动创建同名索引,若索引也输出会因重名报Duplicate foreign key)
		DDLUtils.wrapForeignKeys(tableMeta, upperOrLower, dbType, tableSql, false);
		// 索引(排除与FK约束同名的索引,避免MySQL重复索引名冲突)
		ddlIndexesSkipFk(tableMeta, upperOrLower, dbType, tableSql);
		tableSql.append(NEWLINE);
		tableSql.append(")");
		// 表备注(MySQL语法要求table options在partition options之前,COMMENT在PARTITION BY前)
		if (StringUtil.isNotBlank(tableMeta.getRemarks())) {
			tableSql.append(" COMMENT '").append(tableMeta.getRemarks().replace("\\", "\\\\")).append("'");
		}
		// 分区子句
		DDLUtils.wrapTablePartition(tableMeta, upperOrLower, dbType, tableSql);
		return tableSql.toString();
	}

}
