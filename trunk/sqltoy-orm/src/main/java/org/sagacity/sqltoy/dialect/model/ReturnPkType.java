/**
 * 
 */
package org.sagacity.sqltoy.dialect.model;

/**
 * @project sqltoy-orm
 * @description 主键返回策略
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:ReturnPkType.java,Revision:v1.0,Date:2015年3月19日
 */
public enum ReturnPkType {
	// 手工赋值
	GENERATED_KEYS(1),

	// 数据库sequence
	PREPARD_ID(2),

	// 数据库identity自增模式,oracle,db2中对应always identity
	RESULT_GET(3);

	private final Integer returnPkType;

	private ReturnPkType(Integer returnPkType) {
		this.returnPkType = returnPkType;
	}

	public Integer getValue() {
		return this.returnPkType;
	}

	public String toString() {
		return Integer.toString(this.returnPkType);
	}

	/**
	 * @todo 转换给定字符串为枚举主键策略
	 * @param returnPkType
	 * @return
	 */
	public static ReturnPkType getReturnPkType(Integer returnPkType) {
		if (returnPkType == 1)
			return GENERATED_KEYS;
		else if (returnPkType == 2)
			return PREPARD_ID;
		else if (returnPkType == 1)
			return RESULT_GET;
		return RESULT_GET;
	}
}
