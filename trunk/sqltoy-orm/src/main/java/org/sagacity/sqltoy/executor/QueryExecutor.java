/**
 * 
 */
package org.sagacity.sqltoy.executor;

import java.io.Serializable;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.callback.ReflectPropertyHandler;
import org.sagacity.sqltoy.callback.RowCallbackHandler;
import org.sagacity.sqltoy.config.SqlConfigParseUtils;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.utils.ParamFilterUtils;
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
	 * 实体对象
	 */
	private Serializable entity;

	/**
	 * sql中参数名称
	 */
	private String[] paramsName;

	/**
	 * sql中参数名称对应的值
	 */
	private Object[] paramsValue;

	/**
	 * 原生数值
	 */
	private Object[] shardingParamsValue;

	/**
	 * sql语句或sqlId
	 */
	private String sql;

	/**
	 * jdbc 查询时默认加载到内存中的记录数量 -1表示不设置，采用数据库默认的值
	 */
	private int fetchSize = -1;

	/**
	 * jdbc查询最大返回记录数量
	 */
	private int maxRows = -1;

	/**
	 * 结果集反调处理(已经极少极少使用,可以废弃)
	 */
	@Deprecated
	private RowCallbackHandler rowCallbackHandler;

	/**
	 * 查询属性值反射处理
	 */
	private ReflectPropertyHandler reflectPropertyHandler;

	/**
	 * 查询结果类型
	 */
	private Type resultType;

	/**
	 * 结果为map时标题是否变成驼峰模式
	 */
	private boolean humpMapLabel = true;

	/**
	 * 特定数据库连接资源
	 */
	private DataSource dataSource;

	/**
	 * 是否已经提取过value值
	 */
	private boolean extracted = false;

	public QueryExecutor(String sql) {
		this.sql = sql;
	}

	/**
	 * update 2018-4-10 针对开发者将entity传入Class类别产生的bug进行类型检测,
	 * 
	 * @param sql
	 * @param entity
	 * @throws Exception
	 */
	public QueryExecutor(String sql, Serializable entity) {
		this.sql = sql;
		this.entity = entity;
		if (entity != null) {
			this.resultType = entity.getClass();
			// 类型检测
			if (this.resultType.equals("".getClass().getClass())) {
				throw new IllegalArgumentException("查询参数是要求传递对象的实例,不是传递对象的class类别!你的参数=" + ((Class) entity).getName());
			}
		} else {
			logger.warn("请关注:查询语句sql={} 指定的查询条件参数entity=null,将以ArrayList作为默认类型返回!", sql);
		}
	}

	public QueryExecutor(String sql, String[] paramsName, Object[] paramsValue) {
		this.sql = sql;
		this.paramsName = paramsName;
		this.paramsValue = paramsValue;
		this.shardingParamsValue = paramsValue;
	}

	public QueryExecutor dataSource(DataSource dataSource) {
		this.dataSource = dataSource;
		return this;
	}

	public QueryExecutor names(String... paramsName) {
		this.paramsName = paramsName;
		return this;
	}

	public QueryExecutor values(Object... paramsValue) {
		this.paramsValue = paramsValue;
		this.shardingParamsValue = paramsValue;
		return this;
	}

	public QueryExecutor resultType(Type resultType) {
		if (resultType == null) {
			logger.warn("请关注:查询语句sql={} 指定的resultType=null,将以ArrayList作为默认类型返回!", sql);
		}
		this.resultType = resultType;
		return this;
	}

	public QueryExecutor fetchSize(int fetchSize) {
		this.fetchSize = fetchSize;
		return this;
	}

	public QueryExecutor maxRows(int maxRows) {
		this.maxRows = maxRows;
		return this;
	}

	public QueryExecutor humpMapLabel(boolean humpMapLabel) {
		this.humpMapLabel = humpMapLabel;
		return this;
	}

	@Deprecated
	public QueryExecutor rowCallbackHandler(RowCallbackHandler rowCallbackHandler) {
		this.rowCallbackHandler = rowCallbackHandler;
		return this;
	}

	public QueryExecutor reflectPropertyHandler(ReflectPropertyHandler reflectPropertyHandler) {
		this.reflectPropertyHandler = reflectPropertyHandler;
		return this;
	}

	/**
	 * @return the rowCallbackHandler
	 */
	public RowCallbackHandler getRowCallbackHandler() {
		return rowCallbackHandler;
	}

	/**
	 * @return the entity
	 */
	public Serializable getEntity() {
		return entity;
	}

	/**
	 * @return the paramsName
	 */
	public String[] getParamsName() {
		return paramsName;
	}

	/**
	 * @param sqlToyConfig
	 * @return
	 */
	public String[] getParamsName(SqlToyConfig sqlToyConfig) {
		if (this.entity == null) {
			if (paramsName == null || paramsName.length == 0) {
				return sqlToyConfig.getParamsName();
			}
			return paramsName;
		}
		return sqlToyConfig.getFullParamNames();
	}

	/**
	 * @param sqlToyConfig
	 * @return
	 */
	public String[] getTableShardingParamsName(SqlToyConfig sqlToyConfig) {
		if (this.entity == null) {
			if (paramsName == null || paramsName.length == 0) {
				return sqlToyConfig.getParamsName();
			}
			return paramsName;
		}
		return sqlToyConfig.getTableShardingParams();
	}

	/**
	 * @param sqlToyConfig
	 * @return
	 */
	public String[] getDataSourceShardingParamsName(SqlToyConfig sqlToyConfig) {
		if (this.entity == null) {
			if (paramsName == null || paramsName.length == 0) {
				return sqlToyConfig.getParamsName();
			}
			return paramsName;
		}
		return sqlToyConfig.getDataSourceShardingParams();
	}

	/**
	 * @return the paramsValue
	 */
	public Object[] getParamsValue() {
		return paramsValue;
	}

	/**
	 * @return the fetchSize
	 */
	public int getFetchSize() {
		return fetchSize;
	}

	/**
	 * @todo 获取sql中参数对应的值
	 * @param sqlToyContext
	 * @param sqlToyConfig
	 * @return
	 * @throws Exception
	 */
	public Object[] getParamsValue(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig) throws Exception {
		Object[] realValues = null;
		// 是否萃取过
		if (!extracted) {
			if (this.entity != null) {
				paramsValue = SqlConfigParseUtils.reflectBeanParams(sqlToyConfig.getFullParamNames(), this.entity,
						reflectPropertyHandler);
			}
			extracted = true;
		}
		if (paramsValue != null) {
			realValues = paramsValue.clone();
		}
		// 过滤加工参数值
		if (realValues != null) {
			realValues = ParamFilterUtils.filterValue(sqlToyContext, getParamsName(sqlToyConfig), realValues,
					sqlToyConfig.getFilters());
		} else {
			// update 2017-4-11,默认参数值跟参数数组长度保持一致,并置为null
			String[] names = getParamsName(sqlToyConfig);
			if (names != null && names.length > 0) {
				realValues = new Object[names.length];
			}
		}
		return realValues;
	}

	/**
	 * @todo 获取分表时传递给分表策略的参数值
	 * @param sqlToyConfig
	 * @return
	 * @throws Exception
	 */
	public Object[] getTableShardingParamsValue(SqlToyConfig sqlToyConfig) throws Exception {
		if (this.entity != null) {
			return SqlConfigParseUtils.reflectBeanParams(sqlToyConfig.getTableShardingParams(), this.entity,
					reflectPropertyHandler);
		}
		return shardingParamsValue;
	}

	/**
	 * @todo 获取分库时传递给分库策略的参数值(策略会根据值通过逻辑返回具体的库)
	 * @param sqlToyConfig
	 * @return
	 * @throws Exception
	 */
	public Object[] getDataSourceShardingParamsValue(SqlToyConfig sqlToyConfig) throws Exception {
		if (this.entity != null) {
			return SqlConfigParseUtils.reflectBeanParams(sqlToyConfig.getDataSourceShardingParams(), this.entity,
					reflectPropertyHandler);
		}
		return shardingParamsValue;
	}

	/**
	 * @return the dataSource
	 */
	public DataSource getDataSource() {
		return dataSource;
	}

	/**
	 * @return the sqlOrNamed
	 */
	public String getSql() {
		return sql;
	}

	/**
	 * @return the resultType
	 */
	public Type getResultType() {
		return resultType;
	}

	/**
	 * @return the humpMapLabel
	 */
	public boolean isHumpMapLabel() {
		return humpMapLabel;
	}

	/**
	 * @return the reflectPropertyHandler
	 */
	public ReflectPropertyHandler getReflectPropertyHandler() {
		return reflectPropertyHandler;
	}

	/**
	 * @return the maxRows
	 */
	public int getMaxRows() {
		return maxRows;
	}

	/**
	 * @TODO 用于cache-arg模式下传参数容易漏掉alias-name 的场景
	 * @param sqlToyConfig
	 */
	public void optimizeArgs(SqlToyConfig sqlToyConfig) {
		if (sqlToyConfig.getCacheArgNames().isEmpty() || this.entity != null) {
			return;
		}
		// 只有这种场景下需要校正参数
		if (this.paramsName != null && this.paramsValue != null) {
			List<String> tmp = new ArrayList<String>();
			boolean exist = false;
			for (String comp : sqlToyConfig.getCacheArgNames()) {
				exist = false;
				for (String param : this.paramsName) {
					if (param.toLowerCase().equals(comp)) {
						exist = true;
						break;
					}
				}
				if (!exist) {
					tmp.add(comp);
				}
			}
			if (tmp.isEmpty()) {
				return;
			}
			// 补全额外的参数名称,对应的值则为null
			String[] realParams = new String[this.paramsName.length + tmp.size()];
			Object[] realValues = new Object[this.paramsValue.length + tmp.size()];
			System.arraycopy(this.paramsName, 0, realParams, 0, this.paramsName.length);
			int index = this.paramsName.length;
			for (String extParam : tmp) {
				realParams[index] = extParam;
				index++;
			}
			System.arraycopy(this.paramsValue, 0, realValues, 0, this.paramsValue.length);
			this.paramsName = realParams;
			this.paramsValue = realValues;
			this.shardingParamsValue = realValues;
		}
	}
}
