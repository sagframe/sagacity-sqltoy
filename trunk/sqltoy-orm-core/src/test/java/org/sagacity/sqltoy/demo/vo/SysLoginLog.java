package org.sagacity.sqltoy.demo.vo;

import java.io.Serializable;
import java.math.BigInteger;
import java.time.LocalDateTime;

import org.sagacity.sqltoy.config.annotation.Column;
import org.sagacity.sqltoy.config.annotation.Entity;
import org.sagacity.sqltoy.config.annotation.Id;


/**
 * @project fisher application
 * @author fisher
 * @version 1.0.0
 */
@Entity(tableName = "sys_login_log", comment = "系统访问记录", pk_constraint = "PRIMARY", schema = "common")
public class SysLoginLog implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 7597934527935925661L;

	/**
	 * jdbcType:BIGINT
	 */
	@Id(strategy = "identity")
	@Column(name = "log_id", comment = "日志编号", length = 19L, type = java.sql.Types.BIGINT, nativeType = "BIGINT", nullable = false, autoIncrement = true)
	private BigInteger logId;
	/**
	 * jdbcType:BIGINT
	 */
	@Column(name = "login_user_id", comment = "访问ID", length = 19L, type = java.sql.Types.BIGINT, nativeType = "BIGINT", nullable = false)
	private BigInteger loginUserId;
	/**
	 * jdbcType:VARCHAR
	 */
	@Column(name = "user_name", comment = "用户账号", length = 50L, defaultValue = "", type = java.sql.Types.VARCHAR, nativeType = "VARCHAR", nullable = true)
	private String userName;
	/**
	 * jdbcType:VARCHAR
	 */
	@Column(name = "ipaddr", comment = "登录IP地址", length = 128L, defaultValue = "", type = java.sql.Types.VARCHAR, nativeType = "VARCHAR", nullable = true)
	private String ipaddr;
	/**
	 * jdbcType:VARCHAR
	 */
	@Column(name = "login_location", comment = "登录地点", length = 255L, defaultValue = "", type = java.sql.Types.VARCHAR, nativeType = "VARCHAR", nullable = true)
	private String loginLocation;
	/**
	 * jdbcType:VARCHAR
	 */
	@Column(name = "browser", comment = "浏览器类型", length = 50L, defaultValue = "", type = java.sql.Types.VARCHAR, nativeType = "VARCHAR", nullable = true)
	private String browser;
	/**
	 * jdbcType:VARCHAR
	 */
	@Column(name = "os", comment = "操作系统", length = 50L, defaultValue = "", type = java.sql.Types.VARCHAR, nativeType = "VARCHAR", nullable = true)
	private String os;
	/**
	 * jdbcType:CHAR
	 */
	@Column(name = "status", comment = "登录状态（0成功 1失败）", length = 1L, defaultValue = "0", type = java.sql.Types.CHAR, nativeType = "CHAR", nullable = true)
	private String status;
	/**
	 * jdbcType:TEXT
	 */
	@Column(name = "msg", comment = "提示消息", length = 65535L, type = java.sql.Types.VARCHAR, nativeType = "TEXT", nullable = true)
	private String msg;
	/**
	 * jdbcType:DATETIME
	 */
	@Column(name = "login_time", comment = "访问时间", length = 19L, type = java.sql.Types.DATE, nativeType = "DATETIME", nullable = true)
	private LocalDateTime loginTime;

	/** default constructor */
	public SysLoginLog() {
	}

	/** pk constructor */
	public SysLoginLog(BigInteger logId) {
		this.logId = logId;
	}

	/**
	 * @param logId the logId to set
	 */
	public void setLogId(BigInteger logId) {
		this.logId = logId;
	}

	/**
	 * @return the LogId
	 */
	public BigInteger getLogId() {
		return this.logId;
	}

	/**
	 * @param loginUserId the loginUserId to set
	 */
	public void setLoginUserId(BigInteger loginUserId) {
		this.loginUserId = loginUserId;
	}

	/**
	 * @return the LoginUserId
	 */
	public BigInteger getLoginUserId() {
		return this.loginUserId;
	}

	/**
	 * @param userName the userName to set
	 */
	public void setUserName(String userName) {
		this.userName = userName;
	}

	/**
	 * @return the UserName
	 */
	public String getUserName() {
		return this.userName;
	}

	/**
	 * @param ipaddr the ipaddr to set
	 */
	public void setIpaddr(String ipaddr) {
		this.ipaddr = ipaddr;
	}

	/**
	 * @return the Ipaddr
	 */
	public String getIpaddr() {
		return this.ipaddr;
	}

	/**
	 * @param loginLocation the loginLocation to set
	 */
	public void setLoginLocation(String loginLocation) {
		this.loginLocation = loginLocation;
	}

	/**
	 * @return the LoginLocation
	 */
	public String getLoginLocation() {
		return this.loginLocation;
	}

	/**
	 * @param browser the browser to set
	 */
	public void setBrowser(String browser) {
		this.browser = browser;
	}

	/**
	 * @return the Browser
	 */
	public String getBrowser() {
		return this.browser;
	}

	/**
	 * @param os the os to set
	 */
	public void setOs(String os) {
		this.os = os;
	}

	/**
	 * @return the Os
	 */
	public String getOs() {
		return this.os;
	}

	/**
	 * @param status the status to set
	 */
	public void setStatus(String status) {
		this.status = status;
	}

	/**
	 * @return the Status
	 */
	public String getStatus() {
		return this.status;
	}

	/**
	 * @param msg the msg to set
	 */
	public void setMsg(String msg) {
		this.msg = msg;
	}

	/**
	 * @return the Msg
	 */
	public String getMsg() {
		return this.msg;
	}

	/**
	 * @param loginTime the loginTime to set
	 */
	public void setLoginTime(LocalDateTime loginTime) {
		this.loginTime = loginTime;
	}

	/**
	 * @return the LoginTime
	 */
	public LocalDateTime getLoginTime() {
		return this.loginTime;
	}

	/**
	 * @todo vo columns to String
	 */
	@Override
	public String toString() {
		StringBuilder columnsBuffer = new StringBuilder();
		columnsBuffer.append("logId=").append(getLogId()).append("\n");
		columnsBuffer.append("loginUserId=").append(getLoginUserId()).append("\n");
		columnsBuffer.append("userName=").append(getUserName()).append("\n");
		columnsBuffer.append("ipaddr=").append(getIpaddr()).append("\n");
		columnsBuffer.append("loginLocation=").append(getLoginLocation()).append("\n");
		columnsBuffer.append("browser=").append(getBrowser()).append("\n");
		columnsBuffer.append("os=").append(getOs()).append("\n");
		columnsBuffer.append("status=").append(getStatus()).append("\n");
		columnsBuffer.append("msg=").append(getMsg()).append("\n");
		columnsBuffer.append("loginTime=").append(getLoginTime()).append("\n");
		return columnsBuffer.toString();
	}

}
