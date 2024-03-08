/**
 * 
 */
package org.sagacity.sqltoy.plugins.secure;

import org.sagacity.sqltoy.config.model.SecureMask;

/**
 * @project sagacity-sqltoy
 * @description 字符串脱敏接口定义，sqltoy提供默认实现，开发者也可以扩展
 * @author zhongxuchen
 * @version v1.0,Date:2021-11-10
 */
public interface DesensitizeProvider {
	/**
	 * @TODO 对字符串进行脱敏
	 * @param content
	 * @param maskType
	 * @return
	 */
	public String desensitize(String content, SecureMask maskType);
}
