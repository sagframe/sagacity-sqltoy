/**
 * 
 */
package org.sagacity.sqltoy.demo.plugins;

import java.sql.Timestamp;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.sagacity.sqltoy.model.IgnoreCaseSet;
import org.sagacity.sqltoy.plugins.IUnifyFieldsHandler;
import org.sagacity.sqltoy.utils.DateUtil;

/**
 * 
 * @project sqltoy-orm
 * @description 在sqltoy进行新增和修改操作时，对VO指定属性进行统一赋值操作
 * @author wyl
 * @version Revision:v1.0,Date:2018年1月18日
 * @Modification Date:2018年1月18日
 */
public class SqltoyUnifyFieldsHandler implements IUnifyFieldsHandler {
	private String defaultUserName = "system-auto";

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.plugin.IUnifyFieldsHandler#createUnifyFields()
	 */
	@Override
	public Map<String, Object> createUnifyFields() {
		HashMap<String, Object> map = new HashMap<String, Object>();
		Date nowDate = DateUtil.getNowTime();
		Timestamp nowTime = DateUtil.getTimestamp(null);
		// 获取用户信息
		String userId = getUserId();
		map.put("createBy", userId);
		map.put("createDate", nowDate);
		map.put("createTime", nowTime);
		map.put("updateBy", userId);
		map.put("updateDate", nowDate);
		map.put("updateTime", nowTime);
		// enabled 是否启用状态
		map.put("enabled", 1);
		return map;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.plugin.IUnifyFieldsHandler#updateUnifyFields()
	 */
	@Override
	public Map<String, Object> updateUnifyFields() {
		Timestamp timestamp = DateUtil.getTimestamp(null);
		Map<String, Object> map = new HashMap<String, Object>();
		// 获取用户信息
		map.put("updateBy", getUserId());
		map.put("updateTime", timestamp);
		map.put("updateDate", timestamp);
		return map;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.plugins.IUnifyFieldsHandler#forceUpdateFields()
	 */
	@Override
	public IgnoreCaseSet forceUpdateFields() {
		// 最后修改时间作为必须修改字段
		IgnoreCaseSet forceUpdateFields = new IgnoreCaseSet();
		forceUpdateFields.add("updateTime");
		return forceUpdateFields;
	}

	/**
	 * @todo 获取当前用户Id信息
	 * @return
	 */
	private String getUserId() {
		// return (SpringSecurityUtils.getCurrentUser() != null) ?
		// SpringSecurityUtils.getCurrentUser().getId()
		// : defaultUserName;
		return defaultUserName;
	}

	/**
	 * @return the defaultUserName
	 */
	public String getDefaultUserName() {
		return defaultUserName;
	}

	/**
	 * @param defaultUserName
	 *            the defaultUserName to set
	 */
	public void setDefaultUserName(String defaultUserName) {
		this.defaultUserName = defaultUserName;
	}

}
