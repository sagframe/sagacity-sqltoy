package org.sagacity.sqltoy.model;

import java.io.Serializable;

/**
 * @project sagacity-sqltoy4.0
 * @description 存储过程返回结果集
 * @author chenrf <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:StoreResult.java,Revision:v1.0,Date:2009-12-25 下午03:10:40
 */
public class StoreResult extends DataSetResult implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1936180601122427649L;

	/**
	 * 输出参数的值
	 */
	private Object[] outResult;

	/**
	 * 被修改的记录数量
	 */
	private Long updateCount;

	/**
	 * @return the updateCount
	 */
	public Long getUpdateCount() {
		return updateCount;
	}

	/**
	 * @param updateCount
	 *            the updateCount to set
	 */
	public void setUpdateCount(Long updateCount) {
		this.updateCount = updateCount;
	}

	/**
	 * @return the outResult
	 */
	public Object[] getOutResult() {
		return outResult;
	}

	/**
	 * @param outResult
	 *            the outResult to set
	 */
	public void setOutResult(Object[] outResult) {
		this.outResult = outResult;
	}
}
