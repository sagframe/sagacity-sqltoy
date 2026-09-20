package org.sagacity.sqltoy.plugins.ddl.impl;

import org.sagacity.sqltoy.config.model.MppTableMeta;
import org.sagacity.sqltoy.model.ColumnMeta;
import org.sagacity.sqltoy.model.TableMeta;
import org.sagacity.sqltoy.plugins.ddl.DDLUtils;
import org.sagacity.sqltoy.plugins.ddl.DialectDDLGenerator;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * @project sagacity-sqltoy
 * @description ClickHouse数据库通过POJO生成创建表结构的ddl语句:
 *              ENGINE=xxx[(args)] ORDER BY(...) PARTITION BY ... SETTINGS k=v
 * @author zhongxuchen
 * @version v1.0,Date:2026-09-19
 * @modify Date:2026-09-19,修改说明
 */
public class ClickHouseDDLGenerator implements DialectDDLGenerator {
	private String NEWLINE = "\r\n";
	private String TAB = "   ";

	@Override
	public String createTableSql(TableMeta tableMeta, String schema, String upperOrLower, int dbType) {
		if (tableMeta == null) {
			return null;
		}
		// ClickHouse不允许CREATE TABLE ... ENGINE=View(视图需CREATE VIEW,实体元数据无select定义无法还原),跳过
		if (tableMeta.getMppTableMeta() != null && "View".equalsIgnoreCase(tableMeta.getMppTableMeta().getEngine())) {
			return null;
		}
		StringBuilder tableSql = new StringBuilder();
		// CK标识符大小写敏感:列名/表名/表达式保持原文(不转lower/upper),与ALIAS/MATERIALIZED/PARTITION BY引用一致
		String tableName = tableMeta.getTableName();
		tableSql.append("CREATE TABLE ").append(tableName).append(NEWLINE);
		tableSql.append("(").append(NEWLINE);
		int index = 0;
		for (ColumnMeta colMeta : tableMeta.getColumns()) {
			if (index > 0) {
				tableSql.append(",").append(NEWLINE);
			}
			tableSql.append(TAB).append(colMeta.getColName());
			tableSql.append(" ").append(DDLUtils.convertType(colMeta, dbType));
			if (!colMeta.isNullable()) {
				tableSql.append(" NOT NULL");
			}
			// 计算列:CK用ALIAS(虚拟)/MATERIALIZED(物化)表达,defaultValue承载表达式
			if (colMeta.getGeneratedType() == 1 && StringUtil.isNotBlank(colMeta.getDefaultValue())) {
				tableSql.append(" ALIAS ").append(colMeta.getDefaultValue());
			} else if (colMeta.getGeneratedType() == 2 && StringUtil.isNotBlank(colMeta.getDefaultValue())) {
				tableSql.append(" MATERIALIZED ").append(colMeta.getDefaultValue());
			}
			// 默认值:字符型字面量需引号,数值/日期函数原样(对齐MySql生成器规则)
			else if (colMeta.getGeneratedType() == 0 && StringUtil.isNotBlank(colMeta.getDefaultValue())) {
				tableSql.append(" DEFAULT ");
				if (DDLUtils.isNotChar(colMeta.getDataType())) {
					tableSql.append(colMeta.getDefaultValue());
				} else if (DDLUtils.isDate(colMeta.getDataType())
						&& DDLUtils.isDateFunction(colMeta.getDefaultValue().toUpperCase(java.util.Locale.ROOT))) {
					tableSql.append(colMeta.getDefaultValue());
				} else {
					tableSql.append("'").append(colMeta.getDefaultValue()).append("'");
				}
			}
			// 列注释
			if (StringUtil.isNotBlank(colMeta.getComments())) {
				tableSql.append(" COMMENT '").append(colMeta.getComments()).append("'");
			}
			index++;
		}
		// 主键(clickhouse为主键索引声明,非约束);列名保持原文与列定义一致(CK大小写敏感)
		DDLUtils.wrapTablePrimaryKeys(tableMeta, null, dbType, tableSql);
		tableSql.append(NEWLINE);
		tableSql.append(")");
		// 表引擎元数据
		MppTableMeta mpp = tableMeta.getMppTableMeta();
		if (mpp != null && StringUtil.isNotBlank(mpp.getEngine())) {
			tableSql.append(NEWLINE).append("ENGINE = ").append(mpp.getEngine());
			if (StringUtil.isNotBlank(mpp.getEngineArgs())) {
				tableSql.append("(").append(mpp.getEngineArgs()).append(")");
			}
			if (mpp.getOrderBy() != null && mpp.getOrderBy().length > 0) {
				tableSql.append(NEWLINE).append("ORDER BY (").append(String.join(",", mpp.getOrderBy())).append(")");
			}
		}
		// 分区子句:CK直接PARTITION BY expression(无RANGE/LIST关键字),列名保持原文
		org.sagacity.sqltoy.config.model.PartitionMeta pm = tableMeta.getPartitionMeta();
		if (pm != null && StringUtil.isNotBlank(pm.getExpression())) {
			tableSql.append(" PARTITION BY ").append(pm.getExpression().trim());
		} else if (pm != null && pm.getColumns() != null && pm.getColumns().length > 0) {
			tableSql.append(" PARTITION BY ").append(String.join(",", pm.getColumns()));
		}
		// SETTINGS
		if (mpp != null && mpp.getProperties() != null && mpp.getProperties().length > 0) {
			tableSql.append(NEWLINE).append("SETTINGS ").append(String.join(", ", mpp.getProperties()));
		}
		// 表注释
		if (StringUtil.isNotBlank(tableMeta.getRemarks())) {
			tableSql.append(NEWLINE).append("COMMENT '").append(tableMeta.getRemarks()).append("'");
		}
		return tableSql.toString();
	}

	private String joinColumns(String[] columns, String upperOrLower) {
		StringBuilder result = new StringBuilder();
		for (int i = 0; i < columns.length; i++) {
			if (i > 0) {
				result.append(",");
			}
			result.append(StringUtil.toLowerOrUpper(columns[i], upperOrLower));
		}
		return result.toString();
	}
}
