package org.sagacity.sqltoy.model;

import java.io.Serializable;

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

	private QueryExtend extend = new QueryExtend();

//	/**
//	 * 条件语句
//	 */
//	private String where;
//
//	/**
//	 * 参数名称
//	 */
//	private String[] names;
//
//	/**
//	 * 参数值
//	 */
//	private Object[] values;
//
//	private DataSource dataSource;
//
//	/**
//	 * 锁类型
//	 */
//	private LockMode lockMode;
//
//	/**
//	 * 动态增加缓存翻译配置
//	 */
//	private HashMap<String, Translate> extendsTranslates = new HashMap<String, Translate>();
//
//	/**
//	 * 动态组织的order by 排序
//	 */
//	private LinkedHashMap<String, String> orderBy = new LinkedHashMap<String, String>();
//
//	/**
//	 * 动态设置filters
//	 */
//	private List<ParamsFilter> paramFilters = new ArrayList<ParamsFilter>();

	public EntityQuery where(String where) {
		extend.where = where;
		return this;
	}

	public EntityQuery names(String... names) {
		extend.names = names;
		return this;
	}

	public EntityQuery values(Object... values) {
		extend.values = values;
		return this;
	}

	public EntityQuery orderBy(String field) {
		// 默认为升序
		extend.orderBy.put(field, " ");
		return this;
	}

	public EntityQuery orderByDesc(String field) {
		extend.orderBy.put(field, " desc ");
		return this;
	}

	public EntityQuery lock(LockMode lockMode) {
		extend.lockMode = lockMode;
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
				extend.paramFilters.add(filter);
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
			extend.translates.put(trans.getColumn(), trans);
		}
		return this;
	}

	public EntityQuery dataSource(DataSource dataSource) {
		extend.dataSource = dataSource;
		return this;
	}

	public QueryExtend getExtend() {
		return extend;
	}
}
