package org.sagacity.sqltoy.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

/**
 * 回归测试：(17)translate的alias-name数量少于columns时越界改为回退列名;
 * (19)@OneToMany指向非@Entity VO时给出明确配置错误而非裸NPE
 */
public class EdgeBatch4Test {

	private static SqlToyConfig parseTranslateXml(String translatesXml) throws Exception {
		javax.xml.parsers.DocumentBuilder builder = javax.xml.parsers.DocumentBuilderFactory.newInstance()
				.newDocumentBuilder();
		Document doc = builder.parse(new java.io.ByteArrayInputStream(
				("<root>" + translatesXml + "</root>").getBytes("UTF-8")));
		NodeList translates = doc.getDocumentElement().getElementsByTagName("translate");
		SqlToyConfig config = new SqlToyConfig("mysql");
		SqlXMLConfigParse.parseTranslate(config, translates);
		return config;
	}

	@Test
	public void aliasFewerThanColumnsFallsBackToColumnName() throws Exception {
		// columns两列,alias-name只配一个:修复前第二个translate的aliasNames[1]越界
		SqlToyConfig config = parseTranslateXml(
				"<translate cache=\"cacheA\" columns=\"a,b\" alias-name=\"first\"/>");
		// 两列都成功解析,第二列alias回退为列名b
		assertTrue(config.getTranslateMap().containsKey("a"));
		assertTrue(config.getTranslateMap().containsKey("b"));
		assertEquals("first", config.getTranslateMap().get("a").translates[0].getExtend().alias);
		assertEquals("b", config.getTranslateMap().get("b").translates[0].getExtend().alias);
	}

	@Test
	public void aliasMatchingColumnsStillWorks() throws Exception {
		SqlToyConfig config = parseTranslateXml(
				"<translate cache=\"cacheA\" columns=\"a,b\" alias-name=\"x,y\"/>");
		assertEquals("x", config.getTranslateMap().get("a").translates[0].getExtend().alias);
		assertEquals("y", config.getTranslateMap().get("b").translates[0].getExtend().alias);
	}

	// (19)@OneToMany指向非实体VO
	public static class PlainVo {
		private String name;

		public String getName() {
			return name;
		}
	}

	@org.sagacity.sqltoy.config.annotation.Entity(tableName = "cascade_main")
	public static class MainEntity {
		@org.sagacity.sqltoy.config.annotation.OneToMany(fields = "id", mappedFields = "mainId")
		private java.util.List<PlainVo> items = new java.util.ArrayList<PlainVo>();

		@org.sagacity.sqltoy.config.annotation.Id
		private String id;

		public java.util.List<PlainVo> getItems() {
			return items;
		}

		public String getId() {
			return id;
		}
	}

	@Test
	public void cascadeToNonEntityVoGivesConfigError() {
		SqlToyContext context = new SqlToyContext();
		EntityManager entityManager = new EntityManager();
		// 修复前:subTableMeta为null,在长度校验或字段校验处裸NPE
		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
				() -> entityManager.parseEntityMeta(context, MainEntity.class, true, false));
		assertTrue(ex.getMessage().contains("PlainVo"), "实际:" + ex.getMessage());
		assertTrue(ex.getMessage().contains("不是@Entity实体"), "实际:" + ex.getMessage());
	}

	@Test
	public void normalEntityCascadeStillParses() {
		SqlToyContext context = new SqlToyContext();
		EntityManager entityManager = new EntityManager();
		// 自级联(PlainVo非实体,但用StaffInfo自身做子表验证正常路径不误伤)
		assertDoesNotThrow(() -> entityManager.parseEntityMeta(context,
				org.sagacity.sqltoy.demo.domain.StaffInfo.class, true, false));
	}
}
