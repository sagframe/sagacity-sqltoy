package org.sagacity.sqltoy.model;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import org.sagacity.sqltoy.callback.SelectFields;
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
 * @version v1.0,Date:2020-5-15
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
	 * 通过扩展对象减少EntityQuery里面的大量get方法，减少对开发过程的影响
	 */
	private EntityQueryExtend innerModel = new EntityQueryExtend();

	/**
	 * @TODO 设置查询的字段(不设置默认查询全部字段)
	 * @param fields
	 * @return
	 */
	public EntityQuery select(String... fields) {
		if (fields != null && fields.length > 0) {
			// 支持"fieldA,fieldB" 这种模式编写
			if (fields.length == 1) {
				innerModel.fields = fields[0].split("\\,");
				StringUtil.arrayTrim(innerModel.fields);
			} else {
				innerModel.fields = fields;
			}
			innerModel.notSelectFields = null;
		}
		return this;
	}

	/**
	 * @TODO 设置jdbc参数，一般无需设置
	 * @param fetchSize
	 * @return
	 */
	public EntityQuery fetchSize(int fetchSize) {
		innerModel.fetchSize = fetchSize;
		return this;
	}

	public EntityQuery maxRows(int maxRows) {
		innerModel.maxRows = maxRows;
		return this;
	}

	public EntityQuery distinct() {
		innerModel.distinct = true;
		return this;
	}

	/**
	 * @TODO 用链式模式实现字段选择
	 * @param selectFields
	 * @return
	 */
	public EntityQuery select(SelectFields selectFields) {
		if (selectFields != null) {
			innerModel.fields = selectFields.getSelectFields();
			innerModel.notSelectFields = null;
		}
		return this;
	}

	/**
	 * @TODO 不查询哪些字段(排除的字段)
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
				notFields.add(field.trim().replace("_", "").toLowerCase());
			}
			innerModel.notSelectFields = notFields;
			// 不能共存
			innerModel.fields = null;
		}
		return this;
	}

	/**
	 * @TODO where 条件
	 * @param where
	 * @return
	 */
	public EntityQuery where(String where) {
		innerModel.where = where;
		return this;
	}

	public EntityQuery names(String... names) {
		innerModel.names = names;
		return this;
	}

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
	 * @see 直接使用values(map)传参,逐步减少过多的方法
	 * @TODO 用map形式传参
	 * @param paramsMap
	 * @return
	 */
	@Deprecated
	public EntityQuery paramsMap(Map<String, Object> paramsMap) {
		innerModel.values = new Object[] { new IgnoreKeyCaseMap(paramsMap) };
		return this;
	}

	/**
	 * @TODO 设置条件过滤空白转null为false
	 * @return
	 */
	public EntityQuery blankNotNull() {
		innerModel.blankToNull = false;
		return this;
	}

	// 排序
	public EntityQuery orderBy(String... fields) {
		// 默认为升序
		if (fields != null && fields.length > 0) {
			String[] realFields;
			if (fields.length == 1) {
				realFields = fields[0].split("\\,");
			} else {
				realFields = fields;
			}
			for (String field : realFields) {
				innerModel.orderBy.put(field.trim(), " ");
			}
		}
		return this;
	}

	// 逆序
	public EntityQuery orderByDesc(String... fields) {
		if (fields != null && fields.length > 0) {
			String[] realFields;
			if (fields.length == 1) {
				realFields = fields[0].split("\\,");
			} else {
				realFields = fields;
			}
			for (String field : realFields) {
				innerModel.orderBy.put(field.trim(), " desc ");
			}
		}
		return this;
	}

	/**
	 * @TODO 锁记录
	 * @param lockMode
	 * @return
	 */
	public EntityQuery lock(LockMode lockMode) {
		innerModel.lockMode = lockMode;
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
	 * @TODO 对结果字段进行安全脱敏
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
	 * @TODO 动态增加参数过滤,对参数进行转null或其他的加工处理
	 * @param filters
	 * @return
	 */
	public EntityQuery filters(ParamsFilter... filters) {
		if (filters != null && filters.length > 0) {
			for (ParamsFilter filter : filters) {
				if (StringUtil.isBlank(filter.getType()) || StringUtil.isBlank(filter.getParams())) {
					throw new IllegalArgumentException("针对EntityQuery设置条件过滤必须要设置filterParams=[" + filter.getParams()
							+ "],和filterType=[" + filter.getType() + "]!");
				}
				if (CollectionUtil.any(filter.getType(), "eq", "neq", "gt", "gte", "lt", "lte", "between")) {
					if (StringUtil.isBlank(filter.getValue())) {
						throw new IllegalArgumentException(
								"针对EntityQuery设置条件过滤eq、neq、gt、gte、lt、lte、between等类型必须要设置values值!");
					}
				}
				// 存在blank 过滤器自动将blank param="*" 关闭
				if (filter.getType().equals("blank")) {
					innerModel.blankToNull = false;
				}
				innerModel.paramFilters.add(filter);
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
		if (translates != null && translates.length > 0) {
			TranslateExtend extend;
			for (Translate trans : translates) {
				extend = trans.getExtend();
				if (StringUtil.isBlank(extend.cache) || StringUtil.isBlank(extend.keyColumn)
						|| StringUtil.isBlank(extend.column)) {
					throw new IllegalArgumentException(
							"针对EntityQuery设置缓存翻译必须要明确:cacheName=[" + extend.cache + "]、keyColumn=[" + extend.keyColumn
									+ "](作为key的字段列)、 column=[" + extend.column + "](翻译结果映射的列)!");
				}
				innerModel.translates.put(extend.column, trans);
			}
		}
		return this;
	}

	public EntityQuery dataSource(DataSource dataSource) {
		innerModel.dataSource = dataSource;
		return this;
	}

	/**
	 * @TODO 分页优化
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
	 * @TODO 取top记录
	 * @param topSize
	 * @return
	 */
	public EntityQuery top(double topSize) {
		if (topSize <= 0) {
			throw new IllegalArgumentException("topSize 值必须要大于0!");
		}
		innerModel.pickType = 0;
		innerModel.pickSize = topSize;
		return this;
	}

	/**
	 * @TODO 取随机记录
	 * @param randomSize
	 * @return
	 */
	public EntityQuery random(double randomSize) {
		if (randomSize <= 0) {
			throw new IllegalArgumentException("randomSize 值必须要大于0!");
		}
		innerModel.pickType = 1;
		innerModel.pickSize = randomSize;
		return this;
	}

	/**
	 * @TODO 设置分库策略
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
	 * @TODO 设置分表策略,再复杂场景则推荐用xml的sql中定义
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
	 * @TODO 设置执行时是否输出sql日志
	 * @param showSql
	 * @return
	 */
	public EntityQuery showSql(Boolean showSql) {
		innerModel.showSql = showSql;
		return this;
	}

	public EntityQueryExtend getInnerModel() {
		return innerModel;
	}
}
