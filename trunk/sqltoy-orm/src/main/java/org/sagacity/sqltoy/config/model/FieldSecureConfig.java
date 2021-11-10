package org.sagacity.sqltoy.config.model;

import java.io.Serializable;

import org.sagacity.sqltoy.model.SecureType;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * @project sagacity-sqltoy
 * @description 字段加解密配置模型
 * @author zhongxuchen
 * @version v1.0,Date:2021-11-05
 */
public class FieldSecureConfig implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 7459628659046294643L;

	/**
	 * 字段
	 */
	private String field;

	/**
	 * 安全处理类型(加密或脱敏)
	 */
	private SecureType secureType;

	/**
	 * 脱敏字段对应的加密字段
	 */
	private String sourceField;

	/**
	 * 脱敏处理类型
	 */
	private SecureMask mask;

	public FieldSecureConfig(String field, SecureType secureType, String sourceField, String maskCode, int headSize,
			int tailSize, int maskRate) {
		this.field = field;
		this.secureType = secureType;
		this.sourceField = StringUtil.isBlank(sourceField) ? null : sourceField;
		if (secureType != null && !secureType.equals(SecureType.ENCRYPT)) {
			this.mask = new SecureMask();
			this.mask.setType(secureType.getValue());
			this.mask.setMaskCode(maskCode);
			this.mask.setHeadSize(headSize);
			this.mask.setTailSize(tailSize);
			this.mask.setMaskRate(maskRate);
		}
	}

	public String getField() {
		return field;
	}

	public SecureType getSecureType() {
		return secureType;
	}

	public String getSourceField() {
		return sourceField;
	}

	public SecureMask getMask() {
		return mask;
	}

}
