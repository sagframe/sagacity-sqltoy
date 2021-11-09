package org.sagacity.sqltoy.callback;

import org.sagacity.sqltoy.model.IgnoreCaseSet;
import org.sagacity.sqltoy.plugins.secure.FieldsSecureProvider;

/**
 * @project sagacity-sqltoy
 * @description 解密处理器
 * @author zhongxuchen
 * @version v1.0,Date:2021-11-8
 */
public class DecryptHandler {
	/**
	 * 需要解密的字段
	 */
	private IgnoreCaseSet columns = new IgnoreCaseSet();

	/**
	 * 加解密逻辑实现类
	 */
	private FieldsSecureProvider fieldsSecureProvider;

	public DecryptHandler(FieldsSecureProvider fieldsSecureProvider, IgnoreCaseSet columns) {
		this.fieldsSecureProvider = fieldsSecureProvider;
		this.columns = columns;
	}

	/**
	 * @TODO 实现解密
	 * @param column
	 * @param value
	 * @return
	 */
	public Object decrypt(String column, Object value) {
		if (value == null || fieldsSecureProvider == null || column == null) {
			return value;
		}
		boolean exists = columns.contains(column);
		// 去除下划线
		if (!exists) {
			exists = columns.contains(column.replace("_", ""));
		}
		if (exists) {
			String content = value.toString();
			if (content.trim().equals("")) {
				return value;
			}
			return fieldsSecureProvider.decrypt(content);
		}
		return value;
	}

	public IgnoreCaseSet getColumns() {
		return columns;
	}

	public void setColumns(IgnoreCaseSet columns) {
		this.columns = columns;
	}

	public FieldsSecureProvider getFieldsSecureProvider() {
		return fieldsSecureProvider;
	}

	public void setFieldsSecureProvider(FieldsSecureProvider fieldsSecureProvider) {
		this.fieldsSecureProvider = fieldsSecureProvider;
	}

}
