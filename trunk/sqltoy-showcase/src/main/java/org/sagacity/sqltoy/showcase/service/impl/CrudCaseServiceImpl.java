/**
 * 
 */
package org.sagacity.sqltoy.showcase.service.impl;

import org.sagacity.sqltoy.dao.SqlToyLazyDao;
import org.sagacity.sqltoy.showcase.service.CrudCaseService;
import org.sagacity.sqltoy.showcase.vo.StaffInfoVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @project sqltoy-showcase
 * @description 常规范例演示实现
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:CrudCaseServiceImpl.java,Revision:v1.0,Date:2019年7月11日
 */
@Service("crudCaseService")
public class CrudCaseServiceImpl implements CrudCaseService {
	@Autowired
	private SqlToyLazyDao sqlToyLazyDao;

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.showcase.service.CrudCaseService#saveStaffInfo(org.
	 * sagacity.sqltoy.showcase.vo.StaffInfoVO)
	 */
	@Override
	public void saveStaffInfo(StaffInfoVO staffInfoVO) {
		sqlToyLazyDao.save(staffInfoVO);
	}

}
