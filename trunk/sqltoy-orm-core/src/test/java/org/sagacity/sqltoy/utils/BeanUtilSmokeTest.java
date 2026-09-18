package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.annotation.Column;
import org.sagacity.sqltoy.config.model.DataType;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.TableCascadeModel;
import org.sagacity.sqltoy.plugins.IUnifyFieldsHandler;

/**
 * BeanUtil 功能冒烟测试:按功能组对常用公开API做一轮主干路径验证
 */
public class BeanUtilSmokeTest {

	/** 带自定义key属性的枚举(getKey语义) */
	enum Status {
		ENABLED, DISABLED;

		public String getValue() {
			return this == ENABLED ? "1" : "0";
		}
	}

	/** 员工对象,含嵌套部门用于级联属性 */
	static class Dept implements java.io.Serializable {
		private static final long serialVersionUID = 1L;
		private String deptName;

		public String getDeptName() {
			return deptName;
		}

		public void setDeptName(String deptName) {
			this.deptName = deptName;
		}
	}

	static class Staff implements java.io.Serializable {
		private static final long serialVersionUID = 1L;
		private String staffName;
		private String deptName;
		private Integer age;
		private double salary;
		private boolean active;
		private LocalDate hireDate;
		private String[] tags;
		private List<String> emails;
		private Dept dept;

		public String getStaffName() {
			return staffName;
		}

		public void setStaffName(String staffName) {
			this.staffName = staffName;
		}

		public String getDeptName() {
			return deptName;
		}

		public void setDeptName(String deptName) {
			this.deptName = deptName;
		}

		public Integer getAge() {
			return age;
		}

		public void setAge(Integer age) {
			this.age = age;
		}

		public double getSalary() {
			return salary;
		}

		public void setSalary(double salary) {
			this.salary = salary;
		}

		public boolean isActive() {
			return active;
		}

		public void setActive(boolean active) {
			this.active = active;
		}

		public LocalDate getHireDate() {
			return hireDate;
		}

		public void setHireDate(LocalDate hireDate) {
			this.hireDate = hireDate;
		}

		public String[] getTags() {
			return tags;
		}

		public void setTags(String[] tags) {
			this.tags = tags;
		}

		public List<String> getEmails() {
			return emails;
		}

		public void setEmails(List<String> emails) {
			this.emails = emails;
		}

		public Dept getDept() {
			return dept;
		}

		public void setDept(Dept dept) {
			this.dept = dept;
		}
	}

	/** 字段带@Column注解的实体,用于getClassFieldMap */
	static class AnnoEntity {
		@Column(name = "jsonField", type = java.sql.Types.OTHER)
		private String jsonField;

		private String plainField;
	}

	record PointRecord(int x, String label) {
	}

	/** 持有集合级联属性的主实体,用于oneToMany装配 */
	static class Team implements java.io.Serializable {
		private static final long serialVersionUID = 1L;
		private String deptName;
		private List<Staff> staffs;

		public String getDeptName() {
			return deptName;
		}

		public void setDeptName(String deptName) {
			this.deptName = deptName;
		}

		public List<Staff> getStaffs() {
			return staffs;
		}

		public void setStaffs(List<Staff> staffs) {
			this.staffs = staffs;
		}
	}

	// ==================== 1.属性读写 ====================

