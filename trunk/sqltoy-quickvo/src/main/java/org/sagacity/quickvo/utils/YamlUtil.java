package org.sagacity.quickvo.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.HashMap;

import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLParser;

/**
 * @project sagacity-quickvo
 * @description 提供基于YAML配置文件的解析
 * @author zhongxuchen $<a href="mailto:zhongxuchen@gmail.com">联系作者</a>$
 * @version $id:YamlUtil.java,Revision:v1.0,Date:Oct 19, 2007 10:09:42 AM $
 */
public class YamlUtil {

	/**
	 * @TODO 加载yml文件
	 * @param constantMap
	 * @param ymlFile
	 */
	public static void loadYml(HashMap<String, String> constantMap, File ymlFile) {
		final String DOT = ".";
		try {
			YAMLFactory yamlFactory = new YAMLFactory();
			YAMLParser parser = yamlFactory
					.createParser(new InputStreamReader(new FileInputStream(ymlFile), Charset.forName("UTF-8")));
			String key = "";
			String value = null;
			JsonToken token = parser.nextToken();
			while (token != null) {
				if (JsonToken.START_OBJECT.equals(token)) {
					// do nothing
				} else if (JsonToken.FIELD_NAME.equals(token)) {
					if (key.length() > 0) {
						key = key + DOT;
					}
					key = key + parser.getCurrentName();

					token = parser.nextToken();
					if (JsonToken.START_OBJECT.equals(token)) {
						continue;
					}
					value = parser.getText();
					constantMap.put(key.trim(), value.trim());
					int dotOffset = key.lastIndexOf(DOT);
					if (dotOffset > 0) {
						key = key.substring(0, dotOffset);
					}
					value = null;
				} else if (JsonToken.END_OBJECT.equals(token)) {
					int dotOffset = key.lastIndexOf(DOT);
					if (dotOffset > 0) {
						key = key.substring(0, dotOffset);
					} else {
						key = "";
					}
				}
				token = parser.nextToken();
			}
			parser.close();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
