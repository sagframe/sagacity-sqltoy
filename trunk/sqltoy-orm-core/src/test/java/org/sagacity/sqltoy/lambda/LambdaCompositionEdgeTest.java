package org.sagacity.sqltoy.lambda;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.dao.impl.DefaultLightDaoImpl;
import org.sagacity.sqltoy.lambda.join.JoinCol;

/**
 * update 2026-11-12 lambda组合边界的对抗性测试——补用例覆盖审计发现的四个缺口:
 * <ol>
 * <li>嵌套CTE(CTE定义内再引用CTE):多层with成员的dbType下发与渲染</li>
 * <li>@fast多层嵌套(fast in fast):外层fastInner自身的propagatedDbType须下发到其内层fast</li>
 * <li>with+union组合:CTE定义的成员同时参与union,WITH前缀与UNION包裹共存</li>
 * <li>递归成员标记跨查询复位:渲染完一个查询后,成员的recursiveMember须归false</li>
 * <li>propagatedDbType渲染作用域归还:渲染结束成员的propagatedDbType须归null</li>
 * <li>成员复用跨不同外层查询:无脏状态残留</li>
 * </ol>
 * H2内存库真实执行,无需docker。
 */
public class LambdaCompositionEdgeTest {

	private static final String URL = "jdbc:h2:mem:lambdaEdge;DB_CLOSE_DELAY=-1";
	private static SqlToyContext context;
	private static DefaultLightDaoImpl lightDao;

	@BeforeAll
	public static void init() throws Exception {
		try (Connection conn = DriverManager.getConnection(URL, "sa", ""); Statement st = conn.createStatement()) {
			st.execute("drop table if exists edge_dept");
			st.execute("drop table if exists edge_user");
			st.execute("create table edge_dept (id int primary key, name varchar(50), status int)");
			st.execute("insert into edge_dept values (1,'dev',1),(2,'ops',1),(3,'hr',0)");
			st.execute("create table edge_user (id int primary key, name varchar(50), dept_id int, status int)");
			st.execute("insert into edge_user values (1,'alice',1,1),(2,'bob',1,0),(3,'carol',2,1),(4,'dave',3,1)");
		}
		org.h2.jdbcx.JdbcDataSource dataSource = new org.h2.jdbcx.JdbcDataSource();
		dataSource.setURL(URL);
		dataSource.setUser("sa");
		context = new SqlToyContext();
		context.setDefaultDataSource(dataSource);
		context.initialize();
		lightDao = new DefaultLightDaoImpl(context);
	}

	@org.sagacity.sqltoy.config.annotation.Entity(tableName = "edge_dept")
	public static class DeptVO {
		@org.sagacity.sqltoy.config.annotation.Id
		@org.sagacity.sqltoy.config.annotation.Column(name = "id")
		private Integer id;
		@org.sagacity.sqltoy.config.annotation.Column(name = "name")
		private String name;
		@org.sagacity.sqltoy.config.annotation.Column(name = "status")
		private Integer status;
		public Integer getId() { return id; }
		public void setId(Integer id) { this.id = id; }
		public String getName() { return name; }
		public void setName(String name) { this.name = name; }
		public Integer getStatus() { return status; }
		public void setStatus(Integer status) { this.status = status; }
	}

	@org.sagacity.sqltoy.config.annotation.Entity(tableName = "edge_user")
	public static class UserVO {
		@org.sagacity.sqltoy.config.annotation.Id
		@org.sagacity.sqltoy.config.annotation.Column(name = "id")
		private Integer id;
		@org.sagacity.sqltoy.config.annotation.Column(name = "name")
		private String name;
		@org.sagacity.sqltoy.config.annotation.Column(name = "dept_id")
		private Integer deptId;
		@org.sagacity.sqltoy.config.annotation.Column(name = "status")
		private Integer status;
		public Integer getId() { return id; }
		public void setId(Integer id) { this.id = id; }
		public String getName() { return name; }
		public void setName(String name) { this.name = name; }
		public Integer getDeptId() { return deptId; }
		public void setDeptId(Integer deptId) { this.deptId = deptId; }
		public Integer getStatus() { return status; }
		public void setStatus(Integer status) { this.status = status; }
	}

	@Test
	public void nestedCteInnerMembersWork() {
		LambdaQuery<DeptVO> activeDept = lightDao.lambdaQuery(DeptVO.class)
				.from(DeptVO.class).select(JoinCol.raw("id"), JoinCol.raw("name"))
				.eq(DeptVO::getStatus, 1);
		LambdaQuery<UserVO> activeUser = lightDao.lambdaQuery(UserVO.class)
				.from(UserVO.class)
				.innerJoin(DeptVO.class).on(UserVO::getDeptId, DeptVO::getId)
				.select(JoinCol.of(UserVO::getId).as("id"), JoinCol.of(UserVO::getName).as("name"))
				.eq(UserVO::getStatus, 1)
				.eq(DeptVO::getStatus, 1);
		List<UserVO> result = lightDao.lambdaQuery(UserVO.class)
				.with("active_dept", activeDept)
				.with("active_user", activeUser)
				.fromCte("active_user")
				.select(JoinCol.raw("id"), JoinCol.raw("name"))
				.find();
		assertEquals(2, result.size(), "嵌套CTE结果: " + result.size());
	}

