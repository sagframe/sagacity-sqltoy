/**
 * 
 */
package sqltoy.showcase;

import java.util.HashMap;
import java.util.Map;

import org.sagacity.sqltoy.plugin.IUnifyFieldsHandler;
import org.sagacity.sqltoy.utils.DateUtil;

/**
 * @project sqltoy-showcase
 * @description 统一字段赋值范例
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:SqlToyShowCaseUnifyFieldsHandler.java,Revision:v1.0,Date:2018年1月18日
 */
public class SqlToyShowCaseUnifyFieldsHandler implements IUnifyFieldsHandler {
	// 框架会避免对已经赋值的字段进行覆盖
	// 对没有的属性也会自动规避，因此冗余属性并不影响
	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.plugin.IUnifyFieldsHandler#createUnifyFields()
	 */
	@Override
	public Map<String, Object> createUnifyFields() {
		HashMap<String, Object> map = new HashMap<String, Object>();
		// 这里自行扩展,获取当前用户ID或机构ID等
		map.put("operator", "chenrenfei");
		map.put("operateDate", DateUtil.getNowTime());
		return map;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.plugin.IUnifyFieldsHandler#updateUnifyFields()
	 */
	@Override
	public Map<String, Object> updateUnifyFields() {
		HashMap<String, Object> map = new HashMap<String, Object>();
		map.put("operator", "chen");
		map.put("operateDate", DateUtil.getNowTime());
		map.put("updateDate", DateUtil.getNowTime());
		map.put("updateTime", DateUtil.getNowTime());
		return map;
	}

}
