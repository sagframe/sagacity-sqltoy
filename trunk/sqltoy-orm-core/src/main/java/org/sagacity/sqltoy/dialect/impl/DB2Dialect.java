package org.sagacity.sqltoy.dialect.impl;

import java.io.Serializable;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.callback.DecryptHandler;
import org.sagacity.sqltoy.callback.GenerateSavePKStrategy;
import org.sagacity.sqltoy.callback.GenerateSqlHandler;
import org.sagacity.sqltoy.callback.ReflectPropsHandler;
import org.sagacity.sqltoy.callback.UpdateRowCallback;
import org.sagacity.sqltoy.callback.UpdateRowHandler;
import org.sagacity.sqltoy.config.SqlConfigParseUtils;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.OperateType;
import org.sagacity.sqltoy.config.model.PKStrategy;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlType;
import org.sagacity.sqltoy.dialect.Dialect;
import org.sagacity.sqltoy.dialect.utils.DB2DialectUtils;
import org.sagacity.sqltoy.dialect.utils.DefaultDialectUtils;
import org.sagacity.sqltoy.dialect.utils.DialectExtUtils;
import org.sagacity.sqltoy.dialect.utils.DialectUtils;
import org.sagacity.sqltoy.model.ColumnMeta;
import org.sagacity.sqltoy.model.DBProfile;
import org.sagacity.sqltoy.model.LockMode;
import org.sagacity.sqltoy.model.QueryExecutor;
import org.sagacity.sqltoy.model.QueryResult;
import org.sagacity.sqltoy.model.SavePKStrategy;
import org.sagacity.sqltoy.model.StoreResult;
import org.sagacity.sqltoy.model.TableMeta;
import org.sagacity.sqltoy.model.inner.QueryExecutorExtend;
import org.sagacity.sqltoy.utils.SqlUtil;
import org.sagacity.sqltoy.utils.SqlUtilsExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sagacity-sqltoy
 * @description db2 10.x版本支持limit offset分页机制
 * @author zhongxuchen
 * @version v1.0,Date:2015-02-28
 */
@SuppressWarnings({ "rawtypes" })
public class DB2Dialect implements Dialect {
	/**
	 * 定义日志
	 */
	protected final Logger logger = LoggerFactory.getLogger(DB2Dialect.class);

	/**
	 * db2 脏读sql查询语句正则表达式
	 */
	public static final Pattern DB2_QUERY_UR_PATTERN = Pattern.compile("(?i)\\s+with\\s+ur\\s*$");

	public static final String DB2_QUERY_APPEND = " with ur";

	public static final String NEXT_VAL = "NEXTVAL FOR ";

	/**
	 * 虚表
	 */
	public static final String VIRTUAL_TABLE = "SYSIBM.SYSDUMMY1";

