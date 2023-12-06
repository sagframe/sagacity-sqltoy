/**
 * 
 */
package org.sagacity.sqltoy.utils;

import java.io.FileInputStream;
import java.io.FileNotFoundException;

import org.junit.jupiter.api.Test;

/**
 * @project sagacity-sqltoy
 * @description 请在此说明类的功能
 * @author zhong
 * @version v1.0, Date:2023年12月6日
 * @modify 2023年12月6日,修改说明
 */
public class FileUtilTest {
	@Test
	public void testInputStreamToStr() {
		try {
			String str = FileUtil.inputStreamToStr(new FileInputStream("D:/test.txt"), "UTF-8");
			System.err.println("[" + str + "]");
			System.err.println("[" + str.replaceAll("\r|\n", "") + "]");
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
