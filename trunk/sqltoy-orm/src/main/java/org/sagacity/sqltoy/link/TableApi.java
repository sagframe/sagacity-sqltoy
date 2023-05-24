/**
 * 
 */
package org.sagacity.sqltoy.link;

import java.util.List;

import javax.sql.DataSource;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.model.ColumnMeta;
import org.sagacity.sqltoy.model.TableMeta;

/**
 * @project sagacity-sqltoy
 * @description 提供一个获取数据库表信息和操作表信息的TableApi集合
 * @author zhongxuchen
 * @version v1.0, Date:2023年5月5日
 * @modify 2023年5月5日,修改说明
 */
public class TableApi extends BaseLink {

	/**
	 * 
	 */
	private static final long serialVersionUID = -6239897514441516513L;

	/**
	 * @param sqlToyContext
	 * @param dataSource
	 */
	public TableApi(SqlToyContext sqlToyContext, DataSource dataSource) {
		super(sqlToyContext, dataSource);
	}

	public TableApi dataSource(DataSource dataSource) {
		this.dataSource = dataSource;
		this.defaultDataSource = false;
		return this;
	}

	/**
	 * @TODO 获得表的字段信息
	 * @param catalog
	 * @param schema
	 * @param tableName
	 * @return
	 */
	public List<ColumnMeta> getTableColumns(final String catalog, final String schema, final String tableName) {
		return dialectFactory.getTableColumns(sqlToyContext, catalog, schema, tableName, getDataSource(null));
	}

	/**
	 * @TODO 获得数据库的表信息
	 * @param catalog
	 * @param schema
	 * @param tableName
	 * @return
	 */
	public List<TableMeta> getTables(final String catalog, final String schema, final String tableName) {
		return dialectFactory.getTables(sqlToyContext, catalog, schema, tableName, getDataSource(null));
	}

}