	@Test
	public void smoke_setPropertyGetProperty() {
		Staff staff = new Staff();
		BeanUtil.setProperty(staff, "staffName", "张三");
		BeanUtil.setProperty(staff, "age", "30");
		BeanUtil.setProperty(staff, "salary", "8000.5");
		BeanUtil.setProperty(staff, "active", "true");
		BeanUtil.setProperty(staff, "hireDate", "2024-05-01");

		assertEquals("张三", BeanUtil.getProperty(staff, "staffName"));
		assertEquals(Integer.valueOf(30), staff.getAge(), "字符串应自动转换为Integer");
		assertEquals(8000.5, staff.getSalary(), 0.0001, "字符串应自动转换为double");
		assertTrue(staff.isActive(), "字符串应自动转换为boolean");
		assertEquals(LocalDate.of(2024, 5, 1), staff.getHireDate(), "字符串应自动转换为LocalDate");

		// Map对象读/写均按key操作(2026-9-14修复:此前setProperty对Map抛异常,与读路径不对称)
		Map<String, Object> map = new HashMap<>();
		BeanUtil.setProperty(map, "staffName", "map值");
		assertEquals("map值", BeanUtil.getProperty(map, "staffName"));
		assertEquals("map值", map.get("staffName"), "setProperty应按key写入map");

		// 不存在属性:写抛异常,读返回null
		assertThrows(RuntimeException.class, () -> BeanUtil.setProperty(staff, "notExist", "x"));
		assertNull(BeanUtil.getProperty(staff, "notExist"));
	}

	@Test
	public void smoke_setPropertyMapSupport() {
		// 2026-9-14修复回归:map类型直接按key写入,读/写往返一致
		Map<String, Object> map = new HashMap<>();
		BeanUtil.setProperty(map, "name", "v1");
		assertEquals("v1", BeanUtil.getProperty(map, "name"));
		// 4参重载同样支持
		BeanUtil.setProperty(map, "count", 5, java.sql.Types.INTEGER);
		assertEquals(5, map.get("count"));
		// IgnoreKeyCaseMap等Map实现同样按key写入
		org.sagacity.sqltoy.model.IgnoreKeyCaseMap<String, Object> ignoreCase = new org.sagacity.sqltoy.model.IgnoreKeyCaseMap<>();
		BeanUtil.setProperty(ignoreCase, "Name", "v2");
		assertEquals("v2", BeanUtil.getProperty(ignoreCase, "name"));
		// 批量路径保持既有行为:Map行静默跳过
		Map<String, Object> mapRow = new HashMap<>();
		BeanUtil.batchSetProperties(Arrays.asList(mapRow), new String[] { "name" }, new Object[] { "x" }, true, true);
		assertNull(mapRow.get("name"), "批量路径对Map行应保持静默跳过");
		// bean路径回归:不存在的属性仍抛异常
		assertThrows(RuntimeException.class, () -> BeanUtil.setProperty(new Staff(), "notExist", "x"));
	}

