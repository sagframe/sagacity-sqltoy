/**
 * 
 */
package com.sagframe.sqltoy.showcase;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Resource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.sagacity.sqltoy.dao.SqlToyLazyDao;
import org.sagacity.sqltoy.model.PaginationModel;
import org.sagacity.sqltoy.service.SqlToyCRUDService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.alibaba.fastjson.JSON;
import com.sagframe.sqltoy.SqlToyApplication;
import com.sagframe.sqltoy.showcase.vo.OrderInfo3VO;
import com.sagframe.sqltoy.showcase.vo.OrderInfoVO;

/**
 * @project sqltoy-boot-showcase
 * @description 基于clickhouse的功能验证
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:ClickHouseTest.java,Revision:v1.0,Date:2020年1月22日
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = SqlToyApplication.class)
public class ClickHouseTest {
	@Autowired
	private SqlToyCRUDService sqlToyCRUDService;
	@Resource(name = "sqlToyLazyDao")
	private SqlToyLazyDao sqlToyLazyDao;

	@Test
	public void testInsert() {
		OrderInfo3VO vo = new OrderInfo3VO();
		vo.setOrderId("S001");
		vo.setOrderDate(LocalDate.now());
		vo.setBuyer("P001");
		vo.setOrderType("PO");
		vo.setSaler("P002");
		vo.setPrice(1000f);
		vo.setQuantity(10f);
		vo.setTotalAmt(10000f);
		vo.setStatus("1");

		sqlToyCRUDService.save(vo);
	}

	@Test
	public void testBatchInsert() {
		List<OrderInfo3VO> orders = new ArrayList<OrderInfo3VO>();
		OrderInfo3VO vo = new OrderInfo3VO();
		vo.setOrderId("S002");
		vo.setOrderDate(LocalDate.now());
		vo.setBuyer("P001");
		vo.setOrderType("PO");
		vo.setSaler("P002");
		vo.setPrice(1000f);
		vo.setQuantity(10f);
		vo.setTotalAmt(10000f);
		vo.setStatus("1");
		orders.add(vo);
		OrderInfo3VO vo1 = new OrderInfo3VO();
		vo1.setOrderId("S003");
		vo1.setOrderDate(LocalDate.now());
		vo1.setBuyer("P003");
		vo1.setOrderType("SO");
		vo1.setSaler("P004");
		vo1.setPrice(1000f);
		vo1.setQuantity(10f);
		vo1.setTotalAmt(10000f);
		vo1.setStatus("1");

		orders.add(vo1);
		sqlToyCRUDService.saveAll(orders);
	}

	@Test
	public void testLoad() {
		OrderInfo3VO orderInfo = sqlToyCRUDService.load(new OrderInfo3VO("S001"));
		System.err.println(JSON.toJSONString(orderInfo));
		// sqlToyCRUDService.saveAll(orders);
	}

	@Test
	public void testLoadAll() {
		List<OrderInfo3VO> orders = new ArrayList<OrderInfo3VO>();
		OrderInfo3VO vo = new OrderInfo3VO("S001");
		OrderInfo3VO vo1 = new OrderInfo3VO("S002");
		orders.add(vo);
		orders.add(vo1);
		List<OrderInfo3VO> result = sqlToyCRUDService.loadAll(orders);
		System.err.println(JSON.toJSONString(result));
		// sqlToyCRUDService.saveAll(orders);
	}

	@Test
	public void testDelete() {
		Long result = sqlToyCRUDService.delete(new OrderInfo3VO("S001"));
		System.err.println("delete count=" + result);
	}

	@Test
	public void testDeleteAll() {
		List<OrderInfo3VO> orders = new ArrayList<OrderInfo3VO>();
		OrderInfo3VO vo = new OrderInfo3VO("S002");
		OrderInfo3VO vo1 = new OrderInfo3VO("S003");
		orders.add(vo);
		orders.add(vo1);
		Long result = sqlToyCRUDService.deleteAll(orders);
		System.err.println("delete count=" + result);
	}

	@Test
	public void testQuery() {
		List<OrderInfoVO> result = sqlToyLazyDao.findBySql("clickhouse_trade_info",
				new String[] { "status", "beginDate", "endDate" }, new Object[] { "1", "2019-01-01", "2020-02-20" },
				OrderInfoVO.class);
		System.err.println(JSON.toJSONString(result));
	}

	@Test
	public void testFindTop() {
		List<OrderInfoVO> result = sqlToyLazyDao.findTopBySql("clickhouse_trade_info",
				new String[] { "status", "beginDate", "endDate" }, new Object[] { "1", "2019-01-01", "2020-02-20" },
				OrderInfoVO.class, 5);
		System.err.println(JSON.toJSONString(result));
	}

	@Test
	public void testGetRandom() {
		List<OrderInfoVO> result = sqlToyLazyDao.getRandomResult("clickhouse_trade_info",
				new String[] { "status", "beginDate", "endDate" }, new Object[] { "1", "2019-01-01", "2020-02-20" },
				OrderInfoVO.class, 5);
		System.err.println(JSON.toJSONString(result));
	}

	@Test
	public void testFindPage() {
		PaginationModel result = sqlToyLazyDao.findPageBySql(new PaginationModel(), "clickhouse_trade_info",
				new String[] { "status", "beginDate", "endDate" }, new Object[] { "1", "2019-01-01", "2020-02-20" },
				OrderInfoVO.class);
		System.err.println(JSON.toJSONString(result));
	}

}
