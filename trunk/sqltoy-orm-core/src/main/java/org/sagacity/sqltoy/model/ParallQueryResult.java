/**
 * 
 */
package org.sagacity.sqltoy.model;

import java.io.Serializable;

/**
 * @project sagacity-sqltoy
 * @description 并行查询过程中的结果存放模型，并行单个线程返回的结果,最终结果是整合各个线程结果
 * @author zhongxuchen
 * @version v1.0, Date:2020-8-25
 * @modify 2020-8-25,修改说明
 */
public class ParallQueryResult implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 5615476778698123272L;

	/**
	 * 结果对象
	 */
	private QueryResult result;

	/**
	 * 是否成功
	 */
	private boolean success = true;

	/**
	 * 执行信息
	 */
	private String message;

	/**
	 * @return the result
	 */
	public QueryResult getResult() {
		return result;
	}

	/**
	 * @param result the result to set
	 */
	public void setResult(QueryResult result) {
		this.result = result;
	}

	/**
	 * @return the success
	 */
	public boolean isSuccess() {
		return success;
	}

	/**
	 * @param success the success to set
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

}
