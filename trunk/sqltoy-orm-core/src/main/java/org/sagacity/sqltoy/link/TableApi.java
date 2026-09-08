package org.sagacity.sqltoy.link;

import java.util.List;

import javax.sql.DataSource;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlType;
import org.sagacity.sqltoy.model.ColumnMeta;
import org.sagacity.sqltoy.model.TableMeta;

/**
 * @project sagacity-sqltoy
 * @description 提供一个获取数据库表信息和操作表信息的TableApi集合
 * @author zhongxuchen
 * @version v1.0,Date:2023-05-05
 * @modify Date:2023-05-05,修改说明
 */
public class TableApi extends BaseLink {

	private static final long serialVersionUID = -6239897514441516513L;

	/**
	 * @param sqlToyContext sqltoy全局上下文对象
	 * @param dataSource    获取表信息绑定的数据源，null表示使用默认数据源
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
	 * 获得表的字段信息
	 * 
	 * @param catalog   表所属的catalog（目录名称），null表示不限制
	 * @param schema    表所属的schema（模式/用户名称），null表示不限制
	 * @param tableName 表名称，支持%通配符模糊匹配
	 * @return 表字段元数据信息集合，包含字段名称、类型、长度、精度等
	 */
	public List<ColumnMeta> getTableColumns(final String catalog, final String schema, final String tableName) {
		return dialectFactory.getTableColumns(sqlToyContext, catalog, schema, tableName, getDataSource(null));
	}

	/**
	 * 获得数据库的表信息
	 * 
	 * @param catalog   表所属的catalog（目录名称），null表示不限制
	 * @param schema    表所属的schema（模式/用户名称），null表示不限制
	 * @param tableName 表名称，支持%通配符模糊匹配
	 * @return 匹配到的表元数据信息集合，包含表名称、表类型、备注等
	 */
	public List<TableMeta> getTables(final String catalog, final String schema, final String tableName) {
		return dialectFactory.getTables(sqlToyContext, catalog, schema, tableName, getDataSource(null));
	}

	/**
	 * 清空表数据(根据pojo来获取实际表名称)
	 * 
	 * @param entityClass 实体类，通过其对象关系映射解析出实际操作的表名称
	 */
	public void truncate(Class entityClass) {
		String tableName = sqlToyContext.getEntityMeta(entityClass).getSchemaTable(null, null);
		String sql = "truncate table ".concat(tableName);
		SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(sql, SqlType.delete, super.getDialect(), null);
		dialectFactory.executeSql(sqlToyContext, sqlToyConfig, null, null, null, getDataSource(null));
	}

	/**
	 * 清空表数据
	 * 
	 * @param tableName 待清空数据的表名称
	 */
	public void truncate(String tableName) {
		SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig("truncate table ".concat(tableName), SqlType.delete,
				super.getDialect(), null);
		dialectFactory.executeSql(sqlToyContext, sqlToyConfig, null, null, null, getDataSource(null));
	}

	/**
	 * 删除表结构
	 * 
	 * @param entityClass 实体类，通过其对象关系映射解析出实际删除的表名称
	 */
	public void drop(Class entityClass) {
		String tableName = sqlToyContext.getEntityMeta(entityClass).getSchemaTable(null, null);
		String sql = "drop table ".concat(tableName);
		SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(sql, SqlType.delete, super.getDialect(), null);
		dialectFactory.executeSql(sqlToyContext, sqlToyConfig, null, null, null, getDataSource(null));
	}

	public void drop(String tableName) {
		SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig("drop table ".concat(tableName), SqlType.delete,
				super.getDialect(), null);
		dialectFactory.executeSql(sqlToyContext, sqlToyConfig, null, null, null, getDataSource(null));
	}

//	/**
//	 * @TODO 创建表
//	 * @param tableMeta
//	 * @param createOrReplace
//	 */
//	public void create(TableMeta tableMeta, boolean createOrReplace) {
//
//	}
}
