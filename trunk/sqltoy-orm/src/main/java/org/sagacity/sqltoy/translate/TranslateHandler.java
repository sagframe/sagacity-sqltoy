/**
 * 
 */
package org.sagacity.sqltoy.translate;

/**
 * @project sqltoy-orm
 * @description 提供通过sqltoy缓存进行对象字段翻译的功能
 * @author renfei.chen <a href="mailto:zhongxuchen@hotmail.com">联系作者</a>
 * @version id:TranslateHandler.java,Revision:v1.0,Date:2019-05-20 上午10:08:15
 */
public abstract class TranslateHandler {
	/**
	 * @todo 获取key
	 * @param row
	 * @return
	 */
	public abstract Object getKey(Object row);

	/**
	 * @todo 设置显示名称
	 * @param row
	 * @param name
	 */
	public abstract void setName(Object row, String name);
}
