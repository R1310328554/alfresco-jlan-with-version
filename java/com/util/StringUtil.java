package com.util;


import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
//import org.artofsolving.jodconverter.document.DocumentFormat;
//import org.jsoup.Jsoup;
//import org.jsoup.nodes.Document;
//
//import com.log4ic.DocViewer;

import sun.misc.BASE64Decoder;

public class StringUtil {
	private static Logger logger = Logger.getRootLogger();
	private static final Pattern reUnicode = Pattern.compile("\\\\u([0-9a-zA-Z]{4})");
	private static final String regEx_html = "<[^>]*>";// 定义html标签的正则表达式
	private static final String lineRegex = "[\n|\r|\n\r|\n\r]+";//定义换行正则表达式
	private static final String fileNameRegex = "[^\\s\\\\/:\\*\\?\\\"<>\\|](\\x20|[^\\s\\\\/:\\*\\?\\\"<>\\|])*[^\\s\\\\/:\\*\\?\\\"<>\\|\\.]$";//文件名是否包含非法字符
	private static final String isNumberRegex = "^[0-9]*|([0-9]*.[0-9]*)$";//数字
	private static final String isNumericRegex = "[0-9]*";//正整 数
	private static final String specialCharRegex = "[`~!@#$%^&*()+=|{}'：；'，\\[\\].<>/?~！@#￥%&amp;*（）——+|{}【】｀；：”“’。，、？]";//特殊符号
	private static final String quotationRegex="[\"|“|”|”|“|\u201c|\u201d|\u2018|\u2019]+";//引号和引号的uncode编码
	private static final String jsonerrRegex = "[\u001d|\u2014|\u2016|\u2026|\u203b|\u2030|\u2032|\u2033]+";//客户端json解析会报错的字符
	private static final String unicodeRegex = "(\\\\u(\\p{XDigit}{4}))";
	/**
	 * 判断字符为中文还是英文
	 * 
	 * @param c
	 * @return
	 */
	public static boolean isLetter(char c) {
		int k = 0x80;
		return c / k == 0 ? true : false;
	}

	/**
	 * 返回字符串的长度,中文算2位
	 * 
	 * @param s
	 * @return
	 */
	public static int length(String s) {
		if (s == null)
			return 0;
		char[] c = s.toCharArray();
		int len = 0;
		for (int i = 0; i < c.length; i++) {
			len++;
			if (!isLetter(c[i])) {
				len++;
			}
		}
		return len;
	}
	
	/**
	 * 合并字符（但只合并不重复的字符）
	 * @param srcStr
	 * @param newStr
	 * @return
	 */
	public static String combineChar(String srcStr,String newStr){
		char[] c = newStr.toCharArray();
		for(int i=0;i<c.length;i++)
		{
			if(srcStr.indexOf(c[i])==-1)
			{
				srcStr +=c[i];
			}
		}
		return srcStr;
	}

	/**
	 * 验证邮箱
	 * 
	 * @param strIn
	 * @return
	 */
	public static boolean isValidEmail(String strIn) {
		if(null == strIn) return false;
		String ps = "^([\\w-\\.\u4E00-\uFA29]+)@[\\w-.\u4E00-\uFA29]+(\\.?[a-zA-Z\u4E00-\uFA29]{2,4}$)";
		Pattern p = Pattern.compile(ps);
		Matcher m = p.matcher(strIn);
		if (m.matches()) {
			return true;
		} else {
			return false;
		}
	}
	
	/**
	 * 验证发送的邮箱 格式可以为"hello<test@domain.com>"
	 * @param strIn
	 * @return
	 */
	public static boolean isValidSendEmail(String strIn) {
		if(null == strIn) return false;
		if(strIn.contains("<")){
			String[] toary = strIn.split("[<]");
			strIn = "<"+toary[1];
		}
		String ps = "^([<\\w-\\.\u4E00-\uFA29]+)@[\\w-.\u4E00-\uFA29]+(\\.?[a-zA-Z\u4E00-\uFA29>]{2,5}$)";
		Pattern p = Pattern.compile(ps);
		Matcher m = p.matcher(strIn);
		if (m.matches()) {
			return true;
		} else {
			return false;
		}
	}
	
	/**
	 * 验证文件名是否合法
	 * @param fileName
	 * @return
	 */
	public static boolean isValidFileName(String fileName)
	{
		if(fileName != null && fileName.length()<255)
		{
			return fileName.matches(fileNameRegex);
		}
		else
		{
			return false;
		}
	}
	
