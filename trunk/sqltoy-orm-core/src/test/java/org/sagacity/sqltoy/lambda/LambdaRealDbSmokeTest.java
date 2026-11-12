package org.sagacity.sqltoy.lambda;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.annotation.Column;
import org.sagacity.sqltoy.config.annotation.Entity;
import org.sagacity.sqltoy.config.annotation.Id;
import org.sagacity.sqltoy.dao.LightDao;
import org.sagacity.sqltoy.dao.impl.DefaultLightDaoImpl;

import com.alibaba.druid.pool.DruidDataSource;

/**
 * @description lambda真库冒烟测试:在本地可连接的MySQL/PostgreSQL/SQLServer/Oracle上执行单表条件查询、
 *              聚合、单列终端、join查询、setExpr、join更新与删除(可移植EXISTS改写)及单表删除,
 *              覆盖H2无法证明的真实方言与参数改写;连不上的库优雅跳过,与既有*RealDbSmokeTest约定一致
 * @author zhongxuchen
 */
@DisplayName("lambda真库冒烟测试")
public class LambdaRealDbSmokeTest {

	private static final String[][] PROBES = {
			{ "mysql", "com.mysql.cj.jdbc.Driver",
					"jdbc:mysql://localhost:3307/sqltoy?useSSL=false&allowPublicKeyRetrieval=true", "root",
					"Sqltoy@2026" },
			{ "postgresql", "org.postgresql.Driver", "jdbc:postgresql://localhost:5433/postgres", "postgres",
					"Sqltoy@2026" },
			{ "sqlserver", "com.microsoft.sqlserver.jdbc.SQLServerDriver",
					"jdbc:sqlserver://localhost:1434;encrypt=false;trustServerCertificate=true", "sa", "Sqltoy@2026" },
			{ "oracle", "oracle.jdbc.OracleDriver", "jdbc:oracle:thin:@//localhost:1521/FREEPDB1", "system",
					"Sqltoy@2026" } };

	@Entity(tableName = "lam_rd_dept")
	public static class Dept implements Serializable {
		private static final long serialVersionUID = 1L;

		@Id
		@Column(name = "DEPT_ID")
		private Integer deptId;

		@Column(name = "DEPT_NAME")
		private String deptName;

		public Integer getDeptId() {
			return deptId;
		}

		public void setDeptId(Integer deptId) {
			this.deptId = deptId;
		}

		public String getDeptName() {
			return deptName;
		}

		public void setDeptName(String deptName) {
			this.deptName = deptName;
		}
	}

	@Entity(tableName = "lam_rd_user")
	public static class User implements Serializable {
		private static final long serialVersionUID = 1L;

		@Id
		@Column(name = "ID")
		private Integer id;

		@Column(name = "STAFF_NAME")
		private String staffName;

		@Column(name = "DEPT_ID")
		private Integer deptId;

		@Column(name = "STATUS")
		private Integer status;

		@Column(name = "SALARY")
		private BigDecimal salary;

		public Integer getId() {
			return id;
		}

		public void setId(Integer id) {
			this.id = id;
		}

		public String getStaffName() {
			return staffName;
		}

		public void setStaffName(String staffName) {
			this.staffName = staffName;
		}

		public Integer getDeptId() {
			return deptId;
		}

		public void setDeptId(Integer deptId) {
			this.deptId = deptId;
		}

		public Integer getStatus() {
			return status;
		}

		public void setStatus(Integer status) {
			this.status = status;
		}

		public BigDecimal getSalary() {
			return salary;
		}

		public void setSalary(BigDecimal salary) {
			this.salary = salary;
		}
	}

	@Test
	@DisplayName("真库执行")
	public void verifyRealDatabases() {
		DriverManager.setLoginTimeout(6);
		List<String> skipped = new ArrayList<>();
		List<String> finished = new ArrayList<>();
		List<String> failures = new ArrayList<>();
		for (String[] probe : PROBES) {
			String name = probe[0];
			try {
				Class.forName(probe[1]);
				try (Connection conn = DriverManager.getConnection(probe[2], probe[3], probe[4])) {
					runOne(probe, conn);
					finished.add(name);
					System.out.println("[REALDB] " + name + " OK");
				}
			} catch (java.sql.SQLException e) {
				skipped.add(name + "(" + e.getMessage() + ")");
				System.out.println("[REALDB] " + name + " SKIP: " + e.getMessage());
			} catch (Throwable e) {
				failures.add(name + " -> " + e);
				System.out.println("[REALDB] " + name + " FAILED: " + e);
				e.printStackTrace(System.out);
			}
		}
		System.out.println("[REALDB] finished=" + finished + " skipped=" + skipped + " failed=" + failures.size());
		assertTrue(failures.isEmpty(), "真库执行失败: " + failures);
	}

