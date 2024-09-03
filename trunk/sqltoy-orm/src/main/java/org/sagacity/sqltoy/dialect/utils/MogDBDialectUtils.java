package org.sagacity.sqltoy.dialect.utils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.sagacity.sqltoy.callback.PreparedStatementResultHandler;
import org.sagacity.sqltoy.config.model.PKStrategy;
import org.sagacity.sqltoy.model.ColumnMeta;
import org.sagacity.sqltoy.model.TableMeta;
import org.sagacity.sqltoy.utils.SqlUtil;

/**
 * @author ming
 * @version v1.0, Date:2024年7月3日
 * @project sagacity-sqltoy
 * @description 提供gaussdb数据库相关的特殊逻辑处理封装
 * @modify 2024年7月3日, 修改说明
 */
public class MogDBDialectUtils {
	/**
	 * @param pkStrategy
	 * @return
	 * @TODO 定义当使用sequence或identity时, 是否允许自定义值(即不通过sequence或identity产生 ， 而是由外部直接赋值)
	 */
	public static boolean isAssignPKValue(PKStrategy pkStrategy) {
		if (pkStrategy == null) {
			return true;
		}
		// sequence
		if (pkStrategy.equals(PKStrategy.SEQUENCE)) {
			return true;
		}
		// postgresql10+ 支持identity
		if (pkStrategy.equals(PKStrategy.IDENTITY)) {
			return true;
		}
		return true;
	}

	/**
	 *
	 * @param catalog
	 * @param schema
	 * @param tableName 小写
	 * @param conn
	 * @param dbType
	 * @param dialect
	 * @return
	 * @throws Exception
	 */
	@SuppressWarnings("unchecked")
	public static List<ColumnMeta> getTableColumns(String catalog, String schema, String tableName, Connection conn,
			Integer dbType, String dialect) throws Exception {
		ResultSet rs = conn.getMetaData().getColumns(catalog, schema, tableName.toLowerCase(), "%");
		// 通过preparedStatementProcess反调，第二个参数是pst
		List<ColumnMeta> tableCols = (List<ColumnMeta>) SqlUtil.preparedStatementProcess(null, null, rs,
				new PreparedStatementResultHandler() {
					@Override
					public void execute(Object rowData, PreparedStatement pst, ResultSet rs) throws Exception {
						List<ColumnMeta> colMetas = new ArrayList<ColumnMeta>();
						String isAutoIncrement;
						ColumnMeta colMeta;
						while (rs.next()) {
							colMeta = new ColumnMeta();
							colMeta.setColName(rs.getString("COLUMN_NAME"));
							colMeta.setDataType(rs.getInt("DATA_TYPE"));
							colMeta.setTypeName(rs.getString("TYPE_NAME"));
							colMeta.setDefaultValue(SqlUtil.clearDefaultValue(rs.getString("COLUMN_DEF")));
							colMeta.setColumnSize(rs.getInt("COLUMN_SIZE"));
							colMeta.setDecimalDigits(rs.getInt("DECIMAL_DIGITS"));
							colMeta.setNumPrecRadix(rs.getInt("NUM_PREC_RADIX"));
							colMeta.setComments(rs.getString("REMARKS"));
							colMeta.setAutoIncrement(false);
							isAutoIncrement = rs.getString("IS_AUTOINCREMENT");
							try {
								if (("true".equalsIgnoreCase(isAutoIncrement) || "YES".equalsIgnoreCase(isAutoIncrement)
										|| "Y".equalsIgnoreCase(isAutoIncrement) || "1".equals(isAutoIncrement))) {
									colMeta.setAutoIncrement(true);
								}
							} catch (Exception ignore) {
							}
							colMeta.setNullable(rs.getInt("NULLABLE") == 1);
							colMetas.add(colMeta);
						}
						this.setResult(colMetas);
					}
				});
		ColumnMeta mapMeta;
		// 获取主键信息
		Map<String, ColumnMeta> pkMap = DefaultDialectUtils.getTablePrimaryKeys(catalog, schema, tableName, conn,
				dbType, dialect);
		if (pkMap != null && !pkMap.isEmpty()) {
			for (ColumnMeta colMeta : tableCols) {
				mapMeta = pkMap.get(colMeta.getColName());
				if (mapMeta != null) {
					colMeta.setPK(true);
				}
			}
		}
		// 获取索引信息
		Map<String, ColumnMeta> indexsMap = DefaultDialectUtils.getTableIndexes(catalog, schema, tableName, conn,
				dbType, dialect);
		if (indexsMap != null && !indexsMap.isEmpty()) {
			for (ColumnMeta colMeta : tableCols) {
				mapMeta = indexsMap.get(colMeta.getColName());
				if (mapMeta != null) {
					colMeta.setIndexName(mapMeta.getIndexName());
					colMeta.setUnique(mapMeta.isUnique());
					colMeta.setIndex(true);
				}
			}
		}
		return tableCols;
	}

	/**
	 * @param catalog
	 * @param schema
	 * @param tableName 要小写
	 * @param conn
	 * @param dbType
	 * @param dialect
	 * @return
	 * @throws Exception
	 */
	public static List<TableMeta> getTables(String catalog, String schema, String tableName, Connection conn,
			Integer dbType, String dialect) throws Exception {
		// 可自定义 PreparedStatement pst=conn.xxx;
		ResultSet rs = conn.getMetaData().getTables(catalog, schema, tableName.toLowerCase(),
				new String[] { "TABLE", "VIEW" });
		// 通过preparedStatementProcess反调，第二个参数是pst
		return (List<TableMeta>) SqlUtil.preparedStatementProcess(null, null, rs, new PreparedStatementResultHandler() {
			@Override
			public void execute(Object rowData, PreparedStatement pst, ResultSet rs) throws Exception {
				List<TableMeta> tables = new ArrayList<TableMeta>();
				while (rs.next()) {
					TableMeta tableMeta = new TableMeta();
					tableMeta.setTableName(rs.getString("TABLE_NAME"));
					tableMeta.setSchema(rs.getString("TABLE_SCHEM"));
					tableMeta.setType(rs.getString("TABLE_TYPE"));
					tableMeta.setRemarks(rs.getString("REMARKS"));
					tables.add(tableMeta);
				}
				this.setResult(tables);
			}
		});
	}
}
