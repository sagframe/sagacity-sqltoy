package org.sagacity.sqltoy.dialect.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.sql.PreparedStatement;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.FieldMeta;
import org.sagacity.sqltoy.model.ColumnMeta;
import org.sagacity.sqltoy.model.DBProfile;
import org.sagacity.sqltoy.model.JdbcTypes;
import org.sagacity.sqltoy.plugins.ddl.DDLUtils;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;
import org.sagacity.sqltoy.utils.SqlUtil;

/**
 * update 2026-11-22 跨库部署三缺口回归锁死(真库CrossDbDeployMatrixTest暴露):
 * (1)dm geometry DDL须ST_GEOMETRY(DMGEO,与ST_GeomFromText绑定链一致),pg源nativeType="geometry"
 * 直通dm报-3719非法基类名;(2)h2无vector类型,DDL以VARCHAR承载,merge using select裸?报
 * "Unknown data type",须cast(? as VARCHAR);(3)vastbase驱动setBoolean发smallint,boolean列拒收
 * 且cast(smallint as boolean)非法,须setString文本+SQL层cast(? as boolean)。
 *
 * @author zsy 2026-11-22
 */
public class CrossDbDialectFixTest {

	private static ColumnMeta col(int dataType, String nativeType) {
		ColumnMeta c = new ColumnMeta();
		c.setDataType(dataType);
		c.setNativeType(nativeType);
		return c;
	}

	private static FieldMeta field(int jdbcType) {
		FieldMeta f = new FieldMeta();
		f.setColumnName("vec_col");
		f.setType(jdbcType);
		return f;
	}

	/** dm geometry DDL→ST_GEOMETRY(优先于pg源nativeType直通);oracle/pg精确控制不受影响 */
	@Test
	public void ddlGeometryDmStGeometry() {
		assertEquals("ST_GEOMETRY", DDLUtils.convertType(col(JdbcTypes.GEOMETRY, "geometry"), DBType.DM),
				"dm geometry须ST_GEOMETRY");
		assertEquals("ST_GEOMETRY", DDLUtils.convertType(col(JdbcTypes.GEOMETRY, null), DBType.DM), "dm blank nativeType");
		assertEquals("ST_GEOMETRY", DDLUtils.convertType(col(JdbcTypes.GEOMETRY, "geometry(Point,4326)"), DBType.DM),
				"dm 带精度外部拼写归一ST_GEOMETRY");
		// update 2026-11-22 显式nativeType先判定DM是否支持:真库实测(DM8 20260710+DMGEO)仅ST_GEOMETRY
		// 可建表,DMGEO.ST_GEOMETRY(带schema)/SDO_GEOMETRY/GEOMETRY/GEOGRAPHY/POINT 均-3719非法基类名,
		// 故除ST_GEOMETRY外一律转ST_GEOMETRY;显式ST_GEOMETRY保留原拼写
		assertEquals("ST_GEOMETRY", DDLUtils.convertType(col(JdbcTypes.GEOMETRY, "SDO_GEOMETRY"), DBType.DM),
				"dm不支持的SDO_GEOMETRY须转ST_GEOMETRY(-3719)");
		assertEquals("ST_GEOMETRY", DDLUtils.convertType(col(JdbcTypes.GEOMETRY, "DMGEO.ST_GEOMETRY"), DBType.DM),
				"dm带schema类型不支持列定义,转ST_GEOMETRY(-3719)");
		assertEquals("ST_GEOMETRY", DDLUtils.convertType(col(JdbcTypes.GEOMETRY, "MDSYS.SDO_GEOMETRY"), DBType.DM),
				"dm不支持的schema类型转ST_GEOMETRY");
		assertEquals("ST_GEOMETRY", DDLUtils.convertType(col(JdbcTypes.GEOMETRY, "ST_GEOMETRY"), DBType.DM),
				"dm支持的ST_GEOMETRY保留");
		assertEquals("ST_GEOMETRY", DDLUtils.convertType(col(JdbcTypes.GEOMETRY, "st_geometry"), DBType.DM),
				"类型名大小写不敏感,统一输出ST_GEOMETRY");
		// oracle blank→SDO_GEOMETRY(既有);pg显式精度nativeType直通(精确控制保留)
		assertEquals("SDO_GEOMETRY", DDLUtils.convertType(col(JdbcTypes.GEOMETRY, null), DBType.ORACLE), "oracle SDO");
		assertEquals("geometry(Point,4326)",
				DDLUtils.convertType(col(JdbcTypes.GEOMETRY, "geometry(Point,4326)"), DBType.POSTGRESQL), "pg精度直通");
	}

