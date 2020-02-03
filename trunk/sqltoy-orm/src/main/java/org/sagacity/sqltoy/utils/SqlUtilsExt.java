/**
 * 
 */
package org.sagacity.sqltoy.utils;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalTime;
import java.util.Date;
import java.util.List;

import org.sagacity.sqltoy.config.model.EntityMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sqltoy-orm
 * @description 提供针对org.sagacity.sqltoy.utils.SqlUtil类的扩展(来自org.sagacity.core.
 *              utils.SqlUtil),提供更有针对性的操作,提升性能
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:SqlUtilsExt.java,Revision:v1.0,Date:2015年4月22日
 */
public class SqlUtilsExt {
	/**
	 * 定义日志
	 */
	private final static Logger logger = LoggerFactory.getLogger(SqlUtilsExt.class);

	/**
	 * @todo 通过jdbc方式批量插入数据
	 * @param updateSql
	 * @param rowDatas
	 * @param batchSize
	 * @param updateTypes
	 * @param autoCommit
	 * @param conn
	 * @param dbType
	 * @return
	 * @throws Exception
	 */
	public static Long batchUpdateByJdbc(final String updateSql, final List<Object[]> rowDatas, final int batchSize,
			final Integer[] updateTypes, final Boolean autoCommit, final Connection conn, final Integer dbType)
			throws Exception {
		return batchUpdateByJdbc(updateSql, rowDatas, updateTypes, null, null, batchSize, autoCommit, conn, dbType);
	}

	/**
	 * @todo 通过jdbc方式批量插入数据，一般提供给数据采集时或插入临时表使用
	 * @param updateSql
	 * @param rowDatas
	 * @param batchSize
	 * @param entityMeta
	 * @param autoCommit
	 * @param conn
	 * @param dbType
	 * @return
	 * @throws Exception
	 */
	public static Long batchUpdateByJdbc(final String updateSql, final List<Object[]> rowDatas, final int batchSize,
			final EntityMeta entityMeta, final Boolean autoCommit, final Connection conn, final Integer dbType)
			throws Exception {
		return batchUpdateByJdbc(updateSql, rowDatas, entityMeta.getFieldsTypeArray(),
				entityMeta.getFieldsDefaultValue(), entityMeta.getFieldsNullable(), batchSize, autoCommit, conn,
				dbType);
	}

	/**
	 * @todo 通过jdbc方式批量插入数据，一般提供给数据采集时或插入临时表使用
	 * @param updateSql
	 * @param rowDatas
	 * @param fieldsType
	 * @param fieldsDefaultValue
	 * @param fieldsNullable
	 * @param batchSize
	 * @param autoCommit
	 * @param conn
	 * @param dbType
	 * @return
	 * @throws Exception
	 */
	private static Long batchUpdateByJdbc(final String updateSql, final List<Object[]> rowDatas,
			final Integer[] fieldsType, final String[] fieldsDefaultValue, final Boolean[] fieldsNullable,
			final int batchSize, final Boolean autoCommit, final Connection conn, final Integer dbType)
			throws Exception {
		if (rowDatas == null || rowDatas.isEmpty()) {
			logger.warn("batchUpdateByJdbc批量插入或修改数据库操作数据为空!");
			return 0L;
		}
		long updateCount = 0;
		PreparedStatement pst = null;
		// 判断是否通过default转换方式插入
		boolean supportDefaultValue = (fieldsDefaultValue != null && fieldsNullable != null) ? true : false;
		try {
			boolean hasSetAutoCommit = false;
			// 是否自动提交
			if (autoCommit != null && autoCommit.booleanValue() != conn.getAutoCommit()) {
				conn.setAutoCommit(autoCommit.booleanValue());
				hasSetAutoCommit = true;
			}
			pst = conn.prepareStatement(updateSql);
			int totalRows = rowDatas.size();
			// 只有一条记录不采用批量
			boolean useBatch = (totalRows > 1) ? true : false;
			Object[] rowData;
			// 批处理计数器
			int meter = 0;
			for (int i = 0; i < totalRows; i++) {
				rowData = rowDatas.get(i);
				if (rowData != null) {
					// 使用对象properties方式传值
					for (int j = 0, n = rowData.length; j < n; j++) {
						if (supportDefaultValue) {
							setParamValue(conn, dbType, pst, rowData[j], fieldsType[j], fieldsNullable[j],
									fieldsDefaultValue[j], j + 1);
						} else {
							SqlUtil.setParamValue(conn, dbType, pst, rowData[j],
									fieldsType == null ? -1 : fieldsType[j], j + 1);
						}
					}
					meter++;
					// 批量
					if (useBatch) {
						pst.addBatch();
						// 判断是否是最后一条记录或到达批次量,执行批处理
						if ((meter % batchSize) == 0 || i + 1 == totalRows) {
							int[] updateRows = pst.executeBatch();
							for (int t : updateRows) {
								updateCount = updateCount + ((t > 0) ? t : 0);
							}
							pst.clearBatch();
						}
					} else {
						pst.execute();
						updateCount = updateCount + ((pst.getUpdateCount() > 0) ? pst.getUpdateCount() : 0);
					}
				}
			}
			// 恢复conn原始autoCommit默认值
			if (hasSetAutoCommit) {
				conn.setAutoCommit(!autoCommit);
			}
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			throw e;
		} finally {
			try {
				if (pst != null) {
					pst.close();
					pst = null;
				}
			} catch (SQLException se) {
				logger.error(se.getMessage(), se);
			}
		}
		return updateCount;
	}

