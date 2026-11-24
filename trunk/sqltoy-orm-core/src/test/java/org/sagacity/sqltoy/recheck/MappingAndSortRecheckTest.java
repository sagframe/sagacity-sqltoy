package org.sagacity.sqltoy.recheck;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.utils.MapperUtils;
import org.sagacity.sqltoy.utils.ResultUtils;

/**
 * @project sagacity-sqltoy
 * @description 2026-11-24 后续两处改动的独立复核：MapperUtils 值可赋值给字段类型时直接赋值(含接口字段遍历的 null 守卫)、
 *              ResultUtils.sortList 逐对判型以及一对多分组键改为逐列文本。
 * @author zhongxuchen
 */
@SuppressWarnings({ "rawtypes", "unchecked", "deprecation" })
public class MappingAndSortRecheckTest {

	// ======================= MapperUtils =======================

	public static class Tag implements Serializable {
		private static final long serialVersionUID = 1L;
		private String label;

		public Tag() {
		}

		public Tag(String label) {
			this.label = label;
		}

		public String getLabel() {
			return label;
		}

		public void setLabel(String label) {
			this.label = label;
		}
	}

	public static class ItemA implements Serializable {
		private static final long serialVersionUID = 1L;
		private String title;

		public String getTitle() {
			return title;
		}

		public void setTitle(String title) {
			this.title = title;
		}
	}

	/** 与 ItemA 属性名相同但不是同一个类(也没有继承关系) */
	public static class ItemB implements Serializable {
		private static final long serialVersionUID = 1L;
		private String title;

		public String getTitle() {
			return title;
		}

		public void setTitle(String title) {
			this.title = title;
		}
	}

	public static class WideSource implements Serializable {
		private static final long serialVersionUID = 1L;
		private Object any;
		private Number num;
		private Map map;
		private List rawList;
		private Set<Tag> tagSet;
		private Tag tag;
		private List<ItemA> items;

		public Object getAny() {
			return any;
		}

		public void setAny(Object any) {
			this.any = any;
		}

		public Number getNum() {
			return num;
		}

		public void setNum(Number num) {
			this.num = num;
		}

		public Map getMap() {
			return map;
		}

		public void setMap(Map map) {
			this.map = map;
		}

		public List getRawList() {
			return rawList;
		}

		public void setRawList(List rawList) {
			this.rawList = rawList;
		}

		public Set<Tag> getTagSet() {
			return tagSet;
		}

		public void setTagSet(Set<Tag> tagSet) {
			this.tagSet = tagSet;
		}

		public Tag getTag() {
			return tag;
		}

		public void setTag(Tag tag) {
			this.tag = tag;
		}

		public List<ItemA> getItems() {
			return items;
		}

		public void setItems(List<ItemA> items) {
			this.items = items;
		}
	}

	public static class WideTarget implements Serializable {
		private static final long serialVersionUID = 1L;
		private Object any;
		private Number num;
		private Map map;
		private List rawList;
		private Set<Tag> tagSet;
		private Tag tag;
		private List<ItemB> items;

		public Object getAny() {
			return any;
		}

		public void setAny(Object any) {
			this.any = any;
		}

		public Number getNum() {
			return num;
		}

		public void setNum(Number num) {
			this.num = num;
		}

		public Map getMap() {
			return map;
		}

		public void setMap(Map map) {
			this.map = map;
		}

		public List getRawList() {
			return rawList;
		}

		public void setRawList(List rawList) {
			this.rawList = rawList;
		}

		public Set<Tag> getTagSet() {
			return tagSet;
		}

		public void setTagSet(Set<Tag> tagSet) {
			this.tagSet = tagSet;
		}

		public Tag getTag() {
			return tag;
		}

		public void setTag(Tag tag) {
			this.tag = tag;
		}

		public List<ItemB> getItems() {
			return items;
		}

		public void setItems(List<ItemB> items) {
			this.items = items;
		}
	}

