package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.sql.Types;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.FieldMeta;
import org.sagacity.sqltoy.model.IgnoreCaseSet;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * getDBTime分支矩阵穷举:按dbType×字段类型锁定各库当前时间表达式。
 * 契约:字段在createSqlTimeFields内且为时间类型才返回表达式,否则null;
 * CLICKHOUSE恒为now();TIME按库族分current_time/now()/getdate()/current_timestamp;
 * localdate按库分current_date/getdate();其余timestamp形态恒current_timestamp。
 */
public class SqlUtilGetDBTimeTest {

	private static FieldMeta field(String name, int type, String fieldType) {
		FieldMeta fm = new FieldMeta();
		fm.setFieldName(name);
		fm.setType(type);
		fm.setFieldType(fieldType);
		return fm;
	}

	private static IgnoreCaseSet fields(String name) {
		IgnoreCaseSet set = new IgnoreCaseSet();
		set.add(name);
		return set;
	}

	private static final String TS_FIELD = "create_time";
	private static final String DATE_FIELD = "biz_date";
	private static final String TIME_FIELD = "start_time";

	// ---------------- 守卫分支 ----------------

	@Test
	public void nullArgsReturnNull() {
		assertNull(SqlUtil.getDBTime(null, null, null));
		FieldMeta fm = field(TS_FIELD, Types.TIMESTAMP, "java.time.LocalDateTime");
		assertNull(SqlUtil.getDBTime(DBType.MYSQL, null, fields(TS_FIELD)), "fieldMeta为null返回null");
		assertNull(SqlUtil.getDBTime(DBType.MYSQL, fm, null), "字段集合为null返回null");
		assertNull(SqlUtil.getDBTime(DBType.MYSQL, fm, new IgnoreCaseSet()), "字段集合为空返回null");
	}

	@Test
	public void fieldNotInSetReturnsNull() {
		assertNull(SqlUtil.getDBTime(DBType.MYSQL, field(TS_FIELD, Types.TIMESTAMP, "java.time.LocalDateTime"),
				fields("other_field")), "字段不在集合内返回null");
	}

	@Test
	public void nonTimeTypeReturnsNull() {
		FieldMeta strField = field("name", Types.VARCHAR, "java.lang.String");
		assertNull(SqlUtil.getDBTime(DBType.MYSQL, strField, fields("name")), "非时间类型返回null");
		FieldMeta intField = field("amount", Types.DECIMAL, "java.math.BigDecimal");
		assertNull(SqlUtil.getDBTime(DBType.ORACLE, intField, fields("amount")), "数值类型返回null");
	}

	// ---------------- timestamp形态(默认current_timestamp) ----------------

	@Test
	public void timestampFormDefaultsToCurrentTimestamp() {
		FieldMeta fm = field(TS_FIELD, Types.TIMESTAMP, "java.time.LocalDateTime");
		IgnoreCaseSet set = fields(TS_FIELD);
		// update 2026-9-15 CH单列(任何时间形态恒now()),其余库current_timestamp
		for (int dbType : new int[] { DBType.MYSQL, DBType.ORACLE, DBType.ORACLE11, DBType.POSTGRESQL,
				DBType.POSTGRESQL14, DBType.SQLSERVER, DBType.DB2, DBType.OCEANBASE, DBType.DM, DBType.KINGBASE,
				DBType.DORIS, DBType.STARROCKS, DBType.TIDB, DBType.HANA, DBType.SQLITE, DBType.H2,
				DBType.OPENGAUSS, DBType.VASTBASE, DBType.MOGDB, DBType.STARDB, DBType.OSCAR, DBType.GAUSSDB }) {
			assertEquals("current_timestamp", SqlUtil.getDBTime(dbType, fm, set), "dbType=" + dbType);
		}
		assertEquals("now()", SqlUtil.getDBTime(DBType.CLICKHOUSE, fm, set), "CH恒now()");
	}

	// ---------------- localdate形态(current_date / getdate) ----------------

	@Test
	public void localDateFormCurrentDateExceptSqlserver() {
		FieldMeta fm = field(DATE_FIELD, Types.DATE, "java.time.LocalDate");
		IgnoreCaseSet set = fields(DATE_FIELD);
		for (int dbType : new int[] { DBType.MYSQL, DBType.ORACLE, DBType.ORACLE11, DBType.POSTGRESQL,
				DBType.POSTGRESQL14, DBType.DB2, DBType.OCEANBASE, DBType.DM, DBType.KINGBASE, DBType.DORIS,
				DBType.STARROCKS, DBType.TIDB, DBType.HANA, DBType.SQLITE, DBType.H2,
				DBType.OPENGAUSS, DBType.VASTBASE, DBType.MOGDB, DBType.STARDB, DBType.OSCAR, DBType.GAUSSDB }) {
			assertEquals("current_date", SqlUtil.getDBTime(dbType, fm, set), "dbType=" + dbType);
		}
		assertEquals("getdate()", SqlUtil.getDBTime(DBType.SQLSERVER, fm, set), "sqlserver localdate走getdate()");
		assertEquals("now()", SqlUtil.getDBTime(DBType.CLICKHOUSE, fm, set), "CH恒now()");
	}

