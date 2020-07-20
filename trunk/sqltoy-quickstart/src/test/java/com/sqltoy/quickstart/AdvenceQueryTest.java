/**
 * 
 */
package com.sqltoy.quickstart;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.sagacity.sqltoy.dao.SqlToyLazyDao;
import org.sagacity.sqltoy.service.SqlToyCRUDService;
import org.sagacity.sqltoy.utils.DateUtil;
import org.sagacity.sqltoy.utils.NumberUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.sqltoy.quickstart.vo.DeviceOrderVO;
import com.sqltoy.quickstart.vo.StaffInfoVO;

/**
 * @project sqltoy-quickstart
 * @description 请在此说明类的功能
 * @author zhongxuchen
 * @version v1.0, Date:2020-7-20
 * @modify 2020-7-20,修改说明
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = SqlToyApplication.class)
public class AdvenceQueryTest {
	@Autowired
	SqlToyLazyDao sqlToyLazyDao;

	@Autowired
	SqlToyCRUDService sqlToyCRUDService;

	// 第一步，订单数据初始化
	@Test
	public void mockOrderData() {
		// 模拟订单信息
		List<DeviceOrderVO> orderInfos = new ArrayList<DeviceOrderVO>();
		int max = 1000;
		// 查询全部员工
		List<StaffInfoVO> staffs = sqlToyLazyDao.findBySql("select STAFF_ID,STAFF_NAME,ORGAN_ID from sqltoy_staff_info",
				null, null, StaffInfoVO.class);
		StaffInfoVO staff;
		int[] days = { 10, 15, 20, 30, 60 };
		LocalDate nowTime = DateUtil.getDate();
		// 直接通过sqltoy的缓存获取字典数据,避免查询数据库
		List<Object[]> deviceTypes = new ArrayList<Object[]>(
				sqlToyLazyDao.getTranslateCache("dictKeyName", "DEVICE_TYPE").values());
		// 采购、销售标志
		String[] psTypes = { "PO", "SO" };
		for (int i = 0; i < max; i++) {
			DeviceOrderVO orderVO = new DeviceOrderVO();
			staff = staffs.get(NumberUtil.getRandomNum(staffs.size() - 1));
			orderVO.setBuyer("C000" + i);
			orderVO.setSaler("S000" + i);
			orderVO.setStaffId(staff.getStaffId());
			orderVO.setOrganId(staff.getOrganId());
			orderVO.setTransDate(nowTime);
			orderVO.setDeliveryTerm(DateUtil.asLocalDate(DateUtil.addDay(nowTime, days[NumberUtil.getRandomNum(4)])));
			orderVO.setDeviceType(deviceTypes.get(NumberUtil.getRandomNum(deviceTypes.size() - 1))[0].toString());
			orderVO.setPsType(psTypes[NumberUtil.getRandomNum(1)]);
			orderVO.setTotalCnt(new BigDecimal(NumberUtil.getRandomNum(100, 400)));
			orderVO.setTotalAmt(orderVO.getTotalCnt().multiply(BigDecimal.valueOf(500)));
			orderVO.setStatus(1);
			orderVO.setCreateBy("S0001");
			orderVO.setUpdateBy("S0001");
			orderInfos.add(orderVO);
		}
		// 事务控制在service层上面的
		sqlToyCRUDService.saveAll(orderInfos);
	}
}
