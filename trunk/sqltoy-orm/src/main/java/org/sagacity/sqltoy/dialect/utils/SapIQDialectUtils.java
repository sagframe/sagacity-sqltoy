/**
 * 
 */
package org.sagacity.sqltoy.dialect.utils;

import java.io.IOException;
import java.io.Serializable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.sagacity.sqltoy.SqlExecuteStat;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.callback.PreparedStatementResultHandler;
import org.sagacity.sqltoy.callback.ReflectPropsHandler;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.PKStrategy;
import org.sagacity.sqltoy.config.model.TableCascadeModel;
import org.sagacity.sqltoy.utils.BeanUtil;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;
import org.sagacity.sqltoy.utils.SqlUtil;
import org.sagacity.sqltoy.utils.SqlUtilsExt;
import org.sagacity.sqltoy.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sqltoy-orm
 * @description 针对sybase iq数据库提供特定的辅助工具类，针对分页等进行特殊处理
 * @author zhongxuchen
 * @version v1.0,Date:2015年4月26日
 */
@SuppressWarnings({ "rawtypes" })
public class SapIQDialectUtils {
	/**
	 * 定义日志
	 */
	protected final static Logger logger = LoggerFactory.getLogger(SapIQDialectUtils.class);

	/**
	 * @todo 保存对象(sybase iq支持sequence)
	 * @param sqlToyContext
	 * @param entity
	 * @param openIdentity
	 * @param conn
	 * @param dbType
	 * @param tableName
	 * @return
	 * @throws Exception
	 */
	public static Object save(SqlToyContext sqlToyContext, Serializable entity, boolean openIdentity,
			final Connection conn, final Integer dbType, String tableName) throws Exception {
		final EntityMeta entityMeta = sqlToyContext.getEntityMeta(entity.getClass());
		final boolean isIdentity = entityMeta.getIdStrategy() != null
				&& entityMeta.getIdStrategy().equals(PKStrategy.IDENTITY);
		final boolean isSequence = entityMeta.getIdStrategy() != null
				&& entityMeta.getIdStrategy().equals(PKStrategy.SEQUENCE);
		String insertSql = DialectExtUtils.generateInsertSql(DBType.SYBASE_IQ, entityMeta, entityMeta.getIdStrategy(),
				null, "@mySeqVariable", false, tableName);
		if (isSequence) {
			insertSql = "set nocount on DECLARE @mySeqVariable decimal(20) select @mySeqVariable="
					+ entityMeta.getSequence() + ".NEXTVAL " + insertSql + " select @mySeqVariable ";
		}
		// 无主键,或多主键且非identity、sequence模式
		boolean noPK = (entityMeta.getIdArray() == null);
		int pkIndex = entityMeta.getIdIndex();
		ReflectPropsHandler handler = DialectUtils.getAddReflectHandler(null, sqlToyContext.getUnifyFieldsHandler());
		handler = DialectUtils.getSecureReflectHandler(handler, sqlToyContext.getFieldsSecureProvider(),
				sqlToyContext.getDesensitizeProvider(), entityMeta.getSecureFields());
		Object[] fullParamValues = BeanUtil.reflectBeanToAry(entity,
				(isIdentity || isSequence) ? entityMeta.getRejectIdFieldArray() : entityMeta.getFieldsArray(), null,
				handler);
		boolean needUpdatePk = false;
		// 是否存在业务ID
		boolean hasBizId = (entityMeta.getBusinessIdGenerator() == null) ? false : true;
		int bizIdColIndex = hasBizId ? entityMeta.getFieldIndex(entityMeta.getBusinessIdField()) : 0;
		// 标识符
		String signature = entityMeta.getBizIdSignature();
		Integer[] relatedColumn = entityMeta.getBizIdRelatedColIndex();

		// 主键采用assign方式赋予，则调用generator产生id并赋予其值
		if (entityMeta.getIdStrategy() != null && null != entityMeta.getIdGenerator()) {
			int idLength = entityMeta.getIdLength();
			int bizIdLength = entityMeta.getBizIdLength();
			Object[] relatedColValue = null;
			String businessIdType = hasBizId ? entityMeta.getColumnJavaType(entityMeta.getBusinessIdField()) : "";
			if (relatedColumn != null) {
				relatedColValue = new Object[relatedColumn.length];
				for (int meter = 0; meter < relatedColumn.length; meter++) {
					relatedColValue[meter] = fullParamValues[relatedColumn[meter]];
					if (relatedColValue[meter] == null) {
						throw new IllegalArgumentException("对象:" + entityMeta.getEntityClass().getName()
								+ " 生成业务主键依赖的关联字段:" + relatedColumn[meter] + " 值为null!");
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

		final Object[] paramValues = fullParamValues;
		final Integer[] paramsType = entityMeta.getFieldsTypeArray();
		if (isIdentity) {
			insertSql = insertSql + " select @@IDENTITY ";
		}
		SqlExecuteStat.showSql("执行iq插入", insertSql, null);
		final String realInsertSql = insertSql;
		PreparedStatement pst = null;
		Object result = SqlUtil.preparedStatementProcess(null, pst, null, new PreparedStatementResultHandler() {
			public void execute(Object obj, PreparedStatement pst, ResultSet rs) throws SQLException, IOException {
				pst = conn.prepareStatement(realInsertSql);
				// 存在默认值
				if (null != entityMeta.getFieldsDefaultValue()) {
					SqlUtilsExt.setParamsValue(sqlToyContext.getTypeHandler(), conn, dbType, pst, paramValues,
							entityMeta);
				} else {
					SqlUtil.setParamsValue(sqlToyContext.getTypeHandler(), conn, dbType, pst, paramValues, paramsType,
							0);
				}
				ResultSet keyResult = null;
				if (isSequence || isIdentity) {
					keyResult = pst.executeQuery();
				} else {
					pst.execute();
				}
				if (isSequence || isIdentity) {
					while (keyResult.next()) {
						this.setResult(keyResult.getObject(1));
					}
				}
			}
		});
		// 无主键直接返回null
		if (noPK) {
			return null;
		}
		if (result == null) {
			result = fullParamValues[pkIndex];
		}
		// 回置到entity 主键值
		if (needUpdatePk || isIdentity || isSequence) {
			BeanUtil.setProperty(entity, entityMeta.getIdArray()[0], result);
		}
		// 判断是否有子表级联保存
		if (!entityMeta.getCascadeModels().isEmpty()) {
			List subTableData = null;
			EntityMeta subTableEntityMeta;
			for (TableCascadeModel cascadeModel : entityMeta.getCascadeModels()) {
				final Object[] mainFieldValues = BeanUtil.reflectBeanToAry(entity, cascadeModel.getFields());
				final String[] mappedFields = cascadeModel.getMappedFields();
				subTableEntityMeta = sqlToyContext.getEntityMeta(cascadeModel.getMappedType());
				// oneToMany
				if (cascadeModel.getCascadeType() == 1) {
					subTableData = (List) BeanUtil.getProperty(entity, cascadeModel.getProperty());
				} else {
					subTableData = new ArrayList();
					Object item = BeanUtil.getProperty(entity, cascadeModel.getProperty());
					if (item != null) {
						subTableData.add(item);
					}
				}
				if (subTableData != null && !subTableData.isEmpty()) {
					logger.info("执行save操作的级联子表{}批量保存!", subTableEntityMeta.getTableName());
					SqlExecuteStat.debug("执行子表级联保存操作", null);
					saveAll(sqlToyContext, subTableData, sqlToyContext.getBatchSize(), new ReflectPropsHandler() {
						public void process() {
							for (int i = 0; i < mappedFields.length; i++) {
								this.setValue(mappedFields[i], mainFieldValues[i]);
							}
						}
					}, openIdentity, conn, dbType, null);
				} else {
					logger.info("未执行save操作的级联子表{}批量保存,子表数据为空!", subTableEntityMeta.getTableName());
				}
			}
		}
		return result;
	}

	/**
	 * @todo 批量保存处理
	 * @param sqlToyContext
	 * @param entities
	 * @param batchSize
	 * @param reflectPropsHandler
	 * @param openIdentity
	 * @param conn
	 * @param dbType
	 * @param tableName
	 * @return
	 * @throws Exception
	 */
	public static Long saveAll(SqlToyContext sqlToyContext, List<?> entities, final int batchSize,
			ReflectPropsHandler reflectPropsHandler, boolean openIdentity, Connection conn, final Integer dbType,
			String tableName) throws Exception {
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entities.get(0).getClass());
		String insertSql = DialectExtUtils.generateInsertSql(DBType.SYBASE_IQ, entityMeta, entityMeta.getIdStrategy(),
				null, "@mySeqVariable", false, tableName);
		if (entityMeta.getIdStrategy() != null && entityMeta.getIdStrategy().equals(PKStrategy.SEQUENCE)) {
			insertSql = "DECLARE @mySeqVariable decimal(20) select @mySeqVariable=" + entityMeta.getSequence()
					+ ".NEXTVAL " + insertSql;
		}
		SqlExecuteStat.showSql("IQ批量插入", insertSql, null);
		return saveAll(sqlToyContext, entityMeta, entityMeta.getIdStrategy(), false, insertSql, entities, batchSize,
				reflectPropsHandler, conn, dbType);
	}

	/**
	 * @todo 保存批量对象数据
	 * @param sqlToyContext
	 * @param entityMeta
	 * @param pkStrategy
	 * @param isAssignPK
	 * @param insertSql
	 * @param entities
	 * @param batchSize
	 * @param reflectPropsHandler
	 * @param conn
	 * @param dbType
	 * @return
	 * @throws Exception
	 */
	private static Long saveAll(SqlToyContext sqlToyContext, EntityMeta entityMeta, PKStrategy pkStrategy,
			boolean isAssignPK, String insertSql, List<?> entities, final int batchSize,
			ReflectPropsHandler reflectPropsHandler, Connection conn, final Integer dbType) throws Exception {
		boolean isIdentity = pkStrategy != null && pkStrategy.equals(PKStrategy.IDENTITY);
		boolean isSequence = pkStrategy != null && pkStrategy.equals(PKStrategy.SEQUENCE);
		String[] reflectColumns;
		if ((isIdentity && !isAssignPK) || (isSequence && !isAssignPK)) {
			reflectColumns = entityMeta.getRejectIdFieldArray();
		} else {
			reflectColumns = entityMeta.getFieldsArray();
		}
		ReflectPropsHandler handler = DialectUtils.getAddReflectHandler(reflectPropsHandler,
				sqlToyContext.getUnifyFieldsHandler());
		handler = DialectUtils.getSecureReflectHandler(handler, sqlToyContext.getFieldsSecureProvider(),
				sqlToyContext.getDesensitizeProvider(), entityMeta.getSecureFields());
		List<Object[]> paramValues = BeanUtil.reflectBeansToInnerAry(entities, reflectColumns, null, handler);
		int pkIndex = entityMeta.getIdIndex();
		// 是否存在业务ID
		boolean hasBizId = (entityMeta.getBusinessIdGenerator() == null) ? false : true;
		int bizIdColIndex = hasBizId ? entityMeta.getFieldIndex(entityMeta.getBusinessIdField()) : 0;
		// 标识符
		String signature = entityMeta.getBizIdSignature();
		Integer[] relatedColumn = entityMeta.getBizIdRelatedColIndex();
		// 无主键值以及多主键以及assign或通过generator方式产生主键策略
		if (pkStrategy != null && null != entityMeta.getIdGenerator()) {
			int idLength = entityMeta.getIdLength();
			int bizIdLength = entityMeta.getBizIdLength();
			Object[] rowData;
			boolean isAssigned = true;
			List<Object[]> idSet = new ArrayList<Object[]>();
			String idJdbcType = entityMeta.getIdType();
			Object[] relatedColValue = null;
			String businessIdType = hasBizId ? entityMeta.getColumnJavaType(entityMeta.getBusinessIdField()) : "";
			for (int i = 0, s = paramValues.size(); i < s; i++) {
				rowData = (Object[]) paramValues.get(i);
				if (relatedColumn != null) {
					relatedColValue = new Object[relatedColumn.length];
					for (int meter = 0; meter < relatedColumn.length; meter++) {
						relatedColValue[meter] = rowData[relatedColumn[meter]];
						if (relatedColValue[meter] == null) {
							throw new IllegalArgumentException("对象:" + entityMeta.getEntityClass().getName()
									+ " 生成业务主键依赖的关联字段:" + relatedColumn[meter] + " 值为null!");
						}
					}
				}
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
		SqlExecuteStat.showSql("IQ批量插入", insertSql, null);
		return SqlUtilsExt.batchUpdateByJdbc(sqlToyContext.getTypeHandler(), insertSql, paramValues,
				entityMeta.getFieldsTypeArray(), entityMeta.getFieldsDefaultValue(), entityMeta.getFieldsNullable(),
				batchSize, null, conn, dbType);
	}
}
