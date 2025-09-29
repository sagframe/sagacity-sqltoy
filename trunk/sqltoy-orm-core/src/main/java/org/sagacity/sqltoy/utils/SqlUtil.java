package org.sagacity.sqltoy.utils;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.sagacity.sqltoy.SqlExecuteStat;
import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.SqlToyContext;
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
import org.sagacity.sqltoy.model.IgnoreCaseSet;
import org.sagacity.sqltoy.model.SqlInjectionLevel;
import org.sagacity.sqltoy.model.TreeTableModel;
import org.sagacity.sqltoy.plugins.TypeHandler;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author zhongxuchen
 * @version v1.3, Date:Apr 14, 2009 11:52:31 PM
 * @project sagacity-sqltoy
 * @description 数据库sql相关的处理工具
 * @modify Date:2011-8-18
 *         {移植BaseDaoSupport中分页移植到SqlUtil中，将数据库表、外键、主键等库和表信息移植到DBUtil中 }
 * @modify Date:2011-8-22 {修复getJdbcRecordCount中因group分组查询导致的错误， 如select
 *         name,count(*) from table group by name}
 * @modify Date:2012-11-21
 *         {完善分页查询语句中存在union的处理机制,框架自动判断是否存在union,有union则自动实现外层包裹}
 * @modify Date:2017-6-5 {剔除注释时用空白填补,防止出现类似原本:select xxx from 变成select xxxfrom }
 * @modify Date:2017-6-14 {修复针对阿里的druid数据库datasource针对clob类型处理的错误}
 * @modify Date:2019-7-5 剔除对druid clob bug的支持(druid 1.1.10 已经修复)
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

	/**
	 * 存放转换后的sql
	 */
	private static ConcurrentHashMap<String, String> convertSqlMap = new ConcurrentHashMap<String, String>();

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
	 * @param conditions :数据库in条件的数据集合，可以是POJO List或Object[]
	 * @param colIndex   :二维数组对应列编号
	 * @param property   :POJO property
	 * @param isChar     :in 是否要加单引号
	 * @todo 合成数据库in 查询的条件(不建议使用)
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
	 * @param typeHandler
	 * @param conn
	 * @param dbType
	 * @param pst
	 * @param params
	 * @param paramsType
	 * @param fromIndex
	 * @throws SQLException
	 * @throws IOException
	 * @todo 自动进行类型转换, 设置sql中的参数条件的值
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
	 * @param typeHandler
	 * @param conn
	 * @param dbType
	 * @param pst
	 * @param params
	 * @param paramsType
	 * @param fromIndex
	 * @throws SQLException
	 * @throws IOException
	 * @TODO 针对sqlserver提供特殊处理(避免干扰其他代码)
	 */
	private static void setSqlServerParamsValue(TypeHandler typeHandler, Connection conn, final Integer dbType,
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
				int meter = 0;
				for (int i = 0; i < n; i++) {
					if (paramsType[i] != java.sql.Types.TIMESTAMP) {
						setParamValue(typeHandler, conn, dbType, pst, params[i], paramsType[i], startIndex + meter);
						meter++;
					}
				}
			}
		}
	}

	/**
	 * @param typeHandler
	 * @param conn
	 * @param dbType
	 * @param pst
	 * @param paramValue
	 * @param jdbcType
	 * @param paramIndex
	 * @throws SQLException
	 * @throws IOException
	 * @todo 设置sql中的参数条件的值
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
					if (dbType == DBType.POSTGRESQL || dbType == DBType.POSTGRESQL15) {
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
			} else {
				pst.setString(paramIndex, tmpStr);
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
			} else {
				pst.setInt(paramIndex, paramInt);
			}
		} else if (paramValue instanceof java.time.LocalDateTime) {
			pst.setTimestamp(paramIndex, Timestamp.valueOf((LocalDateTime) paramValue));
		} else if (paramValue instanceof BigDecimal) {
			pst.setBigDecimal(paramIndex, (BigDecimal) paramValue);
		} else if (paramValue instanceof java.time.LocalDate) {
			pst.setDate(paramIndex, java.sql.Date.valueOf((LocalDate) paramValue));
		} else if (paramValue instanceof java.sql.Timestamp) {
			pst.setTimestamp(paramIndex, (java.sql.Timestamp) paramValue);
		} else if (paramValue instanceof java.util.Date) {
			if (dbType == DBType.CLICKHOUSE) {
				pst.setDate(paramIndex, new java.sql.Date(((java.util.Date) paramValue).getTime()));
			} else {
				pst.setTimestamp(paramIndex, new Timestamp(((java.util.Date) paramValue).getTime()));
			}
		} else if (paramValue instanceof java.math.BigInteger) {
			pst.setBigDecimal(paramIndex, new BigDecimal(((BigInteger) paramValue)));
		} else if (paramValue instanceof java.lang.Double) {
			pst.setDouble(paramIndex, ((Double) paramValue));
		} else if (paramValue instanceof java.lang.Long) {
			pst.setLong(paramIndex, ((Long) paramValue));
		} else if (paramValue instanceof java.sql.Clob) {
			tmpStr = clobToString((java.sql.Clob) paramValue);
			pst.setString(paramIndex, tmpStr);
		} else if (paramValue instanceof byte[]) {
			if (jdbcType == java.sql.Types.BLOB) {
				if (dbType == DBType.MOGDB || dbType == DBType.VASTBASE || dbType == DBType.OPENGAUSS
						|| dbType == DBType.STARDB) {
					pst.setBlob(paramIndex, new ByteArrayInputStream((byte[]) paramValue));
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
			pst.setFloat(paramIndex, ((Float) paramValue));
		} else if (paramValue instanceof java.sql.Blob) {
			Blob blob = (java.sql.Blob) paramValue;
			int size = (int) blob.length();
			if (size > 0) {
				pst.setBytes(paramIndex, blob.getBytes(1, size));
			} else {
				pst.setBytes(paramIndex, new byte[0]);
			}
		} else if (paramValue instanceof java.sql.Date) {
			pst.setDate(paramIndex, (java.sql.Date) paramValue);
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
			pst.setTime(paramIndex, java.sql.Time.valueOf((LocalTime) paramValue));
		} else if (paramValue instanceof java.sql.Time) {
			pst.setTime(paramIndex, (java.sql.Time) paramValue);
		} else if (paramValue instanceof java.lang.Character) {
			tmpStr = ((Character) paramValue).toString();
			pst.setString(paramIndex, tmpStr);
		} else if (paramValue instanceof java.lang.Short) {
			pst.setShort(paramIndex, (java.lang.Short) paramValue);
		} else if (paramValue instanceof java.lang.Byte) {
			pst.setByte(paramIndex, (Byte) paramValue);
		} else if (paramValue instanceof Object[]) {
			setArray(dbType, conn, pst, paramIndex, paramValue);
		} // update 2023-08-02 增加默认的枚举类型处理
		else if (paramValue instanceof Enum) {
			pst.setObject(paramIndex, BeanUtil.getEnumValue(paramValue));
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
	 * @param dbType
	 * @param conn
	 * @param pst
	 * @param paramIndex
	 * @param paramValue
	 * @throws SQLException
	 * @TODO setArray gaussdb 必须要通过conn构造Array
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
	 * @param typeHandler
	 * @param rs
	 * @param voClass
	 * @param ignoreAllEmptySet
	 * @param columnFieldMap
	 * @return
	 * @throws Exception
	 * @todo <b>提供数据查询结果集转java对象的反射处理，以java VO集合形式返回</b>
	 */
	private static List reflectResultToVO(TypeHandler typeHandler, DecryptHandler decryptHandler, ResultSet rs,
			Class voClass, boolean ignoreAllEmptySet, HashMap<String, String> columnFieldMap) throws Exception {
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
		String[] columnNames = getColumnLabels(rs.getMetaData());
		// 组织vo中对应的属性
		String[] fields = new String[columnNames.length];
		// update 2020-12-24 增加映射对象时属性映射关系提取
		boolean hasMap = (columnFieldMap == null || columnFieldMap.isEmpty()) ? false : true;
		// 剔除下划线
		for (int i = 0; i < fields.length; i++) {
			fields[i] = columnNames[i].toLowerCase();
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
		Class[] genericTypes = new Class[setMethods.length];
		Type[] types;
		Class methodType;
		for (int i = 0; i < propTypes.length; i++) {
			if (setMethods[i] != null) {
				methodType = setMethods[i].getParameterTypes()[0];
				propTypes[i] = methodType.getTypeName();
				propTypeValues[i] = DataType.getType(methodType);
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
			rowData = reflectResultRowToVOClass(typeHandler, decryptHandler, rs, columnNames, setMethods,
					propTypeValues, propTypes, genericTypes, voClass, ignoreAllEmptySet);
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
			logger.warn("Large Result:class={},total:{}>={}" + index, voClass.getName(), index, warnThresholds);
		}
		// 提醒实际提取数量
		if (maxLimit) {
			logger.warn("Large Result:class={},total:{}>={}" + index, voClass.getName(), index, maxThresholds);
		}
		return resultList;
	}

	/**
	 * @param typeHandler
	 * @param decryptHandler    解密
	 * @param rs
	 * @param columnLabels
	 * @param setMethods
	 * @param propTypeValues    对应类型int值
	 * @param propTypes         没有做大小写处理
	 * @param genericTypes
	 * @param voClass
	 * @param ignoreAllEmptySet
	 * @return
	 * @throws Exception
	 * @todo 提供数据查询结果集转java对象的反射处理，以java VO集合形式返回
	 */
	private static Object reflectResultRowToVOClass(TypeHandler typeHandler, DecryptHandler decryptHandler,
			ResultSet rs, String[] columnLabels, Method[] setMethods, int[] propTypeValues, String[] propTypes,
			Class[] genericTypes, Class voClass, boolean ignoreAllEmptySet) throws Exception {
		// 根据匹配的字段通过java reflection将rs中的值映射到VO中
		Object bean = voClass.getDeclaredConstructor().newInstance();
		Object fieldValue;
		boolean allNull = true;
		Method method;
		// 已经小写
		String typeName;
		String label;
		int typeValue;
		for (int i = 0, n = columnLabels.length; i < n; i++) {
			label = columnLabels[i];
			method = setMethods[i];
			typeName = propTypes[i];
			typeValue = propTypeValues[i];
			if (method != null) {
				fieldValue = rs.getObject(label);
				if (null != fieldValue) {
					if (decryptHandler != null) {
						fieldValue = decryptHandler.decrypt(label, fieldValue);
					}
					allNull = false;
					method.invoke(bean,
							BeanUtil.convertType(typeHandler, fieldValue, typeValue, typeName, genericTypes[i]));
				}
			}
		}
		if (allNull && ignoreAllEmptySet) {
			return null;
		}
		return bean;
	}

	/**
	 * @param rsmd
	 * @return
	 * @throws SQLException
	 * @TODO 获取ResultSet 里面的列名称
	 */
	private static String[] getColumnLabels(ResultSetMetaData rsmd) throws SQLException {
		int fieldCnt = rsmd.getColumnCount();
		String[] columnNames = new String[fieldCnt];
		for (int i = 1; i < fieldCnt + 1; i++) {
			columnNames[i - 1] = rsmd.getColumnLabel(i);
		}
		return columnNames;
	}

	/**
	 * @param userData
	 * @param pst
	 * @param rs
	 * @param preparedStatementResultHandler
	 * @return
	 * @throws Exception
	 * @todo 提供统一的ResultSet, PreparedStatemenet 关闭功能
	 */
	public static Object preparedStatementProcess(Object userData, PreparedStatement pst, ResultSet rs,
			PreparedStatementResultHandler preparedStatementResultHandler) throws Exception {
		try {
			preparedStatementResultHandler.execute(userData, pst, rs);
		} catch (Exception se) {
			se.printStackTrace();
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
				se.printStackTrace();
			}
		}
		return preparedStatementResultHandler.getResult();
	}

	/**
	 * @param userData
	 * @param pst
	 * @param rs
	 * @param callableStatementResultHandler
	 * @return
	 * @throws Exception
	 * @todo 提供统一的ResultSet, callableStatement 关闭功能
	 */
	public static Object callableStatementProcess(Object userData, CallableStatement pst, ResultSet rs,
			CallableStatementResultHandler callableStatementResultHandler) throws Exception {
		try {
			callableStatementResultHandler.execute(userData, pst, rs);
		} catch (Exception se) {
			se.printStackTrace();
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
				se.printStackTrace();
			}
		}
		return callableStatementResultHandler.getResult();
	}

	/**
	 * @param sql
	 * @return
	 * @todo 剔除sql中的注释(提供三种形态的注释剔除)
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
	 * @param sql
	 * @param lineMaskIndex
	 * @return
	 * @TODO 找到行注释的开始位置
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
				sql = sql.substring(0, start).concat(loopBlank(symMarkEnd - start + 1))
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
				sql = sql.substring(0, start).concat(loopBlank(symMarkEnd - start + 1))
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
				sql = sql.substring(0, start).concat(loopBlank(symMarkEnd - start + 2))
						.concat(sql.substring(symMarkEnd + 2));
				start = sql.indexOf("/*");
			} else {
				break;
			}
		}
		return sql.indexOf("--");
	}

	private static String loopBlank(int size) {
		if (size == 0) {
			return "";
		}
		StringBuilder result = new StringBuilder();
		for (int i = 0; i < size; i++) {
			result.append(BLANK);
		}
		return result.toString();
	}

	/**
	 * @param typeHandler
	 * @param queryStr
	 * @param params
	 * @param voClass
	 * @param rowCallbackHandler
	 * @param conn
	 * @param dbType
	 * @param ignoreAllEmptySet
	 * @param colFieldMap
	 * @return
	 * @throws Exception
	 * @todo <b>获取单条记录</b>
	 */
	public static Object loadByJdbcQuery(TypeHandler typeHandler, final String queryStr, final Object[] params,
			final Class voClass, final RowCallbackHandler rowCallbackHandler, final Connection conn,
			final Integer dbType, final boolean ignoreAllEmptySet, final HashMap<String, String> colFieldMap)
			throws Exception {
		List result = findByJdbcQuery(typeHandler, queryStr, params, voClass, rowCallbackHandler, null, conn, dbType,
				ignoreAllEmptySet, colFieldMap, -1, -1);
		if (result != null && !result.isEmpty()) {
			if (result.size() > 1) {
				throw new IllegalAccessException("查询结果不唯一,loadByJdbcQuery 方法只针对单条结果的数据查询!");
			}
			return result.get(0);
		}
		return null;
	}

	/**
	 * @param conn
	 * @param sequence
	 * @param dbType
	 * @return
	 * @throws DataAccessException
	 * @TODO 提供独立的获取sequence下一个值的方法
	 */
	public static Object getSequenceValue(Connection conn, String sequence, Integer dbType) throws DataAccessException {
		String sql = "";
		if (dbType == DBType.POSTGRESQL || dbType == DBType.POSTGRESQL15 || dbType == DBType.KINGBASE
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
			SqlExecuteStat.showSql("获取sequence下一个值", sql, null);
			pst = conn.prepareStatement(sql);
			rs = pst.executeQuery();
			while (rs.next()) {
				id = rs.getObject(1);
				break;
			}
		} catch (Exception e) {
			e.printStackTrace();
			throw new DataAccessException("获取sequence={} 值失败!错误信息:{}", sequence, e.getMessage());
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
	 * @param typeHandler
	 * @param queryStr
	 * @param params
	 * @param voClass
	 * @param rowCallbackHandler
	 * @param decryptHandler
	 * @param conn
	 * @param dbType
	 * @param ignoreAllEmptySet
	 * @param colFieldMap
	 * @param fetchSize
	 * @param maxRows
	 * @return
	 * @throws Exception
	 * @todo <b>sql 查询并返回List集合结果</b>
	 */
	public static List findByJdbcQuery(TypeHandler typeHandler, final String queryStr, final Object[] params,
			final Class voClass, final RowCallbackHandler rowCallbackHandler, final DecryptHandler decryptHandler,
			final Connection conn, final Integer dbType, final boolean ignoreAllEmptySet,
			final HashMap<String, String> colFieldMap, final int fetchSize, final int maxRows) throws Exception {
		ResultSet rs = null;
		PreparedStatement pst = conn.prepareStatement(queryStr, ResultSet.TYPE_FORWARD_ONLY,
				ResultSet.CONCUR_READ_ONLY);
		if (fetchSize > 0) {
			pst.setFetchSize(fetchSize);
		}
		if (maxRows > 0) {
			pst.setMaxRows(maxRows);
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
	 * @param typeHandler
	 * @param rs
	 * @param voClass
	 * @param rowCallbackHandler
	 * @param decryptHandler
	 * @param startColIndex
	 * @param ignoreAllEmptySet
	 * @param colFieldMap
	 * @return
	 * @throws Exception
	 * @todo 处理sql查询时的结果集, 当没有反调或voClass反射处理时以数组方式返回resultSet的数据
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
			result = reflectResultToVO(typeHandler, decryptHandler, rs, voClass, ignoreAllEmptySet, colFieldMap);
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

	/**
	 * @param typeHandler
	 * @param updateSql
	 * @param rowDatas
	 * @param batchSize
	 * @param insertCallhandler
	 * @param updateTypes
	 * @param autoCommit
	 * @param conn
	 * @param dbType
	 * @return
	 * @throws Exception
	 * @todo 通过jdbc方式批量插入数据，一般提供给数据采集时或插入临时表使用，一般采用hibernate 方式插入
	 */
	public static Long batchUpdateByJdbc(TypeHandler typeHandler, final String updateSql, final Collection rowDatas,
			final int batchSize, final InsertRowCallbackHandler insertCallhandler, final Integer[] updateTypes,
			final Boolean autoCommit, final Connection conn, final Integer dbType) throws Exception {
		if (rowDatas == null || rowDatas.isEmpty()) {
			logger.error("执行batchUpdateByJdbc 数据为空，sql={}", updateSql);
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
								throw new IllegalArgumentException(
										"batchUpdate sql中的?参数数量:" + argsCnt + " 跟实际传参数量:" + paramCnt + " 不等,请检查!");
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
								throw new IllegalArgumentException(
										"batchUpdate sql中的?参数数量:" + argsCnt + " 跟实际传参数量:" + paramCnt + " 不等,请检查!");
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
						if ((meter % batchSize) == 0 || index == totalRows) {
							int[] updateRows = pst.executeBatch();
							for (int t : updateRows) {
								updateCount = updateCount + ((t > 0) ? t : 0);
							}
							pst.clearBatch();
						}
					} // 单条执行
					else {
						updateCount = pst.executeUpdate();
					}
				}
			}
			if (hasSetAutoCommit) {
				conn.setAutoCommit(!autoCommit);
			}
		} catch (Exception e) {
			e.printStackTrace();
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
	 * @param typeHandler
	 * @param treeTableModel
	 * @param conn
	 * @param dbType
	 * @return
	 * @throws Exception
	 * @todo 计算树形结构表中的:节点层级、节点对应所有上级节点的路径、是否叶子节点
	 */
	public static boolean wrapTreeTableRoute(TypeHandler typeHandler, final TreeTableModel treeTableModel,
			Connection conn, final Integer dbType) throws Exception {
		if (StringUtil.isBlank(treeTableModel.getTableName()) || StringUtil.isBlank(treeTableModel.getIdField())
				|| StringUtil.isBlank(treeTableModel.getPidField())
				|| StringUtil.isBlank(treeTableModel.getPidValue())) {
			logger.error("请设置树形表的table名称、id字段名称、pid字段名称、pidValue值!");
			throw new IllegalArgumentException("没有对应的table名称、id字段名称、pid字段名称、pidValue值");
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
					SqlToyConstants.FETCH_SIZE, -1);
			// 设置第一层level
			int nodeLevel = 0;
			String nodeRoute = "";
			if (idInfo != null && !idInfo.isEmpty()) {
				if (((List) idInfo.get(0)).get(0) == null) {
					throw new DataAccessException("表中id=" + treeTableModel.getPidValue() + "对应的节点等级字段:" + nodeLevelField
							+ "值为null,不要越层调用wrapTreeTableRoute!");
				}
				if (((List) idInfo.get(0)).get(1) == null) {
					throw new DataAccessException("表中id=" + treeTableModel.getPidValue() + "对应的节点路径字段:" + nodeRouteField
							+ "值为null,不要越层调用wrapTreeTableRoute!");
				}
				nodeLevel = Integer.parseInt(((List) idInfo.get(0)).get(0).toString());
				nodeRoute = ((List) idInfo.get(0)).get(1).toString();
			}
			StringBuilder updateLevelAndRoute = new StringBuilder("update ").append(tableName).append(" set ")
					.append(nodeLevelField).append("=?,").append(nodeRouteField).append("=? ").append(" where ")
					.append(idField).append("=?");
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
						SqlToyConstants.FETCH_SIZE, -1);
			} else {
				ids = findByJdbcQuery(typeHandler,
						nextNodeQueryStr.toString().replaceFirst("\\$\\{inStr\\}",
								flag + treeTableModel.getPidValue() + flag),
						null, null, null, null, conn, dbType, false, null, SqlToyConstants.FETCH_SIZE, -1);
			}
			if (ids != null && !ids.isEmpty()) {
				processNextLevel(typeHandler, updateLevelAndRoute.toString(), nextNodeQueryStr.toString(),
						treeTableModel, pidsMap, ids, nodeLevel + 1, conn, dbType);
			}
		}
		// 设置节点是否为叶子节点，（mysql不支持update table where in 机制）
		if (StringUtil.isNotBlank(leafField)) {
			// 将所有记录先全部设置为叶子节点(isLeaf=1)
			StringBuilder updateLeafSql = new StringBuilder();
			updateLeafSql.append("update ").append(tableName);
			updateLeafSql.append(" set ").append(leafField).append("=1");
			// 附加条件(保留)
			if (StringUtil.isNotBlank(conditions)) {
				updateLeafSql.append(" where ").append(conditions);
			}
			// 先将所有节点设置为叶子
			executeSql(typeHandler, updateLeafSql.toString(), null, null, conn, dbType, null, true);
			// 再设置父节点的记录为非叶子节点(isLeaf=0)
			StringBuilder updateTrunkLeafSql = new StringBuilder();
			updateTrunkLeafSql.append("update ").append(tableName);
			// 支持mysql8 update 2018-5-11
			if (dbType == DBType.MYSQL || dbType == DBType.MYSQL57 || dbType == DBType.DORIS|| dbType == DBType.STARROCKS) {
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
				if (StringUtil.isNotBlank(conditions)) {
					updateTrunkLeafSql.append(" where ").append(conditions);
				}
			} else {
				// update organ_info set IS_LEAF=0
				// where organ_id in (select organ_pid from organ_info)
				updateTrunkLeafSql.append(" set ");
				updateTrunkLeafSql.append(leafField).append("=0");
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
			executeSql(typeHandler, updateTrunkLeafSql.toString(), null, null, conn, dbType, null, false);
		}
		return true;
	}

	/**
	 * @param typeHandler
	 * @param updateLevelAndRoute
	 * @param nextNodeQueryStr
	 * @param treeTableModel
	 * @param pidsMap
	 * @param ids
	 * @param nodeLevel
	 * @param conn
	 * @param dbType
	 * @throws Exception
	 * @todo TreeTableRoute中处理下一层级的递归方法，逐层计算下一级节点的节点层次和路径
	 */
	private static void processNextLevel(TypeHandler typeHandler, final String updateLevelAndRoute,
			final String nextNodeQueryStr, final TreeTableModel treeTableModel, final HashMap pidsMap, List ids,
			final int nodeLevel, Connection conn, final int dbType) throws Exception {
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
				if (treeTableModel.isChar()) {
					pst.setString(3, id);
				} else {
					pst.setLong(3, Long.parseLong(id));
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
					null, null, conn, dbType, false, null, SqlToyConstants.FETCH_SIZE, -1);
			// 递归处理下一层
			if (nextIds != null && !nextIds.isEmpty()) {
				processNextLevel(typeHandler, updateLevelAndRoute, nextNodeQueryStr, treeTableModel,
						CollectionUtil.hashList(subIds, 0, 1, true), nextIds, nodeLevel + 1, conn, dbType);
			}
			if (exist) {
				break;
			}
		}
	}

	/**
	 * @param conn
	 * @param sqlContent
	 * @param batchSize
	 * @param autoCommit
	 * @throws Exception
	 * @todo <b>sql文件自动创建到数据库</b>
	 */
	public static void executeBatchSql(Connection conn, String sqlContent, Integer batchSize, Boolean autoCommit)
			throws Exception {
		String splitSign = DataSourceUtils.getDatabaseSqlSplitSign(conn);
		// 剔除sql中的注释
		sqlContent = SqlUtil.clearMark(sqlContent);
		if (splitSign.indexOf("go") != -1) {
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
			int meter = 0;
			int realBatch = (batchSize == null || batchSize.intValue() > 1) ? batchSize.intValue() : 100;
			int totalRows = statments.length;
			int i = 0;
			for (String sql : statments) {
				if (StringUtil.isNotBlank(sql)) {
					meter++;
					logger.debug("正在批量执行的sql:{}", sql);
					stat.addBatch(sql);
				}
				if ((meter % realBatch) == 0 || i + 1 == totalRows) {
					stat.executeBatch();
					stat.clearBatch();
				}
				i++;
			}
		} catch (SQLException e) {
			e.printStackTrace();
			throw e;
		} finally {
			if (stat != null) {
				stat.close();
				stat = null;
			}
		}
		// 恢复conn原始autoCommit默认值
		if (hasSetAutoCommit) {
			conn.setAutoCommit(!autoCommit);
		}
	}

	/**
	 * @param sql
	 * @param judgeUpcase
	 * @return
	 * @todo <b>判断sql语句中是否有order by排序</b>
	 */
	public static boolean hasOrderBy(String sql, boolean judgeUpcase) {
		// 最后的收括号位置
		int lastBracketIndex = sql.lastIndexOf(")");
		boolean result = false;
		int orderByIndex = StringUtil.matchLastIndex(sql, ORDER_BY_PATTERN, 1);
		// 存在order by
		if (orderByIndex > lastBracketIndex) {
			result = true;
		}
		// 特殊处理 order by，通过ORder这种非常规写法代表分页时是否进行外层包裹(建议废弃使用)
		if (judgeUpcase) {
			int upcaseOrderBy = StringUtil.matchLastIndex(sql, UPCASE_ORDER_PATTERN, 1);
			if (upcaseOrderBy > lastBracketIndex) {
				result = false;
			}
		}
		return result;
	}

	/**
	 * @param clob
	 * @return
	 * @todo clob转换成字符串
	 */
	public static String clobToString(Clob clob) {
		if (clob == null) {
			return null;
		}
		StringBuffer sb = new StringBuffer(1024 * 8);// 8K
		Reader clobStream = null;
		try {
			clobStream = clob.getCharacterStream();
			char[] b = new char[1024];// 每次获取1K
			int i = 0;
			while ((i = clobStream.read(b)) != -1) {
				sb.append(b, 0, i);
			}
		} catch (Exception ex) {
			sb = null;
		} finally {
			closeQuietly(clobStream);
		}
		if (sb == null) {
			return null;
		}
		return sb.toString();
	}

	/**
	 * @param typeHandler
	 * @param executeSql
	 * @param params
	 * @param paramsType
	 * @param conn
	 * @param dbType
	 * @param autoCommit
	 * @param processWord
	 * @return
	 * @throws Exception
	 * @todo 执行Sql语句完成修改操作
	 */
	public static Long executeSql(TypeHandler typeHandler, final String executeSql, final Object[] params,
			final Integer[] paramsType, final Connection conn, final Integer dbType, final Boolean autoCommit,
			boolean processWord) throws Exception {
		// 对sql进行关键词符号替换
		String realSql = processWord ? ReservedWordsUtil.convertSql(executeSql, dbType) : executeSql;
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
		Object result = preparedStatementProcess(null, pst, null, new PreparedStatementResultHandler() {
			@Override
			public void execute(Object obj, PreparedStatement pst, ResultSet rs) throws SQLException, IOException {
				// sqlserver 存在timestamp不能赋值问题,通过对象完成的修改、插入忽视掉timestamp列
				if (dbType == DBType.SQLSERVER && paramsType != null) {
					setSqlServerParamsValue(typeHandler, conn, dbType, pst, params, paramsType, 0);
				} else {
					setParamsValue(typeHandler, conn, dbType, pst, params, paramsType, 0);
				}
				pst.executeUpdate();
				// 返回update的记录数量
				this.setResult(Long.valueOf(pst.getUpdateCount()));
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
		Object result = preparedStatementProcess(null, pst, null, new PreparedStatementResultHandler() {
			@Override
			public void execute(Object obj, PreparedStatement pst, ResultSet rs) throws SQLException, IOException {
				// sqlserver 存在timestamp不能赋值问题,通过对象完成的修改、插入忽视掉timestamp列
				if (dbType == DBType.SQLSERVER && paramsType != null) {
					setSqlServerParamsValue(typeHandler, conn, dbType, pst, params, paramsType, 0);
				} else {
					setParamsValue(typeHandler, conn, dbType, pst, params, paramsType, 0);
				}
				pst.execute();
				ResultSet keyResult = pst.getGeneratedKeys();
				if (keyResult != null) {
					while (keyResult.next()) {
						this.setResult(keyResult.getObject(1));
					}
					keyResult.close();
				}
				// 返回update的记录数量
				SqlExecuteStat.debug("执行结果", "insertReturnPrimaryKey操作影响记录量:{} 条!", Long.valueOf(pst.getUpdateCount()));
			}
		});
		if (hasSetAutoCommit && autoCommit != null) {
			conn.setAutoCommit(!autoCommit);
		}
		return result;
	}

	/**
	 * @param idValue
	 * @param idType
	 * @return
	 * @todo 转换主键数据类型(主键生成只支持数字和字符串类型)
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
	 * @param closeables 可关闭的流对象列表
	 * @throws IOException
	 * @todo 关闭一个或多个流对象
	 */
	public static void close(Closeable... closeables) throws IOException {
		if (closeables != null) {
			for (Closeable closeable : closeables) {
				if (closeable != null) {
					closeable.close();
				}
			}
		}
	}

	/**
	 * @param closeables 可关闭的流对象列表
	 * @todo 关闭一个或多个流对象
	 */
	public static void closeQuietly(Closeable... closeables) {
		try {
			close(closeables);
		} catch (IOException e) {
			// do nothing
		}
	}

	/**
	 * @param sql
	 * @param clearMistyChar
	 * @return
	 * @todo 判断是否内包含union 查询,即是否是select * from (select * from t union select * from
	 *       t2 ) 形式的查询,将所有()剔除后判定是否有union 存在
	 */
	public static boolean hasUnion(String sql, boolean clearMistyChar) {
		if (!StringUtil.matches(sql, UNION_PATTERN)) {
			return false;
		}
		// 存在with as ，先剔除
		if (StringUtil.matches(BLANK + sql, SqlToyConstants.withPattern)) {
			SqlWithAnalysis sqlWith = new SqlWithAnalysis(sql);
			sql = sqlWith.getRejectWithSql();
		}
		String tmpSql = BLANK + (clearMistyChar ? clearMistyChars(sql, BLANK) : sql);
		StringBuilder lastSql = new StringBuilder(tmpSql);
		// 找到第一个select 所对称的from位置，排查掉子查询中的内容
		int fromIndex = StringUtil.getSymMarkMatchIndex(SELECT_REGEX, FROM_REGEX, tmpSql.toLowerCase(), 0);
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
	 * @param entityMeta
	 * @param sql
	 * @return
	 * @TODO 转化对象字段名称为数据库字段名称
	 */
	public static String convertFieldsToColumns(EntityMeta entityMeta, String sql) {
		if (StringUtil.isBlank(sql)) {
			return sql;
		}
		String key = entityMeta.getTableName() + "_" + sql;
		// 从缓存中直接获取,避免每次都处理提升效率
		if (convertSqlMap.containsKey(key)) {
			return convertSqlMap.get(key);
		}
		String[] fields = entityMeta.getFieldsArray();
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
		// 放入缓存
		convertSqlMap.put(key, realSql);
		return realSql;
	}

	/**
	 * @param entityMeta
	 * @return
	 * @TODO 组合动态条件
	 */
	public static String wrapWhere(EntityMeta entityMeta) {
		String[] fields = entityMeta.getFieldsArray();
		StringBuilder sqlBuff = new StringBuilder(" 1=1 ");
		String columnName;
		for (String field : fields) {
			columnName = ReservedWordsUtil.convertWord(entityMeta.getColumnName(field), null);
			sqlBuff.append("#[and ").append(columnName).append("=:").append(field).append("]");
		}
		return sqlBuff.toString();
	}

	/**
	 * @param sqlToyContext
	 * @param entityClass
	 * @param sql
	 * @return
	 * @TODO 针对对象查询补全sql中的select * from table 部分,适度让代码中的sql简短一些(并不推荐)
	 */
	public static String completionSql(SqlToyContext sqlToyContext, Class entityClass, String sql) {
		if (null == entityClass || SqlConfigParseUtils.isNamedQuery(sql)) {
			return sql;
		}
		String sqlLow = sql.toLowerCase().trim();
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
	 * @param sql
	 * @param dbType
	 * @return
	 * @todo 判断sql中是否存在lock锁
	 */
	public static boolean hasLock(String sql, Integer dbType) {
		if (sql == null) {
			return false;
		}
		if (StringUtil.matches(sql, "(?i)\\s+for\\s+update")) {
			return true;
		}
		// sqlserver
		if (dbType != null && dbType.intValue() == DBType.SQLSERVER) {
			if (StringUtil.matches(sql,
					"(?i)with\\s*\\(\\s*(rowlock|xlock|updlock|holdlock|nolock|readpast)?\\,?\\s*(rowlock|xlock|updlock|holdlock|nolock|readpast)\\s*\\)")) {
				return true;
			}
		}
		return false;
	}

	/**
	 * @param defaultValue
	 * @return
	 * @todo 处理sqlserver default值为((value))问题
	 */
	public static String clearDefaultValue(String defaultValue) {
		if (defaultValue == null) {
			return null;
		}
		if ("".equals(defaultValue.trim())) {
			return defaultValue;
		}
		String result = defaultValue;
		// 针对postgresql
		if (result.indexOf("(") != -1 && result.indexOf(")") != -1 && result.indexOf("::") != -1) {
			result = result.substring(result.indexOf("(") + 1, result.indexOf("::"));
		}
		// postgresql
		if (result.indexOf("'") != -1 && result.indexOf("::") != -1) {
			result = result.substring(0, result.indexOf("::"));
		}
		if (result.startsWith("((") && result.endsWith("))")) {
			result = result.substring(2, result.length() - 2);
		}
		if (result.startsWith("(") && result.endsWith(")")) {
			result = result.substring(1, result.length() - 1);
		}
		if (result.startsWith("'") && result.endsWith("'")) {
			result = result.substring(1, result.length() - 1);
		}
		if (result.startsWith("\"") && result.endsWith("\"")) {
			result = result.substring(1, result.length() - 1);
		}
		return result.trim();
	}

	/**
	 * @param source
	 * @param target
	 * @return
	 * @todo 替换换行、回车、tab符号;\r 换行、\t tab符合、\n 回车
	 */
	public static String clearMistyChars(String source, String target) {
		if (source == null) {
			return null;
		}
		// 回车换行前后的空白也剔除
		return source.replaceAll("\\s*(\r|\n)\\s*", target).replaceAll("\t", target);
	}

	/**
	 * @param dbType
	 * @param fieldMeta
	 * @param createSqlTimeFields
	 * @return
	 * @TODO 获取数据库时间字符串
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
						|| dbType == DBType.POSTGRESQL15 || dbType == DBType.KINGBASE || dbType == DBType.DB2
						|| dbType == DBType.OCEANBASE || dbType == DBType.DORIS|| dbType == DBType.STARROCKS) {
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
	 * @param argValue
	 * @return
	 * @TODO 验证sql in的参数,要么是''形式的字符，要么是数字
	 */
	public static boolean validateInArg(String argValue) {
		// 判断是否有关键词
		boolean hasSqlKeyWord = StringUtil.matches(BLANK + argValue, SQL_INJECT_PATTERN);
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
				// 有关键词时，校验是否多个单引号，避免:''+(select field from table)+''模式
				if (hasSqlKeyWord && StringUtil.matchCnt(item, ONE_QUOTA, 0) > 2) {
					return false;
				}
			} else if (argType == 2) {
				if (!item.startsWith("\"") || !item.endsWith("\"")) {
					return false;
				}
				// 有关键词时，校验是否多个双引号，避免:""+(select field from table)+""模式
				if (hasSqlKeyWord && StringUtil.matchCnt(item, DOUBLE_QUOTA, 0) > 2) {
					return false;
				}
			} else if (!NumberUtil.isNumber(item)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * @param sqlArgValue
	 * @param addSingleQuotation 是否加单引号
	 * @return
	 * @TODO 将参数值转成字符传
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
	 * @param sqlArgValue
	 * @param preSql             前面的sql片段
	 * @param addSingleQuotation 是否加单引号
	 * @param dbType             数据库方言
	 * @return
	 * @TODO 将参数值转成字符传
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
	 * @param dateStr
	 * @param preSql
	 * @param type
	 * @param dbType
	 * @return
	 */
	private static String addDateFunction(String dateStr, String preSql, int type, int dbType) {
		// 前置sql片段不以<,=,<,between,and 作为结尾则不增加函数
		if (!StringUtil.matches(preSql, COMPARE_PATTERN)) {
			return dateStr;
		}
		int dateLength = dateStr.length() - 2;
		// oracle 和dm
		if (dbType == DBType.ORACLE || dbType == DBType.ORACLE11 || dbType == DBType.DM || dbType == DBType.OCEANBASE) {
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
				return "CAST(TO_DATE(" + dateStr + ",'HH24:MI:SS') as TIME)";
			}
			// 毫秒time
			if (type == 5) {
				if (dateLength > 12) {
					return "CAST(TO_DATE(" + dateStr + ",'HH24:MI:SS.FF') as TIME)";
				} else {
					return "CAST(TO_DATE(" + dateStr + ",'HH24:MI:SS.FF3') as TIME)";
				}
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
		// mysql和tidb
		if (dbType == DBType.MYSQL || dbType == DBType.MYSQL57 || dbType == DBType.TIDB || dbType == DBType.DORIS|| dbType == DBType.STARROCKS) {
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
		if (dbType == DBType.POSTGRESQL || dbType == DBType.POSTGRESQL15 || dbType == DBType.GAUSSDB
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
	 * @param array
	 * @return
	 * @TODO 组合in参数
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
	 * @param sql
	 * @param dbType
	 * @return
	 */
	public static String adjustMergeIntoSql(String sql, Integer dbType) {
		String sqlTrimLow = sql.toLowerCase().trim();
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
	 * 
	 * @param sql
	 * @param startRegex
	 * @param endRegex
	 * @param startIndex
	 * @return
	 */
	public static int getSymMarkIndexExcludeKeyWords(String sql, String startRegex, String endRegex, int startIndex) {
		Pattern endPattern = Pattern.compile(endRegex);
		String sqlLow = sql.toLowerCase();
		int startRegexIndex = StringUtil.matchIndex(sqlLow, startRegex, startIndex)[0];
		int endRegIndex = StringUtil.getSymMarkMatchIndex(startRegex, endRegex, sqlLow, startIndex);
		// 就一个endPattern直接返回
		if (endRegIndex > 0
				&& StringUtil.matchCnt(startIndex == 0 ? sqlLow : sqlLow.substring(startIndex), endPattern) == 1) {
			return endRegIndex;
		}
		// 如果有select concat(a,'(') from 判断就可能有问题
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
					if (!StringUtil.matches(tail, endPattern)) {
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
	 * @param sql
	 * @return
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
	 * @TODO 校验参数是否存在sql注入(即sql片段)
	 * @param sqlInjectionLevel
	 * @param paramValue
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
	 * @param paramValue
	 * @return
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
}
