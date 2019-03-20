/**
 * 
 */
package org.sagacity.sqltoy.link;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.sql.DataSource;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.model.NoSqlConfigModel;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.executor.QueryExecutor;
import org.sagacity.sqltoy.model.DataSetResult;
import org.sagacity.sqltoy.model.PaginationModel;
import org.sagacity.sqltoy.utils.MongoElasticUtils;
import org.sagacity.sqltoy.utils.ResultUtils;
import org.sagacity.sqltoy.utils.StringUtil;
import org.springframework.data.mongodb.MongoDbFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.BasicQuery;

import com.mongodb.client.AggregateIterable;

/**
 * @project sagacity-sqltoy4.1
 * @description 提供基于mongodb的查询服务(利用sqltoy组织查询的语句机制的优势提供查询相关功能,增删改暂时不提供)
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:Mongo.java,Revision:v1.0,Date:2018年1月1日
 */
public class Mongo extends BaseLink {

	private final String ERROR_MESSAGE = "mongo查询请使用<mql id=\"\" collection=\"\" fields=\"\"></mql>配置,请确定相关配置正确性!";

	/**
	 * 基于spring-data的mongo工厂类
	 */
	private MongoDbFactory mongoDbFactory;

	/**
	 * 查询语句
	 */
	private String sql;

	/**
	 * sql中的参数名称
	 */
	private String[] names;

	/**
	 * 参数对应的值
	 */
	private Object[] values;

	/**
	 * 查询条件赋值的对象,自动根据sql中的参数名称跟对象的属性进行匹配提取响应的值作为条件
	 */
	private Serializable entity;

	/**
	 * 返回结果类型
	 */
	private Class resultType;

	/**
	 * @param sqlToyContext
	 * @param dataSource
	 */
	public Mongo(SqlToyContext sqlToyContext, DataSource dataSource) {
		super(sqlToyContext, dataSource);
	}

	public void mongoDbFactory(MongoDbFactory mongoDbFactory) {
		this.mongoDbFactory = mongoDbFactory;
	}

	public Mongo sql(String sql) {
		this.sql = sql;
		return this;
	}

	public Mongo names(String... names) {
		this.names = names;
		return this;
	}

	public Mongo values(Object... values) {
		this.values = values;
		return this;
	}

	public Mongo entity(Serializable entityVO) {
		this.entity = entityVO;
		return this;
	}

	public Mongo resultType(Class resultType) {
		this.resultType = resultType;
		return this;
	}

	/**
	 * @todo 获取单条记录
	 * @return
	 */
	public Object getOne() throws Exception {
		List result = find();
		if (result != null && !result.isEmpty())
			return result.get(0);
		return null;
	}

	/**
	 * @todo 集合记录查询
	 * @return
	 * @throws Exception
	 */
	public List find() throws Exception {
		QueryExecutor queryExecutor = build();
		SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(sql);
		NoSqlConfigModel noSqlModel = sqlToyConfig.getNoSqlConfigModel();
		if (noSqlModel == null || noSqlModel.getCollection() == null || noSqlModel.getFields() == null)
			throw new Exception(ERROR_MESSAGE);
		// 最后的执行语句
		String realMql = MongoElasticUtils.wrapMql(sqlToyConfig, queryExecutor.getParamsName(sqlToyConfig),
				queryExecutor.getParamsValue(sqlToyContext, sqlToyConfig));
		// 聚合查询
		if (noSqlModel.isHasAggs())
			return aggregate(new MongoTemplate(getMongoDbFactory(noSqlModel.getMongoFactory())), sqlToyConfig, realMql,
					queryExecutor.getResultTypeName());
		else
			return findTop(new MongoTemplate(getMongoDbFactory(noSqlModel.getMongoFactory())), sqlToyConfig, null,
					realMql, queryExecutor.getResultTypeName());
	}

