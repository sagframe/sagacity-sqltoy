package org.sagacity.sqltoy.model;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import org.sagacity.sqltoy.config.model.PageOptimize;
import org.sagacity.sqltoy.config.model.SecureMask;
import org.sagacity.sqltoy.config.model.ShardingStrategyConfig;
import org.sagacity.sqltoy.config.model.Translate;
import org.sagacity.sqltoy.model.inner.EntityQueryExtend;
import org.sagacity.sqltoy.model.inner.TranslateExtend;
import org.sagacity.sqltoy.utils.CollectionUtil;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * @description 提供给代码中进行查询使用，一般适用于接口服务内部逻辑处理以单表为主体(不用于页面展示)
 * @author zhongxuchen
 * @version v1.0,Date:2020-05-15
 */
public class EntityQuery implements Serializable {

	private static final long serialVersionUID = 5223170071884950204L;

	public static EntityQuery create() {
		return new EntityQuery();
	}

	/**
	 * 通过扩展对象减少EntityQuery里面的大量get方法，减少对开发过程的影响
	 */
	private EntityQueryExtend innerModel = new EntityQueryExtend();

	/**
	 * 设置查询的字段(不设置默认查询全部字段)
	 * 
	 * @param fields
	 * @return
	 */
	public EntityQuery select(String... fields) {
		if (fields != null && fields.length > 0) {
			// 支持"fieldA,fieldB" 这种模式编写
			if (fields.length == 1) {
				innerModel.fields = StringUtil.trimArray(fields[0].split("\\,"));
			} else {
				innerModel.fields = fields;
			}
			innerModel.notSelectFields = null;
		}
		return this;
	}

	/**
	 * 设置jdbc参数，一般无需设置
	 * 
	 * @param fetchSize
	 * @return
	 */
	public EntityQuery fetchSize(int fetchSize) {
		innerModel.fetchSize = fetchSize;
		return this;
	}

	/**
	 * 设置jdbc pst查询最大记录数,一般不会涉及
	 * 
	 * @param maxRows
	 * @return
	 */
	@Deprecated
	public EntityQuery maxRows(int maxRows) {
		innerModel.maxRows = maxRows;
		return this;
	}

	public EntityQuery timeout(Integer timeout) {
		if (timeout != null && timeout > 0) {
			innerModel.timeout = timeout;
		}
		return this;
	}

	/**
	 * 查询时增加distinct
	 * 
	 * @return
	 */
	public EntityQuery distinct() {
		innerModel.distinct = true;
		return this;
	}

	/**
	 * 不查询哪些字段(排除的字段)
	 * 
	 * @param fields
	 * @return
	 */
	public EntityQuery unselect(String... fields) {
		if (fields != null && fields.length > 0) {
			String[] realFields;
			if (fields.length == 1) {
				realFields = fields[0].split("\\,");
			} else {
				realFields = fields;
			}
			Set<String> notFields = new HashSet<String>();
			for (String field : realFields) {
				notFields.add(field.trim().replace("_", "").toLowerCase(Locale.ROOT));
			}
			innerModel.notSelectFields = notFields;
			// 不能共存
			innerModel.fields = null;
		}
		return this;
	}

	/**
	 * where 条件，例如: "#[name like :name ] #[and status in (:status)]"
	 * 
	 * @param where
	 * @return
	 */
	public EntityQuery where(String where) {
		innerModel.where = where;
		return this;
	}

	/**
	 * 设置where中涉及的参数
	 * <p>
	 * EntityQuery.create().where("status=:status").names("status").values(1)
	 * </p>
	 * 
	 * @param names
	 * @return
	 */
	public EntityQuery names(String... names) {
		innerModel.names = names;
		return this;
	}

	/**
	 * <p>
	 * 1、EntityQuery.create().where("status=:status").names("status").values(1)
	 * 2、EntityQuery.create().where("status=?").values(1)
	 * 3、EntityQuery.create().where("status=:status and staffName like
	 * :staffName").values(staffInfo对象实体)
	 * 4、EntityQuery.create().where("status=:status").values(map.put("status",1))
	 * </p>
	 * 
	 * @param values
	 * @return
	 */
	public EntityQuery values(Object... values) {
		// 兼容map
		if (values != null && values.length == 1 && values[0] != null && values[0] instanceof Map) {
			innerModel.values = new Object[] { new IgnoreKeyCaseMap((Map) values[0]) };
		} else {
			innerModel.values = values;
		}
		return this;
	}

