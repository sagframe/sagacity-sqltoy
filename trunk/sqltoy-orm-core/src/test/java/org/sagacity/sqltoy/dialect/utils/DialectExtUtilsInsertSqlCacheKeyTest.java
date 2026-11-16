package org.sagacity.sqltoy.dialect.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.EntityManager;
import org.sagacity.sqltoy.config.annotation.Column;
import org.sagacity.sqltoy.config.annotation.Entity;
import org.sagacity.sqltoy.config.annotation.Id;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.PKStrategy;
import org.sagacity.sqltoy.model.DBProfile;
import org.sagacity.sqltoy.model.IgnoreCaseSet;
import org.sagacity.sqltoy.plugins.IUnifyFieldsHandler;
import org.sagacity.sqltoy.utils.DataSourceUtils;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * 回归:insert/insertIgnore/mergeIgnore的静态sql缓存key必须覆盖"生成结果所依赖的全部维度"。
 * 修复前key只有实体类+表名+dbType+主键策略+rowversion,而sql文本还依赖每SqlToyContext一份的
 * IUnifyFieldsHandler(createSqlTimeFields/forceUpdateFields)与DBProfile的真实库事实
 * (isOceanBase/DB2 majorVersion/hasGseSchema),导致后一个上下文或数据源串用先落地的sql
 */
public class DialectExtUtilsInsertSqlCacheKeyTest {

	/** 可配置时间字段集合的统一字段处理器,模拟不同SqlToyContext的配置差异 */
	public static class TimeFieldHandler implements IUnifyFieldsHandler {
		private final IgnoreCaseSet createSqlTimeFields = new IgnoreCaseSet();
		private final IgnoreCaseSet forceUpdateFields = new IgnoreCaseSet();

		public TimeFieldHandler(String[] createFields, String[] forceFields) {
			if (createFields != null) {
				createSqlTimeFields.addAll(Arrays.asList(createFields));
			}
			if (forceFields != null) {
				forceUpdateFields.addAll(Arrays.asList(forceFields));
			}
		}

		@Override
		public IgnoreCaseSet createSqlTimeFields() {
			return createSqlTimeFields;
		}

		@Override
		public IgnoreCaseSet forceUpdateFields() {
			return forceUpdateFields;
		}
	}

	@Entity(tableName = "t_insert_sql_cache_key")
	public static class InsertCacheEntity {
		@Id(strategy = "assign")
		@Column(name = "ID")
		private String id;

		@Column(name = "CREATE_TIME")
		private java.sql.Timestamp createTime;

		@Column(name = "NAME")
		private String name;
	}

	private static EntityMeta entityMeta() {
		return new EntityManager().getEntityMeta(new SqlToyContext(), InsertCacheEntity.class);
	}

	private static DBProfile profile(int dbType, String dialect, String productName, int majorVersion,
			Boolean hasGseSchema) {
		return new DBProfile("jdbc:" + dialect + "://127.0.0.1:1/db", dialect, dbType, productName, majorVersion, null,
				hasGseSchema, false);
	}

	private static DBProfile oracleProfile() {
		return profile(DBType.ORACLE, DataSourceUtils.getDialect(DBType.ORACLE), "Oracle", 19, null);
	}

	@Test
	public void handlerTimeFieldsMustChangeInsertSql() {
		EntityMeta entityMeta = entityMeta();
		DBProfile profile = oracleProfile();
		// 上下文A:createTime取数据库时间
		String sqlWithDbTime = DialectExtUtils.generateInsertSql(new TimeFieldHandler(new String[] { "createTime" }, null),
				profile, entityMeta, PKStrategy.ASSIGN, null, true, null);
		// 上下文B:无统一时间字段
		String sqlWithoutDbTime = DialectExtUtils.generateInsertSql(new TimeFieldHandler(null, null), profile, entityMeta,
				PKStrategy.ASSIGN, null, true, null);
		assertNotNull(sqlWithDbTime);
		assertNotNull(sqlWithoutDbTime);
		assertTrue(sqlWithDbTime.contains("current_timestamp"), "取库时间的sql应含current_timestamp:" + sqlWithDbTime);
		assertFalse(sqlWithoutDbTime.contains("current_timestamp"),
				"未配置统一时间字段的上下文不得复用取库时间的sql:" + sqlWithoutDbTime);
		assertNotEquals(sqlWithDbTime, sqlWithoutDbTime, "insert sql静态缓存key必须包含unifyFieldsHandler维度");
	}

