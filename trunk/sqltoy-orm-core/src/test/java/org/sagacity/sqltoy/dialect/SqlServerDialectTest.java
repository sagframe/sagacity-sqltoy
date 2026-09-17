package org.sagacity.sqltoy.dialect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.FieldMeta;
import org.sagacity.sqltoy.config.model.NotGeneratedColMeta;
import org.sagacity.sqltoy.config.model.PKStrategy;
import org.sagacity.sqltoy.dialect.utils.DialectExtUtils;
import org.sagacity.sqltoy.dialect.utils.DialectUtils;
import org.sagacity.sqltoy.dialect.utils.SqlServerDialectUtils;
import org.sagacity.sqltoy.model.DBProfile;
import org.sagacity.sqltoy.model.JdbcTypes;
import org.sagacity.sqltoy.model.LockMode;
import org.sagacity.sqltoy.utils.DataSourceUtils;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * SqlServer 方言相关逻辑的单元测试
 */
public class SqlServerDialectTest {
	private static final Pattern ORDER_BY = Pattern.compile("(?i)\\Worder\\s*by\\W");

	/**
	 * dbType直调场景的最小连接档案(DialectExtUtils同款构造),saveOrUpdate/mergeIgnore生成用
	 */
	private static DBProfile sqlServerProfile() {
		return new DBProfile(null, DataSourceUtils.getDialect(DBType.SQLSERVER), DBType.SQLSERVER, null, 0, null,
				null, false);
	}

	@Test
	public void testPageSql() {
		String realSql = "select top partation( order by) from (select from table ) t1 where t1.name=?";
		StringBuilder sql = new StringBuilder(realSql);
		int orderByIndex = StringUtil.matchIndex(realSql, ORDER_BY);
		if (orderByIndex > 0) {
			String clearSql = DialectUtils.clearDisturbSql(realSql);
			orderByIndex = StringUtil.matchIndex(clearSql, ORDER_BY);
		}
		if (orderByIndex < 0) {
			sql.append(" order by NEWID() ");
		}
		System.err.println(sql.toString());
	}

	// ======================== lockSql tests ========================

	/**
	 * 无别名表名 + UPGRADE_NOWAIT(nowait语义是拿不到锁立即报错,不能用readpast静默跳过被锁行)
	 */
	@Test
	public void testLockSql_tableWithoutAlias_nowait() {
		String sql = "select * from my_table";
		String result = SqlServerDialectUtils.lockSql(sql, null, LockMode.UPGRADE_NOWAIT);
		assertNotNull(result);
		assertTrue(result.toLowerCase().contains("with (rowlock xlock nowait)"),
				"should contain rowlock xlock nowait hint, got: " + result);
	}

	/**
	 * 无别名表名 + UPGRADE_SKIPLOCK
	 */
	@Test
	public void testLockSql_tableWithoutAlias_skiplock() {
		String sql = "select * from my_table";
		String result = SqlServerDialectUtils.lockSql(sql, null, LockMode.UPGRADE_SKIPLOCK);
		assertNotNull(result);
		assertTrue(result.toLowerCase().contains("with (rowlock readpast)"),
				"should contain rowlock readpast hint, got: " + result);
	}

	/**
	 * 表别名 + where -- 走 where 分支
	 */
	@Test
	public void testLockSql_aliasWithWhere() {
		String sql = "select * from my_table t where t.id=?";
		String result = SqlServerDialectUtils.lockSql(sql, null, LockMode.UPGRADE);
		assertNotNull(result);
		assertTrue(result.toLowerCase().contains("t with (rowlock xlock)"),
				"lock hint should be after alias 't', got: " + result);
		System.err.println("testLockSql_aliasWithWhere => " + result);
	}

	/**
	 * 表别名使用 AS 关键字 -- 走 as 分支
	 */
	@Test
	public void testLockSql_aliasWithAs() {
		String sql = "select * from my_table as t where t.id=?";
		String result = SqlServerDialectUtils.lockSql(sql, null, LockMode.UPGRADE);
		assertNotNull(result);
		assertTrue(result.toLowerCase().contains("t with (rowlock xlock)"),
				"lock hint should be after 'as t', got: " + result);
		System.err.println("testLockSql_aliasWithAs => " + result);
	}

	/**
	 * 多表逗号分隔 -- 走 , 分支
	 */
	@Test
	public void testLockSql_aliasWithComma() {
		String sql = "select * from my_table t, other_table o where t.id=o.id";
		String result = SqlServerDialectUtils.lockSql(sql, null, LockMode.UPGRADE);
		assertNotNull(result);
		assertTrue(result.toLowerCase().contains("with (rowlock xlock)"), "should contain lock hint, got: " + result);
		System.err.println("testLockSql_aliasWithComma => " + result);
	}

