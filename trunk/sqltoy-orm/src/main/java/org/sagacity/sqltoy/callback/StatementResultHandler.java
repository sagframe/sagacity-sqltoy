/**
 * 
 */
package org.sagacity.sqltoy.callback;

import java.sql.ResultSet;
import java.sql.Statement;

/**
 * @project sagacity-sqltoy
 * @description 数据库Statement处理反调抽象类,用来处理result
 * @author zhong
 * @version v1.0, Date:2021-4-29
 * @modify 2021-4-29,修改说明
 */
public abstract class StatementResultHandler {
	/**
	 * 结果集
	 */
	private Object result;

	/**
	 * @TODO 执行pst
	 * @param rowData
	 * @param pst
	 * @param rs
	 * @throws Exception
	 */
	public abstract void execute(Object rowData, Statement pst, ResultSet rs) throws Exception;

	public void setResult(Object result) {
		this.result = result;
	}

	public Object getResult() {
		return this.result;
	}
}
