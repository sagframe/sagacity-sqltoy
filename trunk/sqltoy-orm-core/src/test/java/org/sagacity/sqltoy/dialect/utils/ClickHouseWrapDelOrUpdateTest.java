package org.sagacity.sqltoy.dialect.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.SqlType;

/**
 * 回归测试：clickhouse wrapDelOrUpdate对无where的delete和无set的update给出明确错误,
 * 不再substring(-1)越界或从错误位置截断生成坏SQL;正常语句转换不变
 */
public class ClickHouseWrapDelOrUpdateTest {

	private static EntityMeta meta(String tableName) {
		EntityMeta meta = new EntityMeta();
		meta.setTableName(tableName);
		return meta;
	}

	@Test
	public void deleteWithoutWhereGivesClearError() {
		// 修复前:matchIndex返回-1,substring(-1)抛StringIndexOutOfBoundsException
		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
				() -> ClickHouseDialectUtils.wrapDelOrUpdate(meta("t_order"), "delete from t_order", SqlType.delete));
		assertTrue(ex.getMessage().contains("where"), "实际:" + ex.getMessage());
		assertTrue(ex.getMessage().contains("t_order"), "实际:" + ex.getMessage());
	}

	@Test
	public void updateWithoutSetGivesClearError() {
		// 修复前:-1+4=3,substring(3)从错误位置截断生成坏SQL静默执行
		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
				() -> ClickHouseDialectUtils.wrapDelOrUpdate(meta("t_order"), "update t_order", SqlType.update));
		assertTrue(ex.getMessage().contains("set"), "实际:" + ex.getMessage());
	}

	@Test
	public void normalDeleteAndUpdateUnchanged() {
		String delete = ClickHouseDialectUtils.wrapDelOrUpdate(meta("t_order"),
				"delete from t_order where status=:status", SqlType.delete);
		assertTrue(delete.contains("alter table t_order delete"), "实际:" + delete);
		assertTrue(delete.contains("where status=:status"), "实际:" + delete);

		String update = ClickHouseDialectUtils.wrapDelOrUpdate(meta("t_order"),
				"update t_order set status=:status where id=:id", SqlType.update);
		assertTrue(update.contains("alter table t_order update"), "实际:" + update);
		assertTrue(update.contains("status=:status where id=:id"), "实际:" + update);
		assertEquals("alter table t_order update  status=:status where id=:id", update);
	}
}
