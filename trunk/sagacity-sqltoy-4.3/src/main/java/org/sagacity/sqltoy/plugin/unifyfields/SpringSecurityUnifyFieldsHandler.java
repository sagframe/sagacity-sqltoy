/**
 * 
 */
package org.sagacity.sqltoy.plugin.unifyfields;

import java.sql.Timestamp;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.sagacity.sqltoy.plugin.IUnifyFieldsHandler;
import org.sagacity.sqltoy.utils.DateUtil;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * @project sagacity-sqltoy4.0
 * @description 基于spring security获取当前用户的机制进行统一字段赋值
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:SpringSecurityUnifyFieldsHandler.java,Revision:v1.0,Date:2018年1月17日
 */
public class SpringSecurityUnifyFieldsHandler implements IUnifyFieldsHandler {
	private String defaultUserName = "system";

	// 注:sqltoy会自动判断是否有相关属性,属性不存在则不会进行操作
	// 针对saveOrUpdate操作,sqltoy则分别调用创建和修改的赋值,同时避免修改时冲掉创建人和创建时间信息
	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.plugin.IUnifyFieldsHandler#createUnifyFields()
	 */
	@Override
	public Map<String, Object> createUnifyFields() {
		HashMap<String, Object> keyValueMap = new HashMap<String, Object>();
		Date nowDate = DateUtil.getNowTime();
		Timestamp nowTime = DateUtil.getTimestamp(null);
		// 获取用户信息
		String userId = getCurrentUserName();
		keyValueMap.put("createBy", userId);
		keyValueMap.put("createDate", nowDate);
		keyValueMap.put("createTime", nowTime);
		keyValueMap.put("updateBy", userId);
		keyValueMap.put("updateDate", nowDate);
		keyValueMap.put("updateTime", nowTime);
		// enabled 是否启用状态
		keyValueMap.put("enabled", 1);
		return keyValueMap;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.plugin.IUnifyFieldsHandler#updateUnifyFields()
	 */
	@Override
	public Map<String, Object> updateUnifyFields() {
		HashMap<String, Object> keyValueMap = new HashMap<String, Object>();
		Date nowDate = DateUtil.getNowTime();
		Timestamp nowTime = DateUtil.getTimestamp(null);
		// 获取用户信息
		String userId = getCurrentUserName();
		keyValueMap.put("updateBy", userId);
		keyValueMap.put("updateDate", nowDate);
		keyValueMap.put("updateTime", nowTime);
		return keyValueMap;
	}

	/**
	 * @todo 获取当前用户Id信息
	 * @return
	 */
	private String getCurrentUserName() {
		SecurityContext context = SecurityContextHolder.getContext();
		Authentication authentication = null;
		if (context != null) {
			authentication = context.getAuthentication();
		}
		if (authentication != null && authentication.getPrincipal() != null) {
			return authentication.getName();
		}
		// default
		return defaultUserName;
	}

	public String getDefaultUserName() {
		return defaultUserName;
	}

	public void setDefaultUserName(String defaultUserName) {
		this.defaultUserName = defaultUserName;
	}

	
}
