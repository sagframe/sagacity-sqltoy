/**
 * 
 */
package org.sagacity.sqltoy.criteria;

import java.util.ArrayList;
import java.util.List;

/**
 * @project sagacity-sqltoy4.0
 * @description 请在此说明类的功能
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:Restrictions.java,Revision:v1.0,Date:2018年1月12日
 */
public class Restrictions {
	/**
	 * sql片段
	 */
	private List<String> sqlFragments = new ArrayList<String>();

	public Restrictions or(Restrictions... restrictions) {
		Restrictions re = new Restrictions();
		return re;
	}

	public Restrictions and(Restrictions... restrictions) {
		return this;
	}

	public Restrictions logic(String filed, Compare compare, Object value) {
		return this;
	}

}
