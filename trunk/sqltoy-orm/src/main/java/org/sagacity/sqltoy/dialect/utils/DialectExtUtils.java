/**
 *
 */
package org.sagacity.sqltoy.dialect.utils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;

import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.FieldMeta;
import org.sagacity.sqltoy.config.model.PKStrategy;
import org.sagacity.sqltoy.model.IgnoreCaseSet;
import org.sagacity.sqltoy.model.IgnoreKeyCaseMap;
import org.sagacity.sqltoy.plugins.IUnifyFieldsHandler;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;
import org.sagacity.sqltoy.utils.DateUtil;
import org.sagacity.sqltoy.utils.ReservedWordsUtil;
import org.sagacity.sqltoy.utils.SqlUtil;
import org.sagacity.sqltoy.utils.SqlUtilsExt;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * @project sagacity-sqltoy
 * @description 将原本DialectUtils中的部分功能抽离出来, 从而避免DialectUtils跟一些类之间的互相调用
 * @author zhongxuchen
 * @version v1.0, Date:2020年7月30日
 * @modify 2022-10-19 修改processDefaultValue修复oracle、db2日期类型的支持
 * @modify 2023-10-24 修改了sqlCacheKey，增加pkStrategy作为key的组成,因为gaussdb
 *         save情况下sequence策略会变成assign，saveAll则保持sequence
 */
public class DialectExtUtils {
	// POJO 对应的insert sql语句缓存
	private static ConcurrentHashMap<String, String> insertSqlCache = new ConcurrentHashMap<String, String>(256);

	// POJO 对应的merge into not match insert语句缓存
	private static ConcurrentHashMap<String, String> mergeIgnoreSqlCache = new ConcurrentHashMap<String, String>(256);

	// POJO 对应的insert into ON CONFLICT语句缓存
	private static ConcurrentHashMap<String, String> insertIgnoreSqlCache = new ConcurrentHashMap<String, String>(256);

