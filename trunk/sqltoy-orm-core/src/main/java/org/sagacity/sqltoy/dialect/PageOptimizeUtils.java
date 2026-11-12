package org.sagacity.sqltoy.dialect;

import java.lang.ref.WeakReference;
import java.sql.Connection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import javax.sql.DataSource;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.model.OperateType;
import org.sagacity.sqltoy.config.model.PageOptimize;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.model.DBProfile;
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
 * @modify Date:2026-11-11 命名空间身份升级为"url+账号+schema/catalog"稳定档案(同库重建连接池可复用,
 *         数量随真实端点收敛),拿不到url时回退数据源对象身份;并增加命名空间硬上限与LRU淘汰,避免大量
 *         长生存数据源导致静态缓存线性增长(淘汰只影响命中率,不会产生错值)
 */
public class PageOptimizeUtils {
	private static final int INITIAL_CAPACITY = 128;
	private static final float LOAD_FACTOR = 0.75f;
	// 命名空间硬上限:超过后先清理数据源已回收的命名空间,仍超限则按最近访问时间淘汰最旧项;
	// 淘汰只影响命中率(下次重新count),不会产生错值
	static final int NAMESPACE_MAX_SIZE = 1024;
	// 命名空间LRU访问序号:单调递增,避免依赖毫秒时钟(同毫秒内多次访问时淘汰顺序不确定)
	private static final AtomicLong ACCESS_SEQ = new AtomicLong();
	// 数据源维度的会话账号:按数据源弱引用缓存(同一池内账号恒定),避免每次分页读元数据;
	// 弱引用保证动态数据源可回收(与命名空间弱引用同一动因)。
	// update 2026-11-12 schema/catalog不再缓存:同一连接池经setSchema()/setCatalog()切换多租户时,
	// 缓存首次会话会把身份钉死,不同schema共用count缓存(租户串用);账号读取失败也不缓存
	private static final Map<Object, String> SESSION_USER_CACHE = Collections
			.synchronizedMap(new WeakHashMap<Object, String>());

	/**
	 * 命名空间对应的条件缓存:aliveSeconds/aliveMax控制条件条目(FIFO),lastAccess供命名空间级LRU淘汰
	 */
	private static final class NamespaceCache {
		private final LinkedHashMap<String, Object[]> conditions = new LinkedHashMap<String, Object[]>();
		// 最近访问序号(单调递增),供命名空间级LRU淘汰
		private volatile long lastAccess = ACCESS_SEQ.incrementAndGet();
	}

	// 定义sql对应的分页查询请求(不同查询条件构成的key)对应的总记录数Object[] as
	// {expireTime(失效时间),recordCount(分页查询总记录数)}
	private static ConcurrentHashMap<CacheNamespace, NamespaceCache> pageOptimizeCache = new ConcurrentHashMap<CacheNamespace, NamespaceCache>(
			INITIAL_CAPACITY, LOAD_FACTOR);

	/**
	 * 缓存命名空间:sql标识(dialect)+数据源身份
	 * <p>
	 * 身份优先用"url+账号+schema/catalog"稳定档案(同一库重建连接池也可复用,命名空间数量随真实端点收敛);
	 * 拿不到url时回退数据源对象身份(==,避免不同数据源因hash冲突共用同一份count);对象身份用弱引用,
	 * 避免静态缓存长期持有动态创建的数据源。
	 * </p>
	 */
	private static final class CacheNamespace {
		private final String sqlId;
		private final String dialect;
		// 稳定身份(url+会话身份),null表示回退数据源对象身份
		private final String identity;
		// null表示不做隔离(历史重载方法)或身份已由identity表达;非null时弱引用
		private final WeakReference<Object> dataSourceRef;
		private final int hash;

		private CacheNamespace(String sqlId, String dialect, String identity, Object dataSourceRef) {
			this.sqlId = sqlId;
			this.dialect = dialect;
			this.identity = identity;
			this.dataSourceRef = (identity != null || dataSourceRef == null) ? null
					: new WeakReference<Object>(dataSourceRef);
			int hash = sqlId.hashCode();
			hash = 31 * hash + ((dialect == null) ? 0 : dialect.hashCode());
			this.hash = 31 * hash
					+ ((identity != null) ? identity.hashCode() : System.identityHashCode(dataSourceRef));
		}

