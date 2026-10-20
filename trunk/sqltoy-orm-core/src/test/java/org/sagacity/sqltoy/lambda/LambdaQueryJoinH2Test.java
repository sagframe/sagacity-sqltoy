package org.sagacity.sqltoy.lambda;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import org.sagacity.sqltoy.exception.DataAccessException;
import org.sagacity.sqltoy.lambda.join.ExcludeCols;
import org.sagacity.sqltoy.lambda.join.JoinCol;
import org.sagacity.sqltoy.lambda.LambdaQuery;
import org.sagacity.sqltoy.lambda.LambdaDelete;
import org.sagacity.sqltoy.lambda.LambdaUpdate;
import org.sagacity.sqltoy.model.Page;

/**
 * @description 多表join查询/更新/删除端到端测试,H2内存库;join查询执行验证,
 *              join更新/删除为MySQL风格方言,做sql文本断言(H2不支持多表update/delete语法)
 * @author zhongxuchen
 */
@DisplayName("lambda join多表操作测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class LambdaQueryJoinH2Test {

	private static final String URL = "jdbc:h2:mem:lambdajoin;DB_CLOSE_DELAY=-1";
	private static JdbcDataSource dataSource;
	private static SqlToyContext context;
	private static LightDao lightDao;

	@Entity(tableName = "lambda_join_user")
	public static class JoinUser implements Serializable {

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

	@Entity(tableName = "lambda_join_dept")
	public static class JoinDept implements Serializable {

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

	/**
	 * 查询结果VO,无注解,按select别名映射
	 */
	public static class JoinUserVO {

		private Integer id;
		private String staffName;
		private String deptName;

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

		public String getDeptName() {
			return deptName;
		}

		public void setDeptName(String deptName) {
			this.deptName = deptName;
		}
	}

	@BeforeAll
	public static void init() throws Exception {
		try (Connection conn = DriverManager.getConnection(URL, "sa", ""); Statement st = conn.createStatement()) {
			st.execute("drop table if exists lambda_join_user");
			st.execute("drop table if exists lambda_join_dept");
			st.execute(
					"create table lambda_join_user (id int primary key, staff_name varchar(50), dept_id int, status int)");
			st.execute("create table lambda_join_dept (dept_id int primary key, dept_name varchar(50), status int)");
			st.execute("insert into lambda_join_dept values (10,'研发部',1)");
			st.execute("insert into lambda_join_dept values (20,'市场部',1)");
			st.execute("insert into lambda_join_user values (1,'张三',10,1)");
			st.execute("insert into lambda_join_user values (2,'李四',20,1)");
			st.execute("insert into lambda_join_user values (3,'王五',10,0)");
			st.execute("insert into lambda_join_user values (4,'赵六',null,1)");
		}
		dataSource = new JdbcDataSource();
		dataSource.setURL(URL);
		dataSource.setUser("sa");
		context = new SqlToyContext();
		context.setDefaultDataSource(dataSource);
		context.initialize();
		lightDao = new DefaultLightDaoImpl(context);
	}

	@Test
	@Order(16)
	@DisplayName("selectAll(Class)dump被join实体全部字段")
	public void testSelectAllJoinedEntity() {
		LambdaQuery<JoinUserVO> query = lightDao.lambdaQuery(JoinUserVO.class)
				.selectAll(JoinDept.class)
				.from(JoinUser.class).leftJoin(JoinDept.class).on(JoinUser::getDeptId, JoinDept::getDeptId)
				.eq(JoinUser::getStatus, 1).orderByAsc(JoinUser::getId);
		query.assemble(context::getEntityMeta);
		assertEquals(
				"SELECT j2.DEPT_NAME AS deptName, j2.STATUS AS status, j2.DEPT_ID AS deptId "
						+ "FROM lambda_join_user j1 LEFT JOIN lambda_join_dept j2 ON j1.DEPT_ID = j2.DEPT_ID "
						+ "WHERE j1.STATUS = :p1 ORDER BY j1.ID ASC",
				normalized(query.getSql()));
		List<JoinUserVO> rows = query.find();
		assertEquals(3, rows.size());
		assertEquals("研发部", rows.get(0).getDeptName());
		// 未select主表字段,VO对应属性为null
		assertNull(rows.get(0).getStaffName());
		// 赵六无部门,LEFT JOIN下dump列全为null
		assertNull(rows.get(2).getDeptName());

		// 与主表重复的实体自动跳过,不产生重复列
		LambdaQuery<JoinUser> dedupe = lightDao.lambdaQuery(JoinUser.class).selectAll()
				.selectAll(JoinUser.class).selectAll(JoinDept.class)
				.from(JoinUser.class).leftJoin(JoinDept.class).on(JoinUser::getDeptId, JoinDept::getDeptId);
		dedupe.assemble(context::getEntityMeta);
		String sql = normalized(dedupe.getSql());
		// 主表dump一次;关联表dump与主表同名的列(deptId/status)跳过,仅deptName并入
		assertEquals(sql.indexOf("AS staffName"), sql.lastIndexOf("AS staffName"));
		assertEquals(sql.indexOf("AS deptId"), sql.lastIndexOf("AS deptId"));
		assertTrue(sql.contains("j2.DEPT_NAME AS deptName"));
		assertTrue(!sql.contains("j2.STATUS AS status"));

		// 未参与join的实体dump快速失败
		assertThrows(DataAccessException.class, () -> lightDao.lambdaQuery(JoinUserVO.class)
				.selectAll(JoinDept.class).from(JoinUser.class).find());
	}

	@Test
	@Order(17)
	@DisplayName("join查询空OR分组:不得把后续条件并成OR扩大结果")
	public void testJoinEmptyOrGroup() {
		List<JoinUser> rows = lightDao.lambdaQuery(JoinUser.class).leftJoin(JoinDept.class)
				.on(JoinUser::getDeptId, JoinDept::getDeptId).eq(JoinUser::getStatus, 1)
				.or(w -> w.eqIfPresent(JoinDept::getDeptName, null)).eq(JoinUser::getId, 4).find();
		assertEquals(1, rows.size(), "正确语义应为 status=1 AND id=4,仅赵六一行");
		assertEquals(Integer.valueOf(4), rows.get(0).getId());
	}

	@Test
	@Order(18)
	@DisplayName("join更新空OR分组:EXISTS子查询内不得把条件并成OR")
	public void testJoinUpdateEmptyOrGroup() {
		LambdaUpdate<JoinUser> update = lightDao.lambdaUpdate(JoinUser.class).leftJoin(JoinDept.class)
				.on(JoinUser::getDeptId, JoinDept::getDeptId).set(JoinUser::getStatus, 5)
				.eq(JoinUser::getStatus, 1).or(w -> w.eqIfPresent(JoinDept::getDeptName, null))
				.eq(JoinUser::getId, 4);
		update.assemble(context::getEntityMeta);
		String sql = normalized(update.getSql());
		assertTrue(!sql.contains(" OR "), "空OR分组后不得出现OR: " + sql);
		assertTrue(sql.contains("j1.STATUS = :p2 AND j1.ID = :p3"), "条件应按AND连接: " + sql);
	}

	@Test
	@Order(19)
	@DisplayName("join setExpr命名参数:单个:name必须绑定value")
	public void testJoinSetExprNamedParam() {
		LambdaUpdate<JoinUser> update = lightDao.lambdaUpdate(JoinUser.class).leftJoin(JoinDept.class)
				.on(JoinUser::getDeptId, JoinDept::getDeptId)
				.setExpr(JoinUser::getStatus, "status + :inc", 2).eq(JoinDept::getDeptName, "研发部");
		update.assemble(context::getEntityMeta);
		String sql = normalized(update.getSql());
		assertTrue(sql.contains("STATUS = status + :p1"), "命名参数应改写为内部参数: " + sql);
		assertEquals(2, update.getParams().get("p1"), "命名参数必须绑定到value");
	}

	@Test
	@Order(20)
	@DisplayName("join更新同列重复set:保留最后一次,不得生成重复SET片段")
	public void testJoinDuplicateSetKeepsLast() {
		LambdaUpdate<JoinUser> update = lightDao.lambdaUpdate(JoinUser.class).leftJoin(JoinDept.class)
				.on(JoinUser::getDeptId, JoinDept::getDeptId).set(JoinUser::getStatus, 1)
				.set(JoinUser::getStatus, 2).eq(JoinDept::getDeptName, "研发部");
		update.assemble(context::getEntityMeta);
		String sql = normalized(update.getSql());
		assertEquals(sql.indexOf("STATUS ="), sql.lastIndexOf("STATUS ="), "同一列只允许一个SET片段: " + sql);
		assertEquals(2, update.getParams().get("p2"), "重复set应保留最后一次值");
	}

	private static String normalized(String sql) {
		return sql == null ? "" : sql.replaceAll("\\s+", " ").trim();
	}

	@Test
	@Order(1)
	@DisplayName("缺省select查询主表全部字段")
	public void testDefaultSelect() {
		List<JoinUser> users = lightDao.lambdaQuery(JoinUser.class).leftJoin(JoinDept.class)
				.on(JoinUser::getDeptId, JoinDept::getDeptId).eq(JoinUser::getStatus, 1)
				.orderByAsc(JoinUser::getId).find();
		assertEquals(3, users.size());
		assertEquals("张三", users.get(0).getStaffName());
		// 赵六无部门,LEFT JOIN不丢行
		assertEquals(4, users.get(2).getId().intValue());
	}

	@Test
	@Order(2)
	@DisplayName("select别名映射到VO")
	public void testSelectAliasToVo() {
		List<JoinUserVO> rows = lightDao.lambdaQuery(JoinUserVO.class)
				.select(JoinCol.of(JoinUser::getId).as("id"), JoinCol.of(JoinUser::getStaffName).as("staffName"),
						JoinCol.of(JoinDept::getDeptName).as("deptName"))
				.from(JoinUser.class).leftJoin(JoinDept.class).on(JoinUser::getDeptId, JoinDept::getDeptId)
				.eq(JoinUser::getStatus, 1).orderByAsc(JoinUser::getId).find();
		assertEquals(3, rows.size());
		assertEquals("研发部", rows.get(0).getDeptName());
		// 赵六无部门,左连接deptName为null
		assertNull(rows.get(2).getDeptName());
		assertNotNull(rows.get(2).getStaffName());
	}

	@Test
	@Order(3)
	@DisplayName("join条件与count")
	public void testJoinCount() {
		assertEquals(3L, lightDao.lambdaQuery(JoinUser.class).leftJoin(JoinDept.class)
				.on(JoinUser::getDeptId, JoinDept::getDeptId).eq(JoinUser::getStatus, 1).count());
		assertEquals(2L, lightDao.lambdaQuery(JoinUser.class).leftJoin(JoinDept.class)
				.on(JoinUser::getDeptId, JoinDept::getDeptId).eq(JoinDept::getDeptName, "研发部").count());
		assertTrue(lightDao.lambdaQuery(JoinUser.class).leftJoin(JoinDept.class)
				.on(JoinUser::getDeptId, JoinDept::getDeptId).eq(JoinDept::getDeptName, "市场部").exists());
	}

	@Test
	@Order(4)
	@DisplayName("join分页查询")
	public void testJoinFindPage() {
		Page<JoinUser> page = lightDao.lambdaQuery(JoinUser.class).leftJoin(JoinDept.class)
				.on(JoinUser::getDeptId, JoinDept::getDeptId).eq(JoinUser::getStatus, 1)
				.orderByAsc(JoinUser::getId).findPage(new Page(2, 1));
		assertEquals(3L, page.getRecordCount());
		assertEquals(2, page.getRows().size());
		assertEquals(1, ((JoinUser) page.getRows().get(0)).getId().intValue());
	}

	@Test
	@Order(5)
	@DisplayName("getOne与嵌套or条件")
	public void testGetOneWithNested() {
		JoinUser user = lightDao.lambdaQuery(JoinUser.class).leftJoin(JoinDept.class)
				.on(JoinUser::getDeptId, JoinDept::getDeptId)
				.eq(JoinUser::getStatus, 0)
				.or(nested -> nested.eq(JoinDept::getDeptName, "研发部").eq(JoinUser::getId, 3))
				.getOne();
		assertNotNull(user);
		assertEquals(3, user.getId().intValue());
	}

	@Test
	@Order(6)
	@DisplayName("select列与排序的sql文本")
	public void testQuerySqlText() {
		LambdaQuery<JoinUserVO> join = LambdaQuery.create(JoinUserVO.class)
				.select(JoinCol.of(JoinUser::getStaffName).as("staffName"),
						JoinCol.count(JoinDept::getDeptId).as("deptCount"))
				.from(JoinUser.class).leftJoin(JoinDept.class).on(JoinUser::getDeptId, JoinDept::getDeptId)
				.eq(JoinUser::getStatus, 1).groupBy(JoinUser::getId).orderByDesc(JoinUser::getId);
		join.assemble(context::getEntityMeta);
		assertEquals(
				"SELECT j1.STAFF_NAME AS staffName, COUNT(j2.DEPT_ID) AS deptCount FROM lambda_join_user j1 "
						+ "LEFT JOIN lambda_join_dept j2 ON j1.DEPT_ID = j2.DEPT_ID "
						+ "WHERE j1.STATUS = :p1 GROUP BY j1.ID ORDER BY j1.ID DESC",
				normalized(join.getSql()));
	}

	@Test
	@Order(7)
	@DisplayName("同一join多个on等值条件自动AND")
	public void testMultiOnSqlText() {
		LambdaQuery<JoinUser> join = LambdaQuery.create(JoinUser.class).from(JoinUser.class)
				.leftJoin(JoinDept.class)
				.on(JoinUser::getDeptId, JoinDept::getDeptId, JoinUser::getStatus, JoinDept::getStatus)
				.eq(JoinUser::getId, 1);
		join.assemble(context::getEntityMeta);
		assertEquals(
				"SELECT j1.STAFF_NAME AS staffName, j1.DEPT_ID AS deptId, j1.STATUS AS status, j1.ID AS id "
						+ "FROM lambda_join_user j1 LEFT JOIN lambda_join_dept j2 "
						+ "ON j1.DEPT_ID = j2.DEPT_ID AND j1.STATUS = j2.STATUS WHERE j1.ID = :p1",
				normalized(join.getSql()));
	}

	@Test
	@Order(8)
	@DisplayName("join更新sql文本(MySQL方言)")
	public void testJoinUpdateSqlText() {
		LambdaUpdate<JoinUser> update = lightDao.lambdaUpdate(JoinUser.class).leftJoin(JoinDept.class)
				.on(JoinUser::getDeptId, JoinDept::getDeptId).set(JoinUser::getStatus, 5)
				.eq(JoinDept::getDeptName, "研发部");
		update.assemble(context::getEntityMeta);
		assertEquals(
				"UPDATE lambda_join_user SET STATUS = :p1 WHERE EXISTS (SELECT 1 FROM (SELECT j1.ID AS LPK1 "
						+ "FROM lambda_join_user j1 LEFT JOIN lambda_join_dept j2 ON j1.DEPT_ID = j2.DEPT_ID "
						+ "WHERE j2.DEPT_NAME = :p2) sqd WHERE sqd.LPK1 = lambda_join_user.ID)",
				normalized(update.getSql()));
	}

	@Test
	@Order(9)
	@DisplayName("join删除sql文本(MySQL方言)")
	public void testJoinDeleteSqlText() {
		LambdaDelete<JoinUser> delete = lightDao.lambdaDelete(JoinUser.class).leftJoin(JoinDept.class)
				.on(JoinUser::getDeptId, JoinDept::getDeptId).eq(JoinDept::getDeptName, "市场部");
		delete.assemble(context::getEntityMeta);
		assertEquals(
				"DELETE FROM lambda_join_user WHERE EXISTS (SELECT 1 FROM (SELECT j1.ID AS LPK1 "
						+ "FROM lambda_join_user j1 LEFT JOIN lambda_join_dept j2 ON j1.DEPT_ID = j2.DEPT_ID "
						+ "WHERE j2.DEPT_NAME = :p1) sqd WHERE sqd.LPK1 = lambda_join_user.ID)",
				normalized(delete.getSql()));
	}

	@Test
	@Order(10)
	@DisplayName("join完整性校验:缺on/未参与join的实体")
	public void testJoinValidation() {
		// 缺on:发起join后未调用on关联
		LambdaQuery<JoinUser> noOn = LambdaQuery.create(JoinUser.class).from(JoinUser.class);
		noOn.leftJoin(JoinDept.class);
		assertThrows(DataAccessException.class, () -> noOn.assemble(context::getEntityMeta));
		// join更新缺on同样快速失败
		LambdaUpdate<JoinUser> noOnUpdate = lightDao.lambdaUpdate(JoinUser.class);
		noOnUpdate.leftJoin(JoinDept.class);
		assertThrows(DataAccessException.class, () -> noOnUpdate.assemble(context::getEntityMeta));
		// 条件字段实体未参与join
		assertThrows(DataAccessException.class,
				() -> lightDao.lambdaQuery(JoinUser.class).from(JoinUser.class).eq(JoinDept::getDeptName, "研发部")
						.find());
	}

	@Test
	@Order(11)
	@DisplayName("join更新/删除无条件防护")
	public void testJoinWriteGuard() {
		LambdaUpdate<JoinUser> update = lightDao.lambdaUpdate(JoinUser.class).leftJoin(JoinDept.class)
				.on(JoinUser::getDeptId, JoinDept::getDeptId).set(JoinUser::getStatus, 9);
		update.assemble(context::getEntityMeta);
		assertThrows(DataAccessException.class, () -> update.requireCondition("update"));

		LambdaDelete<JoinUser> delete = lightDao.lambdaDelete(JoinUser.class).leftJoin(JoinDept.class)
				.on(JoinUser::getDeptId, JoinDept::getDeptId);
		delete.assemble(context::getEntityMeta);
		assertThrows(DataAccessException.class, () -> delete.requireCondition("delete"));
	}

	@Test
	@Order(15)
	@DisplayName("join通道findTop取前N条")
	public void testJoinFindTop() {
		List<JoinUser> top = lightDao.lambdaQuery(JoinUser.class)
				.leftJoin(JoinDept.class).on(JoinUser::getDeptId, JoinDept::getDeptId)
				.eq(JoinUser::getStatus, 1).orderByAsc(JoinUser::getId).findTop(2);
		assertEquals(2, top.size());
		assertEquals(1, top.get(0).getId().intValue());
	}

	@Test
	@Order(14)
	@DisplayName("selectAll支持排除单个或多个字段")
	public void testSelectAllExclude() {
		LambdaQuery<JoinUserVO> query = lightDao.lambdaQuery(JoinUserVO.class)
				.selectAll(ExcludeCols.of(JoinUser::getStatus)).from(JoinUser.class)
				.leftJoin(JoinDept.class).on(JoinUser::getDeptId, JoinDept::getDeptId)
				.select(JoinCol.of(JoinDept::getDeptName).as("deptName"))
				.eq(JoinUser::getStatus, 1);
		query.assemble(context::getEntityMeta);
		// STATUS被排除,主表其余字段在前,JoinCol额外列在后
		assertEquals(
				"SELECT j1.STAFF_NAME AS staffName, j1.DEPT_ID AS deptId, j1.ID AS id, "
						+ "j2.DEPT_NAME AS deptName FROM lambda_join_user j1 LEFT JOIN lambda_join_dept j2 "
						+ "ON j1.DEPT_ID = j2.DEPT_ID WHERE j1.STATUS = :p1",
				normalized(query.getSql()));
		assertEquals(3, query.find().size());

		// 多字段排除:varargs传入多个方法引用
		LambdaQuery<JoinUserVO> multi = lightDao.lambdaQuery(JoinUserVO.class)
				.selectAll(ExcludeCols.of(JoinUser::getStatus, JoinUser::getDeptId)).from(JoinUser.class)
				.leftJoin(JoinDept.class).on(JoinUser::getDeptId, JoinDept::getDeptId)
				.select(JoinCol.of(JoinDept::getDeptName).as("deptName"))
				.eq(JoinUser::getStatus, 1);
		multi.assemble(context::getEntityMeta);
		assertEquals(
				"SELECT j1.STAFF_NAME AS staffName, j1.ID AS id, "
						+ "j2.DEPT_NAME AS deptName FROM lambda_join_user j1 LEFT JOIN lambda_join_dept j2 "
						+ "ON j1.DEPT_ID = j2.DEPT_ID WHERE j1.STATUS = :p1",
				normalized(multi.getSql()));
		assertEquals(3, multi.find().size());
	}

	@Test
	@Order(13)
	@DisplayName("selectAll缺省主表全部字段并叠加join表字段")
	public void testSelectAllWithJoinColumns() {
		LambdaQuery<JoinUserVO> query = lightDao.lambdaQuery(JoinUserVO.class)
				.selectAll().from(JoinUser.class)
				.leftJoin(JoinDept.class).on(JoinUser::getDeptId, JoinDept::getDeptId)
				.select(JoinCol.of(JoinDept::getDeptName).as("deptName"))
				.eq(JoinUser::getStatus, 1).orderByAsc(JoinUser::getId);
		query.assemble(context::getEntityMeta);
		// 主表全部字段在前(属性别名),JoinCol额外列在后
		assertEquals(
				"SELECT j1.STAFF_NAME AS staffName, j1.DEPT_ID AS deptId, j1.STATUS AS status, j1.ID AS id, "
						+ "j2.DEPT_NAME AS deptName FROM lambda_join_user j1 LEFT JOIN lambda_join_dept j2 "
						+ "ON j1.DEPT_ID = j2.DEPT_ID WHERE j1.STATUS = :p1 ORDER BY j1.ID ASC",
				normalized(query.getSql()));
		List<JoinUserVO> rows = query.find();
		assertEquals(3, rows.size());
		assertEquals("张三", rows.get(0).getStaffName());
		assertEquals("研发部", rows.get(0).getDeptName());
		// 赵六无部门,LEFT JOIN下deptName为null
		assertNull(rows.get(2).getDeptName());
		assertNotNull(rows.get(2).getStaffName());
	}

	@Test
	@Order(12)
	@DisplayName("join getOne匹配多条视为条件缺陷抛出异常")
	public void testJoinGetOneStrict() {
		// 此时id=1、id=2两条记录status均为1
		assertThrows(DataAccessException.class,
				() -> lightDao.lambdaQuery(JoinUser.class).leftJoin(JoinDept.class)
						.on(JoinUser::getDeptId, JoinDept::getDeptId).eq(JoinUser::getStatus, 1).getOne());
		// 唯一匹配仍正常返回
		JoinUser user = lightDao.lambdaQuery(JoinUser.class).leftJoin(JoinDept.class)
				.on(JoinUser::getDeptId, JoinDept::getDeptId).eq(JoinUser::getId, 1).getOne();
		assertNotNull(user);
		assertEquals("张三", user.getStaffName());
	}

	@Test
	@Order(17)
	@DisplayName("select原生片段(JoinCol.raw)的sql文本:窗口函数原样拼入+别名")
	public void testSelectRawSqlText() {
		LambdaQuery<JoinUserVO> join = LambdaQuery.create(JoinUserVO.class)
				.select(JoinCol.of(JoinUser::getStaffName).as("staffName"),
						JoinCol.raw("ROW_NUMBER() OVER(ORDER BY j1.ID)").as("rn"))
				.from(JoinUser.class).eq(JoinUser::getStatus, 1);
		join.assemble(context::getEntityMeta);
		// 原生片段原样拼入SELECT,与类型化列共存;raw列触发join/SQL通道
		assertEquals("SELECT j1.STAFF_NAME AS staffName, ROW_NUMBER() OVER(ORDER BY j1.ID) AS rn "
				+ "FROM lambda_join_user j1 WHERE j1.STATUS = :p1", normalized(join.getSql()));
	}

	@Test
	@Order(18)
	@DisplayName("select原生片段(JoinCol.raw)执行:窗口函数ROW_NUMBER映射到VO字段")
	public void testSelectRawExecution() {
		// 原生窗口函数片段映射到VO的id字段,与类型化列staffName共存
		List<JoinUserVO> rows = lightDao.lambdaQuery(JoinUserVO.class)
				.select(JoinCol.raw("ROW_NUMBER() OVER(ORDER BY j1.ID)").as("id"),
						JoinCol.of(JoinUser::getStaffName).as("staffName"))
				.from(JoinUser.class).eq(JoinUser::getStatus, 1).orderByAsc(JoinUser::getId).find();
		// status=1 → 张三(id1)/李四(id2)/赵六(id4),按id排序,ROW_NUMBER=1/2/3
		assertEquals(3, rows.size());
		assertEquals(1, rows.get(0).getId().intValue());
		assertEquals("张三", rows.get(0).getStaffName());
		assertEquals(3, rows.get(2).getId().intValue());
		assertEquals("赵六", rows.get(2).getStaffName());
	}
}
