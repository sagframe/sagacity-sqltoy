package org.sagacity.sqltoy.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.sagacity.sqltoy.SqlToyConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sagacity-sqltoy
 * @description 数据处理工具类
 *              <li>提供数字类型的数据转换成特定格式的字符串</li>
 *              <li>提供转换字符串到数字类型数据</li>
 *              <li>提供随机数获取方法，包括给定范围的数据取出不重复的数字</li>
 *              <li>提供字符串表达式函数的执行</li>
 *              <li>提供金额、数字的大小写互转功能</li>
 * @author zhongxuchen
 * @version v1.0,Date:Oct 18, 2007 9:19:50 AM
 */
public class NumberUtil {
	/**
	 * 定义日志
	 */
	private final static Logger logger = LoggerFactory.getLogger(NumberUtil.class);

	/**
	 * 整数数字正则表达式
	 */
	private final static String INTEGER_REGEX = "^[+-]?[\\d]+$";

	/**
	 * 数字格式正则表达式(整数浮点数)
	 */
	private final static String NUMBER_REGEX = "^[+-]?[\\d]+(\\.\\d+)?$";

	// 最大到京
	private final static String[] moneyUOM = { "拾", "佰", "仟", "万", "拾", "佰", "仟", "亿", "拾", "佰", "仟", "万", "拾", "佰",
			"仟", "兆", "拾", "佰", "仟", "万", "拾", "佰", "仟", "亿", "拾", "佰", "仟", "万", "拾", "佰", "仟", "京" };
	private final static String[] numUOM = { "十", "百", "千", "万", "十", "百", "千", "亿", "十", "百", "千", "万", "十", "百", "千",
			"兆", "十", "百", "千", "万", "十", "百", "千", "亿", "十", "百", "千", "万", "十", "百", "千", "京" };
	private final static String[] capitalMoneyNumber = { "", "壹", "贰", "叁", "肆", "伍", "陆", "柒", "捌", "玖" };
	private final static String[] captialNumber = { "", "一", "二", "三", "四", "五", "六", "七", "八", "九", "十" };

	private static final SecureRandom SECURE_RANDOM = new SecureRandom();

	// 一亿常量
	private static final BigDecimal HUNDRED_MILLION = new BigDecimal("100000000");
	// 一万
	private static final BigDecimal TEN_THOUSAND = new BigDecimal("10000");

	public final static class Pattern {
		public final static String CAPITAL = "capital";
		public final static String CAPITAL_MONEY = "capitalmoney";
		public final static String CAPITAL_RMB = "capital-rmb";
		public final static String CAPITAL_EN = "capital-en";
		public final static String CAPITAL_ENGLISH = "capital-english";
	}

	private NumberUtil() {
	}

	/**
	 * @todo 根据给定的模式将数据对象转换成格式化的字符串
	 * @param target
	 * @param pattern
	 * @return
	 */
	public static String format(Object target, String pattern) {
		return format(target, pattern, null, null);
	}

	public static String format(Object target, String pattern, RoundingMode roundingMode, Locale locale) {
		return format(target, pattern, roundingMode, locale, null);
	}

	/**
	 * @todo 根据给定的模式将数据对象转换成格式化的字符串,currency仅对capital-en/capital-english英文金额格式生效,
	 *       指定币种(ISO-4217代码如USD,或直接写单位词)时输出SAY开头的票据标准格式,为null输出不带币种的既有格式
	 * @param target
	 * @param pattern
	 * @param roundingMode
	 * @param locale
	 * @param currency   币种代码或单位词(如USD、POUNDS STERLING)
	 * @return
	 */
	public static String format(Object target, String pattern, RoundingMode roundingMode, Locale locale,
			String currency) {
		if (target == null) {
			return null;
		}
		if (pattern == null) {
			return target.toString();
		}
		try {
			String tmpStr = target.toString().replace(",", "").trim().toLowerCase();
			if ("".equals(tmpStr) || "null".equals(tmpStr) || "nan".equals(tmpStr)) {
				return "";
			}
			BigDecimal tmp = new BigDecimal(tmpStr);
			String lowPattern = pattern.toLowerCase();
			// 将数字转换成大写汉字
			if (lowPattern.equals(Pattern.CAPITAL)) {
				return numberToChina(tmpStr, false);
			}
			// 数字转换成大写汉字金额
			if (lowPattern.equals(Pattern.CAPITAL_MONEY) || lowPattern.equals(Pattern.CAPITAL_RMB)) {
				return toCapitalMoney(tmp);
			}
			// 数字转换成英文金额(currency指定币种时输出SAY开头的票据标准格式)
			if (lowPattern.equals(Pattern.CAPITAL_EN) || lowPattern.equals(Pattern.CAPITAL_ENGLISH)) {
				return convertToEnglishMoney(tmp, currency);
			}
			// locale为null时取sqltoy统一配置的默认区域(未设置则跟随JVM默认区域)
			DecimalFormat df = (DecimalFormat) DecimalFormat
					.getInstance((locale == null) ? SqlToyConstants.getLocale() : locale);
			if (roundingMode != null) {
				df.setRoundingMode(roundingMode);
			}
			df.applyPattern(pattern);
			return df.format(tmp);
		} catch (Exception e) {
			logger.error("value:" + target + ";pattern=" + pattern + ";" + e.getMessage(), e);
		}
		return target.toString();
	}

