package org.sagacity.sqltoy.model;

/**
 * 自定义 JDBC 类型常量 完整复制 java.sql.Types 原生常量 + 业务扩展类型
 * 
 * @date 2026-6-9
 */
public final class JdbcTypes {
	/**
	 * <P>
	 * The constant in the Java programming language, sometimes referred to as a
	 * type code, that identifies the generic SQL type {@code BIT}.
	 */
	public static final int BIT = -7;

	/**
	 * <P>
	 * The constant in the Java programming language, sometimes referred to as a
	 * type code, that identifies the generic SQL type {@code TINYINT}.
	 */
	public static final int TINYINT = -6;

	/**
	 * <P>
	 * The constant in the Java programming language, sometimes referred to as a
	 * type code, that identifies the generic SQL type {@code SMALLINT}.
	 */
	public static final int SMALLINT = 5;

	/**
	 * <P>
	 * The constant in the Java programming language, sometimes referred to as a
	 * type code, that identifies the generic SQL type {@code INTEGER}.
	 */
	public static final int INTEGER = 4;

	/**
	 * <P>
	 * The constant in the Java programming language, sometimes referred to as a
	 * type code, that identifies the generic SQL type {@code BIGINT}.
	 */
	public static final int BIGINT = -5;

	/**
	 * <P>
	 * The constant in the Java programming language, sometimes referred to as a
	 * type code, that identifies the generic SQL type {@code FLOAT}.
	 */
	public static final int FLOAT = 6;

	/**
	 * <P>
	 * The constant in the Java programming language, sometimes referred to as a
	 * type code, that identifies the generic SQL type {@code REAL}.
	 */
	public static final int REAL = 7;

	/**
	 * <P>
	 * The constant in the Java programming language, sometimes referred to as a
	 * type code, that identifies the generic SQL type {@code DOUBLE}.
	 */
	public static final int DOUBLE = 8;

	/**
	 * <P>
	 * The constant in the Java programming language, sometimes referred to as a
	 * type code, that identifies the generic SQL type {@code NUMERIC}.
	 */
	public static final int NUMERIC = 2;

	/**
	 * <P>
	 * The constant in the Java programming language, sometimes referred to as a
	 * type code, that identifies the generic SQL type {@code DECIMAL}.
	 */
	public static final int DECIMAL = 3;

	/**
	 * <P>
	 * The constant in the Java programming language, sometimes referred to as a
	 * type code, that identifies the generic SQL type {@code CHAR}.
	 */
	public static final int CHAR = 1;

	/**
	 * <P>
	 * The constant in the Java programming language, sometimes referred to as a
	 * type code, that identifies the generic SQL type {@code VARCHAR}.
	 */
	public static final int VARCHAR = 12;

	/**
	 * <P>
	 * The constant in the Java programming language, sometimes referred to as a
	 * type code, that identifies the generic SQL type {@code LONGVARCHAR}.
	 */
	public static final int LONGVARCHAR = -1;

	/**
	 * <P>
	 * The constant in the Java programming language, sometimes referred to as a
	 * type code, that identifies the generic SQL type {@code DATE}.
	 */
	public static final int DATE = 91;

	/**
	 * <P>
	 * The constant in the Java programming language, sometimes referred to as a
	 * type code, that identifies the generic SQL type {@code TIME}.
	 */
	public static final int TIME = 92;

	/**
	 * <P>
	 * The constant in the Java programming language, sometimes referred to as a
	 * type code, that identifies the generic SQL type {@code TIMESTAMP}.
	 */
	public static final int TIMESTAMP = 93;

	/**
	 * <P>
	 * The constant in the Java programming language, sometimes referred to as a
	 * type code, that identifies the generic SQL type {@code BINARY}.
	 */
	public static final int BINARY = -2;

	/**
	 * <P>
	 * The constant in the Java programming language, sometimes referred to as a
	 * type code, that identifies the generic SQL type {@code VARBINARY}.
	 */
	public static final int VARBINARY = -3;

	/**
	 * <P>
	 * The constant in the Java programming language, sometimes referred to as a
	 * type code, that identifies the generic SQL type {@code LONGVARBINARY}.
	 */
	public static final int LONGVARBINARY = -4;

	/**
	 * <P>
	 * The constant in the Java programming language that identifies the generic SQL
	 * value {@code NULL}.
	 */
	public static final int NULL = 0;

	/**
	 * The constant in the Java programming language that indicates that the SQL
	 * type is database-specific and gets mapped to a Java object that can be
	 * accessed via the methods {@code getObject} and {@code setObject}.
	 */
	public static final int OTHER = 1111;

	/**
	 * The constant in the Java programming language, sometimes referred to as a
	 * type code, that identifies the generic SQL type {@code JAVA_OBJECT}.
	 * 
	 * @since 1.2
	 */
	public static final int JAVA_OBJECT = 2000;

