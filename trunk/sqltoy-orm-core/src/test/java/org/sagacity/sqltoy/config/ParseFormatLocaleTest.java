package org.sagacity.sqltoy.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.FormatModel;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.utils.DateUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * 回归测试:date-format的format含英文月份/星期符号(MMM、EEE系列)且locale缺失时,
 * 解析器自动按英文区域处理(输出Aug而非"8月");显式locale优先,纯数字格式不受影响
 */
public class ParseFormatLocaleTest {

	private FormatModel parseFormat(String format, String locale) throws Exception {
		StringBuilder xml = new StringBuilder();
		xml.append("<sqltoy xmlns=\"http://www.sagframe.com/schema/sqltoy\">");
		xml.append("<sql id=\"fmt_demo\"><value><![CDATA[select trans_date from orders]]></value>");
		xml.append("<date-format columns=\"trans_date\" format=\"").append(format).append("\"");
		if (locale != null) {
			xml.append(" locale=\"").append(locale).append("\"");
		}
		xml.append("/></sql></sqltoy>");
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		dbf.setNamespaceAware(true);
		Document doc = dbf.newDocumentBuilder()
				.parse(new ByteArrayInputStream(xml.toString().getBytes(StandardCharsets.UTF_8)));
		NodeList sqlList = doc.getDocumentElement().getElementsByTagName("sql");
		return SqlXMLConfigParse.parseSingleSql((Element) sqlList.item(0), null).getFormatModels().get(0);
	}

	@Test
	public void englishMonthPatternDefaultsToEnglish() throws Exception {
		FormatModel fmt = parseFormat("MMM d, yyyy", null);
		assertEquals(Locale.ENGLISH, fmt.getLocale());
		Date base = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse("2026-08-18 12:30:45");
		assertEquals("Aug 18, 2026", DateUtil.formatDate(base, fmt.getFormat(), fmt.getLocale()));
	}

	@Test
	public void englishFullMonthPatternDefaultsToEnglish() throws Exception {
		FormatModel fmt = parseFormat("MMMM d, yyyy", null);
		assertEquals(Locale.ENGLISH, fmt.getLocale());
	}

	@Test
	public void oracleDashPatternDefaultsToEnglish() throws Exception {
		FormatModel fmt = parseFormat("dd-MMM-yyyy", null);
		assertEquals(Locale.ENGLISH, fmt.getLocale());
	}

	@Test
	public void weekdayPatternDefaultsToEnglish() throws Exception {
		FormatModel fmt = parseFormat("EEE, MMM d, yyyy", null);
		assertEquals(Locale.ENGLISH, fmt.getLocale());
	}

	@Test
	public void explicitLocaleNotOverridden() throws Exception {
		FormatModel fmt = parseFormat("MMM d, yyyy", "zh-CN");
		assertEquals(new Locale("zh", "CN"), fmt.getLocale());
	}

	@Test
	public void numericPatternKeepsLocaleNull() throws Exception {
		FormatModel fmt = parseFormat("yyyy-MM-dd HH:mm:ss", null);
		assertNull(fmt.getLocale());
	}
}
