/**
 * @Copyright 2008 版权归陈仁飞，不要肆意侵权抄袭，如引用请注明出处保留作者信息。
 */
package org.sagacity.sqltoy.utils;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.Locale;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 
 * @project sagacity-sqltoy4.0
 * @description 日期处理支持类，提供日常工作中的所有日期的操作处理
 * @author chenrenfei $<a href="mailto:zhongxuchen@hotmail.com">联系作者</a>$
 * @version $id:DateUtil.java,Revision:v1.0,Date:2008-12-14 下午08:01:19 $
 * @Modification $Date:2012-8-27 {对日期的格式化增加了locale功能}
 * @Modification $Date:2015-8-8
 *               {针对parseString功能进行了优化,可以对英文的日期和中文日期进行解析转化为日期格式,同时优化了格式判断的逻辑
 *               ,避免不停的用正则表达式进行格式判断}
 */
@SuppressWarnings({ "unused" })
public class DateUtil {
	/**
	 * 定义日志
	 */
	private final static Logger logger = LogManager.getLogger(DateUtil.class);

	private static final String[] CHINA_DATE_KEYS = { "○", "О", "0", "Ο", "O", "零", "一", "二", "三", "四", "五", "六", "七",
			"八", "九", "十", "年", "月", "日", "时", "分", "秒" };
	private static final String[] CHINA_DATE_KEY_MAP = { "0", "0", "0", "0", "0", "0", "1", "2", "3", "4", "5", "6",
			"7", "8", "9", "10", "-", "-", " ", ":", ":", " " };

	/**
	 * 英文月份名称
	 */
	private static final String[] MONTH_ENGLISH_NAME = { "January", "February", "March", "April", "May", "June", "July",
			"August", "September", "October", "November", "December" };

	/**
	 * 中文星期的名称
	 */
	private static final String[] WEEK_CHINA_NAME = { "星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日" };

	/**
	 * 英文日期的几种格式
	 */
	private static final Collection<String> DEFAULT_PATTERNS = Arrays
			.asList(new String[] { "EEE MMM d HH:mm:ss yyyy", "EEE MMM dd HH:mm:ss Z yyyy", "EEE MMM dd Z yyyy",
					"EEE MMM dd yyyy", "EEEE, dd-MMM-yy HH:mm:ss zzz", "EEE, dd MMM yyyy HH:mm:ss zzz" });

	/**
	 * 定义日期的格式
	 */
	public final static class FORMAT {
		public final static String YEAR_MONTH = "yyyy-MM";

		/**
		 * 6位日期格式
		 */
		public final static String DATE_6CHAR = "yyMMdd";

		/**
		 * 8位日期格式
		 */
		public final static String DATE_8CHAR = "yyyyMMdd";

		/**
		 * 点号日期格式
		 */
		public final static String DATE_DOT = "yyyy.MM.dd";

		/**
		 * 反斜杠日期格式
		 */
		public final static String DATE_SLASH = "yyyy/MM/dd";

		/**
		 * 横杠日期格式
		 */
		public final static String DATE_HORIZONTAL = "yyyy-MM-dd";

		/**
		 * 日期时间(日期点号分割)
		 */
		public final static String DATATIME_DOT = "yyyy.MM.dd HH:mm:ss";

		/**
		 * 日期时间(日期反斜杠)
		 */
		public final static String DATETIME_SLASH = "yyyy/MM/dd HH:mm:ss";

		/**
		 * 日期时间(日期横杠)
		 */
		public final static String DATETIME_HORIZONTAL = "yyyy-MM-dd HH:mm:ss";
	}

	/**
	 * @todo 将日期字符串或时间转换成时间类型 日期字符串中的日期分隔符可是:"/",".","-"， 返回时间具体到秒 只提供常用的日期格式处理
	 * @param str
	 * @return Date
	 */
	public static Date parse(Object data, String format) {
		return parse(data, format, null);
	}

	public static Date parse(Object data, String format, String local) {
		if (null == data) {
			logger.error("The date string is null!");
			return null;
		}
		if (data instanceof String) {
			String dateString = data.toString().trim();
			if ("".equals(dateString) || "null".equals(dateString.toLowerCase())) {
				logger.error("The date string is null!");
				return null;
			}
			return parseString(dateString, format, local);
		} else {
			String result = formatDate(data, format, local);
			return parseString(result, format, local);
		}
	}

	public static Date parseString(String dateStr) {
		return parseString(dateStr, null, null);
	}

