/**
 * 
 */
package org.sagacity.sqltoy.model;

import java.io.Serializable;
import java.lang.reflect.Type;
import java.math.RoundingMode;
import java.util.Map;

import javax.sql.DataSource;

import org.sagacity.sqltoy.callback.RowCallbackHandler;
import org.sagacity.sqltoy.config.model.ColsChainRelativeModel;
import org.sagacity.sqltoy.config.model.FormatModel;
import org.sagacity.sqltoy.config.model.LinkModel;
import org.sagacity.sqltoy.config.model.PageOptimize;
import org.sagacity.sqltoy.config.model.PivotModel;
import org.sagacity.sqltoy.config.model.RowsChainRelativeModel;
import org.sagacity.sqltoy.config.model.SecureMask;
import org.sagacity.sqltoy.config.model.ShardingStrategyConfig;
import org.sagacity.sqltoy.config.model.SummaryGroupMeta;
import org.sagacity.sqltoy.config.model.SummaryModel;
import org.sagacity.sqltoy.config.model.Translate;
import org.sagacity.sqltoy.config.model.TreeSortModel;
import org.sagacity.sqltoy.config.model.UnpivotModel;
import org.sagacity.sqltoy.model.inner.QueryExecutorExtend;
import org.sagacity.sqltoy.model.inner.TranslateExtend;
import org.sagacity.sqltoy.utils.BeanUtil;
import org.sagacity.sqltoy.utils.CollectionUtil;
import org.sagacity.sqltoy.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sqltoy-orm
 * @description 构造统一的查询条件模型
 * @author zhongxuchen
 * @version v1.0,Date:2012-9-3
 */
public class QueryExecutor implements Serializable {
	/**
	 * 定义日志
	 */
	protected final Logger logger = LoggerFactory.getLogger(QueryExecutor.class);

	/**
	 * 
	 */
	private static final long serialVersionUID = -6149173009738072148L;

	/**
	 * 扩展内部模型,减少过多get方法干扰开发
	 */
	private QueryExecutorExtend innerModel = new QueryExecutorExtend();

	public QueryExecutor(String sql) {
		innerModel.sql = sql;
	}

	/**
	 * @TODO sql和以entity对象实体传参模式
	 * @param sql
	 * @param entity 对象传参(可以是任意VO对象)
	 */
	public QueryExecutor(String sql, Serializable entity) {
		innerModel.sql = sql;
		innerModel.entity = entity;
		if (entity != null) {
			// 避免使用{{}}双大括号来初始化对象时getClass不是VO自身的问题
			innerModel.resultType = BeanUtil.getEntityClass(entity.getClass());
			// 类型检测
			if (innerModel.resultType.equals("".getClass().getClass())) {
				throw new IllegalArgumentException("查询参数是要求传递对象的实例,不是传递对象的class类别!你的参数=" + ((Class) entity).getName());
			}
		} else {
			logger.warn("请关注:查询语句sql={} 指定的查询条件参数entity=null,将以ArrayList作为默认类型返回!", sql);
		}
	}

