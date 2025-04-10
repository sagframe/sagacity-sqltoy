package org.sagacity.sqltoy.dialect.impl;

import java.sql.Connection;
import java.util.List;

import org.sagacity.sqltoy.model.TableMeta;

/**
 * @project sqltoy-orm
 * @description 提供适配Mogdb数据库方言的实现(以postgresql9.5+为蓝本实现)
 * @author ming
 * @version v1.0,Date:2024-7-2
 * @modify {Date:2024-7-2,初始创建}
 */
public class MogDBDialect extends OpenGaussDialect {
	@Override
	public List<TableMeta> getTables(String catalog, String schema, String tableName, Connection conn, Integer dbType,
			String dialect) throws Exception {
		// TODO Auto-generated method stub
		return super.getTables(catalog, schema, tableName, conn, dbType, dialect);
	}
}
