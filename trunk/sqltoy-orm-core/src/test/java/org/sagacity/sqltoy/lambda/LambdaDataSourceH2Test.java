package org.sagacity.sqltoy.lambda;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.Serializable;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import javax.sql.DataSource;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.annotation.Column;
import org.sagacity.sqltoy.config.annotation.Entity;
import org.sagacity.sqltoy.config.annotation.Id;
import org.sagacity.sqltoy.dao.LightDao;
import org.sagacity.sqltoy.dao.impl.DefaultLightDaoImpl;

/**
 * @description lambda构建器.dataSource(ds)多数据源路由执行级测试:两个独立H2内存库装不同数据,
 *              验证显式指定数据源后单表与join两通道的查询/count/更新/删除全部落到目标库、默认库不受影响。
 *              单表通道经EntityQuery/EntityUpdate透传、join通道经QueryExecutor/executeSql(Map,ds)透传
 * @author zhongxuchen
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class LambdaDataSourceH2Test {

	private static final String URL1 = "jdbc:h2:mem:dsone;DB_CLOSE_DELAY=-1";
	private static final String URL2 = "jdbc:h2:mem:dstwo;DB_CLOSE_DELAY=-1";

	private static LightDao lightDao;

	/**
	 * 第二个数据源(显式指定用),与dao默认数据源(URL1)装不同数据以区分路由
	 */
	private static DataSource ds2;

	@BeforeAll
	public static void init() throws Exception {
		// 默认库ds1:部门DS1部门,用户2条
		try (Connection conn = DriverManager.getConnection(URL1, "sa", ""); Statement st = conn.createStatement()) {
			st.execute("drop table if exists ds_user");
			st.execute("drop table if exists ds_dept");
			st.execute("create table ds_dept (dept_id int primary key, dept_name varchar(50))");
			st.execute("create table ds_user (id int primary key, staff_name varchar(50), dept_id int, status int)");
			st.execute("insert into ds_dept values (10,'DS1部门')");
			st.execute("insert into ds_user values (1,'DS1甲',10,1)");
			st.execute("insert into ds_user values (2,'DS1乙',10,1)");
		}
		// 第二库ds2:部门DS2部门,用户3条(与ds1可区分)
		try (Connection conn = DriverManager.getConnection(URL2, "sa", ""); Statement st = conn.createStatement()) {
			st.execute("drop table if exists ds_user");
			st.execute("drop table if exists ds_dept");
			st.execute("create table ds_dept (dept_id int primary key, dept_name varchar(50))");
			st.execute("create table ds_user (id int primary key, staff_name varchar(50), dept_id int, status int)");
			st.execute("insert into ds_dept values (20,'DS2部门')");
			st.execute("insert into ds_user values (1,'DS2甲',20,1)");
			st.execute("insert into ds_user values (2,'DS2乙',20,1)");
			st.execute("insert into ds_user values (3,'DS2丙',20,1)");
		}
		JdbcDataSource ds1 = new JdbcDataSource();
		ds1.setURL(URL1);
		ds1.setUser("sa");
		JdbcDataSource second = new JdbcDataSource();
		second.setURL(URL2);
		second.setUser("sa");
		ds2 = second;
		SqlToyContext context = new SqlToyContext();
		context.setDefaultDataSource(ds1);
		context.initialize();
		lightDao = new DefaultLightDaoImpl(context);
	}

	@Test
	@Order(1)
	@DisplayName("单表查询/count按dataSource路由")
	public void singleTableQueryRouting() {
		// 默认数据源ds1
		assertEquals("DS1甲", lightDao.lambdaQuery(DsUser.class).eq(DsUser::getId, 1).getOne().getStaffName());
		assertEquals(2L, lightDao.lambdaQuery(DsUser.class).count());
		// 显式指定ds2:同名id=1取到的是ds2的数据,count也是ds2的
		assertEquals("DS2甲",
				lightDao.lambdaQuery(DsUser.class).dataSource(ds2).eq(DsUser::getId, 1).getOne().getStaffName());
		assertEquals(3L, lightDao.lambdaQuery(DsUser.class).dataSource(ds2).count());
	}

	@Test
	@Order(2)
	@DisplayName("join查询/count按dataSource路由")
	public void joinQueryRouting() {
		// 同一条件(deptName='DS2部门')在ds1无匹配、在ds2有3条,直接证明join查询落到了指定库
		assertEquals(0, lightDao.lambdaQuery(DsUser.class).leftJoin(DsDept.class)
				.on(DsUser::getDeptId, DsDept::getDeptId).eq(DsDept::getDeptName, "DS2部门").find().size());
		assertEquals(3, lightDao.lambdaQuery(DsUser.class).dataSource(ds2).leftJoin(DsDept.class)
				.on(DsUser::getDeptId, DsDept::getDeptId).eq(DsDept::getDeptName, "DS2部门").find().size());
		// join count 同样路由(经getCountByQuery(QueryExecutor.dataSource))
		assertEquals(0L, lightDao.lambdaQuery(DsUser.class).leftJoin(DsDept.class)
				.on(DsUser::getDeptId, DsDept::getDeptId).eq(DsDept::getDeptName, "DS2部门").count());
		assertEquals(3L, lightDao.lambdaQuery(DsUser.class).dataSource(ds2).leftJoin(DsDept.class)
				.on(DsUser::getDeptId, DsDept::getDeptId).eq(DsDept::getDeptName, "DS2部门").count());
	}

	@Test
	@Order(3)
	@DisplayName("单表更新按dataSource路由(目标库改、默认库不动)")
	public void singleTableUpdateRouting() {
		long updated = lightDao.lambdaUpdate(DsUser.class).dataSource(ds2).set(DsUser::getStatus, 99)
				.eq(DsUser::getId, 1).update();
		assertEquals(1L, updated);
		// ds2被改
		assertEquals(99,
				lightDao.lambdaQuery(DsUser.class).dataSource(ds2).eq(DsUser::getId, 1).getOne().getStatus().intValue());
		// ds1未受影响
		assertEquals(1, lightDao.lambdaQuery(DsUser.class).eq(DsUser::getId, 1).getOne().getStatus().intValue());
	}

	@Test
	@Order(4)
	@DisplayName("单表删除按dataSource路由")
	public void singleTableDeleteRouting() {
		long deleted = lightDao.lambdaDelete(DsUser.class).dataSource(ds2).eq(DsUser::getId, 3).delete();
		assertEquals(1L, deleted);
		assertEquals(2L, lightDao.lambdaQuery(DsUser.class).dataSource(ds2).count());
		// ds1始终2条,未被波及
		assertEquals(2L, lightDao.lambdaQuery(DsUser.class).count());
	}

	@Test
	@Order(5)
	@DisplayName("join更新按dataSource路由(EXISTS可移植改写+指定库)")
	public void joinUpdateRouting() {
		// ds2此时剩id1(status99)、id2(status1),join DS2部门更新status=88命中2条
		long updated = lightDao.lambdaUpdate(DsUser.class).dataSource(ds2).leftJoin(DsDept.class)
				.on(DsUser::getDeptId, DsDept::getDeptId).set(DsUser::getStatus, 88)
				.eq(DsDept::getDeptName, "DS2部门").update();
		assertEquals(2L, updated);
		assertEquals(2L, lightDao.lambdaQuery(DsUser.class).dataSource(ds2).eq(DsUser::getStatus, 88).count());
		// ds1无status=88
		assertEquals(0L, lightDao.lambdaQuery(DsUser.class).eq(DsUser::getStatus, 88).count());
	}

	@Test
	@Order(6)
	@DisplayName("join删除按dataSource路由")
	public void joinDeleteRouting() {
		long deleted = lightDao.lambdaDelete(DsUser.class).dataSource(ds2).leftJoin(DsDept.class)
				.on(DsUser::getDeptId, DsDept::getDeptId).eq(DsDept::getDeptName, "DS2部门").delete();
		assertEquals(2L, deleted);
		assertEquals(0L, lightDao.lambdaQuery(DsUser.class).dataSource(ds2).count());
		// ds1完好2条
		assertEquals(2L, lightDao.lambdaQuery(DsUser.class).count());
	}

	@Entity(tableName = "ds_user")
	public static class DsUser implements Serializable {

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
	}

	@Entity(tableName = "ds_dept")
	public static class DsDept implements Serializable {

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
}
