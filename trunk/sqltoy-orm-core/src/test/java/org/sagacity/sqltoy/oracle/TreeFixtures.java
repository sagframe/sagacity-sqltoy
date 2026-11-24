package org.sagacity.sqltoy.oracle;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

/**
 * @project sagacity-sqltoy
 * @description 树形对拍测试的数据生成器：按种子确定性地生成树(形态、id/pid 取值类型、根 pid 写法、孤儿/重复 id/自环/成环/null id、
 *              行顺序)并随机打乱列的位置。只负责造数据，不依赖任何一方的实现。
 * @author zhongxuchen
 */
public class TreeFixtures {
	/** 列的角色 */
	public static final String COL_ID = "id";
	public static final String COL_PID = "pid";
	public static final String COL_AMT_PREFIX = "amt";
	public static final String COL_ORDER = "ord";
	public static final String COL_FILTER = "flt";

	/** id 与 pid 的取值类型组合 */
	public enum IdStyle {
		/** id、pid 同类型 */
		SAME,
		/** id、pid 类型不同，equals 永远不成立(如 Long 对 Integer) */
		CROSS,
		/** id 本身混杂多种类型，只有类型恰好相同的才能挂接 */
		MIXED
	}

	/** 根节点的 pid 写法 */
	public enum RootPidStyle {
		ZERO, NULL, ABSENT
	}

	/**
	 * 自定义类型的 id 取值：equals/hashCode/toString 均按数值 id 实现，但不属于常规取值类型，
	 * 用来覆盖"不能建立索引、退回逐个比对"的路径
	 */
	public static final class TreeKey {
		private final long id;

		public TreeKey(long id) {
			this.id = id;
		}

		@Override
		public boolean equals(Object other) {
			return (other instanceof TreeKey) && ((TreeKey) other).id == id;
		}

		@Override
		public int hashCode() {
			return Long.valueOf(id).hashCode();
		}

		@Override
		public String toString() {
			return "key" + id;
		}
	}

	/** 逻辑节点：id、pid 为数值，落地时再按取值类型转换 */
	private static final class Node {
		long id;
		long pid;
		boolean useRootPid;
		boolean nullPid;
		boolean nullId;
	}

	/** 生成出来的一份树形数据 */
	public static final class TreeData {
		public List<List<Object>> rows = new ArrayList<>();
		/** 列角色到列下标的对照(角色名即 LabelIndexModel 的 key) */
		public Map<String, Integer> columns = new TreeMap<>();
		public int width;
		public int idCol;
		public int pidCol;
		public int[] sumCols = new int[0];
		public int orderCol = -1;
		public int filterCol = -1;
		public IdStyle idStyle;
		public RootPidStyle rootPidStyle;
		public boolean hasNullId;
		/** 根节点 pid 的实际取值(与 pid 同类型) */
		public Object rootPidValue;
	}

	private TreeFixtures() {
	}

	// ---------- 取值类型 ----------
	private static final int T_LONG = 0;
	private static final int T_INTEGER = 1;
	private static final int T_STRING = 2;
	private static final int T_BIGDECIMAL = 3;
	private static final int T_OBJECT = 4;
	private static final int TYPE_COUNT = 5;

	private static Object idValue(int type, long id) {
		switch (type) {
		case T_INTEGER:
			return Integer.valueOf((int) id);
		case T_STRING:
			return "n" + id;
		case T_BIGDECIMAL:
			return BigDecimal.valueOf(id);
		case T_OBJECT:
			return new TreeKey(id);
		default:
			return Long.valueOf(id);
		}
	}

