package org.sagacity.sqltoy.oracle;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.opentest4j.AssertionFailedError;

/**
 * @project sagacity-sqltoy
 * @description 差分对拍测试的公共辅助：独立拷贝输入、按对象身份比对顺序、比对异常。
 *              对拍的双方必须各拿一份独立的输入(算法会原地修改入参)，结果除返回值外还要比对入参被改成了什么样。
 * @author zhongxuchen
 */
public class DiffSupport {
	private DiffSupport() {
	}

	/**
	 * 逐行拷贝(行内元素按引用共享，元素本身均为不可变值)
	 */
	public static List<List<Object>> deepCopy(List<List<Object>> rows) {
		List<List<Object>> out = new ArrayList<>(rows.size());
		for (List<Object> row : rows) {
			out.add(new ArrayList<>(row));
		}
		return out;
	}

	/**
	 * 记录每个行对象当前所处的位置(按对象身份，与 equals 无关)，供算法原地重排后比对顺序
	 */
	public static Map<Object, Integer> identityPositions(List<?> rows) {
		Map<Object, Integer> positions = new IdentityHashMap<>();
		for (int i = 0; i < rows.size(); i++) {
			positions.put(rows.get(i), i);
		}
		return positions;
	}

	/**
	 * 把行的实际顺序翻译成"原先第几行"的序列；出现输入里没有的行对象时记为-1
	 */
	public static List<Integer> identityOrder(List<?> rows, Map<Object, Integer> positions) {
		List<Integer> order = new ArrayList<>(rows.size());
		for (Object row : rows) {
			Integer position = positions.get(row);
			order.add(position == null ? Integer.valueOf(-1) : position);
		}
		return order;
	}

	/**
	 * 逐行逐格比对，相等返回null，否则返回第一处差异的描述(BigDecimal 按 equals 比较，标度不同也算差异)
	 */
	public static String firstDiff(List<?> rowsA, List<?> rowsB) {
		if (rowsA.size() != rowsB.size()) {
			return "row count " + rowsA.size() + " vs " + rowsB.size();
		}
		for (int i = 0; i < rowsA.size(); i++) {
			Object a = rowsA.get(i);
			Object b = rowsB.get(i);
			if (Objects.equals(a, b)) {
				continue;
			}
			if (!(a instanceof List) || !(b instanceof List)) {
				return "row " + i + ": " + a + " vs " + b;
			}
			List<?> ra = (List<?>) a;
			List<?> rb = (List<?>) b;
			if (ra.size() != rb.size()) {
				return "row " + i + " width " + ra.size() + " vs " + rb.size();
			}
			for (int k = 0; k < ra.size(); k++) {
				if (!Objects.equals(ra.get(k), rb.get(k))) {
					return "row " + i + " col " + k + ": " + ra.get(k) + " vs " + rb.get(k);
				}
			}
			return "row " + i + ": same cells but lists not equal";
		}
		return null;
	}

	/**
	 * 执行动作并返回抛出的异常，未抛出返回null
	 */
	public static Throwable capture(Runnable action) {
		try {
			action.run();
			return null;
		} catch (Throwable e) {
			return e;
		}
	}

	/**
	 * 异常签名：类型全名，message 非空时附上
	 */
	public static String errorSignature(Throwable e) {
		if (e == null) {
			return "no error";
		}
		return (e.getMessage() == null) ? e.getClass().getName()
				: e.getClass().getName() + ": " + e.getMessage();
	}

	/**
	 * 比对两侧的异常：类型必须一致；message 只在"两侧都有、且不是 JVM 直接抛出的异常"时才比对。
	 * 空指针、类型转换、数组越界这类异常由 JVM 直接抛出，提示语取决于表达式的写法以及代码当时是解释执行还是已即时编译，
	 * 不属于行为；应用代码或类库给出的提示语(如参数不合法、数字格式错误)两侧必然都存在，会被严格比对。
	 */
	public static void assertSameError(Throwable expected, Throwable actual, String context) {
		boolean same;
		if (expected == null || actual == null) {
			same = (expected == actual);
		} else {
			same = expected.getClass() == actual.getClass();
			if (same && expected.getMessage() != null && actual.getMessage() != null && !isRaisedByJvm(expected)) {
				same = expected.getMessage().equals(actual.getMessage());
			}
		}
		if (!same) {
			throw new AssertionFailedError(context + ": error differs, oracle=" + errorSignature(expected)
					+ ", current=" + errorSignature(actual));
		}
	}

	/**
	 * 是否为 JVM 直接抛出、提示语不稳定的异常
	 */
	private static boolean isRaisedByJvm(Throwable e) {
		return (e instanceof NullPointerException) || (e instanceof ArrayIndexOutOfBoundsException)
				|| (e instanceof ClassCastException);
	}

	/**
	 * 已登记的有意差异(缺陷修复)：原实现遇到不合法的树形数据或旋转配置时，抛出没有说明的数组越界或空指针
	 * (如取第一个根节点、取第一个类别、探测类别维度)，现改为抛出带说明的 IllegalArgumentException(消息以 invalid 开头)。
	 * 只有"旧异常是无说明的越界/空指针 + 新异常是带说明的 IllegalArgumentException"这一种组合才被认可，
	 * 其余任何差异仍然照常判为不一致。
	 */
	public static boolean isExplainedDivergence(Throwable legacy, Throwable current) {
		if (!(current instanceof IllegalArgumentException)) {
			return false;
		}
		String message = current.getMessage();
		if (message == null || !message.startsWith("invalid ")) {
			return false;
		}
		return (legacy instanceof NullPointerException) || (legacy instanceof IndexOutOfBoundsException);
	}
}
