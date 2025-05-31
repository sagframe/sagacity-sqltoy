package org.sagacity.sqltoy.model;

import java.io.Serializable;
import java.util.Map;

import javax.sql.DataSource;

import org.sagacity.sqltoy.model.inner.ParallelQueryExtend;

/**
 * @project sagacity-sqltoy
 * @description 并行查询对象模型
 * @author zhongxuchen
 * @version v1.0, Date:2020-8-25
 * @modify 2020-8-25,修改说明
 */
public class ParallQuery implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1316664483969945064L;

	/**
	 * 参数内部类化，减少get方法
	 */
	private ParallelQueryExtend extend = new ParallelQueryExtend();

	public ParallelQueryExtend getExtend() {
		return extend;
	}

	public static ParallQuery create() {
		return new ParallQuery();
	}

	/**
	 * @TODO 设置具体的sql或id
	 * @param sql
	 * @return
	 */
	public ParallQuery sql(String sql) {
		extend.sql = sql;
		return this;
	}

	/**
	 * @TODO 分页场景
	 * @param page
	 * @return
	 */
	public ParallQuery page(Page page) {
		extend.page = page;
		return this;
	}

	/**
	 * @TODO 取top记录
	 * @param topSize
	 * @return
	 */
	public ParallQuery topSize(double topSize) {
		extend.topSize = topSize;
		return this;
	}

	/**
	 * @TODO 取随机记录
	 * @param randomSize
	 * @return
	 */
	public ParallQuery randomSize(double randomSize) {
		extend.randomSize = randomSize;
		return this;
	}

	/**
	 * @TODO 设置独立的条件参数
	 * @param names
	 * @return
	 */
	public ParallQuery names(String... names) {
		extend.names = names;
		extend.selfCondition = true;
		return this;
	}

	public ParallQuery values(Object... values) {
		extend.values = values;
		extend.selfCondition = true;
		return this;
	}

	// map传参
	public ParallQuery paramsMap(Map<String, Object> paramsMap) {
		extend.values = new Object[] { paramsMap };
		extend.selfCondition = true;
		return this;
	}

	/**
	 * @TODO 返回类型
	 * @param resultType
	 * @return
	 */
	public ParallQuery resultType(Class resultType) {
		extend.resultType = resultType;
		return this;
	}

	public ParallQuery dataSource(DataSource dataSource) {
		extend.dataSource = dataSource;
		return this;
	}

	public ParallQuery showSql(Boolean showSql) {
		extend.showSql = showSql;
		return this;
	}
}
