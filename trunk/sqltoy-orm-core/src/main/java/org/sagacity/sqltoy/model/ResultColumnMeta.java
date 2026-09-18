package org.sagacity.sqltoy.model;

import java.util.Locale;

import org.sagacity.sqltoy.utils.GeometryTypeUtil;

/**
 * update 2026-9-12 查询结果列元数据(优化步骤2):将查询链路上按列下标对齐的并行数组
 * (labelNames[]/labelTypes[]/columnTypeNames[]/columnKinds[]/columnJdbcTypes[]中的
 * 分类与标记信息)收敛为单列对象,由ResultUtils.resolveColumns按ResultSetMetaData
 * 查询级一次性构建(元数据对同一结果集恒定,行循环零新增分配),整体向后传递替代 多数组同步下标维护。实体绑定机制数组(setter
 * Method/泛型类型等)不入本对象—— 本对象只承载"列是什么、怎么读"的分类事实,BeanUtil.reflectRowToBean的既有
 * 数组签名保持不变。
 */
public class ResultColumnMeta {

	/**
	 * 扩展类型标记:查询列是否为json/jsonb/vector/geometry及其具体类别。
	 * 来源双通道:①列类型名判定(原生json/vector/ST_GEOMETRY等,Map与VO路径统一);
	 * ②VO场景@Column(type=JSON)等注解标注优先(承载形态如nvarchar列标注json时
	 * 类型名为NVARCHAR,仅靠类型名会漏标)。标记值经normalizeExtTypeValue/
	 * BeanUtil.convertType消费,预期逐步替代散落的字符串比较。
	 */
	public enum ExtType {
		NONE, JSON, JSONB, VECTOR, GEOMETRY;

		/**
		 * 按列类型名判定扩展类型(与原processResultSet的columnJdbcTypes类型名检测
		 * 链逐字对齐:JSON/JSONB/空间类型族(isGeometryTypeName)/向量类型族
		 * (VECTOR/FLOATVECTOR/REAL_VECTOR),其余NONE)
		 */
		public static ExtType of(String columnTypeName) {
			if (columnTypeName == null) {
				return NONE;
			}
			String t = columnTypeName.toUpperCase(Locale.ROOT).replace("\"", "");
			if (t.equals("JSON")) {
				return JSON;
			}
			if (t.equals("JSONB")) {
				return JSONB;
			}
			if (GeometryTypeUtil.isGeometryTypeName(t)) {
				return GEOMETRY;
			}
			if (t.equals("VECTOR") || t.equals("FLOATVECTOR") || t.equals("REAL_VECTOR")) {
				return VECTOR;
			}
			return NONE;
		}
	}

	/**
	 * 列读取策略(原ResultUtils.buildColumnKinds的columnKinds[]枚举化):
	 * NORMAL常规getObject;TEXT_READ文本化读取(oracle的JSON/VECTOR列getObject直接抛
	 * ORA-17004/18722、db2gse的ST_Geometry读回驱动混淆对象,getString规避);
	 * EXT_BYTE取值后按列类型归一(sqlserver/mysql系的vector与geometry byte[]内部格式、 h2的json
	 * byte[]、hana的ST_GEOMETRY标准WKB byte[])。
	 */
	public enum ReadStrategy {
		NORMAL, TEXT_READ, EXT_BYTE;

		/**
		 * 由原int常量(ResultUtils.COLUMN_NORMAL/COLUMN_TEXT_READ/COLUMN_EXT_BYTE,
		 * 取值0/1/2与ordinal一致)转换,防御未知取值归NORMAL
		 */
		public static ReadStrategy of(int kind) {
			if (kind == 1) {
				return TEXT_READ;
			}
			if (kind == 2) {
				return EXT_BYTE;
			}
			return NORMAL;
		}
	}

	/** ExtType对应的jdbc类型标记(供VO绑定分派,与原columnJdbcTypes类型名检测链一致) */
	public static int jdbcTypeOf(ExtType extType) {
		if (extType == null) {
			return 0;
		}
		switch (extType) {
		case JSON:
			return JdbcTypes.JSON;
		case JSONB:
			return JdbcTypes.JSONB;
		case VECTOR:
			return JdbcTypes.VECTOR;
		case GEOMETRY:
			return JdbcTypes.GEOMETRY;
		default:
			return 0;
		}
	}

