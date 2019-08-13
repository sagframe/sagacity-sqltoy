/**
 * 
 */
package org.sagacity.quickvo.model;

import java.io.Serializable;

/**
 * @project sagacity-quickvo
 * @description 主键生成策略模型
 * @author renfei.chen $<a href="mailto:zhongxuchen@hotmail.com">联系作者</a>$
 * @version $id:PrimaryKeyStrage.java,Revision:v1.0,Date:2012-6-7 下午2:43:26 $
 */
public class PrimaryKeyStrategy implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 5223098603926168409L;

	public PrimaryKeyStrategy() {

	}

	/**
	 * @param name
	 * @param strategy
	 * @param sequence
	 * @param generator
	 */
	public PrimaryKeyStrategy(String name, String strategy, String sequence, String generator) {
		super();
		this.name = name;
		this.strategy = strategy;
		this.sequence = sequence;
		this.generator = generator;
	}

	/**
	 * 表名
	 */
	private String name;

	/**
	 * 主键生成策略,默认为assign 手工赋予
	 */
	private String strategy = "assign";

	/**
	 * 对应数据库sequence
	 */
	private String sequence;

	/**
	 * 主键产生器,对应主键产生的class
	 */
	private String generator;

	/**
	 * 是否强迫改变
	 */
	private boolean force = false;

	/**
	 * @return the name
	 */
	public String getName() {
		return name;
	}

	/**
	 * @param name
	 *            the name to set
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * @return the strategy
	 */
	public String getStrategy() {
		return strategy;
	}

	/**
	 * @param strategy
	 *            the strategy to set
	 */
	public void setStrategy(String strategy) {
		this.strategy = strategy;
	}

	/**
	 * @return the sequence
	 */
	public String getSequence() {
		return sequence;
	}

	/**
	 * @param sequence
	 *            the sequence to set
	 */
	public void setSequence(String sequence) {
		this.sequence = sequence;
	}

	/**
	 * @return the generator
	 */
	public String getGenerator() {
		return generator;
	}

	/**
	 * @param generator
	 *            the generator to set
	 */
	public void setGenerator(String generator) {
		this.generator = generator;
	}

	/**
	 * @return the force
	 */
	public boolean isForce() {
		return force;
	}

	/**
	 * @param force
	 *            the force to set
	 */
	public void setForce(boolean force) {
		this.force = force;
	}

}
