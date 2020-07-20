package com.sqltoy.quickstart;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * 
 * @project sqltoy-quickstart
 * @description 通过员工信息表来演示常规的crud
 * @author zhongxuchen
 * @version v1.0, Date:2020年7月17日
 * @modify 2020年7月17日,修改说明
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = SqlToyApplication.class)
public class StaffInfoServiceTest {
	@Test
	public void testSave() {

	}
	
	@Test
	public void testUpdate() {

	}
	
	@Test
	public void testSaveOrUpdate() {

	}
}
