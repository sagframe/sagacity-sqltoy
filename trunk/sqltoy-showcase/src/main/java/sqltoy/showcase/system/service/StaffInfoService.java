/**
 * 
 */
package sqltoy.showcase.system.service;

import org.sagacity.sqltoy.model.PaginationModel;

import sqltoy.showcase.system.vo.StaffInfoVO;

/**
 * @project sqltoy-showcase
 * @description <p>请在此说明类的功能</p>
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:StaffInfoService.java,Revision:v1.0,Date:2015年10月28日
 */
public interface StaffInfoService {
	/**
	 * @TODO 分页查询员工信息
	 * @param staffInfoVO
	 * @return
	 */
	public PaginationModel findStaff(StaffInfoVO staffInfoVO);
}