	private static WideSource wideSource() {
		WideSource source = new WideSource();
		source.setAny("hello");
		source.setNum(Integer.valueOf(42));
		Map<String, Integer> map = new HashMap<String, Integer>();
		map.put("a", 1);
		source.setMap(map);
		source.setRawList(new ArrayList<Object>(Arrays.asList((Object) 1, 2)));
		source.setTagSet(new HashSet<Tag>(Arrays.asList(new Tag("x"), new Tag("y"))));
		source.setTag(new Tag("shared"));
		ItemA first = new ItemA();
		first.setTitle("t1");
		ItemA second = new ItemA();
		second.setTitle("t2");
		source.setItems(new ArrayList<ItemA>(Arrays.asList(first, second)));
		return source;
	}

	@Test
	public void assignableValuesAreKeptAndDifferentDtoTypesAreStillConverted() {
		WideSource source = wideSource();
		// 第 1 次调用计算 setter 元数据，第 2 次命中缓存
		for (int round = 0; round < 3; round++) {
			WideTarget target = MapperUtils.map(source, WideTarget.class);
			// 值已经是字段类型(或其子类/实现)：直接赋值，原先这些值会被静默丢弃或抛异常
			assertEquals("hello", target.getAny(), "Object <- String");
			assertEquals(Integer.valueOf(42), target.getNum(), "Number <- Integer");
			assertSame(source.getMap(), target.getMap(), "Map <- HashMap");
			assertEquals(Integer.valueOf(1), target.getMap().get("a"));
			assertSame(source.getRawList(), target.getRawList(), "raw List <- ArrayList");
			// 接口类型的字段：值不是 List，原先会走到递归映射并因接口没有父类而空指针
			assertSame(source.getTagSet(), target.getTagSet(), "Set<Tag> <- HashSet");
			assertEquals(2, target.getTagSet().size());
			// 属性声明的类型两边相同：仍是递归映射出的副本(与修改之前一致，目标对象不与源对象共用嵌套对象)
			assertNotSame(source.getTag(), target.getTag(), "同类型嵌套对象应为副本");
			assertEquals("shared", target.getTag().getLabel());
			// 两边是不同的类：仍然要转换成目标元素类型
			assertNotSame(source.getItems(), target.getItems());
			assertEquals(2, target.getItems().size());
			for (Object item : target.getItems()) {
				assertTrue(item instanceof ItemB, "元素类型应为 ItemB，实际 " + item.getClass().getName());
			}
			assertEquals("t1", target.getItems().get(0).getTitle());
			assertEquals("t2", target.getItems().get(1).getTitle());
		}
	}

	@Test
	public void copyPropertiesAssignsSubclassValues() {
		WideSource source = wideSource();
		WideTarget target = new WideTarget();
		// copyProperties 只支持基本类型和类型相同的属性：原先 Map/List 字段因为实际类型与声明类型不同而不赋值
		List<WideSource> sources = new ArrayList<WideSource>();
		sources.add(source);
		List<WideTarget> targets = new ArrayList<WideTarget>();
		targets.add(target);
		MapperUtils.copyProperties(sources, targets);
		assertEquals("hello", target.getAny());
		assertEquals(Integer.valueOf(42), target.getNum());
		assertSame(source.getMap(), target.getMap());
		assertSame(source.getRawList(), target.getRawList());
		assertSame(source.getTag(), target.getTag());
		// copyProperties 不看泛型：按擦除后的类型判断可赋值，List<ItemA> 会原样赋给 List<ItemB> 字段
		// (原先因为实际类型 ArrayList 与声明的 List 类型名不同而不赋值；元素类型是否一致要由调用方保证)
		assertSame(source.getItems(), target.getItems());
		assertTrue((Object) target.getItems().get(0) instanceof ItemA, "元素仍是源对象的类型");
	}

	// ======================= 值的类型与字段类型相同 =======================

	public static class Node implements Serializable {
		private static final long serialVersionUID = 1L;
		private String name;
		private Node next;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public Node getNext() {
			return next;
		}

		public void setNext(Node next) {
			this.next = next;
		}
	}

	/** 不可变对象：只有带参构造，没有无参构造 */
	public static final class ImmutableValue implements Serializable {
		private static final long serialVersionUID = 1L;
		private final int x;