	private void runOne(String[] probe, Connection conn) throws Exception {
		try (Statement st = conn.createStatement()) {
			dropQuietly(st, "lam_rd_user");
			dropQuietly(st, "lam_rd_dept");
			st.execute("create table lam_rd_dept (dept_id int primary key, dept_name varchar(50))");
			st.execute(
					"create table lam_rd_user (id int primary key, staff_name varchar(50), dept_id int, status int, salary numeric(10,2))");
			st.execute("insert into lam_rd_dept values (10,'RD')");
			st.execute("insert into lam_rd_dept values (20,'MKT')");
			st.execute("insert into lam_rd_dept values (30,'EMPTY')");
			st.execute("insert into lam_rd_user values (1,'u1',10,1,100.00)");
			st.execute("insert into lam_rd_user values (2,'u2',20,1,200.00)");
			st.execute("insert into lam_rd_user values (3,'u3',10,0,300.00)");
			st.execute("insert into lam_rd_user values (4,'u4',null,1,400.00)");
		}
		DruidDataSource dataSource = new DruidDataSource();
		dataSource.setDriverClassName(probe[1]);
		dataSource.setUrl(probe[2]);
		dataSource.setUsername(probe[3]);
		dataSource.setPassword(probe[4]);
		SqlToyContext context = new SqlToyContext();
		context.setDefaultDataSource(dataSource);
		context.initialize();
		LightDao dao = new DefaultLightDaoImpl(context);
		try {
			// 1.单表条件查询
			assertEquals(3, dao.lambdaQuery(User.class).eq(User::getStatus, 1).find().size());
			// 2.聚合
			assertEquals(0, new BigDecimal("1000").compareTo(dao.lambdaQuery(User.class).sum(User::getSalary)));
			// 3.单列终端
			assertEquals("[u1, u2, u3, u4]",
					dao.lambdaQuery(User.class).orderByAsc(User::getId).findColumns(User::getStaffName).toString());
			// 4.join查询
			assertEquals(2, dao.lambdaQuery(User.class).innerJoin(Dept.class)
					.on(User::getDeptId, Dept::getDeptId).eq(Dept::getDeptName, "RD").find().size());
			// 5.单表setExpr(?占位符经内核改写成命名参数)
			assertEquals(1, dao.lambdaUpdate(User.class)
					.setExpr(User::getSalary, "salary * ?", new BigDecimal("2")).eq(User::getId, 1).update());
			assertEquals(0, new BigDecimal("200.00")
					.compareTo(dao.lambdaQuery(User.class).eq(User::getId, 1).getScalar(User::getSalary)));
			// 6.join更新(EXISTS改写)
			assertEquals(2, dao.lambdaUpdate(User.class).innerJoin(Dept.class)
					.on(User::getDeptId, Dept::getDeptId).set(User::getStatus, 9)
					.eq(Dept::getDeptName, "RD").update());
			assertEquals(2, dao.lambdaQuery(User.class).eq(User::getStatus, 9).count());
			// 7.join删除被join的从表(deleteTables):部门10仍被用户引用,可从join结果定位
			assertEquals(1, dao.lambdaDelete(User.class).leftJoin(Dept.class)
					.on(User::getDeptId, Dept::getDeptId).deleteTables(Dept.class)
					.eq(Dept::getDeptName, "RD").delete());
			assertNull(dao.lambdaQuery(Dept.class).eq(Dept::getDeptId, 10).getOne());
			// 8.join删除主表
			assertEquals(1, dao.lambdaDelete(User.class).innerJoin(Dept.class)
					.on(User::getDeptId, Dept::getDeptId).eq(Dept::getDeptName, "MKT").delete());
			assertNull(dao.lambdaQuery(User.class).eq(User::getId, 2).getOne());
			// 9.单表删除
			assertEquals(2, dao.lambdaDelete(User.class).eq(User::getStatus, 9).delete());
		} finally {
			try (Statement st = conn.createStatement()) {
				dropQuietly(st, "lam_rd_user");
				dropQuietly(st, "lam_rd_dept");
			}
			dataSource.close();
		}
	}

	private static void dropQuietly(Statement st, String table) {
		try {
			st.execute("drop table " + table);
		} catch (Exception ignore) {
			// 表不存在
		}
	}
}
