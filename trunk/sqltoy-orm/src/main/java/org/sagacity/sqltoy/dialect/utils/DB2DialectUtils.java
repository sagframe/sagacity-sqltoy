package org.sagacity.sqltoy.dialect.utils;

import java.sql.Connection;
import java.util.HashSet;

import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.callback.DecryptHandler;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.FieldMeta;
import org.sagacity.sqltoy.config.model.OperateType;
import org.sagacity.sqltoy.config.model.PKStrategy;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlToyResult;
import org.sagacity.sqltoy.model.IgnoreKeyCaseMap;
import org.sagacity.sqltoy.model.QueryExecutor;
import org.sagacity.sqltoy.model.QueryResult;
import org.sagacity.sqltoy.model.inner.QueryExecutorExtend;
import org.sagacity.sqltoy.plugins.IUnifyFieldsHandler;
import org.sagacity.sqltoy.utils.ReservedWordsUtil;
import org.sagacity.sqltoy.utils.SqlUtilsExt;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * @project sqltoy-orm
 * @description 提供db2数据库通用的操作功能实现,为不同版本提供支持
 * @author zhongxuchen
 * @version v1.0,Date:2015年2月28日
 */
public class DB2DialectUtils {

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
		StringBuilder sql = new StringBuilder();
		String innerSql = sqlToyConfig.isHasFast() ? sqlToyConfig.getFastSql(dialect) : sqlToyConfig.getSql(dialect);
		// sql中是否存在排序或union
		boolean hasOrderOrUnion = DialectUtils.hasOrderByOrUnion(innerSql);
		// 给原始sql标记上特殊的开始和结尾，便于sql拦截器快速定位到原始sql并进行条件补充
		innerSql = SqlUtilsExt.markOriginalSql(innerSql);
		if (sqlToyConfig.isHasFast()) {
			sql.append(sqlToyConfig.getFastPreSql(dialect));
			if (!sqlToyConfig.isIgnoreBracket()) {
				sql.append(" (");
			}
		}
		// 存在order 或union 则在sql外包裹一层
		if (hasOrderOrUnion) {
			sql.append("select " + SqlToyConstants.INTERMEDIATE_TABLE + ".* from (");
		}
		sql.append(innerSql);
		if (hasOrderOrUnion) {
			sql.append(") ");
			sql.append(SqlToyConstants.INTERMEDIATE_TABLE);
			sql.append(" ");
		}
		sql.append(" order by rand() fetch first ");
		sql.append(randomCount);
		sql.append(" rows only ");
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
				extend, decryptHandler, conn, dbType, 0, fetchSize, maxRows);
	}

	/**
	 * @todo 处理加工对象基于db2 的merge into 语句
	 * @param unifyFieldsHandler
	 * @param dbType
	 * @param entityMeta
	 * @param pkStrategy
	 * @param forceUpdateFields
	 * @param fromTable
	 * @param isNullFunction
	 * @param sequence
	 * @param isAssignPK
	 * @param tableName
	 * @return
	 */
	public static String getSaveOrUpdateSql(IUnifyFieldsHandler unifyFieldsHandler, Integer dbType,
			EntityMeta entityMeta, PKStrategy pkStrategy, String[] forceUpdateFields, String fromTable,
			String isNullFunction, String sequence, boolean isAssignPK, String tableName) {
		String realTable = entityMeta.getSchemaTable(tableName, dbType);
		// 在无主键的情况下产生insert sql语句
		if (entityMeta.getIdArray() == null) {
			return DialectExtUtils.generateInsertSql(dbType, entityMeta, pkStrategy, isNullFunction, sequence,
					isAssignPK, realTable);
		}
		// 将新增记录统一赋值属性模拟成默认值模式
		IgnoreKeyCaseMap<String, Object> createUnifyFields = null;
		if (unifyFieldsHandler != null && unifyFieldsHandler.createUnifyFields() != null
				&& !unifyFieldsHandler.createUnifyFields().isEmpty()) {
			createUnifyFields = new IgnoreKeyCaseMap<String, Object>();
			createUnifyFields.putAll(unifyFieldsHandler.createUnifyFields());
		}
		int columnSize = entityMeta.getFieldsArray().length;
		FieldMeta fieldMeta;
		StringBuilder sql = new StringBuilder(columnSize * 30 + 100);
		String columnName;
		sql.append("merge into ");
		sql.append(realTable);
		sql.append(" ta ");
		sql.append(" using (select ");
		for (int i = 0; i < columnSize; i++) {
			fieldMeta = entityMeta.getFieldMeta(entityMeta.getFieldsArray()[i]);
			// 处理保留字
			columnName = ReservedWordsUtil.convertWord(fieldMeta.getColumnName(), dbType);
			// 这里是db2跟oracle、sqlserver不同的地方
			wrapSelectFields(sql, i, columnName, fieldMeta.getType(), fieldMeta.getLength());
		}
		if (StringUtil.isNotBlank(fromTable)) {
			sql.append(" from ").append(fromTable);
		}
		sql.append(") tv on (");
		StringBuilder idColumns = new StringBuilder();
		// 组织on部分的主键条件判断
		for (int i = 0, n = entityMeta.getIdArray().length; i < n; i++) {
			columnName = entityMeta.getColumnName(entityMeta.getIdArray()[i]);
			// 处理保留字
			columnName = ReservedWordsUtil.convertWord(columnName, dbType);
			if (i > 0) {
				sql.append(" and ");
				idColumns.append(",");
			}
			sql.append(" ta.").append(columnName).append("=tv.").append(columnName);
			idColumns.append("ta.").append(columnName);
		}
		sql.append(" ) ");
		// 排除id的其他字段信息
		StringBuilder insertRejIdCols = new StringBuilder();
		StringBuilder insertRejIdColValues = new StringBuilder();
		// 是否全部字段都是ID主键(复合主键),匹配上则无需进行更新，只需将未匹配上的插入即可
		boolean allIds = (entityMeta.getRejectIdFieldArray() == null);
		if (!allIds) {
			// update 操作
			sql.append(" when matched then update set ");
			int rejectIdColumnSize = entityMeta.getRejectIdFieldArray().length;
			// 需要被强制修改的字段
			HashSet<String> fupc = new HashSet<String>();
			if (forceUpdateFields != null) {
				for (String field : forceUpdateFields) {
					// 增加处理保留字
					fupc.add(ReservedWordsUtil.convertWord(entityMeta.getColumnName(field), dbType));
				}
			}
			String defaultValue;
			// update 只针对非主键字段进行修改
			for (int i = 0; i < rejectIdColumnSize; i++) {
				fieldMeta = entityMeta.getFieldMeta(entityMeta.getRejectIdFieldArray()[i]);
				// 增加处理保留字
				columnName = ReservedWordsUtil.convertWord(fieldMeta.getColumnName(), dbType);
				if (i > 0) {
					sql.append(",");
					insertRejIdCols.append(",");
					insertRejIdColValues.append(",");
				}
				sql.append(" ta.").append(columnName).append("=");
				// 强制修改
				if (fupc.contains(columnName)) {
					sql.append("tv.").append(columnName);
				} else {
					sql.append(isNullFunction);
					sql.append("(tv.").append(columnName);
					sql.append(",ta.").append(columnName);
					sql.append(")");
				}
				insertRejIdCols.append(columnName);
				// 将创建人、创建时间等模拟成默认值
				defaultValue = DialectExtUtils.getInsertDefaultValue(createUnifyFields, dbType, fieldMeta);
				// 存在默认值
				if (null != defaultValue) {
					insertRejIdColValues.append(isNullFunction);
					insertRejIdColValues.append("(tv.").append(columnName).append(",");
					DialectExtUtils.processDefaultValue(insertRejIdColValues, dbType, fieldMeta, defaultValue);
					insertRejIdColValues.append(")");
				} else {
					insertRejIdColValues.append("tv.").append(columnName);
				}
			}
		}
		// 主键未匹配上则进行插入操作
		sql.append(" when not matched then insert (");
		String idsColumnStr = idColumns.toString();
		// 不考虑只有一个字段且还是主键的情况
		if (allIds) {
			sql.append(idsColumnStr.replaceAll("ta.", ""));
			sql.append(") values (");
			sql.append(idsColumnStr.replaceAll("ta.", "tv."));
		} else {
			sql.append(insertRejIdCols.toString());
			// sequence方式主键
			if (pkStrategy.equals(PKStrategy.SEQUENCE)) {
				columnName = entityMeta.getColumnName(entityMeta.getIdArray()[0]);
				// 增加处理保留字
				columnName = ReservedWordsUtil.convertWord(columnName, dbType);
				sql.append(",");
				sql.append(columnName);
				sql.append(") values (");
				sql.append(insertRejIdColValues).append(",");
				if (isAssignPK) {
					sql.append(isNullFunction);
					sql.append("(tv.").append(columnName).append(",");
					sql.append(sequence).append(") ");
				} else {
					sql.append(sequence);
				}
			} else if (pkStrategy.equals(PKStrategy.IDENTITY)) {
				columnName = entityMeta.getColumnName(entityMeta.getIdArray()[0]);
				// 增加处理保留字
				columnName = ReservedWordsUtil.convertWord(columnName, dbType);
				if (isAssignPK) {
					sql.append(",");
					sql.append(columnName);
				}
				sql.append(") values (");
				// identity 模式insert无需写插入该字段语句
				sql.append(insertRejIdColValues);
				if (isAssignPK) {
					sql.append(",").append("tv.").append(columnName);
				}
			} else {
				sql.append(",");
				sql.append(idsColumnStr.replaceAll("ta.", ""));
				sql.append(") values (");
				sql.append(insertRejIdColValues).append(",");
				sql.append(idsColumnStr.replaceAll("ta.", "tv."));
			}
		}
		sql.append(")");
		return sql.toString();
	}

	/**
	 * @todo 处理加工对象基于db2 merge into (only insert)
	 * @param dbType
	 * @param entityMeta
	 * @param pkStrategy
	 * @param fromTable
	 * @param isNullFunction
	 * @param sequence
	 * @param isAssignPK
	 * @param tableName
	 * @return
	 */
	public static String getSaveIgnoreExistSql(Integer dbType, EntityMeta entityMeta, PKStrategy pkStrategy,
			String fromTable, String isNullFunction, String sequence, boolean isAssignPK, String tableName) {
		String realTable = entityMeta.getSchemaTable(tableName, dbType);
		// 在无主键的情况下产生insert sql语句
		if (entityMeta.getIdArray() == null) {
			return DialectExtUtils.generateInsertSql(dbType, entityMeta, pkStrategy, isNullFunction, sequence,
					isAssignPK, realTable);
		}
		int columnSize = entityMeta.getFieldsArray().length;
		FieldMeta fieldMeta;
		StringBuilder sql = new StringBuilder(columnSize * 30 + 100);
		String columnName;
		sql.append("merge into ");
		sql.append(realTable);
		sql.append(" ta ");
		sql.append(" using (select ");
		for (int i = 0; i < columnSize; i++) {
			fieldMeta = entityMeta.getFieldMeta(entityMeta.getFieldsArray()[i]);
			// 增加处理保留字
			columnName = ReservedWordsUtil.convertWord(fieldMeta.getColumnName(), dbType);
			// 这里是db2跟oracle、sqlserver不同的地方
			wrapSelectFields(sql, i, columnName, fieldMeta.getType(), fieldMeta.getLength());
		}
		if (StringUtil.isNotBlank(fromTable)) {
			sql.append(" from ").append(fromTable);
		}
		sql.append(") tv on (");
		StringBuilder idColumns = new StringBuilder();
		// 组织on部分的主键条件判断
		for (int i = 0, n = entityMeta.getIdArray().length; i < n; i++) {
			columnName = entityMeta.getColumnName(entityMeta.getIdArray()[i]);
			// 增加处理保留字
			columnName = ReservedWordsUtil.convertWord(columnName, dbType);
			if (i > 0) {
				sql.append(" and ");
				idColumns.append(",");
			}
			sql.append(" ta.").append(columnName).append("=tv.").append(columnName);
			idColumns.append("ta.").append(columnName);
		}
		sql.append(" ) ");
		// 排除id的其他字段信息
		StringBuilder insertRejIdCols = new StringBuilder();
		StringBuilder insertRejIdColValues = new StringBuilder();
		// 是否全部字段都是ID主键(复合主键),匹配上则无需进行更新，只需将未匹配上的插入即可
		boolean allIds = (entityMeta.getRejectIdFieldArray() == null);
		if (!allIds) {
			int rejectIdColumnSize = entityMeta.getRejectIdFieldArray().length;
			// update 只针对非主键字段进行修改
			for (int i = 0; i < rejectIdColumnSize; i++) {
				fieldMeta = entityMeta.getFieldMeta(entityMeta.getRejectIdFieldArray()[i]);
				// 增加处理保留字
				columnName = ReservedWordsUtil.convertWord(fieldMeta.getColumnName(), dbType);
				if (i > 0) {
					insertRejIdCols.append(",");
					insertRejIdColValues.append(",");
				}
				insertRejIdCols.append(columnName);
				insertRejIdColValues.append("tv.").append(columnName);
			}
		}
		// 主键未匹配上则进行插入操作
		sql.append(" when not matched then insert (");
		String idsColumnStr = idColumns.toString();
		// 不考虑只有一个字段且还是主键的情况
		if (allIds) {
			sql.append(idsColumnStr.replaceAll("ta.", ""));
			sql.append(") values (");
			sql.append(idsColumnStr.replaceAll("ta.", "tv."));
		} else {
			sql.append(insertRejIdCols.toString());
			// sequence方式主键
			if (pkStrategy.equals(PKStrategy.SEQUENCE)) {
				columnName = entityMeta.getColumnName(entityMeta.getIdArray()[0]);
				// 增加处理保留字
				columnName = ReservedWordsUtil.convertWord(columnName, dbType);
				sql.append(",");
				sql.append(columnName);
				sql.append(") values (");
				sql.append(insertRejIdColValues).append(",");
				if (isAssignPK) {
					sql.append(isNullFunction);
					sql.append("(tv.").append(columnName).append(",");
					sql.append(sequence).append(") ");
				} else {
					sql.append(sequence);
				}
			} else if (pkStrategy.equals(PKStrategy.IDENTITY)) {
				columnName = entityMeta.getColumnName(entityMeta.getIdArray()[0]);
				// 增加处理保留字
				columnName = ReservedWordsUtil.convertWord(columnName, dbType);
				if (isAssignPK) {
					sql.append(",");
					sql.append(columnName);
				}
				sql.append(") values (");
				// identity 模式insert无需写插入该字段语句
				sql.append(insertRejIdColValues);
				if (isAssignPK) {
					sql.append(",").append("tv.").append(columnName);
				}
			} else {
				sql.append(",");
				sql.append(idsColumnStr.replaceAll("ta.", ""));
				sql.append(") values (");
				sql.append(insertRejIdColValues).append(",");
				sql.append(idsColumnStr.replaceAll("ta.", "tv."));
			}
		}
		sql.append(")");
		return sql.toString();
	}

	/**
	 * @todo 统一组织select 字段信息
	 * @param sql
	 * @param index
	 * @param columnName
	 * @param fieldType
	 * @param length
	 */
	private static void wrapSelectFields(StringBuilder sql, int index, String columnName, int fieldType, int length) {
		if (index > 0) {
			sql.append(",");
		}
		if (fieldType == java.sql.Types.VARCHAR) {
			sql.append("cast(? as varchar(" + length + "))");
		} else if (fieldType == java.sql.Types.CHAR) {
			sql.append("cast(? as char(" + length + "))");
		} else if (fieldType == java.sql.Types.DATE) {
			sql.append("cast(? as date)");
		} else if (fieldType == java.sql.Types.NUMERIC) {
			sql.append("cast(? as numeric)");
		} else if (fieldType == java.sql.Types.DECIMAL) {
			sql.append("cast(? as decimal)");
		} else if (fieldType == java.sql.Types.INTEGER || fieldType == java.sql.Types.BIGINT
				|| fieldType == java.sql.Types.TINYINT) {
			sql.append("cast(? as integer)");
		} else if (fieldType == java.sql.Types.TIMESTAMP) {
			sql.append("cast(? as timestamp)");
		} else if (fieldType == java.sql.Types.DOUBLE) {
			sql.append("cast(? as double)");
		} else if (fieldType == java.sql.Types.FLOAT) {
			sql.append("cast(? as float)");
		} else if (fieldType == java.sql.Types.TIME) {
			sql.append("cast(? as time)");
		} else if (fieldType == java.sql.Types.CLOB) {
			sql.append("cast(? as clob(" + length + "))");
		} else if (fieldType == java.sql.Types.BOOLEAN) {
			sql.append("cast(? as boolean)");
		} else if (fieldType == java.sql.Types.BINARY) {
			sql.append("cast(? as BINARY LARGE OBJECT(" + length + "))");
		} else if (fieldType == java.sql.Types.BLOB) {
			sql.append("cast(? as blob(" + length + "))");
		} else {
			sql.append("?");
		}
		sql.append(" as ");
		sql.append(columnName);
	}
}
