package org.sagacity.sqltoy.dialect.utils;

import java.io.IOException;
import java.io.Serializable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.callback.PreparedStatementResultHandler;
import org.sagacity.sqltoy.callback.ReflectPropertyHandler;
import org.sagacity.sqltoy.config.SqlConfigParseUtils;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.FieldMeta;
import org.sagacity.sqltoy.config.model.SqlToyResult;
import org.sagacity.sqltoy.utils.BeanUtil;
import org.sagacity.sqltoy.utils.SqlUtil;
import org.sagacity.sqltoy.utils.SqlUtilsExt;
import org.sagacity.sqltoy.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sqltoy-orm
 * @description 提供click数据库通用的操作功能实现,为不同版本提供支持
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:ClickHouseDialectUtils.java,Revision:v1.0,Date:2020年1月20日
 */
public class ClickHouseDialectUtils {
	/**
	 * 定义日志
	 */
	protected final static Logger logger = LoggerFactory.getLogger(ClickHouseDialectUtils.class);

	/**
	 * @todo 产生对象对应的insert sql语句
	 * @param entityMeta
	 * @param tableName
	 * @return
	 */
	public static String generateInsertSql(EntityMeta entityMeta, String tableName) {
		int columnSize = entityMeta.getFieldsArray().length;
		StringBuilder sql = new StringBuilder(columnSize * 20 + 30);
		StringBuilder values = new StringBuilder(columnSize * 2 - 1);
		sql.append("insert into ");
		sql.append(entityMeta.getSchemaTable(tableName));
		sql.append(" (");
		FieldMeta fieldMeta;
		String field;
		boolean isStart = true;
		for (int i = 0; i < columnSize; i++) {
			field = entityMeta.getFieldsArray()[i];
			fieldMeta = entityMeta.getFieldMeta(field);
			if (!isStart) {
				sql.append(",");
				values.append(",");
			}
			sql.append(fieldMeta.getColumnName());
			values.append("?");
			isStart = false;
		}
		sql.append(") values (");
		sql.append(values);
		sql.append(")");
		return sql.toString();
	}

