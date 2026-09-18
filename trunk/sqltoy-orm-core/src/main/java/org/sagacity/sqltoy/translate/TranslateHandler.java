package org.sagacity.sqltoy.translate;

/**
 * @project sagacity-sqltoy
 * @description 提供通过sqltoy缓存进行对象字段翻译的功能
 * @author zhongxuchen
 * @version v1.0,Date:2019-05-20
 */
public abstract class TranslateHandler {
	/**
	 * 从行记录中获取key
	 * 
	 * @param row
	 * @return
	 */
	public abstract Object getKey(Object row);

	/**
	 * 将翻译后的名称回写到行记录中
	 * 
	 * @param row
	 * @param name
	 */
	public abstract void setName(Object row, String name);
}
