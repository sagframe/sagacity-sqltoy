package org.sagacity.sqltoy.dialect;

import java.lang.ref.WeakReference;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import javax.sql.DataSource;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.model.OperateType;
import org.sagacity.sqltoy.config.model.PageOptimize;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.model.QueryExecutor;
import org.sagacity.sqltoy.model.inner.QueryExecutorExtend;
import org.sagacity.sqltoy.plugins.IUnifyFieldsHandler;
import org.sagacity.sqltoy.utils.CollectionUtil;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * @project sagacity-sqltoy
 * @description 提供分页优化缓存实现，记录相同查询条件的总记录数,采用FIFO算法保留符合活跃时间和记录规模
 * @author zhongxuchen
 * @version v1.0,Date:2016-11-24
 * @modify Date:2020-08-04 修改原本只支持xml中必须有id的sql才能缓存的策略,便于今后直接从代码中实现分页优化功能
 * @modify Date:2020-08-13 修改失效策略，在登记时只控制aliveMax，在获取时判定aliveSeconds清除过期的
 * @modify Date:2026-11-11 缓存命名空间纳入"实际数据源+方言",条件key纳入"有效租户集合"(authTenants为空回退
 *         getUserTenantId),避免同一sqlId在多租户/多数据源下复用其他租户或库的count
 */
public class PageOptimizeUtils {
	private static final int INITIAL_CAPACITY = 128;
	private static final float LOAD_FACTOR = 0.75f;
	// 命名空间数量超过该阈值时清理数据源已被回收的命名空间
	private static final int NAMESPACE_PURGE_THRESHOLD = 1024;

	// 定义sql对应的分页查询请求(不同查询条件构成的key)对应的总记录数Object[] as
	// {expireTime(失效时间),recordCount(分页查询总记录数)}
	private static ConcurrentHashMap<CacheNamespace, LinkedHashMap<String, Object[]>> pageOptimizeCache = new ConcurrentHashMap<CacheNamespace, LinkedHashMap<String, Object[]>>(
			INITIAL_CAPACITY, LOAD_FACTOR);

	/**
	 * 缓存命名空间:sql标识(dialect)+实际数据源身份
	 * <p>
	 * 用对象身份(==)而非hash值做比较,避免不同数据源因hash冲突共用同一份count;
	 * 无显式数据源时以SqlToyContext的默认数据源为界,默认数据源也为空则退化为context身份。
	 * </p>
	 */
	private static final class CacheNamespace {
		private final String sqlId;
		private final String dialect;
		// null表示不做数据源隔离(历史重载方法);非null时用弱引用,避免静态缓存长期持有动态创建的数据源
		private final WeakReference<Object> dataSourceRef;
		private final int hash;

		private CacheNamespace(String sqlId, String dialect, Object dataSourceRef) {
			this.sqlId = sqlId;
			this.dialect = dialect;
			this.dataSourceRef = (dataSourceRef == null) ? null : new WeakReference<Object>(dataSourceRef);
			int hash = sqlId.hashCode();
			hash = 31 * hash + ((dialect == null) ? 0 : dialect.hashCode());
			this.hash = 31 * hash + System.identityHashCode(dataSourceRef);
		}

		/**
		 * 数据源已被回收:该命名空间不再可能被命中,可清理
		 */
		private boolean isExpired() {
			return dataSourceRef != null && dataSourceRef.get() == null;
		}

		@Override
		public int hashCode() {
			return hash;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (!(obj instanceof CacheNamespace)) {
				return false;
			}
			CacheNamespace other = (CacheNamespace) obj;
			if (hash != other.hash || !sqlId.equals(other.sqlId) || !Objects.equals(dialect, other.dialect)) {
				return false;
			}
			if (dataSourceRef == null || other.dataSourceRef == null) {
				return dataSourceRef == other.dataSourceRef;
			}
			Object thisDataSource = dataSourceRef.get();
			// 数据源被回收后该命名空间不再匹配任何新查询(等待purge清理),避免身份复用造成串用
			return thisDataSource != null && thisDataSource == other.dataSourceRef.get();
		}
	}

