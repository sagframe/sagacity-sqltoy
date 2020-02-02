/**
 * 
 */
package org.sagacity.sqltoy.callback;

import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * @project sagacity-sqltoy
 * @description 提供数据库参数设置反调扩展
 * @author zhongxuchen <a href="mailto:zhongxuchen@hotmail.com">联系作者</a>
 * @version id:PreparedStatementSetter.java,Revision:v1.0,Date:2011-7-14
 */
public abstract class PreparedStatementSetter {
	/**
	 * @todo <b>demo:new PreparedStatementSetter(pst){public void
	 *       setter(pst.set(1,xx)};</b>
	 * @author zhongxuchen
	 * @date 2011-7-14 上午10:31:42
	 * @param pst
	 * @throws SQLException
	 */
	public abstract void setValues(PreparedStatement pst) throws SQLException;
}