	/**
	 * @todo 格式化不同币种的金额
	 * @param target
	 * @param pattern
	 * @param locale
	 * @return
	 */
	public static String formatCurrency(Object target, String pattern, Locale locale) {
		if (target == null) {
			return null;
		}
		if (pattern == null) {
			return target.toString();
		}
		try {
			String tmpStr = target.toString().replace(",", "").trim().toLowerCase();
			if ("".equals(tmpStr) || "null".equals(tmpStr) || "nan".equals(tmpStr)) {
				return "";
			}
			String lowPattern = pattern.toLowerCase();
			BigDecimal tmp = new BigDecimal(tmpStr);
			if (lowPattern.equals(Pattern.CAPITAL)) {
				return numberToChina(tmpStr, false);
			}
			if (lowPattern.equals(Pattern.CAPITAL_MONEY) || lowPattern.equals(Pattern.CAPITAL_RMB)) {
				return toCapitalMoney(tmp);
			}
			// 数字转换成英文金额
			if (lowPattern.equals(Pattern.CAPITAL_EN) || lowPattern.equals(Pattern.CAPITAL_ENGLISH)) {
				return convertToEnglishMoney(tmp);
			}
			// locale为null时取sqltoy统一配置的默认区域(未设置则跟随JVM默认区域)
			DecimalFormat df = (DecimalFormat) DecimalFormat
					.getCurrencyInstance((locale == null) ? SqlToyConstants.getLocale() : locale);
			df.applyPattern(pattern);
			return df.format(tmp);
		} catch (Exception e) {
			logger.error("value:" + target + ";pattern=" + pattern + ";" + e.getMessage(), e);
		}
		return target.toString();
	}

	/**
	 * @todo 转换百分数
	 * @param percent :example: 90% return 0.9
	 * @return
	 */
	public static Float parsePercent(String percent) {
		if (StringUtil.isBlank(percent)) {
			return null;
		}
		NumberFormat nf = NumberFormat.getPercentInstance();
		try {
			return Float.valueOf(nf.parse(percent).floatValue());
		} catch (ParseException e) {
			logger.error("解析百分数[{}]失败:{}", percent, e.getMessage());
		}
		return null;
	}

	/**
	 * @todo 解析float 字符串
	 * @param floatStr
	 * @param maxIntDigits
	 * @param maxFractionDigits
	 * @return
	 */
	public static Float parseFloat(String floatStr, Integer maxIntDigits, Integer maxFractionDigits) {
		Number number = parseStr(floatStr, maxIntDigits, null, maxFractionDigits, null);
		if (number != null) {
			return Float.valueOf(number.floatValue());
		}
		return null;
	}

	/**
	 * @todo 解析decimal 字符串
	 * @param decimalStr
	 * @param maxIntDigits
	 * @param maxFractionDigits
	 * @return
	 */
	public static BigDecimal parseDecimal(String decimalStr, Integer maxIntDigits, Integer maxFractionDigits) {
		Number number = parseStr(decimalStr, maxIntDigits, null, maxFractionDigits, null);
		if (number != null) {
			// 用toString构造而非doubleValue:double的精确二进制展开会带来精度尾巴(如1234.56→1234.559999...945)
			return new BigDecimal(number.toString());
		}
		return null;
	}

	/**
	 * @todo 解析double 字符串
	 * @param doubleStr
	 * @param maxIntDigits
	 * @param maxFractionDigits
	 * @return
	 */
	public static Double parseDouble(String doubleStr, Integer maxIntDigits, Integer maxFractionDigits) {
		Number number = parseStr(doubleStr, maxIntDigits, null, maxFractionDigits, null);
		if (number != null) {
			return Double.valueOf(number.doubleValue());
		}
		return null;
	}

	/**
	 * @todo 将大写中文金额字符串转换成数字,与toCapitalMoney输出范围对称,最大支持到京级(10^63):
	 *       单位幂值拾=10、佰=100、仟=1000、万=10^4、亿=10^8、兆=10^16、京=10^32,
	 *       组合单位(万亿=10^12、万兆=10^20、兆京=10^48、万亿兆京=10^60等)按幂相乘解析
	 * @param capitalMoney
	 * @return
	 */
	public static BigDecimal capitalMoneyToNum(String capitalMoney) {
		if (StringUtil.isBlank(capitalMoney)) {
			return null;
		}
		capitalMoney = capitalMoney.replaceAll("\\s+", "").replace("零", "").replace("圆", "元");
		// 兼容小写中文数字(一~九、两、十、百、千):统一映射为大写后再按大写金额解析,
		// 避免numberToChina输出的小写形式无法反向解析(如"一千二百三十四")
		String[] lowerNums = { "一", "二", "两", "三", "四", "五", "六", "七", "八", "九", "十", "百", "千" };
		String[] upperNums = { "壹", "贰", "贰", "叁", "肆", "伍", "陆", "柒", "捌", "玖", "拾", "佰", "仟" };
		for (int i = 0; i < lowerNums.length; i++) {
			capitalMoney = capitalMoney.replace(lowerNums[i], upperNums[i]);
		}
		// 剥离币种前缀(如"人民币")
		capitalMoney = capitalMoney.replace("人民币", "");
		// 合法性校验:清洗后仅允许数字、大写金额字符与整/负,存在无法识别内容返回null(与englishMoneyToNum容错语义一致)
		if (capitalMoney.isEmpty() || !capitalMoney.matches("[0-9壹贰叁肆伍陆柒捌玖拾佰仟万亿兆京元角分厘整负]+")) {
			return null;
		}
		// 负号提前剥离
		boolean isNegative = capitalMoney.startsWith("负");
		if (isNegative) {
			capitalMoney = capitalMoney.substring(1);
		}
		// 默认小数位长度，默认到厘
		int scale = 3;
		if (capitalMoney.endsWith("整")) {
			capitalMoney = capitalMoney.replace("整", "");
			scale = 0;
		}
		capitalMoney = capitalMoney.trim();
		for (int i = 0; i < 9; i++) {
			capitalMoney = capitalMoney.replace(capitalMoneyNumber[i + 1], Integer.toString(i + 1));
		}
		// 单位幂累加解析:逐字符识别数字与单位,组单位(万亿兆京)连续出现即幂相乘构成组合单位
		BigDecimal total = BigDecimal.ZERO;
		// 当前组内数字(仟佰拾组合而成,0~9999)
		BigDecimal section = BigDecimal.ZERO;
		// 当前组的组合单位幂(如万亿=10^12),为null表示尚未遇到组单位
		BigDecimal groupUnit = null;
		// 当前组数字(组单位生效前的基数)
		BigDecimal groupValue = BigDecimal.ZERO;
		// 角分厘部分,以毫(千分之一)为单位整数累计避免浮点误差
		long fractionMilli = 0;
		// 已读入尚未结合单位的单个数字
		int lastDigit = 0;
		for (int i = 0; i < capitalMoney.length(); i++) {
			char unitChar = capitalMoney.charAt(i);
			if (unitChar >= '1' && unitChar <= '9') {
				// 新组数字开始:上一组的组合单位链已闭合,提交累加
				if (groupUnit != null) {
					total = total.add(groupValue.multiply(groupUnit));
					groupUnit = null;
				}
				lastDigit = unitChar - '0';
			} else if (unitChar == '拾' || unitChar == '佰' || unitChar == '仟') {
				int unitVal = (unitChar == '拾') ? 10 : ((unitChar == '佰') ? 100 : 1000);
				// 拾/佰/仟前无数字的历史写法按壹拾/壹佰/壹仟解析(如"拾元整"=10)
				section = section.add(BigDecimal.valueOf((lastDigit == 0 ? 1 : lastDigit) * unitVal));
				lastDigit = 0;
			} else if (unitChar == '万' || unitChar == '亿' || unitChar == '兆' || unitChar == '京') {
				section = section.add(BigDecimal.valueOf(lastDigit));
				lastDigit = 0;
				BigDecimal unitVal = (unitChar == '万') ? TEN_THOUSAND
						: ((unitChar == '亿') ? HUNDRED_MILLION : BigDecimal.TEN.pow(unitChar == '兆' ? 16 : 32));
				if (groupUnit == null) {
					// 新组:组数字为当前仟佰拾累计(无数字的裸单位按壹计)
					groupValue = (section.compareTo(BigDecimal.ZERO) == 0) ? BigDecimal.ONE : section;
					groupUnit = unitVal;
				} else {
					// 组单位后无数字直接跟下一个组单位:组合单位,幂相乘(如万亿=10^4×10^8)
					groupUnit = groupUnit.multiply(unitVal);
				}
				section = BigDecimal.ZERO;
			} else if (unitChar == '元') {
				section = section.add(BigDecimal.valueOf(lastDigit));
				lastDigit = 0;
			} else if (unitChar == '角' || unitChar == '分' || unitChar == '厘') {
				fractionMilli += lastDigit * ((unitChar == '角') ? 100 : ((unitChar == '分') ? 10 : 1));
				lastDigit = 0;
			}
			// 其余字符(如"人民币"前缀)按单位锚定原则忽略
		}
		if (groupUnit != null) {
			total = total.add(groupValue.multiply(groupUnit));
		}
		BigDecimal result = total.add(section).add(BigDecimal.valueOf(lastDigit))
				.add(BigDecimal.valueOf(fractionMilli, 3));
		if (isNegative) {
			return BigDecimal.ZERO.subtract(result).setScale(scale, RoundingMode.HALF_UP);
		}
		return result.setScale(scale, RoundingMode.HALF_UP);
	}