	/**
	 * @see 5.1.9 启动 EntityQuery.create().values(map)模式传参模式
	 *      用map形式传参，EntityQuery.create().values(map) 模式也可以兼容
	 * @param paramsMap
	 * @return
	 */
	@Deprecated
	public EntityQuery paramsMap(Map<String, Object> paramsMap) {
		innerModel.values = new Object[] { new IgnoreKeyCaseMap(paramsMap) };
		return this;
	}

	/**
	 * 设置条件过滤空白转null为false
	 * 
	 * @return
	 */
	public EntityQuery blankNotNull() {
		innerModel.blankToNull = false;
		return this;
	}

	/**
	 * 设置排序默认为升序，如:EntityQuery.create().orderBy("status")
	 * 
	 * @param fields
	 * @return
	 */
	public EntityQuery orderBy(String... fields) {
		// 默认为升序
		return wrapOrder(" ", fields);
	}

	// 逆序
	public EntityQuery orderByDesc(String... fields) {
		return wrapOrder(" desc ", fields);
	}

	private EntityQuery wrapOrder(String orderWay, String... fields) {
		if (fields != null && fields.length > 0) {
			String[] realFields;
			if (fields.length == 1) {
				realFields = fields[0].split("\\,");
			} else if (fields.length == 2) {
				// 排序为空，默认为asc
				String sortWay = (fields[1] == null) ? "asc" : fields[1].trim().toLowerCase(Locale.ROOT);
				// 排序字段为空当作无效参数
				if (StringUtil.isBlank(fields[0])) {
					return this;
				}
				// {field,sortWay} 模式，field中间无空格,第二个字符串是desc或asc
				if (!StringUtil.matches(fields[0].trim(), "\\s+")
						&& (sortWay.equals("asc") || sortWay.equals("desc"))) {
					realFields = new String[] { fields[0].concat(" ").concat(fields[1]) };
				} else {
					realFields = fields;
				}
			} else {
				realFields = fields;
			}
			// update 2025-5-15 增加字段内空白切割，判断是否已经存在desc或asc
			String[] fieldAndSort;
			for (String field : realFields) {
				if (field != null && !field.trim().equals("")) {
					fieldAndSort = field.trim().split("\\s+");
					innerModel.orderBy.put(fieldAndSort[0],
							(fieldAndSort.length > 1) ? " ".concat(fieldAndSort[1]).concat(" ") : orderWay);
				}
			}
		}
		return this;
	}

	public EntityQuery groupBy(String... groups) {
		if (groups != null && groups.length > 0) {
			innerModel.groupBy = StringUtil.linkAry(",", true, groups);
		}
		return this;
	}

	public EntityQuery having(String having) {
		innerModel.having = having;
		return this;
	}

	/**
	 * 锁记录
	 * 
	 * @param lockMode
	 * @return
	 */
	public EntityQuery lock(LockMode lockMode) {
		innerModel.lockMode = lockMode;
		return this;
	}

	/**
	 * 锁等待时长(秒)
	 * 
	 * @param lockWaitTimeout
	 * @return
	 */
	public EntityQuery lockWaitTimeout(int lockWaitTimeout) {
		innerModel.lockWaitTimeout = lockWaitTimeout;
		return this;
	}

	/**
	 * 对结果字段进行安全脱敏
	 * 
	 * @param maskType
	 * @param columns
	 * @return
	 */
	public EntityQuery secureMask(MaskType maskType, String... columns) {
		if (maskType != null && columns != null && columns.length > 0) {
			for (String column : columns) {
				SecureMask mask = new SecureMask();
				mask.setColumn(column);
				mask.setType(maskType.getValue());
				innerModel.secureMask.put(column, mask);
			}
		}
		return this;
	}

	/**
	 * 指定解密列
	 * 
	 * @param columns
	 * @return
	 */
	public EntityQuery secureDecrypt(String... columns) {
		if (columns != null && columns.length > 0) {
			for (String column : columns) {
				innerModel.decryptColumns.add(column);
			}
		}
		return this;
	}

