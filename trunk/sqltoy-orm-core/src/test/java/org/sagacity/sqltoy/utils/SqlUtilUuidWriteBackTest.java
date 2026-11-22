package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.sql.ResultSet;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.FieldMeta;
import org.sagacity.sqltoy.model.DBProfile;
import org.sagacity.sqltoy.model.JdbcTypes;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * update 2026-11-22 uuid类型ResultSet回写回归锁死:此前SqlUtilsExt.resultUpdate无UUID分派,
 * String承载的uuid(jdbcType=3003)落常规String分支rs.updateString,pg内核系原生uuid列在
 * CONCUR_UPDATABLE结果集updateRow时报"column is of type uuid but expression is of type
 * character varying"(驱动层A/B真库实证:updateString/updateObject(str)/updateObject(str,OTHER)
 * 均FAIL,唯updateObject(java.util.UUID)可行)。修复新增SqlUtil.updateUuidValue,pg内核系+h2走
 * updateObject(UUID),其余varchar(36)族承载列维持updateString,与insert/setNull口径一致。
 * 以记录型ResultSet代理捕获updateXxx实参,无需真库即可锁死分派。
 *
 * @author zsy 2026-11-22
 */
public class SqlUtilUuidWriteBackTest {

	private static final String UUID_STR = "123e4567-e89b-12d3-a456-426614174000";

	private static DBProfile profile(int dbType) {
		return new DBProfile("jdbc:probe", "probe", dbType, null, 0, null, null, false);
	}

	private static FieldMeta uuidField() {
		FieldMeta fm = new FieldMeta();
		fm.setColumnName("uuid_col");
		fm.setType(JdbcTypes.UUID);
		return fm;
	}

	/** 记录型ResultSet:捕获最近一次updateString/updateObject的实参 */
	private static final class Recorder {
		int updateStringCalls = 0;
		int updateObjectCalls = 0;
		String lastMethod = null;
		Object lastValue = null;
		int lastTargetSqlType = Integer.MIN_VALUE;
	}

	private static ResultSet rs(Recorder rec) {
		return (ResultSet) Proxy.newProxyInstance(SqlUtilUuidWriteBackTest.class.getClassLoader(),
				new Class[] { ResultSet.class }, (proxy, method, args) -> {
					String name = method.getName();
					if ("updateString".equals(name)) {
						rec.updateStringCalls++;
						rec.lastMethod = name;
						rec.lastValue = args[1];
						return null;
					}
					if ("updateObject".equals(name)) {
						rec.updateObjectCalls++;
						rec.lastMethod = name;
						rec.lastValue = args[1];
						if (args.length >= 3) {
							rec.lastTargetSqlType = (Integer) args[2];
						}
						return null;
					}
					Class<?> rt = method.getReturnType();
					if (rt == boolean.class) {
						return false;
					}
					if (rt.isPrimitive() && rt != void.class) {
						return 0;
					}
					return null;
				});
	}

	/** updateUuidValue:pg内核系/h2按原生uuid走updateObject(UUID) */
	@Test
	public void updateUuidValuePgKernelAndH2UsesUpdateObjectUuid() throws Exception {
		int[] pgKernel = { DBType.POSTGRESQL, DBType.POSTGRESQL14, DBType.OPENGAUSS, DBType.GAUSSDB, DBType.MOGDB,
				DBType.STARDB, DBType.OSCAR, DBType.VASTBASE, DBType.KINGBASE, DBType.HIGHGO, DBType.H2 };
		for (int dbType : pgKernel) {
			Recorder rec = new Recorder();
			SqlUtil.updateUuidValue(profile(dbType), rs(rec), "uuid_col", UUID_STR);
			assertEquals(1, rec.updateObjectCalls, "dbType=" + dbType + "应updateObject");
			assertEquals(0, rec.updateStringCalls, "dbType=" + dbType + "不应updateString");
			assertTrue(rec.lastValue instanceof java.util.UUID,
					"dbType=" + dbType + "回写值应为java.util.UUID,实为" + rec.lastValue.getClass());
			assertEquals(java.util.UUID.fromString(UUID_STR), rec.lastValue, "dbType=" + dbType + "uuid值");
		}
	}

	/** updateUuidValue:其余varchar(36)/char(36)/uniqueidentifier承载列维持updateString */
	@Test
	public void updateUuidValueCarrierUsesUpdateString() throws Exception {
		int[] carriers = { DBType.MYSQL, DBType.MYSQL57, DBType.TIDB, DBType.OCEANBASE, DBType.SQLSERVER,
				DBType.ORACLE, DBType.ORACLE11, DBType.DM, DBType.DB2 };
		for (int dbType : carriers) {
			Recorder rec = new Recorder();
			SqlUtil.updateUuidValue(profile(dbType), rs(rec), "uuid_col", UUID_STR);
			assertEquals(1, rec.updateStringCalls, "dbType=" + dbType + "应updateString");
			assertEquals(0, rec.updateObjectCalls, "dbType=" + dbType + "不应updateObject");
			assertEquals(UUID_STR, rec.lastValue, "dbType=" + dbType + "uuid文本");
		}
	}

