package org.sagacity.sqltoy.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Time;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.sagacity.sqltoy.SqlToyConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sagacity-sqltoy
 * @description 日期处理支持类，提供日常工作中的所有日期的操作处理
 * @author zhongxuchen
 * @version v1.0,Date:2008-12-14
 * @modify data:2012-8-27 {对日期的格式化增加了locale功能}
 * @modify data:2015-8-8 对parseString功能进行了优化,对英文和中文日期进行解析,同时优化了格式判断的逻辑
 * @modify data:2019-10-11 支持LocalDate、LocalTime、LocalDateTime等新的日期类型
 * @modify data:2023-12-05 强化英文日期的解析以及localTime、localDateTime格式化，提升格式化精度
 */
@SuppressWarnings({ "unused" })
public class DateUtil {
	/**
	 * 定义日志
	 */
	private final static Logger logger = LoggerFactory.getLogger(DateUtil.class);

	private static final String[] CHINA_DATE_KEYS = { "○", "О", "0", "Ο", "O", "零", "一", "二", "三", "四", "五", "六", "七",
			"八", "九", "十", "年", "月", "日", "时", "分", "秒" };
	private static final String[] CHINA_DATE_KEY_MAP = { "0", "0", "0", "0", "0", "0", "1", "2", "3", "4", "5", "6",
			"7", "8", "9", "10", "-", "-", " ", ":", ":", " " };

	/**
	 * 英文月份名称
	 */
	private static final String[] MONTH_ENGLISH_NAME = { "January", "February", "March", "April", "May", "June", "July",
			"August", "September", "October", "November", "December" };
	private static final String[] MONTH_ENGLISH_NAKE = { "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep",
			"Oct", "Nov", "Dec" };
	private final static Pattern MONTH_PATTERN = Pattern
			.compile("(?i)(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\\s");
	/**
	 * 中文星期的名称
	 */
	private static final String[] WEEK_CHINA_NAME = { "星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日" };
	private static final String[] WEEK_ENGLISH_NAME = { "Monday", "Tuesday", "Wednesday", "Thurday", "Friday",
			"Saturday", "Sunday" };
	private static final String[] WEEK_ENGLISH_NAKE = { "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun" };
	private final static Pattern WEEK_PATTERN = Pattern.compile("(?i)(Mon|Tue|Wed|Thu|Fri|Sat|Sun)\\s");
	private final static Pattern DAY_PATTERN = Pattern.compile("(?i)\\s\\d{1,2}(st|th|rd)?\\s");

	// 超过十的中文
	private final static Pattern MORE_TEN_PATTERN = Pattern.compile("(一|二|三|四|五)?\\十(一|二|三|四|五|六|七|八|九)");

	/**
	 * 英文日期的几种格式
	 */
	private static final Collection<String> DEFAULT_DAY_PATTERNS = Arrays
			.asList(new String[] { "MMM dd yyyy", "MMM dd z yyyy" });
	private static final Collection<String> DEFAULT_WEEK_DAY_PATTERNS = Arrays
			.asList(new String[] { "EEE MMM dd yyyy", "EEE MMM dd z yyyy" });
	private static final Collection<String> DEFAULT_TIME_PATTERNS = Arrays.asList(new String[] {
			"MMM dd HH:mm:ss z yyyy", "MMM dd HH:mm:ss yyyy", "MMM dd yyyy HH:mm:ss", "MMM dd yyyy HH:mm:ss z" });
	private static final Collection<String> DEFAULT_WEEK_TIME_PATTERNS = Arrays
			.asList(new String[] { "EEE MMM dd HH:mm:ss z yyyy", "EEE MMM dd HH:mm:ss yyyy",
					"EEE MMM dd yyyy HH:mm:ss z", "EEEE dd-MMM-yyyy HH:mm:ss", "EEEE dd-MMM-yyyy HH:mm:ss z",
					"EEEE dd-MMM-yy HH:mm:ss z", "EEEE dd-MMM-yy HH:mm:ss" });

	private final static Pattern DAYTH_PATTERN = Pattern.compile("(?i)\\s\\d{1,2}(st|th|nd|rd)\\s");
	private final static Pattern TIME_PATTERN = Pattern.compile("\\d{1,2}\\:\\d{1,2}");
	private final static Pattern TIME_DOT_PATTERN = Pattern.compile("\\d\\.\\d");
	private final static Pattern ZONED_TIME_PATTERN = Pattern
			.compile("(\\+|\\-)\\d{1,2}\\:\\d{2}(\\[[a-z|A-Z|\\/|\\_]+\\])?$");

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
		public final static String DATETIME_DOT = "yyyy.MM.dd HH:mm:ss";

		/**
		 * 日期时间(日期反斜杠)
		 */
		public final static String DATETIME_SLASH = "yyyy/MM/dd HH:mm:ss";

