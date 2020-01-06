package org.sagacity.sqltoy.plugins.id.macro.impl;

import java.util.HashMap;

import org.sagacity.sqltoy.plugins.id.macro.AbstractMacro;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * @project sagacity-sqltoy4.2
 * @description 用于主键生成策略，根据依赖的字段值进行切割提取部分字符参与主键生成,用法:@substr(${colName},startIndex,length)
 *              例如 @substr(${colName},0,2) 从第0位开始切去2位字符
 * @author zhongxuchen
 * 
 */
public class SubString extends AbstractMacro {

	@Override
	public String execute(String[] params, HashMap<String, Object> keyValues) {
		if (params == null || params.length < 3)
			return "";
		Object value = keyValues.get(params[0].toLowerCase());
		if (StringUtil.isBlank(value))
			return "";
		String paramValue = value.toString();
		int start = Integer.parseInt(params[1]);
		int length = Integer.parseInt(params[2]);
		// 开始位置大于整体长度
		if (start >= paramValue.length())
			return "";
		// 长度符合切割标准
		if ((paramValue.length() - start) > length)
			return paramValue.substring(start, start + length);
		// 长度小于切割长度
		return paramValue.substring(start);
	}

}
