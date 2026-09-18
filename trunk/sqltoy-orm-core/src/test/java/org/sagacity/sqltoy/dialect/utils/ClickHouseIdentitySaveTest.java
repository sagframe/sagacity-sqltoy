package org.sagacity.sqltoy.dialect.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.EntityManager;
import org.sagacity.sqltoy.config.annotation.Column;
import org.sagacity.sqltoy.config.annotation.Entity;
import org.sagacity.sqltoy.config.annotation.Id;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.PKStrategy;
import org.sagacity.sqltoy.model.DBProfile;
import org.sagacity.sqltoy.utils.DataSourceUtils;

/**
 * ClickHouse save/saveAll 在identity(不赋主键)下的下标回归。以代码事实为准,结论与报告初判不同:
 * 
 * 1、〈类型数组无错位〉EntityMeta.fieldsArray的排序规则是"计算列→常规列→主键"(主键恒在末位),
 * rejectIdFieldArray是其前缀;SqlUtil.setParamsValue以值数组长度遍历、按同下标取类型,
 * 故类型数组末尾多出的主键类型永远不会被读取——types不需要按reflectColumns过滤;
 * 
 * 2、〈真正会崩的是主键下标回读〉save末尾 `result = fullParamValues[pkIndex]` 的pkIndex按字段视图
 * 计算,identity排除主键后恰等于值数组长度 → 必然ArrayIndexOutOfBounds(修复前本用例即为此失败)
 */
public class ClickHouseIdentitySaveTest {

	/** identity主键(CH不赋主键)+ 两个String列 */
	@Entity(tableName = "t_ch_identity")
	public static class ChIdentityVo implements java.io.Serializable {
		@Id(strategy = "identity")
		@Column(name = "ID")
		private Integer id;
		@Column(name = "NAME")
		private String name;
		@Column(name = "REMARK")
		private String remark;

		public Integer getId() {
			return id;
		}

		public void setId(Integer id) {
			this.id = id;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getRemark() {
			return remark;
		}

		public void setRemark(String remark) {
			this.remark = remark;
		}
	}

	private static final class Capture {
		final List<String> types = new ArrayList<String>();

		Connection connection() {
			return (Connection) Proxy.newProxyInstance(Capture.class.getClassLoader(),
					new Class[] { Connection.class }, (proxy, method, args) -> {
						if ("prepareStatement".equals(method.getName())) {
							return statement();
						}
						return defaultReturn(method.getReturnType());
					});
		}

		PreparedStatement statement() {
			return (PreparedStatement) Proxy.newProxyInstance(Capture.class.getClassLoader(),
					new Class[] { PreparedStatement.class }, (proxy, method, args) -> {
						if ("setNull".equals(method.getName())) {
							types.add("setNull[" + args[0] + "]=" + args[1]);
						}
						return defaultReturn(method.getReturnType());
					});
		}

		private static Object defaultReturn(Class<?> type) {
			if (type == boolean.class) {
				return false;
			}
			if (type == int.class) {
				return 0;
			}
			if (type == long.class) {
				return 0L;
			}
			return null;
		}
	}

	private static EntityMeta entityMeta(SqlToyContext context) throws Exception {
		return new EntityManager().getEntityMeta(context, ChIdentityVo.class);
	}

	private static int indexOf(String[] columns, String name) {
		for (int i = 0; i < columns.length; i++) {
			if (columns[i].equalsIgnoreCase(name)) {
				return i;
			}
		}
		throw new AssertionError("字段未找到:" + name);
	}

	@Test
	public void idFieldIsLastAndRejectIdIsPrefixOfFields() throws Exception {
		EntityMeta meta = entityMeta(new SqlToyContext());
		String[] fields = meta.getFieldsArray(true);
		String[] rejectId = meta.getRejectIdFieldArray(true);
		// fieldsArray排序规则:计算列→常规列→主键,主键恒在末位
		assertEquals("id", fields[fields.length - 1], "fieldsArray应把主键列排在末位");
		// rejectIdFieldArray是fieldsArray的前缀 → 类型数组末尾多出的主键类型不会被setParamsValue读取
		assertEquals(fields.length - 1, rejectId.length);
		for (int i = 0; i < rejectId.length; i++) {
			assertEquals(fields[i], rejectId[i], "rejectIdFieldArray必须是fieldsArray的前缀");
		}
	}

	@Test
	public void saveWithIdentityPkDoesNotReadOutOfRangePkIndex() throws Exception {
		SqlToyContext context = new SqlToyContext();
		EntityMeta meta = entityMeta(context);
		// 两个字段都为null → 每个参数都走setNull(index,type),类型配对可观察
		ChIdentityVo vo = new ChIdentityVo();
		DBProfile profile = new DBProfile("jdbc:clickhouse://host:8123/db", "clickhouse",
				DataSourceUtils.DBType.CLICKHOUSE, "ClickHouse", 24, null, null, false);
		Capture capture = new Capture();
		// 修复前:save末尾 result==null → fullParamValues[pkIndex](pkIndex=2,值数组长度=2)越界抛异常
		ClickHouseDialectUtils.save(context, meta, PKStrategy.IDENTITY,
				"insert into t_ch_identity (NAME,REMARK) values (?,?)", vo, capture.connection(), profile);
		// 参数与类型按同下标正确配对(类型数组末尾的主键类型不被读取)
		String[] fields = meta.getFieldsArray(true);
		Integer[] allTypes = meta.getFieldsTypeArray(true);
		assertTrue(capture.types.contains("setNull[1]=" + allTypes[indexOf(fields, "name")]),
				"参数1(NAME)应绑定String列类型,实际:" + capture.types);
		assertTrue(capture.types.contains("setNull[2]=" + allTypes[indexOf(fields, "remark")]),
				"参数2(REMARK)应绑定String列类型,实际:" + capture.types);
		// 值数组无主键:不回写(保持null)且不越界
		assertNull(vo.getId());
	}
}
