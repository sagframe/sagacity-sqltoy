package org.sagacity.sqltoy.utils;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.SqlToyConstants;

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
	public void testShortNanoTimeId() {
		Set<BigDecimal> idset = new HashSet<BigDecimal>();
		BigDecimal id;
		long currentTime = System.currentTimeMillis();
		for (int i = 0; i < 5000000; i++) {
			id = IdUtil.getNanoTimeId(null);
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
	public void testMaxThread() {
		Set<BigDecimal> idset = new HashSet<BigDecimal>();
		for (int i = 0; i < 100; i++) {
			GetId getId = new GetId(idset, 100000);
			getId.run();
		}
	}

	@Test
	public void testShortNanoId() {
		// System.err.println(IdUtil.getNanoTimeId("system_info", "001"));
		String id = IdUtil.getShortNanoTimeId("system_info", "001").toPlainString();
		String id1 = IdUtil.getShortNanoTimeId("001").toPlainString();
		String id2 = IdUtil.getShortNanoTimeId("001").toPlainString();
		System.err.println(id);
		System.err.println(id1);
		System.err.println(id2);
		for (int i = 0; i < 10000; i++) {
			System.err.println(IdUtil.getShortNanoTimeId("001"));
		}
	}

	@Test
	public void testSnowflakeId() {
		SnowflakeIdWorker idWorker = new SnowflakeIdWorker(12, 4);
		for (int i = 0; i < 100; i++) {
			System.err.println(idWorker.nextId("sqlTest" + i));
		}
	}
}
