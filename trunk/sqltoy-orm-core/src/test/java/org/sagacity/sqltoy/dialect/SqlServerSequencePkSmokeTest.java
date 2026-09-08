package org.sagacity.sqltoy.dialect;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import org.sagacity.sqltoy.utils.BeanUtil;
import org.sagacity.sqltoy.utils.DataSourceUtils;

/**
 * SQLServer sequence主键save真实库冒烟:
 * 验证主键策略为sequence时,save通过NEXT VALUE FOR序列取值、插入并回写实体主键。
 * (表/序列由用例防御式创建并清理;实体主键Long,序列返回numeric验证类型回写)
 */
public class SqlServerSequencePkSmokeTest {

	private static Connection conn;

	private static SqlToyContext context;

	private static boolean available = false;

	@Entity(tableName = "sqltoy_probe_seq_t")
	public static class SeqProbeVO implements java.io.Serializable {
		@Id(strategy = "sequence", sequence = "sqltoy_probe_seq")
		@Column(name = "id")
		private Long id;

		@Column(name = "name")
		private String name;

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
			// sqlserver2016+支持DROP IF EXISTS,防御式清理规避重复运行时已存在报错
			st.execute("drop table if exists sqltoy_probe_seq_t");
			st.execute("drop sequence if exists sqltoy_probe_seq");
			st.execute("create sequence sqltoy_probe_seq start with 100 increment by 1");
			st.execute("create table sqltoy_probe_seq_t (id numeric(20,0) primary key, name varchar(100))");
		}
		FunctionUtils.setFunctionConverts(java.util.Arrays.asList("default"));
		available = true;
	}

	@AfterAll
	public static void destroy() throws Exception {
		if (conn != null) {
			try (Statement st = conn.createStatement()) {
				st.execute("drop table if exists sqltoy_probe_seq_t");
				st.execute("drop sequence if exists sqltoy_probe_seq");
			} catch (Exception e) {
				// ignore
			}
			conn.close();
		}
	}

	/** 原始批次形态:sqltoy生成的DECLARE+insert+select在真实sqlserver上执行并返回序列值 */
	@Test
	public void rawSequenceBatch() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeTrue(available, "sqlserver探针环境不可用,跳过");
		String batch = "set nocount on DECLARE @mySeqVariable as numeric(20)=NEXT VALUE FOR sqltoy_probe_seq "
				+ "insert into sqltoy_probe_seq_t (id,name) values (@mySeqVariable,?) select @mySeqVariable";
		try (PreparedStatement pst = conn.prepareStatement(batch)) {
			pst.setString(1, "admin");
			ResultSet rs = pst.executeQuery();
			rs.next();
			long seqVal = rs.getLong(1);
			assertTrue(seqVal >= 100, "应返回序列值: " + seqVal);
		}
		// 表中应存在name=admin且id>=100的行
		try (PreparedStatement pst = conn.prepareStatement(
				"select count(1) from sqltoy_probe_seq_t where name='admin' and id >= 100")) {
			ResultSet rs = pst.executeQuery();
			rs.next();
			assertEquals(1, rs.getInt(1), "批次插入应成功");
		}
	}

	/** sqltoy save全链路:sequence主键插入并回写实体主键 */
	@Test
	public void saveSequencePkWriteBack() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeTrue(available, "sqlserver探针环境不可用,跳过");
		SqlToyContext context = new SqlToyContext();
		SeqProbeVO vo = new SeqProbeVO();
		vo.setName("seqadmin");
		Object result = new SqlServerDialect().save(context, vo, conn, DataSourceUtils.DBType.SQLSERVER, "sqlserver", null);
		assertNotNull(result, "save应返回主键值");
		assertTrue(((Number) result).longValue() >= 100, "返回主键应为序列值: " + result);
		// 验证库中行存在
		try (PreparedStatement pst = conn.prepareStatement(
				"select name from sqltoy_probe_seq_t where id=?")) {
			pst.setLong(1, ((Number) result).longValue());
			ResultSet rs = pst.executeQuery();
			rs.next();
			assertEquals("seqadmin", rs.getString(1), "save应插入正确数据");
		}
	}

	/** BeanUtil对序列值(BigDecimal)回写Long主键的类型转换 */
	@Test
	public void beanUtilBigDecimalToLong() throws Exception {
		SeqProbeVO vo = new SeqProbeVO();
		BeanUtil.setProperty(vo, "id", new java.math.BigDecimal(123));
		assertNotNull(vo.getId(), "BigDecimal应成功写入Long字段");
		assertEquals(123L, vo.getId().longValue(), "BigDecimal→Long值应正确");
	}
}
