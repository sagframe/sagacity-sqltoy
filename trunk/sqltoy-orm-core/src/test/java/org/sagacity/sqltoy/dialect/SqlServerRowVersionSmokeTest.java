package org.sagacity.sqltoy.dialect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Properties;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.annotation.Column;
import org.sagacity.sqltoy.config.annotation.Entity;
import org.sagacity.sqltoy.config.annotation.Id;
import org.sagacity.sqltoy.dialect.impl.SqlServerDialect;
import org.sagacity.sqltoy.plugins.function.FunctionUtils;
import org.sagacity.sqltoy.utils.DataSourceUtils;

/**
 * SQLServer rowversion判据修复真实库冒烟(update 2026-9-5):
 * 原判据按实体侧type==TIMESTAMP识别rowversion,oracle等项目迁移场景(quickvo生成实体带显式
 * type=TIMESTAMP,sqlserver表列实为datetime2)业务时间列被静默跳过丢失;现判据按目标库元数据
 * TYPE_NAME=='timestamp'校准(EntityMeta.rowVersionColumns)。
 * 覆盖:迁移场景datetime2业务列正常写入、真rowversion列save/update/saveOrUpdateAll排除与参数对齐、
 * ensureRowVersionMeta元数据校准集合内容。
 * 连接配置从target/sqlserver-probe.properties读取;探针表测完即删。
 */
public class SqlServerRowVersionSmokeTest {

	private static Connection conn;

	private static boolean available = false;

	/** 迁移模拟实体:ver_time带显式type=TIMESTAMP(oracle quickvo生成形态),sqlserver表列实为datetime2 */
	@Entity(tableName = "sqltoy_probe_rv_mig")
	public static class MigProbeVO implements java.io.Serializable {
		@Id
		@Column(name = "id")
		private Long id;

		@Column(name = "name")
		private String name;

		@Column(name = "ver_time", type = java.sql.Types.TIMESTAMP)
		private LocalDateTime verTime;

		public Long getId() {
			return id;
		}

		public void setId(Long id) {
			this.id = id;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public LocalDateTime getVerTime() {
			return verTime;
		}

		public void setVerTime(LocalDateTime verTime) {
			this.verTime = verTime;
		}
	}

	/** rowversion实体:rv列映射timestamp(rowversion),数据库自动维护不可显式写入 */
	@Entity(tableName = "sqltoy_probe_rv_t")
	public static class RvProbeVO implements java.io.Serializable {
		@Id
		@Column(name = "id")
		private Long id;

		@Column(name = "name")
		private String name;

		@Column(name = "rv")
		private byte[] rv;

		public Long getId() {
			return id;
		}

		public void setId(Long id) {
			this.id = id;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public byte[] getRv() {
			return rv;
		}

		public void setRv(byte[] rv) {
			this.rv = rv;
		}
	}

	/** 级联主表:OneToMany关联含迁移形态时间列的子表 */
	@Entity(tableName = "sqltoy_probe_rv_main")
	public static class CascadeMainVO implements java.io.Serializable {
		@Id
		@Column(name = "id")
		private Long id;

		@Column(name = "name")
		private String name;

		@org.sagacity.sqltoy.config.annotation.OneToMany(fields = { "id" }, mappedFields = { "main_id" })
		private java.util.List<CascadeSubVO> subs;

		public Long getId() {
			return id;
		}

		public void setId(Long id) {
			this.id = id;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public java.util.List<CascadeSubVO> getSubs() {
			return subs;
		}

		public void setSubs(java.util.List<CascadeSubVO> subs) {
			this.subs = subs;
		}
	}

	/** 级联子表:ver_time带显式type=TIMESTAMP(oracle quickvo生成形态),sqlserver表列实为datetime2 */
	@Entity(tableName = "sqltoy_probe_rv_sub")
	public static class CascadeSubVO implements java.io.Serializable {
		@Id
		@Column(name = "id")
		private Long id;

		@Column(name = "main_id")
		private Long mainId;

		@Column(name = "name")
		private String name;

		@Column(name = "ver_time", type = java.sql.Types.TIMESTAMP)
		private LocalDateTime verTime;

		public Long getId() {
			return id;
		}

		public void setId(Long id) {
			this.id = id;
		}

		public Long getMainId() {
			return mainId;
		}

