/**
 * 
 */
package sqltoy.showcase.dao;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagacity.sqltoy.model.PaginationModel;
import org.sagacity.sqltoy.utils.DateUtil;
import org.sagacity.sqltoy.utils.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.alibaba.fastjson.JSON;

import sqltoy.showcase.CommonUtils;
import sqltoy.showcase.system.dao.StaffInfoDao;
import sqltoy.showcase.system.vo.Goods;
import sqltoy.showcase.system.vo.GoodsParam;
import sqltoy.showcase.system.vo.OrganInfoVO;
import sqltoy.showcase.system.vo.StaffInfoVO;

/**
 * @project sqltoy-showcase
 * @description
 *              <p>
 *              请在此说明类的功能
 *              </p>
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:LinkDaoTest.java,Revision:v1.0,Date:2017年10月30日
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "classpath:spring-context.xml" })
public class LinkDaoTest {
	/**
	 * 本测试提供针对org.sagacity.sqltoy.link下面链式操作的测试,具体功能请参见link包下面的类实现
	 * 
	 */

	@Autowired
	private StaffInfoDao staffInfoDao;

	@Test
	public void findPage() throws Exception {
		StaffInfoVO staffInfoVO = new StaffInfoVO();
		staffInfoVO.setStaffName("李");
		PaginationModel pageModel = staffInfoDao.findPage(new PaginationModel(), staffInfoVO);
		System.err.println(pageModel.getRecordCount());
	}

	@Test
	public void saveStaff() throws Exception {
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
				staffInfoDao.saveOrUpdateStaff(staffVOs);
				staffVOs.clear();
			}
		}
	}

	@Test
	public void findES() throws Exception {
		OrganInfoVO organInfoVO = new OrganInfoVO();
		organInfoVO.setOperateDate(DateUtil.parse("2018-03-29 09:19:17", "yyyy-MM-dd HH:mm:ss"));
		// organInfoVO.setStaffName("李");
		List<OrganInfoVO> result = staffInfoDao.findES(organInfoVO);
		for (OrganInfoVO organ : result) {
			System.err.println(organ.getCount());
		}

	}

	@Test
	public void findESPage() throws Exception {
		OrganInfoVO organInfoVO = new OrganInfoVO();
		// organInfoVO.setStaffName("李");
		PaginationModel pageModel = staffInfoDao.findESPage(new PaginationModel(), organInfoVO);
		System.err.println(pageModel.getRecordCount());
		for (OrganInfoVO organ : (List<OrganInfoVO>) pageModel.getRows()) {
			System.err.println(organ.getOrganId() + " organName:=" + organ.getOrganName());
		}

	}

	@Test
	public void findESBySqlPage() throws Exception {
		GoodsParam goodsParam = new GoodsParam();
		goodsParam.setGoodsCateId("1515376757743370152561");
		List<String> goodsCateIds = new ArrayList<>();
		goodsCateIds.add("1515376757743370152561");
		PaginationModel paginationModel = new PaginationModel();
		goodsParam.setPage(paginationModel);
		goodsParam.setGoodsCateIds(goodsCateIds);
		goodsParam.setKeyword("资源");
		PaginationModel pageModel = new PaginationModel();
		PaginationModel pageGoods = staffInfoDao.findESPageBySql(pageModel, goodsParam);
		List<Goods> goodsResults = pageGoods.getRows();
		System.out.println(JSON.toJSONString(goodsResults));

	}

	@Test
	public void findESAggs() throws Exception {
		OrganInfoVO organInfoVO = new OrganInfoVO();
		// organInfoVO.setStaffName("李");
		List<OrganInfoVO> result = staffInfoDao.findESAggs(new PaginationModel(), organInfoVO);
		for (OrganInfoVO organ : result) {
			System.err.println(organ.getOrganId() + " organName:=" + organ.getOrganName());
		}

	}

	@Test
	public void findESSuggest() throws Exception {
		OrganInfoVO organInfoVO = new OrganInfoVO();
		// organInfoVO.setStaffName("李");
		List<OrganInfoVO> result = staffInfoDao.findESSuggest(new PaginationModel(), organInfoVO);
		for (OrganInfoVO organ : result) {
			System.err.println(organ.getOrganId() + " keywords:=" + organ.getKeywords());
		}

	}
}
