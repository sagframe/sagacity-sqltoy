package org.sagacity.sqltoy.config.model;

/**
 * @project sagacity-sqltoy
 * @description 外键参照动作常量,取值与JDBC DatabaseMetaData中importedKey*规则值一致,
 *              用于@Foreign注解的deleteRestict/updateRestict属性,分别对应
 *              ON DELETE/ON UPDATE后面的参照动作
 * @author zhongxuchen
 * @version v1.0,Date:2026-09-19
 * @modify Date:2026-09-19,修改说明
 */
public final class ReferentialAction {

	/**
	 * 级联:ON DELETE/ON UPDATE CASCADE,主键记录删除或修改时同步级联处理外键记录
	 */
	public static final int CASCADE = 0;

	/**
	 * 限制:ON DELETE/ON UPDATE RESTRICT,存在外键引用时禁止删除或修改主键记录
	 */
	public static final int RESTRICT = 1;

	/**
	 * 置空:ON DELETE/ON UPDATE SET NULL,主键记录删除或修改时将外键字段置为null
	 */
	public static final int SET_NULL = 2;

	/**
	 * 无动作:ON DELETE/ON UPDATE NO ACTION,不执行动作,由约束检查兜底(多数数据库默认)
	 */
	public static final int NO_ACTION = 3;

	/**
	 * 置默认值:ON DELETE/ON UPDATE SET DEFAULT,主键记录删除或修改时将外键字段置为默认值
	 */
	public static final int SET_DEFAULT = 4;

	private ReferentialAction() {
	}
}
