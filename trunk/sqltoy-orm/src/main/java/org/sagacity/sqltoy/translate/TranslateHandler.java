package org.sagacity.sqltoy.translate;

/**
 * @project sqltoy-orm
 * @description 提供通过sqltoy缓存进行对象字段翻译的功能
 * @author zhongxuchen
 * @version v1.0,Date:2019-05-20
 */
public abstract class TranslateHandler {
	/**
	 * @todo 从行记录中获取key
	 * @param row
	 * @return
	 */
	public abstract Object getKey(Object row);

	/**
	 * @todo 将翻译后的名称回写到行记录中
	 * @param row
	 * @param name
	 */
	public abstract void setName(Object row, String name);
}