	/**
	 * @todo 将日期字符串或时间转换成时间类型 日期字符串中的日期分隔符可是:"/",".","-"， 返回时间具体到秒 只提供常用的日期格式处理
	 * @param dateStr
	 * @param dateFormat
	 * @param locale
	 * @return
	 */
	public static Date parseString(String dateStr, String dateFormat, String locale) {
		if (dateStr == null)
			return null;
		dateStr = dateStr.trim();
		if ("".equals(dateStr))
			return null;
		String realDF = null;
		if (StringUtil.isNotBlank(dateFormat)) {
			realDF = dateFormat;
		} // 英文日期格式
		else if (StringUtil.matches(dateStr, "[a-zA-Z]")) {
			SimpleDateFormat dateParser = null;
			Iterator<String> formatIter = DEFAULT_PATTERNS.iterator();
			Date result = null;
			while (formatIter.hasNext()) {
				String format = (String) formatIter.next();
				if (dateParser == null) {
					dateParser = new SimpleDateFormat(format, Locale.US);
				} else {
					dateParser.applyPattern(format);
				}
				try {
					result = dateParser.parse(dateStr);
					if (result != null)
						break;
				} catch (ParseException pe) {
				}
			}
			return result;
		} else {
			// 中文日期格式
			if (StringUtil.matches(dateStr, "[年月日时分秒]"))
				dateStr = parseChinaDate(dateStr);
			// 含中文，但非标准的时间性中文
			else if (StringUtil.hasChinese(dateStr)) {
				return null;
			}
			int size;
			boolean hasBlank = (dateStr.indexOf(" ") != -1);
			int splitCount;
			int startIndex;
			// 日期和时间的组合
			if (hasBlank) {
				// 统一格式(去除掉日期中的符号变成全数字)
				dateStr = dateStr.replaceFirst("\\s+", " ").replace("-", "").replace(".", "").replace(":", "")
						.replace("/", "");
				int preSize = dateStr.indexOf(" ");
				size = dateStr.length();
				if (size > 16) {
					realDF = "yyyyMMdd HHmmssSSS";
				} else if (size == 16) {
					if (preSize == 8)
						realDF = "yyyyMMdd HHmmssS";
					else
						realDF = "yyMMdd HHmmssSSS";
				} else if (size == 13) {
					if (preSize == 8)
						realDF = "yyyyMMdd HHmm";
					else
						realDF = "yyMMdd HHmmss";
				} else if (size == 11) {
					if (preSize == 8)
						realDF = "yyyyMMdd HH";
					else
						realDF = "yyMMdd HHmm";
				} else if (size == 9)
					realDF = "yyMMdd HH";
				else
					realDF = "yyyyMMdd HHmmss";
			} else {
				// 去除数字中带的,例如:201,512
				dateStr = dateStr.replace(",", "");
				size = dateStr.length();
				if (dateStr.indexOf(":") != -1) {
					if (dateStr.indexOf(".") != -1) {
						realDF = "HH:mm:ss.SSS";
					} else {
						if (size == 5)
							realDF = "HH:mm";
						else
							realDF = "HH:mm:ss";
					}
				} else {
					dateStr = dateStr.replace("-", "/").replace(".", "/");
					splitCount = StringUtil.matchCnt(dateStr, "\\/");
					if (splitCount == 2) {
						startIndex = dateStr.indexOf("/");
						if (startIndex == 2)
							realDF = "yy/MM/dd";
						else
							realDF = "yyyy/MM/dd";
					} else if (splitCount == 1) {
						if (size > 5)
							realDF = "yyyy/MM";
						else
							realDF = "yy/MM";
					} else {
						if (size >= 15)
							realDF = "yyyyMMddHHmmssSSS";
						else if (size == 14)
							realDF = "yyyyMMddHHmmss";
						else if (size == 12)
							realDF = "yyMMddHHmmss";
						else if (size == 10)
							realDF = "yyyyMMddHH";
						else if (size == 6)
							realDF = "yyyyMM";
						else if (size == 4)
							realDF = "yyyy";
						else
							realDF = "yyyyMMdd";
					}
				}
			}
			if (realDF == null) {
				realDF = hasBlank ? "yyyy-MM-dd HH:mm:ss" : "yyyy-MM-dd";
			}
		}
		DateFormat df = (StringUtil.isBlank(locale)) ? new SimpleDateFormat(realDF)
				: new SimpleDateFormat(realDF, new Locale(locale));
		try {
			return df.parse(dateStr);
		} catch (ParseException e) {
			e.printStackTrace();
		}
		return null;
	}

	public static java.util.Date convertDateObject(Object dt) {
		return convertDateObject(dt, null, null);
	}

