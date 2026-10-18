package org.sagacity.sqltoy.lambda;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import org.sagacity.sqltoy.config.model.PageOptimize;
import org.sagacity.sqltoy.dao.LightDao;
import org.sagacity.sqltoy.dao.impl.DefaultLightDaoImpl;
import org.sagacity.sqltoy.exception.DataAccessException;
import org.sagacity.sqltoy.lambda.LambdaFn;
import org.sagacity.sqltoy.lambda.join.ExcludeCols;
import org.sagacity.sqltoy.lambda.OuterRef;
import org.sagacity.sqltoy.lambda.join.JoinCol;
import org.sagacity.sqltoy.model.Page;

/**
 * @description lambda条件体系全功能覆盖测试(H2内存库+DefaultLightDaoImpl非spring场景):
 *              以独立数据集执行验证全部条件族、逻辑组合、动态化语法糖、值过滤、查询终端、
 *              更新/删除及其安全边界、join查询/聚合/更新删除sql与错误语义;
 *              id>=90的记录(status=7)专供变更类用例,读取类用例以lt(id,90)隔离
 * @author zhongxuchen
 */
@DisplayName("lambda全功能覆盖测试(H2)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class LambdaFullCoverageH2Test {

	private static final String URL = "jdbc:h2:mem:lambdafull;DB_CLOSE_DELAY=-1";
	private static SqlToyContext context;
	private static LightDao lightDao;

	@Entity(tableName = "ff_user")
	public static class FfUser implements Serializable {

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

		@Column(name = "LOGIN_COUNT")
		private Integer loginCount;

		/**
		 * 非映射属性(无@Column),用于列名解析守卫测试
		 */
		private String helper;

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

		public Integer getLoginCount() {
			return loginCount;
		}

		public void setLoginCount(Integer loginCount) {
			this.loginCount = loginCount;
		}

		public String getHelper() {
			return helper;
		}

		public void setHelper(String helper) {
			this.helper = helper;
		}
	}

	@Entity(tableName = "ff_dept")
	public static class FfDept implements Serializable {

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

	public static class FfUserVO {

		private String staffName;
		private String deptName;

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

	public static class FfDeptAggVO {

		private Integer deptId;
		private BigDecimal totalSalary;
		private BigDecimal avgSalary;
		private BigDecimal minSalary;
		private BigDecimal maxSalary;
		private Long userCount;

		public Integer getDeptId() {
			return deptId;
		}

		public void setDeptId(Integer deptId) {
			this.deptId = deptId;
		}

		public BigDecimal getTotalSalary() {
			return totalSalary;
		}

		public void setTotalSalary(BigDecimal totalSalary) {
			this.totalSalary = totalSalary;
		}

		public BigDecimal getAvgSalary() {
			return avgSalary;
		}

		public void setAvgSalary(BigDecimal avgSalary) {
			this.avgSalary = avgSalary;
		}

		public BigDecimal getMinSalary() {
			return minSalary;
		}

		public void setMinSalary(BigDecimal minSalary) {
			this.minSalary = minSalary;
		}

		public BigDecimal getMaxSalary() {
			return maxSalary;
		}

		public void setMaxSalary(BigDecimal maxSalary) {
			this.maxSalary = maxSalary;
		}

		public Long getUserCount() {
			return userCount;
		}

		public void setUserCount(Long userCount) {
			this.userCount = userCount;
		}
	}

	public static class FfDeptStatVO {

		private String deptName;
		private Long userCount;

		public String getDeptName() {
			return deptName;
		}

		public void setDeptName(String deptName) {
			this.deptName = deptName;
		}

		public Long getUserCount() {
			return userCount;
		}

		public void setUserCount(Long userCount) {
			this.userCount = userCount;
		}
	}

	@BeforeAll
	public static void init() throws Exception {
		try (Connection conn = DriverManager.getConnection(URL, "sa", ""); Statement st = conn.createStatement()) {
			st.execute("drop table if exists ff_user");
			st.execute("drop table if exists ff_dept");
			st.execute("create table ff_dept (dept_id int primary key, dept_name varchar(50), status int)");
			st.execute(
					"create table ff_user (id int primary key, staff_name varchar(50), dept_id int, status int, salary numeric(10,2), login_count int)");
			st.execute("insert into ff_dept values (10,'研发部',1)");
			st.execute("insert into ff_dept values (20,'市场部',1)");
			st.execute("insert into ff_dept values (30,'人事部',0)");
			st.execute("insert into ff_user values (1,'张三',10,1,1000,0)");
			st.execute("insert into ff_user values (2,'李四',10,1,2000,1)");
			st.execute("insert into ff_user values (3,'王五',20,0,3000,2)");
			st.execute("insert into ff_user values (4,'赵六',null,1,4000,3)");
			st.execute("insert into ff_user values (5,'孙七',30,1,null,4)");
			st.execute("insert into ff_user values (6,'周八',20,1,6000,5)");
			st.execute("insert into ff_user values (91,'变更甲',10,7,100,0)");
			st.execute("insert into ff_user values (92,'变更乙',20,7,200,0)");
		}
		JdbcDataSource dataSource = new JdbcDataSource();
		dataSource.setURL(URL);
		dataSource.setUser("sa");
		context = new SqlToyContext();
		context.setDefaultDataSource(dataSource);
		context.initialize();
		lightDao = new DefaultLightDaoImpl(context);
	}

	private static List<Integer> ids(List<FfUser> users) {
		return users.stream().map(FfUser::getId).sorted().collect(java.util.stream.Collectors.toList());
	}

	@Test
	@Order(1)
	@DisplayName("比较条件族:eq/ne/gt/ge/lt/le")
	public void testCompareOps() {
		assertEquals(Arrays.asList(1, 2, 4, 5, 6), ids(lightDao.lambdaQuery(FfUser.class)
				.eq(FfUser::getStatus, 1).lt(FfUser::getId, 90).find()));
		assertEquals(Arrays.asList(3), ids(lightDao.lambdaQuery(FfUser.class)
				.ne(FfUser::getStatus, 1).lt(FfUser::getId, 90).find()));
		assertEquals(Arrays.asList(3, 4, 6), ids(lightDao.lambdaQuery(FfUser.class)
				.gt(FfUser::getSalary, 2500).lt(FfUser::getId, 90).find()));
		assertEquals(Arrays.asList(3, 4, 6), ids(lightDao.lambdaQuery(FfUser.class)
				.ge(FfUser::getSalary, 3000).lt(FfUser::getId, 90).find()));
		assertEquals(Arrays.asList(1), ids(lightDao.lambdaQuery(FfUser.class)
				.lt(FfUser::getSalary, 2000).lt(FfUser::getId, 90).find()));
		assertEquals(Arrays.asList(1, 2), ids(lightDao.lambdaQuery(FfUser.class)
				.le(FfUser::getSalary, 2000).lt(FfUser::getId, 90).find()));
	}

	@Test
	@Order(2)
	@DisplayName("模糊条件族:like/notLike/likeLeft/likeRight")
	public void testLikeFamily() {
		assertEquals(Arrays.asList(1), ids(lightDao.lambdaQuery(FfUser.class)
				.like(FfUser::getStaffName, "张").lt(FfUser::getId, 90).find()));
		assertEquals(Arrays.asList(1), ids(lightDao.lambdaQuery(FfUser.class)
				.likeLeft(FfUser::getStaffName, "三").lt(FfUser::getId, 90).find()));
		assertEquals(Arrays.asList(2), ids(lightDao.lambdaQuery(FfUser.class)
				.likeRight(FfUser::getStaffName, "李").lt(FfUser::getId, 90).find()));
		assertEquals(Arrays.asList(2, 3, 4, 5, 6), ids(lightDao.lambdaQuery(FfUser.class)
				.notLike(FfUser::getStaffName, "张").lt(FfUser::getId, 90).find()));
		// 方向语义锁定:likeRight=前缀(值%)、likeLeft=后缀(%值),须与包含匹配(%值%)可区分——
		// "三"仅出现在张三尾部、"李"仅出现在李四头部,故前缀查"三"、后缀查"李"均应为空;
		// 若方向退化为包含匹配(如sqlite曾出现的CONCAT→||触发processLike二次补%缺陷)会误命中张三/李四
		assertTrue(ids(lightDao.lambdaQuery(FfUser.class)
				.likeRight(FfUser::getStaffName, "三").lt(FfUser::getId, 90).find()).isEmpty());
		assertTrue(ids(lightDao.lambdaQuery(FfUser.class)
				.likeLeft(FfUser::getStaffName, "李").lt(FfUser::getId, 90).find()).isEmpty());
	}

	@Test
	@Order(3)
	@DisplayName("区间条件族:between/notBetween/betweenIfPresent半开退化")
	public void testBetweenFamily() {
		assertEquals(Arrays.asList(2, 3), ids(lightDao.lambdaQuery(FfUser.class)
				.between(FfUser::getSalary, 1500, 3500).lt(FfUser::getId, 90).find()));
		assertEquals(Arrays.asList(1, 4, 6), ids(lightDao.lambdaQuery(FfUser.class)
				.notBetween(FfUser::getSalary, 1500, 3500).lt(FfUser::getId, 90).find()));
		// 仅下限退化为ge
		assertEquals(Arrays.asList(3, 4, 6), ids(lightDao.lambdaQuery(FfUser.class)
				.betweenIfPresent(FfUser::getSalary, 2500, null).lt(FfUser::getId, 90).find()));
	}

	@Test
	@Order(4)
	@DisplayName("集合条件族:in集合/in变参/notIn")
	public void testInFamily() {
		assertEquals(3, lightDao.lambdaQuery(FfUser.class).in(FfUser::getId, Arrays.asList(1, 2, 3)).find().size());
		assertEquals(2, lightDao.lambdaQuery(FfUser.class).in(FfUser::getId, 4, 5).find().size());
		assertEquals(Arrays.asList(6), ids(lightDao.lambdaQuery(FfUser.class)
				.notIn(FfUser::getId, Arrays.asList(1, 2, 3, 4, 5)).lt(FfUser::getId, 90).find()));
	}

	@Test
	@Order(5)
	@DisplayName("inSql子查询带命名参数")
	public void testInSql() {
		Map<String, Object> subParams = new HashMap<>();
		subParams.put("deptId", 10);
		assertEquals(Arrays.asList(1, 2), ids(lightDao.lambdaQuery(FfUser.class)
				.inSql(FfUser::getId, "select id from ff_user where dept_id = :deptId", subParams)
				.lt(FfUser::getId, 90).find()));
	}

	@Test
	@Order(6)
	@DisplayName("inb多字段元组in执行与元组长度校验")
	public void testInb() {
		assertEquals(Arrays.asList(1, 3), ids(lightDao.lambdaQuery(FfUser.class)
				.inb(FfUser::getId, FfUser::getStaffName,
						Arrays.asList(new Object[] { 1, "张三" }, new Object[] { 3, "王五" }))
				.find()));
		// 元组长度与字段数不一致时装配期快速失败
		LambdaQuery<FfUser> bad = LambdaQuery.create(FfUser.class)
				.inb(FfUser::getId, FfUser::getStaffName, Arrays.<Object[]>asList(new Object[] { 1 }));
		assertThrows(DataAccessException.class, () -> bad.bind(lightDao).find());
	}

	@Test
	@Order(7)
	@DisplayName("判空条件:isNull/isNotNull")
	public void testNullConditions() {
		assertEquals(Arrays.asList(5), ids(lightDao.lambdaQuery(FfUser.class)
				.isNull(FfUser::getSalary).lt(FfUser::getId, 90).find()));
		assertEquals(5, lightDao.lambdaQuery(FfUser.class).isNotNull(FfUser::getSalary)
				.lt(FfUser::getId, 90).find().size());
	}

	@Test
	@Order(8)
	@DisplayName("逻辑组合:or/and嵌套/not嵌套/二层嵌套/空组丢弃")
	public void testLogicCombos() {
		assertEquals(6, lightDao.lambdaQuery(FfUser.class).eq(FfUser::getStatus, 1).or()
				.eq(FfUser::getStatus, 0).lt(FfUser::getId, 90).find().size());
		assertEquals(Arrays.asList(1, 2), ids(lightDao.lambdaQuery(FfUser.class).eq(FfUser::getDeptId, 10)
				.and(n -> n.eq(FfUser::getStatus, 1).or().eq(FfUser::getStatus, 0)).find()));
		// dept为null的行在NOT(dept=10)下因SQL三值逻辑被排除
		assertEquals(Arrays.asList(3, 5, 6), ids(lightDao.lambdaQuery(FfUser.class)
				.not(n -> n.eq(FfUser::getDeptId, 10)).lt(FfUser::getId, 90).find()));
		// 二层嵌套:((dept=10 OR dept=20) AND status=1) AND id<90 → 张三/李四(dept10,status1)、周八(dept20,status1)
		assertEquals(Arrays.asList(1, 2, 6), ids(lightDao.lambdaQuery(FfUser.class)
				.nested(outer -> outer
						.nested(inner -> inner.eq(FfUser::getDeptId, 10).or().eq(FfUser::getDeptId, 20))
						.eq(FfUser::getStatus, 1))
				.lt(FfUser::getId, 90).find()));
		// 动态空组:组内condition=false全被跳过→整组消失(不产 () 也不留悬挂AND),外层条件照常 → status=1 AND id<90
		assertEquals(Arrays.asList(1, 2, 4, 5, 6), ids(lightDao.lambdaQuery(FfUser.class)
				.eq(FfUser::getStatus, 1).and(n -> n.eq(false, FfUser::getDeptId, 10)).lt(FfUser::getId, 90)
				.find()));
	}

	@Test
	@Order(9)
	@DisplayName("accept消费函数;单表last改为响亮失败(见order33)")
	public void testLastAndAccept() {
		assertEquals(Arrays.asList(6), ids(lightDao.lambdaQuery(FfUser.class).eq(FfUser::getStatus, 1)
				.accept(q -> q.eq(FfUser::getDeptId, 20)).find()));
	}

	@Test
	@Order(10)
	@DisplayName("IfPresent语法糖:null/空白/空集合跳过")
	public void testIfPresent() {
		assertEquals(6, lightDao.lambdaQuery(FfUser.class).eqIfPresent(FfUser::getStatus, null)
				.likeIfPresent(FfUser::getStaffName, "").inIfPresent(FfUser::getId, Arrays.asList())
				.lt(FfUser::getId, 90).find().size());
		assertEquals(Arrays.asList(3, 4, 6), ids(lightDao.lambdaQuery(FfUser.class)
				.betweenIfPresent(FfUser::getSalary, new Object[] { 2500 }).lt(FfUser::getId, 90).find()));
	}

	@Test
	@Order(11)
	@DisplayName("实例级值过滤策略")
	public void testValueFilter() {
		// 过滤掉值为1的条件:status=1被跳过,status=0生效
		assertEquals(Arrays.asList(3), ids(lightDao.lambdaQuery(FfUser.class)
				.valueFilter(v -> v != null && !Integer.valueOf(1).equals(v))
				.eq(FfUser::getStatus, 1).eq(FfUser::getStatus, 0).find()));
	}

	@Test
	@Order(12)
	@DisplayName("select字段与orderBy排序执行")
	public void testSelectAndOrderBy() {
		List<FfUser> selected = lightDao.lambdaQuery(FfUser.class)
				.select(FfUser::getId, FfUser::getStaffName).eq(FfUser::getStatus, 0).find();
		assertEquals(1, selected.size());
		assertEquals("王五", selected.get(0).getStaffName());
		assertNull(selected.get(0).getSalary());

		List<FfUser> ordered = lightDao.lambdaQuery(FfUser.class).eq(FfUser::getStatus, 1)
				.lt(FfUser::getId, 90).orderByDesc(FfUser::getSalary).find();
		assertEquals(6, ordered.get(0).getId().intValue());
	}

	@Test
	@Order(13)
	@DisplayName("查询终端:find/getOne严格语义/findPage/findTop/findRandom/count/exists")
	public void testQueryTerminals() {
		assertEquals(5, lightDao.lambdaQuery(FfUser.class).eq(FfUser::getStatus, 1)
				.lt(FfUser::getId, 90).find().size());
		assertNotNull(lightDao.lambdaQuery(FfUser.class).eq(FfUser::getId, 1).getOne());
		assertNull(lightDao.lambdaQuery(FfUser.class).eq(FfUser::getId, 999).getOne());
		assertThrows(DataAccessException.class,
				() -> lightDao.lambdaQuery(FfUser.class).eq(FfUser::getStatus, 1).getOne());

		Page<FfUser> page = lightDao.lambdaQuery(FfUser.class).eq(FfUser::getStatus, 1)
				.lt(FfUser::getId, 90).orderByAsc(FfUser::getId).findPage(new Page(2, 1));
		assertEquals(5L, page.getRecordCount());
		assertEquals(2, page.getRows().size());
		assertEquals(1, ((FfUser) page.getRows().get(0)).getId().intValue());

		assertEquals(2, lightDao.lambdaQuery(FfUser.class).eq(FfUser::getStatus, 1)
				.lt(FfUser::getId, 90).findTop(2).size());
		// 比例:0.5*5=2
		assertEquals(2, lightDao.lambdaQuery(FfUser.class).eq(FfUser::getStatus, 1)
				.lt(FfUser::getId, 90).findTop(0.5).size());
		assertEquals(3, lightDao.lambdaQuery(FfUser.class).eq(FfUser::getStatus, 1)
				.lt(FfUser::getId, 90).findRandom(3).size());
		assertEquals(5L, lightDao.lambdaQuery(FfUser.class).eq(FfUser::getStatus, 1)
				.lt(FfUser::getId, 90).count());
		assertTrue(lightDao.lambdaQuery(FfUser.class).eq(FfUser::getStatus, 0).exists());
		assertTrue(!lightDao.lambdaQuery(FfUser.class).eq(FfUser::getStaffName, "不存在").exists());
	}

	@Test
	@Order(14)
	@DisplayName("更新:set多字段与condition开关")
	public void testUpdateSet() {
		long updated = lightDao.lambdaUpdate(FfUser.class).set(FfUser::getStaffName, "变更甲改")
				.set(false, FfUser::getSalary, new BigDecimal("999")).eq(FfUser::getId, 91).update();
		assertEquals(1L, updated);
		FfUser user = lightDao.lambdaQuery(FfUser.class).eq(FfUser::getId, 91).getOne();
		assertEquals("变更甲改", user.getStaffName());
		assertEquals(0, new BigDecimal("100").compareTo(user.getSalary()));
	}

	@Test
	@Order(15)
	@DisplayName("更新:setExpr字面量/?占位/:name三种形态")
	public void testUpdateSetExpr() {
		lightDao.lambdaUpdate(FfUser.class).setExpr(FfUser::getSalary, "salary + 50")
				.setExpr(FfUser::getLoginCount, "loginCount + 1").eq(FfUser::getId, 91).update();
		FfUser user = lightDao.lambdaQuery(FfUser.class).eq(FfUser::getId, 91).getOne();
		assertEquals(0, new BigDecimal("150").compareTo(user.getSalary()));
		assertEquals(1, user.getLoginCount().intValue());

		lightDao.lambdaUpdate(FfUser.class).setExpr(FfUser::getSalary, "salary + ?", new BigDecimal("50"))
				.eq(FfUser::getId, 91).update();
		user = lightDao.lambdaQuery(FfUser.class).eq(FfUser::getId, 91).getOne();
		assertEquals(0, new BigDecimal("200").compareTo(user.getSalary()));

		lightDao.lambdaUpdate(FfUser.class).setExpr(FfUser::getSalary, "salary * :rate", 2)
				.eq(FfUser::getId, 91).update();
		user = lightDao.lambdaQuery(FfUser.class).eq(FfUser::getId, 91).getOne();
		assertEquals(0, new BigDecimal("400").compareTo(user.getSalary()));
	}

	@Test
	@Order(16)
	@DisplayName("更新安全边界:无条件守卫/无set守卫/matchAll/无命名参数兜底")
	public void testUpdateGuards() {
		assertThrows(DataAccessException.class,
				() -> lightDao.lambdaUpdate(FfUser.class).set(FfUser::getStatus, 1).update());
		// 无set字段:快速失败(守卫在assemble与join/单表分支之前,单表与join两通道均覆盖)
		assertThrows(DataAccessException.class,
				() -> lightDao.lambdaUpdate(FfUser.class).eq(FfUser::getId, 91).update());
		assertThrows(DataAccessException.class, () -> lightDao.lambdaUpdate(FfUser.class)
				.leftJoin(FfDept.class).on(FfUser::getDeptId, FfDept::getDeptId)
				.eq(FfDept::getDeptName, "研发部").update());
		// condition=false的set被跳过,等价于无set,同样快速失败
		assertThrows(DataAccessException.class, () -> lightDao.lambdaUpdate(FfUser.class)
				.set(false, FfUser::getStatus, 1).eq(FfUser::getId, 91).update());
		long updated = lightDao.lambdaUpdate(FfUser.class).set(FfUser::getStatus, 8).matchAll()
				.eq(FfUser::getId, 91).or().eq(FfUser::getId, 92).update();
		assertEquals(2L, updated);
		lightDao.lambdaUpdate(FfUser.class).set(FfUser::getStatus, 7).matchAll()
				.in(FfUser::getId, Arrays.asList(91, 92)).update();
		// where为字面量(无命名参数)触发1=:DEFAULT_VALUE兜底,不报错且不误伤
		long none = lightDao.lambdaUpdate(FfUser.class).set(FfUser::getStaffName, "无人命中")
				.isNull(FfUser::getLoginCount).update();
		assertEquals(0L, none);
	}

	@Test
	@Order(17)
	@DisplayName("删除:条件删除与无条件守卫")
	public void testDelete() {
		assertEquals(1L, lightDao.lambdaDelete(FfUser.class).eq(FfUser::getId, 92).delete());
		assertNull(lightDao.lambdaQuery(FfUser.class).eq(FfUser::getId, 92).getOne());
		assertThrows(DataAccessException.class, () -> lightDao.lambdaDelete(FfUser.class).delete());
	}

	@Test
	@Order(18)
	@DisplayName("未绑定构建器经bind后执行")
	public void testBindExecution() {
		LambdaQuery<FfUser> query = LambdaQuery.create(FfUser.class).eq(FfUser::getStatus, 7);
		assertEquals(1, query.bind(lightDao).find().size());
	}

	@Test
	@Order(19)
	@DisplayName("join缺省select主表全部字段")
	public void testJoinDefaultSelect() {
		List<FfUser> users = lightDao.lambdaQuery(FfUser.class).leftJoin(FfDept.class)
				.on(FfUser::getDeptId, FfDept::getDeptId).eq(FfUser::getStatus, 1)
				.lt(FfUser::getId, 90).orderByAsc(FfUser::getId).find();
		assertEquals(5, users.size());
		assertEquals("张三", users.get(0).getStaffName());
		assertEquals(10, users.get(0).getDeptId().intValue());
	}

	@Test
	@Order(20)
	@DisplayName("join selectAll+ExcludeCols+JoinCol别名映射VO")
	public void testJoinSelectAllAndExclude() {
		List<FfUserVO> rows = lightDao.lambdaQuery(FfUserVO.class)
				.selectAll(ExcludeCols.of(FfUser::getStatus, FfUser::getSalary, FfUser::getLoginCount))
				.from(FfUser.class).leftJoin(FfDept.class).on(FfUser::getDeptId, FfDept::getDeptId)
				.select(JoinCol.of(FfDept::getDeptName).as("deptName"))
				.eq(FfUser::getStatus, 1).lt(FfUser::getId, 90).orderByAsc(FfUser::getId).find();
		assertEquals(5, rows.size());
		assertEquals("张三", rows.get(0).getStaffName());
		assertEquals("研发部", rows.get(0).getDeptName());
		// 赵六(orderByAsc后第3行)无部门,LEFT JOIN下为null
		assertNull(rows.get(2).getDeptName());
	}

	@Test
	@Order(21)
	@DisplayName("join聚合:groupBy+having+count别名映射VO")
	public void testJoinAggregate() {
		List<FfDeptStatVO> stats = lightDao.lambdaQuery(FfDeptStatVO.class)
				.select(JoinCol.of(FfDept::getDeptName).as("deptName"),
						JoinCol.count(FfUser::getId).as("userCount"))
				.from(FfUser.class).leftJoin(FfDept.class).on(FfUser::getDeptId, FfDept::getDeptId)
				.lt(FfUser::getId, 90).groupBy(FfDept::getDeptName).having("count(*) > 1")
				.find();
		assertEquals(2, stats.size());
		Map<String, Long> statMap = new HashMap<>();
		for (FfDeptStatVO stat : stats) {
			statMap.put(stat.getDeptName(), stat.getUserCount());
		}
		assertEquals(2L, statMap.get("研发部").longValue());
		assertEquals(2L, statMap.get("市场部").longValue());
	}

	@Test
	@Order(22)
	@DisplayName("join多on等值关联与嵌套条件")
	public void testJoinMultiOnAndNested() {
		List<FfUser> users = lightDao.lambdaQuery(FfUser.class).leftJoin(FfDept.class)
				.on(FfUser::getDeptId, FfDept::getDeptId, FfUser::getStatus, FfDept::getStatus)
				.and(n -> n.eq(FfDept::getDeptName, "研发部").or().eq(FfDept::getDeptName, "市场部"))
				.lt(FfUser::getId, 90).orderByAsc(FfUser::getId).find();
		assertEquals(Arrays.asList(1, 2, 6), ids(users));
	}

	@Test
	@Order(23)
	@DisplayName("join查询终端:count/exists/findPage/findTop/getOne严格语义")
	public void testJoinTerminals() {
		assertEquals(5L, lightDao.lambdaQuery(FfUser.class).leftJoin(FfDept.class)
				.on(FfUser::getDeptId, FfDept::getDeptId).eq(FfUser::getStatus, 1)
				.lt(FfUser::getId, 90).count());
		assertTrue(lightDao.lambdaQuery(FfUser.class).leftJoin(FfDept.class)
				.on(FfUser::getDeptId, FfDept::getDeptId).eq(FfDept::getDeptName, "研发部").exists());
		Page<FfUser> page = lightDao.lambdaQuery(FfUser.class).leftJoin(FfDept.class)
				.on(FfUser::getDeptId, FfDept::getDeptId).eq(FfUser::getStatus, 1)
				.lt(FfUser::getId, 90).orderByAsc(FfUser::getId).findPage(new Page(2, 2));
		assertEquals(5L, page.getRecordCount());
		assertEquals(2, page.getRows().size());
		assertEquals(2, lightDao.lambdaQuery(FfUser.class).leftJoin(FfDept.class)
				.on(FfUser::getDeptId, FfDept::getDeptId).eq(FfUser::getStatus, 1)
				.lt(FfUser::getId, 90).findTop(2).size());
		assertThrows(DataAccessException.class, () -> lightDao.lambdaQuery(FfUser.class)
				.leftJoin(FfDept.class).on(FfUser::getDeptId, FfDept::getDeptId)
				.eq(FfUser::getStatus, 1).getOne());
	}

	@Test
	@Order(24)
	@DisplayName("join更新/删除sql文本(MySQL方言)与无条件守卫")
	public void testJoinUpdateDeleteSqlText() {
		LambdaUpdate<FfUser> update = lightDao.lambdaUpdate(FfUser.class).leftJoin(FfDept.class)
				.on(FfUser::getDeptId, FfDept::getDeptId).set(FfUser::getStatus, 5)
				.eq(FfDept::getDeptName, "研发部");
		update.assemble(context::getEntityMeta);
		assertEquals("UPDATE ff_user SET STATUS = :p1 WHERE EXISTS (SELECT 1 FROM (SELECT f1.ID AS LPK1 "
				+ "FROM ff_user f1 LEFT JOIN ff_dept f2 ON f1.DEPT_ID = f2.DEPT_ID WHERE f2.DEPT_NAME = :p2) sqd "
				+ "WHERE sqd.LPK1 = ff_user.ID)", normalized(update.getSql()));
		assertThrows(DataAccessException.class, () -> lightDao.lambdaUpdate(FfUser.class)
				.leftJoin(FfDept.class).on(FfUser::getDeptId, FfDept::getDeptId)
				.set(FfUser::getStatus, 5).update());

		LambdaDelete<FfUser> delete = lightDao.lambdaDelete(FfUser.class).leftJoin(FfDept.class)
				.on(FfUser::getDeptId, FfDept::getDeptId).deleteTables(FfUser.class)
				.eq(FfDept::getDeptName, "市场部");
		delete.assemble(context::getEntityMeta);
		assertEquals("DELETE FROM ff_user WHERE EXISTS (SELECT 1 FROM (SELECT f1.ID AS LPK1 "
				+ "FROM ff_user f1 LEFT JOIN ff_dept f2 ON f1.DEPT_ID = f2.DEPT_ID WHERE f2.DEPT_NAME = :p1) sqd "
				+ "WHERE sqd.LPK1 = ff_user.ID)", normalized(delete.getSql()));
		assertThrows(DataAccessException.class, () -> lightDao.lambdaDelete(FfUser.class)
				.leftJoin(FfDept.class).on(FfUser::getDeptId, FfDept::getDeptId).delete());
	}

	@Test
	@Order(25)
	@DisplayName("错误语义:未参与join实体/缺on/setExpr含=/ExcludeCols实体错配/selectAll全排除")
	public void testErrorSemantics() {
		assertThrows(DataAccessException.class, () -> lightDao.lambdaQuery(FfUser.class)
				.from(FfUser.class).eq(FfDept::getDeptName, "研发部").find());
		LambdaQuery<FfUser> noOn = lightDao.lambdaQuery(FfUser.class);
		noOn.leftJoin(FfDept.class);
		assertThrows(DataAccessException.class, () -> noOn.find());
		assertThrows(DataAccessException.class,
				() -> lightDao.lambdaUpdate(FfUser.class).setExpr(FfUser::getStatus, "status = 1"));
		assertThrows(DataAccessException.class, () -> lightDao.lambdaQuery(FfUserVO.class)
				.selectAll(ExcludeCols.of(FfDept::getDeptName)).from(FfUser.class)
				.leftJoin(FfDept.class).on(FfUser::getDeptId, FfDept::getDeptId).find());
		assertThrows(DataAccessException.class, () -> lightDao.lambdaQuery(FfUser.class)
				.selectAll(ExcludeCols.of(FfUser::getId, FfUser::getStaffName, FfUser::getDeptId,
						FfUser::getStatus, FfUser::getSalary, FfUser::getLoginCount))
				.leftJoin(FfDept.class).on(FfUser::getDeptId, FfDept::getDeptId).find());
	}

	@Test
	@Order(26)
	@DisplayName("分组聚合:sum/avg/min/max/count与having(单表聚合走SQL通道)")
	public void testGroupAggregateFunctions() {
		List<FfDeptAggVO> aggs = lightDao.lambdaQuery(FfDeptAggVO.class)
				.select(JoinCol.of(FfUser::getDeptId).as("deptId"),
						JoinCol.sum(FfUser::getSalary).as("totalSalary"),
						JoinCol.avg(FfUser::getSalary).as("avgSalary"),
						JoinCol.min(FfUser::getSalary).as("minSalary"),
						JoinCol.max(FfUser::getSalary).as("maxSalary"),
						JoinCol.count(FfUser::getId).as("userCount"))
				.from(FfUser.class).lt(FfUser::getId, 90)
				.groupBy(FfUser::getDeptId).having("sum(SALARY) > 2500")
				.find();
		// dept10=3000、dept20=9000、dept为null的组=4000通过having;dept30(sum为null)被排除
		assertEquals(3, aggs.size());
		Map<Integer, FfDeptAggVO> aggMap = new HashMap<>();
		for (FfDeptAggVO agg : aggs) {
			aggMap.put(agg.getDeptId(), agg);
		}
		FfDeptAggVO dept10 = aggMap.get(10);
		assertEquals(0, new BigDecimal("3000").compareTo(dept10.getTotalSalary()));
		assertEquals(0, new BigDecimal("1500").compareTo(dept10.getAvgSalary()));
		assertEquals(0, new BigDecimal("1000").compareTo(dept10.getMinSalary()));
		assertEquals(0, new BigDecimal("2000").compareTo(dept10.getMaxSalary()));
		assertEquals(2L, dept10.getUserCount().longValue());
		FfDeptAggVO dept20 = aggMap.get(20);
		assertEquals(0, new BigDecimal("9000").compareTo(dept20.getTotalSalary()));
		assertEquals(0, new BigDecimal("4500").compareTo(dept20.getAvgSalary()));
		assertEquals(2L, dept20.getUserCount().longValue());
		// 分组键为null的组同样参与聚合
		FfDeptAggVO noDept = aggMap.get(null);
		assertNotNull(noDept);
		assertEquals(0, new BigDecimal("4000").compareTo(noDept.getTotalSalary()));
	}

	@Test
	@Order(27)
	@DisplayName("distinct去重:单表通道与join通道")
	public void testDistinct() {
		// 单表通道:EntityQuery.distinct()
		List<FfUser> statuses = lightDao.lambdaQuery(FfUser.class).distinct()
				.select(FfUser::getStatus).lt(FfUser::getId, 90).find();
		assertEquals(2, statuses.size());

		// join通道:SELECT DISTINCT
		List<FfUserVO> deptNames = lightDao.lambdaQuery(FfUserVO.class).distinct()
				.select(JoinCol.of(FfDept::getDeptName).as("deptName"))
				.from(FfUser.class).leftJoin(FfDept.class).on(FfUser::getDeptId, FfDept::getDeptId)
				.lt(FfUser::getId, 90).find();
		// 研发/市场/人事/null四个去重值
		assertEquals(4, deptNames.size());
	}

	@Test
	@Order(28)
	@DisplayName("setNull显式置null(绕过默认值过滤)")
	public void testSetNull() {
		lightDao.lambdaUpdate(FfUser.class).setNull(FfUser::getSalary).eq(FfUser::getId, 91).update();
		FfUser user = lightDao.lambdaQuery(FfUser.class).eq(FfUser::getId, 91).getOne();
		assertNull(user.getSalary());
		// condition开关
		lightDao.lambdaUpdate(FfUser.class).setNull(false, FfUser::getLoginCount)
				.set(FfUser::getSalary, new BigDecimal("400")).eq(FfUser::getId, 91).update();
		user = lightDao.lambdaQuery(FfUser.class).eq(FfUser::getId, 91).getOne();
		assertEquals(0, new BigDecimal("400").compareTo(user.getSalary()));
		assertNotNull(user.getLoginCount());
	}

	@Test
	@Order(29)
	@DisplayName("orderByAlias按聚合别名排序")
	public void testOrderByAlias() {
		List<FfDeptAggVO> aggs = lightDao.lambdaQuery(FfDeptAggVO.class)
				.select(JoinCol.of(FfUser::getDeptId).as("deptId"),
						JoinCol.count(FfUser::getId).as("userCount"),
						JoinCol.sum(FfUser::getSalary).as("totalSalary"))
				.from(FfUser.class).lt(FfUser::getId, 90)
				.groupBy(FfUser::getDeptId).orderByDesc("userCount").find();
		// 人数:dept10=2、dept20=2、dept30=1、null组=1,降序首行必为2
		assertEquals(4, aggs.size());
		assertEquals(2L, aggs.get(0).getUserCount().longValue());
		assertEquals(1L, aggs.get(3).getUserCount().longValue());
		// 单表通道无列别名,装配期快速失败
		assertThrows(DataAccessException.class, () -> lightDao.lambdaQuery(FfUser.class)
				.orderByAsc("userCount").find());
		// 别名非法标识符快速失败
		assertThrows(DataAccessException.class, () -> lightDao.lambdaQuery(FfDeptAggVO.class)
				.from(FfUser.class).orderByAsc("userCount; drop table ff_user").find());
	}

	@Test
	@Order(30)
	@DisplayName("join通道setNull的sql文本与参数")
	public void testJoinSetNullSqlText() {
		LambdaUpdate<FfUser> update = lightDao.lambdaUpdate(FfUser.class).leftJoin(FfDept.class)
				.on(FfUser::getDeptId, FfDept::getDeptId).setNull(FfUser::getSalary)
				.eq(FfDept::getDeptName, "研发部");
		update.assemble(context::getEntityMeta);
		assertEquals("UPDATE ff_user SET SALARY = :p1 WHERE EXISTS (SELECT 1 FROM (SELECT f1.ID AS LPK1 "
				+ "FROM ff_user f1 LEFT JOIN ff_dept f2 ON f1.DEPT_ID = f2.DEPT_ID "
				+ "WHERE f2.DEPT_NAME = :p2) sqd WHERE sqd.LPK1 = ff_user.ID)", normalized(update.getSql()));
		assertNull(update.getParams().get("p1"));
	}

	@Test
	@Order(31)
	@DisplayName("防卫:join自关联/重复select别名/外部参数名碰撞/VO结果误用属性select")
	public void testDefensiveGuards() {
		// G1 自关联:同一实体注册两次快速失败
		assertThrows(DataAccessException.class, () -> lightDao.lambdaQuery(FfUser.class)
				.from(FfUser.class).leftJoin(FfUser.class));
		// G2 重复别名:selectAll dump的status与JoinCol.as("status")重名,装配期快速失败
		assertThrows(DataAccessException.class, () -> lightDao.lambdaQuery(FfUserVO.class)
				.selectAll().from(FfUser.class).leftJoin(FfDept.class)
				.on(FfUser::getDeptId, FfDept::getDeptId)
				.select(JoinCol.of(FfDept::getStatus).as("status")).find());
		// G3 外部参数名占用内部p数字命名空间:having与inSql调用时快速失败
		Map<String, Object> badParams = new HashMap<>();
		badParams.put("p1", 2);
		assertThrows(DataAccessException.class, () -> lightDao.lambdaQuery(FfUser.class)
				.having("count(*) > :p1", badParams));
		assertThrows(DataAccessException.class, () -> lightDao.lambdaQuery(FfUser.class)
				.inSql(FfUser::getId, "select id from ff_user where status = :p1", badParams));
		// 合法外部参数名不受影响:minCount=2即count(*)>2,仅研发部(3人,含变更行)满足,同时验证参数真实绑定
		Map<String, Object> goodParams = new HashMap<>();
		goodParams.put("minCount", 2);
		assertEquals(1, lightDao.lambdaQuery(FfDeptStatVO.class)
				.select(JoinCol.of(FfDept::getDeptName).as("deptName"),
						JoinCol.count(FfUser::getId).as("userCount"))
				.from(FfUser.class).leftJoin(FfDept.class).on(FfUser::getDeptId, FfDept::getDeptId)
				.groupBy(FfDept::getDeptName).having("count(*) > :minCount", goodParams).find().size());
		assertEquals("研发部", lightDao.lambdaQuery(FfDeptStatVO.class)
				.select(JoinCol.of(FfDept::getDeptName).as("deptName"),
						JoinCol.count(FfUser::getId).as("userCount"))
				.from(FfUser.class).leftJoin(FfDept.class).on(FfUser::getDeptId, FfDept::getDeptId)
				.groupBy(FfDept::getDeptName).having("count(*) > :minCount", goodParams).find().get(0)
				.getDeptName());
		// G8 结果类型为VO(未参与join)时误用属性形式select(VO自身属性)快速失败
		assertThrows(DataAccessException.class, () -> lightDao.lambdaQuery(FfUserVO.class)
				.select(FfUserVO::getStaffName).from(FfUser.class)
				.leftJoin(FfDept.class).on(FfUser::getDeptId, FfDept::getDeptId).find());
	}

	@Test
	@Order(32)
	@DisplayName("组合场景:setNull不受valueFilter影响/distinct+selectAll/单列元组in/多表删除目标")
	public void testComboScenarios() {
		// 自定义valueFilter放行null也不影响setNull语义,setNull独立于过滤策略
		lightDao.lambdaUpdate(FfUser.class).valueFilter(v -> true)
				.set(FfUser::getSalary, null).eq(FfUser::getId, 91L).update();
		assertNull(lightDao.lambdaQuery(FfUser.class).eq(FfUser::getId, 91L).getOne().getSalary());
		lightDao.lambdaUpdate(FfUser.class).setNull(FfUser::getSalary).eq(FfUser::getId, 91L).update();
		assertNull(lightDao.lambdaQuery(FfUser.class).eq(FfUser::getId, 91L).getOne().getSalary());

		// distinct对select列去重(不带selectAll,否则主键列使去重无效)
		assertEquals(2, lightDao.lambdaQuery(FfUserVO.class).distinct()
				.select(JoinCol.of(FfDept::getDeptName).as("deptName"))
				.from(FfUser.class).leftJoin(FfDept.class).on(FfUser::getDeptId, FfDept::getDeptId)
				.in(FfUser::getId, Arrays.asList(1L, 2L, 3L, 6L)).find().size());

		// 单列元组in
		assertEquals(2, lightDao.lambdaQuery(FfUser.class)
				.inb(Arrays.<LambdaFn<FfUser, ?>>asList(FfUser::getId),
						Arrays.asList(new Object[] { 1L }, new Object[] { 2L }))
				.find().size());

		// 多删除目标表sql文本
		LambdaDelete<FfUser> multiDelete = lightDao.lambdaDelete(FfUser.class)
				.leftJoin(FfDept.class).on(FfUser::getDeptId, FfDept::getDeptId)
				.deleteTables(FfUser.class, FfDept.class).eq(FfUser::getId, 1L);
		multiDelete.assemble(context::getEntityMeta);
		assertTrue(normalized(multiDelete.getSql()).startsWith("DELETE FROM ff_user WHERE EXISTS"));
	}

	@Test
	@Order(33)
	@DisplayName("last():单表通道响亮失败,join通道真实执行")
	public void testLastChannelBehavior() {
		assertThrows(DataAccessException.class, () -> lightDao.lambdaQuery(FfUser.class)
				.eq(FfUser::getStatus, 1).last("limit 1").find());
		assertEquals(1, lightDao.lambdaQuery(FfUser.class).leftJoin(FfDept.class)
				.on(FfUser::getDeptId, FfDept::getDeptId).eq(FfUser::getStatus, 1)
				.last("limit 1").find().size());
	}

	@Test
	@Order(34)
	@DisplayName("代理lambda(动态代理)解析优雅失败而非类初始化错误")
	public void testProxyLambdaGracefulFailure() {
		LambdaFn<FfUser, ?> proxied = (LambdaFn<FfUser, ?>) java.lang.reflect.Proxy.newProxyInstance(
				LambdaFn.class.getClassLoader(), new Class<?>[] { LambdaFn.class },
				(proxy, method, args) -> {
					switch (method.getName()) {
					case "hashCode":
						return System.identityHashCode(proxy);
					case "equals":
						return proxy == args[0];
					case "toString":
						return "proxy-lambda";
					default:
						return null;
					}
				});
		assertThrows(DataAccessException.class, () -> LambdaUtils.extractProperty(proxied));
	}

	@Test
	@Order(35)
	@DisplayName("非映射属性(无@Column)条件快速失败")
	public void testUnmappedPropertyGuard() {
		assertThrows(DataAccessException.class, () -> lightDao.lambdaQuery(FfUser.class)
				.eq(FfUser::getHelper, "x").find());
	}

	@Test
	@Order(36)
	@DisplayName("单表通道selectAll(ExcludeCols)转unselect生效")
	public void testSingleTableExclude() {
		List<FfUser> users = lightDao.lambdaQuery(FfUser.class)
				.selectAll(ExcludeCols.of(FfUser::getSalary)).eq(FfUser::getId, 1L).find();
		assertEquals(1, users.size());
		assertNotNull(users.get(0).getStaffName());
		assertNull(users.get(0).getSalary());
	}

	@Test
	@Order(37)
	@DisplayName("join查询重复终端调用不触发别名重复")
	public void testRepeatTerminalIdempotent() {
		LambdaQuery<FfUser> query = lightDao.lambdaQuery(FfUser.class)
				.select(FfUser::getStaffName)
				.leftJoin(FfDept.class).on(FfUser::getDeptId, FfDept::getDeptId)
				.eq(FfUser::getStatus, 1).lt(FfUser::getId, 90);
		assertEquals(5, query.find().size());
		assertEquals(5L, query.count());
	}

	@Test
	@Order(38)
	@DisplayName("更新删除构建器拒绝分组排序/装配后变更join/空入参守卫")
	public void testBuilderGuards() {
		assertThrows(DataAccessException.class, () -> lightDao.lambdaDelete(FfUser.class)
				.eq(FfUser::getStatus, 1).groupBy(FfUser::getDeptId).delete());
		LambdaQuery<FfUser> assembled = lightDao.lambdaQuery(FfUser.class).eq(FfUser::getStatus, 1);
		assembled.find();
		assertThrows(DataAccessException.class, () -> assembled.leftJoin(FfDept.class));
		assertThrows(DataAccessException.class, () -> lightDao.lambdaQuery(null));
		assertThrows(DataAccessException.class,
				() -> lightDao.lambdaQuery(FfUser.class).select((LambdaFn<FfUser, ?>) null));
	}

	@Test
	@Order(39)
	@DisplayName("exists/notExists关联子查询单表通道执行与sql文本")
	public void testExistsSingleTable() {
		LambdaQuery<FfUser> query = lightDao.lambdaQuery(FfUser.class)
				.exists(FfDept.class, sub -> sub.eq(FfDept::getStatus, 1)
						.eq(FfDept::getDeptId, OuterRef.of(FfUser::getDeptId)))
				.lt(FfUser::getId, 90);
		query.assemble(context::getEntityMeta);
		String sql = normalized(query.getSql());
		assertTrue(sql.contains("EXISTS (SELECT 1 FROM ff_dept sq"));
		assertTrue(sql.contains(".DEPT_ID = ff_user.DEPT_ID"));
		assertEquals(Arrays.asList(1, 2, 3, 6), ids(query.find()));

		assertEquals(Arrays.asList(4, 5), ids(lightDao.lambdaQuery(FfUser.class)
				.notExists(FfDept.class, sub -> sub.eq(FfDept::getStatus, 1)
						.eq(FfDept::getDeptId, OuterRef.of(FfUser::getDeptId)))
				.lt(FfUser::getId, 90).find()));

		// exists终端top-1语义
		assertTrue(lightDao.lambdaQuery(FfUser.class).eq(FfUser::getStatus, 1).exists());
		assertTrue(!lightDao.lambdaQuery(FfUser.class).eq(FfUser::getId, 999L).exists());
	}

	@Test
	@Order(40)
	@DisplayName("join通道关联子查询与OuterRef误用守卫")
	public void testExistsJoinAndGuards() {
		List<FfUserVO> rows = lightDao.lambdaQuery(FfUserVO.class).selectAll()
				.select(JoinCol.of(FfDept::getDeptName).as("deptName"))
				.from(FfUser.class).leftJoin(FfDept.class).on(FfUser::getDeptId, FfDept::getDeptId)
				.exists(FfDept.class, sub -> sub.eq(FfDept::getStatus, 1)
						.eq(FfDept::getDeptId, OuterRef.of(FfUser::getDeptId)))
				.lt(FfUser::getId, 90).orderByAsc(FfUser::getId).find();
		assertEquals(4, rows.size());
		assertEquals("研发部", rows.get(0).getDeptName());

		// OuterRef用在子查询之外快速失败
		assertThrows(DataAccessException.class, () -> lightDao.lambdaQuery(FfUser.class)
				.eq(FfUser::getStatus, OuterRef.of(FfUser::getId)).find());
		// between不接受OuterRef
		assertThrows(DataAccessException.class, () -> lightDao.lambdaQuery(FfUser.class)
				.between(FfUser::getSalary, 1, OuterRef.of(FfUser::getSalary)));
		// 子查询内引用非子查询实体快速失败
		assertThrows(DataAccessException.class, () -> lightDao.lambdaQuery(FfUser.class)
				.exists(FfDept.class, sub -> sub.eq(FfUser::getId, 1L)).find());
	}

	@Test
	@Order(41)
	@DisplayName("join更新/删除可移植改写真实执行(含LEFT JOIN语义)")
	public void testJoinUpdateDeleteExecution() {
		long updated = lightDao.lambdaUpdate(FfUser.class).leftJoin(FfDept.class)
				.on(FfUser::getDeptId, FfDept::getDeptId).set(FfUser::getStatus, 4)
				.eq(FfDept::getDeptName, "研发部").lt(FfUser::getId, 90).update();
		assertEquals(2L, updated);
		assertEquals(2L, lightDao.lambdaQuery(FfUser.class).eq(FfUser::getStatus, 4).count());
		// 还原
		lightDao.lambdaUpdate(FfUser.class).leftJoin(FfDept.class)
				.on(FfUser::getDeptId, FfDept::getDeptId).set(FfUser::getStatus, 1)
				.eq(FfDept::getDeptName, "研发部").lt(FfUser::getId, 90).update();
		// join删除:市场部两行
		long deleted = lightDao.lambdaDelete(FfUser.class).leftJoin(FfDept.class)
				.on(FfUser::getDeptId, FfDept::getDeptId).eq(FfDept::getDeptName, "市场部")
				.lt(FfUser::getId, 90).delete();
		assertEquals(2L, deleted);
		assertTrue(!lightDao.lambdaQuery(FfUser.class).in(FfUser::getId, Arrays.asList(3L, 6L)).exists());
	}

	@Test
	@Order(42)
	@DisplayName("删除通道无命名参数where兜底执行")
	public void testDeleteParamlessWhere() {
		long nullSalaryCount = lightDao.lambdaQuery(FfUser.class).isNull(FfUser::getSalary).count();
		assertTrue(nullSalaryCount >= 1);
		long deleted = lightDao.lambdaDelete(FfUser.class).isNull(FfUser::getSalary).delete();
		assertEquals(nullSalaryCount, deleted);
		assertNull(lightDao.lambdaQuery(FfUser.class).eq(FfUser::getId, 91L).getOne());
	}

	@Test
	@Order(43)
	@DisplayName("matchAll删除清空表(最后执行)")
	public void testMatchAllDelete() {
		long before = lightDao.lambdaQuery(FfUser.class).count();
		long deleted = lightDao.lambdaDelete(FfUser.class).matchAll().delete();
		assertEquals(before, deleted);
		assertEquals(0L, lightDao.lambdaQuery(FfUser.class).count());
	}

	@Test
	@Order(44)
	@DisplayName("update/delete的WHERE方向like(rlike前缀/llike后缀)执行级验证")
	public void testLikeInUpdateDeleteWhere() throws Exception {
		// 专用探针行:模式甲(前缀含"模式")、甲模式(后缀含"模式")、甲模式乙(中间含"模式")——三者都"包含"模式,
		// 但前缀匹配仅命中模式甲、后缀匹配仅命中甲模式,可区分方向like与包含like;order43已清空表,此处独立插入零干扰。
		// 验证like修复(纯LIKE :param+按方向补值,等价原生rlike/llike)在update/delete的WHERE中与query同样正确
		// (LambdaUpdate/LambdaDelete继承AbstractWhere同一份like代码,其WHERE同样流经中央解析链processLike)
		try (Connection conn = DriverManager.getConnection(URL, "sa", ""); Statement st = conn.createStatement()) {
			st.execute("insert into ff_user values (95,'模式甲',10,1,100,0)");
			st.execute("insert into ff_user values (96,'甲模式',10,1,200,0)");
			st.execute("insert into ff_user values (97,'甲模式乙',10,1,300,0)");
		}
		// 基准:包含匹配三条全中
		assertEquals(Arrays.asList(95, 96, 97), ids(lightDao.lambdaQuery(FfUser.class)
				.like(FfUser::getStaffName, "模式").ge(FfUser::getId, 95).find()));
		// UPDATE WHERE likeRight(前缀"模式%"):仅模式甲(95)命中;若退化为包含匹配会误伤96/97
		long updated = lightDao.lambdaUpdate(FfUser.class).set(FfUser::getStatus, 99)
				.likeRight(FfUser::getStaffName, "模式").ge(FfUser::getId, 95).update();
		assertEquals(1L, updated);
		assertEquals(99, lightDao.lambdaQuery(FfUser.class).eq(FfUser::getId, 95).getOne().getStatus().intValue());
		assertEquals(1, lightDao.lambdaQuery(FfUser.class).eq(FfUser::getId, 96).getOne().getStatus().intValue());
		assertEquals(1, lightDao.lambdaQuery(FfUser.class).eq(FfUser::getId, 97).getOne().getStatus().intValue());
		// DELETE WHERE likeLeft(后缀"%模式"):仅甲模式(96)命中;若退化为包含匹配会误删95/97
		long deleted = lightDao.lambdaDelete(FfUser.class).likeLeft(FfUser::getStaffName, "模式")
				.ge(FfUser::getId, 95).delete();
		assertEquals(1L, deleted);
		assertNull(lightDao.lambdaQuery(FfUser.class).eq(FfUser::getId, 96).getOne());
		assertNotNull(lightDao.lambdaQuery(FfUser.class).eq(FfUser::getId, 95).getOne());
		assertNotNull(lightDao.lambdaQuery(FfUser.class).eq(FfUser::getId, 97).getOne());
		// 清理探针行
		lightDao.lambdaDelete(FfUser.class).ge(FfUser::getId, 95).delete();
	}

	@Test
	@Order(45)
	@DisplayName("分页优化pageOptimize:单表与join的findPage(parallel+keep-alive)执行级")
	public void testPageOptimize() throws Exception {
		// 自包含数据(order43已清空ff_user);dept_id=10,join用leftJoin不过滤dept→行数恒等于ff_user数
		try (Connection conn = DriverManager.getConnection(URL, "sa", ""); Statement st = conn.createStatement()) {
			for (int i = 1; i <= 5; i++) {
				st.execute("insert into ff_user values (" + i + ",'po" + i + "',10,1,100,0)");
			}
		}
		PageOptimize po = new PageOptimize().parallel(true).aliveSeconds(60);
		// 单表分页带优化:5条,pageSize=2 → recordCount=5、首页2条、共3页
		Page<FfUser> single = lightDao.lambdaQuery(FfUser.class).orderByAsc(FfUser::getId).pageOptimize(po)
				.findPage(new Page(2, 1));
		assertEquals(5L, single.getRecordCount());
		assertEquals(2, single.getRows().size());
		assertEquals(3L, single.getTotalPage());
		// join分页带优化:走findPageByQuery(QueryExecutor.pageOptimize),leftJoin不过滤→仍5条
		Page<FfUser> join = lightDao.lambdaQuery(FfUser.class).leftJoin(FfDept.class)
				.on(FfUser::getDeptId, FfDept::getDeptId).orderByAsc(FfUser::getId).pageOptimize(po)
				.findPage(new Page(2, 1));
		assertEquals(5L, join.getRecordCount());
		assertEquals(2, join.getRows().size());
		// 清理
		lightDao.lambdaDelete(FfUser.class).matchAll().delete();
	}

	private static String normalized(String sql) {
		return sql == null ? "" : sql.replaceAll("\\s+", " ").trim();
	}
}
