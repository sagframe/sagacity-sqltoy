/**
 * 
 */
package org.sagacity.sqltoy.config.model;

import java.io.Serializable;

/**
 * @project sqltoy-orm
 * @description 数据库主键生成策略
 * @author renfei.chen <a href="mailto:zhongxuchen@hotmail.com">联系作者</a>
 * @version id:PKGeneratorStrategy.java,Revision:v1.0,Date:2012-6-7 下午2:43:26
 */
public class PKGeneratorStrategy implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = -1264532949768965572L;

	public PKGeneratorStrategy() {

	}

	/**
	 * @param name
	 * @param strategy
	 * @param sequence
	 * @param generator
	 */
	public PKGeneratorStrategy(String name, String strategy, String sequence, String generator) {
		super();
		this.name = name;
		this.strategy = strategy;
		this.sequence = sequence;
		this.generator = generator;
	}

	/**
	 * 表名,可以是一个正则表达式
	 */
	private String name;

	/**
	 * 主键生成策略,生成策略包含:assign,sequence,identity,generator四种方式
	 */
	private String strategy = "assign";

	/**
	 * 数据库表主键对应的sequence
	 */
	private String sequence;

	/**
	 * 主键产生器,对应一个class
	 */
	private String generator;

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

}