	/**
	 * @todo 日期对象类型转换
	 * @param dt
	 * @param format
	 * @param local
	 * @return
	 */
	public static java.util.Date convertDateObject(Object dt, String format, String local) {
		if (dt == null) {
			System.err.println("日期不能为空,请正确输入!");
			return null;
		}
		if (!(dt instanceof String) && !(dt instanceof java.sql.Date) && !(dt instanceof java.util.Date)
				&& !(dt instanceof java.lang.Number))
			throw new IllegalArgumentException(dt + "日期数据必须是String、Date、Long、Integer类型,请正确输入!");
		Date result = null;
		String dtStr = dt.toString();
		if (dt instanceof String) {
			result = parseString(dtStr, format, local);
		} else if (dt instanceof java.util.Date)
			result = new java.util.Date(((java.util.Date) dt).getTime());
		else if (dt instanceof java.sql.Date)
			result = new java.util.Date(((java.sql.Date) dt).getTime());
		else if (dt instanceof java.lang.Number) {
			// 13位表示毫秒数
			if (dtStr.length() != 13)
				result = parseString(dtStr, format, local);
			else
				result = new java.util.Date(((Number) dt).longValue());
		} else
			result = parseString(dtStr, format, local);
		return result;
	}

	/**
	 * @todo 格式化日期
	 * @param dt
	 * @param fmt
	 * @return
	 */
	public static String formatDate(Object dt, String fmt) {
		return formatDate(dt, fmt, null);
	}

	public static String formatDate(Object dt, String fmt, String locale) {
		if (dt == null)
			return null;
		if (fmt.equalsIgnoreCase("YY")) {
			String year = Integer.toString(getYear(dt));
			return year.substring(year.length() - 2);
		} else if (fmt.equalsIgnoreCase("YYYY")) {
			return Integer.toString(getYear(dt));
		} else if (fmt.equalsIgnoreCase("MM")) {
			int month = getMonth(dt);
			return (month < 10 ? "0" : "").concat(Integer.toString(month));
		} else if (fmt.equalsIgnoreCase("dd")) {
			int day = getDay(dt);
			return (day < 10 ? "0" : "").concat(Integer.toString(day));
		}
		DateFormat df = StringUtil.isBlank(locale) ? new SimpleDateFormat(fmt)
				: new SimpleDateFormat(fmt, new Locale(locale));
		Date tmp = convertDateObject(dt, null, locale);
		return (null == tmp) ? null : df.format(tmp);
	}

	public static String formatDate(Object dt, String fmt, String targetFmt, String locale) {
		Date result = parse(dt, fmt, locale);
		return formatDate(result, targetFmt);
	}

	/**
	 * @todo 获取当前以sql.date的日期
	 * @param date
	 * @return
	 */
	public static java.sql.Date getSqlDate(Object date) {
		return new java.sql.Date(convertDateObject(date == null ? new Date() : date).getTime());
	}

	public static java.sql.Timestamp getTimestamp(Object date) {
		return new Timestamp(date == null ? System.currentTimeMillis() : convertDateObject(date).getTime());
	}

	/**
	 * @todo 获取当前操作系统的时间
	 * @return 当前操作系统的时间
	 */
	public static Date getNowTime() {
		return Calendar.getInstance().getTime();
	}

	// Add millisecond
	public static Date addMilliSecond(Object dt, long millisecond) {
		Date result = convertDateObject(dt);
		if (millisecond != 0)
			result.setTime(result.getTime() + millisecond);
		return result;
	}

	public static Date addSecond(Object dt, double second) {
		Double millisecond = new Double(1000.0 * second);
		return addMilliSecond(dt, millisecond.longValue());
	}

	public static Date addDay(Object dt, long day) {
		return addMilliSecond(dt, 1000L * 60L * 60L * 24L * day);
	}

	public static Date addDay(Object dt, double day) {
		Double millisecond = new Double(1000.0 * 60.0 * 60.0 * 24.0 * day);
		return addMilliSecond(dt, millisecond.longValue());
	}

	// add month
	public static Date addMonth(Object dt, int month) {
		GregorianCalendar gc = new GregorianCalendar();
		gc.setTime(convertDateObject(dt));
		gc.add(2, month);
		return gc.getTime();
	}

	public static int getYear(Object dateValue) {
		GregorianCalendar currentDate = new GregorianCalendar();
		if (dateValue != null)
			currentDate.setTime(convertDateObject(dateValue));
		return currentDate.get(Calendar.YEAR);
	}

	public static int getMonth(Object dateValue) {
		GregorianCalendar currentDate = new GregorianCalendar();
		if (dateValue != null)
			currentDate.setTime(convertDateObject(dateValue));
		return currentDate.get(Calendar.MONTH) + 1;
	}

	public static int getDay(Object dateValue) {
		GregorianCalendar currentDate = new GregorianCalendar();
		if (null != dateValue)
			currentDate.setTime(convertDateObject(dateValue));
		return currentDate.get(Calendar.DAY_OF_MONTH);
	}