	// ---------- 其它列的取值 ----------
	private static Object amountValue(Random rnd) {
		int pick = rnd.nextInt(100);
		if (pick < 12) {
			return null;
		} else if (pick < 34) {
			return BigDecimal.valueOf(rnd.nextInt(100000), rnd.nextInt(3));
		} else if (pick < 50) {
			return Long.valueOf(rnd.nextInt(10000));
		} else if (pick < 64) {
			return Integer.valueOf(rnd.nextInt(1000));
		} else if (pick < 74) {
			return Double.valueOf(rnd.nextInt(500) / 8.0d);
		} else if (pick < 84) {
			return String.valueOf(rnd.nextInt(10000));
		} else if (pick < 99) {
			return (rnd.nextInt(10) == 0 ? "-" : "") + (1000 + rnd.nextInt(9000)) + "," + (100 + rnd.nextInt(900));
		}
		// 非数字文本(仅 1%)：原实现会抛 NumberFormatException
		return rnd.nextBoolean() ? "" : (rnd.nextInt(100) + "%");
	}

	private static Object orderValue(Random rnd) {
		switch (rnd.nextInt(5)) {
		case 0:
			return null;
		case 1:
			return Long.valueOf(rnd.nextInt(6));
		case 2:
			return String.valueOf(rnd.nextInt(6));
		case 3:
			return BigDecimal.valueOf(rnd.nextInt(6));
		default:
			return Integer.valueOf(rnd.nextInt(6));
		}
	}

	// ---------- 逻辑节点 ----------
	private static long[] parents(String shape, int n, Random rnd) {
		long[] parent = new long[n + 1];
		int fanout = 2 + rnd.nextInt(5);
		int roots = 1 + rnd.nextInt(3);
		for (int i = 2; i <= n; i++) {
			switch (shape) {
			case "chain":
				parent[i] = i - 1;
				break;
			case "star":
				parent[i] = 1;
				break;
			case "balanced":
				parent[i] = (i - 2) / fanout + 1;
				break;
			case "forest":
				parent[i] = (i <= roots) ? 0 : 1 + rnd.nextInt(i - 1);
				break;
			default:
				parent[i] = 1 + rnd.nextInt(i - 1);
				break;
			}
		}
		return parent;
	}

	private static Node node(long id, long pid, boolean rootPid) {
		Node node = new Node();
		node.id = id;
		node.pid = pid;
		node.useRootPid = rootPid;
		return node;
	}

	/** 主干节点的前序序列(每个节点紧跟其子孙，兄弟按 id 升序) */
	private static List<Integer> preOrder(long[] parent, int n) {
		Map<Long, List<Integer>> kids = new TreeMap<>();
		List<Integer> top = new ArrayList<>();
		for (int i = 1; i <= n; i++) {
			if (parent[i] == 0) {
				top.add(i);
			} else {
				kids.computeIfAbsent(parent[i], k -> new ArrayList<>()).add(i);
			}
		}
		List<Integer> order = new ArrayList<>(n);
		Deque<Integer> stack = new ArrayDeque<>();
		for (int i = top.size() - 1; i >= 0; i--) {
			stack.push(top.get(i));
		}
		while (!stack.isEmpty()) {
			Integer current = stack.pop();
			order.add(current);
			List<Integer> children = kids.get(current.longValue());
			if (children != null) {
				for (int i = children.size() - 1; i >= 0; i--) {
					stack.push(children.get(i));
				}
			}
		}
		return order;
	}

	/**
	 * 按种子生成一份树形数据
	 * 
	 * @param seed          种子，失败信息里会带回以便复现
	 * @param preOrderInput true 表示主干节点按前序排列(供只做汇总、不做排序的场景使用)
	 */
	public static TreeData generate(long seed, boolean preOrderInput) {
		return generate(seed, preOrderInput, false, true);
	}

	/**
	 * 按种子生成一份树形数据
	 * 
	 * @param seed          种子，失败信息里会带回以便复现
	 * @param preOrderInput true 表示主干节点按前序排列(供只做汇总、不做排序的场景使用)
	 * @param sameTypesOnly true 表示 id 与 pid 取值类型一致：新旧实现在这类数据上的匹配结果必然相同，
	 *                      用于与行为基准做差分对拍；类型不一致的数据另有专门的用例覆盖新语义
	 */
	public static TreeData generate(long seed, boolean preOrderInput, boolean sameTypesOnly) {
		return generate(seed, preOrderInput, sameTypesOnly, true);
	}