	/**
	 * @todo 产生对象对应的insert sql语句
	 * @param unifyFieldsHandler
	 * @param dbType
	 * @param entityMeta
	 * @param pkStrategy
	 * @param isNullFunction
	 * @param sequence
	 * @param isAssignPK(此参数有待改进2023-5-30，应该全部为true)
	 * @param tableName
	 * @return
	 */
	public static String generateInsertSql(IUnifyFieldsHandler unifyFieldsHandler, Integer dbType,
			EntityMeta entityMeta, PKStrategy pkStrategy, String isNullFunction, String sequence, boolean isAssignPK,
			String tableName) {
		// update 2023-5-13 增加缓存机制，避免每次动态组织insert语句
		String sqlCacheKey = getCacheKey(entityMeta, tableName, dbType, pkStrategy);
		String insertSql = insertSqlCache.get(sqlCacheKey);
		if (null != insertSql) {
			return insertSql;
		}
		int columnSize = entityMeta.getFieldsArray().length;
		StringBuilder sql = new StringBuilder(columnSize * 20 + 30);
		StringBuilder values = new StringBuilder(columnSize * 2 - 1);
		sql.append("insert into ");
		sql.append(entityMeta.getSchemaTable(tableName, dbType));
		sql.append(" (");
		FieldMeta fieldMeta;
		String field;
		boolean isStart = true;
		boolean isSupportNULL = StringUtil.isBlank(isNullFunction) ? false : true;
		String columnName;
		// 创建记录时，创建时间、最后修改时间等取数据库时间
		IgnoreCaseSet createSqlTimeFields = (unifyFieldsHandler == null
				|| unifyFieldsHandler.createSqlTimeFields() == null) ? new IgnoreCaseSet()
						: unifyFieldsHandler.createSqlTimeFields();
		String currentTimeStr;
		boolean isString = false;
		for (int i = 0; i < columnSize; i++) {
			field = entityMeta.getFieldsArray()[i];
			fieldMeta = entityMeta.getFieldMeta(field);
			isString = false;
			if ("java.lang.string".equals(fieldMeta.getFieldType())) {
				isString = true;
			}
			columnName = ReservedWordsUtil.convertWord(fieldMeta.getColumnName(), dbType);
			if (fieldMeta.isPK()) {
				// identity主键策略，且支持主键手工赋值
				if (pkStrategy.equals(PKStrategy.IDENTITY)) {
					// 目前只有mysql支持
					if (isAssignPK) {
						if (!isStart) {
							sql.append(",");
							values.append(",");
						}
						sql.append(columnName);
						values.append("?");
						isStart = false;
					}
				} // sequence 策略，oracle12c之后的identity机制统一转化为sequence模式
				else if (pkStrategy.equals(PKStrategy.SEQUENCE)) {
					if (!isStart) {
						sql.append(",");
						values.append(",");
					}
					sql.append(columnName);
					if (isAssignPK && isSupportNULL) {
						values.append(isNullFunction);
						values.append("(?,").append(sequence).append(")");
					} else {
						values.append(sequence);
					}
					isStart = false;
				} else {
					if (!isStart) {
						sql.append(",");
						values.append(",");
					}
					sql.append(columnName);
					values.append("?");
					isStart = false;
				}
			} else {
				if (!isStart) {
					sql.append(",");
					values.append(",");
				}
				sql.append(columnName);
				// kudu 中文会产生乱码
				if (dbType == DBType.IMPALA && isString) {
					values.append("cast(? as string)");
				} else {
					// 2023-5-11 新增操作待增加对default值的处理,nvl(?,current_timestamp)
					currentTimeStr = SqlUtil.getDBTime(dbType, fieldMeta, createSqlTimeFields);
					if (null != currentTimeStr) {
						values.append(isNullFunction).append("(?,").append(currentTimeStr).append(")");
					} else {
						values.append("?");
					}
				}
				isStart = false;
			}
		}
		sql.append(") ");
		sql.append(" values (");
		sql.append(values);
		sql.append(")");
		insertSql = sql.toString();
		insertSqlCache.put(sqlCacheKey, insertSql);
		return insertSql;
	}

