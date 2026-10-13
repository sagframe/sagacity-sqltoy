package org.sagacity.sqltoy.dialect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.annotation.Column;
import org.sagacity.sqltoy.config.annotation.Entity;
import org.sagacity.sqltoy.config.annotation.Id;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.model.DBProfile;
import org.sagacity.sqltoy.model.QueryResult;
import org.sagacity.sqltoy.plugins.function.FunctionUtils;
import org.sagacity.sqltoy.dialect.impl.HighgoDialect;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * HIGHGO三扩展类型(jsonb/vector/geometry)的saveOrUpdate全场景验证:
 * PG12内核无merge→PostgreSqlDialect的PG14兜底(updateAll+saveAllIgnoreExist),
 * update路径getUpdateSql对扩展类型的nullFunction(COALESCE)包裹是否可行是核心疑点
 */
public class HgExtTypeSaveOrUpdateTest {

	private static Connection conn;
	private static SqlToyContext context;
	private static boolean available = false;

	@BeforeAll
	public static void init() throws Exception {
		try (Connection c = DriverManager.getConnection("jdbc:postgresql://localhost:5867/highgo", "highgo",
				"Sqltoy@2026"); Statement st = c.createStatement()) {
			// 三扩展齐备性检查
			for (String ext : new String[] { "vector", "postgis" }) {
				try {
					st.execute("create extension if not exists " + ext);
				} catch (Exception e) {
					System.out.println("[HGEXT] 扩展" + ext + "不可用: " + e.getMessage());
				}
			}
			try (java.sql.ResultSet rs = st
					.executeQuery("select count(*) from pg_extension where extname in ('vector','postgis')")) {
				rs.next();
				if (rs.getInt(1) < 2) {
					return;
				}
			}
			st.execute("drop table if exists sqltoy_hg_ext3");
			st.execute("create table sqltoy_hg_ext3 (id int primary key, nm varchar(50), "
					+ "ext jsonb, v vector(3), g geometry)");
			st.execute("insert into sqltoy_hg_ext3 values (1,'orig','{\"k\":\"v0\"}','[1,2,3]','POINT(9 9)')");
		}
		conn = DriverManager.getConnection("jdbc:postgresql://localhost:5867/highgo", "highgo", "Sqltoy@2026");
		context = new SqlToyContext();
		context.setConnectionFactory(new org.sagacity.sqltoy.integration.ConnectionFactory() {
			@Override
			public Connection getConnection(DataSource ds) {
				try {
					return ds.getConnection();
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}

			@Override
			public void releaseConnection(Connection c, DataSource ds) {
				try {
					if (c != null) {
						c.close();
					}
				} catch (Exception e) {
					// ignore
				}
			}
		});
		FunctionUtils.setFunctionConverts(java.util.Arrays.asList("default"));
		available = true;
	}

	@AfterAll
	public static void destroy() throws Exception {
		if (conn != null) {
			try (Statement st = conn.createStatement()) {
				st.execute("drop table if exists sqltoy_hg_ext3");
			} catch (Exception e) {
				// ignore
			}
			conn.close();
		}
	}

	private static DBProfile profile() {
		return new DBProfile(null, "highgo", DBType.HIGHGO, null, 12, null, null, false);
	}

	private Object q(String sql) throws Exception {
		try (Statement st = conn.createStatement(); java.sql.ResultSet rs = st.executeQuery(sql)) {
			return rs.next() ? rs.getObject(1) : null;
		}
	}

	/**
	 * saveOrUpdate(updateAll+saveIgnore兜底)三扩展类型全覆盖:
	 * 1)新记录insert分支(saveAllIgnoreExist→insertIgnore→on conflict do nothing)
	 * 2)存量update分支(updateAll→getUpdateSql的COALESCE包裹)
	 */
	@Test
	public void allThreeTypesSaveOrUpdate() throws Exception {
		Assumptions.assumeTrue(available, "瀚高扩展不全,跳过");
		HighgoDialect dialect = new HighgoDialect();
		// ---- 批量混合:1条存量(update)+2条新(insert) ----
		List<HgExt3VO> list = new ArrayList<HgExt3VO>();
		// 存量(id=1):三类型全改
		HgExt3VO exist = new HgExt3VO();
		exist.setId(1L);
		exist.setNm("upd1");
		exist.setExt("{\"k\":\"v1x\",\"n\":42}");
		exist.setV("[7,8,9]");
		exist.setG("POLYGON((0 0,2 0,2 2,0 2,0 0))");
		list.add(exist);
		// 新增(id=10/11)
		HgExt3VO new10 = new HgExt3VO();
		new10.setId(10L);
		new10.setNm("ins10");
		new10.setExt("{\"k\":\"v10\"}");
		new10.setV("[10,20,30]");
		new10.setG("POINT(10 20)");
		list.add(new10);
		HgExt3VO new11 = new HgExt3VO();
		new11.setId(11L);
		new11.setNm("ins11");
		new11.setExt("{\"k\":\"v11\"}");
		new11.setV("[11,21,31]");
		new11.setG("LINESTRING(0 0,5 5)");
		list.add(new11);
		Long cnt = dialect.saveOrUpdateAll(context, list, 10, null, null, conn, profile(), null, null);
		assertNotNull(cnt, "saveOrUpdateAll返回非null");
		// ---- 验证update分支(id=1):三类型全改 ----
		assertEquals("upd1", q("select nm from sqltoy_hg_ext3 where id=1"), "update主字段");
		assertEquals("{\"k\": \"v1x\", \"n\": 42}", String.valueOf(q("select ext::text from sqltoy_hg_ext3 where id=1")),
				"update的jsonb");
		assertTrue(String.valueOf(q("select v::text from sqltoy_hg_ext3 where id=1")).contains("[7,8,9]"),
				"update的vector: " + q("select v::text from sqltoy_hg_ext3 where id=1"));
		assertTrue(String.valueOf(q("select st_astext(g) from sqltoy_hg_ext3 where id=1")).toUpperCase()
				.contains("POLYGON"), "update的geometry");
		// ---- 验证insert分支(id=10/11):三类型全写 ----
		assertEquals("ins10", q("select nm from sqltoy_hg_ext3 where id=10"), "insert主字段");
		assertTrue(String.valueOf(q("select ext::text from sqltoy_hg_ext3 where id=10")).contains("v10"),
				"insert的jsonb");
		assertTrue(String.valueOf(q("select v::text from sqltoy_hg_ext3 where id=10")).contains("[10,20,30]"),
				"insert的vector");
		assertTrue(String.valueOf(q("select st_astext(g) from sqltoy_hg_ext3 where id=10")).toUpperCase()
				.contains("POINT"), "insert的geometry");
		assertEquals("ins11", q("select nm from sqltoy_hg_ext3 where id=11"), "insert第二行主字段");
		// ---- 验证null弹性(update传null字段不覆盖)----
		HgExt3VO nullExt = new HgExt3VO();
		nullExt.setId(1L);
		nullExt.setNm("nullext");
		nullExt.setExt(null);
		nullExt.setV(null);
		nullExt.setG(null);
		dialect.saveOrUpdate(context, nullExt, null, conn, profile(), null, null);
		assertEquals("nullext", q("select nm from sqltoy_hg_ext3 where id=1"), "null弹性主字段更新");
		assertTrue(String.valueOf(q("select ext::text from sqltoy_hg_ext3 where id=1")).contains("v1x"),
				"null的jsonb不覆盖原值");
		assertTrue(String.valueOf(q("select v::text from sqltoy_hg_ext3 where id=1")).contains("[7,8,9]"),
				"null的vector不覆盖原值");
		assertTrue(String.valueOf(q("select st_astext(g) from sqltoy_hg_ext3 where id=1")).toUpperCase()
				.contains("POLYGON"), "null的geometry不覆盖原值");
		// ---- 验证load回读三类型 ----
		HgExt3VO loaded = (HgExt3VO) dialect.load(context, new HgExt3VO(1L), false, null, null, 0, conn, profile(),
				null, 0);
		assertNotNull(loaded, "load返回实体");
		assertTrue(loaded.getExt() != null && loaded.getExt().contains("v1x"), "load的jsonb: " + loaded.getExt());
		assertTrue(loaded.getV() != null && loaded.getV().contains("[7,8,9]"), "load的vector: " + loaded.getV());
		assertTrue(loaded.getG() != null && loaded.getG().toUpperCase().contains("POLYGON"),
				"load的geometry: " + loaded.getG());
	}

	@Entity(tableName = "sqltoy_hg_ext3")
	public static class HgExt3VO implements java.io.Serializable {
		@Id()
		@Column(name = "id")
		private Long id;

		@Column(name = "nm")
		private String nm;

		@Column(name = "ext", type = org.sagacity.sqltoy.model.JdbcTypes.JSONB)
		private String ext;

		@Column(name = "v", type = org.sagacity.sqltoy.model.JdbcTypes.VECTOR)
		private String v;

		@Column(name = "g", type = org.sagacity.sqltoy.model.JdbcTypes.GEOMETRY)
		private String g;

		public HgExt3VO() {
		}

		public HgExt3VO(Long id) {
			this.id = id;
		}

		public Long getId() {
			return id;
		}

		public void setId(Long id) {
			this.id = id;
		}

		public String getNm() {
			return nm;
		}

		public void setNm(String nm) {
			this.nm = nm;
		}

		public String getExt() {
			return ext;
		}

		public void setExt(String ext) {
			this.ext = ext;
		}

		public String getV() {
			return v;
		}

		public void setV(String v) {
			this.v = v;
		}

		public String getG() {
			return g;
		}

		public void setG(String g) {
			this.g = g;
		}
	}
}