	/** h2 vector merge cast→cast(? as VARCHAR)(与DDL VARCHAR承载一致,避免裸?无法定型) */
	@Test
	public void h2VectorMergeCastVarchar() {
		StringBuilder sb = new StringBuilder();
		H2DialectUtils.wrapSelectFields(sb, "vec_col", field(JdbcTypes.VECTOR));
		assertTrue(sb.toString().contains("cast(? as VARCHAR)"), "h2 vector应cast VARCHAR: " + sb);
	}

	private static DBProfile profile(int dbType) {
		return new DBProfile("jdbc:probe", "probe", dbType, null, 0, null, null, false);
	}

	/**
	 * vastbase boolean绑定→setObject("true"/"false",Types.OTHER):驱动setBoolean发smallint(列赋值拒收)、
	 * setString谓词报invalid input syntax;unknown文本四种上下文(insert/update/merge/where)全通(真库矩阵);
	 * pg保持setBoolean
	 */
	@Test
	public void vastbaseBooleanBindingUnknownText() throws Exception {
		for (boolean val : new boolean[] { true, false }) {
			AtomicReference<String> method = new AtomicReference<>();
			AtomicReference<Object> arg = new AtomicReference<>();
			AtomicReference<Integer> sqlType = new AtomicReference<>();
			PreparedStatement pst = (PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(),
					new Class[] { PreparedStatement.class }, (proxy, m, a) -> {
						if ("setObject".equals(m.getName()) || "setString".equals(m.getName())
								|| "setBoolean".equals(m.getName())) {
							method.set(m.getName());
							arg.set(a[1]);
							if (a.length > 2 && a[2] instanceof Integer) {
								sqlType.set((Integer) a[2]);
							}
						}
						Class<?> rt = m.getReturnType();
						if (rt == boolean.class) {
							return false;
						}
						if (rt.isPrimitive() && rt != void.class) {
							return 0;
						}
						return null;
					});
			SqlUtil.setParamValue(null, null, profile(DBType.VASTBASE), pst, val, java.sql.Types.BOOLEAN, 1);
			assertEquals("setObject", method.get(), "vastbase boolean应setObject(unknown文本)");
			assertEquals(val ? "true" : "false", arg.get(), "vastbase boolean文本");
			assertEquals(Integer.valueOf(java.sql.Types.OTHER), sqlType.get(), "vastbase boolean须Types.OTHER");
		}
		// null绑定:setNull(OTHER)(setNull(VARCHAR)在无cast的insert/谓词路径被拒,真库矩阵)
		AtomicReference<Integer> nullType = new AtomicReference<>();
		PreparedStatement pst = (PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class[] { PreparedStatement.class }, (proxy, m, a) -> {
					if ("setNull".equals(m.getName())) {
						nullType.set((Integer) a[1]);
					}
					Class<?> rt = m.getReturnType();
					if (rt == boolean.class) {
						return false;
					}
					if (rt.isPrimitive() && rt != void.class) {
						return 0;
					}
					return null;
				});
		SqlUtil.setParamValue(null, null, profile(DBType.VASTBASE), pst, null, java.sql.Types.BOOLEAN, 1);
		assertEquals(Integer.valueOf(java.sql.Types.OTHER), nullType.get(), "vastbase boolean null须setNull(OTHER)");
	}

	/** pg boolean绑定保持setBoolean(不受vastbase修复影响) */
	@Test
	public void pgBooleanBindingUnchanged() throws Exception {
		AtomicReference<String> method = new AtomicReference<>();
		PreparedStatement pst = (PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class[] { PreparedStatement.class }, (proxy, m, a) -> {
					if ("setString".equals(m.getName()) || "setBoolean".equals(m.getName())) {
						method.set(m.getName());
					}
					Class<?> rt = m.getReturnType();
					if (rt == boolean.class) {
						return false;
					}
					if (rt.isPrimitive() && rt != void.class) {
						return 0;
					}
					return null;
				});
		SqlUtil.setParamValue(null, null, profile(DBType.POSTGRESQL), pst, Boolean.TRUE, java.sql.Types.BOOLEAN, 1);
		assertEquals("setBoolean", method.get(), "pg boolean应保持setBoolean");
	}

