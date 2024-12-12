/**
 * 
 */
package org.sagacity.sqltoy.config.model;

import java.io.Serializable;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.sagacity.sqltoy.model.OperateDetailType;
import org.sagacity.sqltoy.utils.IdUtil;

/**
 * @project sagacity-sqltoy
 * @description sql执行日志
 * @author zhongxuchen
 * @version v1.0,Date:2018年3月24日
 */
public class SqlExecuteTrace implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 6050450953137017285L;

	public SqlExecuteTrace(String id, OperateDetailType operateDetailType, boolean isPrint) {
		this.id = id;
		this.operateDetailType = operateDetailType;
		this.start = System.currentTimeMillis();
		this.isPrint = isPrint;
		// 不需要体现年月日
		this.uid = IdUtil.getDebugId();
	}

	/**
	 * 开始执行
	 */
	private Long start;

	/**
	 * sqlid
	 */
	private String id;

	/**
	 * 全局id
	 */
	private String uid;

	/**
	 * 是否输出
	 */
	private boolean isPrint = true;

	/**
	 * 超时标志
	 */
	private boolean overTime = false;

	/**
	 * 数据库类型
	 */
	private String dialect;

	/**
	 * @return the isPrint
	 */
	public boolean isPrint() {
		return isPrint;
	}

	/**
	 * sql执行的类别(分页查询\普通查询\修改操作)
	 */
	private OperateDetailType operateDetailType;

	/**
	 * 批量信息
	 */
	private Long batchSize;

	/**
	 * 是否发生异常
	 */
	private boolean error = false;

	/**
	 * 执行的sql和参数
	 */
	private List<SqlExecuteLog> executeLogs = new CopyOnWriteArrayList<>();

	/**
	 * @return the start
	 */
	public Long getStart() {
		return start;
	}

	public long getExecuteTime() {
		if (start == null) {
			return -1;
		}
		return System.currentTimeMillis() - start;
	}

	/**
	 * @return the id
	 */
	public String getId() {
		return id;
	}

	/**
	 * @param id the id to set
	 */
	public void setId(String id) {
		this.id = id;
	}

	/**
	 * @return the type
	 */
	public String getType() {
		return (operateDetailType == null) ? "" : operateDetailType.value();
	}

	public OperateDetailType getOperateDetailType() {
		return operateDetailType;
	}

	/**
	 * @param type the type to set
	 */
	public void setOperateDetailType(OperateDetailType operateDetailType) {
		this.operateDetailType = operateDetailType;
	}

	/**
	 * @return the sqlToyResults
	 */
	public List<SqlExecuteLog> getExecuteLogs() {
		return executeLogs;
	}

	/**
	 * @param topic
	 * @param sql
	 * @param paramsValue
	 */
	public void addSqlLog(String topic, String sql, Object... paramsValue) {
		executeLogs.add(new SqlExecuteLog(0, topic, sql, paramsValue));
	}

	/**
	 * @param topic
	 * @param content
	 * @param paramsValue
	 */
	public void addLog(String topic, String content, Object... paramsValue) {
		executeLogs.add(new SqlExecuteLog(1, topic, content, paramsValue));
	}

	/**
	 * @return the error
	 */
	public boolean isError() {
		return error;
	}

	/**
	 * @param errorMsg the error to set
	 */
	public void setError(String errorMsg) {
		this.error = true;
		executeLogs.add(new SqlExecuteLog(1, "错误信息", errorMsg, null));
	}

	/**
	 * @return the uid
	 */
	public String getUid() {
		return uid;
	}

	/**
	 * @return the overTime
	 */
	public boolean isOverTime() {
		return overTime;
	}

	/**
	 * @param overTime the overTime to set
	 */
	public void setOverTime(boolean overTime) {
		this.overTime = overTime;
	}

	public String getDialect() {
		return dialect;
	}

	public void setDialect(String dialect) {
		this.dialect = dialect;
	}

	/**
	 * @return the batchSize
	 */
	public Long getBatchSize() {
		return batchSize;
	}

	/**
	 * @param batchSize the batchSize to set
	 */
	public void setBatchSize(Long batchSize) {
		this.batchSize = batchSize;
	}
}
