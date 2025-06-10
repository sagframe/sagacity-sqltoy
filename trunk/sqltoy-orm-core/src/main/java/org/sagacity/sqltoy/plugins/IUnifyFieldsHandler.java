package org.sagacity.sqltoy.plugins;

import java.util.Map;

import org.sagacity.sqltoy.config.model.OperateType;
import org.sagacity.sqltoy.model.DataAuthFilterConfig;
import org.sagacity.sqltoy.model.IgnoreCaseSet;
import org.sagacity.sqltoy.model.IgnoreKeyCaseMap;

/**
 * @project sagacity-sqltoy
 * @description 统一字段(创建人、创建时间、修改人、修改时间、租户等)赋值处理、统一数据权限、多租户传值和越权控制
 * @author zhongxuchen
 * @version v1.0,Date:2018年1月17日
 * @modify {Date:2019-09-15,增加了forceUpdateFields方法}
 * @modify {Date:2021-10-11,增加了dataAuthFilters做统一参数传递和统一数据权限防越权过滤}
 * @modify {Date:2023-3-9,完善了SqlInterceptors，增加authTenants方法获取当前用户的授权租户id数组}
 * @modify {Date:2023-5-13,增加了createSqlTimeFields、updateSqlTimeFields
 *         解决增加和修改记录时，创建时间和修改时间采用数据库时间}
 * @modify {Date:2025-6-5,增加getUserTenantId()方法获取当前用户所在租户id}
 */
public interface IUnifyFieldsHandler {

	// ------------- 请仔细阅读改接口里面的说明 ------------------------------------/

	// 怎么获取当前用户的信息? 举一个简单例子:通过filter将当前用户信息放入ThreadLocal中，然后这里就可以获取到
	/**
	 * @TODO 设置创建记录时需要赋值的字段和对应的值(弹性模式:即优先以传递的值优先，为null再填充)
	 * @return
	 */
	public default Map<String, Object> createUnifyFields() {
		// 范例
		// HashMap<String, Object> map = new HashMap<String, Object>();
		// LocalDateTime nowDate = DateUtil.getDateTime();
		// Timestamp nowTime = DateUtil.getTimestamp(null);
		// 不存在的字段名称会自动忽略掉(因此下述属性未必是每个表中必须存在的)
		// map.put("createBy", getUserId());
		// map.put("createTime", nowTime);
		// map.put("updateBy", userId);
		// map.put("updateTime", nowTime);
		// map.put("tenantId", getUserTenant());
		// // enabled 是否启用状态
		// map.put("enabled", 1);
		// return map;
		return null;
	}

	/**
	 * @TODO 设置修改记录时需要赋值的字段和对应的值(弹性)
	 * @return
	 */
	public default Map<String, Object> updateUnifyFields() {
		return null;
	}

	// 在非强制情况下，create和update赋值都是先判断字段是否已经赋值，如已经赋值则忽视
	// 强制赋值后，则忽视字段赋值，强制覆盖
	/**
	 * @TODO 強制修改的字段(一般针对updateTime属性)
	 * @return
	 */
	public default IgnoreCaseSet forceUpdateFields() {
		return null;
	}

	// --createSqlTimeFields、updateSqlTimeFields
	// 方法本质跟createUnifyFields、updateUnifyFields是重叠的
	// 考虑应用部署环境问题，各个应用终端时间差异大，采用数据库时间，则启用此方法
	/**
	 * 创建记录时，直接代入sql insert into table()values(nvl(?,current_timestamp))时间的字段
	 * 只针对时间类型生效 注意: createUnifyFields中去除相同字段，避免被覆盖
	 * 
	 * @return
	 */
	public default IgnoreCaseSet createSqlTimeFields() {
		// 如:new IgnoreCaseSet().add("createTime").add("updateTime")
		return new IgnoreCaseSet();
	}

	/**
	 * 只针对date、localDate、timestamp等时间类型生效 修改记录时，直接代入update table set
	 * a=nvl(?,current_timestamp)时间的字段 注意: updateUnifyFields中去除相同字段，避免被覆盖
	 * 
	 * @return
	 */
	public default IgnoreCaseSet updateSqlTimeFields() {
		// 如:new IgnoreCaseSet().add("updateTime")
		return new IgnoreCaseSet();
	}

	// 主要用途针对自定义sql关于一些权限过滤部分统一传参数，可以阅读:org.sagacity.sqltoy.utils.QueryExecutorBuilder中的dataAuthFilter方法
	// 跟SqlInterceptors 无关系
	/**
	 * <p>
	 * 可设置如授权租户id:
	 * authTenantId-->S0001,sql语句中tenant_id=:authTenantId,框架则会自动将此值传递给sql
	 * </p>
	 * 
	 * @TODO 数据权限过滤，你可以灵活结合AOP、ThreadLocal技术实现不同场景注入不同的数据权限值
	 * @return
	 */
	public default IgnoreKeyCaseMap<String, DataAuthFilterConfig> dataAuthFilters() {
		// 演示传递当前用户的授权机构
		// IgnoreKeyCaseMap<String, DataAuthFilterConfig> map = new
		// IgnoreKeyCaseMap<String, DataAuthFilterConfig>();
		// 这里固定参数演示，真实场景用threadLocal等模式获取当前用户实际数据
		// Set<String> organIds = new HashSet<String>();
		// organIds.add("100004");
		// organIds.add("100005");
		// organIds.add("100007");
		// map.put("authedOrganIds", new
		// DataAuthFilterConfig().setForcelimit(true).setValues(organIds));
		// return map;
		return null;
	}

	// --- 参考:org.sagacity.sqltoy.plugins.interceptors.TenantFilterInterceptor ---/
	// 如果启用SqlInterceptors:xxxx.TenantInterceptor
	// sql查询租户隔离，建议要实现此方法，因为在分页缓存count环节需要此值作为key的组成
	/**
	 * @TODO 获取授权租户信息，传递表名和操作类型目的为程序可以控制返回:所在租户和授权租户 提供部分决策依据
	 *       一般你可以直接返回当前用户的授权租户id数组，主要用于SqlInterceptors，如自定义的TenantInterceptor
	 *       简单实现
	 * @param entityClass 可以为null
	 * @param operType 可以为null
	 * @return
	 */
	public default String[] authTenants(Class entityClass, OperateType operType) {
		// 你可以不用管entityClass、operType参数，直接返回当前用户授权的租户Id
		// 怎么获取? 通过filter将用户信息放入ThreadLocal，这里就随意获取了,请根据情况发挥
		// return getCurrentUserAuthedTenants();
		return null;
	}

	/**
	 * 返回当前用户的租户id
	 * 
	 * @return
	 */
	public default String getUserTenantId() {
		//return getUserTenant();
		return null;
	}
}
