/**
 * 
 */
package org.sagacity.sqltoy.plugins;

import java.util.Map;

import org.sagacity.sqltoy.model.IgnoreCaseSet;

/**
 * @project sagacity-sqltoy4.0
 * @description 统一字段赋值处理
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version v1.0,Date:2018年1月17日
 * @modify {Date:2019-09-15,增加了forceUpdateFields方法}
 */
public interface IUnifyFieldsHandler {
	/**
	 * @TODO 设置创建记录时需要赋值的字段和对应的值
	 * @return
	 */
	public Map<String, Object> createUnifyFields();

	/**
	 * @TODO 设置修改记录时需要赋值的字段和对应的值
	 * @return
	 */
	public Map<String, Object> updateUnifyFields();

	//在非强制情况下，create和update赋值都是先判断字段是否已经赋值，如已经赋值则忽视
	//强制赋值后，则忽视字段赋值，强制覆盖
	/**
	 * @TODO 強制修改的字段(一般针对update属性)
	 * @return
	 */
	public IgnoreCaseSet forceUpdateFields();

}
