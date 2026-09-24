package org.sagacity.sqltoy.dialect.utils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

import org.sagacity.sqltoy.SqlToyThreadDataHolder;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.FieldMeta;
import org.sagacity.sqltoy.config.model.PKStrategy;
import org.sagacity.sqltoy.model.DBProfile;
import org.sagacity.sqltoy.model.IgnoreCaseSet;
import org.sagacity.sqltoy.model.IgnoreKeyCaseMap;
import org.sagacity.sqltoy.model.JdbcTypes;
import org.sagacity.sqltoy.plugins.IUnifyFieldsHandler;
import org.sagacity.sqltoy.utils.DataSourceUtils;
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
 * @version v1.0,Date:2020-07-30
 * @modify Date:2022-10-19 修改processDefaultValue修复oracle、db2日期类型的支持
 * @modify Date:2023-10-24 修改了sqlCacheKey，增加pkStrategy作为key的组成,因为gaussdb
 *         save情况下sequence策略会变成assign，saveAll则保持sequence
 */
public class DialectExtUtils {
	// POJO 对应的insert sql语句缓存
	private static ConcurrentHashMap<String, String> insertSqlCache = new ConcurrentHashMap<String, String>(256);

	// POJO 对应的insert into ON CONFLICT语句缓存
	private static ConcurrentHashMap<String, String> insertIgnoreSqlCache = new ConcurrentHashMap<String, String>(256);

	/**
	 * update 2026-9-12 dbType入径(无连接档案的直调场景):构建最小DBProfile档案后委托核心实现
	 */
	public static String generateInsertSql(IUnifyFieldsHandler unifyFieldsHandler, Integer dbType,
			EntityMeta entityMeta, PKStrategy pkStrategy, String sequence, boolean isAssignPK, String tableName) {
		return generateInsertSql(unifyFieldsHandler,
				new DBProfile(null, DataSourceUtils.getDialect(dbType), dbType, null, 0, null, null, false), entityMeta,
				pkStrategy, sequence, isAssignPK, tableName);
	}

