package org.sagacity.sqltoy.model;

/**
 * sql注入校验等级
 */
public enum SqlInjectionLevel {
	// 严格的单词
	STRICT_WORD("STRICT_WORD"),
	// 宽松约束的单词
	RELAXED_WORD("RELAXED_WORD"),

	// sql语句关键词
	SQL_KEYWORD("SQL_KEYWORD");

	private final String code;

	private SqlInjectionLevel(String code) {
		this.code = code;
	}

	public String value() {
		return this.code;
	}

	@Override
	public String toString() {
		return code;
	}
}
