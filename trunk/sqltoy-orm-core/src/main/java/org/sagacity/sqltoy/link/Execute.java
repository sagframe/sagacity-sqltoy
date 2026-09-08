package org.sagacity.sqltoy.link;

import java.io.Serializable;
import java.util.Map;

import javax.sql.DataSource;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlType;
import org.sagacity.sqltoy.model.IgnoreKeyCaseMap;
import org.sagacity.sqltoy.model.QueryExecutor;
import org.sagacity.sqltoy.utils.BeanUtil;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * @project sagacity-sqltoy
 * @description 执行sql
 * @author zhongxuchen
 * @version v1.0,Date:2017-10-24
 */
public class Execute extends BaseLink {
	private static final long serialVersionUID = 6336692505147861983L;

	/**
	 * 具体执行的sql
	 */
	private String sql;

	/**
	 * 作为参数传递的参数对象(属性跟sql中的参数名称对应)
	 */
	private Serializable params;

	/**
	 * 是否自动提交
	 */
	private Boolean autoCommit = false;

	/**
	 * 参数名称
	 */
	private String[] paramsNamed;

	/**
	 * 参数值
	 */
	private Object[] paramsValue;

	/**
	 * @param sqlToyContext sqltoy全局上下文对象
	 * @param dataSource    sql执行绑定的数据源，null表示使用默认数据源
	 */
	public Execute(SqlToyContext sqlToyContext, DataSource dataSource) {
		super(sqlToyContext, dataSource);
	}

	public Execute dataSource(DataSource dataSource) {
		this.dataSource = dataSource;
		this.defaultDataSource = false;
		return this;
	}

	public Execute entity(Serializable entityVO) {
		this.params = entityVO;
		return this;
	}

	public Execute names(String... paramsNamed) {
		this.paramsNamed = paramsNamed;
		return this;
	}

	public Execute values(Object... paramsValue) {
		if (paramsValue != null && paramsValue.length == 1 && paramsValue[0] != null && paramsValue[0] instanceof Map) {
			if (paramsValue[0] instanceof IgnoreKeyCaseMap) {
				this.params = (IgnoreKeyCaseMap) paramsValue[0];
			} else {
				this.params = new IgnoreKeyCaseMap((Map) paramsValue[0]);
			}
		} else {
			this.paramsValue = paramsValue;
		}
		return this;
	}

	public Execute autoCommit(Boolean autoCommit) {
		this.autoCommit = autoCommit;
		return this;
	}

	public Execute sql(String sql) {
		this.sql = sql;
		return this;
	}

	/**
	 * 执行并返回修改的记录数量
	 * 
	 * @return 实际被修改(插入、更新、删除)的记录数量
	 */
	public Long submit() {
		if (StringUtil.isBlank(sql)) {
			throw new IllegalArgumentException("execute operate sql is null!");
		}
		QueryExecutor queryExecute = build();
		SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(queryExecute, SqlType.update, super.getDialect());
		return dialectFactory.executeSql(sqlToyContext, sqlToyConfig, queryExecute, null, autoCommit,
				getDataSource(sqlToyConfig));
	}

	/**
	 * 执行insert语句并返回主键字段值
	 * 
	 * @param primaryField 需要获取值的主键数据库字段名称
	 * @return 插入记录后产生的主键字段值，参数为非基础类型对象时会同时回填到其对应属性上
	 */
	public Object insertReturnPrimaryKey(String primaryField) {
		if (StringUtil.isBlank(sql)) {
			throw new IllegalArgumentException("execute operate sql is null!");
		}
		QueryExecutor queryExecute = build();
		SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(queryExecute, SqlType.insert, super.getDialect());
		Object returnPkValue = dialectFactory.insertReturnPrimaryKey(sqlToyContext, sqlToyConfig, queryExecute, null,
				primaryField, autoCommit, getDataSource(sqlToyConfig));
		if (returnPkValue != null && params != null && !(params instanceof Map)
				&& !BeanUtil.isBaseDataType(params.getClass())) {
			BeanUtil.setProperty(params, StringUtil.toHumpStr(primaryField, false), returnPkValue);
		}
		return returnPkValue;
	}

	private QueryExecutor build() {
		QueryExecutor queryExecutor = null;
		if (params != null) {
			queryExecutor = new QueryExecutor(sql, params);
		} else {
			queryExecutor = new QueryExecutor(sql).names(paramsNamed).values(paramsValue);
		}
		return queryExecutor;
	}
}
