/**
 * 
 */
package org.sagacity.sqltoy.model;

import java.io.Serializable;

/**
 * @project sagacity-sqltoy
 * @description 树形表结构模型
 * @author zhongxuchen
 * @version v1.0,Date:2010-9-27
 */
public class TreeTableModel implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 6422408233450252053L;

	/**
	 * 实体对象(entity和tableName 只需指定一个,有entity则无需指定idField\idValue\isChar等属性)
	 */
	private Serializable entity;

	/**
	 * table名称
	 */
	private String tableName;

	/**
	 * 主键值
	 */
	private Object idValue;

	/**
	 * 父节点id值
	 */
	private Object pidValue;

	/**
	 * id对应字段
	 */
	private String idField;

	/**
	 * 父id对应字段
	 */
	private String pidField;

	/**
	 * 节点路径字段
	 */
	private String nodeRouteField = "NODE_ROUTE";

	/**
	 * 节点层次等级字段
	 */
	private String nodeLevelField = "NODE_LEVEL";

	/**
	 * 节点路径分割符号
	 */
	private String splitSign = ",";

	/**
	 * 附加条件
	 */
	private String conditions;

	/**
	 * id值的数据类型，字符还是数字
	 */
	private Boolean isChar = null;

	/**
	 * 补零
	 */
	private boolean appendZero = true;

	/**
	 * 叶子节点字段
	 */
	private String leafField = "IS_LEAF";

	/**
	 * id字段长度
	 */
	private int idLength = -1;

	public TreeTableModel() {

	}

	/**
	 * @param tableName
	 * @param pidValue
	 * @param idField
	 * @param pidField
	 * @param nodeRouteField
	 * @param nodeLevelField
	 * @param leafField
	 * @param isChar
	 * @param idLength
	 */
	public TreeTableModel(String tableName, Object pidValue, String idField, String pidField, String nodeRouteField,
			String nodeLevelField, String leafField, Boolean isChar, int idLength) {
		this.tableName = tableName;
		this.pidValue = pidValue;
		this.idField = idField;
		this.pidField = pidField;
		this.nodeRouteField = nodeRouteField;
		this.nodeLevelField = nodeLevelField;
		this.leafField = leafField;
		this.isChar = isChar;
		this.idLength = idLength;
	}

	public TreeTableModel(Serializable entity) {
		this.entity = entity;
	}

	public TreeTableModel idField(String idField) {
		this.idField = idField;
		return this;
	}

	public TreeTableModel pidField(String pidField) {
		this.pidField = pidField;
		return this;
	}

	public TreeTableModel nodeRouteField(String nodeRouteField) {
		this.nodeRouteField = nodeRouteField;
		return this;
	}

	public TreeTableModel nodeLevelField(String nodeLevelField) {
		this.nodeLevelField = nodeLevelField;
		return this;
	}

	public TreeTableModel idLength(int idLength) {
		this.idLength = idLength;
		return this;
	}

	public TreeTableModel idTypeIsChar(Boolean isChar) {
		this.isChar = isChar;
		return this;
	}

	public TreeTableModel isLeafField(String leafField) {
		this.leafField = leafField;
		return this;
	}

	public TreeTableModel table(String tableName) {
		this.tableName = tableName;
		return this;
	}

	/**
	 * 这里存在命名歧义,正确的是设置父节点的值
	 * @see pidValue(Object pidValue)
	 * @param pidValue
	 * @return
	 */
	@Deprecated
	public TreeTableModel rootId(Object pidValue) {
		this.pidValue = pidValue;
		return this;
	}

	public TreeTableModel pidValue(Object pidValue) {
		this.pidValue = pidValue;
		return this;
	}

	/**
	 * @return the tableName
	 */
	public String getTableName() {
		return tableName;
	}

	/**
	 * @return the idField
	 */
	public String getIdField() {
		return idField;
	}

	/**
	 * @return the pidField
	 */
	public String getPidField() {
		return pidField;
	}

	/**
	 * @return the nodeRouteField
	 */
	public String getNodeRouteField() {
		return nodeRouteField;
	}

	/**
	 * @return the nodeLevelField
	 */
	public String getNodeLevelField() {
		return nodeLevelField;
	}

	/**
	 * @return the isChar
	 */
	public Boolean isChar() {
		return isChar;
	}

	/**
	 * @return the size
	 */
	public int getIdLength() {
		return idLength;
	}

	public Object getPidValue() {
		return pidValue;
	}

	/**
	 * @return the leafField
	 */
	public String getLeafField() {
		return leafField;
	}

	/**
	 * @param conditions the conditions to set
	 */
	public TreeTableModel setConditions(String conditions) {
		this.conditions = conditions;
		return this;
	}

	/**
	 * @return the conditions
	 */
	public String getConditions() {
		return conditions;
	}

	/**
	 * @return the entity
	 */
	public Serializable getEntity() {
		return entity;
	}

	/**
	 * @return the idValue
	 */
	public Object getIdValue() {
		return idValue;
	}

	/**
	 * @param idValue the idValue to set
	 */
	public TreeTableModel setIdValue(Object idValue) {
		this.idValue = idValue;
		return this;
	}

	/**
	 * @return the appendZero
	 */
	public boolean isAppendZero() {
		return appendZero;
	}

	/**
	 * @param appendZero the appendZero to set
	 */
	public TreeTableModel setAppendZero(boolean appendZero) {
		this.appendZero = appendZero;
		return this;
	}

	/**
	 * @return the splitSign
	 */
	public String getSplitSign() {
		return splitSign;
	}

	/**
	 * @param splitSign the splitSign to set
	 */
	public TreeTableModel setSplitSign(String splitSign) {
		this.splitSign = splitSign;
		return this;
	}

}
