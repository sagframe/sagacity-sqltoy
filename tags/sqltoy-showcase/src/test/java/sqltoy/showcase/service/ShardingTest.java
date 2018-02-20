/**
 * 
 */
package sqltoy.showcase.service;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagacity.sqltoy.service.SqlToyCRUDService;
import org.sagacity.sqltoy.utils.DateUtil;
import org.sagacity.sqltoy.utils.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import sqltoy.showcase.CommonUtils;
import sqltoy.showcase.system.vo.StaffInfoVO;

/**
 * @project sqltoy-showcase
 * @description
 *              <p>
 *              对象分库分表测试
 *              </p>
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:ShardingTest.java,Revision:v1.0,Date:2017年11月7日
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "classpath:spring-context.xml" })
public class ShardingTest {
	/**
	 * 请查看StaffInfoVO 上面的@Sharding注解，其标记可以分库分表
	 * 请查看spring-sqltoy.xml hashDataSourceSharding 配置,具体策略配置开发者可以自行定义
	 * 请查看spring-context.xml中的数据源dataSource定义，共分了3个数据库，每个库中有SYS_STAFF_INFO 表和 SYS_STAFF_INFO_1表
	 */
	
	
	@Autowired
	private SqlToyCRUDService sqlToyCRUDService;
	
	@Test
	public void testDeleteAll() throws Exception {
		List<StaffInfoVO> staffInfoVOs = new ArrayList<StaffInfoVO>();
		for (int i = 0; i < 100; i++) {
			StaffInfoVO staffVO = new StaffInfoVO();
			staffVO.setStaffId(Integer.toString(i + 1));
			staffInfoVOs.add(staffVO);
		}
		sqlToyCRUDService.deleteAll(staffInfoVOs);
	}
	
	@Test
	public void testAddStaff() throws Exception {
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
	}
	
	@Test
	public void testLoadAll() throws Exception {
		List<StaffInfoVO> staffInfoVOs = new ArrayList<StaffInfoVO>();
		for (int i = 0; i < 100; i++) {
			StaffInfoVO staffVO = new StaffInfoVO();
			staffVO.setStaffId(Integer.toString(i + 1));
			staffInfoVOs.add(staffVO);
		}
		staffInfoVOs = sqlToyCRUDService.loadAll(staffInfoVOs);
		for (StaffInfoVO staff : staffInfoVOs)
			System.err.println(staff.getStaffName());

	}

	

	@Test
	public void testUpdateAll() throws Exception {
		List<StaffInfoVO> staffInfoVOs = new ArrayList<StaffInfoVO>();
		for (int i = 0; i < 100; i++) {
			StaffInfoVO staffVO = new StaffInfoVO();
			staffVO.setStaffId(Integer.toString(i + 1));
			staffInfoVOs.add(staffVO);
		}
		staffInfoVOs = sqlToyCRUDService.loadAll(staffInfoVOs);
		for (StaffInfoVO staff : staffInfoVOs) {
			staff.setStaffName(staff.getStaffName() + "XX");
		}
		System.err.println(staffInfoVOs.get(0).getStaffId());
		sqlToyCRUDService.delete(staffInfoVOs.get(0));
	}
}
