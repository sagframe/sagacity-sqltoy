package org.sagacity.sqltoy.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.Serializable;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.annotation.Column;
import org.sagacity.sqltoy.config.annotation.Entity;
import org.sagacity.sqltoy.config.annotation.Id;
import org.sagacity.sqltoy.dao.impl.DefaultLightDaoImpl;

/**
 * 单实体对象化 updateFetch：基于实体对象锁查询并修改，只更新，不存在不插入。
 */
@DisplayName("updateFetch单实体对象回调H2测试")
public class UpdateFetchEntityH2Test {

	private static final String URL = "jdbc:h2:mem:updatefetch_entity;DB_CLOSE_DELAY=-1";

	private static SqlToyContext context;

	private static LightDao lightDao;

	@Entity(tableName = "update_fetch_stock")
	public static class Stock implements Serializable {

		private static final long serialVersionUID = 1L;

		@Id
		@Column(name = "STOCK_ID")
		private String stockId;

		@Column(name = "STOCK_CODE")
		private String stockCode;

		@Column(name = "STOCK_NUM")
		private Integer stockNum;

		@Column(name = "STATUS")
		private Integer status;

		public String getStockId() {
			return stockId;
		}

		public void setStockId(String stockId) {
			this.stockId = stockId;
		}

		public String getStockCode() {
			return stockCode;
		}

		public void setStockCode(String stockCode) {
			this.stockCode = stockCode;
		}

		public Integer getStockNum() {
			return stockNum;
		}

		public void setStockNum(Integer stockNum) {
			this.stockNum = stockNum;
		}

		public Integer getStatus() {
			return status;
		}

		public void setStatus(Integer status) {
			this.status = status;
		}
	}

	@BeforeAll
	public static void init() throws Exception {
		try (Connection conn = DriverManager.getConnection(URL, "sa", "");
				Statement st = conn.createStatement()) {
			st.execute("drop table if exists update_fetch_stock");
			st.execute(
					"create table update_fetch_stock (stock_id varchar(32) primary key, stock_code varchar(32) unique, stock_num int, status int)");
		}
		JdbcDataSource dataSource = new JdbcDataSource();
		dataSource.setURL(URL);
		dataSource.setUser("sa");
		context = new SqlToyContext();
		context.setDefaultDataSource(dataSource);
		context.initialize();
		lightDao = new DefaultLightDaoImpl(context);
	}

	@BeforeEach
	public void resetData() throws Exception {
		try (Connection conn = DriverManager.getConnection(URL, "sa", "");
				Statement st = conn.createStatement()) {
			st.execute("delete from update_fetch_stock");
			st.execute("insert into update_fetch_stock values ('S1','C1',100,1)");
			st.execute("insert into update_fetch_stock values ('S2','C2',200,1)");
		}
	}

	@Test
	@DisplayName("实体对象锁查询后修改:只更新且返回修改结果")
	public void updateExistingEntityByPrimaryKey() {
		Stock query = new Stock();
		query.setStockId("S1");
		Stock result = lightDao.updateFetch(query, (stock, rowIndex) -> {
			assertEquals(100, stock.getStockNum().intValue());
			stock.setStockNum(stock.getStockNum() - 10);
		});
		assertNotNull(result);
		assertEquals("S1", result.getStockId());
		assertEquals(90, result.getStockNum().intValue());
		assertEquals(90, query.getStockNum().intValue());
	}

	@Test
	@DisplayName("按uniqueProps定位:属性名与列名不一致时仍按实体映射更新")
	public void updateByUniqueProps() {
		Stock query = new Stock();
		query.setStockCode("C2");
		Stock result = lightDao.updateFetch(query, (stock, rowIndex) -> {
			assertEquals("S2", stock.getStockId());
			assertEquals(200, stock.getStockNum().intValue());
			stock.setStockNum(150);
			stock.setStatus(0);
		}, "stockCode");
		assertNotNull(result);
		assertEquals("S2", result.getStockId());
		assertEquals(150, result.getStockNum().intValue());
		assertEquals(0, result.getStatus().intValue());
	}

	@Test
	@DisplayName("记录不存在:不执行insert并返回null")
	public void missingRecordDoesNotInsert() throws Exception {
		Stock query = new Stock();
		query.setStockCode("NOT_EXIST");
		Stock result = lightDao.updateFetch(query, (stock, rowIndex) -> stock.setStockNum(1), "stockCode");
		assertNull(result);
		assertEquals(0, countByCode("NOT_EXIST"));
		assertEquals(2, countByCode("C1") + countByCode("C2"));
	}

	@Test
	@DisplayName("updateSaveFetch插入语义不受影响")
	public void updateSaveFetchStillInsertWhenMissing() throws Exception {
		Stock query = new Stock();
		query.setStockId("S3");
		query.setStockCode("C3");
		query.setStockNum(300);
		query.setStatus(1);
		Stock result = lightDao.updateSaveFetch(query, (stock, rowIndex) -> stock.setStockNum(310), "stockCode");
		assertNotNull(result);
		assertEquals(1, countByCode("C3"));
		// updateSaveFetch 的插入分支直接使用实体值，不执行回调
		assertEquals(300, result.getStockNum().intValue());
	}

	private long countByCode(String code) throws Exception {
		try (Connection conn = DriverManager.getConnection(URL, "sa", "");
				Statement st = conn.createStatement();
				java.sql.ResultSet rs = st
						.executeQuery("select count(*) from update_fetch_stock where stock_code='" + code + "'")) {
			rs.next();
			return rs.getLong(1);
		}
	}

	/**
	 * update 2026-11-22 复查修复回归锁死:定位值为空时,updateFetch 必须视为"记录不存在"直接返回null;
	 * 此前空值一路走到锁查询SQL,被wrapFetchSql渲染成"col is null",从而命中业务键为NULL的无关行并对
	 * 其执行更新(H2实测改写了stock_code IS NULL那一行)
	 */
	@Test
	@DisplayName("定位值为空:返回null且不得按 is null 命中无关行")
	public void blankLocatorTreatedAsNotFound() throws Exception {
		try (Connection conn = DriverManager.getConnection(URL, "sa", ""); Statement st = conn.createStatement()) {
			st.execute("insert into update_fetch_stock values ('S9',null,900,1)");
		}
		try {
			Stock query = new Stock();
			query.setStockCode(null);
			Stock result = lightDao.updateFetch(query, (stock, rowIndex) -> stock.setStockNum(-1), "stockCode");
			assertNull(result, "空定位值须返回null(记录不存在)");
			assertEquals(900, stockNumOfNullCode(), "stock_code IS NULL 的行不得被修改");
		} finally {
			try (Connection conn = DriverManager.getConnection(URL, "sa", "");
					Statement st = conn.createStatement()) {
				st.execute("delete from update_fetch_stock where stock_id='S9'");
			}
		}
	}

	private int stockNumOfNullCode() throws Exception {
		try (Connection conn = DriverManager.getConnection(URL, "sa", "");
				Statement st = conn.createStatement();
				java.sql.ResultSet rs = st
						.executeQuery("select stock_num from update_fetch_stock where stock_code is null")) {
			rs.next();
			return rs.getInt(1);
		}
	}
}
