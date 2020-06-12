/**
 * 
 */
package org.sagacity.sqltoy.dialect.utils;

import java.io.Serializable;
import java.sql.Connection;
import java.util.HashSet;
import java.util.List;

import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.callback.ReflectPropertyHandler;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.PKStrategy;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlToyResult;
import org.sagacity.sqltoy.dialect.handler.GenerateSavePKStrategy;
import org.sagacity.sqltoy.dialect.handler.GenerateSqlHandler;
import org.sagacity.sqltoy.dialect.model.ReturnPkType;
import org.sagacity.sqltoy.dialect.model.SavePKStrategy;
import org.sagacity.sqltoy.executor.QueryExecutor;
import org.sagacity.sqltoy.model.QueryResult;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;
import org.sagacity.sqltoy.utils.ReservedWordsUtil;

/**
 * @project sqltoy-orm
 * @description 提供postgresql数据库共用的逻辑实现，便于今后postgresql不同版本之间共享共性部分的实现
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:PostgreSqlDialectUtils.java,Revision:v1.0,Date:2015年3月5日
 * @Modification Date:2020-06-12 修复10+版本对identity主键生成的策略
 */
public class PostgreSqlDialectUtils {
	/**
	 * 判定为null的函数
	 */
	public static final String NVL_FUNCTION = "COALESCE";

	/**
	 * @todo 提供随机记录查询
	 * @param sqlToyContext
	 * @param sqlToyConfig
	 * @param queryExecutor
	 * @param totalCount
	 * @param randomCount
	 * @param conn
	 * @param dbType
	 * @param dialect
	 * @return
	 * @throws Exception
	 */
	public static QueryResult getRandomResult(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig,
			QueryExecutor queryExecutor, Long totalCount, Long randomCount, Connection conn, final Integer dbType,
			final String dialect) throws Exception {
		String innerSql = sqlToyConfig.isHasFast() ? sqlToyConfig.getFastSql(dialect) : sqlToyConfig.getSql(dialect);
		// sql中是否存在排序或union
		boolean hasOrderOrUnion = DialectUtils.hasOrderByOrUnion(innerSql);
		StringBuilder sql = new StringBuilder();

		if (sqlToyConfig.isHasFast()) {
			sql.append(sqlToyConfig.getFastPreSql(dialect)).append(" (");
		}
		// 存在order 或union 则在sql外包裹一层
		if (hasOrderOrUnion) {
			sql.append("select sag_random_table.* from (");
		}
		sql.append(innerSql);
		if (hasOrderOrUnion) {
			sql.append(") sag_random_table ");
		}
		sql.append(" order by random() limit ");
		sql.append(randomCount);

		if (sqlToyConfig.isHasFast()) {
			sql.append(") ").append(sqlToyConfig.getFastTailSql(dialect));
		}
		SqlToyResult queryParam = DialectUtils.wrapPageSqlParams(sqlToyContext, sqlToyConfig, queryExecutor,
				sql.toString(), null, null);
		return DialectUtils.findBySql(sqlToyContext, sqlToyConfig, queryParam.getSql(), queryParam.getParamsValue(),
				queryExecutor.getRowCallbackHandler(), conn, dbType, 0, queryExecutor.getFetchSize(),
				queryExecutor.getMaxRows());
	}

