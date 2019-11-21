package org.sagacity.sqltoy.plugins;

import java.util.Arrays;

import org.sagacity.sqltoy.plugins.function.FunctionUtils;

public class FunctionUtilTest {
	public static void main(String[] args) {
		FunctionUtils.setFunctionConverts(Arrays.asList(FunctionUtils.functions));
		String sql = "select group_concat(name separator ',') from table";

		String dialectSql = FunctionUtils.getDialectSql(sql, "mysql");
		System.err.println(dialectSql);
	}
}
