package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.lang.reflect.Method;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.DataType;

/**
 * BeanUtil 边缘场景测试
 */
public class BeanUtilEdgeTest {

	/** 含自定义取值属性的枚举 */
	enum Status {
		ENABLED, DISABLED;

		public String getValue() {
			return this == ENABLED ? "1" : "0";
		}
	}

	/** 无自定义属性的枚举 */
	enum Color {
		RED, BLUE
	}

	static class SampleBean implements java.io.Serializable {
		private static final long serialVersionUID = 1L;
		private String userName;
		private boolean active;
		private Boolean deleted;
		private Integer age;
		private List<String> tags;
		private Map<String, Object> attrs;
		private String[] names;

		// 链式set(返回this)
		public SampleBean setUserName(String userName) {
			this.userName = userName;
			return this;
		}

		public String getUserName() {
			return userName;
		}

		public boolean isActive() {
			return active;
		}

		// 2019-09-05修复的setIsXXX场景:属性isXXX对应setIsXXX
		public SampleBean setActive(boolean active) {
			this.active = active;
			return this;
		}

		public Boolean getDeleted() {
			return deleted;
		}

		public SampleBean setDeleted(Boolean deleted) {
			this.deleted = deleted;
			return this;
		}

		public Integer getAge() {
			return age;
		}

		public SampleBean setAge(Integer age) {
			this.age = age;
			return this;
		}

		public List<String> getTags() {
			return tags;
		}

		public SampleBean setTags(List<String> tags) {
			this.tags = tags;
			return this;
		}

		public Map<String, Object> getAttrs() {
			return attrs;
		}

		public SampleBean setAttrs(Map<String, Object> attrs) {
			this.attrs = attrs;
			return this;
		}

		public String[] getNames() {
			return names;
		}

		public SampleBean setNames(String[] names) {
			this.names = names;
			return this;
		}

		// 无对应属性的get方法
		public String getReadOnly() {
			return "ro";
		}
	}

	/** 数据库列名带下划线的场景 */
	static class UnderScoreBean {
		private String staffName;

		public String getStaffName() {
			return staffName;
		}

		public void setStaffName(String staffName) {
			this.staffName = staffName;
		}
	}

	record PointRecord(int x, String label) {
	}

	@Test
	public void isBaseDataType() {
		assertTrue(BeanUtil.isBaseDataType(String.class));
		assertTrue(BeanUtil.isBaseDataType(int.class));
		assertTrue(BeanUtil.isBaseDataType(BigDecimal.class));
		assertTrue(BeanUtil.isBaseDataType(Timestamp.class));
		assertTrue(BeanUtil.isBaseDataType(LocalDate.class));
		assertTrue(BeanUtil.isBaseDataType(LocalDateTime.class));
		assertFalse(BeanUtil.isBaseDataType(null));
		assertFalse(BeanUtil.isBaseDataType(SampleBean.class));
		assertFalse(BeanUtil.isBaseDataType(List.class));
	}

	@Test
	public void matchSetMethodsBasics() {
		Method[] methods = BeanUtil.matchSetMethods(SampleBean.class, "userName", "age", "tags", "notExist");
		assertEquals("setUserName", nullSafeMethodName(methods[0]));
		assertEquals("setAge", nullSafeMethodName(methods[1]));
		assertEquals("setTags", nullSafeMethodName(methods[2]));
		assertNull(methods[3], "不存在的属性应为null占位");
		// 链式set(返回this)也能匹配
		assertTrue(methods[0] != null);
	}

	@Test
	public void matchSetMethodsBooleanVariants() {
		// 原生boolean属性active对应setActive
		Method[] m1 = BeanUtil.matchSetMethods(SampleBean.class, "active");
		assertEquals("setActive", nullSafeMethodName(m1[0]));
		// 属性名写成isXXX时也应匹配到setActive(2019-09-05修复场景)
		Method[] m2 = BeanUtil.matchSetMethods(SampleBean.class, "isActive");
		assertEquals("setActive", nullSafeMethodName(m2[0]));
		// 包装Boolean属性
		Method[] m3 = BeanUtil.matchSetMethods(SampleBean.class, "deleted");
		assertEquals("setDeleted", nullSafeMethodName(m3[0]));
	}

