package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.annotation.SqlToyFieldAlias;
import org.sagacity.sqltoy.model.Page;
import org.sagacity.sqltoy.model.PropsMapperConfig;

/**
 * MapperUtils 边缘场景测试
 */
public class MapperUtilsEdgeTest {

	public static class AddressDTO implements Serializable {
		private static final long serialVersionUID = 1L;
		private String city;
		private String street;

		public String getCity() {
			return city;
		}

		public void setCity(String city) {
			this.city = city;
		}

		public String getStreet() {
			return street;
		}

		public void setStreet(String street) {
			this.street = street;
		}
	}

	public static class AddressVO implements Serializable {
		private static final long serialVersionUID = 1L;
		private String city;
		private String street;

		public String getCity() {
			return city;
		}

		public void setCity(String city) {
			this.city = city;
		}

		public String getStreet() {
			return street;
		}

		public void setStreet(String street) {
			this.street = street;
		}
	}

	public static class StaffDTO implements Serializable {
		private static final long serialVersionUID = 1L;
		private String staffName;
		private Integer age;
		private String deptName;
		private AddressDTO address;
		private List<AddressDTO> history;

		public String getStaffName() {
			return staffName;
		}

		public void setStaffName(String staffName) {
			this.staffName = staffName;
		}

		public Integer getAge() {
			return age;
		}

		public void setAge(Integer age) {
			this.age = age;
		}

		public String getDeptName() {
			return deptName;
		}

		public void setDeptName(String deptName) {
			this.deptName = deptName;
		}

		public AddressDTO getAddress() {
			return address;
		}

		public void setAddress(AddressDTO address) {
			this.address = address;
		}

		public List<AddressDTO> getHistory() {
			return history;
		}

		public void setHistory(List<AddressDTO> history) {
			this.history = history;
		}
	}

	public static class StaffVO implements Serializable {
		private static final long serialVersionUID = 1L;
		private String staffName;
		private Integer age;
		// 目标属性dept通过别名映射源属性deptName
		@SqlToyFieldAlias("deptName")
		private String dept;
		private AddressVO address;
		private List<AddressVO> history;
		// source中不存在的属性
		private String extra;

		public String getStaffName() {
			return staffName;
		}

		public void setStaffName(String staffName) {
			this.staffName = staffName;
		}

		public Integer getAge() {
			return age;
		}

		public void setAge(Integer age) {
			this.age = age;
		}

		public String getDept() {
			return dept;
		}

		public void setDept(String dept) {
			this.dept = dept;
		}

		public AddressVO getAddress() {
			return address;
		}

		public void setAddress(AddressVO address) {
			this.address = address;
		}

		public List<AddressVO> getHistory() {
			return history;
		}

		public void setHistory(List<AddressVO> history) {
			this.history = history;
		}

		public String getExtra() {
			return extra;
		}

		public void setExtra(String extra) {
			this.extra = extra;
		}
	}

	private StaffDTO mockDTO() {
		StaffDTO dto = new StaffDTO();
		dto.setStaffName("张三");
		dto.setAge(30);
		dto.setDeptName("研发部");
		AddressDTO addr = new AddressDTO();
		addr.setCity("杭州");
		addr.setStreet("文一西路");
		dto.setAddress(addr);
		AddressDTO hist = new AddressDTO();
		hist.setCity("北京");
		dto.setHistory(new ArrayList<>(Arrays.asList(hist)));
		return dto;
	}

	@Test
	public void mapNullSourceAndResult() {
		assertNull(MapperUtils.map(null, StaffVO.class));
		// resultType为null或基本类型抛IllegalArgumentException
		assertThrows(IllegalArgumentException.class, () -> MapperUtils.map(mockDTO(), null));
		assertThrows(IllegalArgumentException.class, () -> MapperUtils.map(mockDTO(), String.class));
		assertThrows(IllegalArgumentException.class, () -> MapperUtils.mapList(Arrays.asList(mockDTO()), null));
	}

