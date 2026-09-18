package org.sagacity.sqltoy.link;

import javax.sql.DataSource;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlType;
import org.sagacity.sqltoy.model.StoreResult;

/**
 * @project sagacity-sqltoy
 * @description 存储过程操作
 * @author zhongxuchen
 * @version v1.0,Date:2017-10-09
 */
public class Store extends BaseLink {

	private static final long serialVersionUID = 8055671388714803899L;

	/**
	 * 输入参数值
	 */
	private Object[] inParamsValue;

	/**
	 * 输出值类型
	 */
	private Integer[] outParamsType;

	/**
	 * 返回结果类型
	 */
	private Class[] resultTypes;

	/**
	 * 是否返回多个结果集合
	 */
	private Boolean moreResult;

	/**
	 * 存储过程语句({?=call xxxStore(? in,? in,? out)})
	 */
	private String sql;

	/**
	 * 查询超时时长(秒)
	 */
	private Integer timeout;

	/**
	 * @param sqlToyContext sqltoy全局上下文对象
	 * @param dataSource    存储过程调用绑定的数据源，null表示使用默认数据源
	 */
	public Store(SqlToyContext sqlToyContext, DataSource dataSource) {
		super(sqlToyContext, dataSource);
	}

	/**
	 * 设置数据源
	 * 
	 * @param dataSource 当前存储过程调用绑定的数据源
	 * @return 当前Store对象，支持链式调用
	 */
	public Store dataSource(DataSource dataSource) {
		this.dataSource = dataSource;
		this.defaultDataSource = false;
		return this;
	}

	@Deprecated
	public Store resultType(Class resultType) {
		this.resultTypes = new Class[] { resultType };
		return this;
	}

	public Store resultTypes(Class... resultTypes) {
		// 多个结果类型且moreResult没有被设置过值，则默认表示存储过程返回多个结果集
		if (this.moreResult == null && resultTypes != null && resultTypes.length > 1) {
			this.moreResult = true;
		}
		this.resultTypes = resultTypes;
		return this;
	}

	/**
	 * 设置返回多个结果集合
	 * 
	 * @param moreResult true表示存储过程返回多个结果集，false表示单个结果集
	 * @return 当前Store对象，支持链式调用
	 */
	public Store moreResult(boolean moreResult) {
		this.moreResult = moreResult;
		return this;
	}

	public Store inParams(Object... inParamsValue) {
		this.inParamsValue = inParamsValue;
		return this;
	}

	public Store outTypes(Integer... outParamsType) {
		this.outParamsType = outParamsType;
		return this;
	}

	public Store sql(String sql) {
		this.sql = sql;
		return this;
	}

	public Store timeout(Integer timeout) {
		this.timeout = timeout;
		return this;
	}

	/**
	 * @return 存储过程执行结果对象，包含出参值、返回值以及查询结果集
	 */
	public StoreResult submit() {
		if (sql == null) {
			throw new IllegalArgumentException("call proceduce sql is null!");
		}
		SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(sql, SqlType.search, "", null);
		return dialectFactory.executeStore(sqlToyContext, sqlToyConfig, inParamsValue, outParamsType, resultTypes,
				(moreResult == null) ? false : moreResult.booleanValue(), timeout, getDataSource(sqlToyConfig));
	}
}
