package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

/**
 * @project sagacity-sqltoy
 * @description uniformFastMarks 预编译@fast标记正则后的精确行为：-- 注释与/*注释 两种标记、
 *              只有起始标记、大小写不敏感、无标记/结束在起始之前时原样返回。
 * @author zhongxuchen
 */
public class SqlUtilUniformFastMarksTest {
	@Test
	public void lineCommentMarkersAreBothReplaced() {
		// "-- @fast_end" 整体被替换为一个空格:标记前的空格 + 替换出的空格 + 标记后的空格
		assertEquals("a @fast b   c", SqlUtil.uniformFastMarks("a -- @fast_start b -- @fast_end c"));
	}

	@Test
	public void blockCommentMarkersAreBothReplaced() {
		assertEquals("a @fast b   c", SqlUtil.uniformFastMarks("a /*@fast_start*/ b /*@fast_end*/ c"));
	}

	@Test
	public void mixedMarkerStylesAreSupported() {
		assertEquals("a @fast b   c", SqlUtil.uniformFastMarks("a -- @fast_start b /*@fast_end*/ c"));
	}

	@Test
	public void onlyStartMarkerIsSupported() {
		assertEquals("a @fast b", SqlUtil.uniformFastMarks("a -- @fast_start b"));
	}

	@Test
	public void markerMatchingIsCaseInsensitive() {
		assertEquals("a @fast b", SqlUtil.uniformFastMarks("a -- @FAST_START b"));
		assertEquals("a @fast b   c", SqlUtil.uniformFastMarks("a -- @Fast_Start b -- @FAST_END c"));
	}

	@Test
	public void sqlWithoutMarkersReturnedAsIs() {
		String sql = "select 1 from dual";
		assertSame(sql, SqlUtil.uniformFastMarks(sql), "无标记时应原样返回同一对象");
	}

	@Test
	public void endMarkerBeforeStartMarkerReturnsAsIs() {
		String sql = "a -- @fast_end b -- @fast_start c";
		assertSame(sql, SqlUtil.uniformFastMarks(sql), "结束标记在起始标记之前时不处理");
	}
}