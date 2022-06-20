package org.sagacity.sqltoy.utils;

import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sagacity-sqltoy
 * @description 科学表达式运算（来源于网络）
 * @author zhongxuchen
 * @version v1.0,Date:2009-5-20
 */
@SuppressWarnings("rawtypes")
public class ExpressionUtil {
	/**
	 * 定义日志
	 */
	private final static Logger logger = LoggerFactory.getLogger(ExpressionUtil.class);

	public static final String OPTS = "+-*/%><][!|&=#";

	private ExpressionUtil() {
	}

	@SuppressWarnings("unchecked")
	public static Object calculate(String expression) {
		try {
			Stack Opts = new Stack();
			Stack Values = new Stack();
			String exp = expression + "#";
			int nCount = exp.length(), nIn, nOut, nTemp;
			Opts.push("#");
			String temp = "", optIn = "", value1 = "", value2 = "", opt = "", temp1 = "";
			int nFun = 0;
			boolean isFun = false;
			for (int i = 0; i < nCount;) {
				nTemp = 0;
				opt = exp.substring(i, i + 1);
				isFun = false;
				temp1 = "";
				while (i < nCount) {
					if (!"".equals(temp1)) {
						if ("(".equals(opt)) {
							nFun++;
							isFun = true;
						} else if (")".equals(opt)) {
							nFun--;
						}
					}
					if ((nFun > 0) || ((!isFun) && isValue(opt))) {
						temp1 += opt;
						nTemp++;
						opt = exp.substring(i + nTemp, i + nTemp + 1);
					} else {
						if (isFun) {
							temp1 += opt;
							nTemp++;
						}
						break;
					}
				}
				if ("".equals(temp1)) {
					temp = opt;
				} else {
					temp = temp1;
				}
				if (nTemp > 0) {
					i = i + nTemp - 1;
				}
				temp = temp.trim();

				if (isValue(temp)) {
					temp = getValue(temp);
					Values.push(temp);
					i++;
				} else {
					optIn = Opts.pop().toString();
					nIn = getOptPriorityIn(optIn);
					nOut = getOptPriorityOut(temp);
					if (nIn == nOut) {
						i++;
					} else if (nIn > nOut) {
						String ret = "";
						value1 = Values.pop().toString();
						value2 = Values.pop().toString();
						ret = String.valueOf(calValue(value2, optIn, value1));
						Values.push(ret);
					} else if (nIn < nOut) {
						Opts.push(optIn);
						Opts.push(temp);
						i++;
					}
				}
			}
			return Values.pop();
		} catch (Exception e) {
			e.printStackTrace();
			logger.error(e.getMessage());
		}
		return expression;
	}

	protected static int getOptPriorityOut(String opt) throws Exception {
		if ("+".equals(opt)) {
			return 1;
		} else if ("-".equals(opt)) {
			return 2;
		} else if ("*".equals(opt)) {
			return 5;
		} else if ("/".equals(opt)) {
			return 6;
		} else if ("%".equals(opt)) {
			return 7;
		} else if (">".equals(opt)) {
			return 11;
		} else if ("<".equals(opt)) {
			return 12;
		} else if ("]".equals(opt)) {
			return 13;
		} else if ("[".equals(opt)) {
			return 14;
		} else if ("!".equals(opt)) {
			return 15;
		} else if ("|".equals(opt)) {
			return 16;
		} else if ("&".equals(opt)) {
			return 23;
		} else if ("=".equals(opt)) {
			return 25;
		} else if ("#".equals(opt)) {
			return 0;
		} else if ("(".equals(opt)) {
			return 1000;
		} else if (")".equals(opt)) {
			return -1000;
		}
		throw new RuntimeException("运算符号" + opt + "非法!");
	}

	protected static int getOptPriorityIn(String opt) throws Exception {
		if ("+".equals(opt)) {
			return 3;
		} else if ("-".equals(opt)) {
			return 4;
		} else if ("*".equals(opt)) {
			return 8;
		} else if ("/".equals(opt)) {
			return 9;
		} else if ("%".equals(opt)) {
			return 10;
		} else if (">".equals(opt)) {
			return 17;
		} else if ("<".equals(opt)) {
			return 18;
		} else if ("]".equals(opt)) {
			return 19;
		} else if ("[".equals(opt)) {
			return 20;
		} else if ("!".equals(opt)) {
			return 21;
		} else if ("|".equals(opt)) {
			return 22;
		} else if ("&".equals(opt)) {
			return 24;
		} else if ("=".equals(opt)) {
			return 26;
		} else if ("(".equals(opt)) {
			return -1000;
		} else if (")".equals(opt)) {
			return 1000;
		} else if ("#".equals(opt)) {
			return 0;
		}
		throw new RuntimeException("运算符号:" + opt + "非法!");
	}

