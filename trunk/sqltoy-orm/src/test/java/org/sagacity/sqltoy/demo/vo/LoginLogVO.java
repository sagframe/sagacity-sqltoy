package org.sagacity.sqltoy.demo.vo;

import java.io.Serializable;
import java.math.BigInteger;
import java.time.LocalDateTime;

/**
 * @project fisher application
 * @author fisher
 * @version 1.0.0
 * @description sys_login_log,系统登录访问记录
 */
public class LoginLogVO implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1680910188658079021L;

	/*---begin-auto-generate-don't-update-this-area--*/
	/**
	 * 日志编号
	 */
	private BigInteger logId;
	/**
	 * 访问ID
	 */
	private BigInteger loginUserId;
	/**
	 * 用户账号
	 */
	private String userName;
	/**
	 * 登录IP地址
	 */
	private String ipaddr;
	/**
	 * 登录地点
	 */
	private String loginLocation;
	/**
	 * 浏览器类型
	 */
	private String browser;
	/**
	 * 操作系统
	 */
	private String os;
	/**
	 * 登录状态（0成功 1失败）
	 */
	private String status;
	/**
	 * 提示消息
	 */
	private String msg;
	/**
	 * 访问时间
	 */
	private LocalDateTime loginTime;

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
	/*---end-auto-generate-don't-update-this-area--*/
}