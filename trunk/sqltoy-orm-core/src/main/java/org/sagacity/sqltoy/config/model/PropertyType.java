package org.sagacity.sqltoy.config.model;

import java.io.Serializable;

/**
 * 
 */
public class PropertyType implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -5740427227356112255L;

	private String property;

	private int index;

	private Class type;

	private int typeValue;

	private String typeName;
	
	private Class genericType;

	public String getProperty() {
		return property;
	}

	public void setProperty(String property) {
		this.property = property;
	}

	public int getIndex() {
		return index;
	}

	public void setIndex(int index) {
		this.index = index;
	}

	public Class getType() {
		return type;
	}

	public void setType(Class type) {
		this.type = type;
	}

	public String getTypeName() {
		return typeName;
	}

	public void setTypeName(String typeName) {
		this.typeName = typeName;
	}

	public int getTypeValue() {
		return typeValue;
	}

	public void setTypeValue(int typeValue) {
		this.typeValue = typeValue;
	}

	public Class getGenericType() {
		return genericType;
	}

	public void setGenericType(Class genericType) {
		this.genericType = genericType;
	}

}