	/**
	 * 根据查询条件组成key
	 *
	 * @param sqlToyContext
	 * @param sqlToyConfig
	 * @param queryExecutor
	 * @param pageOptimize
	 * @return
	 * @throws Exception
	 */
	@SuppressWarnings("rawtypes")
	public static String generateOptimizeKey(final SqlToyContext sqlToyContext, final SqlToyConfig sqlToyConfig,
			final QueryExecutor queryExecutor, PageOptimize pageOptimize) throws Exception {
		// 没有开放分页优化或sql id为null都不执行优化操作
		if (pageOptimize == null || pageOptimize.getAliveMax() <= 1 || pageOptimize.getAliveSeconds() < 1) {
			return null;
		}
		QueryExecutorExtend extend = queryExecutor.getInnerModel();
		String[] paramNames = extend.getParamsName();
		Object[] paramValues = extend.getParamsValue(sqlToyContext, sqlToyConfig);
		StringBuilder cacheKey = new StringBuilder();
		boolean isParamsNamed = (null != paramNames && paramNames.length > 0);
		int i = 0;
		// 循环查询条件的值构造key
		if (paramValues != null) {
			for (Object value : paramValues) {
				if (i > 0) {
					cacheKey.append(",");
				}
				if (isParamsNamed) {
					cacheKey.append(paramNames[i]).append("=");
				} else {
					cacheKey.append("p_").append(i).append("=");
				}
				if (value == null) {
					cacheKey.append("null");
				} else if ((value instanceof Object[]) || (value instanceof Iterable) || value.getClass().isArray()) {
					Object[] arrayValue = null;
					if (value instanceof List) {
						arrayValue = ((List) value).toArray();
					} else if (value instanceof Iterable) {
						arrayValue = CollectionUtil.iterableToArray((Iterable) value);
					} else {
						arrayValue = CollectionUtil.convertArray(value);
					}
					cacheKey.append("[");
					for (Object obj : arrayValue) {
						cacheKey.append((obj == null) ? "null" : obj.toString()).append(",");
					}
					cacheKey.append("]");
				} else {
					cacheKey.append(value.toString());
				}
				i++;
			}
		}
		// update 2026-11-11 租户维度无论有无查询参数都必须参与key:原实现无参数时直接返回sqlId,
		// 完全跳过租户信息,租户隔离的查询会复用其他租户的count
		appendTenantKey(cacheKey, sqlToyContext, extend.entityClass);
		// 无参数且无租户信息时保持原有语义(等价于sqlId)
		return (cacheKey.length() == 0) ? sqlToyConfig.getIdOrSql() : cacheKey.toString();
	}

	/**
	 * 追加有效租户集合:与TenantFilterInterceptor保持一致,授权租户为空时回退当前用户所在租户;
	 * 同一组租户按字典序拼装,避免返回顺序不同产生多份缓存
	 *
	 * @param cacheKey
	 * @param sqlToyContext
	 * @param entityClass
	 */
	@SuppressWarnings("rawtypes")
	private static void appendTenantKey(StringBuilder cacheKey, SqlToyContext sqlToyContext, Class entityClass) {
		if (sqlToyContext == null || sqlToyContext.getUnifyFieldsHandler() == null) {
			return;
		}
		IUnifyFieldsHandler unifyFieldsHandler = sqlToyContext.getUnifyFieldsHandler();
		String[] tenants = unifyFieldsHandler.authTenants(entityClass, OperateType.page);
		// update 2026-11-11 回退getUserTenantId:否则实际SQL按用户租户过滤而key不含租户
		if (tenants == null || tenants.length == 0) {
			String userTenantId = unifyFieldsHandler.getUserTenantId();
			if (!StringUtil.isBlank(userTenantId)) {
				tenants = new String[] { userTenantId };
			}
		}
		if (tenants == null || tenants.length == 0) {
			return;
		}
		TreeSet<String> tenantSet = new TreeSet<String>();
		for (String tenant : tenants) {
			if (!StringUtil.isBlank(tenant)) {
				tenantSet.add(tenant);
			}
		}
		if (tenantSet.isEmpty()) {
			return;
		}
		if (cacheKey.length() > 0) {
			cacheKey.append(",");
		}
		cacheKey.append("tenantIds=[");
		boolean isFirst = true;
		for (String tenant : tenantSet) {
			if (!isFirst) {
				cacheKey.append(",");
			}
			cacheKey.append(tenant);
			isFirst = false;
		}
		cacheKey.append("]");
	}

	/**
	 * 从缓存中获取具体sql相应条件的查询总记录数值
	 *
	 * @param sqlToyConfig
	 * @param pageOptimize
	 * @param conditionsKey
	 * @return
	 */
	public static Long getPageTotalCount(final SqlToyConfig sqlToyConfig, PageOptimize pageOptimize,
			String conditionsKey) {
		return getPageTotalCount(sqlToyConfig, pageOptimize, conditionsKey, sqlToyConfig.getDialect());
	}

	public static Long getPageTotalCount(final SqlToyConfig sqlToyConfig, PageOptimize pageOptimize,
			String conditionsKey, String dialect) {
		return getPageTotalCount(sqlToyConfig, pageOptimize, conditionsKey, dialect, null, null);
	}

