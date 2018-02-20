/**
 * 
 */
package sqltoy.showcase.system.service;

import java.util.List;

import org.sagacity.sqltoy.model.PaginationModel;

import sqltoy.showcase.system.vo.StaffInfoVO;

/**
 * @project sqltoy-showcase
 * @description
 * 				<p>
 *              请在此说明类的功能
 *              </p>
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:SeniorQueryService.java,Revision:v1.0,Date:2016年12月8日
 */
public interface SeniorQueryService {
	/**
	 * 汇总查询
	 * 
	 * @return
	 * @throws Exception
	 */
	public List summarySearch() throws Exception;

	/**
	 * 列转行
	 * 
	 * @return
	 * @throws Exception
	 */
	public List findUnpivotList() throws Exception;

	/**
	 * 行转列
	 * 
	 * @return
	 * @throws Exception
	 */
	public List findPivotList() throws Exception;

	/**
	 * 分页查询员工信息
	 * 
	 * @param pageModel
	 * @param paramNames
	 * @param paramValues
	 * @param resultClass
	 * @return
	 * @throws Exception
	 */
	public PaginationModel findStaffInfo(PaginationModel pageModel, String[] paramNames, Object[] paramValues,
			Class resultClass) throws Exception;

	/**
	 * 分库分表查询
	 * @param pageModel
	 * @param paramNames
	 * @param paramValues
	 * @param resultClass
	 * @return
	 * @throws Exception
	 */
	public PaginationModel findSharding(PaginationModel pageModel, String[] paramNames, Object[] paramValues,
			Class resultClass) throws Exception;
	
	public List findTopStaff(double topSize, String[] paramNames, Object[] paramValues, Class resultClass)
			throws Exception;

	public List findTopStaff(double topSize, StaffInfoVO staffInfoVO) throws Exception;

	public List findRandomStaff(double randomSize, String[] paramNames, Object[] paramValues, Class resultClass)
			throws Exception;

	public List findRandomStaff(double randomSize, StaffInfoVO staffInfoVO) throws Exception;

}
