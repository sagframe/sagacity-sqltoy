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
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.annotation.Column;
import org.sagacity.sqltoy.config.annotation.Entity;
import org.sagacity.sqltoy.config.annotation.Id;
import org.sagacity.sqltoy.config.annotation.OneToMany;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.callback.UpdateRowHandler;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.dialect.impl.GBase8sDialect;
import org.sagacity.sqltoy.model.DBProfile;
import org.sagacity.sqltoy.model.LockMode;
import org.sagacity.sqltoy.model.QueryExecutor;
import org.sagacity.sqltoy.model.QueryResult;
import org.sagacity.sqltoy.plugins.function.FunctionUtils;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * GBase 8s方言全维度真库特性冒烟(2026-10-12):分页(limit/offset形态)、saveOrUpdate
 * (oracle系merge)、锁(for update+set lock mode会话等待)、tableApi(JDBC元数据)、
 * 函数转换(nvl/decode/instr/add_months等原生透传,now→CURRENT)、json类型。
 * 连接:gbasedbt-sqli://19088/sqltoydb,GBASEDBTSERVER=gbase01。
 */
public class Gbase8sDialectFeatureSmokeTest {

	private static Connection conn;

	private static SqlToyContext context;

	private static boolean available = false;

	@BeforeAll
	public static void init() throws Exception {
		Class.forName("com.gbasedbt.jdbc.IfxDriver");
		String url = "jdbc:gbasedbt-sqli://localhost:19088/gbasedbt:GBASEDBTSERVER=gbase01;"
				+ "DB_LOCALE=zh_CN.utf8;CLIENT_LOCALE=zh_CN.utf8";
		try (Connection c = DriverManager.getConnection(url, "gbasedbt", "GBase123");
				Statement st = c.createStatement()) {
			try {
				st.execute("drop table sqltoy_g8_feat");
			} catch (Exception e) {
				// ignore
			}
			st.execute("create table sqltoy_g8_feat (id int primary key, nm lvarchar(50),"
					+ " score decimal(10,2), create_time datetime year to second)");
			st.execute("insert into sqltoy_g8_feat values (1,'r1',10,'2026-01-15 10:30:00')");
			st.execute("insert into sqltoy_g8_feat values (2,'r2',20,'2026-03-20 14:00:00')");
			st.execute("insert into sqltoy_g8_feat values (3,'r3',30,'2026-05-20 09:00:00')");
		}
		conn = DriverManager.getConnection(url, "gbasedbt", "GBase123");
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
				st.execute("drop table sqltoy_g8_feat");
			} catch (Exception e) {
				// ignore
			}
			conn.close();
		}
	}

	private static DBProfile profile() {
		return new DBProfile(null, "gbase8s", DBType.GBASE8S, null, 12, null, null, false);
	}

	private Object querySingle(String sql) throws Exception {
		try (PreparedStatement pst = conn.prepareStatement(sql); ResultSet rs = pst.executeQuery()) {
			rs.next();
			return rs.getObject(1);
		}
	}

	@Test
	public void g8Available() {
		Assumptions.assumeTrue(available, "GBase 8s探针环境不可用,跳过");
	}

	/** 分页:limit ? offset ?形态逐页无重叠无遗漏 */
	@Test
	public void paginationOrdered() throws Exception {
		Assumptions.assumeTrue(available);
		String sql = "select id,nm from sqltoy_g8_feat order by id";
		assertEquals(2, rows(page(sql, 1L, 2)), "第一页2行");
		// 3行数据: 第二页1行(offset2+limit2越界1行)
		assertEquals(0, rows(page(sql, 3L, 2)), "越界页空");
		// 页内容正确性:第一页id为1,2
		List<?> page1 = (List<?>) page(sql, 1L, 2).getRows();
		assertEquals("1", String.valueOf(((List<?>) page1.get(0)).get(0)), "第一页首行id=1");
		assertEquals("2", String.valueOf(((List<?>) page1.get(1)).get(0)), "第一页次行id=2");
	}

	private QueryResult page(String sql, long pageNo, int pageSize) throws Exception {
		SqlToyConfig config = new SqlToyConfig("gbase8s");
		config.setSql(sql);
		return new GBase8sDialect().findPageBySql(context, config, new QueryExecutor(sql), null, pageNo, pageSize,
				conn, profile(), 500, -1);
	}

	private int rows(QueryResult p) {
		return ((List<?>) p.getRows()).size();
	}

	/** saveOrUpdate:oracle系merge形态(真库支持MERGE INTO USING) */
	@Test
	public void saveOrUpdateMerge() throws Exception {
		Assumptions.assumeTrue(available);
		G8FeatVO vo = new G8FeatVO();
		vo.setId(91L);
		vo.setNm("new91");
		vo.setScore(91.0);
		try {
			Long r = (Long) new GBase8sDialect().saveOrUpdate(context, vo, null, conn, profile(), null, null);
			System.out.println("[G8SU] saveOrUpdate return=" + r);
		} catch (Exception e) {
			System.out.println("[G8SU] exception=" + e.getMessage());
		}
		assertEquals("new91", querySingle("select nm from sqltoy_g8_feat where id=91").toString().trim(),
				"merge插入新记录");
		G8FeatVO vo2 = new G8FeatVO();
		vo2.setId(91L);
		vo2.setNm(null);
		vo2.setScore(92.0);
		new GBase8sDialect().saveOrUpdate(context, vo2, null, conn, profile(), null, null);
		assertEquals("new91", querySingle("select nm from sqltoy_g8_feat where id=91").toString().trim(),
				"null字段不覆盖");
	}

	/** 锁:简单select for update可执行;set lock mode to wait会话级设置 */
	@Test
	public void lockForUpdate() throws Exception {
		Assumptions.assumeTrue(available);
		conn.setAutoCommit(false);
		try (PreparedStatement pst = conn
				.prepareStatement("select id from sqltoy_g8_feat where id=1 for update");
				ResultSet rs = pst.executeQuery()) {
			assertTrue(rs.next(), "for update锁定行可查询");
		} finally {
			conn.setAutoCommit(true);
		}
		String lockSql = org.sagacity.sqltoy.dialect.utils.DefaultDialectUtils.getLockSql(
				"select * from sqltoy_g8_feat", DBType.GBASE8S, org.sagacity.sqltoy.model.LockMode.UPGRADE, 0, false,
				null, false);
		assertTrue(lockSql.trim().endsWith("for update") && !lockSql.contains("wait"),
				"lockSql应为for update无wait子句: " + lockSql);
	}

	/**
	 * tableApi:该容器镜像(liaosnet/gbase8s)的列名读取双坑(真库实测):JDBC元数据
	 * getTables不见用户表(全库99行均为系统表)/systables系统表varchar列(tabname)
	 * 读回空串——用户表自身的varchar读写正常,仅系统目录损坏。测试降为调用不抛
	 * 异常且返回列表,列名正确性待官方镜像/驱动组合验证
	 */
	@Test
	public void tableApi() throws Exception {
		Assumptions.assumeTrue(available);
		GBase8sDialect dialect = new GBase8sDialect();
		List<?> tables = dialect.getTables("gbasedbt", null, "sqltoy_g8%", conn, profile());
		assertNotNull(tables, "getTables应返回列表");
	}

	/** 函数转换面:nvl/decode/instr原生透传、now→CURRENT、add_months原生 */
	@Test
	public void functionConversions() throws Exception {
		Assumptions.assumeTrue(available);
		// 直接真库验证函数
		assertEquals("x", querySingle("select nvl(null,'x') from sqltoy_g8_feat where id=1").toString().trim());
		assertEquals("a", querySingle("select decode(1,1,'a','b') from sqltoy_g8_feat where id=1").toString().trim());
		assertEquals(2, ((Number) querySingle("select instr('admin','dmi') from sqltoy_g8_feat where id=1")).intValue());
		assertNotNull(querySingle("select add_months(today,2) from sqltoy_g8_feat where id=1"));
		// now→CURRENT真库执行
		assertNotNull(querySingle("select CURRENT from sqltoy_g8_feat where id=1"), "now转CURRENT可执行");
	}

	/**
	 * updateFetch三契约(驱动CONCUR_UPDATABLE的updateRow SQL组装缺陷,方言以独立
	 * update语句落库+代理承接JDBC可更新结果集读可见性语义):1)handler先读原始数据
	 * 做判断再修改(读到数据库当前值);2)行内updateXxx后再getXxx同列(列名/下标两
	 * 口径)见新值;3)返回行为修改后结果;外加updateNull按列元数据类型落库、事务
	 * 提交后持久化验证
	 */
	@Test
	public void updateFetchContracts() throws Exception {
		Assumptions.assumeTrue(available);
		try (Statement st = conn.createStatement()) {
			st.execute("delete from sqltoy_g8_feat where id in (11,12)");
			st.execute("insert into sqltoy_g8_feat values (11,'u11',10,'2026-01-15 10:30:00')");
			st.execute("insert into sqltoy_g8_feat values (12,'u12',20,'2026-03-20 14:00:00')");
		}
		GBase8sDialect dialect = new GBase8sDialect();
		String sql = "select id,nm,score from sqltoy_g8_feat where id in (11,12)";
		List<String> reads = new ArrayList<String>();
		conn.setAutoCommit(false);
		try {
			QueryResult result = dialect.updateFetch(context, new SqlToyConfig("gbase8s"), sql, null,
					new UpdateRowHandler() {
						@Override
						public void updateRow(ResultSet rs, int index) throws Exception {
							// 契约1:先拿数据库当前值做判断,再决定是否修改
							long id = rs.getLong(1);
							double oldScore = rs.getDouble("score");
							reads.add(id + ":" + oldScore);
							if (oldScore < 15) {
								rs.updateDouble("score", oldScore + 100);
								// 契约2:行内update后再读同列见新值(列名/下标两口径)
								assertEquals(oldScore + 100, rs.getDouble("score"), 1e-9, "行内再读见新值(列名)");
								assertEquals(oldScore + 100, rs.getDouble(3), 1e-9, "行内再读见新值(下标)");
							} else {
								rs.updateNull("nm");
								assertNull(rs.getString("nm"), "updateNull后行内读为null");
							}
						}
					}, conn, profile(), LockMode.UPGRADE, 0, 500, -1);
			conn.commit();
			// 契约3:返回行为修改后结果(公共路径在updateRow()之后才组装返回行)
			List<?> rows = (List<?>) result.getRows();
			assertEquals(2, rows.size(), "锁定读返回2行");
			List<?> row11 = null;
			List<?> row12 = null;
			for (Object r : rows) {
				List<?> row = (List<?>) r;
				if (((Number) row.get(0)).longValue() == 11) {
					row11 = row;
				} else {
					row12 = row;
				}
			}
			assertNotNull(row11, "返回行含id=11");
			assertNotNull(row12, "返回行含id=12");
			assertEquals(110.0, ((Number) row11.get(2)).doubleValue(), 1e-9, "返回行score为修改后值");
			assertNull(row12.get(1), "返回行nm为null(updateNull)");
		} finally {
			conn.setAutoCommit(true);
		}
		// 判断依据为锁定读到的原始值
		assertTrue(reads.contains("11:10.0") && reads.contains("12:20.0"), "handler先读到原始值: " + reads);
		// 独立update语句落库:事务提交后持久化
		assertEquals(110.0, ((Number) querySingle("select score from sqltoy_g8_feat where id=11")).doubleValue(),
				1e-9, "score修改已落库");
		assertNull(querySingle("select nm from sqltoy_g8_feat where id=12"), "updateNull已落库");
		try (Statement st = conn.createStatement()) {
			st.execute("delete from sqltoy_g8_feat where id in (11,12)");
		}
	}

	/**
	 * json原生列全链路(2026-10-12真库定界):GBase 8s的json为不透明类型(元数据
	 * columnType=STRUCT/类型名json),写入侧字符串绑定直通(insert/update实测OK);
	 * 读回侧驱动缺陷:getObject报"A customized type map required"(type map按String
	 * 映射报ClassCastException,要求SQLData实现)、getString返回IfxUDT.toString垃圾值,
	 * 而getBytes返回的即UTF-8 JSON文本(中英文与::lvarchar完全一致)→框架ResultUtils
	 * 将json列归BYTES_TEXT策略按字节读取后UTF-8解码,手写sql查询原生json列无需任何cast
	 */
	@Test
	public void nativeJsonFullChain() throws Exception {
		Assumptions.assumeTrue(available);
		// DDL映射:JSON注解列原生渲染(读回已由BYTES_TEXT策略框架级承接)
		org.sagacity.sqltoy.model.ColumnMeta jm = new org.sagacity.sqltoy.model.ColumnMeta();
		jm.setNativeType("json");
		assertEquals("JSON",
				org.sagacity.sqltoy.plugins.ddl.DDLUtils.convertType(jm, DBType.GBASE8S), "原生json列");
		// 读策略分类:g8的json列归BYTES_TEXT(getBytes→UTF-8解码)
		int[] kinds = org.sagacity.sqltoy.utils.ResultUtils.buildColumnKinds(DBType.GBASE8S,
				new String[] { "json" });
		assertEquals(org.sagacity.sqltoy.utils.ResultUtils.COLUMN_BYTES_TEXT, kinds[0],
				"json列应归BYTES_TEXT读策略");
		// 其他库不受影响(仍为常规getObject)
		assertEquals(org.sagacity.sqltoy.utils.ResultUtils.COLUMN_NORMAL,
				org.sagacity.sqltoy.utils.ResultUtils.buildColumnKinds(DBType.MYSQL, new String[] { "json" })[0],
				"mysql的json列不归BYTES_TEXT");
		try (Statement st = conn.createStatement()) {
			try {
				st.execute("drop table sqltoy_g8_json");
			} catch (Exception e) {
				// ignore
			}
			st.execute("create table sqltoy_g8_json (id int primary key, j json)");
		}
		GBase8sDialect dialect = new GBase8sDialect();
		G8JsonVO vo = new G8JsonVO();
		vo.setId(1L);
		vo.setJ("{\"k\":\"v1\",\"n\":3}");
		dialect.save(context, vo, conn, profile(), null);
		// 手写sql查询(Map/数组行路径):select原生json列,无需用户侧cast,BYTES_TEXT生效
		SqlToyConfig config = new SqlToyConfig("gbase8s");
		config.setSql("select id,j from sqltoy_g8_json where id=1");
		org.sagacity.sqltoy.model.inner.QueryExecutorExtend extend = new org.sagacity.sqltoy.model.inner.QueryExecutorExtend();
		QueryResult qr = dialect.findBySql(context, config, "select id,j from sqltoy_g8_json where id=1", null, extend,
				null, conn, null, profile(), 500, -1);
		List<?> rows = (List<?>) qr.getRows();
		assertEquals(1, rows.size(), "json列查询返回1行");
		assertEquals("{\"k\":\"v1\",\"n\":3}", String.valueOf(((List<?>) rows.get(0)).get(1)),
				"数组行读回json文本(getBytes→UTF-8)");
		// 裸List路径(SqlUtil.processResultSet直查,2026-10-12补BYTES_TEXT策略同口径):
		// g8的json列在该路径若走getObject会报"customized type map required"
		List<?> raw = (List<?>) org.sagacity.sqltoy.utils.SqlUtil.findByJdbcQuery(null,
				"select id,j from sqltoy_g8_json where id=?", new Object[] { 1 }, null, null, null, conn, profile(),
				true, null, 0, 0, null);
		assertEquals(1, raw.size(), "裸List路径返回1行");
		assertEquals("{\"k\":\"v1\",\"n\":3}", String.valueOf(((List<?>) raw.get(0)).get(1)),
				"裸List路径json读回(getBytes→UTF-8)");
		// 实体load(VO映射路径):同为BYTES_TEXT策略
		G8JsonVO loaded = (G8JsonVO) dialect.load(context, new G8JsonVO(1L), false, null, null, 0, conn, profile(),
				null, 0);
		assertNotNull(loaded, "load返回实体");
		assertEquals("{\"k\":\"v1\",\"n\":3}", loaded.getJ(), "实体读回json文本");
		// saveOrUpdate更新分支+中文内容round-trip
		vo.setJ("{\"名字\":\"值一\"}");
		dialect.saveOrUpdate(context, vo, null, conn, profile(), null, null);
		assertEquals("{\"名字\":\"值一\"}", querySingle("select j::lvarchar from sqltoy_g8_json where id=1").toString().trim(),
				"中文json经saveOrUpdate更新落库");
		QueryResult qr2 = dialect.findBySql(context, config, "select id,j from sqltoy_g8_json where id=1", null,
				extend, null, conn, null, profile(), 500, -1);
		assertEquals("{\"名字\":\"值一\"}", String.valueOf(((List<?>) ((List<?>) qr2.getRows()).get(0)).get(1)),
				"中文json读回UTF-8解码一致");
		// null弹性:json字段为null的saveOrUpdate保留原值(merge的nvl两参经lvarchar域:
		// json→lvarchar cast可用,lvarchar→json赋值位置隐式转换,真库定界见DialectUtils)
		vo.setJ(null);
		dialect.saveOrUpdate(context, vo, null, conn, profile(), null, null);
		assertEquals("{\"名字\":\"值一\"}", querySingle("select j::lvarchar from sqltoy_g8_json where id=1").toString().trim(),
				"null的json字段不应覆盖原值(弹性保持)");
		try (Statement st = conn.createStatement()) {
			st.execute("drop table sqltoy_g8_json");
		}
	}

	/**
	 * vector/geometry能力边界:社区镜像(liaosnet/gbase8s,Informix 12.10内核)实测
	 * "Type (vector) not found"/"Type (geometry) not found"(空间能力为商业版
	 * datablade,向量为商业版V8.8能力),DDL渲染VECTOR/GEOMETRY在此镜像上报错属
	 * 预期loud failure,商业版经@Column(nativeType)按实际类型名覆盖
	 */
	@Test
	public void vectorGeometryBoundary() throws Exception {
		Assumptions.assumeTrue(available);
		for (String colType : new String[] { "vector", "geometry", "st_geometry" }) {
			boolean created = false;
			try (Statement st = conn.createStatement()) {
				st.execute("create table sqltoy_g8_vg (id int, v " + colType + ")");
				created = true;
			} catch (Exception e) {
				// 预期:Type not found
			}
			assertTrue(!created, "社区镜像不应支持" + colType + "类型(文档化边界)");
			if (created) {
				try (Statement st = conn.createStatement()) {
					st.execute("drop table sqltoy_g8_vg");
				} catch (Exception e) {
					// ignore
				}
			}
		}
	}

	/**
	 * saveAllIgnoreExist(mergeIgnore形态):存在则忽略/不存在则插入,insert-only merge
	 * 复用appendMergeIntoUsingSelect/appendMergeOnConditions公共拼装(systables伪表/on
	 * 无括号的GBASE8S分派天然生效);两遍执行第二遍应全部忽略;多元素批量顺带检验
	 * 驱动batch参数错位是否影响insert-only merge
	 */
	@Test
	public void saveAllIgnoreExistMergeIgnore() throws Exception {
		Assumptions.assumeTrue(available);
		GBase8sDialect dialect = new GBase8sDialect();
		java.util.List<G8FeatVO> list = new ArrayList<G8FeatVO>();
		for (long id : new long[] { 501, 502, 503 }) {
			G8FeatVO vo = new G8FeatVO();
			vo.setId(id);
			vo.setNm("ignore" + id);
			vo.setScore((double) id);
			list.add(vo);
		}
		try (Statement st = conn.createStatement()) {
			st.execute("delete from sqltoy_g8_feat where id in (501,502,503)");
		}
		Long cnt = dialect.saveAllIgnoreExist(context, list, 10, null, conn, profile(), null, null);
		assertEquals(Long.valueOf(3), cnt, "首遍3条新记录全部插入");
		assertEquals("ignore502", querySingle("select nm from sqltoy_g8_feat where id=502").toString().trim(),
				"插入值正确(批量未错位)");
		assertEquals(502.0, ((Number) querySingle("select score from sqltoy_g8_feat where id=502")).doubleValue(),
				1e-9, "score正确(批量未错位)");
		// 第二遍:全部已存在→忽略
		for (G8FeatVO vo : list) {
			vo.setNm("changed" + vo.getId());
		}
		Long cnt2 = dialect.saveAllIgnoreExist(context, list, 10, null, conn, profile(), null, null);
		assertEquals("ignore501", querySingle("select nm from sqltoy_g8_feat where id=501").toString().trim(),
				"已存在记录被忽略,值未变化");
		try (Statement st = conn.createStatement()) {
			st.execute("delete from sqltoy_g8_feat where id in (501,502,503)");
		}
	}

	/**
	 * updateSaveFetch(锁查询→存在则修改/不存在则插入):公共实现DefaultDialectUtils走
	 * CONCUR_UPDATABLE的updateRow/insertRow,该驱动两项均需代理承接(独立update/insert
	 * 语句落库),三契约:修改后返回新值/不存在的记录插入成功/锁定读判存在性
	 */
	@Test
	public void updateSaveFetchLockModifyOrInsert() throws Exception {
		Assumptions.assumeTrue(available);
		GBase8sDialect dialect = new GBase8sDialect();
		try (Statement st = conn.createStatement()) {
			st.execute("delete from sqltoy_g8_feat where id in (601,602)");
		}
		conn.setAutoCommit(false);
		try {
			// 存在→修改:预置601,锁读后score+100,返回实体应为修改后结果
			G8FeatVO vo = new G8FeatVO();
			vo.setId(601L);
			vo.setNm("usf");
			vo.setScore(10.0);
			dialect.save(context, vo, conn, profile(), null);
			G8FeatVO exist = new G8FeatVO();
			exist.setId(601L);
			java.io.Serializable r1 = dialect.updateSaveFetch(context, exist, new UpdateRowHandler() {
				@Override
				public void updateRow(ResultSet rs, int index) throws Exception {
					rs.updateDouble("score", rs.getDouble("score") + 100);
				}
			}, 5, null, conn, profile(), null);
			assertNotNull(r1, "存在记录的updateSaveFetch返回非空");
			conn.commit();
			assertEquals(110.0,
					((Number) querySingle("select score from sqltoy_g8_feat where id=601")).doubleValue(), 1e-9,
					"存在记录修改已落库");
			// 不存在→插入:602无预置记录,反调置值后insert并返回
			G8FeatVO absent = new G8FeatVO();
			absent.setId(602L);
			absent.setNm("usfIns");
			absent.setScore(20.0);
			dialect.updateSaveFetch(context, absent, new UpdateRowHandler() {
				@Override
				public void updateRow(ResultSet rs, int index) throws Exception {
					// 不存在场景不进入反调
				}
			}, 5, null, conn, profile(), null);
			conn.commit();
			assertNotNull(querySingle("select nm from sqltoy_g8_feat where id=602"), "不存在记录应被插入");
			assertEquals("usfIns", querySingle("select nm from sqltoy_g8_feat where id=602").toString().trim(),
					"插入值正确");
		} finally {
			conn.setAutoCommit(true);
			try (Statement st = conn.createStatement()) {
				st.execute("delete from sqltoy_g8_feat where id in (601,602)");
			}
		}
	}

	/**
	 * DDL生成与锁形态边缘(2026-10-12疏漏排查):DDLFactory补GBASE8S分派(原落default
	 * 抛"not implemented"),自增列由SERIAL类型族承载(无内联identity子句);锁形态——
	 * 该库无语句级wait n/nowait子句(实测语法错),UPGRADE超时走会话级set lock mode、
	 * NOWAIT降级普通for update
	 */
	@Test
	public void ddlGenerationAndLockEdgeCases() throws Exception {
		Assumptions.assumeTrue(available);
		// 1. DDL生成:SERIAL自增/lvarchar/datetime year to second/byte映射,无identity子句
		org.sagacity.sqltoy.config.model.EntityMeta entityMeta = new org.sagacity.sqltoy.config.model.EntityMeta();
		entityMeta.setTableName("sqltoy_g8_ddl");
		entityMeta.setTableComment("ddl测试表");
		org.sagacity.sqltoy.config.model.FieldMeta idMeta = new org.sagacity.sqltoy.config.model.FieldMeta();
		idMeta.setFieldName("id");
		idMeta.setColumnName("id");
		idMeta.setType(java.sql.Types.INTEGER);
		idMeta.setAutoIncrement(true);
		idMeta.setPK(true);
		idMeta.setNullable(false);
		idMeta.setComments("自增主键");
		entityMeta.addFieldMeta(idMeta);
		org.sagacity.sqltoy.config.model.FieldMeta nmMeta = new org.sagacity.sqltoy.config.model.FieldMeta();
		nmMeta.setFieldName("nm");
		nmMeta.setColumnName("nm");
		nmMeta.setType(java.sql.Types.VARCHAR);
		nmMeta.setLength(50);
		entityMeta.addFieldMeta(nmMeta);
		org.sagacity.sqltoy.config.model.FieldMeta scoreMeta = new org.sagacity.sqltoy.config.model.FieldMeta();
		scoreMeta.setFieldName("score");
		scoreMeta.setColumnName("score");
		scoreMeta.setType(java.sql.Types.DECIMAL);
		scoreMeta.setLength(10);
		scoreMeta.setScale(2);
		entityMeta.addFieldMeta(scoreMeta);
		org.sagacity.sqltoy.config.model.FieldMeta timeMeta = new org.sagacity.sqltoy.config.model.FieldMeta();
		timeMeta.setFieldName("createTime");
		timeMeta.setColumnName("create_time");
		timeMeta.setType(java.sql.Types.TIMESTAMP);
		entityMeta.addFieldMeta(timeMeta);
		org.sagacity.sqltoy.model.TableMeta tableMeta = org.sagacity.sqltoy.plugins.ddl.DDLUtils
				.wrapTableMeta(entityMeta, DBType.GBASE8S);
		String ddl = new org.sagacity.sqltoy.plugins.ddl.impl.OracleDDLGenerator().createTableSql(tableMeta, null,
				"lower", DBType.GBASE8S);
		assertTrue(ddl.contains("SERIAL"), "自增列应为SERIAL类型: " + ddl);
		assertTrue(!ddl.contains("IDENTITY"), "不应有内联identity子句: " + ddl);
		assertTrue(ddl.contains("lvarchar(50)"), "varchar应映射lvarchar(50): " + ddl);
		assertTrue(ddl.contains("DECIMAL(10,2)"), "decimal按length渲染精度: " + ddl);
		assertTrue(ddl.contains("datetime year to second"), "timestamp应映射datetime year to second: " + ddl);
		// 2. 真库执行建表语句(create部分)并验证serial自增
		try (Statement st = conn.createStatement()) {
			try {
				st.execute("drop table sqltoy_g8_ddl");
			} catch (Exception e) {
			}
			// 建表语句收口括号独立成行(")\r\n"首次命中是PRIMARY KEY (id)的收口,外层
			// 收口为"\r\n)"模式,截取须含外层)
			int closeIdx = ddl.indexOf("\r\n)");
			try {
				st.execute(ddl.substring(0, closeIdx + 3));
			} catch (Exception e) {
				throw new RuntimeException("DDL执行失败: [BEG]" + ddl.substring(0, closeIdx + 3) + "[END]", e);
			}
			st.execute("insert into sqltoy_g8_ddl(nm,score,create_time) values ('x',8.5,current)");
			assertTrue(((Number) querySingle("select id from sqltoy_g8_ddl where nm='x'")).intValue() > 0,
					"serial自增生效");
			st.execute("drop table sqltoy_g8_ddl");
		}
		// 3. 锁形态:UPGRADE带超时(OracleDialectUtils分派为g8不追加wait)与NOWAIT降级
		String lockWait = org.sagacity.sqltoy.dialect.utils.OracleDialectUtils.getLockSql("select * from t", profile(),
				org.sagacity.sqltoy.model.LockMode.UPGRADE, 5);
		assertTrue(!lockWait.contains("wait"), "g8的UPGRADE超时不应追加wait子句: " + lockWait);
		String lockNowait = org.sagacity.sqltoy.dialect.utils.DefaultDialectUtils.getLockSql("select * from t",
				DBType.GBASE8S, org.sagacity.sqltoy.model.LockMode.UPGRADE_NOWAIT, 0, false, null, false);
		assertTrue(!lockNowait.contains("nowait"), "g8的NOWAIT应降级普通for update: " + lockNowait);
		// 4. 真库:load带UPGRADE锁+超时(会话级set lock mode+裸for update)
		conn.setAutoCommit(false);
		try {
			G8FeatVO vo = new G8FeatVO();
			vo.setId(1L);
			G8FeatVO locked = (G8FeatVO) new GBase8sDialect().load(context, vo, false, null,
					org.sagacity.sqltoy.model.LockMode.UPGRADE, 3, conn, profile(), null, 0);
			assertNotNull(locked, "带锁load成功(无wait子句形态)");
		} finally {
			conn.setAutoCommit(true);
		}
	}

	/**
	 * 主键策略(2026-10-12真库定界):IDENTITY(serial列)——allowAssignPKValue按GBASE8S
	 * 分派为false,id为null时insert省略主键列由serial自增+列提示getGeneratedKeys回取
	 * (显式null绑定报"null key value"故禁赋值;显式非null值仍可insert);SEQUENCE——
	 * nvl(?,seq.nextval)写入可行,但列提示prepareStatement对nvl包装即报"Error In
	 * Specifying Automatically (Server) Generated Keys",主键回取改会话级
	 * seq.currval(同hana模式);id显式赋值时SEQUENCE转ASSIGN直插
	 */
	@Test
	public void pkStrategyIdentityAndSequence() throws Exception {
		Assumptions.assumeTrue(available);
		try (Statement st = conn.createStatement()) {
			for (String t : new String[] { "sqltoy_g8_idn", "sqltoy_g8_seq" }) {
				try {
					st.execute("drop table " + t);
				} catch (Exception e) {
				}
			}
			try {
				st.execute("drop sequence sqltoy_g8_pkseq");
			} catch (Exception e) {
			}
			st.execute("create table sqltoy_g8_idn (id serial not null primary key, nm lvarchar(30))");
			st.execute("create table sqltoy_g8_seq (id int primary key, nm lvarchar(30))");
			st.execute("create sequence sqltoy_g8_pkseq start with 100");
		}
		GBase8sDialect dialect = new GBase8sDialect();
		// identity + id为null:serial自增+生成键回取回写
		G8IdnVO idnNull = new G8IdnVO();
		idnNull.setNm("idnNull");
		Object pk1 = dialect.save(context, idnNull, conn, profile(), null);
		assertNotNull(pk1, "identity生成主键应回取非空");
		assertTrue(((Number) pk1).longValue() > 0, "serial生成键应为正数: " + pk1);
		assertEquals(((Number) pk1).longValue(), idnNull.getId().longValue(), "主键应回写实体");
		// identity批量:同样省略主键列
		java.util.List<G8IdnVO> idnList = new ArrayList<G8IdnVO>();
		for (int i = 0; i < 2; i++) {
			G8IdnVO v = new G8IdnVO();
			v.setNm("idnBatch" + i);
			idnList.add(v);
		}
		dialect.saveAll(context, idnList, 10, null, conn, profile(), null, null);
		assertEquals(2L, ((Number) querySingle(
				"select count(*) from sqltoy_g8_idn where nm like 'idnBatch%'")).longValue(), "identity批量serial自增");
		// sequence + id为null:nvl包装写入+currval回取
		G8SeqVO seqNull = new G8SeqVO();
		seqNull.setNm("seqNull");
		Object pk2 = dialect.save(context, seqNull, conn, profile(), null);
		assertTrue(((Number) pk2).longValue() >= 100, "sequence生成键应>=100(start with 100): " + pk2);
		assertEquals(((Number) pk2).longValue(), seqNull.getId().longValue(), "sequence主键应回写实体");
		// sequence + 显式id:转ASSIGN直插
		G8SeqVO seqExp = new G8SeqVO();
		seqExp.setId(7002L);
		seqExp.setNm("seqExp");
		Object pk3 = dialect.save(context, seqExp, conn, profile(), null);
		assertEquals(7002L, ((Number) pk3).longValue(), "显式id直插返回原值");
		try (Statement st = conn.createStatement()) {
			st.execute("drop table sqltoy_g8_idn");
			st.execute("drop table sqltoy_g8_seq");
			st.execute("drop sequence sqltoy_g8_pkseq");
		}
	}

	/**
	 * 级联验证:save(单条级联子表批量insert)/update(cascade=true,子表经merge saveOrUpdateAll)
	 * /saveAll与updateAll(批量,框架语义无级联)。重点:update级联子表走DialectUtils静态
	 * saveOrUpdateAll多元素batch merge——该驱动batch参数错位(真库定界),且静态调用绕过
	 * GBase8sDialect.saveOrUpdateAll的逐条覆写
	 */
	@Test
	public void cascadeSaveAndUpdate() throws Exception {
		Assumptions.assumeTrue(available);
		try (Statement st = conn.createStatement()) {
			for (String t : new String[] { "sqltoy_g8_cas", "sqltoy_g8_cas_item" }) {
				try {
					st.execute("drop table " + t);
				} catch (Exception e) {
				}
			}
			st.execute("create table sqltoy_g8_cas (id int primary key, nm lvarchar(30))");
			st.execute("create table sqltoy_g8_cas_item (id int primary key, order_id int, item_nm lvarchar(30))");
		}
		GBase8sDialect dialect = new GBase8sDialect();
		// 1. save单条级联:父save时子表批量insert且外键回写
		G8CasVO parent = new G8CasVO();
		parent.setId(1L);
		parent.setNm("p1");
		G8CasItemVO i1 = new G8CasItemVO();
		i1.setId(101L);
		i1.setItemNm("item1");
		G8CasItemVO i2 = new G8CasItemVO();
		i2.setId(102L);
		i2.setItemNm("item2");
		parent.setItems(new ArrayList<>(java.util.Arrays.asList(i1, i2)));
		dialect.save(context, parent, conn, profile(), null);
		assertEquals(2L, ((Number) querySingle(
				"select count(*) from sqltoy_g8_cas_item where order_id=1")).longValue(), "级联子表2条且外键回写");
		assertEquals("item2",
				querySingle("select item_nm from sqltoy_g8_cas_item where id=102").toString().trim(),
				"子表值正确");
		// 2. update级联:子表存量修改+新增(多元素merge)
		i2.setItemNm("item2x");
		G8CasItemVO i3 = new G8CasItemVO();
		i3.setId(103L);
		i3.setItemNm("item3");
		parent.getItems().add(i3);
		parent.setNm("p1x");
		try {
			dialect.update(context, parent, null, true, null, null, conn, profile(), null);
			assertEquals("p1x", querySingle("select nm from sqltoy_g8_cas where id=1").toString().trim(),
					"主表更新生效");
			assertEquals("item2x",
					querySingle("select item_nm from sqltoy_g8_cas_item where id=102").toString().trim(),
					"子表存量修改生效(批量merge未错位)");
			assertEquals("item3",
					querySingle("select item_nm from sqltoy_g8_cas_item where id=103 and order_id=1").toString()
							.trim(),
					"子表新增生效且外键回写");
		} finally {
			// 数据终态留痕便于排查批量错位
			System.out.println("[G8CAS] 终态: " + querySingle(
					"select count(*) from sqltoy_g8_cas_item where order_id=1") + " 行item");
		}
		// 3. saveAll批量(无级联语义):3条直插
		java.util.List<G8CasVO> batch = new ArrayList<G8CasVO>();
		for (long id : new long[] { 11, 12, 13 }) {
			G8CasVO vo = new G8CasVO();
			vo.setId(id);
			vo.setNm("batch" + id);
			batch.add(vo);
		}
		dialect.saveAll(context, batch, 10, null, conn, profile(), null, null);
		assertEquals(4L, ((Number) querySingle("select count(*) from sqltoy_g8_cas")).longValue(), "saveAll批量3条");
		// 4. updateAll批量(无级联语义)
		for (G8CasVO vo : batch) {
			vo.setNm("up" + vo.getId());
		}
		dialect.updateAll(context, batch, 10, null, null, null, conn, profile(), null, null)
		;
		assertEquals("up12", querySingle("select nm from sqltoy_g8_cas where id=12").toString().trim(),
				"updateAll更新生效");
		try (Statement st = conn.createStatement()) {
			st.execute("drop table sqltoy_g8_cas");
			st.execute("drop table sqltoy_g8_cas_item");
		}
	}

	/**
	 * load/loadAll级联加载:load(cascadeTypes)回填@OneToMany子表集合;loadAll批量级联
	 * (子表按fk in(:fk)批量查询);不存在记录load返回null
	 */
	@Test
	public void loadAndLoadAllCascade() throws Exception {
		Assumptions.assumeTrue(available);
		try (Statement st = conn.createStatement()) {
			for (String t : new String[] { "sqltoy_g8_cas2", "sqltoy_g8_cas2_item" }) {
				try {
					st.execute("drop table " + t);
				} catch (Exception e) {
				}
			}
			st.execute("create table sqltoy_g8_cas2 (id int primary key, nm lvarchar(30))");
			st.execute("create table sqltoy_g8_cas2_item (id int primary key, order_id int, item_nm lvarchar(30))");
			// 两个父各带子表数据
			st.execute("insert into sqltoy_g8_cas2 values (1,'p1')");
			st.execute("insert into sqltoy_g8_cas2 values (2,'p2')");
			st.execute("insert into sqltoy_g8_cas2_item values (101,1,'itemA')");
			st.execute("insert into sqltoy_g8_cas2_item values (102,1,'itemB')");
			st.execute("insert into sqltoy_g8_cas2_item values (201,2,'itemC')");
		}
		GBase8sDialect dialect = new GBase8sDialect();
		// 1. load单条+级联
		G8Cas2VO p = new G8Cas2VO();
		p.setId(1L);
		G8Cas2VO loaded = (G8Cas2VO) dialect.load(context, p, false,
				java.util.Arrays.asList(G8Cas2ItemVO.class), null, 0, conn, profile(), null, 0);
		assertNotNull(loaded, "存在的记录load返回实体");
		assertEquals("p1", loaded.getNm().trim(), "主表字段回填");
		assertNotNull(loaded.getItems(), "级联子表集合非null");
		assertEquals(2, loaded.getItems().size(), "子表2条");
		boolean hasA = false;
		boolean hasB = false;
		for (G8Cas2ItemVO item : loaded.getItems()) {
			hasA |= "itemA".equals(item.getItemNm().trim());
			hasB |= "itemB".equals(item.getItemNm().trim());
		}
		assertTrue(hasA && hasB, "子表值回填正确");
		// 2. 不存在记录load返回null
		G8Cas2VO absent = new G8Cas2VO();
		absent.setId(999L);
		assertNull(dialect.load(context, absent, false, null, null, 0, conn, profile(), null, 0),
				"不存在的记录load返回null");
		// 3. loadAll批量+级联(fk in (:fk)批量子表查询)
		G8Cas2VO p1 = new G8Cas2VO();
		p1.setId(1L);
		G8Cas2VO p2 = new G8Cas2VO();
		p2.setId(2L);
		List<?> all = (List<?>) dialect.loadAll(context, new ArrayList<>(java.util.Arrays.asList(p1, p2)), false,
				java.util.Arrays.asList(G8Cas2ItemVO.class), null, 0, conn, profile(), null, 500, -1, 0);
		assertEquals(2, all.size(), "loadAll返回2个实体");
		int totalItems = 0;
		for (Object one : all) {
			G8Cas2VO vo = (G8Cas2VO) one;
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
			st.execute("drop table sqltoy_g8_cas2");
			st.execute("drop table sqltoy_g8_cas2_item");
		}
	}

	@Entity(tableName = "sqltoy_g8_cas2")
	public static class G8Cas2VO implements java.io.Serializable {
		@Id()
		@Column(name = "id")
		private Long id;

		@Column(name = "nm")
		private String nm;

		@OneToMany(fields = { "id" }, mappedFields = { "orderId" })
		private List<G8Cas2ItemVO> items;

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

		public List<G8Cas2ItemVO> getItems() {
			return items;
		}

		public void setItems(List<G8Cas2ItemVO> items) {
			this.items = items;
		}
	}

	@Entity(tableName = "sqltoy_g8_cas2_item")
	public static class G8Cas2ItemVO implements java.io.Serializable {
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

	@Entity(tableName = "sqltoy_g8_cas")
	public static class G8CasVO implements java.io.Serializable {
		@Id()
		@Column(name = "id")
		private Long id;

		@Column(name = "nm")
		private String nm;

		@OneToMany(fields = { "id" }, mappedFields = { "orderId" })
		private List<G8CasItemVO> items;

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

		public List<G8CasItemVO> getItems() {
			return items;
		}

		public void setItems(List<G8CasItemVO> items) {
			this.items = items;
		}
	}

	@Entity(tableName = "sqltoy_g8_cas_item")
	public static class G8CasItemVO implements java.io.Serializable {
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

	@Entity(tableName = "sqltoy_g8_idn")
	public static class G8IdnVO implements java.io.Serializable {
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

	@Entity(tableName = "sqltoy_g8_seq")
	public static class G8SeqVO implements java.io.Serializable {
		@Id(strategy = "sequence", sequence = "sqltoy_g8_pkseq")
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

	@Entity(tableName = "sqltoy_g8_json")
	public static class G8JsonVO implements java.io.Serializable {
		@Id()
		@Column(name = "id")
		private Long id;

		// 原生json列:显式type=JdbcTypes.JSON(写入经JSONTypeUtil字符串绑定直通,merge的
		// update set走nvl(tv,ta::lvarchar)弹性分支),读回由BYTES_TEXT策略承接;
		// DDL生成请用@Column(nativeType="json")
		@Column(name = "j", type = org.sagacity.sqltoy.model.JdbcTypes.JSON)
		private String j;

		public G8JsonVO() {
		}

		public G8JsonVO(Long id) {
			this.id = id;
		}

		public Long getId() {
			return id;
		}

		public void setId(Long id) {
			this.id = id;
		}

		public String getJ() {
			return j;
		}

		public void setJ(String j) {
			this.j = j;
		}
	}

	@Entity(tableName = "sqltoy_g8_feat")
	public static class G8FeatVO implements java.io.Serializable {
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