	@Test
	public void matchSetMethodsUnderScore() {
		// 数据库列名STAFF_NAME与setter匹配
		Method[] m = BeanUtil.matchSetMethods(UnderScoreBean.class, "STAFF_NAME");
		assertEquals("setStaffName", nullSafeMethodName(m[0]));
	}

	@Test
	public void matchGetMethodsBasics() {
		Method[] m1 = BeanUtil.matchGetMethods(SampleBean.class, "userName", "active", "notExist");
		assertEquals("getUserName", nullSafeMethodName(m1[0]));
		// boolean属性匹配isActive
		assertEquals("isActive", nullSafeMethodName(m1[1]));
		assertNull(m1[2]);
		// 属性名写isXXX也能匹配isActive方法
		Method[] m2 = BeanUtil.matchGetMethods(SampleBean.class, "isActive");
		assertEquals("isActive", nullSafeMethodName(m2[0]));
		// 非boolean的is开头方法不误匹配isActive
		Method[] m3 = BeanUtil.matchGetMethods(SampleBean.class, "deleted");
		assertEquals("getDeleted", nullSafeMethodName(m3[0]));
	}

	@Test
	public void matchGetMethodsRecord() throws Exception {
		// Record类型支持
		Method[] m = BeanUtil.matchGetMethods(PointRecord.class, "x", "label", "notExist");
		assertEquals("x", m[0].getName());
		assertEquals("label", m[1].getName());
		assertNull(m[2]);
		PointRecord point = new PointRecord(3, "a");
		assertEquals(3, m[0].invoke(point));
		Integer[] types = BeanUtil.matchMethodsType(PointRecord.class, "x", "label");
		assertEquals(java.sql.Types.INTEGER, types[0]);
		assertEquals(java.sql.Types.VARCHAR, types[1]);
	}

	@Test
	public void matchMethodsType() {
		Integer[] types = BeanUtil.matchMethodsType(SampleBean.class, "userName", "age", "active", "notExist");
		assertEquals(java.sql.Types.VARCHAR, types[0]);
		assertEquals(java.sql.Types.INTEGER, types[1]);
		assertEquals(java.sql.Types.BOOLEAN, types[2]);
		assertEquals(java.sql.Types.NULL, types[3]);
		assertNull(BeanUtil.matchMethodsType(SampleBean.class));
	}

	@Test
	public void convertTypeNumbers() throws Exception {
		// 字符串转各数字类型
		assertEquals(Integer.valueOf(12), BeanUtil.convertType("12", 0, DataType.wrapIntegerType, "java.lang.Integer"));
		assertEquals(Long.valueOf(12), BeanUtil.convertType("12.0", 0, DataType.wrapLongType, "java.lang.Long"));
		assertEquals(Double.valueOf(12.5), BeanUtil.convertType("12.5", 0, DataType.wrapDoubleType, "java.lang.Double"));
		assertEquals(Float.valueOf(1.5f), BeanUtil.convertType("1.5", 0, DataType.wrapFloatType, "java.lang.Float"));
		assertEquals(Short.valueOf((short) 3),
				BeanUtil.convertType("3.6", 0, DataType.wrapShortType, "java.lang.Short"));
		// 空串转包装类型返回null,转原生类型返回默认值
		assertNull(BeanUtil.convertType("", 0, DataType.wrapIntegerType, "java.lang.Integer"));
		assertEquals(0, BeanUtil.convertType("", 0, DataType.primitiveIntType, "int"));
		// boolean字符串转数字
		assertEquals(Integer.valueOf(1), BeanUtil.convertType("true", 0, DataType.wrapIntegerType, "java.lang.Integer"));
		assertEquals(Long.valueOf(0), BeanUtil.convertType("false", 0, DataType.wrapLongType, "java.lang.Long"));
		// null值:原生类型默认值,包装类型null
		assertEquals(0, BeanUtil.convertType(null, 0, DataType.primitiveIntType, "int"));
		assertEquals(false, BeanUtil.convertType(null, 0, DataType.primitiveBooleanType, "boolean"));
		assertEquals(" ".charAt(0), BeanUtil.convertType(null, 0, DataType.primitiveCharType, "char"));
		assertNull(BeanUtil.convertType(null, 0, DataType.wrapIntegerType, "java.lang.Integer"));
		// BigDecimal与BigInteger
		assertEquals(new BigDecimal("1.20"),
				BeanUtil.convertType("1.20", 0, DataType.wrapBigDecimalType, "java.math.BigDecimal"));
		assertEquals(new java.math.BigInteger("99"),
				BeanUtil.convertType("99.5", 0, DataType.wrapBigIntegerType, "java.math.BigInteger"));
		// 类型一致直接返回原对象
		String same = "abc";
		assertSame(same, BeanUtil.convertType(same, 0, DataType.stringType, "java.lang.String"));
	}

