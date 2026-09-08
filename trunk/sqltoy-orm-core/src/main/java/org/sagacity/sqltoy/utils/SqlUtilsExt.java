package org.sagacity.sqltoy.utils;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.NClob;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Date;
import java.util.Locale;

import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.FieldMeta;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.model.JdbcTypes;
import org.sagacity.sqltoy.plugins.TypeHandler;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sagacity-sqltoy
 * @description 提供针对SqlUtil类的扩展,提供更有针对性的操作,提升性能
 * @author zhongxuchen
 * @version v1.0,Date:2015-04-22
 */
public class SqlUtilsExt {
	/**
	 * 定义日志
	 */
	private final static Logger logger = LoggerFactory.getLogger(SqlUtilsExt.class);

	private SqlUtilsExt() {
	}

	/**
	 * 获得全部字段的默认值
	 * 
	 * @param entityMeta
	 * @param excludeGeneratedCols
	 * @return
	 */
	public static Object[] getDefaultValues(EntityMeta entityMeta, boolean excludeGeneratedCols) {
		String[] fieldsDefaultValue = entityMeta.getFieldsDefaultValue(excludeGeneratedCols);
		if (null == entityMeta || null == fieldsDefaultValue || fieldsDefaultValue.length == 0) {
			return null;
		}
		int size = fieldsDefaultValue.length;
		Object[] result = new Object[size];
		String defaultValue = null;
		int fieldType;
		String fieldName = null;
		Boolean nullable;
		// 是否是唯一主键
		boolean isUniqPk = false;
		if (entityMeta.getIdArray() != null && entityMeta.getIdArray().length == 1) {
			isUniqPk = true;
		}
		try {
			FieldMeta fieldMeta;
			String[] fieldsArray = entityMeta.getFieldsArray(excludeGeneratedCols);
			Boolean[] fieldsNullable = entityMeta.getFieldsNullable(excludeGeneratedCols);
			Integer[] fieldsTypeArray = entityMeta.getFieldsTypeArray(excludeGeneratedCols);
			for (int i = 0; i < size; i++) {
				fieldName = fieldsArray[i];
				defaultValue = fieldsDefaultValue[i];
				nullable = fieldsNullable[i];
				fieldMeta = entityMeta.getFieldMeta(fieldName);
				// 唯一主键不允许有默认值(EntityManager.parseFieldTypeAndDefault()已经跳过了唯一主键的默认值设置)
				if (!(fieldMeta.isPK() && isUniqPk) && null != defaultValue) {
					fieldType = fieldsTypeArray[i];
					result[i] = getDefaultValue(null, defaultValue, fieldType, (nullable == null) ? false : nullable);
				}
			}
		} catch (Exception e) {
			logger.error(
					"exception occurred while processing field:[{}] default value:[{}], please check the default value setting, errorMsg={}",
					fieldName, defaultValue, e.getMessage());
			throw e;
		}
		return result;
	}

