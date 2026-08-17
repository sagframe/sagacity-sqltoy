package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.model.PropsMapperConfig;

/**
 * 回归测试：(a)copyProperties传null配置不再NPE(兜底变量被误用为原始参数);
 * (b)源集合含null行时保持下标对齐,后续行的属性值不拷贝到错误的目标对象上
 */
public class MapperUtilsCopyPropertiesTest {

	public static class Src {
		private String name;

		public Src(String name) {
			this.name = name;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}
	}

	public static class Tgt {
		private String name;

		public Tgt(String name) {
			this.name = name;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}
	}

	@Test
	public void nullPropsMapperConfigDoesNotThrow() {
		List src = new ArrayList();
		src.add(new Src("a1"));
		List tgt = new ArrayList();
		tgt.add(new Tgt(null));
		// 修复前:253行解引用原始null参数抛NPE
		MapperUtils.copyProperties(src, tgt, (PropsMapperConfig) null);
		assertEquals("a1", ((Tgt) tgt.get(0)).getName());
	}

	@Test
	public void nullSourceRowKeepsTargetAlignment() {
		List src = Arrays.asList(new Src("a1"), null, new Src("b1"));
		Tgt t1 = new Tgt(null);
		Tgt t2 = new Tgt("KEEP");
		Tgt t3 = new Tgt(null);
		List tgt = Arrays.asList(t1, t2, t3);
		// 用真实配置,独立于null配置缺陷验证行对齐
		MapperUtils.copyProperties(src, tgt, new PropsMapperConfig());
		assertEquals("a1", t1.getName());
		// null行的目标不被波及(修复前b1的值被错误拷贝到t2)
		assertEquals("KEEP", t2.getName());
		// 后续行按正确下标对齐(修复前t3不被赋值)
		assertEquals("b1", t3.getName());
	}
}