	/**
	 * update 2026-11-10 无FROM的常量/表达式查询(如自定义countSql "select 99")补虚表并追加隔离后缀
	 *
	 * <p>
	 * 判定要点:
	 * <ol>
	 * <li>在字面量掩码串上判FROM,避免 {@code select 'a from b'}
	 * 把字面量里的from当成已有FROM(真库实测漏补即报错)</li>
	 * <li>只认<b>顶层</b>(括号深度0)的FROM,避免 {@code select (select max(id) from t)}
	 * 因子查询内的FROM漏补虚表 (顶层无FROM在DB2同样是SQL0104)</li>
	 * <li>先摘出已有的"with ur"尾缀再补虚表,最后只追加一次UR,避免 {@code select 99 with ur} 拼成
	 * {@code select 99 with ur FROM SYSIBM.SYSDUMMY1 with ur}</li>
	 * <li>VALUES形式查询({@code values (1)})本身合法且无需FROM,补虚表反而SQL0104,故排除</li>
	 * </ol>
	 * 注:不处理尾分号——sqltoy的sql配置/查询链路不携带语句终止符,为不存在的输入做防御属过度设计
	 */
	private String appendWithUR(String sql) {
		// update 2026-11-12 先掩双引号定界标识符再掩单引号字面量:DB2的"..."内容是标识符,
		// select 1 as "from"中的from被hasTopLevelFrom误判为顶层FROM而漏补虚表(真库SQL0104实锤),
		// 尾部字样藏在双引号标识符内的" with ur"同理会被误摘。双引号先掩:标识符内的'不会再干扰字面量配对
		String maskedSql = SqlConfigParseUtils.maskLiterals(maskQuotedIdentifiers(sql), false);
		String body = sql;
		String urSuffix = "";
		// 掩码串与原串等长,匹配位置可直接用于原串截取
		Matcher urMatcher = DB2_QUERY_UR_PATTERN.matcher(maskedSql);
		if (urMatcher.find()) {
			body = sql.substring(0, urMatcher.start());
			urSuffix = sql.substring(urMatcher.start());
			maskedSql = maskedSql.substring(0, urMatcher.start());
		}
		String legalSql = body;
		if (!isValuesQuery(maskedSql) && !hasTopLevelFrom(maskedSql)) {
			legalSql = body + " FROM " + VIRTUAL_TABLE;
		}
		if (urSuffix.isEmpty() && SqlToyConstants.db2WithUR()) {
			urSuffix = DB2_QUERY_APPEND;
		}
		return legalSql + urSuffix;
	}

	/**
	 * @param maskedSql 字面量已掩码的sql
	 * @return 是否为VALUES形式查询(首词为values)。这类查询无需FROM,不能补虚表
	 */
	private static boolean isValuesQuery(String maskedSql) {
		int i = 0;
		int length = maskedSql.length();
		while (i < length && Character.isWhitespace(maskedSql.charAt(i))) {
			i++;
		}
		return i + 6 <= length && maskedSql.regionMatches(true, i, "values", 0, 6)
				&& (i + 6 == length || !isIdentifierChar(maskedSql.charAt(i + 6)));
	}

	/**
	 * @param maskedSql 字面量已掩码的sql(掩码后与原文等长,位置偏移一致)
	 * @return 是否存在顶层(括号深度为0)的FROM子句
	 *
	 *         <p>
	 *         注释无需在此处理:sql在解析期已由{@code SqlUtil.clearMark}剔除--与块注释,
	 *         到达方言层的sql不含注释(字面量内的注释标记亦原样保留于字面量,已被maskLiterals掩码)
	 */
	private static boolean hasTopLevelFrom(String maskedSql) {
		int depth = 0;
		int length = maskedSql.length();
		for (int i = 0; i + 4 <= length; i++) {
			char c = maskedSql.charAt(i);
			if (c == '(') {
				depth++;
				continue;
			}
			if (c == ')') {
				if (depth > 0) {
					depth--;
				}
				continue;
			}
			if (depth != 0 || (c != 'f' && c != 'F') || !maskedSql.regionMatches(true, i, "from", 0, 4)) {
				continue;
			}
			// 词边界:from前/后都不能是标识符字符(排除fromx、afrom等)
			if ((i == 0 || !isIdentifierChar(maskedSql.charAt(i - 1)))
					&& (i + 4 == length || !isIdentifierChar(maskedSql.charAt(i + 4)))) {
				return true;
			}
		}
		return false;
	}

	private static boolean isIdentifierChar(char c) {
		return Character.isLetterOrDigit(c) || c == '_' || c == '$' || c == '#';
	}

