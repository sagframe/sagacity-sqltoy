/**
 * 
 */
package org.sagacity.sqltoy.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

import javax.sql.DataSource;

import org.sagacity.sqltoy.config.model.PageOptimize;
import org.sagacity.sqltoy.config.model.SecureMask;
import org.sagacity.sqltoy.config.model.Translate;

/**
 * @project sqltoy-orm
 * @description 针对EntityQuery构造一个存放参数的内部类，避免EntityQuery使用时带出大量的get方法
 * @author renfei.chen <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:EntityQueryExtend.java,Revision:v1.0,Date:2020-8-1
 */
public class EntityQueryExtend implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 5446320176141577000L;

	/**
	 * 条件语句
	 */
	public String where;

	/**
	 * 参数名称
	 */
	public String[] names;

	/**
	 * 参数值
	 */
	public Object[] values;

	/**
	 * 查询哪些字段
	 */
	public String[] fields;

	/**
	 * 数据源
	 */
	public DataSource dataSource;

	/**
	 * 锁类型
	 */
	public LockMode lockMode;

	/**
	 * 空白字符转为null，默认为true
	 */
	public boolean blankToNull = true;

	/**
	 * 动态增加缓存翻译配置
	 */
	public HashMap<String, Translate> translates = new HashMap<String, Translate>();

	/**
	 * 动态组织的order by 排序
	 */
	public LinkedHashMap<String, String> orderBy = new LinkedHashMap<String, String>();

	/**
	 * 动态设置filters
	 */
	public List<ParamsFilter> paramFilters = new ArrayList<ParamsFilter>();

	/**
	 * 对字段进行安全脱敏
	 */
	public LinkedHashMap<String, SecureMask> secureMask = new LinkedHashMap<String, SecureMask>();

	/**
	 * 分页优化模型
	 */
	public PageOptimize pageOptimize;

	/**
	 * 结果类型
	 */
	//public Class resultType;

}