	@Test
	public void smoke_setPropertyMapNestedKey() {
		// 中间层不存在:自动创建嵌套map后写入叶子层
		Map<String, Object> map = new HashMap<>();
		BeanUtil.setProperty(map, "dept.deptName", "研发部");
		Map<String, Object> dept = (Map<String, Object>) map.get("dept");
		assertNotNull(dept);
		assertEquals("研发部", dept.get("deptName"));

		// 中间层已存在且为map:深入写入
		BeanUtil.setProperty(map, "dept.floor", 5);
		assertEquals(5, dept.get("floor"));
		assertEquals(1, map.size(), "不应在顶层产生冗余key");

		// 三层嵌套
		BeanUtil.setProperty(map, "dept.leader.name", "张三");
		assertEquals("张三", ((Map) ((Map) map.get("dept")).get("leader")).get("name"));

		// 整串字面key优先:map中已存在"a.b"形态的key时直接覆盖该key
		Map<String, Object> flatMap = new HashMap<>();
		flatMap.put("dept.deptName", "字面key值");
		BeanUtil.setProperty(flatMap, "dept.deptName", "覆盖后");
		assertEquals("覆盖后", flatMap.get("dept.deptName"));
		assertNull(flatMap.get("dept"), "字面key存在时不应拆分创建嵌套map");

		// 中间层是POJO对象:剩余key转由标准setProperty按setter写入,含类型转换
		Map<String, Object> beanMap = new HashMap<>();
		Staff staff = new Staff();
		beanMap.put("staff", staff);
		BeanUtil.setProperty(beanMap, "staff.staffName", "张三");
		assertEquals("张三", staff.getStaffName());
		// 值类型转换同样生效:字符串"30"转Integer
		BeanUtil.setProperty(beanMap, "staff.age", "30");
		assertEquals(Integer.valueOf(30), staff.getAge());

		// 中间层既不是map也没有对应setter属性(如String):由setter匹配路径抛异常
		Map<String, Object> conflictMap = new HashMap<>();
		conflictMap.put("dept", "字符串值");
		assertThrows(RuntimeException.class, () -> BeanUtil.setProperty(conflictMap, "dept.deptName", "x"));

		// IgnoreKeyCaseMap:每层尊重其自身get/put语义(key忽略大小写)
		org.sagacity.sqltoy.model.IgnoreKeyCaseMap<String, Object> ignoreCase = new org.sagacity.sqltoy.model.IgnoreKeyCaseMap<>();
		BeanUtil.setProperty(ignoreCase, "Dept.DeptName", "大小写不敏感");
		Object nested = BeanUtil.getProperty(ignoreCase, "dept");
		assertTrue(nested instanceof Map, "写入Dept.DeptName后按dept应取到内层map");
		assertEquals("大小写不敏感", ((Map) nested).get("deptname"));

		// 写入后可被读路径的级联取值读回(reflectBeanToAry支持map的xxx.yyy取值)
		assertEquals("研发部", BeanUtil.reflectBeanToAry(map, "dept.deptName")[0]);

		// 单层key行为不回归
		BeanUtil.setProperty(map, "topLevel", "v");
		assertEquals("v", map.get("topLevel"));
	}

	@Test
	public void smoke_batchSetAndMappingSet() {
		Staff s1 = new Staff();
		Staff s2 = new Staff();
		// force=false时null跳过,force=true时类型转换后覆盖
		BeanUtil.batchSetProperties(Arrays.asList(s1, s2), new String[] { "staffName", "age" },
				new Object[] { "公共名", null }, true, false);
		assertEquals("公共名", s1.getStaffName());
		assertEquals("公共名", s2.getStaffName());
		assertNull(s1.getAge());

		BeanUtil.batchSetProperties(Arrays.asList(s1), new String[] { "age", "active" }, new Object[] { "18", "1" },
				true, true);
		assertEquals(Integer.valueOf(18), s1.getAge());
		assertTrue(s1.isActive());

		// 按行映射赋值
		Staff s3 = new Staff();
		Staff s4 = new Staff();
		List<Object[]> rows = new ArrayList<>();
		rows.add(new Object[] { "n3", 20 });
		rows.add(new Object[] { "n4", 40 });
		BeanUtil.mappingSetProperties(Arrays.asList(s3, s4), new String[] { "staffName", "age" }, rows,
				new int[] { 0, 1 }, true);
		assertEquals("n3", s3.getStaffName());
		assertEquals(Integer.valueOf(40), s4.getAge());
	}

	// ==================== 2.方法反射 ====================

