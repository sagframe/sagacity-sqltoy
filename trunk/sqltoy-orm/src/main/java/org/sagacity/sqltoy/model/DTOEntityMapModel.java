/**
 * 
 */
package org.sagacity.sqltoy.model;

import java.io.Serializable;
import java.lang.reflect.Method;

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

	public Method[] dtoSetMethods;

	public Method[] dtoGetMethods;

	public String[] pojoProps;

	public Method[] pojoSetMethods;

	public Method[] pojoGetMethods;

}
