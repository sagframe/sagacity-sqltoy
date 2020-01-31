/**
 * 
 */
package com.sagframe.sqltoy.showcase;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagacity.sqltoy.service.SqlToyCRUDService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import com.alibaba.fastjson.JSON;
import com.sagframe.sqltoy.SqlToyApplication;
import com.sagframe.sqltoy.showcase.vo.OrderInfo3VO;

/**
 * @project sqltoy-boot-showcase
 * @description 基于clickhouse的功能验证
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:ClickHouseTest.java,Revision:v1.0,Date:2020年1月22日
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = SqlToyApplication.class)
public class ClickHouseTest {
	@Autowired
	private SqlToyCRUDService sqlToyCRUDService;

	@Test
	public void testInsert() {
		OrderInfo3VO vo = new OrderInfo3VO();
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
		OrderInfo3VO orderInfo = sqlToyCRUDService.load(new OrderInfo3VO("1580218932959098700561"));
		System.err.println(JSON.toJSONString(orderInfo));
		// sqlToyCRUDService.saveAll(orders);
	}

	@Test
	public void testLoadAll() {
		List<OrderInfo3VO> orders = new ArrayList<OrderInfo3VO>();
		OrderInfo3VO vo = new OrderInfo3VO("1580218932959098700561");
		OrderInfo3VO vo1 = new OrderInfo3VO("1580219553222009800561");
		orders.add(vo);
		orders.add(vo1);
		List<OrderInfo3VO> result = sqlToyCRUDService.loadAll(orders);
		System.err.println(JSON.toJSONString(result));
		// sqlToyCRUDService.saveAll(orders);
	}

	@Test
	public void testDelete() {
		Long result = sqlToyCRUDService.delete(new OrderInfo3VO("1580218932959098700561"));
		System.err.println("delete count=" + result);
	}

	@Test
	public void testDeleteAll() {
		List<OrderInfo3VO> orders = new ArrayList<OrderInfo3VO>();
		OrderInfo3VO vo = new OrderInfo3VO("1580219553264115300561");
		OrderInfo3VO vo1 = new OrderInfo3VO("1580477181747381500561");
		orders.add(vo);
		orders.add(vo1);
		Long result = sqlToyCRUDService.deleteAll(orders);
		System.err.println("delete count=" + result);
	}

	@Test
	public void testQuery() {

		// sqlToyCRUDService.saveAll(orders);
	}

	@Test
	public void testFindPage() {

		// sqlToyCRUDService.saveAll(orders);
	}
}
