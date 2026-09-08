package org.sagacity.sqltoy.dialect.utils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.callback.PreparedStatementResultHandler;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.PKStrategy;
import org.sagacity.sqltoy.model.ColumnMeta;
import org.sagacity.sqltoy.model.IgnoreCaseSet;
import org.sagacity.sqltoy.model.LockMode;
import org.sagacity.sqltoy.model.TableMeta;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;
import org.sagacity.sqltoy.utils.SqlUtil;
import org.sagacity.sqltoy.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sagacity-sqltoy
 * @description 提供基于sqlserver这种广泛应用的数据库通用的逻辑处理,避免大量重复代码
 * @author zhongxuchen
 * @version v1.0,Date:2014-12-26
 * @modify Date:2020-02-05 废弃对sqlserver2008 的支持,最低版本为2012版
 */
@SuppressWarnings({ "rawtypes" })
public class SqlServerDialectUtils {
	/**
	 * 定义日志
	 */
	protected final static Logger logger = LoggerFactory.getLogger(SqlServerDialectUtils.class);

	// update 2026-9-5 目标库rowversion(timestamp)列集合缓存:key=连接url|schema.table,元素为列名
	private static ConcurrentHashMap<String, IgnoreCaseSet> rowVersionColumnsCache = new ConcurrentHashMap<String, IgnoreCaseSet>(
			32);

