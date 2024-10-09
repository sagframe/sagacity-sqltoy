package org.sagacity.sqltoy.config.model;

import java.io.Serializable;

/**
 * @project sagacity-sqltoy
 * @description 构造存放@if @elseif @else sql中逻辑处理的数据模型
 * @author zhongxuchen
 * @version v1.0, Date:2024年10月1日
 * @modify 2024年10月1日,修改说明
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

	/**
	 * 表达式计算结果
	 */
	private boolean logicResult = false;

	/**
	 * 逻辑内的sql
	 */
	private String sqlPart;

	/**
	 * 包含逻辑计算和sql中参数数量
	 */
	private int paramsCnt;

	/**
	 * 在此逻辑片段前面所有sql中所有的参数数量
	 */
	private int preParamsCnt;

	/**
	 * 逻辑表达式中的参数数量
	 */
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