	/**
	 * 针对默认值进行处理
	 * 
	 * @param paramValue
	 * @param defaultValue
	 * @param jdbcType
	 * @param nullable
	 * @return
	 */
	public static Object getDefaultValue(Object paramValue, String defaultValue, int jdbcType, boolean nullable) {
		Object realValue = paramValue;
		// 当前值为null且默认值不为null、且字段不允许为null
		if (realValue == null && defaultValue != null) {
			if (jdbcType == java.sql.Types.VARCHAR || jdbcType == java.sql.Types.CLOB
					|| jdbcType == java.sql.Types.NCHAR || jdbcType == java.sql.Types.NVARCHAR
					|| jdbcType == java.sql.Types.CHAR || jdbcType == java.sql.Types.LONGNVARCHAR
					|| jdbcType == java.sql.Types.LONGVARCHAR || jdbcType == java.sql.Types.NCLOB) {
				return defaultValue;
			}
			boolean isBlank = "".equals(defaultValue.trim());
			// update 2023-2-15增加容错性处理 非字符类型且允许为null，默认值为空白返回null
			if (isBlank && nullable) {
				return null;
			}
			// update 2024-9-26 增加转数字类型时的判断
			if (jdbcType == java.sql.Types.INTEGER || jdbcType == java.sql.Types.TINYINT
					|| jdbcType == java.sql.Types.SMALLINT) {
				if (isBlank) {
					return Integer.valueOf(0);
				}
				if (!NumberUtil.isNumber(defaultValue)) {
					return null;
				}
				realValue = Integer.valueOf(defaultValue);
			} else if (jdbcType == java.sql.Types.DATE) {
				if (isBlank || isCurrentTime(defaultValue)) {
					realValue = new Date();
				} else {
					realValue = DateUtil.convertDateObject(defaultValue);
				}
			} else if (jdbcType == java.sql.Types.TIMESTAMP) {
				if (isBlank || isCurrentTime(defaultValue)) {
					realValue = DateUtil.getTimestamp(null);
				} else {
					realValue = DateUtil.getTimestamp(defaultValue);
				}
			} else if (jdbcType == java.sql.Types.TIMESTAMP_WITH_TIMEZONE) {
				if (isBlank || isCurrentTime(defaultValue)) {
					realValue = OffsetDateTime.now();
				} else {
					realValue = DateUtil.getDateTime(defaultValue).atZone(SqlToyConstants.getZoneId())
							.toOffsetDateTime();
				}
			} else if (jdbcType == java.sql.Types.DECIMAL || jdbcType == java.sql.Types.NUMERIC) {
				if (isBlank) {
					return BigInteger.ZERO;
				}
				if (!NumberUtil.isNumber(defaultValue)) {
					return null;
				}
				realValue = new BigDecimal(defaultValue);
			} else if (jdbcType == java.sql.Types.BIGINT) {
				if (isBlank) {
					return BigInteger.ZERO;
				}
				if (!NumberUtil.isNumber(defaultValue)) {
					return null;
				}
				realValue = new BigInteger(defaultValue);
			} else if (jdbcType == java.sql.Types.TIME) {
				if (isBlank || isCurrentTime(defaultValue)) {
					realValue = LocalTime.now();
				} else {
					realValue = DateUtil.asLocalTime(DateUtil.convertDateObject(defaultValue));
				}
			} else if (jdbcType == java.sql.Types.TIME_WITH_TIMEZONE) {
				if (isBlank || isCurrentTime(defaultValue)) {
					realValue = OffsetTime.now();
				} else {
					LocalTime localTime = DateUtil.asLocalTime(DateUtil.convertDateObject(defaultValue));
					// 2. 获取该时区在当前日期的偏移量（需结合日期，这里用当天）
					ZoneOffset offset = SqlToyConstants.getZoneId().getRules()
							.getOffset(LocalDateTime.of(LocalDate.now(), localTime));
					realValue = localTime.atOffset(offset);
				}
			} else if (jdbcType == java.sql.Types.DOUBLE) {
				if (isBlank) {
					return Double.valueOf("0");
				}
				if (!NumberUtil.isNumber(defaultValue)) {
					return null;
				}
				realValue = Double.valueOf(defaultValue);
			} else if (jdbcType == java.sql.Types.BOOLEAN) {
				realValue = Boolean.parseBoolean(isBlank ? "false" : defaultValue);
			} else if (jdbcType == java.sql.Types.FLOAT || jdbcType == java.sql.Types.REAL) {
				if (isBlank) {
					return Float.valueOf("0");
				}
				if (!NumberUtil.isNumber(defaultValue)) {
					return null;
				}
				realValue = Float.valueOf(defaultValue);
			} else if (jdbcType == java.sql.Types.BIT) {
				if ("true".equalsIgnoreCase(defaultValue) || "false".equalsIgnoreCase(defaultValue)) {
					realValue = Boolean.parseBoolean(defaultValue.toLowerCase(Locale.ROOT));
				} else {
					if (isBlank) {
						return Integer.parseInt("0");
					}
					if (!NumberUtil.isNumber(defaultValue)) {
						return null;
					}
					realValue = Integer.parseInt(defaultValue);
				}
			} else {
				realValue = defaultValue;
			}
		}
		return realValue;
	}

