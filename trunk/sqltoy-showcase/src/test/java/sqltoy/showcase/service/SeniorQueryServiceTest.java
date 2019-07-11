/**
 * 
 */
package sqltoy.showcase.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagacity.sqltoy.model.PaginationModel;
import org.sagacity.sqltoy.service.SqlToyCRUDService;
import org.sagacity.sqltoy.showcase.CommonUtils;
import org.sagacity.sqltoy.utils.DateUtil;
import org.sagacity.sqltoy.utils.DebugUtil;
import org.sagacity.sqltoy.utils.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import sqltoy.showcase.system.service.SeniorQueryService;
import sqltoy.showcase.system.vo.ShardingHisVO;
import sqltoy.showcase.system.vo.ShardingRealVO;
import sqltoy.showcase.system.vo.StaffInfoVO;
import sqltoy.showcase.system.vo.SummaryCaseVO;
import sqltoy.showcase.system.vo.UnpivotDataVO;

/**
 * @project sqltoy-showcase
 * @description
 *              <p>
 *              高级查询测试
 *              </p>
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:SearchServiceTest.java,Revision:v1.0,Date:2015年12月23日
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "classpath:spring-context.xml" })
public class SeniorQueryServiceTest {
	/**
	 * 定义全局日志
	 */
	protected final static Logger logger = LogManager.getLogger(SeniorQueryServiceTest.class);

	@Autowired
	private SeniorQueryService seniorQueryService;

	@Autowired
	private SqlToyCRUDService sqlToyCRUDService;

	/**
	 * 列传行(将3列数据变成了3行)
	 * 
	 * @throws Exception
	 */
	@Test
	public void testSearchUnpivot() throws Exception {
		sqlToyCRUDService.truncate(UnpivotDataVO.class);
		List<UnpivotDataVO> vos = new ArrayList<UnpivotDataVO>();
		for (int i = 0; i < 3; i++) {
			UnpivotDataVO vo = new UnpivotDataVO();
			vo.setTransDate(DateUtil.addDay(new Date(), -i));
			vo.setPersonAmount(10d * CommonUtils.getRandomNum(10 * (i + 1)));
			vo.setCompanyAmount(15d * CommonUtils.getRandomNum(10 * (i + 1)));
			vo.setTotalAmount(vo.getCompanyAmount() + vo.getPersonAmount());
			vos.add(vo);
		}
		// 批量保存
		sqlToyCRUDService.saveAll(vos);
		List<UnpivotDataVO> result = seniorQueryService.findUnpivotList();
		System.err.println("   交易日期       交易类型       交易金额     ");
		for (UnpivotDataVO vo : result) {
			System.err.println("[" + DateUtil.formatDate(vo.getTransDate(), "yyyy-MM-dd") + "],[" + vo.getAmountType()
					+ "],[" + vo.getTransAmount() + "]");
		}
	}

	/**
	 * 列传行(将3列数据变成了3行)
	 * 
	 * @throws Exception
	 */
	@Test
	public void testSummary() throws Exception {
		// 清除数据
		sqlToyCRUDService.truncate(SummaryCaseVO.class);
		String[] transCode = { "S001", "S002", "S003" };
		String[] transChannel = { "WEIXIN", "CUPS", "ALIPAY", "OTHER" };
		// 模拟构造数据
		List<SummaryCaseVO> vos = new ArrayList<SummaryCaseVO>();
		for (int i = 0; i < 200; i++) {
			SummaryCaseVO vo = new SummaryCaseVO();
			vo.setTransCode(transCode[CommonUtils.getRandomNum(transCode.length)]);
			vo.setTransChannel(transChannel[CommonUtils.getRandomNum(transChannel.length)]);
			vo.setTransAmt(Double.valueOf(1000 + 15 * CommonUtils.getRandomNum(20)));
			vo.setTransDate(DateUtil.addDay(new Date(), -CommonUtils.getRandomNum(5)));
			vos.add(vo);
		}
		// 批量保存
		sqlToyCRUDService.saveAll(vos);
		// 汇总查询(三次执行验证dataSourceSharding效果,参见sqltoy-system.sql
		// id="sys_summarySearch" sharding配置)
		List<List> result = seniorQueryService.summarySearch();
		result = seniorQueryService.summarySearch();
		result = seniorQueryService.summarySearch();
		System.err.println("   交易渠道       交易代码       交易金额     ");
		for (List row : result) {
			System.err.println("[" + row.get(0) + "],     [" + row.get(1) + "],     [" + row.get(2) + "]");
		}
	}

	/**
	 * 行转列
	 * 
	 * @throws Exception
	 */
	@Test
	public void testPivotSummary() throws Exception {
		List<List> result = seniorQueryService.findPivotList();
		for (List row : result) {
			System.err.println();
			DebugUtil.printAry(row, ",", false);
		}
	}

	/**
	 * 分页查询演示
	 * 
	 * @throws Exception
	 */
	@Test
	public void testFindPage() throws Exception {
		sqlToyCRUDService.truncate(StaffInfoVO.class);
		List<StaffInfoVO> staffVOs = new ArrayList<StaffInfoVO>();
		String[] postType = { "1", "2", "3", "4" };
		String[] sexType = { "F", "M", "X" };
		String staffNames = "赵钱孙李周吴郑王冯陈楮卫蒋沈韩杨朱秦尤许何吕施张孔曹严华金魏陶姜戚谢邹喻柏水窦章云苏潘葛奚范彭郎鲁韦昌马苗凤花方俞任袁柳酆鲍史唐费廉岑薛雷贺倪汤滕殷罗毕郝邬安常乐于时傅皮卞齐康伍余元卜顾孟平黄";
		String names = "俞倩倪倰偀偲妆佳亿仪寒宜女奴妶好妃姗姝姹姿婵姑姜姣嫂嫦嫱姬娇娟嫣婕婧娴婉姐姞姯姲姳娘娜妹妍妙妹娆娉娥媚媱嫔婷玟环珆珊珠玲珴瑛琼瑶瑾瑞珍琦玫琪琳环琬瑗琰薇珂芬芳芯花茜荭荷莲莉莹菊芝萍燕苹荣草蕊芮蓝莎菀菁苑芸芊茗荔菲蓉英蓓蕾";

		int end = 100;
		for (int i = 0; i < end; i++) {
			StaffInfoVO staffVO = new StaffInfoVO();
			staffVO.setStaffId(Integer.toString(i + 1));
			staffVO.setStaffCode("S" + StringUtil.addLeftZero2Len(Integer.toString(i + 1), 4));
			int index = CommonUtils.getRandomNum(1, staffNames.length() - 1);
			int nameIndex = CommonUtils.getRandomNum(1, names.length() - 1);
			staffVO.setStaffName(
					staffNames.substring(index - 1, index) + names.substring(nameIndex - 1, nameIndex) + i);
			staffVO.setBirthday(DateUtil.getNowTime());
			staffVO.setOperator("admin");
			staffVO.setOperateDate(DateUtil.getNowTime());
			staffVO.setOrganId(Integer.toString(CommonUtils.getRandomNum(2, 10)));
			staffVO.setPost(postType[CommonUtils.getRandomNum(postType.length)]);
			// 按照千人比例取性别(千分之五为不确定性别)
			staffVO.setSexType(sexType[CommonUtils.getProbabilityIndex(new int[] { 493, 498, 5 })]);
			staffVO.setStatus("1");
			staffVOs.add(staffVO);
			if (((i + 1) % 100) == 0 || i == end - 1) {
				sqlToyCRUDService.saveOrUpdateAll(staffVOs);
				staffVOs.clear();
			}
		}

		PaginationModel result = seniorQueryService.findStaffInfo(new PaginationModel(), new String[] { "staffName" },
				new Object[] { "张" }, StaffInfoVO.class);
		System.err.println(result.getRecordCount());
	}

	/**
	 * 快速分页、缓存翻译、分页优化查询演示
	 * 
	 * @throws Exception
	 */
	@Test
	public void testFindFastPage() throws Exception {
		// 先执行一次查询加载缓存,方便真实反映缓存翻译的效果
		PaginationModel result0 = seniorQueryService.findStaffInfo(new PaginationModel(), new String[] { "staffName" },
				new Object[] { "张" }, StaffInfoVO.class);
		DebugUtil.beginTime("start");
		PaginationModel result = seniorQueryService.findStaffInfo(new PaginationModel(), new String[] { "staffName" },
				new Object[] { "陈" }, StaffInfoVO.class);
		DebugUtil.endTime("start");
		DebugUtil.beginTime("start1");
		PaginationModel result1 = seniorQueryService.findStaffInfo(new PaginationModel(), new String[] { "staffName" },
				new Object[] { "陈" }, StaffInfoVO.class);
		DebugUtil.endTime("start1");
	}

	/**
	 * 取top记录数
	 * 
	 * @throws Exception
	 */
	@Test
	public void testFindTop() throws Exception {
		StaffInfoVO staffInfoVO = new StaffInfoVO();
		staffInfoVO.setStaffName("康");
		staffInfoVO.setStatus("-1");
		List<StaffInfoVO> staffInfoVOs = seniorQueryService.findTopStaff(10, staffInfoVO);
		for (StaffInfoVO staff : staffInfoVOs) {
			System.out.println("staffId=" + staff.getStaffId() + ",staffName=" + staff.getStaffName() + ";organName="
					+ staff.getOrganName());
		}
	}

	/**
	 * 取random随机记录数
	 * 
	 * @throws Exception
	 */
	@Test
	public void testGetRandom() throws Exception {
		StaffInfoVO staffInfoVO = new StaffInfoVO();
		staffInfoVO.setStaffName("陈");
		List<StaffInfoVO> staffInfoVOs = seniorQueryService.findRandomStaff(0.2, staffInfoVO);
		for (StaffInfoVO staff : staffInfoVOs) {
			System.out.println("staffId=" + staff.getStaffId() + ",staffName=" + staff.getStaffName() + ";organName="
					+ staff.getOrganName());
		}

	}

	/**
	 * 分库分表sharding
	 * 
	 * @throws Exception
	 */
	@Test
	public void testFindSharding() throws Exception {
		List<ShardingRealVO> realVOs = new ArrayList<ShardingRealVO>();
		for (int i = 0; i < 100; i++) {
			ShardingRealVO realVO = new ShardingRealVO();
			realVO.setId(new BigDecimal(i));
			realVO.setComments("real" + i);
			realVO.setStaffId("S" + CommonUtils.getRandomNum(100));
			realVO.setPostType("POST_MASTER");
			realVO.setCreateTime(DateUtil.addDay(new Date(), -CommonUtils.getRandomNum(15)));
			realVOs.add(realVO);
		}
		sqlToyCRUDService.saveOrUpdateAll(realVOs);
		List<ShardingHisVO> hisVOs = new ArrayList<ShardingHisVO>();
		for (int i = 101; i < 500; i++) {
			ShardingHisVO hisVO = new ShardingHisVO();
			hisVO.setId(new BigDecimal(i));
			hisVO.setComments("real" + i);
			hisVO.setStaffId("S" + CommonUtils.getRandomNum(100));
			hisVO.setPostType("POST_MASTER");
			hisVO.setCreateTime(DateUtil.addDay(new Date(), -CommonUtils.getRandomNum(15, 100)));
			hisVOs.add(hisVO);
		}
		sqlToyCRUDService.saveOrUpdateAll(hisVOs);
		PaginationModel pageModel = new PaginationModel();

		// 14天前,则查询历史表
		// Date beginTime = DateUtil.addDay(new Date(), -30);
		// Date endTime = DateUtil.addDay(new Date(), -14);
		// 14天以内,则查询实时表
		Date beginTime = DateUtil.addDay(new Date(), -13);
		Date endTime = DateUtil.addDay(new Date(), -1);
		PaginationModel results = seniorQueryService.findSharding(pageModel, new String[] { "beginTime", "endTime" },
				new Object[] { beginTime, endTime }, ShardingRealVO.class);
		for (ShardingRealVO sharding : (List<ShardingRealVO>) results.getRows()) {
			System.out.println("staffId=" + sharding.getStaffId() + ",createTime=" + sharding.getCreateTime()
					+ ";comments=" + sharding.getComments());
		}

	}
}
