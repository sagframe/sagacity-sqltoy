package org.sagacity.sqltoy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.Set;
import java.util.regex.Pattern;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.utils.SqlUtil;

/**
 * 回归:initialize中途失败必须释放本次认领的JVM级静态配置首占权。
 * 修复前认领记录在静态Set中且不回滚——携带非法注入正则的context初始化失败后,
 * sqlInjectionRegexes等key被永久锁死,同JVM后续上下文(测试套件/热部署重试)
 * 即使携带正确配置也无法生效(安全攸关)。
 */
public class SqlToyContextInitFailureRollbackTest {

	private Pattern[] originInjectionPatterns;

	@SuppressWarnings("unchecked")
	private static Set<String> owners() throws Exception {
		Field field = SqlToyContext.class.getDeclaredField("STATIC_STATE_OWNERS");
		field.setAccessible(true);
		return (Set<String>) field.get(null);
	}

	@BeforeEach
	public void setUp() throws Exception {
		originInjectionPatterns = SqlUtil.SQL_INJECTION_KEY_WORDS;
		owners().clear();
	}

	@AfterEach
	public void tearDown() throws Exception {
		SqlUtil.SQL_INJECTION_KEY_WORDS = originInjectionPatterns;
		owners().clear();
	}

	@Test
	public void failedInitReleasesClaimsForLaterContext() throws Exception {
		SqlToyContext bad = new SqlToyContext();
		// 非法正则使initialize在认领sqlInjectionRegexes后、写入静态值前抛PatternSyntaxException
		bad.setSqlInjectionRegexes(new String[] { "[" });
		assertThrows(Exception.class, bad::initialize);

		// 修复前:bad认领的首占权不释放,good的同名配置被静默忽略,注入正则停留在默认值
		SqlToyContext good = new SqlToyContext();
		JdbcDataSource dataSource = new JdbcDataSource();
		dataSource.setURL("jdbc:h2:mem:init_rollback_test;DB_CLOSE_DELAY=-1");
		dataSource.setUser("sa");
		good.setDefaultDataSource(dataSource);
		good.setSqlInjectionRegexes(new String[] { "\\bdrop\\s+table\\b" });
		good.initialize();
		try {
			Pattern[] patterns = SqlUtil.SQL_INJECTION_KEY_WORDS;
			assertNotNull(patterns);
			boolean matched = false;
			for (Pattern pattern : patterns) {
				if (pattern.matcher("drop table staff_info").find()) {
					matched = true;
					break;
				}
			}
			assertTrue(matched, "失败context释放首占权后,后续context的注入正则应生效");
		} finally {
			good.destroy();
		}
	}

	@Test
	public void successfulInitKeepsClaims() throws Exception {
		SqlToyContext first = new SqlToyContext();
		JdbcDataSource dataSource = new JdbcDataSource();
		dataSource.setURL("jdbc:h2:mem:init_rollback_first;DB_CLOSE_DELAY=-1");
		dataSource.setUser("sa");
		first.setDefaultDataSource(dataSource);
		first.setSqlInjectionRegexes(new String[] { "\\btruncate\\s+table\\b" });
		first.initialize();
		try {
			// 成功初始化的首占权不释放:后来者同名配置认领仍被拒(首占语义不变)
			assertTrue(owners().contains("sqlInjectionRegexes"));
			SqlToyContext later = new SqlToyContext();
			java.lang.reflect.Method claim = SqlToyContext.class.getDeclaredMethod("claimStaticStateOwned",
					String.class, boolean.class);
			claim.setAccessible(true);
			assertFalse((Boolean) claim.invoke(later, "sqlInjectionRegexes", true));
			assertTrue(owners().contains("sqlInjectionRegexes"), "成功context的首占权不得被释放");
		} finally {
			first.destroy();
		}
	}
}