	/** resultUpdate分派:String承载uuid在pg内核系走updateObject(UUID),不再落String分支updateString(修复前红) */
	@Test
	public void resultUpdateRoutesUuidStringToObjectOnPg() throws Exception {
		Recorder rec = new Recorder();
		SqlUtilsExt.resultUpdate(null, null, rs(rec), uuidField(), UUID_STR, profile(DBType.POSTGRESQL),
				DBType.POSTGRESQL, false, false);
		assertEquals(1, rec.updateObjectCalls, "pg uuid应updateObject");
		assertEquals(0, rec.updateStringCalls, "pg uuid不应updateString(修复前落此分支报类型不匹配)");
		assertTrue(rec.lastValue instanceof java.util.UUID, "pg回写值应为java.util.UUID");
	}

	/** resultUpdate分派:java.util.UUID承载在pg上,不再落final-else updateObject(col,val,3003非法目标类型) */
	@Test
	public void resultUpdateUuidObjectValueHandledOnPg() throws Exception {
		Recorder rec = new Recorder();
		SqlUtilsExt.resultUpdate(null, null, rs(rec), uuidField(), java.util.UUID.fromString(UUID_STR),
				profile(DBType.POSTGRESQL), DBType.POSTGRESQL, false, false);
		assertEquals(1, rec.updateObjectCalls, "pg uuid对象应updateObject");
		assertTrue(rec.lastValue instanceof java.util.UUID, "回写值应为java.util.UUID");
		assertEquals(Integer.MIN_VALUE, rec.lastTargetSqlType, "不应携带3003目标SQL类型(非标准java.sql.Types)");
	}

	/** resultUpdate分派:承载库String uuid维持updateString */
	@Test
	public void resultUpdateRoutesUuidStringToUpdateStringOnCarrier() throws Exception {
		Recorder rec = new Recorder();
		SqlUtilsExt.resultUpdate(null, null, rs(rec), uuidField(), UUID_STR, profile(DBType.MYSQL), DBType.MYSQL,
				false, false);
		assertEquals(1, rec.updateStringCalls, "mysql uuid应updateString");
		assertEquals(0, rec.updateObjectCalls, "mysql uuid不应updateObject");
		assertNotNull(rec.lastValue, "uuid文本非空");
	}

	/** updateUuidValue:pg上32位无连字符hex规范化为UUID回写(原生uuid列接纳框架生成形态) */
	@Test
	public void updateUuidValuePgCompact32Normalized() throws Exception {
		Recorder rec = new Recorder();
		String compact = UUID_STR.replace("-", "");
		SqlUtil.updateUuidValue(profile(DBType.POSTGRESQL), rs(rec), "uuid_col", compact);
		assertEquals(1, rec.updateObjectCalls, "pg应updateObject");
		assertTrue(rec.lastValue instanceof java.util.UUID, "回写值应为java.util.UUID");
		assertEquals(java.util.UUID.fromString(UUID_STR), rec.lastValue, "32位应补连字符解析为规范UUID");
	}

	/** updateUuidValue:承载库32位文本维持原样updateString(不强行补连字符,避免varchar(32)溢出) */
	@Test
	public void updateUuidValueCarrierCompact32Unchanged() throws Exception {
		Recorder rec = new Recorder();
		String compact = UUID_STR.replace("-", "");
		SqlUtil.updateUuidValue(profile(DBType.MYSQL), rs(rec), "uuid_col", compact);
		assertEquals(1, rec.updateStringCalls, "mysql应updateString");
		assertEquals(compact, rec.lastValue, "承载库32位文本原样保留");
	}

	/** updateUuidValue:sqlserver UNIQUEIDENTIFIER原生GUID,32位规范化为36位后updateString */
	@Test
	public void updateUuidValueSqlServerCompact32Normalized() throws Exception {
		Recorder rec = new Recorder();
		String compact = UUID_STR.replace("-", "");
		SqlUtil.updateUuidValue(profile(DBType.SQLSERVER), rs(rec), "uuid_col", compact);
		assertEquals(1, rec.updateStringCalls, "sqlserver应updateString");
		assertEquals(0, rec.updateObjectCalls, "sqlserver不应updateObject");
		assertEquals(UUID_STR, rec.lastValue, "UNIQUEIDENTIFIER须36位规范形");
	}
}