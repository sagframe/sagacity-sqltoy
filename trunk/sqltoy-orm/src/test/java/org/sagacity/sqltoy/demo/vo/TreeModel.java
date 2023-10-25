/**
 *
 */
package org.sagacity.sqltoy.demo.vo;

import java.io.Serializable;
import java.util.Collection;

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
	 * 树类别（系统常量，如机构树、机构人员树、业务类别树等等）
	 */
	private String treeType;

	/**
	 * 归属应用代码
	 */
	private String appCode;

	/**
	 * id
	 */
	private String id;

	/**
	 * 名称
	 */
	private String name;

	/**
	 * 别名
	 */
	private String aliasName;

	/**
	 * 父节点
	 */
	private String pid;

	/**
	 * 是否打开
	 */
	private boolean open;

	/**
	 * 是否父节点
	 */
	private String isParent;

	/**
	 *
	 */
	private String icon;

	/**
	 * 图标皮肤
	 */
	private String iconSkin;

	/**
	 *
	 */
	private String iconClose;

	/**
	 *
	 */
	private String iconOpen;

	/**
	 * 点击
	 */
	private String click;

	/**
	 * 是否叶子节点
	 */
	private boolean isLeaf;

	/**
	 * 节点等级
	 */
	private int nodeLevel;

	/**
	 * 所有父节点路径
	 */
	private String nodeRoute;

	/**
	 * 节点类型
	 */
	private String nodeType;

	/**
	 * 节点业务类型
	 */
	private String nodeBizType;

	/**
	 * 背景图片
	 */
	private String backImage;

	/**
	 * 展开图标
	 */
	private String openIcon;

	/**
	 * 关闭图标
	 */
	private String closeIcon;

	/**
	 * url
	 */
	private String url;

	/**
	 * url打开目标
	 */
	private String urlTarget;

	/**
	 * 提示信息
	 */
	private String altMsg;

	/**
	 * 风格
	 */
	private String style;

	/**
	 * show_index
	 */
	private String showIndex;

	/**
	 * isFavorite
	 */
	private boolean favorite = false;

	/**
	 * 说明
	 */
	private String desc;

	/**
	 * 叶子节点
	 */
	private Collection<TreeModel> leafNodes;

	// 设置平行的绝不可能用到的最多6个扩展属性

	/**
	 * 扩展属性1
	 */
	private String attr1;

	/**
	 * 扩展属性2
	 */
	private String attr2;
	/**
	 * 扩展属性3
	 */
	private String attr3;
	/**
	 * 扩展属性4
	 */
	private String attr4;
	/**
	 * 扩展属性5
	 */
	private String attr5;
	/**
	 * 扩展属性6
	 */
	private String attr6;

	/**
	 * 视图类型 C-主应用Vue组件 F-iframe嵌套 K-子应用组件
	 */
	private String viewType;

	/**
	 * 子应用APP 入口uri, 当viewType为 K （子应用组件） 时生效
	 */
	private String appEntry;

	/**
	 * 禁止选中标记
	 */
	private String chkDisabled;

	public String getAppEntry() {
		return appEntry;
	}

	public void setAppEntry(String appEntry) {
		this.appEntry = appEntry;
	}

	/**
	 * @return the isParent
	 */
	public String getIsParent() {
		return isParent;
	}

	/**
	 * @param isParent
	 *            the isParent to set
	 */
	public void setIsParent(String isParent) {
		this.isParent = isParent;
	}

	/**
	 * @return the id
	 */
	public String getId() {
		return id;
	}

	/**
	 * @param id
	 *            the id to set
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
	 * @param name
	 *            the name to set
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * @return the pid
	 */
	public String getPid() {
		return pid;
	}

	/**
	 * @param pid
	 *            the pid to set
	 */
	public void setPid(String pid) {
		this.pid = pid;
	}

	

	public boolean isLeaf() {
		return isLeaf;
	}

	/**
	 * @return the nodeLevel
	 */
	public int getNodeLevel() {
		return nodeLevel;
	}

	/**
	 * @param nodeLevel
	 *            the nodeLevel to set
	 */
	public void setNodeLevel(int nodeLevel) {
		this.nodeLevel = nodeLevel;
	}

	/**
	 * @return the nodeRoute
	 */
	public String getNodeRoute() {
		return nodeRoute;
	}

	/**
	 * @param nodeRoute
	 *            the nodeRoute to set
	 */
	public void setNodeRoute(String nodeRoute) {
		this.nodeRoute = nodeRoute;
	}

	/**
	 * @return the openIcon
	 */
	public String getOpenIcon() {
		return openIcon;
	}

	/**
	 * @param openIcon
	 *            the openIcon to set
	 */
	public void setOpenIcon(String openIcon) {
		this.openIcon = openIcon;
	}

	/**
	 * @return the closeIcon
	 */
	public String getCloseIcon() {
		return closeIcon;
	}

	/**
	 * @param closeIcon
	 *            the closeIcon to set
	 */
	public void setCloseIcon(String closeIcon) {
		this.closeIcon = closeIcon;
	}

	/**
	 * @return the url
	 */
	public String getUrl() {
		return url;
	}

	/**
	 * @param url
	 *            the url to set
	 */
	public void setUrl(String url) {
		this.url = url;
	}

	/**
	 * @return the urlTarget
	 */
	public String getUrlTarget() {
		return urlTarget;
	}

	/**
	 * @param urlTarget
	 *            the urlTarget to set
	 */
	public void setUrlTarget(String urlTarget) {
		this.urlTarget = urlTarget;
	}

	/**
	 * @return the altMsg
	 */
	public String getAltMsg() {
		return altMsg;
	}

	/**
	 * @param altMsg
	 *            the altMsg to set
	 */
	public void setAltMsg(String altMsg) {
		this.altMsg = altMsg;
	}

	/**
	 * @return the style
	 */
	public String getStyle() {
		return style;
	}

	/**
	 * @param style
	 *            the style to set
	 */
	public void setStyle(String style) {
		this.style = style;
	}

	/**
	 * @return the aliasName
	 */
	public String getAliasName() {
		return aliasName;
	}

	/**
	 * @param aliasName
	 *            the aliasName to set
	 */
	public void setAliasName(String aliasName) {
		this.aliasName = aliasName;
	}

	/**
	 * @return the desc
	 */
	public String getDesc() {
		return desc;
	}

	/**
	 * @param desc
	 *            the desc to set
	 */
	public void setDesc(String desc) {
		this.desc = desc;
	}

	/**
	 * @return the attr1
	 */
	public String getAttr1() {
		return attr1;
	}

	/**
	 * @param attr1
	 *            the attr1 to set
	 */
	public void setAttr1(String attr1) {
		this.attr1 = attr1;
	}

	/**
	 * @return the attr2
	 */
	public String getAttr2() {
		return attr2;
	}

	/**
	 * @param attr2
	 *            the attr2 to set
	 */
	public void setAttr2(String attr2) {
		this.attr2 = attr2;
	}

	/**
	 * @return the attr3
	 */
	public String getAttr3() {
		return attr3;
	}

	/**
	 * @param attr3
	 *            the attr3 to set
	 */
	public void setAttr3(String attr3) {
		this.attr3 = attr3;
	}

	/**
	 * @return the attr4
	 */
	public String getAttr4() {
		return attr4;
	}

	/**
	 * @param attr4
	 *            the attr4 to set
	 */
	public void setAttr4(String attr4) {
		this.attr4 = attr4;
	}

	/**
	 * @return the attr5
	 */
	public String getAttr5() {
		return attr5;
	}

	/**
	 * @param attr5
	 *            the attr5 to set
	 */
	public void setAttr5(String attr5) {
		this.attr5 = attr5;
	}

	/**
	 * @return the attr6
	 */
	public String getAttr6() {
		return attr6;
	}

	/**
	 * @param attr6
	 *            the attr6 to set
	 */
	public void setAttr6(String attr6) {
		this.attr6 = attr6;
	}

	/**
	 * @return the backImage
	 */
	public String getBackImage() {
		return backImage;
	}

	/**
	 * @param backImage
	 *            the backImage to set
	 */
	public void setBackImage(String backImage) {
		this.backImage = backImage;
	}

	/**
	 * @return the leafNodes
	 */
	public Collection<TreeModel> getLeafNodes() {
		return leafNodes;
	}

	/**
	 * @param leafNodes
	 *            the leafNodes to set
	 */
	public void setLeafNodes(Collection<TreeModel> leafNodes) {
		this.leafNodes = leafNodes;
	}

	/**
	 * @return the open
	 */
	public boolean isOpen() {
		return open;
	}

	/**
	 * @param open
	 *            the open to set
	 */
	public void setOpen(boolean open) {
		this.open = open;
	}

	/**
	 * @return the click
	 */
	public String getClick() {
		return click;
	}

	/**
	 * @param click
	 *            the click to set
	 */
	public void setClick(String click) {
		this.click = click;
	}

	/**
	 * @return the treeType
	 */
	public String getTreeType() {
		return treeType;
	}

	/**
	 * @param treeType
	 *            the treeType to set
	 */
	public void setTreeType(String treeType) {
		this.treeType = treeType;
	}

	/**
	 * @return the icon
	 */
	public String getIcon() {
		return icon;
	}

	/**
	 * @param icon
	 *            the icon to set
	 */
	public void setIcon(String icon) {
		this.icon = icon;
	}

	/**
	 * @return the iconClose
	 */
	public String getIconClose() {
		return iconClose;
	}

	/**
	 * @param iconClose
	 *            the iconClose to set
	 */
	public void setIconClose(String iconClose) {
		this.iconClose = iconClose;
	}

	/**
	 * @return the iconOpen
	 */
	public String getIconOpen() {
		return iconOpen;
	}

	/**
	 * @param iconOpen
	 *            the iconOpen to set
	 */
	public void setIconOpen(String iconOpen) {
		this.iconOpen = iconOpen;
	}

	/**
	 * @param isLeaf
	 *            the isLeaf to set
	 */
	public void setLeaf(boolean isLeaf) {
		this.isLeaf = isLeaf;
	}

	/**
	 * @return the iconSkin
	 */
	public String getIconSkin() {
		return iconSkin;
	}

	/**
	 * @param iconSkin
	 *            the iconSkin to set
	 */
	public void setIconSkin(String iconSkin) {
		this.iconSkin = iconSkin;
	}

	/**
	 * @return the showIndex
	 */
	public String getShowIndex() {
		return showIndex;
	}

	/**
	 * @param showIndex
	 *            the showIndex to set
	 */
	public void setShowIndex(String showIndex) {
		this.showIndex = showIndex;
	}

	/**
	 * @return the favorite
	 */
	public boolean getFavorite() {
		return favorite;
	}

	/**
	 * @param favorite
	 *            the favorite to set
	 */
	public void setFavorite(boolean favorite) {
		this.favorite = favorite;
	}

	/**
	 *
	 * @return node type
	 */
	public String getNodeType() {
		return nodeType;
	}

	/**
	 *
	 * @param nodeType type of node
	 */
	public void setNodeType(String nodeType) {
		this.nodeType = nodeType;
	}

	/**
	 *
	 * @return
	 */
	public String getNodeBizType() {
		return nodeBizType;
	}

	/**
	 *
	 * @param nodeBizType
	 */
	public void setNodeBizType(String nodeBizType) {
		this.nodeBizType = nodeBizType;
	}

	public String getViewType() {
		return viewType;
	}

	public void setViewType(String viewType) {
		this.viewType = viewType;
	}

	public String getChkDisabled() {
		return chkDisabled;
	}

	public void setChkDisabled(String chkDisabled) {
		this.chkDisabled = chkDisabled;
	}

	public String getAppCode() {
		return appCode;
	}

	public void setAppCode(String appCode) {
		this.appCode = appCode;
	}
}
