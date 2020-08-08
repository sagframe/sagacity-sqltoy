/**
 * 
 */
package org.sagacity.sqltoy.model;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * @author zhong
 *
 */
public class DTOEntityMapModel implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 8851171361533403505L;

	public String dtoClassName;

	public String pojoClassName;

	public String[] dtoProps;

	public Map<String, Integer> dtoPropsIndex = new HashMap<String, Integer>();

	public Method[] dtoSetMethods;

	public Method[] dtoGetMethods;

	public String[] pojoProps;

	public Map<String, Integer> pojoPropsIndex = new HashMap<String, Integer>();

	public Method[] pojoSetMethods;

	public Method[] pojoGetMethods;

}