	@Test
	public void convertTypeBooleanAndChar() throws Exception {
		assertEquals(Boolean.TRUE, BeanUtil.convertType("true", 0, DataType.wrapBooleanType, "java.lang.Boolean"));
		assertEquals(Boolean.TRUE, BeanUtil.convertType("TRUE", 0, DataType.wrapBooleanType, "java.lang.Boolean"));
		assertEquals(Boolean.TRUE, BeanUtil.convertType("1", 0, DataType.wrapBooleanType, "java.lang.Boolean"));
		assertEquals(Boolean.FALSE, BeanUtil.convertType("abc", 0, DataType.wrapBooleanType, "java.lang.Boolean"));
		assertEquals(Boolean.FALSE, BeanUtil.convertType("0", 0, DataType.wrapBooleanType, "java.lang.Boolean"));
		// 字符串转char取首字符
		assertEquals('a', BeanUtil.convertType("abc", 0, DataType.primitiveCharType, "char"));
		assertEquals(' ', BeanUtil.convertType("", 0, DataType.primitiveCharType, "char"));
		// 枚举转字符串:有getKey方法的枚举返回key值
		assertEquals("1", BeanUtil.convertType(Status.ENABLED, 0, DataType.stringType, "java.lang.String"));
	}

	/**
	 * 回归测试:枚举写读往返一致。Status是"无字段但有getValue()"的枚举，
	 * getEnumValue返回getValue值,newEnumInstance按key值或name均可回读。
	 */
	@Test
	public void enumRoundTrip() {
		Object written = BeanUtil.getEnumValue(Status.ENABLED);
		Object back = BeanUtil.newEnumInstance(written, Status.class);
		assertEquals(Status.ENABLED, back, "getEnumValue的输出应能被newEnumInstance回读");
	}

	/**
	 * 回归测试:newEnumInstance在按getKey值匹配不到时回退name匹配(javadoc"key值或name")
	 */
	@Test
	public void newEnumInstanceNameFallback() {
		assertEquals(Status.DISABLED, BeanUtil.newEnumInstance("DISABLED", Status.class));
	}

	@Test
	public void convertTypeStringFromDate() throws Exception {
		// LocalDateTime→String格式化为yyyy-MM-dd HH:mm:ss(与Date分支一致)
		LocalDateTime dt = LocalDateTime.of(2024, 5, 1, 10, 30, 0);
		assertEquals("2024-05-01 10:30:00", BeanUtil.convertType(dt, 0, DataType.stringType, "java.lang.String"));
		// LocalDate有专门的格式化分支
		LocalDate d = LocalDate.of(2024, 5, 1);
		assertEquals("2024-05-01", BeanUtil.convertType(d, 0, DataType.stringType, "java.lang.String"));
		// java.util.Date有专门的格式化分支
		assertEquals("2024-05-01 10:30:00",
				BeanUtil.convertType(java.sql.Timestamp.valueOf("2024-05-01 10:30:00"), 0, DataType.stringType,
						"java.lang.String"));
	}

	/**
	 * 回归测试:convertBoolean忽略大小写,大写TRUE转数字不再抛NumberFormatException
	 */
	@Test
	public void convertTypeUpperTrueToInteger() throws Exception {
		assertEquals(Integer.valueOf(1), BeanUtil.convertType("TRUE", 0, DataType.wrapIntegerType, "java.lang.Integer"));
		assertEquals(Integer.valueOf(0), BeanUtil.convertType("False", 0, DataType.wrapIntegerType, "java.lang.Integer"));
	}

