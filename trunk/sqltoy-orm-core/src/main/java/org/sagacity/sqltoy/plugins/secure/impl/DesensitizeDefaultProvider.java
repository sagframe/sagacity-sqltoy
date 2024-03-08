package org.sagacity.sqltoy.plugins.secure.impl;

import org.sagacity.sqltoy.config.model.SecureMask;
import org.sagacity.sqltoy.plugins.secure.DesensitizeProvider;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * @project sagacity-sqltoy
 * @description sqltoy 提供默认的脱敏处理
 * @author zhongxuchen
 * @version v1.0,Date:2021-11-10
 */
public class DesensitizeDefaultProvider implements DesensitizeProvider {

	@Override
	public String desensitize(String content, SecureMask maskType) {
		if (content == null || "".equals(content)) {
			return content;
		}
		return maskStr(maskType, content);
	}

	/**
	 * @TODO 实际脱敏处理
	 * @param mask
	 * @param value
	 * @return
	 */
	private String maskStr(SecureMask mask, Object value) {
		String type = mask.getType();
		String realStr = value.toString();
		int size = realStr.length();
		// 单字符无需脱敏
		if (size == 1) {
			return realStr;
		}
		String maskCode = mask.getMaskCode();
		int headSize = mask.getHeadSize();
		int tailSize = mask.getTailSize();
		// 自定义剪切长度
		if (headSize > 0 || tailSize > 0) {
			return StringUtil.secureMask(realStr, (headSize > 0) ? headSize : 0, (tailSize > 0) ? tailSize : 0,
					maskCode);
		}
		// 按比例模糊(百分比)
		if (mask.getMaskRate() > 0) {
			int maskSize = Double.valueOf(size * mask.getMaskRate() * 1.00 / 100).intValue();
			if (maskSize < 1) {
				maskSize = 1;
			} else if (maskSize >= size) {
				maskSize = size - 1;
			}
			tailSize = (size - maskSize) / 2;
			headSize = size - maskSize - tailSize;
			if (maskCode == null) {
				maskCode = "*";
				if (maskSize > 3) {
					maskCode = "***";
				} else if (maskSize == 2) {
					maskCode = "**";
				}
			}
		}
		// 按类别处理
		// 电话
		if ("tel".equals(type)) {
			if (size >= 11) {
				return StringUtil.secureMask(realStr, 3, 4, maskCode);
			} else {
				return StringUtil.secureMask(realStr, 4, 0, maskCode);
			}
		}
		// 邮件(首字符@gmail.com 形式)
		if ("email".equals(type)) {
			String maskStr = (maskCode == null || "".equals(maskCode)) ? "***" : maskCode;
			return realStr.substring(0, 1).concat(maskStr).concat(realStr.substring(realStr.indexOf("@")));
		}
		// 身份证
		if ("id-card".equals(type)) {
			return StringUtil.secureMask(realStr, 0, 4, maskCode);
		}
		// 银行卡
		if ("bank-card".equals(type)) {
			return StringUtil.secureMask(realStr, 6, 4, maskCode);
		}
		// 姓名
		if ("name".equals(type)) {
			if (size >= 4) {
				return StringUtil.secureMask(realStr, 2, 0, maskCode);
			} else {
				return StringUtil.secureMask(realStr, 1, 0, maskCode);
			}
		}
		// 地址
		if ("address".equals(type)) {
			if (size >= 30) {
				return StringUtil.secureMask(realStr, 7, 0, maskCode);
			} else if (size >= 12) {
				return StringUtil.secureMask(realStr, 6, 0, maskCode);
			} else if (size >= 8) {
				return StringUtil.secureMask(realStr, 4, 0, maskCode);
			} else {
				return StringUtil.secureMask(realStr, 2, 0, maskCode);
			}
		}
		// 对公银行账号
		if ("public-account".equals(type)) {
			return StringUtil.secureMask(realStr, 2, 0, maskCode);
		}
		return StringUtil.secureMask(realStr, headSize, tailSize, maskCode);
	}
}