	/**
	 * @todo 统一对表字段默认值进行处理, 主要针对merge into 等sql语句
	 * @param sql
	 * @param dbType
	 * @param fieldMeta
	 * @param defaultValue
	 */
	public static void processDefaultValue(StringBuilder sql, int dbType, FieldMeta fieldMeta, String defaultValue) {
		// EntityManager解析时已经小写化处理
		String fieldType = fieldMeta.getFieldType();
		// 字符串类型
		if ("java.lang.string".equals(fieldType)) {
			if (!defaultValue.startsWith("'")) {
				sql.append("'");
			}
			sql.append(defaultValue);
			if (!defaultValue.endsWith("'")) {
				sql.append("'");
			}
			return;
		}
		// 是否是各种数据库的当前时间、日期的字符
		String defaultLow = defaultValue.toLowerCase();
		boolean isCurrentTime = SqlUtilsExt.isCurrentTime(defaultLow);
		int dateType = -1;
		// 时间
		if ("java.time.localtime".equals(fieldType) || "java.sql.time".equals(fieldType)) {
			dateType = 1;
		} else if ("java.time.localdate".equals(fieldType)) {
			dateType = 2;
		} else if ("java.time.localdatetime".equals(fieldType) || "java.util.date".equals(fieldType)
				|| "java.sql.date".equals(fieldType)) {
			dateType = 3;
		} else if ("java.sql.timestamp".equals(fieldType) || "oracle.sql.timestamp".equals(fieldType)) {
			dateType = 4;
		}
		// 1、固定值;2、类似CURRENT TIMESTAMP 关键词
		String result = defaultValue;
		if (isCurrentTime && dateType != -1) {
			if (dateType == 1) {
				result = DateUtil.formatDate(DateUtil.getNowTime(), "HH:mm:ss");
			} else if (dateType == 2) {
				result = DateUtil.formatDate(DateUtil.getNowTime(), "yyyy-MM-dd");
			} else if (dateType == 3) {
				result = DateUtil.formatDate(DateUtil.getNowTime(), "yyyy-MM-dd HH:mm:ss");
			} else if (dateType == 4) {
				result = DateUtil.formatDate(DateUtil.getNowTime(), "yyyy-MM-dd HH:mm:ss.SSS");
			}
		}

		// 日期类型
		if (dateType != -1) {
			if (!result.startsWith("'") && !result.endsWith("'")) {
				result = "'".concat(result).concat("'");
			}
			// oracle、db2支持merge into场景(sqlserver具有自行转换能力，无需进行格式转换)
			if (dateType == 1) {
				if (dbType == DBType.DB2) {
					result = "time(" + result + ")";
				} else if (dbType == DBType.ORACLE || dbType == DBType.ORACLE11) {
					// oracle 没有time类型,因此本行的逻辑实际不会生效
					result = "to_date(" + result + ",'HH24:mi:ss')";
				}
			} else if (dateType == 2) {
				if (dbType == DBType.DB2) {
					result = "date(" + result + ")";
				} else if (dbType == DBType.ORACLE || dbType == DBType.ORACLE11) {
					result = "to_date(" + result + ",'yyyy-MM-dd')";
				}
			} else if (dateType == 3) {
				if (dbType == DBType.DB2) {
					result = "timestamp(" + result + ")";
				} else if (dbType == DBType.ORACLE || dbType == DBType.ORACLE11) {
					result = "to_date(" + result + ",'yyyy-MM-dd HH24:mi:ss')";
				}
			} // timestamp 类型进行特殊处理，避免批量插入时，所有记录时间一致导致精度损失
			else if (dateType == 4) {
				if (dbType == DBType.DB2) {
					if (isCurrentTime) {
						result = "CURRENT TIMESTAMP";
					} else {
						result = "timestamp(" + result + ")";
					}
				} else if (dbType == DBType.ORACLE || dbType == DBType.ORACLE11) {
					if (isCurrentTime) {
						result = "CURRENT_TIMESTAMP";
					} else {
						result = "TO_TIMESTAMP(" + result + ",'yyyy-MM-dd HH24:mi:ss.FF')";
					}
				} else if (dbType == DBType.MYSQL || dbType == DBType.MYSQL57 || dbType == DBType.POSTGRESQL
						|| dbType == DBType.POSTGRESQL15 || dbType == DBType.DM || dbType == DBType.GAUSSDB
						|| dbType == DBType.MOGDB || dbType == DBType.OCEANBASE || dbType == DBType.SQLITE
						|| dbType == DBType.KINGBASE || dbType == DBType.SQLSERVER || dbType == DBType.TIDB
						|| dbType == DBType.H2) {
					if (isCurrentTime) {
						result = "CURRENT_TIMESTAMP";
					}
				}
			}
		}
		sql.append(result);
	}

	/**
	 * @TODO 组织判断unique的sql(从DialectUtils中抽离避免循环调用)
	 * @param entityMeta
	 * @param realParamNamed
	 * @param dbType
	 * @param tableName
	 * @return
	 */
	public static String wrapUniqueSql(EntityMeta entityMeta, String[] realParamNamed, Integer dbType,
			String tableName) {
		// 构造查询语句(固定1避免无主键表导致select from 问题)
		StringBuilder queryStr = new StringBuilder("select 1 ");
		// 如果存在主键，则查询主键字段
		if (null != entityMeta.getIdArray()) {
			for (String idFieldName : entityMeta.getIdArray()) {
				queryStr.append(",");
				queryStr.append(ReservedWordsUtil.convertWord(entityMeta.getColumnName(idFieldName), dbType));
			}
		}
		queryStr.append(" from ");
		queryStr.append(entityMeta.getSchemaTable(tableName, dbType));
		queryStr.append(" where  ");
		for (int i = 0; i < realParamNamed.length; i++) {
			if (i > 0) {
				queryStr.append(" and ");
			}
			queryStr.append(ReservedWordsUtil.convertWord(entityMeta.getColumnName(realParamNamed[i]), dbType))
					.append("=:" + realParamNamed[i]);
		}
		return queryStr.toString();
	}

