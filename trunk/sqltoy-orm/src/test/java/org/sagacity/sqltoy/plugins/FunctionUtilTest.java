package org.sagacity.sqltoy.plugins;

import java.util.Arrays;

import org.sagacity.sqltoy.plugins.function.FunctionUtils;
import org.sagacity.sqltoy.utils.CommonUtils;

public class FunctionUtilTest {
	private final static String funPackage = "org.sagacity.sqltoy.plugins.function.impl.";

	// 提供默认函数配置
	public final static String[] functions = { /*
												 * funPackage.concat("SubStr"), funPackage.concat("Trim"),
												 * funPackage.concat("Instr"), funPackage.concat("Concat"),
												 * funPackage.concat("ConcatWs"), funPackage.concat("Nvl"),
												 * funPackage.concat("DateFormat"), funPackage.concat("Now"),
												 * funPackage.concat("Length"), funPackage.concat("ToChar"),
												 * funPackage.concat("If"),
												 */
			funPackage.concat("Instr")
			// funPackage.concat("GroupConcat")
	};

	public static void main(String[] args) {
		FunctionUtils.setFunctionConverts(Arrays.asList(functions));
		String sql = CommonUtils.readFileAsString("classpath:/scripts/instr_function.txt", "UTF-8");
		// String sql = "select concat('\\'', t.`ORGAN_ID`, '\\'') from sys_organ_info t
		// ";

		String dialectSql = FunctionUtils.getDialectSql(sql, "oracle");
		System.err.println(dialectSql);
	}
}
