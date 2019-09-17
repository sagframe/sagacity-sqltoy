/**
 * 
 */
package org.sagacity.sqltoy.dialect.model;

import java.io.Serializable;

import org.sagacity.sqltoy.config.model.PKStrategy;

/**
 * @project sqltoy-orm
 * @description 插入数据操作时数据库产生主键的策略(是sequence或identity或uuid或通过自定义接口策略产生随机数)
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:SavePKStrategy.java,Revision:v1.0,Date:2015年3月19日
 */
public class SavePKStrategy implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = -8421486950005753407L;

	/**
	 * 主键策略
	 */
	private PKStrategy pkStrategy;

	/**
	 * 是否针对主键进行手工赋值
	 */
	private boolean isAssginValue;

	public SavePKStrategy(PKStrategy pkStrategy, boolean isAssginValue) {
		this.pkStrategy = pkStrategy;
		this.isAssginValue = isAssginValue;
	}

	/**
	 * @return the pkStrategy
	 */
	public PKStrategy getPkStrategy() {
		return pkStrategy;
	}

	/**
	 * @param pkStrategy
	 *            the pkStrategy to set
	 */
	public void setPkStrategy(PKStrategy pkStrategy) {
		this.pkStrategy = pkStrategy;
	}

	/**
	 * @return the isAssginValue
	 */
	public boolean isAssginValue() {
		return isAssginValue;
	}

	/**
	 * @param isAssginValue
	 *            the isAssginValue to set
	 */
	public void setAssginValue(boolean isAssginValue) {
		this.isAssginValue = isAssginValue;
	}
}