	/**
	 * inner join -- 走 join 分支
	 */
	@Test
	public void testLockSql_aliasWithJoin() {
		String sql = "select * from my_table t inner join other_table o on t.id=o.id";
		String result = SqlServerDialectUtils.lockSql(sql, null, LockMode.UPGRADE);
		assertNotNull(result);
		assertTrue(result.toLowerCase().contains("with (rowlock xlock)"), "should contain lock hint, got: " + result);
		System.err.println("testLockSql_aliasWithJoin => " + result);
	}

	/**
	 * null lockMode 应直接返回原始SQL
	 */
	@Test
	public void testLockSql_nullLockMode() {
		String sql = "select * from my_table where id=?";
		String result = SqlServerDialectUtils.lockSql(sql, null, null);
		assertEquals(sql, result, "null lockMode should return original sql");
	}

	/**
	 * SQL 已包含 lock hint 应直接返回原始SQL
	 */
	@Test
	public void testLockSql_alreadyHasLock() {
		String sql = "select * from my_table with (rowlock xlock) where id=?";
		String result = SqlServerDialectUtils.lockSql(sql, null, LockMode.UPGRADE);
		assertEquals(sql, result, "already-locked sql should return as-is");
	}

	/**
	 * 指定 tableName 参数
	 */
	@Test
	public void testLockSql_explicitTableName() {
		String sql = "select * from my_table where id=?";
		String result = SqlServerDialectUtils.lockSql(sql, "my_table", LockMode.UPGRADE);
		assertNotNull(result);
		assertTrue(result.toLowerCase().contains("with (rowlock xlock)"), "should contain lock hint, got: " + result);
		System.err.println("testLockSql_explicitTableName => " + result);
	}

	/**
	 * 带列名的复杂SQL
	 */
	@Test
	public void testLockSql_complexSelect() {
		String sql = "select t.id, t.name, t.create_time from my_table t where t.status=? and t.type=?";
		String result = SqlServerDialectUtils.lockSql(sql, null, LockMode.UPGRADE);
		assertNotNull(result);
		assertTrue(result.toLowerCase().contains("with (rowlock xlock)"),
				"complex select should contain lock hint, got: " + result);
		System.err.println("testLockSql_complexSelect => " + result);
	}

	// ======================== saveOrUpdate/saveIgnoreExist sql generation tests ========================

	/**
	 * timestamp(rowversion)列应从merge using select中排除,保证占位符数量与实际绑定参数(绑定时跳过rowversion)一致
	 * (update 2026-9-7 统一由DialectUtils.getSaveOrUpdateSql生成,rowversion排除按dbType=sqlserver门控)
	 */
	@Test
	public void testSaveOrUpdateSql_rowversionExcluded() {
		EntityMeta meta = buildEntityMeta("t_foo_rv", true, false, true);
		String sql = DialectUtils.getSaveOrUpdateSql(null, null, sqlServerProfile(), meta, PKStrategy.IDENTITY, null,
				null, "@mySeqVariable", false, null);
		String lowerSql = sql.toLowerCase();
		assertTrue(!lowerSql.contains("? as ver"), "rowversion column should be excluded from using select, got: " + sql);
		// name、id两列参与using select(identity且不允许手工赋值时insert部分省略id列)
		assertEquals(2, countPlaceholders(sql), "placeholder count should match bindable params(name,id), got: " + sql);
		System.err.println("testSaveOrUpdateSql_rowversionExcluded => " + sql);
	}

	/**
	 * geometry类型列在using select子查询中应显式cast(update 2026-9-7 统一由DialectUtils.getSaveOrUpdateSql生成);
	 * vector列按实测结论使用裸?(cast(? as VECTOR)缺维度报错)
	 */
	@Test
	public void testSaveOrUpdateSql_geometryCast() {
		EntityMeta meta = buildEntityMeta("t_foo_geo", false, true, true);
		String sql = DialectUtils.getSaveOrUpdateSql(null, null, sqlServerProfile(), meta, PKStrategy.ASSIGN, null,
				null, "@mySeqVariable", true, null);
		String lowerSql = sql.toLowerCase();
		assertTrue(lowerSql.contains("cast(? as geometry)"), "geometry column should be cast, got: " + sql);
		assertEquals(3, countPlaceholders(sql), "placeholder count should match bindable params(name,geo,id), got: " + sql);
		System.err.println("testSaveOrUpdateSql_geometryCast => " + sql);
	}