	/**
	 * @todo 处理加工对象基于dm、oceanbase、oracle数据库的saveIgnoreExist
	 * @param unifyFieldsHandler
	 * @param dbType
	 * @param entityMeta
	 * @param pkStrategy
	 * @param fromTable          (不同数据库方言虚表，如dual)
	 * @param isNullFunction
	 * @param sequence
	 * @param isAssignPK
	 * @param tableName
	 * @return
	 */
	public static String mergeIgnore(IUnifyFieldsHandler unifyFieldsHandler, Integer dbType, EntityMeta entityMeta,
			PKStrategy pkStrategy, String fromTable, String isNullFunction, String sequence, boolean isAssignPK,
			String tableName) {
		// 在无主键的情况下产生insert sql语句
		String realTable = entityMeta.getSchemaTable(tableName, dbType);
		if (entityMeta.getIdArray() == null && entityMeta.getUniqueIndex() == null) {
			return generateInsertSql(unifyFieldsHandler, dbType, entityMeta, pkStrategy, isNullFunction, sequence,
					isAssignPK, realTable);
		}
		// sql 缓存，避免每次重复产生
		String sqlCacheKey = getCacheKey(entityMeta, tableName, dbType, pkStrategy);
		String mergeIgnoreSql = mergeIgnoreSqlCache.get(sqlCacheKey);
		if (null != mergeIgnoreSql) {
			return mergeIgnoreSql;
		}
		// 创建记录时，创建时间、最后修改时间等取数据库时间
		IgnoreCaseSet createSqlTimeFields = (unifyFieldsHandler == null
				|| unifyFieldsHandler.createSqlTimeFields() == null) ? new IgnoreCaseSet()
						: unifyFieldsHandler.createSqlTimeFields();
		IgnoreCaseSet forceUpdateSqlTimeFields = new IgnoreCaseSet();
		if (unifyFieldsHandler != null && unifyFieldsHandler.forceUpdateFields() != null) {
			forceUpdateSqlTimeFields = unifyFieldsHandler.forceUpdateFields();
		}
		String currentTimeStr;
		int columnSize = entityMeta.getFieldsArray().length;
		StringBuilder sql = new StringBuilder(columnSize * 30 + 100);
		String columnName;
		sql.append("merge into ");
		sql.append(realTable);
		// postgresql15+ 不支持别名
		if (DBType.POSTGRESQL15 != dbType) {
			sql.append(" ta ");
		}
		sql.append(" using (select ");
		FieldMeta fieldMeta;
		for (int i = 0; i < columnSize; i++) {
			fieldMeta = entityMeta.getFieldMeta(entityMeta.getFieldsArray()[i]);
			columnName = ReservedWordsUtil.convertWord(fieldMeta.getColumnName(), dbType);
			if (i > 0) {
				sql.append(",");
			}
			// postgresql15+ 需要case(? as type) as column
			if (DBType.POSTGRESQL15 == dbType) {
				PostgreSqlDialectUtils.wrapSelectFields(sql, columnName, fieldMeta);
			} else if (DBType.H2 == dbType) {
				H2DialectUtils.wrapSelectFields(sql, columnName, fieldMeta);
			} else if (DBType.DB2 == dbType) {
				DB2DialectUtils.wrapSelectFields(sql, columnName, fieldMeta);
			} else {
				sql.append("? as ");
				sql.append(columnName);
			}
		}
		if (StringUtil.isNotBlank(fromTable)) {
			sql.append(" from ").append(fromTable);
		}
		// sql.append(") tv on (");
		sql.append(SqlToyConstants.MERGE_ALIAS_ON);
		StringBuilder idColumns = new StringBuilder();
		boolean hasId = (entityMeta.getIdArray() == null) ? false : true;
		String[] fields = hasId ? entityMeta.getIdArray() : entityMeta.getUniqueIndex().getColumns();
		// 组织on部分的主键条件判断(update 2024-10-16 增加无主键场景下,用唯一索引来替代的方式)
		for (int i = 0, n = fields.length; i < n; i++) {
			columnName = hasId ? entityMeta.getColumnName(fields[i]) : fields[i];
			columnName = ReservedWordsUtil.convertWord(columnName, dbType);
			if (i > 0) {
				sql.append(" and ");
				idColumns.append(",");
			}
			// 不支持别名
			if (DBType.POSTGRESQL15 == dbType) {
				sql.append(realTable + ".");
			} else {
				sql.append("ta.");
			}
			sql.append(columnName).append("=tv.").append(columnName);
			idColumns.append("ta.").append(columnName);
		}
		sql.append(" ) ");
		// 排除id的其他字段信息
		StringBuilder insertRejIdCols = new StringBuilder();
		StringBuilder insertRejIdColValues = new StringBuilder();
		// 是否全部是ID,insert 按主键来构造语句
		boolean allIds = (entityMeta.getRejectIdFieldArray() == null);
		// 部分主键，则按非主键来构造insert字段
		if (!allIds) {
			int rejectIdColumnSize = entityMeta.getRejectIdFieldArray().length;
			// update 只针对非主键字段进行修改
			for (int i = 0; i < rejectIdColumnSize; i++) {
				fieldMeta = entityMeta.getFieldMeta(entityMeta.getRejectIdFieldArray()[i]);
				columnName = ReservedWordsUtil.convertWord(fieldMeta.getColumnName(), dbType);
				if (i > 0) {
					insertRejIdCols.append(",");
					insertRejIdColValues.append(",");
				}
				insertRejIdCols.append(columnName);
				// 使用数据库时间nvl(tv.field,current_timestamp)
				currentTimeStr = SqlUtil.getDBTime(dbType, fieldMeta, createSqlTimeFields);
				if (null != currentTimeStr) {
					if (forceUpdateSqlTimeFields.contains(fieldMeta.getFieldName())) {
						insertRejIdColValues.append(currentTimeStr);
					} else {
						insertRejIdColValues.append(isNullFunction);
						insertRejIdColValues.append("(tv.").append(columnName);
						insertRejIdColValues.append(",").append(currentTimeStr);
						insertRejIdColValues.append(")");
					}
				} else {
					insertRejIdColValues.append("tv.").append(columnName);
				}
			}
		}
		// 主键未匹配上则进行插入操作
		sql.append(SqlToyConstants.MERGE_INSERT);
		sql.append(" (");
		String idsColumnStr = idColumns.toString();
		// 不考虑只有一个字段且还是主键的情况
		if (allIds) {
			sql.append(idsColumnStr.replace("ta.", ""));
			sql.append(") values (");
			sql.append(idsColumnStr.replace("ta.", "tv."));
		} else {
			sql.append(insertRejIdCols.toString());
			// 无主键
			if (pkStrategy == null) {
				sql.append(") values (");
				sql.append(insertRejIdColValues);
			} else {
				// sequence方式主键
				if (pkStrategy.equals(PKStrategy.SEQUENCE)) {
					columnName = entityMeta.getColumnName(entityMeta.getIdArray()[0]);
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
					sql.append(idsColumnStr.replace("ta.", ""));
					sql.append(") values (");
					sql.append(insertRejIdColValues).append(",");
					sql.append(idsColumnStr.replace("ta.", "tv."));
				}
			}
		}
		sql.append(")");
		mergeIgnoreSql = sql.toString();
		mergeIgnoreSqlCache.put(sqlCacheKey, mergeIgnoreSql);
		return mergeIgnoreSql;
	}

