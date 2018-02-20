/**
 * 
 */
package sqltoy.showcase.link;

import java.io.Serializable;
import java.util.List;

import org.sagacity.sqltoy.support.BaseDaoSupport;

/**
 * @project sqltoy-showcase
 * @description
 *              <p>
 *              链式操作演示
 *              </p>
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:LinkDaoDemo.java,Revision:v1.0,Date:2017年10月30日
 */
public class LinkDaoDemo extends BaseDaoSupport {
	public void saveEntities(List entities) throws Exception {
		super.save().batchSize(100).saveMode(UPDATE).many(entities);
		super.flush();
	}

	public Object save(Serializable vo) throws Exception {
		// super.findByCriteria(new Criteria(vo).)
		return super.save().one(vo);
	}
}