		public ImmutableValue(int x) {
			this.x = x;
		}

		public int getX() {
			return x;
		}
	}

	/** 非静态内部类：构造器带有外部类参数，没有无参构造 */
	public class InnerValue implements Serializable {
		private static final long serialVersionUID = 1L;
		private String name;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}
	}

	/** 有无参构造、有 getter、没有 setter */
	public static class ReadOnlyValue implements Serializable {
		private static final long serialVersionUID = 1L;
		private final String name = "ro";

		public String getName() {
			return name;
		}
	}

	public static class SameSource implements Serializable {
		private static final long serialVersionUID = 1L;
		private Node node;
		private ImmutableValue immutable;
		private InnerValue inner;
		private ArrayList rawList;
		private HashSet<String> stringSet;
		private Locale locale;
		private LinkedHashMap<String, Integer> linkedMap;
		private StringBuffer buffer;
		private ReadOnlyValue readOnly;

		public Node getNode() {
			return node;
		}

		public void setNode(Node node) {
			this.node = node;
		}

		public ImmutableValue getImmutable() {
			return immutable;
		}

		public void setImmutable(ImmutableValue immutable) {
			this.immutable = immutable;
		}

		public InnerValue getInner() {
			return inner;
		}

		public void setInner(InnerValue inner) {
			this.inner = inner;
		}

		public ArrayList getRawList() {
			return rawList;
		}

		public void setRawList(ArrayList rawList) {
			this.rawList = rawList;
		}

		public HashSet<String> getStringSet() {
			return stringSet;
		}

		public void setStringSet(HashSet<String> stringSet) {
			this.stringSet = stringSet;
		}

		public Locale getLocale() {
			return locale;
		}

		public void setLocale(Locale locale) {
			this.locale = locale;
		}

		public LinkedHashMap<String, Integer> getLinkedMap() {
			return linkedMap;
		}

		public void setLinkedMap(LinkedHashMap<String, Integer> linkedMap) {
			this.linkedMap = linkedMap;
		}

		public StringBuffer getBuffer() {
			return buffer;
		}

		public void setBuffer(StringBuffer buffer) {
			this.buffer = buffer;
		}

		public ReadOnlyValue getReadOnly() {
			return readOnly;
		}

		public void setReadOnly(ReadOnlyValue readOnly) {
			this.readOnly = readOnly;
		}
	}

	/** 与 SameSource 属性一一对应、类型相同的另一个类 */
	public static class SameTarget implements Serializable {
		private static final long serialVersionUID = 1L;
		private Node node;
		private ImmutableValue immutable;
		private InnerValue inner;
		private ArrayList rawList;
		private HashSet<String> stringSet;
		private Locale locale;
		private LinkedHashMap<String, Integer> linkedMap;
		private StringBuffer buffer;
		private ReadOnlyValue readOnly;

		public Node getNode() {
			return node;
		}

		public void setNode(Node node) {
			this.node = node;
		}

		public ImmutableValue getImmutable() {
			return immutable;
		}

		public void setImmutable(ImmutableValue immutable) {
			this.immutable = immutable;
		}

		public InnerValue getInner() {
			return inner;
		}

		public void setInner(InnerValue inner) {
			this.inner = inner;
		}

		public ArrayList getRawList() {
			return rawList;
		}

		public void setRawList(ArrayList rawList) {
			this.rawList = rawList;
		}

		public HashSet<String> getStringSet() {
			return stringSet;
		}

		public void setStringSet(HashSet<String> stringSet) {
			this.stringSet = stringSet;
		}

		public Locale getLocale() {
			return locale;
		}

		public void setLocale(Locale locale) {
			this.locale = locale;
		}

		public LinkedHashMap<String, Integer> getLinkedMap() {
			return linkedMap;
		}

		public void setLinkedMap(LinkedHashMap<String, Integer> linkedMap) {
			this.linkedMap = linkedMap;
		}

		public StringBuffer getBuffer() {
			return buffer;
		}

		public void setBuffer(StringBuffer buffer) {
			this.buffer = buffer;
		}

		public ReadOnlyValue getReadOnly() {
			return readOnly;
		}

		public void setReadOnly(ReadOnlyValue readOnly) {
			this.readOnly = readOnly;
		}
	}

	@Test
	public void sameTypeBeanIsCopiedWhileValuesThatCannotBeCopiedAreAssignedDirectly() {
		SameSource source = new SameSource();
		// 四层嵌套的同类型对象
		Node level4 = new Node();
		level4.setName("n4");
		Node level3 = new Node();
		level3.setName("n3");
		level3.setNext(level4);
		Node level2 = new Node();
		level2.setName("n2");
		level2.setNext(level3);
		Node level1 = new Node();
		level1.setName("n1");
		level1.setNext(level2);
		source.setNode(level1);
		ImmutableValue immutable = new ImmutableValue(7);
		source.setImmutable(immutable);
		InnerValue inner = new InnerValue();
		inner.setName("in");
		source.setInner(inner);
		ArrayList<String> rawList = new ArrayList<String>(Arrays.asList("a", "b"));
		source.setRawList(rawList);
		HashSet<String> stringSet = new HashSet<String>(Arrays.asList("a", "b"));
		source.setStringSet(stringSet);
		source.setLocale(Locale.CHINA);
		LinkedHashMap<String, Integer> linkedMap = new LinkedHashMap<String, Integer>();
		linkedMap.put("k", 9);
		source.setLinkedMap(linkedMap);
		StringBuffer buffer = new StringBuffer("sb");
		source.setBuffer(buffer);
		ReadOnlyValue readOnly = new ReadOnlyValue();
		source.setReadOnly(readOnly);
		// 第 1 次调用计算映射关系，之后各次走缓存
		for (int round = 0; round < 3; round++) {
			String context = "round=" + round;
			SameTarget target = MapperUtils.map(source, SameTarget.class);
			// 同类型、可以递归映射的 bean：得到副本(与修改之前一致)，最多拷贝 3 层
			assertNotSame(level1, target.getNode(), context + ": 第1层应为副本");
			assertEquals("n1", target.getNode().getName());
			assertNotSame(level2, target.getNode().getNext(), context + ": 第2层应为副本");
			assertNotSame(level3, target.getNode().getNext().getNext(), context + ": 第3层应为副本");
			// 第 4 层已到递归上限，与修改之前一样直接引用
			assertSame(level4, target.getNode().getNext().getNext().getNext(), context + ": 第4层");
			// 修改目标对象的嵌套属性不影响源对象
			target.getNode().setName("changed");
			assertEquals("n1", level1.getName(), context + ": 源对象不应被修改");
			target.getNode().setName("n1");
			// 同类型但递归映射不出副本的值：直接赋值(修改之前这些值被丢弃，非静态内部类还会抛异常)
			assertSame(immutable, target.getImmutable(), context + ": 不可变对象");
			assertSame(inner, target.getInner(), context + ": 非静态内部类");
			assertSame(rawList, target.getRawList(), context + ": 不带泛型的 ArrayList");
			assertSame(stringSet, target.getStringSet(), context + ": HashSet");
			assertSame(source.getLocale(), target.getLocale(), context + ": Locale");
			assertSame(linkedMap, target.getLinkedMap(), context + ": LinkedHashMap");
			assertSame(buffer, target.getBuffer(), context + ": StringBuffer");
			assertSame(readOnly, target.getReadOnly(), context + ": 只有 getter 的类");
		}
	}

	// ======================= ResultUtils.sortList =======================

	/** 修改之前的 sortList：由第一对非 null 取值确定类型，之后无条件按该类型强转比较 */
	private static void legacySortList(List<List<Object>> sortList, int orderCol, int start, int end, boolean ascend) {
		if (end <= start) {
			return;
		}
		Object iData;
		Object jData;
		boolean lessThen = false;
		String str1;
		String str2;
		int dataType = 1;
		boolean finishedJudgeType = false;
		for (int i = start; i < end; i++) {
			for (int j = i + 1; j < end + 1; j++) {
				iData = sortList.get(i).get(orderCol);
				jData = sortList.get(j).get(orderCol);
				if ((iData == null && jData == null) || (iData != null && jData == null)) {
					lessThen = false;
				} else if (iData == null && jData != null) {
					lessThen = true;
				} else {
					if (!finishedJudgeType) {
						if (iData instanceof Number) {
							dataType = 2;
						} else if (iData instanceof Date) {
							dataType = 3;
						} else if (iData instanceof LocalDate) {
							dataType = 4;
						} else if (iData instanceof LocalDateTime) {
							dataType = 5;
						} else if (iData instanceof LocalTime) {
							dataType = 6;
						}
						finishedJudgeType = true;
					}
					if (dataType == 1) {
						str1 = iData.toString();
						str2 = jData.toString();
						if (str1.length() < str2.length()) {
							lessThen = true;
						} else if (str1.length() > str2.length()) {
							lessThen = false;
						} else {
							lessThen = str1.compareTo(str2) < 0;
						}
					} else if (dataType == 2) {
						lessThen = ((Number) iData).doubleValue() < ((Number) jData).doubleValue();
					} else if (dataType == 3) {
						lessThen = ((Date) iData).before((Date) jData);
					} else if (dataType == 4) {
						lessThen = ((LocalDate) iData).compareTo((LocalDate) jData) < 0;
					} else if (dataType == 5) {
						lessThen = ((LocalDateTime) iData).compareTo((LocalDateTime) jData) < 0;
					} else if (dataType == 6) {
						lessThen = ((LocalTime) iData).compareTo((LocalTime) jData) < 0;
					}
				}
				if ((ascend && !lessThen) || (!ascend && lessThen)) {
					List tempList = sortList.get(i);
					sortList.set(i, sortList.get(j));
					sortList.set(j, tempList);
				}
			}
		}
	}

	private static Method sortListMethod() throws Exception {
		Method method = ResultUtils.class.getDeclaredMethod("sortList", List.class, int.class, int.class, int.class,
				boolean.class);
		method.setAccessible(true);
		return method;
	}

	/** 单一类型的排序列取值(含 null) */
	private static Object sortValue(int type, Random rnd) {
		if (rnd.nextInt(6) == 0) {
			return null;
		}
		int value = rnd.nextInt(25);
		switch (type) {
		case 1:
			return Long.valueOf(value);
		case 2:
			return Double.valueOf(value / 8.0d);
		case 3:
			return BigDecimal.valueOf(value, rnd.nextInt(3));
		case 4:
			return String.valueOf((char) ('a' + value % 5)) + (value % 7);
		case 5:
			return new Date(value * 3600_000L);
		case 6:
			return new java.sql.Timestamp(value * 3600_000L + 123456789L);
		case 7:
			return LocalDate.of(2026, 1 + value % 12, 1 + value % 28);
		case 8:
			return LocalDateTime.of(2026, 1 + value % 12, 1 + value % 28, value % 24, value % 60);
		case 9:
			return LocalTime.of(value % 24, value % 60, value % 60);
		default:
			return Integer.valueOf(value);
		}
	}

	@Test
	public void sortListUnchangedForSingleTypedColumns() throws Exception {
		Method sortList = sortListMethod();
		int compared = 0;
		for (int seed = 0; seed < 3000; seed++) {
			Random rnd = new Random(seed * 2654435761L + 29);
			int type = rnd.nextInt(10);
			int size = 2 + rnd.nextInt(22);
			List<List<Object>> current = new ArrayList<List<Object>>();
			for (int i = 0; i < size; i++) {
				current.add(new ArrayList<Object>(Arrays.asList((Object) ("r" + i), sortValue(type, rnd))));
			}
			int start = rnd.nextInt(size);
			int end = start + rnd.nextInt(size - start);
			boolean ascend = rnd.nextBoolean();
			List<List<Object>> legacy = new ArrayList<List<Object>>();
			for (List<Object> row : current) {
				legacy.add(new ArrayList<Object>(row));
			}
			legacySortList(legacy, 1, start, end, ascend);
			sortList.invoke(null, current, 1, start, end, ascend);
			List<String> expectedOrder = new ArrayList<String>();
			List<String> actualOrder = new ArrayList<String>();
			for (List<Object> row : legacy) {
				expectedOrder.add((String) row.get(0));
			}
			for (List<Object> row : current) {
				actualOrder.add((String) row.get(0));
			}
			assertEquals(expectedOrder, actualOrder,
					"seed=" + seed + ", type=" + type + ", start=" + start + ", end=" + end + ", ascend=" + ascend);
			compared++;
		}
		assertEquals(3000, compared);
	}

	@Test
	public void sortListToleratesMixedTypeColumn() throws Exception {
		Method sortList = sortListMethod();
		// 数字与数字文本混在一列：原实现由首个取值定型后无条件强转，会抛 ClassCastException
		List<List<Object>> rows = new ArrayList<List<Object>>();
		rows.add(new ArrayList<Object>(Arrays.asList((Object) "a", Integer.valueOf(10))));
		rows.add(new ArrayList<Object>(Arrays.asList((Object) "b", "9")));
		rows.add(new ArrayList<Object>(Arrays.asList((Object) "c", Integer.valueOf(2))));
		rows.add(new ArrayList<Object>(Arrays.asList((Object) "d", "100")));
		sortList.invoke(null, rows, 1, 0, rows.size() - 1, true);
		assertEquals(4, rows.size());
		// 两对数字之间按数值比较：2 排在 10 之前
		List<String> order = new ArrayList<String>();
		for (List<Object> row : rows) {
			order.add((String) row.get(0));
		}
		assertTrue(order.indexOf("c") < order.indexOf("a"), "顺序=" + order);
		// 原实现在同样的数据上抛类型转换异常
		Throwable legacyError = null;
		try {
			List<List<Object>> legacy = new ArrayList<List<Object>>();
			for (List<Object> row : rows) {
				legacy.add(new ArrayList<Object>(row));
			}
			legacySortList(legacy, 1, 0, legacy.size() - 1, true);
		} catch (Throwable e) {
			legacyError = e;
		}
		assertTrue(legacyError instanceof ClassCastException, "原实现: " + legacyError);
	}

	// ======================= ResultUtils.hashGroupList =======================

	@Test
	public void oneToManyGroupsAreSeparatedByWholeColumnValues() throws Exception {
		Method hashGroupList = ResultUtils.class.getDeclaredMethod("hashGroupList", List.class, int[].class);
		hashGroupList.setAccessible(true);
		// (a,b , c) 与 (a , b,c) 原先都拼成 ",a,b,c"，被当成同一组
		List<List<Object>> rows = new ArrayList<List<Object>>();
		rows.add(new ArrayList<Object>(Arrays.asList((Object) "a,b", "c", 1)));
		rows.add(new ArrayList<Object>(Arrays.asList((Object) "a", "b,c", 2)));
		rows.add(new ArrayList<Object>(Arrays.asList((Object) null, "x", 3)));
		rows.add(new ArrayList<Object>(Arrays.asList((Object) "a,b", "c", 4)));
		LinkedHashMap<List<String>, List> groups = (LinkedHashMap<List<String>, List>) hashGroupList.invoke(null,
				rows, new int[] { 0, 1 });
		assertEquals(3, groups.size(), "分组数");
		List<List<String>> keys = new ArrayList<List<String>>(groups.keySet());
		assertEquals(Arrays.asList("a,b", "c"), keys.get(0), "保持首次出现的顺序");
		assertEquals(Arrays.asList("a", "b,c"), keys.get(1));
		assertEquals(Arrays.asList("null", "x"), keys.get(2), "null 仍按文本 null");
		assertEquals(Arrays.asList(1, 4), valuesOf(groups.get(keys.get(0))));
		assertEquals(Arrays.asList(2), valuesOf(groups.get(keys.get(1))));
		assertEquals(Arrays.asList(3), valuesOf(groups.get(keys.get(2))));
	}

	private static List<Integer> valuesOf(List rows) {
		List<Integer> values = new ArrayList<Integer>();
		for (Object row : rows) {
			values.add((Integer) ((List) row).get(2));
		}
		return values;
	}
}