	/**
	 * 按种子生成一份树形数据
	 * 
	 * @param seed          种子，失败信息里会带回以便复现
	 * @param preOrderInput true 表示主干节点按前序排列(供只做汇总、不做排序的场景使用)
	 * @param sameTypesOnly true 表示 id 与 pid 取值类型一致：新旧实现在这类数据上的匹配结果必然相同
	 * @param allowNullId   是否允许生成 id 为 null 的行：原实现在逐层汇总时会因 id.equals(pid) 抛空指针，
	 *                      与行为基准对拍时应关闭；null id 的新行为由专门用例覆盖
	 */
	public static TreeData generate(long seed, boolean preOrderInput, boolean sameTypesOnly, boolean allowNullId) {
		Random rnd = new Random(seed);
		TreeData data = new TreeData();
		// ---- 形态与规模 ----
		String[] shapes = { "chain", "star", "balanced", "random", "forest", "single", "single", "empty" };
		String shape = shapes[rnd.nextInt(shapes.length)];
		int n;
		if (shape.equals("single")) {
			n = 1;
		} else if (shape.equals("empty")) {
			n = 0;
		} else {
			n = 2 + rnd.nextInt(26);
		}
		// ---- 取值类型与根 pid 写法 ----
		int idType = rnd.nextInt(TYPE_COUNT);
		data.idStyle = sameTypesOnly ? IdStyle.SAME
				: IdStyle.values()[rnd.nextInt(IdStyle.values().length)];
		int pidType = (data.idStyle == IdStyle.CROSS) ? (idType + 1 + rnd.nextInt(TYPE_COUNT - 1)) % TYPE_COUNT
				: idType;
		data.rootPidStyle = RootPidStyle.values()[rnd.nextInt(RootPidStyle.values().length)];
		long absentPid = 9_500_000L + rnd.nextInt(1000);
		data.rootPidValue = rootPid(data.rootPidStyle, absentPid, pidType);
		// ---- 逻辑节点 ----
		List<Node> nodes = new ArrayList<>();
		long[] parent = parents(shape, n, rnd);
		int nullIdNode = n == 0 ? 0 : 1 + rnd.nextInt(n);
		boolean withNullId = allowNullId && rnd.nextInt(5) == 0;
		for (int i = 1; i <= n; i++) {
			Node node = node(i, parent[i], parent[i] == 0);
			node.nullId = withNullId && i == nullIdNode;
			nodes.add(node);
		}
		int mainCount = nodes.size();
		// 重复 id
		for (int k = 0, count = (n > 2 && rnd.nextInt(4) == 0) ? 1 + rnd.nextInt(3) : 0; k < count; k++) {
			nodes.add(node(1 + rnd.nextInt(n), 1 + rnd.nextInt(n), false));
		}
		// 孤儿节点(父 id 不存在)及其子节点
		for (int k = 0, count = rnd.nextInt(3); k < count; k++) {
			long orphanId = 8_000_000L + k;
			nodes.add(node(orphanId, 9_000_000L + k, false));
			if (rnd.nextBoolean()) {
				nodes.add(node(8_100_000L + k, orphanId, false));
			}
		}
		// 成环
		if (rnd.nextInt(4) == 0) {
			nodes.add(node(7_000_001L, 7_000_003L, false));
			nodes.add(node(7_000_002L, 7_000_001L, false));
			nodes.add(node(7_000_003L, 7_000_002L, false));
		}
		// 自环
		if (rnd.nextBoolean()) {
			nodes.add(node(6_000_001L, 6_000_001L, false));
		}
		// pid 为 null 的行
		if (rnd.nextBoolean()) {
			Node extra = node(5_000_001L, 0, false);
			extra.nullPid = true;
			nodes.add(extra);
		}
		// ---- 列布局 ----
		List<String> roles = new ArrayList<>();
		roles.add(COL_ID);
		roles.add(COL_PID);
		int sumCount = rnd.nextInt(3);
		for (int i = 0; i < sumCount; i++) {
			roles.add(COL_AMT_PREFIX + i);
		}
		boolean hasOrder = rnd.nextInt(3) > 0;
		boolean hasFilter = rnd.nextInt(3) > 0;
		if (hasOrder) {
			roles.add(COL_ORDER);
		}
		if (hasFilter) {
			roles.add(COL_FILTER);
		}
		int extraCount = rnd.nextInt(3);
		for (int i = 0; i < extraCount; i++) {
			roles.add("ext" + i);
		}
		Collections.shuffle(roles, rnd);
		data.width = roles.size();
		for (int i = 0; i < data.width; i++) {
			data.columns.put(roles.get(i), i);
		}
		data.idCol = data.columns.get(COL_ID);
		data.pidCol = data.columns.get(COL_PID);
		data.sumCols = new int[sumCount];
		for (int i = 0; i < sumCount; i++) {
			data.sumCols[i] = data.columns.get(COL_AMT_PREFIX + i);
		}
		data.orderCol = hasOrder ? data.columns.get(COL_ORDER) : -1;
		data.filterCol = hasFilter ? data.columns.get(COL_FILTER) : -1;
		// ---- 落地为行 ----
		for (Node node : nodes) {
			List<Object> row = new ArrayList<>(data.width);
			for (int i = 0; i < data.width; i++) {
				row.add(null);
			}
			if (node.nullId) {
				data.hasNullId = true;
			} else {
				row.set(data.idCol,
						idValue(data.idStyle == IdStyle.MIXED ? rnd.nextInt(TYPE_COUNT) : idType, node.id));
			}
			Object pidValue;
			if (node.nullPid) {
				pidValue = null;
			} else if (node.useRootPid) {
				pidValue = data.rootPidValue;
			} else {
				pidValue = idValue(pidType, node.pid);
			}
			row.set(data.pidCol, pidValue);
			for (int i = 0; i < data.width; i++) {
				String role = roles.get(i);
				if (role.startsWith(COL_AMT_PREFIX)) {
					row.set(i, amountValue(rnd));
				} else if (role.equals(COL_ORDER)) {
					row.set(i, orderValue(rnd));
				} else if (role.equals(COL_FILTER)) {
					row.set(i, Integer.valueOf(rnd.nextInt(4)));
				} else if (!role.equals(COL_ID) && !role.equals(COL_PID)) {
					row.set(i, "x" + rnd.nextInt(1000));
				}
			}
			data.rows.add(row);
		}
		// ---- 行顺序 ----
		if (preOrderInput && n > 0) {
			List<Integer> order = preOrder(parent, n);
			boolean[] placed = new boolean[mainCount];
			for (Integer item : order) {
				placed[item - 1] = true;
			}
			List<List<Object>> ordered = new ArrayList<>(data.rows.size());
			for (Integer item : order) {
				ordered.add(data.rows.get(item - 1));
			}
			// 其余节点(主干中未进入前序的、以及非主干节点)随机插入
			for (int i = 0; i < data.rows.size(); i++) {
				if (i >= mainCount || !placed[i]) {
					ordered.add(rnd.nextInt(ordered.size() + 1), data.rows.get(i));
				}
			}
			data.rows = ordered;
		} else {
			Collections.shuffle(data.rows, rnd);
		}
		return data;
	}

	private static Object rootPid(RootPidStyle style, long absentPid, int pidType) {
		switch (style) {
		case NULL:
			return null;
		case ABSENT:
			return idValue(pidType, absentPid);
		default:
			return idValue(pidType, 0L);
		}
	}
}
