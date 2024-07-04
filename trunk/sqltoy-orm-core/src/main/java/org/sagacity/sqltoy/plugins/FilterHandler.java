/**
 * 
 */
package org.sagacity.sqltoy.plugins;

/**
 * @project sagacity-sqltoy
 * @description 针对sql xml中的filters，增加
 *              <p>
 *              type可以不用填，是用来给特殊情况下做一个分类标记，为逻辑处理提供一些辨识
 *              <custom-handler params="" type=""/>让开发自定义参数处理
 *              </p>
 * @author zhongxuchen
 * @version v1.0, Date:2022-04-26
 */
public interface FilterHandler {
	/**
	 * @TODO 提供sql xml中的filters自定义处理
	 * @param value
	 * @param type  用来标识区别逻辑
	 * @return
	 */
	public Object process(Object value, String type);
}
