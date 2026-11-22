package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.sql.PreparedStatement;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.model.DBProfile;
import org.sagacity.sqltoy.model.JdbcTypes;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * update 2026-11-22 缺陷B回归锁死:uuid列null值绑定此前无setNullParam分支,落else的
 * setNull(idx,JdbcTypes.UUID=3003),pg报"不支持的值类型"、mssql报"JDBC类型3003无效"(真库实证)。
 * 修复后pg内核系/h2原生uuid列按OTHER置null,其余varchar(36)/char(36)/uniqueidentifier承载列按
 * VARCHAR置null。以记录型PreparedStatement代理捕获setNull的sqlType实参,无需真库即可锁死分派。
 *
 * @author zsy 2026-11-22
 */
public class SqlUtilUuidNullBindTest {

	private int captureNullSqlType(int dbType) throws Exception {
		DBProfile profile = new DBProfile("jdbc:probe", "probe", dbType, null, 0, null, null, false);
		AtomicInteger captured = new AtomicInteger(Integer.MIN_VALUE);
		PreparedStatement pst = (PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class[] { PreparedStatement.class }, (proxy, method, args) -> {
					if ("setNull".equals(method.getName()) && args != null && args.length == 2) {
						captured.set((Integer) args[1]);
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
		SqlUtil.setParamValue(null, null, profile, pst, null, JdbcTypes.UUID, 1);
		return captured.get();
	}

	@Test
	public void pgKernelAndH2NullUsesOther() throws Exception {
		// pg内核系10库与h2有原生uuid列,setNull(OTHER)交服务器按uuid推断;3003非法
		assertEquals(java.sql.Types.OTHER, captureNullSqlType(DBType.POSTGRESQL));
		assertEquals(java.sql.Types.OTHER, captureNullSqlType(DBType.POSTGRESQL14));
		assertEquals(java.sql.Types.OTHER, captureNullSqlType(DBType.OPENGAUSS));
		assertEquals(java.sql.Types.OTHER, captureNullSqlType(DBType.KINGBASE));
		assertEquals(java.sql.Types.OTHER, captureNullSqlType(DBType.HIGHGO));
		assertEquals(java.sql.Types.OTHER, captureNullSqlType(DBType.VASTBASE));
		assertEquals(java.sql.Types.OTHER, captureNullSqlType(DBType.H2));
	}

	@Test
	public void carrierDialectsNullUsesVarchar() throws Exception {
		// 其余目标uuid以varchar(36)/char(36)/uniqueidentifier承载,setNull(VARCHAR)置null;
		// 此前setNull(3003)在mssql报"JDBC类型3003无效"
		assertEquals(java.sql.Types.VARCHAR, captureNullSqlType(DBType.MYSQL));
		assertEquals(java.sql.Types.VARCHAR, captureNullSqlType(DBType.SQLSERVER));
		assertEquals(java.sql.Types.VARCHAR, captureNullSqlType(DBType.ORACLE));
		assertEquals(java.sql.Types.VARCHAR, captureNullSqlType(DBType.DM));
		assertEquals(java.sql.Types.VARCHAR, captureNullSqlType(DBType.DB2));
	}
}