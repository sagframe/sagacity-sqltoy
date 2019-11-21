package org.sagacity.sqltoy.plugins;

import java.util.Arrays;

import org.sagacity.sqltoy.plugins.function.FunctionUtils;

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
			funPackage.concat("GroupConcat") };

	public static void main(String[] args) {
		FunctionUtils.setFunctionConverts(Arrays.asList(functions));
		String sql = "select group_concat(name separator ',') from table";

		String dialectSql = FunctionUtils.getDialectSql(sql, "postgresql");
		System.err.println(dialectSql);
	}
}
