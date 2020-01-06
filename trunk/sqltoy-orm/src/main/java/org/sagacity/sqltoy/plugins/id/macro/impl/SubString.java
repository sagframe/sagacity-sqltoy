package org.sagacity.sqltoy.plugins.id.macro.impl;

import java.util.HashMap;

import org.sagacity.sqltoy.plugins.id.macro.AbstractMacro;

/**
 * 
 * @author zhong
 *
 */
public class SubString extends AbstractMacro {

	@Override
	public String execute(String[] params, HashMap<String, Object> keyValues) {
		if (params == null || params.length < 3)
			return "";
		Object value = keyValues.get(params[0].toLowerCase());
		if (value == null)
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

	/*public static void main(String[] args) {
		String[] params = { "0H", "2", "2" };
		String paramValue = params[0];
		int start = Integer.parseInt(params[1]);
		int length = Integer.parseInt(params[2]);
		int realSize = (paramValue.length() - start) > length ? length : (paramValue.length() - start);
		String result = "";
		if (start >= paramValue.length())
			result = "";
		else if ((paramValue.length() - start) > length)
			result = paramValue.substring(start, start + length);
		else
			result = paramValue.substring(start);
		System.err.println(result);
	}*/
}
