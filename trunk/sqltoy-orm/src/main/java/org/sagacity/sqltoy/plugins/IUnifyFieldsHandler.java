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
 * @version id:IUnifyFieldsHandler.java,Revision:v1.0,Date:2018年1月17日
 * @Modification {Date:2019-09-15,增加了forceUpdateFields方法}
 */
public interface IUnifyFieldsHandler {
	/**
	 * @TODO 返回创建记录时需要修改的字段和对应的值
	 * @return
	 */
	public Map<String, Object> createUnifyFields();

	/**
	 * @TODO 返回修改记录时相关字段的名称和值
	 * @return
	 */
	public Map<String, Object> updateUnifyFields();

	/**
	 * @TODO 強制修改的字段
	 * @return
	 */
	public IgnoreCaseSet forceUpdateFields();

}