	/**
	 * 切割制定长度的字符串
	 * 
	 * @param origin
	 * @param len
	 * @return
	 */
	public static String subString(String origin, int len) {
		len = len * 2;
		if (origin == null || origin.equals("") || len < 1)
			return "";
		byte[] strByte = new byte[len];
		if (len > StringUtil.length(origin)) {
			return origin;
		}
		System.arraycopy(origin.getBytes(), 0, strByte, 0, len);
		int count = 0;
		for (int i = 0; i < len; i++) {
			int value = (int) strByte[i];
			if (value < 0) {
				count++;
			}
		}
		if (count % 2 != 0) {
			len = (len == 1) ? ++len : --len;
		}
		return new String(strByte, 0, len);
	}

	/**
	 * 对值进行过滤
	 */
	public static String GetDefaultValue(String input) {
		if (input == null)
			input = "";
		else {
			input = input.replaceAll("'", "''");
			// input=input.replaceAll("<", "<");
			// input=input.replaceAll(",", "，");
			input = input.trim();
			// input=input.replaceAll(" ", " ");
			// input=input.replaceAll("<br />", "<br/>");
		}
		return input;
	}

	/**
	 * 字符串转换成 整型数组
	 */
	public static int[] str2IntArr(String str) {
		String[] strArr = str.split(",");
		int[] result = new int[strArr.length];
		for (int i = 0; i < strArr.length; i++) {
			result[i] = Integer.parseInt(strArr[i]);
		}
		return result;
	}

	/**
	 * 判断是否为数字
	 * 
	 * @return 数字true 其他为falseset
	 */
	public static boolean isNumber(String str) {
		Pattern pattern = Pattern.compile(isNumberRegex);
		if (str == null || "".equals(str))
			return false;
		Matcher isn = pattern.matcher(str);
		if (!isn.matches()) {
			return false;
		}
		return true;
	}

	/**
	 * 判断字符串是否是正整数数字
	 * 
	 * @return 是数字返回true 否则返回false
	 */
	public static boolean isNumeric(String str) {
		Pattern pattern = Pattern.compile(isNumericRegex);
		if (str == null || str.equals(""))
			return false;
		Matcher isNum = pattern.matcher(str);
		if (!isNum.matches()) {
			return false;
		}
		return true;
	}

	// 编码
	public static String getBASE64(String s) {
		if (s == null) {
			return null;
		}
		return (new sun.misc.BASE64Encoder()).encode(s.getBytes());
	}

	// 解码
	public static String getFromBASE64(String s) {
		if (s == null)
			return null;
		BASE64Decoder decoder = new BASE64Decoder();
		try {
			byte[] b = decoder.decodeBuffer(s);
			return new String(b);
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * 得到客户端 IP
	 * 
	 * @param request
	 * @return
	 */
	public static String getIpAddr(HttpServletRequest request) {
		String ip = request.getHeader("x-forwarded-for");
		if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
			ip = request.getHeader("Proxy-Client-IP");
		}
		if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
			ip = request.getHeader("WL-Proxy-Client-IP");
		}
		if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
			ip = request.getRemoteAddr();
		}
		return ip;
	}