	/**
	 * 产生对象对应的insert sql语句
	 * 
	 * @param unifyFieldsHandler
	 * @param profile
	 * @param entityMeta
	 * @param pkStrategy
	 * @param isNullFunction
	 * @param sequence
	 * @param isAssignPK(此参数有待改进2023-5-30，应该全部为true)
	 * @param tableName
	 * @return
	 */
	public static String generateInsertSql(IUnifyFieldsHandler unifyFieldsHandler, DBProfile profile,
			EntityMeta entityMeta, PKStrategy pkStrategy, String sequence, boolean isAssignPK, String tableName) {
		String isNullFunction = profile.getNullFunction();
		Integer dbType = profile.getDbType();
		// update 2023-5-13 增加缓存机制，避免每次动态组织insert语句
		String sqlCacheKey = getCacheKey(entityMeta, tableName, dbType, pkStrategy);
		String insertSql = insertSqlCache.get(sqlCacheKey);
		if (null != insertSql) {
			return insertSql;
		}
		String[] fieldsArray = entityMeta.getFieldsArray(true);
		int columnSize = fieldsArray.length;
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
			field = fieldsArray[i];
			fieldMeta = entityMeta.getFieldMeta(field);
			// update 2026-9-7 sqlserver的timestamp(rowversion)列不可写入,insert语句排除该列
			// (判据entityMeta.isRowVersionField按目标库元数据校准,非sqlserver库不启用)
			if (DBType.SQLSERVER == dbType && entityMeta.isRowVersionField(fieldMeta)) {
				continue;
			}
			isString = false;
			if ("java.lang.string".equals(fieldMeta.getFieldType())) {
				isString = true;
			}
			columnName = ReservedWordsUtil.convertWord(fieldMeta.getColumnName(), dbType);
			if (fieldMeta.isPK()) {
				// identity主键策略，且支持主键手工赋值
				if (PKStrategy.IDENTITY.equals(pkStrategy)) {
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
				else if (PKStrategy.SEQUENCE.equals(pkStrategy)) {
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
				if (fieldMeta.getType() == JdbcTypes.GEOMETRY) {
					if (dbType == DBType.DM) {
						values.append("DMGEO.ST_GeomFromText(?,0)");
					} else if (dbType == DBType.DB2) {
						// update 2026-9-10 按GSE探测+nativeType分派(12.1起内置引擎与GSE可并存,
						// 纯版本分派会在GSE列上误选内置函数报-408),详见db2GeomFromTextWrap
						values.append(db2GeomFromTextWrap(fieldMeta));
					} else if (dbType == DBType.MYSQL || dbType == DBType.OCEANBASE || dbType == DBType.TIDB
							|| dbType == DBType.MYSQL57) {
						values.append("ST_GeomFromText(?,0)");
					} else if (dbType == DBType.HANA) {
						// 2026-9-11 hana空间类型为ST_GEOMETRY,构造函数ST_GeomFromText(wkt,srid)与mysql系
						// 同名同参(srid=0为平面坐标),字符串到ST_GEOMETRY无隐式转换须显式包装
						values.append("ST_GeomFromText(?,0)");
					} else if (dbType == DBType.KINGBASE) {
						// 2026-9-7 实测kes的cast仅支持cast(? as type)标准形态,cast(?,type)逗号形态报语法错误
						values.append("cast(? as geometry)");
					} else {
						values.append("?");
					}
				} else if (fieldMeta.getType() == JdbcTypes.VECTOR) {
					if ((dbType == DBType.MYSQL || dbType == DBType.MYSQL57) && !profile.isOceanBase()) {
						values.append("string_to_vector(?)");
					} else if (dbType == DBType.KINGBASE) {
						values.append("cast(? as vector)");
					} else {
						values.append("?");
					}
				} else if (fieldMeta.getType() == JdbcTypes.JSON || fieldMeta.getType() == JdbcTypes.JSONB) {
					if (dbType == DBType.KINGBASE) {
						values.append(
								"cast(? as " + ((fieldMeta.getType() == JdbcTypes.JSON) ? "json" : "jsonb") + ")");
					} else {
						values.append("?");
					}
				} else if (isString) {
					// kudu 中文会产生乱码
					if (dbType == DBType.IMPALA) {
						values.append("cast(? as string)");
					} else {
						values.append("?");
					}
				} else {
					// 针对时间类型default默认值的处理,nvl(?,current_timestamp)
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
		// update 2026-9-8 容量守卫:sqlCacheKey含tableName,分表场景(按天/按值动态表名)下
		// 每张动态表常驻一条SQL文本,上限256防无界增长(超限退化为每次生成)
		if (insertSqlCache.size() < 256) {
			insertSqlCache.put(sqlCacheKey, insertSql);
		}
		return insertSql;
	}

	/**
	 * 统一对表字段默认值进行处理, 主要针对merge into 等sql语句
	 * 
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
		String defaultLow = defaultValue.toLowerCase(Locale.ROOT);
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
						|| dbType == DBType.POSTGRESQL14 || dbType == DBType.DM || dbType == DBType.GAUSSDB
						|| dbType == DBType.OPENGAUSS || dbType == DBType.MOGDB || dbType == DBType.STARDB
						|| dbType == DBType.OSCAR || dbType == DBType.VASTBASE || dbType == DBType.OCEANBASE
						|| dbType == DBType.SQLITE || dbType == DBType.KINGBASE || dbType == DBType.SQLSERVER
						|| dbType == DBType.TIDB || dbType == DBType.H2 || dbType == DBType.DORIS
						|| dbType == DBType.STARROCKS) {
					if (isCurrentTime) {
						result = "CURRENT_TIMESTAMP";
					}
				}
			}
		}
		sql.append(result);
	}

	/**
	 * 组织判断unique的sql(从DialectUtils中抽离避免循环调用)
	 * 
	 * @param entityMeta
	 * @param realParamNamed
	 * @param profile
	 * @param tableName
	 * @return
	 */
	public static String wrapUniqueSql(EntityMeta entityMeta, String[] realParamNamed, DBProfile profile,
			String tableName) {
		Integer dbType = profile.getDbType();
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
	 * update 2026-9-12 dbType入径(无连接档案的直调场景):构建最小DBProfile档案后委托核心实现
	 */
	public static String insertIgnore(IUnifyFieldsHandler unifyFieldsHandler, Integer dbType, EntityMeta entityMeta,
			PKStrategy pkStrategy, String sequence, boolean isAssignPK, String tableName) {
		return insertIgnore(unifyFieldsHandler,
				new DBProfile(null, DataSourceUtils.getDialect(dbType), dbType, null, 0, null, null, false), entityMeta,
				pkStrategy, sequence, isAssignPK, tableName);
	}

	/**
	 * 针对postgresql\kingbase\guassdb\mogdb等数据库
	 * 
	 * @param unifyFieldsHandler
	 * @param profile
	 * @param entityMeta
	 * @param pkStrategy
	 * @param isNullFunction
	 * @param sequence
	 * @param isAssignPK
	 * @param tableName
	 * @return
	 */
	public static String insertIgnore(IUnifyFieldsHandler unifyFieldsHandler, DBProfile profile, EntityMeta entityMeta,
			PKStrategy pkStrategy, String sequence, boolean isAssignPK, String tableName) {
		String isNullFunction = profile.getNullFunction();
		Integer dbType = profile.getDbType();
		// update 2023-5-13 提供缓存方式快速获取sql
		String sqlCacheKey = getCacheKey(entityMeta, tableName, dbType, pkStrategy);
		String insertIgnoreSql = insertIgnoreSqlCache.get(sqlCacheKey);
		if (null != insertIgnoreSql) {
			return insertIgnoreSql;
		}
		String[] fieldsArray = entityMeta.getFieldsArray(true);
		int columnSize = fieldsArray.length;
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
			field = fieldsArray[i];
			fieldMeta = entityMeta.getFieldMeta(field);
			columnName = ReservedWordsUtil.convertWord(fieldMeta.getColumnName(), dbType);
			if (fieldMeta.isPK()) {
				// identity主键策略，且支持主键手工赋值
				if (PKStrategy.IDENTITY.equals(pkStrategy)) {
					if (isAssignPK) {
						if (!isStart) {
							sql.append(",");
							values.append(",");
						}
						sql.append(columnName);
						values.append("?");
						isStart = false;
					}
				} else if (PKStrategy.SEQUENCE.equals(pkStrategy)) {
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
		if (insertIgnoreSqlCache.size() < 256) {
			insertIgnoreSqlCache.put(sqlCacheKey, insertIgnoreSql);
		}
		return insertIgnoreSql;
	}

	/**
	 * 解决saveOrUpdate场景对一些记录无法判断是新增导致无法对创建人、创建时间等属性进行统一赋值，从而通过默认值模式来解决
	 * 
	 * @param createUnifyFields
	 * @param profile
	 * @param fieldMeta
	 * @return
	 */
	public static String getInsertDefaultValue(IgnoreKeyCaseMap<String, Object> createUnifyFields, DBProfile profile,
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
	 * 组织对象操作sql的key
	 * 
	 * @param entityMeta
	 * @param tableName
	 * @param dbType
	 * @param pkStrategy
	 * @return
	 */
	static String getCacheKey(EntityMeta entityMeta, String tableName, int dbType, PKStrategy pkStrategy) {
		// update 2023-10-24 增加主键策略作为缓存key的组成，因为gaussdb
		// save单条保存和saveAll批量机制存在差异，save时sequence策略会提前获取sequence值，然后变成了assign策略
		// update 2026-9-7 rowversion校准状态参与缓存key:避免校准前后(rowVersionColumns判据不同)生成的
		// 语句命中同一条缓存(与SqlServerDialectUtils.getCacheKey对齐;非sqlserver库恒为null不影响)
		return entityMeta.getEntityClass().getName() + "[" + tableName + "]dbType=" + dbType
				+ ((pkStrategy == null) ? "" : pkStrategy.getValue()) + "|rv="
				+ ((entityMeta.getRowVersionColumns() == null) ? -1 : entityMeta.getRowVersionColumns().hashCode());
	}

	// update 2026-9-14
	// 原isOceanBaseAsMysql()(读ThreadLocal的actuallyDBType)已删除:各分派点统一改用
	// DBProfile.isOceanBase()——按产品名判定、由profile参数显式传递,不再依赖线程隐式全局态
	// (并行等场景下ThreadLocal缺失会误判为真mysql),语义详见DBProfile.isOceanBase的javadoc

	/**
	 * update 2026-9-7 实测DB2 12.1起空间能力可内置为非限定SYSIBM函数(ST_GeomFromText等,
	 * 需≥8K页表空间);11.5及以下GSE扩展为db2gse.ST_GeomFromText专属形态。 update 2026-9-10 修正为GSE
	 * schema探测+nativeType分派(取代纯版本分派isDB2BuiltInSpatial):
	 * 实测12.1.5容器GSE与内置引擎并存,内置ST_GEOMETRY与db2gse.ST_GEOMETRY为不同UDT,
	 * 纯版本分派在GSE列上误选内置函数报-408(value cannot be assigned); 分派优先级:列nativeType显式声明 >
	 * 连接档案的DB2GSE schema探测 > 默认GSE形态(保持11.5既有行为); 12.1起首参为CLOB须cast(? as
	 * CLOB)(setString直绑报-4474,db2gse形态cast亦实测通过),11.5无需cast
	 *
	 * @param fieldMeta geometry字段元数据(nativeType含db2gse/st_geometry时显式分派),可为null
	 * @return geometry参数化包装表达式,如db2gse.ST_GeomFromText(cast(? as CLOB),0)
	 */
	public static String db2GeomFromTextWrap(FieldMeta fieldMeta) {
		DBProfile profile = SqlToyThreadDataHolder.getDBProfile();
		boolean db2Profile = (profile != null && profile.getDbType() == DBType.DB2);
		boolean db2v12 = (db2Profile && profile.getMajorVersion() >= 12);
		String nativeType = (fieldMeta == null || fieldMeta.getNativeType() == null) ? ""
				: fieldMeta.getNativeType().toLowerCase(Locale.ROOT);
		boolean useGse;
		if (nativeType.contains("db2gse")) {
			useGse = true;
		} else if (nativeType.contains("geometry") && db2v12) {
			// nativeType显式声明内置ST_GEOMETRY/GEOMETRY形态(仅12.1+存在内置引擎)
			useGse = false;
		} else if (db2Profile) {
			// 按GSE schema探测分派:存在→db2gse形态(12.1.5实测GSE列),不存在→内置形态(12.1.2实测无db2gse)
			useGse = !Boolean.FALSE.equals(profile.getHasGseSchema());
		} else {
			// 未采集到连接档案保持既有db2gse形态
			useGse = true;
		}
		String param = db2v12 ? "cast(? as CLOB)" : "?";
		return (useGse ? "db2gse.ST_GeomFromText(" : "ST_GeomFromText(") + param + ",0)";
	}
}