		/**
		 * 数据源已被回收且无稳定身份:该命名空间不再可能被命中,可清理
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
			if (identity != null || other.identity != null) {
				return identity != null && identity.equals(other.identity);
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
		// update 2026-11-11 count缓存key的授权租户须与count实际下发SQL同一OperateType口径:
		// count SQL在DialectFactory按(实体为空?count:singleTable)装饰租户过滤,此处若固定用page,
		// 当handler按operateType返回不同租户集时,key与实际过滤维度错位,导致跨租户复用count
		String[] tenants = unifyFieldsHandler.authTenants(entityClass,
				(entityClass == null) ? OperateType.count : OperateType.singleTable);
		// update 2026-11-11 与TenantFilterInterceptor口径严格对齐:仅null视为"无租户";
		// 空串同样会被拦截器拼成 tenant_id=''(过滤语义),key必须包含,否则会与无租户查询共用count
		// (原isBlank过滤在空串租户下导致"SQL按空租户过滤、key却无租户"的跨租户count复用)
		if (tenants == null || tenants.length == 0) {
			String userTenantId = unifyFieldsHandler.getUserTenantId();
			if (userTenantId != null) {
				tenants = new String[] { userTenantId };
			}
		}
		if (tenants == null || tenants.length == 0) {
			return;
		}
		TreeSet<String> tenantSet = new TreeSet<String>();
		for (String tenant : tenants) {
			// null按空串处理:key构造不因异常值抛NPE;空串/空白保留,避免与拦截器过滤语义不一致
			tenantSet.add(tenant == null ? "" : tenant);
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
		return getPageTotalCount(sqlToyConfig, pageOptimize, conditionsKey, dialect, sqlToyContext, dataSource, null);
	}

	/**
	 * @param dataSourceKey 稳定数据源身份({@link #buildDataSourceKey}),null时回退数据源对象身份
	 */
	public static Long getPageTotalCount(final SqlToyConfig sqlToyConfig, PageOptimize pageOptimize,
			String conditionsKey, String dialect, SqlToyContext sqlToyContext, DataSource dataSource,
			String dataSourceKey) {
		// sql初次执行查询
		NamespaceCache cache = pageOptimizeCache
				.get(buildNamespace(sqlToyConfig, dialect, sqlToyContext, dataSource, dataSourceKey));
		if (cache == null) {
			return null;
		}
		cache.lastAccess = ACCESS_SEQ.incrementAndGet();
		LinkedHashMap<String, Object[]> map = cache.conditions;
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
		registPageTotalCount(sqlToyConfig, pageOptimize, conditionsKey, totalCount, dialect, sqlToyContext, dataSource,
				null);
	}