	/**
	 * 掩码双引号定界标识符:整段(含定界引号)置为空格,与原串等长保持位置偏移一致。
	 * ""成对转义引号属标识符内容一并掩掉;单引号字符串字面量内的双引号是普通字符,
	 * 必须整体跳过(否则会把字面量之后的FROM/with ur误吞,真库实测SQL0104);不含双引号时原样返回,零开销
	 *
	 * @param sql 原始sql
	 * @return 双引号标识符被掩码的等长sql
	 */
	private static String maskQuotedIdentifiers(String sql) {
		if (sql.indexOf('"') == -1) {
			return sql;
		}
		char[] chars = sql.toCharArray();
		int length = chars.length;
		boolean inQuote = false;
		// update 2026-11-12 必须从0开始扫描:首个双引号可能位于单引号字面量内部(如 select '"' from t),
		// 只从首个"位置起扫会把字面量内的"当成标识符定界,吞掉后续FROM
		for (int i = 0; i < length; i++) {
			char c = chars[i];
			if (!inQuote) {
				// 单引号字面量整体跳过(含''转义),交由maskLiterals统一掩码
				if (c == '\'') {
					i = skipStringLiteral(chars, i);
					continue;
				}
				if (c == '"') {
					inQuote = true;
					chars[i] = ' ';
				}
				continue;
			}
			// ""成对转义:两引号均为标识符内容,一并掩掉
			if (c == '"' && i + 1 < length && chars[i + 1] == '"') {
				chars[i] = ' ';
				chars[i + 1] = ' ';
				i++;
				continue;
			}
			chars[i] = ' ';
			if (c == '"') {
				inQuote = false;
			}
		}
		return new String(chars);
	}

	/**
	 * @param chars 字符数组
	 * @param start 起始单引号下标
	 * @return 字符串字面量结束单引号下标(含''转义处理);未闭合返回末尾下标
	 */
	private static int skipStringLiteral(char[] chars, int start) {
		int length = chars.length;
		for (int i = start + 1; i < length; i++) {
			if (chars[i] == '\'') {
				if (i + 1 < length && chars[i + 1] == '\'') {
					i++;
					continue;
				}
				return i;
			}
		}
		return length - 1;
	}