	/**
	 * @todo 查询前多少条记录
	 * @param topSize
	 * @return
	 * @throws Exception
	 */
	public List findTop(final Float topSize) throws Exception {
		QueryExecutor queryExecutor = build();
		SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(sql);
		NoSqlConfigModel noSqlModel = sqlToyConfig.getNoSqlConfigModel();
		if (noSqlModel == null || noSqlModel.getCollection() == null || noSqlModel.getFields() == null)
			throw new Exception(ERROR_MESSAGE);
		// 最后的执行语句
		String realMql = MongoElasticUtils.wrapMql(sqlToyConfig, queryExecutor.getParamsName(sqlToyConfig),
				queryExecutor.getParamsValue(sqlToyContext,sqlToyConfig));
		return findTop(new MongoTemplate(getMongoDbFactory(noSqlModel.getMongoFactory())), sqlToyConfig, topSize,
				realMql, queryExecutor.getResultTypeName());
	}

	/**
	 * @todo 分页查询
	 * @param pageModel
	 * @return
	 * @throws Exception
	 */
	public PaginationModel findPage(PaginationModel pageModel) throws Exception {
		QueryExecutor queryExecutor = build();
		SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(sql);
		NoSqlConfigModel noSqlModel = sqlToyConfig.getNoSqlConfigModel();
		if (noSqlModel == null || noSqlModel.getCollection() == null || noSqlModel.getFields() == null)
			throw new Exception(ERROR_MESSAGE);
		// 最后的执行语句
		String realMql = MongoElasticUtils.wrapMql(sqlToyConfig, queryExecutor.getParamsName(sqlToyConfig),
				queryExecutor.getParamsValue(sqlToyContext,sqlToyConfig));
		return findPage(new MongoTemplate(getMongoDbFactory(noSqlModel.getMongoFactory())), sqlToyConfig, pageModel,
				realMql, queryExecutor.getResultTypeName());
	}

	/**
	 * @todo 构造统一的查询条件
	 * @return
	 */
	private QueryExecutor build() throws Exception {
		QueryExecutor queryExecutor = null;
		if (entity != null)
			queryExecutor = new QueryExecutor(sql, entity);
		else
			queryExecutor = new QueryExecutor(sql, names, values);
		if (resultType != null)
			queryExecutor.resultType(resultType);
		return queryExecutor;
	}

	/**
	 * @todo 分页查询
	 * @param mongoTemplate
	 * @param sqlToyConfig
	 * @param pageModel
	 * @param mql
	 * @param resultClass
	 * @return
	 * @throws Exception
	 */
	private PaginationModel findPage(MongoTemplate mongoTemplate, SqlToyConfig sqlToyConfig, PaginationModel pageModel,
			String mql, String resultClass) throws Exception {
		PaginationModel result = new PaginationModel();
		result.setPageNo(pageModel.getPageNo());
		result.setPageSize(pageModel.getPageSize());
		BasicQuery query = new BasicQuery(mql);
		result.setRecordCount(mongoTemplate.count(query, sqlToyConfig.getNoSqlConfigModel().getCollection()));

		// 设置分页
		if (result.getPageNo() == -1) {
			query.skip(0).limit(new Long(result.getRecordCount()).intValue());
		} else
			query.skip((pageModel.getPageNo() - 1) * pageModel.getPageSize()).limit(pageModel.getPageSize());
		List<Document> rs = mongoTemplate.find(query, Document.class,
				sqlToyConfig.getNoSqlConfigModel().getCollection());
		if (rs == null || rs.isEmpty())
			return result;
		result.setRows(extractFieldValues(sqlToyConfig, rs.iterator(), resultClass));
		return result;
	}