	/** db2 OffsetDateTime绑定→setObject(LocalDateTime)(驱动拒ODT报-4461,真库实证);保墙钟时间 */
	@Test
	public void db2OffsetDateTimeBindingLocal() throws Exception {
		java.time.OffsetDateTime odt = java.time.OffsetDateTime.parse("2026-01-15T10:30:00+08:00");
		AtomicReference<Object> arg = new AtomicReference<>();
		PreparedStatement pst = (PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class[] { PreparedStatement.class }, (proxy, m, a) -> {
					if ("setObject".equals(m.getName())) {
						arg.set(a[1]);
					}
					Class<?> rt = m.getReturnType();
					if (rt == boolean.class) {
						return false;
					}
					if (rt.isPrimitive() && rt != void.class) {
						return 0;
					}
					return null;
				});
		SqlUtil.setParamValue(null, null, profile(DBType.DB2), pst, odt, java.sql.Types.TIMESTAMP_WITH_TIMEZONE, 1);
		assertTrue(arg.get() instanceof java.time.LocalDateTime, "db2应转LocalDateTime: " + arg.get());
		assertEquals(odt.toLocalDateTime(), arg.get(), "保墙钟时间");
	}

	/** oracle OTHER字段(数组族等未定型)null绑定→setNull(VARCHAR)(拒1111报ORA-17004,真库实证) */
	@Test
	public void oracleOtherNullUsesVarchar() throws Exception {
		AtomicReference<Integer> captured = new AtomicReference<>(Integer.MIN_VALUE);
		PreparedStatement pst = (PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class[] { PreparedStatement.class }, (proxy, m, a) -> {
					if ("setNull".equals(m.getName()) && a != null && a.length == 2) {
						captured.set((Integer) a[1]);
					}
					Class<?> rt = m.getReturnType();
					if (rt == boolean.class) {
						return false;
					}
					if (rt.isPrimitive() && rt != void.class) {
						return 0;
					}
					return null;
				});
		SqlUtil.setParamValue(null, null, profile(DBType.ORACLE), pst, null, java.sql.Types.OTHER, 1);
		assertEquals(java.sql.Types.VARCHAR, captured.get(), "oracle OTHER null应VARCHAR而非1111");
	}

	/** h2 vector承载VARCHAR不以维度作字符长度(维度3≠VARCHAR(3),真库实证'[1,2,3]'7字符被截断) */
	@Test
	public void h2VectorCarrierNotDimLength() {
		ColumnMeta c = new ColumnMeta();
		c.setDataType(JdbcTypes.VECTOR);
		c.setColumnSize(3);
		assertEquals("VARCHAR", DDLUtils.convertType(c, DBType.H2), "h2 vector承载VARCHAR(不以维度作长度)");
	}

	/** pg内核系(含HIGHGO)BLOB byte[]绑定→setBytes,不走createBlob(pgjdbc未实现致连接broken,真库实证) */
	@Test
	public void pgKernelBlobByteUsesSetBytes() throws Exception {
		AtomicReference<String> pstMethod = new AtomicReference<>();
		AtomicReference<Boolean> createBlobCalled = new AtomicReference<>(false);
		java.sql.Connection conn = (java.sql.Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class[] { java.sql.Connection.class }, (proxy, m, a) -> {
					if ("createBlob".equals(m.getName())) {
						createBlobCalled.set(true);
						throw new java.sql.SQLFeatureNotSupportedException("createBlob not implemented");
					}
					Class<?> rt = m.getReturnType();
					if (rt == boolean.class) {
						return false;
					}
					if (rt.isPrimitive() && rt != void.class) {
						return 0;
					}
					return null;
				});
		PreparedStatement pst = (PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class[] { PreparedStatement.class }, (proxy, m, a) -> {
					if ("setBytes".equals(m.getName()) || "setBlob".equals(m.getName())) {
						pstMethod.set(m.getName());
					}
					Class<?> rt = m.getReturnType();
					if (rt == boolean.class) {
						return false;
					}
					if (rt.isPrimitive() && rt != void.class) {
						return 0;
					}
					return null;
				});
		SqlUtil.setParamValue(null, conn, profile(DBType.HIGHGO), pst, new byte[] { 1, 2, 3 }, java.sql.Types.BLOB, 1);
		assertEquals("setBytes", pstMethod.get(), "HIGHGO BLOB byte[]应setBytes");
		org.junit.jupiter.api.Assertions.assertFalse(createBlobCalled.get(), "pg内核系不应createBlob");
	}

