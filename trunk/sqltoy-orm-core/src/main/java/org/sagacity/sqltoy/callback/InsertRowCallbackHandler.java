/**
 * 
 */
package org.sagacity.sqltoy.callback;

import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * @project sagacity-sqltoy
 * @description 批量行数据插入反调抽象类定义(实际极少使用,留给在超极端场景下自定义pst处理)
 * @author zhongxuchen
 * @version v1.0,Date:2010-1-5
 */
@FunctionalInterface
public interface InsertRowCallbackHandler {
	/**
	 * @todo 批量插入反调
	 * @param pst
	 * @param index   第几行
	 * @param rowData 单行数据
	 * @throws SQLException
	 */
	public void process(PreparedStatement pst, int index, Object rowData) throws SQLException;

}