	@Test
	public void smoke_matchMethodsAndInvoke() throws Exception {
		// setter/getter匹配
		Method[] sets = BeanUtil.matchSetMethods(Staff.class, "staffName", "age", "active", "notExist");
		assertEquals("setStaffName", sets[0].getName());
		assertEquals("setAge", sets[1].getName());
		assertEquals("setActive", sets[2].getName());
		assertNull(sets[3], "不存在的属性应为null占位");

		Method[] gets = BeanUtil.matchGetMethods(Staff.class, "staffName", "active", "notExist");
		assertEquals("getStaffName", gets[0].getName());
		assertEquals("isActive", gets[1].getName(), "boolean属性应匹配is前缀方法");
		assertNull(gets[2]);

		// 属性→JDBC类型映射
		Integer[] types = BeanUtil.matchMethodsType(Staff.class, "staffName", "age", "active", "hireDate");
		assertEquals(java.sql.Types.VARCHAR, types[0]);
		assertEquals(java.sql.Types.INTEGER, types[1]);
		assertEquals(java.sql.Types.BOOLEAN, types[2]);
		assertEquals(java.sql.Types.DATE, types[3]);

		// 全部setter属性名
		List<String> setNames = Arrays.asList(BeanUtil.matchSetMethodNames(Staff.class));
		assertTrue(setNames.containsAll(Arrays.asList("staffName", "age", "salary", "active")));

		// 按方法名+参数个数查找并调用
		assertNotNull(BeanUtil.getMethod(Staff.class, "setstaffname", 1));
		assertNull(BeanUtil.getMethod(Staff.class, "setStaffName", 2));
		Staff staff = new Staff();
		BeanUtil.invokeMethod(staff, "SETSTAFFNAME", new Object[] { "反射赋值" });
		assertEquals("反射赋值", staff.getStaffName());
		assertNull(BeanUtil.invokeMethod(staff, "noSuchMethod", new Object[] {}));
	}

	// ==================== 3.类型转换 ====================

	@Test
	public void smoke_convertTypeCore() throws Exception {
		// 数字族
		assertEquals(Integer.valueOf(12), BeanUtil.convertType("12", 0, DataType.wrapIntegerType, "java.lang.Integer"));
		assertEquals(Long.valueOf(12), BeanUtil.convertType("12.0", 0, DataType.wrapLongType, "java.lang.Long"));
		assertEquals(Double.valueOf(12.5), BeanUtil.convertType("12.5", 0, DataType.wrapDoubleType, "java.lang.Double"));
		assertEquals(new BigDecimal("1.20"),
				BeanUtil.convertType("1.20", 0, DataType.wrapBigDecimalType, "java.math.BigDecimal"));
		// boolean族:"1"/"true"/大小写
		assertEquals(Boolean.TRUE, BeanUtil.convertType("1", 0, DataType.wrapBooleanType, "java.lang.Boolean"));
		assertEquals(Boolean.TRUE, BeanUtil.convertType("TRUE", 0, DataType.wrapBooleanType, "java.lang.Boolean"));
		assertEquals(Boolean.FALSE, BeanUtil.convertType("0", 0, DataType.wrapBooleanType, "java.lang.Boolean"));
		assertEquals(Integer.valueOf(1), BeanUtil.convertType("true", 0, DataType.wrapIntegerType, "java.lang.Integer"));
		// 日期族:字符串↔日期,LocalDateTime格式化
		Object date = BeanUtil.convertType("2024-05-01 10:30:00", 0, DataType.dateType, "java.util.Date");
		assertEquals("2024-05-01 10:30:00", DateUtil.formatDate(date, "yyyy-MM-dd HH:mm:ss"));
		assertEquals("2024-05-01 10:30:00",
				BeanUtil.convertType(Timestamp.valueOf("2024-05-01 10:30:00"), 0, DataType.stringType,
						"java.lang.String"));
		assertEquals("2024-05-01",
				BeanUtil.convertType(LocalDate.of(2024, 5, 1), 0, DataType.stringType, "java.lang.String"));
		// 枚举:按getValue值写入并回读
		assertEquals(Status.ENABLED, BeanUtil.convertType("1", 0, DataType.enumType, Status.class.getName()));
		assertEquals("0", BeanUtil.convertType(Status.DISABLED, 0, DataType.stringType, "java.lang.String"));
		// 原生类型null给默认值,包装类型null保持null,同类型原样返回
		assertEquals(0, BeanUtil.convertType(null, 0, DataType.primitiveIntType, "int"));
		assertNull(BeanUtil.convertType(null, 0, DataType.wrapIntegerType, "java.lang.Integer"));
		String same = "abc";
		assertSame(same, BeanUtil.convertType(same, 0, DataType.stringType, "java.lang.String"));
		// convertBoolean
		assertEquals("1", BeanUtil.convertBoolean("True"));
		assertEquals("0", BeanUtil.convertBoolean("FALSE"));
	}