	@Test
	public void convertTypeDateString() throws Exception {
		java.util.Date date = (java.util.Date) BeanUtil.convertType("2024-05-01 10:30:00", 0, DataType.dateType,
				"java.util.Date");
		assertEquals("2024-05-01 10:30:00", DateUtil.formatDate(date, "yyyy-MM-dd HH:mm:ss"));
		// 时间戳数字转Date
		java.util.Date fromLong = (java.util.Date) BeanUtil.convertType(1714545000000L, 0, DataType.dateType,
				"java.util.Date");
		assertEquals(1714545000000L, fromLong.getTime());
	}

	@Test
	public void convertTypeEnum() throws Exception {
		// 字符串key转枚举:按getValue值匹配
		Object result = BeanUtil.convertType("1", 0, DataType.enumType, Status.class.getName());
		assertEquals(Status.ENABLED, result);
		// getValue值"0"匹配DISABLED
		assertEquals(Status.DISABLED, BeanUtil.convertType("0", 0, DataType.enumType, Status.class.getName()));
		// 按name匹配(忽略大小写)
		assertEquals(Status.DISABLED, BeanUtil.convertType("disabled", 0, DataType.enumType, Status.class.getName()));
		// 无自定义属性枚举按name(忽略大小写)匹配
		assertEquals(Color.BLUE, BeanUtil.convertType("blue", 0, DataType.enumType, Color.class.getName()));
	}

	@Test
	public void convertTypeUnmatchTypeReturnRaw() throws Exception {
		// 无法识别的目标类型原样返回
		StringBuilder sb = new StringBuilder("x");
		assertSame(sb, BeanUtil.convertType(sb, 0, DataType.objectType, "java.lang.StringBuilder"));
	}

	@Test
	public void convertBoolean() {
		assertEquals("1", BeanUtil.convertBoolean("true"));
		assertEquals("0", BeanUtil.convertBoolean("false"));
		assertEquals("abc", BeanUtil.convertBoolean("abc"));
	}

	@Test
	public void getEnumValue() {
		// 存在getKey方法(getValue)的枚举返回key值,含无自定义字段但提供getValue方法的枚举
		assertEquals("1", BeanUtil.getEnumValue(Status.ENABLED));
		// 无自定义字段且无getKey方法的枚举返回name
		assertEquals("RED", BeanUtil.getEnumValue(Color.RED));
		assertNull(BeanUtil.getEnumValue(null));
	}

	@Test
	public void newEnumInstance() {
		// Status按getValue值或name(忽略大小写)匹配
		assertEquals(Status.ENABLED, BeanUtil.newEnumInstance("1", Status.class));
		assertEquals(Status.ENABLED, BeanUtil.newEnumInstance("enabled", Status.class));
		// 无自定义字段枚举按name(忽略大小写)匹配
		assertEquals(Color.BLUE, BeanUtil.newEnumInstance("blue", Color.class));
		assertNull(BeanUtil.newEnumInstance(null, Status.class));
		assertNull(BeanUtil.newEnumInstance("notMatch", Status.class));
	}

	@Test
	public void equalsAndCompare() {
		assertTrue(BeanUtil.equals("a", "a"));
		assertTrue(BeanUtil.equals(null, null));
		assertFalse(BeanUtil.equals(null, "x"));
		// 跨类型按字符串比较
		assertTrue(BeanUtil.equalsIgnoreType(123, "123", false));
		assertTrue(BeanUtil.equalsIgnoreType("Abc", "aBC", true));
		assertFalse(BeanUtil.equalsIgnoreType(null, "x", true));
		assertTrue(BeanUtil.equalsIgnoreType(null, null, true));
		// 数值大小比较
		assertTrue(BeanUtil.compare(1, 2) < 0);
		assertTrue(BeanUtil.compare("b", "a") > 0);
		assertEquals(0, BeanUtil.compare(null, null));
		// 数字与日期比较按日期
		assertTrue(BeanUtil.compare("2024-01-01", "2025-01-01") < 0);
	}