	protected static String getOPTS() {
		return OPTS;
	}

	protected static boolean isValue(String cValue) {
		String notValue = getOPTS() + "()";
		return notValue.indexOf(cValue) == -1;
	}

	protected static boolean isOpt(String value) {
		return getOPTS().indexOf(value) >= 0;
	}

	protected static double calValue(String value1, String opt, String value2) throws Exception {
		try {
			double dbValue1 = Double.valueOf(value1).doubleValue();
			double dbValue2 = Double.valueOf(value2).doubleValue();
			long lg = 0;
			if ("+".equals(opt)) {
				return dbValue1 + dbValue2;
			} else if ("-".equals(opt)) {
				return dbValue1 - dbValue2;
			} else if ("*".equals(opt)) {
				return dbValue1 * dbValue2;
			} else if ("/".equals(opt)) {
				return dbValue1 / dbValue2;
			} else if ("%".equals(opt)) {
				lg = (long) (dbValue1 / dbValue2);
				return dbValue1 - lg * dbValue2;
			} else if (">".equals(opt)) {
				if (dbValue1 > dbValue2) {
					return 1;
				} else {
					return 0;
				}
			} else if ("<".equals(opt)) {
				if (dbValue1 < dbValue2) {
					return 1;
				} else {
					return 0;
				}
			} else if ("]".equals(opt)) {
				if (dbValue1 >= dbValue2) {
					return 1;
				} else {
					return 0;
				}
			} else if ("[".equals(opt)) {
				if (dbValue1 <= dbValue2) {
					return 1;
				} else {
					return 0;
				}
			} else if ("!".equals(opt)) {
				if (dbValue1 != dbValue2) {
					return 1;
				} else {
					return 0;
				}
			} else if ("|".equals(opt)) {
				if (dbValue1 > 0 || dbValue2 > 0) {
					return 1;
				} else {
					return 0;
				}
			} else if ("&".equals(opt)) {
				if (dbValue1 > 0 && dbValue2 > 0) {
					return 1;
				} else {
					return 0;
				}
			} else if ("=".equals(opt)) {
				if (dbValue1 == dbValue2) {
					return 1;
				} else {
					return 0;
				}
			}
		} catch (Exception e) {
			throw new RuntimeException("参数:" + value1 + "和:" + value2 + "在进行:" + opt + "运算时非法!");
		}
		throw new RuntimeException("运算符号:" + opt + "非法!");
	}

	protected static String getValue(String oldValue) throws Exception {
		String reg = "^([a-zA-Z0-9_]+)\\(([a-zA-Z0-9_.()]+)\\)$";
		if (isFunctionCal(oldValue)) {
			Pattern p = Pattern.compile(reg);
			Matcher m = p.matcher(oldValue);
			m.find();
			return calFunction(m.group(1), m.group(2));
		}
		return oldValue;
	}

	protected static boolean isFunctionCal(String value) {
		String reg = "^([a-zA-Z0-9_]+)\\(([a-zA-Z0-9_.()]+)\\)$";
		return value.matches(reg);
	}

	protected static String calFunction(String function, String value) throws Exception {
		String lowerFun = function.toLowerCase();
		double db = 0;
		try {
			db = Double.valueOf(getValue(value)).doubleValue();
			if ("log".equals(lowerFun)) {
				return String.valueOf(Math.log(db));
			} else if ("square".equals(lowerFun)) {
				return String.valueOf(Math.pow(db, 2));
			} else if ("sqrt".equals(lowerFun)) {
				return String.valueOf(Math.sqrt(db));
			} else if ("sin".equals(lowerFun)) {
				return String.valueOf(Math.sin(db));
			} else if ("asin".equals(lowerFun)) {
				return String.valueOf(Math.asin(db));
			} else if ("cos".equals(lowerFun)) {
				return String.valueOf(Math.cos(db));
			} else if ("tan".equals(lowerFun)) {
				return String.valueOf(Math.tan(db));
			} else if ("atan".equals(lowerFun)) {
				return String.valueOf(Math.atan(db));
			} else if ("ceil".equals(lowerFun)) {
				return String.valueOf(Math.ceil(db));
			} else if ("exp".equals(lowerFun)) {
				return String.valueOf(Math.exp(db));
			}
		} catch (Exception e) {
			throw new RuntimeException("函数" + function + "参数:" + value + "非法!");
		}

		throw new RuntimeException("函数" + function + "不支持！");
	}
}
