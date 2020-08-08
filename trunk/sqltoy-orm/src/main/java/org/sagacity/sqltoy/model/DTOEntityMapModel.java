/**
 * 
 */
package org.sagacity.sqltoy.model;

import java.io.Serializable;

/**
 * @author zhong
 *
 */
public class DTOEntityMapModel implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 8851171361533403505L;

	public String[] dtoProps;

	public int[] dtoSetMethodIndexs;

	public int[] dtoGetMethodIndexs;

	public String[] pojoProps;

	public int[] pojoSetMethodIndexs;

	public int[] pojoGetMethodIndexs;

}
