/**
 * 
 */
package org.sagacity.sqltoy.dialect.model;

/**
 * @project sqltoy-orm
 * @description 主键返回策略
 * @author zhongxuchen
 * @version v1.0,Date:2015年3月19日
 * @modify 2022-5-27 优化之前容易误导的注释，并标记RESULT_GET类型为过时
 */
public enum ReturnPkType {
	// 数据库identity自增模式,oracle,db2中对应always identity
	GENERATED_KEYS(1),

	// 数据库sequence
	PREPARD_ID(2),

	//执行insert 过程中含return,sqlserver2008、postgresql9.4 等需要，高级版本已经废弃
	@Deprecated
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
		if (returnPkType == 1) {
			return GENERATED_KEYS;
		}
		if (returnPkType == 2) {
			return PREPARD_ID;
		}
		if (returnPkType == 3) {
			return RESULT_GET;
		}
		return RESULT_GET;
	}
}
