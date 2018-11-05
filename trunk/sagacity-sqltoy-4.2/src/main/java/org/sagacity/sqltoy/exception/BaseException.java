/**
 * 
 */
package org.sagacity.sqltoy.exception;

import java.io.PrintStream;
import java.io.PrintWriter;

import org.sagacity.sqltoy.utils.StringUtil;

/**
 * @project sagacity-sqltoy4.0
 * @description 定义框架的基础异常，以后所有异常都必须继承该异常
 * @author zhongxuchen <a href="mailto:zhongxuchen@hotmail.com">联系作者</a>
 * @version id:BaseException.java,Revision:v1.0,Date:Oct 19, 2007 10:07:08 AM
 */
public class BaseException extends RuntimeException {
	/**
	 * 
	 */
	private static final long serialVersionUID = 8726677738478797803L;

	public BaseException() {
		super();
	}

	public BaseException(Throwable cause) {
		super(cause);
	}

	public BaseException(String message) {
		super(message);
	}

	public BaseException(String message, Exception e) {
		super(message, e);
	}

	public BaseException(String message, Object... errorArgs) {
		super(StringUtil.fillArgs(message, errorArgs));
	}

	public BaseException(Exception e, String message, Object... errorArgs) {
		super(StringUtil.fillArgs(message, errorArgs), e);
	}

	public void printStackTrace() {
		printStackTrace(System.err);
	}

	public void printStackTrace(PrintStream outStream) {
		printStackTrace(new PrintWriter(outStream));
	}

	public void printStackTrace(PrintWriter writer) {
		super.printStackTrace(writer);
		if (getCause() != null) {
			getCause().printStackTrace(writer);
		}
		writer.flush();
	}

	public Throwable getRootCause() {
		Throwable rootCause = null;
		Throwable cause = getCause();
		while (cause != null && cause != rootCause) {
			rootCause = cause;
			cause = cause.getCause();
		}
		return rootCause;
	}

}
