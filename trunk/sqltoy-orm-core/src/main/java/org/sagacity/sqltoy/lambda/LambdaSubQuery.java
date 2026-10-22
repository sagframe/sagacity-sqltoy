package org.sagacity.sqltoy.lambda;

import java.util.function.Function;

import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.exception.DataAccessException;
import org.sagacity.sqltoy.lambda.engine.AbstractWhere;
import org.sagacity.sqltoy.lambda.join.JoinContext;
import org.sagacity.sqltoy.lambda.join.JoinContext.Operate;
import org.sagacity.sqltoy.lambda.toolkit.FieldNature;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * @description exists/notExists子查询条件构建器:单表子查询,列以sq前缀别名限定(避免与外层别名碰撞),
 *              参数序号与外层共享保证全局唯一;子查询内通过{@link OuterRef#of(LambdaFn)}引用外层查询的列,
 *              装配期按外层通道渲染为<code>表名.列</code>(单表)或<code>别名.列</code>(join)
 * @param <E> 子查询实体类型
 * @author zhongxuchen
 */
public final class LambdaSubQuery<E> extends AbstractWhere<E, LambdaSubQuery<E>> {

	/**
	 * 子查询表别名
	 */
	private final String alias;

	/**
	 * 外层查询的限定列解析器,用于OuterRef渲染
	 */
	private final ColumnResolver outerResolver;

	/**
	 * @description 由AbstractWhere.exists/notExists创建,不直接对外使用
	 */
	public LambdaSubQuery(Class<E> subEntity, String alias, JoinContext outerContext, ColumnResolver outerResolver) {
		super(subEntity,
				new JoinContext(Operate.SELECT, null, null, outerContext.getParamSeq(), outerContext.getSubQuerySeq()));
		this.alias = alias;
		this.outerResolver = outerResolver;
		joinContext.registerFrom(subEntity, alias);
		// 子查询FROM始终带别名,强制走SQL通道,使嵌套exists的OuterRef按别名限定
		joinContext.forceJoinChannel();
	}

	@Override
	protected LambdaSubQuery<E> instance() {
		// 嵌套条件共享子查询上下文
		LambdaSubQuery<E> nested = new LambdaSubQuery<>(entityClass, alias, joinContext, outerResolver);
		return nested;
	}

	@Override
	public void assemble(Function<Class<?>, EntityMeta> metaLoader) {
		// 子查询上下文注入元信息加载器(existsSql的getFromSql依赖)
		joinContext.assemble(metaLoader);
		super.assemble(metaLoader);
	}

	@Override
	protected ColumnResolver resolverFor(Function<Class<?>, EntityMeta> metaLoader) {
		return (entity, fieldName) -> {
			if (!entityClass.equals(entity)) {
				throw new DataAccessException("Entity [" + entity.getSimpleName()
						+ "] does not belong to the exists sub-query entity [" + entityClass.getSimpleName() + "]");
			}
			String columnName = metaLoader.apply(entity).getColumnName(fieldName);
			if (columnName == null) {
				throw new DataAccessException("Property [" + fieldName + "] of entity [" + entityClass.getSimpleName()
						+ "] is not a mapped database column (@Column); lambda conditions only accept mapped properties");
			}
			return alias + "." + columnName;
		};
	}

	@Override
	protected String outerRefSql(OuterRef<?> ref) {
		FieldNature nature = LambdaUtils.extractNature(ref.getColumn());
		return outerResolver.columnName(nature.entityClass(), nature.fieldName());
	}

	/**
	 * @return EXISTS子查询sql:SELECT 1 FROM 表 别名 [WHERE 条件]
	 */
	public String existsSql() {
		String whereSql = getSqlSegment();
		return "SELECT 1 FROM " + joinContext.getFromSql()
				+ (StringUtil.isNotBlank(whereSql) ? " WHERE " + whereSql : "");
	}
}