	/**
	 * The constant in the Java programming language, sometimes referred to as a
	 * type code, that identifies the generic SQL type {@code DISTINCT}.
	 * 
	 * @since 1.2
	 */
	public static final int DISTINCT = 2001;

	/**
	 * The constant in the Java programming language, sometimes referred to as a
	 * type code, that identifies the generic SQL type {@code STRUCT}.
	 * 
	 * @since 1.2
	 */
	public static final int STRUCT = 2002;

	/**
	 * The constant in the Java programming language, sometimes referred to as a
	 * type code, that identifies the generic SQL type {@code ARRAY}.
	 * 
	 * @since 1.2
	 */
	public static final int ARRAY = 2003;

	/**
	 * The constant in the Java programming language, sometimes referred to as a
	 * type code, that identifies the generic SQL type {@code BLOB}.
	 * 
	 * @since 1.2
	 */
	public static final int BLOB = 2004;

	/**
	 * The constant in the Java programming language, sometimes referred to as a
	 * type code, that identifies the generic SQL type {@code CLOB}.
	 * 
	 * @since 1.2
	 */
	public static final int CLOB = 2005;

	/**
	 * The constant in the Java programming language, sometimes referred to as a
	 * type code, that identifies the generic SQL type {@code REF}.
	 * 
	 * @since 1.2
	 */
	public static final int REF = 2006;

	/**
	 * The constant in the Java programming language, sometimes referred to as a
	 * type code, that identifies the generic SQL type {@code DATALINK}.
	 *
	 * @since 1.4
	 */
	public static final int DATALINK = 70;

	/**
	 * The constant in the Java programming language, sometimes referred to as a
	 * type code, that identifies the generic SQL type {@code BOOLEAN}.
	 *
	 * @since 1.4
	 */
	public static final int BOOLEAN = 16;

	// ------------------------- JDBC 4.0 -----------------------------------

	/**
	 * The constant in the Java programming language, sometimes referred to as a
	 * type code, that identifies the generic SQL type {@code ROWID}
	 *
	 * @since 1.6
	 *
	 */
	public static final int ROWID = -8;

	/**
	 * The constant in the Java programming language, sometimes referred to as a
	 * type code, that identifies the generic SQL type {@code NCHAR}
	 *
	 * @since 1.6
	 */
	public static final int NCHAR = -15;

	/**
	 * The constant in the Java programming language, sometimes referred to as a
	 * type code, that identifies the generic SQL type {@code NVARCHAR}.
	 *
	 * @since 1.6
	 */
	public static final int NVARCHAR = -9;

	/**
	 * The constant in the Java programming language, sometimes referred to as a
	 * type code, that identifies the generic SQL type {@code LONGNVARCHAR}.
	 *
	 * @since 1.6
	 */
	public static final int LONGNVARCHAR = -16;

	/**
	 * The constant in the Java programming language, sometimes referred to as a
	 * type code, that identifies the generic SQL type {@code NCLOB}.
	 *
	 * @since 1.6
	 */
	public static final int NCLOB = 2011;

	/**
	 * The constant in the Java programming language, sometimes referred to as a
	 * type code, that identifies the generic SQL type {@code XML}.
	 *
	 * @since 1.6
	 */
	public static final int SQLXML = 2009;

	// --------------------------JDBC 4.2 -----------------------------

	/**
	 * The constant in the Java programming language, sometimes referred to as a
	 * type code, that identifies the generic SQL type {@code REF CURSOR}.
	 *
	 * @since 1.8
	 */
	public static final int REF_CURSOR = 2012;

	/**
	 * The constant in the Java programming language, sometimes referred to as a
	 * type code, that identifies the generic SQL type {@code TIME WITH TIMEZONE}.
	 *
	 * @since 1.8
	 */
	public static final int TIME_WITH_TIMEZONE = 2013;

	/**
	 * The constant in the Java programming language, sometimes referred to as a
	 * type code, that identifies the generic SQL type
	 * {@code TIMESTAMP WITH TIMEZONE}.
	 *
	 * @since 1.8
	 */
	public static final int TIMESTAMP_WITH_TIMEZONE = 2014;

	// JDK 26 新增：JSON 类型 (2016)
	public static final int JSON = 2016;
	public static final int JSONB = 2017;

	// ===================== 自定义扩展类型（业务/数据库专属） =====================
	// 建议从 3000 开始，避开 JDBC 官方预留区间，防止版本更新冲突

	public static final int GEOMETRY = 3001;
	public static final int HSTORE = 3002;
	public static final int UUID = 3003;

	// 私有构造：禁止实例化、反射构造
	private JdbcTypes() {
		throw new AssertionError("No instance for JdbcTypes");
	}
}
