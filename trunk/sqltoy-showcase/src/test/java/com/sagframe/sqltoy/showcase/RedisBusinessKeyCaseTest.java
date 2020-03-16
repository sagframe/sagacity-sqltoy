/**
 * 
 */
package com.sagframe.sqltoy.showcase;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.sagacity.sqltoy.service.SqlToyCRUDService;
import org.sagacity.sqltoy.utils.DateUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.sagframe.sqltoy.SqlToyApplication;
import com.sagframe.sqltoy.showcase.vo.DeviceOrderInfoVO;

/**
 * @project sqltoy-showcase
 * @description 基于redis实现有规则的业务主键生成策略
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:RedisBusinessKeyCaseTest.java,Revision:v1.0,Date:2019年7月16日
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = SqlToyApplication.class)
public class RedisBusinessKeyCaseTest {
	@Autowired
	private SqlToyCRUDService sqlToyCRUDService;

	// 请参见tools\quickvo\quickvo.xml中的配置:
	// <business-primary-key>
	// <!-- 1位购销标志_2位设备分类代码_6位日期_3位流水 (如果当天超过3位会自动扩展到4位) -->
	// <table name="SQLTOY_DEVICE_ORDER_INFO" column="ORDER_ID"
	// generator="redis"
	// signature="${psType}@case(${deviceType},PC,PC,NET,NT,OFFICE,OF,SOFTWARE,SF,OT)@day(yyMMdd)"
	// related-columns="psType,deviceType" length="12" />
	// </business-primary-key>
	// @case(${var},A,A对应的值,B,B对应的值,other) 类似于oracle的decode用法
	// @day(dateFormat) 表示格式化系统时间按格式输出

	@Test
	public void generateBusinessKey() {
		// @case() 类似于oracle的decode 函数
		// signature="${psType}@case(${deviceType},PC,PC,NET,NT,OFFICE,OF,SOFTWARE,SF,OT)@day(yyMMdd)"
		DeviceOrderInfoVO deviceOrder = new DeviceOrderInfoVO();
		// pc设备
		deviceOrder.setDeviceType("PC");
		// 采购
		deviceOrder.setPsType("P");
		deviceOrder.setTotalAmt(BigDecimal.valueOf(100000));
		deviceOrder.setTotalCnt(BigDecimal.valueOf(20));
		deviceOrder.setBuyer("C10001");
		deviceOrder.setSaler("S00001");
		deviceOrder.setStaffId("S001");
		deviceOrder.setStatus(1);
		deviceOrder.setTransDate(DateUtil.getDate());
		deviceOrder.setDeliveryTerm(DateUtil.asLocalDate(DateUtil.addDay(DateUtil.getNowTime(), 30)));
		// 得到有规则的订单编号,类似:PPC190716001
		String orderId = (String) sqlToyCRUDService.save(deviceOrder);
		System.err.println(orderId);
	}
}