	/**
	 * @param dataSourceKey 稳定数据源身份({@link #buildDataSourceKey}),null时回退数据源对象身份
	 */
	public static void registPageTotalCount(final SqlToyConfig sqlToyConfig, PageOptimize pageOptimize,
			String conditionsKey, Long totalCount, String dialect, SqlToyContext sqlToyContext, DataSource dataSource,
			String dataSourceKey) {
		long nowTime = System.currentTimeMillis();
		// 当前时间
		long expireTime = nowTime + pageOptimize.getAliveSeconds() * 1000;
		// 同一个分页查询sql保留的不同查询条件记录数量
		int aliveMax = pageOptimize.getAliveMax();
		// sql id或sql内容 + 方言 + 实际数据源:同一sql在不同方言/不同库下分别缓存,避免count互相串用
		CacheNamespace namespace = buildNamespace(sqlToyConfig, dialect, sqlToyContext, dataSource, dataSourceKey);
		evictIfNeeded();
		NamespaceCache cache = pageOptimizeCache.computeIfAbsent(namespace, k -> new NamespaceCache());
		cache.lastAccess = ACCESS_SEQ.incrementAndGet();
		LinkedHashMap<String, Object[]> map = cache.conditions;
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

	/**
	 * 命名空间有界化:超过上限先清理数据源已回收项,仍超限则淘汰最久未访问的命名空间;
	 * 淘汰只导致该命名空间后续重新count(性能),不会返回错值
	 */
	private static void evictIfNeeded() {
		if (pageOptimizeCache.size() < NAMESPACE_MAX_SIZE) {
			return;
		}
		pageOptimizeCache.keySet().removeIf(CacheNamespace::isExpired);
		if (pageOptimizeCache.size() < NAMESPACE_MAX_SIZE) {
			return;
		}
		CacheNamespace oldest = null;
		long oldestAccess = Long.MAX_VALUE;
		for (Map.Entry<CacheNamespace, NamespaceCache> entry : pageOptimizeCache.entrySet()) {
			long lastAccess = entry.getValue().lastAccess;
			if (lastAccess < oldestAccess) {
				oldestAccess = lastAccess;
				oldest = entry.getKey();
			}
		}
		if (oldest != null) {
			pageOptimizeCache.remove(oldest);
		}
	}

	/**
	 * 构造分页count缓存用的稳定数据源身份:url+账号/schema/catalog。
	 * <p>
	 * url已由DBProfile按连接档案缓存;账号按数据源弱引用缓存(同一池内恒定);schema/catalog每次从当前
	 * 连接读取(同一池setSchema/setCatalog切换多租户时身份随会话变化,不串用count缓存)。
	 * 同一库重建连接池可复用缓存,命名空间数量随真实"库端点×会话"收敛;url不可用(非JDBC/无档案)时返回null,
	 * 调用侧回退数据源对象身份。
	 * </p>
	 *
	 * @param profile    连接档案(提供url)
	 * @param conn       当前连接(提供账号/schema/catalog),可为null
	 * @param dataSource 执行用数据源(作为会话身份缓存键),可为null
	 * @return 稳定身份串;url不可用时null
	 */
	public static String buildDataSourceKey(DBProfile profile, Connection conn, DataSource dataSource) {
		if (profile == null || StringUtil.isBlank(profile.getUrl())) {
			return null;
		}
		return profile.getUrl() + '\u0000' + sessionIdentity(dataSource, conn);
	}

	private static String sessionIdentity(DataSource dataSource, Connection conn) {
		if (conn == null) {
			return "";
		}
		// update 2026-11-12 schema/catalog每次从当前连接读取(多租户schema切换场景身份必须随会话变化);
		// 仅账号按数据源缓存,读取失败不缓存,避免瞬时元数据异常把空身份永久钉死
		String userName = (dataSource == null) ? readUserName(conn) : cachedUserName(dataSource, conn);
		String schema = null;
		String catalog = null;
		// update 2026-11-12 评审R4:catch Throwable——老驱动未实现JDBC4.1的getSchema/getCatalog
		// 时抛AbstractMethodError(Error非Exception),元数据读取失败不得影响分页
		try {
			schema = conn.getSchema();
		} catch (Throwable ignore) {
		}
		try {
			catalog = conn.getCatalog();
		} catch (Throwable ignore) {
		}
		return (userName == null ? "" : userName) + '\u0001' + (schema == null ? "" : schema) + '\u0001'
				+ (catalog == null ? "" : catalog);
	}

	private static String cachedUserName(DataSource dataSource, Connection conn) {
		String cached = SESSION_USER_CACHE.get(dataSource);
		if (cached != null) {
			return cached;
		}
		String userName = readUserName(conn);
		if (userName != null && !userName.isEmpty()) {
			SESSION_USER_CACHE.put(dataSource, userName);
		}
		return userName;
	}

	private static String readUserName(Connection conn) {
		try {
			return conn.getMetaData().getUserName();
		} catch (Throwable ignore) {
			return null;
		}
	}

	private static CacheNamespace buildNamespace(SqlToyConfig sqlToyConfig, String dialect, SqlToyContext sqlToyContext,
			DataSource dataSource) {
		return buildNamespace(sqlToyConfig, dialect, sqlToyContext, dataSource, null);
	}

	private static CacheNamespace buildNamespace(SqlToyConfig sqlToyConfig, String dialect, SqlToyContext sqlToyContext,
			DataSource dataSource, String dataSourceKey) {
		String realDialect = StringUtil.isBlank(dialect) ? null : dialect.toLowerCase(Locale.ROOT);
		String identity = StringUtil.isBlank(dataSourceKey) ? null : dataSourceKey;
		return new CacheNamespace(sqlToyConfig.getIdOrSql(), realDialect, identity,
				(identity == null) ? resolveDataSourceRef(sqlToyContext, dataSource) : null);
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

	/**
	 * @return 当前命名空间数量(单测断言上限用,非公共API)
	 */
	static int namespaceCount() {
		return pageOptimizeCache.size();
	}
}
