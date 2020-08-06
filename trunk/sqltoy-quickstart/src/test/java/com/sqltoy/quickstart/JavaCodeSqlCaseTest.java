/**
 * 
 */
package com.sqltoy.quickstart;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.sagacity.sqltoy.config.model.PageOptimize;
import org.sagacity.sqltoy.config.model.Translate;
import org.sagacity.sqltoy.dao.SqlToyLazyDao;
import org.sagacity.sqltoy.executor.QueryExecutor;
import org.sagacity.sqltoy.model.EntityQuery;
import org.sagacity.sqltoy.model.MaskType;
import org.sagacity.sqltoy.model.PaginationModel;
import org.sagacity.sqltoy.model.ParamsFilter;
import org.sagacity.sqltoy.utils.DebugUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.alibaba.fastjson.JSON;
import com.sqltoy.quickstart.service.InitDBService;
import com.sqltoy.quickstart.vo.DeviceOrderVO;
import com.sqltoy.quickstart.vo.StaffInfoVO;

/**
 * @project sqltoy-quickstart
 * @description 演示在代码中编写sql并实现原本xml中的一些功能
 * @author zhongxuchen
 * @version v1.0, Date:2020-7-20
 * @modify 2020-7-20,修改说明
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = SqlToyApplication.class)
public class JavaCodeSqlCaseTest {
	@Autowired
	SqlToyLazyDao sqlToyLazyDao;

	@Autowired
	InitDBService initDBService;

	// 第一步，订单数据初始化
	@Test
	public void mockOrderData() {
		Long saveCnt = initDBService.initOrderData();
		System.err.println("创建模拟订单记录:" + saveCnt + " 条!");
	}

	// 很多人对sql写在xml极端鄙视，但说实话sql写在代码中真难看(起初大家都是写在代码中走过来的)!后期维护变更调试的时候无比痛苦!
	// sqltoy一直是支持代码中写sql的，但并不推荐，从4.13.12
	// 开始增强了pageOptimize、filters、numFmt、dateFmt、secureMask、缓存翻译等功能
	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void findPageByTextBlock() {
		// 暂时用字符串拼接来
		String sql = "select t1.*,t2.ORGAN_NAME  from @fast(select t.* "
				+ "	  from sqltoy_staff_info t 	 where t.STATUS=1 #[and t.STAFF_NAME like :staffName] "
				+ "	  order by t.ENTRY_DATE desc) t1 left join sqltoy_organ_info t2 "
				+ "   on  t1.organ_id=t2.ORGAN_ID";

		PaginationModel pageModel = new PaginationModel();
		StaffInfoVO staffVO = new StaffInfoVO();
		// 作为查询条件传参数
		staffVO.setStaffName("陈");
		// 使用了分页优化器
		// 第一次调用:执行count 和 取记录两次查询
		PaginationModel<StaffInfoVO> result = sqlToyLazyDao.findPageByQuery(pageModel, new QueryExecutor(sql, staffVO)
				.filters(new ParamsFilter("staffName").rlike()).pageOptimize(new PageOptimize().aliveSeconds(120)))
				.getPageResult();
		for (StaffInfoVO staff : result.getRows()) {
			System.err.println(JSON.toJSONString(staff));
		}

		// 第二次调用不会再执行count查询
		result = sqlToyLazyDao.findPageByQuery(pageModel, new QueryExecutor(sql, staffVO)
				.filters(new ParamsFilter("staffName").rlike()).pageOptimize(new PageOptimize().aliveSeconds(120)))
				.getPageResult();
		for (StaffInfoVO staff : result.getRows()) {
			System.err.println(JSON.toJSONString(staff));
		}
	}

	// 单表的查询其实还说的过去，确实有存在的意义
	// 演示基于Entity单表对象查询
	@Test
	public void findPageTranslate() {
		// 初始化，实现缓存加载影响性能
		String sql = "STATUS=1 #[and STAFF_NAME like :staffName]";
		ParamsFilter paramFilter = new ParamsFilter("staffName").rlike();
		Translate translate = new Translate("organIdName").setKeyColumn("organId").setColumn("organName");
		PaginationModel pageModel = new PaginationModel();
		// 演示了缓存翻译、电话号码脱敏
		PaginationModel<StaffInfoVO> result = sqlToyLazyDao.findEntity(StaffInfoVO.class, pageModel,
				EntityQuery.create().where(sql).orderByDesc("ENTRY_DATE").values(new StaffInfoVO().setStaffName("陈"))
						.filters(paramFilter).translates(translate).secureMask(MaskType.TEL, "telNo"));
		for (StaffInfoVO staff : result.getRows()) {
			System.err.println(JSON.toJSONString(staff));
		}

		// 第一次查询
		// 单表查询
		DebugUtil.beginTime("firstPage");
		result = sqlToyLazyDao.findEntity(StaffInfoVO.class, pageModel,
				EntityQuery.create().where(sql).orderByDesc("ENTRY_DATE").values(new StaffInfoVO().setStaffName("陈"))
						.filters(paramFilter).translates(translate).pageOptimize(new PageOptimize().aliveSeconds(120)));
		DebugUtil.endTime("firstPage");

		// 第二次查询，分页优化起作用，不会再执行count查询，提升了效率
		DebugUtil.beginTime("secondPage");
		result = sqlToyLazyDao.findEntity(StaffInfoVO.class, pageModel,
				EntityQuery.create().where(sql).orderByDesc("ENTRY_DATE").values(new StaffInfoVO().setStaffName("陈"))
						.filters(paramFilter).translates(translate).pageOptimize(new PageOptimize().aliveSeconds(120)));
		DebugUtil.endTime("secondPage");

	}

	// 演示了查询条件处理中的primary首要条件处理，当这个条件参数不为空，其他参数都为空
	// 当指定orderId 对应的值时生效
	@Test
	public void findBySql() {
		// 授权的机构
		String[] authedOrgans = { "100004", "100007" };
		List<DeviceOrderVO> result = (List<DeviceOrderVO>) sqlToyLazyDao
				.findByQuery(new QueryExecutor("qstart_order_search")
						.names("orderId", "authedOrganIds", "staffName", "beginDate", "endDate")
						.values(null, authedOrgans, "陈", LocalDate.parse("2018-09-01"), null)
						.resultType(DeviceOrderVO.class)
						// orderId是首要参数，但排除authedOrganIds(授权机构参数)
						.filters(new ParamsFilter("orderId").primary("authedOrganIds")))
				.getRows();

		for (DeviceOrderVO vo : result) {
			System.err.println(JSON.toJSONString(vo));
		}
	}
}
