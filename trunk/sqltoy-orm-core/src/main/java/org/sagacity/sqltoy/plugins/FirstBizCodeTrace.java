package org.sagacity.sqltoy.plugins;

/**
 * 提供获取业务代码的位置
 */
public interface FirstBizCodeTrace {
	public StackTraceElement getFirstTrace(StackTraceElement[] stackTraceElements);
}
