package org.sagacity.sqltoy.config.model;

/**
 * 大小写类型
 */
public enum CaseType {
	DEFAULT("DEFAULT"),

	UPPER("UPPER"),

	LOWER("LOWER");

	private final String value;

	private CaseType(String caseType) {
		this.value = caseType;
	}

	public String getValue() {
		return this.value;
	}

	@Override
	public String toString() {
		return this.value;
	}

	/**
	 * @todo 转换给定字符串为枚举主键策略
	 * @param caseType
	 * @return
	 */
	public static CaseType getCaseType(String caseType) {
		if (caseType != null) {
			if (caseType.equalsIgnoreCase(LOWER.getValue())) {
				return LOWER;
			}
			if (caseType.equalsIgnoreCase(UPPER.getValue())) {
				return UPPER;
			}
		}
		return DEFAULT;
	}
}
