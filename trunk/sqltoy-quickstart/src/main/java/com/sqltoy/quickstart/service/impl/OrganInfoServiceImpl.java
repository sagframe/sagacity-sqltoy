/**
 * 
 */
package com.sqltoy.quickstart.service.impl;

import org.sagacity.sqltoy.dao.SqlToyLazyDao;
import org.sagacity.sqltoy.model.TreeTableModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sqltoy.quickstart.service.OrganInfoService;
import com.sqltoy.quickstart.vo.OrganInfoVO;

/**
 * @project sqltoy-quickstart
 * @description 请在此说明类的功能
 * @author zhongxuchen
 * @version v1.0, Date:2020-7-22
 * @modify 2020-7-22,修改说明
 */
@Service("organInfoService")
public class OrganInfoServiceImpl implements OrganInfoService {
	@Autowired
	SqlToyLazyDao sqlToyLazyDao;

	@Transactional
	public void saveOrganInfo(OrganInfoVO organInfoVO) {
		// 先保存机构
		sqlToyLazyDao.save(organInfoVO);
		// id字段根据vo找表的主键会自动匹配上,其它的NODE_ROUTE\NODE_LEVEL\IS_LEAF 为标准命名无需额外设置
		// 需要告知pid对应的字段(vo属性会自动映射到表字段)
		sqlToyLazyDao.wrapTreeTableRoute(new TreeTableModel(organInfoVO).pidField("organPid"));
	}

}
