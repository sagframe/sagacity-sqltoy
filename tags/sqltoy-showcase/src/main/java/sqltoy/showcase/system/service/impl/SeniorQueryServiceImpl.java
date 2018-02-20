/**
 * 
 */
package sqltoy.showcase.system.service.impl;

import java.util.List;

import org.sagacity.sqltoy.model.PaginationModel;
import org.sagacity.sqltoy.dao.SqlToyLazyDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import sqltoy.showcase.system.service.SeniorQueryService;
import sqltoy.showcase.system.vo.StaffInfoVO;
import sqltoy.showcase.system.vo.UnpivotDataVO;

/**
 * @project sqltoy-showcase
 * @description
 * 				<p>
 *              请在此说明类的功能
 *              </p>
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:SeniorQueryServiceImpl.java,Revision:v1.0,Date:2016年12月8日
 */
@Service("seniorQueryService")
public class SeniorQueryServiceImpl implements SeniorQueryService {
	@Autowired
	private SqlToyLazyDao sqlToyLazyDao;

	/*
	 * (non-Javadoc)
	 * 
	 * @see sqltoy.showcase.system.service.SeniorQueryService#summarySearch()
	 */
	@Override
	public List summarySearch() throws Exception {
		return sqlToyLazyDao.findBySql("sys_summarySearch", null);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see sqltoy.showcase.system.service.SeniorQueryService#findUnpivotList()
	 */
	@Override
	public List findUnpivotList() throws Exception {
		// TODO Auto-generated method stub
		return sqlToyLazyDao.findBySql("sys_unpvoitSearch", null, null, UnpivotDataVO.class);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see sqltoy.showcase.system.service.SeniorQueryService#findPivotList()
	 */
	@Override
	public List findPivotList() throws Exception {
		// TODO Auto-generated method stub
		return sqlToyLazyDao.findBySql("sys_pvoitSearch", null);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see sqltoy.showcase.system.service.SeniorQueryService#findStaffInfo(org.
	 * sagacity.core.database.model.PaginationModel, java.lang.String[],
	 * java.lang.Object[], java.lang.Class)
	 */
	@Override
	public PaginationModel findStaffInfo(PaginationModel pageModel, String[] paramNames, Object[] paramValues,
			Class resultClass) throws Exception {
		return sqlToyLazyDao.findPageBySql(pageModel, "sys_findStaff", paramNames, paramValues, resultClass);
	}

	/* (non-Javadoc)
	 * @see sqltoy.showcase.system.service.SeniorQueryService#findSharding(org.sagacity.sqltoy.model.PaginationModel, java.lang.String[], java.lang.Object[], java.lang.Class)
	 */
	@Override
	public PaginationModel findSharding(PaginationModel pageModel, String[] paramNames, Object[] paramValues,
			Class resultClass) throws Exception {
		return sqlToyLazyDao.findPageBySql(pageModel, "sys_findByShardingTime", paramNames, paramValues, resultClass);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * sqltoy.showcase.system.service.SeniorQueryService#findTopStaff(double,
	 * java.lang.String[], java.lang.Object[], java.lang.Class)
	 */
	@Override
	public List findTopStaff(double topSize, String[] paramNames, Object[] paramValues, Class resultClass)
			throws Exception {
		// TODO Auto-generated method stub
		return sqlToyLazyDao.findTopBySql("sys_findStaff", paramNames, paramValues, resultClass, topSize);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * sqltoy.showcase.system.service.SeniorQueryService#findTopStaff(double,
	 * sqltoy.showcase.system.vo.StaffInfoVO)
	 */
	@Override
	public List findTopStaff(double topSize, StaffInfoVO staffInfoVO) throws Exception {
		// TODO Auto-generated method stub
		return sqlToyLazyDao.findTopBySql("sys_findStaff", staffInfoVO, topSize);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * sqltoy.showcase.system.service.SeniorQueryService#findRandomStaff(double,
	 * java.lang.String[], java.lang.Object[], java.lang.Class)
	 */
	@Override
	public List findRandomStaff(double randomSize, String[] paramNames, Object[] paramValues, Class resultClass)
			throws Exception {
		return sqlToyLazyDao.getRandomResult("sys_findStaff", paramNames, paramValues, resultClass, randomSize);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * sqltoy.showcase.system.service.SeniorQueryService#findRandomStaff(double,
	 * sqltoy.showcase.system.vo.StaffInfoVO)
	 */
	@Override
	public List findRandomStaff(double randomSize, StaffInfoVO staffInfoVO) throws Exception {
		// TODO Auto-generated method stub
		return sqlToyLazyDao.getRandomResult("sys_findStaff", staffInfoVO, randomSize);
	}

}