	/**
	 * @todo 取top记录
	 * @param mongoTemplate
	 * @param sqlToyConfig
	 * @param topSize
	 * @param mql
	 * @param resultClass
	 * @return
	 * @throws Exception
	 */
	private List findTop(MongoTemplate mongoTemplate, SqlToyConfig sqlToyConfig, Float topSize, String mql,
			String resultClass) throws Exception {
		BasicQuery query = new BasicQuery(mql);
		if (topSize != null) {
			if (topSize > 1)
				query.limit(topSize.intValue());
			else {
				// 按比例提取
				long count = mongoTemplate.count(query, sqlToyConfig.getNoSqlConfigModel().getCollection());
				query.limit(Double.valueOf(count * topSize.floatValue()).intValue());
			}
		}
		List<Document> rs = mongoTemplate.find(query, Document.class,
				sqlToyConfig.getNoSqlConfigModel().getCollection());
		if (rs == null || rs.isEmpty())
			return null;
		return extractFieldValues(sqlToyConfig, rs.iterator(), resultClass);
	}

	/**
	 * @todo 聚合统计查询
	 * @param mongoTemplate
	 * @param sqlToyConfig
	 * @param mql
	 * @param resultClass
	 * @return
	 * @throws Exception
	 */
	private List aggregate(MongoTemplate mongoTemplate, SqlToyConfig sqlToyConfig, String mql, String resultClass)
			throws Exception {
		String realMql = mql.trim();
		if (realMql.startsWith("{") && realMql.endsWith("}"))
			realMql = realMql.substring(1, realMql.length() - 1).trim();
		if (realMql.startsWith("[") && realMql.endsWith("]"))
			realMql = realMql.substring(1, realMql.length() - 1);
		String[] aggregates = StringUtil.splitExcludeSymMark(realMql, ",", SqlToyConstants.filters);
		List<Bson> dbObjects = new ArrayList<Bson>();
		for (String json : aggregates) {
			if (StringUtil.isNotBlank(json))
				dbObjects.add(Document.parse(json));
		}
		AggregateIterable<Document> out = mongoTemplate
				.getCollection(sqlToyConfig.getNoSqlConfigModel().getCollection()).aggregate(dbObjects);
		if (out == null)
			return null;
		return extractFieldValues(sqlToyConfig, out.iterator(), resultClass);
	}

	private List extractFieldValues(SqlToyConfig sqlToyConfig, Iterator<Document> iter, String resultClass)
			throws Exception {
		List resultSet = new ArrayList();
		Document row;
		String[] fields = sqlToyConfig.getNoSqlConfigModel().getFields();
		// 解决field采用name:aliasName形式
		String[] realFields = new String[fields.length];
		String[] translateFields = new String[fields.length];
		System.arraycopy(fields, 0, realFields, 0, fields.length);
		System.arraycopy(fields, 0, translateFields, 0, fields.length);
		int aliasIndex = 0;
		for (int i = 0; i < realFields.length; i++) {
			aliasIndex = realFields[i].indexOf(":");
			if (aliasIndex != -1) {
				realFields[i] = realFields[i].substring(0, aliasIndex).trim();
				translateFields[i] = translateFields[i].substring(aliasIndex + 1).trim();
			}
		}
		while (iter.hasNext()) {
			row = iter.next();
			List rowData = new ArrayList();
			for (String name : realFields) {
				rowData.add(row.get(name));
			}
			resultSet.add(rowData);
		}
		MongoElasticUtils.processTranslate(sqlToyContext, sqlToyConfig, resultSet, translateFields);

		DataSetResult dataSetResult = new DataSetResult();
		dataSetResult.setRows(resultSet);
		dataSetResult.setLabelNames(translateFields);
		// 不支持指定查询集合的行列转换,对集合进行汇总、行列转换等
		ResultUtils.calculate(sqlToyConfig, dataSetResult, null, sqlToyContext.isDebug());
		MongoElasticUtils.wrapResultClass(resultSet, translateFields, resultClass);
		return resultSet;
	}

	/**
	 * @param mongoFactory
	 * @return
	 */
	private MongoDbFactory getMongoDbFactory(String mongoFactory) {
		if (StringUtil.isNotBlank(mongoFactory))
			return (MongoDbFactory) sqlToyContext.getBean(mongoFactory);
		if (this.mongoDbFactory != null)
			return mongoDbFactory;
		return (MongoDbFactory) sqlToyContext.getBean(sqlToyContext.getMongoFactoryName());
	}
}
