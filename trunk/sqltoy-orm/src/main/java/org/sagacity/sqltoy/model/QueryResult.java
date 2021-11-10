/**
 * 
 */
package org.sagacity.sqltoy.model;

import java.io.Serializable;

import org.sagacity.sqltoy.model.inner.DataSetResult;

/**
 * @project sagacity-sqltoy
 * @description 所有查询的结果形态模型
 * @author zhongxuchen
 * @version v1.0,Date:2014年12月14日
 */
public class QueryResult<T> extends DataSetResult<T> implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 525226255944594283L;

	/**
	 * 当前页
	 */
	private Long pageNo;

	/**
	 * 每页记录数
	 */
	private Integer pageSize;

	/**
	 * 是否跳过查询总记录数
	 */
	private Boolean skipQueryCount = false;

	/**
	 * @return the pageNo
	 */
	public Long getPageNo() {
		return pageNo;
	}

	/**
	 * @param pageNo the pageNo to set
	 */
	public void setPageNo(Long pageNo) {
		this.pageNo = pageNo;
	}

	/**
	 * @return the pageSize
	 */
	public Integer getPageSize() {
		return pageSize;
	}

	/**
	 * @param pageSize the pageSize to set
	 */
	public void setPageSize(Integer pageSize) {
		this.pageSize = pageSize;
	}

	public Boolean getSkipQueryCount() {
		return skipQueryCount;
	}

	public void setSkipQueryCount(Boolean skipQueryCount) {
		this.skipQueryCount = skipQueryCount;
	}

	/**
	 * @todo 获取分页结果模型
	 * @return
	 */
	public Page getPageResult() {
		Page result = new Page();
		result.setPageNo(this.getPageNo());
		result.setPageSize(this.getPageSize());
		result.setRecordCount(this.getRecordCount());
		result.setRows(this.getRows());
		if (skipQueryCount != null) {
			result.setSkipQueryCount(skipQueryCount);
		}
		return result;
	}

}
