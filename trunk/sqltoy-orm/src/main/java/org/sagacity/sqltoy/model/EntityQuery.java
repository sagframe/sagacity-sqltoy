package org.sagacity.sqltoy.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

import javax.sql.DataSource;

import org.sagacity.sqltoy.config.model.Translate;
import org.sagacity.sqltoy.utils.CollectionUtil;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * @description 提供给代码中进行查询使用，一般适用于接口服务内部逻辑处理以单表为主体(不用于页面展示)
 * @author renfei.chen <a href="mailto:zhongxuchen@hotmail.com">联系作者</a>
 * @version id:EntityQuery.java,Revision:v1.0,Date:2020-5-15
 */
public class EntityQuery implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 5223170071884950204L;

	public static EntityQuery create() {
		return new EntityQuery();
	}

	/**
	 * 条件语句
	 */
	private String where;

	/**
	 * 参数名称
	 */
	private String[] names;

	/**
	 * 参数值
	 */
	private Object[] values;

	private DataSource dataSource;

	/**
	 * 锁类型
	 */
	private LockMode lockMode;

	/**
	 * 动态增加缓存翻译配置
	 */
	private HashMap<String, Translate> extendsTranslates = new HashMap<String, Translate>();

	/**
	 * 动态组织的order by 排序
	 */
	private LinkedHashMap<String, String> orderBy = new LinkedHashMap<String, String>();

	/**
	 * 动态设置filters
	 */
	private List<ParamsFilter> paramFilters = new ArrayList<ParamsFilter>();

	public EntityQuery where(String where) {
		this.where = where;
		return this;
	}

	public EntityQuery names(String... names) {
		this.names = names;
		return this;
	}

	public EntityQuery values(Object... values) {
		this.values = values;
		return this;
	}

	public EntityQuery orderBy(String field) {
		// 默认为升序
		orderBy.put(field, " ");
		return this;
	}

	public EntityQuery orderByDesc(String field) {
		orderBy.put(field, " desc ");
		return this;
	}

	public EntityQuery lock(LockMode lockMode) {
		this.lockMode = lockMode;
		return this;
	}

	/**
	 * @TODO 动态增加参数过滤,对参数进行转null或其他的加工处理
	 * @param filters
	 * @return
	 */
	public EntityQuery filters(ParamsFilter... filters) {
		if (filters != null && filters.length > 0) {
			for (ParamsFilter filter : filters) {
				if (StringUtil.isBlank(filter.getType()) || StringUtil.isBlank(filter.getParams())) {
					throw new IllegalArgumentException("针对EntityQuery设置条件过滤必须要设置参数名称和过滤的类型!");
				}
				if (CollectionUtil.any(filter.getType(), "eq", "neq", "gt", "gte", "lt", "lte")) {
					if (StringUtil.isBlank(filter.getValue())) {
						throw new IllegalArgumentException("针对EntityQuery设置条件过滤eq、neq、gt、lt等类型必须要设置values值!");
					}
				}
				paramFilters.add(filter);
			}
		}
		return this;
	}

	/**
	 * @TODO 对sql语句指定缓存翻译
	 * @param translates
	 * @return
	 */
	public EntityQuery translates(Translate... translates) {
		for (Translate trans : translates) {
			if (StringUtil.isBlank(trans.getCache()) || StringUtil.isBlank(trans.getKeyColumn())
					|| StringUtil.isBlank(trans.getColumn())) {
				throw new IllegalArgumentException(
						"针对EntityQuery设置缓存翻译必须要明确:cacheName、keyColumn(作为key的字段列)、 column(翻译结果映射的列)!");
			}
			extendsTranslates.put(trans.getColumn(), trans);
		}
		return this;
	}

	public EntityQuery dataSource(DataSource dataSource) {
		this.dataSource = dataSource;
		return this;
	}

	/**
	 * @return the where
	 */
	public String getWhere() {
		return where;
	}

	/**
	 * @return the names
	 */
	public String[] getNames() {
		return names;
	}

	/**
	 * @return the values
	 */
	public Object[] getValues() {
		return values;
	}

	/**
	 * @return the dataSource
	 */
	public DataSource getDataSource() {
		return dataSource;
	}

	public LockMode getLockMode() {
		return lockMode;
	}

	public LinkedHashMap<String, String> getOrderBy() {
		return orderBy;
	}

	/**
	 * @TODO 获取自定义的缓存翻译配置
	 * @return
	 */
	public HashMap<String, Translate> getTranslates() {
		return this.extendsTranslates;
	}

	/**
	 * @return the paramFilters
	 */
	public List<ParamsFilter> getParamFilters() {
		return paramFilters;
	}
}
