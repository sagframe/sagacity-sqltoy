package org.sagacity.sqltoy.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * @project sagacity-sqltoy
 * @description 分页数据模型
 * @author zhongxuchen
 * @version v1.0,Date:2011-2-25
 */
public class Page<T> implements Serializable {
	private static final long serialVersionUID = -7117473828519846708L;

	/**
	 * 每页记录数(默认为10)
	 */
	private int pageSize = 10;

	/**
	 * 当前页数(默认从1开始,以页面给用户显示的为基准)
	 */
	private long pageNo = 1;

	/**
	 * 分页查询出的数据明细
	 */
	private List<T> rows;

	/**
	 * 是否跳过查询总记录数
	 */
	private Boolean skipQueryCount = false;

	/**
	 * 总记录数
	 */
	private long recordCount;

	/**
	 * 起始记录
	 */
	private long startIndex = 0;

	/**
	 * 跳转超出数据页范围回到第一页
	 */
	private Boolean overPageToFirst;

	public Page() {

	}

	public Page(int pageSize, long pageNo) {
		if (pageSize > 0) {
			this.pageSize = pageSize;
		}
		this.pageNo = pageNo;
	}

	public Page(List<T> rows, long recordCount) {
		setPageSize(10);
		setRecordCount(recordCount);
		setRows(rows);
		this.startIndex = 0;
	}

	public Page(List<T> rows, long recordCount, long startIndex) {
		setPageSize(10);
		setRecordCount(recordCount);
		setRows(rows);
		this.startIndex = startIndex;
	}

	public Page(List<T> rows, long recordCount, int pageSize, long startIndex) {
		setPageSize(pageSize);
		setRecordCount(recordCount);
		setRows(rows);
		this.startIndex = startIndex;
	}

	public List<T> getRows() {
		if (this.rows == null) {
			return new ArrayList<T>();
		}
		return this.rows;
	}

	public Page setRows(List<T> rows) {
		this.rows = rows;
		return this;
	}

	public int getPageSize() {
		if (pageSize < 1) {
			return 10;
		}
		return pageSize;
	}

	public Page setPageSize(int pageSize) {
		this.pageSize = pageSize;
		return this;
	}

	public long getRecordCount() {
		return recordCount;
	}

	public Page setRecordCount(long recordCount) {
		this.recordCount = recordCount < 0 ? 0 : recordCount;
		return this;
	}

	public Page setOverPageToFirst(Boolean overPageToFirst) {
		this.overPageToFirst = overPageToFirst;
		return this;
	}

	/**
	 * Access method for the pageNo property.
	 * 
	 * @return the current value of the pageNo property
	 */
	public long getPageNo() {
		// -1 有特殊用途，表示查询所有记录,用于页面下载场景(实际查询时会控制下载是否超范围)
		if (this.pageNo == -1) {
			return this.pageNo;
		}
		if (this.pageNo < 1) {
			return 1;
		}
		return this.pageNo;
	}

	/**
	 * Sets the value of the pageNo property.
	 * 
	 * @param pageNo the new value of the pageNo property where pageNo==-1 then show
	 *               all page
	 */
	public Page setPageNo(long pageNo) {
		this.pageNo = pageNo;
		return this;
	}

	public long getStartIndex() {
		if (startIndex == 0 && pageNo > 1) {
			return (pageNo - 1) * pageSize;
		}
		return startIndex;
	}

	public long getNextIndex() {
		long nextIndex = getStartIndex() + pageSize;
		if (nextIndex >= recordCount) {
			return getStartIndex();
		}
		return nextIndex;
	}

	public long getPreviousIndex() {
		long previousIndex = getStartIndex() - pageSize;
		if (previousIndex < 0) {
			return 0;
		}
		return previousIndex;
	}

	/**
	 * @return 上一页号
	 */
	public long getPriorPage() {
		if (this.pageNo > 1) {
			return this.pageNo - 1;
		}
		return this.pageNo;
	}

	/**
	 * @return 最后一页
	 */
	public long getLastPage() {
		return (recordCount - 1) / getPageSize() + 1;
	}

	/**
	 * @return 第一页
	 */
	public long getFirstPage() {
		return 1;
	}

	/**
	 * @return 下一页号
	 */
	public long getNextPage() {
		if (this.pageNo + 1 >= getLastPage()) {
			return getLastPage();
		}
		return this.pageNo + 1;
	}

	/**
	 * @return totalPage 总页数
	 */
	public long getTotalPage() {
		if (this.pageSize < 1) {
			return 0;
		}
		return (this.recordCount + this.pageSize - 1) / this.pageSize;
	}

	public Boolean getSkipQueryCount() {
		return skipQueryCount;
	}

	public Page setSkipQueryCount(Boolean skipQueryCount) {
		this.skipQueryCount = skipQueryCount;
		return this;
	}

	public Boolean getOverPageToFirst() {
		return overPageToFirst;
	}

}
