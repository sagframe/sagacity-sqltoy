/**
 * 
 */
package org.sagacity.sqltoy.callback;

import java.sql.CallableStatement;
import java.sql.ResultSet;

/**
 * @project sagacity-sqltoy
 * @description 数据库CallableStatement针对存储过程处理反调抽象类,用来处理result
 * @author zhongxuchen
 * @version v1.0,Date:2009-3-20
 */
public abstract class CallableStatementResultHandler {
	/**
	 * 结果集
	 */
	private Object result;

	/**
	 * @TODO 存储过程执行
	 * @param rowData 数据集合
	 * @param pst     数据库pst
	 * @param rs      ResultSet
	 * @throws Exception 异常抛出
	 */
	public abstract void execute(Object rowData, CallableStatement pst, ResultSet rs) throws Exception;

	public void setResult(Object result) {
		this.result = result;
	}

	public Object getResult() {
		return this.result;
	}
}
