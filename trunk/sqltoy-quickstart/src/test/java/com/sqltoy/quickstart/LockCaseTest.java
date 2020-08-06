/**
 * 
 */
package com.sqltoy.quickstart;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.sagacity.sqltoy.utils.DateUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.alibaba.fastjson.JSON;
import com.sqltoy.quickstart.service.StaffInfoService;
import com.sqltoy.quickstart.vo.StaffInfoVO;

/**
 * @project sqltoy-quickstart
 * @description 锁记录操作演示
 * @author zhong
 * @version v1.0, Date:2020-8-6
 * @modify 2020-8-6,修改说明
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = SqlToyApplication.class)
public class LockCaseTest {
	@Autowired
	StaffInfoService staffInfoService;

	/**
	 * 演示锁住记录查询出结果返回，然后对结果进行修改再保存，2次数据库交互
	 */
	@Test
	public void testLoadLock() {
		staffInfoService.updateLockStaff("S0007",
				"上海市黄浦区三大路254号402室" + DateUtil.formatDate(LocalDate.now(), "yyyy-MM-dd"));
	}

	/**
	 * 演示查询并锁住记录，然后直接修改，再将修改后结果返回，一次数据库交互完成全部动作
	 */
	@Test
	public void testUpdateFetch() {
		List<StaffInfoVO> result = staffInfoService.updateFetch();
		for (StaffInfoVO staff : result) {
			System.err.println(JSON.toJSONString(staff));
		}
	}
}
