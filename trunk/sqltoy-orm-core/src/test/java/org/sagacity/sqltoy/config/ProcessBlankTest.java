package org.sagacity.sqltoy.config;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.SqlToyResult;

/**
 * 回归测试:验证@blank(?)各位置组合下参数剔除的正确性(原审计声称"blank位于首个?之前
 * 会remove(-1)越界"经实测与推演均为误报:paramCnt≥blankCnt恒成立,前置blank为remove(0)合法)
 */
public class ProcessBlankTest {

	private static final String BLANK = "@blank(?)";

	private SqlToyResult process(String sql, Object[] values) {
		return SqlConfigParseUtils.processSql(sql, null, values);
	}

	@Test
	public void blankAtLeadingPosition() {
		SqlToyResult result = process("select * from t where " + BLANK + " and id=?", new Object[] { "BV", "IDX" });
		assertTrue(!result.getSql().contains("@blank"));
		assertArrayEquals(new Object[] { "IDX" }, result.getParamsValue());
	}

	@Test
	public void blankAtMiddlePosition() {
		SqlToyResult result = process("select * from t where id=? and " + BLANK + " and name=?",
				new Object[] { "IDX", "BV", "NM" });
		assertArrayEquals(new Object[] { "IDX", "NM" }, result.getParamsValue());
	}

	@Test
	public void twoBlanksAroundNormalParam() {
		SqlToyResult result = process("select * from t where " + BLANK + " and " + BLANK + " and id=?",
				new Object[] { "B1", "B2", "IDX" });
		assertArrayEquals(new Object[] { "IDX" }, result.getParamsValue());
	}

	@Test
	public void blankAtTailPosition() {
		SqlToyResult result = process("select * from t where id=? and " + BLANK, new Object[] { "IDX", "BV" });
		assertArrayEquals(new Object[] { "IDX" }, result.getParamsValue());
	}

	@Test
	public void blankAsOnlyParam() {
		SqlToyResult result = process("select * from t where " + BLANK, new Object[] { "BV" });
		assertArrayEquals(new Object[0], result.getParamsValue());
	}

	@Test
	public void namedParamModeBlank() {
		Map<String, Object> argMap = new HashMap<String, Object>();
		argMap.put("flag", "FV");
		argMap.put("id", "IDX");
		SqlToyResult result = SqlConfigParseUtils.processSql("select * from t where @blank(:flag) and id=:id", argMap);
		assertArrayEquals(new Object[] { "IDX" }, result.getParamsValue());
	}
}