		public void setMainId(Long mainId) {
			this.mainId = mainId;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public LocalDateTime getVerTime() {
			return verTime;
		}

		public void setVerTime(LocalDateTime verTime) {
			this.verTime = verTime;
		}
	}

	@BeforeAll
	public static void init() throws Exception {
		File cfg = new File("target/sqlserver-probe.properties");
		if (!cfg.exists()) {
			return;
		}
		Properties p = new Properties();
		try (InputStream in = new FileInputStream(cfg)) {
			p.load(in);
		}
		Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
		conn = DriverManager.getConnection(p.getProperty("url"), p.getProperty("username"), p.getProperty("password"));
		try (Statement st = conn.createStatement()) {
			st.execute("drop table if exists sqltoy_probe_rv_mig");
			st.execute("drop table if exists sqltoy_probe_rv_t");
			st.execute("drop table if exists sqltoy_probe_rv_main");
			st.execute("drop table if exists sqltoy_probe_rv_sub");
			// 迁移模拟:业务时间列datetime2(非rowversion)
			st.execute("create table sqltoy_probe_rv_mig (id numeric(12,0) primary key, name varchar(50), "
					+ "ver_time datetime2)");
			// rowversion正向:rv列为timestamp(rowversion)类型
			st.execute("create table sqltoy_probe_rv_t (id numeric(12,0) primary key, name varchar(50), "
					+ "rv rowversion)");
			// 级联主子表:子表业务时间列datetime2
			st.execute("create table sqltoy_probe_rv_main (id numeric(12,0) primary key, name varchar(50))");
			st.execute("create table sqltoy_probe_rv_sub (id numeric(12,0) primary key, main_id numeric(12,0), "
					+ "name varchar(50), ver_time datetime2)");
		}
		FunctionUtils.setFunctionConverts(java.util.Arrays.asList("default"));
		available = true;
	}

	@AfterAll
	public static void destroy() throws Exception {
		if (conn != null) {
			try (Statement st = conn.createStatement()) {
				st.execute("drop table if exists sqltoy_probe_rv_mig");
				st.execute("drop table if exists sqltoy_probe_rv_t");
				st.execute("drop table if exists sqltoy_probe_rv_main");
				st.execute("drop table if exists sqltoy_probe_rv_sub");
			} catch (Exception e) {
				// ignore
			}
			conn.close();
		}
	}

	@Test
	public void sqlserverAvailable() {
		org.junit.jupiter.api.Assumptions.assumeTrue(available, "sqlserver探针环境不可用,跳过");
	}

	/**
	 * 迁移场景:实体显式type=TIMESTAMP的业务时间列(sqlserver表列实为datetime2)必须正常写入,
	 * 修复前被误判为rowversion静默跳过(load回读null)
	 */
	@Test
	public void migrateDateTime2ColumnNotSkipped() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeTrue(available, "sqlserver探针环境不可用,跳过");
		SqlToyContext context = new SqlToyContext();
		MigProbeVO vo = new MigProbeVO();
		vo.setId(1001L);
		vo.setName("mig");
		vo.setVerTime(LocalDateTime.of(2026, 2, 1, 8, 0, 0));
		new SqlServerDialect().save(context, vo, conn, DataSourceUtils.DBType.SQLSERVER, "sqlserver", null);
		try (PreparedStatement pst = conn.prepareStatement("select ver_time from sqltoy_probe_rv_mig where id=1001")) {
			ResultSet rs = pst.executeQuery();
			rs.next();
			assertNotNull(rs.getTimestamp(1), "type=TIMESTAMP的datetime2业务列应正常写入(oracle迁移场景)");
			assertEquals("2026-02-01 08:00:00.0", rs.getTimestamp(1).toString(), "写入值应正确");
		}
		// update路径同样不误伤
		vo.setName("mig2");
		new SqlServerDialect().update(context, vo, null, false, null, null, conn,
				DataSourceUtils.DBType.SQLSERVER, "sqlserver", null);
		try (PreparedStatement pst = conn.prepareStatement(
				"select name,ver_time from sqltoy_probe_rv_mig where id=1001")) {
			ResultSet rs = pst.executeQuery();
			rs.next();
			assertEquals("mig2", rs.getString(1), "update应生效");
			assertNotNull(rs.getTimestamp(2), "update不应清空datetime2业务列");
		}
	}

