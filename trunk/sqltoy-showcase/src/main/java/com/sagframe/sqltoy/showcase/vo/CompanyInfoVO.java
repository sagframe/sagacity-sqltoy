/**
 * 
 */
package com.sagframe.sqltoy.showcase.vo;

import java.io.Serializable;

/**
 * @project sqltoy-boot-showcase
 * @description <p>请在此说明类的功能</p>
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:CompanyInfoVO.java,Revision:v1.0,Date:2020年1月11日
 */
public class CompanyInfoVO implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1123439783877258012L;

	private String companyId;
	
	private String companyName;
	
	private String mdmCode;
	
	private String companyNature;
	
	private String registAddress;
	
	private String creditCode;
	
	private String companyType;
	
	private String companyTypeName;

	/**
	 * @return the companyId
	 */
	public String getCompanyId() {
		return companyId;
	}

	/**
	 * @param companyId the companyId to set
	 */
	public void setCompanyId(String companyId) {
		this.companyId = companyId;
	}

	/**
	 * @return the companyName
	 */
	public String getCompanyName() {
		return companyName;
	}

	/**
	 * @param companyName the companyName to set
	 */
	public void setCompanyName(String companyName) {
		this.companyName = companyName;
	}

	/**
	 * @return the mdmCode
	 */
	public String getMdmCode() {
		return mdmCode;
	}

	/**
	 * @param mdmCode the mdmCode to set
	 */
	public void setMdmCode(String mdmCode) {
		this.mdmCode = mdmCode;
	}

	/**
	 * @return the companyNature
	 */
	public String getCompanyNature() {
		return companyNature;
	}

	/**
	 * @param companyNature the companyNature to set
	 */
	public void setCompanyNature(String companyNature) {
		this.companyNature = companyNature;
	}

	/**
	 * @return the registAddress
	 */
	public String getRegistAddress() {
		return registAddress;
	}

	/**
	 * @param registAddress the registAddress to set
	 */
	public void setRegistAddress(String registAddress) {
		this.registAddress = registAddress;
	}

	/**
	 * @return the creditCode
	 */
	public String getCreditCode() {
		return creditCode;
	}

	/**
	 * @param creditCode the creditCode to set
	 */
	public void setCreditCode(String creditCode) {
		this.creditCode = creditCode;
	}

	/**
	 * @return the companyType
	 */
	public String getCompanyType() {
		return companyType;
	}

	/**
	 * @param companyType the companyType to set
	 */
	public void setCompanyType(String companyType) {
		this.companyType = companyType;
	}

	/**
	 * @return the companyTypeName
	 */
	public String getCompanyTypeName() {
		return companyTypeName;
	}

	/**
	 * @param companyTypeName the companyTypeName to set
	 */
	public void setCompanyTypeName(String companyTypeName) {
		this.companyTypeName = companyTypeName;
	}
	
}
