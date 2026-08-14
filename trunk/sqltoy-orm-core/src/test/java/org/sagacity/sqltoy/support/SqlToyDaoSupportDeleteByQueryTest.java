package org.sagacity.sqltoy.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.model.EntityQuery;

/**
 * 回归测试：deleteByQuery参数为null时必须抛出带明确提示的IllegalArgumentException,
 * 修复前先解引用entityQuery.getInnerModel()直接NPE
 */
public class SqlToyDaoSupportDeleteByQueryTest {
	private final SqlToyDaoSupport daoSupport = new SqlToyDaoSupport();

	@Test
	public void nullEntityQueryGivesClearMessageNotNpe() {
		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
				() -> daoSupport.deleteByQuery(String.class, (EntityQuery) null));
		// 修复前:NullPointerException
		assertTrue(ex.getMessage().contains("deleteByQuery"));
		assertTrue(ex.getMessage().contains("不能为空"));
	}

	@Test
	public void nullEntityClassGivesClearMessage() {
		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
				() -> daoSupport.deleteByQuery(null, EntityQuery.create().where("id=?").values("1")));
		assertTrue(ex.getMessage().contains("不能为空"));
	}

	@Test
	public void blankWhereStillRejected() {
		// where/values缺失时同样给出明确提示(非NPE)
		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
				() -> daoSupport.deleteByQuery(String.class, EntityQuery.create()));
		assertTrue(ex.getMessage().contains("不能为空"));
		assertEquals(IllegalArgumentException.class, ex.getClass());
	}
}
