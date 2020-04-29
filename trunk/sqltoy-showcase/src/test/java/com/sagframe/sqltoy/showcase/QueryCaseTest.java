/**
 * 
 */
package com.sagframe.sqltoy.showcase;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Resource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.sagacity.sqltoy.dao.SqlToyLazyDao;
import org.sagacity.sqltoy.executor.QueryExecutor;
import org.sagacity.sqltoy.model.PaginationModel;
import org.sagacity.sqltoy.model.TreeTableModel;
import org.sagacity.sqltoy.service.SqlToyCRUDService;
import org.sagacity.sqltoy.utils.DateUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.alibaba.fastjson.JSON;
import com.sagframe.sqltoy.SqlToyApplication;
import com.sagframe.sqltoy.showcase.service.InitDataService;
import com.sagframe.sqltoy.showcase.vo.DeviceOrderInfoVO;
import com.sagframe.sqltoy.showcase.vo.DictDetailVO;
import com.sagframe.sqltoy.showcase.vo.OrderInfoVO;
import com.sagframe.sqltoy.showcase.vo.OrganInfoVO;
import com.sagframe.sqltoy.showcase.vo.StaffInfoVO;
import com.sagframe.sqltoy.showcase.vo.TestVO;
import com.sagframe.sqltoy.utils.ShowCaseUtils;