	/**
	 * 无主键实体退化成insert语句(update 2026-9-7 统一走DialectExtUtils.generateInsertSql,rowversion排除已下沉),
	 * rowversion列不参与(insert语句与绑定参数一致,修复前参数数量多出rowversion导致异常)
	 */
	@Test
	public void testSaveOrUpdateSql_noPkRowversionConsistent() {
		EntityMeta meta = buildEntityMeta("t_foo_nopk", true, false, false);
		String sql = DialectUtils.getSaveOrUpdateSql(null, null, sqlServerProfile(), meta, null, null, null,
				"@mySeqVariable", true, null);
		String lowerSql = sql.toLowerCase();
		assertTrue(lowerSql.startsWith("insert into"), "no-pk entity should degrade to insert sql, got: " + sql);
		assertTrue(!lowerSql.contains("ver"), "rowversion column should not appear in insert sql, got: " + sql);
		assertEquals(1, countPlaceholders(sql), "placeholder count should match bindable params(name), got: " + sql);
		System.err.println("testSaveOrUpdateSql_noPkRowversionConsistent => " + sql);
	}

	/**
	 * saveAllIgnoreExist的merge语句同样排除rowversion列并支持geometry cast(绑定走batchUpdateForPOJO本就跳过rowversion参数)
	 * (update 2026-9-7 统一由DialectExtUtils.mergeIgnore生成)
	 */
	@Test
	public void testSaveIgnoreExistSql_rowversionExcludedAndCast() {
		EntityMeta meta = buildEntityMeta("t_foo_ig", true, true, true);
		String sql = DialectUtils.mergeIgnore(null, sqlServerProfile(), meta, PKStrategy.ASSIGN, null,
				"@mySeqVariable", true, null);
		String lowerSql = sql.toLowerCase();
		assertTrue(!lowerSql.contains("? as ver"), "rowversion column should be excluded from using select, got: " + sql);
		assertTrue(lowerSql.contains("cast(? as geometry)"), "geometry column should be cast, got: " + sql);
		assertEquals(3, countPlaceholders(sql), "placeholder count should match bindable params(name,geo,id), got: " + sql);
		System.err.println("testSaveIgnoreExistSql_rowversionExcludedAndCast => " + sql);
	}

	/**
	 * rowversion列排在第一个非主键字段时,merge的insert列清单不能出现前置逗号
	 * (meter需在timestamp排除块内自增,与getSaveOrUpdateSql的对应逻辑保持一致)
	 */
	@Test
	public void testSaveIgnoreExistSql_rowversionFirstNoLeadingComma() {
		EntityMeta meta = new EntityMeta();
		meta.setEntityClass(getClass());
		meta.setTableName("t_foo_rvfirst");
		meta.addFieldMeta(new FieldMeta("ver", "ver", null, null, java.sql.Types.TIMESTAMP, false, false, 0, 0, 0));
		meta.addFieldMeta(new FieldMeta("name", "name", null, null, java.sql.Types.VARCHAR, true, false, 50, 0, 0));
		meta.addFieldMeta(new FieldMeta("id", "id", null, null, java.sql.Types.BIGINT, false, false, 19, 0, 0));
		String[] fieldsArray = { "ver", "name", "id" };
		String[] rejectIdFields = { "ver", "name" };
		meta.setIdArray(new String[] { "id" });
		meta.setFieldsArray(fieldsArray);
		meta.setRejectIdFieldArray(rejectIdFields);
		NotGeneratedColMeta notGenerated = new NotGeneratedColMeta();
		notGenerated.setFieldsArray(fieldsArray);
		notGenerated.setRejectIdFieldArray(rejectIdFields);
		meta.setNotGeneratedColMeta(notGenerated);
		String sql = DialectUtils.mergeIgnore(null, sqlServerProfile(), meta, PKStrategy.ASSIGN, null,
				"@mySeqVariable", true, "t_foo_rvfirst");
		String lowerSql = sql.toLowerCase();
		// 传tableName参数同时避免与其它用例命中同一个静态sql缓存key(缓存key由class+tableName+dbType+策略构成)
		assertTrue(lowerSql.contains("insert into t_foo_rvfirst") || lowerSql.contains("merge into t_foo_rvfirst"),
				"sql should render the passed tableName, got: " + sql);
		assertTrue(lowerSql.contains("insert  (name,id)"), "insert columns should be (name,id), got: " + sql);
		assertTrue(!lowerSql.contains("(,") && !lowerSql.contains(",)"),
				"no leading/trailing comma in column lists, got: " + sql);
		System.err.println("testSaveIgnoreExistSql_rowversionFirstNoLeadingComma => " + sql);
	}

	// ======================== offset keyword detection ========================

