/**
 * 
 */
package com.sqltoy.quickstart;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.sagacity.sqltoy.dao.SqlToyLazyDao;
import org.sagacity.sqltoy.executor.QueryExecutor;
import org.sagacity.sqltoy.model.PaginationModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.alibaba.fastjson.JSON;
import com.sqltoy.quickstart.service.InitDBService;
import com.sqltoy.quickstart.vo.DeviceOrderVO;
import com.sqltoy.quickstart.vo.StaffInfoVO;

/**
 * @project sqltoy-quickstart
 * @description 演示部分查询特性
 * @author zhongxuchen
 * @version v1.0, Date:2020-7-20
 * @modify 2020-7-20,修改说明
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = SqlToyApplication.class)
public class AdvanceQueryTest {
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

	/**
	 * 普通sql查询,本查询演示了缓存翻译、缓存条件匹配过滤(缓存在项目启动时加载配置，首次调用时加载数据,第二次调用时就会体现出缓存效率优势)
	 */
	@Test
	public void findBySql() {
		// 授权的机构
		String[] authedOrgans = { "100004", "100007" };
		List<DeviceOrderVO> result = sqlToyLazyDao.findBySql("qstart_order_search",
				new String[] { "orderId", "authedOrganIds", "staffName", "beginDate", "endDate" },
				new Object[] { null, authedOrgans, "陈", LocalDate.parse("2018-09-01"), null }, DeviceOrderVO.class);
		result.forEach((vo) -> {
			System.err.println(JSON.toJSONString(vo));
		});
	}

	/**
	 * 分页查询 sqltoy 的分页特点: 1、具有快速分页能力，即先分页后关联，实现查询效率的提升
	 * 2、具有分页优化能力，即缓存总记录数，将分页2次查询变成1.3~1.5次 3、具有智能优化count查询能力: -->剔除order by提升性能;
	 * -->解析sql判断是否可以select count(1)替代原语句from前部分,避免直接select count(1) from
	 * (原sql),从而提升效率 (如：select decode(A,1,xxx),case when end from table 等计算变成select
	 * count(1) from table 则避免了不必要的计算)
	 */
	@Test
	public void findPage() {
		PaginationModel pageModel = new PaginationModel();
		StaffInfoVO staffVO = new StaffInfoVO();
		// 作为查询条件传参数
		staffVO.setStaffName("陈");
		// 使用了分页优化器
		// 第一次调用:执行count 和 取记录两次查询
		PaginationModel<StaffInfoVO> result = sqlToyLazyDao.findPageBySql(pageModel, "qstart_fastPage", staffVO);
		result.getRows().forEach((staff) -> {
			System.err.println(JSON.toJSONString(staff));
		});
		// 第二次调用:条件一致，不执行count查询
		result = sqlToyLazyDao.findPageBySql(pageModel, "qstart_fastPage", staffVO);
		System.err.println(JSON.toJSONString(result));
	}

	/**
	 * 取前多少条记录 topSize:如果是大于1的数字,则取其整数部分;如果小于1则表示按比例提取
	 */
	@Test
	public void findTop() {
		// topSize:
		// 授权的机构
		String[] authedOrgans = { "100004", "100007" };
		double topSize = 20;
		List<DeviceOrderVO> result = sqlToyLazyDao.findTopBySql("qstart_order_search",
				new String[] { "orderId", "authedOrganIds", "staffName", "beginDate", "endDate" },
				new Object[] { null, authedOrgans, "陈", "2018-09-01", null }, DeviceOrderVO.class, topSize);
		result.forEach((vo) -> {
			System.err.println(JSON.toJSONString(vo));
		});
	}

	@Test
	public void findTopByQuery() {
		// topSize:
		// 授权的机构
		String[] authedOrgans = { "100004", "100007" };
		String[] paramNames = { "orderId", "authedOrganIds", "staffName", "beginDate", "endDate" };
		Object[] paramValues = { null, authedOrgans, "陈", "2018-09-01", null };
		double topSize = 20;
		List<DeviceOrderVO> result = sqlToyLazyDao.findTopByQuery(new QueryExecutor("qstart_order_search")
				.names(paramNames).values(paramValues).resultType(DeviceOrderVO.class), topSize).getRows();
		result.forEach((vo) -> {
			System.err.println(JSON.toJSONString(vo));
		});
	}

	/**
	 * 查询随机记录 randomSize:如果是大于1的数字,则取其整数部分;如果小于1则表示按比例提取
	 * 
	 * @throws InterruptedException
	 */
	@Test
	public void findByRandom() throws InterruptedException {
		for (int i = 2; i > 0; i--) {
			// 授权的机构
			String[] authedOrgans = { "100004", "100007" };
			double randomSize = 20;
			List<DeviceOrderVO> result = sqlToyLazyDao.getRandomResult("qstart_order_search",
					new String[] { "orderId", "authedOrganIds", "staffName", "beginDate", "endDate" },
					new Object[] { null, authedOrgans, "陈", "2018-09-01", null }, DeviceOrderVO.class, randomSize);
			System.err.println("======第[" + i + "]次取随机记录的结果输出====================");
			result.forEach((vo) -> {
				System.err.println(JSON.toJSONString(vo));
			});
			Thread.sleep(100000);
		}
	}

	@Test
	public void testColsRelativeCalculate() throws InterruptedException {
		List result = sqlToyLazyDao.findBySql("qstart_cols_relative_case", null);
		for (int i = 0; i < result.size(); i++) {
			System.err.println(JSON.toJSONString(result.get(i)));
		}
	}

	@Test
	public void testRowsRelativeCalculate() throws InterruptedException {
		List result = sqlToyLazyDao.findBySql("qstart_rows_relative_case", null);
		for (int i = 0; i < result.size(); i++) {
			System.err.println(JSON.toJSONString(result.get(i)));
		}
	}

	@Test
	public void testPivotList() throws InterruptedException {
		List result = sqlToyLazyDao.findBySql("qstart_pivot_case", null);
		for (int i = 0; i < result.size(); i++) {
			System.err.println(JSON.toJSONString(result.get(i)));
		}
	}

	@Test
	public void testGroupSummary() throws InterruptedException {
		List result = sqlToyLazyDao.findBySql("qstart_group_summary_case", null);
		for (int i = 0; i < result.size(); i++) {
			System.err.println(JSON.toJSONString(result.get(i)));
		}
	}
}