	/**
	 * update 2026-9-5 以目标库元数据校准EntityMeta的rowversion列集合:
	 * sqlserver的timestamp(rowversion)列数据库自动维护不可显式写入,此前按实体侧type==TIMESTAMP判,
	 * oracle等项目迁移场景(quickvo生成实体带显式type=TIMESTAMP,sqlserver表列实为datetime2)会被
	 * 误判跳过导致业务时间列静默丢失;元数据TYPE_NAME=='timestamp'是rowversion在数据库侧的
	 * 唯一权威信号(datetime2/datetime/smalldatetime/datetimeoffset均不同名),每实体仅首次校准,
	 * 元数据查询失败保持未校准(回退历史判据),不阻塞主流程
	 * 
	 * @param sqlToyContext 上下文
	 * @param conn          数据库连接
	 * @param dbType        数据库类型(仅sqlserver校准)
	 * @param tableName     实际表名(可含schema)
	 * @param entityClass   实体类型
	 */
	public static void ensureRowVersionMeta(SqlToyContext sqlToyContext, Connection conn, final Integer dbType,
			final String tableName, Class entityClass) {
		if (conn == null || entityClass == null || dbType == null || dbType.intValue() != DBType.SQLSERVER) {
			return;
		}
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entityClass);
		if (entityMeta.getRowVersionColumns() != null) {
			return;
		}
		String realTable = entityMeta.getSchemaTable(tableName, dbType);
		String cacheKey;
		try {
			cacheKey = conn.getMetaData().getURL() + "|" + realTable;
		} catch (Exception e) {
			cacheKey = realTable;
		}
		IgnoreCaseSet columns = rowVersionColumnsCache.get(cacheKey);
		if (columns == null) {
			columns = new IgnoreCaseSet();
			// 表名含schema时拆分,getColumns按schemaPattern+tableNamePattern匹配
			String schema = null;
			String table = realTable;
			int dot = realTable.indexOf('.');
			if (dot > 0) {
				schema = realTable.substring(0, dot);
				table = realTable.substring(dot + 1);
			}
			try (ResultSet rs = conn.getMetaData().getColumns(null, schema, table, "%")) {
				while (rs.next()) {
					if ("timestamp".equalsIgnoreCase(rs.getString("TYPE_NAME"))) {
						columns.add(rs.getString("COLUMN_NAME"));
					}
				}
			} catch (Exception e) {
				logger.error("query rowversion columns of table {} failed!", realTable, e);
				return;
			}
			rowVersionColumnsCache.put(cacheKey, columns);
		}
		entityMeta.setRowVersionColumns(columns);
	}

	/**
	 * 组织基于sqlserver的锁记录查询sql语句
	 * 
	 * @param loadSql
	 * @param tableName
	 * @param lockMode
	 * @return
	 */
	public static String lockSql(String loadSql, String tableName, LockMode lockMode) {
		// 锁为null直接返回
		if (lockMode == null || SqlUtil.hasLock(loadSql, DBType.SQLSERVER)) {
			return loadSql;
		}
		int fromIndex = SqlUtil.getSymMarkIndexExcludeKeyWords(loadSql, "select\\s+", "\\s+from[\\(\\s+]", 0);
		String selectPart = loadSql.substring(0, fromIndex);
		String fromPart = loadSql.substring(fromIndex);
		String[] sqlChips = fromPart.trim().split("\\s+");
		String realTableName = (tableName == null) ? sqlChips[1] : tableName;
		if (realTableName.indexOf(",") != -1) {
			realTableName = realTableName.substring(0, realTableName.indexOf(","));
		}
		String tmp;
		int chipSize = sqlChips.length;
		String replaceStr = realTableName;
		String regex = realTableName;
		// sqlserver lock 必须在table 后面(如果有别名则在别名后面),这里实现对table和别名位置的查找
		for (int i = 0; i < chipSize; i++) {
			tmp = sqlChips[i];
			if (tmp.toLowerCase(Locale.ROOT).indexOf(realTableName.toLowerCase(Locale.ROOT)) != -1) {
				if (i + 2 < chipSize && "as".equals(sqlChips[i + 1].toLowerCase(Locale.ROOT))) {
					regex = realTableName.concat("\\s+as\\s+").concat(sqlChips[i + 2]);
					replaceStr = realTableName.concat(" as ").concat(sqlChips[i + 2]);
					break;
				} else if (i + 2 < chipSize && "where".equals(sqlChips[i + 2].toLowerCase(Locale.ROOT))) {
					regex = realTableName.concat("\\s+").concat(sqlChips[i + 1]);
					replaceStr = realTableName.concat(" ").concat(sqlChips[i + 1]);
					break;
				} else if (i + 2 < chipSize && ",".equals(sqlChips[i + 2])) {
					regex = realTableName.concat("\\s+").concat(sqlChips[i + 1]).concat(",");
					replaceStr = realTableName.concat(" ").concat(sqlChips[i + 1]);
					break;
				} else if (i + 3 < chipSize && "join".equals(sqlChips[i + 3].toLowerCase(Locale.ROOT))) {
					regex = realTableName.concat("\\s+").concat(sqlChips[i + 1]);
					replaceStr = realTableName.concat(" ").concat(sqlChips[i + 1]);
					break;
				}
			}
		}
		switch (lockMode) {
		case UPGRADE:
			loadSql = selectPart.concat(fromPart.replaceFirst("(?i)".concat(regex), replaceStr.replace(",", "")
					.concat(" with (rowlock xlock) ").concat((regex.endsWith(",") ? "," : ""))));
			break;
		case UPGRADE_NOWAIT:
			// nowait语义是拿不到锁立即报错,不能用readpast(会静默跳过被锁行)
			loadSql = selectPart.concat(fromPart.replaceFirst("(?i)".concat(regex), replaceStr.replace(",", "")
					.concat(" with (rowlock xlock nowait) ").concat((regex.endsWith(",") ? "," : ""))));
			break;
		case UPGRADE_SKIPLOCK:
			loadSql = selectPart.concat(fromPart.replaceFirst("(?i)".concat(regex), replaceStr.replace(",", "")
					.concat(" with (rowlock readpast) ").concat((regex.endsWith(",") ? "," : ""))));
			break;
		}
		return loadSql;
	}

	@SuppressWarnings("unchecked")
	public static List<TableMeta> getTables(String catalog, String schema, String tableName, Connection conn,
			Integer dbType, String dialect) throws Exception {
		String sql = "select d.name TABLE_NAME, cast(isnull(f.value,'') as varchar(1000)) COMMENTS,d.xtype TABLE_TYPE"
				+ " from syscolumns a "
				+ "		 inner join sysobjects d on a.id=d.id and d.xtype in ('U','V') and d.name<>'dtproperties' "
				+ "		 left join sys.extended_properties f on d.id=f.major_id and f.minor_id=0 "
				+ "		 where a.colorder=1 ";
		if (StringUtil.isNotBlank(tableName)) {
			sql = sql.concat(" and d.name like ?");
		}
		PreparedStatement pst = conn.prepareStatement(sql);
		// 设置全局statementTimeout，默认为null
		if (SqlToyConstants.defaultStatementTimeout != null && SqlToyConstants.defaultStatementTimeout > 0) {
			pst.setQueryTimeout(SqlToyConstants.defaultStatementTimeout);
		}
		ResultSet rs = null;
		// 通过preparedStatementProcess反调，第二个参数是pst
		return (List<TableMeta>) SqlUtil.preparedStatementProcess(null, pst, rs, new PreparedStatementResultHandler() {
			@Override
			public void execute(Object rowData, PreparedStatement pst, ResultSet rs) throws Exception {
				try {
					if (StringUtil.isNotBlank(tableName)) {
						if (tableName.contains("%")) {
							pst.setString(1, tableName);
						} else {
							pst.setString(1, "%" + tableName + "%");
						}
					}
					rs = pst.executeQuery();
					List<TableMeta> tables = new ArrayList<TableMeta>();
					while (rs.next()) {
						TableMeta tableMeta = new TableMeta();
						tableMeta.setTableName(rs.getString("TABLE_NAME"));
						tableMeta.setType(rs.getString("TABLE_TYPE"));
						if ("V".equals(tableMeta.getType())) {
							tableMeta.setType("VIEW");
						} else {
							tableMeta.setType("TABLE");
						}
						tableMeta.setRemarks(StringUtil.escapeComment(rs.getString("COMMENTS")));
						tables.add(tableMeta);
					}
					this.setResult(tables);
				} catch (Exception e) {
					throw e;
				} finally {
					if (rs != null) {
						rs.close();
						rs = null;
					}
				}
			}
		});
	}

	@SuppressWarnings("unchecked")
	public static List<ColumnMeta> getTableColumns(String catalog, String schema, String tableName, Connection conn,
			Integer dbType, String dialect) throws Exception {
		List<ColumnMeta> tableColumns = DefaultDialectUtils.getTableColumns(catalog, schema, tableName, conn, dbType,
				dialect);
		String sql = "SELECT a.name COLUMN_NAME,"
				+ "				 cast(isnull(g.[value],'') as varchar(1000)) as COMMENTS "
				+ "				 FROM syscolumns a  inner join sysobjects d on a.id=d.id "
				+ "				 and d.xtype='U' and d.name<>'dtproperties' "
				+ "				 left join syscomments e on a.cdefault=e.id"
				+ "				 left join sys.extended_properties g "
				+ "				 on a.id=g.major_id AND a.colid = g.minor_id   where d.name=? "
				+ "   order by a.id,a.colorder";
		PreparedStatement pst = conn.prepareStatement(sql);
		// 设置全局statementTimeout，默认为null
		if (SqlToyConstants.defaultStatementTimeout != null && SqlToyConstants.defaultStatementTimeout > 0) {
			pst.setQueryTimeout(SqlToyConstants.defaultStatementTimeout);
		}
		ResultSet rs = null;
		// 通过preparedStatementProcess反调，第二个参数是pst
		Map<String, String> colMap = (Map<String, String>) SqlUtil.preparedStatementProcess(null, pst, rs,
				new PreparedStatementResultHandler() {
					@Override
					public void execute(Object rowData, PreparedStatement pst, ResultSet rs) throws Exception {
						pst.setString(1, tableName);
						rs = pst.executeQuery();
						Map<String, String> colComments = new HashMap<String, String>();
						String comment;
						String colName;
						try {
							while (rs.next()) {
								colName = rs.getString("COLUMN_NAME");
								comment = rs.getString("COMMENTS");
								if (colName != null && comment != null) {
									colComments.put(colName.toUpperCase(Locale.ROOT), comment);
								}
							}
							this.setResult(colComments);
						} catch (Exception e) {
							throw e;
						} finally {
							if (rs != null) {
								rs.close();
								rs = null;
							}
						}
					}
				});
		for (ColumnMeta col : tableColumns) {
			col.setComments(colMap.get(col.getColName().toUpperCase(Locale.ROOT)));
		}
		return tableColumns;
	}

	// sqlserver identity主键是不允许写insert table (id,xxx) values (?,?)不能显式体现identity列
	/**
	 * 主键策略是identity或sequence时，主键值允许不由数据库内部自动产生，可人工赋值
	 * 
	 * @param pkStrategy
	 * @return
	 */
	public static boolean allowAssignPKValue(PKStrategy pkStrategy) {
		if (pkStrategy == null) {
			return true;
		}
		if (PKStrategy.SEQUENCE.equals(pkStrategy)) {
			return true;
		}
		if (PKStrategy.IDENTITY.equals(pkStrategy)) {
			return false;
		}
		return true;
	}
}