/**
 * @project sqltoy-boot-showcase
 * @description 演示sql查询
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:QueryCaseTest.java,Revision:v1.0,Date:2019年7月15日
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = SqlToyApplication.class)
public class QueryCaseTest {
	@Resource(name = "sqlToyLazyDao")
	private SqlToyLazyDao sqlToyLazyDao;

	@Autowired
	private SqlToyCRUDService sqlToyCRUDService;

	@Autowired
	private InitDataService initDataService;

	/**
	 * 初次先执行此方法, 加载数据字典、订单等模拟数据sql文件
	 */
	@Test
	public void initData() {
		// 加载初始化数据脚本(最好手工执行数据初始化,便于演示缓存翻译功能)
		initDataService.createData("classpath:/mock/initDataSql.sql");
	}

	@Test
	public void mockOrderData() {
		// 模拟订单信息
		List<DeviceOrderInfoVO> orderInfos = new ArrayList<DeviceOrderInfoVO>();
		int max = 1000;
		// 查询全部员工
		List<StaffInfoVO> staffs = sqlToyLazyDao.findBySql("select STAFF_ID,STAFF_NAME,ORGAN_ID from sqltoy_staff_info",
				null, null, StaffInfoVO.class);
		StaffInfoVO staff;
		int[] days = { 10, 15, 20, 30, 60 };
		LocalDate nowTime = DateUtil.getDate();
		List<DictDetailVO> deviceTypes = sqlToyLazyDao.findBySql(
				"select * from sqltoy_dict_detail where dict_type=:dictType", new DictDetailVO(null, "DEVICE_TYPE"));
		// 采购、销售标志
		String[] psTypes = { "PO", "SO" };
		for (int i = 0; i < max; i++) {
			DeviceOrderInfoVO orderVO = new DeviceOrderInfoVO();
			staff = staffs.get(ShowCaseUtils.getRandomNum(staffs.size() - 1));
			orderVO.setBuyer("C000" + i);
			orderVO.setSaler("S000" + i);
			orderVO.setStaffId(staff.getStaffId());
			orderVO.setOrganId(staff.getOrganId());
			orderVO.setTransDate(nowTime);
			orderVO.setDeliveryTerm(
					DateUtil.asLocalDate(DateUtil.addDay(nowTime, days[ShowCaseUtils.getRandomNum(4)])));
			orderVO.setDeviceType(deviceTypes.get(ShowCaseUtils.getRandomNum(deviceTypes.size() - 1)).getDictKey());
			orderVO.setPsType(psTypes[ShowCaseUtils.getRandomNum(1)]);
			orderVO.setTotalCnt(new BigDecimal(ShowCaseUtils.getRandomNum(100, 400)));
			orderVO.setTotalAmt(orderVO.getTotalCnt().multiply(BigDecimal.valueOf(500)));
			orderVO.setStatus(1);
			orderVO.setCreateBy("S0001");
			orderVO.setUpdateBy("S0001");
			orderInfos.add(orderVO);
		}
		// 事务控制在service层上面的
		sqlToyCRUDService.saveAll(orderInfos);
	}

	/**
	 * 对树形表(类似于机构、产品分类等)组织:节点路径、节点等级、是否叶子节点 通用字段值,便于利用通用的sql实现递归查询(数据库无关)
	 */
	@Test
	public void wrapTreeTable() {
		// 根机构
		OrganInfoVO organVO = sqlToyLazyDao.load(new OrganInfoVO("100001"));
		TreeTableModel treeTableModel = new TreeTableModel(organVO);
		// 设置父节点
		treeTableModel.pidField("organPid");

		// 节点路径、节点等级、是否叶子节点，可以不用设置(默认值是nodeRoute、nodeLevel、isLeaf)
		treeTableModel.nodeLevelField("nodeLevel");
		treeTableModel.nodeRouteField("nodeRoute");
		treeTableModel.isLeafField("isLeaf");

		// 构造节点路径
		sqlToyLazyDao.wrapTreeTableRoute(treeTableModel);
	}

	/**
	 * 树形结构节点递归查询 1、通过节点路径nodeRoute查询下级子机构 2、通过节点等级控制查询多少级 3、通过是否叶子节点控制查询最底层还是非最底层
	 */
	@Test
	public void treeTableSearch() {
		// select * from sqltoy_organ_info t
		// where exists
		// (
		// select 1 from sqltoy_organ_info t1
		// where t1.`ORGAN_ID`=t.`ORGAN_ID`
		// -- 通过节点路径包含关系
		// and instr(t1.`NODE_ROUTE`,:nodeRoute)
		// -- 排除自身 and t.ORGAN_ID<>:organId
		// -- and t.NODE_LEVEL<=:nodeLevel+2
		// -- and t.IS_LEAF=0
		// )
		// 父节点机构，查询其下层所有节点,也可以用节点等级 和是否叶子节点来控制提取第几层
		OrganInfoVO parentOrgan = sqlToyLazyDao.load(new OrganInfoVO("100008"));
		List<OrganInfoVO> subOrgans = sqlToyLazyDao.findBySql("sqltoy_treeTable_search", parentOrgan);
		System.out.print(JSON.toJSONString(subOrgans));
	}

	/**
	 * 根据对象加载数据
	 */
	@Test
	public void loadByEntity() {
		OrganInfoVO parentOrgan = sqlToyLazyDao.load(new OrganInfoVO("100008"));
		System.out.print(JSON.toJSONString(parentOrgan));
	}

	/**
	 * 普通sql加载对象
	 */
	@Test
	public void loadBySql() {
		List<OrganInfoVO> subOrgans = sqlToyLazyDao.findBySql("sqltoy_treeTable_search", new String[] { "nodeRoute" },
				new Object[] { ",100008," }, OrganInfoVO.class);
		System.out.print(JSON.toJSONString(subOrgans));
	}

	/**
	 * 唯一性验证 返回false表示已经存在;返回true表示唯一可以插入
	 */
	@Test
	public void unique() {
		DictDetailVO dictDetail = new DictDetailVO("PC", "DEVICE_TYPE");
		// 第一个参数，放入需要验证的对象
		// 第二个参数，哪几个字段值进行唯一性检查
		boolean isExist = sqlToyLazyDao.isUnique(dictDetail, new String[] { "dictKey", "dictType" });
		// unique 返回false表示已经存在;返回true表示唯一可以插入
		// 在记录变更时,带入主键值，会自动判断是否是自身
		System.err.println(isExist);
	}

	/**
	 * 普通sql查询,本查询演示了缓存翻译、缓存条件匹配过滤(缓存在项目启动时加载配置，首次调用时加载数据,第二次调用时就会体现出缓存效率优势)
	 */
	@Test
	public void findBySql() {
		// 授权的机构
		String[] authedOrgans = { "100004", "100007" };
		List<DeviceOrderInfoVO> result = sqlToyLazyDao.findBySql("sqltoy_order_search",
				new String[] { "orderId", "authedOrganIds", "staffName", "beginDate", "endDate" },
				new Object[] { null, authedOrgans, "陈", LocalDate.parse("2018-09-01"), null }, DeviceOrderInfoVO.class);
		for (DeviceOrderInfoVO vo : result)
			System.err.println(JSON.toJSONString(vo));
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
		PaginationModel<StaffInfoVO> result = sqlToyLazyDao.findPageBySql(pageModel, "sqltoy_fastPage", staffVO);
		for (StaffInfoVO staff : result.getRows()) {
			System.err.println(JSON.toJSONString(staff));
		}
		// 第二次调用:条件一致，不执行count查询
		result = sqlToyLazyDao.findPageBySql(pageModel, "sqltoy_fastPage", staffVO);
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
		List<DeviceOrderInfoVO> result = sqlToyLazyDao.findTopBySql("sqltoy_order_search",
				new String[] { "orderId", "authedOrganIds", "staffName", "beginDate", "endDate" },
				new Object[] { null, authedOrgans, "陈", "2018-09-01", null }, DeviceOrderInfoVO.class, topSize);
		for (DeviceOrderInfoVO vo : result)
			System.err.println(JSON.toJSONString(vo));
	}

	@Test
	public void findTopByQuery() {
		// topSize:
		// 授权的机构
		String[] authedOrgans = { "100004", "100007" };
		String[] paramNames = { "orderId", "authedOrganIds", "staffName", "beginDate", "endDate" };
		Object[] paramValues = { null, authedOrgans, "陈", "2018-09-01", null };
		// QueryExecuter query = new QueryExecutor();
		double topSize = 20;
		List<DeviceOrderInfoVO> result = sqlToyLazyDao.findTopByQuery(new QueryExecutor("sqltoy_order_search")
				.names(paramNames).values(paramValues).resultType(DeviceOrderInfoVO.class), topSize).getRows();
		for (DeviceOrderInfoVO vo : result) {
			System.err.println(JSON.toJSONString(vo));
		}
	}

	/**
	 * 查询随机记录 randomSize:如果是大于1的数字,则取其整数部分;如果小于1则表示按比例提取
	 * 
	 * @throws InterruptedException
	 */
	@Test
	public void findByRandom() throws InterruptedException {
		while (true) {
			// 授权的机构
			String[] authedOrgans = { "100004", "100007" };
			double randomSize = 20;
			List<DeviceOrderInfoVO> result = sqlToyLazyDao.getRandomResult("sqltoy_order_search",
					new String[] { "orderId", "authedOrganIds", "staffName", "beginDate", "endDate" },
					new Object[] { null, authedOrgans, "陈", "2018-09-01", null }, DeviceOrderInfoVO.class, randomSize);
			for (DeviceOrderInfoVO vo : result) {
				// System.err.println(JSON.toJSONString(vo));
			}
			Thread.sleep(100000);
		}
	}

	@Test
	public void testColsRelativeCalculate() throws InterruptedException {
		List result = sqlToyLazyDao.findBySql("cols_relative_case", null);
		for (int i = 0; i < result.size(); i++) {
			System.err.println(JSON.toJSONString(result.get(i)));
		}
	}

	@Test
	public void testRowsRelativeCalculate() throws InterruptedException {
		List result = sqlToyLazyDao.findBySql("rows_relative_case", null);
		for (int i = 0; i < result.size(); i++) {
			System.err.println(JSON.toJSONString(result.get(i)));
		}
	}

	@Test
	public void testPivotList() throws InterruptedException {
		List result = sqlToyLazyDao.findBySql("pivot_case", null);
		for (int i = 0; i < result.size(); i++) {
			System.err.println(JSON.toJSONString(result.get(i)));
		}
	}

	@Test
	public void testUnpivotList() throws InterruptedException {
		// List result = (List) sqlToyLazyDao.findBySql("unpivot_case", new String[]
		// {"id"},new Object[] {"123"},OrderInfoVO.class);
		String sql = "select * from order where user_id=:id";
		List<OrderInfoVO> result = sqlToyLazyDao.findBySql(sql, new String[] { "id" }, new Object[] { "123" },
				OrderInfoVO.class);

		for (int i = 0; i < result.size(); i++) {
			System.err.println(JSON.toJSONString(result.get(i)));
		}
	}

	@Test
	public void testGroupSummary() throws InterruptedException {
		List result = sqlToyLazyDao.findBySql("group_summary_case", null);
		for (int i = 0; i < result.size(); i++) {
			System.err.println(JSON.toJSONString(result.get(i)));
		}
	}

	@Test
	public void testNamedParam() throws InterruptedException {
		String sql = "with d as (\r\n" + "                      select\r\n"
				+ "                          DATE_FORMAT(d.UPDATE_TIME,'%Y-%m-%d %H:%i:%s') CREATE_TIME,\r\n"
				+ "                          d.DICT_TYPE,\r\n" + "                          d.DICT_KEY,\r\n"
				+ "                          d.DICT_NAME,\r\n" + "                          d.SHOW_INDEX\r\n"
				+ "                      from sqltoy_dict_detail d\r\n"
				+ "                      where d.`STATUS`=1 \r\n" + "                    )\r\n"
				+ "                    select *\r\n" + "                    from d\r\n"
				+ "                    where 1=1 \r\n" + "                        #[and d.DICT_TYPE=:dictType]";
		PaginationModel result = sqlToyLazyDao.findPageBySql(new PaginationModel(), sql, new String[] { "dictType" },
				new Object[] { "TRANS_CODE" }, null);
		for (int i = 0; i < result.getRows().size(); i++) {
			System.err.println(JSON.toJSONString(result.getRows().get(i)));
		}
	}

	@Test
	public void testSearchBitType() throws InterruptedException {
		String sql = "select * from sqltoy_test";
		List<TestVO> result = sqlToyLazyDao.findBySql(sql, new TestVO());
		for (int i = 0; i < result.size(); i++) {
			System.err.println(JSON.toJSONString(result.get(i)));
		}
	}

	@Test
	public void testSaveBitType() throws InterruptedException {
		TestVO testVO = new TestVO();
		testVO.setId(true);
		testVO.setName("hello");
		testVO.setSallary(new BigInteger("2000"));
		sqlToyCRUDService.save(testVO);

	}
}
