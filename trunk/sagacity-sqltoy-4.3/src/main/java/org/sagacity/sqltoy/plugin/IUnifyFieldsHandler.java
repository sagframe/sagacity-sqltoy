/**
 * 
 */
package org.sagacity.sqltoy.plugin;

import java.util.Map;

/**
 * @project sagacity-sqltoy4.0
 * @description 统一字段赋值处理
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:AbstractUnifyFieldsHandler.java,Revision:v1.0,Date:2018年1月17日
 */
// 注:sqltoy会自动判断是否有相关属性,属性不存在则不会进行操作
// 针对saveOrUpdate操作,sqltoy则分别调用创建和修改的赋值,同时避免修改时冲掉创建人和创建时间信息
public interface IUnifyFieldsHandler {
	/**
	 * 强制对创建时间、修改时间进行修改
	 */
	//private boolean forceUpdateTime = false;

	/**
	 * 返回创建记录时需要修改的字段和对应的值
	 * 
	 * @return
	 */
	public Map<String, Object> createUnifyFields();

	/**
	 * 返回修改记录时相关字段的名称和值
	 * 
	 * @return
	 */
	public Map<String, Object> updateUnifyFields();

//	/**
//	 * @return the forceUpdateTime
//	 */
//	public boolean isForceUpdateTime() {
//		return forceUpdateTime;
//	}
//
//	/**
//	 * @param forceUpdateTime
//	 *            the forceUpdateTime to set
//	 */
//	public void setForceUpdateTime(boolean forceUpdateTime) {
//		this.forceUpdateTime = forceUpdateTime;
//	}

}
