package org.sagacity.sqltoy.utils;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.sagacity.sqltoy.SqlExecuteStat;
import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.SqlToyThreadDataHolder;
import org.sagacity.sqltoy.callback.CallableStatementResultHandler;
import org.sagacity.sqltoy.callback.DecryptHandler;
import org.sagacity.sqltoy.callback.InsertRowCallbackHandler;
import org.sagacity.sqltoy.callback.PreparedStatementResultHandler;
import org.sagacity.sqltoy.callback.RowCallbackHandler;
import org.sagacity.sqltoy.config.SqlConfigParseUtils;
import org.sagacity.sqltoy.config.model.DataType;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.FieldMeta;
import org.sagacity.sqltoy.config.model.SqlWithAnalysis;
import org.sagacity.sqltoy.exception.DataAccessException;
import org.sagacity.sqltoy.model.DBProfile;
import org.sagacity.sqltoy.model.IgnoreCaseSet;
import org.sagacity.sqltoy.model.JdbcTypes;
import org.sagacity.sqltoy.model.SqlInjectionLevel;
import org.sagacity.sqltoy.model.TreeTableModel;
import org.sagacity.sqltoy.plugins.TypeHandler;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author zhongxuchen
 * @version v1.3,Date:2009-04-14
 * @project sagacity-sqltoy
 * @description 数据库sql相关的处理工具
 * @modify Date:2011-08-18
 *         {移植BaseDaoSupport中分页移植到SqlUtil中，将数据库表、外键、主键等库和表信息移植到DBUtil中 }
 * @modify Date:2011-08-22 修复getJdbcRecordCount中因group分组查询导致的错误， 如select
 *         name,count(*) from table group by name}
 * @modify Date:2012-11-21
 *         {完善分页查询语句中存在union的处理机制,框架自动判断是否存在union,有union则自动实现外层包裹}
 * @modify Date:2017-06-05 剔除注释时用空白填补,防止出现类似原本:select xxx from 变成select xxxfrom
 * @modify Date:2017-06-14 修复针对阿里的druid数据库datasource针对clob类型处理的错误
 * @modify Date:2019-07-05 剔除对druid clob bug的支持(druid 1.1.10 已经修复)
 * @modify Date:2020-06-18 用BeanUtil代替BeanInfo中getWriteMethod,完成对象属性赋值
 * @modify Date:2024-07-12 优化sql注释剔除的处理,兼容sql中存在/* 但没有对应收尾--*\/符号的场景
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public class SqlUtil {
	/**
	 * 定义日志
	 */
	private final static Logger logger = LoggerFactory.getLogger(SqlUtil.class);

	/**
	 * sql中的单行注释
	 */
	public final static Pattern maskPattern = Pattern.compile("\\/\\*[^+!]");
	public static final Pattern GROUP_BY_PATTERN = Pattern.compile("(?i)\\Wgroup\\s+by\\W");
	public static final Pattern ORDER_BY_PATTERN = Pattern.compile("(?i)\\Worder\\s+by\\W");

	public static final Pattern UPCASE_ORDER_PATTERN = Pattern.compile("\\WORder\\s+");

	public static final Pattern ONE_QUOTA = Pattern.compile("\'");
	public static final Pattern DOUBLE_QUOTA = Pattern.compile("\"");

	// 判断sql是否是merge into 开头
	public static final Pattern MERGE_INTO_PATTERN = Pattern.compile("^merge\\s+into\\s+");

	public static Pattern SQL_INJECT_PATTERN = Pattern.compile(
			"(?i)\\W((delete\\s+from)|update|(truncate\\s+table)|(alter\\s+table)|modify|(insert\\s+into)|(sleep\\s*\\(\\s*\\d+\\s*\\))|select|set|create|drop|(merge\\s+into))\\s+");

	// 只针对比较符号、和(的日期字符加函数
	public static final Pattern COMPARE_PATTERN = Pattern
			.compile("(?i)(\\<|\\=|\\>|\\<\\>|\\!\\=|\\<\\=|\\>\\=|\\Wand|\\Wbetween|\\()\\s*$");

	/**
	 * 查询select 匹配
	 */
	public static final String SELECT_REGEX = "\\Wselect\\s+";

	/**
	 * 查询from 匹配
	 */
	public static final String FROM_REGEX = "\\s+from[\\(\\s+]";

	// union 匹配模式
	public static final Pattern UNION_PATTERN = Pattern.compile("(?i)\\W+union\\W+");
	public final static String BLANK = " ";
	// 为了便于sql调试(@fast不方便调试)利用/*@fast_start*/代替@fast标记位置
	private final static String[] FAST_START_REGEXS = { "(?i)\\-{2}\\s+\\@fast\\_start",
			"(?i)\\/\\*\\s*\\@fast\\_start\\s*\\*\\/" };
	private final static String[] FAST_END_REGEXS = { "(?i)\\-{2}\\s+\\@fast\\_end",
			"(?i)\\/\\*\\s*\\@fast\\_end\\s*\\*\\/" };

	// 数字、字母、下划线、横杠
	public static final Pattern STRICT_WORD = Pattern.compile("^[a-zA-Z0-9_\\-\\.]+$");
	// 含中文、点号、%号、单引号、双引号、@
	public static final Pattern RELAXED_WORD = Pattern
			.compile("^[a-zA-Z0-9_\\-\u4e00-\u9fa5\\.\\%'\"@\\[\\]\\（\\）\\【\\】\\{\\}]+$");

	// 函数:abc_edf( 或 abc(
	public static final Pattern FUNCTION_PATTERN = Pattern
			.compile("(?i)\\b([a-zA-Z]+)(_[a-zA-Z]+)*\\s*\\([\\w\\W]*\\)");

	// hint /*+ xxx */
	public static final Pattern COMMENT_PATTERN = Pattern.compile("(?i)\\/\\*\\s*\\+[\\w\\W]*\\*\\/");

	/**
	 * 条件表达式:or 1=1 或 and 1<>1
	 */
	public static final Pattern CONDITION_PATTERN = Pattern
			.compile("(?i)\\b((or|and)\\s+)?[\\w\\W]+(>|>=|<>|=|<|<=|!=|(between\\b)|(is\\s+))\\s*");

	// 关键词
	public static final Pattern SQL_KEYWORD_PATTERN = Pattern.compile(
			"(?i)\\b(SELECT|INSERT|UPDATE|DELETE|DROP|CREATE|ALTER|UNION|JOIN|WHERE|FROM|DISTINCT|EXECUTE|EXEC|HAVING|(TRUNCATE\\s+TABLE)|(ORDER\\s+BY)|(GROUP\\s+BY)|(MERGE\\s+INTO)|(LIMIT\\s+\\d+)|(OFFSET\\s+\\d+))\\b");

	// sql 注入
	public static Pattern[] SQL_INJECTION_KEY_WORDS = { FUNCTION_PATTERN, COMMENT_PATTERN, CONDITION_PATTERN,
			SQL_KEYWORD_PATTERN };

	// pg 类型转化表达式
	private static final Pattern PG_CAST_PATTERN = Pattern
			.compile("::[a-zA-Z_][a-zA-Z0-9_]*(?:\\s+[a-zA-Z0-9_]+)?(?:\\(\\d+(?:,\\d+)?\\))?$");

	/**
	 * 存放转换后的sql
	 */
	private static ConcurrentHashMap<String, String> convertSqlMap = new ConcurrentHashMap<String, String>();

	// convertSqlMap的key含updateByQuery等动态sql,必须有容量上限防止无界内存增长
	private static final int CONVERT_SQL_CACHE_MAX_SIZE = 2000;

	// sql 注释过滤器
	private static HashMap sqlCommentfilters = new HashMap();

	static {
		// 排除表字段说明（注释）中的";"符号
		sqlCommentfilters.put("'", "'");
		sqlCommentfilters.put("(", ")");
		sqlCommentfilters.put("{", "}");
	}

	private SqlUtil() {
	}

	/**
	 * 合成数据库in 查询的条件(不建议使用)
	 * 
	 * @param conditions :数据库in条件的数据集合，可以是POJO List或Object[]
	 * @param colIndex   :二维数组对应列编号
	 * @param property   :POJO property
	 * @param isChar     :in 是否要加单引号
	 * @return:example:1,2,3或'1','2','3'
	 */
	public static String combineQueryInStr(Object conditions, Integer colIndex, String property, boolean isChar) {
		StringBuilder conditons = new StringBuilder(64);
		String flag = "";
		// 是否是字符类型
		if (isChar) {
			flag = "'";
		}
		// 判断数据集合维度
		int dimen = CollectionUtil.judgeObjectDimen(conditions);
		switch (dimen) {
		// 单个数据
		case 0: {
			conditons.append(flag).append(conditions.toString()).append(flag);
			break;
		}
		// 一维数组
		case 1: {
			Object[] array;
			if (conditions instanceof Collection) {
				array = ((Collection) conditions).toArray();
			} else if (conditions.getClass().isArray()) {
				array = CollectionUtil.convertArray(conditions);
			} else {
				array = ((Map) conditions).values().toArray();
			}
			for (int i = 0; i < array.length; i++) {
				if (i != 0) {
					conditons.append(",");
				}
				conditons.append(flag);
				if (null == property) {
					conditons.append(array[i]);
				} else {
					conditons.append(BeanUtil.getProperty(array[i], property));
				}
				conditons.append(flag);
			}
			break;
		}
		// 二维数据
		case 2: {
			Object[][] array;
			if (conditions instanceof Collection) {
				array = CollectionUtil.twoDimenlistToArray((Collection) conditions);
			} else if (conditions instanceof Object[][]) {
				array = (Object[][]) conditions;
			} else {
				array = CollectionUtil.twoDimenlistToArray(((Map) conditions).values());
			}
			for (int i = 0; i < array.length; i++) {
				if (i != 0) {
					conditons.append(",");
				}
				conditons.append(flag);
				if (null == property) {
					conditons.append(array[i][colIndex.intValue()]);
				} else {
					conditons.append(BeanUtil.getProperty(array[i][colIndex.intValue()], property));
				}
				conditons.append(flag);
			}
			break;
		}
		}
		return conditons.toString();
	}

	/**
	 * 自动进行类型转换, 设置sql中的参数条件的值
	 * 
	 * @param typeHandler 自定义类型处理器，非null时优先通过其完成参数设置
	 * @param conn        数据库连接对象
	 * @param dbType      数据库类型，参见DataSourceUtils.DBType
	 * @param pst         PreparedStatement预编译语句对象
	 * @param params      参数值数组
	 * @param paramsType  参数对应的java.sql.Types类型数组，null时按参数值自动判断类型
	 * @param fromIndex   参数起始下标偏移，存储过程调用从1开始时传1，一般为0
	 * @throws SQLException
	 * @throws IOException
	 */
	public static void setParamsValue(TypeHandler typeHandler, Connection conn, final Integer dbType,
			PreparedStatement pst, Object[] params, Integer[] paramsType, int fromIndex)
			throws SQLException, IOException {
		// fromIndex 针对存储过程调用存在从1开始,如:{?=call xxStore()}
		// 一般情况fromIndex 都是0
		if (null != params && params.length > 0) {
			int n = params.length;
			int startIndex = fromIndex + 1;
			if (null == paramsType || paramsType.length == 0) {
				// paramsType=-1 表示按照参数值来判断类型
				for (int i = 0; i < n; i++) {
					setParamValue(typeHandler, conn, dbType, pst, params[i], -1, startIndex + i);
				}
			} else {
				for (int i = 0; i < n; i++) {
					setParamValue(typeHandler, conn, dbType, pst, params[i], paramsType[i], startIndex + i);
				}
			}
		}
	}

	/**
	 * 设置sql中的参数条件的值
	 * 
	 * @param typeHandler 自定义类型处理器，非null时优先通过其完成参数设置
	 * @param conn        数据库连接对象(clob、blob等类型创建需要)
	 * @param dbType      数据库类型，参见DataSourceUtils.DBType
	 * @param pst         PreparedStatement预编译语句对象
	 * @param paramValue  参数值，null时按jdbcType设置null
	 * @param jdbcType    java.sql.Types定义的JDBC类型，-1表示按参数值自动判断
	 * @param paramIndex  参数位置下标(从1开始)
	 * @throws SQLException
	 * @throws IOException
	 */
	public static void setParamValue(TypeHandler typeHandler, Connection conn, final Integer dbType,
			PreparedStatement pst, Object paramValue, int jdbcType, int paramIndex) throws SQLException, IOException {
		// jdbc部分数据库赋null值时必须要指定数据类型
		if (null == paramValue) {
			if (jdbcType != java.sql.Types.NULL) {
				if (typeHandler != null && typeHandler.setNull(dbType, pst, paramIndex, jdbcType)) {
					return;
				}
				// postgresql bytea类型需要统一处理成BINARY
				if (jdbcType == java.sql.Types.BLOB) {
					if (dbType == DBType.POSTGRESQL || dbType == DBType.POSTGRESQL14) {
						pst.setNull(paramIndex, java.sql.Types.BINARY);
					} else {
						pst.setNull(paramIndex, jdbcType);
					}
				} else if (jdbcType == java.sql.Types.CLOB) {
					if (DBType.ORACLE == dbType || DBType.DB2 == dbType || DBType.OCEANBASE == dbType
							|| DBType.ORACLE11 == dbType || DBType.DM == dbType) {
						pst.setNull(paramIndex, jdbcType);
					} else {
						pst.setNull(paramIndex, java.sql.Types.VARCHAR);
					}
				} else if (jdbcType == java.sql.Types.NCLOB) {
					if (DBType.ORACLE == dbType || DBType.DB2 == dbType || DBType.OCEANBASE == dbType
							|| DBType.ORACLE11 == dbType || DBType.DM == dbType) {
						pst.setNull(paramIndex, jdbcType);
					} else {
						pst.setNull(paramIndex, java.sql.Types.NVARCHAR);
					}
				} else if (jdbcType == JdbcTypes.JSON || jdbcType == JdbcTypes.JSONB) {
					JSONTypeUtil.setNull(dbType, pst, paramIndex, jdbcType);
				} else if (jdbcType == JdbcTypes.VECTOR) {
					// vector属于数据库扩展类型,sqlserver用NVARCHAR、mysql系用VARCHAR设置null,其余按OTHER
					if (dbType == DBType.ORACLE || dbType == DBType.ORACLE11) {
						// update 2026-9-8 实测oracle 23ai的vector列null绑定:二参OTHER报ORA-17004,
						// setNull(VARCHAR)/setString(null)/setNull(2016)均可行,取VARCHAR形态
						pst.setNull(paramIndex, java.sql.Types.VARCHAR);
					} else if (dbType == DBType.SQLSERVER) {
						pst.setNull(paramIndex, java.sql.Types.NVARCHAR);
					} else if (dbType == DBType.MYSQL || dbType == DBType.MYSQL57 || dbType == DBType.OCEANBASE
							|| dbType == DBType.TIDB || dbType == DBType.DORIS || dbType == DBType.STARROCKS) {
						pst.setNull(paramIndex, java.sql.Types.VARCHAR);
					} else {
						pst.setNull(paramIndex, java.sql.Types.OTHER);
					}
				} else if (jdbcType == JdbcTypes.GEOMETRY) {
					// geometry空间类型null值设置:oracle的SDO_GEOMETRY为UDT,须三参setNull带类型名
					// (实测二参OTHER/STRUCT/NULL分别报ORA-17004/ORA-17068/ORA-00932),其余策略与vector一致
					if (dbType == DBType.ORACLE || dbType == DBType.ORACLE11) {
						OracleSdoUtil.setNull(pst, paramIndex);
					} else if (dbType == DBType.SQLSERVER) {
						pst.setNull(paramIndex, java.sql.Types.NVARCHAR);
					} else if (dbType == DBType.MYSQL || dbType == DBType.MYSQL57 || dbType == DBType.OCEANBASE
							|| dbType == DBType.TIDB || dbType == DBType.DORIS || dbType == DBType.STARROCKS
							|| dbType == DBType.DB2) {
						// mysql系与db2(db2gse的ST_GeomFromText参数为VARCHAR)均按VARCHAR置null
						pst.setNull(paramIndex, java.sql.Types.VARCHAR);
					} else {
						pst.setNull(paramIndex, java.sql.Types.OTHER);
					}
				} else {
					pst.setNull(paramIndex, jdbcType);
				}
			} else {
				pst.setNull(paramIndex, java.sql.Types.NULL);
			}
			return;
		}
		// 自定义类型处理器，完成setValue处理
		if (typeHandler != null && typeHandler.setValue(dbType, pst, paramIndex, jdbcType, paramValue)) {
			return;
		}
		if (jdbcType == JdbcTypes.JSON || jdbcType == JdbcTypes.JSONB) {
			JSONTypeUtil.setJSONValue(dbType, pst, paramIndex, jdbcType, paramValue);
			return;
		}
		// vector向量类型,pgvector、oracle 23ai、mysql heatwave等均接受'[1,2,3]'字符串形式
		if (jdbcType == JdbcTypes.VECTOR) {
			setVectorValue(dbType, pst, paramIndex, paramValue);
			return;
		}
		// geometry空间类型,统一以WKT字符串为媒介
		if (jdbcType == JdbcTypes.GEOMETRY) {
			setGeometryValue(dbType, pst, paramIndex, paramValue);
			return;
		}
		String tmpStr;
		if (paramValue instanceof java.lang.String) {
			tmpStr = (String) paramValue;
			// clob 类型只有oracle、db2、dm、oceanBase等数据库支持
			if (jdbcType == java.sql.Types.CLOB) {
				if (DBType.ORACLE == dbType || DBType.DB2 == dbType || DBType.OCEANBASE == dbType
						|| DBType.ORACLE11 == dbType || DBType.DM == dbType || DBType.KINGBASE == dbType) {
					Clob clob = conn.createClob();
					clob.setString(1, tmpStr);
					pst.setClob(paramIndex, clob);
				} else {
					pst.setString(paramIndex, tmpStr);
				}
			} else if (jdbcType == java.sql.Types.NCLOB) {
				if (DBType.ORACLE == dbType || DBType.DB2 == dbType || DBType.OCEANBASE == dbType
						|| DBType.ORACLE11 == dbType || DBType.DM == dbType || DBType.KINGBASE == dbType) {
					NClob nclob = conn.createNClob();
					nclob.setString(1, tmpStr);
					pst.setNClob(paramIndex, nclob);
				} else {
					pst.setString(paramIndex, tmpStr);
				}
			} else if (jdbcType == java.sql.Types.BIGINT || jdbcType == java.sql.Types.DECIMAL
					|| jdbcType == java.sql.Types.FLOAT) {
				pst.setBigDecimal(paramIndex, new BigDecimal(tmpStr));
			} else if (jdbcType == java.sql.Types.INTEGER) {
				pst.setInt(paramIndex, Integer.valueOf(tmpStr));
			} else if (jdbcType == java.sql.Types.DATE) {
				pst.setDate(paramIndex, new java.sql.Date(DateUtil.parseString(tmpStr).getTime()));
			} else if (jdbcType == java.sql.Types.TIMESTAMP) {
				pst.setTimestamp(paramIndex, new Timestamp(DateUtil.parseString(tmpStr).getTime()));
			} else if (jdbcType == java.sql.Types.TIME) {
				pst.setTime(paramIndex, new Time(DateUtil.parseString(tmpStr).getTime()));
			} else {
				if (jdbcType == java.sql.Types.BOOLEAN) {
					if (tmpStr.equalsIgnoreCase("true") || tmpStr.equals("1")) {
						pst.setBoolean(paramIndex, true);
					} else {
						pst.setBoolean(paramIndex, false);
					}
				} else {
					pst.setString(paramIndex, tmpStr);
				}
			}
		} else if (paramValue instanceof java.lang.Integer) {
			// update 2023-6-2 兼容前端int对应数据库是boolean场景
			Integer paramInt = (Integer) paramValue;
			if (jdbcType == java.sql.Types.BOOLEAN) {
				if (paramInt == 1) {
					pst.setBoolean(paramIndex, true);
				} else {
					pst.setBoolean(paramIndex, false);
				}
			} else if (jdbcType == java.sql.Types.VARCHAR || jdbcType == java.sql.Types.NCHAR
					|| jdbcType == java.sql.Types.NVARCHAR) {
				pst.setString(paramIndex, paramValue.toString());
			} else {
				pst.setInt(paramIndex, paramInt);
			}
		} else if (paramValue instanceof java.time.LocalDateTime) {
			// 带时区的日期类型
			if (jdbcType == java.sql.Types.TIMESTAMP_WITH_TIMEZONE) {
				pst.setObject(paramIndex,
						((LocalDateTime) paramValue).atZone(SqlToyConstants.getZoneId()).toOffsetDateTime());
			} else if (jdbcType == java.sql.Types.VARCHAR || jdbcType == java.sql.Types.NCHAR
					|| jdbcType == java.sql.Types.NVARCHAR) {
				pst.setString(paramIndex, ((LocalDateTime) paramValue).format(DateTimeFormatter.ISO_DATE_TIME));
			} else {
				pst.setTimestamp(paramIndex, Timestamp.valueOf((LocalDateTime) paramValue));
			}
		} else if (paramValue instanceof OffsetDateTime) {
			if (jdbcType == java.sql.Types.VARCHAR || jdbcType == java.sql.Types.NCHAR
					|| jdbcType == java.sql.Types.NVARCHAR) {
				pst.setString(paramIndex, ((OffsetDateTime) paramValue).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
			} else {
				pst.setObject(paramIndex, (OffsetDateTime) paramValue);
			}
		} else if (paramValue instanceof ZonedDateTime) {
			if (jdbcType == java.sql.Types.VARCHAR || jdbcType == java.sql.Types.NCHAR
					|| jdbcType == java.sql.Types.NVARCHAR) {
				pst.setString(paramIndex, (((ZonedDateTime) paramValue).toOffsetDateTime())
						.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
			} else {
				pst.setObject(paramIndex, ((ZonedDateTime) paramValue).toOffsetDateTime());
			}
		} else if (paramValue instanceof BigDecimal) {
			if (jdbcType == java.sql.Types.VARCHAR || jdbcType == java.sql.Types.NCHAR
					|| jdbcType == java.sql.Types.NVARCHAR) {
				pst.setString(paramIndex, ((BigDecimal) paramValue).toPlainString());
			} else {
				pst.setBigDecimal(paramIndex, (BigDecimal) paramValue);
			}
		} else if (paramValue instanceof java.time.LocalDate) {
			if (jdbcType == java.sql.Types.VARCHAR || jdbcType == java.sql.Types.NCHAR
					|| jdbcType == java.sql.Types.NVARCHAR) {
				pst.setString(paramIndex, ((LocalDate) paramValue).format(DateTimeFormatter.ISO_LOCAL_DATE));
			} else {
				pst.setDate(paramIndex, java.sql.Date.valueOf((LocalDate) paramValue));
			}
		} else if (paramValue instanceof java.sql.Timestamp) {
			// 带时区的日期类型
			if (jdbcType == java.sql.Types.TIMESTAMP_WITH_TIMEZONE) {
				pst.setObject(paramIndex, (DateUtil.asLocalDateTime((java.sql.Timestamp) paramValue))
						.atZone(SqlToyConstants.getZoneId()).toOffsetDateTime());
			} else if (jdbcType == java.sql.Types.VARCHAR || jdbcType == java.sql.Types.NCHAR
					|| jdbcType == java.sql.Types.NVARCHAR) {
				pst.setString(paramIndex, DateUtil.formatDate(paramValue, DateUtil.FORMAT.DATETIME_HORIZONTAL));
			} else {
				pst.setTimestamp(paramIndex, (java.sql.Timestamp) paramValue);
			}
		} else if (paramValue instanceof java.util.Date) {
			// 带时区的日期类型
			if (jdbcType == java.sql.Types.TIMESTAMP_WITH_TIMEZONE) {
				pst.setObject(paramIndex, (DateUtil.asLocalDateTime((Date) paramValue))
						.atZone(SqlToyConstants.getZoneId()).toOffsetDateTime());
			} else if (jdbcType == java.sql.Types.VARCHAR || jdbcType == java.sql.Types.NCHAR
					|| jdbcType == java.sql.Types.NVARCHAR) {
				pst.setString(paramIndex, DateUtil.formatDate(paramValue, DateUtil.FORMAT.DATETIME_HORIZONTAL));
			} else {
				if (dbType == DBType.CLICKHOUSE) {
					pst.setDate(paramIndex, new java.sql.Date(((java.util.Date) paramValue).getTime()));
				} else {
					pst.setTimestamp(paramIndex, new Timestamp(((java.util.Date) paramValue).getTime()));
				}
			}
		} else if (paramValue instanceof java.math.BigInteger) {
			if (jdbcType == java.sql.Types.VARCHAR || jdbcType == java.sql.Types.NCHAR
					|| jdbcType == java.sql.Types.NVARCHAR) {
				pst.setString(paramIndex, paramValue.toString());
			} else {
				pst.setBigDecimal(paramIndex, new BigDecimal(((BigInteger) paramValue)));
			}
		} else if (paramValue instanceof java.lang.Double) {
			if (jdbcType == java.sql.Types.VARCHAR || jdbcType == java.sql.Types.NCHAR
					|| jdbcType == java.sql.Types.NVARCHAR) {
				pst.setString(paramIndex, paramValue.toString());
			} else {
				pst.setDouble(paramIndex, ((Double) paramValue));
			}
		} else if (paramValue instanceof java.lang.Long) {
			if (jdbcType == java.sql.Types.VARCHAR || jdbcType == java.sql.Types.NCHAR
					|| jdbcType == java.sql.Types.NVARCHAR) {
				pst.setString(paramIndex, paramValue.toString());
			} else {
				pst.setLong(paramIndex, ((Long) paramValue));
			}
		} else if (paramValue instanceof java.sql.Clob) {
			tmpStr = clobToString((java.sql.Clob) paramValue);
			pst.setString(paramIndex, tmpStr);
		} else if (paramValue instanceof byte[]) {
			if (jdbcType == java.sql.Types.BLOB) {
				// update 2026-9-6
				// 实测PG系:pgjdbc的createBlob抛SQLFeatureNotSupportedException(SQLSTATE 0A000),
				// Hikari等连接池按SQLSTATE黑名单将连接标记为broken并关闭物理连接,导致"catch后setBytes兜底"
				// 在已死连接上执行报"This connection has been closed.";openGauss系驱动的setBlob亦发送
				// 大对象oid与bytea列不匹配。PG系bytea列原生接受setBytes,直接绑定不走createBlob/setBlob
				// (gaussdb:真GaussDB Kernel JDBC驱动连openGauss实测同样bytea类型不匹配,2026-9-6补入)
				if (dbType == DBType.POSTGRESQL || dbType == DBType.POSTGRESQL14 || dbType == DBType.MOGDB
						|| dbType == DBType.VASTBASE || dbType == DBType.OPENGAUSS || dbType == DBType.STARDB
						|| dbType == DBType.GAUSSDB) {
					pst.setBytes(paramIndex, (byte[]) paramValue);
				} else {
					Blob blob = null;
					try {
						blob = conn.createBlob();
						OutputStream out = blob.setBinaryStream(1);
						out.write((byte[]) paramValue);
						out.flush();
						out.close();
						pst.setBlob(paramIndex, blob);
					} catch (Exception e) {
						pst.setBytes(paramIndex, (byte[]) paramValue);
					}
				}
			} else {
				pst.setBytes(paramIndex, (byte[]) paramValue);
			}
		} else if (paramValue instanceof java.lang.Float) {
			if (jdbcType == java.sql.Types.VARCHAR || jdbcType == java.sql.Types.NCHAR
					|| jdbcType == java.sql.Types.NVARCHAR) {
				pst.setString(paramIndex, paramValue.toString());
			} else {
				pst.setFloat(paramIndex, ((Float) paramValue));
			}
		} else if (paramValue instanceof java.sql.Blob) {
			Blob blob = (java.sql.Blob) paramValue;
			int size = (int) blob.length();
			if (size > 0) {
				pst.setBytes(paramIndex, blob.getBytes(1, size));
			} else {
				pst.setBytes(paramIndex, new byte[0]);
			}
		} else if (paramValue instanceof java.sql.Date) {
			if (jdbcType == java.sql.Types.VARCHAR || jdbcType == java.sql.Types.NCHAR
					|| jdbcType == java.sql.Types.NVARCHAR) {
				pst.setString(paramIndex, DateUtil.formatDate(paramValue, DateUtil.FORMAT.DATETIME_HORIZONTAL));
			} else {
				pst.setDate(paramIndex, (java.sql.Date) paramValue);
			}
		} else if (paramValue instanceof java.lang.Boolean) {
			// update 2023-10-16 增强特殊情况下的兼容
			if (jdbcType == java.sql.Types.VARCHAR || jdbcType == java.sql.Types.CHAR) {
				pst.setString(paramIndex, ((Boolean) paramValue) ? "1" : "0");
			} else if (jdbcType == java.sql.Types.INTEGER || jdbcType == java.sql.Types.SMALLINT
					|| jdbcType == java.sql.Types.TINYINT) {
				pst.setInt(paramIndex, ((Boolean) paramValue) ? 1 : 0);
			} else {
				pst.setBoolean(paramIndex, (Boolean) paramValue);
			}
		} else if (paramValue instanceof java.time.LocalTime) {
			if (jdbcType == java.sql.Types.VARCHAR || jdbcType == java.sql.Types.NCHAR
					|| jdbcType == java.sql.Types.NVARCHAR) {
				pst.setString(paramIndex, ((LocalTime) paramValue).format(DateTimeFormatter.ISO_LOCAL_TIME));
			} else {
				pst.setTime(paramIndex, java.sql.Time.valueOf((LocalTime) paramValue));
			}
		} else if (paramValue instanceof java.sql.Time) {
			if (jdbcType == java.sql.Types.VARCHAR || jdbcType == java.sql.Types.NCHAR
					|| jdbcType == java.sql.Types.NVARCHAR) {
				pst.setString(paramIndex, DateUtil.formatDate(paramValue, "HH:mm:ss"));
			} else {
				pst.setTime(paramIndex, (java.sql.Time) paramValue);
			}
		} else if (paramValue instanceof java.lang.Character) {
			tmpStr = ((Character) paramValue).toString();
			pst.setString(paramIndex, tmpStr);
		} else if (paramValue instanceof java.lang.Short) {
			if (jdbcType == java.sql.Types.VARCHAR || jdbcType == java.sql.Types.NCHAR
					|| jdbcType == java.sql.Types.NVARCHAR) {
				pst.setString(paramIndex, paramValue.toString());
			} else {
				pst.setShort(paramIndex, (java.lang.Short) paramValue);
			}
		} else if (paramValue instanceof java.lang.Byte) {
			pst.setByte(paramIndex, (Byte) paramValue);
		} else if (paramValue instanceof Object[]) {
			setArray(dbType, conn, pst, paramIndex, paramValue);
		} // update 2023-08-02 增加默认的枚举类型处理
		else if (paramValue instanceof Enum) {
			if (jdbcType == java.sql.Types.VARCHAR || jdbcType == java.sql.Types.NCHAR
					|| jdbcType == java.sql.Types.NVARCHAR) {
				pst.setString(paramIndex, BeanUtil.getEnumValue(paramValue).toString());
			} else {
				pst.setObject(paramIndex, BeanUtil.getEnumValue(paramValue));
			}
		}
		// update 2023-5-26 增加集合类型场景支持(对应数据库Array)
		else if (paramValue instanceof Collection) {
			Object[] values = ((Collection) paramValue).toArray();
			// 集合为空，无法判断具体类型，设置为null
			if (values.length == 0) {
				pst.setNull(paramIndex, java.sql.Types.ARRAY);
			} else {
				String type = null;
				for (Object val : values) {
					if (val != null) {
						type = val.getClass().getName().concat("[]");
						break;
					}
				}
				// 将Object[] 转为具体类型的数组(否则会抛异常)
				if (type != null) {
					setArray(dbType, conn, pst, paramIndex, BeanUtil.convertArray(values, type));
				} else {
					pst.setNull(paramIndex, java.sql.Types.ARRAY);
				}
			}
		} else {
			if (jdbcType != java.sql.Types.NULL) {
				pst.setObject(paramIndex, paramValue, jdbcType);
			} else {
				pst.setObject(paramIndex, paramValue);
			}
		}
	}

	/**
	 * vector向量类型参数赋值,统一转为'[1,2,3]'字符串形式(pgvector、oracle 23ai、mysql heatwave等均支持)
	 * 
	 * @param dbType     数据库类型，参见DataSourceUtils.DBType
	 * @param pst        PreparedStatement预编译语句对象
	 * @param paramIndex 参数位置下标(从1开始)
	 * @param value      向量值，支持字符串、数组、集合或驱动专属向量对象
	 * @throws SQLException
	 */
	private static void setVectorValue(Integer dbType, PreparedStatement pst, int paramIndex, Object value)
			throws SQLException {
		String vectorStr = toVectorString(value);
		// org.pgvector.PGvector、oracle.sql.VECTOR、PGobject等驱动专属对象直接交由驱动解析
		if (vectorStr == null) {
			pst.setObject(paramIndex, value);
			return;
		}
		// postgresql系需要用PGobject明确指定向量类型,避免因参数按varchar发送导致无法隐式转换
		// gaussdb企业版向量类型名为floatvector,其余为vector
		// update 2026-9-10 vastbase G100 3.0(Build9)实测向量类型名同为floatvector:无vector别名
		// (create table vector(3)报type "vector" does not exist,floatvector(3)建表/字符串隐式
		// 写入/文本读回/'[..]'::floatvector cast+nvl包裹全部实测可行)
		String pgTypeName = (dbType == DBType.GAUSSDB || dbType == DBType.VASTBASE) ? "floatvector" : "vector";
		// update 2026-9-6 实测PGobject不能跨驱动setObject(报Can't infer the SQL type),
		// 按连接URL scheme选择同源驱动的PGobject;无匹配驱动类型对象时回退setObject(str,OTHER)
		// 由服务器按目标列推断(此前按dbType+classpath静态探测,混合驱动场景会错配)
		Object pgObject = isPGFamily(dbType) ? getPGobjectByConn(pst, pgTypeName, vectorStr) : null;
		if (pgObject != null) {
			pst.setObject(paramIndex, pgObject);
		} else if (isPGFamily(dbType)) {
			pst.setObject(paramIndex, vectorStr, java.sql.Types.OTHER);
		} else {
			pst.setString(paramIndex, vectorStr);
		}
	}

	/**
	 * update 2026-9-6 判定是否为PG系内核方言(PGobject类型包装的适用范围):
	 * 非PG系(mysql/oracle/db2/sqlserver等)直接以字符串绑定,不得构造PGobject
	 */
	private static boolean isPGFamily(Integer dbType) {
		return dbType == DBType.POSTGRESQL || dbType == DBType.POSTGRESQL14 || dbType == DBType.OPENGAUSS
				|| dbType == DBType.MOGDB || dbType == DBType.GAUSSDB || dbType == DBType.STARDB
				|| dbType == DBType.VASTBASE || dbType == DBType.KINGBASE;
	}

	/**
	 * update 2026-9-6 按连接实际驱动选择同源PGobject包装json/vector/geometry等PG系扩展类型:
	 * 实测PGobject不能跨驱动setObject(pg驱动收到org.opengauss的PGobject报Can't infer the SQL
	 * type, 反向同理);此前按dbType+classpath静态探测的分支在混合驱动场景(如classpath同时有postgresql与
	 * opengauss-jdbc,连接走其一)会错配。以连接JDBC URL的scheme段为准(穿透Hikari等连接池代理),
	 * 对应不上classpath中的驱动类时返回null,由调用方回退setObject(str,Types.OTHER)交服务器按列推断。
	 * 
	 * @param pst      预编译语句(取其连接的JDBC URL判定驱动来源)
	 * @param typeName PG扩展类型名(json/jsonb/vector/geometry等)
	 * @param value    字符串形式的类型值
	 * @return 同源驱动的PGobject实例,null表示无匹配驱动类型对象
	 */
	public static Object getPGobjectByConn(PreparedStatement pst, String typeName, String value) {
		try {
			return getPGobjectByConn(pst.getConnection(), typeName, value);
		} catch (Throwable e) {
			return null;
		}
	}

	/**
	 * update 2026-9-6 统筹至DataSourceUtils.getDBProfile(以JDBC URL为key的进程级特征档案,
	 * 含PGobjectHolder反射句柄缓存):取代本类此前独立的URL→holder缓存与解析,解析逻辑 (URL
	 * scheme→同源驱动PGobject类)已迁DataSourceUtils.resolvePGobjectHolder,
	 * PGobjectHolder类提升为DBProfile嵌套类
	 */
	public static Object getPGobjectByConn(Connection conn, String typeName, String value) {
		try {
			DBProfile dbProfile = DataSourceUtils.getDBProfile(conn);
			if (dbProfile == null || dbProfile.getPgObjectHolder() == null) {
				return null;
			}
			// PGobject实例不可跨参数复用(驱动持有引用至execute,批量addBatch复用会串值),每参数新建
			return dbProfile.getPgObjectHolder().create(typeName, value);
		} catch (Throwable e) {
			return null;
		}
	}

	/**
	 * vector向量列回写(upsert等场景通过ResultSet回写)
	 * 
	 * @param dbType     数据库类型，参见DataSourceUtils.DBType
	 * @param rs         ResultSet结果集对象
	 * @param columnName 列名称
	 * @param value      向量值，支持字符串、数组、集合或驱动专属向量对象
	 * @throws SQLException
	 */
	public static void updateVectorValue(Integer dbType, ResultSet rs, String columnName, Object value)
			throws SQLException {
		String vectorStr = toVectorString(value);
		if (vectorStr == null) {
			rs.updateObject(columnName, value);
			return;
		}
		// gaussdb企业版向量类型名为floatvector,其余为vector
		// update 2026-9-10 vastbase G100
		// 3.0实测向量类型名同为floatvector(无vector别名),与setVectorValue同步
		// update 2026-9-6 按连接URL scheme选择同源驱动PGobject(同setVectorValue,规避跨驱动错配)
		String pgTypeName = (dbType == DBType.GAUSSDB || dbType == DBType.VASTBASE) ? "floatvector" : "vector";
		Object pgObject = (!isPGFamily(dbType) || rs.getStatement() == null) ? null
				: getPGobjectByConn(rs.getStatement().getConnection(), pgTypeName, vectorStr);
		if (pgObject != null) {
			rs.updateObject(columnName, pgObject);
		} else if (dbType == DBType.ORACLE || dbType == DBType.ORACLE11) {
			// update 2026-9-8 实测oracle的rs回写updateObject(string)报ORA-17004,
			// updateString可行(读回为科学计数法文本形态,与读链路一致)
			rs.updateString(columnName, vectorStr);
		} else if (dbType == DBType.SQLSERVER) {
			// update 2026-9-8 实测sqlserver的vector rs回写:12.x驱动字符串报"未使用有效的
			// 十六进制格式"、bytes报"BINARY到vector"均被拒(12.x驱动setter无vector类型
			// 支持);13.4+驱动提供microsoft.sql.Vector类型,以反射构造Vector对象回写
			// (维度数+FLOAT32+浮点数组);构造不可用(旧驱动)时回退字符串交由服务器报原始错误
			Object mssqlVector = buildMssqlVector(vectorStr);
			if (mssqlVector != null) {
				rs.updateObject(columnName, mssqlVector);
			} else {
				rs.updateObject(columnName, vectorStr);
			}
		} else if ((dbType == DBType.MYSQL || dbType == DBType.MYSQL57) && !isOceanBaseConnection()) {
			// update 2026-9-7 实测mysql9的rs回写拒绝字符串(vector仅收内部格式:
			// 维度个float32小端直排无头部),updateObject字符串报"cannot be converted";
			// ob的mysql模式排除(vector为字符串隐式转换,updateObject文本直接可用)
			byte[] vectorBytes = toVectorBytes(vectorStr);
			if (vectorBytes != null) {
				rs.updateBytes(columnName, vectorBytes);
			} else {
				rs.updateObject(columnName, vectorStr);
			}
		} else {
			rs.updateObject(columnName, vectorStr);
		}
	}

	/**
	 * 当前连接是否实为oceanbase(按mysql方言配置):ThreadLocal的actuallyDBType为
	 * URL特征判定的OCEANBASE时为true(显式dialect=mysql时processDataSource会设置)
	 */
	private static boolean isOceanBaseConnection() {
		Integer actualDbType = SqlToyThreadDataHolder.getActuallyDBType();
		return (actualDbType != null && actualDbType == DBType.OCEANBASE);
	}

	/**
	 * 将'[1,2,3]'形式向量文本编码为mysql内部格式字节(维度个float32小端直排,无头部,
	 * 与getBytes读回形态一致),解析失败返回null
	 * 
	 * @param vectorStr 向量文本
	 * @return 内部格式字节,格式不符返回null
	 */
	private static byte[] toVectorBytes(String vectorStr) {
		try {
			String str = vectorStr.trim();
			if (!str.startsWith("[") || !str.endsWith("]")) {
				return null;
			}
			String[] parts = str.substring(1, str.length() - 1).split(",");
			java.nio.ByteBuffer buf = java.nio.ByteBuffer.allocate(parts.length * 4)
					.order(java.nio.ByteOrder.LITTLE_ENDIAN);
			for (String part : parts) {
				buf.putFloat(Float.parseFloat(part.trim()));
			}
			return buf.array();
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * 将vector向量值转为'[1,2,3]'形式的字符串
	 * 
	 * @param value 向量值，支持字符串、数组、集合类型
	 * @return 返回null表示属于驱动专属向量对象(如org.pgvector.PGvector、oracle.sql.VECTOR),应直接setObject透传
	 */
	private static String toVectorString(Object value) {
		if (value instanceof String) {
			return (String) value;
		}
		StringBuilder buf = new StringBuilder("[");
		boolean hasElement = false;
		if (value instanceof Collection) {
			for (Object item : (Collection) value) {
				bufAppendVectorElement(buf, hasElement, item);
				hasElement = true;
			}
		} else if (value != null && value.getClass().isArray()) {
			int size = java.lang.reflect.Array.getLength(value);
			for (int i = 0; i < size; i++) {
				bufAppendVectorElement(buf, hasElement, java.lang.reflect.Array.get(value, i));
				hasElement = true;
			}
		} else {
			return null;
		}
		buf.append("]");
		return buf.toString();
	}

	/**
	 * 向字符串缓冲中追加vector单个元素(浮点数避免科学计数法表示)
	 * 
	 * @param buf        字符串缓冲区
	 * @param hasElement 是否已有前置元素(决定是否先追加逗号分隔符)
	 * @param item       向量的单个元素值，null抛出IllegalArgumentException
	 */
	private static void bufAppendVectorElement(StringBuilder buf, boolean hasElement, Object item) {
		if (item == null) {
			throw new IllegalArgumentException("the vector type value contains null elements, please check!");
		}
		if (hasElement) {
			buf.append(",");
		}
		if (item instanceof Float) {
			// 通过Float.toString取最短精度表示(避免float提升为double产生精度尾巴),再转为非科学计数法
			buf.append(new BigDecimal(Float.toString((Float) item)).toPlainString());
		} else if (item instanceof Double) {
			buf.append(new BigDecimal(Double.toString((Double) item)).toPlainString());
		} else if (item instanceof BigDecimal) {
			buf.append(((BigDecimal) item).toPlainString());
		} else {
			buf.append(item.toString());
		}
	}

	/**
	 * geometry空间类型参数赋值,统一以WKT字符串为媒介:postgresql系通过PGobject包装,
	 * mysql、h2等setString隐式转换;oracle由驱动层构造SDO_GEOMETRY STRUCT绑定
	 * (SQL层SDO_UTIL.FROM_WKTGEOMETRY遇null参数报ORA-29532且无法覆盖insert/update,
	 * 2026-9-6起废弃SQL层包装,insert/update/merge共用本绑定逻辑), sqlserver需SQL层cast(? as
	 * geometry)配合
	 *
	 * @param dbType     数据库类型，参见DataSourceUtils.DBType
	 * @param pst        PreparedStatement预编译语句对象
	 * @param paramIndex 参数位置下标(从1开始)
	 * @param value      空间类型值，支持WKT字符串、JTS Geometry或驱动专属对象
	 * @throws SQLException
	 */
	private static void setGeometryValue(Integer dbType, PreparedStatement pst, int paramIndex, Object value)
			throws SQLException {
		String geomStr = toGeometryString(value);
		// PGgeometry等驱动专属对象直接透传
		if (geomStr == null) {
			pst.setObject(paramIndex, value);
			return;
		}
		// update 2026-9-6 按连接URL scheme选择同源驱动PGobject(同setVectorValue,规避跨驱动错配)
		Object pgObject = isPGFamily(dbType) ? getPGobjectByConn(pst, "geometry", geomStr) : null;
		if (pgObject != null) {
			pst.setObject(paramIndex, pgObject);
		} else if (isPGFamily(dbType)) {
			pst.setObject(paramIndex, geomStr, java.sql.Types.OTHER);
		} else if (dbType == DBType.ORACLE || dbType == DBType.ORACLE11) {
			// SDO_GEOMETRY不接受VARCHAR隐式转换(实测ORA-00932),以WKT解析编码后构造驱动STRUCT绑定
			Object sdoValue = buildOracleSdo(pst.getConnection(), geomStr);
			if (sdoValue != null) {
				pst.setObject(paramIndex, sdoValue);
			} else {
				// 无ojdbc/jts或无法识别为空间值时回退setString,交由数据库暴露原始错误
				pst.setString(paramIndex, geomStr);
			}
		} else {
			pst.setString(paramIndex, geomStr);
		}
	}

	/**
	 * 将WKT字符串解析编码为oracle.sql.STRUCT(SDO_GEOMETRY)
	 *
	 * @param conn    数据库连接
	 * @param geomStr WKT/EWKT字符串
	 * @return STRUCT实例,解析或构造失败返回null
	 */
	private static Object buildOracleSdo(Connection conn, String geomStr) {
		Object[] sdoArgs = GeometryTypeUtil.toSdoAttributes(geomStr);
		if (sdoArgs == null) {
			return null;
		}
		return OracleSdoUtil.buildSdoGeometry(conn, (Integer) sdoArgs[0], (Integer) sdoArgs[1], (int[]) sdoArgs[2],
				(double[]) sdoArgs[3]);
	}

	/**
	 * geometry空间列回写(upsert等场景通过ResultSet回写)
	 * 
	 * @param dbType     数据库类型，参见DataSourceUtils.DBType
	 * @param rs         ResultSet结果集对象
	 * @param columnName 列名称
	 * @param value      空间类型值，支持WKT字符串、JTS Geometry或驱动专属对象
	 * @throws SQLException
	 */
	public static void updateGeometryValue(Integer dbType, ResultSet rs, String columnName, Object value)
			throws SQLException {
		String geomStr = toGeometryString(value);
		if (geomStr == null) {
			rs.updateObject(columnName, value);
			return;
		}
		// update 2026-9-6 按连接URL scheme选择同源驱动PGobject(同setVectorValue,规避跨驱动错配)
		Object pgObject = isPGFamily(dbType) ? getPGobjectByConn(rs.getStatement().getConnection(), "geometry", geomStr)
				: null;
		if (pgObject != null) {
			rs.updateObject(columnName, pgObject);
		} else if (dbType == DBType.ORACLE || dbType == DBType.ORACLE11) {
			// 同setGeometryValue,oracle以驱动层STRUCT绑定,string直写会报ORA-00932
			Object sdoValue = buildOracleSdo(rs.getStatement().getConnection(), geomStr);
			if (sdoValue != null) {
				rs.updateObject(columnName, sdoValue);
			} else {
				rs.updateObject(columnName, geomStr);
			}
		} else if (dbType == DBType.SQLSERVER) {
			// update 2026-9-8 实测rs回写:字符串报"未使用有效的十六进制格式";驱动内部
			// Geometry对象updateObject报"GEOMETRY到udt"转换不被setter识别;以
			// Geometry.parse(wkt).serialize()产内部格式字节经updateBytes回写(实测
			// updateRow落库STAsText正确);序列化不可用时回退字符串交由服务器报原始错误
			byte[] mssqlGeomBytes = buildMssqlGeometryBytes(geomStr);
			if (mssqlGeomBytes != null) {
				rs.updateBytes(columnName, mssqlGeomBytes);
			} else {
				rs.updateObject(columnName, geomStr);
			}
		} else if (dbType == DBType.MYSQL || dbType == DBType.MYSQL57 || dbType == DBType.OCEANBASE
				|| isOceanBaseConnection()) {
			// update 2026-9-7 实测mysql9的rs回写拒绝字符串(geometry仅收内部格式:
			// 4字节小端SRID前缀+小端WKB),编码失败回退字符串交由服务器报原始错误;
			// update 2026-9-8 实测ob同样:rs回写字符串报"Cannot get geometry object",
			// 内部格式字节updateBytes回写实测落库正确(ST_AsText回读一致),ob纳入本分支
			// update 2026-9-9 编码下沉JtsGeometryCodec经hasJts门控(本类不得含JTS符号引用)
			byte[] geomBytes = GeometryTypeUtil.wktToMysqlInternalBytes(geomStr);
			if (geomBytes != null) {
				rs.updateBytes(columnName, geomBytes);
			} else {
				rs.updateObject(columnName, geomStr);
			}
		} else {
			rs.updateObject(columnName, geomStr);
		}
	}

	/**
	 * 以mssql-jdbc 13.4+内置的microsoft.sql.Vector构造类型化向量对象(rs回写用):
	 * Vector(dimensionCount, VectorDimensionType.FLOAT32, Float[]);12.x驱动无此类
	 * (setter对字符串/字节均拒vector列),反射构造失败返回null由调用方回退
	 *
	 * @param vectorStr 向量文本形如[1,2,3]
	 * @return microsoft.sql.Vector实例,驱动不支持或解析失败返回null
	 */
	private static Object buildMssqlVector(String vectorStr) {
		try {
			String body = vectorStr.trim();
			if (body.startsWith("[")) {
				body = body.substring(1);
			}
			if (body.endsWith("]")) {
				body = body.substring(0, body.length() - 1);
			}
			String[] parts = body.split(",");
			int n = parts.length;
			Float[] data = new Float[n];
			for (int i = 0; i < n; i++) {
				data[i] = Float.parseFloat(parts[i].trim());
			}
			Class<?> vecCls = Class.forName("microsoft.sql.Vector");
			Class<?> dimTypeCls = Class.forName("microsoft.sql.Vector$VectorDimensionType");
			Object float32 = Enum.valueOf((Class<? extends Enum>) dimTypeCls, "FLOAT32");
			return vecCls.getConstructor(int.class, dimTypeCls, Object[].class).newInstance(n, float32, data);
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * 以mssql-jdbc内置的com.microsoft.sqlserver.jdbc.Geometry.parse将WKT文本构造为
	 * 内部格式字节(rs回写用),Geometry对象直接updateObject报"GEOMETRY到udt"不被setter
	 * 识别,须serialize()产字节经updateBytes回写;驱动不在classpath或解析失败返回null
	 *
	 * @param wkt WKT文本
	 * @return 内部格式字节,失败返回null
	 */
	private static byte[] buildMssqlGeometryBytes(String wkt) {
		try {
			Class<?> geoCls = Class.forName("com.microsoft.sqlserver.jdbc.Geometry");
			Object geom = geoCls.getMethod("parse", String.class).invoke(null, wkt);
			return (byte[]) geoCls.getMethod("serialize").invoke(geom);
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * 提取geometry值的WKT字符串:字符串原样透传,JTS Geometry对象转WKT
	 * (jts-core为可选依赖,无JTS或非Geometry对象返回null交由驱动透传)
	 * 
	 * @param value 空间类型值，可为WKT字符串、JTS Geometry或驱动专属对象
	 * @return WKT字符串，无法转换时返回null
	 */
	private static String toGeometryString(Object value) {
		if (value instanceof String) {
			return (String) value;
		}
		return GeometryTypeUtil.toWKT(value);
	}

	/**
	 * setArray gaussdb 必须要通过conn构造Array
	 * 
	 * @param dbType     数据库类型，参见DataSourceUtils.DBType
	 * @param conn       数据库连接对象(gaussdb系构造Array必须)
	 * @param pst        PreparedStatement预编译语句对象
	 * @param paramIndex 参数位置下标(从1开始)
	 * @param paramValue 数组类型参数值(如Integer[]、String[])
	 * @throws SQLException
	 */
	private static void setArray(Integer dbType, Connection conn, PreparedStatement pst, int paramIndex,
			Object paramValue) throws SQLException {
		// 目前只支持Integer 和 String两种类型
		if (dbType == DBType.GAUSSDB || dbType == DBType.MOGDB || dbType == DBType.OPENGAUSS
				|| dbType == DBType.VASTBASE || dbType == DBType.STARDB || dbType == DBType.OSCAR) {
			if (paramValue instanceof Integer[]) {
				Array array = conn.createArrayOf("INTEGER", (Integer[]) paramValue);
				pst.setArray(paramIndex, array);
			} else if (paramValue instanceof String[]) {
				Array array = conn.createArrayOf("VARCHAR", (String[]) paramValue);
				pst.setArray(paramIndex, array);
			} else if (paramValue instanceof BigDecimal[]) {
				Array array = conn.createArrayOf("NUMBER", (BigDecimal[]) paramValue);
				pst.setArray(paramIndex, array);
			} else if (paramValue instanceof BigInteger[]) {
				Array array = conn.createArrayOf("BIGINT", (BigInteger[]) paramValue);
				pst.setArray(paramIndex, array);
			} else if (paramValue instanceof Float[]) {
				Array array = conn.createArrayOf("FLOAT", (Float[]) paramValue);
				pst.setArray(paramIndex, array);
			} else if (paramValue instanceof Long[]) {
				Array array = conn.createArrayOf("INTEGER", (Long[]) paramValue);
				pst.setArray(paramIndex, array);
			} else {
				pst.setObject(paramIndex, paramValue, java.sql.Types.ARRAY);
			}
		} else {
			pst.setObject(paramIndex, paramValue, java.sql.Types.ARRAY);
		}
	}

	/**
	 * 提供数据查询结果集转java对象的反射处理，以java VO集合形式返回
	 * 
	 * @param dbType            数据库类型，参见DataSourceUtils.DBType(扩展类型列读取策略分派用)
	 * @param typeHandler       自定义类型处理器，非null时优先通过其完成列值转换
	 * @param rs                ResultSet结果集对象
	 * @param voClass           目标VO对象类型
	 * @param ignoreAllEmptySet true表示整行数据全为空值时跳过不构造VO对象
	 * @param columnFieldMap    数据库列名与对象属性的对照映射，null或空时按列名去除下划线映射
	 * @return VO对象集合
	 * @throws Exception
	 */
	private static List reflectResultToVO(Integer dbType, TypeHandler typeHandler, DecryptHandler decryptHandler,
			ResultSet rs, Class voClass, boolean ignoreAllEmptySet, HashMap<String, String> columnFieldMap)
			throws Exception {
		List resultList = new ArrayList();
		// 提取数据预警阈值
		int warnThresholds = SqlToyConstants.getWarnThresholds();
		// 是否超出阈值
		boolean warnLimit = false;
		// 最大阀值
		long maxThresholds = SqlToyConstants.getMaxThresholds();
		boolean maxLimit = false;
		// 最大值要大于等于警告阀值
		if (maxThresholds > 1 && maxThresholds <= warnThresholds) {
			maxThresholds = warnThresholds;
		}
		// rs 中的列名称
		String[][] columnLabelAndTypes = getColumnLabelAndTypes(rs.getMetaData());
		String[] columnNames = columnLabelAndTypes[0];
		String[] columnTypes = columnLabelAndTypes[1];
		// update 2026-9-9 复用Map路径(processResultRow)的列读取策略预分类:oracle的JSON/VECTOR列
		// getObject直接抛ORA-17004/18722须TEXT_READ文本化读取,sqlserver/mysql系的vector/geometry
		// 读回byte[]须EXT_BYTE按列类型解码,此前VO直映射路径未接入导致查询失败或byte[]静默乱码
		int[] columnKinds = ResultUtils.buildColumnKinds(dbType, columnTypes);
		// 组织vo中对应的属性
		String[] fields = new String[columnNames.length];
		// update 2020-12-24 增加映射对象时属性映射关系提取
		boolean hasMap = (columnFieldMap == null || columnFieldMap.isEmpty()) ? false : true;
		// 剔除下划线
		for (int i = 0; i < fields.length; i++) {
			fields[i] = columnNames[i].toLowerCase(Locale.ROOT);
			// 存在pojo中属性跟数据库字段名称有对照映射关系的
			if (hasMap) {
				if (columnFieldMap.containsKey(fields[i])) {
					fields[i] = columnFieldMap.get(fields[i]);
				} else {
					fields[i] = fields[i].replace("_", "");
				}
			} else {
				fields[i] = fields[i].replace("_", "");
			}
		}
		// 匹配对应的set方法
		Method[] setMethods = BeanUtil.matchSetMethods(voClass, fields);
		// set方法对应参数的类型,并全部转为小写
		String[] propTypes = new String[setMethods.length];
		int[] propTypeValues = new int[setMethods.length];
		int[] propertySqlTypes = new int[setMethods.length];
		Class[] genericTypes = new Class[setMethods.length];
		Map<String, Integer> fieldTypeMap = BeanUtil.getClassFieldMap(voClass, fields);
		Type[] types;
		Class methodType;
		String tmpStr;
		for (int i = 0; i < propTypes.length; i++) {
			propertySqlTypes[i] = java.sql.Types.OTHER;
			if (setMethods[i] != null) {
				methodType = setMethods[i].getParameterTypes()[0];
				propTypes[i] = methodType.getTypeName();
				propTypeValues[i] = DataType.getType(methodType);
				if (fields[i] != null) {
					tmpStr = fields[i].toLowerCase(Locale.ROOT);
					if (fieldTypeMap.containsKey(tmpStr)) {
						propertySqlTypes[i] = fieldTypeMap.get(tmpStr);
					} else if (columnTypes[i] != null) {
						tmpStr = columnTypes[i].toUpperCase(Locale.ROOT);
						if (tmpStr.equals("JSON")) {
							propertySqlTypes[i] = JdbcTypes.JSON;
						} else if (tmpStr.equals("JSONB")) {
							propertySqlTypes[i] = JdbcTypes.JSONB;
						} else if (tmpStr.equals("GEOMETRY")) {
							propertySqlTypes[i] = JdbcTypes.GEOMETRY;
						} else if (tmpStr.equals("UUID")) {
							propertySqlTypes[i] = JdbcTypes.UUID;
						} else if (tmpStr.equals("VECTOR") || tmpStr.equals("FLOATVECTOR")) {
							// floatvector为gaussdb企业版的向量类型名
							propertySqlTypes[i] = JdbcTypes.VECTOR;
						} else if (GeometryTypeUtil.isGeometryTypeName(tmpStr)) {
							// geometry空间类型(含mysql的POINT等子类型名)
							propertySqlTypes[i] = JdbcTypes.GEOMETRY;
						}
					}
				}
				types = setMethods[i].getGenericParameterTypes();
				if (types.length > 0 && (types[0] instanceof ParameterizedType)) {
					genericTypes[i] = (Class) ((ParameterizedType) types[0]).getActualTypeArguments()[0];
				}
			}
		}
		int index = 0;
		// 循环通过java reflection将rs中的值映射到VO中
		Object rowData;
		while (rs.next()) {
			rowData = reflectResultRowToVOClass(dbType, typeHandler, decryptHandler, rs, columnNames, columnTypes,
					columnKinds, propertySqlTypes, setMethods, propTypeValues, propTypes, genericTypes, voClass,
					ignoreAllEmptySet);
			if (rowData != null) {
				resultList.add(rowData);
			}
			index++;
			// 存在超出25000条数据的查询
			if (index == warnThresholds) {
				warnLimit = true;
			}
			// 超出最大提取数据阀值,直接终止数据提取
			if (index == maxThresholds) {
				maxLimit = true;
				break;
			}
		}
		// 提醒实际提取数量
		if (warnLimit) {
			logger.warn("Large Result:class={},total={}>={}", voClass.getName(), index, warnThresholds);
		}
		// 提醒实际提取数量
		if (maxLimit) {
			logger.warn("Large Result:class={},total:{}>={}", voClass.getName(), index, maxThresholds);
		}
		return resultList;
	}

	/**
	 * 提供数据查询结果集转java对象的反射处理，以java VO集合形式返回
	 * 
	 * @param dbType            数据库类型，参见DataSourceUtils.DBType(扩展类型列读取策略与归一化分派用)
	 * @param typeHandler       自定义类型处理器，非null时优先通过其完成列值转换
	 * @param decryptHandler    解密
	 * @param rs                当前行ResultSet结果集(已调用next定位)
	 * @param columnLabels      结果集列名称数组(原始label未做大小写处理,与rs列下标i+1一一对齐,仅用于解密列判断)
	 * @param columnTypes       列元数据类型名数组(与columnLabels同下标,EXT_BYTE列解码判定用)
	 * @param columnKinds       列读取策略标记数组(ResultUtils.buildColumnKinds预分类结果)
	 * @param propertySqlTypes  jdbcTypes.JSON 等
	 * @param setMethods        VO属性对应的setter方法数组，与列一一对应
	 * @param propTypeValues    对应类型int值
	 * @param propTypes         没有做大小写处理
	 * @param genericTypes      setter参数的泛型实际类型数组(如List<String>的String)
	 * @param voClass           目标VO对象类型
	 * @param ignoreAllEmptySet true表示整行数据全为空值时返回null不构造VO对象
	 * @return 当前行映射成的VO对象，整行为空且ignoreAllEmptySet为true返回null
	 * @throws Exception
	 */
	private static Object reflectResultRowToVOClass(Integer dbType, TypeHandler typeHandler,
			DecryptHandler decryptHandler, ResultSet rs, String[] columnLabels, String[] columnTypes, int[] columnKinds,
			int[] propertySqlTypes, Method[] setMethods, int[] propTypeValues, String[] propTypes, Class[] genericTypes,
			Class voClass, boolean ignoreAllEmptySet) throws Exception {
		// 根据匹配的字段通过java reflection将rs中的值映射到VO中
		Object bean = voClass.getDeclaredConstructor().newInstance();
		Object fieldValue;
		boolean allNull = true;
		Method method;
		// 已经小写
		String typeName;
		String label;
		int typeValue;
		int columnJdbcType;
		for (int i = 0, n = columnLabels.length; i < n; i++) {
			label = columnLabels[i];
			columnJdbcType = propertySqlTypes[i];
			method = setMethods[i];
			typeName = propTypes[i];
			typeValue = propTypeValues[i];
			if (method != null) {
				// update 2026-9-8 按下标取值(与labelNames同下标,驱动按下标直达,按label需查找)
				// fieldValue = rs.getObject(label);
				// update 2026-9-9 TEXT_READ列文本化读取(oracle的JSON/VECTOR列getObject直接抛错),
				// 取值后统一经扩展类型归一化(byte[]形态按列类型解码),与Map路径processResultRow对齐
				int kind = (columnKinds == null) ? ResultUtils.COLUMN_NORMAL : columnKinds[i];
				fieldValue = (kind == ResultUtils.COLUMN_TEXT_READ) ? rs.getString(i + 1) : rs.getObject(i + 1);
				if (null != fieldValue) {
					// update 2026-9-11 java.sql.Array经normalizeExtTypeValue归一为原生java数组
					// (保结构),目标形态转换由下方convertType按属性类型分派(String→'[a,b]'文本、
					// List/Set/数组→元素级转换),此处无需按目标类型区分
					fieldValue = ResultUtils.normalizeExtTypeValue(fieldValue, dbType,
							(kind == ResultUtils.COLUMN_EXT_BYTE) ? columnTypes[i] : null);
					if (decryptHandler != null) {
						fieldValue = decryptHandler.decrypt(label, fieldValue);
					}
					allNull = false;
					method.invoke(bean, BeanUtil.convertType(typeHandler, fieldValue, columnJdbcType, typeValue,
							typeName, genericTypes[i]));
				}
			}
		}
		if (allNull && ignoreAllEmptySet) {
			return null;
		}
		return bean;
	}

	/**
	 * 获取ResultSet 里面的列名称和类型
	 * 
	 * @param rsmd 结果集元数据对象
	 * @return 二维数组，[0]为列名称数组，[1]为列类型名称数组
	 * @throws SQLException
	 */
	private static String[][] getColumnLabelAndTypes(ResultSetMetaData rsmd) throws SQLException {
		int fieldCnt = rsmd.getColumnCount();
		String[][] columnLabelAndTypes = new String[2][fieldCnt];
		for (int i = 1; i < fieldCnt + 1; i++) {
			columnLabelAndTypes[0][i - 1] = rsmd.getColumnLabel(i);
			columnLabelAndTypes[1][i - 1] = rsmd.getColumnTypeName(i);
		}
		return columnLabelAndTypes;
	}

	/**
	 * 提供统一的ResultSet, PreparedStatemenet 关闭功能
	 * 
	 * @param userData                       传给回调处理器的输入数据
	 * @param pst                            PreparedStatement预编译语句对象，执行完毕后关闭
	 * @param rs                             ResultSet结果集对象，执行完毕后关闭
	 * @param preparedStatementResultHandler 结果处理回调接口，执行具体业务并返回结果
	 * @return 回调处理器返回的处理结果
	 * @throws Exception
	 */
	public static Object preparedStatementProcess(Object userData, PreparedStatement pst, ResultSet rs,
			PreparedStatementResultHandler preparedStatementResultHandler) throws Exception {
		try {
			preparedStatementResultHandler.execute(userData, pst, rs);
		} catch (Exception se) {
			logger.error(se.getMessage(), se);
			throw se;
		} finally {
			try {
				if (rs != null) {
					rs.close();
					rs = null;
				}
				if (pst != null) {
					pst.close();
					pst = null;
				}
			} catch (SQLException se) {
				logger.error("preparedStatementProcess method execution failed", se);
			}
		}
		return preparedStatementResultHandler.getResult();
	}

	/**
	 * 提供统一的ResultSet, callableStatement 关闭功能
	 * 
	 * @param userData                       传给回调处理器的输入数据
	 * @param pst                            CallableStatement存储过程调用语句对象，执行完毕后关闭
	 * @param rs                             ResultSet结果集对象，执行完毕后关闭
	 * @param callableStatementResultHandler 结果处理回调接口，执行具体业务并返回结果
	 * @return 回调处理器返回的处理结果
	 * @throws Exception
	 */
	public static Object callableStatementProcess(Object userData, CallableStatement pst, ResultSet rs,
			CallableStatementResultHandler callableStatementResultHandler) throws Exception {
		try {
			callableStatementResultHandler.execute(userData, pst, rs);
		} catch (Exception se) {
			logger.error(se.getMessage(), se);
			throw se;
		} finally {
			try {
				if (rs != null) {
					rs.close();
					rs = null;
				}
				if (pst != null) {
					pst.close();
					pst = null;
				}
			} catch (SQLException se) {
				logger.error("callableStatementProcess method execution failed", se);
			}
		}
		return callableStatementResultHandler.getResult();
	}

	/**
	 * 剔除sql中的注释(提供三种形态的注释剔除)
	 * 
	 * @param sql 原始sql语句，空白时原样返回
	 * @return 剔除<!-- -->、斜杠星注释和--三种注释(保留oracle hint)并去除末尾分号后的sql语句
	 */
	public static String clearMark(String sql) {
		if (StringUtil.isBlank(sql)) {
			return sql;
		}
		int endMarkIndex;
		// 剔除<!-- -->形式的多行注释
		int markIndex = sql.indexOf("<!--");
		while (markIndex != -1) {
			endMarkIndex = sql.indexOf("-->", markIndex);
			// update 2024-7-8 兼容sql中存在<!-- 但没有-->收尾的情况
			if (endMarkIndex == -1) {
				break;
			} else if (endMarkIndex == sql.length() - 3) {
				sql = sql.substring(0, markIndex);
				break;
			} else {
				sql = sql.substring(0, markIndex).concat(BLANK).concat(sql.substring(endMarkIndex + 3));
			}
			markIndex = sql.indexOf("<!--");
		}
		// 剔除/* */形式的多行注释(如果是/*+ALL_ROWS*/ 或 /*! ALL_ROWS*/形式的诸如oracle hint的用法不看作是注释)
		markIndex = StringUtil.matchIndex(sql, maskPattern);
		while (markIndex != -1) {
			endMarkIndex = sql.indexOf("*/", markIndex);
			// update 2024-7-8 兼容sql中存在/* 但没有*/收尾的情况
			if (endMarkIndex == -1) {
				break;
			} else if (endMarkIndex == sql.length() - 2) {
				sql = sql.substring(0, markIndex);
				break;
			} else {
				sql = sql.substring(0, markIndex).concat(BLANK).concat(sql.substring(endMarkIndex + 2));
			}
			markIndex = StringUtil.matchIndex(sql, maskPattern);
		}
		// 单行注释，必须要放在最后处理，避免跟<!-- --> 这类冲突
		if (sql.contains("--")) {
			String[] sqlAry = sql.split("\n");
			StringBuilder sqlBuffer = new StringBuilder();
			int startMask;
			int lineMaskIndex;
			String lineStr;
			int meter = 0;
			for (String line : sqlAry) {
				lineStr = line.trim();
				// 排除掉-- 开头和空行
				if (!"".equals(lineStr) && !lineStr.startsWith("--")) {
					// 不包含-- 直接拼接
					lineMaskIndex = line.indexOf("--");
					if (meter > 0) {
						sqlBuffer.append("\n");
					}
					// 增加一个空白
					sqlBuffer.append(BLANK);
					if (lineMaskIndex == -1) {
						sqlBuffer.append(line);
					} else {
						// 找到-- 单行注释开始位置(排除在'',""中间的场景)
						startMask = findStartLineMask(line, lineMaskIndex);
						if (startMask > 0) {
							sqlBuffer.append(line.substring(0, startMask));
						} else {
							sqlBuffer.append(line);
						}
					}
					meter++;
				}
			}
			sql = sqlBuffer.toString();
		}
		// 剔除sql末尾的分号逗号(开发过程中容易忽视)
		if (sql.endsWith(";") || sql.endsWith(",")) {
			sql = sql.substring(0, sql.length() - 1);
		}
		// 剔除全角(update 2023-10-24 框架不做干涉)
		// sql = sql.replaceAll("\\：", ":").replaceAll("\\＝", "=").replaceAll("\\．",
		// ".");
		return sql;
	}

	/**
	 * 找到行注释的开始位置
	 * 
	 * @param sql           单行sql内容
	 * @param lineMaskIndex --注释符号的位置
	 * @return 行注释的有效开始位置(排除引号和hint注释内部的--)，位于引号内时返回实际注释起始下标
	 */
	private static int findStartLineMask(String sql, int lineMaskIndex) {
		// 单引号、双引号、hint注释结尾 的最后位置
		int lastIndex = StringUtil.matchLastIndex(sql, "\'|\"|\\*\\/");
		// 行注释的位置在单引号等最后位置后面,直接返回
		if (lineMaskIndex > lastIndex) {
			return lineMaskIndex;
		}
		// 单引号之间
		int start = StringUtil.matchIndex(sql, "\'");
		int symMarkEnd;
		while (start != -1) {
			symMarkEnd = StringUtil.getSymMarkIndex("'", "'", sql, start);
			if (symMarkEnd != -1) {
				sql = sql.substring(0, start).concat(repeatBlank(symMarkEnd - start + 1))
						.concat(sql.substring(symMarkEnd + 1));
				start = StringUtil.matchIndex(sql, "\'");
			} else {
				break;
			}
		}
		// 双引号之间
		start = StringUtil.matchIndex(sql, "\"");
		while (start != -1) {
			symMarkEnd = StringUtil.getSymMarkIndex("\"", "\"", sql, start);
			if (symMarkEnd != -1) {
				sql = sql.substring(0, start).concat(repeatBlank(symMarkEnd - start + 1))
						.concat(sql.substring(symMarkEnd + 1));
				start = StringUtil.matchIndex(sql, "\"");
			} else {
				break;
			}
		}
		// hint /*+ all */ 或 /*! all*/ 注释
		start = sql.indexOf("/*");
		while (start != -1) {
			symMarkEnd = StringUtil.getSymMarkIndex("/*", "*/", sql, start);
			if (symMarkEnd != -1) {
				sql = sql.substring(0, start).concat(repeatBlank(symMarkEnd - start + 2))
						.concat(sql.substring(symMarkEnd + 2));
				start = sql.indexOf("/*");
			} else {
				break;
			}
		}
		return sql.indexOf("--");
	}

	/**
	 * 将剔除掉n的字符串替换为等长度的空白字符串，避免对sql解析造成影响
	 * 
	 * @param size 需要生成的空白字符个数
	 * @return 指定个数的空白字符串，size小于等于0返回空字符串
	 */
	private static String repeatBlank(int size) {
		if (size <= 0) {
			return "";
		}
		// Java 11+
		return BLANK.repeat(size);
	}

	/**
	 * 获取单条记录
	 * 
	 * @param typeHandler        自定义类型处理器，非null时优先通过其完成列值转换
	 * @param queryStr           查询sql语句
	 * @param params             sql中?对应的参数值数组
	 * @param voClass            目标VO对象类型
	 * @param rowCallbackHandler 行数据处理回调接口，非null时逐行回调处理
	 * @param conn               数据库连接对象
	 * @param dbType             数据库类型，参见DataSourceUtils.DBType
	 * @param ignoreAllEmptySet  true表示整行数据全为空值时跳过不构造对象
	 * @param colFieldMap        数据库列名与对象属性的对照映射，null时按列名去除下划线映射
	 * @return 单条记录对应的VO对象，无数据返回null，结果多于一条抛出IllegalAccessException
	 * @throws Exception
	 */
	public static Object loadByJdbcQuery(TypeHandler typeHandler, final String queryStr, final Object[] params,
			final Class voClass, final RowCallbackHandler rowCallbackHandler, final Connection conn,
			final Integer dbType, final boolean ignoreAllEmptySet, final HashMap<String, String> colFieldMap,
			final Integer queryTimeout) throws Exception {
		List result = findByJdbcQuery(typeHandler, queryStr, params, voClass, rowCallbackHandler, null, conn, dbType,
				ignoreAllEmptySet, colFieldMap, -1, -1, queryTimeout);
		if (result != null && !result.isEmpty()) {
			if (result.size() > 1) {
				throw new IllegalAccessException(
						"the query result is not unique, loadByJdbcQuery only supports a single row query!");
			}
			return result.get(0);
		}
		return null;
	}

	/**
	 * 提供独立的获取sequence下一个值的方法
	 * 
	 * @param conn     数据库连接对象
	 * @param sequence sequence名称
	 * @param dbType   数据库类型，参见DataSourceUtils.DBType
	 * @return sequence的下一个值，获取失败抛出DataAccessException
	 * @throws DataAccessException
	 */
	public static Object getSequenceValue(Connection conn, String sequence, Integer dbType) throws DataAccessException {
		String sql = "";
		if (dbType == DBType.POSTGRESQL || dbType == DBType.POSTGRESQL14 || dbType == DBType.KINGBASE
				|| dbType == DBType.H2) {
			sql = "select nextval('" + sequence + "')";
		} else if (dbType == DBType.SQLSERVER) {
			sql = "select NEXT VALUE FOR " + sequence;
		} else if (dbType == DBType.GAUSSDB || dbType == DBType.MOGDB || dbType == DBType.OPENGAUSS
				|| dbType == DBType.VASTBASE || dbType == DBType.OCEANBASE || dbType == DBType.ORACLE
				|| dbType == DBType.ORACLE11 || dbType == DBType.DM || dbType == DBType.STARDB
				|| dbType == DBType.OSCAR) {
			sql = "select " + sequence + ".nextval";
		} else {
			sql = "select NEXTVAL FOR " + sequence;
		}
		PreparedStatement pst = null;
		ResultSet rs = null;
		Object id = null;
		try {
			SqlExecuteStat.showSql("get next sequence value", sql, null);
			pst = conn.prepareStatement(sql);
			// 设置全局statementTimeout，默认为null
			if (SqlToyConstants.defaultStatementTimeout != null && SqlToyConstants.defaultStatementTimeout > 0) {
				pst.setQueryTimeout(SqlToyConstants.defaultStatementTimeout);
			}
			rs = pst.executeQuery();
			while (rs.next()) {
				id = rs.getObject(1);
				break;
			}
		} catch (Exception e) {
			logger.error("getSequenceValue method execution failed", e);
			throw new DataAccessException(
					"failed to get the value of sequence [" + sequence + "]! error message:" + e.getMessage(), e);
		} finally {
			if (rs != null) {
				try {
					rs.close();
				} catch (Exception e) {

				}
				rs = null;
			}
			if (pst != null) {
				try {
					pst.close();
				} catch (Exception e) {

				}
				pst = null;
			}
		}
		return id;
	}

	/**
	 * sql 查询并返回List集合结果
	 * 
	 * @param typeHandler        自定义类型处理器，非null时优先通过其完成列值转换
	 * @param queryStr           查询sql语句
	 * @param params             sql中?对应的参数值数组
	 * @param voClass            目标VO对象类型，null时按行List数组返回
	 * @param rowCallbackHandler 行数据处理回调接口，非null时逐行回调处理
	 * @param decryptHandler     字段解密处理器，非null时对列值做解密处理
	 * @param conn               数据库连接对象
	 * @param dbType             数据库类型，参见DataSourceUtils.DBType
	 * @param ignoreAllEmptySet  true表示整行数据全为空值时跳过
	 * @param colFieldMap        数据库列名与对象属性的对照映射，null时按列名去除下划线映射
	 * @param fetchSize          批量提取行数，小于等于0不设置
	 * @param maxRows            最大提取行数，小于等于0不设置
	 * @return 查询结果List集合，无数据返回空集合
	 * @throws Exception
	 */
	public static List findByJdbcQuery(TypeHandler typeHandler, final String queryStr, final Object[] params,
			final Class voClass, final RowCallbackHandler rowCallbackHandler, final DecryptHandler decryptHandler,
			final Connection conn, final Integer dbType, final boolean ignoreAllEmptySet,
			final HashMap<String, String> colFieldMap, final int fetchSize, final int maxRows,
			final Integer queryTimeout) throws Exception {
		ResultSet rs = null;
		PreparedStatement pst = conn.prepareStatement(queryStr, ResultSet.TYPE_FORWARD_ONLY,
				ResultSet.CONCUR_READ_ONLY);
		if (fetchSize > 0) {
			pst.setFetchSize(fetchSize);
		}
		if (maxRows > 0) {
			pst.setMaxRows(maxRows);
		}
		if (queryTimeout != null && queryTimeout > 0) {
			pst.setQueryTimeout(queryTimeout);
		}
		// 设置全局statementTimeout，默认为null
		else if (SqlToyConstants.defaultStatementTimeout != null && SqlToyConstants.defaultStatementTimeout > 0) {
			pst.setQueryTimeout(SqlToyConstants.defaultStatementTimeout);
		}
		List result = (List) preparedStatementProcess(null, pst, rs, new PreparedStatementResultHandler() {
			@Override
			public void execute(Object obj, PreparedStatement pst, ResultSet rs) throws Exception {
				try {
					setParamsValue(typeHandler, conn, dbType, pst, params, null, 0);
					rs = pst.executeQuery();
					this.setResult(processResultSet(dbType, typeHandler, rs, voClass, rowCallbackHandler,
							decryptHandler, 0, ignoreAllEmptySet, colFieldMap));
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
		// 为null返回一个空集合
		if (result == null) {
			result = new ArrayList();
		}
		return result;
	}

	/**
	 * 处理sql查询时的结果集, 当没有反调或voClass反射处理时以数组方式返回resultSet的数据
	 * 
	 * @param typeHandler        自定义类型处理器，非null时优先通过其完成列值转换
	 * @param rs                 ResultSet结果集对象
	 * @param voClass            目标VO对象类型，null时优先走rowCallbackHandler或行数组返回
	 * @param rowCallbackHandler 行数据处理回调接口，非null时逐行回调处理
	 * @param decryptHandler     字段解密处理器，非null时对列值做解密处理
	 * @param startColIndex      起始提取列下标(从0开始，按行数组返回时生效)
	 * @param ignoreAllEmptySet  true表示整行数据全为空值时跳过
	 * @param colFieldMap        数据库列名与对象属性的对照映射，null时按列名去除下划线映射
	 * @return 查询结果集合(VO集合、回调结果或二维List数组)，超出最大提取阀值时中断提取
	 * @throws Exception
	 */
	public static List processResultSet(Integer dbType, TypeHandler typeHandler, ResultSet rs, Class voClass,
			RowCallbackHandler rowCallbackHandler, final DecryptHandler decryptHandler, int startColIndex,
			boolean ignoreAllEmptySet, final HashMap<String, String> colFieldMap) throws Exception {
		// 记录行记数器
		int index = 0;
		// 提取数据预警阈值
		int warnThresholds = SqlToyConstants.getWarnThresholds();
		// 是否超出阈值
		boolean warnLimit = false;
		// 最大阀值
		long maxThresholds = SqlToyConstants.getMaxThresholds();
		boolean maxLimit = false;
		// 最大值要大于等于警告阀值
		if (maxThresholds > 1 && maxThresholds <= warnThresholds) {
			maxThresholds = warnThresholds;
		}
		List result;
		if (voClass != null) {
			result = reflectResultToVO(dbType, typeHandler, decryptHandler, rs, voClass, ignoreAllEmptySet,
					colFieldMap);
		} else if (rowCallbackHandler != null) {
			while (rs.next()) {
				rowCallbackHandler.processRow(rs, index);
				index++;
				// 超出预警阀值
				if (index == warnThresholds) {
					warnLimit = true;
				}
				// 提取数据超过上限(-1表示不限制)
				if (index == maxThresholds) {
					maxLimit = true;
					break;
				}
			}
			result = rowCallbackHandler.getResult();
		} else {
			// 取得字段列数,在没有rowCallbackHandler用数组返回
			int rowCnt = rs.getMetaData().getColumnCount();
			List items = new ArrayList();
			Object fieldValue = null;
			boolean allNull = true;
			// oracle 的时间戳非标准java类型
			boolean convertOracleTimestamp = SqlToyConstants.convertOracleTimestamp();
			int blobSize;
			while (rs.next()) {
				allNull = true;
				List rowData = new ArrayList();
				for (int i = startColIndex; i < rowCnt; i++) {
					// 处理clob
					fieldValue = rs.getObject(i + 1);
					if (fieldValue != null) {
						allNull = false;
						if (fieldValue instanceof java.sql.Clob) {
							fieldValue = clobToString((java.sql.Clob) fieldValue);
						} else if (fieldValue instanceof java.sql.Blob) {
							java.sql.Blob blob = (java.sql.Blob) fieldValue;
							blobSize = (int) blob.length();
							if (blobSize > 0) {
								fieldValue = blob.getBytes(1, blobSize);
							} else {
								fieldValue = new byte[0];
							}
						} else if (convertOracleTimestamp
								&& fieldValue.getClass().getTypeName().equals("oracle.sql.TIMESTAMP")) {
							fieldValue = BeanUtil.oracleTimeStampConvert(fieldValue);
						}
						// java 特定类型处理
						if (typeHandler != null) {
							fieldValue = typeHandler.toJavaType(dbType, fieldValue);
						}
					}
					rowData.add(fieldValue);
				}
				if (!(allNull && ignoreAllEmptySet)) {
					items.add(rowData);
				}
				index++;
				// 超出预警阀值
				if (index == warnThresholds) {
					warnLimit = true;
				}
				// 超出最大提取数据阀值,直接终止数据提取
				if (index == maxThresholds) {
					maxLimit = true;
					break;
				}
			}
			result = items;
		}
		// 提醒实际提取数据量
		if (warnLimit) {
			logger.warn("Large Result:total={}>={}", index, warnThresholds);
		}
		// 超过最大提取数据阀值
		if (maxLimit) {
			logger.error("Max Large Result:total={}>={}", index, maxThresholds);
		}
		return result;
	}

	// 仅提供对象形式的批量保存、修改、删除相关的最终sql执行
	public static Long batchUpdateForPOJO(TypeHandler typeHandler, final String updateSql,
			final List<Object[]> rowDatas, final Integer[] fieldsType, final int batchSize, final Boolean autoCommit,
			final Connection conn, final Integer dbType) throws Exception {
		// update 2026-9-6 原实现与SqlUtil.batchUpdateByJdbc的批量执行骨架(prepare/分批executeBatch/
		// 尾部批次补齐/autoCommit恢复/close)完全同构,属复制演化产物,骨架级改动需双处同步极易漏改;
		// 收敛为薄包装:数组逐位绑定封装为InsertRowCallbackHandler闭包,批量骨架统一由batchUpdateByJdbc承载;
		// 原fieldsDefaultValue/fieldsNullable参数剔除:数据取值阶段(reflectBeansToInnerAry配合
		// getDefaultValues)已完成null单元格的默认值填充,绑定层二次填充属冗余
		return batchUpdateByJdbc(typeHandler, updateSql, rowDatas, batchSize,
				new org.sagacity.sqltoy.callback.InsertRowCallbackHandler() {
					@Override
					public void process(PreparedStatement pst, int rowIndex, Object rowData) throws SQLException {
						Object[] row = (Object[]) rowData;
						int fieldType;
						for (int j = 0, n = row.length; j < n; j++) {
							fieldType = (fieldsType == null) ? -1 : fieldsType[j];
							try {
								SqlUtil.setParamValue(typeHandler, conn, dbType, pst, row[j], fieldType, j + 1);
							} catch (java.io.IOException e) {
								// 接口仅声明SQLException,blob等流式写值的IOException包装上抛
								throw new SQLException("batchUpdateForPOJO bind parameter failed!", e);
							}
						}
					}
				}, fieldsType, autoCommit, conn, dbType);
	}

	/**
	 * 通过jdbc方式批量插入数据，一般提供给数据采集时或插入临时表使用，一般采用hibernate 方式插入 <br/>
	 * 返回值为影响行数统计:本方法按行执行(每次addBatch绑定一行数据),对Oracle等驱动
	 * executeBatch返回SUCCESS_NO_INFO(-2,语句成功但行数未知)的语句按1行计,
	 * 对单行语句(insert、按主键update/delete)结果精确;若传入一条语句影响多行的sql则为估算值
	 * 
	 * @param typeHandler       自定义类型处理器，非null时优先通过其完成参数设置
	 * @param updateSql         增删改sql语句，?参数与数据行字段一一对应
	 * @param rowDatas          待处理的数据集合，元素可为数组、集合或bean(配合insertCallhandler)
	 * @param batchSize         批处理提交大小
	 * @param insertCallhandler 行数据参数绑定回调接口，null时按数组/集合方式绑定参数
	 * @param updateTypes       各参数对应的java.sql.Types类型数组，null时按参数值自动判断
	 * @param autoCommit        是否自动提交，null时保持连接原有提交方式
	 * @param conn              数据库连接对象
	 * @param dbType            数据库类型，参见DataSourceUtils.DBType
	 * @return 实际影响的行数统计
	 * @throws Exception
	 */
	public static Long batchUpdateByJdbc(TypeHandler typeHandler, final String updateSql, final Collection rowDatas,
			final int batchSize, final InsertRowCallbackHandler insertCallhandler, final Integer[] updateTypes,
			final Boolean autoCommit, final Connection conn, final Integer dbType) throws Exception {
		if (rowDatas == null || rowDatas.isEmpty()) {
			// update 2026-9-6 空数据返回0属正常业务场景(如saveAll空集合),由error降为warn
			logger.warn("batchUpdateByJdbc: the data is empty, sql={}", updateSql);
			return 0L;
		}
		// sql中?参数数量
		int argsCnt = StringUtil.matchCnt(SqlConfigParseUtils.clearDblQuestMark(updateSql),
				SqlConfigParseUtils.ARG_REGEX);
		PreparedStatement pst = null;
		long updateCount = 0;
		try {
			boolean hasSetAutoCommit = false;
			boolean useCallHandler = true;
			// 是否使用反调方式
			if (insertCallhandler == null) {
				useCallHandler = false;
			}
			// 是否自动提交
			if (autoCommit != null && autoCommit.booleanValue() != conn.getAutoCommit()) {
				conn.setAutoCommit(autoCommit.booleanValue());
				hasSetAutoCommit = true;
			}
			pst = conn.prepareStatement(updateSql);
			// 设置全局statementTimeout，默认为null
			if (SqlToyConstants.defaultStatementTimeout != null && SqlToyConstants.defaultStatementTimeout > 0) {
				pst.setQueryTimeout(SqlToyConstants.defaultStatementTimeout);
			}
			int totalRows = rowDatas.size();
			boolean useBatch = (totalRows > 1) ? true : false;
			Object rowData;
			int index = 0;
			// 批处理计数器
			int meter = 0;
			int paramCnt;
			for (Iterator iter = rowDatas.iterator(); iter.hasNext();) {
				rowData = iter.next();
				index++;
				if (rowData != null) {
					// 使用反调
					if (useCallHandler) {
						insertCallhandler.process(pst, index, rowData);
					} else {
						// 使用对象properties方式传值
						if (rowData.getClass().isArray()) {
							Object[] tmp = CollectionUtil.convertArray(rowData);
							paramCnt = tmp.length;
							// 第一次做长度校验
							if (meter == 0 && argsCnt != paramCnt) {
								throw new IllegalArgumentException("the ? param count [" + argsCnt
										+ "] in the batchUpdate sql does not equal the actual param count [" + paramCnt
										+ "], please check!");
							}
							for (int i = 0; i < paramCnt; i++) {
								setParamValue(typeHandler, conn, dbType, pst, tmp[i],
										updateTypes == null ? -1 : updateTypes[i], i + 1);
							}
						} else if (rowData instanceof Collection) {
							Collection tmp = (Collection) rowData;
							paramCnt = tmp.size();
							// 第一次做长度校验
							if (meter == 0 && argsCnt != paramCnt) {
								throw new IllegalArgumentException("the ? param count [" + argsCnt
										+ "] in the batchUpdate sql does not equal the actual param count [" + paramCnt
										+ "], please check!");
							}
							int tmpIndex = 0;
							for (Iterator tmpIter = tmp.iterator(); tmpIter.hasNext();) {
								setParamValue(typeHandler, conn, dbType, pst, tmpIter.next(),
										updateTypes == null ? -1 : updateTypes[tmpIndex], tmpIndex + 1);
								tmpIndex++;
							}
						}
					}
					meter++;
					// 批量执行
					if (useBatch) {
						pst.addBatch();
						if ((meter % batchSize) == 0) {
							int[] updateRows = pst.executeBatch();
							updateCount = updateCount + sumBatchUpdateCounts(updateRows);
							pst.clearBatch();
						}
					} // 单条执行
					else {
						updateCount = pst.executeUpdate();
					}
				}
			}
			// 集合尾部为null的行不会进入循环体内的批次执行判断，未执行的尾部批次需在循环外补齐执行
			if (useBatch && (meter % batchSize) != 0) {
				int[] updateRows = pst.executeBatch();
				updateCount = updateCount + sumBatchUpdateCounts(updateRows);
				pst.clearBatch();
			}
			if (hasSetAutoCommit) {
				conn.setAutoCommit(!autoCommit);
			}
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			throw e;
		} finally {
			try {
				if (pst != null) {
					pst.close();
					pst = null;
				}
			} catch (SQLException se) {
				logger.error(se.getMessage(), se);
			}
		}
		return updateCount;
	}

	/**
	 * 统计批量执行影响行数
	 * 
	 * @description 框架批量链路均为按行执行(每次addBatch绑定一行数据):
	 *              SUCCESS_NO_INFO(-2)表示语句执行成功但驱动不提供行数(如Oracle批量、 MySQL
	 *              rewriteBatchedStatements),按1计才能得到真实行数; EXECUTE_FAILED(-3)按0计
	 * @param updateRows executeBatch返回的各语句影响行数数组
	 * @return 累加后的总影响行数，SUCCESS_NO_INFO(-2)按1计，EXECUTE_FAILED(-3)按0计
	 */
	static long sumBatchUpdateCounts(int[] updateRows) {
		long total = 0;
		for (int t : updateRows) {
			if (t > 0) {
				total += t;
			} else if (t == java.sql.Statement.SUCCESS_NO_INFO) {
				total += 1;
			}
		}
		return total;
	}

	/**
	 * 计算树形结构表中的:节点层级、节点对应所有上级节点的路径、是否叶子节点
	 * 
	 * @param typeHandler    自定义类型处理器，非null时优先通过其完成参数设置
	 * @param treeTableModel 树形表模型(表名、id/pid字段、层级字段、路径字段、叶子字段等)
	 * @param conn           数据库连接对象
	 * @param dbType         数据库类型，参见DataSourceUtils.DBType
	 * @return 处理成功返回true，模型必填字段缺失抛出IllegalArgumentException
	 * @throws Exception
	 */
	public static boolean wrapTreeTableRoute(TypeHandler typeHandler, final TreeTableModel treeTableModel,
			Connection conn, final Integer dbType, final Integer queryTimeout) throws Exception {
		return wrapTreeTableRoute(typeHandler, treeTableModel, conn, dbType, queryTimeout, null);
	}

	/**
	 * 构造树形表的节点路径、节点层级、节点类别(是否叶子节点)
	 * 
	 * @param typeHandler       自定义类型处理器，非null时优先通过其完成参数设置
	 * @param treeTableModel    树形表模型(表名、id/pid字段、层级字段、路径字段、叶子字段等)
	 * @param conn              数据库连接对象
	 * @param dbType            数据库类型，参见DataSourceUtils.DBType
	 * @param queryTimeout      查询超时时间(秒)，null时使用默认值
	 * @param unifyUpdateFields 公共更新字段(数据库列名->值,如最后修改人、最后修改时间),
	 *                          非null时附加到全部路由update语句的set子句并绑定参数
	 * @return 处理成功返回true，模型必填字段缺失抛出IllegalArgumentException
	 * @throws Exception
	 */
	public static boolean wrapTreeTableRoute(TypeHandler typeHandler, final TreeTableModel treeTableModel,
			Connection conn, final Integer dbType, final Integer queryTimeout,
			final Map<String, Object> unifyUpdateFields) throws Exception {
		if (StringUtil.isBlank(treeTableModel.getTableName()) || StringUtil.isBlank(treeTableModel.getIdField())
				|| StringUtil.isBlank(treeTableModel.getPidField())
				|| StringUtil.isBlank(treeTableModel.getPidValue())) {
			logger.error("please set the table name, id field name, pid field name and pidValue of the tree table!");
			throw new IllegalArgumentException("tableName, idField, pidField and pidValue must all be provided!");
		}
		// 公共更新字段转数组,便于sql拼接与参数绑定
		String[] unifyColumns = null;
		Object[] unifyValues = null;
		if (unifyUpdateFields != null && !unifyUpdateFields.isEmpty()) {
			unifyColumns = new String[unifyUpdateFields.size()];
			unifyValues = new Object[unifyUpdateFields.size()];
			int unifyIndex = 0;
			for (Map.Entry<String, Object> entry : unifyUpdateFields.entrySet()) {
				unifyColumns[unifyIndex] = ReservedWordsUtil.convertWord(entry.getKey(), dbType);
				unifyValues[unifyIndex] = entry.getValue();
				unifyIndex++;
			}
		}
		String flag = "";
		// 判断是否字符串类型
		if (treeTableModel.isChar()) {
			flag = "'";
		}
		String nodeRouteField = ReservedWordsUtil.convertWord(treeTableModel.getNodeRouteField(), dbType);
		String nodeLevelField = ReservedWordsUtil.convertWord(treeTableModel.getNodeLevelField(), dbType);
		String idField = ReservedWordsUtil.convertWord(treeTableModel.getIdField(), dbType);
		String pidField = ReservedWordsUtil.convertWord(treeTableModel.getPidField(), dbType);
		String tableName = ReservedWordsUtil.convertSimpleSql(treeTableModel.getTableName(), dbType);
		String conditions = ReservedWordsUtil.convertWord(treeTableModel.getConditions(), dbType);
		String leafField = ReservedWordsUtil.convertWord(treeTableModel.getLeafField(), dbType);
		// 修改nodeRoute和nodeLevel
		if (StringUtil.isNotBlank(nodeRouteField) && StringUtil.isNotBlank(nodeLevelField)) {
			StringBuilder nextNodeQueryStr = new StringBuilder("select ").append(idField).append(",")
					.append(nodeRouteField).append(",").append(pidField).append(" from ").append(tableName)
					.append(" where ").append(pidField).append(" in (${inStr})");
			String idInfoSql = "select ".concat(nodeLevelField).concat(",").concat(nodeRouteField).concat(" from ")
					.concat(tableName).concat(" where ").concat(idField).concat("=").concat(flag)
					.concat(treeTableModel.getPidValue().toString()).concat(flag);
			// 附加条件(如一张表里面分账套,将多家企业的部门信息放于一张表中,附加条件就可以是账套)
			if (StringUtil.isNotBlank(conditions)) {
				idInfoSql = idInfoSql.concat(" and ").concat(conditions);
			}
			// 获取层次等级
			List idInfo = findByJdbcQuery(typeHandler, idInfoSql, null, null, null, null, conn, dbType, false, null,
					SqlToyConstants.FETCH_SIZE, -1, queryTimeout);
			// 设置第一层level
			int nodeLevel = 0;
			String nodeRoute = "";
			if (idInfo != null && !idInfo.isEmpty()) {
				if (((List) idInfo.get(0)).get(0) == null) {
					throw new DataAccessException("the node level field [" + nodeLevelField + "] of the row with id ["
							+ treeTableModel.getPidValue()
							+ "] is null, do not call wrapTreeTableRoute across levels!");
				}
				if (((List) idInfo.get(0)).get(1) == null) {
					throw new DataAccessException("the node route field [" + nodeRouteField + "] of the row with id ["
							+ treeTableModel.getPidValue()
							+ "] is null, do not call wrapTreeTableRoute across levels!");
				}
				nodeLevel = Integer.parseInt(((List) idInfo.get(0)).get(0).toString());
				nodeRoute = ((List) idInfo.get(0)).get(1).toString();
			}
			StringBuilder updateLevelAndRoute = new StringBuilder("update ").append(tableName).append(" set ")
					.append(nodeLevelField).append("=?,").append(nodeRouteField).append("=? ");
			// 附加公共更新字段(如最后修改人、最后修改时间)
			if (unifyColumns != null) {
				for (String unifyColumn : unifyColumns) {
					updateLevelAndRoute.append(",").append(unifyColumn).append("=?");
				}
			}
			updateLevelAndRoute.append(" where ").append(idField).append("=?");
			// 附加条件
			if (StringUtil.isNotBlank(conditions)) {
				nextNodeQueryStr.append(" and ").append(conditions);
				updateLevelAndRoute.append(" and ").append(conditions);
			}
			// 模拟指定节点的信息
			HashMap pidsMap = new HashMap();
			pidsMap.put(treeTableModel.getPidValue().toString(), nodeRoute);
			// 下级节点
			List ids;
			if (StringUtil.isNotBlank(treeTableModel.getIdValue())) {
				StringBuilder firstNextNodeQuery = new StringBuilder("select ").append(idField).append(",")
						.append(nodeRouteField).append(",").append(pidField).append(" from ").append(tableName)
						.append(" where ").append(idField).append("=?");
				if (StringUtil.isNotBlank(conditions)) {
					firstNextNodeQuery.append(" and ").append(conditions);
				}
				ids = findByJdbcQuery(typeHandler, firstNextNodeQuery.toString(),
						new Object[] { treeTableModel.getIdValue() }, null, null, null, conn, dbType, false, null,
						SqlToyConstants.FETCH_SIZE, -1, queryTimeout);
			} else {
				ids = findByJdbcQuery(typeHandler,
						nextNodeQueryStr.toString().replaceFirst("\\$\\{inStr\\}",
								flag + treeTableModel.getPidValue() + flag),
						null, null, null, null, conn, dbType, false, null, SqlToyConstants.FETCH_SIZE, -1,
						queryTimeout);
			}
			if (ids != null && !ids.isEmpty()) {
				processNextLevel(typeHandler, updateLevelAndRoute.toString(), nextNodeQueryStr.toString(),
						treeTableModel, pidsMap, ids, nodeLevel + 1, conn, dbType, queryTimeout, unifyValues);
			}
		}
		// 设置节点是否为叶子节点，（mysql不支持update table where in 机制）
		if (StringUtil.isNotBlank(leafField)) {
			// 将所有记录先全部设置为叶子节点(isLeaf=1)
			StringBuilder updateLeafSql = new StringBuilder();
			updateLeafSql.append("update ").append(tableName);
			updateLeafSql.append(" set ").append(leafField).append("=1");
			// 附加公共更新字段(如最后修改人、最后修改时间)
			if (unifyColumns != null) {
				for (String unifyColumn : unifyColumns) {
					updateLeafSql.append(",").append(unifyColumn).append("=?");
				}
			}
			// 附加条件(保留)
			if (StringUtil.isNotBlank(conditions)) {
				updateLeafSql.append(" where ").append(conditions);
			}
			// 先将所有节点设置为叶子
			executeSql(typeHandler, updateLeafSql.toString(), unifyValues, null, conn, dbType, null, true);
			// 再设置父节点的记录为非叶子节点(isLeaf=0)
			StringBuilder updateTrunkLeafSql = new StringBuilder();
			updateTrunkLeafSql.append("update ").append(tableName);
			// 支持mysql8 update 2018-5-11
			if (dbType == DBType.MYSQL || dbType == DBType.MYSQL57 || dbType == DBType.DORIS
					|| dbType == DBType.STARROCKS) {
				// update sys_organ_info a inner join (select t.organ_pid from
				// sys_organ_info t) b
				// on a.organ_id=b.organ_pid set IS_LEAF=0
				// set field=value
				updateTrunkLeafSql.append(" inner join (select ");
				updateTrunkLeafSql.append(pidField);
				updateTrunkLeafSql.append(" from ").append(tableName);
				if (StringUtil.isNotBlank(conditions)) {
					updateTrunkLeafSql.append(" where ").append(conditions);
				}
				updateTrunkLeafSql.append(") as t_wrapLeaf ");
				updateTrunkLeafSql.append(" on ");
				updateTrunkLeafSql.append(idField).append("=t_wrapLeaf.").append(pidField);
				updateTrunkLeafSql.append(" set ");
				updateTrunkLeafSql.append(leafField).append("=0");
				if (unifyColumns != null) {
					for (String unifyColumn : unifyColumns) {
						updateTrunkLeafSql.append(",").append(unifyColumn).append("=?");
					}
				}
				if (StringUtil.isNotBlank(conditions)) {
					updateTrunkLeafSql.append(" where ").append(conditions);
				}
			} else {
				// update organ_info set IS_LEAF=0
				// where organ_id in (select organ_pid from organ_info)
				updateTrunkLeafSql.append(" set ");
				updateTrunkLeafSql.append(leafField).append("=0");
				if (unifyColumns != null) {
					for (String unifyColumn : unifyColumns) {
						updateTrunkLeafSql.append(",").append(unifyColumn).append("=?");
					}
				}
				updateTrunkLeafSql.append(" where ").append(idField);
				updateTrunkLeafSql.append(" in (select ").append(pidField);
				updateTrunkLeafSql.append(" from ").append(tableName);
				if (StringUtil.isNotBlank(conditions)) {
					updateTrunkLeafSql.append(" where ").append(conditions);
				}
				updateTrunkLeafSql.append(") ");
				if (StringUtil.isNotBlank(conditions)) {
					updateTrunkLeafSql.append(" and ").append(conditions);
				}
			}
			executeSql(typeHandler, updateTrunkLeafSql.toString(), unifyValues, null, conn, dbType, null, false);
		}
		return true;
	}

	/**
	 * TreeTableRoute中处理下一层级的递归方法，逐层计算下一级节点的节点层次和路径
	 * 
	 * @param typeHandler         自定义类型处理器，非null时优先通过其完成参数设置
	 * @param updateLevelAndRoute 更新节点层级和路径的sql语句
	 * @param nextNodeQueryStr    查询下一层级节点的sql语句(含${inStr}占位)
	 * @param treeTableModel      树形表模型
	 * @param pidsMap             父节点id与父节点路径的映射
	 * @param ids                 下一层级待处理的节点数据(id、路径、pid)
	 * @param nodeLevel           当前处理的节点层级
	 * @param conn                数据库连接对象
	 * @param dbType              数据库类型，参见DataSourceUtils.DBType
	 * @param queryTimeout        查询超时时间(秒)
	 * @param unifyValues         公共更新字段对应的参数值数组，null时不绑定
	 * @throws Exception
	 */
	private static void processNextLevel(TypeHandler typeHandler, final String updateLevelAndRoute,
			final String nextNodeQueryStr, final TreeTableModel treeTableModel, final HashMap pidsMap, List ids,
			final int nodeLevel, Connection conn, final int dbType, final Integer queryTimeout,
			final Object[] unifyValues) throws Exception {
		// 修改节点level和节点路径
		batchUpdateByJdbc(typeHandler, updateLevelAndRoute, ids, 500, new InsertRowCallbackHandler() {
			@Override
			public void process(PreparedStatement pst, int index, Object rowData) throws SQLException {
				String id = ((List) rowData).get(0).toString();
				// 获得父节点id和父节点路径
				String pid = ((List) rowData).get(2).toString();
				String nodeRoute = (String) pidsMap.get(pid);
				int size = treeTableModel.getIdLength();
				if (nodeRoute == null || "".equals(nodeRoute.trim())) {
					nodeRoute = "";
					if (!treeTableModel.isChar() || treeTableModel.isAppendZero()) {
						// 负数
						if (NumberUtil.isInteger(pid) && pid.indexOf("-") == 0) {
							nodeRoute = nodeRoute.concat("-")
									.concat(StringUtil.addLeftZero2Len(pid.substring(1), size - 1));
						} else {
							nodeRoute = nodeRoute.concat(StringUtil.addLeftZero2Len(pid, size));
						}
					} else {
						nodeRoute = nodeRoute.concat(StringUtil.addRightBlank2Len(pid, size));
					}
				} else {
					nodeRoute = nodeRoute.trim();
				}
				// update 2018-1-9 增加判断是否以逗号结尾,解决修改过程中出现双逗号问题
				if (!nodeRoute.endsWith(treeTableModel.getSplitSign())) {
					nodeRoute = nodeRoute.concat(treeTableModel.getSplitSign());
				}
				// 回置节点的nodeRoute值
				if (!treeTableModel.isChar() || treeTableModel.isAppendZero()) {
					nodeRoute = nodeRoute.concat(StringUtil.addLeftZero2Len(id, size));
				} else {
					nodeRoute = nodeRoute.concat(StringUtil.addRightBlank2Len(id, size));
				}
				((List) rowData).set(1, nodeRoute);
				// 节点等级
				pst.setInt(1, nodeLevel);
				// 节点路径(当节点路径长度不做补充统一长度操作,则末尾自动加上一个分割符)
				pst.setString(2, nodeRoute + ((size < 2) ? treeTableModel.getSplitSign() : ""));
				// 公共更新字段参数(位置在level、route之后)
				int unifySize = (unifyValues == null) ? 0 : unifyValues.length;
				if (unifyValues != null) {
					for (int i = 0; i < unifySize; i++) {
						pst.setObject(3 + i, unifyValues[i]);
					}
				}
				// id参数位置在公共更新字段之后
				if (treeTableModel.isChar()) {
					pst.setString(3 + unifySize, id);
				} else {
					pst.setLong(3 + unifySize, Long.parseLong(id));
				}
			}
		}, null, null, conn, dbType);
		// 处理节点的下一层次
		int size = ids.size();
		int fromIndex = 0;
		int toIndex = -1;
		// 避免in()中的参数过多，每次500个
		String inStrs;
		List subIds = null;
		List nextIds = null;
		boolean exist = false;
		while (toIndex < size) {
			fromIndex = toIndex + 1;
			toIndex += 500;
			if (toIndex >= size - 1) {
				toIndex = size - 1;
				exist = true;
			}
			if (fromIndex >= toIndex) {
				subIds = new ArrayList();
				subIds.add(ids.get(toIndex));
			} else {
				subIds = ids.subList(fromIndex, toIndex + 1);
			}
			inStrs = combineQueryInStr(subIds, 0, null, treeTableModel.isChar());
			// 获取下一层节点
			nextIds = findByJdbcQuery(typeHandler, nextNodeQueryStr.replaceFirst("\\$\\{inStr\\}", inStrs), null, null,
					null, null, conn, dbType, false, null, SqlToyConstants.FETCH_SIZE, -1, queryTimeout);
			// 递归处理下一层
			if (nextIds != null && !nextIds.isEmpty()) {
				processNextLevel(typeHandler, updateLevelAndRoute, nextNodeQueryStr, treeTableModel,
						CollectionUtil.hashList(subIds, 0, 1, true), nextIds, nodeLevel + 1, conn, dbType, queryTimeout,
						unifyValues);
			}
			if (exist) {
				break;
			}
		}
	}

	/**
	 * sql文件自动创建到数据库
	 * 
	 * @param conn       数据库连接对象
	 * @param sqlContent sql文件内容，支持以分隔符号分割的多条语句
	 * @param batchSize  批处理提交大小，null或小于等于1时默认为100
	 * @param autoCommit 是否自动提交，null时保持连接原有提交方式，执行完毕后恢复
	 * @throws Exception
	 */
	public static void executeBatchSql(Connection conn, String sqlContent, Integer batchSize, Boolean autoCommit)
			throws Exception {
		String splitSign = DataSourceUtils.getDatabaseSqlSplitSign(conn);
		// 剔除sql中的注释
		sqlContent = SqlUtil.clearMark(sqlContent);
		if (DataSourceUtils.SQLSERVER_SPLIT_SIGN.equals(splitSign)) {
			sqlContent = clearMistyChars(sqlContent, BLANK);
		}
		// 分割成多个子语句
		String[] statments = StringUtil.splitExcludeSymMark(sqlContent, splitSign, sqlCommentfilters);
		boolean hasSetAutoCommit = false;
		// 是否自动提交
		if (autoCommit != null && autoCommit.booleanValue() != conn.getAutoCommit()) {
			conn.setAutoCommit(autoCommit.booleanValue());
			hasSetAutoCommit = true;
		}
		Statement stat = null;
		try {
			stat = conn.createStatement();
			// 设置全局statementTimeout，默认为null
			if (SqlToyConstants.defaultStatementTimeout != null && SqlToyConstants.defaultStatementTimeout > 0) {
				stat.setQueryTimeout(SqlToyConstants.defaultStatementTimeout);
			}
			int meter = 0;
			// int realBatch = (batchSize == null || batchSize.intValue() > 1) ?
			// batchSize.intValue() : 100;
			int realBatch = (batchSize == null || batchSize.intValue() <= 1) ? 100 : batchSize.intValue();
			int totalRows = statments.length;
			int i = 0;
			for (String sql : statments) {
				if (StringUtil.isNotBlank(sql)) {
					meter++;
					logger.debug("the sql being batch executed:{}", sql);
					stat.addBatch(sql);
				}
				if (meter > 0 && ((meter % realBatch) == 0 || i + 1 == totalRows)) {
					stat.executeBatch();
					stat.clearBatch();
				}
				i++;
			}
		} catch (SQLException e) {
			// 已切换为手动提交的场景失败必须回滚,避免悬挂事务被后续连接复用者意外提交
			if (hasSetAutoCommit && !autoCommit.booleanValue()) {
				try {
					conn.rollback();
				} catch (SQLException re) {
					logger.error("executeBatchSql rollback failed!", re);
				}
			}
			throw e;
		} finally {
			try {
				if (stat != null) {
					stat.close();
					stat = null;
				}
			} catch (SQLException se) {
				logger.error(se.getMessage(), se);
			}
			// 恢复conn原始autoCommit默认值
			if (hasSetAutoCommit) {
				try {
					conn.setAutoCommit(!autoCommit);
				} catch (SQLException se) {
					logger.error("executeBatchSql failed to restore autoCommit!", se);
				}
			}
		}
	}

	/**
	 * 判断sql语句中是否有order by排序
	 * 
	 * @param sql         sql语句
	 * @param judgeUpcase true时检查ORder大写标记(代表分页时是否外层包裹)，存在则视为无order by
	 * @return 最外层存在order by返回true，否则返回false
	 */
	public static boolean hasOrderBy(String sql, boolean judgeUpcase) {
		// 字面量掩码串与原串等长:order by与收括号定位在掩码串上进行,
		// 规避字面量内的)/order by被误判为最外层排序
		String maskedSql = SqlConfigParseUtils.maskLiterals(sql, false);
		// 最后的收括号位置
		int lastBracketIndex = maskedSql.lastIndexOf(")");
		boolean result = false;
		int orderByIndex = StringUtil.matchLastIndex(maskedSql, ORDER_BY_PATTERN, 1);
		// 存在order by
		if (orderByIndex > lastBracketIndex) {
			result = true;
		}
		// 特殊处理 order by，通过ORder这种非常规写法代表分页时是否进行外层包裹(建议废弃使用)
		if (judgeUpcase) {
			int upcaseOrderBy = StringUtil.matchLastIndex(maskedSql, UPCASE_ORDER_PATTERN, 1);
			if (upcaseOrderBy > lastBracketIndex) {
				result = false;
			}
		}
		return result;
	}

	/**
	 * clob转换成字符串
	 *
	 * @param clob java.sql.Clob对象，null返回null
	 * @return clob对应的字符串内容，读取失败返回null
	 */
	public static String clobToString(Clob clob) {
		if (clob == null) {
			return null;
		}
		// 8K
		StringBuilder sb = new StringBuilder(1024 * 8);
		char[] buf = new char[1024];
		int len;
		try (Reader r = clob.getCharacterStream()) {
			while ((len = r.read(buf)) != -1) {
				sb.append(buf, 0, len);
			}
		} catch (Exception e) {
			logger.error("failed to read the Clob: {}", e.getMessage(), e);
			return null;
		}
		return sb.toString();
	}

	/**
	 * update 2026-9-10 truncate语句的方言适配:db2的TRUNCATE TABLE必须携带IMMEDIATE关键字 (裸形态报-104
	 * END-OF-STATEMENT,12.1.2/12.1.5双版本实测);sqlite无TRUNCATE语法, 改写为delete
	 * from等价语义(全表删除)。仅处理truncate前缀语句,其余语句原样返回(前缀
	 * 判断,常规语句零开销);覆盖lightDao.truncate/TableApi.truncate/用户executeSql全部入口
	 *
	 * @param sql    待执行语句
	 * @param dbType 数据库类型
	 * @return 适配目标库的truncate形态
	 */
	static String adaptTruncateSql(String sql, Integer dbType) {
		if (sql == null || dbType == null) {
			return sql;
		}
		String trimmed = sql.trim();
		if (!trimmed.toLowerCase(Locale.ROOT).startsWith("truncate")) {
			return sql;
		}
		if (dbType.intValue() == DBType.DB2 && !trimmed.toLowerCase(Locale.ROOT).endsWith("immediate")) {
			return trimmed.concat(" immediate");
		}
		if (dbType.intValue() == DBType.SQLITE) {
			return trimmed.replaceFirst("(?i)^truncate\\s+table\\s+", "delete from ");
		}
		return sql;
	}

	/**
	 * 执行Sql语句完成修改操作
	 *
	 * @param typeHandler 自定义类型处理器，非null时优先通过其完成参数设置
	 * @param executeSql  增删改sql语句
	 * @param params      sql中?对应的参数值数组
	 * @param paramsType  参数对应的java.sql.Types类型数组，null时按参数值自动判断类型
	 * @param conn        数据库连接对象
	 * @param dbType      数据库类型，参见DataSourceUtils.DBType
	 * @param autoCommit  是否自动提交，null时保持连接原有提交方式，执行完毕后恢复
	 * @param processWord true对sql中的关键词做保留字转义处理
	 * @return 实际影响的记录行数
	 * @throws Exception
	 */
	public static Long executeSql(TypeHandler typeHandler, final String executeSql, final Object[] params,
			final Integer[] paramsType, final Connection conn, final Integer dbType, final Boolean autoCommit,
			boolean processWord) throws Exception {
		// 对sql进行关键词符号替换
		String realSql = processWord ? ReservedWordsUtil.convertSql(executeSql, dbType) : executeSql;
		// update 2026-9-10 truncate语句方言适配(db2须IMMEDIATE/sqlite无truncate语法)
		realSql = adaptTruncateSql(realSql, dbType);
		SqlExecuteStat.showSql("execute sql=", realSql, params);
		boolean hasSetAutoCommit = false;
		Long updateCounts = null;
		if (autoCommit != null) {
			if (!autoCommit == conn.getAutoCommit()) {
				conn.setAutoCommit(autoCommit);
				hasSetAutoCommit = true;
			}
		}
		PreparedStatement pst = conn.prepareStatement(realSql);
		// 设置全局statementTimeout，默认为null
		if (SqlToyConstants.defaultStatementTimeout != null && SqlToyConstants.defaultStatementTimeout > 0) {
			pst.setQueryTimeout(SqlToyConstants.defaultStatementTimeout);
		}
		Object result = preparedStatementProcess(null, pst, null, new PreparedStatementResultHandler() {
			@Override
			public void execute(Object obj, PreparedStatement pst, ResultSet rs) throws SQLException, IOException {
				// update 2026-9-5 移除按paramsType==TIMESTAMP跳过绑定的sqlserver分支:
				// rowversion的排除已上收到语句生成与调用方参数过滤(目标库元数据校准判据),
				// 语句占位符与参数严格1:1,此层按类型跳过反而造成占位符缺参错位
				setParamsValue(typeHandler, conn, dbType, pst, params, paramsType, 0);
				// 返回update的记录数量
				// update 2026-9-10 改用executeUpdate返回值(JDBC规范即影响行数):sqlite-jdbc
				// 的getUpdateCount()恒返回0(驱动实现缺陷,update/delete/executeSql计数全部
				// 失真为0,实测),executeUpdate返回值各驱动均正确
				this.setResult(Long.valueOf(pst.executeUpdate()));
			}
		});
		if (result != null) {
			updateCounts = (Long) result;
		}
		if (hasSetAutoCommit && autoCommit != null) {
			conn.setAutoCommit(!autoCommit);
		}
		return updateCounts;
	}

	public static Object insertReturnPrimaryKey(TypeHandler typeHandler, final String executeSql, final Object[] params,
			final Integer[] paramsType, final String primaryField, final Connection conn, final Integer dbType,
			final Boolean autoCommit, boolean processWord) throws Exception {
		// 对sql进行关键词符号替换
		String realSql = processWord ? ReservedWordsUtil.convertSql(executeSql, dbType) : executeSql;
		SqlExecuteStat.showSql("execute sql=", realSql, params);
		boolean hasSetAutoCommit = false;
		if (autoCommit != null) {
			if (!autoCommit == conn.getAutoCommit()) {
				conn.setAutoCommit(autoCommit);
				hasSetAutoCommit = true;
			}
		}
		PreparedStatement pst = conn.prepareStatement(realSql,
				new String[] { DataSourceUtils.getReturnPrimaryKeyColumn(primaryField, dbType) });
		// 设置全局statementTimeout，默认为null
		if (SqlToyConstants.defaultStatementTimeout != null && SqlToyConstants.defaultStatementTimeout > 0) {
			pst.setQueryTimeout(SqlToyConstants.defaultStatementTimeout);
		}
		Object result = preparedStatementProcess(null, pst, null, new PreparedStatementResultHandler() {
			@Override
			public void execute(Object obj, PreparedStatement pst, ResultSet rs) throws SQLException, IOException {
				// update 2026-9-5 移除按paramsType==TIMESTAMP跳过绑定的sqlserver分支(同executeSql,
				// rowversion排除已上收到语句生成与调用方参数过滤,占位符与参数严格1:1)
				setParamsValue(typeHandler, conn, dbType, pst, params, paramsType, 0);
				pst.execute();
				ResultSet keyResult = pst.getGeneratedKeys();
				if (keyResult != null) {
					while (keyResult.next()) {
						this.setResult(keyResult.getObject(1));
					}
					keyResult.close();
				}
				// 返回update的记录数量
				SqlExecuteStat.debug("execution result", "insertReturnPrimaryKey affected rows: {}!",
						Long.valueOf(pst.getUpdateCount()));
			}
		});
		if (hasSetAutoCommit && autoCommit != null) {
			conn.setAutoCommit(!autoCommit);
		}
		return result;
	}

	/**
	 * 转换主键数据类型(主键生成只支持数字和字符串类型)
	 * 
	 * @param idValue 原始主键值，null返回null
	 * @param idType  目标类型全名或简称，如java.lang.String、long、int等；空白时原值返回
	 * @return 转换后的主键值，类型无法识别时原值返回
	 */
	public static Object convertIdValueType(Object idValue, String idType) {
		if (idValue == null) {
			return null;
		}
		if (StringUtil.isBlank(idType)) {
			return idValue;
		}
		// 按照优先顺序对比
		if ("java.lang.string".equals(idType)) {
			return idValue.toString();
		}
		if ("java.lang.integer".equals(idType)) {
			return Integer.valueOf(idValue.toString());
		}
		if ("java.lang.long".equals(idType)) {
			return Long.valueOf(idValue.toString());
		}
		if ("java.math.biginteger".equals(idType)) {
			return new BigInteger(idValue.toString());
		}
		if ("java.math.bigdecimal".equals(idType)) {
			return new BigDecimal(idValue.toString());
		}
		if ("long".equals(idType)) {
			return Long.valueOf(idValue.toString()).longValue();
		}
		if ("int".equals(idType)) {
			return Integer.valueOf(idValue.toString()).intValue();
		}
		if ("java.lang.short".equals(idType)) {
			return Short.valueOf(idValue.toString());
		}
		if ("short".equals(idType)) {
			return Short.valueOf(idValue.toString()).shortValue();
		}
		return idValue;
	}

	/**
	 * 判断是否内包含union 查询(转义约定经DBProfile/运行上下文自动解析, 配置解析期等已知方言的场景请用带backslashEscape的重载)
	 * 
	 * @param sql            sql语句
	 * @param clearMistyChar true剔除子查询和括号内容后再判断(只判断最外层)，false直接匹配union关键词
	 * @return true表示存在union查询
	 */
	public static boolean hasUnion(String sql, boolean clearMistyChar) {
		// update 2026-9-10 未显式给定转义约定时统一经DBProfile(真实连接档案)/运行上下文解析
		return hasUnion(sql, clearMistyChar, SqlConfigParseUtils.isBackslashEscape(null));
	}

	/**
	 * 判断是否内包含union 查询,即是否是select * from (select * from t union select * from t2 )
	 * 形式的查询,将所有()剔除后判定是否有union 存在
	 * 字面量内容不参与判定:先将'...'字面量内部掩为等长空白,规避字面量内的union、'('、')'误报或干扰括号剔除
	 * 
	 * @param sql             sql语句
	 * @param clearMistyChar  true剔除子查询和括号内容后再判断(只判断最外层)，false直接匹配union关键词
	 * @param backslashEscape true时字面量内\'不终结字面量(mysql系),掩码须与方言一致否则\'后错位
	 * @return true表示存在union查询
	 */
	public static boolean hasUnion(String sql, boolean clearMistyChar, boolean backslashEscape) {
		if (!StringUtil.matches(sql, UNION_PATTERN)) {
			return false;
		}
		// 存在with as ，先剔除(hasWith基于字面量掩码串判定,字面量内的with xx as (不触发剔除)
		if (SqlConfigParseUtils.hasWith(sql, backslashEscape)) {
			SqlWithAnalysis sqlWith = new SqlWithAnalysis(sql);
			sql = sqlWith.getRejectWithSql();
		}
		String tmpSql = BLANK + (clearMistyChar ? clearMistyChars(sql, BLANK) : sql);
		// 字面量内容掩为等长空白,掩码串与原串等长,位置偏移不受影响
		tmpSql = SqlConfigParseUtils.maskLiterals(tmpSql, backslashEscape);
		StringBuilder lastSql = new StringBuilder(tmpSql);
		// 找到第一个select 所对称的from位置，排查掉子查询中的内容
		int fromIndex = StringUtil.getSymMarkMatchIndex(SELECT_REGEX, FROM_REGEX, tmpSql.toLowerCase(Locale.ROOT), 0);
		if (fromIndex != -1) {
			lastSql.delete(0, fromIndex);
		}
		// 删除所有对称的括号中的内容
		int start = lastSql.indexOf("(");
		int symMarkEnd;
		while (start != -1) {
			symMarkEnd = StringUtil.getSymMarkIndex("(", ")", lastSql.toString(), start);
			if (symMarkEnd != -1) {
				lastSql.delete(start, symMarkEnd + 1);
				start = lastSql.indexOf("(");
			} else {
				break;
			}
		}
		if (StringUtil.matches(lastSql.toString(), UNION_PATTERN)) {
			return true;
		}
		return false;
	}

	/**
	 * 转化对象字段名称为数据库字段名称
	 * 
	 * @param entityMeta 实体对象元数据(含属性与数据库列名对照)
	 * @param sql        含对象属性名称的sql语句
	 * @return 属性名称替换为数据库列名后的sql语句，sql空白时原样返回
	 */
	public static String convertFieldsToColumns(EntityMeta entityMeta, String sql) {
		if (StringUtil.isBlank(sql)) {
			return sql;
		}
		String key = entityMeta.getTableName() + "_" + sql;
		// 从缓存中直接获取,避免每次都处理提升效率
		// update 2026-9-9 单次get判空替代containsKey+get双查找
		String cachedSql = convertSqlMap.get(key);
		if (cachedSql != null) {
			return cachedSql;
		}
		String[] fields = entityMeta.getFieldsArray(false);
		StringBuilder sqlBuff = new StringBuilder();
		// 末尾补齐一位空白,便于后续取index时避免越界
		String realSql = sql.concat(BLANK);
		int start = 0;
		int index;
		String preSql;
		String columnName;
		char preChar, tailChar;
		String varSql;
		boolean isBlank;
		// 转换sql中的对应 vo属性为具体表字段
		for (String field : fields) {
			columnName = entityMeta.getColumnName(field);
			// 对象属性和(表字段一致且非关键词),无需处理
			if (columnName != null
					&& (!columnName.equalsIgnoreCase(field) || ReservedWordsUtil.isKeyWord(columnName))) {
				start = 0;
				// 定位匹配到field,判断匹配的前一位和后一位字符,前一位是:的属于条件,且都不能是字符和数字以及下划线
				index = StringUtil.indexOfIgnoreCase(realSql, field, start);
				while (index != -1) {
					preSql = realSql.substring(start, index);
					isBlank = false;
					if (StringUtil.matches(preSql, "\\s$")) {
						isBlank = true;
					}
					varSql = preSql.trim();
					// 首位字符不是数字(48~57)、(A-Z|a-z)字母(65~90,97~122)、下划线(95)、冒号(58)
					if (!"".equals(varSql)) {
						preChar = varSql.charAt(varSql.length() - 1);
					} else {
						preChar = ' ';
					}
					tailChar = realSql.charAt(index + field.length());
					// 非条件参数(58为冒号),结尾字符不能是数字、字母和(
					if (((isBlank && preChar != 58) || (preChar > 58 && preChar < 65)
							|| (preChar > 90 && preChar < 97 && preChar != 95) || preChar < 48 || preChar > 122)
							&& ((tailChar > 58 && tailChar < 65) || (tailChar > 90 && tailChar < 97 && tailChar != 95)
									|| (tailChar < 48 && tailChar != 40) || tailChar > 122)) {
						// 含关键词处理
						if (preSql.endsWith("[") || preSql.endsWith("`") || preSql.endsWith("\"")) {
							sqlBuff.append(preSql).append(columnName);
						} else {
							sqlBuff.append(preSql).append(ReservedWordsUtil.convertWord(columnName, null));
						}
						start = index + field.length();
					}
					index = StringUtil.indexOfIgnoreCase(realSql, field, index + field.length());
				}
				if (start > 0) {
					sqlBuff.append(realSql.substring(start));
					realSql = sqlBuff.toString();
					sqlBuff.delete(0, sqlBuff.length());
				}
			}
		}
		// 放入缓存(key含动态拼接的sql条件,设置容量上限防止无界增长,超限后不再缓存直接计算)
		if (convertSqlMap.size() < CONVERT_SQL_CACHE_MAX_SIZE) {
			convertSqlMap.put(key, realSql);
		}
		return realSql;
	}

	/**
	 * 组合动态条件
	 * 
	 * @param entityMeta 实体对象元数据
	 * @return 形如" 1=1 #[and col=:field]..."的动态条件串(未赋值的条件执行时自动剔除)
	 */
	public static String wrapWhere(EntityMeta entityMeta) {
		String[] fields = entityMeta.getFieldsArray(false);
		StringBuilder sqlBuff = new StringBuilder(" 1=1 ");
		String columnName;
		for (String field : fields) {
			columnName = ReservedWordsUtil.convertWord(entityMeta.getColumnName(field), null);
			sqlBuff.append("#[and ").append(columnName).append("=:").append(field).append("]");
		}
		return sqlBuff.toString();
	}

	/**
	 * 针对对象查询补全sql中的select * from table 部分,适度让代码中的sql简短一些(并不推荐)
	 * 
	 * @param sqlToyContext sqltoy上下文，用于获取实体元数据
	 * @param entityClass   实体对象类型
	 * @param sql           简写形式的sql语句(如from table、where、and xxx等)
	 * @return 补全后的完整sql语句，已含select/with/call开头或非实体类型时原样返回
	 */
	public static String completionSql(SqlToyContext sqlToyContext, Class entityClass, String sql) {
		if (null == entityClass || SqlConfigParseUtils.isNamedQuery(sql)) {
			return sql;
		}
		String sqlLow = sql.toLowerCase(Locale.ROOT).trim();
		// 包含了select 或with as、show、desc 模式开头直接返回
		if (StringUtil.matches(sqlLow, "^(select|with|show|desc)\\W")) {
			return sql;
		}
		// 存储过程模式直接返回
		if (StringUtil.matches(sqlLow, "^\\{?\\W*call\\W+")) {
			return sql;
		}
		// 非entity实体类型
		if (!sqlToyContext.isEntity(entityClass)) {
			// from 开头补齐select *
			if (StringUtil.matches(sqlLow, "^from\\W")) {
				return "select * ".concat(sql);
			}
			return sql;
		}
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entityClass);
		// from 开头补齐select col1,col2,...
		if (StringUtil.matches(sqlLow, "^from\\W")) {
			return "select ".concat(entityMeta.getAllColumnNames()).concat(BLANK).concat(sql);
		}
		// 没有where和from(排除 select * from table),补齐select * from table where
		if (!StringUtil.matches(BLANK.concat(sqlLow), "\\W(from|where)\\W")) {
			if (StringUtil.matches(sqlLow, "^(and|or)\\W")) {
				return "select ".concat(entityMeta.getAllColumnNames()).concat(" from ")
						.concat(entityMeta.getSchemaTable(null, null)).concat(" where 1=1 ").concat(sql);
			}
			return "select ".concat(entityMeta.getAllColumnNames()).concat(" from ")
					.concat(entityMeta.getSchemaTable(null, null)).concat(" where ").concat(sql);
		}
		// where开头 补齐select * from
		if (StringUtil.matches(sqlLow, "^where\\W")) {
			return "select ".concat(entityMeta.getAllColumnNames()).concat(" from ")
					.concat(entityMeta.getSchemaTable(null, null)).concat(BLANK).concat(sql);
		}
		return sql;
	}

	/**
	 * 判断sql中是否存在lock锁
	 * 
	 * @param sql    sql语句，null返回false
	 * @param dbType 数据库类型，sqlserver需判断with(rowlock)等形式，参见DataSourceUtils.DBType
	 * @return true表示存在for update或对应的锁标记
	 */
	public static boolean hasLock(String sql, Integer dbType) {
		if (sql == null) {
			return false;
		}
		// 字面量内容不参与判定:规避字面量内的for update/with(rowlock...)文本误判为已存在锁而跳过加锁
		String maskedSql = SqlConfigParseUtils.maskLiterals(sql, false);
		if (StringUtil.matches(maskedSql, "(?i)\\s+for\\s+update")) {
			return true;
		}
		// sqlserver
		if (dbType != null && dbType.intValue() == DBType.SQLSERVER) {
			if (StringUtil.matches(maskedSql,
					"(?i)with\\s*\\(\\s*(rowlock|xlock|updlock|holdlock|nolock|readpast)?\\,?\\s*(rowlock|xlock|updlock|holdlock|nolock|readpast)\\s*\\)")) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 处理sqlserver default值为((value))问题
	 * 
	 * @param defaultValue 数据库元数据中的字段默认值表达式
	 * @return 去除外层括号、引号和::text等类型转换后的默认值，NULL::类型转换返回null，null输入返回null
	 */
	public static String clearDefaultValue(String defaultValue) {
		if (defaultValue == null) {
			return null;
		}
		// 按原默认值返回
		if ("".equals(defaultValue.trim())) {
			return defaultValue;
		}
		String result = defaultValue.trim();
		if (result.toUpperCase(Locale.ROOT).startsWith("NULL::") && StringUtil.matches(result, PG_CAST_PATTERN)) {
			return null;
		}
		// 先去除最外层的::text等,比如(x)::text
		result = PG_CAST_PATTERN.matcher(result).replaceAll("").trim();
		// 再去除()、(())
		String[][] wrappers = { { "((", "))" }, { "(", ")" }, { "'", "'" }, { "\"", "\"" } };
		for (String[] wrap : wrappers) {
			String pre = wrap[0];
			String suf = wrap[1];
			if (result.startsWith(pre) && result.endsWith(suf)) {
				result = result.substring(pre.length(), result.length() - suf.length()).trim();
				break;
			}
		}
		// 再去除::text、::double precision、::char(10)、::numeric(10,2)结尾内容
		result = PG_CAST_PATTERN.matcher(result).replaceAll("").trim();
		return result;
	}

	/**
	 * 替换换行、回车、tab符号;\r 换行、\t tab符合、\n 回车
	 * 
	 * @param source 原始字符串，null返回null
	 * @param target 用于替换的字符或字符串
	 * @return 替换后的字符串(回车换行前后空白一并剔除)
	 */
	public static String clearMistyChars(String source, String target) {
		if (source == null) {
			return null;
		}
		// 回车换行前后的空白也剔除
		return source.replaceAll("\\s*(\r|\n)\\s*", target).replaceAll("\t", target);
	}

	/**
	 * 获取数据库时间字符串
	 * 
	 * @param dbType              数据库类型，参见DataSourceUtils.DBType
	 * @param fieldMeta           字段元数据(含字段名称和类型)
	 * @param createSqlTimeFields 需要通过数据库时间填充的字段名称集合
	 * @return 对应数据库的当前时间函数表达式(如current_timestamp)，字段不在集合内或非时间类型返回null
	 */
	public static String getDBTime(Integer dbType, FieldMeta fieldMeta, IgnoreCaseSet createSqlTimeFields) {
		if (fieldMeta == null || createSqlTimeFields == null || createSqlTimeFields.isEmpty()) {
			return null;
		}
		int fieldType = fieldMeta.getType();
		// 统一需要处理的字段、且是日期、时间类型
		if (createSqlTimeFields.contains(fieldMeta.getFieldName()) && (fieldType == java.sql.Types.DATE
				|| fieldType == java.sql.Types.TIME || fieldType == java.sql.Types.TIME_WITH_TIMEZONE
				|| fieldType == java.sql.Types.TIMESTAMP || fieldType == java.sql.Types.TIMESTAMP_WITH_TIMEZONE)) {
			// 只支持now
			if (dbType == DBType.CLICKHOUSE) {
				return "now()";
			}
			// time
			if (fieldType == java.sql.Types.TIME || fieldType == java.sql.Types.TIME_WITH_TIMEZONE
					|| "java.time.localtime".equals(fieldMeta.getFieldType())
					|| "java.sql.time".equals(fieldMeta.getFieldType())) {
				if (dbType == DBType.MYSQL || dbType == DBType.MYSQL57 || dbType == DBType.TIDB
						|| dbType == DBType.SQLITE || dbType == DBType.H2 || dbType == DBType.POSTGRESQL
						|| dbType == DBType.POSTGRESQL14 || dbType == DBType.KINGBASE || dbType == DBType.DB2
						|| dbType == DBType.OCEANBASE || dbType == DBType.DORIS || dbType == DBType.STARROCKS) {
					return "current_time";
				} else if (dbType == DBType.GAUSSDB || dbType == DBType.OPENGAUSS || dbType == DBType.MOGDB
						|| dbType == DBType.VASTBASE || dbType == DBType.STARDB || dbType == DBType.OSCAR) {
					return "now()";
				} else if (dbType == DBType.SQLSERVER) {
					return "getdate()";
				} else {
					return "current_timestamp";
				}
			} // timestamp
			else if ("java.time.localdate".equals(fieldMeta.getFieldType())) {
				if (dbType == DBType.SQLSERVER) {
					return "getdate()";
				}
				return "current_date";
			} else {
				return "current_timestamp";
			}
		}
		return null;
	}

	/**
	 * 验证sql in的参数,要么是''形式的字符，要么是数字
	 * 
	 * @param argValue 待验证的in参数内容(如'1','2'或1,2)
	 * @return true表示格式合法可直接拼入in子句，单个数字等不合规场景返回false(回退参数化)
	 */
	public static boolean validateInArg(String argValue) {
		String argTrim = argValue.replaceAll("\\s+", "");
		String[] args = null;
		// 判断是否有逗号分割
		if (argTrim.indexOf(",") != -1) {
			// 以逗号开始或结束不符合in的写法
			if (argTrim.startsWith(",") || argTrim.endsWith(",")) {
				return false;
			}
			// 分割成数组进行检查
			args = argTrim.split("\\,");
		} else {
			// 单个字符串组成单一数组，形成统一的检查格式
			args = new String[] { argTrim };
		}
		// 1：char;2:string;3:数字
		int argType = 3;
		if (args[0].startsWith("'") && args[0].endsWith("'")) {
			argType = 1;
		} else if (args[0].startsWith("\"") && args[0].endsWith("\"")) {
			argType = 2;
		}
		// 无逗号分隔符，且是数子 不能直接in (123) 输出，返回false，依旧以pst.setString(index,"123")设置条件值
		if (argType == 3 && args.length == 1) {
			return false;
		}
		for (String item : args) {
			if (argType == 1) {
				if (!item.startsWith("'") || !item.endsWith("'")) {
					return false;
				}
				// 引号包裹项必须恰好一对引号且不含反斜杠:多于一对引号意味着值内拼接了额外字面量
				// ('a' or sleep(5)--'形式),反斜杠在MySQL等方言会转义收尾引号提前闭合字面量,一律回退参数化
				if (item.indexOf('\\') != -1 || StringUtil.matchCnt(item, ONE_QUOTA, 0) != 2) {
					return false;
				}
			} else if (argType == 2) {
				if (!item.startsWith("\"") || !item.endsWith("\"")) {
					return false;
				}
				// 双引号同理,\"转义可提前闭合(MySQL ANSI_QUOTES等场景),多余引号即拼接,回退参数化
				if (item.indexOf('\\') != -1 || StringUtil.matchCnt(item, DOUBLE_QUOTA, 0) != 2) {
					return false;
				}
			} else if (!NumberUtil.isNumber(item)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * 将参数值转成字符传
	 * 
	 * @param sqlArgValue        待转换的参数值，null返回"null"
	 * @param addSingleQuotation 是否加单引号
	 * @return 参数值对应的字符串形式(字符和日期时间类型按需加单引号，数组集合转逗号分隔)
	 */
	public static String toSqlString(Object sqlArgValue, boolean addSingleQuotation) {
		if (sqlArgValue == null) {
			return "null";
		}
		// 参数前面是否是条件比较符号，如果是比较符号针对日期、字符串加单引号
		String sign = addSingleQuotation ? "'" : "";
		String valueStr;
		int nanoValue;
		String timeStr;
		Object paramValue;
		if (sqlArgValue instanceof Enum) {
			paramValue = BeanUtil.getEnumValue(sqlArgValue);
		} else {
			paramValue = sqlArgValue;
		}
		if (paramValue instanceof CharSequence) {
			valueStr = sign + paramValue + sign;
		} else if (paramValue instanceof Timestamp) {
			valueStr = sign + DateUtil.formatDate(paramValue, "yyyy-MM-dd HH:mm:ss.SSS") + sign;
		} else if (paramValue instanceof LocalDateTime) {
			nanoValue = ((LocalDateTime) paramValue).getNano();
			if (nanoValue > 0) {
				if (SqlToyConstants.localDateTimeFormat != null
						&& !SqlToyConstants.localDateTimeFormat.equals("auto")) {
					timeStr = DateUtil.formatDate(paramValue, SqlToyConstants.localDateTimeFormat);
				} else {
					timeStr = DateUtil.formatDate(paramValue, "yyyy-MM-dd HH:mm:ss") + DateUtil.processNano(nanoValue);
				}
			} else {
				timeStr = DateUtil.formatDate(paramValue, "yyyy-MM-dd HH:mm:ss");
			}
			valueStr = sign + timeStr + sign;
		} else if (paramValue instanceof LocalDate) {
			valueStr = sign + DateUtil.formatDate(paramValue, "yyyy-MM-dd") + sign;
		} else if (paramValue instanceof LocalTime) {
			nanoValue = ((LocalTime) paramValue).getNano();
			if (nanoValue > 0) {
				if (SqlToyConstants.localTimeFormat != null && !SqlToyConstants.localTimeFormat.equals("auto")) {
					timeStr = DateUtil.formatDate(paramValue, SqlToyConstants.localTimeFormat);
				} else {
					timeStr = DateUtil.formatDate(paramValue, "HH:mm:ss") + DateUtil.processNano(nanoValue);
				}
			} else {
				timeStr = DateUtil.formatDate(paramValue, "HH:mm:ss");
			}
			valueStr = sign + timeStr + sign;
		} else if (paramValue instanceof Time) {
			valueStr = sign + DateUtil.formatDate(paramValue, "HH:mm:ss") + sign;
		} else if (paramValue instanceof Date) {
			valueStr = sign + DateUtil.formatDate(paramValue, "yyyy-MM-dd HH:mm:ss") + sign;
		} else if (paramValue instanceof Object[]) {
			valueStr = combineArray((Object[]) paramValue);
		} else if (paramValue instanceof Collection) {
			valueStr = combineArray(((Collection) paramValue).toArray());
		} else {
			valueStr = "" + paramValue;
		}
		return valueStr;
	}

	/**
	 * 将参数值转成字符传
	 * 
	 * @param sqlArgValue        待转换的参数值，null返回"null"
	 * @param preSql             前面的sql片段
	 * @param addSingleQuotation 是否加单引号
	 * @param dbType             数据库方言
	 * @return 参数值对应的字符串形式，日期时间类型且需要引号时按数据库方言包上转日期函数
	 */
	public static String toSqlLogStr(Object sqlArgValue, String preSql, boolean addSingleQuotation, int dbType) {
		if (sqlArgValue == null) {
			return "null";
		}
		// 参数前面是否是条件比较符号，如果是比较符号针对日期、字符串加单引号
		String sign = addSingleQuotation ? "'" : "";
		String valueStr;
		int nanoValue;
		// 1:day,2:dateTime;3,timestamp;4:time;5:time(3) 毫秒
		int dateType = -1;
		String timeStr;
		Object paramValue;
		if (sqlArgValue instanceof Enum) {
			paramValue = BeanUtil.getEnumValue(sqlArgValue);
		} else {
			paramValue = sqlArgValue;
		}
		if (paramValue instanceof CharSequence) {
			valueStr = sign + paramValue + sign;
		} else if (paramValue instanceof Timestamp) {
			valueStr = sign + DateUtil.formatDate(paramValue, "yyyy-MM-dd HH:mm:ss.SSS") + sign;
			dateType = 3;
		} else if (paramValue instanceof LocalDateTime) {
			nanoValue = ((LocalDateTime) paramValue).getNano();
			if (nanoValue > 0) {
				if (SqlToyConstants.localDateTimeFormat != null
						&& !SqlToyConstants.localDateTimeFormat.equals("auto")) {
					timeStr = DateUtil.formatDate(paramValue, SqlToyConstants.localDateTimeFormat);
				} else {
					timeStr = DateUtil.formatDate(paramValue, "yyyy-MM-dd HH:mm:ss") + DateUtil.processNano(nanoValue);
				}
				if (timeStr.length() > 19) {
					dateType = 3;
				} else {
					dateType = 2;
				}
			} else {
				timeStr = DateUtil.formatDate(paramValue, "yyyy-MM-dd HH:mm:ss");
				dateType = 2;
			}
			valueStr = sign + timeStr + sign;
		} else if (paramValue instanceof LocalDate) {
			valueStr = sign + DateUtil.formatDate(paramValue, "yyyy-MM-dd") + sign;
			dateType = 1;
		} else if (paramValue instanceof LocalTime) {
			nanoValue = ((LocalTime) paramValue).getNano();
			if (nanoValue > 0) {
				if (SqlToyConstants.localTimeFormat != null && !SqlToyConstants.localTimeFormat.equals("auto")) {
					timeStr = DateUtil.formatDate(paramValue, SqlToyConstants.localTimeFormat);
				} else {
					timeStr = DateUtil.formatDate(paramValue, "HH:mm:ss") + DateUtil.processNano(nanoValue);
				}
				if (timeStr.length() > 8) {
					dateType = 5;
				} else {
					dateType = 4;
				}
			} else {
				timeStr = DateUtil.formatDate(paramValue, "HH:mm:ss");
				dateType = 4;
			}
			valueStr = sign + timeStr + sign;
		} else if (paramValue instanceof Time) {
			valueStr = sign + DateUtil.formatDate(paramValue, "HH:mm:ss") + sign;
			dateType = 4;
		} else if (paramValue instanceof Date) {
			valueStr = sign + DateUtil.formatDate(paramValue, "yyyy-MM-dd HH:mm:ss") + sign;
			dateType = 2;
		} else if (paramValue instanceof Object[]) {
			valueStr = combineArray((Object[]) paramValue);
		} else if (paramValue instanceof Collection) {
			valueStr = combineArray(((Collection) paramValue).toArray());
		} else {
			valueStr = "" + paramValue;
		}
		// 增加单引号和日期类型
		if (dateType != -1 && addSingleQuotation) {
			return addDateFunction(valueStr, preSql, dateType, dbType);
		}
		return valueStr;
	}

	/**
	 * add 2025-04-01 sql日志日期、时间类型条件参数增加转日期函数输出
	 * 
	 * @param dateStr 带引号的日期时间字符串
	 * @param preSql  参数前面的sql片段，用于判断是否为条件比较位置
	 * @param type    日期类型：1日期、2日期时间、3时间戳、4时间、5毫秒时间
	 * @param dbType  数据库方言
	 * @return 包裹数据库转日期函数后的字符串，前置片段非比较条件或方言无对应函数时原样返回
	 */
	private static String addDateFunction(String dateStr, String preSql, int type, int dbType) {
		// 前置sql片段不以<,=,<,between,and 作为结尾则不增加函数
		if (!StringUtil.matches(preSql, COMPARE_PATTERN)) {
			return dateStr;
		}
		int dateLength = dateStr.length() - 2;
		// update 2026-9-8 oceanbase移出oracle分支:ob主流为mysql模式,实测TO_DATE不存在
		// ("FUNCTION TO_DATE does not exist"),应走mysql系STR_TO_DATE;oracle与dm拆分time形态:
		// 实测oracle无独立TIME类型(CAST(as TIME)报ORA-00932),dm支持CAST(as TIME)且结果正确
		if (dbType == DBType.ORACLE || dbType == DBType.ORACLE11 || dbType == DBType.DM) {
			// day
			if (type == 1) {
				return "TO_DATE(" + dateStr + ",'YYYY-MM-DD')";
			}
			// datetime
			if (type == 2) {
				return "TO_DATE(" + dateStr + ",'YYYY-MM-DD HH24:MI:SS')";
			}
			// timestamp
			if (type == 3) {
				if (dateLength > 23) {
					return "TO_TIMESTAMP(" + dateStr + ",'YYYY-MM-DD HH24:MI:SS.FF')";
				} else {
					return "TO_TIMESTAMP(" + dateStr + ",'YYYY-MM-DD HH24:MI:SS.FF3')";
				}
			}
			// time:dm有独立TIME类型,CAST(as TIME)实测正确输出时间部分;oracle无TIME类型
			// (实测CAST(as TIME)报ORA-00932),以TO_DATE(补当月首日时间部分)作为日志可执行
			// 形态的近似输出,LocalTime与DATE/TIMESTAMP列比较的精确语义以驱动参数绑定为准
			if (type == 4 || type == 5) {
				if (dbType == DBType.DM) {
					if (type == 5 && dateLength > 12) {
						return "CAST(TO_DATE(" + dateStr + ",'HH24:MI:SS.FF') as TIME)";
					}
					return "CAST(TO_DATE(" + dateStr + ",'HH24:MI:SS') as TIME)";
				}
				if (type == 5 && dateLength > 12) {
					return "TO_DATE(" + dateStr + ",'HH24:MI:SS.FF')";
				}
				return "TO_DATE(" + dateStr + ",'HH24:MI:SS')";
			}
		}
		if (dbType == DBType.SQLSERVER) {
			// day
			if (type == 1) {
				return "CONVERT(date," + dateStr + ")";
			}
			// datetime
			if (type == 2) {
				return "CONVERT(datetime," + dateStr + ")";
			}
			// timestamp
			if (type == 3) {
				return "CONVERT(datetime2," + dateStr + ")";
			}
			// time
			if (type == 4) {
				return "CONVERT(time," + dateStr + ")";
			}
			// 毫秒time
			if (type == 5) {
				return "CONVERT(time(3)," + dateStr + ")";
			}
		}
		// mysql和tidb;update 2026-9-8 oceanbase主流为mysql模式纳入本分支(实测STR_TO_DATE可用,
		// 而TO_DATE报"FUNCTION TO_DATE does not exist")
		if (dbType == DBType.MYSQL || dbType == DBType.MYSQL57 || dbType == DBType.TIDB || dbType == DBType.DORIS
				|| dbType == DBType.STARROCKS || dbType == DBType.OCEANBASE) {
			// day
			if (type == 1) {
				return "STR_TO_DATE(" + dateStr + ",'%Y-%m-%d')";
			}
			// datetime
			if (type == 2) {
				return "STR_TO_DATE(" + dateStr + ",'%Y-%m-%d %H:%i:%s')";
			}
			// timestamp
			if (type == 3) {
				return "STR_TO_DATE(" + dateStr + ",'%Y-%m-%d %H:%i:%s.%f')";
			}
			// time
			if (type == 4) {
				return "STR_TO_DATE(" + dateStr + ",'%H:%i:%s')";
			}
			// 毫秒time
			if (type == 5) {
				return "STR_TO_DATE(" + dateStr + ",'%H:%i:%s.%f')";
			}
		}
		// postgresql系列
		if (dbType == DBType.POSTGRESQL || dbType == DBType.POSTGRESQL14 || dbType == DBType.GAUSSDB
				|| dbType == DBType.STARDB || dbType == DBType.OPENGAUSS || dbType == DBType.MOGDB
				|| dbType == DBType.OSCAR || dbType == DBType.VASTBASE) {
			// day
			if (type == 1) {
				return "TO_DATE(" + dateStr + ",'YYYY-MM-DD')";
			}
			// datetime
			if (type == 2) {
				return "TO_TIMESTAMP(" + dateStr + ",'YYYY-MM-DD HH24:MI:SS')";
			}
			// timestamp
			if (type == 3) {
				if (dateLength > 23) {
					return "TO_TIMESTAMP(" + dateStr + ",'YYYY-MM-DD HH24:MI:SS.US')";
				} else {
					return "TO_TIMESTAMP(" + dateStr + ",'YYYY-MM-DD HH24:MI:SS.MS')";
				}
			}
			// time
			if (type == 4) {
				return "" + dateStr + "::TIME";
			}
			// 毫秒time
			if (type == 5) {
				return "" + dateStr + "::TIME";
			}
		}
		if (dbType == DBType.KINGBASE) {
			// day
			if (type == 1) {
				return "TO_DATE(" + dateStr + ",'YYYY-MM-DD')";
			}
			// datetime
			if (type == 2) {
				return "TO_DATE(" + dateStr + ",'YYYY-MM-DD HH24:MI:SS')";
			}
			// timestamp
			if (type == 3) {
				if (dateLength > 23) {
					return "TO_TIMESTAMP(" + dateStr + ",'YYYY-MM-DD HH24:MI:SS.US')";
				} else {
					return "TO_TIMESTAMP(" + dateStr + ",'YYYY-MM-DD HH24:MI:SS.MS')";
				}
			}
			// time
			if (type == 4) {
				return "TIME(TO_TIMESTAMP(" + dateStr + ",'YYYY-MM-DD HH24:MI:SS'))";
			}
			// 毫秒time
			if (type == 5) {
				if (dateLength > 12) {
					return "TIME(TO_TIMESTAMP(" + dateStr + ",'YYYY-MM-DD HH24:MI:SS.US'))";
				} else {
					return "TIME(TO_TIMESTAMP(" + dateStr + ",'YYYY-MM-DD HH24:MI:SS.MS'))";
				}
			}
		}
		// clickhouse
		if (dbType == DBType.CLICKHOUSE) {
			// day
			if (type == 1) {
				return "toDate(" + dateStr + ")";
			}
			// datetime
			if (type == 2) {
				return "toDateTime(" + dateStr + ")";
			}
			// timestamp
			if (type == 3) {
				if (dateLength > 23) {
					return "toDateTime64(" + dateStr + ",6)";
				} else {
					return "toDateTime64(" + dateStr + ",3)";
				}
			}
			// time
			if (type == 4) {
				return "formatDateTime(toDateTime(" + dateStr + "), '%H:%M:%S')";
			}
			// 毫秒time
			if (type == 5) {
				if (dateLength > 12) {
					return "formatDateTime(toDateTime64(" + dateStr + ",6), '%H:%M:%S.%f')";
				} else {
					return "formatDateTime(toDateTime64(" + dateStr + ",3), '%H:%M:%S.%f')";
				}
			}
		}
		// db2
		if (dbType == DBType.DB2) {
			// day
			if (type == 1) {
				return "TO_DATE(" + dateStr + ",'YYYY-MM-DD')";
			}
			// datetime
			if (type == 2) {
				return "TO_DATE(" + dateStr + ",'YYYY-MM-DD HH24:MI:SS')";
			}
			// timestamp
			if (type == 3) {
				if (dateLength > 23) {
					return "TO_TIMESTAMP(" + dateStr + ",'YYYY-MM-DD HH24:MI:SS.FF')";
				} else {
					return "TO_TIMESTAMP(" + dateStr + ",'YYYY-MM-DD HH24:MI:SS.FF3')";
				}
			}
			// time
			if (type == 4) {
				return "TIME(TO_TIMESTAMP(" + dateStr + ",'YYYY-MM-DD HH24:MI:SS'))";
			}
			// 毫秒time
			if (type == 5) {
				if (dateLength > 12) {
					return "TIME(TO_TIMESTAMP(" + dateStr + ",'HH24:MI:SS.FF'))";
				} else {
					return "TIME(TO_TIMESTAMP(" + dateStr + ",'HH24:MI:SS.FF3'))";
				}
			}
		}
		return dateStr;
	}

	/**
	 * 组合in参数
	 * 
	 * @param array 参数值数组，null或空返回"null"
	 * @return 逗号连接的参数串(字符和日期时间类型加单引号，支持枚举)，可直接拼入in子句
	 */
	public static String combineArray(Object[] array) {
		if (array == null || array.length == 0) {
			return "null";
		}
		StringBuilder result = new StringBuilder();
		Object value;
		int nanoValue;
		String timeStr;
		for (int i = 0; i < array.length; i++) {
			if (i > 0) {
				result.append(",");
			}
			value = array[i];
			if (value == null) {
				result.append("null");
			} else {
				// 支持枚举类型
				if (value instanceof Enum) {
					value = BeanUtil.getEnumValue(value);
				}
				if (value instanceof CharSequence) {
					result.append("'" + value + "'");
				} else if (value instanceof Timestamp) {
					result.append("'" + DateUtil.formatDate(value, "yyyy-MM-dd HH:mm:ss.SSS") + "'");
				} else if (value instanceof LocalDateTime) {
					nanoValue = ((LocalDateTime) value).getNano();
					if (nanoValue > 0) {
						if (SqlToyConstants.localDateTimeFormat != null
								&& !SqlToyConstants.localDateTimeFormat.equals("auto")) {
							timeStr = DateUtil.formatDate(value, SqlToyConstants.localDateTimeFormat);
						} else {
							timeStr = DateUtil.formatDate(value, "yyyy-MM-dd HH:mm:ss")
									+ DateUtil.processNano(nanoValue);
						}
					} else {
						timeStr = DateUtil.formatDate(value, "yyyy-MM-dd HH:mm:ss");
					}
					result.append("'" + timeStr + "'");
				} else if (value instanceof LocalDate) {
					result.append("'" + DateUtil.formatDate(value, "yyyy-MM-dd") + "'");
				} else if (value instanceof LocalTime) {
					nanoValue = ((LocalTime) value).getNano();
					if (nanoValue > 0) {
						if (SqlToyConstants.localTimeFormat != null
								&& !SqlToyConstants.localTimeFormat.equals("auto")) {
							timeStr = DateUtil.formatDate(value, SqlToyConstants.localTimeFormat);
						} else {
							timeStr = DateUtil.formatDate(value, "HH:mm:ss") + DateUtil.processNano(nanoValue);
						}
					} else {
						timeStr = DateUtil.formatDate(value, "HH:mm:ss");
					}
					result.append("'" + timeStr + "'");
				} else if (value instanceof Time) {
					result.append("'" + DateUtil.formatDate(value, "HH:mm:ss") + "'");
				} else if (value instanceof Date) {
					result.append("'" + DateUtil.formatDate(value, "yyyy-MM-dd HH:mm:ss") + "'");
				} else {
					result.append("" + value);
				}
			}
		}
		return result.toString();
	}

	/**
	 * merge into sql特定数据库下需要补充;符号(sql加工成SqlToyConfig 时统一清理掉了分号)
	 * 
	 * @param sql    sql语句
	 * @param dbType 数据库类型，参见DataSourceUtils.DBType
	 * @return 按数据库方言调整分号后的sql语句，非merge into语句原样返回
	 */
	public static String adjustMergeIntoSql(String sql, Integer dbType) {
		String sqlTrimLow = sql.toLowerCase(Locale.ROOT).trim();
		// 非merge into 不做任何处理
		if (!StringUtil.matches(sqlTrimLow, MERGE_INTO_PATTERN)) {
			return sql;
		}
		boolean isBranchEnd = sqlTrimLow.endsWith(";");
		// sqlserver merge into 要以;结尾
		if (dbType == DBType.SQLSERVER && !isBranchEnd) {
			return sql.concat(";");
		}
		// 其他数据库merge into 以;结尾则需要剔除分号
		if (isBranchEnd && dbType != DBType.SQLSERVER) {
			return sql.substring(0, sql.lastIndexOf(";"));
		}
		return sql;
	}

	/**
	 * <p>
	 * 主要用于分页场景(极端特殊情况自定义count-sql):
	 * <li>1、获取select 对称的from位置;</li>
	 * <li>2、判断是否复杂查询(分页是否select count(1) from (sql))，获取from 对称的where的位置</li>
	 * <li>非分页:sqlserver 锁查询，提取from位置,此场景sql简单,不会产生问题</li>
	 * </p>
	 * <p>
	 * 字面量内容不参与判定:先将'...'字面量内部掩为等长空白再分析,
	 * 规避字面量内的from/select/where以及'('、')'等字符干扰关键词定位与括号配对 (掩码串与原串等长,返回位置可直接用于原串截取)
	 * </p>
	 * 
	 * @param sql        sql语句
	 * @param startRegex 起始关键词正则表达式，如select
	 * @param endRegex   结束关键词正则表达式，如from
	 * @param startIndex 起始查找位置
	 * @return 与startRegex对称匹配的endRegex位置，未找到返回-1
	 */
	public static int getSymMarkIndexExcludeKeyWords(String sql, String startRegex, String endRegex, int startIndex) {
		String sqlLow = SqlConfigParseUtils.maskLiterals(sql.toLowerCase(Locale.ROOT), false);
		int startRegexIndex = StringUtil.matchIndex(sqlLow, startRegex, startIndex)[0];
		int endRegIndex = StringUtil.getSymMarkMatchIndex(startRegex, endRegex, sqlLow, startIndex);
		// 就一个endPattern直接返回
		if (endRegIndex > 0
				&& StringUtil.matchCnt(startIndex == 0 ? sqlLow : sqlLow.substring(startIndex), endRegex) == 1) {
			return endRegIndex;
		}
		String startMark = "(", endMark = ")";
		int startBreaket = sqlLow.indexOf(startMark, startRegexIndex);
		// 在select 和from之间有()符号，要排除select (day from()) from 场景
		if (startBreaket < endRegIndex && startBreaket > 0) {
			// 删除所有对称的括号中的内容
			int start = startBreaket;
			int symMarkEnd;
			String tail;
			while (start != -1) {
				symMarkEnd = StringUtil.getSymMarkIndex(startMark, endMark, sqlLow, start);
				if (symMarkEnd != -1) {
					tail = sqlLow.substring(symMarkEnd);
					// 替换掉对称()中的select、from、where为等长字符，避免找select 对称的from位置形成干扰
					sqlLow = sqlLow.substring(0, start) + sqlLow.substring(start, symMarkEnd).replace("from", "AAAA")
							.replace("select", "AAAAAA").replace("where", "AAAAA") + tail;
					// 后续sql中没有endPattern则停止处理
					if (!StringUtil.matches(tail, endRegex)) {
						break;
					}
					start = sqlLow.indexOf(startMark, symMarkEnd);
				} else {
					break;
				}
			}
			int lastEndRegIndex = StringUtil.getSymMarkMatchIndex(startRegex, endRegex, sqlLow, startIndex);
			if (lastEndRegIndex == -1) {
				return endRegIndex;
			}
			return lastEndRegIndex;
		}
		return endRegIndex;
	}

	/**
	 * 统一将sql中@fast的位置标识注释符号转化为@fast，目的是便于sql调试
	 * 
	 * @param sql 含@fast_start/@fast_end注释标记的sql语句
	 * @return 标记还原为@fast后的sql语句，无标记时原样返回
	 */
	public static String uniformFastMarks(String sql) {
		int startRegexIndex = 0;
		int endRegexIndex = 0;
		int fastStart = -1;
		int fastEnd = -1;
		int startRegexLength = FAST_START_REGEXS.length;
		int endRegexLength = FAST_END_REGEXS.length;
		while (startRegexIndex < startRegexLength) {
			fastStart = StringUtil.matchIndex(sql, FAST_START_REGEXS[startRegexIndex]);
			if (fastStart >= 0) {
				break;
			}
			startRegexIndex++;
		}
		if (fastStart == -1) {
			return sql;
		}
		while (endRegexIndex < endRegexLength) {
			fastEnd = StringUtil.matchIndex(sql, FAST_END_REGEXS[endRegexIndex]);
			if (fastEnd > 0) {
				break;
			}
			endRegexIndex++;
		}
		// update 2025-2-5 支持只有-- @fast_start标记场景
		if (fastEnd == -1) {
			return sql.replaceFirst(FAST_START_REGEXS[startRegexIndex], "@fast");
		}
		if (fastEnd > fastStart) {
			return sql.replaceFirst(FAST_START_REGEXS[startRegexIndex], "@fast")
					.replaceFirst(FAST_END_REGEXS[endRegexIndex], " ");
		}
		return sql;
	}

	/**
	 * 校验参数是否存在sql注入(即sql片段)
	 * 
	 * @param sqlInjectionLevel 注入校验级别，决定采用何种匹配策略
	 * @param paramValue        待校验的参数值，支持字符串、字符串数组和集合类型
	 * @throws IllegalArgumentException
	 */
	public static boolean isSqlInjection(SqlInjectionLevel sqlInjectionLevel, Object paramValue) {
		List<String> matchValues = toList(paramValue);
		if (matchValues == null || matchValues.isEmpty()) {
			return false;
		}
		Pattern[] patterns = null;
		// 是否取反
		boolean isNegate = false;
		// 一个单词
		if (sqlInjectionLevel.equals(SqlInjectionLevel.STRICT_WORD)) {
			patterns = new Pattern[] { STRICT_WORD };
			isNegate = true;
		} else if (sqlInjectionLevel.equals(SqlInjectionLevel.RELAXED_WORD)) {
			patterns = new Pattern[] { RELAXED_WORD };
			isNegate = true;
		} else if (sqlInjectionLevel.equals(SqlInjectionLevel.SQL_KEYWORD)) {
			patterns = SQL_INJECTION_KEY_WORDS;
			isNegate = false;
		}
		boolean isInjection = false;
		if (patterns != null) {
			for (Pattern pattern : patterns) {
				for (String paramStr : matchValues) {
					isInjection = isNegate ? !StringUtil.matches(paramStr, pattern)
							: StringUtil.matches(paramStr, pattern);
					if (isInjection) {
						break;
					}
				}
				if (isInjection) {
					break;
				}
			}
		}
		return isInjection;
	}

	/**
	 * sql注入校验的参数，将字符类型的转成List<String>供统一处理，非字符类型返回空集合(无需验证)
	 * 
	 * @param paramValue 待处理的参数值，支持String、String[]和全为字符串的Iterable
	 * @return 字符串值组成的List，混合类型集合从首个非字符串处截断，非字符类型返回空List
	 */
	private static List<String> toList(Object paramValue) {
		List<String> result = new ArrayList<>();
		if (paramValue instanceof String) {
			result.add(paramValue.toString());
			return result;
		} else if (paramValue instanceof String[]) {
			String[] paramsStr = (String[]) paramValue;
			for (String param : paramsStr) {
				result.add(param);
			}
		} else if (paramValue instanceof Iterable) {
			Iterator iter = ((Iterable) paramValue).iterator();
			Object iterValue;
			boolean isStr = false;
			while (iter.hasNext()) {
				iterValue = iter.next();
				if (isStr) {
					result.add(iterValue == null ? null : iterValue.toString());
				} else if (iterValue != null) {
					if (!(iterValue instanceof String)) {
						break;
					} else {
						isStr = true;
						result.add(iterValue.toString());
					}
				}
			}
		}
		return result;
	}

	/**
	 * 替换 SQL 中的 ${xxx}或 ${:xxx} 为 @value(:xxx)
	 * 
	 * @param originalSql 原始 SQL
	 * @return 替换后的 SQL
	 */
	public static String replaceEmbedSqlParams(String originalSql) {
		if (originalSql == null || originalSql.isEmpty()) {
			return originalSql;
		}
		// ${paramName} 或${:paramName} 匹配
		Matcher matcher = SqlToyConstants.EMBED_NAMED_PATTERN.matcher(originalSql);
		StringBuffer sb = new StringBuffer();
		String paramName;
		while (matcher.find()) {
			paramName = matcher.group();
			paramName = paramName.substring(2, paramName.length() - 1).trim();
			// 替换成 @value(:参数名)
			matcher.appendReplacement(sb,
					paramName.startsWith(":") ? "@value(" + paramName + ")" : "@value(:" + paramName + ")");
		}
		matcher.appendTail(sb);
		return sb.toString();
	}

	/**
	 * 转义LIKE查询值中的特殊字符,避免用户输入的_和%被数据库当作通配符 PreparedStatement参数值必须使用\转义(配合ESCAPE
	 * '\'子句),
	 * 
	 * @param value         原始值
	 * @param dbType        数据库类型(保留用于向后兼容,所有数据库统一使用\转义)
	 * @param escapePercent 是否转义%符号(true:将%作为字面量转义;false:保留%作为通配符)
	 * @return 转义后的值(统一使用\转义,需配合ESCAPE '\'子句使用)
	 */
	public static String escapeLikeValue(String value, int dbType, boolean escapePercent) {
		if (value == null || value.isEmpty()) {
			return value;
		}
		// update 2026-9-6 实测sqlserver的LIKE中方括号是字符类通配符([abc]匹配单字符a/b/c),
		// 需转义为字面量(ESCAPE'\'子句下\[即字面量[);其他库[为普通字符不转义
		boolean escapeBracket = (dbType == DBType.SQLSERVER);
		// 先去除已有转义,确保多次调用幂等,避免二次转义
		// 用占位符保护\\避免与\_、\%产生交叉干扰
		String result = value.replace("\\\\", "\u0000").replace("\\_", "_");
		if (escapeBracket) {
			result = result.replace("\\[", "[");
		}
		if (escapePercent) {
			result = result.replace("\\%", "%");
		} else {
			// escapePercent=false时保护已转义的\%,避免被后续\\转义步骤破坏
			result = result.replace("\\%", "\u0001");
		}
		result = result.replace("\u0000", "\\");
		// 重新转义
		result = result.replace("\\", "\\\\").replace("_", "\\_");
		if (escapeBracket) {
			result = result.replace("[", "\\[");
		}
		if (escapePercent) {
			result = result.replace("%", "\\%");
		} else {
			result = result.replace("\u0001", "\\%");
		}
		return result;
	}
}
