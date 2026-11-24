package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

/**
 * MapperUtils 类型映射的子类/实现类场景回归(LightDao.convertType 底层链路)。
 * 固化两处修复:
 * 1) 接口父类遍历null守卫——List/Map等接口字段不再因getSuperclass()=null抛NPE;
 * 2) 值已是字段类型的子类/实现(IS-A)时直接赋值——Object/Number/Map/List子类值不再静默丢失,具体父类字段保留子类实例。
 * 同时确认无关DTO映射、嵌套DTO映射、基本类型转换不受影响。
 */
public class MapperUtilsSubclassMappingTest {

	public static class Animal implements Serializable {
		private String name;
		public String getName() { return name; }
		public void setName(String name) { this.name = name; }
	}

	public static class Dog extends Animal {
		private String breed;
		public String getBreed() { return breed; }
		public void setBreed(String breed) { this.breed = breed; }
	}

	public static class Src implements Serializable {
		private Object obj;
		private Number num;
		private java.util.Date date;
		private Map map;
		private List list;
		private Animal pet;
		private List<String> typedList;
		public Object getObj() { return obj; }
		public void setObj(Object obj) { this.obj = obj; }
		public Number getNum() { return num; }
		public void setNum(Number num) { this.num = num; }
		public java.util.Date getDate() { return date; }
		public void setDate(java.util.Date date) { this.date = date; }
		public Map getMap() { return map; }
		public void setMap(Map map) { this.map = map; }
		public List getList() { return list; }
		public void setList(List list) { this.list = list; }
		public Animal getPet() { return pet; }
		public void setPet(Animal pet) { this.pet = pet; }
		public List<String> getTypedList() { return typedList; }
		public void setTypedList(List<String> typedList) { this.typedList = typedList; }
	}

	public static class Dst implements Serializable {
		private Object obj;
		private Number num;
		private java.util.Date date;
		private Map map;
		private List list;
		private Animal pet;
		private List<String> typedList;
		public Object getObj() { return obj; }
		public void setObj(Object obj) { this.obj = obj; }
		public Number getNum() { return num; }
		public void setNum(Number num) { this.num = num; }
		public java.util.Date getDate() { return date; }
		public void setDate(java.util.Date date) { this.date = date; }
		public Map getMap() { return map; }
		public void setMap(Map map) { this.map = map; }
		public List getList() { return list; }
		public void setList(List list) { this.list = list; }
		public Animal getPet() { return pet; }
		public void setPet(Animal pet) { this.pet = pet; }
		public List<String> getTypedList() { return typedList; }
		public void setTypedList(List<String> typedList) { this.typedList = typedList; }
	}

	private static Src fullSrc() {
		Src s = new Src();
		s.setObj("hello");
		s.setNum(Integer.valueOf(42));
		s.setDate(new Timestamp(1700000000000L));
		Map<String, Integer> m = new HashMap<>();
		m.put("a", 1);
		s.setMap(m);
		List<Integer> l = new ArrayList<>();
		l.add(1);
		l.add(2);
		s.setList(l);
		Dog dog = new Dog();
		dog.setName("Rex");
		dog.setBreed("Lab");
		s.setPet(dog);
		List<String> tl = new ArrayList<>();
		tl.add("x");
		tl.add("y");
		s.setTypedList(tl);
		return s;
	}

	/** 整对象映射: 含接口字段也不NPE, 各子类值都保留 */
	@Test
	public void wholeObjectMapKeepsSubclassValues() {
		Dst d = MapperUtils.map(fullSrc(), Dst.class);
		assertNotNull(d);
		assertEquals("hello", d.getObj(), "Object<-String");
		assertEquals(Integer.valueOf(42), d.getNum(), "Number<-Integer");
		assertEquals(new Timestamp(1700000000000L), d.getDate(), "Date<-Timestamp");
		assertNotNull(d.getMap(), "Map<-HashMap不应丢失");
		assertEquals(1, d.getMap().get("a"));
		assertNotNull(d.getList(), "List<-ArrayList不应丢失/NPE");
		assertEquals(2, d.getList().size());
		assertInstanceOf(Dog.class, d.getPet(), "Animal<-Dog保留子类实例");
		assertEquals("Rex", d.getPet().getName());
		assertEquals("Lab", ((Dog) d.getPet()).getBreed());
		assertEquals(2, d.getTypedList().size(), "List<String>保持");
	}

