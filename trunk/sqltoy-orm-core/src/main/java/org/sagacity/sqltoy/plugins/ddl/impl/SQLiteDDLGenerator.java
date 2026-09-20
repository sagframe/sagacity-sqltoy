package org.sagacity.sqltoy.plugins.ddl.impl;

import java.util.Locale;

import org.sagacity.sqltoy.model.ColumnMeta;
import org.sagacity.sqltoy.model.TableMeta;
import org.sagacity.sqltoy.plugins.ddl.DDLUtils;
import org.sagacity.sqltoy.plugins.ddl.DialectDDLGenerator;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * @project sagacity-sqltoy
 * @description SQLite数据库建表DDL: 类型亲和(TEXT/INTEGER/REAL/BLOB/NUMERIC),
 *              无独立COMMENT语法(备注以行注释形式附加),自增用AUTOINCREMENT
 * @author zhongxuchen
 * @version v1.0,Date:2026-09-19
 */
public class SQLiteDDLGenerator implements DialectDDLGenerator {
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
			// SQLite类型亲和:VARCHAR(n)映射TEXT(SQLite不强制长度,超大VARCHAR无意义)
			String colType = DDLUtils.convertType(colMeta, dbType);
			if (colType.startsWith("VARCHAR") || colType.startsWith("CHARACTER")) {
				colType = "TEXT";
			}
			// SQLite的AUTOINCREMENT硬性要求列类型恰为INTEGER(BIGINT等会被拒),自增列强制INTEGER
			if (colMeta.isAutoIncrement()) {
				colType = "INTEGER";
			}
			tableSql.append(" ").append(colType);
			// 自增:SQLite要求INTEGER PRIMARY KEY AUTOINCREMENT
			if (colMeta.isAutoIncrement()) {
				tableSql.append(" PRIMARY KEY AUTOINCREMENT");
			} else {
				if (!colMeta.isNullable()) {
					tableSql.append(" NOT NULL");
				}
				// 默认值:SQLite仅支持CURRENT_TIMESTAMP/字面量,datetime()等函数不支持
				if (colMeta.getGeneratedType() == 0 && StringUtil.isNotBlank(colMeta.getDefaultValue())) {
					String dv = colMeta.getDefaultValue().trim();
					if (dv.toLowerCase(Locale.ROOT).startsWith("current_timestamp")
							|| dv.toLowerCase(Locale.ROOT).startsWith("datetime")
							|| dv.toLowerCase(Locale.ROOT).startsWith("now")) {
						tableSql.append(" DEFAULT CURRENT_TIMESTAMP");
					} else if (DDLUtils.isNotChar(colMeta.getDataType())) {
						tableSql.append(" DEFAULT ").append(dv);
					} else {
						tableSql.append(" DEFAULT '").append(dv).append("'");
					}
				}
			}
			// SQLite无COMMENT语法,以行注释附加备注
			if (StringUtil.isNotBlank(colMeta.getComments())) {
				tableSql.append(" -- ").append(colMeta.getComments());
			}
			index++;
		}
		// 主键(非autoIncrement的复合主键)
		if (!hasAutoIncrementPk(tableMeta)) {
			DDLUtils.wrapTablePrimaryKeys(tableMeta, upperOrLower, dbType, tableSql);
		}
		tableSql.append(NEWLINE).append(")");
		// 索引:SQLite支持独立CREATE INDEX语句;wrapTableIndexes(outerTable=true)以";"前导逐条生成,
		// 其前导";"同时终止CREATE TABLE(修复历史缺陷:此前未输出索引,唯一约束/普通索引全丢)。
		// 末尾不再补";"——DDLFactory会在表与表之间统一插入";"分隔符,自行补会产生");;"空语句(对齐PG/mysql生成器约定)
		DDLUtils.wrapTableIndexes(tableMeta, upperOrLower, dbType, tableSql, true);
		// SQLite FK需在表定义内且PRAGMA foreign_keys=ON才生效;简化为注释形式
		// (外键约束名CONSTRAINT xxx在SQLite的CREATE TABLE尾部语法敏感,降级为注释)
		if (tableMeta.getForeigns() != null && !tableMeta.getForeigns().isEmpty()) {
			for (org.sagacity.sqltoy.config.model.ForeignModel fk : tableMeta.getForeigns()) {
				tableSql.append(NEWLINE).append("-- FOREIGN KEY (").append(String.join(",", fk.getColumns()))
						.append(") REFERENCES ").append(fk.getForeignTable()).append("(")
						.append(String.join(",", fk.getForeignColumns())).append(")");
			}
		}
		// 表注释(SQLite无COMMENT语法,以注释形式)
		if (StringUtil.isNotBlank(tableMeta.getRemarks())) {
			tableSql.append(NEWLINE).append("-- ").append(tableName).append(": ").append(tableMeta.getRemarks());
		}
		return tableSql.toString();
	}

	private boolean hasAutoIncrementPk(TableMeta tableMeta) {
		for (ColumnMeta col : tableMeta.getColumns()) {
			if (col.isAutoIncrement()) {
				return true;
			}
		}
		return false;
	}
}
