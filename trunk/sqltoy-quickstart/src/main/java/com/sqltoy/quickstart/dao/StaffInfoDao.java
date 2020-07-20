/**
 * 
 */
package com.sqltoy.quickstart.dao;

import org.sagacity.sqltoy.config.model.Translate;
import org.sagacity.sqltoy.model.EntityQuery;
import org.sagacity.sqltoy.model.PaginationModel;
import org.sagacity.sqltoy.support.SqlToyDaoSupport;
import org.springframework.stereotype.Repository;

import com.sqltoy.quickstart.vo.StaffInfoVO;

/**
 * 演示dao的编写,正常情况下sqltoy并不需要写dao(service层调用SqlToyLazyDao即可完成)
 * 
 * @author zhongxuchen
 * @version 1.0.0,Date:2020-07-16
 */
@Repository("staffInfoDao")
public class StaffInfoDao extends SqlToyDaoSupport {
	public PaginationModel<StaffInfoVO> findStaff(PaginationModel<StaffInfoVO> pageModel, StaffInfoVO staffInfoVO) {
		// sql可以直接在代码中编写,复杂sql建议在xml中定义
		// 单表entity查询场景下sql字段可以写成java类的属性名称
		// 单表查询一般适用于接口内部查询,面向页面很少存在单表查询(除非做ETL归集)
		String sql = "#[staffName like :staffName]#[and createTime>=:beginDate]#[and createTime<=:endDate]";
		return findEntity(StaffInfoVO.class, pageModel, EntityQuery.create().where(sql).values(staffInfoVO)
				// 字典缓存必须要设置cacheType
				// 需设置keyColumn构成select keyColumn as column模式
				.translates(new Translate("dictKeyName").setColumn("sexTypeName").setCacheType("SEX_TYPE")
						.setKeyColumn("sexType"))
				.translates(new Translate("organIdName").setColumn("organName").setKeyColumn("organId")));
	}
}
