/**
 * 
 */
package sqltoy.showcase.system.service.impl;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.sagacity.sqltoy.dao.SqlToyLazyDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import sqltoy.showcase.system.service.OrganInfoService;
import sqltoy.showcase.system.vo.OrganInfoVO;

/**
 * @project sqltoy-showcase
 * @description
 *              <p>
 *              请在此说明类的功能
 *              </p>
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:OrganInfoServiceImpl.java,Revision:v1.0,Date:2015年11月3日
 */
@Service("organInfoService")
public class OrganInfoServiceImpl implements OrganInfoService {
	/**
	 * 定义全局日志
	 */
	private final static Logger logger = LogManager.getLogger(OrganInfoServiceImpl.class);

	@Autowired
	private SqlToyLazyDao sqlToyLazyDao;

	/*
	 * (non-Javadoc)
	 * 
	 * @see sqltoy.showcase.system.service.OrganInfoService#add(sqltoy.showcase.
	 * system.vo.OrganInfoVO)
	 */
	@Override
	@CacheEvict(value = "organIdNameCache", allEntries = true)
	public void add(OrganInfoVO organInfoVO) {
		sqlToyLazyDao.save(organInfoVO);
		logger.info("新增机构信息成功！");
	}
}
