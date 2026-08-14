package org.sagacity.sqltoy.plugins.ddl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.FieldMeta;
import org.sagacity.sqltoy.model.TableMeta;
import org.sagacity.sqltoy.plugins.ddl.impl.MySqlDDLGenerator;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * 回归测试：注释统一用标准SQL的''转义单引号(Oracle/PG合法,MySQL亦支持), 修复前表注释单引号不转义(COMMENT
 * ON语句直接断裂)、列注释用\'(Oracle/PG非法)两条路径互不一致; MySQL生成器对反斜杠做MySQL特有的\\转义
 */
public class DDLCommentEscapeTest {

	private static EntityMeta entityWithComments(String tableComment, String columnComment) {
		EntityMeta entityMeta = new EntityMeta();
		entityMeta.setTableName("sys_user");
		entityMeta.setTableComment(tableComment);
		FieldMeta fieldMeta = new FieldMeta();
		fieldMeta.setFieldName("userName");
		fieldMeta.setColumnName("user_name");
		fieldMeta.setComments(columnComment);
		entityMeta.addFieldMeta(fieldMeta);
		return entityMeta;
	}

	@Test
	public void commentOnUsesStandardQuoteDoubling() {
		TableMeta tableMeta = DDLUtils.wrapTableMeta(entityWithComments("it's a \"test\" table", "user's name"),
				DBType.ORACLE);
		StringBuilder sql = new StringBuilder();
		DDLUtils.wrapTableAndColumnsComment(tableMeta, "lower", DBType.ORACLE, sql);
		String result = sql.toString();
		// 修复前:表注释输出IS 'it's a...'(SQL断裂),列注释输出user\'s(Oracle/PG非法)
		assertTrue(result.contains("IS 'it''s a \"test\" table'"), "表注释应输出标准''转义");
		assertTrue(result.contains("IS 'user''s name'"), "列注释应输出标准''转义");
		assertFalse(result.contains("\\'"), "不应出现MySQL风格的\\'转义");
	}

	@Test
	public void mySqlGeneratorEscapesBackslashKeepsQuoteDoubling() {
		// 完整管线:wrapTableMeta完成''转义,MySQL生成器再做反斜杠\\转义
		TableMeta tableMeta = DDLUtils.wrapTableMeta(entityWithComments("tb's \\cmt", "path's \\dir"), DBType.MYSQL);
		String sql = new MySqlDDLGenerator().createTableSql(tableMeta, null, "lower", DBType.MYSQL);
		assertTrue(sql.contains("COMMENT 'path''s \\\\dir'"), "MySQL列注释应为''+\\\\,实际:" + sql);
		assertTrue(sql.contains("COMMENT 'tb''s \\\\cmt'"), "MySQL表注释应为''+\\\\,实际:" + sql);
	}
}