	/**
	 * @todo 保存对象
	 * @param sqlToyContext
	 * @param entityMeta
	 * @param insertSql
	 * @param entity
	 * @param conn
	 * @param dbType
	 * @return
	 * @throws Exception
	 */
	public static Object save(SqlToyContext sqlToyContext, final EntityMeta entityMeta, final String insertSql,
			Serializable entity, final Connection conn, final Integer dbType) throws Exception {
		String[] reflectColumns = entityMeta.getFieldsArray();
		// 构造全新的新增记录参数赋值反射(覆盖之前的)
		ReflectPropertyHandler handler = DialectUtils.getAddReflectHandler(sqlToyContext, null);
		Object[] fullParamValues = BeanUtil.reflectBeanToAry(entity, reflectColumns, null, handler);
		boolean needUpdatePk = false;

		int pkIndex = entityMeta.getIdIndex();
		// 是否存在业务ID
		boolean hasBizId = (entityMeta.getBusinessIdGenerator() == null) ? false : true;
		int bizIdColIndex = hasBizId ? entityMeta.getFieldIndex(entityMeta.getBusinessIdField()) : 0;
		// 标识符
		String signature = entityMeta.getBizIdSignature();
		Integer[] relatedColumn = entityMeta.getBizIdRelatedColIndex();
		String[] relatedColumnNames = entityMeta.getBizIdRelatedColumns();
		int relatedColumnSize = (relatedColumn == null) ? 0 : relatedColumn.length;
		// 主键采用assign方式赋予，则调用generator产生id并赋予其值
		if (entityMeta.getIdStrategy() != null && null != entityMeta.getIdGenerator()) {
			int bizIdLength = entityMeta.getBizIdLength();
			int idLength = entityMeta.getIdLength();
			Object[] relatedColValue = null;
			String businessIdType = hasBizId ? entityMeta.getColumnJavaType(entityMeta.getBusinessIdField()) : "";
			if (relatedColumn != null) {
				relatedColValue = new Object[relatedColumnSize];
				for (int meter = 0; meter < relatedColumnSize; meter++) {
					relatedColValue[meter] = fullParamValues[relatedColumn[meter]];
					if (StringUtil.isBlank(relatedColValue[meter])) {
						throw new IllegalArgumentException("对象:" + entityMeta.getEntityClass().getName()
								+ " 生成业务主键依赖的关联字段:" + relatedColumnNames[meter] + " 值为null!");
					}
				}
			}
			if (StringUtil.isBlank(fullParamValues[pkIndex])) {
				// id通过generator机制产生，设置generator产生的值
				fullParamValues[pkIndex] = entityMeta.getIdGenerator().getId(entityMeta.getTableName(), signature,
						entityMeta.getBizIdRelatedColumns(), relatedColValue, null, entityMeta.getIdType(), idLength,
						entityMeta.getBizIdSequenceSize());
				needUpdatePk = true;
			}
			if (hasBizId && StringUtil.isBlank(fullParamValues[bizIdColIndex])) {
				fullParamValues[bizIdColIndex] = entityMeta.getBusinessIdGenerator().getId(entityMeta.getTableName(),
						signature, entityMeta.getBizIdRelatedColumns(), relatedColValue, null, businessIdType,
						bizIdLength, entityMeta.getBizIdSequenceSize());
				// 回写业务主键值
				BeanUtil.setProperty(entity, entityMeta.getBusinessIdField(), fullParamValues[bizIdColIndex]);
			}
		}

		if (sqlToyContext.isDebug()) {
			logger.debug(insertSql);
		}

		final Object[] paramValues = fullParamValues;
		final Integer[] paramsType = entityMeta.getFieldsTypeArray();
		PreparedStatement pst = null;
		Object result = SqlUtil.preparedStatementProcess(null, pst, null, new PreparedStatementResultHandler() {
			public void execute(Object obj, PreparedStatement pst, ResultSet rs) throws SQLException, IOException {
				pst = conn.prepareStatement(insertSql);
				SqlUtil.setParamsValue(conn, dbType, pst, paramValues, paramsType, 0);
				pst.execute();
			}
		});
		// 无主键直接返回null
		if (entityMeta.getIdArray() == null) {
			return null;
		}
		if (result == null) {
			result = fullParamValues[pkIndex];
		}
		// 回置到entity 主键值
		if (needUpdatePk) {
			BeanUtil.setProperty(entity, entityMeta.getIdArray()[0], result);
		}

		return result;
	}

