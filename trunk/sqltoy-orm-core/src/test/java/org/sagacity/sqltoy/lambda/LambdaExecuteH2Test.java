package org.sagacity.sqltoy.lambda;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Arrays;
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
import org.sagacity.sqltoy.dao.SqlToyDao;
import org.sagacity.sqltoy.dao.SqlToyLazyDao;
import org.sagacity.sqltoy.dao.impl.DefaultLazyDaoImpl;
import org.sagacity.sqltoy.dao.impl.DefaultLightDaoImpl;
import org.sagacity.sqltoy.dao.impl.DefaultSqlToyDaoImpl;
import org.sagacity.sqltoy.exception.DataAccessException;
import org.sagacity.sqltoy.model.Page;

/**
 * @description lambda查询/更新/删除端到端执行测试,H2内存库+DefaultLightDaoImpl非spring场景,
 *              覆盖属性到数据库字段映射、分页统计、无条件防护、where无命名参数兜底等链路
 * @author zhongxuchen
 */
@DisplayName("lambda操作H2执行测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class LambdaExecuteH2Test {

	private static final String URL = "jdbc:h2:mem:lambdaexec;DB_CLOSE_DELAY=-1";
	private static JdbcDataSource dataSource;
	private static SqlToyContext context;
	private static LightDao lightDao;

	@Entity(tableName = "lambda_user")
	public static class User implements Serializable {

		private static final long serialVersionUID = 1L;

		@Id
		@Column(name = "ID")
		private Integer id;

		@Column(name = "STAFF_NAME")
		private String staffName;

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

	@BeforeAll
	public static void init() throws Exception {
		try (Connection conn = DriverManager.getConnection(URL, "sa", ""); Statement st = conn.createStatement()) {
			st.execute("drop table if exists lambda_user");
			st.execute(
					"create table lambda_user (id int primary key, staff_name varchar(50), status int, salary numeric(10,2))");
			st.execute("insert into lambda_user values (1,'张三',1,1000)");
			st.execute("insert into lambda_user values (2,'李四',1,2000)");
			st.execute("insert into lambda_user values (3,'王五',0,3000)");
			st.execute("insert into lambda_user values (4,'赵六',0,4000)");
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
	@Order(1)
	@DisplayName("eq条件查询且属性映射到数据库字段")
	public void testFindWithColumnMapping() {
		List<User> users = lightDao.lambdaQuery(User.class).eq(User::getStaffName, "张三").find();
		assertEquals(1, users.size());
		assertEquals(1, users.get(0).getId().intValue());
	}

	@Test
	@Order(2)
	@DisplayName("链式条件+排序+select字段")
	public void testChainedFind() {
		List<User> users = lightDao.lambdaQuery(User.class).eq(User::getStatus, 1)
				.orderByDesc(User::getId).find();
		assertEquals(2, users.size());
		assertEquals(2, users.get(0).getId().intValue());

		List<User> selected = lightDao.lambdaQuery(User.class).select(User::getId, User::getStaffName)
				.eq(User::getId, 1).find();
		assertEquals(1, selected.size());
		assertEquals("张三", selected.get(0).getStaffName());
	}

	@Test
	@Order(3)
	@DisplayName("in条件与getOne")
	public void testInAndGetOne() {
		List<User> users = lightDao.lambdaQuery(User.class).in(User::getId, Arrays.asList(1, 2)).find();
		assertEquals(2, users.size());

		User one = lightDao.lambdaQuery(User.class).eq(User::getId, 3).getOne();
		assertNotNull(one);
		assertEquals("王五", one.getStaffName());

		User none = lightDao.lambdaQuery(User.class).eq(User::getStaffName, "不存在").getOne();
		assertNull(none);
	}

	@Test
	@Order(4)
	@DisplayName("分页查询")
	public void testFindPage() {
		Page<User> page = lightDao.lambdaQuery(User.class).orderByAsc(User::getId).findPage(new Page(2, 1));
		assertEquals(4L, page.getRecordCount());
		assertEquals(2, page.getRows().size());
		assertEquals(1, ((User) page.getRows().get(0)).getId().intValue());
	}

	@Test
	@Order(5)
	@DisplayName("count与exists")
	public void testCountAndExists() {
		assertEquals(2L, lightDao.lambdaQuery(User.class).eq(User::getStatus, 1).count());
		assertTrue(lightDao.lambdaQuery(User.class).eq(User::getStatus, 0).exists());
		assertFalse(lightDao.lambdaQuery(User.class).eq(User::getStaffName, "不存在").exists());
	}

	@Test
	@Order(6)
	@DisplayName("lambdaUpdate按set与条件更新")
	public void testLambdaUpdate() {
		long updated = lightDao.lambdaUpdate(User.class).set(User::getSalary, new BigDecimal("1500"))
				.eq(User::getId, 1).update();
		assertEquals(1L, updated);
		User user = lightDao.lambdaQuery(User.class).eq(User::getId, 1).getOne();
		assertEquals(0, new BigDecimal("1500").compareTo(user.getSalary()));
	}


	@Test
	@Order(8)
	@DisplayName("无条件更新须显式matchAll")
	public void testUpdateWithoutCondition() {
		assertThrows(DataAccessException.class,
				() -> lightDao.lambdaUpdate(User.class).set(User::getStatus, 9).update());
		long updated = lightDao.lambdaUpdate(User.class).set(User::getStatus, 9).matchAll().update();
		assertEquals(4L, updated);
		// 还原状态供后续用例使用,id=4保持9
		lightDao.lambdaUpdate(User.class).set(User::getStatus, 1).in(User::getId, Arrays.asList(1, 2)).update();
		lightDao.lambdaUpdate(User.class).set(User::getStatus, 0).eq(User::getId, 3).update();
	}

	@Test
	@Order(9)
	@DisplayName("where无命名参数时兜底更新")
	public void testUpdateLiteralWhere() {
		// inSql字面量子查询不产生命名参数,触发1=:DEFAULT_VALUE兜底逻辑
		long updated = lightDao.lambdaUpdate(User.class).set(User::getStatus, 0)
				.inSql(User::getId, "select id from lambda_user where staff_name = '赵六'").update();
		assertEquals(1L, updated);
		assertEquals(1L, lightDao.lambdaQuery(User.class).eq(User::getStaffName, "赵六").eq(User::getStatus, 0)
				.count());
	}

	@Test
	@Order(10)
	@DisplayName("lambdaDelete按条件删除")
	public void testLambdaDelete() {
		long deleted = lightDao.lambdaDelete(User.class).eq(User::getId, 3).delete();
		assertEquals(1L, deleted);
		assertFalse(lightDao.lambdaQuery(User.class).eq(User::getId, 3).exists());
	}

	@Test
	@Order(11)
	@DisplayName("lambdaQuery终端delete与无条件防护")
	public void testDeleteViaQuery() {
		assertThrows(DataAccessException.class, () -> lightDao.lambdaDelete(User.class).delete());
		long deleted = lightDao.lambdaDelete(User.class).eq(User::getStatus, 0).delete();
		assertEquals(1L, deleted);
		assertEquals(2L, lightDao.lambdaQuery(User.class).count());
	}

	@Test
	@Order(12)
	@DisplayName("无条件删除须显式matchAll")
	public void testDeleteMatchAll() {
		assertThrows(DataAccessException.class, () -> lightDao.lambdaDelete(User.class).delete());
	}

	@Test
	@Order(13)
	@DisplayName("setExpr表达式更新:基于当前值自增与比例计算")
	public void testSetExpr() {
		// 字面量表达式自增(id=1当前salary为1500)
		lightDao.lambdaUpdate(User.class).setExpr(User::getSalary, "salary + 500").eq(User::getId, 1).update();
		User user = lightDao.lambdaQuery(User.class).eq(User::getId, 1).getOne();
		assertEquals(0, new BigDecimal("2000").compareTo(user.getSalary()));
		// ?占位绑定参数值
		lightDao.lambdaUpdate(User.class).setExpr(User::getSalary, "salary * ?", new BigDecimal("1.1"))
				.eq(User::getId, 1).update();
		user = lightDao.lambdaQuery(User.class).eq(User::getId, 1).getOne();
		assertEquals(0, new BigDecimal("2200").compareTo(user.getSalary()));
		// 引用其他字段参与计算
		lightDao.lambdaUpdate(User.class).setExpr(User::getStatus, "id - 1").eq(User::getId, 2).update();
		user = lightDao.lambdaQuery(User.class).eq(User::getId, 2).getOne();
		assertEquals(1, user.getStatus().intValue());
	}

	@Test
	@Order(18)
	@DisplayName("未绑定dao的构建器经bind后执行")
	public void testUnboundQueryExecution() {
		LambdaQuery<User> query = LambdaQuery.create(User.class).eq(User::getStatus, 1)
				.orderByAsc(User::getId);
		List<User> users = query.bind(lightDao).find();
		assertEquals(2, users.size());
		assertEquals(1, users.get(0).getId().intValue());
		// bind 幂等重绑,同一构建器可继续执行
		assertEquals(2L, query.count());
	}

	@Test
	@Order(19)
	@DisplayName("删除通道无命名参数where兜底与matchAll清空(末尾执行)")
	public void testDeleteParamlessAndMatchAll() {
		long one = lightDao.lambdaDelete(User.class)
				.inSql(User::getId, "select id from lambda_user where id = 1").delete();
		assertEquals(1L, one);
		long all = lightDao.lambdaDelete(User.class).matchAll().delete();
		assertEquals(1L, all);
		assertEquals(0L, lightDao.lambdaQuery(User.class).count());
	}

	@Test
	@Order(15)
	@DisplayName("findTop取前N条与按比例取")
	public void testFindTop() {
		// 此时status=1的记录为id=1、id=2两条
		List<User> top = lightDao.lambdaQuery(User.class).eq(User::getStatus, 1)
				.orderByAsc(User::getId).findTop(1);
		assertEquals(1, top.size());
		assertEquals(1, top.get(0).getId().intValue());
		// 小于1按比例:0.5*2=1条
		assertEquals(1, lightDao.lambdaQuery(User.class).eq(User::getStatus, 1).findTop(0.5).size());
	}


	@Test
	@Order(16)
	@DisplayName("findRandom随机取N条")
	public void testFindRandom() {
		List<User> random = lightDao.lambdaQuery(User.class).eq(User::getStatus, 1).findRandom(2);
		assertEquals(2, random.size());
		assertEquals(1, lightDao.lambdaQuery(User.class).eq(User::getStatus, 1).findRandom(1).size());
	}

	@Test
	@Order(14)
	@DisplayName("getOne匹配多条视为条件缺陷抛出异常")
	public void testGetOneStrict() {
		// 此时id=1、id=2两条记录status均为1
		assertThrows(DataAccessException.class,
				() -> lightDao.lambdaQuery(User.class).eq(User::getStatus, 1).getOne());
		// 唯一匹配仍正常返回
		User user = lightDao.lambdaQuery(User.class).eq(User::getId, 1).getOne();
		assertNotNull(user);
	}

	@Test
	@Order(17)
	@DisplayName("装配期类型校验:Integer字段绑定String快速失败")
	public void testTypeCheck() {
		assertThrows(DataAccessException.class,
				() -> lightDao.lambdaQuery(User.class).eq(User::getStatus, "0").find());
		assertThrows(DataAccessException.class,
				() -> lightDao.lambdaQuery(User.class).in(User::getStatus, "1").find());
		assertThrows(DataAccessException.class,
				() -> lightDao.lambdaQuery(User.class).between(User::getStatus, "1", "2").find());
		assertThrows(DataAccessException.class,
				() -> lightDao.lambdaUpdate(User.class).set(User::getStatus, "0")
						.eq(User::getId, 1).update());
		// 显式关闭后只校验SQL可组装,不执行(值仍可能被数据库拒绝)
		lightDao.lambdaUpdate(User.class).typeCheck(false).set(User::getStatus, "0").eq(User::getId, 1)
				.assemble(lightDao::getEntityMeta);
		// Number族之间允许:Long值写Integer字段/Integer查询条件
		assertEquals(1, lightDao.lambdaQuery(User.class).eq(User::getId, 1L).find().size());
	}

	@Test
	@Order(17)
	@DisplayName("likeExact按字面量匹配%")
	public void testLikeExactExecution() {
		lightDao.lambdaUpdate(User.class).set(User::getStaffName, "100%").eq(User::getId, 1).update();
		assertEquals(1L, lightDao.lambdaQuery(User.class).likeExact(User::getStaffName, "100%").count());
		assertEquals(1L, lightDao.lambdaQuery(User.class).likeExact(User::getStaffName, "00%").count());
		assertEquals(0L, lightDao.lambdaQuery(User.class).likeExact(User::getStaffName, "999").count());
		lightDao.lambdaUpdate(User.class).set(User::getStaffName, "a_b").eq(User::getId, 1).update();
		assertEquals(1L, lightDao.lambdaQuery(User.class).likeExact(User::getStaffName, "a_b").count());
		assertEquals(0L, lightDao.lambdaQuery(User.class).likeExact(User::getStaffName, "axb").count());
		lightDao.lambdaUpdate(User.class).set(User::getStaffName, "张三").eq(User::getId, 1).update();
	}

	@Test
	@Order(17)
	@DisplayName("聚合标量max/min/sum")
	public void testAggregateScalars() {
		assertEquals(2, lightDao.lambdaQuery(User.class).max(User::getId).intValue());
		assertEquals(1, lightDao.lambdaQuery(User.class).min(User::getId).intValue());
		assertEquals(0, new BigDecimal("3").compareTo(lightDao.lambdaQuery(User.class).sum(User::getId)));
		assertEquals(0, new BigDecimal("1.5").compareTo(lightDao.lambdaQuery(User.class).avg(User::getId)));
		assertThrows(DataAccessException.class,
				() -> lightDao.lambdaQuery(User.class).groupBy(User::getStatus).max(User::getId));
		assertThrows(DataAccessException.class,
				() -> lightDao.lambdaQuery(User.class).having("count(1) > 1").sum(User::getId));
	}

	@Test
	@Order(17)
	@DisplayName("findColumns/getScalar单列终端")
	public void testSingleColumnTerminals() {
		assertThrows(DataAccessException.class, () -> lightDao.lambdaQuery(User.class)
				.select(User::getStaffName).findColumns(User::getStatus));
		assertThrows(DataAccessException.class, () -> lightDao.lambdaQuery(User.class)
				.select(User::getStaffName).max(User::getId));
		List<Integer> statuses = lightDao.lambdaQuery(User.class).eq(User::getStatus, 1)
				.orderByAsc(User::getId).findColumns(User::getStatus);
		assertEquals(Arrays.asList(1, 1), statuses);
		assertEquals("张三", lightDao.lambdaQuery(User.class).eq(User::getId, 1)
				.getScalar(User::getStaffName));
		assertNull(lightDao.lambdaQuery(User.class).eq(User::getId, 999)
				.getScalar(User::getStaffName));
		assertThrows(DataAccessException.class,
				() -> lightDao.lambdaQuery(User.class).eq(User::getStatus, 1).getScalar(User::getStaffName));
	}

	@Test
	@Order(17)
	@DisplayName("getFirst按orderBy取首条,无匹配返回null")
	public void testGetFirst() {
		User first = lightDao.lambdaQuery(User.class).eq(User::getStatus, 1)
				.orderByAsc(User::getId).getFirst();
		assertEquals(1, first.getId().intValue());
		assertNull(lightDao.lambdaQuery(User.class).eq(User::getId, 999).getFirst());
	}

	@Test
	@Order(17)
	@DisplayName("SqlToyDao与SqlToyLazyDao均提供lambda入口")
	public void testLegacyDaoLambdaApi() {
		DefaultSqlToyDaoImpl sqlToyDao = new DefaultSqlToyDaoImpl();
		sqlToyDao.setSqlToyContext(context);
		sqlToyDao.setDataSource(dataSource);
		SqlToyLazyDao lazyDao = new DefaultLazyDaoImpl(context);
		assertEquals(1, sqlToyDao.lambdaQuery(User.class).eq(User::getId, 1).getFirst().getId().intValue());
		assertEquals(1, lazyDao.lambdaQuery(User.class).eq(User::getId, 1).getFirst().getId().intValue());
		assertEquals(2L, sqlToyDao.lambdaQuery(User.class).eq(User::getStatus, 1).count());
		assertEquals(2L, lazyDao.lambdaQuery(User.class).eq(User::getStatus, 1).count());
	}
}