	/**
	 * @todo 分页查询
	 * @param sqlToyContext
	 * @param sqlToyConfig
	 * @param queryExecutor
	 * @param pageNo
	 * @param pageSize
	 * @param conn
	 * @param dbType
	 * @param dialect
	 * @return
	 * @throws Exception
	 */
	public static QueryResult findPageBySql(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig,
			QueryExecutor queryExecutor, Long pageNo, Integer pageSize, Connection conn, final Integer dbType,
			final String dialect) throws Exception {
		StringBuilder sql = new StringBuilder();
		boolean isNamed = sqlToyConfig.isNamedParam();
		if (sqlToyConfig.isHasFast()) {
			sql.append(sqlToyConfig.getFastPreSql(dialect));
			sql.append(" (").append(sqlToyConfig.getFastSql(dialect));
		} else {
			sql.append(sqlToyConfig.getSql(dialect));
		}
		sql.append(" limit ");
		sql.append(isNamed ? ":" + SqlToyConstants.PAGE_FIRST_PARAM_NAME : "?");
		sql.append(" offset ");
		sql.append(isNamed ? ":" + SqlToyConstants.PAGE_LAST_PARAM_NAME : "?");
		if (sqlToyConfig.isHasFast()) {
			sql.append(") ").append(sqlToyConfig.getFastTailSql(dialect));
		}

		SqlToyResult queryParam = DialectUtils.wrapPageSqlParams(sqlToyContext, sqlToyConfig, queryExecutor,
				sql.toString(), Long.valueOf(pageSize), (pageNo - 1) * pageSize);
		return DialectUtils.findBySql(sqlToyContext, sqlToyConfig, queryParam.getSql(), queryParam.getParamsValue(),
				queryExecutor.getRowCallbackHandler(), conn, dbType, 0, queryExecutor.getFetchSize(),
				queryExecutor.getMaxRows());
	}

	/**
	 * @todo 实现top记录查询
	 * @param sqlToyContext
	 * @param sqlToyConfig
	 * @param queryExecutor
	 * @param topSize
	 * @param conn
	 * @param dbType
	 * @param dialect
	 * @return
	 * @throws Exception
	 */
	public static QueryResult findTopBySql(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig,
			QueryExecutor queryExecutor, Integer topSize, Connection conn, final Integer dbType, final String dialect)
			throws Exception {
		StringBuilder sql = new StringBuilder();
		if (sqlToyConfig.isHasFast()) {
			sql.append(sqlToyConfig.getFastPreSql(dialect));
			sql.append(" (").append(sqlToyConfig.getFastSql(dialect));
		} else {
			sql.append(sqlToyConfig.getSql(dialect));
		}
		sql.append(" limit ");
		sql.append(topSize);

		if (sqlToyConfig.isHasFast()) {
			sql.append(") ").append(sqlToyConfig.getFastTailSql(dialect));
		}
		SqlToyResult queryParam = DialectUtils.wrapPageSqlParams(sqlToyContext, sqlToyConfig, queryExecutor,
				sql.toString(), null, null);
		return DialectUtils.findBySql(sqlToyContext, sqlToyConfig, queryParam.getSql(), queryParam.getParamsValue(),
				queryExecutor.getRowCallbackHandler(), conn, dbType, 0, queryExecutor.getFetchSize(),
				queryExecutor.getMaxRows());
	}

	/**
	 * @todo 保存单条对象记录
	 * @param sqlToyContext
	 * @param entity
	 * @param conn
	 * @param dbType
	 * @param tableName
	 * @return
	 * @throws Exception
	 */
	public static Object save(SqlToyContext sqlToyContext, Serializable entity, Connection conn, final Integer dbType,
			String tableName) throws Exception {
		// 只支持sequence模式
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entity.getClass());
		PKStrategy pkStrategy = entityMeta.getIdStrategy();
		String sequence = "nextval('" + entityMeta.getSequence() + "')";
		// 从10版本开始支持identity
		if (pkStrategy != null && pkStrategy.equals(PKStrategy.IDENTITY)) {
			// 伪造成sequence模式
			pkStrategy = PKStrategy.SEQUENCE;
			sequence = "DEFAULT";
		}