/**
	 * 获取本机IP
	 * @return
	 * @throws SocketException
	 */
	@SuppressWarnings({ "unchecked" })
	public static String getlocalIP(){
		Enumeration allNetInterfaces;
		String ipaddr = "";
		try {
			allNetInterfaces = NetworkInterface.getNetworkInterfaces();
			InetAddress ip = null;
			while (allNetInterfaces.hasMoreElements())
			{
				NetworkInterface netInterface = (NetworkInterface) allNetInterfaces.nextElement();
				Enumeration addresses = netInterface.getInetAddresses();
				while (addresses.hasMoreElements())
				{
					ip = (InetAddress) addresses.nextElement();
					if (ip != null && ip instanceof Inet4Address)
					{
						ipaddr = ip.getHostAddress();
					}
				}
			}
		} catch (SocketException e) {
			e.printStackTrace();
		}
		return ipaddr;
	}

	/**
	 * 验证是否为图片
	 * 
	 * @param fileName
	 * @return true|false
	 */
	public static boolean isImage(String fileName) {
		if (null != fileName
				&& (fileName.toLowerCase().endsWith(".jpg")
						|| fileName.toLowerCase().endsWith(".gif") || fileName
						.toLowerCase().endsWith(".png"))) {
			return true;
		}
		return false;
	}

	/**
	 * 过滤特殊字符
	 */
	public static String filterStr(String str) {
		Pattern p = Pattern.compile(specialCharRegex);
		Matcher m = p.matcher(str);
		String newstr = m.replaceAll("").trim();
		return newstr;
	}

	/**
	 * 过滤html标签
	 * @param inputString
	 * @return
	 */
	public static String filterChars(String inputString) {
		if (inputString == null || "".equals(inputString)) {
			return "";
		}
		String htmlStr = inputString; // 含html标签的字符串
		String textStr = "";
		java.util.regex.Pattern p;
		java.util.regex.Matcher m;
		p = Pattern.compile(regEx_html, Pattern.CASE_INSENSITIVE); // [,Pattern.CASE_INSENSITIVE]参数忽略大小写
		m = p.matcher(htmlStr);
		htmlStr = m.replaceAll(""); // 过滤html标签
		
		
		p = Pattern.compile(lineRegex);
		m = p.matcher(htmlStr);
		textStr = m.replaceAll("\n");
		return textStr;// 返回文本字符串
	}
	
	/**
	 * 过滤空格换行
	 * @param inputString
	 * @return
	 */
	public static String filterSpace(String inputString) {
		if (inputString == null || "".equals(inputString)) {
			return "";
		}
		java.util.regex.Pattern p;
		java.util.regex.Matcher m;
//		String regEx = "[&nbsp;| |　||\t]+";
		String regEx = "[\\s*|\t]+";
		p = Pattern.compile(regEx);
		m = p.matcher(inputString);
		inputString = m.replaceAll(" ");
		regEx = "[\n|\r|\n\r|\n\r]+";
		p = Pattern.compile(regEx);
		m = p.matcher(inputString);
		inputString = m.replaceAll(" ");
		return inputString;// 返回文本字符串
	}

	/**
	 * 过滤不符合JSON返回的字符
	 * @param inputString
	 * @return
	 */
	public static String filterJsonString(String inputString)
	{
		String outString = "";
		if(null != inputString)
		{
			inputString = UnicodeToString(inputString);
			Pattern p = Pattern.compile(quotationRegex);      
			Matcher m = p.matcher(inputString); 
			outString = m.replaceAll("'");
			p = Pattern.compile(jsonerrRegex);      
			m = p.matcher(outString); 
			outString = m.replaceAll("");
			try{
				outString = StringUtil.decodeUnicode(outString);
			}catch(Exception e)
			{
				logger.equals("StringUtil.decodeUnicode; outString="+outString+" ;"+e.getMessage());
			}			
		}
		return outString;
	}
	
	public static String UnicodeToString(String str) {
        Pattern pattern = Pattern.compile(unicodeRegex);    
        Matcher matcher = pattern.matcher(str);
        char ch;
        while (matcher.find()) {
            ch = (char) Integer.parseInt(matcher.group(2), 16);
            str = str.replace(matcher.group(1), ch + "");    
        }
        return str;
    }
	
	/**
	 * Unicode转义(\\uXXXX)的解码
	 * @return
	 */
	public static String decodeUnicode(String s) {
	    Matcher m = reUnicode.matcher(s);
	    StringBuffer sb = new StringBuffer(s.length());
	    while (m.find()) {
	        m.appendReplacement(sb,
	                Character.toString((char) Integer.parseInt(m.group(1), 16)));
	    }
	    m.appendTail(sb);
	    return sb.toString();
	}
	
	
	
	/**
	 * 替换mysql标识 中单双引号的特殊使用 以及mysql常见的转移字符
	 * @return
	 */
	public static String filterSymbols(String str)
	{
		if (null == str)
			return null;
		if (str.indexOf("'") >= 0)  
            str = str.replaceAll("'", "''");  
        if (str.indexOf("\"") >= 0)  
            str = str.replaceAll("\"", "\\\\\"");
		return str;
	}
	
	/**
	 * 日期转字符
	 * @param d
	 * @return
	 */
	@SuppressWarnings("deprecation")
	public static String dataToString(Date d) {
		String datestr = "";
		Date date = new Date();
		SimpleDateFormat sf = null;
		if (null != d) {
			if ((date.getYear() + 1900) == (d.getYear() + 1900)) {// 如果是当年就不显示年
				sf = new SimpleDateFormat("MM-dd HH:mm:ss");
			} else {
				sf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
			}
			datestr = sf.format(d);
		}
		return datestr;
	}

	/**
	 * 半角转全角
	 * 
	 * @param input
	 * @return 全角字符串
	 */
	public static String toSBC(String str) {
		char c[] = str.toCharArray();
		for (int i = 0; i < c.length; i++) {
			if (c[i] == ' ') {
				c[i] = '\u3000'; // 采用十六进制,相当于十进制的12288

			} else if (c[i] < '\177') { // 采用八进制,相当于十进制的127
				c[i] = (char) (c[i] + 65248);

			}
		}
		return new String(c);
	}

	/**
	 * 全角转半角
	 * 
	 * @param input
	 *            String.
	 * @return 半角字符串
	 */
	public static String toDBC(String str) {
		char c[] = str.toCharArray();
		for (int i = 0; i < c.length; i++) {
			if (c[i] == '\u3000') {
				c[i] = ' ';
			} else if (c[i] > '\uFF00' && c[i] < '\uFF5F') {
				c[i] = (char) (c[i] - 65248);

			}
		}
		String returnString = new String(c);
		return returnString;
	}
	
	/**
	 * 返回UTF-8编码后的 字符串
	 * 
	 * @param str
	 * @return
	 */
	public static String encodeString(String str) {
		String newStr = str;//先等于原值，防止下面出异常时为空情况
		if (null != str && !"".equals(str)) {
			try {
				newStr = URLEncoder.encode(str, "UTF-8");
				//newStr = URLEncoder.encode(newStr, "UTF-8");//改为转码一次
			} catch (UnsupportedEncodingException e) {
				logger.error("encodeString转码失败,str="+str+"; "+e.getMessage());
			}
		}
		return newStr;
	}
	
	public static String URLEncoder(String str) {
		String newStr = str;//先等于原值，防止下面出异常时为空情况
		if (null != str && !"".equals(str)) {
			try {
				newStr = URLEncoder.encode(str, "UTF-8");
			} catch (UnsupportedEncodingException e) {
				logger.error("URLEncoder转码失败,str="+str+"; "+e.getMessage());
			}
		}
		return newStr;
	}
	
	public static String URLDecoder(String str){
		/*
		 1、对于"a-z", "A-Z", "0-9", ".", "-", "*", "_"，encode/decode前后不产生任何变化，所以实际上无需判断；
		2、" "被转换成"+"，如果原字符串本来就含有"+"，上述方法无效;
		3、其他的字符，根据不同的字符集先被转换成一到多个byte，然后每个byte被表示成类似"%xy"的字符串，其中xy是该byte值的16进制表示形式。所以对于原字符串本来含有"%"或者"%xy"，上述方法也无效，对于"%xy"，如果xy为非法字符，则会抛出IllegalArgumentException。
所以如果需要得到精确的结果，需要自己另加额外的控制标志位。
 		即，原字符包含+号不解码, 包含%号可能转码异常
		 */
		String newStr = str;//先等于原值，防止下面出异常时为空情况
		if (null != str && !"".equals(str) && str.indexOf("+")==-1)
		{
			try
			{
				newStr = URLDecoder.decode(str, "UTF-8");
			}
			catch (Exception e)
			{
				logger.error("URLDecoder转码失败,str="+str+"; "+e.getMessage());
			}
		}
		return newStr;
	}

	/**
	 * 解密UTF-8编码后的 字符串
	 * 
	 * @param str
	 * @return
	 */
	public static String decodeString(String str) {
		/*
		 1、对于"a-z", "A-Z", "0-9", ".", "-", "*", "_"，encode/decode前后不产生任何变化，所以实际上无需判断；
		2、" "被转换成"+"，如果原字符串本来就含有"+"，上述方法无效;
		3、其他的字符，根据不同的字符集先被转换成一到多个byte，然后每个byte被表示成类似"%xy"的字符串，其中xy是该byte值的16进制表示形式。所以对于原字符串本来含有"%"或者"%xy"，上述方法也无效，对于"%xy"，如果xy为非法字符，则会抛出IllegalArgumentException。
所以如果需要得到精确的结果，需要自己另加额外的控制标志位。
		即，原字符包含+号不解码, 包含%号可能转码异常
		 */
//		String newStr = str;//先等于原值，防止下面出异常时为空情况
		if (null != str && !"".equals(str)&& str.indexOf("+")==-1) {
			try {
				str = URLDecoder.decode(str, "UTF-8");
				try{
//					newStr = URLDecoder.decode(newStr, "UTF-8");//改为转码一次，转两次存在部分问题
					if(str.length()>3&& str.indexOf("+")==-1)
					{
						String startStr = str.substring(0,4);
						if(startStr.startsWith("%")&&startStr.endsWith("%"))//如果是编码的字符，则再解码一次
						{
							str = URLDecoder.decode(str, "UTF-8");//改为转码一次，转两次存在部分问题
						}
					}
				}catch (Exception e) {
					logger.error("decodeString转码失败(第二遍),str="+str+"; "+e.getMessage());
				}
			} catch (Exception e) {
				logger.error("decodeString转码失败,str="+str+"; "+e.getMessage());
			}
		}
		return str;
	}

	/**
	 * 从URI截取
	 * @param div
	 * @param div2
	 * @param url
	 * @return
	 */
	public static String getStrFromURL(String div, String div2, String url) {
		String ret = "";
		if (div == null || div2 == null || url == null) {
			return ret;
		}
		int idx = url.lastIndexOf(div);
		if (idx > -1) {
			ret = url.substring(div.length());
			int idx2 = ret.indexOf(div2);
			if (idx2 > -1) {
				ret = ret.substring(0, idx2);
			}
		}
		return ret;
	}
	/**
	 * 生成随机数
	 * 可用于生成密码等
	 * @param len 字符长度
	 * @return
	 */
	public static String createRandomNum(int len)
	{
		// 取随机产生的认证码(len位数字)
		String sRand = "";
		// 生成随机类
		Random random = new Random();
		for (int i = 0; i < len; i++) {
			String rand = String.valueOf(random.nextInt(10));
			sRand += rand;
		}
		return sRand;
	}
	/**
	 * 获得年
	 * @return
	 */
	public static String getYear()
	{
		java.util.Calendar c = java.util.Calendar.getInstance();
		java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy");
		return f.format(c.getTime());
	}
	/**
	 * 获得月
	 * @return
	 */
	public static String getMouth()
	{
		java.util.Calendar c = java.util.Calendar.getInstance();
		java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("MM");
		return f.format(c.getTime());
	}
	/**
	 * 获得月
	 * @return
	 */
	public static String getDay()
	{
		java.util.Calendar c = java.util.Calendar.getInstance();
		java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("dd");
		return f.format(c.getTime());
	}
	/**
	 * 返回“年/月/日”格式路径
	 * @return
	 */
	public static String getDatePath()
	{
		java.util.Calendar c = java.util.Calendar.getInstance();
		java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy/MM/dd");
		return f.format(c.getTime());
	}
	
	public static int getDaysBetween(java.util.Calendar d1, java.util.Calendar d2) {
		if (d1.after(d2)) { // swap dates so that d1 is start and d2 is end
		java.util.Calendar swap = d1;
		d1 = d2;
		d2 = swap;
		}
		int days = d2.get(java.util.Calendar.DAY_OF_YEAR)
		- d1.get(java.util.Calendar.DAY_OF_YEAR);
		int y2 = d2.get(java.util.Calendar.YEAR);
		if (d1.get(java.util.Calendar.YEAR) != y2) {
		d1 = (java.util.Calendar) d1.clone();
		do {
		days = d1.getActualMaximum(java.util.Calendar.DAY_OF_YEAR);
		d1.add(java.util.Calendar.YEAR, 1);
		} while (d1.get(java.util.Calendar.YEAR) != y2);
		}
		return days;
	}
	
	/**
	 * 格式化显示时间
	 * @param date
	 * @return
	 */
	public static String formatDate(Date date)
	{
		if(null ==date)
		{
			return "";
		}
		Date nowDate = new Date();
		Calendar oldCalendar = Calendar.getInstance();
		Calendar nowCalendar = Calendar.getInstance();
		oldCalendar.setTime(date);
		nowCalendar.setTime(nowDate);
		int day=getDaysBetween(oldCalendar,nowCalendar);
		Long nowTime=nowDate.getTime();
		Long oldTime=date.getTime();
		Long time=nowTime-oldTime;
		if(time<60*1000)//小于1分钟
		{
			return "刚刚";
		}
		else if(time<30*60*1000)//小于30分钟
		{
			return (long)time/(60*1000)+"分钟前";
		}
//		else if(time<6*60*60*1000)//小于6小时
//		{
//			return (long)time/(60*60*1000)+"小时前";
//		}
		else
		{
			if(day==0)
			{
				return "今天"+new SimpleDateFormat("HH:mm").format(date);
			}
			else if(day==1)
			{
				return "昨天"+new SimpleDateFormat("HH:mm").format(date);
			}
			else if(day==2)
			{
				return "前天"+new SimpleDateFormat("HH:mm").format(date);
			}
			else
			{
				return new SimpleDateFormat("yyyy-MM-dd HH:mm").format(date); 
			}
		}
	}
	
	/**
	 * 根据毫秒数转换成时间
	 * @param date
	 * @return
	 */
	public static Date getDate(long date)
	{
		return new Date(date);
	}
	
	/**
	 * 获得时分秒毫秒，可作为文件名
	 * @return
	 */
	public static String getHmss()
	{
		java.util.Calendar c = java.util.Calendar.getInstance();
		java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("HHmmssSSS");
		return f.format(c.getTime());
	}
	/**
	 * 是为空
	 * @param str
	 * @return
	 */
	public static boolean isEmpty(String str)
	{
		boolean flag = false;
		if(null != str)
		{
			if(str.trim().length()==0)
			{
				flag = true;
			}
		}
		else 
		{
			flag = true;
		}
		return flag;
	}
	
	/**
	 * 省略显示字符串
	 * @param origin 原字符串
	 * @param len 显示的长度
	 * @param omitChar 省略部分以什么方式显示，如...
	 */
	public static String omitString(String origin, int len,String omitChar)
	{
		if(null != origin)
		{
			if(null == omitChar || "".equals(omitChar))
			{
				omitChar = "……";
			}
			len = len << 1;//左移一位，相当于len * 2
			int subLength = length(omitChar) + len;//截取后的长度=要截取的长度+省略标识符
			//只有当截取后的长度小于原字符长度时才进行截取操作
			if(subLength < length(origin))
			{
				//汉字 gbk：2个字符  utf-8：3个字符
				//所以最好是指定编码格式
				String encode = "gbk";
				//截取字符串
				byte[] strByte = new byte[len];
				try {
					System.arraycopy(origin.getBytes(encode), 0, strByte, 0, len);
					int count = 0;
					for (int i = 0; i < len; i++) {
						int value = (int) strByte[i];
						if (value < 0) {
							count++;
						}
					}
					if (count % 2 != 0) {
						len = (len == 1) ? ++len : --len;
					}
					return (new String(strByte,encode)+omitChar);
				} 
				catch (UnsupportedEncodingException e) 
				{
					e.printStackTrace();
				}
			}
		}
		return origin;
	}
	
	/**
	 * 判断是不是转换所支持的文件
	 * @param filename
	 * @return
	 */
