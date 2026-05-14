/**
 * 
 */
package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
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

	@Test
	public void testFormatPath() {
		System.err.println(FileUtil.formatPath("a\\/b//c\\\\"));
		//assertEquals("a" + File.separator + "b" + File.separator + "c", FileUtil.formatPath("a\\b/c\\"));
		System.err.println(FileUtil.formatPath(null));
		System.err.println(FileUtil.formatPath(""));

		// 各种混合分隔符
		System.err.println(FileUtil.formatPath("a/b\\c"));
		System.err.println(FileUtil.formatPath("a//b\\\\c"));
		System.err.println(FileUtil.formatPath("a\\/b//c\\\\"));

		// Windows 盘符
		System.err.println("\"C:/test\\\\file.txt\" 变成 "+FileUtil.formatPath("C:/test\\file.txt"));

		// 连续分隔符
		System.err.println(FileUtil.formatPath("////\\\\\\\\"));
	}
}
