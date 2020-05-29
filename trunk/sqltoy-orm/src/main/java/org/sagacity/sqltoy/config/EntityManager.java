/**
 * @Copyright 2009 版权归陈仁飞，不要肆意侵权抄袭，如引用请注明出处保留作者信息。
 */
package org.sagacity.sqltoy.config;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.annotation.BusinessId;
import org.sagacity.sqltoy.config.annotation.Column;
import org.sagacity.sqltoy.config.annotation.Entity;
import org.sagacity.sqltoy.config.annotation.Id;
import org.sagacity.sqltoy.config.annotation.ListSql;
import org.sagacity.sqltoy.config.annotation.LoadSql;
import org.sagacity.sqltoy.config.annotation.OneToMany;
import org.sagacity.sqltoy.config.annotation.PaginationSql;
import org.sagacity.sqltoy.config.annotation.Sharding;
import org.sagacity.sqltoy.config.annotation.Strategy;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.FieldMeta;
import org.sagacity.sqltoy.config.model.OneToManyModel;
import org.sagacity.sqltoy.config.model.PKStrategy;
import org.sagacity.sqltoy.config.model.ShardingConfig;
import org.sagacity.sqltoy.config.model.ShardingStrategyConfig;
import org.sagacity.sqltoy.plugins.id.IdGenerator;
import org.sagacity.sqltoy.plugins.id.impl.RedisIdGenerator;
import org.sagacity.sqltoy.utils.ReservedWordsUtil;
import org.sagacity.sqltoy.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sagacity-sqltoy
 * @description 通过注解解析实体对象,得到其跟数据库表的对应关系,并形成相应表增删改查的语句
 * @author renfei.chen <a href="mailto:zhongxuchen@hotmail.com">联系作者</a>
 * @version id:EntityManager.java,Revision:v1.0,Date:2012-6-1 下午4:23:26
 * @modify {Date:2017-10-13,分解之前的parseEntityMeta大方法,进行代码优化}
 * @modify {Date:2018-1-22,增加业务主键配置策略}
 * @modify {Date:2018-9-6,优化增强业务主键配置策略}
 * @modify {Date:2019-8-10,优化字段的解析,避免在子类中定义属性覆盖了父类导致数据库字段失效现象,同时优化部分代码}
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
			put("default", "DefaultIdGenerator");
			put("uuid", "UUIDGenerator");
			put("redis", "RedisIdGenerator");
			put("nanotime", "NanoTimeIdGenerator");
			put("snowflake", "SnowflakeIdGenerator");
			put("defaultidgenerator", "DefaultIdGenerator");
			put("defaultgenerator", "DefaultIdGenerator");
			put("nanotimeidgenerator", "NanoTimeIdGenerator");
			put("snowflakeidgenerator", "SnowflakeIdGenerator");
			put("uuidgenerator", "UUIDGenerator");
			put("redisidgenerator", "RedisIdGenerator");
		}
	};

	/**
	 * id产生器的包路径
	 */
	private static final String IdGeneratorPackage = "org.sagacity.sqltoy.plugins.id.impl.";

	/**
	 * 扫描的包
	 */
	private String[] packagesToScan;

	/**
	 * 是否循环迭代下级包目录
	 */
	private boolean recursive = true;

	/**
	 * 指定的entity class
	 */
	private String[] annotatedClasses;

	/**
	 * 存放每个对象跟数据库表的关系信息
	 */
	private ConcurrentHashMap<String, EntityMeta> entitysMetaMap = new ConcurrentHashMap<String, EntityMeta>();

	/**
	 * 非sqltoy entity类,一般指仅用于查询作为返回结果的VO
	 */
	private ConcurrentHashMap<String, String> unEntityMap = new ConcurrentHashMap<String, String>();

	/**
	 * @TODO 判断是否是实体对象
	 * @param sqlToyContext
	 * @param entityClass
	 * @return
	 */
	public boolean isEntity(SqlToyContext sqlToyContext, Class entityClass) {
		String className = entityClass.getName();
		if (unEntityMap.contains(className))
			return false;
		if (entitysMetaMap.contains(className)) {
			return true;
		}

		EntityMeta entityMeta = parseEntityMeta(sqlToyContext, entityClass);
		if (entityMeta != null)
			return true;
		unEntityMap.put(className, "1");
		return false;
	}

	/**
	 * @todo <b>获取Entity类的对应数据库表信息，如：查询、修改、插入sql、对象属性跟表字段之间的关系等信息</b>
	 * @param sqlToyContext
	 * @param entityClass
	 * @return
	 */
	public EntityMeta getEntityMeta(SqlToyContext sqlToyContext, Class entityClass) {
		if (entityClass == null)
			return null;
		String className = entityClass.getName();
		EntityMeta entityMeta = entitysMetaMap.get(className);
		// update 2017-11-27
		// 增加在使用对象时动态解析的功能,让sqltoy可以不用配置packagesToScan和annotatedClasses
		if (entityMeta == null) {
			entityMeta = parseEntityMeta(sqlToyContext, entityClass);
			if (entityMeta == null) {
				throw new IllegalArgumentException("您传入的对象:[".concat(className)
						.concat(" ]不是一个@SqlToyEntity实体POJO对象,sqltoy实体对象必须使用 @SqlToyEntity/@Entity/@Id 等注解来标识!"));
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
		if (packagesToScan != null && packagesToScan.length > 0)
			for (String pkg : this.packagesToScan) {
				entities.addAll(ScanEntityAndSqlResource.getPackageEntities(pkg, recursive, "UTF-8"));
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
			parseEntityMeta(sqlToyContext, entityClass);
		}
	}

	/**
	 * @todo <b>解析sqltoy entity对象获取其跟数据库相关的配置信息</b>
	 * @param sqlToyContext
	 * @param entityClass
	 * @return
	 */
	public synchronized EntityMeta parseEntityMeta(SqlToyContext sqlToyContext, Class entityClass) {
		if (entityClass == null)
			return null;
		String className = entityClass.getName();
		// 避免重复解析
		if (entitysMetaMap.containsKey(className)) {
			return entitysMetaMap.get(className);
		}
		EntityMeta entityMeta = null;
		try {
			Class realEntityClass = entityClass;
			boolean hasAbstractVO = false;
			boolean isEntity = true;
			// 通过逐层递归来判断是否SqlToy annotation注解所规定的关联数据库的实体类
			// 即@Entity 注解的抽象类
			while (!realEntityClass.isAnnotationPresent(Entity.class)) {
				if (realEntityClass.getSuperclass() == null) {
					isEntity = false;
					break;
				} else {
					realEntityClass = realEntityClass.getSuperclass();
					hasAbstractVO = true;
				}
			}
			// 判断是否是实体类
			if (isEntity) {
				entityMeta = new EntityMeta();
				entityMeta.setEntityClass(realEntityClass);
				Entity entity = (Entity) realEntityClass.getAnnotation(Entity.class);
				// 表名
				entityMeta.setTableName(entity.tableName());

				// 解析自定义注解
				parseCustomAnnotation(entityMeta, entityClass);
				// 解析sharding策略
				parseSharding(entityMeta, entityClass);

				// 主键约束(for postgresql)
				if (StringUtil.isNotBlank(entity.pk_constraint())) {
					entityMeta.setPkConstraint(entity.pk_constraint());
				}
				entityMeta.setSchemaTable((StringUtil.isBlank(entity.schema()) ? "" : (entity.schema().concat(".")))
						.concat(entity.tableName()));

				// 解析Entity包含的字段信息
				Field[] allFields = parseFields(entityClass, realEntityClass, hasAbstractVO);

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
				for (Field field : allFields) {
					// 解析对象字段属性跟数据库表字段的对应关系
					parseFieldMeta(sqlToyContext, entityMeta, field, rejectIdFieldList, allColumnNames,
							loadNamedWhereSql, loadArgWhereSql);
					// oneToMany解析
					parseOneToMany(sqlToyContext, entityMeta, entity, field, idList);
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
						.concat(entityMeta.getSchemaTable()));

				entityMeta.setIdArgWhereSql(loadArgWhereSql.toString());
				entityMeta.setIdNameWhereSql(loadNamedWhereSql.toString());

				// 设置级联关联对象类型
				if (!entityMeta.getOneToManys().isEmpty()) {
					Class[] cascadeTypes = new Class[entityMeta.getOneToManys().size()];
					for (int i = 0; i < entityMeta.getOneToManys().size(); i++) {
						cascadeTypes[i] = entityMeta.getOneToManys().get(i).getMappedType();
					}
					entityMeta.setCascadeTypes(cascadeTypes);
				}

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
					// delete sql是内部产生，所以用?形式作为参数
					entityMeta.setDeleteByIdsSql(
							"delete from ".concat(entityMeta.getSchemaTable()).concat(loadArgWhereSql.toString()));
				}
				// 内部存在逻辑设置allFields
				entityMeta.setFieldsArray(fieldList.toArray(new String[rejectIdFieldList.size() + idList.size()]));

				// 设置字段类型和默认值
				parseFieldTypeAndDefault(entityMeta);
			}
		} catch (Exception e) {
			logger.error("Sqltoy 解析Entity对象:[{}]发生错误,请检查对象注解是否正确!", className);
			e.printStackTrace();
		}
		if (entityMeta != null) {
			entitysMetaMap.put(className, entityMeta);
		} else {
			logger.warn("SqlToy Entity:{}没有使用@Entity注解表明是一个实体类,请检查!", className);
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
		// 分库策略
		if (entityClass.isAnnotationPresent(Sharding.class)) {
			ShardingConfig shardingConfig = new ShardingConfig();
			Sharding sharding = (Sharding) entityClass.getAnnotation(Sharding.class);
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
				ShardingStrategyConfig config = new ShardingStrategyConfig();
				config.setFields(shardingDB.fields());
				// 别名,如果没有设置则将fields作为默认别名,别名的目的在于共用sharding策略中的参数名称
				String[] aliasNames = new String[shardingDB.fields().length];
				System.arraycopy(shardingDB.fields(), 0, aliasNames, 0, aliasNames.length);
				if (shardingDB.aliasNames() != null) {
					System.arraycopy(shardingDB.aliasNames(), 0, aliasNames, 0, shardingDB.aliasNames().length);
				}

				config.setAliasNames(aliasNames);
				config.setDecisionType(shardingDB.decisionType());
				config.setName(strategy);
				shardingConfig.setShardingDBStrategy(config);
			}
			// 分表策略
			Strategy shardingTable = sharding.table();
			strategy = shardingTable.name();
			if (StringUtil.isNotBlank(strategy)) {
				ShardingStrategyConfig config = new ShardingStrategyConfig();
				config.setFields(shardingTable.fields());
				// 别名,如果没有设置则将fields作为默认别名,别名的目的在于共用sharding策略中的参数名称
				String[] aliasNames = new String[shardingTable.fields().length];
				System.arraycopy(shardingTable.fields(), 0, aliasNames, 0, aliasNames.length);
				if (shardingTable.aliasNames() != null) {
					System.arraycopy(shardingTable.aliasNames(), 0, aliasNames, 0, shardingTable.aliasNames().length);
				}

				config.setAliasNames(aliasNames);
				config.setDecisionType(shardingDB.decisionType());
				config.setName(strategy);
				shardingConfig.setShardingTableStrategy(config);
			}
			// 必须有一个策略是存在的
			if (shardingConfig.getShardingDBStrategy() != null || shardingConfig.getShardingTableStrategy() != null) {
				entityMeta.setShardingConfig(shardingConfig);
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
	 * @todo 解析获取entity对象的属性
	 * @param entityClass
	 * @param realEntityClass
	 * @param hasAbstractVO
	 * @return
	 */
	private Field[] parseFields(Class entityClass, Class realEntityClass, boolean hasAbstractVO) {
		HashMap<String, String> fieldNameMap = new HashMap<String, String>();
		List<Field> allFields = new ArrayList<Field>();
		// 提取用户在vo上面自定义的属性,如子表级联保存等
		Field[] voCustFields = entityClass.getDeclaredFields();
		// 自定义VO属性优先处理
		for (Field field : voCustFields) {
			if ((field.getAnnotation(Column.class) != null || field.getAnnotation(OneToMany.class) != null)
					&& !fieldNameMap.containsKey(field.getName())) {
				allFields.add(field);
				fieldNameMap.put(field.getName(), "1");
			}
		}
		// 存在抽象类(标准的sqltoy entity模式)
		if (hasAbstractVO) {
			// abstractVO中的属性
			Field[] fields = realEntityClass.getDeclaredFields();
			for (Field field : fields) {
				if ((field.getAnnotation(Column.class) != null || field.getAnnotation(OneToMany.class) != null)
						&& !fieldNameMap.containsKey(field.getName())) {
					allFields.add(field);
					fieldNameMap.put(field.getName(), "1");
				}
			}
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
	 * @throws Exception
	 */
	private void parseFieldMeta(SqlToyContext sqlToyContext, EntityMeta entityMeta, Field field,
			List<String> rejectIdFieldList, List<String> allFieldAry, StringBuilder loadNamedWhereSql,
			StringBuilder loadArgWhereSql) throws Exception {
		Column column = field.getAnnotation(Column.class);
		if (column == null)
			return;
		// 字段的详细配置信息,字段名称，字段对应数据库表字段，字段默认值，字段类型
		FieldMeta fieldMeta = new FieldMeta(field.getName(), column.name(),
				StringUtil.isNotBlank(column.defaultValue()) ? column.defaultValue() : null, column.type(),
				column.nullable(), column.keyword(), Long.valueOf(column.length()).intValue(), column.precision(),
				column.scale());
		// 增加字段
		allFieldAry.add(column.name());
		// 字段是否自增
		fieldMeta.setAutoIncrement(column.autoIncrement());
		// 设置type类型，并转小写便于后续对比的统一
		fieldMeta.setFieldType(field.getType().getTypeName().toLowerCase());
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
	 * @throws Exception
	 */
	private void processIdGenerator(SqlToyContext sqlToyContext, EntityMeta entityMeta, String idGenerator)
			throws Exception {
		if (!idGenerators.containsKey(idGenerator)) {
			// 自定义springbean 模式
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
				// redis 情况特殊,依赖redisTemplate,小心修改
				if (generator.endsWith("RedisIdGenerator")) {
					RedisIdGenerator redis = (RedisIdGenerator) RedisIdGenerator.getInstance(sqlToyContext);
					if (redis == null || !redis.hasRedisTemplate()) {
						logger.error("POJO Class={} 的redisIdGenerator 未能被正确实例化,可能的原因是未定义RedisTemplate!",
								entityMeta.getEntityClass().getName());
					}
					idGenerators.put(idGenerator, redis);
				} else {
					idGenerators.put(idGenerator,
							(IdGenerator) Class.forName(generator).getDeclaredConstructor().newInstance());
				}
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
	private void parseOneToMany(SqlToyContext sqlToyContext, EntityMeta entityMeta, Entity entity, Field field,
			List<String> idList) {
		// 主表关联多子表记录
		OneToMany oneToMany = field.getAnnotation(OneToMany.class);
		if (oneToMany == null)
			return;
		// 主键字段数量
		int idSize = idList.size();
		if (idSize != oneToMany.mappedColumns().length) {
			logger.error("主表:{}的主键字段数量:{}与子表:{}的外键关联字段数量:{}不等,请检查!", entityMeta.getTableName(), idSize,
					oneToMany.mappedTable(), oneToMany.mappedColumns().length);
			return;
		}
		OneToManyModel oneToManyModel = new OneToManyModel();
		String[] mappedColumns = new String[idSize];
		String[] mappedFields = new String[idSize];
		// 按照主键顺序排列外键顺序
		// 原则上可以:oneToManyModel.setMappedColumns(oneToMany.mappedColumns())
		// 直接复制,考虑主键顺序可能会被人为调整
		String idFieldName;
		String var;
		for (int i = 0; i < idSize; i++) {
			var = oneToMany.mappedFields()[i];
			for (int j = 0; j < idSize; j++) {
				idFieldName = idList.get(j);
				if (var.equalsIgnoreCase(idFieldName)) {
					mappedFields[j] = var;
					mappedColumns[j] = oneToMany.mappedColumns()[i];
					break;
				}
			}
		}

		oneToManyModel.setMappedColumns(mappedColumns);
		oneToManyModel.setMappedFields(mappedFields);
		// 子表的schema.table
		String subSchemaTable = (StringUtil.isBlank(entity.schema()) ? "" : (entity.schema().concat(".")))
				.concat(oneToMany.mappedTable());
		oneToManyModel.setMappedTable(subSchemaTable);
		oneToManyModel.setProperty(field.getName());
		oneToManyModel.setMappedType((Class) ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0]);

		// 是否交由sqltoy进行级联删除,数据库本身存在自动级联机制
		oneToManyModel.setDelete(oneToMany.delete());

		// 子表外键查询条件
		String subWhereSql = " where ";
		for (int i = 0; i < idSize; i++) {
			if (i > 0) {
				subWhereSql = subWhereSql.concat(" and ");
			}
			subWhereSql = subWhereSql.concat(ReservedWordsUtil.convertWord(mappedColumns[i], null)).concat("=:")
					.concat(mappedFields[i]);
		}
		boolean matchedWhere = false;
		// 默认load为true，由程序员通过程序指定哪些子表是否需要加载
		oneToManyModel.setLoad(true);

		// 获取子表的信息(存在递归调用)
		EntityMeta subTableMeta = getEntityMeta(sqlToyContext, oneToManyModel.getMappedType());
		// update 2019-12-09 将select * 转变为select 完整字段
		oneToManyModel.setLoadSubTableSql("select ".concat(subTableMeta.getAllColumnNames()).concat(" from ")
				.concat(subSchemaTable).concat(subWhereSql));
		// 自动加载
		if (StringUtil.isNotBlank(oneToMany.load())) {
			// 是否是:xxx形式的参数条件
			boolean isNamedSql = SqlConfigParseUtils.isNamedQuery(oneToMany.load());
			if (isNamedSql && !StringUtil.matches(oneToMany.load(), "(\\>|\\<)|(\\=)|(\\<\\>)|(\\>\\=|\\<\\=)")) {
				// 自定义加载sql
				if (!oneToMany.load().equalsIgnoreCase("default") && !oneToMany.load().equalsIgnoreCase("true")) {
					oneToManyModel.setLoadSubTableSql(oneToMany.load());
				}
			} else {
				matchedWhere = StringUtil.matches(oneToMany.load().toLowerCase(), "\\s+where\\s+");
				if (matchedWhere) {
					oneToManyModel.setLoadSubTableSql(oneToMany.load());
				} else {
					oneToManyModel.setLoadSubTableSql(
							"select ".concat(subTableMeta.getAllColumnNames()).concat(" from ").concat(subSchemaTable)
									.concat(subWhereSql).concat(" and ").concat(oneToMany.load()));
				}
			}
		}

		// 级联删除，自动组装sql不允许外部修改，所以用?作为条件，顺序在对象加载时约定
		String subDeleteSql = "delete from ".concat(subSchemaTable).concat(" where ");
		for (int i = 0; i < idList.size(); i++) {
			if (i > 0) {
				subDeleteSql = subDeleteSql.concat(" and ");
			}
			subDeleteSql = subDeleteSql.concat(ReservedWordsUtil.convertWord(mappedColumns[i], null)).concat("=?");
		}
		oneToManyModel.setDeleteSubTableSql(subDeleteSql);

		// 深度级联修改
		if (StringUtil.isNotBlank(oneToMany.update())) {
			// 表示先删除子表
			if (oneToMany.update().equalsIgnoreCase("delete")) {
				oneToManyModel.setCascadeUpdateSql("delete from ".concat(subSchemaTable).concat(subWhereSql));
			} else {
				// 修改数据(如设置记录状态为失效)
				matchedWhere = StringUtil.matches(oneToMany.update().toLowerCase(), "\\s+where\\s+");
				oneToManyModel.setCascadeUpdateSql("update ".concat(subSchemaTable).concat(" set ")
						.concat(oneToMany.update()).concat(matchedWhere ? "" : subWhereSql));
			}
		}
		entityMeta.addOneToMany(oneToManyModel);
	}

	/**
	 * @todo 设置字段类型和默认值
	 * @param entityMeta
	 */
	private void parseFieldTypeAndDefault(EntityMeta entityMeta) {
		// 组织对象对应表字段的类型和默认值以及是否可以为null
		int fieldSize = entityMeta.getFieldsArray().length;
		Integer[] fieldsTypeArray = new Integer[fieldSize];
		String[] fieldsDefaultValue = new String[fieldSize];
		Boolean[] fieldsNullable = new Boolean[fieldSize];
		FieldMeta fieldMeta;
		boolean hasDefaultValue = false;
		for (int i = 0; i < fieldSize; i++) {
			fieldMeta = entityMeta.getFieldMeta(entityMeta.getFieldsArray()[i]);
			fieldsTypeArray[i] = fieldMeta.getType();
			if (fieldMeta.getDefaultValue() != null) {
				hasDefaultValue = true;
			}
			fieldsDefaultValue[i] = fieldMeta.getDefaultValue();
			fieldsNullable[i] = fieldMeta.isNullable();
		}
		entityMeta.setHasDefaultValue(hasDefaultValue);
		entityMeta.setFieldsTypeArray(fieldsTypeArray);
		entityMeta.setFieldsDefaultValue(fieldsDefaultValue);
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

}
