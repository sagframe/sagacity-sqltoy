/**
 *
 */
package org.sagacity.sqltoy.demo.vo;

import java.io.Serializable;

/**
 * @project sagacity-service
 * @description 统一的树型对象模型，适用于菜单、机构等树形结构的展示,暂不使用
 * @author chenrenfei $<a href="mailto:zhongxuchen@hotmail.com">联系作者</a>$
 * @version $id:TreeModel.java,Revision:v1.0,Date:2008-12-9 下午01:34:33 $
 */
public class TreeModel implements Serializable {

	/**
	 *
	 */
	private static final long serialVersionUID = -4755478861179496822L;

	/**
	 * id
	 */
	private String id;

	/**
	 * 名称
	 */
	private String name;

	/**
	 * 是否叶子节点
	 */
	private boolean isLeaf;

	/**
	 * @return the id
	 */
	public String getId() {
		return id;
	}

	/**
	 * @param id the id to set
	 */
	public void setId(String id) {
		this.id = id;
	}

	/**
	 * @return the name
	 */
	public String getName() {
		return name;
	}

	/**
	 * @param name the name to set
	 */
	public void setName(String name) {
		this.name = name;
	}

//	/**
//	 * @param isLeaf the isLeaf to set
//	 */
//	public void setLeaf(boolean isLeaf) {
//		this.isLeaf = isLeaf;
//	}

	public void setIsLeaf(boolean isLeaf) {
		this.isLeaf = isLeaf;
	}

	public boolean isLeaf() {
		return isLeaf;
	}

	public boolean getIsLeaf() {
		return isLeaf;
	}

}