	/** rowversion正向:save/update/saveOrUpdateAll/saveAllIgnoreExist对rowversion列排除且参数对齐,rv由数据库自动生成 */
	@Test
	public void rowVersionColumnExcludedAndAligned() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeTrue(available, "sqlserver探针环境不可用,跳过");
		SqlToyContext context = new SqlToyContext();
		SqlServerDialect dialect = new SqlServerDialect();
		RvProbeVO vo = new RvProbeVO();
		vo.setId(2001L);
		vo.setName("rv");
		// save:insert排除rv列(显式插入rowversion会报错)
		dialect.save(context, vo, conn, DataSourceUtils.DBType.SQLSERVER, "sqlserver", null);
		// update:set排除rv列,绑定参数对齐
		vo.setName("rv2");
		dialect.update(context, vo, null, false, null, null, conn, DataSourceUtils.DBType.SQLSERVER, "sqlserver",
				null);
		// saveOrUpdateAll:merge排除rv列
		dialect.saveOrUpdateAll(context, java.util.Arrays.asList(vo), 10, null, null, conn,
				DataSourceUtils.DBType.SQLSERVER, "sqlserver", null, null);
		try (PreparedStatement pst = conn.prepareStatement("select name,rv from sqltoy_probe_rv_t where id=2001")) {
			ResultSet rs = pst.executeQuery();
			rs.next();
			assertEquals("rv2", rs.getString(1), "save/update/saveOrUpdateAll应全部成功");
			assertNotNull(rs.getBytes(2), "rowversion应由数据库自动生成");
		}
		// saveAllIgnoreExist:update 2026-9-6 补该路径的rowversion排除与参数对齐验证
		RvProbeVO exist = new RvProbeVO();
		exist.setId(2001L);
		exist.setName("rv2");
		RvProbeVO fresh = new RvProbeVO();
		fresh.setId(2002L);
		fresh.setName("rv3");
		dialect.saveAllIgnoreExist(context, java.util.Arrays.asList(exist, fresh), 10, null, conn,
				DataSourceUtils.DBType.SQLSERVER, "sqlserver", null, null);
		try (PreparedStatement pst = conn.prepareStatement(
				"select name from sqltoy_probe_rv_t where id in (2001,2002) order by id")) {
			ResultSet rs = pst.executeQuery();
			rs.next();
			assertEquals("rv2", rs.getString(1), "已存在行不被覆盖");
			rs.next();
			assertEquals("rv3", rs.getString(1), "saveAllIgnoreExist新行插入(rowversion列排除且参数对齐)");
		}
	}

	/** ensureRowVersionMeta元数据校准:datetime2表校准集为空,rowversion表校准集含RV列 */
	@Test
	public void rowVersionMetaCalibration() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeTrue(available, "sqlserver探针环境不可用,跳过");
		SqlToyContext context = new SqlToyContext();
		org.sagacity.sqltoy.dialect.utils.SqlServerDialectUtils.ensureRowVersionMeta(context, conn,
				DataSourceUtils.DBType.SQLSERVER, null, MigProbeVO.class);
		org.sagacity.sqltoy.config.model.EntityMeta migMeta = context.getEntityMeta(MigProbeVO.class);
		assertNotNull(migMeta.getRowVersionColumns(), "迁移表应完成校准");
		assertFalse(migMeta.getRowVersionColumns().contains("ver_time"), "datetime2列不应进入rowversion集合");
		assertTrue(migMeta.isRowVersionField(migMeta.getFieldMeta("verTime")) == false,
				"显式type=TIMESTAMP的datetime2字段不应被判定为rowversion");

		org.sagacity.sqltoy.dialect.utils.SqlServerDialectUtils.ensureRowVersionMeta(context, conn,
				DataSourceUtils.DBType.SQLSERVER, null, RvProbeVO.class);
		org.sagacity.sqltoy.config.model.EntityMeta rvMeta = context.getEntityMeta(RvProbeVO.class);
		assertNotNull(rvMeta.getRowVersionColumns(), "rowversion表应完成校准");
		assertTrue(rvMeta.getRowVersionColumns().contains("rv"), "rv列应进入rowversion集合: "
				+ rvMeta.getRowVersionColumns());
		assertTrue(rvMeta.isRowVersionField(rvMeta.getFieldMeta("rv")), "rv字段应被判定为rowversion");
	}