	/**
	 * @param sqlToyContext 用于在未显式指定数据源时定位默认数据源,可为null(仅按context/空隔离)
	 * @param dataSource    实际执行查询的数据源(分库路由后的结果),可为null
	 */
	public static Long getPageTotalCount(final SqlToyConfig sqlToyConfig, PageOptimize pageOptimize,
			String conditionsKey, String dialect, SqlToyContext sqlToyContext, DataSource dataSource) {
		// sql初次执行查询
		LinkedHashMap<String, Object[]> map = pageOptimizeCache
				.get(buildNamespace(sqlToyConfig, dialect, sqlToyContext, dataSource));
		if (map == null) {
			return null;
		}
		synchronized (map) {
			// update 2026-9-8 为null表示条件初次查询或已经全部过期移除;containsKey+get双查合并为一次get
			Object[] values = map.get(conditionsKey);
			if (values == null) {
				return null;
			}
			// 失效时间
			long expireTime = (Long) values[0];
			// 总记录数
			Long totalCount = (Long) values[1];
			long nowTime = System.currentTimeMillis();
			// 已经失效
			if (nowTime >= expireTime) {
				// 剔除超时的记录
				map.remove(conditionsKey);
				return null;
			}
			// 没有过期则返回对应的总记录数值
			return totalCount;
		}
	}

	/**
	 * 将具体条件查询的记录数按照sql id放入缓存
	 *
	 * @param sqlToyConfig
	 * @param pageOptimize
	 * @param conditionsKey
	 * @param totalCount
	 */
	public static void registPageTotalCount(final SqlToyConfig sqlToyConfig, PageOptimize pageOptimize,
			String conditionsKey, Long totalCount) {
		registPageTotalCount(sqlToyConfig, pageOptimize, conditionsKey, totalCount, sqlToyConfig.getDialect());
	}

	public static void registPageTotalCount(final SqlToyConfig sqlToyConfig, PageOptimize pageOptimize,
			String conditionsKey, Long totalCount, String dialect) {
		registPageTotalCount(sqlToyConfig, pageOptimize, conditionsKey, totalCount, dialect, null, null);
	}

	/**
	 * @param sqlToyContext 用于在未显式指定数据源时定位默认数据源,可为null(仅按context/空隔离)
	 * @param dataSource    实际执行查询的数据源(分库路由后的结果),可为null
	 */
	public static void registPageTotalCount(final SqlToyConfig sqlToyConfig, PageOptimize pageOptimize,
			String conditionsKey, Long totalCount, String dialect, SqlToyContext sqlToyContext, DataSource dataSource) {
		long nowTime = System.currentTimeMillis();
		// 当前时间
		long expireTime = nowTime + pageOptimize.getAliveSeconds() * 1000;
		// 同一个分页查询sql保留的不同查询条件记录数量
		int aliveMax = pageOptimize.getAliveMax();
		// sql id或sql内容 + 方言 + 实际数据源:同一sql在不同方言/不同库下分别缓存,避免count互相串用
		CacheNamespace namespace = buildNamespace(sqlToyConfig, dialect, sqlToyContext, dataSource);
		// 数据源被回收后其命名空间不再可用,超过阈值时清理,避免静态缓存无限累积
		if (pageOptimizeCache.size() > NAMESPACE_PURGE_THRESHOLD) {
			pageOptimizeCache.keySet().removeIf(CacheNamespace::isExpired);
		}
		LinkedHashMap<String, Object[]> map = pageOptimizeCache.computeIfAbsent(namespace,
				k -> new LinkedHashMap<String, Object[]>(aliveMax));
		synchronized (map) {
			// 已经存在,先移除队列靠前的旧值
			if (map.containsKey(conditionsKey)) {
				map.remove(conditionsKey);
			}
			// 在最后位置放入最新的记录
			map.put(conditionsKey, new Object[] { expireTime, totalCount });
			// 长度超阀值,移除最早进入的
			while (map.size() > aliveMax) {
				map.remove(map.keySet().iterator().next());
			}
		}
	}

	private static CacheNamespace buildNamespace(SqlToyConfig sqlToyConfig, String dialect, SqlToyContext sqlToyContext,
			DataSource dataSource) {
		String realDialect = StringUtil.isBlank(dialect) ? null : dialect.toLowerCase(Locale.ROOT);
		return new CacheNamespace(sqlToyConfig.getIdOrSql(), realDialect,
				resolveDataSourceRef(sqlToyContext, dataSource));
	}

	/**
	 * 解析缓存隔离用的数据源身份:显式数据源优先,否则取context的默认数据源,
	 * 两者都没有时以context本身隔离(避免不同SqlToyContext的空数据源互相命中)
	 */
	private static Object resolveDataSourceRef(SqlToyContext sqlToyContext, DataSource dataSource) {
		if (dataSource != null) {
			return dataSource;
		}
		if (sqlToyContext == null) {
			return null;
		}
		DataSource defaultDataSource = sqlToyContext.getDefaultDataSource();
		return (defaultDataSource != null) ? defaultDataSource : sqlToyContext;
	}

	/**
	 * 清除掉sql对应的分页count缓存(含该sql的所有方言、所有数据源缓存)
	 *
	 * @param sqlId
	 */
	public static void remove(String sqlId) {
		if (sqlId == null) {
			return;
		}
		pageOptimizeCache.keySet().removeIf(namespace -> sqlId.equals(namespace.sqlId));
	}
}
