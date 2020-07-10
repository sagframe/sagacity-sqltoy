package org.sagacity.sqltoy.model;

import java.io.Serializable;
import java.util.HashMap;
import java.util.LinkedHashMap;

import javax.sql.DataSource;

import org.sagacity.sqltoy.config.model.Translate;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * @description 提供给代码中进行查询使用，一般适用于接口服务内部逻辑处理以单表为主体(不用于页面展示)
 * @author renfei.chen <a href="mailto:zhongxuchen@hotmail.com">联系作者</a>
 * @version id:EntityQuery.java,Revision:v1.0,Date:2020-5-15
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
	 * 条件语句
	 */
	private String where;

	/**
	 * 参数名称
	 */
	private String[] names;

	/**
	 * 参数值
	 */
	private Object[] values;

	private DataSource dataSource;

	/**
	 * 锁类型
	 */
	private LockMode lockMode;

	/**
	 * 动态增加缓存翻译配置
	 */
	private HashMap<String, Translate> extendsTranslates = new HashMap<String, Translate>();

	private LinkedHashMap<String, String> orderBy = new LinkedHashMap<String, String>();

	public EntityQuery where(String where) {
		this.where = where;
		return this;
	}

	public EntityQuery names(String... names) {
		this.names = names;
		return this;
	}

	public EntityQuery values(Object... values) {
		this.values = values;
		return this;
	}

	public EntityQuery orderBy(String field) {
		// 默认为升序
		orderBy.put(field, " ");
		return this;
	}

	public EntityQuery orderByDesc(String field) {
		orderBy.put(field, " desc ");
		return this;
	}

	public EntityQuery lock(LockMode lockMode) {
		this.lockMode = lockMode;
		return this;
	}

	/**
	 * @TODO 对sql语句指定缓存翻译
	 * @param translates
	 * @return
	 */
	public EntityQuery translates(Translate... translates) {
		for (Translate trans : translates) {
			if (StringUtil.isBlank(trans.getCache()) || StringUtil.isBlank(trans.getColumn())) {
				throw new IllegalArgumentException("给查询增加的缓存翻译未定义具体的cacheName 或 column!");
			}
			extendsTranslates.put(trans.getColumn(), trans);
		}
		return this;
	}

	public EntityQuery dataSource(DataSource dataSource) {
		this.dataSource = dataSource;
		return this;
	}

	/**
	 * @return the where
	 */
	public String getWhere() {
		return where;
	}

	/**
	 * @return the names
	 */
	public String[] getNames() {
		return names;
	}

	/**
	 * @return the values
	 */
	public Object[] getValues() {
		return values;
	}

	/**
	 * @return the dataSource
	 */
	public DataSource getDataSource() {
		return dataSource;
	}

	public LockMode getLockMode() {
		return lockMode;
	}

	public LinkedHashMap<String, String> getOrderBy() {
		return orderBy;
	}

	/**
	 * @TODO 获取自定义的缓存翻译配置
	 * @return
	 */
	public HashMap<String, Translate> getTranslates() {
		return this.extendsTranslates;
	}
}
