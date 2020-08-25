package org.sagacity.sqltoy.model;

import java.io.Serializable;

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
	private ParallQueryExtend extend = new ParallQueryExtend();

	public ParallQueryExtend getExtend() {
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
	 * @param pageModel
	 * @return
	 */
	public ParallQuery pageModel(PaginationModel pageModel) {
		extend.pageModel = pageModel;
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
}
