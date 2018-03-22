/**
 * 
 */
package org.sagacity.sqltoy.criteria;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.beanutils.PropertyUtils;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.model.PaginationModel;

/**
 * @project sqltoy-orm
 * @description 基于对象的简单查询标准
 * @author renfei.chen <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:Criteria.java,Revision:v1.0,Date:2012-8-31
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public class Criteria {
	private final String WHERE = " where ";

	private final String AND = " and ";
	private final String OR = " or ";

	/**
	 * 对象实体
	 */
	private Serializable entity;

	/**
	 * 参数值
	 */
	private List paramValues = new ArrayList();

	/**
	 * sql片段
	 */
	private List<String> sqlFragments = new ArrayList<String>();

	public Criteria(Serializable entity) {
		this.entity = entity;
	}

	/**
	 * 分页查询
	 * 
	 * @param pageModel
	 * @return
	 */
	public PaginationModel page(PaginationModel pageModel) {
		return null;
	}

	/**
	 * 取top记录
	 * 
	 * @param topSize
	 * @return
	 */
	public List top(Float topSize) {
		return null;
	}

	public List list() {
		return null;
	}

	public Criteria add(Restrictions restrictions) {
		return this;
	}

	public Criteria groupStart() {
		sqlFragments.add("(");
		return this;
	}

	public Criteria groupEnd() {
		sqlFragments.add(")");
		return this;
	}

	public Criteria and() {
		// if(!sqlFragments.isEmpty() && !sqlFragments.get(0).equals(WHERE))
		// sqlFragments.add(0, WHERE);
		sqlFragments.add(AND);
		return this;
	}

	public Criteria or() {
		// if(!sqlFragments.isEmpty() && !sqlFragments.get(0).equals(WHERE))
		// sqlFragments.add(0, WHERE);
		sqlFragments.add(OR);
		return this;
	}

	public Criteria compare(String property, Compare compare) throws Exception {
		Object paramValue = PropertyUtils.getProperty(entity, property);
		if (null == paramValue && !compare.equals(Compare.is))
			sqlFragments.add(" 1=1 ");
		else {
			sqlFragments.add("@".concat(property));
			sqlFragments.add(compare.getValue());
			sqlFragments.add(" ? ");
			if (compare.equals(Compare.like) && paramValue.toString().indexOf("%") == -1)
				paramValues.add("%" + paramValue + "%");
			else
				paramValues.add(paramValue);
		}
		return this;
	}

	public Criteria compare(String property, Compare compare, Object value) throws Exception {
		if (null == value && !compare.equals(Compare.is))
			sqlFragments.add(" 1=1 ");
		else {
			sqlFragments.add("@".concat(property));
			sqlFragments.add(compare.getValue());
			sqlFragments.add(" ? ");
			if (compare.equals(Compare.like) && value.toString().indexOf("%") == -1)
				paramValues.add("%" + value + "%");
			else
				paramValues.add(value);
		}
		return this;
	}

	public Criteria in(String property, Object inValues) {
		if (null == inValues)
			sqlFragments.add(" 1=1 ");
		else {
			sqlFragments.add("@".concat(property));
			sqlFragments.add("in (?) ");
			paramValues.add(inValues);
		}
		return this;
	}

	public Criteria between(String property, Object startValue, Object endValue) {
		if (null == startValue || null == endValue)
			sqlFragments.add(" 1=1 ");
		else {
			sqlFragments.add("@".concat(property));
			sqlFragments.add(" between ? and ? ");
			paramValues.add(startValue);
			paramValues.add(endValue);
		}
		return this;
	}

	/**
	 * @return the entity
	 */
	public Serializable getEntity() {
		return entity;
	}

	/**
	 * @return the paramValues
	 */
	public List getParamValues() {
		return paramValues;
	}

	/**
	 * @return the sqlParamList
	 */
	public List<String> getSqlFragments() {
		return sqlFragments;
	}

	public String wrapSqlFragments(EntityMeta entityMeta) {
		StringBuilder queryStr = new StringBuilder("");
		String property;
		if (sqlFragments.size() > 0)
			queryStr.append(WHERE);
		for (int i = 0; i < sqlFragments.size(); i++) {
			property = sqlFragments.get(i);
			// 用数据库表字段名称替换用@property 标注的类属性名称
			if (property.startsWith("@"))
				queryStr.append(entityMeta.getColumnName(property.substring(1)));
			else
				queryStr.append(property);
		}
		return queryStr.toString();
	}

	// private void processLink(StringBuilder queryStr, String appendStr) {
	// String sql = queryStr.toString();
	//
	// // if(sql.)
	// }
}
