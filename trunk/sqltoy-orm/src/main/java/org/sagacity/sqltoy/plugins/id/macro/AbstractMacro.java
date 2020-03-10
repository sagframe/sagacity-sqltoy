/**
 * 
 */
package org.sagacity.sqltoy.plugins.id.macro;

import org.sagacity.sqltoy.model.IgnoreKeyCaseMap;

/**
 * @project sqltoy-orm
 * @description 提供抽象的宏处理类
 * @author zhongxuchen <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:AbstractMacro.java,Revision:v1.0,Date:2015年6月23日
 */
public abstract class AbstractMacro {
	/**
	 * @todo 转换器处理逻辑接口方法定义
	 * @param params
	 * @param keyValues
	 * @return
	 */
	public abstract String execute(String[] params, IgnoreKeyCaseMap<String, Object> keyValues);

}
