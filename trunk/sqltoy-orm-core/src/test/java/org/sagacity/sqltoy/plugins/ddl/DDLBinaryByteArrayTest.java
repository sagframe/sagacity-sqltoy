package org.sagacity.sqltoy.plugins.ddl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.EntityManager;
import org.sagacity.sqltoy.config.annotation.Column;
import org.sagacity.sqltoy.config.annotation.Entity;
import org.sagacity.sqltoy.config.annotation.Id;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.model.ColumnMeta;
import org.sagacity.sqltoy.model.TableMeta;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * 回归测试：byte[] 实体字段是二进制语义(bytea/blob/varbinary)，不得被数组组件分派接管；
 * Byte[]/String[] 等真正的数组列语义保持不变。
 */
public class DDLBinaryByteArrayTest {

	@Entity(tableName = "t_binary")
	public static class BinaryEntity {
		@Id
		@Column(name = "id", type = java.sql.Types.VARCHAR)
		private String id;
		/** 显式声明BLOB(等价项目demo AbstractStaffInfo.photo) */
		@Column(name = "photo", type = java.sql.Types.BLOB)
		private byte[] photo;
		/** 未声明type:EntityManager自动映射Types.BINARY */
		@Column(name = "avatar")
		private byte[] avatar;
		/** Boxed字节数组:真正的数组列语义,保持int2[] */
		@Column(name = "nums")
		private Byte[] nums;
		/** 常规数组列语义,不受影响 */
		@Column(name = "tags")
		private String[] tags;

		public String getId() { return id; }
		public void setId(String id) { this.id = id; }
		public byte[] getPhoto() { return photo; }
		public void setPhoto(byte[] photo) { this.photo = photo; }
		public byte[] getAvatar() { return avatar; }
		public void setAvatar(byte[] avatar) { this.avatar = avatar; }
		public Byte[] getNums() { return nums; }
		public void setNums(Byte[] nums) { this.nums = nums; }
		public String[] getTags() { return tags; }
		public void setTags(String[] tags) { this.tags = tags; }
	}

	private static EntityMeta parse() {
		return new EntityManager().parseEntityMeta(new SqlToyContext(), BinaryEntity.class, true, false);
	}

	private static String ddlType(EntityMeta meta, int dbType, String colName) {
		TableMeta tableMeta = DDLUtils.wrapTableMeta(meta, dbType);
		List<ColumnMeta> columns = tableMeta.getColumns();
		for (ColumnMeta column : columns) {
			if (colName.equalsIgnoreCase(column.getColName())) {
				return DDLUtils.convertType(column, dbType);
			}
		}
		return null;
	}

	@Test
	public void byteArrayFieldIsBinaryNotArray() {
		EntityMeta meta = parse();
		// byte[]:不提取组件类型,避免DDL按component=byte分派成int2[]/INT ARRAY/JSON
		assertNull(meta.getFieldMeta("photo").getArrayComponentType(), "byte[](显式BLOB)不得提取数组组件");
		assertNull(meta.getFieldMeta("avatar").getArrayComponentType(), "byte[](自动BINARY)不得提取数组组件");
		// 真正的数组列保持原语义
		assertEquals("java.lang.Byte", meta.getFieldMeta("nums").getArrayComponentType(), "Byte[]保持数组列语义");
		assertEquals("java.lang.String", meta.getFieldMeta("tags").getArrayComponentType(), "String[]保持数组列语义");

		// 二进制列类型必须回归BYTEA/BLOB/VARBINARY(MAX),不能是int2[]/INT ARRAY/JSON/CLOB
		assertEquals("BYTEA", ddlType(meta, DBType.POSTGRESQL, "photo"), "pg photo→BYTEA");
		assertEquals("BYTEA", ddlType(meta, DBType.KINGBASE, "photo"), "kingbase photo→BYTEA");
		assertEquals("BYTEA", ddlType(meta, DBType.POSTGRESQL, "avatar"), "pg avatar→BYTEA");
		assertEquals("BLOB", ddlType(meta, DBType.MYSQL, "photo"), "mysql photo→BLOB");
		assertEquals("BLOB", ddlType(meta, DBType.ORACLE, "photo"), "oracle photo→BLOB");
		assertEquals("BLOB", ddlType(meta, DBType.H2, "photo"), "h2 photo→BLOB");
		assertEquals("VARBINARY(MAX)", ddlType(meta, DBType.SQLSERVER, "photo"), "sqlserver photo→VARBINARY(MAX)");
		assertEquals("BLOB", ddlType(meta, DBType.DB2, "photo"), "db2 photo→BLOB");
		// 数组列正向能力不被误伤
		assertEquals("int2[]", ddlType(meta, DBType.POSTGRESQL, "nums"), "pg Byte[]→int2[]");
		assertEquals("varchar[]", ddlType(meta, DBType.POSTGRESQL, "tags"), "pg String[]→varchar[]");
	}

	@Test
	public void manualBinaryColumnMetaWinsOverArrayComponent() {
		// 反向工程/手工构造链路:ColumnMeta可能同时带BLOB类型与组件类型,二进制语义优先(兜底)
		ColumnMeta manual = new ColumnMeta();
		manual.setDataType(java.sql.Types.BLOB);
		manual.setArrayComponentType("byte");
		manual.setNativeType("");
		assertEquals("BYTEA", DDLUtils.convertType(manual, DBType.POSTGRESQL), "手工BLOB+byte组件→BYTEA");
		assertEquals("BLOB", DDLUtils.convertType(manual, DBType.MYSQL), "手工BLOB+byte组件→BLOB");
		assertEquals("VARBINARY(MAX)", DDLUtils.convertType(manual, DBType.SQLSERVER), "手工BLOB+byte组件→VARBINARY(MAX)");
	}
}