	@Test
	public void smoke_convertArray() {
		// 字符串数组转Integer数组
		Object ints = BeanUtil.convertArray(new String[] { "1", "2" }, "java.lang.Integer[]");
		assertArrayEquals(new Integer[] { 1, 2 }, (Integer[]) ints);
		// Integer数组转long原生数组
		Object longs = BeanUtil.convertArray(new Integer[] { 3, 4 }, "long[]");
		assertTrue(longs.getClass() == long[].class);
		assertEquals(4L, java.lang.reflect.Array.get(longs, 1));
		// 数组转BigDecimal数组
		Object bigDecimals = BeanUtil.convertArray(new String[] { "1.5", "2.5" }, "java.math.BigDecimal[]");
		assertEquals(new BigDecimal("1.5"), java.lang.reflect.Array.get(bigDecimals, 0));
		// 类型一致或不在支持范围原样返回
		String[] same = new String[] { "a" };
		assertSame(same, BeanUtil.convertArray(same, "java.lang.String[]"));
		assertSame(same, BeanUtil.convertArray(same, "java.lang.Object[]"));
		assertNull(BeanUtil.convertArray(null, "int[]"));
	}

	@Test
	public void smoke_enumReadWrite() {
		// 写:有getValue方法的枚举返回getValue值;无则返回name
		assertEquals("1", BeanUtil.getEnumValue(Status.ENABLED));
		// 读:按getValue值或name(忽略大小写)回读
		assertEquals(Status.ENABLED, BeanUtil.newEnumInstance("1", Status.class));
		assertEquals(Status.DISABLED, BeanUtil.newEnumInstance("disabled", Status.class));
		assertNull(BeanUtil.newEnumInstance("不匹配", Status.class));
		assertNull(BeanUtil.newEnumInstance(null, Status.class));
		// 往返一致
		Object written = BeanUtil.getEnumValue(Status.DISABLED);
		assertEquals(Status.DISABLED, BeanUtil.newEnumInstance(written, Status.class));
	}

	// ==================== 4.对象比较 ====================

	@Test
	public void smoke_equalsAndCompare() {
		assertTrue(BeanUtil.equals("a", "a"));
		assertTrue(BeanUtil.equals(null, null));
		assertFalse(BeanUtil.equals(null, "x"));
		// 跨类型按字符串比较
		assertTrue(BeanUtil.equalsIgnoreType(123, "123", false));
		assertTrue(BeanUtil.equalsIgnoreType("Abc", "aBC", true));
		assertFalse(BeanUtil.equalsIgnoreType("abc", "abd", false));
		// 数值与字符串比较
		assertTrue(BeanUtil.compare(1, 2) < 0);
		assertEquals(0, BeanUtil.compare(null, null));
		assertTrue(BeanUtil.compare("b", "a") > 0);
	}

	// ==================== 5.Bean与二维数据互转 ====================

	@Test
	public void smoke_reflectBeanToAry() {
		Staff staff = new Staff();
		staff.setStaffName("tom");
		staff.setAge(20);
		// 多属性提取,缺失属性null占位
		Object[] ary = BeanUtil.reflectBeanToAry(staff, "staffName", "age", "notExist");
		assertEquals("tom", ary[0]);
		assertEquals(20, ary[1]);
		assertNull(ary[2]);
		// 默认值填充
		Object[] ary2 = BeanUtil.reflectBeanToAry(staff, new String[] { "staffName", "notExist" },
				new Object[] { "d1", "d2" }, null);
		assertEquals("tom", ary2[0]);
		assertEquals("d2", ary2[1]);
		// 级联属性(dept.deptName)
		Dept dept = new Dept();
		dept.setDeptName("研发部");
		staff.setDept(dept);
		assertEquals("研发部", BeanUtil.reflectBeanToAry(staff, "dept.deptName")[0]);
		// Map对象按key提取(大小写兼容)
		Map<String, Object> map = new HashMap<>();
		map.put("STAFFNAME", "map取值");
		assertEquals("map取值", BeanUtil.reflectBeanToAry(map, "staffName")[0]);
	}