	/**
	 * @todo 保存批量对象数据
	 * @param sqlToyContext
	 * @param entityMeta
	 * @param insertSql
	 * @param entities
	 * @param batchSize
	 * @param reflectPropertyHandler
	 * @param conn
	 * @param dbType
	 * @param autoCommit
	 * @return
	 * @throws Exception
	 */
	public static Long saveAll(SqlToyContext sqlToyContext, EntityMeta entityMeta, String insertSql, List<?> entities,
			final int batchSize, ReflectPropertyHandler reflectPropertyHandler, Connection conn, final Integer dbType,
			final Boolean autoCommit) throws Exception {
		String[] reflectColumns = entityMeta.getFieldsArray();
		// 构造全新的新增记录参数赋值反射(覆盖之前的)
		ReflectPropertyHandler handler = DialectUtils.getAddReflectHandler(sqlToyContext, reflectPropertyHandler);
		List paramValues = BeanUtil.reflectBeansToInnerAry(entities, reflectColumns, null, handler, false, 0);
		int pkIndex = entityMeta.getIdIndex();
		// 是否存在业务ID
		boolean hasBizId = (entityMeta.getBusinessIdGenerator() == null) ? false : true;
		int bizIdColIndex = hasBizId ? entityMeta.getFieldIndex(entityMeta.getBusinessIdField()) : 0;
		// 标识符
		String signature = entityMeta.getBizIdSignature();
		Integer[] relatedColumn = entityMeta.getBizIdRelatedColIndex();
		String[] relatedColumnNames = entityMeta.getBizIdRelatedColumns();
		int relatedColumnSize = (relatedColumn == null) ? 0 : relatedColumn.length;
		// 无主键值以及多主键以及assign或通过generator方式产生主键策略
		if (entityMeta.getIdStrategy() != null && null != entityMeta.getIdGenerator()) {
			int bizIdLength = entityMeta.getBizIdLength();
			int idLength = entityMeta.getIdLength();
			Object[] rowData;
			boolean isAssigned = true;
			String idJdbcType = entityMeta.getIdType();
			Object[] relatedColValue = null;
			String businessIdType = hasBizId ? entityMeta.getColumnJavaType(entityMeta.getBusinessIdField()) : "";
			List<Object[]> idSet = new ArrayList<Object[]>();
			for (int i = 0, s = paramValues.size(); i < s; i++) {
				rowData = (Object[]) paramValues.get(i);
				// 判断主键策略关联的字段是否有值,合法性验证
				if (relatedColumn != null) {
					relatedColValue = new Object[relatedColumnSize];
					for (int meter = 0; meter < relatedColumnSize; meter++) {
						relatedColValue[meter] = rowData[relatedColumn[meter]];
						if (StringUtil.isBlank(relatedColValue[meter])) {
							throw new IllegalArgumentException("对象:" + entityMeta.getEntityClass().getName()
									+ " 生成业务主键依赖的关联字段:" + relatedColumnNames[meter] + " 值为null!");
						}
					}
				}
				// 主键值为null,调用主键生成策略并赋值
				if (StringUtil.isBlank(rowData[pkIndex])) {
					isAssigned = false;
					rowData[pkIndex] = entityMeta.getIdGenerator().getId(entityMeta.getTableName(), signature,
							entityMeta.getBizIdRelatedColumns(), relatedColValue, null, idJdbcType, idLength,
							entityMeta.getBizIdSequenceSize());
				}
				if (hasBizId && StringUtil.isBlank(rowData[bizIdColIndex])) {
					rowData[bizIdColIndex] = entityMeta.getBusinessIdGenerator().getId(entityMeta.getTableName(),
							signature, entityMeta.getBizIdRelatedColumns(), relatedColValue, null, businessIdType,
							bizIdLength, entityMeta.getBizIdSequenceSize());
					// 回写业务主键值
					BeanUtil.setProperty(entities.get(i), entityMeta.getBusinessIdField(), rowData[bizIdColIndex]);
				}
				idSet.add(new Object[] { rowData[pkIndex] });
			}
			// 批量反向设置最终得到的主键值
			if (!isAssigned) {
				BeanUtil.mappingSetProperties(entities, entityMeta.getIdArray(), idSet, new int[] { 0 }, true);
			}
		}
		if (sqlToyContext.isDebug()) {
			logger.debug("batch insert sql:{}", insertSql);
		}
		return SqlUtilsExt.batchUpdateByJdbc(insertSql, paramValues, batchSize, entityMeta.getFieldsTypeArray(),
				autoCommit, conn, dbType);
	}

	/**
	 * @todo 删除单个对象以及其级联表数据
	 * @param sqlToyContext
	 * @param entity
	 * @param conn
	 * @param dbType
	 * @param tableName
	 * @return
	 * @throws Exception
	 */
	public static Long delete(SqlToyContext sqlToyContext, Serializable entity, Connection conn, final Integer dbType,
			final String tableName) throws Exception {
		if (entity == null)
			return 0L;
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entity.getClass());
		if (null == entityMeta.getIdArray()) {
			throw new IllegalArgumentException("delete table:" + entityMeta.getSchemaTable(tableName)
					+ " no primary key,please check table design!");
		}
		Object[] idValues = BeanUtil.reflectBeanToAry(entity, entityMeta.getIdArray(), null, null);
		Integer[] parameterTypes = new Integer[idValues.length];
		boolean validator = true;
		// 判断主键值是否为空
		for (int i = 0, n = idValues.length; i < n; i++) {
			parameterTypes[i] = entityMeta.getColumnJdbcType(entityMeta.getIdArray()[i]);
			if (StringUtil.isBlank(idValues[i])) {
				validator = false;
				break;
			}
		}
		if (!validator) {
			throw new IllegalArgumentException(entityMeta.getSchemaTable(tableName)
					+ "delete operate is illegal,table must has primary key and all primaryKey's value must has value!");
		}