	@Test
	public void getPropertyAndSetter() {
		SampleBean bean = new SampleBean();
		BeanUtil.setProperty(bean, "userName", "张三");
		assertEquals("张三", BeanUtil.getProperty(bean, "userName"));
		// 自动类型转换:字符串转Integer
		BeanUtil.setProperty(bean, "age", "30");
		assertEquals(Integer.valueOf(30), bean.getAge());
		// boolean原生类型
		BeanUtil.setProperty(bean, "active", "true");
		assertTrue(bean.isActive());
		// 不存在的属性抛异常
		assertThrows(RuntimeException.class, () -> BeanUtil.setProperty(bean, "notExist", "x"));
		// 不存在的get属性返回null
		assertNull(BeanUtil.getProperty(bean, "notExist"));
		// Map类型按key取值
		Map<String, Object> map = new HashMap<>();
		map.put("userName", "map");
		assertEquals("map", BeanUtil.getProperty(map, "userName"));
	}

	@Test
	public void getComplexProperty() {
		SampleBean bean = new SampleBean();
		bean.setTags(Arrays.asList("a", "b", "c"));
		bean.setAttrs(new HashMap<>(Map.of("city", "杭州")));
		bean.setNames(new String[] { "n0", "n1" });
		// 数组/List下标
		assertEquals("b", BeanUtil.getComplexProperty(bean, "tags[1]"));
		assertEquals("n1", BeanUtil.getComplexProperty(bean, "names[1]"));
		// 级联属性(a.b)通过reflectBeanToAry提取
		assertEquals("杭州", BeanUtil.reflectBeanToAry(bean, "attrs.city")[0]);
		// 越界返回null
		assertNull(BeanUtil.getComplexProperty(bean, "tags[9]"));
		// 不存在属性返回null
		assertNull(BeanUtil.getComplexProperty(bean, "noSuchProp"));
	}

	@Test
	public void getKeyAndIndex() {
		assertNull(BeanUtil.getKeyAndIndex("normalProp"));
		assertNull(BeanUtil.getKeyAndIndex("a[1]b"));
		assertNull(BeanUtil.getKeyAndIndex(null));
		var ki = BeanUtil.getKeyAndIndex("tags[12]");
		assertEquals("tags", ki.getKey());
		assertEquals(12, ki.getIndex());
		// 负数下标不符合模式按普通属性名处理
		assertNull(BeanUtil.getKeyAndIndex("tags[-1]"));
	}

	@Test
	public void reflectBeanToAryBasics() {
		SampleBean bean = new SampleBean();
		bean.setUserName("tom").setAge(20);
		Object[] ary = BeanUtil.reflectBeanToAry(bean, "userName", "age", "notExist");
		assertEquals("tom", ary[0]);
		assertEquals(20, ary[1]);
		assertNull(ary[2]);
		// 默认值填充
		Object[] ary2 = BeanUtil.reflectBeanToAry(bean, new String[] { "userName", "notExist" },
				new Object[] { "dft1", "dft2" }, null);
		assertEquals("tom", ary2[0]);
		assertEquals("dft2", ary2[1]);
		// Map对象按key提取
		Map<String, Object> map = new HashMap<>();
		map.put("NAME", "值1");
		Object[] ary3 = BeanUtil.reflectBeanToAry(map, "name");
		assertEquals("值1", ary3[0], "Map取值应兼容key大小写");
	}

	@Test
	public void reflectBeansToList() {
		SampleBean b1 = new SampleBean();
		b1.setUserName("t1").setAge(1);
		SampleBean b2 = new SampleBean();
		b2.setUserName("t2").setAge(2);
		List result = BeanUtil.reflectBeansToList(Arrays.asList(b1, b2), "userName", "age");
		assertEquals(2, result.size());
		assertEquals("t1", ((List) result.get(0)).get(0));
		assertEquals(2, ((List) result.get(1)).get(1));
		// null入参/null行
		assertNull(BeanUtil.reflectBeansToList(null, "userName"));
		assertNull(BeanUtil.reflectBeansToList(new ArrayList<>(), "userName"));
		List result2 = BeanUtil.reflectBeansToList(Arrays.asList((Object) null, b2), "userName");
		assertNull(result2.get(0));
		assertEquals("t2", ((List) result2.get(1)).get(0));
	}

