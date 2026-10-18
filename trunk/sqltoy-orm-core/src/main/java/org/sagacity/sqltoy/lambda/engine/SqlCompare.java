package org.sagacity.sqltoy.lambda.engine;

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
	// like采用纯 LIKE :param 形态:通配符%由lambda层按方向补入参数值(等价sqltoy原生ParamsFilter.llike/rlike),
	// 不用CONCAT拼接——CONCAT在sqlite等库会被改写为 value||'%' 使?紧跟LIKE,触发processLike对裸值二次补%导致前缀匹配退化为包含匹配;
	// 值已含%时processLike只做_和\转义不再二次包装,数据库无关且转义统一
	LIKE(SqlKeyword.LIKE, "%s LIKE :%s"),
	LIKE_LEFT(SqlKeyword.LIKE, "%s LIKE :%s"),
	LIKE_RIGHT(SqlKeyword.LIKE, "%s LIKE :%s"),
	NOT_LIKE(SqlKeyword.NOT_LIKE, "%s NOT LIKE :%s"),
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
