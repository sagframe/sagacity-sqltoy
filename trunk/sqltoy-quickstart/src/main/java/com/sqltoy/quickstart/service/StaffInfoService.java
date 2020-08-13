package com.sqltoy.quickstart.service;

import java.util.List;

import org.sagacity.sqltoy.model.PaginationModel;

import com.sqltoy.quickstart.vo.StaffInfoVO;

/**
 * @project sqltoy-quickstart
 * @description 请在此说明类的功能
 * @author zhong
 * @version v1.0, Date:2020年7月17日
 * @modify 2020年7月17日,修改说明
 */
public interface StaffInfoService {
	public PaginationModel<StaffInfoVO> queryStaff(PaginationModel<StaffInfoVO> pageModel, StaffInfoVO staffInfoVO);

	/**
	 * @TODO 演示锁记录查询、修改并返回修改后的结果
	 * @return
	 */
	public List<StaffInfoVO> updateFetch();

	/**
	 * @TODO 演示锁记录并修改
	 * @param id
	 * @param address
	 */
	public void updateLockStaff(String id, String address);

	public List<StaffInfoVO> callStore();
}
