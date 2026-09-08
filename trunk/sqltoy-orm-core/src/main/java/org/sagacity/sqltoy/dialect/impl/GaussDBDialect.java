package org.sagacity.sqltoy.dialect.impl;

import java.sql.Connection;
import java.util.List;

import org.sagacity.sqltoy.dialect.utils.DefaultDialectUtils;
import org.sagacity.sqltoy.model.ColumnMeta;

/**
 * @project sagacity-sqltoy
 * @description 提供适配华为guassdb数据库方言的实现(以postgresql9.5+为蓝本实现)
 * @author zhongxuchen
 * @version v1.0,Date:2020-06-09
 * @modify Date:2020-06-09 初始创建
 */
public class GaussDBDialect extends OpenGaussDialect {

	@Override
	public List<ColumnMeta> getTableColumns(String catalog, String schema, String tableName, Connection conn,
			Integer dbType, String dialect) throws Exception {
		// gaussdb tableName无需转小写
		return DefaultDialectUtils.getTableColumns(catalog, schema, tableName, conn, dbType, dialect);
	}
}