	@Test
	public void sameHandlerStillHitsCache() {
		EntityMeta entityMeta = entityMeta();
		DBProfile profile = oracleProfile();
		TimeFieldHandler handler = new TimeFieldHandler(new String[] { "createTime" }, null);
		String first = DialectExtUtils.generateInsertSql(handler, profile, entityMeta, PKStrategy.ASSIGN, null, true,
				null);
		String second = DialectExtUtils.generateInsertSql(handler, profile, entityMeta, PKStrategy.ASSIGN, null, true,
				null);
		// 同配置仍走缓存(返回同一sql实例),补维度不得把缓存整体废掉
		assertSame(first, second);
	}

	@Test
	public void forceUpdateFieldsMustChangeCacheKey() {
		EntityMeta entityMeta = entityMeta();
		DBProfile profile = oracleProfile();
		String key1 = DialectExtUtils.getCacheKey(entityMeta, null, profile, PKStrategy.ASSIGN,
				new TimeFieldHandler(new String[] { "createTime" }, null));
		String key2 = DialectExtUtils.getCacheKey(entityMeta, null, profile, PKStrategy.ASSIGN,
				new TimeFieldHandler(new String[] { "createTime" }, new String[] { "createTime" }));
		// mergeIgnore的sql文本受forceUpdateFields影响,key必须区分
		assertNotEquals(key1, key2);
	}

	@Test
	public void profileFactsMustChangeCacheKey() {
		EntityMeta entityMeta = entityMeta();
		TimeFieldHandler handler = new TimeFieldHandler(null, null);
		// 同为mysql方言配置:真实MySQL与OceanBase的向量列sql不同
		String mysqlKey = DialectExtUtils.getCacheKey(entityMeta, null,
				profile(DBType.MYSQL, DataSourceUtils.getDialect(DBType.MYSQL), "MySQL", 8, null), PKStrategy.ASSIGN,
				handler);
		String oceanBaseKey = DialectExtUtils.getCacheKey(entityMeta, null,
				profile(DBType.MYSQL, DataSourceUtils.getDialect(DBType.MYSQL), "OceanBase_CE", 0, null),
				PKStrategy.ASSIGN, handler);
		assertNotEquals(mysqlKey, oceanBaseKey, "isOceanBase必须参与缓存key");
		// DB2 11.5(GSE)与12.1(内置空间)空间列sql不同
		String db2v11Key = DialectExtUtils.getCacheKey(entityMeta, null,
				profile(DBType.DB2, DataSourceUtils.getDialect(DBType.DB2), "DB2", 11, Boolean.TRUE),
				PKStrategy.ASSIGN, handler);
		String db2v12Key = DialectExtUtils.getCacheKey(entityMeta, null,
				profile(DBType.DB2, DataSourceUtils.getDialect(DBType.DB2), "DB2", 12, Boolean.TRUE),
				PKStrategy.ASSIGN, handler);
		String db2NoGseKey = DialectExtUtils.getCacheKey(entityMeta, null,
				profile(DBType.DB2, DataSourceUtils.getDialect(DBType.DB2), "DB2", 11, Boolean.FALSE),
				PKStrategy.ASSIGN, handler);
		assertNotEquals(db2v11Key, db2v12Key, "DB2 majorVersion必须参与缓存key");
		assertNotEquals(db2v11Key, db2NoGseKey, "DB2 hasGseSchema必须参与缓存key");
	}

	@Test
	public void nullHandlerAndEmptyHandlerShareCacheKey() {
		EntityMeta entityMeta = entityMeta();
		DBProfile profile = oracleProfile();
		// 未配置handler与配置了空时间字段集合的handler生成相同sql,应共享缓存
		String nullHandlerKey = DialectExtUtils.getCacheKey(entityMeta, null, profile, PKStrategy.ASSIGN, null);
		String emptyHandlerKey = DialectExtUtils.getCacheKey(entityMeta, null, profile, PKStrategy.ASSIGN,
				new TimeFieldHandler(null, null));
		assertEquals(nullHandlerKey, emptyHandlerKey);
	}
}
