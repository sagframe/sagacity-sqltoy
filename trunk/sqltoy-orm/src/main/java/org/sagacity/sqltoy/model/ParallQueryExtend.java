package org.sagacity.sqltoy.model;

import java.io.Serializable;

import javax.sql.DataSource;

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
}
