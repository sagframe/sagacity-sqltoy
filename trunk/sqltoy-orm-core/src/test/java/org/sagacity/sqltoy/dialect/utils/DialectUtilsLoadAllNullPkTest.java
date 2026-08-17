package org.sagacity.sqltoy.dialect.utils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.demo.domain.StaffInfo;

/**
 * 回归测试：loadAll单主键入参含null时给出带行号的明确IllegalArgumentException
 * (与复合主键分支防护对称)。原实现经sliceToArray过滤null后null主键实体被静默跳过
 * (传N个返回N-1个无任何提示),本修复改为按行提取并报错
 */
public class DialectUtilsLoadAllNullPkTest {

	@Test
	public void nullSinglePkGivesRowOrientedError() {
		SqlToyContext context = new SqlToyContext();
		// 第0行正常,第1行主键未赋值(常见业务错误:忘记setId)
		List<StaffInfo> entities = Arrays.asList(new StaffInfo("S0001"), new StaffInfo((String) null));
		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
				() -> DialectUtils.loadAll(context, entities, false, null, null, null, null, null, null, 0, 0, null));
		// 报错带行号与字段名,可直接定位(修复前:null主键实体被静默跳过,返回数量少1)
		assertTrue(ex.getMessage().contains("row:1"), "实际:" + ex.getMessage());
		assertTrue(ex.getMessage().contains("staffId"), "实际:" + ex.getMessage());
	}

	@Test
	public void allNullPkStillReportsFirstRow() {
		SqlToyContext context = new SqlToyContext();
		List<StaffInfo> entities = Arrays.asList(new StaffInfo((String) null));
		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
				() -> DialectUtils.loadAll(context, entities, false, null, null, null, null, null, null, 0, 0, null));
		assertTrue(ex.getMessage().contains("row:0"), "实际:" + ex.getMessage());
	}

	@Test
	public void normalInputPassesValidation() {
		SqlToyContext context = new SqlToyContext();
		List<StaffInfo> entities = Arrays.asList(new StaffInfo("S0001"), new StaffInfo("S0002"));
		try {
			// null连接/null方言:入参校验应通过,失败发生在其后的SQL组织环节(dbType拆箱NPE),
			// 且异常类型不是IllegalArgumentException(证明不是校验拦截)
			DialectUtils.loadAll(context, entities, false, null, null, null, null, null, null, 0, 0, null);
		} catch (IllegalArgumentException ex) {
			if (ex.getMessage().contains("row:") || ex.getMessage().contains("pk field:")) {
				throw new AssertionError("正常入参不应触发主键校验:" + ex.getMessage());
			}
			// 其他IllegalArgumentException(如元数据相关)不属校验误伤
		} catch (Throwable expectedAtLaterStage) {
			// 预期在后续环节失败,说明校验已通过
		}
	}
}