	/**
	 * 级联子表:主表OneToMany级联save/saveOrUpdate,子表含显式type=TIMESTAMP的datetime2业务列,
	 * 级联路径绕过主表入口的ensure(修复前子表meta未校准回退旧判据,子表业务时间列静默丢失)
	 */
	@Test
	public void cascadeSubTableDateTime2NotSkipped() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeTrue(available, "sqlserver探针环境不可用,跳过");
		SqlToyContext context = new SqlToyContext();
		SqlServerDialect dialect = new SqlServerDialect();
		CascadeMainVO main = new CascadeMainVO();
		main.setId(6001L);
		main.setName("main");
		CascadeSubVO sub = new CascadeSubVO();
		sub.setId(6101L);
		sub.setName("sub");
		sub.setVerTime(LocalDateTime.of(2026, 3, 1, 9, 0, 0));
		main.setSubs(java.util.Arrays.asList(sub));
		// 级联save:主表插入+子表批量保存
		dialect.save(context, main, conn, DataSourceUtils.DBType.SQLSERVER, "sqlserver", null);
		try (PreparedStatement pst = conn.prepareStatement(
				"select main_id,ver_time from sqltoy_probe_rv_sub where id=6101")) {
			ResultSet rs = pst.executeQuery();
			rs.next();
			assertEquals(6001L, rs.getLong(1), "级联应回写关联字段");
			assertNotNull(rs.getTimestamp(2), "级联子表datetime2业务列应正常写入(修复前被误判rowversion丢失)");
		}
		// 级联update(cascade=true):主表update+子表saveOrUpdate(update路径的子表级联)
		sub.setName("sub2");
		dialect.update(context, main, null, true, null, null, conn, DataSourceUtils.DBType.SQLSERVER, "sqlserver",
				null);
		try (PreparedStatement pst = conn.prepareStatement(
				"select name,ver_time from sqltoy_probe_rv_sub where id=6101")) {
			ResultSet rs = pst.executeQuery();
			rs.next();
			assertEquals("sub2", rs.getString(1), "级联saveOrUpdate子表应更新");
			assertNotNull(rs.getTimestamp(2), "级联saveOrUpdate不应清空子表业务时间列");
		}
	}

	/** saveAll批量:迁移形态(参数与datetime2占位符对齐)与rowversion表(批量排除rv列)双场景 */
	@Test
	public void saveAllBatchAlignment() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeTrue(available, "sqlserver探针环境不可用,跳过");
		SqlToyContext context = new SqlToyContext();
		SqlServerDialect dialect = new SqlServerDialect();
		// 迁移形态批量:多行datetime2列全部写入(修复前batchUpdateForPOJO按类型跳过TIMESTAMP参数致占位符缺参)
		MigProbeVO m1 = new MigProbeVO();
		m1.setId(7001L);
		m1.setName("m1");
		m1.setVerTime(LocalDateTime.of(2026, 4, 1, 10, 0, 0));
		MigProbeVO m2 = new MigProbeVO();
		m2.setId(7002L);
		m2.setName("m2");
		m2.setVerTime(LocalDateTime.of(2026, 4, 2, 11, 0, 0));
		dialect.saveAll(context, java.util.Arrays.asList(m1, m2), 10, null, conn,
				DataSourceUtils.DBType.SQLSERVER, "sqlserver", null, null);
		try (PreparedStatement pst = conn.prepareStatement(
				"select count(1) from sqltoy_probe_rv_mig where id in (7001,7002) and ver_time is not null")) {
			ResultSet rs = pst.executeQuery();
			rs.next();
			assertEquals(2, rs.getInt(1), "saveAll批量datetime2业务列应全部写入");
		}
		// rowversion表批量:insert排除rv列,批量参数对齐不报错
		RvProbeVO r1 = new RvProbeVO();
		r1.setId(7101L);
		r1.setName("r1");
		RvProbeVO r2 = new RvProbeVO();
		r2.setId(7102L);
		r2.setName("r2");
		dialect.saveAll(context, java.util.Arrays.asList(r1, r2), 10, null, conn,
				DataSourceUtils.DBType.SQLSERVER, "sqlserver", null, null);
		try (PreparedStatement pst = conn.prepareStatement(
				"select count(1) from sqltoy_probe_rv_t where id in (7101,7102) and rv is not null")) {
			ResultSet rs = pst.executeQuery();
			rs.next();
			assertEquals(2, rs.getInt(1), "saveAll批量rowversion表应排除rv列且行自动生成rv值");
		}
	}
}