	/**
	 * getRandomResult派生表"内层已含offset不追加offset 0 rows"的判定:
	 * update 2026-9-17 由contains("offset")改为词边界+字面量掩码判定,误判会漏加
	 * offset 0 rows致mssql报ORDER BY clause is invalid in derived tables错误
	 */
	@Test
	public void testHasOffsetKeyWord() {
		// 真实offset子句命中(大小写不敏感,@参数相邻不影响词边界)
		assertTrue(SqlServerDialectUtils.hasOffsetKeyWord("select name from t order by id offset 3 rows", false),
				"小写offset子句应命中");
		assertTrue(SqlServerDialectUtils.hasOffsetKeyWord("select name from t ORDER BY id OFFSET 0 ROWS", false),
				"大写OFFSET子句应命中");
		assertTrue(SqlServerDialectUtils.hasOffsetKeyWord(
				"select name from t order by id offset @off rows fetch next 1 rows only", false),
				"offset后跟@参数应命中");
		// 标识符子串不算(rowoffset/offsetflag):修复前contains误判为已含offset
		assertTrue(!SqlServerDialectUtils.hasOffsetKeyWord("select rowoffset, t.offsetflag from t order by id", false),
				"标识符含offset子串不应命中");
		// 字面量内的offset不算:掩码后匹配,修复前contains误判
		assertTrue(!SqlServerDialectUtils.hasOffsetKeyWord("select 'offset' from t order by id", false),
				"字面量内offset不应命中");
		assertTrue(!SqlServerDialectUtils.hasOffsetKeyWord("select * from t where remark='offset flag'", false),
				"条件字面量内offset不应命中");
		// 真实offset与字面量并存仍命中
		assertTrue(SqlServerDialectUtils.hasOffsetKeyWord("select 'offset' from t order by id offset 1 rows", false),
				"字面量与真实offset并存应命中");
		// backslashEscape差异:规则一致(\'不终结字面量,内容整体掩码)时offset被掩住;
		// 标准规则(false)下'a\'在反斜杠后终结字面量,offset落在字面量外参与判定(行为记录,
		// 该输入本身是mysql语法sql,按标准规则解析属垃圾进垃圾出)
		assertTrue(!SqlServerDialectUtils.hasOffsetKeyWord("select 'a\\'b offset c' from t order by id", true),
				"mysql规则下\\'不终结字面量,offset应被掩住");
		assertTrue(SqlServerDialectUtils.hasOffsetKeyWord("select 'a\\'b offset c' from t order by id", false),
				"标准规则下字面量在\\'后终结,offset参与判定");
		// 空值安全
		assertTrue(!SqlServerDialectUtils.hasOffsetKeyWord(null, false), "null sql不应命中");
		assertTrue(!SqlServerDialectUtils.hasOffsetKeyWord("", false), "空sql不应命中");
	}

	private EntityMeta buildEntityMeta(String tableName, boolean withRowversion, boolean withGeometry,
			boolean withId) {
		EntityMeta meta = new EntityMeta();
		// getCacheKey依赖entityClass名称
		meta.setEntityClass(getClass());
		meta.setTableName(tableName);
		meta.addFieldMeta(new FieldMeta("name", "name", null, null, java.sql.Types.VARCHAR, true, false, 50, 0, 0));
		if (withRowversion) {
			meta.addFieldMeta(new FieldMeta("ver", "ver", null, null, java.sql.Types.TIMESTAMP, false, false, 0, 0, 0));
		}
		if (withGeometry) {
			meta.addFieldMeta(new FieldMeta("geo", "geo", null, null, JdbcTypes.GEOMETRY, true, false, 0, 0, 0));
		}
		List<String> fields = new ArrayList<String>();
		fields.add("name");
		if (withRowversion) {
			fields.add("ver");
		}
		if (withGeometry) {
			fields.add("geo");
		}
		String[] rejectIdFields;
		if (withId) {
			meta.addFieldMeta(new FieldMeta("id", "id", null, null, java.sql.Types.BIGINT, false, false, 19, 0, 0));
			fields.add("id");
			meta.setIdArray(new String[] { "id" });
			rejectIdFields = fields.subList(0, fields.size() - 1).toArray(new String[0]);
			meta.setRejectIdFieldArray(rejectIdFields);
		} else {
			rejectIdFields = fields.toArray(new String[0]);
		}
		String[] fieldsArray = fields.toArray(new String[0]);
		meta.setFieldsArray(fieldsArray);
		// getFieldsArray(true)/getRejectIdFieldArray(true)取自notGeneratedColMeta
		NotGeneratedColMeta notGenerated = new NotGeneratedColMeta();
		notGenerated.setFieldsArray(fieldsArray);
		notGenerated.setRejectIdFieldArray(rejectIdFields);
		meta.setNotGeneratedColMeta(notGenerated);
		return meta;
	}

	private int countPlaceholders(String sql) {
		return sql.length() - sql.replace("?", "").length();
	}
}
