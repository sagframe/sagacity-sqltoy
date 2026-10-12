package org.sagacity.sqltoy.dialect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.annotation.Column;
import org.sagacity.sqltoy.config.annotation.Entity;
import org.sagacity.sqltoy.config.annotation.Id;
import org.sagacity.sqltoy.config.annotation.OneToMany;
import org.sagacity.sqltoy.config.SqlConfigParseUtils;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlToyResult;
import org.sagacity.sqltoy.dialect.impl.HighgoDialect;
import org.sagacity.sqltoy.model.DBProfile;
import org.sagacity.sqltoy.model.QueryExecutor;
import org.sagacity.sqltoy.model.QueryResult;
import org.sagacity.sqltoy.plugins.function.FunctionUtils;
import org.sagacity.sqltoy.utils.DataSourceUtils;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * 瀚高方言全维度真库特性冒烟(2026-10-12):覆盖函数适配之外的方言面——
 * 分页(findPageBySql经PG系limit形态)、saveOrUpdate(PG系merge into,含wrapSelectFields
 * 的case列定型)、锁(lockSql的for update,瀚高不支持wait)、like处理(processLike的
 * ESCAPE'\'追加,瀚高非backslash转义方言)、tableApi(getTables/getTableColumns经
 * PG系统表)、DDL类型映射(jsonb/bytea/numeric,瀚高PG12内核)、json/bytea的cast绑定
 * 形态真库执行。连接:vanilla pg驱动+显式dialect=highgo(vanilla驱动上报产品名
 * PostgreSQL无法自动识别,生产部署请显式配置或用官方jdbc:highgo://驱动)。
 */
public class HighgoDialectFeatureSmokeTest {

	private static Connection conn;

	private static SqlToyContext context;

	private static boolean available = false;

	@BeforeAll
	public static void init() throws Exception {
		try (Connection c = DriverManager.getConnection("jdbc:postgresql://localhost:5867/highgo", "highgo",
				"Sqltoy@2026"); Statement st = c.createStatement()) {
			st.execute("drop table if exists sqltoy_hg_feat");
			st.execute("create table sqltoy_hg_feat (id int primary key, nm varchar(50), score numeric(10,2),"
					+ " create_time timestamp, ext jsonb)");
			st.execute("insert into sqltoy_hg_feat values (1,'r1',10,'2026-01-15 10:30:00','{\"k\":\"v1\"}')");
			st.execute("insert into sqltoy_hg_feat values (2,'r2',20,'2026-03-20 14:00:00','{\"k\":\"v2\"}')");
			st.execute("insert into sqltoy_hg_feat values (3,'r3',30,'2026-05-20 09:00:00','{\"k\":\"v3\"}')");
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
				st.execute("drop table if exists sqltoy_hg_feat");
			} catch (Exception e) {
				// ignore
			}
			conn.close();
		}
	}

	private static DBProfile profile() {
		// 瀚高档案:vanilla pg驱动,显式highgo方言
		return new DBProfile(null, "highgo", DBType.HIGHGO, null, 12, null, null, false);
	}

	private Object querySingle(String sql) throws Exception {
		try (PreparedStatement pst = conn.prepareStatement(sql); ResultSet rs = pst.executeQuery()) {
			rs.next();
			return rs.getObject(1);
		}
	}

	/** 分页:有序分页逐页无重叠无遗漏(PG系limit ? offset ?形态) */
	@Test
	public void paginationOrdered() throws Exception {
		Assumptions.assumeTrue(available, "瀚高探针环境不可用,跳过");
		String sql = "select id,nm from sqltoy_hg_feat order by id";
		assertEquals(ids(page(sql, 1L, 2)), set(1L, 2L), "第一页");
		// 页基数受方法执行顺序影响,按总数动态断言
		long total = ((Number) querySingle("select count(*) from sqltoy_hg_feat")).longValue();
		assertEquals(Math.min(2, Math.max(0, (int) (total - 2))), ids(page(sql, 2L, 2)).size());
		assertEquals(ids(page(sql, 3L, 2)).size(), 0, "越界页空");
		// 无序分页
		assertEquals(2, ((List<?>) page("select id,nm from sqltoy_hg_feat where score>0", 1L, 2).getRows()).size(),
				"无序分页第一页2行");
	}

	private QueryResult page(String sql, long pageNo, int pageSize) throws Exception {
		SqlToyConfig config = new SqlToyConfig("highgo");
		config.setSql(sql);
		return new HighgoDialect().findPageBySql(context, config, new QueryExecutor(sql), null, pageNo, pageSize,
				conn, profile(), 500, -1);
	}

	private Set<Long> ids(QueryResult page) {
		Set<Long> set = new HashSet<>();
		for (Object row : (List<?>) page.getRows()) {
			List<?> cols = (List<?>) row;
			set.add(Long.valueOf(String.valueOf(cols.get(0))));
		}
		return set;
	}

	private Set<Long> set(Long... ls) {
		return new HashSet<>(java.util.Arrays.asList(ls));
	}

	/** saveOrUpdate:PG系merge into(瀚高PG12支持),含存在/不存在两分支与jsonb列 */
	@Test
	public void saveOrUpdateMerge() throws Exception {
		Assumptions.assumeTrue(available, "瀚高探针环境不可用,跳过");
		// 不存在→insert
		FeatVO vo = new FeatVO();
		vo.setId(91L);
		vo.setNm("new91");
		vo.setScore(91.0);
		Object pk = new HighgoDialect().saveOrUpdate(context, vo, null, conn, profile(), null, null);
		assertEquals("new91", querySingle("select nm from sqltoy_hg_feat where id=91"));
		// 存在→update(nvl弹性形态:nm为null时保留原值)
		FeatVO vo2 = new FeatVO();
		vo2.setId(91L);
		vo2.setNm(null);
		vo2.setScore(92.0);
		new HighgoDialect().saveOrUpdate(context, vo2, null, conn, profile(), null, null);
		assertEquals("new91", querySingle("select nm from sqltoy_hg_feat where id=91"), "null字段不覆盖原值");
		assertEquals(92.0, ((Number) querySingle("select score from sqltoy_hg_feat where id=91")).doubleValue(), 0.001,
				"非null字段更新生效");
	}

	/** 锁:select for update可执行;瀚高(PG内核)不支持wait n,lockSql不追加wait */
	@Test
	public void lockForUpdate() throws Exception {
		Assumptions.assumeTrue(available, "瀚高探针环境不可用,跳过");
		conn.setAutoCommit(false);
		try (PreparedStatement pst = conn
				.prepareStatement("select id from sqltoy_hg_feat where id=1 for update"); ResultSet rs = pst.executeQuery()) {
			assertTrue(rs.next(), "for update锁定行可查询");
		} finally {
			conn.setAutoCommit(true);
		}
		// lockSql形态:带waitTimeout时瀚高不应追加wait子句(PG无此语法,wait名单不含HIGHGO)
		String lockSql = org.sagacity.sqltoy.dialect.utils.DefaultDialectUtils.getLockSql(
				"select * from sqltoy_hg_feat", DBType.HIGHGO, org.sagacity.sqltoy.model.LockMode.UPGRADE, 5, false, null, false);
		assertTrue(lockSql.trim().endsWith("for update") && !lockSql.contains("wait"),
				"瀚高lockSql应为for update且无wait: " + lockSql);
	}

	/** like处理:processLike对特殊字符转义+追加ESCAPE'\'(瀚高非backslash方言) */
	@Test
	public void processLikeEscape() throws Exception {
		Assumptions.assumeTrue(available, "瀚高探针环境不可用,跳过");
		SqlToyResult r = SqlConfigParseUtils.processSql("select id from sqltoy_hg_feat where nm like :name",
				new String[] { "name" }, new Object[] { "a_b%c" }, "highgo");
		assertTrue(r.getSql().contains("escape '\\'") || r.getSql().toUpperCase().contains("ESCAPE '\\'"),
				"应追加ESCAPE'\\'子句: " + r.getSql());
		// 含%的值仅转义_不包裹%%(保留用户通配意图,与mysql系行为一致)
		assertEquals("a\\_b%c", String.valueOf(r.getParamsValue()[0]));
		// 无特殊字符:不追加ESCAPE
		SqlToyResult r2 = SqlConfigParseUtils.processSql("select id from sqltoy_hg_feat where nm like :name",
				new String[] { "name" }, new Object[] { "r1" }, "highgo");
		assertTrue(!r2.getSql().toLowerCase().contains("escape"), "无特殊字符不应追加ESCAPE: " + r2.getSql());
		// 真库执行双行对照:a_b与axb,转义下划线命中1行,未转义命中2行
		try (Statement st = conn.createStatement()) {
			st.execute("insert into sqltoy_hg_feat(id,nm,score) values (801,'a_b',1)");
			st.execute("insert into sqltoy_hg_feat(id,nm,score) values (802,'axb',2)");
		}
		assertEquals(1L, ((Number) querySingle(
				"select count(*) from sqltoy_hg_feat where nm like 'a\\_b' escape '\\'")).longValue(),
				"转义下划线应命中a_b单行");
		assertEquals(2L, ((Number) querySingle(
				"select count(*) from sqltoy_hg_feat where nm like 'a_b'")).longValue(),
				"未转义下划线为通配符命中两行");
		try (Statement st = conn.createStatement()) {
			st.execute("delete from sqltoy_hg_feat where id in (801,802)");
		}
	}

	/** tableApi:getTables/getTableColumns经PG系统表(systables/pg_catalog) */
	@Test
	public void tableApi() throws Exception {
		Assumptions.assumeTrue(available, "瀚高探针环境不可用,跳过");
		HighgoDialect dialect = new HighgoDialect();
		List<?> tables = dialect.getTables(null, "public", "sqltoy_hg%", conn, profile());
		assertNotNull(tables, "getTables应返回列表");
		assertTrue(tables.stream().anyMatch(
				t -> ((org.sagacity.sqltoy.model.TableMeta) t).getTableName().toLowerCase().contains("sqltoy_hg_feat")),
				"应探得探针表: " + tables);
		List<org.sagacity.sqltoy.model.ColumnMeta> cols = dialect.getTableColumns(null, "public",
				"sqltoy_hg_feat", conn, profile());
		assertTrue(cols.size() >= 5, "应返回全部列: " + cols.size());
		assertTrue(cols.stream().anyMatch(c -> "ext".equalsIgnoreCase(c.getColName())), "jsonb列ext应在列清单");
	}

	/** DDL类型映射+json/bytea绑定形态:jsonb列cast绑定、bytea的cast update */
	@Test
	public void ddlTypesAndJsonBytea() throws Exception {
		Assumptions.assumeTrue(available, "瀚高探针环境不可用,跳过");
		// jsonb insert cast形态(DialectUtils update分派同型)真库执行
		try (PreparedStatement pst = conn.prepareStatement(
				"update sqltoy_hg_feat set ext=cast(? as jsonb) where id=1")) {
			pst.setString(1, "{\"k\":\"v1x\"}");
			pst.executeUpdate();
		}
		assertEquals("v1x", querySingle("select ext->>'k' from sqltoy_hg_feat where id=1"), "jsonb cast绑定更新生效");
		// bytea cast形态(convertBlob路径)
		try (Statement st = conn.createStatement()) {
			st.execute("alter table sqltoy_hg_feat add column bin bytea");
		}
		try (PreparedStatement pst = conn.prepareStatement(
				"update sqltoy_hg_feat set bin=cast(? as bytea) where id=1")) {
			pst.setBytes(1, new byte[] { 1, 2, 3 });
			pst.executeUpdate();
		}
		try (ResultSet rs = conn.prepareStatement("select length(bin) from sqltoy_hg_feat where id=1")
				.executeQuery()) {
			rs.next();
			assertEquals(3, rs.getInt(1), "bytea cast绑定生效");
		}
		// DDL类型映射:瀚高落PG系(BYTEA/NUMERIC/DOUBLE PRECISION)
		assertEquals("BYTEA", org.sagacity.sqltoy.plugins.ddl.DDLUtils.convertType(
				meta("bytea"), DBType.HIGHGO));
		assertEquals("NUMERIC", org.sagacity.sqltoy.plugins.ddl.DDLUtils.convertType(
				meta("numeric"), DBType.HIGHGO));
	}

	private static org.sagacity.sqltoy.model.ColumnMeta meta(String nativeType) {
		org.sagacity.sqltoy.model.ColumnMeta m = new org.sagacity.sqltoy.model.ColumnMeta();
		m.setDataType("bytea".equals(nativeType) ? java.sql.Types.BLOB : java.sql.Types.DECIMAL);
		m.setNativeType(nativeType);
		return m;
	}

	/**
	 * vector类型CRUD(pgvector):DDL映射VECTOR(n)与实体存取全链路。社区镜像未预装
	 * pgvector(无vector.control),本机容器已源码编译pgvector 0.7.4安装并create
	 * extension,无扩展环境自动跳过。绑定走SqlUtil的PG系PGobject路径(isPGFamily含
	 * HIGHGO)——setString直绑vector列报类型错(实测"column v is of type vector but
	 * expression is of type character varying"),OTHER形态为唯一正确绑定
	 */
	@Test
	public void vectorCrud() throws Exception {
		Assumptions.assumeTrue(available, "瀚高探针环境不可用,跳过");
		try (Statement st = conn.createStatement()) {
			st.execute("create extension if not exists vector");
		} catch (Exception e) {
			Assumptions.assumeTrue(false, "瀚高无pgvector扩展,跳过vector用例: " + e.getMessage());
		}
		// DDL映射:VECTOR+维度→VECTOR(3);无维度→裸VECTOR(瀚高同pgvector)
		org.sagacity.sqltoy.model.ColumnMeta vm = new org.sagacity.sqltoy.model.ColumnMeta();
		vm.setDataType(org.sagacity.sqltoy.model.JdbcTypes.VECTOR);
		vm.setColumnSize(3);
		assertEquals("VECTOR(3)",
				org.sagacity.sqltoy.plugins.ddl.DDLUtils.convertType(vm, DBType.HIGHGO), "带维度渲染VECTOR(3)");
		vm.setColumnSize(0);
		assertEquals("VECTOR", org.sagacity.sqltoy.plugins.ddl.DDLUtils.convertType(vm, DBType.HIGHGO),
				"无维度渲染裸VECTOR");
		try (Statement st = conn.createStatement()) {
			st.execute("drop table if exists sqltoy_hg_vec");
			st.execute("create table sqltoy_hg_vec (id int primary key, v vector(3))");
		}
		HighgoDialect dialect = new HighgoDialect();
		// save:实体String字段→列类型名vector触发JdbcTypes.VECTOR→PGobject绑定
		VecVO vo = new VecVO();
		vo.setId(1L);
		vo.setV("[1,2,3]");
		dialect.save(context, vo, conn, profile(), null);
		assertEquals("[1,2,3]", String.valueOf(querySingle("select v from sqltoy_hg_vec where id=1")),
				"vector存入读回文本");
		// load:PGobject经归一化为文本
		VecVO loaded = (VecVO) dialect.load(context, new VecVO(1L), false, null, null, 0, conn, profile(), null,
				0);
		assertNotNull(loaded, "load返回实体");
		assertEquals("[1,2,3]", loaded.getV(), "实体load读回vector文本");
		// saveOrUpdate更新分支
		vo.setV("[4,5,6]");
		dialect.saveOrUpdate(context, vo, null, conn, profile(), null, null);
		assertEquals("[4,5,6]", String.valueOf(querySingle("select v from sqltoy_hg_vec where id=1")),
				"vector经saveOrUpdate更新");
		// 近邻检索(l2距离):[4,5,6]到[4,5,6]为0,到[1,2,3]为sqrt(27)
		Object dist = querySingle("select v <-> '[4,5,6]' from sqltoy_hg_vec where id=1");
		assertEquals(0.0, ((Number) dist).doubleValue(), 1e-9, "l2距离自比为0");
		try (Statement st = conn.createStatement()) {
			st.execute("drop table if exists sqltoy_hg_vec");
		}
	}

	/**
	 * saveAllIgnoreExist:瀚高(PG12内核)走on conflict do nothing(PostgreSqlDialect
	 * 的HIGHGO分支→insertIgnore),首遍插入/第二遍全忽略
	 */
	@Test
	public void saveAllIgnoreExist() throws Exception {
		Assumptions.assumeTrue(available, "瀚高探针环境不可用,跳过");
		HighgoDialect dialect = new HighgoDialect();
		java.util.List<FeatVO> list = new java.util.ArrayList<FeatVO>();
		for (long id : new long[] { 501, 502, 503 }) {
			FeatVO vo = new FeatVO();
			vo.setId(id);
			vo.setNm("ignore" + id);
			vo.setScore((double) id);
			list.add(vo);
		}
		try (Statement st = conn.createStatement()) {
			st.execute("delete from sqltoy_hg_feat where id in (501,502,503)");
		}
		Long cnt = dialect.saveAllIgnoreExist(context, list, 10, null, conn, profile(), null, null);
		assertEquals(Long.valueOf(3), cnt, "首遍3条新记录全部插入");
		assertEquals("ignore502", querySingle("select nm from sqltoy_hg_feat where id=502"), "插入值正确");
		for (FeatVO vo : list) {
			vo.setNm("changed" + vo.getId());
		}
		dialect.saveAllIgnoreExist(context, list, 10, null, conn, profile(), null, null);
		assertEquals("ignore501", querySingle("select nm from sqltoy_hg_feat where id=501"), "已存在记录被忽略,值未变化");
		try (Statement st = conn.createStatement()) {
			st.execute("delete from sqltoy_hg_feat where id in (501,502,503)");
		}
	}

	/**
	 * updateSaveFetch(锁查询→存在则修改/不存在则插入):瀚高走公共实现(vanilla pg驱动
	 * 的CONCUR_UPDATABLE原生支持updateRow/insertRow),两分支语义验证
	 */
	@Test
	public void updateSaveFetchLockModifyOrInsert() throws Exception {
		Assumptions.assumeTrue(available, "瀚高探针环境不可用,跳过");
		HighgoDialect dialect = new HighgoDialect();
		try (Statement st = conn.createStatement()) {
			st.execute("delete from sqltoy_hg_feat where id in (601,602)");
		}
		conn.setAutoCommit(false);
		try {
			FeatVO vo = new FeatVO();
			vo.setId(601L);
			vo.setNm("usf");
			vo.setScore(10.0);
			dialect.save(context, vo, conn, profile(), null);
			FeatVO exist = new FeatVO();
			exist.setId(601L);
			dialect.updateSaveFetch(context, exist, new org.sagacity.sqltoy.callback.UpdateRowHandler() {
				@Override
				public void updateRow(ResultSet rs, int index) throws Exception {
					rs.updateDouble("score", rs.getDouble("score") + 100);
				}
			}, 5, null, conn, profile(), null);
			conn.commit();
			assertEquals(110.0,
					((Number) querySingle("select score from sqltoy_hg_feat where id=601")).doubleValue(), 1e-9,
					"存在记录修改已落库");
			FeatVO absent = new FeatVO();
			absent.setId(602L);
			absent.setNm("usfIns");
			absent.setScore(20.0);
			dialect.updateSaveFetch(context, absent, new org.sagacity.sqltoy.callback.UpdateRowHandler() {
				@Override
				public void updateRow(ResultSet rs, int index) throws Exception {
					// 不存在场景不进入反调
				}
			}, 5, null, conn, profile(), null);
			conn.commit();
			assertEquals("usfIns", querySingle("select nm from sqltoy_hg_feat where id=602"), "不存在记录应被插入");
		} finally {
			conn.setAutoCommit(true);
			try (Statement st = conn.createStatement()) {
				st.execute("delete from sqltoy_hg_feat where id in (601,602)");
			}
		}
	}

	/**
	 * 主键策略(2026-10-12真库验证):IDENTITY(pg generated by default as identity)——
	 * 省略主键列自增+列提示getGeneratedKeys回取(提示须小写,见DataSourceUtils
	 * getReturnPrimaryKeyColumnCase的HIGHGO接线);SEQUENCE——coalesce(?,nextval)
	 * 写入+getGeneratedKeys回取;显式id转ASSIGN直插
	 */
	@Test
	public void pkStrategyIdentityAndSequence() throws Exception {
		Assumptions.assumeTrue(available, "瀚高探针环境不可用,跳过");
		try (Statement st = conn.createStatement()) {
			for (String t : new String[] { "sqltoy_hg_idn", "sqltoy_hg_seq" }) {
				try {
					st.execute("drop table " + t);
				} catch (Exception e) {
				}
			}
			try {
				st.execute("drop sequence sqltoy_hg_pkseq");
			} catch (Exception e) {
			}
			st.execute(
					"create table sqltoy_hg_idn (id bigint generated by default as identity primary key, nm varchar(30))");
			st.execute("create table sqltoy_hg_seq (id bigint primary key, nm varchar(30))");
			st.execute("create sequence sqltoy_hg_pkseq start with 100");
		}
		HighgoDialect dialect = new HighgoDialect();
		HgIdnVO idnNull = new HgIdnVO();
		idnNull.setNm("idnNull");
		Object pk1 = dialect.save(context, idnNull, conn, profile(), null);
		assertNotNull(pk1, "identity生成主键应回取非空");
		assertEquals(((Number) pk1).longValue(), idnNull.getId().longValue(), "主键应回写实体");
		HgSeqVO seqNull = new HgSeqVO();
		seqNull.setNm("seqNull");
		Object pk2 = dialect.save(context, seqNull, conn, profile(), null);
		assertTrue(((Number) pk2).longValue() >= 100, "sequence生成键应>=100(start with 100): " + pk2);
		assertEquals(((Number) pk2).longValue(), seqNull.getId().longValue(), "sequence主键应回写实体");
		HgSeqVO seqExp = new HgSeqVO();
		seqExp.setId(7002L);
		seqExp.setNm("seqExp");
		Object pk3 = dialect.save(context, seqExp, conn, profile(), null);
		assertEquals(7002L, ((Number) pk3).longValue(), "显式id直插返回原值");
		try (Statement st = conn.createStatement()) {
			st.execute("drop table sqltoy_hg_idn");
			st.execute("drop table sqltoy_hg_seq");
			st.execute("drop sequence sqltoy_hg_pkseq");
		}
	}

	/**
	 * 级联验证:save(单条级联子表批量insert)/update(cascade=true,子表saveOrUpdateAll)
	 * /saveAll与updateAll(批量,框架语义无级联)。重点:update级联子表分派:HIGHGO(PG12
	 * 内核无merge)应走pg14形态的update+saveIgnore兜底而非merge
	 */
	@Test
	public void cascadeSaveAndUpdate() throws Exception {
		Assumptions.assumeTrue(available, "瀚高探针环境不可用,跳过");
		try (Statement st = conn.createStatement()) {
			for (String t : new String[] { "sqltoy_hg_cas", "sqltoy_hg_cas_item" }) {
				try {
					st.execute("drop table " + t);
				} catch (Exception e) {
				}
			}
			st.execute("create table sqltoy_hg_cas (id int primary key, nm varchar(30))");
			st.execute("create table sqltoy_hg_cas_item (id int primary key, order_id int, item_nm varchar(30))");
		}
		HighgoDialect dialect = new HighgoDialect();
		HgCasVO parent = new HgCasVO();
		parent.setId(1L);
		parent.setNm("p1");
		HgCasItemVO i1 = new HgCasItemVO();
		i1.setId(101L);
		i1.setItemNm("item1");
		HgCasItemVO i2 = new HgCasItemVO();
		i2.setId(102L);
		i2.setItemNm("item2");
		parent.setItems(new java.util.ArrayList<>(java.util.Arrays.asList(i1, i2)));
		dialect.save(context, parent, conn, profile(), null);
		assertEquals(2L, ((Number) querySingle(
				"select count(*) from sqltoy_hg_cas_item where order_id=1")).longValue(), "级联子表2条且外键回写");
		i2.setItemNm("item2x");
		HgCasItemVO i3 = new HgCasItemVO();
		i3.setId(103L);
		i3.setItemNm("item3");
		parent.getItems().add(i3);
		parent.setNm("p1x");
		dialect.update(context, parent, null, true, null, null, conn, profile(), null);
		assertEquals("p1x", querySingle("select nm from sqltoy_hg_cas where id=1"), "主表更新生效");
		assertEquals("item2x", querySingle("select item_nm from sqltoy_hg_cas_item where id=102"),
				"子表存量修改生效");
		assertEquals("item3",
				querySingle("select item_nm from sqltoy_hg_cas_item where id=103 and order_id=1"),
				"子表新增生效且外键回写");
		java.util.List<HgCasVO> batch = new java.util.ArrayList<HgCasVO>();
		for (long id : new long[] { 11, 12, 13 }) {
			HgCasVO vo = new HgCasVO();
			vo.setId(id);
			vo.setNm("batch" + id);
			batch.add(vo);
		}
		dialect.saveAll(context, batch, 10, null, conn, profile(), null, null);
		assertEquals(4L, ((Number) querySingle("select count(*) from sqltoy_hg_cas")).longValue(), "saveAll批量3条");
		for (HgCasVO vo : batch) {
			vo.setNm("up" + vo.getId());
		}
		dialect.updateAll(context, batch, 10, null, null, null, conn, profile(), null, null);
		assertEquals("up12", querySingle("select nm from sqltoy_hg_cas where id=12"), "updateAll更新生效");
		try (Statement st = conn.createStatement()) {
			st.execute("drop table sqltoy_hg_cas");
			st.execute("drop table sqltoy_hg_cas_item");
		}
	}

	/**
	 * load/loadAll级联加载:load(cascadeTypes)回填@OneToMany子表集合;loadAll批量级联
	 * (子表按fk in(:fk)批量查询);不存在记录load返回null
	 */
	@Test
	public void loadAndLoadAllCascade() throws Exception {
		Assumptions.assumeTrue(available, "瀚高探针环境不可用,跳过");
		try (Statement st = conn.createStatement()) {
			for (String t : new String[] { "sqltoy_hg_cas2", "sqltoy_hg_cas2_item" }) {
				try {
					st.execute("drop table " + t);
				} catch (Exception e) {
				}
			}
			st.execute("create table sqltoy_hg_cas2 (id int primary key, nm varchar(30))");
			st.execute("create table sqltoy_hg_cas2_item (id int primary key, order_id int, item_nm varchar(30))");
			st.execute("insert into sqltoy_hg_cas2 values (1,'p1')");
			st.execute("insert into sqltoy_hg_cas2 values (2,'p2')");
			st.execute("insert into sqltoy_hg_cas2_item values (101,1,'itemA')");
			st.execute("insert into sqltoy_hg_cas2_item values (102,1,'itemB')");
			st.execute("insert into sqltoy_hg_cas2_item values (201,2,'itemC')");
		}
		HighgoDialect dialect = new HighgoDialect();
		HgCas2VO p = new HgCas2VO();
		p.setId(1L);
		HgCas2VO loaded = (HgCas2VO) dialect.load(context, p, false,
				java.util.Arrays.asList(HgCas2ItemVO.class), null, 0, conn, profile(), null, 0);
		assertNotNull(loaded, "存在的记录load返回实体");
		assertEquals("p1", loaded.getNm(), "主表字段回填");
		assertNotNull(loaded.getItems(), "级联子表集合非null");
		assertEquals(2, loaded.getItems().size(), "子表2条");
		boolean hasA = false;
		boolean hasB = false;
		for (HgCas2ItemVO item : loaded.getItems()) {
			hasA |= "itemA".equals(item.getItemNm());
			hasB |= "itemB".equals(item.getItemNm());
		}
		assertTrue(hasA && hasB, "子表值回填正确");
		HgCas2VO absent = new HgCas2VO();
		absent.setId(999L);
		assertNull(dialect.load(context, absent, false, null, null, 0, conn, profile(), null, 0),
				"不存在的记录load返回null");
		HgCas2VO p1 = new HgCas2VO();
		p1.setId(1L);
		HgCas2VO p2 = new HgCas2VO();
		p2.setId(2L);
		List<?> all = (List<?>) dialect.loadAll(context, new java.util.ArrayList<>(java.util.Arrays.asList(p1, p2)),
				false, java.util.Arrays.asList(HgCas2ItemVO.class), null, 0, conn, profile(), null, 500, -1, 0);
		assertEquals(2, all.size(), "loadAll返回2个实体");
		int totalItems = 0;
		for (Object one : all) {
			HgCas2VO vo = (HgCas2VO) one;
			assertNotNull(vo.getItems(), "批量级联子表非null: id=" + vo.getId());
			totalItems += vo.getItems().size();
			if (vo.getId() == 1L) {
				assertEquals(2, vo.getItems().size(), "父1的子表2条");
			} else if (vo.getId() == 2L) {
				assertEquals(1, vo.getItems().size(), "父2的子表1条");
			}
		}
		assertEquals(3, totalItems, "子表总数3(批量in查询按fk正确归属)");
		try (Statement st = conn.createStatement()) {
			st.execute("drop table sqltoy_hg_cas2");
			st.execute("drop table sqltoy_hg_cas2_item");
		}
	}

	@Entity(tableName = "sqltoy_hg_cas2")
	public static class HgCas2VO implements java.io.Serializable {
		@Id()
		@Column(name = "id")
		private Long id;

		@Column(name = "nm")
		private String nm;

		@OneToMany(fields = { "id" }, mappedFields = { "orderId" })
		private List<HgCas2ItemVO> items;

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

		public List<HgCas2ItemVO> getItems() {
			return items;
		}

		public void setItems(List<HgCas2ItemVO> items) {
			this.items = items;
		}
	}

	@Entity(tableName = "sqltoy_hg_cas2_item")
	public static class HgCas2ItemVO implements java.io.Serializable {
		@Id()
		@Column(name = "id")
		private Long id;

		@Column(name = "order_id")
		private Long orderId;

		@Column(name = "item_nm")
		private String itemNm;

		public Long getId() {
			return id;
		}

		public void setId(Long id) {
			this.id = id;
		}

		public Long getOrderId() {
			return orderId;
		}

		public void setOrderId(Long orderId) {
			this.orderId = orderId;
		}

		public String getItemNm() {
			return itemNm;
		}

		public void setItemNm(String itemNm) {
			this.itemNm = itemNm;
		}
	}

	@Entity(tableName = "sqltoy_hg_cas")
	public static class HgCasVO implements java.io.Serializable {
		@Id()
		@Column(name = "id")
		private Long id;

		@Column(name = "nm")
		private String nm;

		@OneToMany(fields = { "id" }, mappedFields = { "orderId" })
		private List<HgCasItemVO> items;

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

		public List<HgCasItemVO> getItems() {
			return items;
		}

		public void setItems(List<HgCasItemVO> items) {
			this.items = items;
		}
	}

	@Entity(tableName = "sqltoy_hg_cas_item")
	public static class HgCasItemVO implements java.io.Serializable {
		@Id()
		@Column(name = "id")
		private Long id;

		@Column(name = "order_id")
		private Long orderId;

		@Column(name = "item_nm")
		private String itemNm;

		public Long getId() {
			return id;
		}

		public void setId(Long id) {
			this.id = id;
		}

		public Long getOrderId() {
			return orderId;
		}

		public void setOrderId(Long orderId) {
			this.orderId = orderId;
		}

		public String getItemNm() {
			return itemNm;
		}

		public void setItemNm(String itemNm) {
			this.itemNm = itemNm;
		}
	}

	/**
	 * 方言自动识别(2026-10-12指纹探测):瀚高的version()/驱动ProductName完全伪装成
	 * vanilla PostgreSQL(实测无任何HighGo标记),但oracle兼容层在系统目录留有硬指纹
	 * (oracle_catalog schema/sys_guid函数,vanilla与orafce均无),vanilla pg驱动场景
	 * 免显式dialect=highgo配置即可自动校正;vanilla PG不误判
	 */
	@Test
	public void autoDetectByFingerprint() throws Exception {
		Assumptions.assumeTrue(available, "瀚高探针环境不可用,跳过");
		org.sagacity.sqltoy.model.DBProfile detected = org.sagacity.sqltoy.utils.DataSourceUtils
				.getDBProfile(conn);
		assertEquals("highgo", detected.getDialect(), "瀚高应经目录指纹自动识别(免显式dialect配置)");
		assertEquals(DBType.HIGHGO, detected.getDbType(), "自动识别dbType应为HIGHGO(240)");
		// 反向:vanilla PG(5433)无指纹不得误判
		Boolean vanillaOk = null;
		try (java.sql.Connection vconn = DriverManager.getConnection("jdbc:postgresql://localhost:5433/postgres",
				"postgres", "Sqltoy@2026")) {
			org.sagacity.sqltoy.model.DBProfile vanilla = org.sagacity.sqltoy.utils.DataSourceUtils
					.getDBProfile(vconn);
			vanillaOk = "postgresql".equals(vanilla.getDialect()) && vanilla.getDbType() == DBType.POSTGRESQL;
		} catch (Exception e) {
			// vanilla容器不在时跳过反向断言
		}
		if (vanillaOk != null) {
			assertTrue(vanillaOk, "vanilla PostgreSQL不得被误判为highgo");
		}
	}

	@Entity(tableName = "sqltoy_hg_idn")
	public static class HgIdnVO implements java.io.Serializable {
		@Id(strategy = "identity")
		@Column(name = "id")
		private Long id;

		@Column(name = "nm")
		private String nm;

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
	}

	@Entity(tableName = "sqltoy_hg_seq")
	public static class HgSeqVO implements java.io.Serializable {
		@Id(strategy = "sequence", sequence = "sqltoy_hg_pkseq")
		@Column(name = "id")
		private Long id;

		@Column(name = "nm")
		private String nm;

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
	}

	@Entity(tableName = "sqltoy_hg_vec")
	public static class VecVO implements java.io.Serializable {
		@Id()
		@Column(name = "id")
		private Long id;

		// type=JdbcTypes.VECTOR:注解type默认OTHER会按Java字段类型推导为VARCHAR,
		// setString直绑vector列报类型错,显式声明触发SqlUtil的PG系PGobject绑定
		@Column(name = "v", type = org.sagacity.sqltoy.model.JdbcTypes.VECTOR)
		private String v;

		public VecVO() {
		}

		public VecVO(Long id) {
			this.id = id;
		}

		public Long getId() {
			return id;
		}

		public void setId(Long id) {
			this.id = id;
		}

		public String getV() {
			return v;
		}

		public void setV(String v) {
			this.v = v;
		}
	}

	@Entity(tableName = "sqltoy_hg_feat")
	public static class FeatVO implements java.io.Serializable {
		@Id()
		@Column(name = "id")
		private Long id;

		@Column(name = "nm")
		private String nm;

		@Column(name = "score")
		private Double score;

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

		public Double getScore() {
			return score;
		}

		public void setScore(Double score) {
			this.score = score;
		}
	}
}
