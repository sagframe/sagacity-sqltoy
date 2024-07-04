/**
 * 
 */
package org.sagacity.sqltoy.plugins.id.macro.impl;

import java.util.Map;

import org.sagacity.sqltoy.plugins.id.macro.AbstractMacro;
import org.sagacity.sqltoy.plugins.id.macro.MacroUtils;
import org.sagacity.sqltoy.utils.BeanUtil;

/**
 * @project sagacity-sqltoy
 * @description 进行类似oracle decode 数据枚举判断
 * @author zhongxuchen
 * @version v1.0,Date:2018年5月25日
 */
public class Case extends AbstractMacro {

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.sagacity.sqltoy.plugins.id.macro.AbstractMacro#execute(java.lang.Object)
	 */
	@Override
	public String execute(String[] params, Map<String, Object> keyValues, Object paramValues, String preSql,
			String extSign) {
		if (params == null) {
			return "";
		}
		int paramSize = params.length;
		// 小于3不符合decode运算模式
		if (paramSize < 3) {
			return "";
		}
		String baseParam = params[0].trim();
		String baseValue = null;
		// ${paramName} 格式
		if (baseParam.contains("$")) {
			baseValue = MacroUtils.replaceParams(baseParam, keyValues);
		} else {
			if (keyValues != null && keyValues.containsKey(baseParam)) {
				Object tmpVar = keyValues.get(baseParam);
				if (tmpVar == null) {
					baseValue = "null";
				} else if (tmpVar instanceof Enum) {
					baseValue = BeanUtil.getEnumValue(tmpVar).toString();
				} else {
					baseValue = tmpVar.toString();
				}
			} else {
				baseValue = baseParam;
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
