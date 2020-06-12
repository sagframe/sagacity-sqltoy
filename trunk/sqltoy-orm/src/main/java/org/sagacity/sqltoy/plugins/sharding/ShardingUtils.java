/**
 * 
 */
package org.sagacity.sqltoy.plugins.sharding;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.QueryShardingModel;
import org.sagacity.sqltoy.config.model.ShardingConfig;
import org.sagacity.sqltoy.config.model.ShardingStrategyConfig;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.executor.QueryExecutor;
import org.sagacity.sqltoy.model.IgnoreCaseLinkedMap;
import org.sagacity.sqltoy.model.ShardingDBModel;
import org.sagacity.sqltoy.model.ShardingGroupModel;
import org.sagacity.sqltoy.model.ShardingModel;
import org.sagacity.sqltoy.plugins.id.IdGenerator;
import org.sagacity.sqltoy.utils.BeanUtil;
import org.sagacity.sqltoy.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sagacity-sqltoy
 * @description 提取sharding表和DataSource
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:ShardingUtils.java,Revision:v1.0,Date:2014年12月7日
 * @Modification Date:2016-9-7 {修复matchReplace方法,解决因表名大小写未匹配无法替换表名错误}
 */
@SuppressWarnings("rawtypes")
public class ShardingUtils {
	/**
	 * 定义日志
	 */
	protected final static Logger logger = LoggerFactory.getLogger(ShardingUtils.class);

	/**
	 * @todo 单个对象sharding策略处理,适用于load、save、update、delete单对象操作
	 * @param sqlToyContext
	 * @param entity
	 * @param wrapIdValue
	 * @param dataSource
	 * @return
	 * @throws Exception
	 */
	public static ShardingModel getSharding(SqlToyContext sqlToyContext, Serializable entity, boolean wrapIdValue,
			DataSource dataSource) throws Exception {
		ShardingModel shardingModel = new ShardingModel();
		shardingModel.setDataSource(dataSource);
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entity.getClass());
		shardingModel.setTableName(entityMeta.getSchemaTable());
		// 主键值需要提前按照主键策略赋予(sequence 和assign模式的不会实际执行赋值)
		if (wrapIdValue) {
			assignPK(sqlToyContext, entityMeta, entity);
		}
		ShardingConfig shardingConfig = entityMeta.getShardingConfig();
		if (shardingConfig == null) {
			return shardingModel;
		}

		ShardingStrategy shardingStrategy;
		ShardingStrategyConfig strategyConfig;
		// 分库策略处理
		if (shardingConfig.getShardingDBStrategy() != null) {
			strategyConfig = shardingConfig.getShardingDBStrategy();
			shardingStrategy = sqlToyContext.getShardingStrategy(strategyConfig.getName());
			if (shardingStrategy == null) {
				throw new IllegalArgumentException("POJO 对象:" + entity.getClass().getName() + " Sharding DB Strategy:"
						+ strategyConfig.getName() + " 未定义,请检查!");
			}
			IgnoreCaseLinkedMap<String, Object> valueMap = hashParams(strategyConfig.getAliasNames(),
					BeanUtil.reflectBeanToAry(entity, strategyConfig.getFields(), null, null));
			ShardingDBModel dbModel = shardingStrategy.getShardingDB(sqlToyContext, entity.getClass(),
					entityMeta.getSchemaTable(), strategyConfig.getDecisionType(), valueMap);
			shardingModel.setDataSourceName(dbModel.getDataSourceName());
			if (dbModel.getDataSource() == null) {
				shardingModel.setDataSource(sqlToyContext.getDataSource(dbModel.getDataSourceName()));
			} else {
				shardingModel.setDataSource(dbModel.getDataSource());
			}
		}