		String deleteSql = "alter table ".concat(entityMeta.getSchemaTable(tableName)).concat(" delete ")
				.concat(entityMeta.getIdArgWhereSql());
		if (sqlToyContext.isDebug()) {
			logger.debug(deleteSql);
		}
		return DialectUtils.executeSql(sqlToyContext, deleteSql, idValues, parameterTypes, conn, dbType, null);
	}

	/**
	 * @todo 批量删除对象并级联删除掉子表数据
	 * @param sqlToyContext
	 * @param entities
	 * @param batchSize
	 * @param conn
	 * @param dbType
	 * @param autoCommit
	 * @param tableName
	 * @return
	 * @throws Exception
	 */
	public static Long deleteAll(SqlToyContext sqlToyContext, List<?> entities, final int batchSize, Connection conn,
			final Integer dbType, final Boolean autoCommit, final String tableName) throws Exception {
		if (null == entities || entities.isEmpty())
			return 0L;
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entities.get(0).getClass());
		String realTable = entityMeta.getSchemaTable(tableName);
		if (null == entityMeta.getIdArray()) {
			throw new IllegalArgumentException("delete/deleteAll 操作,表:" + realTable + "没有主键,请检查表设计!");
		}

		// 主键值
		List pkValues = BeanUtil.reflectBeansToList(entities, entityMeta.getIdArray());
		int idSize = entityMeta.getIdArray().length;
		int loopSize = pkValues.size();
		// 构造内部的listz(如果复合主键，形成{p1v1,p1v2,p1v3},{p2v1,p2v2,p2v3}) 格式，然后一次查询出结果
		List[] idValues = new List[idSize];
		for (int i = 0; i < idSize; i++) {
			idValues[i] = new ArrayList();
		}
		List rowList;
		// 检查主键值,主键值必须不为null
		Object value;
		for (int i = 0, n = loopSize; i < n; i++) {
			rowList = (List) pkValues.get(i);
			for (int j = 0; j < idSize; j++) {
				value = rowList.get(j);
				// 验证主键值是否合法
				if (StringUtil.isBlank(value)) {
					throw new IllegalArgumentException(realTable + " loadAll method must assign value for pk,row:" + i
							+ " pk field:" + entityMeta.getIdArray()[j]);
				}
				if (!idValues[j].contains(value)) {
					idValues[j].add(value);
				}
			}
		}
		// 构造主键值对应的类型
		Integer[] paramTypes = new Integer[idSize * loopSize];
		Integer idType;
		for (int i = 0; i < idSize; i++) {
			idType = entityMeta.getColumnJdbcType(entityMeta.getIdArray()[i]);
			for (int j = 0; j < loopSize; j++) {
				paramTypes[loopSize * i + j] = idType;
			}
		}

		// 构造delete 语句(clickhouse 记录删除语法特殊)
		StringBuilder deleteSql = new StringBuilder();
		deleteSql.append("alter table ");
		deleteSql.append(realTable);
		deleteSql.append(" delete where ");

		String field;
		for (int i = 0, n = entityMeta.getIdArray().length; i < n; i++) {
			field = entityMeta.getIdArray()[i];
			if (i > 0) {
				deleteSql.append(" and ");
			}
			deleteSql.append(entityMeta.getColumnName(field));
			deleteSql.append(" in (:").append(field).append(") ");
		}

		if (sqlToyContext.isDebug()) {
			logger.debug("根据主键批量删除数据 sql:{}", deleteSql);
		}

		SqlToyResult sqlToyResult = SqlConfigParseUtils.processSql(deleteSql.toString(), entityMeta.getIdArray(),
				idValues);
		return SqlUtil.executeSql(sqlToyResult.getSql(), sqlToyResult.getParamsValue(), paramTypes, conn, dbType,
				autoCommit);
	}
}