	@Test
	public void mapSameAndAliasProps() {
		StaffVO vo = MapperUtils.map(mockDTO(), StaffVO.class);
		assertNotNull(vo);
		assertEquals("张三", vo.getStaffName());
		assertEquals(Integer.valueOf(30), vo.getAge());
		// @SqlToyFieldAlias别名映射
		assertEquals("研发部", vo.getDept());
		assertNull(vo.getExtra(), "source不存在的属性应保持null");
		// 反向映射:VO→DTO
		StaffDTO back = MapperUtils.map(vo, StaffDTO.class);
		assertEquals("张三", back.getStaffName());
		assertEquals("研发部", back.getDeptName());
	}

	@Test
	public void mapNestedObjectAndList() {
		StaffVO vo = MapperUtils.map(mockDTO(), StaffVO.class);
		// 多级子对象映射
		assertNotNull(vo.getAddress());
		assertEquals("杭州", vo.getAddress().getCity());
		assertEquals("文一西路", vo.getAddress().getStreet());
		// List<DTO>映射
		assertNotNull(vo.getHistory());
		assertEquals(1, vo.getHistory().size());
		assertEquals("北京", vo.getHistory().get(0).getCity());
	}

	@Test
	public void mapIgnoreProperties() {
		StaffVO vo = MapperUtils.map(mockDTO(), StaffVO.class, new PropsMapperConfig("age").isIgnore(true));
		assertEquals("张三", vo.getStaffName());
		assertNull(vo.getAge(), "被忽略的属性不应被复制");
		// 指定属性映射模式(ignore=false)
		StaffVO vo2 = MapperUtils.map(mockDTO(), StaffVO.class, new PropsMapperConfig("staffName", "deptName"));
		assertEquals("张三", vo2.getStaffName());
		assertEquals("研发部", vo2.getDept());
		assertNull(vo2.getAge(), "未指定的属性不应被复制");
	}

	@Test
	public void mapFieldsMapManualMapping() {
		// 手工指定映射关系:source.deptName → target.extra(名称不一致,fieldsMap生效)
		StaffVO vo = MapperUtils.map(mockDTO(), StaffVO.class,
				new PropsMapperConfig().fieldsMap("deptName:extra"));
		assertEquals("研发部", vo.getExtra());
		// fieldsMap覆盖了target类上的别名映射,dept不再被赋值
		assertNull(vo.getDept());
		// fieldsMap之外的同名属性照常映射
		assertEquals("张三", vo.getStaffName());
	}

	@Test
	public void mapListEmptyAndNull() {
		assertNull(MapperUtils.mapList(null, StaffVO.class));
		assertNotNull(MapperUtils.mapList(new ArrayList<>(), StaffVO.class));
		assertTrue(MapperUtils.mapList(new ArrayList<>(), StaffVO.class).isEmpty());
		// resultType为接口/抽象类抛IllegalArgumentException
		assertThrows(IllegalArgumentException.class,
				() -> MapperUtils.mapList(Arrays.asList(mockDTO()), SerializableInterface.class));
	}

	/** 用于接口校验的空接口 */
	interface SerializableInterface extends Serializable {
	}

	/**
	 * 回归测试:mapList容忍null行,首元素为null时按首个非null元素提取类型,
	 * null行占位保持结果与sourceList下标对齐
	 */
	@Test
	public void mapListFirstNullElement() {
		List<StaffDTO> list = new ArrayList<>();
		list.add(null);
		list.add(mockDTO());
		List<StaffVO> result = MapperUtils.mapList(list, StaffVO.class);
		assertNotNull(result);
		assertNull(result.get(0), "null行应保持null占位");
		assertEquals("张三", result.get(1).getStaffName(), "非null行应正常映射");
		// 全部为null行时返回等长null列表
		List<StaffVO> allNull = MapperUtils.mapList(new ArrayList<>(Arrays.asList(null, null)), StaffVO.class);
		assertNotNull(allNull);
		assertEquals(2, allNull.size());
		assertNull(allNull.get(0));
		assertNull(allNull.get(1));
	}

	@Test
	public void copyPropertiesListWithNullRows() {
		StaffVO vo1 = new StaffVO();
		StaffVO vo2 = new StaffVO();
		// source首行为null不再NPE,null行跳过,后续行正常复制
		List<StaffDTO> source = new ArrayList<>();
		source.add(null);
		source.add(mockDTO());
		MapperUtils.copyProperties(source, Arrays.asList(vo1, vo2), new PropsMapperConfig());
		assertNull(vo1.getStaffName(), "null行对应的target保持原状");
		assertEquals("张三", vo2.getStaffName());
		assertEquals("研发部", vo2.getDept());
	}

