package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.FormatModel;
import org.sagacity.sqltoy.config.model.LabelIndexModel;

/**
 * 回归测试：date-format/number-format按数字列下标配置越界时,
 * 抛出指向format配置的明确IllegalArgumentException(含列数与配置值),
 * 而非结果集处理深处的裸IndexOutOfBoundsException;合法下标行为不变
 */
public class ResultUtilsFormatColumnTest {

	private static FormatModel numberFormat(String column) {
		FormatModel fmt = new FormatModel();
		fmt.setColumn(column);
		fmt.setType(2);
		fmt.setFormat("#,###.##");
		return fmt;
	}

	// 反射调用,异常解包后若为IllegalArgumentException则原样抛出便于断言
	private static void invoke(String methodName, Object rowsOrRow, FormatModel fmt) throws Throwable {
		LabelIndexModel labelIndex = new LabelIndexModel();
		Method method = ResultUtils.class.getDeclaredMethod(methodName, List.class, Iterator.class,
				LabelIndexModel.class);
		method.setAccessible(true);
		try {
			method.invoke(null, rowsOrRow, Collections.singleton(fmt).iterator(), labelIndex);
		} catch (InvocationTargetException e) {
			throw e.getCause();
		}
	}

	@Test
	public void outOfRangeIndexGivesConfigOrientedError() throws Throwable {
		List<List> rows = new ArrayList<List>();
		rows.add(new ArrayList<>(Arrays.asList(1234.5, "name")));
		Throwable cause = assertThrows(IllegalArgumentException.class,
				() -> invoke("formatColumn", rows, numberFormat("5")));
		assertTrue(cause.getMessage().contains("列(column):5") && cause.getMessage().contains("列数量:2"),
				"实际:" + cause.getMessage());
		assertTrue(cause.getMessage().contains("date-format/number-format"), "实际:" + cause.getMessage());
	}

	@Test
	public void negativeIndexGivesConfigOrientedError() throws Throwable {
		List<List> rows = new ArrayList<List>();
		rows.add(new ArrayList<>(Arrays.asList(1234.5, "name")));
		Throwable cause = assertThrows(IllegalArgumentException.class,
				() -> invoke("formatColumn", rows, numberFormat("-1")));
		assertTrue(cause.getMessage().contains("列(column):-1"), "实际:" + cause.getMessage());
	}

	@Test
	public void validIndexFormatsUnchanged() throws Throwable {
		List<List> rows = new ArrayList<List>();
		rows.add(new ArrayList<>(Arrays.asList(1234.5, "name")));
		invoke("formatColumn", rows, numberFormat("0"));
		assertEquals("1,234.5", rows.get(0).get(0));
	}

	@Test
	public void formatRowColumnSameGuard() throws Throwable {
		List row = new ArrayList<>(Arrays.asList(1234.5, "name"));
		Throwable cause = assertThrows(IllegalArgumentException.class,
				() -> invoke("formatRowColumn", row, numberFormat("9")));
		assertTrue(cause.getMessage().contains("列(column):9") && cause.getMessage().contains("列数量:2"),
				"实际:" + cause.getMessage());
	}
}
