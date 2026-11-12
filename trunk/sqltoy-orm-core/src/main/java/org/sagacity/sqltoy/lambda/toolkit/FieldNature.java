package org.sagacity.sqltoy.lambda.toolkit;

/**
 * @description 实体属性与所属实体类的二元组,多表join场景通过lambda推断属性归属的实体
 *              (jre8同步适配:6.0为record,改为等价不可变类)
 * @author zhongxuchen
 */
public final class FieldNature {
	private final Class<?> entityClass;
	private final String fieldName;

	public FieldNature(Class<?> entityClass, String fieldName) {
		this.entityClass = entityClass;
		this.fieldName = fieldName;
	}

	public Class<?> entityClass() {
		return entityClass;
	}

	public String fieldName() {
		return fieldName;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof FieldNature)) {
			return false;
		}
		FieldNature other = (FieldNature) obj;
		return entityClass == other.entityClass && (fieldName == null ? other.fieldName == null
				: fieldName.equals(other.fieldName));
	}

	@Override
	public int hashCode() {
		int result = (entityClass == null) ? 0 : entityClass.hashCode();
		result = 31 * result + ((fieldName == null) ? 0 : fieldName.hashCode());
		return result;
	}

	@Override
	public String toString() {
		return "FieldNature[entityClass=" + entityClass + ", fieldName=" + fieldName + "]";
	}
}
