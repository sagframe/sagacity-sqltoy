/**
 * 
 */
package com.sagframe.sqltoy.showcase;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.sagacity.sqltoy.dao.SqlToyLazyDao;
import org.sagacity.sqltoy.utils.DateUtil;
import org.sagacity.sqltoy.utils.NumberUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.alibaba.fastjson.JSON;
import com.sagframe.sqltoy.SqlToyApplication;
import com.sagframe.sqltoy.showcase.vo.TransInfo15dVO;
import com.sagframe.sqltoy.showcase.vo.TransInfoHisVO;
import com.sagframe.sqltoy.showcase.vo.UserLogVO;

/**
 * @project sqltoy-showcase
 * @description 演示分库分表sharding功能
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:ShardingCaseServiceTest.java,Revision:v1.0,Date:2019年7月12日
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = SqlToyApplication.class)
public class ShardingCaseServiceTest {
	@Autowired
	private SqlToyLazyDao sqlToyLazyDao;

	// 演示分库数据保存或修改效果,参见UserLogVO 对象的注解(maxConcurrents、maxWaitSeconds 是可选配置)
	// @Sharding(db = @Strategy(name = "hashBalanceDBSharding", fields ={"userId"}),
	// //table = @Strategy(name = "hashBalanceSharding", fields ={"userId" }),
	// maxConcurrents = 10, maxWaitSeconds = 1800
	// )
	// hashBalanceDBSharding分库策略配置参见:src/main/resources/spring/spring-sqltoy-sharding.xml
	@Test
	public void testInsertHashSharding() {
		List<UserLogVO> entities = new ArrayList<UserLogVO>();
		UserLogVO logVO = new UserLogVO();
		logVO.setUserId("S0001");
		logVO.setChannel("WEXIN");
		logVO.setDeviceCode("MEI0001");
		logVO.setContents("S0001测试");
		logVO.setLogType("APP_LOGIN");
		logVO.setLogDate(LocalDate.parse("2019-07-31"));
		logVO.setLogTime(DateUtil.getDateTime());
		entities.add(logVO);

		UserLogVO logVO1 = new UserLogVO();
		logVO1.setUserId("S0002");
		logVO1.setChannel("ALIPAY");
		logVO1.setDeviceCode("MEI0002");
		logVO1.setContents("S0002测试");
		logVO1.setLogType("APP_LOGIN");
		logVO1.setLogDate(LocalDate.parse("2019-07-31"));
		logVO1.setLogTime(DateUtil.getDateTime());
		entities.add(logVO1);

		UserLogVO logVO2 = new UserLogVO();
		logVO2.setUserId("S0003");
		logVO2.setChannel("ALIPAY");
		logVO2.setDeviceCode("MEI0003");
		logVO2.setContents("S0003测试");
		logVO2.setLogType("APP_LOGIN");
		logVO2.setLogDate(LocalDate.parse("2019-07-31"));
		logVO2.setLogTime(DateUtil.getDateTime());
		entities.add(logVO2);

		// 这里无论是批量还是单条操作都会进行分库(单条是特殊的批量),根据userId字段值取模获取对应操作的数据库
		sqlToyLazyDao.saveOrUpdateAll(entities);
	}

	// 演示根据分库字段值定位具体数据库，并执行相应查询语句
	// 先执行testInsertHashSharding 方法
	// 详见:sqltoy-showcase.sql 文件sqltoy_db_sharding_case 的
	// <sharding-datasource strategy="hashBalanceDBSharding" params="userId"/>
	@Test
	public void testSearchHashDBSharding() {
		List<UserLogVO> result = sqlToyLazyDao.findBySql("sqltoy_db_sharding_case",
				new String[] { "userId", "beginDate", "endDate" }, new Object[] { "S0001", null, null },
				UserLogVO.class);
		System.err.println(JSON.toJSONString(result));
	}

	// 此场景演示水平分表场景下，将高热度数据跟低热度数据分表存储，以提升高热度数据查询效率(正常模式可以通过分区表来实现,但提供当天、近15天性质的高热度表效果更佳)
	// 本范例则以15天和历史表来进行演示。
	//
	@Test
	public void testTableSharding() {
		// 先删除
		sqlToyLazyDao.executeSql("delete from sqltoy_trans_info_15d", new String[] {}, new Object[] {});
		sqlToyLazyDao.executeSql("delete from sqltoy_trans_info_his", new String[] {}, new Object[] {});
		// 初始化数据
		List transHisSet = new ArrayList();
		List trans15DSet = new ArrayList();
		Date today = DateUtil.getNowTime();
		String[] channels = { "WEXIN", "ALIPAY", "YINLIAN" };
		// 近15天的数据
		for (int i = 0; i < 15; i++) {
			TransInfo15dVO trans = new TransInfo15dVO();
			trans.setUserId("S000" + (i + 1));
			trans.setCardNo("192099990000" + i);
			trans.setStatus(1);
			trans.setTransAmt(BigDecimal.valueOf(1000 + i * NumberUtil.getRandomNum(50)));
			trans.setTransChannel(channels[NumberUtil.getRandomNum(2)]);
			trans.setTransTime(DateUtil.asLocalDateTime(DateUtil.addDay(today, 0 - i)));
			trans.setTransDate(DateUtil.asLocalDate(DateUtil.addDay(today, 0 - i)));
			trans.setTransCode("T00" + NumberUtil.getRandomNum(9));
			trans.setResultCode("1");
			trans15DSet.add(trans);
		}
		sqlToyLazyDao.saveAll(trans15DSet);

		// 15天前的历史数据
		for (int i = 0; i < 100; i++) {
			TransInfo15dVO trans = new TransInfo15dVO();
			trans.setUserId("S000" + (i + 1));
			trans.setCardNo("192099990000" + i);
			trans.setStatus(1);
			trans.setTransAmt(BigDecimal.valueOf(1000 + i * NumberUtil.getRandomNum(50)));
			trans.setTransChannel(channels[NumberUtil.getRandomNum(2)]);
			trans.setTransTime(DateUtil.asLocalDateTime(DateUtil.addDay(today, 0 - i - 15)));
			trans.setTransDate(DateUtil.asLocalDate(DateUtil.addDay(today, 0 - i - 15)));
			trans.setTransCode("T00" + NumberUtil.getRandomNum(9));
			trans.setResultCode("1");
			transHisSet.add(trans);
		}
		sqlToyLazyDao.saveAll(transHisSet);

		String sql = "sqltoy_15d_table_sharding_case";
		String[] paramNamed = { "beginDate", "endDate" };
		// 根据开始日期自动会查询15天表
		List<TransInfo15dVO> fifteenResult = sqlToyLazyDao.findBySql(sql, paramNamed,
				new Object[] { DateUtil.addDay(today, -10), DateUtil.addDay(today, -4) }, TransInfo15dVO.class);
		System.err.print(JSON.toJSONString(fifteenResult));

		// 根据日期自动会查询历史表
		List<TransInfoHisVO> hisResult = sqlToyLazyDao.findBySql(sql, paramNamed,
				new Object[] { DateUtil.addDay(today, -30), DateUtil.addDay(today, -21) }, TransInfoHisVO.class);
		System.err.print(JSON.toJSONString(hisResult));
	}
}
