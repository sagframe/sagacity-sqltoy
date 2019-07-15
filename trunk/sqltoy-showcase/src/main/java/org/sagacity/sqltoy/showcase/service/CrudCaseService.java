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
	 * 
	 * @param staffInfoVO
	 */
	public void saveStaffInfo(StaffInfoVO staffInfoVO);
}
