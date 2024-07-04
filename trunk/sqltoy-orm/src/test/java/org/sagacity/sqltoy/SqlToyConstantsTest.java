package org.sagacity.sqltoy;

import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.DataType;
import org.sagacity.sqltoy.demo.vo.A1;
import org.sagacity.sqltoy.demo.vo.A2;
import org.sagacity.sqltoy.demo.vo.B1;
import org.sagacity.sqltoy.demo.vo.C1;
import org.sagacity.sqltoy.demo.vo.StaffInfoVO;
import org.sagacity.sqltoy.model.DateType;
import org.sagacity.sqltoy.model.MapKit;
import org.sagacity.sqltoy.utils.BeanUtil;
import org.sagacity.sqltoy.utils.MapperUtils;
import org.sagacity.sqltoy.utils.StringUtil;

import com.alibaba.fastjson2.JSON;

public class SqlToyConstantsTest {
	@Test
	public void testParseParams() {
		String template = "你好${ 姓名},请于${bizDate }来开会!";
		Pattern paramPattern = Pattern.compile(
				"\\$\\{\\s*[0-9a-zA-Z\u4e00-\u9fa5]+((\\.|\\_)[0-9a-zA-Z\u4e00-\u9fa5]+)*(\\[\\d*(\\,)?\\d*\\])?\\s*\\}");
		LinkedHashMap<String, String> paramsMap = new LinkedHashMap<String, String>();
		Matcher m = paramPattern.matcher(template);
		String group;
		while (m.find()) {
			group = m.group();
			// key as ${name} value:name
			paramsMap.put(group, group.substring(2, group.length() - 1).trim());
		}
		System.err.println(JSON.toJSONString(paramsMap));
	}

	@Test
	public void rtrim() {
		Pattern COMPARE_PATTERN = Pattern.compile("(!=|<>|\\^=|=|>=|<=)");
		System.err.println(StringUtil.matches("where id!=1", COMPARE_PATTERN));
		System.err.println(StringUtil.matches("where id<>1", COMPARE_PATTERN));
		System.err.println(StringUtil.matches("where id<=1", COMPARE_PATTERN));
		System.err.println(StringUtil.matches("where id=1", COMPARE_PATTERN));

		Map map = new HashMap<String, Object>();
		map.put("1", null);
		System.err.println("map=" + map.containsKey("1"));
		System.err.println(List.class.isAssignableFrom(ArrayList.class));
		System.err.println(Collection.class.isAssignableFrom(List.class));
		System.err.println(Map.class.isAssignableFrom(HashMap.class));
		String[] abc = new String[2];
		System.err.println(abc.getClass().isArray());
		System.err.println(DataType.class.isEnum());
		String idColumns = "ta.staff_id";
		System.err.println(idColumns.replaceAll("ta\\.", ""));
		System.err.println(idColumns.replaceAll("ta\\.", "tv."));
		System.err.println("[" + "v  by bn 1 ".replaceAll("\\s+", "") + "]");
	}

	@Test
	public void testBigIntger() {
		double var = 1000 / 12;
		System.err.println(var);
		MapKit.startOf(null, "").get();
		BigInteger a = new BigInteger("2993439899898779987777777777897777");
		System.err.println(a.toString());
		BigDecimal b = new BigDecimal("2993439899898779987777777777897777");
		System.err.println(b.toString());
		System.err.println(String.class.getTypeName());
		System.err.println(int.class.getTypeName());
		System.err.println(Long.parseLong("20211111102134"));
	}

	@Test
	public void testBeanInfo() {
		Class classType = StaffInfoVO.class;
		System.err.println(StaffInfoVO.class.getName());
		while (!classType.equals(Object.class)) {
			System.err.println(classType.getName());
			classType = classType.getSuperclass();
		}

		System.err.println(new BigDecimal(10).multiply(new BigDecimal(3)).add(new BigDecimal(42))
				.divide(new BigDecimal(4), 3, RoundingMode.HALF_UP));
		Double divData = 398d;
		Double divedData = 24.1d;
		double multiply = 1.2;
		System.err.println(((divData - divedData) * multiply) / divedData);
		BigDecimal value = new BigDecimal(((divData - divedData) * multiply) / divedData).setScale(3,
				RoundingMode.FLOOR);

		System.err.println(value);

		boolean a = Map.class.isAssignableFrom(HashMap.class);
		boolean b = HashMap.class.isAssignableFrom(Map.class);
		boolean c = HashMap.class.isAssignableFrom(Hashtable.class);
		boolean d = Map.class.isAssignableFrom(Map.class);
		System.out.println("a---" + a + "-----b----" + b + "-------c-----" + c + "-------d-----" + d);

	}

	@Test
	public void testBeanInfo1() {
		String sql = SqlToyConstants.MERGE_ALIAS_ON + " ta.name=tv.name)";
		int onTenantIndex = sql.indexOf(SqlToyConstants.MERGE_ALIAS_ON);
		int end = onTenantIndex + SqlToyConstants.MERGE_ALIAS_ON.length();
		String aliasName = sql.substring(end, sql.indexOf(".", end)).trim();
		System.err.println("[" + aliasName + "]");
	}

	@Test
	public void testBeanInfo2() throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		DateType dat = DateType.FIRST_OF_MONTH;

		System.err.println(BeanUtil.getEnumValue(dat));
		String key = "FIRST_OF_WEEK";
		DateType dat1 = (DateType) BeanUtil.newEnumInstance(key, DateType.class);
		System.err.println(JSON.toJSONString(dat1));
	}

	public static void main(String[] args) {
		List<C1> c1List = new ArrayList();
		c1List.add(new C1() {
			{
				setIntNum(3001);
				setIntegerNum(3002);
				setStr("i am c1 string. 。。。。");
				setDecimal(BigDecimal.ONE);
				setShortNum(Short.valueOf("399"));
				setShortTNum(Short.valueOf("398"));
				setDate(new Date());
			}
		});
		c1List.add(new C1() {
			{
				setIntNum(3003);
				setIntegerNum(3004);
				setStr("i am c2 string. 。。。。");
				setDecimal(BigDecimal.ONE);
				setShortNum(Short.valueOf("389"));
				setShortTNum(Short.valueOf("388"));
				setDate(new Date());
			}
		});
		c1List.add(new C1() {
			{
				setIntNum(3005);
				setIntegerNum(3006);
				setStr("i am c3 string. 。。。。");
				setDecimal(BigDecimal.ONE);
				setShortNum(Short.valueOf("379"));
				setShortTNum(Short.valueOf("378"));
				setDate(new Date());
			}
		});
		A1 aa = new A1() {
			{
				setIntNum(1001);
				setIntegerNum(1002);
				setStr("i am a string. 。。。。");
				setDecimal(BigDecimal.TEN);
				setShortNum(Short.valueOf("199"));
				setShortTNum(Short.valueOf("198"));
				setDate(new Date());
				setB(new B1() {
					{
						setIntNum(2001);
						setIntegerNum(2002);
						setStr("i am b string. 。。。。");
						setDecimal(BigDecimal.ZERO);
						setShortNum(Short.valueOf("299"));
						setShortTNum(Short.valueOf("298"));
						setDate(new Date());
					}
				});
				setC(c1List);
			}
		};

		A2 a2 = MapperUtils.map(aa, A2.class);
		System.out.println(JSON.toJSONString(a2));
	}
}
