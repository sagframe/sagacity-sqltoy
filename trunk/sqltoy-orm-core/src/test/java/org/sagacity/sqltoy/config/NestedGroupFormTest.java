package org.sagacity.sqltoy.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.SqlToyResult;
import org.sagacity.sqltoy.utils.SqlUtil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * @project sagacity-sqltoy
 * @description 锁定括号分组三种写法 × 四种参数组合的实际展开行为(全部经 processSql 实测)。
 *              结论矩阵(四种参数组合全部产生合法 SQL):
 *              <li>F1 裸 or 连接两个 #[...]:双有值/仅 a/仅 b/双 null 四种组合全部可用
 *              (2026-10-17 起框架自动清理括号内悬挂的 and/or 连接符,此前 a=null 或 b=null 时会悬挂)</li>
 *              <li>F2 or 内嵌第二个 #[...]:同上,四种组合全部可用</li>
 *              <li>F3 首条件裸写 + or 内嵌第二 #[...]:语义为"a 是该组的前提"——a=null 整组剔除
 *              (即便 b 有值),a 有值时 b 独立可选</li>
 * @author zhongxuchen
 * @version v1.0, Date:2026-10-15
 */
public class NestedGroupFormTest {

	/** 统一入口:参数按 a,b,c,d 顺序填充,未提供的自动为 null(整体按 null 参与判空剔除) */
	private String run(String sql, Object... values) {
		String[] names = { "a", "b", "c", "d" };
		Object[] args = new Object[] { null, null, null, null };
		for (int i = 0; i < values.length && i < args.length; i++) {
			args[i] = values[i];
		}
		SqlToyResult r = SqlConfigParseUtils.processSql(SqlUtil.clearMark(sql), names, args);
		return norm(r.getSql());
	}

	private String norm(String sql) {
		return sql.replaceAll("\\s+", " ").trim().replace("( ", "(").replace(" )", ")");
	}

	@Test
	@DisplayName("F1 裸or:四种组合全部可用(括号内悬挂 or 已自动清理)")
	void form1Matrix() {
		assertEquals("where t.type=1 and (t.a=? or t.b=?)", run("where t.type=1 #[and ( #[t.a=:a] or #[t.b=:b] )]", "A", "B"));
		assertEquals("where t.type=1", run("where t.type=1 #[and ( #[t.a=:a] or #[t.b=:b] )]", null, null));
		// 括号内悬挂 or 已被自动清理(2026-10-17 起),不再产生残缺 SQL
		assertEquals("where t.type=1 and (t.a=?)", run("where t.type=1 #[and ( #[t.a=:a] or #[t.b=:b] )]", "A", null));
		assertEquals("where t.type=1 and (t.b=?)", run("where t.type=1 #[and ( #[t.a=:a] or #[t.b=:b] )]", null, "B"));
	}

	@Test
	@DisplayName("F2 or内嵌:四种组合全部可用")
	void form2Matrix() {
		assertEquals("where t.type=1 and (t.a=? or t.b=?)", run("where t.type=1 #[and ( #[t.a=:a] #[or t.b=:b] )]", "A", "B"));
		assertEquals("where t.type=1", run("where t.type=1 #[and ( #[t.a=:a] #[or t.b=:b] )]", null, null));
		assertEquals("where t.type=1 and (t.a=?)", run("where t.type=1 #[and ( #[t.a=:a] #[or t.b=:b] )]", "A", null));
		// 括号内悬挂 or 已被自动清理(2026-10-17 起)
		assertEquals("where t.type=1 and (t.b=?)", run("where t.type=1 #[and ( #[t.a=:a] #[or t.b=:b] )]", null, "B"));
	}

	@Test
	@DisplayName("F3 首条件裸写:语义为 a 是该组前提,a=null 整组剔除,推荐")
	void form3MasterDetailSemantics() {
		assertEquals("where t.type=1 and (t.a=? or t.b=?)", run("where t.type=1 #[and ( t.a=:a #[or t.b=:b] )]", "A", "B"));
		assertEquals("where t.type=1 and (t.a=?)", run("where t.type=1 #[and ( t.a=:a #[or t.b=:b] )]", "A", null));
		assertEquals("where t.type=1", run("where t.type=1 #[and ( t.a=:a #[or t.b=:b] )]", null, "B"));
		assertEquals("where t.type=1", run("where t.type=1 #[and ( t.a=:a #[or t.b=:b] )]", null, null));
	}

	@Test
	@DisplayName("括号嵌套括号:内层成员剔除后,悬挂 and 链式清理")
	void nestedParenChainedCleanup() {
		assertEquals("where t.type=1 and ((t.a=?) and t.c=?)",
				run("where t.type=1 #[and ( ( t.a=:a #[or t.b=:b] ) and t.c=:c )]", "A", null, "C"));
	}

	@Test
	@DisplayName("双括号组并列:两组各自独立清理,互不影响")
	void parallelGroups() {
		assertEquals("where t.type=1 and ((t.b=?) and (t.d=?))",
				run("where t.type=1 #[and ( ( #[t.a=:a] or t.b=:b ) and ( #[t.c=:c] or t.d=:d ) )]",
						null, "B", null, "D"));
	}

	@Test
	@DisplayName("无误伤:now() 函数空括号、over(order by)、含 and/or 的字符串字面量均不受清理影响")
	void noFalsePositives() {
		assertEquals("where t.type=1 and t.date=now() and t.b=?",
				norm(run("where t.type=1 and t.date=now() #[and t.b=:b]", null, "B")));
		assertEquals("select t.id,t.amount,row_number() over(order by t.amount) rn from t and t.b=?",
				norm(run("select t.id,t.amount,row_number() over(order by t.amount) rn from t #[and t.b=:b]", "A", "B")));
		assertEquals("where t.type=1 and t.memo='x and y' and t.b=?",
				norm(run("where t.type=1 and t.memo='x and y' #[and t.b=:b]", null, "B")));
		// 字面量以 and 结尾且紧邻右括号:保守不清,合法 SQL 不受影响
		assertEquals("where (t.memo='xx and') or t.type=1",
				norm(run("where ( t.memo='xx and' ) or t.type=1", null, "B")));
	}
}