	@Test
	public void reflectBeansToListMapRows() {
		// Map行支持,含key大小写兼容和缺失key补null占位
		Map<String, Object> row = new HashMap<>();
		row.put("NAME", "map值");
		row.put("AGE", 8);
		List result = BeanUtil.reflectBeansToList(Arrays.asList(row), "name", "salary");
		assertEquals("map值", ((List) result.get(0)).get(0));
		assertNull(((List) result.get(0)).get(1), "缺失的key应补null占位保持列对齐");
	}

	@Test
	public void sliceToArray() {
		SampleBean b1 = new SampleBean();
		b1.setUserName("t1");
		SampleBean b2 = new SampleBean();
		// null值被剔除
		Object[] ary = BeanUtil.sliceToArray(Arrays.asList(b1, b2), "userName");
		assertArrayEquals(new Object[] { "t1" }, ary);
		assertNull(BeanUtil.sliceToArray(null, "userName"));
	}

	@Test
	public void reflectListToBeanConvert() {
		List rows = new ArrayList();
		// 行数据:字符串数字转Integer
		rows.add(Arrays.asList("tom", "25"));
		rows.add(Arrays.asList("jerry", 30));
		List result = BeanUtil.reflectListToBean(null, rows, new String[] { "userName", "age" }, null,
				SampleBean.class);
		assertEquals(2, result.size());
		assertEquals("tom", ((SampleBean) result.get(0)).getUserName());
		assertEquals(Integer.valueOf(25), ((SampleBean) result.get(0)).getAge());
		assertEquals(Integer.valueOf(30), ((SampleBean) result.get(1)).getAge());
	}

	@Test
	public void reflectListToBeanRecord() {
		List rows = new ArrayList();
		rows.add(Arrays.asList(1, "p1"));
		// 字符串数字自动转int
		rows.add(Arrays.asList("2", "p2"));
		List result = BeanUtil.reflectListToBean(null, rows, new String[] { "x", "label" }, null, PointRecord.class);
		assertEquals(2, result.size());
		assertEquals(new PointRecord(1, "p1"), result.get(0));
		assertEquals(new PointRecord(2, "p2"), result.get(1));
	}

	@Test
	public void reflectListToBeanValidation() {
		// 空集合在类型校验之前直接返回null(行为记录:空数据不做voClass合法性校验)
		assertNull(BeanUtil.reflectListToBean(null, new ArrayList(), new String[] { "a" }, null, List.class));
		// 抽象类/接口校验(非空数据时触发)
		assertThrows(IllegalArgumentException.class, () -> BeanUtil.reflectListToBean(null,
				Arrays.asList(Arrays.asList("a")), new String[] { "a" }, null, List.class));
		// 属性不存在不抛异常,静默跳过赋值(行为记录)
		List result = BeanUtil.reflectListToBean(null, Arrays.asList(Arrays.asList("a")),
				new String[] { "a", "b" }, null, SampleBean.class);
		assertEquals(1, result.size());
		assertNull(((SampleBean) result.get(0)).getUserName());
	}

	/**
	 * 回归测试:reflectBeansToList逐行判断Map类型,首行为null时后续Map行正常取值
	 */
	@Test
	public void reflectBeansToListMapFirstRowNull() {
		Map<String, Object> row = new HashMap<>();
		row.put("NAME", "map值");
		List result = BeanUtil.reflectBeansToList(Arrays.asList(null, row), "name");
		assertNull(result.get(0));
		assertEquals("map值", ((List) result.get(1)).get(0), "Map行的值应能取出");
	}