		// 分表策略
		if (shardingConfig.getShardingTableStrategy() != null) {
			strategyConfig = shardingConfig.getShardingTableStrategy();
			shardingStrategy = sqlToyContext.getShardingStrategy(strategyConfig.getName());
			if (shardingStrategy == null) {
				throw new IllegalArgumentException("POJO 对象:" + entity.getClass().getName()
						+ " Sharding Table Strategy:" + strategyConfig.getName() + " 未定义,请检查!");
			}
			IgnoreCaseLinkedMap<String, Object> valueMap = hashParams(strategyConfig.getAliasNames(),
					BeanUtil.reflectBeanToAry(entity, strategyConfig.getFields(), null, null));
			String tableName = shardingStrategy.getShardingTable(sqlToyContext, entity.getClass(),
					entityMeta.getSchemaTable(), strategyConfig.getDecisionType(), valueMap);
			if (StringUtil.isNotBlank(tableName)) {
				shardingModel.setTableName(tableName);
			}
		}
		return shardingModel;
	}

	/**
	 * @todo 批量sharding策略处理
	 * @param sqlToyContext
	 * @param entities
	 * @param entityMeta
	 * @param dataSource
	 * @return
	 * @throws Exception
	 */
	public static Collection<ShardingGroupModel> groupShardings(SqlToyContext sqlToyContext, List<?> entities,
			EntityMeta entityMeta, DataSource dataSource) throws Exception {
		ShardingConfig shardingConfig = entityMeta.getShardingConfig();
		ShardingModel shardingModel = null;
		String entityTable = entityMeta.getSchemaTable();
		// 没有sharding配置，则作为单个分组返回
		if (shardingConfig == null) {
			Collection<ShardingGroupModel> result = new ArrayList<ShardingGroupModel>();
			ShardingGroupModel model = new ShardingGroupModel();
			shardingModel = new ShardingModel();
			shardingModel.setDataSource(dataSource);
			shardingModel.setTableName(entityTable);
			model.setShardingModel(shardingModel);
			model.setEntities(entities);
			result.add(model);
			return result;
		}
		Class entityClass = entityMeta.getEntityClass();
		// 分库
		boolean hasDB = false;
		ShardingStrategy dbStrategy = null;
		List<Object[]> shardingDBValues = null;
		ShardingStrategyConfig dbConfig = shardingConfig.getShardingDBStrategy();
		if (dbConfig != null) {
			hasDB = true;
			dbStrategy = sqlToyContext.getShardingStrategy(dbConfig.getName());
			if (dbStrategy == null) {
				throw new IllegalArgumentException("POJO 对象:" + entityClass.getName() + " Sharding DB Strategy:"
						+ dbConfig.getName() + " 未定义,请检查!");
			}
			shardingDBValues = BeanUtil.reflectBeansToInnerAry(entities, dbConfig.getFields(), null, null, false, 0);
		}
		// 分表
		boolean hasTable = false;
		ShardingStrategy tableStrategy = null;
		ShardingStrategyConfig tableConfig = shardingConfig.getShardingTableStrategy();
		List<Object[]> shardingTableValues = null;
		if (tableConfig != null) {
			hasTable = true;
			tableStrategy = sqlToyContext.getShardingStrategy(tableConfig.getName());
			if (tableStrategy == null) {
				throw new IllegalArgumentException("POJO 对象:" + entityClass.getName() + " Sharding Table Strategy:"
						+ tableConfig.getName() + " 未定义,请检查!");
			}
			shardingTableValues = BeanUtil.reflectBeansToInnerAry(entities, tableConfig.getFields(), null, null, false,
					0);
		}

		Map<String, ShardingGroupModel> shardingGroupMaps = new HashMap<String, ShardingGroupModel>();
		IgnoreCaseLinkedMap<String, Object> valueMap;
		ShardingDBModel shardingDBModel = null;
		// 数据分组key(dataSourceName+tableName)
		String dataGroupKey;
		String tableName = null;
		String dataSourceName = null;
		for (int i = 0; i < entities.size(); i++) {
			// 分库
			if (hasDB) {
				valueMap = hashParams(dbConfig.getAliasNames(), shardingDBValues.get(i));
				shardingDBModel = dbStrategy.getShardingDB(sqlToyContext, entityClass, entityTable,
						dbConfig.getDecisionType(), valueMap);
				dataSourceName = shardingDBModel.getDataSourceName();
			}
			// 分表
			if (hasTable) {
				valueMap = hashParams(tableConfig.getAliasNames(), shardingTableValues.get(i));
				tableName = tableStrategy.getShardingTable(sqlToyContext, entityClass, entityTable,
						tableConfig.getDecisionType(), valueMap);
			}
			// 分组key
			dataGroupKey = dataSourceName + tableName;
			// 归并到相同分组
			if (shardingGroupMaps.containsKey(dataGroupKey)) {
				shardingGroupMaps.get(dataGroupKey).getEntities().add(entities.get(i));
			} else {
				// 不同分组
				ShardingGroupModel groupModel = new ShardingGroupModel();
				groupModel.setKey(dataGroupKey);
				// 创建数据分组集合
				List items = new ArrayList();
				items.add(entities.get(i));
				groupModel.setEntities(items);
				shardingModel = new ShardingModel();
				// 分库,设置分组对应的数据库
				if (hasDB) {
					shardingModel.setDataSourceName(dataSourceName);
					if (shardingDBModel.getDataSource() == null) {
						shardingModel.setDataSource(sqlToyContext.getDataSource(shardingDBModel.getDataSourceName()));
					} else {
						shardingModel.setDataSource(shardingDBModel.getDataSource());
					}
				} else {
					shardingModel.setDataSource(dataSource);
				}
				// 分表,设置表名
				if (hasTable && StringUtil.isNotBlank(tableName)) {
					shardingModel.setTableName(tableName);
				}
				groupModel.setShardingModel(shardingModel);
				shardingGroupMaps.put(dataGroupKey, groupModel);
			}
		}
		return shardingGroupMaps.values();
	}

	/**
	 * @todo 根据条件决定使用不同的数据库类型
	 * @param sqlToyContext
	 * @param sqlToyConfig
	 * @param queryExecutor
	 * @param dataSource
	 * @return
	 * @throws Exception
	 */
	public static DataSource getShardingDataSource(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig,
			QueryExecutor queryExecutor, DataSource dataSource) throws Exception {
		// 获取sharding DataSource
		// 优先以直接指定的dataSource为基准
		DataSource shardingDataSource = dataSource;
		// 如果没有sharding策略，则返回dataSource，否则以sharding的结果dataSource为基准
		if (null == sqlToyConfig || null == sqlToyConfig.getDataSourceShardingStragety())
			return shardingDataSource;
		String[] paramNames = queryExecutor.getDataSourceShardingParamsName(sqlToyConfig);
		Object[] paramValues = queryExecutor.getDataSourceShardingParamsValue(sqlToyConfig);
		IgnoreCaseLinkedMap<String, Object> valueMap = hashParams(paramNames, paramValues);
		DataSource result = getShardingDataSource(sqlToyContext, sqlToyConfig, valueMap);
		if (result != null)
			return result;
		return shardingDataSource;
	}

	/**
	 * @todo 根据数据获取sharding对应的DataSource
	 * @param sqlToyContext
	 * @param sqlToyConfig
	 * @param valueMap
	 * @return
	 */
	private static DataSource getShardingDataSource(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig,
			IgnoreCaseLinkedMap<String, Object> valueMap) {
		if (sqlToyConfig.getDataSourceShardingStragety() == null)
			return null;
		ShardingStrategy shardingStrategy = sqlToyContext
				.getShardingStrategy(sqlToyConfig.getDataSourceShardingStragety());
		if (shardingStrategy == null)
			return null;
		IgnoreCaseLinkedMap<String, Object> realDataMap = null;
		if (sqlToyConfig.getDataSourceShardingParams() != null) {
			realDataMap = new IgnoreCaseLinkedMap<String, Object>();
			for (int i = 0, n = sqlToyConfig.getDataSourceShardingParams().length; i < n; i++) {
				realDataMap.put(sqlToyConfig.getDataSourceShardingParamsAlias()[i],
						valueMap.get(sqlToyConfig.getDataSourceShardingParams()[i]));
			}
		} else {
			realDataMap = valueMap;
		}
		ShardingDBModel shardingDBModel = shardingStrategy.getShardingDB(sqlToyContext, null, sqlToyConfig.getId(),
				sqlToyConfig.getDataSourceShardingStrategyValue(), realDataMap);
		if (shardingDBModel.getDataSource() != null) {
			return shardingDBModel.getDataSource();
		}
		return sqlToyContext.getDataSource(shardingDBModel.getDataSourceName());
	}

	/**
	 * @todo 根据查询条件变更sql后同时修改sqltoyConfig(clone后的对象，不会冲掉原配置)
	 * @param sqlToyContext
	 * @param sqlToyConfig
	 * @param paramNames
	 * @param paramValues
	 */
	public static void replaceShardingSqlToyConfig(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig,
			String dialect, String[] paramNames, Object[] paramValues) {
		if (sqlToyConfig.getTablesShardings() == null)
			return;
		HashMap<String, String> shardingTableMap = getShardingTables(sqlToyContext, sqlToyConfig, paramNames,
				paramValues);
		if (shardingTableMap == null || shardingTableMap.isEmpty())
			return;
		Iterator iter = shardingTableMap.entrySet().iterator();
		Map.Entry entry;
		String sqlTable;
		String targetTable;
		boolean hasReplace = false;
		while (iter.hasNext()) {
			entry = (Map.Entry) iter.next();
			sqlTable = (String) entry.getKey();
			targetTable = (String) entry.getValue();
			if (targetTable != null && !targetTable.trim().equals("") && !sqlTable.equals(targetTable)) {
				sqlToyConfig.setCountSql(matchReplace(sqlToyConfig.getCountSql(dialect), sqlTable, targetTable));
				sqlToyConfig.setSql(matchReplace(sqlToyConfig.getSql(dialect), sqlTable, targetTable));
				sqlToyConfig.setFastSql(matchReplace(sqlToyConfig.getFastSql(dialect), sqlTable, targetTable));
				sqlToyConfig.setFastPreSql(matchReplace(sqlToyConfig.getFastPreSql(dialect), sqlTable, targetTable));
				sqlToyConfig.setFastTailSql(matchReplace(sqlToyConfig.getFastTailSql(dialect), sqlTable, targetTable));
				sqlToyConfig.setFastWithSql(matchReplace(sqlToyConfig.getFastWithSql(dialect), sqlTable, targetTable));
				hasReplace = true;
			}
		}
		if (hasReplace) {
			// 清除map中的语句,便于依据新的sharding table重新生成sql
			sqlToyConfig.clearDialectSql();
			sqlToyConfig.setDialect(dialect);
		}
	}

	/**
	 * @todo 替换实际sql中需要查询的表名称(for executeSql方法使用,见DialectFactory.executeSql)
	 * @param sqlToyContext
	 * @param sql
	 * @param sqlToyConfig
	 * @param paramNames
	 * @param paramValues
	 * @return
	 */
	public static String replaceShardingTables(SqlToyContext sqlToyContext, String sql, SqlToyConfig sqlToyConfig,
			String[] paramNames, Object[] paramValues) {
		if (sqlToyConfig.getTablesShardings() == null)
			return sql;
		HashMap<String, String> shardingTableMap = getShardingTables(sqlToyContext, sqlToyConfig, paramNames,
				paramValues);
		if (shardingTableMap == null || shardingTableMap.isEmpty())
			return sql;

		Iterator iter = shardingTableMap.entrySet().iterator();
		Map.Entry entry;
		String sqlTable;
		String targetTable;
		String resultSql = sql;
		while (iter.hasNext()) {
			entry = (Map.Entry) iter.next();
			sqlTable = (String) entry.getKey();
			targetTable = (String) entry.getValue();
			resultSql = matchReplace(resultSql, sqlTable, targetTable);
		}
		return resultSql;
	}

	/**
	 * @todo 获取sharding对应的表
	 * @param sqlToyContext
	 * @param sqlToyConfig
	 * @param paramNames
	 * @param paramValues
	 * @return
	 */
	private static HashMap<String, String> getShardingTables(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig,
			String[] paramNames, Object[] paramValues) {
		if (sqlToyConfig.getTablesShardings() == null)
			return null;
		IgnoreCaseLinkedMap<String, Object> valueMap = hashParams(paramNames, paramValues);
		HashMap<String, String> tableMap = new HashMap<String, String>();
		String[] tables;
		String table;
		String shardingTable;
		ShardingStrategy shardingStrategy;
		IgnoreCaseLinkedMap<String, Object> realDataMap = null;
		for (QueryShardingModel shardingModel : sqlToyConfig.getTablesShardings()) {
			shardingStrategy = sqlToyContext.getShardingStrategy(shardingModel.getStrategy());
			if (shardingStrategy != null) {
				tables = shardingModel.getTables();
				if (shardingModel.getParams() != null) {
					realDataMap = new IgnoreCaseLinkedMap<String, Object>();
					for (int i = 0, n = shardingModel.getParams().length; i < n; i++) {
						realDataMap.put(shardingModel.getParamsAlias()[i], valueMap.get(shardingModel.getParams()[i]));
					}
				} else {
					realDataMap = valueMap;
				}

				for (int i = 0; i < tables.length; i++) {
					table = tables[i];
					shardingTable = shardingStrategy.getShardingTable(sqlToyContext, null, table,
							shardingModel.getStrategyValue(), realDataMap);
					if (null != shardingTable && !shardingTable.equalsIgnoreCase(table)) {
						tableMap.put(table, shardingTable);
					}
				}
			} else {
				logger.error("sharding strategy:{} don't exist,please check sharding config!",
						shardingModel.getStrategy());
			}
		}
		return tableMap;
	}

	/**
	 * @todo 替换sharding table
	 * @param sql
	 * @param sourceTable
	 * @param targetTable
	 * @return
	 */
	private static String matchReplace(String sql, String sourceTable, String targetTable) {
		if (sql == null || sql.trim().equals(""))
			return sql;
		// 用正则表达式前后各加上非数字好字符的目的就是防止:sql中有字符串包含sourceTable
		// 如: from biz_notice,biz_notice_item 就出现了包含情况
		Pattern p = Pattern.compile("(?i)\\W".concat(sourceTable).concat("\\W"));
		// 补充一个空字符，确保匹配正确
		Matcher m = p.matcher(sql.concat(" "));
		StringBuilder lastSql = new StringBuilder();
		int start = 0;
		String tailSql = "";
		boolean isFind = false;
		while (m.find()) {
			isFind = true;
			// update 2016-09-07
			lastSql.append(sql.substring(start, m.start() + 1)).append(targetTable);
			// 设置下一个开始
			start = m.end() - 1;
			tailSql = sql.substring(start);
		}
		if (!isFind) {
			return sql;
		}
		return lastSql.append(tailSql).toString();
	}

	/**
	 * @todo 将sharding决策需要的参数构造成有序map传递给sharding决策器
	 * @param paramNames
	 * @param paramValues
	 * @return
	 */
	private static IgnoreCaseLinkedMap<String, Object> hashParams(String[] paramNames, Object[] paramValues) {
		IgnoreCaseLinkedMap<String, Object> valuesMap = new IgnoreCaseLinkedMap<String, Object>();
		if (paramValues == null || paramValues.length == 0)
			return valuesMap;
		if (paramNames == null || paramNames.length == 0) {
			for (int i = 0; i < paramValues.length; i++) {
				valuesMap.put(Integer.toString(i), paramValues[i]);
			}
		} else {
			for (int i = 0; i < paramValues.length; i++) {
				valuesMap.put(paramNames[i], paramValues[i]);
			}
		}
		return valuesMap;
	}

	/**
	 * @todo 单记录主键赋值
	 * @param sqlToyContext
	 * @param meta
	 * @param entity
	 * @throws Exception
	 */
	public static void assignPK(SqlToyContext sqlToyContext, EntityMeta entityMeta, Serializable entity)
			throws Exception {
		List entities = new ArrayList();
		entities.add(entity);
		assignPKs(sqlToyContext, entityMeta, entities);
	}

	/**
	 * @todo 批量主键赋值
	 * @param sqlToyContext
	 * @param entityMeta
	 * @param entities
	 * @throws Exception
	 */
	public static void assignPKs(SqlToyContext sqlToyContext, EntityMeta entityMeta, List<?> entities)
			throws Exception {
		IdGenerator idGenerator = entityMeta.getIdGenerator();
		String[] pks = entityMeta.getIdArray();
		// 存在主键策略，且只能是单主键
		if (idGenerator == null || pks == null || pks.length > 1)
			return;
		if (idGenerator != null) {
			String table = entityMeta.getSchemaTable();
			String idType = entityMeta.getIdType();
			// 业务主键跟主键重叠，已经将主键长度设置为业务主键长度
			int idLength = entityMeta.getIdLength();
			int sequenceSize = entityMeta.getBizIdSequenceSize();
			String[] reflectColumns = entityMeta.getFieldsArray();
			// 标识符
			String signature = entityMeta.getBizIdSignature();
			Integer[] relatedColumnIndex = entityMeta.getBizIdRelatedColIndex();
			List<Object[]> ids = BeanUtil.reflectBeansToInnerAry(entities, pks, null, null, false, 0);
			Object pkValue;
			Object[] relatedColValue = null;
			Object[] fullParamValues;
			for (int i = 0; i < entities.size(); i++) {
				pkValue = ids.get(i)[0];
				// 主键值未赋予,则自动赋予
				if (pkValue == null || pkValue.toString().trim().equals("")) {
					if (entityMeta.isBizIdEqPK()) {
						fullParamValues = BeanUtil.reflectBeanToAry(entities.get(i), reflectColumns, null, null);
						if (relatedColumnIndex != null) {
							relatedColValue = new Object[relatedColumnIndex.length];
							for (int meter = 0; meter < relatedColumnIndex.length; meter++) {
								relatedColValue[meter] = fullParamValues[relatedColumnIndex[meter]];
								if (relatedColValue[meter] == null) {
									throw new IllegalArgumentException("对象:" + entityMeta.getEntityClass().getName()
											+ " 生成业务主键依赖的关联字段:" + relatedColumnIndex[meter] + " 值为null!");
								}
							}
						}
					}

					BeanUtil.setProperty(entities.get(i), pks[0],
							idGenerator.getId(table, signature, entityMeta.getBizIdRelatedColumns(), relatedColValue,
									null, idType, idLength, sequenceSize));
				}
			}
		}
	}
}
