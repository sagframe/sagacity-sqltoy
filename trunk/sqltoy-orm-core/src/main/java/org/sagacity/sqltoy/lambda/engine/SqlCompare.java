package org.sagacity.sqltoy.lambda.engine;

import org.sagacity.sqltoy.exception.DataAccessException;

/**
 * @description 比较条件枚举,内置各类比较操作的sql模板,参数统一采用sqltoy的<code>:param</code>命名参数形式
 * @author zhongxuchen
 */
public enum SqlCompare {

	EQ(SqlKeyword.EQ, "%s = :%s"),
	NE(SqlKeyword.NE, "%s <> :%s"),
	GT(SqlKeyword.GT, "%s > :%s"),
	GE(SqlKeyword.GE, "%s >= :%s"),
	LT(SqlKeyword.LT, "%s < :%s"),
	LE(SqlKeyword.LE, "%s <= :%s"),
	// like采用数据库无关的concat写法,%%为String.format转义
	LIKE(SqlKeyword.LIKE, "%s LIKE CONCAT('%%',CONCAT(:%s,'%%'))"),
	LIKE_LEFT(SqlKeyword.LIKE, "%s LIKE CONCAT('%%',:%s)"),
	LIKE_RIGHT(SqlKeyword.LIKE, "%s LIKE CONCAT(:%s,'%%')"),
	NOT_LIKE(SqlKeyword.NOT_LIKE, "%s NOT LIKE CONCAT('%%',CONCAT(:%s,'%%'))"),
	IN(SqlKeyword.IN, "%s IN (:%s)"),
	// 子查询in,sql文本原样拼接
	IN_SUBSQL(SqlKeyword.IN, "%s IN (%s)"),
	NOT_IN(SqlKeyword.NOT_IN, "%s NOT IN (:%s)"),
	IS_NULL(SqlKeyword.IS_NULL, "%s IS NULL"),
	IS_NOT_NULL(SqlKeyword.IS_NOT_NULL, "%s IS NOT NULL"),
	BETWEEN(SqlKeyword.BETWEEN, "%s BETWEEN :%s AND :%s"),
	NOT_BETWEEN(SqlKeyword.NOT_BETWEEN, "%s NOT BETWEEN :%s AND :%s"),
	// 用于join的on等值关联:别名.字段 = 别名.字段
	LINK_EQ(SqlKeyword.EQ, "%s = %s");

	private final String sqlContent;

	SqlCompare(SqlKeyword sqlKeyword, String sqlContent) {
		this.sqlContent = sqlContent;
	}

	/**
	 * @description 按模板中的占位符个数依次格式化对应名称
	 * @param names 字段名和参数名序列
	 * @return 对应的sql片段
	 */
	public String getMetaSql(String... names) {
		return String.format(sqlContent, (Object[]) names);
	}

	/**
	 * @description 值位置内联(不做参数绑定)的模板渲染,用于exists子查询中的外层列引用
	 * @param columnName  列名
	 * @param inlineValue 内联sql文本
	 * @return 对应的sql片段
	 */
	public String getInlineMetaSql(String columnName, String inlineValue) {
		return String.format(sqlContent.replace(":%s", "%s"), columnName, inlineValue);
	}
}
