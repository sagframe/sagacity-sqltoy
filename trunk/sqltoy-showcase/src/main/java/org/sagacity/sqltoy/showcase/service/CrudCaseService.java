/**
 * 
 */
package org.sagacity.sqltoy.showcase.service;

import org.sagacity.sqltoy.showcase.vo.StaffInfoVO;

/**
 * @project sqltoy-showcase
 * @description 常规范例服务演示
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:CrudCaseService.java,Revision:v1.0,Date:2019年7月11日
 */
public interface CrudCaseService {
	/**
	 * @TODO 创建保存员工信息
	 * @param staffInfoVO
	 */
	public Object saveStaffInfo(StaffInfoVO staffInfoVO);

	/**
	 * @TODO 修改员工信息
	 * @param staffInfoVO
	 */
	public void updateStaffInfo(StaffInfoVO staffInfoVO);
}
