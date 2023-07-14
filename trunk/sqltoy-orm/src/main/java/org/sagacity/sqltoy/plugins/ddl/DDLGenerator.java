/**
 * 
 */
package org.sagacity.sqltoy.plugins.ddl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.sql.DataSource;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.EntityManager;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.model.IgnoreCaseSet;

/**
 * @project sagacity-sqltoy
 * @description 数据库表脚本创建、更新等操作
 * @author zhongxuchen
 * @version v1.0, Date:2023年7月13日
 * @modify 2023年7月13日,修改说明
 */
public class DDLGenerator {
	// 已经完成的创建或修改的表,因部分表存在外键关系，需梳理顺序
	private static IgnoreCaseSet generatedTables = new IgnoreCaseSet();

	/**
	 * @TODO 提供动态根据POJO产生数据库表创建的脚本文件
	 * @param scanPackages
	 * @param filePath
	 * @param dbType
	 * @throws Exception
	 */
	public static void createSqlFile(String[] scanPackages, String filePath, Integer dbType) throws Exception {
		EntityManager entityManager = new EntityManager();
		entityManager.setPackagesToScan(scanPackages);
		entityManager.initialize(null);
		ConcurrentHashMap<String, EntityMeta> entitysMetaMap = entityManager.getAllEntities();
		if (entitysMetaMap == null || entitysMetaMap.isEmpty()) {
			return;
		}
		// List<EntityMeta> allTableEntities = sortTables(entitysMetaMap);
		// 写文件
	}

	/**
	 * @TODO 动态向数据库创建表结构
	 * @param sqlToyContext
	 * @param entitysMetaMap
	 * @param dataSource
	 */
	public static void createDDL(SqlToyContext sqlToyContext, ConcurrentHashMap<String, EntityMeta> entitysMetaMap,
			DataSource dataSource) {
		if (entitysMetaMap == null || entitysMetaMap.isEmpty()) {
			return;
		}
		// List<EntityMeta> allTableEntities = sortTables(entitysMetaMap);
		// for(EntityMeta entityMeta:allTableEntities)
		// {
		// if(表已经存在){
		// 判断字段、索引、外键差异，做更新操作
		// }else {
		// 直接创建表结构
		// }
		// }
	}

	/**
	 * @TODO 因为存在外键关系，首先需要对表进行排序，被依赖的优先创建
	 * @param entitysMetaMap
	 * @return
	 */
	private static List<EntityMeta> sortTables(ConcurrentHashMap<String, EntityMeta> entitysMetaMap) {
		List<EntityMeta> result = new ArrayList<EntityMeta>();
		for (Map.Entry<String, EntityMeta> entry : entitysMetaMap.entrySet()) {
			// 没有依赖其他表,插入末尾
			if (entry.getValue().getForeignFields() == null) {
				result.add(entry.getValue());
			}
		}
		return result;
	}
}
