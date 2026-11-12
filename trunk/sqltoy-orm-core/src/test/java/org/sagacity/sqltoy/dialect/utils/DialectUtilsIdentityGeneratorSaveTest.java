package org.sagacity.sqltoy.dialect.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.Date;
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
import org.sagacity.sqltoy.plugins.id.IdGenerator;
import org.sagacity.sqltoy.utils.DataSourceUtils;

/**
 * DialectUtils.save 主键生成分支的越界防护回归。
 * 场景:显式配置 @Id(strategy="identity", generator=...) —— processIdGenerator不会把strategy
 * 改成GENERATOR,故 hasId=true;而identity且不赋主键时值数组已排除主键(reflectColumns=
 * getRejectIdFieldArray,主键恒在fieldsArray末位)→ pkIndex恰等于值数组长度,主键生成分支里
 * 按pkIndex回读会越界。修复:该分支加入 pkIndex < 值数组长度 判定(与文件内result回退处一致)
 */
public class DialectUtilsIdentityGeneratorSaveTest {

	/** 制造identity+generator的实体(自相矛盾配置,守卫针对的就是这类配置不越界) */
	@Entity(tableName = "t_id_gen_vo")
	public static class IdentityGeneratorVo implements java.io.Serializable {
		@Id(strategy = "identity", generator = "org.sagacity.sqltoy.dialect.utils.DialectUtilsIdentityGeneratorSaveTest$FixedIdGenerator")
		@Column(name = "ID")
		private Integer id;
		@Column(name = "NAME")
		private String name;

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
	}

	public static class FixedIdGenerator implements IdGenerator {
		@Override
		public Object getId(String tableName, String signature, String[] relatedColumns, Object[] relatedColValue,
				Date bizDate, String idJavaType, int length, int sequenceSize) {
			return 99;
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

	@Test
	public void generatorBranchDoesNotReadOutOfRangePkIndex() throws Exception {
		SqlToyContext context = new SqlToyContext();
		EntityMeta meta = new EntityManager().getEntityMeta(context, IdentityGeneratorVo.class);
		// 前置确认:identity且不赋主键 → 值数组排除主键,而hasId为true(显式配置了generator)
		assertEquals(1, meta.getRejectIdFieldArray(true).length, "值数组应仅含非主键列");
		assertTrue(meta.hasIdGenerator(), "显式配置generator后hasIdGenerator应为true");
		IdentityGeneratorVo vo = new IdentityGeneratorVo();
		vo.setName(null);
		DBProfile profile = new DBProfile("jdbc:postgresql://host:5432/db", "postgresql",
				DataSourceUtils.DBType.POSTGRESQL, "PostgreSQL", 15, null, null, false);
		Capture capture = new Capture();
		// 修复前:hasId&&isBlank(fullParamValues[pkIndex]) 中 pkIndex=1、值数组长度=1 → 越界抛异常
		DialectUtils.save(context, meta, PKStrategy.IDENTITY, false, "insert into t_id_gen_vo (NAME) values (?)",
				vo, null, null, capture.connection(), profile);
		// 非主键列按自身类型绑定
		assertTrue(capture.types.contains("setNull[1]=" + meta.getFieldsTypeArray(true)[0]),
				"NAME应绑定String列类型,实际:" + capture.types);
		// 主键不在值数组中:不回写
		assertNull(vo.getId());
	}
}