	@Test
	public void smoke_reflectBeansToListAndInnerAry() {
		Staff s1 = new Staff();
		s1.setStaffName("t1");
		s1.setAge(1);
		Staff s2 = new Staff();
		s2.setStaffName("t2");
		s2.setAge(2);
		// beans→List行
		List rows = BeanUtil.reflectBeansToList(Arrays.asList(s1, s2), "staffName", "age");
		assertEquals(2, rows.size());
		assertEquals("t1", ((List) rows.get(0)).get(0));
		assertEquals(2, ((List) rows.get(1)).get(1));
		// Map行支持,key大小写兼容,缺失key补null对齐
		Map<String, Object> mapRow = new HashMap<>();
		mapRow.put("STAFFNAME", "m1");
		List mapRows = BeanUtil.reflectBeansToList(Arrays.asList(mapRow), "staffName", "age");
		assertEquals("m1", ((List) mapRows.get(0)).get(0));
		assertNull(((List) mapRows.get(0)).get(1));
		// beans→Object[]行
		List<Object[]> inner = BeanUtil.reflectBeansToInnerAry(Arrays.asList(s1, s2),
				new String[] { "staffName", "age" }, null, null);
		assertEquals(2, inner.size());
		assertArrayEquals(new Object[] { "t2", 2 }, inner.get(1));
		// null入参返回null
		assertNull(BeanUtil.reflectBeansToList(null, "staffName"));
		assertNull(BeanUtil.reflectBeansToInnerAry(new ArrayList<>(), new String[] { "staffName" }, null, null));
		// sliceToArray剔除null
		Staff s3 = new Staff();
		assertArrayEquals(new Object[] { "t1" }, BeanUtil.sliceToArray(Arrays.asList(s1, s3), "staffName"));
	}

	@Test
	public void smoke_reflectListToBean() {
		List rows = new ArrayList();
		rows.add(Arrays.asList("tom", "25"));
		rows.add(Arrays.asList("jerry", 30));
		// 二维行转bean,字符串数字自动转换
		List result = BeanUtil.reflectListToBean(null, rows, new String[] { "staffName", "age" }, null, Staff.class);
		assertEquals(2, result.size());
		assertEquals("tom", ((Staff) result.get(0)).getStaffName());
		assertEquals(Integer.valueOf(25), ((Staff) result.get(0)).getAge());
		// 下标映射(列顺序与属性顺序不一致)
		List result2 = BeanUtil.reflectListToBean(null, rows, new int[] { 1, 0 }, new String[] { "age", "staffName" },
				null, Staff.class);
		assertEquals("tom", ((Staff) result2.get(0)).getStaffName());
		assertEquals(Integer.valueOf(25), ((Staff) result2.get(0)).getAge());
		// Record类型支持
		List recRows = new ArrayList();
		recRows.add(Arrays.asList(1, "p1"));
		List recs = BeanUtil.reflectListToBean(null, recRows, new String[] { "x", "label" }, null, PointRecord.class);
		assertEquals(new PointRecord(1, "p1"), recs.get(0));
		// 抽象类型校验
		assertThrows(IllegalArgumentException.class,
				() -> BeanUtil.reflectListToBean(null, Arrays.asList(Arrays.asList("a")), new String[] { "a" }, null,
						List.class));
	}

	// ==================== 6.级联/复杂取值 ====================

