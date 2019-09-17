/**
 * 
 */
package org.sagacity.sqltoy.callback;

import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * @project sagacity-sqltoy
 * @description 批量行数据插入反调抽象类定义
 * @author chenrf <a href="mailto:zhongxuchen@hotmail.com">联系作者</a>
 * @version id:InsertRowCallbackHandler.java,Revision:v1.0,Date:2010-1-5
 */
public abstract class InsertRowCallbackHandler {
	/**
	 * @todo 批量插入反调
	 * @param pst
	 * @param index
	 * @param rowData
	 * @throws SQLException
	 */
	public abstract void process(PreparedStatement pst, int index, Object rowData) throws SQLException;

}
