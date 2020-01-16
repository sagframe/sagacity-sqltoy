package org.sagacity.sqltoy.utils;

import org.sagacity.sqltoy.config.model.SqlWithAnalysis;

public class SqlWithAnalysisTest {
	public static void main(String[] args) {
		String sql = "with      t1(a,b) as not  materialized (select * from table),t2 as materialized(select name from ta)";
		SqlWithAnalysis sqlWith = new SqlWithAnalysis(sql);
		for (String[] result : sqlWith.getWithSqlSet()) {
			for (String s : result) {
				System.err.println("[" + s + "]");
			}
		}
	}
}
