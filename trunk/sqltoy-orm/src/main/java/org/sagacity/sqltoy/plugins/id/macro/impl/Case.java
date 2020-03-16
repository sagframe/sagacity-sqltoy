/**
 * 
 */
package org.sagacity.sqltoy.plugins.id.macro.impl;

import org.sagacity.sqltoy.model.IgnoreKeyCaseMap;
import org.sagacity.sqltoy.plugins.id.macro.AbstractMacro;
import org.sagacity.sqltoy.plugins.id.macro.MacroUtils;

/**
 * @project sagacity-sqltoy4.2
 * @description 进行类似oracle decode 数据枚举判断
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:Case.java,Revision:v1.0,Date:2018年5月25日
 */
public class Case extends AbstractMacro {

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.sagacity.sqltoy.plugin.id.macro.AbstractMacro#execute(java.lang.Object)
	 */
	@Override
	public String execute(String[] params, IgnoreKeyCaseMap<String, Object> keyValues) {
		if (params == null)
			return "";
		int paramSize = params.length;
		// 小于3不符合decode运算模式
		if (paramSize < 3)
			return "";
		String baseParam = params[0].trim();
		String baseValue = null;
		// ${paramName} 格式
		if (baseParam.contains("$")) {
			baseValue = MacroUtils.replaceParams(baseParam, keyValues);
		} else {
			if (keyValues != null && keyValues.containsKey(baseParam)) {
				baseValue = keyValues.get(baseParam).toString();
			} else {
				baseValue=baseParam;
			}
		}
		// 默认最后一个值为结果
		String result = params[paramSize - 1];
		// {base,a,a1,b,b1,c,c1,other}
		for (int i = 0; i < (paramSize - 1) / 2; i++) {
			if (baseValue.equals(params[i * 2 + 1].trim())) {
				result = params[i * 2 + 2].trim();
				break;
			}
		}
		return result;
	}

}