		boolean isAssignPK = isAssignPKValue(pkStrategy);
		String insertSql = DialectUtils.generateInsertSql(DBType.POSTGRESQL, entityMeta, pkStrategy, NVL_FUNCTION,
				sequence, isAssignPK, tableName);
		return DialectUtils.save(sqlToyContext, entityMeta, pkStrategy, isAssignPK, ReturnPkType.GENERATED_KEYS,
				insertSql, entity, new GenerateSqlHandler() {
					public String generateSql(EntityMeta entityMeta, String[] forceUpdateField) {
						PKStrategy pkStrategy = entityMeta.getIdStrategy();
						String sequence = "nextval('" + entityMeta.getSequence() + "')";
						if (pkStrategy != null && pkStrategy.equals(PKStrategy.IDENTITY)) {
							// 伪造成sequence模式
							pkStrategy = PKStrategy.SEQUENCE;
							sequence = "DEFAULT";
						}
						return DialectUtils.generateInsertSql(DBType.POSTGRESQL, entityMeta, pkStrategy, NVL_FUNCTION,
								sequence, isAssignPKValue(pkStrategy), null);
					}
				}, new GenerateSavePKStrategy() {
					public SavePKStrategy generate(EntityMeta entityMeta) {
						return new SavePKStrategy(entityMeta.getIdStrategy(),
								isAssignPKValue(entityMeta.getIdStrategy()));
					}
				}, conn, dbType);
	}

	/**
	 * @todo 批量保存对象入数据库
	 * @param sqlToyContext
	 * @param entities
	 * @param batchSize
	 * @param reflectPropertyHandler
	 * @param conn
	 * @param dbType
	 * @param autoCommit
	 * @param tableName
	 * @return
	 * @throws Exception
	 */
	public static Long saveAll(SqlToyContext sqlToyContext, List<?> entities, final int batchSize,
			ReflectPropertyHandler reflectPropertyHandler, Connection conn, final Integer dbType,
			final Boolean autoCommit, String tableName) throws Exception {
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entities.get(0).getClass());
		PKStrategy pkStrategy = entityMeta.getIdStrategy();
		String sequence = "nextval('" + entityMeta.getSequence() + "')";
		// identity模式用关键词default 代替
		if (pkStrategy != null && pkStrategy.equals(PKStrategy.IDENTITY)) {
			// 伪造成sequence模式
			pkStrategy = PKStrategy.SEQUENCE;
			sequence = "DEFAULT";
		}
		boolean isAssignPK = isAssignPKValue(pkStrategy);
		String insertSql = DialectUtils.generateInsertSql(DBType.POSTGRESQL, entityMeta, pkStrategy, NVL_FUNCTION,
				sequence, isAssignPK, tableName);
		return DialectUtils.saveAll(sqlToyContext, entityMeta, pkStrategy, isAssignPK, insertSql, entities, batchSize,
				reflectPropertyHandler, conn, dbType, autoCommit);
	}

	/**
	 * @todo postgresql9.5以及以上版本的saveOrUpdate语句
	 * @param dbType
	 * @param entityMeta
	 * @param pkStrategy
	 * @param sequence
	 * @param forceUpdateFields
	 * @param tableName
	 * @return
	 */
	public static String getSaveOrUpdateSql(Integer dbType, EntityMeta entityMeta, PKStrategy pkStrategy,
			String sequence, String[] forceUpdateFields, String tableName) {
		String realTable = entityMeta.getSchemaTable(tableName);
		if (entityMeta.getIdArray() == null) {
			return DialectUtils.generateInsertSql(dbType, entityMeta, entityMeta.getIdStrategy(), NVL_FUNCTION, null,
					false, realTable);
		}
		// 是否全部是ID
		boolean allIds = (entityMeta.getRejectIdFieldArray() == null);
		// 全部是主键采用replace into 策略进行保存或修改,不考虑只有一个字段且是主键的表情况
		StringBuilder sql = new StringBuilder("insert into ");
		StringBuilder values = new StringBuilder();
		String columnName;
		sql.append(realTable);
		sql.append(" AS t1 (");
		for (int i = 0, n = entityMeta.getFieldsArray().length; i < n; i++) {
			if (i > 0) {
				sql.append(",");
				values.append(",");
			}
			columnName = entityMeta.getColumnName(entityMeta.getFieldsArray()[i]);
			sql.append(ReservedWordsUtil.convertWord(columnName, dbType));
			values.append("?");
		}
		sql.append(") values (");
		sql.append(values);
		sql.append(") ");
		// 非全部是主键
		if (!allIds) {
			sql.append(" ON CONFLICT ON ");
			if (entityMeta.getPkConstraint() != null) {
				sql.append(" CONSTRAINT ").append(entityMeta.getPkConstraint());
			} else {
				sql.append(" (");
				for (int i = 0, n = entityMeta.getIdArray().length; i < n; i++) {
					if (i > 0) {
						sql.append(",");
					}
					columnName = entityMeta.getColumnName(entityMeta.getIdArray()[i]);
					sql.append(ReservedWordsUtil.convertWord(columnName, dbType));
				}
				sql.append(" ) ");
			}

			sql.append(" DO UPDATE SET ");
			// 需要被强制修改的字段
			HashSet<String> fupc = new HashSet<String>();
			if (forceUpdateFields != null) {
				for (String field : forceUpdateFields) {
					fupc.add(ReservedWordsUtil.convertWord(entityMeta.getColumnName(field), dbType));
				}
			}

			for (int i = 0, n = entityMeta.getRejectIdFieldArray().length; i < n; i++) {
				columnName = entityMeta.getColumnName(entityMeta.getRejectIdFieldArray()[i]);
				columnName = ReservedWordsUtil.convertWord(columnName, dbType);
				if (i > 0) {
					sql.append(",");
				}
				sql.append(columnName).append("=");
				// 强制修改
				if (fupc.contains(columnName)) {
					sql.append("excluded.").append(columnName);
				} else {
					sql.append("COALESCE(excluded.");
					sql.append(columnName).append(",t1.");
					sql.append(columnName).append(")");
				}
			}
		}
		return sql.toString();
	}

	/**
	 * @todo postgresql9.5以及以上版本的saveOrUpdate语句
	 * @param dbType
	 * @param entityMeta
	 * @param pkStrategy
	 * @param sequence
	 * @param tableName
	 * @return
	 */
	public static String getSaveIgnoreExist(Integer dbType, EntityMeta entityMeta, PKStrategy pkStrategy,
			String sequence, String tableName) {
		String realTable = entityMeta.getSchemaTable(tableName);
		if (entityMeta.getIdArray() == null) {
			return DialectUtils.generateInsertSql(dbType, entityMeta, entityMeta.getIdStrategy(), NVL_FUNCTION, null,
					false, realTable);
		}
		// 全部是主键采用replace into 策略进行保存或修改,不考虑只有一个字段且是主键的表情况
		StringBuilder sql = new StringBuilder("insert into ");
		StringBuilder values = new StringBuilder();
		String columnName;
		sql.append(realTable);
		sql.append(" AS t1 (");
		for (int i = 0, n = entityMeta.getFieldsArray().length; i < n; i++) {
			if (i > 0) {
				sql.append(",");
				values.append(",");
			}
			columnName = entityMeta.getColumnName(entityMeta.getFieldsArray()[i]);
			sql.append(ReservedWordsUtil.convertWord(columnName, dbType));
			values.append("?");
		}
		sql.append(") values (");
		sql.append(values);
		sql.append(") ");
		// 非全部是主键
		if (entityMeta.getRejectIdFieldArray() != null) {
			sql.append(" ON CONFLICT ON ");
			if (entityMeta.getPkConstraint() != null) {
				sql.append(" CONSTRAINT ").append(entityMeta.getPkConstraint());
			} else {
				sql.append(" (");
				for (int i = 0, n = entityMeta.getIdArray().length; i < n; i++) {
					if (i > 0) {
						sql.append(",");
					}
					columnName = entityMeta.getColumnName(entityMeta.getIdArray()[i]);
					sql.append(ReservedWordsUtil.convertWord(columnName, dbType));
				}
				sql.append(" ) ");
			}
			sql.append(" DO NOTHING ");
		}
		return sql.toString();
	}

	/**
	 * @TODO 定义当使用sequence或identity时,是否允许自定义值(即不通过sequence或identity产生，而是由外部直接赋值)
	 * @param pkStrategy
	 * @return
	 */
	private static boolean isAssignPKValue(PKStrategy pkStrategy) {
		if (pkStrategy == null)
			return true;
		// sequence
		if (pkStrategy.equals(PKStrategy.SEQUENCE))
			return false;
		// postgresql10+ 支持identity
		if (pkStrategy.equals(PKStrategy.IDENTITY))
			return false;
		return true;
	}
}
