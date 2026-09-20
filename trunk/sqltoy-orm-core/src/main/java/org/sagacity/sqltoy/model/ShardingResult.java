package org.sagacity.sqltoy.model;

import java.util.List;

/**
 * @project sagacity-sqltoy
 * @description 分库分表批量对象操作的结果,为今后错误策略提供基础(如:单个节点错误判作整体错误)
 * @author zhongxuchen
 * @version v1.0,Date:2017-12-14
 */
public class ShardingResult implements java.io.Serializable {

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
	 * 失败时的原始异常,便于调用方获取根因堆栈
	 */
	private Exception cause;

	/**
	 * @return the rows
	 */
	public List<?> getRows() {
		return rows;
	}

	/**
	 * @param rows List
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
	 * @param message the message to set
	 */
	public void setMessage(String message) {
		this.message = message;
	}

	/**
	 * @return the cause
	 */
	public Exception getCause() {
		return cause;
	}

	/**
	 * @param cause the cause to set
	 */
	public void setCause(Exception cause) {
		this.cause = cause;
	}

}