	/** 各种集合实现类 -> 接口字段 */
	@Test
	public void collectionImplsToInterfaceFields() {
		MSrc s = new MSrc();
		Map<String, String> hm = new HashMap<>();
		hm.put("k", "v");
		Map<String, String> tm = new TreeMap<>();
		tm.put("k", "v");
		List<Integer> al = new ArrayList<>();
		al.add(1);
		List<Integer> ll = new LinkedList<>();
		ll.add(2);
		s.setM1(hm);
		s.setM2(tm);
		s.setL1(al);
		s.setL2(ll);
		s.setO1("any");
		MDst d = MapperUtils.map(s, MDst.class);
		assertEquals(hm, d.getM1(), "Map<-HashMap");
		assertEquals(tm, d.getM2(), "Map<-TreeMap");
		assertEquals(al, d.getL1(), "List<-ArrayList");
		assertEquals(ll, d.getL2(), "List<-LinkedList");
		assertEquals("any", d.getO1(), "Object<-String");
	}

	/** copyProperties(listToList路径)同样保留子类值 */
	@Test
	public void copyPropertiesKeepsSubclassValues() {
		Dst d = new Dst();
		MapperUtils.copyProperties(fullSrc(), d);
		assertEquals("hello", d.getObj());
		assertEquals(Integer.valueOf(42), d.getNum());
		assertNotNull(d.getMap(), "copyProperties Map<-HashMap不应丢失");
		assertNotNull(d.getList(), "copyProperties List<-ArrayList不应丢失");
		assertInstanceOf(Dog.class, d.getPet());
	}

	/** 无关DTO(不可赋值)仍走递归映射复制公共属性 */
	@Test
	public void unrelatedDtoStillMaps() {
		Foo foo = new Foo();
		foo.setId("id-1");
		foo.setOnlyFoo("f");
		Bar bar = MapperUtils.map(foo, Bar.class);
		assertNotNull(bar);
		assertEquals("id-1", bar.getId(), "公共属性id应复制");
	}

	/** 嵌套DTO映射不受影响 */
	@Test
	public void nestedDtoStillMaps() {
		Child c = new Child();
		c.setName("child");
		ParentSrc ps = new ParentSrc();
		ps.setChild(c);
		ParentDst pd = MapperUtils.map(ps, ParentDst.class);
		assertNotNull(pd.getChild());
		assertEquals("child", pd.getChild().getName());
	}

	/** mapList集合映射含接口字段不NPE */
	@Test
	public void mapListWithInterfaceFields() {
		List<Src> list = new ArrayList<>();
		list.add(fullSrc());
		list.add(fullSrc());
		List<Dst> result = MapperUtils.mapList(list, Dst.class);
		assertEquals(2, result.size());
		for (Dst d : result) {
			assertNotNull(d.getMap());
			assertNotNull(d.getList());
			assertEquals("hello", d.getObj());
		}
	}

	public static class MSrc implements Serializable {
		private Map m1;
		private Map m2;
		private List l1;
		private List l2;
		private Object o1;
		public Map getM1() { return m1; }
		public void setM1(Map v) { m1 = v; }
		public Map getM2() { return m2; }
		public void setM2(Map v) { m2 = v; }
		public List getL1() { return l1; }
		public void setL1(List v) { l1 = v; }
		public List getL2() { return l2; }
		public void setL2(List v) { l2 = v; }
		public Object getO1() { return o1; }
		public void setO1(Object v) { o1 = v; }
	}

	public static class MDst implements Serializable {
		private Map m1;
		private Map m2;
		private List l1;
		private List l2;
		private Object o1;
		public Map getM1() { return m1; }
		public void setM1(Map v) { m1 = v; }
		public Map getM2() { return m2; }
		public void setM2(Map v) { m2 = v; }
		public List getL1() { return l1; }
		public void setL1(List v) { l1 = v; }
		public List getL2() { return l2; }
		public void setL2(List v) { l2 = v; }
		public Object getO1() { return o1; }
		public void setO1(Object v) { o1 = v; }
	}

	public static class Foo implements Serializable {
		private String id;
		private String onlyFoo;
		public String getId() { return id; }
		public void setId(String id) { this.id = id; }
		public String getOnlyFoo() { return onlyFoo; }
		public void setOnlyFoo(String onlyFoo) { this.onlyFoo = onlyFoo; }
	}

	public static class Bar implements Serializable {
		private String id;
		public String getId() { return id; }
		public void setId(String id) { this.id = id; }
	}

	public static class Child implements Serializable {
		private String name;
		public String getName() { return name; }
		public void setName(String name) { this.name = name; }
	}

	public static class ParentSrc implements Serializable {
		private Child child;
		public Child getChild() { return child; }
		public void setChild(Child child) { this.child = child; }
	}

	public static class ParentDst implements Serializable {
		private Child child;
		public Child getChild() { return child; }
		public void setChild(Child child) { this.child = child; }
	}
}