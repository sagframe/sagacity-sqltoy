package org.sagacity.sqltoy.model;

import java.io.Serializable;

/**
 * 
 * @author zhong
 *
 */
public class ParallQuery implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1316664483969945064L;
	
	private ParallQueryExtend extend=new ParallQueryExtend();

    public ParallQueryExtend getExtend() {
		return extend;
	}

	public static ParallQuery create() {
		return new ParallQuery();
	}

	public ParallQuery sql(String sql) {
		extend.sql = sql;
		return this;
	}
	
	public ParallQuery pageModel(PaginationModel pageModel) {
		extend.pageModel = pageModel;
		return this;
	}
	
	public ParallQuery resultType(Class resultType) {
		extend.resultType = resultType;
		return this;
	}
}