	/**
	 * @todo 获取两个时间间隔的天数
	 * @param floorDate
	 * @param goalDate
	 * @return
	 */
	public static int getIntervalDays(Object floorDate, Object goalDate) {
		BigDecimal result = new BigDecimal(
				new Double(getIntervalMillSeconds(DateUtil.formatDate(floorDate, FORMAT.DATE_HORIZONTAL),
						DateUtil.formatDate(goalDate, FORMAT.DATE_HORIZONTAL))) / (3600 * 1000 * 24));
		return result.setScale(1, BigDecimal.ROUND_HALF_UP).intValue();
	}

	/**
	 * @todo 获取两时间间隔的毫秒数
	 * @param floorDate
	 * @param goalDate
	 * @return
	 */
	public static long getIntervalMillSeconds(Object floorDate, Object goalDate) {
		return convertDateObject(goalDate).getTime() - convertDateObject(floorDate).getTime();
	}

	/**
	 * @todo 将日期转化为中文格式
	 * @param dateValue
	 * @return
	 */
	public static String format2China(Object dateValue) {
		Date date = convertDateObject(dateValue);
		if (null == date)
			return null;
		GregorianCalendar pointDate = new GregorianCalendar();
		pointDate.setTime(convertDateObject(dateValue));
		String tmpDate;
		StringBuilder result = new StringBuilder();
		if (dateValue instanceof String) {
			tmpDate = (String) dateValue;
			if (tmpDate.length() >= 4)
				result.append(pointDate.get(Calendar.YEAR) + "年");
			if (tmpDate.length() >= 6)
				result.append((pointDate.get(Calendar.MONTH) + 1) + "月");
			if (tmpDate.length() >= 8)
				result.append(pointDate.get(Calendar.DAY_OF_MONTH) + "日");
			if (tmpDate.length() > 10) {
				result.append(pointDate.get(Calendar.HOUR_OF_DAY) + "时");
				result.append(pointDate.get(Calendar.MINUTE) + "分");
				result.append(pointDate.get(Calendar.SECOND) + "秒");
			}
		} else {
			result.append(pointDate.get(Calendar.YEAR) + "年");
			result.append((pointDate.get(Calendar.MONTH) + 1) + "月");
			result.append(pointDate.get(Calendar.DAY_OF_MONTH) + "日");
			result.append(pointDate.get(Calendar.HOUR_OF_DAY) + "时");
			result.append(pointDate.get(Calendar.MINUTE) + "分");
			result.append(pointDate.get(Calendar.SECOND) + "秒");
		}
		return result.toString();
	}

	/**
	 * @todo 转换中文日期为指定格式的英文日期形式
	 * @param chinaDate
	 * @param fmt
	 * @return
	 */
	public static String parseChinaDate(String chinaDate, String fmt) {
		if (StringUtil.isBlank(chinaDate))
			return null;
		// 去除中文日期文字之间的空格
		String tmp = chinaDate.replaceAll("\\s+", "");
		for (int i = 0; i < CHINA_DATE_KEYS.length; i++) {
			tmp = tmp.replaceAll(CHINA_DATE_KEYS[i], CHINA_DATE_KEY_MAP[i]);
		}
		tmp = tmp.replace("整", "").trim();
		if (tmp.endsWith("-") || tmp.endsWith(":"))
			tmp = tmp.substring(0, tmp.length() - 1);
		if (StringUtil.isBlank(fmt))
			return tmp.toString();
		else
			return formatDate(tmp, fmt);
	}

	/**
	 * @todo 转换中文日期为英文格式
	 * @param chinaDate
	 * @return
	 */
	public static String parseChinaDate(String chinaDate) {
		return parseChinaDate(chinaDate, null);
	}

	/**
	 * @todo 获取月份的第一天
	 * @param objectDate
	 * @return
	 */
	public static Date firstDayOfMonth(Object objectDate) {
		Date date = convertDateObject(objectDate);
		if (null == date)
			return null;
		else {
			String tmp = formatDate(date, FORMAT.DATE_HORIZONTAL);
			return parse(tmp, FORMAT.YEAR_MONTH);
		}
	}

	/**
	 * @todo 获取月份的最后一天
	 * @param objectDate
	 * @return
	 */
	public static Date lastDayOfMonth(Object objectDate) {
		Date date = convertDateObject(objectDate);
		if (null == date)
			return null;
		else {
			String tmp = formatDate(date, FORMAT.DATE_HORIZONTAL);
			Date result = parse(tmp, FORMAT.YEAR_MONTH);
			result = addMonth(result, 1);
			result = addDay(result, -1);
			return result;
		}
	}
}