	@Test
	public void batchSetProperties() {
		SampleBean b1 = new SampleBean();
		SampleBean b2 = new SampleBean();
		// forceUpdate=false时null值跳过
		BeanUtil.batchSetProperties(Arrays.asList(b1, b2), new String[] { "userName", "age" },
				new Object[] { "common", null }, true, false);
		assertEquals("common", b1.getUserName());
		assertEquals("common", b2.getUserName());
		assertNull(b1.getAge());
		// forceUpdate=true时null也覆盖(原生类型赋默认值)
		BeanUtil.batchSetProperties(Arrays.asList(b1), new String[] { "age" }, new Object[] { "18" }, true, true);
		assertEquals(Integer.valueOf(18), b1.getAge());
		// 类型自动转换
		BeanUtil.batchSetProperties(Arrays.asList(b1), new String[] { "active" }, new Object[] { "1" }, true, true);
		assertTrue(b1.isActive());
	}

	@Test
	public void mappingSetPropertiesRowwise() {
		SampleBean b1 = new SampleBean();
		SampleBean b2 = new SampleBean();
		List<Object[]> rows = new ArrayList<>();
		rows.add(new Object[] { "n1", 10 });
		rows.add(new Object[] { "n2", 20 });
		BeanUtil.mappingSetProperties(Arrays.asList(b1, b2), new String[] { "userName", "age" }, rows,
				new int[] { 0, 1 }, true);
		assertEquals("n1", b1.getUserName());
		assertEquals(Integer.valueOf(20), b2.getAge());
	}

	@Test
	public void getMethodOverloads() throws Exception {
		// 按名称+参数数量
		assertNotNull2(BeanUtil.getMethod(SampleBean.class, "setusername", 1));
		assertNull(BeanUtil.getMethod(SampleBean.class, "setUserName", 2));
		// 按类型精确匹配重载
		Method m = BeanUtil.getMethod(SampleBean.class, "setUserName", 1, new Class[] { String.class });
		assertEquals("setUserName", m.getName());
		// 类型不兼容时回退首个同名方法
		Method m2 = BeanUtil.getMethod(SampleBean.class, "setUserName", 1, new Class[] { Integer.class });
		assertEquals("setUserName", m2.getName());
		// invokeMethod
		SampleBean bean = new SampleBean();
		BeanUtil.invokeMethod(bean, "SETUSERNAME", new Object[] { "iv" });
		assertEquals("iv", bean.getUserName());
		assertNull(BeanUtil.invokeMethod(bean, "noSuchMethod", new Object[] {}));
	}

	private void assertNotNull2(Object obj) {
		if (obj == null) {
			throw new AssertionError("对象不应为null");
		}
	}

	@Test
	public void invokeMethodNotFound() throws Exception {
		// 方法不存在返回null不抛异常
		assertNull(BeanUtil.invokeMethod(new SampleBean(), "noSuchMethod", new Object[] {}));
		// 方法存在但参数值类型不兼容时invoke抛IllegalArgumentException
		assertThrows(Exception.class,
				() -> BeanUtil.invokeMethod(new SampleBean(), "setUserName", new Object[] { 123 }));
	}

	private String nullSafeMethodName(Method method) {
		return (method == null) ? null : method.getName();
	}

	@Test
	public void wrapEntitiesDedup() {
		org.sagacity.sqltoy.config.model.EntityMeta entityMeta = new org.sagacity.sqltoy.config.model.EntityMeta();
		entityMeta.setIdArray(new String[] { "userName" });
		// 去重且null被剔除
		List<SampleBean> entities = BeanUtil.<SampleBean>wrapEntities(null, entityMeta, SampleBean.class, "id1",
				"id1", null, "id2");
		assertEquals(2, entities.size());
		assertEquals("id1", ((SampleBean) entities.get(0)).getUserName());
		assertEquals("id2", ((SampleBean) entities.get(1)).getUserName());
	}

	@Test
	public void getArrayIndexValue() {
		List<String> list = Arrays.asList("a", "b");
		assertEquals("a", BeanUtil.getArrayIndexValue(list, 0));
		assertNull(BeanUtil.getArrayIndexValue(list, 5));
		assertNull(BeanUtil.getArrayIndexValue(null, 0));
		assertEquals("b", BeanUtil.getArrayIndexValue(new Object[] { "a", "b" }, 1));
		// 非集合类型返回null
		assertNull(BeanUtil.getArrayIndexValue("abc", 0));
	}
}
