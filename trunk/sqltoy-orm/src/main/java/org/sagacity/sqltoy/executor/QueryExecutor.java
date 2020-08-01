/**
 * 
 */
package org.sagacity.sqltoy.executor;

import java.io.Serializable;
import java.lang.reflect.Type;
import java.math.RoundingMode;

import javax.sql.DataSource;

import org.sagacity.sqltoy.callback.ReflectPropertyHandler;
import org.sagacity.sqltoy.callback.RowCallbackHandler;
import org.sagacity.sqltoy.config.model.FormatModel;
import org.sagacity.sqltoy.config.model.SecureMask;
import org.sagacity.sqltoy.config.model.Translate;
import org.sagacity.sqltoy.model.MaskType;
import org.sagacity.sqltoy.model.ParamsFilter;
import org.sagacity.sqltoy.model.QueryExecutorExtend;
import org.sagacity.sqltoy.utils.CollectionUtil;
import org.sagacity.sqltoy.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sqltoy-orm
 * @description 构造统一的查询条件模型
 * @author renfei.chen <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:QueryExecutor.java,Revision:v1.0,Date:2012-9-3
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
	 * update 2018-4-10 针对开发者将entity传入Class类别产生的bug进行提示
	 * 
	 * @param sql
	 * @param entity
	 * @throws Exception
	 */
	public QueryExecutor(String sql, Serializable entity) {
		innerModel.sql = sql;
		innerModel.entity = entity;
		if (entity != null) {
			innerModel.resultType = entity.getClass();
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
					throw new IllegalArgumentException("针对QueryExecutor设置条件过滤必须要设置参数名称和过滤的类型!");
				}
				if (CollectionUtil.any(filter.getType(), "eq", "neq", "gt", "gte", "lt", "lte")) {
					if (StringUtil.isBlank(filter.getValue())) {
						throw new IllegalArgumentException("针对QueryExecutor设置条件过滤eq、neq、gt、lt等类型必须要设置values值!");
					}
				}
				innerModel.paramFilters.add(filter);
			}
		}
		return this;
	}

	public QueryExecutor(String sql, String[] paramsName, Object[] paramsValue) {
		innerModel.sql = sql;
		innerModel.paramsName = paramsName;
		innerModel.paramsValue = paramsValue;
		innerModel.shardingParamsValue = paramsValue;
	}

	public QueryExecutor dataSource(DataSource dataSource) {
		innerModel.dataSource = dataSource;
		return this;
	}

	public QueryExecutor names(String... paramsName) {
		innerModel.paramsName = paramsName;
		return this;
	}

	public QueryExecutor values(Object... paramsValue) {
		innerModel.paramsValue = paramsValue;
		innerModel.shardingParamsValue = paramsValue;
		return this;
	}

	public QueryExecutor resultType(Type resultType) {
		if (resultType == null) {
			logger.warn("请关注:查询语句sql={} 指定的resultType=null,将以ArrayList作为默认类型返回!", innerModel.sql);
		}
		innerModel.resultType = resultType;
		return this;
	}

	public QueryExecutor fetchSize(int fetchSize) {
		innerModel.fetchSize = fetchSize;
		return this;
	}

	public QueryExecutor maxRows(int maxRows) {
		innerModel.maxRows = maxRows;
		return this;
	}

	public QueryExecutor humpMapLabel(boolean humpMapLabel) {
		innerModel.humpMapLabel = humpMapLabel;
		return this;
	}

	/**
	 * @TODO 对sql语句指定缓存翻译
	 * @param translates
	 * @return
	 */
	public QueryExecutor translates(Translate... translates) {
		for (Translate trans : translates) {
			if (StringUtil.isBlank(trans.getCache()) || StringUtil.isBlank(trans.getColumn())) {
				throw new IllegalArgumentException("给查询增加的缓存翻译时未定义具体的cacheName 或 对应的column!");
			}
			innerModel.translates.put(trans.getColumn(), trans);
		}
		return this;
	}

	@Deprecated
	public QueryExecutor rowCallbackHandler(RowCallbackHandler rowCallbackHandler) {
		innerModel.rowCallbackHandler = rowCallbackHandler;
		return this;
	}

	// jdk8 stream之后意义已经不大
	@Deprecated
	public QueryExecutor reflectPropertyHandler(ReflectPropertyHandler reflectPropertyHandler) {
		innerModel.reflectPropertyHandler = reflectPropertyHandler;
		return this;
	}

	/**
	 * @TODO 结果日期格式化
	 * @param format
	 * @param params
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
	 * @param params
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
	 * @param params
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

	public QueryExecutorExtend getInnerModel() {
		return innerModel;
	}
}