	@Test
	public void smoke_complexPropertyAndArrayIndex() {
		Staff staff = new Staff();
		staff.setEmails(Arrays.asList("e0", "e1"));
		staff.setTags(new String[] { "g0", "g1" });
		Dept dept = new Dept();
		dept.setDeptName("研发部");
		staff.setDept(dept);
		// List/数组下标取值
		assertEquals("e1", BeanUtil.getComplexProperty(staff, "emails[1]"));
		assertEquals("g0", BeanUtil.getComplexProperty(staff, "tags[0]"));
		// 越界与不存在的属性返回null
		assertNull(BeanUtil.getComplexProperty(staff, "emails[9]"));
		assertNull(BeanUtil.getComplexProperty(staff, "noSuchProp"));
		// 属性名[x]解析
		assertNull(BeanUtil.getKeyAndIndex("normalProp"));
		var ki = BeanUtil.getKeyAndIndex("emails[3]");
		assertEquals("emails", ki.getKey());
		assertEquals(3, ki.getIndex());
		// getMaybeArrayValue:带[x]按下标取,不带下标返回整个值
		Map<String, Object> map = new HashMap<>();
		map.put("emails", Arrays.asList("m0", "m1"));
		assertEquals("m1", BeanUtil.getMaybeArrayValue(map, "emails[1]"));
		assertEquals(Arrays.asList("m0", "m1"), BeanUtil.getMaybeArrayValue(map, "emails"));
		// getArrayIndexValue:Object[]/Collection/Iterable/越界
		assertEquals("b", BeanUtil.getArrayIndexValue(new Object[] { "a", "b" }, 1));
		assertEquals("b", BeanUtil.getArrayIndexValue(Arrays.asList("a", "b"), 1));
		assertNull(BeanUtil.getArrayIndexValue(Arrays.asList("a"), 5));
		assertNull(BeanUtil.getArrayIndexValue("abc", 0));
	}

	// ==================== 7.统一公共字段回写 ====================

	@Test
	public void smoke_unifyFields() {
		IUnifyFieldsHandler handler = new IUnifyFieldsHandler() {
			@Override
			public Map<String, Object> createUnifyFields() {
				return new HashMap<>(Map.of("createBy", "admin", "createTime", "2024-01-01"));
			}

			@Override
			public Map<String, Object> updateUnifyFields() {
				return new HashMap<>(Map.of("updateBy", "updater"));
			}
		};
		String[] fieldsAry = new String[] { "staffName", "age", "createBy", "createTime", "updateBy" };
		// save类型只取create字段下标
		Map<String, Integer> saveIndex = BeanUtil.getUnifyFieldIndex(handler, fieldsAry, 1);
		assertEquals(2, saveIndex.size());
		assertEquals(2, saveIndex.get("createBy"));
		// update类型只取update字段下标
		Map<String, Integer> updateIndex = BeanUtil.getUnifyFieldIndex(handler, fieldsAry, 2);
		assertEquals(1, updateIndex.size());
		assertEquals(4, updateIndex.get("updateBy"));
		// saveOrUpdate取并集
		assertEquals(3, BeanUtil.getUnifyFieldIndex(handler, fieldsAry, 3).size());
		// null handler返回空Map
		assertTrue(BeanUtil.getUnifyFieldIndex(null, fieldsAry, 1).isEmpty());

		// 单个/批量回写公共字段(不存在的属性静默跳过)
		Staff s1 = new Staff();
		BeanUtil.backWriteUnifyFields(s1, saveIndex, new Object[] { "admin", "2024-01-01" });
		assertNull(s1.getStaffName(), "Staff无createBy属性,应静默跳过");
		Staff s2 = new Staff();
		Staff s3 = new Staff();
		Map<String, Integer> realIndex = new HashMap<>();
		realIndex.put("staffName", 0);
		realIndex.put("age", 1);
		BeanUtil.batchBackWriteUnifyFields(Arrays.asList(s2, s3), realIndex,
				Arrays.asList(new Object[] { "u1", 10 }, new Object[] { "u2", 20 }));
		assertEquals("u1", s2.getStaffName());
		assertEquals(Integer.valueOf(20), s3.getAge());
		// 空入参不动作
		BeanUtil.backWriteUnifyFields(s2, new HashMap<>(), new Object[] { "x" });
		BeanUtil.batchBackWriteUnifyFields(null, realIndex, null);
	}