	@Override
	public boolean isUnique(final SqlToyContext sqlToyContext, Serializable entity, String[] paramsNamed,
			Connection conn, DBProfile profile, final String tableName, final Integer queryTimeout) {
		return DialectUtils.isUnique(sqlToyContext, entity, paramsNamed, conn, profile, tableName,
				(entityMeta, realParamNamed, table, topSize) -> {
					String queryStr = DialectExtUtils.wrapUniqueSql(entityMeta, realParamNamed, profile, table);
					return queryStr + " limit " + topSize;
				}, queryTimeout);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.sagacity.sqltoy.dialect.Dialect#load(org.sagacity.sqltoy.SqlToyContext,
	 * java.io.Serializable, java.util.List, org.sagacity.sqltoy.model.LockMode,
	 * java.sql.Connection, java.lang.Integer, java.lang.String, java.lang.String)
	 */
	@Override
	public Serializable load(final SqlToyContext sqlToyContext, Serializable entity, boolean onlySubTables,
			List<Class> cascadeTypes, LockMode lockMode, int lockWaitTimeout, Connection conn, DBProfile profile,
			final String tableName, final Integer queryTimeout) throws Exception {
		Integer dbType = profile.getDbType();
		String dialect = profile.getDialect();
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entity.getClass());
		// 获取loadsql(loadsql 可以通过@loadSql进行改变，所以需要sqltoyContext重新获取)
		SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(entityMeta.getLoadSql(tableName), SqlType.search,
				dialect, null);
		String loadSql = sqlToyConfig.getSql(dialect);
		loadSql = loadSql.concat(getLockSql(loadSql, dbType, lockMode));
		return (Serializable) DialectUtils.load(sqlToyContext, sqlToyConfig, loadSql, entityMeta, entity, onlySubTables,
				cascadeTypes, conn, profile, queryTimeout);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#loadAll(org.sagacity.sqltoy.
	 * SqlToyContext, java.util.List, java.util.List,
	 * org.sagacity.sqltoy.model.LockMode, java.sql.Connection, java.lang.Integer,
	 * java.lang.String, java.lang.String)
	 */
	@Override
	public List<?> loadAll(final SqlToyContext sqlToyContext, List<?> entities, boolean onlySubTables,
			List<Class> cascadeTypes, final LockMode lockMode, final int lockWaitTimeout, Connection conn,
			DBProfile profile, final String tableName, final int fetchSize, final int maxRows,
			final Integer queryTimeout) throws Exception {
		return DialectUtils.loadAll(sqlToyContext, entities, onlySubTables, cascadeTypes, lockMode, conn, profile,
				tableName, (sql, dbTypeValue, lockedMode) -> {
					return getLockSql(sql, dbTypeValue, lockedMode);
				}, fetchSize, maxRows, queryTimeout);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.sagacity.sqltoy.dialect.Dialect#save(org.sagacity.sqltoy.SqlToyContext,
	 * java.io.Serializable, java.sql.Connection, java.lang.Integer,
	 * java.lang.String, java.lang.String)
	 */
	@Override
	public Object save(SqlToyContext sqlToyContext, Serializable entity, final Connection conn, DBProfile profile,
			final String tableName) throws Exception {
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entity.getClass());
		// save行为根据主键是否赋值情况调整最终的主键策略
		PKStrategy pkStrategy = DialectUtils.getSavePKStrategy(entityMeta, entity, profile);
		boolean isAssignPK = DB2DialectUtils.allowAssignPKValue(pkStrategy);
		// db2 sequence 支持手工赋值
		String insertSql = DialectExtUtils.generateInsertSql(sqlToyContext.getUnifyFieldsHandler(), profile, entityMeta,
				pkStrategy, NEXT_VAL + entityMeta.getSequence(), isAssignPK, tableName);
		return DialectUtils.save(sqlToyContext, entityMeta, pkStrategy, isAssignPK, insertSql, entity,
				new GenerateSqlHandler() {
					@Override
					public String generateSql(EntityMeta entityMeta, String[] forceUpdateField) {
						return DialectExtUtils.generateInsertSql(sqlToyContext.getUnifyFieldsHandler(), profile,
								entityMeta, entityMeta.getIdStrategy(), NEXT_VAL + entityMeta.getSequence(),
								DB2DialectUtils.allowAssignPKValue(entityMeta.getIdStrategy()), null);
					}
				}, new GenerateSavePKStrategy() {
					@Override
					public SavePKStrategy generate(EntityMeta entityMeta) {
						return new SavePKStrategy(entityMeta.getIdStrategy(),
								DB2DialectUtils.allowAssignPKValue(entityMeta.getIdStrategy()));
					}
				}, conn, profile);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#saveAll(org.sagacity.sqltoy.
	 * SqlToyContext , java.util.List,
	 * org.sagacity.sqltoy.callback.ReflectPropsHandler, java.sql.Connection)
	 */
	@Override
	public Long saveAll(SqlToyContext sqlToyContext, List<?> entities, final int batchSize,
			ReflectPropsHandler reflectPropsHandler, Connection conn, DBProfile profile, final Boolean autoCommit,
			final String tableName) throws Exception {
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entities.get(0).getClass());
		boolean isAssignPK = DB2DialectUtils.allowAssignPKValue(entityMeta.getIdStrategy());
		String insertSql = DialectExtUtils.generateInsertSql(sqlToyContext.getUnifyFieldsHandler(), profile, entityMeta,
				entityMeta.getIdStrategy(), NEXT_VAL + entityMeta.getSequence(), isAssignPK, tableName);
		return DialectUtils.saveAll(sqlToyContext, entityMeta, entityMeta.getIdStrategy(), isAssignPK, insertSql,
				entities, batchSize, reflectPropsHandler, conn, profile, autoCommit);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.sagacity.sqltoy.dialect.Dialect#update(org.sagacity.sqltoy.SqlToyContext,
	 * java.io.Serializable, java.lang.String[], boolean, java.lang.Class[],
	 * java.util.HashMap, java.sql.Connection, java.lang.Integer, java.lang.String,
	 * java.lang.String)
	 */
	@Override
	public Long update(final SqlToyContext sqlToyContext, Serializable entity, String[] forceUpdateFields,
			final boolean cascade, final Class[] forceCascadeClasses,
			final HashMap<Class, String[]> subTableForceUpdateProps, Connection conn, DBProfile profile,
			final String tableName) throws Exception {
		return DialectUtils.update(sqlToyContext, entity, forceUpdateFields, cascade,
				(cascade == false) ? null : new GenerateSqlHandler() {
					@Override
					public String generateSql(EntityMeta entityMeta, String[] forceUpdateFields) {
						return DialectUtils.getSaveOrUpdateSql(sqlToyContext, sqlToyContext.getUnifyFieldsHandler(),
								profile, entityMeta, entityMeta.getIdStrategy(), forceUpdateFields, VIRTUAL_TABLE,
								NEXT_VAL + entityMeta.getSequence(),
								DB2DialectUtils.allowAssignPKValue(entityMeta.getIdStrategy()), null);
					}
				}, forceCascadeClasses, subTableForceUpdateProps, conn, profile, tableName);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#updateAll(org.sagacity.sqltoy.
	 * SqlToyContext, java.util.List, int, java.lang.String[],
	 * org.sagacity.sqltoy.callback.ReflectPropsHandler, java.sql.Connection,
	 * java.lang.Integer, java.lang.String, java.lang.Boolean, java.lang.String)
	 */
	@Override
	public Long updateAll(SqlToyContext sqlToyContext, List<?> entities, final int batchSize,
			final String[] uniqueFields, final String[] forceUpdateFields, ReflectPropsHandler reflectPropsHandler,
			Connection conn, DBProfile profile, final Boolean autoCommit, final String tableName) throws Exception {
		return DialectUtils.updateAll(sqlToyContext, entities, batchSize, forceUpdateFields, reflectPropsHandler, conn,
				profile, autoCommit, tableName, false);
	}

	@Override
	public Serializable updateSaveFetch(SqlToyContext sqlToyContext, Serializable entity,
			UpdateRowHandler updateRowHandler, int lockWaitTimeout, String[] uniqueProps, Connection conn,
			DBProfile profile, String tableName) throws Exception {
		return DefaultDialectUtils.updateSaveFetch(sqlToyContext, entity, updateRowHandler, uniqueProps, conn, profile,
				tableName, lockWaitTimeout);
	}

	@Override
	public Serializable updateSaveFetch(SqlToyContext sqlToyContext, Serializable entity,
			UpdateRowCallback updateRowCallback, int lockWaitTimeout, String[] uniqueProps, Connection conn,
			DBProfile profile, String tableName) throws Exception {
		return DefaultDialectUtils.updateSaveFetch(sqlToyContext, entity, updateRowCallback, uniqueProps, conn, profile,
				tableName, lockWaitTimeout);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#getRandomResult(org.sagacity.sqltoy.
	 * SqlToyContext, org.sagacity.sqltoy.config.model.SqlToyConfig,
	 * org.sagacity.sqltoy.model.QueryExecutor, java.lang.Long, java.lang.Long,
	 * java.sql.Connection, java.lang.Integer, java.lang.String)
	 */
	@Override
	public QueryResult getRandomResult(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig,
			QueryExecutor queryExecutor, final DecryptHandler decryptHandler, Long totalCount, Long randomCount,
			Connection conn, DBProfile profile, final int fetchSize, final int maxRows) throws Exception {
		String dialect = profile.getDialect();
		String innerSql = sqlToyConfig.isHasFast() ? sqlToyConfig.getFastSql(dialect) : sqlToyConfig.getSql(dialect);
		// sql中是否存在排序或union
		boolean hasOrderOrUnion = DialectUtils.hasOrderByOrUnion(innerSql);
		StringBuilder sql = DialectUtils.openFastWrap(sqlToyConfig, dialect);
		// 给原始sql标记上特殊的开始和结尾，便于sql拦截器快速定位原始sql并进行条件补充
		String markedSql = SqlUtilsExt.markOriginalSql(innerSql);
		// 存在order 或union 则在sql外包裹一层派生表(order by rand()须作用于整集)
		if (hasOrderOrUnion) {
			sql.append("select " + SqlToyConstants.INTERMEDIATE_TABLE + ".* from (");
			sql.append(markedSql);
			sql.append(") ");
			sql.append(SqlToyConstants.INTERMEDIATE_TABLE);
			sql.append(" ");
		} else {
			sql.append(markedSql);
		}
		sql.append(" order by rand() fetch first ");
		sql.append(randomCount);
		sql.append(" rows only ");
		DialectUtils.closeFastWrap(sqlToyConfig, dialect, sql);
		return DialectUtils.executeWrappedQuery(sqlToyContext, sqlToyConfig, queryExecutor, decryptHandler, conn,
				profile, sql.toString(), null, null,
				(queryExecutor.getInnerModel().entityClass == null) ? OperateType.random : OperateType.singleTable,
				fetchSize, maxRows);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#findPageBySql(org.sagacity.sqltoy.
	 * SqlToyContext, org.sagacity.sqltoy.config.model.SqlToyConfig,
	 * org.sagacity.sqltoy.model.QueryExecutor, java.lang.Long, java.lang.Integer,
	 * java.sql.Connection, java.lang.Integer, java.lang.String)
	 */
	@Override
	public QueryResult findPageBySql(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig,
			QueryExecutor queryExecutor, final DecryptHandler decryptHandler, Long pageNo, Integer pageSize,
			Connection conn, DBProfile profile, final int fetchSize, final int maxRows) throws Exception {
		return DefaultDialectUtils.findPageBySql(sqlToyContext, sqlToyConfig, queryExecutor, decryptHandler, pageNo,
				pageSize, conn, profile, fetchSize, maxRows);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#findTopBySql(org.sagacity.sqltoy.
	 * SqlToyContext, org.sagacity.sqltoy.config.model.SqlToyConfig,
	 * org.sagacity.sqltoy.model.QueryExecutor, java.lang.Integer,
	 * java.sql.Connection, java.lang.Integer, java.lang.String)
	 */
	@Override
	public QueryResult findTopBySql(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig, QueryExecutor queryExecutor,
			final DecryptHandler decryptHandler, Integer topSize, Connection conn, DBProfile profile,
			final int fetchSize, final int maxRows) throws Exception {
		return DefaultDialectUtils.findTopBySql(sqlToyContext, sqlToyConfig, queryExecutor, decryptHandler, topSize,
				conn, profile, fetchSize, maxRows);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#findBySql(org.sagacity.sqltoy.
	 * SqlToyContext, org.sagacity.sqltoy.config.model.SqlToyConfig,
	 * java.lang.String, java.lang.Object[],
	 * org.sagacity.sqltoy.callback.RowCallbackHandler, java.sql.Connection,
	 * java.lang.Integer, java.lang.String, int, int)
	 */
	@Override
	public QueryResult findBySql(final SqlToyContext sqlToyContext, final SqlToyConfig sqlToyConfig, final String sql,
			final Object[] paramsValue, final QueryExecutorExtend queryExecutorExtend,
			final DecryptHandler decryptHandler, final Connection conn, final LockMode lockMode, DBProfile profile,
			final int fetchSize, final int maxRows) throws Exception {
		Integer dbType = profile.getDbType();
		String realSql;
		// db2 锁记录
		if (lockMode != null) {
			realSql = sql.concat(getLockSql(sql, dbType, lockMode));
		} else {
			realSql = appendWithUR(sql);
		}
		return DialectUtils.findBySql(sqlToyContext, sqlToyConfig, realSql, paramsValue, queryExecutorExtend,
				decryptHandler, conn, profile, 0, fetchSize, maxRows);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#getCountBySql(org.sagacity.sqltoy.
	 * SqlToyContext, org.sagacity.sqltoy.config.model.SqlToyConfig,
	 * java.lang.String, java.lang.Object[], boolean, java.sql.Connection,
	 * java.lang.Integer, java.lang.String)
	 */
	@Override
	public Long getCountBySql(final SqlToyContext sqlToyContext, final SqlToyConfig sqlToyConfig, final String sql,
			final Object[] paramsValue, final boolean isLastSql, final QueryExecutorExtend extend, Connection conn,
			DBProfile profile) throws Exception {
		return DialectUtils.getCountBySql(sqlToyContext, sqlToyConfig, appendWithUR(sql), paramsValue, isLastSql,
				extend, conn, profile);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#saveOrUpdate(org.sagacity.sqltoy.
	 * SqlToyContext, java.io.Serializable, java.lang.String[], java.sql.Connection,
	 * java.lang.Integer, java.lang.String, java.lang.Boolean, java.lang.String)
	 */
	@Override
	public Long saveOrUpdate(SqlToyContext sqlToyContext, Serializable entity, final String[] forceUpdateFields,
			Connection conn, DBProfile profile, final Boolean autoCommit, final String tableName) throws Exception {
		List<Serializable> entities = new ArrayList<Serializable>();
		entities.add(entity);
		return saveOrUpdateAll(sqlToyContext, entities, sqlToyContext.getBatchSize(), null, forceUpdateFields, conn,
				profile, autoCommit, tableName);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#saveOrUpdateAll(org.sagacity.sqltoy.
	 * SqlToyContext, java.util.List, int,
	 * org.sagacity.sqltoy.callback.ReflectPropsHandler, java.lang.String[],
	 * java.sql.Connection, java.lang.Integer, java.lang.String, java.lang.Boolean,
	 * java.lang.String)
	 */
	@Override
	public Long saveOrUpdateAll(final SqlToyContext sqlToyContext, List<?> entities, final int batchSize,
			final ReflectPropsHandler reflectPropsHandler, final String[] forceUpdateFields, Connection conn,
			DBProfile profile, final Boolean autoCommit, final String tableName) throws Exception {
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entities.get(0).getClass());
		return DialectUtils.saveOrUpdateAll(sqlToyContext, entities, batchSize, entityMeta, forceUpdateFields,
				new GenerateSqlHandler() {
					@Override
					public String generateSql(EntityMeta entityMeta, String[] forceUpdateFields) {
						return DialectUtils.getSaveOrUpdateSql(sqlToyContext, sqlToyContext.getUnifyFieldsHandler(),
								profile, entityMeta, entityMeta.getIdStrategy(), forceUpdateFields, VIRTUAL_TABLE,
								NEXT_VAL + entityMeta.getSequence(),
								DB2DialectUtils.allowAssignPKValue(entityMeta.getIdStrategy()), tableName);
					}
				}, reflectPropsHandler, conn, profile, autoCommit);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.sagacity.sqltoy.dialect.Dialect#saveAllIgnoreExist(org.sagacity.sqltoy.
	 * SqlToyContext, java.util.List, int,
	 * org.sagacity.sqltoy.callback.ReflectPropsHandler, java.sql.Connection,
	 * java.lang.Integer, java.lang.String, java.lang.Boolean, java.lang.String)
	 */
	@Override
	public Long saveAllIgnoreExist(SqlToyContext sqlToyContext, List<?> entities, final int batchSize,
			ReflectPropsHandler reflectPropsHandler, Connection conn, DBProfile profile, final Boolean autoCommit,
			final String tableName) throws Exception {
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entities.get(0).getClass());
		return DialectUtils.saveAllIgnoreExist(sqlToyContext, entities, batchSize, entityMeta,
				new GenerateSqlHandler() {
					@Override
					public String generateSql(EntityMeta entityMeta, String[] forceUpdateFields) {
						PKStrategy pkStrategy = entityMeta.getIdStrategy();
						return DialectUtils.mergeIgnore(sqlToyContext.getUnifyFieldsHandler(), profile, entityMeta,
								pkStrategy, VIRTUAL_TABLE, NEXT_VAL + entityMeta.getSequence(),
								DB2DialectUtils.allowAssignPKValue(pkStrategy), tableName);
					}
				}, reflectPropsHandler, conn, profile, autoCommit);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.sagacity.sqltoy.dialect.Dialect#delete(org.sagacity.sqltoy.SqlToyContext,
	 * java.io.Serializable, java.sql.Connection, java.lang.Integer,
	 * java.lang.String, java.lang.String)
	 */
	@Override
	public Long delete(SqlToyContext sqlToyContext, Serializable entity, Connection conn, DBProfile profile,
			final String tableName) throws Exception {
		return DialectUtils.delete(sqlToyContext, entity, conn, profile, tableName);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#deleteAll(org.sagacity.sqltoy.
	 * SqlToyContext, java.util.List, int, java.sql.Connection, java.lang.Integer,
	 * java.lang.String, java.lang.Boolean, java.lang.String)
	 */
	@Override
	public Long deleteAll(SqlToyContext sqlToyContext, List<?> entities, final int batchSize, Connection conn,
			DBProfile profile, final Boolean autoCommit, final String tableName) throws Exception {
		return DialectUtils.deleteAll(sqlToyContext, entities, batchSize, conn, profile, autoCommit, tableName);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#updateFetch(org.sagacity.sqltoy.
	 * SqlToyContext, org.sagacity.sqltoy.config.model.SqlToyConfig,
	 * java.lang.String, java.lang.Object[],
	 * org.sagacity.sqltoy.callback.UpdateRowHandler, java.sql.Connection,
	 * java.lang.Integer, java.lang.String)
	 */
	@Override
	public QueryResult updateFetch(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig, String sql,
			Object[] paramsValue, UpdateRowHandler updateRowHandler, Connection conn, DBProfile profile,
			final LockMode lockMode, int lockWaitTimeout, final int fetchSize, final int maxRows) throws Exception {
		Integer dbType = profile.getDbType();
		String realSql = sql.concat(getLockSql(sql, dbType, (lockMode == null) ? LockMode.UPGRADE : lockMode));
		return DialectUtils.updateFetchBySql(sqlToyContext, sqlToyConfig, realSql, paramsValue, updateRowHandler, conn,
				profile, 0, fetchSize, maxRows);
	}

	@Override
	public StoreResult executeStore(SqlToyContext sqlToyContext, final SqlToyConfig sqlToyConfig, final String sql,
			final Object[] inParamsValue, final Integer[] outParamsType, final boolean moreResult,
			final Connection conn, DBProfile profile, final int fetchSize, final Integer timeout) throws Exception {
		return DialectUtils.executeStore(sqlToyConfig, sqlToyContext, sql, inParamsValue, outParamsType, moreResult,
				conn, profile, fetchSize, timeout);
	}

	private String getLockSql(String sql, Integer dbType, LockMode lockMode) {
		// 判断是否已经包含for update
		if (lockMode == null || SqlUtil.hasLock(sql, dbType)) {
			return "";
		}
		// if (lockMode == null || lockMode == LockMode.UPGRADE) {
		// return " for update ";
		// }
		return " for update with rs ";
	}

	@Override
	public List<ColumnMeta> getTableColumns(String catalog, String schema, String tableName, Connection conn,
			DBProfile profile) throws Exception {
		return DefaultDialectUtils.getTableColumns(catalog, schema, tableName, conn, profile);
	}

	@Override
	public List<TableMeta> getTables(String catalog, String schema, String tableName, Connection conn,
			DBProfile profile) throws Exception {
		return DefaultDialectUtils.getTables(catalog, schema, tableName, conn, profile);
	}
}
