package org.sagacity.sqltoy.config.model;

import java.io.Serializable;

import org.sagacity.sqltoy.model.SecureType;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * 
 * @author chenrenfei
 *
 */
public class FieldSecureConfig implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 7459628659046294643L;

	private String field;
	private SecureType secureType;
	private String sourceField;

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
