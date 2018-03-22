/**
 * 
 */
package org.sagacity.sqltoy.translate.model;

import java.io.Serializable;

/**
 * @project sagacity-sqltoy4.2
 * @description 时间区间模型
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:TimeSection.java,Revision:v1.0,Date:2018年3月12日
 */
public class TimeSection implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -159160743683268503L;

	/**
	 * 开始时间HHmmss
	 */
	private int start;

	/**
	 * 截止时间
	 */
	private int end;

	/**
	 * 间隔时间
	 */
	private int intervalSeconds;

	/**
	 * @return the start
	 */
	public int getStart() {
		return start;
	}

	/**
	 * @param start
	 *            the start to set
	 */
	public void setStart(int start) {
		this.start = start;
	}

	/**
	 * @return the end
	 */
	public int getEnd() {
		return end;
	}

	/**
	 * @param end
	 *            the end to set
	 */
	public void setEnd(int end) {
		this.end = end;
	}

	/**
	 * @return the intervalSeconds
	 */
	public int getIntervalSeconds() {
		return intervalSeconds;
	}

	/**
	 * @param intervalSeconds
	 *            the intervalSeconds to set
	 */
	public void setIntervalSeconds(int intervalSeconds) {
		this.intervalSeconds = intervalSeconds;
	}

}