	/**
	 * @todo 将数字转换成中文大写金额
	 * @param money
	 * @return
	 */
	public static String toCapitalMoney(BigDecimal money) {
		// 取绝对值
		BigDecimal realMoney = money.setScale(5, RoundingMode.HALF_UP).abs();
		if (realMoney.compareTo(BigDecimal.ZERO) == 0) {
			// 人行《正确填写票据和结算凭证的基本规定》：大写金额到"元"为止应写"整"字
			return "零元整";
		}
		// 绝对值字符串
		String sourceStr = realMoney.toString();
		int dotIndex = sourceStr.indexOf(".");
		String intPartStr = (dotIndex == -1) ? sourceStr : sourceStr.substring(0, dotIndex);
		String decimalPartStr = "";
		if (dotIndex != -1) {
			decimalPartStr = sourceStr.substring(dotIndex + 1);
		}
		// 处理整数部分
		// 金额大写规范：拾位处于金额开头必须带"壹"(如10元为"壹拾元整"而非"拾元整")，防止添字篡改
		String result = numberToChina(intPartStr, true);
		if (!"".equals(result)) {
			result += "元";
		}

		// 小于零
		if (money.compareTo(BigDecimal.ZERO) < 0) {
			result = "负" + result;
		}

		// 没有小数
		if (dotIndex == -1 || ("".equals(decimalPartStr) || Integer.parseInt(decimalPartStr) == 0)) {
			result += "整";
		} else {
			String[] uomName = { "角", "分", "厘" };
			int indexValue;
			boolean hasZero = false;
			String dotPartStr = "";
			int dotPartSize = decimalPartStr.length() > 3 ? 3 : decimalPartStr.length();
			for (int i = dotPartSize - 1; i >= 0; i--) {
				indexValue = Integer.valueOf(decimalPartStr.substring(i, i + 1));
				if (indexValue != 0) {
					dotPartStr = capitalMoneyNumber[indexValue] + uomName[i] + dotPartStr;
					hasZero = true;
				} else {
					if (hasZero) {
						dotPartStr = "零" + dotPartStr;
						hasZero = false;
					}
				}
			}
			// 人行《正确填写票据和结算凭证的基本规定》："零"仅用于数字中间补位，不足一元直接从角分写起(如0.05为"伍分"而非"零伍分")
			if (result.isEmpty() || "负".equals(result)) {
				while (dotPartStr.startsWith("零")) {
					dotPartStr = dotPartStr.substring(1);
				}
			}
			result += dotPartStr;
		}
		return result;
	}

	/**
	 * @todo 将多位阿拉伯数字转换成中文显示
	 * @param sourceInt
	 * @return
	 */
	public static String numberToChina(int sourceInt) {
		return numberToChina(Integer.toString(sourceInt), false);
	}

	/**
	 * @todo 求数组中数据的最大值(忽略null元素)
	 * @param bigArray
	 * @return
	 */
	public static BigDecimal getMax(BigDecimal[] bigArray) {
		BigDecimal max = null;
		if (bigArray == null) {
			return null;
		}
		for (BigDecimal item : bigArray) {
			if (item == null) {
				continue;
			}
			if (max == null || max.compareTo(item) < 0) {
				max = item;
			}
		}
		return max;
	}

