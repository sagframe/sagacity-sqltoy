package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;

import org.junit.jupiter.api.Test;

/**
 * URL路径解码必须保留字面加号:文件路径中的+不是表单编码空格
 */
public class FileUtilPathDecodeTest {

	@Test
	public void decodePathKeepsLiteralPlus() {
		assertEquals("a+b c", FileUtil.decodePath("a+b%20c"));
		assertEquals("a+b", FileUtil.decodePath("a%2Bb"));
		assertEquals("中文+路径", FileUtil.decodePath("%E4%B8%AD%E6%96%87+%E8%B7%AF%E5%BE%84"));
	}

	@Test
	public void getJarPathKeepsPlusAndDecodesPercentEscapes() throws Exception {
		String path = FileUtil.getJarPath(new URL("jar:file:/tmp/a%20b+c.jar!/demo/x.sql.xml"));
		assertTrue(path.endsWith("a b+c.jar"), "jar路径应保留+并解码%20:" + path);
		assertTrue(!path.contains("a b c.jar"), "+不能被解码成空格:" + path);
	}
}