	// ==================== 8.级联装配 ====================

	@Test
	public void smoke_loadAllMappingOneToManyAndOneToOne() throws Exception {
		// oneToMany:按关联字段匹配后将子对象集合装配到主实体的List属性
		Team team1 = new Team();
		team1.setDeptName("研发部");
		Team team2 = new Team();
		team2.setDeptName("市场部");

		Staff s1 = new Staff();
		s1.setStaffName("研发员工1");
		s1.setDeptName("研发部");
		Staff s2 = new Staff();
		s2.setStaffName("研发员工2");
		s2.setDeptName("研发部");
		Staff s3 = new Staff();
		s3.setStaffName("市场员工");
		s3.setDeptName("市场部");

		TableCascadeModel cascade = new TableCascadeModel();
		cascade.setCascadeType(1);
		cascade.setFields(new String[] { "deptName" });
		cascade.setMappedFields(new String[] { "deptName" });
		cascade.setProperty("staffs");
		BeanUtil.loadAllMapping(Arrays.asList(team1, team2), Arrays.asList(s1, s2, s3), cascade);
		assertEquals(2, team1.getStaffs().size(), "研发部应装配2名员工");
		assertEquals("研发员工1", team1.getStaffs().get(0).getStaffName());
		assertEquals(1, team2.getStaffs().size(), "市场部应装配1名员工");

		// oneToOne:一对一赋值
		TableCascadeModel one2one = new TableCascadeModel();
		one2one.setCascadeType(0);
		one2one.setFields(new String[] { "deptName" });
		one2one.setMappedFields(new String[] { "deptName" });
		one2one.setProperty("dept");
		Dept target = new Dept();
		target.setDeptName("研发部");
		Staff master = new Staff();
		master.setDeptName("研发部");
		BeanUtil.loadAllMapping(Arrays.asList(master), Arrays.asList(target), one2one);
		assertNotNull(master.getDept());
		assertEquals("研发部", master.getDept().getDeptName());
	}

	// ==================== 9.辅助功能 ====================

	@Test
	public void smoke_miscHelpers() {
		// isBaseDataType
		assertTrue(BeanUtil.isBaseDataType(String.class));
		assertTrue(BeanUtil.isBaseDataType(BigDecimal.class));
		assertTrue(BeanUtil.isBaseDataType(int.class));
		assertTrue(BeanUtil.isBaseDataType(LocalDate.class));
		assertFalse(BeanUtil.isBaseDataType(Staff.class));
		assertFalse(BeanUtil.isBaseDataType(null));

		// getClassFieldMap:提取@Column.type
		Map<String, Integer> fieldMap = BeanUtil.getClassFieldMap(AnnoEntity.class,
				new String[] { "jsonField", "plainField" });
		assertEquals(1, fieldMap.size());
		assertEquals(java.sql.Types.OTHER, fieldMap.get("jsonfield"));

		// getEntityClass:普通类原样返回
		assertSame(Staff.class, BeanUtil.getEntityClass(Staff.class));
		assertNull(BeanUtil.getEntityClass(null));

		// wrapEntities:按id数组构建实体并去重
		EntityMeta entityMeta = new EntityMeta();
		entityMeta.setIdArray(new String[] { "staffName" });
		List<Staff> entities = BeanUtil.<Staff>wrapEntities(null, entityMeta, Staff.class, "id1", "id1", null, "id2");
		assertEquals(2, entities.size());
		assertEquals("id1", entities.get(0).getStaffName());
		assertEquals("id2", entities.get(1).getStaffName());
	}
}
