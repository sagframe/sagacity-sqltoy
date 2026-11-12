package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.model.DBProfile;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * update 2026-9-16 resolvePGobjectHolder的dbType守卫单测:非PG系库(mysql/oracle/h2/oscar等)
 * 不再盲探postgresql驱动(optional依赖)的PGobject类,直接返回NULL哨兵——修复前纯mysql用户
 * classpath无org.postgresql.util.PGobject,盲探必抛ClassNotFoundException产生误导性debug日志
 * ("failed to probe PGobject class by url:jdbc:mysql:...");PG系保留URL scheme显式映射
 * (postgresql等)与兜底探测(stardb等兼容postgresql包路径的驱动)。
 * 判定方式:PGobjectHolder.create对NULL哨兵(constructor==null)返回null,对可用句柄返回非null
 * (本模块测试classpath含optional声明的postgresql驱动,守卫缺失时mysql用例将错误地非null,构成回归锚点)。
 */
public class DataSourceUtilsPGProbeTest {

	/** 反射调用私有resolvePGobjectHolder(String url, int dbType) */
	private DBProfile.PGobjectHolder resolve(String url, int dbType) throws Exception {
		Method method = DataSourceUtils.class.getDeclaredMethod("resolvePGobjectHolder", String.class, int.class);
		method.setAccessible(true);
		return (DBProfile.PGobjectHolder) method.invoke(null, url, dbType);
	}

	/** NULL哨兵create返回null,可用句柄返回PGobject实例 */
	private Object tryCreate(DBProfile.PGobjectHolder holder) {
		return holder == null ? null : holder.create("json", "{}");
	}

	@Test
	public void nonPGFamilySkipsProbe() throws Exception {
		// mysql:守卫生效直接返回哨兵,不做Class.forName(修复前测试classpath有pg驱动时
		// 盲探会成功返回可用holder,本断言即失败,构成回归锚点)
		assertNull(tryCreate(resolve("jdbc:mysql://localhost:3306/db?useUnicode=true", DBType.MYSQL)),
				"mysql不探测PGobject");
		assertNull(tryCreate(resolve("jdbc:oracle:thin:@localhost:1521:orcl", DBType.ORACLE)), "oracle不探测PGobject");
		assertNull(tryCreate(resolve("jdbc:h2:mem:test", DBType.H2)), "h2不探测PGobject");
		// oscar:不在isPGFamily白名单(老pgjdbc深度魔改无PGobject同构类,跨驱动setObject必败),
		// 其json/vector以setString绑定为正确形态
		assertNull(tryCreate(resolve("jdbc:oscar://localhost:2003/db", DBType.OSCAR)), "oscar不探测PGobject");
	}

	@Test
	public void pgFamilyRetainsProbe() throws Exception {
		// 显式scheme映射:postgresql→org.postgresql.util.PGobject(测试classpath含该驱动)
		assertNotNull(tryCreate(resolve("jdbc:postgresql://localhost:5432/db", DBType.POSTGRESQL)),
				"postgresql同源探测保留");
		// PG系dbType的兜底探测保留:stardb等URL schema未显式建映射但兼容postgresql驱动包路径
		assertNotNull(tryCreate(resolve("jdbc:stardb://localhost:5432/db", DBType.STARDB)), "stardb兜底探测保留");
	}

	@Test
	public void malformedUrlReturnsSentinel() throws Exception {
		assertNull(tryCreate(resolve(null, DBType.POSTGRESQL)), "null url返回哨兵");
		assertNull(tryCreate(resolve("postgresql://localhost/db", DBType.POSTGRESQL)), "非jdbc:前缀返回哨兵");
	}
}
