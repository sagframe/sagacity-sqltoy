package org.sagacity.sqltoy.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

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

	protected final BigDecimal ONE_BIGDECIMAL = new BigDecimal(1);

	// 最大到京
	private final static String[] moneyUOM = { "拾", "佰", "仟", "万", "拾", "佰", "仟", "亿", "拾", "佰", "仟", "万", "拾", "佰",
			"仟", "兆", "拾", "佰", "仟", "万", "拾", "佰", "仟", "亿", "拾", "佰", "仟", "万", "拾", "佰", "仟", "京" };
	private final static String[] numUOM = { "十", "百", "千", "万", "十", "百", "千", "亿", "十", "百", "千", "万", "十", "百", "千",
			"兆", "十", "百", "千", "万", "十", "百", "千", "亿", "十", "百", "千", "万", "十", "百", "千", "京" };
	private final static String[] capitalMoneyNumber = { "", "壹", "贰", "叁", "肆", "伍", "陆", "柒", "捌", "玖" };
	private final static String[] captialNumber = { "", "一", "二", "三", "四", "五", "六", "七", "八", "九", "十" };

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
			// 数字转换成英文金额
			if (lowPattern.equals(Pattern.CAPITAL_EN) || lowPattern.equals(Pattern.CAPITAL_ENGLISH)) {
				return convertToEnglishMoney(tmp);
			}
			DecimalFormat df = (DecimalFormat) ((locale == null) ? DecimalFormat.getInstance()
					: DecimalFormat.getInstance(locale));
			if (roundingMode != null) {
				df.setRoundingMode(roundingMode);
			}
			df.applyPattern(pattern);
			return df.format(tmp);
		} catch (Exception e) {
			e.printStackTrace();
			logger.error("value:" + target + ";pattern=" + pattern + e.getMessage());
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
			DecimalFormat df = (DecimalFormat) ((locale == null) ? DecimalFormat.getCurrencyInstance()
					: DecimalFormat.getCurrencyInstance(locale));
			df.applyPattern(pattern);
			return df.format(tmp);
		} catch (Exception e) {
			e.printStackTrace();
			logger.error(e.getMessage());
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
			e.printStackTrace();
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
			return new BigDecimal(number.doubleValue());
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
	 * @todo 将大写中文金额字符串转换成数字(最大支持到千万亿)
	 * @param capitalMoney
	 * @return
	 */
	public static BigDecimal capitalMoneyToNum(String capitalMoney) {
		capitalMoney = capitalMoney.replaceAll("\\s+", "").replace("零", "").replace("圆", "元");
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
		int billionIndex = capitalMoney.lastIndexOf("亿");
		// [0]亿元、[1]万元、[2]角币
		String[] splitsCapitalMoney = { "0", "0", "0" };
		// 是否包含亿元
		if (billionIndex != -1) {
			splitsCapitalMoney[0] = capitalMoney.substring(0, billionIndex);
			splitsCapitalMoney[1] = capitalMoney.substring(billionIndex + 1, capitalMoney.indexOf("元"));
		} else if (capitalMoney.indexOf("元") != -1) {
			splitsCapitalMoney[1] = capitalMoney.substring(0, capitalMoney.indexOf("元"));
		}
		if (capitalMoney.indexOf("元") != capitalMoney.length() - 1) {
			splitsCapitalMoney[2] = capitalMoney.substring(capitalMoney.indexOf("元") + 1);
		}
		// 分段处理合并
		BigDecimal result = parseMillMoney(splitsCapitalMoney[0]).multiply(new BigDecimal("100000000"))
				.add(parseMillMoney(splitsCapitalMoney[1])).add(parseLowThousandMoney(splitsCapitalMoney[2]));
		if (capitalMoney.indexOf("负") == 0) {
			return new BigDecimal(0).subtract(result).setScale(scale, RoundingMode.HALF_UP);
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
		if (realMoney.compareTo(new BigDecimal(0)) == 0) {
			return "零元";
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
		String result = numberToChina(intPartStr, true);
		// 处理以"壹拾"开头的结果统一替换成"拾"
		if (result.startsWith("壹拾")) {
			result = result.substring(1);
		}
		if (!"".equals(result)) {
			result += "元";
		}

		// 小于零
		if (money.compareTo(new BigDecimal("0")) < 0) {
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
	 * @todo 求数组中数据的最大值
	 * @param bigArray
	 * @return
	 */
	public static BigDecimal getMax(BigDecimal[] bigArray) {
		BigDecimal max = bigArray[0];
		for (int i = 0; i < bigArray.length; i++) {
			if (max.compareTo(bigArray[i]) < 0) {
				max = bigArray[i];
			}
		}
		return max;
	}

	/**
	 * @todo 求数组中数据的最小值
	 * @param bigArray
	 * @return
	 */
	public static BigDecimal getMin(BigDecimal[] bigArray) {
		BigDecimal min = bigArray[0];
		for (int i = 0; i < bigArray.length; i++) {
			if (min.compareTo(bigArray[i]) > 0) {
				min = bigArray[i];
			}
		}
		return min;
	}

	/**
	 * @todo 求数组中数据的平均值
	 * @param bigArray
	 * @return
	 */
	public static BigDecimal getAverage(BigDecimal[] bigArray) {
		BigDecimal sum = BigDecimal.ZERO;
		if (bigArray == null || bigArray.length == 0) {
			return sum;
		}
		for (int i = 0; i < bigArray.length; i++) {
			sum = sum.add(bigArray[i]);
		}
		return sum.divide(new BigDecimal(bigArray.length));
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
			sum = sum.add(bigArray[i]);
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
			return nf.parse(parseTarget.replace(",", ""));
		} catch (ParseException e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * @todo 处理大于一万的字符金额
	 * @param capitalMoneyStr
	 * @return
	 */
	private static BigDecimal parseMillMoney(String capitalMoneyStr) {
		if ("".equals(capitalMoneyStr) || "0".equals(capitalMoneyStr)) {
			return BigDecimal.ZERO;
		}
		String millStr = "0";
		String lowthousand = "0";
		int millIndex = capitalMoneyStr.indexOf("万");
		if (millIndex != -1) {
			millStr = capitalMoneyStr.substring(0, millIndex);
			lowthousand = (millIndex != capitalMoneyStr.length() - 1) ? capitalMoneyStr.substring(millIndex + 1) : "0";
		} else {
			lowthousand = capitalMoneyStr;
		}
		return parseLowThousandMoney(millStr).multiply(new BigDecimal("10000")).add(parseLowThousandMoney(lowthousand));
	}

	/**
	 * @todo 将多位阿拉伯数字转换成中文
	 * @param sourceInt
	 * @param isMoney
	 * @return
	 */
	private static String numberToChina(String sourceInt, boolean isMoney) {
		if ("0".equals(sourceInt)) {
			return "";
		}
		String[] chinaNum = (isMoney ? capitalMoneyNumber : captialNumber);
		String[] realUOM = (isMoney ? moneyUOM : numUOM);
		int temp;
		int length = sourceInt.length();
		StringBuilder targetStr = new StringBuilder("");
		String firstChar;
		for (int i = 0; i < length; i++) {
			if (targetStr.length() > 0) {
				firstChar = String.valueOf(targetStr.charAt(0));
			} else {
				firstChar = "零";
			}
			// 从低位处理
			temp = Integer.parseInt(sourceInt.substring(length - i - 1, length - i));
			if (temp == 0) {
				if (i > 0 && i % 4 == 0) {
					// 4位全是零，剔除掉单位
					if ("万亿兆京".indexOf(firstChar) != -1) {
						targetStr.delete(0, 1);
					}
					targetStr.insert(0, numUOM[i - 1]);
				} else if ("零万亿兆京".indexOf(firstChar) == -1) {
					targetStr.insert(0, "零");
				}
			} else {
				// 4位全是零，剔除掉单位
				if ((i > 0 && i % 4 == 0) && ("万亿兆京".indexOf(firstChar) != -1)) {
					targetStr.delete(0, 1);
				}
				targetStr.insert(0, chinaNum[temp] + ((i > 0) ? realUOM[i - 1] : ""));
			}
		}
		return targetStr.toString();
	}

	/**
	 * @todo 处理一万以内的金额
	 * @param capitalMoneyStr
	 * @return
	 */
	private static BigDecimal parseLowThousandMoney(String capitalMoneyStr) {
		if ("0".equals(capitalMoneyStr)) {
			return BigDecimal.ZERO;
		}
		String lastStr = capitalMoneyStr.substring(capitalMoneyStr.length() - 1);
		int lastAscii = StringUtil.str2ASCII(lastStr)[0];
		String[] uoms = { "仟", "佰", "拾", "角", "分", "厘" };
		double[] multiples = { 1000, 100, 10, 0.1, 0.01, 0.001 };
		BigDecimal moneyNum = BigDecimal.ZERO;
		int index;
		double splitMoneyNum;
		for (int i = 0; i < uoms.length; i++) {
			index = capitalMoneyStr.indexOf(uoms[i]);
			if (index != -1) {
				splitMoneyNum = Double.parseDouble(capitalMoneyStr.substring(index - 1, index)) * multiples[i];
				moneyNum = moneyNum.add(new BigDecimal(splitMoneyNum));
			}
		}
		if (lastAscii >= 49 && lastAscii <= 57) {
			moneyNum = moneyNum.add(new BigDecimal(lastStr));
		}
		return moneyNum;
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
		long value = Math.abs(new SecureRandom().nextLong()) % (end - start);
		return Long.valueOf(value + start).intValue();
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
		// 长度等于最大值
		if (realSize == maxValue) {
			Object[] result = new Object[maxValue];
			for (int i = 0; i < maxValue; i++) {
				result[i] = i;
			}
			return result;
		}
		Set<Integer> resultSet = new HashSet<Integer>(realSize);
		int randomNum;
		while (resultSet.size() < realSize) {
			randomNum = (int) (Math.random() * maxValue);
			resultSet.add(randomNum);
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
		int randomData = (int) (Math.random() * total) + 1;
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
		String str = value.toString();
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
		String[] a = new String[5]; // 定义5个字串变量来存放解析出来的叁位一组的字串

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
		StringBuilder lm = new StringBuilder(); // 用来存放转换後的整数部分
		int loopEnd = lstrrev.length() / 3;
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
		if ((z > -1) && (BigDecimal.ZERO.compareTo(new BigDecimal(rstr)) == -1)) {
			xs = " AND CENTS " + transTwo(rstr); // 小数部分存在时转换小数 xs = "AND CENTS " + transTwo(rstr) + " ";
		} else {
			xs = " AND CENTS";
		}
		return (isMinus ? "MINUS " : "") + lm.toString().trim() + xs + " ONLY";
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
		String[] a = new String[] { "", "THOUSAND", "MILLION", "BILLION", "TRILLION", "QUADRILLION" };
		return a[Integer.parseInt(s)];
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
		char[] aChr = s.toCharArray();
		StringBuffer tmp = new StringBuffer();
		for (int i = aChr.length - 1; i >= 0; i--) {
			tmp.append(aChr[i]);
		}
		return tmp.toString();
	}

	/****************** 数字金额转换为英文格式 End ********************************/
}
