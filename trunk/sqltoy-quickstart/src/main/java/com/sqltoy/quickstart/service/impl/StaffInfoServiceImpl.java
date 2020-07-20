/**
 * 
 */
package com.sqltoy.quickstart.service.impl;

import org.sagacity.sqltoy.model.PaginationModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
	//这里是演示用，常规情況下无需写dao
	@Autowired
	private StaffInfoDao staffInfoDao;

	public PaginationModel<StaffInfoVO> queryStaff(PaginationModel<StaffInfoVO> pageModel, StaffInfoVO staffInfoVO) {
		return staffInfoDao.findStaff(pageModel, staffInfoVO);
	}
}
