package org.sagacity.sqltoy.model;

import java.io.Serializable;

public class ParallQueryExtend implements Serializable{

	/**
	 * 
	 */
	private static final long serialVersionUID = -988511746842317697L;

	public String sql;

	public PaginationModel pageModel;

	public Class resultType;
}
