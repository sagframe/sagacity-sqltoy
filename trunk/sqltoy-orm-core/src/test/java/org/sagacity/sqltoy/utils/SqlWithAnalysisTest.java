package org.sagacity.sqltoy.utils;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.SqlWithAnalysis;

public class SqlWithAnalysisTest {
	@Test
	public void main() {
		String sql = "with t1 (a, b) as NOT  MATERIALIZED (select * from table),t2(c,d) as materialized(select name from ta) ";
		SqlWithAnalysis sqlWith = new SqlWithAnalysis(sql);
		System.err.println(sqlWith.getWithSql());
		for (String[] result : sqlWith.getWithSqlSet()) {
			for (String s : result) {
				System.err.println("[" + s + "]");
			}
		}
	}

	@Test
	public void withSql() {
		String sql = "with t1 (a, b) as not  materialized (select * from table),t2(c,d) as materialized(select name from ta) select * from t1";
		SqlWithAnalysis sqlWith = new SqlWithAnalysis(sql);
		System.err.println(sqlWith.getWithSql());
		for (String[] result : sqlWith.getWithSqlSet()) {
			for (String s : result) {
				System.err.println("[" + s + "]");
			}
		}
	}
}
