/**
 * 
 */
package com.sqltoy.quickstart.service.impl;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.sagacity.sqltoy.callback.DataSourceCallbackHandler;
import org.sagacity.sqltoy.dao.SqlToyLazyDao;
import org.sagacity.sqltoy.model.EntityQuery;
import org.sagacity.sqltoy.utils.DataSourceUtils;
import org.sagacity.sqltoy.utils.DateUtil;
import org.sagacity.sqltoy.utils.FileUtil;
import org.sagacity.sqltoy.utils.NumberUtil;
import org.sagacity.sqltoy.utils.SqlUtil;
import org.sagacity.sqltoy.utils.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sqltoy.quickstart.service.InitDBService;
import com.sqltoy.quickstart.vo.DeviceOrderVO;
import com.sqltoy.quickstart.vo.StaffInfoVO;

/**
 * @project sqltoy-quickstart
 * @description 请在此说明类的功能
 * @author zhongxuchen
 * @version v1.0, Date:2020年7月17日
 * @modify 2020年7月17日,修改说明
 */
@Service("initDBService")
public class InitDBServiceImpl implements InitDBService {
	@Autowired
	private SqlToyLazyDao sqlToyLazyDao;

	@Transactional
	public void initDatabase(String dataSqlFile) {
		// 加载初始化数据脚本(最好手工执行数据初始化,便于演示缓存翻译功能)
		final String sqlContent = FileUtil.readFileAsStr(dataSqlFile, "UTF-8");
		if (StringUtil.isBlank(sqlContent)) {
			return;
		}
		DataSourceUtils.processDataSource(sqlToyLazyDao.getSqlToyContext(), sqlToyLazyDao.getDataSource(),
				new DataSourceCallbackHandler() {
					@Override
					public void doConnection(Connection conn, Integer dbType, String dialect) throws Exception {
						// executeBatchSql可以根据数据库类型将大的sql字符进行分割循环执行
						SqlUtil.executeBatchSql(conn, sqlContent, 100, true);
					}
				});

	}

	@Transactional
	public Long initOrderData() {
		// 第一步清除数据(deleteByQuery 不允许无条件删除,简易方式跳过)
		sqlToyLazyDao.deleteByQuery(DeviceOrderVO.class, EntityQuery.create().where("1=?").values(1));

		// 模拟订单信息
		List<DeviceOrderVO> orderInfos = new ArrayList<DeviceOrderVO>();
		int max = 1000;
		// 查询全部员工(空条件,sqltoy强制约束需要设置条件)
		List<StaffInfoVO> staffs = sqlToyLazyDao.findEntity(StaffInfoVO.class,
				EntityQuery.create().where("").values(""));
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
			// 随机设置相关参数
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
		return sqlToyLazyDao.saveAll(orderInfos);
	}

}
