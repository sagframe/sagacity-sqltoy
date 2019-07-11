/**
 * 
 */
package sqltoy.showcase.system.service;

import sqltoy.showcase.system.vo.OrganInfoVO;

/**
 * @project sqltoy-showcase
 * @description
 *              <p>
 *              机构信息维护
 *              </p>
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:OrganInfoService.java,Revision:v1.0,Date:2015年10月28日
 */
public interface OrganInfoService {
	/**
	 * 新增机构
	 * 
	 * @param organInfoVO
	 * @throws CreateSequenceException
	 */
	public void add(OrganInfoVO organInfoVO);
}
