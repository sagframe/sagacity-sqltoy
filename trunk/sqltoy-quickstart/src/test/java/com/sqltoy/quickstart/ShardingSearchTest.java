/**
 * 
 */
package com.sqltoy.quickstart;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.assertj.core.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.sagacity.sqltoy.dao.SqlToyLazyDao;
import org.sagacity.sqltoy.service.SqlToyCRUDService;
import org.sagacity.sqltoy.utils.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.alibaba.fastjson.JSON;
import com.sqltoy.quickstart.vo.TransInfo15dVO;

/**
 * @project sqltoy-quickstart
 * @description 演示查询分表分库
 * @author zhongxuchen
 * @version v1.0, Date:2020-7-22
 * @modify 2020-7-22,修改说明
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = SqlToyApplication.class)
public class ShardingSearchTest {
	@Autowired
	SqlToyLazyDao sqlToyLazyDao;

	@Autowired
	SqlToyCRUDService sqlToyCRUDService;

	// Sharding 策略配置参见:com.sqltoy.ShardingStrategyConfig
	// 第一步先初始化模拟表数据
	@Test
	public void initRealHisData() {
		// 请参见TransInfo15dVO 中的sharding注解(如果初次生成VO，则将下面的配置放在类上)
		// @SqlToyEntity
		// @Sharding(table = @Strategy(name = "realHisTable", fields = "transDate"))
		// public class TransInfo15dVO extends AbstractTransInfo15dVO {}
		List<TransInfo15dVO> transInfos = new ArrayList<TransInfo15dVO>();
		// 构造过去100天的数据，会自动分表插入
		for (int i = 0; i < 100; i++) {
			TransInfo15dVO vo = new TransInfo15dVO();
			vo.setCardNo("2009" + StringUtil.addLeftZero2Len(Integer.toString(i), 3));
			vo.setResultCode("1");
			vo.setStatus(1);
			vo.setTransAmt(BigDecimal.valueOf(i * 100));
			vo.setTransChannel("POS");
			vo.setTransCode("T001");
			vo.setUserId("S001");
			vo.setTransTime(LocalDateTime.now());
			vo.setTransDate(LocalDate.now().plusDays(-i));
			transInfos.add(vo);
		}
		sqlToyCRUDService.saveAll(transInfos);
	}

	// 演示根据开始日期作为分表依据，开始日期是40天前的会自动sharding到历史表中去查询
	@Test
	public void testShardingTableSearch() {
		List<TransInfo15dVO> trans = sqlToyLazyDao.findBySql("qstart_sharding_table_case",
				Arrays.array("beginDate", "endDate"), Arrays.array(LocalDate.now().plusDays(-40), null),
				TransInfo15dVO.class);
		trans.forEach((vo) -> {
			System.err.println(JSON.toJSONString(vo));
		});

	}
}