	/**
	 * @TODO 针对postgresql\kingbase\guassdb\mogdb等数据库
	 * @param unifyFieldsHandler
	 * @param dbType
	 * @param entityMeta
	 * @param pkStrategy
	 * @param isNullFunction
	 * @param sequence
	 * @param isAssignPK
	 * @param tableName
	 * @return
	 */
	public static String insertIgnore(IUnifyFieldsHandler unifyFieldsHandler, Integer dbType, EntityMeta entityMeta,
			PKStrategy pkStrategy, String isNullFunction, String sequence, boolean isAssignPK, String tableName) {
		// update 2023-5-13 提供缓存方式快速获取sql
		String sqlCacheKey = getCacheKey(entityMeta, tableName, dbType, pkStrategy);
		String insertIgnoreSql = insertIgnoreSqlCache.get(sqlCacheKey);
		if (null != insertIgnoreSql) {
			return insertIgnoreSql;
		}
		int columnSize = entityMeta.getFieldsArray().length;
		StringBuilder sql = new StringBuilder(columnSize * 20 + 30);
		StringBuilder values = new StringBuilder(columnSize * 2 - 1);
		if (dbType == DBType.GAUSSDB) {
			sql.append("insert ignore into ");
		} // mogdb支持insert into do nothing
		else {
			sql.append("insert into ");
		}
		sql.append(entityMeta.getSchemaTable(tableName, dbType));
		sql.append(" (");
		FieldMeta fieldMeta;
		String field;
		String columnName;
		boolean isStart = true;
		// 创建记录时，创建时间、最后修改时间等取数据库时间
		IgnoreCaseSet createSqlTimeFields = (unifyFieldsHandler == null
				|| unifyFieldsHandler.createSqlTimeFields() == null) ? new IgnoreCaseSet()
						: unifyFieldsHandler.createSqlTimeFields();
		String currentTimeStr;
		for (int i = 0; i < columnSize; i++) {
			field = entityMeta.getFieldsArray()[i];
			fieldMeta = entityMeta.getFieldMeta(field);
			columnName = ReservedWordsUtil.convertWord(fieldMeta.getColumnName(), dbType);
			if (fieldMeta.isPK()) {
				// identity主键策略，且支持主键手工赋值
				if (pkStrategy.equals(PKStrategy.IDENTITY)) {
					if (isAssignPK) {
						if (!isStart) {
							sql.append(",");
							values.append(",");
						}
						sql.append(columnName);
						values.append("?");
						isStart = false;
					}
				} else if (pkStrategy.equals(PKStrategy.SEQUENCE)) {
					if (!isStart) {
						sql.append(",");
						values.append(",");
					}
					sql.append(columnName);
					values.append(isNullFunction).append("(?,").append(sequence).append(")");
					isStart = false;
				} else {
					if (!isStart) {
						sql.append(",");
						values.append(",");
					}
					sql.append(columnName);
					values.append("?");
					isStart = false;
				}
			} else {
				if (!isStart) {
					sql.append(",");
					values.append(",");
				}
				sql.append(columnName);
				currentTimeStr = SqlUtil.getDBTime(dbType, fieldMeta, createSqlTimeFields);
				if (null != currentTimeStr) {
					values.append(isNullFunction);
					values.append("(?,");
					values.append(currentTimeStr);
					values.append(")");
				} else {
					values.append("?");
				}
				isStart = false;
			}
		}
		sql.append(") values ( ");
		sql.append(values);
		sql.append(")");
		// 增加do noting
		if (dbType != DBType.GAUSSDB && entityMeta.getIdArray() != null) {
			sql.append(" ON CONFLICT (");
			for (int i = 0, n = entityMeta.getIdArray().length; i < n; i++) {
				if (i > 0) {
					sql.append(",");
				}
				columnName = entityMeta.getColumnName(entityMeta.getIdArray()[i]);
				sql.append(ReservedWordsUtil.convertWord(columnName, dbType));
			}
			sql.append(" ) DO NOTHING ");
		}
		insertIgnoreSql = sql.toString();
		insertIgnoreSqlCache.put(sqlCacheKey, insertIgnoreSql);
		return insertIgnoreSql;
	}

