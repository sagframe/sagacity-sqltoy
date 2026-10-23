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
import java.util.regex.Pattern;

import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.callback.PreparedStatementResultHandler;
import org.sagacity.sqltoy.config.SqlConfigParseUtils;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.PKStrategy;
import org.sagacity.sqltoy.model.ColumnMeta;
import org.sagacity.sqltoy.model.DBProfile;
import org.sagacity.sqltoy.model.IgnoreCaseSet;
import org.sagacity.sqltoy.model.LockMode;
import org.sagacity.sqltoy.model.TableMeta;
import org.sagacity.sqltoy.utils.DataSourceUtils;
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

	// order by 匹配(与SqlServerDialect.ORDER_BY同形)
	private static final Pattern ORDER_BY_PATTERN = Pattern.compile("(?i)\\Worder\\s*by\\W");

	// 表级锁提示:用于判断目标表自身是否已经加锁(避免子查询/其他表上的hint误抑制目标表)
	private static final Pattern TABLE_LOCK_HINT_PATTERN = Pattern
			.compile("(?i)\\b(rowlock|xlock|updlock|holdlock|nolock|readpast|paglock|tablock|tablockx)\\b");

	// update 2026-9-17 offset分页子句匹配(word边界):规避row_offset/offset_id等标识符子串,以及字面量/别名声明的"offset"被误判为已有分页
	// (原contains("offset")子串误判,跳过offset 0 rows追加后派生表内order by报"The ORDER BY clause is invalid..."语法错误)
	private static final Pattern OFFSET_PATTERN = Pattern.compile("(?i)\\boffset\\b");

	/**
	 * update 2026-9-17 派生表内层SQL合法性处理:mssql要求派生表(子查询)内的order by必须伴随
	 * top/offset,否则报"The ORDER BY clause is invalid in views, inline functions,
	 * derived tables, subqueries...";内层存在top级order by且自身无offset分页时,末尾追加offset 0
	 * rows(offset 0不增删行,随机取数等场景最终行序由外层order by决定);已有offset时不可重复追加
	 * (双offset语法错误);无order by的裸union派生表本已合法,且OFFSET语法必须跟随order by,
	 * 同样不可追加。判定经clearDisturbSql掩码字面量并剔除括号内容,规避字面量内'order by'/'offset'及子查询内order
	 * by的干扰;offset判定使用word边界正则,避免row_offset/offset_id等标识符子串误判为已有分页
	 *
	 * @param innerSql 待放入派生表的内层sql
	 * @return 合法化后的内层sql(需追加时末尾带" offset 0 rows")
	 */
	public static String legalizeDerivedInnerSql(String innerSql) {
		String unDisturbSql = DialectUtils.clearDisturbSql(innerSql);
		if (StringUtil.matches(unDisturbSql, ORDER_BY_PATTERN) && !OFFSET_PATTERN.matcher(unDisturbSql).find()) {
			return innerSql + " offset 0 rows";
		}
		return innerSql;
	}

	/**
	 * update 2026-9-12 dbType入径(无连接档案的直调场景):构建最小DBProfile档案后委托核心实现
	 */
	public static void ensureRowVersionMeta(SqlToyContext sqlToyContext, Connection conn, Integer dbType,
			final String tableName, Class entityClass) {
		ensureRowVersionMeta(sqlToyContext, conn,
				new DBProfile(null, DataSourceUtils.getDialect(dbType), dbType, null, 0, null, null, false), tableName,
				entityClass);
	}

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
	 * @param profile       数据库类型(仅sqlserver校准)
	 * @param tableName     实际表名(可含schema)
	 * @param entityClass   实体类型
	 */
	public static void ensureRowVersionMeta(SqlToyContext sqlToyContext, Connection conn, DBProfile profile,
			final String tableName, Class entityClass) {
		Integer dbType = profile.getDbType();
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
		if (lockMode == null) {
			return loadSql;
		}
		// 复用既有注释清理:后续定位基于无注释sql
		String sql = SqlUtil.clearMark(loadSql, true);
		LockTableRef tableRef = findLockTable(sql); // 只看目标表自身是否已有锁提示:子查询/其他join表上的with(nolock)不能抑制目标表加锁
		TableHint existingHint = findTableHint(sql, tableRef);
		if (hasTableLock(sql, tableRef, existingHint)) {
			return loadSql;
		}
		String lockHint;
		String mergeHint;
		switch (lockMode) {
		case UPGRADE:
			lockHint = " with (rowlock xlock)";
			mergeHint = "rowlock, xlock";
			break;
		case UPGRADE_NOWAIT:
			// nowait语义是拿不到锁立即报错,不能用readpast(会静默跳过被锁行)
			lockHint = " with (rowlock xlock nowait)";
			mergeHint = "rowlock, xlock, nowait";
			break;
		case UPGRADE_SKIPLOCK:
			lockHint = " with (rowlock readpast)";
			mergeHint = "rowlock, readpast";
			break;
		default:
			return loadSql;
		}
		// 目标表已有非锁表提示(如with(index(...))):合并到同一with中,不能追加第二个with
		if (existingHint != null) {
			String hintContent = sql.substring(existingHint.start + 1, existingHint.end).trim();
			String merged = hintContent.isEmpty() ? mergeHint : hintContent.concat(", ").concat(mergeHint);
			return sql.substring(0, existingHint.start + 1).concat(merged).concat(sql.substring(existingHint.end));
		}
		// 表提示必须位于表名/别名之后,不能插在别名之前
		int insertIndex = Math.max(tableRef.tableEnd, tableRef.aliasEnd);
		return sql.substring(0, insertIndex).concat(lockHint).concat(sql.substring(insertIndex));
	}

	/**
	 * @description 判断目标表自身是否已带锁提示(with (...)/旧式(nolock)),只检查该表引用范围,
	 *              不复用SqlUtil.hasLock的全SQL扫描,避免子查询或join其他表上的hint导致目标表漏锁
	 */
	private static boolean hasTableLock(String sql, LockTableRef tableRef, TableHint tableHint) {
		// 旧式 from t (nolock):parseTableRef已把旧提示纳入aliasEnd
		if (TABLE_LOCK_HINT_PATTERN.matcher(sql.substring(tableRef.tableEnd, tableRef.aliasEnd)).find()) {
			return true;
		}
		return tableHint != null
				&& TABLE_LOCK_HINT_PATTERN.matcher(sql.substring(tableHint.start, tableHint.end + 1)).find();
	}

	/**
	 * @description 定位目标表后的with (...)表提示,返回括号区间;无提示返回null
	 */
	private static TableHint findTableHint(String sql, LockTableRef tableRef) {
		int n = sql.length();
		int pos = skipWhitespace(sql, tableRef.aliasEnd);
		if (pos >= n) {
			return null;
		}
		int hintStart = -1;
		if (isWordAt(sql, pos, "with")) {
			hintStart = skipWhitespace(sql, pos + 4);
		} else if (sql.charAt(pos) == '(') {
			hintStart = pos;
		}
		if (hintStart < 0 || hintStart >= n || sql.charAt(hintStart) != '(') {
			return null;
		}
		int hintEnd = StringUtil.getSymMarkIndexSkipQuoted("(", ")", sql, hintStart);
		if (hintEnd < hintStart) {
			return null;
		}
		return new TableHint(hintStart, hintEnd);
	}

	/**
	 * @description lock语义只取第一个顶层from后的表(可带schema/方括号/别名);
	 *              CTE/派生表/表值函数等复杂形态不在这里推演,无法直接定位表就明确报错
	 * @param sql 已去注释sql
	 * @return 第一个from后的表引用
	 */
	private static LockTableRef findLockTable(String sql) {
		// 复用字面量掩码,避免select list中的'from t'被当from子句
		String maskedSql = SqlConfigParseUtils.maskLiterals(sql, true);
		int depth = 0;
		int fromIndex = -1;
		for (int i = 0; i < maskedSql.length(); i++) {
			char c = maskedSql.charAt(i);
			if (c == '(') {
				depth++;
			} else if (c == ')') {
				if (depth > 0) {
					depth--;
				}
			} else if (depth == 0 && isWordAt(maskedSql, i, "from")) {
				fromIndex = i;
				break;
			}
		}
		if (fromIndex < 0) {
			throw new IllegalArgumentException("lock query must contain a from clause, lock sql: " + sql);
		}
		int tableStart = skipWhitespace(sql, fromIndex + 4);
		if (tableStart >= sql.length() || sql.charAt(tableStart) == '(') {
			throw new IllegalArgumentException("lock query must reference a table directly, lock sql: " + sql);
		}
		LockTableRef tableRef = parseTableRef(sql, tableStart);
		if (tableRef == null) {
			throw new IllegalArgumentException("cannot determine the table to lock from sql: " + sql);
		}
		return tableRef;
	}

	/**
	 * @description 解析from/join后的表引用,支持schema.table、[schema].[table]、别名(含as)
	 * @return 表引用;表值函数、派生表等处返回null
	 */
	private static LockTableRef parseTableRef(String sql, int start) {
		int n = sql.length();
		int pos = skipWhitespace(sql, start);
		Identifier identifier = readQualifiedIdentifier(sql, pos);
		if (identifier == null) {
			return null;
		}
		int afterTable = skipWhitespace(sql, identifier.end);
		// 未加引号的关键字不是表名(from where等非法sql),避免把where当表加锁
		if (!isQuotedIdentifierStart(sql.charAt(pos)) && isClauseKeyword(identifier.name)) {
			return null;
		}
		// 表值函数:from openjson(...)等没有可加锁的基表;旧式表提示from t (nolock)除外
		if (afterTable < n && sql.charAt(afterTable) == '(') {
			int close = StringUtil.getSymMarkIndexSkipQuoted("(", ")", sql, afterTable);
			if (close > afterTable && TABLE_LOCK_HINT_PATTERN.matcher(sql.substring(afterTable, close + 1)).find()) {
				LockTableRef oldHintRef = new LockTableRef();
				oldHintRef.name = identifier.name;
				oldHintRef.tableEnd = identifier.end;
				oldHintRef.aliasEnd = close + 1;
				return oldHintRef;
			}
			return null;
		}
		int aliasEnd = identifier.end;
		if (isWordAt(sql, afterTable, "as")) {
			int aliasStart = skipWhitespace(sql, afterTable + 2);
			Identifier alias = readIdentifierPart(sql, aliasStart);
			if (alias != null) {
				aliasEnd = alias.end;
			}
		} else if (isAliasStart(sql, afterTable)) {
			int wordEnd = readWordEnd(sql, afterTable);
			String word = sql.substring(afterTable, wordEnd).toLowerCase(Locale.ROOT);
			if (!isClauseKeyword(word)) {
				Identifier alias = readIdentifierPart(sql, afterTable);
				if (alias != null) {
					aliasEnd = alias.end;
				}
			}
		}
		// 别名后的列清单:from t as a(c1,c2),复用StringUtil括号配对
		int columnList = skipWhitespace(sql, aliasEnd);
		if (columnList < n && sql.charAt(columnList) == '(') {
			int close = StringUtil.getSymMarkIndexSkipQuoted("(", ")", sql, columnList);
			if (close > columnList) {
				aliasEnd = close + 1;
			}
		}
		LockTableRef tableRef = new LockTableRef();
		tableRef.name = identifier.name;
		tableRef.tableEnd = identifier.end;
		tableRef.aliasEnd = aliasEnd;
		return tableRef;
	}

	/**
	 * @description 读取schema.table形式的限定标识符,去掉[]/""/``并转小写
	 */
	private static Identifier readQualifiedIdentifier(String sql, int start) {
		int n = sql.length();
		int pos = skipWhitespace(sql, start);
		List<String> parts = new ArrayList<String>();
		int end = pos;
		while (true) {
			Identifier part = readIdentifierPart(sql, pos);
			if (part == null) {
				return null;
			}
			parts.add(part.name);
			end = part.end;
			pos = skipWhitespace(sql, end);
			if (pos >= n || sql.charAt(pos) != '.') {
				break;
			}
			pos++;
		}
		return new Identifier(String.join(".", parts).toLowerCase(Locale.ROOT), end);
	}

	/**
	 * @description 读取单个标识符:普通单词、[方括号]、"双引号"、`反引号`
	 */
	private static Identifier readIdentifierPart(String sql, int start) {
		int n = sql.length();
		if (start >= n) {
			return null;
		}
		char c = sql.charAt(start);
		if (c == '[') {
			StringBuilder value = new StringBuilder();
			int i = start + 1;
			while (i < n) {
				char ch = sql.charAt(i);
				if (ch == ']') {
					if (i + 1 < n && sql.charAt(i + 1) == ']') {
						value.append(']');
						i += 2;
						continue;
					}
					return new Identifier(value.toString(), i + 1);
				}
				value.append(ch);
				i++;
			}
			return null;
		}
		if (c == '"' || c == '`') {
			StringBuilder value = new StringBuilder();
			int i = start + 1;
			while (i < n) {
				char ch = sql.charAt(i);
				if (ch == c) {
					if (i + 1 < n && sql.charAt(i + 1) == c) {
						value.append(c);
						i += 2;
						continue;
					}
					return new Identifier(value.toString(), i + 1);
				}
				value.append(ch);
				i++;
			}
			return null;
		}
		if (!isIdentifierStart(c)) {
			return null;
		}
		int end = readWordEnd(sql, start);
		return new Identifier(sql.substring(start, end), end);
	}

	private static boolean isAliasStart(String sql, int pos) {
		if (pos >= sql.length()) {
			return false;
		}
		char c = sql.charAt(pos);
		return c == '[' || c == '"' || c == '`' || isIdentifierStart(c);
	}

	private static boolean isQuotedIdentifierStart(char c) {
		return c == '[' || c == '"' || c == '`';
	}

	private static boolean isIdentifierStart(char c) {
		return Character.isLetter(c) || c == '_' || c == '@' || c == '#' || c == '$';
	}

	private static boolean isWordChar(char c) {
		return Character.isLetterOrDigit(c) || c == '_' || c == '@' || c == '#' || c == '$';
	}

	private static int readWordEnd(String sql, int start) {
		int i = start;
		while (i < sql.length() && isWordChar(sql.charAt(i))) {
			i++;
		}
		return i;
	}

	private static boolean isWordAt(String sql, int pos, String word) {
		int n = sql.length();
		if (pos < 0 || pos + word.length() > n || !sql.regionMatches(true, pos, word, 0, word.length())) {
			return false;
		}
		if (pos > 0 && isWordChar(sql.charAt(pos - 1))) {
			return false;
		}
		int after = pos + word.length();
		return after >= n || !isWordChar(sql.charAt(after));
	}

	private static boolean isClauseKeyword(String word) {
		switch (word) {
		case "select":
		case "from":
		case "where":
		case "group":
		case "order":
		case "having":
		case "union":
		case "except":
		case "intersect":
		case "join":
		case "inner":
		case "left":
		case "right":
		case "full":
		case "cross":
		case "outer":
		case "on":
		case "for":
		case "option":
		case "tablesample":
		case "pivot":
		case "unpivot":
		case "apply":
		case "with":
			return true;
		default:
			return false;
		}
	}

	private static int skipWhitespace(String sql, int start) {
		int i = start;
		while (i < sql.length() && Character.isWhitespace(sql.charAt(i))) {
			i++;
		}
		return i;
	}

	private static final class Identifier {
		private final String name;
		private final int end;

		private Identifier(String name, int end) {
			this.name = name;
			this.end = end;
		}
	}

	private static final class LockTableRef {
		private String name;
		private int tableEnd;
		private int aliasEnd;
	}

	private static final class TableHint {
		private final int start;
		private final int end;

		private TableHint(int start, int end) {
			this.start = start;
			this.end = end;
		}
	}

	@SuppressWarnings("unchecked")
	public static List<TableMeta> getTables(String catalog, String schema, String tableName, Connection conn,
			DBProfile profile) throws Exception {
		String sql = "select d.name TABLE_NAME, cast(isnull(f.value,'') as nvarchar(1000)) COMMENTS,d.xtype TABLE_TYPE"
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
						// update 2026-9-11 sysobjects.xtype为CHAR(2),视图值'V '带尾随空格,原"V".equals恒不命中致视图被归为TABLE(sqlserver2022实测),trim后判型
						String rawType = rs.getString("TABLE_TYPE");
						if (rawType != null) {
							rawType = rawType.trim();
						}
						if ("V".equals(rawType)) {
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
			DBProfile profile) throws Exception {
		List<ColumnMeta> tableColumns = DefaultDialectUtils.getTableColumns(catalog, schema, tableName, conn, profile);
		String sql = "SELECT a.name COLUMN_NAME,"
				+ "				 cast(isnull(g.[value],'') as nvarchar(1000)) as COMMENTS "
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