/**
 * 
 */
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
 * @version v1.0,Date:2017年10月9日
 */
public class Store extends BaseLink {

	/**
	 * 
	 */
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

	private boolean moreResult = false;

	/**
	 * 存储过程语句({?=call xxxStore(? in,? in,? out)})
	 */
	private String sql;

	/**
	 * @param sqlToyContext
	 * @param dataSource
	 */
	public Store(SqlToyContext sqlToyContext, DataSource dataSource) {
		super(sqlToyContext, dataSource);
	}

	/**
	 * @todo 设置数据源
	 * @param dataSource
	 * @return
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
		this.resultTypes = resultTypes;
		return this;
	}

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

	/**
	 * @return
	 */
	public StoreResult submit() {
		if (sql == null) {
			throw new IllegalArgumentException("call proceduce sql is null!");
		}
		SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(sql, SqlType.search, "");
		return dialectFactory.executeStore(sqlToyContext, sqlToyConfig, inParamsValue, outParamsType, resultTypes,
				moreResult, getDataSource(sqlToyConfig));
	}
}
