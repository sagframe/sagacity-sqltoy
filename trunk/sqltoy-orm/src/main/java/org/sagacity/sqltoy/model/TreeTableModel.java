/**
 * 
 */
package org.sagacity.sqltoy.model;

import java.io.Serializable;

/**
 * @project sagacity-sqltoy4.0
 * @description 树形表结构模型
 * @author chenrenfei <a href="mailto:zhongxuchen@hotmail.com">联系作者</a>
 * @version id:TreeTableModel.java,Revision:v1.0,Date:2010-9-27 下午06:27:20
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
	 * 根节点id值(一般为-1)
	 */
	private Object rootId;

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
	@Deprecated
	private String conditions;

	/**
	 * id值的数据类型，字符还是数字
	 */
	private boolean isChar = false;

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
	 * 
	 * @param tableName
	 * @param rootId
	 * @param idField
	 * @param pidField
	 * @param nodeRouteField
	 * @param nodeLevelField
	 * @param leafField
	 * @param isChar
	 * @param idLength
	 */
	public TreeTableModel(String tableName, Object rootId, String idField, String pidField, String nodeRouteField,
			String nodeLevelField, String leafField, boolean isChar, int idLength) {
		this.tableName = tableName;
		this.rootId = rootId;
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

	public TreeTableModel idTypeIsChar(boolean isChar) {
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

	public TreeTableModel rootId(Object rootId) {
		this.rootId = rootId;
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
	public boolean isChar() {
		return isChar;
	}

	/**
	 * @return the size
	 */
	public int getIdLength() {
		return idLength;
	}

	/**
	 * @return the rootId
	 */
	public Object getRootId() {
		return rootId;
	}

	/**
	 * @return the leafField
	 */
	public String getLeafField() {
		return leafField;
	}

	/**
	 * @param conditions
	 *            the conditions to set
	 */
	public void setConditions(String conditions) {
		this.conditions = conditions;
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
	 * @param idValue
	 *            the idValue to set
	 */
	public void setIdValue(Object idValue) {
		this.idValue = idValue;
	}

	/**
	 * @return the appendZero
	 */
	public boolean isAppendZero() {
		return appendZero;
	}

	/**
	 * @param appendZero
	 *            the appendZero to set
	 */
	public void setAppendZero(boolean appendZero) {
		this.appendZero = appendZero;
	}

	/**
	 * @return the splitSign
	 */
	public String getSplitSign() {
		return splitSign;
	}

	/**
	 * @param splitSign
	 *            the splitSign to set
	 */
	public void setSplitSign(String splitSign) {
		this.splitSign = splitSign;
	}

}
