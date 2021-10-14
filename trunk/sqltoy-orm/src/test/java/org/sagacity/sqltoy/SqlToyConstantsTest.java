package org.sagacity.sqltoy;

import java.util.LinkedHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.alibaba.fastjson.JSON;

public class SqlToyConstantsTest {
	@Test
	public void testParseParams() {
		String template="你好${ 姓名},请于${bizDate }来开会!";
		Pattern paramPattern = Pattern
				.compile("\\$\\{\\s*[0-9a-zA-Z\u4e00-\u9fa5]+((\\.|\\_)[0-9a-zA-Z\u4e00-\u9fa5]+)*(\\[\\d*(\\,)?\\d*\\])?\\s*\\}");
		LinkedHashMap<String, String> paramsMap = new LinkedHashMap<String, String>();
		Matcher m = paramPattern.matcher(template);
		String group;
		while (m.find()) {
			group = m.group();
			// key as ${name} value:name
			paramsMap.put(group, group.substring(2, group.length() - 1).trim());
		}
		System.err.println(JSON.toJSONString(paramsMap));
	}
}
