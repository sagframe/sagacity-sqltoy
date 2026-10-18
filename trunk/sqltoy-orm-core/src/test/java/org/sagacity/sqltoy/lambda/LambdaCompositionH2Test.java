package org.sagacity.sqltoy.lambda;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.Serializable;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

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
import org.sagacity.sqltoy.lambda.join.JoinCol;
import org.sagacity.sqltoy.model.Page;

/**
 * @description lambda组合查询测试:CTE(WITH AS)/@fast分页下推派生表/UNION-UNION ALL,
 *              验证组合SQL形态(WITH前缀、@fast标记、UNION包裹)、跨builder参数续号不撞号、以及H2真实执行
 * @author zhongxuchen
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class LambdaCompositionH2Test {

	private static final String URL = "jdbc:h2:mem:lambdacomp;DB_CLOSE_DELAY=-1";

	private static LightDao lightDao;
	private static SqlToyContext context;

	@BeforeAll
	public static void init() throws Exception {
		try (Connection conn = DriverManager.getConnection(URL, "sa", ""); Statement st = conn.createStatement()) {
			st.execute("drop table if exists cte_user");
			st.execute("drop table if exists cte_dept");
			st.execute("create table cte_dept (dept_id int primary key, dept_name varchar(50), status int)");
			st.execute("create table cte_user (id int primary key, name varchar(50), dept_id int, status int)");
			st.execute("insert into cte_dept values (10,'研发部',1)");
			st.execute("insert into cte_dept values (20,'市场部',0)");
			st.execute("insert into cte_user values (1,'张三',10,1)");
			st.execute("insert into cte_user values (2,'李四',10,1)");
			st.execute("insert into cte_user values (3,'王五',20,1)");
			// 组织树(递归CTE用):root→a,b; a→a1
			st.execute("drop table if exists cte_org");
			st.execute("create table cte_org (id int primary key, pid int, name varchar(50))");
			st.execute("insert into cte_org values (1,null,'root')");
			st.execute("insert into cte_org values (2,1,'a')");
			st.execute("insert into cte_org values (3,1,'b')");
			st.execute("insert into cte_org values (4,2,'a1')");
		}
		JdbcDataSource dataSource = new JdbcDataSource();
		dataSource.setURL(URL);
		dataSource.setUser("sa");
		context = new SqlToyContext();
		context.setDefaultDataSource(dataSource);
		context.initialize();
		lightDao = new DefaultLightDaoImpl(context);
	}

	private static String norm(String sql) {
		return sql == null ? "" : sql.replaceAll("\\s+", " ").trim();
	}

	@Test
	@Order(1)
	@DisplayName("CTE组合SQL文本:WITH前缀+内层sql+跨builder参数续号")
	public void testCteSqlText() {
		LambdaQuery<CteDept> inner = lightDao.lambdaQuery(CteDept.class).from(CteDept.class)
				.select(JoinCol.of(CteDept::getDeptId).as("deptId"), JoinCol.of(CteDept::getDeptName).as("deptName"))
				.eq(CteDept::getStatus, 1);
		LambdaQuery<CteDeptVO> outer = lightDao.lambdaQuery(CteDeptVO.class).with("d", inner).fromCte("d")
				.eq(CteDeptVO::getDeptName, "研发部");
		outer.assemble(context::getEntityMeta);
		String sql = norm(outer.getSql());
		// WITH前缀 + 内层 + 外层FROM d;内层:p1,外层续号:p2
		assertTrue(sql.startsWith("WITH d AS (SELECT"), sql);
		assertTrue(sql.contains("FROM cte_dept c1 WHERE c1.STATUS = :p1"), sql);
		assertTrue(sql.contains(") SELECT c1.DEPTNAME AS deptName, c1.DEPTID AS deptId FROM d c1 WHERE c1.DEPTNAME = :p2"),
				sql);
		// 参数合并:内层p1=1,外层p2=研发部
		assertEquals(1, outer.getParams().get("p1"));
		assertEquals("研发部", outer.getParams().get("p2"));
	}

	@Test
	@Order(2)
	@DisplayName("CTE组合H2执行:外层过滤CTE结果")
	public void testCteExecution() {
		LambdaQuery<CteDept> inner = lightDao.lambdaQuery(CteDept.class).from(CteDept.class)
				.select(JoinCol.of(CteDept::getDeptId).as("deptId"), JoinCol.of(CteDept::getDeptName).as("deptName"))
				.eq(CteDept::getStatus, 1);
		List<CteDeptVO> rows = lightDao.lambdaQuery(CteDeptVO.class).with("d", inner).fromCte("d")
				.eq(CteDeptVO::getDeptName, "研发部").find();
		assertEquals(1, rows.size());
		assertEquals("研发部", rows.get(0).getDeptName());
	}

	@Test
	@Order(3)
	@DisplayName("多CTE串联:WITH a AS (..), b AS (.. 引用a)")
	public void testMultiCteSqlText() {
		LambdaQuery<CteDept> a = lightDao.lambdaQuery(CteDept.class).from(CteDept.class)
				.select(JoinCol.of(CteDept::getDeptId).as("deptId")).eq(CteDept::getStatus, 1);
		LambdaQuery<CteDeptVO> b = lightDao.lambdaQuery(CteDeptVO.class).fromCte("a")
				.select(JoinCol.of(CteDeptVO::getDeptId).as("deptId"));
		LambdaQuery<CteDeptVO> outer = lightDao.lambdaQuery(CteDeptVO.class).with("a", a).with("b", b).fromCte("b");
		outer.assemble(context::getEntityMeta);
		String sql = norm(outer.getSql());
		assertTrue(sql.startsWith("WITH a AS (SELECT"), sql);
		assertTrue(sql.contains("), b AS (SELECT"), sql);
		assertTrue(sql.contains("FROM a c1 WHERE 1 = 1)"), sql);
	}

	@Test
	@Order(4)
	@DisplayName("fromFast派生表:FROM @fast(内层sql) 别名")
	public void testFromFastSqlText() {
		LambdaQuery<CteUser> inner = lightDao.lambdaQuery(CteUser.class).from(CteUser.class)
				.eq(CteUser::getStatus, 1).orderByDesc(CteUser::getId);
		LambdaQuery<CteUser> outer = lightDao.lambdaQuery(CteUser.class).fromFast(inner, "t1");
		outer.assemble(context::getEntityMeta);
		String sql = norm(outer.getSql());
		// FROM @fast(内层) 别名
		assertTrue(sql.contains("FROM @fast(SELECT"), sql);
		assertTrue(sql.contains(") t1"), sql);
	}

	@Test
	@Order(5)
	@DisplayName("UNION/UNION ALL组合SQL与执行")
	public void testUnion() {
		LambdaQuery<CteUser> q1 = lightDao.lambdaQuery(CteUser.class).from(CteUser.class)
				.select(JoinCol.of(CteUser::getName).as("name")).eq(CteUser::getDeptId, 10);
		LambdaQuery<CteUser> q2 = lightDao.lambdaQuery(CteUser.class).from(CteUser.class)
				.select(JoinCol.of(CteUser::getName).as("name")).eq(CteUser::getDeptId, 20);
		q1.unionAll(q2);
		q1.assemble(context::getEntityMeta);
		String sql = norm(q1.getSql());
		assertTrue(sql.contains(" UNION ALL SELECT"), sql);
		// 执行:dept10(张三,李四)+dept20(王五)=3
		List<CteUser> rows = q1.find();
		assertEquals(3, rows.size());
	}

	@Test
	@Order(6)
	@DisplayName("UNION的count与findPage全终端")
	public void testUnionCountPage() {
		LambdaQuery<CteUser> q1 = lightDao.lambdaQuery(CteUser.class).from(CteUser.class)
				.select(JoinCol.of(CteUser::getName).as("name")).eq(CteUser::getDeptId, 10);
		LambdaQuery<CteUser> q2 = lightDao.lambdaQuery(CteUser.class).from(CteUser.class)
				.select(JoinCol.of(CteUser::getName).as("name")).eq(CteUser::getDeptId, 20);
		q1.union(q2);
		assertEquals(3L, q1.count());
		Page<CteUser> page = q1.findPage(new Page(2, 1));
		assertEquals(3L, page.getRecordCount());
		assertEquals(2, page.getRows().size());
	}

	@Test
	@Order(7)
	@DisplayName("UNION的findTop/findRandom/exists同样作用于组合SQL")
	public void testUnionTopRandomExists() {
		LambdaQuery<CteUser> q1 = lightDao.lambdaQuery(CteUser.class).from(CteUser.class)
				.select(JoinCol.of(CteUser::getName).as("name")).eq(CteUser::getDeptId, 10);
		LambdaQuery<CteUser> q2 = lightDao.lambdaQuery(CteUser.class).from(CteUser.class)
				.select(JoinCol.of(CteUser::getName).as("name")).eq(CteUser::getDeptId, 20);
		q1.unionAll(q2);
		// findTop/findRandom/exists 均作用于组合后的 (q1) UNION ALL (q2)
		assertEquals(2, q1.findTop(2).size());
		assertEquals(2, q1.findRandom(2).size());
		assertTrue(q1.exists());
		// CTE 同样全终端:findTop 作用于 WITH..SELECT
		LambdaQuery<CteDept> inner = lightDao.lambdaQuery(CteDept.class).from(CteDept.class)
				.select(JoinCol.of(CteDept::getDeptId).as("deptId"), JoinCol.of(CteDept::getDeptName).as("deptName"))
				.eq(CteDept::getStatus, 1);
		assertEquals(1, lightDao.lambdaQuery(CteDeptVO.class).with("d", inner).fromCte("d").findTop(5).size());
	}

	@Test
	@Order(8)
	@DisplayName("CTE分页:findPage作用于WITH组合SQL")
	public void testCteFindPage() {
		LambdaQuery<CteUser> inner = lightDao.lambdaQuery(CteUser.class).from(CteUser.class)
				.select(JoinCol.raw("*")).eq(CteUser::getStatus, 1);
		Page<CteUser> page = lightDao.lambdaQuery(CteUser.class).with("u", inner).fromCte("u")
				.findPage(new Page(2, 1));
		assertEquals(3L, page.getRecordCount());
		assertEquals(2, page.getRows().size());
	}

	@Test
	@Order(9)
	@DisplayName("CTE与实表join:外层CTE再leftJoin真实表")
	public void testCteJoinRealTable() {
		LambdaQuery<CteUser> inner = lightDao.lambdaQuery(CteUser.class).from(CteUser.class)
				.select(JoinCol.raw("*")).eq(CteUser::getStatus, 1);
		List<CteUserDeptVO> rows = lightDao.lambdaQuery(CteUserDeptVO.class).with("u", inner).fromCte("u")
				.leftJoin(CteDept.class).on(CteUserDeptVO::getDeptId, CteDept::getDeptId)
				.select(JoinCol.of(CteDept::getDeptName).as("deptName")).find();
		assertEquals(3, rows.size());
		assertEquals("研发部", rows.get(0).getDeptName());
	}

	@Test
	@Order(10)
	@DisplayName("CTE引用CTE执行:b基于a")
	public void testCteRefsCteExecution() {
		LambdaQuery<CteDept> a = lightDao.lambdaQuery(CteDept.class).from(CteDept.class)
				.select(JoinCol.of(CteDept::getDeptId).as("deptId"), JoinCol.of(CteDept::getDeptName).as("deptName"))
				.eq(CteDept::getStatus, 1);
		LambdaQuery<CteDeptVO> b = lightDao.lambdaQuery(CteDeptVO.class).fromCte("a")
				.select(JoinCol.of(CteDeptVO::getDeptId).as("deptId"),
						JoinCol.of(CteDeptVO::getDeptName).as("deptName"));
		List<CteDeptVO> rows = lightDao.lambdaQuery(CteDeptVO.class).with("a", a).with("b", b).fromCte("b")
				.find();
		assertEquals(1, rows.size());   // a=status1的dept(研发部), b取其deptId
		assertEquals(10, rows.get(0).getDeptId().intValue());
	}

	@Test
	@Order(11)
	@DisplayName("递归CTE:WITH RECURSIVE组织树遍历")
	public void testRecursiveCte() {
		LambdaQuery<TreeVO> base = lightDao.lambdaQuery(TreeVO.class).from(CteOrg.class)
				.select(JoinCol.of(CteOrg::getId).as("id"), JoinCol.of(CteOrg::getPid).as("pid"),
						JoinCol.of(CteOrg::getName).as("name"))
				.isNull(CteOrg::getPid);
		// 递归分支必须innerJoin(仅当存在子节点才产行,否则LEFT JOIN无子也产行致递归不终止)
		LambdaQuery<TreeVO> rec = lightDao.lambdaQuery(TreeVO.class).fromCte("tree")
				.innerJoin(CteOrg.class).on(CteOrg::getPid, TreeVO::getId)
				.select(JoinCol.of(CteOrg::getId).as("id"), JoinCol.of(CteOrg::getPid).as("pid"),
						JoinCol.of(CteOrg::getName).as("name"));
		LambdaQuery<TreeVO> inner = base.unionAll(rec);
		// 递归CTE须给列清单(H2强制 WITH RECURSIVE t(id,pid,name) AS)
		List<TreeVO> tree = lightDao.lambdaQuery(TreeVO.class)
				.withRecursive("tree", inner, "id", "pid", "name").fromCte("tree").find();
		assertEquals(4, tree.size());   // root,a,b,a1 全遍历
	}

	@Test
	@Order(12)
	@DisplayName("@fast分页下推执行:findPage的recordCount与页行数")
	public void testFromFastFindPage() {
		LambdaQuery<CteUser> inner = lightDao.lambdaQuery(CteUser.class).from(CteUser.class)
				.select(JoinCol.raw("*")).orderByDesc(CteUser::getId);
		Page<CteUser> page = lightDao.lambdaQuery(CteUser.class).fromFast(inner, "t1")
				.findPage(new Page(2, 1));
		assertEquals(3L, page.getRecordCount());
		assertEquals(2, page.getRows().size());
	}

	@Test
	@Order(13)
	@DisplayName("@fast的count用内层简化SQL")
	public void testFromFastCount() {
		LambdaQuery<CteUser> inner = lightDao.lambdaQuery(CteUser.class).from(CteUser.class)
				.select(JoinCol.raw("*")).eq(CteUser::getStatus, 1);
		assertEquals(3L, lightDao.lambdaQuery(CteUser.class).fromFast(inner, "t1").count());
	}

	@Test
	@Order(14)
	@DisplayName("@fast派生表外层join实表")
	public void testFromFastJoin() {
		LambdaQuery<CteUser> inner = lightDao.lambdaQuery(CteUser.class).from(CteUser.class)
				.select(JoinCol.raw("*")).eq(CteUser::getStatus, 1);
		List<CteUserDeptVO> rows = lightDao.lambdaQuery(CteUserDeptVO.class).fromFast(inner, "t1")
				.leftJoin(CteDept.class).on(CteUserDeptVO::getDeptId, CteDept::getDeptId)
				.select(JoinCol.of(CteDept::getDeptName).as("deptName")).find();
		assertEquals(3, rows.size());
	}

	@Test
	@Order(15)
	@DisplayName("countSql自定义精简count:findPage总数取countSql而非自动count")
	public void testCountSql() {
		// countSql返回99(与真实行数3不同)以证明findPage的recordCount确实取自countSql
		LambdaQuery<CteUser> q = lightDao.lambdaQuery(CteUser.class).from(CteUser.class)
				.select(JoinCol.raw("*")).eq(CteUser::getStatus, 1).countSql("select 99");
		Page<CteUser> page = q.findPage(new Page(2, 1));
		assertEquals(99L, page.getRecordCount());
		assertEquals(2, page.getRows().size());
	}

	@Entity(tableName = "cte_dept")
	public static class CteDept implements Serializable {
		private static final long serialVersionUID = 1L;
		@Id
		@Column(name = "DEPT_ID")
		private Integer deptId;
		@Column(name = "DEPT_NAME")
		private String deptName;
		@Column(name = "STATUS")
		private Integer status;

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

		public Integer getStatus() {
			return status;
		}

		public void setStatus(Integer status) {
			this.status = status;
		}
	}

	@Entity(tableName = "cte_user")
	public static class CteUser implements Serializable {
		private static final long serialVersionUID = 1L;
		@Id
		@Column(name = "ID")
		private Integer id;
		@Column(name = "NAME")
		private String name;
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

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
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

	/**
	 * CTE结果VO:列名与CTE输出别名对齐
	 */
	@Entity(tableName = "cte_dept")
	public static class CteDeptVO implements Serializable {
		private static final long serialVersionUID = 1L;
		@Id
		@Column(name = "DEPTID")
		private Integer deptId;
		@Column(name = "DEPTNAME")
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

	@Entity(tableName = "cte_org")
	public static class CteOrg implements Serializable {
		private static final long serialVersionUID = 1L;
		@Id
		@Column(name = "ID")
		private Integer id;
		@Column(name = "PID")
		private Integer pid;
		@Column(name = "NAME")
		private String name;

		public Integer getId() {
			return id;
		}

		public void setId(Integer id) {
			this.id = id;
		}

		public Integer getPid() {
			return pid;
		}

		public void setPid(Integer pid) {
			this.pid = pid;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}
	}

	/**
	 * 递归CTE结果VO(列与CTE输出别名id/pid/name对齐)
	 */
	@Entity(tableName = "cte_org")
	public static class TreeVO implements Serializable {
		private static final long serialVersionUID = 1L;
		@Id
		@Column(name = "ID")
		private Integer id;
		@Column(name = "PID")
		private Integer pid;
		@Column(name = "NAME")
		private String name;

		public Integer getId() {
			return id;
		}

		public void setId(Integer id) {
			this.id = id;
		}

		public Integer getPid() {
			return pid;
		}

		public void setPid(Integer pid) {
			this.pid = pid;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}
	}

	/**
	 * CTE/@fast派生表join实表的结果VO(dept_id来自派生表,dept_name来自join的实表)
	 */
	@Entity(tableName = "cte_user")
	public static class CteUserDeptVO implements Serializable {
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