	// 判断默认值是否系统时间或日期
	public static boolean isCurrentTime(String defaultValue) {
		String defaultLow = defaultValue.toLowerCase(Locale.ROOT);
		if (defaultLow.contains("sysdate") || defaultLow.contains("now") || defaultLow.contains("current")
				|| defaultLow.contains("sysdatetime") || defaultLow.contains("systime")
				|| defaultLow.contains("timestamp") || defaultLow.contains("curdate") || defaultLow.contains("curtime")
				|| defaultLow.contains("getdate") || defaultLow.contains("getutcdate")) {
			return true;
		}
		return false;
	}

	/**
	 * 对sql增加签名,便于通过db来追溯sql(目前通过将sql id以注释形式放入sql)
	 * 
	 * @param sql
	 * @param dbType       传递过来具体数据库类型,便于对不支持的数据库做区别处理
	 * @param sqlToyConfig
	 * @return
	 */
	public static String signSql(String sql, Integer dbType, SqlToyConfig sqlToyConfig) {
		// 判断是否打开sql签名,提供开发者通过SqlToyContext
		// dialectConfig设置:sqltoy.open.sqlsign=false 来关闭
		// elasticsearch类型 不支持
		if (!SqlToyConstants.openSqlSign() || dbType.equals(DBType.ES)) {
			return sql;
		}
		// 目前几乎所有数据库都支持/* xxx */ 形式的注释
		if (sqlToyConfig != null && StringUtil.isNotBlank(sqlToyConfig.getId())) {
			return "/* id=".concat(sqlToyConfig.getId()).concat(" */ ").concat(sql);
		}
		return sql;
	}

	/**
	 * 给分页、取随机记录sql打上特殊的开始和截止符号，便于后续统一的sql拦截器提取，并进行类似租户过滤条件的补充
	 * 
	 * @param originalSql
	 * @return
	 */
	public static String markOriginalSql(String originalSql) {
		return SqlToyConstants.MARK_ORIGINAL_START.concat(originalSql).concat(SqlToyConstants.MARK_ORIGINAL_END);
	}

	/**
	 * 清除分页、取随机记录等sql中的原始sql位置标记符号
	 * 
	 * @param sql
	 * @return
	 */
	public static String clearOriginalSqlMark(String sql) {
		return sql.replace(SqlToyConstants.MARK_ORIGINAL_START, "").replace(SqlToyConstants.MARK_ORIGINAL_END, "");
	}

	/**
	 * 插入对象
	 * 
	 * @param conn
	 * @param rs
	 * @param fieldMeta
	 * @param paramValue
	 * @param dbType
	 * @param isInsert
	 * @throws Exception
	 */
	public static void resultUpdate(TypeHandler typeHandler, Connection conn, ResultSet rs, FieldMeta fieldMeta,
			Object paramValue, Integer dbType, boolean isInsert) throws Exception {
		resultUpdate(typeHandler, conn, rs, fieldMeta, paramValue, dbType, isInsert, Boolean.FALSE);
	}

