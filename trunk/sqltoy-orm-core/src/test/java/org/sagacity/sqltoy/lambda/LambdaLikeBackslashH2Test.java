package org.sagacity.sqltoy.lambda;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.Serializable;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.annotation.Column;
import org.sagacity.sqltoy.config.annotation.Entity;
import org.sagacity.sqltoy.config.annotation.Id;
import org.sagacity.sqltoy.dao.LightDao;
import org.sagacity.sqltoy.dao.impl.DefaultLightDaoImpl;

/**
 * lambda like族尾部反斜杠回归(独立H2库):wrapLike/exactLike须先转义反斜杠再补%,
 * 否则值尾部\把补的%转义为字面量,前缀/包含匹配退化为字面量匹配
 * (与like过滤器"先转义值最后补%"同序,中央解析链processLike按奇偶识别通配符)
 *
 * @author zhongxuchen
 * @version v1.0,Date:2026-11-19
 */
@DisplayName("lambda like族反斜杠转义回归(H2)")
public class LambdaLikeBackslashH2Test {

	private static final String URL = "jdbc:h2:mem:lambdaLikeBackslash;DB_CLOSE_DELAY=-1";
	private static LightDao lightDao;

	@Entity(tableName = "lb_user")
	public static class LbUser implements Serializable {

		private static final long serialVersionUID = 1L;

		@Id
		@Column(name = "ID")
		private Integer id;

		@Column(name = "STAFF_NAME")
		private String staffName;

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
	}

	@BeforeAll
	public static void init() throws Exception {
		try (Connection conn = DriverManager.getConnection(URL, "sa", ""); Statement st = conn.createStatement()) {
			st.execute("drop table if exists lb_user");
			st.execute("create table lb_user (id int primary key, staff_name varchar(100))");
			st.execute("insert into lb_user values (1,'C:\\Users')");
			st.execute("insert into lb_user values (2,'C:\\Windows')");
			st.execute("insert into lb_user values (3,'C:\\Users\\_x')");
			st.execute("insert into lb_user values (4,'D:\\Users')");
			st.execute("insert into lb_user values (5,'end:C:\\')");
			st.execute("insert into lb_user values (6,'50%off')");
			st.execute("insert into lb_user values (7,'50Xoff')");
			st.execute("insert into lb_user values (8,'a_b')");
			st.execute("insert into lb_user values (9,'axb')");
			st.execute("insert into lb_user values (10,'a\\%b')");
		}
		JdbcDataSource dataSource = new JdbcDataSource();
		dataSource.setURL(URL);
		dataSource.setUser("sa");
		SqlToyContext context = new SqlToyContext();
		context.setDefaultDataSource(dataSource);
		context.initialize();
		lightDao = new DefaultLightDaoImpl(context);
	}

	private static List<Integer> ids(List<LbUser> users) {
		return users.stream().map(LbUser::getId).sorted().collect(java.util.stream.Collectors.toList());
	}

	@Test
	@DisplayName("likeRight尾部反斜杠:前缀匹配不降级为字面量")
	public void likeRightTrailingBackslash() {
		assertEquals(List.of(1, 2, 3), ids(lightDao.lambdaQuery(LbUser.class)
				.likeRight(LbUser::getStaffName, "C:\\").find()));
	}

	@Test
	@DisplayName("like尾部反斜杠:包含匹配不降级为字面量")
	public void likeTrailingBackslash() {
		assertEquals(List.of(1, 2, 3, 5), ids(lightDao.lambdaQuery(LbUser.class)
				.like(LbUser::getStaffName, "C:\\").find()));
	}

	@Test
	@DisplayName("likeLeft尾部反斜杠:后缀匹配")
	public void likeLeftTrailingBackslash() {
		assertEquals(List.of(5), ids(lightDao.lambdaQuery(LbUser.class)
				.likeLeft(LbUser::getStaffName, "C:\\").find()));
	}

	@Test
	@DisplayName("notLike尾部反斜杠:包含取反")
	public void notLikeTrailingBackslash() {
		assertEquals(List.of(4, 6, 7, 8, 9, 10), ids(lightDao.lambdaQuery(LbUser.class)
				.notLike(LbUser::getStaffName, "C:\\").find()));
	}

	@Test
	@DisplayName("likeRight下划线字面量:由中央解析链转义")
	public void likeRightUnderscore() {
		assertEquals(List.of(8), ids(lightDao.lambdaQuery(LbUser.class)
				.likeRight(LbUser::getStaffName, "a_b").find()));
	}

	@Test
	@DisplayName("like值自带%:调用方通配符契约不变")
	public void likeCallerWildcard() {
		assertEquals(List.of(6, 7), ids(lightDao.lambdaQuery(LbUser.class)
				.like(LbUser::getStaffName, "50%").find()));
	}

	@Test
	@DisplayName("likeExact百分号字面量")
	public void likeExactPercent() {
		assertEquals(List.of(6), ids(lightDao.lambdaQuery(LbUser.class)
				.likeExact(LbUser::getStaffName, "50%").find()));
	}

	@Test
	@DisplayName("likeExactRight尾部反斜杠:前缀字面量")
	public void likeExactRightTrailingBackslash() {
		assertEquals(List.of(1, 2, 3), ids(lightDao.lambdaQuery(LbUser.class)
				.likeExactRight(LbUser::getStaffName, "C:\\").find()));
	}

	@Test
	@DisplayName("likeExactLeft尾部反斜杠:后缀字面量")
	public void likeExactLeftTrailingBackslash() {
		assertEquals(List.of(5), ids(lightDao.lambdaQuery(LbUser.class)
				.likeExactLeft(LbUser::getStaffName, "C:\\").find()));
	}

	@Test
	@DisplayName("likeExact反斜杠+百分号:均为字面量")
	public void likeExactBackslashPercent() {
		assertEquals(List.of(10), ids(lightDao.lambdaQuery(LbUser.class)
				.likeExact(LbUser::getStaffName, "a\\%b").find()));
	}
}