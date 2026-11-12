package org.sagacity.sqltoy.lambda;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.annotation.Column;
import org.sagacity.sqltoy.config.annotation.Entity;
import org.sagacity.sqltoy.config.annotation.Id;
import org.sagacity.sqltoy.dao.impl.DefaultLightDaoImpl;
import org.sagacity.sqltoy.lambda.join.JoinCol;

/**
 * 回归测试:外层组合下发的执行库类型必须限定在渲染作用域内
 *
 * <p>
 * 历史问题:composeSql把外层dbType写到成员实例的propagatedDbType后永不归还,成员被复用
 * (独立执行、显式换dataSource、被另一库的组合再次渲染)时会长期按旧外层库类型渲染,
 * 使Recursive关键字/DB2递归隐式JOIN/ClickHouse UNION DISTINCT等方言分支与实际执行库不符。
 */
public class LambdaDbTypePropagationScopeTest {

	private static final String URL = "jdbc:h2:mem:lambdascope;DB_CLOSE_DELAY=-1";

	private static SqlToyContext context;

	private static DefaultLightDaoImpl lightDao;

	@BeforeAll
	public static void init() throws Exception {
		try (Connection conn = DriverManager.getConnection(URL, "sa", "");
				Statement st = conn.createStatement()) {
			st.execute("drop table if exists scope_user");
			st.execute("create table scope_user (id int primary key, name varchar(50))");
			st.execute("insert into scope_user values (1,'a')");
		}
		JdbcDataSource dataSource = new JdbcDataSource();
		dataSource.setURL(URL);
		dataSource.setUser("sa");
		context = new SqlToyContext();
		context.setDefaultDataSource(dataSource);
		context.initialize();
		lightDao = new DefaultLightDaoImpl(context);
	}

	/** 外层绑定dao(能解析真实dbType,渲染期向成员下发),成员经create()构建未bind */
	private static LambdaQuery<ScopeUser> buildOuter(LambdaQuery<ScopeUser> member) {
		LambdaQuery<ScopeUser> outer = lightDao.lambdaQuery(ScopeUser.class).from(ScopeUser.class)
				.select(JoinCol.of(ScopeUser::getId).as("id")).eq(ScopeUser::getId, 1).unionAll(member);
		member.assemble(context::getEntityMeta);
		outer.assemble(context::getEntityMeta);
		return outer;
	}

	private static LambdaQuery<ScopeUser> buildUnboundMember() {
		return LambdaQuery.create(ScopeUser.class).from(ScopeUser.class)
				.select(JoinCol.of(ScopeUser::getId).as("id")).eq(ScopeUser::getId, 1);
	}

	@Test
	public void memberDbTypeIsRestoredAfterOuterRender() throws Exception {
		LambdaQuery<ScopeUser> member = buildUnboundMember();
		LambdaQuery<ScopeUser> outer = buildOuter(member);
		String sql = outer.getSql();
		assertTrue(sql != null && sql.contains("UNION ALL"), sql);
		// 渲染结束必须归还:否则成员之后独立执行/换dataSource会一直按外层库类型渲染
		assertNull(propagatedDbType(member), "渲染结束后成员上的下发库类型必须归零");
		assertEquals(-1, resolveDbType(member), "未bind dao的成员必须回落到自身解析(-1)");
	}

	@Test
	public void repeatedRenderIsStable() throws Exception {
		// 归零后再次渲染会重新下发,两次结果必须一致(不能因归还导致第二次渲染方言分支丢失)
		LambdaQuery<ScopeUser> member = buildUnboundMember();
		LambdaQuery<ScopeUser> outer = buildOuter(member);
		String first = outer.getSql();
		String second = outer.getSql();
		assertEquals(first, second);
		assertNull(propagatedDbType(member));
	}

	private static Object propagatedDbType(LambdaQuery<?> query) throws Exception {
		Field field = LambdaQuery.class.getDeclaredField("propagatedDbType");
		field.setAccessible(true);
		return field.get(query);
	}

	private static int resolveDbType(LambdaQuery<?> query) throws Exception {
		Method method = LambdaQuery.class.getDeclaredMethod("resolveDbType");
		method.setAccessible(true);
		return (Integer) method.invoke(query);
	}

	@Entity(tableName = "scope_user")
	public static class ScopeUser implements Serializable {
		private static final long serialVersionUID = 1L;
		@Id
		@Column(name = "ID")
		private Integer id;
		@Column(name = "NAME")
		private String name;

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
	}
}
