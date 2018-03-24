/**
 * 
 */
package org.sagacity.sqltoy.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.sagacity.sqltoy.config.model.SqlToyResult;

/**
 * @project sagacity-sqltoy4.2
 * @description sql执行日志
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:ExecuteLog.java,Revision:v1.0,Date:2018年3月24日
 */
public class SqlExecuteTrace implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 6050450953137017285L;

	public SqlExecuteTrace(String id, String type) {
		this.id = id;
		this.type = type;
		this.start = System.currentTimeMillis();
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
	 * sql执行的类别(分页查询\普通查询\修改操作)
	 */
	private String type;
	
	/**
	 * 是否发生异常
	 */
	private boolean error=false;

	/**
	 * 执行的sql和参数
	 */
	private List<SqlToyResult> sqlToyResults = new ArrayList<SqlToyResult>();

	/**
	 * @return the start
	 */
	public Long getStart() {
		return start;
	}

	public long getExecuteTime() {
		if (start == null)
			return -1;
		return System.currentTimeMillis() - start;
	}

	/**
	 * @return the id
	 */
	public String getId() {
		return id;
	}

	/**
	 * @param id
	 *            the id to set
	 */
	public void setId(String id) {
		this.id = id;
	}

	/**
	 * @return the type
	 */
	public String getType() {
		return type;
	}

	/**
	 * @param type
	 *            the type to set
	 */
	public void setType(String type) {
		this.type = type;
	}

	/**
	 * @return the sqlToyResults
	 */
	public List<SqlToyResult> getSqlToyResults() {
		return sqlToyResults;
	}

	/**
	 * @param sqlToyResults
	 *            the sqlToyResults to set
	 */
	public void addSqlToyResult(String sql, Object[] paramsValue) {
		//逆序存放
		sqlToyResults.add(0,new SqlToyResult(sql, paramsValue));
	}

	/**
	 * @return the error
	 */
	public boolean isError() {
		return error;
	}

	/**
	 * @param error the error to set
	 */
	public void setError(boolean error) {
		this.error = error;
	}

}
