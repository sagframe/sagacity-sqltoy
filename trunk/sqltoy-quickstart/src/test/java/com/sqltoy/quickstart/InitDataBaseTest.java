package com.sqltoy.quickstart;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * @project sqltoy-quickstart
 * @description 初始化数据库
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:InitDataBaseTest.java,Revision:v1.0,Date:2020年1月22日
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = SqlToyApplication.class)
public class InitDataBaseTest {

	@Test
	public void testInitDB() {

	}
}