	/** jdbc类型标记反向映射为ExtType(非四种扩展类型返回null,调用方保留原标记不覆盖) */
	public static ExtType extTypeOfJdbc(int jdbcType) {
		if (jdbcType == JdbcTypes.JSON) {
			return ExtType.JSON;
		}
		if (jdbcType == JdbcTypes.JSONB) {
			return ExtType.JSONB;
		}
		if (jdbcType == JdbcTypes.VECTOR) {
			return ExtType.VECTOR;
		}
		if (jdbcType == JdbcTypes.GEOMETRY) {
			return ExtType.GEOMETRY;
		}
		return null;
	}

	/** 结果列标签(已按sqltoy的columnLabelUpperOrLower配置规整大小写) */
	private final String label;

	/** 标签小写形态(查询级预计算,翻译/解密/Map定位用;label为null时为null) */
	private final String lowLabel;

	/** jdbc 1-based列下标(结果集存在分页包装列偏移时,取值下标须叠加调用方startColIndex) */
	private final int labelIndex;

	/** 驱动原始列类型名(不受strTypeCols的VARCHAR覆写影响,归一化判定专用) */
	private final String dataTypeName;

	/** 对外呈现的列类型(labelTypes形态,缓存翻译/格式化列被覆写为VARCHAR) */
	private final String labelType;

	/** 列读取策略(查询级预分类,行循环零字符串判定) */
	private final ReadStrategy readStrategy;

	/** 扩展类型标记(类型名派生,VO注解可覆盖) */
	private ExtType extType;

	/**
	 * VO绑定的jdbc类型标记(@Column(type=JSON)等注解优先,类型名兜底,非扩展列为0;
	 * 供BeanUtil.reflectRowToBean做jdbc值到POJO的转换分派,非VO场景无意义)
	 */
	private int jdbcType;

	/** VO映射的属性名(非VO场景为null) */
	private String propertyName;

	public ResultColumnMeta(String label, String lowLabel, int labelIndex, String dataTypeName, String labelType,
			ReadStrategy readStrategy, ExtType extType, int jdbcType, String propertyName) {
		super();
		this.label = label;
		this.lowLabel = lowLabel;
		this.labelIndex = labelIndex;
		this.dataTypeName = dataTypeName;
		this.labelType = labelType;
		this.readStrategy = readStrategy;
		this.extType = extType;
		this.jdbcType = jdbcType;
		this.propertyName = propertyName;
	}

	public String getLabel() {
		return label;
	}

	public String getLowLabel() {
		return lowLabel;
	}

	public int getLabelIndex() {
		return labelIndex;
	}

	public String getDataTypeName() {
		return dataTypeName;
	}

	public String getLabelType() {
		return labelType;
	}

	public ReadStrategy getReadStrategy() {
		return readStrategy;
	}

	public ExtType getExtType() {
		return extType;
	}

	public int getJdbcType() {
		return jdbcType;
	}

	public String getPropertyName() {
		return propertyName;
	}

	/**
	 * VO路径的绑定信息回填(propertyName/jdbcType/extType),由ResultUtils在
	 * 解析出属性映射与字段注解jdbcType后调用(注解优先原则,见原columnJdbcTypes构建逻辑)
	 */
	public void applyVoBinding(String propertyName, int jdbcType, ExtType extType) {
		this.propertyName = propertyName;
		this.jdbcType = jdbcType;
		if (extType != null) {
			this.extType = extType;
		}
	}

	@Override
	public String toString() {
		return "ResultColumnMeta [label=" + label + ", labelIndex=" + labelIndex + ", dataTypeName=" + dataTypeName
				+ ", readStrategy=" + readStrategy + ", extType=" + extType + ", jdbcType=" + jdbcType
				+ ", propertyName=" + propertyName + "]";
	}
}
