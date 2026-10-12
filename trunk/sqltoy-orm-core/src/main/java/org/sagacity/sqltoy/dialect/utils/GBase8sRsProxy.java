package org.sagacity.sqltoy.dialect.utils;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * @project sagacity-sqltoy
 * @description GBase 8s驱动(3.70)CONCUR_UPDATABLE结果集的代理承接:updateRow()存在SQL组装
 *              缺陷(真库实测无论列型/有无for update均报"Found a quote for which there is no
 *              matching quote"),insertRow()同族不可用;且驱动未实现可更新结果集的
 *              读可见性(updateXxx单独调用不报错但行内读回仍是旧值)。本代理只分叉
 *              落库动作:updateXxx记录变更集(不透传驱动)、updateRow以独立update语句
 *              落库(定位键为next()时捕获的keyColumns原始值)、moveToInsertRow/insertRow
 *              以独立insert语句落库、getXxx按下标/列名先查变更集(行内修改后读同列
 *              见新值,JDBC可更新结果集的读可见性契约),其余方法含next()全部透传真实
 *              结果集。updateFetch(GBase8sDialect)与updateSaveFetch
 *              (DefaultDialectUtils)共用
 * @author zhongxuchen
 * @version v1.0,Date:2026-10-12
 */
public class GBase8sRsProxy {

	private GBase8sRsProxy() {
	}

	/**
	 * 包裹CONCUR_UPDATABLE结果集
	 *
	 * @param rs         真实结果集
	 * @param conn       数据库连接(独立update/insert语句使用)
	 * @param table      更新目标表
	 * @param keyColumns 行定位键列(update的where条件列),null时取select首列
	 * @return 代理结果集
	 */
	public static ResultSet wrap(final ResultSet rs, final Connection conn, final String table,
			final String[] keyColumns) throws SQLException {
		final ResultSetMetaData md = rs.getMetaData();
		final int columnCount = md.getColumnCount();
		// 代理键与变更集按label(调用方按select列表引用),落库语句标识用真实列名
		// (别名场景set alias=...非法);部分驱动columnName可能为空,回退label
		final String[] labels = new String[columnCount];
		final String[] colNames = new String[columnCount];
		final int[] colTypes = new int[columnCount];
		final Map<String, Integer> labelIndex = new LinkedHashMap<String, Integer>();
		for (int i = 1; i <= columnCount; i++) {
			labels[i - 1] = md.getColumnLabel(i);
			colNames[i - 1] = (md.getColumnName(i) == null || md.getColumnName(i).isEmpty()) ? labels[i - 1]
					: md.getColumnName(i);
			colTypes[i - 1] = md.getColumnType(i);
			labelIndex.put(labels[i - 1].toLowerCase(Locale.ROOT), i);
		}
		// 行定位键(null取首列):update的where按各键列原始值定位行
		final String[] keys = new String[keyColumns != null ? keyColumns.length : 1];
		if (keyColumns == null || keyColumns.length == 0) {
			keys[0] = colNames[0];
		} else {
			for (int i = 0; i < keyColumns.length; i++) {
				keys[i] = keyColumns[i];
			}
		}
		// 当前行变更集(LinkedHashMap保持更新次序);next()前进时清空并捕获定位键原始值;
		// insert模式(moveToInsertRow)下变更集即待插入列
		final Map<String, Object> changed = new LinkedHashMap<String, Object>();
		final Set<String> nullMark = new HashSet<String>();
		final Object[] keyVals = new Object[keys.length];
		final boolean[] insertMode = { false };
		// wasNull()可见性:最近一次读是否来自变更集及取值(非变更集读透传驱动)
		final Object[] lastMapRead = { NOT_FROM_MAP };
		return (ResultSet) java.lang.reflect.Proxy.newProxyInstance(GBase8sRsProxy.class.getClassLoader(),
				new Class[] { ResultSet.class }, (pxy, method, mArgs) -> {
					String mn = method.getName();
					boolean hasArgs = (mArgs != null && mArgs.length > 0);
					// 行前进:清空变更集并捕获定位键原始值(真实结果集未透传变更,读到的即原始值;
					// 在handler执行前捕获,即使handler改写键列也以旧值定位——数据库当前仍是旧值)
					if ("next".equals(mn)) {
						boolean hasNext = (Boolean) delegate(rs, method, mArgs);
						changed.clear();
						nullMark.clear();
						insertMode[0] = false;
						lastMapRead[0] = NOT_FROM_MAP;
						if (hasNext) {
							for (int k = 0; k < keys.length; k++) {
								keyVals[k] = rs.getObject(keys[k]);
							}
						}
						return hasNext;
					}
					// 落库分叉点:updateRow以独立update语句承接;insertRow(insert模式)以
					// 独立insert语句承接;cancelRowUpdates语义为丢弃当前行变更
					if ("updateRow".equals(mn)) {
						if (!insertMode[0]) {
							flushUpdate(rs, conn, table, colNames, colTypes, labelIndex, changed, nullMark, keys,
									keyVals);
						}
						return null;
					}
					if ("insertRow".equals(mn)) {
						flushInsert(conn, table, colNames, colTypes, labelIndex, changed, nullMark);
						changed.clear();
						nullMark.clear();
						insertMode[0] = false;
						return null;
					}
					if ("moveToInsertRow".equals(mn)) {
						changed.clear();
						nullMark.clear();
						insertMode[0] = true;
						lastMapRead[0] = NOT_FROM_MAP;
						return null;
					}
					if ("moveToCurrentRow".equals(mn) || "cancelRowUpdates".equals(mn)) {
						changed.clear();
						nullMark.clear();
						insertMode[0] = false;
						lastMapRead[0] = NOT_FROM_MAP;
						return null;
					}
					// updateXxx/updateNull:记录变更集(同列后写覆盖前写;非null覆盖null标记)
					if (mn.startsWith("update") && hasArgs) {
						int idx = resolveColumn(rs, labelIndex, mArgs[0]);
						if (idx > 0 && idx <= columnCount) {
							String key = labels[idx - 1].toLowerCase(Locale.ROOT);
							if ("updateNull".equals(mn)) {
								changed.put(key, null);
								nullMark.add(key);
							} else {
								changed.put(key, (mArgs.length > 1) ? mArgs[1] : null);
								nullMark.remove(key);
							}
						}
						return null;
					}
					// 读可见性:按下标与按列名两口径先查变更集(未变更列透传驱动)
					if (mn.startsWith("get") && hasArgs
							&& (mArgs[0] instanceof String || mArgs[0] instanceof Integer)) {
						int idx = resolveColumn(rs, labelIndex, mArgs[0]);
						if (idx > 0 && idx <= columnCount) {
							String key = labels[idx - 1].toLowerCase(Locale.ROOT);
							if (changed.containsKey(key)) {
								Object value = changed.get(key);
								// 原始类型getter返回null会拆箱NPE,按JDBC零值语义返回;
								// Number按目标原始类型收窄(如updateBigDecimal后getInt)
								if (method.getReturnType().isPrimitive()) {
									value = primitiveValue(method.getReturnType(), value);
								}
								lastMapRead[0] = value;
								return value;
							}
						}
					}
					// wasNull对变更集读取按取值判定,其余透传驱动(驱动按其最近一次读判定)
					if ("wasNull".equals(mn) && lastMapRead[0] != NOT_FROM_MAP) {
						return Boolean.valueOf(lastMapRead[0] == null);
					}
					lastMapRead[0] = NOT_FROM_MAP;
					return delegate(rs, method, mArgs);
				});
	}

	/**
	 * 当前行变更集以独立update语句落库:set列为变更集各列(真实列名),where为定位键列
	 * 的next()时原始值。null按目标列元数据类型setNull;BigDecimal沿用SqlUtil.
	 * setNumberParam对该驱动的setDouble规避策略(协议层高精度decimal绑定缺陷)
	 */
	private static void flushUpdate(ResultSet rs, Connection conn, String table, String[] colNames, int[] colTypes,
			Map<String, Integer> labelIndex, Map<String, Object> changed, Set<String> nullMark, String[] keys,
			Object[] keyVals) throws SQLException {
		if (changed.isEmpty() || table == null) {
			return;
		}
		StringBuilder updateSql = new StringBuilder("update ").append(table).append(" set ");
		boolean first = true;
		for (String key : changed.keySet()) {
			if (!first) {
				updateSql.append(",");
			}
			updateSql.append(colNames[labelIndex.get(key) - 1]).append("=?");
			first = false;
		}
		updateSql.append(" where ");
		for (int k = 0; k < keys.length; k++) {
			if (k > 0) {
				updateSql.append(" and ");
			}
			updateSql.append(keys[k]).append("=?");
		}
		try (PreparedStatement up = conn.prepareStatement(updateSql.toString())) {
			int bind = bindSetValues(up, 1, colTypes, labelIndex, changed, nullMark);
			for (int k = 0; k < keys.length; k++) {
				bind = bindValue(up, bind, keyVals[k], Types.OTHER);
			}
			up.executeUpdate();
		}
	}

	/** insert模式的变更集以独立insert语句落库(未设置的列走表默认值,等价insertRow语义) */
	private static void flushInsert(Connection conn, String table, String[] colNames, int[] colTypes,
			Map<String, Integer> labelIndex, Map<String, Object> changed, Set<String> nullMark) throws SQLException {
		if (changed.isEmpty() || table == null) {
			return;
		}
		StringBuilder insertSql = new StringBuilder("insert into ").append(table).append(" (");
		StringBuilder marks = new StringBuilder(" values (");
		List<String> keyList = new ArrayList<String>(changed.keySet());
		for (int i = 0; i < keyList.size(); i++) {
			if (i > 0) {
				insertSql.append(",");
				marks.append(",");
			}
			insertSql.append(colNames[labelIndex.get(keyList.get(i)) - 1]);
			marks.append("?");
		}
		insertSql.append(")");
		marks.append(")");
		try (PreparedStatement ip = conn.prepareStatement(insertSql.append(marks).toString())) {
			bindSetValues(ip, 1, colTypes, labelIndex, changed, nullMark);
			ip.executeUpdate();
		}
	}

	private static int bindSetValues(PreparedStatement pst, int startBind, int[] colTypes,
			Map<String, Integer> labelIndex, Map<String, Object> changed, Set<String> nullMark) throws SQLException {
		int bind = startBind;
		for (String key : changed.keySet()) {
			int idx = labelIndex.get(key);
			bind = bindValue(pst, bind, changed.get(key), (nullMark.contains(key)) ? colTypes[idx - 1] : Types.OTHER);
		}
		return bind;
	}

	/**
	 * 统一绑定策略:null且给了目标列类型按元数据setNull(裸setObject(idx,null)该驱动
	 * 报类型错);BigDecimal转setDouble(SqlUtil.setNumberParam对该驱动的同源规避)
	 */
	private static int bindValue(PreparedStatement pst, int bind, Object value, int sqlType) throws SQLException {
		if (value == null) {
			pst.setNull(bind, (sqlType == Types.OTHER || sqlType == 0) ? Types.NULL : sqlType);
		} else if (value instanceof BigDecimal) {
			pst.setDouble(bind, ((BigDecimal) value).doubleValue());
		} else {
			pst.setObject(bind, value);
		}
		return bind + 1;
	}

	/** 透传真实结果集并解包反射包装异常(驱动SQLException按原异常类型抛出) */
	private static Object delegate(ResultSet rs, java.lang.reflect.Method method, Object[] mArgs) throws Exception {
		try {
			return method.invoke(rs, mArgs);
		} catch (java.lang.reflect.InvocationTargetException ite) {
			Throwable cause = (ite.getCause() != null) ? ite.getCause() : ite;
			if (cause instanceof Exception) {
				throw (Exception) cause;
			}
			if (cause instanceof Error) {
				throw (Error) cause;
			}
			throw ite;
		}
	}

	/** 列引用解析:下标原样,列名先查label小写索引,未命中借驱动的findColumn解析 */
	private static int resolveColumn(ResultSet rs, Map<String, Integer> labelIndex, Object colRef) {
		if (colRef instanceof Integer) {
			return (Integer) colRef;
		}
		String key = String.valueOf(colRef).toLowerCase(Locale.ROOT);
		Integer idx = labelIndex.get(key);
		if (idx != null) {
			return idx;
		}
		try {
			return rs.findColumn(String.valueOf(colRef));
		} catch (SQLException e) {
			return -1;
		}
	}

	/** 原始类型getter读变更集的零值/数值适配(null返回零值,Number按目标类型收窄) */
	private static Object primitiveValue(Class<?> returnType, Object value) {
		if (value != null) {
			if (value instanceof Number) {
				Number num = (Number) value;
				if (returnType == int.class) {
					return Integer.valueOf(num.intValue());
				}
				if (returnType == long.class) {
					return Long.valueOf(num.longValue());
				}
				if (returnType == double.class) {
					return Double.valueOf(num.doubleValue());
				}
				if (returnType == float.class) {
					return Float.valueOf(num.floatValue());
				}
				if (returnType == short.class) {
					return Short.valueOf(num.shortValue());
				}
				if (returnType == byte.class) {
					return Byte.valueOf(num.byteValue());
				}
			}
			return value;
		}
		if (returnType == boolean.class) {
			return Boolean.FALSE;
		}
		if (returnType == int.class) {
			return Integer.valueOf(0);
		}
		if (returnType == long.class) {
			return Long.valueOf(0L);
		}
		if (returnType == double.class) {
			return Double.valueOf(0d);
		}
		if (returnType == float.class) {
			return Float.valueOf(0f);
		}
		if (returnType == short.class) {
			return Short.valueOf((short) 0);
		}
		if (returnType == byte.class) {
			return Byte.valueOf((byte) 0);
		}
		if (returnType == char.class) {
			return Character.valueOf('\0');
		}
		return value;
	}

	/** wasNull可见性哨兵:值非此对象时表示最近一次读来自变更集 */
	private static final Object NOT_FROM_MAP = new Object();
}