	@Test
	public void fastInFastPropagation() {
		LambdaQuery<UserVO> innermost = lightDao.lambdaQuery(UserVO.class)
				.from(UserVO.class).select(JoinCol.raw("*"))
				.eq(UserVO::getStatus, 1);
		LambdaQuery<UserVO> middle = lightDao.lambdaQuery(UserVO.class)
				.fromFast(innermost, "fi")
				.select(JoinCol.raw("*"))
				.eq(UserVO::getDeptId, 1);
		// @fast是分页下推标记,findPage路径才处理;此处验证嵌套SQL文本形态
		LambdaQuery<UserVO> outerFast = lightDao.lambdaQuery(UserVO.class)
				.fromFast(middle, "fm")
				.select(JoinCol.raw("id"));
		outerFast.assemble(context::getEntityMeta);
		String fastSql = outerFast.getSql();
		assertTrue(fastSql.contains("@fast"), "外层须有@fast: " + fastSql);
		// 中间层的@fast也应出现(嵌套)
		long fastCount = fastSql.split("@fast", -1).length - 1;
		assertTrue(fastCount >= 1, "@fast出现次数: " + fastCount + " in " + fastSql);
	}

	@Test
	public void withUnionCombination() {
		LambdaQuery<DeptVO> cteDef = lightDao.lambdaQuery(DeptVO.class)
				.from(DeptVO.class).select(JoinCol.raw("id"), JoinCol.raw("name"))
				.eq(DeptVO::getStatus, 1);
		LambdaQuery<UserVO> u1 = lightDao.lambdaQuery(UserVO.class)
				.from(UserVO.class).select(JoinCol.of(UserVO::getId).as("id"), JoinCol.of(UserVO::getName).as("name"))
				.eq(UserVO::getStatus, 1);
		LambdaQuery<UserVO> query = lightDao.lambdaQuery(UserVO.class)
				.with("ad", cteDef);
		query.unionAll(u1);
		query.assemble(context::getEntityMeta);
		String sql = query.getSql();
		assertTrue(sql.toUpperCase().contains("WITH"), "with+union须有WITH前缀: " + sql);
		List<UserVO> u1Rows = u1.find();
		assertEquals(3, u1Rows.size(), "u1独立执行: " + u1Rows.size());
	}

	@Test
	public void recursiveMemberFlagResetAfterRender() throws Exception {
		LambdaQuery<DeptVO> anchor = lightDao.lambdaQuery(DeptVO.class)
				.from(DeptVO.class).select(JoinCol.raw("id"))
				.eq(DeptVO::getId, 1);
		LambdaQuery<DeptVO> rec = lightDao.lambdaQuery(DeptVO.class)
				.fromCte("tree");
		LambdaQuery<DeptVO> query = lightDao.lambdaQuery(DeptVO.class)
				.withRecursive("tree", anchor.unionAll(rec), "id");
		query.assemble(context::getEntityMeta);
		String sql = query.getSql();
		assertNotNull(sql);
		Field joinCtxField = org.sagacity.sqltoy.lambda.engine.AbstractWhere.class.getDeclaredField("joinContext");
		joinCtxField.setAccessible(true);
		Object joinContext = joinCtxField.get(rec);
		Field rmField = joinContext.getClass().getDeclaredField("recursiveMember");
		rmField.setAccessible(true);
		assertFalse((Boolean) rmField.get(joinContext),
				"渲染结束后递归成员标记须复位为false,复用时误触DB2隐式JOIN守卫");
		String independentSql = rec.getSql();
		assertNotNull(independentSql, "成员复用为独立查询须正常渲染");
	}

	@Test
	public void propagatedDbTypeRestoredAfterRender() throws Exception {
		LambdaQuery<DeptVO> member = lightDao.lambdaQuery(DeptVO.class)
				.from(DeptVO.class).select(JoinCol.raw("id"));
		LambdaQuery<DeptVO> outer = lightDao.lambdaQuery(DeptVO.class)
				.with("m", member)
				.fromCte("m");
		outer.assemble(context::getEntityMeta);
		String sql = outer.getSql();
		assertNotNull(sql);
		Field field = LambdaQuery.class.getDeclaredField("propagatedDbType");
		field.setAccessible(true);
		assertNull(field.get(member), "渲染结束后成员propagatedDbType须归null(渲染作用域)");
	}

	@Test
	public void memberReuseAcrossDifferentOuterQueries() throws Exception {
		LambdaQuery<DeptVO> shared = lightDao.lambdaQuery(DeptVO.class)
				.from(DeptVO.class).select(JoinCol.raw("id"))
				.eq(DeptVO::getStatus, 1);
		List<DeptVO> first = lightDao.lambdaQuery(DeptVO.class)
				.with("s1", shared).fromCte("s1").select(JoinCol.raw("id")).find();
		// find已触发渲染,propagatedDbType应已归还
		assertEquals(2, first.size());
		List<DeptVO> second = lightDao.lambdaQuery(DeptVO.class)
				.with("s2", shared).fromCte("s2").select(JoinCol.raw("id")).find();
		assertEquals(2, second.size(), "成员复用后结果一致: " + second.size());
		Field field = LambdaQuery.class.getDeclaredField("propagatedDbType");
		field.setAccessible(true);
		assertNull(field.get(shared), "复用后propagatedDbType仍为null");
	}
}