	/**
	 * @todo 求数组中数据的最小值(忽略null元素)
	 * @param bigArray
	 * @return
	 */
	public static BigDecimal getMin(BigDecimal[] bigArray) {
		BigDecimal min = null;
		if (bigArray == null) {
			return null;
		}
		for (BigDecimal item : bigArray) {
			if (item == null) {
				continue;
			}
			if (min == null || min.compareTo(item) > 0) {
				min = item;
			}
		}
		return min;
	}

	public static BigDecimal getAverage(BigDecimal[] bigDeicmalArray) {
		return getAverage(bigDeicmalArray, 4, RoundingMode.HALF_UP);
	}

	/**
	 * @todo 求数组中数据的平均值
	 * @param bigDeicmalArray
	 * @param radixSize
	 * @return
	 */
	public static BigDecimal getAverage(BigDecimal[] bigDeicmalArray, int radixSize) {
		return getAverage(bigDeicmalArray, radixSize, RoundingMode.HALF_UP);
	}

	public static BigDecimal getAverage(BigDecimal[] bigDeicmalArray, int radixSize, RoundingMode roundingMode) {
		BigDecimal sum = BigDecimal.ZERO;
		if (bigDeicmalArray == null || bigDeicmalArray.length == 0) {
			return sum;
		}
		for (int i = 0; i < bigDeicmalArray.length; i++) {
			if (bigDeicmalArray[i] != null) {
				sum = sum.add(bigDeicmalArray[i]);
			}
		}
		return sum.divide(new BigDecimal(bigDeicmalArray.length), radixSize,
				roundingMode == null ? RoundingMode.HALF_UP : roundingMode);
	}

	/**
	 * @todo 求数组中数据的和
	 * @param bigArray
	 * @return
	 */
	public static BigDecimal summary(BigDecimal[] bigArray) {
		BigDecimal sum = BigDecimal.ZERO;
		if (bigArray == null || bigArray.length == 0) {
			return sum;
		}
		for (int i = 0; i < bigArray.length; i++) {
			if (bigArray[i] != null) {
				sum = sum.add(bigArray[i]);
			}
		}
		return sum;
	}

	/**
	 * @todo 私有方法，为parseDouble,parseFloat等提供统一的处理实现
	 * @param parseTarget
	 * @param maxIntDigits
	 * @param minIntDigits
	 * @param maxFractionDigits
	 * @param minFractionDigits
	 * @return
	 */
	private static Number parseStr(String parseTarget, Integer maxIntDigits, Integer minIntDigits,
			Integer maxFractionDigits, Integer minFractionDigits) {
		if (StringUtil.isBlank(parseTarget)) {
			return null;
		}
		NumberFormat nf = NumberFormat.getInstance();
		try {
			// 最大整数位
			if (maxIntDigits != null) {
				nf.setMaximumIntegerDigits(maxIntDigits.intValue());
			}
			// 最小整数位
			if (minIntDigits != null) {
				nf.setMinimumIntegerDigits(minIntDigits.intValue());
			}
			// 最大小数位
			if (maxFractionDigits != null) {
				nf.setMaximumFractionDigits(maxFractionDigits.intValue());
			}
			// 最小小数位
			if (minFractionDigits != null) {
				nf.setMinimumFractionDigits(minFractionDigits.intValue());
			}
			Number number = nf.parse(parseTarget.replace(",", ""));
			// JDK的NumberFormat.parse不遵循maximumIntegerDigits/maximumFractionDigits设置,手动完成位数限制
			if (number != null && (maxIntDigits != null || maxFractionDigits != null)) {
				return applyDigitLimits(number, maxIntDigits, maxFractionDigits);
			}
			return number;
		} catch (ParseException e) {
			logger.error("value:" + parseTarget + " " + e.getMessage(), e);
		}
		return null;
	}

	/**
	 * @todo 应用解析位数限制(补足JDK NumberFormat.parse不遵循位数设置的缺陷):
	 *       maxIntDigits超限时整数部分保留低位(如1234.56限3位整数得234.56,与NumberFormat格式化语义一致),
	 *       maxFractionDigits超限时直接截断(非四舍五入,如1.239限2位小数得1.23)
	 * @param number
	 * @param maxIntDigits
	 * @param maxFractionDigits
	 * @return
	 */
	private static Number applyDigitLimits(Number number, Integer maxIntDigits, Integer maxFractionDigits) {
		BigDecimal decimal = new BigDecimal(number.toString());
		if (maxIntDigits != null && maxIntDigits > 0) {
			BigDecimal intPart = decimal.setScale(0, RoundingMode.DOWN);
			if (intPart.abs().toBigInteger().toString().length() > maxIntDigits) {
				decimal = decimal.subtract(intPart).add(intPart.remainder(BigDecimal.TEN.pow(maxIntDigits)));
			}
		}
		if (maxFractionDigits != null) {
			decimal = decimal.setScale(maxFractionDigits, RoundingMode.DOWN);
		}
		return decimal;
	}

	/**
	 * 组单位组合表：组序号按二进制位组合单位，1组=万、2组=亿、4组=兆、8组=京， 如组序号3(10^12)为"万亿"，与原单位表中万、亿、兆、京的位置一致
	 */
	private static final String[] GROUP_UNIT_BITS = { "万", "亿", "兆", "京" };

