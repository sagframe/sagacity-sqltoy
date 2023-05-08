package org.sagacity.sqltoy.utils;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

public class IdUtilTest {
	@Test
	public void testNanoTimeId() {
		Set<BigDecimal> idset = new HashSet<BigDecimal>();
		BigDecimal id;
		for (int i = 0; i < 100000; i++) {
			id = IdUtil.getNanoTimeId("system_info", "001");
			// System.err.println("id=" + id);
			if (idset.contains(id)) {
				System.err.println("id=" + id + "已经重复");
				break;
			} else {
				idset.add(id);
			}
		}
		System.err.println("没有重复！");
		System.err.println(99 % 100);
		System.err.println(DateUtil.formatDate(new Date(), "yyMMddHHmmssS"));
	}

	@Test
	public void testNanoTimeIdTime() {
		Set<BigDecimal> idset = new HashSet<BigDecimal>();
		BigDecimal id;
		long currentTime = System.currentTimeMillis();
		for (int i = 0; i < 1000000; i++) {
			id = IdUtil.getNanoTimeId("system_info", "001");
			if (idset.contains(id)) {
				System.err.println("id=" + id + "已经重复");
				break;
			} else {
				idset.add(id);
			}
		}
		System.err.println("没有重复！" + (System.currentTimeMillis() - currentTime));
	}

	@Test
	public void testShortNanoTimeId() {
		Set<BigDecimal> idset = new HashSet<BigDecimal>();
		BigDecimal id;
		long currentTime = System.currentTimeMillis();
		for (int i = 0; i < 1000000; i++) {
			id = IdUtil.getShortNanoTimeId("system_info", "001");
			if (idset.contains(id)) {
				System.err.println("id=" + id + "已经重复");
				break;
			} else {
				idset.add(id);
			}
		}
		System.err.println("没有重复！" + (System.currentTimeMillis() - currentTime));
	}

	@Test
	public void testShortNanoId() {
		String idsColumnStr="ta.id,ta.name,ta.staff_id";
		System.err.println(idsColumnStr.replace("ta.", ""));
		System.err.println(idsColumnStr.replace("ta.", "tv."));
		System.err.println(IdUtil.getUUID());
		System.err.println(IdUtil.getNanoTimeId("system_info", "001"));
		String id = IdUtil.getShortNanoTimeId("system_info", "001").toPlainString();
		String id1 = IdUtil.getShortNanoTimeId("001").toPlainString();
		String id2 = IdUtil.getShortNanoTimeId("001").toPlainString();
		System.err.println(id);
		System.err.println(id1);
		System.err.println(id2);
//		for (int i = 0; i < 100; i++) {
//			System.err.println(IdUtil.getShortNanoTimeId("001"));
//		}
	}
}
