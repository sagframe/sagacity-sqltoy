package org.sagacity.sqltoy.plugins.ddl.impl;

import org.sagacity.sqltoy.config.model.MppTableMeta;
import org.sagacity.sqltoy.model.ColumnMeta;
import org.sagacity.sqltoy.model.TableMeta;
import org.sagacity.sqltoy.plugins.ddl.DDLUtils;
import org.sagacity.sqltoy.plugins.ddl.DialectDDLGenerator;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * @project sagacity-sqltoy
 * @description Doris/StarRocks建表语句基类(语法同源):
 *              ENGINE=OLAP {UNIQUE|PRIMARY|DUPLICATE|AGGREGATE} KEY(...)
 *              DISTRIBUTED BY HASH(...) [BUCKETS n] PROPERTIES("k"="v")
 *              COMMENT引号风格由子类决定(Doris单引号,StarRocks双引号)
 * @author zhongxuchen
 * @version v1.0,Date:2026-09-19
 * @modify Date:2026-09-19,修改说明
 */
public abstract class AbstractDorisStarRocksDDLGenerator implements DialectDDLGenerator {
	protected String NEWLINE = "\r\n";
	protected String TAB = "   ";

	/** 注释的引号字符:Doris为' StarRocks为" */
	protected abstract char commentQuote();

	/** Doris/SR VARCHAR上限65533,超长降级STRING(等效TEXT) */
	private String mppType(org.sagacity.sqltoy.model.ColumnMeta colMeta, int dbType) {
		String type = DDLUtils.convertType(colMeta, dbType);
		if (type.startsWith("VARCHAR")) {
			int open = type.indexOf('(');
			int close = type.indexOf(')', open);
			if (open > 0 && close > open) {
				try {
					int len = Integer.parseInt(type.substring(open + 1, close).trim());
					if (len > 65533) {
						return "STRING";
					}
				} catch (NumberFormatException ignore) {
				}
			}
		}
		return type;
	}

	@Override
	public String createTableSql(TableMeta tableMeta, String schema, String upperOrLower, int dbType) {
		if (tableMeta == null) {
			return null;
		}
		char quote = commentQuote();
		StringBuilder tableSql = new StringBuilder();
		String tableName = StringUtil.toLowerOrUpper(tableMeta.getTableName(), upperOrLower);
		tableSql.append("CREATE TABLE ").append(tableName).append(NEWLINE);
		tableSql.append("(").append(NEWLINE);
		int index = 0;
		for (ColumnMeta colMeta : tableMeta.getColumns()) {
			if (index > 0) {
				tableSql.append(",").append(NEWLINE);
			}
			tableSql.append(TAB).append(StringUtil.toLowerOrUpper(colMeta.getColName(), upperOrLower));
			tableSql.append(" ").append(mppType(colMeta, dbType));
			if (!colMeta.isNullable()) {
				tableSql.append(" NOT NULL");
			}
			// 默认值(计算列场景defaultValue为表达式)
			if (colMeta.getGeneratedType() > 0 && StringUtil.isNotBlank(colMeta.getDefaultValue())) {
				tableSql.append(" AS (").append(colMeta.getDefaultValue()).append(")");
			} else if (StringUtil.isNotBlank(colMeta.getDefaultValue())) {
				tableSql.append(" DEFAULT ");
				// 仅日期函数默认值(CURRENT_TIMESTAMP/NOW()等)不加引号:加引号会变成字符串字面量被拒为非法datetime默认值。
				// 数值/字符串/日期字面量仍按方言引号包裹——StarRocks要求数值/日期列默认值为字符串字面量形式,
				// 裸IntLiteral(如DEFAULT 0)会报"Unsupported expr IntLiteral for default value"(doris两者都接受,故统一加引号)
				if (DDLUtils.isDate(colMeta.getDataType())
						&& DDLUtils.isDateFunction(colMeta.getDefaultValue().toUpperCase(java.util.Locale.ROOT))) {
					tableSql.append(colMeta.getDefaultValue());
				} else {
					tableSql.append(quote).append(colMeta.getDefaultValue()).append(quote);
				}
			}
			if (StringUtil.isNotBlank(colMeta.getComments())) {
				tableSql.append(" COMMENT ").append(quote).append(colMeta.getComments()).append(quote);
			}
			index++;
		}
		// 主键
		// OLAP键模型(UNIQUE/PRIMARY/DUPLICATE KEY)即表的主键定义,内联PRIMARY KEY会与之重复导致建表失败
		if (tableMeta.getMppTableMeta() == null || StringUtil.isBlank(tableMeta.getMppTableMeta().getKeyModel())) {
			DDLUtils.wrapTablePrimaryKeys(tableMeta, upperOrLower, dbType, tableSql);
		}
		tableSql.append(NEWLINE);
		tableSql.append(")");
		MppTableMeta mpp = tableMeta.getMppTableMeta();
		if (mpp != null) {
			// ENGINE=OLAP
			if (StringUtil.isNotBlank(mpp.getEngine())) {
				tableSql.append(NEWLINE).append("ENGINE = ").append(mpp.getEngine());
			}
			// 键模型(UNIQUE/PRIMARY/DUPLICATE/AGGREGATE KEY)
			if (StringUtil.isNotBlank(mpp.getKeyModel()) && mpp.getOrderBy() != null && mpp.getOrderBy().length > 0) {
				tableSql.append(NEWLINE).append(mpp.getKeyModel().toUpperCase()).append(" KEY(")
						.append(joinColumns(mpp.getOrderBy(), upperOrLower)).append(")");
			}
		}
		// 表注释(Doris/SR语法:COMMENT须在partition子句之前)
		if (StringUtil.isNotBlank(tableMeta.getRemarks())) {
			tableSql.append(NEWLINE).append("COMMENT ").append(quote).append(tableMeta.getRemarks()).append(quote);
		}
		// 分区子句
		DDLUtils.wrapTablePartition(tableMeta, upperOrLower, dbType, tableSql);

		// DISTRIBUTED BY HASH(...) [BUCKETS n]
		if (mpp != null && mpp.getDistributedBy() != null && mpp.getDistributedBy().length > 0) {
			tableSql.append(NEWLINE).append("DISTRIBUTED BY HASH(")
					.append(joinColumns(mpp.getDistributedBy(), upperOrLower)).append(")");
			if (mpp.getBuckets() > 0) {
				tableSql.append(" BUCKETS ").append(mpp.getBuckets());
			}
		}
		// PROPERTIES("k"="v")
		if (mpp != null && mpp.getProperties() != null && mpp.getProperties().length > 0) {
			tableSql.append(NEWLINE).append("PROPERTIES (");
			for (int i = 0; i < mpp.getProperties().length; i++) {
				if (i > 0) {
					tableSql.append(",");
				}
				String prop = mpp.getProperties()[i];
				int eq = prop.indexOf('=');
				if (eq > 0) {
					tableSql.append(NEWLINE).append(quote).append(prop.substring(0, eq).trim()).append(quote)
							.append(" = ").append(quote).append(prop.substring(eq + 1).trim()).append(quote);
				}
			}
			tableSql.append(NEWLINE).append(")");
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