	/**
	 * @TODO 动态增加参数过滤,对参数进行转null或其他的加工处理
	 * @param filters
	 * @return
	 */
	public QueryExecutor filters(ParamsFilter... filters) {
		if (filters != null && filters.length > 0) {
			for (ParamsFilter filter : filters) {
				if (StringUtil.isBlank(filter.getType()) || StringUtil.isBlank(filter.getParams())) {
					throw new IllegalArgumentException("针对QueryExecutor设置条件过滤必须要设置filterParams=[" + filter.getParams()
							+ "],和filterType=[" + filter.getType() + "]!");
				}
				if (CollectionUtil.any(filter.getType(), "eq", "neq", "gt", "gte", "lt", "lte", "between")) {
					if (StringUtil.isBlank(filter.getValue())) {
						throw new IllegalArgumentException(
								"针对QueryExecutor设置条件过滤eq、neq、gt、gte、lt、lte、between等类型必须要设置values值!");
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

	public QueryExecutor(String sql, Map<String, Object> paramsMap) {
		innerModel.sql = sql;
		innerModel.entity = new IgnoreKeyCaseMap(paramsMap);
	}

	public QueryExecutor(String sql, String[] paramsName, Object[] paramsValue) {
		innerModel.sql = sql;
		innerModel.paramsName = paramsName;
		innerModel.paramsValue = paramsValue;
	}

	/**
	 * @TODO 设置数据源
	 * @param dataSource
	 * @return
	 */
	public QueryExecutor dataSource(DataSource dataSource) {
		innerModel.dataSource = dataSource;
		return this;
	}

	public QueryExecutor names(String... paramsName) {
		innerModel.paramsName = paramsName;
		return this;
	}

	/**
	 * @TODO 设置查询参数的值,包含三种场景
	 *       <li>1、new QueryExecutor(sql).names("status").values(1)</li>
	 *       <li>2、new QueryExecutor(sql).values(1),sql中以?模式传参</li>
	 *       <li>3、new QueryExecutor(sql).values(map.put("status",1)),兼容map传参</li>
	 * @param paramsValue
	 * @return
	 */
	public QueryExecutor values(Object... paramsValue) {
		innerModel.paramsValue = paramsValue;
		return this;
	}

	/**
	 * @TODO 锁记录
	 * @param lockMode
	 * @return
	 */
	public QueryExecutor lock(LockMode lockMode) {
		innerModel.lockMode = lockMode;
		return this;
	}

	/**
	 * @TODO 是否将结果封装成父子对象级联模式
	 * @param hiberarchy
	 * @return
	 */
	public QueryExecutor hiberarchy(Boolean hiberarchy) {
		innerModel.hiberarchy = hiberarchy;
		return this;
	}

	/**
	 * 只针对父子对象存在共同属性场景
	 * 
	 * @TODO 设置将结果映射到不同类时查询结果的label跟属性名称的映射关系 此方法同时实现了:
	 *       <li>hiberarchy(Boolean hiberarchy)</li>
	 *       <li>hiberarchyClasses(Class... hiberarchyClasses)</li>
	 * @param resultType
	 * @param fieldsMap
	 * @return
	 */
	public QueryExecutor hiberarchyFieldsMap(Class resultType, Map fieldsMap) {
		if (resultType != null && fieldsMap != null) {
			// 默认开启层次化封装
			innerModel.hiberarchy = true;
			// 默认构造层次化封装涉及的类
			if (innerModel.hiberarchyClasses == null) {
				innerModel.hiberarchyClasses = new Class[] { resultType };
			} else {
				boolean hasExist = false;
				for (Class cls : innerModel.hiberarchyClasses) {
					if (cls.equals(resultType)) {
						hasExist = true;
						break;
					}
				}
				if (!hasExist) {
					int len = innerModel.hiberarchyClasses.length;
					Class[] hiberarchyClasses = new Class[len + 1];
					System.arraycopy(innerModel.hiberarchyClasses, 0, hiberarchyClasses, 0, len);
					hiberarchyClasses[len] = resultType;
					innerModel.hiberarchyClasses = hiberarchyClasses;
				}
			}
			innerModel.fieldsMap.put(resultType, new IgnoreKeyCaseMap((Map<String, String>) fieldsMap));
		}
		return this;
	}

	/**
	 * @TODO 指定需要层次化的级联类(一些表对象关系存在多个oneToMany,但一次查询结果只支持一个oneToMany对象关系)
	 * @param hiberarchyClasses
	 * @return
	 */
	public QueryExecutor hiberarchyClasses(Class... hiberarchyClasses) {
		if (hiberarchyClasses != null && hiberarchyClasses.length > 0) {
			innerModel.hiberarchy = true;
			innerModel.hiberarchyClasses = hiberarchyClasses;
		}
		return this;
	}

	/**
	 * @TODO 设置返回结果的类型
	 * @param resultType
	 * @return
	 */
	public QueryExecutor resultType(Type resultType) {
		innerModel.resultType = resultType;
		return this;
	}

	/**
	 * @TODO 设置分库策略
	 * @param strategy
	 * @param paramNames
	 * @return
	 */
	public QueryExecutor dbSharding(String strategy, String... paramNames) {
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
	 * @param tables
	 * @param paramNames 分表策略依赖的参数
	 * @return
	 */
	public QueryExecutor tableSharding(String strategy, String[] tables, String... paramNames) {
		ShardingStrategyConfig sharding = new ShardingStrategyConfig(1);
		sharding.setTables(tables);
		sharding.setStrategy(strategy);
		sharding.setFields(paramNames);
		sharding.setAliasNames(paramNames);
		innerModel.tableShardings.add(sharding);
		return this;
	}

	/**
	 * @TODO 设置jdbc参数，一般无需设置
	 * @param fetchSize
	 * @return
	 */
	public QueryExecutor fetchSize(int fetchSize) {
		innerModel.fetchSize = fetchSize;
		return this;
	}

	/**
	 * @TODO 设置最大提取记录数量(一般不用设置)
	 * @param maxRows
	 * @return
	 */
	@Deprecated
	public QueryExecutor maxRows(int maxRows) {
		innerModel.maxRows = maxRows;
		return this;
	}

	/**
	 * @TODO 针对resultType为Map.class 时，设定map的key是否转为骆驼命名法，默认true
	 * @param humpMapLabel
	 * @return
	 */
	public QueryExecutor humpMapLabel(Boolean humpMapLabel) {
		innerModel.humpMapLabel = humpMapLabel;
		return this;
	}

	/**
	 * @TODO 设置条件过滤空白转null为false，默认true
	 * @return
	 */
	public QueryExecutor blankNotNull() {
		innerModel.blankToNull = false;
		return this;
	}

	/**
	 * @TODO 对sql语句指定缓存翻译
	 * @param translates
	 * @return
	 */
	public QueryExecutor translates(Translate... translates) {
		if (translates != null && translates.length > 0) {
			TranslateExtend extend;
			for (Translate trans : translates) {
				extend = trans.getExtend();
				if (StringUtil.isBlank(extend.cache) || StringUtil.isBlank(extend.column)) {
					throw new IllegalArgumentException(
							"给查询增加的缓存翻译时未定义具体的cacheName=[" + extend.cache + "] 或 对应的column=[" + extend.column + "]!");
				}
				innerModel.translates.add(trans);
			}
		}
		return this;
	}

	@Deprecated
	public QueryExecutor rowCallbackHandler(RowCallbackHandler rowCallbackHandler) {
		innerModel.rowCallbackHandler = rowCallbackHandler;
		return this;
	}

	/**
	 * @TODO 结果日期格式化
	 * @param format
	 * @param columns
	 * @return
	 */
	public QueryExecutor dateFmt(String format, String... columns) {
		if (StringUtil.isNotBlank(format) && columns != null && columns.length > 0) {
			for (String column : columns) {
				FormatModel fmt = new FormatModel();
				fmt.setType(1);
				fmt.setColumn(column);
				fmt.setFormat(format);
				innerModel.colsFormat.put(column, fmt);
			}
		}
		return this;
	}

	/**
	 * @TODO 对结果的数字进行格式化
	 * @param format
	 * @param roundingMode
	 * @param columns
	 * @return
	 */
	public QueryExecutor numFmt(String format, RoundingMode roundingMode, String... columns) {
		if (StringUtil.isNotBlank(format) && columns != null && columns.length > 0) {
			for (String column : columns) {
				FormatModel fmt = new FormatModel();
				fmt.setType(2);
				fmt.setColumn(column);
				fmt.setFormat(format);
				fmt.setRoundingMode(roundingMode);
				innerModel.colsFormat.put(column, fmt);
			}
		}
		return this;
	}

	/**
	 * @TODO 对结果字段进行安全脱敏
	 * @param maskType
	 * @param columns
	 * @return
	 */
	public QueryExecutor secureMask(MaskType maskType, String... columns) {
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
	 * @TODO 分页优化
	 * @param pageOptimize
	 * @return
	 */
	public QueryExecutor pageOptimize(PageOptimize pageOptimize) {
		if (pageOptimize != null) {
			innerModel.pageOptimize = pageOptimize;
		}
		return this;
	}

	/**
	 * @see 5.1.9 启动 EntityQuery.create().values(map)模式传参模式
	 * @TODO 用map形式传参
	 * @param paramsMap
	 * @return
	 */
	@Deprecated
	public QueryExecutor paramsMap(Map<String, Object> paramsMap) {
		innerModel.entity = new IgnoreKeyCaseMap(paramsMap);
		return this;
	}

	/**
	 * @TODO 列转行
	 * @param unpivotModel
	 * @return
	 */
	public QueryExecutor unpivot(UnpivotModel unpivotModel) {
		if (unpivotModel != null) {
			innerModel.calculators.add(unpivotModel);
		}
		return this;
	}

	/**
	 * @TODO 行转列
	 * @param pivotModel
	 * @return
	 */
	public QueryExecutor pivot(PivotModel pivotModel) {
		if (pivotModel != null) {
			innerModel.calculators.add(pivotModel);
		}
		return this;
	}

	/**
	 * @TODO 设置分页查询countsql(用于极致性能优化，非必须)
	 * @param countSql
	 * @return
	 */
	public QueryExecutor countSql(String countSql) {
		if (countSql != null) {
			innerModel.countSql = countSql;
		}
		return this;
	}

	/**
	 * @TODO 提供代码层面设置分组拼接字段(排序由sql自身完成)
	 * @param groupConcat
	 * @return
	 */
	public QueryExecutor groupConcat(GroupConcat groupConcat) {
		if (groupConcat != null && StringUtil.isNotBlank(groupConcat.getConcat())
				&& StringUtil.isNotBlank(groupConcat.getGroup())) {
			LinkModel linkModel = new LinkModel();
			linkModel.setColumns(groupConcat.getConcat());
			linkModel.setGroupColumns(groupConcat.getGroup());
			linkModel.setDistinct(groupConcat.isDistinct());
			if (groupConcat.getSeparator() != null) {
				linkModel.setSign(groupConcat.getSeparator());
			}
			innerModel.linkModel = linkModel;
		}
		return this;
	}

	/**
	 * @TODO 提供代码层面对树形结果进行排序、汇总等
	 * @param treeSort
	 * @return
	 */
	public QueryExecutor treeSort(TreeSort treeSort) {
		if (treeSort != null && treeSort.getIdColumn() != null && treeSort.getPidColumn() != null) {
			TreeSortModel treeSortModel = new TreeSortModel();
			treeSortModel.setIdColumn(treeSort.getIdColumn()).setPidColumn(treeSort.getPidColumn())
					.setSumColumns(treeSort.getSumColumns()).setFilterColumn(treeSort.getFilterColumn())
					.setCompareType(treeSort.getCompareType()).setCompareValues(treeSort.getCompareValues())
					.setLevelOrderColumn(treeSort.getLevelOrderColumn()).setOrderWay(treeSort.getOrderWay());
			innerModel.calculators.add(treeSortModel);
		}
		return this;
	}

	/**
	 * @TODO 行与行之间的环比(推荐基于xml来配置)
	 * 
	 * @param rowsChainRatio
	 * @return
	 */
	public QueryExecutor rowsChainRatio(RowsChainRatio rowsChainRatio) {
		if (rowsChainRatio != null
				&& (rowsChainRatio.getRelativeColumns() != null && rowsChainRatio.getRelativeColumns().length > 0)) {
			// |月份 | 产品 |交易笔数 | 环比 | 金额 | 环比 | 收入 | 环比 |
			// | 5月 | 香蕉 | 2000 | 环比 | 金额 | 环比 | 收入 | 环比 |
			// | 5月 | 苹果 | 2000 | 环比 | 金额 | 环比 | 收入 | 环比 |
			// | 4月 | 香蕉 | 2000 | 环比 | 金额 | 环比 | 收入 | 环比 |0
			// | 4月 | 苹果 | 2000 | 环比 | 金额 | 环比 | 收入 | 环比 |1
			// | 3月 | 香蕉 | 2000 | 环比 | 金额 | 环比 | 收入 | 环比 |
			// | 3月 | 苹果 | 2000 | 环比 | 金额 | 环比 | 收入 | 环比 |
			// <rows-chain-relative group-column="月份" relative-columns="交易笔数,金额,收入"
			// relative-index="0,1"/>
			RowsChainRelativeModel rowsRelative = new RowsChainRelativeModel();
			// --
			rowsRelative.setDefaultValue(rowsChainRatio.getDefaultValue());
			rowsRelative.setRelativeColumns(rowsChainRatio.getRelativeColumns());
			rowsRelative.setGroupColumn(rowsChainRatio.getGroupColumn());
			rowsRelative.setRelativeIndexs(rowsChainRatio.getRelativeIndexs());
			// 0
			rowsRelative.setStartRow(rowsChainRatio.getStartRow());
			// 默认结束行，可以填-1表示倒数第二行
			rowsRelative.setEndRow(rowsChainRatio.getEndRow());
			// #.00% 或 #.00‰
			rowsRelative.setFormat(rowsChainRatio.getFormat());
			// 是否在比较列右边自动增加一列(否则写sql时自动构造好环比列)
			rowsRelative.setInsert(rowsChainRatio.getIsInsert());
			rowsRelative.setRadixSize(rowsChainRatio.getRadixSize());
			rowsRelative.setMultiply(rowsChainRatio.getMultiply());
			rowsRelative.setReduceOne(rowsChainRatio.isReduceOne());
			// 从末尾往开始倒序比较
			rowsRelative.setReverse(rowsChainRatio.isReverse());
			innerModel.calculators.add(rowsRelative);
		}
		return this;
	}

	/**
	 * @TODO 列与列之间的环比(推荐基于xml来配置)
	 * 
	 * @param colsChainRatio
	 * @return
	 */
	public QueryExecutor colsChainRatio(ColsChainRatio colsChainRatio) {
		if (colsChainRatio != null) {
			ColsChainRelativeModel colsRelative = new ColsChainRelativeModel();
			colsRelative.setDefaultValue(colsChainRatio.getDefaultValue());
			colsRelative.setGroupSize(colsChainRatio.getGroupSize());
			colsRelative.setRelativeIndexs(colsChainRatio.getRelativeIndexs());
			colsRelative.setStartColumn(colsChainRatio.getStartColumn());
			colsRelative.setEndColumn(colsChainRatio.getEndColumn());
			colsRelative.setFormat(colsChainRatio.getFormat());
			colsRelative.setInsert(colsChainRatio.getIsInsert());
			// 默认3位
			colsRelative.setRadixSize(colsChainRatio.getRadixSize());
			colsRelative.setMultiply(colsChainRatio.getMultiply());
			colsRelative.setReduceOne(colsChainRatio.isReduceOne());
			colsRelative.setSkipSize(colsChainRatio.getSkipSize());
			innerModel.calculators.add(colsRelative);
		}
		return this;
	}

	/**
	 * @TODO 定义sqltoy查询结果的处理模式,目前仅提供合计和求平均(推荐基于xml来配置)
	 * @param summary
	 * @return
	 */
	public QueryExecutor summary(Summary summary) {
		if (summary != null && summary.getSummaryGroups() != null && summary.getSummaryGroups().length > 0) {
			SummaryModel summaryModel = new SummaryModel();
			summaryModel.setReverse(summary.isReverse());
			summaryModel.setAveColumns(summary.getAveColumns());
			summaryModel.setSumColumns(summary.getSumColumns());
			summaryModel.setAveSkipNull(summary.isAveSkipNull());
			summaryModel.setRadixSize(summary.getRadixSize());
			summaryModel.setRoundingModes(summary.getRoundingModes());
			// 单条记录分组是否不做汇总和求平均
			summaryModel.setSkipSingleRow(summary.isSkipSingleRow());
			summaryModel.setLinkSign(summary.getLinkSign());
			summaryModel.setSumSite(summary.getSumSite());
			SummaryGroupMeta[] groupMetas = new SummaryGroupMeta[summary.getSummaryGroups().length];
			SummaryGroup summaryGroup;
			for (int i = 0; i < summary.getSummaryGroups().length; i++) {
				summaryGroup = summary.getSummaryGroups()[i];
				SummaryGroupMeta groupMeta = new SummaryGroupMeta();
				// 用逗号拼接起来
				if (summaryGroup.getGroupColumns() != null && summaryGroup.getGroupColumns().length > 0) {
					groupMeta.setGroupColumn(StringUtil.linkAry(",", true, summaryGroup.getGroupColumns()));
				} else if (i == 0) {
					groupMeta.setGlobalReverse(summary.isGlobalReverse());
				}
				groupMeta.setAverageTitle(summaryGroup.getAveTitle());
				groupMeta.setSumTitle(summaryGroup.getSumTitle());
				// 标题列
				groupMeta.setLabelColumn(summaryGroup.getLabelColumn());
				groupMeta.setOrderColumn(summaryGroup.getOrderColumn());
				groupMeta.setOrderWay(summaryGroup.getOrderWay());
				groupMeta.setOrderWithSum(summaryGroup.getOrderWithSum());
				groupMetas[i] = groupMeta;
			}
			summaryModel.setGroupMeta(groupMetas);
			innerModel.calculators.add(summaryModel);
		}
		return this;
	}

	/**
	 * @TODO 设置执行时是否输出sql日志
	 * @param showSql
	 * @return
	 */
	public QueryExecutor showSql(Boolean showSql) {
		innerModel.showSql = showSql;
		return this;
	}

	/**
	 * @TODO 是否是sql片段，即不是单独的一句查询(正常无需使用)
	 * @param sqlSegment
	 * @return
	 */
	public QueryExecutor sqlSegment(boolean sqlSegment) {
		innerModel.sqlSegment = sqlSegment;
		return this;
	}

	public QueryExecutorExtend getInnerModel() {
		return innerModel;
	}
}
