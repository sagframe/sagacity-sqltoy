/**
 * 
 */
package sqltoy.showcase.system.dao;

import java.util.List;

import org.sagacity.sqltoy.model.PaginationModel;
import org.sagacity.sqltoy.support.BaseDaoSupport;
import org.springframework.stereotype.Repository;

import sqltoy.showcase.system.vo.Goods;
import sqltoy.showcase.system.vo.GoodsParam;
import sqltoy.showcase.system.vo.OrganInfoVO;
import sqltoy.showcase.system.vo.StaffInfoVO;

/**
 * @project sqltoy-showcase
 * @description 员工信息操作
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:StaffInfoDao.java,Revision:v1.0,Date:2017年11月8日
 */
@Repository("staffInfoDao")
public class StaffInfoDao extends BaseDaoSupport {
	/**
	 * 分页查询员工信息
	 * 
	 * @param pageModel
	 * @param staffInfoVO
	 * @return
	 * @throws Exception
	 */
	public PaginationModel findPage(PaginationModel pageModel, StaffInfoVO staffInfoVO) throws Exception {
		return page().pageModel(pageModel).sql("sys_findStaff").entity(staffInfoVO).submit();
	}

	/**
	 * 保存员工信息
	 * 
	 * @param staffInfos
	 * @throws Exception
	 */
	public void saveOrUpdateStaff(List<StaffInfoVO> staffInfos) throws Exception {
		save().saveMode(IGNORE).many(staffInfos);
	}

	public List findES(OrganInfoVO organInfoVO) throws Exception {
		return elastic().sql("find_goods_count").entity(organInfoVO).find();
	}

	public PaginationModel findESPage(PaginationModel pageModel, OrganInfoVO organInfoVO) throws Exception {
		return elastic().sql("sys_elastic_test_json").entity(organInfoVO).findPage(pageModel);
	}

	public List findESBySql(OrganInfoVO organInfoVO) throws Exception {
		return elastic().sql("sys_elastic_test").entity(organInfoVO).find();
	}

	public PaginationModel findESPageBySql(PaginationModel pageModel, GoodsParam goodsParam) throws Exception {
		return elastic().sql("query_goods").entity(goodsParam).resultType(Goods.class).findPage(pageModel);
	}

	public List findESAggs(PaginationModel pageModel, OrganInfoVO organInfoVO) throws Exception {
		return elastic().sql("sys_elastic_test_aggs").entity(organInfoVO).find();
	}
	
	public List findESSuggest(PaginationModel pageModel, OrganInfoVO organInfoVO) throws Exception {
		return elastic().sql("query_hot_search").entity(organInfoVO).find();
	}
}
