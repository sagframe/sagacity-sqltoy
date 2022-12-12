package org.sagacity.sqltoy.utils;

import java.sql.Connection;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.SqlExecuteStat;
import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.callback.DataSourceCallbackHandler;
import org.sagacity.sqltoy.config.model.PageOptimize;
import org.sagacity.sqltoy.config.model.SqlExecuteTrace;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.demo.vo.DataRange;
import org.sagacity.sqltoy.demo.vo.StaffInfoVO;
import org.sagacity.sqltoy.exception.DataAccessException;
import org.sagacity.sqltoy.executor.QueryExecutor;
import org.sagacity.sqltoy.model.QueryResult;
import org.sagacity.sqltoy.model.inner.QueryExecutorExtend;

public class BeanUtilTest {

	// 测试多级反射
	@Test
	public void testMultLevelReflect() {
		StaffInfoVO staff = new StaffInfoVO();
		staff.setEmail("zhongxuchen@gmail.com");
		staff.setStaffId("S001");
		DataRange dataRange = new DataRange();
		dataRange.setBeginDate(DateUtil.getDate("2020-10-01"));
		dataRange.setEndDate(LocalDate.now());
		staff.setDataRange(dataRange);

		HashMap params = new HashMap();
		params.put("companyId", "C0001");
		params.put("companyName", "xxx企业集团");
		staff.setParams(params);

		Object[] result = BeanUtil.reflectBeanToAry(staff, new String[] { "staffId", "email", "dataRange.beginDate",
				"dataRange.enddate", "params.companyId", "params.companyName" }, null, null);
		for (Object tmp : result) {
			System.err.println(tmp);
		}
	}

	@Test
	public void testTypeName() {
		System.err.println(DateUtil.formatDate(LocalDate.now(), "MMM dd,yyyy", Locale.US));
		System.err.println(byte[].class.getName());
		System.err.println(byte[].class.getTypeName());
	}

	@Test
	public void testTypeName1() {
		Pattern IN_PATTERN = Pattern.compile(
				"(?i)\\s+in\\s*((\\(\\s*\\?(\\s*\\,\\s*\\?)*\\s*\\))|((\\(\\s*){2}\\?(\\s*\\,\\s*\\?)+(\\s*\\)){2}))");
		String sql = " t.id in (( ? , ?))";
		System.err.println(StringUtil.matches(sql, IN_PATTERN));
		sql = " t.id in ( ( ? , ?   )   )";
		System.err.println(StringUtil.matches(sql, IN_PATTERN));
		sql = " t.id in ((?))";
		System.err.println(StringUtil.matches(sql, IN_PATTERN));
		sql = " t.id in (   ?)";
		System.err.println(StringUtil.matches(sql, IN_PATTERN));
		sql = " t.id in ((   ? ))";
		System.err.println(StringUtil.matches(sql, IN_PATTERN));
		sql = " t.id in (?,?,?)";
		System.err.println(StringUtil.matches(sql, IN_PATTERN));
		sql = " t.id in (    ?, ? , ?)";
		System.err.println(StringUtil.matches(sql, IN_PATTERN));
	}

	@Test
	public void testParall() {
		DebugUtil.beginTime("00001");
		ExecutorService pool = null;
		try {
			pool = Executors.newFixedThreadPool(2);
			// 查询总记录数量
			pool.submit(new Runnable() {
				@Override
				public void run() {
					//System.err.println("@@@@@@@@@@@@@@@@1");
				}
			});
			// 获取记录
			pool.submit(new Runnable() {
				@Override
				public void run() {
					//System.err.println("---------------1");
				}
			});
			pool.shutdown();
			pool.awaitTermination(SqlToyConstants.PARALLEL_MAXWAIT_SECONDS, TimeUnit.SECONDS);
		} catch (Exception e) {
			e.printStackTrace();
			throw new DataAccessException("并行查询执行错误:" + e.getMessage(), e);
		} finally {
			if (pool != null) {
				pool.shutdownNow();
			}
		}
		DebugUtil.endTime("00001");
	}

}