/*	public static boolean isSupport(String filename)
	{
		List<DocumentFormat> allSupport = DocViewer.getAllSupport();
		StringBuffer allSupportStr = new StringBuffer();
	    for (DocumentFormat format : allSupport) {
	        allSupportStr.append(format.getExtension());
	        allSupportStr.append(",");
	    }
	    int i=allSupportStr.indexOf(DiskUtil.getExt(filename)+",");
	    if(i>=0)
	    {
	    	return true;
	    }
	    return false;
	}
	*/
    //默认除法运算精度 

    private   static   final   int   DEF_DIV_SCALE   =   10; 



    /** 

      *   提供精确的加法运算。 

      *   @param   v1   被加数 

      *   @param   v2   加数 

      *   @return   两个参数的和 

      */ 

    public   static   double   add(double   v1,double   v2){ 

            BigDecimal   b1   =   new   BigDecimal(Double.toString(v1)); 

            BigDecimal   b2   =   new   BigDecimal(Double.toString(v2)); 

            return   b1.add(b2).doubleValue(); 

    } 



    /** 

      *   提供精确的减法运算。 

      *   @param   v1   被减数 

      *   @param   v2   减数 

      *   @return   两个参数的差 

      */ 

    public   static   double   sub(double   v1,double   v2){ 

            BigDecimal   b1   =   new   BigDecimal(Double.toString(v1)); 

            BigDecimal   b2   =   new   BigDecimal(Double.toString(v2)); 

            return   b1.subtract(b2).doubleValue(); 

    } 



    /** 

      *   提供精确的乘法运算。 

      *   @param   v1   被乘数 

      *   @param   v2   乘数 

      *   @return   两个参数的积 

      */ 

    public   static   double   mul(double   v1,double   v2){ 

            BigDecimal   b1   =   new   BigDecimal(Double.toString(v1)); 

            BigDecimal   b2   =   new   BigDecimal(Double.toString(v2)); 

            return   b1.multiply(b2).doubleValue(); 

    } 



    /** 

      *   提供（相对）精确的除法运算，当发生除不尽的情况时，精确到 

      *   小数点以后10位，以后的数字四舍五入。 

      *   @param   v1   被除数 

      *   @param   v2   除数 

      *   @return   两个参数的商 

      */ 

    public   static   double   div(double   v1,double   v2){ 

            return   div(v1,v2,DEF_DIV_SCALE); 

    } 



    /** 

      *   提供（相对）精确的除法运算。当发生除不尽的情况时，由scale参数指 

      *   定精度，以后的数字四舍五入。 

      *   @param   v1   被除数 

      *   @param   v2   除数 

      *   @param   scale   表示表示需要精确到小数点以后几位。 

      *   @return   两个参数的商 

      */ 

    public   static   double   div(double   v1,double   v2,int   scale){ 

            if(scale <0){ 

                    throw   new   IllegalArgumentException( 

                            "The   scale   must   be   a   positive   integer   or   zero "); 

            } 

            BigDecimal   b1   =   new   BigDecimal(Double.toString(v1)); 

            BigDecimal   b2   =   new   BigDecimal(Double.toString(v2)); 

            return   b1.divide(b2,scale,BigDecimal.ROUND_HALF_UP).doubleValue(); 

    } 



    /** 

      *   提供精确的小数位四舍五入处理。 

      *   @param   v   需要四舍五入的数字 

      *   @param   scale   小数点后保留几位 

      *   @return   四舍五入后的结果 

      */ 

    public   static   double   round(double   v,int   scale){ 

            if(scale <0){ 

                    throw   new   IllegalArgumentException( 

                            "The   scale   must   be   a   positive   integer   or   zero "); 

            } 

            BigDecimal   b   =   new   BigDecimal(Double.toString(v)); 

            BigDecimal   one   =   new   BigDecimal( "1"); 

            return   b.divide(one,scale,BigDecimal.ROUND_HALF_UP).doubleValue(); 

    }
    
    /**
     * 小数转换成百分数
     * @param v
     * @return
     */
    public static String modv(double v){
    	NumberFormat num = NumberFormat.getPercentInstance(); 
    	num.setMaximumIntegerDigits(3); 
    	num.setMaximumFractionDigits(2); 
    	return num.format(v);
    }
    
    /**
     * 输入文件BT大小，转换成K,M,G
     * @param filebt
     * @return
     */
    public static String getFileSize(int filebt){
    	String restr = "";
    	double v1 = (double)filebt;
    	double filekb = StringUtil.div(v1, 1024, 2);
    	restr = filekb+"K";
    	if(filekb>=1024){
    		double filemb = StringUtil.div(filekb, 1024, 2);
    		restr = filemb+"M";
    		if(filemb>=1024){
    			double filegb = StringUtil.div(filemb, 1024, 2);
    			restr = filegb+"G";
    		}
    	}
    	return restr;
    }
    
    /**
     * 数组的join方法
     * @param o
     * @param flag
     * @return
     */
    public static String join( Object[] o , String flag ){
    	StringBuffer str_buff = new StringBuffer();
     
    	for(int i=0 , len=o.length ; i<len ; i++){
    		str_buff.append( String.valueOf( o[i] ) );
    		if(i<len-1)str_buff.append( flag );
    	}

    	return str_buff.toString(); 
    }
    
    public static String joinInt( Object[] o , String flag ){
    	StringBuffer str_buff = new StringBuffer();
     
    	for(int i=0 , len=o.length ; i<len ; i++){
    		str_buff.append( Integer.parseInt(String.valueOf( o[i] )) );
    		if(i<len-1)str_buff.append( flag );
    	}

    	return str_buff.toString(); 
    }
    
    public static String join( int[] o , String flag ){
    	StringBuffer str_buff = new StringBuffer();
     
    	for(int i=0 , len=o.length ; i<len ; i++){
    		str_buff.append(o[i]);
    		if(i<len-1)str_buff.append( flag );
    	}

    	return str_buff.toString(); 
    }
    
    /**
     * 判断字符串的编码
     *
     * @param str
     * @return
     */  
   public static String getEncoding(String str) {   
        String encode = "GB2312";   
       try {   
           if (str.equals(new String(str.getBytes(encode), encode))) {   
                String s = encode;   
               return s;   
            }   
        } catch (Exception exception) {   
        }   
        encode = "ISO-8859-1";   
       try {   
           if (str.equals(new String(str.getBytes(encode), encode))) {   
                String s1 = encode;   
               return s1;   
            }   
        } catch (Exception exception1) {   
        }   
        encode = "UTF-8";   
       try {   
           if (str.equals(new String(str.getBytes(encode), encode))) {   
                String s2 = encode;   
               return s2;   
            }   
        } catch (Exception exception2) {   
        }   
        encode = "GBK";   
       try {   
           if (str.equals(new String(str.getBytes(encode), encode))) {   
                String s3 = encode;   
               return s3;   
            }   
        } catch (Exception exception3) {   
        }   
       return "";   
    }
    
    /**
     * 从HTML中获取文本
     * @param strHtml
     * @return
     */