	/**
	 * @todo 将多位阿拉伯数字转换成中文
	 * @param sourceInt
	 * @param isMoney
	 * @return
	 */
	private static String numberToChina(String sourceInt, boolean isMoney) {
		if (StringUtil.isBlank(sourceInt)) {
			return "";
		}
		// 负号处理(金额场景已在外层取绝对值，此处兼容负数直接调用)
		boolean negative = sourceInt.startsWith("-");
		String digits = negative ? sourceInt.substring(1) : sourceInt;
		if (digits.isEmpty() || "0".equals(digits)) {
			// 0按规范读"零"；金额场景整数部分为0返回空串，交由上层零头逻辑处理(如0.56为"伍角陆分")
			return isMoney ? "" : "零";
		}
		String[] chinaNum = (isMoney ? capitalMoneyNumber : captialNumber);
		String[] realUOM = (isMoney ? moneyUOM : numUOM);
		int length = digits.length();
		// 按4位一组从高位向低位转换；lastPos记录已输出的最低位数字所在位置(10^lastPos)，用于判断组间是否需要补零
		int groupCount = (length + 3) / 4;
		StringBuilder result = new StringBuilder(negative ? "负" : "");
		int lastPos = -1;
		for (int g = groupCount - 1; g >= 0; g--) {
			int start = Math.max(0, length - (g + 1) * 4);
			int end = length - g * 4;
			int groupValue = Integer.parseInt(digits.substring(start, end));
			if (groupValue == 0) {
				continue;
			}
			// 本组最高位数字位置与已输出内容的间隔达到2位以上时补零
			int highPos = 4 * g + Integer.toString(groupValue).length() - 1;
			if (lastPos - highPos >= 2) {
				result.append("零");
			}
			result.append(fourDigitsToChina(groupValue, chinaNum, realUOM)).append(groupUnit(g));
			int trailingZeros = 0;
			for (int t = groupValue; t % 10 == 0 && t > 0; t /= 10) {
				trailingZeros++;
			}
			lastPos = 4 * g + trailingZeros;
		}
		String resultStr = result.toString();
		// GB/T 15835及汉语规范读法：普通数字最高位为十位时"十"前不加"一"(如15为"十五"、100000为"十万")，
		// 中间位置保留(如110为"一百一十")；金额场景(isMoney)按人行规定保留"壹拾"防涂改
		if (!isMoney) {
			if (resultStr.startsWith("一十")) {
				resultStr = resultStr.substring(1);
			} else if (resultStr.startsWith("负一十")) {
				resultStr = "负".concat(resultStr.substring(2));
			}
		}
		return resultStr;
	}

	/**
	 * @todo 将组内(万以内)数字转换成中文，自动处理组内零(如1001为壹仟零壹)
	 * @param groupValue 组内数值(0~9999)
	 * @param chinaNum   数字字符表
	 * @param realUOM    单位字符表(取仟、佰、拾)
	 * @return
	 */
	private static String fourDigitsToChina(int groupValue, String[] chinaNum, String[] realUOM) {
		int[] digitAry = { groupValue / 1000, groupValue / 100 % 10, groupValue / 10 % 10, groupValue % 10 };
		// 组内单位:仟、佰、拾、个
		String[] units = { realUOM[2], realUOM[1], realUOM[0], "" };
		StringBuilder sb = new StringBuilder();
		boolean zeroPending = false;
		for (int i = 0; i < 4; i++) {
			int digit = digitAry[i];
			if (digit == 0) {
				// 高位出现过非零数字后遇到零，低位可能还有非零数字，挂起待补
				if (sb.length() > 0) {
					zeroPending = true;
				}
			} else {
				if (zeroPending) {
					sb.append("零");
					zeroPending = false;
				}
				sb.append(chinaNum[digit]).append(units[i]);
			}
		}
		return sb.toString();
	}

	/**
	 * @todo 获取组序号对应的组单位，组序号按二进制位组合(万=1组、亿=2组、兆=4组、京=8组)
	 * @param groupIndex 组序号(0为个位组)
	 * @return
	 */
	private static String groupUnit(int groupIndex) {
		if (groupIndex == 0) {
			return "";
		}
		if (groupIndex > 15) {
			throw new IllegalArgumentException("数字超出支持的转换范围(10^64)");
		}
		StringBuilder unit = new StringBuilder();
		for (int bit = 0; (1 << bit) <= groupIndex; bit++) {
			if ((groupIndex & (1 << bit)) != 0) {
				unit.append(GROUP_UNIT_BITS[bit]);
			}
		}
		return unit.toString();
	}

	/**
	 * @todo 判断字符串是整数
	 * @param obj
	 * @return
	 */
	public static boolean isInteger(String obj) {
		return StringUtil.matches(obj, INTEGER_REGEX);
	}

	/**
	 * @todo 判断字符串是否为数字
	 * @param numberStr
	 * @return
	 */
	public static boolean isNumber(String numberStr) {
		return StringUtil.matches(numberStr, NUMBER_REGEX);
	}

	public static int getRandomNum(int max) {
		return getRandomNum(0, max);
	}

	public static int getRandomNum(int start, int end) {
		if (start >= end) {
			throw new IllegalArgumentException("start必须小于end");
		}
		// 生成 [start, end) 区间的随机 int
		return start + SECURE_RANDOM.nextInt(end - start);
	}

	/**
	 * @todo 产生随机数数组
	 * @param maxValue 随机数的最大值
	 * @param size     随机数的个数
	 * @return
	 */
	public static Object[] randomArray(int maxValue, int size) {
		int realSize = size;
		if (realSize > maxValue) {
			realSize = maxValue;
		}
		// 长度等于最大值，返回打乱后的全量数据
		if (realSize == maxValue) {
			List<Integer> result = new ArrayList<>(maxValue);
			for (int i = 0; i < maxValue; i++) {
				result.add(i);
			}
			Collections.shuffle(result, SECURE_RANDOM);
			return result.toArray();
		}
		Set<Integer> resultSet = new HashSet<Integer>(realSize);
		while (resultSet.size() < realSize) {
			resultSet.add(SECURE_RANDOM.nextInt(maxValue));
		}
		return resultSet.toArray();
	}

	/**
	 * @按照概率获取对应概率的数据索引，如：A：概率80%，B：10%，C：6%，D：4%，将出现概率放入数组， 按随机规则返回对应概率的索引
	 * @param probabilities
	 * @return
	 */
	public static int getProbabilityIndex(int[] probabilities) {
		int total = 0;
		for (int probabilitiy : probabilities) {
			total = total + probabilitiy;
		}
		int randomData = SECURE_RANDOM.nextInt(total) + 1;
		int base = 0;
		for (int i = 0; i < probabilities.length; i++) {
			if (randomData > base && randomData <= base + probabilities[i]) {
				return i;
			}
			base = base + probabilities[i];
		}
		return 0;
	}

