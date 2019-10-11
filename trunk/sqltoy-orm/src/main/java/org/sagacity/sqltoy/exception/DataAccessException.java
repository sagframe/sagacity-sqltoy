/**
 * 
 */
package org.sagacity.sqltoy.exception;

import java.io.PrintStream;
import java.io.PrintWriter;

import org.sagacity.sqltoy.utils.StringUtil;

/**
 * @project sagacity-sqltoy
 * @description 数据存取操作异常
 * @author zhongxuchen
 * @version v1.0, Date:2019年7月3日
 * @modify 2019年7月3日,修改说明
 */
public class DataAccessException extends RuntimeException {

	/**
	 * 
	 */
	private static final long serialVersionUID = 6200356390963104605L;

	public DataAccessException() {
		super();
	}

	public DataAccessException(Throwable cause) {
		super(cause);
	}

	public DataAccessException(String message) {
		super(message);
	}

	public DataAccessException(String message, Exception e) {
		super(message, e);
	}

	public DataAccessException(String message, Object... errorArgs) {
		super(StringUtil.fillArgs(message, errorArgs));
	}

	public DataAccessException(Exception e, String message, Object... errorArgs) {
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
