/**
 * 
 */
package org.sagacity.sqltoy.config.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * @project sqltoy-orm
 * @description Sql语句中存在with的分析,只支持单个with 但支持多个as
 * @author zhongxuchen
 * @version v1.0,Date:2013-6-20
 * @modify Date:2019-7-22 优化with as 语法解析格式 ,其格式包含:with xx as (); with xx as xx
 *         () ; with xxx(p1,p2) as () 三种形态
 * @modify Date:2020-1-16 支持mysql8.0.19 开始的with recursive cte as
 */
public class SqlWithAnalysis implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = -5841684922722930298L;

	private final Pattern asPattern = Pattern.compile("\\Was");

	private String sql;

	private String withSql = "";

	/**
	 * 排除with之后的语句
	 */
	private String rejectWithSql;

	/**
	 * with sql前部分
	 */
	private String preSql;

	/**
	 * with sql尾部sql
	 */
	private String footSql;

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
	 * @return the preSql
	 */
	public String getPreSql() {
		return preSql;
	}

	/**
	 * @return the lastSql
	 */
	public String getFootSql() {
		return footSql;
	}

	/**
	 * 将带with 的sql解析成2部分:with as table () 和 select 部分
	 */
	private void parse() {
		rejectWithSql = this.sql;
		String headSql = "";
		String tailSql = " ".concat(this.sql);
		String aliasTable;
		int endWith;
		int asIndex;
		String ext;
		StringBuilder withSqlBuffer = null;
		// 单个with
		Matcher withAsMatcher = SqlToyConstants.withPattern.matcher(tailSql);
		String groupStr;
		String groupLow;
		String withAfter;
		String[] params;
		String withAsMiddle;
		String aliasParams;
		int bracketIndex;
		// WITH RECURSIVE search_graph(id, link, data, depth) AS
		if (withAsMatcher.find()) {
			withAfter = "";
			aliasParams = "";
			headSql = tailSql.substring(0, withAsMatcher.start() + 1);
			hasWith = true;
			withSqlBuffer = new StringBuilder();
			withSqlSet = new ArrayList<String[]>();
			groupStr = withAsMatcher.group();
			groupLow = groupStr.toLowerCase();
			asIndex = StringUtil.matchIndex(groupLow, asPattern) + 1;
			withAsMiddle = groupStr.substring(groupLow.indexOf("with") + 4, asIndex).trim();
			bracketIndex = withAsMiddle.indexOf("(");
			if (bracketIndex > 0 && withAsMiddle.endsWith(")")) {
				aliasParams = withAsMiddle.substring(bracketIndex);
				withAsMiddle = withAsMiddle.substring(0, bracketIndex);
				if (withAsMiddle.endsWith(" ")) {
					aliasParams = " ".concat(aliasParams);
					withAsMiddle = withAsMiddle.trim();
				}
			}
			params = withAsMiddle.split("\\s+");
			// 剔除with
			aliasTable = params[params.length - 1];
			if (params.length > 1) {
				withAfter = params[0];
			}
			ext = groupStr.substring(asIndex + 2, groupStr.indexOf("(", asIndex));
			endWith = StringUtil.getSymMarkIndex("(", ")", tailSql, withAsMatcher.start() + asIndex);
			withSqlBuffer.append(tailSql.substring(withAsMatcher.start() + 1, endWith + 1));
			withSqlSet.add(new String[] { aliasTable, ext, tailSql.substring(withAsMatcher.end(), endWith), withAfter,
					aliasParams });
			tailSql = tailSql.substring(endWith + 1);
		} else {
			return;
		}
		// with 中包含多个 as
		Matcher otherMatcher = SqlToyConstants.otherWithPattern.matcher(tailSql);
		while (otherMatcher.find()) {
			if (otherMatcher.start() != 0) {
				break;
			}
			withAfter = "";
			aliasParams = "";
			groupStr = otherMatcher.group();
			groupLow = groupStr.toLowerCase();
			asIndex = StringUtil.matchIndex(groupLow, asPattern) + 1;
			withAsMiddle = groupStr.substring(groupStr.indexOf(",") + 1, asIndex).trim();
			bracketIndex = withAsMiddle.indexOf("(");
			if (bracketIndex > 0 && withAsMiddle.endsWith(")")) {
				aliasParams = withAsMiddle.substring(bracketIndex);
				withAsMiddle = withAsMiddle.substring(0, bracketIndex);
				if (withAsMiddle.endsWith(" ")) {
					aliasParams = " ".concat(aliasParams);
					withAsMiddle = withAsMiddle.trim();
				}
			}
			params = withAsMiddle.split("\\s+");
			aliasTable = params[params.length - 1];
			if (params.length > 1) {
				withAfter = params[0];
			}
			ext = groupStr.substring(asIndex + 2, groupStr.indexOf("(", asIndex));
			endWith = StringUtil.getSymMarkIndex("(", ")", tailSql, otherMatcher.start() + asIndex);
			withSqlBuffer.append(tailSql.substring(0, endWith + 1));
			withSqlSet.add(new String[] { aliasTable, ext, tailSql.substring(otherMatcher.end(), endWith), withAfter,
					aliasParams });
			tailSql = tailSql.substring(endWith + 1);
			otherMatcher.reset(tailSql);
		}
		this.preSql = headSql.trim();
		this.footSql = tailSql.trim();
		this.rejectWithSql = headSql.concat(" ").concat(tailSql);
		this.withSql = withSqlBuffer.append(" ").toString();
	}

	/**
	 * @return the hasWith
	 */
	public boolean isHasWith() {
		return hasWith;
	}

	/**
	 * @param hasWith the hasWith to set
	 */
	public void setHasWith(boolean hasWith) {
		this.hasWith = hasWith;
	}

}