	/****************** 数字金额转换为英文格式 Begin ********************************/
	/**
	 * @TODO 将数字转换为英文描述
	 * @param value
	 * @return
	 */
	public static String convertToEnglishMoney(BigDecimal value) {
		if (null == value) {
			return "";
		}
		// 用toPlainString而非toString:负scale的BigDecimal(如1E33或大整数stripTrailingZeros后)
		// toString会产生科学计数法导致后续按位解析抛异常
		String str = value.toPlainString();
		int dotIndex = str.indexOf(".");
		if (dotIndex != -1 && str.length() > dotIndex + 3) {
			str = str.substring(0, dotIndex + 3);
		}
		return convertToEnglishMoney(str);
	}

	/**
	 * @TODO 将数字转换为英文描述
	 * @param value
	 * @return
	 */
	public static String convertToEnglishMoney(String value) {
		if (value == null) {
			return null;
		}
		// 是否负数
		boolean isMinus = false;
		if (value.startsWith("-")) {
			isMinus = true;
			value = value.substring(1);
		}
		// 是否有千分位
		boolean hasPermil = value.contains(",");
		// 剔除千分位
		if (hasPermil) {
			value = value.replace(",", "");
		}
		int z = value.indexOf("."); // 取小数点位置
		String lstr, rstr = "";
		if (z > -1) { // 看是否有小数，如果有，则分别取左边和右边
			lstr = value.substring(0, z);
			rstr = value.substring(z + 1);
		} else { // 否则就是全部
			lstr = value;
		}

		String lstrrev = reverse(lstr); // 对左边的字串取反

		switch (lstrrev.length() % 3) {
		case 1:
			lstrrev += "00";
			break;
		case 2:
			lstrrev += "0";
			break;
		default:
			;
		}
		// 按补位后叁位一组的实际组数分配容量，避免超出预设组数时数组越界
		String[] a = new String[lstrrev.length() / 3]; // 定义字串变量来存放解析出来的叁位一组的字串
		StringBuilder lm = new StringBuilder(); // 用来存放转换後的整数部分
		int loopEnd = lstrrev.length() / 3;
		// 与parseMore尺度词表对齐(最大DECILLION=10^33,12组),13组及以上给出明确错误而非parseMore数组越界
		if (loopEnd > 12) {
			throw new IllegalArgumentException("数字超出支持的转换范围(10^36)");
		}
		for (int i = 0; i < loopEnd; i++) {
			a[i] = reverse(lstrrev.substring(3 * i, 3 * i + 3)); // 截取第一个叁位
			if (!"000".equals(a[i])) { // 用来避免这种情况：1000000 = one million thousand only
				if (i != 0) {
					// thousand、million、billion
					if (hasPermil && lm.length() > 0) {
						lm.insert(0, transThree(a[i]) + " " + parseMore(String.valueOf(i)) + ",");
					} else {
						lm.insert(0, transThree(a[i]) + " " + parseMore(String.valueOf(i)) + " ");
					}
				} else {
					lm = new StringBuilder(transThree(a[i])); // 防止i=0时， 在多加两个空格.
				}
			} else {
				lm.append(transThree(a[i]));
			}
		}

		String xs = ""; // 用来存放转换後小数部分
		if ((z > -1) && !rstr.isEmpty() && (BigDecimal.ZERO.compareTo(new BigDecimal(rstr)) == -1)) {
			// 分位按两位小数解读:单位小数处于"角"位(如0.5=50分),右补零;左补零会被当成5分误读
			if (rstr.length() == 1) {
				rstr = rstr.concat("0");
			}
			xs = " AND CENTS " + transTwo(rstr); // 小数部分存在时转换小数
		}
		String intPart = lm.toString().trim();
		// 整数部分为空表示金额为零
		if (intPart.isEmpty()) {
			intPart = "ZERO";
		}
		return (isMinus ? "MINUS " : "") + intPart + xs + " ONLY";
	}

	// 票据标准币种单位词(与ISO-4217代码一一对应,RMB为惯用别名),供带币种参数的convertToEnglishMoney使用
	private final static String[] ISO_CURRENCY_CODES = { "USD", "AUD", "CAD", "HKD", "SGD", "NZD", "GBP", "EUR",
			"JPY", "CHF", "CNY", "RMB" };
	private final static String[] ISO_CURRENCY_WORDS = { "US DOLLARS", "AUSTRALIAN DOLLARS", "CANADIAN DOLLARS",
			"HONG KONG DOLLARS", "SINGAPORE DOLLARS", "NEW ZEALAND DOLLARS", "BRITISH POUNDS STERLING", "EUROS",
			"JAPANESE YEN", "SWISS FRANCS", "CHINESE YUAN", "CHINESE YUAN" };

	/**
	 * @todo 输出票据标准格式的英文金额(SAY+币种单位词开头),如value=1234.56、currency=USD输出:
	 *       "SAY US DOLLARS ONE THOUSAND TWO HUNDRED AND THIRTY-FOUR AND CENTS FIFTY-SIX ONLY";
	 *       currency支持ISO-4217代码(如USD、AUD、JPY、RMB,自动映射票据标准单位词),也可直接传单位词
	 *       (如"POUNDS STERLING");为null或空时输出不带币种的既有格式;单位词统一复数,输出可被englishMoneyToNum还原
	 * @param value    金额数值
	 * @param currency 币种代码或单位词
	 * @return
	 */
	public static String convertToEnglishMoney(BigDecimal value, String currency) {
		return withCurrencyWords(convertToEnglishMoney(value), currency);
	}

	/**
	 * @todo 输出票据标准格式的英文金额,参见convertToEnglishMoney(BigDecimal,String)
	 * @param value
	 * @param currency
	 * @return
	 */
	public static String convertToEnglishMoney(String value, String currency) {
		return withCurrencyWords(convertToEnglishMoney(value), currency);
	}