	// ---------------- time形态(四类库族) ----------------

	@Test
	public void timeFormCurrentTimeFamily() {
		FieldMeta fm = field(TIME_FIELD, Types.TIME, "java.time.LocalTime");
		IgnoreCaseSet set = fields(TIME_FIELD);
		for (int dbType : new int[] { DBType.MYSQL, DBType.MYSQL57, DBType.TIDB, DBType.SQLITE, DBType.H2,
				DBType.POSTGRESQL, DBType.POSTGRESQL14, DBType.KINGBASE, DBType.DB2, DBType.OCEANBASE, DBType.DORIS,
				DBType.STARROCKS }) {
			assertEquals("current_time", SqlUtil.getDBTime(dbType, fm, set), "dbType=" + dbType);
		}
	}

	@Test
	public void timeFormNowFamily() {
		FieldMeta fm = field(TIME_FIELD, Types.TIME, "java.time.LocalTime");
		IgnoreCaseSet set = fields(TIME_FIELD);
		for (int dbType : new int[] { DBType.GAUSSDB, DBType.OPENGAUSS, DBType.MOGDB, DBType.VASTBASE,
				DBType.STARDB, DBType.OSCAR }) {
			assertEquals("now()", SqlUtil.getDBTime(dbType, fm, set), "dbType=" + dbType);
		}
	}

	@Test
	public void timeFormSqlserverAndOthers() {
		FieldMeta fm = field(TIME_FIELD, Types.TIME, "java.time.LocalTime");
		IgnoreCaseSet set = fields(TIME_FIELD);
		assertEquals("getdate()", SqlUtil.getDBTime(DBType.SQLSERVER, fm, set), "sqlserver time走getdate()");
		assertEquals("current_timestamp", SqlUtil.getDBTime(DBType.ORACLE, fm, set), "oracle time走current_timestamp");
		assertEquals("current_timestamp", SqlUtil.getDBTime(DBType.DM, fm, set), "dm time走current_timestamp");
		assertEquals("current_timestamp", SqlUtil.getDBTime(DBType.HANA, fm, set), "hana time走current_timestamp");
	}

	@Test
	public void timeWithTimezoneFollowsTimeBranch() {
		FieldMeta fm = field(TIME_FIELD, Types.TIME_WITH_TIMEZONE, "java.time.OffsetTime");
		IgnoreCaseSet set = fields(TIME_FIELD);
		assertEquals("current_time", SqlUtil.getDBTime(DBType.POSTGRESQL, fm, set), "带时区time走time分支");
		assertEquals("getdate()", SqlUtil.getDBTime(DBType.SQLSERVER, fm, set), "带时区time sqlserver");
	}

	@Test
	public void timestampWithTimezoneFollowsTimestampBranch() {
		FieldMeta fm = field(TS_FIELD, Types.TIMESTAMP_WITH_TIMEZONE, "java.time.OffsetDateTime");
		assertEquals("current_timestamp",
				SqlUtil.getDBTime(DBType.POSTGRESQL, fm, fields(TS_FIELD)), "带时区timestamp走current_timestamp");
	}

	@Test
	public void fieldTypeStringMatchTimeForms() {
		// update 2026-9-15 修复大小写敏感后:jdbcType为TIMESTAMP但fieldType为驼峰LocalTime/
		// LocalTime/LocalDate时走对应形态分支(修复前全小写常量比较永不成立,统一落current_timestamp)
		FieldMeta localTime = field("start_time", Types.TIMESTAMP, "java.time.LocalTime");
		assertEquals("current_time", SqlUtil.getDBTime(DBType.MYSQL, localTime, fields("start_time")),
				"localtime驼峰形态走time分支");
		FieldMeta sqlTime = field("start_time", Types.TIMESTAMP, "java.sql.Time");
		assertEquals("current_time", SqlUtil.getDBTime(DBType.POSTGRESQL, sqlTime, fields("start_time")),
				"java.sql.Time走time分支");
		FieldMeta localDate = field("biz_date", Types.TIMESTAMP, "java.time.LocalDate");
		assertEquals("current_date", SqlUtil.getDBTime(DBType.MYSQL, localDate, fields("biz_date")),
				"localdate驼峰形态走date分支");
	}

	@Test
	public void fieldNameMatchIsCaseInsensitive() {
		FieldMeta fm = field("CREATE_TIME", Types.TIMESTAMP, "java.time.LocalDateTime");
		assertNotNull(SqlUtil.getDBTime(DBType.MYSQL, fm, fields("create_time")), "字段名大小写无关匹配");
	}

}
