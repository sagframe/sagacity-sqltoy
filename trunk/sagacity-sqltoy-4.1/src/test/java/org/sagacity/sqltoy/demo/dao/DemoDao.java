/**
 * 
 */
package org.sagacity.sqltoy.demo.dao;


import org.sagacity.sqltoy.demo.vo.DictTypeVO;
import org.sagacity.sqltoy.model.PaginationModel;
import org.sagacity.sqltoy.support.BaseDaoSupport;
import org.springframework.stereotype.Repository;

/**
 * @project sqltoy-orm
 * @description 测试Dao
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:DemoDao.java,Revision:v1.0,Date:2015年5月15日
 */
@Repository("demoDao")
public class DemoDao extends BaseDaoSupport {
	/**
	 * @todo 数据字典分类分页查询
	 * @param pageModel
	 * @param dictTypeVO
	 * @return
	 */
	public PaginationModel findPage(PaginationModel pageModel,
			DictTypeVO dictTypeVO) throws Exception {
		//super.findByCriteria(new Criteria(dictTypeVO).compare(property, compare))
		return page().pageModel(pageModel).entity(dictTypeVO).sql("sag_findPage_dictType").submit();
//		return super.findPageBySql(pageModel, "sag_findPage_dictType",
//				dictTypeVO);
	}
}