	/**
	 * @todo 为英文金额描述加注SAY和币种单位词前缀,形成票据标准格式
	 * @param money    既有的英文金额描述
	 * @param currency 币种代码或单位词
	 * @return
	 */
	private static String withCurrencyWords(String money, String currency) {
		if (StringUtil.isBlank(currency) || StringUtil.isBlank(money)) {
			return money;
		}
		String unit = currency.trim().toUpperCase();
		for (int i = 0; i < ISO_CURRENCY_CODES.length; i++) {
			if (ISO_CURRENCY_CODES[i].equals(unit)) {
				unit = ISO_CURRENCY_WORDS[i];
				break;
			}
		}
		return "SAY ".concat(unit).concat(" ").concat(money);
	}

	/**
	 * @todo 将英文金额描述转换成数字,支持convertToEnglishMoney输出的完整形式 (如"MINUS ONE THOUSAND AND
	 *       CENTS FIFTY ONLY"),也支持普通英文数字(如"one thousand and thirty-four")和带货币单位词的
	 *       金额描述(如"one thousand two hundred thirty-four dollars and fifty-six cents"、"FIVE EUROS"),
	 *       货币单位覆盖美元/日元/英镑/欧元/澳元/加拿大元/港币/人民币等主要货币的英文全称、国别修饰词
	 *       (如AUSTRALIAN、CANADIAN、JAPANESE)及ISO-4217代码(如USD、JPY、AUD、CAD、HKD);
	 *       同时兼容银行票据标准写法:SAY/SAY TOTAL抬头、AND NO CENTS、分数式分币(如"AND 56/100 DOLLARS")
	 * @param englishMoney
	 * @return 无法识别时返回null
	 */
	public static BigDecimal englishMoneyToNum(String englishMoney) {
		if (StringUtil.isBlank(englishMoney)) {
			return null;
		}
		String[] unitWords = { "ONE", "TWO", "THREE", "FOUR", "FIVE", "SIX", "SEVEN", "EIGHT", "NINE" };
		String[] teenWords = { "TEN", "ELEVEN", "TWELVE", "THIRTEEN", "FOURTEEN", "FIFTEEN", "SIXTEEN", "SEVENTEEN",
				"EIGHTEEN", "NINETEEN" };
		String[] tenWords = { "TWENTY", "THIRTY", "FORTY", "FIFTY", "SIXTY", "SEVENTY", "EIGHTY", "NINETY" };
		String[] scaleWords = { "THOUSAND", "MILLION", "BILLION", "TRILLION", "QUADRILLION", "QUINTILLION",
				"SEXTILLION", "SEPTILLION", "OCTILLION", "NONILLION", "DECILLION" };
		// 货币单位词:主币单位、国别地区修饰词及ISO-4217代码,解析时作为结算边界,不参与数值计算
		String[] currencyWords = {
				// 美元/澳元/加拿大元/港币/新西兰元/新加坡元等dollar系
				"DOLLAR", "DOLLARS", "US", "USD", "AMERICAN", "AUSTRALIAN", "AUSTRALIA", "AUD", "CANADIAN", "CANADA",
				"CAD", "HONG", "KONG", "HKD", "NEW", "ZEALAND", "NZD", "SINGAPORE", "SGD",
				// 英镑/欧元
				"POUND", "POUNDS", "BRITISH", "GBP", "STERLING", "EURO", "EUROS", "EUR",
				// 日元/人民币
				"YEN", "JAPANESE", "JAPAN", "JPY", "YUAN", "CHINESE", "CHINA", "CNY", "RMB",
				// 瑞士法郎/卢比
				"FRANC", "FRANCS", "SWISS", "CHF", "RUPEE", "RUPEES", "INDIAN", "INR" };
		// 归一化:统一大写,逗号和连字符转空白(如TWENTY-FIVE拆成两个词),斜杠周围空白剔除(如"56 / 100"归一为"56/100"),
		// 各类空白(含制表换行、全角空格、不间断空格等非标准空白)统一压缩为单空格,容忍复制粘贴产生的不规范空格
		String[] tokens = englishMoney.trim().toUpperCase().replace(",", " ").replace("-", " ")
				.replaceAll("\\s*/\\s*", "/").replaceAll("[\\s\\u00A0\\u202F\\u3000]+", " ").trim().split("\\s+");
		boolean negative = false;
		// CENTS之后的数值属于分币部分,单独累计后按百分位合并
		boolean centsPart = false;
		// 上一个有效词是否为紧邻的数字词(用于"FIFTY CENTS"后置式分币归属判定)
		boolean lastTokenWasNumber = false;
		BigDecimal total = BigDecimal.ZERO;
		int current = 0;
		int cents = 0;
		for (String token : tokens) {
			if (token.equals("MINUS") || token.equals("NEGATIVE")) {
				negative = true;
				lastTokenWasNumber = false;
				continue;
			}
			// 连接词、收尾词、票据抬头词(SAY/TOTAL)与零值、无分币(NO)表述
			if (token.equals("AND") || token.equals("ONLY") || token.equals("ZERO") || token.equals("SAY")
					|| token.equals("TOTAL") || token.equals("NO")) {
				lastTokenWasNumber = false;
				continue;
			}
			// 美式支票标准的分币分数写法(如"AND 56/100"):分母为100,分子即分币数
			if (token.matches("\\d+/100")) {
				cents += Integer.parseInt(token.substring(0, token.indexOf('/')));
				lastTokenWasNumber = false;
				continue;
			}
			// 货币单位词忽略(如dollars、euros、yen),不参与数值计算
			boolean isCurrency = false;
			for (int i = 0; i < currencyWords.length; i++) {
				if (token.equals(currencyWords[i])) {
					isCurrency = true;
					break;
				}
			}
			if (isCurrency) {
				// 主币单位词是结算边界:其前面的数值构成完整的元金额(如"two dollars fifty cents"中的2),
				// 先结算进总额,避免与后面的分币数值(如fifty)累加混淆
				total = total.add(BigDecimal.valueOf(current));
				current = 0;
				lastTokenWasNumber = false;
				continue;
			}
			// 分币单位词:兼容前置("AND CENTS FIFTY")和后置("FIFTY CENTS")两种顺序,
			// 后置时紧邻单位词且尚未结算的数值归属分币(如"two dollars fifty cents"中的50);
			// 前置时单位词与数字间有连接词(如"THIRTY-FOUR AND CENTS FIFTY"),current保留在整数部分
			if (token.equals("CENT") || token.equals("CENTS") || token.equals("PENNY") || token.equals("PENCE")) {
				if (lastTokenWasNumber && current > 0) {
					cents += current;
					current = 0;
				}
				centsPart = true;
				lastTokenWasNumber = false;
				continue;
			}
			int value = -1;
			for (int i = 0; i < unitWords.length && value == -1; i++) {
				if (token.equals(unitWords[i])) {
					value = i + 1;
				}
			}
			for (int i = 0; i < teenWords.length && value == -1; i++) {
				if (token.equals(teenWords[i])) {
					value = i + 10;
				}
			}
			for (int i = 0; i < tenWords.length && value == -1; i++) {
				if (token.equals(tenWords[i])) {
					value = (i + 2) * 10;
				}
			}
			if (value > 0) {
				if (centsPart) {
					cents += value;
				} else {
					current += value;
				}
				lastTokenWasNumber = true;
				continue;
			}
			if (token.equals("HUNDRED")) {
				if (centsPart) {
					cents = Math.max(cents, 1) * 100;
				} else {
					current = Math.max(current, 1) * 100;
				}
				lastTokenWasNumber = true;
				continue;
			}
			boolean isScale = false;
			for (int i = 0; i < scaleWords.length; i++) {
				if (token.equals(scaleWords[i])) {
					// 尺度词前缺数字时按壹计(如"THOUSAND FIVE"按一千零五处理)
					total = total.add(
							BigDecimal.valueOf(current == 0 ? 1 : current).multiply(BigDecimal.TEN.pow((i + 1) * 3)));
					current = 0;
					isScale = true;
					lastTokenWasNumber = false;
					break;
				}
			}
			if (!isScale) {
				logger.warn("英文金额:{} 存在无法识别的单词:{},返回null!", englishMoney, token);
				return null;
			}
		}
		// 无分币时保持整数结果(scale=0),与capitalMoneyToNum无角分返回整数值的行为一致
		BigDecimal result = total.add(BigDecimal.valueOf(current));
		if (cents > 0) {
			result = result.add(BigDecimal.valueOf(cents, 2));
		}
		return negative ? result.negate() : result;
	}