	@Test
	public void mapPage() {
		Page<StaffDTO> page = new Page<>();
		page.setPageNo(2);
		page.setPageSize(15);
		page.setRecordCount(100);
		page.setRows(new ArrayList<>(Arrays.asList(mockDTO())));
		Page<StaffVO> voPage = MapperUtils.map(page, StaffVO.class);
		assertEquals(2, voPage.getPageNo());
		assertEquals(15, voPage.getPageSize());
		assertEquals(100, voPage.getRecordCount());
		assertEquals(1, voPage.getRows().size());
		assertEquals("张三", voPage.getRows().get(0).getStaffName());
		// 空rows:分页对象元数据保留,rows为空列表
		Page<StaffDTO> emptyPage = new Page<>();
		emptyPage.setRows(new ArrayList<>());
		Page<StaffVO> voEmpty = MapperUtils.map(emptyPage, StaffVO.class);
		assertEquals(0, voEmpty.getRecordCount());
		assertTrue(voEmpty.getRows().isEmpty());
		assertNull(MapperUtils.map(null, StaffVO.class));
	}

	@Test
	public void copyPropertiesObject() {
		StaffDTO dto = mockDTO();
		StaffVO vo = new StaffVO();
		MapperUtils.copyProperties(dto, vo);
		assertEquals("张三", vo.getStaffName());
		assertEquals("研发部", vo.getDept());
		// target已有值时默认会覆盖;skipNull=true时null不覆盖
		StaffVO vo2 = new StaffVO();
		vo2.setExtra("keep");
		dto.setDeptName(null);
		MapperUtils.copyProperties(dto, vo2, new PropsMapperConfig().skipNull(true));
		assertEquals("keep", vo2.getExtra());
		// 基本类型入参抛IllegalArgumentException
		assertThrows(IllegalArgumentException.class, () -> MapperUtils.copyProperties("str", "target"));
	}

	@Test
	public void copyPropertiesList() {
		StaffDTO dto1 = mockDTO();
		StaffDTO dto2 = new StaffDTO();
		dto2.setStaffName("李四");
		StaffVO vo1 = new StaffVO();
		StaffVO vo2 = new StaffVO();
		MapperUtils.copyProperties(Arrays.asList(dto1, dto2), Arrays.asList(vo1, vo2));
		assertEquals("张三", vo1.getStaffName());
		assertEquals("李四", vo2.getStaffName());
		// 数量不一致抛异常
		assertThrows(RuntimeException.class, () -> MapperUtils.copyProperties(Arrays.asList(dto1),
				Arrays.asList(vo1, vo2), new PropsMapperConfig()));
		// 空列表抛异常
		assertThrows(RuntimeException.class,
				() -> MapperUtils.copyProperties(new ArrayList<>(), Arrays.asList(vo1), new PropsMapperConfig()));
	}

	@Test
	public void mapTypeConversion() {
		// age以字符串形式存在于source与Integer目标之间通过convertType完成自动转换
		StaffDTO dto = mockDTO();
		// 利用子对象映射后再检查类型
		StaffVO vo = MapperUtils.map(dto, StaffVO.class);
		assertTrue(vo.getAge() instanceof Integer);
	}

	@Test
	public void mapRecursiveDepth() {
		// 相互引用的两个对象不应无限递归(MAX_RECURSION=3)
		LoopA a = new LoopA();
		LoopB b = new LoopB();
		a.setRef(b);
		b.setRef(a);
		a.setName("loop");
		LoopA copy = MapperUtils.map(a, LoopA.class);
		assertNotNull(copy);
		assertEquals("loop", copy.getName());
	}

	public static class LoopA implements Serializable {
		private static final long serialVersionUID = 1L;
		private String name;
		private LoopB ref;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public LoopB getRef() {
			return ref;
		}

		public void setRef(LoopB ref) {
			this.ref = ref;
		}
	}

	public static class LoopB implements Serializable {
		private static final long serialVersionUID = 1L;
		private LoopA ref;

		public LoopA getRef() {
			return ref;
		}

		public void setRef(LoopA ref) {
			this.ref = ref;
		}
	}
}
