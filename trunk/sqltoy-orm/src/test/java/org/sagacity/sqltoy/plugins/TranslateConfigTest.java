package org.sagacity.sqltoy.plugins;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.translate.TranslateConfigParse;

public class TranslateConfigTest {
	@Test
	public void getTranslateFile() throws Exception {
		List files = TranslateConfigParse.getTranslateFiles("classpath:");
		for (int i = 0; i < files.size(); i++) {
			System.err.println(files.get(i));
		}
	}
}
