/**
 * 
 */
package com.sagframe.sqltoy.showcase;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.sagframe.sqltoy.SqlToyApplication;

/**
 * @project sqltoy-showcase
 * @description 链式(流式)操作演示,本质还是常规的多参数接口调用，只是通过链式设置参数提供更优雅的执行模式
 *              链式的弊端也是很明显的,容易漏设置参数，不如接口方法对参数约束的严谨
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:LinkCaseServiceTest.java,Revision:v1.0,Date:2019年7月12日
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = SqlToyApplication.class)
public class LinkCaseServiceTest {

}