	/**
	 * @TODO 解决saveOrUpdate场景对一些记录无法判断是新增导致无法对创建人、创建时间等属性进行统一赋值，从而通过默认值模式来解决
	 * @param createUnifyFields
	 * @param dbType
	 * @param fieldMeta
	 * @return
	 */
	public static String getInsertDefaultValue(IgnoreKeyCaseMap<String, Object> createUnifyFields, Integer dbType,
			FieldMeta fieldMeta) {
		if (createUnifyFields == null || createUnifyFields.isEmpty()
				|| !createUnifyFields.containsKey(fieldMeta.getFieldName())) {
			return fieldMeta.getDefaultValue();
		}
		Object unifyFieldValue = createUnifyFields.get(fieldMeta.getFieldName());
		if (unifyFieldValue != null) {
			if (unifyFieldValue instanceof String) {
				return (String) unifyFieldValue;
			} else if (unifyFieldValue instanceof Number) {
				return unifyFieldValue.toString();
			} else {
				// entityManager已经做了小写化处理
				String fieldType = fieldMeta.getFieldType();
				if ("java.time.localdate".equals(fieldType)) {
					return DateUtil.formatDate(unifyFieldValue, DateUtil.FORMAT.DATE_HORIZONTAL);
				} else if ("java.time.localtime".equals(fieldType) || "java.sql.time".equals(fieldType)) {
					return DateUtil.formatDate(unifyFieldValue, "HH:mm:ss");
				} else if ("java.time.localdatetime".equals(fieldType) || "java.sql.timestamp".equals(fieldType)
						|| "java.util.date".equals(fieldType) || "java.sql.date".equals(fieldType)) {
					return DateUtil.formatDate(unifyFieldValue, DateUtil.FORMAT.DATETIME_HORIZONTAL);
				}
				// 统一传参数值为日期类型，但数据库中是数字或字符串类型
				if ((unifyFieldValue instanceof Date) || (unifyFieldValue instanceof LocalDate)
						|| (unifyFieldValue instanceof LocalDateTime)) {
					if ("java.lang.integer".equals(fieldType) || "int".equals(fieldType)) {
						return DateUtil.formatDate(unifyFieldValue, DateUtil.FORMAT.DATE_8CHAR);
					} else if ("java.lang.long".equals(fieldType) || "java.math.biginteger".equals(fieldType)
							|| "long".equals(fieldType)) {
						return DateUtil.formatDate(unifyFieldValue, "yyyyMMddHHmmss");
					} else if ("java.lang.string".equals(fieldType)) {
						if (fieldMeta.getLength() >= 19) {
							return DateUtil.formatDate(unifyFieldValue, DateUtil.FORMAT.DATETIME_HORIZONTAL);
						}
						if (fieldMeta.getLength() >= 14) {
							return DateUtil.formatDate(unifyFieldValue, "yyyyMMddHHmmss");
						}
						if (fieldMeta.getLength() >= 8) {
							return DateUtil.formatDate(unifyFieldValue, DateUtil.FORMAT.DATE_8CHAR);
						}
					}
				}
			}
		}
		return fieldMeta.getDefaultValue();
	}

	/**
	 * @TODO 组织对象操作sql的key
	 * @param entityMeta
	 * @param tableName
	 * @param dbType
	 * @param pkStrategy
	 * @return
	 */
	private static String getCacheKey(EntityMeta entityMeta, String tableName, int dbType, PKStrategy pkStrategy) {
		// update 2023-10-24 增加主键策略作为缓存key的组成，因为gaussdb
		// save单条保存和saveAll批量机制存在差异，save时sequence策略会提前获取sequence值，然后变成了assign策略
		return entityMeta.getEntityClass().getName() + "[" + tableName + "]dbType=" + dbType
				+ ((pkStrategy == null) ? "" : pkStrategy.getValue());
	}
}
