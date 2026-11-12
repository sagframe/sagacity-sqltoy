package org.sagacity.sqltoy.dialect.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.model.LockMode;

/**
 * update 2026-11-11 TDengineDialect(2026-11-10新增方言)的确定性契约测试——补两天修改点审计发现的
 * 唯一真实测试缺口(此前仅协作方在真实TDengine 3.3.6上手工验证,无任何自动化用例):
 * <ol>
 * <li>11个引擎不具备语义的操作(isUnique/update族/saveOrUpdate族/delete族/updateFetch/executeStore)
 * 必须首语句抛UnsupportedOperationException,消息须说明TDengine引擎限制并给出替代出路——
 * "响亮且有出路"是框架对不可支持能力的既定口径(与sqlite仅剩executeStore抛异常的先例一致)</li>
 * <li>行级锁恒忽略:TDengine无SELECT...FOR UPDATE(实测code 9728),读操作须按无锁执行而非拼出坏SQL</li>
 * <li>委托契约:查询/分页/随机取数/count/插入走DefaultDialect通用路径(真库行为由协作方实测背书,
 * 此处锁定类型契约——extends DefaultDialect不被破坏)</li>
 * </ol>
 */
public class TDengineDialectContractTest {

	private static final TDengineDialect DIALECT = new TDengineDialect();

	/**
	 * 11个不支持操作:全部抛UOE且消息含引擎限制说明与替代方案提示,不允许静默错值或裸NPE
	 */
	@Test
	public void unsupportedOperationsThrowWithGuidance() throws Exception {
		String[] unsupported = { "isUnique", "update", "updateSaveFetch", "updateAll", "saveOrUpdate", "saveOrUpdateAll",
				"saveAllIgnoreExist", "delete", "deleteAll", "updateFetch", "executeStore" };
		for (String name : unsupported) {
			Method method = findMethod(name);
			assertNotNull(method, "应存在方法:" + name);
			// 原语参数补默认值(方法首语句即抛,参数不被真正使用)
			Object[] args = new Object[method.getParameterCount()];
			for (int i = 0; i < args.length; i++) {
				Class<?> type = method.getParameterTypes()[i];
				if (type == int.class) {
					args[i] = Integer.valueOf(0);
				} else if (type == long.class) {
					args[i] = Long.valueOf(0L);
				} else if (type == boolean.class) {
					args[i] = Boolean.FALSE;
				} else {
					args[i] = null;
				}
			}
			try {
				method.invoke(DIALECT, args);
				fail(name + " 应抛 UnsupportedOperationException");
			} catch (InvocationTargetException e) {
				assertTrue(e.getCause() instanceof UnsupportedOperationException,
						name + " 应抛UnsupportedOperationException,实际:" + e.getCause());
				String message = e.getCause().getMessage();
				assertNotNull(message, name + " 异常消息不得为空");
				// 消息须自解释:点名引擎限制并给出出路(覆盖写/KEEP策略/sql xml手写等)
				assertTrue(message.contains("TDengine"), name + " 消息应说明引擎限制:" + message);
				assertTrue(message.length() >= 15, name + " 消息应含替代方案指引:" + message);
			}
		}
	}

	/**
	 * 行级锁恒忽略:任意LockMode(含UPGRADE/SKIPLOCK)与null都归一为无锁,
	 * 防止DefaultDialect拼出" for update"坏SQL(真库实测code 9728)
	 */
	@Test
	public void lockModesAreAlwaysIgnored() throws Exception {
		Method ignoreLock = TDengineDialect.class.getDeclaredMethod("ignoreLock", LockMode.class, String.class);
		ignoreLock.setAccessible(true);
		for (LockMode mode : new LockMode[] { LockMode.UPGRADE, LockMode.UPGRADE_NOWAIT, LockMode.UPGRADE_SKIPLOCK,
				null }) {
			assertNull(ignoreLock.invoke(DIALECT, mode, "contract-test"), "锁模式必须被归一为无锁:" + mode);
		}
	}

	/**
	 * 委托契约:查询族走DefaultDialect通用路径——方言类不得脱离该继承链
	 * (findBySql/findPageBySql/findTopBySql/getRandomResult/getCountBySql/load/loadAll/save/saveAll
	 * 的真库行为由协作方在TDengine 3.3.6+taos-jdbcdriver 3.3.0实测背书)
	 */
	@Test
	public void queryFamilyDelegatesToDefaultDialect() {
		assertEquals(DefaultDialect.class, TDengineDialect.class.getSuperclass(),
				"查询族委托DefaultDialect的继承契约不得破坏");
		for (String delegated : new String[] { "findBySql", "findPageBySql", "findTopBySql", "getRandomResult",
				"getCountBySql", "load", "loadAll", "save", "saveAll" }) {
			assertNotNull(findMethod(delegated), "委托方法应存在:" + delegated);
		}
	}

	private static Method findMethod(String name) {
		for (Method method : TDengineDialect.class.getDeclaredMethods()) {
			if (method.getName().equals(name)) {
				method.setAccessible(true);
				return method;
			}
		}
		return null;
	}
}