	/**
	 * @todo 自动进行类型转换,设置sql中的参数条件的值
	 * @param conn
	 * @param dbType
	 * @param pst
	 * @param params
	 * @param entityMeta
	 * @throws SQLException
	 * @throws IOException
	 */
	public static void setParamsValue(Connection conn, final Integer dbType, PreparedStatement pst, Object[] params,
			final EntityMeta entityMeta) throws SQLException, IOException {
		if (null != params && params.length > 0) {
			for (int i = 0, n = params.length; i < n; i++) {
				setParamValue(conn, dbType, pst, params[i], entityMeta.getFieldsTypeArray()[i],
						entityMeta.getFieldsNullable()[i], entityMeta.getFieldsDefaultValue()[i], 1 + i);
			}
		}
	}

	/**
	 * @todo 提供针对默认值的转化
	 * @param conn
	 * @param dbType
	 * @param pst
	 * @param paramValue
	 * @param jdbcType
	 * @param isNullable
	 * @param defaultValue
	 * @param paramIndex
	 * @throws SQLException
	 * @throws IOException
	 */
	public static void setParamValue(Connection conn, final Integer dbType, PreparedStatement pst, Object paramValue,
			int jdbcType, boolean isNullable, String defaultValue, int paramIndex) throws SQLException, IOException {
		Object realValue = paramValue;
		// 当前值为null且默认值不为null、且字段不允许为null
		if (realValue == null && defaultValue != null && !isNullable) {
			if (jdbcType == java.sql.Types.DATE) {
				realValue = new Date();
			} else if (jdbcType == java.sql.Types.TIMESTAMP) {
				realValue = DateUtil.getTimestamp(null);
			} else if (jdbcType == java.sql.Types.INTEGER || jdbcType == java.sql.Types.BIGINT
					|| jdbcType == java.sql.Types.TINYINT) {
				realValue = Integer.valueOf(defaultValue);
			} else if (jdbcType == java.sql.Types.DECIMAL || jdbcType == java.sql.Types.NUMERIC) {
				realValue = new BigDecimal(defaultValue);
			} else if (jdbcType == java.sql.Types.DOUBLE) {
				realValue = Double.valueOf(defaultValue);
			} else if (jdbcType == java.sql.Types.BOOLEAN) {
				realValue = Boolean.parseBoolean(defaultValue);
			} else if (jdbcType == java.sql.Types.FLOAT || jdbcType == java.sql.Types.REAL) {
				realValue = Float.valueOf(defaultValue);
			} else if (jdbcType == java.sql.Types.TIME) {
				realValue = java.sql.Time.valueOf(LocalTime.now());
			} else {
				realValue = defaultValue;
			}
		}
		SqlUtil.setParamValue(conn, dbType, pst, realValue, jdbcType, paramIndex);
	}
}
