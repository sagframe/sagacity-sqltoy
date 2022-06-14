package org.sagacity.sqltoy.integration;

import java.util.List;

import org.bson.Document;
import org.sagacity.sqltoy.SqlToyContext;

import com.mongodb.client.MongoCollection;

/**
 * @project sagacity-sqltoy
 * @description 提供mongo集成的接口实现，便于spring、solon、nutz等非spring框架扩展
 * @author zhongxuchen
 * @version v1.0, Date:2022年6月14日
 * @modify 2022年6月14日,修改说明
 */
public interface MongoApi {
	/**
	 * @TODO 获取mongo collection (类似表)
	 * @param collectionName
	 * @return
	 */
	public MongoCollection<Document> getCollection(String collectionName);

	/**
	 * @TODO mongo json查询
	 * @param <T>
	 * @param query
	 * @param entityClass
	 * @param collectionName
	 * @param skip           分页skip，即开始行
	 * @param limit          只取数据记录量
	 * @return
	 */
	public <T> List<T> find(String query, Class<T> entityClass, String collectionName, Long skip, Integer limit);

	/**
	 * @TODO 查询记录量
	 * @param query
	 * @param collectionName
	 * @return
	 */
	public long count(String query, String collectionName);

	/**
	 * 初始化
	 * 
	 * @param sqlToyContext
	 */
	public default void initialize(SqlToyContext sqlToyContext) {

	}
}
