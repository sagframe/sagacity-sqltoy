package org.sagacity.sqltoy.config.model;

import java.io.Serializable;

/**
 * 
 * @author chenrenfei
 *
 */
public class DataVersionConfig implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private String field;

	private boolean startDate;

	public String getField() {
		return field;
	}

	public void setField(String field) {
		this.field = field;
	}

	public boolean isStartDate() {
		return startDate;
	}

	public void setStartDate(boolean startDate) {
		this.startDate = startDate;
	}

}
