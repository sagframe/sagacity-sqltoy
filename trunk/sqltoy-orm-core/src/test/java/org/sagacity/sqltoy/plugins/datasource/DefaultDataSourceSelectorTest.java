package org.sagacity.sqltoy.plugins.datasource;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.integration.AppContext;
import org.sagacity.sqltoy.plugins.datasource.impl.DefaultDataSourceSelector;

/**
 * 默认数据源选择器的优先级与"多数据源无@Primary"回退语义:
 * @Primary查找失败(catch块)必须仍然是吞掉异常并返回null,以便上层要求显式指定数据源;
 * 该分支补debug日志后行为不得改变(日志本身在无SLF4J provider的测试环境不产生输出,故只断言语义)
 */
public class DefaultDataSourceSelectorTest {

	private static DataSource mockDataSource() {
		return (DataSource) Proxy.newProxyInstance(DefaultDataSourceSelectorTest.class.getClassLoader(),
				new Class[] { DataSource.class }, (proxy, method, args) -> null);
	}

	/**
	 * 模拟容器:支持指定getBeansOfType的结果,以及getBean(Class)是否抛异常(多数据源无@Primary的场景)
	 */
	private static AppContext mockContext(Map<String, DataSource> beans, DataSource primary, boolean primaryThrows) {
		return new AppContext() {
			@Override
			public boolean containsBean(String beanName) {
				return beans.containsKey(beanName);
			}

			@Override
			public Object getBean(String beanName) {
				return beans.get(beanName);
			}

			@Override
			@SuppressWarnings("unchecked")
			public <T> T getBean(Class<T> requiredType) {
				if (primaryThrows) {
					throw new IllegalStateException("NoUniqueBeanDefinitionException: expected single matching bean");
				}
				return (T) primary;
			}

			@Override
			@SuppressWarnings("unchecked")
			public <T> Map<String, T> getBeansOfType(Class<T> type) {
				return (Map<String, T>) beans;
			}
		};
	}

	private static Map<String, DataSource> manyDataSources() {
		Map<String, DataSource> beans = new LinkedHashMap<String, DataSource>();
		beans.put("firstDataSource", mockDataSource());
		beans.put("secondDataSource", mockDataSource());
		return beans;
	}

	@Test
	public void singleDataSourceIsUsedDirectly() {
		DataSource only = mockDataSource();
		Map<String, DataSource> beans = new LinkedHashMap<String, DataSource>();
		beans.put("onlyDataSource", only);
		DataSource result = new DefaultDataSourceSelector().getDataSource(mockContext(beans, null, false), null, null,
				null, null);
		assertSame(only, result);
	}

	@Test
	public void primaryDataSourceIsUsedWhenMultipleExist() {
		DataSource primary = mockDataSource();
		DataSource result = new DefaultDataSourceSelector().getDataSource(mockContext(manyDataSources(), primary, false),
				null, null, null, null);
		assertSame(primary, result);
	}

	/**
	 * 多数据源且无@Primary:异常被吞掉、返回null(不抛出),由上层依据null给出"需指定数据源"的提示
	 */
	@Test
	public void multipleWithoutPrimarySwallowsExceptionAndReturnsNull() {
		AppContext context = mockContext(manyDataSources(), null, true);
		assertNull(new DefaultDataSourceSelector().getDataSource(context, null, null, null, null));
	}

	@Test
	public void explicitSourcesTakePriorityOverContainerLookup() {
		DefaultDataSourceSelector selector = new DefaultDataSourceSelector();
		AppContext context = mockContext(manyDataSources(), null, true);
		DataSource point = mockDataSource();
		DataSource inject = mockDataSource();
		DataSource defaultDs = mockDataSource();
		Map<String, DataSource> named = new LinkedHashMap<String, DataSource>();
		DataSource bySql = mockDataSource();
		named.put("bySqlDataSource", bySql);
		AppContext namedContext = mockContext(named, null, true);

		// 1:方法直接传递
		assertSame(point, selector.getDataSource(context, point, "bySqlDataSource", inject, defaultDs));
		// 2:sql上指定的数据源名称
		assertSame(bySql, selector.getDataSource(namedContext, null, "bySqlDataSource", inject, defaultDs));
		// 3:dao注入的数据源
		assertSame(inject, selector.getDataSource(context, null, null, inject, defaultDs));
		// 4:sqltoy统一设置的默认数据源
		assertSame(defaultDs, selector.getDataSource(context, null, null, null, defaultDs));
		// 未指定名称且容器无该bean时继续向下回退
		assertSame(inject, selector.getDataSource(context, null, "notExistDataSource", inject, defaultDs));
	}

	@Test
	public void nullContextReturnsExplicitSourceOrNull() {
		DefaultDataSourceSelector selector = new DefaultDataSourceSelector();
		DataSource inject = mockDataSource();
		assertSame(inject, selector.getDataSource(null, null, null, inject, null));
		assertNull(selector.getDataSource(null, null, null, null, null));
	}
}
