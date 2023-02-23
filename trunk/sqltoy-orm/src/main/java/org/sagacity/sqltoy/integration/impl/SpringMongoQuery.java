package org.sagacity.sqltoy.integration.impl;

import java.util.List;

import org.bson.Document;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.integration.MongoQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.BasicQuery;

import com.mongodb.client.MongoCollection;

/**
 * @project sagacity-sqltoy
 * @description 基于spring-data的实现
 * @author zhongxuchen
 * @version v1.0, Date:2022年6月14日
 * @modify 2022年6月14日,修改说明
 */
public class SpringMongoQuery implements MongoQuery {
	/**
	 * 定义日志
	 */
	protected final Logger logger = LoggerFactory.getLogger(SpringMongoQuery.class);
	/**
	 * 基于spring-data的mongo工厂类
	 */
	private MongoTemplate mongoTemplate;

	@Override
	public MongoCollection<Document> getCollection(String collectionName) {
		return mongoTemplate.getCollection(collectionName);
	}

	@Override
	public <T> List<T> find(String mql, Class<T> entityClass, String collectionName, Long skip, Integer limit) {
		BasicQuery query = new BasicQuery(mql);
		if (skip != null && skip >= 0) {
			query.skip(skip);
		}
		if (limit != null && limit > 0) {
			query.limit(limit);
		}
		logger.debug("findByMongo script=" + query.getQueryObject());
		return mongoTemplate.find(query, entityClass, collectionName);
	}

	@Override
	public long count(String query, String collectionName) {
		return mongoTemplate.count(new BasicQuery(query), collectionName);
	}

	/**
	 * 初始化
	 */
	@Override
	public void initialize(SqlToyContext sqlToyContext) {
		if (mongoTemplate == null) {
			mongoTemplate = sqlToyContext.getApplicationContext().getBean(MongoTemplate.class);
		}
	}

}
