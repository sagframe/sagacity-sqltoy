package org.sagacity.sqltoy.config;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.annotation.BusinessId;
import org.sagacity.sqltoy.config.annotation.Column;
import org.sagacity.sqltoy.config.annotation.DataVersion;
import org.sagacity.sqltoy.config.annotation.Entity;
import org.sagacity.sqltoy.config.annotation.Id;
import org.sagacity.sqltoy.config.annotation.ListSql;
import org.sagacity.sqltoy.config.annotation.LoadSql;
import org.sagacity.sqltoy.config.annotation.OneToMany;
import org.sagacity.sqltoy.config.annotation.OneToOne;
import org.sagacity.sqltoy.config.annotation.PaginationSql;
import org.sagacity.sqltoy.config.annotation.PartitionKey;
import org.sagacity.sqltoy.config.annotation.Secure;
import org.sagacity.sqltoy.config.annotation.SecureConfig;
import org.sagacity.sqltoy.config.annotation.Sharding;
import org.sagacity.sqltoy.config.annotation.Strategy;
import org.sagacity.sqltoy.config.annotation.Tenant;
import org.sagacity.sqltoy.config.model.DataVersionConfig;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.FieldMeta;
import org.sagacity.sqltoy.config.model.FieldSecureConfig;
import org.sagacity.sqltoy.config.model.PKStrategy;
import org.sagacity.sqltoy.config.model.ShardingConfig;
import org.sagacity.sqltoy.config.model.ShardingStrategyConfig;
import org.sagacity.sqltoy.config.model.TableCascadeModel;
import org.sagacity.sqltoy.model.IgnoreCaseSet;
import org.sagacity.sqltoy.model.SecureType;
import org.sagacity.sqltoy.plugins.id.IdGenerator;
import org.sagacity.sqltoy.plugins.id.impl.RedisIdGenerator;
import org.sagacity.sqltoy.utils.BeanUtil;
import org.sagacity.sqltoy.utils.ReservedWordsUtil;
import org.sagacity.sqltoy.utils.SqlUtil;
import org.sagacity.sqltoy.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sagacity-sqltoy
 * @description 通过注解解析实体对象,得到其跟数据库表的对应关系,并形成相应表增删改查的语句
 * @author zhongxuchen
 * @version v1.0,Date:2012-6-1
 * @modify {Date:2017-10-13,分解之前的parseEntityMeta大方法,进行代码优化}
 * @modify {Date:2018-1-22,增加业务主键配置策略}
 * @modify {Date:2018-9-6,优化增强业务主键配置策略}
 * @modify {Date:2019-8-10,优化字段的解析,避免在子类中定义属性覆盖了父类导致数据库字段失效现象,同时优化部分代码}
 * @modify {Date:2020-07-29,修复OneToMany解析时编写错误,由智客软件反馈 }
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public class EntityManager {
	/**
	 * 定义全局日志
	 */
	protected final static Logger logger = LoggerFactory.getLogger(EntityManager.class);

	/**
	 * 存放主键生成策略实例
	 */
	private static HashMap<String, IdGenerator> idGenerators = new HashMap<String, IdGenerator>();

	/**
	 * 定义常用的主键生成方式类名称
	 */
	private static HashMap<String, String> IdGenerators = new HashMap<String, String>() {
		/**
		 * 
		 */
		private static final long serialVersionUID = 3964534243191167226L;
		{
			// 13位当前毫秒+6位纳秒+3位主机ID 构成的22位不重复的ID
			put("default", "DefaultIdGenerator");
			// 32位uuid
			put("uuid", "UUIDGenerator");
			put("redis", "RedisIdGenerator");
			// 26位
			put("nanotime", "NanoTimeIdGenerator");
			// 16位雪花算法
			put("snowflake", "SnowflakeIdGenerator");
			// default的命名容错
			put("defaultidgenerator", "DefaultIdGenerator");
			put("defaultgenerator", "DefaultIdGenerator");
			put("nanotimeidgenerator", "NanoTimeIdGenerator");
			// 雪花算法命名容错
			put("snowflakeidgenerator", "SnowflakeIdGenerator");
			put("uuidgenerator", "UUIDGenerator");
			put("redisidgenerator", "RedisIdGenerator");
		}
	};

	/**
	 * id产生器的包路径(针对类名补充包路径，直接包含了包路径的除外)
	 */
	private static final String IdGeneratorPackage = "org.sagacity.sqltoy.plugins.id.impl.";

	/**
	 * 扫描的包(意义不大,sqltoy已经改为在使用时自动加载)
	 */
	@Deprecated
	private String[] packagesToScan;

	/**
	 * 是否循环迭代下级包目录
	 */
	private boolean recursive = true;

	/**
	 * 指定的entity class(意义不大,sqltoy已经改为用时自动加载)
	 */
	@Deprecated
	private String[] annotatedClasses;

	/**
	 * 存放每个对象跟数据库表的关系信息
	 */
	private ConcurrentHashMap<String, EntityMeta> entitysMetaMap = new ConcurrentHashMap<String, EntityMeta>();

	/**
	 * 存放表名称对应实体对象类名称，再结合entitysMetaMap获取对象的entityMeta
	 */
	private ConcurrentHashMap<String, String> tableEntityNameMap = new ConcurrentHashMap<String, String>();

	/**
	 * 非sqltoy entity类,一般指仅用于查询作为返回结果的VO
	 */
	private ConcurrentHashMap<String, String> unEntityMap = new ConcurrentHashMap<String, String>();

	/**
	 * @TODO 判断是否是实体对象
	 * @param sqlToyContext
	 * @param voClass
	 * @return
	 */
	public boolean isEntity(SqlToyContext sqlToyContext, Class voClass) {
		if (voClass == null || voClass.equals(Object.class)) {
			return false;
		}
		Class entityClass = BeanUtil.getEntityClass(voClass);
		String className = entityClass.getName();
		if (unEntityMap.containsKey(className)) {
			return false;
		}
		if (entitysMetaMap.containsKey(className)) {
			return true;
		}
		EntityMeta entityMeta = parseEntityMeta(sqlToyContext, entityClass, false, false);
		if (entityMeta != null) {
			return true;
		}
		unEntityMap.put(className, "1");
		return false;
	}

	/**
	 * @todo <b>获取Entity类的对应数据库表信息，如：查询、修改、插入sql、对象属性跟表字段之间的关系等信息</b>
	 * @param sqlToyContext
	 * @param voClass
	 * @return
	 */
	public EntityMeta getEntityMeta(SqlToyContext sqlToyContext, Class voClass) {
		if (voClass == null || voClass.equals(Object.class)) {
			return null;
		}
		Class entityClass = BeanUtil.getEntityClass(voClass);
		String className = entityClass.getName();
		EntityMeta entityMeta = entitysMetaMap.get(className);
		// update 2017-11-27
		// 增加在使用对象时动态解析的功能,因此可以不用配置packagesToScan和annotatedClasses
		if (entityMeta == null) {
			entityMeta = parseEntityMeta(sqlToyContext, entityClass, true, false);
			if (entityMeta == null) {
				throw new IllegalArgumentException("您传入的对象:[".concat(className)
						.concat(" ]不是一个@SqlToyEntity实体POJO对象,sqltoy实体对象必须使用 @SqlToyEntity/@Entity/@Id 等注解来标识!"));
			} // update 2022-10-24 加强提示，避免一些手工编写pojo情景遇到问题不知所措(手工编写是因为根本不了解quickvo的特性)
			else if (entityMeta.getFieldsArray() == null || entityMeta.getFieldsArray().length == 0) {
				throw new RuntimeException(
						"您传入的对象:[".concat(className).concat(" ] 没有@column等配置,无法获得POJO属性映射数据库字段的关系,请用quickvo自动生成POJO!"));
			}
		}
		return entityMeta;
	}

	/**
	 * @todo 初始化加载扫描entity类，解析实体类跟数据库之间的关系，并生成相应的数据库操作信息
	 * @param sqlToyContext
	 * @throws Exception
	 */
	public void initialize(SqlToyContext sqlToyContext) throws Exception {
		Set<Class<?>> entities = new LinkedHashSet<Class<?>>();
		// 扫描并获取包以及包下层包中的sqltoy entity对象
		if (packagesToScan != null && packagesToScan.length > 0) {
			for (String pkg : this.packagesToScan) {
				entities.addAll(ScanEntityAndSqlResource.getPackageEntities(pkg.trim(), recursive, "UTF-8"));
			}
		}
		// 加载直接指定的sqltoy entity对象
		if (annotatedClasses != null && annotatedClasses.length > 0) {
			Class entityClass;
			for (String annotationClass : annotatedClasses) {
				try {
					entityClass = Thread.currentThread().getContextClassLoader().loadClass(annotationClass);
					if (ScanEntityAndSqlResource.isSqlToyEntity(entityClass)) {
						entities.add(entityClass);
					}
				} catch (ClassNotFoundException e) {
					// log.error("添加用户自定义实体POJO类错误 找不到此类的.class文件");
					e.printStackTrace();
				}
			}
		}
		// 解析entity对象的注解并放入缓存
		for (Class entityClass : entities) {
			parseEntityMeta(sqlToyContext, entityClass, true, false);
		}
	}

	/**
	 * @todo <b>解析sqltoy entity对象获取其跟数据库相关的配置信息</b>
	 * @param sqlToyContext
	 * @param entityClass
	 * @param isWarn        当不是entity实体bean时是否进行日志提示
	 * @param forCascade    针对级联内部解析
	 * @return
	 */
	public synchronized EntityMeta parseEntityMeta(SqlToyContext sqlToyContext, Class entityClass, boolean isWarn,
			boolean forCascade) {
		if (entityClass == null || entityClass.equals(Object.class)) {
			return null;
		}
		String className = entityClass.getName();
		// 避免重复解析
		if (entitysMetaMap.containsKey(className)) {
			return entitysMetaMap.get(className);
		}
		EntityMeta entityMeta = null;
		try {
			Class realEntityClass = entityClass;
			Entity entity = null;
			// 数据版本
			DataVersion dataVersion = null;
			// 租户
			Tenant tenant = null;
			while (!realEntityClass.equals(Object.class)) {
				entity = (Entity) realEntityClass.getAnnotation(Entity.class);
				if (dataVersion == null) {
					dataVersion = (DataVersion) realEntityClass.getAnnotation(DataVersion.class);
				}
				if (tenant == null) {
					tenant = (Tenant) realEntityClass.getAnnotation(Tenant.class);
				}
				if (entity != null) {
					break;
				}
				realEntityClass = realEntityClass.getSuperclass();
			}
			// 是实体类则开始解析类上的其它注解配置
			if (entity != null) {
				entityMeta = new EntityMeta();
				entityMeta.setEntityClass(realEntityClass);
				// 表名
				entityMeta.setTableName(entity.tableName());
				if (StringUtil.isNotBlank(entity.schema())) {
					entityMeta.setSchema(entity.schema());
				}
				// 解析自定义注解
				parseCustomAnnotation(entityMeta, entityClass);
				// 主键约束(已经废弃)
				if (StringUtil.isNotBlank(entity.pk_constraint())) {
					entityMeta.setPkConstraint(entity.pk_constraint());
				}
				if (tenant != null && !tenant.field().equals("")) {
					entityMeta.setTenantField(tenant.field());
				}
				// 解析Entity包含的字段信息
				Field[] allFields = parseAllFields(entityClass);
				// 排除主键的字段信息
				List<String> rejectIdFieldList = new ArrayList<String>();
				// 表的所有字段
				List<String> fieldList = new ArrayList<String>();
				// 主键
				List<String> idList = new ArrayList<String>();
				// 解析主键
				parseIdFileds(idList, allFields);
				// 构造按照主键获取单条记录的sql,以:named形式
				StringBuilder loadNamedWhereSql = new StringBuilder("");
				// where 主键字段=? 形式，用于构建delete功能操作的sql
				StringBuilder loadArgWhereSql = new StringBuilder("");
				List<String> allColumnNames = new ArrayList<String>();
				String dataVersionField = null;
				for (Field field : allFields) {
					// 解析对象字段属性跟数据库表字段的对应关系
					parseFieldMeta(sqlToyContext, entityMeta, field, rejectIdFieldList, allColumnNames,
							loadNamedWhereSql, loadArgWhereSql);
					if (dataVersion == null) {
						dataVersion = (DataVersion) field.getAnnotation(DataVersion.class);
						if (dataVersion != null) {
							dataVersionField = field.getName();
						}
					}
					if (tenant == null) {
						tenant = (Tenant) field.getAnnotation(Tenant.class);
						if (tenant != null) {
							entityMeta.setTenantField(field.getName());
						}
					}
				}
				// 设置数据库表所有字段信息
				StringBuilder allColNames = new StringBuilder();
				for (int i = 0; i < allColumnNames.size(); i++) {
					if (i > 0) {
						allColNames.append(",");
					}
					allColNames.append(ReservedWordsUtil.convertWord(allColumnNames.get(i), null));
				}
				entityMeta.setAllColumnNames(allColNames.toString());
				// 表全量查询语句 update 2019-12-9 将原先select * 改成 select 具体字段
				entityMeta.setLoadAllSql("select ".concat(entityMeta.getAllColumnNames()).concat(" from ")
						.concat(entityMeta.getSchemaTable(null, null)));
				entityMeta.setIdArgWhereSql(loadArgWhereSql.toString());
				entityMeta.setIdNameWhereSql(loadNamedWhereSql.toString());
				// 排除主键外的字段
				if (rejectIdFieldList.size() > 0) {
					entityMeta.setRejectIdFieldArray(rejectIdFieldList.toArray(new String[rejectIdFieldList.size()]));
					fieldList.addAll(rejectIdFieldList);
				}
				// 存在主键，主键必须放在fieldList最后面，影响到insert，update等语句
				if (idList.size() > 0) {
					entityMeta.setIdArray(idList.toArray(new String[idList.size()]));
					fieldList.addAll(idList);
					// 注解未定义load sql则提供主键为条件的查询为loadsql
					if (StringUtil.isBlank(entityMeta.getLoadSql(null))) {
						entityMeta.setLoadSql(entityMeta.getLoadAllSql().concat(loadNamedWhereSql.toString()));
					}
				}
				// 内部存在逻辑设置allFields
				entityMeta.setFieldsArray(fieldList.toArray(new String[rejectIdFieldList.size() + idList.size()]));
				// 设置字段类型和默认值
				parseFieldTypeAndDefault(entityMeta);
				// 解析sharding策略
				parseSharding(entityMeta, entityClass);
				// 解析加解密配置
				parseSecureConfig(entityMeta, entityClass);
				// 数据版本
				if (dataVersion != null) {
					if (dataVersionField == null) {
						dataVersionField = dataVersion.field();
					}
					FieldMeta fieldMeta = entityMeta.getFieldMeta(dataVersionField);
					if (fieldMeta != null) {
						DataVersionConfig dataVersionConfig = new DataVersionConfig();
						dataVersionConfig.setField(dataVersionField);
						// 202209181至少9位数字
						if (dataVersion.startDate() && fieldMeta.getLength() > 8) {
							dataVersionConfig.setStartDate(true);
						}
						entityMeta.setDataVersion(dataVersionConfig);
					} else {
						throw new RuntimeException(
								"@DataVersion(field=" + dataVersionField + ") 在POJO类:" + className + " 中没有对应的属性!");
					}
				}
				// 校验@Tenant(field="fieldName") 配置的正确性
				if (entityMeta.getTenantField() != null
						&& entityMeta.getFieldMeta(entityMeta.getTenantField()) == null) {
					throw new RuntimeException(
							"@Tenant(field=" + entityMeta.getTenantField() + ") 在POJO类:" + className + " 中没有对应的属性!");
				}
				// 判断是否为级联解析,级联解析无需再进行下级级联解析
				if (!forCascade) {
					// oneToMany和oneToOne解析
					for (Field field : allFields) {
						parseCascade(sqlToyContext, entityMeta, entity, field, idList);
					}
					// 设置级联关联对象类型
					if (!entityMeta.getCascadeModels().isEmpty()) {
						Class[] cascadeTypes = new Class[entityMeta.getCascadeModels().size()];
						for (int i = 0; i < entityMeta.getCascadeModels().size(); i++) {
							cascadeTypes[i] = entityMeta.getCascadeModels().get(i).getMappedType();
						}
						entityMeta.setCascadeTypes(cascadeTypes);
					}
				}
			}
		} catch (Exception e) {
			logger.error("Sqltoy 解析Entity对象:[{}]发生错误,请检查对象注解是否正确!" + e.getMessage(), className);
			throw e;
		}
		// 非内部级联解析，放入map缓存
		if (!forCascade) {
			if (entityMeta != null) {
				entitysMetaMap.put(className, entityMeta);
				tableEntityNameMap.put(entityMeta.getTableName().toLowerCase(), className);
			} else if (isWarn) {
				logger.warn("SqlToy Entity:{}没有使用@Entity注解，表明不是一个实体类,请检查!", className);
			}
		}
		return entityMeta;
	}

	/**
	 * @todo 解析自定义注解
	 * @param entityMeta
	 * @param entityClass
	 */
	private void parseCustomAnnotation(EntityMeta entityMeta, Class entityClass) {
		// 单记录查询的自定义语句
		if (entityClass.isAnnotationPresent(LoadSql.class)) {
			LoadSql loadSql = (LoadSql) entityClass.getAnnotation(LoadSql.class);
			entityMeta.setLoadSql(loadSql.value());
		}

		// 分页查询的语句
		if (entityClass.isAnnotationPresent(PaginationSql.class)) {
			PaginationSql paginationSql = (PaginationSql) entityClass.getAnnotation(PaginationSql.class);
			entityMeta.setPageSql(paginationSql.value());
		}

		// 集合查询语句
		if (entityClass.isAnnotationPresent(ListSql.class)) {
			ListSql listSql = (ListSql) entityClass.getAnnotation(ListSql.class);
			entityMeta.setListSql(listSql.value());
		}
	}

	/**
	 * @todo 解析分库分表策略
	 * @param entityMeta
	 * @param entityClass
	 */
	private void parseSharding(EntityMeta entityMeta, Class entityClass) {
		Class classType = entityClass;
		Sharding sharding = null;
		// 增加递归对父类检测
		while (classType != null && !classType.equals(Object.class)) {
			sharding = (Sharding) classType.getAnnotation(Sharding.class);
			if (sharding != null) {
				break;
			}
			classType = classType.getSuperclass();
		}
		// 不存在分库策略
		if (sharding == null) {
			return;
		}
		// 分库策略
		ShardingConfig shardingConfig = new ShardingConfig();
		// 最大并行数量
		shardingConfig.setMaxConcurrents(sharding.maxConcurrents());
		// 最大执行时长(秒)
		shardingConfig.setMaxWaitSeconds(sharding.maxWaitSeconds());
		// 异常处理策略(是否全局回滚)
		shardingConfig.setGlobalRollback(sharding.is_global_rollback());
		Strategy shardingDB = sharding.db();
		String strategy = shardingDB.name();
		// 分库策略
		if (StringUtil.isNotBlank(strategy)) {
			ShardingStrategyConfig config = new ShardingStrategyConfig(0);
			config.setFields(shardingDB.fields());
			// 别名,如果没有设置则将fields作为默认别名,别名的目的在于共用sharding策略中的参数名称
			String[] aliasNames = new String[shardingDB.fields().length];
			System.arraycopy(shardingDB.fields(), 0, aliasNames, 0, aliasNames.length);
			if (shardingDB.aliasNames() != null) {
				System.arraycopy(shardingDB.aliasNames(), 0, aliasNames, 0, shardingDB.aliasNames().length);
			}
			config.setAliasNames(aliasNames);
			config.setDecisionType(shardingDB.decisionType());
			config.setStrategy(strategy);
			shardingConfig.setShardingDBStrategy(config);
		}
		// 分表策略
		Strategy shardingTable = sharding.table();
		strategy = shardingTable.name();
		if (StringUtil.isNotBlank(strategy)) {
			ShardingStrategyConfig config = new ShardingStrategyConfig(1);
			config.setFields(shardingTable.fields());
			// 别名,如果没有设置则将fields作为默认别名,别名的目的在于共用sharding策略中的参数名称
			String[] aliasNames = new String[shardingTable.fields().length];
			System.arraycopy(shardingTable.fields(), 0, aliasNames, 0, aliasNames.length);
			if (shardingTable.aliasNames() != null) {
				System.arraycopy(shardingTable.aliasNames(), 0, aliasNames, 0, shardingTable.aliasNames().length);
			}
			config.setTables(new String[] { entityMeta.getTableName() });
			config.setAliasNames(aliasNames);
			config.setDecisionType(shardingDB.decisionType());
			config.setStrategy(strategy);
			shardingConfig.setShardingTableStrategy(config);
		}
		// 必须有一个策略是存在的
		if (shardingConfig.getShardingDBStrategy() != null || shardingConfig.getShardingTableStrategy() != null) {
			entityMeta.setShardingConfig(shardingConfig);
		}
	}

	/**
	 * @todo 解析加解密配置
	 * @param entityMeta
	 * @param entityClass
	 */
	private void parseSecureConfig(EntityMeta entityMeta, Class entityClass) {
		Class classType = entityClass;
		SecureConfig secureConfig = null;
		// 增加递归对父类检测
		while (classType != null && !classType.equals(Object.class)) {
			secureConfig = (SecureConfig) classType.getAnnotation(SecureConfig.class);
			if (secureConfig != null) {
				break;
			}
			classType = classType.getSuperclass();
		}
		// 不存在加解密配置
		if (secureConfig == null) {
			return;
		}
		Secure[] secures = secureConfig.secures();
		if (secures != null && secures.length > 0) {
			IgnoreCaseSet secureColumns = new IgnoreCaseSet();
			String field;
			FieldMeta fieldMeta;
			for (Secure secure : secures) {
				field = secure.field();
				fieldMeta = entityMeta.getFieldMeta(field);
				if (fieldMeta != null) {
					// 加密
					if (secure.secureType().equals(SecureType.ENCRYPT)) {
						secureColumns.add(fieldMeta.getColumnName());
						entityMeta
								.addSecureField(new FieldSecureConfig(field, SecureType.ENCRYPT, null, null, 0, 0, 0));
					} else {
						// 依据加密字段进行脱敏保存
						entityMeta
								.addSecureField(new FieldSecureConfig(field, secure.secureType(), secure.sourceField(),
										secure.maskCode(), secure.headSize(), secure.tailSize(), secure.maskRate()));
					}
				}
			}
			// 加密字段
			if (!secureColumns.isEmpty()) {
				entityMeta.setSecureColumns(secureColumns);
			}
		}
	}

	/**
	 * @todo 解析主键字段
	 * @param idList
	 * @param allFields
	 */
	private void parseIdFileds(List<String> idList, Field[] allFields) {
		// 优先提取id集合,有利于统一主键在子表操作中的顺序
		for (Field field : allFields) {
			// 判断字段是否为主键
			if (field.getAnnotation(Id.class) != null) {
				idList.add(field.getName());
			}
		}
	}

	/**
	 * @TODO 解析获取entity对象的属性
	 * @param entityClass
	 * @return
	 */
	private Field[] parseAllFields(Class entityClass) {
		Set<String> fieldSet = new HashSet<String>();
		List<Field> allFields = new ArrayList<Field>();
		Class classType = entityClass;
		String fieldName;
		while (classType != null && !classType.equals(Object.class)) {
			for (Field field : classType.getDeclaredFields()) {
				fieldName = field.getName().toLowerCase();
				if (!fieldSet.contains(fieldName)
						&& (field.getAnnotation(Column.class) != null || field.getAnnotation(OneToMany.class) != null
								|| field.getAnnotation(OneToOne.class) != null)) {
					allFields.add(field);
					fieldSet.add(fieldName);
				}
			}
			// 支持多级继承关系
			classType = classType.getSuperclass();
		}
		return allFields.toArray(new Field[allFields.size()]);
	}

	/**
	 * @todo 解析对象属性跟数据库表字段的信息
	 * @param sqlToyContext
	 * @param entityMeta
	 * @param field
	 * @param rejectIdFieldList
	 * @param allFieldAry
	 * @param loadNamedWhereSql
	 * @param loadArgWhereSql
	 */
	private void parseFieldMeta(SqlToyContext sqlToyContext, EntityMeta entityMeta, Field field,
			List<String> rejectIdFieldList, List<String> allFieldAry, StringBuilder loadNamedWhereSql,
			StringBuilder loadArgWhereSql) {
		Column column = field.getAnnotation(Column.class);
		if (column == null) {
			return;
		}
		// 字段的详细配置信息,字段名称，字段对应数据库表字段，字段默认值，字段类型
		FieldMeta fieldMeta = new FieldMeta(field.getName(), column.name(),
				(SqlToyConstants.DEFAULT_NULL.equals(column.defaultValue())) ? null : column.defaultValue(),
				column.type(), column.nullable(), column.keyword(), Long.valueOf(column.length()).intValue(),
				column.precision(), column.scale());
		// 增加字段
		allFieldAry.add(column.name());
		// 字段是否自增
		fieldMeta.setAutoIncrement(column.autoIncrement());
		// 设置type类型，并转小写便于后续对比的统一
		fieldMeta.setFieldType(field.getType().getTypeName().toLowerCase());
		// 设置是否分区字段
		if (field.getAnnotation(PartitionKey.class) != null) {
			fieldMeta.setPartitionKey(true);
		}
		// 内部包含了构造表字段名称跟vo属性名称的对照
		entityMeta.addFieldMeta(fieldMeta);
		// 判断字段是否为主键
		Id id = field.getAnnotation(Id.class);
		String idColName;
		if (id != null) {
			fieldMeta.setPK(true);
			// 主键生成策略
			entityMeta.setIdStrategy(PKStrategy.getPKStrategy(id.strategy().toLowerCase()));
			entityMeta.setSequence(id.sequence());
			String idGenerator = id.generator();
			if (StringUtil.isNotBlank(idGenerator)) {
				processIdGenerator(sqlToyContext, entityMeta, idGenerator);
				entityMeta.setIdGenerator(idGenerators.get(idGenerator));
			}
			if (loadNamedWhereSql.length() > 1) {
				loadNamedWhereSql.append(" and ");
				loadArgWhereSql.append(" and ");
			} else {
				loadNamedWhereSql.append(" where ");
				loadArgWhereSql.append(" where ");
			}
			idColName = ReservedWordsUtil.convertWord(column.name(), null);
			loadNamedWhereSql.append(idColName).append("=:").append(field.getName());
			loadArgWhereSql.append(idColName).append("=?");
		} else {
			rejectIdFieldList.add(field.getName());
		}
		// 业务主键策略配置解析
		BusinessId bizId = field.getAnnotation(BusinessId.class);
		if (bizId != null && StringUtil.isNotBlank(bizId.generator())) {
			String bizGenerator = bizId.generator();
			entityMeta.setBizIdLength(bizId.length());
			entityMeta.setBizIdSignature(bizId.signature());
			entityMeta.setHasBizIdConfig(true);
			entityMeta.setBizIdSequenceSize(bizId.sequenceSize());
			entityMeta.setBusinessIdField(field.getName());
			// 生成业务主键关联的字段(主键值生成需要其他字段的值进行组合,入交易业务ID组合交易类别码等)
			if (bizId.relatedColumns() != null && bizId.relatedColumns().length > 0) {
				entityMeta.setBizIdRelatedColumns(bizId.relatedColumns());
			}
			processIdGenerator(sqlToyContext, entityMeta, bizGenerator);
			// 如果是业务主键跟ID重叠,则ID以业务主键策略生成
			if (id != null) {
				entityMeta.setIdGenerator(idGenerators.get(bizGenerator));
				fieldMeta.setLength(bizId.length());
				entityMeta.setBizIdEqPK(true);
			} else {
				entityMeta.setBusinessIdGenerator(idGenerators.get(bizGenerator));
			}
		}
	}

	/**
	 * @todo 处理id生成器
	 * @param sqlToyContext
	 * @param entityMeta
	 * @param idGenerator
	 */
	private void processIdGenerator(SqlToyContext sqlToyContext, EntityMeta entityMeta, String idGenerator) {
		// 已经存在跳过处理
		if (idGenerators.containsKey(idGenerator)) {
			return;
		}
		// 自定义springbean 模式，用法在quickvo中配置@bean(beanName)
		if (idGenerator.toLowerCase().startsWith("@bean(")) {
			String beanName = idGenerator.substring(idGenerator.indexOf("(") + 1, idGenerator.indexOf(")"))
					.replaceAll("\"|\'", "").trim();
			idGenerators.put(idGenerator, (IdGenerator) sqlToyContext.getBean(beanName));
		} else {
			String generator = IdGenerators.get(idGenerator.toLowerCase());
			generator = (generator != null) ? IdGeneratorPackage.concat(generator) : idGenerator;
			// sqltoy默认提供的实现(兼容旧版本包命名,统一到新的packageName下面)
			if (generator.startsWith("org.sagacity.sqltoy")) {
				generator = IdGeneratorPackage.concat(generator.substring(generator.lastIndexOf(".") + 1));
			}
			try {
				// redis 情况特殊,依赖redisTemplate,小心修改
				if (generator.endsWith("RedisIdGenerator")) {
					RedisIdGenerator redis = (RedisIdGenerator) RedisIdGenerator.getInstance(sqlToyContext);
					if (redis == null || !redis.hasRedisTemplate()) {
						logger.error("POJO Class={} 的redisIdGenerator 未能被正确实例化,可能的原因是未定义RedisTemplate!",
								entityMeta.getEntityClass().getName());
					}
					idGenerators.put(idGenerator, redis);
				} else {
					// 自定义(不依赖spring模式),用法在quickvo中配置例如:com.xxxx..CustomIdGenerator
					IdGenerator idGeneratorInstance = (IdGenerator) Class.forName(generator).getDeclaredConstructor()
							.newInstance();
					idGeneratorInstance.initialize(sqlToyContext);
					idGenerators.put(idGenerator, idGeneratorInstance);
				}
			} catch (Exception e) {
				throw new RuntimeException("实例化主键生成策略失败:className=" + generator + ",错误信息:" + e.getMessage());
			}
		}
	}

	/**
	 * @todo 解析主键关联的子表信息配置(外键关联)
	 * @param sqlToyContext
	 * @param entityMeta
	 * @param entity
	 * @param field
	 * @param idList
	 */
	private void parseCascade(SqlToyContext sqlToyContext, EntityMeta entityMeta, Entity entity, Field field,
			List<String> idList) {
		// 主表关联多子表记录
		OneToMany oneToMany = field.getAnnotation(OneToMany.class);
		OneToOne oneToOne = field.getAnnotation(OneToOne.class);
		if (oneToMany == null && oneToOne == null) {
			return;
		}
		TableCascadeModel cascadeModel = new TableCascadeModel();
		String[] fields;
		String[] mappedFields;
		String load = null;
		String orderBy = null;
		String update = null;
		// oneToMany
		if (oneToMany != null) {
			fields = oneToMany.fields();
			mappedFields = oneToMany.mappedFields();
			cascadeModel.setCascadeType(1);
			cascadeModel
					.setMappedType((Class) ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0]);
			load = oneToMany.load();
			orderBy = oneToMany.orderBy();
			update = oneToMany.update();
			if (StringUtil.isNotBlank(oneToMany.notNullField())) {
				cascadeModel.setNotNullField(oneToMany.notNullField());
			}
			// 是否交由sqltoy进行级联删除,数据库本身存在自动级联机制
			cascadeModel.setDelete(oneToMany.delete());
		} else {
			fields = oneToOne.fields();
			mappedFields = oneToOne.mappedFields();
			cascadeModel.setCascadeType(2);
			cascadeModel.setMappedType(field.getType());
			load = oneToOne.load();
			update = oneToOne.update();
			cascadeModel.setDelete(oneToOne.delete());
			if (StringUtil.isNotBlank(oneToOne.notNullField())) {
				cascadeModel.setNotNullField(oneToOne.notNullField());
			}
		}
		// 获取子表的信息
		EntityMeta subTableMeta = parseEntityMeta(sqlToyContext, cascadeModel.getMappedType(), false, true);
		// 获取子表的信息(存在递归调用)
		// EntityMeta subTableMeta = getEntityMeta(sqlToyContext,
		// cascadeModel.getMappedType());
		if ((fields == null || fields.length == 0) && idList.size() == 1) {
			fields = entityMeta.getIdArray();
		}
		if (fields == null || fields.length != mappedFields.length) {
			throw new IllegalArgumentException(StringUtil.fillArgs("主表:{}的fields 跟子表:{} mappedFields 长度不一致,请检查!",
					entityMeta.getTableName(), subTableMeta.getTableName()));
		}
		String[] mappedColumns = new String[fields.length];
		// 剔除下划线,避免手工维护时将属性名称写成数据库字段名称
		fields = StringUtil.humpFieldNames(fields);
		mappedFields = StringUtil.humpFieldNames(mappedFields);
		// 主表字段名称
		for (int i = 0; i < fields.length; i++) {
			// 检查属性名称配置是否正确
			if (entityMeta.getFieldMeta(fields[i]) == null) {
				throw new IllegalArgumentException(
						StringUtil.fillArgs("表级联配置对应主表:{}的field属性:{} 并不存在,请检查!", entityMeta.getTableName(), fields[i]));
			}
			if (subTableMeta.getFieldMeta(mappedFields[i]) == null) {
				throw new IllegalArgumentException(StringUtil.fillArgs("表级联配置对应子表:{}的field属性:{} 并不存在,请检查!",
						subTableMeta.getTableName(), mappedFields[i]));
			}
			// 提取子表属性对应的数据库字段名称，并进行关键词处理
			mappedColumns[i] = ReservedWordsUtil.convertWord(subTableMeta.getColumnName(mappedFields[i]), null);
		}
		cascadeModel.setFields(fields);
		cascadeModel.setMappedColumns(mappedColumns);
		cascadeModel.setMappedFields(mappedFields);
		// 子表的schema.table
		String subSchemaTable = subTableMeta.getSchemaTable(null, null);
		cascadeModel.setMappedTable(subSchemaTable);
		cascadeModel.setProperty(field.getName());
		// 子表外键查询条件
		String subWhereSql = " where ";
		// 级联删除，自动组装sql不允许外部修改，所以用?作为条件，顺序在对象加载时约定
		String subDeleteSql = "delete from ".concat(subSchemaTable).concat(" where ");
		for (int i = 0; i < fields.length; i++) {
			if (i > 0) {
				subWhereSql = subWhereSql.concat(" and ");
				subDeleteSql = subDeleteSql.concat(" and ");
			}
			subWhereSql = subWhereSql.concat(mappedColumns[i]).concat("=:").concat(mappedFields[i]);
			subDeleteSql = subDeleteSql.concat(mappedColumns[i]).concat("=?");
		}
		cascadeModel.setLoadSubTableSql(subTableMeta.getLoadAllSql().concat(subWhereSql));
		cascadeModel.setDeleteSubTableSql(subDeleteSql);
		boolean matchedWhere = false;
		// 自定义load sql
		if (StringUtil.isNotBlank(load)) {
			String loadLow = load.toLowerCase();
			// 是否是:xxx形式的引入主键条件(原则上不允许这么操作)
			boolean isNamedSql = SqlConfigParseUtils.isNamedQuery(load);
			if (isNamedSql && !StringUtil.matches(loadLow, "(\\>|\\<)|(\\=)|(\\<\\>)|(\\>\\=|\\<\\=)")) {
				// 自定义加载完整sql
				if (!loadLow.equals("default") && !loadLow.equals("true")) {
					cascadeModel.setLoadSubTableSql(load);
				}
			} else {
				String loadSql = SqlUtil.convertFieldsToColumns(subTableMeta, load);
				matchedWhere = StringUtil.matches(loadLow, "\\s+where\\s+");
				if (matchedWhere) {
					cascadeModel.setLoadSubTableSql(loadSql);
				} else {
					cascadeModel.setLoadSubTableSql(
							subTableMeta.getLoadAllSql().concat(subWhereSql).concat(" and ").concat(loadSql));
					cascadeModel.setLoadExtCondition(loadSql);
				}
			}
		}

		// update 2020-11-20 增加子表级联order by
		if (StringUtil.isNotBlank(orderBy)) {
			// 对属性名称进行替换，替换为实际表字段名称
			orderBy = SqlUtil.convertFieldsToColumns(subTableMeta, orderBy);
			cascadeModel.setOrderBy(orderBy);
			cascadeModel.setLoadSubTableSql(cascadeModel.getLoadSubTableSql().concat(" order by ").concat(orderBy));
		}

		// 深度级联修改
		if (StringUtil.isNotBlank(update)) {
			String updateLow = update;
			// 表示先删除子表
			if (updateLow.equals("delete")) {
				cascadeModel.setCascadeUpdateSql("delete from ".concat(subSchemaTable).concat(subWhereSql));
			} else {
				// 修改数据(如设置记录状态为失效)
				matchedWhere = StringUtil.matches(updateLow, "\\s+where\\s+");
				cascadeModel.setCascadeUpdateSql("update ".concat(subSchemaTable).concat(" set ").concat(update)
						.concat(matchedWhere ? "" : subWhereSql));
			}
		}
		// 是否完成了覆盖
		boolean isRepeat = entityMeta.addCascade(cascadeModel);
		if (isRepeat) {
			logger.warn("表:{} 级联操作子表:{} 出现重复关联,后续:{}关联类型覆盖前面的关联", entityMeta.getTableName(),
					subTableMeta.getTableName(), (cascadeModel.getCascadeType() == 1) ? "oneToMany" : "oneToOne");
		}
	}

	/**
	 * @todo 设置字段类型和默认值
	 * @param entityMeta
	 */
	private void parseFieldTypeAndDefault(EntityMeta entityMeta) {
		// 组织对象对应表字段的类型和默认值以及是否可以为null
		int fieldSize = entityMeta.getFieldsArray().length;
		int pkSize = (entityMeta.getIdArray() == null) ? 0 : entityMeta.getIdArray().length;
		Integer[] fieldsTypeArray = new Integer[fieldSize];
		// 提供对象save\saveAll 构建默认值(主键无需设置)
		String[] fieldsDefaultValue = new String[fieldSize];
		Boolean[] fieldsNullable = new Boolean[fieldSize];
		FieldMeta fieldMeta;
		boolean hasDefault = false;
		for (int i = 0; i < fieldSize; i++) {
			fieldMeta = entityMeta.getFieldMeta(entityMeta.getFieldsArray()[i]);
			fieldsTypeArray[i] = fieldMeta.getType();
			// 非主键或复合主键字段支持默认值
			if (!fieldMeta.isPK() || pkSize > 1) {
				fieldsDefaultValue[i] = fieldMeta.getDefaultValue();
				if (null != fieldMeta.getDefaultValue()) {
					hasDefault = true;
				}
			}
			fieldsNullable[i] = fieldMeta.isNullable();
		}
		entityMeta.setFieldsTypeArray(fieldsTypeArray);
		if (hasDefault) {
			entityMeta.setFieldsDefaultValue(fieldsDefaultValue);
		}
		entityMeta.setFieldsNullable(fieldsNullable);
	}

	/**
	 * @return the packagesToScan
	 */
	public String[] getPackagesToScan() {
		return packagesToScan;
	}

	/**
	 * @param packagesToScan the packagesToScan to set
	 */
	public void setPackagesToScan(String[] packagesToScan) {
		this.packagesToScan = packagesToScan;
	}

	/**
	 * @return the annotatedClasses
	 */
	public String[] getAnnotatedClasses() {
		return annotatedClasses;
	}

	/**
	 * @param annotatedClasses the annotatedClasses to set
	 */
	public void setAnnotatedClasses(String[] annotatedClasses) {
		this.annotatedClasses = annotatedClasses;
	}

	/**
	 * @param recursive the recursive to set
	 */
	public void setRecursive(boolean recursive) {
		this.recursive = recursive;
	}

	public EntityMeta getEntityMeta(String tableName) {
		String className = tableEntityNameMap.get(tableName.toLowerCase());
		if (className == null) {
			return null;
		}
		return entitysMetaMap.get(className);
	}
}
