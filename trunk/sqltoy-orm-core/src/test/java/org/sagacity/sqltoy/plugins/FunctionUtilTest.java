package org.sagacity.sqltoy.plugins;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.plugins.function.FunctionUtils;
import org.sagacity.sqltoy.utils.FileUtil;

public class FunctionUtilTest {
	private final static String funPackage = "org.sagacity.sqltoy.plugins.function.impl.";

	// 提供默认函数配置
	private final static String[] functions = { /*
												 * funPackage.concat("SubStr"), funPackage.concat("Trim"),
												 * funPackage.concat("Instr"), funPackage.concat("Concat"),
												 * funPackage.concat("ConcatWs"), funPackage.concat("Nvl"),
												 * 
												 * funPackage.concat("Length"), funPackage.concat("ToChar"),
												 * funPackage.concat("If"),
												 */
			//funPackage.concat("DateFormat"), 
			funPackage.concat("Instr")
			//, funPackage.concat("Concat")
			// funPackage.concat("GroupConcat")
	};

	@Test
	public void testInstr() {
		FunctionUtils.setFunctionConverts(Arrays.asList(functions));
		String sql = FileUtil.readFileAsStr("classpath:/scripts/instr_function.txt", "UTF-8");
		String dialectSql = FunctionUtils.getDialectSql(sql, "oracle");
		System.err.println(dialectSql);
	}

	@Test
	public void testConcat() {
		FunctionUtils.setFunctionConverts(Arrays.asList(functions));
		String sql = "select concat('\\'', t.`ORGAN_ID`, '\\'') from sys_organ_info t";
		String dialectSql = FunctionUtils.getDialectSql(sql, "oracle");
		System.err.println(dialectSql);
	}

	@Test
	public void testPosition() {
		FunctionUtils.setFunctionConverts(Arrays.asList(functions));
		String sql = "if(position(t.valBeginString in l.CONTENTS)>0,position(t.valEndString in substr(l.CONTENTS,position(t.valBeginString in l.CONTENTS)+length(t.valBeginString))),1) valEndIndex,";
		String dialectSql = FunctionUtils.getDialectSql(sql, "mysql");
		System.err.println(dialectSql);
	}
}
