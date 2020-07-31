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

import org.sagacity.sqltoy.config.model.Translate;

/**
 * @author zhong
 *
 */
public class QueryExtend implements Serializable {

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

	public DataSource dataSource;

	/**
	 * 锁类型
	 */
	public LockMode lockMode;

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

}
