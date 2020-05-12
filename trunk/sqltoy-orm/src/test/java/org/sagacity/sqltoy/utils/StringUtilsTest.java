/**
 * 
 */
package org.sagacity.sqltoy.utils;

import org.sagacity.sqltoy.demo.vo.StaffInfoVO;
import org.sagacity.sqltoy.link.EntityQuery;

/**
 * @author zhongxuchen
 *
 */
public class StringUtilsTest {
	public static void main(String[] args) {
		//where中写条件，利用sqltoy自身特性#[] 实现值为null的判断，简化sql组装
		
		//第一种模式:直接传参
		EntityQuery query = new EntityQuery(StaffInfoVO.class)
				.where("#[staff_name like ?] #[and status in (?)]").values("陈", new Integer[] { 1, 2 });

		//第二种模式:对象传参
		StaffInfoVO staffVO = new StaffInfoVO();
		staffVO.setStaffName("陈");
		staffVO.setStatusAry(1, 2);
		query = new EntityQuery(StaffInfoVO.class).where("#[staff_name like :staffName] #[and status in (:statusAry)]")
				.values(staffVO);
	}
}
