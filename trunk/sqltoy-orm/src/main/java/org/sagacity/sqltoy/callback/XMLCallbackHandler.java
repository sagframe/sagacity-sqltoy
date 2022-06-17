/**
 * 
 */
package org.sagacity.sqltoy.callback;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * @project sagacity-sqltoy
 * @description xml编辑反调接口
 * @author zhongxuchen
 * @version v1.0,Date:2009-12-30
 */
@FunctionalInterface
public interface XMLCallbackHandler {
	/**
	 * @todo 处理xml document对象，用于sqltoy xml文件解析
	 * @param doc
	 * @param root
	 * @return
	 */
	public Object process(Document doc, Element root) throws Exception;
}
