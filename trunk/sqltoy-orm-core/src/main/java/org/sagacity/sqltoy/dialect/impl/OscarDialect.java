package org.sagacity.sqltoy.dialect.impl;

import java.sql.Connection;
import java.util.List;

import org.sagacity.sqltoy.dialect.utils.DefaultDialectUtils;
import org.sagacity.sqltoy.model.ColumnMeta;

/**
 * @project sqltoy-orm
 * @description 神通数据库适配
 * @author zhongxuchen
 * @version v1.0,Date:2024-10-29
 * @modify {Date:2024-10-29,初始创建}
 */
public class OscarDialect extends OpenGaussDialect {
	@Override
	public List<ColumnMeta> getTableColumns(String catalog, String schema, String tableName, Connection conn,
			Integer dbType, String dialect) throws Exception {
		// gaussdb tableName无需转小写
		return DefaultDialectUtils.getTableColumns(catalog, schema, tableName, conn, dbType, dialect);
	}
}
