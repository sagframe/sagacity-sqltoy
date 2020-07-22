/**
 * 
 */
package com.sqltoy.quickstart.service.impl;

import java.util.List;

import org.sagacity.sqltoy.model.PaginationModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sqltoy.quickstart.dao.StaffInfoDao;
import com.sqltoy.quickstart.service.StaffInfoService;
import com.sqltoy.quickstart.vo.StaffInfoVO;

/**
 * 
 * @project sqltoy-quickstart
 * @description 演示
 * @author zhongxuchen
 * @version v1.0, Date:2020年7月20日
 * @modify 2020年7月20日,修改说明
 */
@Service("staffInfoService")
public class StaffInfoServiceImpl implements StaffInfoService {
	// 这里是演示用，常规情況下无需写dao
	@Autowired
	private StaffInfoDao staffInfoDao;

	public PaginationModel<StaffInfoVO> queryStaff(PaginationModel<StaffInfoVO> pageModel, StaffInfoVO staffInfoVO) {
		return staffInfoDao.findStaff(pageModel, staffInfoVO);
	}

	// updateFetch 应用场景一般用于诸如库存台账、资金台账这类需要查询并锁住记录，进行部分逻辑处理后修改记录
	/**
	 * @TODO 演示updateFetch用法
	 * @return
	 */
	@Transactional
	public List<StaffInfoVO> updateFetch() {
		String sql = "";
		return null;
	}
}
