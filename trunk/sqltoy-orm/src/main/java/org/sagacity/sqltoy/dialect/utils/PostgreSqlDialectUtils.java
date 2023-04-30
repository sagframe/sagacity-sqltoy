/**
 * 
 */
package org.sagacity.sqltoy.dialect.utils;

import java.io.Serializable;
import java.sql.Connection;
import java.util.HashSet;
import java.util.List;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.callback.DecryptHandler;
import org.sagacity.sqltoy.callback.GenerateSavePKStrategy;
import org.sagacity.sqltoy.callback.GenerateSqlHandler;
import org.sagacity.sqltoy.callback.ReflectPropsHandler;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.FieldMeta;
import org.sagacity.sqltoy.config.model.OperateType;
import org.sagacity.sqltoy.config.model.PKStrategy;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlToyResult;
import org.sagacity.sqltoy.dialect.model.SavePKStrategy;
import org.sagacity.sqltoy.executor.QueryExecutor;
import org.sagacity.sqltoy.model.QueryResult;
import org.sagacity.sqltoy.model.inner.QueryExecutorExtend;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;
import org.sagacity.sqltoy.utils.ReservedWordsUtil;

/**
 * @project sqltoy-orm
 * @description 提供postgresql数据库共用的逻辑实现，便于今后postgresql不同版本之间共享共性部分的实现
 * @author zhongxuchen
 * @version v1.0,Date:2015年3月5日
 * @modify Date:2020-06-12 修复10+版本对identity主键生成的策略
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
	 * @param fetchSize
	 * @param maxRows
	 * @return
	 * @throws Exception
	 */
	public static QueryResult getRandomResult(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig,
			QueryExecutor queryExecutor, final DecryptHandler decryptHandler, Long totalCount, Long randomCount,
			Connection conn, final Integer dbType, final String dialect, final int fetchSize, final int maxRows)
			throws Exception {
		String innerSql = sqlToyConfig.isHasFast() ? sqlToyConfig.getFastSql(dialect) : sqlToyConfig.getSql(dialect);
		// sql中是否存在排序或union
		boolean hasOrderOrUnion = DialectUtils.hasOrderByOrUnion(innerSql);
		StringBuilder sql = new StringBuilder();
		if (sqlToyConfig.isHasFast()) {
			sql.append(sqlToyConfig.getFastPreSql(dialect));
			if (!sqlToyConfig.isIgnoreBracket()) {
				sql.append(" (");
			}
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
			if (!sqlToyConfig.isIgnoreBracket()) {
				sql.append(") ");
			}
			sql.append(sqlToyConfig.getFastTailSql(dialect));
		}
		SqlToyResult queryParam = DialectUtils.wrapPageSqlParams(sqlToyContext, sqlToyConfig, queryExecutor,
				sql.toString(), null, null, dialect);
		QueryExecutorExtend extend = queryExecutor.getInnerModel();
		// 增加sql执行拦截器 update 2022-9-10
		queryParam = DialectUtils.doInterceptors(sqlToyContext, sqlToyConfig,
				(extend.entityClass == null) ? OperateType.random : OperateType.singleTable, queryParam,
				extend.entityClass, dbType);
		return DialectUtils.findBySql(sqlToyContext, sqlToyConfig, queryParam.getSql(), queryParam.getParamsValue(),
				extend.rowCallbackHandler, decryptHandler, conn, dbType, 0, fetchSize, maxRows);
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
		// 主表gaussdb情况下为了可以返回主键值，sequence模式下在gaussdb下执行了先查询并给主键做了赋值，所以此处修改主键策略为assign
		if (dbType == DBType.GAUSSDB && pkStrategy.equals(PKStrategy.SEQUENCE)) {
			sequence = entityMeta.getSequence() + ".nextval";
			pkStrategy = PKStrategy.ASSIGN;
		}
		// 从10版本开始支持identity
		if (pkStrategy != null && pkStrategy.equals(PKStrategy.IDENTITY)) {
			// 伪造成sequence模式
			pkStrategy = PKStrategy.SEQUENCE;
			sequence = "DEFAULT";
		}

		boolean isAssignPK = isAssignPKValue(pkStrategy);
		String insertSql = DialectExtUtils.generateInsertSql(dbType, entityMeta, pkStrategy, NVL_FUNCTION, sequence,
				isAssignPK, tableName);
		return DialectUtils.save(sqlToyContext, entityMeta, pkStrategy, isAssignPK, insertSql, entity,
				new GenerateSqlHandler() {
					@Override
					public String generateSql(EntityMeta entityMeta, String[] forceUpdateField) {
						PKStrategy pkStrategy = entityMeta.getIdStrategy();
						String sequence = "nextval('" + entityMeta.getSequence() + "')";
						if (dbType == DBType.GAUSSDB) {
							sequence = entityMeta.getSequence() + ".nextval";
						}
						if (pkStrategy != null && pkStrategy.equals(PKStrategy.IDENTITY)) {
							// 伪造成sequence模式
							pkStrategy = PKStrategy.SEQUENCE;
							sequence = "DEFAULT";
						}
						return DialectExtUtils.generateInsertSql(dbType, entityMeta, pkStrategy, NVL_FUNCTION, sequence,
								isAssignPKValue(pkStrategy), null);
					}
				}, new GenerateSavePKStrategy() {
					@Override
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
	 * @param reflectPropsHandler
	 * @param conn
	 * @param dbType
	 * @param autoCommit
	 * @param tableName
	 * @return
	 * @throws Exception
	 */
	public static Long saveAll(SqlToyContext sqlToyContext, List<?> entities, final int batchSize,
			ReflectPropsHandler reflectPropsHandler, Connection conn, final Integer dbType, final Boolean autoCommit,
			String tableName) throws Exception {
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
		String insertSql = DialectExtUtils.generateInsertSql(dbType, entityMeta, pkStrategy, NVL_FUNCTION, sequence,
				isAssignPK, tableName);
		return DialectUtils.saveAll(sqlToyContext, entityMeta, pkStrategy, isAssignPK, insertSql, entities, batchSize,
				reflectPropsHandler, conn, dbType, autoCommit);
	}

	/**
	 * @TODO postgresql15 开始支持merge into 语法
	 * @param sqlToyContext
	 * @param entities
	 * @param batchSize
	 * @param reflectPropsHandler
	 * @param forceUpdateFields
	 * @param conn
	 * @param dbType
	 * @param dialect
	 * @param autoCommit
	 * @param tableName
	 * @return
	 * @throws Exception
	 */
	public static Long saveOrUpdateAll(SqlToyContext sqlToyContext, List<?> entities, final int batchSize,
			ReflectPropsHandler reflectPropsHandler, String[] forceUpdateFields, Connection conn, final Integer dbType,
			final String dialect, final Boolean autoCommit, final String tableName) throws Exception {
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entities.get(0).getClass());
		return DialectUtils.saveOrUpdateAll(sqlToyContext, entities, batchSize, entityMeta, forceUpdateFields,
				new GenerateSqlHandler() {
					@Override
					public String generateSql(EntityMeta entityMeta, String[] forceUpdateFields) {
						PKStrategy pkStrategy = entityMeta.getIdStrategy();
						String sequence = "nextval('" + entityMeta.getSequence() + "')";
						if (pkStrategy != null && pkStrategy.equals(PKStrategy.IDENTITY)) {
							// 伪造成sequence模式
							pkStrategy = PKStrategy.SEQUENCE;
							sequence = "DEFAULT";
						}
						return DialectUtils.getSaveOrUpdateSql(sqlToyContext.getUnifyFieldsHandler(), dbType,
								entityMeta, pkStrategy, forceUpdateFields, null, NVL_FUNCTION, sequence,
								isAssignPKValue(pkStrategy), tableName);
					}
				}, reflectPropsHandler, conn, dbType, autoCommit);
	}

	/**
	 * @todo postgresql9.5以及以上版本的saveOrUpdate语句，实际不会使用(用update和saveIgnore组合替代)，因为postgresql
	 *       此功能存在bug
	 * @param dbType
	 * @param entityMeta
	 * @param pkStrategy
	 * @param sequence
	 * @param forceUpdateFields
	 * @param tableName
	 * @return
	 */
	@Deprecated
	public static String getSaveOrUpdateSql(Integer dbType, EntityMeta entityMeta, PKStrategy pkStrategy,
			boolean isAssignPK, String sequence, String[] forceUpdateFields, String tableName) {
		String realTable = entityMeta.getSchemaTable(tableName, dbType);
		if (entityMeta.getIdArray() == null) {
			return DialectExtUtils.generateInsertSql(dbType, entityMeta, entityMeta.getIdStrategy(), NVL_FUNCTION, null,
					false, realTable);
		}
		// 是否全部是ID
		boolean allIds = (entityMeta.getRejectIdFieldArray() == null);
		// 全部是主键采用replace into 策略进行保存或修改,不考虑只有一个字段且是主键的表情况
		StringBuilder sql = new StringBuilder("insert into ");
		StringBuilder values = new StringBuilder();

		sql.append(realTable);
		sql.append(" AS t1 (");
		FieldMeta fieldMeta;
		String fieldName;
		boolean isStart = true;
		String columnName;
		for (int i = 0, n = entityMeta.getFieldsArray().length; i < n; i++) {
			fieldName = entityMeta.getFieldsArray()[i];
			fieldMeta = entityMeta.getFieldMeta(fieldName);
			columnName = ReservedWordsUtil.convertWord(fieldMeta.getColumnName(), dbType);
			// sql中的关键字处理
			if (!isStart) {
				sql.append(",");
				values.append(",");
			}
			if (fieldMeta.isPK()) {
				// identity主键策略，且支持主键手工赋值
				if (pkStrategy.equals(PKStrategy.IDENTITY)) {
					// 目前只有mysql支持
					if (isAssignPK) {
						sql.append(columnName);
						values.append("?");
						isStart = false;
					}
				} // sequence 策略，oracle12c之后的identity机制统一转化为sequence模式
				else if (pkStrategy.equals(PKStrategy.SEQUENCE)) {
					sql.append(columnName);
					if (isAssignPK) {
						values.append(NVL_FUNCTION);
						values.append("(?,").append(sequence).append(")");
					} else {
						values.append(sequence);
					}
					isStart = false;
				} else {
					sql.append(columnName);
					values.append("?");
					isStart = false;
				}
			} else {
				sql.append(columnName);
				if (null != fieldMeta.getDefaultValue()) {
					values.append(NVL_FUNCTION);
					values.append("(?,");
					DialectExtUtils.processDefaultValue(values, dbType, fieldMeta, fieldMeta.getDefaultValue());
					values.append(")");
				} else {
					values.append("?");
				}
				isStart = false;
			}
		}
		sql.append(") values (");
		sql.append(values);
		sql.append(") ");
		// 非全部是主键
		if (!allIds) {
			// String columnName;
			sql.append(" ON CONFLICT (");
			for (int i = 0, n = entityMeta.getIdArray().length; i < n; i++) {
				if (i > 0) {
					sql.append(",");
				}
				columnName = entityMeta.getColumnName(entityMeta.getIdArray()[i]);
				sql.append(ReservedWordsUtil.convertWord(columnName, dbType));
			}
			sql.append(" ) DO UPDATE SET ");

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
	 * @TODO 定义当使用sequence或identity时,是否允许自定义值(即不通过sequence或identity产生，而是由外部直接赋值)
	 * @param pkStrategy
	 * @return
	 */
	public static boolean isAssignPKValue(PKStrategy pkStrategy) {
		if (pkStrategy == null) {
			return true;
		}
		// sequence
		if (pkStrategy.equals(PKStrategy.SEQUENCE)) {
			return false;
		}
		// postgresql10+ 支持identity
		if (pkStrategy.equals(PKStrategy.IDENTITY)) {
			return false;
		}
		return true;
	}
}