	/** doris/starrocks BLOB DDL承载:doris无BLOB且不支持VARBINARY建表→STRING;starrocks无BLOB→VARBINARY(真库实证) */
	@Test
	public void dorisStarRocksBlobCarrier() {
		ColumnMeta c = new ColumnMeta();
		c.setDataType(java.sql.Types.BLOB);
		assertEquals("STRING", DDLUtils.convertType(c, DBType.DORIS), "doris BLOB→STRING");
		assertEquals("VARBINARY", DDLUtils.convertType(c, DBType.STARROCKS), "starrocks BLOB→VARBINARY");
	}

	/**
	 * vastbase boolean null绑定→setNull(OTHER):setNull(BOOLEAN)经驱动发smallint拒收、
	 * setNull(VARCHAR)在无cast的insert/谓词路径报"column boolean but expression character varying"
	 * (真库矩阵四五种形态),unknown 的 setNull(OTHER)在 insert null 与 where 谓词均可行
	 */
	@Test
	public void vastbaseBooleanNullUsesOther() throws Exception {
		AtomicReference<Integer> captured = new AtomicReference<>(Integer.MIN_VALUE);
		PreparedStatement pst = (PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class[] { PreparedStatement.class }, (proxy, m, a) -> {
					if ("setNull".equals(m.getName()) && a != null && a.length == 2) {
						captured.set((Integer) a[1]);
					}
					Class<?> rt = m.getReturnType();
					if (rt == boolean.class) {
						return false;
					}
					if (rt.isPrimitive() && rt != void.class) {
						return 0;
					}
					return null;
				});
		SqlUtil.setParamValue(null, null, profile(DBType.VASTBASE), pst, null, java.sql.Types.BOOLEAN, 1);
		assertEquals(java.sql.Types.OTHER, captured.get(), "vastbase boolean null应setNull(OTHER)");
	}

	/** BSON族cast与DDL同口径(pg内核系DDL建BSON列,cast亦按bson词面定型;BSON为mongo类型,关系混用属边缘场景) */
	@Test
	public void bsonCastAlignsWithDdl() {
		assertEquals("BSON", DDLUtils.convertType(col(java.sql.Types.OTHER, "bson"), DBType.POSTGRESQL), "pg bson DDL→BSON");
		assertEquals("bson", org.sagacity.sqltoy.utils.NativeTypeUtils.specialCastType(DBType.POSTGRESQL, "bson", "bson"),
				"pg bson cast与DDL同口径bson");
	}

	/** H2 timestamptz DDL须TIMESTAMP WITH TIME ZONE(此前落else TIMESTAMP静默丢时区,真库实证;与merge cast同口径) */
	@Test
	public void h2TimestampTzDdlPreservesTimezone() {
		assertEquals("TIMESTAMP WITH TIME ZONE",
				DDLUtils.convertType(col(java.sql.Types.TIMESTAMP_WITH_TIMEZONE, null), DBType.H2),
				"H2 timestamptz DDL须保时区");
	}

	/** pg内核系geometry外部拼写DDL归一geometry(与specialCastType同口径),geometry/geography精度保留 */
	@Test
	public void pgGeometryExternalSpellingNormalized() {
		assertEquals("geometry", DDLUtils.convertType(col(JdbcTypes.GEOMETRY, "ST_GEOMETRY"), DBType.POSTGRESQL),
				"pg ST_GEOMETRY外部拼写归一geometry");
		assertEquals("geometry", DDLUtils.convertType(col(JdbcTypes.GEOMETRY, "SDO_GEOMETRY"), DBType.POSTGRESQL),
				"pg SDO_GEOMETRY外部拼写归一geometry");
		assertEquals("geometry(Point,4326)",
				DDLUtils.convertType(col(JdbcTypes.GEOMETRY, "geometry(Point,4326)"), DBType.POSTGRESQL),
				"pg geometry精度拼写保留");
		assertEquals("geography", DDLUtils.convertType(col(JdbcTypes.GEOMETRY, "geography"), DBType.POSTGRESQL),
				"pg geography拼写保留");
	}
}