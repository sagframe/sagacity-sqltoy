/**
 * 
 */
package org.sagacity.sqltoy.callback;

import java.util.HashMap;
import java.util.List;

/**
 * @project sagacity-sqltoy
 * @description 反射对象提取数据时，提供对数据的判断和修改
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:ReflectPropertyHandler.java,Revision:v1.0,Date:2012-7-14
 */
@SuppressWarnings("rawtypes")
public abstract class ReflectPropertyHandler {
	/**
	 * 集合数据的行号
	 */
	private int rowIndex = 0;

	private boolean isArray = true;

	/**
	 * 反射处理的对象属性以及属性顺序,reflectBean(entity,String[] properties) hashMap
	 */
	private HashMap<String, Integer> propertyIndexMap = new HashMap<String, Integer>();

	/**
	 * 单行数据值,集合反射时优先将该数据初始化,实现类通过process()设置和修改该数据，从而形成 数据交互
	 */
	private Object[] rowData;

	/**
	 * list 行数据模式
	 */
	private List rowList;

	/**
	 * 抽象方法,需要由实现类实现具体逻辑
	 */
	public abstract void process();

	/**
	 * @todo 提供process实现中设置具体属性的值
	 * @param property
	 * @param value
	 */
	@SuppressWarnings("unchecked")
	public void setValue(String property, Object value) {
		String key = property.toLowerCase();
		if (propertyIndexMap.containsKey(key)) {
			if (isArray) {
				rowData[propertyIndexMap.get(key)] = value;
			} else {
				rowList.set(propertyIndexMap.get(key), value);
			}
		}
	}

	/**
	 * @todo 获取属性的值
	 * @param property
	 * @return
	 */
	public Object getValue(String property) {
		String key = property.toLowerCase();
		if (propertyIndexMap.containsKey(key)) {
			if (isArray) {
				return rowData[propertyIndexMap.get(key)];
			}
			return rowList.get(propertyIndexMap.get(key));
		}
		return null;
	}

	public int getRowIndex() {
		return rowIndex;
	}

	public void setRowIndex(int rowIndex) {
		this.rowIndex = rowIndex;
	}

	public void setPropertyIndexMap(HashMap<String, Integer> propertyIndexMap) {
		this.propertyIndexMap = propertyIndexMap;
	}

	/**
	 * @return the propertyIndexMap
	 */
	public HashMap<String, Integer> getPropertyIndexMap() {
		return propertyIndexMap;
	}

	/**
	 * 取回
	 * 
	 * @return
	 */
	public Object[] getRowData() {
		return rowData;
	}

	/**
	 * 反射过程中调用，提供交互数据
	 * 
	 * @param rowData
	 */
	public void setRowData(Object[] rowData) {
		this.rowData = rowData;
		this.isArray = true;
	}

	/**
	 * @return the rowList
	 */
	public List getRowList() {
		return rowList;
	}

	/**
	 * @param rowList
	 *            the rowList to set
	 */
	public void setRowList(List rowList) {
		this.isArray = false;
		this.rowList = rowList;
	}

	/**
	 * @todo 当特定属性的值为一个给定值时，将反射的属性值设置为null
	 * @param value
	 * @param properties
	 */
	public void setEqualNull(Object value, String... properties) {
		for (String property : properties) {
			if (this.getValue(property) != null && this.getValue(property).equals(value)) {
				this.setValue(property, null);
			}
		}
	}

	public void setEqualNull(Object value, String property) {
		if (this.getValue(property) != null && this.getValue(property).equals(value)) {
			this.setValue(property, null);
		}
	}
}
