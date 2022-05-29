package org.sagacity.sqltoy.plugins;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.demo.vo.StaffInfoVO;
import org.sagacity.sqltoy.model.IgnoreKeyCaseMap;
import org.sagacity.sqltoy.plugins.id.macro.impl.SqlLoop;

import com.alibaba.fastjson.JSON;

public class SqlLoopTest {
	@Test
	public void testSqlLoop() {
		List<StaffInfoVO> staffInfos = new ArrayList<StaffInfoVO>();
		for (int i = 0; i < 5; i++) {
			StaffInfoVO staff = new StaffInfoVO();
			staff.setStaffId("S000" + (i + 1));
			staff.setBirthday(LocalDate.now());
			staffInfos.add(staff);
		}
		SqlLoop sqlLoop = new SqlLoop(false);
		String[] params = { "staffInfos", "(staffId=':staffInfos[i].staffId' and birthDay=':staffInfos[i].birthday')",
				"or" };
		IgnoreKeyCaseMap<String, Object> keyValues = new IgnoreKeyCaseMap<String, Object>();
		keyValues.put("staffInfos", staffInfos);
		String result = sqlLoop.execute(params, keyValues);
		System.err.print(result);
	}

	@Test
	public void testSqlLoopStr() {
		List<String> staffInfos = new ArrayList<String>();
		for (int i = 0; i < 5; i++) {
			if (i == 3 || i == 4) {
				staffInfos.add(null);
			}else {
				staffInfos.add("S000" + (i + 1));
			}
		}
		SqlLoop sqlLoop = new SqlLoop(true);
		String[] params = { "staffInfos", " and staffId=':staffInfos[i]'" };
		IgnoreKeyCaseMap<String, Object> keyValues = new IgnoreKeyCaseMap<String, Object>();
		keyValues.put("staffInfos", staffInfos);
		String result = sqlLoop.execute(params, keyValues);
		System.err.print(result);
	}

	@Test
	public void testSqlLoopLike() {
		List<String> staffInfos = new ArrayList<String>();
		for (int i = 0; i < 5; i++) {
			staffInfos.add("S000" + (i + 1));
		}
		SqlLoop sqlLoop = new SqlLoop();
		String[] params = { "staffInfos", " and staffId like '%:staffInfos[i]%'" };
		IgnoreKeyCaseMap<String, Object> keyValues = new IgnoreKeyCaseMap<String, Object>();
		keyValues.put("staffInfos", staffInfos);
		String result = sqlLoop.execute(params, keyValues);
		System.err.print(result);
	}

	@Test
	public void testParseParams() {
		SqlLoop sqlLoop = new SqlLoop();
		String template = ":sqlToyLoopAsKey_0A.item=1 and name like '%:sqlToyLoopAsKey_0A.name%' :sqlToyLoopAsKey_0A.sexType名称 or :sqlToyLoopAsKey_0A.订单_名称_id";
		Map<String, String[]> result = sqlLoop.parseParams(template);
		System.err.println(JSON.toJSONString(result));
	}
}
