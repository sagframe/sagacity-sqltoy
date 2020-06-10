/**
 * 
 */
package org.sagacity.quickvo.utils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.logging.Logger;

import org.sagacity.quickvo.QuickVOConstants;
import org.sagacity.quickvo.model.DataSourceModel;
import org.sagacity.quickvo.model.TableColumnMeta;
import org.sagacity.quickvo.model.TableConstractModel;
import org.sagacity.quickvo.model.TableMeta;
import org.sagacity.quickvo.utils.DBUtil.DbType;
import org.sagacity.quickvo.utils.callback.PreparedStatementResultHandler;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * @project sagacity-quickvo
 * @description quickvo数据库解析
 * @author chenrenfei $<a href="mailto:zhongxuchen@hotmail.com">联系作者</a>$
 * @version $id:DBHelper.java,Revision:v1.0,Date:2010-7-12 下午03:19:16 $
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public class DBHelper {
	/**
	 * 定义全局日志
	 */
	private static Logger logger = LoggerUtil.getLogger();

	/**
	 * 数据库连接
	 */
	private static Connection conn;

	private static DataSourceModel dbConfig = null;

	private static HashMap<String, DataSourceModel> dbMaps = new HashMap<String, DataSourceModel>();

	/**
	 * @todo 加载数据库配置
	 * @param datasouceElts
	 * @throws Exception
	 */
	public static void loadDatasource(NodeList datasouceElts) throws Exception {
		if (datasouceElts == null || datasouceElts.getLength() == 0) {
			logger.info("没有配置相应的数据库");
			throw new Exception("没有配置相应的数据库");
		}
		Element datasouceElt;
		for (int m = 0; m < datasouceElts.getLength(); m++) {
			datasouceElt = (Element) datasouceElts.item(m);
			DataSourceModel dbModel = new DataSourceModel();
			String name = null;
			if (datasouceElt.hasAttribute("name")) {
				name = datasouceElt.getAttribute("name");
			}
			if (datasouceElt.hasAttribute("catalog")) {
				dbModel.setCatalog(QuickVOConstants.replaceConstants(datasouceElt.getAttribute("catalog")));
			}
			if (datasouceElt.hasAttribute("schema")) {
				dbModel.setSchema(QuickVOConstants.replaceConstants(datasouceElt.getAttribute("schema")));
			}
			dbModel.setUrl(QuickVOConstants.replaceConstants(datasouceElt.getAttribute("url")));
			dbModel.setDriver(QuickVOConstants.replaceConstants(datasouceElt.getAttribute("driver")));
			dbModel.setUsername(QuickVOConstants.replaceConstants(datasouceElt.getAttribute("username")));
			dbModel.setPassword(QuickVOConstants.replaceConstants(datasouceElt.getAttribute("password")));
			dbMaps.put(StringUtil.isBlank(name) ? ("" + m) : name, dbModel);
		}
	}

	/**
	 * @todo 获取数据库连接
	 * @param dbName
	 * @return
	 * @throws Exception
	 */
	public static boolean getConnection(String dbName) throws Exception {
		dbConfig = dbMaps.get(StringUtil.isBlank(dbName) ? "0" : dbName);
		if (dbConfig == null && dbMaps.size() == 1 && StringUtil.isBlank(dbName)) {
			dbConfig = dbMaps.values().iterator().next();
		}
		if (dbConfig != null) {
			logger.info("开始连接数据库:" + dbName + ",url:" + dbConfig.getUrl());
			try {
				Class.forName(dbConfig.getDriver());
				conn = DriverManager.getConnection(dbConfig.getUrl(), dbConfig.getUsername(), dbConfig.getPassword());
				return true;
			} catch (ClassNotFoundException cnfe) {
				cnfe.printStackTrace();
				logger.info("数据库驱动未能加载，请在/libs 目录下放入正确的数据库驱动jar包!");
				throw cnfe;
			} catch (SQLException se) {
				logger.info("获取数据库连接失败!");
				throw se;
			}
		}
		return false;
	}

	/**
	 * @todo 关闭数据库并销毁
	 */
	public static void close() {
		try {
			if (conn != null) {
				conn.close();
				conn = null;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * @todo 获取符合条件的表和视图
	 * @param includes
	 * @param excludes
	 * @return
	 * @throws Exception
	 */
	public static List getTableAndView(final String[] includes, final String[] excludes) throws Exception {
		int dbType = DBUtil.getDbType(conn);
		String schema = dbConfig.getSchema();
		String catalog = dbConfig.getCatalog();
		logger.info("提取数据库:schema=[" + schema + "]和 catalog=[" + catalog + "]");
		String[] types = new String[] { "TABLE", "VIEW" };
		PreparedStatement pst = null;
		ResultSet rs = null;
		// 数据库表注释，默认为remarks，不同数据库其名称不一样
		String commentName = "REMARKS";
		// oracle数据库
		if (dbType == DbType.ORACLE || dbType == DbType.ORACLE12) {
			pst = conn.prepareStatement("select * from user_tab_comments");
			rs = pst.executeQuery();
			commentName = "COMMENTS";
		} // mysql数据库
		else if (dbType == DbType.MYSQL) {
			StringBuilder queryStr = new StringBuilder("SELECT TABLE_NAME,TABLE_SCHEMA,TABLE_TYPE,TABLE_COMMENT ");
			queryStr.append(" FROM INFORMATION_SCHEMA.TABLES where 1=1 ");
			if (schema != null) {
				queryStr.append(" and TABLE_SCHEMA='").append(schema).append("'");
			} else if (catalog != null) {
				queryStr.append(" and TABLE_SCHEMA='").append(catalog).append("'");
			}
			if (types != null) {
				queryStr.append(" and (");
				for (int i = 0; i < types.length; i++) {
					if (i > 0) {
						queryStr.append(" or ");
					}
					queryStr.append(" TABLE_TYPE like '%").append(types[i]).append("'");
				}
				queryStr.append(")");
			}
			pst = conn.prepareStatement(queryStr.toString());
			rs = pst.executeQuery();
			commentName = "TABLE_COMMENT";
		} else {
			rs = conn.getMetaData().getTables(catalog, schema, null, types);
		}
		return (List) DBUtil.preparedStatementProcess(commentName, pst, rs, new PreparedStatementResultHandler() {
			public void execute(Object obj, PreparedStatement pst, ResultSet rs) throws Exception {
				List tables = new ArrayList();
				String tableName;
				// 是否包含标识，通过正则表达是判断是否是需要获取的表
				boolean is_include = false;
				String type;
				while (rs.next()) {
					is_include = false;
					tableName = rs.getString("TABLE_NAME");
					if (includes != null && includes.length > 0) {
						for (int i = 0; i < includes.length; i++) {
							if (StringUtil.matches(tableName, includes[i])) {
								is_include = true;
								break;
							}
						}
					} else {
						is_include = true;
					}
					if (excludes != null && excludes.length > 0) {
						for (int j = 0; j < excludes.length; j++) {
							if (StringUtil.matches(tableName, excludes[j])) {
								is_include = false;
								break;
							}
						}
					}
					if (is_include) {
						TableMeta tableMeta = new TableMeta();
						tableMeta.setTableName(tableName);
						tableMeta.setSchema(dbConfig.getSchema());
						// tableMeta.setSchema(rs.getString("TABLE_SCHEMA"));
						type = rs.getString("TABLE_TYPE").toLowerCase();
						if (type.contains("view")) {
							tableMeta.setTableType("VIEW");
						} else {
							tableMeta.setTableType("TABLE");
						}
						tableMeta.setTableRemark(rs.getString(obj.toString()));
						tables.add(tableMeta);
					}
				}
				this.setResult(tables);
			}
		});
	}

	/**
	 * @todo 获取表名的注释
	 * @param tableName
	 * @return
	 * @throws Exception
	 */
	public static String getTableRemark(String tableName) throws Exception {
		final int dbType = DBUtil.getDbType(conn);
		PreparedStatement pst = null;
		ResultSet rs;
		// sybase or sqlserver
		String tableComment = null;
		if (dbType == DbType.SQLSERVER) {
			StringBuilder queryStr = new StringBuilder();
			queryStr.append("select cast(isnull(f.value,'') as varchar(1000)) COMMENTS");
			queryStr.append(" from syscolumns a");
			queryStr.append(" inner join sysobjects d on a.id=d.id and d.xtype='U' and d.name<>'dtproperties'");
			queryStr.append(" left join sys.extended_properties f on d.id=f.major_id and f.minor_id=0");
			queryStr.append(" where a.colorder=1 and d.name=?");
			pst = conn.prepareStatement(queryStr.toString());
			pst.setString(1, tableName);
			rs = pst.executeQuery();
			tableComment = (String) DBUtil.preparedStatementProcess(null, pst, rs,
					new PreparedStatementResultHandler() {
						public void execute(Object obj, PreparedStatement pst, ResultSet rs) throws SQLException {
							while (rs.next()) {
								this.setResult(rs.getString("COMMENTS"));
							}
						}
					});
		}
		return tableComment;
	}

	/**
	 * @todo 获取表的字段信息
	 * @param tableName
	 * @return
	 * @throws Exception
	 */
	public static List getTableColumnMeta(String tableName) throws Exception {
		final int dbType = DBUtil.getDbType(conn);
		PreparedStatement pst = null;
		ResultSet rs;
		HashMap filedsComments = null;
		// sybase or sqlserver
		if (dbType == DbType.SQLSERVER) {
			if (dbType == DbType.SQLSERVER) {
				StringBuilder queryStr = new StringBuilder();
				queryStr.append("SELECT a.name COLUMN_NAME,");
				queryStr.append(" cast(isnull(g.[value],'') as varchar(1000)) as COMMENTS");
				queryStr.append(" FROM syscolumns a");
				queryStr.append(" inner join sysobjects d on a.id=d.id ");
				queryStr.append(" and d.xtype='U' and d.name<>'dtproperties'");
				queryStr.append(" left join syscomments e");
				queryStr.append(" on a.cdefault=e.id");
				queryStr.append(" left join sys.extended_properties g");
				queryStr.append(" on a.id=g.major_id AND a.colid = g.minor_id");
				queryStr.append(" where d.name=?");
				queryStr.append(" order by a.id,a.colorder");
				pst = conn.prepareStatement(queryStr.toString());
				pst.setString(1, tableName);
				rs = pst.executeQuery();
				filedsComments = (HashMap) DBUtil.preparedStatementProcess(null, pst, rs,
						new PreparedStatementResultHandler() {
							public void execute(Object obj, PreparedStatement pst, ResultSet rs) throws SQLException {
								HashMap filedHash = new HashMap();
								while (rs.next()) {
									TableColumnMeta colMeta = new TableColumnMeta();
									colMeta.setColName(rs.getString("COLUMN_NAME"));
									colMeta.setColRemark(rs.getString("COMMENTS"));
									filedHash.put(rs.getString("COLUMN_NAME"), colMeta);
								}
								this.setResult(filedHash);
							}
						});
			}
			String queryStr = "{call sp_columns ('" + tableName + "')}";
			pst = conn.prepareCall(queryStr);
			rs = pst.executeQuery();
			final HashMap metaMap = filedsComments;
			return (List) DBUtil.preparedStatementProcess(null, null, rs, new PreparedStatementResultHandler() {

				public void execute(Object obj, PreparedStatement pst, ResultSet rs) throws SQLException {
					List result = new ArrayList();
					String isAutoIncrement;
					while (rs.next()) {
						TableColumnMeta colMeta;
						if (dbType == DbType.SQLSERVER) {
							if (metaMap == null) {
								colMeta = new TableColumnMeta();
								colMeta.setColName(rs.getString("COLUMN_NAME"));
								colMeta.setColRemark(rs.getString("REMARKS"));
							} else {
								colMeta = (TableColumnMeta) metaMap.get(rs.getString("COLUMN_NAME"));
							}
						} else {
							colMeta = new TableColumnMeta();
						}
						colMeta.setColDefault(clearDefaultValue(StringUtil.trim(rs.getString("column_def"))));
						colMeta.setDataType(rs.getInt("data_type"));
						colMeta.setTypeName(rs.getString("type_name"));
						if (rs.getInt("char_octet_length") != 0) {
							colMeta.setLength(rs.getInt("char_octet_length"));
						} else {
							colMeta.setLength(rs.getInt("precision"));
						}
						colMeta.setPrecision(colMeta.getLength());
						// 字段名称
						colMeta.setColName(rs.getString("column_name"));
						colMeta.setScale(rs.getInt("scale"));
						colMeta.setNumPrecRadix(rs.getInt("radix"));
						try {
							isAutoIncrement = rs.getString("IS_AUTOINCREMENT");
							if (isAutoIncrement != null && (isAutoIncrement.equalsIgnoreCase("true")
									|| isAutoIncrement.equalsIgnoreCase("YES") || isAutoIncrement.equalsIgnoreCase("Y")
									|| isAutoIncrement.equals("1"))) {
								colMeta.setAutoIncrement(true);
							} else {
								colMeta.setAutoIncrement(false);
							}
						} catch (Exception e) {
						}
						if (colMeta.getTypeName().toLowerCase().indexOf("identity") != -1) {
							colMeta.setAutoIncrement(true);
						}
						// 是否可以为空
						if (rs.getInt("nullable") == 1) {
							colMeta.setNullable(true);
						} else {
							colMeta.setNullable(false);
						}
						result.add(colMeta);
					}
					this.setResult(result);
				}
			});
		}

		// oracle 数据库
		if (dbType == DbType.ORACLE || dbType == DbType.ORACLE12) {
			StringBuilder queryStr = new StringBuilder();
			queryStr.append("SELECT t1.*,t2.DATA_DEFAULT FROM (SELECT COLUMN_NAME,COMMENTS");
			queryStr.append("  FROM user_col_comments");
			queryStr.append("  WHERE table_name =?) t1");
			queryStr.append("  LEFT JOIN(SELECT COLUMN_NAME,DATA_DEFAULT");
			queryStr.append("            FROM user_tab_cols");
			queryStr.append("            WHERE table_name =?) t2");
			queryStr.append("  on t1.COLUMN_NAME=t2.COLUMN_NAME");
			pst = conn.prepareStatement(queryStr.toString());
			pst.setString(1, tableName);
			pst.setString(2, tableName);
			rs = pst.executeQuery();
			filedsComments = (HashMap) DBUtil.preparedStatementProcess(null, pst, rs,
					new PreparedStatementResultHandler() {
						public void execute(Object obj, PreparedStatement pst, ResultSet rs) throws SQLException {
							HashMap filedHash = new HashMap();
							while (rs.next()) {
								TableColumnMeta colMeta = new TableColumnMeta();
								colMeta.setColName(rs.getString("COLUMN_NAME"));
								colMeta.setColRemark(rs.getString("COMMENTS"));
								colMeta.setColDefault(StringUtil.trim(rs.getString("DATA_DEFAULT")));
								filedHash.put(rs.getString("COLUMN_NAME"), colMeta);
							}
							this.setResult(filedHash);
						}
					});
		}
		// clickhouse 数据库
		if (dbType == DbType.CLICKHOUSE) {
			StringBuilder queryStr = new StringBuilder();
			queryStr.append(
					"select name COLUMN_NAME,comment COMMENTS,is_in_primary_key PRIMARY_KEY from system.columns t where t.table=?");
			pst = conn.prepareStatement(queryStr.toString());
			pst.setString(1, tableName);
			rs = pst.executeQuery();
			filedsComments = (HashMap) DBUtil.preparedStatementProcess(null, pst, rs,
					new PreparedStatementResultHandler() {
						public void execute(Object obj, PreparedStatement pst, ResultSet rs) throws SQLException {
							HashMap filedHash = new HashMap();
							while (rs.next()) {
								TableColumnMeta colMeta = new TableColumnMeta();
								colMeta.setColName(rs.getString("COLUMN_NAME"));
								colMeta.setColRemark(rs.getString("COMMENTS"));
								// 是否主键
								if (rs.getString("PRIMARY_KEY").equals("1")) {
									colMeta.setIsPrimaryKey(true);
								}
								filedHash.put(rs.getString("COLUMN_NAME"), colMeta);
							}
							this.setResult(filedHash);
						}
					});
		}
		final HashMap metaMap = filedsComments;
		if (dbType == DbType.MYSQL) {
			rs = conn.getMetaData().getColumns(dbConfig.getCatalog(), dbConfig.getSchema(), tableName, "%");
		} else {
			rs = conn.getMetaData().getColumns(dbConfig.getCatalog(), dbConfig.getSchema(), tableName, null);
		}
		return (List) DBUtil.preparedStatementProcess(metaMap, null, rs, new PreparedStatementResultHandler() {

			public void execute(Object obj, PreparedStatement pst, ResultSet rs) throws SQLException {
				List result = new ArrayList();
				String isAutoIncrement;
				while (rs.next()) {
					TableColumnMeta colMeta;
					if (metaMap == null) {
						colMeta = new TableColumnMeta();
						colMeta.setColName(rs.getString("COLUMN_NAME"));
						colMeta.setColDefault(clearDefaultValue(StringUtil.trim(rs.getString("COLUMN_DEF"))));
						colMeta.setColRemark(rs.getString("REMARKS"));
					} else {
						colMeta = (TableColumnMeta) metaMap.get(rs.getString("COLUMN_NAME"));
						if (colMeta.getColDefault() == null) {
							colMeta.setColDefault(clearDefaultValue(StringUtil.trim(rs.getString("COLUMN_DEF"))));
						}
					}
					if (colMeta != null) {
						colMeta.setDataType(rs.getInt("DATA_TYPE"));
						colMeta.setTypeName(rs.getString("TYPE_NAME"));
						colMeta.setLength(rs.getInt("COLUMN_SIZE"));
						colMeta.setPrecision(colMeta.getLength());
						colMeta.setScale(rs.getInt("DECIMAL_DIGITS"));
						colMeta.setNumPrecRadix(rs.getInt("NUM_PREC_RADIX"));
						try {
							isAutoIncrement = rs.getString("IS_AUTOINCREMENT");
							if (isAutoIncrement != null && (isAutoIncrement.equalsIgnoreCase("true")
									|| isAutoIncrement.equalsIgnoreCase("YES") || isAutoIncrement.equalsIgnoreCase("Y")
									|| isAutoIncrement.equals("1"))) {
								colMeta.setAutoIncrement(true);
							} else {
								colMeta.setAutoIncrement(false);
							}
						} catch (Exception e) {
						}
						if (dbType == DbType.ORACLE12) {
							if (colMeta.getColDefault() != null
									&& colMeta.getColDefault().toLowerCase().endsWith(".nextval")) {
								colMeta.setAutoIncrement(true);
								colMeta.setColDefault(colMeta.getColDefault().replaceAll("\"", "\\\\\""));
							}
						}
						if (rs.getInt("NULLABLE") == 1) {
							colMeta.setNullable(true);
						} else {
							colMeta.setNullable(false);
						}
						result.add(colMeta);
					}
				}
				this.setResult(result);
			}

		});
	}

	/**
	 * @todo 处理sqlserver default值为((value))问题
	 * @param defaultValue
	 * @return
	 */
	private static String clearDefaultValue(String defaultValue) {
		if (defaultValue == null)
			return null;
		// 针对postgresql
		if (defaultValue.indexOf("(") != -1 && defaultValue.indexOf(")") != -1 && defaultValue.indexOf("::") != -1) {
			return defaultValue.substring(defaultValue.indexOf("(") + 1, defaultValue.indexOf("::"));
		}
		if (defaultValue.startsWith("((") && defaultValue.endsWith("))")) {
			return defaultValue.substring(2, defaultValue.length() - 2);
		} else if (defaultValue.startsWith("(") && defaultValue.endsWith(")")) {
			return defaultValue.substring(1, defaultValue.length() - 1);
		} else if (defaultValue.startsWith("'") && defaultValue.endsWith("'")) {
			return defaultValue.substring(1, defaultValue.length() - 1);
		} else if (defaultValue.startsWith("\"") && defaultValue.endsWith("\"")) {
			return defaultValue.substring(1, defaultValue.length() - 1);
		}
		return defaultValue;
	}

	/**
	 * @todo <b>获取表的外键信息</b>
	 * @author zhongxuchen
	 * @date 2011-8-15 下午10:48:12
	 * @param tableName
	 * @return
	 * @throws SQLException
	 */
	public static List getTableImpForeignKeys(String tableName) throws Exception {
		ResultSet rs = conn.getMetaData().getImportedKeys(dbConfig.getCatalog(), dbConfig.getSchema(), tableName);
		return (List) DBUtil.preparedStatementProcess(null, null, rs, new PreparedStatementResultHandler() {
			public void execute(Object obj, PreparedStatement pst, ResultSet rs) throws SQLException {
				List result = new ArrayList();
				while (rs.next()) {
					TableConstractModel constractModel = new TableConstractModel();
					constractModel.setFkRefTableName(rs.getString("PKTABLE_NAME"));
					constractModel.setFkColName(rs.getString("FKCOLUMN_NAME"));
					constractModel.setPkColName(rs.getString("PKCOLUMN_NAME"));
					constractModel.setUpdateRule(rs.getInt("UPDATE_RULE"));
					constractModel.setDeleteRule(rs.getInt("DELETE_RULE"));
					result.add(constractModel);
				}
				this.setResult(result);
			}
		});
	}

	/**
	 * @todo 获取表主键被其他表关联的信息(作为其它表的外键)
	 * @param tableName
	 * @return
	 * @throws Exception
	 */
	public static List<TableConstractModel> getTableExportKeys(String tableName) throws Exception {
		ResultSet rs = conn.getMetaData().getExportedKeys(dbConfig.getCatalog(), dbConfig.getSchema(), tableName);
		return (List<TableConstractModel>) DBUtil.preparedStatementProcess(null, null, rs,
				new PreparedStatementResultHandler() {
					public void execute(Object obj, PreparedStatement pst, ResultSet rs) throws SQLException {
						List<TableConstractModel> result = new ArrayList<TableConstractModel>();
						while (rs.next()) {
							TableConstractModel constractModel = new TableConstractModel();
							constractModel.setPkRefTableName(rs.getString("FKTABLE_NAME"));
							constractModel.setPkColName(rs.getString("PKCOLUMN_NAME"));
							constractModel.setPkRefColName(rs.getString("FKCOLUMN_NAME"));
							constractModel.setUpdateRule(rs.getInt("UPDATE_RULE"));
							constractModel.setDeleteRule(rs.getInt("DELETE_RULE"));
							result.add(constractModel);
						}
						this.setResult(result);
					}
				});
	}

	/**
	 * @todo <b>获取表的主键信息</b>
	 * @author zhongxuchen
	 * @date 2011-8-15 下午10:48:01
	 * @param tableName
	 * @return
	 * @throws SQLException
	 */
	public static List getTablePrimaryKeys(String tableName) throws Exception {
		int dbType = DBUtil.getDbType(conn);
		ResultSet rs;
		if (dbType == DbType.CLICKHOUSE) {
			rs = conn.createStatement().executeQuery("select t.name COLUMN_NAME from system.columns t where t.table='"
					+ tableName + "' and t.is_in_primary_key=1");
		} else {
			rs = conn.getMetaData().getPrimaryKeys(dbConfig.getCatalog(), dbConfig.getSchema(), tableName);
		}
		List pkList = (List) DBUtil.preparedStatementProcess(null, null, rs, new PreparedStatementResultHandler() {
			public void execute(Object obj, PreparedStatement pst, ResultSet rs) throws SQLException {
				List result = new ArrayList();
				while (rs.next()) {
					result.add(rs.getString("COLUMN_NAME"));
				}
				this.setResult(result);
			}
		});
		// 排除重复主键约束
		HashSet hashSet = new HashSet(pkList);
		return new ArrayList(hashSet);
	}

	/**
	 * @todo <b>获取表的主键约束名称</b>
	 * @author zhongxuchen
	 * @date 2011-8-15 下午10:48:01
	 * @param tableName
	 * @return
	 * @throws SQLException
	 */
	public static String getTablePKConstraint(String tableName) throws Exception {
		String pkName = null;
		int dbType = DBUtil.getDbType(conn);
		if (dbType == DbType.CLICKHOUSE)
			return pkName;
		try {
			ResultSet rs = conn.getMetaData().getPrimaryKeys(dbConfig.getCatalog(), dbConfig.getSchema(), tableName);
			pkName = (String) DBUtil.preparedStatementProcess(null, null, rs, new PreparedStatementResultHandler() {
				public void execute(Object obj, PreparedStatement pst, ResultSet rs) throws SQLException {
					rs.next();
					this.setResult(rs.getString("PK_NAME"));
				}
			});
		} catch (Exception e) {
			e.printStackTrace();
		}
		return pkName;
	}

	public static int getDBType() throws Exception {
		return DBUtil.getDbType(conn);
	}

	public static String getDBDialect() throws Exception {
		return DBUtil.getCurrentDBDialect(conn);
	}
}
