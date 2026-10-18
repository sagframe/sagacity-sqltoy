package org.sagacity.sqltoy.plugins.id.macro.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.sagacity.sqltoy.utils.CollectionUtil;
import org.sagacity.sqltoy.utils.ReservedWordsUtil;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * @project sagacity-sqltoy
 * @description &#64;joinFields 宏的渲染与安全校验:把运行期传入的字段名列表拼成 SQL 标识符串,
 *              用于页面配置化的报表与接口服务(由配置决定 select / group by / order by 的列)。
 *              <p>
 *              与 &#64;value
 *              处于同一处理位置(SqlConfigParseUtils.processJoinFields),即命名参数已转成 ?
 *              之后按位置索引取值,因此 sql 里写 &#64;joinFields(:fields,default) 与直接写
 *              &#64;joinFields(?,default) 等价。本类只负责"值 -> 标识符串"的渲染,不涉及参数定位与剔除。
 *              <p>
 *              归一规则:非数组(单个字符串)或数组长度为 1 时,统一按单个字符串以逗号切割; 长度大于 1
 *              时元素即字段、不做二次切割——因此 ["a,b","c"] 会因 "a,b" 非法而报错, 而不是被静默拆成三个字段。
 *              <p>
 *              安全:字段名走字符白名单(字母含中文、数字、下划线,最多三段点号限定),非法值直接抛异常、 不做静默清洗;刻意排除 $ 与
 *              #(与 sqltoy 自身的 #[...] 动态段和 ${} / #{} 参数占位冲突)。 合法字段再经
 *              ReservedWordsUtil.convertWord 按方言对保留字加引用符,非保留字保持裸标识符 (无差别加引号会在
 *              oracle/pg 上引入大小写敏感问题)。 default 取自 sql 文本(开发者编写,可信),原样输出、不做标识符校验。
 *              <p>
 *              只受理纯标识符:sum(col)、col as alias 这类表达式请继续用 &#64;value(安全自负)。
 * @author zhongxuchen
 * @version v1.0, Date:2026-10-15
 */
public final class JoinFields {

	/**
	 * 合法字段名:字母(含中文)、数字、下划线起始,其后同类字符,允许最多两段点号限定(schema.table.column)
	 */
	private static final Pattern FIELD_PATTERN = Pattern.compile(
			"[A-Za-z_\u4e00-\u9fa5][A-Za-z0-9_\u4e00-\u9fa5]*(\\.[A-Za-z_\u4e00-\u9fa5][A-Za-z0-9_\u4e00-\u9fa5]*){0,2}");

	private static final String SEPARATOR = ",";

	private JoinFields() {
	}

	/**
	 * 把字段名列表渲染成 SQL 标识符串
	 * 
	 * @param fieldValue  运行期传入的字段名(数组、集合或逗号分隔的单个字符串),null/空则输出 defaultExpr
	 * @param defaultExpr 字段列表为空时的回退输出(取自 sql 文本,可信,原样返回)
	 * @param dbType      数据库方言,用于对保留字加引用符
	 * @return 逗号连接的标识符串,或 defaultExpr
	 */
	public static String join(Object fieldValue, String defaultExpr, int dbType) {
		List<String> fields = normalize(fieldValue);
		if (fields.isEmpty()) {
			return (defaultExpr == null) ? "" : defaultExpr.trim();
		}
		StringBuilder result = new StringBuilder();
		for (String field : fields) {
			if (result.length() > 0) {
				result.append(SEPARATOR);
			}
			result.append(quoteField(field, dbType));
		}
		return result.toString();
	}

	/**
	 * 把任意形态的入参归一成待校验的字段名列表:单个字符串或长度为 1 的数组按逗号切割, 长度大于 1
	 * 的数组元素即字段;空白段跳过(页面多选序列化常产生尾逗号),非空段必须合法
	 */
	private static List<String> normalize(Object value) {
		List<String> result = new ArrayList<String>();
		if (value == null) {
			return result;
		}
		String[] fragments;
		if (value instanceof CharSequence) {
			fragments = splitByComma(value.toString());
		} else {
			Object[] array = CollectionUtil.convertArray(value);
			if (array == null || array.length == 0) {
				return result;
			}
			if (array.length == 1) {
				// 长度为 1 统一按单个字符串处理(常见于把多选结果序列化成 ["a,b,c"])
				if (array[0] == null) {
					return result;
				}
				fragments = splitByComma(array[0].toString());
			} else {
				// 长度大于 1:元素即字段,不做二次切割
				fragments = new String[array.length];
				for (int i = 0; i < array.length; i++) {
					fragments[i] = (array[i] == null) ? null : array[i].toString().trim();
				}
			}
		}
		for (String fragment : fragments) {
			if (StringUtil.isBlank(fragment)) {
				continue;
			}
			checkField(fragment);
			result.add(fragment);
		}
		return result;
	}

	private static String[] splitByComma(String value) {
		// limit 取 -1 保留尾部空段,交由上层统一按空白跳过
		String[] parts = value.split(SEPARATOR, -1);
		for (int i = 0; i < parts.length; i++) {
			parts[i] = parts[i].trim();
		}
		return parts;
	}

	/**
	 * 逐段处理点号限定名:ReservedWordsUtil.convertWord 只按整体匹配保留字集合, t.order
	 * 这类限定名整体不在集合里就会漏掉引用符,在 mysql 等库上直接语法错误
	 */
	private static String quoteField(String field, int dbType) {
		if (field.indexOf('.') < 0) {
			return ReservedWordsUtil.convertWord(field, dbType);
		}
		String[] segments = field.split("\\.");
		StringBuilder result = new StringBuilder();
		for (int i = 0; i < segments.length; i++) {
			if (i > 0) {
				result.append('.');
			}
			result.append(ReservedWordsUtil.convertWord(segments[i], dbType));
		}
		return result.toString();
	}

	private static void checkField(String field) {
		if (!FIELD_PATTERN.matcher(field).matches()) {
			throw new IllegalArgumentException("@joinFields illegal field name:[" + field
					+ "], only letters(including Chinese), digits, underscore and at most two dots are allowed;"
					+ " for expressions such as sum(col) or col as alias use @value instead!");
		}
	}
}
