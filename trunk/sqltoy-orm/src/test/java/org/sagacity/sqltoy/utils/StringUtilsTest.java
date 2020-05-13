/**
 * 
 */
package org.sagacity.sqltoy.utils;

import org.sagacity.sqltoy.demo.vo.StaffInfoVO;
import org.sagacity.sqltoy.model.EntityQuery;

/**
 * @author zhongxuchen
 *
 */
public class StringUtilsTest {
	public static void main(String[] args) {
		// where中写条件，利用sqltoy自身特性#[] 实现值为null的判断，简化sql组装
		// 第一种模式:直接传参
		EntityQuery query = new EntityQuery();
		StringUtilsTest test = new StringUtilsTest();
		test.selectList(query);
//		// 第二种模式:对象传参
//		StaffInfoVO staffVO = new StaffInfoVO();
//		staffVO.setStaffName("陈");
//		staffVO.setStatusAry(1, 2);
//		query = new EntityQuery<StaffInfoVO>().where("#[staff_name like :staffName] #[and status in (:statusAry)]")
//				.values(staffVO);
	}

	public void selectList(EntityQuery<StaffInfoVO> query) {
		Class result = BeanUtil.getSuperClassGenricType(query.getClass(), 0);
		System.out.println(result.getName());
	}
}
