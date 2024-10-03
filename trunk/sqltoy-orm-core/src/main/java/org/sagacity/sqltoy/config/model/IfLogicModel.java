package org.sagacity.sqltoy.config.model;

import java.io.Serializable;

/**
 * 
 */
public class IfLogicModel implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = -489871910912605304L;

	// 1:if;2:elseif;3:else
	private int type;

	/**
	 * 逻辑表达式
	 */
	private String logicExpression;

	private boolean logicResult = false;

	/**
	 * 逻辑内的sql
	 */
	private String sqlPart;

	// 当前逻辑内参数数量
	private int paramsCnt;

	// 前面总参数数量
	private int preParamsCnt;

	private int logicParamsCnt;

	public int getType() {
		return type;
	}

	public void setType(int type) {
		this.type = type;
	}

	public String getLogicExpression() {
		return logicExpression;
	}

	public void setLogicExpression(String logicExpression) {
		this.logicExpression = logicExpression;
	}

	public String getSqlPart() {
		return sqlPart;
	}

	public void setSqlPart(String sqlPart) {
		this.sqlPart = sqlPart;
	}

	public int getParamsCnt() {
		return paramsCnt;
	}

	public void setParamsCnt(int paramsCnt) {
		this.paramsCnt = paramsCnt;
	}

	public int getPreParamsCnt() {
		return preParamsCnt;
	}

	public void setPreParamsCnt(int preParamsCnt) {
		this.preParamsCnt = preParamsCnt;
	}

	public int getLogicParamsCnt() {
		return logicParamsCnt;
	}

	public void setLogicParamsCnt(int logicParamsCnt) {
		this.logicParamsCnt = logicParamsCnt;
	}

	public boolean isLogicResult() {
		return logicResult;
	}

	public void setLogicResult(boolean logicResult) {
		this.logicResult = logicResult;
	}

}