	/**
	 * 动态增加参数过滤,对参数进行转null或其他的加工处理
	 * 
	 * @param filters
	 * @return
	 */
	public EntityQuery filters(ParamsFilter... filters) {
		if (filters != null && filters.length > 0) {
			for (ParamsFilter filter : filters) {
				if (StringUtil.isBlank(filter.getType()) || StringUtil.isBlank(filter.getParams())) {
					throw new IllegalArgumentException("EntityQuery filters require filterParams=[" + filter.getParams()
							+ "] and filterType=[" + filter.getType() + "], please check!");
				}
				// 类别是对比型的，需要设置value值进行对比
				if (CollectionUtil.any(filter.getType(), "eq", "neq", "gt", "gte", "lt", "lte", "between")) {
					if (StringUtil.isBlank(filter.getValue())) {
						throw new IllegalArgumentException(
								"EntityQuery filters with type eq,neq,gt,gte,lt,lte,between require values to be set!");
					}
				}
				// 存在blank 过滤器自动将blank param="*" 关闭
				if ("blank".equals(filter.getType())) {
					innerModel.blankToNull = false;
				}
				innerModel.paramFilters.add(filter);
			}
		}
		return this;
	}

	/**
	 * 对sql语句指定缓存翻译
	 * 
	 * @param translates
	 * @return
	 */
	public EntityQuery translates(Translate... translates) {
		if (translates != null && translates.length > 0) {
			TranslateExtend extend;
			for (Translate trans : translates) {
				extend = trans.getExtend();
				if (StringUtil.isBlank(extend.cache) || StringUtil.isBlank(extend.keyColumn)
						|| StringUtil.isBlank(extend.column)) {
					throw new IllegalArgumentException("EntityQuery translate must define: cacheName=[" + extend.cache
							+ "], keyColumn=[" + extend.keyColumn + "] (the key property column), column=["
							+ extend.column + "] (the column mapped to the translate result), please check!");
				}
				innerModel.translates.add(trans);
			}
		}
		return this;
	}

	public EntityQuery dataSource(DataSource dataSource) {
		innerModel.dataSource = dataSource;
		return this;
	}

	/**
	 * 分页优化
	 * 
	 * @param pageOptimize
	 * @return
	 */
	public EntityQuery pageOptimize(PageOptimize pageOptimize) {
		if (pageOptimize != null) {
			innerModel.pageOptimize = pageOptimize;
		}
		return this;
	}

	/**
	 * 取top记录
	 * 
	 * @param topSize
	 * @return
	 */
	public EntityQuery top(double topSize) {
		if (topSize <= 0) {
			throw new IllegalArgumentException("topSize must be greater than 0!");
		}
		innerModel.pickType = 0;
		innerModel.pickSize = topSize;
		return this;
	}

	/**
	 * 取随机记录
	 * 
	 * @param randomSize
	 * @return
	 */
	public EntityQuery random(double randomSize) {
		if (randomSize <= 0) {
			throw new IllegalArgumentException("randomSize must be greater than 0!");
		}
		innerModel.pickType = 1;
		innerModel.pickSize = randomSize;
		return this;
	}

	/**
	 * 设置分库策略
	 * 
	 * @param strategy
	 * @param paramNames
	 * @return
	 */
	public EntityQuery dbSharding(String strategy, String... paramNames) {
		ShardingStrategyConfig sharding = new ShardingStrategyConfig(0);
		sharding.setStrategy(strategy);
		sharding.setFields(paramNames);
		sharding.setAliasNames(paramNames);
		innerModel.dbSharding = sharding;
		return this;
	}

	/**
	 * 设置分表策略,再复杂场景则推荐用xml的sql中定义
	 * 
	 * @param strategy
	 * @param paramNames 分表策略依赖的参数
	 * @return
	 */
	public EntityQuery tableSharding(String strategy, String... paramNames) {
		ShardingStrategyConfig sharding = new ShardingStrategyConfig(1);
		sharding.setStrategy(strategy);
		sharding.setFields(paramNames);
		sharding.setAliasNames(paramNames);
		innerModel.tableSharding = sharding;
		return this;
	}

	/**
	 * 设置执行时是否输出sql日志
	 * 
	 * @param showSql
	 * @return
	 */
	public EntityQuery showSql(Boolean showSql) {
		innerModel.showSql = showSql;
		return this;
	}

	/**
	 * 设置执行时上下文数据，如：在拦截器中取值用以业务判断
	 * 
	 * @param contextData
	 * @return
	 */
	public EntityQuery contextData(Object contextData) {
		innerModel.contextData = contextData;
		return this;
	}

	public EntityQueryExtend getInnerModel() {
		return innerModel;
	}
}
