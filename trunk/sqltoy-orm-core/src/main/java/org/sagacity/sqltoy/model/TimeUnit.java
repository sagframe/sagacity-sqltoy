package org.sagacity.sqltoy.model;

/**
 * @project sqltoy-orm
 * @description 时间单位,主要用于sql中filter中的时间加减计算的单位
 * @author zhongxuchen
 * @version v1.0,Date:2022-3-17
 */
public enum TimeUnit {
	// 毫秒
	MILLISECONDS("MILLISECONDS"),

	// 秒
	SECONDS("SECONDS"),

	// 分钟
	MINUTES("MINUTES"),

	HOURS("HOURS"),

	DAYS("DAYS"),

	MONTHS("MONTHS"),

	YEARS("YEARS");

	private final String timeUnit;

	private TimeUnit(String timeUnit) {
		this.timeUnit = timeUnit;
	}

	public String value() {
		return this.timeUnit;
	}

	@Override
	public String toString() {
		return this.timeUnit;
	}
}
