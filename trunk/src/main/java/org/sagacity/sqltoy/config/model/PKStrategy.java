/**
 * 
 */
package org.sagacity.sqltoy.config.model;

/**
 * @project sqltoy-orm
 * @description 通过枚举方式定义数据库主键实现的四种机制
 * @author renfei.chen <a href="mailto:zhongxuchen@hotmail.com">联系作者</a>
 * @version Revision:v1.0,Date:2013-6-10
 * @Modification Date:2013-6-10 {填写修改说明}
 */
public enum PKStrategy {
	// 手工赋值
	ASSIGN("assign"),
	
	// 数据库sequence
	SEQUENCE("sequence"),
	
	// 数据库identity自增模式,oracle,db2中对应always identity
	IDENTITY("identity"),
	
	// 自定义类产生一个不唯一的主键
	GENERATOR("generator");
	
	private final String strategy;

	private PKStrategy(String strategy) {
		this.strategy = strategy;
	}

	public String getValue() {
		return this.strategy;
	}

	public String toString() {
		return this.strategy;
	}

	/**
	 * @todo 转换给定字符串为枚举主键策略
	 * @param strategy
	 * @return
	 */
	public static PKStrategy getPKStrategy(String strategy) {
		if (strategy.equalsIgnoreCase(ASSIGN.getValue()))
			return ASSIGN;
		else if (strategy.equalsIgnoreCase(SEQUENCE.getValue()))
			return SEQUENCE;
		else if (strategy.equalsIgnoreCase(IDENTITY.getValue()))
			return IDENTITY;
		else if (strategy.equalsIgnoreCase(GENERATOR.getValue()))
			return GENERATOR;
		return ASSIGN;
	}
}