	private static String parseFirst(String s) {
		String[] a = new String[] { "", "ONE", "TWO", "THREE", "FOUR", "FIVE", "SIX", "SEVEN", "EIGHT", "NINE" };
		return a[Integer.parseInt(s.substring(s.length() - 1))];
	}

	private static String parseTeen(String s) {
		String[] a = new String[] { "TEN", "ELEVEN", "TWELVE", "THIRTEEN", "FOURTEEN", "FIFTEEN", "SIXTEEN",
				"SEVENTEEN", "EIGHTEEN", "NINETEEN" };
		return a[Integer.parseInt(s) - 10];
	}

	private static String parseTen(String s) {
		String[] a = new String[] { "TEN", "TWENTY", "THIRTY", "FORTY", "FIFTY", "SIXTY", "SEVENTY", "EIGHTY",
				"NINETY" };
		return a[Integer.parseInt(s.substring(0, 1)) - 1];
	}

	// 两位
	private static String transTwo(String s) {
		String value = "";
		// 判断位数
		if (s.length() > 2) {
			s = s.substring(0, 2);
		} else if (s.length() < 2) {
			s = "0" + s;
		}
		if (s.startsWith("0")) // 07 - seven 是否小於10
		{
			value = parseFirst(s);
		} else if (s.startsWith("1")) // 17 seventeen 是否在10和20之间
		{
			value = parseTeen(s);
		} else if (s.endsWith("0")) // 是否在10与100之间的能被10整除的数
		{
			value = parseTen(s);
		} else {
			value = parseTen(s) + "-" + parseFirst(s);
		}
		return value;
	}

	private static String parseMore(String s) {
		String[] unitAry = new String[] { "", "THOUSAND", "MILLION", "BILLION", "TRILLION", "QUADRILLION",
				"QUINTILLION", "SEXTILLION", "SEPTILLION", "OCTILLION", "NONILLION", "DECILLION" };
		return unitAry[Integer.parseInt(s)];
	}

	// 制作叁位的数
	// s.length = 3
	private static String transThree(String s) {
		String value = "";
		if (s.startsWith("0")) // 是否小於100
		{
			value = transTwo(s.substring(1));
		} else if ("00".equals(s.substring(1))) // 是否被100整除
		{
			value = parseFirst(s.substring(0, 1)) + " HUNDRED";
		} else {
			value = parseFirst(s.substring(0, 1)) + " HUNDRED AND " + transTwo(s.substring(1));
		}
		return value;
	}

	private static String reverse(String s) {
		return new StringBuilder(s).reverse().toString();
	}

	/**
	 * 将字符串解析成RoundingMode
	 *
	 * @param roundingModeStr
	 * @return
	 */
	public static RoundingMode parseRoundingMode(String roundingModeStr) {
		// null表示未配置(对应FormatModel不设置舍入模式);空串或无法识别的值统一返回HALF_UP
		if (roundingModeStr == null) {
			return null;
		}
		String roundingStr = roundingModeStr.trim().toUpperCase();
		if (roundingStr.equals("UP")) {
			return RoundingMode.UP;
		} else if (roundingStr.equals("DOWN")) {
			return RoundingMode.DOWN;
		} else if (roundingStr.equals("FLOOR")) {
			return RoundingMode.FLOOR;
		} else if (roundingStr.equals("HALF_UP")) {
			return RoundingMode.HALF_UP;
		} else if (roundingStr.equals("HALF_DOWN")) {
			return RoundingMode.HALF_DOWN;
		} else if (roundingStr.equals("HALF_EVEN")) {
			return RoundingMode.HALF_EVEN;
		} else if (roundingStr.equals("CEILING")) {
			return RoundingMode.CEILING;
		}
		return RoundingMode.HALF_UP;
	}

	/****************** 数字金额转换为英文格式 End ********************************/
}
