/**
 * 
 */
package org.sagacity.sqltoy.model.inner;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.sagacity.sqltoy.exception.DataAccessException;
import org.sagacity.sqltoy.utils.CollectionUtil;

/**
 * @project sagacity-sqltoy
 * @description 提供一个基础的数据集对象模型
 * @author zhongxuchen
 * @version v1.0,Date:2016年3月8日
 */
@SuppressWarnings("rawtypes")
public class DataSetResult<T> implements Serializable {
	private static final long serialVersionUID = -2125295102578360914L;

	/**
	 * 标题的名称
	 */
	private String[] labelNames;

	/**
	 * 标题类别
	 */
	private String[] labelTypes;

	/**
	 * 结果记录信息,默认为空集合
	 */
	private List<T> rows;

	/**
	 * 总记录数量
	 */
	private Long recordCount;

	/**
	 * 执行总时长,毫秒
	 */
	private Long executeTime = -1L;

	/**
	 * 执行成功标志
	 */
	private boolean success = true;

	/**
	 * 执行信息
	 */
	private String message;

	/**
	 * @return the labelNames
	 */
	public String[] getLabelNames() {
		return labelNames;
	}

	/**
	 * @param labelNames the labelNames to set
	 */
	public void setLabelNames(String[] labelNames) {
		this.labelNames = labelNames;
	}

	/**
	 * @return the labelTypes
	 */
	public String[] getLabelTypes() {
		return labelTypes;
	}

	/**
	 * @param labelTypes the labelTypes to set
	 */
	public void setLabelTypes(String[] labelTypes) {
		this.labelTypes = labelTypes;
	}

	/**
	 * @return the rows
	 */
	public List<T> getRows() {
		if (this.rows == null) {
			return new ArrayList<T>();
		}
		return this.rows;
	}

	/**
	 * @param rows the rows to set
	 */
	public void setRows(List<T> rows) {
		this.rows = rows;
	}

	/**
	 * @todo 返回含标题的结果集
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public List getLabelRows() {
		List result = new ArrayList();
		if (labelNames != null) {
			result.add(CollectionUtil.arrayToList(labelNames));
		}
		if (this.getRows() != null) {
			result.addAll(this.getRows());
		}
		return result;
	}

	/**
	 * @TODO 提供查询单列场景下直接返回一维集合
	 * @param distinct 是否去重
	 * @return
	 */
	public List getFirstColumn(boolean distinct) {
		List result = new ArrayList();
		if (this.rows == null || this.rows.isEmpty()) {
			return result;
		}
		// 一维(findByQuery中查询单列值，且resultType为原生类型，rows变成了一维)
		int dimen = CollectionUtil.judgeObjectDimen(this.rows);
		if (dimen == 1) {
			for (Object cell : this.rows) {
				if (distinct) {
					if (!result.contains(cell)) {
						result.add(cell);
					}
				} else {
					result.add(cell);
				}
			}
			return result;
		}
		Object rowTmp = this.rows.get(0);
		Object cell;
		if (rowTmp instanceof List) {
			for (Object row : this.rows) {
				cell = ((List) row).get(0);
				if (distinct) {
					if (!result.contains(cell)) {
						result.add(cell);
					}
				} else {
					result.add(cell);
				}
			}
			return result;
		} else if (rowTmp.getClass().isArray()) {
			for (Object row : this.rows) {
				cell = ((Object[]) row)[0];
				if (distinct) {
					if (!result.contains(cell)) {
						result.add(cell);
					}
				} else {
					result.add(cell);
				}
			}
			return result;
		}
		throw new DataAccessException("切取集合的单列值的前提是返回结果是List<List> 和List<Object[]>类型场景!");
	}

	/**
	 * @return the totalCount
	 */
	public Long getRecordCount() {
		if (recordCount == null) {
			recordCount = Long.valueOf(getRows().size());
		}
		return recordCount;
	}

	/**
	 * @param totalCount the totalCount to set
	 */
	public void setRecordCount(Long recordCount) {
		this.recordCount = recordCount;
	}

	/**
	 * @return the executeTime
	 */
	public Long getExecuteTime() {
		return executeTime;
	}

	/**
	 * @param executeTime the executeTime to set
	 */
	public void setExecuteTime(Long executeTime) {
		this.executeTime = executeTime;
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
