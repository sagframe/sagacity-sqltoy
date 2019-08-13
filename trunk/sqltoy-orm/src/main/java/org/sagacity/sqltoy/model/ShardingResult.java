/**
 * 
 */
package org.sagacity.sqltoy.model;

import java.util.List;

/**
 * @project sagacity-sqltoy4.0
 * @description 分库分表批量对象操作的结果,为今后错误策略提供基础(如:单个节点错误判作整体错误)
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:ShardingResult.java,Revision:v1.0,Date:2017年12月14日
 */
public class ShardingResult implements java.io.Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 2677176208224903988L;

	/**
	 * 结果
	 */
	private List<?> rows;

	/**
	 * 是否成功
	 */
	private boolean success = true;

	/**
	 * 执行信息
	 */
	private String message;

	/**
	 * @return the rows
	 */
	public List<?> getRows() {
		return rows;
	}

	/**
	 * @param rows
	 *            the rows to set
	 */
	public void setRows(List<?> rows) {
		this.rows = rows;
	}

	/**
	 * @return the success
	 */
	public boolean isSuccess() {
		return success;
	}

	/**
	 * @param success
	 *            the success to set
	 */
	public void setSuccess(boolean success) {
		this.success = success;
	}

	/**
	 * @return the message
	 */
	public String getMessage() {
		return message;
	}

	/**
	 * @param message
	 *            the message to set
	 */
	public void setMessage(String message) {
		this.message = message;
	}

}
