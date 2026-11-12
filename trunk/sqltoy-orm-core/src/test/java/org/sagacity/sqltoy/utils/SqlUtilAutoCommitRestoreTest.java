package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.model.DBProfile;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * update 2026-9-18 缺陷#5修复回归:executeSql/insertReturnPrimaryKey的autoCommit恢复
 * 移入finally(与batchUpdateByJdbc范式对齐)。修复前:执行抛异常时连接带着被改写的
 * autoCommit=false归还池化连接,污染后续无关请求;手动提交场景异常还不回滚,
 * finally恢复autoCommit(true)会按JDBC规范隐式提交当前事务。
 */
public class SqlUtilAutoCommitRestoreTest {

	private static DBProfile h2Profile() {
		return new DBProfile(null, "h2", DBType.H2, "H2", 2, null, null, false);
	}

	private Connection newConn(String db) throws Exception {
		return DriverManager.getConnection("jdbc:h2:mem:" + db + ";DB_CLOSE_DELAY=-1", "sa", "");
	}

	/** 异常路径:非法SQL+autoCommit=false,抛出后连接提交方式必须已恢复 */
	@Test
	public void executeSqlFailureRestoresAutoCommit() throws Exception {
		try (Connection conn = newConn("ac_restore_1")) {
			assertTrue(conn.getAutoCommit(), "h2默认autoCommit=true");
			assertThrows(Exception.class, () -> SqlUtil.executeSql(null, "insert into not_exist_t values (?)",
					new Object[] { 1 }, null, conn, h2Profile(), Boolean.FALSE, false));
			assertTrue(conn.getAutoCommit(), "异常后autoCommit必须恢复为true(修复前为false泄漏给池)");
		}
	}

	/** 成功路径:手动提交模式执行成功后恢复且数据已提交(新连接可见) */
	@Test
	public void executeSqlSuccessCommitsAndRestores() throws Exception {
		try (Connection conn = newConn("ac_restore_2")) {
			try (Statement st = conn.createStatement()) {
				st.execute("create table ac_t(id int primary key, name varchar(20))");
			}
			Long cnt = SqlUtil.executeSql(null, "insert into ac_t values (?,?)", new Object[] { 1, "a" }, null, conn,
					h2Profile(), Boolean.FALSE, false);
			assertEquals(1L, cnt.longValue(), "影响1行");
			assertTrue(conn.getAutoCommit(), "成功后autoCommit恢复");
		}
		// 新连接验证数据已提交(finally恢复autoCommit(true)时JDBC隐式提交了手动事务)
		try (Connection verify = newConn("ac_restore_2"); Statement st = verify.createStatement()) {
			ResultSet rs = st.executeQuery("select count(*) from ac_t");
			rs.next();
			assertEquals(1, rs.getInt(1), "手动提交模式的数据已落库");
		}
	}

	/** insertReturnPrimaryKey异常路径:同样恢复autoCommit */
	@Test
	public void insertReturnPkFailureRestoresAutoCommit() throws Exception {
		try (Connection conn = newConn("ac_restore_3")) {
			assertThrows(Exception.class,
					() -> SqlUtil.insertReturnPrimaryKey(null, "insert into not_exist_t values (?)",
							new Object[] { 1 }, null, "id", conn, h2Profile(), Boolean.FALSE, false));
			assertTrue(conn.getAutoCommit(), "异常后autoCommit必须恢复(修复前false泄漏)");
		}
	}

	/** insertReturnPrimaryKey成功路径:identity主键回填且状态恢复 */
	@Test
	public void insertReturnPkSuccessRestoresAutoCommit() throws Exception {
		try (Connection conn = newConn("ac_restore_4")) {
			try (Statement st = conn.createStatement()) {
				st.execute("create table ac_idn(id int auto_increment primary key, name varchar(20))");
			}
			Object pk = SqlUtil.insertReturnPrimaryKey(null, "insert into ac_idn (name) values (?)",
					new Object[] { "x" }, null, "id", conn, h2Profile(), Boolean.FALSE, false);
			assertTrue(conn.getAutoCommit(), "成功后autoCommit恢复");
			assertTrue(pk != null, "identity主键应回填:" + pk);
		}
	}

	/** autoCommit=null(保持连接原状)时不应触碰连接提交方式 */
	@Test
	public void nullAutoCommitKeepsConnectionState() throws Exception {
		try (Connection conn = newConn("ac_restore_5")) {
			conn.setAutoCommit(false);
			try (Statement st = conn.createStatement()) {
				st.execute("create table ac_keep(id int primary key)");
			}
			conn.commit();
			SqlUtil.executeSql(null, "insert into ac_keep values (?)", new Object[] { 1 }, null, conn, h2Profile(),
					null, false);
			assertEquals(false, conn.getAutoCommit(), "autoCommit=null不得改写连接提交方式");
			conn.rollback();
		}
	}
}
