package org.sagacity.sqltoy.model;

import java.io.Serializable;
import java.util.Map;

import javax.sql.DataSource;

import org.sagacity.sqltoy.model.inner.ParallelQueryExtend;

/**
 * @project sagacity-sqltoy
 * @description 并行查询对象模型
 * @author zhongxuchen
 * @version v1.0,Date:2020-08-25
 * @modify Date:2020-08-25 修改说明
 */
public class ParallelQuery implements Serializable {

	private static final long serialVersionUID = 1316664483969945064L;

	/**
	 * 参数内部类化，减少get方法
	 */
	private ParallelQueryExtend extend = new ParallelQueryExtend();

	public ParallelQueryExtend getExtend() {
		return extend;
	}

	public static ParallelQuery create() {
		return new ParallelQuery();
	}

	/**
	 * 设置具体的sql或id
	 * 
	 * @param sql
	 * @return
	 */
	public ParallelQuery sql(String sql) {
		extend.sql = sql;
		return this;
	}

	/**
	 * 分页场景
	 * 
	 * @param page
	 * @return
	 */
	public ParallelQuery page(Page page) {
		extend.page = page;
		return this;
	}

	/**
	 * 取top记录
	 * 
	 * @param topSize
	 * @return
	 */
	public ParallelQuery topSize(double topSize) {
		extend.topSize = topSize;
		return this;
	}

	/**
	 * 取随机记录
	 * 
	 * @param randomSize
	 * @return
	 */
	public ParallelQuery randomSize(double randomSize) {
		extend.randomSize = randomSize;
		return this;
	}

	/**
	 * 设置独立的条件参数
	 * 
	 * @param names
	 * @return
	 */
	public ParallelQuery names(String... names) {
		extend.names = names;
		extend.selfCondition = true;
		return this;
	}

	public ParallelQuery values(Object... values) {
		extend.values = values;
		extend.selfCondition = true;
		return this;
	}

	// map传参
	public ParallelQuery paramsMap(Map<String, Object> paramsMap) {
		extend.values = new Object[] { paramsMap };
		extend.selfCondition = true;
		return this;
	}

	/**
	 * 返回类型
	 * 
	 * @param resultType
	 * @return
	 */
	public ParallelQuery resultType(Class resultType) {
		extend.resultType = resultType;
		return this;
	}

	public ParallelQuery dataSource(DataSource dataSource) {
		extend.dataSource = dataSource;
		return this;
	}

	public ParallelQuery showSql(Boolean showSql) {
		extend.showSql = showSql;
		return this;
	}

	/**
	 * 设置执行时上下文数据，如：在拦截器中取值用以业务判断
	 * 
	 * @param contextData
	 * @return
	 */
	public ParallelQuery contextData(Object contextData) {
		extend.contextData = contextData;
		return this;
	}
}
