package org.sagacity.sqltoy.plugins.ddl.impl;

import java.util.Locale;

import org.sagacity.sqltoy.model.ColumnMeta;
import org.sagacity.sqltoy.model.TableMeta;
import org.sagacity.sqltoy.plugins.ddl.DDLUtils;
import org.sagacity.sqltoy.plugins.ddl.DialectDDLGenerator;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * @project sagacity-sqltoy
 * @description IBM DB2数据库建表DDL: identity用GENERATED ALWAYS AS IDENTITY,
 *              注释走独立COMMENT ON语句,时间默认值CURRENT TIMESTAMP
 * @author zhongxuchen
 * @version v1.0,Date:2026-09-19
 */
public class DB2DDLGenerator implements DialectDDLGenerator {
	private String NEWLINE = "\r\n";
	private String TAB = "   ";

	@Override
	public String createTableSql(TableMeta tableMeta, String schema, String upperOrLower, int dbType) {
		if (tableMeta == null) {
			return null;
		}
		StringBuilder tableSql = new StringBuilder();
		String tableName = StringUtil.toLowerOrUpper(tableMeta.getTableName(), upperOrLower);
		if (StringUtil.isNotBlank(schema)) {
			tableName = schema + "." + tableName;
		}
		tableSql.append("CREATE TABLE ").append(tableName).append(NEWLINE);
		tableSql.append("(").append(NEWLINE);
		int index = 0;
		for (ColumnMeta colMeta : tableMeta.getColumns()) {
			if (index > 0) {
				tableSql.append(",").append(NEWLINE);
			}
			tableSql.append(TAB).append(StringUtil.toLowerOrUpper(colMeta.getColName(), upperOrLower));
			tableSql.append(" ").append(DDLUtils.convertType(colMeta, dbType));
			// 计算列:DB2用GENERATED ALWAYS AS (expression)
			if (colMeta.getGeneratedType() > 0 && StringUtil.isNotBlank(colMeta.getDefaultValue())) {
				tableSql.append(" GENERATED ALWAYS AS (").append(colMeta.getDefaultValue()).append(")");
			}
			// 自增:DB2 identity语法(START WITH/INCREMENT BY)
			else if (colMeta.isAutoIncrement()) {
				tableSql.append(" GENERATED ALWAYS AS IDENTITY (START WITH 1 INCREMENT BY 1)");
			} else if (colMeta.getGeneratedType() == 0 && StringUtil.isNotBlank(colMeta.getDefaultValue())) {
				tableSql.append(" DEFAULT ");
				if (DDLUtils.isNotChar(colMeta.getDataType())) {
					tableSql.append(colMeta.getDefaultValue());
				} else if (DDLUtils.isDate(colMeta.getDataType())
						&& DDLUtils.isDateFunction(
								// DB2 JDBC返回CURRENT TIMESTAMP(空格形态),归一为下划线后再判
								colMeta.getDefaultValue().toUpperCase(Locale.ROOT).replace(" ", "_"))) {
					// DB2时间函数形态: CURRENT TIMESTAMP(带空格)
					tableSql.append(colMeta.getDefaultValue().toUpperCase(Locale.ROOT).replace("CURRENT_TIMESTAMP",
							"CURRENT TIMESTAMP"));
				} else {
					tableSql.append("'").append(colMeta.getDefaultValue()).append("'");
				}
			}
			if (!colMeta.isNullable()) {
				tableSql.append(" NOT NULL");
			}
			index++;
		}
		// 主键
		DDLUtils.wrapTablePrimaryKeys(tableMeta, upperOrLower, dbType, tableSql);
		tableSql.append(NEWLINE).append(")");
		// 分区(DB2需ORGANIZE BY,分区明细以注释形式)
		DDLUtils.wrapTablePartition(tableMeta, upperOrLower, dbType, tableSql);
		// 表和字段注释:DB2走独立COMMENT ON语句
		DDLUtils.wrapTableAndColumnsComment(tableMeta, upperOrLower, dbType, tableSql);
		// 索引
		DDLUtils.wrapTableIndexes(tableMeta, upperOrLower, dbType, tableSql, true);
		// 外键
		DDLUtils.wrapForeignKeys(tableMeta, upperOrLower, dbType, tableSql, true);
		return tableSql.toString();
	}
}
