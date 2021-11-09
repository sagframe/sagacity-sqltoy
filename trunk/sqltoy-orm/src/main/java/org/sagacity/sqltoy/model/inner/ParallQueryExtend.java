package org.sagacity.sqltoy.model.inner;

import java.io.Serializable;

import javax.sql.DataSource;

import org.sagacity.sqltoy.model.PaginationModel;

/**
 * @project sagacity-sqltoy
 * @description 提供并行查询内部扩展参数类
 * @author zhongxuchen
 * @version v1.0, Date:2020-8-25
 * @modify 2020-8-25,修改说明
 */
public class ParallQueryExtend implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -988511746842317697L;

	/**
	 * 查询sql
	 */
	public String sql;

	/**
	 * 分页模型
	 */
	public PaginationModel pageModel;

	/**
	 * 返回结果类型
	 */
	public Class resultType;

	/**
	 * 数据源
	 */
	public DataSource dataSource;

	/**
	 * 自定义条件
	 */
	public boolean selfCondition = false;

	/**
	 * 参数名称
	 */
	public String[] names;

	/**
	 * 参数值
	 */
	public Object[] values;

}