/*    public static String StripHT(String strHtml){
    	String strOutput = "";
    	if(null != strHtml && !"".equals(strHtml)){
    		Document doc = Jsoup.parse(strHtml);
    		strOutput = doc.body().text();
    	}
       return strOutput;
    }*/
    
    /**
     * 是否为ISO-8859-1类似编码
     * @param str
     * @return
     */
    public static boolean isISOCode(String str){
    	if(null != str && !"".equals(str)){
    		return java.nio.charset.Charset.forName("ISO-8859-1").newEncoder().canEncode(str);
    	}
    	return false;
    }
    
    /**
     * 转成UTF-8
     * @param str
     * @return
     */
    public static String decodeUTF8(String str){
    	if(null != str && !"".equals(str)){
    		try{
    			str =  decodeString(new String(str.getBytes("ISO-8859-1"),"GBK"));
    		}catch(Exception e){
    			e.printStackTrace();
    		}
    	}
    	return str;
    }
    
    /**
     * 用于处理SQL查询IN语句中的值
     * @param ids
     * @return
     */
/*    public static String dealSQLInStatementId(String ids){
    	if(StringUtils.isNotEmpty(ids))
    	{
    		Pattern p = Pattern.compile("(^\\D+)|(\\D+$)");
			Matcher m = p.matcher(ids);
			if(m.find())
			{
				ids = m.replaceAll("");
			}
			p = Pattern.compile("\\D+");
			m = p.matcher(ids);
			if(m.find())
			{
				ids = m.replaceAll(",");
			}
    	}
    	return ids;
    }*/
    
    /**
     * 将字符串处理成只包含字母
     * @param str
     * @return
     */
    public static String getPureLettersString(String str){
    	if(str != null && !"".equals(str))
    	{
    		String reg = "[^a-zA-Z]";
    		Pattern p = Pattern.compile(reg);
    		Matcher m = p.matcher(str);
    		if(m.find())
    		{
    			str = m.replaceAll("");
    		}
    	}
    	return str;
    }
    
    /**
     * 检查是否是LFS安全控件支持的浏览器
     * @param browser
     * @return
     */
    public static boolean isSupportBrowser(String browser) {
    	if("IE".equals(browser) || "Firefox".equals(browser) || "Chrome".equals(browser)){//IE,Firefox,Chrome
			return true;
		}
		return false;
	}
    
    /**
     * 用于处理SQL查询IN语句中的值
     * @param ids
     * @return
     */
    public static String dealSQLInStatementId(String ids){
    	if(StringUtils.isNotEmpty(ids))
    	{
    		Pattern p = Pattern.compile("(^\\D+)|(\\D+$)");
			Matcher m = p.matcher(ids);
			if(m.find())
			{
				ids = m.replaceAll("");
			}
			p = Pattern.compile("\\D+");
			m = p.matcher(ids);
			if(m.find())
			{
				ids = m.replaceAll(",");
			}
			if(ids.startsWith(","))
				ids.substring(1);
    	}
    	return ids;
    }
	
	public static void main(String[] args) {

//		System.out.println(StringUtil.isNumber("-1"));
//		System.out.println(StringUtil.isNumeric("-1"));
//		System.out.println(StringUtil.createRandomNum(4));
//		System.out.println(StringUtil.length("你知道吗这是是是.txt"));
//		System.out.println();
//		System.out.println(omitString("名字很长很长很长很长很长很长很长很长的文件夹名",20,"..."));
		/*System.out.println(StringUtil.getEncoding("ÖÐ¹úÈË²ÅÈÈÏß-CJOL"));
		try{
			System.out.println(new String("ÖÐ¹úÈË²ÅÈÈÏß-CJOL".getBytes(),"gbk"));
			System.out.println(new String("ÖÐ¹úÈË²ÅÈÈÏß-CJOL".getBytes("UTF-8"),"gbk"));
			System.out.println(new String("ÖÐ¹úÈË²ÅÈÈÏß-CJOL".getBytes("gbk"),"gbk"));
			System.out.println(new String("ÖÐ¹úÈË²ÅÈÈÏß-CJOL".getBytes("iso-8859-1"),"gbk"));
			System.out.println(new String("ÖÐ¹úÈË²ÅÈÈÏß-CJOL".getBytes("gb2312"),"gbk"));
			System.out.println(new String("ÖÐ¹úÈË²ÅÈÈÏß-CJOL".getBytes("UTF-8"),"UTF-8"));
			System.out.println(new String("ÖÐ¹úÈË²ÅÈÈÏß-CJOL".getBytes("gbk"),"UTF-8"));
			System.out.println(new String("ÖÐ¹úÈË²ÅÈÈÏß-CJOL".getBytes("iso-8859-1"),"UTF-8"));
			System.out.println(new String("ÖÐ¹úÈË²ÅÈÈÏß-CJOL".getBytes("gb2312"),"UTF-8"));
			System.out.println(new String("ÖÐ¹úÈË²ÅÈÈÏß-CJOL".getBytes("UTF-8"),"iso-8859-1"));
			System.out.println(new String("ÖÐ¹úÈË²ÅÈÈÏß-CJOL".getBytes("gbk"),"iso-8859-1"));
			System.out.println(new String("ÖÐ¹úÈË²ÅÈÈÏß-CJOL".getBytes("iso-8859-1"),"iso-8859-1"));
			System.out.println(new String("ÖÐ¹úÈË²ÅÈÈÏß-CJOL".getBytes("gb2312"),"iso-8859-1"));
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
		System.out.println(StringUtil.getEncoding("名字很长很长很"));
		System.out.println(StringUtil.getEncoding("1111111"));
		System.out.println(StringUtil.getEncoding("sdfsdfsfsdf"));*/
		
		String ids = " [ 1 , 2 , 2 3 ,]";
//		System.out.println(dealSQLInStatementId(ids));
	}
	
}
