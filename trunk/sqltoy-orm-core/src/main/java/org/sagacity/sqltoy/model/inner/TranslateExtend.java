/**
 * 
 */
package org.sagacity.sqltoy.model.inner;

import java.io.Serializable;

import org.sagacity.sqltoy.SqlToyConstants;

/**
 * @project sagacity-sqltoy
 * @description Translate内部扩展类，便于隐藏属性避免暴露过多get方法
 * @author zhongxuchen
 * @version v1.0, Date:2020-8-7
 * @modify 2020-8-7,修改说明
 */
public class TranslateExtend implements Serializable, java.lang.Cloneable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -2441421349247126021L;

	/**
	 * 字段列
	 */
	public String column;

	/**
	 * 缓存类型(一般为字典类别)
	 */
	public String cacheType;

	/**
	 * 对应的缓存名称
	 */
	public String cache;

	/**
	 * 默认第二列为value，第一列为key
	 */
	public int index = 1;

	/**
	 * 用于entityQuery场景下指定具体作为key的列
	 */
	public String keyColumn;

	/**
	 * 字段别名
	 */
	public String alias;

	/**
	 * 分隔表达式
	 */
	public String splitRegex;

	/**
	 * 重新连接的字符
	 */
	public String linkSign = ",";

	/**
	 * ${key}_ZH_CN 用于组合匹配缓存
	 */
	public String keyTemplate = null;

	/**
	 * 存在compareColumn==xxx 表达式即表示存在是否进行缓存翻译的逻辑判断
	 */
	public boolean hasLogic = false;

	/**
	 * 逻辑判断的参照字段
	 */
	public String compareColumn;

	/**
	 * 逻辑判断的类型，四种:eq\neq\in\out
	 */
	public String compareType;

	/**
	 * 逻辑判断的对比参照值
	 */
	public String[] compareValues;

	/**
	 * 未被缓存的模板
	 */
	public String uncached = SqlToyConstants.UNCACHED_KEY_RESULT;

	@Override
	public TranslateExtend clone() {
		try {
			return (TranslateExtend) super.clone();
		} catch (CloneNotSupportedException e) {
			e.printStackTrace();
		}
		return null;
	}

}