	/**
	 * 插入对象
	 * 
	 * @param conn
	 * @param rs
	 * @param fieldMeta
	 * @param paramValue
	 * @param dbType
	 * @param isInsert
	 * @param isForcedUpdate 是否强制更新
	 * @throws Exception
	 */
	public static void resultUpdate(TypeHandler typeHandler, Connection conn, ResultSet rs, FieldMeta fieldMeta,
			Object paramValue, Integer dbType, boolean isInsert, boolean isForcedUpdate) throws Exception {
		// 计算列不做修改操作
		if (fieldMeta.getGeneratedType() > 0) {
			return;
		}
		if (!fieldMeta.isPK() && isInsert) {
			paramValue = getDefaultValue(paramValue, fieldMeta.getDefaultValue(), fieldMeta.getType(),
					fieldMeta.isNullable());
		}
		String tmpStr;
		int jdbcType = fieldMeta.getType();
		String columnName = fieldMeta.getColumnName();
		if (paramValue == null) {
			if (isForcedUpdate) {
				rs.updateNull(columnName);
			}
			return;
		}
		// 特殊类型通过自定义处理器处理，返回true表示完成了处理，返回false则由框架完成处理
		if (typeHandler != null && typeHandler.updateValue(dbType, conn, rs, fieldMeta, paramValue)) {
			return;
		}
		// 默认json支持
		if (jdbcType == JdbcTypes.JSON || jdbcType == JdbcTypes.JSONB) {
			JSONTypeUtil.updateJSONValue(dbType, rs, columnName, jdbcType, paramValue);
		} else if (jdbcType == JdbcTypes.VECTOR) {
			// vector向量类型回写(避免float[]、List等常规分支按数组或自定义类型码处理导致错误)
			SqlUtil.updateVectorValue(dbType, rs, columnName, paramValue);
		} else if (jdbcType == JdbcTypes.GEOMETRY) {
			// geometry空间类型回写(避免byte[]、String等常规分支按错误类型处理)
			SqlUtil.updateGeometryValue(dbType, rs, columnName, paramValue);
		} else if (paramValue instanceof java.lang.String) {
			tmpStr = (String) paramValue;
			// clob 类型只有oracle、db2、dm、oceanBase等数据库支持
			if (jdbcType == java.sql.Types.CLOB) {
				if (DBType.ORACLE == dbType || DBType.DB2 == dbType || DBType.OCEANBASE == dbType
						|| DBType.ORACLE11 == dbType || DBType.DM == dbType || DBType.KINGBASE == dbType) {
					Clob clob = conn.createClob();
					clob.setString(1, tmpStr);
					rs.updateClob(columnName, clob);
				} else {
					rs.updateString(columnName, tmpStr);
				}
			} else if (jdbcType == java.sql.Types.NCLOB) {
				if (DBType.ORACLE == dbType || DBType.DB2 == dbType || DBType.OCEANBASE == dbType
						|| DBType.ORACLE11 == dbType || DBType.DM == dbType || DBType.KINGBASE == dbType) {
					NClob nclob = conn.createNClob();
					nclob.setString(1, tmpStr);
					rs.updateNClob(columnName, nclob);
				} else {
					rs.updateString(columnName, tmpStr);
				}
			} else if (jdbcType == java.sql.Types.BIGINT || jdbcType == java.sql.Types.DECIMAL
					|| jdbcType == java.sql.Types.FLOAT) {
				rs.updateBigDecimal(columnName, new BigDecimal(tmpStr));
			} else if (jdbcType == java.sql.Types.INTEGER) {
				rs.updateInt(columnName, Integer.valueOf(tmpStr));
			} else if (jdbcType == java.sql.Types.BOOLEAN) {
				rs.updateBoolean(columnName, tmpStr.equals("1") || tmpStr.equalsIgnoreCase("true"));
			} else {
				rs.updateString(columnName, tmpStr);
			}
		} else if (paramValue instanceof java.lang.Integer || paramValue.getClass() == int.class) {
			if (jdbcType == java.sql.Types.BOOLEAN) {
				rs.updateBoolean(columnName, (Integer) paramValue == 1);
			} else if (jdbcType == java.sql.Types.VARCHAR || jdbcType == java.sql.Types.NVARCHAR
					|| jdbcType == java.sql.Types.NCHAR) {
				rs.updateString(columnName, paramValue.toString());
			} else {
				rs.updateInt(columnName, (Integer) paramValue);
			}
		} else if (paramValue instanceof java.time.LocalDateTime) {
			if (jdbcType == java.sql.Types.VARCHAR || jdbcType == java.sql.Types.CHAR) {
				rs.updateString(columnName, DateUtil.formatDate(paramValue, "yyyy-MM-dd HH:mm:ss"));
			} else {
				rs.updateTimestamp(columnName, Timestamp.valueOf((LocalDateTime) paramValue));
			}
		} else if (paramValue instanceof BigDecimal) {
			if (jdbcType == java.sql.Types.VARCHAR || jdbcType == java.sql.Types.NVARCHAR
					|| jdbcType == java.sql.Types.NCHAR) {
				rs.updateString(columnName, ((BigDecimal) paramValue).toPlainString());
			} else {
				rs.updateBigDecimal(columnName, (BigDecimal) paramValue);
			}
		} else if (paramValue instanceof java.time.LocalDate) {
			if (jdbcType == java.sql.Types.VARCHAR || jdbcType == java.sql.Types.CHAR) {
				rs.updateString(columnName, DateUtil.formatDate(paramValue, "yyyy-MM-dd"));
			} else {
				rs.updateDate(columnName, java.sql.Date.valueOf((LocalDate) paramValue));
			}
		} else if (paramValue instanceof java.sql.Time) {
			if (jdbcType == java.sql.Types.VARCHAR || jdbcType == java.sql.Types.CHAR) {
				rs.updateString(columnName, DateUtil.formatDate(paramValue, "HH:mm:ss"));
			} else {
				rs.updateTime(columnName, (java.sql.Time) paramValue);
			}
		} else if (paramValue instanceof java.util.Date) {
			if (jdbcType == java.sql.Types.VARCHAR || jdbcType == java.sql.Types.CHAR) {
				rs.updateString(columnName, DateUtil.formatDate(paramValue, "yyyy-MM-dd HH:mm:ss"));
			} else {
				if (dbType == DBType.CLICKHOUSE) {
					rs.updateDate(columnName, new java.sql.Date(((java.util.Date) paramValue).getTime()));
				} else {
					rs.updateTimestamp(columnName, new Timestamp(((java.util.Date) paramValue).getTime()));
				}
			}
		} else if (paramValue instanceof java.math.BigInteger) {
			if (jdbcType == java.sql.Types.VARCHAR || jdbcType == java.sql.Types.NVARCHAR
					|| jdbcType == java.sql.Types.NCHAR) {
				rs.updateString(columnName, paramValue.toString());
			} else {
				rs.updateBigDecimal(columnName, new BigDecimal((BigInteger) paramValue));
			}
		} else if (paramValue instanceof java.lang.Double || paramValue.getClass() == double.class) {
			if (jdbcType == java.sql.Types.VARCHAR || jdbcType == java.sql.Types.NVARCHAR
					|| jdbcType == java.sql.Types.NCHAR) {
				rs.updateString(columnName, paramValue.toString());
			} else {
				rs.updateDouble(columnName, ((Double) paramValue));
			}
		} else if (paramValue instanceof java.lang.Long || paramValue.getClass() == long.class) {
			if (jdbcType == java.sql.Types.VARCHAR || jdbcType == java.sql.Types.NVARCHAR
					|| jdbcType == java.sql.Types.NCHAR) {
				rs.updateString(columnName, paramValue.toString());
			} else {
				rs.updateLong(columnName, ((Long) paramValue));
			}
		} else if (paramValue instanceof java.sql.Clob) {
			tmpStr = SqlUtil.clobToString((java.sql.Clob) paramValue);
			rs.updateString(columnName, tmpStr);
		} else if (paramValue instanceof byte[]) {
			if (jdbcType == java.sql.Types.BLOB) {
				Blob blob = null;
				try {
					blob = conn.createBlob();
					OutputStream out = blob.setBinaryStream(1);
					out.write((byte[]) paramValue);
					out.flush();
					out.close();
					rs.updateBlob(columnName, blob);
				} catch (Exception e) {
					rs.updateBytes(columnName, (byte[]) paramValue);
				}
			} else if (jdbcType == java.sql.Types.VARCHAR || jdbcType == java.sql.Types.NVARCHAR
					|| jdbcType == java.sql.Types.LONGVARCHAR || jdbcType == java.sql.Types.LONGNVARCHAR
					|| jdbcType == java.sql.Types.NCHAR) {
				rs.updateString(columnName, new String((byte[]) paramValue, java.nio.charset.StandardCharsets.UTF_8));
			} else {
				rs.updateBytes(columnName, (byte[]) paramValue);
			}
		} else if (paramValue instanceof java.lang.Float || paramValue.getClass() == float.class) {
			if (jdbcType == java.sql.Types.VARCHAR || jdbcType == java.sql.Types.NVARCHAR
					|| jdbcType == java.sql.Types.NCHAR) {
				rs.updateString(columnName, paramValue.toString());
			} else {
				rs.updateFloat(columnName, ((Float) paramValue));
			}
		} else if (paramValue instanceof java.sql.Blob) {
			Blob blob = (java.sql.Blob) paramValue;
			int size = (int) blob.length();
			if (size > 0) {
				rs.updateBytes(columnName, blob.getBytes(1, size));
			} else {
				rs.updateBytes(columnName, new byte[0]);
			}
		} else if (paramValue instanceof java.lang.Boolean || paramValue.getClass() == boolean.class) {
			if (jdbcType == java.sql.Types.VARCHAR || jdbcType == java.sql.Types.CHAR) {
				rs.updateString(columnName, ((Boolean) paramValue) ? "1" : "0");
			} else if (jdbcType == java.sql.Types.INTEGER || jdbcType == java.sql.Types.SMALLINT
					|| jdbcType == java.sql.Types.TINYINT) {
				rs.updateInt(columnName, ((Boolean) paramValue) ? 1 : 0);
			} else {
				rs.updateBoolean(columnName, (Boolean) paramValue);
			}
		} else if (paramValue instanceof java.time.LocalTime) {
			if (jdbcType == java.sql.Types.VARCHAR || jdbcType == java.sql.Types.CHAR) {
				rs.updateString(columnName, DateUtil.formatDate(paramValue, "HH:mm:ss"));
			} else {
				rs.updateTime(columnName, java.sql.Time.valueOf((LocalTime) paramValue));
			}
		} else if (paramValue instanceof java.lang.Character) {
			tmpStr = ((Character) paramValue).toString();
			rs.updateString(columnName, tmpStr);
		} else if (paramValue instanceof java.lang.Short || paramValue.getClass() == short.class) {
			if (jdbcType == java.sql.Types.VARCHAR || jdbcType == java.sql.Types.NVARCHAR
					|| jdbcType == java.sql.Types.NCHAR) {
				rs.updateString(columnName, paramValue.toString());
			} else {
				rs.updateShort(columnName, (java.lang.Short) paramValue);
			}
		} else if (paramValue instanceof java.lang.Byte) {
			rs.updateByte(columnName, (Byte) paramValue);
		} else if (paramValue instanceof Object[]) {
			setArray(dbType, conn, rs, columnName, paramValue);
		} else if (paramValue instanceof Enum) {
			rs.updateObject(columnName, BeanUtil.getEnumValue(paramValue));
		} else if (paramValue instanceof Collection) {
			Object[] values = ((Collection) paramValue).toArray();
			// 集合为空，无法判断具体类型，设置为null
			if (values.length > 0) {
				String type = null;
				for (Object val : values) {
					if (val != null) {
						type = val.getClass().getName().concat("[]");
						break;
					}
				}
				// 将Object[] 转为具体类型的数组(否则会抛异常)
				if (type != null) {
					setArray(dbType, conn, rs, columnName, BeanUtil.convertArray(values, type));
				}
			}
		} else {
			if (jdbcType != java.sql.Types.NULL) {
				rs.updateObject(columnName, paramValue, jdbcType);
			} else {
				rs.updateObject(columnName, paramValue);
			}
		}
	}

