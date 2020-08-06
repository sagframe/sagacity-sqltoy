/**
 * 
 */
package com.sqltoy.quickstart;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.sagacity.sqltoy.service.SqlToyCRUDService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.alibaba.fastjson.JSON;
import com.sqltoy.quickstart.vo.ComplexpkHeadVO;
import com.sqltoy.quickstart.vo.ComplexpkItemVO;

/**
 * @project sqltoy-quickstart
 * @description 演示级联操作(sqltoy支持级联,但实际场景这种外键关联模式未必合适，请根据实际场景进行技术应用)
 * @author zhongxuchen@gmail.com
 * @version v1.0, Date:2020-8-6
 * @modify 2020-8-6,修改说明
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = SqlToyApplication.class)
public class CascadeCaseTest {
	@Autowired
	private SqlToyCRUDService sqlToyCRUDService;

	/**
	 * 每次先执行删除
	 */
	@Test
	public void testDeleteCascade() {
		// 删除
		sqlToyCRUDService.delete(new ComplexpkHeadVO(LocalDate.parse("2020-08-06"), "S0001"));
	}

	/**
	 * 演示级联保存
	 */
	@Test
	public void testSaveCascade() {
		// 主表记录
		ComplexpkHeadVO head = new ComplexpkHeadVO();
		head.setTransDate(LocalDate.parse("2020-08-06"));
		head.setTransCode("S0001");
		head.setTotalCnt(BigDecimal.valueOf(10));
		head.setTotalAmt(BigDecimal.valueOf(10000));

		// 子表记录1
		ComplexpkItemVO item1 = new ComplexpkItemVO();
		// 这里id是为了便于演示手工指定
		item1.setId("S000101");
		item1.setProductId("P01");
		item1.setPrice(BigDecimal.valueOf(1000));
		item1.setAmt(BigDecimal.valueOf(5000));
		item1.setQuantity(BigDecimal.valueOf(5));
		head.getComplexpkItemVOs().add(item1);

		// 子表记录2
		ComplexpkItemVO item2 = new ComplexpkItemVO();
		item2.setId("S000102");
		item2.setProductId("P02");
		item2.setPrice(BigDecimal.valueOf(1000));
		item2.setAmt(BigDecimal.valueOf(5000));
		item2.setQuantity(BigDecimal.valueOf(5));
		head.getComplexpkItemVOs().add(item2);

		sqlToyCRUDService.save(head);
	}

	@Test
	public void testUpdateCascade() {
		// 主表记录
		ComplexpkHeadVO head = new ComplexpkHeadVO();
		head.setTransDate(LocalDate.parse("2020-08-06"));
		head.setTransCode("S0001");
		head.setTotalCnt(BigDecimal.valueOf(11));
		head.setTotalAmt(BigDecimal.valueOf(10000));

		// 子表记录1
		ComplexpkItemVO item1 = new ComplexpkItemVO();
		// 注意这里的id每次测试时需要指定正确
		item1.setId("S000101");
		item1.setProductId("P01");
		item1.setPrice(BigDecimal.valueOf(1100));
		item1.setAmt(BigDecimal.valueOf(5000));
		item1.setQuantity(BigDecimal.valueOf(5));
		head.getComplexpkItemVOs().add(item1);

		// 子表记录2
		ComplexpkItemVO item2 = new ComplexpkItemVO();
		item1.setId("S000103");
		item2.setProductId("P03");
		item2.setPrice(BigDecimal.valueOf(1000));
		item2.setAmt(BigDecimal.valueOf(5000));
		item2.setQuantity(BigDecimal.valueOf(5));
		head.getComplexpkItemVOs().add(item2);

		sqlToyCRUDService.updateCascade(head);
	}

	/**
	 * 演示级联加载
	 */
	@Test
	public void testLoadCascade() {
		ComplexpkHeadVO head = sqlToyCRUDService
				.loadCascade(new ComplexpkHeadVO(LocalDate.parse("2020-08-06"), "S0001"));
		// 打印级联加载的字表数据
		for (ComplexpkItemVO item : head.getComplexpkItemVOs()) {
			System.err.println(JSON.toJSONString(item));
		}
	}
}
