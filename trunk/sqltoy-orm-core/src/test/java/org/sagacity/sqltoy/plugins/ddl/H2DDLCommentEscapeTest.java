package org.sagacity.sqltoy.plugins.ddl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.FieldMeta;
import org.sagacity.sqltoy.model.TableMeta;
import org.sagacity.sqltoy.plugins.ddl.impl.H2DDLGenerator;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * H2生成器对注释/表备注中反斜杠与引号的剔除:原实现为3次replaceAll(正则),
 * 改为String.replace(字面量)后输出必须逐字符一致
 */
public class H2DDLCommentEscapeTest {

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
	public void backslashAndQuotesAreStrippedFromComments() {
		TableMeta tableMeta = DDLUtils.wrapTableMeta(entityWithComments("tb's \\cmt \"x\"", "path's \\dir \"y\""),
				DBType.H2);
		String sql = new H2DDLGenerator().createTableSql(tableMeta, null, "lower", DBType.H2);
		// 列注释:反斜杠、单双引号(含wrapTableMeta做的单引号加倍)全部剔除
		assertTrue(sql.contains("COMMENT 'paths dir y'"), "列注释应剔除反斜杠与引号,实际:" + sql);
		assertTrue(sql.contains("IS 'tbs cmt x'"), "表备注应剔除反斜杠与引号,实际:" + sql);
		assertFalse(sql.contains("\\"), "不应残留反斜杠,实际:" + sql);
	}
}