	private static void setArray(Integer dbType, Connection conn, ResultSet rs, String columnName, Object paramValue)
			throws SQLException {
		// 目前只支持Integer 和 String两种类型
		if (dbType == DBType.GAUSSDB || dbType == DBType.OPENGAUSS || dbType == DBType.MOGDB || dbType == DBType.OSCAR
				|| dbType == DBType.STARDB || dbType == DBType.VASTBASE) {
			if (paramValue instanceof Integer[]) {
				Array array = conn.createArrayOf("INTEGER", (Integer[]) paramValue);
				rs.updateArray(columnName, array);
			} else if (paramValue instanceof String[]) {
				Array array = conn.createArrayOf("VARCHAR", (String[]) paramValue);
				rs.updateArray(columnName, array);
			} else if (paramValue instanceof BigDecimal[]) {
				Array array = conn.createArrayOf("NUMBER", (BigDecimal[]) paramValue);
				rs.updateArray(columnName, array);
			} else if (paramValue instanceof BigInteger[]) {
				Array array = conn.createArrayOf("BIGINT", (BigInteger[]) paramValue);
				rs.updateArray(columnName, array);
			} else if (paramValue instanceof Float[]) {
				Array array = conn.createArrayOf("FLOAT", (Float[]) paramValue);
				rs.updateArray(columnName, array);
			} else if (paramValue instanceof Long[]) {
				Array array = conn.createArrayOf("BIGINT", (Long[]) paramValue);
				rs.updateArray(columnName, array);
			} else {
				rs.updateObject(columnName, paramValue, java.sql.Types.ARRAY);
			}
		} else {
			rs.updateObject(columnName, paramValue, java.sql.Types.ARRAY);
		}
	}
}
