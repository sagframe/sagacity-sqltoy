package org.sagacity.sqltoy.dialect.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.Serializable;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;

import javax.sql.DataSource;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.annotation.Column;
import org.sagacity.sqltoy.config.annotation.Entity;
import org.sagacity.sqltoy.config.annotation.Id;
import org.sagacity.sqltoy.dao.impl.DefaultSqlToyDaoImpl;
import org.sagacity.sqltoy.model.IgnoreCaseSet;
import org.sagacity.sqltoy.plugins.IUnifyFieldsHandler;

/**
 * 端到端回归:insert/insertIgnore静态SQL缓存key补齐IUnifyFieldsHandler维度后,
 * 多SqlToyContext(不同统一字段处理器)保存同一实体类时不得串用"取数据库时间"的SQL。
 * <p>
 * 修复前缓存key只有实体类+表+dbType+主键策略+rowversion:上下文A(createTime取库时间)先保存后,
 * 上下文B(无统一时间字段)会复用A的COALESCE(?,CURRENT_TIMESTAMP)SQL,导致B本应为null的create_time被库时间填充。
 * 本测试用两个H2内存库+两个独立上下文,直接验证落库值。
 * </p>
 */
public class InsertSqlCacheMultiContextH2Test {

	private static final String URL_A = "jdbc:h2:mem:ins_cache_a;DB_CLOSE_DELAY=-1";
	private static final String URL_B = "jdbc:h2:mem:ins_cache_b;DB_CLOSE_DELAY=-1";
	private static final String URL_C = "jdbc:h2:mem:ins_cache_c;DB_CLOSE_DELAY=-1";
	private static final String URL_D = "jdbc:h2:mem:ins_cache_d;DB_CLOSE_DELAY=-1";

	public static class TimeFieldHandler implements IUnifyFieldsHandler {
		private final IgnoreCaseSet createSqlTimeFields = new IgnoreCaseSet();

		public TimeFieldHandler(String... fields) {
			for (String field : fields) {
				createSqlTimeFields.add(field);
			}
		}

		@Override
		public IgnoreCaseSet createSqlTimeFields() {
			return createSqlTimeFields;
		}
	}

	@Entity(tableName = "t_ins_cache")
	public static class InsCacheVo implements Serializable {
		private static final long serialVersionUID = 1L;

		@Id(strategy = "assign")
		@Column(name = "ID")
		private String id;

		@Column(name = "CREATE_TIME")
		private Timestamp createTime;

		@Column(name = "NAME")
		private String name;

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}

		public Timestamp getCreateTime() {
			return createTime;
		}

		public void setCreateTime(Timestamp createTime) {
			this.createTime = createTime;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}
	}

	/** 反向验证用:同表名、不同实体类(避免与正向用例共用缓存key) */
	@Entity(tableName = "t_ins_cache")
	public static class InsCacheReverseVo implements Serializable {
		private static final long serialVersionUID = 1L;

		@Id(strategy = "assign")
		@Column(name = "ID")
		private String id;

		@Column(name = "CREATE_TIME")
		private Timestamp createTime;

		@Column(name = "NAME")
		private String name;

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}

		public Timestamp getCreateTime() {
			return createTime;
		}

		public void setCreateTime(Timestamp createTime) {
			this.createTime = createTime;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}
	}

	private static JdbcDataSource dataSource(String url) throws Exception {
		JdbcDataSource ds = new JdbcDataSource();
		ds.setURL(url);
		ds.setUser("sa");
		ds.setPassword("");
		try (Connection conn = ds.getConnection(); Statement st = conn.createStatement()) {
			st.execute("drop table if exists t_ins_cache");
			st.execute("create table t_ins_cache (ID varchar(50) primary key, CREATE_TIME timestamp, NAME varchar(50))");
		}
		return ds;
	}

	private static SqlToyContext context(DataSource dataSource, IUnifyFieldsHandler handler) throws Exception {
		SqlToyContext context = new SqlToyContext();
		context.setDefaultDataSource(dataSource);
		if (handler != null) {
			context.setUnifyFieldsHandler(handler);
		}
		context.initialize();
		return context;
	}

	private static DefaultSqlToyDaoImpl dao(SqlToyContext context, DataSource dataSource) {
		DefaultSqlToyDaoImpl dao = new DefaultSqlToyDaoImpl();
		dao.setSqlToyContext(context);
		dao.setDataSource(dataSource);
		return dao;
	}

	private static Timestamp queryCreateTime(DataSource dataSource, String id) throws Exception {
		try (Connection conn = dataSource.getConnection();
				java.sql.PreparedStatement ps = conn
						.prepareStatement("select CREATE_TIME from t_ins_cache where ID=?")) {
			ps.setString(1, id);
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next() ? rs.getTimestamp(1) : null;
			}
		}
	}

	@Test
	public void timeHandlerSqlMustNotLeakToPlainHandlerContext() throws Exception {
		JdbcDataSource dsA = dataSource(URL_A);
		JdbcDataSource dsB = dataSource(URL_B);
		SqlToyContext timeContext = context(dsA, new TimeFieldHandler("createTime"));
		SqlToyContext plainContext = context(dsB, new TimeFieldHandler());
		InsCacheVo timeVo = new InsCacheVo();
		timeVo.setId("1");
		timeVo.setName("time-handler");
		dao(timeContext, dsA).save(timeVo);
		InsCacheVo plainVo = new InsCacheVo();
		plainVo.setId("2");
		plainVo.setName("plain-handler");
		dao(plainContext, dsB).save(plainVo);
		// 取库时间的上下文:createTime为null由数据库当前时间填充
		assertNotNull(queryCreateTime(dsA, "1"), "createSqlTimeFields配置的上下文应由库时间填充create_time");
		// 无统一时间字段的上下文:不得复用取库时间的SQL,create_time应为null
		assertNull(queryCreateTime(dsB, "2"), "空handler上下文不得复用取库时间的insert SQL(修复前会串用)");
	}

	@Test
	public void plainHandlerSqlMustNotLeakToTimeHandlerContext() throws Exception {
		JdbcDataSource dsC = dataSource(URL_C);
		JdbcDataSource dsD = dataSource(URL_D);
		SqlToyContext plainContext = context(dsC, new TimeFieldHandler());
		SqlToyContext timeContext = context(dsD, new TimeFieldHandler("createTime"));
		// 反向顺序:先由空handler上下文落一份纯占位符SQL
		InsCacheReverseVo plainVo = new InsCacheReverseVo();
		plainVo.setId("1");
		plainVo.setName("plain-first");
		dao(plainContext, dsC).save(plainVo);
		InsCacheReverseVo timeVo = new InsCacheReverseVo();
		timeVo.setId("2");
		timeVo.setName("time-second");
		dao(timeContext, dsD).save(timeVo);
		assertNull(queryCreateTime(dsC, "1"));
		assertNotNull(queryCreateTime(dsD, "2"), "取库时间上下文不得复用纯占位符SQL(修复前create_time会为null)");
	}

	@Test
	public void explicitCreateTimeIsPreservedInBothContexts() throws Exception {
		JdbcDataSource dsA = dataSource(URL_A);
		SqlToyContext timeContext = context(dsA, new TimeFieldHandler("createTime"));
		Timestamp explicit = Timestamp.valueOf("2026-01-02 03:04:05");
		InsCacheVo vo = new InsCacheVo();
		vo.setId("3");
		vo.setName("explicit");
		vo.setCreateTime(explicit);
		dao(timeContext, dsA).save(vo);
		assertEquals(explicit, queryCreateTime(dsA, "3"), "显式给定的createTime应原样落库");
	}
}
