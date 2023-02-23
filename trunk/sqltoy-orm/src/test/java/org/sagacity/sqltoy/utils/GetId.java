/**
 * 
 */
package org.sagacity.sqltoy.utils;

import java.math.BigDecimal;
import java.util.Set;

/**
 * @project sagacity-sqltoy
 * @description 请在此说明类的功能
 * @author zhong
 * @version v1.0, Date:2022年11月17日
 * @modify 2022年11月17日,修改说明
 */
public class GetId implements Runnable {

	private Set<BigDecimal> idset;

	private int loopSize = 100;

	public GetId(Set<BigDecimal> idset, int loopSize) {
		this.idset = idset;
		this.loopSize = loopSize;
	}

	@Override
	public void run() {
		BigDecimal id;
		for (int i = 0; i < loopSize; i++) {
			id = IdUtil.getNanoTimeId(null);
			if (idset.contains(id)) {
				System.err.println("id=" + id + "已经重复");
				break;
			} else {
				idset.add(id);
			}
		}
		System.err.println("没有重复");
	}

}
