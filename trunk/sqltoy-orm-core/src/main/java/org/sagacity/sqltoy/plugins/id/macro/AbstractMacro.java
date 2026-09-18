package org.sagacity.sqltoy.plugins.id.macro;

import java.util.Map;

/**
 * @project sagacity-sqltoy
 * @description 提供抽象的宏处理类
 * @author zhongxuchen
 * @version v1.0,Date:2015-06-23
 */
public abstract class AbstractMacro {
	/**
	 * 转换器处理逻辑接口方法定义
	 * 
	 * @param params      宏里面的参数名称
	 * @param keyValues   宏涉及的参数名称和对应的值
	 * @param paramValues sql语句中的参数属性名称和值对象(2023-08-18 新增)
	 * @param preSql      宏前面部分的sql，用于特定场景参考(目前主要是@loop循环判null场景)
	 * @param extSign     扩展标记(备用)
	 * @return
	 */
	public abstract String execute(String[] params, Map<String, Object> keyValues, Object paramValues, String preSql,
			String extSign);

}
