/**
 * 
 */
package org.sagacity.sqltoy.plugins.ddl.impl;

import org.sagacity.sqltoy.model.ColumnMeta;
import org.sagacity.sqltoy.model.TableMeta;
import org.sagacity.sqltoy.plugins.ddl.DDLUtils;
import org.sagacity.sqltoy.plugins.ddl.DialectDDLGenerator;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * @project sagacity-sqltoy
 * @description mysql数据库通过POJO生成创建表结构的ddl语句
 * @author zhongxuchen
 * @version v1.0, Date:2023年12月17日
 * @modify 2023年12月17日,修改说明
 */
public class MySqlDDLGenerator implements DialectDDLGenerator {
	private String NEWLINE = "\r\n";
	private String TAB = "   ";

	@Override
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
			tableSql.append(" ").append(DDLUtils.convertType(colMeta, dbType));
			// 是否为null
			if (!colMeta.isNullable()) {
				tableSql.append(" NOT NULL");
			}
			// 自增
			if (colMeta.isAutoIncrement()) {
				tableSql.append(" AUTO_INCREMENT");
			} else if (StringUtil.isNotBlank(colMeta.getDefaultValue())) {
				tableSql.append(" DEFAULT ");
				if (DDLUtils.isNotChar(colMeta.getDataType())) {
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
		// 主键
		DDLUtils.wrapTablePrimaryKeys(tableMeta, upperOrLower, dbType, tableSql);
		// 索引
		DDLUtils.wrapTableIndexes(tableMeta, upperOrLower, dbType, tableSql, false);
		// 外键
		DDLUtils.wrapForeignKeys(tableMeta, upperOrLower, dbType, tableSql, false);
		tableSql.append(NEWLINE);
		tableSql.append(")");
		// 表备注
		if (StringUtil.isNotBlank(tableMeta.getRemarks())) {
			tableSql.append(" COMMENT '").append(tableMeta.getRemarks()).append("'");
		}
		return tableSql.toString();
	}

}
