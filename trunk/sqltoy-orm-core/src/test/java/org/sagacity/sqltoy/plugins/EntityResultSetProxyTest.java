package org.sagacity.sqltoy.plugins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.FieldMeta;
import org.sagacity.sqltoy.utils.DataSourceUtils;

/**
 * 回归测试：代理类按实体类缓存复用(不再每行生成加载新类),且拦截器按行绑定互不串扰
 */
public class EntityResultSetProxyTest {
	public static class Foo {
		private String name;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}
	}

	private static Connection conn;
	private static EntityMeta entityMeta;

	@BeforeAll
	public static void setUp() throws Exception {
		conn = DriverManager.getConnection("jdbc:h2:mem:entityProxy;DB_CLOSE_DELAY=-1", "sa", "");
		entityMeta = new EntityMeta();
		entityMeta.setEntityClass(Foo.class);
		entityMeta
				.addFieldMeta(new FieldMeta("name", "name", null, null, java.sql.Types.VARCHAR, true, false, 50, 0, 0));
	}

	@AfterAll
	public static void tearDown() throws Exception {
		if (conn != null) {
			conn.close();
		}
	}

	private ResultSet query(String value) throws Exception {
		Statement st = conn.createStatement();
		ResultSet rs = st.executeQuery("select '" + value + "' as name");
		rs.next();
		return rs;
	}

	@Test
	public void proxyClassReusedAcrossCalls() throws Exception {
		// 修复前每次createProxy都make+load一个新类,多行结果集场景Metaspace单调增长直至OOM
		ResultSet rs = query("hello");
		Foo first = EntityResultSetProxy.createProxy(null, DataSourceUtils.DBType.H2, conn, rs, Foo.class, entityMeta);
		for (int i = 0; i < 500; i++) {
			Foo proxy = EntityResultSetProxy.createProxy(null, DataSourceUtils.DBType.H2, conn, rs, Foo.class,
					entityMeta);
			assertSame(first.getClass(), proxy.getClass());
			assertEquals("hello", proxy.getName());
		}
	}

	@Test
	public void interceptorBoundPerRowWithoutCrossContamination() throws Exception {
		ResultSet rsA = query("hello");
		ResultSet rsB = query("world");
		Foo proxyA = EntityResultSetProxy.createProxy(null, DataSourceUtils.DBType.H2, conn, rsA, Foo.class,
				entityMeta);
		Foo proxyB = EntityResultSetProxy.createProxy(null, DataSourceUtils.DBType.H2, conn, rsB, Foo.class,
				entityMeta);
		assertEquals("hello", proxyA.getName());
		assertEquals("world", proxyB.getName());
		// 后创建的代理不影响先创建代理的行绑定
		assertEquals("hello", proxyA.getName());
	}

	@Test
	public void nonPropertyMethodFallsBackToSuper() throws Exception {
		ResultSet rs = query("hello");
		Foo proxy = EntityResultSetProxy.createProxy(null, DataSourceUtils.DBType.H2, conn, rs, Foo.class, entityMeta);
		// toString等非get/set方法走原逻辑,不因代理拦截报错
		assertEquals("hello", proxy.getName());
		assertEquals(proxy.toString(), proxy.toString());
	}

	/**
	 * 模拟updateSaveFetch真实形态:逐行回调,每行操作的字段和业务逻辑各不相同, 验证缓存的同一个代理类在行间逻辑差异下行为正确
	 */
	public static class OrderRow {
		private Long id;
		private String name;
		private Double amt;

		public Long getId() {
			return id;
		}

		public String getName() {
			return name;
		}

		public Double getAmt() {
			return amt;
		}
	}

	@Test
	public void sameCachedClassSupportsDifferentLogicPerRow() throws Exception {
		Statement st = conn.createStatement();
		st.execute("create table PROXY_DEMO(ID INT PRIMARY KEY, NAME VARCHAR(50), AMT NUMERIC(10,2))");
		st.execute("insert into PROXY_DEMO values(1,'alice',10.50),(2,'bob',20.75),(3,'carl',30.25)");
		EntityMeta orderMeta = new EntityMeta();
		orderMeta.setEntityClass(OrderRow.class);
		orderMeta.addFieldMeta(new FieldMeta("id", "ID", null, null, java.sql.Types.INTEGER, false, false, 10, 0, 0));
		orderMeta
				.addFieldMeta(new FieldMeta("name", "NAME", null, null, java.sql.Types.VARCHAR, true, false, 50, 0, 0));
		orderMeta.addFieldMeta(new FieldMeta("amt", "AMT", null, null, java.sql.Types.DECIMAL, true, false, 10, 2, 0));
		ResultSet rs = st.executeQuery("select * from PROXY_DEMO order by ID");
		Class<?> cachedClass = null;
		int index = 0;
		while (rs.next()) {
			// 等价于BeanUtil.toSqlToyHandler的逐行回调
			OrderRow row = EntityResultSetProxy.createProxy(null, DataSourceUtils.DBType.H2, conn, rs, OrderRow.class,
					orderMeta);
			if (cachedClass == null) {
				cachedClass = row.getClass();
			} else {
				assertSame(cachedClass, row.getClass());
			}
			// 每行业务逻辑和访问字段各不相同
			switch (index) {
			case 0:
				// 第1行:只读name
				assertEquals("alice", row.getName());
				break;
			case 1:
				// 第2行:读name+amt,交叉访问不受第1行影响
				assertEquals("bob", row.getName());
				assertEquals(20.75, row.getAmt(), 0.0001);
				break;
			case 2:
				// 第3行:不访问实体字段,仅走原生方法
				assertEquals(row.toString(), row.toString());
				break;
			}
			index++;
		}
		assertEquals(3, index);
		st.execute("drop table PROXY_DEMO");
	}
}
