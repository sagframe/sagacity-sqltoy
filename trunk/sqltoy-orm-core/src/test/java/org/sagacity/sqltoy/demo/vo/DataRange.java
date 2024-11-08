package org.sagacity.sqltoy.demo.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class DataRange {
	private LocalDate beginDate;
	private LocalDate endDate;
	private LocalDateTime lastUpdateTime;
	public LocalDate getBeginDate() {
		return beginDate;
	}
	public void setBeginDate(LocalDate beginDate) {
		this.beginDate = beginDate;
	}
	public LocalDate getEndDate() {
		return endDate;
	}
	public void setEndDate(LocalDate endDate) {
		this.endDate = endDate;
	}
	public LocalDateTime getLastUpdateTime() {
		return lastUpdateTime;
	}
	public void setLastUpdateTime(LocalDateTime lastUpdateTime) {
		this.lastUpdateTime = lastUpdateTime;
	}
	
	
}
