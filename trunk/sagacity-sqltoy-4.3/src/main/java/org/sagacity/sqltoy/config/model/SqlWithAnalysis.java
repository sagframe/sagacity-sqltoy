/**
 * 
 */
package org.sagacity.sqltoy.config.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * @project sqltoy-orm
 * @description Sql语句中存在with的分析,只支持单个with 但支持多个as
 * @author chenrenfei <a href="mailto:zhongxuchen@hotmail.com">联系作者</a>
 * @version Revision:v1.0,Date:2013-6-20
 * @Modification Date:2019-7-22 优化with as 语法解析格式 ,其格式包含:with xx as (); with xx
 *               xx as () ; with xxx(p1,p2) as () 三种形态
 */
public class SqlWithAnalysis implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = -5841684922722930298L;

	// postgresql12 支持materialized 物化
	private final Pattern withPattern = Pattern.compile(
			"(?i)\\s*with\\s+[a-z|0-9|\\_]+\\s*(\\([a-z|0-9|\\_|\\s|\\,]+\\))?\\s+as\\s*(\\s+materialized)?\\s*\\(");

	// private final Pattern withPattern = Pattern.compile(
	// "(?i)\\s*with\\s+[a-z|0-9|\\_]+\\s+as\\s*\\(");

	// with 下面多个as
	private final Pattern otherWithPattern = Pattern.compile(
			"(?i)\\s*\\,\\s*[a-z|0-9|\\_]+\\s*(\\([a-z|0-9|\\_|\\s|\\,]+\\))?\\s+as\\s*(\\s+materialized)?\\s*\\(");

	// private final Pattern otherWithPattern = Pattern.compile(
	// "(?i)\\s*\\,\\s*[a-z|0-9|\\_]+as\\s*\\(");

	private final Pattern asPattern = Pattern.compile("\\Was");

	private String sql;

	private String withSql = "";

	/**
	 * 排除with之后的语句
	 */
	private String rejectWithSql;

	/**
	 * 是否有with as
	 */
	private boolean hasWith = false;

	/**
	 * 多个with存放的集合，正向排序(第一版采用逆向排序){asTableName,ext,sql script}
	 */
	private List<String[]> withSqlSet = null;

	public SqlWithAnalysis(String sql) {
		this.sql = sql;
		this.parse();
	}

	/**
	 * @return the sql
	 */
	public String getSql() {
		return sql;
	}

	/**
	 * @return the withSql
	 */
	public String getWithSql() {
		return withSql;
	}

	/**
	 * @return the rejectWithSql
	 */
	public String getRejectWithSql() {
		return rejectWithSql;
	}

	/**
	 * @return the withSqlSet
	 */
	public List<String[]> getWithSqlSet() {
		return withSqlSet;
	}

	/**
	 * 将带with 的sql解析成2部分:with as table () 和 select 部分
	 */
	private void parse() {
		rejectWithSql = this.sql;
		String headSql = "";
		String tailSql = this.sql;
		String aliasTable;
		int endWith;
		int asIndex;
		String ext;
		StringBuilder withSqlBuffer = null;
		// 单个with
		Matcher withAsMatcher = withPattern.matcher(tailSql);
		String groupStr;
		String groupLow;
		if (withAsMatcher.find()) {
			headSql = tailSql.substring(0, withAsMatcher.start());
			hasWith = true;
			withSqlBuffer = new StringBuilder();
			withSqlSet = new ArrayList<String[]>();
			groupStr = withAsMatcher.group();
			groupLow = groupStr.toLowerCase();
			asIndex = StringUtil.matchIndex(groupLow, asPattern) + 1;
			// 剔除with
			aliasTable = groupStr.substring(groupLow.indexOf("with") + 4, asIndex).trim();
			ext = groupStr.substring(asIndex + 2, groupStr.indexOf("(", asIndex));
			endWith = StringUtil.getSymMarkIndex("(", ")", tailSql, withAsMatcher.start() + asIndex);
			withSqlBuffer.append(tailSql.substring(withAsMatcher.start(), endWith + 1));
			withSqlSet.add(new String[] { aliasTable, ext, tailSql.substring(withAsMatcher.end(), endWith) });
			tailSql = tailSql.substring(endWith + 1);
		} else
			return;
		// with 中包含多个 as
		Matcher otherMatcher = otherWithPattern.matcher(tailSql);
		while (otherMatcher.find()) {
			if (otherMatcher.start() != 0)
				break;
			groupStr = otherMatcher.group();
			groupLow = groupStr.toLowerCase();
			asIndex = StringUtil.matchIndex(groupLow, asPattern) + 1;
			aliasTable = groupStr.substring(groupStr.indexOf(",") + 1, asIndex).trim();
			ext = groupStr.substring(asIndex + 2, groupStr.indexOf("(", asIndex));
			endWith = StringUtil.getSymMarkIndex("(", ")", tailSql, otherMatcher.start() + asIndex);
			withSqlBuffer.append(tailSql.substring(0, endWith + 1));
			withSqlSet.add(new String[] { aliasTable, ext, tailSql.substring(otherMatcher.end(), endWith) });
			tailSql = tailSql.substring(endWith + 1);
			otherMatcher.reset(tailSql);
		}
		rejectWithSql = headSql.concat(" ").concat(tailSql);
		this.withSql = withSqlBuffer.append(" ").toString();
	}

	/**
	 * @return the hasWith
	 */
	public boolean isHasWith() {
		return hasWith;
	}

	/**
	 * @param hasWith
	 *            the hasWith to set
	 */
	public void setHasWith(boolean hasWith) {
		this.hasWith = hasWith;
	}

	// public static void main(String[] args) {
	//
	// String sql = "with t1(p1,p2) as (select * from table ),t2 as (select 1 from
	// table2) select * from t1 left join t2 on t1.id=t2.id";
	// // String sql = "with t1(p1,p2) as materialized(select * from table ) select
	// // p1,p2 from t1";
	// DebugUtil.beginTime("id");
	// SqlWithAnalysis sqlWith = new SqlWithAnalysis(sql);
	// // System.err.println(sqlWith.withSql);
	// // System.err.println(sqlWith.rejectWithSql);
	// DebugUtil.endTime("id");
	// // DebugUtil.printAry(sqlWith.withSqlSet, ";", true);
	// }
}