		/**
		 * 日期时间(日期横杠)
		 */
		public final static String DATETIME_HORIZONTAL = "yyyy-MM-dd HH:mm:ss";
	}

	private DateUtil() {

	}

	/**
	 * @todo 将日期字符串或时间转换成时间类型 日期字符串中的日期分隔符可是:"/",".","-"， 返回时间具体到秒 只提供常用的日期格式处理
	 * @param data
	 * @param format
	 * @return
	 */
	public static Date parse(Object data, String format) {
		return parse(data, format, null);
	}

	public static Date parse(Object data, String format, Locale locale) {
		if (null == data) {
			logger.error("The date string is null!");
			return null;
		}
		if (data instanceof String) {
			return parseString((String) data, format, locale);
		}
		if (format == null) {
			return convertDateObject(data, null, locale);
		}
		// 非字符串
		String result = formatDate(data, format, locale);
		return parseString(result, format, locale);
	}

	public static Date parseString(String dateStr) {
		return parseString(dateStr, null, null);
	}

	/**
	 * 去除字符串中的时区信息，避免时间解析错误
	 * 
	 * @param dateVar
	 * @return
	 */
	private static String removeZoneInfo(String dateVar) {
		Matcher m = ZONED_TIME_PATTERN.matcher(dateVar);
		if (m.find()) {
			return dateVar.substring(0, m.start());
		}
		return dateVar;
	}

	/**
	 * @todo 将日期字符串或时间转换成时间类型 日期字符串中的日期分隔符可是:"/",".","-"， 返回时间具体到秒 只提供常用的日期格式处理
	 * @param dateVar
	 * @param dateFormat
	 * @param locale
	 * @return
	 */
	public static Date parseString(String dateVar, String dateFormat, Locale locale) {
		if (dateVar == null) {
			return null;
		}
		String dateStr = dateVar.trim();
		if ("".equals(dateStr) || dateStr.toLowerCase().equals("null")) {
			return null;
		}
		// 去除时区信息
		dateStr = removeZoneInfo(dateStr);
		String realDF = null;
		boolean isLocalDateTime = false;
		boolean isLocalTime = false;
		boolean hasFmt = false;
		if (dateFormat != null && !dateFormat.trim().equals("")) {
			realDF = dateFormat;
			hasFmt = true;
		} // 英文日期格式(2个以上字母)
		else if (StringUtil.matches(dateStr, "[a-zA-Z]{2}")) {
			return parseEnglishDate(dateStr, locale);
		} else {
			// 中文日期格式
			if (StringUtil.matches(dateStr, "[年月日时分秒]")) {
				dateStr = parseChinaDate(dateStr);
			}
			// 含中文，但非标准的时间性中文
			else if (StringUtil.hasChinese(dateStr)) {
				return null;
			}
			int size;
			boolean hasBlank = (dateStr.indexOf(" ") != -1 || dateStr.toUpperCase().indexOf("T") >= 6);
			int splitCount;
			int startIndex;
			// 日期和时间的组合
			if (hasBlank) {
				// 统一格式(去除掉日期中的符号变成全数字),update 2019-08-12 支持jdk8日期
				dateStr = dateStr.replaceFirst("\\s+", " ").replaceFirst("(?i)T", " ").replace("-", "").replace(".", "")
						.replace(":", "").replace("/", "");
				int preSize = dateStr.indexOf(" ");
				size = dateStr.length();
				if (size > 16) {
					dateStr = addZero(dateStr, size, 24);
					realDF = "yyyyMMdd HHmmssSSSSSSSSS";
					isLocalDateTime = true;
				} else if (size == 16) {
					if (preSize == 8) {
						dateStr = addZero(dateStr, size, 21);
						realDF = "yyyyMMdd HHmmssSSSSSS";
						isLocalDateTime = true;
					} else {
						dateStr = addZero(dateStr, size, 19);
						realDF = "yyMMdd HHmmssSSSSSS";
						isLocalDateTime = true;
					}
				} else if (size == 13) {
					if (preSize == 8) {
						realDF = "yyyyMMdd HHmm";
					} else {
						realDF = "yyMMdd HHmmss";
					}
				} else if (size == 11) {
					if (preSize == 8) {
						realDF = "yyyyMMdd HH";
					} else {
						realDF = "yyMMdd HHmm";
					}
				} else if (size == 9) {
					realDF = "yyMMdd HH";
				} else {
					realDF = "yyyyMMdd HHmmss";
				}
			} else {
				// 去除数字中带的,例如:201,512
				dateStr = dateStr.replace(",", "");
				size = dateStr.length();
				if (dateStr.indexOf(":") != -1) {
					if (dateStr.indexOf(".") != -1) {
						dateStr = addZero(dateStr, size, 18);
						realDF = "HH:mm:ss.SSSSSSSSS";
						isLocalTime = true;
					} else {
						if (size == 5) {
							realDF = "HH:mm";
						} else {
							realDF = "HH:mm:ss";
						}
					}
				} else {
					dateStr = dateStr.replace("-", "/").replace(".", "/");
					splitCount = StringUtil.matchCnt(dateStr, "\\/");
					if (splitCount == 2) {
						startIndex = dateStr.indexOf("/");
						if (startIndex == 2) {
							realDF = "yy/MM/dd";
						} else {
							realDF = "yyyy/MM/dd";
						}
					} else if (splitCount == 1) {
						if (size > 5) {
							realDF = "yyyy/MM";
						} else {
							realDF = "yy/MM";
						}
					} else {
						if (size > 16) {
							dateStr = dateStr.substring(0, 8).concat(" ").concat(dateStr.substring(8, 14)).concat(".")
									.concat(addZero(dateStr.substring(14), 9));
							realDF = "yyyyMMdd HHmmss.SSSSSSSSS";
							isLocalDateTime = true;
						} else if (size == 15) {
							realDF = "yyyyMMdd HHmmss";
						} else if (size == 14) {
							realDF = "yyyyMMddHHmmss";
						} // 无空白纯数字13位是System.currentTimeMillis()对应的值
						else if (size == 13) {
							return new java.util.Date(Long.valueOf(dateStr));
						} else if (size == 12) {
							realDF = "yyMMddHHmmss";
						} else if (size == 10) {
							realDF = "yyyyMMddHH";
						} else if (size == 6) {
							realDF = "yyyyMM";
						} else if (size == 4) {
							realDF = "yyyy";
						} else {
							realDF = "yyyyMMdd";
						}
					}
				}
			}
			if (realDF == null) {
				realDF = hasBlank ? "yyyy-MM-dd HH:mm:ss" : "yyyy-MM-dd";
			}
		}
		// 针对高精度进行特殊处理
		if (isLocalTime) {
			LocalTime time = LocalTime.parse(dateStr, DateTimeFormatter.ofPattern(realDF));
			return asDate(time);
		} else if (isLocalDateTime) {
			LocalDateTime localDateTime = LocalDateTime.parse(dateStr, DateTimeFormatter.ofPattern(realDF));
			return asDate(localDateTime);
		}
		Date result = null;
		boolean hasException = false;
		Exception ex = null;
		// 通过异常模式进行一次容错处理
		try {
			DateFormat df = (locale == null) ? new SimpleDateFormat(realDF) : new SimpleDateFormat(realDF, locale);
			result = df.parse(dateStr);
		} catch (ParseException e) {
			hasException = true;
			ex = e;
		}
		// 在指定format情况下result==null，则将format置为null,通过自动解析方式获取具体的格式
		if (result == null && hasFmt) {
			result = parseString(dateVar, null, locale);
			result = parseString(formatDate(result, realDF));
			if (result == null && hasException) {
				ex.printStackTrace();
			}
		}
		return result;
	}

	public static ZonedDateTime parseZonedDateTime(String dateVar) {
		if (dateVar == null) {
			return null;
		}
		// 含时区正则表达式匹配
		Matcher m = ZONED_TIME_PATTERN.matcher(dateVar);
		if (m.find()) {
			LocalDateTime baseDate = parseLocalDateTime(dateVar.substring(0, m.start()));
			// 提取偏差hour
			String timeZone = m.group();
			if (timeZone.contains("[") && timeZone.contains("]")) {
				// 时区，如Asia/Shanghai
				timeZone = timeZone.substring(timeZone.indexOf("[") + 1, timeZone.indexOf("]"));
				return ZonedDateTime.of(baseDate, ZoneId.of(timeZone));
			} // 补齐0，保障±08:00 六位长度
			else if (timeZone.length() == 5) {
				timeZone = timeZone.substring(0, 1).concat("0").concat(timeZone.substring(1));
			}
			return ZonedDateTime.of(baseDate, ZoneOffset.of(timeZone));
		}
		LocalDateTime baseDate = parseLocalDateTime(dateVar);
		return ZonedDateTime.of(baseDate, SqlToyConstants.getZoneId());
	}

	public static LocalDateTime parseLocalDateTime(String dateVar) {
		return parseLocalDateTime(dateVar, null);
	}

	public static LocalDateTime parseLocalDateTime(String dateVar, String dateFormat) {
		if (dateVar == null) {
			return null;
		}
		String dateStr = dateVar.trim();
		if ("".equals(dateStr) || dateStr.toLowerCase().equals("null")) {
			return null;
		}
		// 去除时区信息
		dateStr = removeZoneInfo(dateStr);
		String realDF = null;
		boolean isTime = false;
		boolean isDate = false;
		boolean hasFmt = false;
		if (dateFormat != null && !dateFormat.trim().equals("")) {
			realDF = dateFormat;
			hasFmt = true;
		} // 英文日期格式(2个以上字母)
		else if (StringUtil.matches(dateStr, "[a-zA-Z]{2}")) {
			return asLocalDateTime(parseEnglishDate(dateStr, null));
		} else {
			// 中文日期格式
			if (StringUtil.matches(dateStr, "[年月日时分秒]")) {
				dateStr = parseChinaDate(dateStr);
			} // 含中文，但非标准的时间性中文
			else if (StringUtil.hasChinese(dateStr)) {
				return null;
			}
			int size;
			boolean hasBlank = (dateStr.indexOf(" ") != -1 || dateStr.toUpperCase().indexOf("T") >= 6);
			int splitCount;
			int startIndex;
			// 日期和时间的组合
			if (hasBlank) {
				// 统一格式(去除掉日期中的符号变成全数字),update 2019-08-12 支持jdk8日期
				dateStr = dateStr.replaceFirst("\\s+", " ").replaceFirst("(?i)T", " ").replace("-", "").replace(".", "")
						.replace(":", "").replace("/", "");
				int preSize = dateStr.indexOf(" ");
				size = dateStr.length();
				if (size > 16) {
					dateStr = addZero(dateStr, size, 24);
					realDF = "yyyyMMdd HHmmssSSSSSSSSS";
				} else if (size == 16) {
					if (preSize == 8) {
						dateStr = addZero(dateStr, size, 21);
						realDF = "yyyyMMdd HHmmssSSSSSS";
					} else {
						dateStr = addZero(dateStr, size, 19);
						realDF = "yyMMdd HHmmssSSSSSS";
					}
				} else if (size == 13) {
					if (preSize == 8) {
						realDF = "yyyyMMdd HHmm";
					} else {
						realDF = "yyMMdd HHmmss";
					}
				} else if (size == 11) {
					if (preSize == 8) {
						realDF = "yyyyMMdd HH";
					} else {
						realDF = "yyMMdd HHmm";
					}
				} else if (size == 9) {
					realDF = "yyMMdd HH";
				} else {
					realDF = "yyyyMMdd HHmmss";
				}
			} else {
				// 去除数字中带的,例如:201,512
				dateStr = dateStr.replace(",", "");
				size = dateStr.length();
				if (dateStr.indexOf(":") != -1) {
					if (dateStr.indexOf(".") != -1) {
						dateStr = addZero(dateStr, size, 18);
						realDF = "HH:mm:ss.SSSSSSSSS";
					} else {
						if (size == 5) {
							realDF = "HH:mm";
						} else {
							realDF = "HH:mm:ss";
						}
					}
					isTime = true;
				} else {
					dateStr = dateStr.replace("-", "/").replace(".", "/");
					splitCount = StringUtil.matchCnt(dateStr, "\\/");
					if (splitCount == 2) {
						startIndex = dateStr.indexOf("/");
						if (startIndex == 2) {
							realDF = "yy/MM/dd";
						} else {
							realDF = "yyyy/MM/dd";
						}
						isDate = true;
					} else if (splitCount == 1) {
						if (size > 5) {
							realDF = "yyyy/MM";
						} else {
							realDF = "yy/MM";
						}
						isDate = true;
					} else {
						if (size > 16) {
							realDF = "yyyyMMdd HHmmss.SSSSSSSSS";
							dateStr = dateStr.substring(0, 8).concat(" ").concat(dateStr.substring(8, 14)).concat(".")
									.concat(addZero(dateStr.substring(14), 9));
						} else if (size == 15) {
							realDF = "yyyyMMdd HHmmss";
						} else if (size == 14) {
							realDF = "yyyyMMddHHmmss";
						} // 无空白纯数字13位是System.currentTimeMillis()对应的值
						else if (size == 13) {
							return asLocalDateTime(new java.util.Date(Long.valueOf(dateStr)));
						} else if (size == 12) {
							realDF = "yyMMddHHmmss";
						} else if (size == 10) {
							realDF = "yyyyMMddHH";
						} else if (size == 6) {
							realDF = "yyyyMM";
							isDate = true;
						} else if (size == 4) {
							realDF = "yyyy";
							isDate = true;
						} else {
							realDF = "yyyyMMdd";
							isDate = true;
						}
					}
				}
			}
			if (realDF == null) {
				if (hasBlank) {
					realDF = "yyyy-MM-dd HH:mm:ss";
				} else {
					realDF = "yyyy-MM-dd";
					isDate = true;
				}
			}
		}
		LocalDateTime result = null;
		try {
			if (isTime) {
				LocalTime timeResult = LocalTime.parse(dateStr, DateTimeFormatter.ofPattern(realDF));
				return LocalDateTime.of(LocalDate.now(), timeResult);
			} else if (isDate) {
				Date dateResult = new SimpleDateFormat(realDF).parse(dateStr);
				return asLocalDateTime(dateResult);
			}
			result = LocalDateTime.parse(dateStr, DateTimeFormatter.ofPattern(realDF));
		} catch (Exception e) {
			e.printStackTrace();
		}
		// 结果为null，格式不为null,通过自动格式匹配模式，进行一次补偿处理
		if (result == null && hasFmt) {
			result = parseLocalDateTime(dateStr, null);
			result = parseLocalDateTime(formatDate(result, realDF));
		}
		return result;
	}

	public static Date convertDateObject(Object dt) {
		return convertDateObject(dt, null, null);
	}

	public static LocalDateTime convertLocalDateTime(Object dt) {
		if (dt == null) {
			logger.warn("日期不能为空,请正确输入!");
			return null;
		}
		LocalDateTime result = null;
		if (dt instanceof String) {
			return parseLocalDateTime(dt.toString().trim());
		} // 为什么要new 一个，目的是避免前面日期对象变化导致后续转化后的也变化，所以这里是新建
		else if (dt instanceof java.util.Date) {
			return asLocalDateTime((Date) dt);
		} else if (dt instanceof java.time.LocalDate) {
			LocalDate localDate = (LocalDate) dt;
			result = LocalDateTime.of(localDate.getYear(), localDate.getMonthValue(), localDate.getDayOfMonth(), 0, 0,
					0);
			return result;
		} else if (dt instanceof java.time.LocalDateTime) {
			return (LocalDateTime) dt;
		} else if (dt instanceof java.time.OffsetDateTime) {
			return ((OffsetDateTime) dt).toLocalDateTime();
		} else if (dt instanceof java.time.ZonedDateTime) {
			return ((ZonedDateTime) dt).toLocalDateTime();
		} else if (dt instanceof java.lang.Number) {
			return parseLocalDateTime(dt.toString().trim());
		} else if (dt instanceof java.time.LocalTime) {
			result = LocalDateTime.of(LocalDate.now(), (LocalTime) dt);
		}
		return result;
	}

	/**
	 * @todo 日期对象类型转换
	 * @param dt
	 * @param format
	 * @param locale
	 * @return
	 */
	public static Date convertDateObject(Object dt, String format, Locale locale) {
		if (dt == null) {
			logger.warn("日期不能为空,请正确输入!");
			return null;
		}
		Date result = null;
		if (dt instanceof String) {
			String dtStr = dt.toString().trim();
			if (dtStr.length() == 13 && NumberUtil.isInteger(dtStr)) {
				result = new java.util.Date(Long.valueOf(dtStr));
			} else {
				result = parseString(dtStr, format, locale);
			}
		} // 为什么要new 一个，目的是避免前面日期对象变化导致后续转化后的也变化，所以这里是新建
		else if (dt instanceof java.util.Date) {
			result = new java.util.Date(((java.util.Date) dt).getTime());
		} else if (dt instanceof java.time.LocalDate) {
			result = asDate((LocalDate) dt);
		} else if (dt instanceof java.time.LocalDateTime) {
			result = asDate((LocalDateTime) dt);
		} else if (dt instanceof java.time.OffsetDateTime) {
			result = asDate(((OffsetDateTime) dt).toLocalDateTime());
		} else if (dt instanceof java.time.ZonedDateTime) {
			result = asDate(((ZonedDateTime) dt).toLocalDateTime());
		} else if (dt instanceof java.lang.Number) {
			String dtStr = dt.toString().trim();
			// 13位表示毫秒数
			if (dtStr.length() != 13) {
				result = parseString(dtStr, format, locale);
			} else {
				result = new java.util.Date(((Number) dt).longValue());
			}
		} else if (dt instanceof java.time.LocalTime) {
			result = asDate((LocalTime) dt);
		} else if (dt instanceof java.time.OffsetTime) {
			result = asDate(((OffsetTime) dt).toLocalTime());
		} else {
			result = parseString(dt.toString().trim(), format, locale);
		}
		return result;
	}

	/**
	 * @todo 格式化日期
	 * @param dt
	 * @param format
	 * @return
	 */
	public static String formatDate(Object dt, String format) {
		return formatDate(dt, format, null);
	}

	public static String formatDate(Object dt, String format, Locale locale) {
		if (dt == null) {
			return null;
		}
		if (format == null) {
			throw new IllegalArgumentException("格式化日期指定的format 为null,请正确输入参数!");
		}
		String fmtUpper = format.toUpperCase();
		if ("YY".equals(fmtUpper)) {
			String year = Integer.toString(getYear(dt));
			return year.substring(year.length() - 2);
		}
		if ("YYYY".equals(fmtUpper)) {
			return Integer.toString(getYear(dt));
		}
		if ("MM".equals(fmtUpper)) {
			int month = getMonth(dt);
			return (month < 10 ? "0" : "").concat(Integer.toString(month));
		}
		if ("DD".equals(fmtUpper)) {
			int day = getDayOfMonth(dt);
			return (day < 10 ? "0" : "").concat(Integer.toString(day));
		}
		if (dt instanceof LocalDateTime) {
			return DateTimeFormatter.ofPattern(format).format((LocalDateTime) dt);
		} else if (dt instanceof OffsetDateTime) {
			return DateTimeFormatter.ofPattern(format).format(((OffsetDateTime) dt).toLocalDateTime());
		} else if (dt instanceof ZonedDateTime) {
			return DateTimeFormatter.ofPattern(format).format(((ZonedDateTime) dt).toLocalDateTime());
		} else if (dt instanceof LocalTime) {
			return DateTimeFormatter.ofPattern(format).format((LocalTime) dt);
		} else if (dt instanceof LocalDate) {
			return DateTimeFormatter.ofPattern(format).format((LocalDate) dt);
		}
		// 高精度时间用localDateTime、localTime
		if (locale == null && (fmtUpper.endsWith("SSS") || fmtUpper.endsWith(".S"))) {
			LocalDateTime result = convertLocalDateTime(dt);
			if (result == null) {
				return null;
			}
			// yyyy-MM-dd HH:mm:ss.SSS
			if (fmtUpper.startsWith("YY")) {
				return DateTimeFormatter.ofPattern(format).format(result);
			} else if (fmtUpper.startsWith("HH")) {
				return DateTimeFormatter.ofPattern(format).format(result.toLocalTime());
			}
		}
		// 低精度用SimpleDateFormat，兼容性强
		DateFormat df = (locale == null) ? new SimpleDateFormat(format) : new SimpleDateFormat(format, locale);
		Date tmp = convertDateObject(dt, null, locale);
		return (null == tmp) ? null : df.format(tmp);
	}

	/**
	 * @TODO 通过一个格式解析，再转化为另外一个格式
	 * @param dt
	 * @param format
	 * @param targetFormat
	 * @param locale
	 * @return
	 */
	@Deprecated
	public static String formatDate(Object dt, String format, String targetFormat, Locale locale) {
		Date result = parse(dt, format, locale);
		return formatDate(result, targetFormat);
	}

	/**
	 * @todo 获取当前以sql.date的日期
	 * @param date
	 * @return
	 */
	public static java.sql.Date getSqlDate(Object date) {
		if (date == null) {
			return new java.sql.Date(System.currentTimeMillis());
		}
		if (date instanceof LocalDate) {
			return java.sql.Date.valueOf((LocalDate) date);
		}
		if (date instanceof LocalDateTime) {
			return java.sql.Date.valueOf(((LocalDateTime) date).toLocalDate());
		}
		if (date instanceof OffsetDateTime) {
			return java.sql.Date.valueOf(((OffsetDateTime) date).toLocalDate());
		}
		if (date instanceof ZonedDateTime) {
			return java.sql.Date.valueOf(((ZonedDateTime) date).toLocalDate());
		}
		return new java.sql.Date(convertDateObject(date).getTime());
	}

	public static java.sql.Timestamp getTimestamp(Object date) {
		if (date == null) {
			return new Timestamp(System.currentTimeMillis());
		}
		if (date instanceof LocalDateTime) {
			return Timestamp.valueOf((LocalDateTime) date);
		}
		if (date instanceof OffsetDateTime) {
			return Timestamp.valueOf(((OffsetDateTime) date).toLocalDateTime());
		}
		if (date instanceof ZonedDateTime) {
			return Timestamp.valueOf(((ZonedDateTime) date).toLocalDateTime());
		}
		return new Timestamp(convertDateObject(date).getTime());
	}

	/**
	 * @todo 获取当前操作系统的时间
	 * @return 当前操作系统的时间
	 */
	public static Date getNowTime() {
		// return Calendar.getInstance().getTime();
		return Date.from(Instant.now());
	}

	public static LocalDate getDate() {
		return LocalDate.now();
	}

	public static LocalDate getDate(Object date) {
		if (date == null) {
			return null;
		}
		if (date instanceof LocalDate) {
			return (LocalDate) date;
		} else if (date instanceof LocalDateTime) {
			return ((LocalDateTime) date).toLocalDate();
		} else if (date instanceof OffsetDateTime) {
			return ((OffsetDateTime) date).toLocalDate();
		} else if (date instanceof ZonedDateTime) {
			return ((ZonedDateTime) date).toLocalDate();
		}
		return asLocalDate(convertDateObject(date));
	}

	public static LocalDateTime getDateTime() {
		return LocalDateTime.now();
	}

	public static LocalDateTime getDateTime(Object date) {
		if (date == null) {
			return null;
		}
		if (date instanceof LocalDateTime) {
			return (LocalDateTime) date;
		} else if (date instanceof OffsetDateTime) {
			return ((OffsetDateTime) date).toLocalDateTime();
		} else if (date instanceof ZonedDateTime) {
			return ((ZonedDateTime) date).toLocalDateTime();
		}
		return asLocalDateTime(convertDateObject(date));
	}

	public static LocalTime getTime() {
		return LocalTime.now();
	}

	// Add millisecond
	public static Date addMilliSecond(Object dt, long millisecond) {
		Date result = convertDateObject(dt);
		if (millisecond != 0) {
			result.setTime(result.getTime() + millisecond);
		}
		return result;
	}

	public static Date addSecond(Object dt, double second) {
		Double millisecond = Double.valueOf(1000.0 * second);
		return addMilliSecond(dt, millisecond.longValue());
	}

	public static Date addMinute(Object dt, double minute) {
		Double millisecond = Double.valueOf(1000.0 * 60.0 * minute);
		return addMilliSecond(dt, millisecond.longValue());
	}

	public static Date addHour(Object dt, double hour) {
		Double millisecond = 1000.0 * 60.0 * 60.0 * hour;
		return addMilliSecond(dt, millisecond.longValue());
	}

	public static Date addDay(Object dt, double day) {
		Double millisecond = Double.valueOf(1000.0 * 60.0 * 60.0 * 24.0 * day);
		return addMilliSecond(dt, millisecond.longValue());
	}

	// add month
	public static Date addMonth(Object dt, int month) {
		GregorianCalendar gc = new GregorianCalendar();
		gc.setTime(convertDateObject(dt));
		gc.add(Calendar.MONTH, month);
		return gc.getTime();
	}

	public static Date addYear(Object dt, int year) {
		GregorianCalendar gc = new GregorianCalendar();
		gc.setTime(convertDateObject(dt));
		gc.add(Calendar.YEAR, year);
		return gc.getTime();
	}

	public static int getYear(Object dateValue) {
		if (dateValue == null) {
			return LocalDate.now().getYear();
		}
		return convertLocalDateTime(dateValue).getYear();
	}

	public static int getMonth(Object dateValue) {
		if (dateValue == null) {
			return LocalDate.now().getMonthValue();
		}
		return convertLocalDateTime(dateValue).getMonthValue();
	}

	/**
	 * @see getDayOfMonth(Object dateValue)
	 * @param dateValue
	 * @return
	 */
	@Deprecated
	public static int getDay(Object dateValue) {
		return getDayOfMonth(dateValue);
	}

	/**
	 * @TODO 获取当月中的第几天(1~31)
	 * @param dateValue
	 * @return
	 */
	public static int getDayOfMonth(Object dateValue) {
		if (dateValue == null) {
			return LocalDate.now().getDayOfMonth();
		}
		return convertLocalDateTime(dateValue).getDayOfMonth();
	}

	/**
	 * @todo 获取指定日期是星期几(from 1 (Monday) to 7 (Sunday))
	 * @param dateValue
	 * @return
	 */
	public static int getDayOfWeek(Object dateValue) {
		if (dateValue == null) {
			return LocalDate.now().getDayOfWeek().getValue();
		}
		return convertLocalDateTime(dateValue).getDayOfWeek().getValue();
	}

	/**
	 * @todo 获取给定日期所在年的第几周
	 * @param dateValue
	 * @return
	 */
	public static int getWeekOfYear(Object dateValue) {
		// 默认使用当前日期
		LocalDate targetDate = LocalDate.now();
		if (dateValue != null) {
			// 将传入的 dateValue 转换为 LocalDate（需根据实际类型适配 convertDateObject）
			Date date = convertDateObject(dateValue);
			targetDate = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
		}
		// 定义周的规则：比如中国习惯（周一为一周第一天，第一周至少有1天）
		WeekFields weekFields = WeekFields.of(Locale.CHINA);
		// 获取周数（从1开始），减1转为从0开始
		return targetDate.get(weekFields.weekOfYear()) - 1;
	}

	/**
	 * @todo 获取相隔两个时间的周数
	 * @param floorDate
	 * @param goalDate
	 * @return
	 */
	public static double getIntervalWeeks(Object floorDate, Object goalDate) {
		BigDecimal result = new BigDecimal(getIntervalHours(floorDate, goalDate) / (7 * 24));
		return result.setScale(1, RoundingMode.HALF_UP).doubleValue();
	}

	/**
	 * @todo <b>获取两时间间隔的月数</b>
	 * @param floorDate
	 * @param goalDate
	 * @return
	 */
	public static int getIntervalMonths(Object floorDate, Object goalDate) {
		LocalDateTime date1 = convertLocalDateTime(goalDate);
		LocalDateTime date2 = convertLocalDateTime(floorDate);
		return date1.getYear() * 12 + date1.getMonthValue() - date2.getYear() * 12 - date2.getMonthValue();
	}

	/**
	 * @todo <b>获取两时间间隔的整数年数</b>
	 * @param floorDate
	 * @param goalDate
	 * @return
	 */
	public static int getIntervalYears(Object floorDate, Object goalDate) {
		return convertLocalDateTime(goalDate).getYear() - convertLocalDateTime(floorDate).getYear();
	}

	/**
	 * @todo 获取两个时间间隔的天数
	 * @param floorDate
	 * @param goalDate
	 * @return
	 */
	public static int getIntervalDays(Object floorDate, Object goalDate) {
		BigDecimal result = new BigDecimal(
				Double.valueOf(getIntervalMillSeconds(formatDate(floorDate, FORMAT.DATE_HORIZONTAL),
						formatDate(goalDate, FORMAT.DATE_HORIZONTAL))) / (3600 * 1000 * 24));
		return result.setScale(1, RoundingMode.HALF_UP).intValue();
	}

	/**
	 * @todo 获取两个时间间隔的小时
	 * @param floorDate
	 * @param goalDate
	 * @return
	 */
	public static double getIntervalHours(Object floorDate, Object goalDate) {
		BigDecimal result = new BigDecimal(Double.valueOf(getIntervalMillSeconds(floorDate, goalDate)) / (3600 * 1000));
		return result.setScale(1, RoundingMode.HALF_UP).doubleValue();
	}

	/**
	 * @todo 获取两时间的间隔分钟
	 * @param floorDate
	 * @param goalDate
	 * @return
	 */
	public static double getIntervalMinutes(Object floorDate, Object goalDate) {
		BigDecimal result = new BigDecimal(Double.valueOf(getIntervalMillSeconds(floorDate, goalDate)) / (60 * 1000));
		return result.setScale(1, RoundingMode.HALF_UP).doubleValue();
	}

	/**
	 * @todo 获取两时间间隔的秒数
	 * @param floorDate
	 * @param goalDate
	 * @return
	 */
	public static double getIntervalSeconds(Object floorDate, Object goalDate) {
		return Double.valueOf(getIntervalMillSeconds(floorDate, goalDate)) / (1000);
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
		if (null == date) {
			return null;
		}
		GregorianCalendar pointDate = new GregorianCalendar();
		pointDate.setTime(date);
		String tmpDate;
		StringBuilder result = new StringBuilder();
		if (dateValue instanceof String) {
			tmpDate = (String) dateValue;
			if (tmpDate.length() >= 4) {
				result.append(pointDate.get(Calendar.YEAR) + "年");
			}
			if (tmpDate.length() >= 6) {
				result.append((pointDate.get(Calendar.MONTH) + 1) + "月");
			}
			if (tmpDate.length() >= 8) {
				result.append(pointDate.get(Calendar.DAY_OF_MONTH) + "日");
			}
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
	 * @param format
	 * @return
	 */
	public static String parseChinaDate(String chinaDate, String format) {
		if (StringUtil.isBlank(chinaDate)) {
			return null;
		}
		// 去除中文日期文字之间的空格
		String tmp = chinaDate.replaceAll("\\s+", "");
		// 处理十几的值
		Matcher matcher = MORE_TEN_PATTERN.matcher(tmp);
		LinkedHashMap<String, String> map = new LinkedHashMap<String, String>();
		String groupStr;
		while (matcher.find()) {
			groupStr = matcher.group();
			// 三位如:二十五
			if (groupStr.length() == 3) {
				map.put(groupStr, groupStr.replace("十", ""));
			} else if (groupStr.length() == 2) {
				map.put(groupStr, groupStr.replace("十", "1"));
			}
		}
		for (Map.Entry<String, String> entry : map.entrySet()) {
			tmp = tmp.replaceAll(entry.getKey(), entry.getValue());
		}

		for (int i = 0; i < CHINA_DATE_KEYS.length; i++) {
			tmp = tmp.replaceAll(CHINA_DATE_KEYS[i], CHINA_DATE_KEY_MAP[i]);
		}
		tmp = tmp.replace("整", "").trim();
		if (tmp.endsWith("-") || tmp.endsWith(":")) {
			tmp = tmp.substring(0, tmp.length() - 1);
		}
		if (StringUtil.isBlank(format)) {
			return tmp.toString();
		}
		return formatDate(tmp, format);
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
		if (null == date) {
			return null;
		}
		String tmp = formatDate(date, FORMAT.DATE_HORIZONTAL);
		return parse(tmp, FORMAT.YEAR_MONTH);
	}

	/**
	 * @todo 获取月份的最后一天
	 * @param objectDate
	 * @return
	 */
	public static Date lastDayOfMonth(Object objectDate) {
		Date date = convertDateObject(objectDate);
		if (null == date) {
			return null;
		}
		String tmp = formatDate(date, FORMAT.DATE_HORIZONTAL);
		Date result = parse(tmp, FORMAT.YEAR_MONTH);
		result = addMonth(result, 1);
		result = addDay(result, -1);
		return result;
	}

	public static Date asDate(LocalDate localDate) {
		if (localDate == null) {
			return null;
		}
		return Date.from(localDate.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
	}

	public static Date asSqlDate(LocalDate localDate) {
		if (localDate == null) {
			return null;
		}
		return Date.from(localDate.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
	}

	public static Date asDate(LocalTime localTime) {
		if (localTime == null) {
			return null;
		}
		LocalDateTime localDateTime = LocalDateTime.of(LocalDate.now(), localTime);
		return Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
	}

	public static Time asTime(LocalTime localTime) {
		if (localTime == null) {
			return null;
		}
		return java.sql.Time.valueOf(localTime);
	}

	public static Date asDate(LocalDateTime localDateTime) {
		if (localDateTime == null) {
			return null;
		}
		return Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
	}

	public static LocalDate asLocalDate(Date date) {
		if (date == null) {
			return null;
		}
		return Instant.ofEpochMilli(date.getTime()).atZone(ZoneId.systemDefault()).toLocalDate();
	}

	public static LocalTime asLocalTime(Date date) {
		if (date == null) {
			return null;
		}
		return Instant.ofEpochMilli(date.getTime()).atZone(ZoneId.systemDefault()).toLocalTime();
	}

	public static LocalDateTime asLocalDateTime(Date date) {
		if (date == null) {
			return null;
		}
		return Instant.ofEpochMilli(date.getTime()).atZone(ZoneId.systemDefault()).toLocalDateTime();
	}

	/**
	 * @TODO 处理英文日期字符串，转化为日期类型
	 * @param dateStr
	 * @param locale
	 * @return
	 */
	private static Date parseEnglishDate(String dateStr, Locale locale) {
		// 统一格式,替换逗号和点号为空白
		dateStr = dateStr.replace(",", " ");
		dateStr = dateStr.replace(". ", " ");
		// 替换时间格式中可能存在的点号
		Matcher dotMatcher = TIME_DOT_PATTERN.matcher(dateStr);
		List<Integer> indexList = new ArrayList<>();
		while (dotMatcher.find()) {
			indexList.add(dotMatcher.start() + 1);
		}
		if (!indexList.isEmpty()) {
			StringBuilder stringBuilder = new StringBuilder(dateStr);
			for (int index : indexList) {
				stringBuilder.deleteCharAt(index);
				stringBuilder.insert(index, ":");
			}
			dateStr = stringBuilder.toString();
		}
		for (int i = 0; i < 12; i++) {
			dateStr = dateStr.replaceFirst("(?i)" + MONTH_ENGLISH_NAME[i], MONTH_ENGLISH_NAKE[i]);
		}
		// 统一九月的英文
		dateStr = dateStr.replaceFirst("(?i)Sept\\s", "Sep ");
		// 统一星期2、星期4的英文
		dateStr = dateStr.replaceFirst("(?i)Thur\\s", "Thu ").replaceFirst("(?i)Tues\\s", "Tue ");
		for (int i = 0; i < 7; i++) {
			dateStr = dateStr.replaceFirst("(?i)" + WEEK_ENGLISH_NAME[i], WEEK_ENGLISH_NAKE[i]);
		}
		// 首位补空格，便于匹配
		dateStr = " ".concat(dateStr);
		Matcher daythMatcher = DAYTH_PATTERN.matcher(dateStr);
		while (daythMatcher.find()) {
			String groupString = daythMatcher.group();
			groupString = groupString.substring(0, groupString.length() - 3);
			dateStr = dateStr.substring(0, daythMatcher.start()).concat(groupString)
					.concat(dateStr.substring(daythMatcher.end() - 1));
			break;
		}

		// 规整将月在前
		int monthIndex = StringUtil.matchIndex(dateStr, MONTH_PATTERN);
		int dayIndex = StringUtil.matchIndex(dateStr, DAY_PATTERN);
		if (monthIndex != 1 && dayIndex != -1 && dayIndex < monthIndex) {
			Matcher dayMatcher = DAY_PATTERN.matcher(dateStr);
			String dayStr = "";
			while (dayMatcher.find()) {
				dateStr = dateStr.substring(0, dayMatcher.start()).concat(dateStr.substring(dayMatcher.end() - 1));
				dayStr = dayMatcher.group().trim();
				break;
			}
			Matcher mothMatcher = MONTH_PATTERN.matcher(dateStr);
			while (mothMatcher.find()) {
				dateStr = dateStr.substring(0, mothMatcher.end()).concat(dayStr)
						.concat(dateStr.substring(mothMatcher.end() - 1));
				break;
			}
		}
		// 是否存在week
		boolean hasWeek = StringUtil.matches(dateStr, WEEK_PATTERN);
		dateStr = dateStr.trim();
		SimpleDateFormat dateParser = null;
		Iterator<String> formatIter;
		if (StringUtil.matches(dateStr, TIME_PATTERN)) {
			if (hasWeek) {
				formatIter = DEFAULT_WEEK_TIME_PATTERNS.iterator();
			} else {
				formatIter = DEFAULT_TIME_PATTERNS.iterator();
			}
		} else {
			if (hasWeek) {
				formatIter = DEFAULT_WEEK_DAY_PATTERNS.iterator();
			} else {
				formatIter = DEFAULT_DAY_PATTERNS.iterator();
			}
		}
		Date result = null;
		String format;
		while (formatIter.hasNext()) {
			format = (String) formatIter.next();
			if (dateParser == null) {
				dateParser = new SimpleDateFormat(format, (locale == null) ? Locale.ENGLISH : locale);
			} else {
				dateParser.applyPattern(format);
			}
			try {
				result = dateParser.parse(dateStr);
				if (result != null) {
					break;
				}
			} catch (ParseException pe) {
			}
		}
		return result;
	}

	/**
	 * @TODO 判断是否有纳秒，并处理优化实际精度
	 * @param nanoTime
	 * @return
	 */
	public static String processNano(int nanoTime) {
		// 纳秒为零，则到秒级
		if (nanoTime == 0) {
			return "";
		}
		String nanoStr = StringUtil.addLeftZero2Len("" + nanoTime, 9);
		// 后6位全为零，则为毫秒
		if (nanoStr.endsWith("000000")) {
			nanoStr = nanoStr.substring(0, 3);
		} // 后3位全为零,则为微秒
		else if (nanoStr.endsWith("000")) {
			nanoStr = nanoStr.substring(0, 6);
		}
		return "." + nanoStr;
	}

	private static String addZero(String timeStr, int toLength) {
		return addZero(timeStr, timeStr.length(), toLength);
	}

	/**
	 * 快速补充零
	 * 
	 * @param timeStr
	 * @param size
	 * @param toLength
	 * @return
	 */
	private static String addZero(String timeStr, int size, int toLength) {
		int addSize = toLength - size;
		if (addSize == 1) {
			return timeStr.concat("0");
		} else if (addSize == 2) {
			return timeStr.concat("00");
		} else if (addSize == 3) {
			return timeStr.concat("000");
		} else if (addSize == 5) {
			return timeStr.concat("00000");
		} else if (addSize == 6) {
			return timeStr.concat("000000");
		} else if (addSize == 8) {
			return timeStr.concat("00000000");
		} else if (addSize == 4) {
			return timeStr.concat("0000");
		} else if (addSize == 7) {
			return timeStr.concat("0000000");
		}
		return timeStr;
	}

}
