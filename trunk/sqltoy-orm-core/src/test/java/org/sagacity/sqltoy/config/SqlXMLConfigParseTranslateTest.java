package org.sagacity.sqltoy.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.ByteArrayInputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.FieldTranslate;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.Translate;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

/**
 * 回归测试：同一sql中多个translate元素的split配置互不继承——
 * 第一个配置split-sign的translate之后,无split配置的translate不应残留splitRegex/linkSign
 * (修复前被错误地按分隔符拆分翻译)
 */
public class SqlXMLConfigParseTranslateTest {

	private static SqlToyConfig parseTranslateXml(String translatesXml) throws Exception {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		DocumentBuilder builder = factory.newDocumentBuilder();
		Document doc = builder.parse(new ByteArrayInputStream(
				("<root>" + translatesXml + "</root>").getBytes("UTF-8")));
		NodeList translates = doc.getDocumentElement().getElementsByTagName("translate");
		SqlToyConfig config = new SqlToyConfig("mysql");
		SqlXMLConfigParse.parseTranslate(config, translates);
		return config;
	}

	private static Translate first(SqlToyConfig config, String column) {
		FieldTranslate fieldTranslate = config.getTranslateMap().get(column);
		return (fieldTranslate == null) ? null : fieldTranslate.translates[0];
	}

	@Test
	public void splitConfigNotLeakedToNextTranslate() throws Exception {
		SqlToyConfig config = parseTranslateXml(
				"<translate cache=\"cacheA\" columns=\"tags\" split-sign=\",\" link-sign=\";\"/>"
						+ "<translate cache=\"cacheB\" columns=\"status\"/>");
		Translate withSplit = first(config, "tags");
		Translate withoutSplit = first(config, "status");
		// 配置了split的元素正常解析
		assertEquals("\\,", withSplit.getExtend().splitRegex);
		assertEquals(";", withSplit.getExtend().linkSign);
		// 修复前:残留继承 -> splitRegex="\\,"、linkSign=";",status被错误拆分翻译
		assertNull(withoutSplit.getExtend().splitRegex, "实际:" + withoutSplit.getExtend().splitRegex);
		assertEquals(",", withoutSplit.getExtend().linkSign, "实际:" + withoutSplit.getExtend().linkSign);
	}

	@Test
	public void thirdTranslateWithDifferentSplitStillCorrect() throws Exception {
		SqlToyConfig config = parseTranslateXml(
				"<translate cache=\"cacheA\" columns=\"tags\" split-sign=\",\"/>"
						+ "<translate cache=\"cacheB\" columns=\"status\"/>"
						+ "<translate cache=\"cacheC\" columns=\"path\" split-sign=\"->\"/>");
		assertNull(first(config, "status").getExtend().splitRegex);
		// 第三个元素自身配置了"->",不受前面残留影响
		Translate arrow = first(config, "path");
		assertEquals("\\-\\>", arrow.getExtend().splitRegex);
		assertEquals("->", arrow.getExtend().linkSign);
	}
}
